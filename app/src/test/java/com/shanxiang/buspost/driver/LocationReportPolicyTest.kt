package com.shanxiang.buspost.driver

import com.shanxiang.buspost.core.loc.TrackFix
import com.shanxiang.buspost.data.model.Geo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 上报策略（P1-3 ⑦）：钉住三件"不会被代码推翻就看不见"的事——
 * ① 只发最新的点（合并），② 点过期就停报（不拿旧点冒充当前位置），③ 退避有界。
 *
 * 后端事实（写在这里，防止后人把"补报队列"当成改进）：
 * `AppDriverLocationReqVO` 无客户端时间字段，`DriverAppServiceImpl.reportLocation` 用
 * `LocalDateTime.now()` 给 `VehicleLocationDO` 与 `VehicleLocationTrackDO` 同时盖章，
 * 所以任何"把旧点晚点再发"的实现都会把轨迹时间写错。
 */
class LocationReportPolicyTest {

    private fun fix(atMs: Long, lat: Double = 29.53, lng: Double = 106.57) = TrackFix(
        geo = Geo(lat, lng),
        speedKmh = 38,
        accuracyMeters = 12.0,
        elapsedRealtimeMs = atMs,
        provider = "gps",
    )

    private val nowMs = 1_000_000L

    // ---------- 该不该发 ----------

    @Test
    fun `未绑定司机档案时不发并写明原因`() {
        val step = LocationReportPolicy.decide(fix(nowMs), nowMs, driverBound = false, shiftId = 66L)
        assertTrue(step is LocationReportPolicy.Step.Hold)
        assertTrue((step as LocationReportPolicy.Step.Hold).reason.contains("未绑定"))
    }

    @Test
    fun `还没有定位采样时不发`() {
        val step = LocationReportPolicy.decide(null, nowMs, driverBound = true, shiftId = 66L)
        assertTrue((step as LocationReportPolicy.Step.Hold).reason.contains("等待定位信号"))
    }

    @Test
    fun `点龄超过阈值就停报而不是把旧点当当前位置发出去`() {
        val staleAt = nowMs - LocationReportPolicy.MAX_FIX_AGE_MS - 1_000L
        val step = LocationReportPolicy.decide(fix(staleAt), nowMs, driverBound = true, shiftId = 66L)
        val reason = (step as LocationReportPolicy.Step.Hold).reason
        assertTrue("原因里必须带中断秒数，司机才知道不是网络问题：$reason", reason.contains("定位信号已中断"))
        assertTrue(reason.contains("${(nowMs - staleAt) / 1000L} 秒"))
    }

    @Test
    fun `阈值内照常上报`() {
        val at = nowMs - LocationReportPolicy.MAX_FIX_AGE_MS + 5_000L
        val step = LocationReportPolicy.decide(fix(at), nowMs, driverBound = true, shiftId = 66L)
        assertTrue(step is LocationReportPolicy.Step.Send)
        assertEquals(66L, (step as LocationReportPolicy.Step.Send).shiftId)
    }

    @Test
    fun `本机没有班次时照样上报但不带 shiftId（后端 P1-H 兜底活跃段）`() {
        // 旧行为是 shiftId<=0 就一句不发：运输段/算法派单司机的位置永远传不上去，
        // 村民端"车快到了"对这些单直接失效。
        val step = LocationReportPolicy.decide(fix(nowMs), nowMs, driverBound = true, shiftId = 0L)
        assertTrue(step is LocationReportPolicy.Step.Send)
        assertNull((step as LocationReportPolicy.Step.Send).shiftId)
    }

    @Test
    fun `失败后重试发的是最新的点而非旧点`() {
        val older = fix(nowMs - 20_000L, lat = 29.50)
        val newer = fix(nowMs - 2_000L, lat = 29.60)
        // 服务里 `latest` 只保留最新一条，策略拿到的永远是它：这就是"合并"，不是"排队重放"
        val step = LocationReportPolicy.decide(newer, nowMs, driverBound = true, shiftId = 66L) as LocationReportPolicy.Step.Send
        assertEquals(newer, step.fix)
        assertEquals(29.60, step.fix.geo.lat, 1e-9)
        assertTrue("旧点不得出现在报文里", step.fix.geo.lat != older.geo.lat)
    }

