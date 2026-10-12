#!/usr/bin/env bash
# §38 到站分钟（ETA）真实化探针：契约口径 = 2026-09-29 逐字段实测的后端 VO 与 AppBusServiceImpl
#   AppBusRespVO.java:11-62 / AppBusNearbyRespVO.java:76-151 / AppBusServiceImpl.java:115-142、:436-437、:623-632、:665、:714-790
# 只按**内容**判据，不写行号 —— 行号会漂，内容不漂（§55）。
# 每轮收工必须实测并把数字抄进 AI_ITERATION_STATE.md 的常驻自检表；没有输出就等于没跑。
# 用法：bash tools/check_eta_probes.sh（可用 BUSPOST_ANDROID 覆盖工程根）
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
cd "$ROOT" || exit 1

A=app/src/main/java/com/shanxiang/buspost
MD="$A/data/model/Models.kt"
RP="$A/data/repo/Repo.kt"
DM="$A/data/repo/DemoData.kt"
AL="$A/notify/AlertEngine.kt"
WG="$A/widget/NextBusWidgetProvider.kt"
BS="$A/ui/user/BusScreen.kt"
HS="$A/ui/user/HomeScreen.kt"
PS="$A/ui/user/ParcelScreen.kt"
TST="app/src/test/java/com/shanxiang/buspost/data/BusEtaContractTest.kt"
# vehicleJson 函数体（演示面两个端点的形状都在这几行里，别拿全文件字数当判据）
VJ=$(sed -n '/private fun vehicleJson/,/^    }$/p' "$DM")

# T-a ETA 家族里任何 null→0 塌缩（§14：没给值不许有业务语义）
ta=$(grep -rnE "(etaMinutes|waitDepartureMinutes|nearestStationEtaMinutes|progress|speedKmh)[a-zA-Z]* *(\?:|elvis) *(0|0\.0)\b" $A | wc -l)
# T-b 可空哨兵读法的总用量：Models.kt 里声明 1 + 公交侧使用若干（退回 multiNum 就等于把"没给"读成 0）
tb=$(grep -c "multiNumOrNull" "$MD")
# T-c BusVehicle 九个数值字段必须是可空类型（只数 data class 体内，别把别的类的同名字段算进来）
#    ⑬ 起第 9 个是 etaToNextStationMinutes（AppBusRespVO.java:49-50 Double，可空）
tc=$(sed -n '/^data class BusVehicle/,/^) {/p' "$MD" | grep -cE 'val (speedKmh|etaMinutes|waitDepartureMinutes|distanceToNextStationKm|nearestStationEtaMinutes|stopsToNearestStation|progress|distanceKm|etaToNextStationMinutes): (Int\?|Double\?)')
# T-d1 客户端自编进度（旧实现 1 - eta/40）必须为 0
td1=$(grep -rnE "etaMinutes[a-zA-Z!?.]* *[/*+-] *(40|60)\b" $A | wc -l)
# T-d2 进度条只许由后端 progress 驱动（线路页 + 首页各一处）
td2=$(grep -rn "progress = v.roadProgress" $A/ui/user | wc -l)
# T-e 把 0 当"还没到时间"的旧口径（etaMinutes > 0 / >= 1）在公交与到站提醒路径上必须为 0
#     页面侧（ui/）的同名旧口径属于 SendOrder/TransportLeg/ParcelTrace 那一族 etaMinutes: Int，
#     本轮范围外，列在"残留"里交给下一轮 §38，不混进这条判据。
te=$(grep -rn "etaMinutes > 0\|etaMinutes >= 1" $A/notify $A/widget $A/data | wc -l)
# T-f1 编造的整数码 3（后端只有 0/1/2）不许出现在码表里
tf1=$(grep -cE '"3" *-> *VehicleStatus' "$MD")
# T-f2 2 是"停用"（MonitoringVehicleRespVO:21-22），旧码表把它读成"已到站"
tf2=$(grep -c '"2" -> VehicleStatus.OFF_DUTY' "$MD")
# T-g 中文串硬映射进枚举（后端两个端点都不发中文状态码）
tg=$(grep -rnE '"(在途|空闲|已到站|停用|待发车)" *->' $A | wc -l)
# T-h1 车辆解析出口数：nearby 列表 + 单车详情两处（同处两行也算两处，所以数出现次数）
th1=$(grep -o "BusVehicle.parse(" "$RP" | wc -l)
# T-h2 Repo 里不许再直接构造 BusVehicle
th2=$(grep -n "BusVehicle(" "$RP" | grep -vc "BusVehicle.parse" || true)
# T-i 按离用户远近排序的口径引用点：模型声明 1 + 三处 import + 三处调用
ti=$(grep -rn "byUserDistance" $A | wc -l)
# T-j1 progress 只在 lines 端点出现一次（nearby 的 NearbyBus 没有这个键）
tj1=$(printf '%s' "$VJ" | grep -c '\.put("progress"')
# T-j2 演示面不许再发自造的车辆 routeId（真实车辆 VO 只有 routeName）
tj2=$(printf '%s' "$VJ" | grep -c '"routeId"')
# T-j3 routeProvider 只写后端真会写的标签
tj3=$(printf '%s' "$VJ" | grep -c '"routeProvider", "SCHEDULE"')
# T-j4 演示面"后端给不出"要写成显式 null（与真实 HTTP 体同形），数量必须撑得起三条 state 分支
tj4=$(printf '%s' "$VJ" | grep -o "JSONObject.NULL" | wc -l)
# T-j5 etaMinutes 只有一处写入点，且走的是"有值才写数字"的那条分支
tj5=$(printf '%s' "$VJ" | grep -c '"etaMinutes"')
# T-k 契约测试用例数（只数行首 @Test，被注释掉的 //@Test 不算）
#     ⑬ 之后实测 12 条：下限必须跟着抬，否则"注释掉一条"的负控制会从红变灰（本轮实测到过：
#     用例 9→11 而 pin 还是 9，T-k 的负控制静默失效，直到重跑负控表才发现）。
#     E-1（删自造别名 "eta"）加了一条"只带别名的报文必须读成 null"的用例 ⇒ 12→13，同轮把下限抬到 13。
tk=$([ -f "$TST" ] && grep -cE "^[[:space:]]*@Test" "$TST" || echo 0)
# T-l 用户端与桌面卡片不许裸拼 ETA 数字（null 会被打印成 "null 分钟"），一律走三个文案 getter
tl=$(grep -rnE '\$\{?(v|a|it|vehicle)\.etaMinutes\}?' $A/ui/user $A/widget | wc -l)
# T-m "没给"的文案各就各位：到站时间未给 / 暂无到站预估 / 距下一站未给 / 来源未识别
# 基数 4 → 「逐条各 ≥1」的收紧（2026-10-12，续七）：
#   ① 旧口径把四个概念并成一条 `|` 正则数总数，"删掉其中一条"只要别处补一条就照旧绿 —— 判别力不够；
#   ② `距下一站未给` 在 7d8b819（全面视觉升级/话术去技术化）里被改写成 `Models.kt:738` 的
#      「距下一站暂未知」，语义未变（null 说未知、0.0 是真给了 0），判据跟着换点名对象，**不是放松闸门**。
TM_HITS=0
for t in "到站时间未给" "暂无到站预估" "距下一站暂未知" "来源未识别"; do
    n=$(grep -cF "$t" "$MD")
    [ "$n" -ge 1 ] || echo "  × T-m 缺一条如实文案：$t（Models.kt 里 0 命中）"
    TM_HITS=$((TM_HITS + (n > 0 ? 1 : 0)))
