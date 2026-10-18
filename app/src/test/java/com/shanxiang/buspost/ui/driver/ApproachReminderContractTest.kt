package com.shanxiang.buspost.ui.driver

import com.shanxiang.buspost.data.model.Geo
import com.shanxiang.buspost.data.model.MissionStop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 到车**持续**提醒的契约（D-170，用户口径⑤）。
 *
 * 钉的是四件事，每一件都是一句"屏上会说错的话"的反面：
 * ① 要不要提醒只有一个判点（近 + 后端没说办完），失败态不提醒；
 * ② 第一次看到这一站**不出声** —— 那一句归 [ApproachTracker]，两本同时开口就是同一秒两句话；
 * ③ 重复的节奏：60 秒一遍、最多三遍（**测试里写的是字面量**，改常数必须连同这里一起改，
 *    否则"持续提醒"这件事就变成没人重量过的一行配置）；
 * ④ 那一行念的每个字都来自后端字段，本机不补动作、不补件数。
 */
class ApproachReminderContractTest {

    private fun stop(
        id: Long = 11L,
        name: String = "海棠溪",
        actionType: Int = 3,
        actionName: String = "派送",
        quantity: Int = 3,
        status: Int = 0,
    ) = MissionStop(
        stationId = id,
        name = name,
        geo = Geo(30.9, 106.9),
        actionType = actionType,
        actionName = actionName,
        quantity = quantity,
        status = status,
    )

    private fun ready(stop: MissionStop, meters: Double) =
        ApproachFrame.Ready(stop, meters, DistanceSource.BACKEND_ROAD)

    @Test
    fun `近处且没办完才提醒，四档各一态`() {
        assertNotNull("60 米内要提醒", ApproachReminder.stopOf(ready(stop(), 40.0)))
        assertNotNull("150 米内要提醒", ApproachReminder.stopOf(ready(stop(), 120.0)))
        assertNull("500 米那一档不提醒（那是 ApproachTracker 的『快到了』）", ApproachReminder.stopOf(ready(stop(), 400.0)))
        assertNull("没有可用距离不提醒", ApproachReminder.stopOf(ready(stop(), -1.0)))
        assertNull("静默帧不提醒", ApproachReminder.stopOf(ApproachFrame.Silent("后端没点名")))
        assertNull("null 帧不提醒", ApproachReminder.stopOf(null))
    }

    /** 完成与失败都是后端的结论；本机不许自己判"这站办完了"，也不许对着失败站喊"去办业务" */
    @Test
    fun `后端说办完或失败都不再提醒`() {
        assertNull(
            "已完成（status=7）必须下线",
            ApproachReminder.stopOf(ready(stop(status = 7), 20.0)),
        )
        assertNull(
            "失败（status=8）不是『去办业务』",
            ApproachReminder.stopOf(ready(stop(status = 8), 20.0)),
        )
        // 1..6 都还是进行中：这一格必须继续挂着，否则司机在"行驶中"那一拍就没了提示
        assertNotNull("已到站（status=2）还没办完，继续提醒", ApproachReminder.stopOf(ready(stop(status = 2), 20.0)))
    }

    @Test
    fun `第一次看到这一站不出声`() {
        val tracker = ReminderTracker()
        assertNull(
            "武装那一拍不响：『快到了』已经由 ApproachTracker 说过",
            tracker.next(stop(), 1_000L),
        )
    }

    @Test
    fun `六十秒一遍最多三遍然后闭嘴`() {
        val tracker = ReminderTracker()
        val s = stop()
        val step = 60_000L      // 与 ApproachReminder.REPEAT_INTERVAL_MS 同值，故意写死：改常数要同轮改这里
        assertNull(tracker.next(s, 0L))                       // 武装
        repeat(3) { i ->
            val at = step * (i + 1)
            val text = tracker.next(s, at)
            assertNotNull("第 ${i + 1} 遍该响（到 $at 毫秒）", text)
            assertTrue("响的是这一站：$text", text!!.contains("海棠溪"))
        }
        assertNull("响满三遍之后不再响，靠常驻那一行说话", tracker.next(s, step * 4))
        assertNull("再久也不响：上限是三遍，不是『每 60 秒无限重复』", tracker.next(s, step * 60))
    }

