package com.shanxiang.buspost.ui.user

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
import com.shanxiang.buspost.data.model.WalletBalance
import com.shanxiang.buspost.data.model.WalletTxPage
import com.shanxiang.buspost.data.repo.RemoteState
import com.shanxiang.buspost.data.repo.Repo
import com.shanxiang.buspost.ui.common.BackTopBar
import com.shanxiang.buspost.ui.common.DemoNote
import com.shanxiang.buspost.ui.common.ScreenViewModel
import com.shanxiang.buspost.ui.common.toState

/**
 * 用户端「我的钱包 / 交易记录」（#56）。**只读**：余额四格 + 流水列表 + 一个重试。
 *
 * 判定不在这层（全在 [WalletRows.kt]）。写口的三个理由（无幂等键、模拟与真到账字段不可区分、
 * `query-pay-order` 不校验归属人）记在 `Repo.walletTransactions()` 的注释与台账 D-086，
 * 并且这句话也写在屏上——用户找不到入口时，页面上要有一句真话，而不是让人以为 App 坏了。
 */
class WalletViewModel : ScreenViewModel() {
    /** 两个取数各自一份状态，初值都是"还没结论"（不是空数据）：开局就 success=true 等于替后端说"没流水/0 元" */
    var balance by mutableStateOf(RemoteState<WalletBalance>(loading = true))
        private set
    var txs by mutableStateOf(RemoteState<WalletTxPage>(loading = true))
        private set

    var fetched by mutableStateOf(false)
        private set

    fun load() {
        fetched = true
        work(reset = balance.data == null && txs.data == null) {
            val b = Repo.walletBalance().toState()
            val t = Repo.walletTransactions().toState()
            balance = b
            txs = t
            demo = b.demo || t.demo
            error = b.error ?: t.error
        }
    }
}

@Composable
fun WalletScreen(vm: WalletViewModel = viewModel()) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val page = walletPage(vm.balance, vm.txs)

    LaunchedEffect(Unit) { if (!vm.fetched) vm.load() }

    Column {
        BackTopBar(title = "我的钱包", subtitle = "余额与交易记录（只读）")
        BusPage(spacedBy = 12.dp) {
            DemoNote(page.balance.cell == WalletCell.Shown && vm.balance.demo, text = "当前为演示数据，不是平台真给出的余额。")
            WalletSectionBlock(page.balance, vm, scale, t) {
                vm.load()
            }
            WalletSectionBlock(page.txs, vm, scale, t) {
                vm.load()
            }
            Text(
                NO_WRITE_ENTRY_NOTE,
                color = t.textTertiary,
                fontSize = (12 * scale).sp,
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}

/** 一格一个结论：余额与流水各自有"没取到 / 还没取到 / 后端没给内容 / 有"，互不遮盖 */
@Composable
private fun WalletSectionBlock(
    section: WalletSection<*>,
    vm: WalletViewModel,
    scale: Float,
    t: com.shanxiang.buspost.core.ui.BusTokens,
    onRetry: () -> Unit,
) {
    when (section.cell) {
        WalletCell.Loading -> SkeletonList(2)
        WalletCell.Failed -> ErrorState(section.body, onRetry = onRetry)
        WalletCell.Empty -> EmptyState(title = section.title, subtitle = section.body, actionText = "重新取", onAction = onRetry)
        WalletCell.Shown -> {
            val rows: List<Pair<String, String>> = when (val d = section.data) {
                is WalletBalance -> walletBalanceRows(d)
                else -> emptyList()
            }
            BusCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                    ) {
                        Text(section.title, color = t.textSecondary, fontSize = (13 * scale).sp, fontWeight = FontWeight.Bold)
                        BusButton(
                            text = if (vm.loading) "重新取…" else "重新取",
                            small = true,
                            variant = BusButtonVariant.OUTLINE,
                            enabled = !vm.loading,
                            onClick = onRetry,
                            fullWidth = false,
                        )
                    }
                    if (section.body.isNotEmpty()) {
                        Text(section.body, color = t.textTertiary, fontSize = (11.5 * scale).sp, modifier = Modifier.padding(top = 4.dp))
                    }
                    when (val d = section.data) {
                        is WalletBalance -> LabeledRows(rows, scale, t)
                        is WalletTxPage -> d.items.forEachIndexed { i, tx ->
                            if (i > 0) BusDivider(Modifier.padding(vertical = 8.dp))
                            LabeledRows(walletTxRows(tx), scale, t)
                        }
                    }
                }
            }
        }
    }
}

/** 一行一个"标签 → 后端真给的原文 / 没给就说明没给"，页面自己不补第三个值 */
@Composable
private fun LabeledRows(rows: List<Pair<String, String>>, scale: Float, t: com.shanxiang.buspost.core.ui.BusTokens) {
    Column {
        rows.forEachIndexed { i, (label, value) ->
            if (i > 0) BusDivider(Modifier.padding(vertical = 7.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = t.textTertiary, fontSize = (12.5 * scale).sp, modifier = Modifier.width(104.dp))
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
