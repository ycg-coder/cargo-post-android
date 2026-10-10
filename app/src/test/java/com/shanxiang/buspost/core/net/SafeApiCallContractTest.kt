package com.shanxiang.buspost.core.net

import com.shanxiang.buspost.ServerTest
import com.shanxiang.buspost.TestHost
import com.shanxiang.buspost.data.repo.Repo
import com.shanxiang.buspost.data.store.Prefs
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * 指令 v2.1 §七 点名的 unified helper（`safeApiCall()`）的**行为**契约，核对表 V21-30 点名的那本。
 *
 * 三件事各钉一段，缺一不可：
 * ① 取消穿透：`CancellationException` 不能变成一个可以写回画面的 `ApiResult`；
 * ② 只分四类：401 / 业务码 / 传输层各自归位，`getOrThrow()` 回抛的还是同一个类型（分类不退化）；
 * ③ 程序错误不收：`IllegalStateException` 这类缺陷穿透，**不领一张"业务失败"的通行证**（§六）。
 *
 * 第 5 组用例是本轮唯一的**真实修复**（其余四点见 D-042 的"形状归一"分类）：
 * `Repo.linePolyline` 以前把失败洗成空列表，地图那句"后端未回传该线路折线"就成了假话。
 *
 * ⚠ 页面那半边（`BusScreen.polylineReason()` 的两条措辞、`RouteMap` 不拿站点连线顶替）在这里**测不到**：
 * 工程无 Robolectric，Compose 渲染不进 JVM 用例，只能由 `tools/check_safe_call_probes.sh` 按内容静态复核。
 */
class SafeApiCallContractTest : ServerTest() {

    /** 与 `DemoExceptionIsolationTest` 同一手法：把缺陷挂在 URL 组装这一步（发请求之前） */
    private fun probe(fault: Throwable) = object : Any() {
        override fun toString(): String = throw fault
    }

    // ---------- ① 取消穿透 ----------

    @Test
    fun `块里抛出的取消原样穿透，不收成 ApiResult`() = runBlocking {
        val outcome = runCatching { safeApiCall<Unit> { throw CancellationException("探针：页面已经离场") } }
        assertFalse("取消被收成了一次正常返回：§七 禁止的那条链路又通了", outcome.isSuccess)
        val e = outcome.exceptionOrNull()
        assertTrue("取消必须还是取消，不是 NetworkException/ApiException：${e?.javaClass?.name}", e is CancellationException)
    }

    @Test
    fun `协程已取消时 safeApiCall 不谎报一次网络失败`() = runBlocking {
        TestHost.strictBackend()
        var caught: Throwable? = null
        // 取消的是**内层作用域**，不是 runBlocking 自己：后者一取消，runBlocking 收尾就把协程异常
        // 抛给 JUnit（实测 JobCancellationException: BlockingCoroutine was cancelled），用例红得和契约无关。
        runCatching {
            coroutineScope {
                coroutineContext[Job]!!.cancel()
                caught = runCatching { safeApiCall { Api.get("/app-api/transport/bus/nearby") } }
                    .fold({ null }, { it }) // 被收成 ApiResult 时 caught 就是 null，下面那条断言会当场红
            }
        }
        assertTrue("取消必须原样穿出，不是 NetworkException/ApiException：${caught?.javaClass?.name}", caught is CancellationException)
        assertEquals("取消发生在出网之前，一个请求都不该发到后端", 0, server.requestCount)
    }

    // ---------- ② 四类分对，回抛不退化成同一个说法 ----------

    @Test
    fun `业务码 传输失败 会话失效分别归三类`() = runBlocking {
        TestHost.strictBackend()

        respondBizError(5001, "取件码已使用")
        val biz = safeApiCall { Api.put("/app-api/transport/pickup/verify") }
        assertTrue("业务失败被分进了别的类：$biz", biz is ApiResult.BusinessError)
        assertEquals(5001, (biz as ApiResult.BusinessError).code)

        respond("boom", 503)
        val net = safeApiCall { Api.get("/app-api/transport/bus/nearby") }
        assertTrue("5xx 是传输层不可用，不是业务码：$net", net is ApiResult.NetworkError)
        assertFalse(net.isSuccess)

        Api.token = "t"
        respond("""{"code":401,"msg":"登录已失效","data":null}""", 401)
        val un = safeApiCall { Api.get("/app-api/transport/send/page") }
        assertTrue("401 混进 BusinessError 就没人和『清会话 + 跳登录』对上号：$un", un is ApiResult.Unauthorized)
    }

    @Test
    fun `getOrThrow 回抛同一个类型，分类不在这里退化`() = runBlocking {
        TestHost.strictBackend()
        respondBizError(1009004001, "商品库存不足")
        val biz = safeApiCall { Api.post("/app-api/transport/product-order/create", mapOf("productId" to 1)) }
        try {
            biz.getOrThrow()
            fail("BusinessError.getOrThrow 必须抛 ApiException")
        } catch (e: ApiException) {
            assertEquals("商品库存不足", e.message)
            assertEquals(1009004001, e.code)
        }

        respond("boom", 503)
        val net = safeApiCall { Api.get("/app-api/transport/bus/nearby") }
        try {
            net.getOrThrow()
            fail("NetworkError.getOrThrow 必须抛 NetworkException")
        } catch (e: NetworkException) {
            assertTrue("传输失败必须带着原因，不能是个空壳：'${e.message}'", e.message.isNotEmpty())
        }
    }

