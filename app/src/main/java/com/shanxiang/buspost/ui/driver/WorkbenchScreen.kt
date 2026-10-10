package com.shanxiang.buspost.ui.driver

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shanxiang.buspost.BuildConfig
import com.shanxiang.buspost.core.ui.BusButton
import com.shanxiang.buspost.core.ui.BusButtonVariant
import com.shanxiang.buspost.core.ui.BusCard
import com.shanxiang.buspost.core.ui.BusDivider
import com.shanxiang.buspost.core.ui.BusIcons
import com.shanxiang.buspost.core.ui.BusLazyPage
import com.shanxiang.buspost.core.ui.BusPullRefresh
import com.shanxiang.buspost.core.ui.BusRadius
import com.shanxiang.buspost.core.ui.BusSpace
import com.shanxiang.buspost.core.ui.BusSwitch
import com.shanxiang.buspost.core.ui.BusTag
import com.shanxiang.buspost.core.ui.BusTextField
import com.shanxiang.buspost.core.ui.BusTone
import com.shanxiang.buspost.core.ui.EmptyState
import com.shanxiang.buspost.core.loc.Locator
import com.shanxiang.buspost.core.map.PolylinePhase
import com.shanxiang.buspost.core.ui.ErrorState
import com.shanxiang.buspost.core.ui.InfoRow
import com.shanxiang.buspost.core.ui.LocalBusFontScale
import com.shanxiang.buspost.core.ui.LocalBusTokens
import com.shanxiang.buspost.core.ui.RoadLine
import com.shanxiang.buspost.core.ui.SkeletonList
import com.shanxiang.buspost.core.ui.SectionTitle
import com.shanxiang.buspost.core.ui.Signpost
import com.shanxiang.buspost.core.ui.SourceTag
import com.shanxiang.buspost.core.ui.StationTimeline
import com.shanxiang.buspost.core.ui.StopState
import com.shanxiang.buspost.core.ui.TicketCard
import com.shanxiang.buspost.core.ui.TimelineStop
import com.shanxiang.buspost.data.model.DriverProfile
import com.shanxiang.buspost.data.model.PickupTask
import com.shanxiang.buspost.data.model.ShiftState
import com.shanxiang.buspost.data.model.ShiftTask
import com.shanxiang.buspost.data.model.stopStateOf
import com.shanxiang.buspost.data.repo.Repo
import com.shanxiang.buspost.data.store.Prefs
import com.shanxiang.buspost.core.AppState
import com.shanxiang.buspost.driver.DriverLocationService
import com.shanxiang.buspost.driver.LocationReportPolicy
import com.shanxiang.buspost.driver.Running
import com.shanxiang.buspost.data.repo.DRIVER_UNBOUND
import com.shanxiang.buspost.nav.Routes
import com.shanxiang.buspost.nav.reconcileRole
import com.shanxiang.buspost.nav.serverSaysDriver
import com.shanxiang.buspost.ui.common.DemoNote
import com.shanxiang.buspost.ui.common.GpsFixVisual
import com.shanxiang.buspost.ui.common.LocalNavController
import com.shanxiang.buspost.ui.common.Map3DVisual
import com.shanxiang.buspost.ui.common.RouteUnavailableState
import com.shanxiang.buspost.ui.common.ScreenViewModel
import com.shanxiang.buspost.core.code.rememberBusScanner
import com.shanxiang.buspost.ui.user.formatPrice
import kotlinx.coroutines.delay

/**
 * 司机工作台：班次发车/到站、揽件核验与装车拍照、妥投，
 * 外加小程序做不到的常驻位置上报（前台服务）。
 */

class WorkbenchViewModel : ScreenViewModel() {
    var profile by mutableStateOf<DriverProfile?>(null)
        private set
    var shift by mutableStateOf<ShiftTask?>(null)
        private set
    var pickups by mutableStateOf<List<PickupTask>>(emptyList())
        private set
    var tasks by mutableStateOf<List<com.shanxiang.buspost.data.model.DriverTask>>(emptyList())
        private set
    var guide by mutableStateOf<com.shanxiang.buspost.data.model.RouteGuide?>(null)
        private set

    /**
     * 这一班的**路线问到哪一步**了（F-3 ⑧）：与线路页折线共用 `core/map` 那套三态，不再造第二套说法。
     * 没有这一格时，"这次没问到"与"后端说这一班没有可用道路"在屏幕上长成同一个样子
     * （旧写法两者都是"整块地图不占屏"）。
     *
     * ⚠ 为什么不能改成"读 `guide.routeUnavailableReason == null` 就当成失败"：那个 null 在正式链路里
     * **确实**只有失败支会产出（本轮实测：`RouteGuide.EMPTY` 在 main 源集只有一个生产者，`Repo.kt:1097` 的 catch），
     * 但它是失败支的**副产品**而不是契约——`DriverNavScreen` 就把 EMPTY 当初始值，
     * 而且失败原话（`One.error` 里那句"服务不可用"/DNS 错）只有相位这一格带得动。
     * 把"恰好同形"当判据 = 下一轮有人多生产一个 EMPTY 就静默说假话。
     */
    internal var routePhase by mutableStateOf(PolylinePhase.NotAsked)
        private set

    /** 这次问的失败原因：非空即"没问到"，页面只能说"没问到"，不许升格成"后端没有" */
    internal var routeFailure by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var receipt by mutableStateOf<String?>(null)
        private set

    /** 本机导航视图：到中途站"停靠作业"是本机推进，后端班次状态仍为在途，不伪造 FINISHED/RUNNING */
    var docked by mutableStateOf(false)
        private set

    /**
     * 后端**明确**说这个账号没有司机档案（V-2b）。页面据此把用户送回村民壳 ——
     * 角色降级在 [syncFromServer] 里已经做了，这里只是让"留在错的壳里"这件事有一个真出口。
     * 请求失败 / 演示回落 / `data` 为空都不置位（未知不等于否定）。
     */
    var demoted by mutableStateOf(false)
        private set

    /** 导航推进量（停靠完成后看向下一站）。换班次时归零，不参与班次状态判定 */
    private var navOffset = 0
    private var navShiftId = 0L

    /** 扫码后等待补照片的作业（货运/商城单强制拍照，邮快件凭面单免拍） */
    var pendingLoad by mutableStateOf<PickupTask?>(null)
        private set
    var pendingProof by mutableStateOf<PickupTask?>(null)
        private set

    /**
     * 已装车 / 已妥投的**本班次显示标记**：后端 /driver/pickups 用"列表成员"表达状态（已处理的单会从列表消失），
     * 但仍有些单（商城单）装车后保持在列表里等待妥投。这里只保留"仍在后端列表里"的 id，
     * 每次回读都会裁剪，绝不会留着后端已经不认的旧状态。
     */
    private val loadedIds = mutableSetOf<Long>()
    private val doneIds = mutableSetOf<Long>()

    fun isLoaded(task: PickupTask) = task.orderId in loadedIds
    fun isDelivered(task: PickupTask) = task.orderId in doneIds

    fun clearPending() {
        pendingLoad = null
        pendingProof = null
    }

    /** 回执只由页面侧的轻量提示写入（如"已取消扫码"） */
    fun showReceipt(text: String) {
        receipt = text
    }

    fun load() {
        work(reset = shift == null) { syncFromServer() }
    }

