package com.shanxiang.buspost.ui.driver

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf // 量两条自绘条的高度（见下面 topPx/bottomPx）
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amap.api.navi.AMapNaviView
import com.amap.api.navi.AMapNaviViewListener
import com.amap.api.navi.AMapNaviViewOptions
import com.shanxiang.buspost.BuildConfig
import com.shanxiang.buspost.core.AppState
import com.shanxiang.buspost.core.map.RouteCheck
import com.shanxiang.buspost.core.navigation.NavigationEngines
import com.shanxiang.buspost.core.navigation.NavigationSession
import com.shanxiang.buspost.core.navigation.engineRecalcTextOf
import com.shanxiang.buspost.core.navigation.navPlanOfStops
import com.shanxiang.buspost.core.net.attempt
import com.shanxiang.buspost.core.ui.BusButton
import com.shanxiang.buspost.core.ui.BusButtonVariant
import com.shanxiang.buspost.core.ui.BusIcons
import com.shanxiang.buspost.core.ui.BusRadius
import com.shanxiang.buspost.core.ui.BusElevation
import com.shanxiang.buspost.core.ui.glassmorphic
import com.shanxiang.buspost.core.ui.glassSheen
import com.shanxiang.buspost.core.ui.LocalBusDriving
import com.shanxiang.buspost.core.ui.LocalBusFontScale
import com.shanxiang.buspost.core.ui.LocalBusTokens
import com.shanxiang.buspost.data.model.Geo
import com.shanxiang.buspost.data.model.MissionStop
import com.shanxiang.buspost.data.store.Prefs
import com.shanxiang.buspost.driver.Running
import com.shanxiang.buspost.ui.common.CapsuleAction
import com.shanxiang.buspost.ui.common.CapsuleActions
import com.shanxiang.buspost.ui.common.LocalNavController
import com.shanxiang.buspost.ui.common.MapMarker
import com.shanxiang.buspost.ui.common.capsuleReserve
import com.shanxiang.buspost.ui.common.RouteUnavailableState
import com.shanxiang.buspost.ui.common.markerOptionsOf

/**
 * 车道级导航页（本轮新增，正式可达）。
 *
 * ## 与旧 `DriverNavScreen` 的根本区别
 * 旧屏是**自绘折线 + 文字步骤**：它按车道名文字猜箭头（自己都注释了"这一条不算车道级引导"）。
 * 这一页把引导交给**真实高德导航视图** `AMapNaviView`，因此拿到了三样自绘做不到的东西：
 * ① 车道放大图（`setLaneInfoShow`）、② 路口放大图（真实投影 `setRealCrossDisplayShow` / 模型 `setModeCrossDisplayShow`）、
 * ③ 与真实路网同步的实时路况（`setTrafficBarEnabled` / `setTrafficLayerEnabled`）。
 * 以上开关名逐个 `javap` 核对过本工程实际解析到的 `com.amap.api:navi-3dmap:10.0.600_3dmap10.0.600`
 * （jar 内 1855 个类、`AMapNaviViewOptions` 的完整方法表），不是凭印象写的。
 *
 * ## 诚实性纪律（沿用 `AmapNavigationEngine` 那条"宁可暂时不能导航，不允许假导航"）
 * - **折线必须本页重新算**：目的地只从 [AppState.GuideTarget] 拿三个字段（坐标 / 名字 / 站点号），
 *   不从工作台搬折线过来 —— 搬过来的折线绕过了 `RealRoadRouteValidator` 那道真实道路闸门。
 * - 没有目的地 / 没有定位 / 引擎未就绪 / 算路被拒：**逐条给出真实原因**，不铺一张空白地图充数。
 * - 高德视图在本机装不起来（缺 key / 缺原生库 / 设备 ABI 不支持）时如实说明，并给出「交给外部地图」的出路。
 * - **引导信息只有一份来源**（D-169，用户口径④「导航页面就用高德自带的信息，不要再画蛇添足」）：
 *   转向动作、下一段路名、剩余里程/耗时、车道放大图一律由高德自带面板（`isLayoutVisible = true`）画，
 *   本页顶条只留高德**没有**的那三行：品牌 + 目的地 + 这一趟覆盖几站（外加偏航/重算那一句）。
 *   曾经关掉自带面板、把这四样自绘一遍（D-158 的"沉浸式"），代价是同一屏两套答案：
 *   自绘那一套按 `NavigationGuidance` 的帧走，面板那一套按 SDK 内部状态走，两者不同时司机看到两个数。
 */
