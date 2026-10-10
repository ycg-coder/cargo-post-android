package com.shanxiang.buspost.core.navigation

import com.shanxiang.buspost.core.map.CoordinateSystem
import com.shanxiang.buspost.core.map.RealRoadRouteValidator
import com.shanxiang.buspost.core.map.RouteCheck
import com.shanxiang.buspost.core.map.RouteSource
import com.shanxiang.buspost.data.model.Geo
import com.shanxiang.buspost.data.model.MissionStop
import com.shanxiang.buspost.data.model.RouteGuide
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 导航不可用的**成因码**（F-3 ④⑫）。
 *
 * ⚠ 这五个名字是本仓自己定的编码，**不是指令原文**：v2.1 只写到"导航入口要按 readiness 决定能不能点、
 * 不能点要显示具体原因"这一层（`docs/android/ANDROID_COMMAND_V2.1.md:495` 那一节没有状态名清单，
 * 全 docs grep `NOT_INSTALLED` 实测 0 命中），把它写成"指令点名的"就是没核过的引用。
 * 用码而不是一句中文 reason：调用方要能分辨"组件没随包发布""没配 key""高德初始化失败"，
 * 只写"导航能力未就绪"就分不出该修哪一个（审计 §77：`BLOCKED` 要写缺什么）。
 * [NavigationGateCode.INITIALIZING] 是本码集的第六个、也是指令那类清单通常不会有的一格：把它并进 `ERROR`
 * 就是把"还在等回执"念成"这手机不能导航"，而 `初始化中不是失败，也不能被读成可用` 那条用例拦的正是这件事。
 */
enum class NavigationGateCode {
    /** 引擎就绪且这一单有起点、有调度给的目标点 —— 只有这一种可点 */
    READY,

    /** 导航组件没随包发布（`Class.forName` 实测） */
    NOT_INSTALLED,

    /** 组件在但配置不全（高德 key 未配，SDK 会静默不回调） */
    SDK_UNAVAILABLE,

    /** 已发起初始化，SDK 的结论还没回来 —— 不是失败，也不可点 */
    INITIALIZING,

    /**
     * 引擎原生组件正在**后台加载**，还没轮到问"高德回调了没"。
     *
     * 加载完会自动变成 [READY] 或 [ERROR]，页面**不需要重进**；这不是"这台机器不能导航"。
     */
    LOADING,

    /** 引擎没问题，但这一单没有可导的路线（缺起点或缺调度给的目标点） */
    NO_ROUTE,

    /** 高德回了 `onInitNaviFailure` */
    ERROR,
}

/**
 * 导航引擎的**判据层**（赛道第 8 步 / 任务 #49）。
 *
 * 这一档刻意不引任何 `android.*` 与 `com.amap.*` 类型：工程只有 JVM 单测（无 Robolectric），
 * 所以"引擎可用不可用""SDK 这一帧到底给了没给""算出来的线能不能上路"三个判断必须能被用例钉住，
 * 只有真正调 SDK 的那一层（[AmapNavigationEngine]）才允许碰 Android 与高德类型。
 * 与 `nav/ShellDecision.kt` 同一手法。
 *
 * F-3 ④⑫ 在这一层加了第四个判断：**这个导航入口能不能点**（[navigationGateOf]）——
 * 它同样必须能被用例钉住，所以住在这一层，不住在 `@Composable` 里。
 * F-3 ⑭ 加了第五个：`startNavi()` 之前，**本机记录的那条与高德此刻这条是不是同一条**（[routeBindingOf]）——
 * 理由同上，而且这一条一旦被写进 SDK 层就再也没有 JVM 用例能调到它。
 */
sealed class EngineReadiness {
    /** SDK 类已装载、key 已配置、且 SDK 回了 `onInitNaviSuccess` */
    data object Ready : EngineReadiness()

    /** 已发起初始化，SDK 还没回 —— 这不是失败，页面不许把它念成"导航不可用" */
    data object Initializing : EngineReadiness()

    /**
     * 引擎原生组件正在**后台加载**，还没轮到问"高德回调了没"。
     *
     * 为什么单列一格而不是复用 [Initializing]：`AMapNavi.getInstance()` 会把 41MB 的
     * `libAMapSDK_NAVI` 映射进进程并做重定位（模拟器 arm64 转译实测 23~29 秒，首启直接 ANR），
     * 所以这一步必须从主线程挪到后台。挪走之后"还在加载原生库"就成了一段**真实存在、
     * 且与"已发起初始化、等高德回调"成因完全不同**的时间：前者还没碰到 SDK，后者已经在等 SDK 回话。
     * 混用同一个词就是把"加载中"念成"初始化中"，而 `initResult=null` 那句判断要等加载完才有意义。
     */
    data object Loading : EngineReadiness()

    /**
     * 不可用，且必须带**成因码**与可展示的原因（审计 §77：`BLOCKED` 要写缺什么，不能只写"不支持"）。
     * 这里只允许 `NOT_INSTALLED` / `SDK_UNAVAILABLE` / `ERROR`；`READY`/`INITIALIZING`/`NO_ROUTE` 是入口层结论。
     */
    data class Blocked(
        val code: NavigationGateCode,
        val reason: String,
        val detail: String = "",
    ) : EngineReadiness()

    val canNavigate: Boolean get() = this is Ready

    /** 面向用户的文案；`Loading`/`Initializing`/`Blocked` 三态分开，避免"还没轮到"被当成"这台机器不能导航" */
    val userText: String
        get() = when (this) {
            is Ready -> "内置导航可用"
            is Loading -> "导航引擎正在加载"
            is Initializing -> "导航引擎初始化中"
            is Blocked -> reason
        }
}

/**
 * 导航入口的**唯一结论**（F-3 ④⑫）：一次调用同时回答"能不能点"与"不能点时对用户说什么"。
 *
 * 为什么要这一层，而不是让页面自己拼 `!blocked && geo != null && stop != null`：
 * 2026-10-02 实测那种拼法把 `Initializing` 当成可点（`!blocked` 为真），而判据层
 * `EngineReadiness.canNavigate` 对同一个状态说的是"不可点" —— 同一个问题两个答案。
 * 它是**纯派生**（不持有状态、不落盘、没有 flow），所以不构成第二份真相：
 * 引擎状态仍然只有 [NavigationEngine.readiness] 一处，这一层只是把它和"这一单有没有可导的路线"合起来读。
 */
