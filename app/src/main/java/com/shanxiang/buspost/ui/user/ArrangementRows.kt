package com.shanxiang.buspost.ui.user

import com.shanxiang.buspost.data.model.Arrangement
import com.shanxiang.buspost.data.repo.RemoteState

/**
 * 「我的乘车安排」那一页的全部判定（页面只渲染这里给出的结论，与 #42 天气、D-080 公告栏同一套分工）。
 *
 * 为什么要把判定抽出来：这一页最容易写坏的两件事都是"把未知收成否定"——
 * ① 请求失败渲染成"还没有乘车安排"（司机已经带着货在路上，页面却说没安排）；
 * ② 后端没给的那个**数字码**被当文案念出去（`planStatus` 是 Integer，不是文字）。
 * 两条都在纯层钉住，页面里就不可能各写一套。
 *
 * 契约依据（逐字段读生产者，不猜）：`AppSendController.arrangements()` 只 set 这十一个键 ——
 * `orderId`、`orderNo`、`status`、`statusName`、上车站两个键、下车站两个键、`planId`、`planStatus`、`vehiclePlateNo`；
 * 没有 `etaMinutes`（审计 §68）、没有 `shiftCode/targetStation/dataSource`（演示面自造的键）。
 * 没有 `etaMinutes`（审计 §68）、没有 `shiftCode/targetStation/dataSource`（演示面自造的键）。
 * ⇒ 这一页**不显示任何"还有几分钟"**：到车提醒走的是 `/send/page` 的 `carrierEtaMinutes`，且必须标"直线估算"。
 */

/** 后端 `DispatchPlanStatusEnum` 的六个值，逐条照抄生产者；不在表里的码原样带出来，不猜它是什么 */
internal fun planStatusText(code: Int?): String = when (code) {
    null -> "方案状态未给"
    0 -> "待审核"
    1 -> "已下发"
    2 -> "执行中"
    3 -> "已完成"
    4 -> "已作废"
    5 -> "异常"
    else -> "未识别的方案状态（$code）"
}

/** 后端没给就是"没给"：站点被删时 `stationNameMap.get(...)` 返回 null，不许渲染成空白行，也不许兜一个站名 */
internal const val NOT_GIVEN = "后端没给"

/** 一条安排的所有行。顺序即页面顺序；新增一行要同时改用例（少一行就是把某一格静默删掉） */
internal fun arrangementRows(a: Arrangement): List<Pair<String, String>> = listOf(
    "订单号" to a.orderNo.ifBlank { NOT_GIVEN },
    "订单状态" to a.statusLabel.ifBlank { NOT_GIVEN },
    "上车站" to a.boardingStation.ifBlank { NOT_GIVEN },
    "下车站" to a.alightingStation.ifBlank { NOT_GIVEN },
    "承运车辆" to a.plate.ifBlank { NOT_GIVEN },
    "调度方案" to planStatusText(a.planStatusCode),
)

enum class ArrangementsKind { Loading, Failed, Empty, Shown }

data class ArrangementsPage(
    val kind: ArrangementsKind,
    val title: String,
    val body: String,
    val items: List<Arrangement>,
    /** 这批是否演示数据：演示安排不得冒充平台真给出的安排 */
    val demo: Boolean,
)

private const val EMPTY_TITLE = "还没有乘车安排"
private const val EMPTY_BODY = "客运寄货单排上班次后会出现在这里。"
// （服务边界那句话只写在 `core/about/AppPolicies` 一处：
// 判据 PL-g 数的就是它的出处数，这一页再抄一遍会变成两份文字各自漂移。）
private const val FAILED_TITLE = "乘车安排没取到"

/**
 * 四格分开：
 * · `error` 非空 → Failed（这一轮根本没成功，条数未知，绝不能报"0 条 / 还没有"）；
 * · 还没取到结论（`success=false` 且没错误）→ Loading，不替后端下任何结论；
 * · 取成功且空 → Empty；
 * · 取成功且有 → Shown。
 */
fun arrangementsPage(state: RemoteState<List<Arrangement>>, loading: Boolean): ArrangementsPage {
    val err = state.error
    if (err != null) {
        return ArrangementsPage(
            ArrangementsKind.Failed,
            FAILED_TITLE,
            "$err（下拉刷新或点「重新取」再来一次）",
            emptyList(),
            demo = false,
        )
    }
    val list = state.data ?: emptyList()
    if (!state.success) {
        return ArrangementsPage(ArrangementsKind.Loading, "", "", emptyList(), demo = state.demo)
    }
    if (list.isEmpty()) {
        return ArrangementsPage(ArrangementsKind.Empty, EMPTY_TITLE, EMPTY_BODY, emptyList(), demo = state.demo)
    }
    return ArrangementsPage(
        ArrangementsKind.Shown,
        "共 ${list.size} 条安排",
        "",
        list,
        demo = state.demo,
    )
}
