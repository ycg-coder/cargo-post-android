package com.shanxiang.buspost.ui.user

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import com.shanxiang.buspost.core.AppState
import com.shanxiang.buspost.core.loc.LocFix
import com.shanxiang.buspost.core.loc.Locator
import com.shanxiang.buspost.core.ui.BusDialogAction
import com.shanxiang.buspost.core.ui.AnimatedEnter
import com.shanxiang.buspost.core.ui.BusCard
import com.shanxiang.buspost.core.ui.BusButton
import com.shanxiang.buspost.core.ui.BusButtonVariant
import com.shanxiang.buspost.core.ui.BusDivider
import com.shanxiang.buspost.core.ui.BusIcons
import com.shanxiang.buspost.core.ui.NetworkImage
import com.shanxiang.buspost.core.ui.BusLazyPage
import com.shanxiang.buspost.core.ui.BusPullRefresh
import com.shanxiang.buspost.core.ui.BusRadius
import com.shanxiang.buspost.core.ui.BusScrollRow
import com.shanxiang.buspost.core.ui.BusTag
import com.shanxiang.buspost.core.ui.BusTextField
import com.shanxiang.buspost.core.ui.BusTone
import com.shanxiang.buspost.core.ui.EmptyState
import com.shanxiang.buspost.core.ui.ErrorState
import com.shanxiang.buspost.core.ui.LocalBusFontScale
import com.shanxiang.buspost.core.ui.LocalBusTouch
import com.shanxiang.buspost.core.ui.LocalBusTokens
import com.shanxiang.buspost.core.ui.PriceText
import com.shanxiang.buspost.core.ui.RoadLine
import com.shanxiang.buspost.core.ui.SectionTitle
import com.shanxiang.buspost.core.ui.Signpost
import com.shanxiang.buspost.core.ui.SkeletonCard
import com.shanxiang.buspost.core.ui.SkeletonLine
import com.shanxiang.buspost.core.ui.SourceTag
import com.shanxiang.buspost.core.ui.TicketCard
import com.shanxiang.buspost.ui.common.openWalkRoute
import com.shanxiang.buspost.data.model.BusLine
import com.shanxiang.buspost.data.model.WalkRoute
import com.shanxiang.buspost.data.model.SendOrder
import com.shanxiang.buspost.data.model.BusVehicle
import com.shanxiang.buspost.data.model.byUserDistance
import com.shanxiang.buspost.data.model.Geo
import com.shanxiang.buspost.data.model.Notice
import com.shanxiang.buspost.data.model.NearbyResult
import com.shanxiang.buspost.data.model.Product
import com.shanxiang.buspost.data.model.Station
import com.shanxiang.buspost.data.model.VehicleStatus
import com.shanxiang.buspost.data.model.WeatherInfo
import com.shanxiang.buspost.data.repo.Feed
import com.shanxiang.buspost.data.repo.Repo
import com.shanxiang.buspost.data.store.Prefs
import com.shanxiang.buspost.nav.Routes
import com.shanxiang.buspost.ui.common.DemoNote
import com.shanxiang.buspost.ui.common.EntryTile
import com.shanxiang.buspost.ui.common.LocalNavController
import com.shanxiang.buspost.ui.common.ScreenViewModel
import com.shanxiang.buspost.ui.common.WeatherDetailCard
import com.shanxiang.buspost.ui.common.WeatherPill

class HomeViewModel : ScreenViewModel() {
    var village by mutableStateOf(Prefs.currentVillage)
    var notices by mutableStateOf<List<Notice>>(emptyList())
    var vehicles by mutableStateOf<List<BusVehicle>>(emptyList())
    var stations by mutableStateOf<List<Station>>(emptyList())
    var products by mutableStateOf<List<Product>>(emptyList())
    /** 到车提醒（§68：数据源是 /send/page 的承运车辆字段，/send/arrangements 那个 VO 没有 ETA 键） */
    var arriving by mutableStateOf<List<SendOrder>>(emptyList())
    var weather by mutableStateOf<WeatherInfo?>(null)
    var geo by mutableStateOf<Geo?>(null)
    /** 逆地理得到的定位名（区县+乡镇），天气按 adcode 查区县级 */
    var locationName by mutableStateOf("")
    var weatherUpdateTime by mutableStateOf("")
    /** 附近公交完整结果：inService/世界时段/站点/线路都要用（区分 5 种状态） */
    var nearby by mutableStateOf<NearbyResult?>(null)
    /**
     * 线路名 → 线路 id。`/bus/nearby` 的 `lines[]` **只有 routeName，没有 id**
     * （AppBusNearbyRespVO 逐字段核对，见下面 :609 那条旧注释），而点进详情要的是 `/bus/lines` 那一份 id。
     * 首页此前因此把线路签画成一排点不动的标签（真机反馈"点不进线路详情、也收藏不了"）。
     */
    var lineIdsByName by mutableStateOf<Map<String, String>>(emptyMap())
    /** 首页"附近线路"卡：一条线一张，最多三张，其余去实时公交页看 */
    var nearbyCards by mutableStateOf<List<NearbyLineCard>>(emptyList())
    /** 附近线路总数（卡只放三张时，那句"还有多少条"要有出处） */
    var nearbyLineTotal by mutableStateOf(0)
    /** 公告是否加载失败（与"暂无公告"必须分开，否则网络故障看起来像没有公告） */
    var noticeError by mutableStateOf(false)
    /** 下拉刷新中 */
    var refreshing by mutableStateOf(false)
    /** 定位状态（对齐小程序：denied / unavailable / poorAccuracy / manual 四态） */
    var locFix by mutableStateOf(LocFix.UNAVAILABLE)
    var locationManual by mutableStateOf(false)
    var relocating by mutableStateOf(false)
    var lastSync by mutableStateOf(0L)

    fun load(context: android.content.Context) = work {
        val fix = Locator.onceFix(context)
        locFix = fix
        geo = fix.geo
        fetchAll(context, fix.geo)
    }

    /** 下拉刷新：只收指示器，不重置 loading（避免整页骨架闪一下） */
    fun refresh(context: android.content.Context) = work(reset = false) {
        val fix = Locator.onceFix(context)
        locFix = fix
        geo = fix.geo
        fetchAll(context, fix.geo, onlyBus = true)
        refreshing = false
    }

    /** 点「重新定位」：跳过缓存强制重新采一次 fix（对应小程序 relocate） */
    fun relocate(context: android.content.Context) = work(reset = false) {
        relocating = true
        try {
            val fix = Locator.onceFix(context, force = true)
            locFix = fix
            if (fix.geo != null) {
                geo = fix.geo
                locationManual = false
            }
            fetchAll(context, fix.geo)
        } finally {
            relocating = false
        }
    }

    /** 手动选点：定到用户指定的坐标（对应小程序 manualPickLocation） */
    fun pickLocation(context: android.content.Context, target: Geo, name: String) = work(reset = false) {
        geo = target
        locationManual = true
        locationName = name
        // 手动选点是"用户亲手定的这个点"，没有精度概念可言：按 100m 那一档收半径，
        // 而不是沿用上一次 GPS 的精度（上一次可能是一次很飘的 800m 定位，那会把半径放到 15km）
        fetchAll(context, target, accuracyMeters = 100.0)
    }

