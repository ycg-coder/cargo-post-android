#!/usr/bin/env bash
# 硬编码颜色的判据（配 tools/hardcoded_color_negative_controls.sh）—— #31 那半格的账本。
#
# 这一本不追求"字面量色 = 0"。追求的是：**每一处字面量都有名字、有理由、有人数着**。
#   HC-a 全工程（除令牌定义本身）的字面量色恰好 9 处，且按文件点名到个位；
#   HC-b `UpdateDialog` 必须 0 —— 它是 2026-10-18 修掉的那处真主题泄漏（整个升级弹窗把四套配色与深色全绕过，
#        深色下 `BusCard` 变深底、正文却还是 0xFF374151 深灰字 ⇒ 读不出字）；
#        正对照是同一条扫描在导航屏那一屏数得出 6 处，证明这个 0 不是扫描瞎了给的；
#   HC-c `UpdateDialog` 现在吃的确实是令牌（`t.ink` / `t.textSecondary` / `t.danger` / `t.track` / `t.primary` 各 ≥1）；
#   HC-d 剩下那 9 处**每一处都要在文件里写着自己的理由**（导航覆盖层待拍板 / 支付宝品牌色 / 与固定底色成对），
#        没理由的字面量就是没人认领的债。
#
# 用法：bash tools/check_hardcoded_color_probes.sh        退出码非 0 = 这一格被拆了
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
cd "$ROOT" || exit 2
M=app/src/main/java/com/shanxiang/buspost
UD=$M/core/update/UpdateDialog.kt
GD=$M/ui/driver/DriverGuideScreen.kt
NV=$M/ui/driver/DriverNavScreen.kt
QR=$M/ui/user/BusQrScreen.kt
HM=$M/ui/user/HomeScreen.kt
PAT='Color\(0x[0-9A-Fa-f]{6,8}\)'

for f in "$UD" "$GD" "$NV" "$QR" "$HM"; do
  [ -f "$f" ] || { echo "× 缺 $f：判据没有输入，本轮读数不采信"; exit 2; }
done

cnt() { grep -cE "$PAT" "$1" 2>/dev/null | tr -d ' '; }
# 只数代码行：注释里写一句 Color(0x…) 不算一处
cnt_code() { grep -nE "$PAT" "$1" 2>/dev/null | grep -vE ':[0-9]+:[[:space:]]*(//|\*)' | wc -l | tr -d ' '; }

TOTAL=$(grep -rnE "$PAT" "$M" --include=*.kt 2>/dev/null | grep -v 'core/ui/BusTokens.kt' \
        | grep -vE ':[0-9]+:[[:space:]]*(//|\*)' | wc -l | tr -d ' ')
A_UD=$(cnt_code "$UD"); A_GD=$(cnt_code "$GD"); A_NV=$(cnt_code "$NV")
A_QR=$(cnt_code "$QR"); A_HM=$(cnt_code "$HM")
SUM=$((A_UD + A_GD + A_NV + A_QR + A_HM))

HC_C=0
for tok in 'color = t.ink,' 'color = t.textSecondary,' 'color = t.danger,' '.background(t.track),' 'color = t.primary,'; do
  n=$(grep -cF "$tok" "$UD" 2>/dev/null | tr -d ' ')
  [ "$n" -ge 1 ] && HC_C=$((HC_C + 1))
done

# HC-d 每一处字面量**上面三行内**要有一条 `// 硬编码色：<理由>`。
#     ⚠ 这一格第一版写的是"文件里出现『支付宝』『GOLD』这类词就算有理由" —— 那是假绿：
#       那些是这一屏的主题词，跟那颗颜色为什么不走令牌毫无关系（BusQrScreen 里"支付宝"出现 12 次，
#       没有一处是理由）。所以认的是**紧贴字面量的标记注释**，撤掉标记就红。
PY=$(command -v python || command -v python3)
HC_D=$(PYTHONIOENCODING=utf-8 "$PY" tools/hardcoded_color_reasons.py "$M")
HC_D_LIT=$(printf '%s' "$HC_D" | awk '{print $1}')
HC_D_OK=$(printf '%s' "$HC_D" | awk '{print $2}')
HC_D_MISS=$(printf '%s' "$HC_D" | awk '{print $3}')

fail=0

[ "$TOTAL" = 9 ] || { echo "× HC-a 字面量色总数不是 9（现 $TOTAL）：多了是新债，少了要同轮把在册表与理由一起改，不许悄悄改数"; fail=1; }
[ "$SUM" = "$TOTAL" ] || { echo "× HC-a 五个点名文件加起来 $SUM ≠ 全工程 $TOTAL：有文件没被点名 ⇒ 那几处没人看着"; fail=1; }
[ "$A_GD" = 6 ] || { echo "× HC-a 导航屏那 6 处变了（现 $A_GD）：那一屏的深色覆盖层配色属 #31 待拍板，动它要连本行与理由一起改"; fail=1; }
[ "$A_NV" = 1 ] && [ "$A_QR" = 1 ] && [ "$A_HM" = 1 ] || { echo "× HC-a 其余三处点名数变了（导航 HUD $A_NV / 乘车码 $A_QR / 首页 $A_HM）"; fail=1; }
[ "$A_UD" = 0 ] || { echo "× HC-b 升级弹窗里又出现 $A_UD 处字面量色：那一屏是本轮修掉的真主题泄漏（深色下深底配深灰字读不出），回潮就是回潮"; fail=1; }
[ "$A_GD" -ge 1 ] || { echo "× HC-b 反证失效：同一条扫描在导航屏数出 0 处，上面那个 0 不采信"; fail=1; }
[ "$HC_C" = 5 ] || { echo "× HC-c 升级弹窗只吃到 $HC_C/5 个令牌（ink / textSecondary / danger / track / primary）：0 处字面量不等于走了令牌"; fail=1; }
[ "$HC_D_LIT" = 9 ] || { echo "× HC-d 扫描认到的字面量不是 9（现 $HC_D_LIT）：与 HC-a 不同源的两把尺子迟早分叉"; fail=1; }
[ "$HC_D_OK" = 9 ] || { echo "× HC-d 只有 $HC_D_OK/9 处字面量带着理由注释，没理由的：$HC_D_MISS —— 没理由的字面量就是没人认领的债"; fail=1; }

if [ "$fail" = 0 ]; then
  echo "硬编码颜色判据通过（总数 $TOTAL = 弹窗 $A_UD + 导航 $A_GD + HUD $A_NV + 乘车码 $A_QR + 首页 $A_HM；弹窗走令牌 $HC_C/5；理由注释 $HC_D_OK/$HC_D_LIT）"
else
  echo "硬编码颜色判据失败"
fi
exit "$fail"
