package com.shanxiang.buspost.core.navigation

import com.shanxiang.buspost.core.map.RouteCheck
import com.shanxiang.buspost.core.map.RouteSource
import com.shanxiang.buspost.data.model.Geo
import com.shanxiang.buspost.data.model.MissionStop
import com.shanxiang.buspost.data.model.RouteGuide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 导航引擎判据层的契约测试（赛道第 8 步 / 任务 #49）。
 *
 * 与 `core/map/RealRoadRouteValidatorTest` 的分工：那份钉"后端下发的折线能不能上路"，
 * 这份钉**高德引擎这一路的产物同样没有第二条路**—— 状态只来自回调、算路结果同样过闸门、
 * 引擎不可用时一个数都不造。
 *
 * 全部判据都在 JVM 纯层（无 Robolectric）：`EngineReadiness` / `NavigationGuidance` /
 * `NavigationSession` / `readinessOf` / `guidanceOf` / `naviRouteCheck` / `nextNavigableStop` /
 * `AcceptedRouteSession` / `routeBindingOf` / `turnIconSpeechOf` / `naviVoiceTypeNameOf` /
 * `LaneFrame` / `laneFrameOf` / `laneActionNameOf` / `laneCellLabel` / `laneDisplayTextOf` /
 * `EngineRecalcDisplay` / `engineRecalcTextOf` / `guidanceSwitchOffText`
 * 都不引 `android.*` 与 `com.amap.*`，真正调 SDK 的 `AmapNavigationEngine` 只薄薄包一层。
 * 这与 `nav/ShellDecision.kt`、`ui/driver/WorkbenchRoleReconcile` 同一手法。
 */
class NavigationContractTest {

    /** 重庆江津一带的一段"道路"：四点、有绕路，首末直线约 7.3 公里 */
    private val road = listOf(
        Geo(29.60, 106.60), Geo(29.61, 106.62), Geo(29.63, 106.63), Geo(29.65, 106.65),
    )

    // ---------- 一、引擎可用性：三态，且顺序固定 ----------

    /**
     * 初始化结论是**进程级事实**（[NaviInitState]）：`AMapNavi` 是进程单例、
     * `onInitNaviSuccess` 一生只回调一次，而引擎实例随页面/滚动重建 —— 结论若只存实例，
     * 新实例必然等一个不会再来的回调，页面把"已就绪"念成"初始化中"、按钮永久置灰
     * （2026-10-09 模拟器实测复现后修正）。这条钉住：写进进程级的结论，任何读者都能读到。
     */
    @Test
    fun `初始化结论写入进程级后任何读者可读`() {
        NaviInitState.write(true)
        assertEquals(true, NaviInitState.read())
        NaviInitState.write(false)
        assertEquals(false, NaviInitState.read())
        // 还原，避免污染同进程内其它用例的初值假设
        NaviInitState.write(true)
    }

    /** 就绪的唯一形状：类在 + key 在 + 高德回了成功 */
    @Test
    fun `只有高德回了初始化成功才算就绪`() {
        assertEquals(EngineReadiness.Ready, readinessOf(sdkPresent = true, keyConfigured = true, initResult = true))
        assertTrue(EngineReadiness.Ready.canNavigate)
    }

    /**
     * 回调还没回来 ≠ 失败。
     * 这一条是分界的根：把"初始化中"念成"这台手机不能导航"，就是把未验证说成结论。
     */
    @Test
    fun `初始化中不是失败，也不能被读成可用`() {
        val initializing = readinessOf(sdkPresent = true, keyConfigured = true, initResult = null)
        assertEquals(EngineReadiness.Initializing, initializing)
        assertFalse("Initializing 绝不能 canNavigate", initializing.canNavigate)
        assertEquals("导航引擎初始化中", initializing.userText)
    }

    /**
     * 顺序判据：**先问类、再问 key、最后才问回调结果**。
     * 缺组件的设备不能报"没配 key"，缺 key 的设备不能一直挂着"初始化中"——
     * 那会把配置缺失念成进行中，用户等一个永远不会来的回调。
     */
    @Test
    fun `缺组件优先于缺配置，缺配置优先于等回调`() {
        val noSdk = readinessOf(sdkPresent = false, keyConfigured = false, initResult = null)
        assertTrue(noSdk is EngineReadiness.Blocked)
        assertTrue((noSdk as EngineReadiness.Blocked).reason, noSdk.reason.contains("导航组件未随包发布"))

        val noKey = readinessOf(sdkPresent = true, keyConfigured = false, initResult = null)
        assertTrue(noKey is EngineReadiness.Blocked)
        assertTrue((noKey as EngineReadiness.Blocked).reason, noKey.reason.contains("没有配置高德 key"))

        val initFailed = readinessOf(sdkPresent = true, keyConfigured = true, initResult = false)
        assertTrue(initFailed is EngineReadiness.Blocked)
        assertTrue((initFailed as EngineReadiness.Blocked).reason, initFailed.reason.contains("初始化失败"))
    }

    /** Blocked 必须带真实原因：空原因等于"不支持"三个字，用户不知道缺什么 */
    @Test
    fun `不可用状态必须带可展示的原因`() {
        val blocked = readinessOf(sdkPresent = false, keyConfigured = true, initResult = null) as EngineReadiness.Blocked
        assertTrue(blocked.reason.isNotBlank())
        assertTrue(blocked.detail.isNotBlank())
    }

    /**
     * 第四态：**原生库还在后台加载**。
     *
     * 为什么要单列这一格（2026-10-09 首启 ANR 的产物）：`AMapNavi.getInstance()` 会把 41MB 的
     * `libAMapSDK_NAVI` 映射进进程，它原来跑在 Compose `remember{}`（主线程）里，模拟器实测
     * 23~29 秒 → 一次 ANR。挪到后台线程之后，"还在加载"成了一段真实存在的时间。
     * 这段时间的结论必须与"已发起初始化、等高德回调"分开：
     * 后者已经在等 SDK 回话（`initResult=null` 有意义），前者连 SDK 都还没碰到。
     *
     * 同时钉住"加载中不是坏了"：它绝不能被算进 `engineBlocked`，否则卡片会被染成故障色。
     */
    @Test
    fun `原生库加载中是第四态，既不可点也不算引擎故障`() {
        assertFalse("加载中绝不能 canNavigate", EngineReadiness.Loading.canNavigate)
        assertEquals("导航引擎正在加载", EngineReadiness.Loading.userText)

        val gate = navigationGateOf(EngineReadiness.Loading, hasOrigin = true, hasNavigableDestination = true)
        assertEquals(NavigationGateCode.LOADING, gate.code)
        assertFalse(gate.canNavigate)
        assertFalse("加载中不等于引擎故障，不许染故障色", gate.engineBlocked)
        assertTrue("必须告诉司机这是暂时的", gate.userText.contains("正在加载"))
        assertTrue("必须说明不需要重进", gate.detail.contains("不需要"))
    }

