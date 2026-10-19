package com.shanxiang.buspost.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 启动闪屏 —— 品牌块 + 一条「公交到站」的进度路。
 *
 * 设计契约（2026-10-19 第三次改版。前两版：整屏淡出 → 一辆车从屏底开走）：
 *  ① 底：品牌渐变 + 两团缓慢漂移的柔光 —— 与 app 内部 `FluidBackground` 同一套语言，
 *     闪屏不该长得像另一款 App 的第一屏。
 *  ② 面：徽标 → 名称 → 标语（小字两行）→ 状态/入口，整组偏上（上下留白 1 : 1.45），屏底留给那一路。
 *     这一屏**不给品牌块套玻璃**：v214 真机底片上那块磨砂面只铺到内容框、外圈留一道没人管的边，
 *     读起来像弹错层的对话框，2026-10-19 用户口径"用不了拟态就算了"，撤。
 *  ③ 进度：**这一屏没有单独的进度条** —— 屏底那辆公交本身就是进度条。它沿路面从左边开向右端的站牌，
 *     开到站牌脚下 = 100%；走过的路铺成实线，没走的还是虚线。
 *     为什么让它兼任：一根细条再加一辆车＝同一件事说两遍，而这条路把"在加载"说成了"在赶路"。
 *  ④ 就绪：车停在站牌前、前灯亮起、站牌罩一层金光 —— 三个信号一起说"到站了，可以上车"。
 *  ⑤ 退场：点一下，品牌那一块先散（180ms），车在 60ms 就起步、**快速驶过站牌**冲出右沿（460ms，加速曲线），
 *     整屏到 420ms 才开始淡，全程约 640ms。
 *  ⑥ 兜底：4s 自动进入；500ms 防误触。取数挂住时车停在半路也会被那 4 秒带走，不会把人关在门外。
 *
 * 标语「赋能城乡融合的“快递下乡、农品进城”客货邮数字化管理平台」收成**小字两行**，
 * 中间那句主张用金色 + 半粗，其余压成次要灰白：一屏只许有一个重点，而这一屏的重点是品牌名。
 * 两行是靠**显式换行**拿到的，不是靠 `maxLines = 2` 截出来的 —— 后者在系统字号开大时
 * 会把这句平台定位念成半句带省略号，截断一句给人读的定位比多一行更糟。
 */
