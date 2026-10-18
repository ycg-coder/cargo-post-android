package com.shanxiang.buspost.data

import com.shanxiang.buspost.core.ui.BusTone
import com.shanxiang.buspost.data.model.FreightOrderStatus
import com.shanxiang.buspost.data.model.Geo
import com.shanxiang.buspost.data.model.HandoverStatus
import com.shanxiang.buspost.data.model.LegStatus
import com.shanxiang.buspost.data.model.MissionStop
import com.shanxiang.buspost.data.model.ParcelTrace
import com.shanxiang.buspost.data.model.ProductOrder
import com.shanxiang.buspost.data.model.ProductOrderStatus
import com.shanxiang.buspost.data.model.ReviewStatus
import com.shanxiang.buspost.data.model.STATUS_UNAVAILABLE_LABEL
import com.shanxiang.buspost.data.model.SendOrder
import com.shanxiang.buspost.data.model.TaskItemStatus
import com.shanxiang.buspost.data.model.TransportLeg
import com.shanxiang.buspost.data.model.labelOf
import com.shanxiang.buspost.data.model.toneOf
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 状态码单一映射契约（审计 §18 / §8）。
 *
 * 期望值**逐条抄自后端枚举源码**（不是抄 Android 表，否则测试会跟着错表一起变形）：
 *  - `TransportOrderStatusEnum.java:19-45`     0..13
 *  - `TransportLegStatusEnum.java:24-36`       0..11 + 99
 *  - `ProductOrderStatusEnum.java:14-20`       0..3
 *  - `ReviewStatusEnum.java:20-24`             0..4
 *  - `TaskItemStatusEnum.java:24-32`           0..8（+ `DriverAppServiceImpl.java:123` 的动作名）
 *  - `TransportHandoverStatusEnum.java:24-31`  0..7
 *
 * 反例守卫（这三条一旦变红就说明有人又把注释当契约）：
 *  1. 货运 `4` 必须是"已完成"，不是旧表的"已入池"；
 *  2. 货运 `7` 必须是"待客户操作"，不是旧表的"已完成"；
 *  3. 运输段 `4` 是"前往起点"，既不是完成也不是在途（`TraceView` 旧代码 `status >= 4` 就栽在这）。
 */
class StatusMappingContractTest {

    // ---------- 货运寄货单 ----------

    @Test
    fun `货运 0 至 13 逐码与后端枚举一致`() {
        val expected = mapOf(
            0 to "已创建", 1 to "已入池", 2 to "已分配", 3 to "已发车", 4 to "已完成",
            5 to "已取消", 6 to "待审核", 7 to "待客户操作", 8 to "待入池", 9 to "部分完成",
            10 to "运输中", 11 to "换乘中", 12 to "派送中", 13 to "异常",
        )
        expected.forEach { (code, name) ->
            assertEquals("码 $code", name, FreightOrderStatus.labelOf(code))
        }
        assertEquals(14, FreightOrderStatus.values().size)
    }

    @Test
    fun `审核前置三态不按数值排序理解`() {
        // 反例 1 + 2：旧本地表把 4 读成"已入池"、7 读成"已完成"
        assertEquals("已完成", FreightOrderStatus.labelOf(4))
        assertEquals("待客户操作", FreightOrderStatus.labelOf(7))
        assertNotEquals(FreightOrderStatus.labelOf(7), FreightOrderStatus.labelOf(4))
        assertTrue(FreightOrderStatus.COMPLETED.isFinal)
        assertTrue(FreightOrderStatus.CANCELLED.isFinal)
        // 待客户操作不是终态，界面还得引导用户送到站点
        assertFalse(FreightOrderStatus.WAITING_CUSTOMER_ACTION.isFinal)
        assertTrue(FreightOrderStatus.WAITING_CUSTOMER_ACTION.needsUserAction)
    }

    @Test
    fun `货运 tone 只在路上才给主色`() {
        assertEquals(BusTone.OK, FreightOrderStatus.toneOf(4))
        assertEquals(BusTone.DANGER, FreightOrderStatus.toneOf(5))
        assertEquals(BusTone.DANGER, FreightOrderStatus.toneOf(13))
        listOf(3, 9, 10, 11, 12).forEach {
            assertTrue("码 $it 应在途", FreightOrderStatus.of(it)!!.isOnTheWay)
        }
        listOf(0, 1, 2, 4, 5, 6, 7, 8).forEach {
            assertFalse("码 $it 不该被当成在途", FreightOrderStatus.of(it)!!.isOnTheWay)
        }
    }

    // ---------- 未知码：三态里的"读不到" ----------

