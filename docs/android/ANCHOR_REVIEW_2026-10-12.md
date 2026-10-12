# 台账锚点复核输入（2026-10-12 续七，#159 的**待人工核对清单**）

⚠ **这份表没有被采信，也没有被写进台账。** 它是 `tools/check_doc_anchors.py --strict-id` 报出的 83 条"新增可疑"锚点，
逐条拿"判给该锚点的标识符在目标文件里最近的一次出现"做的**机械候选**。生成它的人（我）在抽样复核后**否掉了这条自动化路线**，
理由见下面"反例"一节 —— 按这份表批量重锚会把**本来就对的锚点改错**。

用法（给 #159）：对每一条，先读台账那一行的原句，再看 `[旧行:…]` 现在指着什么；
只有当旧行确实不再是那句说的东西时，才把行号改成正确的行（**逐条改、改完立刻重跑判据**，不许批量替换）。

分类计数：可机械候选 72 / 文件找不到 9 / 标识符已不在该文件 2；位移分布 = (11×8, 6×6, 23×6, 7×6, 24×6, -8×4, 4×4, …)。

## 为什么不能照着这张表改（三条反例，都是本轮实测）

| 台账那句锚点 | 现在那一行的真实内容 | 机械候选把它改去哪 | 判定 |
|---|---|---|---|
| `AppState.kt:124` | `data class GuideTarget(val lat: Double, val lng: Double, val name: String…)` —— **正是台账描述的那个类** | `AppState.kt:147`（`Prefs.themeColor = value.id`，与句子无关） | 旧行是对的，ID-MISS 是**标识符误配**（`assign_tokens` 把句中别的词判给了它） |
| `Repo.kt:587` | `suspend fun sendOrders(pageNo…, status: Int?…)` —— 正是台账点名的读法 | `Repo.kt:606`（`amount = o.multiNum("totalAmount")`） | 同上 |
| `Models.kt:994` | `val carrierEtaMinutes: Int? = null,` —— 正是台账点名的可空字段 | `Models.kt:1008`（`val bizType: String = ""`） | 同上 |

结论：这本判据当前的 ID-MISS **不能当"漂移条数"读**（工具自己的文件头就把这条列为 D-③-5 的旧病：8 本台账 612 条假报）。
剩下两类是真活：① `NOFILE` 9 条（`DriverAppServiceImpl.java:1238/1482-1498/1498/1545-1546/1657`、`MultiLegServiceImpl.java:391` 等，
后端文件在 `../cargo-post-platform` 里找不到 ⇒ 要么文件被重构过、要么 `--backend` 那侧缺仓）；
② 真漂移若干（例如 `Repo.kt:351-358` 现在指着 `WeatherDay(`，而 8387929/7d8b819 之后确实整体位移过）。
这两类要**逐条人工判**，不能数个数当闸门用。

## 候选清单

