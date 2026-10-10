package com.shanxiang.buspost.ui.user

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanxiang.buspost.core.ui.BusButton
import com.shanxiang.buspost.core.ui.BusCard
import com.shanxiang.buspost.core.ui.BusIcons
import com.shanxiang.buspost.core.ui.BusRadius
import com.shanxiang.buspost.core.ui.LocalBusFontScale
import com.shanxiang.buspost.core.ui.LocalBusTokens
import com.shanxiang.buspost.core.ui.QrView
import com.shanxiang.buspost.ui.common.BackTopBar

private const val ALIPAY_TRANSIT_QR = "alipays://platformapi/startapp?appId=20001104"
private const val ALIPAY_QR_SCANNER = "alipays://platformapi/startapp?appId=10000007"

@Composable
fun BusQrScreen() {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val context = LocalContext.current

    val qrContent = "BUSPOST-QR:${System.currentTimeMillis() / 60000}"

    val infiniteTransition = rememberInfiniteTransition(label = "qr-pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    fun openAlipayTransit() {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ALIPAY_TRANSIT_QR)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "未检测到支付宝，请先安装支付宝", Toast.LENGTH_SHORT).show()
        }
    }

    fun openAlipayScanner() {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ALIPAY_QR_SCANNER)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "未检测到支付宝，请先安装支付宝", Toast.LENGTH_SHORT).show()
        }
    }

    Column(Modifier.fillMaxSize()) {
        BackTopBar(title = "乘车码", subtitle = "扫码乘车 · 到站自动扣费")

        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 支付宝乘车码入口（主推）
            BusCard(
                modifier = Modifier.fillMaxWidth().clickable { openAlipayTransit() },
                warm = true,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1677FF)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("支", color = Color.White, fontSize = (16 * scale).sp, fontWeight = FontWeight.Black)
                    }
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text("重庆公交乘车码", color = t.ink, fontSize = (15 * scale).sp, fontWeight = FontWeight.Bold)
                        Text("支付宝官方 · 全市公交通用", color = t.textSecondary, fontSize = (12 * scale).sp)
                    }
                    Text("打开 →", color = t.primaryOnLight, fontSize = (13 * scale).sp, fontWeight = FontWeight.Bold)
                }
            }

            // 本 App 二维码
            Box(
                Modifier
                    .size(220.dp)
                    .clip(RoundedCornerShape(BusRadius.l))
                    .background(Color.White)
                    .border(
                        width = 3.dp,
                        color = t.primary.copy(alpha = pulseAlpha),
                        shape = RoundedCornerShape(BusRadius.l),
                    )
                    .padding(18.dp),
                contentAlignment = Alignment.Center,
            ) {
                QrView(
                    text = qrContent,
                    caption = "",
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Text(
                "乡邮智联乘车码 · 每分钟自动刷新",
                color = t.textTertiary,
                fontSize = (11 * scale).sp,
                textAlign = TextAlign.Center,
            )

            // 双入口按钮
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BusButton(
                    text = "支付宝乘车码",
                    onClick = ::openAlipayTransit,
                    modifier = Modifier.weight(1f),
                )
                BusButton(
                    text = "扫码付款",
                    onClick = ::openAlipayScanner,
                    modifier = Modifier.weight(1f),
                    variant = com.shanxiang.buspost.core.ui.BusButtonVariant.OUTLINE,
                )
            }

            // 支持的线路
            BusCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("适用线路", color = t.ink, fontSize = (14 * scale).sp, fontWeight = FontWeight.Bold)
                    listOf(
                        "1路 · 县城⇄云山村",
                        "2路 · 县城⇄石桥村",
                        "3路 · 县城⇄河坝镇",
                        "K1 · 县城快线",
                    ).forEach { line ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(t.primary),
                            )
                            Text(
                                line,
                                color = t.textSecondary,
                                fontSize = (12 * scale).sp,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }

            // 使用须知
            BusCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("使用须知", color = t.ink, fontSize = (14 * scale).sp, fontWeight = FontWeight.Bold)
                    Text(
                        "· 推荐使用支付宝乘车码，全市公交通用\n· 上车扫码，下车无需再扫\n· 票价按站点距离自动计算\n· 余额不足时请提前充值",
                        color = t.textSecondary,
                        fontSize = (12 * scale).sp,
                        lineHeight = (18 * scale).sp,
                    )
                }
            }
        }
    }
}