done
tm=$TM_HITS
# T-n 桌面卡片与页面同口径
tn=$(grep -c "etaValueText\|etaUnitText\|etaCaptionText" "$WG")
# T-o1 到站提醒过滤掉"没给 ETA"的行（旧口径 etaMinutes in 1..10 会把 null 读成 0）
to1=$(grep -c "mapNotNull" "$AL")
# T-o2 页面侧同样先判 null 再比较（⑫ 之后寄货侧读的是 carrierEtaMinutes，同一个口径换个字段名，不能漏判）
to2=$(grep -rcE "[eE]taMinutes != null" "$PS" "$HS" | awk -F: '{s+=$2} END {print s}')
# T-p 真实键名读得到：busId / plateNo（旧候选名列表里没有它们，对接真后端时车辆编号与车牌静默变空）
# 两个键名**分开数**：合并成一条 `busId|plateNo` 时基线是 3，把 :1374 换成 plateNo 真键读法后涨到 4，
# 于是"删掉 busId 读法"的注入仍然 ≥3 ⇒ 注入空跑、判据没判别力（D-040 同族）。分开设闸才各自管得住。
tp=$(grep -cE 'multiStr\("busId"' "$MD")
tp2=$(grep -cE 'multiStr\("plateNo"' "$MD")
# T-q 到站提醒的数据源缺口有记录（AppSendArrangementRespVO 里没有 etaMinutes，审计 §68）
tq=$(grep -c "审计 §68" "$MD")
# ---- 以下 T-r…T-z 是第 ⑫ 轮把同一味病在**货运侧**收口后新增的判据 ----
# 契约口径（2026-09-29 实测，审计 §71）：
#   AppSendOrderRespVO:96 etaMinutes 只在 /send/track 的 fillEta 里、且仅当预计到达时间在未来才写（AppSendController:246-250）
#   AppSendOrderRespVO:106 carrierEtaMinutes 是 GeoDistanceUtil 的 Haversine ÷ 25km/h 估算，整块可能不填（:443-457）
#   /send/page 只走 fillCarrierBatch（:142），所以列表项里连 etaMinutes 这个键都不存在
#   AppSendLegRespVO / AppDriverLegRespVO 两个段契约都没有 etaMinutes 键
# T-r 货运侧 ETA 家族的字段声明必须是可空类型（按类体范围数，别把公交侧 BusVehicle/Arrangement 的同名字段串进来）
tr_send=$(sed -n '/^data class SendOrder/,/^) {/p' "$MD" | grep -cE 'val (etaMinutes|carrierEtaMinutes): Int\?')
tr_leg=$(sed -n '/^data class TransportLeg/,/^) {/p' "$MD" | grep -cE 'val etaMinutes: Int\?')
tr_trace=$(sed -n '/^data class ParcelTrace/,/^) {/p' "$MD" | grep -cE 'val etaMinutes: Int\?')
tr_guide=$(sed -n '/^data class RouteGuide/,/^) {/p' "$MD" | grep -cE 'val etaMinutes: Int\?')
tr_prev=$(sed -n '/^data class RoutePreview/,/^) {/p' "$MD" | grep -cE 'val (distanceKm|durationMinutes): (Double\?|Int\?)')
# T-s 解析层不许再用 multiNum 读**到站分钟这一族**（退回它就等于把"没给"读成 0）；注释里引用旧写法不算破口
#     注意别把 TransportLeg.durationMinutes 混进来 —— 那是"计划时长"，同病但另属一段契约，列在下面"残留"里由下一轮收。
ts=$(grep -rnE 'multiNum\("(etaMinutes|carrierEtaMinutes|eta|minutes)"' $A --include=*.kt | grep -vE ':[0-9]+: *(\*|//)' | grep -v 'durationMinutes' | wc -l)
# T-t 班次 ETA 的候选键名里不许再混进 carrierEtaMinutes（直线估算与班次到达时间是两件事）
#     这里必须 -F：BRE 里 \(/\) 是分组而不是字面圆括号，写成转义形式会永远匹配不到（负控制实测出来过）
tt=$(grep -rFn 'multiNumOrNull("etaMinutes", "eta", "carrierEtaMinutes")' $A | wc -l)
# T-u 页面/提醒里把 0 当"还没到时间"的旧口径必须为 0（⑫ 之后这条覆盖 ui/ 全域，不再只盯 notify）
tu=$(grep -rn "etaMinutes > 0" $A/ui $A/notify | wc -l)
# T-v 承运分钟数是直线估算，展示文案必须带口径标注（只数**界面字符串**，注释里再多次也不算）
#     实测 5 处 = 提醒 1 + 首页 1 + 寄货页 3（AlertEngine:101 / HomeScreen:234 / ParcelScreen:254,342,444）
tv=$(grep -rn "（直线估算）" "$AL" "$HS" "$PS" | grep -vE ':[0-9]+: *(\*|//)' | wc -l)
# T-v2 闭包判据：把 carrierEtaMinutes 打成"分钟"文案的那一行**必须**同时带口径标注。
#     只数总处数会漏掉"新增一处不带标注的展示"，这条按行配对，删掉任一标注就变红。
tv2=$(grep -rn 'carrierEtaMinutes' $A/ui $A/notify | grep -vE ':[0-9]+: *(\*|//)' | grep '分钟' | grep -vc '（直线估算）')
# T-w1 §68 迁移：到站提醒的真实数据源改成 /send/page
tw1=$(grep -c "Repo.sendOrders()" "$AL")
# T-w2 提醒侧不许再回到 /send/arrangements（那个 VO 没有 ETA 键）
tw2=$(grep -c "myArrangements" "$AL")
# T-x 货运侧 ETA 契约测试用例数（同样只数行首 @Test）
# E-1 轮加了 3 条"自造别名不被采纳 / 真实键照常工作"的货运侧用例 ⇒ 9→12，下限同轮抬到 12
TX="$ROOT/app/src/test/java/com/shanxiang/buspost/data/SendEtaContractTest.kt"
tx=$([ -f "$TX" ] && grep -cE "^[[:space:]]*@Test" "$TX" || echo 0)
# T-y 位置过期（REAL_STALE）不参与提醒与列表：新鲜度是承运 ETA 的前置条件
ty=$(grep -rn 'carrierLocationSource != "REAL_STALE"' $A | wc -l)
# T-z "没给"的如实文案在货运侧各就各位（后端未给到站分钟 / 到站分钟未下发 / 里程未给 / 时长未给）
# 同上收紧 + 同一轮改名对照：
#   到站分钟未下发/未给到站分钟 → `ParcelScreen.kt:393` 「时间暂未知」；
#   里程未给 → `BusScreen.kt:1088`、`SendScreen.kt:599` 「里程暂未知」；
#   时长未给 → `DriverNavScreen.kt:441` 「全程时长暂未知」+ `TraceView.kt`/`TraceScreen.kt` 的「时长暂未知」。
#   ⚠ 但 `TraceScreen.kt` 那两处不是改名，是**整段消失**（旧写法 `dist > 0 才 append`）：
#     "没下发" 与 "这段真 0 公里" 长得一样、行还被静默裁短 ⇒ 本轮把两处补回如实标签，这里才够 4 条。
# 但"文件级在场"不够：本轮实测注入"删掉 TraceScreen 那一处时长暂未知"时，T-z 仍读 4/4
#   （`时长暂未知` 这个词在别处还有），而那恰恰是这本判据要防的形状 —— **某一格自己变静默**。
#   所以再加一层**按整串 `?: "X"` 数出现次数**并钉恰好 **7**（2026-10-12 逐处实测：
#     时间暂未知 1（ParcelScreen.kt:393）／里程暂未知 3（BusScreen.kt:1088、SendScreen.kt:599、TraceScreen.kt:312）
#     ／时长暂未知 2（SendScreen.kt:600、TraceScreen.kt:313）／全程时长暂未知 1（DriverNavScreen.kt:441））。
#   ⚠ 这一行第一次落稿时我写成了"恰好 8"，闸门当场红（现 7）——少的那 1 处是把 `?: "时长暂未知"` 与
#     `?: "全程时长暂未知"` 数重了；照本文件的老规矩（引用任何数字前先 grep 到那一行原文），以 7 为准。
#   少一处=那一格的缺失又不再说话，多一处=有人在别处新造了一格，两者都要当场重新归因。
TZ_HITS=0
TZ_OCC=0
for t in ': "时间暂未知"' ': "里程暂未知"' ': "时长暂未知"' ': "全程时长暂未知"'; do
    n=$(grep -roF "?$t" $A --include=*.kt | wc -l | tr -d ' ')
    [ "$n" -ge 1 ] || echo "  × T-z 货运侧缺一条如实文案：$t"
    TZ_HITS=$((TZ_HITS + (n > 0 ? 1 : 0)))
    TZ_OCC=$((TZ_OCC + n))
