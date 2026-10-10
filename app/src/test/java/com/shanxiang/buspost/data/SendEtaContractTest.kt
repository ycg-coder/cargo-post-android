package com.shanxiang.buspost.data

import com.shanxiang.buspost.ServerTest
import com.shanxiang.buspost.TestHost
import com.shanxiang.buspost.core.map.RouteRepository
import com.shanxiang.buspost.data.model.SendOrder
import com.shanxiang.buspost.data.model.TransportLeg
import com.shanxiang.buspost.data.repo.Repo
import com.shanxiang.buspost.data.store.Prefs
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 第 ⑫ 轮：§38「ETA 不许 null→0」在**货运侧**的收口（审计 §71，§68 数据源迁移）。
 *
 * fixture 的键名逐个抄自 2026-09-29 实测的后端源码，不是照客户端模型编的：
 * - `AppSendOrderRespVO`：`etaMinutes`(:96) 只在 `estimatedArrivalTime` 晚于当前时间时才写
 *   （`AppSendController:246-250`），而且**只有 `/send/track` 走 `fillEta`**(:156)，
 *   `/send/page` 走的是 `fillCarrierBatch`(:142) —— 所以列表项里根本没有 `etaMinutes` 键；
 * - `carrierEtaMinutes`(:106) 是 `GeoDistanceUtil` 的 Haversine ÷ 25km/h 估算(:81-95)，
 *   车辆无位置或真实上报过期时后端整块不填(:443-457)；
 * - `AppSendLegRespVO`（用户侧 `/send/legs`）与 `AppDriverLegRespVO`（司机侧 `/driver/legs`）
 *   两个段契约**都没有** `etaMinutes`/`eta` 键，只有 `estimatedDeparture/estimatedArrival` 绝对时间。
 */
class SendEtaContractTest : ServerTest() {

    /** 显式 null 与"缺键"是两种契约态，都要能构造出来 */
    private fun obj(vararg pairs: Pair<String, Any?>): JSONObject {
        val o = JSONObject()
        pairs.forEach { (k, v) -> o.put(k, v ?: JSONObject.NULL) }
        return o
    }

    @Before
    fun clearRouteCache() = RouteRepository.clear()

    @Test
    fun `send page 的列表项没有 etaMinutes 键，班次 ETA 必须是 null 而不是偷承运估算`() {
        // 真实 /send/page 形状：fillCarrierBatch 只补 carrier* 与车牌/站点名，etaMinutes 键压根不出现
        val s = SendOrder.parse(
            obj(
                "id" to 9001,
                "orderNo" to "YP20260929001",
                "status" to 3,
                "statusName" to "已发车",
                "goodsName" to "土鸡蛋 2 箱",
                "vehiclePlate" to "渝A·8K231",
                "shiftCode" to "K1-0830",
                "targetStation" to "石桥驿站",
                "pickupCode" to "202609",
                "carrierLongitude" to 106.5432,
                "carrierLatitude" to 29.5688,
                "carrierDistanceKm" to 3.4,
                "carrierEtaMinutes" to 8,
                "carrierLocationSource" to "REAL_FRESH",
                "carrierApproaching" to true,
            ),
        )
        assertNull("/send/page 不填 etaMinutes，旧写法 multiNum(...) 会读成 0", s.etaMinutes)
        // 旧实现把 carrierEtaMinutes 列成了 etaMinutes 的别名键 ⇒ 直线估算会被当成班次 ETA 显示
        assertEquals(8, s.carrierEtaMinutes)
        assertTrue(s.isArriving)
        assertEquals("REAL_FRESH", s.dataSource)
    }

    @Test
    fun `send track 的 etaMinutes 是显式 null 时不许塌成 0`() {
        // fillEta:246-250 —— estimatedArrivalTime 不在未来（已过站/未排到达时间）时 setEtaMinutes 根本不调用
        val past = SendOrder.parse(
            obj(
                "id" to 9002, "orderNo" to "YP20260929002", "status" to 3,
                "estimatedArrivalTime" to "2026-09-29 10:00:00", "etaMinutes" to null,
                "targetStation" to "云山村口站",
            ),
        )
        assertNull(past.etaMinutes)
        assertFalse("null 不等于『还有一分钟都不到』", past.isArriving)

        val future = SendOrder.parse(
            obj(
                "id" to 9003, "orderNo" to "YP20260929003", "status" to 3,
                "estimatedArrivalTime" to "2026-09-29 15:00:00", "etaMinutes" to 26,
            ),
        )
        assertEquals(26, future.etaMinutes)
    }