    /**
     * 与小程序 workbench.js loadAll 同序回读：profile →（shifts / pickups / tasks / route）→ handovers → 未读数。
     * 后端所有司机 POST 只回 Boolean，不回状态，所以**任何动作之后都必须靠这次回读拿到真值**。
     *
     * 唯一一处例外（V-2b）：档案**明确**说这账号不是司机时，后面四条司机域请求一条都不发，直接早退 ——
     * 这条早退不是可选优化。`/driver/shifts` 与 `/driver/pickups` 后端按登录会员识别身份、**不带 driverId**，
     * `driverIdParam()` 那道守卫拦不住它们（`Repo.kt:807-808` 记的就是这件事），
     * 只有在这里停下来才算真的"撤档之后不再替非司机账号拉司机数据"。
     */
    private suspend fun syncFromServer() {
        val p = Repo.driverProfile()
        val server = serverSaysDriver(
            driverId = p.value?.driverId ?: 0L,
            failed = p.error != null,
            demo = p.demo,
        )
        val stored = Prefs.role
        val next = reconcileRole(stored, server)
        if (next != stored) AppState.setAccountRole(next)
        if (server == false) {
            demoted = true
            profile = null
            shift = null
            pickups = emptyList()
            tasks = emptyList()
            guide = null
            // 早退支：四条司机域请求一条都没发，路线状态必须退回"没问过"，
            // 否则上一班次留下的 Asked 会让地图那块念一句"后端说没有"（⑧）。
            routePhase = PolylinePhase.NotAsked
            routeFailure = null
            docked = false
            Running.shiftId = 0L
            Prefs.driverShiftId = 0L
            mergeDemo(p.demo)
            // 与 driverIdParam() 守卫同一句文案：本机没有档案就是没有，不另编一套说法
            error = DRIVER_UNBOUND
            return
        }
        demoted = false
        val s = Repo.driverShifts()
        val k = Repo.driverPickups()
        val t = Repo.driverTasks()
        // 相位只圈住这一条请求：在飞时置 Asking，落地后无论成败都置 Asked，成败由 routeFailure 说。
        // 取消单独退回 NotAsked —— 取消不是失败，是"这次没问完"（与 F-3 ⑤ 那条同一条理由）。
        routePhase = PolylinePhase.Asking
        val g = try {
            Repo.routeGuide()
        } catch (e: kotlinx.coroutines.CancellationException) {
            routePhase = PolylinePhase.NotAsked
            throw e
        }
        routePhase = PolylinePhase.Asked
        routeFailure = g.error
        profile = p.value
        shift = s.items.firstOrNull { it.state == ShiftState.RUNNING }
            ?: s.items.firstOrNull { it.state == ShiftState.WAITING }
            ?: s.items.firstOrNull()
        pickups = k.items
        loadedIds.retainAll(k.items.map { it.orderId }.toSet())
        doneIds.retainAll(k.items.map { it.orderId }.toSet())
        tasks = t.items
        guide = g.value
        // 后端已经没有在途班次 → 本机停靠视图失去意义，位置上报也跟着停
        if (shift?.state != ShiftState.RUNNING) {
            docked = false
            Running.shiftId = 0L
            Prefs.driverShiftId = 0L
        } else {
            Running.shiftId = shift?.shiftId ?: 0L
            Prefs.driverShiftId = Running.shiftId
        }
        mergeDemo(p.demo, s.demo, k.demo, t.demo, g.demo)
        error = listOfNotNull(p.error, s.error, k.error, t.error, g.error).firstOrNull()
        p.value?.name?.let { Prefs.driverName = it }
        p.value?.plate?.let { if (it.isNotEmpty()) Prefs.vehiclePlate = it }
    }

    fun depart() {
        val id = shift?.shiftId ?: return
        act("发车") { Repo.depart(id) }
    }

    /**
     * 目标站序号 = 后端 currentStationId 推出的下一站 + 本机导航推进量（navOffset）。
     * navOffset 只影响"导航看向哪一站"，班次状态本身仍完全来自后端，与小程序 workbench.js:307-317 的 resumeIdx 同源。
     */
    fun targetIndex(): Int {
        val s = shift ?: return 0
        val cur = s.stations.indexOfFirst { it.id == s.currentStationId }
        return ((if (cur >= 0) cur + 1 else 0) + navOffset).coerceIn(0, (s.stations.size - 1).coerceAtLeast(0))
    }

    fun targetStationId(): Long = shift?.stations?.getOrNull(targetIndex())?.id ?: 0L

    fun targetStationName(): String = shift?.stations?.getOrNull(targetIndex())?.name.orEmpty()

    /**
     * 距目标站的**直线**米数（-1 表示无法判定）。
     *
     * 只用于 [distanceText] 里那句显式标注"不是道路里程"的远近说明：
     * 审计 §32 的判据是"直线值不得当里程、不得当业务门槛"，所以这里既不显示为道路距离，
     * 也不参与「确认到达」是否可点 —— `/driver/arrive` 只收 `shiftId + stationId`，到站是司机声明。
     */
    private fun straightDistanceToTarget(): Double {
        val s = shift ?: return -1.0
        val target = s.stations.getOrNull(targetIndex()) ?: return -1.0
        val me = Running.geo.value ?: return -1.0
        if (target.geo.lat == 0.0 || target.geo.lng == 0.0) return -1.0
        return com.shanxiang.buspost.data.model.GeoMath.distance(me, target.geo)
    }

    /** 到站卡片的距离文案：后端道路值优先，没有就说"接口未给"（见 [MissionMarkers.nextStopDistanceText]） */
    fun distanceText(): String = MissionMarkers.nextStopDistanceText(
        roadMeters = guide?.nextStopRoadDistanceMeters ?: 0,
        straightMeters = straightDistanceToTarget(),
        targetName = targetStationName(),
    )

    /**
     * 到站：**只提交后端 + 回读后端状态**，本机不再 copy 出 RUNNING/FINISHED。
     * 中途站到站后进入"停靠作业"视图（docked），末站由后端把班次收口为已完成。
     * 没有任何 GPS 门槛：定位只影响上面那行远近说明（硬规则「禁止 GPS 自动完成业务」的另一半是禁止 GPS 自动放行业务）。
     */
    fun arrive(stationId: Long) {
        val s = shift ?: return
        val id = s.shiftId
        val ti = targetIndex()
        val isTerminal = ti >= s.stations.size - 1
        val sid = if (stationId > 0) stationId else s.stations.getOrNull(ti)?.id ?: 0L
        // 后端 `AppDriverArriveReqVO.stationId @NotNull("站点编号不能为空")`，不在本线路的站还会被
        // `DRIVER_STATION_NOT_IN_ROUTE` 拒掉（`DriverAppServiceImpl:902`）——所以 0L 发出去只是一条必被拒的请求。
        // 班次里一个站都没给时按 [finish] 同一形状早退：宁可不提交，不造一个站点编号。
        if (sid <= 0L) {
            receipt = "后端没给本站编号，到站提交不了——请回到工作台重新拉取班次"
            return
        }
        act("到站") {
            val ack = Repo.arrive(id, sid)
            // 幂等闸拦下的那次（notSent）一个字节都没上网络，后端状态没有变化，
            // 所以本机视图也不许先行推进到"停靠作业"——那会和回执里"显示的是后端当前状态"自相矛盾。
            if (!ack.notSent) docked = !isTerminal
            ack
        }
    }

    /**
     * 停靠完成 / 跳过本站 → 继续行驶。**纯本机导航视图推进**（小程序 continueToNextStation 同语义），
     * 只把目标站往后挪一格，不重发 depart、也不改写后端班次状态。
     */
    fun continueToNextStation() {
        val s = shift ?: return
        if (s.state != ShiftState.RUNNING) return
        docked = false
        if (navShiftId != s.shiftId) { navShiftId = s.shiftId; navOffset = 0 }
        navOffset += 1
        if (navOffset > (s.stations.size - 1).coerceAtLeast(0)) {
            navOffset = (s.stations.size - 1).coerceAtLeast(0)
            receipt = "已经是最后一站，到站后由后端收口本班"
        } else {
            receipt = "导航目标已切到 ${targetStationName()}（班次状态仍以后端回读为准）"
        }
    }

    /**
     * 收班 = **到末站到站**（审计 §39）：`POST /driver/arrive` 到达线路最大 sequence 站时，
     * 后端把执行记录置已完成（`DriverAppServiceImpl:914-920`），这才是收班的真出口。
     *
     * 旧写法把 `"shift-$id"` 当 `legId` 发给 `@NotNull Long legId` 的 `/driver/leg/complete`：
     * ① 字符串转 Long 必 400；② 把"客运班次"和"货运运输段"两个业务域混进同一个调用（硬规则 1）。
     *
     * 后端有**防跳站**：`targetSeq` 必须在 `currentSeq + 1` 之内，否则 `DRIVER_STATION_ORDER_ILLEGAL`
     * （`DriverAppServiceImpl:900-911`）。所以没走到末站前一格时点收班会被**服务端拒**，
     * 这里不预先猜"能不能收"：错误如实进 `error`，下一次 `load()` 按后端班次状态把 `Running.shiftId` 复位（`:169-176`）。
     */
    fun finish() {
        val s = shift ?: return
        val terminalId = s.stations.lastOrNull()?.id ?: 0L
        if (terminalId == 0L) {
            receipt = "后端没给末站编号，收班提交不了——请到末站后用「到站」收口本班"
            return
        }
        act("收班（末站到站）") {
            val ack = Repo.arrive(s.shiftId, terminalId)
            docked = false
            Running.shiftId = 0L
            ack
        }
    }

