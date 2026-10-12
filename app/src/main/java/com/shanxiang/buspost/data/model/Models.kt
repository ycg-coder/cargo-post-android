package com.shanxiang.buspost.data.model

import com.shanxiang.buspost.core.ui.BusTone
import com.shanxiang.buspost.core.ui.StopState
import org.json.JSONArray
import org.json.JSONObject

/** 角色：用户与司机在登录时就分流，两套导航图与底部 tab 完全独立 */
enum class Role { USER, DRIVER }

// ---------- JSON 宽容取值：后端字段命名与小程序端偶有出入，多键兜底避免整页空白 ----------
fun JSONArray.objList(): List<JSONObject> {
    val out = ArrayList<JSONObject>(length())
    for (i in 0 until length()) optJSONObject(i)?.let { out.add(it) }
    return out
}

/** 字符串数组（车道名等）：后端与演示集都可能直接下发 string 列表 */
fun JSONArray.strList(): List<String> {
    val out = ArrayList<String>(length())
    for (i in 0 until length()) {
        val v = opt(i)
        if (v is JSONObject) {
            val s = v.multiStr("name", "label", "text")
            if (s.isNotEmpty()) out.add(s)
        } else {
            val s = optString(i)
            if (s.isNotEmpty()) out.add(s)
        }
    }
    return out
}

/** 站点所属线路：后端两种形态都出现过（字符串数组 / 对象数组），统一取名字 */
fun JSONArray.lineNames(): List<String> = strList()

fun JSONObject.multiStr(vararg keys: String): String {
    for (k in keys) {
        val v = opt(k)
        if (v != null && v != JSONObject.NULL) {
            // 数组/对象不当作字符串：高德逆地理在直辖市下 city 返回空数组 []，
            // 直接 toString() 会得到字面量 "[]" 并渲染成「重庆市 [] 垫江县」。
            if (v is JSONArray || v is JSONObject) continue
            val s = v.toString()
            if (s.isNotEmpty() && s != "null") return s
        }
    }
    return ""
}

fun JSONObject.multiNum(vararg keys: String): Double {
    for (k in keys) {
        val v = opt(k)
        if (v is Number) return v.toDouble()
        if (v is String && v.isNotBlank()) v.toDoubleOrNull()?.let { return it }
    }
    return 0.0
}

fun JSONObject.multiBool(vararg keys: String): Boolean {
    for (k in keys) {
        val v = opt(k)
        if (v is Boolean) return v
        if (v is Number) return v.toInt() != 0
        if (v is String && v.isNotBlank()) return v == "true" || v == "1"
    }
    return false
}

/**
 * 可空数值读法：键不存在 / 值为 `null` 都返回 **null**，不是 0。
 * 存在的理由（审计 §14）：`distanceKm`/`walkMinutes` 在后端的三个早退分支里根本不赋值
 * （`AppSendReachabilityService.evaluate:53-57 / :68-72 / :93-97`），用 `multiNum` 会把"接口没给"渲染成"0 公里 / 0 分钟"。
 */
fun JSONObject.multiNumOrNull(vararg keys: String): Double? {
    for (k in keys) {
        val v = opt(k)
        if (v is Number) return v.toDouble()
        if (v is String && v.isNotBlank()) v.toDoubleOrNull()?.let { return it }
    }
    return null
}

/** 可空布尔读法：`reachable` 缺失 = 判定未知（UNKNOWN），不能塌成 false 后编一条"不可达原因" */
fun JSONObject.multiBoolOrNull(vararg keys: String): Boolean? {
    for (k in keys) {
        val v = opt(k)
        if (v is Boolean) return v
        if (v is Number) return v.toInt() != 0
        if (v is String && v.isNotBlank()) return v == "true" || v == "1"
    }
    return null
}

fun JSONObject.multiArr(vararg keys: String): List<JSONObject> {
    for (k in keys) {
        val a = optJSONArray(k)
        if (a != null && a.length() > 0) return a.objList()
    }
    return emptyList()
}

/**
 * 时间串归一。后端 `YudaoJacksonAutoConfiguration:43,62` 把 LocalDateTime 注册成
 * `TimestampLocalDateTimeSerializer`（LocalDateTime ↔ EpochMillis），所以真机拿到的 createTime 是
 * 13 位数字而不是 ISO 串；小程序侧同样是 `new Date(timestamp)`。旧实现只处理 ISO 串，会把
 * "1759049640000" 原样当时间显示（13 位数字直接进 UI），这里按同一契约把纯数字毫秒转成本机时区可读串。
 */
private fun ts(v: String): String {
    val millis = if (v.length in 12..14 && v.all { it.isDigit() }) v.toLongOrNull() else null
    if (millis != null) {
        return java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA).format(java.util.Date(millis))
    }
    return v.replace('T', ' ').substringBefore('.').take(16)
}

/**
 * 金额数字串（整数不补 .00，其余两位小数）：与 UI 侧 formatPrice 同口径。
 * 数据层不反向依赖 ui/，所以这里独立实现一份，QuoteResult 的"未给/金额"文案才能被 JVM 契约测试断言。
 */
private fun yuan(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else String.format(java.util.Locale.CHINA, "%.2f", v)

fun JSONObject.timeOf(vararg keys: String): String = ts(multiStr(*keys, "createTime", "create_time"))

/**
 * 事件时刻：与 [timeOf] 同一套 epoch 毫秒/ISO 口径，但**不回落 `createTime`**。
 * 位置上报时间、装货时间这类"某件事什么时候发生"的字段，回落成订单创建时间就是编了一个没发生过的时刻。
 */
fun JSONObject.reportTimeOf(vararg keys: String): String = ts(multiStr(*keys))

// ---------- 地理 ----------
data class Geo(val lat: Double, val lng: Double)

/**
 * 逆地理结果（高德 regeo，Web 服务 key）。
 * 用途：① 取 adcode 供天气按区县查询；② 取乡镇名做首页定位文案（小程序 location.js amapReverse 同口径）。
 * 注意「直辖市 city 为空数组」——解析层已跳过数组，不会渲染出 "[]"。
 */
data class ReverseGeo(
    val adcode: String,
    val province: String,
    val city: String,
    val district: String,
    val township: String,
    val formatted: String,
) {
    /** 展示用定位名：优先「区县 + 乡镇」，退化为区县、市、省 */
    val displayName: String
        get() = when {
            district.isNotEmpty() && township.isNotEmpty() -> "$district$township"
            district.isNotEmpty() -> district
            city.isNotEmpty() -> city
            else -> province
        }

    companion object {
        val EMPTY = ReverseGeo("", "", "", "", "", "")
    }
}

object GeoMath {
    /** Haversine 直线距离（米），与小程序同源算法 */
    fun distance(a: Geo, b: Geo): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val la1 = Math.toRadians(a.lat)
        val la2 = Math.toRadians(b.lat)
        val h = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(la1) * kotlin.math.cos(la2) * kotlin.math.sin(dLng / 2) * kotlin.math.sin(dLng / 2)
        return 2 * r * kotlin.math.asin(kotlin.math.sqrt(h))
    }

    fun bearing(from: Geo, to: Geo): Double {
        val dLng = Math.toRadians(to.lng - from.lng)
        val la1 = Math.toRadians(from.lat)
        val la2 = Math.toRadians(to.lat)
        val y = kotlin.math.sin(dLng) * kotlin.math.cos(la2)
        val x = kotlin.math.cos(la1) * kotlin.math.sin(la2) - kotlin.math.sin(la1) * kotlin.math.cos(la2) * kotlin.math.cos(dLng)
        return (Math.toDegrees(kotlin.math.atan2(y, x)) + 360) % 360
    }

    /** 方位角差换算转向动作，用于内置导航的车道/转向指引 */
    fun turnOf(prevBearing: Double, nextBearing: Double): NavIcon {
        var d = (nextBearing - prevBearing + 540) % 360 - 180
        return when {
            d < -110 -> NavIcon.UTURN_LEFT
            d < -55 -> NavIcon.TURN_LEFT
            d < -20 -> NavIcon.SLIGHT_LEFT
            d <= 20 -> NavIcon.STRAIGHT
            d <= 55 -> NavIcon.SLIGHT_RIGHT
            d <= 110 -> NavIcon.TURN_RIGHT
            else -> NavIcon.UTURN_RIGHT
        }
    }

    fun bounds(points: List<Geo>): Pair<Geo, Geo>? {
        if (points.isEmpty()) return null
        var minLat = Double.MAX_VALUE; var maxLat = -Double.MAX_VALUE
        var minLng = Double.MAX_VALUE; var maxLng = -Double.MAX_VALUE
        points.forEach {
            minLat = minOf(minLat, it.lat); maxLat = maxOf(maxLat, it.lat)
            minLng = minOf(minLng, it.lng); maxLng = maxOf(maxLng, it.lng)
        }
        return Geo(minLat, minLng) to Geo(maxLat, maxLng)
    }
}

// ---------- 会话 ----------
data class Account(
    val id: Long,
    val mobile: String,
    val nickname: String,
    val avatar: String,
    val gender: Int,
    val role: Role,
    val token: String,
) {
    companion object {
        fun parse(o: JSONObject, token: String, fallbackRole: Role): Account = Account(
            id = o.multiNum("id", "userId").toLong(),
            mobile = o.multiStr("mobile", "phone"),
            nickname = o.multiStr("nickname", "realName", "name").ifEmpty { "用户" },
            avatar = o.multiStr("avatar", "avatarUrl"),
            gender = o.multiNum("gender", "sex").toInt(),
            role = when (o.multiStr("role", "userRole").lowercase()) {
                "driver" -> Role.DRIVER
                "user", "member" -> Role.USER
                else -> fallbackRole
            },
            token = token,
        )
    }
}

// ---------- 公告 / 天气 ----------
data class Notice(val id: Long, val title: String, val content: String, val time: String) {
    companion object {
        fun parse(o: JSONObject) = Notice(
            o.multiNum("id").toLong(),
            o.multiStr("title", "noticeTitle"),
            o.multiStr("content", "noticeContent", "summary"),
            o.timeOf("createTime", "publishTime"),
        )
    }
}

/** 逐日预报（高德 all 档下发；小程序首页 4 日预报同源） */
data class WeatherDay(
    val day: String,
    val icon: String,
    val tempHi: Int,
    val tempLo: Int,
    val condition: String,
)

/**
 * 天气。数据源优先级与小程序一致：高德天气（区县级）→ open-meteo → 空态。
 * mock=true 现在只表示"两路都没返回"（字段全空），页面出「天气未取到」，不再兜一组本地编的读数。
 */
data class WeatherInfo(    val text: String,
    val temperature: Int,
    val feel: Int,
    val wind: String,
    val source: String,
    val icon: String = "",
    val humidity: String = "",
    val tips: String = "",
    /**
     * 司机侧提示（与 `tips` 同源同刻，但说法按角色分）。
     * 以前只有一份货运口径的 `farmTips`（"给山货加盖苫布"），
     * 而司机要的是"因天气而变的操作动作"（车距/雾灯/是否延后发车）。
     * 空串 = 这一路天气没拿到 ⇒ 页面不许编一句安全提示。
     */
    val driverTips: String = "",
    val forecast: List<WeatherDay> = emptyList(),
    val mock: Boolean = false,
) {
    companion object {
        fun parse(o: JSONObject, source: String): WeatherInfo = WeatherInfo(
            text = o.multiStr("text", "weather", "condition"),
            temperature = o.multiNum("temperature", "temp").toInt(),
            feel = o.multiNum("feel", "feelsLike", "temperature").toInt(),
            wind = o.multiStr("wind", "windDesc").ifEmpty {
                val dir = o.multiStr("windDirection", "winddirection")
                val power = o.multiStr("windPower", "windpower")
                if (dir.isEmpty() && power.isEmpty()) "" else "风 $dir$power"
            },
            source = source,
            icon = o.multiStr("icon", "weatherIcon"),
            humidity = o.multiStr("humidity", "humidityText"),
            tips = o.multiStr("tips", "farmTip", "advice"),
            forecast = o.multiArr("forecast", "forecasts", "daily").map {
                WeatherDay(
                    day = it.multiStr("day", "date", "week"),
                    icon = it.multiStr("icon", "weatherIcon"),
                    tempHi = it.multiNum("tempHi", "high", "maxTemp", "tempMax", "daytemp").toInt(),
                    tempLo = it.multiNum("tempLo", "low", "minTemp", "tempMin", "nighttemp").toInt(),
                    condition = it.multiStr("condition", "text", "weather", "dayweather"),
                )
            },
            mock = o.multiBool("mock", "simulated"),
        )
    }
}

// ---------- 商品与商城 ----------

// ---------- 商品与商城 ----------
data class Product(
    val id: Long,
    val name: String,
    val subtitle: String,
    val price: Double,
    /** 划线价口径后端契约里没有（`AppProductRespVO.java:12-34` 十二键无一一对应），恒 null ⇒ 界面不提划线价 */
    val originPrice: Double? = null,
    val unit: String,
    /** 库存：真实键 `AppProductRespVO.java:30 stock`（Integer，可缺席）。缺键不许塌成 0 —— 0 会被算成"已售罄"并禁用提交。 */
    val stock: Int? = null,
    /** 销量口径同上：后端、小程序、演示面三方都无生产者，读出来只能是 null，不许渲染成"已售 0" */
    val sales: Int? = null,
    val image: String,
    val category: String,
    val village: String,
    val intro: String,
) {
    /** 产地村名：小程序卡片主标「来自某某村」，缺时回落到 village */
    val fromVillage: String get() = rawFromVillage.ifEmpty { village }
    /** 角标文案：小程序商品卡/详情主图的「巴士带货」 */
    val badge: String get() = rawBadge.ifEmpty { "巴士带货" }
    /** 只有后端真给了 0/负数才叫售罄；缺键（stock=null）不许自动变成"已售罄"（F-3 ⑯：null/0 不许自己变成业务结论） */
    val soldOut: Boolean get() = stock != null && stock <= 0
    /** 库存角标：缺键时明说"库存未给"，不许渲染成"库存 0" */
    val stockText: String get() = stock?.let { "库存 $it" } ?: "库存未给"
    /** 原始字段值由 parse 填入，见下方伴生对象 */
    var rawFromVillage: String = ""
    var rawBadge: String = ""

    companion object {
        fun parse(o: JSONObject): Product {
            val p = Product(
                id = o.multiNum("id", "productId").toLong(),
                name = o.multiStr("name", "productName", "title"),
                subtitle = o.multiStr("subtitle", "shortDesc", "description").take(40),
                price = o.multiNum("price", "sellPrice"),
                // originPrice/sales 两处读法已撤（F-3 ②）：键名在后端 AppProductRespVO、小程序商品面
                // （只读 price：pages/goods/detail.js:69）与演示面三方都无生产者，留着就是第四个真相源。
                unit = o.multiStr("unit").ifEmpty { "份" },
                stock = o.multiNumOrNull("stock", "inventory")?.toInt(),
                // imageUrl 是小程序 product-img.js 的首选字段，必须排在 picUrl 之前
                image = o.multiStr("imageUrl", "picUrl", "image", "imgUrl", "avatar"),
                category = o.multiStr("category", "categoryName", "tag"),
                village = o.multiStr("village", "origin", "producer"),
                intro = o.multiStr("intro", "detail", "description"),
            )
            p.rawFromVillage = o.multiStr("fromVillage", "villageName", "originVillage")
            p.rawBadge = o.multiStr("badge", "cornerLabel")
            return p
        }
    }
}

