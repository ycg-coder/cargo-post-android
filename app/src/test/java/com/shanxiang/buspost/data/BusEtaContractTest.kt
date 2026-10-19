package com.shanxiang.buspost.data

import com.shanxiang.buspost.data.model.Arrangement
import com.shanxiang.buspost.data.model.BusVehicle
import com.shanxiang.buspost.data.model.LocationSource
import com.shanxiang.buspost.data.model.VehicleStatus
import com.shanxiang.buspost.data.model.busLineLinkOf
import com.shanxiang.buspost.data.model.byUserDistance
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §38「ETA 不许 null→0」契约测试。
 *
 * fixture 的键名与码值逐字段照抄 2026-09-29 实测的两个真实端点形状（不是照着客户端模型编的）：
 * - `/bus/nearby` → `AppBusNearbyRespVO.NearbyBus`：status 字符串码，带 locationSource / routeProvider /
 *   lastLocationTime / distanceKm / nearestStation*，**不带** progress、**不带** speedKmh；
 * - `/bus/lines` → 内嵌 `AppBusRespVO`：status 整数（0 空闲 / 1 在途 / 2 停用），带 progress / speedKmh /
 *   etaToNextStationMinutes，**不带**新鲜度与最近站那一组键。
 *
 * 后端给不出 ETA 时是**显式 null**（`AppBusServiceImpl:134`/`:665`/`:719`），而且两条算 ETA 的路都
 * `Math.max(1, …)`（`:132`/`:728`）—— 所以 0 从来不是合法到站分钟，客户端读出来的 0 只可能是自己编的。
 */
class BusEtaContractTest {

    /** 显式 null 与"缺键"是两种不同契约态，都要能构造出来 */
    private fun obj(vararg pairs: Pair<String, Any?>): JSONObject {
        val o = JSONObject()
        pairs.forEach { (k, v) -> o.put(k, v ?: JSONObject.NULL) }
        return o
    }

    /** nearby 端点：在途、真实位置、后端就是给不出到站分钟（下一站未知，`:665`） */
    private fun nearbyNoEta() = BusVehicle.parse(
        obj(
            "busId" to 900104L,
            "plateNo" to "渝A·3Q778",
            "shiftCode" to "K1-1530",
            "routeName" to "K1 县城 ⇄ 云山村",
            "startStation" to "云山村口站",
            "endStation" to "县城客运站",
            "status" to "RUNNING",
            "nextStation" to null,
            "latitude" to 29.5688,
            "longitude" to 106.5432,
            "dataSource" to "REAL",
            "locationSource" to "REAL_FRESH",
            "currentStation" to "石桥驿站",
            "distanceToNextStationKm" to null,
            "etaMinutes" to null,
            "waitDepartureMinutes" to null,
            "routeProvider" to "SCHEDULE",
            "lastLocationTime" to "2026-09-29 11:12:00",
            "updatedAt" to "2026-09-29 11:12:05",
            "distanceKm" to 2.4,
            "nearestStationName" to "石桥驿站",
            "nearestStationEtaMinutes" to null,
            "stopsToNearestStation" to null,
            "nearestStationDistanceKm" to 0.3,
        ),
    )

    @Test
    fun `nearby 在途但后端没给 ETA 时读成 null 不是 0`() {
        val v = nearbyNoEta()
        assertEquals(VehicleStatus.RUNNING, v.status)
        assertNull("后端 setEtaMinutes(null) 不许塌成 0", v.etaMinutes)
        assertNull(v.nearestStationEtaMinutes)
        assertNull(v.waitDepartureMinutes)
        // 副券大数字：没有分钟数就不许出现数字，也不许出现「0」
        assertEquals("—", v.etaValueText)
        assertEquals("", v.etaUnitText)
        assertEquals("到站时间未给", v.etaCaptionText)
        assertFalse(v.etaCaptionText.contains("0 分钟"))
        assertFalse("下一站未识别时不许编一个站名", v.nextStation.isNotEmpty())
    }

