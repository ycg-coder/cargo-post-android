package com.shanxiang.buspost

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shanxiang.buspost.core.AppState
import com.shanxiang.buspost.data.store.Prefs
import com.shanxiang.buspost.nav.Routes
import com.shanxiang.buspost.notify.NotifyWorker
import com.shanxiang.buspost.ui.BusPostRoot
import com.shanxiang.buspost.core.ui.BusAppTheme

class MainActivity : ComponentActivity() {

    private val askNotifications = registerForActivityResult(RequestPermission()) { granted ->
        if (granted) NotifyWorker.runNow(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-Edge（2026-10-09 现代化改造）：Android 15+ 强制、Android 16 真机默认全屏。
        // 内容延伸到状态栏/导航栏之下，**避让统一交给 BusScaffold 的 Scaffold insets**，
        // 页面不各自补 padding —— 那是两个真相源，总会有一个页面漏掉而在状态栏下露字。
        // 例外：车道级导航页（DriverGuideScreen）是全屏地图，自带 statusBarsPadding，不重复避让。
        enableEdgeToEdge()
        Prefs.init(applicationContext)
        handleIntent(intent)
        askNotificationOnce()
        setContent {
            val theme by AppState.theme.collectAsStateWithLifecycle()
            val elderly by AppState.elderly.collectAsStateWithLifecycle()
            val driving by AppState.driving.collectAsStateWithLifecycle()
            // 夜间暗色由 AppState 统一裁决（`dark || 驾驶模式夜间`），这里不自己拼 —— 拼两遍就是两个真相
            val nightUi by AppState.nightUi.collectAsStateWithLifecycle()
            BusAppTheme(theme = theme, dark = nightUi, elderly = elderly, driving = driving) {
                BusPostRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        AppState.foreground.value = true
    }

    override fun onStop() {
        AppState.foreground.value = false
        super.onStop()
    }

    /** 通知点击 = 深链：把 Intent 带来的路由串交给根导航消费（前缀口径与通知侧共用 [Routes.routeFromDeepLink]） */
    private fun handleIntent(intent: Intent?) {
        // 剥前缀与通知侧拼前缀共用 Routes 的同一份口径；不匹配就是 null，不当成"目标是空路由"
        Routes.routeFromDeepLink(intent?.dataString)?.let { AppState.pendingRoute.value = it }
    }

    private fun askNotificationOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || Prefs.notifPrompted) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        Prefs.notifPrompted = true
        if (!granted) runCatching { askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }
}
