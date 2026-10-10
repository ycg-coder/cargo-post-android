package com.shanxiang.buspost.core.navigation

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.amap.api.maps.model.LatLng
import com.amap.api.navi.AMapNavi
import com.amap.api.navi.SimpleNaviListener
import com.amap.api.navi.enums.NaviType
import com.amap.api.navi.enums.PathPlanningStrategy
import com.amap.api.navi.model.AMapCalcRouteResult
import com.amap.api.navi.model.AMapLaneInfo
import com.amap.api.navi.model.AMapNaviPath
import com.amap.api.navi.model.NaviInfo
import com.amap.api.navi.model.NaviPoi
import com.shanxiang.buspost.BuildConfig
import com.shanxiang.buspost.core.map.RealRoadRouteValidator
import com.shanxiang.buspost.core.map.RouteCheck
import com.shanxiang.buspost.core.net.attempt
import com.shanxiang.buspost.data.model.Geo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 高德导航引擎（赛道第 8 步）。
 *
 * **只调用 `javap` 逐个核对过的签名**（审计 §77 一，核对对象是实际解析到的
 * `com.amap.api:navi-3dmap:10.0.600_3dmap10.0.600`）：
 * `AMapNavi.getInstance(Context)`、`addAMapNaviListener` / `removeAMapNaviListener`、
 * `calculateDriveRoute(NaviPoi, NaviPoi, List<NaviPoi>, int)`、`getNaviPath()`、
 * `AMapNaviPath.getCoordList()/getAllLength()/getAllTime()`、`startNavi(int)`、`stopNavi()`、
 * `NaviInfo.getCurStep()/getPathRetainDistance()/getPathRetainTime()/getCurStepRetainDistance()/getCurrentRoadName()/getNextRoadName()/getIconType()/getRouteRemainLightCount()`。
 * 车道那一族同样是 javap 实测（同一份 jar）：`SimpleNaviListener.showLaneInfo(AMapLaneInfo)`、
 * `showLaneInfo(AMapLaneInfo[], byte[], byte[])`、`hideLaneInfo()`，以及 `AMapLaneInfo` 的三个 public 成员
 * `int[] backgroundLane` / `int[] frontLane` / `int laneCount`。
 * 偏航重算那一族也是同一份 jar 实测：`SimpleNaviListener.onReCalculateRouteForYaw()` 与
 * `onReCalculateRouteForTrafficJam()`（两条都**无入参**，所以引擎里没有任何"偏了多少米"的量可写）。
 * 这一族**不需要新的 import**：两个方法都在已引入的 `SimpleNaviListener` 上，显示态类型住纯层 ——
 * 所以判据 N-j 的 SDK import 数与白名单本轮都不抬（抬了就等于承认引入了没核对过的类名）。
 * 任何一个没在 javap 输出里出现的名字都不在这里出现 —— 编不出就是不存在，不能凭印象写。
 *
 * 这一层的诚实性底线（硬规则"宁可暂时不能导航，不允许假导航"）：
 * - 推进只来自 SDK 回调（`onNaviInfoUpdate`），**没有定时器、没有 `stepIndex + 1`**；
 * - 算路结果只走 [naviRouteCheck] → [RealRoadRouteValidator] 那**唯一**一道真实道路闸门；
 * - 拿不到结果就回 [RouteCheck.Rejected] 带原因，不用后端 `routeGuide` 的折线、更不用站点连线顶替。
 */
class AmapNavigationEngine(private val context: Context) : NavigationEngine {

    private val guidanceState = MutableStateFlow<NavigationGuidance?>(null)
    override val guidance: StateFlow<NavigationGuidance?> = guidanceState.asStateFlow()

    /**
     * 播报帧的唯一写入点是下面那个监听里的 `onGetNavigationText` —— 文本与类型码都取高德的原文。
     * 这里不拼句子、不接本机 TTS：本机合成出来的话听着再像真的也不是引擎说的（硬规则"宁可暂时不能导航，
     * 不允许假导航"在语音这一半的落点）。
     */
    private val voiceState = MutableStateFlow<NavigationVoice?>(null)
    override val voice: StateFlow<NavigationVoice?> = voiceState.asStateFlow()

    /**
     * 车道帧的唯一写入点是下面那个监听里的 `showLaneInfo` / `hideLaneInfo`（赛道第 10 步）。
     * null = 本机当前没有车道帧（高德没回调过，或回调里没给对象），**不等于**"这一段路没有车道"；
     * 后一句只有高德的 `hideLaneInfo` 说得出（[LaneDisplay.Cleared]）。
     */
    private val laneState = MutableStateFlow<LaneDisplay?>(null)
    override val lane: StateFlow<LaneDisplay?> = laneState.asStateFlow()

