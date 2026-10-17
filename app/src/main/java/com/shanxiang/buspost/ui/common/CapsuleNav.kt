package com.shanxiang.buspost.ui.common

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanxiang.buspost.core.ui.BusRadius
import com.shanxiang.buspost.core.ui.LocalBusFontScale
import com.shanxiang.buspost.core.ui.LocalBusTokens
import com.shanxiang.buspost.core.ui.LocalBusTouch
import com.shanxiang.buspost.core.ui.glassmorphic
import com.shanxiang.buspost.core.ui.pressFeedback

/**
 * 全局悬浮胶囊导航栏（用户 2026-10-17 口径："做成全局悬浮胶囊导航栏，各个主界面就是原来那些底部导航栏按钮，
 * 点进去具体界面的悬浮导航栏就是具体界面的功能按钮"）。
 *
 * ## 为什么收在根上而不是每屏各画一条
 * 全工程只画**一枚**胶囊，它浮在 `NavHost` 之上；每一屏只负责**登记**自己那一格该显示什么
 * （[CapsuleActions]）。这样"这一屏能做什么"与"这一屏把什么画在底部"不会长成两套说法 ——
 * 之前每屏自己摆底部动作条时，同一件事在司机端就有"顶条 / 底条 / 高德自带面板"三处答案（真机截图里"退出"出现两次）。
 *
 * ## 三条规矩（都在 [capsuleItemsOf] 这一份里，页面不许各写一套）
 * 1. 一屏最多 [CAPSULE_MAX] 格：再多既点不动也认不出；
 * 2. 没有标签的格子不收（图标单独一个没人知道是什么，见任务 #81 的前提）；
 * 3. 禁用的格子必须带原因，原因就地念出来，不许留一个点不动的格子让人猜。
 *
 * ⚠ 本文件**不许**被 `core/ui` 反向依赖（它是界面层），也**不许**出现裸 `Button`（判据 `check_ui_button_probes.sh`）。
 */

/** 胶囊上的一格。`key` 只用于诊断与测试对齐，不进画面。 */
data class CapsuleAction(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val badge: Int = 0,
    val selected: Boolean = false,
    val enabled: Boolean = true,
    /** `enabled = false` 时这一格就地念的话；空串 = 没理由的禁用，会被 [capsuleItemsOf] 摘掉 */
    val disabledReason: String = "",
)

/** 一屏最多几格（超过就是点不动、也认不出） */
internal const val CAPSULE_MAX = 5

/**
 * 一格的最小宽度（dp）。这是**观感**那一半：格子按内容长，两格的胶囊就会一边宽一边窄
 * （真机 2026-10-18 用户端消息中心截图：左格念七个字、右格念"刷新"两个字，整枚歪着）。
 * 撑到 76dp 之后，2 格与 5 格都是"等宽的一排"，而不是随文案长短抖出来的形状。
 */
internal const val CAPSULE_CELL_MIN_WIDTH_DP = 76

/**
 * 登记进来的格子 → 胶囊真正显示的格子。规则只在这一份。
 *
 * 无标签的、以及"禁用又没给原因"的都被摘掉：前者点了不知道会发生什么，
 * 后者是一个坏掉的按钮而不是一句解释 —— 两者都不配上这一栏。
 */
internal fun capsuleItemsOf(actions: List<CapsuleAction>): List<CapsuleAction> =
    actions.filter { it.label.isNotEmpty() && (it.enabled || it.disabledReason.isNotEmpty()) }
        .take(CAPSULE_MAX)

/** 合格的格子数（不含被 [CAPSULE_MAX] 截断的那几格）：`capsuleDropped` 与它一起读才有意义 */
internal fun capsuleEligible(actions: List<CapsuleAction>): Int =
    actions.count { it.label.isNotEmpty() && (it.enabled || it.disabledReason.isNotEmpty()) }

/**
 * 因为"一栏装不下"而被摘掉的格数：胶囊上面那句"还有 N 个动作没显示"用它，不许静默截断。
 * 不合格的格子（没标签、禁用没理由）**不算在这里** —— 那是登记方的错，不是"装不下"。
 */
internal fun capsuleDropped(actions: List<CapsuleAction>): Int =
    (capsuleEligible(actions) - CAPSULE_MAX).coerceAtLeast(0)

/**
 * 谁登记、谁显示之间的那格共享状态。根上 [LocalCapsuleHost] 提供一份，全工程只有这一份。
 *
 * 它是**一叠**而不是一格：详情页是从壳层**推**上去的，转场那一帧里两屏都活着，
 * 而壳层的 effect 不会重跑。若只存"最后一次写入"，从详情页返回时详情页 dispose 把格子清空、
 * 壳层却不再登记 ⇒ 回到首页后主导航整条消失。所以按登记顺序压栈，画的是栈顶那一屏，
 * dispose 只弹自己那一层。
 */
class CapsuleHost internal constructor() {
    private class Layer(val id: Any, val items: List<CapsuleAction>, val droppedCount: Int)

    private val layers = mutableStateListOf<Layer>()

    /** 当前这一屏交给胶囊的格子（空 = 这一屏不显示胶囊） */
    var actions by mutableStateOf<List<CapsuleAction>>(emptyList())
        internal set

    /** 这一屏被截掉的格数（0 = 全都在这栏上） */
    var dropped by mutableStateOf(0)
        internal set