    @Test
    fun `carrier 块整块缺失时逐项都是 null，后端真给 0 就原样透传`() {
        val bare = SendOrder.parse(obj("id" to 9004, "orderNo" to "YP20260929004", "status" to 1))
        assertNull(bare.carrierEtaMinutes)
        assertNull(bare.etaMinutes)
        assertEquals(0.0, bare.carrierDistanceKm, 0.0001)
        assertFalse(bare.isArriving)

        // 后端 estimateMinutes 有 Math.max(1,…) 与"坐标缺失整块不填"两条路，0 只可能来自别的口径；
        // 客户端不替后端改数，也不把 0 当成"没给"
        val zero = SendOrder.parse(obj("id" to 9005, "orderNo" to "YP20260929005", "carrierEtaMinutes" to 0))
        assertEquals(0, zero.carrierEtaMinutes)
        assertFalse("0 分钟不落在 1..10 的提醒窗口里", zero.isArriving)
    }

    @Test
    fun `isArriving 认后端旗标，不自己拿缺键的 0 判窗口`() {
        val flagged = SendOrder.parse(
            obj("id" to 9006, "orderNo" to "YP20260929006", "carrierApproaching" to true),
        )
        assertTrue(flagged.isArriving)
        val stale = SendOrder.parse(
            obj(
                "id" to 9007, "orderNo" to "YP20260929007",
                "carrierEtaMinutes" to 4, "carrierLocationSource" to "REAL_STALE",
            ),
        )
        // 分钟数照常透传，"位置可能过期"由 carrierLocationSource 表达，页面/提醒自己决定要不要用
        assertEquals(4, stale.carrierEtaMinutes)
        assertEquals("REAL_STALE", stale.carrierLocationSource)
    }

    @Test
    fun `司机段契约没有到站分钟键，其余字段必须逐个读到`() {
        // AppDriverLegRespVO 的真实键名：legSequence/fromStationName/toStationName/status/statusName/
        // estimatedDeparture/estimatedArrival/actualArrival/plateNo/distanceKm/durationMinutes/
        // navigationSource/handoverRequired/handoverId/handoverStatusName —— 没有 etaMinutes
        val leg = TransportLeg.parse(
            obj(
                "id" to 771,
                "orderId" to 9008,
                "orderNo" to "YP20260929008",
                "legSequence" to 2,
                "fromStationId" to 11,
                "fromStationName" to "县城客运站",
                "toStationId" to 12,
                "toStationName" to "石桥驿站",
                "status" to 7,
                "statusName" to "运输中",
                "estimatedDeparture" to "2026-09-29 09:40:00",
                "estimatedArrival" to "2026-09-29 10:20:00",
                "vehicleId" to 5,
                "plateNo" to "渝A·8K231",
                "driverName" to "王有德",
                "distanceKm" to 12.4,
                "durationMinutes" to 40,
                "navigationSource" to "AMAP",
                "handoverRequired" to true,
                "handoverId" to 66,
                "handoverStatusName" to "待交接",
            ),
        )
        assertNull("审计 §71：司机段契约没有 etaMinutes，旧写法读成 0 后页面静默隐藏，用户分不清『没下发』和『没到时间』", leg.etaMinutes)
        assertEquals("771", leg.legId)
        assertEquals(2, leg.seq)
        assertEquals("县城客运站", leg.fromStation)
        assertEquals("石桥驿站", leg.toStation)
        assertEquals(7, leg.status)
        assertEquals("运输中", leg.statusName)
        assertEquals("渝A·8K231", leg.plate)
        assertEquals("王有德", leg.driverName)
        assertEquals(12.4, leg.distanceKm!!, 0.0001)
        assertEquals(40, leg.durationMinutes)
        assertEquals("AMAP", leg.navigationSource)
        assertTrue(leg.handoverRequired)
        assertEquals(66L, leg.handoverId)
        assertEquals("待交接", leg.handoverStatusName)
        assertEquals("YP20260929008", leg.orderNo)
    }

    @Test
    fun `用户段契约同样没有到站分钟键`() {
        // AppSendLegRespVO：legSequence/fromStationName/toStationName/status/statusName/
        // estimatedArrival/actualArrival/driverName/plateNo —— 同样没有 etaMinutes
        val leg = TransportLeg.parse(
            obj(
                "legSequence" to 1,
                "fromStationName" to "云山村口站",
                "toStationName" to "县城客运站",
                "status" to 2,
                "statusName" to "运输中",
                "estimatedArrival" to null,
                "driverName" to "李有田",
                "plateNo" to "渝B·5M107",
            ),
        )
        assertNull(leg.etaMinutes)
        assertEquals("云山村口站 → 县城客运站", "${leg.fromStation} → ${leg.toStation}")
        assertEquals("运输中", leg.statusLabel)
        assertFalse(leg.done)
    }