    /**
     * 高德自己发起的重算，唯一写入点是下面那个监听里的两条广播与 `pending == null` 那两支（赛道第 11 步）。
     * null = 高德没说过它在重算 —— 本机不拿垂距卡阈值去造这一格，所以页面读到 null 时说的就是"高德没说"。
     */
    private val recalcState = MutableStateFlow<EngineRecalcDisplay?>(null)
    override val recalc: StateFlow<EngineRecalcDisplay?> = recalcState.asStateFlow()

    /**
     * SDK 的初始化结论：null = 回调还没回来，**不等于失败**。
     *
     * 初值取 [NaviInitState] 而不是写死 null：`AMapNavi` 是**进程单例**，`onInitNaviSuccess`
     * 一生只回调一次；而引擎实例随页面进出 / 长列表滚动重建。结论只存实例里的话，
     * 第二次进这一屏必然读到 null —— 页面就把"已就绪"念成"初始化中"，按钮永久置灰。
     */
    private val initResult = MutableStateFlow(NaviInitState.read())

    /**
     * 后台预热算出来的两个环境量（类在不在 / key 配没配）。`null` = 还没算完。
     *
     * 不在构造期算：构造发生在 Compose 的 `remember{}` 里（主线程），而预热那一步要调
     * `AMapNavi.getInstance()` —— 它把 41MB 的 `libAMapSDK_NAVI` 映射进进程。
     * 主线程做这件事的代价，2026-10-09 模拟器实测是一次 ANR。
     */
    @Volatile
    private var sdkPresent: Boolean? = null

    /** 预热只允许起飞一次（`prepare()` 会从算路与开始导航两处被调到） */
    private val warmupLock = Any()

    /**
     * 预热完成的信号：`true` = 监听**已经真的**挂在进程单例上。
     *
     * 为什么需要它：预热搬进后台线程之前，"取实例 → 挂监听 → 发算路请求"是同一个函数里顺序执行的，
     * 先后关系天然成立；搬走之后，发请求的那一方必须先等这一步 —— 高德的算路回执只发给已注册的监听。
     */
    private val warmupDone = CompletableDeferred<Boolean>()

    @Volatile
    private var warming = false

    /** 页面已经走了。预热晚到时要自己把监听摘掉，不能留一个没人管的监听挂在进程单例上 */
    @Volatile
    private var released = false

    /**
     * 状态有三个写入点：构造时的 [EngineReadiness.Loading]（原生库还在后台加载）、
     * 预热线程算出环境量之后的 [publishReadiness]、以及高德初始化回调。页面订阅它，不本机轮询
     * —— 轮询等于用定时器的节奏去猜引擎的节奏。
     *
     * 唯一一处例外是先读 [NaviInitState]：它非空说明高德**已经回过话**，那类一定在、库一定加载过，
     * 这一格可以直接给出结论，不必再等一次预热（也就不用为了填一格去碰高德类）。
     */
    private val readinessState = MutableStateFlow(
        NaviInitState.read()?.let { known ->
            readinessOf(sdkPresent = true, keyConfigured = BuildConfig.AMAP_KEY.isNotBlank(), initResult = known)
        } ?: EngineReadiness.Loading,
    )
    override val readiness: StateFlow<EngineReadiness> = readinessState.asStateFlow()

    /** 正在等算路回调的那一次；null = 当前没有请求在飞 */
    private var pending: CompletableDeferred<CalcOutcome>? = null

    /**
     * §九 的那份记录（[AcceptedRouteSession]）。写/清就是下面这几处，别处一律不许碰：
     * 只有过了 [naviRouteCheck] 被 Accepted 的那一次才写入；一旦本机对"高德现在握着哪条路"失去确认
     * （请求发出去了却没被受理、超时/算路失败、高德连路径都没给、高德自己广播要重算（偏航、拥堵）、
     * 高德自己回了算路结果、停止导航、释放引擎）就清空。
     *
     * 清空而不是"沿用上一条"：留着一条对不上的记录，下一步就是拿它去 `startNavi()`，
     * 那正是"导航用的不是刚验过的那条"（V21-09）。
     */
    private var acceptedRoute: AcceptedRouteSession? = null

    /** 由预热线程写、主线程读（[release]），必须 volatile */
    @Volatile
    private var listener: SimpleNaviListener? = null

    /**
     * 把"后台预热算出的环境量 + 高德回调给的结论"合成一次判据。三处写状态都走它，
     * 保证同一次读取里的两个输入来自同一时刻 —— 分开写会出现"类在不在"更新了、
     * 结论还是上一轮的那一格。
     */
    private fun publishReadiness() {
        val present = sdkPresent ?: return // 环境量还没算完，继续停在 Loading，不编一个默认值
        readinessState.value = readinessOf(
            sdkPresent = present,
            keyConfigured = BuildConfig.AMAP_KEY.isNotBlank(),
            initResult = initResult.value,
        )
        Log.i(NAVI_TAG, "readiness=${readinessState.value} @${Thread.currentThread().name}")
    }

