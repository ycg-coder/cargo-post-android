#!/usr/bin/env bash
# #39「收藏线路首页入口」结构判据的负控制（配 tools/check_favorite_entry_probes.sh）
# 每个"目标 0 / 恰好 N"都要人为造一次破口，证明它会红；注入落在 /tmp 沙盒（整目录 copy），工程真身不动。
# 用法：bash tools/favorite_entry_negative_controls.sh
set -u
SRC="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
. "${BASH_SOURCE[0]%/*}/negctl_lock.sh"
negctl_lock "$(basename "$0")" || exit 3
SC=/tmp/fe_neg.$$
CLEAN=/tmp/fe_neg_clean.$$
PKG=com/shanxiang/buspost
PROBE="$SRC/tools/check_favorite_entry_probes.sh"

mkdir -p "$SC/app/src/main" "$SC/app/src/test"
cp -r "$SRC/app/src/main/java" "$SC/app/src/main/java"
cp -r "$SRC/app/src/test/java" "$SC/app/src/test/java"
M=$SC/app/src/main/java/$PKG
FL=$M/ui/user/FavoriteLines.kt
HS=$M/ui/user/HomeScreen.kt
BS=$M/ui/user/BusScreen.kt
CT=$SC/app/src/test/java/$PKG/ui/user/FavoriteLinesContractTest.kt
for f in "$FL" "$HS" "$BS" "$CT"; do
  [ -f "$f" ] || { echo "✘ 沙盒缺 $f：判据扫得到、副本没有，本轮负控全部作废"; exit 2; }
done
cp -r "$SC" "$CLEAN"
trap 'rm -rf "$SC" "$CLEAN"; negctl_release' EXIT

out=$(BUSPOST_ANDROID="$SC" bash "$PROBE" 2>&1); base=$?
if [ "$base" != 0 ]; then echo "✘ 基线未通过，负控结果一律不采信"; printf '%s\n' "$out" | grep -a '^×' | head -5; exit 2; fi
echo "✔ 基线：副本原样 exit 0"

MISS=0; ok=0; bad=0
sub() { python - "$1" "$2" "$3" <<'PY' || { MISS=1; return 1; }
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8', newline='').read()
if s.count(old) != 1: sys.exit("ANCHOR-NOT-UNIQUE(%d): %s" % (s.count(old), old[:60]))
if old in new: sys.exit("SELF-MATCH: 替换文本里还留着锚点")
open(p, 'w', encoding='utf-8', newline='').write(s.replace(old, new, 1))
PY
}
delline() { python - "$1" "$2" <<'PY' || { MISS=1; return 1; }
import sys
p, needle = sys.argv[1], sys.argv[2]
s = open(p, encoding='utf-8', newline='').read()
if s.count(needle) != 1: sys.exit("ANCHOR-NOT-UNIQUE(%d): %s" % (s.count(needle), needle[:60]))
i = s.find(needle); j = s.find('\n', i)
open(p, 'w', encoding='utf-8', newline='').write(s[:i] + s[j + 1:])
PY
}
ins_after() { python - "$1" "$2" "$3" <<'PY' || { MISS=1; return 1; }
import sys
p, anchor, add = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8', newline='').read()
if s.count(anchor) != 1: sys.exit("ANCHOR-NOT-UNIQUE(%d): %s" % (s.count(anchor), anchor[:60]))
NL = '\r\n' if '\r\n' in s else '\n'
j = s.index('\n', s.find(anchor)) + 1
open(p, 'w', encoding='utf-8', newline='').write(s[:j] + add.replace('\n', NL) + NL + s[j:])
PY
}

check() { # $1=编号 $2=期望点名的判据 $3..=注入
  local id="$1" want="$2" rc out
  shift 2
  if ! "$@"; then
    echo "✘ $id 锚点没命中/不唯一，破口根本没注入（这条负控不成立）"
    bad=$((bad + 1)); MISS=0; rm -rf "$SC"; cp -r "$CLEAN" "$SC"; return
  fi
  out=$(BUSPOST_ANDROID="$SC" bash "$PROBE" 2>&1); rc=$?
  if [ "$rc" = 0 ]; then
    echo "✘ $id 判据仍然绿（破口没咬住），这条判据等于没判据"; bad=$((bad + 1))
  elif printf '%s\n' "$out" | grep -qF "$want"; then
    echo "✔ $id 红且原因对得上（$want）"; ok=$((ok + 1))
  else
    echo "✘ $id 红了但不是那一条（缺 $want；实为 $(printf '%s\n' "$out" | grep -a '^×' | head -1)）"; bad=$((bad + 1))
  fi
  MISS=0; rm -rf "$SC"; cp -r "$CLEAN" "$SC"
}
check_miss() {
  local id="$1"; shift
  if "$@"; then
    echo "✘ $id 注入器放过了一个不存在的锚点（后面每条 ✔ 都可能是假绿）"; bad=$((bad + 1))
  else
    echo "✔ $id 不存在的锚点被注入器当场拒绝"; ok=$((ok + 1))
  fi
  MISS=0; rm -rf "$SC"; cp -r "$CLEAN" "$SC"
}

