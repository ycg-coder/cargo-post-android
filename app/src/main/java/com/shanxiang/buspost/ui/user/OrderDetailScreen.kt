package com.shanxiang.buspost.ui.user

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.shanxiang.buspost.core.ui.BusButton
import com.shanxiang.buspost.core.ui.BusButtonVariant
import com.shanxiang.buspost.core.ui.BusCard
import com.shanxiang.buspost.core.ui.BusIcons
import com.shanxiang.buspost.core.ui.BusPage
import com.shanxiang.buspost.core.ui.BusRadius
import com.shanxiang.buspost.core.ui.BusTag
import com.shanxiang.buspost.core.ui.ErrorState
import com.shanxiang.buspost.core.ui.InfoRow
import com.shanxiang.buspost.core.ui.LocalBusFontScale
import com.shanxiang.buspost.core.ui.LocalBusTokens
import com.shanxiang.buspost.core.ui.PriceText
import com.shanxiang.buspost.core.ui.QrView
import com.shanxiang.buspost.core.ui.SectionTitle
import com.shanxiang.buspost.core.ui.SkeletonList
import com.shanxiang.buspost.data.model.OrderItem
import com.shanxiang.buspost.data.model.ParcelTrace
import com.shanxiang.buspost.data.model.ProductOrder
import com.shanxiang.buspost.data.repo.Repo
import com.shanxiang.buspost.nav.Routes
import com.shanxiang.buspost.ui.common.BackTopBar
import com.shanxiang.buspost.ui.common.DemoNote
import com.shanxiang.buspost.ui.common.LocalNavController
import com.shanxiang.buspost.ui.common.ScreenViewModel
import com.shanxiang.buspost.ui.common.TraceView

class OrderDetailViewModel : ScreenViewModel() {
    var order by mutableStateOf<ProductOrder?>(null)
    var trace by mutableStateOf<ParcelTrace?>(null)
    var cancelled by mutableStateOf(false)
    var confirmCancel by mutableStateOf(false)

    fun load(id: Long) = work {
        val list = Repo.productOrders(pageSize = 50)
        order = list.items.firstOrNull { it.id == id } ?: list.items.firstOrNull()
        val tr = Repo.productOrderTrace(id)
        trace = tr.value
        mergeDemo(list.demo, tr.demo)
        error = list.error ?: tr.error
    }

    /** 取消之后重新读后端：状态与时间线都以后端回读为唯一真值，不在本机把订单改成"已取消" */
    fun cancel(id: Long) = work(reset = false) {
        Repo.cancelProductOrder(id)
        val list = Repo.productOrders(pageSize = 50)
        order = list.items.firstOrNull { it.id == id } ?: order
        trace = Repo.productOrderTrace(id).value
        mergeDemo(list.demo)
        error = list.error
        cancelled = true
    }
}

