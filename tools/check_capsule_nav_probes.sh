#!/usr/bin/env bash
# 全局悬浮胶囊导航栏的结构判据（配 tools/capsule_nav_negative_controls.sh）。
#
# 管七件事：
#   CN-a 胶囊只有一份实现、只在根上画一处；
#   CN-b 登记点恰好四屏（两个壳 + 实时公交 + 车道级导航），多一屏少一屏都要同轮改本行；
#   CN-c 旧的那条通栏底部 tab 已经撤干净（BusTabBar / TabItem / bottomBar 三个名字都不许再出现在**代码**里）；
#   CN-d 以前被 BA-c 豁免掉、其实没有可见退出口的两屏（BUS / DRIVER_GUIDE）现在各有一格；
#   CN-e 三条收口规则（上限 / 必须有标签 / 禁用必须带原因）只有 capsuleItemsOf 这一份实现；
#   CN-f 车道级导航那一屏：高德自带面板关掉、重复的"退出"药丸与通栏底部按钮条撤掉；
#   CN-g 契约用例条数（下限等于现读）；
#   CN-h 格子等宽下限（观感那一半）：一份定义、一处使用、不低于 76dp、且不许有第二处在别处定宽度。
#
# ⚠ 本簿每一条 `grep -r` 都要过"去注释"这一道（D-156 第一轮就被自己的注释顶红四次：
#   注释里写一句 `CapsuleActions(...)` 就被数成一个登记点）。判据认的是代码形状，不是散文。
#
# 用法：bash tools/check_capsule_nav_probes.sh        退出码非 0 = 这一格被拆了
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
cd "$ROOT" || exit 2
M=app/src/main/java/com/shanxiang/buspost
CN=$M/ui/common/CapsuleNav.kt
RT=$M/ui/BusPostRoot.kt
BS=$M/ui/user/BusScreen.kt
GD=$M/ui/driver/DriverGuideScreen.kt
CT=app/src/test/java/com/shanxiang/buspost/ui/common/CapsuleNavContractTest.kt
CMT='(^[^:]+:[0-9]+:[[:space:]]*(//|\*|/\*))|(^[0-9]+:[[:space:]]*(//|\*|/\*))'

for f in "$CN" "$RT" "$BS" "$GD" "$CT"; do
  [ -f "$f" ] || { echo "× 缺 $f：判据没有输入，本轮读数不采信"; exit 2; }
done

# 只数代码行：$1 = grep -E 的形，其余 = 目录或文件
# ⚠ 必须 -E：BRE 里的 `\(` 是分组起始，`grep -rn 'CapsuleAction\('` 直接报 Unmatched 并返回 0 行 ——
#   那会让上面两个"目标 0"读成通过（本簿第一轮就是这么被顶红的，靠 CC_PROOF 才露出来）。
code_count() {
  local pat="$1"; shift
  grep -rnE "$pat" "$@" --include=*.kt 2>/dev/null | grep -vE "$CMT" | wc -l | tr -d ' '
}
one_count() {
  local pat="$1"; shift
  grep -nE "$pat" "$@" 2>/dev/null | grep -vE "$CMT" | wc -l | tr -d ' '
}

# CN-a 一份实现、一处画它
CA_DEF=$(one_count '^fun CapsuleNavOverlay\(' "$CN")
CA_USE=$(one_count 'CapsuleNavOverlay\(' "$RT")

# CN-b 登记点恰好四屏（定义那一处不算：它在 CapsuleNav.kt 里且形如 fun CapsuleActions(）
CB_REG=$(grep -rn 'CapsuleActions(' "$M" --include=*.kt | grep -vE "$CMT" | grep -v 'fun CapsuleActions' | grep -v 'CapsuleNav.kt' | wc -l | tr -d ' ')
CB_HOST=$(one_count 'LocalCapsuleHost provides' "$RT")

# CN-c 旧通栏撤干净（正对照：同一套扫描在四屏里数得出十几处 CapsuleAction(）
CC_TABBAR=$(code_count 'BusTabBar|TabItem' "$M")
CC_BOTTOM=$(code_count 'bottomBar' "$M")
CC_PROOF=$(code_count 'CapsuleAction\(' "$M")

# CN-d 两屏各补上一格看得见的出口
CD_BACK=$(one_count 'CapsuleAction\("back", "返回"' "$BS")
CD_EXIT=$(one_count 'key = "exit"' "$GD")

# CN-e 三条规则只有这一份实现
CE_MAX=$(one_count 'const val CAPSULE_MAX' "$CN")
CE_RULE=$(one_count 'fun capsuleItemsOf' "$CN")
CE_ELSE=$(grep -rn 'CAPSULE_MAX' "$M" --include=*.kt | grep -v 'CapsuleNav.kt' | grep -vE "$CMT" | wc -l | tr -d ' ')

# CN-f 导航那一屏：高德面板关掉、重复出口与通栏按钮条撤掉
CF_LAYOUT=$(one_count 'o\.isLayoutVisible = false' "$GD")
CF_ONEXIT=$(one_count 'onExit' "$GD")
CF_BAR=$(one_count 'GuideBottomBar' "$GD")

