package com.shanxiang.buspost.ui.driver

import android.content.Context
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shanxiang.buspost.core.ui.BusButton
import com.shanxiang.buspost.core.ui.BusButtonVariant
import com.shanxiang.buspost.core.ui.BusCard
import com.shanxiang.buspost.core.ui.BusDivider
import com.shanxiang.buspost.core.ui.BusIcons
import com.shanxiang.buspost.core.ui.BusPage
import com.shanxiang.buspost.core.ui.BusRadius
import com.shanxiang.buspost.BuildConfig
import com.shanxiang.buspost.core.map.RouteSource
import com.shanxiang.buspost.core.net.attempt
import com.shanxiang.buspost.core.ui.BusSwitch
import com.shanxiang.buspost.core.ui.BusTag
import com.shanxiang.buspost.core.ui.BusTone
import com.shanxiang.buspost.core.ui.ErrorState
import com.shanxiang.buspost.core.ui.InfoRow
import com.shanxiang.buspost.core.ui.LocalBusFontScale
import com.shanxiang.buspost.core.ui.LocalBusTokens
import com.shanxiang.buspost.core.ui.RoadLine
import com.shanxiang.buspost.core.ui.SectionTitle
import com.shanxiang.buspost.core.ui.Signpost
import com.shanxiang.buspost.core.ui.SourceTag
import com.shanxiang.buspost.data.model.Geo
import com.shanxiang.buspost.data.model.GeoMath
import com.shanxiang.buspost.data.model.NavIcon
import com.shanxiang.buspost.data.model.NavStep
import com.shanxiang.buspost.data.model.RouteGuide
import com.shanxiang.buspost.data.repo.Repo
import com.shanxiang.buspost.data.store.Prefs
import com.shanxiang.buspost.driver.DriverLocationService
import com.shanxiang.buspost.driver.Running
import com.shanxiang.buspost.ui.common.DemoNote
import com.shanxiang.buspost.ui.common.LocalNavController
import com.shanxiang.buspost.ui.common.RouteMap
import com.shanxiang.buspost.ui.common.ScreenViewModel
import java.util.Locale
import kotlinx.coroutines.delay
import kotlin.math.min

/**
 * **调试入口，不是正式导航**（DECISION_LOG D-003 / 任务 #51）。
 *
 * 正式包的导航走工作台那一节：`NavigationEngine → DriverMissionMap` + [DriverSdkNavCard]（含外部地图交接）；
 * 这一屏整屏只在 `BuildConfig.DEBUG` 注册与可达，页面上每一块都写清它是不是后端真数据。
 * 保留说明（用户硬规则"不要直接删除文件"）：文件与旧路由的兼容入口都在，被删掉的只有那套假步进 ——
 * 4 秒定时器逐条翻页的路径已经不存在，翻看指引只能是司机自己点。
 *
 * 注意口径：`com.amap.api:navi-3dmap` 已进入依赖（赛道第 8 步），但**这一屏的转向与折线仍然只读后端线路**，
 * 两者不互相顶替。
 */
class DriverNavViewModel : ScreenViewModel() {
    var guide by mutableStateOf(RouteGuide.EMPTY)
        private set

    /** 当前指引步骤：优先跟本地推进，其次跟后端 currentStep */
    var stepIndex by mutableStateOf(0)
    var voice by mutableStateOf(Prefs.voiceGuidance)
    var lanesOn by mutableStateOf(Prefs.laneGuidance)

    /** 前台服务上报到的真实车位置；离线时退回接口里的模拟位置 */
    var liveGeo by mutableStateOf<Geo?>(null)
    var liveSpeed by mutableStateOf(0)

    val step: NavStep? get() = guide.steps.getOrNull(stepIndex)

    val vehicleGeo: Geo? get() = liveGeo ?: guide.vehicleGeo