    @Test
    fun `nearby 全字段逐字段读到非默认值`() {
        val o = obj(
            "busId" to 900101L,
            "plateNo" to "渝A·8K231",
            "shiftCode" to "K1-0830",
            "routeName" to "K1 县城 ⇄ 云山村",
            "startStation" to "云山村口站",
            "endStation" to "县城客运站",
            "status" to "RUNNING",
            "nextStation" to "石桥驿站",
            "latitude" to 29.5688,
            "longitude" to 106.5432,
            "dataSource" to "REAL",
            "locationSource" to "REAL_FRESH",
            "currentStation" to "白鹤站",
            "distanceToNextStationKm" to 3.6,
            "etaMinutes" to 12,
            "routeProvider" to "AMAP",
            "lastLocationTime" to "2026-09-29 11:12:00",
            "distanceKm" to 1.8,
            "nearestStationName" to "石桥驿站",
            "nearestStationEtaMinutes" to 9,
            "stopsToNearestStation" to 2,
            "nearestStationDistanceKm" to 0.4,
        )
        val v = BusVehicle.parse(o)
        // §49：每个字段都要用互不相同的非默认值，读不到就是契约破了，不能靠"两边都空"蒙过断言
        assertEquals("900101", v.id)
        assertEquals("渝A·8K231", v.plate)
        assertEquals("K1-0830", v.shiftCode)
        assertEquals("K1 县城 ⇄ 云山村", v.lineName)
        assertEquals("云山村口站", v.startStation)
        assertEquals("县城客运站", v.endStation)
        assertEquals(VehicleStatus.RUNNING, v.status)
        assertEquals("石桥驿站", v.nextStation)
        assertEquals(29.5688, v.geo!!.lat, 0.0001)
        assertEquals(106.5432, v.geo!!.lng, 0.0001)
        assertEquals("REAL", v.dataSource)
        assertEquals(LocationSource.REAL_FRESH, v.locationSource)
        assertEquals("白鹤站", v.currentStation)
        assertEquals(3.6, v.distanceToNextStationKm!!, 0.001)
        assertEquals(12, v.etaMinutes)
        assertEquals("AMAP", v.routeProvider)
        assertEquals("2026-09-29 11:12", v.lastLocationTime)
        assertEquals(1.8, v.distanceKm!!, 0.001)
        assertEquals("石桥驿站", v.nearestStationName)
        assertEquals(9, v.nearestStationEtaMinutes)
        assertEquals(2, v.stopsToNearestStation)
        // 大数字优先"离我最近那站"，其次在途到站分钟（小程序 index.js:464-466 同口径）
        assertEquals("9", v.etaValueText)
        assertEquals("分钟", v.etaUnitText)
        assertEquals("约 9 分钟到「石桥驿站」", v.etaCaptionText)
        assertEquals("离你最近的「石桥驿站」还有 2 站", v.stationText)
        assertEquals("3.6 km", v.nextDistanceShortText)
        assertEquals("高德真实路网", v.providerText)
    }

    @Test
    fun `lines 端点整数状态 1 2 0 三种码各有说法`() {
        val running = BusVehicle.parse(
            obj(
                "busId" to 900101L, "plateNo" to "渝A·8K231", "status" to 1,
                "etaMinutes" to 12, "etaToNextStationMinutes" to 8.4,
                "progress" to 76, "speedKmh" to 27,
                "nextStation" to "石桥驿站", "routeName" to "K1 县城 ⇄ 云山村",
                "latitude" to 29.5688, "longitude" to 106.5432, "dataSource" to "REAL",
            ),
        )
        assertEquals(VehicleStatus.RUNNING, running.status)
        assertEquals(76, running.progress)
        assertEquals(0.76f, running.roadProgress!!, 0.0001f)
        // 精确到下一站分钟（Double）优先，取整口径与小程序 detail.js:136 的 Math.max(1, ceil()) 一致
        assertEquals(8.4, running.etaToNextStationMinutes!!, 0.0001)
        assertEquals(9, running.nextStopMinutes)
        assertEquals("约 9 分钟到下一站", running.etaCaptionText)
        assertEquals("9", running.etaValueText)
        assertEquals(27, running.speedKmh)
        // lines 端点根本没有 locationSource 键：新鲜度未给就不许说成"实时"
        assertEquals(LocationSource.UNKNOWN, running.locationSource)
        assertEquals("实时（新鲜度未标）", running.sourceText)

        val idle = BusVehicle.parse(
            obj(
                "busId" to 900102L, "plateNo" to "渝A·6F412", "status" to 0,
                "etaMinutes" to null, "waitDepartureMinutes" to 6, "progress" to null,
                "currentStation" to "兴隆场站", "routeName" to "K1 县城 ⇄ 云山村",
            ),
        )
        assertEquals(VehicleStatus.IDLE, idle.status)
        assertNull(idle.etaMinutes)
        assertEquals(6, idle.waitDepartureMinutes)
        // 「还有多久发车」与「还有多久到站」是两件事，不能混着写（:128-129 注释）
        assertEquals("待发", idle.etaValueText)
        assertEquals("", idle.etaUnitText)
        assertEquals("6 分钟后发车", idle.etaCaptionText)
        assertFalse(idle.etaCaptionText.contains("到站"))

        val offDuty = BusVehicle.parse(
            obj("busId" to 900103L, "plateNo" to "渝B·5M107", "status" to 2),
        )
        // 整数码 2 是"停用"（MonitoringVehicleRespVO:21-22），旧码表把它读成"已到站"是错的
        assertEquals(VehicleStatus.OFF_DUTY, offDuty.status)
        assertEquals("停用", offDuty.statusLabel)
        assertEquals("车辆停用", offDuty.etaCaptionText)
    }

