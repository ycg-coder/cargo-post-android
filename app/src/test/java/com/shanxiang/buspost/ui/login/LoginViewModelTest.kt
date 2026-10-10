package com.shanxiang.buspost.ui.login

import com.shanxiang.buspost.ServerTest
import com.shanxiang.buspost.TestHost
import com.shanxiang.buspost.awaitUntil
import com.shanxiang.buspost.core.net.Api
import com.shanxiang.buspost.core.net.NetTestOverride
import com.shanxiang.buspost.data.model.Role
import com.shanxiang.buspost.data.store.Prefs
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * 登录页的诚实性：校验拦在本地，失败原因来自后端，会话只认后端 token。
 * 演示入口属于 debug 形态，release 形态下必须点不动。
 */
class LoginViewModelTest : ServerTest() {

    @Before
    fun setUpMain() {
        TestHost.installMainDispatcher()
    }

    private fun filled(
        mobile: String = "13800004021",
        password: String = "pw123456",
        agreed: Boolean = true,
    ): LoginViewModel = LoginViewModel().apply {
        this.mobile = mobile
        this.password = password
        this.agreed = agreed
    }

    @Test
    fun `本地校验不通过就不发请求`() {
        val vm = LoginViewModel()
        vm.submit { fail("不该回调成功") }
        assertEquals("请输入 11 位手机号", vm.error)
        assertEquals(0, server.requestCount)

        vm.mobile = "13800004021"
        vm.submit { fail("不该回调成功") }
        assertEquals("密码至少 6 位", vm.error)

        vm.password = "pw123456"
        vm.submit { fail("不该回调成功") }
        assertEquals("请先阅读并同意服务协议", vm.error)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `正式构建没有演示入口`() {
        TestHost.strictBackend()
        val vm = LoginViewModel()
        assertFalse(vm.demoEntryAvailable)
        vm.demoEnter { fail("演示登录不该被放行") }
        assertEquals("当前构建不支持演示登录", vm.error)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `登录成功用的是后端 token`() {
        TestHost.strictBackend()
        respondData("""{"accessToken":"server-token-9","id":7,"mobile":"13800004021","nickname":"李秀兰"}""")
        respondData("""{"id":7,"mobile":"13800004021","nickname":"李秀兰","gender":1}""")
        val vm = filled()
        val done = CountDownLatch(1)
        var role: Role? = null
        vm.submit { r -> role = r; done.countDown() }
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertEquals(Role.USER, role)
        assertEquals("server-token-9", Prefs.token)
        assertEquals("server-token-9", Api.token)
        assertFalse("真实登录后不能留在演示态", Api.demoSession)
        assertNull(vm.error)
        assertFalse(vm.busy)
        assertEquals(7L, Prefs.userId)
    }

    @Test
    fun `登录失败要说出后端的原因`() {
        TestHost.strictBackend()
        Api.onUnauthorized = { fail("口令错误不该被当成会话失效广播") }
        respondBizError(1009003000, "账号或密码不正确")
        val vm = filled(password = "wrong-password")
        vm.submit { fail("登录失败不该回调成功") }
        awaitUntil { vm.error != null || !vm.busy }
        assertTrue("错误文案要带后端原因，实际：${vm.error}", vm.error?.contains("账号或密码不正确") == true)
        assertEquals("", Prefs.token)
        assertFalse(vm.busy)
    }

    @Test
    fun `连不上后端说的是网络而不是账号问题`() {
        TestHost.strictBackend()
        NetTestOverride.baseUrl = "http://127.0.0.1:1"
        val vm = filled()
        vm.submit { fail("连不上后端不该进入登录态") }
        awaitUntil { vm.error != null || !vm.busy }
        assertTrue("实际：${vm.error}", vm.error?.contains("网络") == true)
    }

    @Test
    fun `司机账号未绑定档案时不进入司机端`() {
        TestHost.strictBackend()
        val vm = filled()
        vm.role = Role.DRIVER
        respondData("""{"accessToken":"server-token-9","id":7,"mobile":"13800004021"}""")
        respondData("""{"id":7,"mobile":"13800004021","nickname":"张师傅"}""")
        respondData("""{"driverId":0,"name":""}""")
        vm.submit { fail("未绑定档案不该回调成功") }
        awaitUntil { vm.error != null || !vm.busy }
        assertNotNull(vm.error)
        assertTrue("实际：${vm.error}", vm.error!!.contains("司机档案"))
        assertEquals(0L, Prefs.driverId)
    }
}