    /** 初始化结论**只能**由高德的回调写入 */
    private fun applyInitResult(success: Boolean) {
        Log.i(NAVI_TAG, "高德初始化回调 onInitNaviSuccess/Failure($success) @${Thread.currentThread().name}")
        // 同时落到进程级：这份结论属于"高德引擎在不在"，不属于"这张卡片还在不在"
        NaviInitState.write(success)
        // 能让回调跑起来就证明类在（也顺手补齐环境量，免得回调比预热那行写值更早到）
        sdkPresent = true
        initResult.value = success
        publishReadiness()
    }

    /**
     * 开始预热：注册监听，必须早于第一次算路，否则 `onInitNaviSuccess` 会在没人听的时候过去，
     * 这一屏就永远停在"初始化中" —— 那是把已就绪念成未就绪，同样是不实。
     *
     * **方法本身立刻返回**，两段式：
     * 1. [NaviNativeLoad] 在后台线程把 41MB 的原生库映射进进程（贵，但不挑线程）；
     * 2. 加载完再回**主线程**取单例 + 挂监听（便宜，但高德的初始化结果只投递给主线程）。
     *
     * 对照数据（2026-10-09，模拟器，同一次安装）：
     * - 拆开之前：`getInstance()` 整段压在 Compose `remember{}`（主线程）里 —— 首启实测 13~29 秒，ANR 一次；
     * - 拆开之后：库加载不占主线程，主线程只剩 `AMapNavi.getInstance()` 的 2.3 秒，回调正常到达、无 ANR。
     *
     * 这段时间页面读到的是 [EngineReadiness.Loading]，文案由判据层自带，页面**不需要加任何分支**。
     */
    fun prepare() {
        if (listener != null) return
        synchronized(warmupLock) {
            if (warming) return
            warming = true
        }
        NaviNativeLoad.ensure(Runnable { warmUp() })
    }

    /**
     * 等预热把监听挂上。上限 [WARMUP_WAIT_MS]：这段等待只可能发生在"原生库早就加载过"的情况下
     * （没加载过时 readiness 还停在 [EngineReadiness.Loading]，算路那道闸门会先把它挡回去），
     * 所以它本该是毫秒级；等不到就是真的出了问题，如实回 false，让调用方带原因拒绝。
     */
    private suspend fun awaitWarmUp(): Boolean =
        listener != null || withTimeoutOrNull(WARMUP_WAIT_MS) { warmupDone.await() } == true

