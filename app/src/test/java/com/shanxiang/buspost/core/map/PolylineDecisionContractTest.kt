package com.shanxiang.buspost.core.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 线路页折线三态的契约测试（F-3 ⑤）。
 *
 * 钉的是**措辞的优先顺序**，不是措辞好不好看：`BusScreen` 那两句文案在 JVM 里渲染不出来
 * （工程没有 Robolectric），所以把"哪一格允许说哪句话"抽成纯函数放在这里钉，页面侧只留接线，
 * 结构那一半由 `tools/check_safe_call_probes.sh` 的 SA-e2/SA-e3/SA-e4 钉。
 *
 * 三条要命的性质，各自一条用例：
 * · **没问过 ≠ 后端说没有**（D-042 在册的那句同族假话，本轮收口的就是它）；
 * · "后端未回传该线路折线"这一句在全树里**只有一个出口**（`Asked` + 0 点 + 无失败）；
 * · 三格文案都不给几何承诺 —— 不说"稍后再试就有"，也不拿站点连线顶替。
 */
class PolylineDecisionContractTest {

    // 页面在折线缺失处给的那句原文案（F-2 ⑰ 不重排 UI，这里只验证它仍只在"问过且回空"时用到）
    private val whenEmpty = "后端没有这条线路的真实道路折线，这里不用站点连线顶替。"

    // ---------- 该不该出网 ----------

    @Test
    fun `首屏自动选中线路后立刻去问一次折线`() {
        assertTrue(shouldAskPolyline("12", silent = false))
    }

    /** 15s 静默轮询只是刷新车辆位置：接上按需查询会把"点开线路才查一次"变成轮询风暴 */
    @Test
    fun `静默轮询不去问折线`() {
        assertFalse(shouldAskPolyline("12", silent = true))
    }

    /** `routeId` 在后端是 `@RequestParam` 必填（`AppBusController.java:48-54`）：空编号出网只会拿到 400，再把"我自己没编号"念成"后端没给轨迹" */
    @Test
    fun `没有线路编号时不出网`() {
        assertFalse(shouldAskPolyline(null, silent = false))
        assertFalse(shouldAskPolyline("", silent = false))
    }

    // ---------- 三格各自的措辞 ----------

    /** 本轮修的就是这一格：没问过之前不得念"后端未回传" */
    @Test
    fun `还没去问时说的是没问而不是后端没有`() {
        val s = polylineSubtitleOf(PolylinePhase.NotAsked, pointCount = 0, failure = null)
        assertEquals("还没去问这条线路的折线", s)
        assertFalse(s, s.contains("后端未回传"))
        assertFalse(s, s.contains("没有轨迹"))
    }

    @Test
    fun `请求在飞时明写在飞`() {
        val s = polylineSubtitleOf(PolylinePhase.Asking, pointCount = 0, failure = null)
        assertEquals("正在向后端要这条线路的折线…", s)
        assertNotEquals(polylineSubtitleOf(PolylinePhase.NotAsked, 0, null), s)
    }

    /** 唯一允许说"后端未回传"的组合：问过、后端回了 0 点、这次没失败 */
    @Test
    fun `问过且后端回空才说后端未回传`() {
        assertEquals(
            "后端未回传该线路折线",
            polylineSubtitleOf(PolylinePhase.Asked, pointCount = 0, failure = null),
        )
    }

    @Test
    fun `问过且这次失败说没问到并可重试`() {
        assertEquals(
            "这次没问到折线，可重试",
            polylineSubtitleOf(PolylinePhase.Asked, pointCount = 0, failure = "网络不太好"),
        )
    }

    /** 证据优先于相位：重问（replan）不清旧几何，画面上真有后端给过的折线时不许改口说"正在要" */
    @Test
    fun `已有两点以上几何时副标题只认几何`() {
        for (phase in PolylinePhase.values()) {
            assertEquals(phase.toString(), "真实道路折线", polylineSubtitleOf(phase, pointCount = 2, failure = "网络不太好"))
        }
    }