    private suspend fun fetchAll(
        context: android.content.Context,
        g: Geo?,
        onlyBus: Boolean = false,
        // 半径跟着**定位精度**走（与 Repo.nearbyRadius 那条三面同规则一致）：
        // 真机反馈"附近公交把很远的线路也算成附近"读到的就是这个默认值 —— 首页此前根本不传半径，
        // 于是永远按 15km 问一次（实测该半径下回 188 条线路 / 1238 个站，而 5km 内是 104 条 / 285 站）。
        accuracyMeters: Double = locFix.accuracyMeters,
    ) = coroutineScope {
        val radius = Repo.nearbyRadius(accuracyMeters)
        val nbDeferred = async { Repo.nearbyBuses(g, radius) }
        // 线路 id 只能从 /bus/lines 拿（nearby 那一份不带 id），所以首页也问这一份 —— 与实时公交页同一对取数
        val blDeferred = async { Repo.busLines(g, radius) }
        if (!onlyBus) {
            val noticesDeferred = async { Repo.notices() }
            val productsDeferred = async { Repo.products() }
            val arrDeferred = async { if (Prefs.loggedIn) Repo.sendOrders() else Feed(emptyList()) }
            val weatherDeferred = async {
                if (g == null) {
                    Triple(null, null, "")
                } else {
                    val rg = Repo.reverseGeo(g)
                    val w = Repo.weather(g, rg.adcode)
                    val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA).format(java.util.Date())
                    Triple(rg, w, time)
                }
            }

            val nb = nbDeferred.await()
            val n = noticesDeferred.await()
            val p = productsDeferred.await()
            val arr = arrDeferred.await()
            val (rg, w, time) = weatherDeferred.await()

            notices = n.items
            noticeError = n.error != null
            products = p.items.take(8)
            arriving = arr.items.filter {
                it.carrierEtaMinutes != null && it.carrierEtaMinutes in 1..45 &&
                    it.carrierLocationSource != "REAL_STALE"
            }
            if (g == null) {
                weather = null
                weatherUpdateTime = ""
            } else {
                if (rg != null && !locationManual) locationName = rg.displayName
                weather = w
                weatherUpdateTime = time
            }
            mergeDemo(n.demo, p.demo, nb.demo, arr.demo)
            nearby = nb.value
            vehicles = nb.value?.vehicles.orEmpty().byUserDistance()
            stations = nb.value?.stations.orEmpty()
            lineIdsByName = lineIdIndex(blDeferred.await().items)
            nearbyCards = buildNearbyCards(nb.value, blDeferred.await().items, g)
            nearbyLineTotal = nb.value?.lineCount ?: 0
            error = nb.error
        } else {
            val nb = nbDeferred.await()
            mergeDemo(nb.demo)
            nearby = nb.value
            vehicles = nb.value?.vehicles.orEmpty().byUserDistance()
            stations = nb.value?.stations.orEmpty()
            lineIdsByName = lineIdIndex(blDeferred.await().items)
            nearbyCards = buildNearbyCards(nb.value, blDeferred.await().items, g)
            nearbyLineTotal = nb.value?.lineCount ?: 0
            error = nb.error
        }
        lastSync = System.currentTimeMillis()
        // 报一声"首屏这一轮收场了"：闪屏的阶段③等的就是这个（缺陷 #78 第二格）。
        // 放在末尾而不是某个字段赋值处 ⇒ 个别端点失败也算收场（失败有各自的错误条，不该把用户关在闪屏外）。
        AppState.markFirstScreenLoaded()
    }

    /** 线路名 → id 的索引：空 id 的条目不进表（宁可退回不可点的标签，也不给一个点进去是空页的假可点态） */
    private fun lineIdIndex(lines: List<BusLine>): Map<String, String> =
        lines.mapNotNull { l -> if (l.id.isEmpty() || l.name.isEmpty()) null else l.name to l.id }.toMap()

    /**
     * 首页的"附近线路"卡：一条线一张，**按真实步行距离排**，只留三张。
     *
     * 先按直线远近取 4 条候选（省配额：一次冷启动最多问 4 次高德步行路线），
     * 再各问一次真实步行路线、按问到的距离重排。问不到的那条退回直线估算并如实标出来，
     * 不许把它当成"更近"挤掉别人。
     */
    private suspend fun buildNearbyCards(
        nb: NearbyResult?,
        linesWithId: List<BusLine>,
        me: Geo?,
    ): List<NearbyLineCard> {
        // 写成显式判空而不是 `nb ?: return emptyList()`：后一种形状会把"请求失败"洗成"这里没有线路"
        // （SA-d 就是钉这个的），空列表必须是"问到了、确实没有"才许出现
        if (nb == null || me == null) return emptyList()
        val r = nb
        val idByName = lineIdIndex(linesWithId)
        // 线路名 → 这条线停靠的、离我最近的那个站（nearby 的 stations[].routes 说"哪条线停这一站"）
        val stopByLine = HashMap<String, Station>()
        r.stations.filter { it.distanceMeters > 0 && it.geo.lat != 0.0 && it.geo.lng != 0.0 }
            .sortedBy { it.distanceMeters }
            .forEach { st -> st.routes.forEach { n -> if (!stopByLine.containsKey(n)) stopByLine[n] = st } }
        val pool = (r.lines + linesWithId).distinctBy { it.name }
        val candidates = pool
            .mapNotNull { l -> stopByLine[l.name]?.let { st -> Pair(l, st) } }
            .sortedBy { it.second.distanceMeters }
            .take(4)
        return coroutineScope {
            candidates.map { (line, st) ->
                async {
                    NearbyLineCard(
                        lineId = line.id.ifEmpty { idByName[line.name].orEmpty() },
                        name = line.name,
                        from = line.from,
                        to = line.to,
                        realTransit = line.dataSource.contains("REAL"),
                        stationName = st.name,
                        stationGeo = st.geo,
                        walk = Repo.walkRoute(me, st.geo),
                    )
                }
            }.awaitAll().sortedBy { it.walk.meters }.take(3)
        }
    }

    /** 附近公交状态：off 非运营 / empty 有站无车 / error 接口失败 / noloc 没位置 / data 有数据 */
    fun busState(): String {
        // 没有位置就没有"附近"：这时后端给空报文是"没算过"，不是"这里没线路"。
        // 旧写法让它落到 empty 那一支，念出「附近暂无公交线路」，
        // 而同一时刻实时公交页写着 80 条线路、1192 个站点（设备实拍 home_1 与 bus_1 前后脚）—— 两屏互相打脸。
        if (geo == null) return "noloc"
        // 有失败原因就必须报 error：旧写法先 `nearby ?: return "loading"`，而 `Repo.kt:837` 失败时回的是
        // One(null, error=…) ⇒ `error != null` 必然 `nearby == null` ⇒ 下面那行 error 判定与
        // 「网络不太好 / 重新加载」那张卡（:496-502）一起变成**死代码**，失败只表现为永远转圈的骨架屏
        // —— 真机反馈"首页没有附近公交卡片"读到的就是这一格。
        if (nearby == null && error != null) return "error"
        val r = nearby ?: return "loading"
        if (error != null && r.vehicles.isEmpty()) return "error"
        // 同上：inService 缺失不许当成"运营中"的反面（F-2 §四）；
        // 且只有后端给了服务时段才允许说"不在运营时间"，服务区外的空报文走 empty（Models 里那条注释）
        if (r.isOffServiceAttested && r.vehicles.isEmpty()) return "off"
        if (r.vehicles.isEmpty()) return "empty"
        return "data"
    }

    fun pickVillage(name: String) {
        village = name
        Prefs.currentVillage = name
    }
}

