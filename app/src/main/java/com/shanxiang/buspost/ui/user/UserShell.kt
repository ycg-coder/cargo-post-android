package com.shanxiang.buspost.ui.user

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shanxiang.buspost.core.AppState
import com.shanxiang.buspost.data.store.Prefs
import com.shanxiang.buspost.ui.common.BusScaffold
import com.shanxiang.buspost.ui.common.CapsuleAction
import com.shanxiang.buspost.ui.common.CapsuleActions
import com.shanxiang.buspost.ui.common.LoginGateDialog
import com.shanxiang.buspost.core.ui.BusIcons
import com.shanxiang.buspost.nav.Routes
import com.shanxiang.buspost.ui.common.LocalNavController

/** 村民端四 tab，与小程序自定义 tabBar 一一对应（那边的第三个 text 就是「快递」），但「快递」里多了到车提醒入口 */
@Composable
fun UserShell() {
    var selected by rememberSaveable { mutableStateOf(0) }
    val unread by AppState.unread.collectAsStateWithLifecycle()
    val guest = !Prefs.loggedIn
    var showLoginGate by remember { mutableStateOf(false) }
    val nav = LocalNavController.current

    /**
     * 游客点商城/快递/我的 → 弹登录窗而不是切 tab（对标主流 App 的"先体验再注册"）。
     * 首页（index=0）完整开放：实时公交、附近公交、天气、公告都不需要登录。
     */
    fun onTabSelect(index: Int) {
        if (guest && index != 0) {
            showLoginGate = true
        } else {
            selected = index
        }
    }

    // 主导航四格交给全局悬浮胶囊（原来是底部那条 tab 栏）。上面那条游客规矩收在 onTabSelect 一处，
    // 胶囊只是它的另一个入口，不是第二套判断。
    val items = listOf(
        CapsuleAction("home", "首页", BusIcons.home, onClick = { onTabSelect(0) }, selected = selected == 0),
        CapsuleAction("shop", "商城", BusIcons.cart, onClick = { onTabSelect(1) }, selected = selected == 1),
        CapsuleAction("parcel", "快递", BusIcons.parcel, onClick = { onTabSelect(2) }, selected = selected == 2, badge = unread),
        CapsuleAction("mine", "我的", BusIcons.person, onClick = { onTabSelect(3) }, selected = selected == 3),
    )
    CapsuleActions(items)

    BusScaffold { modifier ->
        Column(modifier.fillMaxSize()) {
            when (selected) {
                0 -> HomeScreen(
                    onSelectTab = { onTabSelect(it) },
                    onLoginClick = { showLoginGate = true },
                    guest = guest,
                )
                1 -> GoodsScreen()
                2 -> ParcelScreen()
                else -> MineScreen()
            }
        }
    }

    if (showLoginGate) {
        LoginGateDialog(
            onDismiss = { showLoginGate = false },
            onLogin = {
                showLoginGate = false
                nav.navigate(Routes.LOGIN)
            },
        )
    }

    // 系统返回在非首页 tab 上是"回首页"，不是退出 App（Material 底部导航约定；设备走查实测三支都直接退到桌面）
    androidx.activity.compose.BackHandler(enabled = selected != 0) { selected = 0 }
}