    /**
     * 三态用词不许混：加载中 / 初始化中 / 不可用 各自有各的话。
     * 混用就是把"还没轮到"念成"已经发出去了"或"这台机器不行"。
     */
    @Test
    fun `加载与初始化与不可用三态文案互不相同`() {
        val loading = EngineReadiness.Loading.userText
        val initializing = EngineReadiness.Initializing.userText
        val blocked = readinessOf(sdkPresent = false, keyConfigured = true, initResult = null).userText
        assertEquals(3, setOf(loading, initializing, blocked).size)
        assertFalse("加载中不许被说成不可用", loading.contains("不可用"))
        assertFalse("加载中不许被说成初始化中", loading.contains("初始化"))
    }

    // ---------- 一之二、导航入口闸门：能不能点由一个结论说（F-3 ④⑫） ----------

    /**
     * 只有"引擎就绪 + 有起点 + 有调度给的目标点"这一种组合可点。
     *
     * 这一条钉的是 2026-10-02 实测到的破口：页面原来写 `enabled = !blocked && …`，
     * 于是 `Initializing`（`blocked` 为假）被读成可点，而判据层 `canNavigate` 对它说的是不可点 ——
     * 同一个问题两个答案。现在入口只许读 [navigationGateOf]，两侧由同一批用例钉住。
     */
    @Test
    fun `只有引擎就绪且这一单有起点与目标点才可点`() {
        val ready = navigationGateOf(EngineReadiness.Ready, hasOrigin = true, hasNavigableDestination = true)
        assertEquals(NavigationGateCode.READY, ready.code)
        assertTrue(ready.canNavigate)

        val noOrigin = navigationGateOf(EngineReadiness.Ready, hasOrigin = false, hasNavigableDestination = true)
        assertEquals(NavigationGateCode.NO_ROUTE, noOrigin.code)
        assertFalse(noOrigin.canNavigate)

        val noDestination = navigationGateOf(EngineReadiness.Ready, hasOrigin = true, hasNavigableDestination = false)
        assertEquals(NavigationGateCode.NO_ROUTE, noDestination.code)
        assertFalse(noDestination.canNavigate)

        val initializing = navigationGateOf(
            readinessOf(sdkPresent = true, keyConfigured = true, initResult = null),
            hasOrigin = true,
            hasNavigableDestination = true,
        )
        assertEquals(NavigationGateCode.INITIALIZING, initializing.code)
        assertFalse("初始化中绝不能被读成可点", initializing.canNavigate)
    }

    /** 三种"引擎不能用"必须分得开：缺组件、缺配置、高德回了失败，修的是三个不同的东西 */
    @Test
    fun `成因码分得开缺组件缺配置与初始化失败`() {
        val notInstalled = navigationGateOf(
            readinessOf(sdkPresent = false, keyConfigured = true, initResult = null),
            hasOrigin = true,
            hasNavigableDestination = true,
        )
        assertEquals(NavigationGateCode.NOT_INSTALLED, notInstalled.code)

        val sdkUnavailable = navigationGateOf(
            readinessOf(sdkPresent = true, keyConfigured = false, initResult = null),
            hasOrigin = true,
            hasNavigableDestination = true,
        )
        assertEquals(NavigationGateCode.SDK_UNAVAILABLE, sdkUnavailable.code)

        val error = navigationGateOf(
            readinessOf(sdkPresent = true, keyConfigured = true, initResult = false),
            hasOrigin = true,
            hasNavigableDestination = true,
        )
        assertEquals(NavigationGateCode.ERROR, error.code)
        assertTrue(error.engineBlocked)
    }

    /**
     * 顺序判据：引擎本身不可用时，不许把原因说成"这一单没有可导的路线"。
     *
     * 反过来的话，一台没装导航组件的机器会一直被告知"调度还没给任务点"——
     * 那是把环境问题念成业务问题，司机会在页面上等一个后端永远不会来的点。
     */
    @Test
    fun `引擎缺组件时不报调度没给目标`() {
        val gate = navigationGateOf(
            readinessOf(sdkPresent = false, keyConfigured = false, initResult = null),
            hasOrigin = false,
            hasNavigableDestination = false,
        )
        assertEquals(NavigationGateCode.NOT_INSTALLED, gate.code)
        assertFalse(gate.reason.contains("调度"))
    }

    /** `NO_ROUTE` 的两个成因要分别说清缺的是起点还是目标，且不算"引擎坏了" */
    @Test
    fun `没有可导路线时分清缺起点还是缺目标`() {
        val missingOrigin = navigationGateOf(EngineReadiness.Ready, hasOrigin = false, hasNavigableDestination = true)
        assertTrue(missingOrigin.reason, missingOrigin.reason.contains("定位"))
        assertFalse(missingOrigin.engineBlocked)

        val missingStop = navigationGateOf(EngineReadiness.Ready, hasOrigin = true, hasNavigableDestination = false)
        assertTrue(missingStop.reason, missingStop.reason.contains("调度"))
        assertFalse(missingStop.engineBlocked)
    }

    /** 不可点的措辞只有一个出口：都带"导航暂不可用"，且必须跟着具体原因（禁止只说"不支持"） */
    @Test
    fun `不可点的每个状态都说得出缺什么`() {
        val gates = listOf(
            navigationGateOf(EngineReadiness.Ready, hasOrigin = false, hasNavigableDestination = true),
            navigationGateOf(EngineReadiness.Ready, hasOrigin = true, hasNavigableDestination = false),
            navigationGateOf(EngineReadiness.Initializing, hasOrigin = true, hasNavigableDestination = true),
            navigationGateOf(
                readinessOf(sdkPresent = false, keyConfigured = true, initResult = null),
                hasOrigin = true,
                hasNavigableDestination = true,
            ),
            navigationGateOf(
                readinessOf(sdkPresent = true, keyConfigured = true, initResult = false),
                hasOrigin = true,
                hasNavigableDestination = true,
            ),
        )
        gates.forEach { gate ->
            assertFalse(gate.canNavigate)
            assertTrue("${gate.code} 的 reason 为空", gate.reason.isNotBlank())
            assertTrue("${gate.code} 的措辞没走唯一出口", gate.userText.startsWith("导航暂不可用："))
            assertTrue("${gate.code} 缺 detail", gate.detail.isNotBlank())
        }
        assertFalse(navigationGateOf(EngineReadiness.Ready, true, true).userText.startsWith("导航暂不可用"))
    }