@Composable
fun DriverGuideScreen() {
    val context = LocalContext.current
    val nav = LocalNavController.current
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val driving = LocalBusDriving.current
    val nightUi by AppState.nightUi.collectAsStateWithLifecycle()

    // 目的地：进入这一屏时消费一次（与司机异常上报页的 legId 同一手法）。消费完即清，
    // 免得下次从别处进来时还念着上一次的目的地。
    val target = remember { AppState.consumeGuideTarget() }
    val me by Running.geo.collectAsStateWithLifecycle()

    /**
     * 全任务段计划（[navPlanOfStops]）：终点与途经点**出自同一次取数**，顺序只认后端 `visitSequence`。
     * 工作台传来的站表为空时（老调用点只给一个目的地）这一格是 null，整屏行为退回逐站，
     * 与本轮之前一字不差 —— 新读点不许把旧路径顶坏。
     */
    val plan = remember(target) {
        target?.let { it.legStops.ifEmpty { null }?.let { stops -> navPlanOfStops(stops) } }
    }
    // 顶部条与算路的终点同一个来源：全段模式认 plan.destination（后端顺序最后一站），
    // 逐站模式认进页时那一个目的地。两者只可能有一个非空，界面不会出现"两个目的地"。
    val destGeo = plan?.destination?.geo ?: target?.let { Geo(it.lat, it.lng) }
    val destName = plan?.destination?.name?.ifEmpty { null }
        ?: target?.name.orEmpty().ifEmpty { if (destGeo == null) "" else "任务点" }

    val engine = remember { NavigationEngines.create(context) }
    DisposableEffect(engine) {
        onDispose { engine.release() }
    }
    val readiness by engine.readiness.collectAsStateWithLifecycle()
    val frame by engine.guidance.collectAsStateWithLifecycle()
    // 车道帧这一轮不再在本页收集（D-169）：车道放大图是高德自己画的（`isLaneInfoShow`），
    // 我们那一行文字是它的第二套答案。引擎侧的 Flow 保留 —— 工作台那张卡还在读它。
    val recalc by engine.recalc.collectAsStateWithLifecycle()
    val arrivedWayIndex by engine.arrivedWayPointIndex.collectAsStateWithLifecycle()

    /**
     * 把"高德说到了第 k 个途经点"翻成站表里的那一站。
     *
     * 下标语义来自 `AMapNaviListener.onArrivedWayPoint(int wayID)` 的官方 javadoc：
     * wayID 是**到达的途经点编号，从 0 开始** —— 所以它配 `plan.wayPoints[k]`，而终点那一站
     * 由高德的 `onArriveDestination` 说（这一格永远配不到它）。界面念的是"第 k+1 个"（1 基），
     * 与存下来的那个下标故意不同写法：混为一谈就整体错开一站。
     *
     * 对不上就**不猜是哪一站**：`known=false`，卡片只念"高德说了到达、下标不在本趟站表里"，
     * 并保留回工作台核对的出口。到站之后要做的事（揽收/扫码/交接）仍然只在工作台那一面。
     */
    val arrivedStop = arrivedWayIndex?.let { k ->
        val stop = plan?.wayPoints?.getOrNull(k)
        ArrivedStop(index = k, stop = stop, known = stop != null)
    }

    /**
     * 到近播报（缺陷 #61）：这一屏以前没有本机播报器 —— 司机在全屏导航那一段路上反而听不到
     * "快到 X 站"，而工作台那一面一直有（`DriverSdkNavCard` 里那个）。配对口径只认引擎：
     * 高德那帧剩余里程属于"我正在导到的那一站"，而这一站由本趟计划 + 高德报过的到达下标算出来，
     * 与上面 [arrivedStop] 同一个来源 —— 两处各算一遍就会串站，那是到近播报最坏的错法。
     */
    val announcer = remember { ApproachAnnouncer(context) }
    DisposableEffect(announcer) {
        announcer.ensureTts()
        onDispose { announcer.shutdown() }
    }
    // 到车持续提醒那一格判出来的站（null = 这一刻不该提醒）。由下面那条喂帧的 effect 写，
    // 顶条与出声循环都读这一格 —— 「要不要提醒」这件事在一屏里只许有一份。
    var reminderStop by remember { mutableStateOf<MissionStop?>(null) }
    val engineTargetStop = plan?.let { p ->
        val k = (arrivedWayIndex ?: -1) + 1
        p.wayPoints.getOrNull(k) ?: p.destination.takeIf { k >= p.wayPoints.size }
    }
    // 重复出声的账本（节奏全在纯层）。**这里没有 delay、没有定时器**：N-h 那条"页面不许用本机节奏
    // 刷引擎状态"的红线不分场合，而这件事本来也不需要定时器 —— 高德每来一帧转向信息就跑一次判点，
    // 纯层自己拿单调时钟算"够不够一个间隔"。帧一停（退出导航 / 高德不再回执），也就不再重复。
    val reminder = remember { ReminderTracker() }
    LaunchedEffect(plan, arrivedWayIndex, frame) {
        val stops = plan?.stops ?: return@LaunchedEffect
        val meters = frame?.takeIf { it.hasRemainDistance }?.remainMeters
        val feed = ApproachFeed.fromStops(stops, engineTargetStop, meters)
        announcer.onFrame(feed)
        // 到车持续提醒（D-170）：这一屏的口径是引擎那一路（高德正在导到的那一站 + 它的剩余里程），
        // "要不要提醒"这件事仍然只在 `ApproachReminder.stopOf` 判一次，与工作台那一屏共用同一个判点。
        val near = ApproachReminder.stopOf(feed)
        reminderStop = near
        // 每一帧问一次纯层"够不够一个间隔"：第一次看到这一站只记时刻不出声（那一句归 ApproachTracker），
        // 之后每过一个间隔回一句、最多几遍，响满就闭嘴。
        reminder.next(near, android.os.SystemClock.elapsedRealtime())?.let { announcer.remind(it) }
    }

    var phase by remember { mutableStateOf<GuidePhase>(GuidePhase.Waiting) }
    var session by remember { mutableStateOf<NavigationSession?>(null) }
    // 视角固定车头朝上：它必须落到底层 `setNaviMode` 上（不是只改本地标签）。
    // 页面上那个"车头朝上 / 正北朝上"的切换按钮本轮摘掉了，理由写在 `GuideBottomBar` 的注释里。
    val carUp = true

    // 算路：目的地 + 本机定位 + 引擎就绪 三者齐了才发请求。缺一个就照实说缺哪个。
    //
    // ⚠ 键里**不能放 `me` 这个坐标**（缺陷 #76 的真因）：司机在动，`me` 每来一帧就换一个值 ⇒
    // 这个 effect 被反复重启，而老那一协程正挂在 `engine.calculateRoute()` 里等回执 ——
    // `attempt` 按 §七 让 CancellationException 穿出，于是老的一路死在半道、没来得及写 phase，
    // 新的那一进来看见 `phase is Calculating` 就 return ⇒ **屏幕上永远停在"正在请求真实道路…"**，
    // 底下已经没有任何人在算路。所以这里按"有没有定位"（`me == null`）而不是"定位是多少"来触发；
    // 起点用启动这一轮取到的那一帧，中途不跟车移动重算（重算由高德的偏航通道负责）。
    LaunchedEffect(target, me == null, readiness.canNavigate) {
        val dest = destGeo
        if (dest == null) {
            phase = GuidePhase.NoTarget
            return@LaunchedEffect
        }
        if (me == null) {
            phase = GuidePhase.NoFix
            return@LaunchedEffect
        }
        if (!readiness.canNavigate) {
            phase = GuidePhase.EngineBlocked(readiness.userText)
            return@LaunchedEffect
        }
        // 这里**不许**再写 `if (phase is Calculating || phase is Ready) return`：
        // 键一变，老协程已经被取消而 phase 还留在 Calculating —— 那道守卫只会让新协程也提前退出，
        // 于是这一格永远"计算中"（就是上面那条注释说的病）。重启就等于重新算一遍，代价可接受。
        phase = GuidePhase.Calculating
        // 途经点只在"这一帧是全段模式"时喂：plan 自己会因超出高德上限而退回逐站（legByLeg + note 说要话），
        // 那时 wayPoints 是空列表，算路就只导到最近一站 —— 摘掉中间站等于跳站，这一支永远不许那样。
        val result = attempt {
            engine.calculateRoute(me, Geo(dest.lat, dest.lng), plan?.wayPoints?.map { it.geo } ?: emptyList())
        }
            .getOrElse { RouteCheck.Rejected(reason = "导航引擎调用异常", detail = it.toString()) }
        phase = when (result) {
            is RouteCheck.Accepted -> GuidePhase.Ready(result.route.roadDistanceMeters, result.route.roadEtaSeconds)
            is RouteCheck.Rejected -> GuidePhase.Refused(
                result.reason + if (result.detail.isEmpty()) "" else "（${result.detail}）",
            )
        }
    }

    // 算路一过就开始本机导航：高德视图画的是"高德此刻握着的那条"，而刚过闸门的正是它
    // （引擎内部用 `AcceptedRouteSession` 把这两件事钉在一起，见 NavigationEngine §九）。
    LaunchedEffect(phase) {
        if (phase is GuidePhase.Ready && session !is NavigationSession.Running) {
            session = engine.startGpsNavi(Prefs.voiceGuidance)
        }
    }

    BackHandler(enabled = true) {
        engine.stopNavi()
        nav.popBackStack()
    }

    val exit = {
        engine.stopNavi()
        nav.popBackStack()
        Unit
    }

    /**
     * 全览这一格**不再由本页持有**（D-169 续，v204 真机）：高德自带面板右下角那颗「全览 / 退出全览」
     * 做的就是同一件事（点完它，我们这一格原来会跟着改标签，说明两者驱动的是同一个 SDK 显示模式）。
     * 上一版在页面里留一个 `overview` 本地布尔，于是"现在是不是全览"有两个答案：
     * 司机按高德的键，我们的布尔不知道；司机按我们的键，高德的面板标签反过来又自己变。
     * 现在镜头模式整个交给高德，本页一次都不再调 `setShowMode`。
     */

    // 本页的悬浮胶囊只剩**播报**一格（D-169 续，v204 真机量过）：
    // 「看整段」与「退出导航」以前是这一屏的三格之二，但高德自带面板上就有「全览」与「退出」两颗键，
    // 而且都真做事（v204 实测：点面板左下的「退出」直接回到司机工作台）。
    // 用户口径④「用高德自带的信息，不要再画蛇添足」+ 更早那条"退出出现两次像调试页"，
    // 两条合起来指向同一件事：**同一个动作只许有一颗键**，这一屏留高德的那两颗，胶囊只留高德没有的播报。
    // 播报开关改完必须重起一次导航才生效（高德内置播报是进程单例上的开关）—— 这里如实重起，
    // 而不是只翻本地标签让司机以为已经静音了。
    val voiceOn = Prefs.voiceGuidance
    CapsuleActions(
        listOf(
            CapsuleAction(
                key = "voice",
                label = if (voiceOn) "播报已开" else "播报已关",
                icon = if (voiceOn) BusIcons.volume else BusIcons.volumeOff,
                onClick = {
                    Prefs.voiceGuidance = !voiceOn
                    engine.stopNavi()
                    session = engine.startGpsNavi(!voiceOn)
                },
            ),
        ),
    )

    val ready = phase is GuidePhase.Ready
    // 任务点标记：与工作台地图同一个出口（`MissionMarkers.stopMarkers`），焦点交给这一趟的终点。
    // 站表为空（逐站模式的旧调用点）时这里就是 0 个标记，画面与本轮之前一致。
    val navMarkers = remember(plan) {
        MissionMarkers.stopMarkers(plan?.stops ?: emptyList(), focusStopId = plan?.destination?.stationId ?: 0L)
    }
    // 两条自绘条是**盖在全屏地图上**的，而 AMapNaviView 把自己的路口放大图 / 转向提示画在它表面的
    // 左上角 —— 于是那些控件画在我们的标题**底下**，既看不见也点不着（2026-10-10 真机实测：
    // `layout_audit` 报"进入 × 乡邮智联 · 车道级导航 重叠 50%"，且该节点被屏幕左边裁掉一半）。
    // 修法不是关掉放大图（司机要它），而是**量出两条自绘条的实际高度，把地图夹在中间**。
    // 手法对齐本文件同仓的 `StickyBarScaffold`（`DriverBits.kt`）：量 px 再换 dp，
    // **首帧没量到时给保守值而不是 0** —— 给 0 就是"地图先全屏、下一帧才缩"，那一帧里放大图照样被压。
    val density = androidx.compose.ui.platform.LocalDensity.current
    var topPx by remember { mutableIntStateOf(0) }
    var bottomPx by remember { mutableIntStateOf(0) }
    // 120dp 是"首帧还没量到"时的保守值：顶条从五行（品牌/目的地/本段 k 站/转向+剩余/车道）
    // 收成三行（D-169 去重）之后它比原来矮一半，170 那个数是按旧的五行给的，留着的代价是
    // 地图白让出 50dp，而高德面板的转向卡会跟着我们的条子一起往下挪。
    val topReserve = if (topPx == 0) 120.dp else with(density) { topPx.toDp() }
    // 底部原来那条通栏按钮条撤了（功能按钮搬进悬浮胶囊，胶囊是浮在地图上的、不占地图高度），
    // 所以这一格只剩"到站那一格"的高度 + 胶囊那一格。
    //
    // ⚠ 但它不是唯一浮在地图下沿的东西：**全局悬浮胶囊也画在同一格底部**（本页登记了播报 / 看整段 / 退出导航）。
    // 2026-10-17 自查发现到站那一格右下的「回工作台作业」整枚按钮被胶囊盖在下面 —— 看着还剩半截，
    // 点下去走的是胶囊那一格。修法是让到站格**浮在胶囊之上**（下面那句 `padding(bottom = capsuleReserve())`），
    // 而地图让出的高度要一并把胶囊那一格算进去，否则路口放大图又换成被到站格压住。
    //
    // ⚠ D-169 起连"没有到站格"那一支也要让高（旧写法给 0.dp）：高德自带面板重新打开后，
    // 它的"剩余里程 / 耗时"那条就画在**地图视图自己的**下沿，而地图是铺到底的 —— 让 0 就是让胶囊压住它。
    // 撤掉面板那四行自绘换回来的东西不能转头又被自己的胶囊盖住，那只是把遮挡从一处搬到另一处。
    val bottomReserve = when {
        bottomPx > 0 -> with(density) { bottomPx.toDp() } + capsuleReserve() + 8.dp
        arrivedStop != null -> 84.dp + capsuleReserve()
        else -> capsuleReserve()
    }

    /**
     * 导航中把系统两条 bar 收掉（用户口径："开启自带车道级导航后尽量占全屏，去掉无用的按钮和遮挡"）。
     *
     * 为什么现在能收：上一轮的遮挡根因不是自绘条太厚，而是**高德把路口放大图画在视图左上角**，
     * 而我们的顶条盖在它上面（真机实测重叠 50%）。顶条已经改成"量高度再把地图夹在中间"，
     * 这一格再收掉状态栏，顶条让出来的那一条就真还给地图了。
     *
     * 只在 ready 时收，离场必须还原：不还原的话司机退出导航后，整个 App 都少了一条状态栏，
     * 那是把一屏的选择变成全局的。`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` 而不是完全禁掉 ——
     * 司机要从上边下拉通知，手势仍然给。
     */
    val controller = (context as? android.app.Activity)?.window?.insetsController
    DisposableEffect(ready) {
        if (ready) {
            controller?.systemBarsBehavior =
                android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(android.view.WindowInsets.Type.systemBars())
        } else {
            controller?.show(android.view.WindowInsets.Type.systemBars())
        }
        onDispose { controller?.show(android.view.WindowInsets.Type.systemBars()) }
    }

    Box(Modifier.fillMaxSize().background(if (ready) Color.Black else t.paper)) {
        if (ready) {
            AmapNaviSurface(
                modifier = Modifier.fillMaxSize().padding(top = topReserve, bottom = bottomReserve),
                night = nightUi || driving,
                laneOn = Prefs.laneGuidance,
                carUp = carUp,
                markers = navMarkers,
                onExit = exit,
            )
            // ---- 顶部自绘条：只留高德面板**没有**的那三行（D-169 去重）----
            GuideTopStrip(
                modifier = Modifier.align(Alignment.TopCenter).onSizeChanged { topPx = it.height },
                destination = destName,
                // 全段模式说"本段 k 站（终点是最后一站）"；逐站模式不编这句话。
                // 剩余里程那一个大数字现在由高德面板画（D-169），而它是**整条导航路径**的剩余，
                // 全段模式下它就是到终点的剩余，所以这一行是把那个数的口径写明白，
                // 不让司机把它读成"到最近一站还有这么多"。
                stopLine = when {
                    plan == null -> ""
                    plan.legByLeg -> plan.note
                    plan.stops.size > 1 -> "本段共 ${plan.stops.size} 站 · 途经点 ${plan.wayPoints.size} 个（按调度顺序）"
                    else -> "本段只剩这一站"
                },
                // 到车持续提醒那一行（D-170）：这一站还没办完就一直在，办完之后后端回读 ⇒ 自己消失
                reminderText = reminderStop?.let { "这一站还没办完：" + ApproachReminder.cardText(it) },
                recalcText = engineRecalcTextOf(recalc),
            )
            // ---- 底部：只剩"到站那一格"（高德说的）。语音 / 结束两格搬进悬浮胶囊，不再占通栏 ----
            // 它浮在胶囊**之上**而不是同一格底部：胶囊正压着这一格右下的「回工作台作业」（见 bottomReserve 注）。
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = capsuleReserve())
                    .onSizeChanged { bottomPx = it.height },
            ) {
                arrivedStop?.let { arrived ->
                    GuideArrivedCard(
                        arrived = arrived,
                        onGoWorkbench = exit,
                    )
                }
            }
            (session as? NavigationSession.Refused)?.let { refused ->
                // 算路过了但 startNavi 被拒：这一条同样要露出来，不能静默停在"看着像在导"
                Text(
                    refused.reason,
                    color = Color.White,
                    fontSize = (12 * scale).sp,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(BusRadius.m))
                        // 硬编码色：导航覆盖层的算路拒绝条 —— 这一屏永远画在深色地图上，浅色令牌会让条子消失
                        .background(Color(0xCC1B1B1B))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        } else {
            // 非就绪态：**不铺地图**。逐条说清"为什么还没有导航"，并给出这一屏能做的事。
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "车道级导航",
                    color = t.ink,
                    fontSize = (20 * scale).sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    guidePhaseDetail(phase, destName),
                    color = t.textSecondary,
                    fontSize = (13 * scale).sp,
                )
                if (phase is GuidePhase.Calculating) {
                    Text("正在请高德按真实路网算一条道路…", color = t.textTertiary, fontSize = (12 * scale).sp)
                }
                RouteUnavailableState(
                    height = 150.dp,
                    reason = guidePhaseDetail(phase, destName),
                    // "重算"这里等于"退出去重进"：算路由上面那条四态驱动的 LaunchedEffect 负责，
                    // 就地再写一条重算路径就会出现两个算路口径（一个是真相，一个会漂移）。
                    onReplan = exit,
                )
                Spacer(Modifier.weight(1f))
                BusButton(
                    text = "返回工作台",
                    variant = BusButtonVariant.OUTLINE,
                    onClick = exit,
                )
            }
        }
    }
}

