package com.shanxiang.buspost.core

import com.shanxiang.buspost.data.model.Role
import com.shanxiang.buspost.data.store.Prefs
import com.shanxiang.buspost.core.ui.BusTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 进程级状态：外观偏好（立即生效并持久化）、**账号角色 / 在看哪个域两层分开**（[AppDomain]）、
 * 未读角标、通知深链。
 * 页面只读这里的 Flow，不各自缓存，避免设置页改了首页还是旧主题。
 */
object AppState {
    val theme = MutableStateFlow(BusTheme.fromId(Prefs.themeColor))
    val dark = MutableStateFlow(Prefs.darkMode)
    val elderly = MutableStateFlow(Prefs.elderlyMode)

    /**
     * 驾驶模式（司机端）：大字 + 大触区 + 夜间暗色。与 [dark] / [elderly] **互不覆盖** ——
     * 一个是"我在开车"的场景档，另两个是长期外观偏好；关掉驾驶模式后另两个照旧（见 [Prefs.drivingMode] 的说明）。
     */
    val driving = MutableStateFlow(Prefs.drivingMode)

    /** 驾驶模式里"是否同时切夜间暗色"的副开关（默认 true）。只有 [driving] 为真时才有效果 */
    val drivingNight = MutableStateFlow(Prefs.drivingNight)

    /**
     * 界面最终该不该用暗色 —— **全站唯一答案**。主题入口与需要有"夜/日"分支的页面（导航 HUD、地图）都读它，
     * 不要各自去拼 `dark || driving`：拼两遍就是两个真相，改一处漏一处（导航页白天开着驾驶模式不变暗就是这么来的）。
     *
     * 做成 StateFlow 而不是 `get()`：后者读的是快照、**不会触发重组**，
     * 于是"在设置里切夜间，导航页不变"这种坏要等到重进页面才看得出来。
     * 写入点只有 [recomputeNightUi]，由三个真源各自的 setter 调用。
     */
    private val _nightUi = MutableStateFlow(Prefs.darkMode || (Prefs.drivingMode && Prefs.drivingNight))
    val nightUi = _nightUi.asStateFlow()

    private fun recomputeNightUi() {
        _nightUi.value = dark.value || (driving.value && drivingNight.value)
    }

    /**
     * 在看哪个域（壳）：**唯一会被 UI 切页改的状态**，账号角色不在这一层（[AppDomain] / F-3 ③）。
     * 初值跟随账号角色，与 `startDestinationFor` 同一口径选壳；此后每个"换壳"的动线都要显式落一次
     * （登录 `BusPostRoot` / 进司机域 `MineScreen` / 出司机域 `DriverShell` / 撤档 [setAccountRole]），
     * 这样 `activeDomain` 恒等于"此刻屏幕上是哪个壳"，后台提醒（`AlertEngine`）才有可信的分域依据。
     * 与 [reportUserUnread] 同一纪律：对外只读，写只能走 [setActiveDomain] / [setAccountRole]。
     */
    private val _activeDomain = MutableStateFlow(AppDomain.of(Prefs.role))
    val activeDomain = _activeDomain.asStateFlow()

    /**
     * 未读角标：`_x` 私有 + 只读对外，**唯一写入口是 [reportUserUnread] / [reportDriverUnread]**。
     * 原来是三个页面各写 `.value`，其中 `Repo.unreadCount()` 那条恒 0（信封读法见审计 §22），
     * 谁最后写谁赢 → 进一次消息中心红点就没了（审计 §52）。
     */
    private val _unread = MutableStateFlow(0)
    val unread = _unread.asStateFlow()
    private val _driverUnread = MutableStateFlow(0)
    val driverUnread = _driverUnread.asStateFlow()

    /** 401 踢回登录页的事件计数：用递增 Long 而不是 Boolean，保证连续失效都能触发一次导航 */
    val kicked = MutableStateFlow(0L)

    /** 首页搜索框与商城页共享关键词，避免跨 tab 传参 */
    val search = MutableStateFlow("")

    /** 前后台标记：前台时 20 秒级轮询提醒，退到后台交给 WorkManager，省电也省流量 */
    val foreground = MutableStateFlow(false)

    val hasUserSession: Boolean get() = Prefs.loggedIn

    /** 通知点击带来的目标路由，根导航消费一次后清空 */
    val pendingRoute = MutableStateFlow<String?>(null)

    /**
     * 「快递」页的进入意图（对齐小程序 getApp().globalData.parcelIntent）：
     * type = TAB 时切到指定内层 tab（0 我的寄货 / 1 我的购物），
     * type = TRACK 时带 no 直接展开该单的物流详情。
     */
    data class ParcelIntent(val type: String, val tab: Int = 0, val no: String = "")
    val parcelIntent = MutableStateFlow<ParcelIntent?>(null)

    fun openParcel(tab: Int) {
        parcelIntent.value = ParcelIntent("TAB", tab = tab)
    }

    fun trackParcel(no: String) {
        parcelIntent.value = ParcelIntent("TRACK", no = no)
    }

    fun consumeParcelIntent(): ParcelIntent? {
        val v = parcelIntent.value
        parcelIntent.value = null
        return v
    }

