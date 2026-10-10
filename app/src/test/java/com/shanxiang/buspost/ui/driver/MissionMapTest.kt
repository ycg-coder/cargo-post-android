package com.shanxiang.buspost.ui.driver

import com.shanxiang.buspost.core.ui.StopState
import com.shanxiang.buspost.data.model.Geo
import com.shanxiang.buspost.data.model.MissionStop
import com.shanxiang.buspost.data.model.RouteGuide
import com.shanxiang.buspost.ui.common.MapMarker
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 任务地图的数据装配契约（审计 §62 + P1-1）。
 *
 * 钉住三件事：
 * ① 任务点字段按**后端真实键名**读得到（visitSequence / quantity / statusName…），
 *    旧的 `Station` 键名集合覆盖不到它们，曾经让地图上的任务点"位置对、内容全空"；
 * ② 顺序只跟后端 `visitSequence`，客户端不重排（调度决定去哪里）；
 * ③ 没有可信折线就没有线，失败态绝不借用完成态的语义。
 */
class MissionMapTest {

    private val backendStopJson = """
        {"stationId":31,"stationName":"高家镇客运站","longitude":106.91,"latitude":30.94,
         "visitSequence":2,"actionType":4,"actionName":"揽收","orderId":777,"orderNo":"YP20260928001",
         "quantity":6,"status":2,"statusName":"已到站","estimatedArrivalTime":1759000000000}
    """.trimIndent()

    @Test
    fun `后端真实字段名的任务点全部读得到`() {
        val stop = MissionStop.parse(JSONObject(backendStopJson))
        assertEquals(31L, stop.stationId)
        assertEquals("高家镇客运站", stop.name)
        assertEquals(2, stop.visitSequence)
        assertEquals(4, stop.actionType)
        assertEquals("揽收", stop.actionLabel)
        assertEquals(6, stop.quantity)
        assertEquals(2, stop.status)
        assertEquals("已到站", stop.statusLabel)
        assertEquals(1759000000000L, stop.estimatedArrivalMillis)
        assertTrue(stop.hasGeo)
        assertFalse(stop.isDone)
    }

    /**
     * 反例守卫：把键名换成 Station 那套旧写法（seq / count / status 文案缺失），
     * 任务点必须读成 0 / 未标注，而不是"看起来有值"。
     * 键名漂移在这里会当场变红，而不是静默显示 0 让人以为这站没货。
     */
    @Test
    fun `旧键名喂不进任务点模型`() {
        val stop = MissionStop.parse(
            JSONObject("""{"stationId":31,"stationName":"某站","seq":5,"count":9,"latitude":30.9,"longitude":106.9}""")
        )
        assertEquals(0, stop.visitSequence)
        assertEquals(0, stop.quantity)
        assertEquals(-1, stop.status)
        assertEquals("", stop.statusLabel)
    }

    /** 顺序表错误的老毛病（§18）：8 是"失败"，绝不能显示成"已完成" */
    @Test
    fun `状态码按 TaskItemStatusEnum 取值`() {
        assertEquals("失败", MissionStop.parse(JSONObject("""{"status":8}""")).statusLabel)
        assertEquals("已完成", MissionStop.parse(JSONObject("""{"status":7}""")).statusLabel)
        assertEquals("待执行", MissionStop.parse(JSONObject("""{"status":0}""")).statusLabel)
        assertTrue(MissionStop.parse(JSONObject("""{"status":8}""")).isFailed)
        assertFalse(MissionStop.parse(JSONObject("""{"status":8}""")).isDone)
    }

    /** 后端没给 actionName / statusName 时才用本地兜底表，兜底口径与枚举一致 */
    @Test
    fun `后端文案优先，缺失才兜底`() {
        val fromServer = MissionStop.parse(JSONObject("""{"status":6,"statusName":"派送中","actionType":3,"actionName":"送货上门"}"""))
        assertEquals("派送中", fromServer.statusLabel)
        assertEquals("送货上门", fromServer.actionLabel)

        val fallback = MissionStop.parse(JSONObject("""{"status":6,"actionType":3}"""))
        assertEquals("派送中", fallback.statusLabel)
        assertEquals("派送", fallback.actionLabel)

        // actionType 缺省是"未给"，不能兜底成 0（0 在后端是"出发"）
        assertEquals("", MissionStop.parse(JSONObject("{}")).actionLabel)
    }

