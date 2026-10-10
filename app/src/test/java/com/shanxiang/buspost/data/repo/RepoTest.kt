package com.shanxiang.buspost.data.repo

import com.shanxiang.buspost.ServerTest
import com.shanxiang.buspost.TestHost
import com.shanxiang.buspost.core.net.Api
import com.shanxiang.buspost.core.net.ApiException
import com.shanxiang.buspost.core.net.NetworkException
import com.shanxiang.buspost.data.model.Role
import com.shanxiang.buspost.data.store.Prefs
import com.shanxiang.buspost.ui.common.toState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 仓库层的三态契约：成功有数据 / 成功但空 / 失败。
 * 页面完全依赖这三者可区分，所以这里必须钉死。
 */
class RepoTest : ServerTest() {

    @Test
    fun `列表取到后端数据并带总数`() = runBlocking {
        TestHost.strictBackend()
        respondData("""{"list":[{"id":1,"name":"山村土蜂蜜"},{"id":2,"name":"高山核桃"}],"total":37}""")
        val feed = Repo.productsPage(pageNo = 2, pageSize = 20)
        assertEquals(2, feed.items.size)
        assertEquals(37, feed.total)
        assertFalse(feed.demo)
        assertNull(feed.error)
        val request = lastRequest()
        assertTrue(request.path!!, request.path!!.contains("pageNo=2"))
        assertTrue(request.path!!.contains("pageSize=20"))
    }

    @Test
    fun `后端真的没有数据时是空列表不是错误`() = runBlocking {
        TestHost.strictBackend()
        respondData("""{"list":[],"total":0}""")
        val feed = Repo.sendOrders()
        assertTrue(feed.items.isEmpty())
        assertNull(feed.error)
        assertFalse(feed.demo)
        // RemoteState 只有 success 才允许渲染"暂无数据"
        val state = feed.toState()
        assertTrue(state.success)
        assertFalse(state.isError)
    }

    @Test
    fun `请求失败与真空数据分得开`() = runBlocking {
        TestHost.strictBackend()
        respond("<html>gateway</html>", 502, "text/html")
        val feed = Repo.notifications()
        assertNotNull(feed.error)
        assertTrue(feed.items.isEmpty())
        assertFalse(feed.toState().success)
        assertTrue(feed.toState().isError)
    }

    @Test
    fun `登录只认后端下发的 accessToken`() = runBlocking {
        TestHost.strictBackend()
        respondData("""{"id":7,"mobile":"13800004021","nickname":"李秀兰"}""")
        try {
            Repo.login("13800004021", "pw123456", Role.USER)
            assertTrue("响应里没有 token 就不能算登录成功", false)
        } catch (e: ApiException) {
            assertTrue(e.message.isNotEmpty())
        }
        assertEquals("", Prefs.token)
        assertNull(Api.token)
    }

    @Test
    fun `登录成功后会话落到本地并清掉演示标记`() = runBlocking {
        TestHost.strictBackend()
        respondData("""{"accessToken":"server-token-1","id":7,"mobile":"13800004021","nickname":"李秀兰","gender":1}""")
        val account = Repo.login("13800004021", "pw123456", Role.USER)
        assertEquals("server-token-1", account.token)
        assertEquals("server-token-1", Prefs.token)
        assertEquals("server-token-1", Api.token)
        assertEquals(7L, Prefs.userId)
        assertFalse(Api.demoSession)
        val request = lastRequest()
        assertTrue(request.path!!, request.path!!.endsWith("/app-api/member/auth/login"))
        assertTrue(bodyOf(request).contains("\"mobile\":\"13800004021\""))
    }

    @Test
    fun `退出登录把账号维度字段清干净`() = runBlocking {
        TestHost.strictBackend()
        Prefs.token = "server-token-1"
        Prefs.userId = 7L
        Prefs.mobile = "13800004021"
        Prefs.nickname = "李秀兰"
        Prefs.driverId = 9L
        Prefs.driverName = "张师傅"
        Prefs.vehiclePlate = "渝A12345"
        Prefs.driverShiftId = 66L
        Prefs.sendDraft = """{"goodsName":"蜂蜜"}"""
        Prefs.markArrivalAlerted("SH001")
        // 设备级偏好不属于会话，退出后应保持
        Prefs.themeColor = "gold"
        Prefs.elderlyMode = true

        respondData("true")
        Repo.logout()

        assertEquals("", Prefs.token)
        assertEquals("", Prefs.mobile)
        assertEquals("", Prefs.nickname)
        assertEquals(0L, Prefs.userId)
        assertEquals(0L, Prefs.driverId)
        assertEquals("", Prefs.driverName)
        assertEquals("", Prefs.vehiclePlate)
        assertEquals(0L, Prefs.driverShiftId)
        assertEquals("", Prefs.sendDraft)
        assertFalse(Prefs.arrivalAlerted("SH001"))
        assertNull(Api.token)
        assertFalse(Api.demoSession)
        assertEquals("gold", Prefs.themeColor)
        assertTrue(Prefs.elderlyMode)
    }

    @Test
    fun `会话失效与退出走同一套清理`() {
        Prefs.token = "t"
        Prefs.driverId = 3L
        Api.token = "t"
        Repo.clearSessionLocal()
        assertEquals("", Prefs.token)
        assertEquals(0L, Prefs.driverId)
        assertNull(Api.token)
    }

    @Test
    fun `照片上传成功才返回真实地址`() = runBlocking {
        TestHost.strictBackend()
        respondData("""{"id":99,"url":"https://cdn.example.com/file/99.jpg"}""")
        val ack = Repo.uploadPhoto(byteArrayOf(1, 2, 3), "proof.jpg")
        assertEquals("https://cdn.example.com/file/99.jpg", ack.url)
        assertFalse(ack.demo)
        assertEquals("/app-api/infra/file/upload", lastRequest().path)
    }

    @Test
    fun `照片上传失败必须抛出，不能给出假地址`() = runBlocking {
        TestHost.strictBackend()
        respondBizError(500, "文件过大")
        try {
            Repo.uploadPhoto(byteArrayOf(1), "proof.jpg")
            assertTrue("上传失败不应返回可用 url", false)
        } catch (e: ApiException) {
            assertEquals(500, e.code)
        }

        respond("<html>err</html>", 503, "text/html")
        try {
            Repo.uploadPhoto(byteArrayOf(1), "proof.jpg")
            assertTrue(false)
        } catch (e: NetworkException) {
            assertTrue(e.message!!.contains("HTTP 503"))
        }
    }

    @Test
    fun `取消订单失败原样抛出后端原因`() = runBlocking {
        TestHost.strictBackend()
        respondBizError(1005002003, "订单已发车，无法取消")
        try {
            Repo.cancelProductOrder(88L)
            assertTrue(false)
        } catch (e: ApiException) {
            assertEquals("订单已发车，无法取消", e.message)
        }
    }

    @Test
    fun `未绑定司机档案不伪造身份`() = runBlocking {
        TestHost.strictBackend()
        Prefs.token = "server-token-1"
        Api.token = "server-token-1"
        respondData("""{"driverId":0,"name":"","plate":""}""")
        val one = Repo.bindDriver()
        assertNull(one.value)
        assertNotNull(one.error)
        assertEquals(0L, Prefs.driverId)
    }
}
