package com.shanxiang.buspost.data.store

import android.content.Context
import android.content.SharedPreferences
import com.shanxiang.buspost.data.model.Role

/** 轻量本地存储：会话、外观偏好、草稿、已读水位。测试项目不引 Room，减少构建面。 */
object Prefs {
    private const val FILE = "buspost_prefs"
    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        sp = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    }

    private fun str(key: String, def: String = "") = sp.getString(key, def) ?: def
    private fun put(key: String, value: String) = sp.edit().putString(key, value).apply()

    var token: String
        get() = str("token")
        set(v) = put("token", v)

    var refreshToken: String
        get() = str("refreshToken")
        set(v) = put("refreshToken", v)

    /**
     * accessToken 的到期时刻（epoch 毫秒；0 = 后端没给或没登录过）。
     * 存它是因为后端 access token 只有 1800 秒，而 App 以前完全不判到期 ——
     * 30 分钟后第一条请求 401 就直接清盘跳登录，用户体感是"每次进来都要重新登录"。
     */
    var expiresAt: Long
        get() = sp.getLong("expiresAt", 0L)
        set(v) = sp.edit().putLong("expiresAt", v).apply()

    var userId: Long
        get() = sp.getLong("userId", 0L)
        set(v) = sp.edit().putLong("userId", v).apply()

    var mobile: String
        get() = str("mobile")
        set(v) = put("mobile", v)

    var nickname: String
        get() = str("nickname")
        set(v) = put("nickname", v)

    var avatar: String
        get() = str("avatar")
        set(v) = put("avatar", v)

    var gender: Int
        get() = sp.getInt("gender", 0)
        set(v) = sp.edit().putInt("gender", v).apply()

    /** 与小程序 userInfo.sex 同义（0 未知 / 1 男 / 2 女），仅命名对齐，存储共用 gender */
    var sex: Int
        get() = gender
        set(v) { gender = v }

    var role: Role
        get() = runCatching { Role.valueOf(str("role", Role.USER.name)) }.getOrDefault(Role.USER)
        set(v) = put("role", v.name)

    var driverId: Long
        get() = sp.getLong("driverId", 0L)
        set(v) = sp.edit().putLong("driverId", v).apply()

    var driverName: String
        get() = str("driverName")
        set(v) = put("driverName", v)

    var vehiclePlate: String
        get() = str("vehiclePlate")
        set(v) = put("vehiclePlate", v)

    var currentVillage: String
        get() = str("currentVillage")
        set(v) = put("currentVillage", v)

    var themeColor: String
        get() = str("themeColor", "blue")
        set(v) = put("themeColor", v)

    var darkMode: Boolean
        get() = sp.getBoolean("darkMode", false)
        set(v) = sp.edit().putBoolean("darkMode", v).apply()

    var elderlyMode: Boolean
        get() = sp.getBoolean("elderlyMode", false)
        set(v) = sp.edit().putBoolean("elderlyMode", v).apply()

    /**
     * 驾驶模式（司机端一等公民，2026-10-09）：一次打开"大字 ×1.2 + 触区 56dp + 界面夜间暗色"。
     *
     * 为什么单开一格而不复用 [elderlyMode]：两者服务的场景不同 —— 老年模式解决"看不清"，
     * 驾驶模式解决"**手在方向盘上、眼睛只能离开路面一秒**"。后者对触区（颠簸防误触）和夜间反光
     * （挡风玻璃倒影）的要求，老年模式一条都不管。共用一格会让"开大字"顺手把司机界面变暗。
     *
     * 与 [darkMode] 的关系同样是**独立**的：驾驶模式走的是同一个暗色令牌集（[com.shanxiang.buspost.core.ui.BusAppTheme]
     * 里 `nightUi = dark || driving`），但不回写 darkMode —— 关掉驾驶模式后，司机自己在设置里选的日/夜要原样还在。
     * 这是设备级外观偏好，[clearSession] 不清它（与主题、深色、老年模式同一档）。
     */
    var drivingMode: Boolean
        get() = sp.getBoolean("drivingMode", false)
        set(v) = sp.edit().putBoolean("drivingMode", v).apply()

    /** 驾驶模式的副开关：是否同时把界面切夜间暗色（默认跟随，司机可在工作台上单独关掉白天用） */
    var drivingNight: Boolean
        get() = sp.getBoolean("drivingNight", true)
        set(v) = sp.edit().putBoolean("drivingNight", v).apply()

    /** 消息提醒总开关（关闭后 Worker 不再弹通知，仅更新角标） */
    var notifyEnabled: Boolean
        get() = sp.getBoolean("notifyEnabled", true)
        set(v) = sp.edit().putBoolean("notifyEnabled", v).apply()

    var arrivalAlertEnabled: Boolean
        get() = sp.getBoolean("arrivalAlert", true)
        set(v) = sp.edit().putBoolean("arrivalAlert", v).apply()

    var weatherAlertEnabled: Boolean
        get() = sp.getBoolean("weatherAlert", true)
        set(v) = sp.edit().putBoolean("weatherAlert", v).apply()

    /** 上次天气预警推送时间戳（ms）：同一类天气 4 小时内不重复推送 */
    var lastWeatherAlertTimestamp: Long
        get() = sp.getLong("lastWeatherAlertTs", 0L)
        set(v) = sp.edit().putLong("lastWeatherAlertTs", v).apply()

    /** 上次天气预警的天气状况文本：天气变了才重新推送（晴→雨推，雨→雨不推） */
    var lastWeatherAlertCondition: String
        get() = sp.getString("lastWeatherAlertCond", "") ?: ""
        set(v) = sp.edit().putString("lastWeatherAlertCond", v).apply()

    /** 语音播报转向指引，司机行驶中免看屏 */
    var voiceGuidance: Boolean
        get() = sp.getBoolean("voiceGuidance", false)
        set(v) = sp.edit().putBoolean("voiceGuidance", v).apply()

    var laneGuidance: Boolean
        get() = sp.getBoolean("laneGuidance", true)
        set(v) = sp.edit().putBoolean("laneGuidance", v).apply()

    /**
     * 到站作业播报：快到任务点时读本机自己的那句话（该派送几件 / 可确认到达）。
     * 与 [voiceGuidance] 是两件事 —— 那台交给高德决定"要不要播转向"（`setUseInnerVoice`），
     * 这台只决定本机作业提醒出不出声，所以必须分开，不能共用（共用了关一个就悄悄关掉另一个）。
     */
    var approachVoice: Boolean
        get() = sp.getBoolean("approachVoice", true)
        set(v) = sp.edit().putBoolean("approachVoice", v).apply()

    /** 位置上报间隔（秒）：后台耗电与地图实时性的折中，服务内还会按失败次数退避 */
    var reportIntervalSec: Int
        get() = sp.getInt("reportIntervalSec", 10)
        set(v) = sp.edit().putInt("reportIntervalSec", v.coerceIn(3, 60)).apply()

    /**
     * 当前在途班次 ID：位置上报必须携带。前台服务被系统回收后凭它恢复上报，
     * 不必司机再点一次开关；收班或退出登录时清零。
     */
    var driverShiftId: Long
        get() = sp.getLong("driverShiftId", 0L)
        set(v) = sp.edit().putLong("driverShiftId", v).apply()

    /** 通知权限只问一次，拒绝后不再骚扰 */
    var notifPrompted: Boolean
        get() = sp.getBoolean("notifPrompted", false)
        set(v) = sp.edit().putBoolean("notifPrompted", v).apply()

    /** 已提醒过的消息 id 水位，避免重启后重复轰炸 */
    var lastSeenNotifyId: Long
        get() = sp.getLong("lastSeenNotifyId", 0L)
        set(v) = sp.edit().putLong("lastSeenNotifyId", v).apply()

    /** 司机消息水位：调度通知单独去重，和用户消息中心互不干扰 */
    var lastSeenDriverMsgId: Long
        get() = sp.getLong("lastSeenDriverMsgId", 0L)
        set(v) = sp.edit().putLong("lastSeenDriverMsgId", v).apply()

    /** 已弹过"车快到了"的订单，同一订单不打扰第二次 */
    fun arrivalAlerted(orderNo: String): Boolean = sp.getBoolean("alerted_$orderNo", false)
    fun markArrivalAlerted(orderNo: String) = sp.edit().putBoolean("alerted_$orderNo", true).apply()

    /** 寄件表单草稿：只存表单本体，照片临时路径与费用试算等派生数据不存 */
    var sendDraft: String
        get() = str("sendDraft")
        set(v) = put("sendDraft", v)

    var loginRedirect: String
        get() = str("loginRedirect")
        set(v) = put("loginRedirect", v)

    val loggedIn: Boolean get() = token.isNotEmpty()

    /**
     * 退出登录 / 会话失效时调用：**账号维度的字段一个都不留**。
     * 外观与提醒偏好（主题、深色、老年人模式、通知开关、语音播报）是设备级的，保留；
     * 上一位用户的司机身份、车辆、地址草稿、已读水位若漏掉，下一个人登录就会看到别人的数据。
     */
    fun clearSession() {
        val staleAlerts = sp.all.keys.filter { it.startsWith("alerted_") }
        sp.edit()
            .remove("token")
            .remove("refreshToken")
            .remove("expiresAt")
            .remove("userId")
            .remove("mobile")
            .remove("nickname")
            .remove("avatar")
            .remove("gender")
            .remove("role")
            .remove("driverId")
            .remove("driverName")
            .remove("vehiclePlate")
            .remove("driverShiftId")
            .remove("currentVillage")
            .remove("sendDraft")
            .remove("loginRedirect")
            .remove("lastSeenNotifyId")
            .remove("lastSeenDriverMsgId")
            .apply()
        if (staleAlerts.isNotEmpty()) sp.edit().apply { staleAlerts.forEach { remove(it) } }.apply()
    }

    /**
     * 建立新会话前调用：只清**司机身份**这一组账号级字段（审计 §29）。
     * driverId 只能由本次会话的 `/driver/profile` 授予；留着上一位的残留值，
     * 新账号的司机端就会拿 A 的 driverId 去查 B 的班次。
     */
    fun clearDriverBinding() {
        sp.edit()
            .remove("driverId")
            .remove("driverName")
            .remove("vehiclePlate")
            .remove("driverShiftId")
            .remove("lastSeenDriverMsgId")
            .apply()
    }

    // ---------- 收藏线路（本地 DataStore，不依赖后端） ----------

    /**
     * 收藏的线路 ID 列表：逗号分隔的字符串，空串表示无收藏。
     * 为什么不用 JSON：只有 ID 一个维度，逗号分隔够用，解析也简单。
     */
    private var favoriteLineIdsRaw: String
        get() = str("favoriteLineIds")
        set(v) = put("favoriteLineIds", v)

    /** 收藏的线路 ID 集合（读时解析，写时回写） */
    val favoriteLineIds: Set<String>
        get() = favoriteLineIdsRaw.split(",").filter { it.isNotEmpty() }.toSet()

    /** 切换收藏状态：已收藏则取消，未收藏则加入 */
    fun toggleFavoriteLine(lineId: String) {
        val current = favoriteLineIds.toMutableSet()
        if (lineId in current) {
            current.remove(lineId)
        } else {
            current.add(lineId)
        }
        favoriteLineIdsRaw = current.joinToString(",")
    }

    /** 查询是否已收藏 */
    fun isFavoriteLine(lineId: String): Boolean = lineId in favoriteLineIds
}