    /** 件数/人数的单位来自后端 actionType 口径：接客下车算人，揽收派送算件 */
    @Test
    fun `数量单位按动作区分`() {
        with(MissionMarkers) {
            assertEquals("6 件", MissionStop.parse(JSONObject("""{"actionType":4,"quantity":6}""")).quantityText())
            assertEquals("3 人", MissionStop.parse(JSONObject("""{"actionType":1,"quantity":3}""")).quantityText())
            assertNull(MissionStop.parse(JSONObject("""{"actionType":4,"quantity":0}""")).quantityText())
        }
    }

    @Test
    fun `标记顺序只跟随后端 visitSequence`() {
        // 故意打乱数组顺序：地图上的序号必须仍按后端 visitSequence，而不是数组下标
        val stops = listOf(
            MissionStop.parse(JSONObject("""{"visitSequence":3,"stationName":"末站","latitude":30.99,"longitude":106.99}""")),
            MissionStop.parse(JSONObject("""{"visitSequence":1,"stationName":"首站","latitude":30.91,"longitude":106.91}""")),
            MissionStop.parse(JSONObject("""{"visitSequence":2,"stationName":"中间站","latitude":30.95,"longitude":106.95}""")),
        )
        val markers = MissionMarkers.stopMarkers(stops)
        assertEquals(listOf("1 · 首站", "2 · 中间站", "3 · 末站"), markers.map { it.title })
    }

    /** 无坐标的任务点不进地图：画在原点比不画更容易误导 */
    @Test
    fun `没有坐标的任务点被滤掉`() {
        val stops = listOf(
            MissionStop.parse(JSONObject("""{"visitSequence":1,"stationName":"有坐标","latitude":30.6,"longitude":106.5}""")),
            MissionStop.parse(JSONObject("""{"visitSequence":2,"stationName":"缺坐标"}""")),
        )
        assertEquals(1, MissionMarkers.stopMarkers(stops).size)
    }

    @Test
    fun `标记详情带动作件数与状态`() {
        val marker = MissionMarkers.stopMarkers(
            listOf(MissionStop.parse(JSONObject(backendStopJson)))
        ).first()
        assertEquals(MapMarker.KIND_TASK_ACTIVE, marker.kind)
        assertEquals("揽收 · 6 件 · 已到站", marker.detail)
    }

    /** 失败态的语义色必须独立于完成态 */
    @Test
    fun `失败与完成不同标记态`() {
        assertEquals(
            MapMarker.KIND_TASK_FAILED,
            MissionMarkers.taskKind(MissionStop.parse(JSONObject("""{"status":8,"latitude":30.1,"longitude":106.1}"""))),
        )
        assertEquals(
            MapMarker.KIND_TASK_DONE,
            MissionMarkers.taskKind(MissionStop.parse(JSONObject("""{"status":7,"latitude":30.1,"longitude":106.1}"""))),
        )
    }

    @Test
    fun `没有可信折线就没有线`() {
        val noRoad = RouteGuide.EMPTY.copy(
            polyline = emptyList(),
            routeSourceName = "AMAP",
            routeUnavailableReason = "后端没有可用的真实道路路线",
        )
        assertTrue(MissionMarkers.paths(noRoad).isEmpty())

        // 数组里有点但来源不可信：RouteGuide 的 polyline 只在通过校验后才有值，这里模拟"被拒后为空"
        val rejected = RouteGuide.EMPTY.copy(polyline = emptyList(), routeSourceName = "")
        assertTrue(MissionMarkers.paths(rejected).isEmpty())

        val real = RouteGuide.EMPTY.copy(
            polyline = listOf(Geo(30.9, 106.9), Geo(30.92, 106.92), Geo(30.95, 106.94)),
            routeSourceName = "AMAP",
            roadDistanceMeters = 9200,
        )
        val paths = MissionMarkers.paths(real)
        assertEquals(1, paths.size)
        assertEquals(9200, paths.first().roadDistanceMeters)
        assertEquals("AMAP", paths.first().source)
    }

    /** 车辆标记说清它是"后端最后上报位置"，不冒充实时 GPS */
    @Test
    fun `车辆标记如实标注来源`() {
        val guide = RouteGuide.EMPTY.copy(vehicleGeo = Geo(30.9, 106.9), routeSourceName = "AMAP")
        val marker = MissionMarkers.vehicleMarker(guide)!!
        assertTrue(marker.title.contains("最后上报"))
        assertFalse(marker.detail.contains("实时"))
    }

    @Test
    fun `脚注不编造道路里程`() {
        val withRoad = RouteGuide.EMPTY.copy(
            stops = listOf(MissionStop.parse(JSONObject("""{"visitSequence":1,"stationName":"a","latitude":30.1,"longitude":106.1}"""))),
            roadDistanceMeters = 1200,
            routeSourceName = "OSM_GRAPHHOPPER",
        )
        val text = missionFootnote(withRoad)
        assertTrue(text.contains("任务点 1 个"))
        assertTrue(text.contains("1.2 公里"))
        assertTrue(text.contains("OSM_GRAPHHOPPER"))

        // 接口没给里程就写"接口未给"，不能拿直线距离凑一个数
        assertTrue(missionFootnote(RouteGuide.EMPTY).contains("道路里程接口未给"))
    }