/** 本页四态：算路没成之前一律不铺地图（铺了就是拿空白地图冒充导航） */
private sealed class GuidePhase {
    data object Waiting : GuidePhase()
    data object NoTarget : GuidePhase()
    data object NoFix : GuidePhase()
    data object Calculating : GuidePhase()
    data class EngineBlocked(val reason: String) : GuidePhase()
    data class Refused(val reason: String) : GuidePhase()
    data class Ready(val roadMeters: Int, val roadEtaSeconds: Int) : GuidePhase()
}

private fun guidePhaseDetail(p: GuidePhase, destination: String): String = when (p) {
    is GuidePhase.Waiting -> "正在准备导航…"
    is GuidePhase.NoTarget ->
        "这一次没有带上目的地（调度没给未完成的任务点）。导航不自己决定去哪里 —— 请回工作台从任务点卡进入。"
    is GuidePhase.NoFix ->
        "暂无定位，算不出起点。请先在工作台打开「位置上报（前台服务）」，等坐标出现后再进这一页。"
    is GuidePhase.Calculating -> "正在请求真实道路…"
    is GuidePhase.EngineBlocked -> "导航组件不可用：${p.reason}"
    is GuidePhase.Refused -> "未能给出可用道路：${p.reason}"
    is GuidePhase.Ready ->
        "已就绪：前往${destination.ifEmpty { "任务点" }}（道路 ${p.roadMeters} 米 / " +
            "${if (p.roadEtaSeconds > 0) "${p.roadEtaSeconds / 60} 分钟" else "耗时未知"}）"
}

