package com.shanxiang.buspost.ui.user

import com.shanxiang.buspost.core.ui.StopState
import com.shanxiang.buspost.data.model.LegHandover
import com.shanxiang.buspost.data.model.ParcelTrace
import com.shanxiang.buspost.data.model.TraceNode
import com.shanxiang.buspost.data.model.TransportLeg
import com.shanxiang.buspost.data.model.TransitPlan
import com.shanxiang.buspost.data.model.nodeTimeOf
import com.shanxiang.buspost.data.repo.RemoteState
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「查件票面」那一页的契约（#55：`Repo.parcelTopology()` 此前零消费者，`GET /app-api/transport/send/topology`）。
 *
 * 钉五类事，每类都对应一条真实会发生的退化：
 * · **四格分开**：没取到 / 还没结论 / 后端回了空结构 / 有内容。
 *   `AppSendController.java:214-215` 对**非归属人**返回的是 `new OrderTopologyRespVO()`（全字段 null），
 *   既不是错误也不是"没有这件包裹" ⇒ 失败与空结构都不许念成"包裹不存在"（用户手里那张单号是真的）；
 * · **口径必须跟着生产者**：`totalDurationMinutes` 内含"每次换乘 +20 分钟"（`TransportTopologyServiceImpl:134`，
 *   @Schema 只写"总时长(分钟)"），`totalDistanceKm` 是各段相加而段里程可能是 Haversine 估算
 *   （`MultiLegPlanner:187`、`transport-schema.sql:257`）⇒ 念数字不念口径就是把估算说成实测；
 * · **可空键不许塌成结论**：`Candidate.chosen:69` / `durationMinutes:65` / `Handover.itemCount:120`
 *   都是包装类型，塌成 false/0 会把"后端没说"念成"不推荐 / 0 分钟 / 0 件"；
 * · **键名抄生产者**：交接的车牌与时刻真键是 `fromPlateNo`/`toPlateNo`/`arrivedAt`/`handoverStartedAt`/
 *   `handoverCompletedAt`，时间线是 `eventTypeName`/`eventTime`/`detail`（`OrderTopologyRespVO:113-137`）——
 *   旧读法认的是自造键，真接口下整列念空（D-085）；
 * · **时刻归一**：后端把 `LocalDateTime` 注册成 Epoch 毫秒（`Models.kt` 的 `ts()` 取证），
 *   旧写法直接切串会显示 "17590"。
 */
class ParcelTicketContractTest {

    private fun obj(vararg pairs: Pair<String, Any?>): JSONObject {
        val o = JSONObject()
        pairs.forEach { (k, v) -> if (v == null) o.put(k, JSONObject.NULL) else o.put(k, v) }
        return o
    }

    /** 造一条最小可用的 trace：只关心被测那一格，其余走默认值 */
    private fun trace(
        orderNo: String = "YP20261013001",
        status: Int = 6,
        statusName: String = "已发车",
        legs: List<TransportLeg> = emptyList(),
        handovers: List<LegHandover> = emptyList(),
        plans: List<TransitPlan> = emptyList(),
        totalLegs: Int? = 2,
        transferCount: Int? = 1,
        totalDistanceKm: Double? = 27.4,
        totalDurationMinutes: Int? = 106,
        planningModeName: String = "多段联运",
        planNo: String = "PLAN-K1-0830",
        planReason: String = "双河口换乘，含 20 分钟换乘等待",
        startStation: String = "云山村口站",
        endStation: String = "石桥驿站",
    ) = ParcelTrace(
        orderNo = orderNo,
        status = status,
        goodsName = "土鸡蛋 2 箱",
        nodes = emptyList(),
        legs = legs,
        vehiclePlate = "",
        driverName = "",
        driverMobile = "",
        targetStation = "",
        etaMinutes = null,
        dataSource = "",
        vehicleGeo = null,
        polyline = emptyList(),
        stations = emptyList(),
        startStation = startStation,
        endStation = endStation,
        handovers = handovers,
        plans = plans,
        planningModeName = planningModeName,
        planNo = planNo,
        totalLegs = totalLegs,
        transferCount = transferCount,
        totalDistanceKm = totalDistanceKm,
        totalDurationMinutes = totalDurationMinutes,
        planReason = planReason,
    )

    private fun leg(source: String = "") = TransportLeg.parse(
        obj("legSequence" to 1, "fromStationName" to "云山村口站", "toStationName" to "双河口换乘站",
            "plateNo" to "渝A·8K231", "driverName" to "王有德", "status" to 7, "statusName" to "运输中",
            "durationMinutes" to 34, "distanceKm" to 15.2, "navigationSource" to source),
    )

    private val failed = RemoteState<ParcelTrace>(error = "网络不给力，请重试")
    private val notYet = RemoteState<ParcelTrace>(loading = true)
    private val blank = RemoteState<ParcelTrace>(data = ParcelTrace(
        orderNo = "", status = -1, goodsName = "", nodes = emptyList(), legs = emptyList(),
        vehiclePlate = "", driverName = "", driverMobile = "", targetStation = "",
        etaMinutes = null, dataSource = "", vehicleGeo = null, polyline = emptyList(), stations = emptyList(),
    ), success = true)

