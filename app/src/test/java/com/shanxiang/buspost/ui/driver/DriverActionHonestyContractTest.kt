package com.shanxiang.buspost.ui.driver

import com.shanxiang.buspost.ServerTest
import com.shanxiang.buspost.TestHost
import com.shanxiang.buspost.awaitUntil
import com.shanxiang.buspost.data.model.Role
import com.shanxiang.buspost.data.repo.Repo
import com.shanxiang.buspost.data.store.Prefs
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 司机工作台"缺项即不出网"与"本机推进不动后端"的契约（F-3 ⑦⑨ · MockWebServer 计数）。
 *
 * 判据一律是 `server.requestCount`（MockWebServer 自己数的）而不是"我看代码觉得会早退"：
 * 后端 `AppDriverArriveReqVO.stationId` 是 `@NotNull("站点编号不能为空")`、`pickupCode` 走
 * `POSTAL_PICKUP_CODE_INVALID`，所以这三条发出去都只是**必被拒的一跳**；拒了不留脏数据，
 * 但回执会显示后端拒绝原因，而司机看到的其实是"本机自己造了一个缺项的请求"（硬规则：宁可不提交，不造字段）。
 *
 * 前三条各自配了反方向的对照用例，防止"早退"退成"这个动作永远不发"：
 * · 有站点编号时到站照常提交（11 条 = 5 读 + 1 写 + 5 读）；
 * · 幂等闸拦下的那次只该有回读、不该有写；
 * · 填了取件码才提交，报文形状原样不变（`Api` 只丢空值，我们不改 wire）。
 * 第五条（#147 收尾）钉的是另一头：`「继续行驶」`只动导航目标那一层，后端当前站与出网条数都不许动。
 */
class DriverActionHonestyContractTest : ServerTest() {

    @Before
    fun bindDriverAndMain() {
        TestHost.installMainDispatcher()
        Prefs.token = "tk-driver"
        Prefs.role = Role.DRIVER
        // 司机写路径一律要 driverId，未绑定会在出网前就抛 DriverUnboundException，测不到下面这些语义
        Prefs.driverId = 7002L
    }

    /**
     * 三站班次、后端说当前在第 1 站 ⇒ 目标站是 502（中途站），到站真发成功后才该进"停靠作业"视图。
     *
     * 键名一律用后端**实际下发**的那一套（`/driver/shifts` 的站列表叫 `stops` 而不是 `stations`，
     * 站对象的主键是 `stationId`/`stationName`，见 `AppDriverShiftRespVO:34` 与 `AppDriverStationRespVO:11-13`，
     * `status` 是 Integer）：客户端 `multiArr("stations","stops","viaStations")` 认别名，
     * 所以别名形状的用例同样全绿，但它证明的是"别名可用"，不是"真机那条路走得通"——
     * 一旦按 F-3 ② 的路子把无生产者别名删掉，真机点「到站」会坏而这些用例照绿。
     *
     * 写成一行是有原因的：raw string 拆成两段拼接时，前一段以 `"` 结尾会被解析成结束定界符的一部分，
     * 拼出来的 JSON 少一个引号 ⇒ 夹具静默坏成"没有站点"，把"缺项早退"读成"测试通过"。
     */
    private fun midWayShift() = """[{"shiftId":7101,"status":1,"currentStationId":501,"stops":[{"stationId":501,"stationName":"起点站","sequenceNo":1},{"stationId":502,"stationName":"中途站","sequenceNo":2},{"stationId":503,"stationName":"末站","sequenceNo":3}]}]"""

    /**
     * 一次**进入/刷新**的回读是 6 条，顺序与 syncFromServer 一致：profile → shifts → pickups → tasks → route → notice；
     * 作业动作后的那次回读只有 5 条（公告不随司机动作而变，见 `WorkbenchScreen.kt` 的 `withNotices`）。
     * ⚠ 2026-10-12 加公告那一格时本类全部计数同轮 +1（只加在进入/刷新那侧），不是把闸调松：
     *   少一条会红、多一条也会红。
     * 车牌的真实键是 `plateNo`（`AppDriverProfileRespVO:19`），`Models.kt:1374` 现在也只认它——
     * 上一版夹具写的 `plate` 是已被删掉的自造别名，留着它只会让夹具离真机越来越远。
     */
    private fun enqueueReads(shiftsJson: String, pickupsJson: String = "[]") {
        respondData("""{"driverId":7002,"name":"王有德","plateNo":"渝A·8K231","cargoCapacityKg":1200}""")
        respondData(shiftsJson)
        respondData(pickupsJson)
        respondData("[]")
        respondData("null")
        // 公告：夹具给"后端确实没有公告"这一格（show=false，不影响本类任何画面断言，只多一跳计数）
        respondData("[]")
    }