    /**
     * ⑬：`etaMinutes` 这个键名在两个端点说的是两件事，客户端不许一视同仁。
     * - `/bus/nearby`（`fillEta:726-731`）：它就是"到下一站"，而且 nearby **不下发 `progress`**；
     * - `/bus/lines`（`AppBusServiceImpl:130-132`）：它是"剩余进度 × 班次计划时长"= 跑完剩余线路。
     * 所以 lines 形状又没有精确值时，界面只能说"剩余全程约 N 分钟"，大数字位置（那个位置在说"到站"）必须空着。
     */
    @Test
    fun `同一个 etaMinutes 键在两个端点语义不同 只有 nearby 能说成到下一站`() {
        val nearbyShape = BusVehicle.parse(
            obj("busId" to 11L, "plateNo" to "渝A·N0011", "status" to "RUNNING", "etaMinutes" to 12,
                "routeProvider" to "SCHEDULE", "nextStation" to "石桥驿站"),
        )
        assertNull("nearby 的 NearbyBus 没有 etaToNextStationMinutes 这个键", nearbyShape.etaToNextStationMinutes)
        assertEquals(12, nearbyShape.nextStopMinutes)
        assertEquals("约 12 分钟到下一站", nearbyShape.etaCaptionText)
        assertEquals(null, nearbyShape.remainingRouteMinutesText)

        val linesShape = BusVehicle.parse(
            obj("busId" to 12L, "plateNo" to "渝A·L0012", "status" to 1, "etaMinutes" to 64,
                "progress" to 21, "nextStation" to "石桥驿站"),
        )
        assertNull("lines 形状没给精确值时，64 分钟不是到下一站的数", linesShape.nextStopMinutes)
        assertEquals("剩余全程约 64 分钟（按线路进度与班次计划时长估算，不是到下一站）", linesShape.etaCaptionText)
        assertEquals("—", linesShape.etaValueText)
        assertEquals("", linesShape.etaUnitText)
        // 判据钉的是"不许出现肯定式 分钟到下一站"，不是"不许出现这五个字" ——
        // 口径说明里那句"不是到下一站"必须留着，它恰恰是唯一告诉用户这数不是到站数的地方。
        assertFalse(linesShape.etaCaptionText.contains("分钟到下一站"))
        assertTrue(linesShape.etaCaptionText.contains("不是到下一站"))
    }

    @Test
    fun `精确到站分钟向上取整且至少 1 与小程序同口径`() {
        fun minutes(v: Double?) = BusVehicle.parse(
            obj("busId" to 13L, "plateNo" to "渝A·X0013", "status" to 1, "progress" to 50,
                "etaToNextStationMinutes" to v),
        ).nextStopMinutes
        assertEquals(9, minutes(8.4))
        assertEquals(1, minutes(0.2))
        assertEquals(1, minutes(1.0))
        assertNull(minutes(null))
        // 后端真给 0（两条 ETA 算法都 Math.max(1,…) 所以不该出现，但契约态要原样透传）→ 至少 1，不显示"0 分钟"
        assertEquals(1, minutes(0.0))
    }

