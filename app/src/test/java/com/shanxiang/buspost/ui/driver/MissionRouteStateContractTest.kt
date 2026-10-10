package com.shanxiang.buspost.ui.driver

import com.shanxiang.buspost.ServerTest
import com.shanxiang.buspost.TestHost
import com.shanxiang.buspost.awaitUntil
import com.shanxiang.buspost.core.map.PolylinePhase
import com.shanxiang.buspost.data.model.Role
import com.shanxiang.buspost.data.store.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 任务地图"无路线"那一支的说话契约（F-3 ⑧ / 队列 #148）。
 *
 * 两半各钉一头，缺一半都会漏：
 * · **纯函数那一半**钉"同一个问题只有五格说法、而且没有一格凭空说『后端没有下发』"；
 * · **VM 那一半**钉"相位与失败原因真的跟着那条请求走"—— 工程无 Robolectric，屏幕渲染不出来，
 *   但 `routePhase`/`routeFailure` 是页面唯一的输入，它们错了页面文案必然错（与 SA-e3 同一口径）。
 *
 * 收口前的真实形状（本轮实测，不是推测）：`/driver/route` 失败时 `Repo.routeGuide()` 回
 * `One(RouteGuide.EMPTY, error = …)`（`Repo.kt:1091-1098`），而 EMPTY 的 `routeUnavailableReason` 是 null，
 * 旧页面因此只有两条出口：要么整块地图静默消失（旧注释还写着"后端确实没给，不是加载失败"），
 * 要么由 `DriverMissionMap` / `MapCanvas` 各自自带那句"后端没有下发这一班的真实道路折线"——
 * 那是把**自己没问到**说成**后端说过没有**，与 F-2 ⑧ 登记、F-3 ⑤ 收口的是同一族假话。
 * ⚠ 本轮顺带实测出一个**比"两者同形"更精确**的事实：正式链路里 EMPTY 的唯一生产者就是那条 catch 支，
 * 后端真说"没有可用道路"时必然带非空原因（`routeGuideOf` 的 `Rejected` / `routeAvailable=false` 两支都给原因），
 * 所以下面两条对照用例（失败支 reason=null、成功支 reason 非空）钉的是**这一轮新立的那半边证据**。
 */
class MissionRouteStateContractTest : ServerTest() {

    @Before
    fun bindMainDispatcher() {
        TestHost.installMainDispatcher()
    }

    private val phases = listOf(PolylinePhase.NotAsked, PolylinePhase.Asking, PolylinePhase.Asked)

    // ---------- 说什么：五格说法、优先级、不许凭空替后端下结论 ----------

    /** 12 种输入组合只可能落到 5 格：多出来的一格就是第二份真相，少了就是又收成布尔 */
    @Test
    fun `无路线说法恰好五格`() {
        val cells = mutableSetOf<String>()
        for (phase in phases) {
            for (failure in listOf(null, "服务不可用")) {
                for (roadReason in listOf(null, "后端没有可用的真实道路路线")) {
                    cells += MissionMarkers.missionRouteReasonOf(phase, failure, roadReason)
                }
            }
        }
        assertEquals("三相位 × 有无失败 × 有无后端原因 = 最多 5 格说法", 5, cells.size)
    }

    /** 红线：没有一格有权说"后端没有下发"——那句话需要后端自己的原因键作证据 */
    @Test
    fun `任何一格都不许说后端没有下发`() {
        for (phase in phases) {
            for (failure in listOf(null, "超时")) {
                for (roadReason in listOf(null, "第 2 段暂无真实道路")) {
                    val text = MissionMarkers.missionRouteReasonOf(phase, failure, roadReason)
                    assertFalse("[$phase/$failure/$roadReason] 又把没问到说成没有：$text", text.contains("后端没有下发"))
                    assertTrue("每一格都要拒绝画示意线：$text", text.contains("示意线"))
                }
            }
        }
    }

    @Test
    fun `在飞那一格优先于失败原因`() {
        // 请求还在飞时，上一轮留下的失败原因必须被压住，否则屏幕会同时说"正在要"和"这次没问到"
        val asking = MissionMarkers.missionRouteReasonOf(PolylinePhase.Asking, "服务不可用", "后端的原因")
        assertTrue(asking, asking.contains("正在向后端要这一班的路线"))
        assertFalse(asking, asking.contains("这次没问到"))
    }