    /** 登记方唯一允许的写入口：同一层重复登记只留最新一份，然后重算栈顶 */
    internal fun push(id: Any, items: List<CapsuleAction>, droppedCount: Int) {
        layers.removeAll { it.id === id }
        layers.add(Layer(id, items, droppedCount))
        syncTop()
    }

    /** 只弹自己那一层：别的屏随后登记的东西不该被这里的离场抹掉 */
    internal fun pop(id: Any) {
        layers.removeAll { it.id === id }
        syncTop()
    }

    private fun syncTop() {
        val top = layers.lastOrNull()
        actions = top?.items ?: emptyList()
        dropped = top?.droppedCount ?: 0
    }
}

val LocalCapsuleHost = staticCompositionLocalOf { CapsuleHost() }

/** 胶囊本体高度：跟着触控下限走（适老/驾驶模式下 56dp 那一档也要装得下） */
@Composable
fun capsulePillHeight(): Dp = LocalBusTouch.current.min + 12.dp

/** 内容底部要给胶囊让出来的高度（含与手势条之间的那一段） */
@Composable
fun capsuleReserve(): Dp = capsulePillHeight() + 14.dp

/**
 * 一屏把自己的功能按钮交给全局悬浮胶囊；**离开这一屏就收回**（`onDispose`）。
 *
 * 谁登记谁负责：不登记就没有胶囊（不是"上一屏的那几条留在屏幕上"）。
 * 壳层（[com.shanxiang.buspost.ui.user.UserShell] / [com.shanxiang.buspost.ui.driver.DriverShell]）
 * 在这里登记的就是原来那四个 tab —— 用户口径里"主界面就是原来那些底部导航栏按钮"。
 */
@Composable
fun CapsuleActions(actions: List<CapsuleAction>) {
    val host = LocalCapsuleHost.current
    // 每次进入这个组合都换一个层号：栈里那一层代表"这一屏的这一次登记"
    val layer = remember { Any() }
    val items = capsuleItemsOf(actions)
    val dropped = capsuleDropped(actions)
    DisposableEffect(layer, items, dropped) {
        host.push(layer, items, dropped)
        onDispose { host.pop(layer) }
    }
}

/**
 * 那枚浮在内容之上的胶囊。**空列表就一格都不画**（不是一条空槽占着屏幕）。
 *
 * 调用点在 `BusPostRoot.kt` 的根 `Box` 里（`Modifier.align(Alignment.BottomCenter)`），
 * 全工程只此一处 —— 判据 `check_capsule_nav_probes.sh` 钉的就是"只有一处画它"。
 */
@Composable
fun CapsuleNavOverlay(modifier: Modifier = Modifier) {
    val host = LocalCapsuleHost.current
    val items = host.actions
    if (items.isEmpty()) return
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val touch = LocalBusTouch.current
    val dropped = host.dropped
    Column(
        modifier.navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (dropped > 0) {
            Text(
                "这一屏还有 $dropped 个动作没放上这一栏（一栏最多 $CAPSULE_MAX 个）",
                color = t.textTertiary,
                fontSize = (10.5 * scale).sp,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
        Row(
            Modifier
                .widthIn(max = BusContentMaxWidth)
                .defaultMinSize(minHeight = capsulePillHeight())
                .glassmorphic(t, shape = RoundedCornerShape(BusRadius.pill), shadowElevation = 10.dp)
                .border(1.dp, t.primary.copy(alpha = 0.20f), RoundedCornerShape(BusRadius.pill))
                .clip(RoundedCornerShape(BusRadius.pill))
                .padding(horizontal = 6.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(touch.gap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { action -> CapsuleCell(action) }
        }
    }
}

@Composable
private fun RowScope.CapsuleCell(action: CapsuleAction) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val touch = LocalBusTouch.current
    val src = MutableInteractionSource()
    val pressed by src.collectIsPressedAsState()
    val on = action.selected
    Column(
        Modifier
            .weight(1f, fill = false)
            .clip(RoundedCornerShape(BusRadius.pill))
            .background(if (on) t.primaryLight else if (pressed) t.primaryLight.copy(alpha = 0.5f) else Color.Transparent)
            .pressFeedback(src)
            .then(
                if (action.enabled) {
                    Modifier.clickable(interactionSource = src, indication = LocalIndication.current) { action.onClick() }
                } else {
                    Modifier
                },
            )
            .defaultMinSize(
                minWidth = CAPSULE_CELL_MIN_WIDTH_DP.dp,
                minHeight = touch.min,
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Icon(
                action.icon,
                contentDescription = null, // 文字就在下面，图标不再重复念一遍（无障碍由 label 那一格负责）
                tint = if (!action.enabled) t.textTertiary else if (on) t.tabActive else t.textTertiary,
                modifier = Modifier.size((21 * scale).dp),
            )
            if (action.badge > 0) {
                Box(
                    Modifier
                        .size((7 * scale).dp)
                        .background(t.danger, CircleShape),
                )
            }
        }
        Text(
            // 禁用格念的是"为什么不能点"，不是按钮名 —— 一个点不动的格子配一个正常标签，就是界面在骗人
            if (action.enabled) action.label else action.disabledReason,
            color = if (!action.enabled) t.textTertiary else if (on) t.primaryOnLight else t.textSecondary,
            fontSize = (10.5 * scale).sp,
            fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
