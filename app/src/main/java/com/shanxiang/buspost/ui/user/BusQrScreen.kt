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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanxiang.buspost.core.ui.BusButton
import com.shanxiang.buspost.core.ui.BusCard
import com.shanxiang.buspost.core.ui.BusIcons
import com.shanxiang.buspost.core.ui.BusRadius
import com.shanxiang.buspost.core.ui.LocalBusFontScale
import com.shanxiang.buspost.core.ui.LocalBusTokens
import com.shanxiang.buspost.ui.common.BackTopBar

private const val ALIPAY_TRANSIT_QR = "alipays://platformapi/startapp?appId=20001104"
private const val ALIPAY_QR_SCANNER = "alipays://platformapi/startapp?appId=10000007"

@Composable
fun BusQrScreen() {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val context = LocalContext.current

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
        BackTopBar(title = "乘车码", subtitle = "本机没有卡务接口 · 扫码乘车走支付宝官方码")

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
                            // 硬编码色：支付宝品牌蓝 1677FF —— 品牌方规定的入口色，不随本 App 四套配色变
                            .background(Color(0xFF1677FF)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("支", color = Color.White, fontSize = (16 * scale).sp, fontWeight = FontWeight.Black)
                    }
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text("重庆公交乘车码", color = t.ink, fontSize = (15 * scale).sp, fontWeight = FontWeight.Bold)
                        // "全市公交通用"本机无从核实：支付宝乘车码的可用线路与扣费只在支付宝那一侧决定。
                        Text("支付宝官方 · 可用线路以支付宝页面为准", color = t.textSecondary, fontSize = (12 * scale).sp)
                    }
                    Text("打开 →", color = t.primaryOnLight, fontSize = (13 * scale).sp, fontWeight = FontWeight.Bold)
                }
            }

            // 本机不出二维码：后端没有任何卡务/闸机/乘车码接口（`grep -rn "qr|card|ticket-code"
            // App*Controller` 0 个出口），而下面那行 `"BUSPOST-QR:" + 时间戳` 画出来的图**谁都扫不出来**——
            // 把它摆在"每分钟自动刷新"的话术下面，就是让用户举着一张假票上车。
            // 真实能用的只有支付宝乘车码那一个出口，所以这一格改成品实的话 + 那一个出口。
            BusCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("本机不出乘车码", color = t.ink, fontSize = (14 * scale).sp, fontWeight = FontWeight.Bold)
                    Text(
                        "后台还没有卡务/闸机接口，这一页没有可刷新的二维码。" +
                            "要扫码乘车请走下面那张「支付宝官方乘车码」，扣费与线路以支付宝页面显示为准。",
                        color = t.textSecondary,
                        fontSize = (12 * scale).sp,
                        lineHeight = (17 * scale).sp,
                    )
                }
            }

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

            // 使用须知：只说这一页真能做的事。
            // 原来这里钉了一份手写线路表（1路 县城⇄…、K1 县城快线…），后台没有给过这些车次，
            // 而判据 L-v 的全工程唯一硬编码村名额度是给输入框示例用的，不是给假线路让路的（D-066）。
            BusCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("使用须知", color = t.ink, fontSize = (14 * scale).sp, fontWeight = FontWeight.Bold)
                    Text(
                        "· 本页不代扣车费，票价与优惠按承运方规则结算\n" +
                            "· 能坐哪几路、怎么刷卡，以支付宝乘车码页面显示为准\n" +
                            "· 线路与班次在「实时公交」那一页读的是后台下发，不在这页手写",
                        color = t.textSecondary,
                        fontSize = (12 * scale).sp,
                        lineHeight = (18 * scale).sp,
                    )
                }
            }
        }
    }
}
