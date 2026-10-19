package com.shanxiang.buspost.core.net

import com.shanxiang.buspost.ServerTest
import com.shanxiang.buspost.TestHost
import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * 网络层的分类契约：失败就是失败，真空数据就是空，演示数据只在显式开启的构建里出现。
 * 这一层错了，上面所有页面的「空态 / 错误条 / 演示标签」都会跟着错。
 */
class ApiTest : ServerTest() {

    @Test
    fun `后端数据标记为来自服务器`() = runBlocking {
        TestHost.strictBackend()
        respondData("""{"list":[{"id":1,"name":"山村蜜"}],"total":42}""")
        val p = Api.get("/app-api/transport/product/page", mapOf("pageNo" to 1, "pageSize" to 20))
        assertFalse(p.demo)
        assertTrue(p.fromServer)
        assertEquals(1, p.list.size)
        assertEquals(42, p.total)
    }

    @Test
    fun `真实空数据是成功空集合而不是失败`() = runBlocking {
        TestHost.strictBackend()
        respondData("""{"list":[],"total":0}""")
        val p = Api.get("/app-api/transport/send/page")
        assertTrue(p.list.isEmpty())
        assertFalse(p.demo)
        // data 为 null 同样算"后端确实没给"，不能变成失败，也不能造数据
        respondData("null")
        val empty = Api.get("/app-api/transport/notice/list")
        assertTrue(empty.list.isEmpty())
        assertNull(empty.arr)
    }

    @Test
    fun `业务失败抛出后端原因`() = runBlocking {
        TestHost.strictBackend()
        respondBizError(1009004001, "商品库存不足")
        try {
            Api.post("/app-api/transport/product-order/create")
            fail("业务失败必须抛出，不能被静默成成功")
        } catch (e: ApiException) {
            assertTrue("不该被识别成会话失效", e !is UnauthorizedException)
            assertEquals(1009004001, e.code)
            assertEquals("商品库存不足", e.message)
        }
    }

    @Test
    fun `HTTP 500 归为传输层失败`() = runBlocking {
        TestHost.strictBackend()
        respond("<html>Bad Gateway</html>", 500, "text/html")
        try {
            Api.get("/app-api/transport/bus/nearby")
            fail("5xx 必须抛 NetworkException")
        } catch (e: NetworkException) {
            assertTrue("5xx 要说清是谁没应答：" + e.message, e.message!!.contains("不接受询问"))
            // 2026-10-19 发布口径：屏上那句不带结果码。改的是**期望串**，不是把断言摘掉——
            // 这一行同时钉住"码没漏回屏上"，写回 HTTP 就会红。
            assertTrue("发的包不许把结果码印上屏：" + e.message, !e.message!!.contains("HTTP"))
        }
    }

    @Test
    fun `网络不可达`() = runBlocking {
        TestHost.strictBackend()
        // 先占一个端口再关掉，得到真实的 connection refused
        val dead = MockWebServer()
        dead.start()
        val port = dead.port
        dead.shutdown()
        NetTestOverride.baseUrl = "http://127.0.0.1:$port"
        try {
            Api.get("/app-api/transport/bus/lines")
            fail("连不上必须抛 NetworkException")
        } catch (e: NetworkException) {
            assertTrue(e.message!!.isNotEmpty())
        } catch (e: IOException) {
            fail("IOException 应被收成 NetworkException")
        }
    }

    @Test
    fun `响应不是合法 JSON 也算失败`() = runBlocking {
        TestHost.strictBackend()
        respond("<html>ok</html>", 200, "text/html")
        try {
            Api.get("/app-api/transport/home/summary")
            fail("无 JSON 信封的 2xx 不能当成功")
        } catch (e: NetworkException) {
            assertTrue("读不懂就说读不懂：" + e.message, e.message!!.contains("读不懂"))
        }
    }

