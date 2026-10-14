package com.shanxiang.buspost.ui.user

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shanxiang.buspost.core.AppState
import com.shanxiang.buspost.core.loc.Locator
import com.shanxiang.buspost.core.net.ApiResult
import com.shanxiang.buspost.core.net.safeApiCall
import com.shanxiang.buspost.core.ui.BusCard
import com.shanxiang.buspost.core.ui.BusButton
import com.shanxiang.buspost.core.ui.BusButtonVariant
import com.shanxiang.buspost.core.ui.BusIcons
import com.shanxiang.buspost.core.ui.BusPage
import com.shanxiang.buspost.core.ui.BusRadius
import com.shanxiang.buspost.core.ui.BusTag
import com.shanxiang.buspost.core.ui.BusTextField
import com.shanxiang.buspost.core.ui.BusTone
import com.shanxiang.buspost.core.ui.EmptyState
import com.shanxiang.buspost.core.ui.ErrorState
import com.shanxiang.buspost.core.ui.LocalBusFontScale
import com.shanxiang.buspost.core.ui.LocalBusTokens
import com.shanxiang.buspost.core.ui.RoadLine
import com.shanxiang.buspost.core.ui.SectionTitle
import com.shanxiang.buspost.core.ui.SkeletonList
import com.shanxiang.buspost.core.ui.SourceTag
import com.shanxiang.buspost.core.ui.StationTimeline
import com.shanxiang.buspost.core.ui.TicketCard
import com.shanxiang.buspost.core.ui.TimelineStop
import com.shanxiang.buspost.data.model.BusLine
import com.shanxiang.buspost.data.model.BusVehicle
import com.shanxiang.buspost.data.model.byUserDistance
import com.shanxiang.buspost.data.model.Geo
import com.shanxiang.buspost.data.model.GeoMath
import com.shanxiang.buspost.data.model.NearbyResult
import com.shanxiang.buspost.data.model.ParcelTrace
import com.shanxiang.buspost.data.model.ProductOrder
import com.shanxiang.buspost.data.model.ProductOrderStatus
import com.shanxiang.buspost.data.model.SendOrder
import com.shanxiang.buspost.data.model.FreightOrderStatus
import com.shanxiang.buspost.data.model.Station
import com.shanxiang.buspost.data.model.VehicleStatus
import com.shanxiang.buspost.core.ui.StopState
import com.shanxiang.buspost.core.map.polylineReasonOf
import com.shanxiang.buspost.core.map.polylineSubtitleOf
import com.shanxiang.buspost.core.map.PolylinePhase
import com.shanxiang.buspost.core.map.shouldAskPolyline
import com.shanxiang.buspost.data.store.Prefs
import com.shanxiang.buspost.data.repo.Repo
import com.shanxiang.buspost.nav.Routes
import com.shanxiang.buspost.ui.common.BackTopBar
import com.shanxiang.buspost.ui.common.BusMap
import com.shanxiang.buspost.ui.common.DemoNote
import com.shanxiang.buspost.ui.common.LocalNavController
import com.shanxiang.buspost.ui.common.MapMarker
import com.shanxiang.buspost.ui.common.MapPath
import com.shanxiang.buspost.ui.common.nearestWalkTarget
import com.shanxiang.buspost.ui.common.openWalkRoute
import com.shanxiang.buspost.ui.common.RouteMap
import com.shanxiang.buspost.ui.common.ScreenViewModel

/**
 * 实时公交页定位提示的**唯一出口**（纯函数，JVM 用例可断言）。
 *
 * 为什么单独拎出来：旧写法把"没授权"和"授权了但这一次没定到位"压成同一句话
 * （`geo == null → "没拿到定位，展示全部线路。去系统设置里允许定位后刷新更准"`），
 * 于是用户在系统里明明已经允许了，界面还在教他去开权限 —— 这正是 §51 在册的
 * "定位三态被折叠成 me==null"。三态各有各的下一步动作，就不许共用一个号。
 */
internal fun locationHintFor(granted: Boolean, fix: com.shanxiang.buspost.data.model.Geo?): String = when {
    !granted -> "未允许定位：地图不显示你的位置，已展示全部线路。点右上角可再次申请。"
    fix == null -> "已允许定位，这次没定到（室内/冷启动常见）：先展示全部线路，稍后自动重试。"
    else -> "已按你的位置排序"
}

/**
 * 页头"位置口径"标签的唯一映射点（§8 同族：页面不许各自拼码）。
 *
 * 关键那一格是**混档**：现网一趟车里有 GPS 上报的只有本平台派出去的那几辆，其余是时刻表推算
 * （后端优先级 `REAL > OFFLINE`，另有一层 SIMULATED 插值；`VehicleLocationProvider.java:16-20`）。
 * 旧写法 `if (real) "实时位置"` 用的是整页 OR ⇒ 只要有一辆实时，整页顶盖都写成"实时位置"，
 * 用户就把推算的那几辆也按实时读 —— 卡片级文案是对的（`BusVehicle.sourceText`），页头在说大话。
 */
internal fun positionSourceTag(realCount: Int, otherCount: Int, demo: Boolean): Pair<String, BusTone> = when {
    realCount > 0 && otherCount == 0 -> "实时位置" to BusTone.OK
    realCount > 0 -> "$realCount 辆实时 · 其余按时刻推算" to BusTone.WARN
    demo -> "演示线路" to BusTone.WARN
    otherCount > 0 -> "班次时刻（无实时位置）" to BusTone.WARN
    // 一辆车都没显示时不许留个"班次时刻"暗示有时刻表：那是"没有可显示的位置"
    else -> "暂无车辆位置" to BusTone.NEUTRAL
}

class BusViewModel : ScreenViewModel() {
    var nearby by mutableStateOf<NearbyResult?>(null)
    var lines by mutableStateOf<List<BusLine>>(emptyList())
    /**
     * 线路列表这一轮的失败原因（`Repo.busLines` 的 `Feed.error`）：收藏那一栏要拿它分两格——
     * "没拿到列表所以显示不出来"与"拿到了但那条线路确实不在了"是两件事
     * （旧写法直接把 `lf.items` 赋给 lines 就把失败洗成了空列表，见 FavoriteLines.kt 顶部第 3 条）。
     */
    var linesError by mutableStateOf<String?>(null)
    var selected by mutableStateOf<BusLine?>(null)
    var polyline by mutableStateOf<List<Geo>>(emptyList())
    /** 线路搜索关键词（对标车来了/高德的搜索框：按线路名过滤） */
    var lineQuery by mutableStateOf("")