    @Test
    fun `未知码不回落成 0 也不自称完成`() {
        listOf(-1, 14, 999).forEach { code ->
            assertEquals(STATUS_UNAVAILABLE_LABEL, FreightOrderStatus.labelOf(code))
            assertEquals(BusTone.NEUTRAL, FreightOrderStatus.toneOf(code))
            assertNullObject(FreightOrderStatus.of(code))
        }
        assertEquals(STATUS_UNAVAILABLE_LABEL, LegStatus.labelOf(12))
        assertEquals(STATUS_UNAVAILABLE_LABEL, ProductOrderStatus.labelOf(4))
    }

    private fun assertNullObject(status: Any?) {
        assertTrue("表外码必须解析为空，不能落成第一个枚举项", status == null)
    }

    // ---------- 运输段 ----------

    @Test
    fun `运输段 0 至 11 与 99 逐码一致`() {
        val expected = mapOf(
            0 to "已规划", 1 to "已分配", 2 to "司机已接单", 3 to "等待发车", 4 to "前往起点",
            5 to "已到达起点", 6 to "装货中", 7 to "运输中", 8 to "已到达终点", 9 to "交接中",
            10 to "派送中", 11 to "已完成", 99 to "异常",
        )
        expected.forEach { (code, name) -> assertEquals("码 $code", name, LegStatus.labelOf(code)) }
        assertEquals(13, LegStatus.values().size)
    }

    @Test
    fun `段只有 11 是完成 4 5 6 是在起点之前`() {
        // 反例 3：旧 TraceView `leg.status >= 4 -> 已完成`
        fun leg(status: Int) = TransportLeg.parse(JSONObject("""{"legId":"L1","seq":1,"status":$status}"""))
        listOf(0, 1, 2, 3, 4, 5, 6, 99).forEach { code ->
            assertFalse("码 $code 不是完成", leg(code).done)
        }
        listOf(7, 8, 9, 10).forEach { code ->
            assertTrue("码 $code 应进行中", leg(code).active)
            assertFalse("码 $code 不是完成", leg(code).done)
        }
        listOf(4, 5, 6).forEach { code -> assertFalse("码 $code 车还没装货，不能算在途", leg(code).active) }
        assertTrue(leg(11).done)
        assertFalse(leg(11).active)
        // 段异常 99 既不完成也不在途
        assertFalse(leg(99).active)
        assertEquals(BusTone.DANGER, leg(99).statusTone)
    }

    @Test
    fun `段文案优先后端 缺失才兜底 表外码说暂不可用`() {
        val fromServer = TransportLeg.parse(JSONObject("""{"status":7,"statusName":"正在赶路"}"""))
        assertEquals("正在赶路", fromServer.statusLabel)
        val fallback = TransportLeg.parse(JSONObject("""{"status":7}"""))
        assertEquals("运输中", fallback.statusLabel)
        val unknown = TransportLeg.parse(JSONObject("""{"status":88}"""))
        assertEquals(STATUS_UNAVAILABLE_LABEL, unknown.statusLabel)
        assertEquals(BusTone.NEUTRAL, unknown.statusTone)
    }

    @Test
    fun `段缺 status 键读成未给而不是已规划`() {
        // §14：缺键不能落成 0，0 在后端是"已规划"，那是业务语义
        val noKey = TransportLeg.parse(JSONObject("""{"legId":"L1","seq":1}"""))
        assertEquals(-1, noKey.status)
        assertEquals(STATUS_UNAVAILABLE_LABEL, noKey.statusLabel)
        assertFalse(noKey.done)
        assertFalse(noKey.active)
    }

    // ---------- 商城订单 ----------

    @Test
    fun `商城 0 至 3 逐码一致且 1 是已发货`() {
        assertEquals("待发货", ProductOrderStatus.labelOf(0))
        assertEquals("已发货", ProductOrderStatus.labelOf(1))
        assertEquals("已完成", ProductOrderStatus.labelOf(2))
        assertEquals("已取消", ProductOrderStatus.labelOf(3))
        assertEquals(4, ProductOrderStatus.values().size)
        assertEquals(STATUS_UNAVAILABLE_LABEL, ProductOrderStatus.labelOf(9))
    }

