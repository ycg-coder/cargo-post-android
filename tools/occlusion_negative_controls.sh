#!/usr/bin/env bash
# 浮层遮挡判据的负控制（配 tools/check_occlusion_probes.sh）。
# 每一格"恰好 N / 目标 0"都人为造一次破口，证明判据真的会红；注入落在 /tmp 沙盒，工程真身不动。
#
# 这一簿特别的一条：OC-a 管的是"写了判据但没接进 audit()" —— 那种空转在源码里长得跟正常一模一样，
# 只有靠"删掉调用行 ⇒ 下面所有几何读数一起变 0"才抓得住，所以 NC-1 是整簿的核心。
# 用法：bash tools/occlusion_negative_controls.sh
set -u
SRC="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
. "${BASH_SOURCE[0]%/*}/negctl_lock.sh"
negctl_lock "$(basename "$0")" || exit 3
SC=/tmp/oc_neg.$$
CLEAN=/tmp/oc_neg_clean.$$
PKG=com/shanxiang/buspost
PROBE="$SRC/tools/check_occlusion_probes.sh"

mkdir -p "$SC/tools" "$SC/app/src/main"
cp "$SRC/tools/layout_audit.py" "$SC/tools/"
cp -r "$SRC/tools/fixtures" "$SC/tools/fixtures"
mkdir -p "$SC/docs"
cp -r "$SRC/app/src/main/java" "$SC/app/src/main/java"
mkdir -p "$SC/docs/device-evidence/20261018-float"
cp "$SRC"/docs/device-evidence/20261018-float/*.xml "$SC/docs/device-evidence/20261018-float/" \
  || { echo "X 沙盒缺本轮真机 dump：OC-g 没有输入，本轮负控作废"; exit 2; }
for f in docs/device-evidence/20261010-sweep/27-lane-nav.xml \
         docs/device-evidence/20261010-sweep/28-voice-on.xml \
         docs/device-evidence/20261010-115241/44-real-tab-848.xml \
         docs/device-evidence/20261010-115253/44-real-tab-1288.xml; do
  mkdir -p "$SC/$(dirname "$f")"
  cp "$SRC/$f" "$SC/$f" || { echo "✘ 沙盒缺 $f：OC-d 没有输入，本轮负控作废"; exit 2; }
done
M=$SC/app/src/main/java/$PKG
LA=$SC/tools/layout_audit.py
GD=$M/ui/driver/DriverGuideScreen.kt
PK=$M/ui/user/MapPointPickerScreen.kt
for f in "$LA" "$GD" "$PK"; do
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
ins() { python - "$1" "$2" "$3" <<'PY' || { MISS=1; return 1; }
import sys
p, needle, line = sys.argv[1], sys.argv[2], sys.argv[3]
lines = open(p, encoding='utf-8', newline='').read().split('\n')
hits = [i for i, l in enumerate(lines) if needle in l]
if len(hits) != 1: sys.exit("NEEDLE-NOT-UNIQUE(%d): %s" % (len(hits), needle[:60]))
lines.insert(hits[0] + 1, line)
open(p, 'w', encoding='utf-8', newline='').write('\n'.join(lines))
PY
}
expect_red() {
  local name="$1" needle="$2" want_rc="${3:-1}"
  out=$(BUSPOST_ANDROID="$SC" bash "$PROBE" 2>&1); rc=$?
  if [ "$rc" = "$want_rc" ] && printf '%s\n' "$out" | grep -aq "$needle"; then
    echo "✔ $name：判据按预期变红（rc=$rc）"; ok=$((ok+1))
  else
    echo "✘ $name：期望 rc=$want_rc 且「$needle」实际 rc=$rc"; bad=$((bad+1))
    printf '%s\n' "$out" | grep -a '^×' | head -3
  fi
  rm -rf "$SC"; cp -r "$CLEAN" "$SC"
}

# NC-1 判据写了但没接进 audit() → OC-a 红，而且下面每一条几何读数一起塌（空转的形状）
sub "$LA" '    issues.extend(occluded(leaves, min_pct=min_cover))
' ''
expect_red "NC-1 遮挡档没接进 audit()" "× OC-a 写了判据但没接进 audit()"

# NC-2 门槛被人抬到永远够不着 → 坏 fixture 不再报（OC-b 红）
sub "$LA" '        if pct >= min_pct:' '        if pct >= 200:'
expect_red "NC-2 门槛抬成永不相交" "× OC-b 坏 fixture 报的遮挡不是恰好 1 处"

# NC-3 正对照的对面：把好 fixture 的按钮按回被压住的那一版 → OC-c 红
sub "$SC/tools/fixtures/occlusion_capsule_good.xml" '[760,2040][1040,2110]' '[760,2270][1040,2340]'
expect_red "NC-3 好样本里把按钮压回胶囊下" "× OC-c 好 fixture 报"

# NC-4 到站格不再浮在胶囊之上（撤掉那一句 padding）→ OC-e 红
sub "$GD" '                    .padding(bottom = capsuleReserve())
' ''
expect_red "NC-4 撤掉到站格的让高" "× OC-e 到站格没有浮在胶囊之上"

# NC-5 bottomReserve 少了胶囊那一格（地图仍被到站格压住）→ OC-e 红
sub "$GD" 'bottomPx.toDp() } + capsuleReserve() + 8.dp' 'bottomPx.toDp() } + 8.dp'
expect_red "NC-5 地图让高没算胶囊那一格" "× OC-e 导航页给胶囊让高的地方不是 3"

# NC-6 选点屏那枚自绘白方块回来 → OC-f 红
ins "$PK" '    CapsuleActions(' '    val fabDesc = "回到当前位置"'
expect_red "NC-6 白方块回来" "× OC-f 地图上那枚自绘白方块回来了"

# NC-7 真机 dump 被删一份：合计仍可能对得上，但来源不对 → 必须红（缺输入走 fail-closed rc=2）
rm -f "$SC/docs/device-evidence/20261010-sweep/28-voice-on.xml"
expect_red "NC-7 少一份真机 dump" "× 缺真机 dump" 2

# NC-8 修好之后的真机 dump 里混进一份被压住的 → OC-g 红（这一档认的是屏幕上的几何，不是 fixture）
cp "$SC/tools/fixtures/occlusion_capsule_bad.xml" "$SC/docs/device-evidence/20261018-float/99-regression.xml"
expect_red "NC-8 新真机 dump 里出现遮挡" "OC-g" 1

# NC-9 本轮真机 dump 整批被删 → fail-closed rc=2
rm -f "$SC"/docs/device-evidence/20261018-float/*.xml
expect_red "NC-9 缺本轮真机证据" "本轮真机 dump" 2

echo "RESULT ok=$ok bad=$bad miss=$MISS"
[ "$bad" = 0 ] && [ "$MISS" = 0 ] || exit 1
