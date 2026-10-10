package com.shanxiang.buspost.ui.user

import com.shanxiang.buspost.data.model.BusLine

/**
 * 线路详情页"线路那一格"的状态判断（F-3 ⑥）。
 *
 * 单独成文件的原因与 `nav/ShellDecision.kt` 一样、不是为了整洁，是**可测**：
 * 这段判断原先长在 @Composable 的 `if (vm.loading) … else ErrorState("线路信息获取失败")` 里，
 * 工程无 Robolectric ⇒ 页面上到底念了哪句，JVM 用例永远读不到。
 * 抽成纯函数后它归 `RouteDomainDeepLinkTest` 钉，UI 那侧一行视觉结构都没动。
 *
 * ⚠ 本文件**不许**出现 `androidx.compose` 依赖（判据 `check_shell_routing_probes.sh` 的 SR-m 钉这一点）。
 */

/** 线路详情的四种情形，**四态不是布尔** —— 把前两格说成第三格就是假话。 */
enum class DetailSlot {
    /** 拿到了那条线路，正常渲染详情 */
    Ready,

    /** 还在取数：这时说"没有这条线路"是把"还没等到"说成"没有" */
    Loading,

    /** 取数成功、列表里也确实没有这个编号（旧链接、线路已下线） */
    NotInList,

    /** 这次取数失败了：只有这一格允许念"获取失败" */
    Failed,
}

/**
 * 优先级：`Ready > Loading > Failed > NotInList`。
 * `Loading` 与 `Failed` 都拦在 `NotInList` 前面，因为"没等到"和"这次没拿到"都不等于"后端说没有" ——
 * 这个顺序就是本函数唯一的产品含义，改动它会直接改变用户看到的那句话。
 *
 * 最后一格 `else -> Failed` 是**兜底而不是静默**：取数成功、编号也在这批列表里、却没选中它，
 * 说明状态机被写坏了（`load` 里的选线只在落地之后发生），宁可显示错误态让人看见，不画一条别人的线路顶替。
 */
internal fun detailSlotOf(
    target: String?,
    lines: List<BusLine>,
    selectedId: String?,
    loading: Boolean,
    error: String?,
): DetailSlot = when {
    target != null && selectedId == target -> DetailSlot.Ready
    loading -> DetailSlot.Loading
    error != null -> DetailSlot.Failed
    target != null && lines.none { it.id == target } -> DetailSlot.NotInList
    else -> DetailSlot.Failed
}
