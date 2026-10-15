package com.shanxiang.buspost.core.net

import com.shanxiang.buspost.ServerTest
import com.shanxiang.buspost.TestHost
import com.shanxiang.buspost.data.repo.Repo
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 网络卫生契约（缺陷 #79 的第一、二、三格；第四格在 `SessionRefreshContractTest`）。
 *
 * 前两条是**真发请求**的：挂一个带磁盘缓存的 OkHttpClient 打 MockWebServer，用 `server.requestCount`
 * 判"第二次到底有没有去问后端"。只测 `cachePolicyFor` 那个纯函数的话，拦截器没挂上、`no-store` 拼错、
 * 头部被后面的拦截器覆盖 —— 全都照样绿，而那些才是这一格真正会坏的地方。
 * 最后一条把生产的 `Repo.clearSessionLocal()` → `Api.clearCache()` 接在同一个 Cache 实例上跑，
 * 钉的是"换下一个人时缓存里真没东西了"，而不是 grep 到那行写过一次。
 */
class NetHygieneContractTest : ServerTest() {

    private var cacheDir: File? = null
    private var client: OkHttpClient? = null

    private fun tempDir(): File =
        cacheDir?.let { return it }
            ?: File(System.getProperty("java.io.tmpdir"), "buspost-net-${System.nanoTime()}")
                .apply { mkdirs() }
                .also { cacheDir = it }

    /** 与生产同形：磁盘缓存 + 同一个网络拦截器（`Api.http` 里就是这么装的） */
    private fun cachedClient(cache: Cache = Cache(tempDir(), 512L * 1024L)): OkHttpClient =
        OkHttpClient.Builder()
            .cache(cache)
            .addNetworkInterceptor(CacheControlInterceptor())
            .build()
            .also { client = it }

    private fun body(path: String): String {
        val c = client ?: cachedClient()
        c.newCall(Request.Builder().url(server.url(path).toString()).get().build()).execute().use {
            return it.body?.string() ?: ""
        }
    }

    @After
    fun tearDownClient() {
        runCatching { client?.cache?.close() }
        runCatching { client?.dispatcher?.executorService?.shutdown() }
        runCatching { cacheDir?.deleteRecursively() }
        cacheDir = null
        client = null
    }

    @Test
    fun `白名单里的公告列表第二次命中磁盘缓存，后端只被问一次`() {
        cachedClient()
        respondData("""{"list":[{"id":1}],"total":1}""")
        respondData("""{"list":[{"id":2}],"total":1}""")   // 若真打了第二次，正文就会变成 id:2

        val first = body("/app-api/transport/notice/list")
        val second = body("/app-api/transport/notice/list")

        assertEquals("白名单这一族第二次不该再打后端", 1, server.requestCount)
        assertEquals("第二次拿到的必须是缓存里那一份", first, second)
    }

    @Test
    fun `写完就回读的那一族每次都真打到后端，不许拿 60 秒前的旧答案当回读`() {
        cachedClient()
        // 段状态：司机点完"到达"之后那一轮回读就是这条（§26 "状态以回读为准"）
        respond("""{"code":0,"msg":"","data":{"list":[{"id":7,"status":7}]}}""")
        respond("""{"code":0,"msg":"","data":{"list":[{"id":7,"status":11}]}}""")

        val before = body("/app-api/transport/driver/legs")
        val after = body("/app-api/transport/driver/legs")

        assertEquals("回读必须两次都到后端", 2, server.requestCount)
        assertNotEquals("第二次还拿到旧状态，说明这一族又被缓存了", before, after)
        assertTrue(after.contains("\"status\":11"))
    }

    @Test
    fun `缓存白名单只收 browse 类，近名端点不得被后缀顺带命中`() {
        listOf(
            "/app-api/transport/notice/list",
            "/app-api/transport/weather",
            "/app-api/transport/bus/lines",
            "/app-api/transport/bus/nearby",
            "/app-api/transport/product/list",
            "/app-api/transport/product/page",
            "/app-api/transport/product/get",
            "/app-api/transport/send/stations",
        ).forEach { assertEquals("$it 该给 60 秒", "max-age=60", cachePolicyFor(it)) }

        listOf(
            "/app-api/transport/driver/legs",
            "/app-api/transport/driver/current-leg",
            "/app-api/transport/driver/messages/unread-count",
            "/app-api/transport/notification/unread-count",
            // 近名陷阱：白名单里是 /transport/product/page，而这是 /transport/product-order/page，
            // 后缀匹配只差一个连字符就会把"下单后回读"也缓存 60 秒。
            "/app-api/transport/product-order/page",
            "/app-api/transport/send/arrangements",
            "/app-api/member/user/get",
            "/app-api/member/auth/refresh-token",
        ).forEach { assertEquals("$it 必须 no-store", "no-store", cachePolicyFor(it)) }
    }

    @Test
    fun `协程取消要穿透到这条请求：离开这一屏之后它不该还挂在网上`() {
        // 旧形状 `newCall(request).execute()` 阻塞在 IO 线程里，取消时没人 cancel Call：
        // 请求照样跑完，占着连接池与流量。这里把响应压 30 秒，看取消能不能立刻收场。
        TestHost.strictBackend()
        server.enqueue(
            MockResponse()
                .setBody("""{"code":0,"msg":"","data":{"list":[]}}""")
                .setHeadersDelay(30, TimeUnit.SECONDS)
        )
        runBlocking {
            val job = launch { runCatching { Api.get("/app-api/transport/driver/legs") } }
            delay(300)
            val cancelAt = System.currentTimeMillis()
            job.cancel()
            val finished = withTimeoutOrNull(5_000) { job.join(); true } == true
            val cancelMillis = System.currentTimeMillis() - cancelAt
            assertTrue(
                "取消没穿透：等了 ${cancelMillis} ms 还没收场（旧形状要干等那 30 秒，请求还占着连接池）",
                finished && cancelMillis < 2_000
            )
        }
    }

    @Test
    fun `退登要真把磁盘响应缓存清掉，而且清完缓存还能继续工作`() {
        // 走的是生产的 `Api.clearCache()`（由 `Repo.clearSessionLocal()` 触发）。
        // 反射只注入 `httpCache` 这一个字段 —— 它是 `private var`，而 `http` 是 `static final` 的
        // lazy 盒子，反射改不动；所以这里用与 `Api.http` 同形的客户端，把同一个 Cache 实例交给 Api。
        // 如果 `clearCache()` 只是把字段置 null 或顺手删了目录，下面那两条断言会分别红。
        val cache = Cache(tempDir(), 512L * 1024L)
        cachedClient(cache)
        val url = "/app-api/transport/notice/list"
        repeat(4) { respondData("""{"list":[{"id":1}],"total":1}""") }

        body(url)
        body(url)
        assertEquals("前两条里必须有一条命中缓存（不然下面那条断言是空的）", 1, server.requestCount)

        val field = Api.javaClass.getDeclaredField("httpCache").apply { isAccessible = true }
        val old = field.get(null)
        field.set(null, cache)
        try {
            Repo.clearSessionLocal()

            val refetched = body(url)   // 清过 ⇒ 必须重新问后端
            val cachedAgain = body(url)  // 紧接着这条又该命中缓存 ⇒ 证明客户端没被弄残
            assertEquals("清完会话必须重新问后端一次", 2, server.requestCount)
            assertEquals(refetched, cachedAgain)
            assertTrue("读回来的必须是真正文，不是空响应顶数：$cachedAgain", cachedAgain.contains("\"id\":1"))
        } finally {
            field.set(null, old)
            runCatching { cache.close() }
        }
    }
}
