package com.shanxiang.buspost.core.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 间距刻度：4dp 基准，等价小程序 rpx/2（375dp 宽设备） */
object BusSpace {
    val xs = 2.dp
    val s = 4.dp
    val m = 8.dp
    val l = 12.dp
    val xl = 16.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    val page = 40.dp
    val section = 48.dp
}

/**
 * 圆角阶梯（2026 对标主流 App）：
 * - card 16dp：货拉拉/滴滴货运/顺丰均用 16-20dp 卡片圆角
 * - btn 12dp：主流 App 按钮 12-16dp，告别 2020 时代的 6dp 方角
 * - field 12dp：输入框与按钮同口径
 * - signpost 16dp：页头大卡片与 card 对齐
 */
object BusRadius {
    val s = 6.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 20.dp
    val pill = 100.dp
    val card = 16.dp
    val signpost = 16.dp
    val field = 12.dp
    val btn = 12.dp
    val dialog = 28.dp
}

/**
 * 卡片悬浮高度（dp）。2026 主流做法：轻阴影 + 表面色调分层（tonal elevation）。
 * 三档：静态卡 [card]、可点卡 [raised]、按下 [pressed]。
 * 深色模式下阴影几乎不可见，BusCard 改用 1dp 描边做同一件事。
 */
object BusElevation {
    val none = 0.dp
    val card = 3.dp
    val raised = 6.dp
    val pressed = 0.dp
    val floating = 10.dp
}

/**
 * 触控目标下限。这不是审美偏好，是**可达性硬下限**：
 * WCAG 2.5.5 / Material 都要求可点区域 ≥ 48dp，而司机端还要叠加"单手、戴手套、车在动"三个减分项，
 * 所以驾驶模式再抬一档到 56dp。
 *
 * 用法：**视觉尺寸可以更小，但命中区必须撑到 [min]**（`Modifier.defaultMinSize(minHeight = ...)`），
 * 不要用 `size()` 把按钮钉死在 32dp —— 那正是改造前 `BusButton(small = true)` 的老毛病（实测约 33dp）。
 */
data class BusTouch(val min: Dp = 48.dp, val gap: Dp = 8.dp) {
    companion object {
        val Default = BusTouch()
        /** 驾驶模式：56dp 触区 + 更宽的按钮间距（颠簸时防止误触隔壁那颗） */
        val Driving = BusTouch(min = 56.dp, gap = 12.dp)
    }
}

/** 动效统一缓动 + 时长（对标 2026 主流 App 微交互） */
object BusMotion {
    /** 通用缓动：Material 3 Emphasized 曲线 */
    val easing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)
    /** 减速入场：元素出现时用 */
    val decelerate = CubicBezierEasing(0f, 0f, 0f, 1f)
    /** 加速离场：元素消失时用 */
    val accelerate = CubicBezierEasing(0.3f, 0f, 1f, 1f)

    fun <T> enter(durationMs: Int = 300) = tween<T>(durationMillis = durationMs, easing = easing)
    fun <T> exit(durationMs: Int = 200) = tween<T>(durationMillis = durationMs, easing = accelerate)

    const val PAGE_MS = 250
    const val PRESS_MS = 100
    const val SKELETON_MS = 1000
    const val SHEET_MS = 300
    const val TOGGLE_MS = 180
}

/** 字号阶梯：caption 仅用于票号/单位/时间戳等非关键信息 */
fun busTypography(scale: Float, ink: Color): Typography {
    val family = FontFamily.Default
    fun ts(weight: FontWeight, size: Int, line: Int) = TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = (size * scale).sp,
        lineHeight = (line * scale).sp,
        color = ink,
    )
    return Typography(
        displayLarge = ts(FontWeight.Bold, 28, 36),
        displaySmall = ts(FontWeight.Bold, 22, 28),
        headlineMedium = ts(FontWeight.Bold, 20, 26),
        headlineSmall = ts(FontWeight.Bold, 18, 24),
        titleLarge = ts(FontWeight.Bold, 16, 22),
        titleMedium = ts(FontWeight.SemiBold, 15, 20),
        titleSmall = ts(FontWeight.SemiBold, 14, 19),
        bodyLarge = ts(FontWeight.Normal, 16, 24),
        bodyMedium = ts(FontWeight.Normal, 14, 21),
        bodySmall = ts(FontWeight.Normal, 13, 19),
        labelLarge = ts(FontWeight.SemiBold, 16, 20),
        labelMedium = ts(FontWeight.Medium, 13, 17),
        labelSmall = ts(FontWeight.Medium, 12, 16),
    )
}