    // ---------- ① 四格分开 ----------

    @Test
    fun `取失败那一格绝不念成后端没给内容`() {
        val p = parcelTicketPage(failed)
        assertEquals(ParcelTicketKind.Failed, p.kind)
        // 失败格必须同时给"重试"与"回追踪页"两条出口，不能把人关在票面上
        assertTrue(p.body.contains("重试"))
        assertFalse(p.title.contains("没给这张单的票面内容"))
    }

    @Test
    fun `还没取到结论时不替后端下任何结论`() {
        val p = parcelTicketPage(notYet)
        assertEquals(ParcelTicketKind.Loading, p.kind)
        assertEquals("", p.title)
    }

    @Test
    fun `成功但后端回空结构是单独一格`() {
        val p = parcelTicketPage(blank)
        assertEquals(ParcelTicketKind.Absent, p.kind)
        // 非归属人 / 还没排线都走这一格：只能说"没给内容"，不能否定用户手里那张单，也不能说"包裹不存在"
        assertFalse(p.title.contains("不存在"))
        assertFalse(p.body.contains("没有这件包裹"))
        assertTrue(p.body.contains("追踪页"))
    }

    @Test
    fun `有内容才进 Shown 并带出单号`() {
        val p = parcelTicketPage(RemoteState(data = trace(), success = true))
        assertEquals(ParcelTicketKind.Shown, p.kind)
        assertTrue(p.title.contains("YP20261013001"))
    }

    @Test
    fun `给了单号就不算空结构`() {
        assertFalse(ticketContentAbsent(trace(transferCount = null)))
        assertTrue(ticketContentAbsent(trace(orderNo = "", status = -1, planningModeName = "", planNo = "",
            startStation = "", endStation = "", planReason = "", totalLegs = null,
            totalDistanceKm = null, totalDurationMinutes = null)))
    }

    // ---------- ② 抬头行与"没给就说明没给" ----------

    @Test
    fun `抬头恰好十一行且每行都有说法`() {
        val rows = ticketHeaderRows(trace())
        assertEquals(11, rows.size)
        rows.forEach { row -> assertTrue(row.first + " 那格空着", row.second.isNotBlank()) }
        assertEquals("多段联运", rows.toMap()["组织方式"])
        assertEquals("PLAN-K1-0830", rows.toMap()["方案号"])
    }

    @Test
    fun `后端没给的那几格逐行念 后端没给 而不是空白`() {
        val rows = ticketHeaderRows(trace(statusName = "", planningModeName = "", planNo = "",
            totalLegs = null, transferCount = null, totalDistanceKm = null, totalDurationMinutes = null,
            planReason = "", startStation = "", endStation = "")).toMap()
        assertEquals(NOT_GIVEN, rows["状态"])
        assertEquals(NOT_GIVEN, rows["取货站"])
        assertEquals(NOT_GIVEN, rows["送达站"])
        assertEquals(NOT_GIVEN, rows["组织方式"])
        assertEquals(NOT_GIVEN, rows["方案号"])
        assertEquals(NOT_GIVEN, rows["总段数"])
        assertEquals(NOT_GIVEN, rows["换乘次数"])
        assertEquals(NOT_GIVEN, rows["总里程"])
        assertEquals(NOT_GIVEN, rows["总时长"])
        assertEquals(NOT_GIVEN, rows["方案解释"])
    }

    // ---------- ③ 口径：时长含换乘等待、里程必须标估算 ----------

    @Test
    fun `总时长必须带上每次换乘二十分钟的后端口径`() {
        assertTrue(ticketDurationText(trace()).contains("20 分钟"))
        // 换乘次数没给时，不许替后端宣布"已计入/未计入"
        val noCount = ticketDurationText(trace(transferCount = null))
        assertTrue(noCount.contains("换乘次数未给"))
        assertEquals(NOT_GIVEN, ticketDurationText(trace(totalDurationMinutes = null)))
    }

    @Test
    fun `总里程按段的导航来源标口径`() {
        val est = trace(legs = listOf(leg("ESTIMATED"), leg("AMAP")))
        assertTrue(ticketDistanceText(est).contains("含估算段"))
        assertTrue(ticketDistanceText(est).contains("不是真实道路里程"))
        val real = trace(legs = listOf(leg("AMAP")))
        assertTrue(ticketDistanceText(real).contains("真实道路里程相加"))
        // 没有段可依据时不声称口径
        assertTrue(ticketDistanceText(trace(legs = emptyList())).contains("口径未给"))
        assertEquals(NOT_GIVEN, ticketDistanceText(trace(totalDistanceKm = null)))
    }

    // ---------- ④ 可空键不许塌成结论 ----------

