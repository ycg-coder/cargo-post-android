package com.shanxiang.buspost.driver

import com.shanxiang.buspost.ServerTest
import com.shanxiang.buspost.TestHost
import com.shanxiang.buspost.core.net.ApiException
import com.shanxiang.buspost.core.net.NetworkException
import com.shanxiang.buspost.data.model.Geo
import com.shanxiang.buspost.data.model.PickupTask
import com.shanxiang.buspost.data.model.ShiftState
import com.shanxiang.buspost.data.model.ShiftTask
import com.shanxiang.buspost.data.repo.Repo
import com.shanxiang.buspost.data.store.Prefs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 司机端状态机：状态只能来自后端回读，本机不推进、不缓存、不伪造。
 * 位置上报的报文字段也是契约的一部分：`driverId` 必带（越权校验），`shiftId` 自后端 P1-H 起可选。
 */
class DriverStateTest : ServerTest() {

    private fun shift(status: String) = ShiftTask.parse(
        org.json.JSONObject("""{"shiftId":66,"shiftCode":"K1-0830","routeName":"山香线","status":"$status"}""")
    )

    @Test
    fun `班次状态码与小程序同口径`() {
        assertEquals(ShiftState.WAITING, shift("0").state)
        assertEquals(ShiftState.RUNNING, shift("1").state)
        assertEquals(ShiftState.FINISHED, shift("2").state)
        assertEquals(ShiftState.STOPPED, shift("3").state)
        // 后端没给状态文案时才有本地兜底文案
        assertEquals("待发车", shift("0").statusLabel)
        assertEquals("进行中", shift("1").statusLabel)
        assertEquals("已完成", shift("2").statusLabel)
        assertEquals(66L, shift("0").shiftId)
    }

    @Test
    fun `后端给了状态文案就以后端的为准`() {
        val s = ShiftTask.parse(
            org.json.JSONObject(
                "{\"shiftId\":66,\"status\":\"1\",\"statusName\":\"已在途 · 经停白朝村\"}"
            )
        )
        assertEquals("已在途 · 经停白朝村", s.statusLabel)
        assertEquals(ShiftState.RUNNING, s.state)
    }

    @Test
    fun `发车后状态由回读的班次决定`() = runBlocking {
        TestHost.strictBackend()
        Prefs.driverId = 7001L
        // depart 的响应里写的是 RUNNING，但页面状态只认随后 shifts 回读的那条
        respondData("""{"ok":true,"state":"RUNNING"}""")
        Repo.depart(66L)
        val request = lastRequest()
        assertEquals("POST", request.method)
        assertEquals("/app-api/transport/driver/depart", request.path)
        val body = bodyOf(request)
        assertTrue(body.contains("\"driverId\":7001"))
        assertTrue(body.contains("\"shiftId\":66"))

        respondData("""{"list":[{"shiftId":66,"status":"1","routeName":"山香线"}]}""")
        val running = Repo.driverShifts()
        assertEquals(ShiftState.RUNNING, running.items.first().state)
        assertFalse(running.demo)

        // 班次完成后回读：状态变 FINISHED，工作台据此停止位置上报
        respondData("""{"list":[{"shiftId":66,"status":"2","routeName":"山香线"}]}""")
        assertEquals(ShiftState.FINISHED, Repo.driverShifts().items.first().state)
    }

    @Test
    fun `班次接口不额外传 driverId 且失败与空分开`() = runBlocking {
        TestHost.strictBackend()
        Prefs.driverId = 7001L
        respondData("""{"list":[]}""")
        val empty = Repo.driverShifts()
        assertTrue(empty.items.isEmpty())
        assertNull(empty.error)
        val path = lastRequest().path!!
        assertTrue(path.startsWith("/app-api/transport/driver/shifts"))
        assertFalse("后端按登录会员识别司机，多传 driverId 会被当越权", path.contains("driverId="))

        respond("<html>err</html>", 500, "text/html")
        val failed = Repo.driverShifts()
        assertNotNull("HTTP 500 必须是错误，不能渲染成今天没有班次", failed.error)
        assertTrue(failed.error!!, failed.error!!.contains("不接受询问"))
    }

    @Test
    fun `揽件任务以列表成员表达状态`() = runBlocking {
        TestHost.strictBackend()
        respondData(
            """{"list":[{"orderId":9001,"orderNo":"BS20260928001","goodsName":"土蜂蜜","bizType":"FREIGHT"},
               {"orderId":9002,"orderNo":"MP20260928002","goodsName":"核桃","bizType":"PRODUCT"}]}"""
        )
        val first = Repo.driverPickups()
        assertEquals(2, first.items.size)
        assertFalse(first.demo)

        // 装车完成后后端把它从待办列表里去掉——本机绝不 copy(checked=true) 假装处理过
        respondData("""{"list":[{"orderId":9002,"orderNo":"MP20260928002","goodsName":"核桃","bizType":"PRODUCT"}]}""")
        val second = Repo.driverPickups()
        assertEquals(listOf("MP20260928002"), second.items.map { it.orderNo })
    }

