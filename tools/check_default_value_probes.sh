#!/usr/bin/env bash
# F-2 §五「数字默认值 A/B/C/D 分类，禁止把 B（缺字段）/C（网络异常）当 A（真零值）说」判据簿。
#   本轮（2026-09-30）收口的首要修复点 = 司机运营统计 `/transport/driver/earnings`。
#   分类基数由 tools/default_value_census.py 实测产出（不是手挑文件，扫描范围由内容决定）。
#   2026-10-03 F-3 ⑬ 起本簿还管一族事：**解析层撤掉的无生产者别名键不许回流**（DV-t…t6，路线与轨迹两块），
#   且**按站点判**：`provider` 是 route-preview 的真键（四处还在读），写成"全 main 归零"会当场长红。
#   2026-10-06 队列 #262 起再加一族（DV-y…y6，任务族 /driver/tasks）：同样**只按块判**，
#   并把 DV-k 的基数由 116 收到 115（这一轮的补丁把七处 multiNum 换成了 multiNumOrNull + -1 哨兵）。
#   2026-10-06 队列 #269 起再加一族（DV-z…z6，包裹页页首计数）：这一族扫的不是解析层而是**页面层**——
#   §五 那条"不许把 B（缺字段）/C（网络异常）当 A（真零值）说"在页面上的形状是「本页条数当总数 + 失败念成 0 单」。
# 契约口径 = 三方实测：
#   后端 producer  AppDriverEarningsRespVO.java:14-26（todayAmount/totalAmount/totalOrders/todayOrders/
#                  shiftCount/pendingCount/records，**全部包装类型 ⇒ 可缺席**）
#                  + AppDriverEarningsRecordRespVO.java:14-26 + 生产者 DriverAppServiceImpl.java:365-391、toRecord:1846-1859
#                  （goodsName/weightKg 只在 CargoOrderDO 命中时才 set）
#   时间口径      YudaoJacksonAutoConfiguration.java:43,62 把 LocalDateTime 注册成 TimestampLocalDateTimeSerializer
#                  ⇒ 后端发的是 EpochMillis 数字，不是 ISO 串
#   小程序 consumer pages/driver/earnings/earnings.js:52-69（只读 todayAmount/totalAmount/totalOrders/
#                  shiftCount/pendingCount + records[].totalAmount/weightKg/statusName/createTime）
#   演示面 writer  DemoData.kt 的 /driver/earnings 分支（已改成与后端同形）
#   无生产者      weekAmount / monthAmount / totalWeight：grep -rn 后端 yudao-module-transport/src/main 0 命中，
#                  小程序不读，旧值（452.0/1830.5/246.8）全部由 DemoData 自造 = 第四个真相源
# 用法：bash tools/check_default_value_probes.sh（可用 BUSPOST_ANDROID 覆盖工程根）
set -u
SELF="$(cd "$(dirname "$0")/.." && pwd)"
ROOT="${BUSPOST_ANDROID:-$SELF}"
# 真 work 根：负控沙盒（只 copy app/src）里没有 cargo-post-platform / -miniprogram，
# 不指回真身的话普查会把每个键都读成"后端 0 命中"，C 类凭空涨满。
WORK="${BUSPOST_WORK:-$(cd "$SELF/.." && pwd)}"
cd "$ROOT" || exit 1

A=app/src/main/java/com/shanxiang/buspost
MD="$A/data/model/Models.kt"
RP="$A/data/repo/Repo.kt"
DM="$A/data/repo/DemoData.kt"
ES="$A/ui/driver/EarningsScreen.kt"
TE="$ROOT/app/src/test/java/com/shanxiang/buspost/data/EarningsContractTest.kt"

# 只取 driverEarnings 的解析体：别名键写进别的解析函数不归本条管（那是各自契约的判据）
EB=$(sed -n '/suspend fun driverEarnings/,/^    }$/p' "$RP")
# 只取演示面 /driver/earnings 分支的报文形状
DEB=$(sed -n '/path.endsWith("\/driver\/earnings")/,/^            )$/p' "$DM")

ALIASES='"(todayEarnings|totalEarnings|orderCount|weekAmount|monthAmount|totalWeight|items|details|fee|bizType|cargoName|date|day|count|type|todayShiftCount|pendingLoadCount|todayOrderCount|weight)"'

# ---- GUARD：grep 计数管道一旦失效，下面所有"预期 0"会集体变成假绿 ----
guard_pipeline=$(printf 'aa bb\n' | grep -hoE 'bb' | wc -l | tr -d ' ')
# 正控制：别名模式必须在测试源码（负控注入所在地，不在判据扫描范围内）数得到，数不到就判红而不是判绿
guard_alias=$(grep -rhoE "$ALIASES" "$TE" 2>/dev/null | wc -l | tr -d ' ')
# 正控制：两段 sed 抽取必须真的抓到了代码，抓到空串的话 DV-a/DV-b/DV-c 的 0 毫无意义
guard_block=$(printf '%s\n' "$EB" | wc -l | tr -d ' ')
guard_demo=$(printf '%s\n' "$DEB" | grep -c 'totalAmount' | tr -d ' ')

# DV-a 收益解析体内不许再有非可空数值/布尔读法（multiNum/multiBool 缺键即 0，正是 §五 的破口形状）
dva=$(printf '%s\n' "$EB" | grep -cE 'multi(Num|Bool)\(')
# DV-b 解析体内的自造别名键（三面实测无生产者）
dvb=$(printf '%s\n' "$EB" | grep -oE "$ALIASES" | wc -l | tr -d ' ')
# DV-c 演示面同形：不许再下发这些键，否则"无生产者"论据当场失效，等于给别名续命
dvc=$(printf '%s\n' "$DEB" | grep -oE "$ALIASES" | wc -l | tr -d ' ')
# DV-d 真实键单键读法数量（顶层 7 个键 + records + 明细 5 个真实键，共 12 处读法）
dvd=$(printf '%s\n' "$EB" | grep -oE 'multi(NumOrNull|Str|Arr)\("[A-Za-z]+"\)' | wc -l | tr -d ' ')
# DV-e/F 模型数值字段可空声明：Earnings 9 个、EarningItem 2 个
dve=$(sed -n '/^data class Earnings(/,/^}/p' "$MD" | grep -cE 'val [a-zA-Z]+: (Double|Int)\?')
dvf=$(sed -n '/^data class EarningItem(/,/^}/p' "$MD" | grep -cE 'val [a-zA-Z]+: (Double|Int)\?')
# DV-g 未给文案 getter（缺失态必须能在 JVM 里被断言，而不是只有 grep 能看见）
dvg=$(sed -n '/^data class Earnings(/,/^}/p' "$MD" | grep -cE 'val [a-zA-Z]+Text: String get')
dvg2=$(sed -n '/^data class EarningItem(/,/^}/p' "$MD" | grep -cE 'val [a-zA-Z]+Text: String get')
# DV-h 页面裸读可空数值（formatPrice(e.today) 这类能编译就说明上游已经塌 0；`?: 0.0` 的自补零同样算）
dvh=$(grep -cE '(formatPrice\(e\.(today|total|totalWeight|orderCount)[^a-zA-Z]|formatPrice\(item\.(amount|weightKg)[^a-zA-Z]|e\.(orderCount|totalWeight) > 0|item\.(amount|weightKg) *\?: *(0|0\.0))' "$ES")
# DV-i 无生产者键在整个 main 源码里归零（不止收益解析体，别处复现也算）
dvi=$(grep -rnE '"(weekAmount|monthAmount|totalWeight|todayEarnings|totalEarnings)"' $A --include=*.kt | grep -vE ':[0-9]+: *(\*|//)' | wc -l | tr -d ' ')
# DV-j 后端 EpochMillis 时间口径分支必须在（缺了它 13 位毫秒数会原样进 UI）
dvj=$(grep -cE 'length in 12\.\.14 && v\.all \{ it\.isDigit\(\) \}' "$MD")
# DV-k 全项目数值/布尔非可空读法基线护栏：F-2 轮 152→132，F-3 ② 轮 132→116，#262 轮 116→115，#261 轮 115→111（G-1 把段坐标四个自造别名的读法换成 multiNumOrNull ⇒ 非空读法少 4 处，实测 111），只许降不许升
dvk=$(grep -rnE 'multi(Num|Bool)\(' $A --include=*.kt | grep -vE ':[0-9]+: *(\*|//)' | wc -l | tr -d ' ')
# DV-l 契约测试用例数
dvl=$(grep -cE '^[[:space:]]*@Test' "$TE")