    /** `Initializing` 不是故障：卡片底色按"引擎侧坏了没有"分，未就绪里只有三种算坏 */
    @Test
    fun `初始化中与缺组件在界面上不是一回事`() {
        val initializing = navigationGateOf(EngineReadiness.Initializing, hasOrigin = true, hasNavigableDestination = true)
        assertFalse("初始化中不许被念成引擎坏了", initializing.engineBlocked)
        assertTrue(initializing.userText, initializing.userText.contains("初始化"))

        val broken = navigationGateOf(
            readinessOf(sdkPresent = false, keyConfigured = true, initResult = null),
            hasOrigin = true,
            hasNavigableDestination = true,
        )
        assertTrue(broken.engineBlocked)
    }

    /** 入口结论与替身引擎的回执是同一个原因：页面显示的和引擎答的是同一句话，不留两份措辞 */
    @Test
    fun `替身引擎拒绝算路的原因与入口闸门一致`() = kotlinx.coroutines.runBlocking {
        val readiness = readinessOf(sdkPresent = false, keyConfigured = true, initResult = null)
        val engine = BlockedNavigationEngine(readiness)
        val gate = navigationGateOf(engine.readiness.value, hasOrigin = true, hasNavigableDestination = true)
        assertFalse(gate.canNavigate)
        val check = engine.calculateRoute(Geo(29.60, 106.60), Geo(29.65, 106.65))
        assertTrue(check is RouteCheck.Rejected)
        assertEquals(gate.reason, (check as RouteCheck.Rejected).reason)
    }

    // ---------- 二、一帧指引：只许把"没给"归一到 0 / -1 / 空 ----------

    /** 正常一帧：数值原样透出，不做任何"看起来更完整"的加工 */
    @Test
    fun `高德给数的帧原样透出`() {
        val frame = guidanceOf(
            curStep = 3,
            pathRetainDistanceMeters = 4200,
            pathRetainTimeSeconds = 600,
            curStepRetainDistanceMeters = 300,
            currentRoadName = "珞璜大道",
            nextRoadName = "兴珞支路",
            iconType = 4,
            remainLightCount = 2,
        )
        assertEquals(3, frame.sdkStepIndex)
        assertEquals(4200, frame.remainMeters)
        assertEquals(600, frame.remainSeconds)
        assertEquals("珞璜大道", frame.currentRoad)
        assertTrue(frame.hasStepProgress)
        assertTrue(frame.hasRemainDistance)
    }

    /**
     * 反向那一半：SDK 没给 = 0 / -1 / 空串，页面据此显示"高德没给"。
     * 归一函数**只允许这一个方向**—— 填任何经验值、直线值、上一帧的值都是假导航。
     */
    @Test
    fun `高德没给的字段一律归零而不是补数`() {
        val frame = guidanceOf(
            curStep = -1,
            pathRetainDistanceMeters = 0,
            pathRetainTimeSeconds = 0,
            curStepRetainDistanceMeters = 0,
            currentRoadName = null,
            nextRoadName = null,
            iconType = -1,
            remainLightCount = -1,
        )
        assertEquals(-1, frame.sdkStepIndex)
        assertFalse("没有步骤号就不能显示第 N 步", frame.hasStepProgress)
        assertFalse("剩余里程 0 表示没给，不是\"已经到了\"", frame.hasRemainDistance)
        assertEquals(0, frame.remainMeters)
        assertEquals("", frame.currentRoad)
        assertEquals("", frame.nextRoad)
        assertEquals(0, frame.remainTrafficLights)
    }

    /** 负数读数塌成 0，但不塌成"绝对值"：符号是数据坏了的信号，不是方向 */
    @Test
    fun `异常负值只抬到零不取绝对值`() {
        val frame = guidanceOf(
            curStep = 0,
            pathRetainDistanceMeters = -500,
            pathRetainTimeSeconds = -60,
            curStepRetainDistanceMeters = -10,
            currentRoadName = "",
            nextRoadName = "",
            iconType = 0,
            remainLightCount = -3,
        )
        assertEquals(0, frame.remainMeters)
        assertEquals(0, frame.remainSeconds)
        assertEquals(0, frame.currentStepRemainMeters)
        assertEquals(0, frame.remainTrafficLights)
    }

    // ---------- 三、算路产物：同一道闸门，不做第二次判断 ----------

    /** 通过的那条路：来源固定是 AMAP（真实来源白名单内），里程与折线同源 */
    @Test
    fun `高德折线走同一道真实道路闸门`() {
        val check = naviRouteCheck(points = road, roadDistanceMeters = 9_500, roadEtaSeconds = 900)
        assertTrue(check.toString(), check is RouteCheck.Accepted)
        val route = (check as RouteCheck.Accepted).route
        assertEquals(RouteSource.AMAP, route.source)
        assertEquals(9_500, route.roadDistanceMeters)
        assertEquals(900, route.roadEtaSeconds)
    }

    /** 点数不足：一帧都凑不出线，就是没有线 */
    @Test
    fun `高德只回一个点时判为没有道路`() {
        val check = naviRouteCheck(points = listOf(Geo(29.60, 106.60)), roadDistanceMeters = 1_000, roadEtaSeconds = 60)
        assertTrue(check is RouteCheck.Rejected)
        assertTrue((check as RouteCheck.Rejected).reason, (check).reason.contains("折线"))
    }

    /** 所有点重合 = 退化成一个点，画出来什么都没有 */
    @Test
    fun `重合点折线被拒`() {
        val flat = List(5) { Geo(29.60, 106.60) }
        val check = naviRouteCheck(points = flat, roadDistanceMeters = 100, roadEtaSeconds = 30)
        assertTrue(check is RouteCheck.Rejected)
    }

    /** 非法坐标（越界 / 0,0）不进正式链路 */
    @Test
    fun `非法坐标折线被拒`() {
        val bad = listOf(Geo(0.0, 0.0), Geo(29.65, 106.65))
        val check = naviRouteCheck(points = bad, roadDistanceMeters = 7_000, roadEtaSeconds = 600)
        assertTrue(check is RouteCheck.Rejected)
        assertTrue((check as RouteCheck.Rejected).reason, check.reason.contains("非法坐标"))
    }

    /**
     * "里程比直线还短"这条判据对高德同样成立：
     * 高德把直线值当里程给（或我们把 `GeoMath.distance` 当里程报）都会被拒。
     */
    @Test
    fun `里程小于首末直线距离时同样拒收`() {
        val straight = com.shanxiang.buspost.data.model.GeoMath.distance(road.first(), road.last()).toInt()
        val check = naviRouteCheck(points = road, roadDistanceMeters = straight / 2, roadEtaSeconds = 60)
        assertTrue(check is RouteCheck.Rejected)
        assertTrue((check as RouteCheck.Rejected).reason, check.reason.contains("直线距离"))
    }

