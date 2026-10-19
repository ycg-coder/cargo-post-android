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

/**
 * 附近线路卡片上那一行小字：**只放认得出这条线需要的三样** —— 起→止、停几站、现实公交还是项目线路。
 * 缺哪样就明说缺哪样（"起终点没给" / "站点数没给"），不许塌成空白、也不许把没给写成 0 站。
 * 地图、真实折线、逐站时刻都在点进去的详情页，这里不重复铺。
 */
internal fun lineBriefSubtitle(line: BusLine): String {
    val pair = if (line.from.isNotEmpty() && line.to.isNotEmpty()) "${line.from} → ${line.to}" else "起终点没给"
    val stops = if (line.stations.isNotEmpty()) "${line.stations.size} 站" else "站点数没给"
    return "$pair · $stops · ${if (line.dataSource.contains("REAL")) "现实公交" else "项目线路"}"
}

/**
 * 卡片点下去**打不开详情页**的原因：null = 打得开。
 *
 * `/bus/nearby` 的 `lines[]` 只回 `routeName/startStation/endStation/dataSource`，没有 `routeId`
 * （实测 189 条里 121 条没编号，台账 #110）——详情页、地图、经停、折线全都按 `routeId` 取数，
 * 编号都没有就许不了"看这条线的地图与经停"这个动作。旧写法把这种卡也挂上跳转，落点是一句"没有这条线路"，
 * 那是把**后端没给编号**说成**这条线不存在**。现在尾巴上直接念真实原因，点它只把它选给主图。
 */
internal fun lineCardNoDetailReason(line: BusLine): String? =
    if (line.id.isNotEmpty()) null else "系统没给这条线的编号，看不了它的地图与经停"