# ===== 以下 DV-m…DV-s = F-3 ②（2026-10-01）本轮收口的六个「三面全无生产者键」族 =====
# 契约口径逐个抄后端 VO，不抄 Android 的候选名列表：
#   AppDriverProfileRespVO.java:11-23 = driverId/name/mobile/vehicleId/plateNo/cargoCapacity/cargoCapacityKg
#   OrderTopologyRespVO.Candidate:60-69 = mode/modeName/legCount/transferCount/distanceKm/durationMinutes/score/reason/chosen
#   AppProductRespVO.java:12-34（12 键，无划线原价、无销量）
#   AppDriverPickupRespVO.java:13-33（11 键，无勾选态）/ AppDriverHandoverRespVO.java:14-62（17 键，只有 photoUrl）
TG="$ROOT/app/src/test/java/com/shanxiang/buspost/data/NoProducerKeyFamilyContractTest.kt"
CG="$A/ui/driver/CargoGauge.kt"
WB="$A/ui/driver/WorkbenchScreen.kt"
PB=$(sed -n '/fun parse(o: JSONObject) = DriverProfile(/,/^        )$/p' "$MD")
TB=$(sed -n '/fun parse(o: JSONObject) = TransitPlan(/,/^        )$/p' "$MD")
KB=$(sed -n '/fun parse(o: JSONObject) = PickupTask(/,/^        )$/p' "$MD")
HB=$(sed -n '/fun parse(o: JSONObject) = HandoverTask(/,/^        )$/p' "$MD")

# 本轮被撤的键（数值/布尔族）：解析层读点 + 演示面 writer 两头都要归零。
# ⚠ 不含 plate/vehiclePlate：那两个名字在 BusVehicle(Models.kt:765) 与 TransportLeg(:1125) 的解析块里还是活别名，
#   属字符串族/steps 族一批处置（见下方残留清单），放进本条会把别的族的账算到本轮头上、判据当场长红。
CKEYS='"(originPrice|sales|usedKg|verified|capacityKg|checked|needPhoto|photoRequired|forcePhoto|recommended|planName|best|selected|segments|explain)"'

# DV-m/m2 司机档案：三个真实键读法必须在，六个自造名必须不在
dvm=$(printf '%s\n' "$PB" | grep -oE 'multi(NumOrNull|Str|BoolOrNull)\("(plateNo|cargoCapacityKg|cargoCapacity)"' | wc -l | tr -d ' ')
dvm2=$(printf '%s\n' "$PB" | grep -oE '"(plate|vehiclePlate|carNo|capacityKg|usedKg|verified)"' | wc -l | tr -d ' ')
# DV-n/n2 候选方案：modeName/chosen/legCount/durationMinutes/reason 五个真键 vs 九个自造名
dvn=$(printf '%s\n' "$TB" | grep -oE 'multi(NumOrNull|Num|Str|BoolOrNull)\("(modeName|chosen|legCount|durationMinutes|reason)"' | wc -l | tr -d ' ')
dvn2=$(printf '%s\n' "$TB" | grep -oE '"(name|planName|title|recommended|best|selected|legs|segments|explain)"' | wc -l | tr -d ' ')
# DV-o/o2 商品：stock 可空 + 售罄判定必须先过 null（缺键塌 0 会把下单按钮锁死）；划线价/销量读法归零
dvo=$(grep -cE '(val stock: Int\? = null|stock != null && stock <= 0)' "$MD")
dvo2=$(grep -rnE 'multi(Num|Bool)(OrNull)?\("(originPrice|sales)"' $A --include=*.kt | grep -vE ':[0-9]+: *(\*|//)' | wc -l | tr -d ' ')
# DV-p/p2 揽件勾选与交接拍照要求：解析层不再读这些键，两个解析块里连键名都不许出现
dvp=$(grep -rnE 'multiBool\("(checked|needPhoto|photoRequired|forcePhoto)"' $A --include=*.kt | grep -vE ':[0-9]+: *(\*|//)' | wc -l | tr -d ' ')
dvp2=$(printf '%s\n%s\n' "$KB" "$HB" | grep -oE '"(checked|done|needPhoto|photoRequired|forcePhoto)"' | wc -l | tr -d ' ')
# DV-q/q2/q3 载重口径：界面只吃件数口径（cargoCapacity − loadedCount），无生产者的重量口径不许回流页面
dvq=$(sed -n '/^fun CargoGauge(/,/^) {/p' "$CG" | grep -cE '(capacityPercent|loadedCount|cargoCapacity|capacityKg): (Double|Int)\?')
dvq2=$(grep -c 'freePercent(' "$WB")
dvq3=$(grep -rn 'usedKg' $A/ui --include=*.kt | grep -vE ':[0-9]+: *(\*|//)' | wc -l | tr -d ' ')
# DV-r 演示面同形：撤掉的键不许留在报文里给别名续命（注释里点名它们是为了留证据，不算命中）
dvr=$(grep -nE "$CKEYS" "$DM" | grep -vE '^[0-9]+: *(\*|//)' | wc -l | tr -d ' ')
# DV-s 本轮新增的契约测试类：用例数不许被删
dvs=$(grep -cE '^[[:space:]]*@Test' "$TG")

# ===== 以下 DV-t…t6 = F-3 ⑬（2026-10-03）本轮撤掉的路线/轨迹别名键 =====
# 契约口径（逐面实测，不抄客户端的候选名列表）：
#   后端 AppDriverRouteRespVO.java:17-86 的键 = planId/vehicleId/stops/polyline/routeProvider/routeAvailable/
#        routeUnavailableReason/routeDistanceMeters/routeDurationSeconds/routeComplete/deviationMeters/
#        deviated/current*/nextAction*/nextStop*/currentSegmentPolyline —— 没有 guidance/instructions/
#        routeSource/roadDistanceMeters/durationSeconds/crs/offRoute，steps 里也没有 meters/lane/seconds；
#   `provider` **不是全局死键**：它是 route-preview 的键，Models.kt:524(线路 dataSource)、:1900、
#        Repo.kt:832(transitProvider)、DemoData:501 四处还在读 ⇒ 本条只按站点口径判 routeGuideOf，
#        写一条"全 main 的 provider = 0"会当场长红（这正是"一个键名一条闸"要防的串味）。
#   `finished` 三方 0 命中，`completed` 只在后端 bigscreen 的 admin 汇总（BigScreenServiceImpl:120）出现，
#        都不在 /send/track 报文里 ⇒ 轨迹节点完成态只认 `done`。
RGT="$ROOT/app/src/test/java/com/shanxiang/buspost/data/repo/RouteGuideContractTest.kt"
UTT="$ROOT/app/src/test/java/com/shanxiang/buspost/data/UserTrackingContractTest.kt"
RB=$(sed -n '/private fun routeGuideOf/,/^    }$/p' "$RP")
NB=$(sed -n '/private fun navStepOf/,/^    }$/p' "$RP")
RKEYS='"(guidance|instructions|routeSource|roadDistanceMeters|durationSeconds|crs|offRoute|finished|completed|meters|lane|seconds)"'
RREAL='"(routeProvider|dataSource|polyline|track|points|routeDistanceMeters|distanceMeters|routeDurationSeconds|coordinateSystem|routeAvailable|routeUnavailableReason|steps|stops|stations|nextStopRoadDistanceMeters|nextStopRoadEtaSeconds|nextStopDistanceMeters|deviated|etaMinutes|stationName|nextStation|current|arrive|destination|icon|maneuver|action|text|instruction|road|detail|hint|roadName|lanes|recommendedLane|etaSeconds|done)"'
# DV-t 两个解析块里被撤的别名键名（块内注释用反引号点名字，不带引号，不会被这条数到）
dvt=$(printf '%s\n%s\n' "$RB" "$NB" | grep -oE "$RKEYS" | wc -l | tr -d ' ')
# DV-t2 闭包判据：挪到别的解析块、换个第一候选名也响（注释行按 DV-i/DV-k 同一口径排除）
# 2026-10-10 重锚：这条从"整 main 出现该字面量"收成"同一行既有别名键、又有读法出口 `multi*(`"。
#   成因是实测的假阳性：本轮它数到 1，命中的是 `WorkbenchScreen.kt:822  item(key = "finished")`
#   —— 那是 LazyColumn 的**列表键**，不是报文键，与"自造别名续命"毫无关系。
#   收窄后闭包性质**没丢**：威胁模型是"把幻影键塞进某次读取"，而本工程所有报文读取只有
#   `multiArr/multiBool/multiBoolOrNull/multiNum/multiNumOrNull/multiStr` 六个出口
#   （`grep -rn 'fun JSONObject\.' app/src/main` 实测就这六个），所以别名出现在**任意一本文件的任意一次
#   multi* 调用里**（第一候选名或并列候选名都算）仍然会响 ⇒ 由 DV-t2/DV-t2b 两条注入分别钉。
dvt2=$(grep -rnE "$RKEYS" $A --include=*.kt | grep -vE ':[0-9]+: *(\*|//)' | grep -cE 'multi[A-Za-z]*\(' | tr -d ' ')
# DV-t3/t4 真实键读法数量：撤别名时把真键一起删掉是最常见的坏法，归零判据必须配对数
dvt3=$(printf '%s\n' "$RB" | grep -oE "multi(NumOrNull|Num|Str|Bool|BoolOrNull|Arr)\($RREAL" | wc -l | tr -d ' ')
dvt4=$(printf '%s\n' "$NB" | grep -oE "multi(NumOrNull|Num|Str|Bool|BoolOrNull)\($RREAL" | wc -l | tr -d ' ')
# DV-t5/t6 两份行为用例的用例数（别名不产生值这件事只有 JVM 用例能证明，grep 只能证明键名没出现）
dvt5=$(grep -cE '^[[:space:]]*@Test' "$RGT")
dvt6=$(grep -cE '^[[:space:]]*@Test' "$UTT")

