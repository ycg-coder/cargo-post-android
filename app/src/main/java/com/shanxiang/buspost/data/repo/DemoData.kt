package com.shanxiang.buspost.data.repo

import com.shanxiang.buspost.core.net.Api
import com.shanxiang.buspost.core.net.Payload
import com.shanxiang.buspost.data.model.FreightOrderStatus
import com.shanxiang.buspost.data.model.Geo
import com.shanxiang.buspost.data.model.ProductOrderStatus
import com.shanxiang.buspost.data.model.freightStatusLabel
import org.json.JSONArray
import org.json.JSONObject

/**
 * 本地演示数据集：后端不可达或返回空时兜底，让界面始终有内容可看。
 * 所有响应都标记 demo=true，页面必须显式展示"演示数据"，不伪装成真实业务数据。
 * 站点/线路/班次坐标为同一条县域线路，保证地图与导航几何自洽。
 */
object DemoData {

    private fun j(vararg pairs: Pair<String, Any?>): JSONObject {
        val o = JSONObject()
        pairs.forEach { (k, v) -> if (v != null) o.put(k, v) }
        return o
    }

    private fun a(vararg items: Any?): JSONArray {
        val arr = JSONArray()
        items.forEach { if (it != null) arr.put(it) }
        return arr
    }

    /** 由站点序列生成的线路坐标 */
    private fun lineOf(stops: List<DStation>): List<Geo> {
        val out = ArrayList<Geo>()
        for (i in stops.indices) {
            val p = stops[i].geo
            out.add(p)
            if (i < stops.size - 1) {
                val q = stops[i + 1].geo
                // 两站之间插两个带侧向偏移的点，模拟山路弯曲
                for (t in 1..2) {
                    val f = t / 3.0
                    val mid = Geo(p.lat + (q.lat - p.lat) * f, p.lng + (q.lng - p.lng) * f)
                    val perp = Geo(-(q.lat - p.lat) * 0.06, (q.lng - p.lng) * 0.06)
                    out.add(Geo(mid.lat + perp.lat * (if (i % 2 == 0) 1 else -1), mid.lng + perp.lng * (if (i % 2 == 0) 1 else -1)))
                }
            }
        }
        return out
    }

    private data class DStation(val id: Long, val name: String, val geo: Geo, val minutes: Int)

    private val county = DStation(101, "县城客运站", Geo(29.5325, 106.5765), 0)
    private val hetao = DStation(102, "核桃坡站", Geo(29.5510, 106.5590), 12)
    private val shiqiao = DStation(103, "石桥驿站", Geo(29.5688, 106.5432), 23)
    private val xinglong = DStation(104, "兴隆场站", Geo(29.5815, 106.5288), 31)
    private val baihe = DStation(105, "白合村站", Geo(29.5902, 106.5165), 38)
    private val shuanghe = DStation(106, "双河口换乘站", Geo(29.5978, 106.5048), 45)
    private val feilong = DStation(107, "飞龙村站", Geo(29.6055, 106.4962), 52)
    private val yunshan = DStation(108, "云山村口站", Geo(29.6132, 106.4880), 60)
    private val huajiao = DStation(109, "花椒基地站", Geo(29.5760, 106.5620), 18)
    private val gaolu = DStation(110, "高炉村站", Geo(29.5895, 106.5702), 26)
    private val warehouse = DStation(111, "快递共配仓", Geo(29.5266, 106.5842), 5)

    private val k1 = listOf(county, hetao, shiqiao, xinglong, baihe, shuanghe, feilong, yunshan)
    private val k2 = listOf(county, hetao, huajiao, gaolu, shuanghe)
    private val k3 = listOf(warehouse, county, hetao, shiqiao)

    private fun stationJson(s: DStation, orderCount: Int = 0) = j(
        "id" to s.id, "name" to s.name, "stationName" to s.name,
        "latitude" to s.geo.lat, "longitude" to s.geo.lng,
        "plannedMinutes" to s.minutes, "orderCount" to orderCount,
    )

    private fun stationsJson(list: List<DStation>) = a(*list.map { stationJson(it, ((it.id * 7 % 5).toInt())) }.toTypedArray())

    /** 车辆沿线路插值移动，位置随当前时间变化，看起来是"活"的 */
    private fun movingGeo(line: List<DStation>, progress: Double): Geo {
        val pts = lineOf(line)
        val idx = (progress.coerceIn(0.0, 0.999) * (pts.size - 1))
        val i = idx.toInt()
        val f = idx - i
        val p = pts[i]
        val q = pts[minOf(i + 1, pts.size - 1)]
        return Geo(p.lat + (q.lat - p.lat) * f, p.lng + (q.lng - p.lng) * f)
    }

    private fun progressNow(): Double = (System.currentTimeMillis() / 1000.0 % 900) / 900.0

    /** 演示车辆编号：真实端点这里是自增主键（`AppBusNearbyRespVO:81`），演示面没有库，用固定号段占位 */
    private val demoBusIds = mapOf(
        "渝A·8K231" to 900101L,
        "渝A·6F412" to 900102L,
        "渝B·5M107" to 900103L,
        "渝A·3Q778" to 900104L,
    )