    // ---------- 缺失处那段说明 ----------

    @Test
    fun `reason 的没问那一格不承诺几何也不冒充后端答复`() {
        val r = polylineReasonOf(PolylinePhase.NotAsked, failure = null, whenEmpty = whenEmpty)
        assertTrue(r, r.contains("还没去问"))
        assertTrue(r, r.contains("点重试"))
        assertFalse(r, r.contains("后端未回传"))
        // 这句是新增的第三种假话：没问过却说"后端没有这条线路的折线"
        assertFalse(r, r.contains("后端没有这条线路"))
    }

    @Test
    fun `reason 的在飞那一格只说在飞`() {
        val r = polylineReasonOf(PolylinePhase.Asking, failure = null, whenEmpty = whenEmpty)
        assertTrue(r, r.contains("正在向后端要"))
        assertTrue(r, r.contains("不画线"))
        assertFalse(r, r.contains("后端未回传"))
        assertFalse(r, r.contains("后端没有这条线路"))
    }

    /** 失败原因要原样带上：只说"没问到"等于把病因丢了（F-2 ⑧ 的分类文案） */
    @Test
    fun `reason 的失败那一格带上分类原因`() {
        val r = polylineReasonOf(PolylinePhase.Asked, failure = "后端业务码 500：服务器忙", whenEmpty = whenEmpty)
        assertTrue(r, r.contains("这次没问到该线路的折线（后端业务码 500：服务器忙）"))
        assertTrue(r, r.contains("点重试"))
    }

    @Test
    fun `reason 的问过且回空那一格原样用页面文案`() {
        assertEquals(whenEmpty, polylineReasonOf(PolylinePhase.Asked, failure = null, whenEmpty = whenEmpty))
    }

    // ---------- 全局性质 ----------

    /**
     * "后端未回传"这一句在 3 相位 × 2 点数 × 2 失败 = 12 种组合里**恰好出现一次**。
     *
     * 这条是本轮真正的收口判据：上面每一条用例都只钉一格，而这一格漂移最隐蔽的方式恰恰是
     * "把默认分支写宽"（例如有人把 `else ->` 挪到前面）——只有穷举才看得见。
     */
    @Test
    fun `后端未回传那句在全部组合里只有一个出口`() {
        var hits = 0
        for (phase in PolylinePhase.values()) {
            for (points in intArrayOf(0, 2)) {
                for (failure in arrayOf<String?>(null, "网络不太好")) {
                    val s = polylineSubtitleOf(phase, points, failure)
                    if (s.contains("后端未回传")) hits++
                }
            }
        }
        assertEquals(1, hits)
    }

    /** 三格两两不同，且不等于页面那句 `whenEmpty`：把 NotAsked 写成 whenEmpty 是一次真实的回退 */
    @Test
    fun `三态副标题两两不同`() {
        val asked = polylineSubtitleOf(PolylinePhase.Asked, 0, null)
        val asking = polylineSubtitleOf(PolylinePhase.Asking, 0, null)
        val notAsked = polylineSubtitleOf(PolylinePhase.NotAsked, 0, null)
        assertNotEquals(asked, asking)
        assertNotEquals(asking, notAsked)
        assertNotEquals(asked, notAsked)
        assertNotEquals(whenEmpty, notAsked)
        assertNotEquals(whenEmpty, asking)
    }

    /** reason 同理：三格必须各不相同，否则"三态"只是变量名 */
    @Test
    fun `三态 reason 两两不同`() {
        val a = polylineReasonOf(PolylinePhase.Asked, null, whenEmpty)
        val b = polylineReasonOf(PolylinePhase.Asking, null, whenEmpty)
        val c = polylineReasonOf(PolylinePhase.NotAsked, null, whenEmpty)
        val d = polylineReasonOf(PolylinePhase.Asked, "网络不太好", whenEmpty)
        assertNotEquals(a, b)
        assertNotEquals(b, c)
        assertNotEquals(a, d)
    }
}
