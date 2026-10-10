package com.shanxiang.buspost.ui.driver

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shanxiang.buspost.core.AppState
import com.shanxiang.buspost.core.map.RouteCheck
import com.shanxiang.buspost.core.net.attempt
import com.shanxiang.buspost.core.navigation.NavigationEngines
import com.shanxiang.buspost.core.navigation.NavigationSession
import com.shanxiang.buspost.core.navigation.engineRecalcTextOf
import com.shanxiang.buspost.core.navigation.shouldAutoOpenGuide
import com.shanxiang.buspost.core.navigation.guidanceSwitchOffText
import com.shanxiang.buspost.core.navigation.laneDisplayTextOf
import com.shanxiang.buspost.core.navigation.naviVoiceTypeNameOf
import com.shanxiang.buspost.core.navigation.navigationGateOf
import com.shanxiang.buspost.core.navigation.nextNavigableStop
import com.shanxiang.buspost.core.navigation.turnIconSpeechOf
import com.shanxiang.buspost.core.ui.BusButton
import com.shanxiang.buspost.core.ui.BusButtonVariant
import com.shanxiang.buspost.core.ui.BusCard
import com.shanxiang.buspost.core.ui.BusDivider
import com.shanxiang.buspost.core.ui.BusIcons
import com.shanxiang.buspost.core.ui.BusTag
import com.shanxiang.buspost.core.ui.BusTone
import com.shanxiang.buspost.core.ui.InfoRow
import com.shanxiang.buspost.core.ui.LocalBusFontScale
import com.shanxiang.buspost.core.ui.LocalBusTokens
import com.shanxiang.buspost.data.model.Geo
import com.shanxiang.buspost.data.model.RouteGuide
import com.shanxiang.buspost.data.store.Prefs
import com.shanxiang.buspost.ui.common.RouteMap
import kotlinx.coroutines.launch

/**
 * 正式导航面（D-003）：挂在司机工作台的「本班任务地图」下面，与 [DriverMissionMap] 同一节。
 * 旧 `DriverNavScreen` 转成 DEBUG ONLY 之后，release 里可达的导航就是这两块 + 本卡片的外部地图交接。
 *
 * 它**不替换**后端线路：任务地图画的仍然是 `/driver/route` 下发的真实道路（那是"这条班次的路"）；
 * 这一块只回答另一个问题 —— **设备本地能不能算出一条真实道路**，
 * 用于后端这一班没有下发折线时给司机一条真路，而不是没有路。
 *
 * 三条底线（硬规则"宁可暂时不能导航，不允许假导航"）：
 * - 引擎状态、剩余里程、当前道路全部来自高德回调（引擎创建之后页面只订阅 flow，没有本机定时器）；
 * - 高德算出来的折线同样要过 `RealRoadRouteValidator`，不过就显示原因；
 * - 目的地只认后端调度任务点（[nextNavigableStop]），调度没给就不算路、也不交给外部地图。
 */