    /** 剩余里程：从当前步骤往后累加 */
    val remainingMeters: Int
        get() = guide.steps.drop(stepIndex).sumOf { it.distanceMeters }

    /**
     * 车到线路折线的**最小垂距**，一个本机自己算出来的测量值。
     *
     * ⚠ 它不再参与任何偏航判定（指令 v2.1 第 ⑪ 条"不得本地假判定替代后端"）：
     * 从前这里挂着"超过 60 米即算偏航"，比"等后端说偏航"更及时 —— 那句"更及时"就是问题所在：
     * 阈值是本机自己挑的，没有任何一方（后端或高德）认可过它，而它产出的结论写着"已偏离线路"，
     * 司机按它重取线路、调度按后端那条位看数据，两边就不是同一件事了。
     * 导航进行中的偏航只能由高德的 `onReCalculateRouteForYaw()` 说（正式导航面那张卡片那一格）。
     */
    fun crossTrackMeters(): Double {
        val p = vehicleGeo ?: return 0.0
        val line = guide.polyline
        if (line.size < 2) return 0.0
        var best = Double.MAX_VALUE
        for (i in 0 until line.size - 1) {
            best = min(best, distToSegment(p, line[i], line[i + 1]))
        }
        return best
    }

    private fun distToSegment(p: Geo, a: Geo, b: Geo): Double {
        val k = (a.lat - p.lat) * (b.lat - a.lat) + (a.lng - p.lng) * (b.lng - a.lng)
        val len = (b.lat - a.lat) * (b.lat - a.lat) + (b.lng - a.lng) * (b.lng - a.lng)
        val t = if (len == 0.0) 0.0 else (-k / len).coerceIn(0.0, 1.0)
        val proj = Geo(a.lat + t * (b.lat - a.lat), a.lng + t * (b.lng - a.lng))
        return GeoMath.distance(p, proj)
    }

    fun load() {
        work(reset = guide.steps.isEmpty()) {
            val one = Repo.routeGuide()
            guide = one.value ?: RouteGuide.EMPTY
            demo = one.demo
            if (guide.steps.isNotEmpty() && stepIndex !in guide.steps.indices) {
                stepIndex = guide.currentStep.coerceIn(0, guide.steps.size - 1)
            }
        }
    }

    /**
     * 翻看后端指引的下一条。**只是翻页**：车的位置与班次状态都不由它推进，
     * 推进只认后端 `currentStep` 与 GPS —— 原来那个"4 秒定时器自己往后翻一条"的实现已经删除（§24）。
     */
    fun showNextStep() {
        if (guide.steps.isEmpty()) return
        stepIndex = guide.steps.indices.firstOrNull { it > stepIndex } ?: 0
    }

    fun applyVoice(value: Boolean) {
        voice = value
        Prefs.voiceGuidance = value
    }

    fun applyLanes(value: Boolean) {
        lanesOn = value
        Prefs.laneGuidance = value
    }
}

