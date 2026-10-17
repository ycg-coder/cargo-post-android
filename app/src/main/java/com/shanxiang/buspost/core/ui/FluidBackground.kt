package com.shanxiang.buspost.core.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 流体云背景：三层动画渐变网格，营造有机流动感（对标 Apple Music / Spotify 2026 背景）。
 *
 * 性能：单次 rememberInfiniteTransition 驱动三个相位偏移，GPU 友好。
 * 深色模式：降低 alpha，避免过亮。
 */
@Composable
fun FluidBackground(modifier: Modifier = Modifier) {
    val t = LocalBusTokens.current
    val tr = rememberInfiniteTransition(label = "fluid")

    // 三个相位偏移，速度不同 → 有机流动感
    val phase1 by tr.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "p1",
    )
    val phase2 by tr.animateFloat(
        initialValue = 0.33f,
        targetValue = 1.33f,
        animationSpec = infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(25000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "p2",
    )
    val phase3 by tr.animateFloat(
        initialValue = 0.66f,
        targetValue = 1.66f,
        animationSpec = infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(30000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "p3",
    )

    // 深色模式降低 alpha，避免过亮
    val alphaMultiplier = if (t.isDark) 0.5f else 1f

    Box(
        modifier
            .fillMaxSize()
            .background(t.paper)
            .drawBehind {
                val w = size.width
                val h = size.height

                // 层 1：主色大渐变（左上 → 右下流动）
                val x1 = w * (0.3f + 0.4f * phase1)
                val y1 = h * (0.2f + 0.3f * phase1)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(t.primary.copy(alpha = 0.08f * alphaMultiplier), Color.Transparent),
                        center = Offset(x1, y1),
                        radius = w * 0.6f,
                    ),
                )

                // 层 2：强调色小渐变（右上 → 左下流动，相位偏移）
                val x2 = w * (0.7f - 0.3f * phase2)
                val y2 = h * (0.6f + 0.2f * phase2)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(t.accent.copy(alpha = 0.12f * alphaMultiplier), Color.Transparent),
                        center = Offset(x2, y2),
                        radius = w * 0.5f,
                    ),
                    blendMode = BlendMode.Overlay,
                )

                // 层 3：陶土色底部渐变（左下 → 右上流动）
                val x3 = w * (0.2f + 0.5f * phase3)
                val y3 = h * (0.8f - 0.3f * phase3)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(t.clay.copy(alpha = 0.06f * alphaMultiplier), Color.Transparent),
                        center = Offset(x3, y3),
                        radius = w * 0.55f,
                    ),
                    blendMode = BlendMode.Overlay,
                )
            },
    )
}

/**
 * 玻璃拟态修饰符：半透明渐变 + 描边 + 阴影（对标 iOS / Material You 2026 玻璃效果）。
 *
 * 为什么不用 RenderEffect.createBlurEffect：需要 API 31+，且只能模糊自身层内内容，
 * 无法模糊背后的滚动内容（除非用复杂的 Bitmap 缓存，性能代价高）。
 * 半透明 + 渐变 + 描边的组合在视觉上已足够接近玻璃感，且全 API 兼容。
 */
fun Modifier.glassmorphic(
    tokens: BusTokens,
    shape: RoundedCornerShape = RoundedCornerShape(0.dp),
    shadowElevation: Dp = 4.dp,
    tint: Color = tokens.card,
): Modifier = this
    .shadow(shadowElevation, shape)
    .drawBehind {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    tint.copy(alpha = if (tokens.isDark) 0.88f else 0.93f),
                    tint.copy(alpha = if (tokens.isDark) 0.80f else 0.88f),
                ),
            ),
        )
    }
    .clip(shape)
    .border(
        width = 0.5.dp,
        color = if (tokens.isDark) tokens.line else Color.White.copy(alpha = 0.25f),
        shape = shape,
    )

/**
 * 玻璃的那一道**边缘高光**：只在顶边给一条"中间亮、两端隐"的亮线，到底部 fade 成透明。
 *
 * 为什么单列一个 modifier 而不是把 alpha 调高：2026 那一档玻璃感靠的不是透明度，是**边缘**——
 * 同一块半透明面，有一条亮边就读得出"厚度"，没有就只是一张褪色的纸。
 * 反过来说它也不能代替对比度：这条线只有 1dp，正文颜色与表面色一律不动。
 *
 * 深色模式压到 0.16f：白线在深底上等于一条划痕，浅底上才是玻璃。
 */
fun Modifier.glassSheen(tokens: BusTokens, shape: Shape = RoundedCornerShape(0.dp)): Modifier =
    this.border(
        width = 1.dp,
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = if (tokens.isDark) 0.16f else 0.80f),
                Color.Transparent,
            ),
        ),
        shape = shape,
    )

/**
 * 通用按压缩放反馈：按下时缩至 0.97，松开回弹。
 *
 * 用法：放在 `.clickable(...)` **之前**，并把同一个 [src] 传给 `.clickable`，
 * 让两者共享按压状态。不传 src 时内部自建 —— 适用于该元素只有 pressFeedback 一处消费交互的场景。
 *
 * 为什么收口成一处：全工程约 65 个 `.clickable`，只有 4 个有按压动效；
 * 统一 modifier 后，任何组件加一行 `.pressFeedback(src)` 即可吃到一致的触感。
 */
fun Modifier.pressFeedback(src: MutableInteractionSource? = null): Modifier = composed {
    val interaction = src ?: remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = BusMotion.enter(BusMotion.PRESS_MS),
        label = "pressFb",
    )
    this.graphicsLayer { scaleX = scale; scaleY = scale }
}
