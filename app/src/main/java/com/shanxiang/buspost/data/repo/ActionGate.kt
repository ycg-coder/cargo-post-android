package com.shanxiang.buspost.data.repo

import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.ConcurrentHashMap

/** 一次提交的结局：真的发出去了（[Sent]），或者被本地闸拦下、**根本没上网络**（[Duplicate]）。 */
sealed class GateResult<out T> {
    data class Sent<T>(val value: T) : GateResult<T>()
    data object Duplicate : GateResult<Nothing>()
}

/**
 * 司机动作的本地幂等闸（V-1 / 指令 §52）。
 *
 * 为什么只能在客户端做：后端 `AppDriverController` 全类**零** `@Idempotent`，11 个司机 ReqVO 里
 * 也**没有任何请求级唯一键**（`clientRequestId`/`requestId`/`bizNo` 全部不存在），所以客户端没有"可复用的键"，
 * 也**绝不允许自造一个后端不收的字段**。框架自带的 `@Idempotent` 只装在两个下单接口上
 * （`AppProductOrderController.create` / `AppSendController.create`，10 秒窗口、重复回错误码 900）。
 *
 * 为什么"防重复点击"在这里不是体验问题而是数据问题 —— 逐条读过后端实现，四个写路径重复提交都会留脏数据：
 * · `depart`（`DriverAppServiceImpl:861-868`）：执行记录状态不是"在途"时会被**改回在途**，
 *   并且 `arriveTime` 置 null、`currentStationId` 退回首站 —— 本班已收的进度直接消失；
 * · `pickup-confirm`（`:1069-1087` + `PICKUP_CONFIRM_STATUSES = List.of(0,1,2,3)` 见 `:118`）：
 *   已发车(3) 在允许集合里，所以 CAS 走 3→3 成功，随后 `incrementLoadedCount*` **再把已装件数 +1**，
 *   容量被虚占，后来的货会撞 `DRIVER_CARGO_FULL`；
 * · `leg/complete`：`advanceLegStatus` 与 `updateOrderStatus` 都"同状态即跳过"
 *   （`MultiLegServiceImpl:706-710`、`DriverAppServiceImpl:1595-1597`），跳过不是失败，
 *   于是后面的 `orderEventService.record` 照跑 —— 而它是**无条件 insert**（`OrderEventServiceImpl:41-58`），
 *   `transport_order_event` 没有唯一索引兜底，每重发一次多一条"订单完成"事件、用户多收一条完成通知；
 * · `leg/arrive-dest`（需换乘）：`HandoverServiceImpl:107-145` 的建单/改状态/推进订单三处都有幂等跳过，
 *   但 `record(HANDOVER_REQUIRED)` 与两条推送照旧执行 → 多一条事件、后序司机与货主各多收一次接驳提醒。
 *   （不需换乘的那一支是例外：第二次会撞 `TransportLegStatusEnum:53` 的守卫抛 `LEG_TRANSITION_ILLEGAL`，
 *   不产生脏数据，但司机会看到一条莫名其妙的后端报错。）
 *
 * 三条刻意的取舍：
 * · **[GateResult.Duplicate] 是"丢弃"而不是"排队"** —— 用 `Mutex.withLock` 会把第二次提交排到队列里、
 *   等第一次完成后**照样发给后端**，那正是这里要避免的副作用，所以只能用 [tryLock]。
 * · **成功后继续持有 [successHoldMs]** —— 页面的 `busy` 旗随 ViewModel 一起被销毁（切壳 `popUpTo inclusive`），
 *   协程取消会释放锁，但请求可能已经在后端生效；这段窗口就是补那个缺口的。时长取 10 秒是为了和后端
 *   `@Idempotent(timeout = 10)` 同一口径，而不是拍脑袋。
 * · **异常立即释放**（等价于后端 `deleteKeyWhenException = true`）—— 业务失败后必须能马上重试，
 *   否则一次网络抖动就把这个动作永久锁死。
 *
 * 锁按 key 分桶：不同班次/不同段/不同订单之间互不阻塞，只有"同一个目标上的同一个动作"才会被拦。
 */
class ActionGate(private val now: () -> Long = System::currentTimeMillis) {

    /** 成功之后仍视为"同一动作在途中"的时长；测试里调短它来证窗口边界。 */
    internal var successHoldMs: Long = SUCCESS_HOLD_MS

    private val locks = ConcurrentHashMap<String, Mutex>()
    private val heldUntil = ConcurrentHashMap<String, Long>()

    suspend fun <T> submit(key: String, request: suspend () -> T): GateResult<T> {
        if ((heldUntil[key] ?: 0L) > now()) return GateResult.Duplicate
        // key 基数受"动作 × 班次/段/订单"约束，不做淘汰：淘汰正在使用的 Mutex 反而会把闸拆掉。
        val mutex = locks.getOrPut(key) { Mutex() }
        if (!mutex.tryLock()) return GateResult.Duplicate
        try {
            val value = request()
            heldUntil[key] = now() + successHoldMs
            return GateResult.Sent(value)
        } catch (e: Throwable) {
            heldUntil.remove(key)
            throw e
        } finally {
            mutex.unlock()
        }
    }

    companion object {
        /** 与后端 `@Idempotent(timeout = 10, SECONDS)` 同口径 */
        internal const val SUCCESS_HOLD_MS = 10_000L
    }
}
