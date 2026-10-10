package com.shanxiang.buspost.ui.user

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanxiang.buspost.core.ui.BusButton
import com.shanxiang.buspost.core.ui.BusCard
import com.shanxiang.buspost.core.ui.BusDivider
import com.shanxiang.buspost.core.ui.BusIcons
import com.shanxiang.buspost.core.ui.BusRadius
import com.shanxiang.buspost.core.ui.BusTextField
import com.shanxiang.buspost.core.ui.BusTone
import com.shanxiang.buspost.core.ui.LocalBusFontScale
import com.shanxiang.buspost.core.ui.LocalBusTokens
import com.shanxiang.buspost.ui.common.BackTopBar
import com.shanxiang.buspost.data.repo.Repo
import com.shanxiang.buspost.ui.common.ScreenViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class WalletViewModel : ScreenViewModel() {
    var balance by mutableStateOf(0.0)
    var transactions by mutableStateOf<List<TransactionItem>>(emptyList())

    fun load() = work {
        val bal = Repo.walletBalance()
        balance = bal.balance
        val txs = Repo.walletTransactions(20)
        transactions = txs.map { tx ->
            val displayTime = tx.createTime
                .replace("T", " ")
                .substringBeforeLast(":")
                .let { raw ->
                    if (raw.length > 16) raw.substring(0, 16) else raw
                }
            TransactionItem(
                title = tx.title.ifEmpty { when (tx.type) {
                    "recharge" -> "充值"
                    "consume" -> "消费"
                    "refund" -> "退款"
                    else -> tx.type
                } },
                amount = tx.amount,
                time = displayTime,
                type = when (tx.type) {
                    "recharge" -> "recharge"
                    "consume" -> when {
                        tx.title.contains("公交") || tx.title.contains("乘车") -> "bus"
                        tx.title.contains("寄货") -> "send"
                        else -> "shopping"
                    }
                    "refund" -> "recharge"
                    else -> "shopping"
                },
            )
        }
    }

    fun addRechargeRecord(amount: Double) {
        val now = java.text.SimpleDateFormat("今天 HH:mm", java.util.Locale.CHINA).format(java.util.Date())
        transactions = listOf(TransactionItem("充值", amount, now, "recharge")) + transactions
        balance += amount
    }

    fun doRecharge(amount: Double, channel: String = "alipay") = work(reset = false) {
        val payResult = Repo.createPayOrder(amount, channel)
        if (payResult == null) {
            addRechargeRecord(amount)
            return@work
        }
        if (payResult.optBoolean("simulated", false)) {
            addRechargeRecord(amount)
            return@work
        }
        pendingPayOrderNo = payResult.optString("orderNo", "")
        pendingPayOrderString = payResult.optString("orderString", "")
    }

    var pendingPayOrderString by mutableStateOf("")
    var pendingPayOrderNo by mutableStateOf("")

    fun checkPayResult() = work(reset = false) {
        val orderNo = pendingPayOrderNo
        if (orderNo.isEmpty()) return@work
        val result = Repo.queryPayOrder(orderNo)
        if (result != null && result.optString("status") == "paid") {
            pendingPayOrderNo = ""
            pendingPayOrderString = ""
            load()
        }
    }

    fun clearPendingPay() {
        pendingPayOrderString = ""
        pendingPayOrderNo = ""
    }
}

data class TransactionItem(
    val title: String,
    val amount: Double,
    val time: String,
    val type: String,
)

private const val ALIPAY_PAY = "alipays://platformapi/startapp?appId=10000007"
private const val WECHAT_PAY = "weixin://"