    @Test
    fun `没到间隔不响`() {
        val tracker = ReminderTracker()
        val s = stop()
        assertNull(tracker.next(s, 0L))
        assertNull("30 秒就再响一遍就是轰炸", tracker.next(s, 30_000L))
        assertNotNull(tracker.next(s, 60_000L))
    }

    @Test
    fun `换站重新武装并重新数三遍`() {
        val tracker = ReminderTracker()
        val a = stop(id = 11L, name = "海棠溪")
        val b = stop(id = 22L, name = "南山大道", actionName = "揽收", quantity = 6)
        assertNull(tracker.next(a, 0L))
        assertNotNull(tracker.next(a, 60_000L))
        assertNotNull(tracker.next(a, 120_000L))
        assertNotNull(tracker.next(a, 180_000L))
        assertNull(tracker.next(a, 240_000L))            // a 已经响满
        assertNull("换到 b 的第一拍只记时刻", tracker.next(b, 250_000L))
        val again = tracker.next(b, 310_000L)
        assertNotNull("b 有自己的三遍", again)
        assertTrue("响的是新站：$again", again!!.contains("南山大道"))
        assertTrue("响的是新动作：$again", again.contains("揽收"))
    }

    @Test
    fun `那一行只念后端给过的字段`() {
        assertEquals("海棠溪 · 派送 · 3 件", ApproachReminder.cardText(stop()))
        // 后端没给动作名：走 ACTION_NAMES 那张表（4 = 揽收），不自己编一个"取货"。
        // 量词按 `MissionStop.quantityText` 那一份表：1/2 计"人"，3/4 计"件" —— 揽收是件，不是人。
        assertEquals(
            "邮电大学 · 揽收 · 2 件",
            ApproachReminder.cardText(stop(name = "邮电大学", actionType = 4, actionName = "", quantity = 2)),
        )
        // 件数没给（0 / -1）就不出现数量那一截，不写"0 件"
        assertEquals(
            "老厂 · 出发",
            ApproachReminder.cardText(stop(name = "老厂", actionType = 0, actionName = "", quantity = 0)),
        )
        // 站名没给：说"任务点#id"，不冒充一个站名
        assertEquals(
            "任务点#77 · 派送 · 3 件",
            ApproachReminder.cardText(stop(id = 77L, name = "")),
        )
        // 动作码表外且没给动作名：动作那一截整块不出现，但件数仍然念 ——
        // 量词只有 `MissionMarkers.quantityText()` 这一张表（表外码走它自己的 else 分支"项"），
        // 提醒这一行不许再造第二套兜底，否则同一站地图上那枚钉和屏上那一行会各说一种量词。
        assertEquals(
            "某站 · 3 项",
            ApproachReminder.cardText(stop(name = "某站", actionType = 99, actionName = "")),
        )
        // 站名与动作都没有：仍念得出"任务点#id · 3 项"，不会交回空串让页面留一格空白
        assertEquals("任务点#5 · 3 项", ApproachReminder.cardText(stop(id = 5L, name = "", actionType = 99, actionName = "")))
    }

    @Test
    fun `提醒与播报读的是同一个距离口径`() {
        // 后端口径：`ApproachFeed.of(guide, null, null)` 拿 nextStopName + nextStopRoadDistanceMeters
        val guide = com.shanxiang.buspost.data.model.RouteGuide.EMPTY.copy(
            stops = listOf(stop()),
            nextStopName = "海棠溪",
            nextStopRoadDistanceMeters = 80,
        )
        assertNotNull("后端配得上就提醒", ApproachReminder.stopOf(ApproachFeed.of(guide, null, null)))
        // 后端没点名就不提醒：宁可不说，也不拿本机挑的点去配那段里程（串站是这一族最坏的错法）
        val empty = com.shanxiang.buspost.data.model.RouteGuide.EMPTY.copy(stops = listOf(stop()))
        assertNull("后端没点名就不提醒", ApproachReminder.stopOf(ApproachFeed.of(empty, null, null)))
    }
}
