package com.shanxiang.buspost.ui.user

import com.shanxiang.buspost.data.model.LegHandover
import com.shanxiang.buspost.data.model.ParcelTrace
import com.shanxiang.buspost.data.model.TransportLeg
import com.shanxiang.buspost.data.model.TransitPlan
import com.shanxiang.buspost.data.repo.RemoteState

/**
 * 「查件票面」（`GET /app-api/transport/send/topology`）那一页的全部判定 —— 页面只渲染这里给出的结论。
 * 分工与 #42 天气、D-080 公告栏、D-081 乘车安排同一条：**判定在纯层**，这样每一格都能被 JVM 用例钉住
 * （工程没有 Robolectric，页面渲染不出证据）。
 *
 * 为什么这条通路值得单独一页：`Repo.parcelTopology()`（`Repo.kt:780`）自打写进来就**全仓零消费者**，
 * 而后端 `OrderTopologyRespVO` 里"段与段怎么交接、为什么是这个方案、总共多少段/多少换乘"这几格
 * 只有这个端点会发 —— 追踪页（`/send/track`）根本没有这些键。
 *
 * 契约依据（逐字段读生产者 `OrderTopologyRespVO.java:18-137` 与 `TransportTopologyServiceImpl.java:92-176`）：
 * · 顶层 6 格只在 `order != null` 时 set（`:92-99`），`planNo/planningMode/planningModeName/planReason`
 *   只在 `plan != null` 时 set（`:100-109`）；
 * · **非归属人访问时后端返回 `new OrderTopologyRespVO()`（`AppSendController.java:214-215`）= 全字段 null 的空结构，
 *   不是错误码** ⇒ "这一格后端没给内容"与"这件包裹不存在"是两件事，页面必须分开说（本文件 `Absent` 那一格）；
 * · `totalDurationMinutes`（`:44`）除各段计划耗时之和外，**每次换乘另加 20 分钟**（`ServiceImpl:134`），
 *   @Schema 只写"总时长(分钟)" ⇒ 措辞必须带上这个口径，不能与段的 `durationMinutes` 混成一句话；
 * · `totalDistanceKm`（`:42`）是各段里程相加，而段里程可能是经停坐标 Haversine 估算
 *   （`MultiLegPlanner.java:187`「距离/时长先用直线估算，后续由路网回填」、`transport-schema.sql:257`），
 *   所以里程那一格**永远要标口径**，不许念成"真实道路里程"；
 * · `candidates`（`:49`）在 `multiLegService.preview` 抛异常时保持 null（`ServiceImpl:172-174`）
 *   ⇒ 没有候选方案时念"方案对比没给"，不念"这单只有一个方案"。
 */

/** 后端没给就念这一句：不许留空白行，也不许兜一个值（`NOT_GIVEN` 与 #54 同一处出处） */
internal fun given(v: String): String = v.ifBlank { NOT_GIVEN }

internal fun given(v: Int?, suffix: String = ""): String = v?.let { "$it$suffix" } ?: NOT_GIVEN

/**
 * 里程那一格：后端给了数才说话，并且**口径跟着段的 `navigationSource` 走**。
 * · 全部段都是 AMAP → 「各段真实道路里程相加」；
 * · 有任何一段是 ESTIMATED/没给来源 → 「含估算段，不是真实道路里程」；
 * · 一格都给不出（没有任何段） → 「口径未给」。
 * ⚠ 不许只念数字：那会把估算值读成实测值（审计 §72 F4 同一条）。
 */
fun ticketDistanceText(t: ParcelTrace): String {
    val km = t.totalDistanceKm ?: return NOT_GIVEN
    val n = String.format(java.util.Locale.CHINA, "%.1f", km)
    val legs = t.legs
    val caliber = when {
        legs.isEmpty() -> "（口径未给）"
        legs.all { it.navigationSource.equals("AMAP", true) } -> "（各段真实道路里程相加）"
        legs.any { it.estimated } -> "（含估算段，不是真实道路里程）"
        else -> "（各段里程相加，来源后端未标）"
    }
    return "$n km$caliber"
}

/**
 * 总时长那一格：措辞必须带上后端那条「每次换乘 +20 分钟」的口径（`ServiceImpl:134`）。
 * 换乘次数没给时，明说"是否已含换乘等待未知" —— 不许替后端宣布它加过或没加过。
 */
fun ticketDurationText(t: ParcelTrace): String {
    val min = t.totalDurationMinutes ?: return NOT_GIVEN
    val transfers = t.transferCount
    val note = if (transfers == null) "（后端口径含每次换乘 20 分钟；换乘次数未给，是否已计入未知）"
    else "（含 $transfers 次换乘、每次 20 分钟的等待）"
    return "$min 分钟$note"
}

/** 票面抬头：顺序即页面顺序，少一行就是静默删掉一格（新增一行要同轮加用例） */
fun ticketHeaderRows(t: ParcelTrace): List<Pair<String, String>> = listOf(
    "单号" to given(t.orderNo),
    "状态" to given(t.statusName),
    "取货站" to given(t.startStation),
    "送达站" to given(t.endStation),
    "组织方式" to given(t.planningModeName),
    "方案号" to given(t.planNo),
    "总段数" to given(t.totalLegs, " 段"),
    "换乘次数" to given(t.transferCount, " 次"),
    "总里程" to ticketDistanceText(t),
    "总时长" to ticketDurationText(t),
    "方案解释" to given(t.planReason),
)

