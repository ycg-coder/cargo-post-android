package com.shanxiang.buspost.core.navigation

import com.shanxiang.buspost.data.model.Geo
import com.shanxiang.buspost.data.model.MissionStop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 全任务段导航的计划契约（`navPlanOfStops`）。
 *
 * 钉的四格：
 * ① 终点 = 后端 `visitSequence` 的**最后一站**，途经点 = 其余各站且顺序不变（顺序只来自调度）；
 * ② 途经点数永远 = 剩余站数 − 1，也就是**一个都不许被摘掉**；
 * ③ 超出高德上限时**退回逐站**（终点变成最近那站），而不是带着被裁过的途经点继续全段 ——
 *    被摘掉的那几站高德根本不会绕，等于客户端替司机跳站（每站必停是业务底线，
 *    后端 `arrive` 那侧也会以 `DRIVER_STATION_ORDER_ILLEGAL` 拒跳站）；
 * ④ 没有站表时返回 null，调用方必须说"调度还没给点"，不许凑一个目的地。
 */
class NavigationPlanTest {

    private fun stop(id: Long, seq: Int, status: Int = 0, lat: Double = 29.5, lng: Double = 106.5) =
        MissionStop(
            stationId = id,
            name = "站$id",
            geo = Geo(lat, lng),
            visitSequence = seq,
            status = status,
        )

    private fun planOf(vararg stops: MissionStop) = navPlanOfStops(stops.toList())

    @Test
    fun `三站时终点是最后一站，前两站按调度顺序当途经点`() {
        val p = planOf(stop(10, 1), stop(20, 2), stop(30, 3))!!
        assertEquals(30L, p.destination.stationId)
        assertEquals(listOf(10L, 20L), p.wayPoints.map { it.stationId })
        assertEquals(false, p.legByLeg)
        assertEquals("", p.note)
    }

    @Test
    fun `途经点顺序跟 visitSequence，不跟数组到达顺序`() {
        // 后端把第 3 站排在数组最前面，也不能改变"先经过谁"
        val p = planOf(stop(30, 3), stop(10, 1), stop(20, 2))!!
        assertEquals(listOf(10L, 20L), p.wayPoints.map { it.stationId })
        assertEquals(30L, p.destination.stationId)
    }

    @Test
    fun `途经点数恒等于剩余站数减一，一个都不许被摘掉`() {
        val p = planOf(stop(1, 1), stop(2, 2), stop(3, 3), stop(4, 4))!!
        assertEquals(p.stops.size - 1, p.wayPoints.size)
        assertEquals(listOf(1L, 2L, 3L), p.wayPoints.map { it.stationId })
        assertEquals(4L, p.destination.stationId)
    }

    @Test
    fun `只剩一站时没有途经点，仍然算全段模式`() {
        val p = planOf(stop(7, 2, status = 7), stop(8, 3))!!
        assertEquals(8L, p.destination.stationId)
        assertTrue(p.wayPoints.isEmpty())
        assertEquals(false, p.legByLeg)
    }

    @Test
    fun `超出高德途经点上限时退回逐站，而不是裁掉中间站继续全段`() {
        // 上限 + 终点 + 一个多余的中间站：如果实现选择"摘掉最后一个中间站"，
        // 那一站高德永远不会绕 —— 这一条用例就是钉住它不许那样。
        val many = (1..(MAX_AMAP_WAY_POINTS + 2)).map { stop(it.toLong(), it) }
        val p = navPlanOfStops(many)!!
        assertTrue("超上限必须标记成逐站", p.legByLeg)
        assertTrue("逐站模式下不许带任何途经点", p.wayPoints.isEmpty())
        assertEquals(1L, p.destination.stationId)
        assertTrue("退回逐站必须说要话，不能静默换口径", p.note.isNotEmpty())
    }

    @Test
    fun `正好在上限内不算超限`() {
        // 上限 16 个途经点 + 终点 = 17 站：这一格是边界，差一站就该整体换口径，必须钉住
        val exact = (1..(MAX_AMAP_WAY_POINTS + 1)).map { stop(it.toLong(), it) }
        val p = navPlanOfStops(exact)!!
        assertEquals(false, p.legByLeg)
        assertEquals(MAX_AMAP_WAY_POINTS, p.wayPoints.size)
    }

    @Test
    fun `没有坐标的站既不进途经点也不当终点`() {
        val p = planOf(stop(1, 1), stop(2, 2, lat = 0.0, lng = 0.0), stop(3, 3))!!
        assertEquals(listOf(1L, 3L), p.stops.map { it.stationId })
        assertEquals(listOf(1L), p.wayPoints.map { it.stationId })
        assertEquals(3L, p.destination.stationId)
    }

    @Test
    fun `失败态的站仍然是要去的一站`() {
        val p = planOf(stop(1, 1, status = 8), stop(2, 2))!!
        assertEquals(listOf(1L, 2L), p.stops.map { it.stationId })
        assertEquals(1L, p.wayPoints.first().stationId)
    }

    @Test
    fun `全完成或空站表都不给计划`() {
        assertNull(planOf())
        assertNull(planOf(stop(1, 1, status = 7), stop(2, 2, status = 7)))
    }

    @Test
    fun `途经点数变化会换指纹，即使几何与终点都没变`() {
        // §九：确认到站之后站表变短、途经点数就变，旧 session 必须作废。
        // 两次算路终点/点数/里程完全相同是可能发生的（同一趟里重新算同一段），
        // 所以指纹要能仅凭途经点数就把它们分开。
        val base = AcceptedRouteSession(
            destinationLat = 29.5, destinationLng = 106.5, coordCount = 120, roadDistanceMeters = 8000,
        )
        val withWayPoints = base.copy(wayPointCount = 3)
        assertEquals(0, base.wayPointCount)
        assertTrue(
            "途经点数没进指纹：${base.routeFingerprint}",
            base.routeFingerprint != withWayPoints.routeFingerprint,
        )
    }
}
