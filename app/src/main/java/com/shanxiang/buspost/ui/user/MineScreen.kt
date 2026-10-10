package com.shanxiang.buspost.ui.user

import androidx.compose.foundation.background
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shanxiang.buspost.core.AppDomain
import com.shanxiang.buspost.core.AppState
import com.shanxiang.buspost.core.ui.BusAvatar
import com.shanxiang.buspost.core.ui.BusButton
import com.shanxiang.buspost.core.ui.BusButtonVariant
import com.shanxiang.buspost.core.ui.BusCard
import com.shanxiang.buspost.core.ui.BusDivider
import com.shanxiang.buspost.core.ui.BusIcons
import com.shanxiang.buspost.core.ui.BusPage
import com.shanxiang.buspost.core.ui.BusRadius
import com.shanxiang.buspost.core.ui.BusTag
import com.shanxiang.buspost.core.ui.BusTone
import com.shanxiang.buspost.core.ui.InfoRow
import com.shanxiang.buspost.core.ui.LocalBusFontScale
import com.shanxiang.buspost.core.ui.LocalBusTokens
import com.shanxiang.buspost.core.ui.SectionTitle
import com.shanxiang.buspost.data.model.Role
import com.shanxiang.buspost.data.repo.DRIVER_UNBOUND
import com.shanxiang.buspost.data.repo.Repo
import com.shanxiang.buspost.data.store.Prefs
import com.shanxiang.buspost.nav.Routes
import com.shanxiang.buspost.ui.common.DemoNote
import com.shanxiang.buspost.ui.common.EntryTile
import com.shanxiang.buspost.ui.common.LocalNavController
import com.shanxiang.buspost.ui.common.ScreenViewModel
import kotlinx.coroutines.launch

class MineViewModel : ScreenViewModel() {
    var nickname by mutableStateOf(Prefs.nickname)
    var mobile by mutableStateOf(Prefs.mobile)
    /**
     * null = **这一次没问到**（还没加载完，或后端回了失败）；只有请求成功才允许写成数字。
     * 旧写法把三者初始化为 0 且失败后不写回，于是"接口挂了"被念成"你一条寄货都没有"。
     */
    var sendCount by mutableStateOf<Int?>(null)
    var orderCount by mutableStateOf<Int?>(null)
    var addressCount by mutableStateOf<Int?>(null)

    fun load() = work {
        val s = Repo.sendOrders(pageSize = 1)
        val o = Repo.productOrders(pageSize = 1)
        val a = Repo.addresses()
        // 取的是分页总数（后端 PageResult.total），不是"这一页拿回几条"——pageSize=1 时后者恒为 1。
        sendCount = if (s.error == null) s.total else null
        orderCount = if (o.error == null) o.total else null
        addressCount = if (a.error == null) a.total else null
        nickname = Prefs.nickname
        mobile = Prefs.mobile
        mergeDemo(s.demo, o.demo, a.demo)
        error = s.error ?: o.error ?: a.error
    }

    /**
     * 进司机端的唯一入口（审计 §29）：先让后端确认这个账号确实有司机档案，再切本机角色。
     * 与 `LoginViewModel.submit` 同一条纪律 —— 原来这里直接把角色写成 DRIVER 就跳壳，
     * 非司机账号进来只看到一排"暂无任务"，把"你没有司机档案"这个真因洗成了"今天没活"。
     *
     * F-3 ③ 之后这里是**两次写入**：`setAccountRole` 记的是后端授予的事实，`setActiveDomain` 记的是
     * "这人此刻选择进司机端"这个动作。合在一起才等价于旧的 `setRole(DRIVER)` + 跳壳 ——
     * 反过来（司机壳点「用户版」）只能改后者，见司机壳 DriverShell.kt。
     */
    fun enterDriverMode(onGranted: () -> Unit) = work(reset = false) {
        val bound = Repo.bindDriver()
        mergeDemo(bound.demo)
        if ((bound.value?.driverId ?: 0L) <= 0L) {
            error = bound.error ?: DRIVER_UNBOUND
            return@work
        }
        AppState.setAccountRole(Role.DRIVER)
        AppState.setActiveDomain(AppDomain.DRIVER)
        onGranted()
    }
}