private val busShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(BusRadius.s),
    medium = RoundedCornerShape(BusRadius.m),
    large = RoundedCornerShape(BusRadius.l),
    extraLarge = RoundedCornerShape(BusRadius.xl),
)

/**
 * 全站唯一主题入口：四套主题 × 深色模式 × 老年模式 × **驾驶模式**。
 *
 * [driving]（司机端一等公民，用户 2026-10-09 选定）一次打开三件事，因为它们服务同一个场景「人在开车」：
 * ① 字号 ×1.2（比老年模式再大一档，行车中扫一眼就要看清）；
 * ② 触区抬到 [BusTouch.Driving]（56dp），配合颠簸路面；
 * ③ **界面转夜间暗色** —— 复用已有的暗色令牌，不新造一套配色（那套是过了对比度审计的）。
 * 驾驶模式优先于老年模式：两者都开时按 1.2 走，不叠加（叠加会溢出单行标签）。
 */
@Composable
fun BusAppTheme(
    theme: BusTheme = BusTheme.BLUE,
    dark: Boolean = false,
    elderly: Boolean = false,
    driving: Boolean = false,
    content: @Composable () -> Unit,
) {
    val nightUi = dark || driving
    val tokens = remember(theme, nightUi) { busTokens(theme, nightUi) }
    val scale = when {
        driving -> 1.2f
        elderly -> 1.125f
        else -> 1f
    }
    val touch = remember(driving) { if (driving) BusTouch.Driving else BusTouch.Default }
    val typography = remember(scale, tokens.ink) { busTypography(scale, tokens.ink) }
    val scheme = remember(tokens, nightUi) {
        if (!nightUi) {
            lightColorScheme(
                primary = tokens.primaryOnLight,
                onPrimary = tokens.onPrimary,
                primaryContainer = tokens.primaryLight,
                onPrimaryContainer = tokens.primaryOnLight,
                inversePrimary = tokens.accent,
                secondary = tokens.goldDeep,
                onSecondary = tokens.onPrimary,
                secondaryContainer = tokens.goldLight,
                onSecondaryContainer = tokens.goldDeep,
                tertiary = tokens.clayDeep,
                onTertiary = tokens.card,
                tertiaryContainer = tokens.clayLight,
                onTertiaryContainer = tokens.clayDeep,
                error = tokens.danger,
                onError = tokens.card,
                errorContainer = tokens.clayLight,
                onErrorContainer = tokens.danger,
                background = tokens.paper,
                onBackground = tokens.ink,
                surface = tokens.card,
                onSurface = tokens.ink,
                surfaceVariant = tokens.track,
                onSurfaceVariant = tokens.textSecondary,
                outline = tokens.line,
                outlineVariant = tokens.lineDeep,
            )
        } else {
            darkColorScheme(
                primary = tokens.primaryOnLight,
                onPrimary = tokens.ink,
                primaryContainer = tokens.primaryLight,
                onPrimaryContainer = tokens.primaryOnLight,
                inversePrimary = tokens.accent,
                secondary = tokens.goldDeep,
                onSecondary = tokens.paper,
                secondaryContainer = tokens.goldLight,
                onSecondaryContainer = tokens.goldDeep,
                tertiary = tokens.clayDeep,
                onTertiary = tokens.paper,
                tertiaryContainer = tokens.clayLight,
                onTertiaryContainer = tokens.clayDeep,
                error = tokens.danger,
                onError = tokens.paper,
                errorContainer = tokens.clayLight,
                onErrorContainer = tokens.danger,
                background = tokens.paper,
                onBackground = tokens.ink,
                surface = tokens.card,
                onSurface = tokens.ink,
                surfaceVariant = tokens.track,
                onSurfaceVariant = tokens.textSecondary,
                outline = tokens.line,
                outlineVariant = tokens.lineDeep,
            )
        }
    }
    CompositionLocalProvider(
        LocalBusTokens provides tokens,
        LocalBusFontScale provides scale,
        LocalBusTouch provides touch,
        LocalBusDriving provides driving,
    ) {
        MaterialTheme(colorScheme = scheme, shapes = busShapes, typography = typography, content = content)
    }
}

/** 常用文字样式简写 */
object BusText {
    @Composable
    fun caption() = MaterialTheme.typography.labelSmall.copy(color = LocalBusTokens.current.textTertiary)

    @Composable
    fun secondary() = MaterialTheme.typography.bodySmall.copy(color = LocalBusTokens.current.textSecondary)
}
