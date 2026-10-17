package com.shanxiang.buspost.ui.user

import com.shanxiang.buspost.TestHost
import com.shanxiang.buspost.data.model.BusLine
import com.shanxiang.buspost.data.model.BusVehicle
import com.shanxiang.buspost.data.model.Geo
import com.shanxiang.buspost.core.ui.BusTone
import com.shanxiang.buspost.data.model.NearbyResult
import com.shanxiang.buspost.data.model.Station
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 「附近公交」两屏状态机的契约（真机反馈：首页没有附近公交卡片 / 实时公交每次都是"网络不好"）。
 *
 * 两处病根同一个形状：**把"还没拿到"读成"失败了"**。
 * - `HomeViewModel.busState()` 旧写法第一行就 `nearby ?: return "loading"`，而 `Repo.kt:837`
 *   失败时回的是 `One(null, error=…)` ⇒ 有 error 必然没 nearby ⇒ 那张「网络不太好 / 重新加载」
 *   卡是死代码，用户只看到永远转圈的骨架。
 * - `BusViewModel.state()` 旧写法 `nearby ?: return if (loading) "loading" else "error"`，
 *   而并发的 `loadMyShippingOrders()`（`BusScreen.kt:224`）走 `work(reset = false)`，
 *   基类 finally 把 `loading` 抢先写成 false（`ScreenViewModel.kt:42`）⇒ 主请求还在飞就被读成"失败"。
 *
 * 钉三件事：没有失败原因不得报 error；有失败原因不得压在加载态里；没有位置时"没有附近可言"优先
 * （否则首页说"暂无线路"、实时公交页说 80 条，两屏互相打脸 —— §51 在册那条）。
 *
 * ViewModel 是 final（生产形状，不为测试放开），所以字段用反射赋值 ——
 * 与 `TestSupport.installPrefs` / `resetActionGate` 同一手法；找不到 setter 直接 error()，
 * 不许静默跳过（静默跳过的用例等于没有用例）。
 */
class BusStateContractTest {

    /** `HomeViewModel` 构造时读 `Prefs.currentVillage`，JVM 里没有 Android 运行时 ⇒ 换内存版 SharedPreferences */
    @Before
    fun setUpPrefs() {
        TestHost.installPrefs()
    }

    private fun setProp(target: Any, name: String, value: Any?) {
        var c: Class<*>? = target.javaClass
        while (c != null) {
            c.declaredMethods.firstOrNull { it.name == name && it.parameterCount == 1 }?.let {
                it.isAccessible = true
                it.invoke(target, value)
                return
            }
            c = c.superclass
        }
        error("找不到 $name：生产代码形状已变，这条用例要重锚而不是静默跳过")
    }

    private fun bus(nearby: NearbyResult?, loading: Boolean, error: String?): String {
        val vm = BusViewModel()
        setProp(vm, "setNearby", nearby)
        setProp(vm, "setLoading", loading)
        setProp(vm, "setError", error)
        return vm.state()
    }

    private fun home(geo: Geo?, nearby: NearbyResult?, loading: Boolean, error: String?): String {
        val vm = HomeViewModel()
        setProp(vm, "setGeo", geo)
        setProp(vm, "setNearby", nearby)
        setProp(vm, "setLoading", loading)
        setProp(vm, "setError", error)
        return vm.busState()
    }

    private val none = NearbyResult(emptyList(), emptyList(), emptyList(), true)

    private val some = NearbyResult(
        stations = emptyList(), lines = emptyList(),
        vehicles = listOf(
            BusVehicle(
                id = "V1", plate = "渝A12345", geo = Geo(29.55, 106.50),
                speedKmh = 24, dataSource = "REAL", nextStation = "村口",
                etaMinutes = 3, lineName = "山村线", shiftCode = "S1",
            ),
        ),
        realTransit = true,
    )

    private val geo = Geo(29.55, 106.50)

    @Test
    fun `实时公交页 没有失败原因时不得报 error`() {
        // 并发补拉把 loading 抢先写成 false 的那一刻：既没数据也没原因 ⇒ 只能算"还在加载"
        assertEquals("loading", bus(nearby = null, loading = false, error = null))
        assertEquals("loading", bus(nearby = null, loading = true, error = null))
        assertEquals("error", bus(nearby = null, loading = false, error = "网络连接失败"))
    }