    /**
     * 演示车辆。两个端点的形状**不一样**，演示面必须照抄（审计 §34 R-l 的同一条教训：
     * 键名/码值照抄，数值才可以编，否则又是"演示能用、真接口全空"）。
     * 键名逐条对过 2026-09-29 实测字段清单：
     * - `/bus/nearby` → `AppBusNearbyRespVO.NearbyBus`：status 是**字符串**码（RUNNING/IDLE/ARRIVED/NO_LOCATION），
     *   有 locationSource / routeProvider / lastLocationTime / distanceKm / nearestStation*，
     *   **没有** progress、**没有** speedKmh、**没有** etaToNextStationMinutes；
     * - `/bus/lines` → 内嵌 `AppBusRespVO`：status 是**整数**（0 空闲 / 1 在途 / 2 停用），
     *   有 progress / speedKmh / etaToNextStationMinutes，**没有**新鲜度与最近站那一组键。
     * 两边都**没有** `routeId`（车辆只带 `routeName`），所以这里也不许写 —— 车辆卡"点一下跳线路详情"
     * 只能按线路名回查线路列表，见 [com.shanxiang.buspost.data.model.BusVehicle] 的 `routeId` 注释（审计 §69）。
     * `state` 只取后端真会出现的三种口径，让页面三种渲染在演示面都能亲眼看到：
     * - `RUNNING`：在途且后端算出了到站分钟（`AppBusServiceImpl:132` 只在 status==1 时给，且 `Math.max(1,…)` 永不为 0）；
     * - `NO_ETA`：在途但后端给不出 ETA —— `:665`「下一站未知：只给站点名，不臆造到站分钟」、`:134`/`:719`
     *   `setEtaMinutes(null)`。HTTP 体里是**显式 `null`**（MVC mapper 没开 NON_NULL，不过滤 null 键），
     *   既不是缺键也不是 0，所以演示面写 `JSONObject.NULL`，而不是"干脆不给这个键"；
     * - `IDLE`：空闲/待发车，只给 `waitDepartureMinutes`（`:136`，语义是"还有多久发车"，不是到站）。
     * `routeProvider` 只写后端真会写的标签（`SCHEDULE`，见 `:722/:730`；真实路网标签走 `RealRoadProviders` 白名单），
     * 旧演示面写 "REAL"/"SIM" 是自造码 —— 页面拿它判"现实公交"时永远判不到。
     */
    private fun vehicleJson(
        plate: String,
        geo: Geo,
        source: String,
        next: DStation,
        state: String,
        line: String,
        shiftCode: String,
        eta: Int?,
        speed: Int?,
        nearby: Boolean = false,
        distanceKm: Double? = null,
    ): JSONObject {
        val running = state != "IDLE"
        val hasEta = state == "RUNNING" && eta != null
        val base = j(
            "busId" to (demoBusIds[plate] ?: 900199L),
            "plateNo" to plate,
            "shiftCode" to shiftCode,
            "routeName" to line,
            "startStation" to "云山村口站", "endStation" to "县城客运站",
            "latitude" to geo.lat, "longitude" to geo.lng,
            "dataSource" to source,
            "currentStation" to next.name,
        )
        base.put("nextStation", if (state == "NO_ETA") JSONObject.NULL else next.name)
        base.put("etaMinutes", if (hasEta) eta else JSONObject.NULL)
        base.put("waitDepartureMinutes", if (running) JSONObject.NULL else 6)
        base.put("distanceToNextStationKm", if (hasEta) eta!! * 0.42 else JSONObject.NULL)
        return if (nearby) {
            base
                .put("status", if (running) "RUNNING" else "IDLE")
                .put("locationSource", if (source.contains("REAL")) "REAL_FRESH" else "SIMULATED")
                .put("routeProvider", "SCHEDULE")
                // NearbyBus:131「模拟车辆为 null」
                .put("lastLocationTime", if (source.contains("REAL")) "2026-09-29 11:12:00" else JSONObject.NULL)
                .put("distanceKm", distanceKm ?: JSONObject.NULL)
                .put("nearestStationName", next.name)
                .put("nearestStationEtaMinutes", if (hasEta) eta else JSONObject.NULL)
                .put("stopsToNearestStation", if (hasEta) 2 else JSONObject.NULL)
                .put("nearestStationDistanceKm", distanceKm ?: JSONObject.NULL)
        } else {
            // /bus/lines 的 AppBusRespVO：整数 status + progress/speedKmh/etaToNextStationMinutes，
            // 没有 lastLocationTime / locationSource / routeProvider / nearestStation* 这些 nearby 专属键。
            // `NO_ETA` 在 lines 端点是不会出现的：`:131-132` 只要 status==1 就用进度反推 ETA（Math.max(1,…)），
            // 所以进度与 ETA 在这里是绑定的；进度缺失时后端按 0 处理，也仍会给 ETA。
            base.put("status", if (running) 1 else 0)
                .put("progress", if (hasEta) (100 - eta!! * 2).coerceIn(0, 100) else JSONObject.NULL)
                .put("speedKmh", if (running) speed else JSONObject.NULL)
                .put("etaToNextStationMinutes", if (hasEta) eta!! * 0.6 else JSONObject.NULL)
        }
    }

    private val products = listOf(
        Triple("高山土鸡蛋", "30枚/盒", 26.8),
        Triple("手工红薯粉条", "500g/把", 15.0),
        Triple("野山核桃", "250g/袋", 32.5),
        Triple("老树绿茶", "100g/罐", 68.0),
        Triple("山崖土蜂蜜", "500g/瓶", 88.0),
        Triple("高山红花椒", "100g/袋", 22.0),
        Triple("柴火熏腊肉", "500g/份", 45.0),
        Triple("富硒大米", "5kg/袋", 56.0),
    )

    /** 与 products 一一对应的本地图；没有贴合实物的留空，由商品格子画占位，不拿不相干的图充数 */
    private val productImages = listOf(
        "product-egg.png", "xiaomian.jpg", "product-nut.png", "product-tea.png",
        "mihuatang.jpg", "", "larou.jpg", "",
    )

    fun imageOf(i: Int): String = productImages[i.mod(productImages.size)].let {
        if (it.isEmpty()) "" else "file:///android_asset/products/$it"
    }

    private fun productJson(i: Int): JSONObject {
        val (name, spec, price) = products[i]
        val villages = listOf("云山村", "飞龙村", "白合村", "高炉村", "核桃坡")
        return j(
            "id" to (2001L + i),
            "name" to name,
            "subtitle" to "$spec · 当日山货",
            "price" to price,
            "unit" to "份",
            "stock" to 40 + i * 13,
            // 已撤 originPrice / sales：AppProductRespVO.java:12-34 的 12 个键里没有划线原价，也没有销量口径，
            // 小程序商品面只读 price（pages/goods/detail.js:69）。这两个键此前只有演示面在发 ⇒ 第四个真相源，
            // 继续发就等于给解析层的别名续命（详情页会凭空显示"已售 167"和一条假划线价）。
            "imageUrl" to imageOf(i),
            "picUrl" to imageOf(i),
            "badge" to "巴士带货",
            "category" to listOf("山蛋", "杂粮", "干果", "茶叶", "蜜品", "香辛", "腊味", "大米")[i],
            "village" to villages[i % villages.size],
            "fromVillage" to villages[i % villages.size],
            "intro" to "产自 ${villages[i % villages.size]} 海拔 900 米以上山村，由每日下行班车直送县城共配仓，" +
                "上午下单当天下午到仓，支持到站自提与村口交付。",
        )
    }

    private val orderNos = listOf("SH20260928001", "SH20260928002", "SH20260927018", "SH20260926009")

