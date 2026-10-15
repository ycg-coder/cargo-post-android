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
import com.shanxiang.buspost.core.ui.BusDialogAction
import com.shanxiang.buspost.core.ui.NetworkImage
import com.shanxiang.buspost.core.net.attempt
import com.shanxiang.buspost.core.ui.BusButton
import com.shanxiang.buspost.core.ui.BusButtonVariant
import com.shanxiang.buspost.core.ui.BusCard
import com.shanxiang.buspost.core.ui.BusDivider
import com.shanxiang.buspost.core.ui.BusIcons
import com.shanxiang.buspost.core.ui.BusPage
import com.shanxiang.buspost.core.ui.BusPullRefresh
import com.shanxiang.buspost.core.ui.BusRadius
import com.shanxiang.buspost.core.ui.BusSegmented
import com.shanxiang.buspost.core.ui.BusTag
import com.shanxiang.buspost.core.ui.BusTextField
import com.shanxiang.buspost.core.ui.BusTone
import com.shanxiang.buspost.core.ui.EmptyState
import com.shanxiang.buspost.core.ui.ErrorState
import com.shanxiang.buspost.core.ui.SkeletonList
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
import com.shanxiang.buspost.ui.common.LocalNavController
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

    var refreshing by mutableStateOf(false)
        private set

    /**
     * 待办 = 还没进终态的那些。#58：以前写的是 `!status.contains("已交接")`，
     * 超时(5)/取消(6)/异常(7) 匹配不上就被留在待办里，司机再点一次 → 后端 `HANDOVER_STATUS_ILLEGAL`。
     * 现在按后端原码判（`HandoverTask.terminal`），文案只负责显示。
     */
    val pending: List<HandoverTask> get() = items.filter { !it.terminal }

    fun reload() {
        refreshing = true
        load()
    }

    fun load() {
        work(reset = items.isEmpty()) {
            try {
                val feed = Repo.driverHandovers()
                items = feed.items
                demo = feed.demo
                val c = Repo.currentLeg()
                currentLeg = c.value
                mergeDemo(feed.demo, c.demo)
            } finally {
                refreshing = false
            }
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
                    ack.notSent -> "同一个段动作还在处理中，以下为当前状态"
                    ack.demo -> "操作已记录，系统同步中"
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
     * 交接确认闭环（需求 §7）：选照片（可再手写签名）→ `/infra/file/upload` 拿**真实 URL** →
     * 提交 handover/confirm → 重新回读 `/driver/handovers`，回执只写后端回读到的事实。
     *
     * 三条硬约束：
     * · 附了照片但拿不到 URL 时**不提交** —— `HandoverServiceImpl.confirmHandover:196-197` 对已完成的交接直接
     *   return（幂等），先落"交接完成"就再也补不进 photoUrl，凭证会永久丢失；
     * · 签名同一条纪律（判据在 [HandoverSignature.shouldBlockSubmit]）：签了却没传上去也不提交，
     *   绝不退化成"当成没签名继续提交"——那等于替后端把这一格记成"这人确实没签"；
     * · 本机不再自己把条目改写成"已交接"（审计 §26 修改方案 ②），状态一律以回读为准。
     */
    fun confirm(context: Context, task: HandoverTask, uri: Uri?, remark: String, signaturePng: ByteArray? = null) {
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
                val signed = signaturePng != null
                val signUploaded = if (signed) {
                    attempt { Repo.uploadPhoto(signaturePng!!, "handover-${task.id}-sign.png").url }
                        .getOrDefault("")
                } else {
                    ""
                }
                if (HandoverSignature.shouldBlockSubmit(signed, signUploaded)) {
                    receipt = "签名上传失败，交接未提交——签名补不进已完成的交接，请重签一次再确认"
                    return@work
                }
                val ack = Repo.confirmHandover(task.id, uploaded, remark, signUploaded)
                val feed = Repo.driverHandovers()
                items = feed.items
                mergeDemo(ack.demo, feed.demo)
                val onServer = feed.items.firstOrNull { it.id == task.id }
                val photoStored = onServer?.photoUrl?.isNotEmpty() == true
                val signStored = onServer?.signUrl?.isNotEmpty() == true
                receipt = when {
                    ack.notSent -> "本次确认未发送（同一个交接单还在处理中），以下为当前状态"
                    ack.demo -> "交接已记录，系统同步中"
                    feed.error != null -> "已提交，但未能回读确认，请下拉刷新核对"
                    uploaded.isNotEmpty() && !photoStored -> "已提交，但回读里没有凭证照片，请稍后刷新核对或补拍"
                    signUploaded.isNotEmpty() && !signStored -> "已提交，但回读里没有签名图，请稍后刷新核对或重签一次"
                    onServer == null -> "已提交，该交接单已不在司机列表中，请下拉刷新核对"
                    else -> "已提交，当前状态「${onServer.status}」。" +
                        HandoverSignature.receiptFor(photoStored, signStored)
                }
            } finally {
                busy = false
            }
        }
    }

}