data class NavigationGate(
    val code: NavigationGateCode,
    val reason: String = "",
    val detail: String = "",
) {
    val canNavigate: Boolean get() = code == NavigationGateCode.READY

    /** 成因在引擎侧还是在这一单侧：卡片底色只由它决定，`NO_ROUTE`/`INITIALIZING` 不算"引擎坏了" */
    val engineBlocked: Boolean
        get() = code == NavigationGateCode.NOT_INSTALLED ||
            code == NavigationGateCode.SDK_UNAVAILABLE ||
            code == NavigationGateCode.ERROR

    /** 面向用户的措辞只有这一个出口：不可点必须带具体原因，不许只说"不支持" */
    val userText: String get() = if (canNavigate) "导航可用" else "导航暂不可用：$reason"
}

/**
 * 入口结论 = 引擎结论 + 这一单的路线条件。
 *
 * 顺序与 [readinessOf] 同一条理由：**先问引擎在不在、配没配、初始化结果，再问这一单有没有可导的路线**。
 * 反过来的话，一台没装导航组件的机器会被念成"调度还没给任务点"，那是把环境问题说成业务问题。
 */
fun navigationGateOf(
    readiness: EngineReadiness,
    hasOrigin: Boolean,
    hasNavigableDestination: Boolean,
): NavigationGate = when (readiness) {
    is EngineReadiness.Blocked -> NavigationGate(
        code = readiness.code,
        reason = readiness.reason,
        detail = readiness.detail,
    )

    EngineReadiness.Loading -> NavigationGate(
        code = NavigationGateCode.LOADING,
        reason = "导航引擎正在加载（首次进入约需数秒）",
        detail = "导航组件正在后台加载，加载完这一格会自动变成「导航可用」，不需要退出重进。",
    )

    EngineReadiness.Initializing -> NavigationGate(
        code = NavigationGateCode.INITIALIZING,
        reason = "导航引擎正在初始化",
        detail = "initResult=null",
    )

    EngineReadiness.Ready -> when {
        !hasOrigin -> NavigationGate(
            code = NavigationGateCode.NO_ROUTE,
            reason = "暂无定位，没有起点就不算路",
            detail = "hasOrigin=false（不用终点或默认点顶替）",
        )

        !hasNavigableDestination -> NavigationGate(
            code = NavigationGateCode.NO_ROUTE,
            reason = "调度还没给未完成且有坐标的任务点",
            detail = "hasNavigableDestination=false（硬规则 3：导航不自己决定去哪里）",
        )

        else -> NavigationGate(NavigationGateCode.READY)
    }
}

/**
 * 一帧转向指引，**全部字段只来自 SDK 回调**。
 *
 * 关键约束（硬规则"宁可暂时不能导航，不允许假导航"）：[sdkStepIndex] 是 SDK 给的
 * `NaviInfo.getCurStep()`，没给就是 -1，任何情况下都不允许由本机定时器 +1 得到。
 * 剩余里程/耗时同理：来源没给 = 0，页面显示"—"，不用直线距离或经验值补。
 */
data class NavigationGuidance(
    val sdkStepIndex: Int,
    val remainMeters: Int,
    val remainSeconds: Int,
    val currentStepRemainMeters: Int,
    val currentRoad: String,
    val nextRoad: String,
    val iconType: Int,
    val remainTrafficLights: Int,
) {
    /** SDK 没给步骤号 = 这一帧不构成推进，页面不得据此显示"第 N 步" */
    val hasStepProgress: Boolean get() = sdkStepIndex >= 0

    /** 剩余里程是否可读；0 表示来源未给，不是"已经到了" */
    val hasRemainDistance: Boolean get() = remainMeters > 0
}

/** 导航会话状态：只有 SDK 真在导（[Running]）才算在推进 */
sealed class NavigationSession {
    data object Idle : NavigationSession()

    /** [startedBySdk] 直接取 `AMapNavi.startNavi(int)` 的返回值，不由本机推断 */
    data class Running(val naviType: Int, val startedBySdk: Boolean) : NavigationSession()

    data class Refused(val reason: String) : NavigationSession()
}

/**
 * 本机**刚过完真实道路闸门、因此允许据此开始导航**的那一条路（指令 §九 点名的 `AcceptedRouteSession`）。
 *
 * 为什么必须有这份记录：`calculateRoute()` 验的是高德**当时**的 `getNaviPath()`，
 * 而 `startGpsNavi()` 启动的是高德**此刻**握着的那条。`AMapNavi` 是进程单例，中间它会自己偏航重算、
 * 也会被另一张卡片（另一个引擎实例）的算路顶掉 —— 没有这份记录，"启动的就是刚验过的那条"只是愿望，
 * 这正是 V21-09 那两行 ❌ 钉的东西。
 *
 * 只装实测读得到的四个量：终点是调用方交给高德的业务坐标（GCJ-02），
 * 点数与道路米数取自 javap 核对过的 `AMapNaviPath.getCoordList()` / `getAllLength()`。
 */
data class AcceptedRouteSession(
    val destinationLat: Double,
    val destinationLng: Double,
    val coordCount: Int,
    val roadDistanceMeters: Int,
) {
    /**
     * §九 点名的 `routeFingerprint`：**上面四个实测数的定长拼接**，不是哈希、不是高德给的图版本号
     * （`graphVersion` 在后端 VO / 小程序 / DemoData 三面 0 生产者，拿它当指纹就是凭空造一个看起来可信的东西）。
     *
     * 终点参与拼接不是为了比对（见 [routeBindingOf] 为什么只能比另两项），是为了让
     * §九 那条"任务发生变化 → routeFingerprint 变化 → 旧 session 作废"**恒成立**：
     * 换了任务点就一定换指纹，即使两次算出的几何一模一样。
     */
    val routeFingerprint: String
        get() = "to=%.6f,%.6f|pts=%d|m=%d".format(destinationLat, destinationLng, coordCount, roadDistanceMeters)
}

