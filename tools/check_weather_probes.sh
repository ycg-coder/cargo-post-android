#!/usr/bin/env bash
# #42「天气提醒按角色分文案 + 司机端显示」的结构判据（配 tools/weather_negative_controls.sh 的行为负控）
# 用法：bash tools/check_weather_probes.sh        退出码非 0 = 链路破了纪律
# 判据写法：**按内容/结构过滤，不按行号**（行号会漂）。
# 基线是本轮实测出来的，不是推算的；0 命中的那几项由 weather_negative_controls.sh 反证过"能红"。
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
cd "$ROOT/app/src/main/java" || { echo "找不到 $ROOT/app/src/main/java"; exit 2; }

KT=(--include=*.kt)
REPO=com/shanxiang/buspost/data/repo/Repo.kt
WB=com/shanxiang/buspost/ui/driver/WorkbenchScreen.kt
WC=com/shanxiang/buspost/ui/common/WeatherCard.kt
DWT=com/shanxiang/buspost/ui/driver/DriverWeatherTip.kt

# W-a 按角色分档的那个纯函数只有一处生产者（页面自己不再另编文案）
WA=$(grep -rn "${KT[@]}" "internal fun weatherTipsFor" . | wc -l | tr -d ' ')

# W-b 两个天气生产者（高德实况 + open-meteo）都必须同时填两份说法。
#   只填一边 = 另一半回到"司机听寄件人的话"；这一项是收紧型改动，拒绝侧由 W-c/W-h 钉。
WB1=$(grep -c "tips = userTip" "$REPO")
WB2=$(grep -c "driverTips = driverTip" "$REPO")

# W-c 旧的那一份单文案（六条清一色货运口径）不得回潮
WC0=$(grep -rn "${KT[@]}" "farmTips(" . | wc -l | tr -d ' ')

# W-d 司机端那一栏真的挂在工作台的滚动列表上（不是只写了个没被调用的 Composable）
# ⚠ 按"是不是注释行"过滤：把 `item(key = "weather") { … }` 整行注释掉，代码上就是**没有这一栏**了，
#   但文本还在文件里。负控 WS-3 实测的就是这个形状 —— 不过滤等于把"摘掉"读成"还在场"。
WD1=$(grep -n 'item(key = "weather")' "$WB" | grep -vE '^[0-9]+: *(//|\*)' | wc -l | tr -d ' ')
WD2=$(grep -n "DriverWeatherNotice(" "$WB" | grep -vE '^[0-9]+: *(//|\*)' | wc -l | tr -d ' ')

# W-e/W-f 闸门与显示都得接线：只有文件在场而没人调用，等于机制没上岗（同样滤注释行）
WE=$(grep -n "DriverWeatherTip.shouldFetch(" "$WB" | grep -vE '^[0-9]+: *(//|\*)' | wc -l | tr -d ' ')
WF=$(grep -n "DriverWeatherTip.lineOf(" "$WB" | grep -vE '^[0-9]+: *(//|\*)' | wc -l | tr -d ' ')

# W-g 15 分钟这个数字只能有一个出处（页面/服务里再写一个就是第二套刷新口径）
WG=$(grep -rn "${KT[@]}" "15 \* 60 \* 1000L" . | wc -l | tr -d ' ')

# W-h 用户侧（首页天气卡）只读用户那份；读了 driverTips 就是两屏说同一句话，#42 直接归零
WH=$(grep -rn "${KT[@]}" "driverTips" com/shanxiang/buspost/ui/user com/shanxiang/buspost/ui/common | wc -l | tr -d ' ')
WH2=$(grep -c "weather.tips" "$WC")

# W-i 空态生产者仍然两份都留空（天气没取到时页面不出这一栏，而不是兜一句"注意安全"）
WI1=$(grep -c 'tips = "",' "$REPO")
WI2=$(grep -c 'source = "未取到"' "$REPO")

