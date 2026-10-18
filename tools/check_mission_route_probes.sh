#!/usr/bin/env bash
# F-3 ⑧ 任务地图无路线态判据（队列 #148：DriverMissionMap / MissionMarkers 在"后端没有路线"那一支
# **说什么、画什么** —— 不许用直线补几何，也不许把"没问到"说成"没有"）。
# 用法：bash tools/check_mission_route_probes.sh     退出码非 0 = 这次收口破了
#
# 分工：行为由 app/src/test/.../ui/driver/MissionRouteStateContractTest.kt 钉（12 条：五格说法的纯函数判据
# + 五格 MockWebServer 请求条数/相位判据 + 画什么那一侧的 paths 判据）。
# 这里钉**结构**：工程没有 Robolectric，"页面再也自带不出那句'后端没有下发'""noRouteReason 是必填参数"
# "相位写入的先后""失败那一支真的占了一格并给了重试入口"只能按整行字面量与行号顺序复核。
#
# 写法沿用既有教训（每条都有对应的负控制，见 tools/mission_route_negative_controls.sh）：
# · 先验文件存在 —— grep 找不到文件时输出是空的，空串参与数值比较看起来像"跑过了"，其实根本没跑；
# · 注释行排除 + 每条"恰好 N"都钉整行字面量（子串匹配对注释行没有免疫力）；
# · 每个"目标 0"都配一条**同机制反证**：同一个模式在别处必须能数出非 0，否则那个 0 只是模式没命中；
# · 一条判据只管一个键名/一个形状，不把两种破口并进同一条"数 0"。
#
# 本册**不重复** #51 那套 DEBUG 闸门判据（未加闸的 DRIVER_NAV 使用/注册带闸/文件自守 = N-n），
# 也不重复 P0-2 的直线归零（N-d/N-m）；本册只钉 ⑧ 这一族：说法的唯一出口 + 相位 + 失败支占屏。
#
# MR-l…MR-r 是队列 #216（指令 §八 ⑫⑬ 3D 任务 Marker / 3D 车辆）同轮加进来的**另一族**，判的是视觉层：
# 立体感只有一个出口、旋转只给本机 GPS 真给出 bearing 的那一格、新鲜度只有一个阈值、视觉不写业务状态。
# 行为那一半在 app/src/test/.../ui/common/Map3DVisualContractTest.kt（16 条），刻意不进 ⑧ 那本
# ——两族的条数各自钉死（MR-a 恰好 14、MR-r 恰好 16），并进同一本就看不出删掉的是哪一族的用例。
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
cd "$ROOT" || { echo "找不到 $ROOT"; exit 2; }

SRC=app/src/main/java/com/shanxiang/buspost
MM="$SRC/ui/driver/MissionMarkers.kt"
DMM="$SRC/ui/driver/DriverMissionMap.kt"
WB="$SRC/ui/driver/WorkbenchScreen.kt"
MC="$SRC/ui/common/MapCanvas.kt"
PD="$SRC/core/map/PolylineDecision.kt"
# 反证用的对照组：#51 关进 DEBUG 的旧导航屏，那句写死文案仍在那里生效（正式链路已归零）
DN="$SRC/ui/driver/DriverNavScreen.kt"
# ⑫⑬ 3D 视觉层（本轮）：视觉层本体、唯一消费它的 SDK 装配层、朝向的两个生产者与消费者
V3="$SRC/ui/common/Map3DVisual.kt"
BM="$SRC/ui/common/BusMap.kt"
GD="$SRC/ui/driver/DriverGuideScreen.kt"
CG="$SRC/ui/driver/CargoGauge.kt"
LOC="$SRC/core/loc/Locator.kt"
DLS="$SRC/driver/DriverLocationService.kt"
# 业务状态那一侧的对照组（MR-r 的"0 命中"要靠它证明模式命中得了真实代码行）
BIZ1="$SRC/ui/driver/DriverExceptionScreen.kt"
BIZ2="$SRC/ui/driver/DriverLegStateMapper.kt"
T=app/src/test/java/com/shanxiang/buspost/ui/driver/MissionRouteStateContractTest.kt
V3T=app/src/test/java/com/shanxiang/buspost/ui/common/Map3DVisualContractTest.kt
NC='(^|[0-9]+:)[[:space:]]*(\*|//)'

# 先验文件存在（缺文件会被读成"计数 0"，那是判据自己骗自己）
for f in "$MM" "$DMM" "$WB" "$MC" "$PD" "$DN" "$T" "$V3" "$BM" "$LOC" "$DLS" "$V3T" "$BIZ1" "$BIZ2"; do
    [ -f "$f" ] || { echo "× 判据文件缺失：$f（先确认工程根/副本建对，别把缺文件读成计数 0）"; exit 2; }
done

# MR-a 用例条数：删用例必须让这里变红，所以是"恰好"不是下限
MRA=$(grep -cE '^[[:space:]]*@Test' "$T")

