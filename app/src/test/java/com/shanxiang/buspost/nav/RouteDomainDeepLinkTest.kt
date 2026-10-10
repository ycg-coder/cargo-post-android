package com.shanxiang.buspost.nav

import com.shanxiang.buspost.data.model.BusLine
import com.shanxiang.buspost.data.model.Role
import com.shanxiang.buspost.ui.user.DetailSlot
import com.shanxiang.buspost.ui.user.detailSlotOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder

/**
 * 深链**整条链**的分域复核（F-3 ⑥；指令硬规则 1，审计 §29）。
 *
 * 与 [ShellRoutingContractTest] 的分工：那份钉的是纯函数 `deepLinkTarget` 本身（喂给它的是手写的路由字面量），
 * 这里钉的是**通知/桌面小组件真实投递的那串字符**：`Routes.deepLink(...)` 拼出来 →
 * `MainActivity` 用 `Routes.routeFromDeepLink(...)` 剥回去 → 再过闸。
 * 三步里任何一步各写一份口径，落地的就不是同一条路由 —— 而"拼"与"剥"分属两个文件，
 * 单测闸门函数是看不见这种漂移的（本轮就是在这里查出 `Uri.encode` 在 JVM 侧根本不落地，见第 6 条）。
 *
 * 不起 MockWebServer：这一域的三条判断都不读网络，也不读 `Prefs`（角色由入参给），
 * 所以本类**不**继承 ServerTest —— 挂了服务器反而让人误以为这里有后端行为。
 */
class RouteDomainDeepLinkTest {

    /** 1. 拼与剥是同一个口径：任何一条路由组装再拆解，回来的必须还是它自己 */
    @Test
    fun `深链拼装与剥离往返同一条路由`() {
        val routes = setOf(
            Routes.BUS, Routes.ORDERS, Routes.PARCEL, Routes.NOTIFICATIONS, Routes.SETTINGS,
            Routes.trace("SH20260929001"), Routes.busDetail("L001"), Routes.goodsDetail(7L),
        )
        for (route in routes) {
            assertEquals("拼出去再剥回来应当是同一串：$route", route, Routes.routeFromDeepLink(Routes.deepLink(route)))
        }
    }

    /**
     * 2. 不是本 App 深链的形状一律给 **null**，不给空串。
     * 旧写法是 `data.substringAfter("buspost://app/", "")` + 调用处 `if (tail.isNotEmpty())`：
     * "对不上前缀"与"目标是空路由"被混成同一个值，闸门就少了一次判断（少一个 `isNotEmpty` 就是开一个洞）。
     */
    @Test
    fun `非本应用的深链不落到路由层`() {
        val foreign = listOf(
            null,
            "https://example.com/trace/SH20260929001",
            "buspost://ap/trace/SH1",
            "buspost://evil/trace/SH1",
            "buspost://app",
            "buspost://app/",
        )
        for (data in foreign) {
            assertNull("不该被当成一条路由：$data", Routes.routeFromDeepLink(data))
            assertNotEquals("空串会把'没有目标'伪装成'目标是空路由'：$data", "", Routes.routeFromDeepLink(data))
        }
    }

    /**
     * 3. 司机域路由：经通知拼装之后**仍然是集合里的那个字面量**，于是闸门照常拦得住。
     * 这条钉的是"组装不改变成员身份"——闸门只认精确成员，拼装多塞一个字符它就不认识了。
     */
    @Test
    fun `司机域深链经拼装仍是同一条路由`() {
        for (route in Routes.DRIVER_ONLY) {
            val assembled = Routes.routeFromDeepLink(Routes.deepLink(route))
            assertEquals(route, assembled)
            assertNull("用户会话不该被一条通知送进司机域：$route", deepLinkTarget(assembled!!, Role.USER))
            assertEquals(route, deepLinkTarget(assembled, Role.DRIVER))
        }
    }

    /**
     * 4. 反向事实：一旦司机域路由带上参数，它就落在集合之外、闸门**放行**。
     * 这条不是"希望如此"，是把 `Routes.DRIVER_EXCEPTION` 为什么不带 `{legId}` 的那个理由钉成读数 ——
     * 谁将来给司机域加带参数的路由，第 3 条会跟着红，这一条负责说明为什么红。
     */
    @Test
    fun `带参数的司机域路由就是开闸形状`() {
        val bypassing = "${Routes.DRIVER_MESSAGES}?from=widget"
        assertEquals("带查询串就绕过了精确成员判断（所以司机域路由一律无参数）",
            bypassing, deepLinkTarget(bypassing, Role.USER))
    }