    @Test
    fun `候选方案的 chosen 三态里 null 是后端没说`() {
        val absent = planRows(TransitPlan.parse(obj("modeName" to "方案 A"))).toMap()
        assertEquals("后端没说（chosen 未给）", absent["是否推荐"])
        // 塌成 false 会被念成"这个方案不被推荐"——那是替后端下了一个它没下的结论
        assertFalse(absent["是否推荐"].orEmpty().contains("不推荐"))
        val chosen = planRows(TransitPlan.parse(obj("modeName" to "方案 A", "chosen" to true))).toMap()
        assertEquals("后端标为推荐", chosen["是否推荐"])
        val rejected = planRows(TransitPlan.parse(obj("modeName" to "方案 A", "chosen" to false))).toMap()
        assertEquals("后端标为不推荐", rejected["是否推荐"])
        assertEquals(NOT_GIVEN, planRows(TransitPlan.parse(obj("modeName" to "方案 A"))).toMap()["时长"])
    }

    @Test
    fun `交接件数缺席不念成 0 件`() {
        val rows = handoverRows(LegHandover.parse(obj("stationName" to "双河口换乘站"))).toMap()
        assertEquals(NOT_GIVEN, rows["件数"])
        assertFalse(rows["件数"].orEmpty().contains("0"))
        assertEquals("无异常记录", rows["异常原因"])
        val six = handoverRows(LegHandover.parse(obj("itemCount" to 6, "exceptionReason" to "箱子压变形"))).toMap()
        assertEquals("6 件", six["件数"])
        assertEquals("箱子压变形", six["异常原因"])
    }

    // ---------- ⑤ 键名抄生产者（D-085 那次收口的正证） ----------

    @Test
    fun `交接的车牌与时刻只认真接口发的那几个键`() {
        val h = LegHandover.parse(obj(
            "stationName" to "双河口换乘站", "fromPlateNo" to "渝A·8K231", "toPlateNo" to "渝B·5M107",
            "fromDriverName" to "王有德", "toDriverName" to "罗建平",
            "arrivedAt" to "2026-09-28 10:52", "handoverStartedAt" to "2026-09-28 11:00",
            "handoverCompletedAt" to "2026-09-28 11:05", "itemCount" to 6, "statusName" to "已交接",
        ))
        val rows = handoverRows(h).toMap()
        assertEquals("渝A·8K231 · 王有德", rows["交出"])
        assertEquals("渝B·5M107 · 罗建平", rows["接收"])
        // 完成时刻优先（票面问的是"什么时候交完"）
        assertTrue(rows["交接时间"].orEmpty().contains("11:05"))
        assertEquals("已交接", rows["交接状态"])

        // 自造键不再续命：只发 fromPlate/handoverTime 时，票面应当念"后端没给"而不是把假键当真值读出来
        val legacy = handoverRows(LegHandover.parse(obj("fromPlate" to "渝A·1", "handoverTime" to "11:05"))).toMap()
        assertEquals(NOT_GIVEN, legacy["交出"])
        assertEquals(NOT_GIVEN, legacy["交接时间"])
    }

    @Test
    fun `时间线只认 eventTypeName 与 detail 这两个真键`() {
        val t = trace().copy(nodes = listOf(
            TraceNode(label = "已发车", time = "10:20", hint = "双河口站", state = StopState.DONE),
        ))
        val rows = ticketEventRows(t)
        assertEquals(1, rows.size)
        assertEquals("已发车", rows[0].first)
        assertTrue(rows[0].second.contains("10:20"))
        assertTrue(rows[0].second.contains("双河口站"))
        assertFalse(ticketEventsGiven(trace()))
        assertTrue(ticketEventsGiven(t))
    }

    @Test
    fun `epoch 毫秒的时刻归一而不是切成 17590`() {
        // 后端把 LocalDateTime 注册成 Epoch 毫秒（Models.kt 的 ts() 取证），旧写法会把 13 位数字切出前 5 位
        val millis = "1759049640000"
        val got = obj("eventTime" to millis).nodeTimeOf("eventTime")
        assertFalse(got.contains("17590"))
        assertTrue(Regex("""^\d{2}:\d{2}$""").matches(got))
        // ISO 串同样口径（带 T 也要吃）
        assertEquals("11:05", obj("eventTime" to "2026-09-28T11:05:00").nodeTimeOf("eventTime"))
        assertEquals("", obj("other" to 1).nodeTimeOf("eventTime"))
    }

    // ---------- ⑥ 段票 ----------

    @Test
    fun `段票逐行列出状态与计划耗时且不合并`() {
        val rows = legTicketRows(leg("ESTIMATED"))
        assertEquals(5, rows.size)
        val m = rows.toMap()
        assertEquals("运输中", m["第 1 段"])
        assertEquals("云山村口站 → 双河口换乘站", m["走向"])
        assertEquals("渝A·8K231", m["车辆"])
        assertTrue(m["本段"].orEmpty().contains("计划 34 分钟"))
        assertTrue(m["本段"].orEmpty().contains("估算"))
        val bare = legTicketRows(TransportLeg.parse(obj("legSequence" to 2))).toMap()
        assertEquals(NOT_GIVEN, bare["车辆"])
        assertEquals(NOT_GIVEN, bare["本段"])
    }
}