# MR-b 唯一出口：定义只有一处（新造第二套说法就是 §八 ㉗ 说的"两个答案"），页面侧读它两处（画地图支 + 失败支）
MRB_DEF=$(grep -rn 'fun missionRouteReasonOf' "$SRC" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')
MRB_USE=$(grep -n 'MissionMarkers.missionRouteReasonOf(' "$WB" | grep -vE "$NC" | wc -l | tr -d ' ')

# MR-c 五格说法：Asking / NotAsked / 没问到 / 后端或校验器给的原因 / 无证据兜底，各钉整行字面量
# 2026-10-12 续七重锚：`8387929 全面清除界面技术术语` + `7d8b819 视觉升级` 把这五句改写了一遍
#   （"这一班"→保留、"不画任何示意线"→"不画示意线 / 期间不画示意线"、句长收紧），**语义一支都没少**：
#   逐串在 `MissionMarkers.kt:133-141` 实测各恰好 1 命中。按本簿 2026-10-10 那条教训**不再钉缩进**
#   （套上 BusRouteBody 平板壳后整行右移过），改用整串唯一子串锚定 ⇒ 换词即红、复制第二处也红。
MR_C1=$(grep -cF '正在向后端要这一班的路线，期间不画示意线。"' "$MM")
MR_C2=$(grep -cF '还没去问过路线，不是后端说没有路线，期间不画示意线。"' "$MM")
MR_C3=$(grep -cF '（这不是后端说没有路线，不画示意线）' "$MM")
MR_C4=$(grep -cF '（站点与顺序仍按调度安排显示，不画示意线）' "$MM")
MR_C5=$(grep -cF '没有证据表明后端说过它有路线，暂无路线数据，不画示意线。"' "$MM")
MR_CT=$(grep -n '不画示意线' "$MM" | grep -vE "$NC" | wc -l | tr -d ' ')
# MR_C6 "这次没问到"那一格的**归因框架**：这一格只能说"这次没问到 ＋ 后端/校验器给的原话"，
#   不能说成"后端没有可用路线"（后端没说过这句话）。上面 MR_C3 钉的是括号里那半句免责，
#   括号**前面**那半句被换掉时 MR_C3/MR_CT 一个都不动 ⇒ D-082 之后补这一条，
#   破口是 MR-c 第三条（把「这次没问到：」写成「后端没有可用路线，」）。
MR_C6=$(grep -cF '这次没问到：$failure' "$MM")

# MR-d 「后端没有下发」在**正式可达**的四个文件里必须归零（生效行；注释里引用它是本轮的取证文字，不算）。
# 反证：同一个模式在 DEBUG 屏 DriverNavScreen 数得出 3 —— 模式命中得了代码行，所以那个 0 不是空跑。
MR_D0=$(grep -n '后端没有下发' "$MM" "$DMM" "$WB" "$MC" | grep -vE "$NC" | wc -l | tr -d ' ')
# 反证的旧模式 `后端没有下发` 已在 `8387929` 那轮整词清除（全仓 5 命中都在注释/别处，DEBUG 屏 0 命中）
#   ⇒ 那个 0 不再是"归零"，而是"模式没命中"。换成 DEBUG 屏现在仍在说的 `暂无`（实测非注释恰好 8 处）。
MR_D9=$(grep -n '暂无' "$DN" | grep -vE "$NC" | wc -l | tr -d ' ')

# MR-e 页面不许自己编：noRouteReason 是必填参数（无默认值），值只能来自唯一出口，调用点仍是一处
MRE_P=$(grep -c '^    noRouteReason: String,$' "$DMM")
MRE_DEF=$(grep -cE '^[[:space:]]*noRouteReason: String =' "$DMM")
# 反证：同一种"带默认值"的写法在这个签名里数得出（focusStopId），所以 MRE_DEF=0 不是模式没命中
MRE_PROOF=$(grep -c 'focusStopId: Long = 0L' "$DMM")
# 2026-10-10 起这一族锚点一律**不钉缩进**：上一轮（未入账那段）给每屏套了 BusRouteBody 平板壳，
# WorkbenchScreen 里那几行整体右移 8~24 空格，按 `^ {16}…$` 写的字面锚当场全漂（本轮实测 MR-e/MR-i/MR-l 共 7 格红，
# 逐条读源码确认**语义一支都没少**）。目标值仍是"恰好 1"，所以右移不会放过重复项：多一处就数到 2，照样红。
MRE_ARG=$(grep -cE '^[[:space:]]*noRouteReason = MissionMarkers\.missionRouteReasonOf\($' "$WB")
MRE_RAW=$(grep -n 'noRouteReason =' "$WB" | grep -vE "$NC" | grep -vc 'missionRouteReasonOf' | tr -d ' ')
MRE_CALL=$(grep -rn 'DriverMissionMap(' "$SRC" --include=*.kt | grep -vE "$NC" | grep -v 'fun DriverMissionMap(' | wc -l | tr -d ' ')

# MR-f 相位写入：一次装载只有 Asking→Asked 各一次，退回 NotAsked 两处（撤档早退 + 协程取消），初值 NotAsked
# ⚠ 一律整行锚定（^[[:space:]]*…$）：负控制第一条就抓到"把整行注释掉，子串 grep -c 照样数到 1"，
#   那种判据对注释行没有免疫力，等于没判据（同一条教训在 RR-d 也踩过）。
MRF_ASKING=$(grep -cE '^[[:space:]]*routePhase = PolylinePhase\.Asking$' "$WB")
MRF_ASKED=$(grep -cE '^[[:space:]]*routePhase = PolylinePhase\.Asked$' "$WB")
MRF_NEVER=$(grep -cE '^[[:space:]]*routePhase = PolylinePhase\.NotAsked$' "$WB")
MRF_DECL=$(grep -c '^    internal var routePhase by mutableStateOf(PolylinePhase.NotAsked)$' "$WB")
MRF_FAILNULL=$(grep -cE '^[[:space:]]*routeFailure = null$' "$WB")
MRF_FAILERR=$(grep -cE '^[[:space:]]*routeFailure = g\.error$' "$WB")
MRF_FAILDECL=$(grep -c '^    internal var routeFailure by mutableStateOf<String?>(null)$' "$WB")

# MR-g 早退支：四条司机域请求一条都没发时，路线状态必须退回"没问过"（上一轮残留的 Asked 会让地图念成"后端说没有"）
DEN=$(grep -nE '^[[:space:]]*if \(server == false\) \{$' "$WB" | head -1 | cut -d: -f1)
RET=$(awk -v s="${DEN:-0}" 'NR>s && /^[[:space:]]*return$/ {print NR; exit}' "$WB")
MRG_NEVER=$(awk -v s="${DEN:-0}" -v e="${RET:-0}" 'NR>s && NR<e' "$WB" | grep -cE '^[[:space:]]*routePhase = PolylinePhase\.NotAsked$')
MRG_FAIL=$(awk -v s="${DEN:-0}" -v e="${RET:-0}" 'NR>s && NR<e' "$WB" | grep -cE '^[[:space:]]*routeFailure = null$')
MRG_GUIDE=$(awk -v s="${DEN:-0}" -v e="${RET:-0}" 'NR>s && NR<e' "$WB" | grep -cE '^[[:space:]]*guide = null$')

# MR-h 装载顺序：Asking 必须在请求之前、Asked 必须在请求之后（顺序错了，"在问"那一格永远不可能被读到）
A1=$(grep -nE '^[[:space:]]*routePhase = PolylinePhase\.Asking$' "$WB" | head -1 | cut -d: -f1)
A2=$(grep -nE '^[[:space:]]*Repo\.routeGuide\(\)$' "$WB" | head -1 | cut -d: -f1)
A3=$(grep -nE '^[[:space:]]*routePhase = PolylinePhase\.Asked$' "$WB" | head -1 | cut -d: -f1)
MRH_IN=$(awk -v a="${A1:-0}" -v b="${A3:-0}" 'NR>a && NR<b' "$WB" | grep -cE '^[[:space:]]*Repo\.routeGuide\(\)$')

# MR-i UI 三支：问到了→画；这次问失败/问过了但后端说没有→占一格说实话并给重试；没问过→不占屏。旧写法把后两支并成了"整块消失"
# 2026-10-09 重锚（工作台重排 M-1）：失败支的条件从 `vm.routeFailure != null` 扩成了
#     `missionGuide != null || vm.routeFailure != null`（后端把班次明细答成"没有"时 routeFailure 是空的，
#     旧条件让整块导航面无声消失——那句成因写在源码 660-663），`reason` 也从写死的 `, null)`
#     改成读后端那句 `missionGuide?.routeUnavailableReason`。两支语义都还在，锚点按今天的形状重钉：
#     窗口从 s+8 放到 s+10（那一格改成多行调用后 onReplan 落在了第 9 行），出口那句仍按整行等值匹配。
# 2026-10-10 二次重锚（平板 BusRouteBody 壳把整块右移 16 空格）：两支条件式改成**不钉缩进**，
#   窗口由 s+10 抬到 s+12 —— 现读失败支那一支共 15 个物理行（`}` 落在 s+15），onReplan 在 s+11、
#   闭括号在 s+12，所以 s+12 仍整支关在本支之内（下一支 `if (shift != null …)` 从 s+16 才开始），
#   不会把别处的 RouteUnavailableState/onReplan 混进来数。
MRI_COND=$(grep -cE '^[[:space:]]*if \(missionGuide != null && \(missionGuide\.stops\.isNotEmpty\(\) \|\| missionGuide\.polyline\.size >= 2\)\) \{$' "$WB")
MRI_ELSE=$(grep -cE '^[[:space:]]*\} else if \(missionGuide != null \|\| vm\.routeFailure != null\) \{$' "$WB")
BR=$(grep -nE '^[[:space:]]*\} else if \(missionGuide != null \|\| vm\.routeFailure != null\) \{$' "$WB" | head -1 | cut -d: -f1)
MRI_BOX=$(awk -v s="${BR:-0}" 'NR>s && NR<=s+12' "$WB" | grep -cE '^[[:space:]]*RouteUnavailableState\($')
MRI_REASON=$(awk -v s="${BR:-0}" 'NR>s && NR<=s+12' "$WB" | grep -cE '^[[:space:]]*reason = MissionMarkers\.missionRouteReasonOf\($')
MRI_RETRY=$(awk -v s="${BR:-0}" 'NR>s && NR<=s+12' "$WB" | grep -cE '^[[:space:]]*onReplan = \{ vm\.load\(\) \},$')

# MR-j 画什么那一侧：折线只走 paths() 那个出口，任务点没有任何一条路能被连成线
MRJ_GATE=$(grep -c '^        if (guide.polyline.size < 2 || guide.routeUnavailableReason != null) emptyList()$' "$MM")
MRJ_VAR=$(grep -c '^    val paths = MissionMarkers\.paths(guide)$' "$DMM")
MRJ_MP_MM=$(grep -n 'MapPath(' "$MM" | grep -vE "$NC" | wc -l | tr -d ' ')
MRJ_MP_DMM=$(grep -n 'MapPath(' "$DMM" | grep -vE "$NC" | wc -l | tr -d ' ')

# MR-k 复用而不是新造：PolylinePhase 只有一套（线路页折线 ⑤ 与司机班次 ⑧ 共用），MapCanvas 的兜底不再替后端下结论
MRK_ENUM=$(grep -rn 'enum class PolylinePhase' "$SRC" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')
MRK_IMP=$(grep -cE '^import com\.shanxiang\.buspost\.core\.map\.PolylinePhase$' "$MM" "$WB" | awk -F: '{s+=$2} END {print s+0}')
MRK_OLD=$(grep -rn 'enum class MissionRoutePhase' "$SRC" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')
MRK_FALLBACK=$(grep -c '^                reason ?: "现在没有可画的真实道路折线（具体原因未取得）。为避免显示与实际行驶无关的假路线，这里不做任何连线示意。",$' "$MC")
MRK_NULLABLE=$(grep -c '^    reason: String? = null,$' "$MC")

# ==================== ⑫⑬ 3D 任务 Marker / 3D 车辆（纯视觉层）====================
# 这一族的口径：指令要的是"看得见的立体感"，硬约束是"3D Marker 不允许影响业务"。
# 所以判据只管三件事：立体感的说法只有一个出口（页面不许各自 setFlat/altitude/rotate）、
# 旋转的角度只能来自本机 GPS 真给出来的 bearing（折线推断出来的那个不算，编出来的 0.0 更不算）、
# 视觉层不写任何业务/上报状态。行为由 Map3DVisualContractTest.kt 钉，这里钉结构。

# MR-l 唯一出口 + 页面接线：3D 形状只由 Map3DVisual 产出，SDK 的那五个调用只落在 BusMap 一处
MRL_OBJ=$(grep -c '^object Map3DVisual {$' "$V3")
MRL_FUN=$(grep -rn 'fun marker3DOf\|fun gpsFixVisual' "$SRC" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')
MRL_SDK_BM=$(grep -nE '\.(setFlat|perspective|zIndex|rotateAngle|altitude)\(' "$BM" | grep -vE "$NC" | wc -l | tr -d ' ')
MRL_SDK_OUT=$(grep -rnE '\.(setFlat|perspective|zIndex|rotateAngle|altitude)\(' "$SRC" --include=*.kt | grep -vE "$NC" | grep -v 'BusMap\.kt:' | wc -l | tr -d ' ')
# 页面接线这三格同 MR-i：2026-10-10 起不钉缩进（平板壳右移），目标值仍是"恰好 1"。
MRL_HEAD=$(grep -cE '^[[:space:]]*val serviceHeading by Running\.headingDegrees\.collectAsStateWithLifecycle\(\)$' "$WB")
MRL_GPS=$(grep -cE '^[[:space:]]*val meGps = Map3DVisual\.gpsFixVisual\($' "$WB")
MRL_ARG=$(grep -cE '^[[:space:]]*meGps = meGps,$' "$WB")

# MR-m 地图倾斜：参数有默认值 0f（不用 3D 的页面一行都不变），只有真的传了倾斜角才开手势+动相机
MRM_PARAM=$(grep -c '^    tiltDegrees: Float = 0f,$' "$BM")
MRM_GEST=$(grep -c '^[[:space:]]*isTiltGesturesEnabled = tiltDegrees > 0f$' "$BM")
MRM_TILT=$(grep -c '^[[:space:]]*if (tiltDegrees > 0f) aMap.moveCamera(CameraUpdateFactory.changeTilt(tiltDegrees))$' "$BM")
MRM_CONST=$(grep -c '^    const val MAP_TILT_DEGREES = 35f$' "$V3")
MRM_USE=$(grep -c '^[[:space:]]*tiltDegrees = Map3DVisual\.MAP_TILT_DEGREES,$' "$DMM")

# MR-n 相机 800ms 是唯一被交出去的动画；marker 逐帧插值不做（无设备在环，插不出也验不了，写了就是死代码）
MRN_CONST=$(grep -c '^    const val CAMERA_ANIMATION_MS = 800L$' "$V3")
MRN_MS=$(grep -c '^[[:space:]]*Map3DVisual\.CAMERA_ANIMATION_MS,$' "$BM")
MRN_ANIM=$(grep -n 'aMap\.animateCamera(' "$BM" | grep -vE "$NC" | wc -l | tr -d ' ')
MRN_MOVE=$(grep -n 'aMap\.moveCamera(' "$BM" | grep -vE "$NC" | wc -l | tr -d ' ')
MRN_NOIP=$(grep -rn 'animateVehicle\|fun lerp' "$SRC" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')
# 反证：同一个模式在 Map3DVisual 的 KDoc 里数得出 1（那条"不做"的取证文字），所以 NOIP=0 是真归零不是没命中
MRN_NOIP_P=$(grep -rn 'animateVehicle\|fun lerp' "$SRC" --include=*.kt | wc -l | tr -d ' ')
# 顺序：先按倾角摆好相机，再交给 SDK 做视口动画（顺序倒了，倾角会被后一次相机更新覆盖）
TL=$(grep -n '^[[:space:]]*if (tiltDegrees > 0f) aMap\.moveCamera' "$BM" | head -1 | cut -d: -f1)
AN=$(grep -n '^[[:space:]]*.*aMap\.animateCamera(' "$BM" | head -1 | cut -d: -f1)

# MR-o 朝向只有一个来源：本机 Location.hasBearing()。旧代码那句 else 0.0 会让"没给朝向"变成"正北"
MRO_FUN=$(grep -c '^    fun heading(location: Location): Double? = if (location\.hasBearing()) location\.bearing\.toDouble() else null$' "$LOC")
MRO_FIELD=$(grep -c '^    val headingDegrees: Double? = null,$' "$LOC")
MRO_WIRE=$(grep -c '^            headingDegrees = heading(l),$' "$LOC")
MRO_FAKE=$(grep -rn 'bearing\.toDouble() else 0\.0' "$SRC" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')
MRO_FAKE_P=$(grep -c 'else 0\.0' "$LOC")
# 上报报文不带朝向（后端 AppDriverLocationReqVO 没有朝向字段；视觉用的东西不许顺手变成上报的内容）
MRO_UP=$(grep -n 'Repo\.reportLocation(' "$DLS" | grep -vE "$NC" | grep -c headingDegrees | tr -d ' ')
MRO_UP_P=$(grep -n 'headingDegrees' "$DLS" | grep -vE "$NC" | wc -l | tr -d ' ')

# MR-p 旋转只给"我"这一格：只有 KIND_ME 拿得到角度，任务点/车辆都不转
MRP_ME=$(grep -c '^            rotateAngle = if (kind == MapMarker\.KIND_ME) rotateOf(visual\.headingDegrees) else null,$' "$V3")
MRP_ROT=$(grep -n 'rotateOf' "$V3" | grep -vE "$NC" | wc -l | tr -d ' ')
MRP_ROT_MM=$(grep -n 'rotateOf' "$MM" | grep -vE "$NC" | wc -l | tr -d ' ')
# 装配层只把 GPS 朝向交给"我"那一格（三处：参数 + headingText + MarkerVisual），车辆那条 marker 不在这儿
MRP_HEAD_MM=$(grep -n 'headingDegrees' "$MM" | grep -vE "$NC" | wc -l | tr -d ' ')

# MR-q 新鲜度的阈值只有一个生产者：沿用服务侧 LocationReportPolicy，不在视觉层再造一个时钟
MRQ_POLICY=$(grep -c '^        fixAgeMs > LocationReportPolicy\.MAX_FIX_AGE_MS -> GpsFixState\.STALE$' "$V3")
MRQ_TIME=$(grep -cE '^    const val [A-Z_]*(MS|MILLIS|AGE|TIMEOUT|SECOND)[A-Z_]* = ' "$V3")
MRQ_LIVE=$(grep -c '^                    Running\.headingDegrees\.value = fix\.headingDegrees$' "$DLS")
MRQ_RESET=$(grep -c '^        headingDegrees\.value = null$' "$DLS")

# MR-r 视觉不碰业务：Map3DVisual / BusMap 里既不出现三个业务状态名，也不写任何共享状态。
# 指令点名的 TaskState/OrderState/LegState 在本工程只有一个真实生产者 —— DriverLegStateMapper
# （§10/§26-28 那条段状态机），所以模式取 "LegState" 这一段就是取它；对照组数得出 7 条生效行。
MRR_BIZ_V3=$(grep -nE 'TaskState|OrderState|LegState' "$V3" | grep -vE "$NC" | wc -l | tr -d ' ')
MRR_BIZ_BM=$(grep -nE 'TaskState|OrderState|LegState' "$BM" | grep -vE "$NC" | wc -l | tr -d ' ')
MRR_BIZ_PF=$(grep -rnE 'TaskState|OrderState|LegState' "$BIZ1" "$BIZ2" | grep -vE "$NC" | wc -l | tr -d ' ')
MRR_WRITE=$(grep -rnE '(Running|Repo)\.[A-Za-z]+(\.[A-Za-z]+)* = ' "$V3" "$BM" | grep -vE "$NC" | wc -l | tr -d ' ')
MRR_WRITE_PF=$(grep -rnE '(Running|Repo)\.[A-Za-z]+(\.[A-Za-z]+)* = ' "$DLS" | grep -vE "$NC" | wc -l | tr -d ' ')
# 视觉层不 import 任何业务模型/契约类型（唯一例外是 LocationReportPolicy：新鲜度阈值必须沿用服务侧那一个）
MRR_IMP=$(grep -c '^import com\.shanxiang\.buspost\.\(data\.model\|core\)\.' "$V3")
# 基数 10 → 11 的唯一增量（2026-10-18 D-169）：`MissionMarkers.kt` 多 import 了一行
# `data.model.TaskItemStatus` —— 地图标记那个动作字与后端动作名必须查同一张表，
# 在装配层自己抄一份"6 → 停"就是第二个答案。这一格是反证（证明扫描数得出业务 import），
# 抬一是因为装配层真的多读了一个业务类型，不是把闸调松。
MRR_IMP_PF=$(grep -c '^import com\.shanxiang\.buspost\.\(data\.model\|core\)\.' "$BM" "$MM" | awk -F: '{s+=$2} END {print s+0}')
MRR_TEST=$(grep -cE '^[[:space:]]*@Test' "$V3T")

# ── MR-s / MR-t / MR-u（2026-10-10 D-070 那三处改动的挂点判据）────────────────
# 三条各钉一个可独立坏掉的点：
#   MR-s  导航卡必须挂在**独立的** `item(key = "navCard")` 上，且全页调用点恰好一处。
#         回退成"嵌在有路线支里"就会让"后端只给空明细"的司机整块看不到导航面（本轮真机撞到的）。
#   MR-t  导航页地图必须按两条自绘条的实测高度留位 —— 写死像素不算，平板/手机/大字模式都不同。
#   MR-u  仓位填充必须来自 `100f − 空余`；用回空余本身就把"一件没装"画成"整车涂满"。
# ⚠ 全部剔注释行（NC）：负控注入是把整行改成注释，不剔就是"注释顶替生效代码"那一族坑。
MS_CARD=$(grep -n 'item(key = "navCard") {' "$WB" | grep -vE "$NC" | wc -l | tr -d ' ')
MS_CALL=$(awk '/item\(key = "navCard"\) \{/{f=1; next} f && /DriverSdkNavCard\(/{c++; exit} END{print c+0}' "$WB")
MS_ONE=$(grep -n 'DriverSdkNavCard(' "$WB" | grep -vE "$NC" | wc -l | tr -d ' ')
MT_PAD=$(grep -n 'padding(top = topReserve, bottom = bottomReserve)' "$GD" | grep -vE "$NC" | wc -l | tr -d ' ')
MT_TOP=$(grep -n 'onSizeChanged { topPx = it.height }' "$GD" | grep -vE "$NC" | wc -l | tr -d ' ')
MT_BOT=$(grep -n 'onSizeChanged { bottomPx = it.height }' "$GD" | grep -vE "$NC" | wc -l | tr -d ' ')
MU_FILL=$(grep -n '100f - it.toFloat()' "$CG" | grep -vE "$NC" | wc -l | tr -d ' ')

echo "MR-a 行为用例条数                       = $MRA （目标 14：五格纯函数 + 五格请求条数/相位 + 其余 + 2026-10-15 #49 那两格）"
echo "MR-b 唯一出口定义/页面读点              = $MRB_DEF/$MRB_USE （目标 1/2：只有一个出口，页面两处读它）"
echo "MR-c 五格字面量（在问/没问过/没问到/后端给的/兜底） = $MR_C1/$MR_C2/$MR_C3/$MR_C4/$MR_C5 （目标全 1，整串唯一锚定）"
echo "MR-c 出口里的'不画示意线'生效行        = $MR_CT （目标 5：每一格都自带'不画线'承诺）"
echo "MR-c '这次没问到'那一格的归因框架      = $MR_C6 （目标 1：只能说这次没问到，不能替后端说没有）"
echo "MR-d 旧那句 正式可达四文件/DEBUG 屏对照   = $MR_D0/$MR_D9 （目标 0/8：归零 + 同机制反证）"
echo "MR-e 必填参数/带默认值/默认值写法反证     = $MRE_P/$MRE_DEF/$MRE_PROOF （目标 1/0/1）"
echo "MR-e 出口传参/绕过出口的写点/地图调用点   = $MRE_ARG/$MRE_RAW/$MRE_CALL （目标 1/0/1）"
echo "MR-f Asking/Asked/退回 NotAsked/初值      = $MRF_ASKING/$MRF_ASKED/$MRF_NEVER/$MRF_DECL （目标 1/1/2/1）"
echo "MR-f 失败原因写点(null/g.error)/声明      = $MRF_FAILNULL/$MRF_FAILERR/$MRF_FAILDECL （目标 1/1/1）"
echo "MR-g 早退块内 NotAsked/routeFailure=null/guide=null = $MRG_NEVER/$MRG_FAIL/$MRG_GUIDE （目标 1/1/1）"
echo "MR-h Asking<请求<Asked 行号              = ${A1:-无}/${A2:-无}/${A3:-无}（目标严格递增，且窗口内请求=1：实测 $MRH_IN）"
echo "MR-i 有路线支条件式/失败支 else if        = $MRI_COND/$MRI_ELSE （目标 1/1）"
echo "MR-i 失败支块内 占屏/走唯一出口/给重试     = $MRI_BOX/$MRI_REASON/$MRI_RETRY （目标 1/1/1）"
echo "MR-j 折线闸门行/页面取线行                = $MRJ_GATE/$MRJ_VAR （目标 1/1）"
echo "MR-j MapPath( 出现处 出口/任务地图页       = $MRJ_MP_MM/$MRJ_MP_DMM （目标 1/0：任务点连不成线）"
echo "MR-k PolylinePhase 定义/复用 import/第二套枚举 = $MRK_ENUM/$MRK_IMP/$MRK_OLD （目标 1/2/0）"
echo "MR-k MapCanvas 兜底句/可空 reason 参数     = $MRK_FALLBACK/$MRK_NULLABLE （目标 1/2）"
echo "MR-l 视觉出口对象/出口函数/SDK 调用 BusMap内外 = $MRL_OBJ/$MRL_FUN/$MRL_SDK_BM/$MRL_SDK_OUT （目标 1/2/5/0）"
echo "MR-l 页面接线 朝向读/三态装配/marker 传参    = $MRL_HEAD/$MRL_GPS/$MRL_ARG （目标全 1）"
echo "MR-m 倾斜 参数/手势/相机/常量/任务地图用量    = $MRM_PARAM/$MRM_GEST/$MRM_TILT/$MRM_CONST/$MRM_USE （目标全 1）"
echo "MR-n 800ms 常量/相机动画用点/animateCamera/moveCamera = $MRN_CONST/$MRN_MS/$MRN_ANIM/$MRN_MOVE （目标 1/3/3/1）"
echo "MR-n 逐帧插值 生效行/含注释反证              = $MRN_NOIP/$MRN_NOIP_P （目标 0/1）"
echo "MR-n 倾斜先行<视口动画 行号                  = ${TL:-无}/${AN:-无}（目标严格递增）"
echo "MR-o heading 整行/TrackFix 字段/采样接线      = $MRO_FUN/$MRO_FIELD/$MRO_WIRE （目标全 1）"
echo "MR-o 伪造正北/同文件 else 0.0 反证            = $MRO_FAKE/$MRO_FAKE_P （目标 0/1）"
echo "MR-o 上报行带朝向/服务侧朝向出现处反证         = $MRO_UP/$MRO_UP_P （目标 0/3）"
echo "MR-p 只旋转 KIND_ME 整行/出口内 rotateOf       = $MRP_ME/$MRP_ROT （目标 1/3）"
echo "MR-p 装配层 rotateOf/朝向字面量               = $MRP_ROT_MM/$MRP_HEAD_MM （目标 0/3）"
echo "MR-q 政策阈值整行/视觉层时间常量/朝向置位+复位  = $MRQ_POLICY/$MRQ_TIME/$MRQ_LIVE/$MRQ_RESET （目标 1/1/1/1）"
echo "MR-r 视觉层业务状态名 出口/BusMap/业务对照     = $MRR_BIZ_V3/$MRR_BIZ_BM/$MRR_BIZ_PF （目标 0/0/7）"
echo "MR-r 视觉层写共享状态/服务侧反证/新用例条数     = $MRR_WRITE/$MRR_WRITE_PF/$MRR_TEST （目标 0/≥1/16）"
echo "MR-s 导航卡独立格/卡在该格内/全页调用点        = $MS_CARD/$MS_CALL/$MS_ONE （目标 1/1/1）"
echo "MR-t 地图按实测高度避让/顶条量/底栏量          = $MT_PAD/$MT_TOP/$MT_BOT （目标全 1）"
echo "MR-u 仓位填充取 100-空余                       = $MU_FILL （目标 1）"
echo "MR-r 视觉层 import 业务模型/BusMap+出口对照反证  = $MRR_IMP/$MRR_IMP_PF （目标 0/11）"

fail=0
[ "$MRA" = 14 ] || { echo "× MR-a ⑧ 的行为用例不是 14 条（说法那一半或请求条数那一半被删了）：12 → 14 的逐点归因 = 2026-10-15 #49 在 ui/driver/MissionRouteStateContractTest.kt 新加的两格 —— ①「页脚的折线完整性三格各说各的」（routeComplete 的 没发键/后端说断了/后端说完整）②「偏航行三格读数 但一句未偏航都不说」（deviationMeters 的 没发键/-1 无法判定/真实米数，并钉住 deviated 缺键塌 false 时界面不许念'未偏航'）；再变就得逐点重名，不许把"恰好"改成下限"; fail=1; }
[ "$MRB_DEF" = 1 ] || { echo "× MR-b '这一班有没有路线'有了第二个出口定义：同一个问题两个答案"; fail=1; }
[ "$MRB_USE" = 2 ] || { echo "× MR-b 页面读唯一出口的点不是 2 处（画地图支 + 失败支），有人开始自己拼句子"; fail=1; }
[ "$MR_C1" = 1 ] || { echo "× MR-c '在问'那一格的字面量变了：在飞时会念成别的（或整格没了）"; fail=1; }
[ "$MR_C2" = 1 ] || { echo "× MR-c '没问过'那一格丢了'不是后端说没有路线'：没问又被念成后端说没有"; fail=1; }
[ "$MR_C3" = 1 ] || { echo "× MR-c '这次没问到'那一格丢了失败原因或又开始替后端下结论"; fail=1; }
[ "$MR_C4" = 1 ] || { echo "× MR-c 转述来源原因那一格丢了'站点与顺序仍按调度安排显示'"; fail=1; }
[ "$MR_C5" = 1 ] || { echo "× MR-c 无证据兜底那一格被换成指责任何一方的措辞"; fail=1; }
[ "$MR_CT" = 5 ] || { echo "× MR-c 出口里带'不画任何示意线'的格子不是 5：有一格不再承诺不画线"; fail=1; }
[ "$MR_C6" = 1 ] || { echo "× MR-c '这次没问到'那一格不再带着后端/校验器给的原话（框架被换成'后端没有可用路线'）：没问到被念成后端说没有"; fail=1; }
[ "$MR_D0" = 0 ] || { echo "× MR-d 正式可达文件里又出现了写死的'后端没有下发'：没问到被说成没有"; fail=1; }
[ "$MR_D9" = 8 ] || { echo "× MR-d 反证失效（DEBUG 屏那 8 处「暂无」也数不出了）：MR-d 的 0 无法证明是归零还是没命中"; fail=1; }
[ "$MRE_P" = 1 ] || { echo "× MR-e noRouteReason 不再是必填参数整行"; fail=1; }
[ "$MRE_DEF" = 0 ] || { echo "× MR-e noRouteReason 有了默认值：页面不带出口也能画，兜底句会复活"; fail=1; }
[ "$MRE_PROOF" = 1 ] || { echo "× MR-e 默认值写法反证掉了（同签名里数不出带默认值的参数），MRE_DEF 的 0 不可信"; fail=1; }
[ "$MRE_ARG" = 1 ] || { echo "× MR-e 画地图那一支的 noRouteReason 不再来自唯一出口"; fail=1; }
[ "$MRE_RAW" = 0 ] || { echo "× MR-e 出现绕过出口的 noRouteReason 传参（页面自己编句子）"; fail=1; }
[ "$MRE_CALL" = 1 ] || { echo "× MR-e 任务地图的调用点不是 1 处（第二处一定自带第二套说法）"; fail=1; }
[ "$MRF_ASKING" = 1 ] || { echo "× MR-f 装载时不再置 Asking：'在问'那一格永远读不到"; fail=1; }
[ "$MRF_ASKED" = 1 ] || { echo "× MR-f 落地后不再置 Asked：失败轮会被念成'还没去问过'"; fail=1; }
[ "$MRF_NEVER" = 2 ] || { echo "× MR-f 退回 NotAsked 的不是两处（撤档早退 + 协程取消）：残留状态或取消被当失败"; fail=1; }
[ "$MRF_DECL" = 1 ] || { echo "× MR-f routePhase 不再是页面私有写：外部可以直接置相位"; fail=1; }
[ "$MRF_FAILNULL" = 1 ] || { echo "× MR-f 早退时不清失败原因：上一轮的'没问到'会跟着下一轮一起念"; fail=1; }
[ "$MRF_FAILERR" = 1 ] || { echo "× MR-f 不再把 One.error 记进 routeFailure：失败原话丢了"; fail=1; }
[ "$MRF_FAILDECL" = 1 ] || { echo "× MR-f routeFailure 不再是页面私有写"; fail=1; }
[ -n "$DEN" ] && [ -n "$RET" ] || { echo "× MR-g 找不到撤档早退分支或它的 return"; fail=1; }
[ "$MRG_NEVER" = 1 ] && [ "$MRG_FAIL" = 1 ] && [ "$MRG_GUIDE" = 1 ] || { echo "× MR-g 早退块内三件清零不再齐全（相位/原因/guide）"; fail=1; }
[ -n "$A1" ] && [ -n "$A2" ] && [ -n "$A3" ] && [ "$A1" -lt "$A2" ] && [ "$A2" -lt "$A3" ] || { echo "× MR-h 相位写入不再包住那条请求"; fail=1; }
[ "$MRH_IN" = 1 ] || { echo "× MR-h Asking 与 Asked 之间不只有 1 条 routeGuide 调用"; fail=1; }
[ "$MRI_COND" = 1 ] || { echo "× MR-i '问到了就画'的条件式不再是'有任务点或有可信折线'"; fail=1; }
[ "$MRI_ELSE" = 1 ] || { echo "× MR-i 失败支的 else if 不在了：这次没问到又变回整块静默消失"; fail=1; }
[ "$MRI_BOX" = 1 ] || { echo "× MR-i 失败支不再占一格（RouteUnavailableState 没了）"; fail=1; }
[ "$MRI_REASON" = 1 ] || { echo "× MR-i 失败支那句不再来自唯一出口"; fail=1; }
[ "$MRI_RETRY" = 1 ] || { echo "× MR-i 失败支没有重试入口：用户只能等下一轮装载"; fail=1; }
[ "$MRJ_GATE" = 1 ] || { echo "× MR-j 折线闸门行变了：可信折线之外的东西可能被画上线"; fail=1; }
[ "$MRJ_VAR" = 1 ] || { echo "× MR-j 任务地图不再从 paths() 那一个出口取线"; fail=1; }
[ "$MRJ_MP_MM" = 1 ] || { echo "× MR-j 反证掉了（出口里数不出 MapPath 构造），MRJ_MP_DMM 的 0 不可信"; fail=1; }
[ "$MRJ_MP_DMM" = 0 ] || { echo "× MR-j 任务地图页自己构造了折线：任务点被连成'路'"; fail=1; }
[ "$MRK_ENUM" = 1 ] || { echo "× MR-k PolylinePhase 定义不再是 1 处"; fail=1; }
[ "$MRK_IMP" = 2 ] || { echo "× MR-k 复用那套三态的文件不再是任务地图出口 + 工作台（新造说法或改回布尔）"; fail=1; }
[ "$MRK_OLD" = 0 ] || { echo "× MR-k 出现第二套'班次路线相位'枚举：同一个问题两个答案（§八 ㉗）"; fail=1; }
[ "$MRK_FALLBACK" = 1 ] || { echo "× MR-k MapCanvas 的兜底句又替后端下结论了"; fail=1; }
[ "$MRK_NULLABLE" = 2 ] || { echo "× MR-k MapCanvas 两处 reason 参数形状变了（RouteMap 与不可用态）"; fail=1; }

# —— ⑫⑬ 3D 视觉层 ——
[ "$MRL_OBJ" = 1 ] || { echo "× MR-l 视觉层出口对象不在了/不止一个：立体感又有了第二个说法"; fail=1; }
[ "$MRL_FUN" = 2 ] || { echo "× MR-l 3D 形状/定位三态的出口函数不再是两个（marker3DOf + gpsFixVisual）"; fail=1; }
[ "$MRL_SDK_BM" = 5 ] || { echo "× MR-l BusMap 里那五个 SDK 立体调用不再是 5（反证掉了，下面那个 0 不可信）"; fail=1; }
[ "$MRL_SDK_OUT" = 0 ] || { echo "× MR-l BusMap 之外又有人直接调 SDK 的立体接口：页面各自 setFlat/altitude 就是两个答案"; fail=1; }
[ "$MRL_HEAD" = 1 ] && [ "$MRL_GPS" = 1 ] && [ "$MRL_ARG" = 1 ] || { echo "× MR-l 页面接线变了（朝向读点/三态装配/marker 传参不再是各 1 处）"; fail=1; }
[ "$MRM_PARAM" = 1 ] && [ "$MRM_GEST" = 1 ] && [ "$MRM_TILT" = 1 ] || { echo "× MR-m 倾斜的参数默认值/手势闸门/相机更新整行变了"; fail=1; }
[ "$MRM_CONST" = 1 ] || { echo "× MR-m 倾角常量不再是 Map3DVisual 里那一个"; fail=1; }
[ "$MRM_USE" = 1 ] || { echo "× MR-m 任务地图不再从常量取倾角（页面自己带数字）"; fail=1; }
[ "$MRN_CONST" = 1 ] || { echo "× MR-n 动画时长常量不再是 800L（指令给的那个数被换或搬到别处）"; fail=1; }
[ "$MRN_MS" = 3 ] || { echo "× MR-n 用 800ms 的相机动画不再是三处（视口那一族被动过）"; fail=1; }
[ "$MRN_ANIM" = 3 ] && [ "$MRN_MOVE" = 1 ] || { echo "× MR-n animateCamera/moveCamera 的配比变了：视口更新又开始硬跳或倾角走了动画"; fail=1; }
[ "$MRN_NOIP" = 0 ] || { echo "× MR-n 出现了 marker 逐帧插值：视觉插出来的位置绝不能进上报链路，且 0 台设备验不了"; fail=1; }
[ "$MRN_NOIP_P" = 1 ] || { echo "× MR-n 逐帧插值反证掉了（连记录'不做'的那行 KDoc 都数不出），NOIP 的 0 不可信"; fail=1; }
[ -n "$TL" ] && [ -n "$AN" ] && [ "$TL" -lt "$AN" ] || { echo "× MR-n 倾角不再先于视口动画落地：倾角会被后一次相机更新覆盖"; fail=1; }
[ "$MRO_FUN" = 1 ] || { echo "× MR-o heading() 不再是'bearing 有就说有、没有就是没有'那一行"; fail=1; }
[ "$MRO_FIELD" = 1 ] && [ "$MRO_WIRE" = 1 ] || { echo "× MR-o 朝向没进 TrackFix 或采样点没接线"; fail=1; }
[ "$MRO_FAKE" = 0 ] || { echo "× MR-o 又把'定位没给朝向'写成 0.0：正北是编出来的"; fail=1; }
[ "$MRO_FAKE_P" = 1 ] || { echo "× MR-o else 0.0 反证掉了（同文件里数不出该写法），FAKE 的 0 不可信"; fail=1; }
[ "$MRO_UP" = 0 ] || { echo "× MR-o 上报报文里出现了朝向：后端没有这个字段，视觉用的东西被当成上报内容"; fail=1; }
[ "$MRO_UP_P" = 3 ] || { echo "× MR-o 服务侧朝向出现处不再是 3（置位/声明/复位），UP 的 0 不可信"; fail=1; }
[ "$MRP_ME" = 1 ] || { echo "× MR-p 旋转不再是只有 KIND_ME 拿得到角度"; fail=1; }
[ "$MRP_ROT" = 3 ] || { echo "× MR-p 出口里 rotateOf 的读点不再是 3（归一/文本/装配），ME 那条整行孤立无援"; fail=1; }
[ "$MRP_ROT_MM" = 0 ] || { echo "× MR-p 装配层自己算起旋转角：任务点/车辆可能被判成有朝向"; fail=1; }
[ "$MRP_HEAD_MM" = 3 ] || { echo "× MR-p 装配层朝向读点不再是 3（参数/文本/marker 视觉），有第二处开始用它"; fail=1; }
[ "$MRQ_POLICY" = 1 ] || { echo "× MR-q STALE 不再沿用服务侧那个政策阈值：新鲜度有了第二个时钟"; fail=1; }
[ "$MRQ_TIME" = 1 ] || { echo "× MR-q 视觉层的时间常量不再是 1 个（那 1 个是相机动画）：这里新造了过期阈值"; fail=1; }
[ "$MRQ_LIVE" = 1 ] && [ "$MRQ_RESET" = 1 ] || { echo "× MR-q 朝向的置位或复位不在了：旧朝向会跟着新任务一起转"; fail=1; }
[ "$MRR_BIZ_V3" = 0 ] && [ "$MRR_BIZ_BM" = 0 ] || { echo "× MR-r 视觉层开始引用业务状态（TaskState/OrderState/LegState）：3D 影响了业务"; fail=1; }
[ "$MRR_BIZ_PF" = 7 ] || { echo "× MR-r 业务侧对照数不出了，上面那两个 0 无法证明是归零还是没命中"; fail=1; }
[ "$MRR_WRITE" = 0 ] || { echo "× MR-r 视觉层开始写共享状态：画法的一层反过来改了数据"; fail=1; }
[ "$MRR_WRITE_PF" -ge 1 ] || { echo "× MR-r 写状态反证掉了（服务侧数不出该写法），WRITE 的 0 不可信"; fail=1; }
[ "$MRR_IMP" = 0 ] || { echo "× MR-r 视觉层 import 了业务模型/契约类型：画法的一层开始读业务数据"; fail=1; }
[ "$MRR_IMP_PF" = 11 ] || { echo "× MR-r import 反证掉了（装配层那十一处业务 import 数不出），IMP 的 0 不可信"; fail=1; }
[ "$MRR_TEST" = 16 ] || { echo "× MR-r 视觉层行为用例不是 16 条（删一条就少一格可证的口径）"; fail=1; }
[ "$MS_CARD" = 1 ] || { echo "× MR-s 导航卡不再是独立一格（现 $MS_CARD）：塞回有路线支，后端只给空明细时司机整块看不到导航面"; fail=1; }
[ "$MS_CALL" = 1 ] || { echo "× MR-s 导航卡不挂在 navCard 那一格里（现 $MS_CALL）：挂点漂回路线条件，又回到看不见为什么不能走"; fail=1; }
[ "$MS_ONE" = 1 ]   || { echo "× MR-s 全页 DriverSdkNavCard 调用点不是恰好 1（现 $MS_ONE）：多一处就是给"怎么走"造了第二个答案"; fail=1; }
[ "$MT_PAD" = 1 ]   || { echo "× MR-t 地图不再按两条自绘条的实测高度留位（现 $MT_PAD）：高德路口放大图会画回我们标题底下"; fail=1; }
[ "$MT_TOP" = 1 ] && [ "$MT_BOT" = 1 ] || { echo "× MR-t 顶条/底栏高度有一处不再现量（$MT_TOP/$MT_BOT）：写死的数迟早小于实际高度，那一帧又压住内容"; fail=1; }
[ "$MU_FILL" = 1 ]  || { echo "× MR-u 仓位填充不再是 100-空余（现 $MU_FILL）：退回空余填充，司机据此判断装车会读反"; fail=1; }

if [ "$fail" = 0 ]; then echo "任务地图无路线态判据通过"; else echo "任务地图无路线态判据失败"; fi
exit "$fail"
