#!/usr/bin/env bash
# 启动闪屏版面的静态闸（配 tools/splash_negative_controls.sh）。
#
# 2026-10-19 这一屏连着改了三轮，每轮都是**用户看着真机截图**指出来的问题，所以每一处都钉住：
#   SP-a 平台标语在册，且旧那句"面向城乡融合的…运力协同平台"不许回来（两句话讲同一件事＝两坨字互相盖）；
#   SP-b **那辆车就是进度条**：`ride` 由真实 progress 驱动，屏上不再另有一根进度条
#        （`LinearProgressIndicator` 在这一屏 = 0）。上一版是"细条 + 车"两件事并存，同一句话说了两遍。
#   SP-c 屏底版本号读的是包里的真值：全 main 不许再出现写死的 "v1.0.0"（正对照 = 调用点确实传了 BuildConfig）；
#   SP-d 退场三层各自一条时间线都在（品牌散 → 车驶离 → 整屏淡），少一层就退回「整屏一闪」那一版；
#   SP-e 就绪那一格**只给下限不给死高**：上一版钉 `height(34.dp)`，胶囊自己 35dp 高，
#        真机 v214 上被齐根切掉（用户口径"轻触上车按钮被遮挡住了"）。
#
# 用法：bash tools/check_splash_probes.sh
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
cd "$ROOT" || exit 2
M=app/src/main/java/com/shanxiang/buspost
SP=$M/core/ui/SplashOverlay.kt
ROOTUI=$M/ui/BusPostRoot.kt
CMT='(^[^:]+:[0-9]+:[[:space:]]*(//|\*|/\*))|(^[0-9]+:[[:space:]]*(//|\*|/\*))'

for f in "$SP" "$ROOTUI"; do
  [ -f "$f" ] || { echo "× 缺 $f：判据没有输入，本轮读数不采信"; exit 2; }
done

ncnt() { grep -nE "$1" "$2" 2>/dev/null | grep -vE "$CMT" | wc -l | tr -d ' '; }
cnt() { grep -c "$1" "$2" 2>/dev/null | tr -d ' '; }

# ---------- SP-a 标语 ----------
# ⚠ 必须走 `ncnt`（滤注释）：文件头那段设计契约里把整句标语原样抄了一遍做说明，
#   按字面量数就会把"写在注释里"数成"画在屏上"（§八 ㉗ 同族，本轮第一次跑就撞上）。
SA_LEAD=$(ncnt '赋能城乡融合的' "$SP")
SA_CLAIM=$(ncnt '“快递下乡、农品进城”' "$SP")
SA_TAIL=$(ncnt '客货邮数字化管理平台' "$SP")
SA_OLD=$(ncnt '面向城乡融合' "$SP")
SA_SLOGAN=$(ncnt '公交带货 · 快递进村' "$SP")

# ---------- SP-b 车就是进度条 ----------
SB_DEF=$(cnt '^private fun BusRoad(' "$SP")
SB_USE=$(ncnt 'BusRoad\(' "$SP")
SB_DRIVE=$(ncnt 'progress\.coerceIn\(0, 100\) / 100f' "$SP")
SB_BAR=$(ncnt 'LinearProgressIndicator' "$SP")
# 站牌与"停在哪"要落在**代码行**上：这两个名字只出现在注释里时，判据其实什么都没钉住
SB_STAND=$(ncnt 'val signX = size\.width' "$SP")
SB_STOP=$(ncnt 'val stopX = headLeft' "$SP")

# ---------- SP-c 版本号不写死 ----------
SC_FAKE=$(grep -rn '"v1\.0\.0"' "$M" --include=*.kt 2>/dev/null | grep -vE "$CMT" | wc -l | tr -d ' ')
SC_PASS=$(ncnt 'version = "v" \+ com\.shanxiang\.buspost\.BuildConfig\.VERSION_NAME' "$ROOTUI")
SC_NODEF=$(ncnt 'version: String,$' "$SP")

# ---------- SP-d 退场三层 ----------
SD_BRAND=$(ncnt 'brand\.animateTo\(0f,' "$SP")
SD_DEPART=$(ncnt 'depart\.animateTo\(1f,' "$SP")
SD_VEIL=$(ncnt 'veil\.animateTo\(0f,' "$SP")
SD_FALLBACK=$(cnt 'delay(4000)' "$SP")

# ---------- SP-e 就绪那一格不被切 ----------
SE_MIN=$(ncnt 'heightIn\(min = 44\.dp\)' "$SP")
SE_DEAD=$(ncnt 'height\(34\.dp\)' "$SP")

printf 'SP-a 引导/主张/落点/旧句/旧口号 : %s/%s/%s/%s/%s（目标 1/1/1/0/0）\n' \
  "$SA_LEAD" "$SA_CLAIM" "$SA_TAIL" "$SA_OLD" "$SA_SLOGAN"
printf 'SP-b BusRoad 定义/调用/进度驱动/另有一根条/站牌/停车点 : %s/%s/%s/%s/%s/%s（目标 1/2/1/0/1/1）\n' \
  "$SB_DEF" "$SB_USE" "$SB_DRIVE" "$SB_BAR" "$SB_STAND" "$SB_STOP"