OK	WorkbenchScreen.kt:795-853	旧行:[vm.routePhase,]	建议:[WorkbenchScreen.kt:681|com.shanxiang.buspost.data.model.DriverTaskSegment.group(vm.tasks)]
OK	BusScreen.kt:180	旧行:[/**]	建议:[BusScreen.kt:198|fun detailSlot(): DetailSlot = detailSlotOf(detailTarget, lines, selec]
OK	BusScreen.kt:94	旧行:[*]	建议:[BusScreen.kt:100|internal fun locationHintFor(granted: Boolean, fix: com.shanxiang.busp]
OK	AppState.kt:124	旧行:[data class GuideTarget(val lat: Double, val lng: Double, val name: Str]	建议:[AppState.kt:147|Prefs.themeColor = value.id]
OK	Models.kt:1952	旧行:[* UNKNOWN 既不能说可运，也**不许本机编一条"不可达原因"** —— 原因是后端的 `reasonCode` 给的，不是本机推的。]	建议:[Models.kt:1963|* `distanceProvider` 实际有三个取值 —— `AMAP_ROAD` / `HAVERSINE`（初始默认）/ `EUCL]
OK	Models.kt:1398	旧行:[val loaded = loadedCount ?: return null]	建议:[Models.kt:1405|name = o.multiStr("name", "driverName", "realName"),]
OK	Repo.kt:466	旧行:[throw e]	建议:[Repo.kt:472|* 用户端"创建订单"的本地幂等闸（F-2 第 6 步 · v2.1 清单第 6 项；V-1 那套 `ActionGate` 的**第二个实]
OK	Repo.kt:1064-1067	旧行:[p.demo,]	建议:[Repo.kt:1072|suspend fun driverHandovers(): Feed<HandoverTask> = try {]
OK	DriverShell.kt:39	旧行:[}) { modifier ->]	建议:[DriverShell.kt:30|val unread by AppState.driverUnread.collectAsStateWithLifecycle()]
NOFILE	DriverAppServiceImpl.java:1657	旧行:[]	建议:[]
NOFILE	DriverAppServiceImpl.java:1238	旧行:[]	建议:[]
OK	Prefs.kt:159-181	旧行:[var lastSeenNotifyId: Long]	建议:[Prefs.kt:151|set(v) = sp.edit().putLong("driverShiftId", v).apply()]
NOFILE	DriverAppServiceImpl.java:1498	旧行:[]	建议:[]
NOFILE	MultiLegServiceImpl.java:391	旧行:[]	建议:[]
OK	DriverMessagesScreen.kt:85	旧行:[} finally {]	建议:[DriverMessagesScreen.kt:99|AppState.reportDriverUnread(]
NOFILE	DriverAppServiceImpl.java:1482-1498	旧行:[]	建议:[]
OK	Models.kt:755-757	旧行:["MIXED" -> "真实路网（分段拼接）"]	建议:[Models.kt:762|* 数字串来自 `/bus/lines` 的 Integer status，字符串来自 `/bus/nearby`。]
OK	MineScreen.kt:85-97	旧行:[load()]	建议:[MineScreen.kt:62|class MineViewModel : ScreenViewModel() {]
OK	Models.kt:1138-1141	旧行:[val parts = listOfNotNull(km, min)]	建议:[Models.kt:1149|val fromLat = o.multiNumOrNull("fromLatitude")]
OK	Models.kt:1153	旧行:[return TransportLeg(]	建议:[Models.kt:1164|etaMinutes = o.multiNumOrNull("etaMinutes")?.toInt(),]
OK	HomeScreen.kt:162	旧行:[val n = Repo.notices()]	建议:[HomeScreen.kt:168|// /send/page 又只走 fillCarrierBatch（`etaMinutes` 只在 /send/track 的 fillE]
OK	Repo.kt:590	旧行:[} catch (e: CancellationException) {]	建议:[Repo.kt:577|// 不标来源，`ParcelTrace` 就会拿货运表去解释商城的 3（"已取消" vs "已发车"）。]
NOFILE	DriverAppServiceImpl.java:1545-1546	旧行:[]	建议:[]
OK	MinePages.kt:171	旧行:[try {]	建议:[MinePages.kt:139|fun uploadAvatar(context: android.content.Context, uri: android.net.Ur]
OK	Repo.kt:696-697	旧行:[/**]	建议:[Repo.kt:714|val track = geoListOf(o.multiArr("track", "polyline", "points"))]
OK	MineScreen.kt:164	旧行:[Stat("地址", vm.addressCount)]	建议:[MineScreen.kt:176|nav.navigate(Routes.ORDERS)]
OK	DriverShell.kt:39	旧行:[}) { modifier ->]	建议:[DriverShell.kt:43|1 -> RoutesScreen()]
OK	Models.kt:994	旧行:[val carrierEtaMinutes: Int? = null,]	建议:[Models.kt:1008|val bizType: String = "",]
OK	Bits.kt:39	旧行:[}]	建议:[Bits.kt:45|* 注意：根据UI复刻方案，演示数据横幅已移除，但保留DemoData.kt作为离线兜底。]
OK	MinePages.kt:171	旧行:[try {]	建议:[MinePages.kt:216|password.length < 4 || password.length > 16 -> "密码长度需为 4-16 位"]
OK	Repo.kt:587	旧行:[suspend fun sendOrders(pageNo: Int = 1, pageSize: Int = 20, status: In]	建议:[Repo.kt:606|amount = o.multiNum("totalAmount"),]
OK	BusPostRoot.kt:138	旧行:[popUpTo(0) { inclusive = true }]	建议:[BusPostRoot.kt:161|// 旧 DriverNavScreen 转 DEBUG ONLY（DECISION_LOG D-003 / 任务 #51）：正式包连路由都]
NOFILE	DriverAppServiceImpl.java:1657	旧行:[]	建议:[]
OK	Models.kt:755	旧行:["MIXED" -> "真实路网（分段拼接）"]	建议:[Models.kt:762|* 数字串来自 `/bus/lines` 的 Integer status，字符串来自 `/bus/nearby`。]
OK	Repo.kt:616	旧行:[]	建议:[Repo.kt:605|pickupCode = o.multiStr("pickupCode"),]
OK	Bits.kt:39	旧行:[}]	建议:[Bits.kt:45|* 注意：根据UI复刻方案，演示数据横幅已移除，但保留DemoData.kt作为离线兜底。]
OK	MinePages.kt:171	旧行:[try {]	建议:[MinePages.kt:216|password.length < 4 || password.length > 16 -> "密码长度需为 4-16 位"]
OK	Repo.kt:351-358	旧行:[WeatherDay(]	建议:[Repo.kt:324|openMeteoWeather(geo)?.let { return it }]
OK	BusPostRoot.kt:138	旧行:[popUpTo(0) { inclusive = true }]	建议:[BusPostRoot.kt:161|// 旧 DriverNavScreen 转 DEBUG ONLY（DECISION_LOG D-003 / 任务 #51）：正式包连路由都]
OK	Repo.kt:725	旧行:[label = it.multiStr("label", "title", "statusLabel", "nodeName"),]	建议:[Repo.kt:749|dataSource = o.multiStr("carrierLocationSource", "dataSource"),]
OK	Repo.kt:740	旧行:[goodsName = o.multiStr("goodsName", "cargoName"),]	建议:[Repo.kt:764|carrierTaskStatus = o.multiStr("carrierTaskStatus"),]
OK	Models.kt:1398	旧行:[val loaded = loadedCount ?: return null]	建议:[Models.kt:1405|name = o.multiStr("name", "driverName", "realName"),]
OK	DriverShell.kt:38	旧行:[BusTabBar(items, selected) { selected = it }]	建议:[DriverShell.kt:30|val unread by AppState.driverUnread.collectAsStateWithLifecycle()]
OK	MineScreen.kt:97-98	旧行:[mobile = Prefs.mobile]	建议:[MineScreen.kt:104|* 与 `LoginViewModel.submit` 同一条纪律 —— 原来这里直接把角色写成 DRIVER 就跳壳，]
OK	BusPostRoot.kt:116	旧行:[CompositionLocalProvider(LocalNavController provides nav) {]	建议:[BusPostRoot.kt:136|AppState.setActiveDomain(AppDomain.of(role))]
OK	AppState.kt:124	旧行:[data class GuideTarget(val lat: Double, val lng: Double, val name: Str]	建议:[AppState.kt:147|Prefs.themeColor = value.id]
OK	Models.kt:1952	旧行:[* UNKNOWN 既不能说可运，也**不许本机编一条"不可达原因"** —— 原因是后端的 `reasonCode` 给的，不是本机推的。]	建议:[Models.kt:1963|* `distanceProvider` 实际有三个取值 —— `AMAP_ROAD` / `HAVERSINE`（初始默认）/ `EUCL]
OK	BusScreen.kt:94	旧行:[*]	建议:[BusScreen.kt:100|internal fun locationHintFor(granted: Boolean, fix: com.shanxiang.busp]
OK	MainActivity.kt:60	旧行:[override fun onStop() {]	建议:[MainActivity.kt:65|/** 通知点击 = 深链：把 Intent 带来的路由串交给根导航消费（前缀口径与通知侧共用 [Routes.routeFromDeepL]
OK	BusPostRoot.kt:87	旧行:[]	建议:[BusPostRoot.kt:94|deepLinkTarget(route, Prefs.role)?.let { attempt { nav.navigate(it) } ]
OK	AmapNavigationEngine.kt:357	旧行:[acceptedRoute = null]	建议:[AmapNavigationEngine.kt:340|// 正在导的会话由高德继续带着走（SDK 才是推进来源），但下一次 startGpsNavi 必须先重新算。]
OK	Models.kt:1138-1141	旧行:[val parts = listOfNotNull(km, min)]	建议:[Models.kt:1149|val fromLat = o.multiNumOrNull("fromLatitude")]
OK	Models.kt:1153	旧行:[return TransportLeg(]	建议:[Models.kt:1164|etaMinutes = o.multiNumOrNull("etaMinutes")?.toInt(),]
OK	Repo.kt:873	旧行:[*/]	建议:[Repo.kt:899|suspend fun unreadCount(): Long? = safeApiCall {]
TOKMISS	WorkbenchScreen.kt:822	旧行:[]	建议:[]
OK	Notifier.kt:88	旧行:[bigText: Boolean = false,]	建议:[Notifier.kt:80|@SuppressLint("MissingPermission")]
OK	Repo.kt:1344	旧行:[* 键里带消息 id，所以"连点两条不同消息"不会互相挡。]	建议:[Repo.kt:1369|suspend fun uploadPhoto(bytes: ByteArray, fileName: String): Ack {]
NOFILE	OAuth2TokenServiceImpl.java:64	旧行:[]	建议:[]
TOKMISS	Api.kt:330-333	旧行:[throw NetworkException(e.message ?: "网络连接失败", e)]	建议:[]
OK	BusScreen.kt:224	旧行:[}]	建议:[BusScreen.kt:228|work(reset = !silent) {]
NOFILE	SmsCodeServiceImpl.java:70-71	旧行:[]	建议:[]
OK	LoginScreen.kt:150	旧行:[// 写"6位数字"曾经是个假承诺：local/dev 的固定码是 4 位（8888），真机上就是]	建议:[LoginScreen.kt:154|placeholder = "4-6 位数字",]
OK	WorkbenchScreen.kt:573	旧行:[}]	建议:[WorkbenchScreen.kt:596|val askLocation = rememberLauncherForActivityResult(RequestPermission(]
OK	BusScreen.kt:990	旧行:[overflow = TextOverflow.Ellipsis,]	建议:[BusScreen.kt:1019|SourceTag(v.sourceText, Modifier.padding(start = 7.dp))]
OK	Notifier.kt:22-45	旧行:[object Channels {]	建议:[Notifier.kt:103|.setStyle(if (bigText) NotificationCompat.BigTextStyle().bigText(body)]
OK	AlertEngine.kt:69	旧行:[Notifier.push(]	建议:[AlertEngine.kt:20|object AlertEngine {]
OK	Repo.kt:464-476	旧行:[One(product, p.demo)]	建议:[Repo.kt:495|* 键只存**摘要**，不存明文：`MessageDigest` 走 SHA-256，收货人姓名/手机号/地址不会以可读形式]
OK	DriverShell.kt:37	旧行:[BusScaffold(bottomBar = {]	建议:[DriverShell.kt:30|val unread by AppState.driverUnread.collectAsStateWithLifecycle()]
OK	DriverShell.kt:38	旧行:[BusTabBar(items, selected) { selected = it }]	建议:[DriverShell.kt:30|val unread by AppState.driverUnread.collectAsStateWithLifecycle()]
OK	Repo.kt:869-871	旧行:[* 于是地图那句"后端未回传该线路折线"在**请求根本没成功**时也是句假话。]	建议:[Repo.kt:893|* 不是 `{"count": …}` 对象（审计 §22）。返回 null 表示"这次没读到数"（异常/键缺失），]
OK	Models.kt:1159	旧行:[toStation = o.multiStr("toStation", "toStationName", "endStation", "de]	建议:[Models.kt:1172|durationMinutes = o.multiNumOrNull("durationMinutes")?.toInt(),]
OK	Models.kt:1160	旧行:[plate = o.multiStr("plateNo", "vehiclePlate", "plate"),]	建议:[Models.kt:1171|distanceKm = o.multiNumOrNull("distanceKm"),]
OK	Models.kt:1161	旧行:[driverName = o.multiStr("driverName"),]	建议:[Models.kt:1172|durationMinutes = o.multiNumOrNull("durationMinutes")?.toInt(),]
OK	Models.kt:816	旧行:[}]	建议:[Models.kt:812|distanceKm = o.multiNumOrNull("distanceKm"),]
OK	Models.kt:819	旧行:[]	建议:[Models.kt:823|* 全为 null 时（`/bus/lines` 不下发 distanceKm）就原样保留后端顺序，不再自己按 ETA 重排。]
OK	Repo.kt:580	旧行:[throw e]	建议:[Repo.kt:558|Ack(p.demo, orderNo = o.multiStr("orderNo")) // 回执 VO 只有 id 与 orderNo（]
OK	Repo.kt:816	旧行:["$T/bus/nearby",]	建议:[Repo.kt:844|// 真实键只有 realTransitAvailable（AppBusNearbyRespVO:50 + AppBusServiceImp]
OK	Repo.kt:727	旧行:[hint = it.multiStr("hint", "description", "stationName", "remark"),]	建议:[Repo.kt:749|dataSource = o.multiStr("carrierLocationSource", "dataSource"),]
OK	Repo.kt:739	旧行:[fromProductTrace = fromProductTrace,]	建议:[Repo.kt:763|lastReportTime = o.reportTimeOf("lastReportTime", "reportTime", "gpsTi]
OK	Repo.kt:740	旧行:[goodsName = o.multiStr("goodsName", "cargoName"),]	建议:[Repo.kt:764|carrierTaskStatus = o.multiStr("carrierTaskStatus"),]
OK	Repo.kt:741	旧行:[nodes = nodes,]	建议:[Repo.kt:765|carrierTaskTime = o.reportTimeOf("carrierArrivedTime"),]
OK	Repo.kt:746	旧行:[targetStation = o.multiStr("targetStation", "deliveryStation", "statio]	建议:[Repo.kt:765|carrierTaskTime = o.reportTimeOf("carrierArrivedTime"),]
OK	Repo.kt:725	旧行:[label = it.multiStr("label", "title", "statusLabel", "nodeName"),]	建议:[Repo.kt:748|// "positionSource" 曾在这里当候选键，但两仓（后端 VO + DemoData）都没有生产者，属于自造别名，已删。]
