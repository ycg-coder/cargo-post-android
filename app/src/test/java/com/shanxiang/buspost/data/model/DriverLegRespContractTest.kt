package com.shanxiang.buspost.data.model

import com.shanxiang.buspost.core.ui.BusTone
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `GET /driver/legs` 报文字段对照（审计 §36）。
 *
 * fixture 的键名逐个抄自后端 `AppDriverLegRespVO` + `DriverAppServiceImpl#toLegVO:1610-1640`，
 * 不是抄 Android 的候选名列表 —— 旧解析只认 `seq/fromStation/toStation/vehiclePlate`，
 * 后端下发的是 `legSequence/fromStationName/toStationName/plateNo`，于是司机段卡显示成"→"、无车牌、序号并列。
 * 这类 bug 不报错，只会静默取默认值，所以逐字段断言"不是靠默认值蒙过去"。
 */
class DriverLegRespContractTest {

    private val realVo = """
        {
          "id": 7001,
          "orderId": 5100,
          "orderNo": "YP20260929007",
          "legSequence": 2,
          "fromStationId": 31,
          "fromStationName": "石桥驿站",
          "toStationId": 44,
          "toStationName": "江北机场站",
          "status": 8,
          "statusName": "已到达终点",
          "estimatedDeparture": "2026-09-29T08:30:00",
          "estimatedArrival": "2026-09-29T09:40:00",
          "actualArrival": "2026-09-29T09:36:00",
          "vehicleId": 12,
          "plateNo": "渝A·88888",
          "distanceKm": 21.4,
          "durationMinutes": 55,
          "navigationSource": "AMAP",
          "handoverRequired": true,
          "handoverId": 9002,
          "handoverStatusName": "前序已到达"
        }
    """.trimIndent()

    @Test
    fun `后端真实键名逐个能读到 不靠默认值蒙`() {
        val leg = TransportLeg.parse(JSONObject(realVo))
        assertEquals("7001", leg.legId)
        assertEquals("YP20260929007", leg.orderNo)
        assertEquals(2, leg.seq)
        assertEquals("石桥驿站", leg.fromStation)
        assertEquals("江北机场站", leg.toStation)
        assertEquals("渝A·88888", leg.plate)
        assertEquals(8, leg.status)
        assertEquals("已到达终点", leg.statusName)
        assertEquals(21.4, leg.distanceKm!!, 1e-9)
        assertEquals(55, leg.durationMinutes)
        assertEquals("AMAP", leg.navigationSource)
        assertTrue("handoverRequired 必须解析（段动作分支靠它）", leg.handoverRequired)
        assertEquals(9002L, leg.handoverId)
        assertEquals("前序已到达", leg.handoverStatusName)
    }

    @Test
    fun `段状态文案与配色走唯一表 完成只认 11`() {
        val leg = TransportLeg.parse(JSONObject(realVo))
        assertEquals("已到达终点", leg.statusLabel)
        assertTrue(!leg.done)
        assertTrue(leg.active)
        // 后端给了 statusName 就用后端的
        val renamed = TransportLeg.parse(JSONObject("""{"status":8,"statusName":"到站啦"}"""))
        assertEquals("到站啦", renamed.statusLabel)
        assertEquals(BusTone.PRIMARY, leg.statusTone)
    }

    /** 报文里没有的键必须留空/给哨兵，不能编一个看起来像真值的值 */
    @Test
    fun `后端不下发段级几何 解析结果就是空`() {
        val leg = TransportLeg.parse(JSONObject(realVo))
        assertTrue(leg.navigationPolyline.isEmpty())
        assertEquals(null, leg.fromGeo)
        assertEquals(null, leg.toGeo)
        // VO 里没有 driverName：为空就是空，页面不得填"张师傅"这类占位（§12/§14）
        assertEquals("", leg.driverName)
        assertEquals("", leg.transferStation)
    }

    @Test
    fun `空报文只说读不到 不冒充已规划`() {
        val leg = TransportLeg.parse(JSONObject("{}"))
        assertEquals(-1, leg.status)
        assertEquals(STATUS_UNAVAILABLE_LABEL, leg.statusLabel)
        assertTrue(!leg.done && !leg.active)
        assertEquals(BusTone.NEUTRAL, leg.statusTone)
        assertTrue(!leg.handoverRequired)
        // ⑬：里程与时长在契约里都可空（AppDriverLegRespVO:58-62 BigDecimal/Integer），缺键只能是 null
        assertEquals(null, leg.distanceKm)
        assertEquals(null, leg.durationMinutes)
    }

    /**
     * 显式 null 与缺键是同一个结论（后端没测算出这一段），但都要单独跑一遍：
     * 旧写法 `multiNum("distanceKm","distance")` 在两种形态下都返回 0.0，页面会读成"这段 0 公里"。
     * 顺带钉住"不许再认 `distance` 这个别名" —— 本工程里 `distance` 一律是**米**（Station/NavStep），
     * 挂在公里字段上是单位事故，不是兼容。
     */
    @Test
    fun `段里程与时长缺键或显式 null 都不许塌成 0`() {
        val explicitNull = TransportLeg.parse(
            JSONObject("""{"legSequence":1,"distanceKm":null,"durationMinutes":null,"navigationSource":"ESTIMATED"}"""),
        )
        assertEquals(null, explicitNull.distanceKm)
        assertEquals(null, explicitNull.durationMinutes)
        assertTrue("navigationSource=ESTIMATED 必须被认成估算（VO :64 原文要求前端标注）", explicitNull.estimated)

        val legacyAlias = TransportLeg.parse(JSONObject("""{"legSequence":1,"distance":8000}"""))
        assertEquals("米制的 distance 不许被当成 distanceKm 读进来", null, legacyAlias.distanceKm)

        val amap = TransportLeg.parse(JSONObject("""{"distanceKm":21.4,"durationMinutes":55,"navigationSource":"AMAP"}"""))
        assertTrue(!amap.estimated)
        assertEquals(55, amap.durationMinutes)
    }

    /** 用户侧 `/send/legs` 的段契约（AppSendLegRespVO）逐字段核过：没有里程/时长/ETA 任何一键 */
    @Test
    fun `用户侧段报文本来就没有里程与时长 读出来只能是 null`() {
        val leg = TransportLeg.parse(
            JSONObject(
                """{"legSequence":1,"fromStationName":"石桥驿站","toStationName":"江北机场站",
                   "status":2,"statusName":"运输中","estimatedArrival":"2026-09-29T09:40:00",
                   "actualArrival":null,"driverName":"陈师傅","plateNo":"渝A·8K231"}"""
            ),
        )
        assertEquals("石桥驿站", leg.fromStation)
        assertEquals("江北机场站", leg.toStation)
        assertEquals("渝A·8K231", leg.plate)
        assertEquals(null, leg.distanceKm)
        assertEquals(null, leg.durationMinutes)
        assertEquals(null, leg.etaMinutes)
    }
}
