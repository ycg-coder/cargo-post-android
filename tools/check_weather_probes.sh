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

# W-j 天气→建议这张表：生产者仍只有两处（高德 / open-meteo 各一，都在 Repo.kt 里）；
#      读它的人从 2 变 3 是 D-158 有意加的 —— 推送不再自己抄第二张表，而是来读这同一张。
#      所以这一格拆成两条：生产者数一多就是"第三处就地编天气"，读者数一多也要说清是谁在读。
WJ=$(grep -rn "${KT[@]}" "weatherTipsFor(" . | grep -v "internal fun" | grep -vE ":[0-9]+: *\*" | grep -c 'data/repo/Repo.kt' || true)
WJR=$(grep -rn "${KT[@]}" "weatherTipsFor(" . | grep -v "internal fun" | grep -vE ":[0-9]+: *\*" | wc -l | tr -d ' ')

echo "W-a 分档函数生产者      = $WA （目标 1）"
echo "W-b 两生产者各填两份    = tips $WB1 / driverTips $WB2 （目标 2 / 2）"
echo "W-c 旧单份文案 farmTips = $WC0 （目标 0）"
echo "W-d 司机端那一栏        = item $WD1 / Notice $WD2 （目标 1 / 2）"
echo "W-e 闸门接线            = $WE （目标 1）"
echo "W-f 显示接线            = $WF （目标 1）"
echo "W-g 15 分钟数字出处      = $WG （目标 1：只有 DriverWeatherTip.kt）"
echo "W-h 用户侧读 driverTips = $WH （目标 0）；WeatherCard 读 weather.tips = $WH2 （目标 2）"
echo "W-i 空态两份留空        = tips空 $WI1 / 未取到 $WI2 （目标 1 / 1）"
echo "W-j 分档函数生产者/读者 = $WJ / $WJR （目标 2 / 3：生产者是高德与 open-meteo 各一处，都在 Repo.kt；第 3 处是 D-158 让推送来读同一张表，不再自己抄一份）"

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
[ "$WJ" = 2 ] || { echo "× W-j 天气生产者不是 2（现 $WJ）：多了=第三处就地编天气，少了=某一来源不再出提示"; fail=1; }
[ "$WJR" = 3 ] || { echo "× W-j 读这张表的总数不是 3（现 $WJR：两处生产者 + 推送）：多一处就是有人又起了第二张天气→建议表"; fail=1; }

# W-k 限流当天的"这组数是谁给的"（D-173）。三格合起来反的是一件事：
#     高德天气被限流时，旧写法 `status != "1" → return null` **把错误码丢掉**，open-meteo 顶上，
#     而 `WeatherCard` 的 meta 那行优先显示"更新于 HH:mm"、把 `source` 整行盖掉
#     ⇒ 屏上没有任何一处说得出"这组数来自 open-meteo、高德为什么没给"。
#     ⚠ `Repo.kt` 里那句旧注释"界面已经明写来源高德天气"当场是假的（K5 钉它不许回来）。
# ⚠ K1 的锚点必须带上参数表：只写 `internal fun amapWeatherFailureOf` 时，把它改名成
#   `amapWeatherFailureOfRenamed` 仍然是"恰好 1"（负控 WK-2 第一轮实测就是这么绿的）——
#   改名等于把翻译器摘掉，但 grep 的形状没变。带上 `(live: JSONObject)` 之后改名念 0。
K1=$(grep -c "internal fun amapWeatherFailureOf(live: JSONObject)" "$REPO")
K2=$(grep -c "null to " "$REPO")
K3=$(sed -n '/private suspend fun amapWeather/,/^    private suspend fun openMeteoWeather/p' "$REPO" \
  | grep -E "return null" | grep -vc "null to")
K4S=$(grep -c "weather.source" "$WC")
K4P=$(grep -c "primaryFailure" "$WC")
K4OLD=$(grep -c "updateTime.isNotEmpty() -> updateTime" "$WC")
K5=$(grep -c "界面已经明写来源" "$REPO")
K5P=$(grep -c "D-173" "$REPO")
printf 'W-k 失败码翻译器/带因返回/裸 null/来源露出/主因露出/旧盖法/旧假注释 : %s/%s/%s/%s/%s/%s/%s（目标 1/≥5/0/≥1/≥2/0/0）\n' \
  "$K1" "$K2" "$K3" "$K4S" "$K4P" "$K4OLD" "$K5"