    @Test
    fun `揽件单类型决定作业要求`() {
        val freight = PickupTask.parse(org.json.JSONObject("""{"orderId":1,"orderType":1,"bizType":"FREIGHT"}"""))
        assertFalse(freight.isProduct)
        assertTrue("货运单装车必须拍照", freight.photoRequired)

        val product = PickupTask.parse(org.json.JSONObject("""{"orderId":2,"bizType":"PRODUCT"}"""))
        assertTrue(product.isProduct)
        assertFalse(product.photoRequired)

        val mail = PickupTask.parse(org.json.JSONObject("""{"orderId":3,"orderType":3,"mailNo":"SF123"}"""))
        assertTrue(mail.isMail)
        assertTrue(mail.photoRequired)

        // 后端 `ORDER_TYPE_CARGO=2 / POSTAL=3 / PRODUCT=4`（`DriverAppServiceImpl:101-105`），
        // 商城单的 bizType 恒为 "PRODUCT"（`:352`）。旧写法在 `isProduct` 里还挂着
        // `|| orderType == 3 && mailNo.isEmpty()` ⇒ 一张**没面单号的邮快件**被判成商城单，
        // 装车走 `/driver/product-load`、妥投走 `/driver/product-deliver`，那两个接口按 `ProductOrderDO` 查单，
        // 等于把邮快件塞进商城域（硬规则 1：业务域必须分开）。这条断言就是那一步的闸。
        val mailNoWaybill = PickupTask.parse(org.json.JSONObject("""{"orderId":4,"orderType":3,"bizType":"POSTAL","pickupCode":"4321"}"""))
        assertFalse("没面单号的邮快件不许被分流进商城域", mailNoWaybill.isProduct)
        assertTrue("邮快件仍走 pickup-confirm 那一支的照片闸门", mailNoWaybill.photoRequired)

        val realProduct = PickupTask.parse(org.json.JSONObject("""{"orderId":5,"orderType":4,"bizType":"PRODUCT"}"""))
        assertTrue("真商城单（后端 orderType=4）照旧走 product-load", realProduct.isProduct)
    }

    @Test
    fun `位置上报报文与小程序逐字段一致`() = runBlocking {
        TestHost.strictBackend()
        Prefs.driverId = 7001L
        respondData("""{"ok":true,"accepted":true}""")
        val ack = Repo.reportLocation(66L, Geo(lat = 29.5, lng = 106.5), 42.0)
        assertFalse(ack.demo)

        val request = lastRequest()
        assertEquals("POST", request.method)
        assertEquals("/app-api/transport/driver/location", request.path)
        val body = org.json.JSONObject(bodyOf(request))
        val keys = mutableSetOf<String>()
        val names = body.keys()
        while (names.hasNext()) keys.add(names.next())
        assertEquals(setOf("driverId", "shiftId", "longitude", "latitude", "speedKmh"), keys)
        assertEquals(7001L, body.getLong("driverId"))
        assertEquals(66L, body.getLong("shiftId"))
        assertEquals(106.5, body.getDouble("longitude"), 0.0001)
        assertEquals(29.5, body.getDouble("latitude"), 0.0001)
        assertEquals(42.0, body.getDouble("speedKmh"), 0.0001)
    }

    @Test
    fun `本机没有班次时上报不带 shiftId 但请求照发`() = runBlocking {
        // 后端 P1-H 把 shiftId 改成可选（缺省用当前活跃段兜底）。
        // 旧客户端写死了"没有班次就不传"，运输段/算法派单司机的位置因此永远不上去。
        TestHost.strictBackend()
        Prefs.driverId = 7001L
        respondData("""{"ok":true,"accepted":true}""")
        val ack = Repo.reportLocation(null, Geo(29.5, 106.5), 20.0)
        assertFalse(ack.demo)

        val body = org.json.JSONObject(bodyOf(lastRequest()))
        val keys = mutableSetOf<String>()
        val names = body.keys()
        while (names.hasNext()) keys.add(names.next())
        assertEquals("shiftId 缺省就不出现在报文里（Api 会丢掉 null 值）", setOf("driverId", "longitude", "latitude", "speedKmh"), keys)
        assertEquals(29.5, body.getDouble("latitude"), 0.0001)
    }

    @Test
    fun `报文不许出现后端 VO 没有的字段`() = runBlocking {
        // `AppDriverLocationReqVO` 只有 driverId/shiftId/longitude/latitude/speedKmh，
        // 没有客户端定位时刻字段。谁想"补报历史点"就得先加时间字段——那会被服务端 now 盖掉，
        // 等于伪造轨迹时间（审计 §45、硬规则 20）。这条断言把这条路堵在报文层。
        TestHost.strictBackend()
        Prefs.driverId = 7001L
        respondData("""{"ok":true}""")
        Repo.reportLocation(66L, Geo(29.5, 106.5), 18.0)
        val body = org.json.JSONObject(bodyOf(lastRequest()))
        val forbidden = listOf("locateTime", "timestamp", "reportTime", "clientTime", "time")
        forbidden.forEach { assertTrue("报文不得带 $it", !body.has(it)) }
    }

    @Test
    fun `位置上报失败必须抛出好让服务退避`() = runBlocking {
        TestHost.strictBackend()
        Prefs.driverId = 7001L
        respond("<html>err</html>", 503, "text/html")
        try {
            Repo.reportLocation(66L, Geo(29.5, 106.5), 30.0)
            assertTrue("网络失败不能静默吞掉", false)
        } catch (e: NetworkException) {
            assertTrue(e.message!!.contains("不接受询问"))
        }

        respondBizError(1005004004, "班次已结束")
        try {
            Repo.reportLocation(66L, Geo(29.5, 106.5), 30.0)
            assertTrue(false)
        } catch (e: ApiException) {
            assertEquals("班次已结束", e.message)
        }
    }

    @Test
    fun `未绑定档案时司机接口不伪造身份`() = runBlocking {
        TestHost.strictBackend()
        assertEquals(0L, Prefs.driverId)
        respondData("""{"driverId":0,"name":"","plate":""}""")
        val profile = Repo.driverProfile()
        assertNull(profile.error)
        assertEquals(0L, profile.value!!.driverId)
        assertEquals("后端没绑定就仍然为 0", 0L, Prefs.driverId)
    }
}
