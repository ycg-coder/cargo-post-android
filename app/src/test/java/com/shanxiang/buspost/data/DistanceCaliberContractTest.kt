package com.shanxiang.buspost.data

import com.shanxiang.buspost.data.model.BusLine
import com.shanxiang.buspost.data.model.QuoteResult
import com.shanxiang.buspost.data.model.RoutePreview
import com.shanxiang.buspost.data.model.TransportLeg
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 审计 §72「三个 distanceKm 三种口径」契约测试。
 *
 * fixture 的键名逐个抄自后端 VO，不是抄 Android 的候选名列表：
 * - 线路里程 `AppBusLineRespVO.java:27-28` `distanceKm` Double（`AppBusServiceImpl:179` 原样透传
 *   `route.getDistanceKm()`，上游 `MonitoringServiceImpl:126` 可空）；
 * - 段里程 `AppDriverLegRespVO.java:58-62` `distanceKm` BigDecimal + `durationMinutes` Integer，都可空；
 * - 计价里程 `AppSendQuoteRespVO.java:21-22` `distanceKm` BigDecimal，Schema 原文
 *   "**直线距离 × 路网系数**；站点缺坐标时为 null"。
 *
 * 三处共用一个键名却是三种口径，所以旧写法犯过的两类错都要各钉一遍：
 * ① 缺键塌成 0（读起来像"这段 0 公里"），② 把别的报文里表示**米**的 `distance`/`meters` 当公里读。
 */
class DistanceCaliberContractTest {

    private fun obj(vararg pairs: Pair<String, Any?>): JSONObject {
        val o = JSONObject()
        pairs.forEach { (k, v) -> o.put(k, v ?: JSONObject.NULL) }
        return o
    }

    @Test
    fun `线路里程只认 distanceKm 缺键是 null 不是 0 公里`() {
        val line = BusLine.parse(obj("routeId" to 7, "routeName" to "K1 县城 ⇄ 云山村", "distanceKm" to 12.4))
        assertEquals(12.4, line.distanceKm!!, 1e-9)

        // 后端契约里没有 distance/meters 这两个键：它们只出现在旧报文里，所以保留解析但**不许**当公里读，
        // 也不再缺键塌 0（F-3 ②：distanceMeters 改可空 ⇒ 缺键=null，旧报文里有值才读得到）
        val legacy = BusLine.parse(obj("routeId" to 7, "routeName" to "K1", "distance" to 12400))
        assertNull("米制 distance 不许被当公里读", legacy.distanceKm)
        assertEquals(12400.0, legacy.distanceMeters!!, 1e-9)
        assertNull("缺键就是没给里程，不许读成 0.0 公里", BusLine.parse(obj("routeId" to 7, "routeName" to "K1")).distanceMeters)

        // 显式 null（上游 route.getDistanceKm() 为 null）与缺键同结论，但都要能构造出来
        assertNull(BusLine.parse(obj("routeId" to 7, "distanceKm" to null)).distanceKm)
        assertNull(BusLine.parse(obj("routeId" to 7)).distanceKm)
    }

