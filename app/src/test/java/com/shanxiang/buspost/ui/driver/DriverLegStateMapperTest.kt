package com.shanxiang.buspost.ui.driver

import com.shanxiang.buspost.data.model.LegStatus
import com.shanxiang.buspost.data.model.TransportLeg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 段状态机映射守卫（审计 §10 / §26–§28）。
 *
 * 期望值抄自后端两处源码，不是抄本工程的表：
 *  - `TransportLegStatusEnum.java:42-55` 的 `TRANSITIONS`（合法流转）
 *  - `DriverAppServiceImpl.java:1482-1500` 的 `legAction` switch（动作 → 目标状态）
 * 核心判据是 [每个动作的目标状态都必须在后端合法流转表里]：本机一旦"猜下一步"，
 * 这条测试会红 —— 旧 `RoutesScreen.nextStatus` 把 load/start/navigate 都写成 3、
 * `HandoverScreen.legStep` 用按钮序号冒充状态，都属于这一类。
 */
class DriverLegStateMapperTest {

    private fun leg(status: Int, handoverRequired: Boolean = false) = TransportLeg(
        legId = "L$status${if (handoverRequired) "H" else ""}",
        seq = 1,
        fromStation = "石桥驿站",
        toStation = "江北机场",
        plate = "渝A·12345",
        driverName = "张师傅",
        status = status,
        statusName = "",
        etaMinutes = 0,
        action = "",
        transferStation = "",
        handoverRequired = handoverRequired,
    )

    /** `TransportLegStatusEnum.TRANSITIONS` 的逐码副本（key=当前码，value=允许到达的码） */
    private val transitions = mapOf(
        0 to setOf(1, 99),
        1 to setOf(2, 0, 99),
        2 to setOf(3, 4, 99),
        3 to setOf(4, 99),
        4 to setOf(5, 99),
        5 to setOf(6, 99),
        6 to setOf(7, 99),
        7 to setOf(8, 99),
        8 to setOf(9, 10, 99),
        9 to setOf(11, 99),
        10 to setOf(11, 99),
        11 to emptySet(),
        99 to setOf(0, 1, 2, 3),
    )

