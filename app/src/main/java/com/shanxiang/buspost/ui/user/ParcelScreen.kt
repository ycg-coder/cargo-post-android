package com.shanxiang.buspost.ui.user

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shanxiang.buspost.core.ui.NetworkImage
import com.shanxiang.buspost.core.AppState
import com.shanxiang.buspost.core.ui.BusButton
import com.shanxiang.buspost.core.ui.BusButtonVariant
import com.shanxiang.buspost.core.ui.BusCard
import com.shanxiang.buspost.core.ui.BusIcons
import com.shanxiang.buspost.core.ui.BusPage
import com.shanxiang.buspost.core.ui.BusRadius
import com.shanxiang.buspost.core.ui.BusSegmented
import com.shanxiang.buspost.core.ui.BusTag
import com.shanxiang.buspost.core.ui.BusTextField
import com.shanxiang.buspost.core.ui.BusSwitch
import com.shanxiang.buspost.core.ui.BusTone
import com.shanxiang.buspost.core.ui.EmptyState
import com.shanxiang.buspost.core.ui.ErrorState
import com.shanxiang.buspost.core.ui.LocalBusFontScale
import com.shanxiang.buspost.core.ui.LocalBusTokens
import com.shanxiang.buspost.core.ui.PriceText
import com.shanxiang.buspost.core.ui.QrView
import com.shanxiang.buspost.core.ui.SectionTitle
import com.shanxiang.buspost.core.ui.SkeletonList
import com.shanxiang.buspost.core.ui.SourceTag
import com.shanxiang.buspost.core.ui.TicketCard
import com.shanxiang.buspost.data.model.ProductOrder
import com.shanxiang.buspost.data.model.SendOrder
import com.shanxiang.buspost.data.repo.Repo
import com.shanxiang.buspost.data.store.Prefs
import com.shanxiang.buspost.nav.Routes
import com.shanxiang.buspost.notify.NotifyWorker
import com.shanxiang.buspost.ui.common.DemoNote
import com.shanxiang.buspost.ui.common.LocalNavController
import com.shanxiang.buspost.ui.common.ScreenViewModel

class ParcelViewModel : ScreenViewModel() {
    var sends by mutableStateOf<List<SendOrder>>(emptyList())
    var orders by mutableStateOf<List<ProductOrder>>(emptyList())
    /** 到车提醒列表：来自 `/send/page` 里后端真给了承运车辆到站分钟的那些单（§68） */
    var arrivals by mutableStateOf<List<SendOrder>>(emptyList())
    var sendsDemo by mutableStateOf(false)
    var ordersDemo by mutableStateOf(false)
    /**
     * 页首计数：null = **这一次没问到**（还没加载完，或后端回了失败），只有请求成功才允许写数字。
     * 口径与「我的」页的 sendCount 相同，见文件末尾的 serverCount。
     */
    var sendCount by mutableStateOf<Int?>(null)
    var orderCount by mutableStateOf<Int?>(null)

    fun load() = work {
        val s = Repo.sendOrders()
        val o = Repo.productOrders()
        sends = s.items
        orders = o.items
        sendCount = serverCount(s.error, s.total)
        orderCount = serverCount(o.error, o.total)
        // §68 迁移：以前这里过滤 `/send/arrangements` 的 etaMinutes，而那个 VO 压根没这个键，
        // 真接口下永远是空列表 —— 看着像"没有快到站的车"，其实是"没有数据源"。
        // 现在按 /send/page 的 carrierEtaMinutes 过滤，位置标记为 REAL_STALE 的不参与。
        arrivals = s.items.filter {
            it.carrierEtaMinutes != null && it.carrierLocationSource != "REAL_STALE"
        }
        sendsDemo = s.demo
        ordersDemo = o.demo
        mergeDemo(s.demo, o.demo)
        error = s.error ?: o.error
        AppState.reportUserUnread(Repo.unreadCount(), null)
    }

    /** 待客户操作：确认已送站 → 待入池（小程序 confirmStationAction 同口径） */
    fun confirmStation(orderId: Long) = work(reset = false) {
        Repo.confirmStationAction(orderId)
        load()
    }
}