    @Test
    fun `首页 有失败原因时不得压在骨架屏里`() {
        assertEquals(
            "失败必须报 error，否则那张「网络不太好 / 重新加载」卡永远不可达",
            "error", home(geo = geo, nearby = null, loading = false, error = "网络连接失败"),
        )
        assertEquals("loading", home(geo = geo, nearby = null, loading = true, error = null))
    }

    @Test
    fun `首页 没有位置时 noloc 优先于失败与加载`() {
        // 没有位置就没有"附近"：这时后端给空报文是"没算过"，不是"这里没线路"
        assertEquals("noloc", home(geo = null, nearby = null, loading = false, error = "网络连接失败"))
        assertEquals("noloc", home(geo = null, nearby = some, loading = false, error = null))
    }

    @Test
    fun `有车的报文照常落 data 不被新分支误伤`() {
        // 反向对照：修复只该改变"没数据"那几支，不许把有数据也吞成 loading/error
        assertEquals("data", home(geo = geo, nearby = some, loading = false, error = null))
        assertEquals("data", bus(nearby = some, loading = false, error = null))
    }

    @Test
    fun `空报文仍走 empty 而不是 error`() {
        assertEquals("empty", home(geo = geo, nearby = none, loading = false, error = null))
        assertEquals("empty", bus(nearby = none, loading = false, error = null))
    }

    @Test
    fun `失败那一格必须把原因带上 不许换成笼统话`() {
        // 2026-10-17 真机：首页念「网络不太好，稍后再试」，而同一时刻对同一端点发同样的请求是
        // code:0 + 368500 字节真数据 ⇒ 原因被这句话吃掉了，网络 / 解析 / 鉴权三种病长得一模一样。
        val t = busFailureText("ApiException: HTTP 500 服务器开小差了")
        assertTrue("实际句子：$t", t.contains("HTTP 500 服务器开小差了"))
        assertTrue("不许再出现那句笼统话：$t", !t.contains("网络不太好"))
        // 真机 2026-10-17 那条原文（104 字）。第一版按"取前 90 字"剪，把尾巴 `after 8000ms` 剪丢了
        // —— 而那半句才是"这是超时、不是断网"的唯一依据 ⇒ 两头都必须留住。
        val real = "failed to connect to api.qwer2333.com/1.15.29.107 (port 443) from /10.17.200.82 (port 47808) after 8000ms"
        val long = busFailureText(real)
        assertTrue("头（连到哪里）要留：$long", long.contains("failed to connect to api.qwer2333.com"))
        assertTrue("尾（等了多久）要留：$long", long.contains("after 8000ms"))
        assertTrue("副标题要有上限，实际长度=${long.length}", long.length < 130)
        assertTrue("截完仍要留出口：$long", long.endsWith("再试"))
    }

    @Test
    fun `原因缺失时换成一句实话 且不许留空白的「原因：」`() {
        val blank = busFailureText("   ")
        assertTrue("空原因必须换成一句实话，实际：$blank", blank.contains("都没给原因"))
        assertTrue("不许出现「原因：」后面一片空白：$blank", !blank.contains("原因：。"))
        assertTrue("null 与空串同形", busFailureText(null).contains("都没给原因"))
    }

    @Test
    fun `经停站点只数这一条线 缺站点时不许拿附近并集顶替`() {
        // 2026-10-17 用户报："站点有错误 —— 站点是这个路线的站点，不是整体的站点数量"。
        // 旧写法 `line?.stations?.takeIf { it.isNotEmpty() } ?: vm.nearby?.stations` 在那条线没给站点时
        // 退回附近所有站点的并集（15km 半径实测 1238 个），标题却仍写「经停站点 · 共 N 站」。
        val stops = listOf(
            Station(id = 1L, name = "华一路", geo = geo),
            Station(id = 2L, name = "北区路", geo = geo),
            Station(id = 3L, name = "鹅岭公园", geo = geo),
        )
        val line = BusLine(
            id = "R829", name = "829路", from = "海棠溪", to = "鹅岭",
            stations = stops, vehicles = emptyList(), dataSource = "REAL_TRANSIT",
        )
        val shown = viaStationsOf(line)
        assertTrue("有站点就照它自己数：$shown", shown is ViaStations.Shown && shown.stations.size == 3)
        assertEquals("标题要点名是哪条线", "829路 共 3 站", viaStationsTitle(shown))

        val bare = viaStationsTitle(viaStationsOf(line.copy(stations = emptyList())))
        assertTrue("这条线没给站点只许说没给：$bare", bare.contains("没给经停站点"))

        val none = viaStationsTitle(viaStationsOf(null))
        assertTrue("没选线路时这个问题还不成立：$none", none.contains("还没选线路"))
    }

