package com.shanxiang.buspost.ui.driver

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shanxiang.buspost.core.AppState
import com.shanxiang.buspost.core.ui.BusIcons
import com.shanxiang.buspost.ui.common.BusScaffold
import com.shanxiang.buspost.ui.common.BusTabBar
import com.shanxiang.buspost.ui.common.TabItem

/**
 * 司机端四 tab（2026-10-10 调整）：工作台 · 我的路线 · 我的收益 · 我的。
 *
 * 原 第 4 格「用户版」是整壳跳转出口，放底部导航有两个问题：
 * · 驾驶/装车场景单手持机，误触即整壳跳走，回去还要再进一次司机端；
 * · 与"避免干扰驾驶操作"的布局原则冲突。
 * 出口改为收进「我的」页的菜单行（`DriverMineScreen`），切换语义不变：
 * 仍只写 `AppState.setActiveDomain(AppDomain.USER)` 切视图域，账号角色由后端档案决定
 * （F-3 ③ 纪律 / 审计 §29；探针 SR-f2 / RD4 / RR-e2 已随本改动同步换文件与锚点）。
 */
@Composable
fun DriverShell() {
    var selected by rememberSaveable { mutableStateOf(0) }
    val unread by AppState.driverUnread.collectAsStateWithLifecycle()
    val items = listOf(
        TabItem("工作台", BusIcons.flag, badge = unread),
        TabItem("我的路线", BusIcons.route),
        TabItem("我的收益", BusIcons.money),
        TabItem("我的", BusIcons.person),
    )
    BusScaffold(bottomBar = {
        BusTabBar(items, selected) { selected = it }
    }) { modifier ->
        Column(modifier.fillMaxSize()) {
            when (selected) {
                0 -> WorkbenchScreen()
                1 -> RoutesScreen()
                2 -> EarningsScreen()
                else -> DriverMineScreen()
            }
        }
    }
    // 系统返回在非工作台 tab 上是"回工作台"，不是退出 App；与 UserShell 同形。
    androidx.activity.compose.BackHandler(enabled = selected != 0) { selected = 0 }
}