@Composable
fun HomeScreen(
    onSelectTab: (Int) -> Unit,
    vm: HomeViewModel = viewModel(),
    onLoginClick: (() -> Unit)? = null,
    guest: Boolean = false,
) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val context = LocalContext.current
    val nav = LocalNavController.current
    val unread by AppState.unread.collectAsStateWithLifecycle()
    var showVillages by remember { mutableStateOf(false) }
    var showManualPick by remember { mutableStateOf(false) }
    var showAddressBook by remember { mutableStateOf(false) }
    /** 天气详情卡是否展开（对应小程序 weatherExpanded） */
    var weatherExpanded by rememberSaveable { mutableStateOf(false) }
    var readingNotice by remember { mutableStateOf<Notice?>(null) }

    LaunchedEffect(Unit) {
        if (vm.lastSync == 0L) vm.load(context)
    }

    BusPullRefresh(
        refreshing = vm.refreshing,
        onRefresh = {
            vm.refreshing = true
            vm.refresh(context)
        },
    ) {
    BusLazyPage(spacedBy = 10.dp) {
        item {
        Signpost(
            title = "乡邮智联",
            subtitle = "客货邮 · 城乡直达",
            // 分钟数是 Haversine 直线 ÷ 25km/h 的估算（后端 GeoDistanceUtil），徽章上也要带口径，不许写成道路到站时间
            badge = vm.arriving.firstOrNull()?.carrierEtaMinutes?.let { "约 $it 分钟到车（直线估算）" } ?: "县城⇄乡村",
        ) {
            Row(
                // 这一行必须与上面的副标题拉开：'位置未取到' 自己只有 24dp 行高，可点之后
                // 系统按 48dp 下限把命中框**以文字中心为轴上下各撑开约 27px**（真机 dump 实测
                // 341..473 对副标题 302..368 ⇒ 重叠 5481px²）。贴着排就会让"点副标题"落到地址簿上。
                // 14dp 是把中心推过副标题底沿再加余量的实测值，不是审美间距。
                modifier = Modifier.padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(BusIcons.location, null, tint = t.onPrimary.copy(alpha = 0.9f), modifier = Modifier.size(15.dp))
                Text(
                    // 「拿到了坐标」和「拿到了区域名」是两件事：旧写法两头都空就统一说"位置未取到"，
                    // 于是服务区外（加州实测：fix 有效、hAcc=5m、逆地理无名字）被说成定位失败，
                    // 而下面紧接着的"附近暂无公交线路"明明是坐标算出来的结论 —— 同一屏自相矛盾。
                    // 小程序同一处分两支（index.wxml:13 `currentVillage || (locationUnavailable ? '定位不可用' : '当前位置')`）。
                    vm.locationName.ifEmpty { vm.village.ifEmpty { if (vm.locFix.available) "当前位置" else "位置未取到" } },
                    color = t.onPrimary,
                    fontSize = (13 * scale).sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clip(RoundedCornerShape(BusRadius.s))
                        .clickable { showAddressBook = true }
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                )
                Text("切换 ▾", color = t.onPrimary.copy(alpha = 0.72f), fontSize = (11 * scale).sp)
            }
            // 定位状态条（四态：权限被拒 / 不可用 / 精度低 / 已手动选点）——对齐 index.wxml:24-38
            LocationHintBar(
                fix = vm.locFix,
                manual = vm.locationManual,
                relocating = vm.relocating,
                onOpenSettings = {
                    runCatching {
                        context.startActivity(
                            android.content.Intent(
                                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                android.net.Uri.parse("package:${context.packageName}"),
                            )
                        )
                    }
                },
                onRelocate = { vm.relocate(context) },
                onManualPick = { showManualPick = true },
            )
            // 天气胶囊：一行浓缩 + 点击展开 4 日预报（小程序 .weather-pill）
            vm.weather?.let { w ->
                WeatherPill(
                    weather = w,
                    expanded = weatherExpanded,
                    locationName = vm.locationName,
                    onToggle = { weatherExpanded = !weatherExpanded },
                )
            }
            // 公告轮播融入站牌底部：不再单独占一整行，作为 Signpost 内的一条小滚动条
            if (vm.notices.isNotEmpty()) {
                NoticeCarouselStrip(vm.notices) { readingNotice = it }
            }
        }
        }

        // 天气详情卡（默认收起，点胶囊展开）：图标/湿度/风力/农事提示 + 4 日预报
        if (weatherExpanded) {
            item {
            vm.weather?.let { w ->
                WeatherDetailCard(
                    weather = w,
                    loading = vm.loading,
                    updateTime = vm.weatherUpdateTime,
                )
            }
            }
        }

        // 游客登录引导条：对标主流 App 顶部/底部轻量引导（不弹窗，降低打扰感）
        if (guest && onLoginClick != null) {
            item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(BusRadius.m))
                    .background(
                        Brush.linearGradient(
                            listOf(t.primary.copy(alpha = 0.12f), t.accent.copy(alpha = 0.08f))
                        )
                    )
                    .clickable { onLoginClick() }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(BusIcons.person, null, tint = t.primaryOnLight, modifier = Modifier.size(16.dp))
                Text(
                    "登录后可买东西、寄货、收到车提醒",
                    color = t.ink,
                    fontSize = (13 * scale).sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 8.dp).weight(1f),
                )
                Text(
                    "去登录",
                    color = t.primaryOnLight,
                    fontSize = (13 * scale).sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            }
        }

        item { DemoNote(vm.demo) }

        // 到车提醒：紧凑横条，不再用完整 BusCard 占一整块
        if (vm.arriving.isNotEmpty()) {
            item { ArrivingStrip(vm.arriving) { nav.navigate(Routes.trace(it.orderNo)) } }
        }

        // 功能入口 · 两行四宫格（对齐主流 App 快捷入口密度）
        item {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FeatureTile(BusIcons.bus, "实时公交", FeatureBg.GREEN, { nav.navigate(Routes.BUS) }, Modifier.weight(1f), 0)
            FeatureTile(BusIcons.leaf, "农产品", FeatureBg.GOLD, {
                if (guest) onLoginClick?.invoke() else onSelectTab(1)
            }, Modifier.weight(1f), 1)
            FeatureTile(BusIcons.parcel, "我的快递", FeatureBg.DEEP, {
                if (guest) onLoginClick?.invoke() else onSelectTab(2)
            }, Modifier.weight(1f), 2)
            FeatureTile(BusIcons.send, "我要寄货", FeatureBg.CLAY, {
                if (guest) onLoginClick?.invoke() else nav.navigate(Routes.SEND)
            }, Modifier.weight(1f), 3)
        }
        }
        item {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FeatureTile(BusIcons.qr, "乘车码", FeatureBg.GREEN, {
                if (guest) onLoginClick?.invoke() else nav.navigate(Routes.BUS_QR)
            }, Modifier.weight(1f), 4)
            FeatureTile(BusIcons.bell, "消息通知", FeatureBg.GOLD, {
                if (guest) onLoginClick?.invoke() else nav.navigate(Routes.NOTIFICATIONS)
            }, Modifier.weight(1f), 5)
            FeatureTile(BusIcons.wallet, "钱包", FeatureBg.DEEP, {
                if (guest) onLoginClick?.invoke() else nav.navigate(Routes.WALLET)
            }, Modifier.weight(1f), 6)
            // 收藏线路：这一格读的是本机 DataStore（后端没有收藏接口），所以**不设登录闸门**——
            // 旁边三格要 gate 是因为它们去的是账号数据。旧的"更多功能"格与"个人中心"格是同一个
            // onSelectTab(3)，属重复入口，这一格把它换掉。
            // ⚠ 合并 origin/main 时对方在同一 Row 又加了一格"扫一扫"，onClick 与本页第 4 格"乘车码"
            //    是同一个 `nav.navigate(Routes.BUS_QR)`，而全工程没有任何扫码实现
            //    （`grep -rln "BarcodeScanner|CameraX|ImageAnalysis" app/src/main` = 0 个文件）。
            //    两格同一去向 + 一个做不到的动作名，正是上面"更多功能"那一条已经砍掉过的病，
            //    所以这一格并进"乘车码"、宫格数保持 8（判据 FE-a 的目标值就是这个数）。
            FeatureTile(BusIcons.star, FAVORITE_ENTRY_LABEL, FeatureBg.CLAY, {
                nav.navigate(Routes.BUS)
            }, Modifier.weight(1f), 7)
        }
        }

        // ---------- 附近线路：一条线一张卡，首页最多三张，其余去「更多」 ----------
        // 用户两轮反馈：①"没有附近公交卡片"（当时整节散在页面流里）；②"不要合到一起，
        // 首页显示三个就行"（第一轮我把五条状态合进了一张大卡，方向反了）。
        // 现在：区块头 + 口径签在卡外，每条线路自己一张卡，卡里给的是**真实步行路线**的距离与时间。
        item {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 实时脉冲点：对标车来了/高德的"实时"指示灯
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (vm.busState() == "data") t.primary else t.textTertiary),
                )
                Text(
                    "附近公交",
                    color = t.ink,
                    fontSize = (16 * scale).sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp),
                )
                // 车辆计数（对标车来了：标题旁显示在跑车辆数）
                if (vm.busState() == "data" && vm.vehicles.isNotEmpty()) {
                    Text(
                        " ${vm.vehicles.size} 辆在跑",
                        color = t.primaryOnLight,
                        fontSize = (12 * scale).sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(Modifier.weight(1f))
                // 「文字 + 箭头」必须在同一个容器里，否则 space-between 会把中间项居中、箭头推到最右
                Row(
                    Modifier
                        .clip(RoundedCornerShape(BusRadius.pill))
                        .clickable { nav.navigate(Routes.BUS) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("更多", color = t.primaryOnLight, fontSize = (13 * scale).sp, fontWeight = FontWeight.Medium)
                    Icon(BusIcons.next, null, tint = t.primaryOnLight, modifier = Modifier.size(15.dp))
                }
            }

            val nb = vm.nearby
            if (nb != null && (vm.locationName.isNotEmpty() || vm.lastSync > 0L)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (vm.locationName.isNotEmpty()) BusTag("根据${vm.locationName}展示", tone = BusTone.NEUTRAL)
                    // 这一签说的是"附近公交这批数据本机什么时候拉到的"，所以取 lastSync。
                    // 原来挂的是 weatherUpdateTime：下拉刷新走 onlyBus=true 那一支，不写它，
                    // 于是车辆列表刷新了、时间还停在天气那一次（设备实拍 s_home2：表钟 14:21，签上 14:17）。
                    if (vm.lastSync > 0L) {
                        val syncText = java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA).format(java.util.Date(vm.lastSync))
                        BusTag("更新于 $syncText", tone = BusTone.NEUTRAL)
                    }
                }
            }
            // 班次与服务时段是"这一片"的口径（后端 nearby 只给一个 nextDepartureTime），
            // 因此挂在区块下，不塞进某一条线路的卡里冒充那一条线的班次。
            val shiftLine = listOfNotNull(
                nb?.nextDepartureTime?.takeIf { it.isNotEmpty() }?.let { "下一班 $it 发车" },
                nb?.serviceWindowText?.takeIf { it.isNotEmpty() }?.let { "服务时段 $it" },
            ).joinToString("  ·  ")
            if (shiftLine.isNotEmpty()) {
                Text(
                    shiftLine,
                    color = t.ink,
                    fontSize = (13 * scale).sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            when (vm.busState()) {
                "loading" -> SkeletonCard(rows = 2)

                // 没拿到位置：只说"没位置所以算不了附近"，不替后端下"这里没线路"的结论
                "noloc" -> BusCard(modifier = Modifier.fillMaxWidth(), border = true) {
                    EmptyState(
                        title = "还没拿到你的位置",
                        subtitle = "定位恢复后这里会列出附近的站点与线路",
                        icon = BusIcons.bus,
                        actionText = "重新定位",
                        onAction = { vm.relocate(context) },
                    )
                    // 第二条出口：室内、GPS 冷启动、系统定位服务关着时，用户不必等定位 ——
                    // 选一个附近站点当作自己的位置，附近公交立刻可用。
                    BusButton(
                        text = "选一个附近站点当作我的位置",
                        onClick = { showManualPick = true },
                        variant = BusButtonVariant.TEXT,
                        small = true,
                    )
                }

                "error" -> BusCard(modifier = Modifier.fillMaxWidth(), border = true) {
                    EmptyState(
                        title = "实时公交暂时不可用",
                        subtitle = busFailureText(vm.error),
                        icon = BusIcons.info,
                        actionText = "重新加载",
                        onAction = { vm.load(context) },
                    )
                }

                else -> {
                    // 非运营时间：如实说明，不铺"待发车"卡片（MP-11）；线路卡照给，
                    // 因为"附近有哪些线、走过去要多久"这件事跟此刻发不发车无关。
                    if (vm.busState() == "off") {
                        BusCard(modifier = Modifier.fillMaxWidth(), border = true) {
                            EmptyState(
                                title = "当前不在运营时间",
                                subtitle = listOfNotNull(
                                    nb?.serviceWindowText?.takeIf { it.isNotEmpty() }?.let { "服务时段 $it" },
                                    nb?.lineCount?.takeIf { it > 0 }?.let { "附近共 $it 条公交线路" },
                                ).joinToString("；"),
                                icon = BusIcons.clock,
                            )
                        }
                    }
                    // 在跑的车（有实时位置才出现；项目车辆开始上报后走的就是这一格）
                    vm.vehicles.take(3).forEach { v ->
                        // 真实 NearbyBus/AppBusRespVO 不带 routeId（只带 routeName，2026-09-29 逐字段核对，审计 §69）：
                        // 先按线路名查 /bus/lines 那一份 id（lineIdsByName），再退到车辆自带的 routeId，
                        // 两头都没有就是空串 —— 不给「点了没反应」的假可点态
                        val lineId = vm.lineIdsByName[v.lineName]
                            ?: vm.nearby?.lines
                                ?.firstOrNull { it.name.isNotEmpty() && it.name == v.lineName }?.id?.ifEmpty { null }
                            ?: v.routeId
                        BusCard(modifier = Modifier.fillMaxWidth(), border = true) {
                            VehicleRow(v) { if (lineId.isNotEmpty()) nav.navigate(Routes.busDetail(lineId)) }
                        }
                    }
                    if (vm.nearbyCards.isEmpty() && vm.busState() != "off") {
                        BusCard(modifier = Modifier.fillMaxWidth(), border = true) {
                            EmptyState(
                                title = if ((nb?.stations.orEmpty()).isNotEmpty())
                                    "附近有 ${nb?.stations?.size} 个公交站点"
                                else "附近暂无公交线路",
                                subtitle = "这一带还没接入实时公交位置源；班车开始上报位置后，这里直接列在跑的车。",
                                icon = BusIcons.bus,
                            )
                        }
                    }
                    vm.nearbyCards.forEach { card -> NearbyLineCardView(card) }
                    if (vm.nearbyLineTotal > vm.nearbyCards.size) {
                        Text(
                            "附近还有 ${vm.nearbyLineTotal - vm.nearbyCards.size} 条线路，点「更多」看全部",
                            color = t.textTertiary,
                            fontSize = (11 * scale).sp,
                        )
                    }
                }
            }
        }
        }

        // ---------- 推荐农产品（对齐 index.wxml:236-282：标题「优质农产品 · 村里直发」+ 更多 + 骨架/空态/横滑） ----------
        item {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(BusIcons.leaf, null, tint = t.primaryOnLight, modifier = Modifier.size(17.dp))
            Text(
                "优质农产品 · 村里直发",
                color = t.ink,
                fontSize = (16 * scale).sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 6.dp),
            )
            Spacer(Modifier.weight(1f))
            Row(
                Modifier
                    .clip(RoundedCornerShape(BusRadius.pill))
                    .clickable { onSelectTab(1) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("更多", color = t.primaryOnLight, fontSize = (13 * scale).sp, fontWeight = FontWeight.Medium)
                Icon(BusIcons.next, null, tint = t.primaryOnLight, modifier = Modifier.size(15.dp))
            }
        }
        when {
            vm.loading && vm.products.isEmpty() -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(2) {
                    Column(
                        Modifier
                            .width(128.dp)
                            .clip(RoundedCornerShape(BusRadius.card))
                            .background(t.card)
                            .padding(8.dp),
                    ) {
                        SkeletonLine(height = 88.dp)
                        SkeletonLine(height = 13.dp, width = 96.dp, modifier = Modifier.padding(top = 7.dp))
                        SkeletonLine(height = 11.dp, width = 64.dp, modifier = Modifier.padding(top = 5.dp))
                    }
                }
            }
            vm.products.isEmpty() -> EmptyState(
                title = "暂无推荐农产品",
                subtitle = "农户正在备货，过会儿再来看看村里的好货",
                icon = BusIcons.leaf,
            )
            else -> BusScrollRow {
                items(vm.products, key = { it.id }) { p ->
                    ProductMiniCard(p) {
                        if (guest) onLoginClick?.invoke() else nav.navigate(Routes.goodsDetail(p.id))
                    }
                }
            }
        }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
    }

    if (showManualPick) {
        // 手动选点：从附近站点/常用村里选一个当坐标（对齐小程序 manualPickLocation 的纠偏目的）
        val spots = vm.stations.take(10).map { it.name to it.geo }
            .filter { it.second.lat != 0.0 || it.second.lng != 0.0 }
        AlertDialog(
            onDismissRequest = { showManualPick = false },
            title = { Text("手动选择位置") },
            text = {
                Column {
                    Text(
                        "定位偏差较大时，选一个附近站点当作你的位置，附近公交与天气会按它重查。",
                        color = t.textSecondary,
                        fontSize = (12.5 * scale).sp,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    if (spots.isEmpty()) {
                        Text("附近站点还没加载出来，先刷新一次。", color = t.textTertiary, fontSize = (13 * scale).sp)
                    } else {
                        spots.forEach { (name, g) ->
                            Text(
                                name,
                                color = t.ink,
                                fontSize = (14 * scale).sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showManualPick = false
                                        vm.pickLocation(context, g, name)
                                    }
                                    .padding(vertical = 10.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = { BusDialogAction("取消", onClick = { showManualPick = false }) },
        )
    }

    // 公告正文就地可读。以前点这一条只把人丢到「消息中心」，而那一页读的是 /notification/page
    // （平台发给我的通知），跟 /notice/list（站点公告）是两张表 —— 于是接口一直有给的公告正文
    // （`Notice.content`）在用户侧**没有任何一格能读到**，反倒是司机端同一条接口能看正文。
    readingNotice?.let { n ->
        AlertDialog(
            onDismissRequest = { readingNotice = null },
            confirmButton = {
                BusButton(
                    text = "知道了",
                    onClick = { readingNotice = null },
                    variant = BusButtonVariant.PRIMARY,
                )
            },
            title = { Text(n.title, color = t.ink, fontSize = (16 * scale).sp) },
            text = {
                // 正文可滚：公告按后端列宽可以写到几百字，AlertDialog 的 text 槽不滚就是把超出部分
                // 直接剪掉 —— "能点开"不等于"能读完"，这一格的缺陷原文就是"正文无处可读"。
                Column(
                    Modifier
                        .padding(top = 4.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        n.content.ifBlank { "（这条公告没有正文）" },
                        color = t.ink,
                        fontSize = (13.5f * scale).sp,
                    )
                    if (n.time.isNotEmpty()) {
                        Text(
                            n.time,
                            color = t.textTertiary,
                            fontSize = (11 * scale).sp,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }
            },
        )
    }

    if (showVillages) {
        val names = vm.stations.map { it.name }.distinct()
        // 后端没给站点时不再用硬编码村名填空：那会让用户"选中"一个后端不存在的村庄（设备实测 16:42 加州定位点暴露）。
        AlertDialog(
            onDismissRequest = { showVillages = false },
            title = { Text("选择所在村庄") },
            text = {
                Column {
                    // 撤掉硬编码填充表之后，后端没给站点时这里会真空着（设备复拍同形）：空态要说清是接口没给，不是没有村庄。
                    if (names.isEmpty()) {
                        Text(
                            "暂无可选村庄",
                            color = t.textSecondary,
                            fontSize = (13 * scale).sp,
                            modifier = Modifier.padding(vertical = 10.dp),
                        )
                    }
                    names.take(12).forEach { name ->
                        Text(
                            name,
                            color = if (name == vm.village) t.primaryOnLight else t.ink,
                            fontSize = (14 * scale).sp,
                            fontWeight = if (name == vm.village) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.pickVillage(name)
                                    showVillages = false
                                    vm.load(context)
                                }
                                .padding(vertical = 10.dp),
                        )
                    }
                }
            },
            confirmButton = { BusDialogAction("关闭", onClick = { showVillages = false }) },
        )
    }

    // 地址簿弹窗：点击"当前位置"打开，选择地址后跳转到该地的公交查询
    if (showAddressBook) {
        AddressBookDialog(
            onDismiss = { showAddressBook = false },
            onPickAddress = { address ->
                showAddressBook = false
                nav.currentBackStackEntry?.savedStateHandle?.set("initialAddress", address)
                nav.navigate(Routes.ADDRESS_SEARCH)
            },
            onManualSearch = {
                showAddressBook = false
                nav.navigate(Routes.ADDRESS_SEARCH)
            },
        )
    }
}

/**
 * 定位状态条（四态）—— 对应小程序 index.wxml:24-38。
 * 为什么要这一条：「拿不到定位」和「附近没有公交」在界面上长得一样的话，
 * 用户会以为是服务坏了；这里把原因和可操作入口（去设置 / 重新定位 / 手动选点）摆出来。
 */
@Composable
private fun LocationHintBar(
    fix: LocFix,
    manual: Boolean,
    relocating: Boolean,
    onOpenSettings: () -> Unit,
    onRelocate: () -> Unit,
    onManualPick: () -> Unit,
) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current

    // 站牌头是深色板，小签用黑 22% 承载白字（浅色/深色主题都达标）
    @Composable
    fun Bar(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 9.dp)
                .clip(RoundedCornerShape(BusRadius.m))
                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.22f))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
    val textColor = t.onPrimary
    // 缺陷 #77：这几行状态条里的动作只有 12.5sp 一行字，命中区没撑到下限（指尖要点的是一整行文字的高度）。
    // 动作全部按 UB 那套规矩走：**视觉不放大，命中区 `defaultMinSize` 撑到 48/56dp**。
    val touch = LocalBusTouch.current

    when {
        fix.denied -> Bar {
            Icon(BusIcons.location, null, tint = textColor, modifier = Modifier.size(14.dp))
            Text(
                "开启定位后，可查看附近公交",
                color = textColor,
                fontSize = (12.5f * scale).sp,
                modifier = Modifier.weight(1f).padding(start = 7.dp),
            )
            Text(
                "去设置",
                color = textColor,
                fontSize = (12.5f * scale).sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .defaultMinSize(minHeight = touch.min)
                    .clickable(onClick = onOpenSettings).padding(horizontal = 4.dp),
            )
            Icon(BusIcons.next, null, tint = textColor, modifier = Modifier.size(14.dp))
        }

        !fix.available -> Bar {
            Icon(BusIcons.location, null, tint = textColor, modifier = Modifier.size(14.dp))
            Text(
                "定位不可用，附近公交与天气暂按县城中心展示",
                color = textColor,
                fontSize = (12.5f * scale).sp,
                modifier = Modifier.weight(1f).padding(start = 7.dp),
            )
            Text(
                "重新定位",
                color = textColor,
                fontSize = (12.5f * scale).sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .defaultMinSize(minHeight = touch.min)
                    .clickable(enabled = !relocating, onClick = onRelocate).padding(horizontal = 4.dp),
            )
        }

        fix.poorAccuracy -> Bar {
            Icon(BusIcons.warning, null, tint = textColor, modifier = Modifier.size(14.dp))
            Text(
                buildString {
                    append("定位精度较低")
                    if (fix.accuracyText.isNotEmpty()) append("（约 ${fix.accuracyText}）")
                    if (fix.veryPoorAccuracy) append("，区域名可能不准")
                    append(if (relocating) "，重新定位中…" else "，点此重新定位")
                },
                color = textColor,
                fontSize = (12 * scale).sp,
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = touch.min)
                    .padding(start = 7.dp)
                    .clickable(enabled = !relocating, onClick = onRelocate),
            )
            Text(
                "手动选择位置",
                color = textColor,
                fontSize = (12 * scale).sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .defaultMinSize(minHeight = touch.min)
                    .clickable(onClick = onManualPick).padding(horizontal = 4.dp),
            )
            Icon(BusIcons.next, null, tint = textColor, modifier = Modifier.size(14.dp))
        }

        manual -> Bar {
            Icon(BusIcons.myLocation, null, tint = textColor, modifier = Modifier.size(14.dp))
            Text(
                "已手动选择位置，点此可修改",
                color = textColor,
                fontSize = (12.5f * scale).sp,
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = touch.min)
                    .padding(start = 7.dp)
                    .clickable(onClick = onManualPick),
            )
            Icon(BusIcons.next, null, tint = textColor, modifier = Modifier.size(14.dp))
        }
        // 定位正常：不显示任何条，避免噪声
    }
}

/**
 * 紧凑公告条：放在 Signpost 底部，不单独占一整行。
 * 白底半透明 + 单行滚动，点任意位置进公告详情。
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun NoticeCarouselStrip(notices: List<Notice>, onClick: (Notice) -> Unit) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    if (notices.isEmpty()) return
    val pager = androidx.compose.foundation.pager.rememberPagerState(pageCount = { notices.size })
    LaunchedEffect(notices.size) {
        if (notices.size <= 1) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(4000)
            val next = (pager.currentPage + 1) % notices.size
            try {
                pager.animateScrollToPage(next)
            } catch (e: kotlinx.coroutines.CancellationException) {
                if (kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]?.isCancelled == true) throw e
            } catch (e: Exception) {
                android.util.Log.w("BUS_HOME_CAROUSEL_STRIP", e)
            }
        }
    }
    androidx.compose.foundation.pager.HorizontalPager(
        state = pager,
        modifier = Modifier.fillMaxWidth().height(28.dp),
    ) { page ->
        val n = notices[page]
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onClick(n) }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(BusIcons.bell, null, tint = t.onPrimary.copy(alpha = 0.7f), modifier = Modifier.size(12.dp))
            Text(
                "  ${n.title}",
                color = t.onPrimary.copy(alpha = 0.85f),
                fontSize = (12 * scale).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
        }
    }
}

/**
 * 到车提醒紧凑横条：横向滚动 ETA 芯片，不再用完整 BusCard。
 * 每个芯片显示货物名 +  ETA 分钟数，点芯片进物流详情。
 */
@Composable
private fun ArrivingStrip(arriving: List<SendOrder>, onTap: (SendOrder) -> Unit) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val scrollState = rememberScrollState()
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        arriving.take(5).forEach { order ->
            val eta = order.carrierEtaMinutes
            Row(
                Modifier
                    .clip(RoundedCornerShape(BusRadius.m))
                    .background(t.card)
                    .clickable { onTap(order) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(BusIcons.bus, null, tint = t.primary, modifier = Modifier.size(14.dp))
                Text(
                    " ${order.goodsName.ifEmpty { "货物" }}",
                    color = t.ink,
                    fontSize = (12.5 * scale).sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Box(
                    Modifier
                        .clip(RoundedCornerShape(BusRadius.pill))
                        .background(if (eta != null && eta <= 5) t.danger.copy(alpha = 0.12f) else t.primaryLight)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        eta?.let { "${it}分" } ?: " Soon",
                        color = if (eta != null && eta <= 5) t.danger else t.primary,
                        fontSize = (12 * scale).sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun NoticeCard(notice: Notice, onClick: () -> Unit) {    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    BusCard(onClick = onClick, warm = true, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(BusRadius.s))
                    .background(t.goldLight),
                contentAlignment = Alignment.Center,
            ) {
                Icon(BusIcons.bolt, null, tint = t.goldDeep, modifier = Modifier.size(15.dp))
            }
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text(
                    notice.title,
                    color = t.ink,
                    fontSize = (14 * scale).sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    notice.content,
                    color = t.textSecondary,
                    fontSize = (12 * scale).sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
                Text(
                    notice.time,
                    color = t.textTertiary,
                    fontSize = (11 * scale).sp,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun VehicleRow(v: BusVehicle, onClick: () -> Unit) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val running = v.status == VehicleStatus.RUNNING
    // 车辆卡 = 小程序 .bus-card（ticket-card 结构，index.wxml:198-232）：
    // serial（线路·班次）+ 大线路名 + 状态签 + 起终站 + 山路线 + 下一站/距离 + 来源签 + 副券 ETA
    TicketCard(
        serial = listOf(v.lineName, v.shiftCode).filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { null },
        stubValue = v.etaValueText,
        stubLabel = v.etaUnitText,
        stubColor = if (running && v.etaMinutes != null && v.etaMinutes <= 5) t.danger else t.primary,
        onClick = onClick,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    v.lineName.ifEmpty { v.plate.ifEmpty { "班车" } },
                    color = t.ink,
                    fontSize = (17 * scale).sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (v.statusLabel.isNotEmpty()) {
                    BusTag(
                        v.statusLabel,
                        tone = when (v.status) {
                            VehicleStatus.RUNNING -> BusTone.PRIMARY
                            VehicleStatus.IDLE -> BusTone.WARN
                            VehicleStatus.ARRIVED -> BusTone.OK
                            else -> BusTone.NEUTRAL
                        },
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Icon(BusIcons.location, null, tint = t.textTertiary, modifier = Modifier.size(12.dp))
                Text(
                    "${v.startStation.ifEmpty { "—" }} → ${v.endStation.ifEmpty { "—" }}",
                    color = t.textSecondary,
                    fontSize = (12 * scale).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 3.dp),
                )
            }
            RoadLine(
                progress = v.roadProgress ?: -1f,
                modifier = Modifier.padding(top = 7.dp),
            )
            // 到站/发车/已到终点是三件不同的事，文案由模型统一给（§8 单一映射点，页面不再自己拼分钟数）
            Text(
                v.etaCaptionText,
                color = t.textSecondary,
                fontSize = (12 * scale).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 5.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 5.dp)) {
                Text(
                    v.stationText,
                    color = t.textSecondary,
                    fontSize = (12 * scale).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                v.nextDistanceShortText?.let {
                    Text(
                        it,
                        color = t.textTertiary,
                        fontSize = (11 * scale).sp,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            Row(modifier = Modifier.padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BusTag(v.sourceText, tone = if (v.isReal) BusTone.OK else BusTone.NEUTRAL, dot = v.isReal)
                // 这一签说的是"到站分钟怎么算出来的"。旧写法拿 routeProvider 里找 "REAL" 判"现实公交/项目线路"，
                // 而后端 routeProvider 只会是 SCHEDULE 或真实路网标签（AMAP/OSM_*/…），"REAL" 是演示面自己造的，
                // 结果真接后端时高德线路反被打成"项目线路"。线路来源在 NearbyLine.dataSource（REAL_TRANSIT/PROJECT_TRANSIT），不是车辆上的这个键。
                // 2026-10-10 撤掉"班次 xx"签：班次号已在卡片 serial 行（线路 · 班次）里，重复展示同一信息。
                if (v.providerText.isNotEmpty()) BusTag(v.providerText, tone = BusTone.NEUTRAL)
            }
        }
    }
}

@Composable
private fun ProductMiniCard(p: Product, onClick: () -> Unit) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    Column(
        Modifier
            .width(140.dp)
            .clip(RoundedCornerShape(BusRadius.card))
            .background(t.card)
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        if (p.image.isNotEmpty()) {
            NetworkImage(
                url = p.image,
                contentDescription = null,
                width = 400.dp,
                height = 100.dp,
                modifier = Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(BusRadius.m)),
                fallbackIcon = BusIcons.leaf,
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(BusRadius.m)).background(t.track),
                contentAlignment = Alignment.Center,
            ) {
                Icon(BusIcons.leaf, null, tint = t.textTertiary, modifier = Modifier.size(32.dp))
            }
        }
        Text(
            p.name,
            color = t.ink,
            fontSize = (13 * scale).sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 7.dp),
        )
        // 产地村（对齐小程序 .product-from：pin + 村名）+ 价格行（¥价 / 单位）
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(BusIcons.location, null, tint = t.textTertiary, modifier = Modifier.size(11.dp))
            Text(
                p.fromVillage,
                color = t.textSecondary,
                fontSize = (11 * scale).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 2.dp),
            )
        }
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 3.dp)) {
            PriceText(formatPrice(p.price), size = 15)
            Text(
                "/${p.unit}",
                color = t.textTertiary,
                fontSize = (11 * scale).sp,
                modifier = Modifier.padding(start = 2.dp, bottom = 1.dp),
            )
        }
    }
}

fun formatPrice(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else String.format("%.2f", v)

/** 功能入口底色：对齐 index.wxss .green-bg/.gold-bg/.deep-bg/.clay-bg（135° 渐变方块） */
private enum class FeatureBg { GREEN, GOLD, DEEP, CLAY }

@Composable
private fun FeatureTile(
    icon: ImageVector,
    name: String,
    bg: FeatureBg,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    index: Int = 0,
) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    AnimatedEnter(index, modifier) {
        Column(
            Modifier
                .clip(RoundedCornerShape(BusRadius.m))
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size((50 * scale).dp)
                    .clip(RoundedCornerShape(BusRadius.l))
                    .background(
                        when (bg) {
                            FeatureBg.GREEN -> Brush.linearGradient(listOf(t.primary, t.accent))
                            FeatureBg.GOLD -> Brush.linearGradient(listOf(t.gold, t.goldDeep))
                            FeatureBg.DEEP -> Brush.linearGradient(listOf(t.primaryDark, t.primary))
                            FeatureBg.CLAY -> Brush.linearGradient(listOf(t.clay, t.clayDeep))
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                // 金色底上白字只有 2.25:1 —— 对齐小程序 MP-08 修正：图标用深棕 #5A4310
                Icon(
                    icon, null,
                    // 硬编码色：金色渐变 tile 上的深棕图标色，与 FeatureBg.GOLD 那块固定渐变成对（换主题也不动）
                    tint = if (bg == FeatureBg.GOLD) Color(0xFF5A4310) else Color.White,
                    modifier = Modifier.size((25 * scale).dp),
                )
            }
            Text(
                name,
                color = t.ink,
                fontSize = (14 * scale).sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 7.dp, start = 2.dp, end = 2.dp),
            )
        }
    }
}

/**
 * 地址簿弹窗：点击首页"当前位置"打开，展示用户保存的地址列表。
 * 选择某个地址后跳转到该地的公交查询页；也可以手动输入新地址。
 */
@Composable
private fun AddressBookDialog(
    onDismiss: () -> Unit,
    onPickAddress: (String) -> Unit,
    onManualSearch: () -> Unit,
) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    var addresses by remember { mutableStateOf<List<com.shanxiang.buspost.data.model.AddressItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val result = com.shanxiang.buspost.data.repo.Repo.addresses()
            addresses = result.items
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            failed = true
        }
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择地址") },
        text = {
            Column(
                Modifier
                    .height(320.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 手动输入地址入口
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(BusRadius.m))
                        .background(t.primaryLight)
                        .clickable { onManualSearch() }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(BusIcons.search, null, tint = t.primary, modifier = Modifier.size(18.dp))
                    Column(Modifier.padding(start = 8.dp).weight(1f)) {
                        Text("手动输入地址", color = t.ink, fontSize = (13 * scale).sp, fontWeight = FontWeight.Bold)
                        Text("输入任意地址查看附近公交", color = t.textTertiary, fontSize = (11 * scale).sp)
                    }
                    Icon(BusIcons.next, null, tint = t.textTertiary, modifier = Modifier.size(16.dp))
                }

                if (loading) {
                    Text("加载中…", color = t.textSecondary, fontSize = (12 * scale).sp, modifier = Modifier.padding(8.dp))
                } else if (failed) {
                    // 取失败不许念成"你没有存过地址"：那是替后端下结论。
                    Text(
                        "地址簿没取到，检查网络后重新打开这一格",
                        color = t.danger,
                        fontSize = (12 * scale).sp,
                        modifier = Modifier.padding(8.dp),
                    )
                } else if (addresses.isEmpty()) {
                    Text(
                        "暂无保存的地址，可手动输入地址查询",
                        color = t.textSecondary,
                        fontSize = (12 * scale).sp,
                        modifier = Modifier.padding(8.dp),
                    )
                } else {
                    addresses.forEach { addr ->
                        val displayAddress = addr.regionLabel + addr.detail
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(BusRadius.m))
                                .background(t.track.copy(alpha = 0.5f))
                                .clickable { onPickAddress(displayAddress) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(BusIcons.location, null, tint = t.textSecondary, modifier = Modifier.size(16.dp))
                            Column(Modifier.padding(start = 8.dp).weight(1f)) {
                                Text(addr.name, color = t.ink, fontSize = (13 * scale).sp, fontWeight = FontWeight.Medium)
                                Text(displayAddress, color = t.textTertiary, fontSize = (11 * scale).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(BusIcons.next, null, tint = t.textTertiary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { BusDialogAction("关闭", onClick = onDismiss) },
    )
}

/**
 * 首页"附近线路"卡的数据：一条线一张卡。
 *
 * [walk] 是**真实步行路线**（高德 direction/walking），不是直线距离 —— 真机实测直线 440 米的站
 * 步行要 1.24 公里 / 约 17 分钟，按直线排会挑错站、也会把要走的路说短。
 */
data class NearbyLineCard(
    val lineId: String,
    val name: String,
    val from: String,
    val to: String,
    val realTransit: Boolean,
    val stationName: String,
    val stationGeo: Geo,
    val walk: WalkRoute,
)

/**
 * 一条附近线路 = 一张卡（用户点名"不要合到一起"）。
 *
 * 卡上的距离与时间是**真实步行路线**（高德 direction/walking），不是直线：直线 440 米的站
 * 实测要走 1.24 公里 / 约 17 分钟。问不到路线时退回直线估算，并把"按直线估算"写在数字旁边。
 */
@Composable
private fun NearbyLineCardView(card: NearbyLineCard) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val nav = LocalNavController.current
    val context = LocalContext.current
    var isFav by remember(card.lineId) { mutableStateOf(Prefs.isFavoriteLine(card.lineId)) }
    var walkProblem by remember { mutableStateOf<String?>(null) }
    val openDetail = if (card.lineId.isEmpty()) null else ({ nav.navigate(Routes.busDetail(card.lineId)) })
    BusCard(
        modifier = Modifier.fillMaxWidth(),
        border = true,
        onClick = openDetail,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                card.name,
                color = t.ink,
                fontSize = (15 * scale).sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (card.lineId.isNotEmpty()) {
                Icon(
                    BusIcons.star,
                    if (isFav) "已收藏" else "收藏",
                    tint = if (isFav) t.goldDeep else t.textTertiary,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size((22 * scale).dp)
                        .clickable {
                            Prefs.toggleFavoriteLine(card.lineId)
                            isFav = !isFav
                        },
                )
            }
        }
        Text(
            (if (card.from.isNotEmpty() && card.to.isNotEmpty()) "${card.from} → ${card.to} · " else "") +
                if (card.realTransit) "现实公交" else "项目线路",
            color = t.textSecondary,
            fontSize = (12 * scale).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            "步行到「${card.stationName}」",
            color = t.ink,
            fontSize = (13 * scale).sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            card.walk.text,
            color = when (card.walk.routed) {
                null -> t.textTertiary   // 没问到：灰着说"给不出"，不演成"走不通"
                false -> t.danger        // 问到了确实没有步行路线
                else -> t.textSecondary
            },
            fontSize = (12 * scale).sp,
            modifier = Modifier.padding(top = 2.dp),
        )
        BusButton(
            text = "步行导航",
            onClick = {
                walkProblem = if (openWalkRoute(context, card.stationName, card.stationGeo)) {
                    null
                } else {
                    "这台机器打不开步行导航：既没装高德地图，也没能打开高德网页版"
                }
            },
            variant = BusButtonVariant.OUTLINE,
            small = true,
            modifier = Modifier.padding(top = 10.dp),
        )
        walkProblem?.let {
            Text(it, color = t.danger, fontSize = (12 * scale).sp, modifier = Modifier.padding(top = 6.dp))
        }
        // 编号缺失时整卡不可点：不给"点了没反应"的假可点态，并把原因写在卡上
        if (openDetail == null) {
            Text(
                "这条线还没拿到线路编号，暂时点不进详情",
                color = t.textTertiary,
                fontSize = (11 * scale).sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