    /**
     * 5. 订单号里的保留字符不会把一条路由变成两条路径段。
     * `trace/SH 001/2?x#y` 若不编码，`NavHost` 读到的就是"trace 段后面又多了两段"，
     * 落地页拿到的编号也是截断的 —— 分域闸与取数同时错。
     */
    @Test
    fun `参数里的分隔符不会多出路径段`() {
        val raw = "SH 001/2?x#y&z=1"
        val route = Routes.trace(raw)
        assertEquals("整条路由只剩 trace/ 那一个分隔符", 1, route.count { it == '/' })
        assertTrue("段内不许再有 ?：$route", !route.substringAfter("trace/").contains('?'))
        assertTrue("段内不许再有 #：$route", !route.substringAfter("trace/").contains('#'))
        assertTrue("段内不许再有 &：$route", !route.substringAfter("trace/").contains('&'))
        // 编了码还得能还原成同一个单号，否则闸门对了、查出来的单却是错的
        assertEquals(raw, URLDecoder.decode(route.substringAfter("trace/"), "UTF-8"))
    }

    /**
     * 6. 编码这一步在 JVM 用例里**是可观测的**（本轮查出的那条空转）。
     * 原来这里是 `android.net.Uri.encode`，而 `unitTests.isReturnDefaultValues = true` 把 android.jar 的桩函数
     * 全 stub 成默认值 ⇒ `Uri.encode` 返回 null，`Routes.trace(x)` 得到 `trace/null`，
     * 旧那条用例拿它跟它自己比，永远绿。现在换成纯 Kotlin，这一格才有读数。
     */
    @Test
    fun `路径段编码用未保留字符集`() {
        assertEquals("trace/SH%20001", Routes.trace("SH 001"))
        assertNotEquals("Uri.encode 在 JVM 里的桩返回值：把'没编码'伪装成'编码过了'", "trace/null", Routes.trace("SH 001"))
        // 未保留字符（字母数字与 - _ . ~）原样通过：后端给的单号/线路编号就是这个形状
        assertEquals("trace/SH20260929001_L-1.2~3", Routes.trace("SH20260929001_L-1.2~3"))
        // 非 ASCII 走 UTF-8 百分号，解码回来是原值
        assertEquals("班车", URLDecoder.decode(Routes.busDetail("班车").substringAfter("bus/"), "UTF-8"))
    }

    /**
     * 7. 详情页那一格的四态优先级（D-⑤-1 收口的第二半：把"没等到/没拿到"说成"没有"也是假话）。
     * `Loading` 与 `Failed` 都拦在 `NotInList` 前面 —— 只有取数成功且列表里确实没有，才允许说"没有这条线路"。
     */
    @Test
    fun `线路详情的四态各说各话`() {
        val lines = listOf(busLine("L001"), busLine("L002"))
        assertEquals(DetailSlot.Ready, detailSlotOf("L002", lines, selectedId = "L002", loading = false, error = null))
        // 还在取数：此时列表是空的，但"空列表"不表示"没有这条线路"
        assertEquals(DetailSlot.Loading, detailSlotOf("L009", lines, selectedId = null, loading = true, error = null))
        // 这次取数失败：病因是失败，不是"线路不存在"
        assertEquals(DetailSlot.Failed, detailSlotOf("L009", lines, selectedId = null, loading = false, error = "网络不给力"))
        // 取数成功、列表里就是这个编号没有：唯一允许说"列表里没有"的一格
        assertEquals(DetailSlot.NotInList, detailSlotOf("L009", lines, selectedId = null, loading = false, error = null))
        // 编号在列表里却没被选中 = 状态机写坏了，宁可显示错误态，不拿另一条线路顶替
        assertEquals(DetailSlot.Failed, detailSlotOf("L001", lines, selectedId = null, loading = false, error = null))
    }

    /**
     * 8. 前台服务通知点开的效果：它带的是**壳路由**，闸门对壳路由一律返回 null，
     * 于是点开等于"回到本角色自己的壳"。这条钉住它不是免闸通道 —— 谁把它改成具体页面，这里就红。
     */
    @Test
    fun `前台服务通知不构成免闸入口`() {
        val stripped = Routes.routeFromDeepLink(Routes.deepLink(Routes.DRIVER_SHELL))
        assertEquals(Routes.DRIVER_SHELL, stripped)
        assertNull("进哪个壳只由角色决定", deepLinkTarget(stripped!!, Role.DRIVER))
        assertNull(deepLinkTarget(stripped, Role.USER))
    }

    /** 9. 桌面小组件的深链：目标页在用户域，两个会话都放行（与既有「用户域深链在两个会话都放行」同口径） */
    @Test
    fun `小组件深链过闸落到实时公交页`() {
        val stripped = Routes.routeFromDeepLink(Routes.deepLink(Routes.BUS))
        assertEquals(Routes.BUS, stripped)
        assertEquals(Routes.BUS, deepLinkTarget(stripped!!, Role.USER))
        assertEquals(Routes.BUS, deepLinkTarget(stripped, Role.DRIVER))
    }

    private fun busLine(id: String) = BusLine(
        id = id,
        name = "线路$id",
        from = "山香村服务站",
        to = "白沙镇站点",
        stations = emptyList(),
        vehicles = emptyList(),
        dataSource = "REAL",
    )
}
