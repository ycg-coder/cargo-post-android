package com.shanxiang.buspost.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/** 站牌式按钮：实板 + 顶部浅色落款横档，等价 .btn-primary / .btn-orange */
enum class BusButtonVariant { PRIMARY, CLAY, GOLD, OUTLINE, GHOST, DANGER, TEXT }

@Composable
fun BusButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: BusButtonVariant = BusButtonVariant.PRIMARY,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    fullWidth: Boolean = true,
    small: Boolean = false,
) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    // 触控下限：small 按钮改造前实测约 33dp（7dp 上下留白 + 14sp 字），司机单手/颠簸时按不中。
    // 视觉仍按 small 的紧凑排布，但**命中区**用 defaultMinSize 撑到令牌下限（普通 48dp / 驾驶 56dp）。
    val touch = LocalBusTouch.current
    val filled = variant == BusButtonVariant.PRIMARY || variant == BusButtonVariant.CLAY ||
        variant == BusButtonVariant.GOLD || variant == BusButtonVariant.DANGER
    val board = if (!enabled) t.track else when (variant) {
        BusButtonVariant.PRIMARY -> t.primary
        BusButtonVariant.CLAY -> t.clay
        BusButtonVariant.GOLD -> t.goldDeep
        BusButtonVariant.DANGER -> t.danger
        else -> t.card
    }
    // 不可用不能只靠"把整块板调淡"：45% 白字落在 45% 蓝板上，字与板实测只差 1.37:1，等于把按钮名字抹掉。
    // 改成灰板 + 正常灰字（实测 5.9–6.1:1），loading 保持实色板（它是"正在办"，不是"不能办"）。
    val onBoard = when {
        !enabled -> t.textSecondary
        filled -> t.onPrimary
        else -> t.primaryOnLight
    }
    val outlined = variant == BusButtonVariant.OUTLINE || variant == BusButtonVariant.GHOST
    val pressed = remember { MutableInteractionSource() }
    val isPressed by pressed.collectIsPressedAsState()
    val dimmed = !enabled || loading
    val shape = RoundedCornerShape(BusRadius.btn)
    Box(
        modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .clip(shape)
            .background(if (filled) board else Color.Transparent, shape)
            .then(
                if (outlined) {
                    Modifier.border(1.dp, if (!enabled) t.line else t.lineDeep, shape)
                } else Modifier
            )
            .then(if (isPressed && !dimmed) Modifier.background(Color.Black.copy(alpha = 0.08f)) else Modifier)
            .clickable(
                interactionSource = pressed,
                indication = LocalIndication.current,
                enabled = !dimmed,
                onClick = onClick,
            )
            // 命中区下限在 padding **之前**：先撑到 48/56dp，再按 small/普通加留白，
            // 顺序反了就是"看着有 56dp、实际只有文字那一行能点"
            .defaultMinSize(minHeight = touch.min)
            .padding(
                horizontal = if (small) 12.dp else 18.dp,
                vertical = if (small) (7 * scale).dp else (13 * scale).dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (loading) {
                CircularProgressIndicator(
                    Modifier.size(if (small) 14.dp else 18.dp),
                    strokeWidth = 2.dp,
                    color = onBoard,
                )
                Spacer(Modifier.width(8.dp))
            } else if (icon != null) {
                androidx.compose.foundation.Image(
                    imageVector = icon,
                    contentDescription = null,
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(onBoard),
                    modifier = Modifier.size(if (small) 16.dp else 20.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text,
                color = onBoard,
                fontSize = ((if (small) 14 else 16) * scale).sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
            )
        }
    }
}

/** 主券式卡片：等价 .card 表面色 + 圆角 + 阴影；可点击时带按压下沉 */
@Composable
fun BusCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    warm: Boolean = false,
    border: Boolean = false,
    padding: PaddingValues = PaddingValues(14.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = LocalBusTokens.current
    val shape = RoundedCornerShape(BusRadius.card)
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val bg = animateColorAsState(
        if (warm) t.cardWarm else t.card,
        BusMotion.enter(160),
        label = "cardBg",
    )
    Column(
        modifier
            // 阴影要在 clip 之前：先按 shape 投影，再把内容裁成圆角，顺序反了投影会被裁掉
            .shadow(
                if (pressed) BusElevation.pressed else if (onClick != null) BusElevation.raised else BusElevation.card,
                shape,
                spotColor = if (t.isDark) Color.Black else t.primaryShadow,
                ambientColor = if (t.isDark) Color.Black else t.primaryShadow,
            )
            .clip(shape)
            .background(bg.value, shape)
            // 深色下阴影看不见，用 1dp 描边做同一件事（把卡从纸底上分出来）
            .then(if (border || t.isDark) Modifier.border(1.dp, t.line, shape) else Modifier)
            .then(if (pressed) Modifier.background(Color.Black.copy(alpha = 0.04f)) else Modifier)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick)
                } else Modifier
            )
            .padding(padding)
    ) { content() }
}