/**
 * 高德**此刻**手里那条路径的形状，每次开始导航前重新读一次，不拿本机记忆顶替。
 *
 * 只有两项：`getNaviPath()` 能读回折线与里程，读不回终点（`getStartPont/getEndPont` 这类名字
 * 从未在本仓的 javap 取证里出现过，按不存在处理）。
 */
data class SdkPathShape(val coordCount: Int, val roadDistanceMeters: Int)

/** 绑定检查的结论：[Unbound] 必须带一句能对用户说的话，且不同成因不许共用一句 */
sealed class RouteBinding {
    data object Bound : RouteBinding()

    data class Unbound(val reason: String, val detail: String = "") : RouteBinding()
}

/**
 * §九：`startNavi()` 之前验证"本机记录的那条"与"高德现在这条"是不是同一条。
 *
 * 比对只用 SDK 真能读回来的那两个量。返回 [RouteBinding.Unbound] 的三种成因互不合并：
 * 本机压根没算成过 / 高德现在没有路径 / 有路径但不是那一条 ——
 * 合成一句"不能导航"就把该修哪一个（重新算、还是等定位、还是回工作台刷新任务）说丢了。
 *
 * 引擎可用性不在这里判：[AmapNavigationEngine.startGpsNavi] 先读 `readiness`，
 * 判据层 [EngineReadiness.canNavigate] 与入口层 [navigationGateOf] 已经各有一道闸（N-s），
 * 这里再读一次就是第三个答案。
 */
fun routeBindingOf(
    accepted: AcceptedRouteSession?,
    currentPath: SdkPathShape?,
): RouteBinding = when {
    accepted == null -> RouteBinding.Unbound(
        reason = "还没有一次通过验证的算路",
        detail = "accepted=null（§九：没有 accepted route 就禁止 startGpsNavi，要重新算真实道路）",
    )

    currentPath == null -> RouteBinding.Unbound(
        reason = "没有路径几何",
        detail = "getNaviPath()=null，本机记录=${accepted.routeFingerprint}",
    )

    currentPath.coordCount != accepted.coordCount || currentPath.roadDistanceMeters != accepted.roadDistanceMeters ->
        RouteBinding.Unbound(
            reason = "不是同一条",
            detail = "session=${accepted.routeFingerprint} now=pts=${currentPath.coordCount}|m=${currentPath.roadDistanceMeters}",
        )

    else -> RouteBinding.Bound
}

/**
 * 把 SDK 一帧 `NaviInfo` 的原始读数归一成 [NavigationGuidance]。
 *
 * 归一规则只有一条方向：**"没给"一律落到 0 / -1 / 空串**，反向（造一个看起来更完整的数）禁止。
 */
fun guidanceOf(
    curStep: Int,
    pathRetainDistanceMeters: Int,
    pathRetainTimeSeconds: Int,
    curStepRetainDistanceMeters: Int,
    currentRoadName: String?,
    nextRoadName: String?,
    iconType: Int,
    remainLightCount: Int,
): NavigationGuidance = NavigationGuidance(
    sdkStepIndex = if (curStep < 0) -1 else curStep,
    remainMeters = pathRetainDistanceMeters.coerceAtLeast(0),
    remainSeconds = pathRetainTimeSeconds.coerceAtLeast(0),
    currentStepRemainMeters = curStepRetainDistanceMeters.coerceAtLeast(0),
    currentRoad = currentRoadName.orEmpty(),
    nextRoad = nextRoadName.orEmpty(),
    iconType = iconType,
    remainTrafficLights = remainLightCount.coerceAtLeast(0),
)

/**
 * 赛道第 9 步（实时 turn-by-turn）的转向话术表：把 `NaviInfo.getIconType()` 的整数换成一句人话。
 *
 * **值域不是本机整理的**，是 `javap -constants com.amap.api.navi.enums.IconType`
 * 在实际解析到的 `com.amap.api:navi-3dmap:10.0.600_3dmap10.0.600` 上读出来的常量表，
 * 形状是 `0..54` 连号 + `65`/`66`（55..64 在该版本里根本不存在）：
 * NONE=0 DEFAULT=1 LEFT=2 RIGHT=3 LEFT_FRONT=4 RIGHT_FRONT=5 LEFT_BACK=6 RIGHT_BACK=7
 * LEFT_TURN_AROUND=8 STRAIGHT=9 ARRIVED_WAYPOINT=10 ENTER_ROUNDABOUT=11 OUT_ROUNDABOUT=12
 * ARRIVED_SERVICE_AREA=13 ARRIVED_TOLLGATE=14 ARRIVED_DESTINATION=15 ARRIVED_TUNNEL=16
 * ENTRY_LEFT_RING=17 LEAVE_LEFT_RING=18 U_TURN_RIGHT=19 SPECIAL_CONTINUE=20
 * ENTRY_RING_LEFT=21 ENTRY_RING_RIGHT=22 ENTRY_RING_CONTINUE=23 ENTRY_RING_UTURN=24
 * ENTRY_LEFT_RING_LEFT=25 ENTRY_LEFT_RING_RIGHT=26 ENTRY_LEFT_RING_CONTINUE=27 ENTRY_LEFTRINGU_TURN=28
 * CROSSWALK=29 OVERPASS=30 UNDERPASS=31 SQUARE=32 PARK=33 STAIRCASE=34 LIFT=35 CABLEWAY=36
 * SKY_CHANNEL=37 CHANNEL=38 WALK_ROAD=39 CRUISE_ROUTE=40 SIGHTSEEING_BUSLINE=41 SLIDEWAY=42
 * LADDER=43 SLOPE=44 BRIDGE=45 FERRY=46 SUBWAY=47 ENTER_BUILDING=48 LEAVE_BUILDING=49
 * BY_ELEVATOR=50 BY_STAIR=51 BY_ESCALATOR=52 LOW_TRAFFIC_CROSS=53 LOW_CROSS=54
 * MERGE_LEFT=65 MERGE_RIGHT=66
 *
 * 表为什么**一张都不能少**：漏掉一个已定义的值，页面就会对着一个高德明明给了的转向码说"未定义"，
 * 那是把"有"念成"没有"，与把"没有"念成"有"同一种不实。表外的值回 null，由页面如实说
 * "高德给了这张表里没有的转向码（N）" —— 不许就近挑一个箭头顶上（旧屏那台 `laneIcon()` 按字面猜图形，
 * 是 D-003 已登记的假导航形状）。
 */
