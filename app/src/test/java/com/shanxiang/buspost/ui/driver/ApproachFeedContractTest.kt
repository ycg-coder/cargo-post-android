package com.shanxiang.buspost.ui.driver

import com.shanxiang.buspost.data.model.MissionStop
import com.shanxiang.buspost.data.model.RouteGuide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 到近播报"谁来配这一对"的契约（L-1 的第二半）。
 *
 * 三件事各钉一头：
 * · **里程与任务点同源**（[ApproachFeed] 的整段理由）：后端那段 `nextStopRoadDistanceMeters` 只能配
 *   后端自己点名的 `nextStopName`；本机引擎那段只能配本机算路时的那个目的地；
 * · **客户端自选的下一个点不许配后端里程**：`nextNavigableStop` 额外要求 `hasGeo`，
 *   后端的 next 不看坐标，两边在"下一站没坐标"时必然错开 —— 一说一配就是串站；
 * · **直线值不进这一层**：`nextStopDistanceMeters` 是后端 Haversine 旧字段（Models.kt 已注明禁止读它做里程），
 *   这里给它一个非零值，要求结论仍然是"静默"而不是"配对成功"（审计 §32）。
 */
class ApproachFeedContractTest {

    private fun guide(
        stops: List<MissionStop> = emptyList(),
        nextStopName: String = "",
        nextActionName: String = "",
        roadMeters: Int = 0,
        legacyStraightMeters: Int = 0,
    ) = RouteGuide.EMPTY.copy(
        stops = stops,
        nextStopName = nextStopName,
        nextActionName = nextActionName,
        nextStopRoadDistanceMeters = roadMeters,
        nextStationDistanceMeters = legacyStraightMeters,
    )

    private fun stop(
        id: Long = 7,
        name: String = "石马河站",
        status: Int = 1,
        actionName: String = "派送",
        quantity: Int = 6,
    ) = MissionStop(
        stationId = id,
        name = name,
        status = status,
        actionType = 3,
        actionName = actionName,
        quantity = quantity,
    )

    @Test
    fun `引擎剩余里程只配本机正在导的那个点，口径是本机道路`() {
        val s = stop()
        val frame = ApproachFeed.of(guide(stops = listOf(s)), navigatingStopId = 7L, engineRemainMeters = 120)
        assertTrue(frame is ApproachFrame.Ready)
        frame as ApproachFrame.Ready
        assertEquals(s, frame.stop)
        assertEquals(120.0, frame.meters, 0.0)
        assertEquals(DistanceSource.ENGINE_ROAD, frame.source)
    }

    /** 后端口径与引擎口径同时可用时取实时那一支，但两支不能说成同一支 */
    @Test
    fun `两支都有时引擎优先，后端口径不会盖掉实时里程`() {
        val s = stop()
        val g = guide(stops = listOf(s), nextStopName = "石马河站", roadMeters = 900)
        val frame = ApproachFeed.of(g, navigatingStopId = 7L, engineRemainMeters = 120) as ApproachFrame.Ready
        assertEquals(DistanceSource.ENGINE_ROAD, frame.source)
        assertEquals(120.0, frame.meters, 0.0)
    }

    /** 本机导航的目的地如果不在后端 stops 里，那段剩余里程就配不到任何点：宁可退后端口径也不硬配 */
    @Test
    fun `导航目的地不在后端任务点里时不配对，也不编出一个点`() {
        val silent = ApproachFeed.fromEngine(guide(stops = listOf(stop(id = 9))), navigatingStopId = 7L, engineRemainMeters = 120)
        assertTrue(silent is ApproachFrame.Silent)
        assertTrue((silent as ApproachFrame.Silent).reason.contains("配不到点"))
        // 后端也没点名 ⇒ 整帧静默，屏幕上留下的原因是"后端没给"，不是"高德没给"
        assertEquals(ApproachFrame.Silent("后端没给下一任务点，也没有可配的道路里程"), ApproachFeed.of(guide(), 7L, 120))
    }

    /** 高德这一帧没回剩余里程（0 与负数同样算没给）：退到后端口径，而不是念一个 0 米 */
    @Test
    fun `高德没给剩余里程就退后端口径，两支都没给才静默`() {
        val s = stop()
        val g = guide(stops = listOf(s), nextStopName = "石马河站", roadMeters = 400)
        for (meters in listOf(null, 0, -50)) {
            val frame = ApproachFeed.of(g, navigatingStopId = 7L, engineRemainMeters = meters) as ApproachFrame.Ready
            assertEquals("meters=$meters", DistanceSource.BACKEND_ROAD, frame.source)
            assertEquals(400.0, frame.meters, 0.0)
            assertEquals(
                ApproachFrame.Silent("高德这一帧没给剩余里程"),
                ApproachFeed.fromEngine(g, 7L, meters),
            )
        }
        assertEquals(
            ApproachFrame.Silent("后端没给下一任务点，也没有可配的道路里程"),
            ApproachFeed.of(guide(), 7L, 0),
        )
    }