data class OrderItem(
    val name: String,
    val count: Int,
    val price: Double,
    val image: String,
    val productId: Long = 0L,
    /** 行金额：后端下发 amount，缺时由 price×count 兜底 */
    val amount: Double = 0.0,
) {
    val lineAmount: Double get() = if (amount > 0) amount else price * count
}

data class ProductOrder(
    val id: Long,
    val orderNo: String,
    val status: Int,
    val items: List<OrderItem>,
    val amount: Double,
    val receiverName: String,
    val receiverMobile: String,
    val address: String,
    val vehiclePlate: String,
    val driverName: String,
    val shiftCode: String,
    val deliveryStation: String,
    val createTime: String,
    val remark: String,
    /** 后端状态文案优先，缺时本地按 status 兜底 */
    val statusName: String = "",
    /** 班次线路名 */
    val routeName: String = "",
    val driverMobile: String = "",
    /** 司机是否已到达交付站点 */
    val driverArrived: Boolean = false,
    /** 装车时间 / 妥投时间（小程序配送进度时间线） */
    val loadTime: String = "",
    val deliverTime: String = "",
    /** 装车核验凭证 / 妥投凭证照片 */
    val loadPhotoUrl: String = "",
    val deliverPhotoUrl: String = "",
) {
    val statusLabel: String get() = statusName.ifEmpty { productStatusLabel(status) }

    /** 语义色跟着 [ProductOrderStatus] 的码走，页面不得自己 `status == 1` 上色（§18） */
    val statusTone: BusTone get() = ProductOrderStatus.toneOf(status)

    /** 货已在路上 = 1 已发货（旧本地表把 1 写成"配送中"，动效与文案一起错位，§33） */
    val onTheWay: Boolean get() = ProductOrderStatus.of(status) == ProductOrderStatus.DELIVERED

    /** 允许操作：看产地溯源只在 1 已发货 / 2 已完成（§18"允许操作"列，页面不再写 status == 1） */
    val canTrace: Boolean
        get() = ProductOrderStatus.of(status)
            ?.let { it == ProductOrderStatus.DELIVERED || it == ProductOrderStatus.COMPLETED } == true

    /** 允许操作：只有 0 待发货能取消；已发货后取消会被后端拒，入口不该出现 */
    val canCancel: Boolean get() = ProductOrderStatus.of(status) == ProductOrderStatus.PENDING_DELIVERY

    /** 2 已完成：交付凭证/送达态 */
    val isCompleted: Boolean get() = ProductOrderStatus.of(status) == ProductOrderStatus.COMPLETED
    companion object {
        fun parse(o: JSONObject) = ProductOrder(
            id = o.multiNum("id", "orderId").toLong(),
            orderNo = o.multiStr("orderNo", "bizOrderNo", "no"),
            status = o.multiNum("status", "orderStatus").toInt(),
            items = o.multiArr("items", "products", "orderItems").map {
                OrderItem(
                    it.multiStr("productName", "name", "goodsName"),
                    it.multiNum("count", "quantity", "num").toInt().coerceAtLeast(1),
                    it.multiNum("price", "unitPrice"),
                    it.multiStr("productImage", "imageUrl", "picUrl", "image", "imgUrl"),
                    it.multiNum("productId", "id").toLong(),
                    it.multiNum("amount", "totalPrice", "lineAmount"),
                )
            },
            amount = o.multiNum("totalPrice", "amount", "payAmount"),
            receiverName = o.multiStr("receiverName"),
            receiverMobile = o.multiStr("receiverMobile"),
            address = o.multiStr("receiverAddress", "address", "detailAddress"),
            vehiclePlate = o.multiStr("vehiclePlate", "plate"),
            driverName = o.multiStr("driverName"),
            shiftCode = o.multiStr("shiftCode"),
            deliveryStation = o.multiStr("deliveryStation", "targetStation", "stationName", "deliverStationName"),
            createTime = o.timeOf(),
            remark = o.multiStr("remark"),
            statusName = o.multiStr("statusName"),
            routeName = o.multiStr("routeName", "lineName"),
            driverMobile = o.multiStr("driverMobile", "driverPhone"),
            driverArrived = o.multiBool("driverArrived", "arrived"),
            loadTime = o.timeOf("loadTime", "loadedTime"),
            deliverTime = o.timeOf("deliverTime", "deliveredTime"),
            loadPhotoUrl = o.multiStr("loadPhotoUrl", "loadPhoto"),
            deliverPhotoUrl = o.multiStr("deliverPhotoUrl", "deliverPhoto"),
        )
    }
}

/**
 * 商城订单文案。**码表在 [ProductOrderStatus]（后端 `ProductOrderStatusEnum`）**，这里只是旧调用点的转发。
 * 注意 1 是"已发货"（§33：本地旧表把它写成"配送中"就是从这里来的）。
 */
fun productStatusLabel(s: Int) = ProductOrderStatus.labelOf(s)

/**
 * 货运订单状态文案：**按 `TransportOrderStatusEnum` 的枚举码 keyed，0..13，不是顺序号**。
 * 审核前置态 6/7/8 的码值比"流程位置"大，任何按数值排序的表都会把它们读错（§18 的事故）。
 * 逐码来源与"允许/禁止操作"见 `docs/STATUS_MAPPING.md`；表本体在 [FreightOrderStatus]。
 * 页面应优先显示后端下发的 `statusName`（`ParcelTrace.statusLabel` / `SendOrder.statusLabel`），
 * 这个函数只在后端没给名字时兜底。
 */
fun freightStatusLabel(s: Int) = FreightOrderStatus.labelOf(s)

fun freightTone(s: Int) = FreightOrderStatus.toneOf(s)

fun stopStateOf(done: Boolean, current: Boolean) = when {
    current -> StopState.CURRENT
    done -> StopState.DONE
    else -> StopState.PENDING
}

// ---------- 站点 / 车辆 / 线路 ----------
data class Station(
    val id: Long,
    val name: String,
    val geo: Geo,
    val plannedMinutes: Int = 0,
    val orderCount: Int = 0,
    val distanceMeters: Double = 0.0,
    val routes: List<String> = emptyList(),
    /** 途经站序号（小程序站点时间轴 +N 分钟标注） */
    val sequenceNo: Int = 0,
    /** 是否自建站：后端站点 VO 没有这个布尔键，真实判定口径是 `dataSource`（见 isRealTransit）⇒ 恒 null */
    val custom: Boolean? = null,
    /** 站点来源：REAL_TRANSIT 现实公交站 / 项目自建站（页面要分标签） */
    val dataSource: String = "",
) {
    /** 现实公交站点（高德 POI），与项目自建站区分展示 */
    val isRealTransit: Boolean get() = dataSource.contains("REAL")

    companion object {
        fun parse(o: JSONObject): Station {
            // 真实接口（/bus/nearby）下发的是 distanceKm（公里），小程序按米使用；
            // 缺少换算会让"距离最近站点"永远显示 0，故两种单位都认。
            val km = o.multiNum("distanceKm")
            // 只认 `distance`（演示面 /bus/nearby 的米制形状）；`distanceM`/`meters` 三方都无生产者，删。
            // 真实接口给的是 distanceKm（AppBusNearbyRespVO.NearbyStation:189），下一行负责换算。
            val meters = o.multiNum("distance")
            val resolved = when {
                meters > 0 -> meters
                km > 0 -> km * 1000
                else -> 0.0
            }
            return Station(
                id = o.multiNum("id", "stationId").toLong(),
                name = o.multiStr("name", "stationName", "title"),
                geo = Geo(o.multiNum("latitude", "lat"), o.multiNum("longitude", "lng")),
                // 只认后端有的一个键：plannedMinutes（AppDriverStationRespVO:17 / RouteStationRespVO:32 /
                // AppBusLineRespVO:54）。`minutes` 与 `offsetMinutes` 三方（后端 VO、小程序、演示集）都查不到生产者，删。
                plannedMinutes = o.multiNum("plannedMinutes").toInt(),
                orderCount = o.multiNum("orderCount", "cargoCount", "count").toInt(),
                distanceMeters = resolved,
                // lines 在真实接口里是字符串数组，演示集里是对象数组，统一取名字
                routes = o.optJSONArray("routes")?.lineNames()
                    ?: o.optJSONArray("lines")?.lineNames()
                    ?: emptyList(),
                sequenceNo = o.multiNum("sequenceNo", "seq", "sort").toInt(),
                // custom 读法已撤（F-3 ②）：`custom`/`selfBuilt`/`isCustom` 三个键名三方都无生产者，
                // 而全工程也没有任何 UI 读这个字段——站点类型一律按 dataSource 判（isRealTransit）。
                dataSource = o.multiStr("dataSource", "provider"),
            )
        }
    }
}

/**
 * 车辆运行状态。两个端点给的码制不同，这里一次收口（审计 §38/§8）：
 * `/bus/nearby` 给字符串（`AppBusNearbyRespVO:14-20`：RUNNING / IDLE / ARRIVED / NO_LOCATION），
 * `/bus/lines` 内嵌 `AppBusRespVO.status` 给整数（`:28-29`，取自 `MonitoringVehicleRespVO:21-22`：0 空闲 / 1 在途 / **2 停用**）。
 * 整数 2 是"停用"不是"已到站"；旧实现把 2 映射成 ARRIVED、还把不存在的 3 映射成 NO_LOCATION，都是凭空造的码。
 */
enum class VehicleStatus { RUNNING, IDLE, ARRIVED, NO_LOCATION, OFF_DUTY, UNKNOWN }

/**
 * 位置新鲜度。后端真实只发三种（`AppBusNearbyRespVO:113` + `AppBusServiceImpl:623-632`）：
 * REAL_FRESH / REAL_STALE / NO_LOCATION；`SIMULATED` **不是后端值**，只有演示面用它自标。
 * 注意 nearby 列表实测拿不到 NO_LOCATION：`AppBusServiceImpl:392-394` 已把无坐标车辆过滤掉，
 * 这个分支是给 `/bus/lines`（`:102` 允许有班次无坐标的车）和将来变更留的。
 */
enum class LocationSource {
    REAL_FRESH, REAL_STALE, NO_LOCATION, SIMULATED, UNKNOWN;

    /**
     * 已定性的枚举值的统一文案（§8 单一映射点）：线路车辆侧与货运轨迹侧共用，页面不得再自己拼码。
     * UNKNOWN 故意不给文案 —— 两条链路对"新鲜度未给"的兜底能力不同（[BusVehicle] 还能从 dataSource 猜一手，
     * 货运轨迹猜不出来），强行合并会逼着其中一边说谎。
     */
    val knownLabel: String?
        get() = when (this) {
            REAL_FRESH -> "实时"
            REAL_STALE -> "位置可能过期"
            NO_LOCATION -> "位置暂不可用"
            // 后端把"位置由班次计划推算出来"的车原样标成 SIMULATED（2026-10-08 本机 48080 实测：
            // /bus/nearby 与 /bus/lines 的 dataSource、locationSource 均为 SIMULATED）。这不是"演示数据"：
            // 车、线路、站都是后端表里的真记录，只有位置是推算的。小程序同一值念「位置推算」，
            // 后端 docs/transport/business-rules.md 也要求"明确标注位置推算"，旧文案「模拟演示」两头都不对，
            // 还会把真车说成假车（用户口径：产品形态不出现演示字样）。
            SIMULATED -> "位置推算"
            UNKNOWN -> null
        }

    companion object {
        /** 唯一解析点。`/send/track` 的 carrierLocationSource 只发 REAL_FRESH / REAL_STALE（AppSendController:474）。 */
        fun of(raw: String): LocationSource = when (raw.uppercase(java.util.Locale.ROOT)) {
            "REAL_FRESH" -> REAL_FRESH
            // dataSource 只有 REAL/REAL_STALE（AppBusNearbyRespVO:110），它说的是"位置来自真实上报"，不说新鲜度；
            // /bus/lines 的 AppBusRespVO 根本没有 locationSource 键，所以这里不许把 REAL 直接升格成 REAL_FRESH
            // （否则线路页在真接口上会写"实时"，而实际新鲜度未给 → 走 UNKNOWN 的「实时（新鲜度未标）」）
            "REAL_STALE", "STALE" -> REAL_STALE
            "NO_LOCATION" -> NO_LOCATION
            "SIMULATED", "SIM" -> SIMULATED
            else -> UNKNOWN
        }
    }
}

