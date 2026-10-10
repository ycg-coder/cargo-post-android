package com.shanxiang.buspost.ui.user

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import com.shanxiang.buspost.core.loc.Locator
import com.shanxiang.buspost.core.net.ApiResult
import com.shanxiang.buspost.core.net.safeApiCall
import com.shanxiang.buspost.core.ui.BusCard
import com.shanxiang.buspost.core.ui.BusIcons
import com.shanxiang.buspost.core.ui.BusPage
import com.shanxiang.buspost.core.ui.BusRadius
import com.shanxiang.buspost.core.ui.BusTag
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
import com.shanxiang.buspost.data.model.Station
import com.shanxiang.buspost.data.model.VehicleStatus
import com.shanxiang.buspost.core.ui.StopState
import com.shanxiang.buspost.core.map.polylineReasonOf
import com.shanxiang.buspost.core.map.polylineSubtitleOf
import com.shanxiang.buspost.core.map.PolylinePhase
import com.shanxiang.buspost.core.map.shouldAskPolyline
import com.shanxiang.buspost.data.repo.Repo
import com.shanxiang.buspost.nav.Routes
import com.shanxiang.buspost.ui.common.BackTopBar
import com.shanxiang.buspost.ui.common.DemoNote
import com.shanxiang.buspost.ui.common.LocalNavController
import com.shanxiang.buspost.ui.common.RouteMap
import com.shanxiang.buspost.ui.common.ScreenViewModel

class BusViewModel : ScreenViewModel() {
    var nearby by mutableStateOf<NearbyResult?>(null)
    var lines by mutableStateOf<List<BusLine>>(emptyList())
    var selected by mutableStateOf<BusLine?>(null)
    var polyline by mutableStateOf<List<Geo>>(emptyList())

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

    fun load(context: android.content.Context, silent: Boolean = false) {
        val mySeq = ++reqSeq
        work(reset = !silent) {
            val geo = Locator.once(context)
            me = geo
            if (!silent) {
                locationHint = when {
                    geo == null -> "没拿到定位，展示全部线路。去系统设置里允许定位后刷新更准。"
                    else -> "已按你的位置排序"
                }
            }
            val one = Repo.nearbyBuses(geo, Repo.nearbyRadius(0.0))
            if (mySeq != reqSeq) return@work
            nearby = one.value
            val lf = Repo.busLines(geo)
            if (mySeq != reqSeq) return@work
            lines = lf.items
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
        val r = nearby ?: return if (loading) "loading" else "error"
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

    LaunchedEffect(Unit) { if (vm.nearby == null && vm.lines.isEmpty()) vm.load(context) }

    // 15s 静默轮询（对齐小程序 pages/bus/index.js:113-127）：失败不弹提示、保留旧数据；
    // 页面离开自动取消（LaunchedEffect 随组合销毁），不需要手动清定时器。
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(15_000)
            vm.load(context, silent = true)
        }
    }

    val real = vm.nearby?.realTransit == true ||
        vm.lines.any { l -> l.vehicles.any { it.isReal } || l.dataSource.contains("REAL") }

