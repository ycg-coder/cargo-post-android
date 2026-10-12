#!/usr/bin/env bash
# #55 用户端「查件票面」（`GET /app-api/transport/send/topology`）的结构判据（配 tools/parcel_ticket_negative_controls.sh）
# 管六件事：那条零消费者通路真的被接上了、入口恰好一个、判定与措辞只在纯层、
# 口径（换乘等待 / 估算里程）不离开生产者说过的那句、交接与时间线只认真接口发的键名、可空键不许塌成结论。
# 用法：bash tools/check_parcel_ticket_probes.sh        退出码非 0 = 这一格被拆了
# 写法沿用本仓规矩：按内容过滤不按行号；每个"目标 0"都配同文件正对照；注释里点名某句话**不算**把那句话写进了产品。
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
cd "$ROOT" || exit 2
M=app/src/main/java/com/shanxiang/buspost
RT=$M/nav/Routes.kt
BR=$M/ui/BusPostRoot.kt
TS=$M/ui/user/TraceScreen.kt
PT=$M/ui/user/ParcelTicket.kt
PS=$M/ui/user/ParcelTicketScreen.kt
MD=$M/data/model/Models.kt
RP=$M/data/repo/Repo.kt
DD=$M/data/repo/DemoData.kt
CT=app/src/test/java/com/shanxiang/buspost/ui/user/ParcelTicketContractTest.kt
NC='^[0-9]+:[[:space:]]*(\*|//|/\*)'

for f in "$RT" "$BR" "$TS" "$PT" "$PS" "$MD" "$RP" "$DD" "$CT"; do
  [ -f "$f" ] || { echo "× 缺 $f：判据没有输入，本轮读数不采信"; exit 2; }
done

nc_count() { grep -n "$2" "$1" | grep -cvE "$NC" || true; }
nc_all() {
  grep -rn "$1" "$M" --include=*.kt | sed 's/^[^:]*:[0-9]*://' | grep -cvE '^[[:space:]]*(\*|//|/\*)' || true
}

# PT-a 路由常量 / 注册 / 走没走 BusRouteBody
PTA_CONST=$(grep -c 'const val PARCEL_TICKET = "parcelTicket/{no}"' "$RT")
PTA_BUILDER=$(grep -c 'fun parcelTicket(no: String) = "parcelTicket/" + encodeSegment(no)' "$RT")
# 注册那行是换行写的（composable( + Routes.PARCEL_TICKET,）⇒ 钉带逗号那一行，别钉整句
PTA_REG=$(grep -c 'Routes.PARCEL_TICKET,' "$BR")
PTA_BODY=$(grep -c 'BusRouteBody { ParcelTicketScreen(' "$BR")

# PT-b 入口恰好一个（追踪页里那一行；注释里提路由名不算第二个入口）
PTB=$(nc_count "$TS" 'nav.navigate(Routes.parcelTicket(no))')
PTB_ALL=$(nc_all 'nav.navigate(Routes.parcelTicket(')

# PT-c 死通路接活：Repo.kt:780 那条 parcelTopology 此前全仓零消费者
PTC_DEF=$(grep -c 'suspend fun parcelTopology(no: String): One<ParcelTrace>' "$RP")
PTC_CALL=$(nc_all 'Repo.parcelTopology(')
PTC_PAGE=$(nc_count "$PS" 'Repo.parcelTopology(number).toState()')

# PT-d 措辞只在纯层：页面不许自己写"票面没取到 / 没给内容"（两处文字各自漂移的开端）
PTD1=$(nc_count "$PS" 票面没取到)
PTD2=$(nc_count "$PS" 没给这张单的票面内容)
PTD_P1=$(nc_count "$PT" 票面没取到)
PTD_P2=$(nc_count "$PT" 没给这张单的票面内容)

# PT-e 口径句子只在纯层：换乘等待那半句、里程"含估算段"那半句，都不许在页面里重抄
PTE1=$(nc_count "$PS" 每次 20 分钟)
PTE_P1=$(nc_count "$PT" "每次 20 分钟")
PTE_P2=$(nc_count "$PT" 换乘次数未给)
PTE_P3=$(nc_count "$PT" 含估算段，不是真实道路里程)
PTE_P4=$(nc_count "$PT" 各段真实道路里程相加)
PTE_P5=$(nc_count "$PT" 口径未给)
#   页面不许自己拼公里数（那会绕过上面的口径分支）
PTE_KM=$(nc_count "$PS" ' km')