data class BusVehicle(
    val id: String,
    val plate: String,
    /** 车的位置：后端没给经纬度就是 null（`AppBusRespVO:38/:41` 可为空），不许塌成 (0,0) 画到海里去 */
    val geo: Geo?,
    /** 速度：`AppBusRespVO:59`，仅在途时有值（`MonitoringVehicleRespVO:51-52`） */
    val speedKmh: Int?,
    val dataSource: String,
    val nextStation: String,
    /** 到下一站分钟：`AppBusServiceImpl:134/:719/:728` 在"未在途/算不出"时**显式给 null**，0 分钟=车已经到了 */
    val etaMinutes: Int?,
    val lineName: String,
    val shiftCode: String,
    /**
     * 线路编号：真实车辆 VO（`AppBusRespVO` 19 字段 / `AppBusNearbyRespVO.NearbyBus` 20 字段，2026-09-29 逐字段核对）
     * 里**没有** `routeId` 这个键，车辆只带 `routeName` —— 所以这里在真接口下恒为空串，
     * 想跳线路详情必须按 `lineName` 回查 `/bus/lines` 的 `routeId`（审计 §69）。字段留着是为了兼容旧演示/旧接口，不许删。
     */
    val routeId: String = "",
    // ---- 小程序 index.js 车辆卡字段（到站预估/线路/状态） ----
    val startStation: String = "",
    val endStation: String = "",
    val status: VehicleStatus = VehicleStatus.UNKNOWN,
    val currentStation: String = "",
    /** 待发车分钟数（未发车班次，`AppBusRespVO:46-47`：语义是"还有多久发车"，不是到站） */
    val waitDepartureMinutes: Int? = null,
    /** 距下一站剩余公里：`AppBusRespVO:52-53`，无可靠位置时 null */
    val distanceToNextStationKm: Double? = null,
    /**
     * 精确到下一站分钟（小数）：`AppBusRespVO.java:49-50`，由 `AppBusServiceImpl:140` 从统一位置快照原样透传，
     * 待发/无下一站时为 null。**只有 `/bus/lines` 内嵌的 `AppBusRespVO` 带这个键**，
     * `AppBusNearbyRespVO.NearbyBus`（`:81-150` 逐字段核过）里没有它 ⇒ 真接口下 nearby 车辆恒为 null，
     * 那种情形要按 [etaMinutes] 的端点语义走（见 [nextStopMinutes]）。审计 §72。
     */
    val etaToNextStationMinutes: Double? = null,
    /** 到站分钟的路网来源：后端只发 `SCHEDULE`（`AppBusServiceImpl:722/:730`）或真实路网标签（`RealRoadProviders`），见 [providerText] */
    val routeProvider: String = "",
    val lastLocationTime: String = "",
    val locationSource: LocationSource = LocationSource.UNKNOWN,
    /** 离用户最近的站点与到该站的预估 */
    val nearestStationName: String = "",
    val nearestStationEtaMinutes: Int? = null,
    val stopsToNearestStation: Int? = null,
    /** 线路进度 0-100：`AppBusRespVO:55-56`。nearby 端点不下发这个键，所以它是 null —— 进度条只许由它驱动 */
    val progress: Int? = null,
    /** 距用户直线距离：`AppBusNearbyRespVO:137-138`，无定位场景为 null；后端按它升序排（`AppBusServiceImpl:436-437` nullsLast） */
    val distanceKm: Double? = null,
    /** 模拟引擎状态：车在模拟运行中 / 模拟坐标 */
    val simRunning: Boolean = false,
    val simGeo: Geo? = null,
) {
    val isReal: Boolean get() = dataSource.contains("REAL") || locationSource == LocationSource.REAL_FRESH || locationSource == LocationSource.REAL_STALE
    val running: Boolean get() = status == VehicleStatus.RUNNING
    /** 状态文案：单一映射点，页面不得再自己拼码（§8） */
    val statusLabel: String
        get() = when (status) {
            VehicleStatus.RUNNING -> "在途"
            // 整数码 0 在后端是"空闲/已到站"（AppBusRespVO:28），分不清就不硬说"待发车"
            VehicleStatus.IDLE -> "待发/停靠"
            VehicleStatus.ARRIVED -> "已到站"
            VehicleStatus.NO_LOCATION -> "暂无定位"
            VehicleStatus.OFF_DUTY -> "停用"
            VehicleStatus.UNKNOWN -> "状态暂未知"
        }
    /** 数据来源文案：与小程序 index.js 的车辆来源口径同值（实时/位置可能过期/位置推算/位置暂不可用） */
    val sourceText: String
        get() = locationSource.knownLabel ?: when {
            dataSource.contains("STALE") -> "位置可能过期"
            dataSource.contains("REAL") -> "实时（新鲜度未标）"
            // 走到这里就是枚举与 dataSource 都没给结论：只能说"没给"，不能替后端认一笔"演示"。
            // 旧写法在这里兜「模拟演示」，于是 /bus/lines 那种根本没有 locationSource 键的报文
            // 一旦 dataSource 也不含 REAL/STALE，就被说成假数据 —— 而它可能只是新鲜度没标。
            else -> "位置来源未标"
        }

    /**
     * 司机自身位置端点（`GET /transport/driver/position` → `AppDriverPositionRespVO`，只有
     * `longitude`/`latitude`/`dataSource` **三个键**）的标签口径：这份报文里没有 `status`、没有 `nextStation`，
     * 所以本机不许说"在途"、也不许拼一个空站名（§14 没有值不许有业务语义；审计 §72）。
     */
    val positionOnlyTag: String
        get() = when {
            dataSource.isEmpty() -> "位置来源未给"
            status == VehicleStatus.UNKNOWN -> "位置来源 $dataSource（未给状态）"
            else -> "位置来源 $dataSource"
        }

    /**
     * "到下一站"的分钟数（向上取整、至少 1，与小程序 `detail.js:132-136` 的 `Math.max(1, ceil())` 同口径）：
     * - 精确值有 ⇒ 用它（只 `/bus/lines` 内嵌的 `AppBusRespVO` 下发，`AppBusServiceImpl:140`）；
     * - 精确值没给、但这是 nearby 形状 ⇒ 认 [etaMinutes]，它在 nearby **就是**到下一站（`fillEta:726-731`）。
     *   nearby 不下发 `progress`，所以 `progress == null` 是这里唯一观察得到的端点判据；
     * - lines 形状且精确值缺失 ⇒ 返回 null：此时 `etaMinutes` = 剩余进度 × 班次计划时长（`AppBusServiceImpl:130-132`），
     *   说的是"跑完剩余线路"，**不是到下一站**，只能走 [remainingRouteMinutesText]（审计 §72）。
     */
    val nextStopMinutes: Int?
        get() {
            val precise = etaToNextStationMinutes
            if (precise != null) return Math.max(1, kotlin.math.ceil(precise).toInt())
            return if (running && progress == null) etaMinutes else null
        }
    /** lines 形状只拿到"剩余全程"时的说法：口径必须写在文案里，不许缩成"约 N 分钟到下一站" */
    val remainingRouteMinutesText: String?
        get() = if (running && nextStopMinutes == null && etaMinutes != null)
            "剩余全程约 $etaMinutes 分钟（按线路进度与班次计划时长估算，不是到下一站）"
        else null

    /**
     * 卡片副券大数字（对齐小程序 index.js:464-466）：
     * 优先"离我最近那站还有几分钟"，其次**到下一站**分钟；两者都没给就是"—"/"待发"，**不能是 0**，
     * 也不许把"剩余全程"的分钟数顶到这个位置（那个数字不是到站时间）。
     */
    val etaValueText: String
        get() = when {
            nearestStationEtaMinutes != null -> "$nearestStationEtaMinutes"
            nextStopMinutes != null -> "$nextStopMinutes"
            status == VehicleStatus.IDLE -> "待发"
            else -> "—"
        }
    /** 大数字下面的单位：没有分钟数时留空，避免渲染成"0 分钟" */
    val etaUnitText: String
        get() = if (nearestStationEtaMinutes != null || nextStopMinutes != null) "分钟" else ""
    /** 卡片小字（对齐小程序 index.js:468-471）：到站、发车、已到终点是三件不同的事，各说各的 */
    val etaCaptionText: String
        get() = when {
            nearestStationEtaMinutes != null && nearestStationName.isNotEmpty() ->
                "约 $nearestStationEtaMinutes 分钟到「$nearestStationName」"
            nextStopMinutes != null -> "约 $nextStopMinutes 分钟到下一站"
            waitDepartureMinutes != null -> "$waitDepartureMinutes 分钟后发车"
            status == VehicleStatus.ARRIVED -> "已到终点站"
            status == VehicleStatus.OFF_DUTY -> "车辆停用"
            status == VehicleStatus.NO_LOCATION -> "暂无定位，无法预估"
            running -> remainingRouteMinutesText ?: "到站时间未给"
            else -> "暂无到站预估"
        }
    /** 站点行文案（对齐小程序 index.js:438-446）：后端按线路站点序算好的"你最方便的站"优先 */
    val stationText: String
        get() = when {
            nearestStationName.isNotEmpty() && nearestStationEtaMinutes != null && stopsToNearestStation != null ->
                "离你最近的「$nearestStationName」还有 $stopsToNearestStation 站"
            nearestStationName.isNotEmpty() -> "将经过离你最近的「$nearestStationName」"
            running && nextStation.isNotEmpty() -> "下一站：$nextStation"
            currentStation.isNotEmpty() -> "${if (running) "当前停靠" else "待发车"}：$currentStation"
            endStation.isNotEmpty() -> "已到终点站：$endStation"
            else -> "位置待更新"
        }
    /** 距下一站文案：null 说"未给"，0.0 是后端真给了 0，两者不许混 */
    val nextDistanceShortText: String?
        get() = distanceToNextStationKm?.let { String.format(java.util.Locale.ROOT, "%.1f km", it) }
    val nextDistanceText: String
        get() = nextDistanceShortText?.let { "距下一站 $it" } ?: "距下一站暂未知"
    /**
     * 山路线进度：只认后端 `progress`（AppBusRespVO:55-56）。
     * 旧实现用 `1 - eta/40` 反推，等于自己编一条 0-100 —— ETA 没给时会画成"已经到站"。
     */
    val roadProgress: Float? get() = progress?.let { it.coerceIn(0, 100) / 100f }
    /**
     * 到站分钟是按什么算的：`AppBusServiceImpl:722/:730` 发 `SCHEDULE`（班次计划时长估算），
     * `:760` 只发 `RealRoadProviders` 白名单里的真实路网标签。VO 的 @Schema（`AppBusNearbyRespVO:128-129`）
     * 说"AMAP / EUCLIDEAN"是过时的：实现里已不再把估算打成 EUCLIDEAN（`:758-759`）。
     */
    val providerText: String
        get() = when (routeProvider.uppercase(java.util.Locale.ROOT)) {
            "" -> ""
            "SCHEDULE" -> "按班次计划时长估算"
            "AMAP" -> "高德真实路网"
            "OSM_GRAPHHOPPER", "OSM_ROAD_GRAPH", "VALHALLA", "CACHE_REAL_ROAD" -> "真实路网估算"
            "MIXED" -> "真实路网（分段拼接）"
            else -> "来源未识别（$routeProvider）"
        }

    companion object {
        /**
         * 状态码表：只认后端两个端点真实会给的值（见 [VehicleStatus] 的 KDoc）。
         * 数字串来自 `/bus/lines` 的 Integer status，字符串来自 `/bus/nearby`。
         */
        private fun statusOf(raw: String): VehicleStatus = when (raw.uppercase(java.util.Locale.ROOT)) {
            "RUNNING" -> VehicleStatus.RUNNING
            "IDLE" -> VehicleStatus.IDLE
            "ARRIVED" -> VehicleStatus.ARRIVED
            "NO_LOCATION" -> VehicleStatus.NO_LOCATION
            "1" -> VehicleStatus.RUNNING
            "0" -> VehicleStatus.IDLE
            "2" -> VehicleStatus.OFF_DUTY
            else -> VehicleStatus.UNKNOWN
        }

        fun parse(o: JSONObject): BusVehicle {
            val simLat = o.multiNumOrNull("simLatitude", "simLat")
            val simLng = o.multiNumOrNull("simLongitude", "simLng")
            val lat = o.multiNumOrNull("latitude", "lat")
            val lng = o.multiNumOrNull("longitude", "lng")
            return BusVehicle(
                // 真实键是 busId（AppBusNearbyRespVO:81）；旧候选名里没有它，车牌也读不到 plateNo，
                // 结果对接真后端时每辆车的 id 和车牌都静默变空
                id = o.multiStr("busId", "vehicleId", "id", "vehicleNo"),
                plate = o.multiStr("plateNo", "plate", "vehiclePlate", "plateNumber"),
                geo = if (lat != null && lng != null) Geo(lat, lng) else null,
                speedKmh = o.multiNumOrNull("speedKmh", "speed")?.toInt(),
                dataSource = o.multiStr("dataSource", "source"),
                nextStation = o.multiStr("nextStation", "targetStation", "stationName"),
                etaMinutes = o.multiNumOrNull("etaMinutes")?.toInt(),
                lineName = o.multiStr("routeName", "lineName", "name"),
                shiftCode = o.multiStr("shiftCode", "busCode"),
                routeId = o.multiStr("routeId"),
                startStation = o.multiStr("startStation", "fromStation", "frontName"),
                endStation = o.multiStr("endStation", "toStation", "terminalName"),
                status = statusOf(o.multiStr("status", "runStatus")),
                currentStation = o.multiStr("currentStation", "currentStationName"),
                // `waitMinutes` 不是后端的键（AppBusRespVO:47 / AppBusNearbyRespVO:126 都只叫
                // waitDepartureMinutes）—— 它是小程序 pages/bus/index.js:449 自己起的局部变量名，
                // 拿它当候选等于把"前端变量名"当成"接口契约"。删。
                waitDepartureMinutes = o.multiNumOrNull("waitDepartureMinutes")?.toInt(),
                distanceToNextStationKm = o.multiNumOrNull("distanceToNextStationKm", "nextDistanceKm"),
                // 真实键只有 etaToNextStationMinutes 一个名字（AppBusRespVO.java:49-50）；nearby 没这个键，
                // 这里不许给它加 etaMinutes 之类的候选 —— 那两个口径不同，共用候选名就等于把剩余全程当成到下一站
                etaToNextStationMinutes = o.multiNumOrNull("etaToNextStationMinutes"),
                routeProvider = o.multiStr("routeProvider"),
                lastLocationTime = o.timeOf("lastLocationTime", "reportTime", "gpsTime"),
                locationSource = LocationSource.of(o.multiStr("locationSource")),
                nearestStationName = o.multiStr("nearestStationName", "nearestStopName"),
                nearestStationEtaMinutes = o.multiNumOrNull("nearestStationEtaMinutes")?.toInt(),
                stopsToNearestStation = o.multiNumOrNull("stopsToNearestStation")?.toInt(),
                progress = o.multiNumOrNull("progress")?.toInt(),
                distanceKm = o.multiNumOrNull("distanceKm"),
                simRunning = o.multiBool("simRunning", "simulated"),
                simGeo = if (simLat != null && simLng != null) Geo(simLat, simLng) else null,
            )
        }
    }
}

/**
 * 车辆列表排序：只按后端给的"距用户直线距离"升序，**没给距离的排最后**（未知不等于最近）。
 * 与后端 `AppBusServiceImpl:436-437` 的 `nullsLast` 同口径；Kotlin 的 sortedWith 稳定，
 * 全为 null 时（`/bus/lines` 不下发 distanceKm）就原样保留后端顺序，不再自己按 ETA 重排。
 */
fun List<BusVehicle>.byUserDistance(): List<BusVehicle> =
    sortedWith(Comparator { a, b ->
        val x = a.distanceKm
        val y = b.distanceKm
        when {
            x == null && y == null -> 0
            x == null -> 1
            y == null -> -1
            else -> x.compareTo(y)
        }
    })