/**
 * 「后端回了空结构」这一格的判定：不是错误、也不是"没有这件包裹"。
 * 非归属人（`AppSendController:214-215`）与"还没排线"都会走到这里，所以文案只能说"没给内容"，
 * 并保留单号本身（用户手里那张单号是真的，不能反过来否定它）。
 */
fun ticketContentAbsent(t: ParcelTrace): Boolean =
    t.orderNo.isBlank() && t.status < 0 && t.legs.isEmpty() && t.handovers.isEmpty() &&
        t.plans.isEmpty() && t.nodes.isEmpty() && t.totalLegs == null && t.planNo.isBlank()

/** 一段票面行。车牌/司机/时刻没给就各自念"后端没给"，不并成一格 */
fun legTicketRows(l: TransportLeg): List<Pair<String, String>> = listOf(
    "第 ${l.seq} 段" to given(l.statusLabel),
    "走向" to listOf(l.fromStation, l.toStation).joinToString(" → ").ifBlank { NOT_GIVEN },
    "车辆" to given(l.plate),
    "司机" to given(l.driverName),
    "本段" to (l.legPlanText ?: NOT_GIVEN),
)

/** 一次换乘交接：交出/接收两方分开念（`Handover:116-119` 就是四个独立键） */
fun handoverRows(h: LegHandover): List<Pair<String, String>> = listOf(
    "交接站" to given(h.station),
    "交出" to listOf(h.fromPlate, h.fromDriver).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { NOT_GIVEN },
    "接收" to listOf(h.toPlate, h.toDriver).filter { it.isNotBlank() }.joinBlankToGiven(),
    "件数" to given(h.itemCount, " 件"),
    "交接时间" to given(h.time),
    "交接状态" to given(h.statusName),
    // 异常原因只有后端说了才有：没有就是"没有异常记录"，不许把空串念成"异常"
    "异常原因" to if (h.exceptionReason.isBlank()) "无异常记录" else h.exceptionReason,
)

private fun List<String>.joinBlankToGiven(): String = filter { it.isNotBlank() }.joinToString(" · ").ifBlank { NOT_GIVEN }

/**
 * 一个候选方案：推荐标记三态（true/false/null）—— 后端 `chosen:69` 是包装 Boolean，缺席就是"没说"，
 * 塌成 false 会被念成"这个方案不被推荐"（那是替后端下了一个它没下的结论）。
 */
fun planRows(p: TransitPlan): List<Pair<String, String>> = listOf(
    "方案" to given(p.name),
    "是否推荐" to when (p.recommended) {
        true -> "后端标为推荐"
        false -> "后端标为不推荐"
        null -> "后端没说（chosen 未给）"
    },
    "段数" to given(p.legs, " 段"),
    "换乘" to given(p.transfers, " 次"),
    "时长" to given(p.minutes, " 分钟"),
    "理由" to given(p.reason),
)

/**
 * 事件时间线：`timeline`（`OrderTopologyRespVO:55`）的 `eventTypeName`/`eventTime`/`detail` 三格。
 * 一条都没有时**不许**由页面写一句"没有事件"：后端 `ServiceImpl:176` 只在 `order != null` 时才填这一段，
 * 所以空可能是"没给"而不是"没发生"。页面按 `ticketEventsGiven` 决定是列表还是一句"后端没给"。
 */
fun ticketEventRows(t: ParcelTrace): List<Pair<String, String>> = t.nodes.map { n ->
    n.label.ifBlank { NOT_GIVEN } to
        listOf(n.time, n.hint).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { NOT_GIVEN }
}

fun ticketEventsGiven(t: ParcelTrace): Boolean = t.nodes.isNotEmpty()

enum class ParcelTicketKind { Loading, Failed, Absent, Shown }

data class ParcelTicketPage(
    val kind: ParcelTicketKind,
    val title: String,
    val body: String,
    val trace: ParcelTrace?,
    /** 这批是否演示报文：演示票面不得冒充平台真给出的排线与交接 */
    val demo: Boolean,
)

private const val FAILED_TITLE = "票面没取到"
private const val ABSENT_TITLE = "后端这次没给这张单的票面内容"
private const val ABSENT_BODY = "可能是这张单不是您本人的，或还没有排线。追踪页仍可以看已知进度。"

/**
 * 四格分开（与 #54 同一条底线）：
 * · `error` 非空 → Failed（这一轮没成功，票面未知，绝不能念成"没有这张单"）；
 * · 还没结论（`success=false`）→ Loading；
 * · 成功但后端给的是空结构 → **Absent**（`AppSendController:214-215` 那条真实分支）；
 * · 成功且有内容 → Shown。
 */
fun parcelTicketPage(state: RemoteState<ParcelTrace>): ParcelTicketPage {
    val err = state.error
    if (err != null) {
        return ParcelTicketPage(ParcelTicketKind.Failed, FAILED_TITLE, "$err（点「重试」再看一次；也可以回追踪页）", null, demo = false)
    }
    if (!state.success) {
        return ParcelTicketPage(ParcelTicketKind.Loading, "", "", null, demo = state.demo)
    }
    val t = state.data
    if (t == null || ticketContentAbsent(t)) {
        return ParcelTicketPage(ParcelTicketKind.Absent, ABSENT_TITLE, ABSENT_BODY, t, demo = state.demo)
    }
    return ParcelTicketPage(ParcelTicketKind.Shown, "票面 ${t.orderNo.ifBlank { NOT_GIVEN }}", "", t, demo = state.demo)
}