    @Test
    fun `段卡里程缺哪个就不提哪个 两个都缺整行不显示`() {
        val both = TransportLeg.parse(
            obj("legSequence" to 2, "distanceKm" to 21.4, "durationMinutes" to 55, "navigationSource" to "AMAP"),
        )
        assertEquals("本段 21.4 km · 计划 55 分钟", both.legPlanText)

        // durationMinutes 语义是"计划"不是"剩余"，措辞不许写成还需/还有
        assertFalse(both.legPlanText!!.contains("剩余"))
        assertFalse(both.legPlanText!!.contains("还需"))

        val onlyKm = TransportLeg.parse(obj("legSequence" to 1, "distanceKm" to 21.4))
        assertEquals("本段 21.4 km", onlyKm.legPlanText)
        val onlyMin = TransportLeg.parse(obj("legSequence" to 1, "durationMinutes" to 55))
        assertEquals("本段 计划 55 分钟", onlyMin.legPlanText)

        // 两个都没给：整行不显示（返回 null），不许拼出"本段 0.0 km"
        val none = TransportLeg.parse(obj("legSequence" to 1, "distanceKm" to null, "durationMinutes" to null))
        assertNull(none.legPlanText)
        assertNull(TransportLeg.parse(obj("legSequence" to 1)).legPlanText)

        // 估算来源必须带口径标记（与后端 admin 拓扑页 navigationSource==='ESTIMATED' 补"· 估算"同口径）
        val est = TransportLeg.parse(
            obj("legSequence" to 1, "distanceKm" to 15.2, "durationMinutes" to 34, "navigationSource" to "ESTIMATED"),
        )
        assertTrue(est.estimated)
        assertEquals("本段 15.2 km · 计划 34 分钟（估算）", est.legPlanText)

        // 后端真给 0 就原样透传，不改成 null、也不拿它当"没给"
        val zero = TransportLeg.parse(obj("legSequence" to 1, "distanceKm" to 0))
        assertEquals("本段 0.0 km", zero.legPlanText)
    }

    @Test
    fun `计价里程有值也必须带直线估算口径 没值说未给`() {
        val q = QuoteResult.parse(obj("amount" to 9.5, "itemFee" to 6.0, "distanceFee" to 3.5, "itemCount" to 3, "distanceKm" to 24.6))
        assertEquals(24.6, q.distanceKm!!, 1e-9)
        assertTrue(q.distanceCaliberText.contains("24.6 km"))
        assertTrue("估算口径必须写明（硬规则：不允许把直线距离说成道路里程）", q.distanceCaliberText.contains("直线距离×路网系数"))
        assertTrue(q.distanceCaliberText.contains("非道路里程"))

        val missing = QuoteResult.parse(obj("amount" to 6.0, "itemFee" to 6.0, "distanceFee" to 0.0, "itemCount" to 3, "distanceKm" to null))
        assertEquals("计价里程未给", missing.distanceCaliberText)
        assertFalse("缺键不许显示 0 km", missing.distanceCaliberText.contains("0"))

        val legacyKey = QuoteResult.parse(obj("amount" to 9.5, "itemCount" to 3, "distance" to 12400))
        assertNull("米制 distance 不许混进计价里程", legacyKey.distanceKm)
        assertEquals("计价里程未给", legacyKey.distanceCaliberText)
    }

    /**
     * ⑬ 实测的第二处同类破口：`Repo.routePreview` 曾经写 `multiNumOrNull("distanceKm", "distance")` 与
     * `("durationMinutes", "minutes")`。`RoutePreviewRespVO` 只有 distanceKm/durationMinutes/provider/available/warning
     * 五个键，`distance`/`minutes` 是客户端自己加的别名，而 `distance` 在 `BusLine` 那一族里是**米**。
     */
    @Test
    fun `route-preview 的两个自造别名键不许再当候选`() {
        val fake = RoutePreview.parse(obj("available" to false, "distance" to 12400, "minutes" to 35))
        assertNull("后端不发 distance，它出现就说明报文不是这个契约", fake.distanceKm)
        assertNull("后端不发 minutes 这个键", fake.durationMinutes)

        val real = RoutePreview.parse(
            obj("available" to true, "distanceKm" to 18.2, "durationMinutes" to 44, "provider" to "amap"),
        )
        assertEquals(18.2, real.distanceKm!!, 1e-9)
        assertEquals(44, real.durationMinutes)
        assertFalse("provider=amap 才是真实路网", real.estimated)
        val downgraded = RoutePreview.parse(obj("distanceKm" to 18.2, "provider" to "euclidean"))
        assertTrue(downgraded.estimated)
        // 缺 durationMinutes 键：不许从别的字段反推一个时长出来
        assertNull(downgraded.durationMinutes)
    }
}