    @Test
    fun `没问过那一格优先于失败原因并明说不是后端的结论`() {
        val never = MissionMarkers.missionRouteReasonOf(PolylinePhase.NotAsked, "服务不可用", null)
        assertTrue(never, never.contains("还没去问过"))
        assertTrue(never, never.contains("不是后端说没有路线"))
        assertFalse(never, never.contains("这次没问到"))
    }

    @Test
    fun `这次没问到要带上原因但不升格成后端的结论`() {
        val missed = MissionMarkers.missionRouteReasonOf(PolylinePhase.Asked, "Unable to resolve host", null)
        assertTrue(missed, missed.contains("这次没问到：Unable to resolve host"))
        assertTrue(missed, missed.contains("这不是后端说没有路线"))
    }

    /** 只有后端/校验器真的给了原因，页面才转述它，而且转述原文、不另编一句 */
    @Test
    fun `后端给的原因原样转述`() {
        val roadReason = "第 2 段（共 3 段）暂无真实道路，折线已在此截断"
        val text = MissionMarkers.missionRouteReasonOf(PolylinePhase.Asked, null, roadReason)
        assertTrue(text, text.startsWith(roadReason))
        assertTrue(text, text.contains("任务点的位置与顺序仍按后端下发"))
    }

    /** 问过、没失败、后端也没给原因 = 本机手里没有任何结论，兜底那格谁都不指责 */
    @Test
    fun `无证据兜底不指责任何一方`() {
        val text = MissionMarkers.missionRouteReasonOf(PolylinePhase.Asked, null, null)
        assertTrue(text, text.contains("没有证据表明后端说过它有"))
        assertFalse(text, text.contains("后端没有下发"))
    }

    // ---------- 相位与失败原因跟着那一条请求走（MockWebServer） ----------

    private fun driverResidue() {
        Prefs.token = "tk-driver"
        Prefs.role = Role.DRIVER
        Prefs.driverId = 61L
    }

    /** profile / shifts / pickups / tasks 四条按序回成功且空，把判据干净留给第 5 条 route */
    private fun enqueueFirstFour() {
        respondData("""{"driverId":61,"name":"王有德","plateNo":"渝A·8K231"}""")
        respondData("[]")
        respondData("[]")
        respondData("[]")
    }

    /**
     * 本轮买的东西在这里落地：route 失败**不再等于"什么都没有"**。
     * 相位落到 Asked（真的问过）、失败原因非空（页面据此走第二支、不静默消失），
     * 而"画什么"那一半必须仍是空的——失败不能顺手画出一条线来。
     */
    @Test
    fun `路线请求失败时问过这一事实与失败原因都留下`() {
        TestHost.strictBackend()
        driverResidue()
        enqueueFirstFour()
        respond("服务不可用", code = 500)

        val vm = WorkbenchViewModel()
        vm.load()
        awaitUntil { !vm.loading }

        assertEquals(5, server.requestCount)
        assertEquals(PolylinePhase.Asked, vm.routePhase)
        assertNotNull("失败原因必须留得住，否则页面只剩『后端没有』这一句可说", vm.routeFailure)
        assertNotNull(vm.guide)
        assertTrue("失败轮不许画出任何道路几何", MissionMarkers.paths(vm.guide!!).isEmpty())
        assertTrue(vm.guide!!.stops.isEmpty())
        assertNull(
            "失败支拿回的是 EMPTY：后端这一轮没机会给任何原因，所以 reason 必须是 null（原因非空即『后端说过话』）",
            vm.guide!!.routeUnavailableReason,
        )
        val text = MissionMarkers.missionRouteReasonOf(vm.routePhase, vm.routeFailure, vm.guide!!.routeUnavailableReason)
        assertTrue(text, text.contains("这次没问到"))
    }