internal fun turnIconSpeechOf(iconType: Int): String? = when (iconType) {
    0 -> "无动作"
    1 -> "通用（高德没给具体转向）"
    2 -> "左转"
    3 -> "右转"
    4 -> "左前方"
    5 -> "右前方"
    6 -> "左后方"
    7 -> "右后方"
    8 -> "左转掉头"
    9 -> "直行"
    10 -> "到达途经点"
    11 -> "进入环岛"
    12 -> "驶出环岛"
    13 -> "到达服务区"
    14 -> "到达收费站"
    15 -> "到达目的地"
    16 -> "到达隧道"
    17 -> "进入左环"
    18 -> "离开左环"
    19 -> "右转掉头"
    20 -> "特殊直行"
    21 -> "环岛向左出口"
    22 -> "环岛向右出口"
    23 -> "环岛直行出口"
    24 -> "环岛掉头出口"
    25 -> "左环向左出口"
    26 -> "左环向右出口"
    27 -> "左环直行出口"
    28 -> "左环掉头出口"
    29 -> "通过人行横道"
    30 -> "通过过街天桥"
    31 -> "通过地下通道"
    32 -> "通过广场"
    33 -> "通过公园"
    34 -> "通过阶梯"
    35 -> "通过电梯"
    36 -> "通过索道"
    37 -> "通过天空通道"
    38 -> "通过通道"
    39 -> "通过道路"
    40 -> "巡航路线"
    41 -> "观光线路"
    42 -> "通过滑道"
    43 -> "通过梯子"
    44 -> "通过斜坡"
    45 -> "通过桥"
    46 -> "通过轮渡"
    47 -> "通过地铁通道"
    48 -> "进入建筑物"
    49 -> "离开建筑物"
    50 -> "乘电梯"
    51 -> "走楼梯"
    52 -> "乘扶梯"
    53 -> "通过路口"
    54 -> "通过平路口"
    65 -> "向左并线"
    66 -> "向右并线"
    else -> null
}

/**
 * 高德要播报的那一帧。[text] 只能是 `AMapNaviListener.onGetNavigationText` 给的**原文**：
 * 本机不合成句子、不拼"前方请左转"那种高德没说过的话，所以引擎只在文本非空时才写这一帧。
 *
 * [typeCode] 同样取回调给的那个码（`javap -constants com.amap.api.navi.enums.NaviTTSType`：
 * NAVIINFO_TEXT=1、NAVI_START_TEXT=2、NAVI_END_TEXT=3、CUSTOM_TTS_TEXT=4、INTERRUPT_CURRENT=5、NAVI_YAW=6）。
 * 高德那个只带文本的重载没有类型码，所以这里是 `Int?` 而**不是**填一个"看起来像 0 号类型"的数 ——
 * null 就是"回调没带类型码"，页面据此说"高德没说是哪类播报"。
 */
data class NavigationVoice(val typeCode: Int?, val text: String)

/** 播报类型的话术；null 或表外的码都回 null，页面说"高德没说是哪类播报"，不就近并入 1 */
internal fun naviVoiceTypeNameOf(typeCode: Int?): String? = when (typeCode) {
    1 -> "导航提示"
    2 -> "开始导航"
    3 -> "结束导航"
    4 -> "自定义播报"
    5 -> "打断当前播报"
    6 -> "偏航播报"
    else -> null
}

/**
 * SDK 算出来的路径 → 唯一那道真实道路闸门。
 *
 * 走 [RealRoadRouteValidator] 而不是在引擎里另判一次，理由与审计 §26"两套状态机"同一条：
 * 同一个问题不能有第二个答案。几何声明 GCJ-02（高德自身口径），
 * 因此这里**不**做坐标系换算，也**不许**再调 `Locator.toGcj02`（会把已经偏过的点再偏一次，审计 §17 的二次偏移）。
 */
fun naviRouteCheck(
    points: List<Geo>,
    roadDistanceMeters: Int,
    roadEtaSeconds: Int,
): RouteCheck = RealRoadRouteValidator.validate(
    sourceRaw = RouteSource.AMAP.name,
    points = points,
    roadDistanceMeters = roadDistanceMeters,
    roadEtaSeconds = roadEtaSeconds,
    crsRaw = CoordinateSystem.GCJ02.name,
)

/**
 * 引擎可用性判据（纯函数，[AmapNavigationEngine] 与 [NavigationEngines] 都只调它）。
 *
 * 顺序是有意的：**先问类在不在，再问 key，最后才问 SDK 初始化结果**。
 * 反过来的话，一台没配 key 的设备会在初始化回调回来之前一直显示"初始化中"，那是把配置缺失念成进行中。
 *
 * @param sdkPresent 导航组件是否真的打进包里（`Class.forName` 实测，不靠 `BuildConfig` 猜版本）
 * @param keyConfigured 高德 key 是否配置（release/debug 都必须配置，缺 key 时 SDK 会静默不回调）
 * @param initResult SDK 的 `onInitNaviSuccess/Failure` 结果；null = 还没回
 */
fun readinessOf(
    sdkPresent: Boolean,
    keyConfigured: Boolean,
    initResult: Boolean?,
): EngineReadiness = when {
    !sdkPresent -> EngineReadiness.Blocked(
        code = NavigationGateCode.NOT_INSTALLED,
        reason = "导航能力未就绪：导航组件未随包发布",
        detail = "sdkPresent=false",
    )

    !keyConfigured -> EngineReadiness.Blocked(
        code = NavigationGateCode.SDK_UNAVAILABLE,
        reason = "导航能力未就绪：没有配置高德 key",
        detail = "keyConfigured=false（缺 key 时 SDK 不会回调，导航不能算可用）",
    )

    initResult == null -> EngineReadiness.Initializing
    initResult -> EngineReadiness.Ready
    else -> EngineReadiness.Blocked(
        code = NavigationGateCode.ERROR,
        reason = "导航能力未就绪：导航引擎初始化失败",
        detail = "onInitNaviFailure",
    )
}

