package com.shanxiang.buspost.data

import com.shanxiang.buspost.ServerTest
import com.shanxiang.buspost.TestHost
import com.shanxiang.buspost.core.ui.StopState
import com.shanxiang.buspost.data.model.LocationSource
import com.shanxiang.buspost.data.repo.Repo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F-3 ⑩/⑮：用户实时物流页的"车辆位置 / 上报时间 / 新鲜度"三格，只许讲后端真给的东西。
 *
 * 契约源（逐字段抄后端，不是照客户端模型编）：
 * - `/send/track` 就是 `AppSendOrderRespVO`：`carrierLongitude:100` / `carrierLatitude:102` /
 *   `carrierEtaMinutes:106` / `carrierLocationSource:108` / `carrierArrived:114` / `carrierTaskStatus:118` /
 *   `carrierArrivedTime:120`；`AppSendController.fillEta:252/:262` 在详情上先填作业进度再填车辆位置，
 *   新鲜度只发 `REAL_FRESH` / `REAL_STALE` 两个值（:474）；
 * - 同一个 VO 里的站点坐标是另一组键 `servicePointLongitude:46` / `servicePointLatitude:48` —— 它是**站点**，
 *   不是车，拿它顶车辆位置就是画一个假位置；
 * - 位置上报时间在寄货 VO 里**没有任何键**（只有 `createTime:84`、`estimatedArrivalTime:94`、`carrierArrivedTime:120`，
 *   三者都不是"这次坐标是什么时候上报的"），所以缺键时 `lastReportTime` 必须是空串，由页面念"上报时间未给"；
 * - `/product-order/trace` 是另一套键：`currentLongitude:68` / `currentLatitude:70` / `lastReportTime:72`，
 *   由 `ProductOrderServiceImpl:377-379` 从 `vehicle_location` 的真实上报行填 —— 它**有生产者**，
 *   客户端不读就等于在商城单上永远谎报"暂无实时位置"；但它没有新鲜度键，所以只能到 UNKNOWN。
 */
class UserTrackingContractTest : ServerTest() {

    private fun track(dataJson: String) = runBlocking {
        TestHost.strictBackend()
        respondData(dataJson)
        Repo.trackParcel("YP20261001001").value!!
    }

    private fun productTrace(dataJson: String) = runBlocking {
        TestHost.strictBackend()
        respondData(dataJson)
        Repo.productOrderTrace(7101L).value!!
    }

    @Test
    fun `承运车辆坐标读后端真实键 carrierLatitude 与 carrierLongitude`() {
        val trace = track(
            """{"id":9101,"orderNo":"YP20261001001","status":3,"statusName":"已发车",
               "carrierLatitude":29.5688,"carrierLongitude":106.5432,"carrierLocationSource":"REAL_FRESH"}"""
        )
        assertEquals(29.5688, trace.vehicleGeo!!.lat, 1e-9)
        assertEquals(106.5432, trace.vehicleGeo!!.lng, 1e-9)
        assertEquals("REAL_FRESH", trace.dataSource)
        assertEquals(LocationSource.REAL_FRESH, trace.locationSource)
        assertEquals("实时", trace.locationSource.knownLabel)
    }

    @Test
    fun `REAL_STALE 不升格成实时`() {
        // AppSendController:474 的分级：真实上报但超过 5 分钟 = REAL_STALE，坐标照发，新鲜度另说
        val trace = track(
            """{"id":9107,"orderNo":"YP20261001007","carrierLatitude":29.5,"carrierLongitude":106.5,
               "carrierLocationSource":"REAL_STALE"}"""
        )
        assertEquals(LocationSource.REAL_STALE, trace.locationSource)
        assertEquals("位置可能过期", trace.locationSource.knownLabel)
    }

    @Test
    fun `后端没给承运坐标时是 null，不是 0,0 也不是别的什么点`() {
        // 已创建/未发车那一支：carrier* 整块不填（AppSendController:447-449 直接 return）
        val trace = track(
            """{"id":9102,"orderNo":"YP20261001002","status":1,"statusName":"已创建",
               "carrierLatitude":null,"carrierLongitude":null,"carrierLocationSource":null}"""
        )
        assertNull(trace.vehicleGeo)
        assertEquals("", trace.dataSource)
        assertEquals(LocationSource.UNKNOWN, trace.locationSource)
        // UNKNOWN 没有文案：页面拿不到 knownLabel 时才许写"新鲜度未给"，不许它 fallback 成"实时"
        assertNull(trace.locationSource.knownLabel)
    }

    @Test
    fun `站点坐标不会被当成车辆位置`() {
        val trace = track(
            """{"id":9103,"orderNo":"YP20261001003","status":1,
               "servicePointLatitude":29.7,"servicePointLongitude":106.4}"""
        )
        assertNull(trace.vehicleGeo)
    }

    @Test
    fun `承运坐标显式为 0 时不许画成一个真的点`() {
        // (0,0) 在几内亚湾，不是重庆的任何一个站点；RealRoadRouteValidator 同一口径拒绝它
        val trace = track(
            """{"id":9104,"orderNo":"YP20261001004","carrierLatitude":0.0,"carrierLongitude":0.0}"""
        )
        assertNull(trace.vehicleGeo)
    }