    /** 没在导任何点时只走后端口径：这一支完全由后端点名的那个 next 说话 */
    @Test
    fun `不在导航中也走后端口径，配对由后端说`() {
        val s = stop()
        val frame = ApproachFeed.of(guide(listOf(s), "石马河站", "派送", 400), navigatingStopId = null, engineRemainMeters = 120) as ApproachFrame.Ready
        assertEquals(DistanceSource.BACKEND_ROAD, frame.source)
        assertEquals(s, frame.stop)
    }

    @Test
    fun `后端点名的站名能对上的未完成任务点时动作与件数一起带上`() {
        val s = stop()
        val frame = ApproachFeed.of(guide(listOf(s), "石马河站", "派送", 400), null, null) as ApproachFrame.Ready
        assertEquals(7L, frame.stop.stationId)
        val job = with(MissionMarkers) { "${frame.stop.actionLabel} ${frame.stop.quantityText()}" }
        assertEquals("派送 6 件", job.trim())
    }

    /** 只有已到站（status=7）的同名点时不算对上：后端点名的那个点已经完成，不能再拿它做目的地 */
    @Test
    fun `同名但已完成的点不被当成后端点名的那个点`() {
        val done = stop(status = 7)
        val frame = ApproachFeed.of(guide(listOf(done), "石马河站", "派送", 400), null, null) as ApproachFrame.Ready
        assertEquals(0L, frame.stop.stationId)
        assertEquals("石马河站", frame.stop.name)
        assertEquals("派送", frame.stop.actionName)
        // 后端没给件数就是没给：合成点上的件数只能是 0，播报因此不会念出「派送 0 件」
        assertEquals(0, frame.stop.quantity)
        assertNotEquals(done, frame.stop)
    }

    @Test
    fun `后端点名了但这一段没道路里程时静默，理由点名是哪一段缺`() {
        val silent = ApproachFeed.of(guide(listOf(stop()), "石马河站", "派送", roadMeters = 0), null, null)
        assertEquals(ApproachFrame.Silent("后端点名了「石马河站」，但这一段没有真实道路里程"), silent)
    }

    /**
     * §32 的那一半在这里的落点：旧字段 `nextStopDistanceMeters`（后端 Haversine 直线值）给了数也不许变成一帧 Ready。
     * 这一条同时是"后端没点名"的静默理由用例 —— 两个红点共用一条线，注入成读直线值就会两条一起红。
     */
    @Test
    fun `直线旧字段给再大也不配对，静默理由也不能把它当里程`() {
        val frame = ApproachFeed.of(guide(legacyStraightMeters = 888), null, null)
        assertTrue(frame is ApproachFrame.Silent)
        val reason = (frame as ApproachFrame.Silent).reason
        assertFalse("直线值漏进了静默理由：$reason", reason.contains("888"))
        assertEquals("后端没给下一任务点，也没有可配的道路里程", reason)
    }

    /** 后端只给了动作话术、没点名任务点：屏上那句要说"没点名"，不能替后端点一个站 */
    @Test
    fun `只有动作名没有站名时说不清去哪儿`() {
        assertEquals(
            ApproachFrame.Silent("后端说下一任务是「完成订单」，但没点名是哪个任务点"),
            ApproachFeed.of(guide(nextActionName = "完成订单", roadMeters = 500), null, null),
        )
    }

    /** 这一层只可能产出两种道路口径：直线与"无来源"永远不该从这里出去 */
    @Test
    fun `这一层产出的口径只有两种道路值`() {
        val frames = listOf(
            ApproachFeed.of(guide(listOf(stop()), "石马河站", "派送", 400), null, null),
            ApproachFeed.of(guide(listOf(stop())), 7L, 120),
            ApproachFeed.of(guide(listOf(stop(status = 1)), "双碑站", "揽收", 300), null, null),
        )
        for (frame in frames) {
            assertTrue(frame is ApproachFrame.Ready)
            val source = (frame as ApproachFrame.Ready).source
            assertTrue("不该出现的口径：$source", source == DistanceSource.BACKEND_ROAD || source == DistanceSource.ENGINE_ROAD)
            assertNotEquals(DistanceSource.STRAIGHT, source)
            assertNotEquals(DistanceSource.NONE, source)
        }
    }
}