    /**
     * 预热第二段：在**主线程**（[NaviNativeLoad.ensure] 保证）算环境量 → 取进程单例 → 挂监听。
     * 任何一步不成都不编结论，只如实重算一次判据。
     */
    private fun warmUp() {
        // 每条出口都要给 warmupDone 一个交代：它是 awaiter 的唯一信号，漏一条就会让人白等到超时
        if (released) {
            warmupDone.complete(false)
            return
        }
        val present = naviClassPresent()
        sdkPresent = present
        Log.i(NAVI_TAG, "预热开始 @${Thread.currentThread().name}: 导航类存在=$present")
        if (!present) {
            publishReadiness()
            warmupDone.complete(false)
            return
        }
        // 取不到实例时不在这里编一个原因：readiness 会按"类在、key 在、高德还没回"如实报
        val t0 = SystemClock.elapsedRealtime()
        val navi = instance() ?: run {
            Log.w(NAVI_TAG, "AMapNavi.getInstance 未取得实例")
            publishReadiness()
            warmupDone.complete(false)
            return
        }
        Log.i(NAVI_TAG, "AMapNavi.getInstance 耗时 ${SystemClock.elapsedRealtime() - t0}ms @${Thread.currentThread().name}")
        if (released) {
            warmupDone.complete(false)
            return
        }
        val l = object : SimpleNaviListener() {
            override fun onInitNaviSuccess() {
                applyInitResult(true)
            }

            override fun onInitNaviFailure() {
                applyInitResult(false)
            }

            override fun onNaviInfoUpdate(info: NaviInfo?) {
                if (info == null) return
                guidanceState.value = guidanceOf(
                    curStep = info.curStep,
                    pathRetainDistanceMeters = info.pathRetainDistance,
                    pathRetainTimeSeconds = info.pathRetainTime,
                    curStepRetainDistanceMeters = info.curStepRetainDistance,
                    currentRoadName = info.currentRoadName,
                    nextRoadName = info.nextRoadName,
                    iconType = info.iconType,
                    remainLightCount = info.routeRemainLightCount,
                )
            }

            /**
             * 赛道第 9 步：播报文本由高德给。两个重载都接（javap 实测 `SimpleNaviListener` 同时有
             * `onGetNavigationText(int, String)` 与 `onGetNavigationText(String)`），
             * 到底哪一个真会被调到只有真机能验，本轮不挑一个假装"另一个不存在"。
             * 空文本不写帧（写成空串会让页面显示一行空白而被读成"高德说没什么"）。
             */
            override fun onGetNavigationText(type: Int, text: String?) {
                voiceState.value = text?.takeIf { it.isNotBlank() }?.let { NavigationVoice(typeCode = type, text = it) }
            }

            override fun onGetNavigationText(text: String?) {
                voiceState.value = text?.takeIf { it.isNotBlank() }?.let { NavigationVoice(typeCode = null, text = it) }
            }

            /**
             * 赛道第 10 步：车道信息由高德给。两个重载都接（javap 实测 `SimpleNaviListener` 同时有
             * `showLaneInfo(AMapLaneInfo)` 与 `showLaneInfo(AMapLaneInfo[], byte[], byte[])`），
             * 真机到底走哪一个只有真机能验，本轮不挑一个假装"另一个不存在"。
             *
             * `info == null` 与 `hideLaneInfo()` 分开写：前者是"回调来了但没给对象"，本机只能说没有帧；
             * 后者才是高德明说"已经不显示车道"。**清空而不留住上一帧** —— 留着的帧会被读成"现在还有这些车道"，
             * 而它属于上一个路口。
             */
            override fun showLaneInfo(info: AMapLaneInfo?) {
                laneState.value = info?.let {
                    LaneDisplay.Single(
                        laneFrameOf(
                            laneCount = it.laneCount,
                            backgroundLane = it.backgroundLane,
                            frontLane = it.frontLane,
                        ),
                    )
                }
            }

            /**
             * 数组重载只登记**组数**。javap 对那两个 `byte[]` 只给类型（`byte[]`）不给语义，
             * `AMapLaneInfo` 也没有可读的注释可依据 ⇒ 取 `first()` 当单组、或把两个数组按车道号解释，
             * 都是把没核过的东西写成高德的意思（硬规则在车道这一半的落点）。
             */
            override fun showLaneInfo(infos: Array<AMapLaneInfo>?, unused1: ByteArray?, unused2: ByteArray?) {
                val groupCount = infos?.size ?: 0
                laneState.value = if (groupCount == 0) null else LaneDisplay.Groups(groupCount)
            }

            override fun hideLaneInfo() {
                laneState.value = LaneDisplay.Cleared
            }

            /**
             * 赛道第 11 步：偏航由高德说。javap 实测这条是 `public abstract void onReCalculateRouteForYaw()`
             * —— **无入参**，所以本机只知道"它认为车偏离了它给的这条线、它要重新算路"，
             * 偏多少米、往哪偏、什么时候算完，高德这一条里一个都没给，页面也就一个都不许说
             * （旧调试屏那台"垂距 > 60 米即算偏航"是本地假判定，撤下的理由记 D-057）。
             *
             * 广播一到就把 §九 那份凭证作废：高德既然说要重算，"本机刚验过的那条"从这一刻起就不再是
             * 高德手里那条了。留着它就可能被拿去 `startNavi()`，而重算的结局还要等一条不带原因的回执 ——
             * 那才是 V21-09 要避免的那种"对不上也说不清"。
             */
            override fun onReCalculateRouteForYaw() {
                acceptedRoute = null
                recalcState.value = EngineRecalcDisplay.Yaw
            }

            /**
             * 拥堵重算与偏航重算**各一格，不合并**：成因是高德那两条不同的回调给的，
             * 并成一句"高德在重算"就把司机该做的事（是不是开出线了 vs 前方堵了）说丢了。
             */
            override fun onReCalculateRouteForTrafficJam() {
                acceptedRoute = null
                recalcState.value = EngineRecalcDisplay.TrafficJam
            }

            override fun onCalculateRouteSuccess(result: AMapCalcRouteResult?) {
                val slot = pending
                if (slot == null) {
                    // 没人在等这一次回执 = 高德自己重算的（偏航、前方路况、外部调用）。
                    // §九：routeFingerprint 一变，旧 session 就作废、要求重新算真实道路 ——
                    // 本机那道闸门（naviRouteCheck）没验过这条新路径，所以这里把它记成"已验证"就是不实。
                    // 正在导的会话由高德继续带着走（SDK 才是推进来源），但下一次 startGpsNavi 必须先重新算。
                    acceptedRoute = null
                    // 这一格只报"高德自己回了一次成功"，**不报它是上面哪句广播的结果**：
                    // 这条回调不带原因，`AMapNavi` 又是进程单例（另一个调用方的算路也会走到这里）。
                    // 真机到底会不会同时走 `onCalculateRouteSuccess(int[])` 那个重载，本机没取过一次样本 ⇒ D-⑪-1。
                    recalcState.value = EngineRecalcDisplay.EngineInitiatedSuccess(result?.errorCode ?: -1)
                    return
                }
                slot.complete(CalcOutcome.Calculated(result?.errorCode ?: 0))
            }

            override fun onCalculateRouteFailure(result: AMapCalcRouteResult?) {
                val code = result?.errorCode ?: -1
                val desc = listOfNotNull(result?.errorDescription, result?.errorDetail)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                // 算路失败后高德手里是哪条，本机不知道；把上一条记录留着就可能被拿去 startNavi()
                acceptedRoute = null
                val slot = pending
                if (slot == null) {
                    // 没人等这次失败 = 高德自己那一次重算没成。失败文本（errorDescription/errorDetail）不搬进这一格：
                    // 高德可能给空串，而页面读到的空串会被念成"失败但没有原因"，那是把字段缺失包装成结论。
                    recalcState.value = EngineRecalcDisplay.EngineInitiatedFailure(code)
                    return
                }
                slot.complete(CalcOutcome.Failed("高德算路失败（code=$code${desc.ifEmpty { "" }.let { "：$it" }}）"))
            }

            override fun onArriveDestination() {
                // 到达是高德说的，不是本机算的；这里只留状态，段/订单状态仍必须回读后端
            }
        }
        if (runCatching { navi.addAMapNaviListener(l) }.isFailure) {
            Log.w(NAVI_TAG, "addAMapNaviListener 失败")
            publishReadiness()
            warmupDone.complete(false)
            return
        }
        Log.i(NAVI_TAG, "已注册初始化监听，等高德回调")
        // 挂上之后再看一次"页面走没走"：走在这一步之前的话，上面那次检查放过了它，
        // 监听就会留在进程单例上一直没人摘 —— 那是把一次页面进出变成一份进程级残留。
        if (released) {
            runCatching { navi.removeAMapNaviListener(l) }
            // 交代（2026-10-09 修）：本函数头顶那句“每条出口都要给 warmupDone 一个交代”，
            // 这里是唯一没照做的出口 —— 裸 return 会让 awaiter 白等满 5s 超时。
            warmupDone.complete(false)
            return
        }
        listener = l
        // 这里通常还是 Initializing（高德的结论要等它自己回）；本轮把 Loading 收敛成
        // “环境量已知”，避免页面在原生库加载完之后还停在一个更含糊的格子上。
        publishReadiness()
        // 成功出口的交代（2026-10-09 修）：失败出口五处都 complete(false)，唯独这里漏了 ——
        // awaitWarmUp 只能白等满 WARMUP_WAIT_MS 超时，实机表现为“首帧拒绝、重试即好”
        // （第一次等满超时被拒；第二次进来 listener 已非空短路返回，所以又好了）。
        // 信号语义是“监听已挂上”而不是“高德已就绪”：calculateRoute 拿到真值后发算路请求，
        // 回执由这个刚挂上的监听接；init 结论到没到由 readiness 那道闸门另行把关，两者不能混。
        warmupDone.complete(true)
    }

