package com.shanxiang.buspost.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.shanxiang.buspost.BuildConfig
import com.shanxiang.buspost.core.AppDomain
import com.shanxiang.buspost.core.AppState
import com.shanxiang.buspost.core.net.attempt
import com.shanxiang.buspost.core.ui.BusMotion
import com.shanxiang.buspost.core.ui.SplashOverlay
import com.shanxiang.buspost.data.model.Role
import com.shanxiang.buspost.data.repo.Repo
import com.shanxiang.buspost.data.store.Prefs
import kotlinx.coroutines.async
import com.shanxiang.buspost.nav.Routes
import com.shanxiang.buspost.nav.deepLinkTarget
import com.shanxiang.buspost.nav.startDestinationFor
import com.shanxiang.buspost.ui.common.LocalNavController
import com.shanxiang.buspost.ui.driver.DriverNavScreen
import com.shanxiang.buspost.ui.driver.DriverGuideScreen
import com.shanxiang.buspost.ui.driver.DriverShell
import com.shanxiang.buspost.ui.driver.DriverExceptionScreen
import com.shanxiang.buspost.ui.driver.DriverMessagesScreen
import com.shanxiang.buspost.ui.driver.HandoverScreen
import com.shanxiang.buspost.ui.login.LoginScreen
import com.shanxiang.buspost.ui.user.AddressEditScreen
import com.shanxiang.buspost.ui.user.AddressScreen
import com.shanxiang.buspost.ui.user.ArrangementsScreen
import com.shanxiang.buspost.ui.user.ParcelTicketScreen
import com.shanxiang.buspost.ui.user.WalletScreen
import com.shanxiang.buspost.ui.user.BusDetailScreen
import com.shanxiang.buspost.ui.user.BusScreen
import com.shanxiang.buspost.core.about.PolicyKind
import com.shanxiang.buspost.ui.user.OssLicensesScreen
import com.shanxiang.buspost.ui.user.PolicyScreen
import com.shanxiang.buspost.ui.user.FeedbackScreen
import com.shanxiang.buspost.ui.user.GoodsDetailScreen
import com.shanxiang.buspost.ui.user.GoodsTraceScreen
import com.shanxiang.buspost.ui.user.NotificationScreen
import com.shanxiang.buspost.ui.user.OrderDetailScreen
import com.shanxiang.buspost.ui.user.OrdersScreen
import com.shanxiang.buspost.ui.user.ParcelScreen
import com.shanxiang.buspost.ui.user.ProfileScreen
import com.shanxiang.buspost.ui.user.SendScreen
import com.shanxiang.buspost.ui.user.SettingsScreen
import com.shanxiang.buspost.ui.user.TraceScreen
import com.shanxiang.buspost.ui.user.UserShell

/**
 * 根导航：会话决定起点（未登录 → 登录页；已登录 → 对应角色的 Shell），
 * 401 与通知深链都在这里收口，页面只管调用不关心彼此。
 */