    /**
     * 按路径挑出已经打出去的请求。必须在动作跑完之后调 —— MockWebServer 的记录队列取完即空，
     * 拿 200 毫秒超时当"队列空了"的判据，所以它只会用在网络轮次已经结束的地方。
     */
    private fun requestsFor(path: String): List<RecordedRequest> =
        generateSequence { server.takeRequest(200, TimeUnit.MILLISECONDS) }
            .filter { it.path == path }
            .toList()

    /**
     * 夹具自检。这一层不是装饰：本轮第一版把班次 JSON 拆成两段 raw string 拼接，
     * 前一段以 `"` 结尾 ⇒ 拼出来少一个引号 ⇒ 后端"给了 3 站"在夹具里静默坏成"一个站都没有"，
     * 于是"缺项早退"的用例因为夹具坏而绿。先断 `vm.error == null` 与站点条数，再谈动作。
     */
    private fun awaitLoaded(vm: WorkbenchViewModel, expectStations: Int) {
        awaitUntil { !vm.loading }
        assertNull("加载必须干净完成，坏夹具会把早退读成通过", vm.error)
        assertEquals(expectStations, vm.shift?.stations?.size)
        assertEquals("加载本身恰好 6 条读（5 条司机域 + 1 条公告）", 6, server.requestCount)
    }

    @Test
    fun `后端没给站点编号时到站一条都不发`() {
        TestHost.strictBackend()
        // 班次在、状态在途，但 stops 是空数组：后端 `vo.setStops(...)` 会给一个空列表（线路没配途经站的真实形状）
        enqueueReads("""[{"shiftId":7102,"status":1,"currentStationId":0,"stops":[]}]""")

        val vm = WorkbenchViewModel()
        vm.load()
        awaitLoaded(vm, expectStations = 0)
        assertEquals(0L, vm.targetStationId())

        vm.arrive(0L)

        assertEquals("站点编号缺项时一条都不许出网，连动作后的回读都不发", 6, server.requestCount)
        assertEquals("后端没给本站编号，到站提交不了——请回到工作台重新拉取班次", vm.receipt)
        assertFalse(vm.docked)
    }

    @Test
    fun `有站点编号时到站照常提交并进入停靠视图`() {
        TestHost.strictBackend()
        enqueueReads(midWayShift())
        respondData("true")
        enqueueReads(midWayShift())

        val vm = WorkbenchViewModel()
        vm.load()
        awaitLoaded(vm, expectStations = 3)
        assertEquals(502L, vm.targetStationId())

        // 与按钮调用点同形：UI 传的就是 vm.targetStationId()，这里传 0 走的是班次里取目标站那一支
        vm.arrive(0L)
        awaitUntil { !vm.busy }

        assertEquals("6 读 + 1 写 + 5 读", 12, server.requestCount)
        val body = bodyOf(requestsFor("/app-api/transport/driver/arrive").single())
        assertTrue("报文仍是后端 ReqVO 那两个字段，早退闸门不改 wire", body.contains("\"shiftId\":7101"))
        assertTrue(body.contains("\"stationId\":502"))
        assertTrue("真发出去的那一次才允许推进本机视图", vm.docked)
    }

    /**
     * V-1 幂等闸与"本机视图"的接缝：`ack.notSent` 意味着请求被本地拦下、**一个字节都没上网络**，
     * 此时把画面翻成"停靠作业"就和同一句回执（"显示的是后端当前状态"）自相矛盾。
     */
    @Test
    fun `幂等闸拦下的那次到站不推进本机停靠视图`() = runBlocking {
        TestHost.strictBackend()
        enqueueReads(midWayShift())
        respondData("true")
        enqueueReads(midWayShift())

        val vm = WorkbenchViewModel()
        vm.load()
        awaitLoaded(vm, expectStations = 3)

        // 闸门外先有一次真的在路上（另一个入口/上一次点击还没跑完），键与工作台这次完全相同
        Repo.arrive(7101L, 502L)
        assertEquals("闸门外的这一次是真发出去了", 7, server.requestCount)
        assertFalse(vm.docked)

        vm.arrive(502L)
        awaitUntil { !vm.busy }
        assertNull("notSent 那一支的回读也必须干净完成", vm.error)

        assertEquals("拦下后只该多 5 条回读，多一条就是幂等闸没拦住", 12, server.requestCount)
        assertFalse("一个字节都没上网络，画面不许先行进入「停靠作业」", vm.docked)
        assertTrue(vm.receipt!!.contains("未发给后端"))
    }

