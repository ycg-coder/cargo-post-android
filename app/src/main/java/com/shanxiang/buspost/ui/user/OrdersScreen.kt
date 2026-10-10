package com.shanxiang.buspost.ui.user

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.graphics.Color
import com.shanxiang.buspost.core.ui.BusCard
import com.shanxiang.buspost.core.ui.BusDivider
import com.shanxiang.buspost.core.ui.BusIcons
import com.shanxiang.buspost.core.ui.BusLazyPage
import com.shanxiang.buspost.core.ui.BusPullRefresh
import com.shanxiang.buspost.core.ui.BusRadius
import com.shanxiang.buspost.core.ui.BusSegmented
import com.shanxiang.buspost.core.ui.BusTag
import com.shanxiang.buspost.core.ui.BusTone
import com.shanxiang.buspost.core.ui.EmptyState
import com.shanxiang.buspost.core.ui.ErrorState
import com.shanxiang.buspost.core.ui.FilterChipRow
import com.shanxiang.buspost.core.ui.ListEndNote
import com.shanxiang.buspost.core.ui.LocalBusFontScale
import com.shanxiang.buspost.core.ui.LocalBusTokens
import com.shanxiang.buspost.core.ui.PriceText
import com.shanxiang.buspost.core.ui.SkeletonList
import com.shanxiang.buspost.core.ui.TicketCard
import com.shanxiang.buspost.core.ui.fg
import com.shanxiang.buspost.data.model.ProductOrder
import com.shanxiang.buspost.data.model.SendOrder
import com.shanxiang.buspost.data.repo.Repo
import com.shanxiang.buspost.nav.Routes
import com.shanxiang.buspost.ui.common.BackTopBar
import com.shanxiang.buspost.ui.common.DemoNote
import com.shanxiang.buspost.ui.common.LocalNavController
import com.shanxiang.buspost.ui.common.ScreenViewModel
import kotlinx.coroutines.delay

/**
 * 我的订单页 —— 对应小程序 pages/orders/orders。
 * 两种订单来源共用一个列表：商城订单（默认，带状态筛选/取消/溯源）与寄货订单（与「快递」页同源）。
 * 分页 pageSize=10 与小程序一致；触底自动续页，末尾显示「没有更多了」。
 */
class OrdersViewModel : ScreenViewModel() {

    /** 0 = 商城订单，1 = 寄货订单 */
    var source by mutableStateOf(0)
    var statusIndex by mutableStateOf(0)
    var productOrders by mutableStateOf<List<ProductOrder>>(emptyList())
    var sendOrders by mutableStateOf<List<SendOrder>>(emptyList())
    var pageNo by mutableStateOf(1)
    var total by mutableStateOf(0)
    var refreshing by mutableStateOf(false)
    var loadingMore by mutableStateOf(false)
    /** 网络失败（区别于"真的没有订单"的空态） */
    var loadError by mutableStateOf(false)

    /** 状态筛选：全部/待发货/已发货/已完成/已取消（索引 0 不带 status 参数） */
    val statusTabs = listOf("全部", "待发货", "已发货", "已完成", "已取消")
    private val statusParam: Int? get() = if (statusIndex == 0) null else statusIndex - 1

    val hasMore: Boolean get() = currentCount < total
    private val currentCount: Int
        get() = if (source == 0) productOrders.size else sendOrders.size

    fun reload() {
        pageNo = 1
        productOrders = emptyList()
        sendOrders = emptyList()
        total = 0
        loadError = false
        load(reset = true)
    }

    fun loadMore() {
        if (loading || loadingMore || !hasMore) return
        loadingMore = true
        pageNo += 1
        load(reset = false)
    }

    fun load(reset: Boolean = true) = work(reset = reset) {
        try {
            fetch()
            loadError = false
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            loadError = true
            error = com.shanxiang.buspost.data.repo.errorTextOf(e)
        } finally {
            loadingMore = false
        }
    }

    private suspend fun fetch() {
        if (source == 0) {
            val f = Repo.productOrders(pageNo = pageNo, pageSize = PAGE_SIZE, status = statusParam)
            // 失败就保留已上屏的列表，只挂错误条 + 重试；绝不把失败渲染成"没有订单"
            if (f.error != null) { demo = f.demo; error = f.error; return }
            productOrders = if (pageNo == 1) f.items else productOrders + f.items
            // 后端给了 total 就以它为准（演示集没有时才按本页是否满页推断）
            total = if (f.total > 0) f.total else if (f.items.size >= PAGE_SIZE) productOrders.size + 1 else productOrders.size
            demo = f.demo
            error = null
        } else {
            val f = Repo.sendOrders(pageNo = pageNo, pageSize = PAGE_SIZE)
            if (f.error != null) { demo = f.demo; error = f.error; return }
            sendOrders = if (pageNo == 1) f.items else sendOrders + f.items
            total = if (f.total > 0) f.total else if (f.items.size >= PAGE_SIZE) sendOrders.size + 1 else sendOrders.size
            demo = f.demo
            error = null
        }
    }

