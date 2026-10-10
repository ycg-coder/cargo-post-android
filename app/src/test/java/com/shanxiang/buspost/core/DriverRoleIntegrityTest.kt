package com.shanxiang.buspost.core

import com.shanxiang.buspost.TestHost
import com.shanxiang.buspost.data.model.Role
import com.shanxiang.buspost.data.store.Prefs
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * 指令 F-3 ③ / v2.1 §三："**任何情况下：accountRole 不允许因为 UI 页面切换而修改**"。
 *
 * 这一本钉的是拆开的两层各自该听谁：
 * · 账号角色（`Prefs.role`）只由会话建立与后端结论改写，切壳那一下碰不得（旧写法点"用户版"就把司机写成用户，
 *   撤档与否从此分不清，冷启动落点也跟着变——审计 §29/§45 用改角色治串域，代价是洗掉了账号事实）；
 * · 在看哪个域（`AppState.activeDomain`）是界面状态，可以随便切，但**司机视图只对司机账号开放**；
 * · 后台提醒按这一层分域（`AlertEngine`），所以"停掉司机域轮询"不再需要以改写角色为前提。
 *
 * 纯函数（[AppDomain.of] / [domainFor] / [domainAfterRoleChange]）+ `AppState` 的写入纪律一起钉：
 * 工程没有 Robolectric，导航图渲染不出来，能钉的是"状态机本身不会走歪"，结构（司机壳里那一行不再写角色）
 * 由 `tools/check_role_reconcile_probes.sh` 的 RR-e2 与负控制守。
 */
class DriverRoleIntegrityTest {

    @Before
    fun bindPrefs() {
        TestHost.installPrefs()
        // 每个用例自己铺前提：AppState 是进程级对象，activeDomain 会跨用例活着
        AppState.setActiveDomain(AppDomain.USER)
    }

    /** 视图回到"没人显式切过"的可控起点，再用账号角色铺出用例需要的前提 */
    private fun givenAccount(role: Role) {
        Prefs.role = role
        AppState.setActiveDomain(AppDomain.of(role))
    }

    @Test
    fun `切回用户版只改视图不动账号角色`() {
        givenAccount(Role.DRIVER)
        assertEquals(AppDomain.DRIVER, AppState.activeDomain.value)

        AppState.setActiveDomain(AppDomain.USER)

        assertEquals("UI 切页不得改写账号角色", Role.DRIVER, Prefs.role)
        assertEquals(AppDomain.USER, AppState.activeDomain.value)
    }

    @Test
    fun `非司机账号切不出司机视图`() {
        Prefs.role = Role.USER

        AppState.setActiveDomain(AppDomain.DRIVER)

        assertEquals(Role.USER, Prefs.role)
        assertEquals("硬规则 1 的界面版：司机视图要有后端授予的身份才给", AppDomain.USER, AppState.activeDomain.value)
    }

    @Test
    fun `撤档降级时视图离开司机域`() {
        givenAccount(Role.DRIVER)
        AppState.setActiveDomain(AppDomain.DRIVER)

        AppState.setAccountRole(Role.USER)

        assertEquals(Role.USER, Prefs.role)
        assertEquals("角色是后端结论，视图不能还停在司机域接着轮询 /driver/messages", AppDomain.USER, AppState.activeDomain.value)
    }

    @Test
    fun `后端授予身份不会自动把视图搬进司机域`() {
        Prefs.role = Role.USER
        AppState.setActiveDomain(AppDomain.USER)

        AppState.setAccountRole(Role.DRIVER)

        assertEquals(Role.DRIVER, Prefs.role)
        assertEquals("进司机域必须是用户的显式动作，不能是角色变更的副作用", AppDomain.USER, AppState.activeDomain.value)
    }

    @Test
    fun `撤档之后切不回司机视图，直到身份重新被授予`() {
        givenAccount(Role.DRIVER)
        AppState.setAccountRole(Role.USER)
        AppState.setActiveDomain(AppDomain.DRIVER)
        assertEquals(AppDomain.USER, AppState.activeDomain.value)

        // 站点重新归档：bindDriver 授予之后，显式进司机端才拿得到司机视图
        AppState.setAccountRole(Role.DRIVER)
        AppState.setActiveDomain(AppDomain.DRIVER)
        assertEquals(AppDomain.DRIVER, AppState.activeDomain.value)
    }

    @Test
    fun `两层各自的真值表`() {
        assertEquals(AppDomain.DRIVER, AppDomain.of(Role.DRIVER))
        assertEquals(AppDomain.USER, AppDomain.of(Role.USER))
        assertEquals(domainFor(AppDomain.DRIVER, Role.DRIVER), AppDomain.DRIVER)
        assertEquals(domainFor(AppDomain.DRIVER, Role.USER), AppDomain.USER)
        assertEquals(domainFor(AppDomain.USER, Role.DRIVER), AppDomain.USER)
        // 角色变更只带着视图**离开**司机域，从不带着它进去
        assertEquals(domainAfterRoleChange(Role.USER, AppDomain.DRIVER), AppDomain.USER)
        assertEquals(domainAfterRoleChange(Role.USER, AppDomain.USER), AppDomain.USER)
        assertEquals(domainAfterRoleChange(Role.DRIVER, AppDomain.USER), AppDomain.USER)
        assertEquals(domainAfterRoleChange(Role.DRIVER, AppDomain.DRIVER), AppDomain.DRIVER)
    }
}