    @Test
    fun `商城允许操作只由码表给出`() {
        fun order(status: Int) = ProductOrder.parse(JSONObject("""{"id":1,"orderNo":"MP1","status":$status}"""))
        assertTrue(order(0).canCancel)
        assertFalse(order(1).canCancel)
        assertFalse(order(2).canCancel)
        assertFalse(order(0).canTrace)
        assertTrue(order(1).canTrace)
        assertTrue(order(2).canTrace)
        assertFalse(order(3).canTrace)
        assertTrue(order(1).onTheWay)
        assertFalse(order(2).onTheWay)
        assertTrue(order(2).isCompleted)
        assertEquals(BusTone.WARN, order(0).statusTone)
        assertEquals(BusTone.PRIMARY, order(1).statusTone)
        assertEquals(BusTone.OK, order(2).statusTone)
        assertEquals(BusTone.DANGER, order(3).statusTone)
        // 后端给了名字就用名字
        assertEquals(
            "在路上啦",
            ProductOrder.parse(JSONObject("""{"status":1,"statusName":"在路上啦"}""")).statusLabel,
        )
    }

    // ---------- 两套码不混（ParcelTrace 双域） ----------

    @Test
    fun `同一个数字在两个域意思不同`() {
        assertEquals("已入池", FreightOrderStatus.labelOf(1))
        assertEquals("已发货", ProductOrderStatus.labelOf(1))
        assertEquals(BusTone.WARN, ProductOrderStatus.toneOf(0))
        assertEquals(BusTone.NEUTRAL, FreightOrderStatus.toneOf(0))

        val freight = trace(status = 5)
        val product = trace(status = 5, fromProduct = true)
        assertTrue("货运 5 = 已取消", freight.cancelled)
        assertFalse("商城没有 5 这个码，不能猜成已取消", product.cancelled)
        assertEquals("已取消", freight.statusLabel)
        assertEquals(STATUS_UNAVAILABLE_LABEL, product.statusLabel)

        assertEquals("已发货", trace(status = 1, fromProduct = true).statusLabel)
        assertEquals("已入池", trace(status = 1).statusLabel)
        // 商城 3 才是取消，货运 3 是已发车
        assertTrue(trace(status = 3, fromProduct = true).cancelled)
        assertFalse(trace(status = 3).cancelled)
        // tone 跟着同一张表走，不能拿商城表给货运码上色
        assertEquals(BusTone.DANGER, trace(status = 5).tone)
        assertEquals(BusTone.NEUTRAL, trace(status = 5, fromProduct = true).tone)
    }

    private fun trace(status: Int, statusName: String = "", fromProduct: Boolean = false) = ParcelTrace(
        orderNo = "YP20260928001",
        status = status,
        goodsName = "土鸡蛋 2 箱",
        nodes = emptyList(),
        legs = emptyList(),
        vehiclePlate = "",
        driverName = "",
        driverMobile = "",
        targetStation = "",
        etaMinutes = 0,
        dataSource = "REAL",
        vehicleGeo = Geo(0.0, 0.0),
        polyline = emptyList(),
        stations = emptyList(),
        statusName = statusName,
        fromProductTrace = fromProduct,
    )

    @Test
    fun `轨迹文案优先后端 statusName`() {
        assertEquals("师傅正在赶来", trace(status = 3, statusName = "师傅正在赶来").statusLabel)
        assertEquals("已发车", trace(status = 3).statusLabel)
    }

    // ---------- 任务点 / 交接 / 审核 ----------

    @Test
    fun `任务点 7 完成 8 失败 动作表含 6 经停`() {
        (0..8).forEach { code -> assertNotNullLabel(TaskItemStatus.labelOf(code)) }
        assertEquals("已完成", TaskItemStatus.labelOf(7))
        assertEquals("失败", TaskItemStatus.labelOf(8))
        assertEquals(9, TaskItemStatus.values().size)
        // 后端 ACTION_NAMES 有 7 项，Android 旧表漏了 6 经停
        assertEquals("经停", TaskItemStatus.ACTION_NAMES[6])
        assertEquals(7, TaskItemStatus.ACTION_NAMES.size)
        (1..6).forEach { assertTrue("码 $it 是正在进行", TaskItemStatus.of(it)!!.isActive) }
        assertFalse(TaskItemStatus.PENDING.isActive)
        assertFalse(TaskItemStatus.FAILED.isActive)
        assertFalse(TaskItemStatus.FAILED.isDone)
    }

