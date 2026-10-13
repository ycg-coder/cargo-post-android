package com.shanxiang.buspost.data.repo

import com.shanxiang.buspost.ServerTest
import com.shanxiang.buspost.TestHost
import com.shanxiang.buspost.core.net.Api
import com.shanxiang.buspost.core.net.ApiException
import com.shanxiang.buspost.core.net.UnauthorizedException
import com.shanxiang.buspost.data.model.Role
import com.shanxiang.buspost.data.store.Prefs
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * 会话续期契约（真机问题："登录过一次，下次进 App 还要再登录"）。
 *
 * 病根不是"会话没持久化" —— 落盘与恢复两条路本来是通的（`Repo.saveSession` / `restoreSession`）。
 * 真正的原因是后端 access token 只有 1800 秒，而 App **既不存 refreshToken、也不判 expiresTime、
 * 也从不调后端一直存在的 `/member/auth/refresh-token`** ⇒ 30 分钟后第一条业务请求 401，
 * 直接清盘跳登录，用户以为自己要重新登录，其实他从没退出过。
 *
 * 这几条用例钉的是"续期成功时不许踢人"与"续期失败时必须照旧踢人"两半 ——
 * 只钉前半的话，把 401 一律吞掉也能骗绿。
 */
class SessionRefreshContractTest : ServerTest() {

    private val loginBody = """
        {"userId":2048,"accessToken":"new-token","refreshToken":"refresh-1",
         "expiresTime":1790000000000,"mobile":"13900001003","nickname":"张"}
    """.trimIndent()

    @Test
    fun `登录成功要把 refreshToken 与到期时刻一起落盘`() = runBlocking {
        TestHost.strictBackend()
        respondData(loginBody)
        Repo.smsLogin("13900001003", "8888", Role.USER)

        // 改前这两个值是空的（saveSession 只写 token/资料）⇒ 这条用例就是修复本身的锚点
        assertEquals("refresh-1", Prefs.refreshToken)
        assertEquals(1790000000000L, Prefs.expiresAt)
    }

    @Test
    fun `expiresTime 不是数字时不得崩，也不得编一个到期时刻`() = runBlocking {
        TestHost.strictBackend()
        // 后端把 LocalDateTime 序列化成时间戳（TimestampLocalDateTimeSerializer），
        // 但历史/演示形态可能是字符串日期：读不懂就留 0，绝不能拿 now+猜测 冒充"后端说还没到期"
        respondData(
            """{"userId":1,"accessToken":"t","refreshToken":"r","expiresTime":"2026-10-28 00:00"}"""
        )
        Repo.smsLogin("13900001003", "8888", Role.USER)
        assertEquals("r", Prefs.refreshToken)
        assertEquals(0L, Prefs.expiresAt)
    }

    @Test
    fun `业务 401 先静默续期并重试成功，不踢回登录页`() = runBlocking {
        TestHost.strictBackend()
        var kicked = 0
        Api.onUnauthorized = { kicked++ }
        Api.token = "expired-token"
        Prefs.refreshToken = "refresh-1"
        Api.refresher = { Repo.refreshTokenOnce() }

        respond("""{"code":401,"msg":"登录状态已过期","data":null}""", 401)   // 首次业务请求
        respondData("""{"userId":1,"accessToken":"fresh-token","refreshToken":"refresh-2","expiresTime":1790009999000}""") // 续期
        respondData("""{"list":[{"id":9}],"total":1}""")                       // 重试的业务请求

        val payload = Api.get("/app-api/transport/send/page")

        assertFalse("续期成功却把用户踢回登录页", kicked > 0)
        assertEquals(1, payload.list.size)
        assertEquals("fresh-token", Api.token)
        assertEquals("fresh-token", Prefs.token)
        assertEquals("后端轮换的 refreshToken 必须跟上，否则下次续期用的就是旧码", "refresh-2", Prefs.refreshToken)
        assertEquals(1790009999000L, Prefs.expiresAt)

        // 请求顺序与形态：业务(旧 token) → 续期 → 业务(新 token)。MockWebServer 按到达顺序出队，
        // 所以第一次 takeRequest 拿到的就是那条 401 的业务请求，不能跳过它去认续期。
        assertEquals(3, server.requestCount)
        val first = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("/app-api/transport/send/page", first.path)
        assertEquals("Bearer expired-token", first.getHeader("Authorization"))
        val refresh = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertTrue("refreshToken 必须在 query 里：${refresh.path}",
            refresh.path!!.startsWith("/app-api/member/auth/refresh-token") &&
                refresh.path!!.contains("refreshToken=refresh-1"))
        val retried = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("Bearer fresh-token", retried.getHeader("Authorization"))
    }

    @Test
    fun `续期也失败时才按原路踢回登录，且不重试第三次`() = runBlocking {
        TestHost.strictBackend()
        var kicked = 0
        Api.onUnauthorized = { kicked++ }
        Api.token = "expired-token"
        Prefs.refreshToken = "refresh-dead"
        Api.refresher = { Repo.refreshTokenOnce() }

        respond("""{"code":401,"msg":"登录状态已过期","data":null}""", 401)
        respond("""{"code":401,"msg":"刷新令牌已过期","data":null}""", 401)   // /auth/ 路径 → 普通 ApiException，不递归

        try {
            Api.get("/app-api/transport/send/page")
            fail("续期失败后必须仍然抛出会话失效")
        } catch (e: UnauthorizedException) {
            assertEquals("登录状态已过期", e.message)
        }
        assertEquals("续期失败要按原路广播踢人", 1, kicked)
        assertEquals("只发两个请求：业务 401 + 续期 401", 2, server.requestCount)
    }

    @Test
    fun `没有 refreshToken 就不发无谓的续期请求`() = runBlocking {
        TestHost.strictBackend()
        var kicked = 0
        Api.onUnauthorized = { kicked++ }
        Api.token = "expired-token"
        Prefs.refreshToken = ""          // 老会话 / 清过盘
        Api.refresher = { Repo.refreshTokenOnce() }

        respond("""{"code":401,"msg":"登录状态已过期","data":null}""", 401)
        try {
            Api.get("/app-api/transport/order/detail")
            fail("401 仍须抛出")
        } catch (e: ApiException) {
            assertTrue(e is UnauthorizedException)
        }
        assertEquals(1, kicked)
        assertEquals("没有续期码就不该多打一次后端", 1, server.requestCount)
    }

    @Test
    fun `演示回落造出来的 token 不得当成续期成功`() = runBlocking {
        // 演示构建里失败的请求会回落到 DemoData；那种 accessToken 是编的。
        // 拿它"续期成功"等于给一个并不存在的会话续命 —— 比让用户重登更坏。
        TestHost.demoBackend()
        Api.token = "expired-token"
        Prefs.token = "expired-token"
        Prefs.refreshToken = "refresh-1"
        respond("""{"code":500,"msg":"boom","data":null}""", 500)

        assertFalse(Repo.refreshTokenOnce())
        assertEquals("续期失败不得覆写现有 token", "expired-token", Api.token)
    }
}