done
tz=$TZ_HITS

# ---- 以下 T-aa…T-ag 是第 ⑬ 轮（精确到站分钟 + 三个 distanceKm 口径）新增的判据 ----
# 契约口径（2026-09-29 实测，审计 §72）：
#   AppBusRespVO.java:44 etaMinutes(Integer) / :49-50 etaToNextStationMinutes(Double) / :56 progress
#   —— 前者在 AppBusServiceImpl:130-132 是"剩余进度 × 班次计划时长"= 跑完剩余线路，后者才是到下一站；
#   AppBusNearbyRespVO 的 NearbyBus 有 etaMinutes(:123) 与 distanceKm(:138/:189)，**没有** etaToNextStationMinutes；
#   AppBusLineRespVO.java:27-28 distanceKm(Double)、AppSendQuoteRespVO.java:21-22 distanceKm 是"直线距离×路网系数"可 null、
#   AppDriverLegRespVO.java:58-62 distanceKm(BigDecimal)/durationMinutes(Integer) 都可空；
#   RoutePreviewRespVO 只有 available/distanceKm/durationMinutes/provider/warning 五个键。
#   全模块 app 侧 VO 里**没有**名为 distance/minutes/eta 的字段（实测 grep），所以它们出现在候选名里就是自造别名。
# T-aa 精确到站分钟字段在类体里是可空 Double（退成非空 0 就会显示"约 0 分钟到下一站"）
taa=$(sed -n '/^data class BusVehicle/,/^) {/p' "$MD" | grep -cE 'val etaToNextStationMinutes: Double\?')
# T-ab 精度口径与小程序一致（pages/bus/detail.js:132-137 Math.max(1, Math.ceil(...))），且全工程只有一处
tab=$(grep -c "Math.max(1, kotlin.math.ceil" "$MD")
# T-ac 里程/时长读法里不许混进米制或自造别名键（`("distanceKm","distance")`/`("durationMinutes","minutes")`）
tac=$(grep -rnE 'multiNum(OrNull)?\("(distanceKm|durationMinutes)", *"(distance|meters|minutes)"' $A | grep -vE ':[0-9]+: *(\*|//)' | wc -l)
# T-ad1 七个 distanceKm 声明全部可空（车辆/线路/段/试算/路线预估/可达性/候选方案 D-085）
tad1=$(grep -c "val distanceKm: Double?" "$MD")
# T-ad2 ⑫ 交给本轮的收口：TransportLeg 类体内 distanceKm/durationMinutes 都是可空类型
tad2=$(sed -n '/^data class TransportLeg/,/^) {/p' "$MD" | grep -cE 'val (distanceKm|durationMinutes): (Double\?|Int\?)')
# T-ae 闭包判据：界面凡读 .distanceKm 必须先判 null（?.let / == null / 走模型 getter），否则 null 会被打印成 "null km"
tae=$(grep -rn '\.distanceKm' $A/ui | grep -vE '\?\.|== *null|distanceCaliberText|legPlanText' | wc -l)
# T-af ⑬ 契约测试用例数（口径/精度/缺键 null 三类各钉一遍）
TD="$ROOT/app/src/test/java/com/shanxiang/buspost/data/DistanceCaliberContractTest.kt"
tdc=$([ -f "$TD" ] && grep -cE "^[[:space:]]*@Test" "$TD" || echo 0)
# T-ah 司机位置端点（AppDriverPositionRespVO 只有 longitude/latitude/dataSource）不许被界面读成"在途"
#     注释行要排除（与 T-s/T-v 同一写法）：本轮这条第一次响，响的是我自己写的"旧写法如何如何"说明注释，
#     不是代码 —— 删说明来凑绿是错的，按内容过滤才对。
tah=$(grep -rn '"在途' $A/ui | grep -vE ':[0-9]+: *(\*|//)' | wc -l)
# ---- 以下 T-ai…T-ai3 是 E-1 轮（删掉解析层自造的 ETA 别名键）新增的判据 ----
# 契约口径（2026-09-30 三方实测，审计 §79）：
#   后端 app 侧 VO 没有名为 eta 的字段（Java 字段声明 0 处 / @JsonProperty("eta") 0 处 / Java 源码 "eta" 字符串 0 处，
#   同一套手段下 etaMinutes 命中 3 处：AppBusNearbyRespVO:123、AppBusRespVO:44、AppSendOrderRespVO:96）；
#   小程序 0 处读它；演示面 DemoData 只写 etaMinutes ⇒ 这个键**三面都没有生产者**，是客户端解析层自造的第四个真相源。
# T-ai etaMinutes 读法里带第二个候选名的处数（E-1 删掉的正是这 6 处，含 BusVehicle 那处的裸 "minutes"）
tai=$(grep -rnE 'multiNum(OrNull)?\("etaMinutes", *"' $A | grep -vE ':[0-9]+: *(\*|//)' | wc -l)
# T-ai2 闭包判据：main 源码里裸 "eta" 字面量（注释行按 T-s/T-v/T-ah 的同一口径排除 —— 说明"曾经有别名"的注释不算破口）
tai2=$(grep -rn '"eta"' $A | grep -vE ':[0-9]+: *(\*|//)' | wc -l)
# T-ai3 演示面也不许出现 "eta" 键：钉住"两侧都无生产者"，防止有人给 demo 补一个键来给别名续命
tai3=$(grep -c '"eta"' "$DM")
# T-ag 演示面 lines 分支必须真下发 etaToNextStationMinutes，否则新字段只在单测里活着、页面上永远走不到那条分支
tag=$(printf '%s' "$VJ" | grep -c '\.put("etaToNextStationMinutes"')
# ---- 以下 T-aj…T-aj6 是 F-2 步 2（Minutes 家族五处第二候选名）新增的判据 ----
# 契约口径（2026-09-30 逐处核到后端 VO / 小程序读点 / 演示面写点）：
#   Station.plannedMinutes            ← AppDriverStationRespVO:17、RouteStationRespVO:32、AppBusLineRespVO:54
#   BusVehicle.waitDepartureMinutes   ← AppBusRespVO:47、AppBusNearbyRespVO:126（waitMinutes 只是小程序局部变量名 pages/bus/index.js:449）
#   TransitPlan.minutes               ← OrderTopologyRespVO.Candidate:65 durationMinutes（信封级 totalDurationMinutes:44 口径不同，不许混）
#   ShiftTask.plannedDurationMinutes  ← AppDriverShiftRespVO:24（durationMinutes 属 AppDriverLegRespVO:62 / Leg:92 = 段时长）
#   Reachability.walkMinutes          ← AppSendReachabilityRespVO:32
# 扫描范围按内容取 main 全域 $A，注释行按 T-s/T-v/T-ah/T-ai 的同一口径排除；不写行号（§55）。
TM="$ROOT/app/src/test/java/com/shanxiang/buspost/data/MinutesContractTest.kt"
# T-aj Minutes 家族仍带第二候选名的读法（宽形态：别名在前在后都响）
taj=$(grep -rnE 'multiNum(OrNull)?\([^)]*Minutes[^)]*,' $A --include=*.kt | grep -vE ':[0-9]+: *(\*|//)' | wc -l)
# T-aj2 已删的四个自造别名键在 main 源码里的字面量 —— 闭包判据：换第一候选名、挪到别的读法里也照样响
taj2=$(grep -rnE '"(offsetMinutes|waitMinutes|totalMinutes|minutes)"' $A --include=*.kt | grep -vE ':[0-9]+: *(\*|//)' | wc -l)
# T-aj3 演示面也不许下发这些键：钉住"两侧都无生产者"，防止有人给 demo 补一个键来给别名续命
taj3=$(grep -cE '"(offsetMinutes|waitMinutes|totalMinutes|minutes)"' "$DM")
# T-aj4 五处真实键的单键读法仍在（含 TransportLeg:1069 / RouteGuide:1705 的 durationMinutes —— 同键名不同对象，各自都有生产者）
taj4=$(grep -rnE 'multiNum(OrNull)?\("(plannedMinutes|waitDepartureMinutes|durationMinutes|plannedDurationMinutes|walkMinutes)"\)' $A --include=*.kt | grep -vE ':[0-9]+: *(\*|//)' | wc -l)
# T-aj5 main 里单键 *Minutes 读法总量：删掉真实键会掉下来，新增读法是正常演进，所以只钉下界
taj5=$(grep -rhoE 'multiNum(OrNull)?\("[A-Za-z]*Minutes"\)' $A --include=*.kt | wc -l)
# T-aj6 本轮契约测试用例数（每处一对"真实键仍可用 + 别名不再被采纳"，另加演示面键名同步用例）
taj6=$([ -f "$TM" ] && grep -cE "^[[:space:]]*@Test" "$TM" || echo 0)
# GUARD：grep 计数管道一旦失效，上面三条"预期 0"会集体变成假绿。
#        正控制 —— 别名模式必须在测试源码（负控输入所在地，不在判据扫描范围内）里匹配得到；匹配不到就判红而不是判绿。
tj_guard=$(grep -rhoE '"(offsetMinutes|waitMinutes|totalMinutes|minutes)"' "$TM" 2>/dev/null | wc -l)

