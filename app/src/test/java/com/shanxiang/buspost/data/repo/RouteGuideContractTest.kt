package com.shanxiang.buspost.data.repo

import com.shanxiang.buspost.ServerTest
import com.shanxiang.buspost.TestHost
import com.shanxiang.buspost.core.map.RouteRepository
import com.shanxiang.buspost.data.store.Prefs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * `/driver/route` 的真实性契约。
 *
 * 这里钉住的是最容易复发的一件事：后端没给道路数据时，客户端绝不能"算一个出来"。
 * 旧实现会用 Haversine 把车到下一站的直线距离填进 nextStationDistanceMeters，
 * 司机看到的就是一个伪装成路况的直线数 —— 下面第二个用例专门盯着它。
 */
class RouteGuideContractTest : ServerTest() {

    @Before
    fun driverSession() {
        Prefs.driverId = 9L
        RouteRepository.clear()
    }

    /** 首末直线约 7.3 公里的一段真实道路 */
    private val realPolyline = """
        [{"latitude":29.60,"longitude":106.60},{"latitude":29.62,"longitude":106.63},{"latitude":29.65,"longitude":106.65}]
    """.trimIndent()

    private val twoStops = """
        [{"id":11,"stationName":"县城客运站","latitude":29.60,"longitude":106.60},
         {"id":12,"stationName":"白朝村驿站","latitude":29.65,"longitude":106.65}]
    """.trimIndent()

    @Test
    fun `amap 来源的真实折线与同源里程一并收下`() = runBlocking {
        TestHost.strictBackend()
        respondData(
            """{"routeProvider":"amap","coordinateSystem":"GCJ-02",
               "stops":$twoStops,"polyline":$realPolyline,
               "routeDistanceMeters":9200,"routeDurationSeconds":1560,
               "nextStopRoadDistanceMeters":4100,"nextStopRoadEtaSeconds":660,
               "steps":[{"text":"沿滨江路向北","distanceMeters":320,"current":true}]}"""
        )
        val guide = Repo.routeGuide().value!!
        assertNull(guide.routeUnavailableReason)
        assertTrue(guide.hasRealRoad)
        assertEquals(3, guide.polyline.size)
        assertEquals(9200, guide.roadDistanceMeters)
        assertEquals(1560, guide.roadEtaSeconds)
        assertEquals(26, guide.etaMinutes)
        assertEquals(4100, guide.nextStopRoadDistanceMeters)
        assertEquals(660, guide.nextStopRoadEtaSeconds)
        assertEquals("AMAP", guide.routeSourceName)
    }

    @Test
    fun `后端 routeAvailable=false 时即使带折线也不画`() = runBlocking {
        TestHost.strictBackend()
        // 后端是权威：它说这一版没有完整道路（例如中段取不到、折线被截断），客户端就不能拿数组里的点继续画
        respondData(
            """{"routeProvider":"amap","coordinateSystem":"GCJ-02","routeAvailable":false,
               "routeUnavailableReason":"第 2 段（共 3 段）暂无真实道路，折线已在此截断",
               "stops":$twoStops,"polyline":$realPolyline,"routeDistanceMeters":9200}"""
        )
        val guide = Repo.routeGuide().value!!
        assertFalse(guide.hasRealRoad)
        assertTrue(guide.polyline.isEmpty())
        assertEquals(0, guide.roadDistanceMeters)
        assertTrue(
            "要原样展示后端给的原因：${guide.routeUnavailableReason}",
            guide.routeUnavailableReason.orEmpty().contains("暂无真实道路"),
        )
    }

    @Test
    fun `routeAvailable 缺省时按折线可信度判断`() = runBlocking {
        TestHost.strictBackend()
        respondData(
            """{"routeProvider":"amap","coordinateSystem":"GCJ-02","stops":$twoStops,
               "polyline":$realPolyline,"routeDistanceMeters":9200}"""
        )
        val guide = Repo.routeGuide().value!!
        assertTrue("老后端没带这个字段，不能因此判死", guide.hasRealRoad)
        assertEquals(9200, guide.roadDistanceMeters)
    }

    @Test
    fun `后端没给来源时不得用直线距离编造下一站里程`() = runBlocking {
        TestHost.strictBackend()
        // 有车位、有站点、有 steps，但 routeProvider 缺失、steps 里没有 distanceMeters
        respondData(
            """{"stops":$twoStops,
               "vehicleLongitude":106.60,"vehicleLatitude":29.60,
               "steps":[{"text":"前往白朝村驿站","stationName":"白朝村驿站","current":true}]}"""
        )
        val guide = Repo.routeGuide().value!!
        assertTrue("来源未知就不能算有路线", !guide.hasRealRoad)
        assertNotNull(guide.routeUnavailableReason)
        assertTrue(guide.polyline.isEmpty())
        // 关键回归：旧实现在这里会填进约 7300 米的 Haversine 值
        assertEquals(0, guide.nextStationDistanceMeters)
        assertEquals(0, guide.nextStopRoadDistanceMeters)
        assertEquals(0, guide.roadDistanceMeters)
    }

