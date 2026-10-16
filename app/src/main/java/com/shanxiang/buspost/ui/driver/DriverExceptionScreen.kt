package com.shanxiang.buspost.ui.driver

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.shanxiang.buspost.core.AppState
import com.shanxiang.buspost.core.net.attempt
import com.shanxiang.buspost.core.ui.BusButton
import com.shanxiang.buspost.core.ui.BusButtonVariant
import com.shanxiang.buspost.core.ui.BusCard
import com.shanxiang.buspost.core.ui.BusIcons
import com.shanxiang.buspost.core.ui.BusPage
import com.shanxiang.buspost.core.ui.BusPullRefresh
import com.shanxiang.buspost.core.ui.BusRadius
import com.shanxiang.buspost.core.ui.BusTag
import com.shanxiang.buspost.core.ui.BusTextField
import com.shanxiang.buspost.core.ui.BusTone
import com.shanxiang.buspost.core.ui.ErrorState
import com.shanxiang.buspost.core.ui.FilterChipRow
import com.shanxiang.buspost.core.ui.LocalBusFontScale
import com.shanxiang.buspost.core.ui.LocalBusTokens
import com.shanxiang.buspost.core.ui.SectionTitle
import com.shanxiang.buspost.data.model.LegStatus
import com.shanxiang.buspost.data.model.TransportLeg
import com.shanxiang.buspost.data.repo.Repo
import com.shanxiang.buspost.ui.common.BackTopBar
import com.shanxiang.buspost.ui.common.ScreenViewModel

/**
 * 司机异常上报（指令 §54）。
 *
 * 三条约束都来自**实测契约**，不是本机偏好：
 * · 后端只收自由文本 —— `AppDriverLegActionReqVO` 四字段里没有异常类型，`case "exception"` 只往下传
 *   `remark`，所以七个分类经 [buildExceptionRemark] 拼进 remark 文本，**不发 `type` 键**；
 * · 状态归后端 —— `reportLegException` 把段与订单一起置异常并告警后台，本机不提前改写 `legs`，
 *   提交后一律整表回读 `/driver/legs`（审计 §26 同一条纪律）；
 * · 出口只有一个 [Repo.legAction] —— 它自带 V-1 幂等闸（§52），连点只发一次，被拦下时回执必须说"未发给后端"。
 */
class DriverExceptionViewModel : ScreenViewModel() {
    var legs by mutableStateOf<List<TransportLeg>>(emptyList())
        private set

    /** 上报哪一段：进入意图（[AppState.openException]）> 手动选择 > 后端当前段 > 第一个可报段 */
    var selectedLegId by mutableStateOf<String?>(null)
        private set
    var reasonIndex by mutableStateOf(-1)
        private set
    var detail by mutableStateOf("")
        private set
    var busy by mutableStateOf(false)
        private set
    var receipt by mutableStateOf<String?>(null)
        private set
    var refreshing by mutableStateOf(false)
        private set

    /** 分类表 [DriverExceptionReason] 是 internal（§54 的话术不外泄给别的模块），所以这个 getter 也得 internal */
    internal val reason: DriverExceptionReason? get() = DriverExceptionReason.values().getOrNull(reasonIndex)
    val selectedLeg: TransportLeg? get() = legs.firstOrNull { it.legId == selectedLegId }
    val detailBudget: Int get() = exceptionDetailBudget(reason)

    fun reload() {
        refreshing = true
        load()
    }

    fun load() {
        work(reset = legs.isEmpty()) {
            try {
                val feed = Repo.driverLegs()
                val intent = AppState.consumeExceptionLegId()
                val current = Repo.currentLeg()
                legs = feed.items
                val currentId = current.value?.legId
                val keep = selectedLegId?.takeIf { id -> feed.items.any { it.legId == id } }
                val wanted = when {
                    intent != null && feed.items.any { it.legId == intent } -> intent
                    keep != null -> keep
                    currentId != null && feed.items.any { it.legId == currentId } -> currentId
                    // 兜底先挑一个还能报异常的段；全都终态时退回第一段，让"不可再报"那句说明露出来，
                    // 而不是留个空选择让司机以为是页面坏了
                    else -> feed.items.firstOrNull { DriverLegStateMapper.of(it).canReportException }?.legId
                        ?: feed.items.firstOrNull()?.legId
                }
                selectedLegId = wanted
                if (intent != null && wanted != intent) {
                    receipt = "进入时指定的那一段已不在当前列表（可能已被调度重排），请在下面重新选择"
                }
                mergeDemo(feed.demo, current.demo)
            } finally {
                refreshing = false
            }
        }
    }