data class BusLine(
    val id: String,
    val name: String,
    val from: String,
    val to: String,
    val stations: List<Station>,
    val vehicles: List<BusVehicle>,
    val dataSource: String,
    /** 旧字段：后端契约里没有 `distance|meters` 这两个键，只保留解析以兼容旧报文；界面一律改读 [distanceKm]（审计 §72）。
     *  F-3 ② 改可空：旧报文带键就照旧给值，后端没给就是 null，不再塌成 0.0 当"里程 0"。 */
    val distanceMeters: Double? = null,
    /**
     * 线路里程(km)：真实键 `distanceKm`（`AppBusLineRespVO.java:27-28` Double），
     * `AppBusServiceImpl:179` 原样透传 `route.getDistanceKm()`（上游 `MonitoringServiceImpl:126` 可为 null）
     * ⇒ **没给就是 null**，不许塌成 0 再显示"全程 0.0 公里"。
     */
    val distanceKm: Double? = null,
) {
    companion object {
        fun parse(o: JSONObject) = BusLine(
            id = o.multiStr("routeId", "lineId", "id"),
            name = o.multiStr("routeName", "lineName", "name"),
            from = o.multiStr("startStation", "fromStation", "frontName"),
            to = o.multiStr("endStation", "toStation", "terminalName"),
            stations = o.multiArr("stations", "stops", "points").map { Station.parse(it) },
            vehicles = o.multiArr("vehicles", "buses").map { BusVehicle.parse(it) },
            dataSource = o.multiStr("dataSource", "transitProvider"),
            // 旧米制报文只可能带 `distance`（DistanceCaliberContractTest 钉住"保留但不许当公里读"）；
            // `meters` 后端/小程序/DemoData 三面 0 生产者，是客户端自己起的第三个名字 ⇒ 撤。
            distanceMeters = o.multiNumOrNull("distance"),
            // 真实键只有 distanceKm 一个名字，且单位是公里：这里不许加 "distance"/"meters" 候选，
            // 那会把"米"当"公里"读（旧 distanceMeters 读法就是因为它才恒为 0）
            distanceKm = o.multiNumOrNull("distanceKm"),
        )
    }
}

/** 附近公交结果；运营时段相关字段供页面区分「非运营时间 / 无车 / 线路为空」三种空态 */
data class NearbyResult(
    val stations: List<Station>,
    val lines: List<BusLine>,
    val vehicles: List<BusVehicle>,
    val realTransit: Boolean,
    /**
     * 是否在运营时段。F-2 §四：旧读法是 `o.optBoolean("inService", true)` —— **缺键当"是"**，
     * 于是演示面（DemoData 根本不写这个键）和任何漏字的报文都会被首页/线路页说成"正在运营"。
     * 三方实测：后端 `AppBusNearbyRespVO.java:65 Boolean inService`（`AppBusServiceImpl:481/:532` 赋值）、
     * 小程序按**三态**读（`pages/bus/index.js:240` `data.inService != null ? data.inService : null`，
     * :288/:291 分别判 `=== false` / `=== true`；`pages/index/index.js:539/:554` 同口径），
     * ⇒ 缺失必须是 UNKNOWN(null)，只有真给 false 才允许进"非运营"那一支。
     */
    val inService: Boolean? = null,
    val serviceWindowText: String = "",
    val nextDepartureTime: String = "",
    val lineCount: Int = 0,
    val locationLevel: String = "",
    val runningEmptyText: String = "",
    /** 现实公交层供应方（实测返回 AMAP，即后端已合并高德 POI，客户端无需再补一层） */
    val transitProvider: String = "",
    val realStationCount: Int = 0,
    val projectStationCount: Int = 0,
    /**
     * 后端是否认这次上报的坐标。同 §四 的另一处：旧读法 `o.optBoolean("located", true)` 把缺键说成"定位成功"。
     * 生产者 `AppBusNearbyRespVO.java:45 Boolean located`（`AppBusServiceImpl:256` 按经纬度合法性赋值），
     * 小程序读 `data.located`；缺失只能 UNKNOWN。当前没有 UI 消费它，但**默认值本身就是谎言**，一样改可空。
     */
    val located: Boolean? = null,
) {
    /** 线路 chip 用的附近线路（最多 6 条，与小程序一致） */
    val nearLines: List<BusLine> get() = lines.take(6)

    /**
     * "明确不在运营时段"——只有后端真给 false 才成立（F-2 §四）。
     * 页面拿它决定 off 空态，缺键（null）时落到 empty/data 那一支，与小程序 `inService === false ? 'off' : 'empty'`
     * （pages/index/index.js:554）逐字对齐。写在模型上是为了让 JVM 契约测试能断言三态。
     */
    val isOffService: Boolean get() = inService == false

    /**
     * 「现在不在运营时间」这句话后端给没给凭证（设备实拍 s_home2 暴露的第二半）。
     *
     * 实测同一端点、两种情形都回 `inService=false`：
     * - 服务区内、收车后：`serviceWindowText="06:30–23:52"`、`nextDepartureTime="06:30"`、`lineCount=189`；
     * - 服务区外（模拟器停在 37.42,-122.08）：`serviceWindowText`/`nextDepartureTime` 都是 null、`lineCount=0`。
     * 后者被念成"当前不在运营时间"，是把"这里查不到线路"洗成了一个关于时钟的结论，
     * 而且那一屏的副标题正因为三个字段全空而整条消失——用户只看到一句没有依据的话。
     * 所以 off 那一支必须有服务时段才成立；拿不到就走 empty（文案「附近暂无公交线路」）。
     */
    val isOffServiceAttested: Boolean get() = isOffService && serviceWindowText.isNotEmpty()

    /** 定位精度是否已足够（PRECISE 才适合给"最近 N 米"这类强结论） */
    val preciseLocation: Boolean get() = locationLevel.equals("PRECISE", true)

    /** 附近站点里的现实公交站（高德 POI），用于"现实公交 N 个"标签 */
    val realStations: List<Station> get() = stations.filter { it.isRealTransit }
}

/** 线路供应方：现实公交（高德 POI）与自建线路在列表里要分标签，与小程序一致 */
data class TransitCandidate(
    val name: String,
    val provider: String,
    val stations: List<Station>,
) {
    val isReal: Boolean get() = provider.equals("REAL", true) || provider.contains("AMAP", true)
}

// ---------- 寄货 / 包裹 ----------
data class SendOrder(
    val id: Long,
    val orderNo: String,
    val goodsName: String,
    val weightKg: Double,
    val itemCount: Int,
    val volumeM3: Double,
    val fresh: Boolean,
    val cargoCategory: String,
    val status: Int,
    val pickupCode: String,
    val senderName: String,
    val senderMobile: String,
    val senderAddress: String,
    val receiverName: String,
    val receiverMobile: String,
    val receiverAddress: String,
    val fee: Double,
    val vehiclePlate: String,
    val shiftCode: String,
    val targetStation: String,
    /**
     * 班次口径的到站剩余分钟。真实来源只有 `AppSendOrderRespVO.etaMinutes`（`java:96`），
     * 由 `AppSendController:246-250` 在 `estimatedArrivalTime` **晚于当前时间**时才写入，否则是显式 null
     * ⇒ 缺键/null 一律 null，不许塌成 0（0 会被读成"还有不到一分钟"）。
     * ⚠ 本字段**不认** `carrierEtaMinutes`：那是承运车辆实时位置按 Haversine ÷ 25km/h 估的另一件事
     * （`GeoDistanceUtil:81-95`），混进同一个字段等于把直线估算包装成班次 ETA。
     */
    val etaMinutes: Int?,
    val dataSource: String,
    val createTime: String,
    val remark: String,
    // ---- 小程序 parcel.wxml 卡片/到车提醒/待客户操作所需字段 ----
    /** 后端状态文案优先 */
    val statusName: String = "",
    /** 承运审核：1通过 2需操作 3待人工 4不通过（send/create 回执，列表也可能带） */
    val reviewStatus: Int = 0,
    /** 审核原因码，逗号分隔（如 "OVER_WEIGHT,DANGEROUS_GOODS"） */
    val reviewReasonCodes: String = "",
    /** 车快到了（后端计算，前端不自己判阈值） */
    val carrierApproaching: Boolean = false,
    val carrierArrived: Boolean = false,
    val carrierArrivedStation: String = "",
    val carrierLoaded: Boolean = false,
    val carrierDelivered: Boolean = false,
    /**
     * 承运车辆距目标站点的估算分钟：后端 `carrierEtaMinutes`（`AppSendOrderRespVO:106`）= Haversine 直线公里
     * ÷ `DEFAULT_AVG_SPEED_KMH` 25km/h（`GeoDistanceUtil:21/:81-95`），**不是道路里程 ETA**，展示必须带口径标注；
     * 车辆无位置、真实上报过期或订单不在途时后端整块不填（`AppSendController:443-457`）⇒ null。
     */
    val carrierEtaMinutes: Int? = null,
    val carrierDistanceKm: Double = 0.0,
    val carrierLocationSource: String = "",
    val carrierLongitude: Double = 0.0,
    val carrierLatitude: Double = 0.0,
    /** 待客户操作：就近交接站点（替代交接方案） */
    val servicePointStationName: String = "",
    val servicePointDistanceKm: Double = 0.0,
    val servicePointLatitude: Double = 0.0,
    val servicePointLongitude: Double = 0.0,
    val estimatedArrivalTime: String = "",
    val driverName: String = "",
    val driverMobile: String = "",
    val orderType: Int = 0,
    val bizType: String = "",
) {
    val statusLabel: String get() = statusName.ifEmpty { freightStatusLabel(status) }
    /** 语义色跟着 [FreightOrderStatus] 的码走（§18：旧页面按 status 区间上色，把 4 已完成读成主色） */
    val statusTone: BusTone get() = freightTone(status)
    /** 终态（4 已完成 / 5 已取消）：不该再出现会改状态的动作入口 */
    val isFinalStatus: Boolean get() = FreightOrderStatus.of(status)?.isFinal == true
    /** 货已在路上（3/9/10/11/12）：只有这些码才允许展示实时轨迹与承运信息 */
    val onTheWay: Boolean get() = FreightOrderStatus.of(status)?.isOnTheWay == true
    /** 车快到了：后端旗标优先；自己判阈值时也必须先确认分钟数真的下发了（null ≠ 0 分钟） */
    val isArriving: Boolean get() = carrierApproaching || (carrierEtaMinutes != null && carrierEtaMinutes in 1..10)
    /** 待客户操作态：需要引导用户送到就近站点 */
    val needsCustomerAction: Boolean get() = reviewStatus == ReviewStatus.CONDITIONAL.code && servicePointStationName.isNotEmpty()
    companion object {
        fun parse(o: JSONObject) = SendOrder(
            id = o.multiNum("id", "orderId").toLong(),
            orderNo = o.multiStr("orderNo", "bizOrderNo", "no"),
            goodsName = o.multiStr("goodsName", "cargoName", "goods"),
            weightKg = o.multiNum("weightKg", "weight", "goodsWeight"),
            itemCount = o.multiNum("itemCount", "pieces").toInt().coerceAtLeast(1),
            volumeM3 = o.multiNum("volumeM3", "volume"),
            fresh = o.multiBool("freshFlag", "fresh"),
            cargoCategory = o.multiStr("cargoCategory", "category"),
            status = o.multiNum("status", "orderStatus").toInt(),
            pickupCode = o.multiStr("pickupCode", "code", "takeCode"),
            senderName = o.multiStr("senderName"),
            senderMobile = o.multiStr("senderMobile", "senderPhone"),
            senderAddress = o.multiStr("senderAddress"),
            receiverName = o.multiStr("receiverName"),
            receiverMobile = o.multiStr("receiverMobile", "receiverPhone"),
            receiverAddress = o.multiStr("receiverAddress"),
            fee = o.multiNum("fee", "totalPrice", "amount", "totalAmount"),
            vehiclePlate = o.multiStr("vehiclePlate", "plate"),
            shiftCode = o.multiStr("shiftCode"),
            targetStation = o.multiStr("targetStation", "deliveryStation"),
            etaMinutes = o.multiNumOrNull("etaMinutes")?.toInt(),
            dataSource = o.multiStr("dataSource", "positionSource", "carrierLocationSource"),
            createTime = o.timeOf(),
            remark = o.multiStr("remark", "goodsNote"),
            statusName = o.multiStr("statusName"),
            reviewStatus = o.multiNum("reviewStatus").toInt(),
            reviewReasonCodes = o.multiStr("reviewReasonCodes", "reviewReason"),
            carrierApproaching = o.multiBool("carrierApproaching"),
            carrierArrived = o.multiBool("carrierArrived"),
            carrierArrivedStation = o.multiStr("carrierArrivedStation"),
            carrierLoaded = o.multiBool("carrierLoaded"),
            carrierDelivered = o.multiBool("carrierDelivered"),
            carrierEtaMinutes = o.multiNumOrNull("carrierEtaMinutes")?.toInt(),
            carrierDistanceKm = o.multiNum("carrierDistanceKm"),
            carrierLocationSource = o.multiStr("carrierLocationSource"),
            carrierLongitude = o.multiNum("carrierLongitude"),
            carrierLatitude = o.multiNum("carrierLatitude"),
            servicePointStationName = o.multiStr("servicePointStationName"),
            servicePointDistanceKm = o.multiNum("servicePointDistanceKm"),
            servicePointLatitude = o.multiNum("servicePointLatitude"),
            servicePointLongitude = o.multiNum("servicePointLongitude"),
            estimatedArrivalTime = o.timeOf("estimatedArrivalTime", "planArrivalTime"),
            driverName = o.multiStr("driverName"),
            driverMobile = o.multiStr("driverMobile", "driverPhone"),
            orderType = o.multiNum("orderType").toInt(),
            bizType = o.multiStr("bizType"),
        )
    }
}

data class TraceNode(val label: String, val time: String, val hint: String, val state: StopState)