/**
 * 导航的目的地：只从后端调度任务点里取，按 `visitSequence` 找**第一个未完成且有坐标**的。
 *
 * 硬规则 3「调度决定去哪里，导航决定怎么走」在这一层的落点：这里只是**读**后端的顺序，
 * 既不重排（filter + minBy 不改顺序）、也不按距离自选一个"更近的" —— 后者就是客户端替调度做决定。
 * 返回 null 时引擎必须明说"调度还没给目的地"，不许拿终点站或县城中心凑一个。
 *
 * 只排除 `COMPLETED`：状态 8（失败）是"这次操作没成"，后端并没有说**不用去那儿**，
 * 客户端把它跳掉就等于替调度改路线；失败点继续作为目的地，处理它走异常上报（V-3）。
 */
internal fun nextNavigableStop(guide: RouteGuide): MissionStop? =
    guide.stops.filter { !it.isDone && it.hasGeo }.minByOrNull { it.visitSequence }

/**
 * 「自动进导航」的唯一判定（用户口径 A：在途司机打开 App 就直接给他导航）。
 *
 * 纯函数：不持状态、不读 BuildConfig、不碰 SDK —— 状态由调用方递进来，判断只在这一处有一份。
 * 三条都必须成立，缺一条就不许跳：
 * - `canNavigate`：引擎可用 + 有真实起点 + 调度给了可导的点（见 [navigationGateOf]，不许拿假起点凑）；
 * - `hasNavigableStop`：目的地只能来自调度（硬规则 3），没有点就没有"跳过去干什么"；
 * - `!alreadyOpenedThisProcess`：**一个进程只自动跳一次**。真机实测过反面：
 *   司机按「结束导航」回到工作台，若没有这一条会被立刻弹回导航页（日志连打三次），人被困住。
 */
internal fun shouldAutoOpenGuide(
    canNavigate: Boolean,
    hasNavigableStop: Boolean,
    alreadyOpenedThisProcess: Boolean,
): Boolean = canNavigate && hasNavigableStop && !alreadyOpenedThisProcess

/**
 * 可插拔导航引擎。
 *
 * 实现只有两种：[AmapNavigationEngine]（真调 SDK）与 [BlockedNavigationEngine]（一条请求都不发、
 * 一帧指引都不造）。**没有第三种**，也就没有"用墙钟假步进顶替导航"那种实现（§24 的 6 处命中是反例，目标 0）。
 *
 * [readiness] 与 [guidance] 都是 Flow 而不是 getter：页面若每帧去读一次，就等于在本机用轮询
 * 猜测引擎状态；状态只能由 SDK 的回调推过来（初始化成功/失败、`onNaviInfoUpdate`）。
 */
interface NavigationEngine {
    val readiness: StateFlow<EngineReadiness>

    /** SDK 最近一帧指引；未初始化 / 未开始导航时恒为 null */
    val guidance: StateFlow<NavigationGuidance?>

    /** SDK 最近一次播报（文本与类型码都来自高德的回调）；高德没回调过就恒为 null，页面不许本机造话术 */
    val voice: StateFlow<NavigationVoice?>

    /**
     * SDK 最近一帧车道信息（赛道第 10 步）。null = 本机当前没有车道帧（高德没回调过，或回调里没给对象），
     * **不等于**"这一段路没有车道"——后者只有 [LaneDisplay.Cleared]（高德的 `hideLaneInfo`）才说得出。
     */
    val lane: StateFlow<LaneDisplay?>

    /**
     * SDK 最近一次**自己发起的重算**（赛道第 11 步）。null = 高德没说过它在重算。
     *
     * 这一格必须由高德说：偏航判定在本机没有任何合法来源 —— 后端只给 `RouteGuide.deviated` 一个位，
     * SDK 才是知道自己那条路的车道级偏离检测的那一方；本机自己算垂距再卡一个阈值去宣布"已偏离线路"，
     * 就是拿一个没经任何一方确认的结论替司机决定要不要重取线路（指令 v2.1 第 ⑪ 条"不得本地假判定替代后端"）。
     */
    val recalc: StateFlow<EngineRecalcDisplay?>

    /**
     * 算一条真实道路路线。[from] 为 null 时由定位补；参数一律是业务坐标（GCJ-02）。
     * 返回 [RouteCheck]：不通过时带原因，调用方显示"路线不可用 + 原因 + 重试"，不做连线示意。
     */
    suspend fun calculateRoute(from: Geo?, to: Geo): RouteCheck

    /**
     * 开始 GPS 导航；结果直接来自 SDK 返回值，不本机推断。
     *
     * [voiceOn] 是司机在设置页那个"语音播报"开关的**当次读数**：播报交给高德内置语音
     * （`setUseInnerVoice`），本机不建 `TextToSpeech`、不合成句子 —— 开关在这里读而不是在引擎里读，
     * 是为了让引擎的状态只有一个来源（SDK 回调），不掺进一份存储态。
     */
    fun startGpsNavi(voiceOn: Boolean): NavigationSession

    fun stopNavi()

    /** 与页面生命周期绑定：离开页面必须摘监听，否则 SDK 的回调会继续写这屏的状态 */
    fun release()
}

/** 引擎不可用时的替身：每个出口都回同一句真实原因，且不产生任何指引 */
class BlockedNavigationEngine(private val blockedReadiness: EngineReadiness) : NavigationEngine {
    private val emptyGuidance = MutableStateFlow<NavigationGuidance?>(null)
    override val guidance: StateFlow<NavigationGuidance?> = emptyGuidance.asStateFlow()

