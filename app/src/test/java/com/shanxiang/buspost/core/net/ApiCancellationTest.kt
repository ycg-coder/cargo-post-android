package com.shanxiang.buspost.core.net

import com.shanxiang.buspost.ServerTest
import com.shanxiang.buspost.TestHost
import com.shanxiang.buspost.data.model.Notice
import com.shanxiang.buspost.data.repo.Feed
import com.shanxiang.buspost.data.repo.Repo
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 指令 §七：`runCatching` 捕获的是 `Throwable`，于是"页面已经离开 / Worker 已被停止 / 外层超时到了"
 * 这三种取消都会被读成一次普通失败 —— 挂起来的代码接着往已经离场的画面上写"加载失败"。
 * [attempt] 是这一层的唯一出口：取消原样穿出，其余失败才进 [Result]。
 */
class ApiCancellationTest : ServerTest() {

    @Test
    fun `attempt 让超时取消原样送达 withTimeoutOrNull`() = runBlocking {
        var captured: Result<Long>? = null
        val outcome = withTimeoutOrNull(60) {
            captured = attempt { delay(5_000); 1L }
        }
        assertNull("外层超时到了，这里只能是 null", outcome)
        assertNull("attempt 没有返回值可赋：CancellationException 直接穿出去了", captured)
    }

    @Test
    fun `attempt 把真实失败收成 Result 而不是抛出`() = runBlocking {
        TestHost.strictBackend()
        respond("<html>Bad Gateway</html>", 500, "text/html")
        val r = attempt { Api.get("/app-api/transport/notice/list") }
        assertTrue(r.isFailure)
        assertSame("失败类型必须原样保留，不能退化成一个字符串", NetworkException::class.java,
            r.exceptionOrNull()!!.javaClass)
    }

    /**
     * 对照组：同一位置换成旧写法（`Locator` 修之前的形状）。
     * 这条用例的意义是**量出差异**：如果框架自己兜住了超时，那处的可观察差异就是 0，
     * 修它是为了形状；如果框架没兜住，那处就是"超时被吞成一个假值"的真 bug。两种结果都要留字面证据。
     */
    @Test
    fun `旧写法 runCatching 在超时点上交出的是什么`() = runBlocking {
        val outcome = withTimeoutOrNull(60) { runCatching { delay(5_000); 1L }.getOrElse { -1L } }
        assertNull("期望：框架在块返回时仍按超时结算（若这一条红了，说明 runCatching 把超时洗成了 -1 这个假值）", outcome)
    }

    @Test
    fun `Repo 读接口不把取消写成一次普通失败`() = runBlocking {
        TestHost.strictBackend()
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"code":0,"msg":"","data":{"list":[],"total":0}}""")
                .setBodyDelay(1_200, TimeUnit.MILLISECONDS),
        )
        var feed: Feed<Notice>? = null
        var thrown: Throwable? = null
        val job = launch {
            feed = try {
                Repo.notices()
            } catch (e: Throwable) {
                thrown = e
                null
            }
        }
        delay(150)
        job.cancelAndJoin()
        assertNull("取消不该产出一个 Feed（旧写法返回的是 error 非空的那句「加载失败」）", feed)
        assertTrue("该抛出的是取消本身，不是被 Repo 的 catch(Exception) 接住的普通失败：$thrown",
            thrown is CancellationException)
    }

    /** 对偶：真实失败照旧走 Repo 的 error 分支 —— 这一轮没有把"失败"改成"崩溃" */
    @Test
    fun `真实失败仍然回一个带 error 的 Feed`() = runBlocking {
        TestHost.strictBackend()
        respond("<html>x</html>", 500, "text/html")
        val f = Repo.notices()
        assertTrue(f.items.isEmpty())
        assertTrue("失败必须可见", f.error != null)
    }
}
