package com.shanxiang.buspost.notify

import android.content.Context
import com.shanxiang.buspost.core.AppDomain
import com.shanxiang.buspost.core.AppState
import com.shanxiang.buspost.data.repo.Repo
import com.shanxiang.buspost.data.store.Prefs
import com.shanxiang.buspost.nav.Routes
import kotlinx.coroutines.CancellationException

/**
 * 提醒判定内核：前台（App 打开时 20 秒一轮）和后台（WorkManager 15 分钟一轮）共用同一套逻辑，
 * 两端只在这个函数里"看一眼有没有新消息"，不在别处复制规则。司机域这两条读**按在看哪个域**决定（`AppState.activeDomain`，F-3 ③）——以前按账号角色，于是"从司机壳点回用户版"必须改写账号角色才停得下轮询，把一个界面动作记成了一条账号事实。
 *
 * F-2 ⑩：返回类型从 `Unit` 改成 [AlertScan]。改的不是提醒规则，是**这一轮发生了什么有没有出口**：
 * 旧的裸 `return` 把"没登录""通知关了""接口 429""推了 3 条"压成同一个形状，
 * `NotifyWorker` 拿不到区别，只能一律 `Result.success()`（详见 [NotifyOutcome] 顶部）。
 * 前台那轮（`BusPostApp:37`）继续忽略返回值——它没有"重跑"的概念，20 秒后自然再来一次，
 * 忽略是这一路的正确行为，不是漏了一处检查。
 */
object AlertEngine {

    /** 首轮只记录水位，不弹历史消息，避免刚装完 App 被积压消息轰炸 */
    private var primed = false