data class TransportLeg(
    val legId: String,
    val seq: Int,
    val fromStation: String,
    val toStation: String,
    val plate: String,
    val driverName: String,
    val status: Int,
    /** 后端下发的段状态名原值（`statusLabel/statusName/stateLabel`），可能为空 */
    val statusName: String,
    /**
     * 段到站分钟。**实测后端两个段契约都没有这个键**：`AppSendLegRespVO`（用户侧 `/send/legs`）与
     * `AppDriverLegRespVO`（司机侧 `/driver/legs`）给的是绝对时刻与**计划**耗时（`durationMinutes`），
     * 没有"剩余分钟"字段 ⇒ 真接口下恒为 null。旧写法 `multiNum("etaMinutes","eta").toInt()` 把它塌成 0，
     * 页面 `> 0` 静默隐藏，用户看不出是"没下发"还是"没到时间"（审计 §71）。缺键/null 一律 null。
     */
    val etaMinutes: Int?,
    val action: String,
    val transferStation: String,
    /** 本段是否需要换乘交接（后端 `AppDriverLegRespVO.handoverRequired`，非最终段为 true）。
     *  它决定"到达终点"之后的出口是交接还是派送，缺这个字段就只能靠猜，所以必须读后端。 */
    val handoverRequired: Boolean = false,
    val handoverStatusName: String = "",
    /** 换乘交接单编号：`legAction("handover-*")` 由后端按 legId 反查，本机只用它判断有没有交接单 */
    val handoverId: Long = 0L,
    val orderNo: String = "",
    /**
     * 本段里程(km)：`AppDriverLegRespVO.java:58-59` 是 **BigDecimal、可空**（未测算就是没给），
     * 塌成 0.0 会被读成"这段 0 公里"。⑫ 把它列为打印残留，本轮收口（审计 §72 F4）。
     */
    val distanceKm: Double? = null,
    /** 本段计划耗时(分钟)：`AppDriverLegRespVO.java:61-62` Integer 可空，语义是**计划**时长不是剩余 */
    val durationMinutes: Int? = null,
    /** ESTIMATED 表示里程/时长是估算值，页面需提示 */
    val navigationSource: String = "",
    val fromGeo: Geo? = null,
    val toGeo: Geo? = null,
    /** 该段真实道路轨迹（高德路网） */
    val navigationPolyline: List<Geo> = emptyList(),
) {
    /** 段已完成：后端 `TransportLegStatusEnum.COMPLETED` 只有一个码（11），不是"数值到某档就算完成" */
    val done: Boolean get() = LegStatus.of(status)?.isDone == true

    /** 段进行中：7 运输中 / 8 已到达终点 / 9 交接中 / 10 派送中（4/5/6 是"车还在去起点路上"，不算） */
    val active: Boolean get() = LegStatus.of(status)?.isActive == true

    /** 后端给了名字就用，没给才按枚举兜底；未知码 = [STATUS_UNAVAILABLE_LABEL]，不猜"待承运" */
    val statusLabel: String get() = statusName.ifEmpty { LegStatus.labelOf(status) }

    /** 配色跟着码走：未知码 NEUTRAL */
    val statusTone: BusTone get() = LegStatus.toneOf(status)
    val estimated: Boolean get() = navigationSource.equals("ESTIMATED", true)

    /**
     * 本段"里程 · 计划耗时"。两个键在后端都可空（`distanceKm` BigDecimal / `durationMinutes` Integer，
     * 未测算就是不赋值），所以**缺哪个就不提哪个**；两个都缺返回 null、整行不显示 ——
     * 与小程序 handover.wxml:26 的 `wx:if="{{currentLeg.distanceKm}}"` 同口径，不写"0 km"（审计 §72 F4）。
     * [durationMinutes] 语义是**计划**耗时不是剩余，措辞跟着走；ESTIMATED 时按后端 admin 拓扑的写法补"（估算）"。
     */
    val legPlanText: String?
        get() {
            val km = distanceKm?.let { String.format(java.util.Locale.CHINA, "%.1f km", it) }
            val min = durationMinutes?.let { "计划 $it 分钟" }
            val parts = listOfNotNull(km, min)
            if (parts.isEmpty()) return null
            return "本段 " + parts.joinToString(" · ") + if (estimated) "（估算）" else ""
        }
    companion object {
        fun parse(o: JSONObject): TransportLeg {
            val road = o.multiArr("navigationPolyline", "polyline", "track").map {
                Geo(it.multiNum("latitude", "lat"), it.multiNum("longitude", "lng"))
            }.filter { it.lat != 0.0 && it.lng != 0.0 }
            // 后端 `AppSendLegRespVO:23-33` 给的就是这四个键（站点档案无坐标时为 null），
            // 小程序 parcel.js:378 读的也是这四个；旧的 start*/end* 别名三面 0 生产者，已撤读（G-1）。
            val fromLat = o.multiNumOrNull("fromLatitude")
            val fromLng = o.multiNumOrNull("fromLongitude")
            val toLat = o.multiNumOrNull("toLatitude")
            val toLng = o.multiNumOrNull("toLongitude")
            return TransportLeg(
                legId = o.multiStr("legId", "id"),
                // 后端 `AppDriverLegRespVO` 给的是 legSequence / fromStationName / plateNo，
                // 旧解析只认 seq / fromStation / vehiclePlate（§36：真机联调下站点名、车牌、序号全空）
                seq = o.multiNum("seq", "legSeq", "legSequence", "sort").toInt(),
                fromStation = o.multiStr("fromStation", "fromStationName", "startStation", "pickupStation"),
                toStation = o.multiStr("toStation", "toStationName", "endStation", "deliveryStation"),
                plate = o.multiStr("plateNo", "vehiclePlate", "plate"),
                driverName = o.multiStr("driverName"),
                status = if (o.has("status")) o.multiNum("status").toInt() else -1,
                statusName = o.multiStr("statusLabel", "statusName", "stateLabel"),
                etaMinutes = o.multiNumOrNull("etaMinutes")?.toInt(),
                action = o.multiStr("action", "nextAction"),
                transferStation = o.multiStr("transferStation", "handoverStation"),
                handoverRequired = o.multiBool("handoverRequired"),
                handoverStatusName = o.multiStr("handoverStatusName"),
                handoverId = o.multiNum("handoverId").toLong(),
                orderNo = o.multiStr("orderNo", "bizOrderNo"),
                distanceKm = o.multiNumOrNull("distanceKm"),
                durationMinutes = o.multiNumOrNull("durationMinutes")?.toInt(),
                navigationSource = o.multiStr("navigationSource", "source"),
                fromGeo = if (fromLat != null && fromLng != null) Geo(fromLat, fromLng) else null,
                toGeo = if (toLat != null && toLng != null) Geo(toLat, toLng) else null,
                navigationPolyline = road,
            )
        }
    }
}

/** 换乘交接点（多段联运：上一段与下一段在换乘站交接） */
data class LegHandover(
    val station: String,
    val fromPlate: String,
    val toPlate: String,
    val fromDriver: String,
    val toDriver: String,
    val time: String,
    val itemCount: Int,
) {
    companion object {
        fun parse(o: JSONObject) = LegHandover(
            station = o.multiStr("stationName", "transferStation", "handoverStation"),
            fromPlate = o.multiStr("fromPlate", "vehiclePlate"),
            toPlate = o.multiStr("toPlate", "nextPlate"),
            fromDriver = o.multiStr("fromDriverName", "fromDriver"),
            toDriver = o.multiStr("toDriverName", "toDriver"),
            time = o.timeOf("handoverTime", "confirmTime", "createTime"),
            itemCount = o.multiNum("itemCount", "count").toInt(),
        )
    }
}

/**
 * 候选运输方案解释（后端算出的多段方案，含推荐标记与理由）。
 *
 * `recommended`/`legs` 的来源是 `Candidate.chosen:69`（Boolean 包装类）与 `legCount:62`（Integer），
 * 两者都可以缺席 ⇒ 可空，缺键不许变成"不推荐 / 0 段方案"这种看起来像结论的默认值。
 */
data class TransitPlan(
    val name: String,
    val recommended: Boolean? = null,
    val legs: Int? = null,
    val minutes: Int,
    val reason: String,
) {
    companion object {
        fun parse(o: JSONObject) = TransitPlan(
            // 候选方案的契约就是 OrderTopologyRespVO.Candidate 那 9 个键（mode:60 / modeName:61 / legCount:62 /
            // transferCount:63 / distanceKm:64 / durationMinutes:65 / score:66 / reason:67 / chosen:69）。
            // `name`/`planName`/`title`、`recommended`/`best`/`selected`、`legs`/`segments` 三面 0 生产者，
            // 改前只有 DemoData 在发 ⇒ 第四个真相源；名称改认真键 modeName，推荐标记改认真键 chosen。
            name = o.multiStr("modeName"),
            recommended = o.multiBoolOrNull("chosen"),
            legs = o.multiNumOrNull("legCount")?.toInt(),
            // 时长只认 durationMinutes:65（F-2 ⑤ 已收口：totalMinutes/minutes 都无生产者，
            // 原先只有 DemoData 在发 totalMinutes，跟着一起改成真实键名，不改就会在演示包里读成 0 分钟）。
            // ⚠ 别拿信封上那个 `totalDurationMinutes`:44 来补这里：它是整单的总时长（另一个对象、另一个口径）。
            minutes = o.multiNum("durationMinutes").toInt(),
            reason = o.multiStr("reason"),
        )
    }
}

data class ParcelTrace(
    val orderNo: String,
    val status: Int,
    val goodsName: String,
    val nodes: List<TraceNode>,
    val legs: List<TransportLeg>,
    val vehiclePlate: String,
    val driverName: String,
    val driverMobile: String,
    val targetStation: String,
    /** `/send/track` 就是 `AppSendOrderRespVO`，所以这里与 [SendOrder.etaMinutes] 同一口径：只在后端下发分钟数时非 null */
    val etaMinutes: Int?,
    val dataSource: String,
    val vehicleGeo: Geo?,
    /**
     * 车辆位置的新鲜度定性，与 [BusVehicle.locationSource] 同一枚举、同一解析点。
     * 生产者：`/send/track` 的 `carrierLocationSource`（`AppSendOrderRespVO:108`，由 `AppSendController:474` 写
     * REAL_FRESH / REAL_STALE）；`/product-order/trace` 根本没有这个键（`AppProductOrderTraceRespVO` 只有
     * `currentLongitude:68` / `currentLatitude:70` / `lastReportTime:72`），所以那条链路解析出来是 UNKNOWN，
     * 页面只能显示"新鲜度未给"，不许替后端猜成"实时"。
     */
    val locationSource: LocationSource = LocationSource.UNKNOWN,
    val polyline: List<Geo>,
    val stations: List<Station>,
    // ---- 小程序 trace.wxml 承运信息卡 / 凭证 / 边界态所需字段 ----
    val productName: String = "",
    val statusName: String = "",
    val startStation: String = "",
    val endStation: String = "",
    val routeName: String = "",
    val shiftCode: String = "",
    val receiverName: String = "",
    val receiverMobile: String = "",
    val receiverAddress: String = "",
    val lastReportTime: String = "",
    /**
     * 承运司机当前作业状态名（待执行/行驶中/已到站/揽收中/派送中/已完成）。
     * 生产者：`/send/track` → `fillEta:252` → `fillCarrierArrival:395` 的 `TaskItemStatusEnum.nameOf(派单经停明细状态)`。
     * 未派单（`fillEta:215-217` 明细为空直接 return）或访问者不是下单人/收件人（`track:151-153` 走 toProgressVO）时
     * 后端整块不下发，这里就得是空串、由页面念"当前任务未给"——不许拿订单 statusName 顶一个"派送中"给用户。
     */
    val carrierTaskStatus: String = "",
    /**
     * 作业状态的更新时间。后端自己的口径是"经停明细 update_time，近似现场时间"（`AppSendOrderRespVO:119-120`），
     * **不是**位置上报时间，所以文案里不许和 [lastReportTime] 混成"最后更新时间"一格。
     */
    val carrierTaskTime: String = "",
    val loadTime: String = "",
    val deliverTime: String = "",
    val driverArrived: Boolean = false,
    val loadPhotoUrl: String = "",
    val deliverPhotoUrl: String = "",
    /** 途经站点（含序号与 +N 分钟） */
    val points: List<Station> = emptyList(),
    val handovers: List<LegHandover> = emptyList(),
    val plans: List<TransitPlan> = emptyList(),
    /**
     * 这份轨迹来自哪个状态空间。`ParcelTrace` 同时承载 `/send/track`（寄货，`TransportOrderStatusEnum` 0..13）
     * 和 `/product-order/trace`（商城，`ProductOrderStatusEnum` 0..3）——**同一个码在两边意思相反**：
     * 寄货 3 = 已发车、5 = 已取消；商城 3 = 已取消。所以只能由取数处声明来源，不能让页面猜。
     * 见 `docs/STATUS_MAPPING.md` 的"两套码"一节。
     */
    val fromProductTrace: Boolean = false,
) {
    /** 优先用后端下发的 `statusName`，本地表只兜底（§18 方案 3）；兜底表按 [fromProductTrace] 选域 */
    val statusLabel: String
        get() = statusName.ifEmpty {
            if (fromProductTrace) productStatusLabel(status) else freightStatusLabel(status)
        }

    /** 边界态：已取消（无轨迹可展示）。按状态空间取码，不看数字 3。 */
    val cancelled: Boolean
        get() = if (fromProductTrace) {
            ProductOrderStatus.of(status) == ProductOrderStatus.CANCELLED
        } else {
            FreightOrderStatus.of(status) == FreightOrderStatus.CANCELLED
        }

    /** 配色与文案同一张表（§18）：未知码 NEUTRAL，不靠猜给"完成/危险"这类强语义色。 */
    val tone: BusTone
        get() = if (fromProductTrace) ProductOrderStatus.toneOf(status) else freightTone(status)

    /** 未发车：无任何线路与轨迹数据 */
    val notShipped: Boolean get() = vehicleGeo == null && polyline.isEmpty() && points.isEmpty()
}

// ---------- 消息通知 ----------

/** 消息语义级别：决定列表左侧竖条颜色（与小程序 notification.wxss 同源） */
enum class MsgLevel { SUCCESS, WARNING, EXCEPTION, ACTION_REQUIRED, INFO, UNKNOWN }

data class NotifyMsg(
    val id: Long,
    val title: String,
    val content: String,
    val type: String,
    val read: Boolean,
    val time: String,
    val orderId: Long,
    val orderNo: String,
    /** 事件枚举，决定列表图标（小程序 EVENT_ICONS 21 种） */
    val eventType: String = "",
    val eventTypeName: String = "",
    val level: MsgLevel = MsgLevel.UNKNOWN,
    /** 需要用户处理（红色「需要处理」胶囊） */
    val actionRequired: Boolean = false,
) {
    val typeLabel: String get() = eventTypeName.ifEmpty { type }
    companion object {
        private fun levelOf(raw: String) = when (raw.uppercase()) {
            "SUCCESS" -> MsgLevel.SUCCESS
            "WARNING" -> MsgLevel.WARNING
            "EXCEPTION", "ERROR" -> MsgLevel.EXCEPTION
            "ACTION_REQUIRED", "ACTION" -> MsgLevel.ACTION_REQUIRED
            "INFO" -> MsgLevel.INFO
            else -> MsgLevel.UNKNOWN
        }

        fun parse(o: JSONObject): NotifyMsg {
            val evt = o.multiStr("eventType", "event", "type", "bizType", "templateType")
            return NotifyMsg(
                id = o.multiNum("id", "messageId").toLong(),
                title = o.multiStr("title", "msgTitle", "templateTitle"),
                content = o.multiStr("content", "msgContent", "params", "text"),
                type = evt,
                read = o.multiBool("readStatus", "isRead", "read") || o.multiNum("readStatus") == 1.0,
                time = o.timeOf("createTime", "sendTime", "time"),
                orderId = o.multiNum("orderId", "bizId").toLong(),
                orderNo = o.multiStr("orderNo", "bizOrderNo", "no"),
                eventType = evt,
                eventTypeName = o.multiStr("eventTypeName", "typeName"),
                level = levelOf(o.multiStr("level", "msgLevel")),
                actionRequired = o.multiBool("actionRequired", "needAction"),
            )
        }
    }
}