# CN-g 用例条数
# CN-h 格子等宽下限（观感那一半）：常量一份、用在 defaultMinSize 一处、且不低于触控下限
CH_DEF=$(one_count '^internal const val CAPSULE_CELL_MIN_WIDTH_DP' "$CN")
CH_USE=$(one_count 'minWidth = CAPSULE_CELL_MIN_WIDTH_DP.dp' "$CN")
CH_NUM=$(grep -oE 'CAPSULE_CELL_MIN_WIDTH_DP = [0-9]+' "$CN" | grep -oE '[0-9]+$' | head -1)
CH_OUT=$(grep -rn 'CAPSULE_CELL_MIN_WIDTH_DP' "$M" --include=*.kt | grep -v 'CapsuleNav.kt' | wc -l | tr -d ' ')

CG=$(grep -c '^    @Test' "$CT")

fail=0

[ "$CA_DEF" = 1 ] || { echo "× CN-a 胶囊实现不是恰好一份（现 $CA_DEF）：两处实现就会长成两套答案"; fail=1; }
[ "$CA_USE" = 1 ] || { echo "× CN-a 根上画它的地方不是恰好一处（现 $CA_USE）：多一处就是屏幕上会叠两条"; fail=1; }
[ "$CB_REG" = 5 ] || { echo "× CN-b 登记胶囊的屏数不是 5（现 $CB_REG，预期 两个壳 + BusScreen + DriverGuideScreen + MapPointPickerScreen）：新增一屏要同轮登记并写清"为什么这一屏的动作非浮不可"，撤一屏要写理由"; fail=1; }
[ "$CB_HOST" = 1 ] || { echo "× CN-b 根上没有提供那一份共享状态（现 $CB_HOST）：页面登记了也没人画"; fail=1; }
[ "$CC_TABBAR" = 0 ] || { echo "× CN-c 旧的通栏底部 tab 又出现 $CC_TABBAR 处：主导航只许有悬浮胶囊一份（正对照 CN-c 的 $CC_PROOF）"; fail=1; }
[ "$CC_BOTTOM" = 0 ] || { echo "× CN-c bottomBar 还在代码里（现 $CC_BOTTOM）：BusScaffold 的底部槽位已撤，留着就是有人往 Scaffold 里塞第二条栏"; fail=1; }
[ "$CC_PROOF" -ge 13 ] || { echo "× CN-c 反证失效：同一套扫描只数出 $CC_PROOF 处 CapsuleAction(，上面那两个 0 不采信"; fail=1; }
[ "$CD_BACK" = 1 ] || { echo "× CN-d 实时公交那一格「返回」不见了（现 $CD_BACK）：这一屏以前靠底部 tab 豁免，现在 tab 撤了"; fail=1; }
[ "$CD_EXIT" = 1 ] || { echo "× CN-d 导航那一格「退出导航」不见了（现 $CD_EXIT）：全屏导航里没出口 = 司机被困住"; fail=1; }
{ [ "$CE_MAX" = 1 ] && [ "$CE_RULE" = 1 ]; } || { echo "× CN-e 收口规则不唯一（上限 $CE_MAX / 规则函数 $CE_RULE）"; fail=1; }
[ "$CE_ELSE" = 0 ] || { echo "× CN-e 有页面自己抄了上限判断（现 $CE_ELSE 处引用 CAPSULE_MAX）：截断口径会长第二套"; fail=1; }
[ "$CF_LAYOUT" = 1 ] || { echo "× CN-f 高德自带面板没关掉（现 $CF_LAYOUT）：它的「退出 / 全程 / 到达时间」会跟我们的胶囊抢同一块地方"; fail=1; }
[ "$CF_ONEXIT" = 0 ] || { echo "× CN-f 顶条那颗重复的退出药丸回来了（现 $CF_ONEXIT 处 onExit）：出口只许在胶囊那一处"; fail=1; }
[ "$CF_BAR" = 0 ] || { echo "× CN-f 通栏底部按钮条 GuideBottomBar 又出现在代码里（现 $CF_BAR）：功能按钮只许在悬浮胶囊里"; fail=1; }
{ [ "$CH_DEF" = 1 ] && [ "$CH_USE" = 1 ]; } || { echo "× CN-h 格子等宽下限不是"一份定义 + 一处使用"（现 $CH_DEF/$CH_USE）：两处写法就会有一处漏掉，两格的胶囊又会歪"; fail=1; }
[ "${CH_NUM:-0}" -ge 76 ] || { echo "× CN-h 格子下限被压到 $CH_NUM dp（要 ≥76）：低于这个数，短文案那一格就跟着内容缩，整枚胶囊歪"; fail=1; }
[ "$CH_OUT" = 0 ] || { echo "× CN-h 有 $CH_OUT 处在 CapsuleNav 之外引用格子宽度：一屏自己改宽度就没有统一观感了"; fail=1; }
[ "$CG" = 7 ] || { echo "× CN-g 契约用例不是 7 条（现 $CG）：下限等于现读，改了要连同本行一起改"; fail=1; }

if [ "$fail" = 0 ]; then echo "悬浮胶囊判据通过（实现 $CA_DEF / 画它 $CA_USE / 登记 $CB_REG 屏 / 旧通栏 $CC_TABBAR / 反证 $CC_PROOF / 用例 $CG）"; else echo "悬浮胶囊判据失败"; fi
exit "$fail"
