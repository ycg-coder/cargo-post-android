package com.shanxiang.buspost.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.shanxiang.buspost.core.net.attempt
import com.shanxiang.buspost.data.repo.Repo
import java.util.concurrent.TimeUnit

/**
 * 后台消息轮询：WorkManager 周期任务（系统最小 15 分钟），App 在前台时另有 20 秒的即时轮询。
 * 只有演示构建（`BuildConfig.DEMO_ALLOWED`）里 Repo 才会回落演示数据；正式包失败就是失败。
 *
 * F-2 ⑩：`doWork` 的返回值现在**跟着这一轮的真实结局走**（映射表在 [notifyDirective]，不在这里）。
 * 旧写法是 `attempt { check() }` 之后无条件 `return Result.success()`——网络挂了、401、后端 429
 * 全被 `Feed.error` 洗成文案，WorkManager 收到的信号是"这轮跑好了，15 分钟后再来"，
 * 而真相是"这轮什么都没问到"，弱网之后提醒整整迟一个周期且日志写着成功。
 *
 * `Result.retry()` 不是立刻重跑的循环：本工程没有 `setBackoffCriteria`，退避由 WorkManager 决定，
 * 实测常量（work-runtime **2.9.1**，`javap -constants` 直读依赖）`DEFAULT_BACKOFF_DELAY_MILLIS = 30_000`、
 * `MIN_BACKOFF_MILLIS = 10_000`。默认策略是否为指数、以及 `failure()` 会不会终止后续周期，
 * **未在本机验证**（无设备、无 Robolectric），登记在 DECISION_LOG D-044。
 */
class NotifyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // attempt 而非 runCatching：被系统取消时要把 CancellationException 穿出 doWork，
        // 吞掉它再 return success()，等于向 WorkManager 谎报"这一轮已经跑过"。
        // 能走到 getOrElse 的只剩真失败（取消已经在 attempt 里穿透），所以这里按异常类型定结局是安全的。
        val scan = attempt { AlertEngine.check(applicationContext) }
            .getOrElse { AlertScan(classifyNotifyFailure(it), announced = 0) }

        // 会话失效走既有认证出口：不打后端（后端已经说这个 token 无效了），只清本机会话。
        // Api 的 401 广播有 2 秒节流（Api.kt:363-367），后台这一路没有页面会再触发它，所以这里补一次。
        // 清完下一轮就是 NOT_LOGGED_IN → success：不会每分钟拿死 token 再撞一次。
        if (scan.outcome == NotifyOutcome.SESSION_INVALID) Repo.clearSessionLocal()

        return when (notifyDirective(scan.outcome)) {
            NotifyDirective.SUCCEED -> Result.success()
            NotifyDirective.RETRY -> Result.retry()
            NotifyDirective.FAIL -> Result.failure()
        }
    }

    companion object {
        private const val NAME = "buspost_notify"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<NotifyWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueue(
                androidx.work.OneTimeWorkRequestBuilder<NotifyWorker>().build()
            )
        }
    }
}
