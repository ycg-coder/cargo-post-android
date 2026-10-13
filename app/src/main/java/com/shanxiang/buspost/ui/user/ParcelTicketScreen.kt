package com.shanxiang.buspost.ui.user

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shanxiang.buspost.core.ui.BusButton
import com.shanxiang.buspost.core.ui.BusButtonVariant
import com.shanxiang.buspost.core.ui.BusCard
import com.shanxiang.buspost.core.ui.BusDivider
import com.shanxiang.buspost.core.ui.BusPage
import com.shanxiang.buspost.core.ui.EmptyState
import com.shanxiang.buspost.core.ui.ErrorState
import com.shanxiang.buspost.core.ui.LocalBusFontScale
import com.shanxiang.buspost.core.ui.LocalBusTokens
import com.shanxiang.buspost.core.ui.SkeletonList
import com.shanxiang.buspost.data.model.ParcelTrace
import com.shanxiang.buspost.data.repo.RemoteState
import com.shanxiang.buspost.data.repo.Repo
import com.shanxiang.buspost.nav.Routes
import com.shanxiang.buspost.ui.common.BackTopBar
import com.shanxiang.buspost.ui.common.DemoNote
import com.shanxiang.buspost.ui.common.LocalNavController
import com.shanxiang.buspost.ui.common.ScreenViewModel
import com.shanxiang.buspost.ui.common.toState

/**
 * 用户端「查件票面」：`Repo.parcelTopology()`（`Repo.kt:780`，`GET /send/topology`）的落点 ——
 * 那条通路自写进来全仓零消费者，而"段与段怎么交接、为什么是这个方案、总共几段几次换乘"只有它会给。
 *
 * 这一页只渲染 `ParcelTicket.kt` 的判定结果（四格 + 每格的措辞），页面自己不补第三个值。
 * **兜底是硬要求**：取不到或后端回空结构时，页面上一定有"回追踪页"的出口 ——
 * 用户从追踪页点进来，不能因为票面这一轮没成就被关在门外。
 */
class ParcelTicketViewModel : ScreenViewModel() {
    /** 初值是"还没取过结论"（`loading = true`），不是空数据：开局就 `success = true` 等于替后端说"这张单没内容" */
    var state by mutableStateOf(RemoteState<ParcelTrace>(loading = true))
        private set

    var fetched by mutableStateOf(false)
        private set

    fun load(number: String) {
        fetched = true
        work(reset = state.data == null) {
            val s = Repo.parcelTopology(number).toState()
            state = s
            demo = s.demo
            error = s.error
        }
    }
}

@Composable
fun ParcelTicketScreen(no: String, vm: ParcelTicketViewModel = viewModel()) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val nav = LocalNavController.current
    val page = parcelTicketPage(vm.state)

    LaunchedEffect(no) { if (!vm.fetched) vm.load(no) }

    Column {
        BackTopBar(title = "查件票面", subtitle = no)
        BusPage(spacedBy = 12.dp) {
            DemoNote(page.demo, text = "当前为演示票面，不是平台真给出的排线与交接。")
            when (page.kind) {
                ParcelTicketKind.Loading -> SkeletonList(3)

                ParcelTicketKind.Failed -> {
                    ErrorState(page.body, onRetry = { vm.load(no) })
                    // 兜底出口：票面这一轮没成，也要能回去看已知进度，不能把人关在这里
                    BackToTraceRow { nav.navigate(Routes.trace(no)) }
                }

                ParcelTicketKind.Absent -> {
                    EmptyState(
                        title = page.title,
                        subtitle = page.body,
                        actionText = "重新取一次",
                        onAction = { vm.load(no) },
                    )
                    BackToTraceRow { nav.navigate(Routes.trace(no)) }
                }

                ParcelTicketKind.Shown -> {
                    val tr = page.trace
                    if (tr != null) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(page.title, color = t.textSecondary, fontSize = (13 * scale).sp)
                            BusButton(
                                text = if (vm.loading) "重新取…" else "重新取",
                                small = true,
                                variant = BusButtonVariant.OUTLINE,
                                enabled = !vm.loading,
                                onClick = { vm.load(no) },
                                fullWidth = false,
                            )
                        }
                        TicketCard("整单", ticketHeaderRows(tr))
                        if (tr.legs.isEmpty()) {
                            TicketCard("分段", listOf("后端没给" to NOT_GIVEN))
                        } else {
                            tr.legs.forEachIndexed { i, l -> TicketCard("第 ${i + 1} 张段票", legTicketRows(l)) }
                        }
                        if (tr.handovers.isEmpty()) {
                            TicketCard("换乘交接", listOf("后端没给" to NOT_GIVEN))
                        } else {
                            tr.handovers.forEachIndexed { i, h -> TicketCard("交接 ${i + 1}", handoverRows(h)) }
                        }
                        if (tr.plans.isEmpty()) {
                            // `candidates` 在后端 preview 抛异常时保持 null（ServiceImpl:172-174）⇒ 只能说"没给"
                            TicketCard("方案对比", listOf("后端没给" to "方案对比没给，不表示这单只有一个方案"))
                        } else {
                            tr.plans.forEachIndexed { i, p -> TicketCard("方案 ${i + 1}", planRows(p)) }
                        }
                        TicketCard(
                            "事件时间线",
                            if (ticketEventsGiven(tr)) ticketEventRows(tr)
                            else listOf("后端没给" to "这条链路这轮没发事件，不等于没发生过事"),
                        )
                        BackToTraceRow { nav.navigate(Routes.trace(no)) }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

/** 一行一个"标签 → 后端真给的原文 / 没给就说明没给" */
@Composable
private fun TicketCard(title: String, rows: List<Pair<String, String>>) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    BusCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(title, color = t.textSecondary, fontSize = (12.5 * scale).sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            rows.forEachIndexed { i, (label, value) ->
                if (i > 0) BusDivider(Modifier.padding(vertical = 7.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        label,
                        color = t.textTertiary,
                        fontSize = (12.5 * scale).sp,
                        modifier = Modifier.width(92.dp),
                    )
                    Text(
                        value,
                        color = if (value == NOT_GIVEN) t.textTertiary else t.ink,
                        fontSize = (13.5 * scale).sp,
                        fontWeight = if (i == 0) FontWeight.Medium else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun BackToTraceRow(onOpen: () -> Unit) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    BusCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(
                "这张单的已知进度在追踪页，票面没成也不该挡住你",
                color = t.textTertiary,
                fontSize = (12.5 * scale).sp,
            )
            Spacer(Modifier.height(6.dp))
            BusButton(
                text = "回追踪页",
                variant = BusButtonVariant.OUTLINE,
                onClick = onOpen,
                fullWidth = false,
            )
        }
    }
}