echo "T-a  ETA 家族 null→0 塌缩处数        : $ta   （预期 0：§14 没给值不许有业务语义）"
echo "T-b  multiNumOrNull 用量             : $tb   （预期 ≥10：声明 1 + 公交侧可空读法）"
echo "T-c  BusVehicle 可空数值字段         : $tc   （预期 9：speed/eta/wait/nextKm/nearestEta/stops/progress/distanceKm/etaToNextStationMinutes）"
echo "T-d1 客户端自编进度（eta/40）        : $td1  （预期 0）"
echo "T-d2 进度条由后端 progress 驱动      : $td2  （预期 2：线路页 + 首页）"
echo "T-e  公交/提醒路径把 0 当『还没到』  : $te   （预期 0：notify+widget+data）"
echo "T-f1 码表里编造的整数码 3            : $tf1  （预期 0）"
echo "T-f2 整数码 2 读成停用               : $tf2  （预期 1）"
echo "T-g  中文状态串硬映射                : $tg   （预期 0：两个端点都不发中文码）"
echo "T-h1 Repo 车辆解析出口               : $th1  （预期 2：nearby 列表 + 单车详情）"
echo "T-h2 Repo 直接构造 BusVehicle        : $th2  （预期 0）"
echo "T-i  byUserDistance 引用点            : $ti   （预期 7：声明 1 + import 3 + 调用 3）"
echo "T-j1 演示面 progress 写入点          : $tj1  （预期 1：只在 lines 分支）"
echo "T-j2 演示面车辆 routeId              : $tj2  （预期 0：真实车辆 VO 没有这个键）"
echo "T-j3 演示面 routeProvider=SCHEDULE   : $tj3  （预期 1）"
echo "T-j4 演示面显式 JSONObject.NULL      : $tj4  （预期 ≥8：三条 state 分支的未给值）"
echo "T-j5 演示面 etaMinutes 写入点        : $tj5  （预期 1）"
echo "T-k  BusEtaContractTest 用例数       : ${tk:-0}   （预期 ≥13：⑬ 加了端点语义 + 位置报文用例，E-1 加了别名不采纳用例）"
echo "T-l  用户端/卡片裸拼 ETA             : $tl   （预期 0：一律走 etaValueText/etaCaptionText）"
echo "T-m  『没给』文案（逐条点名）           : $tm/4 条在场 （预期 4/4）"
echo "T-n  桌面卡片同口径 getter           : $tn   （预期 3）"
echo "T-o1 提醒用 mapNotNull 滤掉 null     : $to1  （预期 ≥1）"
echo "T-o2 页面先判 null 再比较            : $to2  （预期 ≥3：寄货页 + 首页）"
echo "T-p  真实键 busId 读法               : $tp   （预期 ≥1：车辆编号只有这一个真键）"
echo "T-p2 真实键 plateNo 读法             : $tp2  （预期 ≥3：车辆 1 + 段 1 + 司机档案 1）"
echo "T-q  §68 数据源缺口记录              : $tq   （预期 ≥1）"
echo "T-r  货运侧 ETA 可空声明             : send=${tr_send}/2 leg=${tr_leg}/1 trace=${tr_trace}/1 guide=${tr_guide}/1 preview=${tr_prev}/2"
echo "T-s  解析层 multiNum 读 ETA          : $ts   （预期 0：没给不许读成 0）"
echo "T-t  班次 ETA 混入直线估算别名        : $tt   （预期 0）"
echo "T-u  页面/提醒拿 0 当『还没到』       : $tu   （预期 0）"
echo "T-v  承运分钟带『直线估算』口径       : $tv   （预期 5：提醒 1 + 首页 1 + 寄货页 3）"
echo "T-v2 出分钟行未带口径标注的处数      : $tv2  （预期 0：闭包判据，逐行配对）"
echo "T-w1 提醒数据源 = /send/page         : $tw1  （预期 1）"
echo "T-w2 提醒不再读 /send/arrangements   : $tw2  （预期 0）"
echo "T-x  SendEtaContractTest 用例数      : ${tx:-0}   （预期 ≥12：⑫ 9 条 + E-1 别名不采纳 3 条）"
echo "T-y  REAL_STALE 不参与提醒/列表       : $ty   （预期 3：提醒 + 首页 + 寄货页）"
echo "T-z  货运侧『没给』文案（逐条点名）      : $tz/4 条在场 / 出现 $TZ_OCC 处 （预期 4/4 与恰好 7）"
echo "T-aa 精确到站分钟可空声明            : $taa  （预期 1：etaToNextStationMinutes: Double?）"
echo "T-ab 与小程序同口径的向上取整        : $tab  （预期 1：Math.max(1, ceil(...))，0.2 分钟不许显示 0）"
echo "T-ac 里程/时长读法里的自造别名键     : $tac  （预期 0：distance/meters/minutes 都不在这个契约里）"
echo "T-ad1 distanceKm 可空声明            : $tad1 （预期 7：车辆/线路/段/试算/路线预估/可达性/候选方案）"
echo "T-ad2 段里程与计划时长可空（⑫ 残留） : $tad2 （预期 2）"
echo "T-ae 界面裸读 .distanceKm 未判 null  : $tae              （预期 0：闭包判据）"
echo "T-af DistanceCaliberContractTest 用例: ${tdc:-0}   （预期 ≥4）"
echo "T-ag 演示面 lines 下发精确到站分钟   : $tag  （预期 1：新字段在 demo 形状里也要出现一次）"
echo "T-ah 界面把位置报文说成『在途』      : $tah  （预期 0：该报文没有 status/nextStation）"
echo "T-ai etaMinutes 读法里的第二候选名  : $tai  （预期 0：E-1 删掉的正是这 6 处自造别名）"
echo "T-ai2 main 源码里的裸 \"eta\" 字面量 : $tai2 （预期 0：闭包判据，三面实测无生产者）"
echo "T-ai3 演示面里的 \"eta\" 键           : $tai3 （预期 0：不许靠 demo 补键给别名续命）"
echo "T-aj  Minutes 家族多候选读法         : $taj  （预期 0：F-2 步 2 删的正是这五处第二候选名）"
echo "T-aj2 main 里的四个已删别名键字面量   : $taj2 （预期 0：闭包判据，换第一候选名也响）"
echo "T-aj3 演示面的已删别名键             : $taj3 （预期 0：demo 也不得成为第四个真相源）"
echo "T-aj4 五处真实键单键读法             : $taj4 （预期 ≥7：删掉真实键=契约断裂还静默）"
echo "T-aj5 单键 *Minutes 读法总量         : $taj5 （预期 ≥16）"
echo "T-aj6 MinutesContractTest 用例数     : $taj6 （预期 ≥6：①~⑤ 各一对 + 演示面同步用例）"
echo "T-aj GUARD 别名模式在测试源码的命中   : $tj_guard （预期 ≥4：证明 grep 还数得动，否则本族的 0 不采信）"
echo "--- 残留（本轮范围外，供下一轮复核，不判红）---"
echo "· Reachability 里程仍读 distance/distanceMeters（AppSendReachabilityRespVO 实发 distanceKm，无生产者，同族第四真相源）："
grep -rnE 'multiNum(OrNull)?\("distance(Meters)?"' $A --include=*.kt | grep -vE ':[0-9]+: *(\*|//)' | wc -l
echo "· TransitPlan 仍读 name/recommended（OrderTopologyRespVO.Candidate 实发 modeName:61 / chosen:69）："
grep -rnE 'multiStr\("name"|j\("name"|"recommended"' $A --include=*.kt | grep -vE ':[0-9]+: *(\*|//)' | wc -l
echo "· Arrangement.etaMinutes 现在是无人消费的兼容字段（§68 已把提醒与两个列表迁到 /send/page）："
grep -rln "import com.shanxiang.buspost.data.model.Arrangement" $A/ui $A/notify | wc -l