    @Test
    fun `进度没给就是没给，不许用 ETA 反推`() {
        val v = BusVehicle.parse(
            obj("busId" to 1L, "plateNo" to "渝A·11111", "status" to 1, "etaMinutes" to 30),
        )
        assertNull("旧实现按 1 - eta/40 自编 0-100，ETA 没给时画成『已经到站』", v.progress)
        assertNull(v.roadProgress)
        val given = BusVehicle.parse(
            obj("busId" to 1L, "plateNo" to "渝A·11111", "status" to 1, "etaMinutes" to 30, "progress" to 118),
        )
        // 后端越界值按 0-100 收，但不许反过来造
        assertEquals(1.0f, given.roadProgress!!, 0.0001f)
    }

    @Test
    fun `司机位置端点只有三个键 不许被读成在途`() {
        // GET /transport/driver/position → AppDriverPositionRespVO：longitude / latitude / dataSource，
        // 没有 status、没有 nextStation、没有 ETA（AppDriverController.java:258-263 实测）
        val v = BusVehicle.parse(
            obj("longitude" to 106.5432, "latitude" to 29.5688, "dataSource" to "REAL"),
        )
        assertEquals(29.5688, v.geo!!.lat, 0.0001)
        assertEquals("REAL", v.dataSource)
        assertTrue("报文没给状态，模型不许给一个看起来像真值的标签", !v.positionOnlyTag.contains("在途"))
        assertEquals("位置来源 REAL（未给状态）", v.positionOnlyTag)
        assertTrue("下一站不在这份报文里，页面不许拼它", v.nextStation.isEmpty())
        // dataSource 缺失 = 来源未给，也不许反过来挑一个默认标签冒充
        val blank = BusVehicle.parse(obj("latitude" to 29.5, "longitude" to 106.5))
        assertEquals("位置来源未给", blank.positionOnlyTag)
    }

    @Test
    fun `没有经纬度不塌成北纬0度东经0度`() {
        val v = BusVehicle.parse(
            obj("busId" to 2L, "plateNo" to "渝A·22222", "status" to 0, "latitude" to null, "longitude" to null),
        )
        assertNull(v.geo)
        // status=NO_LOCATION / locationSource=NO_LOCATION 在真实接口上是"防御性"取值：
        // nearby 把无坐标的车直接跳过了（AppBusServiceImpl:392-394），停用（2）也在 :387-389 跳过，
        // 而 lines 端点用整数码、压根没有新鲜度键。枚举留着不删（旧数据/新端点都可能给），但页面不许靠它兜底。
        val noLoc = BusVehicle.parse(
            obj(
                "busId" to 3L, "plateNo" to "渝A·33333", "status" to "NO_LOCATION",
                "locationSource" to "NO_LOCATION",
            ),
        )
        assertEquals(VehicleStatus.NO_LOCATION, noLoc.status)
        assertEquals("暂无定位，无法预估", noLoc.etaCaptionText)
        assertEquals("位置暂不可用", noLoc.sourceText)
    }

    @Test
    fun `按离用户远近排序且没给距离的排最后`() {
        val far = BusVehicle.parse(obj("busId" to 4L, "plateNo" to "远", "distanceKm" to 5.0))
        val near = BusVehicle.parse(obj("busId" to 5L, "plateNo" to "近", "distanceKm" to 1.0))
        val unknown = BusVehicle.parse(obj("busId" to 6L, "plateNo" to "没给距离"))
        val sorted = listOf(far, unknown, near).byUserDistance().map { it.plate }
        // 与后端同口径：nullsLast（AppBusServiceImpl:436-437），客户端不许把缺距离当 0 km 排到最前
        assertEquals(listOf("近", "远", "没给距离"), sorted)
    }

    @Test
    fun `估算来源不许冒充真实路网`() {
        val schedule = BusVehicle.parse(obj("busId" to 7L, "plateNo" to "S", "routeProvider" to "SCHEDULE"))
        assertEquals("按班次计划时长估算", schedule.providerText)
        // 直线估算（老 VO 注释里出现过 EUCLIDEAN）必须显式说"没识别"，更不许出现"真实"
        val euclid = BusVehicle.parse(obj("busId" to 8L, "plateNo" to "E", "routeProvider" to "EUCLIDEAN"))
        assertFalse(euclid.providerText.contains("真实"))
        assertTrue(euclid.providerText.contains("未识别"))
        val blank = BusVehicle.parse(obj("busId" to 9L, "plateNo" to "B"))
        // 后端没给来源时页面就什么都不能说（线路来源在 NearbyLine.dataSource，不在车辆上）
        assertEquals("", blank.providerText)
    }

