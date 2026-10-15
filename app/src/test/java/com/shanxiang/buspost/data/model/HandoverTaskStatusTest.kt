package com.shanxiang.buspost.data.model

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 交接状态判定（缺陷 #58）。
 *
 * 坏法原来长这样：`HandoverScreen.isDone(task) = task.status.contains("已交接") || …contains("完成")`
 * —— 超时(5)/取消(6)/异常(7) 三个终态匹配不上，于是它们留在"待办"里，司机再点一次确认，
 * 后端回 `HANDOVER_STATUS_ILLEGAL`(1_005_016_003)。文案是给人看的，判断一律走后端原码。
 *
 * 码表出处：`AppDriverHandoverRespVO.java:53 private Integer status`（TransportHandoverStatusEnum 0..7）。
 */
class HandoverTaskStatusTest {

    private fun task(status: Int?, statusName: String = ""): HandoverTask = HandoverTask.parse(
        JSONObject().apply {
            put("id", 9001)
            put("orderNo", "YP2026101501")
            if (status != null) put("status", status)
            if (statusName.isNotEmpty()) put("statusName", statusName)
        },
    )

    @Test
    fun `四个终态都算关闭，其中只有交接完成算 done`() {
        listOf(4, 5, 6, 7).forEach { code ->
            val t = task(code)
            assertTrue("码 $code 必须关闭（不能再提交）", t.terminal)
        }
        assertTrue(task(4).handoverState?.isDone == true)
        assertFalse("超时不是完成", task(5).handoverState?.isDone == true)
        assertFalse("取消不是完成", task(6).handoverState?.isDone == true)
        assertFalse("异常不是完成", task(7).handoverState?.isDone == true)
    }

    @Test
    fun `未完成的四个码仍可操作`() {
        listOf(0, 1, 2, 3).forEach { code ->
            val t = task(code)
            assertFalse("码 $code 还在流程里，不许被当成终态藏起来", t.terminal)
        }
    }

    @Test
    fun `没给码与表外码都不替后端认状态`() {
        val none = task(null)
        assertEquals(-1, none.statusCode)
        assertNull(none.handoverState)
        assertFalse("没给码 ⇒ 不能说它已关闭，也不能说它完成", none.terminal)

        val outside = task(99)
        assertEquals(99, outside.statusCode)
        assertNull("表外码不猜语义", outside.handoverState)
        assertFalse(outside.terminal)
    }

    @Test
    fun `文案不参与判定`() {
        // 名字写着"已交接"而码还是 0（等待交接）⇒ 判断跟着码走，这一单仍可提交
        val lying = task(0, "已交接")
        assertTrue(lying.status.contains("已交接"))
        assertFalse(lying.terminal)
        // 反过来：码 4 而文案缺失 ⇒ 仍然算完成
        assertTrue(task(4).handoverState?.isDone == true)
    }
}
