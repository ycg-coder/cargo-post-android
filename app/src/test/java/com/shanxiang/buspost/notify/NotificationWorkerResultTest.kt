package com.shanxiang.buspost.notify

import com.shanxiang.buspost.ServerTest
import com.shanxiang.buspost.TestHost
import com.shanxiang.buspost.core.net.Api
import com.shanxiang.buspost.core.net.ApiException
import com.shanxiang.buspost.core.net.NetTestOverride
import com.shanxiang.buspost.core.net.NetworkException
import com.shanxiang.buspost.core.net.UnauthorizedException
import com.shanxiang.buspost.data.model.NotifyMsg
import com.shanxiang.buspost.data.repo.DriverUnboundException
import com.shanxiang.buspost.data.repo.Feed
import com.shanxiang.buspost.data.repo.Repo
import com.shanxiang.buspost.data.store.Prefs
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 指令 v2.1 第 ⑩ 步点名的那本：后台一轮的**结局 → WorkManager 动作**。
 *
 * 修掉的假话（`NotifyWorker.doWork` 旧形）：
 * ```
 * attempt { AlertEngine.check(applicationContext) }   // 结局被丢掉
 * return Result.success()                            // 无论刚才发生了什么
 * ```
 * 后端 503 / 429 / 401 与"这轮确实没有新东西"在旧写法里是同一个 `success()`，
 * WorkManager 于是按 15 分钟排下一轮，而真相是"这轮什么都没问到"。
 *
 * 测缝说明（不是"覆盖到了"的意思）：
 * `doWork()` 本体要 `Context` + `WorkerParameters` 才能跑，工程**无 Robolectric**，
 * 所以这里驱动的是它调用的三段生产代码本身——`Repo` 的真实 HTTP 分类、`AlertEngine.outcomeOf` 的判据、
 * `notifyDirective` 的映射表；剩下那三行 `when -> Result` 由 `tools/check_notify_result_probes.sh` 静态钉。
 * ⚠ 三行胶水之外的东西都在这里测了，三行胶水的**运行时行为**（`Result.retry()` 真的重跑、
 * 401 后周期任务真的继续）不在这里，属 `NOT VERIFIED ON DEVICE`。
 */
class NotificationWorkerResultTest : ServerTest() {

    /** 一轮 = 一次消息列表读；把 Feed 的失败原因喂进 Worker 用的那条判据 */
    private fun outcomeOf(feed: Feed<NotifyMsg>, announced: Int = 0): NotifyOutcome =
        AlertEngine.outcomeOf(feed.cause?.let { listOf(it) } ?: emptyList(), announced)

    // ---------- 一、指令点名的五类，每类都走真实后端响应 ----------

    /** 网络临时错误 → retry。5xx 在 `Api.businessOrTransport`（Api.kt:348-355）就归了传输层，不是业务码 */
    @Test
    fun `后端 503 是临时失败且动作是 retry`() = runBlocking {
        TestHost.strictBackend()
        respond("service overloaded", 503)
        val feed = Repo.notifications(pageNo = 1, pageSize = 20)
        val cause = feed.cause
        assertNotNull("503 之后 Feed.cause 没带出异常，Worker 就无从分类：$feed", cause)
        assertTrue("5xx 必须是 NetworkException 而不是 ApiException：${cause?.javaClass?.name}", cause is NetworkException)
        assertEquals(1, server.requestCount)
        assertEquals(NotifyOutcome.TRANSIENT_FAILURE, outcomeOf(feed))
        assertEquals(NotifyDirective.RETRY, notifyDirective(outcomeOf(feed)))
    }

    /** 429 → retry。它到 Worker 这一层是 `ApiException(429)`（4xx 不进 5xx 分支），别当成"业务失败不用管" */
    @Test
    fun `后端 429 是临时失败且动作是 retry`() = runBlocking {
        TestHost.strictBackend()
        respond("too many requests", 429)
        val feed = Repo.notifications(pageNo = 1, pageSize = 20)
        val cause = feed.cause
        assertTrue("429 被分到了别处：${cause?.javaClass?.name}", cause is ApiException)
        assertEquals("HTTP 429 就是 ApiException.code=429，映射表按这个数排", 429, (cause as ApiException).code)
        assertEquals(NotifyOutcome.TRANSIENT_FAILURE, outcomeOf(feed))
        assertEquals(NotifyDirective.RETRY, notifyDirective(outcomeOf(feed)))
    }

    /** 正常完成（有未读、本轮推了 2 条）→ success */
    @Test
    fun `正常回包并推出消息时动作是 success`() = runBlocking {
        TestHost.strictBackend()
        respondData("""{"list":[{"id":9,"readStatus":0,"content":"车已装车","eventType":"LOAD"}],"total":1}""")
        val feed = Repo.notifications(pageNo = 1, pageSize = 20)
        assertNull("成功回包不该带失败原因：${feed.cause}", feed.cause)
        assertEquals(1, feed.items.size)
        val outcome = outcomeOf(feed, announced = 2)
        assertEquals(NotifyOutcome.NEW_MESSAGES, outcome)
        assertEquals(NotifyDirective.SUCCEED, notifyDirective(outcome))
    }

