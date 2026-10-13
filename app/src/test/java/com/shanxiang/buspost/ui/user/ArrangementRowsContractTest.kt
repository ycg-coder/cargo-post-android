package com.shanxiang.buspost.ui.user

import com.shanxiang.buspost.data.model.Arrangement
import com.shanxiang.buspost.data.repo.RemoteState
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用户端「我的乘车安排」那一页的契约（#54：`Repo.myArrangements()` 此前零消费者）。
 *
 * 钉四类事：
 * · **四格分开**：没取到 / 还没取到结论 / 真没有 / 有 n 条 —— 前两条都不许被念成"还没有乘车安排"；
 * · **后端给的是数字码**：`planStatus` 是 Integer（`DispatchPlanStatusEnum` 六个值），
 *   用 `multiStr` 去读就会把屏幕写成"调度方案：2"，这是把码当文案念；
 * · **没给就说明没给**：站点被删时后端那两格是 null，不许渲染成空白行、也不许兜一个站名；
 * · **不显示 ETA**：这个端点根本没有 `etaMinutes` 这个键（审计 §68），到车提醒走的是另一条通路。
 */
class ArrangementRowsContractTest {

    private fun obj(vararg pairs: Pair<String, Any?>): JSONObject {
        val o = JSONObject()
        pairs.forEach { (k, v) -> if (v == null) o.put(k, JSONObject.NULL) else o.put(k, v) }
        return o
    }

    /** 逐键照抄生产者 `AppSendController.arrangements()` 真正 set 的那一套 */
    private fun realPayload(planStatus: Any? = 2) = obj(
        "orderId" to 9001,
        "orderNo" to "YP20261012001",
        "status" to 3,
        "statusName" to "已发车",
        "boardingStationId" to 501,
        "boardingStationName" to "县城客运站",
        "alightingStationId" to 503,
        "alightingStationName" to "石桥驿站",
        "planId" to 66,
        "planStatus" to planStatus,
        "vehiclePlateNo" to "渝A·8K231",
    )

    private fun ok(list: List<Arrangement>, demo: Boolean = false) =
        RemoteState(data = list, success = true, demo = demo)

    private val failed = RemoteState<List<Arrangement>>(error = "网络不给力，请下拉重试")

    /** 还没取到结论：既没成功也没失败 */
    private val notAsked = RemoteState<List<Arrangement>>(loading = true)

    @Test
    fun `后端真给的那套键必须逐行读出来`() {
        val a = Arrangement.parse(realPayload())
        val rows = arrangementRows(a).toMap()
        assertEquals("YP20261012001", rows["订单号"])
        assertEquals("已发车", rows["订单状态"])
        assertEquals("县城客运站", rows["上车站"])
        assertEquals("石桥驿站", rows["下车站"])
        assertEquals("渝A·8K231", rows["承运车辆"])
        assertEquals("执行中", rows["调度方案"])
        assertFalse("任何一行都不该落在「没给」上：$rows", rows.values.any { it == NOT_GIVEN })
    }

    @Test
    fun `方案状态是数字码不许被念成文案`() {
        // 旧写法 multiStr("planStatus") 会把 2 念成字符串"2"，屏幕上就是"调度方案：2"
        val rows = arrangementRows(Arrangement.parse(realPayload(2))).toMap()
        assertEquals("执行中", rows.getValue("调度方案"))
        assertFalse("读出来的是码本身而不是它的名字", rows.getValue("调度方案") == "2")
    }

    @Test
    fun `六个方案状态码逐条对上后端枚举`() {
        assertEquals("待审核", planStatusText(0))
        assertEquals("已下发", planStatusText(1))
        assertEquals("执行中", planStatusText(2))
        assertEquals("已完成", planStatusText(3))
        assertEquals("已作废", planStatusText(4))
        assertEquals("异常", planStatusText(5))
    }

    @Test
    fun `未知码带出原值_null 不许塌成待审核`() {
        assertTrue("未识别的码必须把原值带出来：${planStatusText(9)}", planStatusText(9).contains("9"))
        assertTrue(planStatusText(9).contains("未识别"))
        assertFalse("null 不许被当成 0（「待审核」）", planStatusText(null) == planStatusText(0))
        assertEquals("方案状态未给", planStatusText(null))
        // 报文里整个键缺席同样必须是"未给"，而不是 0
        val a = Arrangement.parse(realPayload(planStatus = null))
        assertNull(a.planStatusCode)
        assertEquals("方案状态未给", arrangementRows(a).toMap()["调度方案"])
    }