ok=1
[ "$ta" = 0 ] || { echo "× T-a ETA 家族又出现 null→0 塌缩（§14）"; ok=0; }
[ "$tb" -ge 10 ] || { echo "× T-b 可空哨兵读法被换回 multiNum（没给=0）"; ok=0; }
[ "$tc" = 9 ] || { echo "× T-c BusVehicle 数值字段不再是可空类型"; ok=0; }
[ "$td1" = 0 ] || { echo "× T-d1 客户端又开始自己编进度百分比"; ok=0; }
[ "$td2" = 2 ] || { echo "× T-d2 进度条不再只由后端 progress 驱动"; ok=0; }
[ "$te" = 0 ] || { echo "× T-e 又把 0 分钟当成『还没到时间』"; ok=0; }
[ "$tf1" = 0 ] || { echo "× T-f1 码表里又出现后端不会发的整数码 3"; ok=0; }
[ "$tf2" = 1 ] || { echo "× T-f2 整数码 2 又不读成停用（会把停用车说成已到站）"; ok=0; }
[ "$tg" = 0 ] || { echo "× T-g 中文状态串又被当成码表输入"; ok=0; }
[ "$th1" = 2 ] || { echo "× T-h1 车辆解析出现第三个/少了出口"; ok=0; }
[ "$th2" = 0 ] || { echo "× T-h2 Repo 又开始自己拼 BusVehicle"; ok=0; }
[ "$ti" = 7 ] || { echo "× T-i 排序口径引用点数量变了（nullsLast 失守风险）"; ok=0; }
[ "$tj1" = 1 ] || { echo "× T-j1 演示面给 nearby 也编了 progress"; ok=0; }
[ "$tj2" = 0 ] || { echo "× T-j2 演示面又发自造的车辆 routeId"; ok=0; }
[ "$tj3" = 1 ] || { echo "× T-j3 演示面 routeProvider 又写自造码"; ok=0; }
[ "$tj4" -ge 8 ] || { echo "× T-j4 演示面把『没给』写成缺键，真实契约态测不到"; ok=0; }
[ "$tj5" = 1 ] || { echo "× T-j5 演示面 etaMinutes 出现第二个写入点"; ok=0; }
[ "${tk:-0}" -ge 13 ] || { echo "× T-k 公交 ETA 契约测试被删"; ok=0; }
[ "$tl" = 0 ] || { echo "× T-l 页面把 null ETA 裸拼进文案"; ok=0; }
[ "$tm" = 4 ] || { echo "× T-m 『没给』的如实文案没齐（$tm/4）：每一条都要单独在场，少一条就是那一格又变静默"; ok=0; }
[ "$tn" = 3 ] || { echo "× T-n 桌面卡片与页面口径脱钩"; ok=0; }
[ "$to1" -ge 1 ] || { echo "× T-o1 到站提醒又不过滤 null（把没数据说成还没到时间）"; ok=0; }
[ "$to2" -ge 3 ] || { echo "× T-o2 页面又开始拿 null ETA 参与比较"; ok=0; }
[ "$tp" -ge 1 ] || { echo "× T-p busId 的真实键名读法丢了（车辆编号静默变空）"; ok=0; }
[ "$tp2" -ge 3 ] || { echo "× T-p2 plateNo 的真实键名读法少于 3 处（现 $tp2）：车牌又回到自造候选名列表"; ok=0; }
[ "$tq" -ge 1 ] || { echo "× T-q §68 的数据源缺口记录被删（提醒恒不响又没人知道）"; ok=0; }
[ "$tr_send" = 2 ] || { echo "× T-r1 SendOrder 的 etaMinutes/carrierEtaMinutes 又变成非空类型（null→0 复发）"; ok=0; }
[ "$tr_leg" = 1 ] || { echo "× T-r2 TransportLeg.etaMinutes 又变成非空类型"; ok=0; }
[ "$tr_trace" = 1 ] || { echo "× T-r3 ParcelTrace.etaMinutes 又变成非空类型"; ok=0; }
[ "$tr_guide" = 1 ] || { echo "× T-r4 RouteGuide.etaMinutes 又变成非空类型（0 分钟会显示成『全程约 0 分钟』）"; ok=0; }
[ "$tr_prev" = 2 ] || { echo "× T-r5 RoutePreview 的里程/时长又塌回非空默认值"; ok=0; }
[ "$ts" = 0 ] || { echo "× T-s 解析层用 multiNum 读 ETA/时长（缺键被读成 0）"; ok=0; }
[ "$tt" = 0 ] || { echo "× T-t 班次 ETA 又把承运直线估算当别名读（两种口径混成一个数字）"; ok=0; }
[ "$tu" = 0 ] || { echo "× T-u 页面/提醒又把 0 当成『还没到时间』"; ok=0; }
[ "$tv" = 5 ] || { echo "× T-v 直线估算 ETA 的口径标注处数与实测不符（被删或改了展示口径）"; ok=0; }
[ "$tv2" = 0 ] || { echo "× T-v2 有页面把承运分钟打出来却没带『直线估算』口径（把 Haversine 说成到站时间）"; ok=0; }
[ "$tw1" = 1 ] || { echo "× T-w1 到站提醒的数据源又离开 /send/page"; ok=0; }
[ "$tw2" = 0 ] || { echo "× T-w2 到站提醒又读回没有 ETA 键的 /send/arrangements（§68 复发）"; ok=0; }
[ "${tx:-0}" -ge 12 ] || { echo "× T-x 货运侧 ETA 契约测试被删"; ok=0; }
[ "$ty" = 3 ] || { echo "× T-y 过期位置又开始参与提醒（新鲜度闸失守）"; ok=0; }
[ "$tz" = 4 ] || { echo "× T-z 货运侧『没给』的如实文案没齐（$tz/4）：缺失又变静默"; ok=0; }
[ "$TZ_OCC" = 7 ] || { echo "× T-z 四处如实文案的出现处数不是恰好 7（现 $TZ_OCC）：少一处=那一格重新变静默，多一处=有人新造了一格，都要重新归因"; ok=0; }
[ "$taa" = 1 ] || { echo "× T-aa 精确到站分钟又变成非空类型（没给会被说成『约 0 分钟到下一站』）"; ok=0; }
[ "$tab" = 1 ] || { echo "× T-ab 向上取整口径变了（0.2 分钟会显示成 0，与小程序 detail.js 脱钩）"; ok=0; }
[ "$tac" = 0 ] || { echo "× T-ac 里程/时长读法又混进自造别名键（米被当公里读）"; ok=0; }
[ "$tad1" = 7 ] || { echo "× T-ad1 distanceKm 的可空声明不是恰好 7（现 $tad1）：少了=某一处又出现非空声明（缺键塌成 0.0），多了=新加了一处却没登记（D-085 实测 6→7，增量是 `TransitPlan.distanceKm`，它抄的是 OrderTopologyRespVO.Candidate:64 的 Double）"; ok=0; }
[ "$tad2" = 2 ] || { echo "× T-ad2 段里程/计划时长又回到非空默认值（⑫ 残留复发）"; ok=0; }
[ "$tae" = 0 ] || { echo "× T-ae 界面裸读 .distanceKm 没判 null（null 会被打印进文案）"; ok=0; }
[ "${tdc:-0}" -ge 4 ] || { echo "× T-af ⑬ 里程口径契约测试被删"; ok=0; }
[ "$tag" = 1 ] || { echo "× T-ag 演示面不再下发 etaToNextStationMinutes（新字段在真机演示路径上永不为真）"; ok=0; }
[ "$tah" = 0 ] || { echo "× T-ah 界面又把只有经纬度的位置报文宣布成『在途』（§14 没有值不许有业务语义）"; ok=0; }
[ "$tai" = 0 ] || { echo "× T-ai etaMinutes 读法又混进第二候选名（后端三面都无生产者的键，等于凭空造一个 ETA 来源）"; ok=0; }
[ "$tai2" = 0 ] || { echo "× T-ai2 main 源码里又出现裸 \"eta\" 字面量"; ok=0; }
[ "$tai3" = 0 ] || { echo "× T-ai3 演示面开始下发 \"eta\" 键（给自造别名续命，T-ai 的三方无生产者论据当场失效）"; ok=0; }
[ "$taj" = 0 ] || { echo "× T-aj Minutes 家族读法又混进第二候选名（后端/小程序/演示面三面核过的键名被稀释）"; ok=0; }
[ "$taj2" = 0 ] || { echo "× T-aj2 已删的自造别名键回到 main 源码（offsetMinutes/waitMinutes/totalMinutes/minutes 均无生产者）"; ok=0; }
[ "$taj3" = 0 ] || { echo "× T-aj3 演示面开始下发已删别名键（给\"三方无生产者\"续命，T-aj2 的论据当场失效）"; ok=0; }
[ "$taj4" -ge 7 ] || { echo "× T-aj4 五处真实键的单键读法丢失（后端实发的键不再被读，字段恒为默认值）"; ok=0; }
[ "$taj5" -ge 16 ] || { echo "× T-aj5 单键 *Minutes 读法总量下降（真实时长契约点被删或改回多候选）"; ok=0; }
[ "$taj6" -ge 6 ] || { echo "× T-aj6 Minutes 契约测试用例被删（别名复发无人报警）"; ok=0; }
[ "${tj_guard:-0}" -ge 4 ] || { echo "× T-aj GUARD 别名模式在测试源码 0 命中：grep 计数管道失效，本族所有\"预期 0\"一律不采信"; ok=0; }

[ "$ok" = 1 ] && echo "ETA 判据通过（公交侧 + 货运侧 + ⑬ 里程口径 + E-1 别名键归零）" || { echo "ETA 判据未通过"; exit 1; }