@Composable
fun OrderDetailScreen(id: Long, vm: OrderDetailViewModel = viewModel()) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val nav = LocalNavController.current

    LaunchedEffect(id) { vm.load(id) }

    Column {
        BackTopBar(title = vm.order?.statusLabel ?: "订单详情", subtitle = vm.order?.orderNo)
        BusPage(spacedBy = 12.dp) {
            DemoNote(vm.demo, text = "这单来自本地演示集，取消与追踪只在演示数据里生效。")
            vm.error?.let { ErrorState(it, onRetry = { vm.load(id) }) }
            if (vm.cancelled) {
                Text("已在演示模式下取消，真实环境需后端确认。", color = t.clayDeep, fontSize = (12 * scale).sp)
            }

            val o = vm.order
            if (vm.loading && o == null) {
                SkeletonList(3)
            } else if (o == null) {
                ErrorState("没找到这笔订单", onRetry = { vm.load(id) })
            } else {
                TicketHead(o)

                SectionTitle("商品明细")
                BusCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        o.items.forEachIndexed { i, item ->
                            ItemRow(item)
                            if (i < o.items.size - 1) Spacer(Modifier.height(9.dp))
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("合计", color = t.ink, fontSize = (14 * scale).sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            PriceText(formatPrice(o.amount), size = 20)
                        }
                    }
                }

                SectionTitle("收货与配送")
                BusCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        InfoRow(label = "收货人", value = "${o.receiverName} ${o.receiverMobile}".trim(), icon = BusIcons.person)
                        InfoRow(label = "地址", value = o.address.ifEmpty { "—" }, icon = BusIcons.location)
                        InfoRow(label = "送达站点", value = o.deliveryStation.ifEmpty { "—" }, icon = BusIcons.flag)
                        InfoRow(
                            label = "承运",
                            value = listOfNotNull(o.vehiclePlate.ifEmpty { null }, o.driverName.ifEmpty { null })
                                .joinToString(" · ").ifEmpty { "待排班" },
                            icon = BusIcons.bus,
                        )
                        if (o.shiftCode.isNotEmpty()) InfoRow(label = "班次", value = o.shiftCode, icon = BusIcons.calendar)
                        if (o.remark.isNotEmpty()) InfoRow(label = "备注", value = o.remark, icon = BusIcons.edit)
                        InfoRow(label = "下单时间", value = o.createTime.ifEmpty { "—" }, icon = BusIcons.clock)
                    }
                }

                vm.trace?.let { tr ->
                    SectionTitle("配送轨迹")
                    TraceView(
                        trace = tr,
                        statusLabel = o.statusLabel,
                        tone = o.statusTone,
                        onReplan = { vm.load(id) },
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (o.canCancel) {
                        BusButton(
                            text = "取消订单",
                            onClick = { vm.confirmCancel = true },
                            variant = BusButtonVariant.OUTLINE,
                            modifier = Modifier.weight(1f),
                            small = true,
                        )
                    }
                    BusButton(
                        text = "查看溯源",
                        onClick = { nav.navigate(Routes.goodsTrace(o.id)) },
                        variant = BusButtonVariant.OUTLINE,
                        modifier = Modifier.weight(1f),
                        small = true,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (vm.confirmCancel) {
        val o = vm.order
        AlertDialog(
            onDismissRequest = { vm.confirmCancel = false },
            title = { Text("取消这笔订单") },
            text = { Text("取消后班车不再配送，货款不发生。确定取消 ${o?.orderNo ?: ""} 吗？") },
            confirmButton = {
                TextButton(onClick = {
                    vm.confirmCancel = false
                    vm.cancel(id)
                }) { Text("取消订单") }
            },
            dismissButton = { TextButton(onClick = { vm.confirmCancel = false }) { Text("保留") } },
        )
    }
}

@Composable
private fun TicketHead(o: ProductOrder) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    com.shanxiang.buspost.core.ui.TicketCard(
        serial = o.orderNo,
        stubValue = "${o.items.sumOf { it.count }}",
        stubLabel = "件商品",
        stubColor = if (o.isCompleted) t.primary else t.clay,
    ) {
        Column(Modifier.weight(1f)) {
            Text(o.statusLabel, color = t.ink, fontSize = (16 * scale).sp, fontWeight = FontWeight.Bold)
            Text(
                o.receiverName.ifEmpty { "自提" } + " · " + o.address.take(18),
                color = t.textSecondary,
                fontSize = (12 * scale).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BusTag(if (o.vehiclePlate.isEmpty()) "待排班" else o.vehiclePlate, tone = o.statusTone)
                if (o.deliveryStation.isNotEmpty()) BusTag(o.deliveryStation)
            }
        }
    }
}

@Composable
private fun ItemRow(item: OrderItem) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (item.image.isNotEmpty()) {
            AsyncImage(
                model = item.image,
                contentDescription = item.name,
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(BusRadius.m)),
            )
        } else {
            Icon(BusIcons.parcel, null, tint = t.textTertiary, modifier = Modifier.size(32.dp))
        }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(item.name, color = t.ink, fontSize = (13.5 * scale).sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${item.count} 件 · 单价 ${formatPrice(item.price)}", color = t.textSecondary, fontSize = (12 * scale).sp)
        }
        PriceText(formatPrice(item.price * item.count), size = 14)
    }
}