@Composable
fun SplashOverlay(
    onFinished: () -> Unit,
    /** 真实加载进度 0..100（33 = 本地会话判据完成，100 = 首屏数据就绪）—— 也就是那辆车开到哪儿 */
    progress: Int,
    ready: Boolean,
    statusText: String,
    /**
     * 屏底那一格版本号。**没有默认值**：上一版这里是 `= "v1.0.0"`，而唯一的调用点（`BusPostRoot`）
     * 从没传过它 —— 于是闪屏在所有机器上都写着 v1.0.0，而"关于"页与更新页念的是 1.0.212。
     * 一个会过期的默认值比缺参数更坏：缺参数编译不过，过期默认值只会安静地撒谎。
     */
    version: String,
) {
    val t = LocalBusTokens.current
    var shownAt by remember { mutableStateOf(System.currentTimeMillis()) }
    var dismissing by remember { mutableStateOf(false) }
    // 退场三层各自一条时间线：车走完 → 整屏才淡，别让车在半透明里消失
    val veil = remember { Animatable(1f) }
    val brand = remember { Animatable(1f) }
    val depart = remember { Animatable(0f) }
    // 车在路上的位置：0 = 起点，1 = 站牌脚下。跟着真实进度走，不是墙钟自增
    val ride = remember { Animatable(0f) }

    // 入场进度（0→1）：驱动徽标缩放 + 各档 stagger
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        shownAt = System.currentTimeMillis()
        entrance.animateTo(1f, tween(500, easing = LinearOutSlowInEasing))
    }

    // 进度跳一格（0→33→100），车用 700ms 滑过去：车就是进度条，得有"在走"的连续性
    LaunchedEffect(progress) {
        ride.animateTo(
            progress.coerceIn(0, 100) / 100f,
            tween(700, easing = FastOutSlowInEasing),
        )
    }

    // 徽标呼吸脉动 + 背景两团柔光的漂移相位
    val tr = rememberInfiniteTransition(label = "splash-anim")
    val pulseScale by tr.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "pulse",
    )
    val drift1 by tr.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(19000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift1",
    )
    val drift2 by tr.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(tween(26000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift2",
    )

    // 4s 兜底自动进入（对标主流 App 1.5-3s 区间，取 4s 留余量给弱网）
    LaunchedEffect(Unit) {
        delay(4000)
        if (!dismissing) dismissing = true
    }

    // 就绪 + 入场完成 → 允许点击进入
    LaunchedEffect(dismissing) {
        if (!dismissing) return@LaunchedEffect
        coroutineScope {
            launch { brand.animateTo(0f, tween(180, easing = FastOutLinearInEasing)) }
            launch {
                // 60ms 而不是 120ms：真机连拍里那一帧品牌已经散了、车还没动，
                // 中间空出一格"谁都不在"的屏；起步提前一点，眼睛才接得上这股动势。
                delay(60)
                depart.animateTo(1f, tween(460, easing = CubicBezierEasing(0.5f, 0f, 0.9f, 0.28f)))
            }
            launch {
                delay(420)
                veil.animateTo(0f, tween(220, easing = LinearOutSlowInEasing))
            }
        }
        onFinished()
    }

    // 入场 stagger：徽标 0 → 名称 150ms → 标语 220ms → 路面那辆车 260ms → 状态/版本号 300ms
    val logoAlpha = (entrance.value * 2.5f).coerceIn(0f, 1f)
    val logoScale = 0.86f + 0.14f * entrance.value
    val nameAlpha = ((entrance.value - 0.15f) * 2.5f).coerceIn(0f, 1f)
    val taglineAlpha = ((entrance.value - 0.22f) * 2.5f).coerceIn(0f, 1f)
    val roadAlpha = ((entrance.value - 0.26f) * 2.5f).coerceIn(0f, 1f)
    val barAlpha = ((entrance.value - 0.30f) * 2.8f).coerceIn(0f, 1f)

    // 标语：一屏只给一个重点 —— 中间那句主张上金色半粗，其余压成次要灰白
    val tagline = remember {
        buildAnnotatedString {
            withStyle(SpanStyle(color = Color.White.copy(alpha = 0.74f))) { append("赋能城乡融合的") }
            withStyle(SpanStyle(color = SPLASH_GOLD, fontWeight = FontWeight.SemiBold)) {
                append("“快递下乡、农品进城”")
            }
            withStyle(SpanStyle(color = Color.White.copy(alpha = 0.90f))) { append("\n客货邮数字化管理平台") }
        }
    }

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
            // 两团缓慢漂移的柔光：与 app 内 `FluidBackground` 同一族"流体底"
            .drawBehind {
                brandGlow(drift1 = drift1, drift2 = drift2, accent = t.accent, alpha = 0.5f * veil.value)
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                if (System.currentTimeMillis() - shownAt < 500) return@clickable
                if (ready && !dismissing) dismissing = true
            },
        contentAlignment = Alignment.Center,
    ) {
        // 屏底那一路：站牌在右端，车本身就是进度条
        BusRoad(
            ride = ride.value,
            depart = depart.value,
            ready = ready && !dismissing,
            windowColor = t.primaryDark,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = roadAlpha },
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp)
                .graphicsLayer { alpha = brand.value }
                .offset(y = (-12.dp) * (1f - brand.value)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 上下留白 1 : 1.45 —— 品牌块整体偏上，屏底那一路才不会被挤
            Spacer(Modifier.weight(1f))

            // 徽标盘面：白底 + 稻谷金细环 + 原图插画
            Box(
                Modifier
                    .size(92.dp)
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
                    modifier = Modifier.size(78.dp),
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
                    .padding(top = 20.dp)
                    .graphicsLayer { alpha = nameAlpha },
            )

            // 平台标语：小字两行，主张那句上金
            Text(
                tagline,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Normal,
                lineHeight = 20.sp,
                letterSpacing = 0.5.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .graphicsLayer { alpha = taglineAlpha },
            )

            // 状态文案 / 就绪胶囊：**只给下限不给死高**。
            // 上一版这里钉的是 `height(34.dp)`，而胶囊自己的高度是 8+19+8=35dp ——
            // 高出一丁点就被容器**齐根切掉**（真机 v214 底片：胶囊下沿与「车」字的下半截是糊的）。
            // 固定高度原本的用途是"加载中→就绪"那一跳不把整组顶得上动，`heightIn(min=)` 同样给得住。
            Box(
                Modifier
                    .padding(top = 22.dp)
                    .heightIn(min = 44.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (ready) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(BusRadius.pill))
                            .background(Color.White.copy(alpha = 0.18f))
                            .border(
                                1.dp,
                                Color.White.copy(alpha = 0.30f),
                                RoundedCornerShape(BusRadius.pill),
                            )
                            .padding(horizontal = 18.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            "轻触上车",
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

            Spacer(Modifier.weight(1.45f))
        }

        // 版本号：整屏唯一贴底的一格（它不属于品牌面，是"这台机器上装的是哪一版"）
        Text(
            version,
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 11.sp,
            letterSpacing = 1.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 26.dp)
                .graphicsLayer { alpha = barAlpha },
        )
    }
}

