package com.shanxiang.buspost.ui.driver

import com.shanxiang.buspost.data.model.MissionStop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 到近分层与播报文本的契约（L-1）。
 *
 * 三件事各钉一头，正反成对：
 * · **直线值不出数字**（审计 §32）——判档可以用直线远近，念给司机的那句不许把它当里程；
 * · **到点那句只说"可确认到达"**——`/driver/arrive` 按司机声明受理，播报不参与放行，
 *   所以文案里既不能出现"已到达"，也不能因为进了 AT_STOP 就少发一次请求；
 * · **一层只播一次 + 往外跳要过余量**——GPS 抖动不该让同一句话在一分钟里响三遍。
 */
class MissionApproachContractTest {

    private val th = ApproachThresholds()

    private fun stop(
        id: Long = 7,
        name: String = "石马河站",
        actionType: Int = 4,
        actionName: String = "派送",
        quantity: Int = 6,
    ) = MissionStop(stationId = id, name = name, actionType = actionType, actionName = actionName, quantity = quantity)

    @Test
    fun `四档边界按阈值切，档位外一律算更远那一档`() {
        assertEquals(ApproachTier.AT_STOP, MissionApproach.tierOf(60.0, th))
        assertEquals(ApproachTier.PREPARING, MissionApproach.tierOf(61.0, th))
        assertEquals(ApproachTier.PREPARING, MissionApproach.tierOf(150.0, th))
        assertEquals(ApproachTier.ADVISORY, MissionApproach.tierOf(151.0, th))
        assertEquals(ApproachTier.ADVISORY, MissionApproach.tierOf(500.0, th))
        assertEquals(ApproachTier.FAR, MissionApproach.tierOf(501.0, th))
    }

    /** 没有可用定位就是"不知道"，不是"很远"——把 -1 当距离判成 AT_STOP 会凭空播报到站 */
    @Test
    fun `无定位与非法距离都是 UNKNOWN，不被当成任何一档`() {
        for (meters in listOf(null, Double.NaN, -1.0, -0.1)) {
            assertEquals(ApproachTier.UNKNOWN, MissionApproach.tierOf(meters, th))
            assertNull(MissionApproach.announceText(stop(), ApproachTier.UNKNOWN, meters, DistanceSource.NONE))
        }
    }

    @Test
    fun `远档与未知不说话了，而不是硬凑一句`() {
        assertNull(MissionApproach.announceText(stop(), ApproachTier.FAR, 3000.0, DistanceSource.BACKEND_ROAD))
        assertNull(MissionApproach.announceText(stop(), ApproachTier.UNKNOWN, null, DistanceSource.STRAIGHT))
    }

    @Test
    fun `道路来源才报数字，后端路网与本机引擎两套说法分得开`() {
        assertEquals(
            "快到「石马河站」，还有 100 米，准备派送 6 件",
            MissionApproach.announceText(stop(), ApproachTier.PREPARING, 100.0, DistanceSource.ENGINE_ROAD),
        )
        assertTrue(
            MissionApproach.announceText(stop(), ApproachTier.ADVISORY, 400.0, DistanceSource.BACKEND_ROAD)!!
                .contains("道路还有 400 米"),
        )
    }

    /** §32 的那一半：直线只判远近，念出来必须不带米数 */
    @Test
    fun `直线口径不出口成里程`() {
        val text = MissionApproach.announceText(stop(), ApproachTier.PREPARING, 100.0, DistanceSource.STRAIGHT)!!
        assertFalse("直线米数被念进了播报：$text", text.contains("100"))
        assertEquals("快到「石马河站」，准备派送 6 件", text)
    }

    /** 后端没给动作名也没给件数时不能编一个"派送 0 件"出来 */
    @Test
    fun `缺字段就少说半句，站名与动作都缺时退到通用说法`() {
        val bare = stop(actionType = -1, actionName = "", quantity = 0, name = "")
        assertEquals("已到「任务点」附近，可确认到达", MissionApproach.announceText(bare, ApproachTier.AT_STOP, 40.0, DistanceSource.BACKEND_ROAD))
        assertEquals("「任务点」 在前方", MissionApproach.announceText(bare, ApproachTier.ADVISORY, 400.0, DistanceSource.STRAIGHT))
    }

