package com.shanxiang.buspost.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.shanxiang.buspost.core.ui.BusRadius
import com.shanxiang.buspost.core.ui.BusText
import com.shanxiang.buspost.core.ui.LocalBusFontScale
import com.shanxiang.buspost.core.ui.LocalBusTokens
import com.shanxiang.buspost.core.ui.pressFeedback

/** 详情级页面随处可跳：根导航注入，页面不必层层传 lambda */
val LocalNavController = compositionLocalOf<NavHostController> {
    error("NavController 未注入，BusPostRoot 之外不要使用")
}

/**
 * 数据来源诚实标注：接口不通回落到本地演示集时，页面必须出现这一行，
 * 不能让用户把演示数据当成真实班次（小程序里没有这个约束）。
 *
 * 2026-10-15 复活：UI 复刻方案撤掉的是"黄色警告横幅"这种**样式**，不是撤掉这句话。
 * 上一版直接把函数体 `return` 掉，等于全站 20 处各自写好的诚实文案一起失声，
 * 59 处算出来的 `demo` 标志没人读 —— 演示数据被念成真数据，比丑横幅严重得多。
 * 这里换成一行说明文字（无底色块、无告警条），既守住"不承诺做不到的事"，也不破坏版面。
 */
@Composable
fun DemoNote(demo: Boolean, label: String = "演示数据", text: String = "当前为演示模式，展示本地演示数据，仅用于查看交互效果。") {
    if (!demo) return
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(t.gold),
        )
        Text(
            label,
            color = t.goldDeep,
            fontSize = (11 * scale).sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            modifier = Modifier.padding(start = 6.dp),
        )
        Text(
            " · " + text,
            color = t.textSecondary,
            fontSize = (11 * scale).sp,
            modifier = Modifier.padding(start = 2.dp),
        )
    }
}

/** 首页功能入口格子 */
@Composable
fun EntryTile(
    icon: ImageVector,
    label: String,
    hint: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: Int = 0,
) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val src = remember { MutableInteractionSource() }
    Column(
        modifier
            .pressFeedback(src)
            .clip(RoundedCornerShape(BusRadius.l))
            .background(t.card)
            .clickable(interactionSource = src, indication = androidx.compose.foundation.LocalIndication.current, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
    ) {
        Box {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(BusRadius.m))
                    .background(t.primaryLight),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = label, tint = t.primaryOnLight, modifier = Modifier.size(20.dp))
            }
            if (badge > 0) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(9.dp)
                        .clip(RoundedCornerShape(BusRadius.pill))
                        .background(t.danger),
                )
            }
        }
        Text(
            label,
            color = t.ink,
            fontSize = (14 * scale).sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            modifier = Modifier.padding(top = 9.dp),
        )
        Text(hint, color = t.textTertiary, fontSize = (11 * scale).sp, maxLines = 2, modifier = Modifier.padding(top = 2.dp))
    }
}

/** 尚未接入的 tab 占位（分批实现期间可见，不留白屏） */
@Composable
fun ComingSoon(name: String) {
    val t = LocalBusTokens.current
    Box(Modifier.fillMaxSize().background(t.paper), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(name, color = t.ink, fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Text("页面正在分批接入", style = BusText.caption())
        }
    }
}