    override suspend fun calculateRoute(from: Geo?, to: Geo): RouteCheck {
        val state = readiness.value
        // 只认 Ready：`Initializing` 时高德还没说"我能用"，这时把算路请求发出去就是替 SDK 做决定
        // （F-3 ④⑫：入口层与引擎层必须给同一个答案，判据层的 canNavigate 早就说它不可点）
        if (!state.canNavigate) {
            return RouteCheck.Rejected(
                reason = state.userText,
                detail = "readiness=$state（只有 Ready 才把算路请求交给高德）",
            )
        }
        // 起点缺失时禁止用终点、县城中心或"上一个已知点"顶替：那是假导航的第一步
        val origin = from ?: return RouteCheck.Rejected(
            reason = "没有定位，无法算路",
            detail = "from=null（不用默认点顶替）",
        )
        if (!RealRoadRouteValidator.isValidGeo(origin)) {
            return RouteCheck.Rejected(reason = "起点坐标非法", detail = origin.toString())
        }
        if (!RealRoadRouteValidator.isValidGeo(to)) {
            return RouteCheck.Rejected(reason = "终点坐标非法", detail = to.toString())
        }
        val navi = instance() ?: return RouteCheck.Rejected(
            reason = "导航引擎实例不可用",
            detail = "AMapNavi.getInstance 未取得实例",
        )
        prepare()
        // 监听必须先真的挂上，再把算路请求发出去：高德的算路回执只发给**已注册**的监听，
        // 晚挂一步这一次请求就没人接，页面只能等满 20 秒超时、然后把超时报成"引擎故障"。
        // 预热搬进后台线程之后，这个先后关系不再由"同一个函数里顺序执行"天然保证，必须显式等。
        if (!awaitWarmUp()) {
            return RouteCheck.Rejected(
                reason = "导航引擎还在准备中",
                detail = "预热未完成（监听尚未注册，此刻发算路请求没人接回执）",
            )
        }
        // 回执是进程级单回调，只有一个等待槽：并发算路会让前一次永远等不到自己的结果（超时报成引擎故障）。
        // 宁可明说"上一次还在飞"，也不让两次请求互相顶掉。
        if (pending != null) {
            return RouteCheck.Rejected(
                reason = "已有一次算路在等高德回执，请稍后重试",
                detail = "pending!=null（同一引擎同一时刻只允许一次算路）",
            )
        }
        val deferred = CompletableDeferred<CalcOutcome>()
        pending = deferred
        // attempt 而非 runCatching：这里在 `override suspend fun calculateRoute` 里，
        // runCatching 连 CancellationException 一起收成 false，会把"调用方已取消"报成"算路请求没被受理"。
        val accepted = attempt {
            navi.calculateDriveRoute(
                NaviPoi(null, LatLng(origin.lat, origin.lng), null),
                NaviPoi(null, LatLng(to.lat, to.lng), null),
                null,
                STRATEGY,
            )
        }.getOrDefault(false)
        if (!accepted) {
            pending = null
            // 请求交给高德了却没被受理：高德现在握着哪条路径，本机无从知道
            acceptedRoute = null
            return RouteCheck.Rejected(
                reason = "算路请求没有被导航引擎受理",
                detail = "calculateDriveRoute=false",
            )
        }
        val outcome = withTimeoutOrNull(CALC_TIMEOUT_MS) { deferred.await() }
            ?: CalcOutcome.Failed("导航引擎在 ${CALC_TIMEOUT_MS / 1000} 秒内没有回算路结果")
        pending = null
        if (outcome is CalcOutcome.Failed) {
            acceptedRoute = null
            return RouteCheck.Rejected(reason = outcome.reason, detail = "strategy=$STRATEGY_NAME")
        }
        val path = navi.naviPath
        if (path == null) {
            acceptedRoute = null
            return RouteCheck.Rejected(
                reason = "高德没有返回路径几何",
                detail = "getNaviPath()=null",
            )
        }
        val points = path.geoPoints()
        val check = naviRouteCheck(
            points = points,
            roadDistanceMeters = path.allLength,
            roadEtaSeconds = path.allTime,
        )
        // 记录与返回必须来自同一个判断：过了闸门才留下 §九 那份凭证，没过就把上一条一起带走
        acceptedRoute = (check as? RouteCheck.Accepted)?.let {
            AcceptedRouteSession(
                destinationLat = to.lat,
                destinationLng = to.lng,
                coordCount = points.size,
                roadDistanceMeters = path.allLength,
            )
        }
        return check
    }