    /** 按搜索词过滤后的线路列表（空串 = 不过滤） */
    val filteredLines: List<BusLine>
        get() = if (lineQuery.isBlank()) lines
        else lines.filter { it.name.contains(lineQuery, ignoreCase = true) }

    /** 用户正在运输中的商品订单（商城购买） */
    var shippingProductOrders by mutableStateOf<List<ProductOrder>>(emptyList())
        private set

    /** 用户正在运输中的寄货单 */
    var shippingSendOrders by mutableStateOf<List<SendOrder>>(emptyList())
        private set

    /** 运输中订单关联的车辆信息（车牌 → 订单摘要） */
    val myTransportingVehicles: Map<String, List<String>>
        get() {
            val result = mutableMapOf<String, MutableList<String>>()
            shippingProductOrders.forEach { order ->
                if (order.vehiclePlate.isNotEmpty()) {
                    result.getOrPut(order.vehiclePlate) { mutableListOf() }
                        .add("商城·${order.items.firstOrNull()?.name ?: "商品"}")
                }
            }
            shippingSendOrders.forEach { order ->
                if (order.vehiclePlate.isNotEmpty()) {
                    result.getOrPut(order.vehiclePlate) { mutableListOf() }
                        .add("寄货·${order.goodsName}")
                }
            }
            return result
        }

    /** 判断某辆车是否在运送用户的商品 */
    fun isMyVehicle(vehicle: BusVehicle): Boolean =
        vehicle.plate.isNotEmpty() && myTransportingVehicles.containsKey(vehicle.plate)

    /** 获取车辆运送的用户商品描述 */
    fun myGoodsDescription(vehicle: BusVehicle): String =
        myTransportingVehicles[vehicle.plate]?.joinToString("、") ?: ""

    /**
     * "这次没问到折线"的说法：非 null 是失败分类文案；null 有两种，到底是哪一种由 [polylinePhase] 说
     * （成功且后端回空 / 还没去问 —— 这两件事**必须**分开，见 F-3 ⑤）。
     *
     * 为什么不复用 [ScreenViewModel.error]：那个字段被 nearby / 线路列表 / 轮询共用，
     * 拿它当折线的病因，就会把"车辆接口挂了"显示成"这条线路没有轨迹"——把一个假话换成另一个假话（F-2 ⑧）。
     */
    var polylineUnconfirmed by mutableStateOf<String?>(null)

    /**
     * 折线问到哪一步了（F-3 ⑤）：**三态不是布尔** —— "没问""在问""问过且后端回了空"是三件事。
     * 两格文案会把第一件说成第三件，首屏自动选中那条线路时念的就是这句假话。
     */
    internal var polylinePhase by mutableStateOf(PolylinePhase.NotAsked)
    var me by mutableStateOf<Geo?>(null)
    var locationHint by mutableStateOf("")
    var lastRefresh by mutableStateOf("")

    /** 详情页要直达的那条线路编号：选线由 [load] 在列表**落地之后**按它做（见 [openDetail]） */
    var detailTarget by mutableStateOf<String?>(null)
        private set

    /**
     * 详情页那一格的四态（取数中 / 取数失败 / **取成功但这条不在这批列表里** / 拿到了）。
     * 判断本体是纯函数 [detailSlotOf]，优先级由 JVM 用例钉住：旧写法把第三格也念成"线路信息获取失败"，
     * 而那时后端明明给了完整列表 —— 与 F-3 ⑤ 收口的"把没问说成没有"同族（F-2 ⑧ 登记那一条）。
     */
    fun detailSlot(): DetailSlot = detailSlotOf(detailTarget, lines, selected?.id, loading, error)

    /** 请求序号：15s 轮询与手动刷新会并发，旧响应不得覆盖新结果（小程序 index.js:209-276 同法） */
    private var reqSeq = 0

    /**
     * 折线请求序号：与 [reqSeq] 分开的第二个计数器。
     *
     * 轮询是 15s 一次、改点线路是用户随手一下，两条并发时"最新的附近车辆"和"最新选中的线路"
     * 不是同一件事，共用一个号会让一次刷新把在飞的折线判成过期。
     * 要它的理由只有一个：`/line-polyline` 是按 `routeId` 按需查询，迟到的 A 线几何若写回当前页，
     * 画在 B 线画面上的就是一条**属于另一条线路的真实折线**（小程序 `pages/bus/index.js:339` 同法，
     * 假路线是红线，不是文案问题）。
     */
    private var polylineSeq = 0

    /**
     * 运行时定位授权。用户端以前**从没申请过**（`RequestPermission` 只出现在司机端工作台），
     * 而未授权时高德既不会有蓝点、`Locator.once` 也拿不到点 ⇒ "不显示我的位置"是两件事叠出来的。
     */
    var locationGranted by mutableStateOf(false)

    /** 三态提示的唯一出口（纯函数 [locationHintFor]，JVM 用例可断言） */
    fun onLocationPermission(granted: Boolean) {
        locationGranted = granted
        locationHint = locationHintFor(granted, me)
    }

