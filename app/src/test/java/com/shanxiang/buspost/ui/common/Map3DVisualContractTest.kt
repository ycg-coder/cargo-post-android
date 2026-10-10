package com.shanxiang.buspost.ui.common

import com.shanxiang.buspost.data.model.Geo
import com.shanxiang.buspost.data.model.MissionStop
import com.shanxiang.buspost.data.model.RouteGuide
import com.shanxiang.buspost.driver.LocationReportPolicy
import com.shanxiang.buspost.ui.driver.MissionMarkers
import com.shanxiang.buspost.ui.driver.missionFootnote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ⑫⑬ 3D 视觉层的行为判据（纯 JVM，不需要高德、不需要 Robolectric）。
 *
 * 这一族最容易出的错不是"画得不好看"，而是**画出一个不存在的事实**：
 * 没有 bearing 时把车转成正北、把折线推断的朝向当成车辆朝向、点早过期了还显示成在线、
 * 以及高亮任务点时顺手改了它的状态色（那就是在动业务）。所以每条都在钉"不编造"。
 *
 * ⚠ 本类刻意**不**放进 `MissionRouteStateContractTest`：那本的判据 MR-a 钉的是"⑧ 那一族恰好 12 条用例"，
 * 把 ⑫⑬ 塞进去等于把两族的收口状态互相顶掉（§八 ㉛ 的分工规矩）。
 */
class Map3DVisualContractTest {

    private fun stop(seq: Int, stationId: Long, status: Int) = MissionStop(
        stationId = stationId,
        name = "站$seq",
        geo = Geo(30.0 + seq / 100.0, 106.0 + seq / 100.0),
        visitSequence = seq,
        actionType = 3,
        quantity = 2,
        status = status,
    )

    // ---------- 朝向：未给就是未给 ----------

    @Test
    fun `朝向未给时不落到正北`() {
        assertNull(Map3DVisual.rotateOf(null))
        val text = Map3DVisual.headingText(null)
        assertTrue(text, text.contains("未给"))
        assertFalse(text, text.contains("0°"))
    }

    @Test
    fun `朝向归一到 0 到 360 且不写出一个不存在的角`() {
        assertEquals(270.0, Map3DVisual.rotateOf(-90.0)!!.toDouble(), 1e-6)
        assertEquals(90.0, Map3DVisual.rotateOf(450.0)!!.toDouble(), 1e-6)
        assertEquals(0.0, Map3DVisual.rotateOf(360.0)!!.toDouble(), 1e-6)
        // 整句比对：`contains("0°")` 会被 "360°" 骗过（子串里就带着一个 "0°"），钉不住"不写出 360°"这句话
        assertEquals("朝向 0°（GPS）", Map3DVisual.headingText(359.6))
        assertEquals("朝向 90°（GPS）", Map3DVisual.headingText(90.4))
    }

    // ---------- 新鲜度：三态与停报阈值同源 ----------

    @Test
    fun `新鲜度三态用的是政策那个停报阈值`() {
        val limit = LocationReportPolicy.MAX_FIX_AGE_MS
        assertEquals(GpsFixState.UNAVAILABLE, Map3DVisual.gpsFixStateOf(hasFix = false, fixAgeMs = 0L))
        assertEquals(GpsFixState.LIVE, Map3DVisual.gpsFixStateOf(hasFix = true, fixAgeMs = limit))
        assertEquals(GpsFixState.STALE, Map3DVisual.gpsFixStateOf(hasFix = true, fixAgeMs = limit + 1L))
    }

    @Test
    fun `非 LIVE 时不把旧朝向带进画面`() {
        val visual = Map3DVisual.gpsFixVisual(
            hasFix = true,
            fixAgeMs = LocationReportPolicy.MAX_FIX_AGE_MS + 1L,
            holdReason = "",
            headingDegrees = 90.0,
        )
        assertEquals(GpsFixState.STALE, visual.state)
        assertNull(visual.headingDegrees)
    }

    @Test
    fun `一条 fix 都没有时也不说成后端没有位置`() {
        val note = Map3DVisual.gpsFixVisual(
            hasFix = false,
            fixAgeMs = 0L,
            holdReason = "",
            headingDegrees = null,
        ).note
        assertTrue(note, note.contains("任务点与车辆位置仍按后端下发"))
        assertFalse(note, note.contains("后端说"))
    }

    // ---------- 旋转归属：只有"我"能转 ----------

