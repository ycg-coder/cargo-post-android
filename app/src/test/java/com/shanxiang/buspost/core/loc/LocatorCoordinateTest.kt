package com.shanxiang.buspost.core.loc

import com.shanxiang.buspost.core.map.CoordinateSystem
import com.shanxiang.buspost.core.map.CoordinateTransform
import com.shanxiang.buspost.data.model.Geo
import com.shanxiang.buspost.data.model.GeoMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 定位出口坐标系（审计 §17 的客户端一半）。
 *
 * 这里能测的是"纯换算部分"：`Locator.toGcj02` 不碰 Android 类型，
 * 所以可以在 JVM 里断言"业务拿到的 Geo 一定是 GCJ-02"。
 * `Location` → `TrackFix` 的那一段（`fixOf`）需要真实 `android.location.Location`，
 * 工程没有 Robolectric，属 §57 的真机项，**不在这里冒充已验证**。
 */
class LocatorCoordinateTest {

    private val cqWgs = Geo(29.5325, 106.5765)

    @Test
    fun `Locator 出口给的是 GCJ-02 而不是设备原始坐标`() {
        val out = Locator.toGcj02(cqWgs)
        assertNotEquals("原始 WGS-84 不得直接进业务", cqWgs, out)
        val shift = GeoMath.distance(cqWgs, out)
        assertTrue("偏移 $shift 米", shift in 100.0..900.0)
    }

    @Test
    fun `换算只走 CoordinateTransform 这一个入口`() {
        assertEquals(
            CoordinateTransform.convert(cqWgs, CoordinateSystem.WGS84, CoordinateSystem.GCJ02),
            Locator.toGcj02(cqWgs),
        )
    }

    @Test
    fun `境外点原样透传，不因为换算而跳走`() {
        val tokyo = Geo(35.68, 139.69)
        assertEquals(tokyo, Locator.toGcj02(tokyo))
    }

    @Test
    fun `采样自带精度、来源与采集时刻`() {
        val fix = TrackFix(Geo(29.53, 106.57), 42, 12.5, 1_000L, "gps")
        assertEquals(42, fix.speedKmh)
        assertEquals(12.5, fix.accuracyMeters, 1e-9)
        assertEquals("gps", fix.provider)
    }

    @Test
    fun `点龄用单调时钟且时钟回拨给出 0 而不是负数`() {
        val fix = TrackFix(Geo(29.53, 106.57), 0, 0.0, 1_000L, "gps")
        assertEquals(500L, fix.ageMs(1_500L))
        assertEquals(0L, fix.ageMs(900L))
        assertEquals(0L, fix.ageMs(1_000L))
    }

    @Test
    fun `LocFix 的四态判定不受换算改动影响`() {
        // 页面靠这四态区分"权限被拒 / 定位不可用 / 精度差 / 正常"，
        // 换算改动若把 available 或精度门槛带偏，这里会先红
        val denied = LocFix.DENIED
        assertTrue(denied.denied)
        assertTrue(!denied.available)
        val unavailable = LocFix.UNAVAILABLE
        assertTrue(!unavailable.available && !unavailable.denied)
        val poor = LocFix(Geo(29.53, 106.57), 120.0)
        assertTrue(poor.poorAccuracy)
        assertTrue(!poor.veryPoorAccuracy)
        assertTrue(LocFix(Geo(29.53, 106.57), 600.0).veryPoorAccuracy)
        assertEquals("120m", poor.accuracyText)
        assertEquals("1km", LocFix(Geo(29.53, 106.57), 1_200.0).accuracyText)
    }
}