    @Test
    fun `trackParcel 的 etaMinutes 缺键时是 null，页面副券显示破折号而不是 0`() = runBlocking {
        TestHost.strictBackend()
        // /send/track 的 data 就是 AppSendOrderRespVO，这里复刻"未分配/已过期"那一支：etaMinutes 显式 null
        respondData(
            """{"id":9009,"orderNo":"YP20260929009","status":1,"statusName":"已创建",
               "goodsName":"土蜂蜜 1 箱","etaMinutes":null,"targetStation":null,"vehiclePlate":null}"""
        )
        val trace = Repo.trackParcel("YP20260929009").value!!
        assertNull(trace.etaMinutes)
        assertEquals("YP20260929009", trace.orderNo)
        assertEquals("已创建", trace.statusName)
    }

    @Test
    fun `routeGuide 没有道路耗时也没有 etaMinutes 键时不许给 0 分钟`() = runBlocking {
        TestHost.strictBackend()
        Prefs.driverId = 9L
        // 只有站点与转向、没有任何道路里程/耗时，也没有旧的 etaMinutes 键
        respondData(
            """{"routeProvider":"amap","coordinateSystem":"GCJ-02",
               "stops":[{"id":11,"stationName":"县城客运站","latitude":29.60,"longitude":106.60},
                        {"id":12,"stationName":"白朝村驿站","latitude":29.65,"longitude":106.65}],
               "steps":[{"text":"沿滨江路向北","distanceMeters":320,"current":true}]}"""
        )
        val guide = Repo.routeGuide().value!!
        assertNull("旧写法 multiNum(...).toInt() 会产出 0 分钟，HUD 上『全程约 0 分钟』是最像真话的假话", guide.etaMinutes)
        assertEquals(0, guide.roadEtaSeconds)
    }

    @Test
    fun `route-preview 不可用时里程与时长都是 null，不许显示 0 km 0 分钟`() = runBlocking {
        TestHost.strictBackend()
        // RoutePreviewRespVO:15-19 写明"不可用时为 null"；这个契约里从来没有 etaMinutes 键
        respondData(
            """{"available":false,"distanceKm":null,"durationMinutes":null,
               "provider":"euclidean","warning":"高德配额用尽，已降级为直线估算"}"""
        )
        val r = Repo.routePreview(11L, 12L).value!!
        assertNull(r.distanceKm)
        assertNull(r.durationMinutes)
        assertTrue("provider 非 amap 必须让页面知道这是估算", r.estimated)
    }

    @Test
    fun `订单与段都不接受自造别名 eta，真实键照常工作`() {
        // 2026-09-30 实测：AppSendOrderRespVO 有 etaMinutes(:96) 而没有 eta，
        // AppDriverLegRespVO / AppSendLegRespVO 两个段契约连 etaMinutes 都没有 —— 别名两侧都无生产者
        val order = SendOrder.parse(obj("id" to 9101, "orderNo" to "YP20260930101", "status" to 3,
            "etaMinutes" to null, "eta" to 25))
        assertNull(order.etaMinutes)
        assertEquals(25, SendOrder.parse(obj("id" to 9102, "orderNo" to "YP20260930102", "status" to 3,
            "etaMinutes" to 25)).etaMinutes)
        assertNull(TransportLeg.parse(obj("legId" to "L11", "eta" to 9)).etaMinutes)
        assertEquals(9, TransportLeg.parse(obj("legId" to "L12", "etaMinutes" to 9)).etaMinutes)
    }

    @Test
    fun `轨迹报文只来自造别名 eta 时全程分钟仍是 null`() = runBlocking {
        TestHost.strictBackend()
        respondData(
            """{"id":9010,"orderNo":"YP20260930010","status":3,"statusName":"运输中",
               "goodsName":"土蜂蜜 2 箱","eta":30}"""
        )
        assertNull(Repo.trackParcel("YP20260930010").value!!.etaMinutes)
    }

    @Test
    fun `路线引导不许拿自造别名 eta 补出全程分钟`() = runBlocking {
        TestHost.strictBackend()
        Prefs.driverId = 9L
        // AppDriverRouteRespVO 里全程耗时的真实键是 routeDurationSeconds(:48)；
        // 这里既不给它、也不给 etaMinutes，只给一个后端从来不发的 eta
        respondData(
            """{"routeProvider":"amap","coordinateSystem":"GCJ-02","eta":25,
               "stops":[{"id":11,"stationName":"县城客运站","latitude":29.60,"longitude":106.60},
                        {"id":12,"stationName":"白朝村驿站","latitude":29.65,"longitude":106.65}],
               "steps":[{"text":"沿滨江路向北","distanceMeters":320,"current":true}]}"""
        )
        val guide = Repo.routeGuide().value!!
        assertNull(guide.etaMinutes)
        assertEquals(0, guide.roadEtaSeconds)
    }
}
