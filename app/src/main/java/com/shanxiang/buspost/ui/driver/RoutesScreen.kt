package com.shanxiang.buspost.ui.driver

import androidx.compose.foundation.background
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shanxiang.buspost.BuildConfig
import com.shanxiang.buspost.core.AppState
import com.shanxiang.buspost.core.ui.BusButton
import com.shanxiang.buspost.core.ui.BusButtonVariant
import com.shanxiang.buspost.core.ui.BusCard
import com.shanxiang.buspost.core.ui.BusIcons
import com.shanxiang.buspost.core.ui.BusPage
import com.shanxiang.buspost.core.ui.BusPullRefresh
import com.shanxiang.buspost.core.ui.BusRadius
import com.shanxiang.buspost.core.ui.BusTag
import com.shanxiang.buspost.core.ui.BusTone
import com.shanxiang.buspost.core.ui.ErrorState
import com.shanxiang.buspost.core.ui.InfoRow
import com.shanxiang.buspost.core.ui.LocalBusFontScale
import com.shanxiang.buspost.core.ui.LocalBusTokens
import com.shanxiang.buspost.core.ui.SectionTitle
import com.shanxiang.buspost.core.ui.Signpost
import com.shanxiang.buspost.core.ui.SourceTag
import com.shanxiang.buspost.core.ui.StationTimeline
import com.shanxiang.buspost.core.ui.StopState
import com.shanxiang.buspost.core.ui.TimelineStop
import com.shanxiang.buspost.data.model.BusVehicle
import com.shanxiang.buspost.data.model.TransportLeg
import com.shanxiang.buspost.data.repo.Repo
import com.shanxiang.buspost.nav.Routes
import com.shanxiang.buspost.ui.common.DemoNote
import com.shanxiang.buspost.ui.common.LocalNavController
import com.shanxiang.buspost.ui.common.ScreenViewModel

/**
 * 司机线路台：把"这一班要跑几段、每段该做什么"摊开，
 * 每段都能直接落到内置导航或状态推进。
 */
class RoutesViewModel : ScreenViewModel() {
    var legs by mutableStateOf<List<TransportLeg>>(emptyList())
        private set
    var current by mutableStateOf<TransportLeg?>(null)
        private set
    var vehicle by mutableStateOf<BusVehicle?>(null)
        private set
    var receipt by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set

    /** 下拉刷新指示器由调用方持有；load 结束一定复位 */
    var refreshing by mutableStateOf(false)
        private set

    fun reload() {
        refreshing = true
        load()
    }

    fun load() {
        work(reset = legs.isEmpty()) {
            try {
                val l = Repo.driverLegs()
                val c = Repo.currentLeg()
                val v = Repo.driverPosition()
                legs = l.items
                current = c.value?.legId?.let { id -> l.items.firstOrNull { it.legId == id } } ?: c.value
                vehicle = v.value
                mergeDemo(l.demo, c.demo, v.demo)
            } finally {
                refreshing = false
            }
        }
    }

    /**
     * 段动作闭环（审计 §26）：发一步 → **整表回读** `GET /driver/legs`。
     *
     * 旧实现在 ack 之后本机 `copy(status = nextStatus(...))`，而 `nextStatus` 造的是 2/3/4 这套
     * 不存在的推进（真实表 4 = 前往起点），后端拒了也照显示（§28）。现在客户端不再算下一状态。
     */
    fun action(leg: TransportLeg, action: LegDriverAction) {
        if (busy) return
        busy = true
        work(reset = false) {
            try {
                // 闸键走枚举名而不是 URL：ARRIVE_DEST 与 ENTER_DELIVERY 打同一个 URL 却是两次不同的意图
                //（后端按段的当前状态分两支），只按 URL 拼键会把第二次拦在本机，段永远进不了派送。
                val ack = Repo.legAction(action.api, leg.legId, actionKey = action.name)
                val feed = Repo.driverLegs()
                legs = feed.items
                val back = feed.items.firstOrNull { it.legId == leg.legId }
                mergeDemo(ack.demo, feed.demo)
                receipt = when {
                    // 被本地幂等闸拦下：一个字节都没发出去，所以不能说"已提交"也不能说"失败"
                    ack.notSent -> "同一个动作还在处理中，本次点击未发给后端，以下为后端当前状态"
                    ack.demo -> "${action.api} 已记录，系统同步中"
                    back == null -> "动作已提交，但回读列表里已经没有这一段，请下拉刷新核对"
                    back.status == leg.status ->
                        "动作已提交，后端回读仍是「${back.statusLabel}」，可能未被接受，请下拉刷新核对"
                    else -> "后端回读：${back.fromStation} → ${back.toStation} ${back.statusLabel}"
                }
            } finally {
                busy = false
            }
        }
    }
}