    fun load(context: android.content.Context, silent: Boolean = false) {
        val mySeq = ++reqSeq
        work(reset = !silent) {
            // 半径按**这次定位的精度**收：`nearbyRadius(0.0)` 等于永远问 15km，
            // 真机反馈"附近公交把别处的线路也算进来"在这一页是同一个病根（首页那一处见 HomeScreen.fetchAll）
            val fix = Locator.onceFix(context)
            val geo = fix.geo
            me = geo
            if (!silent) {
                locationHint = locationHintFor(locationGranted, geo)
            }
            val radius = Repo.nearbyRadius(fix.accuracyMeters)
            val one = Repo.nearbyBuses(geo, radius)
            if (mySeq != reqSeq) return@work
            nearby = one.value
            val lf = Repo.busLines(geo, radius)
            if (mySeq != reqSeq) return@work
            lines = lf.items
            linesError = lf.error
            val target = detailTarget
            if (target != null) {
                // 直达：只能由这批刚落地的 lines 回答"有没有这条线路"，选不中就把 selected 清空、
                // 让页面走 DetailSlot.NotInList 那一格（不自动兜到第一条，那等于把用户的直达变成另一条线路的详情）
                if (selected?.id != target) {
                    selected = lf.items.firstOrNull { it.id == target }
                    if (selected != null && shouldAskPolyline(target, silent)) loadPolyline()
                }
            } else if (selected == null || selected?.id?.isEmpty() == true) {
                selected = one.value?.lines?.firstOrNull() ?: lf.items.firstOrNull()
                // 自动选中之后必须跟着问一次折线：不问，副标题就在**问过之前**念出"后端未回传该线路折线"
                // ——把"我还没问"说成"后端说没有"，与 F-2 ⑧ 修掉的那句同族（F-3 ⑤ 收口 D-042 在册那条）。
                if (shouldAskPolyline(selected?.id, silent)) loadPolyline()
            }
            mergeDemo(one.demo, lf.demo)
            // 静默轮询失败保留旧数据 + 轻量错误条，不清空已看到的内容
            if (!silent) error = one.error ?: lf.error
            lastRefresh = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.CHINA).format(java.util.Date())
        }
    }

    /** 加载用户正在运输中的订单（商城 + 寄货），用于在地图上高亮运送用户商品的车辆 */
    fun loadMyShippingOrders() {
        if (!AppState.hasUserSession) return
        work(reset = false) {
            // 这两个读接口自己就回 `Feed(error = …)`，并且**在内部把取消单独穿透**（`Repo.kt:565`/`:590`）。
            // 旧写法在外面又套了一层 `runCatching`：那一层收下的正是被穿透上来的 CancellationException
            // ——换页/销毁时"取消"会被读成一次普通失败（RC-b 实测点名的那两处，§三十一 的口径）。
            // 这一格只是地图上的高亮装饰：拿不到就维持原样，不清空司机已经看到的东西。
            val productFeed = Repo.productOrders(pageNo = 1, pageSize = 50, status = 2)
            if (productFeed.error == null) {
                shippingProductOrders = productFeed.items.filter { it.vehiclePlate.isNotEmpty() }
            }
            val sendFeed = Repo.sendOrders(pageNo = 1, pageSize = 50, status = 4)
            if (sendFeed.error == null) {
                shippingSendOrders = sendFeed.items.filter { it.vehiclePlate.isNotEmpty() }
            }
        }
    }

    /**
     * 详情页入口：记下要直达的那条线，再取数 —— **选线由 [load] 在列表落地之后做**。
     * 旧写法是 `load(context)` 之后紧接着 `lines.firstOrNull { it.id == routeId }`，而 `load` 的函数体在
     * `viewModelScope.launch` 里（`ScreenViewModel.kt` 的 `work`），那一句读到的是还没被写过的旧列表，
     * 于是"直达某条线路"永远落在首屏自动选中的那一条上（在册缺陷 D-⑤-1，与 F-3 ⑤ 修掉的首屏那处同形状）。
     */
    fun openDetail(context: android.content.Context, routeId: String) {
        detailTarget = routeId
        if (selected?.id != routeId) load(context)
    }

    /** 运营时段态：off 非运营 / empty 有站无车 / error 接口失败 / data 有车 */
    fun state(): String {
        // "没拿到数据"不等于"接口失败"：失败必须有真实原因在册才允许报 error。
        // 真机上"每次点进实时公交都显示网络不好、点重新加载才有数据"的病根是并发的
        // `loadMyShippingOrders()` 走 `work(reset = false)`，基类在 finally 里把 loading 抢先写成 false
        // （`ScreenViewModel.kt:42`），而主请求还在飞 ⇒ 这一刻既无数据也无失败原因，
        // 正确的读法是"还在加载"，不是把没发生过的事说成发生过。
        val r = nearby ?: return if (loading || error == null) "loading" else "error"
        if (error != null && r.vehicles.isEmpty() && lines.isEmpty()) return "error"
        // 只有后端真给 false 才算非运营（F-2 §四）：缺键走 empty 那一支，与小程序 index.js:554 同口径；
        // 再叠一层"必须拿到服务时段"，服务区外的空报文就不再被说成"不在运营时间"
        if (r.isOffServiceAttested && r.vehicles.isEmpty()) return "off"
        if (r.vehicles.isEmpty() && lines.isEmpty() && r.stations.isEmpty()) return "empty"
        return "data"
    }

    fun pick(line: BusLine) {
        // 再次点击同一条线路 = 取消选中（小程序同交互）
        if (selected?.id == line.id) {
            selected = null
            polyline = emptyList()
            polylineUnconfirmed = null
            polylinePhase = PolylinePhase.NotAsked
            return
        }
        selected = line
        polyline = emptyList()
        polylineUnconfirmed = null
        polylinePhase = PolylinePhase.NotAsked
        loadPolyline()
    }

    private fun loadPolyline() = work(reset = false) {
        val id = selected?.id ?: return@work
        if (id.isEmpty()) return@work
        val mySeq = ++polylineSeq
        polylinePhase = PolylinePhase.Asking
        try {
            // Repo.linePolyline 现在会抛（F-2 ⑧：失败不再被洗成空列表）。收成分类文案而不是让它冒到 work{}，
            // 是因为共享的 vm.error 会被别的接口覆盖，页面需要知道"只有折线这次没问到"。
            val result = safeApiCall { Repo.linePolyline(id) }
            // 两个条件缺一不可：序号管"这条响应已经不是最新一次请求"，编号管"它问的不是当前选中那条线"
            // （取消选中时序号没被新的请求顶掉，只有编号这条拦得住迟到的几何）。
            if (mySeq != polylineSeq || selected?.id != id) return@work
            polyline = result.getOrNull().orEmpty()
            polylineUnconfirmed = when (result) {
                is ApiResult.Success -> null
                is ApiResult.BusinessError -> "后端业务码 ${result.code}：${result.message}"
                is ApiResult.NetworkError -> result.message
                is ApiResult.Unauthorized -> result.message
            }
            polylinePhase = PolylinePhase.Asked
        } catch (e: kotlinx.coroutines.CancellationException) {
            // 取消**不是失败**，是"这次没问完"：相位必须退回 NotAsked，
            // 否则换页回来（ViewModel 还活着）屏幕上会一直挂着"正在向后端要折线…"那句谎。
            if (mySeq == polylineSeq) polylinePhase = PolylinePhase.NotAsked
            throw e
        }
    }

    /** 「暂无真实路线」的重试入口：只重新拉后端折线，拿不到就继续显示不可用 */
    fun replan() = loadPolyline()

    /**
     * 折线缺失处的措辞：三态（没问 / 在问 / 问过）与「后端说没有」「这次没问到」分开讲，
     * 三格都不画站点连线。`whenEmpty` 由页面自己给，保留两处原文案（F-2 ⑰ 不重排 UI）。
     *
     * 优先级写在 [polylineReasonOf] 里、由 JVM 用例钉住：本屏**没问过**之前，
     * "后端未回传"那句不许出口（F-2 ⑧ 登记、F-3 ⑤ 收口的就是这句）。
     */
    fun polylineReason(whenEmpty: String): String = polylineReasonOf(polylinePhase, polylineUnconfirmed, whenEmpty)

    /** 折线这次是"没问到""还在问""还没去问"还是"后端回空"：四个口径分开，不能一律写"后端未回传" */
    fun polylineSubtitle(): String = polylineSubtitleOf(polylinePhase, polyline.size, polylineUnconfirmed)

    fun vehiclesOf(line: BusLine?): List<BusVehicle> {
        val v = line?.vehicles.orEmpty()
        return if (v.isNotEmpty()) v else nearby?.vehicles.orEmpty()
    }

    /**
     * 车辆卡「跳线路详情」的线路编号。真实车辆 VO 里**没有** `routeId`
     * （`AppBusRespVO` 19 个字段、`AppBusNearbyRespVO.NearbyBus` 20 个字段都只有 `routeName`，2026-09-29 逐字段核对），
     * 所以只能按线路名回查 `/bus/lines` 的线路编号；查不到就返回空串，页面据此不给可点态（审计 §69）。
     */
    fun lineIdOf(v: BusVehicle): String =
        lines.firstOrNull { it.name.isNotEmpty() && it.name == v.lineName }?.id?.ifEmpty { null }
            ?: v.routeId
}

