package com.shanxiang.buspost.data.repo

import com.shanxiang.buspost.core.map.RouteCheck
import com.shanxiang.buspost.core.map.RouteRepository
import com.shanxiang.buspost.core.map.RouteSource
import com.shanxiang.buspost.core.net.Api
import com.shanxiang.buspost.core.net.ApiException
import com.shanxiang.buspost.core.net.ApiResult
import com.shanxiang.buspost.core.net.NetworkException
import com.shanxiang.buspost.core.net.Payload
import com.shanxiang.buspost.core.net.UnauthorizedException
import com.shanxiang.buspost.core.net.attempt
import com.shanxiang.buspost.core.net.safeApiCall
import com.shanxiang.buspost.data.model.*
import com.shanxiang.buspost.data.store.Prefs
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

/**
 * 列表读取结果。demo=true 只可能来自显式开启的演示模式（release 恒为 false），页面必须挂"演示数据"标签；
 * error 非空表示这次刷新**失败**，页面保留旧列表并显示轻量错误条 + 重试，不能拿演示数据顶上去。
 */
class Feed<T>(
    val items: List<T>,
    val demo: Boolean = false,
    val error: String? = null,
    /** 后端分页总数：页面据此判断"还有下一页吗"，缺省用当前页条数兜底 */
    val total: Int = items.size,
    /**
     * 失败时的**原始异常**，供需要"是哪一类失败"的消费者分类（目前只有后台轮询 `AlertEngine`，见 ⑩ / D-044）。
     * ⚠ 它**不是**给页面念的：页面照旧读 [error] 那句文案。把它放进 Feed 是因为 `errorTextOf` 之后
     * 类型信息就没了，而 Worker 要区分"退避重试"与"重试也不会变好" —— 用文案字符串去分等于重新造一次
     * "拿中文当判据"（本项目在 ETA / 状态映射上踩过两次的口径）。默认 null，其余调用点一字未动。
     */
    val cause: Throwable? = null,
)

/** 单对象读取结果 */
class One<T>(val value: T?, val demo: Boolean = false, val error: String? = null)

/**
 * 页面级取数状态：把"在加载 / 请求失败 / 真的没数据 / 演示数据 / 来自后端"五件事分开。
 * 之前页面把失败渲染成空列表（看起来像"没有数据"），或把演示数据渲染成真实数据，都是这一层缺字段导致的。
 * empty 是派生属性：只有 success（请求成功且后端确实回了空集合）才允许渲染 EmptyState。
 */
data class RemoteState<T>(
    val data: T? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val demo: Boolean = false,
    val fromServer: Boolean = false,
    /** 本次请求已成功：error=null 且未抛异常。区分"成功但空"与"失败所以空" */
    val success: Boolean = false,
) {
    val isEmpty: Boolean get() = success && data == null
    val isError: Boolean get() = error != null
}

/**
 * 网络层异常 → 页面文案。分类必须保留：
 * 401 不能被写成"网络异常"，否则用户会反复下拉重试一个已经失效的会话。
 */
internal fun errorTextOf(e: Throwable): String = when (e) {
    is DriverUnboundException -> DRIVER_UNBOUND
    is UnauthorizedException -> e.message
    is NetworkException -> e.message
    is ApiException -> e.message
    else -> e.message ?: "加载失败，请下拉重试"
}

/**
 * 司机档案未绑定：请求根本不该发出去（审计 §29）。
 * 后端对 `driverId=0` 回空列表，页面会把"你没有司机档案"渲染成"今天没有任务"，
 * 这是把真因洗成假象，所以这一类异常在 [driverIdParam] 里抛出，永远不带请求上网络。
 */
class DriverUnboundException : Exception(DRIVER_UNBOUND)

/** 未绑定司机档案时各层的统一说法：错误条、状态条、上报服务都引用这一个常量 */
const val DRIVER_UNBOUND = "未绑定司机档案：后端没有该账号的 driverId，请联系站点核对工号"

/** 写操作回执：demo=true 时页面提示"演示模式：本次操作未提交后端"，避免用户误以为已入账 */
data class Ack(
    val demo: Boolean,
    val orderNo: String = "",
    val pickupCode: String = "",
    val amount: Double = 0.0,
    val url: String = "",
    /** 承运审核状态：1通过 2需操作 3待人工 4不通过（send/create 回执，与小程序 review.js 同口径） */
    val reviewStatus: Int = 0,
    val reviewReasonCodes: String = "",
    val servicePointStationName: String = "",
    val servicePointDistanceKm: Double = 0.0,
    val servicePointLatitude: Double = 0.0,
    val servicePointLongitude: Double = 0.0,
    /** 写操作失败原因：非空表示后端没受理，页面必须保留原状态并提示，不能假装成功 */
    val error: String? = null,
    /**
     * 是否已被列表回读确认。create 返回 200 只说明后端受理了，
     * 回读失败时页面要说"暂未在列表确认"，不能写成"已确认"。
     */
    val confirmed: Boolean = true,
    /**
     * true = 本次请求**被本地闸拦下、根本没发给后端**（同一个目标上的同一个动作还在途中，或刚成功不到 10 秒）。
     * 页面必须把它当成"什么都没发生"：既不能说"已提交后端"，也不能说"失败"，状态一律以回读到的为准。
     */
    val notSent: Boolean = false,
)

private const val M = "/app-api/member"
private const val T = "/app-api/transport"

/** 幂等键的十六进制字符表（小写，与后端 MD5 的十六进制表示同一形状；不用 `String.format` 是为了不碰默认 Locale） */
private val HEX_DIGITS = "0123456789abcdef".toCharArray()

object Repo {

    // ---------- 会话 ----------

    /** 进程启动时把本地会话装回网络层 */
    fun restoreSession() {
        val stored = Prefs.token
        Api.token = stored.ifEmpty { null }
        // 演示会话标记只在显式开启演示模式的构建里可能为真
        Api.demoSession = Api.demoAllowed && stored.startsWith("demo-")
    }

    /**
     * 静默续期（任务：登录态）。由 `Api.refresher` 在业务请求 401 时调用，也可供冷启动临期检查复用。
     *
     * 成功条件写死成"后端真的给了新的 accessToken"：拿不到新 token 就返回 false，
     * 让上层按原路走"清盘跳登录" —— 带着旧 token 假装续期成功，比让用户重登更坏。
     * 没有 refreshToken（老会话/清过盘）也直接 false，不发无意义的请求。
     */
    suspend fun refreshTokenOnce(): Boolean {
        val refresh = Prefs.refreshToken
        if (refresh.isEmpty()) return false
        // 后端是 `@RequestParam String refreshToken` ⇒ 必须在 query，塞进 JSON body 会直接 400
        // （同审计 §39 记的 `/send/confirm-station-action` 那条形态坑）
        val p = attempt { Api.post("$M/auth/refresh-token", query = mapOf("refreshToken" to refresh)) }
            .getOrNull() ?: return false
        // 演示构建里失败的请求会回落到 DemoData —— 那种"token"是编出来的，
        // 拿它续期等于给一个并不存在的会话续命（宁可就让用户重登）。
        if (p.demo) return false
        val token = p.str("accessToken").ifEmpty { p.str("token") }
        if (token.isEmpty()) return false
        Api.token = token
        Prefs.token = token
        // 后端会轮换 refreshToken；没给就沿用旧的，别把它清成空
        Prefs.refreshToken = p.str("refreshToken").ifEmpty { refresh }
        Prefs.expiresAt = p.longOf("expiresTime") ?: 0L
        return true
    }

    suspend fun login(mobile: String, password: String, role: Role): Account {
        val p = Api.post("$M/auth/login", mapOf("mobile" to mobile, "password" to password))
        return saveSession(p, role, mobile)
    }

    suspend fun smsLogin(mobile: String, code: String, role: Role): Account {
        val p = Api.post("$M/auth/sms-login", mapOf("mobile" to mobile, "code" to code))
        return saveSession(p, role, mobile)
    }

    /** 发码：后端没受理就抛异常（NetworkException / ApiException），不再一律返回 true 让页面假称"已发送" */
    suspend fun sendSmsCode(mobile: String, scene: Int = 1): Boolean {
        Api.post("$M/auth/send-sms-code", mapOf("mobile" to mobile, "scene" to scene))
        return true
    }

    /**
     * 会话落地：**只认后端回来的 accessToken**。
     * 后端 200 但没有 token 视为登录失败（抛错），绝不再造一个 demo token 让 App 进入"假登录"状态。
     */
    private fun saveSession(p: Payload, role: Role, mobile: String): Account {
        val token = p.str("accessToken").ifEmpty { p.str("token") }
        if (token.isEmpty()) {
            val reason = p.str("msg", "登录响应缺少 accessToken，请重试")
            throw ApiException(p.int("code", -1), reason)
        }
        val acc = Account.parse(p.obj ?: JSONObject(), token, role)
        val account = acc.copy(
            // 不再回填 10086：后端没给用户 id 就保持 0，由 /member/user/get 补全
            id = acc.id,
            mobile = acc.mobile.ifEmpty { mobile },
            nickname = acc.nickname,
        )
        // 换账号（不退出直接登录）必须先丢掉上一位的司机身份，否则新会话拿旧 driverId 发司机请求（审计 §29）
        Prefs.clearDriverBinding()
        Api.token = token
        Api.demoSession = p.demo
        Prefs.token = token
        // 会话续期用的两件套：后端一直在回，此前被直接丢弃 ⇒ 30 分钟到期只能重新登录（任务：登录态）。
        // expiresTime 只服务"临期主动续"，读不到也不影响正确性 —— 真正的兜底路径是 401 时静默续期重试。
        Prefs.refreshToken = p.str("refreshToken")
        Prefs.expiresAt = p.longOf("expiresTime") ?: 0L
        Prefs.userId = account.id
        Prefs.mobile = account.mobile
        Prefs.nickname = account.nickname
        Prefs.avatar = account.avatar
        Prefs.gender = account.gender
        Prefs.role = role
        return account
    }