@Composable
fun ParcelScreen(standalone: Boolean = false, vm: ParcelViewModel = viewModel()) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val nav = LocalNavController.current
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { if (vm.sends.isEmpty() && vm.orders.isEmpty()) vm.load() }

    // 「我的」页入口带来的进入意图：直达寄货/购物 tab，或直接展开某单物流详情
    LaunchedEffect(Unit) {
        val intent = AppState.consumeParcelIntent() ?: return@LaunchedEffect
        when (intent.type) {
            "TAB" -> tab = intent.tab
            "TRACK" -> if (intent.no.isNotEmpty()) nav.navigate(Routes.trace(intent.no))
        }
    }

    Column {
        if (standalone) {
            com.shanxiang.buspost.ui.common.BackTopBar(title = "快递", subtitle = "寄货记录 · 商城订单 · 单号查询")
        }
        BusPage(spacedBy = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (standalone) "包裹" else "我的包裹",
                        color = t.ink,
                        fontSize = (21 * scale).sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        parcelHeaderCountText(vm.sendCount, vm.orderCount),
                        color = t.textSecondary,
                        fontSize = (12.5 * scale).sp,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                Icon(
                    BusIcons.refresh,
                    null,
                    tint = t.primary,
                    modifier = Modifier
                        .size(22.dp)
                        .clickable { vm.load() },
                )
            }

            // 到车提醒是安卓端相对小程序最大的增量：后台轮询 + 系统通知栏
            ArrivalAlertBlock(vm.arrivals, onOpen = { nav.navigate(Routes.trace(it)) })

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                BusTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "输入运单号查询物流",
                    icon = BusIcons.search,
                    modifier = Modifier.weight(1f),
                )
                // 走 BusButton 而不是裸 Text：#283 实测过"onPrimary 白字落在浅色页面上等于没有按钮"，
                // 禁用态由组件统一给灰板 + 正常灰字
                BusButton(
                    text = "查询",
                    onClick = { nav.navigate(Routes.trace(query.trim())) },
                    enabled = query.isNotBlank(),
                    variant = BusButtonVariant.PRIMARY,
                    small = true,
                    fullWidth = false,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            // 与小程序 parcel.wxml 三个内层 tab 同口径（我的寄货 / 我的购物 / 单号查询）
            BusSegmented(listOf("我的寄货", "我的购物", "单号查询"), tab, onSelect = { tab = it })

            DemoNote(vm.demo, text = if (tab == 0) "寄货记录来自本地演示集，非真实订单。" else "商城订单来自本地演示集，非真实订单。")
            vm.error?.let { ErrorState(it, onRetry = { vm.load() }) }

            if (vm.loading && vm.sends.isEmpty() && vm.orders.isEmpty()) {
                SkeletonList(3)
            } else if (tab == 0) {
                if (vm.sends.isEmpty()) {
                    EmptyState(
                        title = "还没有寄货记录",
                        subtitle = "山货出山、包裹进村，点这里寄一件",
                        icon = BusIcons.send,
                        actionText = "去寄一件",
                        onAction = { nav.navigate(Routes.SEND) },
                    )
                } else {
                    vm.sends.forEach { s -> SendCard(s, onConfirmStation = { vm.confirmStation(s.id) }) { nav.navigate(Routes.trace(s.orderNo)) } }
                }
            } else if (tab == 1) {
                if (vm.orders.isEmpty()) {
                    EmptyState(
                        title = "还没有购物订单",
                        subtitle = "去逛逛山货集市，看中了大巴给你捎回家",
                        icon = BusIcons.cart,
                        actionText = "去逛逛",
                        onAction = { nav.navigate(Routes.USER_SHELL) },
                    )
                } else {
                    vm.orders.forEach { o -> OrderCard(o) { nav.navigate(Routes.orderDetail(o.id)) } }
                }
            } else {
                // Tab2: 单号查询——查询条在页首，点「查询」进完整物流票面
                EmptyState(
                    title = "输入上方的运单号",
                    subtitle = "查询物流进度，结果以完整票面展示",
                    icon = BusIcons.search,
                )
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun ArrivalAlertBlock(arrivals: List<SendOrder>, onOpen: (String) -> Unit) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val context = LocalContext.current
    var notifyOn by remember { mutableStateOf(Prefs.arrivalAlertEnabled) }
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        BusCard(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
            warm = arrivals.isNotEmpty(),
            padding = PaddingValues(horizontal = 14.dp, vertical = 11.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(BusIcons.bell, null, tint = if (notifyOn) t.primary else t.textTertiary, modifier = Modifier.size(18.dp))
                Text(
                    "到车提醒",
                    color = t.ink,
                    fontSize = (14 * scale).sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp).weight(1f),
                )
                if (arrivals.isNotEmpty()) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(BusRadius.pill))
                            .background(t.primaryLight)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            "${arrivals.size} 辆",
                            color = t.primary,
                            fontSize = (11.5 * scale).sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Icon(
                    if (expanded) BusIcons.expandLess else BusIcons.expandMore,
                    null,
                    tint = t.textSecondary,
                    modifier = Modifier.size(18.dp).padding(start = 8.dp),
                )
            }
            // 展开后显示说明文字和开关
            if (expanded) {
                Text(
                    if (notifyOn) "后台每 15 分钟检查一次，车到站前 10 分钟系统通知栏提醒"
                    else "开启后即使没打开 App 也会在通知栏提醒（安卓专属能力）",
                    color = t.textSecondary,
                    fontSize = (11.5 * scale).sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Row(
                    Modifier.padding(top = 8.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("通知开关", color = t.textSecondary, fontSize = (12.5 * scale).sp)
                    BusSwitch(
                        checked = notifyOn,
                        onCheckedChange = {
                            notifyOn = it
                            Prefs.arrivalAlertEnabled = it
                            NotifyWorker.runNow(context)
                        },
                    )
                }
            }
        }
        // 展开时才显示到车列表
        if (expanded) {
            arrivals.take(2).forEach { a ->
                val eta = a.carrierEtaMinutes
                TicketCard(
                    serial = a.orderNo,
                    stubValue = eta?.toString() ?: "—",
                    stubLabel = if (eta != null) "分钟到站\n（直线估算）" else "暂未知",
                    stubColor = if (eta != null && eta <= 5) t.danger else t.primary,
                    onClick = { onOpen(a.orderNo) },
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(a.targetStation.ifEmpty { "目标站点" }, color = t.ink, fontSize = (15 * scale).sp, fontWeight = FontWeight.Bold)
                        Text(
                            listOf(a.vehiclePlate, a.shiftCode, a.statusLabel).filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { "已排班" },
                            color = t.textSecondary,
                            fontSize = (12 * scale).sp,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                        SourceTag(a.dataSource, Modifier.padding(top = 5.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SendCard(s: SendOrder, onConfirmStation: () -> Unit, onClick: () -> Unit) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val nav = LocalNavController.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    // 取件码默认打码，点「查看」才显明文（小程序 toggleCode 同口径）
    var codeShown by remember(s.orderNo) { mutableStateOf(false) }
    var showQr by remember(s.orderNo) { mutableStateOf(false) }
    val rejected = s.reviewStatus == REVIEW_REJECTED

    // 车票卡：主券放货物信息，副券放包裹状态（对齐小程序 parcel-card ticket-card）
    TicketCard(
        serial = "单号: ${s.orderNo.ifEmpty { "未生成单号" }}",
        stubValue = if (rejected) "审核不通过" else s.statusLabel,
        stubLabel = "包裹状态",
        onClick = onClick,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 2.dp)) {
            // parcel-head：box 图标 + 货名
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(BusIcons.parcel, null, tint = t.primaryOnLight, modifier = Modifier.size(20.dp))
                Text(
                    s.goodsName.ifEmpty { "寄货" },
                    color = t.ink,
                    fontSize = (15 * scale).sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 7.dp),
                )
            }
            // parcel-meta：重量 + 下单时间
            Text(
                buildString {
                    if (s.weightKg > 0) append("重量 ${s.weightKg}kg")
                    if (s.createTime.isNotEmpty()) {
                        if (isNotEmpty()) append(" · ")
                        append(s.createTime)
                    }
                }.ifEmpty { "—" },
                color = t.textSecondary,
                fontSize = (12 * scale).sp,
                modifier = Modifier.padding(top = 4.dp),
            )

            // 货物信息 chips（寄货页填写）：类型 / 件数 / 体积 / 生鲜（对齐 parcel-cargo-row）
            Row(
                Modifier.padding(top = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CargoChip(s.cargoCategory.ifEmpty { "农产品" })
                CargoChip("${s.itemCount.coerceAtLeast(1)} 件")
                if (s.volumeM3 > 0) CargoChip(String.format("%.2f m³", s.volumeM3))
                if (s.fresh) CargoChip("生鲜冷链", gold = true)
            }

            // 车快到了 / 司机已到达提醒条（后端 carrierApproaching / carrierArrived 驱动，不在前端自判阈值）
            if (s.isArriving) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp)
                        .clip(RoundedCornerShape(BusRadius.m)).background(t.primaryLight)
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(BusIcons.bus, null, tint = t.primaryOnLight, modifier = Modifier.size(14.dp))
                    Text(
                        // carrierEtaMinutes = Haversine 直线公里 ÷ 25km/h（后端 GeoDistanceUtil），不是道路 ETA，文案必须带口径
                        "车快到了 · 距${s.targetStation.ifEmpty { "目标站" }}" +
                            (s.carrierEtaMinutes?.let { "约 $it 分钟（直线估算）" } ?: "时间暂未知") +
                            "，请提前到站点等候",
                        color = t.primaryOnLight,
                        fontSize = (11.5 * scale).sp,
                        modifier = Modifier.weight(1f).padding(start = 6.dp),
                    )
                    Text(
                        "看车辆",
                        color = t.primaryOnLight,
                        fontSize = (11.5 * scale).sp,
                        fontWeight = FontWeight.Bold,
                        // 内边距在 clickable 之后：撑出可点区域（设备实拍里链接紧贴换行文案、命中高度仅约 16dp）
                        modifier = Modifier
                            .padding(start = 10.dp)
                            .clip(RoundedCornerShape(BusRadius.pill))
                            .clickable { nav.navigate(Routes.trace(s.orderNo)) }
                            .padding(horizontal = 8.dp, vertical = 9.dp),
                    )
                }
            }
            if (s.carrierArrived) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp)
                        .clip(RoundedCornerShape(BusRadius.m)).background(t.clayLight)
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(BusIcons.location, null, tint = t.clayDeep, modifier = Modifier.size(14.dp))
                    Text(
                        "司机已到达${s.carrierArrivedStation.ifEmpty { s.targetStation }}，请到站点取件",
                        color = t.clayDeep,
                        fontSize = (11.5 * scale).sp,
                        modifier = Modifier.weight(1f).padding(start = 6.dp),
                    )
                }
            }

            // 待客户操作：引导送到就近交接站点 + 确认入池按钮（小程序 confirmStationAction 同口径）
            if (s.needsCustomerAction) {
                BusCard(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), warm = true) {
                    Column {
                        Text(
                            "需您操作",
                            color = t.clayDeep,
                            fontSize = (12.5 * scale).sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            buildString {
                                append("请将货物送到就近站点「${s.servicePointStationName}」")
                                if (s.servicePointDistanceKm > 0) append("（距取货点约 ${s.servicePointDistanceKm} km）")
                                append("交接后即可入池")
                            },
                            color = t.textSecondary,
                            fontSize = (12 * scale).sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                BusButton(
                    text = "我已送到指定站点，确认入池",
                    onClick = onConfirmStation,
                    variant = BusButtonVariant.OUTLINE,
                    modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
                )
            }

            Row(
                Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                if (s.pickupCode.isNotEmpty()) {
                    Text(
                        if (codeShown) "取件码 ${s.pickupCode}" else "取件码 ＊＊＊＊",
                        color = if (codeShown) t.clayDeep else t.textSecondary,
                        fontSize = (12.5 * scale).sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(BusRadius.pill))
                            .background(t.goldLight)
                            .clickable {
                                if (codeShown) {
                                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(s.pickupCode))
                                } else {
                                    codeShown = true
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                    Text(
                        if (codeShown) "点码复制" else "查看",
                        color = t.primaryOnLight,
                        fontSize = (11.5 * scale).sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(BusRadius.pill))
                            .clickable { showQr = !showQr }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                    )
                }
                s.etaMinutes?.let {
                    BusTag("预计 $it 分钟", tone = if (s.isArriving) BusTone.DANGER else BusTone.PRIMARY)
                }
                // 承运侧分钟来自 Haversine 直线 ÷ 25km/h（GeoDistanceUtil），与上面的班次 ETA 不是一件事，
                // 混显会让用户把直线估算当成道路到站时间（硬规则：禁止把 Haversine 说成道路 ETA）。
                // isArriving 时上方提醒条已经报过同一个分钟数与口径，不再重复一遍。
                if (!s.isArriving) s.carrierEtaMinutes?.let {
                    BusTag("车距站约 $it 分钟（直线估算）", tone = BusTone.GOLD)
                }
                Spacer(Modifier.weight(1f))
                PriceText(formatPrice(s.fee), size = 15)
            }

            // 取件码二维码：司机扫码核销（小程序 drawParcelQr）
            if (showQr && s.pickupCode.isNotEmpty()) {
                Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                    QrView(text = s.pickupCode, size = 140, caption = "司机扫码核销取件")
                }
            }

            SourceTag(s.dataSource, Modifier.padding(top = 6.dp))
        }
    }
}

