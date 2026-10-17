package com.shanxiang.buspost.ui.user

import com.shanxiang.buspost.data.model.BusLine
import com.shanxiang.buspost.data.model.Station

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

/** 「经停站点」那一栏的三种情形（同样**不是布尔**：凑上来的数字比空白更坏）。 */
internal sealed class ViaStations {
    /** 还没选线路 —— 这时"这条线停几站"这个问题还不成立 */
    object NoLine : ViaStations()

    /** 选了线路，但后端这条线没给站点（`points` 为空是常态） */
    object NotGiven : ViaStations()

    /** 这条线路自己的经停站，按它自己给的顺序数 */
    data class Shown(val lineName: String, val stations: List<Station>) : ViaStations()
}

/**
 * 2026-10-17 用户报的第三条：「站点有错误 —— 站点是这个路线的站点，不是整体的站点数量」。
 *
 * 病根是这一行的退路：`line?.stations?.takeIf { it.isNotEmpty() } ?: vm.nearby?.stations.orEmpty()`。
 * 选中的那条线一旦没给站点，就退回**附近所有站点的并集**，而标题仍写着「经停站点 · 共 N 站」
 * ⇒ 用户读到的是"这条线停一千多站"（实测 `/bus/nearby` 在 15km 半径下回 1238 个站，见 `HomeScreen` 那条注释）。
 * 现在三格各说各话，**任何一格都不许拿附近并集顶替**；"附近有多少站"那一格另有它自己的说法
 * （实时公交页的「附近 N 个站点」标签），不混在这里。
 */
internal fun viaStationsOf(line: BusLine?): ViaStations = when {
    line == null -> ViaStations.NoLine
    line.stations.isEmpty() -> ViaStations.NotGiven
    else -> ViaStations.Shown(line.name, line.stations)
}

/** 那一栏的副标题：三格各有各的实话 */
internal fun viaStationsTitle(v: ViaStations): String = when (v) {
    ViaStations.NoLine -> "还没选线路：先挑一条，这里才谈得上经停"
    ViaStations.NotGiven -> "这条线路没给经停站点"
    is ViaStations.Shown -> "${v.lineName.ifEmpty { "这条线" }} 共 ${v.stations.size} 站"
}
