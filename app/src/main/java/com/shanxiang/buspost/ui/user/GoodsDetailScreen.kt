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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shanxiang.buspost.core.ui.NetworkImage
import com.shanxiang.buspost.core.ui.BusButton
import com.shanxiang.buspost.core.ui.BusCard
import com.shanxiang.buspost.core.ui.BusIcons
import com.shanxiang.buspost.core.ui.BusPage
import com.shanxiang.buspost.core.ui.BusRadius
import com.shanxiang.buspost.core.ui.BusTag
import com.shanxiang.buspost.core.ui.BusTextField
import com.shanxiang.buspost.core.ui.BusTone
import com.shanxiang.buspost.core.ui.ErrorState
import com.shanxiang.buspost.core.ui.LocalBusFontScale
import com.shanxiang.buspost.core.ui.LocalBusTokens
import com.shanxiang.buspost.core.ui.PriceText
import com.shanxiang.buspost.core.ui.SectionTitle
import com.shanxiang.buspost.core.ui.SkeletonList
import com.shanxiang.buspost.core.ui.Stepper
import com.shanxiang.buspost.data.model.Product
import com.shanxiang.buspost.data.repo.Repo
import com.shanxiang.buspost.data.store.Prefs
import com.shanxiang.buspost.nav.Routes
import com.shanxiang.buspost.ui.common.BackTopBar
import com.shanxiang.buspost.ui.common.DemoNote
import com.shanxiang.buspost.ui.common.LocalNavController
import com.shanxiang.buspost.ui.common.ScreenViewModel

/** 手机号校验：11 位、1 开头、纯数字（与小程序 util.validatePhone 等价） */
private fun isValidPhone(s: String): Boolean = s.length == 11 && s[0] == '1' && s.all { it.isDigit() }

class GoodsDetailViewModel : ScreenViewModel() {
    var product by mutableStateOf<Product?>(null)
    var count by mutableStateOf("1")
    var remark by mutableStateOf("")
    var receiverName by mutableStateOf("")
    var receiverMobile by mutableStateOf("")
    var receiverAddress by mutableStateOf("")
    var submitted by mutableStateOf<String?>(null)
    /** 后端订单列表是否真的回读到了这一单 */
    var submittedConfirmed by mutableStateOf(true)
    var submittedDemo by mutableStateOf(false)
    var submitting by mutableStateOf(false)

    /** 已售罄：只有后端真的回传 0/负数才算售罄；`stock` 缺键时不下这个结论（售罄判定归后端，下单时它会拒）。 */
    val soldOut: Boolean get() = product?.soldOut == true

    fun load(id: Long) = work {
        val p = Repo.product(id)
        product = p.value
        // 预填收货信息：优先默认地址（对齐小程序"预填登录手机号"的体验）
        val d = Repo.addresses().items.firstOrNull { it.isDefault } ?: Repo.addresses().items.firstOrNull()
        receiverName = d?.name ?: ""
        receiverMobile = d?.mobile ?: ""
        receiverAddress = if (d != null) (d.regionLabel + " " + d.detail).trim() else ""
        mergeDemo(p.demo)
        error = p.error
    }

    fun submit() {
        val p = product ?: return
        if (soldOut) return
        if (receiverName.isBlank()) { error = "请输入收货人姓名"; return }
        if (!isValidPhone(receiverMobile.trim())) { error = "请输入正确的联系电话"; return }
        if (receiverAddress.isBlank()) { error = "请输入收货地址"; return }
        val pieces = count.toIntOrNull()?.coerceIn(1, maxCount(p)) ?: 1
        work(reset = false) {
            submitting = true
            try {
                // 请求体与小程序 goods/detail/detail.js:183-191 一致（手输 receiver* 自由文本）
                val ack = Repo.createProductOrder(
                    productId = p.id,
                    quantity = pieces,
                    receiverName = receiverName.trim(),
                    receiverMobile = receiverMobile.trim(),
                    receiverAddress = receiverAddress.trim(),
                    remark = remark,
                )
                // 被本地幂等闸拦下（F-2 第 6 步）：一个字节都没出网，既不能说"已下单"也不能说"下单失败"，
                // 更不能往下走去读列表——读到的那条可能是**上一次**点击的单，把它当成本次的结果就是替用户编订单号
                if (ack.notSent) {
                    error = "同一份订单还在创建中，本次点击没有发给后端；请稍后到「我的订单」核对，不要重复提交"
                    return@work
                }
                // 「提交返回 200」不等于「订单已入账」：立刻重拉后端订单列表，以服务端那条为唯一真值
                val page = Repo.productOrders(1, 20)
                val server = page.items.firstOrNull { it.orderNo.isNotEmpty() && it.orderNo == ack.orderNo }
                    ?: page.items.firstOrNull()
                val no = server?.orderNo?.ifEmpty { ack.orderNo }.orEmpty().ifEmpty { ack.orderNo }
                if (no.isEmpty()) {
                    error = page.error ?: "后端未回订单号，请到「我的订单」核对后再试"
                    return@work
                }
                submitted = no
                submittedConfirmed = page.error == null
                submittedDemo = ack.demo || page.demo
            } finally {
                submitting = false
            }
        }
    }