@Composable
fun DriverNavScreen(vm: DriverNavViewModel = viewModel()) {
    // 第二道闸：正式包不注册这条路由（见 BusPostRoot），残留入口或旧深链打进来只看到这一句，不渲染任何指引
    if (!BuildConfig.DEBUG) {
        DebugEntryClosed()
        return
    }
    val context = LocalContext.current
    val nav = LocalNavController.current
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val serviceGeo by Running.geo.collectAsStateWithLifecycle()
    val serviceSpeed by Running.speed.collectAsStateWithLifecycle()
    val reporting = Running.ok
    var toast by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (vm.guide.steps.isEmpty()) vm.load()
    }
    LaunchedEffect(serviceGeo, serviceSpeed) {
        vm.liveGeo = serviceGeo
        vm.liveSpeed = serviceSpeed
    }
    LaunchedEffect(toast) {
        val msg = toast ?: return@LaunchedEffect
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        toast = null
    }

    val engine = rememberTts(context)
    LaunchedEffect(vm.stepIndex, vm.voice) {
        val s = vm.step ?: return@LaunchedEffect
        if (!vm.voice || engine == null) return@LaunchedEffect
        speak(engine, "${s.text}，${formatMeters(s.distanceMeters)}后${s.detail}")
    }

    val g = vm.guide
    val step = vm.step
    val pos = vm.vehicleGeo
    val roadOk = g.hasRealRoad
    // 指令 v2.1 第 ⑪ 条：这一屏的"偏航"只由后端那个位说，本机不再拿垂距卡阈值顶替它（旧写法是把
    // 后端的位与本机算的垂距阈值 OR 在一起，而那个阈值是本机自己挑的，没有任何来源认可过）。
    val offRoute = roadOk && g.deviated
    val nextName = g.nextStation.ifEmpty { step?.text ?: "" }
    val usingGps = serviceGeo != null

    BusPage(spacedBy = 12.dp) {
        Signpost(
            title = "旧导航页（调试入口 · 非真实导航）",
            subtitle = if (g.stations.size >= 2) "${g.stations.first().name} → ${g.stations.last().name}" else "实际道路轨迹，这一屏的转向只读它",
            badge = if (roadOk) "剩余 ${formatMeters(vm.remainingMeters)}" else "暂无真实道路",
        ) {
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                BusTag(if (reporting) "定位上报中" else "未开启上报", tone = if (reporting) BusTone.PRIMARY else BusTone.NEUTRAL)
                BusTag(if (vm.liveSpeed > 0) "${vm.liveSpeed} km/h" else "车速 0", tone = BusTone.CLAY)
                SourceTag(if (usingGps) "GPS 定位" else g.dataSource)
            }
        }

        // D-003：这一屏只在调试包可达，页头把"哪里不实"写清楚，正式导航在工作台那一节
        BusCard(modifier = Modifier.fillMaxWidth(), warm = true) {
            Column {
                Text("非真实导航：这一屏只在调试包打开", color = t.ink, fontSize = (14 * scale).sp, fontWeight = FontWeight.Bold)
                Text(
                    "逐条/自动推进已删除，翻页只在司机自己点；车道箭头是按车道名文字猜的图形，没有车道图形。" +
                        "正式导航走工作台的「本班任务地图」+ 导航引擎。",
                    color = t.textSecondary,
                    fontSize = (11.5 * scale).sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        DemoNote(
            vm.demo,
            text = "暂无线路指引，当前是演示道路与车道数据，转向逻辑真实但位置为模拟。",
        )
        vm.error?.let { ErrorState(it, onRetry = { vm.load() }) }

        if (!roadOk) {
            NavUnavailableBanner(reason = g.routeUnavailableReason ?: "暂无真实道路轨迹", onRetry = { vm.load() })
        }

        if (offRoute) {
            // 成因只剩一个：后端那个位。从前这里还传 `serverSide` 与本机算的 `meters`，
            // 为的是让横幅自己解释"是后端说的还是本机说的"——本机那一支已经不成立，就不留着半边假分支。
            DeviationBanner(onReroute = { vm.load(); toast = "已重新拉取线路指引" })
        }

        // 转向 HUD：行驶中只需瞟一眼。没有真实道路折线时不展示转向 —— 转向必须挂在道路数据上
        if (roadOk && step != null) {
            NavHud(
                step = step,
                nextStation = nextName.ifEmpty { step.text },
                // 只读 *Road* 字段：后端 nextStopDistanceMeters 是直线值，正式界面禁止当剩余里程（CONTEXT_LOCK §四）
                remaining = if (g.nextStopRoadDistanceMeters > 0 && vm.stepIndex == g.currentStep) g.nextStopRoadDistanceMeters else step.distanceMeters,
                etaMinutes = g.etaMinutes,
                index = vm.stepIndex,
                total = g.steps.size,
                progress = if (g.steps.size <= 1) 0f else vm.stepIndex.toFloat() / (g.steps.size - 1),
            )
        }

        if (roadOk && vm.lanesOn && step != null && step.lanes.isNotEmpty()) {
            LaneGuideStrip(lanes = step.lanes, recommended = step.recommendedLane)
        }

        SectionTitle("线路图")
        RouteMap(
            polyline = g.polyline,
            stations = g.stations,
            me = pos,
            highlightStationId = g.stations.getOrNull(min(vm.stepIndex, (g.stations.size - 1).coerceAtLeast(0)))?.id ?: 0L,
            height = 240.dp,
            reason = g.routeUnavailableReason
                ?: "暂无这一班的真实道路轨迹。导航需要道路数据，这里不用站点连线代替。",
            onReplan = { vm.load() },
        )
        BusCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                InfoRow("车辆位置", if (g.vehicleGeo != null) "%.5f, %.5f".format(g.vehicleGeo!!.lat, g.vehicleGeo!!.lng) else "暂未知")
                InfoRow(
                    "我的位置",
                    if (pos != null) "%.5f, %.5f".format(pos.lat, pos.lng) else if (reporting) "等待定位信号" else "未开启上报",
                )
                // 从前这一行叫"偏离线路"，值是同一个数：0 米会被读成"正好在线上"，而 `crossTrackMeters()`
                // 在没定位或折线不足两点时返回的 0 是**算不出**，不是量出来的 0。改名并把"算不出"分开写，
                // 顺带在值里写明它是本机算的距离 —— 它不参与任何偏航结论（指令 ⑪）。
                InfoRow(
                    "到折线垂距",
                    when {
                        !roadOk -> "暂无道路轨迹，算不出垂距"
                        pos == null -> "暂无定位，算不出垂距"
                        else -> "%.0f 米（估算距离，非偏航判定）".format(vm.crossTrackMeters())
                    },
                )
                InfoRow("行驶朝向", if (roadOk) "${g.headingDegrees.toInt()}°" else "—")
                InfoRow("道路来源", if (roadOk) RouteSource.parse(g.routeSourceName).label else "—")
                InfoRow(
                    "道路里程",
                    if (g.roadDistanceMeters > 0) formatMeters(g.roadDistanceMeters) else "暂未知",
                )
                InfoRow(
                    "下一站道路里程",
                    if (g.nextStopRoadDistanceMeters > 0) formatMeters(g.nextStopRoadDistanceMeters)
                    else "暂未知",
                )
                BusDivider(Modifier.padding(vertical = 4.dp))
                BusButton(
                    text = if (reporting) "位置上报运行中，点击停止" else "开启位置上报（前台服务）",
                    variant = if (reporting) BusButtonVariant.DANGER else BusButtonVariant.CLAY,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    onClick = {
                        if (reporting) {
                            DriverLocationService.stop(context)
                            vm.liveGeo = null
                            toast = "已停止上报"
                        } else {
                            DriverLocationService.start(context)
                            toast = "已请求开启：需要位置权限，且在系统设置里允许后台位置"
                        }
                    },
                )
            }
        }

        // 本机导航引擎卡片已经不在这一屏：正式导航面在工作台（DriverMissionMap + DriverSdkNavCard），
        // 引擎只挂一处，调试页不重复创建第二个 AMapNavi 实例。
        SectionTitle("语音与车道", subtitle = "行驶中不必盯屏")
        BusCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(BusIcons.volume, contentDescription = null, tint = t.primary, modifier = Modifier.size((18 * scale).dp))
                    Spacer(Modifier.width(8.dp))
                    Text("播报转向指引", color = t.ink, fontSize = (14 * scale).sp, modifier = Modifier.weight(1f))
                    BusSwitch(checked = vm.voice, onCheckedChange = { vm.applyVoice(it) })
                }
                BusDivider(Modifier.padding(vertical = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(BusIcons.route, contentDescription = null, tint = t.clay, modifier = Modifier.size((18 * scale).dp))
                    Spacer(Modifier.width(8.dp))
                    Text("显示车道引导", color = t.ink, fontSize = (14 * scale).sp, modifier = Modifier.weight(1f))
                    BusSwitch(checked = vm.lanesOn, onCheckedChange = { vm.applyLanes(it) })
                }
                // 逐条推进的开关已删除：定时器驱动的步骤不是导航，正式包更没有这条路径
            }
        }

        if (roadOk) {
            SectionTitle("全程指引", subtitle = "共 ${g.steps.size} 条")
            if (g.steps.isEmpty()) {
                Text("暂无指引步骤", color = t.textTertiary, fontSize = (13 * scale).sp)
            }
            g.steps.forEachIndexed { i, s ->
                StepRow(
                    step = s,
                    index = i,
                    current = i == vm.stepIndex,
                    passed = i < vm.stepIndex,
                    onClick = { vm.stepIndex = i },
                )
            }

            // 只是翻看后端指引，不代表车已经走到：位置与业务状态都不由这个按钮推进
            BusButton(
                text = "查看下一条指引：${nextName.ifEmpty { "—" }}" +
                    (if (g.nextStopRoadDistanceMeters > 0) " · ${formatMeters(g.nextStopRoadDistanceMeters)}" else ""),
                onClick = { vm.showNextStep() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            )
        }
        BusButton(
            text = "回到工作台",
            variant = BusButtonVariant.TEXT,
            onClick = { nav.popBackStack() },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 转向看板：深色底 + 大号转向箭头，仿车载 HUD，阳光下可读 */
@Composable
private fun NavHud(
    step: NavStep,
    nextStation: String,
    remaining: Int,
    etaMinutes: Int?,
    index: Int,
    total: Int,
    progress: Float,
) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(BusRadius.l))
            .background(if (t.isDark) t.ink.copy(alpha = 0.55f) else Color(0xFF16232E))
            .padding(horizontal = 16.dp, vertical = 15.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NavArrow(step.icon, size = 46.dp, tint = if (step.icon == NavIcon.ARRIVE) t.gold else Color.White)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    if (remaining > 0) formatMeters(remaining) else "即达",
                    color = Color.White,
                    fontSize = (30 * scale).sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    step.text,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = (15 * scale).sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text("第 ${index + 1}/${total} 段", color = Color.White.copy(alpha = 0.65f), fontSize = (11.5 * scale).sp)
                Text(
                    // 只有通过真实道路校验的 roadEtaSeconds 才会是非 null；没有就是没有，不写"约 0 分钟"
                    etaMinutes?.let { "全程约 $it 分钟" } ?: "全程时长暂未知",
                    color = t.gold,
                    fontSize = (11.5 * scale).sp,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        if (step.detail.isNotEmpty()) {
            Text(
                step.detail,
                color = Color.White.copy(alpha = 0.75f),
                fontSize = (12.5 * scale).sp,
                modifier = Modifier.padding(top = 11.dp),
            )
        }
        if (nextStation.isNotEmpty()) {
            Row(
                Modifier
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(BusRadius.pill))
                    .background(Color.White.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(BusIcons.flag, contentDescription = null, tint = t.gold, modifier = Modifier.size((14 * scale).dp))
                Text(
                    "下一站 $nextStation",
                    color = Color.White,
                    fontSize = (12 * scale).sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
        RoadLine(modifier = Modifier.padding(top = 12.dp), progress = progress)
    }
}

/** 车道引导条：推荐车道高亮 + 箭头，其余车道灰显，与内置转向同源 */
@Composable
private fun LaneGuideStrip(lanes: List<String>, recommended: Int?) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    BusCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("车道引导", color = t.ink, fontSize = (14 * scale).sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                if (recommended != null && recommended in lanes.indices) {
                    BusTag("推荐走 ${recommended + 1} 车道", tone = BusTone.PRIMARY)
                } else {
                    // 后端没给建议车道 ⇒ 什么都不标。旧写法读成 0，于是这条会指着第 1 车道说"推荐走 1 车道"。
                    BusTag("暂无建议车道", tone = BusTone.NEUTRAL)
                }
            }
            Text(
                "车道名与建议车道来自演示数据，箭头图形是按名字猜的，不算车道级引导",
                color = t.textTertiary,
                fontSize = (10.5 * scale).sp,
                modifier = Modifier.padding(top = 3.dp),
            )
            Row(
                Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                lanes.forEachIndexed { i, name ->
                    val hot = i == recommended
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(BusRadius.m))
                            .background(if (hot) t.primary else t.track)
                            .padding(vertical = 9.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        NavArrow(
                            laneIcon(name),
                            size = 18.dp,
                            tint = if (hot) t.onPrimary else t.textTertiary,
                            dim = !hot,
                        )
                        Text(
                            name,
                            color = if (hot) t.onPrimary else t.textSecondary,
                            fontSize = (10.5 * scale).sp,
                            maxLines = 2,
                            fontWeight = if (hot) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(top = 5.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun laneIcon(name: String): NavIcon = when {
    name.contains("左转") -> NavIcon.TURN_LEFT
    name.contains("右转") -> NavIcon.TURN_RIGHT
    name.contains("掉头") -> NavIcon.UTURN_LEFT
    name.contains("合流") || name.contains("匝道") -> NavIcon.MERGE
    name.contains("公交") -> NavIcon.STRAIGHT
    else -> NavIcon.STRAIGHT
}

@Composable
private fun StepRow(step: NavStep, index: Int, current: Boolean, passed: Boolean, onClick: () -> Unit) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(BusRadius.m))
            .background(if (current) t.primaryLight else t.card)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.Top,
    ) {
        NavArrow(
            step.icon,
            size = 20.dp,
            tint = when {
                current -> t.primaryOnLight
                passed -> t.textTertiary else -> t.clayDeep
            },
            dim = passed,
        )
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                step.text,
                color = if (passed) t.textTertiary else t.ink,
                fontSize = (13.5 * scale).sp,
                fontWeight = if (current) FontWeight.Bold else FontWeight.Medium,
            )
            if (step.detail.isNotEmpty()) {
                Text(
                    step.detail,
                    color = t.textTertiary,
                    fontSize = (11.5 * scale).sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                formatMeters(step.distanceMeters),
                color = if (current) t.primary else t.textSecondary,
                fontSize = (12.5 * scale).sp,
                fontWeight = FontWeight.Bold,
            )
            val lane = step.recommendedLane
            if (lane != null && lane in step.lanes.indices) {
                Text(
                    "${lane + 1} 车道",
                    color = t.clayDeep,
                    fontSize = (10.5 * scale).sp,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun DeviationBanner(onReroute: () -> Unit) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    BusCard(modifier = Modifier.fillMaxWidth(), warm = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(BusIcons.warning, contentDescription = null, tint = t.danger, modifier = Modifier.size((22 * scale).dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("路线偏离", color = t.danger, fontSize = (14 * scale).sp, fontWeight = FontWeight.Bold)
                Text(
                    "这一句只由路线偏离状态决定；导航进行中的偏航由导航引擎回调判定。",
                    color = t.textSecondary,
                    fontSize = (11.5 * scale).sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            BusButton("重取线路", small = true, variant = BusButtonVariant.OUTLINE, onClick = onReroute)
        }
    }
}

/**
 * 导航不可用横幅：没有真实道路折线时，这里就是本页的主要产物。
 * 不做转向、不做车道、不做偏航判定 —— 那些都必须挂在道路数据之上。
 */
@Composable
private fun NavUnavailableBanner(reason: String, onRetry: () -> Unit) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    BusCard(modifier = Modifier.fillMaxWidth(), warm = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(BusIcons.warning, contentDescription = null, tint = t.goldDeep, modifier = Modifier.size((22 * scale).dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("暂时不能导航", color = t.goldDeep, fontSize = (14 * scale).sp, fontWeight = FontWeight.Bold)
                Text(
                    "$reason。宁可暂时没有导航，也不给司机画一条不存在的路。可先用下方外部地图按起终点导航。",
                    color = t.textSecondary,
                    fontSize = (11.5 * scale).sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            BusButton("重新拉取", small = true, variant = BusButtonVariant.OUTLINE, onClick = onRetry)
        }
    }
}

/** 转向箭头：一个矢量箭头按角度旋转，避免依赖具体图标名 */@Composable
private fun NavArrow(icon: NavIcon, size: androidx.compose.ui.unit.Dp, tint: Color, dim: Boolean = false) {
    val angle = when (icon) {
        NavIcon.DEPART -> 0f
        NavIcon.STRAIGHT -> 0f
        NavIcon.SLIGHT_RIGHT -> 40f
        NavIcon.TURN_RIGHT -> 90f
        NavIcon.UTURN_RIGHT -> 135f
        NavIcon.SLIGHT_LEFT -> -40f
        NavIcon.TURN_LEFT -> -90f
        NavIcon.UTURN_LEFT -> -135f
        NavIcon.MERGE -> 25f
        NavIcon.ARRIVE -> 0f
    }
    Box(
        Modifier
            .size(size)
            .alpha(if (dim) 0.45f else 1f),
        contentAlignment = Alignment.Center,
    ) {
        if (icon == NavIcon.ARRIVE) {
            Canvas(Modifier.size(size)) {
                val r = this.size.minDimension / 2
                drawCircle(color = tint.copy(alpha = 0.22f), radius = r)
                drawCircle(color = tint, radius = r * 0.42f, center = Offset(r, r), style = Stroke(width = r * 0.16f))
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(r - r * 0.1f, r * 0.18f),
                    size = Size(r * 0.2f, r * 1.5f),
                    cornerRadius = CornerRadius(r * 0.1f),
                )
            }
        } else {
            Icon(
                BusIcons.arrowUp,
                contentDescription = icon.name,
                tint = tint,
                modifier = Modifier
                    .size(size * 0.8f)
                    .rotate(angle),
            )
        }
    }
}

private fun formatMeters(meters: Int): String = when {
    meters <= 0 -> ""
    meters < 1000 -> "${meters} 米"
    else -> "%.1f 公里".format(meters / 1000.0)
}

/* ---------- 语音播报 ---------- */

@Composable
private fun rememberTts(context: Context): TextToSpeech? {
    val ready = remember { mutableStateOf(false) }
    val engine = remember(context) {
        runCatching {
            TextToSpeech(context.applicationContext) { status -> ready.value = status == TextToSpeech.SUCCESS }
        }.getOrNull()
    }
    LaunchedEffect(ready.value) {
        // attempt 而非 runCatching：这一页随时可能因返回而被取消，取消不该读成"语种设置失败"
        if (ready.value) attempt { engine?.setLanguage(Locale.CHINESE) }
    }
    DisposableEffect(engine) {
        onDispose { engine?.shutdown() }
    }
    return engine
}

private fun speak(engine: TextToSpeech, text: String) {
    runCatching { engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nav") }
}

/** 正式包的第二道闸：不加载线路、不渲染转向、不播语音，只说这一屏没开 */
@Composable
private fun DebugEntryClosed() {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    BusPage {
        BusCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text("这一屏只在调试包开放", color = t.ink, fontSize = (15 * scale).sp, fontWeight = FontWeight.Bold)
                Text(
                    "旧导航页不算正式导航。正式导航在工作台的「本班任务地图」与导航引擎里。",
                    color = t.textSecondary,
                    fontSize = (12.5 * scale).sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
