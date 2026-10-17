package com.shanxiang.buspost.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanxiang.buspost.core.ui.FluidBackground
import com.shanxiang.buspost.core.ui.LocalBusFontScale
import com.shanxiang.buspost.core.ui.LocalBusTokens
import com.shanxiang.buspost.core.ui.glassmorphic
import com.shanxiang.buspost.core.ui.pressFeedback

/**
 * 两端共用的内容列上限（2026-10-10 布局统一）。
 *
 * 为什么是一个数而不是"平板一套、手机一套"：手机（<640dp）上这一条**完全不改变**现有布局，
 * 只有平板/折叠屏会把全幅拉伸收成居中列 —— "两端一致"因此不再依赖宽度分支，
 * 也没有地方再长出侧边导航栏（用户 2026-10-10 明确不要侧栏）。
 * 640dp 取 Material 3 medium 区间偏保守的一档：777dp 宽的平板上两侧各留约 68dp，
 * 单列行长仍在可读范围内，卡片也不会再出现"标签在最左、值跑到 1900px 外"。
 */
val BusContentMaxWidth = 640.dp

/**
 * 统一页面骨架：纸底 + 内容，避免每个页面重复 Scaffold。
 *
 * 2026-10-17 起这一层**不再有 `bottomBar` 参数**：主导航换成全局悬浮胶囊（`CapsuleNav.kt`），
 * 由根上那一处统一画、由每一屏自己登记内容（用户口径："各个主界面就是原来那些底部导航栏按钮"）。
 * 内容底部要给胶囊让出来的高度在这里读同一个 host 算，页面自己不用记这个数。
 */
@Composable
fun BusScaffold(content: @Composable (Modifier) -> Unit) {
    val t = LocalBusTokens.current
    val host = LocalCapsuleHost.current
    Scaffold(
        containerColor = Color.Transparent,
        contentColor = t.ink,
    ) { insets ->
        Box(
            Modifier.padding(insets).consumeWindowInsets(insets).fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            FluidBackground(Modifier.matchParentSize())
            Box(Modifier.widthIn(max = BusContentMaxWidth)) {
                content(
                    Modifier.fillMaxSize()
                        .padding(bottom = if (host.actions.isEmpty()) 0.dp else capsuleReserve()),
                )
            }
        }
    }
}

/**
 * 全屏路由的系统栏避让壳（2026-10-09 Edge-to-Edge 适配）。
 *
 * 为什么要有这一层：targetSdk 36 起 Android 强制 edge-to-edge，窗口延伸到状态栏/导航栏之下。
 * 顶部的避让已收口到 `core/ui` 的 `BusTopBar`（statusBarsPadding，一处改全部生效），但底部没有
 * 对应的公共收口点 ——
 * 全屏路由页都是裸 `Column { BackTopBar(...); 内容 }`，19 个页面各自补一遍
 * 就是 19 个会漏的地方（登录页的「大字模式」就是这么被手势条压掉的）。
 *
 * 调用点在 `BusPostRoot.kt`，那个文件 :78~:138 是在册锚点（`check_doc_anchors.py` / DA-d 闸门在管），
 * 所以调用侧用**全限定名**而不是加 import —— 加一行 import 就把锚点整体顶下几行。
 * 每个路由只把内层 lambda 包一层，行数一字不变。
 *
 * 不包裹的例外：USER_SHELL / DRIVER_SHELL（避让由 [BusScaffold] 的 Scaffold insets 负责，
 * 再包一层会双重避让）；DRIVER_GUIDE / DRIVER_NAV（全屏地图，导航 SDK 自己按 insets 画 UI，
 * 内容不该被手势条顶起来）。
 */
@Composable
fun BusRouteBody(content: @Composable () -> Unit) {
    // `ime` 必须并进来。targetSdk 36 强制 edge-to-edge 之后，manifest 那句
    // `android:windowSoftInputMode="adjustResize"` **已经不缩放窗口了**（Android 15/16 的行为变更），
    // 只吃 navigationBars 的话键盘一弹就把主操作整块盖住 —— 2026-10-10 真机（小米平板 6，2136x3200@440dpi）
    // 实测：登录页"登录"按钮与《服务协议》勾选行完全不可见，且表单不滚动（截图 08-ime-scroll / 09-home）。
    // 用 union 而不是再叠一层 imePadding：键盘弹起时 ime 那一格已含导航条，叠两次会多出一段空白。
    // 2026-10-10 补上 statusBars：部分页面（寄货/商品详情等）没有 BackTopBar 做顶部避让，
    // 内容会顶进状态栏。统一在这里收口并消费掉，页面内的 BackTopBar/Signpost 不再重复避让。
    // systemBars 包含状态栏 + 导航栏；再 union ime 覆盖键盘弹起场景。
    val allInsets = WindowInsets.systemBars.union(WindowInsets.ime)
    val host = LocalCapsuleHost.current
    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(allInsets)
            .consumeWindowInsets(allInsets),
        contentAlignment = Alignment.TopCenter,
    ) {
        FluidBackground(Modifier.matchParentSize())
        // 这一屏登记了胶囊 ⇒ 内容底部让出一格（没登记就是 0，其余 20 多屏布局一字不变）
        Box(
            Modifier
                .widthIn(max = BusContentMaxWidth)
                .padding(bottom = if (host.actions.isEmpty()) 0.dp else capsuleReserve()),
        ) { content() }
    }
}

@Composable
fun BackTopBar(title: String, subtitle: String? = null, actions: @Composable () -> Unit = {}) {
    val nav = LocalNavController.current
    com.shanxiang.buspost.core.ui.BusTopBar(
        title = title,
        subtitle = subtitle,
        onBack = { nav.popBackStack() },
        actions = { actions() },
    )
}
