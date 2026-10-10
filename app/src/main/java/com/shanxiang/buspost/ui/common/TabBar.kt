package com.shanxiang.buspost.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanxiang.buspost.core.ui.LocalBusFontScale
import com.shanxiang.buspost.core.ui.LocalBusTokens

data class TabItem(val label: String, val icon: ImageVector, val badge: Int = 0)

/**
 * 主导航（2026-10-10 定版：**全宽度统一底部 tab，不做侧边导航栏**）。
 *
 * 2026-10-09 那一轮曾按 Material 3 自适应口径给 ≥600dp 的大屏配了 Navigation Rail；
 * 本轮按用户指令撤掉 —— 底部导航两端（手机/平板、用户/司机）只有一套形态：
 * · 交互与视觉的单一答案，两端一致性不再有"宽度分支"这个变量；
 * · 司机端单手/驾驶场景下，底部触达比左侧边栏直接；
 * · 平板上四格 tab 居中铺开依然成立（Row weight 均分，宽度只影响格子内边距观感）。
 *
 * 为什么壳不变：`DriverShell.kt` 曾是在册锚点文件，把宽度判断收在共享组件内部、
 * 两个 Shell 的调用保持一字不动，是那一轮就定下的边界，本轮继续沿用。
 */
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

@Composable
fun BusTabBar(
    items: List<TabItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    BusBottomBar(items, selected, onSelect)
}

/**
 * 底部 tab：站牌横梁式的顶边线 + 主题色激活态，用户端与司机端共用一套视觉，
 * 但项目由各自的 Shell 决定，司机看不到"商城"，用户看不到"发车"。
 */
@Composable
private fun BusBottomBar(
    items: List<TabItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(t.card)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(t.lineDeep))
        // 背景与那条横梁仍是全宽（它是一条"栏"），只有格子本身收进与内容列同一把尺子并居中：
        // 平板上四格摊到 2136px 会让图标与文字之间出现无意义的空旷，也让人怀疑点不点得到。
        Row(
            Modifier.widthIn(max = BusContentMaxWidth)
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 7.dp),
        ) {
            items.forEachIndexed { i, item ->
                val active = i == selected
                Column(
                    Modifier
                        .weight(1f)
                        .clickable { onSelect(i) }
                        .padding(vertical = 3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(contentAlignment = Alignment.TopEnd) {
                        Icon(
                            item.icon,
                            contentDescription = item.label,
                            tint = if (active) t.tabActive else t.textTertiary,
                            modifier = Modifier.size((22 * scale).dp),
                        )
                        if (item.badge > 0) {
                            Box(
                                Modifier
                                    .size((7 * scale).dp)
                                    .background(t.danger, CircleShape),
                            )
                        }
                    }
                    Text(
                        item.label,
                        color = if (active) t.tabActive else t.textTertiary,
                        fontSize = (11 * scale).sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }
    }
}

/**
 * 统一页面骨架：纸底 + 底部导航 + 内容，避免每个页面重复 Scaffold。
 *
 * ⚠ 前置条件：`bottomBar` 必须是 [BusTabBar]（主导航）。动作栏（如 StickyActionBar）
 * 不是页面级导航，塞进来会吃掉内容高度 —— 当前全工程只有两个 Shell 调用本组件，两者都传 BusTabBar。
 */
@Composable
fun BusScaffold(
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (Modifier) -> Unit,
) {
    val t = LocalBusTokens.current
    Scaffold(
        containerColor = t.paper,
        contentColor = t.ink,
        bottomBar = bottomBar,
    ) { insets ->
        // consumeWindowInsets（2026-10-09 Edge-to-Edge 适配）：`padding(insets)` 只是留白，
        // **不消费** —— 子孙再写 `statusBarsPadding()` 会把状态栏高度再叠一遍，壳内页面顶部多出一条空白。
        // 消费之后，Signpost/BusTopBar 里的 statusBarsPadding 在壳内恒为 0（Scaffold 已避让）、
        // 在 BusScaffold 之外的全屏路由恒为真实值 —— 同一个组件两种宿主都正确，不用传参区分。
        // 内容列统一收进居中限宽（理由见 BusContentMaxWidth 那段）：两个 Shell 都走这一条，
        // 所以"用户端首页宫格左堆 35%、司机端卡片值跑到屏幕另一头"这类平板形变一次改掉，页面内部一字不动。
        Box(
            Modifier.padding(insets).consumeWindowInsets(insets).fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            // ⚠ `widthIn` 必须写在 `fillMaxWidth` **之前**（这里干脆不写 fillMaxWidth）：
            //   反过来写会先被撑到父级满宽、限宽失效 —— 真机实测第二版就是这样（内容仍从 x=88 起、底栏没动）。
            // ⚠ 限宽必须落在**这一层容器**上，不能塞进传给 content 的 Modifier 里：
            //   两个 Shell 拿到 modifier 后都写 `Column(modifier.fillMaxSize())`，
            //   `widthIn` 被后面的 `fillMaxSize` 顶掉 —— 真机实测第一版就是这样，底栏收住了、内容仍从 x=50 起。
            Box(Modifier.widthIn(max = BusContentMaxWidth)) {
                content(Modifier.fillMaxSize())
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
    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
        contentAlignment = Alignment.TopCenter,
    ) {
        // 与 BusScaffold 同一把尺子：详情/表单路由在平板上也不许全幅拉伸，两端共用一个常量。
        Box(Modifier.widthIn(max = BusContentMaxWidth)) { content() }
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