// ---------- 司机端 ----------
data class DriverProfile(
    val driverId: Long,
    val name: String,
    val mobile: String,
    val plate: String,
    val lineName: String,
    val company: String,
    val shiftCode: String,
    /** 额定载重(kg)：真实键 `cargoCapacityKg`（`AppDriverProfileRespVO.java:23`），旧读法的 `capacityKg|capacity` 三方都无生产者 ⇒ 曾恒 0 */
    val capacityKg: Double? = null,
    /** 已装(kg)：后端 profile 里没有这个口径（件数口径在班次的 loadedCount），三方无生产者 ⇒ 恒 null */
    val usedKg: Double? = null,
    /** 认证状态：后端 profile 没有任何审核/认证键（VO 共 7 键），三方无生产者 ⇒ 恒 null，界面不许说"未认证" */
    val verified: Boolean? = null,
    /** 行李舱件数上限（真实键 cargoCapacity，`AppDriverProfileRespVO.java:21` Integer，可缺席；与 capacityKg 是两套口径） */
    val cargoCapacity: Int? = null,
) {
    /**
     * 空余仓位百分比（件数口径，与小程序 workbench 的 cargo-percent 同源：上限 − 本班 loadedCount）。
     * 上限或已装件数任一没给 ⇒ null。旧写法在这里回 0，于是"后端没说"被画成一个确定的空仓百分比。
     */
    fun freePercent(loadedCount: Int?): Double? {
        val cap = cargoCapacity ?: return null
        val loaded = loadedCount ?: return null
        if (cap <= 0) return null
        return (100.0 - loaded * 100.0 / cap).coerceIn(0.0, 100.0)
    }
    companion object {
        fun parse(o: JSONObject) = DriverProfile(
            driverId = o.multiNum("driverId", "id").toLong(),
            name = o.multiStr("name", "driverName", "realName"),
            mobile = o.multiStr("mobile", "phone"),
            // 车牌的真实键是 plateNo（AppDriverProfileRespVO.java:19，小程序同源：workbench.js:182
            // `plateNo: profile.plateNo || ''`）。改前读的是 plate/vehiclePlate/carNo 三个自造名 ⇒ 真机上车牌整列空白。
            plate = o.multiStr("plateNo"),
            lineName = o.multiStr("lineName", "routeName"),
            company = o.multiStr("company", "companyName"),
            shiftCode = o.multiStr("shiftCode"),
            capacityKg = o.multiNumOrNull("cargoCapacityKg"),
            // usedKg / verified 两处读法已撤（F-3 ②）：AppDriverProfileRespVO:11-23 只有
            // driverId/name/mobile/vehicleId/plateNo/cargoCapacity/cargoCapacityKg 七个键，
            // 已装重量与认证状态在**重量口径**上三方都无生产者（件数口径在班次的 loadedCount，另一条链路）。
            // 行李舱件数上限的真实键是 cargoCapacity（:21）；capacityCount/cargoCapacityCount 无生产者，删。
            cargoCapacity = o.multiNumOrNull("cargoCapacity")?.toInt(),
        )
    }
}

enum class ShiftState { WAITING, RUNNING, STOPPED, FINISHED }

data class ShiftTask(
    val shiftId: Long,
    val shiftCode: String,
    val lineName: String,
    val departTime: String,
    val arriveTime: String,
    val state: ShiftState,
    val statusLabel: String,
    val stations: List<Station>,
    val cargoKg: Double,
    val progress: Float,
    val currentStationId: Long,
    /** 已装车件数（行李舱运力按件数算，与 cargoCapacity 配对） */
    val loadedCount: Int = 0,
    /** 计划发车时间 / 计划时长（决定 ETA 与经停预计到达） */
    val plannedDepartureTime: String = "",
    val plannedDurationMinutes: Int = 0,
) {
    /** 当前站序号（-1 表示未定位到） */
    val currentIndex: Int get() = stations.indexOfFirst { it.id == currentStationId }
    companion object {
        /**
         * 班次状态码与小程序 routes.js:45 / workbench.js:317 同源：
         * 0 待发车 / 1 进行中 / 2 已完成
         */
        fun parse(o: JSONObject): ShiftTask {
            val raw = o.multiStr("status", "executionStatus", "state")
            val state = when (raw.uppercase()) {
                "1", "RUNNING", "在途", "进行中" -> ShiftState.RUNNING
                "2", "FINISHED", "DONE", "已完成" -> ShiftState.FINISHED
                "3", "STOPPED", "停靠" -> ShiftState.STOPPED
                else -> ShiftState.WAITING
            }
            val departRaw = o.multiStr("plannedDepartureTime", "departTime", "startTime")
            return ShiftTask(
                shiftId = o.multiNum("shiftId", "id").toLong(),
                shiftCode = o.multiStr("shiftCode", "code"),
                lineName = o.multiStr("routeName", "lineName"),
                departTime = departRaw.substringAfter(' ').take(5).ifEmpty { departRaw.take(5) },
                arriveTime = o.multiStr("arriveTime", "endTime").substringAfter(' ').take(5),
                state = state,
                statusLabel = o.multiStr("statusName", "statusLabel").ifEmpty {
                    when (state) {
                        ShiftState.RUNNING -> "进行中"
                        ShiftState.STOPPED -> "到站停靠"
                        ShiftState.FINISHED -> "已完成"
                        ShiftState.WAITING -> "待发车"
                    }
                },
                stations = o.multiArr("stations", "stops", "viaStations").map { Station.parse(it) },
                // orderCount 读法已撤：/driver/shifts → AppDriverShiftRespVO 的 12 个键里没有 orderCount/cargoCount，
                // 全 app 侧 VO 也 0 生产者 ⇒ 恒 0；本班件数的唯一真实键是 loadedCount（DriverAppServiceImpl:236，null 也显式发 0）。
                cargoKg = o.multiNum("cargoKg", "weightKg"),
                progress = o.multiNum("progress", "completionRate").toFloat(),
                currentStationId = o.multiNum("currentStationId", "stationId").toLong(),
                loadedCount = o.multiNum("loadedCount", "loaded").toInt(),
                plannedDepartureTime = departRaw,
                // 司机班次（/driver/shifts → AppDriverShiftRespVO:24）只有 plannedDurationMinutes。
                // `durationMinutes` 确实存在于后端，但那是**另一个对象**的字段：段时长
                // AppDriverLegRespVO:62 / OrderTopologyRespVO.Leg:92 —— 班次总时长与单段时长口径不同，
                // 共用候选名等于允许"段时长"冒充"班次时长"（与 E-1 给 etaToNextStationMinutes 定的同一条规则）。
                plannedDurationMinutes = o.multiNum("plannedDurationMinutes").toInt(),
            )
        }
    }
}

data class PickupTask(
    val orderId: Long,
    val orderNo: String,
    val goodsName: String,
    val senderName: String,
    val senderMobile: String,
    val stationName: String,
    val weightKg: Double,
    val pickupCode: String,
    val fresh: Boolean,
    val deadline: String,
    val type: String,
    /** PRODUCT=商城单 / FREIGHT=货运 / MAIL=邮快件：决定装车妥投走哪组端点 */
    val bizType: String = "",
    /** 3 = 邮快件（必须拍照 + 取件码核销） */
    val orderType: Int = 0,
    /** 快递面单号 */
    val mailNo: String = "",
    val receiverName: String = "",
    val receiverMobile: String = "",
    val receiverAddress: String = "",
    /** 停靠作业勾选态：后端 `/driver/pickups` 的 VO（AppDriverPickupRespVO:13-33，11 个键）没有任何勾选键 ⇒ 恒 null */
    val checked: Boolean? = null,
) {
    /**
     * 分流只看 `bizType`：后端商城单恒发 `bizType="PRODUCT"`（`DriverAppServiceImpl:352`），
     * 小程序 `pages/driver/workbench/workbench.js:885` 也只认这一个条件。
     *
     * 旧写法这里还挂着 `|| orderType == 3 && mailNo.isEmpty()`，而 3 在后端是**邮快件**
     * （`ORDER_TYPE_CARGO=2`/`POSTAL=3`/`PRODUCT=4`，`DriverAppServiceImpl:101-105`；本类 [orderType] 的注释
     * 写的也是"3 = 邮快件"）⇒ 一张没面单号的邮快件会被判成商城单，装车走 `/driver/product-load`、
     * 妥投走 `/driver/product-deliver`，那两个接口在 `ProductOrderServiceImpl` 里按 `ProductOrderDO` 查单，
     * 等于把客运/货运域的一张单塞进商城域（硬规则 1）。枚举码写错位的分支留着就是事故源，删。
     */
    val isProduct: Boolean get() = bizType.equals("PRODUCT", true)
    val isMail: Boolean get() = orderType == 3 || mailNo.isNotEmpty()
    /** 货运单装车必须带司机收件照片 */
    val photoRequired: Boolean get() = !isProduct
    companion object {
        fun parse(o: JSONObject) = PickupTask(
            orderId = o.multiNum("orderId", "id").toLong(),
            orderNo = o.multiStr("orderNo", "bizOrderNo"),
            goodsName = o.multiStr("goodsName", "cargoName", "productName"),
            senderName = o.multiStr("senderName", "customerName"),
            senderMobile = o.multiStr("senderMobile", "mobile", "phone"),
            stationName = o.multiStr("stationName", "pickupStation", "targetStation"),
            weightKg = o.multiNum("weightKg", "weight"),
            pickupCode = o.multiStr("pickupCode", "code"),
            fresh = o.multiBool("freshFlag", "fresh"),
            deadline = o.multiStr("deadline", "latestTime", "departTime").take(5),
            type = o.multiStr("cargoCategory", "category", "orderTypeName"),
            bizType = o.multiStr("bizType", "orderCategory"),
            orderType = o.multiNum("orderType").toInt(),
            mailNo = o.multiStr("mailNo", "mailNumber", "expressNo"),
            receiverName = o.multiStr("receiverName"),
            receiverMobile = o.multiStr("receiverMobile", "receiverPhone"),
            receiverAddress = o.multiStr("receiverAddress"),
        )
    }
}

data class EarningItem(
    val date: String = "",
    val orderNo: String,
    val goodsName: String,
    /** 后端真实键 totalAmount（BigDecimal，可缺席）；amount/fee 曾是无生产者的自造别名，真机上每笔都渲染成 ¥0 */
    val amount: Double? = null,
    val weightKg: Double? = null,
    val statusName: String = "",
    val type: String = "",
    val createTime: String = "",
) {
    val amountText: String get() = amount?.let { "¥${yuan(it)}" } ?: "金额未给"
    val weightText: String get() = weightKg?.let { "${yuan(it)} kg" } ?: "重量未给"
}

/**
 * 司机运营统计。三方证据（2026-09-30 逐字段核）：
 *
 * | 字段 | 后端生产者 | 小程序消费者 | 被删掉的候选名 | 依据 |
 * |---|---|---|---|---|
 * | today | AppDriverEarningsRespVO:18 `todayAmount` | earnings.js:65 | `todayEarnings`、`today` | 后端/小程序 0 命中，只有旧 DemoData 在发 |
 * | total | :22 `totalAmount` | earnings.js:64 | `totalEarnings`、`total` | 同上 |
 * | orderCount | :20 `totalOrders` | earnings.js:66 | `orderCount`、`count` | 同上 |
 * | items | :26 `records` | earnings.js:52-61 | `items`、`details` | 后端只发 records |
 * | shiftCount/pendingCount/todayOrders | :14/:24/:16 | earnings.js:68/69/67 | `todayShiftCount`、`pendingLoadCount`、`todayOrderCount` | 0 生产者 |
 * | **week / month / totalWeight** | **后端全模块 grep 0 命中** | **小程序不读** | — | 旧值 452.0/1830.5/246.8 全部由 DemoData 自造 |
 *
 * week/month/totalWeight 保留字段（不删，避免打断旧调用点），但解析层不再读任何键，值恒为 null；
 * UI 只能显示"未给"，不许显示 0 —— F-2 §五 禁止把 B（缺字段）当 A（真零值）说。
 */
data class Earnings(
    val today: Double? = null,
    val week: Double? = null,
    val month: Double? = null,
    val total: Double? = null,
    val orderCount: Int? = null,
    val totalWeight: Double? = null,
    val items: List<EarningItem> = emptyList(),
    /** 今日计划班次 */
    val shiftCount: Int? = null,
    /** 待装车任务数 */
    val pendingCount: Int? = null,
    /** 今日订单数（后端 todayOrders，与累计 totalOrders 是两个口径） */
    val todayOrders: Int? = null,
) {
    val todayText: String get() = today?.let { yuan(it) } ?: "未给"
    val weekText: String get() = week?.let { yuan(it) } ?: "未给"
    val monthText: String get() = month?.let { yuan(it) } ?: "未给"
    val totalText: String get() = total?.let { "${yuan(it)} 元" } ?: "累计收益未给"
    val todayBadgeText: String get() = today?.let { "今日 ${yuan(it)} 元" } ?: "今日收益未给"
    val orderCountText: String get() = orderCount?.let { "$it 单" } ?: "累计单数未给"
    val todayOrdersText: String get() = todayOrders?.let { "$it 单" } ?: "今日订单数未给"
    val totalWeightText: String get() = totalWeight?.let { "${yuan(it)} kg" } ?: "后端无此口径"
}