@Composable
fun DriverSdkNavCard(
    guide: RouteGuide,
    vehicleGeo: Geo?,
    onToast: (String) -> Unit,
    onOpenLaneGuide: () -> Unit = {},
) {
    val context = LocalContext.current
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val scope = rememberCoroutineScope()

    val engine = remember { NavigationEngines.create(context) }
    DisposableEffect(engine) {
        onDispose { engine.release() }
    }

    val readiness by engine.readiness.collectAsStateWithLifecycle()
    val sdkFrame by engine.guidance.collectAsStateWithLifecycle()
    val sdkVoice by engine.voice.collectAsStateWithLifecycle()
    val sdkLane by engine.lane.collectAsStateWithLifecycle()
    val sdkRecalc by engine.recalc.collectAsStateWithLifecycle()
    var routeCheck by remember { mutableStateOf<RouteCheck?>(null) }
    var session by remember { mutableStateOf<NavigationSession?>(null) }
    var calculating by remember { mutableStateOf(false) }
    // 「本机引擎详情」是否展开。默认收起：这张卡十几行实时回执是**诊断**信息，
    // 而司机的正事是"点一下开始导航"，两者不该抢同一屏。
    var detailExpanded by remember { mutableStateOf(false) }

    // 到近播报（L-1）：本机 TTS 只播业务作业提醒，与高德的转向口令是两个引擎、两句话（见 ApproachAnnouncer）。
    val announcer = remember { ApproachAnnouncer(context) }
    DisposableEffect(announcer) {
        announcer.ensureTts()
        onDispose { announcer.shutdown() }
    }
    val approachLast by announcer.last.collectAsStateWithLifecycle()
    val approachStatus by announcer.status.collectAsStateWithLifecycle()
    // 高德剩余里程属于"我正在导的这个点"，只在算路成功时记下目的地；
    // 没记着就不把它配给后端的下一任务点（串站是到近播报最坏的错法）。
    var navigatingStopId by remember { mutableStateOf<Long?>(null) }
    // 每一帧只推给判据层一次：这里不测距、不设阈值、不造话术（L-1 的分工）
    LaunchedEffect(guide, navigatingStopId, sdkFrame) {
        val frame = sdkFrame
        val engineMeters = frame?.remainMeters?.takeIf { frame.hasRemainDistance }
        announcer.onFrame(ApproachFeed.of(guide, navigatingStopId, engineMeters))
    }

    val nextStop = nextNavigableStop(guide)
    // F-3 ④⑫：这一屏"能不能点、不能点时说什么"只有 navigationGateOf 这一个答案。
    // 它不读 BuildConfig —— 构建类型不是业务能力开关（旧假导航屏那 8 处 DEBUG 闸门是 D-003 红线要求的，不在这张卡片里）。
    val gate = navigationGateOf(
        readiness = readiness,
        hasOrigin = vehicleGeo != null,
        hasNavigableDestination = nextStop != null,
    )

    /**
     * 用户口径 A：在途司机打开 App 就直接给他导航；判断只有一份，住在
     * [shouldAutoOpenGuide]（纯函数）。这里只负责"跳一次"这件事的状态位与动作。
     */
    // 一次性：进程内已经自动跳过一次，就再也不自动跳（手动按钮永远在）
    LaunchedEffect(AppState.autoGuideUsed, gate.canNavigate, nextStop?.stationId) {
        val stop = nextStop ?: return@LaunchedEffect
        if (!shouldAutoOpenGuide(gate.canNavigate, true, AppState.autoGuideUsed)) return@LaunchedEffect
        AppState.autoGuideUsed = true
        android.util.Log.i("BUS_UI", "auto_open_guide stop=${stop.stationId} origin=${vehicleGeo?.lat},${vehicleGeo?.lng}")
        AppState.openGuide(
            AppState.GuideTarget(
                lat = stop.geo.lat,
                lng = stop.geo.lng,
                name = stop.name.ifEmpty { "任务点#${stop.stationId}" },
                stationId = stop.stationId,
            ),
        )
        onOpenLaneGuide()
    }

    // 界面上不再念诊断原文（引擎 / 算路 / 回执 / 字段名一律不进界面），
    // 但这些读数排障时必须有 —— 全部字段化进 logcat：adb logcat -s BUS_UI BUS_NAV
    androidx.compose.runtime.LaunchedEffect(gate.code, vehicleGeo, nextStop?.stationId, sdkFrame?.remainMeters) {
        android.util.Log.i(
            "BUS_UI",
            "nav_card gate=${gate.code} canNavigate=${gate.canNavigate} detail=${gate.detail} " +
                "origin=${vehicleGeo?.lat},${vehicleGeo?.lng} nextStop=${nextStop?.stationId}@${nextStop?.name} " +
                "remainMeters=${sdkFrame?.remainMeters} stops=${guide.stops.size} source=${guide.routeSourceName}",
        )
    }

    BusCard(modifier = Modifier.fillMaxWidth(), warm = gate.engineBlocked) {
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    "导航状态（高德）",
                    color = t.ink,
                    fontSize = (14.5 * scale).sp,
                    fontWeight = FontWeight.Bold,
                )
                BusTag(
                    gate.userText,
                    tone = when {
                        gate.canNavigate -> BusTone.PRIMARY
                        gate.engineBlocked -> BusTone.NEUTRAL
                        else -> BusTone.CLAY
                    },
                )
            }
            // 不可用时**不再在副标题重复状态**：上面那颗 chip 已经带了具体原因，
            // 而 `gate.detail` 是诊断原文（形如 `hasOrigin=false（…）`），摆在主面上等于把内部字段名给司机看。
            // 原文没删，挪到下面「展开更多」里那一格（诊断信息该待的地方）。
            if (gate.canNavigate) {
                Text(
                    "调度没给线路时，按当前车位让高德重新规划，给出剩余里程和预计到达。",
                    color = t.textTertiary,
                    fontSize = (11.5 * scale).sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // 车道级导航入口（本轮新增）置顶：它是这张卡真正要交给司机的动作 ——
            // 打开真实高德导航视图（转向大图 / 车道放大图 / 路口放大图 / 实时路况），
            // 而不是在本页读十几行实时回执。下面那些行留给"路上出问题时对账"用。
            BusButton(
                text = if (nextStop == null) "车道级导航（暂未指定目的地）" else "开始车道级导航",
                icon = BusIcons.navigation,
                variant = BusButtonVariant.CLAY,
                enabled = gate.canNavigate,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 9.dp),
                onClick = {
                    val stop = nextStop ?: return@BusButton
                    AppState.openGuide(
                        AppState.GuideTarget(
                            lat = stop.geo.lat,
                            lng = stop.geo.lng,
                            name = stop.name.ifEmpty { "任务点#${stop.stationId}" },
                            stationId = stop.stationId,
                        ),
                    )
                    onOpenLaneGuide()
                },
            )
            // 本机引擎详情默认收起。这张卡下面原本一口气铺了十余行 InfoRow
            // （当前位置 / 目的地 / 里程 / 耗时 / 折线点数 / 剩余里程 / 剩余时间 / 当前道路 / 下一段 /
            //  步骤号 / 转向 / 车道 / 重算 / 播报 / 到近），加起来两屏多，
            // 把"怎么走"这件正事的注意力全吃掉了 —— 工作台首屏因此永远看不到底下真正要按的东西。
            // 现在默认只留两行摘要，需要时再展开；信息一条都没删，只是从"一直占着屏"变成"要的时候在"。
            BusButton(
                text = if (detailExpanded) "收起更多" else "展开更多（定位 · 路线 · 里程 · 车道）",
                variant = BusButtonVariant.TEXT,
                icon = if (detailExpanded) BusIcons.expandLess else BusIcons.expandMore,
                small = true,
                modifier = Modifier.padding(top = 3.dp),
                onClick = { detailExpanded = !detailExpanded },
            )
            if (!detailExpanded) {
                // 收起态也必须回答"现在到底在不在导 / 还差多远"这两问，否则整张卡等于什么都没说
                // 收起态这行只说"差在哪"，不再把 chip 那句前缀复读一遍
                InfoRow("导航", if (gate.canNavigate) gate.userText else gate.reason)
                InfoRow(
                    "剩余里程",
                    sdkFrame?.let { if (it.hasRemainDistance) formatSdkMeters(it.remainMeters) else "暂未知" }
                        ?: "还没开始导航",
                )
            }

            if (detailExpanded) {
                BusDivider(Modifier.padding(vertical = 7.dp))
                // 诊断原文回到这里：收起时主面只有一句人话原因，要查"到底差哪一步"再展开看
                if (!gate.canNavigate) InfoRow("差在哪一步", gate.detail)
                InfoRow(
                    "当前位置",
                    if (vehicleGeo != null) "%.5f, %.5f".format(vehicleGeo.lat, vehicleGeo.lng) else "暂无定位",
                )
                InfoRow(
                    "导航目的地",
                    when {
                        nextStop == null -> "调度还没给未完成的任务点（硬规则 3：导航不自己决定去哪里）"
                        nextStop.name.isNotEmpty() -> nextStop.name
                        else -> "任务点#${nextStop.stationId}"
                    },
                )

                BusButton(
                    text = if (calculating) "正在计算路线…" else "计算真实道路",
                    variant = BusButtonVariant.CLAY,
                    enabled = gate.canNavigate && !calculating,
                    loading = calculating,
                    small = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 9.dp),
                    onClick = {
                        val from = vehicleGeo ?: return@BusButton
                        val stop = nextStop ?: return@BusButton
                        scope.launch {
                            calculating = true
                            val result = attempt { engine.calculateRoute(from, stop.geo) }
                                // 引擎自己抛出来的一律如实回"没算成"，绝不回退到旧折线。
                                // attempt 而非 runCatching：司机离开这一屏时取消要穿出，不能被收成"引擎异常"
                                // 再往已经离场的 StateFlow 上写一次假回执（§七）。
                                .getOrElse { RouteCheck.Rejected(reason = "导航引擎调用异常", detail = it.toString()) }
                            routeCheck = result
                            // 只有高德真回了这条路的折线，"剩余里程 → 任务点"这个配对才成立；
                            // 失败/异常时清空，宁可让到近那一行退到后端口径或沉默。
                            navigatingStopId = if (result is RouteCheck.Accepted) stop.stationId else null
                            calculating = false
                            onToast(
                                when (result) {
                                    is RouteCheck.Accepted -> "高德已返回真实道路"
                                    is RouteCheck.Rejected -> result.reason
                                },
                            )
                        }
                    },
                )

                routeCheck?.let { check ->
                    when (check) {
                        is RouteCheck.Rejected -> Text(
                            check.reason + if (check.detail.isEmpty()) "" else "（${check.detail}）",
                            color = t.goldDeep,
                            fontSize = (11.5 * scale).sp,
                            modifier = Modifier.padding(top = 7.dp),
                        )

                        is RouteCheck.Accepted -> Column(Modifier.padding(top = 9.dp)) {
                            val route = check.route
                            RouteMap(
                                polyline = route.gcj02Points,
                                stations = emptyList(),
                                me = vehicleGeo,
                                height = 170.dp,
                            )
                            BusDivider(Modifier.padding(vertical = 6.dp))
                            InfoRow("道路来源", route.source.label)
                            InfoRow(
                                "道路里程",
                                if (route.roadDistanceMeters > 0) formatSdkMeters(route.roadDistanceMeters)
                                else "暂无",
                            )
                            InfoRow(
                                "道路耗时",
                                if (route.roadEtaSeconds > 0) "${route.roadEtaSeconds / 60} 分钟" else "暂无",
                            )
                            InfoRow("折线点数", "${route.points.size} 点")

                            val running = session is NavigationSession.Running
                            BusButton(
                                text = if (running) "停止导航" else "开始导航",
                                variant = if (running) BusButtonVariant.DANGER else BusButtonVariant.PRIMARY,
                                // 开始要过闸门，停止永远能按：导航中定位丢一下就把"停"也锁住，是把闸门锁错了方向
                                enabled = running || gate.canNavigate,
                                small = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                onClick = {
                                    session = if (running) {
                                        engine.stopNavi()
                                        // 高德停了，这一帧的剩余里程就不再代表任何任务点：配对与去重一起清
                                        navigatingStopId = null
                                        announcer.reset()
                                        NavigationSession.Idle
                                    } else {
                                        engine.startGpsNavi(Prefs.voiceGuidance)
                                    }
                                },
                            )
                            (session as? NavigationSession.Refused)?.let {
                                Text(
                                    it.reason,
                                    color = t.goldDeep,
                                    fontSize = (11.5 * scale).sp,
                                    modifier = Modifier.padding(top = 5.dp),
                                )
                            }
                        }
                    }
                }

                // 导航实时数据
                sdkFrame?.let { frame ->
                    BusDivider(Modifier.padding(vertical = 6.dp))
                    Text(
                        "导航实时数据",
                        color = t.textSecondary,
                        fontSize = (12 * scale).sp,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                    InfoRow(
                        "剩余里程",
                        if (frame.hasRemainDistance) formatSdkMeters(frame.remainMeters) else "暂未知",
                    )
                    InfoRow(
                        "剩余时间",
                        if (frame.remainSeconds > 0) "${frame.remainSeconds / 60} 分钟" else "暂未知",
                    )
                    InfoRow("当前道路", frame.currentRoad.ifEmpty { "暂未知" })
                    InfoRow("下一段道路", frame.nextRoad.ifEmpty { "暂未知" })
                    InfoRow(
                        "步骤",
                        if (frame.hasStepProgress) "${frame.sdkStepIndex}" else "暂未知",
                    )
                    // 转向名只查核对过的 IconType 表；表外的码就说"暂不支持"
                    InfoRow(
                        "转向",
                        turnIconSpeechOf(frame.iconType) ?: "暂不支持此转向类型",
                    )
                    // 车道（赛道第 10 步）：帧只能来自高德的 showLaneInfo/hideLaneInfo，四种说法由 laneDisplayTextOf 一个出口给。
                    // 「高德没给」与「这条路没车道」不是一句；开关关掉时那一句走 guidanceSwitchOffText（与下面的播报行同一个出口）。
                    InfoRow(
                        "车道",
                        if (!Prefs.laneGuidance) guidanceSwitchOffText("车道级指引", "这一格不显示车道")
                        else laneDisplayTextOf(sdkLane),
                    )
                    // 偏航重算（赛道第 11 步）：这一格只能由高德的 onReCalculateRouteForYaw / onReCalculateRouteForTrafficJam 说。
                    // 页面不测距、不卡阈值 —— 本机没有任何一处能宣布"已偏离线路"，那句话要么由高德说、由后端 deviated 说，就不说。
                    // 高德那两条广播没有结局，随后的算路回执又不带原因，所以这里也不写"重算完成/已回到线路"（配对推断禁止）。
                    // 这一行没有对应的司机开关（Prefs 里只有上面那一条车道与下面的语音两条），所以它不接 guidanceSwitchOffText。
                    InfoRow("重算", engineRecalcTextOf(sdkRecalc))
                    // 播报文本是高德 onGetNavigationText 原句；本机没有 TTS，关着时也不冒充"高德没给"。
                    // 播报类型名进的是**值**那一侧：label 这一屏全是静态词，动态词拼进 label 会让 13 行的标签宽窄不齐。
                    InfoRow(
                        "播报",
                        when {
                            !Prefs.voiceGuidance -> guidanceSwitchOffText("语音播报", "语音播报已关闭")
                            else -> (sdkVoice?.text ?: "暂无播报") +
                                (naviVoiceTypeNameOf(sdkVoice?.typeCode)?.let { "（$it）" } ?: "")
                        },
                    )
                }

                // 到近播报回显（L-1）：这一行只说"最近说出去的那句"或"这一帧为什么不说话"，
                // 阈值、话术、去重都在纯层里，页面在这儿不判距离。
                BusDivider(Modifier.padding(vertical = 6.dp))
                InfoRow(
                    "到近播报",
                    if (!Prefs.approachVoice) guidanceSwitchOffText("到近作业播报", "不会读作业提醒")
                    else approachLast ?: approachStatus,
                )
            }

            // 外部地图交接（等价小程序 workbench 的 navigateToNextStop → wx.openLocation）：
            // 实时路况在高德/百度那边，本机不自己画转向；目的地同样只取调度任务点。
            BusDivider(Modifier.padding(vertical = 7.dp))
            Text(
                if (nextStop == null) "外部地图：没有任务点可以交给它（导航不自己决定去哪里）"
                else "外部地图：把「${nextStop.name.ifEmpty { "任务点#${nextStop.stationId}" }}」交给高德 / 百度",
                color = t.textTertiary,
                fontSize = (11.5 * scale).sp,
            )
            Row(Modifier.padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BusButton(
                    text = "交给高德",
                    variant = BusButtonVariant.OUTLINE,
                    small = true,
                    enabled = nextStop != null,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val stop = nextStop ?: return@BusButton
                        onToast(openAmap(context, stop.geo, stop.name.ifEmpty { "任务点#${stop.stationId}" }))
                    },
                )
                BusButton(
                    text = "交给百度",
                    variant = BusButtonVariant.OUTLINE,
                    small = true,
                    enabled = nextStop != null,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val stop = nextStop ?: return@BusButton
                        onToast(openBaidu(context, stop.geo, stop.name.ifEmpty { "任务点#${stop.stationId}" }))
                    },
                )
            }
        }
    }
}

/* ---------- 外部地图交接（从旧 DriverNavScreen 移入正式导航面，D-003） ---------- */

private fun openAmap(context: Context, to: Geo, name: String): String = view(
    context,
    "https://uri.amap.com/navigation?to=${to.lng},${to.lat},${Uri.encode(name.ifEmpty { "目的地" })}&mode=drive&coordinate=gaode&callnative=1",
    "未安装高德地图",
)

private fun openBaidu(context: Context, to: Geo, name: String): String = view(
    context,
    "baidumap://map/direction?destination=latlng:${to.lat},${to.lng}|name=${Uri.encode(name.ifEmpty { "目的地" })}&mode=driving&src=cargo.post.android",
    "未安装百度地图",
)

private fun view(context: Context, uri: String, missing: String): String = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    "已唤起外部地图"
} catch (e: ActivityNotFoundException) {
    missing
} catch (e: Exception) {
    "唤起失败：${e.message}"
}

/** 只服务本文件的里程文案；旧调试屏那份是 private，正式界面不再走那一屏，两边不必合并 */
private fun formatSdkMeters(meters: Int): String = when {
    meters <= 0 -> "0 米"
    meters < 1000 -> "$meters 米"
    else -> "%.1f 公里".format(meters / 1000.0)
}
