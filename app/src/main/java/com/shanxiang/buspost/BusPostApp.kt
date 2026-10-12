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

class BusPostApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // 高德要求：调用 SDK 任何接口前必须先设隐私合规，否则有崩溃风险（见 AmapCompliance 注释）。
        // 放在第一行 = 后面无论哪条路径先碰到 AMap，合规都已经就位。
        AmapCompliance.apply(this)
        Prefs.init(this)
        Channels.ensure(this)
        Repo.restoreSession()
        // 业务请求 401 时先试静默续期（后端 /member/auth/refresh-token 一直存在，此前 App 从不调用）
        Api.refresher = { Repo.refreshTokenOnce() }
        Api.onUnauthorized = {
            Prefs.clearSession()
            AppState.kickToLogin()
        }
        // 后台：WorkManager 15 分钟一轮
        NotifyWorker.schedule(this)
        // 前台：20 秒一轮，"车快到了"这类分钟级提醒只有这样才能落到实处
        scope.launch {
            while (true) {
                if (AppState.foreground.value && AppState.hasUserSession) {
                    // attempt 而非 runCatching：这个循环的 scope 被取消时，检查中途的 CancellationException
                    // 必须原样穿出（吞掉它=进程已经不在了还继续 round），其余失败与旧行为一样不阻塞下一轮
                    attempt { AlertEngine.check(this@BusPostApp) }
                }
                delay(20_000L)
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