    BusPage(spacedBy = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("实时公交", color = t.ink, fontSize = (21 * scale).sp, fontWeight = FontWeight.Black)
                Text(
                    vm.locationHint.ifEmpty { "正在定位…" },
                    color = t.textSecondary,
                    fontSize = (12 * scale).sp,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            // 自动刷新节奏要让用户看得见，否则"每 15s 自动更新"等于没做
            if (vm.lastRefresh.isNotEmpty()) {
                Text(
                    "${vm.lastRefresh} 更新",
                    color = t.textTertiary,
                    fontSize = (11 * scale).sp,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            Icon(
                BusIcons.refresh, null, tint = t.primary,
                modifier = Modifier.size(22.dp).clickable { vm.load(context) },
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            BusTag(
                if (real) "实时位置" else if (vm.demo) "演示线路" else "班次时刻",
                tone = if (real) BusTone.OK else BusTone.WARN,
            )
            if (vm.nearby?.stations?.isNotEmpty() == true) {
                BusTag("附近 ${vm.nearby!!.stations.size} 个站点", tone = BusTone.NEUTRAL)
            }
            // 首屏没落地时 `lines` 是空表，念出来的「0 条线路」不是"这里没有线路"而是"还不知道有几条"，
            // 而同一行下面的骨架屏正在表示"还在问"。与上一枚站点签（:312 只在有结果时出现）同口径，
            // 也是 G-3b（快递页计数诚实化）那一族：没答案就不报数。
            if (vm.nearby != null) {
                BusTag("${vm.lines.size} 条线路", tone = BusTone.PRIMARY)
            }
        }

        DemoNote(vm.demo, text = "该区域没有开通实时公交，下面是客货邮班车的演示线路与时刻。")

        when (vm.state()) {
            "loading" -> SkeletonList(3)

            // 非运营时间：说明服务时段与下一班，不铺"待发车"卡片（MP-11）
            "off" -> EmptyState(
                title = "当前不在运营时间",
                subtitle = listOfNotNull(
                    vm.nearby?.serviceWindowText?.takeIf { it.isNotEmpty() }?.let { "服务时段 $it" },
                    vm.nearby?.nextDepartureTime?.takeIf { it.isNotEmpty() }?.let { "下一班 $it 发车" },
                    vm.nearby?.lineCount?.takeIf { it > 0 }?.let { "附近共 $it 条公交线路" },
                ).joinToString("；")
                    .ifEmpty { "附近暂无运营中的班次，可稍后再来" },
                icon = BusIcons.clock,
            )

            // 有站/有线路但无车：如实列出站点与线路，不能说"附近没有公交"
            "empty" -> {
                val st = vm.nearby?.stations.orEmpty()
                Column {
                    EmptyState(
                        title = if (st.isNotEmpty()) "附近有 ${st.size} 个公交站点" else "附近暂无公交线路",
                        subtitle = "当前暂无实时车辆数据",
                        icon = BusIcons.bus,
                    )
                    if (st.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            st.take(8).forEach { s ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(BusIcons.location, null, tint = t.textTertiary, modifier = Modifier.size(14.dp))
                                    Text(
                                        s.name,
                                        color = t.ink,
                                        fontSize = (13 * scale).sp,
                                        modifier = Modifier.padding(start = 6.dp).weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (s.distanceMeters > 0) {
                                        Text("${"%.1f".format(s.distanceMeters / 1000)}km", color = t.textTertiary, fontSize = (11.5f * scale).sp)
                                    }
                                    if (s.isRealTransit) {
                                        BusTag("现实公交", Modifier.padding(start = 6.dp), tone = BusTone.OK)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "error" -> EmptyState(
                title = "实时公交暂时不可用",
                subtitle = "网络不太好，稍后再试",
                icon = BusIcons.info,
                actionText = "重新加载",
                onAction = { vm.load(context) },
            )

            else -> {
            if (vm.lines.size > 1) {
                // 附近线路：横滑 chip（名称 + 来源标签），默认 6 条可展开（对齐小程序 line-chip）
                SectionTitle(
                    "附近线路",
                    actionText = if (vm.lines.size > 6) (if (showAllLines) "收起" else "全部线路") else null,
                    onAction = { showAllLines = !showAllLines },
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    (if (showAllLines) vm.lines else vm.lines.take(6)).forEach { l ->
                        val on = l.id == vm.selected?.id
                        Column(
                            Modifier
                                .clip(RoundedCornerShape(BusRadius.m))
                                .background(if (on) t.primaryLight else t.card)
                                .clickable { vm.pick(l) }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                        ) {
                            Text(
                                l.name.ifEmpty { "未命名线路" },
                                color = if (on) t.primaryOnLight else t.ink,
                                fontSize = (13 * scale).sp,
                                fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                            )
                            Text(
                                if (l.dataSource.contains("REAL")) "现实公交站点" else "真实公交线路",
                                color = if (on) t.primaryOnLight else t.textTertiary,
                                fontSize = (10.5 * scale).sp,
                                modifier = Modifier.padding(top = 3.dp),
                            )
                        }
                    }
                }
            }

            val line = vm.selected
            SectionTitle(
                line?.name?.ifEmpty { null } ?: "线路图",
                subtitle = vm.polylineSubtitle(),
                actionText = if (line != null && line.id.isNotEmpty()) "看详情" else null,
                onAction = { if (line != null) nav.navigate(Routes.busDetail(line.id)) },
            )
            RouteMap(
                polyline = vm.polyline,
                stations = line?.stations ?: vm.nearby?.stations ?: emptyList(),
                vehicles = vm.vehiclesOf(line),
                me = vm.me,
                height = 220.dp,
                reason = if (vm.polyline.size >= 2) null else vm.polylineReason(
                    "这条线路还没有真实道路折线：后端只在运营线路有轨迹数据时下发。站点顺序看下面的经停站列表。",
                ),
                onReplan = if (line != null) { { vm.replan() } } else null,
            )

            val vs = vm.vehiclesOf(line).byUserDistance()
            if (vs.isNotEmpty()) {
                SectionTitle("正在运行", subtitle = "${vs.size} 辆，按离你的远近排（后端给的距离，没给的排最后）")
                vs.take(5).forEachIndexed { i, v -> VehicleCard(v, isNearest = i == 0, lineId = vm.lineIdOf(v)) }
            }

            val st = line?.stations?.takeIf { it.isNotEmpty() } ?: vm.nearby?.stations.orEmpty()
            if (st.isNotEmpty()) {
                SectionTitle("经停站点", subtitle = "共 ${st.size} 站")
                BusCard(modifier = Modifier.fillMaxWidth()) {
                    StationTimeline(
                        st.mapIndexed { i, s ->
                            TimelineStop(
                                label = s.name,
                                time = if (s.plannedMinutes > 0) "${s.plannedMinutes} 分" else null,
                                hint = listOfNotNull(
                                    if (s.distanceMeters > 0) "距你 %.1f 公里".format(s.distanceMeters / 1000.0) else null,
                                    if (s.orderCount > 0) "待取 ${s.orderCount} 件" else null,
                                ).joinToString(" · ").ifEmpty { if (i == 0) "起点" else if (i == st.size - 1) "终点" else "" },
                                state = when {
                                    i == 0 -> StopState.DONE
                                    vm.me != null && s.distanceMeters > 0 && s.distanceMeters < 800 -> StopState.CURRENT
                                    else -> StopState.PENDING
                                },
                            )
                        },
                    )
                }
            }
        }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun VehicleCard(v: BusVehicle, isNearest: Boolean = false, lineId: String = "") {
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
            // vehicle-plate-row：bus 图标 + 车牌 + 位置来源
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
                SourceTag(v.sourceText, Modifier.padding(start = 7.dp))
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

    LaunchedEffect(routeId) { vm.openDetail(context, routeId) }

    Column {
        BackTopBar(title = vm.selected?.name?.ifEmpty { null } ?: "线路详情", subtitle = routeId)
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
                    "全程 " + (line.distanceKm?.let { "%.1f 公里".format(it) } ?: "里程未给"),
                    subtitle = "${line.from} → ${line.to} · ${line.stations.size} 站",
                )
                RouteMap(
                    polyline = vm.polyline,
                    stations = line.stations,
                    vehicles = vm.vehiclesOf(line),
                    me = vm.me,
                    height = 250.dp,
                    reason = vm.polylineReason(
                        "后端没有这条线路的真实道路折线，这里不用站点连线顶替。经停站顺序看下面的时刻表。",
                    ),
                    onReplan = { vm.replan() },
                )
                if (vm.me != null && line.stations.isNotEmpty()) {
                    val nearest = line.stations.minByOrNull { GeoMath.distance(vm.me!!, it.geo) }
                    if (nearest != null) {
                        BusCard(modifier = Modifier.fillMaxWidth(), warm = true) {
                            Text(
                                "离你最近的经停站：${nearest.name}",
                                color = t.ink,
                                fontSize = (13.5 * scale).sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "直线距离约 %.1f 公里（仅表示远近，不是道路里程）".format(GeoMath.distance(vm.me!!, nearest.geo) / 1000.0),
                                color = t.textSecondary,
                                fontSize = (12 * scale).sp,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
                SectionTitle("经停时刻", subtitle = "以站点计划时间为准")
                BusCard(modifier = Modifier.fillMaxWidth()) {
                    StationTimeline(
                        line.stations.map { s ->
                            TimelineStop(
                                s.name,
                                if (s.plannedMinutes > 0) "${s.plannedMinutes} 分" else null,
                                listOfNotNull(
                                    s.routes.firstOrNull(),
                                    if (s.orderCount > 0) "待取 ${s.orderCount} 件" else null,
                                ).joinToString(" · "),
                                StopState.PENDING,
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