/**
 * 真实高德导航视图。
 *
 * 生命周期严格照官方顺序：`onCreate` → 设 `AMapNaviViewOptions` → `onResume`，离场 `onPause` → `onDestroy`。
 * 选项要在 `onCreate` 之后、`onResume` 之前设，顺序反了车道/放大图那几个开关会被重置掉。
 *
 * 原生库不可用时（缺 key、x86 设备没装 ARM 翻译层、R8 裁掉类）**不能崩**：
 * 构造与初始化整段 `runCatching`（缺 `.so` 抛的是 `UnsatisfiedLinkError`，它不是 `Exception`，
 * 所以这里 catch 的是 `Throwable`），失败就把原因放到 `failed`，页面退回"用不了"的实话。
 */
@Composable
private fun AmapNaviSurface(
    modifier: Modifier,
    night: Boolean,
    laneOn: Boolean,
    carUp: Boolean,
    markers: List<MapMarker>,
    onExit: () -> Unit,
) {
    var failed by remember { mutableStateOf<String?>(null) }
    // 位图 marker 要按屏幕密度画，而 `AndroidView` 的 factory 那一层拿到的 `ctx` 在 `LaunchedEffect`
    // 里看不见 —— 所以在函数体这一层取一次同一个 context，两处共用它。
    val hostContext = LocalContext.current
    if (BuildConfig.AMAP_KEY.isBlank()) {
        failed = "地图服务未配置，导航视图无法启动"
    }
    var viewRef by remember { mutableStateOf<AMapNaviView?>(null) }
    // 这一屏自己贴的标记，自己收回：`AMapNaviView.getMap()` 拿到的 `AMap` 与高德导航层共用，
    // 页面离场不摘就会留在进程里（下一次进来看不到旧标记，但旧标记还在占 SDK 的图层）。
    var placed by remember { mutableStateOf<List<com.amap.api.maps.model.Marker>>(emptyList()) }

    if (failed == null) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                runCatching {
                    AMapNaviView(ctx).also { v ->
                        v.onCreate(Bundle())
                        v.setAMapNaviViewListener(object : AMapNaviViewListener {
                            override fun onNaviSetting() = Unit
                            override fun onNaviCancel() = Unit
                            // true = "退出由我自己处理"，高德就不再弹它自己的退出确认框。
                            // D-169 起这一格**必须真做事**：自带面板重新打开 ⇒ 那颗返回键就在屏幕上，
                            // 只回 true 不退出等于一颗点不动的按钮（界面不许承诺做不到的动作）。
                            override fun onNaviBackClick(): Boolean {
                                onExit()
                                return true
                            }
                            override fun onNaviMapMode(mode: Int) = Unit
                            override fun onNaviTurnClick() = Unit
                            override fun onNextRoadClick() = Unit
                            override fun onScanViewButtonClick() = Unit
                            override fun onLockMap(lock: Boolean) = Unit
                            override fun onNaviViewLoaded() = Unit
                            override fun onMapTypeChanged(type: Int) = Unit
                            override fun onNaviViewShowMode(mode: Int) = Unit
                        })
                        applyNaviOptions(v, night = night, laneOn = laneOn, carUp = carUp)
                        v.onResume()
                        viewRef = v
                    }
                }.getOrElse { e ->
                    failed = "导航视图无法加载：${e.javaClass.simpleName}" +
                        (e.message?.let { " · $it" } ?: "")
                    android.view.View(ctx)
                }
            },
            update = { v ->
                // 夜间 / 车道 / 视角都是"运行中也要跟着改"的：update 里重设一次，
                // 免得司机关掉车道开关后导航页还画着车道放大图、切了视角标签却不跟着转。
                runCatching {
                    (v as? AMapNaviView)?.let { applyNaviOptions(it, night = night, laneOn = laneOn, carUp = carUp) }
                }
            },
        )
        // 任务点标记：贴在高德导航视图自己的底图上（用户要的那一格："导航是全任务段整体的，
        // 但具体业务点在导航里看得见"）。来源仍是 [MissionMarkers.stopMarkers] —— 顺序、配色、
        // 标题与工作台地图同一出口，这里只多一件事：把同一批 `MapMarker` 交给 `AMap`。
        // 整批一次贴：中途某条抛了就当作"这一批都没贴上"并留一行 logcat ——
        // 比"贴上 5 个、第 6 个静默丢了、屏幕上一切正常"诚实（贴一半最难查）。
        LaunchedEffect(viewRef, markers) {
            val view = viewRef ?: return@LaunchedEffect
            runCatching {
                placed.forEach { it.remove() }
                val aMap = view.map
                val options = markers.mapNotNull { m -> markerOptionsOf(m, hostContext) }
                placed = if (aMap == null) {
                    android.util.Log.w(
                        "BUS_MAP",
                        "导航面任务点标记没贴：AMap 句柄为 null（这一趟要贴 ${markers.size} 个）",
                    )
                    emptyList()
                } else {
                    options.mapNotNull { aMap.addMarker(it) }
                }
                // 三个数分开念：真机 v151 那张导航图上数不出我们自己的标记，而这一格过去两种失败
                // 都是静默的（句柄 null 直接给空列表、坐标 0/0 被 mapNotNull 吃掉）——
                // 没有这一行就只能靠截图猜"是没贴上，还是被高德的图层盖住了"。
                android.util.Log.i(
                    "BUS_MAP",
                    "导航面任务点标记：要贴 ${markers.size} 个 / 换算出 ${options.size} 个 / 贴上 ${placed.size} 个",
                )
            }.onFailure {
                placed = emptyList()
                android.util.Log.w("BUS_MAP", "任务点标记没贴上导航面（底图句柄为 null 或 SDK 抛了）", it)
            }
        }
        DisposableEffect(Unit) {
            onDispose {
                // 这一屏自己贴的标记自己收回：`getMap()` 的 `AMap` 与高德导航层共用，
                // 不摘就会留在进程里（下一次进来看不到旧标记，但旧标记还占着 SDK 图层）。
                runCatching { placed.forEach { it.remove() } }
                placed = emptyList()
                viewRef?.let { v ->
                    runCatching { v.onPause() }
                    runCatching { v.onDestroy() }
                }
                viewRef = null
            }
        }
    }

    failed?.let { reason ->
        val scale = LocalBusFontScale.current
        // 视图起不来时的实话 + 出路：这一屏不许变成一块黑屏
        Box(
            // 硬编码色：地图起不来时那一屏的深色底（有意不跟主题，属 #31 待拍板）
            Modifier.fillMaxSize().background(Color(0xFF14181C)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    BusIcons.warning,
                    contentDescription = null,
                    // 硬编码色：警示图标用导航金，与上面那块深色底成对，换成主题金会糊
                    tint = Color(0xFFE8C271),
                    modifier = Modifier.size(34.dp),
                )
                // 字号必须乘全局字阶：这一屏以前写死 17/12.5/12，是全工程唯一没接
                //   LocalBusFontScale 的一屏 —— 结果适老模式（+22%）与驾驶模式下
                //   **只有这一块不放大**，而它恰好是"地图起不来时司机唯一能读的一屏"。
                // 深色底与配色保持原样（那是导航覆盖层的有意设计，属 #31 待拍板项，不擅自动）。
                Text("车道级导航不可用", color = Color.White, fontSize = (17 * scale).sp, fontWeight = FontWeight.Bold)
                // 硬编码色：深色底上的正文米白（同上，成对色）
                Text(reason, color = Color(0xFFD6D0C2), fontSize = (12.5f * scale).sp)
                Text(
                    "可以退回工作台，用「交给高德 / 交给百度」把目的地交给手机里装的导航 App。",
                    // 硬编码色：深色底上的次要说明灰米（同上，成对色）
                    color = Color(0xFFB3AC9B),
                    fontSize = (12 * scale).sp,
                )
                Spacer(Modifier.height(4.dp))
                val nav = LocalNavController.current
                BusButton(
                    text = "返回工作台",
                    variant = BusButtonVariant.OUTLINE,
                    onClick = { nav.popBackStack() },
                )
            }
        }
    }
}