@Composable
fun BusScreen(vm: BusViewModel = viewModel()) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val nav = LocalNavController.current
    val context = LocalContext.current
    // 附近线路展开态：默认最多 6 条（对齐小程序 toggleAllLines）
    var showAllLines by remember { mutableStateOf(false) }
    // 高德地图不可用时的回退标记（与 DriverMissionMap 同模式：onUnavailable 非 @Composable，
    // 不能在里面直接调 RouteMap，改用状态变量让外层 if/else 切换）
    var mapFailed by remember { mutableStateOf(false) }
    // 收藏状态变化后触发重组（Prefs 不是 State，需要手动驱动 UI 刷新）
    var favTick by remember { mutableStateOf(0) }

    // 运行时定位授权 —— 用户端以前**从没申请过**（`RequestPermission` 只在司机端工作台出现），
    // 而未授权时高德画不出蓝点、`Locator.once` 也拿不到点 ⇒ "地图不显示我的位置"是两件事叠出来的。
    // 进页问一次；被拒不追弹，改由 locationHintFor 说实话（"未允许定位"），线路照常给全。
    val askLocation = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        vm.onLocationPermission(granted)
        if (granted && vm.me == null) vm.load(context)
    }
    LaunchedEffect(Unit) {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_FINE_LOCATION,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        vm.locationGranted = granted
        if (!granted) askLocation.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
    }

    LaunchedEffect(Unit) {
        if (vm.nearby == null && vm.lines.isEmpty()) vm.load(context)
    }

    // 登录状态变化时重新加载运输订单（游客→登录后能看到运送自己商品的车辆）
    LaunchedEffect(AppState.hasUserSession) {
        vm.loadMyShippingOrders()
    }

    // 15s 静默轮询（对齐小程序 pages/bus/index.js:113-127）：失败不弹提示、保留旧数据；
    // 页面离开自动取消（LaunchedEffect 随组合销毁），不需要手动清定时器。
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(15_000)
            vm.load(context, silent = true)
        }
    }

    // 页头口径只描述**当前显示的这几辆车**（旧写法是整批 OR，见 positionSourceTag）
    val shownVehicles = vm.vehiclesOf(vm.selected)
    val realVehicles = shownVehicles.count { it.isReal }
    val (sourceTag, sourceTone) = positionSourceTag(
        realVehicles, shownVehicles.size - realVehicles, vm.demo,
    )

    // 高德风格：地图为主视图 + 底部详情面板
    Column(Modifier.fillMaxSize().background(t.paper)) {
        // 顶部栏：标题 + 刷新
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("实时公交", color = t.ink, fontSize = (20 * scale).sp, fontWeight = FontWeight.Black)
                Text(
                    vm.locationHint.ifEmpty { "正在定位…" },
                    color = t.textSecondary,
                    fontSize = (11 * scale).sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (vm.lastRefresh.isNotEmpty()) {
                Text(
                    "${vm.lastRefresh} 更新",
                    color = t.textTertiary,
                    fontSize = (10 * scale).sp,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            Icon(
                BusIcons.refresh, null, tint = t.primary,
                modifier = Modifier.size(22.dp).clickable { vm.load(context) },
            )
        }

        // 搜索框
        BusCard(
            padding = PaddingValues(6.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            BusTextField(
                value = vm.lineQuery,
                onValueChange = { vm.lineQuery = it },
                placeholder = "搜索线路名称",
                icon = BusIcons.search,
                trailing = {
                    if (vm.lineQuery.isNotEmpty()) {
                        Icon(
                            BusIcons.close, null,
                            tint = t.textTertiary,
                            modifier = Modifier.size(18.dp).clickable { vm.lineQuery = "" },
                        )
                    }
                },
            )
        }

        // 状态标签行（对标高德：实时状态 + 运营时段 + 站点数 + 线路数）
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            BusTag(sourceTag, tone = sourceTone)
            // 运营时段（对标高德/车来了：首末班时间）
            vm.nearby?.serviceWindowText?.takeIf { it.isNotEmpty() }?.let {
                BusTag("运营 $it", tone = BusTone.NEUTRAL)
            }
            if (vm.nearby?.stations?.isNotEmpty() == true) {
                BusTag("附近 ${vm.nearby!!.stations.size} 个站点", tone = BusTone.NEUTRAL)
            }
            if (vm.nearby != null) {
                BusTag("${vm.lines.size} 条线路", tone = BusTone.PRIMARY)
            }
        }

        // 非运营时段提示卡（对标高德：明确告知用户何时可乘车）
        if (vm.state() == "off" && vm.nearby != null) {
            BusCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                warm = true,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(BusIcons.clock, null, tint = t.goldDeep, modifier = Modifier.size(20.dp))
                    Column(Modifier.padding(start = 10.dp).weight(1f)) {
                        Text(
                            "当前不在运营时段",
                            color = t.ink,
                            fontSize = (13 * scale).sp,
                            fontWeight = FontWeight.Bold,
                        )
                        val serviceTime = vm.nearby?.serviceWindowText
                        val nextDeparture = vm.nearby?.nextDepartureTime
                        if (!serviceTime.isNullOrEmpty()) {
                            Text(
                                "运营时间：$serviceTime",
                                color = t.textSecondary,
                                fontSize = (11 * scale).sp,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        if (!nextDeparture.isNullOrEmpty()) {
                            Text(
                                "下一班发车：$nextDeparture",
                                color = t.primaryOnLight,
                                fontSize = (11 * scale).sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
            }
        }

        DemoNote(vm.demo, text = "该区域没有开通实时公交，下面是客货邮班车的演示线路与时刻。")

        // 地图区域（对标高德：地图占主视图）
        when (vm.state()) {
            "loading" -> Box(Modifier.fillMaxWidth().height(300.dp)) { SkeletonList(3) }

            "off" -> Box(Modifier.fillMaxWidth().height(300.dp)) {
                EmptyState(
                    title = "当前不在运营时间",
                    subtitle = listOfNotNull(
                        vm.nearby?.serviceWindowText?.takeIf { it.isNotEmpty() }?.let { "服务时段 $it" },
                        vm.nearby?.nextDepartureTime?.takeIf { it.isNotEmpty() }?.let { "下一班 $it 发车" },
                    ).joinToString("；").ifEmpty { "附近暂无运营中的班次" },
                    icon = BusIcons.clock,
                )
            }

            "empty" -> Box(Modifier.fillMaxWidth().height(300.dp)) {
                EmptyState(
                    title = "附近暂无公交线路",
                    subtitle = "当前暂无实时车辆数据",
                    icon = BusIcons.bus,
                )
            }

            "error" -> Box(Modifier.fillMaxWidth().height(300.dp)) {
                EmptyState(
                    title = "实时公交暂时不可用",
                    subtitle = "网络不太好，稍后再试",
                    icon = BusIcons.info,
                    actionText = "重新加载",
                    onAction = { vm.load(context) },
                )
            }

            else -> {
                // 高德风格地图：线路叠加 + 车辆标记 + 站点标记
                val line = vm.selected
                val markers = buildList {
                    // 我的位置
                    vm.me?.let { add(MapMarker(it, "我的位置", MapMarker.KIND_ME)) }
                    // 站点
                    (line?.stations ?: vm.nearby?.stations ?: emptyList()).forEach { s ->
                        add(MapMarker(s.geo, s.name, MapMarker.KIND_STOP, "站点"))
                    }
                    // 车辆（高亮运送用户商品的车辆）
                    vm.vehiclesOf(line).forEach { v ->
                        v.geo?.let { geo ->
                            val isMyVehicle = vm.isMyVehicle(v)
                            add(
                                MapMarker(
                                    geo,
                                    v.lineName.ifEmpty { "班车" },
                                    if (v.isReal) MapMarker.KIND_BUS_REAL else MapMarker.KIND_BUS_SIM,
                                    buildString {
                                        append(v.plate.ifEmpty { "待派车" })
                                        if (isMyVehicle) append(" · 运送您的商品")
                                    },
                                ),
                            )
                        }
                    }
                }

                val paths = if (vm.polyline.size >= 2) {
                    listOf(MapPath(vm.polyline, MapPath.ROLE_MAIN, source = "amap"))
                } else emptyList()

                // 切线路时重置地图失败标记，让新高德有机会重新尝试
                LaunchedEffect(line?.id) { mapFailed = false }

                // 折线状态这一句必须留在**主路径**上（F-3 ⑤ / 判据 SA-e、SA-e2 的口径同源）。
                // 7d8b819 把地图上方的 `SectionTitle(subtitle = vm.polylineSubtitle())` 撤成"地图主视图"时，
                // 连带删掉了这句唯一出口：此后只有"高德失败转自绘 RouteMap"那一支还带 reason，
                // 于是"还没去问 / 正在问 / 这次没问到 / 后端回了空"四种情况在主路径上又长成同一个样子。
                SectionTitle(
                    line?.name?.ifEmpty { null } ?: "线路地图",
                    subtitle = vm.polylineSubtitle(),
                )

                if (mapFailed) {
                    // 高德不可用时回退到自绘地图（与 DriverMissionMap 同模式）
                    RouteMap(
                        polyline = vm.polyline,
                        stations = line?.stations ?: vm.nearby?.stations ?: emptyList(),
                        vehicles = vm.vehiclesOf(line),
                        me = vm.me,
                        height = 320.dp,
                        reason = vm.polylineReason("地图服务不可用"),
                    )
                } else {
                    BusMap(
                        markers = markers,
                        paths = paths,
                        modifier = Modifier.fillMaxWidth().height(320.dp),
                        fitAll = true,
                        // 蓝点只在真拿到授权后开：没授权时开图层等于承诺一个永远不来的位置
                        showMyLocation = vm.locationGranted,
                        onUnavailable = { mapFailed = true },
                    )
                }
            }
        }

        // 底部详情面板（对标高德：路线信息 + ETA + 车辆列表）
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            when (vm.state()) {
                "loading", "off", "empty", "error" -> Unit // 已在地图区域显示

                else -> {
                    // 线路选择 chip（横滑）
                    if (vm.filteredLines.size > 1) {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            vm.filteredLines.take(8).forEach { l ->
                                val on = l.id == vm.selected?.id
                                Column(
                                    Modifier
                                        .clip(RoundedCornerShape(BusRadius.m))
                                        .background(if (on) t.primaryLight else t.card)
                                        .clickable { vm.pick(l) }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                ) {
                                    Text(
                                        l.name.ifEmpty { "未命名线路" },
                                        color = if (on) t.primaryOnLight else t.ink,
                                        fontSize = (12 * scale).sp,
                                        fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    // 收藏线路（快速访问）：判定与文案出处都在 FavoriteLines.kt，这里只渲染。
                    // ⚠ 旧写法是 `if (favLines.isNotEmpty())`——线路列表取失败时这一栏整块消失，
                    //   用户被引导成"我没有收藏"，而本机明明记着 id。现在按"收藏了几条 / 显示了几条"两格走。
                    val favIds = Prefs.favoriteLineIds
                    val favLines = vm.lines.filter { it.id in favIds }
                    if (favoriteRowVisible(favIds.size)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                BusIcons.star,
                                null,
                                tint = t.goldDeep,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                " " + favoriteRowTitle(favIds.size),
                                color = t.textSecondary,
                                fontSize = (11 * scale).sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        if (favLines.isNotEmpty()) {
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                favLines.forEach { fl ->
                                    val on = fl.id == vm.selected?.id
                                    Column(
                                        Modifier
                                            .clip(RoundedCornerShape(BusRadius.m))
                                            .background(if (on) t.goldLight else t.card)
                                            .clickable { vm.pick(fl) }
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                fl.name.ifEmpty { "未命名线路" },
                                                color = if (on) t.goldDeep else t.ink,
                                                fontSize = (11 * scale).sp,
                                                fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        // 一条都没解析出来时，未命中那一格就是这一栏唯一的内容（不许静默消失）
                        favoriteUnresolvedText(favIds.size, favLines.size, vm.linesError)?.let {
                            Text(
                                it,
                                color = t.textTertiary,
                                fontSize = (11 * scale).sp,
                                modifier = Modifier.padding(top = 5.dp),
                            )
                        }
                        Text(
                            " " + FAVORITE_LOCAL_NOTE,
                            color = t.textTertiary,
                            fontSize = (10.5 * scale).sp,
                            modifier = Modifier.padding(top = 5.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                    } else {
                        favoriteEmptyText(favIds.size)?.let {
                            Text(
                                " " + it,
                                color = t.textTertiary,
                                fontSize = (10.5 * scale).sp,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                    }

                    // 选中线路的 ETA 卡片（对标高德：大数字 ETA + 方向）
                    val line = vm.selected
                    val vehicles = vm.vehiclesOf(line).byUserDistance()
                    if (vehicles.isNotEmpty()) {
                        val nearest = vehicles.first()
                        val lineId = line?.id ?: ""
                        val isFav = lineId.isNotEmpty() && Prefs.isFavoriteLine(lineId)
                        BusCard(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            line?.name?.ifEmpty { null } ?: "线路",
                                            color = t.ink,
                                            fontSize = (18 * scale).sp,
                                            fontWeight = FontWeight.Black,
                                        )
                                        // 收藏按钮（星标）
                                        if (lineId.isNotEmpty()) {
                                            Icon(
                                                BusIcons.star,
                                                if (isFav) "已收藏" else "收藏",
                                                tint = if (isFav) t.goldDeep else t.textTertiary,
                                                modifier = Modifier
                                                    .padding(start = 8.dp)
                                                    .size((20 * scale).dp)
                                                    .clickable {
                                                        Prefs.toggleFavoriteLine(lineId)
                                                        favTick++
                                                    },
                                            )
                                        }
                                    }
                                    // 方向显示（对标高德/车来了："开往XX方向"）
                                    Text(
                                        "开往 ${line?.to ?: "终点站"} 方向",
                                        color = t.textSecondary,
                                        fontSize = (12 * scale).sp,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                                // ETA 大数字
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        nearest.etaValueText,
                                        color = if (nearest.etaMinutes != null && nearest.etaMinutes <= 5) t.danger else t.primary,
                                        fontSize = (28 * scale).sp,
                                        fontWeight = FontWeight.Black,
                                    )
                                    Text(
                                        nearest.etaUnitText,
                                        color = t.textSecondary,
                                        fontSize = (11 * scale).sp,
                                    )
                                }
                            }
                            // 多班车倒计时条（对标高德：显示接下来 2-3 班车的 ETA）
                            if (vehicles.size > 1) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp)
                                        .clip(RoundedCornerShape(BusRadius.s))
                                        .background(t.track.copy(alpha = 0.5f))
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    vehicles.take(3).forEachIndexed { idx, v ->
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Text(
                                                v.etaValueText,
                                                color = if (v.etaMinutes != null && v.etaMinutes <= 5) t.danger else t.ink,
                                                fontSize = (16 * scale).sp,
                                                fontWeight = FontWeight.Bold,
                                            )
                                            Text(
                                                if (idx == 0) "下一班" else "第${idx + 1}班",
                                                color = t.textTertiary,
                                                fontSize = (10 * scale).sp,
                                            )
                                        }
                                        if (idx < 2 && idx < vehicles.size - 1) {
                                            Box(
                                                Modifier
                                                    .width(1.dp)
                                                    .height(24.dp)
                                                    .background(t.line),
                                            )
                                        }
                                    }
                                }
                            }
                            // 运送用户商品的提示
                            if (vm.isMyVehicle(nearest)) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                        .clip(RoundedCornerShape(BusRadius.s))
                                        .background(t.goldLight)
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(BusIcons.parcel, null, tint = t.goldDeep, modifier = Modifier.size(16.dp))
                                    Text(
                                        " 这辆车正在运送您的商品：${vm.myGoodsDescription(nearest)}",
                                        color = t.goldDeep,
                                        fontSize = (11 * scale).sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(start = 4.dp),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    // 车辆列表
                    if (vehicles.isNotEmpty()) {
                        SectionTitle("正在运行", subtitle = "${vehicles.size} 辆")
                        vehicles.take(5).forEachIndexed { i, v ->
                            VehicleCard(
                                v,
                                isNearest = i == 0,
                                lineId = vm.lineIdOf(v),
                                isMyVehicle = vm.isMyVehicle(v),
                                myGoodsDesc = vm.myGoodsDescription(v),
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    // 经停站点
                    val st = line?.stations?.takeIf { it.isNotEmpty() } ?: vm.nearby?.stations.orEmpty()
                    if (st.isNotEmpty()) {
                        SectionTitle("经停站点", subtitle = "共 ${st.size} 站")
                        BusCard(modifier = Modifier.fillMaxWidth()) {
                            StationTimeline(
                                st.mapIndexed { i, s ->
                                    val seqText = "第 ${i + 1} 站 / 共 ${st.size} 站"
                                    val typeText = if (s.isRealTransit) "实时公交站" else "项目站点"
                                    TimelineStop(
                                        label = s.name,
                                        time = if (s.plannedMinutes > 0) "${s.plannedMinutes} 分" else null,
                                        hint = listOfNotNull(
                                            seqText,
                                            if (s.distanceMeters > 0) "距你 %.1f 公里".format(s.distanceMeters / 1000.0) else null,
                                            if (s.orderCount > 0) "待取 ${s.orderCount} 件" else null,
                                            if (s.routes.isNotEmpty()) "经停: ${s.routes.take(2).joinToString(", ")}" else null,
                                        ).joinToString(" · "),
                                        state = when {
                                            i == 0 -> StopState.DONE
                                            vm.me != null && s.distanceMeters > 0 && s.distanceMeters < 800 -> StopState.CURRENT
                                            else -> StopState.PENDING
                                        },
                                    )
                                },
                            )
                        }
                        // 步行导航到站点：挑站与唤起都走 ui/common/WalkToStation.kt。
                        // 旧写法在这里自己挑"时间轴里第一个 800 米内的站，没有就退回线路起点站" ——
                        // 起点站往往在三公里外，按钮上写的站名因此是错的。
                        val walkTarget = nearestWalkTarget(st, vm.me)
                        var walkProblem by remember { mutableStateOf<String?>(null) }
                        if (walkTarget != null) {
                            val ctx = LocalContext.current
                            BusButton(
                                text = "步行导航到「${walkTarget.name}」",
                                onClick = {
                                    walkProblem = if (openWalkRoute(ctx, walkTarget.name, walkTarget.geo)) {
                                        null
                                    } else {
                                        "这台机器打不开步行导航：既没装高德地图，也没能打开高德网页版"
                                    }
                                },
                                variant = BusButtonVariant.OUTLINE,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            walkProblem?.let {
                                Text(
                                    it,
                                    color = LocalBusTokens.current.danger,
                                    fontSize = (12 * LocalBusFontScale.current).sp,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 位置新鲜度文案：把后端给的 `lastLocationTime`（ISO 或 epoch millis）翻成"X 分钟前更新"。
 * 超过 30 分钟返回 null（不显示，避免噪声）；解析失败也返回 null（不报"未知"这种假话）。
 */
private fun formatLocationFreshness(raw: String): String? {
    if (raw.isEmpty()) return null
    val millis = runCatching {
        if (raw.all { it.isDigit() } && raw.length == 13) raw.toLong()
        else java.time.LocalDateTime.parse(raw).atZone(java.time.ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli()
    }.getOrNull() ?: return null
    val diffMin = ((System.currentTimeMillis() - millis) / 60_000).toInt()
    return when {
        diffMin < 1 -> "刚刚更新"
        diffMin < 60 -> "$diffMin 分钟前更新"
        diffMin < 1440 -> "${diffMin / 60} 小时前更新"
        else -> null
    }
}

@Composable
private fun VehicleCard(
    v: BusVehicle,
    isNearest: Boolean = false,
    lineId: String = "",
    isMyVehicle: Boolean = false,
    myGoodsDesc: String = "",
) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val nav = LocalNavController.current

    // 车票卡：主券放线路/车牌/下一站，副券放 ETA 大数字（对齐小程序 vehicle-card ticket-card）
    TicketCard(
        serial = buildString {
            append(v.lineName.ifEmpty { "班车" })
            if (v.shiftCode.isNotEmpty()) append(" · ${v.shiftCode}")
            else if (v.plate.isNotEmpty()) append(" · ${v.plate}")
        },
        stubValue = v.etaValueText,
        stubLabel = v.etaUnitText,
        stubColor = if (v.running && v.etaMinutes != null && v.etaMinutes <= 5) t.danger else t.primaryOnLight,
        onClick = { if (lineId.isNotEmpty()) nav.navigate(Routes.busDetail(lineId)) },
    ) {
        Column(Modifier.weight(1f).padding(vertical = 2.dp)) {
            // vehicle-head：线路名 + 「离你最近」+ 状态
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    v.lineName.ifEmpty { "班车" },
                    color = t.ink,
                    fontSize = (15.5 * scale).sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (isNearest) {
                    Text(
                        "离你最近",
                        color = t.goldDeep,
                        fontSize = (10.5 * scale).sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(BusRadius.pill))
                            .background(t.goldLight)
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                    Spacer(Modifier.size(6.dp))
                }
                Text(
                    v.statusLabel,
                    color = when (v.status) {
                        VehicleStatus.RUNNING -> t.primaryOnLight
                        VehicleStatus.IDLE -> t.goldDeep
                        VehicleStatus.ARRIVED -> t.primaryOnLight
                        else -> t.textTertiary
                    },
                    fontSize = (12 * scale).sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            // ETA 来源标识（对标车来了：实时计算 vs 按时刻表）
            if (v.routeProvider.isNotEmpty()) {
                Text(
                    v.providerText,
                    color = t.textTertiary,
                    fontSize = (10 * scale).sp,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            // 运送用户商品的提示条
            if (isMyVehicle && myGoodsDesc.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(BusRadius.s))
                        .background(t.goldLight)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(BusIcons.parcel, null, tint = t.goldDeep, modifier = Modifier.size(14.dp))
                    Text(
                        " 运送您的商品：$myGoodsDesc",
                        color = t.goldDeep,
                        fontSize = (11 * scale).sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 4.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // vehicle-plate-row：bus 图标 + 车牌 + 速度 + 位置来源
            Row(Modifier.padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    BusIcons.bus, null,
                    tint = if (v.isReal) t.primaryOnLight else t.clayDeep,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    v.plate.ifEmpty { "待派车" },
                    color = t.textSecondary,
                    fontSize = (12.5 * scale).sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 6.dp),
                )
                // 实时速度（对标高德/车来了：显示当前行驶速度）
                v.speedKmh?.let { speed ->
                    if (speed > 0) {
                        Text(
                            " · $speed km/h",
                            color = t.primary,
                            fontSize = (11 * scale).sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                SourceTag(v.sourceText, Modifier.padding(start = 7.dp))
            }
            // 位置新鲜度（对标车来了：显示最后定位时间，让用户知道数据是否过时）
            if (v.lastLocationTime.isNotEmpty()) {
                val freshText = formatLocationFreshness(v.lastLocationTime)
                if (freshText != null) {
                    Text(
                        "位置：$freshText",
                        color = t.textTertiary,
                        fontSize = (10 * scale).sp,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
            // 山路线：进度只由后端 progress 驱动（nearby 不下发就画无进度虚线），旧写法用 1-eta/40 反推，
            // ETA 没给时会画成"车已经到终点"
            RoadLine(
                progress = v.roadProgress ?: -1f,
                modifier = Modifier.padding(top = 7.dp),
            )
            // vehicle-next-row：站点文案（优先"你最方便那站还有几站"）+ 到站/发车说明 + 距离
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(BusIcons.location, null, tint = t.textTertiary, modifier = Modifier.size(12.dp))
                Column(Modifier.weight(1f).padding(start = 5.dp)) {
                    Text(
                        v.stationText,
                        color = t.textSecondary,
                        fontSize = (12 * scale).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        v.etaCaptionText,
                        color = t.textTertiary,
                        fontSize = (11.5 * scale).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                v.nextDistanceShortText?.let {
                    Text(
                        it,
                        color = t.textTertiary,
                        fontSize = (11 * scale).sp,
                    )
                }
            }
        }
    }
}

@Composable
fun BusDetailScreen(routeId: String, vm: BusViewModel = viewModel()) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val context = LocalContext.current
    // 收藏：这一页此前没有入口（星标只画在实时公交页的内嵌详情里），从首页线路签点进来的人
    // 根本没法收藏（真机反馈"也不能收藏"）。收藏表在 Prefs 里、不是 State，所以本地存一份
    // 翻转值来驱动重组，不另造一个"tick"。
    var isFav by remember(routeId) { mutableStateOf(Prefs.isFavoriteLine(routeId)) }
    /** 唤起步行导航失败的原话：只在真失败时出现，不写"已为你打开导航" */
    var walkProblem by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(routeId) { vm.openDetail(context, routeId) }

    Column {
        BackTopBar(
            title = vm.selected?.name?.ifEmpty { null } ?: "线路详情",
            // 原来这里直接把 routeId（一串内部编号，实测显示成"500"）挂在标题下面当副标题 ——
            // 那是调试物，不是给用户看的信息。改成这一条线的来源口径。
            subtitle = vm.selected?.dataSource?.takeIf { it.isNotEmpty() }
                ?.let { if (it.contains("REAL")) "现实公交" else "项目线路" },
            actions = {
                if (routeId.isNotEmpty()) {
                    Icon(
                        BusIcons.star,
                        if (isFav) "已收藏" else "收藏",
                        tint = if (isFav) t.goldDeep else t.onPrimary,
                        modifier = Modifier
                            .padding(horizontal = 10.dp)
                            .size((22 * scale).dp)
                            .clickable {
                                Prefs.toggleFavoriteLine(routeId)
                                isFav = !isFav
                            },
                    )
                }
            },
        )
        BusPage(spacedBy = 12.dp) {
            DemoNote(vm.demo)
            val line = vm.selected
            if (line == null) {
                // 四态各自的说法由纯函数 detailSlotOf 决定（用例钉优先级，判据钉它是纯函数）
                when (vm.detailSlot()) {
                    DetailSlot.Loading -> SkeletonList(2)
                    DetailSlot.NotInList -> ErrorState(
                        "当前线路列表里没有编号为 $routeId 的线路（可能已下线，或链接来自旧版本）。",
                        onRetry = { vm.openDetail(context, routeId) },
                    )
                    DetailSlot.Failed -> ErrorState("线路信息获取失败", onRetry = { vm.load(context) })
                    DetailSlot.Ready -> Unit
                }
            } else {
                SectionTitle(
                    // 只认后端真实键 distanceKm（公里，AppBusLineRespVO:27-28）；没给就说"里程未给"，不写"全程 0.0 公里"
                    "全程 " + (line.distanceKm?.let { "%.1f 公里".format(it) } ?: "里程暂未知"),
                    subtitle = "${line.from} → ${line.to} · ${line.stations.size} 站",
                )
                RouteMap(
                    polyline = vm.polyline,
                    stations = line.stations,
                    vehicles = vm.vehiclesOf(line),
                    me = vm.me,
                    height = 250.dp,
                    reason = vm.polylineReason(
                        "暂无该线路的真实道路折线，经停站顺序请看下方时刻表。",
                    ),
                    onReplan = { vm.replan() },
                )
                if (vm.me != null && line.stations.isNotEmpty()) {
                    // 挑站与唤起步行导航都走 ui/common/WalkToStation.kt 那一份口径：
                    // 这颗按钮此前只存在于实时公交页的内嵌详情里，从首页线路签点进来的人看不到它
                    // （真机反馈"步行导航去站点"这一格是空的）。
                    val walkTarget = nearestWalkTarget(line.stations, vm.me)
                    if (walkTarget != null) {
                        val meters = GeoMath.distance(vm.me!!, walkTarget.geo)
                        BusCard(modifier = Modifier.fillMaxWidth(), warm = true) {
                            Text(
                                "离你最近的经停站：${walkTarget.name}",
                                color = t.ink,
                                fontSize = (13.5 * scale).sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "直线距离约 %.1f 公里（仅表示远近，不是道路里程）".format(meters / 1000.0),
                                color = t.textSecondary,
                                fontSize = (12 * scale).sp,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            BusButton(
                                text = "步行导航到「${walkTarget.name}」",
                                onClick = {
                                    walkProblem = if (openWalkRoute(context, walkTarget.name, walkTarget.geo)) {
                                        null
                                    } else {
                                        "这台机器打不开步行导航：既没装高德地图，也没能打开高德网页版"
                                    }
                                },
                                modifier = Modifier.padding(top = 10.dp),
                            )
                            // 失败必须留在页面上：唤起类动作点了没动静，用户只会以为是自己没点到
                            walkProblem?.let {
                                Text(
                                    it,
                                    color = t.danger,
                                    fontSize = (12 * scale).sp,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                        }
                    }
                }
                SectionTitle("经停时刻", subtitle = "共 ${line.stations.size} 站 · 以站点计划时间为准")
                BusCard(modifier = Modifier.fillMaxWidth()) {
                    StationTimeline(
                        line.stations.mapIndexed { i, s ->
                            val seqText = "第 ${i + 1} 站 / 共 ${line.stations.size} 站"
                            val typeText = if (s.isRealTransit) "实时公交站" else "项目站点"
                            TimelineStop(
                                label = s.name,
                                time = if (s.plannedMinutes > 0) "${s.plannedMinutes} 分" else null,
                                hint = listOfNotNull(
                                    seqText,
                                    if (s.distanceMeters > 0) "距你 %.1f 公里".format(s.distanceMeters / 1000.0) else null,
                                    if (s.orderCount > 0) "待取 ${s.orderCount} 件" else null,
                                    if (s.routes.isNotEmpty()) "经停: ${s.routes.take(2).joinToString(", ")}" else null,
                                ).joinToString(" · "),
                                state = when {
                                    i == 0 -> StopState.DONE
                                    vm.me != null && s.distanceMeters > 0 && s.distanceMeters < 800 -> StopState.CURRENT
                                    else -> StopState.PENDING
                                },
                            )
                        },
                    )
                }
                val vs = vm.vehiclesOf(line)
                if (vs.isNotEmpty()) {
                    SectionTitle("本线车辆", subtitle = "${vs.size} 辆")
                    vs.forEach { VehicleCard(it, lineId = routeId) }
                }
                SourceTag(line.dataSource)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
