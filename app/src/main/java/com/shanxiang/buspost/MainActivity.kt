package com.shanxiang.buspost

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shanxiang.buspost.core.AppState
import com.shanxiang.buspost.core.update.AppUpdater
import com.shanxiang.buspost.core.update.Release
import com.shanxiang.buspost.core.update.UpdateDialog
import com.shanxiang.buspost.core.update.UpdateOutcome
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

            // app 内自更新：冷启动查一次 GitHub Release，**只有真读到"有可用新版本"才弹窗**。
            // 查询失败 / 仓库还没发布过 都不弹任何东西 —— 但这里也不说"已是最新"：
            // 要说这句话得去「设置 → 关于 → 检查更新」，那一格才把原话摆出来（#48 的诚实分格）。
            // 2026-10-14 真机两条规矩：
            // ① 同一个 tag 只拦一次：用户点了"稍后再说"，下次冷启动不该再拿同一个版本堵门
            //    （此前每次冷启动都盖住首页，这才是"弹窗骚扰"的真身）；
            // ② 调试包也照常提示（用户当日点名"还是把自动更新开启"）：本地包 versionCode 恒为 1，
            //    跟任何正式版都比"旧"，所以它一定会提示 —— 装不上是签名的事，由弹窗里那句原话承担，
            //    不在这里替用户决定"你不需要知道有新版本"。
            var updateRelease by remember { mutableStateOf<Release?>(null) }
            LaunchedEffect(Unit) {
                val latest = (AppUpdater.check() as? UpdateOutcome.Available)?.release ?: return@LaunchedEffect
                if (latest.tagName != Prefs.updateSkipTag) updateRelease = latest
            }
            BusAppTheme(theme = theme, dark = nightUi, elderly = elderly, driving = driving) {
                BusPostRoot()
                // 弹窗必须画在 BusAppTheme **里面**。放在外面它读的是 compositionLocal 的默认值
                // （`busTokens(BusTheme.BLUE, false)` = 永远浅色、字号永远 1f）⇒ 夜间导航背后顶着一块刺眼的白卡，
                // 而「深色站牌」「字号放大」对它完全不生效。D-163 把六处字面量换成 t.* 在那时是空转的：
                // 它读到的 t 就是这份默认令牌（真机 v183 提示弹窗的截图是凭证）。
                updateRelease?.let { release ->
                    UpdateDialog(
                        release = release,
                        onDismiss = {
                            Prefs.updateSkipTag = release.tagName
                            updateRelease = null
                        },
                    )
                }
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