    private fun sendOrderJson(i: Int): JSONObject {
        val st = listOf(3, 2, 4, 7)[i]
        val hasCarrier = FreightOrderStatus.of(st)?.let { it == FreightOrderStatus.ASSIGNED || it == FreightOrderStatus.DEPARTED || it == FreightOrderStatus.COMPLETED } == true
        val goods = listOf("土鸡蛋 2 箱", "腊肉 3 件", "山核桃 1 袋", "茶叶礼盒 2 件")[i]
        return j(
            "id" to (5001L + i),
            "orderNo" to orderNos[i],
            "goodsName" to goods,
            "weightKg" to listOf(12.5, 8.2, 1.5, 4.0)[i],
            "itemCount" to listOf(2, 3, 1, 2)[i],
            "volumeM3" to listOf(0.06, 0.04, 0.01, 0.02)[i],
            "freshFlag" to (i == 0 || i == 1),
            "cargoCategory" to listOf("生鲜农产", "生鲜农产", "干货", "礼品")[i],
            "status" to st,
            "statusName" to freightLabel(st),
            "statusLabel" to freightLabel(st),
            "reviewStatus" to if (i == 3) 2 else 1,
            "reviewReasonCodes" to if (i == 3) "USER_LOCATION_UNREACHABLE,CUSTOMER_ACTION_REQUIRED" else "",
            "servicePointStationName" to if (i == 3) shiqiao.name else "",
            "servicePointDistanceKm" to if (i == 3) 1.86 else 0.0,
            "servicePointLatitude" to if (i == 3) shiqiao.geo.lat else 0.0,
            "servicePointLongitude" to if (i == 3) shiqiao.geo.lng else 0.0,
            "pickupCode" to listOf("8213", "4470", "9155", "3082")[i],
            "senderName" to listOf("李秀兰", "王建国", "张阿婆", "陈立")[i],
            "senderMobile" to listOf("138****4021", "137****8830", "135****1207", "150****6644")[i],
            "senderAddress" to listOf("云山村口站", "飞龙村站", "白合村站", "兴隆场站")[i],
            "receiverName" to listOf("周敏", "山乡食堂", "刘阿姨", "县城代收点")[i],
            "receiverMobile" to listOf("132****7788", "186****2210", "139****5566", "133****9911")[i],
            "receiverAddress" to listOf("县城客运站旁", "石桥驿站", "双河口", "快递共配仓")[i],
            "fee" to listOf(14.0, 12.0, 9.0, 11.0)[i],
            "driverName" to if (hasCarrier) "王有德" else "",
            "driverMobile" to if (hasCarrier) "13700001234" else "",
            "vehiclePlate" to if (hasCarrier) "渝A·8K231" else "",
            "shiftCode" to if (hasCarrier) "K1-0830" else "",
            "targetStation" to listOf("石桥驿站", "云山村口站", "双河口换乘站", "县城客运站")[i],
            "carrierApproaching" to (i == 0),
            "carrierArrived" to (i == 0),
            "carrierArrivedStation" to if (i == 0) "石桥驿站" else "",
            "carrierEtaMinutes" to listOf(8, 26, 48, 0)[i],
            "carrierDistanceKm" to listOf(3.2, 12.6, 24.1, 0.0)[i],
            "carrierLocationSource" to if (i == 0) "REAL_FRESH" else "SIMULATED",
            "etaMinutes" to listOf(8, 26, 48, 0)[i],
            "dataSource" to if (i == 0) "REAL" else "SIMULATED",
            "estimatedArrivalTime" to if (hasCarrier) "11:40" else "",
            "createTime" to "2026-09-2${8 - i} 09:1${i}4",
            // 演示形态把两格都演出来：第 2 单带后端那个回读键（数字毫秒），其余不带 ⇒ 列表里同时看得见
            // "有值怎么念"和"没给怎么念"，不是一屏假完整。真实接口这一键来自 AppSendOrderRespVO（本轮补）。
            "earliestPickupTime" to if (i == 1) System.currentTimeMillis() + 3 * 3600_000L else null,
            "remark" to if (i == 0) "易碎，请轻放" else "",
        )
    }

    /** 演示数据不再自带第二张码表：文案与后端枚举同源（ServerStatus.kt，§18） */
    private fun freightLabel(s: Int) = freightStatusLabel(s)

    private fun productOrderJson(i: Int): JSONObject {
        val st = listOf(1, 2, 0)[i]
        val shipped = ProductOrderStatus.of(st)?.let { it == ProductOrderStatus.DELIVERED || it == ProductOrderStatus.COMPLETED } == true
        val completed = ProductOrderStatus.of(st) == ProductOrderStatus.COMPLETED
        return j(
            "id" to (7001L + i),
            "orderNo" to "MP2026092${7 - i}00${i + 1}",
            "status" to st,
            "statusName" to listOf("已发货", "已完成", "待发货")[i],
            "totalPrice" to listOf(79.5, 56.0, 26.8)[i],
            "receiverName" to listOf("赵先生", "孙女士", "周敏")[i],
            "receiverMobile" to listOf("135****2211", "188****7766", "132****7788")[i],
            "receiverAddress" to listOf("县城兴隆街 12 号", "双河口村 3 组", "快递共配仓自提")[i],
            "vehiclePlate" to if (shipped) "渝A·8K231" else "",
            "driverName" to if (shipped) "王有德" else "",
            "driverMobile" to if (shipped) "13700001234" else "",
            "driverArrived" to (shipped),
            "routeName" to "K1 县城 ⇄ 云山村",
            "shiftCode" to if (shipped) "K1-0830" else "",
            "deliveryStation" to listOf("县城客运站", "双河口换乘站", "核桃坡站")[i],
            "loadTime" to if (shipped) "2026-09-2${7 - i} 10:20" else "",
            "deliverTime" to if (completed) "2026-09-2${7 - i} 11:38" else "",
            // 核验凭证：详情页要能点开大图
            "loadPhotoUrl" to if (shipped) imageOf(i * 2) else "",
            "deliverPhotoUrl" to if (completed) imageOf(i * 2 + 1) else "",
            "createTime" to "2026-09-2${7 - i} 15:2${i}",
            "remark" to if (i == 0) "工作日白天送达" else "",
            "items" to a(
                j(
                    "productId" to (2001L + i * 2 % products.size),
                    "productName" to products[i * 2 % products.size].first,
                    "count" to listOf(1, 1, 1)[i],
                    "price" to listOf(26.8, 56.0, 26.8)[i],
                    "amount" to listOf(26.8, 56.0, 26.8)[i],
                    "productImage" to imageOf(i * 2),
                    "picUrl" to imageOf(i * 2),
                ),
                j(
                    "productId" to (2001L + (i * 2 + 1) % products.size),
                    "productName" to products[(i * 2 + 1) % products.size].first,
                    "count" to if (i == 0) 2 else 1,
                    "price" to listOf(15.0, 0.0, 0.0)[i],
                    "amount" to listOf(30.0, 0.0, 0.0)[i],
                    "productImage" to imageOf(i * 2 + 1),
                    "picUrl" to imageOf(i * 2 + 1),
                ),
            ),
        )
    }

    private fun nodesJson(orderNo: String, reached: Int, vehicle: String, shift: String) = a(
        j("label" to "订单已提交", "time" to "09:14", "done" to true),
        j("label" to "站点收件核验", "time" to "09:32", "done" to true),
        j("label" to "调度已分配 · $vehicle $shift", "time" to "10:05", "done" to (reached >= 3)),
        j("label" to "装车发车", "time" to "10:20", "done" to (reached >= 4), "current" to (reached == 4)),
        j("label" to "途经双河口换乘站交接", "time" to "11:05", "done" to (reached >= 5), "current" to (reached == 5)),
        j("label" to "到达目标站点 · 待取件", "time" to "11:40", "done" to (reached >= 6), "current" to (reached == 6)),
    )

    private fun legsJson(active: Int) = a(
        j(
            "legId" to "L1", "seq" to 1, "fromStation" to "云山村口站", "toStation" to "双河口换乘站",
            "vehiclePlate" to "渝A·8K231", "driverName" to "王有德",
            "status" to if (active >= 1) 7 else 2,
            "statusName" to if (active >= 1) "运输中" else "司机已接单", "statusLabel" to if (active >= 1) "运输中" else "司机已接单",
            "etaMinutes" to 18, "action" to "load",
            "orderNo" to orderNos[0],
            "distanceKm" to 24.6, "durationMinutes" to 52, "navigationSource" to "amap",
            "fromLatitude" to k1[0].geo.lat, "fromLongitude" to k1[0].geo.lng,
            "toLatitude" to k1[2].geo.lat, "toLongitude" to k1[2].geo.lng,
            "navigationPolyline" to polylineJson(k1.subList(0, 3)),
        ),
        j(
            "legId" to "L2", "seq" to 2, "fromStation" to "双河口换乘站", "toStation" to "县城客运站",
            "vehiclePlate" to "渝B·5M107", "driverName" to "罗建平",
            "status" to if (active >= 2) 7 else 0,
            "statusName" to if (active >= 2) "运输中" else "已规划", "statusLabel" to if (active >= 2) "运输中" else "已规划",
            "etaMinutes" to 32,
            "transferStation" to "双河口换乘站", "action" to "handover-start",
            "orderNo" to orderNos[0],
            "distanceKm" to 15.2, "durationMinutes" to 34, "navigationSource" to "ESTIMATED",
            "fromLatitude" to k1[2].geo.lat, "fromLongitude" to k1[2].geo.lng,
            "toLatitude" to k1[4].geo.lat, "toLongitude" to k1[4].geo.lng,
        ),
    )