    /**
     * 坐标系纪律（审计 §17 的二次偏移）：高德 `getCoordList()` 本身就是 GCJ-02，
     * 判据层声明 GCJ-02 之后 `gcj02Points` 必须与原点**逐位相同**。
     * 谁在这里再套一次换算，这条用例就红。
     */
    @Test
    fun `高德折线不做任何二次换算`() {
        val check = naviRouteCheck(points = road, roadDistanceMeters = 9_500, roadEtaSeconds = 900)
        val route = (check as RouteCheck.Accepted).route
        assertEquals(road, route.points)
        assertEquals(road, route.gcj02Points)
        assertEquals(com.shanxiang.buspost.core.map.CoordinateSystem.GCJ02, route.crs)
    }

    // ---------- 四、替身引擎：一条请求都不发，一帧指引都不造 ----------

    /** 未就绪时的替身：算路、开始导航都只回同一句真实原因 */
    @Test
    fun `替身引擎不产生任何导航产物`() = kotlinx.coroutines.runBlocking {
        val blocked = BlockedNavigationEngine(
            readinessOf(sdkPresent = false, keyConfigured = true, initResult = null),
        )
        assertNull(blocked.guidance.value)
        assertFalse(blocked.readiness.value.canNavigate)

        val check = blocked.calculateRoute(Geo(29.60, 106.60), Geo(29.65, 106.65))
        assertTrue(check is RouteCheck.Rejected)
        val session = blocked.startGpsNavi(voiceOn = false)
        assertTrue(session is NavigationSession.Refused)
        assertEquals((blocked.readiness.value as EngineReadiness.Blocked).reason, (session as NavigationSession.Refused).reason)

        // 替身的 stopNavi / release 必须安全可重入：页面退出时会对任何引擎调用它们
        blocked.stopNavi()
        blocked.release()
        assertNull(blocked.guidance.value)
    }

    /** Running 只能由 SDK 的返回值构成：naviType 与 startedBySdk 都来自外面，不本机推断 */
    @Test
    fun `会话状态里的 startedBySdk 不能由页面自己填 true`() {
        val refused = NavigationSession.Refused("高德没有开始导航（startNavi 返回 false）")
        assertTrue(refused.reason.contains("startNavi"))
        // Running 的两个字段没有默认值：写不出"漏传即视为已启动"的代码
        val running = NavigationSession.Running(naviType = 2, startedBySdk = true)
        assertEquals(2, running.naviType)
        assertTrue(running.startedBySdk)
    }

    // ---------- 五、目的地：调度决定去哪里 ----------

    private fun guideWith(vararg stops: MissionStop) = RouteGuide(
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
        dataSource = "",
        stops = stops.toList(),
    )

    /** 顺序只认后端 `visitSequence`：数组里靠后但序号更小的点才是下一站 */
    @Test
    fun `目的地按后端序号不按数组顺序`() {
        val later = MissionStop(stationId = 11, name = "德感", geo = Geo(29.65, 106.65), visitSequence = 5, status = 0)
        val sooner = MissionStop(stationId = 12, name = "珞璜", geo = Geo(29.60, 106.60), visitSequence = 2, status = 0)
        val next = nextNavigableStop(guideWith(later, sooner))
        assertEquals(12L, next!!.stationId)
        assertEquals(2, next!!.visitSequence)
    }

    /** 已完成的点跳过；失败(8) 的点**不**跳过 —— 后端没说不用去，客户端不能替调度改路线 */
    @Test
    fun `只跳过后端标记为已完成的点`() {
        val done = MissionStop(stationId = 21, name = "已送达", geo = Geo(29.60, 106.60), visitSequence = 1, status = 7)
        val failed = MissionStop(stationId = 22, name = "派送失败", geo = Geo(29.62, 106.62), visitSequence = 2, status = 8)
        val pending = MissionStop(stationId = 23, name = "支坪", geo = Geo(29.65, 106.65), visitSequence = 3, status = 0)
        assertEquals(22L, nextNavigableStop(guideWith(done, failed, pending))!!.stationId)
    }

    /** 没有坐标的任务点不能当目的地：拿 (0,0) 算路会导向几内亚湾 */
    @Test
    fun `没有坐标的任务点被跳过`() {
        val noGeo = MissionStop(stationId = 31, name = "坐标未给", geo = Geo(0.0, 0.0), visitSequence = 1, status = 0)
        val real = MissionStop(stationId = 32, name = "江津", geo = Geo(29.64, 106.63), visitSequence = 2, status = 0)
        assertEquals(32L, nextNavigableStop(guideWith(noGeo, real))!!.stationId)
    }

    /** 调度什么都没给 = 导航不知道该去哪，返回 null 由页面如实说，不拿终点站凑一个 */
    @Test
    fun `调度没给任务点时没有目的地`() {
        assertNull(nextNavigableStop(guideWith()))
        val allDone = MissionStop(stationId = 41, name = "完成", geo = Geo(29.60, 106.60), visitSequence = 1, status = 7)
        assertNull(nextNavigableStop(guideWith(allDone)))
    }

    // ---------- 六、§九 路线绑定：startNavi 启动的必须是刚验过的那一条（F-3 ⑭ / V21-09）----------

    private val acceptedSession = AcceptedRouteSession(
        destinationLat = 29.65,
        destinationLng = 106.65,
        coordCount = 4,
        roadDistanceMeters = 9_200,
    )

    /**
     * §九 硬条件"没有 accepted route：禁止 startGpsNavi()"。
     * 这一条拦的是"卡片上还画着上一次算出来的线，本机其实没有凭证"那种状态。
     */
    @Test
    fun `本机没有一次通过验证的算路时不许开始导航`() {
        val binding = routeBindingOf(accepted = null, currentPath = SdkPathShape(coordCount = 4, roadDistanceMeters = 9_200))
        assertTrue(binding is RouteBinding.Unbound)
        assertTrue((binding as RouteBinding.Unbound).reason.contains("还没有一次通过验证的算路"))
    }

    /**
     * 三种成因各说各的话：没算过 / 高德现在没有路径 / 有路径但不是那一条。
     * 合成一句"不能导航"就把该重新算、还是该等高德回路径说丢了（与 N-t 分码同一条理由）。
     */
    @Test
    fun `三条拒收成因不共用一句话`() {
        val none = routeBindingOf(accepted = null, currentPath = null) as RouteBinding.Unbound
        val noPath = routeBindingOf(accepted = acceptedSession, currentPath = null) as RouteBinding.Unbound
        val other = routeBindingOf(accepted = acceptedSession, currentPath = SdkPathShape(coordCount = 9, roadDistanceMeters = 300)) as RouteBinding.Unbound
        assertTrue(none.reason.contains("还没有一次通过验证的算路"))
        assertTrue(noPath.reason.contains("没有路径几何"))
        assertTrue(other.reason.contains("不是同一条"))
        assertEquals("三条成因必须三句不同的话", 3, listOf(none.reason, noPath.reason, other.reason).toSet().size)
        // "高德现在没有路径"时要把本机那份记录带进 detail：否则现场只剩一句"没有路"，查不出是哪一次算路对不上
        assertTrue(noPath.detail.contains("to=29.650000,106.650000"))
    }

