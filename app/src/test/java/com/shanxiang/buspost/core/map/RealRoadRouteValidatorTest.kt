package com.shanxiang.buspost.core.map

import com.shanxiang.buspost.data.model.Geo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 真实道路校验器的契约测试：假来源必须被拒，且拒得有意义。
 *
 * 这些用例外层就是项目硬约束本身 —— 只要有一条假路线能从 Validator 走到 UI，
 * 司机屏幕上就会出现一条实际不存在的路。
 */
class RealRoadRouteValidatorTest {

    // 重庆江津一带的一段"道路"：三点、有绕路，首末直线约 7.3 公里
    private val road = listOf(Geo(29.60, 106.60), Geo(29.62, 106.63), Geo(29.65, 106.65))
    private val stations = listOf(Geo(29.60, 106.60), Geo(29.65, 106.65))

    @Before
    fun resetCache() {
        RouteRepository.clear()
    }

    @Test
    fun `euclidean 来源即使带折线也一律拒收`() {
        val check = RealRoadRouteValidator.validate("euclidean", road, roadDistanceMeters = 9_000)
        assertTrue(check::class.simpleName, check is RouteCheck.Rejected)
        val reason = (check as RouteCheck.Rejected).reason
        assertTrue(reason, reason.contains("不可信"))
    }

    @Test
    fun `直线兜底与站点连线的各种写法都能被识别`() {
        mapOf(
            "euclidean" to RouteSource.EUCLIDEAN,
            "EUCLIDEAN" to RouteSource.EUCLIDEAN,
            "euclidean_fallback" to RouteSource.EUCLIDEAN,
            "haversine" to RouteSource.EUCLIDEAN,
            "great_circle" to RouteSource.EUCLIDEAN,
            "straight" to RouteSource.STRAIGHT,
            "two_point" to RouteSource.STRAIGHT,
            "station_join" to RouteSource.STRAIGHT,
            "linear" to RouteSource.STRAIGHT,
        ).forEach { (raw, expected) ->
            assertEquals("provider=$raw", expected, RouteSource.parse(raw))
            assertFalse("provider=$raw 不可信", expected.real)
        }
    }

    @Test
    fun `mock demo fake 一类来源全部不算真实道路`() {
        listOf("mock", "fake", "simulated", "simulation", "demo", "estimated", "SIM", "REAL", "", null, "   ")
            .forEach { raw ->
                val source = RouteSource.parse(raw)
                assertFalse("provider=$raw 不应可信", source.real)
                assertTrue(
                    "provider=$raw 应被拒",
                    RealRoadRouteValidator.validate(raw, road, roadDistanceMeters = 9_000) is RouteCheck.Rejected,
                )
            }
    }

    @Test
    fun `amap graphhopper valhalla 与真实道路缓存属于可信来源`() {
        listOf("amap", "gaode", "AMAP", " graphhopper ", "osm_graphhopper", "roadgraph", "valhalla", "offline_cache")
            .forEach { raw -> assertTrue("provider=$raw", RouteSource.parse(raw).real) }
    }

    /**
     * 后端 `RealRoadProviders.labelOf` 实际下发的就是这几个大写标签。
     * 客户端白名单一旦和它漂移，线上表现是"路线突然消失"而不是报错，所以在这里钉死。
     */
    @Test
    fun `后端下发的标准标签全部在白名单内`() {
        listOf("AMAP", "OSM_GRAPHHOPPER", "OSM_ROAD_GRAPH", "VALHALLA", "CACHE_REAL_ROAD", "MIXED")
            .forEach { label ->
                assertTrue("后端标签 $label 必须可信", RouteSource.parse(label).real)
            }
        // 算法服务的原始 provider 也要能吃下（历史数据里存在小写形态）
        assertTrue(RouteSource.parse("local_graph").real)
        // 没有路线时的标记：不属于任何真实来源
        assertFalse(RouteSource.parse("unavailable").real)
        assertFalse(RouteSource.parse("").real)
    }

    @Test
    fun `可信来源但折线不足两点时仍然拒收`() {
        val one = RealRoadRouteValidator.validate("amap", listOf(Geo(29.6, 106.6)))
        assertTrue(one is RouteCheck.Rejected)
        assertTrue((one as RouteCheck.Rejected).reason.contains("折线"))

        val none = RealRoadRouteValidator.validate("amap", emptyList())
        assertTrue(none is RouteCheck.Rejected)
    }

    @Test
    fun `两个点恰好是首末站点坐标时认定为站点连线而非道路`() {
        val check = RealRoadRouteValidator.validate("amap", stations, stationGeos = stations)
        assertTrue(check::class.simpleName, check is RouteCheck.Rejected)
        assertTrue((check as RouteCheck.Rejected).reason.contains("连线"))
    }