    @Test
    fun `车辆 marker 不因折线推断而旋转`() {
        val vehicle = Map3DVisual.marker3DOf(MapMarker.KIND_BUS_REAL, MarkerVisual(headingDegrees = 90.0))
        assertNull(vehicle.rotateAngle)
        assertTrue(vehicle.flat)
        assertEquals(0.0, vehicle.altitudeMeters.toDouble(), 1e-6)
    }

    @Test
    fun `只有我这格按 GPS 朝向旋转`() {
        assertEquals(
            90.0,
            Map3DVisual.marker3DOf(MapMarker.KIND_ME, MarkerVisual(headingDegrees = 90.0)).rotateAngle!!.toDouble(),
            1e-6,
        )
        assertNull(Map3DVisual.marker3DOf(MapMarker.KIND_ME, MarkerVisual()).rotateAngle)
        assertNull(Map3DVisual.marker3DOf(MapMarker.KIND_TASK_ACTIVE, MarkerVisual(headingDegrees = 90.0)).rotateAngle)
        assertNull(Map3DVisual.marker3DOf(MapMarker.KIND_STOP, MarkerVisual(headingDegrees = 90.0)).rotateAngle)
    }

    // ---------- 立体感：浮空只给任务点 ----------

    @Test
    fun `只有任务点浮空其余贴地`() {
        for (kind in listOf(
            MapMarker.KIND_TASK_TODO,
            MapMarker.KIND_TASK_ACTIVE,
            MapMarker.KIND_TASK_DONE,
            MapMarker.KIND_TASK_FAILED,
        )
        ) {
            val v = Map3DVisual.marker3DOf(kind, MarkerVisual())
            assertEquals(Map3DVisual.TASK_ALTITUDE_METERS.toDouble(), v.altitudeMeters.toDouble(), 1e-6)
            assertFalse(v.flat)
            assertTrue(v.perspective)
        }
        for (kind in listOf(MapMarker.KIND_START, MapMarker.KIND_STOP, MapMarker.KIND_ME, MapMarker.KIND_BUS_REAL)) {
            assertEquals(0.0, Map3DVisual.marker3DOf(kind, MarkerVisual()).altitudeMeters.toDouble(), 1e-6)
        }
    }

    @Test
    fun `当前任务压住下一任务且降级时我不冒充在线`() {
        val current = Map3DVisual.marker3DOf(MapMarker.KIND_TASK_ACTIVE, MarkerVisual(emphasis = MarkerEmphasis.CURRENT)).zIndex
        val next = Map3DVisual.marker3DOf(MapMarker.KIND_TASK_TODO, MarkerVisual(emphasis = MarkerEmphasis.NEXT)).zIndex
        val plain = Map3DVisual.marker3DOf(MapMarker.KIND_TASK_DONE, MarkerVisual()).zIndex
        val meOnline = Map3DVisual.marker3DOf(MapMarker.KIND_ME, MarkerVisual()).zIndex
        val vehicle = Map3DVisual.marker3DOf(MapMarker.KIND_BUS_REAL, MarkerVisual()).zIndex
        val meDegraded = Map3DVisual.marker3DOf(MapMarker.KIND_ME, MarkerVisual(degraded = true)).zIndex
        assertTrue("$current/$next", current > next)
        assertTrue("$next/$plain", next > plain)
        assertTrue("$meOnline/$current", meOnline > current)
        assertTrue("$vehicle/$meOnline", vehicle > current)
        assertTrue("$meDegraded/$plain", meDegraded < plain)
    }

    // ---------- 脚注：什么时候说话、说什么 ----------

    @Test
    fun `正常定位时脚注不多一句话`() {
        assertEquals("", Map3DVisual.footnoteOf(GpsFixState.LIVE, ""))
        // 策略因为"没绑司机"而停报时，定位这点其实是新的：地图脚注不该跟着谎报定位降级
        assertEquals(
            "任务点 0 个 · 顺序由调度下发 · 道路里程接口未给",
            missionFootnote(RouteGuide.EMPTY, Map3DVisual.footnoteOf(GpsFixState.LIVE, "司机身份未绑定（该账号没有司机档案）")),
        )
    }

    @Test
    fun `降级脚注转述服务原话而不是重写一句`() {
        val reason = "定位信号已中断 52 秒，暂停上报（不拿过期点冒充当前位置）"
        val note = Map3DVisual.footnoteOf(GpsFixState.STALE, reason)
        assertTrue(note, note.startsWith("定位降级："))
        assertTrue(note, note.contains(reason))
        assertTrue(missionFootnote(RouteGuide.EMPTY, note).endsWith(note))
    }

