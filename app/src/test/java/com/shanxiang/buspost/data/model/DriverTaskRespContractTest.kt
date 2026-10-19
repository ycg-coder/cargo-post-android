package com.shanxiang.buspost.data.model

import com.shanxiang.buspost.core.ui.BusTone
import com.shanxiang.buspost.ui.driver.MissionMarkers
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `GET /driver/tasks` 的报文契约（队列 #262）。
 *
 * fixture 的键名逐个抄自后端 `AppDriverTaskRespVO.java:18-55`（19 个字段）与生产者
 * `DriverAppServiceImpl#tasks:421-444`，**不是**抄客户端当年的候选名列表。
 * 旧解析读的 8 族键（`taskType`/`bizType`/`targetStation`/`cargoName`/`itemCount`/`count`/
 * `action`/`taskName`/`planTime`，外加 `goodsName` 回落 `createTime`）在这份报文里一个都不存在，
 * `goodsName` 连生产者都没有 —— 于是真接口下整块恒空，界面却照样渲染"货物  件 · "那一行缺内容。
 * 这里逐字段钉"读到的是后端给的值"，并钉"后端没给的读出来就是没给"。
 */
class DriverTaskRespContractTest {

    /** 后端这 19 个键的原样形状（LocalDateTime 走 `TimestampLocalDateTimeSerializer` = 13 位毫秒） */
    private val realVo = """
        {
          "planId": 3001,
          "taskWindowStart": 1761734400000,
          "taskWindowEnd": 1761741600000,
          "visitSequence": 2,
          "id": 88002,
          "stationId": 31,
          "stationName": "石桥驿站",
          "actionType": 4,
          "actionName": "揽收",
          "orderId": 5100,
          "orderNo": "YP20261028007",
          "estimatedArrivalTime": 1761737100000,
          "plannedDepartureTime": 1761737700000,
          "serviceDurationSeconds": 600,
          "quantity": 6,
          "segmentDurationSeconds": 900,
          "segmentDistanceKm": 8.4,
          "status": 2,
          "statusName": "已到站"
        }
    """.trimIndent()

