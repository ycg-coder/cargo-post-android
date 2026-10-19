#!/usr/bin/env bash
# 硬编码颜色判据的负控制（配 tools/check_hardcoded_color_probes.sh）。
# 每一格"恰好 N / 目标 0"都人为造一次破口，证明判据真的会红；注入落在 /tmp 沙盒，工程真身不动。
#
# 这一簿要证明的两件事：
#   ① HC-b 那个"升级弹窗 0 处"是**扫出来的**，不是扫描瞎了给的（正对照：同一把尺子在导航屏数得出 6）；
#   ② HC-d 的"理由"认的是**紧贴字面量的标记注释** —— 撤掉标记必须红，
#      而不是文件里凑巧出现过"支付宝""GOLD"这类主题词就算有理由（第一版就是这么假绿的）。
# 用法：bash tools/hardcoded_color_negative_controls.sh
set -u
SRC="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
. "${BASH_SOURCE[0]%/*}/negctl_lock.sh"
negctl_lock "$(basename "$0")" || exit 3
SC=/tmp/hc_neg.$$
CLEAN=/tmp/hc_neg_clean.$$
PKG=com/shanxiang/buspost
PROBE="$SRC/tools/check_hardcoded_color_probes.sh"

mkdir -p "$SC/tools" "$SC/app/src/main"
cp -r "$SRC/app/src/main/java" "$SC/app/src/main/java"
cp "$SRC/tools/hardcoded_color_reasons.py" "$SC/tools/"
cp "$SRC/tools/dialog_theme_scope.py" "$SC/tools/"
M=$SC/app/src/main/java/$PKG
UD=$M/core/update/UpdateDialog.kt
MA=$M/MainActivity.kt
QR=$M/ui/user/BusQrScreen.kt
BT=$M/core/ui/BusTokens.kt
for f in "$UD" "$QR" "$BT" "$MA" "$SC/tools/hardcoded_color_reasons.py" "$SC/tools/dialog_theme_scope.py"; do
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
del() { python - "$1" "$2" <<'PY' || { MISS=1; return 1; }
import sys
p, old = sys.argv[1], sys.argv[2]
s = open(p, encoding='utf-8', newline='').read()
if s.count(old) != 1: sys.exit("ANCHOR-NOT-UNIQUE(%d): %s" % (s.count(old), old[:60]))
open(p, 'w', encoding='utf-8', newline='').write(s.replace(old, "", 1))
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
expect_green() {
  local name="$1" hint="$2"
  out=$(BUSPOST_ANDROID="$SC" bash "$PROBE" 2>&1); rc=$?
  if [ "$rc" = 0 ] && printf '%s\n' "$out" | grep -aq "$hint"; then
    echo "✔ $name：仍绿且读数含「$hint」"; ok=$((ok+1))
  else
    echo "✘ $name：期望绿且读数含「$hint」，实际 rc=$rc"; bad=$((bad+1))
    printf '%s\n' "$out" | tail -2
  fi
  rm -rf "$SC"; cp -r "$CLEAN" "$SC"
}

# NC-1 升级弹窗里回潮一处字面量色 → HC-b 与 HC-a 一起红（这条是整簿的核心：那个 0 必须是扫出来的）
sub "$UD" 'color = t.ink,' 'color = Color(0xFF374151),'
expect_red "NC-1 弹窗字面量色回潮" "× HC-b 升级弹窗里又出现 1 处字面量色"

# NC-2 弹窗改成画黑字（绕过令牌）→ HC-c 红（0 处字面量不等于走了令牌）
sub "$UD" 'color = t.danger,' 'color = Color.Black,'
expect_red "NC-2 弹窗不吃令牌" "× HC-c 升级弹窗只吃到"

# NC-3 撤掉乘车码那颗的理由标记 → HC-d 红，且点名是哪一行
del "$QR" '                            // 硬编码色：支付宝品牌蓝 1677FF —— 品牌方规定的入口色，不随本 App 四套配色变
'
expect_red "NC-3 字面量没理由" "× HC-d 只有 15/16 处字面量带着理由注释"

# NC-4 令牌定义文件里自己加一处字面量 → 仍绿（证明排除的是定义本身，不是整本没扫）
ins "$BT" 'val isDark: Boolean,' '    val probeNew: Color = Color(0xFF123456),'
expect_green "NC-4 令牌定义内的字面量被正确排除" "总数 16"

# NC-5 探针脚本没了 → fail-closed（判据不许拿"跑不了"当 0）
rm -f "$SC/tools/hardcoded_color_reasons.py"
expect_red "NC-5 探针脚本缺失" "× HC-d 扫描认到的字面量不是 16" 1

# NC-6 把弹窗调用点搬回 BusAppTheme **外面**（HC-b/HC-c 仍然全绿的那种写法）→ HC-e 必须红
#      这一条是整簿最要紧的一条：D-163 那轮"0 处字面量 + 五个令牌都在"念的是绿，
#      而弹窗当时读的是默认浅色令牌 —— 写法判据看不见归属，归属只有括号知道。
sub "$MA" "UpdateDialog(" "RetiredDialog("
ins "$MA" "var updateRelease by remember" "                UpdateDialog(release = null, onDismiss = {})"
expect_red "NC-6 弹窗画在主题块外" "× HC-e 弹窗画在 BusAppTheme"

echo "RESULT ok=$ok bad=$bad miss=$MISS"
[ "$bad" = 0 ] && [ "$MISS" = 0 ] || exit 1