# W-j 取数点仍只有两处生产者 + 一处调用（新增第三处"就地编天气"就要红）
WJ=$(grep -rn "${KT[@]}" "weatherTipsFor(" . | grep -v "internal fun" | grep -vE ":[0-9]+: *\*" | wc -l | tr -d ' ')

echo "W-a 分档函数生产者      = $WA （目标 1）"
echo "W-b 两生产者各填两份    = tips $WB1 / driverTips $WB2 （目标 2 / 2）"
echo "W-c 旧单份文案 farmTips = $WC0 （目标 0）"
echo "W-d 司机端那一栏        = item $WD1 / Notice $WD2 （目标 1 / 2）"
echo "W-e 闸门接线            = $WE （目标 1）"
echo "W-f 显示接线            = $WF （目标 1）"
echo "W-g 15 分钟数字出处      = $WG （目标 1：只有 DriverWeatherTip.kt）"
echo "W-h 用户侧读 driverTips = $WH （目标 0）；WeatherCard 读 weather.tips = $WH2 （目标 2）"
echo "W-i 空态两份留空        = tips空 $WI1 / 未取到 $WI2 （目标 1 / 1）"
echo "W-j 分档函数调用点      = $WJ （目标 2：高德 + open-meteo）"

fail=0
[ "$WA" = 1 ] || { echo "× W-a 分档函数不是恰好一处生产者（现 $WA）：多了=有人另编一套角色文案，少了=两份说法没出处"; fail=1; }
[ "$WB1" = 2 ] || { echo "× W-b 用户那份不是两个生产者都填（现 $WB1）：漏一个来源，那一屏的用户提示就变空"; fail=1; }
[ "$WB2" = 2 ] || { echo "× W-b 司机那份不是两个生产者都填（现 $WB2）：司机端拿不到自己的说法，#42 只做完一半"; fail=1; }
[ "$WC0" = 0 ] || { echo "× W-c farmTips 回潮（现 $WC0）：又出现一份不分角色的货运口径"; fail=1; }
[ "$WD1" = 1 ] || { echo "× W-d 司机端天气栏不在场（现 $WD1）：#42 那半句「司机端根本不显示天气」重新成立"; fail=1; }
[ "$WD2" = 2 ] || { echo "× W-d DriverWeatherNotice 不是恰好 1 调用 + 1 定义（现 $WD2）"; fail=1; }
[ "$WE" = 1 ] || { echo "× W-e 15 分钟闸门没接在回读里（现 $WE）：要么每刷一次打一次 REST，要么根本没取数"; fail=1; }
[ "$WF" = 1 ] || { echo "× W-f 显示没走 lineOf（现 $WF）：闸门与显示脱钩，mock 空态会被显示成真实天气"; fail=1; }
[ "$WG" = 1 ] || { echo "× W-g 15 分钟出现在 $WG 处：页面/服务写了第二个刷新口径"; fail=1; }
[ "$WH" = 0 ] || { echo "× W-h 用户侧读到 driverTips（现 $WH）：两屏又变成同一句话"; fail=1; }
[ "$WH2" = 2 ] || { echo "× W-h 首页卡读 weather.tips 的处数不是 2（现 $WH2）：那份文案的出口被改动"; fail=1; }
[ "$WI1" = 1 ] || { echo "× W-i 空态生产者不再留空（现 $WI1）：没取到天气时开始编提示"; fail=1; }
[ "$WI2" = 1 ] || { echo "× W-i 空态来源标签缺失（现 $WI2）：那一路没返回却被显示成有来源"; fail=1; }
[ "$WJ" = 2 ] || { echo "× W-j 分档函数调用点不是 2（现 $WJ）：多了=第三个天气生产者，少了=某一来源不再出提示"; fail=1; }

if [ "$fail" = 0 ]; then echo "天气分角色判据通过"; else echo "天气分角色判据失败"; fi
exit "$fail"