data class HandoverTask(
    val id: Long,
    val orderNo: String,
    val goodsName: String,
    val weightKg: Double,
    val fromStation: String,
    val toStation: String,
    val fromPlate: String,
    val toPlate: String,
    val status: String,
    val actionLabel: String,
    /**
     * 后端有没有说"这一单必须拍照"。
     * `AppDriverHandoverRespVO.java:14-62` 的 17 个键（id/orderId/orderNo/goodsName/stationId/stationName/
     * fromDriverId/fromDriverName/toDriverId/toDriverName/itemCount/weightKg/photoUrl/status/statusName/
     * handoverTime/remark）里没有任何拍照要求位 ⇒ 恒 null；
     * 改前 `multiBool("needPhoto",...)` 缺键塌成 false，UI 于是把"后端没要求"讲成了一句确定结论。
     * 凭证照片本身仍然可以上传（photoUrl:50 是真实回读键），这里只管"要求"这个口径。
     */
    val needPhoto: Boolean? = null,
    val time: String,
    /** 交接件数 */
    val itemCount: Int = 0,
    val fromDriverName: String = "",
    val toDriverName: String = "",
    /** 换乘站 */
    val transferStation: String = "",
    val handoverTime: String = "",
    /**
     * 后端已入库的凭证照片地址（`AppDriverHandoverRespVO.photoUrl`）。
     * 交接提交的回执只认这个回读值，不认"本机上传成功了"（需求 §7：重新查询仍能看到照片才算闭环）。
     */
    val photoUrl: String = "",
) {
    companion object {
        fun parse(o: JSONObject) = HandoverTask(
            id = o.multiNum("id", "handoverId").toLong(),
            orderNo = o.multiStr("orderNo", "bizOrderNo"),
            goodsName = o.multiStr("goodsName", "cargoName"),
            weightKg = o.multiNum("weightKg", "weight"),
            fromStation = o.multiStr("fromStation", "currentStation"),
            toStation = o.multiStr("toStation", "nextStation"),
            fromPlate = o.multiStr("fromPlate", "vehiclePlate"),
            toPlate = o.multiStr("toPlate", "nextPlate"),
            // 后端的 status 是**数字**（TransportHandoverStatusEnum 0..7），文案在 statusName；
            // 直接读 status 会把"0"渲染成状态标签，且 isDone() 永远不成立（已完成的交接一直留在待办里）。
            status = o.multiStr("statusName", "statusLabel", "status"),
            actionLabel = o.multiStr("action", "actionLabel", "nextAction"),
            // needPhoto/photoRequired/forcePhoto 三面 0 生产者：AppDriverHandoverRespVO.java:14-62 的 17 个键里
            // 只有 photoUrl:50（已入库凭证地址），没有任何"这单必须拍照"的要求位 ⇒ 恒 null，见下方字段声明。
            time = o.timeOf("createTime", "planTime"),
            itemCount = o.multiNum("itemCount", "count", "pieces").toInt(),
            fromDriverName = o.multiStr("fromDriverName", "fromDriver"),
            toDriverName = o.multiStr("toDriverName", "toDriver"),
            transferStation = o.multiStr("transferStation", "handoverStation", "stationName"),
            handoverTime = o.timeOf("handoverTime", "confirmTime"),
            photoUrl = o.multiStr("photoUrl", "handoverPhotoUrl"),
        )
    }
}

// ---------- 内置导航 ----------
enum class NavIcon { DEPART, STRAIGHT, SLIGHT_LEFT, TURN_LEFT, UTURN_LEFT, SLIGHT_RIGHT, TURN_RIGHT, UTURN_RIGHT, MERGE, ARRIVE }

data class NavStep(
    val icon: NavIcon,
    val text: String,
    val detail: String,
    val distanceMeters: Int,
    val lanes: List<String>,
    /** 建议车道：后端 `/driver/route` 的 VO 里没有任何车道键，只有演示面在发 ⇒ 没给就是 null，页面必须说"猜的" */
    val recommendedLane: Int? = null,
    /** 单步 ETA：同上，三方都无生产者（`etaSeconds`/`seconds` 都不在后端契约里） */
    val etaSeconds: Int? = null,
)

data class RouteGuide(
    val steps: List<NavStep>,
    /** 只装通过真实道路校验的折线；未通过时是空列表，由 RouteMap 渲染"路线不可用" */
    val polyline: List<Geo>,
    val stations: List<Station>,
    val totalMeters: Double,
    /**
     * 全程分钟：只认通过校验的真实道路 `roadEtaSeconds`（后端真实键是 `routeDurationSeconds`，
     * `AppDriverRouteRespVO:48`），或 payload 里现存的 `etaMinutes`。2026-09-30 逐字段实测：
     * **真实报文里没有 `etaMinutes` 这个键**，全工程唯一发它的是演示面的 `/driver/route` 分支
     * ⇒ 正式接口下这一项只可能来自道路耗时；都没给 = null（旧写法塌成 0）。
     * 候选名里曾有第三个键 `"eta"` —— 后端、小程序、演示面三方都没有它（实测三处 0 命中），是客户端自造的
     * "第四个真相源"，已删并由 T-ai 钉住。
     */
    val etaMinutes: Int?,
    val currentStep: Int,
    val nextStation: String,
    /**
     * 旧字段：后端 `nextStopDistanceMeters` 是 Haversine 直线距离，不是道路里程。
     * 保留只为不破坏兼容，正式界面禁止读它 —— 读 [nextStopRoadDistanceMeters]。
     */
    val nextStationDistanceMeters: Int,
    val deviated: Boolean,
    val vehicleGeo: Geo?,
    val headingDegrees: Double,
    val dataSource: String,
    /** 折线来源，取值见 core/map/RouteSource；空表示后端没说 */
    val routeSourceName: String = "",
    /** 全路线真实道路里程（米）；0 = 来源没给，界面显示"—"，禁止用直线距离补 */
    val roadDistanceMeters: Int = 0,
    /** 全路线真实道路耗时（秒）；0 = 来源没给 */
    val roadEtaSeconds: Int = 0,
    /** 下一任务点真实道路里程（米）；0 = 来源没给 */
    val nextStopRoadDistanceMeters: Int = 0,
    /** 下一任务点真实道路 ETA（秒）；0 = 来源没给 */
    val nextStopRoadEtaSeconds: Int = 0,
    /** 非空即"路线不可用"，内容是要展示给用户的原因 */
    val routeUnavailableReason: String? = null,
    /**
     * 调度任务点（`/driver/route` 的 `stops[]`，后端 `AppDriverRouteRespVO.Stop`）。
     * 与 [stations] 是同一数组的两种读法：stations 走公交站模型（历史调用点在用），
     * stops 带序号/动作/件数/状态，任务地图只读这个（见审计 §62）。
     */
    val stops: List<MissionStop> = emptyList(),
    /**
     * 后端点名的"下一个要去的任务点"名字（`AppDriverRouteRespVO.nextStopName`，
     * 生产者 `DriverAppServiceImpl.fillNextActionAndNav:668`）。
     *
     * 它和 [nextStopRoadDistanceMeters] 是**同一次循环里同一个 `next` 写出来的**
     * （`:649-656` 取第一个 status≠7 的明细，`:717` 才写里程），所以"这段道路里程是关于哪个点的"
     * 这句话只由后端说；不读它就得客户端自己猜一个点去配这段里程（那是替后端补话）。
     * 空 = 后端没说（`next == null` 时后端只发 `nextActionName=本班任务已完成`，压根不发名字）。
     */
    val nextStopName: String = "",
    /** 后端给的动作话术（`nextActionName`，同一生产者 `:668-679`）；空 = 后端没说 */
    val nextActionName: String = "",
) {
    /** 有通过校验的道路折线才谈得上导航 */
    val hasRealRoad: Boolean get() = routeUnavailableReason == null && polyline.size >= 2

    companion object {
        val EMPTY = RouteGuide(emptyList(), emptyList(), emptyList(), 0.0, 0, 0, "", 0, false, null, 0.0, "")
    }
}

// ---------- 地址 / 反馈 / 乘车安排 ----------
data class AddressItem(
    val id: Long,
    val name: String,
    val mobile: String,
    val region: String,
    val detail: String,
    val isDefault: Boolean,
    /** 地区 ID（后端入库必需字段，小程序写路径用 areaId） */
    val areaId: String = "",
    /** 后端直接下发的区域文案（优先于省市区拼接） */
    val regionText: String = "",
) {
    /** 展示用区域：优先 regionText，回退拼接 */
    val regionLabel: String get() = regionText.ifEmpty { region }
    companion object {
        fun parse(o: JSONObject) = AddressItem(
            id = o.multiNum("id").toLong(),
            name = o.multiStr("name", "receiver"),
            mobile = o.multiStr("mobile", "phone"),
            region = listOf(o.multiStr("provinceName"), o.multiStr("cityName"), o.multiStr("districtName"))
                .filter { it.isNotEmpty() }.joinToString(" "),
            detail = o.multiStr("detailAddress", "detail"),
            isDefault = o.multiBool("defaultStatus", "isDefault"),
            areaId = o.multiStr("areaId"),
            regionText = o.multiStr("regionText", "areaText"),
        )
    }
}

/** 省市区三级节点（来源 GET /app-api/system/area/tree，免登录） */
data class AreaNode(val id: String, val name: String, val children: List<AreaNode> = emptyList()) {
    companion object {
        fun parse(o: JSONObject): AreaNode = AreaNode(
            id = o.multiStr("id", "areaId", "code"),
            name = o.multiStr("name", "areaName"),
            children = o.multiArr("children", "childList").map { parse(it) },
        )
    }
}

data class FeedbackItem(
    val id: Long,
    val content: String,
    val reply: String,
    val status: String,
    val time: String,
    /** status===1 即已回复（小程序 feedback.wxml:37 判定口径） */
    val replied: Boolean = false,
) {
    val statusLabel: String get() = if (replied) "已回复" else status.ifEmpty { "待处理" }
    companion object {
        fun parse(o: JSONObject): FeedbackItem {
            val code = o.multiNum("status").toInt()
            val reply = o.multiStr("replyContent", "reply", "platformReply")
            return FeedbackItem(
                id = o.multiNum("id").toLong(),
                content = o.multiStr("content"),
                reply = reply,
                status = o.multiStr("statusName", "statusLabel"),
                time = o.timeOf(),
                replied = code == 1 || reply.isNotEmpty(),
            )
        }
    }
}

data class Arrangement(
    val orderNo: String,
    val shiftCode: String,
    val plate: String,
    val targetStation: String,
    /**
     * 到站分钟。实测 `AppSendArrangementRespVO`（11 个字段：orderNo/status/statusName/上下车站/planId/planStatus/vehiclePlateNo…）
     * **没有** `etaMinutes` 键，所以对接真后端时这里恒为 null、到站提醒恒不响（旧实现塌成 0，0 不在 1..10 里同样不响，
     * 只是"看起来像还没到时间"）。演示面自己造了这个键。真实数据源应是 `/send/page` 的 `carrierEtaMinutes`（审计 §68）。
     */
    val etaMinutes: Int?,
    val dataSource: String,
    val statusLabel: String,
    /** 上车站 / 下车站（村民到站通知） */
    val boardingStation: String = "",
    val alightingStation: String = "",
    /**
     * 调度方案状态**码**（后端 `AppSendArrangementRespVO.planStatus` 是 Integer，不是文案）。
     * ⚠ 不能用 `multiStr` 读：那条路会把数字 2 直接念成字符串"2"给用户看。
     * 码→名的表在 `ui/user/ArrangementRows.kt`（对齐后端 `DispatchPlanStatusEnum` 六个值），
     * null = 后端没给方案（这条安排还没落到调度方案明细），不许塌成 0（"待审核"）。
     */
    val planStatusCode: Int? = null,
) {
    companion object {
        fun parse(o: JSONObject) = Arrangement(
            orderNo = o.multiStr("orderNo", "bizOrderNo"),
            shiftCode = o.multiStr("shiftCode"),
            plate = o.multiStr("vehiclePlate", "plate", "vehiclePlateNo"),
            targetStation = o.multiStr("targetStation", "stationName"),
            etaMinutes = o.multiNumOrNull("etaMinutes")?.toInt(),
            dataSource = o.multiStr("dataSource"),
            statusLabel = o.multiStr("statusName", "statusLabel", "status"),
            boardingStation = o.multiStr("boardingStationName", "boardingStation"),
            alightingStation = o.multiStr("alightingStationName", "alightingStation"),
            planStatusCode = o.multiNumOrNull("planStatus")?.toInt(),
        )
    }
}

/**
 * 费用试算结果（字段名与小程序 send.wxml:282-287 一致：amount/itemFee/distanceFee/itemCount/distanceKm）。
 * ⚠ [distanceKm] 在契约里是**估算口径**且可空，见该字段注释 —— 别把它当道路里程印出去。
 *
 * F-2 §三：除 [distanceKm] 外这四个字段也曾一律 `multiNum`，缺键被读成 0.0 后界面显示"¥0 / 件数费 ¥0"，
 * 把"后端没给"说成"免费"。三方实测（2026-09-30）：
 *   producer 只有 `AppSendQuoteRespVO.java:13-25`（amount/itemFee/distanceFee/distanceKm/itemCount/pricePerItem/pricePerKm），
 *   `AppSendController.java:506-513` 逐字段 set，`CargoPricingService.java:45-75` 里只有 distanceKm 会为 null（:55 站点缺坐标）；
 *   小程序只读 quote.amount/itemFee/distanceFee/itemCount/distanceKm（send.wxml:282-287，且 :287 对 distanceKm 已写 `!= null`）；
 *   演示面 DemoData.kt:472-476 也只写这五个键。
 * ⇒ 旧的别名 fee/totalPrice/unitFee/mileageFee/count **三面都没有生产者**（count 的 9 处 java 命中全在
 *   DashboardController/BigScreenServiceImpl/JobController，与 /send/quote 报文无关），按 §二 同一条规则删除；
 *   同时四个字段改成可空，缺键就说"未给"。不引入 ContractParseException：本文件的缺失态一律用可空类型表达
 *   （distanceKm/ETA 家族已是这个口径），一个缺失态两套机制只会让解析层再分一次叉。
 */
data class QuoteResult(
    val amount: Double? = null,
    val itemFee: Double? = null,
    val distanceFee: Double? = null,
    val itemCount: Int? = null,
    /**
     * 计价里程(km)：`AppSendQuoteRespVO.java:21-22` BigDecimal，Schema 原文
     * "**直线距离 × 路网系数**；站点缺坐标时为 null" ⇒ 既可空、本身就不是道路里程，
     * 展示必须带口径（审计 §72 F5）。旧读法 `multiNum("distanceKm","distance")` 同时犯两个错：
     * 缺键塌成 0.0，以及把别的报文里表示**米**的 `distance` 当公里读。
     */
    val distanceKm: Double? = null,
) {
    /** 金额纯数字串（千位不带分隔，整数不补 .00，与 UI 侧 formatPrice 同口径）；缺键为"未给"而不是 0 */
    val amountValueText: String get() = amount?.let { yuan(it) } ?: "未给"

    /** 带币符的整句：给核对页/车票副券用，缺键不许显示 ¥0 */
    val amountText: String get() = amount?.let { "¥${yuan(it)}" } ?: "金额未给"

    /**
     * 件数费 + 里程费那一行，对齐小程序 send.wxml:287 的条件拼接：
     * [distanceFee] 后端无坐标时真给 0（CargoPricingService.java:56）⇒ A 类真零值，按 mp 口径不叠加；
     * 缺键（null）是 B 类"没给" ⇒ 明说未给，两者不许混成同一个"¥0"。
     */
    val feeBreakdownText: String
        get() = buildString {
            append(itemFee?.let { "件数费 ¥${yuan(it)}" } ?: "件数费未给")
            when (distanceFee) {
                null -> append(" · 里程费未给")
                0.0 -> Unit
                else -> append(" + 里程费 ¥${yuan(distanceFee)}")
            }
            append("（").append(itemCount?.let { "$it 件" } ?: "件数未给").append(" · ").append(distanceCaliberText).append("）")
        }

    /**
     * 计价里程那一句话：有值也必须带"直线距离 × 路网系数"口径，缺值说"未给"而不是 0 km（§72 F5）。
     * 文案落在模型 getter 上是为了能被 JVM 契约测试断言 —— 只写在 Compose 里就只剩 grep 判据。
     */
    val distanceCaliberText: String
        get() = distanceKm?.let {
            "计价里程 ${String.format(java.util.Locale.CHINA, "%.1f", it)} km（直线距离×路网系数，非道路里程）"
        } ?: "计价里程未给"

    companion object {
        fun parse(o: JSONObject): QuoteResult = QuoteResult(
            amount = o.multiNumOrNull("amount"),
            itemFee = o.multiNumOrNull("itemFee"),
            distanceFee = o.multiNumOrNull("distanceFee"),
            // 后端 normalize 过件数（itemCount null/<=0 按 1 件，CargoPricingService.java:46），
            // 客户端再拿请求值兜底就会显示一个服务端根本没用的数字 ⇒ 缺键只说未给。
            itemCount = o.multiNumOrNull("itemCount")?.toInt(),
            distanceKm = o.multiNumOrNull("distanceKm"),
        )
    }
}

