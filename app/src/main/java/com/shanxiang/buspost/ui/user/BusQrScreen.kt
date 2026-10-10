package com.shanxiang.buspost.ui.user

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanxiang.buspost.core.ui.BusCard
import com.shanxiang.buspost.core.ui.BusIcons
import com.shanxiang.buspost.core.ui.BusRadius
import com.shanxiang.buspost.core.ui.LocalBusFontScale
import com.shanxiang.buspost.core.ui.LocalBusTokens
import com.shanxiang.buspost.core.ui.QrView
import com.shanxiang.buspost.ui.common.BackTopBar

/**
 * 乘车码页面：展示当地公交乘车二维码，扫码上车。
 * 对标支付宝/微信乘车码：大二维码 + 动态刷新 + 线路提示。
 */
@Composable
fun BusQrScreen() {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current

    // 二维码内容：用用户ID + 时间戳生成动态码（每分钟刷新）
    val qrContent = "BUSPOST-QR:${System.currentTimeMillis() / 60000}"

    // 呼吸动画提示扫码
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

    Column(Modifier.fillMaxSize()) {
        BackTopBar(title = "乘车码", subtitle = "扫码乘车 · 到站自动扣费")

        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 顶部提示
            BusCard(modifier = Modifier.fillMaxWidth(), warm = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(BusIcons.bus, null, tint = t.goldDeep, modifier = Modifier.size(20.dp))
                    Column(Modifier.padding(start = 10.dp).weight(1f)) {
                        Text("城乡公交乘车码", color = t.ink, fontSize = (14 * scale).sp, fontWeight = FontWeight.Bold)
                        Text("请将二维码对准车载扫码机", color = t.textSecondary, fontSize = (12 * scale).sp)
                    }
                }
            }

            // 二维码区域
            Box(
                Modifier
                    .size(240.dp)
                    .clip(RoundedCornerShape(BusRadius.l))
                    .background(Color.White)
                    .border(
                        width = 3.dp,
                        color = t.primary.copy(alpha = pulseAlpha),
                        shape = RoundedCornerShape(BusRadius.l),
                    )
                    .padding(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                QrView(
                    text = qrContent,
                    caption = "",
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // 刷新提示
            Text(
                "二维码每分钟自动刷新",
                color = t.textTertiary,
                fontSize = (11 * scale).sp,
            )

            Spacer(Modifier.height(8.dp))

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
                        "· 上车扫码，下车无需再扫\n· 票价按站点距离自动计算\n· 余额不足时请提前充值\n· 如遇扫码失败请联系司机",
                        color = t.textSecondary,
                        fontSize = (12 * scale).sp,
                        lineHeight = (18 * scale).sp,
                    )
                }
            }
        }
    }
}