    @Test
    fun `到站提醒的真实数据源根本没有 etaMinutes 这个键`() {
        // 实测 AppSendArrangementRespVO：11 个字段里既没有 etaMinutes，也没有 targetStation/shiftCode/dataSource
        val a = Arrangement.parse(
            obj(
                "orderNo" to "YP20260929001",
                "status" to 3,
                "statusName" to "已排班",
                "boardingStationName" to "县城客运站",
                "alightingStationName" to "石桥驿站",
                "planId" to 66,
                "planStatus" to 1,
                "vehiclePlateNo" to "渝A·8K231",
            ),
        )
        assertNull("审计 §68：/send/arrangements 不返回 ETA，到站提醒在真接口上是恒不响的，旧实现把它读成 0 只是把『没数据』伪装成『还没到时间』", a.etaMinutes)
        assertEquals("YP20260929001", a.orderNo)
        assertEquals("渝A·8K231", a.plate)
        // AlertEngine / ParcelScreen 的过滤口径：etaMinutes 为 null 的行必须被滤掉，而不是当成 0 分钟
        assertTrue(listOf(a).filter { it.etaMinutes != null && it.etaMinutes in 1..10 }.isEmpty())
    }

    @Test
    fun `后端真给 0 分钟时原样透传，不改成 null 也不改成 1`() {
        val v = BusVehicle.parse(
            obj("busId" to 10L, "plateNo" to "渝A·00000", "status" to 1, "etaMinutes" to 0),
        )
        // 两条 ETA 算法都 Math.max(1, …)，所以 0 只可能来自别的口径；客户端不替后端改数，也不拿它当"没给"
        assertEquals(0, v.etaMinutes)
    }

    @Test
    fun `自造别名 eta 与 minutes 不是契约键，只带它们的报文必须读成 null`() {
        // 2026-09-30 三方实测：后端 app 侧 VO 里没有名为 eta/minutes 的字段（Java 字段声明、@JsonProperty、
        // "eta" 字符串三种手段各 0 命中，同口径下 etaMinutes 能命中 3 处）、小程序 0 处读它、演示面 DemoData
        // 也只写 etaMinutes ⇒ 旧解析里那些第二候选名是客户端自己造的"第四个真相源"。
        // 这里刻意把 etaMinutes 写成显式 null：旧读法会顺着候选名去接 eta，凭空给出一分钟数
        val alias = BusVehicle.parse(
            obj("busId" to 11L, "plateNo" to "渝A·00011", "status" to 1,
                "etaMinutes" to null, "eta" to 15, "minutes" to 15),
        )
        assertNull(alias.etaMinutes)
        // 真实键照常工作 —— 防止"把整条读法删掉"也被当成修好了
        assertEquals(7, BusVehicle.parse(obj("busId" to 12L, "etaMinutes" to 7)).etaMinutes)

        val a = Arrangement.parse(obj("orderNo" to "YP20260930011", "etaMinutes" to null, "eta" to 6))
        assertNull(a.etaMinutes)
        assertEquals(6, Arrangement.parse(obj("orderNo" to "YP20260930012", "etaMinutes" to 6)).etaMinutes)
    }

    // ── #110：车辆卡「看这条线」的编号从哪来 ─────────────────────────────
    // 钉的是**优先级**这一件事。旧次序是"先按线路名回查 /bus/lines，查不到才看后端给的 routeId"，
    // 那是 §69 那轮在"后端根本没有车辆编号"的前提下写的唯一出路；前提变了次序就得反过来 ——
    // 让一个会因改名、全半角括号、「(公交站)」后缀而失配的字符串去覆盖一个权威整数，
    // 就是"卡片点了没反应"那一族的根。
    @Test
    fun `车辆编号以后端随车辆给的那个为准，名字回查退成兜底`() {
        assertEquals("41", busLineLinkOf("41", "9"))
        assertEquals("41", busLineLinkOf("41", null))
    }

    @Test
    fun `后端没给编号才退回按线路名查到的那个`() {
        assertEquals("9", busLineLinkOf("", "9"))
    }

    @Test
    fun `两个来源都给不出就是空串，页面据此不给可点态而不是跳进空白详情`() {
        assertEquals("", busLineLinkOf("", null))
        assertEquals("", busLineLinkOf("", ""))
    }
}
