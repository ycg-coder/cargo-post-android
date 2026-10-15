package com.shanxiang.buspost.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 一枚图标返回键（无文字，命中 ≥ [LocalBusTouch] 下限），[Signpost] 的箭头与自带页头的屏共用这一份。
 * 单独开出来是为了 #80 那条规矩：每个整屏路由都要有**看得见**的退出口 —— 各屏自己画会画歪，
 * 歪掉的那一格恰好是用户唯一能按的出口。
 */
@Composable
fun BusBackArrow(onBack: () -> Unit, tint: Color = Color.White, modifier: Modifier = Modifier) {
    val touch = LocalBusTouch.current
    Box(
        modifier
            .defaultMinSize(touch.min, touch.min)
            .clip(CircleShape)
            .clickable(onClickLabel = "返回", onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Image(
            imageVector = BusIcons.back,
            contentDescription = "返回",
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(tint),
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * 站牌头：主题深板渐变 + 顶部落款横档 + 角落光晕，等价 app.wxss 的 .signpost。
 * 页面顶部通栏与卡片内均复用本原语。
 *
 * [onBack] 非空时在标题前挂一枚返回箭头（与 [BusTopBar] 同一形状、同一 `BusIcons.back`）。
 * 为什么箭头长在这里而不是叫屏再叠一条 `BackTopBar`：全屏路由（货物交接 / 寄货）的第一格
 * 就是这块站牌头，再加一条顶栏等于两行标题压在一起，白吃掉一屏里最贵的那 56dp。
 * 壳内宿主（首页 / 商城 / 工作台）本来就有底部标签栏可退，不传这个参数就不会长出箭头。
 */
@Composable
fun Signpost(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    badge: String? = null,
    compact: Boolean = false,
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val t = LocalBusTokens.current
    val shape = RoundedCornerShape(BusRadius.signpost)
    Box(
        modifier = modifier
            .fillMaxWidth()
            // Edge-to-Edge（2026-10-09）：targetSdk 36 起系统强制全屏，Signpost 是渐变卡片，
            // 在全屏路由（调度消息 / 货物交接等）里作为页面第一格，会从 y=0 画起、顶进状态栏。
            // 壳内宿主（首页/商城/工作台）的 insets 已被 BusScaffold 的 consumeWindowInsets 消费掉，
            // 这里恒为 0 —— 同一个原语两种宿主都正确，不用传参区分。
            .statusBarsPadding()
            .background(Brush.linearGradient(listOf(t.primaryDark, t.primary)), shape)
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(0f to Color.White.copy(alpha = 0.12f), 0.7f to Color.Transparent),
                        center = Offset(size.width * 0.92f, -size.height * 0.15f),
                        radius = size.width * 0.5f,
                    )
                )
            }
    ) {
        // 横档必须在正文之外：小程序 .signpost::before 是 top:8rpx、正文 padding-top:30rpx，
        // 两者本不重叠；早先横档写在已带 padding 的 Box 里，就被压到了标题行上
        // （设备实拍 crop-home-a 量到卡顶下 20dp 处一条贯穿标题与徽章的亮线）。
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(start = 12.dp, end = 12.dp, top = 4.dp)
                .fillMaxWidth()
                .height(2.5.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.28f))
        )
        Column(
            Modifier.padding(
                start = 16.dp, end = 16.dp,
                top = if (compact) 12.dp else 16.dp,
                bottom = if (compact) 12.dp else 14.dp,
            )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) BusBackArrow(onBack = onBack)
                Text(
                    title,
                    color = Color.White,
                    fontSize = if (compact) 17.sp else 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = if (onBack != null) 4.dp else 0.dp).weight(1f, fill = false),
                )
                if (badge != null) {
                    Box(
                        Modifier
                            .padding(start = 8.dp)
                            .clip(RoundedCornerShape(BusRadius.s))
                            .background(Color.White.copy(alpha = 0.16f))
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Text(badge, color = Color.White, fontSize = 11.sp, letterSpacing = 0.5.sp)
                    }
                }
            }
            if (subtitle != null) {
                Text(
                    subtitle,
                    color = Color.White.copy(alpha = 0.84f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            content()
        }
    }
}

