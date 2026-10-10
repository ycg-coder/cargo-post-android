package com.shanxiang.buspost.nav

import com.shanxiang.buspost.data.model.Role

/**
 * 壳路由的两个决策（指令 v2.0 Phase 1 验收 / 审计 §29、§70 的 V-2）。
 *
 * 单独成文件的原因不是"整洁"，是**可测**：原来这两段判断写在 `BusPostRoot` 的 @Composable 里，
 * 工程没有 Robolectric ⇒ "司机不能看到用户首页"这条验收永远只能靠肉眼看导航图。
 * 纯函数化之后它能被 JVM 用例钉住，UI 侧一行没动。
 *
 * 两条决策都**只读 `Prefs`**，不读 `AppState`：冷启动时 `AppState` 是内存态，而它记的"当前在看哪个壳"
 * （`AppState.activeDomain`，F-3 ③）是**界面层的事实**——拿它决定起点就等于让"上一段会话最后停在哪个 tab"
 * 越过账号角色去选壳，多引一个源就会有两个答案。
 */

/**
 * 冷启动的起点：**未登录永远进登录页**，即使本机还残留着上一位用户的 `role=DRIVER`。
 * 原来这里就是 `when { !loggedIn -> LOGIN; role == DRIVER -> DRIVER_SHELL; else -> USER_SHELL }`，
 * 抽出来是为了把"残留角色不得越过登录态"这条钉成用例（清会话见 `Prefs.clearSession`）。
 */
internal fun startDestinationFor(loggedIn: Boolean, role: Role): String = when {
    !loggedIn -> Routes.LOGIN
    role == Role.DRIVER -> Routes.DRIVER_SHELL
    else -> Routes.USER_SHELL
}

/**
 * 通知深链能不能落地，返回 `null` 表示"留在当前页"。三道判断各有各的来处，**不要合并成一条**：
 * · 两个壳路由不接受外部投递 —— 进哪个壳只由角色决定，否则一条旧通知就能把用户会话整体搬进司机壳；
 * · [Routes.DRIVER_ONLY] 在用户会话里拒绝 —— 深链串来自 Intent，可能是上一个角色时期点剩的那条；
 * · 陌生路由**这里放行**：它是不是存在不由本函数判，交给 `NavHost` 抛异常、调用方 `runCatching` 兜住
 *   （旧版本通知留下的路由串不该让用户看到崩溃，也不该在这里被静默吞成"成功跳转"）。
 */
internal fun deepLinkTarget(route: String, role: Role): String? = when {
    route == Routes.USER_SHELL || route == Routes.DRIVER_SHELL -> null
    role != Role.DRIVER && route in Routes.DRIVER_ONLY -> null
    else -> route
}

/**
 * 后端档案对这个账号的说法（V-2b）。**三态，不是布尔**：
 * · `true` —— 回包里有 `driverId > 0`，是司机；
 * · `false` —— 后端**明确**说这账号没有司机档案；
 * · `null` —— **不知道**（请求失败、演示回落）。
 *
 * "否定"到底长什么样，来自当场读码而不是推测：`DriverAppServiceImpl.profile():167-171` 在
 * `currentDriverOrNull()==null` 时 `return null`，信封是 `{"code":0,"data":null}`；
 * `Api.kt:314-315` 把 null data 读成**空的 `JSONObject()`**。所以"这账号不是司机"在客户端看到的
 * 形状就是"一个成功的回包 + 里面没有 driverId" —— 再加一条 `data 非空` 的前置条件，
 * 就等于把后端唯一的否定形状排除在判据之外（撤档永不生效，V-2 记的那条残留角色的病根）。
 *
 * 反方向的守卫原样保留：`failed || demo` 先返回 `null`，网络抖动、业务失败、演示回落都**不可能**产出
 * `false`。**未知不等于否定** —— 把一次读不到当成"这人都不是司机了"，会把正在跑班的司机锁在司机端外面。
 */
internal fun serverSaysDriver(driverId: Long, failed: Boolean, demo: Boolean): Boolean? = when {
    failed || demo -> null
    driverId > 0L -> true
    else -> false
}

/**
 * 冷启动/进壳之后角色该不该降级：**只有后端明确否定才把 DRIVER 降成 USER**。
 *
 * 为什么需要这一步：`Prefs.role` 是本机残留，而"是不是司机"的权威在后端画像（`AppDriverProfileRespVO.driverId`）。
 * 站点撤档之后本机不会自己知道，于是人落进司机壳 —— 现状是诚实的 `DRIVER_UNBOUND` 错误态，但人在错的壳里。
 * 反向不允许多做：**这条只降级、不升级**，用户壳永远不可能因为"档案说有"就被自动搬进司机壳（硬规则 1：
 * 进司机域是用户的显式动作，见 `MineScreen.enterDriverMode` 必须先 `bindDriver` 成功）。
 */
internal fun reconcileRole(stored: Role, server: Boolean?): Role =
    if (stored == Role.DRIVER && server == false) Role.USER else stored