/** 输入域：米纸底 + 描边，label 在上 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String? = null,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1,
    icon: ImageVector? = null,
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    var focused by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        if (label != null) {
            Text(
                label,
                color = t.textSecondary,
                fontSize = (13 * scale).sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 5.dp, start = 2.dp),
            )
        }
        // 描边是输入框唯一的可视边界：field 底色与 paper 只差 1.02:1，没有边框时肉眼等于「没有输入框」
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(BusRadius.field))
                .background(if (error != null) t.danger.copy(alpha = 0.08f) else t.field)
                .border(
                    if (focused || error != null) 2.dp else 1.dp,
                    when {
                        error != null -> t.danger
                        focused -> t.accent
                        else -> t.lineDeep
                    },
                    RoundedCornerShape(BusRadius.field),
                )
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                androidx.compose.foundation.Image(
                    imageVector = icon,
                    contentDescription = null,
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(t.textTertiary),
                    modifier = Modifier.size(18.dp).padding(end = 8.dp),
                )
            }
            Box(Modifier.weight(1f)) {
                TextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = singleLine,
                    minLines = minLines,
                    textStyle = TextStyle(fontSize = (15 * scale).sp, color = t.ink),
                    placeholder = { Text(placeholder, color = t.textTertiary, fontSize = (15 * scale).sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        errorContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        errorIndicatorColor = Color.Transparent,
                        cursorColor = t.primary,
                        focusedTextColor = t.ink,
                        unfocusedTextColor = t.ink,
                        disabledTextColor = t.textTertiary,
                        errorTextColor = t.danger,
                    ),
                    modifier = Modifier.fillMaxWidth().onFocusEvent { focused = it.isFocused },
                )
            }
            trailing?.invoke()
        }
        if (error != null) {
            Text(error, color = t.danger, fontSize = (12 * scale).sp, modifier = Modifier.padding(top = 4.dp, start = 2.dp))
        }
    }
}

/** 状态/属性标签：浅底 + 深字，保证对比度 */
enum class BusTone { NEUTRAL, PRIMARY, CLAY, GOLD, OK, WARN, DANGER }

/** 语义色的两处真值：浅底。页面自己画状态时用它，不要再抄一份 when。 */
fun BusTone.bg(t: BusTokens): Color = when (this) {
    BusTone.NEUTRAL -> t.track
    BusTone.PRIMARY -> t.primaryLight
    BusTone.CLAY -> t.clayLight
    BusTone.GOLD -> t.goldLight
    BusTone.OK -> t.goldLight
    BusTone.WARN -> t.goldLight
    BusTone.DANGER -> t.clayLight
}

/** 语义色的两处真值：深字。未知语义落成 NEUTRAL，不要靠颜色暗示"完成/危险"。 */
fun BusTone.fg(t: BusTokens): Color = when (this) {
    BusTone.NEUTRAL -> t.textSecondary
    BusTone.PRIMARY -> t.primaryOnLight
    BusTone.CLAY -> t.clayDeep
    BusTone.GOLD -> t.goldDeep
    BusTone.OK -> t.goldDeep
    BusTone.WARN -> t.statusWarn
    BusTone.DANGER -> t.danger
}