    /** 没有新消息 → success。**它与"根本没问"是两个值**，混写就回到旧的那个假话 */
    @Test
    fun `后端回空列表是没有新消息而不是失败`() = runBlocking {
        TestHost.strictBackend()
        respondData("""{"list":[],"total":0}""")
        val feed = Repo.notifications(pageNo = 1, pageSize = 20)
        assertNull(feed.cause)
        assertTrue(feed.items.isEmpty())
        val outcome = outcomeOf(feed)
        assertEquals(NotifyOutcome.NO_NEW_MESSAGE, outcome)
        assertEquals(NotifyDirective.SUCCEED, notifyDirective(outcome))
        assertEquals(1, server.requestCount)
    }

    /** 会话失效 → failure + 走真实的清会话出口（不打后端：后端已经认这个会话无效了） */
    @Test
    fun `401 归会话失效且动作是 failure`() = runBlocking {
        TestHost.strictBackend()
        Api.token = "server-token-7"
        respond("""{"code":401,"msg":"登录已失效","data":null}""", 401)
        val feed = Repo.notifications(pageNo = 1, pageSize = 20)
        assertTrue("401 必须是 UnauthorizedException：${feed.cause?.javaClass?.name}", feed.cause is UnauthorizedException)
        val outcome = outcomeOf(feed)
        assertEquals(NotifyOutcome.SESSION_INVALID, outcome)
        assertEquals(NotifyDirective.FAIL, notifyDirective(outcome))
    }

    /**
     * 指令给的另一种可接受写法（"invalid auth→failure **或真实 logout path**"）这里一起钉：
     * `clearSessionLocal()` 是 Worker 在 SESSION_INVALID 分支实际调的那个出口，
     * 它必须清干净本机且不新增请求——否则下一轮拿死 token 再撞一次，15 分钟一次地撞。
     */
    @Test
    fun `会话失效的清理出口只清本机且不打后端`() = runBlocking {
        TestHost.strictBackend()
        // loggedIn 是派生的（`Prefs.loggedIn = token.isNotEmpty()`，Prefs.kt:143），写 token 就够
        Prefs.token = "server-token-7"
        Api.token = "server-token-7"
        val before = server.requestCount
        Repo.clearSessionLocal()
        assertEquals("清会话多打了一次后端接口", before, server.requestCount)
        assertNull(Api.token)
        assertEquals("", Prefs.token)
        // 清完之后同一轮判据的下一次：未登录是"根本没问"，动作是 success（不需要重跑），不是"没有新消息"
        assertEquals(NotifyDirective.SUCCEED, notifyDirective(NotifyOutcome.NOT_LOGGED_IN))
    }

    // ---------- 二、retry 与 failure 的边界：谁重试都不会变好 ----------

    /** 真连不上（端口没人听）：IOException → NetworkException，按临时失败重试 */
    @Test
    fun `连不上后端时按临时失败重试`() = runBlocking {
        TestHost.strictBackend()
        NetTestOverride.baseUrl = "http://127.0.0.1:9"
        val feed = Repo.notifications(pageNo = 1, pageSize = 20)
        assertTrue("连不上该是传输失败：${feed.cause?.javaClass?.name}", feed.cause is NetworkException)
        assertEquals(NotifyDirective.RETRY, notifyDirective(outcomeOf(feed)))
    }

    /** 超时同一条：`SocketTimeoutException` 是 IOException 的子类，不靠文案认 */
    @Test
    fun `读取超时按类型归临时失败`() {
        val outcome = classifyNotifyFailure(SocketTimeoutException("read timed out"))
        assertEquals(NotifyOutcome.TRANSIENT_FAILURE, outcome)
        assertEquals(NotifyDirective.RETRY, notifyDirective(outcome))
    }

    /** 业务码（HTTP 200 + code!=0）不是过载信号：重试不会变好，不该伪装成可重试 */
    @Test
    fun `业务失败不被洗成可重试`() = runBlocking {
        TestHost.strictBackend()
        respondBizError(900, "该订单当前状态不允许此操作")
        val feed = Repo.notifications(pageNo = 1, pageSize = 20)
        assertEquals(900, (feed.cause as ApiException).code)
        val outcome = outcomeOf(feed)
        assertEquals(NotifyOutcome.UNRECOVERABLE_LOCAL, outcome)
        assertEquals(NotifyDirective.FAIL, notifyDirective(outcome))
    }

    /** 程序错误（NPE/ISE）不领"重试的通行证"——这条是 F-1 §六 的口径在 Worker 侧的续 */
    @Test
    fun `客户端缺陷归不可恢复且不是 retry`() {
        for (fault in listOf(NullPointerException("探针"), IllegalStateException("探针"))) {
            val outcome = classifyNotifyFailure(fault)
            assertEquals("缺陷 ${fault.javaClass.simpleName} 被分进了可重试：$outcome", NotifyOutcome.UNRECOVERABLE_LOCAL, outcome)
            assertEquals(NotifyDirective.FAIL, notifyDirective(outcome))
        }
    }

