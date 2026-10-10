package com.shanxiang.buspost.core.net

import kotlinx.coroutines.CancellationException

/**
 * 指令 §七 要求的"统一 helper"落点。命名用 `attempt` 而不是 `safeApiCall`：
 * 它的适用面比 API 调用更宽（`AlertEngine` 轮询、照片读取、Worker 的 doWork 都在同一形状里），
 * 叫 `safeApiCall` 会让后面的人以为只有网络层该用它，于是漏掉真正会吞取消的那几处。
 *
 * 与 `runCatching` 的唯一区别：**`CancellationException` 一律穿透，不收进 [Result]**。
 * `runCatching` 捕获 `Throwable`，把"页面已经离开 / Worker 已被停止 / 外层超时"读成一次普通失败，
 * 于是代码继续往下走，把 null、把 `Result.success()`、把 UI 状态写回一个已经不该再被写的地方。
 * 在协程里这是错的：取消不是失败，是"这次不该再有结果"。
 *
 * ⚠ 它**不是**"把异常都收进 Result"的许可：业务/传输失败照旧进 [Result.failure]，
 * 由调用方决定怎么如实表达（见 `Api.execute` 的演示回落白名单，以及 `DemoExceptionIsolationTest`）。
 */
suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}
