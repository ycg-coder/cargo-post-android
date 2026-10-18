#!/usr/bin/env bash
# 浮层遮挡判据（配 tools/occlusion_negative_controls.sh）。用户口径："能悬浮的都可以做悬浮按钮不要阻挡"。
#
# "不阻挡"不能是一句形容词：本簿把它落成一次几何复算 ——
#   OC-a 遮挡判据在 `tools/layout_audit.py` 里只有一份实现，且真的被 `audit()` 调用（不认形状不认语义的那类空转）；
#   OC-b 坏 fixture（导航页到站格贴屏幕底、胶囊后画压住「回工作台作业」）报**恰好 1 处**并点名那颗按钮；
#   OC-c 好 fixture（同形状但到站格让出胶囊那一格）报 0 处，正对照是同一次扫描在坏的那份里数得出 1；
#   OC-d 四份在册真机 dump 各报 1 处（合计 4）—— 这一档在真实几何上咬得住，不是只会读 fixture；
#   OC-e 车道级导航那一屏：到站格浮在胶囊之上（两处让高 + 那一句 padding 都在）；
#   OC-f 地图选点屏：功能按钮已进胶囊，地图上那枚自绘白方块与面板末尾的通栏按钮都撤了。
#
# 为什么 fixture 与真机 dump 都要：fixture 管"判据还活着"，真机 dump 管"这一档量的确实是屏幕上的东西"。
# 用法：bash tools/check_occlusion_probes.sh        退出码非 0 = 这一格被拆了
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
cd "$ROOT" || exit 2
M=app/src/main/java/com/shanxiang/buspost
LA=tools/layout_audit.py
GD=$M/ui/driver/DriverGuideScreen.kt
PK=$M/ui/user/MapPointPickerScreen.kt
BAD=tools/fixtures/occlusion_capsule_bad.xml
GOOD=tools/fixtures/occlusion_capsule_good.xml
DUMPS="docs/device-evidence/20261010-sweep/27-lane-nav.xml
docs/device-evidence/20261010-sweep/28-voice-on.xml
docs/device-evidence/20261010-115241/44-real-tab-848.xml
docs/device-evidence/20261010-115253/44-real-tab-1288.xml"
CMT='(^[^:]+:[0-9]+:[[:space:]]*(//|\*|/\*))|(^[0-9]+:[[:space:]]*(//|\*|/\*))'

PY=$(command -v python || command -v python3)
[ -n "$PY" ] || { echo "× 本机没有 python：几何复算跑不了，本轮读数不采信"; exit 2; }

for f in "$LA" "$BAD" "$GOOD" "$GD" "$PK"; do
  [ -f "$f" ] || { echo "× 缺 $f：判据没有输入，本轮读数不采信"; exit 2; }
done
for f in $DUMPS; do
  [ -s "$f" ] || { echo "× 缺真机 dump $f：OC-d 没有输入，本轮读数不采信"; exit 2; }
done

# occ_lines <xml>  → 只数"被后画浮层盖住"这一档的行（其余档位不参与本簿）
occ_lines() {
  PYTHONIOENCODING=utf-8 "$PY" "$LA" "$1" 2>/dev/null | grep -ac "被后画浮层盖住" | tr -d ' '
}

one_count() { grep -nE "$1" "$2" 2>/dev/null | grep -vE "$CMT" | wc -l | tr -d ' '; }

# OC-a 一份实现、且真的被调用
OA_DEF=$(one_count '^def occluded\(' "$LA")
OA_CALL=$(one_count 'issues\.extend\(occluded\(' "$LA")
OA_FLAG=$(one_count '\-\-min-cover=' "$LA")

# OC-b / OC-c
OB=$(occ_lines "$BAD")
OB_NAME=$(PYTHONIOENCODING=utf-8 "$PY" "$LA" "$BAD" 2>/dev/null | grep -ac "被后画浮层盖住.*回工作台作业" | tr -d ' ')
OC=$(occ_lines "$GOOD")

# OC-d 真机 dump 合计
OD=0
OD_FILES=0
for f in $DUMPS; do
  n=$(occ_lines "$f")
  OD=$((OD + n))
  [ "$n" -ge 1 ] && OD_FILES=$((OD_FILES + 1))
done

# OC-e 到站格浮在胶囊之上，且地图**三个分支**都把胶囊那一格让出来
#     （基数 3 → 4，2026-10-18 D-169：高德自带面板重新打开后，它的"剩余里程/耗时"那条画在地图视图
#       自己的下沿，而"没有到站格"那一支原来是 `else -> 0.dp` —— 让 0 就是让胶囊压住高德那条。）
OE_RESERVE=$(one_count 'capsuleReserve\(\)' "$GD")
OE_PAD=$(one_count '\.padding\(bottom = capsuleReserve\(\)\)' "$GD")

