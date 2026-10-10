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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shanxiang.buspost.core.ui.BusButton
import com.shanxiang.buspost.core.ui.BusButtonVariant
import com.shanxiang.buspost.core.ui.BusCard
import com.shanxiang.buspost.core.ui.BusPage
import com.shanxiang.buspost.core.ui.BusRadius
import com.shanxiang.buspost.core.ui.BusTag
import com.shanxiang.buspost.core.ui.BusTone
import com.shanxiang.buspost.core.ui.ErrorState
import com.shanxiang.buspost.core.ui.InfoRow
import com.shanxiang.buspost.core.ui.LocalBusFontScale
import com.shanxiang.buspost.core.ui.LocalBusTokens
import com.shanxiang.buspost.core.ui.SectionTitle
import com.shanxiang.buspost.core.ui.Signpost
import com.shanxiang.buspost.data.model.EarningItem
import com.shanxiang.buspost.data.model.Earnings
import com.shanxiang.buspost.data.repo.Repo
import com.shanxiang.buspost.ui.common.DemoNote
import com.shanxiang.buspost.ui.common.ScreenViewModel
import com.shanxiang.buspost.ui.user.formatPrice
import kotlin.math.max

/** 运营统计：按天聚合的收益条 + 明细，替代小程序里只能看列表的做法 */
class EarningsViewModel : ScreenViewModel() {
    var data by mutableStateOf<Earnings?>(null)
        private set

    fun load() {
        work(reset = data == null) {
            val one = Repo.driverEarnings()
            data = one.value
            demo = one.demo
        }
    }
}

@Composable
fun EarningsScreen(vm: EarningsViewModel = viewModel()) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current

    LaunchedEffect(Unit) { if (vm.data == null) vm.load() }

    val e = vm.data
    BusPage(spacedBy = 12.dp) {
        Signpost(
            title = "运营统计",
            subtitle = "跑班收入按件计费，明细可回溯到订单",
            badge = e?.todayBadgeText ?: "今日 —",
        ) {
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                // 今日单数用 todayOrders，累计单数用 totalOrders：后端是两个字段，旧代码把 totalOrders 标成"今日"
                e?.todayOrders?.let { if (it > 0) BusTag("今日 $it 单", tone = BusTone.PRIMARY) }
                e?.totalWeight?.let { if (it > 0) BusTag("今日 ${formatPrice(it)} kg", tone = BusTone.CLAY) }
            }
        }

        DemoNote(vm.demo, text = "后端未返回收益，当前为演示金额，不代表真实结算。")
        vm.error?.let { ErrorState(it, onRetry = { vm.load() }) }

        if (e != null) {
            TicketCardRow(e)
            DayChart(e.items)
            SectionTitle("收益明细", subtitle = "${e.items.size} 笔")
            if (e.items.isEmpty()) {
                Text("暂无明细", color = t.textTertiary, fontSize = (13 * scale).sp)
            }
            e.items.forEach { ItemRow(it) }
        } else if (!vm.loading) {
            Text("没有取到统计数据", color = t.textTertiary, fontSize = (13 * scale).sp)
        }

        BusButton(
            text = "刷新统计",
            variant = BusButtonVariant.OUTLINE,
            onClick = { vm.load() },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TicketCardRow(e: Earnings) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
        MoneyCell("今日", e.today, Modifier.weight(1f), highlight = true)
        MoneyCell("本周", e.week, Modifier.weight(1f))
        MoneyCell("本月", e.month, Modifier.weight(1f))
    }
    BusCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            InfoRow("累计收益", e.totalText, valueColor = t.price)
            InfoRow("累计件数", e.orderCountText)
            InfoRow("累计载重", e.totalWeightText)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(top = 4.dp)) {
                BusTag("按件计费", tone = BusTone.GOLD)
                BusTag("T+1 结算", tone = BusTone.NEUTRAL)
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "结算口径以系统为准，此处仅供参考。",
                color = t.textTertiary,
                fontSize = (11 * scale).sp,
            )
        }
    }
}

@Composable
private fun MoneyCell(label: String, value: Double?, modifier: Modifier = Modifier, highlight: Boolean = false) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    Column(
        modifier
            .clip(RoundedCornerShape(BusRadius.l))
            .background(if (highlight) t.primaryLight else t.card)
            .padding(horizontal = 12.dp, vertical = 13.dp),
    ) {
        Text(label, color = if (highlight) t.primaryOnLight else t.textTertiary, fontSize = (11.5 * scale).sp)
        // 后端没给这一格时显示"未给"，不许塌成 0（F-2 §五：缺字段 ≠ 真零值）
        Text(
            if (value == null) "未给" else formatPrice(value),
            color = if (highlight) t.primary else t.ink,
            fontSize = (20 * scale).sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 3.dp),
        )
        if (value != null) Text("元", color = if (highlight) t.primaryOnLight else t.textTertiary, fontSize = (11 * scale).sp)
    }
}

@Composable
private fun DayChart(items: List<EarningItem>) {
    // 只有"有真实金额且日期可归集"的记录参与成图；缺金额的记录不计成 0（F-2 §五 B→A 禁止）
    val priced = items.mapNotNull { item -> item.amount?.takeIf { item.date.isNotEmpty() }?.let { item.date to it } }
    if (priced.isEmpty()) return
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val byDay = priced.groupBy({ it.first }, { it.second }).toSortedMap()
    val skipped = items.size - priced.size
    val maxAmount = max(1.0, byDay.values.maxOf { values -> values.sum() })
    BusCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text("按日收益", color = t.ink, fontSize = (14 * scale).sp, fontWeight = FontWeight.Bold)
            Text(
                if (skipped > 0) "近 ${byDay.size} 天，单位元（$skipped 笔金额未给，未计入）"
                else "近 ${byDay.size} 天，单位元",
                color = t.textTertiary,
                fontSize = (11.5 * scale).sp,
                modifier = Modifier.padding(top = 2.dp),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                byDay.forEach { (day, values) ->
                    val amount = values.sum()
                    val fraction = (amount / maxAmount).toFloat()
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text(formatPrice(amount), color = t.textSecondary, fontSize = (10.5 * scale).sp)
                        Box(
                            Modifier
                                .padding(top = 4.dp)
                                .fillMaxWidth()
                                .height((6 + 66 * fraction).dp)
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(t.primary),
                        )
                        Text(day, color = t.textTertiary, fontSize = (10.5 * scale).sp, modifier = Modifier.padding(top = 5.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemRow(item: EarningItem) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    BusCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size((30 * scale).dp)
                    .clip(RoundedCornerShape(BusRadius.m))
                    .background(t.track),
                contentAlignment = Alignment.Center,
            ) {
                Text(item.date.takeLast(2), color = t.textSecondary, fontSize = (12 * scale).sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.goodsName.ifEmpty { "货物" },
                    color = t.ink,
                    fontSize = (13.5 * scale).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${item.orderNo} · ${item.weightText}",
                    color = t.textTertiary,
                    fontSize = (11.5 * scale).sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(
                if (item.amount == null) item.amountText else "+${item.amountText}",
                color = t.price,
                fontSize = (15 * scale).sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