    /**
     * 异常上报页的目标段（指令 §54）：路由 `driverException` **不带参数**，
     * 因为 `deepLinkTarget` 的分域闸按精确集合成员判断，带查询串的路由落在集合之外就等于没闸。
     * 于是"司机点的是哪一段"用意图传，页面消费一次即清（与 [parcelIntent] 同一手法）。
     */
    val exceptionLegId = MutableStateFlow<String?>(null)

    fun openException(legId: String) {
        exceptionLegId.value = legId
    }

    fun consumeExceptionLegId(): String? {
        val v = exceptionLegId.value
        exceptionLegId.value = null
        return v
    }

    /**
     * 车道级导航页的目的地（与 [exceptionLegId] 同一手法）：路由 `driverGuide` **不带参数**，
     * 因为分域闸按精确集合成员判断、带查询串就落在集合之外。目的地由工作台写入，导航页消费一次即清。
     *
     * 只装"要去哪儿"这三个字段，**不装折线**：折线必须由导航页自己让高德算一次真实道路
     * （硬规则"宁可暂时不能导航，不允许假导航"），从别处搬一条过来就绕过了 `RealRoadRouteValidator`。
     */
    data class GuideTarget(val lat: Double, val lng: Double, val name: String, val stationId: Long)

    /**
     * 自动进导航在**一个进程里只许发生一次**（用户口径 A：在途司机开 App 就给他导航）。
     * 放在 AppState 而不是 remember：卡片随工作台重建，remember 会复位，
     * 于是「结束导航」刚回到工作台就被再弹回导航页，司机再也出不去（真机实测 3 次连弹）。
     */
    var autoGuideUsed = false

    val guideTarget = MutableStateFlow<GuideTarget?>(null)

    fun openGuide(target: GuideTarget) {
        guideTarget.value = target
    }

    fun consumeGuideTarget(): GuideTarget? {
        val v = guideTarget.value
        guideTarget.value = null
        return v
    }

    fun setTheme(value: BusTheme) {
        theme.value = value
        Prefs.themeColor = value.id
    }

    fun setDark(value: Boolean) {
        dark.value = value
        Prefs.darkMode = value
        recomputeNightUi()
    }

    fun setElderly(value: Boolean) {
        elderly.value = value
        Prefs.elderlyMode = value
    }

    /** 驾驶模式总开关；关掉时把副开关的选择一起留着，下次开还是同一个夜/日口径 */
    fun setDriving(value: Boolean) {
        driving.value = value
        Prefs.drivingMode = value
        recomputeNightUi()
    }

    fun setDrivingNight(value: Boolean) {
        drivingNight.value = value
        Prefs.drivingNight = value
        recomputeNightUi()
    }

    /**
     * 切壳：只改"在看哪个域"，**账号角色一个字节都不动**（V2.1 §三 点名要取消的那句
     * `AppState.setRole(Role.USER)` 就是被这一句顶替的）。非司机账号拿不到司机视图，见 [domainFor]。
     */
    fun setActiveDomain(value: AppDomain) {
        _activeDomain.value = domainFor(value, Prefs.role)
    }

    /**
     * 账号角色的写入口，语义是"后端对这个账号的结论变了"：
     * · 用户显式进司机模式且 `bindDriver` 已授予（`MineViewModel.enterDriverMode`）；
     * · 档案**明确**否定后的降级（`WorkbenchViewModel.syncFromServer` → `reconcileRole`）。
     * **UI 页面切换永远不许调它**（F-3 ③）；登录落盘走 `Repo.saveSession` 直写存储，角色在那一步属于会话建立。
     * 角色变了顺带把视图从司机域带出来，但**不会**因为"变成司机"就把人搬进司机壳（[domainAfterRoleChange]）。
     */
    fun setAccountRole(value: Role) {
        Prefs.role = value
        _activeDomain.value = domainAfterRoleChange(value, _activeDomain.value)
    }

    /** 用户域角标唯一写入点，语义见 [reportUnread] */
    fun reportUserUnread(serverCount: Long?, listCount: Int?) {
        _unread.value = reportUnread(serverCount, listCount) ?: _unread.value
    }

    /** 司机域角标唯一写入点，语义见 [reportUnread] */
    fun reportDriverUnread(serverCount: Long?, listCount: Int?) {
        _driverUnread.value = reportUnread(serverCount, listCount) ?: _driverUnread.value
    }

    /**
     * 未读的合并规则：`serverCount`（后端 unread-count 接口，全量真值）与 `listCount`
     * （本页列表里数出的未读，最多只有 pageSize 条）取 **max**；两个都不知道时返回 null，
     * 由调用方保持原值——"这次没读到"绝不写成 0（审计 §52 的"红点自己闪没"就是这么来的）。
     * 注意不与上一次的值取 max，否则读完消息后角标再也降不下来。
     */
    private fun reportUnread(serverCount: Long?, listCount: Int?): Int? =
        listOfNotNull(serverCount?.toInt(), listCount).maxOrNull()

    fun kickToLogin() = kicked.update { it + 1 }
}
