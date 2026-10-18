package com.shanxiang.buspost.ui.driver

import com.shanxiang.buspost.core.map.PolylinePhase
import com.shanxiang.buspost.core.ui.StopState
import com.shanxiang.buspost.core.ui.TimelineStop
import com.shanxiang.buspost.data.model.Geo
import com.shanxiang.buspost.data.model.MissionStop
import com.shanxiang.buspost.data.model.RouteGuide
import com.shanxiang.buspost.data.model.TaskItemStatus
import com.shanxiang.buspost.ui.common.Map3DVisual
import com.shanxiang.buspost.ui.common.MapMarker
import com.shanxiang.buspost.ui.common.MapPath
import com.shanxiang.buspost.ui.common.MarkerEmphasis
import com.shanxiang.buspost.ui.common.MarkerVisual

/**
 * 任务地图的数据装配：把 `/driver/route` 的返回变成"标记 + 折线"。
 *
 * 这一层是纯函数（无 Compose、无 SDK），因此可以在 JVM 里直接断言：
 * ① 顺序只来自后端 `visitSequence`；② 没有可信折线就没有线；③ 状态 8 绝不落成"完成"的颜色。
 */
object MissionMarkers {

    /** `actionType` 里的"经停"那一格（`TaskItemStatus.ACTION_NAMES` 的 6）：只停、不办业务 */
    const val ACTION_TYPE_PASS_THROUGH = 6

    /**
     * 唯一的顺序来源：后端 `visitSequence`（硬规则 3「调度决定去哪里」），客户端不重排、不按距离排。
     * 标记、列表、时间线共用这一个入口，避免三处各排各的。
     */
    fun ordered(stops: List<MissionStop>): List<MissionStop> = stops.sortedBy { it.visitSequence }

    /**
     * 任务点标记：标题带序号，详情带"动作 · 件数/人数 · 状态"。
     *
     * `focusStopId` 是**导航焦点**（本机在看哪一站），只用来决定"当前/下一任务"这两格的高亮，
     * 而且优先级低于后端状态：后端说哪站在办，哪站就是当前任务。它不推进任何业务状态。
     *
     * [badgeOf] / [stationLikeOf] 两格是 D-169 加的：同一批点在地图上要先分得出**站点**与**任务点**
     * 两种身份，任务点还要带一个动作字 —— 高德自己画的"途经 N"只说序号，不说这一格要干什么活。
     */
    fun stopMarkers(stops: List<MissionStop>, focusStopId: Long = 0L): List<MapMarker> {
        val visible = ordered(stops).filter { it.hasGeo }
        val emphasis = emphasisOf(visible, focusStopId)
        return visible.indices.map { i ->
            val stop = visible[i]
            MapMarker(
                geo = stop.geo,
                title = stop.markerTitle(),
                detail = stop.markerDetail(),
                kind = taskKind(stop),
                visual = MarkerVisual(emphasis = emphasis[i]),
                badge = badgeOf(stop.actionType),
                stationLike = stationLikeOf(stop.actionType),
            )
        }
    }

    /**
     * 圆心那个动作字。表在 `TaskItemStatus.ACTION_GLYPHS`（与 `ACTION_NAMES` 同一批码，
     * 契约测试钉住"字在动作名里 + 两表只差 6 经停"），表外拿不到字就留空 —— 不替调度编一个动作。
     */
    fun badgeOf(actionType: Int): String = TaskItemStatus.ACTION_GLYPHS[actionType] ?: ""

    /**
     * 6 = 经停：这一格是线路上的**站点**（每站必停拉客），不带具体业务动作，画圆环不画带字钉。
     * 只有后端明说"经停"才算 —— `actionType` 缺键（-1）时保持任务点形状，
     * 因为"不知道干什么"与"这里只停不办事"是两件事。
     */
    fun stationLikeOf(actionType: Int): Boolean = actionType == ACTION_TYPE_PASS_THROUGH

    /**
     * 当前任务 = 后端 `isActive` 那一站，没有才退回导航焦点；
     * 下一任务 = 优先取其后第一个未完成未失败的，当前是最后一站时才绕回最早的未完成站（不指责任何顺序）。
     */
    private fun emphasisOf(visible: List<MissionStop>, focusStopId: Long): List<MarkerEmphasis> {
        val current = visible.indexOfFirst { it.isActive }
            .let { if (it >= 0) it else visible.indexOfFirst { focusStopId != 0L && it.stationId == focusStopId } }
        val candidates = visible.indices.filter { i -> i != current && !visible[i].isDone && !visible[i].isFailed }
        val next = candidates.firstOrNull { i -> current < 0 || i > current } ?: candidates.firstOrNull() ?: -1
        return visible.indices.map { i ->
            when (i) {
                current -> MarkerEmphasis.CURRENT
                next -> MarkerEmphasis.NEXT
                else -> MarkerEmphasis.NONE
            }
        }
    }