    /** 点数与道路米数都读得回来且一致，才允许把 startNavi() 发出去 */
    @Test
    fun `同一条路的两项实测形状一致才绑定成功`() {
        val binding = routeBindingOf(accepted = acceptedSession, currentPath = SdkPathShape(coordCount = 4, roadDistanceMeters = 9_200))
        assertEquals(RouteBinding.Bound, binding)
    }

    /**
     * 点数相同、里程不同也必须拒。
     * 拦的是"绑定只比点数"那种偷懒写法：高德偏航重算后完全可能给回一条点数巧合相同的路径。
     */
    @Test
    fun `点数相同但道路米数不同不算同一条`() {
        val binding = routeBindingOf(accepted = acceptedSession, currentPath = SdkPathShape(coordCount = 4, roadDistanceMeters = 9_201))
        assertTrue(binding is RouteBinding.Unbound)
        val detail = (binding as RouteBinding.Unbound).detail
        assertTrue(detail, detail.contains("session=to=29.650000,106.650000|pts=4|m=9200"))
        assertTrue(detail, detail.contains("now=pts=4|m=9201"))
    }

    /**
     * `routeFingerprint` 就是四个实测数的定长拼接，不是摘要、不是高德给的图版本号。
     * 钉住字面形状：一旦有人换成哈希，这台机器上就没有任何来源能解释那串字符是什么（V21-13 的 graphVersion 三面 0 生产者就是前车之鉴）。
     */
    @Test
    fun `指纹就是实测数的拼接`() {
        assertEquals("to=29.650000,106.650000|pts=4|m=9200", acceptedSession.routeFingerprint)
        assertFalse(acceptedSession.routeFingerprint.contains("sha"))
        assertFalse(acceptedSession.routeFingerprint.contains("md5"))
    }

    /**
     * §九"任务发生变化 → routeFingerprint 变化 → 旧 session 作废"的依据。
     * 换成另一个任务点，即使高德两次给回**完全相同**的几何，指纹也必须不同 ——
     * 几何一项都不变时，只有终点能说明"这已经不是为这一单算的路了"。
     */
    @Test
    fun `终点变了指纹就变即使几何完全相同`() {
        val next = acceptedSession.copy(destinationLat = 29.70, destinationLng = 106.70)
        assertNotEquals(acceptedSession.routeFingerprint, next.routeFingerprint)
        assertEquals(acceptedSession.coordCount, next.coordCount)
        assertEquals(acceptedSession.roadDistanceMeters, next.roadDistanceMeters)
    }

    /**
     * 记录里的几何就是闸门验过的那份：过 [naviRouteCheck] 的点数与里程原样进记录，
     * 不再从别处（本机记忆、后端折线）取第二个数 —— 那会让绑定检查比的是两份不同的东西。
     */
    @Test
    fun `记录里的几何就是闸门验过的那份`() {
        val accepted = naviRouteCheck(points = road, roadDistanceMeters = 9_500, roadEtaSeconds = 900) as RouteCheck.Accepted
        val recorded = AcceptedRouteSession(
            destinationLat = 29.65,
            destinationLng = 106.65,
            coordCount = accepted.route.points.size,
            roadDistanceMeters = accepted.route.roadDistanceMeters,
        )
        assertEquals(road.size, recorded.coordCount)
        assertEquals(9_500, recorded.roadDistanceMeters)
        assertEquals(RouteBinding.Bound, routeBindingOf(recorded, SdkPathShape(coordCount = road.size, roadDistanceMeters = 9_500)))
    }

    /**
     * 替身引擎走不到绑定这一步：它的算路恒为拒收，所以永远没有 accepted 记录可绑。
     * 它给出的仍是一开始那句引擎成因，不是"没有路径几何"那种本机的话。
     */
    @Test
    fun `替身引擎的开始导航只回引擎未就绪那一句`() = kotlinx.coroutines.runBlocking {
        val blocked = BlockedNavigationEngine(readinessOf(sdkPresent = true, keyConfigured = true, initResult = false))
        val started = blocked.startGpsNavi(voiceOn = true)
        assertTrue(started is NavigationSession.Refused)
        assertEquals(
            (blocked.readiness.value as EngineReadiness.Blocked).reason,
            (started as NavigationSession.Refused).reason,
        )
        assertNull(blocked.guidance.value)
        // 替身也没有播报可给：voiceOn=true 不能让它凭空造出一句高德的话
        assertNull(blocked.voice.value)
    }

    // ---------- 八、赛道 ⑨：转向话术表与播报类型表（值域来自 javap，不是本机整理） ----------

    /**
     * 抽查若干行的**字面**话术：改一个字都要有用例红。
     *
     * 钉这几行不是因为它们最常出现，而是因为它们各自代表一类容易写错的方向：
     * 0（高德明明给了"无动作"）、1（通用 ≠ 直行）、8/19（两种掉头方向相反）、
     * 15（到达目的地 ≠ 到达途经点）、53/54（路口与平路口）、65/66（并线，值不在 0..54 的连号段里）。
     */
    @Test
    fun `转向码表的话术是逐字钉住的`() {
        assertEquals("无动作", turnIconSpeechOf(0))
        assertEquals("通用（高德没给具体转向）", turnIconSpeechOf(1))
        assertEquals("左转掉头", turnIconSpeechOf(8))
        assertEquals("直行", turnIconSpeechOf(9))
        assertEquals("右转掉头", turnIconSpeechOf(19))
        assertEquals("到达途经点", turnIconSpeechOf(10))
        assertEquals("到达目的地", turnIconSpeechOf(15))
        assertEquals("通过路口", turnIconSpeechOf(53))
        assertEquals("通过平路口", turnIconSpeechOf(54))
        assertEquals("向左并线", turnIconSpeechOf(65))
        assertEquals("向右并线", turnIconSpeechOf(66))
    }