    suspend fun check(context: Context): AlertScan {
        // 这两种状态是"根本没问"，不是"问了说没有"——旧写法在这里与真正的空结果共用同一个 return
        if (!Prefs.loggedIn) return AlertScan(NotifyOutcome.NOT_LOGGED_IN, announced = 0)
        if (!Prefs.notifyEnabled) return AlertScan(NotifyOutcome.NOTIFICATION_DISABLED, announced = 0)

        // 本轮每一读的失败原因都留**异常类型**：一条读挂了不能被另一条读的成功洗白。
        // 页面侧照旧读 Feed.error 的文案，这里不参与渲染。
        // ⚠ 标量未读数（`unreadCount()` / `driverUnreadCount()`）不在这张表里：它们失败的出口是 null、
        //   没有可分类的异常，同一轮的会话/网络判据由下面的列表读提供。
        val causes = mutableListOf<Throwable>()
        val msgs = Repo.notifications(pageNo = 1, pageSize = 20)
        msgs.cause?.let { causes += it }
        // 列表只能数出本页（≤20 条）的未读，标量接口才是全量真值；两者取 max，读不到就不改角标（审计 §52）
        AppState.reportUserUnread(
            serverCount = Repo.unreadCount(),
            listCount = if (msgs.error == null) msgs.items.count { !it.read } else null,
        )

        if (!primed) {
            Prefs.lastSeenNotifyId = msgs.items.maxOfOrNull { it.id } ?: 0L
            if (AppState.activeDomain.value == AppDomain.DRIVER) {
                val dm = Repo.driverMessages(pageNo = 1, pageSize = 20)
                dm.cause?.let { causes += it }
                AppState.reportDriverUnread(
                    serverCount = Repo.driverUnreadCount(),
                    listCount = if (dm.error == null) dm.items.count { !it.read } else null,
                )
                Prefs.lastSeenDriverMsgId = dm.items.maxOfOrNull { it.id } ?: 0L
            }
            primed = true
        }

        if (AppState.activeDomain.value == AppDomain.DRIVER) {
            val dm = Repo.driverMessages(pageNo = 1, pageSize = 20)
            dm.cause?.let { causes += it }
            AppState.reportDriverUnread(
                serverCount = Repo.driverUnreadCount(),
                listCount = if (dm.error == null) dm.items.count { !it.read } else null,
            )
            val fresh = dm.items
                .filter { !it.read && it.id > Prefs.lastSeenDriverMsgId }
                .sortedByDescending { it.id }
                .take(3)
            fresh.forEach {
                Notifier.push(
                    context,
                    Channels.forType(it.type),
                    it.title.ifEmpty { "调度通知" },
                    // 单号写进正文，点通知仍留在司机域；不再深链乘客侧的轨迹页（审计 §29/§45）
                    it.orderNo.takeIf { no -> no.isNotEmpty() }
                        ?.let { no -> "${it.content}（运单 $no）" }
                        ?: it.content,
                    it.id.toInt(),
                    route = Routes.DRIVER_MESSAGES,
                )
            }
            dm.items.maxOfOrNull { it.id }?.let { Prefs.lastSeenDriverMsgId = it }
            return AlertScan(outcomeOf(causes, fresh.size), announced = fresh.size)
        }

        val fresh = msgs.items
            .filter { !it.read && it.id > Prefs.lastSeenNotifyId }
            .sortedByDescending { it.id }
            .take(3)
        fresh.forEach {
            Notifier.push(
                context,
                Channels.forType(it.type),
                it.title.ifEmpty { "订单动态" },
                it.content,
                it.id.toInt(),
                route = it.orderNo.takeIf { no -> no.isNotEmpty() }?.let(Routes::trace),
            )
        }
        msgs.items.maxOfOrNull { it.id }?.let { Prefs.lastSeenNotifyId = it }

        var arrival = 0
        if (Prefs.arrivalAlertEnabled) {
            // §68 数据源迁移：这条以前读 `/send/arrangements`，而 `AppSendArrangementRespVO`（11 个键）里
            // 根本没有 ETA 键 —— 真接口下永远不响，旧实现把缺键塌成 0 又让它看起来像"还没到时间"。
            // 真值是 `/send/page`（`AppSendOrderRespVO`）的承运车辆侧字段：carrierEtaMinutes 只在后端
            // 决定提醒时才填（`AppSendController:443-457` 车辆无位置/真实上报过期直接 return），
            // 但它是 Haversine 直线 ÷ 25km/h 的估算，文案必须写明口径，不许说成道路 ETA。
            val send = Repo.sendOrders()
            send.cause?.let { causes += it }
            send.items
                .filter { it.carrierLocationSource != "REAL_STALE" }
                .mapNotNull { o -> o.carrierEtaMinutes?.let { o to it } }
                .filter { (_, eta) -> eta in 1..10 }
                .forEach { (o, eta) ->
                    if (Prefs.arrivalAlerted(o.orderNo)) return@forEach
                    Prefs.markArrivalAlerted(o.orderNo)
                    arrival++
                    Notifier.push(
                        context,
                        Channels.ARRIVAL,
                        "车快到了 · 还有约 $eta 分钟（直线估算）",
                        "${o.vehiclePlate.ifEmpty { "承运车辆" }} 正驶向${o.targetStation.ifEmpty { "目标站点" }}，" +
                            "订单 ${o.orderNo}" +
                            o.pickupCode.takeIf { it.isNotEmpty() }?.let { " 到站后凭取件码 $it" }.orEmpty() +
                            "，请提前到站点等候。",
                        ("arrival" + o.orderNo).hashCode() and 0x7fffffff,
                        route = Routes.trace(o.orderNo),
                    )
                }
        }

        // ---------- 天气预警 ----------
        // 与到车提醒同模式：同一类天气 4 小时内不重复推送；天气状况变了（晴→雨）立即推，
        // 持续同一种恶劣天气（雨→雨）不重复打扰。没有位置就不推（无法确定"当地"天气）。
        var weatherAlerted = 0
        if (Prefs.weatherAlertEnabled) {
            val geo = com.shanxiang.buspost.core.loc.Locator.once(context)
            if (geo != null) {
                try {
                    val rg = Repo.reverseGeo(geo)
                    val w = Repo.weather(geo, rg.adcode)
                    if (!w.mock) {
                        val severity = weatherSeverity(w.text, w.temperature)
                        if (severity != null) {
                            val now = System.currentTimeMillis()
                            val elapsed = now - Prefs.lastWeatherAlertTimestamp
                            val conditionChanged = Prefs.lastWeatherAlertCondition != w.text
                            // 4 小时内同一天气不重推；天气变了立即推
                            if (conditionChanged || elapsed >= 4 * 3600 * 1000L) {
                                Prefs.lastWeatherAlertTimestamp = now
                                Prefs.lastWeatherAlertCondition = w.text
                                weatherAlerted++
                                val (title, body) = weatherAlertText(severity, w.text, w.temperature, w.wind)
                                Notifier.push(
                                    context,
                                    Channels.WEATHER,
                                    title,
                                    body,
                                    "weather_alert".hashCode() and 0x7fffffff,
                                    // 有意**不带 route**：天气预警不属于用户域也不属于司机域，点开就交给
                                    // 角色裁决那一步（`startDestinationFor`）。在这里写死 `Routes.USER_SHELL`
                                    // 就是通知替角色决定进哪个壳 —— 判据 SR-g 钉的正是这一格（D-100）。
                                )
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    // 取消必须穿透：这一格在挂起作用域里，把它收成一次普通失败就是 F-2 ⑨ 迁掉的那类病
                    throw e
                } catch (_: Exception) {
                    // 天气取数失败不影响其他提醒，静默跳过
                }
            }
        }

        val announced = fresh.size + arrival + weatherAlerted
        return AlertScan(outcomeOf(causes, announced), announced = announced)
    }

    /**
     * 天气严重程度判定：返回 null 表示不需要预警，非 null 表示需要预警并给出等级。
     * 与 Repo.farmTips 同口径但更严格 —— farmTips 给建议，这里决定是否推送通知。
     * 等级：LIGHT（提示）/ HEAVY（警告），影响文案措辞。
     */
    private enum class WeatherSeverity { LIGHT, HEAVY }

    private fun weatherSeverity(condition: String, temperature: Int): WeatherSeverity? {
        val c = condition.lowercase()
        // 暴雨、大雪、冰雹等 → 重度预警
        if (c.contains("暴雨") || c.contains("大") && (c.contains("雨") || c.contains("雪")) ||
            c.contains("冰雹") || c.contains("雷暴") || c.contains("台风")
        ) return WeatherSeverity.HEAVY
        // 普通雨雪、大雾、极端高温/低温 → 轻度预警
        if (c.contains("雨") || c.contains("雪") || c.contains("雾") || c.contains("沙尘") ||
            c.contains("霾") || temperature >= 38 || temperature <= -10
        ) return WeatherSeverity.LIGHT
        return null
    }

    private fun weatherAlertText(
        severity: WeatherSeverity,
        condition: String,
        temperature: Int,
        wind: String,
    ): Pair<String, String> {
        // 图标必须跟**天况**走，不能只跟严重度走。以前 LIGHT 恒为 🌧，
        // 于是"雾 / 霾 / 高温 / 低温"四种提醒在通知栏上都顶着一张下雨图 ——
        // 真机 2026-10-15 用户截图就是这条：正文写「当前天气：雾」，图标是 🌧。
        // 用户读到的信号是图，不是那行小字；图文对不上时人会先不信内容。
        val glyph = when {
            severity == WeatherSeverity.HEAVY -> "⚠️"
            condition.contains("雪") || condition.contains("冰") || condition.contains("冻") -> "❄"
            condition.contains("雾") || condition.contains("霾") || condition.contains("沙") -> "🌫"
            condition.contains("雨") -> "🌧"
            temperature >= 35 -> "☀"
            temperature <= 0 -> "🧊"
            else -> "⛅"
        }
        val prefix = "$glyph " + (
            if (severity == WeatherSeverity.HEAVY) "恶劣天气预警" else "天气提醒"
        )
        val body = buildString {
            append("当前天气：$condition，气温 ${temperature}°C")
            if (wind.isNotEmpty()) append("，$wind")
            append("。")
            when {
                condition.contains("雨") -> append(" 出行请带雨具，山货装车前请加盖苫布防淋。")
                condition.contains("雪") -> append(" 路面可能湿滑，发车请减速慢行。")
                condition.contains("雾") || condition.contains("霾") -> append(" 能见度低，山路发车请注意安全。")
                temperature >= 38 -> append(" 气温偏高，生鲜件建议走早班车。")
                temperature <= -10 -> append(" 气温极低，注意防寒保暖。")
                else -> append(" 请关注天气变化，合理安排出行。")
            }
        }
        return prefix to body
    }

    /**
     * 水位重置口。⚠ **当前全工程 0 个调用点**（实测 `grep -rn resetWatermark app/src` 只命中本行），
     * 所以换账号后 `primed` 仍是 true、上一个账号的 `lastSeenNotifyId` 继续当水位用。
     * 本轮只登记不接线（接它要选登录成功的落点，属 ⑩ 之外），已进 ⑰ 的 OPEN_DEFECT 清单。
     */
    fun resetWatermark() {
        primed = false
    }

    /**
     * 结局 = 本轮失败里**最该被 Worker 知道**的那一类，其次才看推了几条：
     * 401 会让所有读一起失败，只报"没有新消息"就把会话失效读成了平静；
     * 429/断网是"现在不行，稍后再来"；未绑定档案与程序错误重试也不会变好。
     *
     * `internal` 只为 `NotificationWorkerResultTest` 能在没有 Context 的 JVM 用例里直接驱动这条判据
     * （`check()` 要 Context 才能走 `Notifier.push`，工程无 Robolectric）。判据本体没有为测试改形。
     */
    internal fun outcomeOf(causes: List<Throwable>, announced: Int): NotifyOutcome {
        val kinds = causes.map { classifyNotifyFailure(it) }
        return when {
            NotifyOutcome.SESSION_INVALID in kinds -> NotifyOutcome.SESSION_INVALID
            NotifyOutcome.TRANSIENT_FAILURE in kinds -> NotifyOutcome.TRANSIENT_FAILURE
            NotifyOutcome.UNRECOVERABLE_LOCAL in kinds -> NotifyOutcome.UNRECOVERABLE_LOCAL
            announced > 0 -> NotifyOutcome.NEW_MESSAGES
            else -> NotifyOutcome.NO_NEW_MESSAGE
        }
    }
}