/** 商品溯源：山货从哪个村、哪班车、几点发车 + 司机核验凭证 + 订单二维码票根 */
@Composable
fun GoodsTraceScreen(id: Long, vm: OrderDetailViewModel = viewModel()) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val nav = LocalNavController.current
    var showQr by remember { mutableStateOf(false) }

    LaunchedEffect(id) { vm.load(id) }

    Column {
        BackTopBar(
            title = vm.order?.items?.firstOrNull()?.name?.let { "$it 溯源" } ?: "商品溯源",
            subtitle = vm.order?.orderNo,
        )
        BusPage(spacedBy = 12.dp) {
            DemoNote(vm.demo)
            val o = vm.order
            if (vm.loading && o == null) {
                SkeletonList(2)
            } else if (o == null) {
                ErrorState("包裹轨迹暂时打不开", onRetry = { vm.load(id) })
            } else {
                SectionTitle("承运信息", subtitle = "班车装货与到村时间可追溯")
                BusCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        o.items.forEach { item ->
                            InfoRow(label = item.name, value = "${item.count} 件 · ${formatPrice(item.price)}", icon = BusIcons.parcel)
                        }
                        InfoRow(label = "送达站点", value = o.deliveryStation.ifEmpty { "—" }, icon = BusIcons.flag)
                        InfoRow(
                            label = "装车班次",
                            value = listOfNotNull(
                                o.routeName.ifEmpty { null },
                                o.shiftCode.ifEmpty { null },
                                o.vehiclePlate.ifEmpty { null },
                            ).joinToString(" · ").ifEmpty { "待排班" },
                            icon = BusIcons.bus,
                        )
                        InfoRow(
                            label = "司机",
                            value = listOfNotNull(o.driverName.ifEmpty { null }, o.driverMobile.ifEmpty { null })
                                .joinToString(" · ").ifEmpty { "—" },
                            icon = BusIcons.person,
                        )
                        InfoRow(label = "收货信息", value = "${o.receiverName} ${o.receiverMobile}", icon = BusIcons.location)
                        InfoRow(label = "下单时间", value = o.createTime.ifEmpty { "—" }, icon = BusIcons.clock)
                    }
                }

                val tr = vm.trace
                if (tr != null) {
                    // 司机已到达交付站点：醒目的到站横幅（小程序 trace.wxml:41-47）
                    if (tr.driverArrived) {
                        BusCard(modifier = Modifier.fillMaxWidth(), warm = true) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(BusIcons.location, null, tint = t.clayDeep, modifier = Modifier.size(18.dp))
                                Text(
                                    "司机已到达${tr.targetStation.ifEmpty { "交付站点" }}，请准备取件",
                                    color = t.clayDeep,
                                    fontSize = (13 * scale).sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    }

                    // 司机核验凭证：装车照 / 妥投照（小程序 trace.wxml:50-71）
                    val proofs = listOfNotNull(
                        tr.loadPhotoUrl.takeIf { it.isNotEmpty() }?.let { "装车核验" to (it to tr.loadTime) },
                        tr.deliverPhotoUrl.takeIf { it.isNotEmpty() }?.let { "妥投凭证" to (it to tr.deliverTime) },
                    )
                    if (proofs.isNotEmpty()) {
                        SectionTitle("司机核验凭证", subtitle = "司机现场拍照留存，可作为交付依据")
                        proofs.forEach { (label, pair) ->
                            val (url, time) = pair
                            BusCard(modifier = Modifier.fillMaxWidth()) {
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(label, color = t.ink, fontSize = (13.5 * scale).sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                        if (time.isNotEmpty()) {
                                            Text(time, color = t.textTertiary, fontSize = (11.5 * scale).sp)
                                        }
                                    }
                                    AsyncImage(
                                        model = url,
                                        contentDescription = label,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .padding(top = 8.dp)
                                            .fillMaxWidth()
                                            .height(170.dp)
                                            .clip(RoundedCornerShape(BusRadius.m)),
                                    )
                                }
                            }
                        }
                    }

                    // 订单二维码票根：到站出示给司机扫码核销（小程序 trace.wxml:74-79）
                    SectionTitle("取件凭证")
                    BusCard(modifier = Modifier.fillMaxWidth()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            if (showQr) {
                                QrView(text = tr.orderNo.ifEmpty { o.orderNo }, caption = "向司机出示此码核销取件")
                            }
                            BusButton(
                                text = if (showQr) "收起二维码" else "显示取件二维码",
                                variant = if (showQr) BusButtonVariant.OUTLINE else BusButtonVariant.PRIMARY,
                                icon = BusIcons.qr,
                                onClick = { showQr = !showQr },
                                modifier = Modifier.padding(top = if (showQr) 12.dp else 0.dp),
                            )
                            Text(
                                "单号 ${tr.orderNo.ifEmpty { o.orderNo }}（点击复制）",
                                color = t.textSecondary,
                                fontSize = (12 * scale).sp,
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .clickable {
                                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(tr.orderNo.ifEmpty { o.orderNo }))
                                    },
                            )
                        }
                    }

                    // 边界态：已取消 / 未发车
                    if (tr.cancelled) {
                        BusCard(modifier = Modifier.fillMaxWidth(), warm = true) {
                            Text("该订单已取消，运输轨迹不再更新", color = t.clayDeep, fontSize = (13 * scale).sp)
                        }
                    } else if (tr.notShipped) {
                        BusCard(modifier = Modifier.fillMaxWidth(), warm = true) {
                            Column {
                                Text("商品还未发车，暂无运输轨迹", color = t.ink, fontSize = (13.5 * scale).sp, fontWeight = FontWeight.Bold)
                                Text("发货后这里会展示大巴的实时轨迹", color = t.textSecondary, fontSize = (12 * scale).sp, modifier = Modifier.padding(top = 3.dp))
                            }
                        }
                    }

                    TraceView(
                        trace = tr,
                        statusLabel = tr.statusLabel,
                        tone = tr.tone,
                        onReplan = { vm.load(id) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
