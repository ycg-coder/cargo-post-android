package com.shanxiang.buspost.core.navigation

import com.shanxiang.buspost.data.model.Geo
import com.shanxiang.buspost.data.model.MissionStop
import com.shanxiang.buspost.data.model.RouteGuide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 任务段站序的**同源**契约（`navigableStops` / `nextNavigableStop`）。
 *
 * 钉的是全任务段导航的地基：喂给高德的途经点列表，和界面显示的"下一站"，
 * 必须出自同一个过滤条件与同一个顺序。两者一旦分开，就会出现
 * 「高德按 A 序导、界面按 B 序念第 i/n 站」—— 那等于客户端替调度改了路线（硬规则 3）。
 */
class NavigationStopsTest {

    private fun stop(
        id: Long,
        seq: Int,
        status: Int = 0,
        lat: Double = 29.5,
        lng: Double = 106.5,
    ) = MissionStop(
        stationId = id,
        name = "站$id",
        geo = Geo(lat, lng),
        visitSequence = seq,
        status = status,
    )

    private fun guide(vararg stops: MissionStop) = RouteGuide(
        stops = stops.toList(),
        steps = emptyList(),
        polyline = emptyList(),
        stations = emptyList(),
        totalMeters = 0.0,
        etaMinutes = null,
        currentStep = 0,
        nextStation = "",
        nextStationDistanceMeters = 0,
        deviated = false,
        vehicleGeo = null,
        headingDegrees = 0.0,
        dataSource = "TEST",
    )

    @Test
    fun `下一站永远等于剩余站列表的第一个`() {
        val g = guide(stop(1, 1), stop(2, 2), stop(3, 3))
        val rest = navigableStops(g)
        assertEquals(3, rest.size)
        assertEquals(nextNavigableStop(g)?.stationId, rest.first().stationId)
    }

    @Test
    fun `顺序按后端 visitSequence，不按数组到达顺序`() {
        // 后端就算把第 3 站排在数组最前面，也不能改变"先去哪站"——那是调度的决定
        val g = guide(stop(30, 3), stop(10, 1), stop(20, 2))
        assertEquals(listOf(10L, 20L, 30L), navigableStops(g).map { it.stationId })
        assertEquals(10L, nextNavigableStop(g)?.stationId)
    }

    @Test
    fun `已完成与没坐标的站都不进列表`() {
        val g = guide(
            stop(1, 1, status = 7),                       // 已完成
            stop(2, 2, lat = 0.0, lng = 0.0),             // 后端没给坐标
            stop(3, 3),
        )
        assertEquals(listOf(3L), navigableStops(g).map { it.stationId })
    }

    @Test
    fun `失败态的站必须留着，不许客户端替调度跳掉`() {
        // status 8 = 失败：后端只说"这次操作没成"，没说"不用去那儿"。
        // 跳掉它就是客户端改路线；处理它走异常上报。
        val g = guide(stop(1, 1, status = 8), stop(2, 2))
        assertEquals(listOf(1L, 2L), navigableStops(g).map { it.stationId })
        assertEquals(1L, nextNavigableStop(g)?.stationId)
    }

    @Test
    fun `没有可去的站时两者一起为空，不凑一个目的地`() {
        val g = guide(stop(1, 1, status = 7))
        assertTrue(navigableStops(g).isEmpty())
        assertNull(nextNavigableStop(g))
        assertNull(nextNavigableStop(guide()))
    }
}