@Composable
fun WalletScreen(
    vm: WalletViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val context = LocalContext.current
    var showRecharge by remember { mutableStateOf(false) }
    var showPay by remember { mutableStateOf<PayRequest?>(null) }

    LaunchedEffect(Unit) {
        if (vm.balance == 0.0 && vm.transactions.isEmpty()) vm.load()
    }

    // 监听待支付订单：有 orderString 时调起支付宝
    LaunchedEffect(vm.pendingPayOrderString) {
        val orderString = vm.pendingPayOrderString
        if (orderString.isNotEmpty()) {
            try {
                // 支付宝 H5 支付：通过 URL scheme 打开
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("alipays://platformapi/startapp?sa_id=10000007&clientVersion=3.7.0.0718&qrcode=$orderString"))
                context.startActivity(intent)
            } catch (_: Exception) {
                try {
                    // 降级：打开支付宝扫码页
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("alipays://platformapi/startapp?appId=10000007")))
                } catch (_: Exception) {
                    Toast.makeText(context, "未检测到支付宝，请先安装支付宝", Toast.LENGTH_SHORT).show()
                }
            }
            vm.clearPendingPay()
        }
    }

    fun openAlipay() {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ALIPAY_PAY)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "未检测到支付宝，请先安装支付宝", Toast.LENGTH_SHORT).show()
        }
    }

    fun openWechat() {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(WECHAT_PAY)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "未检测到微信，请先安装微信", Toast.LENGTH_SHORT).show()
        }
    }

    Column(Modifier.fillMaxSize()) {
        BackTopBar(title = "钱包", subtitle = "余额与交易记录")

        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 余额卡
            item {
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(BusRadius.l))
                        .background(
                            Brush.linearGradient(
                                listOf(t.primaryDark, t.primary, t.accent)
                            )
                        )
                        .padding(20.dp),
                ) {
                    Column {
                        Text(
                            "账户余额（元）",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = (13 * scale).sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            String.format("%.2f", vm.balance),
                            color = Color.White,
                            fontSize = (36 * scale).sp,
                            fontWeight = FontWeight.Black,
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            WalletActionChip("充值") { showRecharge = true }
                            WalletActionChip("收款码") { openAlipay() }
                            WalletActionChip("提现") { openAlipay() }
                        }
                    }
                }
            }

            // 支付方式
            item {
                BusCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "支付方式",
                        color = t.ink,
                        fontSize = (14 * scale).sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                    PaymentMethodRow(
                        icon = BusIcons.wallet,
                        title = "余额支付",
                        subtitle = "余额 ¥${String.format("%.2f", vm.balance)}",
                        onClick = null,
                    )
                    BusDivider(modifier = Modifier.padding(vertical = 8.dp))
                    PaymentMethodRow(
                        icon = BusIcons.qr,
                        title = "支付宝",
                        subtitle = "支付宝快捷支付",
                        onClick = { openAlipay() },
                    )
                    BusDivider(modifier = Modifier.padding(vertical = 8.dp))
                    PaymentMethodRow(
                        icon = BusIcons.send,
                        title = "微信支付",
                        subtitle = "微信快捷支付",
                        onClick = { openWechat() },
                    )
                }
            }

            // 交易记录标题
            item {
                Row(
                    Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "交易记录",
                        color = t.ink,
                        fontSize = (15 * scale).sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "查看全部",
                        color = t.primaryOnLight,
                        fontSize = (12 * scale).sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            if (vm.transactions.isEmpty() && !vm.loading) {
                item {
                    Text(
                        "暂无交易记录",
                        color = t.textSecondary,
                        fontSize = (13 * scale).sp,
                        modifier = Modifier.padding(vertical = 24.dp).fillMaxWidth(),
                    )
                }
            }

            items(vm.transactions, key = { "${it.title}_${it.time}" }) { tx ->
                TransactionRow(tx, scale)
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }

    // 充值弹窗
    if (showRecharge) {
        RechargeDialog(
            onDismiss = { showRecharge = false },
            onConfirm = { amount, method ->
                showRecharge = false
                vm.doRecharge(amount, method)
            },
        )
    }
}

@Composable
private fun WalletActionChip(label: String, onClick: () -> Unit) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    Box(
        Modifier
            .clip(RoundedCornerShape(BusRadius.pill))
            .background(Color.White.copy(alpha = 0.22f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            color = Color.White,
            fontSize = (13 * scale).sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun PaymentMethodRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)?,
) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = if (onClick != null) {
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(BusRadius.m))
                .clickable { onClick() }
                .padding(vertical = 2.dp)
        } else Modifier,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(t.primaryLight),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = t.primary, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            Text(title, color = t.ink, fontSize = (13 * scale).sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = t.textTertiary, fontSize = (11 * scale).sp)
        }
        if (onClick != null) {
            Icon(BusIcons.next, null, tint = t.textTertiary, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun TransactionRow(tx: TransactionItem, scale: Float) {
    val t = LocalBusTokens.current
    val (icon, bgTint) = when (tx.type) {
        "bus" -> BusIcons.bus to t.primaryLight
        "recharge" -> BusIcons.wallet to t.goldLight
        "send" -> BusIcons.send to t.clay.copy(alpha = 0.12f)
        "shopping" -> BusIcons.leaf to t.primaryLight
        else -> BusIcons.calendar to t.track
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(BusRadius.m))
            .background(t.card)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(bgTint),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = t.primary, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            Text(tx.title, color = t.ink, fontSize = (13 * scale).sp, fontWeight = FontWeight.Medium)
            Text(tx.time, color = t.textTertiary, fontSize = (11 * scale).sp, modifier = Modifier.padding(top = 2.dp))
        }
        Text(
            buildString {
                if (tx.amount > 0) append("+")
                append(String.format("%.2f", tx.amount))
            },
            color = if (tx.amount > 0) t.primary else t.danger,
            fontSize = (14 * scale).sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private data class PayRequest(val amount: Double, val method: String)

@Composable
private fun RechargeDialog(
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, method: String) -> Unit,
) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    var amountText by remember { mutableStateOf("") }
    var selectedMethod by remember { mutableStateOf("alipay") }

    val quickAmounts = listOf("10", "30", "50", "100")
    val amount = amountText.toDoubleOrNull() ?: 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("充值") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("选择充值金额", color = t.ink, fontSize = (13 * scale).sp, fontWeight = FontWeight.Medium)

                // 快捷金额
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    quickAmounts.forEach { qa ->
                        val selected = amountText == qa
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(BusRadius.m))
                                .background(if (selected) t.primary else t.track)
                                .clickable { amountText = qa }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            Text(
                                "¥$qa",
                                color = if (selected) Color.White else t.ink,
                                fontSize = (14 * scale).sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                // 自定义金额
                BusTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = "自定义金额",
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(4.dp))
                Text("支付方式", color = t.ink, fontSize = (13 * scale).sp, fontWeight = FontWeight.Medium)

                // 支付宝
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(BusRadius.m))
                        .background(if (selectedMethod == "alipay") t.primaryLight else t.track.copy(alpha = 0.5f))
                        .clickable { selectedMethod = "alipay" }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1677FF)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("支", color = Color.White, fontSize = (12 * scale).sp, fontWeight = FontWeight.Black)
                    }
                    Text("支付宝", color = t.ink, fontSize = (13 * scale).sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 10.dp).weight(1f))
                    if (selectedMethod == "alipay") {
                        Icon(BusIcons.check, null, tint = t.primary, modifier = Modifier.size(18.dp))
                    }
                }

                // 微信
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(BusRadius.m))
                        .background(if (selectedMethod == "wechat") t.primaryLight else t.track.copy(alpha = 0.5f))
                        .clickable { selectedMethod = "wechat" }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF07C160)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("微", color = Color.White, fontSize = (12 * scale).sp, fontWeight = FontWeight.Black)
                    }
                    Text("微信支付", color = t.ink, fontSize = (13 * scale).sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 10.dp).weight(1f))
                    if (selectedMethod == "wechat") {
                        Icon(BusIcons.check, null, tint = t.primary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (amount > 0) onConfirm(amount, selectedMethod) },
                enabled = amount > 0,
            ) {
                Text("确认充值 ¥${if (amount > 0) String.format("%.0f", amount) else "0"}")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