printf 'SP-c 全 main 写死 v1.0.0 / 调用点传真值 / 参数无默认 : %s/%s/%s（目标 0/1/1）\n' \
  "$SC_FAKE" "$SC_PASS" "$SC_NODEF"
printf 'SP-d 品牌散/车驶离/整屏淡/4s 兜底 : %s/%s/%s/%s（目标 1/1/1/1）\n' \
  "$SD_BRAND" "$SD_DEPART" "$SD_VEIL" "$SD_FALLBACK"
printf 'SP-e 只给下限/钉死高 : %s/%s（目标 1/0）\n' "$SE_MIN" "$SE_DEAD"

fail=0
[ "$SA_LEAD" = 1 ] || { echo "× SP-a 标语的引导句不是恰好一处（现 $SA_LEAD）：这一行是用户点名要的平台定位，多了就是长出第二份"; fail=1; }
[ "$SA_CLAIM" = 1 ] || { echo "× SP-a 「快递下乡、农品进城」那句主张不在册（现 $SA_CLAIM）：金色那半句没了＝整句定位失去重点"; fail=1; }
[ "$SA_TAIL" = 1 ] || { echo "× SP-a 落点「客货邮数字化管理平台」不在册（现 $SA_TAIL）：只剩半句定位"; fail=1; }
[ "$SA_OLD" = 0 ] || { echo "× SP-a 旧那句「面向城乡融合的…运力协同平台」回来了（现 $SA_OLD）：与新标语是同一件事说两遍"; fail=1; }
[ "$SA_SLOGAN" = 0 ] || { echo "× SP-a 旧口号「公交带货 · 快递进村 · 农产品出山」又回到闪屏（现 $SA_SLOGAN）：它与新标语里的「快递下乡、农品进城」互相盖"; fail=1; }
[ "$SB_DEF" = 1 ] || { echo "× SP-b 屏底那一路不是恰好一份实现（现 $SB_DEF）"; fail=1; }
[ "$SB_USE" = 2 ] || { echo "× SP-b BusRoad 的定义+调用不是 2（现 $SB_USE）：少一处＝车又变成装饰，多一处＝屏上长出第二条路"; fail=1; }
[ "$SB_DRIVE" = 1 ] || { echo "× SP-b 车不再由真实 progress 驱动（现 $SB_DRIVE）：那它就又是墙钟自演的动画，进度这一格没人管了"; fail=1; }
[ "$SB_BAR" = 0 ] || { echo "× SP-b 这一屏又长出一根独立进度条（现 $SB_BAR）：车与细条同一件事说两遍"; fail=1; }
[ "$SB_STAND" = 1 ] || { echo "× SP-b 站牌不再画在路那头（现 $SB_STAND）：车没有终点，进度就读不出「到站」这件事"; fail=1; }
[ "$SB_STOP" = 1 ] || { echo "× SP-b 车不再停在站牌脚下（现 $SB_STOP）：100% 那一格失去落点，进度条又变回一根线"; fail=1; }
[ "$SC_FAKE" = 0 ] || { echo "× SP-c 全 main 又出现写死的 \"v1.0.0\"（现 $SC_FAKE）：闪屏会安静地撒谎说自己是 1.0.0"; fail=1; }
[ "$SC_PASS" = 1 ] || { echo "× SP-c 调用点不再把包里的真版本传进去（现 $SC_PASS）：与上一条同族——那一格没人传就有人写默认值"; fail=1; }
[ "$SC_NODEF" = 1 ] || { echo "× SP-c 闪屏的 version 参数又带上默认值（现 $SC_NODEF）：会过期的默认值比缺参数更坏，缺参数编译不过"; fail=1; }
{ [ "$SD_BRAND" = 1 ] && [ "$SD_DEPART" = 1 ] && [ "$SD_VEIL" = 1 ]; } || { echo "× SP-d 退场三层不齐（品牌 $SD_BRAND 车 $SD_DEPART 整屏 $SD_VEIL）：少一层就退回「整屏一闪」那一版"; fail=1; }
[ "$SD_FALLBACK" = 1 ] || { echo "× SP-d 那条 4 秒自动进入没了（现 $SD_FALLBACK）：取数挂住时用户被关在闪屏外，没有出口"; fail=1; }
[ "$SE_MIN" = 1 ] || { echo "× SP-e 就绪那一格不再只给高度下限（现 $SE_MIN）：见下一条"; fail=1; }
[ "$SE_DEAD" = 0 ] || { echo "× SP-e 就绪那一格又钉了死高 34dp（现 $SE_DEAD）：胶囊本身 35dp 高，真机上被齐根切掉（v214 底片）"; fail=1; }

if [ "$fail" = 0 ]; then
  echo "闪屏版面判据通过（标语三档在册 / 车就是进度条 / 版本号读真值 / 退场三层齐 / 就绪格不被切）"
  exit 0
fi
exit 1
