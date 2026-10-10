package com.shanxiang.buspost.nav

import com.shanxiang.buspost.ServerTest
import com.shanxiang.buspost.data.model.Role
import com.shanxiang.buspost.data.store.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 壳路由与分域闸（指令 v2.0 Phase 1 验收 · V-2 · 审计 §29）。
 *
 * 判据取的是**行为**而不是目录：Phase 1 原文写"司机端不能看到用户首页"，但"看到"发生在
 * `NavHost` 的导航图里，工程没有 Robolectric ⇒ 无法用 JVM 用例渲染导航图。
 * 于是把决定"进哪个壳 / 深链能不能落地"的那两个判断从 @Composable 里抽成纯函数
 * （`nav/ShellDecision.kt`），这里把它们钉住；目录级的复核交给
 * `tools/check_shell_routing_probes.sh`（结构判据）。
 *
 * 与 [com.shanxiang.buspost.data.repo.DomainSplitTest] 的分工：那份用例管"角色由后端档案授予"
 * （`切换司机模式先过后端档案`），本用例管"授予之后路由怎么分配域"，两条不重复。
 */
class ShellRoutingContractTest : ServerTest() {

    // ---------- 冷启动落哪个壳 ----------

    /** 未登录进游客模式（USER_SHELL），首页实时公交完整开放（对标主流 App 游客体验，Task #16） */
    @Test
    fun `未登录进游客模式壳`() {
        assertEquals(Routes.USER_SHELL, startDestinationFor(loggedIn = false, role = Role.DRIVER))
        assertEquals(Routes.USER_SHELL, startDestinationFor(loggedIn = false, role = Role.USER))
    }

    @Test
    fun `已登录按本机角色落对应的壳`() {
        assertEquals(Routes.DRIVER_SHELL, startDestinationFor(loggedIn = true, role = Role.DRIVER))
        assertEquals(Routes.USER_SHELL, startDestinationFor(loggedIn = true, role = Role.USER))
        // 两个壳必须是两个不同的目的地，否则"分域"只是一句注释
        assertNotEqualsStrings(Routes.DRIVER_SHELL, Routes.USER_SHELL)
    }

    /** 退登录会把角色一起清掉（`Prefs.clearSession`），所以残留司机身份不可能活到下一个账号的冷启动 */
    @Test
    fun `退出登录后残留司机角色也回不到司机壳`() {
        Prefs.token = "tk-driver"
        Prefs.role = Role.DRIVER
        Prefs.driverId = 61L
        assertEquals(Routes.DRIVER_SHELL, startDestinationFor(Prefs.loggedIn, Prefs.role))

        Prefs.clearSession()
        assertFalse(Prefs.loggedIn)
        assertEquals(Role.USER, Prefs.role)
        assertEquals(0L, Prefs.driverId)
        assertEquals(Routes.USER_SHELL, startDestinationFor(Prefs.loggedIn, Prefs.role))
    }

    // ---------- 通知深链的分域闸 ----------

    @Test
    fun `壳路由不接受通知深链`() {
        for (role in Role.values()) {
            assertNull("进哪个壳只由角色决定，${role} 也不接受 ${Routes.USER_SHELL} 深链",
                deepLinkTarget(Routes.USER_SHELL, role))
            assertNull("一条旧通知不该把会话整体搬进另一个壳（$role）",
                deepLinkTarget(Routes.DRIVER_SHELL, role))
        }
    }

    /** DRIVER_ONLY 的成员要钉死：少一条就是漏闸，多一条就是把用户页锁死 */
    @Test
    fun `司机域路由集合就是那五条`() {
        // 5 = 旧调试导航 + 车道级导航 + 消息 + 交接 + 异常上报。
        // DRIVER_GUIDE（车道级导航）本轮入闸：它与 DRIVER_EXCEPTION 同理由 —— 目的地走
        // AppState.openGuide 意图传递，路由保持无参数字面量，深链才能被精确集合判断拦住。
        assertEquals(5, Routes.DRIVER_ONLY.size)
        assertTrue(
            Routes.DRIVER_ONLY.containsAll(
                setOf(
                    Routes.DRIVER_NAV,
                    Routes.DRIVER_GUIDE,
                    Routes.DRIVER_MESSAGES,
                    Routes.HANDOVER,
                    Routes.DRIVER_EXCEPTION,
                )
            )
        )
        // 用户域路由绝不能被误登记进司机域（否则司机壳之外的正常入口会被闸掉）
        for (userRoute in setOf(Routes.PARCEL, Routes.ORDERS, Routes.ADDRESS, Routes.FEEDBACK, Routes.PROFILE)) {
            assertFalse("$userRoute 属于用户域", Routes.DRIVER_ONLY.contains(userRoute))
        }
    }

    /**
     * 闸门是**精确集合成员**判断（`route in Routes.DRIVER_ONLY`），所以司机域路由一律不带路径参数：
     * `driverException/123` 这种串不在集合里，用户会话靠一条深链就能进司机工作台 —— 而且不会有任何报错，
     * 因为导航图那边照样能匹配上参数路由。§54 新增这一页时踩过的是"想当然加个 {legId}"，钉在这里。
     */
    @Test
    fun `司机域路由都是无参数的字面量`() {
        for (route in Routes.DRIVER_ONLY) {
            assertFalse("$route 带路径参数，分域闸的精确匹配会失效", route.contains('{'))
            assertFalse("$route 带查询串，同样绕过闸门", route.contains('?'))
        }
    }

    @Test
    fun `司机域深链在用户会话被拒`() {
        for (route in Routes.DRIVER_ONLY) {
            assertNull("用户会话不该被一条旧通知送进司机域：$route", deepLinkTarget(route, Role.USER))
        }
    }

    /** 反向守卫：闸若写成"不分角色一律拒"，司机点自己的提醒通知就永远进不了消息页 */
    @Test
    fun `司机域深链在司机会话原样放行`() {
        for (route in Routes.DRIVER_ONLY) {
            assertEquals(route, deepLinkTarget(route, Role.DRIVER))
        }
    }

    @Test
    fun `用户域深链在两个会话都放行`() {
        for (route in setOf(Routes.PARCEL, Routes.ORDERS, Routes.NOTIFICATIONS, Routes.trace("SH20260929001"))) {
            assertEquals(route, deepLinkTarget(route, Role.USER))
            assertEquals(route, deepLinkTarget(route, Role.DRIVER))
        }
    }

    /**
     * 陌生路由在这里**放行**：是不是存在不由本函数判，交给 `NavHost` 找不到 composable 时抛、
     * 调用方 `runCatching` 兜住。若改成在这里一律返回 null，旧版本通知留下的合法路由也会被静默吞掉，
     * 用户看到的就是"点了通知没反应"，比崩更难查。
     */
    @Test
    fun `陌生路由交给导航图而不是静默吞掉`() {
        assertEquals("ghostRoute/9", deepLinkTarget("ghostRoute/9", Role.USER))
        assertEquals(Routes.goodsDetail(7L), deepLinkTarget(Routes.goodsDetail(7L), Role.USER))
    }

    private fun assertNotEqualsStrings(a: String, b: String) = assertTrue("两个壳路由相同意味着分域失效：$a", a != b)
}
