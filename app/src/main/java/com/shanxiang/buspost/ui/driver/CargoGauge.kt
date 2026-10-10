package com.shanxiang.buspost.ui.driver

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanxiang.buspost.core.ui.BusRadius
import com.shanxiang.buspost.core.ui.BusSpace
import com.shanxiang.buspost.core.ui.LocalBusFontScale
import com.shanxiang.buspost.core.ui.LocalBusTokens

/**
 * 大巴车行李舱可视化（对齐小程序 workbench.wxml:153-187）
 * 显示当前空余仓位百分比，带渐变填充动画和已用标记线。
 *
 * 口径：件数（`cargoCapacity` − 本班 `loadedCount`，两个都是后端真实键），与重量口径的
 * `capacityKg` 是两套数；已装重量后端没有口径，所以这里不出现"已用 kg"。
 * 三个数任缺其一都不能凭空算出百分比 ⇒ 参数可空，null 走"未给"态而不是"100% 空仓"。
 */
@Composable
fun CargoGauge(
    capacityPercent: Double?, // 0-100，当前空余仓位比例；null = 后端没给够算它的数
    loadedCount: Int?,
    cargoCapacity: Int?,
    capacityKg: Double?,
    modifier: Modifier = Modifier,
) {
    val t = LocalBusTokens.current
    // 旧写法硬编码 `val scale = 1f` 并注释"WorkbenchScreen 会通过 LocalBusFontScale 传递" ——
    // 那是把"本该读令牌"写成了"假定外面会传"，实际从未有参数传进来：老年模式与驾驶模式下
    // 整个仓位表（含"0 / 24 件"这类关键数）都不跟着放大，是全站唯一漏掉字号令牌的组件。
    val scale = LocalBusFontScale.current

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 大巴车图形主体
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BusSpace.l),
        ) {
            // 左侧：大巴车身
            BusBodyGraphic(
                capacityPercent = capacityPercent,
                loadedCount = loadedCount,
                cargoCapacity = cargoCapacity,
            )

            // 右侧：运力数值
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(BusSpace.s),
            ) {
                Text(
                    text = capacityPercent?.let { "${it.toInt()}%" } ?: "未给",
                    style = androidx.compose.material3.MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Black,
                        color = if (capacityPercent == null) t.textTertiary else t.primary,
                        fontSize = (28 * scale).sp,
                    ),
                )
                Text(
                    text = if (capacityPercent == null) "空余仓位（件数未给）" else "当前空余仓位",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(
                        color = t.textSecondary,
                        fontSize = (13 * scale).sp,
                    ),
                )
                if (loadedCount != null && cargoCapacity != null) {
                    Text(
                        text = "$loadedCount / $cargoCapacity 件",
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium.copy(
                            color = t.clay,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = (14 * scale).sp,
                        ),
                    )
                }
                // 额定载重是后端真实键 cargoCapacityKg；"已装多少 kg"没有口径，所以这一行只说额定值
                if (capacityKg != null) {
                    Text(
                        text = "额定载重 ${formatKg(capacityKg)} kg",
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium.copy(
                            color = t.textSecondary,
                            fontSize = (12.5 * scale).sp,
                        ),
                    )
                }
            }
        }

        // 仓位标签（满仓/50%/空仓）
        Row(
            modifier = Modifier
                .width(120.dp)
                .padding(top = BusSpace.m),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("满仓", style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(color = t.textTertiary, fontSize = (11 * scale).sp))
            Text("50%", style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(color = t.textTertiary, fontSize = (11 * scale).sp))
            Text("空仓", style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(color = t.textTertiary, fontSize = (11 * scale).sp))
        }
    }
}

@Composable
private fun BusBodyGraphic(
    capacityPercent: Double?,
    loadedCount: Int?,
    cargoCapacity: Int?,
    modifier: Modifier = Modifier,
) {
    val t = LocalBusTokens.current
    val animatedPercent = remember { Animatable(0f) }

    LaunchedEffect(capacityPercent) {
        animatedPercent.animateTo(
            // 填充画的是**已用**，不是空余。`capacityPercent` 的口径是"当前空余仓位比例"（下面那行文字
            // 就是这么念的），直接拿它当填充会把"空余 100% = 一件都没装"画成"整车涂满蓝"，
            // 司机一眼读成"装满货了" —— 2026-10-10 真机那一屏就是这个形状
            // （`docs/device-evidence/20261010-sweep/17-driver-workbench.png`：100% + 0/24 件 + 车身全蓝 + 标尺写着"满仓"）。
            // 数据一字未改（数字与文案仍说空余），改的只是图形的方向与"满仓/空仓"标尺同向。
            // 没给百分比仍然不画（旧写法把缺失当 100%，那一半的教训原样有效）。
            targetValue = capacityPercent?.let { (100f - it.toFloat()).coerceIn(0f, 100f) } ?: 0f,
            animationSpec = tween(durationMillis = 300),
        )
    }

    Column(
        modifier = modifier
            .width(120.dp)
            .clip(RoundedCornerShape(BusRadius.l))
            .background(t.card)
            .border(1.dp, t.line.copy(alpha = 0.08f), RoundedCornerShape(BusRadius.l))
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 车窗（4个）
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            repeat(4) {
                Box(
                    modifier = Modifier
                        .size(width = 20.dp, height = 14.dp)
                        .clip(RoundedCornerShape(BusRadius.s))
                        .background(t.primaryLight)
                        .border(0.5.dp, t.line.copy(alpha = 0.06f), RoundedCornerShape(BusRadius.s)),
                )
            }
        }

        // 行李舱区域（虚线边框 + 渐变填充）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .clip(RoundedCornerShape(BusRadius.m))
                .background(t.paper)
                .border(1.dp, t.line.copy(alpha = 0.16f), RoundedCornerShape(BusRadius.m)),
        ) {
            // 渐变填充（从下往上）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .height((animatedPercent.value / 100f * 100).dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(t.accent, t.primary),
                        ),
                    )
                    .clip(
                        RoundedCornerShape(
                            topStart = 0.dp,
                            topEnd = 0.dp,
                            bottomStart = BusRadius.m,
                            bottomEnd = BusRadius.m,
                        ),
                    ),
            ) {
                // 内部高光条
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(2.dp)
                        .align(Alignment.TopCenter)
                        .padding(top = 4.dp)
                        .background(Color.White.copy(alpha = 0.35f))
                        .clip(RoundedCornerShape(BusRadius.s)),
                )
            }

            // 已用标记线（陶土橙）：件数口径，上限与已装两个数都齐才画
            if (loadedCount != null && cargoCapacity != null &&
                cargoCapacity > 0 && loadedCount > 0 && loadedCount < cargoCapacity
            ) {
                val usedPercent = loadedCount.toFloat() / cargoCapacity * 100f
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = (usedPercent / 100f * 100).dp)
                        .height(1.5.dp)
                        .background(t.clay),
                )
            }
        }

        // 车轮（2个）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            repeat(2) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(t.ink)
                        .border(1.5.dp, t.ink.copy(alpha = 0.25f), CircleShape),
                )
            }
        }
    }
}

private fun formatKg(kg: Double): String {
    return if (kg >= 1000) {
        String.format("%.1f", kg / 1000) + "吨"
    } else {
        kg.toInt().toString()
    }
}