/** 货物信息小胶囊（对齐小程序 .cargo-chip） */
@Composable
private fun CargoChip(text: String, gold: Boolean = false) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    Text(
        text,
        color = if (gold) t.goldDeep else t.textSecondary,
        fontSize = (11 * scale).sp,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(BusRadius.pill))
            .background(if (gold) t.goldLight else t.track)
            .padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

/** 与小程序 review.js 的 reviewStatus=4（审核不通过）同口径 */
private const val REVIEW_REJECTED = 4

@Composable
private fun OrderCard(o: ProductOrder, onClick: () -> Unit) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current

    // 车票卡：主券放商品明细，副券放订单状态（对齐小程序 parcel tab1 ticket-card）
    TicketCard(
        serial = "单号: ${o.orderNo.ifEmpty { "订单" }}",
        stubValue = o.statusLabel,
        stubLabel = "订单状态",
        onClick = onClick,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 2.dp)) {
            // parcel-head：shop 图标 + 首商品名
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(BusIcons.cart, null, tint = t.primaryOnLight, modifier = Modifier.size(20.dp))
                Text(
                    o.items.firstOrNull()?.name?.ifEmpty { null } ?: "购物订单",
                    color = t.ink,
                    fontSize = (15 * scale).sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 7.dp),
                )
            }

            // 商品明细：首商品图位 + 名称 + ¥价×数量 + 右侧合计/等X件（对齐 mall-goods-row）
            o.items.firstOrNull()?.let { g ->
                Row(Modifier.fillMaxWidth().padding(top = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(46.dp).clip(RoundedCornerShape(BusRadius.m)).background(t.track),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (g.image.isNotEmpty()) {
                            NetworkImage(
                                url = g.image,
                                contentDescription = g.name,
                                width = 400.dp,
                                height = 46.dp,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().height(46.dp),
                            )
                        } else {
                            Icon(BusIcons.leaf, null, tint = t.primaryOnLight, modifier = Modifier.size(22.dp))
                        }
                    }
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(
                            g.name, color = t.ink, fontSize = (14 * scale).sp,
                            fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "¥${formatPrice(g.price)} × ${g.count}",
                            color = t.textSecondary, fontSize = (12 * scale).sp,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        PriceText(formatPrice(o.amount), size = 14)
                        if (o.items.size > 1) {
                            Text(
                                "等 ${o.items.size} 件商品",
                                color = t.textTertiary, fontSize = (10.5 * scale).sp,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
            }

            // 配送提示（已发货=司机配送中，与 deliveryHint 同口径）
            if (o.canTrace) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp)
                        .clip(RoundedCornerShape(BusRadius.m)).background(t.primaryLight)
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(BusIcons.bus, null, tint = t.primaryOnLight, modifier = Modifier.size(14.dp))
                    Text(
                        if (o.onTheWay) "已发货 · 大巴捎货进城" else "已送达 · 点击查看订单详情",
                        color = t.primaryOnLight,
                        fontSize = (11.5 * scale).sp,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(o.createTime, color = t.textTertiary, fontSize = (11 * scale).sp, modifier = Modifier.weight(1f))
                Text("点击查看订单详情", color = t.textTertiary, fontSize = (11.5 * scale).sp)
                Icon(BusIcons.next, null, tint = t.textTertiary, modifier = Modifier.size(13.dp).padding(start = 2.dp))
            }
        }
    }
}

/**
 * 计数格的唯一口径：**只有请求成功才允许写数字**。
 * 后端两个分页接口（`/transport/send/page`、`/transport/product-order/page`）都回
 * `new PageResult<>(list, total)`，`total` 是真下发的键；旧写法念的是 `items.size` ——
 * 本页 pageSize=20，超过 20 单时那句"寄货 N 单"说的是**这一页拿回几条**，不是用户有几单。
 * error 非空 = 这一次没问到，返回 null 让页面说"未取到"，而不是把失败洗成 0。
 */
internal fun serverCount(error: String?, total: Int): Int? = if (error == null) total else null

/** 页首那一句的唯一话术出口：两段各自「数字 + 单」或「未取到」，一段失败不带着另一段一起变 0 */
internal fun parcelHeaderCountText(sendCount: Int?, orderCount: Int?): String =
    "${countSegment("寄货", sendCount)} · ${countSegment("商城", orderCount)}"

private fun countSegment(label: String, count: Int?): String =
    if (count == null) "${label}未取到" else "$label $count 单"