/**
 * 把"车道级"相关开关一次设好。方法名全部来自 `javap` 实测的 `AMapNaviViewOptions` 方法表
 * （`setLaneInfoShow` / `setRealCrossDisplayShow` / `setModeCrossDisplayShow` /
 * `setTrafficBarEnabled` / `setTrafficLayerEnabled` / `setAutoLockCar` / `setScreenAlwaysBright` /
 * `setNaviNight` / `setCompassEnabled`），视角走 `AMapNaviView.setNaviMode`
 * （常量 `CAR_UP_MODE=0` / `NORTH_UP_MODE=1`，同样是 javap 取的值）。
 * ⚠ 这里**不调** `setShowMode`：跟随 / 全览那一档整个交给高德面板自己的「全览」键（D-169 续，
 * v204 真机：本页每次重设一遍 `SHOW_MODE_LOCK_CAR`，司机按高德的键进全览就会被我们拽回来）。
 */
private fun applyNaviOptions(v: AMapNaviView, night: Boolean, laneOn: Boolean, carUp: Boolean) {
    val o: AMapNaviViewOptions = v.viewOptions ?: AMapNaviViewOptions()
    o.isLaneInfoShow = laneOn              // 车道放大图 —— "车道级"三个字在本页的落点
    o.isRealCrossDisplayShow = true        // 路口放大图（真实投影）
    // ⚠ 这一对**getter/setter 名字不一致**（javap 实测：`isModelCrossDisplayShow()` / `setModeCrossDisplayShow(boolean)`），
    // Kotlin 合成不出属性，写 `o.isModeCrossDisplayShow = true` 编不过，只能显式调方法。
    o.setModeCrossDisplayShow(true)        // 路口放大图（模型，投影取不到时兜底）
    o.setTrafficBarEnabled(true)           // 实时路况条（入参是装箱 Boolean，属性语法不合成，只能调方法）
    o.setTrafficLayerEnabled(true)         // 路况图层
    // 同上：`isCompassEnabled()` / `setCompassEnabled(Boolean)` 名字一致但入参装箱，
    // 合成属性会得到 `Boolean?`；显式调方法读起来更直白。
    o.isAutoLockCar = true                 // 车标自动锁定，司机不用手动拖地图
    o.isScreenAlwaysBright = true          // 导航中屏幕常亮：不该出现"屏黑了要再点一下"
    o.isNaviNight = night
    o.setCompassEnabled(true)              // 指南针（同为装箱 Boolean）
    // 高德自带的那套面板（顶部转向卡 + 底部"还有多远 / 多久到"条）**打开**（D-169，
    // 用户口径④「app 导航页面就用高德自带的信息，我们不要再画蛇添足了」）。
    // 上一轮为了"沉浸式"把它关掉，代价是本页自绘了同一批数据的**第二套答案**
    // （转向动作 / 剩余里程 / 剩余耗时 / 车道四行），而两套答案的更新时机不同：
    // 面板按 SDK 内部状态走，自绘按 `NavigationGuidance` 的帧走，司机一眼看到两个数。
    // 顶条因此只留高德没有的那三行：品牌、目的地、这一趟覆盖几站（外加偏航/重算那一句）。
    o.isLayoutVisible = true
    v.viewOptions = o
    v.setNaviMode(if (carUp) AMapNaviView.CAR_UP_MODE else AMapNaviView.NORTH_UP_MODE)
}