    fun selectLeg(id: String) {
        selectedLegId = id
    }

    /** 换分类时把说明压回新预算：截断发生在输入时，不在提交时静默发生 */
    fun selectReason(index: Int) {
        reasonIndex = index
        val budget = exceptionDetailBudget(DriverExceptionReason.values().getOrNull(index))
        if (detail.length > budget) detail = detail.take(budget)
    }

    fun onDetailChange(value: String) {
        detail = value.take(detailBudget)
    }

    /**
     * 提交。**返回 null 的三种情况一个字节都不出网**，且回执逐条说清原因：
     * 没选段 / 没选分类 / 「其他」没写具体情况。空 remark 会被后端兜成"司机上报异常"，
     * 那等于让后端替司机编原因 —— 宁可不发。
     */
    /**
     * `context` 可空只为一件事：JVM 侧用例（没有 Robolectric）要能跑通「不附照片」这条主路径。
     * 附照片这条要求 photo 与 context 都在，缺任何一个都按「没带照片」走 —— 不默默丢。
     */
    fun submit(context: Context? = null, photo: Uri? = null) {
        val leg = selectedLeg
        if (leg == null) {
            receipt = "没有可选中的运输段，本次未发给后端"
            return
        }
        if (!DriverLegStateMapper.of(leg).canReportException) {
            receipt = "这一段现在是「${leg.statusLabel}」，后端不再接受异常上报，本次未发给后端"
            return
        }
        val remark = buildExceptionRemark(reason, detail)
        if (remark == null) {
            receipt = if (reason == null) "先选一项异常情况，本次未发给后端"
            else "选「其他」必须写清具体情况，本次未发给后端"
            return
        }
        if (busy) return
        busy = true
        work(reset = false) {
            try {
                // 现场照片：#60 之前这条链路没人接（请求里有 photoUrl 字段，后端只读 remark）。
                // 与交接确认**不同**的一条判断：照片传不上去**不拦异常上报** —— 异常必须先到调度，
                // 少一张照片是取证不全，不发这一条是整段货没人管。但回执必须说清没带上。
                val uploaded = if (photo != null && context != null) {
                    attempt {
                        val bytes = context.contentResolver.openInputStream(photo)?.use { it.readBytes() }
                            ?: ByteArray(0)
                        Repo.uploadPhoto(bytes, "exception-${leg.legId}.jpg").url
                    }.getOrDefault("")
                } else {
                    ""
                }
                val photoDropped = photo != null && uploaded.isEmpty()
                val ack = Repo.legAction(LegDriverAction.EXCEPTION.api, leg.legId, exceptionActionBody(remark, uploaded))
                val feed = Repo.driverLegs()
                legs = feed.items
                mergeDemo(ack.demo, feed.demo)
                val back = feed.items.firstOrNull { it.legId == leg.legId }
                receipt = when {
                    // 被本地幂等闸拦下：一个字节都没发出去，不能说"已提交"
                    ack.notSent -> "同一个异常上报还在处理中，以下为当前状态"
                    ack.demo -> "操作已记录，系统同步中"
                    back == null -> "异常已提交，但回读列表里已经没有这一段，请下拉刷新核对"
                    LegStatus.of(back.status) != LegStatus.EXCEPTION ->
                        "异常已提交，但后端回读仍是「${back.statusLabel}」，可能未被接受，请下拉刷新核对"
                    photoDropped -> "异常已提交，但现场照片没传上去（后端那一格仍是空）——可重报一次或让调度补录"
                    else -> "后端回读：${legReceiptText(back)}，重排由调度处理" +
                        if (uploaded.isNotEmpty()) "；现场照片已随上报提交" else ""
                }
            } finally {
                busy = false
            }
        }
    }
}