    @Test
    fun `道路里程不可能小于首末直线距离`() {
        val check = RealRoadRouteValidator.validate("amap", road, roadDistanceMeters = 1_000)
        assertTrue(check::class.simpleName, check is RouteCheck.Rejected)
        val rejected = check as RouteCheck.Rejected
        assertTrue(rejected.reason, rejected.reason.contains("直线"))
        assertNotNull("detail 要给出两个数便于排查", rejected.detail)
    }

    @Test
    fun `里程与直线比例异常时判为脏数据`() {
        // 首末直线约 7.3km，给 200km 里程显然是坐标或投影错了
        val check = RealRoadRouteValidator.validate("amap", road, roadDistanceMeters = 200_000)
        assertTrue(check is RouteCheck.Rejected)
        assertTrue((check as RouteCheck.Rejected).detail.contains("windiness"))
    }

    @Test
    fun `全任务段那一条不该被首末直线顶死`() {
        // 真机读数（2026-10-17，1f71ee9b）：导航页念 `road=33230m / straight=2014m`、windiness 16.4 被拒，
        // 而**同一趟**司机工作台念的是"本班 6 站 · 道路里程 33.2 公里"⇒ 高德回的就是全任务段那条真路，
        // 错的是分母：终点绕回出发点附近、中间还压着 5 个途经点，首末直线当然只有 2 公里。
        val origin = Geo(29.5560, 106.5680)
        val far = Geo(29.6500, 106.6500)
        val dest = Geo(29.5380, 106.5700)
        val points = listOf(origin, Geo(29.60, 106.60), far, Geo(29.60, 106.62), dest)
        val check = RealRoadRouteValidator.validate(
            sourceRaw = "amap",
            points = points,
            roadDistanceMeters = 33_230,
            viaGeos = listOf(far),
        )
        assertTrue("这一条必须过闸，否则全任务段导航永远起跑不了：${(check as? RouteCheck.Rejected)?.detail}",
            check is RouteCheck.Accepted)
        val route = (check as RouteCheck.Accepted).route
        assertTrue("前提要成立：首末直线确实很小（${route.straightDistanceMeters}m）",
            route.straightDistanceMeters < 3_000)
        assertTrue("下界必须把途经点算进去（bound=${route.boundDistanceMeters}m）",
            route.boundDistanceMeters > route.straightDistanceMeters * 5)
        assertTrue("绕路比回到正常区间：${route.windiness}", route.windiness in 1.0..2.5)
    }

    @Test
    fun `带途经点时里程短于逐段下界判为没有真的绕经`() {
        // 反方向：号称绕了 far 那个点，里程却只给了首末那点距离 ⇒ 折线没真的过去，这一趟不许开始导航。
        // 旧分母下这条是**过闸**的（5_000 > 首末 2_011），所以它同时是上一格的反证。
        val origin = Geo(29.5560, 106.5680)
        val far = Geo(29.6500, 106.6500)
        val dest = Geo(29.5380, 106.5700)
        val check = RealRoadRouteValidator.validate(
            sourceRaw = "amap",
            points = listOf(origin, far, dest),
            roadDistanceMeters = 5_000,
            viaGeos = listOf(far),
        )
        assertTrue(check::class.simpleName, check is RouteCheck.Rejected)
        val rejected = check as RouteCheck.Rejected
        assertTrue(rejected.reason, rejected.reason.contains("逐段直线和"))
        assertTrue("detail 要数得出途经点有几个：${rejected.detail}", rejected.detail.contains("via=1"))
    }

    @Test
    fun `非法坐标与全点重合都被拒收`() {
        assertFalse(RealRoadRouteValidator.isValidGeo(Geo(0.0, 0.0)))
        assertFalse(RealRoadRouteValidator.isValidGeo(Geo(Double.NaN, 106.6)))
        assertFalse(RealRoadRouteValidator.isValidGeo(Geo(120.0, 106.6)))
        assertTrue(
            RealRoadRouteValidator.validate("amap", listOf(Geo(29.6, 106.6), Geo(91.0, 106.6))) is RouteCheck.Rejected,
        )
        val same = listOf(Geo(29.6, 106.6), Geo(29.6, 106.6), Geo(29.6, 106.6))
        val check = RealRoadRouteValidator.validate("amap", same)
        assertTrue(check is RouteCheck.Rejected)
        assertTrue((check as RouteCheck.Rejected).reason.contains("重合"))
    }

    @Test
    fun `正常道路折线通过校验并带上同源里程与耗时`() {
        val check = RealRoadRouteValidator.validate(
            sourceRaw = "amap",
            points = road,
            roadDistanceMeters = 9_200,
            roadEtaSeconds = 1_560,
            stationGeos = stations,
        )
        assertTrue(check::class.simpleName, check is RouteCheck.Accepted)
        val route = (check as RouteCheck.Accepted).route
        assertEquals(RouteSource.AMAP, route.source)
        assertEquals(9_200, route.roadDistanceMeters)
        assertEquals(1_560, route.roadEtaSeconds)
        assertTrue("绕路比应大于 1", route.windiness > 1.0)
        assertTrue("首末直线距离应小于道路里程", route.straightDistanceMeters < route.roadDistanceMeters)
    }

