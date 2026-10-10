package com.shanxiang.buspost.data.repo

import com.shanxiang.buspost.ServerTest
import com.shanxiang.buspost.TestHost
import com.shanxiang.buspost.awaitUntil
import com.shanxiang.buspost.data.model.Geo
import com.shanxiang.buspost.data.model.Role
import com.shanxiang.buspost.data.store.Prefs
import com.shanxiang.buspost.ui.user.MineViewModel
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用户域 / 司机域的分域契约（审计 §29 · NEXT ⑥）。
 *
 * 钉住四件事：
 * ① 司机域的 `driverId` 只能来自后端 `/driver/profile`，未绑定时**一个司机请求都不发**
 *    （发出去只会拿到空列表，被页面渲染成"今天没有任务"，把真因洗成假象）；
 * ② 引导接口（profile / shifts / earnings）按登录会员识别身份，不受守卫影响 ——
 *    否则第一次绑定永远发不出去，守卫就成了死锁；
 * ③ 换账号、后端撤销档案，本机司机身份必须跟着掉，不留残留值；
 * ④ "切换为司机模式"这个入口先过后端档案再切角色，与登录页同一条纪律。
 */
class DomainSplitTest : ServerTest() {

    @Test
    fun `未绑定司机档案时司机域请求一个都不发`() = runBlocking {
        TestHost.strictBackend()

        val legs = Repo.driverLegs()
        assertEquals(DRIVER_UNBOUND, legs.error)
        assertTrue(legs.items.isEmpty())

        assertEquals(DRIVER_UNBOUND, Repo.driverTasks().error)
        assertEquals(DRIVER_UNBOUND, Repo.routeGuide().error)
        assertEquals(DRIVER_UNBOUND, Repo.driverMessages().error)
        assertEquals(DRIVER_UNBOUND, Repo.currentLeg().error)
        assertEquals(DRIVER_UNBOUND, Repo.driverHandovers().error)
        assertEquals(DRIVER_UNBOUND, Repo.driverPosition().error)

        // 写路径同样短路：异常在"拼 driverId 参数"这一步抛出，请求根本不上网络
        assertTrue(runCatching { Repo.depart(5L) }.exceptionOrNull() is DriverUnboundException)
        assertTrue(runCatching { Repo.arrive(5L, 31L) }.exceptionOrNull() is DriverUnboundException)
        assertTrue(
            runCatching { Repo.reportLocation(5L, Geo(30.9, 106.9), 40.0) }.exceptionOrNull() is DriverUnboundException,
        )
        assertTrue(runCatching { Repo.legAction("accept", "12") }.exceptionOrNull() is DriverUnboundException)

        assertEquals("未绑定时后端不应收到任何请求", 0, server.requestCount)
    }

    @Test
    fun `绑定后司机请求带上后端授予的 driverId`() = runBlocking {
        TestHost.strictBackend()
        respondData("""{"driverId":61,"name":"王有德","plate":"渝A·8K231"}""")
        assertEquals(61L, Repo.bindDriver().value?.driverId)
        assertEquals(61L, Prefs.driverId)

        respondData("""{"list":[],"total":0}""")
        assertNull(Repo.driverLegs().error)
        assertEquals(2, server.requestCount)

        val profileReq = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertTrue(profileReq.path!!.endsWith("/driver/profile"))
        val legsReq = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertTrue("driverId 必须来自档案而不是 0", legsReq.path!!.contains("driverId=61"))
    }

