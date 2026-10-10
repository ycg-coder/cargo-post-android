package com.shanxiang.buspost.data.model

import org.json.JSONObject

/**
 * 调度任务点：`/driver/route` 的 `stops[]`（后端 `AppDriverRouteRespVO.Stop`），任务族复用见 [parseTaskItem]。
 *
 * 键名严格按后端：`visitSequence / actionType / actionName / quantity / status / statusName`。
 * 不复用 [Station] —— Station 同时服务 `/bus/nearby` 的公交站，为了任务点去改它的键名集合
 * 会把公交侧既有的 distanceKm 等兼容逻辑一起牵扯进来。
 *
 * 顺序只来自后端 `visitSequence`：调度决定去哪里（硬规则 3），客户端不得重排。
 */
data class MissionStop(
    val stationId: Long = 0,
    val name: String = "",
    val geo: Geo = Geo(0.0, 0.0),
    val visitSequence: Int = 0,
    /** 0 出发 / 1 接客 / 2 送客 / 3 派送 / 4 揽收 / 5 返回 / 6 经停（后端 VO 注释漏了 6，表见 [TaskItemStatus.ACTION_NAMES]） */
    val actionType: Int = -1,
    val actionName: String = "",
    val orderId: Long = 0,
    val orderNo: String = "",
    /** 接客/下车=人数，揽收/派送=件数 */
    val quantity: Int = 0,
    /** `TaskItemStatusEnum`：0 待执行 … 7 已完成 8 失败 */
    val status: Int = -1,
    val statusName: String = "",
    /** 后端 LocalDateTime 走 `TimestampLocalDateTimeSerializer`，线上一律是毫秒时间戳 */
    val estimatedArrivalMillis: Long = 0,
    val hasEstimatedArrival: Boolean = false,
) {
    val hasGeo: Boolean get() = geo.lat != 0.0 && geo.lng != 0.0

    val isDone: Boolean get() = TaskItemStatus.of(status)?.isDone == true
    val isFailed: Boolean get() = TaskItemStatus.of(status) == TaskItemStatus.FAILED

    /** 1..6 都是"正在进行"（行驶中 → 派送中）：后端还没给出终态，界面不能用"完成"色 */
    val isActive: Boolean get() = TaskItemStatus.of(status)?.isActive == true

    /** 后端没给中文动作名时的兜底（仅在 actionName 为空时使用）；单一表见 [TaskItemStatus.ACTION_NAMES]，含 6 经停 */
    val actionLabel: String
        get() = actionName.ifEmpty { TaskItemStatus.ACTION_NAMES[actionType] ?: "" }

    // -1 是模型自己的"后端没给 status 键"哨兵，不是后端状态码，所以留空让页面按未标注处理；
    // 后端真给了表外的码才由 labelOf 说"状态暂不可用"。
    val statusLabel: String
        get() = statusName.ifEmpty { if (status < 0) "" else TaskItemStatus.labelOf(status) }

    /** 到站时间拿不到就说"未给"，不猜 */
    val hasEta: Boolean get() = hasEstimatedArrival

    companion object {
        const val STATUS_PENDING = 0
        const val STATUS_EN_ROUTE = 1
        const val STATUS_ARRIVED = 2
        const val STATUS_DELIVERING = 6
        const val STATUS_COMPLETED = 7
        const val STATUS_FAILED = 8

        /**
         * `/driver/tasks` 的一行（`AppDriverTaskRespVO`，生产者 `DriverAppServiceImpl.tasks`）读成任务点。
         *
         * 与 [parse] 同一批键名（`stationId / stationName / visitSequence / actionType / actionName /
         * orderId / orderNo / quantity / status / statusName / estimatedArrivalTime` 两个 VO 都原名下发），
         * 所以任务段面板直接复用 [MissionStop] 与 `MissionMarkers` 那套标签/状态出口，不另起第二套说法。
         * 但两处必须分开，不能图省事共用 [parse]：
         * ① **不认 `id` / `name` 别名** —— 任务 VO 的 `id` 是**调度明细编号**（`AppDriverTaskRespVO.java` 的
         *    「调度明细编号」），落进 `stationId` 就是把一个不存在的站当成分组键，同站聚合会散成每行一站；
         * ② **不读坐标** —— 这个 VO 全文没有 `longitude` / `latitude`（站点坐标只在 `/driver/route` 的
         *    `stops` 里给）⇒ [hasGeo] 恒 false，任务段只做路单，不许拿去画地图。
         */
        fun parseTaskItem(o: JSONObject): MissionStop {
            val eta = o.opt("estimatedArrivalTime")
            val etaNumber = (eta as? Number)?.toLong() ?: -1L
            // 这七处一律走 multiNumOrNull：VO 的 19 个键全是包装类型（Long/Integer），后端不给就是"没给"，
            // 而 multiNum 缺键塌 0 会把它说成一个真值 —— stationId=0 会被 groupBy 当成一个存在的站、
            // quantity=0 会渲染成"0 件"、visitSequence=0 会被排到第一段第一位。哨兵沿用本文件既有的 -1：
            // 页面侧 markerTitle 只在 >0 时显示序号、quantityText 只在 >0 时显示数量、status/ActionType 的 -1 走"未给"分支。
            return MissionStop(
                stationId = o.multiNumOrNull("stationId")?.toLong() ?: -1L,
                name = o.multiStr("stationName"),
                visitSequence = o.multiNumOrNull("visitSequence")?.toInt() ?: -1,
                actionType = o.multiNumOrNull("actionType")?.toInt() ?: -1,
                actionName = o.multiStr("actionName"),
                orderId = o.multiNumOrNull("orderId")?.toLong() ?: -1L,
                orderNo = o.multiStr("orderNo"),
                quantity = o.multiNumOrNull("quantity")?.toInt() ?: -1,
                status = o.multiNumOrNull("status")?.toInt() ?: -1,
                statusName = o.multiStr("statusName"),
                estimatedArrivalMillis = if (etaNumber > 0) etaNumber else 0,
                hasEstimatedArrival = etaNumber > 0,
            )
        }

        fun parse(o: JSONObject): MissionStop {
            val eta = o.opt("estimatedArrivalTime")
            val etaNumber = (eta as? Number)?.toLong() ?: -1L
            return MissionStop(
                stationId = o.multiNum("stationId", "id").toLong(),
                name = o.multiStr("stationName", "name"),
                geo = Geo(o.multiNum("latitude", "lat"), o.multiNum("longitude", "lng")),
                visitSequence = o.multiNum("visitSequence").toInt(),
                actionType = if (o.has("actionType")) o.multiNum("actionType").toInt() else -1,
                actionName = o.multiStr("actionName"),
                orderId = o.multiNum("orderId").toLong(),
                orderNo = o.multiStr("orderNo"),
                quantity = o.multiNum("quantity").toInt(),
                status = if (o.has("status")) o.multiNum("status").toInt() else -1,
                statusName = o.multiStr("statusName"),
                estimatedArrivalMillis = if (etaNumber > 0) etaNumber else 0,
                hasEstimatedArrival = etaNumber > 0,
            )
        }
    }
}