@Composable
fun BusTag(
    text: String,
    modifier: Modifier = Modifier,
    tone: BusTone = BusTone.NEUTRAL,
    dot: Boolean = false,
) {
    val t = LocalBusTokens.current
    val bg = tone.bg(t)
    val fg = tone.fg(t)
    Box(modifier.clip(CircleShape).background(bg).padding(horizontal = 9.dp, vertical = 3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (dot) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(fg))
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text,
                color = fg,
                fontSize = (11.5f * LocalBusFontScale.current).sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

/** 分段控件：轨道 track，选中项抬为卡片 */
@Composable
fun BusSegmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(BusRadius.l))
            .background(t.track)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEachIndexed { i, label ->
            val active = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(BusRadius.m))
                    .background(if (active) t.card else Color.Transparent)
                    .clickable { onSelect(i) }
                    .defaultMinSize(minHeight = LocalBusTouch.current.min)
                    .padding(vertical = (8 * scale).dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (active) t.primaryOnLight else t.textSecondary,
                    fontSize = (14 * scale).sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 页面顶栏：深色站牌色板，返回 + 标题 + 右侧动作 */
@Composable
fun BusTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    tonal: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    // 返回键原为 38dp 圆形热区 —— 低于 48dp 下限，且是司机端唯一"退不出页面"的出口，按不中就被困住
    val touch = LocalBusTouch.current
    Row(
        modifier
            .fillMaxWidth()
            .background(if (tonal) t.primaryDark else t.card)
            // Edge-to-Edge（2026-10-09）：targetSdk 36 起系统强制全屏，状态栏是透明的、
            // 内容从 y=0 开始画。顶栏的底色要**先**画、再把内容压到状态栏之下 ——
            // 顺序反了（先 padding 后 background）会让状态栏那一条露出窗口底色。
            // 这里收口而不让 19 个全屏路由各自补：各补一遍就是 19 个会漏的地方。
            // （在 BusScaffold 内的页面不经过本组件，其顶部避让由 Scaffold 的 insets 负责。）
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(
                Modifier
                    .defaultMinSize(touch.min, touch.min)
                    .clip(CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.Image(
                    imageVector = BusIcons.back,
                    contentDescription = "返回",
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(if (tonal) Color.White else t.ink),
                    modifier = Modifier.size(22.dp),
                )
            }
        } else {
            Spacer(Modifier.width(4.dp))
        }
        Column(Modifier.weight(1f).padding(start = if (onBack != null) 4.dp else 8.dp)) {
            Text(
                title,
                color = if (tonal) Color.White else t.ink,
                fontSize = (17 * scale).sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    color = if (tonal) Color.White.copy(alpha = 0.78f) else t.textSecondary,
                    fontSize = (12 * scale).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        actions()
    }
}

@Composable
fun SectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(3.dp, 15.dp).clip(CircleShape).background(t.clay).padding(end = 8.dp))
                Spacer(Modifier.width(6.dp))
                Text(title, color = t.ink, fontSize = (16 * scale).sp, fontWeight = FontWeight.Bold)
            }
            if (subtitle != null) {
                Text(subtitle, color = t.textTertiary, fontSize = (12 * scale).sp, modifier = Modifier.padding(top = 2.dp, start = 9.dp))
            }
        }
        if (actionText != null && onAction != null) {
            // 右侧动作（"更多/展开"）也是可点元素，同样吃触控下限：原来 4dp 上下留白 ≈ 28dp，
            // 是"司机想点更多、结果点到标题上"的那类小坑
            Text(
                actionText,
                color = t.primaryOnLight,
                fontSize = (13 * scale).sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onAction)
                    .defaultMinSize(minHeight = LocalBusTouch.current.min)
                    .padding(horizontal = 8.dp),
            )
        }
    }
}