    @Test
    fun `站点与车牌没给时说明没给而不是留白`() {
        // 后端 stationNameMap.get(...) 在站点被删时给 null；vehiclePlateNo 只有派了车才有值
        val a = Arrangement.parse(
            obj("orderNo" to "YP002", "statusName" to "已分配", "planStatus" to 1),
        )
        val rows = arrangementRows(a)
        val map = rows.toMap()
        assertEquals(NOT_GIVEN, map["上车站"])
        assertEquals(NOT_GIVEN, map["下车站"])
        assertEquals(NOT_GIVEN, map["承运车辆"])
        assertTrue("每行都得有可读的字：$rows", rows.all { it.second.isNotBlank() })
        assertEquals("已下发", map["调度方案"])
    }

    @Test
    fun `这一页不许出现任何分钟数`() {
        // 审计 §68：这个端点的十一个键里没有 etaMinutes；到车提醒走 /send/page 的 carrierEtaMinutes（且须标"直线估算"）
        val rows = arrangementRows(Arrangement.parse(realPayload()))
        assertTrue("标签里出现了 ETA 字样：$rows", rows.none { it.first.contains("分钟") || it.first.contains("还有") })
        val withEta = Arrangement(orderNo = "X", shiftCode = "", plate = "", targetStation = "", etaMinutes = 8,
            dataSource = "", statusLabel = "已发车", planStatusCode = 1)
        val rows2 = arrangementRows(withEta)
        assertTrue("演示面自己造的 etaMinutes=8 不得出现在这一页：$rows2", rows2.none { it.second.contains("8 分钟") })
    }

    @Test
    fun `一条安排固定六行`() {
        assertEquals(
            listOf("订单号", "订单状态", "上车站", "下车站", "承运车辆", "调度方案"),
            arrangementRows(Arrangement.parse(realPayload())).map { it.first },
        )
    }

    @Test
    fun `没取到时绝不念成还没有乘车安排`() {
        val p = arrangementsPage(failed, loading = false)
        assertEquals(ArrangementsKind.Failed, p.kind)
        assertTrue(p.title, p.title.contains("没取到"))
        assertTrue("后端原话要带给用户：${p.body}", p.body.contains("网络不给力，请下拉重试"))
        assertTrue("要给下一步：${p.body}", p.body.contains("重新取"))
        assertFalse("失败栏不许出现「还没有」", p.title.contains("还没有") || p.body.contains("还没有"))
        assertTrue("失败时不许带着条目", p.items.isEmpty())
    }

    @Test
    fun `还没取到结论时不落进空态`() {
        val p = arrangementsPage(notAsked, loading = false)
        assertEquals("未知不能被念成「后端说没有」", ArrangementsKind.Loading, p.kind)
        assertEquals(0, p.items.size)
        // 在飞那一格同样是 Loading，两格都不许出现 Empty/Shown
        assertEquals(ArrangementsKind.Loading, arrangementsPage(notAsked, loading = true).kind)
    }

    @Test
    fun `取成功且为空才允许报还没有`() {
        val p = arrangementsPage(ok(emptyList()), loading = false)
        assertEquals(ArrangementsKind.Empty, p.kind)
        assertTrue(p.title, p.title.contains("还没有乘车安排"))
        assertFalse("服务边界那句话只许写在服务协议正文里（判据 PL-g 数出处），这一页不许再抄一份：${p.body}",
            p.body.contains("不提供网上预约乘车"))
    }

    @Test
    fun `有安排时报条数并全量交出`() {
        val list = listOf(
            Arrangement.parse(realPayload(1)),
            Arrangement.parse(realPayload(2)),
            Arrangement.parse(realPayload(3)),
        )
        val p = arrangementsPage(ok(list), loading = false)
        assertEquals(ArrangementsKind.Shown, p.kind)
        assertTrue("标题要报条数：${p.title}", p.title.contains("3 条"))
        assertEquals(3, p.items.size)
    }

    @Test
    fun `演示安排与真实安排两种说法`() {
        val list = listOf(Arrangement.parse(realPayload(2)))
        val demo = arrangementsPage(ok(list, demo = true), loading = false)
        val real = arrangementsPage(ok(list), loading = false)
        assertTrue("演示那一格必须带着演示标记", demo.demo)
        assertFalse(real.demo)
        assertEquals(demo.kind, real.kind)
    }

    @Test
    fun `四格两两不同名`() {
        val titles = listOf(
            arrangementsPage(failed, false),
            arrangementsPage(notAsked, false),
            arrangementsPage(ok(emptyList()), false),
            arrangementsPage(ok(listOf(Arrangement.parse(realPayload()))), false),
        )
        assertEquals(
            "四种状态必须四个 kind",
            4,
            titles.map { it.kind }.distinct().size,
        )
    }
}