    // ---------- 间隔与退避 ----------

    @Test
    fun `正常一拍用配置值并夹在 3 到 60 秒内`() {
        assertEquals(10_000L, LocationReportPolicy.intervalMs(10, 0))
        assertEquals(3_000L, LocationReportPolicy.intervalMs(0, 0))
        assertEquals(60_000L, LocationReportPolicy.intervalMs(999, 0))
    }

    @Test
    fun `连续失败按 2 倍退避且不超过一分钟`() {
        assertEquals(20_000L, LocationReportPolicy.intervalMs(10, 1))
        assertEquals(40_000L, LocationReportPolicy.intervalMs(10, 2))
        // 10s × 8 = 80s > 上限 → 60s
        assertEquals(60_000L, LocationReportPolicy.intervalMs(10, 3))
        assertEquals(60_000L, LocationReportPolicy.intervalMs(10, 9))
        // 配置的底值本身已经到上限时不再放大
        assertEquals(60_000L, LocationReportPolicy.intervalMs(60, 2))
    }

    @Test
    fun `退避单调不减`() {
        var prev = 0L
        for (streak in 0..8) {
            val v = LocationReportPolicy.intervalMs(5, streak)
            assertTrue("streak=$streak interval=$v", v >= prev)
            prev = v
        }
    }

    // ---------- 班次说明 ----------

    @Test
    fun `有班次时不需要额外说明`() {
        assertEquals("", LocationReportPolicy.shiftNote(66L))
    }

    @Test
    fun `无班次的说明不得声称已经归到某个班次`() {
        val note = LocationReportPolicy.shiftNote(0L)
        assertTrue(note.contains("未带班次"))
        assertTrue("归班由后端判定，客户端不能替它下结论", note.contains("后端"))
    }

    // ---------- 台账：丢点必须可见 ----------

    @Test
    fun `首个未确认点不计数，被顶替的才计`() {
        val ledger = ReportLedger()
        ledger.onFix(100L)
        assertEquals(0, ledger.superseded)
        assertTrue(ledger.hasUnacked)
        ledger.onFix(200L)
        assertEquals(1, ledger.superseded)
        ledger.onFix(300L)
        assertEquals(2, ledger.superseded)
        // 待发点是最新那一条：更早的已经进了丢弃计数，不存在"排队等重放"
        assertEquals(300L, ledger.pendingFixAtMs)
    }

    @Test
    fun `确认后欠账清零`() {
        val ledger = ReportLedger()
        ledger.onFix(100L)
        ledger.onFix(200L)
        ledger.onAck()
        assertEquals(0L, ledger.pendingFixAtMs)
        assertEquals(1, ledger.acked)
        assertEquals(0L, ledger.pendingAgeMs(9_000L))
        // 确认之后的新点重新开始欠账，不再算顶替
        ledger.onFix(300L)
        assertEquals(1, ledger.superseded)
        assertEquals(300L, ledger.pendingFixAtMs)
    }

    @Test
    fun `同一条采样重复回调不重复计数`() {
        val ledger = ReportLedger()
        ledger.onFix(100L)
        ledger.onFix(100L)
        ledger.onFix(100L)
        assertEquals(0, ledger.superseded)
        assertEquals(100L, ledger.pendingFixAtMs)
    }

    @Test
    fun `欠账时长按单调时钟计算且不为负`() {
        val ledger = ReportLedger()
        ledger.onFix(500L)
        assertEquals(300L, ledger.pendingAgeMs(800L))
        assertEquals("时钟回拨不得给出负年龄", 0L, ledger.pendingAgeMs(400L))
    }
}