/** 价格：统一陶土橙，不再用警示红 */
@Composable
fun PriceText(
    value: String,
    modifier: Modifier = Modifier,
    size: Int = 18,
    prefix: String = "¥",
    origin: String? = null,
) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(
            prefix + value,
            color = t.price,
            fontSize = (size * scale).sp,
            fontWeight = FontWeight.Black,
        )
        if (origin != null) {
            Text(
                prefix + origin,
                color = t.textTertiary,
                fontSize = (12 * scale).sp,
                modifier = Modifier.padding(start = 5.dp, bottom = 2.dp),
            )
        }
    }
}

/** 键值行：详情页与设置页通用 */
@Composable
fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
    icon: ImageVector? = null,
    showNext: Boolean = false,
    onClick: (() -> Unit)? = null,
    valueStyle: TextStyle? = null,
) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            // 键值行既可能是纯展示、也可能是可点行（设置项/详情跳转），统一吃到触控下限：
            // 原 11dp 上下留白 ≈ 43dp，差一口气；驾驶模式下抬到 56dp，行车中扫行不会串行。
            .defaultMinSize(minHeight = LocalBusTouch.current.min)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            androidx.compose.foundation.Image(
                imageVector = icon,
                contentDescription = null,
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(t.textTertiary),
                modifier = Modifier.size(18.dp).padding(end = 8.dp),
            )
        }
        Text(label, color = t.textSecondary, fontSize = (15 * scale).sp)
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            color = valueColor ?: t.ink,
            fontSize = (15 * scale).sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            style = valueStyle ?: TextStyle.Default,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (showNext) {
            androidx.compose.foundation.Image(
                imageVector = BusIcons.next,
                contentDescription = null,
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(t.textTertiary),
                modifier = Modifier.size(16.dp).padding(start = 4.dp),
            )
        }
    }
}

/** 空态：icon + 主副文案 + 可选按钮 */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    Column(
        modifier.fillMaxWidth().padding(vertical = 34.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(t.track),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.Image(
                    imageVector = icon,
                    contentDescription = null,
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(t.textTertiary),
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
        }
        Text(title, color = t.ink, fontSize = (15 * scale).sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Text(
                subtitle,
                color = t.textTertiary,
                fontSize = (13 * scale).sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 5.dp),
            )
        }
        if (actionText != null && onAction != null) {
            BusButton(actionText, onAction, variant = BusButtonVariant.OUTLINE, small = true, fullWidth = false, modifier = Modifier.padding(top = 14.dp))
        }
    }
}

/** 错误态：与空态分离，网络失败不冒充"暂无数据" */
@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val t = LocalBusTokens.current
    Column(modifier.fillMaxWidth().padding(vertical = 26.dp, horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(t.clayLight), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Image(
                imageVector = BusIcons.warning,
                contentDescription = null,
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(t.danger),
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(message, color = t.textSecondary, fontSize = (13.5f * LocalBusFontScale.current).sp, textAlign = TextAlign.Center)
        BusButton("重新加载", onRetry, variant = BusButtonVariant.OUTLINE, small = true, fullWidth = false, modifier = Modifier.padding(top = 14.dp))
    }
}

@Composable
fun LoadingState(label: String = "加载中…", modifier: Modifier = Modifier) {
    val t = LocalBusTokens.current
    Row(modifier.fillMaxWidth().padding(vertical = 26.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = t.primary)
        Text(" $label", color = t.textTertiary, fontSize = (13 * LocalBusFontScale.current).sp, modifier = Modifier.padding(start = 8.dp))
    }
}

/** 首屏骨架：加载 / 空 / 错误三态分离中的加载态 */
@Composable
fun SkeletonList(count: Int = 3, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(count) { SkeletonCard() }
    }
}