    @Test
    fun `euclidean 来源即使带折线也被拒收`() = runBlocking {
        TestHost.strictBackend()
        respondData(
            """{"routeProvider":"euclidean","stops":$twoStops,"polyline":$realPolyline,
               "routeDistanceMeters":7300,"steps":[{"text":"前往白朝村驿站","distanceMeters":7300,"current":true}]}"""
        )
        val guide = Repo.routeGuide().value!!
        assertFalse(guide.hasRealRoad)
        assertTrue(guide.polyline.isEmpty())
        assertTrue(
            "原因里要写明来源不可信：${guide.routeUnavailableReason}",
            guide.routeUnavailableReason.orEmpty().contains("不可信"),
        )
    }

    @Test
    fun `amap 标注但折线只有两个站点坐标时判为站点连线`() = runBlocking {
        TestHost.strictBackend()
        respondData(
            """{"routeProvider":"amap","stops":$twoStops,
               "polyline":[{"latitude":29.60,"longitude":106.60},{"latitude":29.65,"longitude":106.65}],
               "steps":[{"text":"前往白朝村驿站","distanceMeters":7300,"current":true}]}"""
        )
        val guide = Repo.routeGuide().value!!
        assertFalse("两点站点连线不能算道路", guide.hasRealRoad)
        assertTrue(guide.polyline.isEmpty())
        assertTrue(guide.routeUnavailableReason.orEmpty().contains("连线"))
    }

    @Test
    fun `amap 折线可信但没给里程时保持未知而不是补数`() = runBlocking {
        TestHost.strictBackend()
        respondData("""{"routeProvider":"amap","stops":$twoStops,"polyline":$realPolyline}""")
        val guide = Repo.routeGuide().value!!
        assertTrue(guide.hasRealRoad)
        assertEquals("接口没给里程就是没给", 0, guide.roadDistanceMeters)
        assertEquals(0, guide.nextStopRoadDistanceMeters)
    }

    @Test
    fun `路网里程小于直线距离时整体拒收`() = runBlocking {
        TestHost.strictBackend()
        respondData(
            """{"routeProvider":"amap","stops":$twoStops,"polyline":$realPolyline,
               "routeDistanceMeters":1000}"""
        )
        val guide = Repo.routeGuide().value!!
        assertFalse(guide.hasRealRoad)
        assertTrue(guide.routeUnavailableReason.orEmpty().contains("直线"))
    }

    @Test
    fun `旧直线字段原样保留但不参与判断`() = runBlocking {
        TestHost.strictBackend()
        respondData(
            """{"routeProvider":"amap","stops":$twoStops,"polyline":$realPolyline,
               "routeDistanceMeters":9200,"nextStopDistanceMeters":7300}"""
        )
        val guide = Repo.routeGuide().value!!
        // 后端 nextStopDistanceMeters 是直线值：解析保留（兼容），真实值仍是 0（本次没给）
        assertEquals(7300, guide.nextStationDistanceMeters)
        assertEquals(0, guide.nextStopRoadDistanceMeters)
        assertTrue(guide.hasRealRoad)
    }

    @Test
    fun `接口 500 时得到错误而不是编造的指引`() = runBlocking {
        TestHost.strictBackend()
        respond("<html>gateway</html>", 500, "text/html")
        val one = Repo.routeGuide()
        assertNotNull(one.error)
        assertTrue(one.value!!.polyline.isEmpty())
        assertFalse(one.value!!.hasRealRoad)
    }

    @Test
    fun `响应为空对象时不崩且明确不可用`() = runBlocking {
        TestHost.strictBackend()
        respondData("{}")
        val guide = Repo.routeGuide().value!!
        assertTrue(guide.steps.isEmpty())
        assertTrue(guide.polyline.isEmpty())
        assertFalse(guide.hasRealRoad)
        assertNotNull(guide.routeUnavailableReason)
        assertTrue(guide.stops.isEmpty())
    }

