package com.shanxiang.buspost.core.map

/**
 * 线路页折线的三态（F-3 ⑤ / D-042 与 D-048 在册的那句"同族假话"）。
 *
 * 单独成文件的原因不是"整洁"，还是**可测**：这三格判断原先写在 `BusViewModel` 的两处 `when` 里，
 * 工程没有 Robolectric ⇒ 页面文案只能靠肉眼看；而"没问过"与"后端回空"这两格**根本还没被写出来**
 * （原先只有"有没有点"和"失败文案"两格，`polylineSubtitle()` 的 `else ->` 把第三种情况念成了"后端未回传"）。
 * 纯函数化之后，三态的**优先顺序**能被 JVM 用例逐格钉住，页面那边只是把状态喂进来。
 *
 * 为什么必须是**三态而不是布尔**：`AppBusController.java:48-54` 的 `/line-polyline` 是
 * "点开线路时按需查询"（`AppBusLineRespVO.RoadPoint` 只有经纬度，没有来源键），所以客户端手里
 * 同时存在四种真实情况 —— 没问、在问、问过且后端回了空数组、问过且这次失败 —— 而
 * `AppBusServiceImpl.getLinePolyline():227` 在"线路不存在 / points 为 null / `points.size < 2`"三种
 * 服务端状态下**都返回 null**（`RoadPoint` 无 provider 键 ⇒ 客户端连"这条几何算不算真实道路"都分不出）。
 * 两格文案会把"没问"和"问过且回空"说成同一句话，这正是本轮要修的那处。
 */
internal enum class PolylinePhase {
    /** 还没有为当前线路发出过折线请求（首屏自动选中、线路编号为空、请求被取消） */
    NotAsked,

    /** 请求在飞 */
    Asking,

    /** 问过这一次，结果已经写进 `polyline` / 失败原因 */
    Asked,
}

/**
 * 首屏自动选中之后要不要顺手去问折线。
 *
 * · `silent` 一律不问：15s 静默轮询只是刷新车辆位置，把它接上按需查询会把"点开线路才查一次"
 *   变成轮询风暴（后端那侧是 5 分钟缓存，`fetchRoutePolyline` 缓存 300_000 ms，但请求条数仍是轮询节奏）；
 * · 编号为空一律不问：`routeId` 在后端是 `@RequestParam` **必填**，空编号发出去只会拿到 400，
 *   然后把"我自己没编号"念成"后端没给这条线路轨迹"。
 */
internal fun shouldAskPolyline(selectedRouteId: String?, silent: Boolean): Boolean =
    !silent && !selectedRouteId.isNullOrEmpty()

/**
 * 折线区上方的那句状态（副标题）。
 *
 * 顺序即结论：**问过的证据要晚于"在问/没问"**，否则一次被取消的请求会留下"正在要"的谎；
 * 而 `NotAsked`/`Asking` 两格必须早于失败与"后端未回传" —— "后端未回传该线路折线"这一句
 * 只允许在 `Asked` + 0 点 + 无失败时出口，那是本屏唯一真的知道"后端回了空"的时刻。
 */
internal fun polylineSubtitleOf(phase: PolylinePhase, pointCount: Int, failure: String?): String = when {
    pointCount >= 2 -> "真实道路折线"
    phase == PolylinePhase.Asking -> "正在向后端要这条线路的折线…"
    phase == PolylinePhase.NotAsked -> "还没去问这条线路的折线"
    failure != null -> "这次没问到折线，可重试"
    else -> "暂无线路折线"
}

/**
 * 折线缺失处（地图上）那段说明。`whenEmpty` 由页面自己给，保留两处原文案（F-2 ⑰ 不重排 UI）。
 *
 * 与副标题同一条顺序，多带一层"这里不拿站点连线顶替"的禁止性说明：三格新文案都不给按钮之外的
 * 任何几何承诺 —— 不说"稍后再试就有"，也不画示意线。
 */
internal fun polylineReasonOf(phase: PolylinePhase, failure: String?, whenEmpty: String): String = when {
    phase == PolylinePhase.Asking ->
        "正在向后端要这条线路的折线，拿到之前这里不画线：站点顺序看下面的经停站列表。"
    phase == PolylinePhase.NotAsked ->
        "这条线路的折线还没去问过（不是后端说没有）。点重试可以现在问一次，问过之前这里不拿站点连线顶替。"
    failure != null ->
        "这次没问到该线路的折线（$failure）。这里不拿站点连线顶替，点重试可再问一次。"
    else -> whenEmpty
}
