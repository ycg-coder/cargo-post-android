package com.shanxiang.buspost.ui.user

import com.shanxiang.buspost.ServerTest
import com.shanxiang.buspost.TestHost
import com.shanxiang.buspost.core.net.ApiException
import com.shanxiang.buspost.core.net.NetworkException
import com.shanxiang.buspost.data.model.Geo
import com.shanxiang.buspost.data.model.Station
import com.shanxiang.buspost.data.repo.Repo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 寄货闭环：表单校验拦在本地，订单号只认后端，提交后必须回读 /send/page。
 * 这里同时钉住"没传到后端照片就不许提交"这条闸门——它是演示态数据混进真实业务的入口。
 */
class SendOrderTest : ServerTest() {

    private val form = mapOf(
        "pickupStationId" to 11L,
        "deliveryStationId" to 22L,
        "goodsName" to "山村土蜂蜜",
        "goodsWeight" to 5.0,
        "itemCount" to 2,
        "photoUrl" to "https://cdn.example.com/cargo-1.jpg",
        "receiverName" to "李秀兰",
        "receiverMobile" to "13800004021",
        "receiverAddress" to "山香村 3 组",
    )

    @Test
    fun `下单回执只取后端下发的字段`() = runBlocking {
        TestHost.strictBackend()
        respondData(
            """{"orderNo":"BS20260928001","pickupCode":"8421","totalAmount":18.5,
               "reviewStatus":2,"reviewReasonCodes":"OVER_WEIGHT",
               "servicePointStationName":"山香镇驿站","servicePointDistanceKm":1.2}"""
        )
        val ack = Repo.createSendOrder(form)
        assertEquals("BS20260928001", ack.orderNo)
        assertEquals("8421", ack.pickupCode)
        assertEquals(18.5, ack.amount, 0.001)
        assertEquals(2, ack.reviewStatus)
        assertEquals("OVER_WEIGHT", ack.reviewReasonCodes)
        assertEquals("山香镇驿站", ack.servicePointStationName)
        assertFalse(ack.demo)
        assertNull(ack.error)

        val request = lastRequest()
        assertEquals("POST", request.method)
        assertEquals("/app-api/transport/send/create", request.path)
        val body = bodyOf(request)
        assertTrue(body.contains("\"goodsName\":\"山村土蜂蜜\""))
        assertTrue(body.contains("\"photoUrl\":\"https://cdn.example.com/cargo-1.jpg\""))
        assertTrue(body.contains("\"pickupStationId\":11"))
    }

    @Test
    fun `后端没回订单号时不编造一个`() = runBlocking {
        TestHost.strictBackend()
        respondData("{}")
        val ack = Repo.createSendOrder(form)
        assertEquals("", ack.orderNo)
        assertEquals("", ack.pickupCode)
        assertEquals(0.0, ack.amount, 0.001)
        assertFalse("空回执也是真实成功，不能标成演示数据", ack.demo)
    }

    @Test
    fun `后端从不发的自造别名不再被采纳`() = runBlocking {
        // 2026-10-08 对 127.0.0.1:48080 实测 /send/create 的回执键：
        // orderNo/pickupCode/totalAmount/reviewStatus/reviewReasonCodes/servicePoint*（见 AppSendOrderRespVO）
        // fee / amount / no / code / bizOrderNo 三面 0 生产者，只发这些键时回执必须留空。
        TestHost.strictBackend()
        respondData("""{"fee":18.5,"amount":9.9,"no":"BS-DEAD","code":"0000","bizOrderNo":"BS-DEAD2"}""")
        val ack = Repo.createSendOrder(form)
        assertEquals("", ack.orderNo)
        assertEquals("", ack.pickupCode)
        assertEquals(0.0, ack.amount, 0.001)
    }

    @Test
    fun `提交后回读寄货列表且以服务端那条为准`() = runBlocking {
        TestHost.strictBackend()
        respondData("""{"orderNo":"BS20260928001","pickupCode":"8421","totalAmount":18.5}""")
        respondData("""{"list":[{"id":501,"orderNo":"BS20260928001","goodsName":"山村土蜂蜜","status":0,"pickupCode":"8421"}],"total":1}""")
        Repo.createSendOrder(form)
        val page = Repo.sendOrders(1, 20)

        assertEquals(2, server.requestCount)
        assertEquals("/app-api/transport/send/create", server.takeRequest().path)
        val reloaded = server.takeRequest()
        assertEquals("GET", reloaded.method)
        assertTrue(reloaded.path!!.startsWith("/app-api/transport/send/page"))
        assertTrue(reloaded.path!!.contains("pageNo=1"))
        assertTrue(reloaded.path!!.contains("pageSize=20"))

        assertEquals(1, page.items.size)
        assertEquals("BS20260928001", page.items.first().orderNo)
        assertEquals(501L, page.items.first().id)
        assertFalse(page.demo)
        assertNull(page.error)
    }

    @Test
    fun `下单业务失败原样抛出后端原因`() = runBlocking {
        TestHost.strictBackend()
        respondBizError(1005001001, "货物超出限重")
        try {
            Repo.createSendOrder(form)
            assertTrue("业务失败必须抛出", false)
        } catch (e: ApiException) {
            assertEquals(1005001001, e.code)
            assertEquals("货物超出限重", e.message)
        }
    }

    @Test
    fun `下单网络失败在正式构建不回落成演示单`() = runBlocking {
        TestHost.strictBackend()
        respond("<html>bad gateway</html>", 502, "text/html")
        try {
            Repo.createSendOrder(form)
            assertTrue(false)
        } catch (e: NetworkException) {
            assertTrue(e.message!!, e.message!!.contains("HTTP 502"))
        }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `演示构建失败才回落并明确标记`() = runBlocking {
        TestHost.demoBackend()
        respondBizError(500, "服务异常")
        val ack = Repo.createSendOrder(form)
        assertTrue("debug 形态回落演示数据时必须标出来", ack.demo)
        assertTrue(ack.orderNo.isNotEmpty())
    }

    @Test
    fun `寄货列表失败与真的没下单分得开`() = runBlocking {
        TestHost.strictBackend()
        respondData("""{"list":[],"total":0}""")
        val empty = Repo.sendOrders()
        assertTrue(empty.items.isEmpty())
        assertNull(empty.error)

        respond("<html>err</html>", 503, "text/html")
        val failed = Repo.sendOrders()
        assertTrue(failed.items.isEmpty())
        assertNotNull("失败不能被渲染成空列表", failed.error)
    }

    @Test
    fun `表单不全时提交闸门拦住且不发请求`() {
        TestHost.strictBackend()
        val vm = SendViewModel()
        assertEquals("请填写货物名称", vm.validateStep1())

        vm.goodsName = "土蜂蜜"
        vm.weight = "10"
        assertEquals("请填写货物长宽高", vm.validateStep1())

        vm.length = "30"
        vm.width = "20"
        vm.height = "15"
        assertEquals("请选择取货站点", vm.validateStep1())

        vm.pickup = Station(id = 11L, name = "山香镇客运站台", geo = Geo(29.5, 106.5))
        vm.delivery = vm.pickup
        assertEquals("取货站点和送达站点不能相同", vm.validateStep1())

        vm.delivery = Station(id = 22L, name = "白朝村驿站", geo = Geo(29.6, 106.6))
        assertEquals("", vm.validateStep1())
        // 第 2 步的硬闸门：照片没真的上传成功就不让提交，绝不能带空 photoUrl 塞单进后端
        assertEquals("请先拍照确认货物", vm.validateStep2())
        assertEquals(0, server.requestCount)
    }
}