    /**
     * D-169 地图标记那个字与动作名的**同一张表**契约。
     *
     * 两张表各写各的就会分叉：列表念"揽收"而地图上钉一个"收"，或者反过来有人改了名字没改字。
     * 这里不重复抄一份期望值，钉的是**关系**：字必须出现在对应动作名里，且两表只差 6 经停
     * （那一格是站点，它的标记是圆环那个形状，不是再塞一个字）。
     */
    @Test
    fun `动作字表与动作名表只差经停`() {
        assertEquals(
            TaskItemStatus.ACTION_NAMES.keys - 6,
            TaskItemStatus.ACTION_GLYPHS.keys,
        )
        TaskItemStatus.ACTION_GLYPHS.forEach { (code, glyph) ->
            val name = TaskItemStatus.ACTION_NAMES[code]
            assertTrue("码 $code 没有动作名，字 \"$glyph\" 无从核对", name != null)
            assertTrue("字 \"$glyph\" 不在动作名 \"$name\" 里", name!!.contains(glyph))
            assertEquals("一个标记只放一个字", 1, glyph.length)
        }
        assertNull("6 经停不给字", TaskItemStatus.ACTION_GLYPHS[6])
        assertNull("后端没给动作码时没有字", TaskItemStatus.ACTION_GLYPHS[-1])
    }

    @Test
    fun `任务点模型委托到同一张表`() {
        fun stop(json: String) = MissionStop.parse(JSONObject(json))
        assertEquals("派送中", stop("""{"status":6}""").statusLabel)
        assertEquals("已完成", stop("""{"status":7}""").statusLabel)
        assertTrue(stop("""{"status":7}""").isDone)
        assertFalse(stop("""{"status":8}""").isDone)
        assertTrue(stop("""{"status":8}""").isFailed)
        assertTrue(stop("""{"status":3}""").isActive)
        assertFalse(stop("""{"status":0}""").isActive)
        // 后端给了名字优先
        assertEquals("正在揽件", stop("""{"status":5,"statusName":"正在揽件"}""").statusLabel)
        // 表外码说"暂不可用"
        assertEquals(STATUS_UNAVAILABLE_LABEL, stop("""{"status":42}""").statusLabel)
        // -1 是"后端没给 status 键"的哨兵，留空让页面按未标注处理，不冒充任何状态
        val noKey = stop("{}")
        assertEquals(-1, noKey.status)
        assertEquals("", noKey.statusLabel)
    }

    private fun assertNotNullLabel(label: String) {
        assertTrue("已知码不该落到占位文案", label.isNotEmpty() && label != STATUS_UNAVAILABLE_LABEL)
    }

    @Test
    fun `交接 4 才是完成 5 与 7 不是`() {
        val expected = mapOf(
            0 to "等待交接", 1 to "前序已到达", 2 to "接收方待接", 3 to "交接中",
            4 to "交接完成", 5 to "交接超时", 6 to "已取消", 7 to "异常",
        )
        expected.forEach { (code, name) -> assertEquals("码 $code", name, HandoverStatus.labelOf(code)) }
        assertEquals(8, HandoverStatus.values().size)
        assertTrue(HandoverStatus.COMPLETED.isDone)
        assertFalse(HandoverStatus.of(5)!!.isDone)
        assertFalse(HandoverStatus.of(7)!!.isDone)
    }

    @Test
    fun `审核 2 需客户操作 4 不通过`() {
        val expected = mapOf(
            0 to "待审核", 1 to "审核通过", 2 to "需客户操作", 3 to "需人工审核", 4 to "审核不通过",
        )
        expected.forEach { (code, name) -> assertEquals("码 $code", name, ReviewStatus.labelOf(code)) }
        assertEquals(5, ReviewStatus.values().size)
        assertEquals(2, ReviewStatus.CONDITIONAL.code)
    }

    @Test
    fun `寄货单出口用得上审核码而不是数字 2`() {
        fun send(json: String) = SendOrder.parse(JSONObject(json))
        val base = """"id":1,"orderNo":"SH1","goodsName":"土鸡蛋","weightKg":12.5,"itemCount":2,
            "volumeM3":0.06,"fee":18.0"""
        assertTrue(send("""{$base,"status":3,"reviewStatus":2,"servicePointStationName":"石桥驿站"}""").needsCustomerAction)
        assertFalse(send("""{$base,"status":3,"reviewStatus":1,"servicePointStationName":"石桥驿站"}""").needsCustomerAction)
        assertFalse(send("""{$base,"status":3,"reviewStatus":2}""").needsCustomerAction)
        assertEquals("已发车", send("""{$base,"status":3}""").statusLabel)
        assertEquals(BusTone.OK, send("""{$base,"status":4}""").statusTone)
        assertTrue(send("""{$base,"status":4}""").isFinalStatus)
        assertFalse(send("""{$base,"status":3}""").isFinalStatus)
        assertTrue(send("""{$base,"status":10}""").onTheWay)
        assertFalse(send("""{$base,"status":1}""").onTheWay)
        // 后端名字优先于本地表
        assertEquals("在车上啦", send("""{$base,"status":10,"statusName":"在车上啦"}""").statusLabel)
    }
}
