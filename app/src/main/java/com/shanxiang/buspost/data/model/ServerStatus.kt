package com.shanxiang.buspost.data.model

import com.shanxiang.buspost.core.ui.BusTone

/**
 * 后端业务状态码的**唯一**映射表（审计 §18）。
 *
 * 码值与中文名逐条抄自后端源码，**按枚举码 keyed，不是顺序号**：
 *  - `TransportOrderStatusEnum.java:18-44` → 0..13（审核态 6/7/8 是插在流程前面的，不是第 6/7/8 步）
 *  - `TransportLegStatusEnum.java:24-36`   → 0..11 + 99 异常
 *  - `ProductOrderStatusEnum.java:14-20`   → 0..3（**1 是"已发货"**，不是"配送中"）
 *  - `ReviewStatusEnum.java:20-24`         → 0..4
 *  - `TaskItemStatusEnum.java:24-32`       → 0..8（7 完成 / 8 失败）
 *  - `TransportHandoverStatusEnum.java:24-31` → 0..7
 *
 * 为什么要专门写这一段：`AppSendLegRespVO:22` 的 `@Schema` 注释写的是
 * "0待分配 1已分配 2运输中 3已到达 4已交接 5异常"，**与 `TransportLegStatusEnum` 不符**
 * （同类注释错误还有 `AppDriverHandoverRespVO:52`）。照着注释排表就会出现 §18 的事故：
 * 后端 `4 已完成` 被显示成"已入池"、`7 待客户操作` 被显示成"已完成"。
 * **唯一可信的是枚举本身**，注释只是注释。逐码对照表见 `docs/STATUS_MAPPING.md`。
 *
 * 读法约定（与小程序 `o.statusName || statusText(o.status)` 同构）：
 * 后端下发了给人看的 `statusName` 就**先用后端的**，本表只在没给名字时兜底，
 * 并负责回答"这个码是什么语义"（终态 / 进行中 / 需要谁操作）—— 页面不得自己 `status == 4` 这样判断。
 */

/** 后端给了码值但不在已知枚举里：只能说"暂不可用"，不得当成 0、也不得当成完成。 */
const val STATUS_UNAVAILABLE_LABEL = "状态暂不可用"

/** 状态码的公共形态：码值 + 后端中文名 + 配色语义。 */
sealed interface ServerStatus {
    val code: Int
    val label: String
    val tone: BusTone
}

/**
 * 货运订单（寄货单）状态：`TransportOrderStatusEnum`。
 * 终态判据抄后端 `isFinal()`（`TransportOrderStatusEnum.java:56-58`）：只有已完成 / 已取消是终态。
 */
enum class FreightOrderStatus(
    override val code: Int,
    override val label: String,
    override val tone: BusTone,
) : ServerStatus {
    CREATED(0, "已创建", BusTone.NEUTRAL),

    /** 已归入调度订单池 —— 注意是 1，不是注释里的"待审核" */
    POOLED(1, "已入池", BusTone.PRIMARY),
    ASSIGNED(2, "已分配", BusTone.PRIMARY),
    DEPARTED(3, "已发车", BusTone.PRIMARY),
    COMPLETED(4, "已完成", BusTone.OK),
    CANCELLED(5, "已取消", BusTone.DANGER),

    /** 审核前置三态：码值大、流程靠前，任何"按数值排序流程"的写法都会在这里翻车 */
    PENDING_REVIEW(6, "待审核", BusTone.WARN),
    WAITING_CUSTOMER_ACTION(7, "待客户操作", BusTone.WARN),
    READY_FOR_POOL(8, "待入池", BusTone.WARN),

    PARTIALLY_COMPLETED(9, "部分完成", BusTone.PRIMARY),
    IN_TRANSIT(10, "运输中", BusTone.PRIMARY),
    TRANSFERRING(11, "换乘中", BusTone.PRIMARY),
    DELIVERING(12, "派送中", BusTone.PRIMARY),
    EXCEPTION(13, "异常", BusTone.DANGER);

    /** 后端 `isFinal()`：终态不再流转，页面不得再给"取消/催单"这类动作 */
    val isFinal: Boolean get() = this == COMPLETED || this == CANCELLED

    /** 货已在路上（含部分完成/换乘/派送），页面才允许显示实时轨迹与承运信息 */
    val isOnTheWay: Boolean
        get() = this == DEPARTED || this == PARTIALLY_COMPLETED || this == IN_TRANSIT ||
            this == TRANSFERRING || this == DELIVERING

    /** 需要用户做点什么（送到就近站点 / 去取货）：对应后端强提醒 */
    val needsUserAction: Boolean get() = this == WAITING_CUSTOMER_ACTION || this == DELIVERING

    companion object {
        fun of(code: Int?): FreightOrderStatus? = if (code == null) null else values().firstOrNull { it.code == code }
    }
}