    fun switchSource(index: Int) {
        if (index == source) return
        source = index
        // 寄货订单的状态语义与商城不同，切来源时状态筛选重置为「全部」（与小程序一致）
        statusIndex = 0
        reload()
    }

    fun switchStatus(index: Int) {
        if (index == statusIndex) return
        statusIndex = index
        reload()
    }

    /** 取消以服务端为准：POST 成功后回到第 1 页重拉，绝不在本机把这条"抹掉" */
    fun cancel(orderId: Long) = work(reset = false) {
        Repo.cancelProductOrder(orderId)
        pageNo = 1
        fetch()
    }

    private companion object {
        const val PAGE_SIZE = 10
    }
}

@Composable
fun OrdersScreen(vm: OrdersViewModel = viewModel()) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val nav = LocalNavController.current
    var toCancel by remember { mutableStateOf<ProductOrder?>(null) }

    LaunchedEffect(Unit) { if (vm.productOrders.isEmpty() && vm.sendOrders.isEmpty()) vm.load() }

    Column {
        BackTopBar(
            title = "我的订单",
            subtitle = if (vm.source == 0) "商城订单 · 大巴带货到家" else "寄货订单 · 点卡片看物流详情",
        ) {
            Text(
                "刷新",
                color = t.onPrimary,
                fontSize = (13 * scale).sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(BusRadius.pill))
                    .clickable { vm.reload() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }

        BusPullRefresh(
            refreshing = vm.refreshing,
            onRefresh = {
                vm.refreshing = true
                vm.reload()
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            BusLazyPage(
                spacedBy = 10.dp,
                contentPadding = PaddingValues(16.dp),
            ) {
                // 订单来源：商城订单 / 寄货订单（小程序 .order-source-tabs）
                item {
                    BusSegmented(
                        options = listOf("商城订单", "寄货订单"),
                        selected = vm.source,
                        onSelect = { vm.switchSource(it) },
                    )
                }

                item { DemoNote(vm.demo) }

                // 状态筛选胶囊条（仅商城订单有此维度）
                if (vm.source == 0) {
                    item {
                        FilterChipRow(
                            options = vm.statusTabs,
                            selected = vm.statusIndex,
                            onSelect = { vm.switchStatus(it) },
                        )
                    }
                }

                if (vm.loading && vm.productOrders.isEmpty() && vm.sendOrders.isEmpty()) {
                    item { SkeletonList(3) }
                } else if (vm.loadError && vm.productOrders.isEmpty() && vm.sendOrders.isEmpty()) {
                    item {
                        ErrorState("没能加载订单列表", onRetry = { vm.reload() })
                    }
                } else if (vm.productOrders.isEmpty() && vm.sendOrders.isEmpty()) {
                    item {
                        EmptyState(
                            title = "还没有订单",
                            subtitle = if (vm.source == 0) "去逛逛山货集市，看中了大巴给你捎回家" else "山货出山、包裹进村，点这里寄一件",
                            icon = BusIcons.parcel,
                            actionText = if (vm.source == 0) "去逛逛" else "去寄一件",
                            onAction = {
                                if (vm.source == 0) nav.navigate(Routes.USER_SHELL)
                                else nav.navigate(Routes.SEND)
                            },
                        )
                    }
                }

                if (vm.source == 0) {
                    itemsIndexed(vm.productOrders, key = { _, it -> it.id }) { index, order ->
                        com.shanxiang.buspost.core.ui.AnimatedEnter(index) {
                            ProductOrderTicket(
                                order = order,
                                onOpen = { nav.navigate(Routes.orderDetail(order.id)) },
                                onTrace = { nav.navigate(Routes.goodsTrace(order.id)) },
                                onCancel = { toCancel = order },
                            )
                        }
                    }
                } else {
                    itemsIndexed(vm.sendOrders, key = { _, it -> it.id }) { index, order ->
                        com.shanxiang.buspost.core.ui.AnimatedEnter(index) {
                            SendOrderTicket(
                                order = order,
                                onOpen = { nav.navigate(Routes.trace(order.orderNo)) },
                            )
                        }
                    }
                }

                if (vm.hasMore) {
                    item {
                        Text(
                            if (vm.loadingMore) "正在加载…" else "点击加载更多",
                            color = t.primaryOnLight,
                            fontSize = (13 * scale).sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(BusRadius.m))
                                .clickable { vm.loadMore() }
                                .padding(vertical = 12.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                    // 触底自动续页
                    item {
                        LaunchedEffect(vm.pageNo, vm.source, vm.statusIndex) {
                            vm.loadMore()
                        }
                        Spacer(Modifier.height(1.dp))
                    }
                } else if (vm.productOrders.isNotEmpty() || vm.sendOrders.isNotEmpty()) {
                    item { ListEndNote() }
                }

                item { Spacer(Modifier.height(6.dp)) }
            }
        }
    }

    // 刷新完成收指示器
    LaunchedEffect(vm.loading) {
        if (!vm.loading) {
            delay(260)
            vm.refreshing = false
        }
    }

    toCancel?.let { o ->
        AlertDialog(
            onDismissRequest = { toCancel = null },
            title = { Text("取消订单") },
            text = { Text("确定取消该订单吗？") },
            confirmButton = {
                TextButton(onClick = {
                    toCancel = null
                    vm.cancel(o.id)
                }) { Text("确认取消") }
            },
            dismissButton = { TextButton(onClick = { toCancel = null }) { Text("再想想") } },
        )
    }
}

/** 商城订单车票卡：主券放商品清单与操作，副券放合计金额 */
@Composable
private fun ProductOrderTicket(
    order: ProductOrder,
    onOpen: () -> Unit,
    onTrace: () -> Unit,
    onCancel: () -> Unit,
) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val clip = LocalClipboardManager.current
    val dotLive = order.onTheWay

    TicketCard(
        modifier = Modifier.fillMaxWidth(),
        serial = "单号 ${order.orderNo}",
        stubValue = "¥${formatPrice(order.amount)}",
        stubLabel = "合计",
        onClick = onOpen,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                if (dotLive) {
                    Box(Modifier.size(6.dp).clip(androidx.compose.foundation.shape.CircleShape).background(t.primary))
                    Spacer(Modifier.size(5.dp))
                }
                Text(
                    order.statusLabel,
                    color = order.statusTone.fg(t),
                    fontSize = (12.5 * scale).sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            order.items.forEach { g ->
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(46.dp).clip(RoundedCornerShape(BusRadius.m)).background(t.track),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (g.image.isNotEmpty()) {
                            AsyncImage(
                                model = g.image,
                                contentDescription = g.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            androidx.compose.material3.Icon(
                                BusIcons.leaf, null,
                                tint = t.primaryOnLight,
                                modifier = Modifier.size(22.dp),
                            )
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
                    Text(
                        "¥${formatPrice(g.lineAmount)}",
                        color = t.ink, fontSize = (13 * scale).sp, fontWeight = FontWeight.Bold,
                    )
                }
            }

            // 配送提示：已发货/已完成引导看溯源（与小程序 deliveryHint 同口径）
            if (order.canTrace) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp)
                        .clip(RoundedCornerShape(BusRadius.m)).background(t.primaryLight)
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.Icon(
                        BusIcons.bus, null, tint = t.primaryOnLight, modifier = Modifier.size(14.dp),
                    )
                    Text(
                        if (order.onTheWay) "已发货 · 司机在路上，点「产地溯源」看司机与到站提醒"
                        else "已送达 · 点「产地溯源」看交付凭证",
                        color = t.primaryOnLight,
                        fontSize = (11.5 * scale).sp,
                        modifier = Modifier.padding(start = 6.dp).weight(1f),
                    )
                }
                // 车辆与司机信息（对标货拉拉/滴滴货运：运输中显示车牌和司机）
                if (order.onTheWay && (order.vehiclePlate.isNotEmpty() || order.driverName.isNotEmpty())) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 6.dp)
                            .clip(RoundedCornerShape(BusRadius.m)).background(t.goldLight)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.Icon(
                            BusIcons.location, null, tint = t.goldDeep, modifier = Modifier.size(13.dp),
                        )
                        Column(Modifier.padding(start = 6.dp).weight(1f)) {
                            if (order.vehiclePlate.isNotEmpty()) {
                                Text(
                                    "承运车辆：${order.vehiclePlate}",
                                    color = t.goldDeep,
                                    fontSize = (11.5 * scale).sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                            if (order.driverName.isNotEmpty()) {
                                Text(
                                    "司机：${order.driverName}" +
                                        if (order.driverArrived) " · 已到站" else "",
                                    color = t.textSecondary,
                                    fontSize = (10.5 * scale).sp,
                                    modifier = Modifier.padding(top = 1.dp),
                                )
                            }
                        }
                        if (order.routeName.isNotEmpty()) {
                            BusTag(order.routeName, tone = BusTone.NEUTRAL)
                        }
                    }
                }
            }

            BusDivider(Modifier.padding(top = 10.dp))

            Row(Modifier.fillMaxWidth().padding(top = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    order.createTime,
                    color = t.textTertiary,
                    fontSize = (11 * scale).sp,
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            clip.setText(AnnotatedString(order.orderNo))
                        },
                )
                if (order.canTrace) {
                    Text(
                        "产地溯源",
                        color = t.primaryOnLight,
                        fontSize = (12.5 * scale).sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(BusRadius.pill))
                            .clickable(onClick = onTrace)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                    Spacer(Modifier.size(6.dp))
                }
                if (order.canCancel) {
                    Text(
                        "取消订单",
                        color = t.danger,
                        fontSize = (12.5 * scale).sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(BusRadius.pill))
                            .clickable(onClick = onCancel)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}

/** 寄货订单车票卡：一张卡=一个包裹，副券放件数，点卡片进物流详情 */
@Composable
private fun SendOrderTicket(order: SendOrder, onOpen: () -> Unit) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val rejected = order.reviewStatus == REVIEW_REJECTED

    TicketCard(
        modifier = Modifier.fillMaxWidth(),
        serial = "单号 ${order.orderNo}",
        stubValue = "${order.itemCount}",
        stubLabel = "寄货件数",
        onClick = onOpen,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                BusTag(
                    if (rejected) "审核不通过" else order.statusLabel,
                    tone = if (rejected) BusTone.DANGER else order.statusTone,
                    dot = order.onTheWay,
                )
            }

            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(46.dp).clip(RoundedCornerShape(BusRadius.m)).background(t.track),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Icon(BusIcons.parcel, null, tint = t.primaryOnLight, modifier = Modifier.size(23.dp))
                }
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(
                        order.goodsName.ifEmpty { "寄货" },
                        color = t.ink, fontSize = (14 * scale).sp,
                        fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        buildString {
                            if (order.weightKg > 0) append("重量 ${order.weightKg}kg · ")
                            append("${order.itemCount} 件")
                        },
                        color = t.textSecondary, fontSize = (12 * scale).sp,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                Text(
                    "${order.itemCount} 件",
                    color = t.ink, fontSize = (13 * scale).sp, fontWeight = FontWeight.Bold,
                )
            }

            // 承运车辆信息（运输中才显示，对标快递100/菜鸟：实时车牌+ETA）
            if (order.onTheWay && order.vehiclePlate.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp)
                        .clip(RoundedCornerShape(BusRadius.m)).background(t.goldLight)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.Icon(
                        BusIcons.bus, null, tint = t.goldDeep, modifier = Modifier.size(13.dp),
                    )
                    Column(Modifier.padding(start = 6.dp).weight(1f)) {
                        Text(
                            "承运车辆：${order.vehiclePlate}" +
                                if (order.driverName.isNotEmpty()) " · ${order.driverName}" else "",
                            color = t.goldDeep,
                            fontSize = (11.5 * scale).sp,
                            fontWeight = FontWeight.Medium,
                        )
                        val eta = order.carrierEtaMinutes
                        if (eta != null && eta > 0) {
                            Text(
                                if (order.carrierArrived) "已到站 ${order.carrierArrivedStation}"
                                else if (eta <= 5) "即将到达 · 约 $eta 分钟"
                                else "距到达约 $eta 分钟",
                                color = if (eta <= 5) t.danger else t.textSecondary,
                                fontSize = (10.5 * scale).sp,
                                fontWeight = if (eta <= 5) FontWeight.Medium else FontWeight.Normal,
                                modifier = Modifier.padding(top = 1.dp),
                            )
                        } else if (order.carrierArrivedStation.isNotEmpty()) {
                            Text(
                                "司机已到达 ${order.carrierArrivedStation}",
                                color = t.textSecondary,
                                fontSize = (10.5 * scale).sp,
                                modifier = Modifier.padding(top = 1.dp),
                            )
                        }
                    }
                }
            }

            BusDivider(Modifier.padding(top = 10.dp))

            Row(Modifier.fillMaxWidth().padding(top = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(order.createTime, color = t.textTertiary, fontSize = (11 * scale).sp, modifier = Modifier.weight(1f))
                Text(
                    "点卡片看物流详情",
                    color = t.textTertiary,
                    fontSize = (11.5 * scale).sp,
                )
                androidx.compose.material3.Icon(
                    BusIcons.next, null, tint = t.textTertiary, modifier = Modifier.size(13.dp).padding(start = 2.dp),
                )
            }
        }
    }
}

/** 与小程序 review.js REASON_TEXT_MAP 对齐的承运拒运状态码 */
private const val REVIEW_REJECTED = 4

/** 商城订单状态色只有一处真值：`ProductOrder.statusTone` 语义 + `BusTone.fg` 前景色（§18），这里不留第二张表 */