    @Test
    fun `每个下一步动作的目标状态都是后端允许的流转`() {
        val codes = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 99)
        codes.forEach { code ->
            listOf(false, true).forEach { required ->
                val next = DriverLegStateMapper.nextActionOf(leg(code, required)) ?: return@forEach
                val target = next.target ?: return@forEach
                val allowed = transitions.getValue(code) + code // 同状态幂等放行（canTransit:62-65）
                assertTrue(
                    "码 $code（handoverRequired=$required）发 ${next.api} 会到 ${target.code}，" +
                        "但 TRANSITIONS 只允许 $allowed",
                    allowed.contains(target.code),
                )
            }
        }
    }

    @Test
    fun `动作取值全部落在后端 leg 斜线动作白名单里`() {
        val backendActions = setOf(
            "accept", "navigate", "arrive-origin", "load", "start", "arrive-dest",
            "handover-start", "handover-confirm", "complete", "exception",
        )
        LegDriverAction.entries.forEach { assertTrue("${it.api} 后端没有", backendActions.contains(it.api)) }
    }

    @Test
    fun `段状态到动作的逐步对照`() {
        assertEquals("accept", DriverLegStateMapper.nextActionOf(leg(1))?.api)
        assertEquals("navigate", DriverLegStateMapper.nextActionOf(leg(2))?.api)
        assertEquals("navigate", DriverLegStateMapper.nextActionOf(leg(3))?.api)
        assertEquals("arrive-origin", DriverLegStateMapper.nextActionOf(leg(4))?.api)
        assertEquals("load", DriverLegStateMapper.nextActionOf(leg(5))?.api)
        assertEquals("start", DriverLegStateMapper.nextActionOf(leg(6))?.api)
        assertEquals("arrive-dest", DriverLegStateMapper.nextActionOf(leg(7))?.api)
        assertEquals("handover-start", DriverLegStateMapper.nextActionOf(leg(8, true))?.api)
        assertEquals("handover-confirm", DriverLegStateMapper.nextActionOf(leg(9, true))?.api)
        assertEquals("complete", DriverLegStateMapper.nextActionOf(leg(10))?.api)
        assertNull(DriverLegStateMapper.nextActionOf(leg(11)))
    }

    @Test
    fun `反例 4 是前往起点不是已完成`() {
        // 旧写法 `status >= 4` 会把这里判成完成，按钮换成"重新接单"
        val navigating = leg(4)
        assertEquals("前往起点", navigating.statusLabel)
        assertTrue(!navigating.done)
        assertEquals("arrive-origin", DriverLegStateMapper.nextActionOf(navigating)?.api)
        assertEquals(2, DriverLegStateMapper.of(navigating).doneSteps)
    }

    @Test
    fun `最终段停在 8 时不发 complete 因为 8 不能直达 11`() {
        // 后端 arriveDestination 对非交接段是一次 7→8→10，段正常不会停在 8；
        // 停在 8 时唯一合法出口是再发一次 arrive-dest 转派送（canTransit(8,11) = false）
        val flow = DriverLegStateMapper.of(leg(8))
        assertEquals("arrive-dest", flow.next?.api)
        assertEquals(LegStatus.DELIVERING, flow.next?.target)
        assertTrue(transitions.getValue(8).contains(9) && !transitions.getValue(8).contains(11))
    }

    @Test
    fun `已规划与异常段没有司机可发动作`() {
        val planned = DriverLegStateMapper.of(leg(0))
        assertNull(planned.next)
        assertTrue(planned.waitingReason!!.contains("等待调度派单"))

        val broken = DriverLegStateMapper.of(leg(99))
        assertNull(broken.next)
        // 不许本机 accept 重试到 2：异常段由调度重排（docs/STATUS_MAPPING.md §4）
        assertTrue(broken.waitingReason!!.contains("不自行重试"))
        assertTrue(!broken.canReportException)
    }

    @Test
    fun `状态读不到时说清楚而不冒充已规划`() {
        val flow = DriverLegStateMapper.of(leg(-1))
        assertNull(flow.next)
        assertEquals(0, flow.doneSteps)
        assertTrue(!flow.isCompleted)
        assertTrue(flow.waitingReason!!.contains("后端没给段状态"))
    }

    @Test
    fun `中间段以交接收尾 最终段以完成收尾`() {
        val mid = DriverLegStateMapper.stepsOf(leg(7, true))
        val last = DriverLegStateMapper.stepsOf(leg(7))
        assertEquals(8, mid.size)
        assertEquals(7, last.size)
        assertEquals(LegDriverAction.HANDOVER_CONFIRM, mid.last())
        assertEquals(LegDriverAction.COMPLETE, last.last())
        // 中间段排不出 complete：后端 completeLeg 对非最后一段直接抛"请先在换乘站完成交接"
        assertTrue(last.indexOf(LegDriverAction.COMPLETE) >= 0 && mid.none { it == LegDriverAction.COMPLETE })
    }

    @Test
    fun `时间线步数按码值语义数`() {
        // handoverRequired=true：0/1→0、2/3→1、4→2、5→3、6→4、7→5、8→6、9→7、11→8
        val required = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 11) to
            listOf(0, 0, 1, 1, 2, 3, 4, 5, 6, 7, 8)
        required.let { (codes, expected) ->
            codes.zip(expected).forEach { (code, want) ->
                assertEquals("码 $code（中间段）", want, DriverLegStateMapper.of(leg(code, true)).doneSteps)
            }
        }
        // 最终段：10 派送中是"到终点已完成、差最后一步"，所以仍是 6，不是 7
        assertEquals(6, DriverLegStateMapper.of(leg(10)).doneSteps)
        assertEquals(7, DriverLegStateMapper.of(leg(11)).doneSteps)
        assertEquals(7, DriverLegStateMapper.of(leg(11)).steps.size)
    }

    @Test
    fun `交接中却没有换乘交接单时不猜该确认哪张`() {
        val flow = DriverLegStateMapper.of(leg(9, handoverRequired = false))
        assertNull(flow.next)
        assertTrue(flow.waitingReason!!.contains("没给出换乘交接单"))
    }

    @Test
    fun `终态段不能再报异常 途中段可以`() {
        assertTrue(DriverLegStateMapper.of(leg(7)).canReportException)
        assertTrue(!DriverLegStateMapper.of(leg(11)).canReportException)
        assertTrue(!DriverLegStateMapper.of(leg(-1)).canReportException)
    }
}
