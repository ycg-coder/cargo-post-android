package com.shanxiang.buspost.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearOutSlowInEasing
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * 启动闪屏 —— 对应小程序 pages/index/index 的 .splash（品牌固定画布，13 节点）。
 *
 * 设计契约（与小程序注释同源，改版前请先读）：
 *  ① 只留品牌元素：柔光 · 远山 · 徽标盘面 · 原图插画 · 名称 · slogan ·
 *     进度线容器 · 进度线 · 进入胶囊 · 胶囊文字 · 箭头 · 状态文案 · 版本号 = 13 节点。
 *     纯装饰只允许「柔光 + 远山」两处。
 *  ② 版面 / 配色 / 文案固化，不随版本改动；唯一可变的是真实加载进度与状态文案。
 *  ③ 色相只用两种：品牌主色梯度（底）+ 稻谷金（点缀）。
 *  ④ 字号全部 ≥28rpx（=14sp），交互：整屏可点进入、500ms 防误触、6s 兜底自动进入。
 *  ⑤ 散场：品牌碎片各自四散（徽标上抛 / 名称左飞 / slogan 右飞 / 进度与胶囊下坠），
 *     幕布延迟 55% 再整屏退场。
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
    var scattering by remember { mutableStateOf(false) }
    val veil = remember { Animatable(1f) }

    // scatter 各碎片进度（0→1），用独立进度实现"各自四散"
    val sc = remember { Animatable(0f) }

    LaunchedEffect(Unit) { shownAt = System.currentTimeMillis() }

    // 6s 兜底自动进入：用户不知道可以点时不会被卡住
    LaunchedEffect(Unit) {
        delay(6000)
        if (!scattering) {
            scattering = true
        }
    }

    LaunchedEffect(scattering) {
        if (!scattering) return@LaunchedEffect
        sc.animateTo(1f, tween(460, easing = ScatterEase))
        veil.animateTo(0f, tween(560, easing = ScatterEase))
        onFinished()
    }

    /** 碎片自定义散场：stagger 由各节点自己的 delay 表达，这里统一 ease-in */
    fun scatterMod(
        dx: Float = 0f,
        dy: Float = 0f,
        rotate: Float = 0f,
        scaleTo: Float = 1f,
        delayMs: Int = 0,
    ): Modifier {
        val p = ((sc.value * 460f - delayMs) / 460f).coerceIn(0f, 1f)
        return Modifier.graphicsLayer {
            translationX = dx * p
            translationY = dy * p
            rotationZ = rotate * p
            scaleX = 1f + (scaleTo - 1f) * p
            scaleY = 1f + (scaleTo - 1f) * p
            alpha = 1f - p
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
                        0.22f to t.primary,
                        1f to t.primaryDark,
                    ),
                    center = Offset(0.5f, 0f),
                    radius = 1400f,
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                // 500ms 内点击视为误触忽略
                if (System.currentTimeMillis() - shownAt < 500) return@clickable
                scattering = true
            },
        contentAlignment = Alignment.Center,
    ) {
        // 节点 1：品牌柔光（唯一装饰之一）
        Box(
            Modifier
                .size(540.dp)
                .graphicsLayer { alpha = 1f - sc.value; scaleX = 1f + 0.5f * sc.value; scaleY = scaleX }
                .align(Alignment.TopCenter)
                .padding(top = 0.dp)
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colorStops = arrayOf(0f to Color.White.copy(alpha = 0.13f), 0.64f to Color.Transparent),
                        ),
                        radius = size.minDimension / 2,
                    )
                },
        )

        // 节点 2：远山剪影（唯一装饰之二）—— 单节点做"亮脊 + 暗身"
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(310.dp)
                .padding(horizontal = 0.dp)
                .graphicsLayer {
                    alpha = 1f - sc.value
                    translationY = size.height * 0.22f * sc.value
                }
                .drawBehind {
                    drawOval(
                        brush = Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.10f), Color.Black.copy(alpha = 0.10f)),
                        ),
                        topLeft = Offset(-size.width * 0.24f, size.height * 0.10f),
                        size = androidx.compose.ui.geometry.Size(size.width * 1.48f, size.height * 1.4f),
                    )
                },
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(start = 32.dp, end = 32.dp, bottom = 70.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // 节点 3 + 4：徽标盘面（白底 + 稻谷金细环）+ 用户定稿原图插画
            Box(
                Modifier
                    .size(94.dp)
                    .scale(1f)
                    .clip(CircleShape)
                    .background(t.onPrimary)
                    .border(3.dp, t.gold, CircleShape)
                    .then(scatterMod(dy = -130f, rotate = -8f, scaleTo = 0.92f)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = coil.compose.rememberAsyncImagePainter(model = LOGO_ASSET),
                    contentDescription = "乡邮智联",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(80.dp),
                )
            }

            // 节点 5：品牌名称（字重 800 + 大字距 = 品牌字标记）
            Text(
                "乡邮智联",
                color = t.onPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 5.sp,
                modifier = Modifier
                    .padding(top = 24.dp)
                    .then(scatterMod(dx = -100f, dy = 20f, rotate = -4f, delayMs = 40)),
            )

            // 节点 6：slogan —— 一行楷体细体，nowrap（品牌页一行文案必须保单行）
            Text(
                "公交带货 · 快递进村 · 农产品出山",
                color = t.onPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = FontFamily.Serif,
                letterSpacing = 3.sp,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .then(scatterMod(dx = 100f, dy = 20f, rotate = 4f, delayMs = 60)),
            )

            // 节点 7 + 8：进度线容器 + 进度线（唯一动态区）
            Box(
                Modifier
                    .padding(top = 34.dp)
                    .width(130.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Color.Black.copy(alpha = 0.30f))
                    .then(scatterMod(dy = 70f, delayMs = 70)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.coerceIn(0, 100) / 100f)
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(Color.White),
                )
            }

            // 节点 9/10/11：就绪胶囊（静态，无无限动画）；未就绪则 节点 12 状态文案
            if (ready) {
                Row(
                    Modifier
                        .padding(top = 20.dp)
                        .clip(RoundedCornerShape(BusRadius.pill))
                        .background(Color.Black.copy(alpha = 0.24f))
                        .border(1.dp, Color.White.copy(alpha = 0.32f), RoundedCornerShape(BusRadius.pill))
                        .padding(start = 17.dp, end = 15.dp, top = 8.dp, bottom = 8.dp)
                        .then(scatterMod(dy = 110f, delayMs = 80)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "轻触屏幕进入",
                        color = t.onPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("›", color = t.onPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Text(
                    statusText,
                    color = t.onPrimary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(top = 20.dp)
                        .then(scatterMod(dy = 110f, delayMs = 80)),
                )
            }
        }

        // 节点 13：版本号（黑 28% 票根底，保证白字达标）
        Text(
            version,
            color = t.onPrimary,
            fontSize = 12.sp,
            letterSpacing = 1.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 30.dp)
                .clip(RoundedCornerShape(BusRadius.s))
                .background(Color.Black.copy(alpha = 0.28f))
                .padding(horizontal = 8.dp, vertical = 3.dp)
                .then(scatterMod(dy = 55f, delayMs = 90)),
        )
    }
}

/** ease-in：碎片加速离场（对应小程序 cubic-bezier(0.55,0,0.85,0.36) 的加速感） */
private val ScatterEase = Easing { fraction -> fraction * fraction * (3f - 2f * fraction) * 0.35f + fraction * fraction * 0.65f }

/**
 * 品牌 Logo：assets/logo.png —— 与小程序 images/logo.png 同一张定稿原图
 * （用户原图，不重绘 / 不复刻 / 不抠底；原图白底与白色盘面同底无缝）。
 */
private const val LOGO_ASSET = "file:///android_asset/logo.png"