@Composable
fun BusSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val t = LocalBusTokens.current
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedTrackColor = t.primary,
            checkedThumbColor = t.card,
            checkedBorderColor = t.primary,
            uncheckedTrackColor = t.lineDeep,
            uncheckedThumbColor = t.card,
            uncheckedBorderColor = t.lineDeep,
        ),
    )
}

/** 数值步进器：件数、重量等 */
@Composable
fun Stepper(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    step: Double = 1.0,
    min: Double = 0.0,
    max: Double = 9999.0,
    unit: String = "",
) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val num = value.toDoubleOrNull() ?: 0.0
    // ± 原为 30dp 方块，是全项目最小的可点元素：撑到触控下限，字形仍按 scale 走不跟着撑
    val touch = LocalBusTouch.current
    fun fmt(v: Double) = if (step >= 1) v.toInt().toString() else String.format("%.2f", v)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.defaultMinSize(touch.min, touch.min).clip(RoundedCornerShape(BusRadius.m)).background(t.track)
                .clickable(enabled = num - step >= min) { onValueChange(fmt(num - step)) },
            contentAlignment = Alignment.Center,
        ) { Text("−", color = t.ink, fontSize = (17 * scale).sp, fontWeight = FontWeight.Bold) }
        Text(
            value + if (unit.isNotEmpty()) " $unit" else "",
            color = t.ink,
            fontSize = (15 * scale).sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        Box(
            Modifier.defaultMinSize(touch.min, touch.min).clip(RoundedCornerShape(BusRadius.m)).background(t.track)
                .clickable(enabled = num + step <= max) { onValueChange(fmt(num + step)) },
            contentAlignment = Alignment.Center,
        ) { Text("+", color = t.ink, fontSize = (17 * scale).sp, fontWeight = FontWeight.Bold) }
    }
}

/** 头像：网络图缺省时用姓名首字，避免空图 */
@Composable
fun BusAvatar(name: String, modifier: Modifier = Modifier, url: String? = null, size: Int = 44) {
    val t = LocalBusTokens.current
    val px = (size * LocalBusFontScale.current).dp
    Box(
        modifier.size(px).clip(CircleShape).background(t.primaryLight),
        contentAlignment = Alignment.Center,
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(px),
            )
        } else {
            Text(
                name.takeIf { it.isNotEmpty() }?.substring(0, 1) ?: "乘",
                color = t.primaryOnLight,
                fontSize = (16 * LocalBusFontScale.current).sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** 未读红点 / 计数角标 */
@Composable
fun BadgeDot(count: Int, modifier: Modifier = Modifier, color: Color = LocalBusTokens.current.danger) {
    if (count <= 0) return
    val px = if (count > 9) 18.dp else 8.dp
    Box(
        modifier
            .then(if (count > 9) Modifier.width(px).height(16.dp) else Modifier.size(px))
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        if (count > 9) {
            Text(if (count > 99) "99+" else "$count", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** 数据来源标注：真实上报 / 班次计划推算 / 本地演示，绝不伪装 */
@Composable
fun SourceTag(source: String?, modifier: Modifier = Modifier) {
    if (source.isNullOrBlank()) return
    val tone = when {
        source.contains("REAL") || source.contains("实时") -> BusTone.OK
        source.contains("SIMULATED") || source.contains("推算") || source.contains("模拟") -> BusTone.WARN
        else -> BusTone.NEUTRAL
    }
    val label = when {
        source.contains("REAL_TRANSIT") -> "现实公交"
        source.contains("REAL") -> "实时"
        // 后端的 SIMULATED 说的是"位置由班次计划推算"，不是说这条记录是演示数据（见 Models.kt 枚举文案注释）
        source.contains("SIMULATED") || source.contains("推算") -> "位置推算"
        source.contains("DEMO") -> "演示数据"
        else -> source
    }
    BusTag(label, modifier, tone = tone)
}

@Composable
fun BusDivider(modifier: Modifier = Modifier, color: Color = LocalBusTokens.current.line) {
    Box(modifier.fillMaxWidth().height(0.7.dp).background(color))
}
