package com.shanxiang.buspost.data.repo

import com.shanxiang.buspost.ServerTest
import com.shanxiang.buspost.TestHost
import com.shanxiang.buspost.data.model.Geo
import com.shanxiang.buspost.data.model.Reachability
import com.shanxiang.buspost.data.model.ReachabilityState
import com.shanxiang.buspost.data.model.Station
import com.shanxiang.buspost.ui.user.SendViewModel
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `/send/reachability` 契约（审计 §34 可达性真实化 + 用户 §11 三态）。
 *
 * 口径来源是后端实际 Response VO，不是猜的：`AppSendReachabilityRespVO.java:19-56`
 * （`distanceKm` 公里制 BigDecimal、站名在 `recommendedStation.name`、`distanceProvider` 三值），
 * 服务实现 `AppSendReachabilityService.java:51-149`（三条早退分支只填 reachable/reasonCode/message，
 * 其余字段留 null；`reachable` 用**道路距离**在 :129 复核，不是用直线值）。
 *
 * 钉住的三件事：
 * ① 只有后端 `reachable == true` 才允许 REACHABLE 口径，缺键/null 一律 UNKNOWN（不许塌成"不可达"再本机编原因）；
 * ② 接口没给的数字必须渲染成"接口未给"，不能出现"0 公里 / 步行 0 分钟"；
 * ③ 提交报文里的 `pickupServiceMode` 只能是 `ServiceModeEnum` 的五个码之一，且只在该推荐站点真是本单取货站点时才回传
 *    （旧代码写死 `"STATION"`，不在枚举里，`TransportOrderServiceImpl:176/:202-203` 原样落库并优先采信）。
 *
 * fixture 按 §49 要求逐字段取**非默认值**（0.18/1/31L/非空站名），并在第一个用例里逐个和空态对比，
 * 否则字段名写错时会因为"两边都是默认值"而假通过。
 */
class ReachabilityContractTest : ServerTest() {

    /** 后端可达分支的真实形状（reasonCode 为 null 是 `:138` 的口径，不是缺键） */
    private val reachableJson = """
        {"reachable":true,"reasonCode":null,"serviceMode":"DOOR_PICKUP","distanceKm":0.18,"walkMinutes":1,
         "distanceProvider":"AMAP_ROAD","message":"当前位置可由车辆就近服务，取货站将使用最近站点",
         "recommendedStation":{"id":31,"name":"高家镇客运站","longitude":106.91,"latitude":30.94,"distanceKm":0.18}}
    """.trimIndent()

    private fun parse(json: String) = Reachability.parse(JSONObject(json))

    @Test
    fun `可达分支逐字段读出且都不是默认值`() = runBlocking {
        TestHost.strictBackend()
        respondData(reachableJson)
        val r = Repo.reachability(Geo(30.9400, 106.9100)).value!!
        val empty = Reachability.unknown()

        assertEquals(ReachabilityState.REACHABLE, r.state)
        assertTrue(r.reachable)
        // 非默认值守卫：每个字段都必须与空态不同，否则 fixture 假通过
        assertNonDefault(empty.serviceMode, r.serviceMode)
        assertNonDefault(empty.distanceKm, r.distanceKm)
        assertNonDefault(empty.walkMinutes, r.walkMinutes)
        assertNonDefault(empty.distanceProvider, r.distanceProvider)
        assertNonDefault(empty.stationId, r.stationId)
        assertNonDefault(empty.stationName, r.stationName)
        assertNonDefault(empty.message, r.message)

        assertEquals("DOOR_PICKUP", r.serviceMode)
        assertEquals("上门/就近交接", r.serviceModeLabel)
        assertEquals(0.18, r.distanceKm!!, 0.0001)
        assertEquals(1, r.walkMinutes)
        assertEquals("AMAP_ROAD", r.distanceProvider)
        assertTrue("高德道路来源才配说道路距离", r.isRealRoadDistance)
        assertEquals("（道路距离）", r.providerLabel)
        assertTrue(r.distanceText.contains("公里"))
        assertTrue(r.walkText.contains("分钟"))
        assertEquals(31L, r.stationId)
        assertEquals("高家镇客运站", r.stationLabel)
        assertEquals(30.94, r.stationGeo!!.lat, 0.0001)
        assertEquals(106.91, r.stationGeo!!.lng, 0.0001)
        // 后端 reachable 时 reasonCode 是 null（:138），不是空串也不是别的码
        assertEquals("", r.reasonCode)
    }

