package com.shanxiang.buspost.notify

import com.shanxiang.buspost.core.net.ApiException
import com.shanxiang.buspost.core.net.NetworkException
import com.shanxiang.buspost.core.net.UnauthorizedException
import com.shanxiang.buspost.data.repo.DriverUnboundException
import java.io.IOException
import kotlinx.coroutines.CancellationException

/**
 * 后台轮询一轮的**结局**（指令 v2.1 第 ⑩ 步要的映射表就是这几个值），以及它该变成 WorkManager 的哪个动作。
 *
 * 为什么要单独一层：`NotifyWorker.doWork()` 旧写法是
 * ```
 * attempt { AlertEngine.check(applicationContext) }   // 结果丢掉
 * return Result.success()                            // 无论刚才发生了什么
 * ```
 * `check()` 里网络失败、401、后端 429、本地前置闸门全被 `Feed.error` 洗成一句文案，Worker 拿不到"是哪一类"，
 * 于是只能无条件报 success —— WorkManager 收到的信号是"这轮跑好了，按 15 分钟排下一轮"，
 * 而真相是"这轮什么都没问到"。用户侧的表现就是：弱网/服务过载之后，提醒整整迟了一个周期，
 * 且日志与状态板上都写着成功（§三十三 说的"失败语义不准确"就在这一行）。
 *
 * ⚠ 这一层**不含** `CancellationException`：取消在 [com.shanxiang.buspost.core.net.attempt] 里就已经穿出
 * `doWork`，永远不会被读成一个结局（F-1/⑨ 那条红线）。如果哪天这里出现 `CANCELLED` 这种值，
 * 说明有人开始在 Worker 里收取消，那是要拆掉的，不是要加测试覆盖的。
 */
enum class NotifyOutcome {
    /** 问到新消息并且已经推出去了 */
    NEW_MESSAGES,

    /** 问过了，后端说没有新的（或本轮没有满足推送条件的）——与下面两个"根本没问"分开写 */
    NO_NEW_MESSAGE,

    /** 未登录：本轮不该发任何请求。**不是**"没有新消息" */
    NOT_LOGGED_IN,

    /** 用户把通知开关关了：同上，是"没问"，不是"没有" */
    NOTIFICATION_DISABLED,

    /** 传输层失败 / 服务过载 / 5xx / 429：稍后重试有意义 */
    TRANSIENT_FAILURE,

    /** 401 / 会话失效：走现有认证出口清会话，重试只会每分钟再撞一次 */
    SESSION_INVALID,

    /** 重试不会变好的本地错误：司机档案未绑定（请求根本不该上网络）、程序错误（NPE/ISE/解析契约） */
    UNRECOVERABLE_LOCAL,
}

/** WorkManager 侧的三个动作。`doWork()` 只负责把 [NotifyOutcome] 翻成它，判定不写在里面。 */
enum class NotifyDirective { SUCCEED, RETRY, FAIL }

/**
 * 分类只看**异常类型**，不看文案：`Repo.errorTextOf` 那套文案是给页面念的，
 * 拿它当判据等于把"哪一类失败"重新绑回一句中文字符串（本项目在 ETA/状态映射上踩过两次的口径）。
 *
 * 顺序即优先级：取消先原样抛出（不参与分类）；`UnauthorizedException` 是 `ApiException(401)` 的子类，必须排前面；
 * `DriverUnboundException` 不是 `ApiException`，但要在"其余异常"之前拦住。
 */
fun classifyNotifyFailure(e: Throwable): NotifyOutcome = when {
    // 结构护栏（⑨ 那条红线）：这一层本该永远收不到取消——`doWork` 用的是 attempt。
    // 但"收不到"是上游给的运气，不是这里的保证；把它写成 throw 之后，谁把 attempt 换回 runCatching，
    // 崩在这里而不是安静地把"调用方已取消"发成一个 failure()。
    e is CancellationException -> throw e
    e is UnauthorizedException -> NotifyOutcome.SESSION_INVALID
    // 后端 `CommonResult.code` 与 HTTP 状态码共用 ApiException.code（Api.kt:340 / :348-353）：
    // 5xx 已经被 businessOrTransport 归成 NetworkException，所以这里能见到的 ApiException 是业务码与 4xx。
    // 429 = 过载，退避重试是它本来的语义；其余业务码（含 900 这类"这单不能这么操作"）重试不会变好。
    e is ApiException && e.code == HTTP_TOO_MANY_REQUESTS -> NotifyOutcome.TRANSIENT_FAILURE
    e is NetworkException -> NotifyOutcome.TRANSIENT_FAILURE
    e is IOException -> NotifyOutcome.TRANSIENT_FAILURE
    e is DriverUnboundException -> NotifyOutcome.UNRECOVERABLE_LOCAL
    else -> NotifyOutcome.UNRECOVERABLE_LOCAL
}

/**
 * 结局 → 动作。指令给的映射表在这里是**一张表**而不是散在 `doWork` 里的 if：
 * 正常完成（含"没有新消息"与两种"根本没问"）→ success；临时失败 → retry；
 * 会话失效与不可恢复的本地错误 → failure。
 *
 * ⚠ "没登录/关了通知"给 success 不是把它们当成功，是给 WorkManager 一个**不需要重跑**的信号：
 * 这两种状态下重跑一次的结果一样。页面侧的文案由 `AlertScan.outcome` 自己说，不经过这张表。
 */
fun notifyDirective(outcome: NotifyOutcome): NotifyDirective = when (outcome) {
    NotifyOutcome.NEW_MESSAGES,
    NotifyOutcome.NO_NEW_MESSAGE,
    NotifyOutcome.NOT_LOGGED_IN,
    NotifyOutcome.NOTIFICATION_DISABLED,
    -> NotifyDirective.SUCCEED

    NotifyOutcome.TRANSIENT_FAILURE -> NotifyDirective.RETRY

    NotifyOutcome.SESSION_INVALID,
    NotifyOutcome.UNRECOVERABLE_LOCAL,
    -> NotifyDirective.FAIL
}

private const val HTTP_TOO_MANY_REQUESTS = 429

/**
 * 一轮扫描的结果：结局 + 推数。
 * `announced` 是**交给 `Notifier.push` 的条数**，不等于"用户看到了"——`Notifier.kt:86-91` 对 33+ 未授予
 * `POST_NOTIFICATIONS` 的 `SecurityException` 是静默跳过，这里不重复判一次系统权限。
 * 它只为"NEW_MESSAGES 这件事确实发生过"服务（用例断言它 >0），不参与动作判定。
 */
data class AlertScan(val outcome: NotifyOutcome, val announced: Int)