    @Test
    fun `附近线路卡片的小字只放三样 缺的就说缺`() {
        // 用户要"每条路线做成卡片、只保留简单信息"：三样 = 起→止、停几站、现实公交还是项目线路。
        val full = BusLine(
            id = "R347", name = "347路(老厂--菜园坝火车站)", from = "老厂", to = "菜园坝火车站",
            stations = List(19) { Station(id = it.toLong(), name = "S$it", geo = geo) },
            vehicles = emptyList(), dataSource = "REAL_TRANSIT",
        )
        assertEquals("起终点 · 站数 · 来源，一样不多", "老厂 → 菜园坝火车站 · 19 站 · 现实公交", lineBriefSubtitle(full))

        val bare = full.copy(from = "", to = "  ", stations = emptyList(), dataSource = "")
        val t = lineBriefSubtitle(bare)
        assertTrue("缺起终点要明说：$t", t.contains("起终点没给"))
        assertTrue("缺站点数不许写 0 站：$t", t.contains("站点数没给") && !t.contains("0 站"))
        assertTrue("来源不含 REAL 就是项目线路：$t", t.contains("项目线路"))
    }

    @Test
    fun `卡片点不点得进详情由编号决定 没编号不许跳成没有这条线路`() {
        val noId = BusLine(
            id = "", name = "347路(老厂--菜园坝火车站)", from = "老厂", to = "菜园坝火车站",
            stations = emptyList(), vehicles = emptyList(), dataSource = "REAL_TRANSIT",
        )
        val reason = lineCardNoDetailReason(noId)
        assertNotNull("没编号必须给出点不进去的原因", reason)
        assertTrue("原因要说的是没给编号，不是「线路不存在」：$reason", reason!!.contains("没给这条线的编号"))
        assertFalse("没编号不许把锅甩成后端说没有这条线：$reason", reason.contains("没有这条线路"))

        assertNull("有 routeId 就打得开：详情页/地图/经停全按它取数",
            lineCardNoDetailReason(noId.copy(id = "R347")))
    }

    @Test
    fun `定位提示三态各说各话 被拒不许说成没定到`() {
        // §51 在册那条：旧写法把"没授权"与"授权了但这次没定到位"折叠成同一句话，
        // 于是已经允许过的用户被教"去系统设置里允许定位"。三态各有各的下一步动作。
        val denied = locationHintFor(granted = false, fix = null)
        assertTrue("未授权要说未授权：$denied", denied.contains("未允许定位"))
        val cold = locationHintFor(granted = true, fix = null)
        assertTrue("已授权未定到要说已授权：$cold", cold.contains("已允许定位"))
        assertNotEquals(denied, cold)
        assertEquals("已按你的位置排序", locationHintFor(granted = true, fix = geo))
    }

    @Test
    fun `页头位置口径 混档不许只说实时位置`() {
        // 全实时才许顶"实时位置"绿标；混档必须把两档都说出来（卡片级早就有 sourceText，
        // 页头以前是整批 OR，一辆实时就整页写成"实时位置"）
        val (all, allTone) = positionSourceTag(realCount = 3, otherCount = 0, demo = false)
        assertEquals("实时位置", all)
        assertEquals(BusTone.OK, allTone)

        val (mixed, mixedTone) = positionSourceTag(realCount = 1, otherCount = 4, demo = false)
        org.junit.Assert.assertFalse("混档不许只写实时位置", mixed == "实时位置")
        assertTrue("混档要说清两档：$mixed", mixed.contains("1 辆实时") && mixed.contains("推算"))
        assertEquals(BusTone.WARN, mixedTone)

        assertEquals("班次时刻（无实时位置）", positionSourceTag(0, 4, false).first)
        assertEquals("演示线路", positionSourceTag(0, 2, true).first)
        // 一辆车都没显示时不许留个"班次时刻"暗示有时刻表
        assertTrue(positionSourceTag(0, 0, false).first.contains("暂无"))
    }
}