/**
 * 屏底那一路 —— 公交兼任进度条。
 *
 * `ride` 0..1 = 车从左端起点开到右端站牌脚下的位置（就是真实加载进度）；
 * `depart` 0..1 = 点过之后车**冲过站牌**出右沿。两个数各自独立，所以取数没走完就被 4s 兜底
 * 带走时，车也是从它当下待着的那一点起步，不会先跳一格再走。
 */
@Composable
private fun BusRoad(
    ride: Float,
    depart: Float,
    ready: Boolean,
    windowColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val r = ride.coerceIn(0f, 1f)
        val d = depart.coerceIn(0f, 1f)
        val trackY = size.height * 0.86f
        val busW = 78.dp.toPx()
        val busH = 28.dp.toPx()
        val wheelR = 6.5.dp.toPx()
        val headW = 28.dp.toPx()
        val headH = 19.dp.toPx()
        val signX = size.width - 42.dp.toPx()
        val headLeft = signX - headW / 2f
        val startX = 26.dp.toPx()
        val stopX = headLeft - 8.dp.toPx() - busW
        val exitX = size.width + busW * 0.5f
        val x = startX + (stopX - startX) * r + (exitX - stopX) * d
        val light = if (ready) 0.5f else 0.15f + 0.85f * d
        val arrived = r >= 0.995f && d <= 0.001f

        // 没走过的路：虚线槽；走过的路：被车铺成实线。这一对就是进度条的两半
        val aheadEnd = headLeft - 4.dp.toPx()
        if (aheadEnd > x + busW) {
            drawLine(
                color = Color.White.copy(alpha = 0.16f),
                start = Offset(x + busW, trackY),
                end = Offset(aheadEnd, trackY),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 9.dp.toPx()), 0f),
            )
        }
        drawLine(
            color = Color.White.copy(alpha = 0.72f),
            start = Offset(startX - 6.dp.toPx(), trackY),
            end = Offset(x + busW * 0.5f, trackY),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round,
        )

        // 站牌：杆 + 底座 + 带金顶条的白牌面 + 牌面上一辆迷你公交。
        // 到站那一层光给**实心 + 一圈环**两层，比平涂一个圆更读得出"停稳了"。
        val headTop = trackY - 48.dp.toPx()
        val headCy = headTop + headH / 2f
        val halo = if (arrived) 1f else (1f - d)
        if (arrived || d > 0f) {
            drawCircle(
                color = SPLASH_GOLD.copy(alpha = 0.14f * halo),
                radius = 30.dp.toPx(),
                center = Offset(signX, headCy),
            )
            drawCircle(
                color = SPLASH_GOLD.copy(alpha = 0.38f * halo),
                radius = 30.dp.toPx(),
                center = Offset(signX, headCy),
                style = Stroke(width = 1.2.dp.toPx()),
            )
        }
        // 杆与底座（底座那一段给得比杆宽，站牌才像是插在地上而不是浮着）
        drawLine(
            color = Color.White.copy(alpha = 0.52f),
            start = Offset(signX, headTop + headH),
            end = Offset(signX, trackY - 1.5.dp.toPx()),
            strokeWidth = 2.5.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color.White.copy(alpha = 0.42f),
            start = Offset(signX - 6.5.dp.toPx(), trackY),
            end = Offset(signX + 6.5.dp.toPx(), trackY),
            strokeWidth = 2.5.dp.toPx(),
            cap = StrokeCap.Round,
        )
        // 牌面
        drawRoundRect(
            color = Color.White.copy(alpha = 0.95f),
            topLeft = Offset(headLeft, headTop),
            size = Size(headW, headH),
            cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
        )
        // 顶上一条金带：站牌这一格靠它跟"一块白卡片"分开
        drawLine(
            color = SPLASH_GOLD.copy(alpha = 0.95f),
            start = Offset(headLeft + 5.dp.toPx(), headTop + 3.dp.toPx()),
            end = Offset(headLeft + headW - 5.dp.toPx(), headTop + 3.dp.toPx()),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round,
        )
        // 牌面上的迷你公交：车身 + 一道车窗 + 两只轮，尺寸小到只能给剪影
        val mbLeft = headLeft + 6.dp.toPx()
        val mbTop = headTop + 8.dp.toPx()
        val mbW = headW - 12.dp.toPx()
        drawRoundRect(
            color = windowColor.copy(alpha = 0.92f),
            topLeft = Offset(mbLeft, mbTop),
            size = Size(mbW, 6.5.dp.toPx()),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
        )
        drawLine(
            color = Color.White.copy(alpha = 0.9f),
            start = Offset(mbLeft + 2.dp.toPx(), mbTop + 2.6.dp.toPx()),
            end = Offset(mbLeft + mbW - 2.dp.toPx(), mbTop + 2.6.dp.toPx()),
            strokeWidth = 1.1.dp.toPx(),
        )
        drawCircle(
            color = windowColor.copy(alpha = 0.92f),
            radius = 1.4.dp.toPx(),
            center = Offset(mbLeft + 3.5.dp.toPx(), mbTop + 7.4.dp.toPx()),
        )
        drawCircle(
            color = windowColor.copy(alpha = 0.92f),
            radius = 1.4.dp.toPx(),
            center = Offset(mbLeft + mbW - 3.5.dp.toPx(), mbTop + 7.4.dp.toPx()),
        )

        // 车尾扬尘与速度线：只有真的在跑才出现
        val dust = d * (1f - d)
        if (dust > 0.01f) {
            for (k in 0 until 3) {
                drawCircle(
                    color = Color.White.copy(alpha = dust * (0.22f - 0.05f * k)),
                    radius = (2.5f + 1.8f * k).dp.toPx() * (0.7f + d),
                    center = Offset(x - (6f + 9f * k).dp.toPx(), trackY - (1.5f + 4f * k).dp.toPx()),
                )
            }
        }
        val streak = ((d - 0.1f) / 0.6f).coerceIn(0f, 1f) * (1f - d)
        if (streak > 0.01f) {
            for (k in 0 until 2) {
                val y = trackY - busH - wheelR + (8f + 11f * k).dp.toPx()
                drawLine(
                    color = Color.White.copy(alpha = streak * 0.4f),
                    start = Offset(x - (26f - 8f * k).dp.toPx(), y),
                    end = Offset(x - 5.dp.toPx(), y),
                    strokeWidth = 1.3.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }

        // 车身：跑起来才颠簸一下
        val bob = if (d > 0f) sin(d * 8f) * 0.9.dp.toPx() * d else 0f
        val top = trackY - wheelR - busH + bob
        // 落地阴影：车身与路面之间那道软影，没有它车是浮在屏上的
        drawLine(
            color = PAINT_GROUND.copy(alpha = 0.30f),
            start = Offset(x + 8.dp.toPx(), trackY + 1.6.dp.toPx()),
            end = Offset(x + busW - 8.dp.toPx(), trackY + 1.6.dp.toPx()),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round,
        )
        // 底盘（压在车身下面露出薄薄一条，车才不是"一张白纸片"）
        drawRoundRect(
            color = PAINT_CHASSIS.copy(alpha = 0.75f),
            topLeft = Offset(x + 4.dp.toPx(), top + busH - 3.dp.toPx()),
            size = Size(busW - 8.dp.toPx(), 5.dp.toPx()),
            cornerRadius = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx()),
        )
        // 车身主体
        drawRoundRect(
            color = Color.White.copy(alpha = 0.96f),
            topLeft = Offset(x, top),
            size = Size(busW, busH),
            cornerRadius = CornerRadius(7.dp.toPx(), 7.dp.toPx()),
        )
        // 车顶那道高光：车身靠它读得出厚度
        drawLine(
            color = Color.White.copy(alpha = 0.85f),
            start = Offset(x + 9.dp.toPx(), top + 1.4.dp.toPx()),
            end = Offset(x + busW - 9.dp.toPx(), top + 1.4.dp.toPx()),
            strokeWidth = 1.2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        // 前顶那块线路牌 + 牌上亮着的一道金 —— 公交最有辨识度的一格，比多画一扇窗值
        drawRoundRect(
            color = windowColor.copy(alpha = 0.92f),
            topLeft = Offset(x + busW - 23.dp.toPx(), top + 3.2.dp.toPx()),
            size = Size(16.dp.toPx(), 4.dp.toPx()),
            cornerRadius = CornerRadius(1.8.dp.toPx(), 1.8.dp.toPx()),
        )
        drawLine(
            color = SPLASH_GOLD.copy(alpha = 0.55f + 0.45f * light),
            start = Offset(x + busW - 21.dp.toPx(), top + 5.2.dp.toPx()),
            end = Offset(x + busW - 12.dp.toPx(), top + 5.2.dp.toPx()),
            strokeWidth = 1.1.dp.toPx(),
        )
        // 金色裙带（点亮的车才上色，剪影阶段不给）
        drawLine(
            color = SPLASH_GOLD.copy(alpha = 0.9f * light),
            start = Offset(x + 6.dp.toPx(), top + busH - 6.5.dp.toPx()),
            end = Offset(x + busW - 6.dp.toPx(), top + busH - 6.5.dp.toPx()),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        // 侧窗三扇 + 一扇前挡：前挡给得更高更圆，车头那面斜玻璃就是这么读的
        for (i in 0 until 3) {
            drawRoundRect(
                color = windowColor.copy(alpha = 0.88f),
                topLeft = Offset(x + (7f + 13f * i).dp.toPx(), top + 9.dp.toPx()),
                size = Size(10.5.dp.toPx(), 8.dp.toPx()),
                cornerRadius = CornerRadius(2.4.dp.toPx(), 2.4.dp.toPx()),
            )
        }
        drawRoundRect(
            color = windowColor.copy(alpha = 0.88f),
            topLeft = Offset(x + busW - 15.dp.toPx(), top + 8.5.dp.toPx()),
            size = Size(10.5.dp.toPx(), 10.5.dp.toPx()),
            cornerRadius = CornerRadius(3.5.dp.toPx(), 3.5.dp.toPx()),
        )
        // 前后两道门缝：门给成半透，中间那一道白线是两扇叶片
        drawRoundRect(
            color = windowColor.copy(alpha = 0.5f),
            topLeft = Offset(x + 47.dp.toPx(), top + 9.dp.toPx()),
            size = Size(7.5.dp.toPx(), 12.dp.toPx()),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
        )
        drawLine(
            color = Color.White.copy(alpha = 0.45f),
            start = Offset(x + 50.75.dp.toPx(), top + 10.5.dp.toPx()),
            end = Offset(x + 50.75.dp.toPx(), top + 19.5.dp.toPx()),
            strokeWidth = 0.8.dp.toPx(),
        )
        // 轮拱 + 车轮：滚动角按走过的距离算，车走多快轮子就转多快
        val roll = (x - startX) / wheelR
        for (i in 0 until 2) {
            val cx = x + (19f + 40f * i).dp.toPx()
            val cy = trackY - wheelR + bob
            drawCircle(
                color = PAINT_CHASSIS.copy(alpha = 0.45f),
                radius = wheelR + 1.6.dp.toPx(),
                center = Offset(cx, cy),
            )
            drawCircle(color = PAINT_TIRE, radius = wheelR, center = Offset(cx, cy))
            drawCircle(color = Color.White.copy(alpha = 0.78f), radius = wheelR * 0.34f, center = Offset(cx, cy))
            for (s in 0 until 2) {
                val ang = roll + s * PI.toFloat() / 2f
                drawLine(
                    color = Color.White.copy(alpha = 0.5f),
                    start = Offset(cx - cos(ang) * wheelR * 0.68f, cy - sin(ang) * wheelR * 0.68f),
                    end = Offset(cx + cos(ang) * wheelR * 0.68f, cy + sin(ang) * wheelR * 0.68f),
                    strokeWidth = 1.dp.toPx(),
                )
            }
        }
        // 尾灯：车尾那一颗红，侧视公交没有它就不完整
        drawCircle(
            color = PAINT_TAIL.copy(alpha = 0.85f),
            radius = 1.7.dp.toPx(),
            center = Offset(x + 3.5.dp.toPx(), top + busH - 10.dp.toPx()),
        )
        // 前照灯：到站待机微亮，起步全亮（双层光斑，不给一条假光束）
        if (light > 0.02f) {
            val hx = x + busW - 3.5.dp.toPx()
            val hy = top + busH - 10.dp.toPx()
            drawCircle(
                color = PAINT_LAMP.copy(alpha = 0.24f * light),
                radius = 7.dp.toPx(),
                center = Offset(hx, hy),
            )
            drawCircle(
                color = PAINT_LAMP_CORE.copy(alpha = 0.95f * light),
                radius = 2.2.dp.toPx(),
                center = Offset(hx, hy),
            )
        }
    }
}

/**
 * 品牌底上那两团漂移柔光（`DrawScope` 扩展，只在闪屏这一屏用）。
 * 与 `FluidBackground` 同一族做法：两三个大半径径向渐变，各按自己的相位慢慢走。
 * 差别只在底是深色品牌渐变，所以光团用白光而不是主题色 —— 主题色压主题色会糊成一片。
 */
private fun DrawScope.brandGlow(drift1: Float, drift2: Float, accent: Color, alpha: Float) {
    val w = size.width
    val h = size.height
    drawRect(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to Color.White.copy(alpha = 0.16f * alpha),
                0.6f to Color.White.copy(alpha = 0.04f * alpha),
                1f to Color.Transparent,
            ),
            center = Offset(w * (0.22f + 0.3f * drift1), h * (0.16f + 0.1f * drift1)),
            radius = w * 0.75f,
        ),
    )
    drawRect(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to accent.copy(alpha = 0.24f * alpha),
                0.55f to accent.copy(alpha = 0.05f * alpha),
                1f to Color.Transparent,
            ),
            center = Offset(w * (0.82f - 0.28f * drift2), h * (0.62f + 0.12f * drift2)),
            radius = w * 0.7f,
        ),
    )
}