    override fun startGpsNavi(voiceOn: Boolean): NavigationSession {
        // 未就绪时先回真实原因（缺组件 / 缺 key / 初始化失败 / 还没回初始化结果），不要退化成含糊的"实例不可用"
        val state = readiness.value
        if (!state.canNavigate) {
            return NavigationSession.Refused(state.userText)
        }
        // 监听还没挂上就发 startNavi：路会真的开始导，但转向/车道/播报一帧都到不了页面 ——
        // 那正是"看着像在导"的假导航。这里如实拒绝，不靠"算路刚成功过"推断监听一定在。
        if (listener == null) {
            return NavigationSession.Refused("导航引擎还在准备中（监听尚未注册）")
        }
        val navi = instance() ?: return NavigationSession.Refused("导航引擎实例不可用")
        prepare()
        // §九：先把高德**此刻**那条路径读回来，与本机记录比一次，再决定要不要把 startNavi() 发出去。
        // 读不到就按读不到处理 —— 不许拿"上一次算路成功过"当作"现在还是那一条"。
        val currentPath = runCatching { navi.naviPath }.getOrNull()?.let {
            SdkPathShape(coordCount = it.geoPoints().size, roadDistanceMeters = it.allLength)
        }
        val binding = routeBindingOf(accepted = acceptedRoute, currentPath = currentPath)
        if (binding is RouteBinding.Unbound) {
            return NavigationSession.Refused(binding.reason)
        }
        // 赛道第 9 步的语音那一半交给高德内置播报（`setUseInnerVoice(boolean)` 是 javap 核对过的签名）：
        // 本机不建 TextToSpeech、不合成句子。关的时候也必须显式传 false —— `AMapNavi` 是进程单例，
        // 上一次开过就会一直开着，只在一个方向上调用等于把司机设置里那个开关做成了单向的。
        runCatching { navi.setUseInnerVoice(voiceOn) }
        val started = runCatching { navi.startNavi(NaviType.GPS) }.getOrDefault(false)
        return if (started) {
            NavigationSession.Running(naviType = NaviType.GPS, startedBySdk = true)
        } else {
            NavigationSession.Refused("高德没有开始导航（startNavi 返回 false）")
        }
    }