# OC-f 选点屏：进胶囊一处、白方块与通栏确认都撤干净
OF_REG=$(one_count 'CapsuleActions\(' "$PK")
OF_FAB=$(one_count '回到当前位置' "$PK")
OF_CTA=$(one_count '确认选择此位置' "$PK")
OF_PROOF=$(one_count 'CapsuleAction\(' "$PK")

# OC-g 修好之后的真机 dump（在册目录）：每一张都必须 0 处遮挡
EVID=docs/device-evidence/20261018-float
OG_N=$(ls "$EVID"/*.xml 2>/dev/null | wc -l | tr -d ' ')
[ "$OG_N" -ge 3 ] || { echo "× 缺本轮真机 dump（$EVID 只有 $OG_N 份）：'不阻挡'没有屏幕证据，本轮读数不采信"; exit 2; }
OG=0
for f in "$EVID"/*.xml; do OG=$((OG + $(occ_lines "$f"))); done

fail=0

[ "$OA_DEF" = 1 ] || { echo "× OC-a 遮挡判据的实现不是恰好一份（现 $OA_DEF）：两处实现就会长成两套口径"; fail=1; }
[ "$OA_CALL" = 1 ] || { echo "× OC-a 写了判据但没接进 audit()（现 $OA_CALL 个调用点）：不接 = 这一档永远不报，是空转"; fail=1; }
[ "$OA_FLAG" -ge 1 ] || { echo "× OC-a 门槛写死在代码里（--min-cover 现 $OA_FLAG）：换 dpi/换屏就不能调了"; fail=1; }
[ "$OB" = 1 ] || { echo "× OC-b 坏 fixture 报的遮挡不是恰好 1 处（现 $OB）：判据要么钝了要么在乱报"; fail=1; }
[ "$OB_NAME" = 1 ] || { echo "× OC-b 报出来的那一处没点名「回工作台作业」（现 $OB_NAME）：真机被压住的就是这颗按钮"; fail=1; }
[ "$OC" = 0 ] || { echo "× OC-c 好 fixture 报 $OC 处：让高写法本身被读成遮挡，判据不能这样"; fail=1; }
[ "$OD" = 4 ] || { echo "× OC-d 四份真机 dump 合计遮挡不是 4（现 $OD，命中 $OD_FILES 份）：这一档在真实几何上不咬了"; fail=1; }
[ "$OD_FILES" = 4 ] || { echo "× OC-d 只有 $OD_FILES/4 份真机 dump 有读数：合计对得上但来源不对，反证不采信"; fail=1; }
[ "$OE_RESERVE" = 4 ] || { echo "× OC-e 导航页给胶囊让高的地方不是 4（现 $OE_RESERVE：bottomReserve 三个分支 + 到站格那一句 padding）：少一处就是胶囊又压回按钮或压回高德那条剩余里程"; fail=1; }
[ "$OE_PAD" = 1 ] || { echo "× OC-e 到站格没有浮在胶囊之上（现 $OE_PAD）：这一格贴屏幕底就是被压的那一版"; fail=1; }
[ "$OF_REG" = 1 ] || { echo "× OC-f 选点屏没把功能按钮交给胶囊（现 $OF_REG）：反证 $OF_PROOF 处 CapsuleAction("; fail=1; }
[ "$OF_FAB" = 0 ] || { echo "× OC-f 地图上那枚自绘白方块回来了（现 $OF_FAB 处「回到当前位置」）：两处浮层会互相压"; fail=1; }
[ "$OF_CTA" = 0 ] || { echo "× OC-f 面板末尾那颗通栏「确认选择此位置」回来了（现 $OF_CTA）：同一个动作两处出口，压住的是内容"; fail=1; }
[ "$OG" = 0 ] || { echo "× OC-g 本轮真机 dump（$OG_N 份）里报出 $OG 处遮挡：浮层又压回内容了，这不是 fixture 的事，是屏幕上的事"; fail=1; }

if [ "$fail" = 0 ]; then
  echo "浮层遮挡判据通过（实现 $OA_DEF/调用 $OA_CALL/坏 $OB/好 $OC/旧真机 $OD 处 4 份/新真机 $OG_N 份合计 $OG/导航让高 $OE_RESERVE/选点 $OF_REG）"
else
  echo "浮层遮挡判据失败"
fi
exit "$fail"