/**
 * 车票卡：主券 + 右侧副券，撕票虚线两端打孔，等价 .ticket-card。
 * stubValue/stubLabel 为副券大字与小标签（ETA、票价、检票口等）。
 */
@Composable
fun TicketCard(
    modifier: Modifier = Modifier,
    serial: String? = null,
    stubValue: String? = null,
    stubLabel: String? = null,
    stubColor: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val t = LocalBusTokens.current
    val shape = RoundedCornerShape(BusRadius.l)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val lift by animateFloatAsState(
        targetValue = if (pressed) 0.992f else 1f,
        animationSpec = BusMotion.enter(BusMotion.PRESS_MS),
        label = "ticketPress",
    )
    val pressModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interaction,
            indication = LocalIndication.current,
            onClick = onClick,
        )
    } else Modifier

    // IntrinsicSize.Max：Row 默认按子项内容高度布局，不锁成最高子项的话
    // 副券的底色只覆盖自己那几行文字，主券比它高时右侧露出一截白底（设备实拍 e2e-11 已见）
    Row(
        modifier
            .height(IntrinsicSize.Max)
            .graphicsLayer { scaleX = lift; scaleY = lift }
            .clip(shape)
            .background(t.card, shape)
            .then(pressModifier)
    ) {
        Column(Modifier.weight(1f).padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)) {
            if (serial != null) {
                Text(
                    serial,
                    color = t.textTertiary,
                    fontSize = 11.sp,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            Row(Modifier.fillMaxWidth()) { content() }
        }
        if (stubValue != null || stubLabel != null) {
            Box(
                Modifier
                    .width(92.dp)
                    .fillMaxHeight()
                    .drawBehind {
                        drawRect(t.cardWarm)
                        val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 5f), 0f)
                        drawLine(
                            color = t.lineDeep,
                            start = Offset(0f, 0f),
                            end = Offset(0f, size.height),
                            strokeWidth = 1.5f,
                            pathEffect = dash,
                        )
                    }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    if (stubValue != null) {
                        Text(
                            stubValue,
                            color = stubColor ?: t.clay,
                            // 副券只有 92dp 宽：状态类文案（"审核不通过""已揽收"）按字数降号，
                            // 否则 maxLines=1 会被裁成半个字（设备实拍 e2e-11 已见）
                            fontSize = when {
                                stubValue.length <= 3 -> 19.sp
                                stubValue.length <= 4 -> 16.sp
                                stubValue.length <= 6 -> 13.sp
                                else -> 11.5.sp
                            },
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (!stubLabel.isNullOrEmpty()) {
                        Text(
                            stubLabel,
                            color = t.textSecondary,
                            fontSize = 10.5.sp,
                            lineHeight = 13.5.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                            modifier = Modifier.padding(top = if (stubValue != null) 3.dp else 0.dp),
                        )
                    }
                }
                // 撕票口上下打孔：与页面同色的半圆缺口
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .offset(x = (-6).dp, y = (-6).dp)
                        .size(12.dp)
                        .background(t.paper, CircleShape)
                )
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = (-6).dp, y = 6.dp)
                        .size(12.dp)
                        .background(t.paper, CircleShape)
                )
            }
        }
    }
}

/** 山路线：虚线道路，等价 .road-line；progress ∈ 0..1 时已走过的部分实线 */
@Composable
fun RoadLine(modifier: Modifier = Modifier, progress: Float = -1f) {
    val t = LocalBusTokens.current
    Canvas(modifier.fillMaxWidth().height(3.dp)) {
        val y = size.height / 2
        val dash = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
        val done = progress.coerceIn(0f, 1f)
        if (progress < 0f) {
            drawLine(t.accent.copy(alpha = 0.7f), Offset(0f, y), Offset(size.width, y), size.height, pathEffect = dash)
        } else {
            drawLine(t.primary, Offset(0f, y), Offset(size.width * done, y), size.height)
            drawLine(
                t.accent.copy(alpha = 0.5f),
                Offset(size.width * done, y),
                Offset(size.width, y),
                size.height * 0.7f,
                pathEffect = dash,
            )
        }
    }
}