@Composable
fun BusPostRoot() {
    val nav = rememberNavController()
    val kicked by AppState.kicked.collectAsStateWithLifecycle()
    val pending by AppState.pendingRoute.collectAsStateWithLifecycle()

    // ---------- 启动闪屏（品牌固定画布）：每个进程生命周期只播一次 ----------
    var splashVisible by remember { mutableStateOf(!splashShownOnce) }
    var splashProgress by remember { mutableStateOf(0) }
    var splashReady by remember { mutableStateOf(false) }
    var splashStatus by remember { mutableStateOf("正在连接服务站…") }

    LaunchedEffect(kicked) {
        if (kicked > 0) {
            nav.navigate(Routes.LOGIN) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(pending) {
        val route = pending ?: return@LaunchedEffect
        AppState.pendingRoute.value = null
        if (!Prefs.loggedIn) return@LaunchedEffect
        // 深链来自 Intent，可能是旧版本通知留下的未知路由：跳不动就留在当前页，不能崩
        // attempt 而非 runCatching：这一段在 LaunchedEffect 里，取消要穿出而不是被当成"这一条深链无效"
        deepLinkTarget(route, Prefs.role)?.let { attempt { nav.navigate(it) } }
    }

    val start = startDestinationFor(Prefs.loggedIn, Prefs.role)

    // 阶段①②：会话恢复完成 → 33%；阶段③：首屏数据任一返回 → 100% 就绪
    LaunchedEffect(Unit) {
        if (!splashVisible) return@LaunchedEffect
        splashShownOnce = true
        splashProgress = 33
        splashStatus = if (Prefs.loggedIn) "正在准备您的班次…" else "正在连接服务站…"
        // 与小程序一致：首屏数据预取并行发起，任一完成即推进阶段（失败也算完成，不让闪屏卡住）
        // attempt 只把「取消」原样抛出：离开组合就该停下，不该继续点亮 100%；其余失败收成 Result，
        // 并留一行日志——预取失败是常态（冷启动无网），但客户端缺陷不能跟着一起被静默。
        val a = async { attempt { Repo.notices() } }
        val b = async { attempt { Repo.nearbyBuses(null) } }
        listOf(a, b).forEach { it.await().onFailure { android.util.Log.w("BUS_SPLASH", it) } }
        splashProgress = 100
        splashReady = true
        splashStatus = "已就绪，轻触进入"
    }

    CompositionLocalProvider(LocalNavController provides nav) {
    Box(Modifier.fillMaxSize()) {
    NavHost(
        navController = nav,
        startDestination = start,
        // 转场（2026-10-10 丝滑化）：位移从 1/16 抬到 1/8，时长微调到 240/180ms，
        // 用项目统一缓动曲线（BusMotion.easing = Material 3 Emphasized）代替默认线性。
        // 1/16 在真机（小米平板 6）上几乎看不见位移，像"闪切"而非"滑入"；1/8 保留层级感又不喧宾夺主。
        enterTransition = { fadeIn(tween(240, easing = BusMotion.easing)) + slideInHorizontally(tween(240, easing = BusMotion.easing)) { it / 8 } },
        exitTransition = { fadeOut(tween(180, easing = BusMotion.easing)) },
        popEnterTransition = { fadeIn(tween(220, easing = BusMotion.easing)) },
        popExitTransition = { fadeOut(tween(180, easing = BusMotion.easing)) + slideOutHorizontally(tween(180, easing = BusMotion.easing)) { it / 8 } },
    ) {
        composable(Routes.LOGIN) {
            // 登录页必须走同一个吃 ime 的壳：它是全工程输入最密的一屏，而此前只有它没套
            // BusRouteBody —— 键盘弹起时"登录"按钮和《服务协议》勾选行整块不可见（真机实测）。
            com.shanxiang.buspost.ui.common.BusRouteBody {
                LoginScreen(onEntered = { role ->
                    // 进哪个壳由账号角色决定；视图域必须跟着显式落一次，否则同一进程里"退出→再登录成司机"
                    // 还留着上一段"我已切回用户版"的覆写，就成了看着司机壳、后台按用户域轮询（F-3 ③）。
                    AppState.setActiveDomain(AppDomain.of(role))
                    nav.navigate(if (role == Role.DRIVER) Routes.DRIVER_SHELL else Routes.USER_SHELL) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                })
            }
        }
        composable(Routes.USER_SHELL) { UserShell() }
        composable(Routes.DRIVER_SHELL) { DriverShell() }

        composable(Routes.SEND) { com.shanxiang.buspost.ui.common.BusRouteBody { SendScreen() } }
        composable(Routes.BUS) { com.shanxiang.buspost.ui.common.BusRouteBody { BusScreen() } }
        composable(Routes.ORDERS) { com.shanxiang.buspost.ui.common.BusRouteBody { OrdersScreen() } }
        composable(Routes.PARCEL) { com.shanxiang.buspost.ui.common.BusRouteBody { ParcelScreen(standalone = true) } }
        composable(Routes.NOTIFICATIONS) { com.shanxiang.buspost.ui.common.BusRouteBody { NotificationScreen() } }
        composable(Routes.ARRANGEMENTS) { com.shanxiang.buspost.ui.common.BusRouteBody { ArrangementsScreen() } }
        // #56 钱包：只读余额与流水。写口（充值/余额支付）这一版不开，理由写在页面上而不是藏起来
        composable(Routes.WALLET) { com.shanxiang.buspost.ui.common.BusRouteBody { WalletScreen() } }
        composable(Routes.PROFILE) { com.shanxiang.buspost.ui.common.BusRouteBody { ProfileScreen() } }
        composable(Routes.ADDRESS) { com.shanxiang.buspost.ui.common.BusRouteBody { AddressScreen() } }
        composable(Routes.ADDRESS_SELECT) { com.shanxiang.buspost.ui.common.BusRouteBody { AddressScreen(select = true) } }
        composable(Routes.FEEDBACK) { com.shanxiang.buspost.ui.common.BusRouteBody { FeedbackScreen() } }
        composable(Routes.SETTINGS) { com.shanxiang.buspost.ui.common.BusRouteBody { SettingsScreen() } }
        // 指令 v2.1 ⑭：OSM/ODbL 署名的最小入口，用户域，不加角色闸门
        composable(Routes.OSS_LICENSES) { com.shanxiang.buspost.ui.common.BusRouteBody { OssLicensesScreen() } }
        // 登录页的《服务协议》勾选行要能点开正文 ⇒ 这两条路由不设角色/登录闸门（内容全在 AppPolicies，页面只渲染）
        composable(Routes.PRIVACY) { com.shanxiang.buspost.ui.common.BusRouteBody { PolicyScreen(PolicyKind.PRIVACY) } }
        composable(Routes.TERMS) { com.shanxiang.buspost.ui.common.BusRouteBody { PolicyScreen(PolicyKind.TERMS) } }
        composable(Routes.HANDOVER) { com.shanxiang.buspost.ui.common.BusRouteBody { HandoverScreen() } }
        composable(Routes.DRIVER_MESSAGES) { com.shanxiang.buspost.ui.common.BusRouteBody { DriverMessagesScreen() } }
        // 旧 DriverNavScreen 转 DEBUG ONLY（DECISION_LOG D-003 / 任务 #51）：正式包连路由都没有，
        // 司机页面上指向它的入口同轮加闸；正式导航走工作台的 DriverMissionMap + DriverSdkNavCard。
        // 用户硬规则"不要直接删除文件" —— 这里是降级入口，文件与本函数都保留。
        if (BuildConfig.DEBUG) composable(Routes.DRIVER_NAV) { DriverNavScreen() }
        // §54 司机异常上报：路由不带参数（分域闸按精确成员判断，见 Routes.DRIVER_EXCEPTION 的说明）
        composable(Routes.DRIVER_EXCEPTION) { com.shanxiang.buspost.ui.common.BusRouteBody { DriverExceptionScreen() } }
        // 车道级导航（**正式路由，与上面的 DEBUG-only DRIVER_NAV 不是一件事**）：
        // 这一页承载真实高德 `AMapNaviView`（转向大图 / 车道放大图 / 路口放大图 / 实时路况），
        // 目的地不走路由参数，由 AppState.openGuide(...) 传入（理由见 Routes.DRIVER_GUIDE 注释）。
        // 设备不支持（无 arm64/v7a 原生库 → unsatisfiedLinkError）时页内自降级成不可用说明，
        // 不会崩、也不会假装在导航 —— 这是"宁可暂时不能导航，不允许假导航"的落点。
        composable(Routes.DRIVER_GUIDE) { DriverGuideScreen() }

        composable(
            Routes.GOODS_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { com.shanxiang.buspost.ui.common.BusRouteBody { GoodsDetailScreen(it.arguments?.getLong("id") ?: 0L) } }

        composable(
            Routes.GOODS_TRACE,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { com.shanxiang.buspost.ui.common.BusRouteBody { GoodsTraceScreen(it.arguments?.getLong("id") ?: 0L) } }

        composable(
            Routes.ORDER_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { com.shanxiang.buspost.ui.common.BusRouteBody { OrderDetailScreen(it.arguments?.getLong("id") ?: 0L) } }

        composable(
            Routes.ADDRESS_EDIT,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { com.shanxiang.buspost.ui.common.BusRouteBody { AddressEditScreen(it.arguments?.getLong("id") ?: 0L) } }

        composable(
            Routes.TRACE,
            arguments = listOf(navArgument("no") { type = NavType.StringType }),
        ) { com.shanxiang.buspost.ui.common.BusRouteBody { TraceScreen(it.arguments?.getString("no").orEmpty()) } }

        // #55 查件票面：`/send/topology` 那条零消费者通路的唯一读方。用户域，不加角色闸门；
        // 页内自带"回追踪页"的出口（票面取不到时不能把人关在门外）。
        composable(
            Routes.PARCEL_TICKET,
            arguments = listOf(navArgument("no") { type = NavType.StringType }),
        ) { com.shanxiang.buspost.ui.common.BusRouteBody { ParcelTicketScreen(it.arguments?.getString("no").orEmpty()) } }

        composable(
            Routes.BUS_DETAIL,
            arguments = listOf(navArgument("routeId") { type = NavType.StringType }),
        ) { com.shanxiang.buspost.ui.common.BusRouteBody { BusDetailScreen(it.arguments?.getString("routeId").orEmpty()) } }
    }

    // 闪屏压在导航图之上（含底部导航栏），与小程序"页面内 fixed 遮罩 + 隐藏 tabBar"等效
    if (splashVisible) {
        SplashOverlay(
            onFinished = { splashVisible = false },
            progress = splashProgress,
            ready = splashReady,
            statusText = splashStatus,
        )
    }
    }
    }
}

/** 闪屏每个进程只播一次（对齐小程序 globalData._splashShown） */
private var splashShownOnce = false