    @Test
    fun `请求按 reqVO 的两个必填坐标发出`() = runBlocking {
        TestHost.strictBackend()
        respondData(reachableJson)
        Repo.reachability(Geo(30.94, 106.91))
        val req = lastRequest()
        assertEquals("POST", req.method)
        assertEquals("/app-api/transport/send/reachability", req.path)
        val body = bodyOf(req)
        assertTrue(body.contains("\"latitude\":30.94"))
        assertTrue(body.contains("\"longitude\":106.91"))
    }

    @Test
    fun `HAVERSINE 必须标成直线估算而不是道路里程`() = runBlocking {
        TestHost.strictBackend()
        respondData(
            """{"reachable":false,"reasonCode":"USER_LOCATION_UNREACHABLE","serviceMode":"NEAREST_STATION",
               "distanceKm":0.86,"walkMinutes":22,"distanceProvider":"HAVERSINE",
               "recommendedStation":{"id":32,"name":"白马村驿站","longitude":106.98,"latitude":30.99,"distanceKm":0.86},
               "message":"当前位置车辆无法直接进入，请送到最近服务站点交接"}"""
        )
        val r = Repo.reachability(Geo(30.99, 106.98)).value!!
        assertEquals(ReachabilityState.NOT_REACHABLE, r.state)
        assertFalse(r.isRealRoadDistance)
        assertEquals("（直线估算，不是道路里程）", r.providerLabel)
        assertTrue("来源如实是硬规则：${r.distanceText}", r.distanceText.contains("直线估算"))
        assertEquals(0.86, r.distanceKm!!, 0.0001)
        assertEquals(22, r.walkMinutes)
        assertEquals("最近站点交接", r.serviceModeLabel)
        assertEquals("白马村驿站", r.stationLabel)
        assertEquals("USER_LOCATION_UNREACHABLE", r.reasonCode)
        assertTrue(r.reasonText("兜底").contains("当前位置不适合车辆进入"))
    }

    /** `distanceProvider` 实际有三个值，VO 的 @Schema 只写了前两个；OSM/GraphHopper 里程会被后端打成 EUCLIDEAN（:122） */
    @Test
    fun `EUCLIDEAN 同样按直线估算标注`() {
        val r = parse(
            """{"reachable":false,"serviceMode":"NEAREST_STATION","distanceKm":1.25,"walkMinutes":31,
               "distanceProvider":"EUCLIDEAN"}"""
        )
        assertEquals("（直线估算，不是道路里程）", r.providerLabel)
        assertFalse(r.isRealRoadDistance)
    }

    /** 未知来源不许替后端编一句"道路距离"，原样带出来对账 */
    @Test
    fun `未知来源原样说明不冒充道路`() {
        val r = parse("""{"reachable":true,"distanceKm":2.5,"distanceProvider":"GRAPH_HOPPER"}""")
        assertEquals("（来源 GRAPH_HOPPER）", r.providerLabel)
        assertFalse(r.isRealRoadDistance)
    }

    /** 早退分支（`:53-57`/`:68-72`/`:93-97`）只填三个字段，其余是 null —— Jackson 会把 null 键一起下发 */
    @Test
    fun `早退分支的空字段渲染成接口未给而不是零`() = runBlocking {
        TestHost.strictBackend()
        respondData(
            """{"reachable":false,"reasonCode":"ROAD_UNREACHABLE","serviceMode":null,"distanceKm":null,
               "walkMinutes":null,"distanceProvider":null,"recommendedStation":null,
               "message":"未提供当前位置，无法判断可达性"}"""
        )
        val r = Repo.reachability(Geo(0.0, 0.0)).value!!
        assertEquals(ReachabilityState.NOT_REACHABLE, r.state)
        assertNull(r.distanceKm)
        assertNull(r.walkMinutes)
        assertEquals("距离接口未给", r.distanceText)
        assertEquals("步行时间接口未给", r.walkText)
        assertFalse("不许出现 0 公里这种假数字", r.distanceText.contains("0"))
        assertEquals(0L, r.stationId)
        assertEquals("", r.stationLabel)
        assertEquals("", r.serviceModeLabel)
        assertTrue(r.reasonText("兜底").contains("未提供当前位置"))
    }