@Composable
fun RoutesScreen(vm: RoutesViewModel = viewModel()) {
    val nav = LocalNavController.current
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current

    LaunchedEffect(Unit) { if (vm.legs.isEmpty()) vm.load() }

    BusPullRefresh(refreshing = vm.refreshing, onRefresh = { vm.reload() }) {
        BusPage(spacedBy = 12.dp) {
            Signpost(
                title = "今日排班班次",
                subtitle = vm.current?.let { "当前第 ${it.seq} 段：${it.fromStation} → ${it.toStation}" } ?: "按段接单、装车、交接",
                badge = if (vm.legs.isEmpty()) "暂无班次" else "${vm.legs.size} 段",
            ) {
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    // /driver/position 的报文只有经纬度 + dataSource：状态、下一站都不在这份报文里，
                    // 旧写法无条件写"在途 ${nextStation}"（站名恒空），等于本机替后端宣布车在跑（§14/§72）
                    vm.vehicle?.let { BusTag(it.positionOnlyTag, tone = BusTone.CLAY) }
                    vm.vehicle?.let { if (it.etaMinutes != null) BusTag("ETA ${it.etaMinutes} 分钟", tone = BusTone.PRIMARY) }
                }
            }

            DemoNote(vm.demo, text = "当前为演示分段任务。")
            vm.error?.let { ErrorState(it, onRetry = { vm.load() }) }
            // 回执条统一走 DriverBits.ReceiptBar（本页此前内联了一份同形实现，样式已与工作台漂移）
            vm.receipt?.let { ReceiptBar(it) }

            vm.vehicle?.let { VehicleCard(it) }

            if (vm.legs.isNotEmpty()) {
                ProgressSummary(vm.legs, vm.current)
            }

            // 途经站点时间线：把今日班次摊成一条站点链（等价小程序「途经站点时间线」）
            if (vm.legs.isNotEmpty()) {
                SectionTitle("途经站点时间线")
                BusCard(modifier = Modifier.fillMaxWidth()) {
                    StationTimeline(
                        stops = vm.legs.map { leg ->
                            val done = leg.done
                            val isCur = leg.legId == vm.current?.legId
                            TimelineStop(
                                label = "${leg.fromStation} → ${leg.toStation}",
                                hint = buildString {
                                    append(if (isCur) "进行中" else if (done) "已完成" else "待执行")
                                    leg.legPlanText?.let { if (isNotEmpty()) append(" · "); append(it) }
                                },
                                state = when {
                                    done -> StopState.DONE
                                    isCur -> StopState.CURRENT
                                    else -> StopState.PENDING
                                },
                            )
                        },
                    )
                }
            }

            SectionTitle("分段任务", subtitle = "查看各段运输状态")
            if (vm.legs.isEmpty() && !vm.loading) {
                Text("今天没有被分配运输分段", color = t.textTertiary, fontSize = (13 * scale).sp)
            }
            vm.legs.forEach { leg ->
                LegRow(
                    leg = leg,
                    isCurrent = leg.legId == vm.current?.legId,
                    busy = vm.busy,
                    onAction = { vm.action(leg, it) },
                    // 需要凭证的段动作改跳交接页：那一面有选图、有"绝不发没有 photoUrl 的确认"那道闸
                    onNeedsPhoto = { nav.navigate(Routes.HANDOVER) },
                    // 旧导航页是调试入口（D-003）：路由只在 DEBUG 注册，这里同轮加闸
                    onNav = { if (BuildConfig.DEBUG) nav.navigate(Routes.DRIVER_NAV) },
                    // 段号靠进入意图传，不写进路由：带参数的司机域路由会绕过分域闸（§54，见 Routes.DRIVER_EXCEPTION）
                    onException = { AppState.openException(leg.legId); nav.navigate(Routes.DRIVER_EXCEPTION) },
                )
            }

            BusButton(
                text = "刷新分段",
                variant = BusButtonVariant.OUTLINE,
                onClick = { vm.load() },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun VehicleCard(v: BusVehicle) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    BusCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(BusIcons.bus, contentDescription = null, tint = t.primary, modifier = Modifier.size((20 * scale).dp))
                Spacer(Modifier.width(9.dp))
                Text(
                    v.plate.ifEmpty { "本车" },
                    color = t.ink,
                    fontSize = (15 * scale).sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                SourceTag(v.dataSource)
            }
            Spacer(Modifier.width(4.dp))
            InfoRow("下一站", v.nextStation.ifEmpty { "暂未知" })
            InfoRow("预计到达", if (v.etaMinutes != null) "${v.etaMinutes} 分钟" else "暂未知")
            InfoRow("当前车速", if (v.speedKmh != null) "${v.speedKmh} km/h" else "—")
            InfoRow("线路", v.lineName.ifEmpty { "—" })
            if (!v.isReal) {
                Text(
                    "位置为模拟推算：开启工作台的位置上报后才会是真实 GPS。",
                    color = t.textTertiary,
                    fontSize = (11.5 * scale).sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun ProgressSummary(legs: List<TransportLeg>, current: TransportLeg?) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val doneCount = legs.count { it.done }
    val total = legs.size
    val progress = if (total > 0) doneCount.toFloat() / total else 0f
    BusCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("班次进度", color = t.ink, fontSize = (14 * scale).sp, fontWeight = FontWeight.Bold)
                    Text(
                        "已完成 $doneCount / $total 段",
                        color = t.textSecondary,
                        fontSize = (12 * scale).sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(BusRadius.pill))
                        .background(t.primaryLight)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        "${(progress * 100).toInt()}%",
                        color = t.primary,
                        fontSize = (15 * scale).sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).padding(top = 10.dp),
                color = t.primary,
                trackColor = t.track,
            )
            current?.let {
                Text(
                    "当前：${it.fromStation} → ${it.toStation} · ${it.statusLabel}",
                    color = t.textTertiary,
                    fontSize = (11.5 * scale).sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun LegRow(
    leg: TransportLeg,
    isCurrent: Boolean,
    busy: Boolean,
    onAction: (LegDriverAction) -> Unit,
    onNeedsPhoto: () -> Unit,
    onNav: () -> Unit,
    onException: () -> Unit,
) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val flow = DriverLegStateMapper.of(leg)
    val done = flow.isCompleted
    BusCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size((26 * scale).dp)
                        .clip(RoundedCornerShape(BusRadius.pill))
                        .background(if (done) t.primaryLight else if (isCurrent) t.clayLight else t.track),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${leg.seq}",
                        color = if (done) t.primaryOnLight else if (isCurrent) t.clayDeep else t.textSecondary,
                        fontSize = (13 * scale).sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${leg.fromStation} → ${leg.toStation}",
                        color = t.ink,
                        fontSize = (14.5 * scale).sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOf(leg.plate, leg.driverName).filter { it.isNotEmpty() }.joinToString(" · "),
                        color = t.textSecondary,
                        fontSize = (12 * scale).sp,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        leg.statusLabel,
                        color = if (done) t.statusDone else if (isCurrent) t.clayDeep else t.textSecondary,
                        fontSize = (12 * scale).sp,
                        fontWeight = FontWeight.Bold,
                    )
                    if (!done) {
                        Text(
                            // 段契约（AppDriverLegRespVO / AppSendLegRespVO）根本没有 etaMinutes 键，真接口下走这条文案（审计 §71）
                            leg.etaMinutes?.let { "$it 分" } ?: "暂未知",
                            color = t.textTertiary,
                            fontSize = (11 * scale).sp,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
            }
            if (leg.transferStation.isNotEmpty()) {
                BusTag("换乘 ${leg.transferStation}", tone = BusTone.GOLD, modifier = Modifier.padding(top = 9.dp))
            }
            leg.legPlanText?.let {
                Text(
                    it,
                    color = t.textTertiary,
                    fontSize = (11.5 * scale).sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            val next = flow.next
            if (next == null) {
                flow.waitingReason?.let {
                    Text(
                        it,
                        color = t.textTertiary,
                        fontSize = (11.5 * scale).sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            Row(Modifier.padding(top = 11.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (next != null) {
                    BusButton(
                        text = next.label,
                        small = true,
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            // 分流只由 legClickOf 一处说（#53）：需要凭证照片的动作**不在这里发**，
                            // 因为后端对已完成的交接是幂等 return，先落一次没有 photoUrl 的提交就补不回来了。
                            when (legClickOf(next)) {
                                LegClick.GO_HANDOVER -> onNeedsPhoto()
                                LegClick.SEND -> next?.let(onAction)
                            }
                        },
                    )
                }
                // 旧导航页只在调试包可达（D-003）。这一页的段契约只有站名没有坐标，
                // 正式包不在这里跳导航 —— 就近导航请到工作台那张任务地图（目的地来自 /driver/route 的 stops）。
                if (BuildConfig.DEBUG) {
                    BusButton(
                        text = "调试导航",
                        small = true,
                        variant = BusButtonVariant.OUTLINE,
                        modifier = Modifier.weight(1f),
                        onClick = onNav,
                    )
                }
            }
            // §54：异常只能由经手的司机报，且已终态/已异常的段不再给入口（判据在 DriverLegStateMapper）
            if (flow.canReportException) {
                BusButton(
                    text = "上报异常",
                    small = true,
                    variant = BusButtonVariant.GHOST,
                    enabled = !busy,
                    fullWidth = false,
                    modifier = Modifier.padding(top = 8.dp),
                    onClick = onException,
                )
            }
        }
    }
}