    @Test
    fun `空白取件码不许出网_填了码才提交`() {
        TestHost.strictBackend()
        // currentStationId 整个键缺席也是真形状：后端只在有执行记录时才 set 它（`DriverAppServiceImpl:235-237`）
        val shift = """[{"shiftId":7103,"status":1,"stops":[{"stationId":601,"stationName":"站点","sequenceNo":1}]}]"""
        // 待核销的那张单必须是邮快件：后端 `pickupVerify` 开头就写死"仅邮快件（order_type=3）支持取件核销"
        // （`DriverAppServiceImpl:1150-1152`），货运/商城单发过去吃的是 `DRIVER_ORDER_STATUS_ILLEGAL` 而不是取件码校验。
        // 邮快件的 goodsName 后端就是拿面单号填的（`:302` `blankToDefault(postal.getMailNo(), "快递")`），夹具照抄这个形状。
        val pickups = """[{"orderId":8801,"orderNo":"Y2601","orderType":3,"bizType":"POSTAL","mailNo":"SF7788990011","pickupCode":"1234","goodsName":"SF7788990011","receiverName":"李"}]"""
        enqueueReads(shift, pickups)
        respondData("true")
        enqueueReads(shift, pickups)

        val vm = WorkbenchViewModel()
        vm.load()
        awaitLoaded(vm, expectStations = 1)
        val task = vm.pickups.single()

        // 手动输入那一支的输入框只收数字，但核验按钮在没有码时同样可点：这两条必须都不出网
        vm.verify(task, "")
        assertEquals("空码一条都不发", 6, server.requestCount)
        assertEquals("取件码还没填，核销提交不了——请扫码或让寄件人报出取件码", vm.receipt)

        vm.verify(task, "   ")
        assertEquals("纯空格同样不发：Api 出网前会把空串整个键丢掉，后端收到的是没有 pickupCode 的请求", 6, server.requestCount)

        vm.verify(task, "1234")
        awaitUntil { server.requestCount >= 12 }

        assertEquals("填了码才走真提交", 12, server.requestCount)
        val body = bodyOf(requestsFor("/app-api/transport/driver/pickup-verify").single())
        assertTrue(body.contains("\"orderId\":8801"))
        assertTrue("发送的还是用户输入的那一串，判空不改报文", body.contains("\"pickupCode\":\"1234\""))
    }

    /**
     * 「继续行驶」推进的只是**导航目标**那一层：后端当前站一个字节都不许变，且这是纯本机动作、一条请求都不发。
     *
     * 为什么值得单独一条用例（F-3 ⑦⑨ #147 收尾）：本机"任务点"是两层派生 —— 导航目标 =
     * 后端 `currentStationId` 的下一站 + `navOffset`，显示层的"第 N/M 站"只认后端 `currentStationId`。
     * 结构判据（H-m1/H-m2/H-m3）只看得见"写点在哪"，看不见"点下去之后后端那一格有没有被本机改掉"。
     * 一旦有人让「继续行驶」顺手推进 `shift`（"界面上去了就等于上去了"），三条结构判据全都还是绿的，
     * 而司机画面上的进度已经不再是后端状态 —— 与 H-b 那条 `docked` 的红线同族。
     * 与小程序 `workbench.js:849-866 continueToNextStation()` 同语义（它也只 `applyNavView`，不回写 stops）。
     */
    @Test
    fun `继续行驶只推进导航目标_后端当前站与网络都不动`() {
        TestHost.strictBackend()
        enqueueReads(midWayShift())

        val vm = WorkbenchViewModel()
        vm.load()
        awaitLoaded(vm, expectStations = 3)
        assertEquals(501L, vm.shift?.currentStationId)
        assertEquals("当前在第 1 站 ⇒ 导航目标是下一站 502", 502L, vm.targetStationId())

        vm.continueToNextStation()
        assertEquals(503L, vm.targetStationId())
        assertEquals("推进不得回写后端给的当前站", 501L, vm.shift?.currentStationId)
        assertEquals("纯本机视图：一条请求都不许多发", 6, server.requestCount)

        // 再点两次跨过末站：第二次 navOffset=2（仍 ≤ 上限），第三次触发上限归位并给回执
        vm.continueToNextStation()
        vm.continueToNextStation()
        assertEquals("到末站后不再往后指（不存在第 4 站）", 503L, vm.targetStationId())
        assertTrue(vm.receipt!!.contains("已经是最后一站"))
        assertEquals(501L, vm.shift?.currentStationId)
        assertEquals(6, server.requestCount)
    }
}