    /** 到站是司机声明的：文案给"可以点"，不给"已经到" */
    @Test
    fun `到点那句只说可确认到达，不替司机宣布已到达`() {
        val text = MissionApproach.announceText(stop(), ApproachTier.AT_STOP, 40.0, DistanceSource.BACKEND_ROAD)!!
        assertTrue(text, text.contains("可确认到达"))
        assertFalse(text, text.contains("已到达"))
    }

    @Test
    fun `接客计人与揽收计件跟着后端动作码走，不统一说成件`() {
        assertTrue(
            MissionApproach.announceText(stop(actionType = 1, actionName = "接客", quantity = 3), ApproachTier.AT_STOP, 30.0, DistanceSource.BACKEND_ROAD)!!
                .contains("接客 3 人"),
        )
        assertTrue(
            MissionApproach.announceText(stop(actionType = 3, actionName = "派送", quantity = 6), ApproachTier.AT_STOP, 30.0, DistanceSource.BACKEND_ROAD)!!
                .contains("派送 6 件"),
        )
    }

    @Test
    fun `往里走立刻说话，同一站同一档只说一次`() {
        val t = ApproachTracker(th)
        val s = stop()
        assertNull(t.next(s, 3000.0, DistanceSource.BACKEND_ROAD))
        assertEquals("「石马河站」 道路还有 400 米，派送 6 件", t.next(s, 400.0, DistanceSource.BACKEND_ROAD))
        assertEquals("快到「石马河站」，还有 100 米，准备派送 6 件", t.next(s, 100.0, DistanceSource.ENGINE_ROAD))
        // 同一档里再进三帧（GPS 抖动），一个字都不许多说
        assertNull(t.next(s, 95.0, DistanceSource.ENGINE_ROAD))
        assertNull(t.next(s, 120.0, DistanceSource.ENGINE_ROAD))
        assertNull(t.next(s, 99.0, DistanceSource.ENGINE_ROAD))
        assertEquals("已到「石马河站」附近，派送 6 件，可确认到达", t.next(s, 30.0, DistanceSource.ENGINE_ROAD))
    }

    /** 余量是这条链路上唯一防"响三遍"的东西：60→80 还在 AT_STOP 的余量内，不许降档再重播 */
    @Test
    fun `往外跳要过余量，阈值线上抖不重新说话`() {
        val t = ApproachTracker(th)
        val s = stop()
        assertEquals("已到「石马河站」附近，派送 6 件，可确认到达", t.next(s, 50.0, DistanceSource.BACKEND_ROAD))
        assertNull(t.next(s, 80.0, DistanceSource.BACKEND_ROAD))
        // 80 米仍未越过 60+30 的外沿，所以 95 米才第一次承认离开 AT_STOP
        assertNull(t.next(s, 90.0, DistanceSource.BACKEND_ROAD))
        assertEquals("快到「石马河站」，道路还有 95 米，准备派送 6 件", t.next(s, 95.0, DistanceSource.BACKEND_ROAD))
    }

    @Test
    fun `换目标点或 reset 重新武装，同一点同一档不重复说`() {
        val t = ApproachTracker(th)
        assertNotNull(t.next(stop(id = 7), 30.0, DistanceSource.BACKEND_ROAD))
        assertNotNull(t.next(stop(id = 8, name = "双碑站"), 30.0, DistanceSource.BACKEND_ROAD))
        // 调度把目标指回 7 站（回读后的正常情形）：换站即重新武装，该说还得说
        assertNotNull(t.next(stop(id = 7), 28.0, DistanceSource.BACKEND_ROAD))
        t.reset()
        assertNotNull(t.next(stop(id = 11, name = "磁器口站"), 27.0, DistanceSource.BACKEND_ROAD))
        assertNull(t.next(stop(id = 11, name = "磁器口站"), 26.0, DistanceSource.BACKEND_ROAD))
    }
}
