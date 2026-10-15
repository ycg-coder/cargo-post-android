package com.shanxiang.buspost.ui.driver

import com.shanxiang.buspost.data.model.LegStatus
import com.shanxiang.buspost.data.model.STATUS_UNAVAILABLE_LABEL
import com.shanxiang.buspost.data.model.TransportLeg

/**
 * 司机端可发给 `POST /driver/leg/{action}` 的动作。
 * `target` 抄后端 `DriverAppServiceImpl#legAction:1482-1500`，不是本机猜的：
 * 后端每个动作对应一次 `advanceLegStatus(到 target)`，非法流转会被 `TransportLegStatusEnum.canTransit` 拦下。
 */
enum class LegDriverAction(
    val api: String,
    val label: String,
    val target: LegStatus?,
    /** 需要凭证照片的动作必须在提交前拿到真实 URL，否则宁可不提交（审计 §7） */
    val needsPhoto: Boolean = false,
) {
    ACCEPT("accept", "接受这一段", LegStatus.DRIVER_ACCEPTED),
    NAVIGATE("navigate", "开始导航去装货站", LegStatus.NAVIGATING),
    ARRIVE_ORIGIN("arrive-origin", "到达装货站", LegStatus.ARRIVED_ORIGIN),
    LOAD("load", "装车中", LegStatus.LOADING),
    START("start", "已发车", LegStatus.IN_TRANSIT),
    ARRIVE_DEST("arrive-dest", "到达卸货站", LegStatus.ARRIVED_DESTINATION),

    /**
     * 最终段"到达终点"在后端是一次 7→8→10（`DriverAppServiceImpl#arriveDestination:1521-1528`），
     * 段停在 8 又不需交接时，只有再发一次 `arrive-dest` 才会转入派送 —— 这是后端分支，不是重复动作。
     */
    ENTER_DELIVERY("arrive-dest", "确认到达，转入派送", LegStatus.DELIVERING),
    HANDOVER_START("handover-start", "发起换乘交接", LegStatus.HANDOVER),
    HANDOVER_CONFIRM("handover-confirm", "拍照确认交接", LegStatus.COMPLETED, needsPhoto = true),
    COMPLETE("complete", "完成这一段", LegStatus.COMPLETED),

    /** 后端对非最终段的 `complete` 直接抛"请先在换乘站完成交接"（`DriverAppServiceImpl#completeLeg:1538-1546`），所以中间段排不出这一步 */
    EXCEPTION("exception", "上报异常", LegStatus.EXCEPTION);
}

/**
 * 段动作按钮点下去该走哪条路（#53）。
 *
 * 为什么单独成一个导出函数：这条分流原先只写在 Composable 的 `onClick` 里，工程无 Robolectric ⇒ 它红不了，
 * 而它管的是"凭证会不会永久丢失"那一格。提出来之后用例能钉住"需要凭证的动作绝不在路线页就地发"。
 *
 * 后果有原文依据：`HandoverServiceImpl.java:210-211` 对**已完成**的交接是幂等 return，
 * 所以先落一次没有 photoUrl 的提交，照片那一格再也补不进去；而交接页同一动作本来就拦
 *（`HandoverScreen.kt:486-494`「绝不在这发一次没有 photoUrl 的 handover-confirm」）——
 * 两个入口必须给同一个答案。
 */
enum class LegClick { SEND, GO_HANDOVER }

fun legClickOf(action: LegDriverAction?): LegClick =
    if (action != null && action.needsPhoto) LegClick.GO_HANDOVER else LegClick.SEND

/** 一段当前能做什么：动作、时间线走到第几步、以及"没有动作"时的原因。 */
data class LegFlowState(
    val steps: List<LegDriverAction>,
    val doneSteps: Int,
    val next: LegDriverAction?,
    val waitingReason: String?,
    val canReportException: Boolean,
    val isCompleted: Boolean,
) {
    val actionable: Boolean get() = next != null || canReportException
}

/**
 * 运输段状态机映射（审计 §10 / §26–§28）。
 *
 * 存在理由：`AppSendLegRespVO:22` 的注释写了另一张码表（`0待分配 1已分配 2运输中 3已到达 4已交接 5异常`），
 * 旧 `RoutesScreen.nextActionOf` 和 `HandoverScreen.legStep` 照那张错表排，于是 `status >= 4` 当成完成、
 * `status == 5` 当成异常去"重新接单" —— 真实表里 4 是"前往起点"、5 是"已到达起点"、异常是 99。
 * 现在段状态只有这一处出口，页面不再自己比数字。流转合法性以后端 `TRANSITIONS` 为准，客户端只读不推。
 */
object DriverLegStateMapper {