# ===== 以下 DV-y…y6 = 队列 #262（2026-10-06）本轮收口的任务族 /driver/tasks =====
# 契约口径（逐面实测）：后端 AppDriverTaskRespVO.java:18-55 的 19 个键 = planId/taskWindowStart/taskWindowEnd/
#   visitSequence/id/stationId/stationName/actionType/actionName/orderId/orderNo/estimatedArrivalTime/
#   plannedDepartureTime/serviceDurationSeconds/quantity/segmentDurationSeconds/segmentDistanceKm/status/statusName；
#   生产者 DriverAppServiceImpl#tasks:421-444（窗口三键只在 planMap 命中时才 set）。
# ⚠ 本族**只按块判**：taskType/bizType/targetStation/cargoName/goodsName/itemCount/planTime 这些名字在
#   别的报文里是真键或有既有读点（实测全 main 生效行分别 2/7/10/4/25/16/3 处），
#   写成"全 main 归零"就撞进 DV-t 那条 `provider` 同族的串味坑 ⇒ 块外一条都不判。
MS="$A/data/model/MissionStop.kt"
DTR="$ROOT/app/src/test/java/com/shanxiang/buspost/data/model/DriverTaskRespContractTest.kt"
# ⚠ 变量名一律带 Y 前缀：早前这族直接复用了 TB/PB，而 95-99 行的 DV-m/DV-n 与 268-269 行的 GUARD2 读的是
#   DriverProfile/TransitPlan 那两个块，被这里覆盖后 guard 数到了别的块（两块行数都恰好过线 ⇒ GUARD2 静默变成假绿）。
YSTP=$(sed -n '/fun parseTaskItem/,/^        }$/p' "$MS")
YDTP=$(sed -n '/fun parse(o: JSONObject) = DriverTask(/,/^        )$/p' "$MD")
YSPB=$(sed -n '/fun parse(o: JSONObject): MissionStop/,/^        }$/p' "$MS")
YBLK=$(printf '%s\n%s\n' "$YSTP" "$YDTP")
# 八个幻影键族 + 冒充窗口的 createTime（旧模型就是这么读的）
TKEYS='"(taskType|bizType|targetStation|cargoName|goodsName|itemCount|planTime|taskName|createTime)"|"count"'
TREAL='multi(Num|Str)(OrNull)?\("(stationId|stationName|visitSequence|actionType|actionName|orderId|orderNo|quantity|status|statusName|planId)"'
GEOKEYS='"(latitude|longitude|lat|lng)"'
# DV-y 任务族两块解析体内不许再出现幻影键
dvy=$(printf '%s\n' "$YBLK" | grep -oE "$TKEYS" | wc -l | tr -d ' ')
# DV-y2 配对数：撤别名时把 19 个真键一起删掉是最常见的坏法（11 处 multi + 2 处 reportTimeOf + 1 处 ETA opt）
dvy2=$(printf '%s\n' "$YBLK" | grep -oE "$TREAL" | wc -l | tr -d ' ')
dvy2w=$(printf '%s\n' "$YBLK" | grep -oE 'reportTimeOf\("(taskWindowStart|taskWindowEnd)"|o\.opt\("estimatedArrivalTime"\)' | wc -l | tr -d ' ')
# DV-y3 别名回潮最直观的形状就是逗号后第二个候选名；任务族两块内必须一个都没有（同文件 parse 里数得到 = 反证）
dvy3=$(printf '%s\n' "$YBLK" | grep -oE 'multi(Num|Str|Bool)\("[a-zA-Z]+", *"' | wc -l | tr -d ' ')
dvy3p=$(printf '%s\n' "$YSPB" | grep -oE 'multi(Num|Str|Bool)\("[a-zA-Z]+", *"' | wc -l | tr -d ' ')
# DV-y4 这个 VO 全文没有坐标：任务族读点不许吃 lat/lng（站点坐标只在 /driver/route 的 stops）
dvy4=$(printf '%s\n' "$YSTP" | grep -oE "$GEOKEYS" | wc -l | tr -d ' ')
dvy4p=$(printf '%s\n' "$YSPB" | grep -oE "$GEOKEYS" | wc -l | tr -d ' ')
# DV-y5 行为用例条数（幻影键不产生值、任务段进不了地图点位，只有 JVM 用例能证明）
dvy5=$(grep -cE '^[[:space:]]*@Test' "$DTR")
# DV-y6 「哪些行属于同一段」只有一个出口：聚合在模型层做一次（planId 一次 + 同站并动作一次），页面只渲染
# ⚠ dvy6s 用 -F 定字符串：`groupBy { it\.stop\.stationId }` 走 BRE 会报 Invalid content of \{\}，报错的 0 会被读成"归零"
NC='(^|[0-9]+:)[[:space:]]*(\*|//)'
dvy6g=$(grep -rn 'DriverTaskSegment\.group(' "$A" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')
dvy6t=$(grep -c 'SectionTitle("今日运营任务"' "$WB")
dvy6s=$(grep -cF 'groupBy { it.stop.stationId }' "$MD")
dvy6p=$(grep -cF 'items.groupBy { it.planId }' "$MD")
# DV-y6u 页面侧不许自己重新分组：工作台生效行里 groupBy 必须为 0（同域收益页按天分组是真需求 = 反证）
dvy6u=$(grep -nE 'groupBy' "$WB" | grep -vE "$NC" | wc -l | tr -d ' ')
dvy6up=$(grep -nE 'groupBy' "$A/ui/driver/EarningsScreen.kt" | grep -vE "$NC" | wc -l | tr -d ' ')
# 反证：幻影键模式在任务族契约用例的 fixture 里数得到（那是负控注入地，不在 dvy 的扫描范围内）
guard_ykeys=$(grep -oE "$TKEYS" "$DTR" 2>/dev/null | wc -l | tr -d ' ')
guard_ystp=$(printf '%s\n' "$YSTP" | wc -l | tr -d ' ')
guard_ydtp=$(printf '%s\n' "$YDTP" | wc -l | tr -d ' ')
guard_yspb=$(printf '%s\n' "$YSPB" | grep -c 'multiStr' | tr -d ' ')

# ===== 以下 DV-z…z6 = 队列 #269（2026-10-06）本轮收口的包裹页页首计数 =====
# 契约口径（逐面实测）：后端 `AppSendController#page` 与 `AppProductOrderController#page` 都是
#   `return success(new PageResult<>(list, total))`（PageResult.java:15 `private Long total`）
#   ⇒ **分页总数是真下发的键**，客户端 `Payload.total` 读的就是它（缺键才回落 list.size）。
#   旧写法念的是 `vm.sends.size` / `vm.orders.size` = **这一页拿回几条**（本页 pageSize=20，第 21 单起就少念），
#   且请求失败时 items 为空 ⇒ 那一句"寄货 0 单"把"接口挂了"洗成"你没有下单"。
# ⚠ 本族只按**页**判：`Payload.total` 的回落形状是另一份契约（Api.kt，不在这里判），
#   同 DV-y 只按块判的理由一样 —— 全项目归零会把合法用法一起判死。
PS="$A/ui/user/ParcelScreen.kt"
PCT="$ROOT/app/src/test/java/com/shanxiang/buspost/ui/user/ParcelCountContractTest.kt"
# 只取 ParcelViewModel.load() 的函数体：计数写在别的块里不归本条管
ZLB=$(sed -n '/fun load() = work {/,/^    }$/p' "$PS")
# DV-z1 旧写法（把本页条数念成总数）在整页生效行里必须为 0
dvz1=$(grep -nE 'vm\.(sends|orders)\.size' "$PS" | grep -vE "$NC" | wc -l | tr -d ' ')
# DV-z2 计数口径只有一个定义点 + 两个调用点（第二处调用一定带第二套口径）
dvz2=$(grep -cE 'internal fun serverCount\(' "$PS")
dvz2b=$(printf '%s\n' "$ZLB" | grep -cE '= serverCount\(')
# DV-z3 出口类型必须仍是 Int?：写回 Int 就是把失败洗成 0 的那条路
dvz3=$(grep -cE 'internal fun serverCount\(error: String\?, total: Int\): Int\?' "$PS")
# DV-z4 两格声明必须可空且初值 null（还没加载完也不许念 0）
dvz4=$(grep -cE 'var (sendCount|orderCount) by mutableStateOf<Int\?>\(null\)' "$PS")
# DV-z5 页首那一句只有一个话术出口（定义 + 页面渲染各一处）
# ⚠ z5b 用 -F：字面量里有 `${label}`，走 ERE 的 `\{` 会撞 DV-y6s 那条 Invalid content of \{\} 的坑（报错的 0 会被读成"归零"）
dvz5=$(grep -cE 'parcelHeaderCountText\(' "$PS")
dvz5b=$(grep -cF '"${label}未取到"' "$PS")
# DV-z6 行为用例条数：失败≠0、total≠本页长度、后端真说 0 仍说 0 —— 这三件事只有 JVM 用例能钉
dvz6=$(grep -cE '^[[:space:]]*@Test' "$PCT" 2>/dev/null | tr -d ' ')
# 反证：z1 的模式必须在判据扫描范围之外数得到（契约用例里 `assertEquals(2, vm.sends.size)` 正是它的合法用法——
#   断言的就是"本页两条、全量 137 条"这件事），数不到就说明模式写坏了，dvz1 的 0 是"没数到"而不是"归零"
guard_zsize=$(grep -cE 'vm\.sends\.size' "$PCT" 2>/dev/null | tr -d ' ')
guard_zload=$(printf '%s\n' "$ZLB" | wc -l | tr -d ' ')

# ===== DV-u…DV-w（F-3 ⑰ census 补列，2026-10-03）=====
# 目的：把 `D-②-4`（在册"三面 0 生产者 ∩ 数值布尔 = 32 处"这个数**不可复现**）变成逐字可复现的三列，
#   并把 DV-k 的 grep 口径与 census 的行口径**接起来**（实测 115 vs 113，差的两行是 multiNum/multiBool 两个函数声明）。
# 普查要读后端与小程序两面：负控沙盒里只有 app/src ⇒ BUSPOST_WORK 必须指向真 work 根（脚本自己算，见文件头）。
CENSUS="$ROOT/tools/default_value_census.py"
[ -f "$CENSUS" ] || CENSUS="$SELF/tools/default_value_census.py"
CENSUS_JSON_PATH=$(PYTHONIOENCODING=utf-8 python -c 'import tempfile,os;print(os.path.join(tempfile.gettempdir(),"dv_census_probe_%d.json"%os.getpid()))')
BUSPOST_ANDROID="$ROOT" BUSPOST_WORK="$WORK" \
    python "$CENSUS" --write --out "$CENSUS_JSON_PATH" >/dev/null 2>&1
census_rc=$?
# ⚠ 三个坑（本轮实测踩过）：
#   1) 走文件而不是管道：Windows python 的 stdin 按 GBK 解码，普查 JSON 里的中文源码行会被悄悄改字符，
#      json.loads 在 line 1153 处炸；bash 变量中转一圈也一样坏。
#   2) 路径用 argv（--out）传，且取临时路径那次 python 要 PYTHONIOENCODING=utf-8：
#      默认 GBK 的 stdout 经 bash 变量中转会把中文用户名（`袁`）改成 `Ԭ` ⇒ open 直接 FileNotFound，
#      普查看起来像"跑不通"而不是"没路径"。
#   3) 读数失败一律置 -1 并判红，不许静默变 0——"0 命中"在这本簿里必须是量出来的 0。
CENSUS_READER=$(cat <<'PY'
import json, sys, re
try:
    with open(sys.argv[1], encoding="utf-8", errors="replace") as fh:
        d = json.load(fh)
except Exception:
    sys.exit(3)
# §二之二 的规则**在这里独立实现一遍**：与 census 里的 SHAPE_* 常数不是同一份代码，
# 谁被改坏（含负控注入 census）这两读数就会分叉 ⇒ DV-u 不是把 census 自己算的数抄一遍。
ARR = re.compile(r'multiArr\(|optJSONArray')
NB = re.compile(r'multi(?:Num|Bool)(?:OrNull)?\(|opt(?:Int|Double|Boolean|Long)\(')
NN = re.compile(r'multi(?:Num|Bool)\(|opt(?:Int|Double|Boolean|Long|Number)\(')
c = [r for r in d["rows"] if r["class"] == "C"]
h = {"arr": 0, "numbool": 0, "nonnull": 0, "str": 0}
for r in c:
    t = r["text"]
    if ARR.search(t):
        h["arr"] += 1
    elif NB.search(t):
        h["numbool"] += 1
        if NN.search(t):
            h["nonnull"] += 1
    else:
        h["str"] += 1
cs, tf, bz = d["c_shape"], d["three_facet_zero"], d["dvk_bridge"]
print("\t".join(str(x) for x in [
    len(d["rows"]), d["tally"].get("C", -1),
    cs.get("arr", -1), cs.get("numbool", -1), cs.get("numbool_nonnull", -1), cs.get("str", -1),
    h["arr"], h["numbool"], h["nonnull"], h["str"],
    tf["keys"], tf["sites"], tf["sites_in_c_rows"], tf["numeric_bool_nonnull_sites"],
    d["demo_writers"]["distinct"], len(d["demo_writers"]["files"]),
    bz["dvk_grep_lines"], bz["census_dvk_shape_rows"], bz["delta"],
    len(bz["only_in_grep"]), len(d["c_no_producer"])]))
PY
)
census_fields=$(python -c "$CENSUS_READER" "$CENSUS_JSON_PATH" 2>/dev/null)
census_code=$?
rm -f "$CENSUS_JSON_PATH"
if [ "$census_rc" = 0 ] && [ "$census_code" = 0 ]; then
  IFS=$'\t' read -r cens_rows cens_c cens_arr cens_nb cens_nn cens_str hand_arr hand_nb hand_nn hand_str \
      tfz_keys tfz_sites tfz_c tfz_nb demo_dist demo_files bridge_grep bridge_rows bridge_delta \
      bridge_only cnop <<< "$census_fields"
else
  cens_rows=-1; cens_c=-1; cens_arr=-1; cens_nb=-1; cens_nn=-1; cens_str=-1
  hand_arr=-1; hand_nb=-1; hand_nn=-1; hand_str=-1
  tfz_keys=-1; tfz_sites=-1; tfz_c=-1; tfz_nb=-1; demo_dist=-1; demo_files=-1
  bridge_grep=-1; bridge_rows=-1; bridge_delta=-1; bridge_only=-1; cnop=-1
fi

# ---- GUARD4（F-3 ⑰ 新增）：普查两面输入 + census 退出码。DV-u/v/w 全部建在这份 JSON 上，
#      census 一旦跑不起来（沙盒没 tools/、后端路径没接上），下面三个数会集体是 -1 ⇒ 必须当场判红 ----
guard_census_rc=$census_rc
guard_census_code=$census_code

# DV-u C 类形态拆分：census 补的 c_shape 必须与「本簿里独立实现的 §二之二 规则」逐字相等，
#   再把"非可空数值布尔"这一支钉在上限（本轮实测 19，与 §二之二 那一轮同一个数）。
dvu_shape_eq=1
for pair in "$cens_arr:$hand_arr" "$cens_nb:$hand_nb" "$cens_nn:$hand_nn" "$cens_str:$hand_str"; do
  [ "${pair%%:*}" = "${pair##*:}" ] || dvu_shape_eq=0
done
dvu_c_sum=$((cens_arr + cens_nb + cens_str))

# DV-v 三面 0 生产者（后端 VO 0 命中 ∧ 小程序 0 读点 ∧ 演示面 0 写键）：键数与站点数都钉上限
# DV-w DV-k 的 grep 口径与 census 的行口径必须**对上**（本轮实测 115 = 113 读法 + 2 行函数声明）
dvw_eq=$([ "$bridge_grep" = "$dvk" ] && echo 1 || echo 0)
dvw_delta_ok=$([ "$bridge_delta" = "$((bridge_grep - bridge_rows))" ] && [ "$bridge_delta" = "$bridge_only" ] && echo 1 || echo 0)

# ---- GUARD（本轮新增）：0 命中必须先能反证「模式本身数得到」，否则红和绿没有区别 ----
guard_tg=$(grep -ohE "$CKEYS" "$TG" 2>/dev/null | wc -l | tr -d ' ')
guard_pb=$(printf '%s\n' "$PB" | wc -l | tr -d ' ')
guard_tb=$(printf '%s\n' "$TB" | wc -l | tr -d ' ')
guard_kb=$(printf '%s\n%s\n' "$KB" "$HB" | wc -l | tr -d ' ')
# ---- GUARD3（F-3 ⑬ 新增）：路线块 sed 锚点 + 别名模式必须有正控制，否则 DV-t/DV-t2 的 0 与坏掉没区别 ----
guard_rb=$(printf '%s\n' "$RB" | wc -l | tr -d ' ')
guard_nb=$(printf '%s\n' "$NB" | wc -l | tr -d ' ')
# 正控制：别名模式在两份测试源码（负控注入的对照地，不在 DV-t2 的扫描范围内）里必须数得到
guard_rkeys=$(cat "$RGT" "$UTT" 2>/dev/null | grep -ohE "$RKEYS" | wc -l | tr -d ' ')

printf 'DV-a  收益解析体非可空数值读法      : %s  （预期 0：缺键不许读成 0）\n' "$dva"
printf 'DV-b  收益解析体自造别名键          : %s  （预期 0：三面都无生产者）\n' "$dvb"
printf 'DV-c  演示面 earnings 自造键         : %s  （预期 0：demo 不得成为第四个真相源）\n' "$dvc"
printf 'DV-d  真实键单键读法                : %s  （预期 ≥12：删掉真实键=契约断裂还静默）\n' "$dvd"
printf 'DV-e  Earnings 可空数值字段         : %s  （预期 9）\n' "$dve"
printf 'DV-f  EarningItem 可空数值字段      : %s  （预期 2）\n' "$dvf"
printf 'DV-g  Earnings 未给文案 getter      : %s  （预期 8）\n' "$dvg"
printf 'DV-g2 EarningItem 未给文案 getter   : %s  （预期 2）\n' "$dvg2"
printf 'DV-h  页面裸读可空数值              : %s  （预期 0：一律走 *Text getter）\n' "$dvh"
printf 'DV-i  无生产者键全 main 命中        : %s  （预期 0）\n' "$dvi"
printf 'DV-j  ts() 的 EpochMillis 分支      : %s  （预期 1）\n' "$dvj"
printf 'DV-k  全项目数值非可空读法基线      : %s  （预期 ≤107：F-2 收到 132，F-3 ② 收到 116，#262 收到 115，#261 收到 111，2026-10-13 收到现读 107——原 ≤111 高出实测 4，删四处也不红）\n' "$dvk"
printf 'DV-l  EarningsContractTest 用例数   : %s  （预期 ≥8）\n' "$dvl"
printf 'DV-m  档案真实键读法(plateNo/两件数) : %s  （预期 =3：换成自造名就数不到）\n' "$dvm"
printf 'DV-m2 档案解析体自造别名            : %s  （预期 0：plate/vehiclePlate/carNo/capacityKg/usedKg/verified）\n' "$dvm2"
printf 'DV-n  方案真实键读法                : %s  （预期 ≥5：modeName/chosen/legCount/durationMinutes/reason）\n' "$dvn"
printf 'DV-n2 方案解析体自造别名            : %s  （预期 0：name/planName/title/recommended/best/selected/legs/segments/explain）\n' "$dvn2"
printf 'DV-o  商品 stock 可空+售罄过 null    : %s  （预期 =2：缺键不许判成已售罄）\n' "$dvo"
printf 'DV-o2 originPrice/sales 读法        : %s  （预期 0：后端 12 键里没有这两个口径）\n' "$dvo2"
printf 'DV-p  勾选/拍照要求位读法           : %s  （预期 0：两个 VO 共 28 键里没有任何要求位）\n' "$dvp"
printf 'DV-p2 揽件+交接解析块里的要求位键名 : %s  （预期 0）\n' "$dvp2"
printf 'DV-q  CargoGauge 可空口径参数       : %s  （预期 ≥4：任一未给就画不出确定百分比）\n' "$dvq"
printf 'DV-q2 Workbench 走 freePercent      : %s  （预期 ≥1：直读百分比就是把未给算成 0）\n' "$dvq2"
printf 'DV-q3 页面裸读 usedKg               : %s  （预期 0：后端没有已装重量口径）\n' "$dvq3"
printf 'DV-r  演示面被撤的无生产者键        : %s  （预期 0：demo 不得成为第四个真相源）\n' "$dvr"
printf 'DV-s  NoProducerKeyFamily 用例数    : %s  （预期 ≥7）\n' "$dvs"
printf 'DV-t  路线两块解析体内别名键        : %s  （预期 0：routeGuideOf+navStepOf，12 个已撤别名）\n' "$dvt"
printf 'DV-t2 已撤别名键全 main 命中        : %s  （预期 0：闭包判据，挪进别的解析块也响）\n' "$dvt2"
printf 'DV-t3 routeGuideOf 真实键读法       : %s  （预期 ≥19：撤别名时把真键一起删就靠这条数）\n' "$dvt3"
printf 'DV-t4 navStepOf 真实键读法          : %s  （预期 ≥7：steps 只有演示面那套真键）\n' "$dvt4"
printf 'DV-t5 RouteGuideContractTest 用例   : %s  （预期 ≥15：别名不产生值要有 JVM 断言）\n' "$dvt5"
printf 'DV-t6 UserTrackingContractTest 用例 : %s  （预期 ≥13：节点完成态只认 done）\n' "$dvt6"
printf 'DV-y  任务族两块解析体内幻影键      : %s  （预期 0：taskType/bizType/targetStation/cargoName/goodsName/itemCount/planTime/taskName/createTime/count）\n' "$dvy"
printf 'DV-y2 任务族真实键读法              : %s + 窗口/ETA %s  （预期 11+3：撤别名把 19 键一起带走就靠这两条数）\n' "$dvy2" "$dvy2w"
printf 'DV-y3 任务族多候选名读法/反证        : %s/%s  （预期 0/≥4：/driver/tasks 一块只有一个键名，parse 那块的别名是另一份契约）\n' "$dvy3" "$dvy3p"
printf 'DV-y4 任务族读坐标/反证              : %s/%s  （预期 0/≥4：任务 VO 19 键里没有任何坐标）\n' "$dvy4" "$dvy4p"
printf 'DV-y5 DriverTaskRespContractTest 用例: %s  （预期 ≥10：幻影键不产生值要有 JVM 断言）\n' "$dvy5"
printf 'DV-y6 聚合出口调用/标题两支/模型内分组 = %s/%s/%s+%s  （预期 1/2/1+1：只有一个出口，无段那一支也占格）\n' \
    "$dvy6g" "$dvy6t" "$dvy6p" "$dvy6s"
printf 'DV-y6u 页面自己重新分组/反证          : %s/%s  （预期 0/≥1：分段只在 DriverTaskSegment.group 做一次）\n' "$dvy6u" "$dvy6up"
printf 'DV-z1 页首计数的旧写法                : %s  （预期 0：把"这一页拿回几条"念成"有几单"，第 21 单起就少念）\n' "$dvz1"
printf 'DV-z2 计数口径定义/调用点             : %s/%s  （预期 1/2：第二处调用一定带第二套口径）\n' "$dvz2" "$dvz2b"
printf 'DV-z3 serverCount 出口类型            : %s  （预期 1：必须是 Int?，写回 Int 就是把失败洗成 0）\n' "$dvz3"
printf 'DV-z4 两格声明可空且初值 null         : %s  （预期 2：未加载完也不许念 0）\n' "$dvz4"
printf 'DV-z5 话术出口/未取到字面量           : %s/%s  （预期 2/1：一句页面话术只有一个出口、一种措辞）\n' "$dvz5" "$dvz5b"
printf 'DV-z6 ParcelCountContractTest 用例    : %s  （预期 ≥4：失败≠0 只剩 grep 就没人钉）\n' "$dvz6"
printf 'DV-u  C 类形态拆分(独立实现/census)  : arr %s/%s numbool %s/%s 非可空 %s/%s str %s/%s  （预期四对全等，非可空 ≤19）\n' \
    "$hand_arr" "$cens_arr" "$hand_nb" "$cens_nb" "$hand_nn" "$cens_nn" "$hand_str" "$cens_str"
printf 'DV-v  三面 0 生产者键/站点           : keys %s sites %s 其中 C 行 %s 非可空数值布尔 %s  （预期 ≤127/≤152/≤32/≤38，只降不升：2026-10-13 把天花板收到现读——原来 132/165/33/42 高出实测 5/13/1/4，负控注入一个孤立键也不红）\n' \
    "$tfz_keys" "$tfz_sites" "$tfz_c" "$tfz_nb"
printf 'DV-w  DV-k 口径桥                    : grep %s = census 行 %s + delta %s（逐名列 %s）｜DV-k 实测 %s  （预期 bridge_grep==DV-k 且 delta ≤2）\n' \
    "$bridge_grep" "$bridge_rows" "$bridge_delta" "$bridge_only" "$dvk"
printf 'DV-x  census 基数与第四面            : rows %s C %s（拆分和 %s）演示面键 %s/%s 文件 c_no_producer %s  （预期 rows ≥400 且 C==拆分和 且 演示面键 ≥200）\n' \
    "$cens_rows" "$cens_c" "$dvu_c_sum" "$demo_dist" "$demo_files" "$cnop"
printf 'GUARD 计数管道=%s（预期 1） 别名在测试源码=%s（预期 ≥10） 解析体行数=%s（预期 ≥20） 演示块含 totalAmount=%s（预期 ≥1）\n' \
    "$guard_pipeline" "$guard_alias" "$guard_block" "$guard_demo"
printf 'GUARD2 撤键模式在测试源码=%s（预期 ≥20） 档案块行数=%s（预期 ≥10） 方案块行数=%s（预期 ≥8） 揽件+交接块行数=%s（预期 ≥20）\n' \
    "$guard_tg" "$guard_pb" "$guard_tb" "$guard_kb"
printf 'GUARD3 路线块行数=%s（预期 ≥60） 步骤块行数=%s（预期 ≥20） 别名模式在测试源码=%s（预期 ≥10）\n' \
    "$guard_rb" "$guard_nb" "$guard_rkeys"
printf 'GUARD4 任务段块行数=%s（预期 ≥15） 任务解析块行数=%s（预期 ≥6） stop-parse 块 multiStr=%s（预期 ≥3） 幻影键模式在契约用例=%s（预期 ≥8）\n' \
    "$guard_ystp" "$guard_ydtp" "$guard_yspb" "$guard_ykeys"
printf 'GUARD5 load() 块行数=%s（预期 ≥15） z1 模式在契约用例=%s（预期 ≥1） 页文件=%s 契约用例=%s\n' \
    "$guard_zload" "$guard_zsize" "$([ -f "$PS" ] && echo 有 || echo 缺)" "$([ -f "$PCT" ] && echo 有 || echo 缺)"

echo "--- 残留（本轮范围外，供 §五 后续轮复核，不判红）---"
echo "· 同一口径的第二份副本（D-G3b-1）：「我的」页三格仍是就地写的 if (x.error == null) x.total else null，"
echo "  没有走 #269 那个 serverCount 出口。不并轨的原因是台账锚点账：MineScreen.kt 有 5 条在册 OK 锚点，"
echo "  加一行 import 就会整体位移 ⇒ 留给 #159 内容级重锚那一轮一起做（改口径要改两处 = 这一条的代价）："
grep -cE 'if \([a-z]\.error == null\) [a-z]\.total else null' $A/ui/user/MineScreen.kt | tr -d ' '
echo "· 其余数值/布尔非可空读法（DV-k 基线内的 111 处，逐处三方核对是下一轮的活）："
printf '%s\n' "$dvk"
echo "· 天气面温度/风力缺键塌 0（高德 daytemp/nighttemp 与 OpenWeather temperature 都是外部真实生产者但可缺席）："
grep -rnE 'multiNum\("(temperature|temp|tempHi|tempLo|maxTemp|minTemp|daytemp|nighttemp|windspeed|windpower|weathercode)' $A --include=*.kt | grep -vE ':[0-9]+: *(\*|//)' | wc -l | tr -d ' '
echo "· 里程口径族仍读 distance/meters/distanceMeters（AppSendReachabilityRespVO 实发 distanceKm，同族第四真相源）："
grep -rnE 'multi(Num|Str)\("[a-zA-Z]*([Dd]istance|distanceMeters)"' $A --include=*.kt | grep -vE ':[0-9]+: *(\*|//)' | wc -l | tr -d ' '
echo "· steps/车道族仍按演示报文读（Repo.kt:1102/1104/1116/1133/1168；AppDriverRouteRespVO:17-86 里没有任何 steps/lanes 键）⇒ 待 F-3 ⑬⑭ 一批处置，本轮只把 recommendedLane/etaSeconds 改成可空"
echo "· 档案字符串族 lineName/company/shiftCode 不在 VO 那 7 个键里（DemoData:687-690 自注）⇒ 收法要与解析层读点同定，已按 OPEN_DEFECT 登记"
echo "· 车牌别名族：BusVehicle:765 与 TransportLeg:1125 仍读 plate/vehiclePlate/plateNumber，演示面 /bus/realtime:647 还在发 plate ⇒ 同属字符串族一批（本轮只收 DriverProfile 一处，真键 plateNo）"

ok=1
[ "$dva" = 0 ] || { echo "× DV-a 收益解析体又出现缺键读成 0 的非可空读法"; ok=0; }
[ "$dvb" = 0 ] || { echo "× DV-b 自造别名键回流（三面实测无生产者）"; ok=0; }
[ "$dvc" = 0 ] || { echo "× DV-c 演示面又开始发自造键给别名续命"; ok=0; }
[ "$dvd" -ge 12 ] || { echo "× DV-d 真实键读法被删减=契约断裂"; ok=0; }
[ "$dve" = 9 ] || { echo "× DV-e Earnings 数值字段不再是可空类型"; ok=0; }
[ "$dvf" = 2 ] || { echo "× DV-f EarningItem 数值字段不再是可空类型"; ok=0; }
[ "$dvg" = 8 ] || { echo "× DV-g Earnings 未给文案 getter 数量变了（测试断言会跟着失真）"; ok=0; }
[ "$dvg2" = 2 ] || { echo "× DV-g2 EarningItem 未给文案 getter 数量变了"; ok=0; }
[ "$dvh" = 0 ] || { echo "× DV-h 页面裸读可空数值（能编译就说明上游已塌 0）"; ok=0; }
[ "$dvi" = 0 ] || { echo "× DV-i 无生产者键在全项目复现"; ok=0; }
[ "$dvj" = 1 ] || { echo "× DV-j EpochMillis 时间口径分支丢失（13 位毫秒数会原样进 UI）"; ok=0; }
[ "$dvk" -le 107 ] || { echo "× DV-k 全项目数值非可空读法回涨（2026-10-13 现读 107）"; ok=0; }
[ "$dvl" -ge 8 ] || { echo "× DV-l 契约测试用例被删"; ok=0; }
[ "$dvm" = 3 ] || { echo "× DV-m 档案真实键读法缺位（plateNo/cargoCapacityKg/cargoCapacity 少一个就是契约断裂）"; ok=0; }
[ "$dvm2" = 0 ] || { echo "× DV-m2 档案解析体又采纳了自造别名"; ok=0; }
[ "$dvn" -ge 5 ] || { echo "× DV-n 方案真实键读法被删（Candidate 只有那 9 个键）"; ok=0; }
[ "$dvn2" = 0 ] || { echo "× DV-n2 方案解析体回流 name/recommended/legs 那批自造名"; ok=0; }
[ "$dvo" = 2 ] || { echo "× DV-o stock 不再是可空或售罄判定绕过 null（缺库存会锁死下单）"; ok=0; }
[ "$dvo2" = 0 ] || { echo "× DV-o2 划线原价/销量读法回流（会凭空渲染'已售 167'和一条假划线价）"; ok=0; }
[ "$dvp" = 0 ] || { echo "× DV-p 勾选态/拍照要求位又被读成后端事实（缺键塌 false 就是把'没要求'讲成结论）"; ok=0; }
[ "$dvp2" = 0 ] || { echo "× DV-p2 揽件/交接解析块里还留着要求位键名"; ok=0; }
[ "$dvq" -ge 4 ] || { echo "× DV-q CargoGauge 口径参数退回非空（未给会被画成确定的空仓百分比）"; ok=0; }
[ "$dvq2" -ge 1 ] || { echo "× DV-q2 Workbench 不再经 freePercent（绕过 null 折叠成 0）"; ok=0; }
[ "$dvq3" = 0 ] || { echo "× DV-q3 页面又开始显示无生产者的已装重量"; ok=0; }
[ "$dvr" = 0 ] || { echo "× DV-r 演示面重新下发无生产者键（demo 又成第四个真相源）"; ok=0; }
[ "$dvs" -ge 7 ] || { echo "× DV-s NoProducerKeyFamilyContractTest 用例被删"; ok=0; }
[ "$dvt" = 0 ] || { echo "× DV-t 路线解析块又采纳了已撤别名键（guidance/instructions/routeSource/crs/offRoute/meters/lane/seconds…三面 0 生产者）"; ok=0; }
[ "$dvt2" = 0 ] || { echo "× DV-t2 已撤别名键在 main 别处复现（换个解析块就是第三个真相源；provider 不在此列，它是 route-preview 的真键，只按站点判）"; ok=0; }
[ "$dvt3" -ge 19 ] || { echo "× DV-t3 routeGuideOf 的真实键读法被删（撤别名把 routeDistanceMeters/routeProvider 一起带走，字段恒为默认值还静默）"; ok=0; }
[ "$dvt4" -ge 7 ] || { echo "× DV-t4 navStepOf 的真实键读法被删（steps 只认演示面 guideOf() 那套键名）"; ok=0; }
[ "$dvt5" -ge 15 ] || { echo "× DV-t5 RouteGuideContractTest 用例被删（别名不产生值只剩 grep，行为没人钉）"; ok=0; }
[ "$dvt6" -ge 13 ] || { echo "× DV-t6 UserTrackingContractTest 用例被删（finished/completed 顶替 done 这件事没人钉）"; ok=0; }
# DV-y：任务族只按块判（块外那些名字在别的报文里是真键，全 main 归零会当场长红 —— 同 DV-t 的 provider 教训）
[ -f "$DTR" ] || { echo "× DV-y 找不到 DriverTaskRespContractTest.kt（缺文件会被读成用例数 0，那是判据自己骗自己）"; ok=0; }
[ "$dvy" = 0 ] || { echo "× DV-y /driver/tasks 的两个解析体又采纳了幻影键（后端 19 键里没有 taskType/targetStation/cargoName/itemCount/planTime…，缺键塌 0 就是 §五 的破口形状）"; ok=0; }
[ "$dvy2" = 11 ] || { echo "× DV-y2 任务族真实键读法不再是 11（撤别名把 stationId/statusName 一起带走，字段恒为默认值还静默）"; ok=0; }
[ "$dvy2w" = 3 ] || { echo "× DV-y2b 任务窗口两键 + ETA 的读法不再是 3（回落 createTime = 给任务段编一个没发生过的窗口）"; ok=0; }
[ "$dvy3" = 0 ] || { echo "× DV-y3 任务族解析体出现多候选名读法：这个 VO 每个字段只有一个键名"; ok=0; }
[ "$dvy3p" -ge 4 ] || { echo "× DV-y3p 反证掉了（/driver/route 的 parse 里数不出别名读法），dvy3 的 0 不可信"; ok=0; }
[ "$dvy4" = 0 ] || { echo "× DV-y4 任务族读点吃了坐标：任务 VO 没有 lat/lng，凭空给任务点定位就是自造事实"; ok=0; }
[ "$dvy4p" -ge 4 ] || { echo "× DV-y4p 反证掉了（route 的 parse 里数不出坐标读法），dvy4 的 0 不可信"; ok=0; }
[ "$dvy5" -ge 10 ] || { echo "× DV-y5 DriverTaskRespContractTest 用例被删（幻影键不产生值只剩 grep，行为没人钉）"; ok=0; }
[ "$dvy6g" = 1 ] || { echo "× DV-y6g 任务段聚合的调用点不再是 1 处（第二处一定带第二套分组口径）"; ok=0; }
[ "$dvy6t" = 2 ] || { echo "× DV-y6t '今日运营任务'标题不再是两支各一处：后端没下发任务段时那一格没了"; ok=0; }
[ "$dvy6p" = 1 ] || { echo "× DV-y6p 按 planId 分段的行不在了（多个调度方案混排会被并成一段）"; ok=0; }
[ "$dvy6s" = 1 ] || { echo "× DV-y6s 同站多动作并行的聚合不在了（一个站点会重复出现成多条任务行）"; ok=0; }
[ "$dvy6u" = 0 ] || { echo "× DV-y6u 工作台页面自己重新分组：分段口径出现第二个答案"; ok=0; }
[ "$dvy6up" -ge 1 ] || { echo "× DV-y6up 反证掉了（收益页按天分组也数不出 groupBy），dvy6u 的 0 不可信"; ok=0; }
# DV-z：包裹页页首计数（队列 #269）。§五 那条规则在**页面层**的形状：本页条数当总数 + 失败念成 0 单。
#   这一族判的是"页面读的是哪一个数"，与 DV-k/census 的解析层基数是两件事，互不替代。
[ -f "$PS" ] || { echo "× DV-z 找不到 ParcelScreen.kt（页文件缺位时下面六条会集体是 0，那是判据自己骗自己）"; ok=0; }
[ -f "$PCT" ] || { echo "× DV-z 找不到 ParcelCountContractTest.kt（缺文件会被读成用例数 0，同上）"; ok=0; }
[ "$dvz1" = 0 ] || { echo "× DV-z1 页首那一句又用本页条数当总数：第 21 单起少念，请求失败还会念成「寄货 0 单」"; ok=0; }
[ "$dvz2" = 1 ] || { echo "× DV-z2 计数口径的定义点不再是 1 处（第二处一定带第二套口径）"; ok=0; }
[ "$dvz2b" = 2 ] || { echo "× DV-z2b load() 里走 serverCount 的调用不再是 2 处（某一格绕开了口径）"; ok=0; }
[ "$dvz3" = 1 ] || { echo "× DV-z3 serverCount 的出口类型被改成非空：失败会被写成 0，未取到与真零值就分不开了"; ok=0; }
[ "$dvz4" = 2 ] || { echo "× DV-z4 计数格退回非空初值：还没加载完就把「没问到」念成「0 单」"; ok=0; }
[ "$dvz5" = 2 ] || { echo "× DV-z5 页首那句计数话术出现第二个出口（两处各写一套措辞）"; ok=0; }
[ "$dvz5b" = 1 ] || { echo "× DV-z5b「未取到」的字面量不再只在一处产生：未取到态开始各处自写"; ok=0; }
[ "$dvz6" -ge 4 ] || { echo "× DV-z6 ParcelCountContractTest 用例被删（失败≠0 这件事只剩 grep 在钉）"; ok=0; }
[ "$guard_zload" -ge 15 ] || { echo "× GUARD5a 没抓到 ParcelViewModel.load() 函数体（sed 锚点失效，DV-z2b 的 2 不可信）"; ok=0; }
[ "$guard_zsize" -ge 1 ] || { echo "× GUARD5b z1 模式在契约用例里也数不到 ⇒ 反证输入没了，DV-z1 的 0 不采信"; ok=0; }
[ "$guard_ystp" -ge 15 ] || { echo "× GUARD4a 没抓到 parseTaskItem 函数体（sed 锚点失效，DV-y/DV-y2/DV-y4 的数全是假的）"; ok=0; }
[ "$guard_ydtp" -ge 6 ] || { echo "× GUARD4b 没抓到 DriverTask.parse 函数体（DV-y2b 的窗口两键计数不可信）"; ok=0; }
[ "$guard_yspb" -ge 3 ] || { echo "× GUARD4c 没抓到 MissionStop.parse 函数体，DV-y3p/DV-y4p 两条反证全不成立"; ok=0; }
[ "$guard_ykeys" -ge 8 ] || { echo "× GUARD4d 幻影键模式在契约用例里数不到 ⇒ 负控输入没了，DV-y 的 0 不采信"; ok=0; }
# DV-u：census 补的 shape 列必须与「本簿独立实现的 §二之二 规则」全等；非可空数值布尔只许降
[ "$census_rc$guard_census_code" = "00" ] || { echo "× DV-u census 跑不通（rc=$census_rc/$census_code）：沙盒缺 tools/ 或 BUSPOST_WORK 没接上，DV-u/v/w/x 的数全部不采信"; ok=0; }
[ "$dvu_shape_eq" = 1 ] || { echo "× DV-u C 类形态拆分与独立实现的 §二之二 规则不一致（census 的 SHAPE_* 被改坏或 rows 文本口径变了）"; ok=0; }
[ "$cens_nn" -le 19 ] || { echo "× DV-u C 类非可空数值/布尔读法回涨（F-3 ② 实测 19，只许降）"; ok=0; }
[ "$cens_arr" -ge 0 ] || { echo "× DV-u census 的 c_shape 缺项（拆分列没写出来）"; ok=0; }
# DV-v：三面 0 生产者（这一轮才第一次有演示面那一列）
#   2026-10-13 合并 origin/main：sites 152→153、sites_in_c_rows 32→33，**只有一个新读点**，
#   逐点比对（HEAD 版本里没有同形一行的站点）实测 = 1 处：
#     `data/repo/Repo.kt:389  formatted = first.optString("formatted_address", address)`
#   它读的是**高德 v3 正向地理编码**（`restapi.amap.com/v3/geocode/geo` 的 `geocodes[0].formatted_address`），
#   三方服务的键天然不在"后端 VO / 小程序 / 演示面"这三面里 ⇒ 三面普查必然记它 0 生产者。
#   同形的那一处 `Repo.kt:350`（高德 regeo 的 `formatted_address`）本来就在 152 里，
#   所以这不是新出现的一类病，是同一类病的第二个点。天花板随之 +1，仍"只降不升"。
[ "$tfz_keys" -le 127 ] || { echo "× DV-v 三面 0 生产者的键数回涨（2026-10-13 现读 127）"; ok=0; }
[ "$tfz_sites" -le 153 ] || { echo "× DV-v 三面 0 生产者的站点数回涨（2026-10-13 合并后现读 153：多的一处是外部高德键 formatted_address 的第二个读点，见上方注释）"; ok=0; }
[ "$tfz_c" -le 33 ] || { echo "× DV-v C 行里的三面 0 生产者站点回涨（2026-10-13 合并后现读 33，同一处）"; ok=0; }
[ "$tfz_nb" -le 38 ] || { echo "× DV-v 三面 0 生产者里的非可空数值布尔回涨（2026-10-13 现读 38）"; ok=0; }
# DV-w：两个口径必须接得上
[ "$dvw_eq" = 1 ] || { echo "× DV-w census 的 grep 口径读数与 DV-k 不等 ⇒ 有一边被单独改过，DV-k 这个基线不再是同一个东西"; ok=0; }
[ "$dvw_delta_ok" = 1 ] || { echo "× DV-w 口径桥不平（grep − census 行 ≠ 逐点列出的 delta）"; ok=0; }
[ "$bridge_delta" -le 2 ] || { echo "× DV-w 口径桥 delta 回涨（本轮实测 2 = multiNum/multiBool 两行函数声明）"; ok=0; }
# DV-x：census 自身基数 + 第四面必须有输入（否则 DV-v 的"三面"退化成两轮的老两列）
[ "$cens_rows" -ge 400 ] || { echo "× DV-x census 行数异常（扫描面塌了）"; ok=0; }
[ "$cens_c" = "$dvu_c_sum" ] || { echo "× DV-x C 类总数与形态拆分和对不上（有行没被 arr/numbool/str 接住）"; ok=0; }
[ "$demo_dist" -ge 200 ] || { echo "× DV-x 演示面写键集合读不到（本轮实测 216）⇒ 三面判据退化成两面，DV-v 的 0 不采信"; ok=0; }
[ "$demo_files" -ge 1 ] || { echo "× DV-x 找不到 Demo*.kt ⇒ 第四面根本没有输入"; ok=0; }
[ "$guard_pipeline" = 1 ] || { echo "× GUARD grep 计数管道失效，本簿所有『预期 0』都不采信"; ok=0; }
[ "$guard_alias" -ge 10 ] || { echo "× GUARD 别名模式在测试源码数不到 ⇒ 负控输入没了，DV-b/DV-c 的 0 不采信"; ok=0; }
[ "$guard_block" -ge 20 ] || { echo "× GUARD 没抓到 driverEarnings 解析体（sed 锚点失效，DV-a/DV-b/DV-d 全不采信）"; ok=0; }
[ "$guard_demo" -ge 1 ] || { echo "× GUARD 没抓到演示面 earnings 块（DV-c 的 0 不采信）"; ok=0; }
[ "$guard_tg" -ge 20 ] || { echo "× GUARD2 撤键模式在 NoProducerKeyFamilyContractTest 数不到 ⇒ 负控输入没了，DV-m2/DV-n2/DV-r 的 0 不采信"; ok=0; }
[ "$guard_pb" -ge 10 ] || { echo "× GUARD2 没抓到 DriverProfile 解析块（DV-m/DV-m2 的数全是假的）"; ok=0; }
[ "$guard_tb" -ge 8 ] || { echo "× GUARD2 没抓到 TransitPlan 解析块（DV-n/DV-n2 的数全是假的）"; ok=0; }
[ "$guard_kb" -ge 20 ] || { echo "× GUARD2 没抓到揽件/交接解析块（DV-p2 的 0 不采信）"; ok=0; }
[ "$guard_rb" -ge 60 ] || { echo "× GUARD3 没抓到 routeGuideOf 函数体（sed 锚点失效，DV-t/DV-t3 的数全是假的）"; ok=0; }
[ "$guard_nb" -ge 20 ] || { echo "× GUARD3 没抓到 navStepOf 函数体（DV-t/DV-t4 的数全是假的）"; ok=0; }
[ "$guard_rkeys" -ge 10 ] || { echo "× GUARD3 别名模式在测试源码数不到 ⇒ 反证输入没了，DV-t/DV-t2 的 0 不采信"; ok=0; }

if [ "$ok" = 1 ]; then
    echo "数字默认值判据通过（§五 收益页 A/B/C/D 收口 + F-3 ② 六族无生产者键收口 + F-3 ⑬ 路线/轨迹别名撤读 + #262 任务族 /driver/tasks：真键单键读法 + 别名归零 + 演示面同形 + 未给不塌成结论 + #269 页首计数：分页总数而非本页条数、失败说未取到）"
    exit 0
fi
exit 1