    /** 扫码结果 → 待办任务：单号或取件码任一匹配（小程序 scanOrder 同口径） */
    fun matchScan(scanned: String): PickupTask? {
        val s = scanned.trim()
        if (s.isEmpty()) return null
        return pickups.firstOrNull { it.orderNo.isNotEmpty() && it.orderNo == s }
            ?: pickups.firstOrNull { it.pickupCode.isNotEmpty() && it.pickupCode == s }
            ?: pickups.firstOrNull { it.mailNo.isNotEmpty() && it.mailNo == s }
    }

    fun verify(task: PickupTask, code: String) {
        // 空白取件码不能发：`Api` 出网前会把空串**整个键丢掉**（`Api.kt:297`），
        // 后端收到的是一条没有 `pickupCode` 的请求，必然 `POSTAL_PICKUP_CODE_INVALID`。
        // 扫码那一支（[scanVerify]）早就有同形状的早退，手动输入这一支不该是漏的那一半。
        // 只判空、不改报文：发出去的还是用户输入的那一串。
        if (code.trim().isEmpty()) {
            receipt = "取件码还没填，核销提交不了——请扫码或让寄件人报出取件码"
            return
        }
        act("取件核销") { Repo.pickupVerify(task.orderId, code) }
    }

    /** 扫码核销取件码：未匹配到待取快递时给出明确回执，不静默 */
    fun scanVerify(scanned: String) {
        val task = matchScan(scanned)
        if (task == null) {
            receipt = "未匹配到待取快递（扫到：$scanned）"
            return
        }
        if (task.pickupCode.isEmpty()) {
            receipt = "该单没有取件码，请改用装车/妥投"
            return
        }
        act("取件核销") { Repo.pickupVerify(task.orderId, task.pickupCode) }
    }

    // ---------- 装车：分流只看 bizType（`PickupTask.isProduct`），照片闸门看 [PickupTask.photoRequired] ----------
    // · 商城单（bizType=PRODUCT）→ product-load，**本机不强制拍照**（photoRequired = !isProduct）
    // · 邮快件 / 货运单 → pickup-confirm，一律强制拍照
    // ⚠ 这与小程序不同：`workbench.js:885-893` 是"商城单要拍照、邮快件有面单不拍"，本机正好反了后两条。
    //   后端两种都能收（`ProductOrderServiceImpl:229-237` 把空 url 存成 ""；`DriverAppServiceImpl:1049-1050` 只对货运强制照片），
    //   所以这不是坏请求，是**凭证完整度差异**——改它要同时翻转 `DriverStateTest:121-126` 那条已断言的行为，留给用户裁决（D-⑦⑨-2）。

    fun beginScanLoad(scanned: String) {
        val task = matchScan(scanned)
        if (task == null) {
            receipt = "未匹配到待装订单（扫到：$scanned）"
            return
        }
        if (isLoaded(task)) {
            receipt = "${task.goodsName} 已经装过车了"
            return
        }
        if (task.photoRequired) {
            pendingLoad = task // 交给页面拉起拍照
        } else {
            loadOnBus(task, "")
        }
    }

    fun loadOnBus(task: PickupTask, photoUrl: String) {
        act(if (task.isProduct) "商城单装车" else "货运单装车") {
            val ack = if (task.isProduct) Repo.productLoad(task.orderId, photoUrl)
            else Repo.pickupConfirm(task.orderId, photoUrl)
            loadedIds.add(task.orderId)
            ack
        }
    }

    // ---------- 妥投：商城单走 product-deliver 并拍交付凭证，其他走 deliver ----------

    fun beginScanDeliver(scanned: String) {
        val task = matchScan(scanned)
        if (task == null) {
            receipt = "未匹配到待妥投订单（扫到：$scanned）"
            return
        }
        if (!isLoaded(task)) {
            receipt = "${task.goodsName} 还没装车，先扫码装车"
            return
        }
        if (task.isProduct) {
            pendingProof = task // 商城单要交付凭证照片
        } else {
            deliver(task)
        }
    }

    fun deliver(task: PickupTask) {
        act("妥投") {
            val ack = Repo.deliver(task.orderId)
            doneIds.add(task.orderId)
            ack
        }
    }

    fun deliverWithProof(task: PickupTask, photoUrl: String) {
        act("商城单妥投") {
            val ack = Repo.productDeliver(task.orderId, photoUrl)
            doneIds.add(task.orderId)
            ack
        }
    }

    /**
     * 选图 → 上传 → 执行挂起的作业。
     * 凭证照片**必须真的上传成功拿到后端 url**：上传失败就是不提交，回执写清失败原因，
     * 不再"没 url 也照样提交"把缺凭证的作业塞进后端。
     */
    fun photoPicked(context: Context, uri: android.net.Uri) {
        val loadTask = pendingLoad
        val proofTask = pendingProof
        pendingLoad = null
        pendingProof = null
        if (loadTask == null && proofTask == null) return
        if (busy) return
        busy = true
        work(reset = false) {
            try {
                // 直接调用，不再 runCatching 然后把异常原样 rethrow：那一圈仪式唯一的实际作用
                // 是把 CancellationException 在半空中接住一次（§七 / §三十一）。
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("读取照片失败")
                val ack = Repo.uploadPhoto(bytes, "cargo-${System.currentTimeMillis()}.jpg")
                if (ack.url.isEmpty()) {
                    error = if (ack.demo) "演示模式：照片未真实上传，作业未提交" else "照片未上传成功，作业未提交，请重试"
                    return@work
                }
                if (loadTask != null) runAction(if (loadTask.isProduct) "商城单装车" else "货运单装车") {
                    val a = if (loadTask.isProduct) Repo.productLoad(loadTask.orderId, ack.url)
                    else Repo.pickupConfirm(loadTask.orderId, ack.url)
                    loadedIds.add(loadTask.orderId)
                    a
                } else proofTask?.let { t ->
                    runAction("商城单妥投") {
                        val a = Repo.productDeliver(t.orderId, ack.url)
                        doneIds.add(t.orderId)
                        a
                    }
                }
            } finally {
                busy = false
            }
        }
    }

    /**
     * 司机动作的统一出口：POST 成功 → **立即回读后端**（shifts / pickups / tasks / route / profile）。
     * 后端这些写接口只回 Boolean、不回状态，所以本机绝不代替后端推进状态机。
     *
     * `ack.notSent` 走的是**独立的一支**：请求被本地幂等闸拦下、根本没上网络，
     * 所以既不能说"已提交后端"也不能说"失败"，画面只能反映回读到的后端状态（V-1 / 指令 §52）。
     */
    private suspend fun runAction(label: String, block: suspend () -> com.shanxiang.buspost.data.repo.Ack) {
        val ack = block()
        syncFromServer()
        receipt = when {
            ack.notSent -> "$label：本次请求未发给后端（同一个动作还在处理中），显示的是后端当前状态"
            ack.demo -> "$label：演示模式，后端未接收本次操作，状态只在本机显示"
            else -> "${label}已提交后端，班次与任务状态已按后端回读刷新"
        }
    }

    private fun act(label: String, block: suspend () -> com.shanxiang.buspost.data.repo.Ack) {
        if (busy) return
        // 先置位再启动：旧写法把 `busy = true` 写在 work {} 里面，
        // 它"够用"完全取决于 viewModelScope 的 Main.immediate 会在主线程同步跑到第一个挂起点 ——
        // 这一点在本工程无法用测试证明（无 Robolectric），所以不依赖调度器语义。
        busy = true
        work(reset = false) {
            try {
                runAction(label, block)
            } finally {
                busy = false
            }
        }
    }
}