    // 替身一帧播报都不会有：文本只能来自高德的回调，引擎被挡住时没有任何来源
    private val emptyVoice = MutableStateFlow<NavigationVoice?>(null)
    override val voice: StateFlow<NavigationVoice?> = emptyVoice.asStateFlow()

    // 车道同理：替身没有高德回调，这一格永远是"没给过"，不是"高德说没有车道"
    private val emptyLane = MutableStateFlow<LaneDisplay?>(null)
    override val lane: StateFlow<LaneDisplay?> = emptyLane.asStateFlow()

    // 重算同理：引擎被挡住时连"高德在导哪条路"都没有，更没有什么重算可言；这一格只能恒为 null
    private val emptyRecalc = MutableStateFlow<EngineRecalcDisplay?>(null)
    override val recalc: StateFlow<EngineRecalcDisplay?> = emptyRecalc.asStateFlow()

    // 替身的状态是构造时就定死的，不会再变 —— 用它自己的 flow 暴露，页面仍然只订阅、不轮询
    private val blockedState = MutableStateFlow(blockedReadiness)
    override val readiness: StateFlow<EngineReadiness> = blockedState.asStateFlow()

    override suspend fun calculateRoute(from: Geo?, to: Geo): RouteCheck =
        RouteCheck.Rejected(reason = blockedReadiness.userText, detail = blockedReadiness.toString())

    override fun startGpsNavi(voiceOn: Boolean): NavigationSession =
        NavigationSession.Refused(blockedReadiness.userText)

    override fun stopNavi() = Unit

    override fun release() = Unit
}

/**
 * 高德一帧车道信息（赛道第 10 步）。三个数只从 `AMapLaneInfo` 的 public 字段搬来：
 * `javap com.amap.api.navi.model.AMapLaneInfo` 实测只有 `int[] backgroundLane`、`int[] frontLane`、
 * `int laneCount`（外加 `setLaneTypeId(int)` / `isRecommended()` / `getLaneTypeIdArray()` 三个方法，
 * **语义 javap 给不出来** ⇒ 本轮一个都不读，见 D-⑩-1）。
 *
 * ⚠ **两个数组不按下角标配对**：包里没有任何 JavaDoc 说 `frontLane[i]` 是 `backgroundLane[i]` 的
 * 推荐标记，而 `laneCount` 与两个数组谁长谁短也没在真机上取过一次样本。所以这里只**并置**两串读数，
 * 由页面各自念一遍；一旦本机按下标配对，就是拿一个没核过的假设替司机决定"走第几条道"。
 */
data class LaneFrame(
    val laneCount: Int,
    val backgroundCodes: List<Int>,
    val frontCodes: List<Int>,
) {
    val backgroundCount: Int get() = backgroundCodes.size
    val frontCount: Int get() = frontCodes.size

    /** 三个数自洽与否只作**提示**用：不一致时页面把三个数都念出来，本机不挑一个信、也不补齐短的 */
    val countsAgree: Boolean get() = backgroundCount == laneCount && frontCount == laneCount
}

/**
 * 车道显示状态的三种来源，**互不合并**（§八 ㉛ 的同一条规矩：「没问到」与「没有」是两格）。
 * 引擎流里的 `null` 是第四格：这一帧之前高德压根没调过车道回调，页面只能说"没给过"。
 */
sealed class LaneDisplay {
    data class Single(val frame: LaneFrame) : LaneDisplay()

    /**
     * `showLaneInfo(AMapLaneInfo[], byte[], byte[])` 那个重载：数组重载附带两个 `byte[]`，
     * javap 只给类型不给语义（很可能是"哪几条是当前推荐"的标记位，但**没证据就不解释**）。
     * 本机既不取 `first()` 冒充单组，也不把 `byte[]` 念成推荐车道 —— 猜错的方向是"让司机走错道"。
     */
    data class Groups(val groupCount: Int) : LaneDisplay()

    /** 高德主动说 `hideLaneInfo`：这一格是"已经不给车道了"，与"没给过"不同，更与"这条路没车道"不同 */
    data object Cleared : LaneDisplay()
}

/**
 * 车道码 → 高德自己的常量名。值域**不是本机整理的**，是
 * `javap -constants com.amap.api.navi.enums.LaneAction` 在实际解析到的
 * `com.amap.api:navi-3dmap:10.0.600_3dmap10.0.600`（jar sha256 前缀 `9fa3cfb8`）上读出来的 27 个
 * `public static final int`：`0..25` 连号 ＋ `255`（`LANE_ACTION_NULL`）—— ⚠ 不是 0..26，
 * 26 与 27..254 在该版本里根本没有。
 *
 * 这张表**必须 27 条一个不缺**：漏一条就是把高德明明给了的车道念成"表外"，
 * 而表外那格由 [laneCellLabel] 明说，两种说法差一个字就是两种结论。
 */
internal fun laneActionNameOf(code: Int): String? = when (code) {
    0 -> "LANE_ACTION_AHEAD"
    1 -> "LANE_ACTION_LEFT"
    2 -> "LANE_ACTION_AHEAD_LEFT"
    3 -> "LANE_ACTION_RIGHT"
    4 -> "LANE_ACTION_AHEAD_RIGHT"
    5 -> "LANE_ACTION_LU_TURN"
    6 -> "LANE_ACTION_LEFT_RIGHT"
    7 -> "LANE_ACTION_AHEAD_LEFT_RIGHT"
    8 -> "LANE_ACTION_RU_TURN"
    9 -> "LANE_ACTION_AHEAD_LU_TURN"
    10 -> "LANE_ACTION_AHEAD_RU_TURN"
    11 -> "LANE_ACTION_LEFT_LU_TURN"
    12 -> "LANE_ACTION_RIGHT_RU_TURN"
    13 -> "LANE_ACTION_LEFT_IN_AHEAD"
    14 -> "LANE_ACTION_LEFT_IN_LEFT_LU_TURN"
    15 -> "LANE_ACTION_RESERVED"
    16 -> "LANE_ACTION_AHEAD_LEFT_LU_TURN"
    17 -> "LANE_ACTION_RIGHT_RU_TURN_EX"
    18 -> "LANE_ACTION_LEFT_RU_TURN"
    19 -> "LANE_ACTION_AHEAD_RIGHT_RU_TURN"
    20 -> "LANE_ACTION_LEFT_LU_TURN_EX"
    21 -> "LANE_ACTION_BUS"
    22 -> "LANE_ACTION_EMPTY"
    23 -> "LANE_ACTION_VARIABLE"
    24 -> "LANE_ACTION_DEDICATED"
    25 -> "LANE_ACTION_TIDAL"
    255 -> "LANE_ACTION_NULL"
    else -> null
}

