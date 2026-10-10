package com.shanxiang.buspost.ui.driver

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
}
