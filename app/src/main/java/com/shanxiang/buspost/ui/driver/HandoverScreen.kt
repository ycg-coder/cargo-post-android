package com.shanxiang.buspost.ui.driver

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.shanxiang.buspost.core.net.attempt
import com.shanxiang.buspost.core.ui.BusButton
import com.shanxiang.buspost.core.ui.BusButtonVariant
import com.shanxiang.buspost.core.ui.BusCard
import com.shanxiang.buspost.core.ui.BusDivider
import com.shanxiang.buspost.core.ui.BusIcons
import com.shanxiang.buspost.core.ui.BusPage
import com.shanxiang.buspost.core.ui.BusRadius
import com.shanxiang.buspost.core.ui.BusSegmented
import com.shanxiang.buspost.core.ui.BusTag
import com.shanxiang.buspost.core.ui.BusTextField
import com.shanxiang.buspost.core.ui.BusTone
import com.shanxiang.buspost.core.ui.EmptyState
import com.shanxiang.buspost.core.ui.ErrorState
import com.shanxiang.buspost.core.ui.LocalBusFontScale
import com.shanxiang.buspost.core.ui.LocalBusTokens
import com.shanxiang.buspost.core.ui.SectionTitle
import com.shanxiang.buspost.core.ui.Signpost
import com.shanxiang.buspost.core.ui.SourceTag
import com.shanxiang.buspost.core.ui.StationTimeline
import com.shanxiang.buspost.core.ui.StopState
import com.shanxiang.buspost.core.ui.TimelineStop
import com.shanxiang.buspost.data.model.HandoverTask
import com.shanxiang.buspost.data.model.TransportLeg
import com.shanxiang.buspost.data.repo.Repo
import com.shanxiang.buspost.ui.common.DemoNote
import com.shanxiang.buspost.ui.common.ScreenViewModel
import com.shanxiang.buspost.ui.user.formatPrice

/**
 * 货物交接：换乘站上把货交给下一班车，需要拍照留证的后端会置 needPhoto。
 * 照片走 /infra/file/upload，离线时 url 为空但流程仍可走完（回执说明）。
 */
class HandoverViewModel : ScreenViewModel() {
    var items by mutableStateOf<List<HandoverTask>>(emptyList())
        private set
    var busy by mutableStateOf(false)
        private set
    var receipt by mutableStateOf<String?>(null)
        private set

    /** 当前运输段：交接页顶部的「当前运输段卡」数据源 */
    var currentLeg by mutableStateOf<TransportLeg?>(null)
        private set
    var legBusy by mutableStateOf(false)
        private set

    val pending: List<HandoverTask> get() = items.filter { !isDone(it) }

    fun load() {
        work(reset = items.isEmpty()) {
            val feed = Repo.driverHandovers()
            items = feed.items
            demo = feed.demo
            val c = Repo.currentLeg()
            currentLeg = c.value
            mergeDemo(feed.demo, c.demo)
        }
    }

    /**
     * 当前运输段动作：一步一问，**推进后一律回读** `/driver/current-leg`。
     *
     * 旧实现在 ack 之后本机 `copy(status = legStep+1)` 自封下一步（审计 §27/§28）：
     * 状态空间被换成"按钮序号"，库里还在 9（交接中）就能显示成"已完成"。
     * 现在回执只写后端回读到的状态；回读失败就明说失败，不拿本机值顶替。
     */
    fun advanceLeg(action: String) {
        val leg = currentLeg ?: return
        if (legBusy) return
        legBusy = true
        work(reset = false) {
            try {
                val ack = Repo.legAction(action, leg.legId)
                val c = Repo.currentLeg()
                currentLeg = c.value
                mergeDemo(ack.demo, c.demo)
                val back = c.value
                receipt = when {
                    ack.notSent -> "同一个段动作还在处理中，本次点击未发给后端，以下为后端当前状态"
                    ack.demo -> "演示模式：段动作未提交后端，仅本机显示"
                    back == null -> "动作已提交，但没能回读段状态，请下拉刷新核对"
                    back.status == leg.status ->
                        "动作已提交，后端回读仍是「${back.statusLabel}」，可能未被接受，请下拉刷新核对"
                    else -> "后端回读：${back.statusLabel}"
                }
            } finally {
                legBusy = false
            }
        }
    }