/** 路线预估（字段名与小程序 send.wxml:297-309 一致：distanceKm/durationMinutes/provider） */
data class RoutePreview(
    /** `RoutePreviewRespVO.distanceKm` 明确写"不可用时为 null"（java:15-16），塌成 0 km 就是给用户看一个不存在的里程 */
    val distanceKm: Double? = null,
    /** `durationMinutes` 同样是可空 Integer（java:18-19）；旧解析还把 `etaMinutes` 当别名，那个键在这个契约里根本不存在 */
    val durationMinutes: Int? = null,
    val provider: String = "",
) {
    /** provider 非 amap 表示路网不可用、当前是直线估算，页面需提示 */
    val estimated: Boolean get() = provider.isNotEmpty() && !provider.equals("amap", true)

    companion object {
        /**
         * 候选键名逐个抄 `RoutePreviewRespVO`：只有 `distanceKm`(BigDecimal) / `durationMinutes`(Integer) /
         * `provider`(String) / `available` / `warning` 五个键。
         * ⑬ 之前 Repo 里写的是 `multiNumOrNull("distanceKm", "distance")` 和 `("durationMinutes", "minutes")` ——
         * 后两个别名在这个契约里根本不存在，而 `distance` 在别的报文里是**米**（`BusLine.distance`），
         * 留着它等于"缺公里键就拿米当公里"（审计 §72）。解析收回模型里，判据和测试才有唯一落脚点。
         */
        fun parse(o: JSONObject) = RoutePreview(
            distanceKm = o.multiNumOrNull("distanceKm"),
            durationMinutes = o.multiNumOrNull("durationMinutes")?.toInt(),
            provider = o.multiStr("provider", "navigationSource"),
        )
    }
}

/**
 * 可达性判定的三态（审计 §11）。**只有 REACHABLE 才允许出现"可以运输 / 车辆能到"的口径**：
 * UNKNOWN 既不能说可运，也**不许本机编一条"不可达原因"** —— 原因是后端的 `reasonCode` 给的，不是本机推的。
 */
enum class ReachabilityState(val label: String) {
    REACHABLE("车辆可就近上门"),
    NOT_REACHABLE("需送到服务站点交接"),
    UNKNOWN("可达性暂不可用"),
}

/**
 * 当前位置可达性（`POST /send/reachability`）。字段口径**抄** `AppSendReachabilityRespVO.java:19-56`：
 * `distanceKm` 是**公里**（`BigDecimal`，两位小数），站名在 `recommendedStation.name`，
 * `distanceProvider` 实际有三个取值 —— `AMAP_ROAD` / `HAVERSINE`（初始默认）/ `EUCLIDEAN`
 * （算法服务返回了里程但来源不在真实道路白名单里，`AppSendReachabilityService:110-126`）；
 * VO 的 `@Schema` 只写了前两个，属注释漂移，以代码为准。
 * `reasonCode` 同样有第三个值 `ROAD_UNREACHABLE`（"未提供当前位置"那条早退分支，`:53-57`），VO 注释也没列全。
 */
data class Reachability(
    val reachable: Boolean,
    val nearestStation: String,
    /** 旧字段：后端契约里**没有** `distanceMeters`，只保留解析以兼容旧报文；界面一律改读 [distanceKm]（审计 §34 ③）。
     *  F-3 ② 改可空：旧报文带键照旧给值，没给就是 null，不再冒充"距离 0 米"。 */
    val distanceMeters: Int? = null,
    val walkMinutes: Int?,
    val tip: String,
    val state: ReachabilityState = ReachabilityState.UNKNOWN,
    val reasonCode: String = "",
    /** 后端 `serviceMode` 原样保存（`ServiceModeEnum` 五值），提交时**不翻译、不改写**（小程序 `send.js:452` 同口径） */
    val serviceMode: String = "",
    val distanceKm: Double? = null,
    val stationId: Long = 0L,
    val stationName: String = "",
    val stationGeo: Geo? = null,
    val distanceProvider: String = "",
    val message: String = "",
) {
    /** 站名：`recommendedStation.name` 优先，旧报文的 `nearestStation` 兜底 */
    val stationLabel: String get() = stationName.ifEmpty { nearestStation }

    /** 真实道路来源才配"道路距离"这句话；直线估算必须写出来（审计 §34 ②），本机**不做**直线→道路的换算 */
    val isRealRoadDistance: Boolean get() = distanceProvider.uppercase(java.util.Locale.ROOT) == "AMAP_ROAD"

    /**
     * `ServiceModeEnum.java:21-25` 的中文名字面复制（后端没有 code→name 的接口，只有 `nameOf`）。
     * 兼作**合法性判定**：五个码之外一律空串 —— 提交时空串就不回传，
     * 免得像旧代码那样把不在枚举里的 `"STATION"` 落进 `CargoOrderDO.pickupServiceMode`
     * （`TransportOrderServiceImpl:176` 原样落库、`:202-203` 非空即采信客户端值）。
     */
    val serviceModeLabel: String get() = when (serviceMode.trim().uppercase(java.util.Locale.ROOT)) {
        "DOOR_PICKUP" -> "上门/就近交接"
        "NEAREST_STATION" -> "最近站点交接"
        "SAFE_ROADSIDE" -> "安全点交接"
        "CUSTOMER_TO_STATION" -> "客户送站"
        "STATION_TO_STATION" -> "站到站"
        else -> ""
    }
    val providerLabel: String get() = when (distanceProvider.uppercase(java.util.Locale.ROOT)) {
        "AMAP_ROAD" -> "（道路距离）"
        "HAVERSINE", "EUCLIDEAN" -> "（直线估算，不是道路里程）"
        "" -> ""
        else -> "（来源 $distanceProvider）"
    }

    /** 距离文案：null 一律说"接口未给"，不出现"0 公里" */
    val distanceText: String
        get() = distanceKm?.let { "约 ${String.format(java.util.Locale.CHINA, "%.2f", it)} 公里$providerLabel" }
            ?: "距离接口未给"

    val walkText: String get() = walkMinutes?.let { "步行约 $it 分钟" } ?: "步行时间接口未给"

    /**
     * 不可达原因文案：只按后端 `reasonCode` 分支，本机不猜。
     * 三个已知码来自 `ReviewReasonCodeEnum`（`:18/:21/:23`）；未知码原样带出来，方便对账而不是吞掉。
     */
    fun reasonText(fallback: String): String = when (reasonCode) {
        "USER_LOCATION_UNREACHABLE" -> "当前位置不适合车辆进入，请送到服务站点交接"
        "NO_SAFE_HANDOFF_POINT" -> "附近没有可用站点（含车辆可停靠的站点），请联系平台"
        "ROAD_UNREACHABLE" -> "未提供当前位置，无法判断可达性"
        "" -> fallback
        else -> "后端返回原因码 $reasonCode：${message.ifEmpty { fallback }}"
    }

    companion object {
        /**
         * 后端没给判定时的空态（接口失败、demo 关闭后没有兜底、报文缺 `reachable`）。
         * 全部字段取"没值"而不是"看着像值"：界面读到 null/空串才能说"接口未给"，
         * 也保证 UNKNOWN 态不可能被误渲染成"可以运输"。
         */
        fun unknown(): Reachability = Reachability(
            reachable = false,
            nearestStation = "",
            distanceMeters = null,
            walkMinutes = null,
            tip = "",
        )

        fun parse(o: JSONObject): Reachability {
            val rec = o.optJSONObject("recommendedStation")
            val reachable = o.multiBoolOrNull("reachable", "directReach")
            val lat = rec?.multiNumOrNull("latitude")
            val lng = rec?.multiNumOrNull("longitude")
            return Reachability(
                reachable = reachable == true,
                nearestStation = o.multiStr("nearestStation", "stationName"),
                // AppSendReachabilityRespVO 全文只有 distanceKm(:29/:55) 一个距离键，后端/小程序/演示面三方都没有
                // `distance`/`distanceMeters` 的写点 ⇒ 这条读法恒 null。撤读但不删字段（展示走 distanceText，
                // 它已经只会说"约 X 公里"或"接口未给"，没有第二个口径）。
                distanceMeters = null,
                // AppSendReachabilityRespVO:32 只有 walkMinutes 一个名字；`minutes` 后端/小程序/演示集三方
                // 都查不到生产者，而且这一个对象里同时有 distanceKm，`minutes` 极易是别的报文里的"总时长"。
                walkMinutes = o.multiNumOrNull("walkMinutes")?.toInt(),
                tip = o.multiStr("tip", "message"),
                state = when (reachable) {
                    null -> ReachabilityState.UNKNOWN
                    true -> ReachabilityState.REACHABLE
                    false -> ReachabilityState.NOT_REACHABLE
                },
                reasonCode = o.multiStr("reasonCode"),
                serviceMode = o.multiStr("serviceMode"),
                distanceKm = o.multiNumOrNull("distanceKm") ?: rec?.multiNumOrNull("distanceKm"),
                stationId = rec?.multiNumOrNull("id")?.toLong() ?: 0L,
                stationName = rec?.multiStr("name", "stationName") ?: "",
                stationGeo = if (lat != null && lng != null && (lat != 0.0 || lng != 0.0)) Geo(lat, lng) else null,
                distanceProvider = o.multiStr("distanceProvider"),
                message = o.multiStr("message"),
            )
        }
    }
}

// ---------- 司机任务段（GET /transport/driver/tasks，算法派单结果） ----------
/**
 * `/driver/tasks` 的一行 = 一个任务段里的一个经停动作。行本体是 [MissionStop]（解析见 [MissionStop.parseTaskItem]），
 * 外面这一圈只装**段**级的三个键：`planId / taskWindowStart / taskWindowEnd`
 * —— 它们来自 `DispatchPlanDO`（`DriverAppServiceImpl.tasks:422-427`），`/driver/route` 的 stops 里没有，
 * 所以既不能塞进 MissionStop（那条链路的任务点没有窗口），也不能靠 `id` 猜。
 *
 * ⚠ 旧模型 8 个字段读的是 8 个**不存在的键**：`taskType`/`bizType`、`targetStation`、`cargoName`、
 * `itemCount`/`count`、`action`、`taskName`、`planTime`，外加 goodsName 回落 `createTime`；
 * 而 `goodsName` 在这个 VO 里连生产者都没有（它属于收益明细 `AppDriverEarningsRecordRespVO`，
 * 且只在命中 CargoOrderDO 时才 set）⇒ 真接口下整块恒空串/0，界面却照样渲染"货物  件 · "那样一行缺内容。
 * 现在只认 VO 里真有的键名，一个别名不加。VO 还有 `id`（调度明细编号）/`plannedDepartureTime`/
 * `serviceDurationSeconds`/`segmentDurationSeconds`/`segmentDistanceKm`/`orderId` 六个真键：
 * 本页路单不显示 ⇒ 不读（读了就是养出第二个真相源，等 UI 真要了再加）。
 */
data class DriverTask(
    val planId: Long,
    val windowStartText: String,
    val windowEndText: String,
    val stop: MissionStop,
) {
    companion object {
        fun parse(o: JSONObject) = DriverTask(
            planId = o.multiNumOrNull("planId")?.toLong() ?: -1L,
            // 事件时刻走 reportTimeOf：回落 createTime 等于给任务段编一个没发生过的窗口
            windowStartText = o.reportTimeOf("taskWindowStart"),
            windowEndText = o.reportTimeOf("taskWindowEnd"),
            stop = MissionStop.parseTaskItem(o),
        )
    }
}

/** 任务段里的一站：同站多动作（客/货同站执行）聚合成一条，行首取该站 visitSequence 最小的那行 */
data class DriverTaskStation(
    val head: MissionStop,
    val actions: List<MissionStop>,
)

/**
 * 一个 `planId` 的完整任务段：按 `visitSequence` 有序、同站聚合。
 * 口径抄小程序 `workbench.js:221-254`（buildTaskSegment），差别只有一处：后端整表按
 * `visitSequence` 升序返回（`DispatchPlanItemMapper:14-18`，多方案是混排的），段的先后就按
 * "该 planId 第一次出现的位置"排 —— 顺序只跟后端，本机不重排（硬规则 3：调度决定去哪里）。
 */
data class DriverTaskSegment(
    val planId: Long,
    val windowStartText: String,
    val windowEndText: String,
    val stations: List<DriverTaskStation>,
) {
    /** 任务窗口文案：后端没给就说没给，不拿本机时钟或班次时刻补一个窗口 */
    val windowText: String
        get() = when {
            windowStartText.isNotEmpty() && windowEndText.isNotEmpty() -> "任务窗口 $windowStartText ~ $windowEndText"
            windowStartText.isNotEmpty() -> "任务窗口自 $windowStartText 起（结束时刻接口未给）"
            windowEndText.isNotEmpty() -> "任务窗口至 $windowEndText（开始时刻接口未给）"
            else -> "任务窗口接口未给"
        }

    companion object {
        fun group(items: List<DriverTask>): List<DriverTaskSegment> =
            items.groupBy { it.planId }.map { (planId, rows) ->
                DriverTaskSegment(
                    planId = planId,
                    windowStartText = rows.firstNotNullOfOrNull { it.windowStartText.ifEmpty { null } }.orEmpty(),
                    windowEndText = rows.firstNotNullOfOrNull { it.windowEndText.ifEmpty { null } }.orEmpty(),
                    stations = rows.sortedBy { it.stop.visitSequence }
                        .groupBy { it.stop.stationId }
                        .map { (_, atStation) ->
                            val ordered = atStation.sortedBy { it.stop.visitSequence }
                            DriverTaskStation(ordered.first().stop, ordered.map { it.stop })
                        },
                )
            }
    }
}

/** 订阅消息模板（GET /member/social-user/get-subscribe-template-list） */
data class SubscribeTemplate(val id: String, val title: String, val type: String) {
    companion object {
        fun parse(o: JSONObject) = SubscribeTemplate(
            id = o.multiStr("id", "templateId"),
            title = o.multiStr("title", "name"),
            type = o.multiStr("type", "templateType"),
        )
    }
}
