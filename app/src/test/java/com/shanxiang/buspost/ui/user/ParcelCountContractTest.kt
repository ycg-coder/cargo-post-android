package com.shanxiang.buspost.ui.user

import com.shanxiang.buspost.ServerTest
import com.shanxiang.buspost.TestHost
import com.shanxiang.buspost.awaitUntil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 页首那一句"寄货 N 单 · 商城 M 单"的口径。三个状态必须分开，旧写法只有一个数字格子，
 * 于是"接口挂了"和"用户真的没下单"被念成同一句"寄货 0 单"：
 * **后端给了总数**（念 total，不是本页条数）／**后端真说 0**（念 0）／**这一次没问到**（念"未取到"）。
 *
 * 夹具刻意让 `total` 与 `list.size` 不相等 —— 两者相等时，断言分不清页面读的是哪一个。
 */
class ParcelCountContractTest : ServerTest() {

    @Before
    fun setUpMain() {
        TestHost.installMainDispatcher()
        TestHost.strictBackend()
    }

    /** 与后端 `new PageResult<>(list, total)` 同形：本页几条与全量几条各写各的 */
    private fun enqueueSendPage(total: Int, listSize: Int) {
        val items = (1..listSize).joinToString(",") {
            """{"id":$it,"orderNo":"BS2026100600$it","goodsName":"山村土蜂蜜","status":1}"""
        }
        respondData("""{"list":[$items],"total":$total}""")
    }

    private fun enqueueOrderPage(total: Int, listSize: Int) {
        val items = (1..listSize).joinToString(",") {
            """{"id":$it,"orderNo":"MP2026100600$it","status":1,"totalPrice":18.5}"""
        }
        respondData("""{"list":[$items],"total":$total}""")
    }

    /** 未读角标是这一页第三次出网，夹具给一个裸数字（后端 `CommonResult<Long>`） */
    private fun enqueueUnread(count: Int) = respondData("$count")

    private fun enqueueSendFailure() = respondBizError(1009005000, "寄货列表暂时取不到")

    private fun loaded(sendTotal: Int, sendSize: Int, orderTotal: Int, orderSize: Int): ParcelViewModel {
        enqueueSendPage(sendTotal, sendSize)
        enqueueOrderPage(orderTotal, orderSize)
        enqueueUnread(3)
        return ParcelViewModel().apply {
            load()
            awaitUntil { !loading }
        }
    }

    @Test
    fun `计数取分页总数而不是本页拿回几条`() {
        val vm = loaded(sendTotal = 137, sendSize = 2, orderTotal = 9, orderSize = 1)
        assertEquals(2, vm.sends.size)
        assertEquals(1, vm.orders.size)
        assertEquals(137, vm.sendCount!!.toInt())
        assertEquals(9, vm.orderCount!!.toInt())
        assertEquals("寄货 137 单 · 商城 9 单", parcelHeaderCountText(vm.sendCount, vm.orderCount))
        assertNull(vm.error)

        assertEquals("/app-api/transport/send/page", server.takeRequest().path!!.substringBefore("?"))
        assertEquals("/app-api/transport/product-order/page", server.takeRequest().path!!.substringBefore("?"))
        assertEquals("/app-api/transport/notification/unread-count", server.takeRequest().path!!.substringBefore("?"))
    }

    @Test
    fun `寄货请求失败时那一格说未取到而不是 0`() {
        enqueueSendFailure()
        enqueueOrderPage(9, 1)
        enqueueUnread(0)
        val vm = ParcelViewModel().apply {
            load()
            awaitUntil { !loading }
        }
        assertNull(vm.sendCount)
        assertEquals(9, vm.orderCount!!.toInt())
        assertEquals("寄货未取到 · 商城 9 单", parcelHeaderCountText(vm.sendCount, vm.orderCount))
        assertNotNull(vm.error)
        assertTrue("一段失败不带着另一段一起空", vm.orders.isNotEmpty())
    }

    @Test
    fun `后端真说零单就念零而不是未取到`() {
        val vm = loaded(sendTotal = 0, sendSize = 0, orderTotal = 0, orderSize = 0)
        assertEquals(0, vm.sendCount!!.toInt())
        assertEquals(0, vm.orderCount!!.toInt())
        assertEquals("寄货 0 单 · 商城 0 单", parcelHeaderCountText(vm.sendCount, vm.orderCount))
        assertNull(vm.error)
        assertTrue(vm.sends.isEmpty())
    }

    @Test
    fun `还没加载完不念 0`() {
        val vm = ParcelViewModel()
        assertNull(vm.sendCount)
        assertNull(vm.orderCount)
        assertEquals("寄货未取到 · 商城未取到", parcelHeaderCountText(vm.sendCount, vm.orderCount))
        assertEquals(0, server.requestCount)
    }
}