    /**
     * 交接确认闭环（需求 §7）：选照片 → `/infra/file/upload` 拿**真实 URL** → 提交 handover/confirm
     * → 重新回读 `/driver/handovers`，回执只写后端回读到的事实。
     *
     * 两条硬约束：
     * · 附了照片但拿不到 URL 时**不提交** —— `HandoverServiceImpl.confirmHandover:196-197` 对已完成的交接直接
     *   return（幂等），先落"交接完成"就再也补不进 photoUrl，凭证会永久丢失；
     * · 本机不再自己把条目改写成"已交接"（审计 §26 修改方案 ②），状态一律以回读为准。
     */
    fun confirm(context: Context, task: HandoverTask, uri: Uri?, remark: String) {
        if (busy) return
        busy = true
        work(reset = false) {
            try {
                val uploaded = if (uri != null) {
                    attempt {
                        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: ByteArray(0)
                        Repo.uploadPhoto(bytes, "handover-${task.id}.jpg").url
                    }.getOrDefault("")
                } else {
                    ""
                }
                if (uri != null && uploaded.isEmpty()) {
                    receipt = "凭证照片上传失败，交接未提交——请重新选一次照片再确认"
                    return@work
                }
                val ack = Repo.confirmHandover(task.id, uploaded, remark)
                val feed = Repo.driverHandovers()
                items = feed.items
                mergeDemo(ack.demo, feed.demo)
                val onServer = feed.items.firstOrNull { it.id == task.id }
                val photoStored = onServer?.photoUrl?.isNotEmpty() == true
                receipt = when {
                    ack.notSent -> "本次确认未发给后端（同一个交接单还在处理中），以下为后端当前状态"
                    ack.demo -> "演示模式：交接未提交后端，仅本机显示"
                    feed.error != null -> "已提交后端，但未能回读确认，请下拉刷新核对"
                    uploaded.isNotEmpty() && !photoStored -> "已提交，但回读里没有凭证照片，请稍后刷新核对或补拍"
                    uploaded.isNotEmpty() && photoStored -> "交接已提交后端，凭证照片已入库"
                    onServer == null -> "已提交后端，该交接单已不在司机列表中，请下拉刷新核对"
                    else -> "已提交后端，当前状态「${onServer.status}」（未附凭证照片）"
                }
            } finally {
                busy = false
            }
        }
    }

    private fun isDone(task: HandoverTask) = task.status.contains("已交接") || task.status.contains("完成")
}

