package com.shanxiang.buspost.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanxiang.buspost.core.ui.BusCard
import com.shanxiang.buspost.core.ui.BusIcons
import com.shanxiang.buspost.core.ui.BusRadius
import com.shanxiang.buspost.core.ui.LocalBusFontScale
import com.shanxiang.buspost.core.ui.LocalBusTokens
import com.shanxiang.buspost.core.utils.ReviewMapper
import com.shanxiang.buspost.data.model.ReviewStatus

/**
 * 承运审核结果卡 —— 对应小程序 review-card（可运/需操作/待人工/不可运）。
 * 根据 reviewStatus 显示不同颜色和文案，reasonCodes 自动映射为中文。
 */
@Composable
fun ReviewResultCard(
    status: Int,
    reasonCodes: String?,
    servicePointName: String? = null,
    servicePointKm: Double? = null,
    onNavigate: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    
    // 根据状态确定颜色
    val (borderColor, iconColor, titleColor) = when (ReviewStatus.of(status)) {
        ReviewStatus.PASSED -> Triple(t.statusDone.copy(alpha = 0.3f), t.statusDone, t.statusDone)
        ReviewStatus.CONDITIONAL -> Triple(t.statusShipping.copy(alpha = 0.3f), t.statusShipping, t.statusShipping)
        ReviewStatus.MANUAL_REVIEW -> Triple(t.clayDeep.copy(alpha = 0.3f), t.clayDeep, t.clayDeep)
        ReviewStatus.REJECTED -> Triple(t.danger.copy(alpha = 0.3f), t.danger, t.danger)
        else -> Triple(t.line, t.textSecondary, t.ink)
    }
    
    BusCard(
        modifier = modifier
            .fillMaxWidth()
            .border(1.5.dp, borderColor, RoundedCornerShape(BusRadius.m)),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // 标题行
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    BusIcons.info,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    ReviewMapper.reviewTitle(status),
                    color = titleColor,
                    fontSize = (15 * scale).sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            
            // 原因文案
            val reasonText = ReviewMapper.reasonText(reasonCodes)
            if (reasonText.isNotEmpty()) {
                Text(
                    "原因：$reasonText",
                    color = t.textSecondary,
                    fontSize = (13 * scale).sp,
                )
            }
            
            // 提示文案
            Text(
                ReviewMapper.reviewHint(status),
                color = t.textTertiary,
                fontSize = (12 * scale).sp,
            )
            
            // 需客户送站：显示交接站点 + 导航按钮
            if (ReviewStatus.of(status) == ReviewStatus.CONDITIONAL && servicePointName != null) {
                Spacer(Modifier.height(8.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(BusRadius.s))
                        .background(t.track)
                        .padding(10.dp),
                ) {
                    Text(
                        buildString {
                            append("交接站点：$servicePointName")
                            if (servicePointKm != null && servicePointKm > 0) {
                                append("（距取货点约 ${String.format("%.1f", servicePointKm)} km）")
                            }
                        },
                        color = t.ink,
                        fontSize = (13 * scale).sp,
                        fontWeight = FontWeight.Medium,
                    )
                    if (onNavigate != null) {
                        Text(
                            "导航前往",
                            color = t.primaryOnLight,
                            fontSize = (13 * scale).sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .clickable(onClick = onNavigate),
                        )
                    }
                }
            }
        }
    }
}