    /** 时间线步序：中间段以"拍照确认交接"收尾，最终段以"完成这一段"收尾 */
    fun stepsOf(leg: TransportLeg): List<LegDriverAction> =
        if (leg.handoverRequired) {
            listOf(
                LegDriverAction.ACCEPT, LegDriverAction.NAVIGATE, LegDriverAction.ARRIVE_ORIGIN,
                LegDriverAction.LOAD, LegDriverAction.START, LegDriverAction.ARRIVE_DEST,
                LegDriverAction.HANDOVER_START, LegDriverAction.HANDOVER_CONFIRM,
            )
        } else {
            listOf(
                LegDriverAction.ACCEPT, LegDriverAction.NAVIGATE, LegDriverAction.ARRIVE_ORIGIN,
                LegDriverAction.LOAD, LegDriverAction.START, LegDriverAction.ARRIVE_DEST,
                LegDriverAction.COMPLETE,
            )
        }

    fun of(leg: TransportLeg): LegFlowState {
        val steps = stepsOf(leg)
        val status = LegStatus.of(leg.status)
        val next = nextActionOf(leg, status)
        val completed = status == LegStatus.COMPLETED
        return LegFlowState(
            steps = steps,
            doneSteps = if (completed) steps.size else completedCount(status, leg.handoverRequired),
            next = next,
            waitingReason = if (next != null) null else waitingReasonOf(status),
            // 异常由调度重排（docs/STATUS_MAPPING.md §4）：本机不发 accept 重试，也不许在终态段上再报
            canReportException = status != null && status != LegStatus.COMPLETED && status != LegStatus.EXCEPTION,
            isCompleted = completed,
        )
    }

    /** 只给"下一步"，页面不得把整条时间线当成可点列表 */
    fun nextActionOf(leg: TransportLeg): LegDriverAction? = nextActionOf(leg, LegStatus.of(leg.status))

    private fun nextActionOf(leg: TransportLeg, status: LegStatus?): LegDriverAction? = when (status) {
        // 0 已规划：还没派到司机手上，accept 会撞 0→2 的非法流转
        LegStatus.PLANNED -> null
        LegStatus.ASSIGNED -> LegDriverAction.ACCEPT
        LegStatus.DRIVER_ACCEPTED, LegStatus.WAITING_START -> LegDriverAction.NAVIGATE
        LegStatus.NAVIGATING -> LegDriverAction.ARRIVE_ORIGIN
        LegStatus.ARRIVED_ORIGIN -> LegDriverAction.LOAD
        LegStatus.LOADING -> LegDriverAction.START
        LegStatus.IN_TRANSIT -> LegDriverAction.ARRIVE_DEST
        LegStatus.ARRIVED_DESTINATION -> if (leg.handoverRequired) LegDriverAction.HANDOVER_START else LegDriverAction.ENTER_DELIVERY
        LegStatus.HANDOVER -> if (leg.handoverRequired) LegDriverAction.HANDOVER_CONFIRM else null
        LegStatus.DELIVERING -> LegDriverAction.COMPLETE
        LegStatus.COMPLETED, LegStatus.EXCEPTION, null -> null
    }

    /**
     * 已走完几步：按码值的业务语义数，**不是**"数值到某档就算完成"。
     * 异常/读不到都是 0 —— 后端只存了"异常"，没存断在哪一步，本机不许猜进度。
     */
    private fun completedCount(status: LegStatus?, handoverRequired: Boolean): Int = when (status) {
        LegStatus.PLANNED, LegStatus.ASSIGNED, LegStatus.EXCEPTION, null -> 0
        LegStatus.DRIVER_ACCEPTED, LegStatus.WAITING_START -> 1
        LegStatus.NAVIGATING -> 2
        LegStatus.ARRIVED_ORIGIN -> 3
        LegStatus.LOADING -> 4
        LegStatus.IN_TRANSIT -> 5
        LegStatus.ARRIVED_DESTINATION -> 6
        LegStatus.HANDOVER -> if (handoverRequired) 7 else 6
        LegStatus.DELIVERING -> 6
        LegStatus.COMPLETED -> if (handoverRequired) 8 else 7
    }

    private fun waitingReasonOf(status: LegStatus?): String? = when (status) {
        null, LegStatus.EXCEPTION ->
            "${STATUS_UNAVAILABLE_LABEL}：${if (status == null) "状态未知" else "该段已异常"}，请联系调度"
        LegStatus.PLANNED -> "本段尚未派给你，等待调度派单"
        LegStatus.COMPLETED -> null
        // 段在"交接中"却不需要交接：两个字段互相矛盾，本机不挑一个信，等回读
        LegStatus.HANDOVER -> "本段在交接中，但缺少换乘交接单，请下拉刷新核对"
        else -> "请下拉刷新"
    }
}