/**
 * 顶部自绘条：只留**高德面板没有**的那几行（D-169 去重，用户口径④）。
 *
 * 留下的每一行都问得出"为什么不是高德给"：
 * · 品牌行 —— 乡邮智联的口径要跟全站一致，高德面板上不会写我们；
 * · 目的地 —— 高德面板只说"距终点还有多远"，不说这一趟去的是哪一站；
 * · 本段覆盖几站 —— 全段模式的范围只有后端调度知道，高德只知道它自己那几个途经点；
 * · 偏航 / 拥堵重算那一句 —— 只有高德那两条广播说过，而它不会把这件事打在面板上。
 * 撤掉的四行（转向动作 / 下一段路名 / 剩余里程与耗时 / 车道）全部由高德自带面板画，
 * 本页不再抄第二套 —— 两套的更新时机不同，司机一眼看到两个数。
 */
@Composable
private fun GuideTopStrip(
    modifier: Modifier = Modifier,
    destination: String,
    stopLine: String,
    reminderText: String?,
    recalcText: String,
) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    Column(
        modifier
            .fillMaxWidth()
            .background(t.primaryDark)
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(BusIcons.bus, contentDescription = null, tint = Color.White, modifier = Modifier.size((17 * scale).dp))
            Spacer(Modifier.width(7.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "乡邮智联 · 车道级导航",
                    color = Color.White,
                    fontSize = (12 * scale).sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    destination.ifEmpty { "任务点" },
                    color = Color.White,
                    fontSize = (15 * scale).sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (stopLine.isNotEmpty()) {
                    // 这一行写的是"这一趟导航覆盖几站"，不是后端状态：站点是否已完成只由后端说（`arrive` 之后回读）。
                    // 逐站模式（老调用点没有站表）时这一格是空串，一行都不占。
                    Text(
                        stopLine,
                        // 硬编码色：顶栏"本段 k 站"那行的浅蓝，压在深色导航底上（成对色）
                        color = Color(0xFFBFD3EA),
                        fontSize = (11.5 * scale).sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // 到车持续提醒（D-170，用户口径⑤）：车在近处、后端又说这一站没办完 ⇒ 这一行一直在。
            // 它是**非模态**的：不挡地图手势、不挡高德的转向卡，也不要求谁点它一下才肯走 ——
            // "持续"与"不影响驾驶"这两条要求同时满足的写法只有这一种，模态弹窗满足前者就会犯后者。
            reminderText?.let {
                Text(
                    it,
                    color = Color.White,
                    fontSize = (13.5 * scale).sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        // 偏航 / 拥堵重算只有高德那两条广播能说；没说过就不占行（不留一个"一切正常"的假结论）
        if (recalcText.isNotEmpty() && !recalcText.contains("没")) {
            Text(
                recalcText,
                color = Color.White,
                fontSize = (12 * scale).sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * 高德宣布"到达第 [index] 个途经点"（0 基，来自 SDK 回调原值）这一帧。
 *
 * [stop] 为 null 是有意保留的一格：下标越界时不许随便指一站顶上 ——
 * 那是把"高德说了到达"和"到了哪一站"两件事里较贵的那一件编出来。
 */
private data class ArrivedStop(
    val index: Int,
    val stop: com.shanxiang.buspost.data.model.MissionStop?,
    val known: Boolean,
)

/**
 * 到站那一格（用户口径："到任务点要揽收扫码，然后接着下一任务继续导航"）。
 *
 * 这一屏只做三件事：说清**高德说到了哪一站**（动作 / 件数都取后端 `stops`，本机不编）、
 * 给一个**回工作台作业**的出口、明说**到站状态不由这里写**。
 * 为什么不放"确认到站"按钮：`/driver/arrive` 要 `shiftId` 且后端按顺序校验防跳站
 * （`DriverAppServiceImpl:905`），而导航页手里只有进页时的站表快照，没有班次态 ——
 * 在这一屏写就是拿一份可能过期的快照去赌后端放行，而工作台那一面本来就有完整的确认链路。
 */
@Composable
private fun GuideArrivedCard(arrived: ArrivedStop, onGoWorkbench: () -> Unit) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .glassmorphic(
                t,
                shape = RoundedCornerShape(BusRadius.m),
                shadowElevation = BusElevation.floating,
                tint = if (t.isDark) t.cardWarm else t.goldLight,
            )
            .glassSheen(t, shape = RoundedCornerShape(BusRadius.m))
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                // 念的是 1 基"第几个"，存的是 0 基下标：这句只给司机看，日志里那一行给的是原值。
                if (arrived.known) {
                    "高德说已到第 ${arrived.index + 1} 个途经点 · ${arrived.stop!!.name.ifEmpty { "任务点#${arrived.stop.stationId}" }}"
                } else {
                    "高德说已到第 ${arrived.index + 1} 个途经点，但本趟站表里没有这一格"
                },
                color = t.goldDeep,
                fontSize = (13 * scale).sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (arrived.known) {
                    with(MissionMarkers) {
                        listOfNotNull(
                            arrived.stop!!.actionLabel.ifEmpty { null },
                            arrived.stop!!.quantityText(),
                            arrived.stop!!.statusLabel.ifEmpty { null },
                        ).joinToString(" · ")
                    }.ifEmpty { "这一站后端没给动作与件数" } + " · 到站状态回工作台确认后才写"
                } else "下标对不上时不猜是哪一站：回工作台核对任务列表",
                color = t.goldDeep,
                fontSize = (11.5 * scale).sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        BusButton(
            text = "回工作台作业",
            variant = BusButtonVariant.PRIMARY,
            small = true,
            onClick = onGoWorkbench,
        )
    }
}