    /**
     * 换乘交接点：待交接清单要展示交出/接收司机与件数。
     * ⚠ 键名逐个抄 `OrderTopologyRespVO.Handover`：车牌是 `fromPlateNo`/`toPlateNo`（D-081 那条"演示面与契约同键"
     *   的收尾——旧写法发的是 `fromPlate`/`toPlate`/`handoverTime`，真接口从不发这三个，于是真机下交接整格念空，
     *   只有演示包"看起来是对的"）。
     */
    private fun handoversJson() = a(
        j(
            "stationName" to "双河口换乘站", "fromPlateNo" to "渝A·8K231", "toPlateNo" to "渝B·5M107",
            "fromDriverName" to "王有德", "toDriverName" to "罗建平",
            "arrivedAt" to "2026-09-28 10:52", "handoverStartedAt" to "2026-09-28 11:00",
            "handoverCompletedAt" to "2026-09-28 11:05", "itemCount" to 6,
            "status" to 2, "statusName" to "已交接",
        ),
    )

    /** 候选运输方案：拓扑接口下发的方案解释。键名逐个抄 OrderTopologyRespVO.Candidate:60-69
     *  （modeName/legCount/durationMinutes/reason/chosen）；name/recommended 是演示面自造的，已撤。 */
    private fun plansJson() = a(
        j("modeName" to "方案 A · 双河口换乘", "chosen" to true, "legCount" to 2, "durationMinutes" to 86,
            "reason" to "全程在班车经停范围内，换乘窗口 20 分钟，预计最快到达"),
        j("modeName" to "方案 B · 直达共配仓", "chosen" to false, "legCount" to 1, "durationMinutes" to 142,
            "reason" to "不经换乘，但需等下一班次发车"),
    )

    /** 司机排班：早中晚三班，早班在途 */
    private fun shiftJson(i: Int): JSONObject {
        val code = listOf("K1-0830", "K1-1330", "K1-1830")[i]
        val status = listOf(1, 0, 0)[i]
        return j(
            "shiftId" to (301L + i),
            "shiftCode" to code,
            "routeName" to "K1 县城 ⇄ 云山村",
            "lineName" to "K1 县城 ⇄ 云山村",
            "departTime" to listOf("2026-09-28 08:30", "2026-09-28 13:30", "2026-09-28 18:30")[i],
            "plannedDepartureTime" to listOf("2026-09-28 08:30", "2026-09-28 13:30", "2026-09-28 18:30")[i],
            "arriveTime" to listOf("10:30", "15:30", "20:30")[i],
            "status" to status,
            "state" to listOf("RUNNING", "WAITING", "WAITING")[i],
            "statusName" to listOf("进行中", "待发车", "待发车")[i],
            "statusLabel" to listOf("进行中", "待发车", "待发车")[i],
            "plannedDurationMinutes" to 120,
            // orderCount 已随 ShiftTask 的读法一起撤（班次 VO 无此键），演示报文同形，不给无生产者键续命
            "loadedCount" to listOf(4, 0, 0)[i],
            "cargoKg" to listOf(78.5, 26.0, 4.5)[i],
            "progress" to listOf(0.62, 0.0, 0.0)[i],
            "currentStationId" to if (status == 1) shiqiao.id else county.id,
            "stations" to stationsJson(k1),
        )
    }

    private fun guideOf(progress: Double): List<JSONObject> {
        val steps = ArrayList<JSONObject>()
        val line = k1
        steps.add(j(
            "icon" to "DEPART", "text" to "从${county.name}出发", "detail" to "沿滨江路向北，注意站前限速 30",
            "distanceMeters" to 320, "lanes" to a("左转专用", "直行", "直行", "公交专用"), "recommendedLane" to 2,
        ))
        for (i in 1 until line.size) {
            val from = line[i - 1]
            val to = line[i]
            val bearing = com.shanxiang.buspost.data.model.GeoMath.turnOf(
                com.shanxiang.buspost.data.model.GeoMath.bearing(if (i >= 2) line[i - 2].geo else from.geo, from.geo),
                com.shanxiang.buspost.data.model.GeoMath.bearing(from.geo, to.geo),
            )
            val dist = com.shanxiang.buspost.data.model.GeoMath.distance(from.geo, to.geo).toInt()
            val lanes = when (bearing) {
                com.shanxiang.buspost.data.model.NavIcon.TURN_LEFT,
                com.shanxiang.buspost.data.model.NavIcon.SLIGHT_LEFT -> a("左转专用", "直行", "直行")
                com.shanxiang.buspost.data.model.NavIcon.TURN_RIGHT,
                com.shanxiang.buspost.data.model.NavIcon.SLIGHT_RIGHT -> a("直行", "直行", "右转专用")
                else -> a("直行", "直行", "直行", "应急车道")
            }
            steps.add(
                j(
                    "icon" to bearing.name,
                    "text" to "前往${to.name}",
                    "detail" to if (i == line.size - 1) "到站点位停车，等待收件人凭取件码交接" else "山路弯道多，注意会车",
                    "distanceMeters" to dist,
                    "lanes" to lanes,
                    "recommendedLane" to if (bearing.name.contains("LEFT")) 0 else if (bearing.name.contains("RIGHT")) lanes.length() - 1 else 1,
                    "stationId" to to.id,
                    "stationName" to to.name,
                    "plannedMinutes" to to.minutes,
                )
            )
        }
        val cur = (progress * steps.size).toInt().coerceIn(0, steps.size - 1)
        steps.forEachIndexed { idx, o -> o.put("remaining", steps.size - idx).put("current", idx == cur) }
        return steps
    }

    private fun polylineJson(line: List<DStation>) = a(
        *lineOf(line).map { j("latitude" to it.lat, "longitude" to it.lng) }.toTypedArray()
    )