    /** 补全资料：登录接口只回 token，昵称头像要另取 */
    suspend fun refreshUser(role: Role): One<Account> = try {
        val p = Api.get("$M/user/get")
        val acc = Account.parse(p.obj ?: JSONObject(), Prefs.token, role)
        val merged = acc.copy(
            id = if (acc.id > 0) acc.id else Prefs.userId,
            mobile = acc.mobile.ifEmpty { Prefs.mobile },
            nickname = acc.nickname.ifEmpty { Prefs.nickname },
        )
        Prefs.userId = merged.id
        Prefs.mobile = merged.mobile
        Prefs.nickname = merged.nickname
        Prefs.avatar = merged.avatar
        Prefs.gender = merged.gender
        One(merged, p.demo)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        One(null, error = errorTextOf(e))
    }

    /**
     * 司机身份绑定：司机端所有接口都吃 driverId，登录后取一次档案。
     * driverId 只能来自后端；未绑定就是未绑定（返回空档案 + 提示），不再造 7001 让司机端"看起来能用"。
     */
    suspend fun bindDriver(): One<DriverProfile> = try {
        val p = Api.get("$T/driver/profile")
        val o = p.obj ?: JSONObject()
        val profile = DriverProfile.parse(o)
        if (profile.driverId > 0) {
            Prefs.driverId = profile.driverId
            Prefs.driverName = profile.name
            Prefs.vehiclePlate = profile.plate
            One(profile, p.demo)
        } else {
            // 后端明确回了"没有司机档案"（`data:null` 或没有 driverId）：本机不能留着上一个账号的 driverId 继续发司机请求
            if (!p.demo) Prefs.clearDriverBinding()
            One(null, p.demo, error = if (p.demo) "演示模式：无司机档案" else "该账号未绑定司机档案，请联系站点核对工号")
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        One(null, error = errorTextOf(e))
    }

    /** 退出登录：先尽力通知后端撤销会话，再无条件清本地。清不掉就是"没退出"，下一个人会看到上一个人的数据 */
    suspend fun logout() {
        // F-2 第 8 步迁点：**哪一类失败**要可见（401 / 业务码 / 传输层三种以前都落进同一个静默分支），
        // 但清本地照旧**无条件**执行——会话卫生优先于分类，反过来就是把登录态留在设备上。
        val result = safeApiCall { Api.post("$M/auth/logout") }
        Api.token = null
        Api.demoSession = false
        Prefs.clearSession()
        if (result !is ApiResult.Success) {
            android.util.Log.w("AUTH_LOGOUT", "后端未确认撤销会话：$result")
        }
    }

    /** 401 / token 失效的统一出口：与 logout 同一套清理，但不打后端接口（后端已经认这个会话无效了） */
    fun clearSessionLocal() {
        Api.token = null
        Api.demoSession = false
        Prefs.clearSession()
    }

    // ---------- 首页：公告 / 天气 ----------

    suspend fun notices(): Feed<Notice> = try {
        val p = Api.get("$T/notice/list")
        Feed(p.list.map { Notice.parse(it) }, p.demo)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Feed(emptyList(), error = errorTextOf(e))
    }

    /**
     * 逆地理编码（高德 regeo，Web 服务 key）：坐标 → adcode / 区县 / 乡镇。
     * 天气按 adcode 查区县级更准（中国气象局数据），首页定位文案也用它。
     * 失败返回 EMPTY，调用方回落到无 adcode 的坐标级天气（open-meteo）。
     */
    suspend fun reverseGeo(geo: Geo): ReverseGeo {
        val key = com.shanxiang.buspost.BuildConfig.AMAP_WEB_KEY
        if (key.isEmpty()) return ReverseGeo.EMPTY
        return try {
            val text = Api.rawGet(
                "https://restapi.amap.com/v3/geocode/regeo?key=$key" +
                    "&location=${geo.lng},${geo.lat}&extensions=base"
            )
            val root = JSONObject(text)
            if (root.optString("status") != "1") return ReverseGeo.EMPTY
            val rc = root.optJSONObject("regeocode") ?: return ReverseGeo.EMPTY
            val ac = rc.optJSONObject("addressComponent") ?: return ReverseGeo.EMPTY
            ReverseGeo(
                adcode = ac.multiStr("adcode"),
                province = ac.multiStr("province"),
                // 直辖市下 city 是空数组，multiStr 已跳过数组 → 得到空串而非 "[]"
                city = ac.multiStr("city"),
                district = ac.multiStr("district"),
                township = ac.multiStr("township"),
                formatted = rc.multiStr("formatted_address"),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ReverseGeo.EMPTY
        }
    }

    /**
     * 正向地理编码（高德 geo，Web 服务 key）：地址 → 坐标。
     * 用于"手动地址查公交"功能：用户输入地址，得到坐标后查附近公交。
     * 失败返回 null。
     */
    data class GeoResult(val geo: Geo, val formatted: String, val adcode: String)

    suspend fun forwardGeo(address: String, city: String = ""): GeoResult? {
        if (address.isBlank()) return null
        val key = com.shanxiang.buspost.BuildConfig.AMAP_WEB_KEY
        if (key.isEmpty()) return null
        return try {
            val cityParam = if (city.isNotEmpty()) "&city=$city" else ""
            val encoded = java.net.URLEncoder.encode(address, "UTF-8")
            val text = Api.rawGet(
                "https://restapi.amap.com/v3/geocode/geo?key=$key$cityParam&address=$encoded"
            )
            val root = JSONObject(text)
            if (root.optString("status") != "1") return null
            val geocodes = root.optJSONArray("geocodes") ?: return null
            if (geocodes.length() == 0) return null
            val first = geocodes.getJSONObject(0)
            val location = first.optString("location", "")
            if (location.isEmpty() || !location.contains(",")) return null
            val parts = location.split(",")
            if (parts.size != 2) return null
            val lng = parts[0].toDoubleOrNull() ?: return null
            val lat = parts[1].toDoubleOrNull() ?: return null
            GeoResult(
                geo = Geo(lat, lng),
                formatted = first.optString("formatted_address", address),
                adcode = first.optString("adcode", ""),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 天气。数据源优先级与小程序 utils/weather.js 一致：
     *   1) 高德天气（中国气象局，区县级精度，需 key；extensions=base 实况 + all 预报）
     *   2) open-meteo（国际模式，坐标级，无 key）
     *   3) 两路都没给 → 空态（mock=true），页面只说"未取到"，不再编一组温度与 4 日预报
     * 高德 key 若为「微信小程序」类型会返回 10009 平台不匹配，此处静默降级，不阻塞页面。
     */
    suspend fun weather(geo: Geo, adcode: String = ""): WeatherInfo {
        amapWeather(adcode)?.let { return it }
        openMeteoWeather(geo)?.let { return it }
        // 旧兜底发的是「多云 22°、湿度 68%、4 日预报」——一个数都没来自后端/服务商，纯本机编的，
        // 挂在首屏就是拿假天气给司机和用户做装车决策。现在只回结构空值 + mock=true，
        // 页面据此出「天气未取到」；温度/湿度/预报一律不渲染（§14 没有值不许有业务语义）。
        return WeatherInfo(
            text = "", temperature = 0, feel = 0, wind = "",
            source = "未取到", icon = "", humidity = "", tips = "",
            forecast = emptyList(), mock = true,
        )
    }

    private suspend fun amapWeather(adcode: String): WeatherInfo? {
        if (adcode.isEmpty()) return null
        // 天气走「Web 服务」key（REST 接口），与地图 SDK 的「Android 平台」key 是两个不同的 key；
        // 实测用平台受限 key 请求 REST 会返回 10009 USERKEY_PLAT_NOMATCH。
        val key = com.shanxiang.buspost.BuildConfig.AMAP_WEB_KEY
        if (key.isEmpty()) return null
        return try {
            val base = "https://restapi.amap.com/v3/weather/weatherInfo?key=$key&city=$adcode"
            val live = JSONObject(Api.rawGet("$base&extensions=base"))
            if (live.optString("status") != "1") return null
            val l = live.optJSONArray("lives")?.optJSONObject(0) ?: return null
            val forecast = safeApiCall {
                val all = JSONObject(Api.rawGet("$base&extensions=all"))
                all.optJSONArray("forecasts")?.optJSONObject(0)?.optJSONArray("casts")?.let { casts ->
                    (0 until casts.length()).mapNotNull { i ->
                        val c = casts.optJSONObject(i) ?: return@mapNotNull null
                        WeatherDay(
                            day = c.optString("date").takeLast(5),
                            icon = c.optString("dayweather"),
                            tempHi = c.optString("daytemp").toIntOrNull() ?: 0,
                            tempLo = c.optString("nighttemp").toIntOrNull() ?: 0,
                            condition = c.optString("dayweather"),
                        )
                    }
                }.orEmpty()
            // F-2 第 8 步迁点（形状归一，语义未变）：预报是高德的**可选装饰**，取不到就是没有这七天，
            // 界面已经明写来源"高德天气"，所以这里保持 orEmpty()，不新开一条错误通路。
            }.getOrNull().orEmpty()
            val temp = l.optString("temperature").toIntOrNull() ?: 0
            val (userTip, driverTip) = weatherTipsFor(l.optString("weather"), temp)
            WeatherInfo(
                text = l.optString("weather"),
                temperature = temp,
                feel = temp,
                wind = "风 ${l.optString("winddirection")}${l.optString("windpower")}",
                source = "高德天气",
                icon = l.optString("weather"),
                humidity = l.optString("humidity").let { if (it.isEmpty()) "" else "湿度 $it%" },
                tips = userTip,
                driverTips = driverTip,
                forecast = forecast,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun openMeteoWeather(geo: Geo): WeatherInfo? = try {
        val text = Api.rawGet(
            "https://api.open-meteo.com/v1/forecast?latitude=${geo.lat}&longitude=${geo.lng}" +
                "&current_weather=true&daily=weathercode,temperature_2m_max,temperature_2m_min&timezone=Asia%2FShanghai"
        )
        val root = JSONObject(text)
        val cw = root.optJSONObject("current_weather") ?: throw Exception("无天气数据")
        val daily = root.optJSONObject("daily")
        // attempt 而非 runCatching：这一段在 suspend 作用域里，块内一旦新增挂起点，
        // runCatching 会把"页面已离开"的 CancellationException 收成一次解析失败，回落成"没有预报"。
        val forecast = attempt {
            val codes = daily?.optJSONArray("weathercode")
            val days = daily?.optJSONArray("time")
            val highs = daily?.optJSONArray("temperature_2m_max")
            val lows = daily?.optJSONArray("temperature_2m_min")
            val n = minOf(codes?.length() ?: 0, 4)
            (0 until n).map { i ->
                WeatherDay(
                    day = days?.optString(i).orEmpty().takeLast(5),
                    icon = weatherText(codes?.optInt(i) ?: 0),
                    tempHi = highs?.optDouble(i, 0.0)?.toInt() ?: 0,
                    tempLo = lows?.optDouble(i, 0.0)?.toInt() ?: 0,
                    condition = weatherText(codes?.optInt(i) ?: 0),
                )
            }
        }.getOrDefault(emptyList())
        val temp = cw.optDouble("temperature", 0.0).toInt()
        val cond = weatherText(cw.optInt("weathercode"))
        val (userTip, driverTip) = weatherTipsFor(cond, temp)
        WeatherInfo(
            text = cond,
            temperature = temp,
            feel = temp,
            wind = "风 ${cw.optDouble("windspeed", 0.0).toInt()} km/h",
            source = "open-meteo",
            icon = cond,
            tips = userTip,
            driverTips = driverTip,
            forecast = forecast,
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private fun weatherText(code: Int): String = when (code) {
        0 -> "晴"; 1, 2 -> "多云"; 3 -> "阴"
        in 45..48 -> "雾"
        in 51..67 -> "毛毛雨"
        in 71..77 -> "雪"
        in 80..95 -> "阵雨"
        else -> "恶劣天气"
    }

    // ---------- 商城 ----------

    suspend fun products(): Feed<Product> = try {
        val p = Api.get("$T/product/list")
        val items = p.list.map { Product.parse(it) }
        if (items.isEmpty()) productsPage() else Feed(items, p.demo)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Feed(emptyList(), error = errorTextOf(e))
    }

    suspend fun productsPage(pageNo: Int = 1, pageSize: Int = 20): Feed<Product> = try {
        val p = Api.get("$T/product/page", mapOf("pageNo" to pageNo, "pageSize" to pageSize))
        Feed(p.list.map { Product.parse(it) }, p.demo, total = p.total)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Feed(emptyList(), error = errorTextOf(e))
    }

    suspend fun product(id: Long): One<Product> = try {
        val p = Api.get("$T/product/get", mapOf("id" to id))
        val o = p.obj ?: JSONObject()
        val product = Product.parse(if (o.length() > 0) o else p.list.firstOrNull() ?: JSONObject())
        One(product, p.demo)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        One(null, error = errorTextOf(e))
    }

    /**
     * 用户端"创建订单"的本地幂等闸（F-2 第 6 步 · v2.1 清单第 6 项；V-1 那套 `ActionGate` 的**第二个实例**）。
     *
     * 为什么这里与司机侧**是两个实例而不是一个**：硬规则 1 要求用户域与司机域分开，共用一个键表的话
     * 一次下单的 10 秒保留窗会去挡司机动作（不同业务目标互相阻塞），域就破了。
     *
     * 为什么键要按"端点 + 登录身份 + 整份报文"摘要建 —— 这三个维度**不是本机发明的**，是抄后端的：
     * 这两个接口本来就带 `@Idempotent(timeout = 10, timeUnit = SECONDS, keyResolver = UserIdempotentKeyResolver)`
     * （`AppProductOrderController.java:38` / `AppSendController.java:120`），而该解析器的键是
     * `MD5(方法签名 + 方法参数 + userId + userType)`（`UserIdempotentKeyResolver.java:21-25`）。
     * ⇒ 后端判"重复"的口径就是**同一个人 + 同一份报文**：本机按同一口径建键，才能同时做到
     * "后端必然回 900 的那几次，出网之前就拦掉"与"后端不会拦的（改了数量或地址），本机也不许拦"。
     *
     * ⚠ 两条与后端对齐的行为边界（都是读过后端代码才写的，不是假设）：
     * · `deleteKeyWhenException = true` 是注解默认值（`Idempotent.java:61`，两处都没覆盖），
     *   后端在业务异常时**删键**、允许立刻重试 ⇒ 本机也必须异常即释放（`ActionGate` 本来就是这个语义），
     *   否则一次"货物超出限重"就把这个用户 10 秒内钉死，而后端其实随时收；
     * · 同一份报文在 10 秒内成功过一次，后端**一定**回 `ServiceException(900, "订单正在创建中，请勿重复提交")`
     *   （`IdempotentAspect.java:50-53` + `GlobalErrorCodeConstants.java:36`）⇒ 本机不是发明去重，
     *   是把那几次必然的 900 挪到出网之前，并且**如实标 `notSent`**（请求真没发出去）。
     */
    private val userCreateGate = ActionGate()

    /**
     * 键只存**摘要**，不存明文：`MessageDigest` 走 SHA-256，收货人姓名/手机号/地址不会以可读形式
     * 留在进程级的键表里（闸门是 `object Repo` 上的进程级状态，活得比任何一个页面都久）。
     *
     * `internal` 不是给业务用的：契约测试要能直接断言"键里有端点、有登录身份、有整份报文"这三个维度，
     * 走公网调用去反推键维度只能用例多、结论少（同 `DriverActionIdempotencyContractTest` 的取舍）。
     */
    internal fun userCreateKey(path: String, body: Map<String, Any?>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(path.toByteArray(Charsets.UTF_8))
        digest.update(0)
        digest.update(Prefs.mobile.toByteArray(Charsets.UTF_8))
        digest.update(0)
        body.entries.sortedBy { it.key }.forEach { (key, value) ->
            digest.update(key.toByteArray(Charsets.UTF_8))
            digest.update(0)
            // 1/2 两个起始字节把"值为 null"与"值为空串"分开：否则清空收件人地址与不填地址会是同一个键
            if (value == null) digest.update(1) else {
                digest.update(2)
                digest.update(value.toString().toByteArray(Charsets.UTF_8))
            }
            digest.update(0)
        }
        // MessageDigest.digest() 是"完成并复位"，第二次再调拿到的是空输入的哈希 ⇒ 只调一次
        val bytes = digest.digest()
        val hex = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val i = b.toInt() and 0xFF
            hex.append(HEX_DIGITS[i shr 4]).append(HEX_DIGITS[i and 0xF])
        }
        return "create:$hex"
    }

    private suspend fun gatedUserCreate(path: String, body: Map<String, Any?>, request: suspend () -> Ack): Ack =
        when (val r = userCreateGate.submit(userCreateKey(path, body), request)) {
            is GateResult.Sent -> r.value
            is GateResult.Duplicate -> Ack(demo = false, notSent = true)
        }

    /**
     * 创建商城订单（货到付款）。请求体与小程序 goods/detail/detail.js:183-191 **逐字段一致**：
     * {productId, quantity, receiverName, receiverMobile, receiverAddress, remark, userMobile}
     */
    suspend fun createProductOrder(
        productId: Long,
        quantity: Int,
        receiverName: String,
        receiverMobile: String,
        receiverAddress: String,
        remark: String,
    ): Ack {
        val path = "$T/product-order/create"
        val body = mapOf(
            "productId" to productId,
            "quantity" to quantity,
            "receiverName" to receiverName,
            "receiverMobile" to receiverMobile,
            "receiverAddress" to receiverAddress,
            "remark" to remark,
            "userMobile" to Prefs.mobile,
        )
        return gatedUserCreate(path, body) {
            val p = Api.post(path, body)
            val o = p.obj ?: JSONObject()
            Ack(p.demo, orderNo = o.multiStr("orderNo")) // 回执 VO 只有 id 与 orderNo（AppProductOrderCreateRespVO）：amount/totalPrice/no 三面 0 生产者，不许在这里塌成一个"后端给的 0.0"
        }
    }

    suspend fun productOrders(pageNo: Int = 1, pageSize: Int = 20, status: Int? = null): Feed<ProductOrder> = try {
        val p = Api.get("$T/product-order/page", mapOf("pageNo" to pageNo, "pageSize" to pageSize, "status" to status))
        Feed(p.list.map { ProductOrder.parse(it) }, p.demo, total = p.total)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Feed(emptyList(), error = errorTextOf(e))
    }

    suspend fun cancelProductOrder(id: Long): Ack =
        Ack(Api.put("$T/product-order/cancel", emptyMap(), mapOf("id" to id)).demo)

    suspend fun productOrderTrace(id: Long): One<ParcelTrace> = try {
        val p = Api.get("$T/product-order/trace", mapOf("id" to id))
        // 商城轨迹的 status 是 `ProductOrderStatusEnum`（0..3），与寄货单 0..13 是两套码：
        // 不标来源，`ParcelTrace` 就会拿货运表去解释商城的 3（"已取消" vs "已发车"）。
        One(traceOf(p, fromProductTrace = true), p.demo)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        One(null, error = errorTextOf(e))
    }

    // ---------- 寄货 / 包裹 ----------

    suspend fun sendOrders(pageNo: Int = 1, pageSize: Int = 20, status: Int? = null): Feed<SendOrder> = try {
        val p = Api.get("$T/send/page", mapOf("pageNo" to pageNo, "pageSize" to pageSize, "status" to status))
        Feed(p.list.map { SendOrder.parse(it) }, p.demo, total = p.total)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // cause 供后台轮询按异常**类型**定重试/失败（F-2 ⑩）；页面仍读 error 文案。
        Feed(emptyList(), error = errorTextOf(e), cause = e)
    }

    suspend fun createSendOrder(form: Map<String, Any?>): Ack {
        val path = "$T/send/create"
        return gatedUserCreate(path, form) {
            val p = Api.post(path, form)
            val o = p.obj ?: JSONObject()
            Ack(
                demo = p.demo,
                orderNo = o.multiStr("orderNo"),
                pickupCode = o.multiStr("pickupCode"),
                amount = o.multiNum("totalAmount"),
                reviewStatus = o.multiNum("reviewStatus").toInt(),
                reviewReasonCodes = o.multiStr("reviewReasonCodes", "reviewReason"),
                servicePointStationName = o.multiStr("servicePointStationName"),
                servicePointDistanceKm = o.multiNum("servicePointDistanceKm"),
                servicePointLatitude = o.multiNum("servicePointLatitude"),
                servicePointLongitude = o.multiNum("servicePointLongitude"),
            )
        }
    }

    suspend fun trackParcel(no: String): One<ParcelTrace> = try {
        val p = Api.get("$T/send/track", mapOf("no" to no))
        One(traceOf(p, no), p.demo)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        One(null, error = errorTextOf(e))
    }

    suspend fun parcelLegs(no: String): Feed<TransportLeg> = try {
        val p = Api.get("$T/send/legs", mapOf("no" to no))
        Feed(p.list.map { TransportLeg.parse(it) }, p.demo)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Feed(emptyList(), error = errorTextOf(e))
    }

    suspend fun sendStations(): Feed<Station> = try {
        val p = Api.get("$T/send/stations")
        Feed(p.list.map { Station.parse(it) }, p.demo)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Feed(emptyList(), error = errorTextOf(e))
    }

    /** 寄货试算：响应字段与小程序 send.wxml:282-287 一致（amount/itemFee/distanceFee/itemCount/distanceKm），缺键由模型侧呈现为"未给" */
    suspend fun quoteSendFee(pickupStationId: Long, deliveryStationId: Long, itemCount: Int): One<QuoteResult> = try {
        val p = Api.post(
            "$T/send/quote",
            mapOf("pickupStationId" to pickupStationId, "deliveryStationId" to deliveryStationId, "itemCount" to itemCount),
        )
        One(QuoteResult.parse(p.obj ?: JSONObject()), p.demo)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        One(null, error = errorTextOf(e))
    }

    /**
     * 当前位置可达性（审计 §34/§11）。请求体按 `AppSendReachabilityReqVO`：`latitude`/`longitude` 都 @NotNull，
     * 解析一律走 [Reachability.parse] —— 三态（REACHABLE / NOT_REACHABLE / UNKNOWN）由 `reachable` 键在不在决定，
     * 这里不再用 `multiBool` 把"接口没给"塌成 false 后由界面编一条不可达原因。
     * 失败返回 `One(null, error)`，页面按 UNKNOWN 显示，不给"可以运输"的口径。
     */
    suspend fun reachability(geo: Geo): One<Reachability> = try {
        val p = Api.post("$T/send/reachability", mapOf("latitude" to geo.lat, "longitude" to geo.lng))
        One(Reachability.parse(p.obj ?: JSONObject()), p.demo)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        One(null, error = errorTextOf(e))
    }

    /** 路线预估：响应字段照 `RoutePreviewRespVO`（available/distanceKm/durationMinutes/provider/warning） */
    suspend fun routePreview(pickupStationId: Long, deliveryStationId: Long): One<RoutePreview> = try {
        val p = Api.post("$T/send/route-preview", mapOf("pickupStationId" to pickupStationId, "deliveryStationId" to deliveryStationId))
        One(
            // 键名候选收回 RoutePreview.parse：这里曾经写 ("distanceKm","distance")/("durationMinutes","minutes")，
            // 后两个别名后端根本不发，而 distance 在别的契约里是米（§72）
            RoutePreview.parse(p.obj ?: JSONObject()),
            p.demo,
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        One(null, error = errorTextOf(e))
    }

    suspend fun myArrangements(): Feed<Arrangement> = try {
        val p = Api.get("$T/send/arrangements")
        Feed(p.list.map { Arrangement.parse(it) }, p.demo)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Feed(emptyList(), error = errorTextOf(e))
    }

    /**
     * 钱包余额（`GET /app-api/transport/wallet/balance`，`AppWalletController:32-43`）。只读。
     * 失败返回 `One(null, error)` —— 页面那一格必须能说"没取到"，不许退回显示 ¥0.00。
     */
    suspend fun walletBalance(): One<WalletBalance> = try {
        val p = Api.get("$T/wallet/balance")
        One(WalletBalance.parse(p.obj ?: JSONObject()), p.demo)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        One(null, error = errorTextOf(e))
    }

    /**
     * 钱包流水（`GET /app-api/transport/wallet/transactions`，`AppWalletController:81-103`）。只读。
     *
     * ⚠ 刻意**不接**充值与余额支付两个写口（`/recharge:65-79`、`/pay:105-126`），理由记在台账 D-086：
     * · `/recharge` 无幂等键（`V028:39` 的唯一索引只盖住流水号，不盖住请求），重复点击会**重复加余额**；
     * · 它写入的流水与真实到账在字段上不可区分（渠道被写死 `"alipay"`、状态写死成功，
     *   `WalletServiceImpl:53-58`；表里没有"来源/是否模拟"列）⇒ 界面无法如实标注；
     * · `query-pay-order:59-63` 只按 orderNo 查、不校验归属人（`PayService:182`），
     *   且在 `status=0` 时会触发放款（`:190-200`）——这是别人的单子也能被查到并推进的那一格。
     * 展示侧只读这两条通路，写口等后端补上幂等键与来源列之后再接。
     */
    suspend fun walletTransactions(limit: Int = 20): One<WalletTxPage> = try {
        val p = Api.get("$T/wallet/transactions", mapOf("limit" to limit.toString()))
        One(WalletTxPage.parse(p.obj ?: JSONObject()), p.demo)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        One(null, error = errorTextOf(e))
    }

    /**
     * 客户确认"已按替代交接送到指定站点"。
     * 后端 `AppSendController:129` 是 `@RequestParam("orderId")`，参数必须在 query ——
     * 旧写法放进 JSON body 会稳定 400（审计 §39），而且失败被吞成演示回执，没人发现。
     */
    suspend fun confirmStationAction(orderId: Long): Ack =
        Ack(Api.post("$T/send/confirm-station-action", emptyMap(), mapOf("orderId" to orderId)).demo)

    /**
     * 轨迹/拓扑统一解析器。同一套字段口径同时服务：
     *   GET /transport/product-order/trace（商城订单溯源）
     *   GET /transport/send/track        （寄货包裹追踪）
     *   GET /transport/send/topology     （多段联运：分段 + 换乘交接 + 候选方案 + 时间线）
     * 字段名对齐小程序 trace.js / parcel.js 的实际读取口径。
     */
    private fun traceOf(p: Payload, fallbackNo: String = "", fromProductTrace: Boolean = false): ParcelTrace {
        val o = p.obj ?: JSONObject()
        val geo = geoOf(o)
        val track = geoListOf(o.multiArr("track", "polyline", "points"))
        val stations = o.multiArr("stations", "stops").map { Station.parse(it) }
        // points 是「途经站点（含序号/计划分钟）」的独立口径；缺时回落到 stations
        val rawPoints = o.multiArr("points", "viaStations", "routePoints")
        val points = if (rawPoints.isNotEmpty() && rawPoints.first().opt("latitude") != null) {
            rawPoints.map { Station.parse(it) }
        } else {
            stations
        }
        val nodes = o.multiArr("nodes", "timeline", "records").map {
            TraceNode(
                // `eventTypeName`/`detail` 是拓扑信封 `Timeline:129-137` 的真键；原来那四个键
                // （label/title/statusLabel/nodeName 与 hint/description/stationName/remark）在这条链路上一个生产者都没有
                // ⇒ `/send/topology` 的时间线整列念空（D-085）。放在最前面，另外两条链路的读法不受影响。
                label = it.multiStr("eventTypeName", "label", "title", "statusLabel", "nodeName"),
                // 后端把 LocalDateTime 注册成 EpochMillis（`Models.kt:104-107` 那条取证），所以这里必须先过 `ts()`
                // 归一再取 HH:mm；旧写法直接 `substringAfter(' ').take(5)` 会把 13 位毫秒串切出 "17590"。
                time = it.nodeTimeOf("eventTime", "time", "createTime", "nodeTime"),
                hint = it.multiStr("detail", "hint", "description", "stationName", "remark"),
                // 轨迹节点完成态的真键只有 `done`（小程序 11 处、DemoData 6 处都在写它）；
                // `finished` 三方 0 命中，`completed` 只在后端 bigscreen 的 admin 汇总里出现过（BigScreenServiceImpl:120），
                // 都不在这个报文里 ⇒ 撤掉，避免"节点状态"长出第二个名字。
                state = stopStateOf(it.multiBool("done"), it.multiBool("current")),
            )
        }
        return ParcelTrace(
            orderNo = o.multiStr("orderNo", "bizOrderNo", "no").ifEmpty { fallbackNo },
            // 缺 status 键 = -1（未知），不能落成 0：0 在寄货侧是"已创建"、在商城侧是"待发货"，
            // 猜错就等于给用户看一个不存在的业务状态（§14 禁止 null→0 当业务语义）。
            status = if (o.has("status") || o.has("orderStatus")) o.multiNum("status", "orderStatus").toInt() else -1,
            fromProductTrace = fromProductTrace,
            goodsName = o.multiStr("goodsName", "cargoName"),
            nodes = nodes,
            legs = o.multiArr("legs", "segments").map { TransportLeg.parse(it) },
            vehiclePlate = o.multiStr("vehiclePlate", "plate"),
            driverName = o.multiStr("driverName"),
            driverMobile = o.multiStr("driverMobile", "driverPhone"),
            targetStation = o.multiStr("targetStation", "deliveryStation", "stationName"),
            etaMinutes = o.multiNumOrNull("etaMinutes")?.toInt(),
            // "positionSource" 曾在这里当候选键，但两仓（后端 VO + DemoData）都没有生产者，属于自造别名，已删。
            dataSource = o.multiStr("carrierLocationSource", "dataSource"),
            vehicleGeo = geo,
            locationSource = LocationSource.of(o.multiStr("carrierLocationSource", "locationSource", "dataSource")),
            polyline = track,
            stations = stations,
            productName = o.multiStr("productName"),
            // `orderStatusName` 是拓扑信封的真键（`OrderTopologyRespVO:25`）；放在最前，另外两条链路的 `statusName` 不受影响。
            statusName = o.multiStr("orderStatusName", "statusName"),
            // 起讫站：拓扑给的是 `originStationName`/`destinationStationName`（`:27`/`:29`），
            // 查不到站点档案时后端就是 null ⇒ 下面票面那一格念"后端没给"，不兜一个站名。
            startStation = o.multiStr("originStationName", "startStation", "fromStation", "pickupStation"),
            endStation = o.multiStr("destinationStationName", "endStation", "toStation"),
            routeName = o.multiStr("routeName", "lineName"),
            shiftCode = o.multiStr("shiftCode"),
            receiverName = o.multiStr("receiverName"),
            receiverMobile = o.multiStr("receiverMobile", "receiverPhone"),
            receiverAddress = o.multiStr("receiverAddress"),
            lastReportTime = o.reportTimeOf("lastReportTime", "reportTime", "gpsTime"),
            carrierTaskStatus = o.multiStr("carrierTaskStatus"),
            carrierTaskTime = o.reportTimeOf("carrierArrivedTime"),
            loadTime = o.timeOf("loadTime", "loadedTime"),
            deliverTime = o.timeOf("deliverTime", "deliveredTime"),
            // carrierArrived 是寄货侧的真实键（AppSendController:403），driverArrived 是商城侧的（AppProductOrderTraceRespVO:46）：
            // ParcelTrace 两条链路共用，只读后者会让寄货单永远显示"司机未到达"。
            driverArrived = o.multiBool("carrierArrived", "driverArrived", "arrived"),
            loadPhotoUrl = o.multiStr("loadPhotoUrl", "loadPhoto"),
            deliverPhotoUrl = o.multiStr("deliverPhotoUrl", "deliverPhoto"),
            points = points,
            handovers = o.multiArr("handovers", "transfers").map { LegHandover.parse(it) },
            plans = o.multiArr("plans", "candidates", "schemes").map { TransitPlan.parse(it) },
            // ---- 拓扑信封（OrderTopologyRespVO:32-46）：只有 /send/topology 会发，其余两条链路读到默认值 ----
            planningMode = o.multiStr("planningMode"),
            planningModeName = o.multiStr("planningModeName"),
            planNo = o.multiStr("planNo"),
            totalLegs = o.multiNumOrNull("totalLegs")?.toInt(),
            transferCount = o.multiNumOrNull("transferCount")?.toInt(),
            totalDistanceKm = o.multiNumOrNull("totalDistanceKm"),
            totalDurationMinutes = o.multiNumOrNull("totalDurationMinutes")?.toInt(),
            planReason = o.multiStr("planReason"),
        )
    }

    /** 用户端：按订单号查运输拓扑（分段 + 换乘交接 + 方案解释 + 时间线，一次返回） */
    suspend fun parcelTopology(no: String): One<ParcelTrace> = try {
        val p = Api.get("$T/send/topology", mapOf("no" to no))
        One(traceOf(p, no), p.demo)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        One(null, error = errorTextOf(e))
    }

    // ---------- 实时公交 ----------

    /**
     * 附近公交搜索半径（米）：按定位精度自适应，与小程序 utils/location.js:377-385 同规则。
     * accuracy<=100m → 5000；100~500m → 8000；>500m 或未知 → 15000。
     */
    fun nearbyRadius(accuracyMeters: Double): Double = when {
        accuracyMeters <= 0.0 -> 15000.0
        accuracyMeters <= 100.0 -> 5000.0
        accuracyMeters <= 500.0 -> 8000.0
        else -> 15000.0
    }

    suspend fun busLines(geo: Geo?, radiusMeters: Double = 15000.0): Feed<BusLine> = try {
        val p = Api.get(
            "$T/bus/lines",
            mapOf("latitude" to geo?.lat, "longitude" to geo?.lng, "radius" to radiusMeters),
        )
        Feed(p.list.map { BusLine.parse(it) }, p.demo)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Feed(emptyList(), error = errorTextOf(e))
    }

    suspend fun nearbyBuses(geo: Geo?, radiusMeters: Double = 15000.0, district: String = ""): One<NearbyResult> = try {
        val p = Api.get(
            "$T/bus/nearby",
            mapOf("latitude" to geo?.lat, "longitude" to geo?.lng, "radius" to radiusMeters, "district" to district),
        )
        val o = p.obj ?: JSONObject()
        val list = p.list
        val result = if (list.isNotEmpty() && o.length() == 0) {
            val lines = list.map { BusLine.parse(it) }
            NearbyResult(
                stations = lines.flatMap { it.stations },
                lines = lines,
                vehicles = lines.flatMap { it.vehicles },
                realTransit = lines.any { l -> l.vehicles.any { it.isReal } || l.dataSource.contains("REAL") },
                lineCount = lines.size,
            )
        } else {
            val lines = o.multiArr("lines", "routes").map { BusLine.parse(it) }
            // 真实接口（实测 2026-09-28）返回 stations 键为 nearbyStations，另有单数 nearestStation；
            // 早期实现只认 stations/stops，会导致"附近站点"永远为空 —— 三种键都认。
            val stations = o.multiArr("nearbyStations", "stations", "stops").map { Station.parse(it) }
            val nearest = o.optJSONObject("nearestStation")?.let { Station.parse(it) }
            NearbyResult(
                stations = if (nearest != null && stations.none { it.name == nearest.name }) {
                    listOf(nearest) + stations
                } else {
                    stations
                },
                lines = lines,
                vehicles = o.multiArr("vehicles", "buses").map { BusVehicle.parse(it) },
                // 真实键只有 realTransitAvailable（AppBusNearbyRespVO:50 + AppBusServiceImpl:276/:287 + 小程序 index.js:553 + 演示面都用它）；
                // 裸 realTransit 三面都无生产者，按 §二 同一条规则删除。
                realTransit = o.multiBool("realTransitAvailable"),
                inService = o.multiBoolOrNull("inService"),
                serviceWindowText = o.multiStr("serviceWindowText", "serviceWindow"),
                nextDepartureTime = o.multiStr("nextDepartureTime"),
                lineCount = o.multiNum("lineCount").toInt().let { if (it > 0) it else lines.size },
                locationLevel = o.multiStr("locationLevel"),
                runningEmptyText = o.multiStr("runningEmptyText", "emptyText"),
                transitProvider = o.multiStr("transitProvider", "provider"),
                realStationCount = o.multiNum("realStationCount").toInt(),
                projectStationCount = o.multiNum("projectStationCount").toInt(),
                located = o.multiBoolOrNull("located"),
            )
        }
        One(result, p.demo)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        One(null, error = errorTextOf(e))
    }

    /**
     * 线路真实道路轨迹。**"这次没问到"与"后端没有轨迹"是两件事**（F-2 第 8 步的修复点）：
     * 旧写法 `attempt { … }.getOrNull() ?: return emptyList()` 把网络失败、业务失败、401 全洗成空列表，
     * 于是地图那句"后端未回传该线路折线"在**请求根本没成功**时也是句假话。
     * 现在失败原样抛出（`getOrThrow()` 回抛同一个类型，分类不退化），由调用方 `BusScreen.loadPolyline()`
     * 自己记"这一屏这次没问到"；只有请求成功而报文里没有几何点才返回空列表。
     * 无论哪一种，都**不拿站点连线顶替**（§24 / 审计 §36 ②③）。
     */
    suspend fun linePolyline(routeId: String): List<Geo> {
        val p = safeApiCall { Api.get("$T/bus/line-polyline", mapOf("routeId" to routeId)) }.getOrThrow()
        val o = p.obj ?: JSONObject()
        return geoListOf(o.multiArr("points", "polyline", "track").ifEmpty { p.list })
    }

    // ---------- 消息中心 ----------

    suspend fun notifications(pageNo: Int = 1, pageSize: Int = 20, readStatus: Int? = null): Feed<NotifyMsg> = try {
        val p = Api.get("$T/notification/page", mapOf("pageNo" to pageNo, "pageSize" to pageSize, "readStatus" to readStatus))
        Feed(p.list.map { NotifyMsg.parse(it) }, p.demo, total = p.total)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Feed(emptyList(), error = errorTextOf(e), cause = e)
    }

    /**
     * 我的未读消息数。后端 `AppNotificationController.java:44-48` 是 `CommonResult<Long>`——**裸数字**，
     * 不是 `{"count": …}` 对象（审计 §22）。返回 null 表示"这次没读到数"（异常/键缺失），
     * 调用方必须把 null 和 0 分开：0 是后端说"确实没有未读"，null 不许抹掉已有的角标（审计 §52）。
     *
     * F-2 ⑧ 换成 [safeApiCall]：`longOf` 本身不抛（缺键就是 null），块内可能抛的只有 Api 那三类，
     * 所以这一处的**对外行为与旧的 `attempt` 完全一致**，换的是形状不是语义——台账不许把它记成修了缺陷。
     */
    suspend fun unreadCount(): Long? = safeApiCall {
        Api.get("$T/notification/unread-count").longOf("count", "unreadCount")
    }.getOrNull()

    suspend fun readNotification(id: Long): Ack = Ack(Api.put("$T/notification/read", emptyMap(), mapOf("id" to id)).demo)

    suspend fun readAllNotifications(orderId: Long? = null): Ack =
        Ack(Api.put("$T/notification/read-all", emptyMap(), mapOf("orderId" to orderId)).demo)

    // ---------- 我的 ----------

    suspend fun addresses(): Feed<AddressItem> = try {
        val p = Api.get("$M/address/list")
        Feed(p.list.map { AddressItem.parse(it) }, p.demo)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Feed(emptyList(), error = errorTextOf(e))
    }

    /**
     * 收货地址保存。写路径字段与小程序 address.js:202-208 逐字段一致：
     * {name, mobile, areaId, detailAddress, defaultStatus}（编辑时附 id）
     */
    suspend fun saveAddress(form: Map<String, Any?>): Ack {
        val editing = form["id"] != null
        val path = if (editing) "$M/address/update" else "$M/address/create"
        val p = if (editing) Api.put(path, form) else Api.post(path, form)
        // 后端是 CommonResult<Boolean>，本来就没有 URL；旧代码读 p.obj.url 是死读法，
        // 留着会让人以为"地址接口会返回图片地址"（审计 §22 修改方案第 3 条）
        return Ack(p.demo)
    }

    suspend fun deleteAddress(id: Long): Ack = Ack(Api.delete("$M/address/delete", mapOf("id" to id)).demo)

    /** 省市区三级地区树（免登录），供地址表单的三级级联选择器使用 */
    suspend fun areaTree(): Feed<AreaNode> = try {
        val p = Api.get("/app-api/system/area/tree")
        Feed(p.list.map { AreaNode.parse(it) }, p.demo)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Feed(emptyList(), error = errorTextOf(e))
    }

    /** 资料保存：字段与小程序 profile.js:109-111 一致（nickname + sex，avatar 非空才带） */
    suspend fun updateProfile(form: Map<String, Any?>): Ack = Ack(Api.put("$M/user/update", form).demo)

    /**
     * 修改密码。字段与小程序 profile.js:202 一致：{password, code}
     * code 是 scene=3（MEMBER_UPDATE_PASSWORD）的短信验证码，不是旧密码。
     */
    suspend fun updatePassword(password: String, code: String): Ack =
        Ack(Api.put("$M/user/update-password", mapOf("password" to password, "code" to code)).demo)

    suspend fun feedbackPage(pageNo: Int = 1, pageSize: Int = 20): Feed<FeedbackItem> = try {
        val p = Api.get("$T/feedback/page", mapOf("pageNo" to pageNo, "pageSize" to pageSize))
        Feed(p.list.map { FeedbackItem.parse(it) }, p.demo, total = p.total)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Feed(emptyList(), error = errorTextOf(e))
    }

    suspend fun submitFeedback(content: String, name: String, mobile: String): Ack =
        Ack(Api.post("$T/feedback/create", mapOf("content" to content, "name" to name, "mobile" to mobile)).demo)

    // ---------- 司机端读取 ----------

    private val driverId: Long get() = Prefs.driverId

    /**
     * 司机域请求的 `driverId` 参数（审计 §29）：值只能来自后端档案。
     * 未绑定就抛 [DriverUnboundException]，在拼参数这一步短路，**请求根本不发**——
     * 省掉一排 `if (未绑定) return 错误态`，也让"0 号查询"在结构上不可能出现。
     */
    private fun driverIdParam(): Map<String, Any?> {
        val id = Prefs.driverId
        if (id <= 0L) throw DriverUnboundException()
        return mapOf("driverId" to id)
    }

    /**
     * 司机档案（V-2b 纠正了"什么算否定"）：
     * 后端 `DriverAppServiceImpl.profile():167-171` 在**这账号没有司机记录**时 `return null`，
     * 信封就是 `{"code":0,"data":null}`；`Api.kt:314-315` 把 null data 读成**空的 `JSONObject()`**，
     * 所以旧判据 `!p.demo && o.length() > 0` 在真后端的否定回包上**恒为 false** ——
     * 撤档之后本机永远清不掉绑定，角色也永远降不回来（V-2 记的那条"残留 DRIVER 落进司机壳"的病根就在这儿）。
     *
     * 现在按后端事实改：**请求成功（没抛异常）+ 不是演示回落 + 回包里没有 `driverId>0`** 就是明确否定。
     * 三种"未知"仍然原样保留：网络/业务失败走 catch（`error` 非空，不碰绑定）、演示回落 `p.demo`、
     * 以及回包真的带着 `driverId>0`。**未知不等于否定**这条没变，变的是"后端说不是"长什么样。
     */
    suspend fun driverProfile(): One<DriverProfile> = try {
        val p = Api.get("$T/driver/profile")
        val o = p.obj ?: JSONObject()
        val profile = DriverProfile.parse(o)
        val bound = profile.driverId > 0
        if (bound) Prefs.driverId = profile.driverId
        else if (!p.demo) Prefs.clearDriverBinding()
        One(profile, p.demo)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        One(null, error = errorTextOf(e))
    }

    // 注意：shifts / pickups / earnings 三个接口后端**按登录会员识别司机身份**，
    // 小程序端不传 driverId（utils/api.js:313-324）；此处对齐，传了反而会被后端当成越权查询。

    suspend fun driverShifts(): Feed<ShiftTask> = try {
        val p = Api.get("$T/driver/shifts")
        Feed(p.list.map { ShiftTask.parse(it) }, p.demo)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Feed(emptyList(), error = errorTextOf(e))
    }

    suspend fun driverPickups(): Feed<PickupTask> = try {
        val p = Api.get("$T/driver/pickups")
        Feed(p.list.map { PickupTask.parse(it) }, p.demo)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Feed(emptyList(), error = errorTextOf(e))
    }

    /** 调度任务（算法派单结果，预留） */
    suspend fun driverTasks(): Feed<DriverTask> = try {
        val p = Api.get("$T/driver/tasks", driverIdParam())
        Feed(p.list.map { DriverTask.parse(it) }, p.demo)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Feed(emptyList(), error = errorTextOf(e))
    }

    suspend fun driverEarnings(): One<Earnings> = try {
        val p = Api.get("$T/driver/earnings")
        val o = p.obj ?: JSONObject()
        // 键名逐个抄自 AppDriverEarningsRespVO:14-26 / AppDriverEarningsRecordRespVO:14-26
        // （生产者 DriverAppServiceImpl:365-391、toRecord:1846-1859），小程序读点 earnings.js:52-69。
        // week/month/totalWeight 三面均无生产者，这里不再读任何键，交由模型显示"未给/后端无此口径"。
        One(
            Earnings(
                today = o.multiNumOrNull("todayAmount"),
                total = o.multiNumOrNull("totalAmount"),
                orderCount = o.multiNumOrNull("totalOrders")?.toInt(),
                items = o.multiArr("records").map {
                    val created = it.timeOf()
                    EarningItem(
                        date = monthDay(created),
                        orderNo = it.multiStr("orderNo"),
                        goodsName = it.multiStr("goodsName"),
                        amount = it.multiNumOrNull("totalAmount"),
                        weightKg = it.multiNumOrNull("weightKg"),
                        statusName = it.multiStr("statusName"),
                        createTime = created,
                    )
                },
                shiftCount = o.multiNumOrNull("shiftCount")?.toInt(),
                pendingCount = o.multiNumOrNull("pendingCount")?.toInt(),
                todayOrders = o.multiNumOrNull("todayOrders")?.toInt(),
            ),
            p.demo,
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        One(null, error = errorTextOf(e))
    }

    suspend fun driverHandovers(): Feed<HandoverTask> = try {
        val p = Api.get("$T/driver/handovers", driverIdParam())
        Feed(p.list.map { HandoverTask.parse(it) }, p.demo)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Feed(emptyList(), error = errorTextOf(e))
    }

    suspend fun driverLegs(): Feed<TransportLeg> = try {
        val p = Api.get("$T/driver/legs", driverIdParam())
        Feed(p.list.map { TransportLeg.parse(it) }, p.demo)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Feed(emptyList(), error = errorTextOf(e))
    }

    suspend fun currentLeg(): One<TransportLeg> = try {
        val p = Api.get("$T/driver/current-leg", driverIdParam())
        One(TransportLeg.parse(p.obj ?: JSONObject()), p.demo)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        One(null, error = errorTextOf(e))
    }

    suspend fun driverPosition(): One<BusVehicle> = try {
        val p = Api.get("$T/driver/position", driverIdParam())
        One(BusVehicle.parse(p.obj ?: JSONObject()), p.demo)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        One(null, error = errorTextOf(e))
    }

    /**
     * 导航数据源：有序经停 + 真实道路 polyline + 转向/车道步骤 + 偏航判定。
     *
     * 折线一律过 [RouteRepository]（内含 [com.shanxiang.buspost.core.map.RealRoadRouteValidator]）：
     * 来源不可信或折线缺失时 `polyline` 返回空并带上不可用原因，
     * 页面渲染"路线不可用"。这里不再计算任何直线距离代替道路里程。
     */
    suspend fun routeGuide(): One<RouteGuide> = try {
        val p = Api.get("$T/driver/route", driverIdParam())
        One(routeGuideOf(p), p.demo)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        One(RouteGuide.EMPTY, error = routeFailureTextOf("driverRoute:$driverId", errorTextOf(e)))
    }

    private fun routeGuideOf(p: Payload): RouteGuide {
        val o = p.obj ?: JSONObject()
        // ⑬：`guidance`/`instructions` 三面（后端 AppDriverRouteRespVO 1-125 全类 / 小程序 / DemoData 的 /driver/route）
        // 0 生产者，只有 `steps` 是演示面 guideOf() 真在写的键 ⇒ 撤掉两个自造别名，不给它们续命。
        val raw = o.multiArr("steps")
        val steps = raw.map { navStepOf(it) }
        val current = raw.indexOfFirst { it.multiBool("current") }.coerceAtLeast(0)
        val stations = o.multiArr("stations", "stops").map { Station.parse(it) }
        // 同一数组的第二种读法：任务点带 visitSequence / actionName / quantity / status，
        // Station 的键名集合覆盖不到它们（审计 §62），任务地图必须用这个列表。
        val stops = o.multiArr("stops", "stations").map { MissionStop.parse(it) }
        val vehicle = geoOf(o)
        // 这个端点的来源键只有两个真生产者：后端 `routeProvider`（AppDriverRouteRespVO:36）与演示面 `dataSource`
        //（DemoData 的 /driver/route 写 "DEMO"）。`provider` 是 route-preview 的键、`routeSource` 三方 0 命中 ⇒ 撤。
        val providerRaw = o.multiStr("routeProvider", "dataSource")
        val serverAvailable = !o.has("routeAvailable") || o.multiBool("routeAvailable")
        val check = RouteRepository.resolve(
            key = "driverRoute:$driverId",
            sourceRaw = providerRaw,
            points = geoListOf(o.multiArr("polyline", "track", "points")),
            roadDistanceMeters = o.multiNum("routeDistanceMeters", "distanceMeters").toInt(),
            roadEtaSeconds = o.multiNum("routeDurationSeconds").toInt(),
            crsRaw = o.multiStr("coordinateSystem"),
            stationGeos = stations.map { it.geo }, serverAvailable = serverAvailable,
        )
        // 后端 routeAvailable 是权威判定：它说没有真实道路，客户端就不画，也不管数组里有没有点。
        // 这一格现在还同时决定缓存条目的去留：后端否决时连本机旧条目一起删，不让"刷新过"读成"没刷也一样"（D-⑱-3）。
        val accepted = if (serverAvailable) (check as? RouteCheck.Accepted)?.route else null
        val unavailable = (check as? RouteCheck.Rejected)?.reason
            ?: if (!serverAvailable) {
                o.multiStr("routeUnavailableReason").ifEmpty { "暂无可用路线" }
            } else null

        val nextIdx = (current + 1).coerceAtMost(steps.size - 1)
        val nextRaw = raw.getOrNull(nextIdx)
        val nextStation = nextRaw?.multiStr("stationName", "nextStation").orEmpty().ifEmpty { stations.getOrNull(nextIdx)?.name ?: "" }
        // 旧字段照旧解析（后端给的是直线值），但不再用它兜底算距离：拿不到就是 0，界面显示"—"
        // 步骤内的米制键只有 `distanceMeters` 一个真写点（DemoData.guideOf）；`distance` 三方 0 生产者 ⇒ 撤。
        val legacyNextDistance = nextRaw?.multiNum("distanceMeters")?.toInt() ?: 0
        val heading = vehicle?.let { v ->
            accepted?.gcj02Points?.firstOrNull { it != v }?.let { GeoMath.bearing(v, it) } ?: 0.0
        } ?: 0.0
        return RouteGuide(
            steps = steps,
            // 折线一律取 GCJ-02 视图：底图、站点、车辆位置都是 GCJ-02，
            // 来源声明 WGS-84 时直接画会整体偏出约 500 米（审计 §17）
            polyline = accepted?.gcj02Points ?: emptyList(),
            stations = stations,
            totalMeters = accepted?.roadDistanceMeters?.toDouble() ?: 0.0,
            etaMinutes = if (accepted != null && accepted.roadEtaSeconds > 0) accepted.roadEtaSeconds / 60
            else o.multiNumOrNull("etaMinutes")?.toInt(),
            currentStep = current,
            nextStation = nextStation,
            nextStationDistanceMeters = o.multiNum("nextStopDistanceMeters").toInt().let { if (it > 0) it else legacyNextDistance },
            deviated = o.multiBool("deviated"),
            vehicleGeo = vehicle,
            headingDegrees = heading,
            dataSource = providerRaw.ifEmpty { o.multiStr("dataSource", "source") },
            routeSourceName = accepted?.source?.name ?: RouteSource.parse(providerRaw).name,
            roadDistanceMeters = accepted?.roadDistanceMeters ?: 0,
            roadEtaSeconds = accepted?.roadEtaSeconds ?: 0,
            nextStopRoadDistanceMeters = o.multiNum("nextStopRoadDistanceMeters").toInt(),
            nextStopRoadEtaSeconds = o.multiNum("nextStopRoadEtaSeconds").toInt(),
            routeUnavailableReason = unavailable,
            stops = stops,
            // 后端自己点名的"下一个点"与动作话术：与 nextStopRoad* 同一个 next 写出，所以到近播报只认这一对。
            // 客户端原先只按 stops 数组自己挑下一个点（nextNavigableStop），挑到的和这段里程是不是同一个点，本机无从证明。
            nextStopName = o.multiStr("nextStopName"),
            nextActionName = o.multiStr("nextActionName"),
        )
    }

    private fun navStepOf(it: JSONObject): NavStep = NavStep(
        icon = runCatching { NavIcon.valueOf(it.multiStr("icon", "maneuver", "action").uppercase()) }
            .getOrDefault(if (it.multiBool("arrive", "destination")) NavIcon.ARRIVE else NavIcon.STRAIGHT),
        text = it.multiStr("text", "instruction", "road"),
        detail = it.multiStr("detail", "hint", "roadName"),
        distanceMeters = it.multiNum("distanceMeters").toInt(),
        lanes = it.optJSONArray("lanes")?.strList() ?: emptyList(),
        // steps 整族都没有后端生产者：AppDriverRouteRespVO.java:17-86 的键是
        // planId/vehicleId/stops/polyline/routeProvider/routeAvailable/routeUnavailableReason/routeDistanceMeters/
        // routeDurationSeconds/routeComplete/deviationMeters/deviated/current*/nextAction*/nextStop*/currentSegmentPolyline，
        // 既没有 steps/guidance/instructions，也没有任何车道键 ⇒ 唯一 writer 是演示面 guideOf()。
        // 因此这里只认演示报文自己的键名，lane/seconds 两个自造别名撤掉；缺键 = null（旧写法塌成"第 1 车道 / 0 秒"，
        // 页面会把"没给"渲染成一句肯定的车道推荐）。
        recommendedLane = it.multiNumOrNull("recommendedLane")?.toInt(),
        etaSeconds = it.multiNumOrNull("etaSeconds")?.toInt(),
    )

    private fun geoOf(o: JSONObject): Geo? {
        // carrierLatitude/carrierLongitude 是 /send/track 里"承运车辆实时位置"的真实键
        //（AppSendOrderRespVO:100/:102，未发车/未上报时为 null）；放在最前，否则用户跟踪页永远读不到车。
        // currentLatitude/currentLongitude 是 /product-order/trace 的同义键
        //（AppProductOrderTraceRespVO:68/:70，ProductOrderServiceImpl:377-378 从 vehicle_location 真实上报取），
        // 之前两边都不命中，商城单的车辆位置永远显示"暂无"——那是假阴性，不是后端没给。
        val lat = o.multiNum("carrierLatitude", "currentLatitude", "latitude", "lat", "vehicleLatitude")
        val lng = o.multiNum("carrierLongitude", "currentLongitude", "longitude", "lng", "vehicleLongitude")
        return if (lat != 0.0 && lng != 0.0) Geo(lat, lng) else null
    }

    private fun geoListOf(list: List<JSONObject>): List<Geo> =
        list.map { Geo(it.multiNum("latitude", "lat"), it.multiNum("longitude", "lng")) }
            .filter { it.lat != 0.0 && it.lng != 0.0 }

    // ---------- 司机端动作 ----------

    /**
     * 司机一次性动作的唯一提交出口（V-1）。**只用于"会改后端状态且重发有副作用"的动作**，
     * 键里必须带上目标标识（班次/段/订单/消息 id），否则不同目标会互相挡住。
     *
     * 有意**不走**这道闸的两个：
     * · [reportLocation] —— 它是周期上报，⑦ 已经在 `DriverLocationService` 侧做了"合并 + 新鲜度闸 + 丢点可见"，
     *   这里的"丢弃重复"会把正常的新点位当重复扔掉，等于偷点；
     * · [uploadPhoto] —— 它只是取一个文件地址，重复上传不改变业务状态，真正的提交是后面那次 confirm。
     */
    private val actionGate = ActionGate()

    private suspend fun gatedAction(key: String, request: suspend () -> Ack): Ack =
        when (val r = actionGate.submit(key, request)) {
            is GateResult.Sent -> r.value
            is GateResult.Duplicate -> Ack(demo = false, notSent = true)
        }

    suspend fun depart(shiftId: Long): Ack = gatedAction("depart:$shiftId") {
        Ack(Api.post("$T/driver/depart", driverIdParam() + mapOf("shiftId" to shiftId)).demo)
    }

    suspend fun arrive(shiftId: Long, stationId: Long): Ack = gatedAction("arrive:$shiftId:$stationId") {
        Ack(Api.post("$T/driver/arrive", driverIdParam() + mapOf("shiftId" to shiftId, "stationId" to stationId)).demo)
    }

    suspend fun pickupConfirm(orderId: Long, photoUrl: String): Ack = gatedAction("pickup-confirm:$orderId") {
        Ack(Api.post("$T/driver/pickup-confirm", driverIdParam() + mapOf("orderId" to orderId, "driverPhotoUrl" to photoUrl)).demo)
    }

    suspend fun deliver(orderId: Long): Ack = gatedAction("deliver:$orderId") {
        Ack(Api.post("$T/driver/deliver", driverIdParam() + mapOf("orderId" to orderId)).demo)
    }

    suspend fun pickupVerify(orderId: Long, pickupCode: String): Ack = gatedAction("pickup-verify:$orderId") {
        Ack(Api.post("$T/driver/pickup-verify", driverIdParam() + mapOf("orderId" to orderId, "pickupCode" to pickupCode)).demo)
    }

    suspend fun productLoad(orderId: Long, photoUrl: String): Ack = gatedAction("product-load:$orderId") {
        Ack(Api.post("$T/driver/product-load", driverIdParam() + mapOf("orderId" to orderId, "driverPhotoUrl" to photoUrl)).demo)
    }

    suspend fun productDeliver(orderId: Long, photoUrl: String): Ack = gatedAction("product-deliver:$orderId") {
        Ack(Api.post("$T/driver/product-deliver", driverIdParam() + mapOf("orderId" to orderId, "driverPhotoUrl" to photoUrl)).demo)
    }

    /**
     * 位置上报。报文与小程序 workbench.js:648-655 逐字段一致：
     * {driverId, shiftId, longitude, latitude, speedKmh}，**不带任何客户端时间字段**
     * （`AppDriverLocationReqVO` 里没有这个字段，服务端 `reportTime` 一律用 `LocalDateTime.now()` 盖章，见审计 §45）。
     *
     * `shiftId` 可为 null —— 后端 P1-H 已把它改成可选：缺省时服务端用司机当前活跃段的班次兜底，
     * 兜不到就只更新「车辆最新位置」、不追加班次轨迹。
     * 此前这里写着「shiftId 必须带」，是照旧 VO 写的旧结论；它的实际后果是
     * 运输段/算法派单司机（本机拿不到班次）永远不上报，村民端到车提醒对这些单直接失效。
     *
     * `geo` 必须是 GCJ-02：坐标系在 `Locator` 出口统一换算（审计 §17），这里不再判断。
     */
    suspend fun reportLocation(shiftId: Long?, geo: Geo, speedKmh: Double): Ack =
        Ack(
            Api.post(
                "$T/driver/location",
                driverIdParam() + mapOf(
                    "shiftId" to shiftId,
                    "longitude" to geo.lng,
                    "latitude" to geo.lat,
                    "speedKmh" to speedKmh,
                ),
            ).demo,
        )

    /** action ∈ accept/navigate/arrive-origin/load/start/arrive-dest/handover-start/handover-confirm/complete */
    suspend fun legAction(action: String, legId: String, extra: Map<String, Any?> = emptyMap()): Ack =
        gatedAction("leg/$action:$legId") {
            Ack(Api.post("$T/driver/leg/$action", driverIdParam() + mapOf("legId" to legId) + extra).demo)
        }

    /**
     * 交接确认。照片与签名**各自一条 URL、各自可缺**：
     * 后端 `HandoverServiceImpl.confirmHandover:241-243` 对 `signUrl` 的纪律是"没带就不写"（不落空串），
     * 所以这里空串 = 这一格什么都不说，不能读成"核对过、这人确实没签"。
     */
    suspend fun confirmHandover(handoverId: Long, photoUrl: String, remark: String, signUrl: String = ""): Ack =
        gatedAction("handover-confirm:$handoverId") {
            Ack(
                Api.post(
                    "$T/driver/handover/confirm",
                    driverIdParam() + mapOf(
                        "handoverId" to handoverId, "photoUrl" to photoUrl,
                        "remark" to remark, "signUrl" to signUrl,
                    ),
                ).demo,
            )
        }

    suspend fun driverMessages(pageNo: Int = 1, pageSize: Int = 20): Feed<NotifyMsg> = try {
        val p = Api.get("$T/driver/messages", driverIdParam() + mapOf("pageNo" to pageNo, "pageSize" to pageSize))
        Feed(p.list.map { NotifyMsg.parse(it) }, p.demo, total = p.total)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Feed(emptyList(), error = errorTextOf(e), cause = e)
    }

    /**
     * 司机未读消息数（工作台铃铛角标，小程序 workbench.js:205）。
     * 后端 `AppDriverController.java:239-243` 同为 `CommonResult<Long>` 裸数字（审计 §23）；
     * 未绑定司机档案时返回 null（页面显示"未绑定"而不是 0），**且请求不出网**——
     * 这条契约由 `ScalarEnvelopeContractTest.未绑定司机时司机未读返回 null 且不上网络` 钉住（`server.requestCount == 0`）。
     *
     * 旧的 `attempt` 是靠"把 [DriverUnboundException] 兜成 null"来满足它的，代价是"未绑定"和
     * "接口读不到数"在 `Long?` 上变成同一个值。这里把门**显式写在调用之前**，异常不再被 helper 顺手收掉，
     * 后半段换成 [safeApiCall] 后块内可能抛的就只剩 Api 那三类（`longOf` 缺键只返回 null、不抛）。
     */
    suspend fun driverUnreadCount(): Long? {
        if (Prefs.driverId <= 0L) return null
        return safeApiCall {
            Api.get("$T/driver/messages/unread-count", driverIdParam()).longOf("count", "unreadCount")
        }.getOrNull()
    }

    /**
     * 标记消息已读。后端 `PUT /driver/messages/read` 没有幂等键，重复点会再打一次库；
     * 键里带消息 id，所以"连点两条不同消息"不会互相挡。
     */
    suspend fun readDriverMessage(id: Long): Ack = gatedAction("read-message:$id") {
        Ack(Api.put("$T/driver/messages/read", emptyMap(), driverIdParam() + mapOf("id" to id)).demo)
    }

    // ---------- 文件 ----------

    /**
     * 上传凭证照片，返回可展示的 url；离线演示时 url 为空，页面用本地临时图预览。
     *
     * 后端 `AppFileController.java:34-43` 是 `CommonResult<String>`——**裸 URL 字符串**，
     * 走对象键 `url/fileUrl` 永远读不到（审计 §22 里"交接照片实际未入库却提示成功"的那一半病根）。
     * 返回的 url 为空即"后端没给回地址"，调用方不得提示已上传。
     */
    suspend fun uploadPhoto(bytes: ByteArray, fileName: String): Ack {
        val p = Api.upload("/app-api/infra/file/upload", bytes, fileName)
        return Ack(p.demo, url = p.strOf("url", "fileUrl") ?: "")
    }

    /** 按日聚合用的 MM-dd：ts() 已把后端 EpochMillis 归一成 "yyyy-MM-dd HH:mm"，取日期段后 5 位；旧演示串 "09-28" 原样透传 */
    private fun monthDay(s: String): String = when {
        s.length >= 10 && s[4] == '-' -> s.substring(5, 10)
        s.length > 5 -> s.takeLast(5)
        else -> s
    }
}

/**
 * 天气提示按角色分档（纯函数，JVM 用例可断言）：返回 `用户/寄件人说法 to 司机说法`。
 *
 * 分工的依据是**谁能改什么**：用户能改的是"什么时候把件送到站点、要不要加苫布"，
 * 司机能改的是"怎么开"（跟车距离、雾灯、是否延后发车）。以前全工程只有一份
 * `farmTips`（六条清一色货运口径），而司机端一处都不显示天气
 * （`Repo.weather` 唯一的调用点在首页）⇒ 那句话实际上是"对着司机说寄件人的话"。
 *
 * 司机侧只写"因天气而变的操作动作"，**不声称知道路面实际状况**（我们没有路况传感，
 * 说"路面湿滑"就是造第二条真相）；`condition` 为空（天气没取到）时两份都给空串，
 * 页面据此不显示，而不是兜一句万能提示。
 */
internal fun weatherTipsFor(condition: String, temp: Int): Pair<String, String> = when {
    condition.isEmpty() -> "" to ""
    condition.contains("雨") || condition.contains("雪") ->
        "有降水，装车前请给山货加盖苫布" to "降水：出发前检查苫布与刹车，山路减速、拉大跟车距离"
    condition.contains("雾") || condition.contains("霾") ->
        "能见度偏低，山路发车请减速慢行" to "能见度偏低：使用雾灯并降速；能见度过低时延后发车，并通知候车乘客"
    condition.contains("晴") && temp >= 30 ->
        "气温偏高，生鲜件尽量走早班车" to "高温：车厢通风，生鲜件尽量安排早班车"
    condition.contains("晴") -> "晴好天气，适合发车与晾晒" to "天气晴好，按班次正常发车间"
    condition.contains("云") || condition.contains("阴") -> "多云，适合当日运输" to "多云，按班次正常发车间"
    temp <= 0 -> "低温，怕冻货品请提前告知" to "低温：起步放缓，留意背阴路段结冰"
    else -> "出行前留意山区路况" to "出行前留意山区路况变化"
}
