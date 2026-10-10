package com.shanxiang.buspost.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * 启动闪屏 —— 对标主流 App（高德/车来了/货拉拉/滴滴货运）的简洁品牌展示。
 *
 * 设计契约（2026-10-10 改版，替代旧 13 节点散场动画）：
 *  ① 品牌画布：柔光渐变底 + 中心徽标 + 名称 + slogan + 进度条 + 状态文案 + 版本号。
 *  ② 入场动画：徽标从 0.85x 缩放至 1.0x + 淡入（350ms），名称/slogan 延迟入场（200ms stagger）。
 *  ③ 就绪后：徽标轻微呼吸脉动（infinite transition），底部出现「轻触进入」胶囊。
 *  ④ 退场：整屏淡出（300ms），不做碎片四散（主流 App 均为整屏退场）。
 *  ⑤ 兜底：4s 自动进入（旧版 6s 偏慢，主流 App 1.5-3s）；500ms 防误触。
 */
@Composable
fun SplashOverlay(
    onFinished: () -> Unit,
    /** 真实加载进度 0..100（33 = 本地会话判据完成，100 = 首屏数据就绪） */
    progress: Int,
    ready: Boolean,
    statusText: String,
    version: String = "v1.0.0",
) {
    val t = LocalBusTokens.current
    var shownAt by remember { mutableStateOf(System.currentTimeMillis()) }
    var dismissing by remember { mutableStateOf(false) }
    val veil = remember { Animatable(1f) }

    // 入场进度（0→1）：驱动徽标缩放 + 淡入
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        shownAt = System.currentTimeMillis()
        entrance.animateTo(1f, tween(500, easing = LinearOutSlowInEasing))
    }

    // 徽标呼吸脉动（就绪后启用）
    val infiniteTransition = rememberInfiniteTransition(label = "splash-pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    // 4s 兜底自动进入（对标主流 App 1.5-3s 区间，取 4s 留余量给弱网）
    LaunchedEffect(Unit) {
        delay(4000)
        if (!dismissing) dismissing = true
    }

    // 就绪 + 入场完成 → 允许点击进入
    LaunchedEffect(dismissing) {
        if (!dismissing) return@LaunchedEffect
        veil.animateTo(0f, tween(300, easing = LinearOutSlowInEasing))
        onFinished()
    }

    // 入场 stagger：徽标 0ms → 名称 150ms → slogan 280ms → 进度条 380ms
    val logoAlpha = (entrance.value * 2.5f).coerceIn(0f, 1f)
    val logoScale = 0.85f + 0.15f * entrance.value
    val nameAlpha = ((entrance.value - 0.15f) * 2.5f).coerceIn(0f, 1f)
    val sloganAlpha = ((entrance.value - 0.28f) * 2.5f).coerceIn(0f, 1f)
    val barAlpha = ((entrance.value - 0.38f) * 2.8f).coerceIn(0f, 1f)

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = veil.value }
            .background(
                Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to t.accent,
                        0.25f to t.primary,
                        1f to t.primaryDark,
                    ),
                    center = Offset(0.5f, 0.15f),
                    radius = 1200f,
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                if (System.currentTimeMillis() - shownAt < 500) return@clickable
                if (ready && !dismissing) dismissing = true
            },
        contentAlignment = Alignment.Center,
    ) {
        // 品牌柔光（顶部大面积光晕）
        Box(
            Modifier
                .size(500.dp)
                .align(Alignment.TopCenter)
                .graphicsLayer { alpha = 0.6f * logoAlpha }
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colorStops = arrayOf(
                                0f to Color.White.copy(alpha = 0.15f),
                                0.5f to Color.White.copy(alpha = 0.05f),
                                1f to Color.Transparent,
                            ),
                        ),
                        radius = size.minDimension / 2,
                    )
                },
        )

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(120.dp))

            // 徽标盘面：白底 + 稻谷金细环 + 原图插画
            Box(
                Modifier
                    .size(96.dp)
                    .graphicsLayer {
                        alpha = logoAlpha
                        scaleX = logoScale * if (ready) pulseScale else 1f
                        scaleY = logoScale * if (ready) pulseScale else 1f
                    }
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(2.5.dp, t.gold, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = coil.compose.rememberAsyncImagePainter(model = LOGO_ASSET),
                    contentDescription = "乡邮智联",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(82.dp),
                )
            }

            // 品牌名称
            Text(
                "乡邮智联",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 6.sp,
                modifier = Modifier
                    .padding(top = 28.dp)
                    .graphicsLayer { alpha = nameAlpha },
            )

            // Slogan
            Text(
                "公交带货 · 快递进村 · 农产品出山",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = FontFamily.Serif,
                letterSpacing = 2.sp,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .graphicsLayer { alpha = sloganAlpha },
            )

            // 进度条
            Box(
                Modifier
                    .padding(top = 40.dp)
                    .width(120.dp)
                    .height(2.5.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(Color.White.copy(alpha = 0.20f))
                    .graphicsLayer { alpha = barAlpha },
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.coerceIn(0, 100) / 100f)
                        .height(2.5.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(Color.White),
                )
            }

            // 状态文案 / 就绪胶囊
            Box(
                Modifier
                    .padding(top = 24.dp)
                    .graphicsLayer { alpha = barAlpha },
            ) {
                if (ready) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(BusRadius.pill))
                            .background(Color.White.copy(alpha = 0.18f))
                            .border(1.dp, Color.White.copy(alpha = 0.30f), RoundedCornerShape(BusRadius.pill))
                            .padding(horizontal = 18.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            "轻触屏幕进入",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.sp,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("›", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text(
                        statusText,
                        color = Color.White.copy(alpha = 0.80f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        // 版本号（底部）
        Text(
            version,
            color = Color.White.copy(alpha = 0.60f),
            fontSize = 11.sp,
            letterSpacing = 1.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .graphicsLayer { alpha = barAlpha },
        )
    }
}

/**
 * 品牌 Logo：assets/logo.png —— 与小程序 images/logo.png 同一张定稿原图
 * （用户原图，不重绘 / 不复刻 / 不抠底；原图白底与白色盘面同底无缝）。
 */
private const val LOGO_ASSET = "file:///android_asset/logo.png"