# PT-f 交接只认真键（`OrderTopologyRespVO.Handover:113-127`）；别名在**那一段解析里**归零
PTF_SEG=$(sed -n '/^data class LegHandover/,/^}/p' "$MD")
PTF_FROM=$(printf '%s\n' "$PTF_SEG" | grep -c 'multiStr("fromPlateNo")')
PTF_TO=$(printf '%s\n' "$PTF_SEG" | grep -c 'multiStr("toPlateNo")')
PTF_TIME=$(printf '%s\n' "$PTF_SEG" | grep -c 'reportTimeOf("handoverCompletedAt", "handoverStartedAt", "arrivedAt")')
# ⚠ 这一段原来数的是"几个**已知**别名没回来"（黑名单）：新增一个没见过的自造键（如 nextDriver）照样绿
#   —— 本轮写负控时实测到这个形状。改成**白名单**：这段解析里出现的每个键名都必须是
#   `OrderTopologyRespVO.Handover:115-126` 的真键，多一个就红；基数也钉住（少一格 = 某一格又不读了）。
PTF_WHITELIST='^(stationName|fromPlateNo|toPlateNo|fromDriverName|toDriverName|handoverCompletedAt|handoverStartedAt|arrivedAt|itemCount|statusName|exceptionReason)$'
PTF_KEYS=$(printf '%s\n' "$PTF_SEG" | grep -oE '"[A-Za-z]+"' | tr -d '"' | sort -u)
PTF_COUNT=$(printf '%s\n' "$PTF_KEYS" | grep -c .)
PTF_ALIAS=$(printf '%s\n' "$PTF_KEYS" | grep -vcE "$PTF_WHITELIST" || true)
#   同一段里的正对照：真键数得出来 ⇒ 上面那个 0 不是"段没取到"
PTF_PROOF=$(printf '%s\n' "$PTF_SEG" | grep -c 'itemCount = o.multiNumOrNull("itemCount")')

# PT-g 时间线与时刻：只认 eventTypeName/eventTime/detail；旧的裸切串只在归一函数里出现一次
PTG_LABEL=$(grep -c 'multiStr("eventTypeName"' "$RP")
PTG_TIME=$(grep -c 'nodeTimeOf("eventTime", "time", "createTime", "nodeTime")' "$RP")
PTG_DEF=$(grep -c 'fun JSONObject.nodeTimeOf' "$MD")
PTG_OLD=$(nc_count "$RP" "substringAfter(' ').take(5)")
# ⚠ 只能钉在归一函数那一段里：Models.kt 另有两处 `substringAfter(' ').take(5)`（班次起讫时刻，
#   那两个键后端给的就是 ISO/空格串，不是这条链路），整文件数会得到 4 而不是 1。
PTG_OLD_PROOF=$(awk '/^fun JSONObject.nodeTimeOf/,0' "$MD" | head -3 | grep -c "substringAfter(' ').take(5)")