[ "$K1" = 1 ] || { echo "× W-k 高德失败码翻译器不是恰好一份（现 $K1）：两份映射表迟早对同一个码说两句不同的话"; fail=1; }
[ "$K2" -ge 5 ] || { echo "× W-k 只有 $K2/5 处失败带上了原因（应 5：没区域码/没配 key/状态非 1/没给 lives/抛异常）：漏一处就是又回到静默 null"; fail=1; }
[ "$K3" = 0 ] || { echo "× W-k amapWeather 里还有 $K3 处裸 return null：错误码在那一刻被丢掉，屏上再怎么改也说不清是谁没给"; fail=1; }
[ "$K4S" -ge 1 ] || { echo "× W-k 天气卡不再显示 source：限流当天没人知道这组数来自哪一路"; fail=1; }
[ "$K4P" -ge 2 ] || { echo "× W-k 天气卡没把 primaryFailure 露出来（现 $K4P，要 ≥2：兜底态与降级态各一处）：换源这件事又变回静默"; fail=1; }
[ "$K4OLD" = 0 ] || { echo "× W-k 旧写法回来了（updateTime 一非空就顶掉整行 meta）：来源永远显示不出来"; fail=1; }
[ "$K5" = 0 ] || { echo "× W-k 那句假注释「界面已经明写来源」又回到 Repo.kt：注释承诺了一件屏上没有的事，比没注释更坏"; fail=1; }
[ "$K5P" -ge 1 ] || { echo "× W-k Repo.kt 里没有 D-173 的更正说明：K5 那个 0 就成了『谁都没碰过这段』而不是『改过且不许回退』"; fail=1; }

# W-l 逆地理那一路的"为什么没拿到区域码"（D-173 第二格，与 W-k 同一族）。
#     `reverseGeo` 旧写法五种失败全回同一个 `EMPTY`：限流、没配 key、报文缺字段、抛异常在调用方眼里一模一样，
#     于是天气卡只能说"还没拿到区域码，没去问高德天气"—— 那是本机压根没定位到时才成立的话。
#     高德逆地理被限流当天，这句话把服务商的配额问题写成了本机的定位问题。
L1=$(grep -c "internal fun amapInfocodeText" "$REPO")
# ⚠ 只数 `EMPTY.copy(` 不数 `EMPTY.copy(failure =`：那两处"报文缺字段"的分支写成了换行形式，
#   按后者数会念 3 而真实是 5 —— 判据读到的数必须与它声称在数的那五件事一一对应（§八 ㉗ 同族）。
L2=$(sed -n '/suspend fun reverseGeo/,/suspend fun walkRoute/p' "$REPO" | grep -c "ReverseGeo.EMPTY.copy(")
L3=$(sed -n '/suspend fun reverseGeo/,/suspend fun walkRoute/p' "$REPO" \
  | grep -E "return ReverseGeo.EMPTY" | grep -vc "copy")
L4=$(grep -rn "${KT[@]}" "rg.failure" . | wc -l | tr -d ' ')
# 注释行里也会出现这个词（KDoc 指着它），不滤掉就把"写在说明里"读成"接在线上"——与 W-d 同一个坑。
L5=$(grep -n "noAdcodeReasonOf" "$REPO" | grep -vE '^[0-9]+: *(//|\*)' | wc -l | tr -d ' ')
printf 'W-l 共用码表/逆地理带因/裸 EMPTY/调用点透传/两成因分话 : %s/%s/%s/%s/%s（目标 1/≥5/0/≥3/2）\n' \
  "$L1" "$L2" "$L3" "$L4" "$L5"
