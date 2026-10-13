package com.shanxiang.buspost.ui.user

import com.shanxiang.buspost.TestHost
import com.shanxiang.buspost.data.model.BusVehicle
import com.shanxiang.buspost.data.model.Geo
import com.shanxiang.buspost.core.ui.BusTone
import com.shanxiang.buspost.data.model.NearbyResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
