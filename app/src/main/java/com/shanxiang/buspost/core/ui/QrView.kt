package com.shanxiang.buspost.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanxiang.buspost.core.code.QrCode

/**
 * 订单二维码票根 —— 对应小程序寄货成功页 / 单号查询里的 `#qrCanvas`。
 * 白底 + 主题墨色模块（与小程序 qrcode-render.js 同参数），下方一行用途说明。
 */
@Composable
fun QrView(
    text: String,
    modifier: Modifier = Modifier,
    size: Int = 168,
    caption: String? = "司机扫码装车 / 妥投核验",
) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    // 墨色随主题走，避免深色模式下二维码仍是浅底深点造成观感割裂
    val bmp = remember(text, t.ink) {
        QrCode.bitmap(text, sizePx = size * 4, darkColor = t.ink.toArgb())
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(size.dp)
                .clip(RoundedCornerShape(BusRadius.m))
                .background(Color.White)
                .padding(6.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "订单二维码",
                    modifier = Modifier.size((size - 12).dp),
                )
            } else {
                Text("二维码生成失败", color = t.textTertiary, fontSize = (12 * scale).sp)
            }
        }
        if (caption != null) {
            Text(
                caption,
                color = t.textSecondary,
                fontSize = (12 * scale).sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 9.dp),
            )
        }
    }
}

private fun Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255f).toInt().coerceIn(0, 255),
    (red * 255f).toInt().coerceIn(0, 255),
    (green * 255f).toInt().coerceIn(0, 255),
    (blue * 255f).toInt().coerceIn(0, 255),
)
