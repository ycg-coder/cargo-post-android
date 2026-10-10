package com.shanxiang.buspost.data.repo

import com.shanxiang.buspost.ServerTest
import com.shanxiang.buspost.TestHost
import com.shanxiang.buspost.data.model.Geo
import com.shanxiang.buspost.data.store.Prefs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * V-1（指令 §52）在**仓库 + 真网络**这一层的契约：闸门拦下时必须一个字节都没上网络。
 *
 * 判据只用 `server.requestCount` —— 它是 MockWebServer 自己数的，
 * 比任何"我看代码觉得没发"都硬：请求真发出去了就一定数得到。
 *
 * ⚠ `Repo` 是进程单例，闸门的键会跨用例活着，所以每个用例的班次/订单/段 id 都不同
 * （9101 / 9102 / 9301 / 9401 / 9402），不靠"清理闸门状态"这种测试后门。
 */
class DriverActionIdempotencyContractTest : ServerTest() {

    @Before
    fun bindDriverProfile() {
        // 司机写路径一律要 driverId，未绑定会在出网前就抛，测不到幂等语义
        Prefs.driverId = 7002L
    }

    @Test
    fun `连点两次发车只有一次真的上网络`() = runBlocking {
        TestHost.strictBackend()
        respondData("true")
        val acks = coroutineScope {
            val first = async { Repo.depart(9101L) }
            val second = async { Repo.depart(9101L) }
            listOf(first.await(), second.await())
        }
        assertEquals("两次提交里必须恰好有一次没上网络", 1, server.requestCount)
        assertEquals(1, acks.count { it.notSent })
        assertEquals(1, acks.count { !it.notSent })
        val request = lastRequest()
        assertEquals("/app-api/transport/driver/depart", request.path)
        // 闸门只决定发不发，绝不改写报文：driverId/shiftId 仍按后端 ReqVO 原样
        val body = bodyOf(request)
        assertTrue(body.contains("\"driverId\":7002"))
        assertTrue(body.contains("\"shiftId\":9101"))
    }

    @Test
    fun `不同班次的发车各发各的`() = runBlocking {
        TestHost.strictBackend()
        respondData("true")
        respondData("true")
        val acks = coroutineScope {
            val a = async { Repo.depart(9102L) }
            val b = async { Repo.depart(9401L) }
            listOf(a.await(), b.await())
        }
        assertEquals(2, server.requestCount)
        assertTrue("不同目标不该互相挡", acks.none { it.notSent })
    }

    @Test
    fun `位置上报有意不进闸门_每个点位都必须真的上报`() = runBlocking {
        TestHost.strictBackend()
        respondData("true")
        respondData("true")
        val first = Repo.reportLocation(9301L, Geo(29.5, 106.5), 42.0)
        val second = Repo.reportLocation(9301L, Geo(29.5, 106.5), 42.0)
        assertFalse(first.notSent)
        assertFalse(
            "周期上报被幂等闸丢掉就是偷点：新鲜度与丢点可见由 DriverLocationService 的合并闸负责（⑦），不是这里",
            second.notSent,
        )
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `后端业务拒绝后同一动作可以立刻重试`() = runBlocking {
        TestHost.strictBackend()
        respondBizError(1_005_002_000, "订单状态不允许装车")
        val failed = runCatching { Repo.pickupVerify(9402L, "8821") }
        assertTrue("业务失败必须原样抛出，不能咽成 notSent", failed.isFailure)
        respondData("true")
        val retry = Repo.pickupVerify(9402L, "8821")
        assertFalse("失败还占着窗口，司机就再也重试不了了", retry.notSent)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `段动作按动作分键_同段不同动作都能提交`() = runBlocking {
        TestHost.strictBackend()
        respondData("true")
        respondData("true")
        val accept = Repo.legAction("accept", "9501")
        val navigate = Repo.legAction("navigate", "9501")
        assertFalse(accept.notSent)
        assertFalse(navigate.notSent)
        assertEquals(2, server.requestCount)
        val duplicate = Repo.legAction("navigate", "9501")
        assertTrue("同一个段上的同一个动作在窗口内重发必须被拦", duplicate.notSent)
        assertEquals("拦下后请求总数仍然不变", 2, server.requestCount)
    }

    @Test
    fun `标记已读也走同一道闸`() = runBlocking {
        TestHost.strictBackend()
        respondData("true")
        val sent = Repo.readDriverMessage(9601L)
        val dup = Repo.readDriverMessage(9601L)
        assertFalse(sent.notSent)
        assertTrue(dup.notSent)
        assertEquals(1, server.requestCount)
        assertEquals("PUT", lastRequest().method)
    }
}