/** 运输段状态：`TransportLegStatusEnum`（0..11 + 99）。合法流转表在后端 `TRANSITIONS`，客户端只读不推。 */
enum class LegStatus(
    override val code: Int,
    override val label: String,
    override val tone: BusTone,
) : ServerStatus {
    PLANNED(0, "已规划", BusTone.NEUTRAL),
    ASSIGNED(1, "已分配", BusTone.NEUTRAL),
    DRIVER_ACCEPTED(2, "司机已接单", BusTone.PRIMARY),
    WAITING_START(3, "等待发车", BusTone.PRIMARY),
    NAVIGATING(4, "前往起点", BusTone.PRIMARY),
    ARRIVED_ORIGIN(5, "已到达起点", BusTone.PRIMARY),
    LOADING(6, "装货中", BusTone.PRIMARY),
    IN_TRANSIT(7, "运输中", BusTone.PRIMARY),
    ARRIVED_DESTINATION(8, "已到达终点", BusTone.PRIMARY),
    HANDOVER(9, "交接中", BusTone.WARN),
    DELIVERING(10, "派送中", BusTone.PRIMARY),

    /** 唯一的"段完成"码。小程序同值（`l.status === 11`） */
    COMPLETED(11, "已完成", BusTone.OK),
    EXCEPTION(99, "异常", BusTone.DANGER);

    /**
     * 段进行中：7 运输中 … 10 派送中（与既有 `TransportLeg.active` 同区间）。
     * **不含** 4/5/6 —— 那是"车还在去起点的路上"，旧代码 `status >= 4` 就是把这段错显成已完成/在途。
     */
    val isActive: Boolean get() = this == IN_TRANSIT || this == ARRIVED_DESTINATION || this == HANDOVER || this == DELIVERING

    val isDone: Boolean get() = this == COMPLETED

    companion object {
        fun of(code: Int?): LegStatus? = if (code == null) null else values().firstOrNull { it.code == code }
    }
}

/** 商城订单状态：`ProductOrderStatusEnum`。1 是"已发货"（§33 的错位就出在本地表把它写成"配送中"）。 */
enum class ProductOrderStatus(
    override val code: Int,
    override val label: String,
    override val tone: BusTone,
) : ServerStatus {
    PENDING_DELIVERY(0, "待发货", BusTone.WARN),
    DELIVERED(1, "已发货", BusTone.PRIMARY),
    COMPLETED(2, "已完成", BusTone.OK),
    CANCELLED(3, "已取消", BusTone.DANGER);

    val isFinal: Boolean get() = this == COMPLETED || this == CANCELLED

    companion object {
        fun of(code: Int?): ProductOrderStatus? = if (code == null) null else values().firstOrNull { it.code == code }
    }
}

/** 审核状态：`ReviewStatusEnum`。2 = 需客户操作（要引导送到就近站点），4 = 审核不通过。 */
enum class ReviewStatus(
    override val code: Int,
    override val label: String,
    override val tone: BusTone,
) : ServerStatus {
    PENDING(0, "待审核", BusTone.NEUTRAL),
    PASSED(1, "审核通过", BusTone.OK),
    CONDITIONAL(2, "需客户操作", BusTone.WARN),
    MANUAL_REVIEW(3, "需人工审核", BusTone.WARN),
    REJECTED(4, "审核不通过", BusTone.DANGER);

    companion object {
        fun of(code: Int?): ReviewStatus? = if (code == null) null else values().firstOrNull { it.code == code }
    }
}

/**
 * 任务点状态：`TaskItemStatusEnum`（0..8）。
 * 后端注释即写明"前端不得用 Boolean 代替状态机"，所以 7 才是完成，8 是失败而不是"另一种完成"。
 */