# PT-h 可空键不许塌成结论：Candidate.durationMinutes / Handover.itemCount 必须是可空读法
PTH_BAD1=$(grep -c 'multiNum("durationMinutes")' "$MD")
# ⚠ 段内数：`SendOrder.itemCount`（Models.kt:1036）与司机端交接单（:1714）是**另外的 VO**，
#   本轮只收 LegHandover/TransitPlan 那两格，把整文件当扫描面会误伤别的契约（实测整文件 = 2）
PTH_BAD2=$(printf '%s
' "$PTF_SEG" | grep -c 'multiNum("itemCount"')
PTH_OK1=$(grep -c 'minutes = o.multiNumOrNull("durationMinutes")?.toInt()' "$MD")
PTH_OK2=$(grep -c 'val minutes: Int?' "$MD")
PTH_OK3=$(printf '%s
' "$PTF_SEG" | grep -c 'val itemCount: Int?')
#   chosen 三态：null 必须有第三种说法，不许塌成"不推荐"
PTH_CHOSEN=$(grep -c '后端没说（chosen 未给）' "$PT")

# PT-i 四格各有出口 + 兜底出口在场
PTI_L=$(grep -c 'ParcelTicketKind.Loading,' "$PT")
# ⚠ 四格光数"产出点"不够：把 enum 里那一头摘掉（负控 PT-i1 实测过这个形状：三值 enum + 页面少一个分支，
#   四个产出计数照样是 1/1/1/1 —— 编译会破，但这是文本判据，看不见编译）。所以 enum 声明行单独钉一次。
PTI_ENUM=$(grep -c 'enum class ParcelTicketKind { Loading, Failed, Absent, Shown }' "$PT")
PTI_F=$(grep -c 'ParcelTicketKind.Failed,' "$PT")
PTI_A=$(grep -c 'ParcelTicketKind.Absent,' "$PT")
PTI_S=$(grep -c 'ParcelTicketKind.Shown,' "$PT")
PTI_BACK=$(nc_count "$PS" 'BackToTraceRow {')
PTI_BACK_DEF=$(grep -c 'private fun BackToTraceRow(' "$PS")

# PT-j 初值必须是"还没取过结论"
PTJ=$(grep -c 'RemoteState<ParcelTrace>(loading = true)' "$PS")
PTJ_BAD=$(grep -c 'RemoteState<ParcelTrace>(data = ' "$PS")

# PT-k 演示面与契约同键：拓扑信封那 10 个键只挂在 /send/topology 分支上（不污染 /send/track）
PTK_GATE=$(grep -c 'val topo = path.endsWith("/send/topology")' "$DD")
PTK_RETURN=$(grep -c 'return@let it' "$DD")
# ⚠ 光数"有 return@let"挡不住把闸门写死成永真（负控 PT-k2 实测：`if (!topo)` 变成 `if (false)` 时
#   上面三个计数一个都不掉，而信封键就漏到 /send/track 那份报文上了）⇒ 把闸门那一行整句钉住。
PTK_GUARD=$(grep -c 'if (!topo) return@let it' "$DD")
PTK_KEYS=$(grep -c 'put("originStationName"' "$DD")
# ⚠ 段内数：`/driver/handover` 那条演示报文里 handoverTime 是**它自己 VO 的真键**（DemoData:774/780），
#   整文件扫会把对的东西判成错（实测整文件 = 2）。这里只看拓扑交接那一段。
PTK_HSEG=$(sed -n '/private fun handoversJson/,/^    )$/p' "$DD")
PTK_DEMO_ALIAS=$(printf '%s
' "$PTK_HSEG" | grep -c '"handoverTime"')
PTK_DEMO_REAL=$(printf '%s
' "$PTK_HSEG" | grep -c '"fromPlateNo"')

# PT-l 契约用例在场（行为侧那半边的底盘；删用例要这里先红）
PTL=$(grep -cE '^[[:space:]]*@Test' "$CT")

echo "PT-a 常量/构造/注册/BusRouteBody   = $PTA_CONST/$PTA_BUILDER/$PTA_REG/$PTA_BODY （目标 1/1/1/1）"
echo "PT-b 入口（本页 / 全工程）          = $PTB/$PTB_ALL （目标 1/1）"
echo "PT-c 定义 / 真调用 / 页面取数点      = $PTC_DEF/$PTC_CALL/$PTC_PAGE （目标 1/1/1：此前是零消费者的死代码）"
echo "PT-d 页面自带措辞 / 纯层反证         = $PTD1+$PTD2/$PTD_P1+$PTD_P2 （目标 0/1+1）"
echo "PT-e 页面重抄口径 / 纯层五处口径     = $PTE1/$PTE_P1+$PTE_P2+$PTE_P3+$PTE_P4+$PTE_P5（目标 0/1+1+1+1+1）、页面 ' km' = $PTE_KM（目标 0）"
echo "PT-f 真键(交出/接收/时刻) / 键基数 / 白名单外 = $PTF_FROM/$PTF_TO/$PTF_TIME / $PTF_COUNT / $PTF_ALIAS（目标 1/1/1 / 恰好 11 / 0）、正对照 itemCount = $PTF_PROOF（目标 1）"
echo "PT-g 时间线键 / 归一函数 / 旧裸切串  = $PTG_LABEL/$PTG_TIME/$PTG_DEF / Repo $PTG_OLD、Models 正对照 $PTG_OLD_PROOF（目标 1/1/1/0/1）"
echo "PT-h 塌成 0 的坏形 / 可空读法         = $PTH_BAD1+$PTH_BAD2/$PTH_OK1+$PTH_OK2+$PTH_OK3（目标 0/1+1+1）、chosen 三态 $PTH_CHOSEN（目标 1）"
echo "PT-i 四格产出 L/F/A/S / 兜底出口     = $PTI_L/$PTI_F/$PTI_A/$PTI_S / $PTI_BACK+$PTI_BACK_DEF（目标 1/1/1/1 / 恰好 3+1）"
echo "PT-j 初值 loading / 初值写成空        = $PTJ/$PTJ_BAD （目标 1/0）"
echo "PT-k 闸门 / 信封键 / return@let / 闸门整句 / 交接段别名+真键 = $PTK_GATE/$PTK_KEYS/$PTK_RETURN/$PTK_GUARD / $PTK_DEMO_ALIAS+$PTK_DEMO_REAL（目标 1/1/1/1 / 0+1）"
echo "PT-l 契约用例数                       = $PTL （目标 ≥15，等于现读）"

fail=0
[ "$PTA_CONST" = 1 ] || { echo "× PT-a 路由常量缺失或重复（$PTA_CONST）"; fail=1; }
[ "$PTA_BUILDER" = 1 ] || { echo "× PT-a 单号构造器不在了（现 $PTA_BUILDER）：跳转会把单号原样拼进路径（编码口径丢一半）"; fail=1; }
[ "$PTA_REG" = 1 ] || { echo "× PT-a 注册点不是恰好一处（现 $PTA_REG）：这一页打不开"; fail=1; }
[ "$PTA_BODY" = 1 ] || { echo "× PT-a 有注册没走 BusRouteBody（$PTA_BODY/1）：这一页与全工程口径不一致"; fail=1; }
[ "$PTB" = 1 ] || { echo "× PT-b 追踪页那个入口不是恰好一条（现 $PTB）：页面存在但用户找不到"; fail=1; }
[ "$PTB_ALL" = 1 ] || { echo "× PT-b 全工程入口数=$PTB_ALL（目标 1）：多了就是第二处导航各走各的"; fail=1; }
[ "$PTC_DEF" = 1 ] || { echo "× PT-c Repo 里 parcelTopology 的定义不见了（现 $PTC_DEF）"; fail=1; }
[ "$PTC_CALL" = 1 ] || { echo "× PT-c 真调用数=$PTC_CALL（目标恰好 1）：0 = 这一格又变回死代码，>1 = 同一条通路两个消费者各自取数"; fail=1; }
[ "$PTC_PAGE" = 1 ] || { echo "× PT-c 页面里的取数点不是恰好一处（现 $PTC_PAGE）"; fail=1; }
{ [ "$PTD1" = 0 ] && [ "$PTD2" = 0 ]; } || { echo "× PT-d 页面自己写了措辞（$PTD1/$PTD2）：两处文字迟早和纯层对不上"; fail=1; }
{ [ "$PTD_P1" = 1 ] && [ "$PTD_P2" = 1 ]; } || { echo "× PT-d 反证掉了（纯层 $PTD_P1/$PTD_P2，目标 1/1）：上面那个 0 数的是不存在的东西"; fail=1; }
[ "$PTE1" = 0 ] || { echo "× PT-e 页面重抄了换乘等待口径（现 $PTE1）：同一个后端口径出现两份文字，改一处漏一处"; fail=1; }
{ [ "$PTE_P1" = 1 ] && [ "$PTE_P2" = 1 ] && [ "$PTE_P3" = 1 ] && [ "$PTE_P4" = 1 ] && [ "$PTE_P5" = 1 ]; } || { echo "× PT-e 纯层口径分支不齐（$PTE_P1/$PTE_P2/$PTE_P3/$PTE_P4/$PTE_P5，目标全 1）：少一格就是某一档口径又开始静默"; fail=1; }
[ "$PTE_KM" = 0 ] || { echo "× PT-e 页面自己拼了公里数（现 $PTE_KM）：绕开了'含估算段就不许念成实测'那条分支"; fail=1; }
{ [ "$PTF_FROM" = 1 ] && [ "$PTF_TO" = 1 ] && [ "$PTF_TIME" = 1 ]; } || { echo "× PT-f 交接的真键读法不齐（$PTF_FROM/$PTF_TO/$PTF_TIME，目标 1/1/1）：票面那两行会退回念空"; fail=1; }
[ "$PTF_ALIAS" = 0 ] || { echo "× PT-f 交接解析里出现白名单外的键（现 $PTF_ALIAS 个）：自造键续命，真接口下永远念不到（E-1 那一族）。逐个对照 OrderTopologyRespVO.Handover:115-126"; fail=1; }
[ "$PTF_COUNT" = 11 ] || { echo "× PT-f 交接解析的键基数不是恰好 11（现 $PTF_COUNT）：少一格是某一格又不读了，多一格要先证明它在后端 VO 里存在"; fail=1; }
[ "$PTF_PROOF" = 1 ] || { echo "× PT-f 反证掉了（同段 itemCount=$PTF_PROOF，目标 1）：那个别名 0 可能是段没取到"; fail=1; }
{ [ "$PTG_LABEL" = 1 ] && [ "$PTG_TIME" = 1 ] && [ "$PTG_DEF" = 1 ]; } || { echo "× PT-g 时间线真键/归一函数不齐（$PTG_LABEL/$PTG_TIME/$PTG_DEF，目标 1/1/1）"; fail=1; }
[ "$PTG_OLD" = 0 ] || { echo "× PT-g Repo 里又出现裸切串（现 $PTG_OLD）：13 位毫秒串会被切出 '17590' 直接上屏"; fail=1; }
[ "$PTG_OLD_PROOF" = 1 ] || { echo "× PT-g 反证掉了（归一函数里那处切串=$PTG_OLD_PROOF，目标 1）：上面那个 0 可能是没扫到文件"; fail=1; }
{ [ "$PTH_BAD1" = 0 ] && [ "$PTH_BAD2" = 0 ]; } || { echo "× PT-h 可空键又被塌成 0（$PTH_BAD1/$PTH_BAD2）：'后端没说' 会变成 '0 分钟 / 0 件'"; fail=1; }
{ [ "$PTH_OK1" = 1 ] && [ "$PTH_OK2" = 1 ] && [ "$PTH_OK3" = 1 ]; } || { echo "× PT-h 可空读法不齐（$PTH_OK1/$PTH_OK2/$PTH_OK3，目标全 1）"; fail=1; }
[ "$PTH_CHOSEN" = 1 ] || { echo "× PT-h chosen 缺席那一格没了（现 $PTH_CHOSEN）：'后端没说'会被念成'这个方案不被推荐'"; fail=1; }
{ [ "$PTI_L" = 1 ] && [ "$PTI_F" = 1 ] && [ "$PTI_A" = 1 ] && [ "$PTI_S" = 1 ]; } || { echo "× PT-i 四格出口不齐（$PTI_L/$PTI_F/$PTI_A/$PTI_S）：某一态被静默并进另一态"; fail=1; }
[ "$PTI_ENUM" = 1 ] || { echo "× PT-i 四格 enum 声明不再是那四个值（现 $PTI_ENUM）：少一头就是某一态又被并回另一态"; fail=1; }
[ "$PTI_BACK" = 3 ] || { echo "× PT-i 「回追踪页」出口不是恰好三处（现 $PTI_BACK）：失败格或空结构格把人关在票面上，就是没兜底"; fail=1; }
[ "$PTI_BACK_DEF" = 1 ] || { echo "× PT-i 兜底出口那个 composable 不在了（现 $PTI_BACK_DEF）"; fail=1; }
[ "$PTJ" = 1 ] || { echo "× PT-j 初值不再是「还没结论」（现 $PTJ）：开局就替后端说了这张单没内容"; fail=1; }
[ "$PTJ_BAD" = 0 ] || { echo "× PT-j 初值被写成带数据（$PTJ_BAD 处）"; fail=1; }
{ [ "$PTK_GATE" = 1 ] && [ "$PTK_KEYS" = 1 ] && [ "$PTK_RETURN" = 1 ]; } || { echo "× PT-k 演示分面闸门不齐（$PTK_GATE/$PTK_KEYS/$PTK_RETURN，目标 1/1/1）：信封键会漏到 /send/track 那份报文上，演示面比契约更像真话"; fail=1; }
[ "$PTK_GUARD" = 1 ] || { echo "× PT-k 演示分面那道闸门整句不见了（现 $PTK_GUARD）：信封键会漏进 /send/track 的报文，演示面比契约更像真话"; fail=1; }
[ "$PTK_DEMO_ALIAS" = 0 ] || { echo "× PT-k 演示交接段又发自造键 handoverTime（现 $PTK_DEMO_ALIAS）：拓扑 VO 从不发它，读它就等于给假键发通行证"; fail=1; }
[ "$PTK_DEMO_REAL" = 1 ] || { echo "× PT-k 演示交接段的真键 fromPlateNo 没了（现 $PTK_DEMO_REAL）：上面那个 0 可能是整段没取到"; fail=1; }
# ⚠ 下限必须等于现读（D-084 第 ③ 条同一味病：下限低于实测 = 删一到"实测-下限"条都不红）。
#   第一版这里写 ≥14 而实测 15，负控 PT-l1 注释掉一条用例照样绿 —— 是那条负控当场抓出来的。
[ "$PTL" -ge 15 ] || { echo "× PT-l 契约用例少于 15 条（现 $PTL）：四格、两口径、可空三态没人钉"; fail=1; }

if [ "$fail" = 0 ]; then echo "查件票面判据通过"; else echo "查件票面判据失败"; fi
exit "$fail"