    /**
     * 车辆位置：来自后端最后上报，不是本机 GPS，也不是实时流。
     *
     * 这一格**不给朝向**：车载 GPS 的 bearing 在后端链路里没有对应字段
     * （`AppDriverLocationReqVO` / `AppDriverRouteRespVO` 逐字段读下来都没有），
     * `RouteGuide.headingDegrees` 是折线几何推断出来的，把它当"车辆朝向"就是替后端说话。
     * 所以车辆 marker 只平贴（`flat`）不旋转 —— 指令 ⑬ 要的方向旋转落在"我"这一格，那里是本机 GPS。
     */
    fun vehicleMarker(guide: RouteGuide): MapMarker? {
        val v = guide.vehicleGeo ?: return null
        if (v.lat == 0.0 && v.lng == 0.0) return null
        return MapMarker(v, "车辆（后端最后上报位置）", MapMarker.KIND_BUS_REAL, guide.routeSourceLabel())
    }

    /** 本机位置：唯一允许按 GPS bearing 旋转的一格；`degraded` 时沉到任务点之下 */
    fun meMarker(me: Geo?, headingDegrees: Double? = null, degraded: Boolean = false): MapMarker? {
        val g = me ?: return null
        if (g.lat == 0.0 && g.lng == 0.0) return null
        return MapMarker(
            geo = g,
            title = "我的位置",
            kind = MapMarker.KIND_ME,
            detail = Map3DVisual.headingText(headingDegrees),
            visual = MarkerVisual(headingDegrees = headingDegrees, degraded = degraded),
        )
    }

    /**
     * 折线：只交后端下发且已过校验的 polyline。
     * `guide.polyline` 为空即"没有真实道路"，这里返回空列表，由地图渲染不可用态 —— 绝不补线。
     */
    fun paths(guide: RouteGuide): List<MapPath> =
        if (guide.polyline.size < 2 || guide.routeUnavailableReason != null) emptyList()
        else listOf(
            MapPath(
                points = guide.polyline,
                colorRole = MapPath.ROLE_MAIN,
                source = guide.routeSourceName,
                roadDistanceMeters = guide.roadDistanceMeters,
            ),
        )

    /**
     * "这一班到底有没有路线"这句话的**唯一出口**（F-3 ⑧）。
     *
     * 之前同一个问题有三处答案：本页真地图支写死的那句、`MapCanvas.RouteUnavailableState` 的默认那句、
     * 后端/校验器给的 `routeUnavailableReason`。前两处都在**没有证据**时说"后端没有下发"。
     *
     * ⚠ 这里不写"`RouteGuide.EMPTY` 与后端回空在模型上同形"——本轮实测的结论更硬也更不一样：
     * 正式链路里 `RouteGuide.EMPTY` 的**唯一生产者**就是 `Repo.routeGuide():1091-1098` 的 catch 支
     * （另有两处写 EMPTY 在 `DriverNavScreen.kt:95`/`:138`，那是 #51 关进 DEBUG 的初始值与回落，不在正式链路），
     * 而后端真的说"这一班没有可用道路"时走的是 `routeGuideOf` 的 `RouteCheck.Rejected` / `routeAvailable=false`
     * 两支，**都会带一个非空 reason**。所以 `reason == null` 不是"后端说了没有"，是"这次根本没拿到"——
     * 旧写法恰好把这两种情况念成同一句。相位仍要显式记：`reason == null` 只是失败支的**副产品**而不是契约，
     * 且失败原话只在 `One.error` 里，没有这一格页面连"没问到：（原因）"都说不出来。
     * 与 F-2 ⑧ 登记、F-3 ⑤ 收口的是同一族假话。
     *
     * 顺序照 [com.shanxiang.buspost.core.map.polylineReasonOf]：先"在问"、再"没问过"、再"这次没问到"，
     * 只有真的拿到后端或校验器的原因时才替它说话；最后那格兜底不指责任何一方。
     *
     * ⚠ 复用 `PolylinePhase` 而不是新造枚举：这一问的是"说话的根据到手了没有"，
     * 司机班次的路线与线路页的折线在同一问上是一件事；再造一套就是 §八 ㉗ 说的"两个答案"。
     */
    internal fun missionRouteReasonOf(phase: PolylinePhase, failure: String?, roadReason: String?): String = when {
        phase == PolylinePhase.Asking ->
            "正在向后端要这一班的路线，期间不画示意线。"
        phase == PolylinePhase.NotAsked ->
            "还没去问过路线，不是后端说没有路线，期间不画示意线。"
        failure != null ->
            "这次没问到：$failure（这不是后端说没有路线，不画示意线）"
        roadReason != null ->
            "$roadReason（站点与顺序仍按调度安排显示，不画示意线）"
        else ->
            "没有证据表明后端说过它有路线，暂无路线数据，不画示意线。"
    }

