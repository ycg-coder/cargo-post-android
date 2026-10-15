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

    /**
     * #51 同一个 URL 上的**两种意图**必须都能提交。
     *
     * `LegDriverAction.ARRIVE_DEST` 与 `ENTER_DELIVERY` 的 api 串都是 `arrive-dest`
     *（枚举注释原话："段停在 8 又不需交接时，只有再发一次 arrive-dest 才会转入派送 —— 这是后端分支，不是重复动作"），
     * 而后端 `legAction` 的 switch 按段的当前状态分两支；这些段动作端点**没有** `@Idempotent`
     *（全模块只有 `AppProductOrderController:38` / `AppSendController:119` 两处有），
     * 所以"第二次被拦"完全是本机闸自己造的：段会永远停在"到达卸货站"进不了派送。
     * 上一那条用例钉的是"不同动作分键"，它看不见这一格——两个动作的 api 串**相同**时它照样绿。
     */
    @Test
    fun `同一URL的两种意图各发各的_只有枚举名能分开它们`() = runBlocking {
        TestHost.strictBackend()
        respondData("true")
        respondData("true")
        val arrive = Repo.legAction("arrive-dest", "9509", actionKey = "ARRIVE_DEST")
        val enter = Repo.legAction("arrive-dest", "9509", actionKey = "ENTER_DELIVERY")
        assertFalse("第一次到达必须真的上网络", arrive.notSent)
        assertFalse("转入派送那一次也必须真的上网络（同 URL、不同意图）", enter.notSent)
        assertEquals(2, server.requestCount)
        // 但同一意图连点仍然要拦：修这一格不是把闸门拆掉
        val repeat = Repo.legAction("arrive-dest", "9509", actionKey = "ENTER_DELIVERY")
        assertTrue("同一个意图在窗口内重发仍必须被拦", repeat.notSent)
        assertEquals("拦下后请求数不变", 2, server.requestCount)
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