    fun respond(path: String, query: Map<String, Any?>, body: Map<String, Any?>?, method: String): Payload {
        val now = progressNow()
        val data: Any? = when {
            path.endsWith("/notice/list") -> a(
                j("id" to 1, "title" to "国庆期间班车时刻调整", "content" to "10 月 1—3 日 K1 线路早班提前至 07:30 发车，" +
                    "寄货请提前一天送达站点；其余班次时刻不变。", "createTime" to "2026-09-26 17:20"),
                j("id" to 2, "title" to "生鲜件当日达通道开通", "content" to "土鸡蛋、腊肉等生鲜农产品可在村口站直接装车，" +
                    "当日 16:00 前到县城共配仓，超过 30kg 请拆分下单。", "createTime" to "2026-09-24 09:05"),
                j("id" to 3, "title" to "双河口换乘站交接窗口", "content" to "换乘站交接时间 11:00—11:20、16:40—17:00，" +
                    "错过窗口将顺延至下一班，请寄件人留意取件码短信。", "createTime" to "2026-09-21 14:40"),
            )

            path.endsWith("/product/list") || path.endsWith("/product/page") ->
                if (path.endsWith("/page")) j("list" to a(*IntRange(0, 7).map { productJson(it) }.toTypedArray()), "total" to 8)
                else a(*IntRange(0, 7).map { productJson(it) }.toTypedArray())

            path.endsWith("/product/get") -> productJson(((query["id"]?.toString()?.toIntOrNull() ?: 2001) - 2001).coerceIn(0, 7))

            path.endsWith("/product-order/page") -> j(
                "list" to a(*IntRange(0, 2).map { productOrderJson(it) }.toTypedArray()), "total" to 3,
            )

            path.endsWith("/send/page") -> j(
                "list" to a(*IntRange(0, 3).map { sendOrderJson(it) }.toTypedArray()), "total" to 4,
            )

            path.endsWith("/send/stations") -> stationsJson((k1 + listOf(huajiao, gaolu, warehouse)).distinctBy { it.id })

            // 字段名与小程序 send.wxml:282-287 一致：amount / itemFee / distanceFee / itemCount / distanceKm
            path.endsWith("/send/quote") -> {
                val items = (body?.get("itemCount")?.toString()?.toIntOrNull() ?: 1)
                j("amount" to 6.0 + items * 2.5, "itemFee" to items * 2.5, "distanceFee" to 6.0,
                    "itemCount" to items, "distanceKm" to 24.6)
            }

            // 兜底报文按 `AppSendReachabilityRespVO` 的**真实形状**给：公里制 distanceKm + recommendedStation 子对象，
            // 不再用旧的 nearestStation/distance(米) 自造键（否则三态解析只会读到"接口未给"，调试时看不出契约差异）。
            // distanceProvider 只给 HAVERSINE：兜底数据可以假距离，但不许冒充高德道路里程（界面会标"直线估算"）。
            // 步行分钟与后端公式同口径：ceil(1.86*1000/1.2/60)=26（AppSendReachabilityService:152-154）。
            path.endsWith("/send/reachability") -> j(
                "reachable" to false,
                "reasonCode" to "USER_LOCATION_UNREACHABLE",
                "serviceMode" to "NEAREST_STATION",
                "distanceKm" to 1.86,
                "walkMinutes" to 26,
                "distanceProvider" to "HAVERSINE",
                "recommendedStation" to j(
                    "id" to shiqiao.id, "name" to shiqiao.name,
                    "latitude" to shiqiao.geo.lat, "longitude" to shiqiao.geo.lng,
                    "distanceKm" to 1.86,
                ),
                "message" to "当前位置车辆无法直接进入，请送到最近服务站点交接（演示数据）",
            )

            // 字段名与小程序 send.wxml:297-309 一致：distanceKm / durationMinutes / provider
            path.endsWith("/send/route-preview") ->
                j("distanceKm" to 24.6, "durationMinutes" to 72, "provider" to "amap")

            // 省市区三级地区树（免登录）
            path.endsWith("/system/area/tree") -> a(
                j("id" to "530000", "name" to "云南省", "children" to a(
                    j("id" to "532900", "name" to "大理白族自治州", "children" to a(
                        j("id" to "532923", "name" to "祥云县"),
                        j("id" to "532922", "name" to "漾濞彝族自治县"),
                    )),
                    j("id" to "530100", "name" to "昆明市", "children" to a(
                        j("id" to "530102", "name" to "五华区"),
                    )),
                )),
            )

            // 调度任务（算法派单结果，预留）
            path.endsWith("/driver/tasks") -> a(
                j("taskId" to "T-1", "taskType" to "PICKUP", "stationName" to shiqiao.name,
                    "orderNo" to orderNos[1], "goodsName" to "腊肉 2 箱", "itemCount" to 2,
                    "actionLabel" to "装车", "planTime" to "11:05"),
                j("taskId" to "T-2", "taskType" to "DELIVER", "stationName" to "县城共配仓",
                    "orderNo" to orderNos[3], "goodsName" to "土蜂蜜 1 箱", "itemCount" to 1,
                    "actionLabel" to "交付", "planTime" to "12:20"),
            )

            // 订阅消息模板（安卓侧走系统通知，保留契约）
            path.endsWith("/get-subscribe-template-list") -> a(
                j("id" to "TPL_DISPATCH", "title" to "派单通知", "type" to "DISPATCH"),
                j("id" to "TPL_ARRIVE", "title" to "到站提醒", "type" to "ARRIVE"),
            )

            path.endsWith("/send/arrangements") -> a(
                j("orderNo" to orderNos[0], "shiftCode" to "K1-0830", "vehiclePlate" to "渝A·8K231",
                    "targetStation" to "石桥驿站", "etaMinutes" to 8, "dataSource" to "SIMULATED", "statusLabel" to "已发车"),
                j("orderNo" to orderNos[2], "shiftCode" to "K1-1330", "vehiclePlate" to "渝A·8K231",
                    "targetStation" to "云山村口站", "etaMinutes" to 41, "dataSource" to "SIMULATED", "statusLabel" to "已分配"),
            )

            path.endsWith("/send/track") || path.endsWith("/send/topology") -> {
                val topo = path.endsWith("/send/topology")
                j(
                "orderNo" to orderNos[0],
                "status" to 6,
                "statusName" to "已发车",
                "statusLabel" to "已发车",
                "productName" to "土鸡蛋 2 箱",
                "goodsName" to "土鸡蛋 2 箱",
                "vehiclePlate" to "渝A·8K231",
                "driverName" to "王有德",
                "driverMobile" to "13700001234",
                "shiftCode" to "K1-0830",
                "routeName" to "K1 县城 ⇄ 云山村",
                "startStation" to "云山村口站",
                "endStation" to "石桥驿站",
                "targetStation" to "石桥驿站",
                "receiverName" to "周敏",
                "receiverMobile" to "13200007788",
                "receiverAddress" to "县城客运站旁",
                "etaMinutes" to 8,
                "dataSource" to "SIMULATED",
                "estimatedArrivalTime" to "11:40",
                "lastReportTime" to "2026-09-28 11:12",
                "loadTime" to "2026-09-28 10:20",
                "deliverTime" to "",
                "driverArrived" to false,
                "loadPhotoUrl" to imageOf(0),
                "deliverPhotoUrl" to "",
                "latitude" to movingGeo(k1, now).lat,
                "longitude" to movingGeo(k1, now).lng,
                "nodes" to nodesJson(orderNos[0], 5, "渝A·8K231", "K1-0830"),
                "legs" to legsJson(1),
                "handovers" to handoversJson(),
                "plans" to plansJson(),
                "points" to a(*k1.mapIndexed { idx, st ->
                    stationJson(st, idx * 19).put("sequenceNo", idx + 1)
                }.toTypedArray()),
                "stations" to stationsJson(k1),
                "track" to polylineJson(k1.subList(0, 5)),
                ).let {
                    // 拓扑信封那 10 个键（`OrderTopologyRespVO:25-46`）**只有 /send/topology 会发**。
                    // 挂在共用报文上，演示包的 `/send/track` 也会"多出一格真接口从不下发的内容"
                    // —— 那就是 D-081 登记的"演示面比契约更像真话"，所以按路径分开发。
                    if (!topo) return@let it
                    it.put("originStationName", "云山村口站").put("destinationStationName", "石桥驿站")
                        .put("orderStatusName", "已发车").put("planningMode", "MULTI_LEG")
                        .put("planningModeName", "多段联运").put("planNo", "PLAN-K1-0830")
                        .put("totalLegs", 2).put("transferCount", 1)
                        .put("totalDistanceKm", 27.4)
                        // 86 是各段计划耗时之和，+20 是后端 `TransportTopologyServiceImpl:134` 每次换乘另加的量
                        .put("totalDurationMinutes", 86 + 20)
                        .put("planReason", "双河口换乘：全程在班车经停范围内，含 20 分钟换乘等待")
                }
            }

            path.endsWith("/send/legs") -> legsJson(1)

            path.endsWith("/bus/lines") -> a(
                j(
                    "routeId" to "K1", "routeName" to "K1 县城 ⇄ 云山村", "startStation" to county.name, "endStation" to yunshan.name,
                    "dataSource" to "PROJECT_TRANSIT", "stations" to stationsJson(k1),
                    "vehicles" to a(
                        vehicleJson("渝A·8K231", movingGeo(k1, now), "REAL", shiqiao, "RUNNING", "K1 县城 ⇄ 云山村", "K1-0830", 12, 27),
                        vehicleJson("渝A·6F412", movingGeo(k1, (now + 0.34) % 1), "SIMULATED", xinglong, "IDLE", "K1 县城 ⇄ 云山村", "K1-1330", null, null),
                    ),
                ),
                j(
                    "routeId" to "K2", "routeName" to "K2 县城 ⇄ 高炉村", "startStation" to county.name, "endStation" to gaolu.name,
                    "dataSource" to "PROJECT_TRANSIT", "stations" to stationsJson(k2),
                    "vehicles" to a(
                        vehicleJson("渝B·5M107", movingGeo(k2, (now + 0.5) % 1), "SIMULATED", huajiao, "RUNNING", "K2 县城 ⇄ 高炉村", "K2-0930", 19, 18),
                    ),
                ),
            )

            path.endsWith("/bus/nearby") -> {
                val radius = (query["radius"]?.toString()?.toDoubleOrNull() ?: 15000.0)
                val userGeo = Geo(
                    query["latitude"]?.toString()?.toDoubleOrNull() ?: county.geo.lat,
                    query["longitude"]?.toString()?.toDoubleOrNull() ?: county.geo.lng,
                )
                val near = (k1 + huajiao + gaolu + warehouse).filter {
                    com.shanxiang.buspost.data.model.GeoMath.distance(userGeo, it.geo) <= radius
                }.map { st ->
                    stationJson(st, 0).put(
                        "distance",
                        com.shanxiang.buspost.data.model.GeoMath.distance(userGeo, st.geo).toInt(),
                    ).put("routes", a(j("name" to "K1 县城 ⇄ 云山村"), j("name" to "K2 县城 ⇄ 高炉村")))
                }
                j(
                    "realTransitAvailable" to false,
                    "stations" to a(*near.toTypedArray()),
                    // nearby 端点才会出现「在途但给不出 ETA」（AppBusServiceImpl:665/:719 显式 null），
                    // lines 端点用进度反推、恒 ≥1 分钟，所以这条车只挂在 nearby 列表里（首页车辆卡与桌面卡片能看到它）
                    // 这里的 GeoMath.distance 量的是**用户与车辆的直线远近**，与后端 `NearbyBus.distanceKm`
                    // （"距用户直线距离(km)"，AppBusNearbyRespVO:137-138）同口径：只用于排序与"离你 N km"文案，
                    // 不是道路里程、不参与任何可用性判定。
                    "vehicles" to a(
                        vehicleJson(
                            "渝A·8K231", movingGeo(k1, now), "REAL", shiqiao, "RUNNING",
                            "K1 县城 ⇄ 云山村", "K1-0830", 12, 27, nearby = true,
                            distanceKm = com.shanxiang.buspost.data.model.GeoMath.distance(userGeo, movingGeo(k1, now)) / 1000.0,
                        ),
                        vehicleJson(
                            "渝A·6F412", movingGeo(k1, (now + 0.34) % 1), "SIMULATED", xinglong, "IDLE",
                            "K1 县城 ⇄ 云山村", "K1-1330", null, null, nearby = true,
                            distanceKm = com.shanxiang.buspost.data.model.GeoMath.distance(userGeo, movingGeo(k1, (now + 0.34) % 1)) / 1000.0,
                        ),
                        vehicleJson(
                            "渝B·5M107", movingGeo(k2, (now + 0.5) % 1), "SIMULATED", huajiao, "RUNNING",
                            "K2 县城 ⇄ 高炉村", "K2-0930", 19, 18, nearby = true,
                            distanceKm = com.shanxiang.buspost.data.model.GeoMath.distance(userGeo, movingGeo(k2, (now + 0.5) % 1)) / 1000.0,
                        ),
                        vehicleJson(
                            "渝A·3Q778", movingGeo(k1, (now + 0.66) % 1), "REAL", baihe, "NO_ETA",
                            "K1 县城 ⇄ 云山村", "K1-1530", null, 24, nearby = true,
                            distanceKm = com.shanxiang.buspost.data.model.GeoMath.distance(userGeo, movingGeo(k1, (now + 0.66) % 1)) / 1000.0,
                        ),
                    ),
                )
            }

            path.endsWith("/notification/page") || path.endsWith("/driver/messages") -> j(
                "list" to notificationsJson(), "total" to 6,
            )

            path.endsWith("/unread-count") -> j("count" to 3, "unreadCount" to 3)

            path.endsWith("/feedback/page") -> j(
                "list" to a(
                    j("id" to 1, "content" to "希望村口站可以增加晚间收件窗口。", "replyContent" to "已排期，10 月起村口站延长至 19:00。", "status" to "已回复", "createTime" to "2026-09-18 20:11"),
                    j("id" to 2, "content" to "寄件页面重量填写不方便，希望能用语音输入。", "reply" to "", "status" to "待处理", "createTime" to "2026-09-11 08:32"),
                ),
                "total" to 2,
            )

            path.endsWith("/address/list") -> a(
                j("id" to 1, "name" to "周敏", "mobile" to "13200007788", "provinceName" to "重庆市", "cityName" to "武隆区",
                    "districtName" to "凤山街道", "detailAddress" to "县城兴隆街 12 号 1 单元", "defaultStatus" to true),
                j("id" to 2, "name" to "李秀兰", "mobile" to "13800004021", "provinceName" to "重庆市", "cityName" to "武隆区",
                    "districtName" to "土坎镇", "detailAddress" to "云山村口站旁 200 米", "defaultStatus" to false),
            )

            path.endsWith("/member/user/get") -> j(
                "id" to 10086, "mobile" to "13800004021", "nickname" to "李秀兰", "gender" to 1, "avatar" to "",
            )

            path.endsWith("/driver/profile") -> j(
                // 演示面与后端同形：键名逐个抄 AppDriverProfileRespVO.java:11-23 的 7 个键
                // （driverId/name/mobile/vehicleId/plateNo/cargoCapacity/cargoCapacityKg）。
                // 已撤 capacityKg/usedKg/verified ——后端没有任何"已装重量/认证状态"键，继续发就是替解析层造第四个真相源，
                // 真机上这两个读法恒 null，界面就会露出"载重 0 kg / 未认证"这种凭空结论。
                "driverId" to 61, "name" to "王有德", "mobile" to "13700001234", "plateNo" to "渝A·8K231",
                "cargoCapacityKg" to 200,
                // 行李舱运力按「件数」口径（小程序 workbench.js:183 cargoCapacity）
                "cargoCapacity" to 24,
                // ⚠ lineName/company/shiftCode 也**不在**那 7 个键里，属字符串族无生产者键（本轮未收口，
                // 随 F-3 ② 的字符串族一批登记）：删了会让演示面的"所属线路/公司"两行凭空变空，
                // 收法要和解析层读点一起定（改成从班次/线路端点取，还是界面直接不提）。
                "lineName" to "K1 县城 ⇄ 云山村", "company" to "乡邮智联运输有限公司", "shiftCode" to "K1-0830",
            )

            path.endsWith("/driver/shifts") -> a(*IntRange(0, 2).map { shiftJson(it) }.toTypedArray())

            path.endsWith("/driver/pickups") -> a(
                // 已撤 checked：AppDriverPickupRespVO.java:13-33 的 11 个键里没有任何勾选态
                // （orderId/bizType/orderNo/orderType/goodsName/weightKg/receiver*/mailNo/pickupCode），
                // 小程序那一侧的 item.checked 也是死读（workbench.wxml:307-308 渲染、workbench.js 从不赋值）。
                // 装车勾选是本机的界面态，不是后端字段 ⇒ 解析层不再读键，演示报文也不再发。
                j("orderId" to 5001, "orderNo" to orderNos[0], "goodsName" to "土鸡蛋 2 箱", "senderName" to "李秀兰",
                    "senderMobile" to "13800004021", "stationName" to "云山村口站", "weightKg" to 12.5,
                    "pickupCode" to "8213", "freshFlag" to true, "deadline" to "08:20", "cargoCategory" to "生鲜农产",
                    "bizType" to "FREIGHT", "orderType" to 1,
                    "receiverName" to "周敏", "receiverMobile" to "13200007788", "receiverAddress" to "县城客运站旁"),
                j("orderId" to 5002, "orderNo" to orderNos[1], "goodsName" to "腊肉 3 件", "senderName" to "王建国",
                    "senderMobile" to "13700008830", "stationName" to "飞龙村站", "weightKg" to 8.2,
                    "pickupCode" to "4470", "freshFlag" to true, "deadline" to "08:45", "cargoCategory" to "生鲜农产",
                    "bizType" to "FREIGHT", "orderType" to 1,
                    "receiverName" to "山乡食堂", "receiverMobile" to "18600002210", "receiverAddress" to "石桥驿站"),
                j("orderId" to 7001, "orderNo" to "MP20260927001", "goodsName" to "云山土鸡蛋", "senderName" to "云山村集体合作社",
                    "senderMobile" to "13800004021", "stationName" to "云山村口站", "weightKg" to 5.0,
                    "pickupCode" to "", "freshFlag" to true, "deadline" to "08:50", "cargoCategory" to "山蛋",
                    "bizType" to "PRODUCT", "orderType" to 0,
                    "receiverName" to "赵先生", "receiverMobile" to "13500002211", "receiverAddress" to "县城兴隆街 12 号"),
                j("orderId" to 5004, "orderNo" to orderNos[3], "goodsName" to "茶叶礼盒 2 件", "senderName" to "陈立",
                    "senderMobile" to "15000006644", "stationName" to "兴隆场站", "weightKg" to 4.0,
                    "pickupCode" to "3082", "freshFlag" to false, "deadline" to "09:10", "cargoCategory" to "礼品",
                    "bizType" to "MAIL", "orderType" to 3, "mailNo" to "SF7788990011",
                    "receiverName" to "县城代收点", "receiverMobile" to "13300009911", "receiverAddress" to "快递共配仓"),
            )

            // 演示面与后端同形：键名逐个抄 AppDriverEarningsRespVO:14-26 + AppDriverEarningsRecordRespVO:14-26，
            // createTime 按 YudaoJacksonAutoConfiguration:43 的契约发 EpochMillis（不是 ISO 串）。
            // 已删：weekAmount/monthAmount/totalWeight/todayEarnings/totalEarnings/orderCount/items/date/amount/type
            // ——后端全模块 0 生产者，演示面继续发的话就还是第四个真相源。
            path.endsWith("/driver/earnings") -> j(
                "todayAmount" to 86.5, "totalAmount" to 24680.0,
                "totalOrders" to 18, "todayOrders" to 4,
                "shiftCount" to 3, "pendingCount" to 2,
                "records" to a(
                    j("orderNo" to orderNos[0], "goodsName" to "土鸡蛋 2 箱", "totalAmount" to 14.0,
                        "weightKg" to 12.5, "status" to 3, "statusName" to "已发车", "createTime" to 1790558040000L),
                    j("orderNo" to orderNos[1], "goodsName" to "腊肉 3 件", "totalAmount" to 12.0,
                        "weightKg" to 8.2, "status" to 2, "statusName" to "已分配", "createTime" to 1790558640000L),
                    j("orderNo" to orderNos[2], "goodsName" to "山核桃 1 袋", "totalAmount" to 9.0,
                        "weightKg" to 1.5, "status" to 4, "statusName" to "已完成", "createTime" to 1790472840000L),
                    j("orderNo" to "SH20260927021", "goodsName" to "富硒大米 4 袋", "totalAmount" to 18.5,
                        "weightKg" to 20.0, "status" to 4, "statusName" to "已完成", "createTime" to 1790488920000L),
                    j("orderNo" to orderNos[3], "goodsName" to "茶叶礼盒 2 件", "totalAmount" to 11.0,
                        "weightKg" to 4.0, "status" to 1, "statusName" to "已入池", "createTime" to 1790383800000L),
                    j("orderNo" to "SH20260926014", "goodsName" to "红花椒 5 袋", "totalAmount" to 22.0,
                        "weightKg" to 5.5, "status" to 4, "statusName" to "已完成", "createTime" to 1790388900000L),
                ),
            )

            path.endsWith("/driver/handovers") -> a(
                j("id" to 91, "orderNo" to orderNos[0], "goodsName" to "土鸡蛋 2 箱", "weightKg" to 12.5,
                    "fromStation" to "双河口换乘站", "toStation" to "县城客运站", "fromPlate" to "渝A·8K231",
                    "toPlate" to "渝B·5M107", "status" to "待接货", "action" to "accept",
                    "createTime" to "2026-09-28 10:52",
                    "itemCount" to 6, "fromDriverName" to "王有德", "toDriverName" to "罗建平",
                    "transferStation" to "双河口换乘站", "handoverTime" to "2026-09-28 11:05"),
                j("id" to 92, "orderNo" to orderNos[1], "goodsName" to "腊肉 3 件", "weightKg" to 8.2,
                    "fromStation" to "云山村口站", "toStation" to "双河口换乘站", "fromPlate" to "渝A·8K231",
                    "toPlate" to "渝A·8K231", "status" to "运输中", "action" to "arrive-dest",
                    "createTime" to "2026-09-28 09:31",
                    "itemCount" to 3, "fromDriverName" to "王有德", "toDriverName" to "王有德",
                    "transferStation" to "双河口换乘站", "handoverTime" to "2026-09-28 11:05"),
            )

            path.endsWith("/driver/legs") -> legsJson(1)

            path.endsWith("/driver/current-leg") -> j(
                "legId" to "L1", "seq" to 1, "fromStation" to "云山村口站", "toStation" to "双河口换乘站",
                "vehiclePlate" to "渝A·8K231", "driverName" to "王有德", "status" to 7, "statusName" to "运输中",
                "statusLabel" to "运输中",
                "etaMinutes" to 18, "action" to "start", "stations" to stationsJson(k1),
                "orderCount" to 3, "cargoKg" to 24.7,
                "orderNo" to orderNos[0], "distanceKm" to 24.6, "durationMinutes" to 52,
                "navigationSource" to "amap",
                "navigationPolyline" to polylineJson(k1),
            )

            path.endsWith("/driver/route") -> j(
                "shiftCode" to "K1-0830",
                "lineName" to "K1 县城 ⇄ 云山村",
                "dataSource" to "DEMO",
                "distanceMeters" to 24600,
                "etaMinutes" to 62,
                "deviated" to false,
                "vehicleLatitude" to movingGeo(k1, now).lat,
                "vehicleLongitude" to movingGeo(k1, now).lng,
                "stations" to stationsJson(k1),
                "polyline" to polylineJson(k1),
                "steps" to a(*guideOf(now).toTypedArray()),
            )

            path.endsWith("/driver/position") -> j(
                "latitude" to movingGeo(k1, now).lat, "longitude" to movingGeo(k1, now).lng,
                "speed" to 27, "dataSource" to "SIMULATED", "nextStation" to shiqiao.name, "etaMinutes" to 12,
            )

            path.endsWith("/product-order/trace") -> j(
                "orderNo" to "MP20260928001",
                "vehiclePlate" to "渝A·8K231",
                "driverName" to "王有德",
                "shiftCode" to "K1-0830",
                "deliveryStation" to "县城客运站",
                "latitude" to movingGeo(k1, now).lat,
                "longitude" to movingGeo(k1, now).lng,
                "dataSource" to "SIMULATED",
                "stations" to stationsJson(k1),
                "track" to polylineJson(k1.subList(0, 6)),
                "nodes" to nodesJson("MP20260928001", 5, "渝A·8K231", "K1-0830"),
            )

            path.endsWith("/auth/login") || path.endsWith("/auth/sms-login") -> j(
                "userId" to 10086, "token" to "demo-android-token", "expiresTime" to "2026-10-28 00:00",
                "refreshToken" to "demo-refresh-token",
            )

            // 写操作：后端不通时返回成功 ack（demo=true），页面必须提示"演示模式：本次操作未提交后端"
            path.endsWith("/send/create") -> j(
                "orderId" to 5009, "orderNo" to "SH20260928109", "pickupCode" to "6174", "totalAmount" to 12.5,
            )

            path.endsWith("/product-order/create") -> j("orderId" to 7009, "orderNo" to "MP20260928109")

            path.contains("/driver/depart") -> j("ok" to true, "state" to "RUNNING", "shiftCode" to "K1-0830")

            path.contains("/driver/arrive") -> j("ok" to true, "state" to "STOPPED", "nextStation" to yunshan.name)

            path.contains("/driver/location") -> j("ok" to true, "accepted" to true, "serverTime" to "11:12")

            path.contains("/driver/leg/") -> j("ok" to true, "action" to path.substringAfterLast('/'))

            path.contains("/infra/file/upload") -> j("url" to "")

            path.contains("/driver/pickup-confirm") || path.contains("/driver/deliver") ||
                path.contains("/driver/pickup-verify") || path.contains("/driver/product-load") ||
                path.contains("/driver/product-deliver") || path.contains("/driver/handover/confirm") ||
                path.endsWith("/member/user/update") || path.endsWith("/user/update-password") ||
                path.endsWith("/address/create") || path.endsWith("/address/update") ||
                path.endsWith("/address/delete") || path.endsWith("/feedback/create") ||
                path.endsWith("/notification/read-all") || path.contains("/notification/read") ||
                path.contains("/send/confirm-station-action") || path.contains("/product-order/cancel") ||
                path.contains("/auth/logout") -> j("ok" to true)

            else -> null
        }
        return Api.wrap(data ?: JSONObject())
    }

