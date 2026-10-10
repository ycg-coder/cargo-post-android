package com.shanxiang.buspost.ui.driver

import com.shanxiang.buspost.ServerTest
import com.shanxiang.buspost.TestHost
import com.shanxiang.buspost.awaitUntil
import com.shanxiang.buspost.data.model.Role
import com.shanxiang.buspost.data.repo.DRIVER_UNBOUND
import com.shanxiang.buspost.data.store.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 司机工作台的"角色与后端画像同源"行为（V-2b · MockWebServer 计数）。
 *
 * 三条真实风险各钉一条，判据全是**请求条数**与**Prefs 里的角色**，不是"我看代码觉得会早退"：
 * · 后端撤档（`data:null`）后，工作台必须只发 1 条 `/driver/profile`，其余四条司机域请求一条都不许出网 ——
 *   `/driver/shifts` 与 `/driver/pickups` 不带 driverId，`driverIdParam()` 那道守卫拦不住它们，只有早退拦得住；
 * · 档案接口**失败**时既不能降级角色，也不能少发请求：否则一次网络抖动就把正在跑班的司机踢出司机端；
 * · 档案说"是司机"也**不能**把用户壳升成司机壳（硬规则 1：进司机域是用户的显式动作）。
 */
class WorkbenchRoleReconcileContractTest : ServerTest() {

    @Before
    fun bindMainDispatcher() {
        TestHost.installMainDispatcher()
    }

    /** 撤档前的残留状态：本机自认为是司机，还留着上一轮授予的 driverId 与班次 */
    private fun residueDriver() {
        Prefs.token = "tk-driver"
        Prefs.role = Role.DRIVER
        Prefs.driverId = 61L
        Prefs.driverName = "王有德"
        Prefs.driverShiftId = 88L
    }

    /** 档案之外的四条司机域回包：都用"成功且空"，把判据干净地留给请求条数 */
    private fun enqueueRest() {
        respondData("[]")
        respondData("[]")
        respondData("[]")
        respondData("null")
    }

    @Test
    fun `撤档后只发档案这一条请求并把角色降回用户`() {
        TestHost.strictBackend()
        residueDriver()
        // 后端的否定形状：code=0 + data:null（DriverAppServiceImpl.profile():167-171）
        respondData("null")

        val vm = WorkbenchViewModel()
        vm.load()
        awaitUntil { vm.demoted }

        assertEquals("明确否定后不能再发任何司机域请求", 1, server.requestCount)
        assertEquals(Role.USER, Prefs.role)
        assertEquals(0L, Prefs.driverId)
        assertEquals("残留班次不能继续当成本机在跑班", 0L, Prefs.driverShiftId)
        assertEquals(DRIVER_UNBOUND, vm.error)
        assertNull(vm.profile)
        assertNull(vm.shift)
        assertFalse(vm.demo)
    }

    /**
     * 反例守卫（这条决定了上一行判据是不是"宁可错杀"）：档案接口**失败**属于未知，
     * 未知既不清绑定、也不降级角色、更不少发请求 —— 五条全都照常尝试。
     */
    @Test
    fun `档案接口失败时不降级也不少发请求`() {
        TestHost.strictBackend()
        residueDriver()
        respond("服务不可用", code = 500)
        enqueueRest()

        val vm = WorkbenchViewModel()
        vm.load()
        awaitUntil { !vm.loading }

        assertEquals("失败轮次不得少发任何一条", 5, server.requestCount)
        assertEquals("未知不等于否定：角色原样保留", Role.DRIVER, Prefs.role)
        assertEquals(61L, Prefs.driverId)
        assertFalse(vm.demoted)
        assertNotNull("档案读不到必须显示错误，不能渲染成空工作台", vm.error)
    }

    /** 硬规则 1 的行为版：档案承认他是司机，也不允许把用户壳自动搬进司机域 */
    @Test
    fun `档案授予身份时不把用户角色升级成司机`() {
        TestHost.strictBackend()
        Prefs.token = "tk-user"
        Prefs.role = Role.USER
        Prefs.driverId = 0L
        respondData("""{"driverId":61,"name":"王有德","plate":"渝A·8K231"}""")
        enqueueRest()

        val vm = WorkbenchViewModel()
        vm.load()
        awaitUntil { !vm.loading }

        assertEquals(Role.USER, Prefs.role)
        assertFalse(vm.demoted)
        assertEquals("身份仍然要收下（后续显式进司机模式要用它拼参数）", 61L, Prefs.driverId)
        assertEquals(5, server.requestCount)
        assertNull(vm.error)
    }

    /**
     * 演示回落属于未知：debug 构建里档案失败会回落到本地演示数据（`demo=true`），
     * 这时既不能清绑定、也不能降角色，更不能让页面以为"后端说我不是司机"。
     */
    @Test
    fun `演示回落不算后端否定`() {
        TestHost.demoBackend()
        residueDriver()
        repeat(5) { respond("服务不可用", code = 500) }

        val vm = WorkbenchViewModel()
        vm.load()
        awaitUntil { !vm.loading }

        assertEquals(Role.DRIVER, Prefs.role)
        assertEquals(61L, Prefs.driverId)
        assertFalse(vm.demoted)
        assertTrue("演示数据必须带上演示标记", vm.demo)
    }
}