/**
 * 时间线站点状态。`FAILED` 对应后端 `TaskItemStatusEnum` 的 8（失败）：
 * 必须与 `DONE` 分色，否则司机会把"派送失败"看成"已送达"（审计 §18/§62）。
 */
enum class StopState { DONE, CURRENT, PENDING, FAILED }

data class TimelineStop(
    val label: String,
    val time: String? = null,
    val hint: String? = null,
    val state: StopState = StopState.PENDING,
)

/**
 * 站点时间线：竖排山路线 + 站点圆点，用于物流轨迹、班次经停、配送进度。
 */
@Composable
fun StationTimeline(
    stops: List<TimelineStop>,
    modifier: Modifier = Modifier,
) {
    val t = LocalBusTokens.current
    Column(modifier) {
        stops.forEachIndexed { i, s ->
            val isLast = i == stops.size - 1
            val lineColor = when (s.state) {
                StopState.DONE -> t.primary
                StopState.CURRENT -> t.clay
                StopState.PENDING -> t.lineDeep
                StopState.FAILED -> t.danger
            }
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Box(Modifier.width(26.dp).fillMaxHeight().drawBehind {
                    val x = 11.dp.toPx()
                    val cy = 12.dp.toPx()
                    val dash = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
                    if (i > 0) {
                        drawLine(t.lineDeep, Offset(x, 0f), Offset(x, cy), 2f, pathEffect = dash)
                    }
                    if (!isLast) {
                        drawLine(
                            if (s.state == StopState.PENDING || s.state == StopState.FAILED) t.lineDeep
                            else t.primary.copy(alpha = 0.55f),
                            Offset(x, cy + 6.dp.toPx()),
                            Offset(x, size.height),
                            2f,
                            pathEffect = if (s.state == StopState.PENDING || s.state == StopState.FAILED) dash else null,
                        )
                    }
                    val r = if (s.state == StopState.CURRENT) 6.dp.toPx() else 5.dp.toPx()
                    if (s.state != StopState.PENDING) {
                        drawCircle(lineColor, radius = r, center = Offset(x, cy))
                        if (s.state == StopState.CURRENT) {
                            drawCircle(Color.White.copy(alpha = 0.9f), radius = r * 0.42f, center = Offset(x, cy))
                        }
                    } else {
                        drawCircle(t.card, radius = r, center = Offset(x, cy))
                        drawCircle(t.lineDeep, radius = r, center = Offset(x, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
                    }
                })
                Column(Modifier.weight(1f).padding(start = 10.dp, bottom = if (isLast) 0.dp else 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            s.label,
                            color = if (s.state == StopState.PENDING) t.textSecondary else t.ink,
                            fontSize = 15.sp,
                            fontWeight = if (s.state == StopState.CURRENT) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (s.time != null) {
                            Text(
                                s.time,
                                color = t.textTertiary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                    // 空串与缺席同样处理：hint 为空时不再画一行"看不见的文字"。
                    // 设备实测（r68-15 司机工作台）经停站每行 177px 而文字只有 66px，
                    // 差额就是这一行空白 12sp 行 + 2dp 上边距 —— 18 站累积成"布局松散"。
                    if (!s.hint.isNullOrBlank()) {
                        Text(
                            s.hint,
                            color = when (s.state) {
                                StopState.CURRENT -> t.clay
                                StopState.FAILED -> t.danger
                                else -> t.textTertiary
                            },
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 骨架流光条 */
@Composable
fun SkeletonLine(modifier: Modifier = Modifier, height: Dp = 12.dp, width: Dp? = null) {
    val t = LocalBusTokens.current
    val tr = rememberInfiniteTransition(label = "skeleton")
    val slide by tr.animateFloat(-1f, 2.4f, infiniteRepeatable(tween(BusMotion.SKELETON_MS, easing = LinearEasing), RepeatMode.Restart), label = "slide")
    val sized = if (width != null) modifier.width(width) else modifier.fillMaxWidth()
    Box(
        sized
            .height(height)
            .clip(RoundedCornerShape(BusRadius.s))
            .drawBehind {
                drawRect(
                    brush = Brush.linearGradient(
                        listOf(t.skeletonA, t.skeletonB, t.skeletonA),
                        start = Offset(size.width * slide, 0f),
                        end = Offset(size.width * slide + size.width * 0.7f, size.height),
                    )
                )
            }
    )
}

/** 骨架卡：仿卡片轮廓的占位容器，首屏先出骨架再出真实内容 */
@Composable
fun SkeletonCard(modifier: Modifier = Modifier, rows: Int = 3, leading: Boolean = true) {
    val t = LocalBusTokens.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(BusRadius.card))
            .background(t.card)
            .padding(16.dp)
    ) {
        if (leading) SkeletonLine(Modifier.padding(end = 12.dp), height = 48.dp, width = 48.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            repeat(rows) { SkeletonLine(height = if (it == 0) 14.dp else 11.dp, width = if (it == rows - 1) 120.dp else null) }
        }
    }
}

/** 列表项阶梯入场：等价 .anim-rise + inline animation-delay */
@Composable
fun AnimatedEnter(index: Int = 0, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (index > 0) kotlinx.coroutines.delay((index.coerceAtMost(8) * 45).toLong())
        visible = true
    }
    // ⚠ modifier 落在 AnimatedVisibility **这一层**：它是父 Row/Column 的直接子节点。
    //   以前只有 index/content 两个参数，调用方把 `Modifier.weight(1f)` 传进 content 里的子 Column 上，
    //   weight 就落在孙节点 ⇒ 等于没落（首页四宫格因此在平板上左堆成 55dp 宽，2026-10-10 真机 bounds 量到）。
    AnimatedVisibility(
        modifier = modifier,
        visible = visible,
        enter = slideInVertically(BusMotion.enter(320)) { it / 5 } + fadeIn(BusMotion.enter(240)),
        exit = fadeOut(),
    ) { content() }
}

/**
 * 下拉刷新容器：等价小程序的 onPullDownRefresh + wx.stopPullDownRefresh()。
 * refreshing 由调用方持有（true 期间显示指示器），结束时置回 false。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BusPullRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = modifier,
    ) { content() }
}

/**
 * 状态筛选胶囊条：等价小程序 .order-tabs / .order-tab（吸顶横向滚动、选中抬为实板）。
 */
@Composable
fun FilterChipRow(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    androidx.compose.foundation.lazy.LazyRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(options) { i, label ->
            val active = i == selected
            val chipShape = RoundedCornerShape(BusRadius.btn)
            val chipSrc = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .pressFeedback(chipSrc)
                    .clip(chipShape)
                    .background(if (active) t.primary else t.card)
                    .then(if (!active) Modifier.border(1.dp, t.line, chipShape) else Modifier)
                    .clickable(interactionSource = chipSrc, indication = LocalIndication.current) { onSelect(i) }
                    .defaultMinSize(minHeight = LocalBusTouch.current.min)
                    .padding(horizontal = 16.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (active) t.onPrimary else t.textSecondary,
                    fontSize = (13 * scale).sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

/** 「没有更多了」尾注：等价 .order-nomore / .list-nomore */
@Composable
fun ListEndNote(modifier: Modifier = Modifier, text: String = "没有更多了") {
    val t = LocalBusTokens.current
    Text(
        text,
        color = t.textTertiary,
        fontSize = 12.sp,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = modifier.fillMaxWidth().padding(vertical = 14.dp),
    )
}