    override fun stopNavi() {
        instance()?.let { runCatching { it.stopNavi() } }
        // 停止导航不等于停止指引展示：清掉这一帧，页面回落到"未在导航"，而不是留住最后一个数
        guidanceState.value = null
        // 播报同理：停在"最后一句高德说的话"上，会被读成还在导
        voiceState.value = null
        // 车道也同理：停在最后一个路口上，会被读成"当前路口还是这些车道"
        laneState.value = null
        // 重算那一格同理：留住"高德说它要重算"会在下一次进这一屏时被读成"现在还在重算"，
        // 而那件事属于上一次会话；清掉之后页面念的是"高德没说过"，那才是这一屏当前的真相
        recalcState.value = null
        // 一次会话结束了，凭证就一起作废：下一次开始必须重新算一条真实道路（§九"不能继续沿用旧路线"）
        acceptedRoute = null
    }

    override fun release() {
        // 先立旗再摘监听：预热可能在 `AMapNavi.getInstance()` 里还没回来（那一步十几秒），
        // 走在这一刻的页面不该因为"还没注册"就漏掉清理 —— 让预热线程自己收尾。
        released = true
        val l = listener ?: return
        listener = null
        pending?.cancel()
        pending = null
        // 引擎实例随页面离场：`AMapNavi` 是进程单例，下一张卡片会另起一个引擎，
        // 这份记录留在旧实例里只会变成"对不上也说不清"的第二个真相
        acceptedRoute = null
        runCatching { instance()?.removeAMapNaviListener(l) }
        // 不调 AMapNavi.destroy()：它是进程单例，页面退出就销毁会把下一次算路一起带走。
        // 需要彻底回收时由应用级生命周期负责，不在这一层。
    }

    private fun instance(): AMapNavi? =
        runCatching { AMapNavi.getInstance(context) }.getOrNull()

    private companion object {
        /** 没配 key、无网、离线区域三种情况高德都会**静默不回调**，所以等待必须有上限 */
        const val CALC_TIMEOUT_MS = 20_000L

        /**
         * 等预热把监听挂上的上限。正常情况下它只覆盖"实例已取到、只是挂监听的几毫秒"，
         * 所以给 5 秒已经很宽；给不出结果就是真出了问题，宁可如实拒绝也不无限等。
         */
        const val WARMUP_WAIT_MS = 5_000L

        /**
         * 算路策略取「躲避拥堵」，理由不是偏好，是取证：
         * `javap -v` 扫过实际解析到的 10.0.600 jar，`DRIVING_DEFAULT` / `DRIVING_SHORTEST_DISTANCE`
         * 带着 `Deprecated` 属性（高德已弃用旧单路径默认值），而 `DRIVING_AVOID_CONGESTION`(=4) 没有。
         * 也**不**取 `DRIVING_MULTIPLE_ROUTES_*`：多路径要额外约定选路，`getNaviPath()` 读回的是"选中那条"，
         * 这层选择在真机上还没验证过，不引入未验证的分支。
         */
        val STRATEGY: Int = PathPlanningStrategy.DRIVING_AVOID_CONGESTION
        const val STRATEGY_NAME = "DRIVING_AVOID_CONGESTION"
    }
}

/** 算路回调的两种落点；失败必须带高德给的原因，不能只说"失败了" */
private sealed class CalcOutcome {
    data class Calculated(val code: Int) : CalcOutcome()
    data class Failed(val reason: String) : CalcOutcome()
}

/**
 * 高德路径 → 业务折线。
 *
 * `getCoordList()` 给的就是 GCJ-02（高德自身口径），所以这里**只搬运不换算**。
 * 任何再套一层 `Locator.toGcj02` / `CoordinateTransform` 的写法都是二次偏移（审计 §17 的已知坑）。
 */
private fun AMapNaviPath.geoPoints(): List<Geo> =
    coordList.orEmpty().map { Geo(it.latitude, it.longitude) }

/**
 * 「高德导航引擎初始化成功了吗」——**进程级事实**的唯一存放处。
 *
 * 为什么不能留在引擎实例里：`AMapNavi` 是进程单例（`release()` 里那句"页面退出就销毁会把下一次
 * 算路一起带走"说的就是这件事），而 `onInitNaviSuccess` **一生只回调一次**；引擎实例却随
 * 页面进出、长列表滚动反复重建。结论存实例里 ⇒ 新实例永远等一个不会再来的回调，
 * 页面把"已就绪"念成"初始化中"，导航按钮永久置灰（2026-10-09 模拟器实测复现）。
 *
 * 只由 [AmapNavigationEngine] 的初始化回调写入，别处不许碰；`null` = 确实还没回过。
 */