/**
 * 车道码 → 一句人话。**只给名字自解释的那 22 条**：纯方向词的组合
 * （`AHEAD`/`LEFT`/`RIGHT`/`LU_TURN`/`RU_TURN` 的与式）与本身就是车道类别名词的
 * （`BUS`/`EMPTY`/`VARIABLE`/`DEDICATED`/`TIDAL`）再加 `NULL`。
 *
 * ⚠ 另外五条（`LEFT_IN_AHEAD`、`LEFT_IN_LEFT_LU_TURN`、`RIGHT_RU_TURN_EX`、`LEFT_LU_TURN_EX`、
 * `RESERVED`）在这里**故意回 null**：`LEFT_IN_*` 的 in 指什么、`_EX` 是什么例外、`RESERVED` 替谁保留，
 * 包里没有任何材料说得出，硬翻成一个箭头就是本机替高德说话（与旧屏那台 `laneIcon()` 按字面猜图形同族）。
 * null 不等于"这个码不存在"—— 那五条走 [laneCellLabel] 的第二格，把高德的常量名原样报给司机。
 */
internal fun laneActionSpeechOf(code: Int): String? = when (code) {
    0 -> "直行"
    1 -> "左转"
    2 -> "直行或左转"
    3 -> "右转"
    4 -> "直行或右转"
    5 -> "左掉头"
    6 -> "左转或右转"
    7 -> "直行或左转或右转"
    8 -> "右掉头"
    9 -> "直行或左掉头"
    10 -> "直行或右掉头"
    11 -> "左转或左掉头"
    12 -> "右转或右掉头"
    16 -> "直行或左转或左掉头"
    18 -> "左转或右掉头"
    19 -> "直行或右转或右掉头"
    21 -> "公交"
    22 -> "空车道"
    23 -> "可变车道"
    24 -> "专用车道"
    25 -> "潮汐车道"
    255 -> "无车道动作"
    else -> null
}

/**
 * 一个车道码面向司机的一句话，**唯一出口**（页面自己拼第二套话术会被 N-z 抓）。
 * 三格分别对应三种结论，顺序不许反：有话术用话术 → 只有名字就报名并明说"本机不解释这个词" →
 * 名字都不在 27 条里就说是**表外的码**（高德给了这张表里没定义的值，这本身就是异常，不是"没有车道"）。
 */
internal fun laneCellLabel(code: Int): String =
    laneActionSpeechOf(code)
        ?: laneActionNameOf(code)?.let { "车道码 $code（$it，本机不解释这个词）" }
        ?: "高德给了一个 LaneAction 表里没有的车道码（$code）"

/**
 * SDK 字段 → [LaneFrame] 的唯一搬运口。方向只有一条：**没给的一律收成空串列表**，
 * 反向（补一个长度、补一个 0、补一个"直行"）禁止 —— 那是把"没给"画成一条真车道。
 */
internal fun laneFrameOf(
    laneCount: Int,
    backgroundLane: IntArray?,
    frontLane: IntArray?,
): LaneFrame = LaneFrame(
    laneCount = laneCount,
    backgroundCodes = backgroundLane?.toList().orEmpty(),
    frontCodes = frontLane?.toList().orEmpty(),
)

/**
 * 车道那一格面向司机的一句话，**页面唯一的出口**（话术在本机拼第二套就会被判据抓）。
 *
 * 四格分开写，一格一个结论，不共用句子（§八 ㉛「没问到」与「没有」是两格的那条规矩）：
 * - null：高德没调过车道回调（或调了没给对象）—— 这时**不能**说"这条路没有车道"；
 * - [LaneDisplay.Cleared]：高德自己说不显示了；
 * - [LaneDisplay.Groups]：走的是数组重载，本机只报组数；
 * - [LaneDisplay.Single]：逐条念，两个数组各念各的。
 *
 * 字段名一律照抄 `backgroundLane` / `frontLane` / `laneCount` 原文：`javap` 只给名字和类型，
 * 包里没有任何材料说清这两个数组各指哪一层，所以这里**不译成**"建议车道/全部车道"那种解释。
 */
internal fun laneDisplayTextOf(display: LaneDisplay?): String = when (display) {
    null -> "暂时没拿到车道信息"

    LaneDisplay.Cleared -> "当前路段不显示车道"

    is LaneDisplay.Groups ->
        "${display.groupCount} 组车道信息"

    is LaneDisplay.Single -> {
        val frame = display.frame
        val background = if (frame.backgroundCount == 0) "无"
        else frame.backgroundCodes.joinToString("、") { laneCellLabel(it) }
        val front = if (frame.frontCount == 0) "无"
        else frame.frontCodes.joinToString("、") { laneCellLabel(it) }
        "共 ${frame.laneCount} 条车道：后方 $background，前方 $front" +
            if (frame.countsAgree) "" else "（车道数对不上，以实际路况为准）"
    }
}