@Composable
fun DriverExceptionScreen(vm: DriverExceptionViewModel = viewModel()) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val context = LocalContext.current
    /** 选中的现场照片（只在本机活着）：传不上去不拦异常上报，但回执要说清没带上 */
    var picked by remember { mutableStateOf<Uri?>(null) }
    /**
     * ⚠ 取消必须**留着**已经选好的那一张（真机抓到的：点"已选 1 张，点击重选"再按返回，回过来的 `it` 是 null，
     *   直接赋回去就把司机刚拍的照片静默清掉 —— 页面回到"没附照片"那一形，而司机以为自己还有照片，
     *   提交出去就是又一条"凭证没带上"，与 #60 同一格。所以是 `it ?: picked`，不是 `it`。
     */
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { picked = it ?: picked }

    LaunchedEffect(Unit) { if (vm.legs.isEmpty()) vm.load() }

    BusPullRefresh(refreshing = vm.refreshing, onRefresh = { vm.reload() }) {
        BusPage(spacedBy = 12.dp) {
            BackTopBar(title = "上报运输异常", subtitle = "报给调度：段与订单转异常，由调度重排")

            val leg = vm.selectedLeg
            // 回执条统一走 DriverBits.ReceiptBar（本页此前内联了一份同形实现，样式已与工作台漂移）
            vm.receipt?.let { ReceiptBar(it) }
            vm.error?.let { ErrorState(it, onRetry = { vm.load() }) }

            SectionTitle("哪一段出了问题", subtitle = "${vm.legs.size} 段")
            if (vm.legs.isEmpty() && !vm.loading) {
                Text("没有被分配运输分段", color = t.textTertiary, fontSize = (13 * scale).sp)
            }
            vm.legs.forEach { item ->
                LegOption(
                    leg = item,
                    selected = item.legId == leg?.legId,
                    onSelect = { vm.selectLeg(item.legId) },
                )
            }

            SectionTitle("出了什么事", subtitle = "分类会写进给调度的一句话")
            FilterChipRow(
                options = DriverExceptionReason.values().map { it.label },
                selected = vm.reasonIndex,
                onSelect = { vm.selectReason(it) },
            )
            BusTextField(
                value = vm.detail,
                onValueChange = { vm.onDetailChange(it) },
                label = "补充说明",
                placeholder = "在哪、卡住多久、货现在怎么样",
                minLines = 3,
                enabled = vm.reason != null,
                error = if (vm.reason?.requiresDetail == true && vm.detail.isBlank()) "「其他」需要写清具体情况" else null,
            )
            Text(
                vm.reason?.let { "将发给后端：${buildExceptionRemark(it, vm.detail) ?: "（还不能发）"} · ${vm.detail.length}/${vm.detailBudget} 字" }
                    ?: "先选一项（说明限 ${vm.detailBudget} 字）",
                color = t.textTertiary,
                fontSize = (11.5 * scale).sp,
                modifier = Modifier.padding(top = 4.dp),
            )

            BusButton(
                text = if (picked == null) "附现场照片（可选）" else "已选 1 张，点击重选",
                variant = BusButtonVariant.OUTLINE,
                small = true,
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            )
            Text(
                if (picked == null) "没附照片也能上报；照片会记进这一单的事件流，事后作为责任凭证。"
                else "照片会随这次上报一起提交。",
                color = t.textTertiary,
                fontSize = (11 * scale).sp,
                modifier = Modifier.padding(top = 4.dp),
            )

            val canSend = leg != null && DriverLegStateMapper.of(leg).canReportException && vm.reason != null
            BusButton(
                text = "提交异常上报",
                enabled = canSend && !vm.busy,
                loading = vm.busy,
                variant = BusButtonVariant.DANGER,
                onClick = { vm.submit(context, picked) },
            )
            if (leg != null && !DriverLegStateMapper.of(leg).canReportException) {
                Text(
                    "这一段已是「${leg.statusLabel}」，异常由调度重排，不重复上报。",
                    color = t.textTertiary,
                    fontSize = (11.5 * scale).sp,
                )
            }
        }
    }
}

/** 选段行：状态与"能不能报"都取自 [DriverLegStateMapper]，页面不自己比码值 */
@Composable
private fun LegOption(leg: TransportLeg, selected: Boolean, onSelect: () -> Unit) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val flow = DriverLegStateMapper.of(leg)
    BusCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(BusRadius.m))
                .clickable(enabled = flow.canReportException, onClick = onSelect)
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size((26 * scale).dp)
                    .clip(RoundedCornerShape(BusRadius.pill))
                    .background(
                        when {
                            !flow.canReportException -> t.track
                            selected -> t.clayLight
                            else -> t.track
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${leg.seq}",
                    color = if (selected && flow.canReportException) t.clayDeep else t.textSecondary,
                    fontSize = (13 * scale).sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(
                    "${leg.fromStation} → ${leg.toStation}",
                    color = t.ink,
                    fontSize = (14 * scale).sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    legOptionTitle(leg, flow.canReportException),
                    color = t.textTertiary,
                    fontSize = (11.5 * scale).sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (selected && flow.canReportException) {
                Icon(BusIcons.check, contentDescription = "已选", tint = t.clayDeep, modifier = Modifier.size((18 * scale).dp))
            } else {
                BusTag(leg.statusLabel, tone = if (flow.canReportException) BusTone.NEUTRAL else BusTone.DANGER)
            }
        }
    }
}