    /**
     * 两头都钉：**已定义的一个不缺**，**表外的一个不编**。
     *
     * 少一个已定义的值，页面会对着高德明明给了的转向码说"这张表里没有"，那是把"有"念成"没有"；
     * 多给一个表外的值（就近挑个箭头顶上），就是旧屏 `laneIcon()` 那种按字面猜的假引导。
     * 值域本身是 `javap -constants com.amap.api.navi.enums.IconType` 在 10.0.600 上读出来的
     * `0..54` 连号 + `65`/`66`，55..64 在该版本里不存在。
     */
    @Test
    fun `IconType 实测值域一个不缺且表外一个不编`() {
        val defined = (0..54).toSet() + setOf(65, 66)
        assertEquals(57, defined.size)
        defined.forEach {
            assertNotNull("码 $it 在高德的 IconType 里有定义，表里不能缺它", turnIconSpeechOf(it))
        }
        val outside = listOf(-1, 67, 100, 999) + (55..64).toList()
        outside.forEach { assertNull("码 $it 不是高德这个版本里的 IconType，不许猜一个动作", turnIconSpeechOf(it)) }
    }

    /**
     * 播报类型只认 `NaviTTSType` 实测到的那 6 个码。
     *
     * `null` 与表外码都回 null：高德那个只带文本的重载没有类型码，填 0 会被读成"0 号类型"，
     * 并入 1 会把"高德没说是哪类播报"念成"导航提示"。6（偏航）在这里有名字，
     * 但偏航这件事本身仍由高德的回调说，不本机推断。
     */
    @Test
    fun `播报类型码表外与没给都不并入别的类型`() {
        assertEquals("导航提示", naviVoiceTypeNameOf(1))
        assertEquals("开始导航", naviVoiceTypeNameOf(2))
        assertEquals("结束导航", naviVoiceTypeNameOf(3))
        assertEquals("自定义播报", naviVoiceTypeNameOf(4))
        assertEquals("打断当前播报", naviVoiceTypeNameOf(5))
        assertEquals("偏航播报", naviVoiceTypeNameOf(6))
        assertNull(naviVoiceTypeNameOf(null))
        assertNull(naviVoiceTypeNameOf(0))
        assertNull(naviVoiceTypeNameOf(7))
    }

    // ---------- 九、赛道 ⑩：车道码表与车道帧（值域来自 javap，两个数组不配对） ----------

    /**
     * `LaneAction` 的**名字**表必须 27 条一个不缺。
     *
     * 值域是 `javap -constants com.amap.api.navi.enums.LaneAction` 在实际解析到的
     * `navi-3dmap:10.0.600_3dmap10.0.600` 上读出来的 `0..25` 连号 ＋ `255`，
     * ⚠ **不是 0..26**：26 与 27..254 在这个版本里根本没有。
     * 少一条名字，页面就会对着高德给了的码说"表外"，那是把"有"念成"没有"。
     */
    @Test
    fun `车道码名字表把 javap 实测的 27 条一个不缺`() {
        val defined = (0..25).toSet() + setOf(255)
        assertEquals("LaneAction 实测就是 27 条：0..25 连号加 255", 27, defined.size)
        defined.forEach {
            assertNotNull("码 $it 在高德 LaneAction 里有定义，名字表不能缺它", laneActionNameOf(it))
        }
        // 抽查几行的字面：改一个常量名都要有用例红
        assertEquals("LANE_ACTION_AHEAD", laneActionNameOf(0))
        assertEquals("LANE_ACTION_LU_TURN", laneActionNameOf(5))
        assertEquals("LANE_ACTION_TIDAL", laneActionNameOf(25))
        assertEquals("LANE_ACTION_NULL", laneActionNameOf(255))
    }

    /**
     * 表外那三格（26、27、-1）不并入任何已定义的车道：26 挨着 25，最容易"顺手当成下一个"，
     * 而 255 是 `LANE_ACTION_NULL`（高德给了这个码 ≠ 没给码）。
     */
    @Test
    fun `车道码表外一个不编`() {
        listOf(-1, 26, 27, 100, 254, 256).forEach {
            assertNull("码 $it 不是这个版本里的 LaneAction，不许给它安一个名字", laneActionNameOf(it))
            assertTrue(
                "表外码必须被明说是表外，而不是念成某个车道",
                laneCellLabel(it).contains("LaneAction 表里没有的车道码"),
            )
        }
        assertEquals("无车道动作", laneCellLabel(255))
    }

    /**
     * 话术表**故意只有 22 条**：`LEFT_IN_AHEAD`(13)、`LEFT_IN_LEFT_LU_TURN`(14)、`RESERVED`(15)、
     * `RIGHT_RU_TURN_EX`(17)、`LEFT_LU_TURN_EX`(20) 这五条翻不出有证据的说法，
     * 于是走第二格：把高德的常量名原样报出来，并明说"本机不解释这个词"。
     *
     * 这五格是这个类里最容易写错的方向 —— 就近译成"直行加左转"看着更像人话，但那是本机替高德说话。
     */
    @Test
    fun `车道话术只给名字自解释的 22 条，另外五条报名字不解释`() {
        val speech = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 16, 18, 19, 21, 22, 23, 24, 25, 255)
        assertEquals(22, speech.size)
        speech.forEach { assertNotNull("码 $it 有话术", laneActionSpeechOf(it)) }
        listOf(13, 14, 15, 17, 20).forEach { assertNull("码 $it 的语义没有材料，不许硬翻", laneActionSpeechOf(it)) }

