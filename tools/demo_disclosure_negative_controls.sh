#!/usr/bin/env bash
# 「兜底/演示数据必须说出来」判据的负控制（配 tools/check_demo_disclosure_probes.sh）
# 每个"目标 0 / 恰好 N"都要人为造一次破口，证明它会红；注入落在 /tmp 沙盒（整目录 copy），工程真身不动。
# 用法：bash tools/demo_disclosure_negative_controls.sh
set -u
SRC="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
. "${BASH_SOURCE[0]%/*}/negctl_lock.sh"
negctl_lock "$(basename "$0")" || exit 3
SC=/tmp/dd_neg.$$
CLEAN=/tmp/dd_neg_clean.$$
PKG=com/shanxiang/buspost
PROBE="$SRC/tools/check_demo_disclosure_probes.sh"

mkdir -p "$SC/app/src/main"
cp -r "$SRC/app/src/main/java" "$SC/app/src/main/java"
M=$SC/app/src/main/java/$PKG
BITS=$M/ui/common/Bits.kt
HS=$M/ui/user/HomeScreen.kt
WB=$M/ui/driver/WorkbenchScreen.kt
MD=$M/data/model/Models.kt
LSC=$M/ui/login/LoginScreen.kt
DMINE=$M/ui/driver/DriverMineScreen.kt
for f in "$BITS" "$HS" "$WB" "$MD" "$LSC" "$DMINE"; do
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

# 每条注入后必须让判据变红；红在**哪一格**也要点名，否则"因为别的格红"也算红=空转
expect_red() { # expect_red <标签> <必须出现的 × 行关键字>
  local label="$1" needle="$2"
  out=$(BUSPOST_ANDROID="$SC" bash "$PROBE" 2>&1); rc=$?
  if [ "$rc" = 0 ]; then
    echo "✘ $label：注入了，判据却仍然全绿 —— 这道闸是空转的"; bad=$((bad+1))
  elif ! printf '%s\n' "$out" | grep -a "^× $needle" >/dev/null; then
    echo "✘ $label：红了，但没红在该管的那一格（缺 × $needle）"; bad=$((bad+1))
    printf '%s\n' "$out" | grep -a '^×' | head -3 | sed 's/^/      /'
  else
    echo "✔ $label → 红在 × $needle"; ok=$((ok+1))
  fi
  rm -rf "$SC"; cp -r "$CLEAN" "$SC"
}

# NC-1a 把守卫换成裸 return（复现 10-15 真机那一刀）：DD-a2 目标 0 必须抓到
sub "$BITS" '    if (!demo) return' '    return' && expect_red "NC-1a 守卫改成裸 return" "DD-a2"

# NC-1b 守卫反向（`demo` 为真才 return）：函数永远不画标注，但裸 return 计数仍是 0 ——
# 只有"守卫恰好一处 !demo"这一格能抓住它，证明 DD-a 不是凑数的
sub "$BITS" 'if (!demo) return' 'if (demo) return' && expect_red "NC-1b 守卫方向反了" "DD-a "

# NC-2 删掉一处调用点（首页）⇒ 26 < 27
sub "$HS" 'item { DemoNote(vm.demo) }' 'item { }' && expect_red "NC-2 少一处调用点" "DD-b"

# NC-3 有人把某屏写死成 false ⇒ 目标 0 的那一格必须抓到（正对照：DD-c2 仍 =1）
sub "$WB" 'DemoNote(true, text =' 'DemoNote(false, text =' && expect_red "NC-3 写死 false" "DD-c "

# NC-4 明文图不再升 https（退回"原样返回"）⇒ 调用点与升级分支同时掉
sub "$MD" 'return if (isPrivateHost(host)) s else "https://" + s.substringAfter("://")' 'return s' \
  && expect_red "NC-4 撤掉 http→https" "DD-e2"

# NC-5 页面绕过 onMobileChange 直接改号码 ⇒ 口子引用掉 0、裸写回到 1
sub "$LSC" 'onValueChange = { v -> vm.onMobileChange(v) },' 'onValueChange = { v -> vm.mobile = v.filter { it.isDigit() }.take(11) },' \
  && expect_red "NC-5 换号不作废" "DD-f2"

# NC-6 司机"我的"退回只念账号昵称 ⇒ 档案名那格掉
sub "$DMINE" 'val rosterName = Prefs.driverName' 'val rosterName = ""' && expect_red "NC-6 档案名不上屏" "DD-g "

echo
echo "负控：$ok 红 / $bad 未红，注入锚点未命中 $MISS"
if [ "$bad" != 0 ] || [ "$MISS" != 0 ]; then
  echo "❌ demo_disclosure 负控未全数抓到破口 —— 判据不可信"
  exit 1
fi
echo "✅ demo_disclosure 负控：7 刀全红，且各自红在该管的那一格"