    /**
     * 反例守卫：如果连 `/driver/profile`、`/driver/shifts` 也被守卫挡住，
     * 首次绑定就发不出去，司机端会永久停在"未绑定" —— 这三个接口后端按登录会员识别身份，不传 driverId。
     */
    @Test
    fun `身份引导接口不受 driverId 守卫影响`() = runBlocking {
        TestHost.strictBackend()
        respondData("""{"driverId":61,"name":"王有德"}""")
        respondData("""{"list":[],"total":0}""")
        respondData("""{"todayAmount":120}""")
        assertNull(Repo.driverProfile().error)
        assertNull(Repo.driverShifts().error)
        assertNull(Repo.driverEarnings().error)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `换账号登录不继承上一位的司机身份`() = runBlocking {
        TestHost.strictBackend()
        Prefs.driverId = 61L
        Prefs.driverName = "王有德"
        Prefs.vehiclePlate = "渝A·8K231"
        Prefs.driverShiftId = 88L

        respondData("""{"accessToken":"token-b","id":9,"mobile":"13900000002","nickname":"新账号"}""")
        Repo.login("13900000002", "pw123456", Role.USER)

        assertEquals(0L, Prefs.driverId)
        assertEquals("", Prefs.driverName)
        assertEquals("", Prefs.vehiclePlate)
        assertEquals(0L, Prefs.driverShiftId)

        // 新会话没绑定档案：司机域依旧一个请求都不发（登录只发了 1 条）
        assertEquals(DRIVER_UNBOUND, Repo.driverLegs().error)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `后端回空档案时本机司机身份跟着掉`() = runBlocking {
        TestHost.strictBackend()
        Prefs.driverId = 61L
        respondData("""{"driverId":0,"name":""}""")
        Repo.driverProfile()
        assertEquals(0L, Prefs.driverId)
        assertEquals(1, server.requestCount)

        // 身份掉了就不再拿旧 driverId 试探后端（这是审计 §29 里的越权探测面）
        assertEquals(DRIVER_UNBOUND, Repo.driverLegs().error)
        assertEquals(1, server.requestCount)
    }

    /**
     * 反例守卫（V-2b 纠正了"否定长什么样"，守卫本身没变）：**请求失败**属于未知，不是"后端说你不是司机"。
     * 若把未知当否定，一次网络抖动就会清掉绑定，把正在跑班的司机锁在司机端外面。
     *
     * ⚠ 这条用例原来把 `data:null` 也归进"未知"，本轮读后端源码后改了方向，记录在案：
     * `DriverAppServiceImpl.profile():167-171` 在 `currentDriverOrNull()==null` 时 `return null`，
     * 信封就是 `{"code":0,"data":null}`；`Api.kt:314-315` 把 null data 读成**空的 `JSONObject()`**。
     * 所以 `data:null` 正是后端**唯一**的否定形状 —— 把它当未知等于"撤档永不生效"。
     * 上面那条 `{"driverId":0,...}` 钉的是同一个否定的另一种写法，这条钉 HTTP 500，两者不再混用。
     */
    @Test
    fun `档案接口失败时保留既有司机身份`() = runBlocking {
        TestHost.strictBackend()
        Prefs.driverId = 61L
        respond("服务不可用", code = 500)
        val one = Repo.driverProfile()
        assertNotNull("HTTP 500 必须带着错误回来，不能当成空档案", one.error)
        assertEquals(61L, Prefs.driverId)

        respondData("""{"list":[],"total":0}""")
        assertNull(Repo.driverLegs().error)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `切换司机模式先过后端档案`() {        TestHost.installMainDispatcher()
        TestHost.strictBackend()
        Prefs.role = Role.USER

        // 后端说这账号不是司机：不进司机壳，把真因显示出来
        respondData("""{"driverId":0,"name":""}""")
        var entered = false
        val vm = MineViewModel()
        vm.enterDriverMode { entered = true }
        awaitUntil { vm.error != null }
        assertFalse(entered)
        assertEquals("该账号未绑定司机档案，请联系站点核对工号", vm.error)
        assertEquals(Role.USER, Prefs.role)
        assertEquals(0L, Prefs.driverId)

        // 后端给了档案：角色才切，壳才进
        respondData("""{"driverId":61,"name":"王有德","plate":"渝A·8K231"}""")
        vm.enterDriverMode { entered = true }
        awaitUntil { entered }
        assertTrue(entered)
        assertEquals(Role.DRIVER, Prefs.role)
        assertEquals(61L, Prefs.driverId)
        assertEquals("两次尝试都只问了一次档案接口", 2, server.requestCount)
    }
}