@Composable
fun HandoverScreen(vm: HandoverViewModel = viewModel()) {
    val context = LocalContext.current
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val nav = LocalNavController.current
    var tab by remember { mutableStateOf(0) }
    var target by remember { mutableStateOf<HandoverTask?>(null) }
    var picked by remember { mutableStateOf<Uri?>(null) }
    var remark by remember { mutableStateOf("") }
    /** 已导出的签名 PNG 字节；null = 这一单还没签。只在本机活着，提交失败不假装"已签过" */
    var signPng by remember { mutableStateOf<ByteArray?>(null) }
    var showPad by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> picked = uri }

    LaunchedEffect(Unit) { if (vm.items.isEmpty()) vm.load() }

    val list = if (tab == 0) vm.pending else vm.items

    BusPullRefresh(refreshing = vm.refreshing, onRefresh = { vm.reload() }) {
    BusPage(spacedBy = 12.dp) {
        Signpost(
            title = "货物交接",
            subtitle = "换乘站把货交给下一班车，拍照留证后可追溯责任段",
            badge = if (vm.pending.isEmpty()) "无待办" else "${vm.pending.size} 笔待办",
            onBack = { nav.popBackStack() },
        )
        DemoNote(vm.demo, text = "当前为演示交接任务。")
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
        // #56：取单失败此前既念红条又念一句"暂无交接单"（空态只判了 `!loading`），
        // 两件事被说成一件；而首屏连骨架都没有，白屏看着像"没有单"。
        if (vm.loading && list.isEmpty()) {
            SkeletonList(3)
        } else if (list.isEmpty() && vm.error == null) {
            EmptyState(title = "暂无交接单", subtitle = "到达换乘站后由系统派发", actionText = "刷新", onAction = { vm.load() })
        }
        list.forEach { task -> HandoverCard(task = task, busy = vm.busy, onConfirm = { target = task; picked = null; remark = ""; signPng = null }) }
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
                    // 签收人手写签名（#20 阶段 B）。不签也能提交 —— 后端那一格"没带就不写"，
                    // 但一旦签了却没传上去，提交会被拦下来（[HandoverSignature.shouldBlockSubmit]），
                    // 因为已完成的交接补不进签名。
                    BusButton(
                        text = when (signPng) {
                            null -> "手写签收签名"
                            else -> "已签（${signPng!!.size / 1024 + 1} KB），点击重签"
                        },
                        variant = BusButtonVariant.OUTLINE,
                        small = true,
                        onClick = { showPad = true },
                    )
                    if (signPng == null) {
                        Text(
                            "本单未附手写签名；提交后这一格在后端仍是空，不会被念成「已核对过签名」。",
                            color = t.textTertiary,
                            fontSize = (11 * scale).sp,
                        )
                    }
                    BusTextField(value = remark, onValueChange = { remark = it }, label = "备注", placeholder = "件数/包装情况", minLines = 2)
                }
            },
            confirmButton = {
                BusDialogAction(
                    "提交交接",
                    onClick = {
                        vm.confirm(context, task, picked, remark, signPng)
                        target = null
                    }
                )
            },
            dismissButton = { BusDialogAction("取消", onClick = { target = null }) },
        )
    }

    if (showPad) {
        SignaturePadDialog(
            onCancel = { showPad = false },
            onSigned = { png ->
                signPng = png
                showPad = false
            },
        )
    }
    }
}

@Composable
private fun HandoverCard(task: HandoverTask, busy: Boolean, onConfirm: () -> Unit) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    // 终态一律按后端原码判（#58）；超时/取消/异常不是"完成"，但同样不能再提交
    val done = task.handoverState?.isDone == true
    val closed = task.terminal
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
                // 状态标签跟着后端原码走：文案没给时只能说"未给/暂不可用"，
                // 旧写法在这里兜一句「待接货」，等于替后端认了一个它没说过的状态。
                BusTag(
                    task.status.ifEmpty { task.handoverState?.label ?: "状态未给" },
                    tone = task.handoverState?.tone ?: BusTone.CLAY,
                )
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
                if (!closed) {
                    BusButton(
                        text = task.actionLabel.ifEmpty { "确认交接" },
                        small = true,
                        enabled = !busy,
                        onClick = onConfirm,
                    )
                } else if (!done) {
                    // 超时/取消/异常：不能再提交，但也不能念成"已交接"（#58）
                    Text(
                        "已${task.handoverState?.label ?: "终态"}，不能再提交交接",
                        color = t.textTertiary,
                        fontSize = (11.5 * scale).sp,
                    )
                }
            }
            // 凭证照片只画后端回读出来的 photoUrl（需求 §7 的"重新查询仍然能看到照片"），
            // 本机选过但没入库的照片不在这里冒充凭证。
            if (task.photoUrl.isNotEmpty()) {
                NetworkImage(
                    url = task.photoUrl,
                    contentDescription = "交接凭证照片",
                    width = 400.dp,
                    height = 120.dp,
                    modifier = Modifier
                        .padding(top = 9.dp)
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(BusRadius.m)),
                    contentScale = ContentScale.Crop,
                )
            }
            // 签收签名同一条口径：只画后端回读出来的 signUrl（V034 的 sign_url 列），
            // 本机签过但没入库的那张不在这里冒充凭证 —— 与照片各画各的，两格不合并。
            if (task.signUrl.isNotEmpty()) {
                NetworkImage(
                    url = task.signUrl,
                    contentDescription = "签收手写签名图",
                    width = 200.dp,
                    height = 94.dp,
                    modifier = Modifier
                        .padding(top = 9.dp)
                        .clip(RoundedCornerShape(BusRadius.m)),
                    contentScale = ContentScale.Fit,
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
