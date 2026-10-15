package com.shanxiang.buspost.ui.driver

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shanxiang.buspost.core.AppDomain
import com.shanxiang.buspost.core.AppState
import com.shanxiang.buspost.core.ui.BusDialogAction
import com.shanxiang.buspost.core.ui.BusAvatar
import com.shanxiang.buspost.core.ui.BusCard
import com.shanxiang.buspost.core.ui.BusDivider
import com.shanxiang.buspost.core.ui.BusIcons
import com.shanxiang.buspost.core.ui.BusPage
import com.shanxiang.buspost.core.ui.BusRadius
import com.shanxiang.buspost.core.ui.BusTag
import com.shanxiang.buspost.core.ui.BusTone
import com.shanxiang.buspost.core.ui.LocalBusFontScale
import com.shanxiang.buspost.core.ui.LocalBusTokens
import com.shanxiang.buspost.core.ui.SectionTitle
import com.shanxiang.buspost.data.model.Role
import com.shanxiang.buspost.data.repo.Repo
import com.shanxiang.buspost.data.store.Prefs
import com.shanxiang.buspost.nav.Routes
import com.shanxiang.buspost.ui.common.LocalNavController
import kotlinx.coroutines.launch

/**
 * 司机端「我的」（2026-10-10 新增，替代原底部导航第 4 格「用户版」）。
 *
 * 布局对齐用户端 `MineScreen`（同一套 MineRow 视觉语言），但内容按司机域裁剪：
 * · 工作服务只放司机域自带入口（调度消息 / 异常上报），不掺用户端业务页；
 * · 「切换为用户版」从底部导航降级为菜单行 —— 驾驶途中误触底部第 4 格会整壳跳转，
 *   收进「我的」多一层距离，正好符合"避免干扰驾驶操作"的要求；
 * · 切换只写视图域（`setActiveDomain`），账号角色由后端档案决定（F-3 ③ 纪律，与用户端
 *   `MineScreen.enterDriverMode` 对称：那边显式授予、这边只切视图）。
 *
 * ⚠ 探针契约（改本文件必须连带核对）：
 * · `tools/check_shell_routing_probes.sh` 的 SR-f2 在本文件数 `setActiveDomain(AppDomain.USER)`（恰好 1）；
 * · `tools/check_role_reconcile_probes.sh` 的 RD4 数「回用户壳」导航字面量（Workbench + 本文件合数 2）；
 * · `tools/role_reconcile_negative_controls.sh` 的 RR-e2 在本文件的切换行后注入角色写（注入必须变红）。
 */
@Composable
fun DriverMineScreen() {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val nav = LocalNavController.current
    val unread by AppState.driverUnread.collectAsStateWithLifecycle()
    var confirmLogout by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    BusPage(spacedBy = 12.dp) {
        // 用户卡：与用户端 MineScreen 同构（头像 + 昵称 + 手机号 + 角色签）
        //
        // 排班档案名与账号昵称是**后端两张表里的两个值**，云端实测（13800138001）就是不一致的：
        // 会员表 nickname=张建国，司机档案 name=周建国。以前这一格只念账号昵称，
        // 而工作台念档案名 —— 同一个司机在两块屏上叫两个名字，谁都不知道哪个是真的。
        // 现在以"今天开哪台车的名字"为主（工作台/交接单都用它），账号名如实标成第二行。
        BusCard(modifier = Modifier.fillMaxWidth()) {
            val rosterName = Prefs.driverName
            val accountName = Prefs.nickname
            Row(verticalAlignment = Alignment.CenterVertically) {
                BusAvatar(name = rosterName.ifEmpty { accountName }.ifEmpty { "司机" }, url = Prefs.avatar.ifEmpty { null })
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        rosterName.ifEmpty { accountName }.ifEmpty { "司机" },
                        color = t.ink,
                        fontSize = (17 * scale).sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        listOfNotNull(
                            accountName.takeIf { it.isNotEmpty() && rosterName.isNotEmpty() && it != rosterName }
                                ?.let { "账号 $it" },
                            Prefs.mobile.ifEmpty { "未绑定手机号" },
                        ).joinToString(" · "),
                        color = t.textSecondary,
                        fontSize = (12.5 * scale).sp,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                BusTag("司机", tone = BusTone.CLAY)
            }
        }

        // 工作服务：全部是司机域自带路由，不存在跨域
        SectionTitle("工作服务")
        BusCard(modifier = Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Column {
                MineRow(BusIcons.bell, "调度消息", "调度与到车通知", {
                    nav.navigate(Routes.DRIVER_MESSAGES)
                }, badgeCount = unread)
                BusDivider()
                MineRow(BusIcons.warning, "异常上报", "晚点 · 绕行 · 货损", {
                    nav.navigate(Routes.DRIVER_EXCEPTION)
                })
            }
        }

        // 通用：资料与设置是两端共用的工具页，非用户业务页
        SectionTitle("通用")
        BusCard(modifier = Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Column {
                MineRow(BusIcons.person, "个人资料", "昵称与手机号", {
                    nav.navigate(Routes.PROFILE)
                })
                BusDivider()
                MineRow(BusIcons.settings, "设置", "主题 / 适老 / 提醒", {
                    nav.navigate(Routes.SETTINGS)
                })
                BusDivider()
                // 驾驶模式（字号 ×1.2 / 大按钮 / 夜间）此前只有 `DrivingModeRow` 这一个实现、
                // 而它零调用点 —— 开关做完了却没有一格能按（缺陷 #62）。挂在"通用"这一格最靠上：
                // 司机在开车前调它，与"个人资料/设置"同一个场景动线。
                DrivingModeRow(Modifier.padding(top = 10.dp))
            }
        }

        // 切换为用户版：原底部导航「用户版」入口的落点（见类注释的探针契约）
        SectionTitle("切换视图")
        BusCard(modifier = Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Column {
                MineRow(BusIcons.home, "切换为用户版", "去买票 · 寄货 · 查快递", {
                    AppState.setActiveDomain(AppDomain.USER)
                    nav.navigate(Routes.USER_SHELL) { popUpTo(Routes.DRIVER_SHELL) { inclusive = true } }
                })
            }
        }

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
                BusDialogAction(
                    "退出",
                    onClick = {
                        confirmLogout = false
                        scope.launch { Repo.logout() }
                        AppState.kickToLogin()
                    }
                )
            },
            dismissButton = { BusDialogAction("再想想", onClick = { confirmLogout = false }) },
        )
    }
}

/** 菜单行：图标托底 + 标题副标题 + 右箭头 —— 与用户端 `MineScreen.MineRow` 同一视觉语言（private 各持一份，避免跨包依赖）。 */
@Composable
private fun MineRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    danger: Boolean = false,
    badgeCount: Int = 0,
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
        if (badgeCount > 0) {
            androidx.compose.foundation.layout.Box(
                Modifier
                    .padding(end = 6.dp)
                    .clip(RoundedCornerShape(BusRadius.pill))
                    .background(t.danger)
                    .padding(horizontal = 7.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (badgeCount > 99) "99+" else "$badgeCount",
                    color = t.onPrimary,
                    fontSize = (11 * scale).sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        androidx.compose.material3.Icon(BusIcons.next, null, tint = t.textTertiary, modifier = Modifier.size(17.dp))
    }
}