    @Test
    fun `自造别名键 positionSource 不再被采纳`() {
        // 两仓都没有这个键的生产者（后端 VO 0 命中、DemoData 0 命中），留着就是给假数据开门
        val trace = track(
            """{"id":9108,"orderNo":"YP20261001008","positionSource":"REAL_FRESH",
               "carrierLatitude":29.5,"carrierLongitude":106.5}"""
        )
        assertEquals("", trace.dataSource)
        assertEquals(LocationSource.UNKNOWN, trace.locationSource)
    }

    @Test
    fun `位置上报时间缺键时是空串，不许回落成订单创建时间`() {
        val trace = track(
            """{"id":9105,"orderNo":"YP20261001005","createTime":"2026-10-01 09:12:00",
               "carrierLatitude":29.5,"carrierLongitude":106.5}"""
        )
        assertEquals("", trace.lastReportTime)
    }

    @Test
    fun `位置上报时间在场时按 epoch 毫秒口径转成可读串`() {
        // 后端 LocalDateTime 走 TimestampLocalDateTimeSerializer（EpochMillis），不是 ISO 串。
        // 断言不钉具体时分：JVM 用的是默认时区，钉死墙钟串会让用例在换机器的时区时假红。
        val trace = track(
            """{"id":9106,"orderNo":"YP20261001006","createTime":"2026-10-01 09:12:00",
               "lastReportTime":1759255500000,"carrierLatitude":29.5,"carrierLongitude":106.5}"""
        )
        assertTrue(
            "上报时间应是 yyyy-MM-dd HH:mm 可读串，实际是「${trace.lastReportTime}」",
            Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}""").matches(trace.lastReportTime),
        )
        assertNotEquals("2026-10-01 09:12", trace.lastReportTime)
    }

    @Test
    fun `商城轨迹的 currentLatitude 有真实生产者，必须读得到车`() {
        val trace = productTrace(
            """{"orderId":7101,"orderNo":"SC20261001001","status":2,"statusName":"运输中",
               "currentLatitude":29.61,"currentLongitude":106.49,"lastReportTime":1759255500000}"""
        )
        assertEquals(29.61, trace.vehicleGeo!!.lat, 1e-9)
        assertEquals(106.49, trace.vehicleGeo!!.lng, 1e-9)
        assertTrue(trace.lastReportTime.isNotEmpty())
    }

    @Test
    fun `商城轨迹没有新鲜度键，位置读得到也不许说成实时`() {
        // AppProductOrderTraceRespVO 整本没有 locationSource/dataSource 键
        val trace = productTrace(
            """{"orderId":7102,"orderNo":"SC20261001002","status":2,
               "currentLatitude":29.61,"currentLongitude":106.49}"""
        )
        assertEquals(LocationSource.UNKNOWN, trace.locationSource)
        assertNull(trace.locationSource.knownLabel)
    }

    @Test
    fun `当前任务读后端真实键 carrierTaskStatus 与 carrierArrivedTime`() {
        // fillEta:252 → fillCarrierArrival:395 按派单经停明细状态给名字；:405 的时间是明细 update_time
        val trace = track(
            """{"id":9109,"orderNo":"YP20261001009","statusName":"已发车",
               "carrierTaskStatus":"派送中","carrierArrivedTime":1759255500000,"carrierArrived":true}"""
        )
        assertEquals("派送中", trace.carrierTaskStatus)
        assertTrue(trace.carrierTaskTime.isNotEmpty())
        assertTrue(trace.driverArrived)
    }

    @Test
    fun `未派单时当前任务是空串，不许拿订单状态名顶一个`() {
        // items 为空时 fillEta:215-217 直接 return：carrier* 整块不下发，statusName 却不缺席。
        // 把 statusName("已发车") 当成"当前任务"给用户看，就是编一个后端没说过的作业状态。
        val trace = track(
            """{"id":9110,"orderNo":"YP20261001010","status":3,"statusName":"已发车"}"""
        )
        assertEquals("", trace.carrierTaskStatus)
        assertEquals("", trace.carrierTaskTime)
        assertNotEquals(trace.statusName, trace.carrierTaskStatus)
        assertFalse(trace.driverArrived)
    }

    /**
     * F-3 ⑬：轨迹节点完成态的真键只有 `done`（小程序 11 处、演示面 6 处都在写它）。
     * `finished` 三方 0 命中，`completed` 只在后端 bigscreen 的 admin 汇总里出现（BigScreenServiceImpl:120），
     * 不在这个报文里 ⇒ 只给别名时节点必须还是"未开始"，否则用户侧的时间线凭空多出一个已完成。
     */
    @Test
    fun `只给 finished 与 completed 别名的轨迹节点不算完成`() {
        val trace = track(
            """{"id":9110,"orderNo":"YP20261001010","status":3,
               "nodes":[{"label":"已揽收","time":"2026-10-01 09:12","finished":true},
                        {"label":"派送中","time":"2026-10-01 10:02","completed":true,"current":true},
                        {"label":"已签收","time":"2026-10-01 11:30","done":true}]}"""
        )
        assertEquals(3, trace.nodes.size)
        assertEquals("finished 不是这个报文的键", StopState.PENDING, trace.nodes[0].state)
        assertEquals("completed 也不是", StopState.CURRENT, trace.nodes[1].state)
        assertEquals("真实键 done 才产生完成态", StopState.DONE, trace.nodes[2].state)
    }
}
