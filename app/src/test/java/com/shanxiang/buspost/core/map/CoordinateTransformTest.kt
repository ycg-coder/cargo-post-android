package com.shanxiang.buspost.core.map

import com.shanxiang.buspost.data.model.Geo
import com.shanxiang.buspost.data.model.GeoMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 坐标系换算（P1-3 ⑦ 把它从死代码变成唯一出口，所以它自己必须先被钉住）。
 *
 * 数值口径来自本机实测（同一套 Krasovsky 公式），不是从文档抄来的"参考值"：
 * 重庆主城 (29.5325, 106.5765) WGS-84 → GCJ-02 = (29.529603, 106.580187)，偏移 **480.6 米**。
 * 断言只用区间与不变量（往返一致、确定性、境外不动），避免把某个常数当成权威。
 */
class CoordinateTransformTest {

    private val cq = Geo(29.5325, 106.5765)
    private val tokyo = Geo(35.68, 139.69)

    @Test
    fun `境内点确实被偏移且量级符合公开算法`() {
        val gcj = CoordinateTransform.wgs84ToGcj02(cq)
        assertNotEquals(cq, gcj)
        val shift = GeoMath.distance(cq, gcj)
        assertTrue("偏移 $shift 米，应落在 100~900 米之间", shift in 100.0..900.0)
    }

    @Test
    fun `换算给出实测的那一个点`() {
        val gcj = CoordinateTransform.wgs84ToGcj02(cq)
        assertEquals(29.529603, gcj.lat, 1e-5)
        assertEquals(106.580187, gcj.lng, 1e-5)
    }

    @Test
    fun `WGS 与 GCJ 往返误差不超过一厘米`() {
        val back = CoordinateTransform.gcj02ToWgs84(CoordinateTransform.wgs84ToGcj02(cq))
        assertEquals(cq.lat, back.lat, 1e-9)
        assertEquals(cq.lng, back.lng, 1e-9)
    }

    @Test
    fun `换算确定：同一输入两次结果完全一致`() {
        assertEquals(CoordinateTransform.wgs84ToGcj02(cq), CoordinateTransform.wgs84ToGcj02(cq))
    }

    @Test
    fun `BD09 与 GCJ02 互为反算（本轮补上百度漏掉的两个偏移项）`() {
        // 补之前往返差 6.5e-3 度（约 700 米），补后应在 1e-5 度以内
        val bd = CoordinateTransform.gcj02ToBd09(cq)
        val back = CoordinateTransform.bd09ToGcj02(bd)
        assertEquals(cq.lat, back.lat, 1e-5)
        assertEquals(cq.lng, back.lng, 1e-5)
    }

    @Test
    fun `境外点一律不偏移`() {
        assertEquals(tokyo, CoordinateTransform.convert(tokyo, CoordinateSystem.WGS84, CoordinateSystem.GCJ02))
        assertTrue(!CoordinateTransform.inChina(tokyo))
    }

    @Test
    fun `已知限制：国境判断是矩形，东南亚点也会被偏移`() {
        // 公开算法用的就是这个外包矩形（经 72.004~137.8347、纬 0.8293~55.8271），
        // 中南半岛落在里面。本 App 只在重庆与周边跑，不构成实际问题；
        // 这条断言的意义是把这个限制钉住——将来收窄判断必须是刻意的改动，不能顺手改。
        val sea = Geo(15.0, 105.0)
        assertTrue(CoordinateTransform.inChina(sea))
        assertNotEquals(sea, CoordinateTransform.convert(sea, CoordinateSystem.WGS84, CoordinateSystem.GCJ02))
    }

    @Test
    fun `同系换算原样返回`() {
        assertEquals(cq, CoordinateTransform.convert(cq, CoordinateSystem.GCJ02, CoordinateSystem.GCJ02))
    }

    @Test
    fun `convertAll 保序等长并逐项与单点换算一致`() {
        val pts = listOf(cq, Geo(30.75, 106.05), Geo(31.95, 108.32))
        val all = CoordinateTransform.convertAll(pts, CoordinateSystem.WGS84, CoordinateSystem.GCJ02)
        assertEquals(pts.size, all.size)
        pts.forEachIndexed { i, p ->
            assertEquals(CoordinateTransform.wgs84ToGcj02(p), all[i])
        }
    }

    @Test
    fun `坐标系标签解析覆盖后端与引擎的真实写法`() {
        assertEquals(CoordinateSystem.GCJ02, CoordinateSystem.parse("GCJ-02"))
        assertEquals(CoordinateSystem.GCJ02, CoordinateSystem.parse("gcj02"))
        assertEquals(CoordinateSystem.GCJ02, CoordinateSystem.parse("AMAP"))
        assertEquals(CoordinateSystem.WGS84, CoordinateSystem.parse("WGS-84"))
        assertEquals(CoordinateSystem.WGS84, CoordinateSystem.parse("OSM"))
        assertEquals(CoordinateSystem.BD09, CoordinateSystem.parse("BD-09"))
        // 未知与缺失回落 GCJ-02：后端契约（AppDriverRouteRespVO）声明的就是 GCJ-02
        assertEquals(CoordinateSystem.GCJ02, CoordinateSystem.parse(null))
        assertEquals(CoordinateSystem.GCJ02, CoordinateSystem.parse(""))
        assertEquals(CoordinateSystem.GCJ02, CoordinateSystem.parse("whatever"))
    }

    @Test
    fun `来源声明 WGS84 的折线画到底图前必须换算，但原始点保持不变`() {
        val raw = listOf(Geo(29.5325, 106.5765), Geo(29.54, 106.58))
        val route = RealRoadRoute(RouteSource.OSM_GRAPHHOPPER, CoordinateSystem.WGS84, raw, 3_000, 600)
        val drawn = route.gcj02Points
        assertEquals(raw.size, drawn.size)
        assertTrue("每条点都被偏移", drawn.zip(raw).all { (a, b) -> a != b })
        assertTrue("偏移量级在几百米", GeoMath.distance(raw[0], drawn[0]) in 100.0..900.0)
        // points 是"后端给了什么"的证据，换算只发生在展示层视图里，不得就地改来源数据
        assertEquals(raw, route.points)
    }

    @Test
    fun `来源声明 GCJ02 的折线不得二次偏移`() {
        val raw = listOf(Geo(29.5325, 106.5765), Geo(29.54, 106.58))
        val route = RealRoadRoute(RouteSource.AMAP, CoordinateSystem.GCJ02, raw, 3_000, 600)
        assertEquals(raw, route.gcj02Points)
    }
}