    /** §6 三态：5/0/null 里的 null —— `reachable` 缺键或为 null 都是 UNKNOWN */
    @Test
    fun `reachable 缺失或为 null 都是 UNKNOWN`() = runBlocking {
        TestHost.strictBackend()
        respondData("""{"reachable":null,"message":"网关返回了空判定"}""")
        val nullFlag = Repo.reachability(Geo(30.9, 106.9)).value!!
        assertEquals(ReachabilityState.UNKNOWN, nullFlag.state)
        assertFalse("UNKNOWN 不能同时被当成可运", nullFlag.reachable)

        respondData("{}")
        val missing = Repo.reachability(Geo(30.9, 106.9)).value!!
        assertEquals(ReachabilityState.UNKNOWN, missing.state)
        assertEquals("", missing.reasonCode)
        assertEquals("距离接口未给", missing.distanceText)
    }

    @Test
    fun `三个后端原因码与未知码的口径`() {
        assertEquals("附近没有可用站点（含车辆可停靠的站点），请联系平台",
            parse("""{"reachable":false,"reasonCode":"NO_SAFE_HANDOFF_POINT"}""").reasonText("兜底"))
        // 后端第三个码 ROAD_UNREACHABLE 在 VO 注释里没列，这里按源码 :55 认
        assertTrue(parse("""{"reachable":false,"reasonCode":"ROAD_UNREACHABLE"}""").reasonText("兜底")
            .contains("未提供当前位置"))
        val other = parse("""{"reachable":false,"reasonCode":"STATION_CLOSED","message":"站点暂停"}""")
        assertTrue("未知码要能对上账，不许翻译成别的原因：${other.reasonText("兜底")}",
            other.reasonText("兜底").contains("STATION_CLOSED"))
        assertTrue(other.reasonText("兜底").contains("站点暂停"))
        // reasonCode 缺键时才退回调用方给的文案
        assertEquals("请送到服务站点交接", parse("""{"reachable":false}""").reasonText("请送到服务站点交接"))
    }

    @Test
    fun `接口失败给错误而不是编一个判定`() = runBlocking {
        TestHost.strictBackend()
        respondBizError(1005002001, "可达性服务未配置")
        val biz = Repo.reachability(Geo(30.9, 106.9))
        assertNull(biz.value)
        assertNotNull(biz.error)
        assertTrue(biz.error!!.contains("可达性服务未配置"))

        respond("<html>bad gateway</html>", 502, "text/html")
        val net = Repo.reachability(Geo(30.9, 106.9))
        assertNull("正式构建不许回落成演示判定", net.value)
        assertFalse(net.demo)
        assertNotNull(net.error)
    }

    /** 演示兜底也必须按真实契约形状给字段，否则三态解析在 debug 上看到的是另一个世界 */
    @Test
    fun `演示兜底按真实形状给公里与推荐站点并明确标记`() = runBlocking {
        TestHost.demoBackend()
        respond("<html>down</html>", 503, "text/html")
        val one = Repo.reachability(Geo(29.5688, 106.5432))
        assertTrue(one.demo)
        val r = one.value!!
        assertEquals(ReachabilityState.NOT_REACHABLE, r.state)
        assertEquals(1.86, r.distanceKm!!, 0.0001)
        // 步行分钟与后端公式同口径：ceil(1.86*1000/1.2/60)=26
        assertEquals(26, r.walkMinutes)
        assertEquals("HAVERSINE", r.distanceProvider)
        assertEquals("NEAREST_STATION", r.serviceMode)
        assertTrue("兜底数据不许冒充道路里程", r.distanceText.contains("直线估算"))
        assertEquals(103L, r.stationId)
        assertEquals("石桥驿站", r.stationLabel)
    }

    // ── 提交报文：取货方式与原始地址 ──

    private fun vmWith(json: String?, pickupId: Long, stations: List<Station>): SendViewModel {
        val vm = SendViewModel()
        vm.stations = stations
        vm.pickup = stations.firstOrNull { it.id == pickupId }
        vm.originalAddress = "重庆市永川区高家镇场口"
        vm.reach = json?.let { parse(it) }
        return vm
    }