internal object NaviInitState {
    @Volatile
    private var result: Boolean? = null

    fun read(): Boolean? = result

    fun write(success: Boolean) {
        result = success
    }
}

/**
 * 导航类是否随包发布 —— 全项目**只有这一个**问法（内部审计 §26"同一个问题不能有第二个答案"）。
 *
 * 用字符串类名而不是 `AMapNavi::class.java`：后者在编译期就固化了引用，R8 把类裁掉时这里不会报错，
 * 于是"包里没有导航能力"会被读成"有"，正是要防的那种不实。
 */
internal fun naviClassPresent(loader: ClassLoader? = AmapNavigationEngine::class.java.classLoader): Boolean =
    runCatching { Class.forName(NAVI_CLASS_NAME, false, loader) }.isSuccess

internal const val NAVI_CLASS_NAME = "com.amap.api.navi.AMapNavi"

/** 导航引擎与合规那几处的日志标签（与 `AmapCompliance` 同一个，便于一次 `-s BUS_NAV` 取全） */
private const val NAVI_TAG = "BUS_NAV"

/**
 * 把高德原生库**提前映射进进程**，进程级只做一次，且**不在主线程做**。
 *
 * 为什么要把这一步单独拎出来（2026-10-09 实测，不是推测）：
 * - 贵的是"把 41MB 的 `libAMapSDK_NAVI` mmap + 重定位"这一段，它**不挑线程**；
 * - 而 `AMapNavi.getInstance()` 触发的初始化结果**只投递给主线程** —— 同一次安装、同一台机器的对照：
 *   放在带 Looper 的后台线程上，`onInitNaviSuccess` 永远不到（`readiness` 卡在 `Initializing`）；
 *   放回主线程，2.3 秒后回调到达、`readiness=Ready`。
 *
 * 所以拆成两段：**库在这里预先加载，单例回主线程取**（`warmUp`）。这样主线程只剩几毫秒到数百毫秒的
 * Java 侧构造，既躲开首启那种十几秒的窗口冻结（ANR），又不破坏高德的线程约定。
 *
 * 库名取自 jar 内 `lib/` 的实际文件名（`unzip -l` 核对过）；加载失败不编结论，只记日志，
 * 后面的 `warmUp` 会走 SDK 自己的慢路径 —— 慢，但仍然是真的。
 */
private object NaviNativeLoad {

    private val LIBS = listOf("AMapSDK_NAVI_v10_0_600", "neonui_shared", "neonuijni_public")

    private val lock = Any()
    private var started = false
    private var done = false
    private val waiting = mutableListOf<Runnable>()

    /**
     * 立刻返回，不阻塞调用方（调用方是主线程）。[onLoaded] 保证在库处理完之后**由主线程**触发，
     * 成功与失败都触发 —— 由 `warmUp` 自己去面对"加载没成功"这件事。
     */
    fun ensure(onLoaded: Runnable) {
        var alreadyDone = false
        var startNow = false
        synchronized(lock) {
            if (done) {
                alreadyDone = true
            } else {
                waiting += onLoaded
                startNow = !started
                if (startNow) started = true
            }
        }
        when {
            alreadyDone -> Handler(Looper.getMainLooper()).post(onLoaded)
            startNow -> Thread(::loadOnce, "buspost-navi-preload").apply { isDaemon = true }.start()
        }
    }

    private fun loadOnce() {
        val t0 = SystemClock.elapsedRealtime()
        var allOk = true
        LIBS.forEach { name ->
            runCatching { System.loadLibrary(name) }.onFailure {
                allOk = false
                Log.w(NAVI_TAG, "原生库预加载失败：$name（后续由 SDK 自己加载，会占主线程）", it)
            }
        }
        Log.i(NAVI_TAG, "原生库预加载${if (allOk) "完成" else "有失败"}，耗时 ${SystemClock.elapsedRealtime() - t0}ms（不占主线程）")
        val callbacks: List<Runnable>
        synchronized(lock) {
            done = true
            callbacks = waiting.toList()
            waiting.clear()
        }
        Handler(Looper.getMainLooper()).post { callbacks.forEach { it.run() } }
    }
}

/**
 * 引擎工厂：可用时给真引擎，不可用时给替身 —— 替身不会偷偷变成"用直线假装在导航"。
 * 判据 N-d 钉的就是"这里没有任何第三种返回"。
 */
object NavigationEngines {

    fun create(context: Context): NavigationEngine {
        val appContext = context.applicationContext
        val present = naviClassPresent()
        val keyConfigured = BuildConfig.AMAP_KEY.isNotBlank()
        if (!present || !keyConfigured) {
            return BlockedNavigationEngine(readinessOf(sdkPresent = present, keyConfigured = keyConfigured, initResult = null))
        }
        return AmapNavigationEngine(appContext).also { it.prepare() }
    }
}