[ "$L1" = 1 ] || { echo "× W-l 高德失败码表不是恰好一份（现 $L1）：天气与逆地理迟早对同一个 infocode 说两句不同的话"; fail=1; }
[ "$L2" -ge 5 ] || { echo "× W-l 逆地理只有 $L2/5 处失败带上了原因（应 5：没配 key/状态非 1/没给 regeocode/没给 addressComponent/抛异常）：漏一处天气那一格就在替限流说假话"; fail=1; }
[ "$L3" = 0 ] || { echo "× W-l reverseGeo 里还有 $L3 处裸 return EMPTY：失败原因在那一刻被丢掉"; fail=1; }
[ "$L4" -ge 3 ] || { echo "× W-l 只有 $L4 个天气调用点把逆地理的原因传了下去（首页/工作台/推送三处）：漏一处那一屏还是说『还没拿到区域码』"; fail=1; }
[ "$L5" = 2 ] || { echo "× W-l 空区域码的两种成因分话不是『一处定义 + weather 一处调用』（现 $L5）：两份话或没人用，都是回到一句话说尽两种成因"; fail=1; }

# W-m 正向地理编码那一格（D-173 第三格）。`forwardGeo` 旧写法六种失败全回 `null`，
#     于是"高德答了但没这个地址"与"高德压根没答（限流/key/网络）"共用一句
#     "未找到该地址，请检查输入是否正确"—— 那句话把原因**指到用户输入上**：
#     配额用完那天村民会一遍遍改地址，而改多少遍都不会好。三态必须各说各的话。
AS=com/shanxiang/buspost/ui/user/AddressSearchScreen.kt
# 与 check_capsule_nav_probes.sh 同一口径：注释行里出现过的词不算"接在线上"
one_count() { grep -nE "$1" "$2" 2>/dev/null | grep -vE '^[0-9]+: *(//|\*)' | wc -l | tr -d ' '; }
M1=$(one_count 'data class GeoAttempt\(' "$REPO")
M2=$(sed -n '/suspend fun forwardGeo/,/suspend fun weather/p' "$REPO" | grep -cE 'return null$|\) return null')
M3=$(sed -n '/suspend fun forwardGeo/,/suspend fun weather/p' "$REPO" | grep -c 'GeoAttempt(failure =')
M4=$(grep -rn "${KT[@]}" "attempt.failure" . | wc -l | tr -d ' ')
M5=$(grep -rn "${KT[@]}" 'ifEmpty { "未找到该地址' . | wc -l | tr -d ' ')
M6=$(one_count 'ErrorState\(vm.searchFailure' "$AS")
M6B=$(one_count '高德答了，但里面没有这一条' "$AS")
printf 'W-m 三态类型/裸 null/带因返回/调用点透传/两屏分话/没问到走错误态/没匹配措辞 : %s/%s/%s/%s/%s/%s/%s（目标 1/0/7/≥3/2/1/1）\n' \
  "$M1" "$M2" "$M3" "$M4" "$M5" "$M6" "$M6B"
[ "$M1" = 1 ] || { echo "× W-m 三态结局类型不是恰好一份（现 $M1）：两份 GeoAttempt 就会有两套『什么算没问到』"; fail=1; }
[ "$M2" = 0 ] || { echo "× W-m forwardGeo 里还有 $M2 处裸 return null：原因在那一刻被丢掉，屏上再怎么改也说不清是谁的错"; fail=1; }
[ "$M3" = 7 ] || { echo "× W-m 带因返回不是恰好 7 处（现 $M3；应 7：地址空/没配 key/状态非 1/没给 geocodes 数组/location 空/location 不成对/抛异常）：漏一处就又回到『请检查输入是否正确』"; fail=1; }
[ "$M4" -ge 3 ] || { echo "× W-m 只有 $M4 个调用点读了 attempt.failure（地址查公交/寄件选址/寄货表单三处）：漏一处那一屏还是怪用户"; fail=1; }
[ "$M5" = 2 ] || { echo "× W-m 『没问到 vs 没匹配』的分话不是恰好两处（现 $M5）：寄件那两屏少一处就还在说『请检查输入是否正确』"; fail=1; }
{ [ "$M6" = 1 ] && [ "$M6B" = 1 ]; } || { echo "× W-m 地址查公交那一屏没把三态分开（错误态 $M6 / 没匹配措辞 $M6B）：限流当天它还得把服务商的事写成用户输入错"; fail=1; }

if [ "$fail" = 0 ]; then echo "天气分角色判据通过"; else echo "天气分角色判据失败"; fi
exit "$fail"