    // ---------- ⑤ 任务点业务化：时间线上的序号 / 动作 / 件数 / 状态 ----------

    @Test
    fun `时间线同样只跟随后端序号，并带动作件数状态`() {
        val stops = listOf(
            MissionStop.parse(JSONObject("""{"visitSequence":2,"stationName":"高家镇","actionType":4,"quantity":6,"status":2,"statusName":"已到站"}""")),
            MissionStop.parse(JSONObject("""{"visitSequence":1,"stationName":"解放碑","actionType":3,"quantity":2,"status":7,"statusName":"已完成"}""")),
        )
        val line = MissionMarkers.timelineOf(stops, targetStationId = 0L)
        assertEquals(listOf("1 · 解放碑", "2 · 高家镇"), line.map { it.label })
        assertEquals(listOf("派送 · 2 件 · 已完成", "揽收 · 6 件 · 已到站"), line.map { it.hint })
        assertEquals(listOf(StopState.DONE, StopState.CURRENT), line.map { it.state })
    }

    /** 进行中的六档（1..6）与失败（8）都不能显示成"已完成" */
    @Test
    fun `只有后端状态 7 才是完成`() {
        for (status in listOf(0, 1, 2, 3, 4, 5, 6, 8)) {
            val stop = MissionStop.parse(JSONObject("""{"visitSequence":1,"status":$status}"""))
            assertFalse("状态 $status 不是完成", stop.isDone)
        }
        assertTrue(MissionStop.parse(JSONObject("""{"status":7}""")).isDone)
    }

    @Test
    fun `失败任务点进 FAILED 态`() {
        val stop = MissionStop.parse(JSONObject("""{"visitSequence":1,"status":8,"statusName":"失败"}"""))
        assertEquals(StopState.FAILED, MissionMarkers.stateOf(stop, targetStationId = 0L))
        // 失败优先于"导航目标"：即使这站是当前目标，也不能画成进行中
        assertEquals(StopState.FAILED, MissionMarkers.stateOf(stop.copy(stationId = 9), targetStationId = 9L))
    }

    /** 后端没给 status 时才退回"本机导航目标"，这只影响高亮，不推进业务 */
    @Test
    fun `状态缺失时导航目标才高亮`() {
        val noStatus = MissionStop.parse(JSONObject("""{"visitSequence":1,"stationId":9,"stationName":"某站"}"""))
        assertEquals(-1, noStatus.status)
        assertEquals(StopState.CURRENT, MissionMarkers.stateOf(noStatus, targetStationId = 9L))
        assertEquals(StopState.PENDING, MissionMarkers.stateOf(noStatus, targetStationId = 11L))
    }

    @Test
    fun `到站时间没给就不显示`() {
        val withEta = MissionStop.parse(JSONObject("""{"visitSequence":1,"estimatedArrivalTime":1759000000000}"""))
        val line = MissionMarkers.timelineOf(listOf(withEta))
        assertTrue((line.first().time ?: "").startsWith("预计 "))
        assertNull(MissionMarkers.timelineOf(listOf(MissionStop.parse(JSONObject("{}")))).first().time)
    }

    /**
     * 审计 §32 的文案契约：道路值优先，直线值必须自带"不是道路里程"的标注，两者都缺就明说未给。
     * 特意断言"直线数字不出现"，防止把 300 米直线冒充成里程。
     */
    @Test
    fun `距离文案优先后端道路值`() {
        val text = MissionMarkers.nextStopDistanceText(roadMeters = 1200, straightMeters = 300.0, targetName = "高家镇")
        assertTrue(text, text.contains("道路距离约 1.2 公里"))
        assertFalse("直线值不能混进道路距离文案：$text", text.contains("300"))

        val labeled = MissionMarkers.nextStopDistanceText(roadMeters = 0, straightMeters = 300.0, targetName = "高家镇")
        assertTrue(labeled, labeled.contains("直线约 300 米"))
        assertTrue(labeled, labeled.contains("不是道路里程"))

        val none = MissionMarkers.nextStopDistanceText(roadMeters = 0, straightMeters = -1.0, targetName = "高家镇")
        assertTrue(none, none.contains("接口未给"))
        assertFalse("没有数值时不能出现数字：$none", none.any { it.isDigit() })
    }
}