    @Test
    fun `业务接口的 401 广播会话失效`() = runBlocking {
        TestHost.strictBackend()
        var kicked = 0
        Api.onUnauthorized = { kicked++ }
        Api.token = "expired-token"
        respond("""{"code":401,"msg":"登录状态已过期","data":null}""", 401)
        try {
            Api.get("/app-api/transport/send/page")
            fail("401 必须抛 UnauthorizedException")
        } catch (e: UnauthorizedException) {
            assertEquals("登录状态已过期", e.message)
        }
        assertEquals(1, kicked)
    }

    @Test
    fun `登录接口的 401 是口令错误不是会话失效`() = runBlocking {
        TestHost.strictBackend()
        var kicked = 0
        Api.onUnauthorized = { kicked++ }
        respond("""{"code":401,"msg":"账号或密码不正确","data":null}""", 401)
        try {
            Api.post("/app-api/member/auth/login", mapOf("mobile" to "13800000000", "password" to "wrong"))
            fail("登录失败必须抛异常")
        } catch (e: ApiException) {
            assertEquals(401, e.code)
            assertEquals("账号或密码不正确", e.message)
            assertFalse("登录失败不该把用户踢回登录页", e is UnauthorizedException)
        }
        assertEquals(0, kicked)
    }

    @Test
    fun `令牌随请求带上`() = runBlocking {
        TestHost.strictBackend()
        Api.token = "server-access-token"
        respondData("[]")
        Api.get("/app-api/member/address/list")
        assertEquals("Bearer server-access-token", lastRequest().getHeader("Authorization"))
    }

    @Test
    fun `演示构建才会回落演示数据`() = runBlocking {
        TestHost.demoBackend()
        respondBizError(1009004001, "商品库存不足")
        val p = Api.post("/app-api/transport/product-order/create", mapOf("productId" to 1))
        // 回落出来的数据必须打上演示标记，页面才能挂"演示数据"标签
        assertTrue(p.demo)
        assertFalse(p.fromServer)

        TestHost.strictBackend()
        respondBizError(1009004001, "商品库存不足")
        try {
            Api.post("/app-api/transport/product-order/create", mapOf("productId" to 1))
            fail("关闭演示开关后同样的失败必须原样抛出")
        } catch (e: ApiException) {
            assertEquals("商品库存不足", e.message)
        }
    }

    /**
     * F-2 第 8 步把 helper 改名为 `safeApiCall`（指令 §七 点的那个名字），这条用例**原样跟着改名**、
     * 断言一个没减：它就是"0 调用点先证方法"那条教训里证伪我猜测的那份现成证据 ——
     * 非挂起的 lambda 形参在 `inline` 函数里可以包挂起调用（:171 这行 `Api.put(...)` 今天就在编译通过）。
     * 取消穿透 / 程序错误穿透 / linePolyline 的失败≠空列表，那三条行为在 `SafeApiCallContractTest` 里钉。
     */
    @Test
    fun `safeApiCall 只做分类不吞异常`() = runBlocking {
        TestHost.strictBackend()
        assertEquals("v", ApiResult.Success("v").getOrNull())
        assertEquals("x!", safeApiCall { "x" }.map { it + "!" }.getOrNull())

        respondBizError(5001, "取件码已使用")
        val biz = safeApiCall { Api.put("/app-api/transport/pickup/verify") }
        assertTrue(biz is ApiResult.BusinessError)
        assertEquals(5001, (biz as ApiResult.BusinessError).code)

        respond("boom", 503)
        val net = safeApiCall { Api.get("/app-api/transport/bus/nearby") }
        assertTrue(net is ApiResult.NetworkError)
        assertFalse(net.isSuccess)

        Api.token = "t"
        respond("""{"code":401,"msg":"登录已失效","data":null}""", 401)
        val un = safeApiCall { Api.get("/app-api/transport/send/page") }
        assertTrue(un is ApiResult.Unauthorized)

        try {
            biz.getOrThrow()
            fail("BusinessError.getOrThrow 必须抛 ApiException")
        } catch (e: ApiException) {
            assertEquals("取件码已使用", e.message)
        }
    }
}
