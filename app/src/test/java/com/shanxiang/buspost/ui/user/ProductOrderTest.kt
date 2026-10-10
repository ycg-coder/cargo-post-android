package com.shanxiang.buspost.ui.user

import com.shanxiang.buspost.ServerTest
import com.shanxiang.buspost.TestHost
import com.shanxiang.buspost.data.model.Product
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 商城下单/取消的闭环：写操作成功不等于入账，页面必须回读 product-order/page，
 * 并把后端的失败原因原样说出来，绝不"在本机把这一单抹掉"假装取消成功。
 */
class ProductOrderTest : ServerTest() {

    @Before
    fun setUpMain() {
        TestHost.installMainDispatcher()
    }

    private fun detailVm(): GoodsDetailViewModel = GoodsDetailViewModel().apply {
        product = Product(
            id = 1L,
            name = "山村土蜂蜜",
            subtitle = "海拔 1200 米中华蜂",
            price = 68.0,
            originPrice = 88.0,
            unit = "瓶",
            stock = 12,
            sales = 340,
            image = "https://cdn.example.com/honey.jpg",
            category = "生鲜",
            village = "山香村",
            intro = "",
        )
        receiverName = "李秀兰"
        receiverMobile = "13800004021"
        receiverAddress = "山香村 3 组"
    }

    @Test
    fun `下单成功后回读订单列表`() {
        TestHost.strictBackend()
        com.shanxiang.buspost.data.store.Prefs.mobile = "13800004021"
        respondData("""{"orderId":8801,"orderNo":"MP20260928001","amount":136.0}""")
        respondData("""{"list":[{"id":8801,"orderNo":"MP20260928001","status":0,"amount":136.0}],"total":1}""")
        val vm = detailVm()
        vm.submit()
        assertTrue("下单未进入成功态：${vm.error}", awaitUntil3 { vm.submitted != null })

        val create = server.takeRequest()
        assertEquals("POST", create.method)
        assertEquals("/app-api/transport/product-order/create", create.path)
        val body = bodyOf(create)
        assertTrue(body.contains("\"productId\":1"))
        assertTrue(body.contains("\"quantity\":1"))
        assertTrue(body.contains("\"receiverMobile\":\"13800004021\""))
        // 归属只带登录手机号给后端核对，不本机伪造 userId
        assertTrue(body.contains("\"userMobile\":\"13800004021\""))

        val reload = server.takeRequest()
        assertEquals("GET", reload.method)
        assertTrue(reload.path!!.startsWith("/app-api/transport/product-order/page"))
        assertTrue(reload.path!!.contains("pageNo=1"))

        assertEquals("MP20260928001", vm.submitted)
        assertTrue("列表已回读到这一单", vm.submittedConfirmed)
        assertFalse("正式构建不能出现演示标记", vm.submittedDemo)
        assertNull(vm.error)
        assertFalse(vm.submitting)
    }

    @Test
    fun `列表回读失败时不谎称已在列表确认`() {
        TestHost.strictBackend()
        respondData("""{"orderNo":"MP20260928001"}""")
        respond("<html>err</html>", 503, "text/html")
        val vm = detailVm()
        vm.submit()
        assertTrue(awaitUntil3 { vm.submitted != null })
        // 单号确实来自后端 create 回执，可以给出；但"已在列表确认"这句话不能说
        assertEquals("MP20260928001", vm.submitted)
        assertFalse(vm.submittedConfirmed)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `后端未回订单号时提示核对而不是编一个`() {
        TestHost.strictBackend()
        respondData("{}")
        respondData("""{"list":[],"total":0}""")
        val vm = detailVm()
        vm.submit()
        assertTrue(awaitUntil3 { vm.error != null })
        assertNull(vm.submitted)
        assertTrue("实际：${vm.error}", vm.error!!.contains("后端未回订单号"))
    }

    @Test
    fun `下单业务失败要说出后端原因`() {
        TestHost.strictBackend()
        respondBizError(1005003002, "库存不足")
        val vm = detailVm()
        vm.submit()
        assertTrue(awaitUntil3 { vm.error != null })
        assertEquals("库存不足", vm.error)
        assertNull(vm.submitted)
        assertEquals("创建都没成功，不该再去回读列表", 1, server.requestCount)
    }

    @Test
    fun `收货信息不合法或售罄时不发请求`() {
        TestHost.strictBackend()
        val vm = detailVm()
        vm.receiverMobile = "1380000"
        vm.submit()
        assertEquals("请输入正确的联系电话", vm.error)

        vm.receiverMobile = "13800004021"
        vm.receiverAddress = ""
        vm.submit()
        assertEquals("请输入收货地址", vm.error)

        vm.receiverAddress = "山香村 3 组"
        vm.product = vm.product!!.copy(stock = 0)
        vm.submit()
        assertTrue(vm.soldOut)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `取消成功后重新拉列表`() {
        TestHost.strictBackend()
        val vm = OrdersViewModel()
        respondData(
            """{"list":[{"id":8801,"orderNo":"MP20260928001","status":0,"amount":136.0},
               {"id":8802,"orderNo":"MP20260928002","status":0,"amount":68.0}],"total":2}"""
        )
        vm.load()
        assertTrue("首屏列表未上屏：${vm.error}", awaitUntil3 { vm.productOrders.size == 2 })

        respondData("true")
        respondData("""{"list":[{"id":8802,"orderNo":"MP20260928002","status":0,"amount":68.0}],"total":1}""")
        vm.cancel(8801L)
        assertTrue("取消后没有回读列表：${vm.error}", awaitUntil3 { vm.productOrders.size == 1 })

        assertEquals("/app-api/transport/product-order/page", server.takeRequest().path!!.substringBefore("?"))
        val cancel = server.takeRequest()
        assertEquals("/app-api/transport/product-order/cancel", cancel.path!!.substringBefore("?"))
        assertTrue(cancel.path!!.contains("id=8801"))
        assertTrue(server.takeRequest().path!!.startsWith("/app-api/transport/product-order/page"))

        assertEquals("MP20260928002", vm.productOrders.first().orderNo)
        assertNull(vm.error)
    }

    @Test
    fun `取消失败保留列表并说出后端原因`() {
        TestHost.strictBackend()
        val vm = OrdersViewModel()
        respondData(
            """{"list":[{"id":8801,"orderNo":"MP20260928001","status":1,"amount":136.0},
               {"id":8802,"orderNo":"MP20260928002","status":0,"amount":68.0}],"total":2}"""
        )
        vm.load()
        assertTrue(awaitUntil3 { vm.productOrders.size == 2 })

        respondBizError(1005002003, "订单已发车，无法取消")
        vm.cancel(8801L)
        assertTrue(awaitUntil3 { vm.error != null })

        assertTrue("实际：${vm.error}", vm.error!!.contains("订单已发车，无法取消"))
        assertEquals("失败后不能在本机把这一单抹掉", 2, vm.productOrders.size)
        assertEquals("取消失败不该触发列表回读", 2, server.requestCount)
    }

    private fun awaitUntil3(timeoutMs: Long = 3_000, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(20)
        }
        return condition()
    }
}