@Composable
fun MineScreen(vm: MineViewModel = viewModel()) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val nav = LocalNavController.current
    val unread by AppState.unread.collectAsStateWithLifecycle()
    val role = Prefs.role
    var confirmLogout by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { vm.load() }

    BusPage(spacedBy = 12.dp) {
        BusCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BusAvatar(name = vm.nickname.ifEmpty { "用户" }, url = Prefs.avatar.ifEmpty { null })
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        vm.nickname.ifEmpty { "用户" },
                        color = t.ink,
                        fontSize = (17 * scale).sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        vm.mobile.ifEmpty { "未绑定手机号" },
                        color = t.textSecondary,
                        fontSize = (12.5 * scale).sp,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                BusTag(
                    // 小程序 mine.wxml:36 的角色标签就是「村民」/司机
                    if (role == Role.DRIVER) "司机" else "村民",
                    tone = if (role == Role.DRIVER) BusTone.CLAY else BusTone.PRIMARY,
                )
            }
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("寄货", vm.sendCount)
                Stat("订单", vm.orderCount)
                Stat("地址", vm.addressCount)
                Stat("未读", unread)
            }
        }

        DemoNote(vm.demo, text = "订单与地址数量来自本地演示集，仅作占位展示。")

        // 我的服务：与小程序 mine.wxml「我的服务」分组一致（我的订单 / 我的寄货 / 我的购物 / 切换为司机模式）
        SectionTitle("我的服务")
        BusCard(modifier = Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Column {
                MineRow(BusIcons.parcel, "我的订单", "商城订单状态与取消", {
                    nav.navigate(Routes.ORDERS)
                })
                BusDivider()
                MineRow(BusIcons.send, "我的寄货", "取件码与物流轨迹", {
                    AppState.openParcel(0)
                    nav.navigate(Routes.PARCEL)
                })
                BusDivider()
                MineRow(BusIcons.cart, "我的购物", "商城包裹的物流进度", {
                    AppState.openParcel(1)
                    nav.navigate(Routes.PARCEL)
                })
                BusDivider()
                MineRow(BusIcons.bus, "切换为司机模式", "发车 · 装车 · 妥投", {
                    // 角色切换由后端档案决定：绑定成功才进司机壳（审计 §29）
                    vm.enterDriverMode {
                        nav.navigate(Routes.DRIVER_SHELL) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                })
                vm.error?.let {
                    BusDivider()
                    Text(
                        it,
                        color = t.danger,
                        fontSize = (12.5f * scale).sp,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
        }

        // 农户服务：醒目的大按钮入口（小程序 .farmer-service 的陶土橙主行动）
        BusCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text(
                    "农户服务",
                    color = t.ink,
                    fontSize = (15 * scale).sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "语音下单 · 大巴承运 · 直达城里",
                    color = t.textTertiary,
                    fontSize = (11.5 * scale).sp,
                    modifier = Modifier.padding(top = 3.dp),
                )
                BusButton(
                    text = "我要寄货",
                    onClick = { nav.navigate(Routes.SEND) },
                    variant = BusButtonVariant.CLAY,
                    icon = BusIcons.send,
                    modifier = Modifier.padding(top = 11.dp),
                )
            }
        }

        // 服务与设置：与小程序 mine.wxml:90-127 同构的 menu-item 行列表（顺序一致）
        SectionTitle("服务与设置")
        BusCard(modifier = Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Column {
                MineRow(BusIcons.location, "收货地址", "下单时直接选", {
                    nav.navigate(Routes.ADDRESS)
                })
                BusDivider()
                MineRow(BusIcons.edit, "意见反馈", "告诉站点缺什么", {
                    nav.navigate(Routes.FEEDBACK)
                })
                BusDivider()
                MineRow(BusIcons.bell, "消息通知", if (unread > 0) "$unread 条未读" else "到车与状态提醒", {
                    nav.navigate(Routes.NOTIFICATIONS)
                })
                BusDivider()
                MineRow(BusIcons.person, "个人资料", "昵称与手机号", {
                    nav.navigate(Routes.PROFILE)
                })
                BusDivider()
                MineRow(BusIcons.settings, "设置", "主题 / 适老 / 提醒", {
                    nav.navigate(Routes.SETTINGS)
                })
            }
        }

        // 提醒与显示：三项都是设置的开关状态，整卡可点直达设置页（去掉了原「Android 专属能力」这种面向开发者的节名）
        SectionTitle("提醒与显示")
        BusCard(modifier = Modifier.fillMaxWidth().clickable { nav.navigate(Routes.SETTINGS) }) {
            Column {
                InfoRow(
                    label = "后台消息提醒",
                    value = if (Prefs.notifyEnabled) "已开启（每 15 分钟）" else "已关闭",
                    icon = BusIcons.bell,
                    valueColor = if (Prefs.notifyEnabled) t.primaryOnLight else t.textTertiary,
                    showNext = true,
                )
                InfoRow(
                    label = "到车提醒",
                    value = if (Prefs.arrivalAlertEnabled) "车到前 10 分钟通知" else "未开启",
                    icon = BusIcons.clock,
                    valueColor = if (Prefs.arrivalAlertEnabled) t.clayDeep else t.textTertiary,
                    showNext = true,
                )
                InfoRow(
                    label = "适老大字",
                    value = if (Prefs.elderlyMode) "已开启" else "标准字号",
                    icon = BusIcons.info,
                    showNext = true,
                )
            }
        }

        // 退出登录：对齐 mine.wxml:129-137（menu-section 里的 danger 行），保留二次确认
        BusCard(modifier = Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            MineRow(BusIcons.close, "退出登录", "清除本地登录状态", {
                confirmLogout = true
            }, danger = true)
        }
        Spacer(Modifier.height(8.dp))
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("退出登录") },
            text = { Text("退出后本地会话清空，后台提醒会停止。已保存的草稿与地址不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    scope.launch { Repo.logout() }
                    AppState.kickToLogin()
                }) { Text("退出") }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("再想想") } },
        )
    }
}

@Composable
private fun RowScope.Stat(label: String, value: Int?) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    Column(
        Modifier
            .weight(1f)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // null 显示"未取到"而不是 0：把"没问到"念成"数量为 0"是这一屏原来唯一的假话
        Text(value?.toString() ?: "—", color = t.ink, fontSize = (18 * scale).sp, fontWeight = FontWeight.Black)
        Text(label, color = t.textTertiary, fontSize = (11 * scale).sp, modifier = Modifier.padding(top = 2.dp))
    }
}

/** 我的服务菜单行：图标托底 + 标题副标题 + 右箭头（等价小程序 .menu-item） */
@Composable
private fun MineRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(BusRadius.m))
                .background(if (danger) t.danger.copy(alpha = 0.10f) else t.primaryLight),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(
                icon, null,
                tint = if (danger) t.danger else t.primaryOnLight,
                modifier = Modifier.size(19.dp),
            )
        }
        Column(Modifier.weight(1f).padding(start = 11.dp)) {
            Text(title, color = t.ink, fontSize = (14.5f * scale).sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = t.textTertiary, fontSize = (11.5f * scale).sp, modifier = Modifier.padding(top = 2.dp))
        }
        androidx.compose.material3.Icon(BusIcons.next, null, tint = t.textTertiary, modifier = Modifier.size(17.dp))
    }
}