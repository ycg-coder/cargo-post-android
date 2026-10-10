package com.shanxiang.buspost.ui.driver

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import com.amap.api.navi.enums.AMapNaviViewShowMode
import com.shanxiang.buspost.BuildConfig
import com.shanxiang.buspost.core.AppState
import com.shanxiang.buspost.core.map.RouteCheck
import com.shanxiang.buspost.core.navigation.NavigationEngines
import com.shanxiang.buspost.core.navigation.NavigationGuidance
import com.shanxiang.buspost.core.navigation.NavigationSession
import com.shanxiang.buspost.core.navigation.engineRecalcTextOf
import com.shanxiang.buspost.core.navigation.guidanceSwitchOffText
import com.shanxiang.buspost.core.navigation.laneDisplayTextOf
import com.shanxiang.buspost.core.navigation.turnIconSpeechOf
import com.shanxiang.buspost.core.net.attempt
import com.shanxiang.buspost.core.ui.BusButton
import com.shanxiang.buspost.core.ui.BusButtonVariant
import com.shanxiang.buspost.core.ui.BusIcons
import com.shanxiang.buspost.core.ui.BusRadius
import com.shanxiang.buspost.core.ui.LocalBusDriving
import com.shanxiang.buspost.core.ui.LocalBusFontScale
import com.shanxiang.buspost.core.ui.LocalBusTokens
import com.shanxiang.buspost.core.ui.LocalBusTouch
import com.shanxiang.buspost.data.model.Geo
import com.shanxiang.buspost.data.store.Prefs
import com.shanxiang.buspost.driver.Running
import com.shanxiang.buspost.ui.common.LocalNavController
import com.shanxiang.buspost.ui.common.RouteUnavailableState

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
 * - 自绘信息条（顶部）只显示引擎**已经回过**的数据：没回就说"高德没给"，不用本机时钟造剩余里程。
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

    val engine = remember { NavigationEngines.create(context) }
    DisposableEffect(engine) {
        onDispose { engine.release() }
    }
    val readiness by engine.readiness.collectAsStateWithLifecycle()
    val frame by engine.guidance.collectAsStateWithLifecycle()
    val laneFrame by engine.lane.collectAsStateWithLifecycle()
    val recalc by engine.recalc.collectAsStateWithLifecycle()

    var phase by remember { mutableStateOf<GuidePhase>(GuidePhase.Waiting) }
    var session by remember { mutableStateOf<NavigationSession?>(null) }
    // 车头朝上 / 正北朝上：这是**高德视图的视角**，必须落到底层 setNaviMode 上，不能只改本地标签
    var carUp by remember { mutableStateOf(true) }

    // 算路：目的地 + 本机定位 + 引擎就绪 三者齐了才发请求。缺一个就照实说缺哪个。
    LaunchedEffect(target, me, readiness) {
        val dest = target
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
        if (phase is GuidePhase.Calculating || phase is GuidePhase.Ready) return@LaunchedEffect
        phase = GuidePhase.Calculating
        val result = attempt { engine.calculateRoute(me, Geo(dest.lat, dest.lng)) }
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

    val ready = phase is GuidePhase.Ready
    // 两条自绘条是**盖在全屏地图上**的，而 AMapNaviView 把自己的路口放大图 / 转向提示画在它表面的
    // 左上角 —— 于是那些控件画在我们的标题**底下**，既看不见也点不着（2026-10-10 真机实测：
    // `layout_audit` 报"进入 × 乡邮智联 · 车道级导航 重叠 50%"，且该节点被屏幕左边裁掉一半）。
    // 修法不是关掉放大图（司机要它），而是**量出两条自绘条的实际高度，把地图夹在中间**。
    // 手法对齐本文件同仓的 `StickyBarScaffold`（`DriverBits.kt`）：量 px 再换 dp，
    // **首帧没量到时给保守值而不是 0** —— 给 0 就是"地图先全屏、下一帧才缩"，那一帧里放大图照样被压。
    val density = androidx.compose.ui.platform.LocalDensity.current
    var topPx by remember { mutableIntStateOf(0) }
    var bottomPx by remember { mutableIntStateOf(0) }
    val topReserve = if (topPx == 0) 170.dp else with(density) { topPx.toDp() }
    val bottomReserve = if (bottomPx == 0) 96.dp else with(density) { bottomPx.toDp() } + 8.dp
    Box(Modifier.fillMaxSize().background(if (ready) Color.Black else t.paper)) {
        if (ready) {
            AmapNaviSurface(
                modifier = Modifier.fillMaxSize().padding(top = topReserve, bottom = bottomReserve),
                night = nightUi || driving,
                laneOn = Prefs.laneGuidance,
                carUp = carUp,
            )
            // ---- 自绘信息条（顶部）：品牌 + 下一动作 + 剩余里程/时间 + 车道 ----
            GuideTopStrip(
                modifier = Modifier.align(Alignment.TopCenter).onSizeChanged { topPx = it.height },
                destination = target?.name.orEmpty(),
                frame = frame,
                laneText = if (!Prefs.laneGuidance) guidanceSwitchOffText("车道级指引", "已关闭")
                else laneDisplayTextOf(laneFrame),
                recalcText = engineRecalcTextOf(recalc),
                onExit = exit,
            )
            // ---- 底部：语音 / 视角 / 结束 ----
            GuideBottomBar(
                modifier = Modifier.align(Alignment.BottomCenter).onSizeChanged { bottomPx = it.height },
                voiceOn = Prefs.voiceGuidance,
                onToggleVoice = { on ->
                    Prefs.voiceGuidance = on
                    // 高德内置播报是进程单例上的开关，改完必须重起一次导航才生效；
                    // 这里如实重起，而不是只改本地开关让司机以为已经静音了。
                    engine.stopNavi()
                    session = engine.startGpsNavi(on)
                },
                carUp = carUp,
                onToggleCarUp = { carUp = it },
                onStop = exit,
            )
            (session as? NavigationSession.Refused)?.let { refused ->
                // 算路过了但 startNavi 被拒：这一条同样要露出来，不能静默停在"看着像在导"
                Text(
                    refused.reason,
                    color = Color.White,
                    fontSize = (12 * scale).sp,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(BusRadius.m))
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
                    guidePhaseDetail(phase, target?.name.orEmpty()),
                    color = t.textSecondary,
                    fontSize = (13 * scale).sp,
                )
                if (phase is GuidePhase.Calculating) {
                    Text("正在请高德按真实路网算一条道路…", color = t.textTertiary, fontSize = (12 * scale).sp)
                }
                RouteUnavailableState(
                    height = 150.dp,
                    reason = guidePhaseDetail(phase, target?.name.orEmpty()),
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
        "本机还没有定位点，算不出起点。请先在工作台打开「位置上报（前台服务）」，等坐标出现后再进这一页。"
    is GuidePhase.Calculating -> "正在向高德请求真实道路…"
    is GuidePhase.EngineBlocked -> "导航组件不可用：${p.reason}"
    is GuidePhase.Refused -> "高德没能给出可用道路：${p.reason}"
    is GuidePhase.Ready ->
        "已就绪：前往${destination.ifEmpty { "任务点" }}（高德道路 ${p.roadMeters} 米 / " +
            "${if (p.roadEtaSeconds > 0) "${p.roadEtaSeconds / 60} 分钟" else "耗时未给"}）"
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
) {
    var failed by remember { mutableStateOf<String?>(null) }
    if (BuildConfig.AMAP_KEY.isBlank()) {
        failed = "地图服务未配置，导航视图无法启动"
    }
    var viewRef by remember { mutableStateOf<AMapNaviView?>(null) }

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
                            // true = "退出由我自己处理"，高德就不再弹它自己的退出确认框
                            override fun onNaviBackClick(): Boolean = true
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
                    failed = "高德导航视图在本机装不起来：${e.javaClass.simpleName}" +
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
        DisposableEffect(Unit) {
            onDispose {
                viewRef?.let { v ->
                    runCatching { v.onPause() }
                    runCatching { v.onDestroy() }
                }
                viewRef = null
            }
        }
    }

    failed?.let { reason ->
        // 视图起不来时的实话 + 出路：这一屏不许变成一块黑屏
        Box(
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
                    tint = Color(0xFFE8C271),
                    modifier = Modifier.size(34.dp),
                )
                Text("车道级导航在本机不可用", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(reason, color = Color(0xFFD6D0C2), fontSize = 12.5.sp)
                Text(
                    "可以退回工作台，用「交给高德 / 交给百度」把目的地交给手机里装的导航 App。",
                    color = Color(0xFFB3AC9B),
                    fontSize = 12.sp,
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
 * `setNaviNight` / `setCompassEnabled`），视角走 `AMapNaviView.setNaviMode` +
 * `setShowMode`（常量 `CAR_UP_MODE=0` / `NORTH_UP_MODE=1` / `SHOW_MODE_LOCK_CAR=1`，同样是 javap 取的值）。
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
    v.viewOptions = o
    v.setNaviMode(if (carUp) AMapNaviView.CAR_UP_MODE else AMapNaviView.NORTH_UP_MODE)
    v.setShowMode(AMapNaviViewShowMode.SHOW_MODE_LOCK_CAR)
}

/**
 * 顶部自绘信息条（用户指定的"自绘信息条"那一半）。
 *
 * 为什么自绘而不是全靠高德自带面板：品牌口径（乡邮智联 / 站牌色）要跟全站一致，
 * 退出这一屏的按钮也要在**固定位置** —— 司机不必在别人的 UI 里找出口。
 * 数据一律取引擎回调（[NavigationGuidance] / 车道帧），本机不造：没回就写"高德没给"。
 */
@Composable
private fun GuideTopStrip(
    modifier: Modifier = Modifier,
    destination: String,
    frame: NavigationGuidance?,
    laneText: String,
    recalcText: String,
    onExit: () -> Unit,
) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val touch = LocalBusTouch.current
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
            }
            // 退出按钮：**实心白底 + 深色字**（对比度稳、驾驶中一眼能认），并吃满触控下限
            Text(
                "退出",
                color = t.primaryDark,
                fontSize = (14 * scale).sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(BusRadius.pill))
                    .background(Color.White)
                    .clickable(onClick = onExit)
                    .defaultMinSize(minWidth = touch.min, minHeight = touch.min)
                    .padding(horizontal = 18.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(
                    // 转向动作只查 javap 核对过的 IconType 映射表；表外的码如实说"不在这张表里"
                    // （与 DriverSdkNavCard 同一口径：`turnIconSpeechOf` 返回 null 不等于"没有帧"）
                    frame?.let { turnIconSpeechOf(it.iconType) ?: "转向码 ${it.iconType}（不在已核对的表里）" }
                        ?: "等待高德第一帧",
                    color = Color.White,
                    fontSize = (22 * scale).sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (frame != null) {
                    Text(
                        listOfNotNull(
                            frame.currentRoad.ifEmpty { null },
                            frame.nextRoad.ifEmpty { null }?.let { "下一段 $it" },
                        ).joinToString(" · "),
                        color = Color.White,
                        fontSize = (12.5 * scale).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    // 剩余里程：来源没给就写"高德没给"，不用直线距离或经验值补（硬规则）
                    frame?.let { if (it.hasRemainDistance) fmtMeters(it.remainMeters) else "高德没给" } ?: "—",
                    color = Color.White,
                    fontSize = (20 * scale).sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    frame?.let { if (it.remainSeconds > 0) "${it.remainSeconds / 60} 分钟" else "高德没给" } ?: "—",
                    color = Color.White,
                    fontSize = (13 * scale).sp,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                BusIcons.laneSign,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size((14 * scale).dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                laneText,
                color = Color.White,
                fontSize = (12 * scale).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
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

@Composable
private fun GuideBottomBar(
    modifier: Modifier = Modifier,
    voiceOn: Boolean,
    onToggleVoice: (Boolean) -> Unit,
    carUp: Boolean,
    onToggleCarUp: (Boolean) -> Unit,
    onStop: () -> Unit,
) {
    val t = LocalBusTokens.current
    Row(
        modifier
            .fillMaxWidth()
            .background(t.card)
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BusButton(
            text = if (voiceOn) "播报已开" else "播报已关",
            icon = if (voiceOn) BusIcons.volume else BusIcons.volumeOff,
            variant = if (voiceOn) BusButtonVariant.CLAY else BusButtonVariant.OUTLINE,
            modifier = Modifier.weight(1f),
            onClick = { onToggleVoice(!voiceOn) },
        )
        BusButton(
            text = if (carUp) "车头朝上" else "正北朝上",
            icon = BusIcons.compass,
            variant = BusButtonVariant.OUTLINE,
            modifier = Modifier.weight(1f),
            onClick = { onToggleCarUp(!carUp) },
        )
        BusButton(
            text = "结束导航",
            icon = BusIcons.stopCircle,
            variant = BusButtonVariant.DANGER,
            modifier = Modifier.weight(1f),
            onClick = onStop,
        )
    }
}

private fun fmtMeters(meters: Int): String = when {
    meters <= 0 -> "0 米"
    meters < 1000 -> "$meters 米"
    else -> "%.1f 公里".format(meters / 1000.0)
}