    /**
     * 审计 §62：`/driver/route` 的 `stops[]` 要按后端真实键名读出任务点属性。
     * 同一个数组喂给两种模型：`stations`（公交站读法，历史调用点在用）与 `stops`（任务点读法）。
     */
    @Test
    fun `任务点属性按后端字段名读出，站点读法不回归`() = runBlocking {
        TestHost.strictBackend()
        val taskStops = """
            [{"stationId":31,"stationName":"高家镇客运站","latitude":30.94,"longitude":106.91,
              "visitSequence":2,"actionType":4,"actionName":"揽收","orderNo":"YP20260928001",
              "quantity":6,"status":2,"statusName":"已到站"},
             {"stationId":32,"stationName":"白马村驿站","latitude":30.99,"longitude":106.98,
              "visitSequence":3,"actionType":3,"actionName":"派送","orderNo":"YP20260928002",
              "quantity":2,"status":7,"statusName":"已完成"}]
        """.trimIndent()
        respondData(
            """{"routeProvider":"amap","coordinateSystem":"GCJ-02","stops":$taskStops,
               "polyline":$realPolyline,"routeDistanceMeters":9200}"""
        )
        val guide = Repo.routeGuide().value!!

        assertEquals(2, guide.stops.size)
        assertEquals(2, guide.stations.size)
        assertEquals(listOf(2, 3), guide.stops.map { it.visitSequence })
        assertEquals(listOf("揽收", "派送"), guide.stops.map { it.actionLabel })
        assertEquals(listOf(6, 2), guide.stops.map { it.quantity })
        assertEquals(listOf("已到站", "已完成"), guide.stops.map { it.statusLabel })
        assertEquals(listOf(31L, 32L), guide.stops.map { it.stationId })

        // 站点读法保持原样：名字与坐标仍可用（历史页面依赖它），但不带任务语义
        assertEquals("高家镇客运站", guide.stations.first().name)
        assertEquals(0, guide.stations.first().sequenceNo)
    }

    /**
     * F-3 ⑬：`guidance` / `instructions` 两个数组键在后端 VO、小程序、演示面三面都没有生产者，
     * 只有 `steps` 是演示面 guideOf() 真在写的键。上一轮把这两个别名从解析层撤了，
     * 这一条钉住撤完之后的行为：**报文里只给别名时，一步都造不出来**。
     * 留着别名等于给"步骤"长出第二个名字 —— 后端改键名时页面不会坏，而是静默读到一个空数组或旧数组。
     */
    @Test
    fun `只给 guidance 与 instructions 时一步都不造`() = runBlocking {
        TestHost.strictBackend()
        respondData(
            """{"routeProvider":"amap","coordinateSystem":"GCJ-02","stops":$twoStops,
               "polyline":$realPolyline,"routeDistanceMeters":9200,
               "guidance":[{"text":"别名步骤","distanceMeters":320,"current":true}],
               "instructions":[{"text":"别名步骤二","distanceMeters":640}]}"""
        )
        val guide = Repo.routeGuide().value!!
        assertTrue("别名数组不许产出步骤：${guide.steps.map { it.text }}", guide.steps.isEmpty())
        // 步骤空了，下一站就只能是没有；从别名数组里"读"出来的站名就是一个没人下发过的结论
        assertEquals("", guide.nextStation)
        assertEquals(0, guide.nextStationDistanceMeters)
    }

    /**
     * F-3 ⑬：来源键在本端点只有 `routeProvider`（后端 AppDriverRouteRespVO:36）与 `dataSource`（演示面）两个真生产者。
     * `routeSource` 三方 0 命中、`provider` 是另一个端点（route-preview）的键 —— 在这里读它们，
     * 就是让一个本端点没有生产者的名字决定"这条折线可不可信"。
     */
    @Test
    fun `routeSource 与 provider 两个别名不决定来源`() = runBlocking {
        TestHost.strictBackend()
        respondData(
            """{"routeSource":"amap","provider":"amap","coordinateSystem":"GCJ-02","stops":$twoStops,
               "polyline":$realPolyline,"routeDistanceMeters":9200}"""
        )
        val guide = Repo.routeGuide().value!!
        assertEquals("UNKNOWN", guide.routeSourceName)
        assertEquals("", guide.dataSource)
        assertFalse("别名给的 amap 不算来源声明", guide.hasRealRoad)
        assertTrue(guide.polyline.isEmpty())
    }

    /**
     * F-3 ⑬⑭：这一屏里被撤掉的五个别名键各自顶替一个真实键 ——
     * roadDistanceMeters→routeDistanceMeters、durationSeconds→routeDurationSeconds、crs→coordinateSystem、
     * offRoute→deviated、steps 内的 meters→distanceMeters / lane→recommendedLane / seconds→etaSeconds。
     * 全部只给别名时必须一件都不产生值：塌成 0 与"后端没给"在页面上是两种话，前者是一句肯定的假话。
     */
    @Test
    fun `五个别名键同时出现也不产生里程时长偏航与步骤量`() = runBlocking {
        TestHost.strictBackend()
        respondData(
            """{"routeProvider":"amap","stops":$twoStops,"polyline":$realPolyline,
               "roadDistanceMeters":7777,"durationSeconds":999,"crs":"GCJ-02","offRoute":true,
               "steps":[{"text":"沿滨江路向北","meters":320,"lane":2,"seconds":90}]}"""
        )
        val guide = Repo.routeGuide().value!!
        assertEquals(0, guide.roadDistanceMeters)
        assertEquals(0, guide.roadEtaSeconds)
        assertFalse("偏航位只由后端的 deviated 决定", guide.deviated)
        val step = guide.steps.first()
        assertEquals(0, step.distanceMeters)
        assertNull(step.recommendedLane)
        assertNull(step.etaSeconds)
    }
}