# ---------- FE-a 首页那一格 / 宫格总数 ----------
check FE-a1 "× FE-a 首页收藏那一格不是恰好一个" sub "$HS" \
  '            FeatureTile(BusIcons.star, FAVORITE_ENTRY_LABEL, FeatureBg.CLAY, {' \
  '            FeatureTile(BusIcons.next, "更多功能", FeatureBg.CLAY, {'
check FE-a2 "× FE-a 首页宫格总数不是 8" delline "$HS" '            FeatureTile(BusIcons.bell, "消息通知", FeatureBg.GOLD, {'
check FE-a3 "× FE-a 首页收藏那一格不是恰好一个" ins_after "$HS" \
  '                nav.navigate(Routes.BUS)
            }, Modifier.weight(1f), 7)' \
  '            FeatureTile(BusIcons.star, FAVORITE_ENTRY_LABEL, FeatureBg.CLAY, { nav.navigate(Routes.BUS) }, Modifier.weight(1f), 8)'

# ---------- FE-b 登录闸门 / 进得去公交页 ----------
check FE-b1 "× FE-b 首页登录闸门数变了" sub "$HS" \
  '            FeatureTile(BusIcons.star, FAVORITE_ENTRY_LABEL, FeatureBg.CLAY, {
                nav.navigate(Routes.BUS)' \
  '            FeatureTile(BusIcons.star, FAVORITE_ENTRY_LABEL, FeatureBg.CLAY, {
                if (guest) onLoginClick?.invoke() else nav.navigate(Routes.BUS)'
check FE-b2 "× FE-b 进实时公交的导航点不是 3" sub "$HS" \
  '                nav.navigate(Routes.BUS)
            }, Modifier.weight(1f), 7)' \
  '                nav.navigate(Routes.SEND)
            }, Modifier.weight(1f), 7)'

# ---------- FE-c 文案唯一出处 ----------
check_miss FE-c0 sub "$FL" '这一行在本仓根本不存在-NEG-CONTROL' 'x'
check FE-c1 "× FE-c 入口文案的唯一出处断了" sub "$HS" \
  'FeatureTile(BusIcons.star, FAVORITE_ENTRY_LABEL' \
  'FeatureTile(BusIcons.star, "收藏线路"'
check FE-c2 "× FE-c 入口文案的唯一出处断了" sub "$FL" \
  'internal const val FAVORITE_ENTRY_LABEL = "收藏线路"' \
  'internal const val FAV_LABEL_MOVED_AWAY = "收藏线路"'
check FE-c3 "× FE-c 页面重抄了纯层字面" ins_after "$BS" \
  '                            " " + FAVORITE_LOCAL_NOTE,' \
  '                            "收藏只存在这台设备上，改这一份不会同步到别处",'

# ---------- FE-d 未命中那一格的两支 / 失败原因带出来 ----------
check FE-d1 "× FE-d 未命中那一格的两支不齐" sub "$FL" \
  '"线路列表这一轮没取到（$reason），所以这 $missing 条收藏先不显示——不是没有收藏"' \
  '"其中 $missing 条收藏在当前线路列表里没有（可能已下架），本机仍记着 $idCount 条"'

# ---------- FE-e 整栏闸门 / 未命中调用 ----------
check FE-e1 "× FE-e 整栏闸门形状变了" sub "$BS" \
  'if (favoriteRowVisible(favIds.size))' \
  'if (favLines.isNotEmpty())'
check FE-e2 "× FE-e 页面不再拿三个数去判未命中" delline "$BS" \
  '                        favoriteUnresolvedText(favIds.size, favLines.size, vm.linesError)?.let {'

# ---------- FE-f 页面两处说明落点 ----------
check FE-f1 "× FE-f 页面说明落点不齐" sub "$BS" \
  '                            " " + FAVORITE_LOCAL_NOTE,' \
  '                            "",'

# ---------- FE-g 不许出现同步类承诺 ----------
check FE-g1 "× FE-g 纯层生效文案里出现同步/恢复类承诺" sub "$FL" \
  'internal const val FAVORITE_LOCAL_NOTE = "收藏只存在这台设备上，换设备或清除应用数据后会丢失"' \
  'internal const val FAVORITE_LOCAL_NOTE = "收藏已同步到账号，换设备登录即可恢复"'

# ---------- FE-h 用例簿 ----------
check FE-h1 "× FE-h 契约用例不是 9 条" ins_after "$CT" \
  '    fun `标题数的是本机记着的条数`() {' \
  '    @Test'

echo "---"
echo "计数：按名点名变红 $ok 条 / 不成立 $bad 条"
[ "$bad" = 0 ] || echo "✘ 有破口没被自己那条判据抓住，或注入根本没落"
[ "$bad" = 0 ]
