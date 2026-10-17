package com.shanxiang.buspost.ui.user

/**
 * 「附近公交」/「实时公交」那一格的失败文案。
 *
 * 2026-10-17 真机：首页那一格念「实时公交暂时不可用 / 网络不太好，稍后再试」，
 * 而同一时刻对同一个端点发同样的请求是 `HTTP 200 / code:0 / 368500 字节`真数据
 * （`nearbyStations[0]` = 华一路 0.13km、`dataSource=REAL_TRANSIT`）。
 * 状态机里 `error` 明明带着这一轮的真实原因（`HomeScreen` 的 `error = nb.error`），
 * 渲染时却被换成一句笼统话 ⇒ 网络、解析、鉴权三种病在这句话下**长得一模一样**，
 * 谁也没法判断该修哪一层。
 *
 * 口径与司机端异常回执那个 `photoDropText` 同一条：**失败要带原因，原因缺失要换成一句实话**，
 * 不许留成「原因：」后面一片空白。
 *
 * ⚠ 截断必须**从中间剪**：2026-10-17 真机第一版按"取前 90 字"剪，把
 * `…(port 4780` 后面的 `82) after 8000ms` 剪掉了 —— 而那半句正好是"这是超时而不是断网"的唯一依据。
 * 头 62 + 尾 28 留住的分别是"连到哪个地址的哪个端口"和"等了多久"，中间是主机名重复段，可以丢。
 */
internal fun busFailureText(why: String?): String {
    val w = why?.trim().orEmpty()
    val kept = if (w.length <= 90) w else "${w.take(62)}…${w.takeLast(28)}"
    return "这一轮没读到实时公交数据——原因：${if (kept.isEmpty()) "后端与本机都没给原因" else kept}。可点「重新加载」再试"
}