/**
 * 品牌 Logo：assets/logo.png —— 与小程序 images/logo.png 同一张定稿原图
 * （用户原图，不重绘 / 不复刻 / 不抠底；原图白底与白色盘面同底无缝）。
 */
private const val LOGO_ASSET = "file:///android_asset/logo.png"

/**
 * 标语与车灯用的金：#F2C673。令牌里的 `gold` = #D9A441 对这块闪屏渐变底
 * 按 WCAG 相对亮度只算得出 2.0:1（0.418 / 0.188），12.5sp 的小字更撑不住，
 * 提亮一档才是 4.5:1。只在这一屏用 —— 它压的是深色渐变底，与页面里的浅色卡片不同底，
 * 所以没有回流进 `BusTokens.gold` 去污染那批浅底用法。
 */
private val SPLASH_GOLD = Color(0xFFF2C673) // 硬编码色：闪屏专用亮金，对深色品牌底算得出 4.5:1（令牌那档只有 2.0:1）

/**
 * 那辆车与路面的**插画色** —— 六颗，逐颗点名理由（判据 HC-d 认的就是这些标记）。
 *
 * 为什么整体不走令牌：这一屏永远压在深色品牌渐变上，而这些颜色讲的是"实物是什么色"。
 * 吃 `t.ink` 那类会随主题翻白的令牌，深色模式下就会画出一辆白轮子、白底盘的车；
 * 而它们也不是任何语义色（危险/价格/状态），借过来只会让后面的读码人找错地方。
 */
private val PAINT_TIRE = Color(0xFF1D1D1A)      // 硬编码色：轮胎橡胶黑，深浅两套主题下都该是黑
private val PAINT_CHASSIS = Color(0xFF22364A)   // 硬编码色：底盘与轮拱的深钢色（比车身暗、比轮胎亮一档）
private val PAINT_GROUND = Color(0xFF0B2033)    // 硬编码色：车身压在品牌底上的那道落地阴影
private val PAINT_LAMP = Color(0xFFFFF0BE)      // 硬编码色：前照灯外圈暖光
private val PAINT_LAMP_CORE = Color(0xFFFFF6DC) // 硬编码色：前照灯灯芯
private val PAINT_TAIL = Color(0xFFE0705E)      // 硬编码色：尾灯那颗红