    @Test
    fun `后端真实键名逐个读得到 不靠默认值蒙`() {
        val task = DriverTask.parse(JSONObject(realVo))
        assertEquals(3001L, task.planId)
        val stop = task.stop
        assertEquals(31L, stop.stationId)
        assertEquals("石桥驿站", stop.name)
        assertEquals(2, stop.visitSequence)
        assertEquals(4, stop.actionType)
        assertEquals("揽收", stop.actionName)
        assertEquals(5100L, stop.orderId)
        assertEquals("YP20261028007", stop.orderNo)
        assertEquals(6, stop.quantity)
        assertEquals(2, stop.status)
        assertEquals("已到站", stop.statusLabel)
        assertEquals(1761737100000L, stop.estimatedArrivalMillis)
        assertTrue(stop.hasEstimatedArrival)
        // 任务窗口是"某件事什么时候发生"，13 位毫秒必须被归一成本机时区可读串，不能原样进 UI
        assertTrue("窗口文案该是 yyyy-MM-dd HH:mm，实测：${task.windowStartText}",
            task.windowStartText.matches(Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}""")))
        assertFalse("不许把 13 位时间戳原样显示：${task.windowEndText}", task.windowEndText.all { it.isDigit() })
    }

    /**
     * `id` 是**调度明细编号**（VO:26-27 的注释原文），不是站点。
     * 旧写法 `multiNum("stationId","id")` 会把它落进 `stationId`，同站聚合当场散成"每行一座站"，
     * 路单上就会出现两个同名站各自带一个动作。这条钉住别名撤掉之后那个回落真的没了。
     */
    @Test
    fun `调度明细编号不被当成站点编号`() {
        val noStation = DriverTask.parse(
            JSONObject("""{"planId":9,"id":88002,"visitSequence":1,"actionType":1,"status":0}"""),
        )
        assertEquals("这个 VO 没给 stationId 时只能是未给哨兵，不能拿明细编号冒充", -1L, noStation.stop.stationId)
        assertEquals("", noStation.stop.name)

        val withStation = DriverTask.parse(JSONObject("""{"planId":9,"id":88002,"stationId":31}"""))
        assertEquals("给了 stationId 才读得到站", 31L, withStation.stop.stationId)
    }

    /**
     * 这个 VO 全文没有 `latitude`/`longitude`（站点坐标只在 `/driver/route` 的 stops 里）。
     * 所以任务段行喂进任务地图那个出口，一个点都画不出来 —— 这是"只当路单、不拿去画地图"的结构证明，
     * 而不是靠页面记得别用它。
     */
    @Test
    fun `任务 VO 没有坐标 任务段进不了地图点位`() {
        val stop = DriverTask.parse(JSONObject(realVo)).stop
        assertFalse("hasGeo 必须恒 false", stop.hasGeo)
        assertEquals(0.0, stop.geo.lat, 1e-9)
        assertEquals(0.0, stop.geo.lng, 1e-9)
        // 报文里真塞进坐标也不读：这条链路的坐标来源永远是 /driver/route，不是 /driver/tasks
        val forced = MissionStop.parseTaskItem(
            JSONObject("""{"stationId":31,"latitude":29.6,"longitude":106.6,"lat":29.6,"lng":106.6}"""),
        )
        assertFalse("parseTaskItem 不读坐标键", forced.hasGeo)
        with(MissionMarkers) {
            assertEquals("任务段不许被画成地图点", 0, MissionMarkers.stopMarkers(listOf(stop)).size)
        }
    }

    /** 旧模型那 8 族幻影键一个都不再产生值；`createTime` 也不再冒充任务窗口 */
    @Test
    fun `八个幻影键不再被采纳`() {
        val phantom = DriverTask.parse(
            JSONObject(
                """{"planId":7,"taskType":3,"bizType":2,"targetStation":"江北机场站","cargoName":"土豆",
                   "goodsName":"土豆","itemCount":9,"count":9,"action":"揽收","taskName":"上午揽收",
                   "planTime":1761734400000,"createTime":1761000000000}"""
            ),
        )
        assertEquals(-1, phantom.stop.actionType)
        assertEquals("", phantom.stop.actionLabel)
        assertEquals("targetStation 不是这个 VO 的键，站名只能是空", "", phantom.stop.name)
        assertEquals(-1, phantom.stop.quantity)
        assertEquals(-1, phantom.stop.status)
        assertEquals("", phantom.stop.statusLabel)
        assertEquals("", phantom.windowStartText)
        assertEquals("", phantom.windowEndText)
        with(MissionMarkers) {
            assertNull("货物件数没有生产者，不许渲染出 0 件那一行", phantom.stop.quantityText())
        }
    }

    /** 动作名/单位/状态色都走唯一那张表：6 经停是后端 VO 注释漏写的那个码，8 失败不等于完成 */
    @Test
    fun `动作名单位与状态色只有表里那一个答案`() {
        fun stopOf(json: String) = MissionStop.parseTaskItem(JSONObject(json))
        assertEquals("经停", stopOf("""{"actionType":6}""").actionLabel)
        assertEquals("出发", stopOf("""{"actionType":0}""").actionLabel)
        assertEquals("表外的动作码不编名字", "", stopOf("""{"actionType":9}""").actionLabel)
        // 后端给了 actionName 就用后端的，不拿本机表覆盖
        assertEquals("揽货", stopOf("""{"actionType":4,"actionName":"揽货"}""").actionLabel)

        with(MissionMarkers) {
            assertEquals("6 件", stopOf("""{"actionType":4,"quantity":6}""").quantityText())
            assertEquals("3 人", stopOf("""{"actionType":1,"quantity":3}""").quantityText())
            assertNull("缺 quantity 就是没给，不显示 0 项", stopOf("""{"actionType":3}""").quantityText())
        }

        val failed = stopOf("""{"status":8}""")
        assertEquals(BusTone.DANGER, TaskItemStatus.of(failed.status)?.tone)
        assertTrue(failed.isFailed)
        assertFalse("失败不是另一种完成", failed.isDone)
        val done = stopOf("""{"status":7}""")
        assertEquals(BusTone.OK, TaskItemStatus.of(done.status)?.tone)
        assertTrue(done.isDone)
        assertEquals("表外状态码要说状态暂不可用，不许回落成 0 的文案",
            "状态暂不可用", stopOf("""{"status":42}""").statusLabel)
        assertEquals("后端没给 status 键时留空，由页面按未标注处理", "", stopOf("""{}""").statusLabel)
    }

    /**
     * 段的聚合口径抄小程序 `workbench.js:221-254`：同 planId 归一段、段内按 visitSequence 升序、
     * 同站多动作（客/货同站执行）并成一条、行首取该站 visitSequence 最小的那行。
     * 后端整表按 visitSequence 升序返回、多方案是混排的（`DispatchPlanItemMapper:14-18`），
     * 所以段的先后只能按"该 planId 第一次出现的位置"，本机不重排。
     */
    @Test
    fun `多方案混排按 visitSequence 聚合成段 同站多动作并成一条`() {
        val rows = listOf(
            DriverTask.parse(JSONObject("""{"planId":10,"stationId":31,"stationName":"石桥驿站","visitSequence":1,"actionType":1,"status":7}""")),
            DriverTask.parse(JSONObject("""{"planId":20,"stationId":44,"stationName":"江北机场站","visitSequence":1,"actionType":3,"status":0}""")),
            DriverTask.parse(JSONObject("""{"planId":10,"stationId":31,"stationName":"石桥驿站","visitSequence":2,"actionType":4,"quantity":6,"status":2}""")),
            DriverTask.parse(JSONObject("""{"planId":10,"stationId":55,"stationName":"白朝村驿站","visitSequence":3,"actionType":6,"status":0}""")),
        )
        val segs = DriverTaskSegment.group(rows)
        assertEquals(2, segs.size)
        assertEquals("段按 planId 首次出现的顺序，不按本机排序", listOf(10L, 20L), segs.map { it.planId })

        val first = segs[0]
        assertEquals("同站两个动作并成一条", listOf(31L, 55L), first.stations.map { it.head.stationId })
        val at31 = first.stations[0]
        assertEquals(2, at31.actions.size)
        assertEquals("行首取 visitSequence 最小的那行", 1, at31.head.visitSequence)
        assertEquals("接客", at31.head.actionLabel)
        assertEquals("该站第二个动作仍在同一条里", 2, at31.actions[1].visitSequence)
        assertEquals(listOf(1, 2, 3), first.stations.flatMap { it.actions }.map { it.visitSequence })
        assertEquals(1, segs[1].stations.size)
        assertEquals(44L, segs[1].stations[0].head.stationId)
    }

    /** 窗口的四种形状各说各的：缺哪个就说缺哪个，不拿本机时钟或班次时刻补 */
    @Test
    fun `任务窗口四格诚实文案`() {
        fun seg(start: String, end: String) = DriverTaskSegment(1L, start, end, emptyList())
        val both = seg("2026-10-29 08:00", "2026-10-29 10:00")
        assertTrue(both.windowText, both.windowText.startsWith("任务窗口 2026-10-29 08:00 ~ "))
        assertTrue(seg("2026-10-29 08:00", "").windowText.contains("结束时刻没给"))
        assertTrue(seg("", "2026-10-29 10:00").windowText.contains("开始时刻没给"))
        assertEquals("任务窗口没给", seg("", "").windowText)
    }

    /** 同段内某一行没带窗口（plan 在 planMap 里取不到时后端就不 set）：取有值那行，全空才说没给 */
    @Test
    fun `段窗口只在整段都没有时才说没给`() {
        val segs = DriverTaskSegment.group(
            listOf(
                DriverTask.parse(JSONObject("""{"planId":5,"stationId":31,"visitSequence":1}""")),
                DriverTask.parse(JSONObject("""{"planId":5,"stationId":44,"visitSequence":2,"taskWindowStart":1761734400000}""")),
            ),
        )
        assertEquals(1, segs.size)
        assertTrue(segs[0].windowStartText.isNotEmpty())
        assertEquals("", segs[0].windowEndText)
        assertTrue(segs[0].windowText.contains("结束时刻没给"))
    }

    /** ETA 的"没有"有几种写法（缺键 / 显式 null / 0 / 负数），结论都只能是"未给"，不许变成 1970 年那一次到站 */
    @Test
    fun `预计到达缺键或 0 都不塌成时刻`() {
        listOf("""{"stationId":31}""", """{"stationId":31,"estimatedArrivalTime":null}""",
            """{"stationId":31,"estimatedArrivalTime":0}""", """{"stationId":31,"estimatedArrivalTime":-1}""",
        ).forEach { raw ->
            val stop = MissionStop.parseTaskItem(JSONObject(raw))
            assertFalse("$raw 不该被当成有到站时刻", stop.hasEstimatedArrival)
            assertEquals(0L, stop.estimatedArrivalMillis)
            with(MissionMarkers) { assertNull("$raw 的 etaText 必须是 null", stop.etaText()) }
        }
        val real = MissionStop.parseTaskItem(JSONObject("""{"estimatedArrivalTime":1761737100000}"""))
        assertTrue(real.hasEta)
        with(MissionMarkers) {
            assertTrue(real.etaText()!!.startsWith("预计 "))
        }
    }

    /**
     * §五 的那一条：VO 的 19 个键全是包装类型，"后端没给"不许塌成一个看着像值的 0。
     * 三个最容易翻车的口径各钉一遍 —— stationId 塌 0 会被同站聚合当成一座真站、
     * visitSequence 塌 0 会被排到段首、quantity 塌 0 会渲染成"0 件"。
     */
    @Test
    fun `空报文整行读成未给哨兵 页面那一侧一个值都不编`() {
        val stop = MissionStop.parseTaskItem(JSONObject("{}"))
        assertEquals(-1L, stop.stationId)
        assertEquals(-1, stop.visitSequence)
        assertEquals(-1, stop.quantity)
        assertEquals(-1, stop.actionType)
        assertEquals(-1, stop.status)
        assertEquals("", stop.statusLabel)
        assertFalse(stop.hasEstimatedArrival)
        with(MissionMarkers) {
            assertEquals("没给序号就不显示序号", "任务点", stop.markerTitle())
            assertNull(stop.quantityText())
            assertEquals("", stop.markerDetail())
        }
        // 段级同理：planId 没给也只能是哨兵，不许冒充 0 号方案
        assertEquals(-1L, DriverTask.parse(JSONObject("""{"stationId":31}""")).planId)
    }
}
