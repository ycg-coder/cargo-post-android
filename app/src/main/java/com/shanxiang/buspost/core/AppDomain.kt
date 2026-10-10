package com.shanxiang.buspost.core

import com.shanxiang.buspost.data.model.Role

/**
 * **在看哪个业务域（壳）**——指令 v2.1 §三 / F-3 ③ 要求把这件事和"账号是什么角色"拆开。
 *
 * 拆开之前两件事共用 `Prefs.role`，于是司机壳点"用户版"必须改写账号角色（`AppState.setRole(Role.USER)`），
 * 否则后台提醒还在按司机域轮询 `/driver/messages`（审计 §29/§45）。用改角色来治串域是**洗掉事实**：
 * 账号明明还是司机，本机却记成了用户——撤档与否再也分不清，冷启动落哪个壳也跟着变。
 * 现在切壳只写这一层，账号角色只由后端档案说。
 *
 * 视图是**进程级**的，不落盘：新进程没人显式切过，初值就跟着账号角色走（[AppDomain.of]），
 * 与 `startDestinationFor` 同一口径选壳，不会出现"看着司机壳、后台按用户域轮询"。
 */
enum class AppDomain {
    USER, DRIVER;

    companion object {
        /** 还没人显式切壳时的默认视图：司机账号开司机域，其余一律用户域。 */
        fun of(accountRole: Role): AppDomain = if (accountRole == Role.DRIVER) DRIVER else USER
    }
}

/**
 * 切壳的许可：**司机视图只对确实是司机的账号开放**（硬规则 1 的界面版）。
 * 回用户域永远允许，所以只卡"进司机域"这一个方向。
 */
internal fun domainFor(requested: AppDomain, accountRole: Role): AppDomain =
    if (requested == AppDomain.DRIVER && accountRole != Role.DRIVER) AppDomain.USER else requested

/**
 * 账号角色变了之后视图该落在哪：**只跟着离开司机域，绝不跟着进入**。
 *
 * 后端档案授予身份（`bindDriver` 回 `driverId > 0`）不等于用户此刻想看司机端，
 * 自动搬进司机壳就是把"进司机域"从显式动作降级成副作用；反过来，撤档必须立刻把人带出司机壳，
 * 否则正在报位置的服务还在替一个已经没有档案的账号跑司机链路。
 */
internal fun domainAfterRoleChange(newRole: Role, current: AppDomain): AppDomain =
    if (newRole == Role.DRIVER) current else AppDomain.USER