    // ---------- ③ 程序错误不领通行证 ----------

    @Test
    fun `客户端缺陷穿透 safeApiCall，不被发一张业务失败的通行证`() = runBlocking {
        TestHost.strictBackend()
        val fault = IllegalStateException("探针：状态机不自洽")
        val outcome = runCatching {
            safeApiCall { Api.get("/app-api/transport/notice/list", mapOf("probe" to probe(fault))) }
        }
        val e = outcome.exceptionOrNull() ?: fail("缺陷被收成 $outcome —— 它就成了一个可以画到页面上的『业务失败』")
        assertTrue("类型不能变（变成 NetworkException 就是被接住过）：${e.javaClass.name}", e is IllegalStateException)
        assertEquals("缺陷发生在组 URL 时，一个请求都不该发到后端", 0, server.requestCount)
    }

    // ---------- ④ 迁移点：失败与"后端回空"分家 ----------

    /**
     * 本轮的真实修复点：以前这三件事都返回 `emptyList()`，页面只能把它们统一说成"后端没有这条线路的轨迹"。
     * 现在失败原样抛出（`BusScreen.loadPolyline()` 用 safeApiCall 把分类记成"这次没问到"），
     * 而**成功但报文里没有几何点**照旧是空列表——那是后端真的没给。
     */
    @Test
    fun `折线传输失败抛出而不是洗成空列表`() = runBlocking {
        TestHost.strictBackend()
        respond("boom", 503)
        val failure = runCatching { Repo.linePolyline("L10") }.exceptionOrNull()
        assertTrue("网络失败又被洗成空列表了：$failure", failure is NetworkException)
        assertEquals("请求确实发出去了，失败发生在后端：${server.requestCount}", 1, server.requestCount)
    }

    @Test
    fun `折线业务失败抛出且带着后端原因`() = runBlocking {
        TestHost.strictBackend()
        respondBizError(5002, "线路不存在")
        val failure = runCatching { Repo.linePolyline("L10") }.exceptionOrNull()
        assertTrue(failure is ApiException)
        assertEquals("线路不存在", failure?.message)
    }

    @Test
    fun `折线成功但后端没给几何点才是空列表`() = runBlocking {
        TestHost.strictBackend()
        respondData("""{"points":[]}""")
        val points = Repo.linePolyline("L10")
        assertTrue("成功回包里没几何点，就该是真空：$points", points.isEmpty())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `折线成功时按真实点位返回`() = runBlocking {
        TestHost.strictBackend()
        respondData("""{"points":[{"lat":30.11,"lng":103.21},{"lat":30.12,"lng":103.22}]}""")
        val points = Repo.linePolyline("L10")
        assertEquals(2, points.size)
        assertEquals(30.11, points[0].lat, 1e-9)
        val request = server.takeRequest(1, TimeUnit.SECONDS)!!
        // 这条接口带 query（routeId），不能照抄裸接口的 endsWith：实测 endsWith("/transport/bus/line-polyline")
        // 在 `?routeId=L10` 上是红的，红得和契约没关系。
        val path = request.path!!
        assertTrue(path, path.startsWith("/app-api/transport/bus/line-polyline"))
        assertTrue(path, path.contains("routeId=L10"))
    }

    /** 未读数那处是**形状归一**（语义与旧的 attempt 一致），钉在这里是为了迁移后行为没漂 */
    @Test
    fun `未读数读不到仍是 null，不落 0 也不抛`() = runBlocking {
        TestHost.strictBackend()
        respond("boom", 503)
        assertNull("读不到数不能变成 0：0 是后端说『确实没有未读』", Repo.unreadCount())
        respondData("7")
        assertEquals(7L, Repo.unreadCount())
    }

    /** 司机域那道"未绑定不出网"的门本轮从兜底改成了显式判断，两条契约都得原地不动 */
    @Test
    fun `未绑定司机档案时未读数读不到且不出网`() = runBlocking {
        TestHost.strictBackend()
        Prefs.driverId = 0L
        assertNull(Repo.driverUnreadCount())
        assertEquals(0, server.requestCount)
        Prefs.driverId = 61L
        respondData("4")
        assertEquals(4L, Repo.driverUnreadCount())
    }

    /**
     * 退出登录那处也是**真实改进**，但改进的是"可见"不是"顺序"：
     * 后端没确认撤销也必须清本地（会话卫生优先），失败只进日志；反过来把登录态留在设备上才是事故。
     */
    @Test
    fun `退出时后端失败也照样清本地且不抛给调用方`() = runBlocking {
        TestHost.strictBackend()
        Prefs.token = "server-token-9"
        Api.token = "server-token-9"
        respond("boom", 503)
        Repo.logout()
        assertEquals("", Prefs.token)
        assertNull(Api.token)
        assertFalse(Api.demoSession)
        assertEquals(1, server.requestCount)
    }
}