    @Test
    fun `定位不可用时说清仍按后端下发`() {
        val withReason = Map3DVisual.footnoteOf(GpsFixState.UNAVAILABLE, "等待定位信号（室内或弱信号时常见）")
        assertTrue(withReason, withReason.contains("等待定位信号"))
        assertTrue(withReason, withReason.contains("任务点与车辆位置仍按后端下发"))
        val fallback = Map3DVisual.footnoteOf(GpsFixState.UNAVAILABLE, "   ")
        assertEquals(Map3DVisual.NO_FIX_NOTE, fallback)
    }

    // ---------- 高亮：只跟后端状态，且不动业务 ----------

    @Test
    fun `当前任务跟随后端状态下一任务取其後第一个未完成`() {
        // 当前 = 后端那个进行中（1 行驶中）；它后面先撞见已完成（7）和失败（8）那两站，
        // 两站都不许被指成"下一个要去" —— 排除规则一旦摘掉，这一格就会先落在 7 那一站
        val markers = MissionMarkers.stopMarkers(
            listOf(stop(1, 101L, 2), stop(2, 102L, 7), stop(3, 103L, 8), stop(4, 104L, 0)),
            focusStopId = 0L,
        )
        assertEquals(MarkerEmphasis.CURRENT, markers[0].visual.emphasis)
        assertEquals(MarkerEmphasis.NONE, markers[1].visual.emphasis)
        assertEquals(MarkerEmphasis.NONE, markers[2].visual.emphasis)
        assertEquals(MarkerEmphasis.NEXT, markers[3].visual.emphasis)
    }

    @Test
    fun `没有后端进行中状态时才退回导航焦点`() {
        val stops = listOf(stop(1, 101L, 0), stop(2, 102L, 0))
        val focused = MissionMarkers.stopMarkers(stops, focusStopId = 102L)
        assertEquals(MarkerEmphasis.NEXT, focused[0].visual.emphasis)
        assertEquals(MarkerEmphasis.CURRENT, focused[1].visual.emphasis)
        // 全是已完成：没有下一任务可高亮，也不该硬凑一个
        val allDone = MissionMarkers.stopMarkers(listOf(stop(1, 101L, 7), stop(2, 102L, 7)), focusStopId = 0L)
        assertEquals(listOf(MarkerEmphasis.NONE, MarkerEmphasis.NONE), allDone.map { it.visual.emphasis })
    }

    @Test
    fun `高亮是视觉不改动任务状态与配色`() {
        val stops = listOf(stop(1, 101L, 0), stop(2, 102L, 2), stop(3, 103L, 7))
        val plain = MissionMarkers.stopMarkers(stops, focusStopId = 0L)
        val highlighted = MissionMarkers.stopMarkers(stops, focusStopId = 101L)
        assertEquals(plain.map { it.kind }, highlighted.map { it.kind })
        assertEquals(plain.map { it.title }, highlighted.map { it.title })
        assertEquals(plain.map { it.detail }, highlighted.map { it.detail })
        assertEquals(
            listOf(MapMarker.KIND_TASK_TODO, MapMarker.KIND_TASK_ACTIVE, MapMarker.KIND_TASK_DONE),
            highlighted.map { it.kind },
        )

        // 焦点落在"还没开始"那站时它就是 CURRENT —— 这一格本来不是进行中，
        // 高亮不许顺手把它染成 KIND_TASK_ACTIVE（那等于视觉层替后端推进了业务状态）
        val noActive = listOf(stop(1, 101L, 0), stop(2, 102L, 0), stop(3, 103L, 7))
        val focused = MissionMarkers.stopMarkers(noActive, focusStopId = 101L)
        assertEquals(
            listOf(MarkerEmphasis.CURRENT, MarkerEmphasis.NEXT, MarkerEmphasis.NONE),
            focused.map { it.visual.emphasis },
        )
        assertEquals(
            listOf(MapMarker.KIND_TASK_TODO, MapMarker.KIND_TASK_TODO, MapMarker.KIND_TASK_DONE),
            focused.map { it.kind },
        )
    }

    @Test
    fun `我这格的详情里就写明朝向有没有`() {
        val known = MissionMarkers.meMarker(Geo(30.1, 106.1), 45.0, false)!!
        assertTrue(known.detail, known.detail.contains("45°"))
        val unknown = MissionMarkers.meMarker(Geo(30.1, 106.1), null, true)!!
        assertTrue(unknown.detail, unknown.detail.contains("未给"))
        assertNull(MissionMarkers.meMarker(null))
        assertNull(MissionMarkers.meMarker(Geo(0.0, 0.0)))
    }
}
