package com.shanxiang.buspost.ui.user

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shanxiang.buspost.core.ui.BusButton
import com.shanxiang.buspost.core.ui.BusButtonVariant
import com.shanxiang.buspost.core.ui.BusCard
import com.shanxiang.buspost.core.ui.BusIcons
import com.shanxiang.buspost.core.ui.BusPage
import com.shanxiang.buspost.core.ui.BusSwitch
import com.shanxiang.buspost.core.ui.BusTag
import com.shanxiang.buspost.core.ui.ErrorState
import com.shanxiang.buspost.core.ui.InfoRow
import com.shanxiang.buspost.core.ui.LocalBusFontScale
import com.shanxiang.buspost.core.ui.LocalBusTokens
import com.shanxiang.buspost.core.ui.SectionTitle
import com.shanxiang.buspost.core.ui.SkeletonList
import com.shanxiang.buspost.data.model.ParcelTrace
import com.shanxiang.buspost.data.model.TransportLeg
import com.shanxiang.buspost.data.repo.Repo
import com.shanxiang.buspost.data.store.Prefs
import com.shanxiang.buspost.notify.NotifyWorker
import com.shanxiang.buspost.ui.common.BackTopBar
import com.shanxiang.buspost.ui.common.DemoNote
import com.shanxiang.buspost.ui.common.ScreenViewModel
import com.shanxiang.buspost.ui.common.TraceView
import kotlinx.coroutines.delay

class TraceViewModel : ScreenViewModel() {
    var trace by mutableStateOf<ParcelTrace?>(null)
    var legs by mutableStateOf<List<TransportLeg>>(emptyList())
    var no by mutableStateOf("")
    // 页面可见时每 30 秒自动刷新，小程序切后台就查不到实时位置
    var autoRefresh by mutableStateOf(true)
    var updatedAgo by mutableStateOf(0L)

    fun load(number: String) = work {
        no = number
        val one = Repo.trackParcel(number)
        val t = one.value
        val extra = Repo.parcelLegs(number)
        trace = t?.copy(legs = if (t.legs.isEmpty()) extra.items else t.legs)
        legs = trace?.legs.orEmpty()
        demo = one.demo || extra.demo
        error = one.error ?: extra.error
        updatedAgo = System.currentTimeMillis()
    }
}

@Composable
fun TraceScreen(no: String, vm: TraceViewModel = viewModel()) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var alertOn by remember { mutableStateOf(Prefs.arrivalAlertEnabled) }

    LaunchedEffect(no) { vm.load(no) }
    LaunchedEffect(vm.autoRefresh, no) {
        while (vm.autoRefresh) {
            delay(30_000)
            vm.load(no)
        }
    }

    Column {
        BackTopBar(title = "包裹追踪", subtitle = no)
        BusPage(spacedBy = 12.dp) {
            DemoNote(vm.demo, text = "后端没有这单的实时数据，下面是演示轨迹，仅用于查看交互。")
            vm.error?.let { ErrorState(it, onRetry = { vm.load(no) }) }

            if (vm.loading && vm.trace == null) {
                SkeletonList(3)
            } else {
                val tr = vm.trace
                if (tr == null) {
                    ErrorState("没查到单号 $no，检查一下是否抄错", onRetry = { vm.load(no) })
                } else {
                    TraceView(
                        trace = tr,
                        statusLabel = tr.statusLabel,
                        tone = tr.tone,
                        onReplan = { vm.load(no) },
                    )

                    SectionTitle("单号与操作")
                    BusCard(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            InfoRow(label = "单号", value = tr.orderNo.ifEmpty { no }, icon = BusIcons.info)
                            InfoRow(
                                label = "货物",
                                value = tr.goodsName,
                                icon = BusIcons.parcel,
                            )
                            InfoRow(
                                label = "最近更新",
                                value = "${((System.currentTimeMillis() - vm.updatedAgo) / 1000).coerceAtLeast(0)} 秒前" +
                                    if (vm.autoRefresh) " · 每 30 秒自动刷新" else "",
                                icon = BusIcons.clock,
                            )
                        }
                    }

                    BusCard(modifier = Modifier.fillMaxWidth(), warm = alertOn) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(BusIcons.bell, null, tint = if (alertOn) t.primary else t.textTertiary, modifier = Modifier.size(20.dp))
                            Column(Modifier.weight(1f).padding(start = 9.dp)) {
                                Text("到站提醒", color = t.ink, fontSize = (14.5 * scale).sp, fontWeight = FontWeight.Bold)
                                Text(
                                    if (alertOn) "车到 ${tr.targetStation.ifEmpty { "目的地" }} 前 10 分钟，通知栏会弹提醒"
                                    else "开启后 App 退到后台也会提醒",
                                    color = t.textSecondary,
                                    fontSize = (11.5 * scale).sp,
                                    modifier = Modifier.padding(top = 3.dp),
                                )
                            }
                            BusSwitch(
                                checked = alertOn,
                                onCheckedChange = {
                                    alertOn = it
                                    Prefs.arrivalAlertEnabled = it
                                    NotifyWorker.runNow(context)
                                },
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BusButton(
                            text = "复制单号",
                            onClick = { clipboard.setText(AnnotatedString(tr.orderNo.ifEmpty { no })) },
                            variant = BusButtonVariant.OUTLINE,
                            modifier = Modifier.weight(1f),
                            small = true,
                        )
                        BusButton(
                            text = "刷新轨迹",
                            onClick = { vm.load(no) },
                            variant = BusButtonVariant.OUTLINE,
                            modifier = Modifier.weight(1f),
                            small = true,
                            loading = vm.loading,
                        )
                        if (tr.orderNo.isNotEmpty()) {
                            BusButton(
                                text = "转发给收货人",
                                onClick = {
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "我的包裹 ${tr.orderNo} 当前状态：${tr.statusLabel}，可在乡邮智联 App 查看轨迹。",
                                        )
                                    }
                                    context.startActivity(Intent.createChooser(send, "分享包裹进度"))
                                },
                                variant = BusButtonVariant.OUTLINE,
                                modifier = Modifier.weight(1f),
                                small = true,
                            )
                        }
                    }

                    if (vm.legs.isNotEmpty()) {
                        SectionTitle("分段详情", subtitle = "共 ${vm.legs.size} 段")
                        vm.legs.forEach { leg ->
                            BusCard(modifier = Modifier.fillMaxWidth()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            "第 ${leg.seq} 段 ${leg.fromStation} → ${leg.toStation}",
                                            color = t.ink,
                                            fontSize = (13.5 * scale).sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(
                                            listOfNotNull(leg.plate.ifEmpty { null }, leg.driverName.ifEmpty { null })
                                                .joinToString(" · ").ifEmpty { "承运待定" },
                                            color = t.textSecondary,
                                            fontSize = (12 * scale).sp,
                                            modifier = Modifier.padding(top = 3.dp),
                                        )
                                    }
                                    BusTag(leg.statusLabel, tone = leg.statusTone)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