    /** 状态四态配色：失败单独一色，不能与"已完成"共用 */
    fun taskKind(stop: MissionStop): Int = when {
        stop.isFailed -> MapMarker.KIND_TASK_FAILED
        stop.isDone -> MapMarker.KIND_TASK_DONE
        stop.isActive -> MapMarker.KIND_TASK_ACTIVE
        else -> MapMarker.KIND_TASK_TODO
    }

    /**
     * 任务点时间线（⑤ 任务点业务化）：序号、动作、件数、状态全部来自后端 `stops`。
     *
     * 顺序仍然只跟 `visitSequence`；状态色只跟 `status`（`TaskItemStatusEnum`）。
     * "已完成"只能由后端回读得到 —— GPS、本机时钟、点击都不行。
     */
    fun timelineOf(stops: List<MissionStop>, targetStationId: Long = 0L): List<TimelineStop> =
        ordered(stops).map { stop ->
            TimelineStop(
                label = stop.markerTitle(),
                time = stop.etaText(),
                hint = stop.markerDetail().ifEmpty { null },
                state = stateOf(stop, targetStationId),
            )
        }

    fun stateOf(stop: MissionStop, targetStationId: Long): StopState = when {
        stop.isFailed -> StopState.FAILED
        stop.isDone -> StopState.DONE
        stop.isActive -> StopState.CURRENT
        // 后端没给状态时才退回"本机导航目标"，这只代表导航看向哪一站，不代表业务推进
        stop.status < 0 && targetStationId != 0L && stop.stationId == targetStationId -> StopState.CURRENT
        else -> StopState.PENDING
    }

    /**
     * 到站卡片的距离文案（审计 §32）。
     * · 有后端道路值就报道路距离；
     * · 没有就写"接口未给"，最多补一句**带标注**的直线远近（与 `BusScreen.kt:498` 同一口径）；
     * · 直线值绝不参与"能否确认到达"的判定 —— `/driver/arrive` 只收 shiftId+stationId，到站是司机声明。
     */
    fun nextStopDistanceText(roadMeters: Int, straightMeters: Double, targetName: String): String {
        val name = targetName.ifEmpty { "目标站" }
        return when {
            roadMeters > 0 -> "距「$name」还有约 ${formatRoadMeters(roadMeters)}（道路里程）"
            straightMeters >= 0 -> "距「$name」直线约 ${straightMeters.toInt()} 米（只表示远近，不是道路里程）；道路里程暂无"
            else -> "距「$name」的道路里程暂无（还没拿到当前位置，远近也判不了）"
        }
    }

    /**
     * 偏航那一行的说法（#49：后端 `deviationMeters` 一直在发，本机以前只有 `deviated` 一个位）。
     *
     * 只报**后端量出来的数**，不报结论：`deviated` 缺键时模型塌成 false，所以"未偏航"这一句
     * 本机永远说不出（它分不清"后端说不偏"与"后端没答"）；反过来 `deviated=true` 是后端正面给的，
     * 可以照说。这一条不对称是有意的，不是偷懒。
     *
     * 三格读数各有各的话：没发键 / 发了但后端自己标"无法判定"（VO:53 的 -1）/ 有真实米数。
     * 本机一律不补数 —— 垂距那条已经明确"不参与偏航判定"（`DriverNavViewModel.crossTrackMeters` 的注释）。
     */
    fun deviationText(guide: RouteGuide): String = when {
        guide.deviationKnown -> {
            val meters = guide.deviationMeters!!.toInt()
            "距规划线 ${meters} 米（后端量）" + if (guide.deviated) " · 后端标记偏航" else ""
        }
        guide.deviationUndeterminable -> "偏航距离：后端说这一趟判定不了" +
            if (guide.deviated) " · 但后端标记了偏航" else ""
        else -> "偏航读数：后端没给" + if (guide.deviated) " · 不过后端标记了偏航" else ""
    }

    fun MissionStop.markerTitle(): String {
        val seq = if (visitSequence > 0) "$visitSequence · " else ""
        return "$seq${name.ifEmpty { "任务点" }}"
    }

    fun MissionStop.markerDetail(): String {
        val parts = listOfNotNull(
            actionLabel.ifEmpty { null },
            quantityText(),
            statusLabel.ifEmpty { null },
        )
        return parts.joinToString(" · ")
    }

    /** 到站时间只报后端给的值；没给就不显示，不猜 */
    fun MissionStop.etaText(): String? = if (!hasEstimatedArrival) null
    else "预计 " + java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA)
        .format(java.util.Date(estimatedArrivalMillis))

    /** 接客/下车计"人"，揽收/派送计"件"（后端 VO 对 quantity 的口径注释） */
    fun MissionStop.quantityText(): String? = when {
        quantity <= 0 -> null
        actionType == 1 || actionType == 2 -> "$quantity 人"
        actionType == 3 || actionType == 4 -> "$quantity 件"
        else -> "$quantity 项"
    }

    fun RouteGuide.routeSourceLabel(): String = when (routeSourceName) {
        "" -> "位置来源：暂未知"
        else -> "位置来源：$routeSourceName"
    }
}