    private val stationList = listOf(
        Station(id = 31L, name = "高家镇客运站", geo = Geo(30.94, 106.91)),
        Station(id = 32L, name = "白马村驿站", geo = Geo(30.99, 106.98)),
    )

    @Test
    fun `旧代码写死的 STATION 码不再进报文`() {
        val vm = vmWith(
            """{"reachable":true,"serviceMode":"STATION","recommendedStation":{"id":31,"name":"高家镇客运站"}}""",
            pickupId = 31L,
            stations = stationList,
        )
        assertNull("不在 ServiceModeEnum 里的码一律不回传，交给承运审核推导", vm.buildSendForm()["pickupServiceMode"])
    }

    @Test
    fun `推荐站点被选中时才带后端服务方式`() {
        val vm = vmWith(
            """{"reachable":false,"reasonCode":"USER_LOCATION_UNREACHABLE","serviceMode":"NEAREST_STATION",
               "recommendedStation":{"id":31,"name":"高家镇客运站","distanceKm":0.86}}""",
            pickupId = 31L,
            stations = stationList,
        )
        vm.useRecommendedStation()
        assertEquals(31L, vm.pickup?.id)
        assertEquals("NEAREST_STATION", vm.buildSendForm()["pickupServiceMode"])
        assertTrue(vm.serviceModeText().contains("送到服务站点交接"))
    }

    @Test
    fun `用户自选别的站点时不冒充最近站点交接`() {
        val vm = vmWith(
            """{"reachable":false,"serviceMode":"NEAREST_STATION","recommendedStation":{"id":31,"name":"高家镇客运站"}}""",
            pickupId = 32L,
            stations = stationList,
        )
        assertNull("取货站点不是后端推荐的那个，服务方式就不回传", vm.buildSendForm()["pickupServiceMode"])
    }

    /** 推荐站点不在可选列表里：宁可让用户手选，也不构造一个只有 id/name 的假站点塞进表单 */
    @Test
    fun `推荐站点不在列表时保持用户站点并给出提示`() {
        val vm = vmWith(
            """{"reachable":false,"serviceMode":"NEAREST_STATION","recommendedStation":{"id":99,"name":"外站"}}""",
            pickupId = 32L,
            stations = stationList,
        )
        vm.useRecommendedStation()
        assertEquals(32L, vm.pickup?.id)
        assertTrue(vm.formError.contains("不在可选站点列表"))
        assertNull(vm.buildSendForm()["pickupServiceMode"])
    }

    @Test
    fun `原始地址是用户位置而不是推荐站点名`() {
        val vm = vmWith(
            """{"reachable":false,"serviceMode":"NEAREST_STATION","recommendedStation":{"id":31,"name":"高家镇客运站"}}""",
            pickupId = 31L,
            stations = stationList,
        )
        val form = vm.buildSendForm()
        assertEquals("重庆市永川区高家镇场口", form["originalAddress"])
        assertNonDefault(vm.reach!!.stationLabel, form["originalAddress"])
        // 逆地理取不到时留空，让 Api 丢键，也不拿站点名顶
        vm.originalAddress = "   "
        assertEquals("", vm.buildSendForm()["originalAddress"])
    }

    @Test
    fun `可达性未知时说明里不出现可以运输的口径`() {
        val noReach = SendViewModel()
        noReach.reachError = "服务暂时不可用（HTTP 502）"
        val text = noReach.serviceModeText()
        assertTrue(text.contains("未做可达性判定"))
        assertFalse(text.contains("可就近"))
        assertFalse(text.contains("可以运输"))

        val unknown = vmWith("""{"message":"网关没给判定"}""", pickupId = 31L, stations = stationList)
        assertTrue(unknown.serviceModeText().contains("可达性未判定"))
        assertNull(unknown.buildSendForm()["pickupServiceMode"])
    }

    @Test
    fun `空态模型自身就是 UNKNOWN`() {
        val empty = Reachability.unknown()
        assertEquals(ReachabilityState.UNKNOWN, empty.state)
        assertNull(empty.distanceKm)
        assertNull(empty.walkMinutes)
        assertEquals("", empty.stationLabel)
    }
}

private fun assertNonDefault(unexpected: Any?, actual: Any?) {
    assertFalse("fixture 与空态取值相同（$actual），字段名写错也不会失败", unexpected == actual)
}