@Composable
fun HandoverScreen(vm: HandoverViewModel = viewModel()) {
    val context = LocalContext.current
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    var tab by remember { mutableStateOf(0) }
    var target by remember { mutableStateOf<HandoverTask?>(null) }
    var picked by remember { mutableStateOf<Uri?>(null) }
    var remark by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> picked = uri }

    LaunchedEffect(Unit) { if (vm.items.isEmpty()) vm.load() }

    val list = if (tab == 0) vm.pending else vm.items

    BusPage(spacedBy = 12.dp) {
        Signpost(
            title = "货物交接",
            subtitle = "换乘站把货交给下一班车，拍照留证后可追溯责任段",
            badge = if (vm.pending.isEmpty()) "无待办" else "${vm.pending.size} 笔待办",
        )
        DemoNote(vm.demo, text = "后端未返回交接单，当前为演示交接任务。")
        vm.error?.let { ErrorState(it, onRetry = { vm.load() }) }
        vm.currentLeg?.let { leg ->
            CurrentLegCard(
                leg,
                busy = vm.legBusy,
                onAdvance = { action -> vm.advanceLeg(action) },
                onOpenHandoverList = { tab = 1 },
            )
        }
        // 回执条统一走 DriverBits.ReceiptBar（本页此前内联了一份同形实现，样式已与工作台漂移）
        vm.receipt?.let { ReceiptBar(it) }

        BusSegmented(
            options = listOf("待交接", "全部"),
            selected = tab,
            onSelect = { tab = it },
            modifier = Modifier.fillMaxWidth(),
        )

        SectionTitle("交接单", subtitle = "${list.size} 笔")
        if (list.isEmpty() && !vm.loading) {
            EmptyState(title = "暂无交接单", subtitle = "到达换乘站后由系统派发", actionText = "刷新", onAction = { vm.load() })
        }
        list.forEach { task -> HandoverCard(task = task, busy = vm.busy, onConfirm = { target = task; picked = null; remark = "" }) }
    }

    if (target != null) {
        val task = target!!
        AlertDialog(
            onDismissRequest = { target = null },
            title = { Text("确认交接") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(
                        "${task.goodsName} · ${formatPrice(task.weightKg)} kg",
                        color = t.ink,
                        fontSize = (14 * scale).sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${task.fromStation} → ${task.toStation}（${task.toPlate}）",
                        color = t.textSecondary,
                        fontSize = (12.5 * scale).sp,
                    )
                    if (task.needPhoto == true) {
                        BusTag("此单要求凭证照片", tone = BusTone.GOLD)
                    }
                    BusButton(
                        text = if (picked == null) "选择凭证照片" else "已选 1 张，点击重选",
                        variant = BusButtonVariant.OUTLINE,
                        small = true,
                        onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    )
                    BusTextField(value = remark, onValueChange = { remark = it }, label = "备注", placeholder = "件数/包装情况", minLines = 2)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.confirm(context, task, picked, remark)
                    target = null
                }) { Text("提交交接") }
            },
            dismissButton = { TextButton(onClick = { target = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun HandoverCard(task: HandoverTask, busy: Boolean, onConfirm: () -> Unit) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val done = task.status.contains("已交接") || task.status.contains("完成")
    BusCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    BusIcons.ship,
                    contentDescription = null,
                    tint = if (done) t.statusDone else t.clay,
                    modifier = Modifier.size((20 * scale).dp),
                )
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        task.goodsName.ifEmpty { "货物" },
                        color = t.ink,
                        fontSize = (14.5 * scale).sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${task.fromStation} → ${task.toStation}",
                        color = t.textSecondary,
                        fontSize = (12.5 * scale).sp,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                BusTag(task.status.ifEmpty { "待接货" }, tone = if (done) BusTone.OK else BusTone.CLAY)
            }
            BusDivider(Modifier.padding(vertical = 9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("我的车", color = t.textTertiary, fontSize = (11 * scale).sp)
                    Text(task.fromPlate.ifEmpty { "—"}, color = t.ink, fontSize = (13 * scale).sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("接车", color = t.textTertiary, fontSize = (11 * scale).sp)
                    Text(task.toPlate.ifEmpty { "—"}, color = t.ink, fontSize = (13 * scale).sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("重量", color = t.textTertiary, fontSize = (11 * scale).sp)
                    Text("${formatPrice(task.weightKg)} kg", color = t.price, fontSize = (13 * scale).sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
                }
            }
            Row(Modifier.padding(top = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                SourceTag(task.orderNo.ifEmpty { null }, modifier = Modifier.weight(1f))
                if (!done) {
                    BusButton(
                        text = task.actionLabel.ifEmpty { "确认交接" },
                        small = true,
                        enabled = !busy,
                        onClick = onConfirm,
                    )
                }
            }
            // 凭证照片只画后端回读出来的 photoUrl（需求 §7 的"重新查询仍然能看到照片"），
            // 本机选过但没入库的照片不在这里冒充凭证。
            if (task.photoUrl.isNotEmpty()) {
                AsyncImage(
                    model = task.photoUrl,
                    contentDescription = "交接凭证照片",
                    modifier = Modifier
                        .padding(top = 9.dp)
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(BusRadius.m)),
                    contentScale = ContentScale.Crop,
                )
            }
            if (task.time.isNotEmpty()) {
                Text(
                    "派单时间 ${task.time}",
                    color = t.textTertiary,
                    fontSize = (11 * scale).sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

/** 当前运输段卡：段状态、动作时间线和"只能点下一步"的入口，全部出自 [DriverLegStateMapper] */
@Composable
private fun CurrentLegCard(
    leg: TransportLeg,
    busy: Boolean,
    onAdvance: (String) -> Unit,
    onOpenHandoverList: () -> Unit,
) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val flow = DriverLegStateMapper.of(leg)
    BusCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(BusIcons.route, contentDescription = null, tint = t.primary, modifier = Modifier.size((18 * scale).dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("当前运输段", color = t.textSecondary, fontSize = (12 * scale).sp)
                    Text(
                        "${leg.fromStation} → ${leg.toStation}",
                        color = t.ink,
                        fontSize = (15 * scale).sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BusTag(leg.statusLabel, tone = leg.statusTone)
            }
            if (leg.transferStation.isNotEmpty()) {
                BusTag("换乘 ${leg.transferStation}", tone = BusTone.GOLD, modifier = Modifier.padding(top = 9.dp))
            }
            StationTimeline(
                stops = flow.steps.mapIndexed { i, step ->
                    TimelineStop(
                        label = step.label,
                        state = when {
                            i < flow.doneSteps -> StopState.DONE
                            !flow.isCompleted && i == flow.doneSteps -> StopState.CURRENT
                            else -> StopState.PENDING
                        },
                    )
                },
                modifier = Modifier.padding(top = 10.dp),
            )
            // 只给"下一步"：整条时间线是进度显示，不是可点的动作列表（点了后端会拒）
            flow.next?.let { next ->
                if (next.needsPhoto) {
                    // 交接确认必须带凭证照片（需求 §7）：段卡上没有拍照入口，引导到交接列表那一条去确认，
                    // 绝不在这发一次没有 photoUrl 的 handover-confirm
                    BusButton(
                        text = "去交接列表拍照确认",
                        variant = BusButtonVariant.OUTLINE,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        onClick = onOpenHandoverList,
                    )
                } else {
                    BusButton(
                        text = next.label,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        onClick = { onAdvance(next.api) },
                    )
                }
            }
            flow.waitingReason?.let {
                Text(
                    it,
                    color = t.textTertiary,
                    fontSize = (11.5 * scale).sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
