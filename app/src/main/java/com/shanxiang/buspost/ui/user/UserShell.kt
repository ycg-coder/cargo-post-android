package com.shanxiang.buspost.ui.user

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shanxiang.buspost.core.AppState
import com.shanxiang.buspost.ui.common.BusScaffold
import com.shanxiang.buspost.ui.common.BusTabBar
import com.shanxiang.buspost.ui.common.ComingSoon
import com.shanxiang.buspost.ui.common.TabItem
import com.shanxiang.buspost.core.ui.BusIcons

/** 村民端四 tab，与小程序自定义 tabBar 一一对应（那边的第三个 text 就是「快递」），但「快递」里多了到车提醒入口 */
@Composable
fun UserShell() {
    var selected by rememberSaveable { mutableStateOf(0) }
    val unread by AppState.unread.collectAsStateWithLifecycle()
    val items = listOf(
        TabItem("首页", BusIcons.home),
        TabItem("商城", BusIcons.cart),
        TabItem("快递", BusIcons.parcel, badge = unread),
        TabItem("我的", BusIcons.person),
    )
    BusScaffold(bottomBar = { BusTabBar(items, selected) { selected = it } }) { modifier ->
        Column(modifier.fillMaxSize()) {
            when (selected) {
                0 -> HomeScreen(onSelectTab = { selected = it })
                1 -> GoodsScreen()
                2 -> ParcelScreen()
                else -> MineScreen()
            }
        }
    }
    // 系统返回在非首页 tab 上是"回首页"，不是退出 App（Material 底部导航约定；设备走查实测三支都直接退到桌面）
    androidx.activity.compose.BackHandler(enabled = selected != 0) { selected = 0 }
}