    /** 数量上限 = 库存（小程序 detail.js:128-134），库存未给时不编一个上限出来 */
    fun maxCount(p: Product): Int = p.stock?.takeIf { it > 0 } ?: 99
}

@Composable
fun GoodsDetailScreen(id: Long, vm: GoodsDetailViewModel = viewModel()) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val nav = LocalNavController.current
    var preview by remember { mutableStateOf(false) }
    val guest = !Prefs.loggedIn
    var showLoginDialog by remember { mutableStateOf(false) }

    LaunchedEffect(id) { vm.load(id) }

    val p = vm.product
    val soldOut = vm.soldOut
    val maxC = if (p != null) vm.maxCount(p) else 99
    val countNum = vm.count.toIntOrNull()?.coerceIn(1, maxC) ?: 1
    val total = (p?.price ?: 0.0) * countNum

    Box(Modifier.fillMaxSize()) {
        Column {
            BackTopBar(title = p?.name ?: "山货详情")
            
            // 游客模式提示条：可以逛，但下单需要登录
            if (guest) {
                BusCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    warm = true,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(BusIcons.info, null, tint = t.clayDeep, modifier = Modifier.size(18.dp))
                        Text(
                            "登录后即可下单",
                            color = t.clayDeep,
                            fontSize = (13 * scale).sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(start = 8.dp).weight(1f),
                        )
                        Text(
                            "去登录",
                            color = t.primaryOnLight,
                            fontSize = (13 * scale).sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(BusRadius.s))
                                .clickable { showLoginDialog = true }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            
            BusPage(spacedBy = 12.dp) {
                DemoNote(vm.demo)
                vm.error?.let { ErrorState(it, onRetry = { vm.load(id) }) }

                if (vm.loading && p == null) {
                    SkeletonList(2)
                } else if (p == null) {
                    ErrorState("这件山货暂时看不到，可能已下架", onRetry = { vm.load(id) })
                } else {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(230.dp)
                            .clip(RoundedCornerShape(BusRadius.l))
                            .background(t.skeletonA)
                            .clickable(enabled = p.image.isNotEmpty()) { preview = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (p.image.isNotEmpty()) {
                            NetworkImage(
                                url = p.image,
                                contentDescription = p.name,
                                width = 400.dp,
                                height = 230.dp,
                                modifier = Modifier.fillMaxWidth().height(230.dp),
                                contentScale = ContentScale.Crop,
                            )
                            Icon(
                                BusIcons.search, null,
                                tint = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp)
                                    .size(20.dp),
                            )
                        } else {
                            Icon(BusIcons.parcel, null, tint = t.textTertiary, modifier = Modifier.size(52.dp))
                        }
                    }

                    BusCard(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(p.name, color = t.ink, fontSize = (19 * scale).sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                                if (soldOut) BusTag("已售罄", tone = BusTone.DANGER)
                            }
                            if (p.subtitle.isNotEmpty()) {
                                Text(
                                    p.subtitle,
                                    color = t.textSecondary,
                                    fontSize = (13 * scale).sp,
                                    modifier = Modifier.padding(top = 5.dp),
                                )
                            }
                            Row(
                                Modifier.padding(top = 10.dp),
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                PriceText(formatPrice(p.price), size = 24)
                                Text("/ ${p.unit}", color = t.textTertiary, fontSize = (12 * scale).sp)
                                // 划线原价：后端 12 个键里没有这个口径 ⇒ 没有就不提，不许自己乘个 1.25 画一条假划线
                                val origin = p.originPrice
                                if (origin != null && origin > p.price) {
                                    Text("¥${formatPrice(origin)}", color = t.textTertiary, fontSize = (12 * scale).sp)
                                }
                            }
                            Row(Modifier.padding(top = 9.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (p.category.isNotEmpty()) BusTag(p.category, tone = BusTone.PRIMARY)
                                if (p.village.isNotEmpty()) BusTag("产地 ${p.village}", tone = BusTone.CLAY)
                                BusTag(p.stockText, tone = if (p.soldOut) BusTone.DANGER else BusTone.NEUTRAL)
                                // 销量：后端没有销量口径，缺键时这一枚角标整个不出现（旧写法无条件渲染"已售 0"）
                                val sold = p.sales
                                if (sold != null) BusTag("已售 $sold")
                            }
                        }
                    }

                    if (soldOut) {
                        BusCard(modifier = Modifier.fillMaxWidth(), warm = true) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(BusIcons.info, null, tint = t.clayDeep, modifier = Modifier.size(18.dp))
                                Text(
                                    "这件山货暂时售罄，可先看看其他村的好货",
                                    color = t.clayDeep,
                                    fontSize = (13 * scale).sp,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    }

                    if (p.intro.isNotEmpty()) {
                        SectionTitle("山货介绍")
                        BusCard(modifier = Modifier.fillMaxWidth()) {
                            Text(p.intro, color = t.textSecondary, fontSize = (13 * scale).sp, lineHeight = (20 * scale).sp)
                        }
                    }

                    SectionTitle("下单信息")
                    BusCard(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(BusIcons.parcel, null, tint = t.textTertiary, modifier = Modifier.size(17.dp))
                                Text(
                                    "购买件数",
                                    color = t.ink,
                                    fontSize = (14 * scale).sp,
                                    modifier = Modifier.padding(start = 7.dp, end = 10.dp),
                                )
                                Stepper(
                                    value = vm.count,
                                    onValueChange = { if (!soldOut && !guest) vm.count = it },
                                    min = 1.0,
                                    max = maxC.toDouble(),
                                    unit = p.unit,
                                )
                            }
                            BusDividerGap()
                            BusTextField(
                                value = vm.receiverName,
                                onValueChange = { vm.receiverName = it },
                                label = "收货人",
                                placeholder = if (guest) "登录后填写" else "请输入收货人姓名",
                                icon = BusIcons.person,
                                enabled = !soldOut && !guest,
                            )
                            BusDividerGap()
                            BusTextField(
                                value = vm.receiverMobile,
                                onValueChange = { vm.receiverMobile = it },
                                label = "联系电话",
                                placeholder = if (guest) "登录后填写" else "请输入 11 位手机号",
                                icon = BusIcons.phone,
                                keyboardType = KeyboardType.Number,
                                enabled = !soldOut && !guest,
                            )
                            BusDividerGap()
                            BusTextField(
                                value = vm.receiverAddress,
                                onValueChange = { vm.receiverAddress = it },
                                label = "收货地址",
                                placeholder = if (guest) "登录后填写" else "如：云山村 3 组，村口小卖部",
                                icon = BusIcons.location,
                                minLines = 2,
                                singleLine = false,
                                enabled = !soldOut && !guest,
                            )
                            BusDividerGap()
                            BusTextField(
                                value = vm.remark,
                                onValueChange = { vm.remark = it },
                                label = "备注",
                                placeholder = if (guest) "登录后填写" else "比如：到村口小卖部打电话",
                                minLines = 2,
                                singleLine = false,
                                enabled = !soldOut && !guest,
                            )
                        }
                    }

                    BusCard(modifier = Modifier.fillMaxWidth(), warm = true) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("货到付款 · 班车到村自提", color = t.textSecondary, fontSize = (12 * scale).sp)
                                Text(
                                    "合计 ${formatPrice(total)} 元（含运费试算）",
                                    color = t.ink,
                                    fontSize = (14 * scale).sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 3.dp),
                                )
                            }
                            PriceText(formatPrice(total), size = 22)
                        }
                    }

                    BusButton(
                        text = when {
                            soldOut -> "已售罄"
                            guest -> "登录后可下单"
                            else -> "提交订单（货到付款）"
                        },
                        onClick = {
                            when {
                                soldOut -> {}
                                guest -> showLoginDialog = true
                                else -> vm.submit()
                            }
                        },
                        loading = vm.submitting,
                        enabled = !soldOut,
                        icon = if (guest) BusIcons.person else BusIcons.cart,
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }

        if (preview && p != null && p.image.isNotEmpty()) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.94f)).clickable { preview = false },
                contentAlignment = Alignment.Center,
            ) {
                NetworkImage(
                    url = p.image,
                    contentDescription = p.name,
                    width = 400.dp,
                    height = 700.dp,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit,
                )
                Text(
                    "点击任意处关闭",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = (12 * scale).sp,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp),
                )
            }
        }
    }

    vm.submitted?.let { no ->
        AlertDialog(
            onDismissRequest = { vm.submitted = null },
            title = { Text("下单成功") },
            text = {
                Text(
                    when {
                        vm.submittedDemo -> "订单号 $no 已生成，当前为体验模式，数据暂未同步到后台。"
                        !vm.submittedConfirmed -> "订单号 $no 已提交，系统正在确认中。请到「我的订单」查看，确认前不要重复下单。"
                        else -> "订单号 $no，已确认成功。班车装货后会推送发货提醒。"
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.submitted = null
                    nav.popBackStack()
                }) { Text("知道了") }
            },
        )
    }

    // 游客登录拦截弹窗
    if (showLoginDialog) {
        AlertDialog(
            onDismissRequest = { showLoginDialog = false },
            title = { Text("需要登录") },
            text = { Text("下单前请先登录或注册，登录后即可享受完整的购物功能。") },
            confirmButton = {
                TextButton(onClick = {
                    showLoginDialog = false
                    nav.navigate(Routes.LOGIN)
                }) { Text("去登录") }
            },
            dismissButton = {
                TextButton(onClick = { showLoginDialog = false }) { Text("先逛逛") }
            },
        )
    }
}

/** InfoRow 之间的细线，避免每个页面重复画 */
@Composable
private fun BusDividerGap() {
    val t = LocalBusTokens.current
    Box(Modifier.fillMaxWidth().height(1.dp).background(t.line))
}
