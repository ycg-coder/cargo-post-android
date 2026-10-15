package com.shanxiang.buspost.ui.driver

import com.shanxiang.buspost.data.model.TransportLeg
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 指令 §54 的七个分类与 remark 文本构造（纯函数，无 Android 运行时）。
 *
 * 为什么单独钉这一层：后端只认 `remark` 一个自由文本字段（`AppDriverLegActionReqVO` 实测四字段），
 * "分类"能不能被调度读到，全在这里拼出的那一句 —— 页面改版、文案重写都可能把它拼歪，
 * 而 Compose 侧没有 Robolectric 可测，所以判据落在文本构造上。
 */
class DriverExceptionReportTest {

    /** §54 逐字列了七项，顺序照指令原文。多一项是发明后端不认识的话术，少一项是漏需求。 */
    @Test
    fun `七个分类与指令逐字一致`() {
        assertEquals(
            listOf("车辆故障", "道路阻断", "收件人不在", "货物异常", "无法交接", "天气影响", "其他"),
            DriverExceptionReason.values().map { it.label },
        )
    }

    @Test
    fun `没选分类拼不出 remark`() {
        assertNull(buildExceptionRemark(null, "半路抛锚"))
        assertNull(buildExceptionRemark(null, ""))
    }

    @Test
    fun `只选分类时 remark 就是分类名`() {
        assertEquals("车辆故障", buildExceptionRemark(DriverExceptionReason.VEHICLE_FAULT, ""))
        assertEquals("天气影响", buildExceptionRemark(DriverExceptionReason.WEATHER, "   "))
    }

    @Test
    fun `分类与说明拼成一句`() {
        assertEquals(
            "道路阻断：塌方中断约 3 小时",
            buildExceptionRemark(DriverExceptionReason.ROAD_BLOCKED, " 塌方中断约 3 小时 "),
        )
    }

    /** 「其他」不带具体情况，落到后台告警里就是"第 N 段异常：其他"，调度无从处置 */
    @Test
    fun `其他必须写具体情况`() {
        assertTrue(DriverExceptionReason.OTHER.requiresDetail)
        assertNull(buildExceptionRemark(DriverExceptionReason.OTHER, ""))
        assertEquals("其他：货箱一侧被压瘪", buildExceptionRemark(DriverExceptionReason.OTHER, "货箱一侧被压瘪"))
        // 其余六项自带语义，说明可以留空
        for (reason in DriverExceptionReason.values().filter { it != DriverExceptionReason.OTHER }) {
            assertEquals(reason.label, buildExceptionRemark(reason, ""))
        }
    }

    /**
     * remark 的真实落点是两个 varchar(500)（`transport_order_event.detail` 与
     * `transport_user_notification.content`），后端还会各加前缀。超限 = MySQL 1406 = 整笔回滚，
     * 司机看到"上报失败"而后端什么都没记 —— 所以上限必须在出网前就压住。
     */
    @Test
    fun `remark 永不超过上限`() {
        // ⚠ 这里必须写**字面量 200**，不能写 `EXCEPTION_REMARK_MAX`：拿常量证常量是同义反复，
        //    把上限抬到 2000 这条用例照样全绿（负控 X-4 第一次跑就是这么抓到的）。
        //    200 这个数不是随手取的：后端落点是 `transport_order_event.detail varchar(500)`
        //    与 `transport_user_notification.content varchar(500)`，而服务层会再前缀"第 N 段异常："/告警文案，
        //    越界不是截断而是 MySQL 1406 整笔回滚。
        val long = "阻".repeat(5_000)
        val remark = buildExceptionRemark(DriverExceptionReason.ROAD_BLOCKED, long)
        assertEquals(200, remark!!.length)
        assertTrue("截断必须保住分类前缀，否则调度只看到一串省略号", remark.startsWith("道路阻断"))
        assertTrue(remark.endsWith("…"))
    }

    @Test
    fun `说明的预算随分类长度收缩`() {
        assertEquals(200, exceptionDetailBudget(null))
        assertEquals(200 - "车辆故障".length - 1, exceptionDetailBudget(DriverExceptionReason.VEHICLE_FAULT))
        assertEquals(200 - "收件人不在".length - 1, exceptionDetailBudget(DriverExceptionReason.RECEIVER_ABSENT))
        assertEquals(200 - "其他".length - 1, exceptionDetailBudget(DriverExceptionReason.OTHER))
    }

    // ---- 缺陷 #43：段序是"每张单内"的序号，跨单会重号 ----

    private fun obj(vararg pairs: Pair<String, Any?>): JSONObject {
        val o = JSONObject()
        pairs.forEach { (k, v) -> o.put(k, v ?: JSONObject.NULL) }
        return o
    }

    /** 两张单的 `legSequence` 都从 1 起（后端按单算），所以两行都念"第 1 段"时司机认不出报的是哪一段 */
    @Test
    fun `跨单重号的段在选段行里必须互不相同`() {
        val a = TransportLeg.parse(obj("id" to "9001", "legSequence" to 1, "orderNo" to "TO20261015000000123", "status" to 7))
        val b = TransportLeg.parse(obj("id" to "9002", "legSequence" to 1, "orderNo" to "TO20261015000000456", "status" to 7))
        assertEquals("第 1 段", "第 ${a.seq} 段", "第 ${b.seq} 段")

        val titleA = legOptionTitle(a, actionable = true)
        val titleB = legOptionTitle(b, actionable = true)
        assertTrue("两行文案相同就是缺陷本身：$titleA", titleA != titleB)
        assertEquals("单号尾 000123 · 第 1 段 · 运输中", titleA)
        assertEquals("单号尾 000456 · 第 1 段 · 运输中", titleB)
    }

    /** 后端没给单号（或演示包）时不许悄悄退回"第 1 段"，那正是缺陷的写法 */
    @Test
    fun `缺单号就明说缺而不是省略标识`() {
        val noOrder = TransportLeg.parse(obj("id" to "9003", "legSequence" to 2, "status" to 7))
        assertEquals("", noOrder.orderNo)
        assertEquals("单号未给 · 第 2 段 · 运输中", legOptionTitle(noOrder, actionable = true))
        assertEquals("单号未给 · 第 2 段「运输中」", legReceiptText(noOrder))

        // 空白单号按缺处理，不能拼出"单号尾  · "这种半句话
        val blank = TransportLeg.parse(obj("id" to "9004", "legSequence" to 1, "orderNo" to "  ", "status" to 7))
        assertEquals("单号未给 · 第 1 段 · 运输中", legOptionTitle(blank, actionable = true))
    }

    /** 终态段的"不可再报"后缀是点击闸的说明文字，掉了就等于界面允许对已完成段再报一次 */
    @Test
    fun `不可再报的段在文案里留痕`() {
        val done = TransportLeg.parse(obj("id" to "9005", "legSequence" to 3, "orderNo" to "TO7", "status" to 11))
        assertEquals("单号尾 TO7 · 第 3 段 · 已完成", legOptionTitle(done, actionable = true))
        assertEquals("单号尾 TO7 · 第 3 段 · 已完成（不可再报）", legOptionTitle(done, actionable = false))
        assertEquals("单号尾 TO7 · 第 3 段「已完成」", legReceiptText(done))
    }
}
