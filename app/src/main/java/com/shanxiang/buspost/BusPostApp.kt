package com.shanxiang.buspost

import android.app.Application
import com.shanxiang.buspost.core.AppState
import com.shanxiang.buspost.core.navigation.AmapCompliance
import com.shanxiang.buspost.core.net.Api
import com.shanxiang.buspost.core.net.attempt
import com.shanxiang.buspost.data.repo.Repo
import com.shanxiang.buspost.data.store.Prefs
import com.shanxiang.buspost.notify.AlertEngine
import com.shanxiang.buspost.notify.Channels
import com.shanxiang.buspost.notify.NotifyWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BusPostApp : Application() {

    /**
     * 启动路径分步计时，只进 logcat（`adb logcat -s BUS_BOOT`）不进任何界面。
     *
     * 为什么留着这几行（缺陷 #78 的第一格）：`onCreate` 里原本五件事全在主线程，
     * "打开慢半拍"没有分步读数就只能猜 —— 而猜错的方向是把 `Prefs.init` 挪后台，
     * 那会让紧接着的 `Repo.restoreSession()` 读到空会话，比慢更糟。
     * 每行成本 = 一次 `elapsedRealtime()`，没有文件与网络。
     */
    override fun onCreate() {
        val origin = android.os.SystemClock.elapsedRealtime()
        var markAt = origin
        fun bootMark(what: String) {
            val now = android.os.SystemClock.elapsedRealtime()
            android.util.Log.i("BUS_BOOT", "$what=${now - markAt}ms 累计=${now - origin}ms")
            markAt = now
        }

        super.onCreate()
        bootMark("super")
        // 高德要求：调用 SDK 任何接口前必须先设隐私合规，否则有崩溃风险（见 AmapCompliance 注释）。
        // 放在第一行 = 后面无论哪条路径先碰到 AMap，合规都已经就位。
        AmapCompliance.apply(this)
        bootMark("amapPrivacy")
        Prefs.init(this)
        bootMark("prefsInit")
        Channels.ensure(this)
        bootMark("channels")
        Api.initCache(this)
        bootMark("httpCache")
        Repo.restoreSession()
        bootMark("restoreSession")
        // 业务请求 401 时先试静默续期（后端 /member/auth/refresh-token 一直存在，此前 App 从不调用）
        Api.refresher = { Repo.refreshTokenOnce() }
        Api.onUnauthorized = {
            Prefs.clearSession()
            AppState.kickToLogin()
        }
        // 后台：WorkManager 15 分钟一轮 —— **不在主线程发起**。
        // `WorkManager.getInstance()` 首次调用才初始化整套（建库 + 恢复未完成任务），
        // 而它的结果没有任何需要在第一帧之前读到的东西。这一段跑在 launch 里，自己量自己，
        // 不碰上面那个 `markAt`（两个线程写同一个局部变量会把读数搅浑）。
        // 兜底：周期任务本来就是"错过就下一轮"，延后几百毫秒不会丢提醒；
        // 且这条 launch 在 `restoreSession()` 之后发起，首轮要用的会话已经恢复完。
        scope.launch {
            val t = android.os.SystemClock.elapsedRealtime()
            withContext(Dispatchers.IO) { NotifyWorker.schedule(this@BusPostApp) }
            android.util.Log.i(
                "BUS_BOOT",
                "notifyWorkerSchedule=${android.os.SystemClock.elapsedRealtime() - t}ms（IO 线程，不占主线程）",
            )
        }
        // 前台：30 秒一轮（原 20 秒，优化电池），"车快到了"这类分钟级提醒仍然及时
        scope.launch {
            while (true) {
                if (AppState.foreground.value && AppState.hasUserSession) {
                    attempt { AlertEngine.check(this@BusPostApp) }
                }
                delay(30_000L)
            }
        }
        bootMark("onCreateReturn")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
