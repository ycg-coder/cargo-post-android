package com.shanxiang.buspost.driver

import com.shanxiang.buspost.core.loc.TrackFix

/**
 * 位置上报策略（纯函数，不碰 Android API，所以能在 JVM 里逐条钉住）。
 *
 * 为什么**没有**传统意义上的"重放队列"（审计 §45 原方案）—— 后端事实：
 * `AppDriverLocationReqVO` 只有 `driverId/shiftId/longitude/latitude/speedKmh`，**没有客户端时间字段**；
 * `DriverAppServiceImpl.reportLocation` 用 `reportTime = LocalDateTime.now()` 盖章，
 * 并把它同时写进"车辆最新位置"与 `transport_vehicle_location_track`。
 * 于是把一个 20 分钟前的点在今天补上去，服务端会记成"**此刻**车在这里"——
 * 轨迹上凭空多出一个时间错误、位置也错误的点，管理端与"车快到了"提醒都会信它。
 * 这就是"宁可没有上报，不允许假上报"（硬规则 20）在本条链路的具体形状。
 *
 * 所以这里的语义是**合并（coalescing）而不是重放（replay）**：
 * 每一拍只可能发"当前最新那一条 fix"，失败时它被更新的点顶替，过期的点直接放弃并计数可见。
 * 代价如实记入 `DECISION_LOG.md`：弱网期间的历史缺口仍然缺（后端没有可承接时间的字段），
 * 要真正补报必须由后端在 VO 上增加定位时间字段，已登记 §49 契约待办。
 */
object LocationReportPolicy {

    /** 与 `Prefs.reportIntervalSec` 的可配区间一致 */
    const val MIN_INTERVAL_SEC = 3
    const val MAX_INTERVAL_SEC = 60

    /** 退避上限：再久村民端就看不到车了 */
    const val MAX_BACKOFF_MS = 60_000L

    /**
     * 点龄上限：超过就**停报**而不是把旧点当当前位置发出去。
     * 采样周期是 5 秒，45 秒 ≈ 连续 9 个采样周期没有新点 —— 这已经是"定位源掉了"，
     * 不是"车停着不动"（车停着时 GPS 仍会每几秒给出新 fix，点龄不会涨）。
     */
    const val MAX_FIX_AGE_MS = 45_000L

    /** 退避倍数上限：2^3 = 8 倍后不再增长，只受 [MAX_BACKOFF_MS] 约束 */
    private const val MAX_SHIFT = 3

    /** 一拍该做什么 */
    sealed class Step {
        /** 发这一条（永远是当前最新的 fix）；`shiftId` 为 null 表示不带班次，后端按活跃段兜底 */
        data class Send(val fix: TrackFix, val shiftId: Long?) : Step()

        /** 这一拍不发。原因必须可见（进前台通知与工作台），"不发"也要说清为什么不发 */
        data class Hold(val reason: String) : Step()
    }

    /**
     * 等待间隔：正常一拍用配置值；连续失败按 2 倍退避，最多 [MAX_BACKOFF_MS]。
     * `baseSec` 越界一律夹回区间，不做"配置写错就崩"的假设。
     */
    fun intervalMs(baseSec: Int, failStreak: Int): Long {
        val base = baseSec.coerceIn(MIN_INTERVAL_SEC, MAX_INTERVAL_SEC) * 1_000L
        if (failStreak <= 0) return base
        return (base * (1L shl minOf(failStreak, MAX_SHIFT))).coerceAtMost(MAX_BACKOFF_MS)
    }

    fun decide(
        latest: TrackFix?,
        nowMs: Long,
        driverBound: Boolean,
        shiftId: Long,
    ): Step {
        if (!driverBound) {
            // Repo 的守卫会在请求前就抛，走到这里连 URL 都不该拼出来
            return Step.Hold("司机身份未绑定（该账号没有司机档案）")
        }
        if (latest == null) {
            return Step.Hold("等待定位信号（室内或弱信号时常见）")
        }
        val age = latest.ageMs(nowMs)
        if (age > MAX_FIX_AGE_MS) {
            return Step.Hold(
                "定位信号已中断 ${(age / 1000L).coerceAtLeast(1L)} 秒，暂停上报" +
                    "（不拿过期点冒充当前位置）",
            )
        }
        return Step.Send(latest, if (shiftId > 0L) shiftId else null)
    }

    /**
     * 班次归属说明。后端 `AppDriverLocationReqVO.shiftId` 是可选项：
     * 缺省时服务端用司机当前活跃段的班次兜底，兜不到就只更新车辆最新位置、不写班次轨迹。
     * 客户端**无从知道**服务端最终归到了哪一段，所以文案只能说"没带班次"，不能声称已归班或已丢轨迹。
     */
    fun shiftNote(shiftId: Long): String =
        if (shiftId > 0L) "" else "上报未带班次：车辆位置照常更新，班次归属由后端按当前活跃段判定"
}

/**
 * 上报台账：把"被顶替掉的点"和"最久多久没确认"变成**可见计数**。
 *
 * 之前是一次失败后 `last` 被新定位覆盖，丢了多少点谁也不知道（审计 §45 的"静默丢"）。
 * 这里仍然合并（不重放），但每次顶替都记账，界面能显示"已丢弃 N 个点"。
 */
class ReportLedger {

    /** 当前**待发（未被确认）的那一条** fix 的采集时刻，单调毫秒；0 = 没有欠账。
     * 合并语义下它恒等于最新点——更早的点已经被顶替，只能进 `superseded` 计数。 */
    var pendingFixAtMs: Long = 0L
        private set

    /** 被更新的点顶替、且从未被后端确认过的点数 */
    var superseded: Int = 0
        private set

    /** 后端确认过的点数（与 `Running.reported` 同向，这里只管台账） */
    var acked: Int = 0
        private set

    /** 采集到一条新 fix。同一个点重复回调不计数（按采集时刻判等）。 */
    fun onFix(elapsedRealtimeMs: Long) {
        if (elapsedRealtimeMs == pendingFixAtMs) return
        if (pendingFixAtMs != 0L) superseded += 1
        pendingFixAtMs = elapsedRealtimeMs
    }

    /** 后端确认成功：欠账清零 */
    fun onAck() {
        pendingFixAtMs = 0L
        acked += 1
    }

    /** 当前待发点已经攒了多久（毫秒）；没有欠账就是 0 */
    fun pendingAgeMs(nowMs: Long): Long =
        if (pendingFixAtMs == 0L) 0L else (nowMs - pendingFixAtMs).coerceAtLeast(0L)

    val hasUnacked: Boolean get() = pendingFixAtMs != 0L
}