        assertEquals("潮汐车道", laneActionSpeechOf(25))
        assertEquals("可变车道", laneActionSpeechOf(23))
        assertEquals("直行或左转或右转", laneActionSpeechOf(7))
        // 第二格的字面：名字照抄 + 明说不解释
        assertEquals("车道码 15（LANE_ACTION_RESERVED，本机不解释这个词）", laneCellLabel(15))
        assertEquals("车道码 13（LANE_ACTION_LEFT_IN_AHEAD，本机不解释这个词）", laneCellLabel(13))
    }

    /**
     * 两个数组**各念各的**，不按下标配对：包里没有任何材料说 `frontLane[i]` 是 `backgroundLane[i]` 的
     * 推荐标记，`laneCount` 与两个数组谁长谁短也没在真机取过样本（D-⑩-1）。
     *
     * 一旦本机按下标 zip 起来，就是拿没核过的假设替司机决定"走第几条道"，
     * 而那种错的方向是"车开错道"，不是"少显示一行字"。
     */
    @Test
    fun `车道帧里两个数组不配成同一条道`() {
        val frame = laneFrameOf(laneCount = 3, backgroundLane = intArrayOf(0, 3), frontLane = intArrayOf(1))
        assertEquals(2, frame.backgroundCount)
        assertEquals(1, frame.frontCount)
        assertFalse("车道数对不上：不许把短的补齐", frame.countsAgree)

        val text = laneDisplayTextOf(LaneDisplay.Single(frame))
        assertTrue("要报总车道数：$text", text.contains("共 3 条车道"))
        assertTrue("后方车道要念出来：$text", text.contains("后方 直行、右转"))
        assertTrue("前方车道要念出来：$text", text.contains("前方 左转"))
        assertFalse("不许把 background=0 与 front=1 配成「直行或左转」", text.contains("直行或左转"))
        assertTrue("车道数对不上要明说：$text", text.contains("车道数对不上"))
    }

    /** 数组没条目 ≠ 高德没给这一格：空数组是"给了对象、里面 0 条"，话术里要有车道数 0 而不是"没拿到车道信息" */
    @Test
    fun `车道帧的空数组收成空列表不补数`() {
        val frame = laneFrameOf(laneCount = 0, backgroundLane = null, frontLane = intArrayOf())
        assertEquals(emptyList<Int>(), frame.backgroundCodes)
        assertEquals(emptyList<Int>(), frame.frontCodes)
        assertTrue(frame.countsAgree)
        val text = laneDisplayTextOf(LaneDisplay.Single(frame))
        assertTrue("后方无车道要显示'无'：$text", text.contains("后方 无"))
        assertTrue("前方无车道要显示'无'：$text", text.contains("前方 无"))
        assertNotEquals(laneDisplayTextOf(null), text)
    }

    /**
     * 四格分开，一格一个结论（§八 ㉛「没问到」与「没有」是两格那条规矩的车道版）：
     * 没回调过 / 高德已经不显示 / 多组车道 / 单组能逐条念。
     */
    @Test
    fun `没给过与已经不给与多组车道是三种不同说法`() {
        assertEquals("暂时没拿到车道信息", laneDisplayTextOf(null))
        assertEquals("当前路段不显示车道", laneDisplayTextOf(LaneDisplay.Cleared))
        val groups = laneDisplayTextOf(LaneDisplay.Groups(2))
        assertTrue(groups.contains("2 组"))
        assertFalse("多组车道那一格不许念出任何具体车道", Regex("直行|左转|右转|掉头").containsMatchIn(groups))
    }

    /**
     * 多组车道即使只有一个元素也不塌成单组。
     *
     * `first()` 看着无害，但它把"两组车道的语义没核过"这件事一起塌掉了 ——
     * 页面会显示得像 `showLaneInfo(AMapLaneInfo)` 那条路径的产物，而两条路径给的字段完整度并不同。
     */
    @Test
    fun `多组车道哪怕一组也不冒充单组`() {
        val groups: LaneDisplay = LaneDisplay.Groups(1)
        assertFalse(groups is LaneDisplay.Single)
        val text = laneDisplayTextOf(groups)
        assertTrue(text.contains("1 组车道"))
        assertFalse(text.contains("后方"))
        assertFalse(text.contains("前方"))
    }

    /** 替身引擎一帧车道都不该有：它没有高德的回调，写"暂时没拿到"是事实，写"这条路没车道"就是替高德说话 */
    @Test
    fun `替身引擎的车道流恒空`() {
        val blocked = BlockedNavigationEngine(
            readinessOf(sdkPresent = false, keyConfigured = true, initResult = null),
        )
        assertNull(blocked.lane.value)
        assertEquals("暂时没拿到车道信息", laneDisplayTextOf(blocked.lane.value))
    }

    // ---------- 赛道第 11 步：偏航重算（只能由导航引擎触发） ----------

    /**
     * 五格各自一句话，互不并入（与车道那四格同一条规矩）。
     *
     * 每句都以"高德"开头不是修辞：这一格里本机能说的话只有"高德说了什么"，
     * 出现一句以本机主语开头的结论（"已重新算好""判定偏航"）就是把没来源的东西写成了事实。
     */
    @Test
    fun `重算五格各自一句话且都只报高德说过的事`() {
        val cells: List<EngineRecalcDisplay?> = listOf(
            null,
            EngineRecalcDisplay.Yaw,
            EngineRecalcDisplay.TrafficJam,
            EngineRecalcDisplay.EngineInitiatedSuccess(0),
            EngineRecalcDisplay.EngineInitiatedFailure(0),
        )
        val texts = cells.map { engineRecalcTextOf(it) }
        assertEquals("五格必须有五种说法，两格共用一句就是被并掉了", 5, texts.distinct().size)
        texts.forEach { assertTrue("每格都得写明是高德说的：$it", it.startsWith("高德")) }
    }

    /**
     * 偏航与拥堵是两条回调、两格，谁都不许替对方说话。
     *
     * javap 实测这两条都是**无入参**的 `public abstract void`，所以两格能说的只有"它要重新算路"和原因；
     * 把拥堵并成偏航，司机会去检查一条自己根本没走过的错（指令 §39/§40 的"宁可不明说，不许说错"）。
     */
    @Test
    fun `偏航与拥堵是两格谁都不替对方说话`() {
        val yaw = engineRecalcTextOf(EngineRecalcDisplay.Yaw)
        val jam = engineRecalcTextOf(EngineRecalcDisplay.TrafficJam)
        assertNotEquals(yaw, jam)
        assertTrue(yaw.contains("偏离"))
        assertFalse("偏航那一格不许被念成拥堵", yaw.contains("拥堵"))
        assertTrue(jam.contains("拥堵"))
        assertFalse("拥堵那一格不许被念成偏航", jam.contains("偏离"))
        assertFalse("回调无入参：偏了多少米高德没说，本机不许报出来", Regex("偏了\\s*\\d+\\s*米").containsMatchIn(yaw))
    }

    /**
     * 两条广播没有结局。
     *
     * 高德的 `onReCalculateRouteForYaw` / `ForTrafficJam` 只说"要重算"，结局走的是**不带原因**的算路回执；
     * 本机若在广播那一格就宣布"重算成功""已回到线路"，等于替高德把一句它没说过的话说完了。
     */
    @Test
    fun `两条广播句子里不许出现结局`() {
        val outcome = Regex("重算成功|重算完成|已重新算好|已回到线路|结局")
        listOf(EngineRecalcDisplay.Yaw as EngineRecalcDisplay?, EngineRecalcDisplay.TrafficJam).forEach {
            val text = engineRecalcTextOf(it)
            assertFalse("广播那一格不许报结局：$text", outcome.containsMatchIn(text))
            assertTrue("要写明是高德要重新算路这件事：$text", text.contains("它要重新算路"))
        }
    }

    /**
     * 高德自己回的算路结果只报"回了什么"，绝不与前面的广播配成因果。
     *
     * 这一条是 D-057 里最容易走样的地方：`onCalculateRouteSuccess` / `Failure` 都不带原因，
     * 而 `AMapNavi` 是进程单例（偏航、前方路况、另一个调用方都能触发一次算路），
     * 于是"刚才那句偏航"与"现在这次成功"之间没有任何本机可依据的对应关系。
     * [EngineRecalcDisplay.EngineInitiatedSuccess.code] 照抄高德的 `errorCode`，本机不重新分档。
     */
    @Test
    fun `高德自己回的算路结果不配成偏航重算成功`() {
        val ok = engineRecalcTextOf(EngineRecalcDisplay.EngineInitiatedSuccess(0))
        val fail = engineRecalcTextOf(EngineRecalcDisplay.EngineInitiatedFailure(5))
        assertNotEquals(ok, fail)
        assertTrue(ok.contains("errorCode=0"))
        assertTrue(fail.contains("errorCode=5"))
        listOf(ok, fail).forEach {
            assertTrue("回执不带原因这件事必须写在句子里：$it", it.contains("不带原因"))
            assertFalse("不许把回执配成那次广播的结果：$it", Regex("重算成功|重算完成|已回到线路").containsMatchIn(it))
        }
        listOf(-1, 4, 17).forEach {
            assertTrue(engineRecalcTextOf(EngineRecalcDisplay.EngineInitiatedSuccess(it)).contains("errorCode=$it"))
        }
    }

    /**
     * null 那一格同时是本机"不判偏航"的声明（指令 v2.1 第 ⑪ 条：不得本地假判定替代后端）。
     *
     * 后端 `RouteGuide` 只下发 `deviated` 一个位，导航进行中的偏航只有高德说得出；
     * 旧写法是本机算垂距再卡一个自己挑的阈值，和后端那个位 OR 在一起 —— 那是两个都没来源的结论合成一个。
     */
    @Test
    fun `高德没说过重算那一格顺带声明本机不判偏航`() {
        val none = engineRecalcTextOf(null)
        assertNotEquals(engineRecalcTextOf(EngineRecalcDisplay.Yaw), none)
        assertTrue(none.contains("本机不拿距离卡阈值判偏航"))
    }

    /**
     * 这一族里没有"米"量级的本机结论，也没有旧屏那句本机判定的措辞。
     *
     * 阈值判定一旦搬回这一层，会长成"偏离 60 米"这种带数字的句子，所以按字面钉住：
     * 五句话里任何"数字＋米"都不许出现（`偏了多少米` 那种没有数的转述是合法的）。
     */
    @Test
    fun `重算这一族里没有本机阈值也没有本机偏离结论`() {
        val cells: List<EngineRecalcDisplay?> = listOf(
            null,
            EngineRecalcDisplay.Yaw,
            EngineRecalcDisplay.TrafficJam,
            EngineRecalcDisplay.EngineInitiatedSuccess(0),
            EngineRecalcDisplay.EngineInitiatedFailure(0),
        )
        cells.forEach {
            val text = engineRecalcTextOf(it)
            assertFalse("这一族不许报本机的距离结论：$text", Regex("\\d+\\s*米").containsMatchIn(text))
            assertFalse("旧屏那句本机判定不许被搬进来：$text", text.contains("已偏离线路"))
        }
    }

    /** 替身引擎连"高德在导哪条路"都没有，更没有重算可言：这一格恒为 null，不许写"高德说偏航了" */
    @Test
    fun `替身引擎的重算流恒空`() {
        val blocked = BlockedNavigationEngine(
            readinessOf(sdkPresent = false, keyConfigured = true, initResult = null),
        )
        assertNull(blocked.recalc.value)
        assertEquals(engineRecalcTextOf(null), engineRecalcTextOf(blocked.recalc.value))
    }

    // ---------- 赛道第 16 步：转向/播报/车道/重算四行的"没有就说不没有"口径 ----------

    /**
     * 司机自己把开关关掉时，那一格**不许替高德宣告任何东西**。
     *
     * 旧写法"本机没显示高德给的车道"里那半句"高德给的"是本机在这一刻说不出来的事实
     * （车道帧只由高德回调写入，而车道开关从未递给引擎）；把它写进句子，
     * 下一次读到"这一格没显示"就会被理解成"高德确实给过、是 App 藏起来了"。
     * 同一条红线的另一头是 ⑨/⑩ 那句"本机没做 ≠ 高德没给"，所以两句里都不许出现"高德没给"。
     */
    @Test
    fun `开关关掉那一格不替高德宣告给过什么`() {
        val lane = guidanceSwitchOffText("车道级指引", "这一格不显示车道")
        val voice = guidanceSwitchOffText("语音播报", "没让高德播报（开始导航时传的就是关）")
        listOf(lane, voice).forEach {
            assertTrue("先说清是哪条开关：$it", it.contains("开关是关的（在「我的」里）"))
            assertTrue("关着时的成因只能归到本机：$it", it.contains("），本机"))
            assertFalse("不许顺手替高德说'没给'：$it", it.contains("高德没给"))
            assertFalse("不许顺手替高德说'给过'：$it", it.contains("高德给的"))
        }
        assertNotEquals("车道与播报是两条开关、两种机制，共用一句就是把两种机制说成一种", lane, voice)
        assertNotEquals("开关关着 ≠ 高德没给车道：这是两格", lane, laneDisplayTextOf(null))
    }

    /**
     * 开关名照抄「我的」里那一行（`MinePages` 的司机导航偏好节：语音播报 / 车道级指引）。
     *
     * 这一条是纯排版复核里最实在的一处：旧写法写的是"车道引导开关"，而「我的」里根本没有那一条
     * （"车道引导"只在旧调试屏出现），司机按卡片去找会找不到；播报那一格则连名字都没给。
     */
    @Test
    fun `开关名照抄设置页那一行不指错开关`() {
        assertTrue(guidanceSwitchOffText("车道级指引", "不显示").startsWith("车道级指引开关"))
        assertTrue(guidanceSwitchOffText("语音播报", "不播").startsWith("语音播报开关"))
        assertFalse(
            "「车道引导」是旧调试屏那台开关的名字，正式卡片不指它",
            guidanceSwitchOffText("车道级指引", "不显示").startsWith("车道引导"),
        )
    }

    /**
     * 两条开关句各说各的机制，不互相套用。
     *
     * 车道开关只影响这一格显不显示（引擎那侧 0 读取点，高德照旧推帧），所以那句只能说"不显示"；
     * 语音开关是**真的**交给了高德（`startGpsNavi` → `setUseInnerVoice`），所以那句能说"没让高德播报"。
     * 把两句写成同一句，就是把"本机没显示"与"本机让高德不播"混成一件事 —— 而这两件事的修法完全不同。
     */
    @Test
    fun `两条开关句不互相套用对方的机制`() {
        val lane = guidanceSwitchOffText("车道级指引", "这一格不显示车道")
        val voice = guidanceSwitchOffText("语音播报", "没让高德播报（开始导航时传的就是关）")
        assertTrue(lane.contains("不显示车道"))
        assertFalse("车道开关没交给引擎，这一句写不出'没让高德…'", lane.contains("没让高德"))
        assertTrue(voice.contains("没让高德播报"))
        assertFalse("播报开关是真交给高德的，不说成'这一格不显示'", voice.contains("不显示"))
    }
}