    /**
     * 司机域"未绑定档案"：结局是"重试也不会变好"，且**请求根本没上网络**（`Repo.driverIdParam` 在拼参数时就短路）。
     * 这一条同时钉住"绑定状态不是网络问题"——把它归成 retry 会让 WorkManager 每 15 分钟撞一次没有接口的墙。
     */
    @Test
    fun `未绑定司机档案不出网且归不可恢复`() = runBlocking {
        TestHost.strictBackend()
        Prefs.driverId = 0L
        val feed = Repo.driverMessages(pageNo = 1, pageSize = 20)
        assertTrue("未绑定该是 DriverUnboundException：${feed.cause?.javaClass?.name}", feed.cause is DriverUnboundException)
        assertEquals("未绑定却把请求发出去了", 0, server.requestCount)
        val outcome = outcomeOf(feed)
        assertEquals(NotifyOutcome.UNRECOVERABLE_LOCAL, outcome)
        assertEquals(NotifyDirective.FAIL, notifyDirective(outcome))
    }

    // ---------- 三、映射表本身逐行钉住 + ⑨ 那条红线 ----------

    /**
     * 七个结局各自的动作。`notifyDirective` 是穷尽 `when`，新增结局不写分支就编译不过；
     * 这条用例钉的是**已有的七个不许漂**（尤其"根本没问"的两种必须走 SUCCEED 而不是 RETRY）。
     */
    @Test
    fun `映射表逐行为七个结局给定动作`() {
        assertEquals(7, NotifyOutcome.entries.size)
        mapOf(
            NotifyOutcome.NEW_MESSAGES to NotifyDirective.SUCCEED,
            NotifyOutcome.NO_NEW_MESSAGE to NotifyDirective.SUCCEED,
            NotifyOutcome.NOT_LOGGED_IN to NotifyDirective.SUCCEED,
            NotifyOutcome.NOTIFICATION_DISABLED to NotifyDirective.SUCCEED,
            NotifyOutcome.TRANSIENT_FAILURE to NotifyDirective.RETRY,
            NotifyOutcome.SESSION_INVALID to NotifyDirective.FAIL,
            NotifyOutcome.UNRECOVERABLE_LOCAL to NotifyDirective.FAIL,
        ).forEach { (outcome, expected) ->
            assertEquals("$outcome 的动作漂了", expected, notifyDirective(outcome))
        }
    }

    /** 两种"根本没问"与"问了说没有"必须是三个不同的值，否则 Worker 又把它们说成同一件事 */
    @Test
    fun `没登录与关了通知不混成没有新消息`() {
        assertTrue(
            "三个值必须互不相等：${NotifyOutcome.NO_NEW_MESSAGE}/${NotifyOutcome.NOT_LOGGED_IN}/${NotifyOutcome.NOTIFICATION_DISABLED}",
            setOf(NotifyOutcome.NO_NEW_MESSAGE, NotifyOutcome.NOT_LOGGED_IN, NotifyOutcome.NOTIFICATION_DISABLED).size == 3,
        )
        // 判据本体：没有失败原因、也没推东西 → NO_NEW_MESSAGE；推了才是 NEW_MESSAGES
        assertEquals(NotifyOutcome.NO_NEW_MESSAGE, AlertEngine.outcomeOf(emptyList(), 0))
        assertEquals(NotifyOutcome.NEW_MESSAGES, AlertEngine.outcomeOf(emptyList(), 1))
        // "没登录/通知关了"不经过这条判据——它们在 `check()` 的最前面各占一个 return，
        // 这里只钉它们**不等于** NO_NEW_MESSAGE，否则"根本没问"就被说成了"后端说没有"
        assertTrue(
            NotifyOutcome.NOT_LOGGED_IN != NotifyOutcome.NO_NEW_MESSAGE &&
                NotifyOutcome.NOTIFICATION_DISABLED != NotifyOutcome.NO_NEW_MESSAGE,
        )
    }

    /**
     * ⑨ 那条红线的续：取消**永远不该被读成一个结局**。
     * `doWork` 用 attempt，取消在它里面就穿出去了；这里再给分类器本身加一道——
     * 谁哪天把 attempt 换回 runCatching，取消会崩在这里，而不是安静地变成一个 failure()。
     */
    @Test
    fun `取消进不了这一层，进来了也原样抛出`() {
        val outcome = runCatching { classifyNotifyFailure(CancellationException("探针：调用方已取消")) }
        val e = outcome.exceptionOrNull()
        assertTrue("取消被收成了结局 ${outcome.getOrNull()}：${e?.javaClass?.name}", e is CancellationException)
        assertNull(outcome.getOrNull())
        // 结局枚举里没有、也不该有 CANCELLED：有它等于开始在 Worker 里收取消
        assertTrue(
            "NotifyOutcome 里出现了 CANCELLED 这类值，说明有人在收取消：${NotifyOutcome.entries}",
            NotifyOutcome.entries.none { it.name.contains("CANCEL") },
        )
    }
}