    private fun notificationsJson(): JSONArray = a(
        j("id" to 1, "title" to "班车快到了", "content" to "渝A·8K231 距石桥驿站约 8 分钟，请提前到站点位等候取件。",
            "eventType" to "CARRIER_APPROACHING", "eventTypeName" to "到车提醒", "level" to "ACTION_REQUIRED",
            "actionRequired" to true, "type" to "ARRIVAL", "readStatus" to 0,
            "createTime" to "2026-09-28 11:02", "orderNo" to orderNos[0], "orderId" to 5001),
        j("id" to 2, "title" to "已发车", "content" to "您的寄货 SH20260928001 已随 K1-0830 班次发出，预计 11:40 到达石桥驿站。",
            "eventType" to "SHIFT_DEPARTED", "eventTypeName" to "班次发车", "level" to "INFO",
            "type" to "DEPART", "readStatus" to 0,
            "createTime" to "2026-09-28 10:20", "orderNo" to orderNos[0], "orderId" to 5001),
        j("id" to 3, "title" to "交接完成", "content" to "SH20260927018 已在双河口换乘站完成二段交接，承运车辆改为渝B·5M107。",
            "eventType" to "HANDOVER_CONFIRMED", "eventTypeName" to "交接完成", "level" to "SUCCESS",
            "type" to "HANDOVER", "readStatus" to 0,
            "createTime" to "2026-09-27 16:41", "orderNo" to orderNos[2], "orderId" to 5003),
        j("id" to 4, "title" to "取件码已生成", "content" to "SH20260926009 取件码 3082，到站后凭码取件，24 小时内有效。",
            "eventType" to "PICKUP_CODE_CREATED", "eventTypeName" to "取件码", "level" to "INFO",
            "type" to "CODE", "readStatus" to 1,
            "createTime" to "2026-09-26 09:12", "orderNo" to orderNos[3], "orderId" to 5004),
        j("id" to 5, "title" to "商城订单已妥投", "content" to "MP20260927001 已由司机王有德交付至县城客运站，感谢使用乡邮智联。",
            "eventType" to "PRODUCT_DELIVERED", "eventTypeName" to "订单妥投", "level" to "SUCCESS",
            "type" to "DELIVERED", "readStatus" to 1,
            "createTime" to "2026-09-27 17:08", "orderNo" to "MP20260927001", "orderId" to 7002),
        j("id" to 6, "title" to "调度提醒", "content" to "明日 K1 早班因雨雾提前 15 分钟发车，寄生鲜的村民请提前送件。",
            "eventType" to "SYSTEM_NOTICE", "eventTypeName" to "系统公告", "level" to "WARNING",
            "type" to "SYSTEM", "readStatus" to 1,
            "createTime" to "2026-09-28 07:30", "orderId" to 0),
    )
}