    /** 撤档早退那一条：四条司机域请求一条都没发，相位必须退回"没问过"，不能挂着上一轮的"问过" */
    @Test
    fun `撤档早退时路线状态退回没问过`() {
        TestHost.strictBackend()
        driverResidue()
        respondData("null")

        val vm = WorkbenchViewModel()
        vm.load()
        awaitUntil { vm.demoted }

        assertEquals(1, server.requestCount)
        assertEquals(PolylinePhase.NotAsked, vm.routePhase)
        assertNull(vm.routeFailure)
        assertNull(vm.guide)
        assertFalse(
            "没问过那一格不能念成后端的结论",
            MissionMarkers.missionRouteReasonOf(vm.routePhase, vm.routeFailure, null).contains("后端没有下发"),
        )
    }

    /**
     * 跨轮残留（这条才是"清零"那两行的行为证据）：
     * 第一轮 route 失败留下 Asked + 失败原因，第二轮档案说不是司机 → 两个状态都必须回到起点，
     * 否则撤档后页面还挂着上一班次的"这次没问到"。
     */
    @Test
    fun `上一轮留下的问过与失败在早退时一起清零`() {
        TestHost.strictBackend()
        driverResidue()
        enqueueFirstFour()
        respond("服务不可用", code = 500)

        val vm = WorkbenchViewModel()
        vm.load()
        awaitUntil { !vm.loading }
        assertEquals(PolylinePhase.Asked, vm.routePhase)
        assertNotNull(vm.routeFailure)

        respondData("null")
        vm.load()
        awaitUntil { vm.demoted }

        assertEquals("早退后仍然只多发这一条档案请求", 6, server.requestCount)
        assertEquals(PolylinePhase.NotAsked, vm.routePhase)
        assertNull(vm.routeFailure)
    }

    /**
     * "后端真的说过话"那一支（与上一条失败支对照）：回了任务点、来源也声明了，但没回折线，
     * 校验器一定给一个**非空**原因（这条措辞是 `RealRoadRouteValidator.validate` 的产出，不是本机编的），
     * 页面必须转述它而不是自己另编。两条合起来才是"reason 为 null = 没问到"这半边判据的行为证据。
     *
     * 夹具按键形状取后端生产者键：`routeProvider`（`AppDriverRouteRespVO:36`）+ `stops[]`。
     */
    @Test
    fun `问到任务点时不留失败原因而转述后端给的原因`() {
        TestHost.strictBackend()
        driverResidue()
        enqueueFirstFour()
        respondData(
            """{"shiftCode":"K1-0830","lineName":"K1 县城 ⇄ 云山村","routeProvider":"OSM_GRAPHHOPPER","routeDistanceMeters":24600,"routeDurationSeconds":3720,"deviated":false,"stops":[{"stationId":31,"stationName":"高家镇客运站","longitude":106.91,"latitude":30.94,"visitSequence":1,"actionType":4,"actionName":"揽收","quantity":6,"status":2,"statusName":"已到站"}]}"""
        )

        val vm = WorkbenchViewModel()
        vm.load()
        awaitUntil { !vm.loading }

        assertEquals(PolylinePhase.Asked, vm.routePhase)
        assertNull(vm.routeFailure)
        assertEquals(1, vm.guide!!.stops.size)
        assertEquals("重庆 OSM · GraphHopper没有返回道路折线", vm.guide!!.routeUnavailableReason)
        val text = MissionMarkers.missionRouteReasonOf(vm.routePhase, vm.routeFailure, vm.guide!!.routeUnavailableReason)
        assertTrue(text, text.startsWith("重庆 OSM · GraphHopper没有返回道路折线"))
        assertTrue(text, text.contains("任务点的位置与顺序仍按后端下发"))
        assertTrue(MissionMarkers.paths(vm.guide!!).isEmpty())
    }

    /** 演示回落是"拿到了一份演示几何"，既不是失败也不是没问，不能被念成后端说没有 */
    @Test
    fun `演示回落不算这次没问到`() {
        TestHost.demoBackend()
        driverResidue()
        repeat(5) { respond("服务不可用", code = 500) }

        val vm = WorkbenchViewModel()
        vm.load()
        awaitUntil { !vm.loading }

        assertEquals(PolylinePhase.Asked, vm.routePhase)
        assertNull("演示数据顶上时没有『没问到』这件事可说", vm.routeFailure)
        assertTrue(vm.demo)
    }
}