@Composable
fun WorkbenchScreen(vm: WorkbenchViewModel = viewModel()) {
    val context = LocalContext.current
    val nav = LocalNavController.current
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    var reportOn by remember { mutableStateOf(Running.ok) }
    val serviceGeo by Running.geo.collectAsStateWithLifecycle()
    val reported by Running.reported.collectAsStateWithLifecycle()
    // 视觉层三格都取自服务已有的读数：朝向、停报原因、采样时刻。地图侧不自己算点龄阈值。
    val serviceHeading by Running.headingDegrees.collectAsStateWithLifecycle()
    val serviceWaiting by Running.waiting.collectAsStateWithLifecycle()
    val serviceFixAt by Running.fixAtElapsedMs.collectAsStateWithLifecycle()
    var verifyTarget by remember { mutableStateOf<PickupTask?>(null) }
    var code by remember { mutableStateOf("") }
    // 「扫码作业」大号三选一的开关：三种扫码动作收敛到这一处入口（见底部操作栏注释）
    var scanSheet by remember { mutableStateOf(false) }

    val askLocation = rememberLauncherForActivityResult(RequestPermission()) { granted ->
        if (granted) {
            DriverLocationService.start(context)
            reportOn = true
        }
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.photoPicked(context, uri)
    }

    // 扫码模式：LOAD 装车 / DELIVER 妥投 / VERIFY 取件核销（对齐小程序 scanToLoad 等三个入口）
    var scanMode by remember { mutableStateOf("") }
    val scanner = rememberBusScanner { scanned ->
        val mode = scanMode
        scanMode = ""
        if (scanned.isNullOrBlank()) {
            // 取消扫码不报错，提示可手输兜底（相机不可用时作业流程不能被堵死）
            if (mode.isNotEmpty()) vm.showReceipt("已取消扫码，可用下方「手输单号」兜底")
            return@rememberBusScanner
        }
        when (mode) {
            "LOAD" -> vm.beginScanLoad(scanned)
            "DELIVER" -> vm.beginScanDeliver(scanned)
            "VERIFY" -> vm.scanVerify(scanned)
        }
    }

    // 需要拍照的作业（货运装车 / 商城单装车与妥投）→ 自动拉起选图
    LaunchedEffect(vm.pendingLoad, vm.pendingProof) {
        if (vm.pendingLoad != null || vm.pendingProof != null) {
            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    LaunchedEffect(Unit) { if (vm.shift == null) vm.load() }

    /**
     * 司机进工作台就自动开始位置上报，不再要人去找那个开关（用户口径 v3「登录即上报」）。
     * 没授予定位权限时**不自动弹系统框**：首屏就弹是骚扰，改成界面留一句引导、点了才走 [askLocation]。
     * 走的是与手动开关完全同一条路径（含 shiftId 回落），所以"自动"不会带出第二份上报口径。
     */
    LaunchedEffect(vm.shift) {
        if (vm.shift == null || reportOn) return@LaunchedEffect
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            if (Running.shiftId <= 0L) Running.shiftId = Prefs.driverShiftId
            DriverLocationService.start(context)
            reportOn = true
            android.util.Log.i("BUS_UI", "auto_start_report shiftId=${Running.shiftId} role=DRIVER")
        } else {
            android.util.Log.i("BUS_UI", "auto_start_report skipped: fine location not granted")
        }
    }

    /**
     * 后端**明确**说这账号没有司机档案（V-2b）：角色已经在 `syncFromServer` 里降回 USER，
     * 人就不能继续留在司机壳 —— 与 `DriverShell` 的「用户版」出口同一手法（改角色 + 导航，两件事缺一不可）。
     * 位置上报服务也在这里停：档案都没了还继续拿旧 `shiftId` 报点位，那就是假上报。
     */
    LaunchedEffect(vm.demoted) {
        if (vm.demoted) {
            DriverLocationService.stop(context)
            nav.navigate(Routes.USER_SHELL) { popUpTo(Routes.DRIVER_SHELL) { inclusive = true } }
        }
    }

    LaunchedEffect(Running.ok, reported) { reportOn = Running.ok }

    // 本班收口（末站到站 / 手动收班）后位置上报不再有意义，服务自动关闭，通知栏不留"上报中"。
    // ⚠ 这个副作用原来写在滚动内容里；换成懒加载之后必须**上提到组合外层** ——
    // 留在 item 里就等于"这一屏没滚到它就不执行"，司机收班了通知栏还亮着"上报中"。
    LaunchedEffect(vm.shift?.state, vm.shift?.shiftId) {
        if (vm.shift?.state == ShiftState.FINISHED && Running.ok) {
            DriverLocationService.stop(context)
            reportOn = false
        }
    }

    val shift = vm.shift
    // 停靠作业只是本机导航视图（后端仍在途）：主操作区据此显示「停靠完成，继续行驶」，
    // 与 ShiftCard 内部用的是同一个判定，所以提到这一层算一次，避免两处各算一遍漂移。
    val viewState = if (vm.docked && shift?.state == ShiftState.RUNNING) ShiftState.STOPPED else shift?.state
    // 分组放外层 remember：原来写在滚动内容里，懒加载后每次滚动都会重算一遍分组
    val taskSegments = remember(vm.tasks) {
        com.shanxiang.buspost.data.model.DriverTaskSegment.group(vm.tasks)
    }

    // 固定底部操作栏：司机唯一必须做的那个动作（发车/到站/停靠完成）钉在拇指区，
    // 不再被"任务地图 + 十余行实时回执"压在几屏之下（设备实测旧版要划到第四屏才够得着）。
    StickyBarScaffold(
        bar = {
            WorkbenchActionBar(
                hasShift = shift != null,
                viewState = viewState,
                busy = vm.busy,
                hint = workbenchBarHint(shift, viewState, vm.distanceText()),
                onDepart = { vm.depart() },
                onArrive = { vm.arrive(vm.targetStationId()) },
                onContinue = { vm.continueToNextStation() },
                onRefresh = { vm.load() },
                onOpenScanSheet = { scanSheet = true },
            )
        },
    ) { bottom ->
        BusPullRefresh(
            refreshing = vm.loading && shift != null,
            onRefresh = { vm.load() },
        ) {
            // 长页改 LazyColumn：旧版把 15 个版块 + 每张揽件卡一次全量布局进 verticalScroll，
            // 件数一多首帧就卡；懒加载后只有屏幕上那几块参与测量。
            BusLazyPage(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = bottom),
                spacedBy = 12.dp,
            ) {
                item(key = "header") {
                    Signpost(
                        title = "司机工作台",
                        subtitle = listOfNotNull(vm.profile?.name, vm.profile?.plate, vm.profile?.lineName)
                            .filter { it?.isNotEmpty() == true }.joinToString(" · "),
                        badge = shift?.statusLabel ?: "今日班次",
                    ) {
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            // 载重只说后端真给的额定值（cargoCapacityKg）；"已装多少 kg"三方都没有口径，
                            // 旧写法把 usedKg 缺键塌成 0.0，于是标签写"载重 0/200 kg"——那是个编出来的装载数。
                            val cap = vm.profile?.capacityKg
                            if (cap != null) {
                                BusTag("额定载重 ${formatPrice(cap)} kg", tone = BusTone.GOLD)
                            } else {
                                BusTag("额定载重未给", tone = BusTone.NEUTRAL)
                            }
                            // 认证状态：AppDriverProfileRespVO 七个键里没有任何审核位 ⇒ 没给就不出标签，
                            // 旧写法无条件写"未认证"，等于替后端下了一个负面结论。
                            val verified = vm.profile?.verified
                            if (verified != null) {
                                BusTag(
                                    if (verified) "已认证" else "未认证",
                                    tone = if (verified) BusTone.PRIMARY else BusTone.NEUTRAL,
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            // 驾驶模式开关进站牌头：它是"整屏怎么显示"的档位，属于头部而不是列表里的一条设置，
                            // 放这儿一秒能找到，也不占滚动区高度。
                            DrivingModeChip()
                        }
                    }
                }

                if (vm.demo) item(key = "demo") {
                    DemoNote(true, text = "后端没有返回司机数据，当前为演示班次与揽件任务，操作不会真正改变订单状态。")
                }
                vm.error?.let { message ->
                    item(key = "error") { ErrorState(message, onRetry = { vm.load() }) }
                }
                vm.receipt?.let { text ->
                    item(key = "receipt") { ReceiptBar(text) }
                }
                if (vm.loading && shift == null) item(key = "skeleton") { SkeletonList(3) }

                // 任务地图置顶（设备实测 wb1/wb2.png：旧顺序下它排在仓位表与 18 站时间线之后，
                // 司机要划四屏才够到"怎么走"那一格）。出现的条件仍是 F-3 ⑧ 那三种，一种都不能并掉：
                // · 问到了东西（有任务点或有可信折线）→ 照常画；这一支里"没有路"那句话走唯一出口
                //   `MissionMarkers.missionRouteReasonOf`，本页不再自带"后端没有下发"式兜底。
                // · 这次**问失败**、什么都没拿到 → 不能整块静默消失。旧注释写的是"后端确实没给，不是加载失败"，
                //   而 `Repo.routeGuide()` 的 catch 支回的就是 `RouteGuide.EMPTY` —— 藏起来等于把"没问到"演成"没有"，
                //   所以这里占一格说实话并给重试入口。
                // · 压根没问过（撤档早退、还没走到这条请求）→ 不占屏，也不替后端下任何结论。
                //
                // 第二支旧写法漏了一种实测到的情形：`/driver/route` 回 `{"code":0,"data":null}`
                // （本机库里这个司机一条调度明细都没有，`DriverAppServiceImpl.getRoute:444-454` 两处 `return null`）。
                // 这时 guide 非空、routeFailure 为空，两个分支都不命中 —— 整块导航面无声消失，
                // 司机既看不到"为什么没有"，也没有重试入口，比写错一句话更糟。现在按后端事实分开：
                // 只要**问过了**（guide 已经落地，或 phase 已经是 Asked），就占一格说实话。
                val missionGuide = vm.guide
                if (missionGuide != null && (missionGuide.stops.isNotEmpty() || missionGuide.polyline.size >= 2)) {
                    item(key = "map") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionTitle("本班任务地图", subtitle = missionGuide.nextStation.ifEmpty { null })
                            // ⑬ 的降级/不可用警告由服务侧结论得出：点龄用同一个单调时钟，阈值由 Map3DVisual
                            // 转读 LocationReportPolicy 的那个常量，页面这里不写第二个数字。
                            val meGps = Map3DVisual.gpsFixVisual(
                                hasFix = serviceGeo != null,
                                fixAgeMs = (Locator.elapsed() - serviceFixAt).coerceAtLeast(0L),
                                holdReason = serviceWaiting,
                                headingDegrees = serviceHeading,
                            )
                            DriverMissionMap(
                                guide = missionGuide,
                                noRouteReason = MissionMarkers.missionRouteReasonOf(
                                    vm.routePhase,
                                    vm.routeFailure,
                                    missionGuide.routeUnavailableReason,
                                ),
                                me = serviceGeo,
                                meGps = meGps,
                                focusStopId = vm.targetStationId(),
                                onReplan = { vm.load() },
                            )
                        }
                    }
                } else if (missionGuide != null || vm.routeFailure != null) {
                    item(key = "mapUnavailable") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionTitle("本班任务地图", subtitle = null)
                            RouteUnavailableState(
                                height = 132.dp,
                                reason = MissionMarkers.missionRouteReasonOf(
                                    vm.routePhase,
                                    vm.routeFailure,
                                    missionGuide?.routeUnavailableReason,
                                ),
                                onReplan = { vm.load() },
                            )
                        }
                    }
                }

                // 正式导航面（D-003）：本机高德引擎 + 外部地图交接。旧 DriverNavScreen 转 DEBUG ONLY 之后，
                // release 可达的"怎么走"只有这一处 —— 所以**调用点保持恰好一个**（判据 N-g 钉着）。
                // 2026-10-10 从"有路线支"里搬出来，改挂在"问过了且 guide 落地"这个条件上：
                // 真机实测（D-069 §三 报告）后端把 `/driver/route` 答成"有 guide、零站点、零折线"时，
                // 原来整块导航面**跟着路线一起消失** —— 司机既看不到地图，也看不到"为什么不能走"，
                // 更没有外部地图那条退路。目的地策略一字未改（仍只认 `nextNavigableStop(guide)`，
                // 调度没给就不算路、也不交给外部地图），改的只是**这一格要在场并把原因说清楚**。
                if (missionGuide != null) {
                    item(key = "navCard") {
                        DriverSdkNavCard(
                            guide = missionGuide,
                            vehicleGeo = serviceGeo,
                            onToast = { vm.showReceipt(it) },
                            onOpenLaneGuide = { nav.navigate(Routes.DRIVER_GUIDE) },
                            inTransit = vm.shift?.state == ShiftState.RUNNING,
                        )
                    }
                }

                // 待发车状态：显示大巴车行李舱容量可视化（对齐小程序 workbench.wxml:153-187）
                if (shift != null && shift.state == ShiftState.WAITING) {
                    item(key = "cargoGauge") {
                        BusCard(modifier = Modifier.fillMaxWidth()) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "待发车",
                                    style = androidx.compose.material3.MaterialTheme.typography.headlineSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = t.ink,
                                    ),
                                )
                                Spacer(Modifier.height(BusSpace.l))
                                CargoGauge(
                                    // 件数口径：cargoCapacity（profile:21）− loadedCount（shift:30），两个都是后端真实键。
                                    // 任一没给就是 null，仪表盘显示"未给"而不是旧写法那种"100% 空仓"。
                                    capacityPercent = vm.profile?.freePercent(shift.loadedCount),
                                    loadedCount = shift.loadedCount,
                                    cargoCapacity = vm.profile?.cargoCapacity,
                                    capacityKg = vm.profile?.capacityKg,
                                )
                            }
                        }
                    }
                }

                if (shift != null) {
                    item(key = "shift") {
                        ShiftCard(
                            shift = shift,
                            // viewState 可空（没有班次时就是 null），这一支里 shift 已非空 ⇒ 取回本班真实状态，
                            // 不从别处补一个默认值（补出来的状态会让卡片和底部操作栏说两句话）
                            viewState = viewState ?: shift.state,
                            distanceText = vm.distanceText(),
                            stops = vm.guide?.stops ?: emptyList(),
                            targetStationId = vm.targetStationId(),
                        )
                    }
                }

                // 完成面板：班次收口后给出一行小结（等价小程序「本班已完成」汇总）
                if (shift != null && shift.state == ShiftState.FINISHED) {
                    item(key = "finished") {
                        BusCard(modifier = Modifier.fillMaxWidth(), warm = true) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(BusIcons.checkCircle, contentDescription = null, tint = t.statusDone, modifier = Modifier.size((20 * scale).dp))
                                Spacer(Modifier.width(9.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("本班已完成", color = t.ink, fontSize = (15 * scale).sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        // 收班小结也只剩 loadedCount 这一个件数口径（orderCount 无生产者，念出来就是编数）
                                        "${shift.lineName.ifEmpty { "本机班次" }} · ${shift.loadedCount} 件货物已全部处理",
                                        color = t.textSecondary,
                                        fontSize = (12.5 * scale).sp,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                item(key = "report") {
                    ReportCard(
                        on = reportOn,
                        geo = serviceGeo,
                        reported = reported,
                        waiting = Running.waiting.collectAsStateWithLifecycle().value,
                        intervalSec = Prefs.reportIntervalSec,
                        lastSuccessAt = Running.lastSuccessAt,
                        lastError = Running.lastError.collectAsStateWithLifecycle().value,
                        shiftId = Running.shiftId,
                        fixAtElapsedMs = Running.fixAtElapsedMs.collectAsStateWithLifecycle().value,
                        accuracyMeters = Running.accuracyMeters.collectAsStateWithLifecycle().value,
                        provider = Running.provider.collectAsStateWithLifecycle().value,
                        dropped = Running.dropped.collectAsStateWithLifecycle().value,
                        shiftNote = Running.shiftNote.collectAsStateWithLifecycle().value,
                        locatorState = Running.locator.collectAsStateWithLifecycle().value,
                        onToggle = { want ->
                            if (want) {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                                    == PackageManager.PERMISSION_GRANTED
                                ) {
                                    if (Running.shiftId <= 0L) Running.shiftId = Prefs.driverShiftId
                                    DriverLocationService.start(context)
                                    reportOn = true
                                } else {
                                    askLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                }
                            } else {
                                DriverLocationService.stop(context)
                                reportOn = false
                            }
                        },
                        onOpenNav = { if (BuildConfig.DEBUG) nav.navigate(Routes.DRIVER_NAV) },
                    )
                }

                // 今日运营任务（任务段路单）：数据 = /driver/tasks，"哪些行属于同一段、同站有哪几个动作"
                // 在模型层聚好（DriverTaskSegment.group，口径抄小程序 workbench.js:221-254），页面只渲染，
                // 不再自己分组第二遍。这一块 `tasks` 过去只回读、不上屏（司机看得见班次与揽件，看不见派单路单）。
                if (taskSegments.isNotEmpty()) {
                    item(key = "taskTitle") {
                        SectionTitle("今日运营任务", subtitle = "${taskSegments.size} 个任务段")
                    }
                    items(taskSegments.size, key = { "task-$it" }) { i ->
                        val seg = taskSegments[i]
                        BusCard(modifier = Modifier.fillMaxWidth()) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                // 窗口两值都来自后端 plan.taskWindowStart/End；缺哪个就说缺哪个，不拿本机时钟补
                                Text(seg.windowText, color = t.textSecondary, fontSize = (12 * scale).sp)
                                seg.stations.forEach { station ->
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                with(MissionMarkers) { station.head.markerTitle() },
                                                color = t.ink,
                                                fontSize = (14 * scale).sp,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.weight(1f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            // 状态色只跟后端 status（TaskItemStatusEnum 那张表）：
                                            // 失败与已完成不同色，本机点击/GPS 都不参与这一格。
                                            BusTag(
                                                station.head.statusLabel.ifEmpty { "状态未给" },
                                                tone = com.shanxiang.buspost.data.model.TaskItemStatus
                                                    .of(station.head.status)?.tone ?: BusTone.NEUTRAL,
                                            )
                                        }
                                        with(MissionMarkers) { station.head.etaText() }?.let {
                                            Text(it, color = t.textTertiary, fontSize = (11.5 * scale).sp)
                                        }
                                        station.actions.forEach { action ->
                                            // 数量走 quantityText()：<=0 不显示，绝不渲染"×0 件"那种看着像值的空口径
                                            // （它是 MissionMarkers 的成员扩展，必须 with 进来，不能裸调）
                                            val line = listOfNotNull(
                                                action.actionLabel.ifEmpty { null },
                                                action.orderNo.ifEmpty { null },
                                                with(MissionMarkers) { action.quantityText() },
                                            ).joinToString(" · ")
                                            if (line.isNotEmpty()) {
                                                Text(line, color = t.textSecondary, fontSize = (13 * scale).sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (!vm.loading && vm.error == null) {
                    // 请求成功而段为空 = 这一次问到了、报文里没有任务段；这和"这次没问到/拉取失败"（上面 ErrorState 那条）
                    // 必须是两种长相，否则空列表会替后端下一句它没说过的结论（F-3 ⑧ 同一条理由）。
                    // 话术走全项目那一个口径「接口未给」（MR-d 禁的就是"后端没有下发"这种替后端下结论的写法）。
                    item(key = "taskEmpty") {
                        SectionTitle("今日运营任务", subtitle = "任务段接口未给")
                    }
                }

                item(key = "pickupTitle") { SectionTitle("揽件任务", subtitle = "${vm.pickups.size} 单") }
                if (vm.pickups.isEmpty() && !vm.loading) {
                    // 空态走全站统一的 EmptyState（旧写法一行裸 Text，与交接页/消息页的长相不一致）
                    item(key = "pickupEmpty") {
                        EmptyState(title = "当前没有待揽收的货物", subtitle = "有新揽件时后端会把订单推进这个列表")
                    }
                }
                items(vm.pickups.size, key = { "pickup-${vm.pickups[it].orderId}" }) { i ->
                    val task = vm.pickups[i]
                    PickupCard(
                        task = task,
                        index = i,
                        loaded = vm.isLoaded(task),
                        delivered = vm.isDelivered(task),
                        busy = vm.busy,
                        onScanVerify = { scanMode = "VERIFY"; scanner.scan("扫描取件码核销") },
                        onManualVerify = { verifyTarget = task; code = "" },
                        onScanLoad = { scanMode = "LOAD"; scanner.scan("扫描装车（订单二维码 / 面单条码）") },
                        onScanDeliver = { scanMode = "DELIVER"; scanner.scan("扫描妥投") },
                        onCall = { callDriver(context, task.senderMobile) },
                    )
                }

                // 「扫码作业」独立版块已删：装车/妥投/核销三颗按钮过去在这里、班次卡里、每张揽件卡里
                // 各来一份（同一动作三套入口，误触与认知负担都翻倍）。现在收敛到**底部固定栏的「扫码作业」**
                // 一处，点开是大号三选一 —— 驾驶场景下多一次点击换来的是一颗不用瞄准的按钮。

                item(key = "more") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionTitle("更多入口")
                        BusCard(modifier = Modifier.fillMaxWidth()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                BusButton(
                                    text = "货物交接",
                                    icon = BusIcons.ship,
                                    variant = BusButtonVariant.CLAY,
                                    modifier = Modifier.weight(1f),
                                    onClick = { nav.navigate(Routes.HANDOVER) },
                                )
                                BusButton(
                                    text = "司机消息",
                                    icon = BusIcons.bell,
                                    variant = BusButtonVariant.OUTLINE,
                                    modifier = Modifier.weight(1f),
                                    onClick = { nav.navigate(Routes.DRIVER_MESSAGES) },
                                )
                            }
                        }
                        // 页尾保留显式刷新：下拉刷新是"顺手"，底部按钮是"我就是想刷一下"（且无障碍可达）
                        BusButton(
                            text = "刷新班次与任务",
                            variant = BusButtonVariant.OUTLINE,
                            onClick = { vm.load() },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    if (verifyTarget != null) {
        AlertDialog(
            onDismissRequest = { verifyTarget = null },
            title = { Text("核验取件码") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(
                        "${verifyTarget!!.goodsName} · ${verifyTarget!!.senderName}",
                        color = t.textSecondary,
                        fontSize = (13 * scale).sp,
                    )
                    Text(
                        "让寄件人报出 4 位取件码后手动输入；也可以直接用上方「取件核销」扫码，二者等效。",
                        color = t.textTertiary,
                        fontSize = (11.5 * scale).sp,
                    )
                    BusTextField(value = code, onValueChange = { code = it.filter { c -> c.isDigit() } }, label = "取件码", singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.verify(verifyTarget!!, code)
                    verifyTarget = null
                }) { Text("核验") }
            },
            dismissButton = { TextButton(onClick = { verifyTarget = null }) { Text("取消") } },
        )
    }

    // 「扫码作业」三选一：三颗大按钮代替原来散在三处的三颗小按钮。
    // 每颗都吃触控下限（驾驶模式 56dp），且动作本身仍是同一个扫码器、同一个 scanMode。
    if (scanSheet) {
        AlertDialog(
            onDismissRequest = { scanSheet = false },
            title = { Text("扫码作业") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    BusButton(
                        text = "扫码装车",
                        icon = BusIcons.qr,
                        enabled = !vm.busy,
                        onClick = { scanSheet = false; scanMode = "LOAD"; scanner.scan("扫描装车（订单二维码 / 面单条码）") },
                    )
                    BusButton(
                        text = "扫码妥投",
                        icon = BusIcons.checkCircle,
                        variant = BusButtonVariant.CLAY,
                        enabled = !vm.busy,
                        onClick = { scanSheet = false; scanMode = "DELIVER"; scanner.scan("扫描妥投") },
                    )
                    BusButton(
                        text = "取件核销",
                        icon = BusIcons.qr,
                        variant = BusButtonVariant.OUTLINE,
                        enabled = !vm.busy,
                        onClick = { scanSheet = false; scanMode = "VERIFY"; scanner.scan("扫描取件码核销") },
                    )
                    Text(
                        "货运散件与商城单装车会强制拍照留证；邮快件凭面单可免拍。相机不可用时点订单卡上的「核验码」手输。",
                        color = t.textTertiary,
                        fontSize = (11.5f * scale).sp,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { scanSheet = false }) { Text("取消") } },
        )
    }
}

/**
 * 司机工作台的固定底部操作栏。
 *
 * 为什么是"固定"而不是"卡片里的一颗按钮"：司机这一班的**唯一必须动作**（发车 → 到站 → 停靠完成）
 * 只有 4 个，而且顺序固定；把它钉在拇指区之后，司机不必先滚动去找 —— 这是本轮"人性化"的第一条。
 *
 * 分工：本栏只放**当前这一步**的主操作 + 「扫码作业」。班次细节（线路、时间、经停时间线）留在
 * [ShiftCard] 里，两者不重复任何一个按钮。
 */
@Composable
private fun WorkbenchActionBar(
    hasShift: Boolean,
    viewState: ShiftState?,
    busy: Boolean,
    hint: String?,
    onDepart: () -> Unit,
    onArrive: () -> Unit,
    onContinue: () -> Unit,
    onRefresh: () -> Unit,
    onOpenScanSheet: () -> Unit,
) {
    StickyActionBar(hint = hint) {
        if (!hasShift) {
            // 还没拉到班次（加载中 / 拉取失败 / 演示回落之前）：主操作位放「刷新」。
            // 这一支不能空着 —— 底部栏留一块死区比放一颗暂时用不上的按钮更糟。
            BusButton(
                text = "刷新班次",
                variant = BusButtonVariant.OUTLINE,
                icon = BusIcons.refresh,
                enabled = !busy,
                modifier = Modifier.weight(1f),
                onClick = onRefresh,
            )
        } else {
            when (viewState) {
                ShiftState.WAITING -> BusButton(
                    text = "确认发车",
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    onClick = onDepart,
                )
                ShiftState.RUNNING -> BusButton(
                    // 后端按司机声明受理到站，没有 GPS 门槛（审计 §32）
                    text = "确认到达",
                    enabled = !busy,
                    variant = BusButtonVariant.CLAY,
                    modifier = Modifier.weight(1f),
                    onClick = onArrive,
                )
                ShiftState.STOPPED -> BusButton(
                    // 停靠完成只推进本机导航目标，不重发 depart、也不改写后端班次状态
                    text = "停靠完成",
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    onClick = onContinue,
                )
                ShiftState.FINISHED -> BusButton(
                    text = "本班已完成",
                    enabled = false,
                    modifier = Modifier.weight(1f),
                    onClick = {},
                )
                null -> BusButton(
                    text = "刷新班次",
                    variant = BusButtonVariant.OUTLINE,
                    icon = BusIcons.refresh,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    onClick = onRefresh,
                )
            }
        }
        BusButton(
            text = "扫码作业",
            icon = BusIcons.qr,
            variant = BusButtonVariant.OUTLINE,
            enabled = !busy,
            modifier = Modifier.weight(1f),
            onClick = onOpenScanSheet,
        )
    }
}

/**
 * 底部栏上面那行提示：只放"当前这一步该干什么 / 还差多少路"，不放第二份状态。
 * 没有班次时返回 null（不占行高）—— 空字符串会让栏里凭空多出一条空白带。
 */
private fun workbenchBarHint(shift: ShiftTask?, viewState: ShiftState?, distanceText: String): String? {
    if (shift == null) return null
    return when (viewState) {
        ShiftState.WAITING -> "本班待发车：确认后开始向村民端推送到车进度"
        ShiftState.RUNNING -> distanceText
        ShiftState.STOPPED -> "已到站停靠（本机导航视图，班次后端仍为在途）：卸完货点「停靠完成」继续"
        ShiftState.FINISHED -> "本班已收口，位置上报已停止"
        null -> null
    }
}

// 回执条已抽到 `DriverBits.kt`（原来本页 / 我的路线 / 货物交接 / 异常上报各写一份同形实现，
// 样式已经漂移）。本文件不再私有一份，避免"改一处漏三处"。

/** 经停时间线默认露出的站数：再多一行就要划屏找作业区（设备实测 18 站占两屏） */
private const val TimelinePreview = 3

/**
 * 班次卡：本班的票据主券 + 经停时间线。**不再承载主操作按钮** ——
 * 「确认发车 / 确认到达 / 停靠完成」已提到页面级固定底部栏（[WorkbenchActionBar]），
 * 原因见那里的注释：旧版它被压在卡片内部、位于任务地图与十余行实时回执之下，首屏够不到。
 * 扫码入口同理，收敛到底部栏的「扫码作业」一处。
 */
@Composable
private fun ShiftCard(
    shift: ShiftTask,
    viewState: ShiftState,
    distanceText: String,
    stops: List<com.shanxiang.buspost.data.model.MissionStop>,
    targetStationId: Long,
) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    var stationsExpanded by remember { mutableStateOf(false) }
    val currentIdx = shift.stations.indexOfFirst { it.id == shift.currentStationId }
    // viewState 由调用方传入（底部操作栏用的是同一个值）：两处各算一遍就会在"停靠中"这类
    // 中间态上漂移 —— 出现"卡片说停靠、按钮说行驶"的矛盾。
    TicketCard(
        modifier = Modifier.fillMaxWidth(),
        serial = shift.shiftCode,
        // 本班件数在本屏只有一个真相源 loadedCount：CargoGauge 画的 "0 / 24 件" 与这里的值必须同数。
        // 旧写法读 shift.orderCount（班次 VO 无此键 ⇒ 恒 0）再塌成 "—"，于是同屏两个答案。
        stubValue = "${shift.loadedCount}",
        stubLabel = "件已装车",
        stubColor = when (viewState) {
            ShiftState.RUNNING -> t.primary
            ShiftState.STOPPED -> t.clay
            ShiftState.FINISHED -> t.statusDone
            ShiftState.WAITING -> t.statusPending
        },
    ) {
        Column(Modifier.weight(1f)) {
            Text(shift.lineName.ifEmpty { "本机班次" }, color = t.ink, fontSize = (16 * scale).sp, fontWeight = FontWeight.Bold)
            Text(
                listOf(shift.departTime, shift.arriveTime).filter { it.isNotEmpty() }.joinToString(" → ") + " 发车",
                color = t.textSecondary,
                fontSize = (12.5 * scale).sp,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(Modifier.padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // 「停靠中」不再读调用方的一个布尔（旧签名里的 docked）：viewState 已经是
                // "停靠作业优先于班次状态"的**唯一结论**，卡片再存一个 docked 就是同一个问题两个答案。
                BusTag(
                    if (viewState == ShiftState.STOPPED) "到站停靠（本机导航）" else shift.statusLabel,
                    tone = BusTone.PRIMARY,
                )
                if (shift.cargoKg > 0) BusTag("${formatPrice(shift.cargoKg)} kg", tone = BusTone.CLAY)
            }
            RoadLine(
                modifier = Modifier.padding(top = 10.dp),
                progress = if (shift.progress > 0f) shift.progress
                else if (shift.stations.size > 1 && currentIdx >= 0) currentIdx.toFloat() / (shift.stations.size - 1) else 0f,
            )
        }
    }

    BusCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (stops.isNotEmpty()) "调度任务点" else "经停站点",
                    color = t.ink, fontSize = (14 * scale).sp, fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                if (stops.isNotEmpty()) {
                    // 完成数只数后端 status==7 的点，本机不额外"+1"
                    BusTag("${stops.count { it.isDone }}/${stops.size} 已完成", tone = BusTone.CLAY)
                } else if (shift.stations.isNotEmpty()) {
                    BusTag("${currentIdx.coerceAtLeast(0) + 1}/${shift.stations.size}", tone = BusTone.CLAY)
                }
            }
            // ⑤ 任务点业务化：序号=后端 visitSequence、动作/件数/状态=stops 字段、配色=TaskItemStatusEnum。
            // 全量时间线的体量在设备上实测过：18 站要吃掉两整屏，工作台被经停站撑满、
            // 作业区与扫码被挤到第十屏。默认只画前 [TimelinePreview] 站，展开入口写清总数；
            // 折叠只影响"这一次画几行"，点的顺序、状态、件数一个都不改。
            val timeline = if (stops.isNotEmpty()) {
                MissionMarkers.timelineOf(stops, targetStationId)
            } else {
                shift.stations.mapIndexed { i, s ->
                    TimelineStop(
                        label = s.name,
                        time = if (s.plannedMinutes > 0) "+${s.plannedMinutes} 分" else "",
                        hint = if (s.orderCount > 0) "${s.orderCount} 件待处理" else "",
                        state = stopStateOf(i < currentIdx, i == currentIdx),
                    )
                }
            }
            if (timeline.isEmpty()) {
                Text("接口未给经停站", color = t.textTertiary, fontSize = (12 * scale).sp, modifier = Modifier.padding(top = 6.dp))
            } else {
                StationTimeline(
                    stops = if (stationsExpanded || timeline.size <= TimelinePreview) timeline
                    else timeline.take(TimelinePreview),
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (timeline.size > TimelinePreview) {
                    BusButton(
                        text = if (stationsExpanded) "收起，先看前 $TimelinePreview 站"
                        else "展开全部 ${timeline.size} 站",
                        small = true,
                        variant = BusButtonVariant.TEXT,
                        modifier = Modifier.padding(top = 2.dp),
                        onClick = { stationsExpanded = !stationsExpanded },
                    )
                }
                if (stops.isNotEmpty()) {
                    Text(
                        "顺序与状态均以后端回读为准；「确认到达」由你点，定位不参与放行",
                        color = t.textTertiary,
                        fontSize = (11 * scale).sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            // 卡片内**不再放操作按钮**：主操作在页面级固定底部栏（WorkbenchActionBar），扫码在其右邻。
            // 这里只留"这一班到哪了"的说明，让班次卡回归"看"而不是"按"。
            // 「跳过本站，不停靠」也一并撤下：它与底部栏的「停靠完成，继续行驶」是**同一个 handler**
            // （vm.continueToNextStation），并排两颗等于给同一个动作起两个名字。
            //
            // 远近说明：道路值来自后端，没有就直说；直线值一律带"不是道路里程"的标注，且不拦任何按钮。
            if (viewState == ShiftState.RUNNING || viewState == ShiftState.STOPPED) {
                Text(
                    distanceText,
                    color = t.textSecondary,
                    fontSize = (11.5 * scale).sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ReportCard(
    on: Boolean,
    geo: com.shanxiang.buspost.data.model.Geo?,
    reported: Int,
    waiting: String,
    intervalSec: Int,
    lastSuccessAt: Long,
    lastError: String,
    shiftId: Long,
    fixAtElapsedMs: Long,
    accuracyMeters: Double,
    provider: String,
    dropped: Int,
    shiftNote: String,
    locatorState: String,
    onToggle: (Boolean) -> Unit,
    onOpenNav: () -> Unit,
) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // 点龄走单调时钟：定位采集时刻是 elapsedRealtime 口径，拿墙钟去减会得出负数或几天的假年龄
    var elapsed by remember { mutableLongStateOf(Locator.elapsed()) }
    LaunchedEffect(on) {
        while (on) {
            now = System.currentTimeMillis()
            elapsed = Locator.elapsed()
            delay(5_000L)
        }
    }
    BusCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size((9 * scale).dp)
                        .clip(CircleShape)
                        .background(if (on) t.statusDone else t.lineDeep),
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("位置上报（前台服务）", color = t.ink, fontSize = (14 * scale).sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (on) "每 $intervalSec 秒向后端上传一次车辆位置，村民端到车提醒依赖它"
                        else "关闭后村民只能看到线路预计，不会收到实时到车提醒",
                        color = t.textTertiary,
                        fontSize = (11.5 * scale).sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                BusSwitch(checked = on, onCheckedChange = onToggle)
            }
            if (on) {
                BusDivider(Modifier.padding(vertical = 8.dp))
                InfoRow("已上报次数", "$reported")
                InfoRow("归属班次", if (shiftId > 0L) "#$shiftId" else shiftNote)
                InfoRow("最后一次成功", sinceText(lastSuccessAt, now))
                InfoRow("最新定位采样", sinceFixText(fixAtElapsedMs, elapsed))
                InfoRow(
                    "最新坐标",
                    geo?.let { "%.5f, %.5f（%s）".format(it.lat, it.lng, providerLabel(provider, accuracyMeters)) }
                        ?: waiting.ifEmpty { "等待定位信号" },
                )
                if (dropped > 0) InfoRow("未确认已丢弃", "$dropped 个定位点（失败期间被更新的点顶替，不补报旧点）")
                if (locatorState.isNotEmpty()) {
                    BusTag("定位源：$locatorState", tone = BusTone.WARN)
                }
                if (lastError.isNotEmpty()) {
                    BusTag("上报失败：$lastError", tone = BusTone.DANGER)
                }
                if (waiting.isNotEmpty()) {
                    BusTag("暂停上报：$waiting", tone = BusTone.WARN)
                }
            }
            // 旧屏是调试入口，正式包不显示这条跳转（本页下方就是正式导航面）
            if (BuildConfig.DEBUG) {
                BusButton(
                    text = "打开旧导航页（调试·非真实导航）",
                    variant = BusButtonVariant.OUTLINE,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 9.dp),
                    onClick = onOpenNav,
                )
            }
        }
    }
}

/** 上报新鲜度：司机要看得见"多久没传了"，而不是只有一个开关状态 */
private fun sinceText(at: Long, now: Long): String {
    if (at <= 0L) return "尚无成功上报"
    val sec = ((now - at) / 1000L).coerceAtLeast(0L)
    return when {
        sec < 60 -> "${sec} 秒前"
        sec < 3600 -> "${sec / 60} 分钟前"
        else -> "${sec / 3600} 小时前"
    }
}

/**
 * 定位新鲜度：与"最后一次成功上报"是两件事 —— 网络通但 GPS 断时，
 * 上报会成功发出去的是**旧点**，策略到 45 秒就停报，这里把停报阈值写可见。
 */
private fun sinceFixText(atElapsedMs: Long, nowElapsedMs: Long): String {
    if (atElapsedMs <= 0L) return "尚无定位采样"
    val sec = ((nowElapsedMs - atElapsedMs) / 1000L).coerceAtLeast(0L)
    val limit = LocationReportPolicy.MAX_FIX_AGE_MS / 1000L
    return when {
        sec > limit -> "$sec 秒前 · 已超过 ${limit}s 阈值，已停报"
        sec < 60 -> "${sec} 秒前"
        else -> "${sec / 60} 分钟前"
    }
}

/** 定位源与精度：车载 GPS 与网络定位差一个数量级，司机需要看得见用的是哪个 */
private fun providerLabel(provider: String, accuracyMeters: Double): String {
    val src = when {
        provider.equals("gps", true) -> "GPS"
        provider.equals("network", true) || provider.equals("fused", true) -> "网络定位"
        provider.isEmpty() -> "来源未知"
        else -> provider
    }
    return if (accuracyMeters > 0.0) "$src ±${accuracyMeters.toInt()}m" else src
}

@Composable
private fun PickupCard(
    task: PickupTask,
    index: Int,
    loaded: Boolean,
    delivered: Boolean,
    busy: Boolean,
    onScanVerify: () -> Unit,
    onManualVerify: () -> Unit,
    onScanLoad: () -> Unit,
    onScanDeliver: () -> Unit,
    onCall: () -> Unit,
) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    BusCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier
                        .size((34 * scale).dp)
                        .clip(RoundedCornerShape(BusRadius.m))
                        .background(if (task.fresh) t.clayLight else t.track),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (task.fresh) BusIcons.bolt else BusIcons.parcel,
                        contentDescription = null,
                        tint = if (task.fresh) t.clayDeep else t.textSecondary,
                        modifier = Modifier.size((17 * scale).dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        task.goodsName.ifEmpty { "货物" },
                        color = t.ink,
                        fontSize = (14.5 * scale).sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOf(task.senderName, task.stationName).filter { it.isNotEmpty() }.joinToString(" · "),
                        color = t.textSecondary,
                        fontSize = (12 * scale).sp,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (task.weightKg > 0) {
                        Text("${formatPrice(task.weightKg)} kg", color = t.price, fontSize = (13 * scale).sp, fontWeight = FontWeight.Bold)
                    }
                    if (task.deadline.isNotEmpty()) {
                        Text("截止 ${task.deadline}", color = t.statusWarn, fontSize = (11 * scale).sp, modifier = Modifier.padding(top = 3.dp))
                    }
                }
            }
            Row(Modifier.padding(top = 9.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (task.pickupCode.isNotEmpty()) BusTag("取件码 ${task.pickupCode}", tone = BusTone.GOLD)
                if (task.type.isNotEmpty()) BusTag(task.type)
                if (loaded) BusTag("已装车", tone = BusTone.PRIMARY)
                if (delivered) BusTag("已妥投", tone = BusTone.OK)
            }
            Row(Modifier.padding(top = 11.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!loaded) {
                    BusButton("扫码装车", small = true, enabled = !busy, modifier = Modifier.weight(1f), onClick = onScanLoad)
                    BusButton("核验码", small = true, variant = BusButtonVariant.OUTLINE, enabled = !busy, modifier = Modifier.weight(1f), onClick = onManualVerify)
                } else if (!delivered) {
                    BusButton("扫码妥投", small = true, enabled = !busy, modifier = Modifier.weight(1f), onClick = onScanDeliver)
                    BusButton("联系寄件人", small = true, variant = BusButtonVariant.TEXT, modifier = Modifier.weight(1f), onClick = onCall)
                } else {
                    BusButton("已完成", small = true, variant = BusButtonVariant.TEXT, enabled = false, modifier = Modifier.weight(1f), onClick = onCall)
                }
            }
            // 邮快件下行核销入口（小程序「取件核销」按钮）
            if (task.isMail && !delivered) {
                BusButton(
                    "扫码核销取件码",
                    small = true,
                    variant = BusButtonVariant.OUTLINE,
                    enabled = !busy,
                    modifier = Modifier.padding(top = 7.dp),
                    onClick = onScanVerify,
                )
            }
            SourceTag(if (task.orderNo.isNotEmpty()) task.orderNo else null, Modifier.padding(top = 8.dp))
        }
    }
}

private fun callDriver(context: Context, mobile: String) {
    if (mobile.isEmpty()) return
    runCatching {
        context.startActivity(
            android.content.Intent(
                android.content.Intent.ACTION_DIAL,
                android.net.Uri.parse("tel:$mobile"),
            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
