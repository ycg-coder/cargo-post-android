package com.shanxiang.buspost.nav

import com.shanxiang.buspost.data.model.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 角色与后端画像的同源判定（V-2b · 审计 §70 的尾巴）。
 *
 * 和 [ShellRoutingContractTest] 的分工：那份钉"本机角色决定进哪个壳"，这份钉
 * **"本机角色凭什么改"** —— 后者原来根本没有答案：`Prefs.role` 只由登录页、"切换为司机模式"
 * 和司机壳出口写，后端档案说什么都不影响它。撤档之后人永远留在司机壳里。
 *
 * 全部判据是纯函数，没有 Robolectric 也能钉死；行为侧（真的不发司机域请求）在
 * `ui/driver/WorkbenchRoleReconcileContractTest.kt` 用 MockWebServer 计数验证。
 */
class RoleReconcileTest {

    /** 后端授予身份的唯一形状：回包里有 `driverId > 0` */
    @Test
    fun `回包里有 driverId 才算司机`() {
        assertEquals(true, serverSaysDriver(driverId = 61L, failed = false, demo = false))
    }

    /**
     * 地基用例：**这就是真后端的否定形状**。
     * `DriverAppServiceImpl.profile():167-171` 没有司机记录时 `return null` → `{"code":0,"data":null}`，
     * 而 `Api.kt:314-315` 把 null data 读成空的 `JSONObject()`，于是客户端看到的是
     * "请求成功 + demo=false + driverId=0"。旧代码在这里多要一个 `data 非空`，
     * 判据就永远为假（撤档永不生效）。把这条钉死，是为了让那个错误判据回不来。
     */
    @Test
    fun `成功回包里没有 driverId 就是明确否定`() {
        assertEquals(false, serverSaysDriver(driverId = 0L, failed = false, demo = false))
        // 负数不是"没给"，是脏值：后端只可能给正 id，非正一律按"没有档案"处理
        assertEquals(false, serverSaysDriver(driverId = -1L, failed = false, demo = false))
    }

    /** 三种"未知"：网络/业务失败、演示回落 —— 都不许被读成"这账号不是司机" */
    @Test
    fun `失败与演示都是未知而不是否定`() {
        assertNull(serverSaysDriver(driverId = 0L, failed = true, demo = false))
        assertNull(serverSaysDriver(driverId = 0L, failed = false, demo = true))
        assertNull(serverSaysDriver(driverId = 0L, failed = true, demo = true))
        // 演示回包里带着 driverId 也不算后端承认：那条数据是本地编的
        assertNull(serverSaysDriver(driverId = 61L, failed = false, demo = true))
    }

    @Test
    fun `只有明确否定才把司机角色降级`() {
        assertEquals(Role.USER, reconcileRole(Role.DRIVER, server = false))
        assertEquals(Role.DRIVER, reconcileRole(Role.DRIVER, server = true))
        assertEquals(Role.DRIVER, reconcileRole(Role.DRIVER, server = null))
    }

    /** 硬规则 1：进司机域是用户的显式动作，档案说有也不许自动把人搬进司机壳 */
    @Test
    fun `降级是单向的：永远不会把用户升成司机`() {
        assertEquals(Role.USER, reconcileRole(Role.USER, server = true))
        assertEquals(Role.USER, reconcileRole(Role.USER, server = false))
        assertEquals(Role.USER, reconcileRole(Role.USER, server = null))
    }
}