enum class TaskItemStatus(
    override val code: Int,
    override val label: String,
    override val tone: BusTone,
) : ServerStatus {
    PENDING(0, "待执行", BusTone.NEUTRAL),
    EN_ROUTE(1, "行驶中", BusTone.PRIMARY),
    ARRIVED(2, "已到站", BusTone.PRIMARY),
    BOARDING(3, "上车中", BusTone.PRIMARY),
    ALIGHTING(4, "下车中", BusTone.PRIMARY),
    PICKUP(5, "揽收中", BusTone.PRIMARY),
    DELIVERY(6, "派送中", BusTone.PRIMARY),
    COMPLETED(7, "已完成", BusTone.OK),
    FAILED(8, "失败", BusTone.DANGER);

    /** 1..6 都是"正在进行"：后端还没给出终态，界面不得用"完成"色 */
    val isActive: Boolean get() = this != PENDING && this != COMPLETED && this != FAILED

    val isDone: Boolean get() = this == COMPLETED

    companion object {
        fun of(code: Int?): TaskItemStatus? = if (code == null) null else values().firstOrNull { it.code == code }

        /** 任务点动作名：`DriverAppServiceImpl.java:123` 的 `ACTION_NAMES`（0 出发 … **6 经停**） */
        val ACTION_NAMES = mapOf(
            0 to "出发", 1 to "接客", 2 to "送客", 3 to "派送", 4 to "揽收", 5 to "返回", 6 to "经停",
        )
    }
}

/** 换乘交接状态：`TransportHandoverStatusEnum`（0..7）。见 §25：司机侧"能不能确认收货"由它决定。 */
enum class HandoverStatus(
    override val code: Int,
    override val label: String,
    override val tone: BusTone,
) : ServerStatus {
    WAITING(0, "等待交接", BusTone.NEUTRAL),
    SOURCE_ARRIVED(1, "前序已到达", BusTone.PRIMARY),
    TARGET_WAITING(2, "接收方待接", BusTone.PRIMARY),
    HANDOVER(3, "交接中", BusTone.WARN),
    COMPLETED(4, "交接完成", BusTone.OK),
    TIMEOUT(5, "交接超时", BusTone.DANGER),
    CANCELLED(6, "已取消", BusTone.NEUTRAL),
    EXCEPTION(7, "异常", BusTone.DANGER);

    val isDone: Boolean get() = this == COMPLETED

    companion object {
        fun of(code: Int?): HandoverStatus? = if (code == null) null else values().firstOrNull { it.code == code }
    }
}

/** 文案：已知码给后端同名文案，未知码给"状态暂不可用"（不猜、不回落成 0 的文案）。 */
fun FreightOrderStatus.Companion.labelOf(code: Int?): String = FreightOrderStatus.of(code)?.label ?: STATUS_UNAVAILABLE_LABEL

fun LegStatus.Companion.labelOf(code: Int?): String = LegStatus.of(code)?.label ?: STATUS_UNAVAILABLE_LABEL

fun ProductOrderStatus.Companion.labelOf(code: Int?): String = ProductOrderStatus.of(code)?.label ?: STATUS_UNAVAILABLE_LABEL

fun ReviewStatus.Companion.labelOf(code: Int?): String = ReviewStatus.of(code)?.label ?: STATUS_UNAVAILABLE_LABEL

fun TaskItemStatus.Companion.labelOf(code: Int?): String = TaskItemStatus.of(code)?.label ?: STATUS_UNAVAILABLE_LABEL

fun HandoverStatus.Companion.labelOf(code: Int?): String = HandoverStatus.of(code)?.label ?: STATUS_UNAVAILABLE_LABEL

/** 配色：未知码一律 NEUTRAL —— 危险/完成这类强语义不能凭"猜"上色。 */
fun FreightOrderStatus.Companion.toneOf(code: Int?): BusTone = FreightOrderStatus.of(code)?.tone ?: BusTone.NEUTRAL

fun LegStatus.Companion.toneOf(code: Int?): BusTone = LegStatus.of(code)?.tone ?: BusTone.NEUTRAL

fun ProductOrderStatus.Companion.toneOf(code: Int?): BusTone = ProductOrderStatus.of(code)?.tone ?: BusTone.NEUTRAL