    @Test
    fun `来源没给里程时保持为 0 而不是补一个直线值`() {
        val check = RealRoadRouteValidator.validate("amap", road)
        assertTrue(check is RouteCheck.Accepted)
        val route = (check as RouteCheck.Accepted).route
        assertEquals(0, route.roadDistanceMeters)
        // 直线距离仍然算得出来，但它只用于合理性校验，不会被当成里程展示
        assertTrue(route.straightDistanceMeters > 0)
    }

    @Test
    fun `RouteRepository 缓存通过校验的路线并在来源不可信时清掉`() {
        val ok = RouteRepository.resolve("driverRoute:7", "amap", road, roadDistanceMeters = 9_200, stationGeos = stations)
        assertTrue(ok is RouteCheck.Accepted)
        val hit = RouteRepository.cached("driverRoute:7")
        assertTrue("刚写进去的必须读得到条目", hit is RouteCacheRead.Hit)
        // 缓存里留的是后端给的**原文**，不是归一后的枚举名：披露句要说"后端当时说了什么"
        assertEquals("amap", (hit as RouteCacheRead.Hit).entry.providerRaw)

        val bad = RouteRepository.resolve("driverRoute:7", "euclidean", road, roadDistanceMeters = 9_200)
        assertTrue(bad is RouteCheck.Rejected)
        // 刷新后来源变成不可信：旧缓存必须失效，否则"重新规划"等于什么都没做
        assertTrue(RouteRepository.cached("driverRoute:7") is RouteCacheRead.Absent)
    }

    @Test
    fun `后端自己说没有真实道路时既不写入也删掉旧条目`() {
        val ok = RouteRepository.resolve("driverRoute:8", "amap", road, roadDistanceMeters = 9_200)
        assertTrue(ok is RouteCheck.Accepted)
        assertTrue(RouteRepository.cached("driverRoute:8") is RouteCacheRead.Hit)
        // 同一条 key 这次后端 routeAvailable=false：校验器仍然认这段几何，但缓存不能留着它
        val rejected = RouteRepository.resolve(
            "driverRoute:8", "amap", road, roadDistanceMeters = 9_200, serverAvailable = false,
        )
        assertTrue(rejected is RouteCheck.Accepted)
        assertTrue(RouteRepository.cached("driverRoute:8") is RouteCacheRead.Absent)
    }

    @Test
    fun `过期条目读不到几何并且当场被删掉`() {
        val written = RouteRepository.resolve("driverRoute:9", "amap", road, roadDistanceMeters = 9_200, nowMillis = 1_000L)
        assertTrue(written is RouteCheck.Accepted)
        // 边界：指令原话是"当前时间 < expiresAt 才算新鲜"，相等即过期
        val expiresAt = 1_000L + RouteRepository.MAX_AGE_MILLIS
        assertTrue(RouteRepository.cached("driverRoute:9", expiresAt) is RouteCacheRead.Stale)
        assertTrue("读到过期条目应当已被删除", RouteRepository.cached("driverRoute:9") is RouteCacheRead.Absent)
        assertTrue(RouteRepository.size() == 0)
    }

    @Test
    fun `缓存条数受控且淘汰最旧的一条`() {
        (1..14).forEach { i ->
            RouteRepository.resolve("busLine:$i", "amap", road, roadDistanceMeters = 9_200)
        }
        assertEquals(RouteRepository.MAX_ENTRIES, RouteRepository.size())
        assertTrue("最旧的 busLine:1 应被淘汰", RouteRepository.cached("busLine:1") is RouteCacheRead.Absent)
        assertTrue(RouteRepository.cached("busLine:14") is RouteCacheRead.Hit)
    }

    @Test
    fun `重庆范围内 WGS84 与 GCJ02 换算偏移合理且可回退`() {
        val wgs = Geo(29.60, 106.60)
        val gcj = CoordinateTransform.wgs84ToGcj02(wgs)
        val offset = com.shanxiang.buspost.data.model.GeoMath.distance(wgs, gcj)
        assertTrue("境内偏移应在 0~800 米，实测 ${offset.toInt()}m", offset in 1.0..800.0)
        val back = CoordinateTransform.gcj02ToWgs84(gcj)
        assertTrue(
            "往返误差应在 1 米内，实测 ${com.shanxiang.buspost.data.model.GeoMath.distance(wgs, back).toInt()}m",
            com.shanxiang.buspost.data.model.GeoMath.distance(wgs, back) < 1.0,
        )
    }

    @Test
    fun `境外坐标不做偏移`() {
        val paris = Geo(48.8566, 2.3522)
        assertEquals(paris, CoordinateTransform.wgs84ToGcj02(paris))
        assertFalse(CoordinateTransform.inChina(paris))
    }
}