/**
 * 高德**自己发起**的重算（赛道第 11 步：偏航重算）。
 *
 * 值域与"为什么只有这四格"都来自 javap（jar sha256 前缀 `9fa3cfb8`，实际解析到的
 * `com.amap.api:navi-3dmap:10.0.600_3dmap10.0.600`）：
 * `AMapNaviListener.onReCalculateRouteForYaw()` 与 `onReCalculateRouteForTrafficJam()`
 * **两条都是 `public abstract void` 且无入参** —— 高德只说"它要重新算路"以及原因是偏航还是拥堵，
 * 不给偏了多少米、不给往哪个方向偏、也不给这次重算成没成。所以前两格各只对应一条回调，
 * 谁都不许把另一条并进来（把拥堵说成偏航，司机就会去找自己根本没走过的错）。
 *
 * ⚠ 后两格是这一族里唯一一处"看起来能配对但绝不配对"的地方：
 * 重算的结果仍然是从 `onCalculateRouteSuccess` / `onCalculateRouteFailure` 回来的，而这两条回调**不带原因**，
 * `AMapNavi` 又是进程单例（偏航、前方路况、另一个调用方都能触发一次），
 * 于是"刚才那句偏航"与"现在这次成功"之间没有任何本机可依据的对应关系。
 * 把它们接成一对，就是本机替高德说了一句"你那次偏航已经重算好了"——那是把推断写成事实。
 * 所以 [EngineInitiatedSuccess] / [EngineInitiatedFailure] 只报"高德自己回了一次什么"，格子之间也不互相覆盖出因果。
 *
 * 另外两族**故意不在这里**（不是漏了，是本轮按证据判定接不了）：
 * - `onNaviRouteNotify(AMapNaviRouteNotifyData)`：`AMapNaviRouteNotifyDataType` 实测只有
 *   `AVOID_RESTRICT_AREA=1 / FORBIDDEN_AREA=2 / ROAD_CLOSED_AREA=3 / AVOID_JAM_AREA=4 / DISPATCH=5 /
 *   CHANGE_MAIN_ROUTE=20 / GPS_SIGNAL_WEAK=21` 七条，**里面没有偏航这一类** ⇒ 它不是偏航通道；
 *   而入参类八个成员全 private（只有 getter），`distance` / `latitude` / `longitude` / `roadName` 指什么
 *   javap 给不出语义 ⇒ 一个都不解释。
 * - `reCalculateRoute(int)`：`ReCalculateRouteType`（含 `ROUTE_TYPE_YAW=1`）是那**个方法的入参**值域，
 *   即"本机主动要求重算"。本机没有任何合法的触发依据（偏航检测在高德那边），调它等于本机替高德决定何时重算。
 */
sealed class EngineRecalcDisplay {
    /** 高德的 `onReCalculateRouteForYaw()`：它认为车已经偏离它给的这条线，要重新算 */
    data object Yaw : EngineRecalcDisplay()

    /** 高德的 `onReCalculateRouteForTrafficJam()`：前方拥堵，它要重新算 */
    data object TrafficJam : EngineRecalcDisplay()

    /**
     * 高德回了一次算路成功，而**本机当时没有在等回执**（`pending == null`）。
     * [code] 直接取 `AMapCalcRouteResult.errorCode`（高德那个字段本来就跟着成功回执一起回），
     * 没有对象时如实是 0 之外的 -1：这个数是高德给的，不是本机分的档。
     */
    data class EngineInitiatedSuccess(val code: Int) : EngineRecalcDisplay()

    /** 同上，只是高德回的是失败。失败原因文本（`errorDescription`/`errorDetail`)可能是空串，所以不搬进这一格 */
    data class EngineInitiatedFailure(val code: Int) : EngineRecalcDisplay()
}

/**
 * 重算那一格面向司机的一句话，**页面唯一的出口**（与 [laneDisplayTextOf] 同一条规矩：
 * 页面自己拼第二套说法就会被判据抓）。
 *
 * 五格分开写、一格一个结论：
 * - null：高德没说过它在重算 —— 这一句**同时**是本机"不判偏航"的声明（阈值判定不归本机）；
 * - Yaw / TrafficJam：各自只念自己那条回调，且明说回调没给的那些量本机不知道；
 * - 后两格：只报"高德自己回了一次"，不报因果。
 *
 * ⚠ 句子里不许出现"重算成功""已回到线路"这类结论：高德那两条广播没有结局，
 * 算路成功的回执又不带原因，本机无论站哪一头都说不出来这句话。
 */
internal fun engineRecalcTextOf(display: EngineRecalcDisplay?): String = when (display) {
    null -> "高德没有说过重算（本机不拿距离卡阈值判偏航）"

    EngineRecalcDisplay.Yaw ->
        "高德说车辆偏离路线，它要重新算路"

    EngineRecalcDisplay.TrafficJam ->
        "高德说前方拥堵，它要重新算路"

    is EngineRecalcDisplay.EngineInitiatedSuccess ->
        "高德回了算路结果（errorCode=${display.code}，回执不带原因）"

    is EngineRecalcDisplay.EngineInitiatedFailure ->
        "高德回了算路失败（errorCode=${display.code}，回执不带原因）"
}

/**
 * 「我的」里那两条司机开关**关着**时的一句话，页面唯一的出口（与 [laneDisplayTextOf] /
 * [engineRecalcTextOf] 同一条规矩：句子不从这一处以外长出来）。
 *
 * 赛道 ⑯ 查出来的口径不一致就在这两句上。开关关着的旧写法是"本机没显示高德给的车道"——
 * 那半句"高德给的"是本机在这一刻**说不出**的：车道帧的写入点全在高德回调里（[LaneDisplay]），
 * 而司机开关从未交给过引擎（实测 `Prefs.laneGuidance` 在 `core/navigation` 里 0 读取点，
 * 唯一读它的地方就是这一屏的显示分支），所以关掉开关时高德照旧在推帧，本机只是这一格不念。
 * 反过来，语音开关是**真的**递给高德的（`startGpsNavi(Prefs.voiceGuidance)` → `setUseInnerVoice`），
 * 那一格可以照实说"本机没让高德播报"。
 *
 * 两句共用的红线是 ⑨/⑩ 立下的那条：本机没做 ≠ 高德没给，二者不许并成一句，
 * 也不许由开关那一格替高德宣告它给过什么。`switchName` 因此必须照抄「我的」里那一行的名字，
 * 指错名字等于把司机支到一条不存在的开关前面（旧写法那句"车道引导开关"在「我的」里没有对应行）。
 */
internal fun guidanceSwitchOffText(switchName: String, detail: String): String =
    "${switchName}开关是关的（在「我的」里），本机$detail"
