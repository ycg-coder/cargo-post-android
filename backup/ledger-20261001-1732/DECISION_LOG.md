# DECISION_LOG — 决策与偏离记录

> 目的：让未来的会话知道"为什么是这个样子"，避免把有意为之的选择当成 bug 改掉，也避免静默放宽硬约束。
> 格式：决策 / 背景 / 备选 / 代价 / 状态。

---

## D-000 最高级约束：真实道路（REAL ROAD）

**决策**：真实道路是本项目最高级约束，优先级高于功能完整度、开发速度、以及"与小程序行为一致"。

**Why（用户原话）**：
- "宁可没有路线，不允许假路线。"
- "宁可暂时不能导航，不允许假导航。"
- "宁可 3D 少一点，不允许 3D 里的路线是假路线。"
- "不要为了'兼容旧接口'把 euclidean 转换成 OSM。必须真实来源才行。"
- "正式 App：禁止任何：直线路线 / 两点连线 / Euclidean / Haversine 驾驶距离 / Mock polyline / Fake GPS / Fake Lane / Fake turn。"

**推论（已在代码里作为判定依据）**：
1. 小程序里存在直线兜底（`driver/routes.js:122-127`）与外部地图跳转（只有 `wx.openLocation`）。**"小程序也这么做"不构成 Android 的豁免** —— 对齐的是业务契约，不是它的假数据兜底。
2. 真实来源缺失时，UI 的正确产物是"不可用状态 + 原因 + 重试"，而不是任何形式的近似。
3. `nextStopDistanceMeters` 这类旧字段不删（避免破坏兼容），但语义降级为调试字段；真实值走 `*Road*` 新字段。

**状态**：生效，写入 `CONTEXT_LOCK.md` 第一至第三节。

---

## D-001 导航 SDK：优先 AMap，但不编造 API

**决策**：实时逐向导航首选高德导航 SDK；在确认其真实 API 之前，不写任何 `AMapNavi` 调用。

**背景**：仓库当前只声明 `com.amap.api:3dmap:10.0.600`，未见 navigation artifact。用户明确禁止"自行编造 `AMap3DModel` 之类不存在的 API"。

**备选**：
- (a) 先按想象写骨架，等编译报错再改 —— 否决，会产生假进度。
- (b) 用后端 GraphHopper 出真实 polyline + steps，先做"真实路线展示 + 外部地图交给高德" —— 采纳，作为 AMap 接入前的合法形态。
- (c) MapLibre + Valhalla 自建 —— 记为 P3 研究备用，编译困难就标 `BLOCKED`。

**代价**：在 SDK 确认前，App 内没有真正的 turn-by-turn；这是可接受的（"宁可暂时不能导航"）。

**验证方法**：`./gradlew :app:dependencies` 看 artifact 是否解析；解包 aar 后 `javap` 校验类与方法签名，再决定是否可用。

**状态**：`BLOCKED`（见 `AI_ITERATION_STATE.md` BLOCKED #2）。

---

## D-002 业务状态推进：只认"司机动作 + 后端回读"

**决策**：禁止 GPS 自动完成业务；禁止客户端本地合成 status。状态变化必须由 `POST /driver/leg/{action}`（或 depart/arrive/…）之后**回读列表**确认。

**背景**：`RoutesViewModel.action()` 现在这样写：
```kotlin
legs = legs.map { if (it.legId == leg.legId) it.copy(statusLabel = labelOf(action), status = nextStatus(leg.status, action)) else it }
```
`nextStatus` 只产出 2/3/4，而 `TransportLeg.done` 判 `status == 11`、`active in 7..10`。同时小程序 `handover.js:95-104` 用的是 1..10 的九段动作序列。**三处状态机不一致，且客户端在替后端做决定。**

**方案**：把本地 `copy(...)` 改成"提交 → 重新 `Repo.driverLegs()` 回读 → 用后端返回值渲染"；action 失败时保留原状态并显式提示（沿用 `Ack.error` 的诚实性约定）。动作序列以后端 + 小程序实测为准重写，不猜。

**代价**：每次动作多一次 GET，弱网下反馈稍慢。可接受。

**状态**：待实施（属于 P0/P1 之间的合同修正，记入审计矩阵 `CONTRACT_MISMATCH`）。

---

## D-003 旧 `DriverNavScreen` 降级为 DEBUG ONLY，不删除

**决策**：`DriverNavScreen.kt` 的假导航（`stepping` / `delay(4000)` / `next()` 循环 / `laneIcon()` 字面猜车道）不删文件，改为仅 `BuildConfig.DEBUG` 可达的兼容/演示入口，并在页面上显著标注"非真实导航"。正式导航走 `NavigationEngine → DriverMissionMap`。

**Why（用户原话）**："不要直接删除文件"。同时 `stepping` 属于自查 grep 的命中词，必须被 debug source set 或 `BuildConfig.DEBUG` 关住。

**代价**：仓库里暂时留着一条调试路径；靠访问控制与文案标注消除误用风险。

**状态**：**已实施（2026-09-30，#51 / D-032）** —— 结构与 JVM 用例级证据齐（判据 §八 ⑲ N-m…N-p + 27 条结构负控 + 8 条用例级负控）；
⚠ **闸门本身没有在真机上验过**，`NOT VERIFIED ON DEVICE` 那半条按 D-032 在册。执行明细见审计 §78。

---

## D-004 用 `BuildConfig.DEMO_ALLOWED` 而不是 product flavors（既有偏离，保留）

**决策**：演示数据闸门走 `BuildConfig.DEMO_ALLOWED`（debug 默认 true / release 硬 false），不引入 `demo`/`prod` flavors。

**背景**：flavors 会把单元测试任务改名（`testDemoDebugUnitTest`），破坏用户指定的验证命令 `:app:testDebugUnitTest`。

**代价**：同一 build type 内同时存在两种能力，靠 BuildConfig 与代码门控区分；不如 flavors 干净但可验证。

**状态**：已实施并验证（release 配置期对 http 直接失败、`DEMO_ALLOWED=false` 已从生成产物确认）。

---

## D-005 Windows/GBK：不改默认编码，只做本机临时覆盖（既有偏离，保留）

**决策**：`gradle.properties` 保持 `-Dfile.encoding=UTF-8`；本机跑测试时临时加 `-Dorg.gradle.jvmargs="... -Dfile.encoding=GBK"`。文档说明，不改代码绕过。

**背景**：工程路径含中文（`袁/Desktop/测试`），JDK 21 的 Windows 启动器按 ANSI 代码页解码 Gradle 的 UTF-8 `@argfile`，导致 worker `ClassNotFoundException: GradleWorkerMain`。用户规则："如果 Gradle wrapper 下载失败，不要修改代码来绕过。"

**代价**：本机命令多一段参数；CI（Linux）保持 UTF-8 正确值。

**状态**：已实施（`gradle.properties` 注释 + `BACKEND_INTEGRATION.md`）。

---

## D-006 后端为唯一真实业务后端，不新建 API

**决策**：以现有 GitHub 后端 `cgp-team/cargo-post-platform`（芋道框架，端口 48080）为唯一真实业务后端。不重写项目、不重新设计后端、不创建新的 Demo 功能、不删除现有 Compose UI。

**代价**：Android 侧能力上限受后端 VO 字段约束；需要新字段（如 `routeProvider`、`*Road*` 系列）时走后端小步扩展，不在客户端伪造。

**状态**：生效。

---

## D-007 `BusMap.kt` 是死代码 → 作为真地图基座复用

**决策**：新增 `ui/driver/DriverMissionMap.kt` 复用 `BusMap.kt` 已验证的 `AndroidView<MapView>` 生命周期写法（`onCreate/onResume/onPause/onDestroy` + `DisposableEffect`、`addPolyline`、`addMarker`、`LatLngBounds` 自适应），而不是另起一套地图封装。

**背景**：`BusMap` 零调用点，页面全在用自绘 canvas（`MapCanvas.kt`）。它的 `KIND_BUS_SIM`（模拟车）语义在正式链路里必须消失或改名，因为"模拟车辆位置"与真实道路展示混在一起会误导司机。

**代价**：需要清理 `KIND_BUS_SIM` 语义；改动集中在地图层。

**状态**：待实施（P1-1）。

---

## D-008 坐标系只有一个入口

**决策**：后端下发的路线/点位是 GCJ-02（后端文档对 `AppDriverRouteRespVO` 的说明），OSM/GraphHopper 是 WGS84。所有跨系统换算必须经 `core/map/CoordinateTransform`，业务代码不得手写偏移公式。

**Why**：高德底图 + GCJ-02 点位天然对齐；把 WGS84 折线直接画到高德上会有 50–500 m 系统性偏移，那看起来像"偏航"，实际是坐标系错误 —— 属于会造成假路线的隐蔽缺陷。

**代价**：多一层显式声明 `CoordinateSystem`；换来可测试、可追责的换算。

**状态**：待实施（P0-3）。

---

## D-009 第三方开源复用需登记 NOTICE

**决策**：允许研究/复用/修改开源代码，但每个复用文件登记进 `THIRD_PARTY_NOTICES.md`（项目 / URL / License / 复用文件 / 用途），保留原始许可证，不整仓复制。

**状态**：待建文件（当前尚无第三方源码复用，仅有依赖）。
---

## D-009 折线在缺口处截断，并新增 `routeComplete`

**决策**：`/driver/route` 与后台监控折线逐段取真实道路；**遇到第一段取不到道路的段立即停止拼接**，
新增 `routeComplete=false` 表示"这条线只覆盖前 N 段"。客户端要显示"后半段待确认"，不得自行补线。

**背景**：原先跨缺口继续 `addAll`，两个站之间会连出一条笔直的斜线，图上看不出任何异常 ——
这正是"绝对不能补线"要防的情形，而且比"整条线是直线"更隐蔽。

**备选**：① 缺口处用直线连（否：假路线）；② 整条线只在完整时才下发（否：司机丢掉可用前半段）；
③ 截断 + 显式标记（选）。

**代价**：客户端与后台 UI 要处理"半条线"这一新状态。

**状态**：已实施（`buildRoutePolylineSafely` / `RouteSpine` / `PlanSegment`），回归见 `RouteGuideContractTest`。

---

## D-010 路网来源白名单：四处同步、下发大写标签

**决策**："这个来源算不算真实道路"只有一个判据集合，落在四处，新增来源必须四处一起改：
`algorithm/app/main.py::real_road_route`、`service/geo/RealRoadProviders.java`、
`core/map/RouteSource.kt`、`DispatchVisualDialog.vue::REAL_ROAD_SOURCES`。
后端对客户端下发**归一后的大写标签**（`AMAP` / `OSM_GRAPHHOPPER` / `OSM_ROAD_GRAPH` / `VALHALLA` / `MIXED`）。

**背景**：白名单化之前是三处各写各的判断：Java 只认字符串 `"amap"`（GraphHopper 结果被丢弃）、
实时公交写成 `amap ? "AMAP" : "EUCLIDEAN"`（任何非高德来源都被污名化成直线估算）、
运输段写 `... : "ESTIMATED"`（同样污名化）、后台 Vue 又只放行 `=== 'AMAP'`（改了后端标签就静默不渲染）。

**关键性质**：这是**白名单不是黑名单**。`""`、`none`、`estimated`、将来某个 provider 新起的名字，
全部按"来源未知 → 不可信"处理。理由与 D-000 相同：判"不是 euclidean"是不够的。

**代价**：漂移风险 —— 因此加了钉住"后端下发的标准标签必须全部在客户端白名单内"的用例
（`RealRoadRouteValidatorTest.后端下发的标准标签全部在白名单内`）。

**状态**：已实施，前后端 + 客户端 + 后台四处已对齐（javac status=0，Android 16+11 用例通过）。

---

## D-011 起终点同点返回 `same_point` / 0 公里，是唯一允许的"非路网里程"

**决策**：`/api/v1/route` 在起终点坐标完全相同时返回 `available=true, distanceKm=0, durationSeconds=0,
provider="same_point"` 且**不带 polyline**。

**背景**：把这条也一并返回 `available=false` 是另一种说谎 —— 0 公里不是推算值；
而返回 polyline 又没有意义（没有路可画）。Java 侧仍要求 `polyline.size() >= 2`，
所以这个响应**永远不会**被当成几何画出来。

**代价**：`same_point` 不在 `RealRoadProviders` 白名单内，所以它只被 `/distance`、ETA 这类"只要数字"的链路使用。

**状态**：已实施，回归见 `test_route_same_origin_destination_distance_zero`。

---

## D-012 后端验证路径：`javac @argfile` + ASCII junction（本机无 mvn）

**决策**：以后端"是否编译通过"以 `bash C:/temp/check-backend.sh` 的输出为准（360 源文件、`status=0`）。
不再把"mvn 不存在"当作无法验证的理由。

**背景**：`mvn` 未安装且没有 `mvnw`。曾以为只能做源码级审查（记为 UNVERIFIED）。
实测的 Windows/CJK 工具链缺陷有两个：
① javac 用系统 ANSI 代码页（GBK）解码 `@argfile`，本工作区路径含中文 → 一律乱码"找不到文件"，
解法是指 junction（`C:\m2repo`、`C:\proj`，非符号链接）让 argfile 内容全 ASCII；
② Lombok 不处理经 `-sourcepath` 隐式加载的源文件 → 必须把整模块 `.java` 显式列出，
否则报一片"找不到符号（log / getter）"。

**代价**：只覆盖 `yudao-module-transport` 主源码，不跑单测、不做打包；跨模块回归仍需 CI。

**状态**：已实施并用于本轮验证。另注：`target/classes` 里的旧产物会干扰判断，检查脚本每次清空输出目录。

---

## D-013 保留"带标注的直线估算"三处，明确划出本次清除边界

**决策**：本轮只清除**会产出几何**的直线兜底。以下三处保留，但必须如实标注：
`/api/v1/distance`（报价/时效预估矩阵）、`AppSendRouteInfoService`（寄件预览 `provider=euclidean` + warning）、
`DispatchServiceImpl` 后台调度轨迹图（EUCLIDEAN 段画虚线并标注）。

**背景**：这三条链路不下发 polyline，画不出"一条不存在的路"；而彻底清空会让未配高德 key 的开发环境
连报价里程都没有。用户要求"不要为了兼容旧接口把 euclidean 转换成 OSM"，同样意味着不能偷偷换个标签糊过去。

**代价**：审计文档必须持续说明"哪里是估算"，否则下一个人会以为全系统已经没有直线。

**状态**：有意偏离，已记入 `docs/REAL_ROAD_ROUTING_ARCHITECTURE.md` 第 1、7 节。

---

## D-014 公交线路轨迹：断线即截断，落库写真实出处；后端测试在无 mvn 环境真跑

**决策**：`AppBusServiceImpl.fetchRoutePolyline` 的逐段兜底改成"该段没有真实道路几何就停在这里"（`break`），
删除 `addFallbackPoint`；`AppBusLineRespVO.roadPolyline` 的 Schema 不再授权"前端回退到 points 直线"。
落库/读库的来源标签一律走 `RealRoadProviders`：写的时候记真实出处（AMAP / OSM_GRAPHHOPPER / MIXED），
读的时候判白名单而不是判 `"AMAP"` 字符串；**截断过的线路不落库**，只进 5 分钟内存缓存。
`DispatchServiceImpl` 预热只回读库里已有几何、不再重写，避免把 GraphHopper 几何抹成 AMAP。

**背景**：这是 P0-2"清除直线兜底"漏掉的第五个点——前两处（`MapCanvas` 补线、算法服务 euclidean 分支）改完之后，
线路详情接口仍会把两个站点的直连当成"道路轨迹"下发，而它的 VO 文档把这行为写成了规范。
用户口径：宁可少画一段，不允许把直线说成路；也**不允许把真实来源改写成别家来源**。

**为什么不落库半条线**：`transport_route.navigation_polyline` 是长期走廊，会被
`RouteCorridorService.corridor()` / `MonitoringServiceImpl` / 站间切片反复复用；
半条几何一旦入库，之后每次读都以为拿到了整条走廊，缺口就永久隐身了。

**后端测试配方**：`C:/temp/check-backend-tests.sh`（javac 编整棵 `src/test/java` + `C:/temp/RunTests.java` 用
junit-platform-launcher 执行）。要点：classpath 必须压成单行；`-cp` 前置 `C:/temp/javac-out`（主源码产物）；
类名从 `C:/proj/<module>/src/test/java/...` 剥前缀。实测 36 个测试类编译通过、309 tests 全绿。

**代价**：线路详情地图在断线时只显示前半段（没有"完整/截断"标志位——`/line-polyline` 返回裸数组，
加对象字段会同时破坏 Android `Repo.linePolyline` 与小程序 `api.js` 的既有解析）。已在 Schema 里写明语义。

**状态**：已实施并验证（4 条新回归用例 + 全模块 309 tests）。

---

## D-015 三方对齐审计定案：36 条违规分级 + 三条旧"已修"记录被推翻

**决策**：`ANDROID_PARITY_AUDIT_V2.md`（61 节 / 9 张矩阵）作为 Android↔小程序↔后端的**唯一对齐台账**。
每条非 `REAL_COMPLETE` 必须给九项（文件 / 类 / 函数 / 当前行为 / 后端真实行为 / 小程序行为 / 问题 / 修改方案 / 测试方案），
禁止"需要优化"这种第四态；不可做的记 `BLOCKED`/`ENVIRONMENT_BLOCKED`，明确不做的记"不复刻"并写理由（§48）。

**被推翻的三条既有记录**（代码为准，旧结论作废）：
1. **"`DriverNavScreen` 假导航已修"** → 未修。`grep -n "stepping\|delay(4_000\|stepIndex + 1"` 今天实测 **6 处命中**
   （`:103/:146/:180/:182/:183/:342`），工程无导航 SDK 依赖、无 `core/navigation` 目录。§24 保持 `UI_ONLY`，真导航保持 `BLOCKED`。
2. **"OrderDetailScreen 取消订单假成功"** → 不成立。该页走 `PUT /product-order/cancel?id=` 并回读服务器列表；
   真问题是 `:101` 把**真实成功**说成"已在演示模式下取消"。定级从 `UI_ONLY` 降为文案诚实性缺陷（§31）。
3. **"真地图组件已接线 / MapCanvas 零调用点"** → 说反了。零调用点的是 `BusMap.kt:88`，全站用自绘 `RouteMap`
   （`MapCanvas.kt:55`，在 TraceView / BusScreen / DriverNavScreen 均有调用）。→ §44、P1-1。

**根因判定（§49）**：三方字段命名系统性漂移，且没有任何契约守卫；`CONTRACT_MISMATCH` 之所以比崩溃更危险，
是因为它一律表现为"静默的 0 / 空 / 错标签"。其中最集中的一类是 **`CommonResult` 标量信封**（§22/§23/§52）：
`Api.toPayload` 把裸 `Long`/`String` 包成 `{"v": …}`，`Repo` 却读 `count|unreadCount` / `url|fileUrl`
→ 未读角标恒 0、上传地址恒空、交接照片实际未入库却提示成功。

**修法定契约守卫**：在 `app/src/test/resources/contract/` 放后端真实 VO 样例，测试断言"每个 `parse` 读到的键
必须出现在样例里"；命名漂移只做**加字段**，不删旧字段（沿用 `CONTEXT_LOCK.md` 的字段兼容规则）。

**`grep --include` 陷阱（写进自检）**：`--include=*.kt --include=*.xml` 会把 `build.gradle.kts` 整类排除，
所以"release 不得指向 127.0.0.1"的构建期拦截（`app/build.gradle.kts:164-172`）必须单独一条 grep 验证；
混用 `grep -vE "A" -e "B"` 会让 `A` 被当成文件名而报 No such file，全部模式都得写 `-e`。

**遗留编号问题**：`DECISION_LOG.md` 里有两个 `D-009`（坐标系入口 / 折线缺口截断）。
**不改号**——`ANDROID_PARITY_AUDIT_V2.md`、`AI_ITERATION_STATE.md`、架构文档都按 D-009 引用过缺口截断那条，
重排会制造新的对不上；新增决策从 D-015 起继续递增，引用旧条目时带上标题。

**状态**：审计完成；修复按 §58 的 P0 顺序逐条推进，一条都没有"顺带做完"。

---

## D-016 真地图接线时的四个选择：只加字段不删、顺序只认后端、无折线就只画点、真图要有 key 前置（2026-09-29）

**背景**：P1-1 要让司机端第一屏用真地图。动手前发现 `Repo.routeGuideOf` 只把后端 `stops/stations` 读成坐标，
`AppDriverRouteRespVO.Stop` 的 9 个业务字段全部丢弃 → 记为审计 **§62**（`CONTRACT_MISMATCH`）。
不修 §62 就直接接线，等于把"任务点没有顺序/动作/件数/状态"这个既有缺陷固化到地图上。

**决策 1：新增 `RouteGuide.stops`，`stations` 原样保留**。
同一个 JSON 数组的两种读法并存（坐标视图 + 业务视图），**不删字段**（`CONTEXT_LOCK.md` 第四节）。
删 `stations` 会连带打断 TraceView / BusScreen / DriverNavScreen 的既有渲染，且后端字段语义仍在使用。

**决策 2：任务点顺序只允许来自后端 `visitSequence`，渲染层不重排、不按距离排、不按 GPS 推进**。
硬规则 3「调度决定去哪里」。`MissionMarkers.stopMarkers` 里唯一允许的排序键是 `visitSequence`；
零坐标点跳过而不是补一个占位点。`actionType`/`status` 的兜底文案表**按真实枚举码建键**而不是顺序数组
（`TaskItemStatusEnum` 0..8 不连续，用顺序数组会把 8「失败」读成第 9 项）。

**决策 3：没有可信折线时，地图上只有任务点，一条示意线都不画**。
`MissionMarkers.paths()` 在 `polyline.size < 2` 或 `routeUnavailableReason != null` 时返回空列表，
页面同时打印"后端没有下发这一班的真实道路折线，地图上只有任务点，不画任何示意线。"
——这是硬规则 19/20 在 UI 层的落地，而不是靠 `RouteMap` 的旧兜底路径。

**决策 4：`DriverMissionMap` 的真图分支以 `BuildConfig.AMAP_KEY` 非空为前置**。
key 为空时 `BusMap` 只会给出空白底图 + 水印，比自绘更误导；此时回落 `RouteMap` 并保持同一套不可用文案。
真机上的 key 校验通过率仍是 `ENVIRONMENT_BLOCKED`（§57），JVM 单测不冒充渲染验证。

**取证：高德导航 SDK 确认不存在（不是"没查"）**。
对已解析的 `com.amap.api:3dmap:10.0.600` jar：`com/amap/api/navi/` **零类**，`javap com.amap.api.navi.AMapNavi` → class not found；
`BitmapDescriptorFactory` 只有 `defaultMarker()/defaultMarker(float)/fromBitmap/fromView`。
同一 jar **已捆绑** `com/amap/api/location/*`，所以定位能力可用而**不能再加** `com.amap.api:location`（duplicate class）。
后果：P1-2 必须先引入 `com.amap.api:navi-3dmap` 并逐个 javap 核对签名；拉不到就维持 `BLOCKED` + 页面"导航能力未就绪"，
**不许用 `stepping/delay(4_000L)` 的假步进顶替**（§24 的 6 处命中保持为目标 0 的证据，不许删掉自检行来"通过"）。
依据：「绝不自己假造 API」「如果 SDK 当前 API 能力不确定：必须先查当前依赖 API」。

**自检基线的两处如实修正**（写完才发现口径不同，不是新事实）：
`CONTEXT_LOCK.md` §八 ① 是**宽口径**（含 `stepping|two.?point|连线|直线`），实测 **19 行** = 4 处假步进违规 + 15 行禁止性说明/带标注值，
§55 C 是**窄口径**，实测 **0 行**；两者是不同的探针，以后引用时必须说明是哪一条。
新增的 1 行是 `DriverMissionMap.kt:166` 的注释（"接口没给里程就写'接口未给'，不拿直线距离凑"），属说明文字，不是违规调用点；
`GeoMath` 命中数保持 **19 行未增长**，唯一违规仍是 `WorkbenchScreen.kt:213`（§32）。

**验证**：`24 tests completed` / `BUILD SUCCESSFUL`（MissionMapTest + RouteGuideContractTest）、
`:app:assembleDebug :app:testDebugUnitTest :app:lintDebug` 全绿、`lint-results-debug.txt` **0 条 `: Error:`** 且新文件零 finding。
有一条用例先红：fixture 的 stops 缺经纬度被 geo 过滤丢弃，是**实现按设计生效、用例写漏**，补坐标转绿。

**状态**：§62 在 Android 侧已修并加契约断言（后端仍需在 `stops` 里真给这 9 个字段，端到端待联调）；
`BusMap` 零调用点（§44）解除；下一步 ⑤ 任务点业务化，先解决 §32 的 Haversine 到达门槛。

---

## D-017 到站不再由 GPS 放行，并与小程序有意分歧；顺带抓出 §63 的"旧直线字段当里程"（2026-09-29）

**背景**：NEXT 第 ⑤ 项（任务点业务化）要求"禁止 GPS 自动完成业务"。动手时发现工作台的「确认到达」
正好是它的镜像问题 —— 用 GPS **放行**业务：`WorkbenchScreen` 用客户端 Haversine 距离决定按钮 `enabled`，
`d < 0`（无定位）时又直接返回 true。审计 §32 记过这条，本轮按 §32 的方案修。

**决策 1：门槛整体删除，不降级成"提醒"**。
`canArrive()`、`ARRIVE_RADIUS_METERS` 常量、以及 VM 里那个从未被写入也从未被读的 `canArrive` 状态位一并删。
依据是后端事实而非偏好：`AppDriverArriveReqVO` 只收 `shiftId + stationId`，**不吃坐标、不做距离校验**；
`Repo.arrive`(`Repo.kt:947`) 发的 body 也只有 `driverId/shiftId/stationId`。
既然服务端按司机声明受理，客户端自设 50 米门槛就是"既严又不管用"的随机行为（无定位时形同虚设）。
工程里不存在"GPS 自动到站提醒"，所以没有走 §32 方案的第 ③ 分支（常量改作提醒）；将来若加提醒，**只能提示，不得改按钮可用性**。

**决策 2：这一条与小程序有意不一致，且不追求"三方对齐"**。
`workbench.js:21/759/762/775` 同样是客户端 Haversine + 50 米门槛，而这类行为在 §16/§48 已定为"参考实现自身违规、不复刻"。
对齐审计的目的是**契约与状态真值**，不是把违规也复刻一遍。已在 §32 的"本轮状态"里写明分歧与理由。

**决策 3：直线值仍可显示，但必须带标注，且只有一处出口**。
`MissionMarkers.nextStopDistanceText(roadMeters, straightMeters, targetName)` 是唯一拼装距离文案的地方：
道路值优先并写"（后端路网）"；道路值缺失时才输出"直线约 N 米（仅表示远近，不是道路里程）；道路距离接口未给"；
两者都没有就不出现任何数字。`WorkbenchScreen` 里那个 `GeoMath.distance` 调用点被收进 private
`straightDistanceToTarget()`，唯一消费者就是上面这个函数 —— §55 的 GeoMath 分类据此把 `WorkbenchScreen` 从"违规待修"改成"允许（显式标注的远近）"，
调用点数量没有减少（仍 19 行命中），**减少的是它的用途**。

**顺手抓到的新问题（审计 §63）**：`DriverNavScreen.kt:244` 把 `g.nextStationDistanceMeters` 当"剩余里程"显示，
而该字段的 KDoc 自己就写着"正式界面禁止读它"（`Models.kt:1175-1179`），`CONTEXT_LOCK.md` §四 也规定"UI 只准读 `*Road*` 系列"。
同一文件 `:279/:284`（本轮改动后是 `:280/:285`）的文案还写着"旧字段是直线值，不显示" —— 代码与自己的禁令矛盾。
处置：HUD 改读 `nextStopRoadDistanceMeters`；**字段与解析保留**（不删字段），判据用 grep：
`grep -rn "nextStationDistanceMeters" app/src/main/java/com/shanxiang/buspost/ui` → 实测 0 命中。
这条不在原 61 节清单里，是接线过程中读相邻代码发现的，已补 §63 全九项。

**判据固化（§55 新增 F/G 两组，§56 新增两行不许回潮）**：
```
grep -rn "canArrive\|ARRIVE_RADIUS_METERS" app/src/main/java                          # 空
grep -rn "nextStationDistanceMeters" .../buspost/ui                                    # 空
grep -rn "MissionStop" app/src/main/java/com/shanxiang/buspost | wc -l                # 实测 20
grep -rn "timelineOf\|\.ordered(" .../ui/driver | wc -l                               # 实测 3
```
F/G 都是**结构判据**（删除即归零，回潮立刻非零），比"我记得我删了"可靠；G 是下限不是精确值。
宽口径自检（`CONTEXT_LOCK.md` §八 ①）基线由 19 行升到 **29 行**：新增 10 行全部是
"直线/`straight`"关键字在**禁止性注释、函数名、带标注文案**里的出现，不是新的几何伪造；
假步进仍是 **4 行违规**（§24 未修，D 组判据仍是 6 处命中）。

**如实记录没做的部分**：§32 测试方案里的 **Robolectric 点击断言请求 body** 未做（工程没有 Robolectric 配置），
改用"代码事实（body 只有三个键）+ grep 判据"顶着，仍列待办；
`StopState.FAILED` 只在工作台时间线接上，`RoutesScreen`/`HandoverScreen`/`BusScreen` 仍用 `stopStateOf` 三态，
不是"全工程失败态已统一"；真机上任务点顺序与状态色的核对仍是 `ENVIRONMENT_BLOCKED`。

**验证**：`:app:testDebugUnitTest` 全量 **98 tests / 0 failures**（MissionMapTest 12→18、RouteGuideContractTest 12，两组 30）；
`:app:assembleDebug` BUILD SUCCESSFUL；`:app:lintDebug` BUILD SUCCESSFUL、`lint-results-debug.txt` **0 条 `: Error:`**。

**状态**：§32/§63 Android 侧已修并固化判据；§62 的任务点业务化（时间线）已落地，端到端待后端真实班次数据；
NEXT 下一项按用户顺序是 ⑥ 用户/司机分域复核。

---

## D-018 分域复核：司机身份只能从后端档案来，角色 / 通知 / 深链三处不再串域（2026-09-29）

**输入**：`AI_ITERATION_STATE.md` NEXT ⑥（用户/司机分域复核）+ `CONTEXT_LOCK.md` 硬规则 1
（"用户端与司机端是两个业务域，必须分开"）+ 审计 §29/§45/§47 的分域相关行。
**方法**：逐页读 Android（`ui/user`、`ui/login`、`ui/common`、`widget`、`qs`、`nav`、`notify`）与 `Repo`，
对后端 `AppDriverController` / `DriverAppService` 逐字段核，对小程序逐调用点核（不是凭印象）。

**复核结论（先说好消息）**：用户域**干净** —— `ui/user`/`ui/login`/`ui/common`/`widget`/`qs`/`nav` 里
0 个司机域读点、0 处 `Running.*` / `Prefs.driver*`（判据 P-a/P-a2 实测 0）；位置上报只在司机域
（`DriverLocationService` 只有两个司机壳调用点，`reportOnce()` 在无班次或无 driverId 时一句不发），
§45 当年担心的"上报串域"**不成立**。§29 则**仍未修**，且顺带抓出三处此前没有任何矩阵行抓到的串域（新 §64）。

**决策 1：`driverId` 走结构守卫，不走页面检查。** 新增 `Repo.driverIdParam()`，19 个司机调用点全部改走它，
未绑定（`Prefs.driverId <= 0`）直接抛 `DriverUnboundException` → `errorTextOf` 翻成 `DRIVER_UNBOUND`。
关键在**抛在参数求值之前**：`driverId=0` 的 URL 不是"发了被拦"，是**构造不出来**；
`mapOf("driverId" to driverId)` 字面量拼装实测归零，以后想绕过就得先把这行字面量写回来，判据立刻报红。
Why：19 处靠人记"这里要不要判 driverId"必然漏，而且漏了不报错、只把空列表渲染成"暂无任务"（§29 的病根）。

**决策 2：四个身份引导接口（profile / shifts / pickups / earnings）明确豁免守卫。**
它们在小程序 `api.js:313-324` 与后端都是**按 token 识别**、URL 里没有 `driverId`；
如果给它们也加守卫，未绑定时连"问后端我是谁"都发不出去，绑定永远不可能发生。
这是本条唯一容易被后人"顺手加个守卫"破坏的地方，所以 §56 的不许回潮行里写死了"**不许**给这四个加守卫"。

**决策 3：档案响应的三种情况分治，而不是一律清绑定。**
`driverId > 0` → 落盘；后端回**非演示且有条目却没有 driverId** → 视为"档案已被撤销"，清绑定；
回 `data:null` / 演示回落 / 请求失败 → 视为"**未知**"，**保留**既有绑定。
第一版我写成"只要 driverId<=0 就清"，自查时发现那等于**一次网络抖动就把真司机锁死**（且他无法自救），
遂改成三态并补了专门用例（`档案接口没有内容时保留既有司机身份`）。
Why：残留绑定是越权探测面（后端 `requireCurrentDriver`，`DriverAppService.java:91-96`，会拒），
但"未知"当"撤销"处理是把可用性问题换成安全问题，两头都不占才是对的。

**决策 4：换账号只清司机绑定，不清整个会话。** `Prefs.clearDriverBinding()`（司机域 5 个键）在 `saveSession`
写新 token 之前调用。Why：`clearSession()` 会连用户域数据一起丢，而"不退出直接登录第二个账号"路径需要丢的
只有司机身份；不清则 A 的 `driverId` 能被 B 的会话继续用来探司机接口。

**决策 5：司机通知留在司机域，且不复刻小程序的 `navigateBack()`。**
`AlertEngine` 司机分支改为 `route = Routes.DRIVER_MESSAGES`，单号写进正文（`${content}（运单 $no）`）。
小程序 `notification.js:184-188` 的 `navigateBack()` 之所以成立，是因为它已经在司机消息页里；
Android 的通知是**进程外入口**（可能冷启动），"返回"没有对象，所以必须给一个确定页。
被禁止的是原来那个 `trace/$orderNo` —— 那是村民端页面，装的是货运单 id（§41：寄货单在村民端无详情入口）。

**决策 6：Intent 深链加角色闸。** `Routes.DRIVER_ONLY = {DRIVER_NAV, DRIVER_MESSAGES, HANDOVER}`，
`BusPostRoot.deepLinkAllowed()` = `route !in DRIVER_ONLY || Prefs.role == Role.DRIVER`；不允许就留在当前页。
Why：深链绕过所有页面级守卫（守卫在页面里，路由已经把人送进来了），是"角色没切但进了司机页"的唯一通道。
不猜、不跳、不崩 —— 不去"猜测用户其实想当司机"。

**决策 7：分域判据落成脚本，且一律按内容/函数名。** `tools/check_domain_probes.sh` 打印 8 个数字并以退出码判定。
这一轮同时被 §55 C 教训过一次：`Repo.kt:2[45][0-9]` 行号白名单因文件头插入 14 行而漏放两行，窄判据从 4 涨到 6，
看起来像"新违规"其实只是漂移 —— **行号白名单会漂，内容白名单不会**。
裸词 `driverIdParam`（实测 21）而不是 `driverIdParam()`（20）：带括号漏掉声明自身 KDoc 的那次引用，
数字对不上时分不清"少一个调用点"还是"注释被删"。

**如实记录没做的部分**：
① 司机壳顶部**没有**专门的"未绑定司机档案"横幅，沿用三个 tab 各自的 `ErrorState`（原方案 §29 ②要求顶部硬状态条）；
② 工程**没有 Robolectric / Compose UI 测试**，所以 §29/§64 测试方案里写的 UI 断言全部没做，
   只做到 Repo + ViewModel 层（`DomainSplitTest` 7 例）；纯 Compose 行为（退出用户版后 20 秒内不再轮 `/driver/messages`、
   点通知落在司机消息中心、杀进程从通知栏重进不落司机壳、非司机账号点"司机模式"停在原页）
   四条**只改了代码 + grep 为绿**，真机未验证，标 `ENVIRONMENT_BLOCKED`（§57 新增行）；
③ `AlertEngine` 的用户/司机两套未读水印仍在本机 Prefs，跨设备不同步 —— 与小程序一致，不是本轮引入。

**验证（全部有输出）**：`:app:testDebugUnitTest` 全量 **105 tests / 0 failures / 0 errors**（10 个测试类；
DomainSplitTest 7、MissionMapTest 18、RealRoadRouteValidator 16、RouteGuideContract 12、Repo 11、Api 11、
DriverState 9、SendOrder 8、ProductOrder 7、LoginViewModel 6）；`:app:assembleDebug`、`:app:lintDebug`
BUILD SUCCESSFUL 且 `lint-results-debug.txt` **0 条 `: Error:`**；
`bash tools/check_domain_probes.sh` = P-a 0 / P-a2 0 / P-b 0 / P-b2 21 / P-c 2 / P-d 2 / P-e 1 / P-f 5，末行"分域判据通过"；
§55 C 窄口径 **4**、宽口径 **30**、`GeoMath` **19**、假步进 **6**（§24 仍未修）、`canArrive|ARRIVE_RADIUS_METERS` **0**、
安全 grep **0**。

**状态**：§29 与 §64 已修并固化判据；NEXT 按用户固定顺序进入 ⑦ `DriverLocationService` 稳定化（有界队列 + 失败可见）。

---

## D-019 上报不做重放队列而做"合并 + 新鲜度闸"，因为后端不给历史点盖章（2026-09-29）

**输入**：`AI_ITERATION_STATE.md` NEXT ⑦（`DriverLocationService` 稳定化：单一 GPS 源 → 队列 → 采样）+
审计 §45 的修改方案 ①②③（"有界环形队列、成功才出队、失败按序重试"）+ 硬规则 10（禁止 Fake GPS）+
用户红线 **"宁可没有上报，不允许假上报"**。

**先查后端，再决定要不要写队列 —— 这一步推翻了自己的方案。**
第一版我按 §45 原方案准备实现待发队列，动手前逐字段读了后端契约，三条事实改变了设计：
1. `AppDriverLocationReqVO.java` 只有 `driverId/shiftId/longitude/latitude/speedKmh`，**没有任何客户端时间字段**
   （§45 方案 ③ 写的"在报文里带 `locateTime`"落到这里就是**未知字段**，后端不收）；
2. `DriverAppServiceImpl.reportLocation` 用 `LocalDateTime.now()` 给每次上报**盖服务器章**，
   所以重放一个 3 分钟前的隧道点，在服务端看来是"司机此刻在这个坐标" —— **重放历史点 = 给旧位置盖新章**，
   这正是"假上报"的一种，只是它用的是真实坐标；
3. `shiftId` 已可选（后端 P1-H：`currentLegShiftId()` 按当前活跃段兜底），而 Android 侧
   `reportOnce()` 仍写死 `Running.shiftId <= 0` 就一句不发 —— 依据是 `Repo.kt` 里一句**陈旧注释**
   （"`shiftId` 必带"），实际后果是**运输段模式（无班次）的司机永远不上报**，监控上看就是车凭空消失。

**决策 1：不做待发队列，改做"合并语义"（coalescing）—— 只发最新点，并把丢掉的点如实计数给司机看。**
`LocationReportPolicy.decide()` 是无副作用的纯函数：`Send(latest, shiftId)` 或 `Hold(原因)`。
`ReportLedger.onFix()` 在两次 ack 之间每来一个新点就 `superseded += 1`（覆盖不是丢失，是被更新点取代），
`onAck()` 才归零；前台通知与工作台都打印"已丢弃 N 个未确认点"。
**代价明确记录**：弱网期间的中间点**不可恢复地丢了**，管理端轨迹的缺口仍然存在 ——
这条没有"解决"，只是从"静默丢"变成"看得见地丢"，并且不再有可能被解读为"当前实时位置"的重放假象。
Why：客户端拿不到、也不该伪造"这一点的真实发生时间"，在没有服务端时间槽的前提下，
补报比丢报更接近假上报。（§45 原方案里"先查 VO，没有就不塞未知字段"这一句是对的，据此否掉了队列本身。）

**决策 2：45 秒新鲜度闸（`MAX_FIX_AGE_MS = 45_000`），过期点直接停报并写明原因，不拿它冒充当前位置。**
5 秒采样 × 9 次连续拿不到 = 停报。`Hold` 的三种文案各有对应事实：
司机身份未绑定（该账号没有司机档案）/ 等待定位信号（室内或弱信号时常见）/
定位信号已中断 N 秒，暂停上报。`Driving` 期间这个阈值是**故意保守**的：
宁可屏幕上出现"暂停上报"，也不让管理端看到一条把 30 秒前的点当作现在的轨迹。

**决策 3：单一 GPS 源在 ⑦ 落地为"单一换算出口"。**
⑥ 复核确认上报只在司机域，本轮不动分域；动的是坐标：`Locator` 三处 `Geo(it.latitude, it.longitude)`
出口全部改走 `Locator.toGcj02`，`CoordinateTransform` 从**零生产调用点**变成 **2 个**
（`Locator` 出口 + `RealRoadRoute.gcj02Points`），实测重庆 `(29.5325,106.5765)` → `(29.529603,106.580187)`，
偏移 **480.6 m**（落在 §17 记的 150–700 m 区间内）。
`trackFixes()` 让服务层拿到的 `TrackFix` 已经是 GCJ-02；`Repo.reportLocation` 不做二次换算 ——
§17 修改方案第 3 条"不要在每个 `Repo.*` 里各转一次"至此有了可 grep 的形态（判据 L-a=2、L-b=0、L-e=0、L-h=0）。
`GeoMath.distance` 因此新增一个调用点（仅 `BuildConfig.DEBUG` 打印偏移米数），
基线 19 → **22**（14 调用 + 5 import + 1 声明 + 2 注释）；该处注释按 §55 的规则写明
"量的是**两个坐标系之间的偏移量**，只用于真机核对，不是道路里程、不是远近展示"。

**决策 4：`shiftId` 改为可选（`Long?`），无班次也上报。**
`reportLocation(shiftId: Long?, …)` 在 `shiftId <= 0` 时**不带该键**（`Api.rawExecute` 剔空值），
`LocationReportPolicy.shiftNote()` 把这件事说给司机听："上报未带班次：车辆位置照常更新，班次归属由后端按当前活跃段判定"。
用例 `本机没有班次时上报不带 shiftId 但请求照发` 钉住 body 键集合恰为 `{driverId, longitude, latitude, speedKmh}`；
另一条 `报文不许出现后端 VO 没有的字段` 反向钉住 `locateTime/clientTime/gpsTime/reportTime/time` 全部为 0 ——
**决策 1 的契约前提由测试守着**，将来后端若真给时间字段，这条测试会先红。

**顺带发现并修掉两个真 bug（都不是"优化"）**：
1. `CoordinateSystem.gcj02ToBd09` 漏了 `+0.006 / +0.0065` 两个偏移项，`gcj02 → bd09 → gcj02` 往返实测残差
   **6.5e-3 度（≈700 m）**；补上后残差 **6.3e-7 … 9.1e-7 度**（米级以下）。
   `bd09ToGcj02` 的对称写法一并核对。这条是"死代码里藏的错"：⑦ 之前 `CoordinateTransform` 没有生产调用点，
   所以它错了也没人撞上 —— 一旦变成唯一换算出口就必须先修对，测试用 `百度往返自洽` 钉住。
2. `Locator.onceFix` 在**所有定位Provider都关着**时，`locations(context).first()` 会随 flow 正常结束而抛
   `NoSuchElementException`（不是超时、不是 null），把"拿不到定位"变成崩溃。改成
   `runCatching { … }.getOrNull()`，超时/关闭统一走"退回 5 分钟内旧缓存 → 再不行 `LocFix.UNAVAILABLE`"。
   `locations()` 的 KDoc 补了一句：**正常完成意味着定位源已死**，所以消费方（服务的 `observe`）必须重订阅而不是当结束。
   服务侧据此把 `observe` 写成 `while (isActive)` + 退出后 `delay(RESTART_DELAY_MS)` 重试，并在通知里写明原因。

**新增渲染层边界（新 §65）**：`RealRoadRoute.gcj02Points` —— `points` 保留原始来源坐标与 `crs` 标签（不改数据），
`RouteRepository` 校验通过后由页面读 `gcj02Points` 画线（`Repo.routeGuideOf` 已改），
`heading` 也改在 GCJ-02 空间里算，消掉一处"拿 WGS-84 车辆点和 GCJ-02 折线比方向"的跨系比较。
这条防止的是：后端某天给 `crs=WGS84` 的真折线时，客户端把它直接画在 GCJ-02 底图上，**整条线偏 500 m 而看起来完全正常**。

**判据固化**：`tools/check_location_probes.sh`（8 个数字 + 退出码，实测 L-a 2 / L-b 0 / L-c 0 / L-d 0 / L-e 0 / L-f 0 / L-g 1 / L-h 0），
`CONTEXT_LOCK.md` §八 ⑧、§55 新 I 组、§56 两行新的不许回潮不变量（"定位出口只有一处换算"、"报文不得带客户端时间"）。
负控制实测：人为在 `Repo` 里加一处 `Geo(l.latitude…)` 构造、人为写一段手写偏移公式，L-b / L-e 都当场报红并 exit 1 ——
**判据是内容/函数名口径，不按行号**（同 §55 C、D-018 的教训）。

**如实记录没做的部分**：
① 工程**没有 Robolectric**，所以 `Locator.fixOf` / `trackFixes` / 前台服务 `observe` 重试环 / 通知文案
   全部**只有 JVM 纯函数层覆盖**（策略与账本可测，Android 定位对象不可测）；
② §45 的**持久化队列故意不做**（不是没做完）——理由见决策 1，实现的是内存合并；
   进程被杀后不补报，恢复后从最新 fix 重新开始；
③ 45 秒阈值、退避上限 60 秒、`superseded` 计数都是**本机内存值**，重启归零，不入后端；
④ 真机项（弱信号隧道进出、静止 60 秒的停报表现、OEM 网络定位是否本身已回 GCJ-02 因而二次偏移）
   标 `ENVIRONMENT_BLOCKED`，见 §57 新增三行 —— 第 ④ 条尤其重要：如果某厂商的网络定位已经是 GCJ-02，
   `Locator` 无条件换算就会**转两次**，这个风险是"单一出口"设计的代价，必须真机核对，不能凭 JVM 测试宣称安全。

**验证（全部有输出）**：`:app:assembleDebug :app:testDebugUnitTest :app:lintDebug` → **BUILD SUCCESSFUL in 58s**；
**140 tests / 0 failures / 0 errors / 0 skipped（13 个测试类）**，本轮新增三类：
`LocationReportPolicyTest` 15（三种 Hold、Send 带/不带班次、区间 3..60 与退避 20/40/60/60、单调不退、账本 4 例）、
`CoordinateTransformTest` 12（重庆实测值与 100..900 m 带宽、往返 1e-9、BD09 反解 1e-5、境外不变换、
矩形局限被钉住、`convertAll` 保序、`gcj02Points` 两系对照）、
`LocatorCoordinateTest` 6（导出即 GCJ-02、`toGcj02` 等价于 `CoordinateTransform.convert`、境外原样、
`TrackFix` 字段、age 单调钳非负、`LocFix` 四态与精度文案回归）；`DriverStateTest` 9 → **11**（上述两条报文用例）。
`lint-results-debug.txt` **0 条 `: Error:`**；`bash tools/check_location_probes.sh` 与 `bash tools/check_domain_probes.sh` 均退出码 0；
§55 C 窄口径 **4**、宽口径 **30**、`GeoMath` **22**、假步进 **6**（§24 仍未修）、`canArrive` **0**、
`ui` 包旧直线字段 **0**、`MissionStop` **20**、`timelineOf|.ordered(` **3**、`DemoData` **3 文件**、安全 grep **0**。

**状态**：§17 与 §45 Android 侧已修（§45 按后端事实改方案，代价在册）；新增 §65 记 BD-09 缺陷与渲染层坐标边界；
NEXT 按用户固定顺序进入 ⑧ AMap Navigation —— 仍受 BLOCKED #2（依赖里没有 `com/amap/api/navi/`）阻塞，不做假实现。

---

## D-020 信封标量走"按类型分通道"，未读角标收进状态所有者，缺凭证照片拦在提交前（2026-09-29）

**决策**：§22/§23/§52/§7 四条一起修，但三处的修法都**不采用审计原文给的方案**，理由如下。

1. **`Payload` 加标量通道，而不是给每个调用点补 `optJSONObject("data")` 兜底**。
   `toPayload` 里按 `data` 的**实际类型**分流：`JSONArray → arr`、`JSONObject → obj`、其余 `→ scalar`（同时仍塞 `{"v"}` 保旧读点）。
   审计方案 1 提过"统一 `unwrapData()` 包一层"，那会让 `Payload.obj` 的语义变化波及 24 个调用点；
   新增一个带默认值的构造参数，**旧调用点零改动**，风险面最小。`longOf/strOf` 先读标量、再读对象键，
   所以后端哪天把 `/unread-count` 改成对象形态，客户端不用改。
2. **角标唯一写入口设在 `AppState`，不设在 `AlertEngine`**（审计方案 ① 写的是"集中到 `AlertEngine`"）。
   `private val _unread` + `asStateFlow()`：外部**编译期**就拿不到 `.value`。入口设在某个调用方身上，
   下一个新页面/新轮询仍然绕得过去；设在状态所有者身上才是结构约束。合并语义取 **max**（不是"后写覆盖"），
   且两路都读不到时**保持上一个值**——这一条直接消灭"红点自己闪没"：**读不到 ≠ 后端说没有**。
3. **缺凭证照片 = 拦在提交前**，不是"先提交、后补照片"（审计 §7 方案 4 给的是后者）。
   依据是读到的后端事实：`HandoverServiceImpl.java:196-197` 对状态已是 `COMPLETED` 的交接**直接 return**。
   一旦先提交，这条交接就**永远补不上照片**。UI 策略必须由后端幂等性决定，而不是由"哪种交互更顺"决定。
4. **成功文案以回读为准**：提交后再 `GET /driver/handovers`，只有列表里那条**真带 photoUrl** 才说"凭证照片已入库"；
   回读失败/记录消失/照片没落库各给各的文案，**同时删除** `items.map { it.copy(status = "已交接") }`（§26/§27 的形态）。

**本轮三条"实测推翻想当然"**（记下来是因为它们都会复发）：
① 后端**不需要**最小扩展：`DriverAppServiceImpl.java:1657` 已经 `setPhotoUrl`。第一轮 grep 写的是 `photo`，
   Java 里是 `setPhotoUrl` ⇒ 未命中，差点据此写下"后端字段不够，要加字段"。**grep 词形要对准实现而不是概念**。
② `Api.rawExecute:262` 会把 **null 和空串**的 body 字段整个丢弃。测试首版断言 `"photoUrl":""` 存在于 body，
   失败后确认这是既有且正确的契约 ⇒ **改断言**（`!body.contains("photoUrl")`），不改代码迁就测试。
③ §22 的真实影响等级被低估：`uploadPhoto().url` 恒空 ⇒ `WorkbenchScreen.photoPicked` 的诚实守卫
   使**正式包的装车/妥投动作全部死锁**（一律提示"照片未上传成功，作业未提交"）。
   台账此前只记成"角标恒 0"，所以它的 P0 顺位本来被排低了。

**连带发现**：`HandoverTask.status` 读的是 `status`（后端是 0..7 的数字），界面渲染成"0/4"，
而 `isDone()` 靠中文串判"已完成"⇒ **恒不成立**，已完成的交接一直留在待办列表。改为 `statusName` 优先。
这是同一信封病根的第三处，不在原审计条目里，属 §49 那类"字段命名三方不统一"的实证样本。

**判据**：`tools/check_envelope_probes.sh`（§55 J 组、`CONTEXT_LOCK.md` §八 ⑨）
E-a 0 / E-b 3 / E-c 0 / E-d 0 / E-e 1 / E-f 1/1 / E-g 1 / E-h 1，退出码 0；负控制逐条实测会报红。
两条判据自身的问题也是实测出来的：E-e 首版数到 2（`Models.kt` 另一个模型的 `multiStr("statusName"…)` 是正当命中）、
E-g 首版数到 2（演示分支文案也含"交接未提交"四字）—— **收窄 pattern**，不把目标数改成 2 来蒙混。
E-f 用**两个数**（模型解析 + 页面渲染）：只数一头会漏"解析了没人显示"或"显示了没解析"，而这两头都构成假闭环。

**工具环境教训**：判据第一版是在 `python subprocess.run(["bash", …])` 下跑的，Windows 上那个 `bash` 解析到
`bash.exe`→WSL 转发，输出是 UTF-16 乱码、exit=1 全是假的（还顺带回写改动了 4 个主源码文件的行尾）。
⇒ **判据必须在真 Git Bash 里跑**，且不能只看退出码：要看到数字和末行中文通过语句。
还原后以 gradle 输出为唯一真值复跑：`BUILD SUCCESSFUL in 48s`、lint `0 errors, 48 warnings`、**157 tests / 0 failures（14 类）**。

**如实记录没做**：① 真机端到端（拍照→落库→刷新仍在）`ENVIRONMENT_BLOCKED`，审计 §57 新增一行，跑完前只能说"代码闭环"；
② `needPhoto` 后端不下发，"必须拍照"只能靠 Android 判类型；③ `saveAddress` 后端是 `CommonResult<Boolean>` 恒无 URL，
只加注释不加特判；④ 新发现 `NotifyMsg.orderNo` 是**幻字段**（后端只有 `orderId`）⇒ 用户通知深链 `trace/<orderNo>` 真机恒空，
涉及后端查询参数语义，单独排期不夹在本轮；⑤ `AppDriverHandoverRespVO:52` 注释与 `TransportHandoverStatusEnum` 矛盾，只登记不动后端。

**状态**：§22/§23/§52/§7（Android 侧代码 + 契约测试）完成，分层结论 **CODE COMPLETE / TEST COMPLETE / DEVICE 未验证 / E2E 未验证**。
NEXT 按用户 §16 顺序在 `CONTRACT_MISMATCH` 队列内进入 **§18 状态映射统一 + 新建 `docs/STATUS_MAPPING.md`**，
再 §25 → §28（`DriverLegStateMapper`）→ §34 → §35 → §36 → §38 → §39；导航仍排在这些之后（用户明令"不要急着做导航"）。

---

## D-021 段状态机只有一个出口，且动作表以后端 `TRANSITIONS` 反推 —— 连审计自己的建议都被它推翻（2026-09-29，⑨）

**输入**：`AI_ITERATION_STATE.md` CURRENT ⑨（§18 状态映射 + §8 建表 + §53 "不要魔法数字"）+
审计 §10/§26/§27/§28（段状态机三处不同源）+ §36（段字段名对不上）+ §39（两个必 400 写路径）+
用户 §16 顺序与红线 **"客户端只读不推"、"禁止 null → 0 作为默认业务语义"、"不要删除字段以免破坏旧兼容"**。

**核心事实（读源码，不猜）：后端有一张显式流转表，任何"客户端算下一个状态"都在跟它抢方向盘。**
`TransportLegStatusEnum.java:42-55` 的 `TRANSITIONS` 是 `Map<当前, Set<允许到达>>`，`canTransit` 同码幂等放行；
`DriverAppServiceImpl#legAction`(`:1475-1500`) 每个动作只做一次 `advanceLegStatus(到 target)`，
而 `MultiLegServiceImpl.advanceLegStatus`(`:700`) 在 `:714` **显式调用 `canTransit`**，不过就抛 `LEG_TRANSITION_ILLEGAL`
（`forceLegStatus` 同样在 `:746` 受检，"强制"不等于跳级；并发由 `:964` 的 CAS 条件更新保证）。
⇒ Android 端**没有任何资格**决定"下一个码"，本机算出来的非法码不是"体验差"，是**必然 4xx**。

**这一轮最值钱的产出是"推翻了三条台账建议"，而不是写了多少代码。**
审计 §28 的修改方案 ③ 让我照着状态表排下一步动作，其中三格**非法**（`canTransit` 逐格实测）：

| 台账/小程序原建议 | 目标码 | `TRANSITIONS` 实际允许 | 结论 |
|---|---|---|---|
| `0 → accept` | 2 | PLANNED 只有 `{1, 99}` | **非法**。0 是"已规划"，派单是调度的事；映射器给 `waitingReason="本段尚未派给你，等待调度派单"` |
| `2 → arrive-origin` | 5 | DRIVER_ACCEPTED 只有 `{3, 4, 99}` | **非法**。2 的下一步是 `navigate`（目标 4，合法） |
| `8 → complete` | 11 | ARRIVED_DESTINATION 只有 `{9, 10, 99}` | **非法**。需交接→`handover-start`；末端段不需交接→**再发一次 `arrive-dest`** 让后端转派送（`arriveDestination:1528-1530`） |

小程序 `handover.js:101` 的 `8 → complete` 同样会被服务端拒 ⇒ **这是"三方一致但一致地错"，不按它对**（对齐目标是后端契约，不是小程序）。
守卫写法：`DriverLegStateMapperTest` 把 `TRANSITIONS` **逐码抄成期望值**，对映射器给出的每个 `next` 断言 `canTransit(当前, target)`，
任何人再往里塞非法动作就红 —— 抄表而不是引用，是因为跨仓库没有编译期依赖，抄了就得靠测试对齐，这比"注释里写一句请注意"可靠。

**决策 1：段状态的读出口只有一个 `data/model/ServerStatus.kt`，动作与进度的出口只有一个 `ui/driver/DriverLegStateMapper.kt`。**
`ServerStatus.kt` 收 6 套后端枚举（货运 0..13 / 段 0..11+99 / 商城 0..3 / 审核 / 任务点 / 交接 0..7），
`fromCode(未知) → null`（不是 0）、缺键哨兵 **-1**、文案优先用后端 `statusName`、配色 `BusTone`、语义谓词 `done/active`。
`DriverLegStateMapper` 只提供 `nextActionOf / stepsOf / completedCount / waitingReasonOf`，**没有一个函数返回"下一个码"**。
被删的三张本地表：`RoutesScreen.nextStatus()` + `labelOf()` + 私有 `nextActionOf()`、`HandoverScreen.LEG_ACTIONS` + `legStep()`、
以及时间轴/卡片上两处 `done = leg.status >= 4`（那一格把"前往起点/已到达起点/装货中/运输中/交接中"全判成已完成）。
Why：同一工程里同时存在正确（`Models.kt:755` 的 `status == 11`）与错误两套判据，说明"再写一张表"不解决问题，
**得让错的表无处可写**——判据 S-a/S-c/S-la/S-lb 归零就是这条结构约束的可执行版本。

**决策 2：写动作后必须回读，回执只描述后端回读到的东西。**
`RoutesViewModel.action`：`legAction` → `Repo.driverLegs()` 整表替换；`HandoverScreen.advanceLeg`：`legAction` → `Repo.currentLeg()`。
四种分支各有文案：演示模式（"未提交后端"）/ 段读不到 / 回读同码（"可能未被接受，请下拉刷新核对"，**不写成功**）/ 回读变了。
判据 S-le=2 且"两处后面都必须紧跟回读"——只数调用点不数回读，等于给"又改成不发回读"留了后门。

**决策 3：`handover-confirm` 需要凭证照片，就不在段卡上发。**
`LegDriverAction.HANDOVER_CONFIRM(needsPhoto = true)`：卡面按钮改成"去交接列表拍照确认"（切 tab=1），
不在没有 `photoUrl` 的情况下打 `handover-confirm`。
Why：后端 `HandoverServiceImpl:196-197` 对已完成交接**幂等 return**，先提交就永远补不上照片（D-020 同一条根）。

**决策 4：§36 的"字段为空"分两类处理，不要一锅烩。**
① **键名对不上**（可修）：后端 VO 是 `legSequence/fromStationName/toStationName/plateNo`，Android 只认 `seq/fromStation/toStation/plate`
⇒ `multiStr/multiNum` 的候选名**只加不减**（旧名继续吃旧报文），另补 `handoverRequired/handoverId/handoverStatusName`。
② **契约里根本没有**（不许修成"有"）：`TransportLeg` 声明了 `navigationPolyline/fromGeo/toGeo`，但 `AppDriverLegRespVO` **从不下发段级几何**。
这不是解析 bug，是模型字段给人的错觉。处置：字段留着（不删，遵守兼容红线），**渲染路径摘掉**，
并在契约测试里正面断言"按真实 VO 报文解析后几何必须为空"，同时在后端侧建议加 `AppDriverLegRespVOTest` 防有人误加假几何。
Why：一旦在这里"用直线补一个几何"，就正好复刻 §46 的假路线；"宁可没有路线，不允许假路线"优先于"看起来完整"。

**决策 5：§39 的两个必 400 —— 一个按契约补 query，一个**否掉审计自己的方案**。**
① `AppSendController.java:127-129` 是 `@RequestParam("orderId")`：`Api.post` 加 query 重载，`confirmStationAction` 把 `orderId` 放 query。
② `WorkbenchViewModel.finish` 原来把 `"shift-$id"` 当 `legId` 发 `/driver/leg/complete`。审计建议"改从 `Repo.currentLeg()` 拿真 `legId` 再发 complete"，
**这条被否**：班次（客运域）与运输段（货运域）不是同一 id 空间，用段动作收班次违反硬规则 1，且中间段 `complete` 会被 `DriverAppServiceImpl:1545-1546` 的守卫拒。
真出口是 `POST /driver/arrive`（只收 `shiftId + stationId`，到线路最大 sequence 站时后端把执行记录收口为已完成，`:914-920`）。
末站 id 缺失时**不提交**并给文案，不拼假 id、不让"收班"按钮假装成功。
副作用：S-le 从 3→2 —— 少的那一个不是"删掉的调用点"，而是"本就不该存在的那一个"。

**这条改法带来的新约束，也在源码里读到并如实记**：`/driver/arrive` 有**防跳站**
（`DriverAppServiceImpl:900-911`，`targetSeq` 不在 `currentSeq..currentSeq+1` 内就抛 `DRIVER_STATION_ORDER_ILLEGAL`），
⇒ 没走到末站前一格就点"收班"**会被服务端拒**。这是**有意的取舍**：客户端不预先猜"现在能不能收班"
（猜一次就多一处本机真值），错误如实进 `error`，下一次 `load()` 按后端班次状态把 `Running.shiftId` 复位。
审计原方案 ④ 是"先清本机 `shiftId` 停上报，再打段动作"，实现保留"提交成功后才清本机位"——
因为先清 `shiftId` 会让 `Repo.arrive` 的身份/班次参数失去依据；**顺序以代码现状记录，不按台账设想记录**。

**判据**：`tools/check_status_probes.sh`（§55 K 组，17 条 S-*）实测
S-a 0 / S-b 0 / S-c 0 / S-c2 2 / S-c3 6 / S-d 6 / S-e 10 / S-f 0 / S-g 0 / S-h 3 / S-i 64 / S-j 4 / S-k 17 /
S-la 0 / S-lb 0 / S-lc 2 / S-ld 2 / S-le 2，退出码 0；H/I/J 三组复跑仍 exit 0，宽窄口径真实性探针仍 30 / 4 行未漂移。
"恰好"与"下限"逐条写在 §55 K 块（S-c2=2 是 Android 自有车辆枚举、不是后端码，归零反而说明有人乱塞；S-d=6 与 S-k 用例数联动）。
**判据的说明也会错**：我给 S-lc 写的预期是"映射器 + Repo + DemoData"，实测命中文件是"映射器 + DemoData"
（`Repo.legAction` 只收 `action: String` 形参、不含字面量）——数对了不等于解释对了，解释要回到那两类文件里看。

**工具环境教训（新增一条，与 D-020 同族）**：`grep "timelineOf\|\.ordered("` 这类**手写正则**在 `grep -rn` 下要写成
`grep -rnE "timelineOf|\.ordered("`；我用 BRE 的裸 `|` 复跑过，直接得到 **0**，看起来像"⑨ 把后端序号排序删掉了"，
实际是 **3** 处未变。⇒ 复跑判据时若数字"突然变好"，先用 `-E` 重跑一遍再下结论，不要把正则失效当成改进。

**如实记录没做**：① `Repo.legAction` 形参仍是 `String`（§39 建议的 `Long` 编译期守卫未做，波及两处调用点与既有测试，单独一批）；
② §36 的几何字段还在模型里、只是渲染路径摘掉，真正的段几何要改从 `Repo.routeGuide()` 取（与 §44/§46 地图接线同批）；
③ **无 Robolectric/MockWebServer** ⇒ "POST 之后紧跟一次 GET"、"query 里真的带 orderId"仍是代码读证 + grep，不是拦截器级断言（`ENVIRONMENT_BLOCKED`）；
④ 后端 `AppDriverLegRespVO` 的 `status` 注释漂移（§28 方案 ④）属后端仓库，本轮只在 Android 侧记账；
⑤ 真机未验：`legSequence` 缺失时段的排序表现、交接门禁的实际点击路径。

**状态**：§18/§8/§10/§26/§27/§28/§36/§39（Android 侧代码 + 契约测试）完成，
`:app:assembleDebug :app:testDebugUnitTest` `BUILD SUCCESSFUL`、**189 tests / 0 failures（17 类）**、lint `0 errors / 48 warnings`。
分层结论 **CODE COMPLETE / TEST COMPLETE(纯 JVM) / DEVICE 未验证 / E2E 未验证**。
NEXT 按 §16 顺序在 `CONTRACT_MISMATCH` 队列内进 **§34 可达性 + §11 `ReachabilityState`**，再 §35 → §38 → §24（假步进归零）→ #51；导航仍在这些之后。

---

## D-022 可达性只说"能不能到"，所以客户端既不能替后端编原因、也不能替用户编位置（2026-09-29，⑩）

**背景**：用户 §11 要求 `ReachabilityState`（REACHABLE / NOT_REACHABLE / UNKNOWN，"只有 REACHABLE 才能显示可以运输"），
审计 §34 记的是"站名与距离字段名/单位都不对，错误值还会写进下单报文"。这一条是下单前的**准入判定**，判错直接决定用户能不能发货，
而且旧代码把错的值**当参数提交**（`originalAddress` = 恒空的 `nearestStation`、`pickupServiceMode` = 枚举外的 `"STATION"`）。

**先读后端再动手**（用户 §5 的第三次兑现，本轮抓到**三处**台账/注释错）：`AppSendReachabilityRespVO.java:17-41` 给字段与**单位**
（`distanceKm` 是 BigDecimal **公里**(`:29`)，旧代码变量名叫 `distanceMeters` 还把后端给的公里又除了一次 1000 —— 双重 1000 倍错）；
`recommendedStation` 是**嵌套对象**（字段 `:38`、内类 `:45-56`，站名在里面而不是顶层），旧解析在顶层找站名所以永远找不到；
`distanceProvider` 真实取值是 **`HAVERSINE`（`:103` 的初始默认）| `AMAP_ROAD` | `EUCLIDEAN`（`:122` 白名单判定的两个分支）三个**，
台账与 VO 的 `@Schema`(`:34`) 都只写了两个；`reasonCode` 同样是**三个**（`ROAD_UNREACHABLE` `:55` /
`NO_SAFE_HANDOFF_POINT` `:70` 与 `:95` / `USER_LOCATION_UNREACHABLE` `:138`），台账只写了两个，
漏掉的正是"用户还没给位置"这一条 —— 而它在 UI 上最容易被说成"不可达"；
可运判定**不是一次算成**的：`:79` 先用直线半径试（`REACHABLE_RADIUS_KM = 0.3`，`:42`），拿到道路里程后 `:129`
**再用 `km <= 0.3` 重新判定**，`:138` 可运时显式把 `reasonCode` 置 null、`:139-140` 在 `DOOR_PICKUP`/`NEAREST_STATION` 里二选一。
⇒ 三值都得有如实文案，`HAVERSINE/EUCLIDEAN` 一律写"**直线估算，不是道路里程**"（硬规则：不许把直线换算成道路距离，也不许不标注）；
⇒ 客户端**不许**自己算"再远一点就可运了"，判定只读 `reachable`。

**决策**：
1. **布尔与三态分家**：`reachable` 保留成"兼容视图"（非空布尔，给旧调用点，遵守"不删字段"），
   `state: ReachabilityState` 才是界面唯一入口。**缺键 / 显式 null / 非布尔三种都落 `UNKNOWN`**，由 `multiBoolOrNull` 给哨兵 2
   —— 这是 §14（null 不是 0）在可达性上的落点：`reachable` 的 null 不是 false，塌成 false 就会替后端编一条"不可达原因"。
2. **UNKNOWN 说什么**：写"可达性暂不可用 / 后端没有返回可达性判定（**不代表车辆能到**）"，
   并且**不**输出任何"可就近上门"字样（判据 R-n2 专门数这件事，0 才算过）。
   `serviceModeText()` 在 UNKNOWN 时说"可达性未判定"，而不是旧代码的"自选站点（未做可达性判定）"——后者听着像能运。
3. **推荐站点只在"能落进可选列表"时才自动选中**（`applyRecommended` 找不到就返回 false）：
   不构造"只有 id 和名字、其它字段全默认"的 `Station` 塞进表单 —— 那是给下单报文喂一个**不存在的站点对象**。
   找不到时提示"推荐站点不在可选列表，请手动选择取货站点"，宁可让用户手选也不编站点（R-o/R-o2 钉住可达与不可达两个分支各有一处提示）。
4. **`serviceMode` 回传加了三道闸**：必须是 `ServiceModeEnum` 五码之一（`nameOf` 不认 ⇒ 空串 ⇒ 不回传）、
   必须与**本单实际取货站点**是同一个站（否则 `NEAREST_STATION` 会贴到用户自选的别的站点上，成了假口径）、
   不满足就返回 null，让 `Api.kt:269` 的空值丢键由服务端推导（`TransportOrderServiceImpl:202-203`：客户端非空才采信）。
5. **`originalAddress` 与站点彻底分家**：用户位置走 `Locator` → `Repo.reverseGeo`（`formatted` 兜 `displayName`），
   站点走 `pickupStationId`。逆地理没给文字地址时**不藏那一行**，改显示"文字地址未取到，本单只提交了经纬度"（R-p）。
   **空地址不拦提交**（与小程序 `send.js:709` 同口径，且报文里有经纬度兜底），拦的是"拿站点名顶替用户位置"（R-d=0）。
6. **演示兜底按真实契约形状给字段**（R-l=5）：`reachable:false + reasonCode + serviceMode + distanceKm + walkMinutes +
   distanceProvider + recommendedStation{...}`，并显式标 `（演示数据）`。演示态**可以**假距离，但**必须带 `HAVERSINE` 来源标注** ——
   假数据不许冒充道路里程，这是 §46 那条"宁可没有也不冒充"在可达性上的推论。

**判据的教训（与 §55 K 组同一课，但这次栽在自己的探针上）**：`tools/reach_negative_controls.sh` 首轮跑完 21 条里 **R-j、R-k 两条负控制失效**
—— `grep -c "@Test"` 把注释掉的 `//@Test` 照数（用例被删光也"通过"）、`grep -c` 数**行数**（两个出口挤一行看不见）。
改成 `^[[:space:]]*@Test` 与 `grep -o | wc -l` 后两条才真报红。**判据太弱比没有判据更危险**：它给出绿色的假安心。
同轮还实测到 **21 条判据全绿的源码可以同时编译不过**（误删换行把两行并一行），所以内容探针与 `assembleDebug` 谁也替代不了谁。

**实测**：`ReachabilityContractTest` 17 例全绿；探针 21 条全过（R-a 0 / R-b 0 / R-c 0 / R-c2 1 / R-d 0 / R-e 25 / R-f 2 / R-g 2 /
R-h 0 / R-i 1 / R-j 17 / R-k 1 / R-k2 0 / R-l 5 / R-m 2 / R-n 3 / R-n2 0 / R-n3 2 / R-o 1 / R-o2 2 / R-p 1）；
负控制 21 条逐条破口全 ✔（恢复后重新变绿）；全量 `206 tests / 0 failures / 0 errors / 0 skipped`（18 类）、
`assembleDebug` BUILD SUCCESSFUL、`lintDebug` 0 errors / 46 warnings；其余四组探针（S/P/L/E）复跑仍全绿。

**补记（同轮稍后，写自检文档时抓到的第二个缺陷）**：常驻自检的**白名单自身有洞** ——
`CONTEXT_LOCK.md` §八 与 §55 C 的宽口径 grep 里有一条 `Models\.kt:(1[1-4][0-9]|2[0-9][0-9])`，**结尾少了冒号**，
于是 `Models.kt:1444` 的前缀 "144" 就算命中，`Models.kt` **1100..1499 整段命中被静默吞掉**（实测吞掉 7 行，其中 6 行是本轮新写的来源说明）。
改成按内容过滤（注释行 + `Haversine 直线距离|mock|直线估算|不是道路里程|路网不可用`）后窄口径仍是 4、宽口径仍是 30，
数字没变，变的是"以后 `Models.kt:13xx` 写一行真违规看得见"。这与 ⑥ 那次"行号白名单漂出两行"是同一缺陷的反向形态，
两边合起来就是 §56 那句结论的完整版本：**行号白名单要么漂出真违规、要么宽到吞掉真违规，只能按内容**。

---

## D-023 "后端算不出 ETA"在契约里是 **null**，不是 0 —— 所以客户端的每一个 `?: 0` 都是替后端编一个它从没发过的值（2026-09-29，⑪）

**背景**：审计 §38 记的是症状（等车页"0 分钟"配红色"马上到"），用户 §14 记的是病根（禁止 null → 0 当业务语义）。
这一处比 §34 更"轻"（不影响能不能发货），但它是**直接给乘客看的假信息**：0 分钟意味着车已经在站台上。
旧代码不是一处 `?: 0`，是一族：模型 `multiNum("etaMinutes",…).toInt()`（缺键给 0.0）、页面 `etaMinutes > 0`（0 走"还没到"分支）、
进度 `1 - eta/40`（ETA 没给时算出 1.0，进度条画成"已经到站"）。

**先读后端再动手**（用户 §5 的第四次兑现，本轮又抓到台账与自己的预设各错一处）：
`AppBusRespVO.java:11-62` 与 `AppBusNearbyRespVO.java:78-151` 逐字段抄了一遍（两个端点的形状**不一样**，见下面的"决策 1"）；
决定性的一步是去 `AppBusServiceImpl` 看"算不出时发什么"：`:132` 是
`setEtaMinutes(Math.max(1, Math.round((100-progress)/100f*duration)))`、`:728` 是 `max(1, ceil(...))` ——
**两条 ETA 公式都下探到 1 分钟**，`:134` 拿不到线路时长时直接 `setEtaMinutes(null)`，`:665` 注释原文
"下一站未知：只给站点名，不臆造到站分钟"。⇒ **0 在这条契约里不合法**，客户端出现 0 只能是塌缩来的。
报文里"没给"是**显式 null 键**而不是缺键：HTTP 走 MVC 的 Jackson（不过滤 null；`JsonUtils.java:48` 那个
`NON_NULL` 只是工具 mapper，不参与响应序列化）—— 所以 `optInt(...,0)` 与 `isNull` 两种读法都会把它读成 0。
本轮顺带核对出**三处只在真后端下现形的静默断链**（演示态一切正常）：旧 `multiStr` 候选名里没有 `busId`/`plateNo`
→ 车辆 id 与车牌恒空；`statusOf` 把 `/bus/lines` 的整数 2 读成 `ARRIVED`（2 是停用）且码表里留着一个后端从不发的 3；
`sourceOf` 把 `dataSource=REAL` 升格成 `REAL_FRESH`，而 `/bus/lines` 根本没有 `locationSource` 键 —— 等于替后端宣称新鲜度已确认。

**先纠正我自己刚写下的一条**（记进日志是因为这类错会重复发生）：本节初稿写的是"**台账错处**：§35 修改方案 ② 建议由 ETA 反推进度，本轮否决"。
复核原文后台账**没错** —— §35 方案 ② 的原文正是"`progress` 有值就用后端进度、没有就**不画进度条**（不得由 `etaMinutes` 反推）"。
被推翻的是**代码**（`BusScreen` 那句 `1 - eta/40`，分母 40 是 Android 自选常数、后端没有对应量），本轮是**照台账落地**。
⇒ 教训：**"实现违背台账"和"台账本身错"是两种结论，写反了下一轮就会有人拿着我的记录去把正确的方案改反。**
台账上本轮真正补上的一条细节：`:125-127` 是 `getProgress()!=null ? getProgress() : 0`，即**"后端给 0"与"后端没给 progress"是两件事**，
反推会把这两者搅平（没给时画成"刚出发"）。方案 ② 只说了"没给就不画"，没说后端自己会给 0。
**我的预设被否处**：原打算在"④ 联动"里用 `etaToNextStationMinutes` 给 `distanceToNextStationKm` 找替身。**拒绝**：
它只在 `AppBusRespVO:50`，**不在 `NearbyBus`**，而且说的是"车到自己下一站"不是"车到用户那个站" —— 换过去是造一条不存在的语义，
按硬规则宁可空着。

**决策**：
1. **按端点分形状，不搞"一套报文两边用"**：`DemoData.vehicleJson` 的 `nearby` 分支发字符串 status +
   `locationSource` + `routeProvider="SCHEDULE"` + `nearestStation*`，`lines` 分支发整数 status + `progress` +
   `speedKmh` + `etaToNextStationMinutes`；"没给"一律写 `JSONObject.NULL`（12 处）而不是省键，与后端报文同形。
   并且**新增一辆只在 nearby 出现的车**（渝A·3Q778，在途但无 ETA）—— 因为这条路径 `/bus/lines` 走不通
   （进度反推恒 ≥1 分钟），不在演示面造出来，判据 T-a/T-k 就没有被测到的样本。
2. **8 个数值字段改可空**（`speedKmh/etaMinutes/waitDepartureMinutes/distanceToNextStationKm/
   nearestStationEtaMinutes/stopsToNearestStation/progress/distanceKm`），读法 `multiNumOrNull(...)?.toInt()`。
   **后端真给 0 分钟时原样透传**（有独立测试钉住）：0 与 null 不许合并，这是 §14 的完整口径而不是"见到 0 就当没给"。
3. **渲染只说后端给过的话**：`etaValueText`（没值 → "—"/"待发"，`etaUnitText` 同时留空，所以渲染不出"0 分钟"）、
   `etaCaptionText`（到站 / 发车 / 已到终点是三件不同的事）、`nextDistanceText`（null → "距下一站未给"）、
   `roadProgress`（只认后端 `progress`）、`providerText` 按 `:722/:730` 实际发的 `SCHEDULE` 给"按班次计划时长估算"。
   **删掉旧代码"现实公交/项目线路"那个标签**（它由 `routeProvider.contains("REAL")` 推出，是替后端背书线路性质，契约里没有这个概念）。
4. **不新增字段，只把读不到的读回来**：`busId`/`plateNo` 进候选名列表头部；`statusOf` 只留两个端点真实会发的码
   （字符串 RUNNING/IDLE/ARRIVED/NO_LOCATION + 整数 1/0/2）；`sourceOf` 去掉 `REAL→REAL_FRESH`，落 `UNKNOWN` →
   文案"实时（新鲜度未标）"。`BusVehicle.routeId` **保留不删**（旧兼容），但 KDoc 写明"真接口下恒为空串"。
5. **排序回到后端口径**：`byUserDistance()` 用 nullsLast，对齐 `AppBusServiceImpl:436-437` 的
   `Comparator.comparing(NearbyBus::getDistanceKm, nullsLast)` —— 没给距离的车不许排到队首当"离你最近"。
6. **§69（本轮顺带发现，登记并做能做的部分）**：车辆端点**没有任何一个带 `routeId`**（两个 VO 里 grep 0 次；
   `routeId` 只在 `AppBusLineRespVO:13`），所以旧 `nav.navigate(Routes.busDetail(v.routeId))` 在真后端下恒为 `busDetail("")`：
   不崩、不报错，只是点进一条空白详情。线路页按 `routeName` 回查行 id（`lineIdOf`），解析不到就置灰；
   首页因为 `NearbyLine` 只有 `routeName/startStation/endStation/dataSource` 四个键（**连线路列表也没 id**）
   而**如实保持不可点** —— 少一个功能可以接受，一个"看着能点其实点不开"的状态是骗人。
   ⚠ 按名字匹配隐含"线路名唯一"，后端另有 `routeCode:16`；将来重名的正确解法是**后端补 `routeId`**，
   不是客户端取第一条匹配或给名字做 hash。
7. **§68（登记，不在本轮"修好"）**：`AppSendArrangementRespVO.java:8-43` 共 11 个键里**没有 `etaMinutes`**
   （也没有 `targetStation/shiftCode/dataSource`），所以"车快到了"提醒在真后端下**结构性地不响**。
   真正带 ETA 的是 `/send/page`·`/send/track`：`:96 etaMinutes` 由 `AppSendController:246-249`
   用 `estimatedArrivalTime - now()` 算（**只有未来时间才给值，否则 null**），`:106 carrierEtaMinutes` 由 `:478` 给，
   而后者出自 `GeoDistanceUtil`（`:6/:18/:21` 写明 **Haversine + 25 km/h 折算**，VO 的 @Schema 自己也标了 Haversine）。
   ⇒ **不为了"让提醒响"去造键、也不拿班次时间反推**；⑫ 迁数据源时 `carrierEtaMinutes` 只能标成
   "按直线距离估算，非道路里程"，不许写成读起来像实时路网到站时间的文案，更不许当放行条件。

**判据与两条方法论补强**：`tools/check_eta_probes.sh`（T-a…T-q，25 条按内容判据）+ `tools/eta_negative_controls.sh`（25/25 ✔）。
- **一条契约一个作用域**：T-c 与 R-g 都改成 `sed -n '/^data class X/,/^) {/p'` 只数**类体**。
  起因是 `BusVehicle` 新增可空 `distanceKm` 之后，可达性那条 R-g 数到了别的契约的字段 ——
  处置是**把字段摘出该判据的作用域**，而不是把预期数字从 2 抬到 3（抬数字就等于这条判据从此不判任何东西）。
- **`≥` 而不是 `=` 的那几条要写清为什么**：T-p（`busId`/`plateNo` 读法）实测 3 不是 2，因为 `TransportLeg` 也读 `plateNo`。
  写死 2 会在别人补兼容时假报红，假报红多了判据就没人看。
- 四条负控制第一版"注入破口后 exit 0"（T-d1 正则漏 `!!`、T-e 锚点选了不存在的 `fun scan(`、
  T-l 锚点是已被替换掉的文案、T-o2 的 `sed` 只替到 5 处里的 1 处）—— **又是判据本身太弱**，与 ⑩ 的 R-j/R-k 同一课：
  负控制必须先跑，判据太弱比没判据危险。
- 同轮再次实测到 **25 条全绿的源码可以同时编译不过**（一次编辑吃掉行尾换行，把两行并成一行，探针毫无反应），
  以及一处新的时间戳坑：测试 XML 是 14:05 的、`lint-results-debug.txt` 却停在改源码前 36 秒 ——
  **每个构建产物各自对时间戳**，不能因为"这轮跑过 gradle"就把三个数字一起当成新鲜的。

**代价**：用户现在看到的是"— 分钟"、"到站时间未给"、"距下一站未给"、"实时（新鲜度未标）"这些**不确定的话**，
等车页的"确定性"肉眼可见地变少了。这是本轮愿意付的：那些确定性本来就不是后端给的，是客户端替它编的。
另一项代价是页面多了一层 null 判定（`etaMinutes != null` 5 处 + `mapNotNull` 1 处），后续 ⑫ 改货运侧时会再摊一次同样的成本。

---

## D-024 指令 v2.0 到货：用"契约能不能证"决定照做 / 变通 / 不照字面做（2026-09-29，⑫ 开工前的 §1 基线扫描）

**背景**：用户给了 `USER / DRIVER 双业务域统一客户端` 的 v2.0 命令（0–130 段），它比先前记录的版本长，
且开头 §1 就要求"开始前必须扫描当前 Android 项目，而不是假定以前文件仍完全一致"。
本轮**不改任何业务代码**，只做 §1 要求的基线重建 + 对表，并把冲突落成决策。

**先纠正我自己上一轮的记录（五条，全是指令原文 vs 我的记忆）**：

1. 我记成"**§125** 要求 THIRD_PARTY_NOTICES.md"。原文：§100 才是 Third-party Notice，**§125 是"司机安全降级"**。
   ⇒ 段号是引用指令时的头等事实，凭印象写段号与凭印象写后端字段是同一类错。
2. 我在本文件里读到过"§104 要 13 份文档"。**原文是 7 份**。多出来的 6 个名字是我自己拼的。
3. 写 CONTEXT_LOCK §〇 时我落了一句"指令 **§166–167** 禁止未知 → 回退"。
   实测本文最大段号 **130** —— **这条引用不存在**。已当场改成 §32「不允许一个引擎失败就直线」+ 本文件硬规则 19。
4. `docs/REAL_ROAD_ROUTING_ARCHITECTURE.md` 我按"本仓 docs/ 下有"来指路，`find` 实测**只在后端仓**
   （`../cargo-post-platform/docs/`）。指路牌里一个不存在的相对路径，比不指路更坏。
5. 预设 `MineScreen` 的 `AppState.setRole(Role.DRIVER)` 是 §4 禁止的"本地写死角色"——
   **读代码后撤销**：它先 `Repo.bindDriver()`，`driverId<=0` 就 return，而且函数自己的 KDoc 就写着"这是修掉的旧违规"。
   （这正是 §1 那条"不要假定以前文件仍完全一致"的价值：连我自己三小时前的判断都不能假定。）

**基线（实测，非回忆）**：`app/src/main/java` **71** 个 `.kt`；测试 **20** 个文件 / 生效 `@Test` **215**
（`TestSupport.kt` 0 例，所以是 19 个测试类）；依赖清单 22 条（含 4 条 test-only）；`app/libs` 不存在，无本地 aar/jar。

**决策**：

1. **不做包结构迁移**。指令 §2 画的是 `core/ + feature/{user,driver}/ + shell/`，工程实际是
   `core/ + data/ + ui/{user,driver,login,common} + driver/ + nav/ + notify/ + qs/ + widget/`。
   两边**职责等价**（域确实分开了，探针 `DomainSplitTest` 也在判职责不判目录）。
   迁移 71 个文件只为了对齐目录名，会让 §八 现有 **12 组**按路径/内容写的判据全部重测一遍，
   而"改路径不改行为"恰恰是最容易在 `sed` 作用域里静默丢判据的操作。按 §122「不删有价值功能、逐步迁移」——
   这里的"价值"是**已验证的判据**，目录名不是。**新增**能力（如 NavigationEngine）才用指令建议的新目录。
2. **角色与 Session 按后端契约做，不按指令的类名做**（详见 §九 新增表）。三条实测证据：
   `AppAuthLoginRespVO` 只有 `userId/accessToken/refreshToken/expiresTime/openid`；
   `member/controller` 全量 grep role 键 **0**；`Repo.login` 报文只有 `mobile/password`。
   ⇒ 不新建 `SessionManager.roles: Set<AppRole>`，因为那个 `Set` 里任何 `DISPATCHER/ADMIN` 都是后端从没发过的东西（§123）。
   登录页的"选择身份"选择器**保留**：它是入口意图，不是权威；权威是 `driverId` 档案。
3. **`RouteSource` 保留 `EUCLIDEAN/STRAIGHT/MOCK/DEMO/ESTIMATED/UNKNOWN`**，与 §16 字面冲突，理由写进 CONTEXT_LOCK §〇：
   这些字符串后端/历史数据真的会回，删掉枚举它们只能塌进 `UNKNOWN`，届时"是直线"和"没认出来"共用一个值，
   拒收文案就退化成"来源未知"。**不变量是 `real=false` 进不了正式链路**，不是"表里没有这些字"。
4. **`THIRD_PARTY_NOTICES.md` 建档**（本仓根目录，§100 指定的文件名）。顺带证明建档有必要：
   一建就暴露 **OSM/ODbL 署名在 App 内命中 0**（真实道路折线源自 OSM 数据），
   而这条义务 CONTEXT_LOCK §五 与 D-009 早就写了"要登记"，只是从没落成文件 —— **"有规则、没有台账"就是没做**。
   同时如实登记：工程**未复制任何第三方源码**，MapLibre/GraphHopper Example/Valhalla 是"计划复用、尚未引入"。
5. **§121 的 checkpoint commit 记 `ENVIRONMENT_BLOCKED`**：`git rev-parse --is-inside-work-tree`
   → `fatal: not a git repository`。父目录留着 `_commit-backup-20260912-*` / `_git-rescue-20260912-*`，
   说明版本库早先就已脱离。**不 `git init` 新历史冒充连续**，不把 commit 写进任何交付口径。
6. **§107 的编号建议不采纳**（它建议 `D-020…D-027`，而这些号本工程已用于 2026-09-29 的四轮）。
   重排历史编号会让审计里所有 `D-0xx` 引用集体失效。**改为映射表**（本文件末尾），
   并如实记一个台账缺陷：**历史上存在两个 `D-009`**（"第三方开源复用需登记 NOTICE" 与 "折线在缺口处截断"），
   单独引用 `D-009` 是有歧义的 —— 引用旧号必须带主题。

**指令条目 → 本工程实际决策 / 状态 映射表**（供后续按 §107 口径核对）：

| 指令 §107 建议 | 建议内容 | 本工程对应 |
|---|---|---|
| D-020 一个 APK + USER/DRIVER 双域 | 架构 | 已成立：硬规则 1/18 + `DomainSplitTest`（7 例）；无独立决策号（是前置约束，不是选择） |
| D-021 Mini Program frozen | 范围 | 已成立：规则 A；对应"小程序只作只读对齐基准"（审计 §55 分域复核，D-018 同批） |
| D-022 GraphHopper 仍是服务端路由权威 | 分层 | 已成立：D-000（REAL ROAD 最高级约束）+ D-010（来源白名单四处同步、下发大写标签）+ D-012（后端验证路径）|
| D-023 NavigationEngine 抽象 | 计划 | **未做**：任务 #49，`BLOCKED`（导航 SDK 未进依赖，jar 内 `com/amap/api/navi/` 零类）—— 与本号冲突，本工程的 D-023 是"ETA null 不是 0" |
| D-024 AMap Navigation 首个在线引擎 | 计划 | **策略已定、实现未做**：D-001「优先 AMap，但不编造 API」就是这一条；卡在依赖 ⇒ `BLOCKED`。本号（D-024）已被本条占用 |
| D-025 MapLibre + Valhalla 二阶段 | 计划 | 未做，Phase 8；已在 `THIRD_PARTY_NOTICES.md` §5 预登记许可证义务 |
| D-026 禁止直线进正式路线 | 硬约束 | 已成立：D-000（REAL ROAD）+ 硬规则 5–9/19/20 + D-013（保留三处带标注直线并划清边界）|
| D-027 司机任务状态服务端权威 | 硬约束 | 已成立：D-002（业务状态推进只认后端）+ D-021（段状态机单出口，动作表从后端 `TRANSITIONS` 反推）+ D-017（到站不由 GPS 放行） |

**判据**：本轮零代码改动 ⇒ **不新增判据**（新增一组没有代码可判的探针只会得到一个恒绿的数字）。
两项对表产出的实活已排成 V-1（§52 幂等）与 V-2（壳路由用例），后又有 V-3（§54 异常上报）—— 刻意不用圈号续排，因为圈号 ⑭⑮⑯ 已被路线图位次占用（离线缓存 / MapLibre / UI），同号两义正是本工程刚记过的缺陷，判据在实现那一轮随代码一起建，
并要求各自配负控制 —— 与 ⑩⑪ 同一课：**判据太弱比没判据危险**。

---

## D-025 承运分钟与班次分钟拆成两个字段，且承运那一半**只能带着"直线估算"四个字出现**（2026-09-29，⑫）

**背景**：⑪ 把公交侧的 null→0 收口后，同一味病在货运侧还剩 `/send/page`·`/send/track`·两个段契约。
本轮照"先读后端源码"开工，读到的四条事实（详见审计 §71）里有一条不在原方案里：
旧 `SendOrder.etaMinutes` 的候选键是 `("etaMinutes","eta","carrierEtaMinutes")` —— **把后端 Haversine ÷ 25 km/h
估出来的承运分钟，和用户理解的"车到我这站还几分钟"写进了同一个字段**。

**决策**：

1. **拆字段，而不是加换算**。`etaMinutes`（班次口径，`AppSendOrderRespVO:96`，仅 `/send/track` 的 `fillEta` 会写，
   且只在未来时间才写）与 `carrierEtaMinutes`（承运直线估算，`:106`）成为两个可空字段，候选键互为别名这条路封死（探针 T-t 判红）。
   **为什么不"把直线估算换算成道路 ETA 再填进一个字段"**：那正是硬规则点名禁止的形态
   （"禁止 Haversine 驾驶距离"、"不要为了'兼容旧接口'把 euclidean 转换成 OSM，必须真实来源才行"）。
   换算不会让数变真，只会让假数变得不可追溯。
2. **宁可标注，不宁可删数**。承运分钟继续显示，但**每一处界面文案都必须同行带"（直线估算）"**，实测 5 处
   （`AlertEngine:101`、`HomeScreen:234`、`ParcelScreen:254/342/444`）。
   配套：判据从"数总数 T-v=5"升级成**闭包条 T-v2**（同一行出现 `carrierEtaMinutes` + "分钟"就必须含口径），
   因为只数总数拦不住"新增一处不带口径的展示" —— 这正是这条硬规则的可执行形态：**假话可以暂时存在，但必须自报是估算**。
3. **§68 的提醒不再挂在没有 ETA 键的端点上**。到站提醒与两个用户侧列表的数据源迁到 `/send/page` 的承运字段，
   过滤链 `carrierLocationSource != "REAL_STALE"` → `mapNotNull { carrierEtaMinutes }` → `in 1..10`（T-y=3、T-w1=1、T-w2=0）。
   **新鲜度是承运 ETA 的前置条件**：后端在位置缺失/过期时整块不填，客户端若只判空就等于拿过期位置催用户出门。
4. **`Arrangement.etaMinutes` 保留字段、清空消费者**。不删字段（旧解析与历史报文兼容），但 ui/notify 的 import 消费点实测归零；
   这条"字段留着、没人读"的状态必须在探针尾部**打印**出来，否则下一轮会以为它还有数据源。
5. **契约边界优先于"顺手一起修"**。`TransportLeg.distanceKm = 0.0` / `durationMinutes = 0` 与 ETA 同病，
   但属"段计划时长/里程"另一段契约 ⇒ **本轮不并进 T-s**（会把两个契约的字段混进一条计数，正是 ⑪ 修掉的串味判据），
   改为探针尾部打印残留 2 + 排进 ⑬。同理 ⑬ 的两项（`etaToNextStationMinutes`、`BusLine.distanceKm`）不合并进本轮判据。
6. **一条永不报红的判据比没有判据更危险** —— 本轮两条负控制抓到的是**判据自身**：
   - T-t 写成 `grep 'multiNumOrNull\("etaMinutes", …\)'`：BRE 里 `\(`/`\)` 是**分组**而非字面圆括号，**永远 0**，
     注入破口后探针仍 exit 0 才暴露 ⇒ 改 `-F`；
   - T-o2 的负控制 sed 用小写 `etaMinutes != null`，而 ⑫ 之后页面读的是 `carrierEtaMinutes != null`（大写 `E`），
     **破口根本没注进去**，⑫ 之前这条控制是假绿 ⇒ 改 `s/\([eE]\)taMinutes != null/\1…/`。
   由此把口径写死：**新判据必须与它的负控制同轮交付**，且控制必须逐条 `✔`（本轮 41 条 0 ✘ / 0 △）。
   另新增 `sbysed`（按 `data class` 类体边界替换）—— 全局 sed 会一次改掉四个类的同名字段，"谁破了"就分不清了。

**后果与遗留**：货运侧 ETA 的 null→0 已收口（T-a/T-s/T-t/T-u 全 0），到站提醒在真后端下第一次"能响且说的是估算"。
未收口的同族尾巴按第 5 条留给 ⑬；`Repo.kt` 的 `optBoolean(…, true)`（缺键替后端默认"在服务/已定位"）仍是同类病，登记未修。
本轮证据：探针 6 套 exit 0、ETA 负控制 41 ✔、可达性负控制 21 ✔、`testDebugUnitTest --rerun` executed →
20 类 224 tests 0 失败（XML 16:20:52）、`assembleDebug` executed（APK 16:15:22）、`lintDebug` executed（0 errors, 46 warnings）。

---

## D-026 一个键名两种语义时，客户端只能按**能观测到的形状**分家：宁可大数字空着，不许摆一个口径错的数（2026-09-29，⑬）

**背景**：⑫ 收工时留了三条尾巴给 ⑬（`etaToNextStationMinutes` 没进模型、`BusLine` 读的是不存在的
`distance|meters`、`TransportLeg.distanceKm/durationMinutes` 仍是非空默认值）。
本轮照例先读后端 VO（审计 §72 六条事实），其中一条比原方案严重：**`etaMinutes` 这个键名在两个端点上说的是两件事** ——
`/bus/lines`（`AppBusServiceImpl:130-132`）是"跑完剩余线路要几分钟"，`/bus/nearby`（`fillEta:726-731`）是"到下一站要几分钟"。
客户端过去把两种形状喂进同一个 `etaCaptionText`，于是 lines 的车在界面上被说成"约 64 分钟到下一站"。

**决策**：

1. **端点判别子只用可观测事实：`progress` 只在 lines 形状里存在**。`nearby` 的 `NearbyBus` 没有这个键
   （`AppBusNearbyRespVO:76-151` 逐字段核过），所以 `progress == null` 是"这份报文是 nearby"的唯一证据。
   **为什么不加 `endpoint` 标记参数**：那会把"我从哪个 URL 拿的"传进模型层，
   而 `BusVehicle.parse` 有两个调用点共用（列表 + 单车详情），传错的代价比判据失效更隐蔽。
   **残余风险照实登记**：若哪天 nearby 也下发 `progress`，这条推断就失效 —— 已写在 `nextStopMinutes` 的注释里。
2. **口径不对的数一律不占"看起来像答案"的位置**。lines 形状且没有精确值时，
   `etaValueText` 是 "—"（大数字位空着）、`etaCaptionText` 走 `remainingRouteMinutesText`
   （"剩余全程约 N 分钟（按线路进度与班次计划时长估算，不是到下一站）"）。
   这是硬规则"宁可没有路线，不允许假路线"在数字位置上的形态：**64 分钟本身不是假数，假的是"到下一站"这四个字**。
3. **精度与小程序同式子而不是同显示**：`Math.max(1, ceil(etaToNextStationMinutes))`
   （`pages/bus/detail.js:132-137` 同一式子）。后端两条 ETA 算法都 `Math.max(1, …)` ⇒ 0 从来不是合法到站分钟，
   客户端读出的 0 只可能是自己编的；0.2 分钟显示"1 分钟"，不显示"0"、也不显示"0.2"。
4. **三个 `distanceKm` 不合并成一条判据**（`BusLine` 公里 / `TransportLeg` 公里可空 / `QuoteResult` 是"直线距离×路网系数"估算），
   但**同一个病根**：候选名里混着契约里不存在的键。`distance` 在 `BusLine` 那一族是**米**，
   把它列进公里字段的别名等于缺键时拿米当公里 —— `Repo.routePreview` 的 `("distanceKm","distance")` 就是这么写的。
   判据 T-ac 按"读法里出现 `distance|meters|minutes` 别名"判红（实测 0），而不是按字段名数总数。
5. **口径文案必须落在模型 getter 上，否则测不到**。本工程的单测没有 Robolectric ⇒
   Compose 里的字符串断言不了。所以 `QuoteResult.distanceCaliberText`、`TransportLeg.legPlanText`、
   `BusVehicle.positionOnlyTag` 三个 getter 把"没给就不显示 / 有值也带口径"的判断搬进模型，界面只渲染。
   **代价照实记**：多三个 getter，换来 4 条能跑的契约测试；不搬就只有 grep 判据这一层保护。
6. **解析候选名收回模型**：新增 `RoutePreview.parse(o)`，Repo 不再就地拼 `multiNumOrNull(...)`。
   候选键名散在 Repo 里时，判据得扫两个文件、测试还得走 MockWebServer 才碰得到 —— 这是 §71/§72 两轮里
   同一类"破口藏在第二个落脚点"的根因。
7. **只有经纬度的报文不许被界面宣布成"在途"**。`AppDriverPositionRespVO` 只有三个键（端点确实存在，
   在 `AppDriverController.java:258-263`），`RoutesScreen:144` 过去无条件渲染 `"在途 ${nextStation}"`（站名恒空）。
   这与 §14 是同一句话在司机域的形态：**没有值就不许有业务语义**；"调度决定去哪里"里也没有"本机替调度宣布车在跑"。
8. **两条判据自身的失效，同轮抓到**（延续 D-025 第 6 条）：
   - **T-k 被本轮改成假绿**：⑬ 给 `BusEtaContractTest` 加了用例（9→11）而 pin 还是 `-ge 9`，
     "注释掉一条"的负控制当场从红变灰（`exit 0`）。⇒ `-ge` 型计数判据必须在**加**用例的同一轮抬下限，
     本轮跟到 12。**这条值得单独立规矩**：加测试的人不会去动探针，探针作者也不会去数测试条数，
     所以"用例数判据"必须由负控表周期性替双方对账。
   - **T-ah 第一次响，响的是我自己写的注释**（说明注释里引用了旧文案 `"在途 ${nextStation}"`）。
     处置是按内容过滤注释行（与 T-s/T-v 同一写法），**不是删注释** —— 删说明凑绿等于把"这轮为什么改过"抹掉。
9. **契约边界优先于"顺手一起修"**：ETA 候选名里还剩 6 处自造别名 `"eta"`，其中 `Repo.kt:947` 的 `RouteGuide`   读的是**导航引擎自己的报文**、不是 app VO —— 没核过那份契约就不能动（"如果 SDK 当前 API 能力不确定，
   必须先查当前依赖 API，不能自行编造"）。同理 `QuoteResult` 三个金额字段仍 `multiNum` 塌 0，登记不并轮。
10. **四条过程纪律由本轮事故立下**（不是代码问题，但会让"抄数"抄到假东西，所以记进决策层）：
   - **负控脚本一次只跑一个**。本轮把 `eta_negative_controls.sh` 起了两份（一份前台超时被转后台、一份又后台起），
     两者 `sed` 破口/恢复互相覆盖，重叠窗口里对源码做的读取与写入都不可信。
     ⇒ 等它打印 `[恢复后] … 判据通过` 之后再碰源码；本轮收工另跑一遍负控，仍是 **50 ✔ / 0 ✘ / 0 △**。
   - **台账要跑重复块自检**。本轮写完发现 `AI_ITERATION_STATE.md` 有两整块逐字节重复的内容（785 行 + 47 行），
     删前用 `difflib` 逐行证"确实相同"（不凭印象），删后 `grep -c "^## 常驻自检"` = 1、文件 966 行
     （此后每次补台账它都在长：写"验证证据"与"⑬ 收工最后一遍"两段后 1007，补完口径说明后再量 1041，
     中途还读过 1039/1040 —— **行数不是不变量，"每个 `##` 标题恰好出现一次"才是**，抄行数必须当场重跑 `wc -l`）。
     ⇒ 收工加一步：每个 `##` 标题只应出现一次；文件出现重复块时，任何"按行号引用台账"的做法都会指到错的那一份。
   - **"0 命中"必须连同 pattern 一起抄**（收工最后一遍抓到，起因是状态板那行台账只写语义没写命令）：
     照"AMAP_KEY 字面量 … 全部 0 命中"按**裸 token** 跑 `grep -rn "AMAP_KEY" app/src/main` 实测 **2 命中**
     （`AndroidManifest.xml:55` 注释 + `DriverMissionMap.kt:71` 的 `BuildConfig.AMAP_KEY.isNotBlank()`，都是引用不是密钥值）。
     **判据本体没坏** —— 审计 §55 E 那条自带 `grep -v "@string/amap_key\|BuildConfig.AMAP_KEY"`，照原样复跑就是 **0**；
     坏的是**简写**（key 真值只在 `local.properties` 的 `amap.key`，经 `app/build.gradle.kts:74` 注入）。
     ⇒ 状态板那行已改成"照抄 §55 E 的六条命令"并写明"那条 `grep -v` 是判据的一部分"。
     这与"行号会漂"是同一族缺陷的**第三个方向**：**离开命令的"0"不是判据，只是一个脑结论** ——
     抄数的人要么把 2 读成红线破了，要么把判据改成他数得出的那个数，两种都比把 pattern 写全贵。
   - **顺带修正 ⑪ 的"时间戳纪律"**：本轮 `find` 显示三个源文件 mtime（17:56）晚于 lint 报告（17:50:46），我据此判"lint 过期"；
     重跑后 gradle 报 `lintAnalyzeDebug`/`lintReportDebug`/`testDebugUnitTest`/`compileDebugKotlin` **全部 UP-TO-DATE**
     （按输入内容哈希判定）⇒ 那三个 mtime 是负控脚本原样恢复留下的，产物其实对应当前源码。
     **mtime 只能让人起疑，不能判"产物过期"**；要无歧义只能 `--rerun-tasks`（`--rerun` 只作用于命名到的任务，不带依赖重跑）。

**后果与遗留**：公交侧 ETA 家族第一次做到"两个端点两套口径、界面各说各话"，三个 `distanceKm` 全部可空且带口径。
遗留：① `"eta"` 自造别名 6 处（RouteGuide 那处要先核导航报文契约）；② `QuoteResult` 金额字段 null→0；
③ `TransportLeg` 的段几何后端不下发 ⇒ 段卡进度仍只能是状态驱动示意，属 #49 真道路改造线（BLOCKED on 高德导航 SDK）。
本轮证据：`testDebugUnitTest --rerun` executed → **21 类 233 例 0 失败**、`assembleDebug` executed、
`lintDebug` executed（BUILD SUCCESSFUL）、探针 6 套 exit 0、ETA 负控制 **50 条全 ✔（0 ✘ / 0 △）**、
可达性负控制 21 ✔、安全组 E1/E3/E4/E5/E6 全 0、宽口径 42→44（新增两行都是口径标注）、窄口径 9 与 `GeoMath` 27 未变。

## D-027 后端没有幂等机制时，客户端可以自己写，但**只能写在进程单例那一层、且绝不给报文加后端不收的字段**（2026-09-29，V-1 / 指令 §52）

**背景**：状态板把 V-1 记成 P0 `CONTRACT_MISMATCH`（实测 `clientRequestId|requestId|Mutex|withLock|actionLock|inFlight`
在 `app/src/main` 命中 **0**，而司机写路径有 10 条）。指令 §52 给的顺序是硬的：**先去后端查有没有幂等机制**，
有就复用那个键，没有才允许只做本地。用户本轮的授权原话是"后端没有对应的东西安卓可以自己写，只是要符合架构要求"。

**后端实测**（审计 §73 七条事实，全部带出处）：`AppDriverController` 全类 **零** `@Idempotent`；
框架那个注解在 app 侧只装在两个 `create`（`AppProductOrderController.java:36-41` / `AppSendController.java:119-124`，
`timeout = 10, SECONDS`）；键是服务端 `md5(methodName + argsStr + userId + userType)` 算的，
**客户端不发键**；11 个司机 ReqVO 里没有任何唯一键。
⇒ 结论是"没有可复用的键"，而不是"后端有键我没找到"。
而重复提交的代价是**真的**：`depart` 把已收口的执行记录倒带并 `arriveTime = null`（`DriverAppServiceImpl:861-868`）、
`pickup-confirm` 因 `PICKUP_CONFIRM_STATUSES` 含 3 而 CAS 3→3 成功后再 +1 件（`:118`、`:1069-1087`）、
`leg/complete` 与换乘 `arrive-dest` 各多插一条事件并多推一次（`MultiLegServiceImpl:706-710` + `OrderEventServiceImpl:41-58`
的"跳过不是失败"链条）。

**决策**：

1. **闸门放在 `object Repo` 这一层，不放 ViewModel**。页面的 `busy` 随 ViewModel 销毁（切壳 `popUpTo inclusive`），
   协程取消还会释放锁而后端可能已经生效 —— 只有进程单例能跨页面持有。这也是"符合架构要求"的具体含义：
   分层不变（data 层）、UI 不新增依赖、调用点不动（所以分域判据 S-le 仍 2/2 没漂）。
2. **`tryLock` 而不是 `withLock`**：`withLock` 会把第二次提交排队，等第一次完成后**照样发给后端** ——
   那正是要避免的副作用。语义必须是**丢弃**（判据 I-d 同时钉 `tryLock = 1` 和 `.withLock( = 0`，
   只钉前者可以被"换成 withLock 但保留一句 tryLock 注释"糊过去）。
3. **成功后仍持有 10 秒**，口径直接抄后端 `@Idempotent(timeout = 10, SECONDS)`，不是拍脑袋；
   **异常立即释放**，等价 `deleteKeyWhenException = true` —— 否则一次网络抖动就把这个动作永久锁死。
4. **`notSent` 是一种新结局，不是失败的别名**。`Ack` 加可判别字段，四个回执点各加一支，
   文案只能说"本次请求未发给后端……以下为后端当前状态"；判据 I-f 反向钉住这些行里不许出现"已提交后端"。
   这也是"宁可暂时不能确认，不许假确认"的形态：客户端**不知道**后端有没有受理（它没被拒绝，只是没发出去），
   所以任何"已提交/失败"的断言都是编的，唯一诚实的动作是回读。
5. **两条有意豁免**：`reportLocation`（⑦ 已有"合并 + 新鲜度闸 + 丢点可见"，再套一层丢弃就是**偷点**）与
   `uploadPhoto`（只取文件地址，真正的提交是后面那次 confirm）。豁免不是漏做，所以钉成**反向判据 I-c**：
   谁把上报接进闸门，探针就报红。
6. **不自造字段是红线**（I-h）：`clientRequestId/requestId/bizNo/idempotencyKey/X-Request-Id` 在 `app/src/main`
   按**排除注释行**的口径必须为 0。⚠ 这条判据第一次跑出来是 **1**，命中正是我自己写在 `ActionGate` 类注释里那句
   "`clientRequestId`/`requestId`/`bizNo` 全部不存在" —— 处置是**给判据加注释行过滤**（与 T-s/T-v、T-ah 同一写法），
   **不是删注释**。离开注释的"1"会让下一轮误读成"有人已经把假字段发出去了"。

**过程纪律新增一条**：**新机制必须跑全量，不能只跑自己的新用例**。本轮 `--rerun-tasks` 首跑抓到 1 例红
（`ScalarEnvelopeContractTest.kt:191` NPE），根因是**我加的闸门**挂在进程单例上、把 10 秒窗口带进了别人的用例
（Gradle 默认所有测试类共用一个 JVM）。处置是 `TestHost.resetActionGate()` 用反射清 `locks`/`heldUntil`
（与 `installPrefs` 同一手法，**不在生产代码上开测试专用的口子**）。
只跑新加的 11 条用例，这个数字永远不会响。

**后果与遗留**：司机 10 条写路径第一次有了单一提交出口，重复点击不再可能产生后端侧的重复副作用；
`ActionGate` 5 条 + 仓库/网络 6 条 = 11 条用例，**把闸门摘掉后有 10 条红**（唯一不红的是"上报有意不进闸"那条）。
遗留：① 后端**有**幂等的两个 `create` 重复回 HTTP 200 + `code=900`，Android 现在把它的 msg 当错误条显示，
严格说 900 是"正在处理中 + 回读"而不是失败 —— 未做，属用户侧下单线；② `notSent` 文案只有 grep 钉住，
无 Robolectric/设备 ⇒ 真机呈现未验证；③ 键桶不淘汰（长进程 `locks` 只增不减），属已知局限不是待办。
本轮证据：`testDebugUnitTest + assembleDebug + lintDebug` **BUILD SUCCESSFUL / gradle exit 0**
（`53 tasks: 7 executed, 46 up-to-date`）、`suites=23 tests=244 skipped=0 failures=0 errors=0`（XML 全部 20:00:21，
逐类加总 244）、lint **0 Error / 46 Warning**、APK 57,941,378 B；七套判据全 exit 0，幂等负控 **12 条全 ✔**、
用例级负控 **10 红 + 恢复后 cmp 一致**、ETA 负控 **50 ✔**、可达性 **21 ✔**；
窄口径 9 / 宽口径 44 / `GeoMath` 27 / `DemoData` 3 文件 / 假步进 6 处 / 安全组六条 0·4·0·0·0·0 全部无漂移。
⚠ **APK 字节数收工重量为 57,941,374**（上面那行是中途读数，无法回溯核对是哪一次构建的产物）。
处置：不改动上面那行（它是当时的实测），只在下面补当前产物的读数与出处 —— 收工遍 `stat -c %s` = **57,941,374 B**、
`assembleDebug` 报 UP-TO-DATE ⇒ 对应当前源码。⇒ **纪律：字节数只当场量、不从上一遍抄；两个读数打架时写清"哪个对应当前产物"，
不要挑一个数字塞进台账。**
同轮另立一条过程纪律：**判构建成败不许用管道** —— `./gradlew … | tail -25` 的 `$?` 是 `tail` 的，
本轮就是用它把一次真实的 `BUILD FAILED`（`ActionGateTest` 里 `Unresolved reference 'ApiException'`）读成了"exit 0"；
一律 `> log 2>&1; echo GRADLE_EXIT=$?`。

## D-028 没有 Robolectric 时，"导航图级别的分域"只能靠**把决策提成纯函数**来测——结构收敛不算重构 UI（2026-09-29，V-2 / 指令 v2.0 Phase 1 验收）

**背景**：指令 v2.0 Phase 1 的验收句是"司机端绝不能看到普通用户首页"。登记时的实测是
`grep -rn "USER_SHELL|DRIVER_SHELL|startDestination" app/src/test` 命中 **0** —— 三条决策全部靠读证，
重构即回潮。本轮的授权原话仍是"后端没有对应的东西安卓可以自己写，只是要符合架构要求"。

**开工先核的四条现状**（全部当场 `grep`，不抄台账）：

1. "谁能看到哪一页"的判定地点是 `NavHost` 的**导航图**（`BusPostRoot` 里的 `composable(...)` + `startDestination`），
   而工程**没有 Robolectric** ⇒ JVM 用例渲染不出导航图。**补测框架不是本轮的选项**（那是另一笔账，已记在 ⑦ 遗留 §60）。
2. 两条决策原先**内联在 @Composable 里**：`val start = when { !Prefs.loggedIn -> ... }` 与文件级私有 `deepLinkAllowed(route)`
   —— 不是"有函数没测"，是**根本没有可调用的函数**。
3. 全工程跨域导航点**只有 2 处且都是故意的**：`ui/user/MineScreen.kt`（进司机端入口，先 `Repo.bindDriver()`）与
   `ui/driver/DriverShell.kt`（"用户版"出口，`AppState.setRole(Role.USER)` + 导航）
   ⇒ "用户目录不许出现 DRIVER_SHELL 字样"式的判据会把真业务判成违规。
4. 角色真值有**两个来源**（`Prefs.role` 与后端 `/driver/profile`），绑定司机必须先后端档案通过（§六既有规矩，`DomainSplitTest` 已钉）。

**决策**：

1. **只做结构收敛**：新建 `nav/ShellDecision.kt`，把"冷启动落点"和"深链是否接受"提成两个 `internal` 纯函数
   （`startDestinationFor(loggedIn, role)` / `deepLinkTarget(route, role)`）。**UI 树、导航图、路由表一字未动**，
   `BusPostRoot` 只是改成调用它们并删掉那份内联 `when` 与私有 `deepLinkAllowed`。
   为什么不新建"tab 集合真值源"：那会是第二份真相，`Routes` 已经有一份；本轮要的是**可测的判定**，不是新的抽象。
2. **两个函数都只读 `Prefs`，不读 `AppState.role`**：冷启动时 `AppState` 是内存态，`Prefs` 才是恢复来源；
   写清楚这一点比多写一条断言有用（KDoc 里就记了这句"为什么"）。
3. **陌生路由原样交给 `NavHost`**，不在纯函数里静默吞掉：吞掉就把"导航图里没这条路由"这个事实变成了界面的沉默，
   负控 M-6 专门钉这条（`else -> null` 这种"更安全的写法"是假安全）。
4. **判据按 ⑬/V-1 的口径建**（SR-a…SR-h，13 条）：按内容/结构数、不按行号、注释行一律排除、"预期 0"必须配负控。
   `SR-f2` 给 DriverShell 那条合法跨域出口**留"恰好 1"个名额**，而不是归零。
5. **9 条用例落在 `nav/ShellRoutingContractTest`**，只测纯函数（含一条**反向**用例："司机域深链在司机会话必须原样放行" ——
   只测被拒那一侧，闸门收紧过头也不会红）。行为 (c)（`Prefs.role` 与后端档案冲突时以档案为准）**不重复测**，
   那条已由 `DomainSplitTest.切换司机模式先过后端档案` 钉住。

**本轮最值钱的两条过程纪律**（都是负控自己失效之后才改对的）：

- **凡是"预期 0"的判据，先证明它能命中非 0**。SR-e/SR-f 第一次跑就是 `0/0`，看着像红线守住了，
  实际是 pattern 里的 `|` 在 **BRE** 下是字面字符 —— GNU grep 3.0 实测同一条 pattern 带 `-E` 命中 1、不带命中 0。
- **判据文件必须先验存在再计数**。`grep` 找不到文件时输出是空的，`[ "" = 0 ]` 会被读成"通过"；
  那次负控脚本就是在没拷全的目录树上（`cp -r app/src/test $SC/app/src/test` 产出 `test/test/…`）
  打印了 12 个 ✔，而 `[基线]` 明明报的是"判据失败"。修法是**探针内部**加文件预检 + `exit 2`，
  并在负控开头加"基线不通过则一律不采信"的闸门。

**同轮事故与处置（记在这里是为了让下一轮别用同样的写法）**：收工写台账时，一条
`io.open(p, 'w', encoding='n')`（编码名打错）在**校验编码之前就把 `AI_ITERATION_STATE.md` 截成了 0 字节** ——
`open(...,'w')` 的截断发生在编码解析失败之前，所以"报错的那一行"和"数据已经没了"是同一件事。
工程**没有 git**，恢复路径是：`.qoder-cn/file-history` 里 ⑬ 收工时的快照（132,332 B / 1,041 行，作为基座）
+ 从会话日志 `projects/.../<session>.jsonl` 里把该时间点之后**每一条**改动该文件的 `Edit`/`Write`/`Bash` 原样取回，
按时间顺序在**沙箱副本**里重放（48 条 bash 重放 rc=0、11 条 Edit 命中、1 条 Edit 因两侧文本都已存在而跳过、
1 条 bash 的 SyntaxError 与原始那次一模一样 ⇒ 说明重放忠实）。
还原后 164,888 B / 1,308 行，再与截断前当场读过的行号逐条对齐（`BLOCKED:88 / TEST_STATUS:101 / BUILD_STATUS:106 /
DEVICE_STATUS:114 / ## 常驻自检:302 / 表格 318/319/320/321/322/323`）—— 全部命中才算恢复成立。
**新增硬规矩**：① 就地重写台账一律"写临时文件 + `os.replace`"，绝不 `open(path,'w')` 直接开真身；
② 每轮动台账前先 `cp AI_ITERATION_STATE.md /tmp/ledger_base_$(date +%H%M%S).md`；
③ 恢复的判据是**行号 + 锚点双向对齐**，不是"看着差不多"。

**后果与遗留**：Phase 1 那句验收从"读证"变成"9 条用例 + 13 条判据 + 12 条结构负控 + 6 条用例级负控"。
登记 **V-2b**：**冷启动不复核 `/driver/profile`** ⇒ 本机残留 `DRIVER` 角色而档案已失效的人，会直接落在司机壳里
（拿到的是诚实的 `ErrorState(DRIVER_UNBOUND)` + **0 条司机请求**，不是假空列表，但人在错壳里，唯一出口是"用户版"tab）。
要修是在会话恢复处加一次档案复核并据此降级角色 —— **这是新机制，不在验收轮顺手加**。
另有真机未验项：`buspost://app/…` 的通知深链只有 JVM 侧纯函数判据，Intent 解析与 `NavHost` 找不到路由时
`runCatching` 是否兜住，只能真机验（`ENVIRONMENT_BLOCKED`）。

---

## D-029 后端没有异常类型枚举时，"七类异常"只能作为**客户端话术**拼进自由文本 `remark`；而 remark 的真实落点是两张 `varchar(500)`，预算必须算上后端自己加的前缀（2026-09-29，V-3 / 指令 v2.0 §54）

**背景**：指令 §54 逐字列了七个分类（车辆故障 / 道路阻断 / 收件人不在 / 货物异常 / 无法交接 / 天气影响 / 其他），
并要求"上报后段与订单转异常、由调度重排"。授权原话仍是"后端没有对应的东西安卓可以自己写，只是要符合架构要求"。
**但这一条后端不是"没有"，而是"有一半"**：服务层早就写好了，缺的只是一个 HTTP 入口。

**开工先核的六条后端事实**（全部当场读源码，`file:line` 见审计 §75 一）：
`AppDriverLegActionReqVO` 只有 `driverId/legId/photoUrl/remark` 四键（**没有任何类型枚举**）；
`DriverAppServiceImpl:1498` 的 `case "exception"` 是服务层白名单里现成的动作名；
`reportLegException:1503-1519` 已经把"段转异常 + 订单转异常 + 记事件 + 告警后台 + 通知下单人"做完；
`remark` 落进 `transport_order_event.detail varchar(500)` 与 `transport_user_notification.content varchar(500)`，
两处都由后端**自己再加前缀**（"第 N 段异常：" / "订单 {id} … ，请重调度"）；
`TransportLegStatusEnum:42-55` 允许一切非 `COMPLETED` → `EXCEPTION`，`forceLegStatus:732` 的 `canTransit` 守卫
让 `EXCEPTION → EXCEPTION` 幂等早返回；后端**零 `@Idempotent`**。

**决策**：

1. **分类不进报文**。七个 `enum` 只是 UI 话术，出口只有一句 `remark`：`道路阻断：塌方，中断约 3 小时`。
   **绝不发 `type`** —— 那字段后端不收，轻则被丢弃、重则被当成契约漂移。契约测试用 `keysOf(body)` 钉死
   `setOf("driverId","legId","remark")`，多一个键就红。
2. **补的是入口不是业务**：`AppDriverController.java:226-231` 六行，只调 `legAction("exception", reqVO)`。
   不改服务层、不加字段、不动别的端点 —— "后端没有的东西安卓可以自己写"不等于"把后端的业务重写一遍"。
3. **预算 200 是算出来的，不是拍出来的**：`varchar(500)` − 后端前缀（最坏约 40）− 余量。
   ⚠ **上一轮记的"`transport_leg.remark varchar(255)` 是落点"被本轮证伪** —— `applyLegStatus:933` 只写 `status/actual*`，
   整个 `MultiLegServiceImpl` `grep remark` 零命中。**台账里被源码推翻的数，以源码为准并记下推翻过程**。
   越界不是"被截断"，是严格模式 1406 → **整笔事务回滚**，即"报了异常、段状态也没改"，所以宁可客户端先截。
4. **诚实回执优先于成功文案**：三条不出网分支 + `notSent`/`demo`/回读缺段/回读状态未变 四类风险话术，
   页面**没有**"上报成功"四个字（判据 EX-g 钉"这三个字出现 0 次"）；段状态一律以回读为准，页面不 `copy(status=…)` 自封。
5. **路由不带参数**：`driverException` 而不是 `driverException/{legId}` —— `deepLinkTarget` 按**精确集合成员**判分域，
   带参数就绕过闸门；段号走 `AppState.openException()` 一次性意图。加这一条路由**同一轮**动了五处
   （集合字面量 / 探针 `DRV` 正则 / SR-h 负控锚点 / "就是那四条"用例 / SR-a 下限 9→10）。

**本轮判据自身被抓到的四条缺陷**（都写进了审计 §75 四）：
① 用例拿 `EXCEPTION_REMARK_MAX` 证 `EXCEPTION_REMARK_MAX` 是**同义反复**，上限抬到 2000 全绿 ⇒ 改字面量 200；
② "恰好 N"的判据数的是**关键字出现次数**（`canReportException` 实测 10）与**子串匹配**（`= 200` 命中 2000）⇒ 改成钉整行字面量、每条各配破口；
③ 只 copy 部分文件的负控沙盒让**基线**先红（I-e 从 4 到 5 是因为多了第四个司机页），而那份负控没有基线闸门仍打出 11 个 ✔
   ⇒ 整目录 copy + 给 `eta` / `reach` 两份负控补上同一道闸门；
④ 与 §54 无关但被同一轮顶掉的两条下限：S-le `=2 → =3`（新增 S-le2 逐文件验"写完必回读"）、I-e `4 → 5`。

**后果与遗留**：§54 从"没有实现"变成"13 条新用例 + EX-a…EX-i 判据 + 21 条结构负控 + 8 条用例级负控"，
全量 **26 类 / 267 tests / 0 failures / 0 errors**。
**三条尾巴如实记**：① 跨仓那六行**没有 git 可提交**（工作区非仓库，`ENVIRONMENT_BLOCKED`）也**没部署到 48080**，
所以"司机报异常→后台收到→调度重排"的端到端**未验证**，只能说 JVM 契约闭环；② `ActionGate` 仍是进程内，
杀进程重启后 10 秒保留窗失效（V-1 已登记，异常上报继承同一边界）；③ 七个分类与后端 `ORDER_EXCEPTION` 之间**没有码表关系**，
后端若将来真加异常类型枚举，这层要改成读后端码而不是继续拼字符串（已写进 `DriverExceptionReport.kt` 的 KDoc）。

## D-030 后端的"你不是司机"是一句 `code=0 + data 空`，不是一个异常 —— 所以守卫必须三态、且必须**单向**（2026-09-30，V-2b / 指令 v2.0 Phase 1 硬规则 1）

**背景**：V-2 收口时登记的残留是"进壳的两个输入不同源"—— 落点只读本地 `Prefs.role`，
而"是不是司机"的权威在后端画像里（`AppAuthLoginRespVO` 只有 `userId/accessToken/refreshToken/expiresTime/openid`
**五个键，没有任何 role**，角色既不上送也不回传，见 D-024）。本轮按队列做 V-2b：把权威接进来。

**开工先核的五条契约事实**（全部当场读源码，`file:line` 见审计 §76 一）：
1. `DriverAppServiceImpl.profile():167-171` 在 `currentDriverOrNull()==null` 时 `return null` ⇒ 信封是
   `{"code":0,"data":null}`，**HTTP 200、code 0**。后端的否定**不是错误码**。
2. `Api.kt:314-315` 把 `data:null` 读成**空 `JSONObject()`** ⇒ 旧代码里那句 `o.length() > 0`
   让"清绑定"分支成为**不可达代码**：函数写得再对也永远不执行。这是本轮的根因，也是"撤销/降级"类逻辑的通病
   —— 它的失败形态不是抛异常，是**安静地不执行**。
3. `bindDriver()` 与 `driverProfile()` 打的是**同一个端点**，判据却是两套（一处看 `driverId>0`、一处看 `length()>0`）
   ⇒ "一份答案写两遍"在这里的具体危害是：一处能撤档、另一处不能。
4. `driverShifts()/driverPickups()` **不经过** `driverIdParam()`（后端按登录会员识别）
   ⇒ 撤档后光清 `driverId` 拦不住这四条请求，**只有早退**能拦 —— 这正是审计 §29 那条越权探测面。
5. 后端 `TransportLegStatusEnum` / 状态机与角色无关，**没有任何"降级"接口** ⇒ 降级只能是客户端的"停止进入司机域"，
   不是"通知后端改状态"。

**决策**：

1. **三态，不是布尔**：`nav/ShellDecision.kt` 的 `serverSaysDriver(driverId, failed, demo): Boolean?` ——
   `true`（回包有 `driverId>0`）/ `false`（成功回包但没给 `driverId`，含 `data:null` 那种空对象）/
   `null`（**请求失败或演示回落 = 未知**）。
   **为什么不能退回布尔**：一次网络抖动就会被读成"后端说我不是司机"，把正在跑班的司机锁在司机端外面；
   反过来把 `null` 当 `true` 则等于闸门永不生效。`SR-d2` 钉的就是这个**返回类型**（行为由用例钉，类型由判据钉）。
2. **单向降级**：`reconcileRole(stored, server)` 只在 `stored==DRIVER && server==false` 时返回 `USER`，
   **永不把用户升成司机**。升级等于替后端造一个它从不下发的角色 —— 指令 §5（不要问用户选角色）与 §123
   （不许替后端编字段）在这里的具体形态就是"看起来更智能的双向同步"。
   所以这条"单向"由 `降级是单向的：永远不会把用户升成司机` 一条用例显式钉住。
3. **校正点放在进壳之后的第一条档案请求**（`WorkbenchScreen.syncFromServer()` 头部），**不放在 `BusPostRoot`**：
   放前面要把网络拉取提到导航之前，那是第二次机制改动（启动路径 + 加载态 + 无网冷启动全要重做）。
   代价如实记在册 —— 两个纯函数各只有 **1** 个调用点（RR-c 实测），冷启动那一帧仍可能先落司机壳再被弹回。
4. **降级必须三件事同一块**：改角色 + 出司机壳（`navigate(Routes.USER_SHELL)`）+ 停位置上报
   （`DriverLocationService.stop`）。只改角色 = 前台服务继续以"已不是司机"的身份上报点位，那是**假上报**的一种；
   RR-d 用整行字面量钉"三件都在同一个块里"。
5. **两套判据收成一条**：`driverProfile()` 与 `bindDriver()` 现在共用同一谓词
   （`if (bound) Prefs.driverId = … else if (!p.demo) Prefs.clearDriverBinding()`），
   `RR-b` 钉旧 `o.length() > 0` 在两个函数体里的残留 = 0，`RR-b3` 钉新整行 = 1。
   **"演示回落不算后端否定"** —— demo 分支不是后端答的，拿它清绑定就是自己造了一次撤档。
6. **第一版设计已完整回退**（`ProfileAnswer` / `deniedByServer` 全工程 grep **0** 命中）：
   它把"未知"再包一层信封，行为等价但工程里出现**第三种说法**，与"决策必须是唯一答案"（V-2 ①）冲突。
   回退成本一次编辑，留着成本是下一轮分不清谁判态。
7. **不为变绿放宽闸门**：`SR-d` 下限 2 → 4 与新增 `SR-d2/d3` 是和那两个纯函数**同一轮**落的；
   `DomainSplitTest` 那条旧用例原本把 `data:null` 归进"未知"，本轮读源码后**方向反过来**（`data:null` 正是后端唯一的
   否定形状），改写同时在 KDoc 记下"前提被证伪"，没有悄悄改掉断言。

**判据与测量自身的六条缺陷**（明细在审计 §76 四，四条已升为 §八 ⑰ 与 §六 的永久规矩）：
其中两条最贵 —— ① **RR-c 原本写绝对行号**，加一段 KDoc 就漂、漂了不报错只是永远绿 ⇒ 改相对顺序判据
（`DEN < SFT`、`RET ∈ DEN+1..DEN+22`）；② **`bash s 2>&1 | tail -6; echo $?` 取到的是 `tail` 的退出码**
（与 §六 V-2 ② 的 `gradlew | tail` 同族，第二次踩）⇒ 取退出码一律 `out=$(bash "$s" 2>&1); rc=$?`。
另有一条**台账层面的自纠**：草稿写过"四本台账从没记过 release 的 HTTPS 闸门"，grep 当场证伪
（`AI_ITERATION_STATE.md` 生产化阶段、`DECISION_LOG.md:85`、审计 §1104 都记过 `build.gradle.kts:160-176`）
⇒ 新规矩：**台账里"从未 / 没记过"这类全称否定，写之前必须先 grep 并把命令与输出留在当轮**。

**后果与遗留**：V-2b 从"登记在册的偏差"变成 **9 条新用例**（`RoleReconcileTest` 5 + `WorkbenchRoleReconcileContractTest` 4）
+ RR-a…RR-e 判据 + 13 条结构负控 + RRt-1…RRt-6 六条用例级负控，全量 **28 类 / 276 tests / 0 failures / 0 errors / 0 skipped**
（24 个任务 `executed`，`UP-TO-DATE`/`FROM-CACHE` 不计入）。**撤档后实测**：`Prefs.driverId` 61→0、请求 5 条→**1 条**、
角色 DRIVER→USER。
**三条尾巴**：① **真机未验证** —— 深链 `buspost://app/<tail>` 点开落哪个壳、降级时前台通知是否真的停，
仍是 `DEVICE_STATUS: NOT VERIFIED ON DEVICE`（无 Robolectric、无设备）；② **`release` 产物从未产出** ——
`assembleRelease` 配置期即失败（`release 包必须使用 HTTPS（当前 http://1.15.29.107/api）`），
按"如果构建失败不要修改代码来绕过"**原样 `BLOCKED`**，解锁顺序 = 先有 https baseUrl 再补 keystore，
本轮只重跑过 `assembleDebug`；③ **降级点在"进壳之后"**，`startDestinationFor` 用的仍是本地那一份 ——
要把画像拉取提到导航之前，那是第二次机制改动，本轮不做、也不谎称做到。

---

## D-031 导航能力"未验证"的正确出路是**先取证再落地**：换依赖拿到 javap 证据，把判据留在零 Android 类型的纯层，SDK 那一层薄到没有说谎空间（2026-09-30，N-1 / 路线图第 8 步）

**问题**：`AI_ITERATION_STATE.md` 的 BLOCKED #2 与 `THIRD_PARTY_NOTICES.md` §3 第 3 点记着同一件事 ——
解析到的 `3dmap:10.0.600` jar 里 `com/amap/api/navi/` **零类**，所以 §18 的 `NavigationEngine`、
Phase 3–5 全部挂起。指令同时写着两条不好同时满足的要求：§22 "不许凭博客或记忆猜 API"、
规则 15 "turn-by-turn 优先用高德导航 SDK"。**"两条都遵守"的做法不是放弃，是把 SDK 的存在变成可测的事实。**

**取证得到的（全部来自实际 jar 的 javap，明细见审计 §77 一）**：`AMapNavi.getInstance(Context)` **throws** `AMapException`；
`calculateDriveRoute` 有五个重载，返回的是**受理与否**；`AMapNaviPath` 给 `getCoordList()/getAllLength()/getAllTime()`；
`NaviInfo` 给八个读数；`NaviType` 是 `int` 常量类不是 enum；**没有任何公开的 `isInited()`**；
`PathPlanningStrategy` 里 `DRIVING_DEFAULT` / `DRIVING_SHORTEST_DISTANCE` / 两个 `DRIVING_MULTIPLE_*` **带 `Deprecated`**，
`DRIVING_AVOID_CONGESTION`(=4) 不带。

**决策**：

1. **顶替而非并存**：`com.amap.api:3dmap` → `com.amap.api:navi-3dmap:10.0.600_3dmap10.0.600`（依赖坐标数仍是 **20** 条）。
   并存 = duplicate class，与历史上 `com.amap.api:location` 那颗雷同族。判据 N-i 钉住 1/0/0（新构件 1、旧构件并存 0、location 并存 0）。
2. **分两层，判据住在不碰 Android 的那一层**：`NavigationEngine.kt`（`readinessOf` / `guidanceOf` / `naviRouteCheck` /
   `nextNavigableStop` 全是纯函数）+ `AmapNavigationEngine.kt`（唯一允许 `android.*` 与 `com.amap.*` 的地方）。
   工程只有 JVM 单测 ⇒ 不分层就没有任何一条判据能钉住"假步进 / 二次换算 / 闸门旁路"。与 `nav/ShellDecision.kt` 同手法。
3. **状态用 `StateFlow` 推，不用 getter 拉**：`readiness` / `guidance` 一旦是 getter，页面就得每帧去读，
   那是用本机定时器的节奏猜引擎节奏 —— §24 那 6 处假步进正是这么长出来的。实测 N-h = 2/2/0（两个 Flow、两处订阅、**0 处轮询**）。
4. **`EngineReadiness` 三态而不是两态**：没有 `isInited()` 可查 ⇒ "回调还没回"必须能说出口，
   否则实现被迫把"未验证"报成"不可用"（同一类错误的 UI 版就是"初始化中"被念成"这手机不能导航"）。
   判序也钉死：**先问类在不在，再问 key，最后才问回调**（M3 负控专门把顺序倒过来，用例立刻红）。
5. **算路策略是取证换的，不是偏好换的**：第一版写 `DRIVING_DEFAULT` 触发编译期弃用警告，
   于是 `javap -v` 扫 `Deprecated` 属性，改成 `DRIVING_AVOID_CONGESTION`；
   **也**不取 `DRIVING_MULTIPLE_ROUTES_*` —— 多路径要额外约定"选中哪条"，而 `getNaviPath()` 的选中语义真机未验，
   不引入未验证分支。超时上限 20 秒的理由同一条口径：没配 key、无网、离线区域高德都**静默不回调**。
6. **一条请求在飞**：回执是进程级单回调，两个并发算路会互相顶掉，前一次只能报成"引擎超时"——
   那是把调用方的并发问题念成引擎故障。宁可回"已有一次算路在等回执，请稍后重试"。
7. **`destroy()` 故意不调**：`AMapNavi` 是进程单例，页面退出就销毁会把下一次算路一起带走；
   这一层只 `removeAMapNaviListener`（`release()`），彻底回收归应用级生命周期。
8. **目的地规则里"失败点不跳"**：`nextNavigableStop` 只排 `COMPLETED`(7)，**不**排 `FAILED`(8) ——
   后端说"这次操作没成"，从没说"不用去那儿"；客户端跳掉它等于替调度改路线（硬规则 3）。
   失败点的处理出口是异常上报（V-3），不是本机静默重排。M6/M7 两条负控专门钉这条。
9. **坐标只搬运不换算**（§17 的二次偏移）：`getCoordList()` 本身就是 GCJ-02 ⇒ `geoPoints()` 只做 `NaviLatLng → Geo`，
   并且**不许**在这条路上再套 `Locator.toGcj02`。钉法两处：用例 `高德折线不做任何二次换算` + 判据 N-c=0 + 坐标豁免改为**计数**（L-b2 恰好 1 处）。
10. **替身引擎而不是第三种实现**：不可用时返回 `BlockedNavigationEngine`，每个出口回同一句真实原因、
    `guidance` 恒为 null。工厂里**没有第三种返回**（N-f = 1/1/2）—— 有第三种，就会有人拿"直线 + 定时器"冒充它。
11. **顺带把闸门的措辞跟着来源走**：`RealRoadRouteValidator` 的 `"后端没有下发道路折线"` →
    `"${source.label}没有返回道路折线"`。这道闸门现在守两条路（后端折线 + 高德折线），
    固定主语会把高德的失败念成后端的锅。改前 grep 实测三份判据/文档**零耦合**，改后 12 套探针全 rc=0。
12. **没有为变绿放宽任何判据**，也没有为省事提前删掉那句过时文档：`DriverNavScreen` 的 KDoc 与副标题原本写着
    "无需地图 SDK"，接了 SDK 之后它变成**假话** —— 本轮改的是这句**声明**（改成"这一屏的转向只读后端下发折线，
    SDK 产物住在 `DriverSdkNavCard`"），并新增 N-l 钉残留 = 0。**先改文档再改代码会留假凭证，只改代码不改文档会留假陈述**，两边同一轮收。

**判据与工具自身的八条坑**（明细在审计 §77 四，其中**五条升为 §八 ⑱ 的永久规矩：②③⑥⑦⑧**）：
① `Class.forName(...) != null` 在类缺失时是**抛**不是回 false，且 `AMapNavi::class.java` 会在编译期固化引用
（R8 裁掉类时"没有"会被读成"有"）⇒ 收成唯一一个 `naviClassPresent()` + 字符串类名；
② **`local code; code=$?` 读到的是 `local` 的退出码**，第一版用例级负控因此把七次真红报成"用例仍然全绿"
—— 负控工具说谎比没有负控更糟，它给出的是"验过了"这种假凭证；
③ **文件级 `grep -v` 是无界豁免**（负控 BREAK2 实测"豁免文件里再多一处手写坐标"仍 rc=0）⇒ 豁免必须**计数**；
④ **破了编译的变异不算证词**（NOXML 要单独判，不许混进"红了"）；
⑤ 从 python 里 spawn `python -` 会继承父 stdin 而**永远挂住**；
⑥ 用 `sed -n "/# M1 /,/case_run M1 /p"` 这种近似切法自检变异注入，八条全报"命中"而实际**一条都没注入**
⇒ 自检脚本也要有"锚点没命中就 exit 2"的硬失败，并且提取规则必须真复刻 shell 的引号语义。
⑦ **本轮我自己改错了一处有出处的数**：怀疑 "+68.6 MB" 没有基线，就把它改成 `58,410,069 B / +68.1 MB`，
而那个 58,410,069 是**更早一轮**（APK mtime 14:33:58）的在册值、不是换依赖前**紧邻**的那一次；
紧邻基线在状态板 `BUILD_STATUS`（V-2b 真重打包 **57,941,374 B**）⇒ 四处台账本轮改回 **+68,575,313 B ≈ +68.6 MB**。
**永久规矩：改一个数之前先把它的出处找出来，出处不成立才改；"我觉得这句没依据"不等于"这个数错了"。**
⑧ **两个 gradle 调用同时在飞会造出假红**：后台全量取证未完时前台起 `--tests … NavigationContractTest`，
实测 `failures=19/19`；`./gradlew --stop` 后单跑 `exit=0 failures=0/19`。
**永久规矩：一台机器一次只跑一个 gradle 调用**，并行等出来的红不是语义的红。

**后果与遗留**：第 8 步由"纸面架构"变成 **19 条判据层用例 + N-a…N-l 十二条判据 + 18 条结构负控 + 8 条用例级负控**，
全量 `--rerun-tasks` **29 类 / 295 tests / 0 failures / 0 errors / 0 skipped**（24 任务全 executed），
`assembleDebug` rc=0 / APK **126,516,687 B**、`lintDebug` **0 errors**。
**四条尾巴**：① **真机零验证**（`verify_device.sh --no-build` 实测 exit 1、设备列表空）——
`onInitNaviSuccess` 会不会回、§77 二那两条单位/语义口径、后台定位、TTS、导航视图全部 `NOT VERIFIED ON DEVICE`；
② **"初始化回调永远不来"在 JVM 层无法与"还在初始化"区分**，所以这层没给"初始化等待上限"这个数 ——
编一个数就是假判据，等有真机再定；③ APK 体积 **126,516,687 B**，较换依赖前**紧邻**的在册读数 57,941,374 B
（状态板 `BUILD_STATUS`，V-2b 那次真重打包、APK mtime 23:59:33）多 **+68,575,313 B ≈ +68.6 MB / 2.18 倍** ——
⚠ **跨轮对照，不是"换依赖前后"的受控 A/B**，本轮没有为量体积把依赖回退重编过一次；
归因**收台账时已改成实测**（明细在审计 §77 七）：包内高德 `.so` 净增 **+43,681,128 B**（占差额 63.7%，
由两件 jar 的 `lib/` 解压量相减得到），其余 **24,894,185 B 只是残差**——上一轮包的 dex/assets 未留档，**增量分不出来**；
原来那句"约 500 行撑不起 68 MB"的量级话作废；
高德专有许可证已回填 `THIRD_PARTY_NOTICES.md` §3；
④ 本机引擎**不取代**后端 `routeGuide` 那条线路事实，两者互不顶替；`#51`（旧屏 DEBUG ONLY）、
§24 假步进 6 处、`release` 产物、git checkpoint 四条一律未动。

**N-1-d 收台账追加一条决策（D-031 之内，不另起号）**：**台账产物本身纳入判据范围** ——
新增 `tools/check_ledger_tables.py`（五本手写台账的表格行结构自检，报告 `tools/ledger_table_report.txt`）。
**Why**：本轮抓到 **6 个坏表行**（一处被拆成三条物理行、至少坏了一轮；五处把未转义裸竖线写进代码段），
而这类缺陷**不报错、只是不渲染**，等于台账里有一段"写了但没人能看见"的记录。
**代价/边界**：判据先负控过才采信（M1 拆行 / M2 剥转义 / M3 并行各 rc=1，原件副本 rc=0）；
口径是"同表内每行未转义竖线数 = 表头行"，**不是全局常数**（写成"每行必须 4 个竖线"会把五列表全判错，我第一版就犯了这个）。
规矩正文在 `CONTEXT_LOCK.md` §六 V-1 ④，本轮实测在状态板"§八 探针 N-1 复跑"末尾的 N-1-d 段。

---

## D-032 "删掉假导航"不许顺手删掉真导航：#51 的正确顺序是**先搬迁能力、再上闸、最后才改文案**（2026-09-30，D-003 执行 / §24 假步进 6 → 0）

**问题**：D-003 挂着"待实施"三周，因为它同时被四句话夹住 ——
①"D-003：旧 `DriverNavScreen` 降级为 DEBUG ONLY"、②用户硬规则"**不要直接删除文件**"、
③§24"假步进 6 处 → 0"、④"宁可暂时不能导航，不允许假导航"。
前三句靠"给旧屏上一道 `BuildConfig.DEBUG` 闸"就能同时交代，**第四句不行**：
本轮 grep 实测 `DriverSdkNavCard(` 全工程**只有 1 个调用点，而它就挂在要被关掉的旧屏里**
⇒ 直接上闸会让 release 里唯一能驱动本机高德引擎的入口一起消失。
**"不允许假导航"的正解不是"没有导航"，是"把真的那条路先接上，再关假的"** —— 顺序错了就是功能倒退，而且倒退得很安静。

**决策与执行**：

1. **先搬迁，后上闸**（本轮真正的工程）：正式导航面重新落位在 `WorkbenchScreen` 的"本班指引"那一节 ——
   `DriverMissionMap`（后端折线）+ `DriverSdkNavCard`（本机高德引擎）+ 卡片底部新增的**外部地图交接行**。
   外部地图不是可选项：小程序司机端"导航"的实现就是 `wx.openLocation` / `openAmapNav`
   （`utils/nav.js`、`routes.js:160`），语义是"交给外部地图"。Android 只留内嵌引擎**不算对齐**，
   所以 `openAmap/openBaidu/view` 三个 helper 从旧屏**搬进** `DriverSdkNavCard.kt`（搬，不是复制 —— 旧屏里现在为 0，判据 N-p 钉）。
2. **交接对象只准是调度给的那个点**：两个按钮都读同一个 `nextNavigableStop(guide)`；
   它为空时 `enabled = false` 且文案写成"外部地图：没有任务点可以交给它（导航不自己决定去哪里）"，
   **不许**退回 `guide.stops.firstOrNull { it.hasGeo }` 那类"本机自己挑一个"。
   负控 `N-g 目的地改成本机自选` 专门打这一条，实测会红。
3. **闸与用同一物理行**（判据形状倒逼的写法，不是风格偏好）：
   `if (BuildConfig.DEBUG) composable(Routes.DRIVER_NAV) { DriverNavScreen() }`、
   `onNav = { if (BuildConfig.DEBUG) nav.navigate(Routes.DRIVER_NAV) },`。
   N-n 要判"**每一处**使用点都带闸"，而跨行的 `if { … }` 用单行 grep 判不出来（判"下一行有没有 if"不是 grep 的能力）。
   代价是几行偏长；收益是拆闸必须改那一行，而改它会红。**这是 §六"判据能判的形态优先于好看的形态"的第三次应用**（前两次：V-2 提纯函数、N-1 分两层）。
4. **两道闸，不加第三道**：注册处不注册（正式包没这条路由）+ 组件第一句自守
   `if (!BuildConfig.DEBUG) { DebugEntryClosed(); return }`。第二道是给"**我没看到的那条入口**"留的：
   `nav/ShellDecision.kt` 把深链解析整段包在 `runCatching` 里，残留深链不该在 release 渲染出任何指引。
   **没有**拆 `app/src/debug/` source set：那要把文件搬走 = 历史断档，与"不删除文件"冲突，收益不值。
5. **`Routes.DRIVER_NAV` 常量、`onNav`/`onOpenNav` 形参、旧屏文件本体全部保留**（兼容规则"不要删除字段以免破坏旧兼容"）。
   **物理删除的只有那套假步进机器**：`var stepping`、`LaunchedEffect(vm.stepping)` + `delay(4_000L)` 循环、
   DEBUG 那个"逐条推进"开关。`next()` 改名 `showNextStep()`，语义收窄成"司机自己翻页"，
   注释写明"车的位置与班次状态都不由它推进，推进只认后端 `currentStep` 与 GPS"。
6. **§24 归零的同时把口径抬高**：`check_authenticity_counts.sh` 的假步进那条原来只 grep 旧屏**一个文件** ——
   挪个文件就"归零"。本轮改成**整个 `ui/driver` 目录** + 含注释的裸计数（判据 §八 ⑲ N-m 同口径）。
   **九条白名单一字未改**。⇒ 与"禁为了变绿放宽判据"是同一条规矩的两面：**归零必须伴随口径变严，否则归零没有信息量。**
7. **"非真实导航"要成为屏幕事实**：页首横幅 + 分节标题 + 车道箭头说明三处（判据 N-o 钉 ≥2/1）。
   第三条立了一条新口径：**"猜出来的图形"和"编出来的数据"是两件事，但都必须说出来** ——
   后端只有车道**名**、没有车道图形字段，所以 `laneIcon()` 是按名字猜的，屏幕上明写"调试看效果用，不算车道级引导"。
   `NavIcon.STRAIGHT` 枚举成员保留在宽口径白名单里（豁免的是**图标名**，不是**路线几何**），这条豁免本轮**没有**扩到任何新文件。
8. **线路页的导航入口只在 DEBUG，并且写下为什么**：`TransportLeg` 只有站名、**没有任何坐标字段**（§36 在册偏差本体）
   ⇒ release 里拼不出 `destination=lat,lng`。硬要能跳就得拿站名反查一个点，那是 §36 禁止的补法。
   ⇒ **功能少一条，比功能假一条好**；且这条"少"是后端契约缺口，不是客户端偷懒，理由就写在按钮旁边。
9. **N-g 第六位从"≥1"改成"恰好 1 且锚在 `WorkbenchScreen.kt`"**（本轮唯一的判据语义变更，两个方向都会红）：
   0 = 正式包没有导航面（本轮要避免的那个事故），≥2 = 两处各自 new 引擎 = 进程级单回调被两个持有者抢
   （§77 三"同一时刻只允许一次算路在飞"那颗雷的 UI 版）。**收紧的正当性来自本轮实测到的旧结构本身是红的**，不是"看起来更严"。

**判据与负控扩建**：导航簿 12 → **16** 条（新增 N-m 目录级假步进 0、N-n 闸门 0/1/1、N-o 标注 2/1、N-p 能力搬迁 0/1/1/0），
结构负控 18 → **27** 条破口（新增九条：定时器塞回引擎 / 注册去闸 / 工作台入口去闸 / 自守变 `if (false)` /
横幅删除 / 车道说明删除 / 引擎搬回旧屏 / 交接目的地换成 `Geo(0.0, 0.0)` / `openAmap` 搬回旧屏）。

**一条关于"负控该不该跳过"的决策**：用例级负控本轮**照跑**，没有用"它只锚 `NavigationEngine.kt`，而我没改那个文件"来跳过。
**理由**：那个文件的 mtime（01:34）晚于上一次该脚本实跑，而 **mtime 不能证明"上次跑的时候树就是现在这样"** ⇒
跳过就成了自述。实测：基线 `exit=0 failures=0/19` → M1…M8 八次全红且红在该类（M5 红 4 例）→ 恢复逐字节相同 → 复跑 exit=0。
**永久规矩：不能用"改动范围与判据范围不相交"当跳过负控的理由 —— 除非那个不相交本身是实测出来的。**

**后果与遗留**：D-003 从"待实施"变**已实施（结构 + 用例级证据）**；§24 假步进 **6 → 0**（删机器 + 抬口径，非放宽白名单）；
全量测试 **29 类 / 295 tests / 0 failures**（XML 读）、APK **126,516,687 B**、十二套探针 rc=0、结构负控 27 ✔、真实性宽口径 49 → 45
（**归因实测**：只有 `DriverNavScreen.kt` 8 → 4，其余 16 文件逐项未变，剩的 4 行全是禁止性说明）。
**四条尾巴（一律在册，不得当作已解决）**：① **闸门本身零真机验证** —— 正式包点进去是不是那句 `DebugEntryClosed`、
外部地图在装了/没装高德 App 两条分支、按钮禁用态窄屏是否折行，全在 `NOT VERIFIED ON DEVICE`；
② **线路页 release 导航入口没做**，解锁条件是后端补上段几何/站点坐标（§36/§44 同一条路），不许用站名反查补；
③ `release` 产物仍 `BLOCKED`（https baseUrl → keystore 顺序不变）、git checkpoint 仍 `ENVIRONMENT_BLOCKED`（本机无仓库）；
④ 旧屏那套"按名字猜"的图形**没有**改成不显示 —— 留着当调试参考并由 N-o 钉住"必须说明是猜的"，**这是选择不是遗漏**。

## D-033 候选键名列表是**第四个真相源**：三方都没有的键，删掉它的读法才是收口，给演示面补一个键是把它洗成"真契约"（2026-09-30，E-1 / 队列首项）

**问题**：解析层有 6 处 ETA 读法写成 `multiNumOrNull("etaMinutes", "eta", …)`，多出来的 `"eta"`（`BusVehicle` 那处还有裸 `"minutes"`）
既不是后端键、也不是小程序键、也不是演示面键。它之所以危险，是因为**它长得像防御性兼容**：
读不到 `etaMinutes` 就退而读 `eta`，看起来"多兼容一条路"，实际是**客户端自己凭空造了一个 ETA 生产者** ——
后端的诚实形状是"显式 null / 缺键"（`AppBusServiceImpl:134`/`:665`/`:719`），而这一行会把它变成一个数：
只要报文里真出现一个叫 `eta` 的东西（别的模块、别的版本、别人的字段），客户端就会宣布"车还有 N 分钟到"，
而这句话没有任何来源。§38 那条红线（"没给值不许有业务语义"）拦得住 `null → 0`，**拦不住 `缺键 → 猜出来的键`**，
因为它读的仍然是"一个存在的值"。

**三方实测**（本轮当轮重跑，不是沿用上一轮）：后端 `yudao-module-transport` 全模块 `"eta"` 字面量 **0 命中**
（同口径下 `etaMinutes` 在 java 里 **13 处**，证明 grep 不是找错了地方）；`cargo-post-miniprogram` 与 `miniprogram` 两棵树
裸 `eta` 用法各 **0 命中**；`DemoData.kt` `"eta"` **0 处**。⇒ 这个键**从来没有生产者**，删它不可能破坏任何真实兼容。
反证同时做了，而且用的是**改动前那棵树的整份副本**（`/tmp/ledger_replay`，mtime 09-29 21:18）：同一组 grep 打在那棵树上
读到 **T-ai=6 / T-ai2=6 / T-ai3=0**，打在当前树上读到 **0 / 0 / 0** —— 前三位证明判据**判得到**，
最后那位的"改动前后都是 0"证明演示面从来没有这个键。

**决策与执行**：

1. **删读法，不补键**。两条可选路里选了前者：
   ~~给演示面加一个 `"eta"` 键，让别名"有一个真生产者"~~ —— 那是**把自造的键洗成契约**，
   一旦做了，下一轮就再也无法证明这个键不该存在，而且 T-ai 的三方无生产者论据当场作废。
   因此本轮除了删 6 处读法，还专门立了 **T-ai3**（演示面 `"eta"` 键数 = 0）把这条诱惑钉死。
2. **`etaMinutes` 这个键本身留着，但把它的声明改成事实**。`RouteGuide.etaMinutes` 的旧注释写"…或后端原始 payload 里
   真给的那个键"，本轮实测：**真实报文里没有这个键**，全工程唯一的发送者是 `DemoData` 的 `/driver/route` 分支。
   注释改成这个口径（并写明 `"eta"` 曾是被删掉的第四个来源），代码保留 —— 因为**删掉它会顺手删掉演示能力**，
   而留着它 + 一句实话，比删掉它 + 少一条可跑的演示更符合"如实"。
   同理 `Repo.kt:962` 那处 `else o.multiNumOrNull("etaMinutes")?.toInt()` 保留：它是演示路径的落点，不是正式接口的兼容分支。
3. **`roadEtaSeconds` / `roadDistanceMeters` 不是 JSON 键**，是客户端字段名；真实键是
   `routeDurationSeconds` / `routeDistanceMeters`（`AppDriverRouteRespVO:48`/`:45`）。本轮顺手核了 `Repo.kt:933-934` 确认
   读法**已经**以真实键打头（`multiNum("routeDistanceMeters", …)`），所以正式包的全程分钟只可能来自道路耗时，
   与 #51 的"release 导航面"结论接上了。
4. **把"打印不判红"的残留升格成三条判据**（T-ai 具体、T-ai2 闭包、T-ai3 反诱惑），
   闭包那条用 `grep -rn … | grep -vE ':[0-9]+: *(\*|//)'` 排除注释行 —— **排除注释必须先有 `grep -n` 的输出形状**，
   没有 `-n` 时这个过滤器等于没写（本轮实测翻过一次，见 §79 六）。
5. **加用例的同一轮抬计数下限**：`BusEtaContractTest` 12 → **13**、`SendEtaContractTest` 9 → **12**，
   T-k / T-x 的下限与注释在同一轮改掉。上一轮的教训正是 pin 忘了抬（9 而实际 11）导致"注释掉一条"的负控制**从红变灰**。
6. **#51 那条永久规矩本轮被证明写窄了**：原文是"不能用'改动范围与**这套负控**不相交'当跳过理由"，
   实际发生的是 `#51` 改的那一行**正是 `shell_routing` 簿里 SR-f 的注入锚点** —— 同一簿的负控跑了、别的簿没跑，
   于是那条负控**静默失效了一整轮**才被抓到。规矩升级为：**改源码文本，同一轮要核所有簿的注入锚点**（或直接复跑所有簿）；
   代价实测：五套就地簿本轮共 **39** 次单类 gradle 调用，按 `/tmp/*_neg_*.log` 的**留档体数**清点
   （异常 10 = 基线+8 破口+恢复 / 幂等 2 / 壳路由 9 = 基线+7+恢复 / 导航 10 / 角色 8）；
   ⚠ 按脚本结构推算本来是 **40**（幂等簿当作 1+2）—— 实跑只落了 **2** 份日志，**台账改记实测体数、不记推算值**（这是本轮第五处自抓的错）；
   分段墙钟实测 04:04:13–04:06:32 / 04:13:36–04:15:53 / 04:16 / 04:16:47–04:18:35 / 04:18:44–04:20:51，**合计约 9 分钟**
   （daemon 已热、每次只跑一个测试类；原来写的"7 分钟、04:14 起跑"是推算，作废）。
   **锚点是源码文本，不是符号名** —— 任何一次改写文案/换写法都会作废它，包括"上一轮改的、另一个文件里的"。
7. **范围没有外扩**：同一批 `Minutes` 家族读法里还剩 **5 处**第二候选名
   （`plannedMinutes`/`waitDepartureMinutes`/`totalMinutes`/`plannedDurationMinutes`/`walkMinutes`，实测行号 Models.kt 479/729/1110/1328/1802）。
   本轮**没有**顺手删，因为它们的契约要逐端点重核（`waitMinutes` 之类完全可能是小程序真在用的键），
   而现在手里没有这份实测。判据里以"·"行如实打印这 5 处，**不当已通过**，已登记为下一轮队首。

**后果与遗留**：ETA 簿的别名判据 T-ai/T-ai2/T-ai3 实测 0/0/0，全量 `:app:testDebugUnitTest :app:assembleDebug --rerun-tasks`
rc=0（**43 actionable tasks: 43 executed**，本轮是**全量重跑**不是增量）、**29 类 / 299 tests / 0 failures / 0 errors**；
各簿读数与"APK 体积口径差 12,653,686 B 只能记为不可比"见 §79 八。
尾巴：其余 `Minutes` 5 处（下一轮队首）、`QuoteResult` 三个金额缺键塌 `0.0`、`Repo.optBoolean(…, true)` 默认放开、
真机仍 `NOT VERIFIED ON DEVICE`、`release` 仍 `BLOCKED`、git 仍 `ENVIRONMENT_BLOCKED`。



## D-034 "把缺陷洗成演示数据"和"把取消写成失败"是同一件事的两面：客户端不许替后端、也不替用户宣布结果（2026-09-30，F-1 / 指令 v2.1 §六 · §七 · §三十一）

**问题**：这条缺陷链上有两句谎话，而且都是**编译器完全不拦**的那种。
① `Api` 出网失败时兜底到 `DemoData`，但当时的兜底面写的是 `catch (e: Throwable)` —— 于是
`IllegalStateException` / `NullPointerException` / `ClassCastException` / `LinkageError` / `OutOfMemoryError` / `AssertionError`
这些**程序自己的缺陷**也被洗成"后端给了演示数据"，界面照常渲染、标签照常写"演示"，堆栈同时丢掉：
这一条违反的是"宁可没有上报，不允许假上报"的同族红线（宁可显示不出来，不许显示一个来历不明的数据）。
② `catch (e: Exception)` **会**接住 `CancellationException`（它继承 `Exception`），`runCatching` 接住 `Throwable`。
于是"页面已经划走 / ViewModel 已销毁 / 外层超时 / Worker 被系统停止"这一整类**取消**，被写成一次"加载失败"，
并且继续往已经没人看的 StateFlow 上写；`NotifyWorker` 那一处更直接 —— 吞掉取消之后 `return Result.success()`，
等于替 WorkManager 宣布"这一轮跑过了"。

**取证（先读代码再动手，三条前提被实测推翻，见下）**：
main 源码里 `catch (e: Throwable)` 生效 **6** 处、`catch (e: Exception | Throwable)` 共 **69** 处（F-1-a 的分类读数量）。
按"里面到底挂不挂起"分完之后的**当前**读数：`catch (e: Throwable)` 生效 **2**（都在白名单，见第 3 点）、
`catch (e: Exception)` **49** 处，其中 try 首段含挂起点 **48** 处（已全部先穿透取消）、不含挂起点 **1** 处
（`DriverSdkNavCard.kt:291` 唤起外部地图，`startActivity` 不可能被取消）。

**决策与执行**：

1. **回落面收成"两类真失败"，且每一处自带开关闸**。`Api.execute` / `Api.upload` 各两条：
   `NetworkException` 与 `ApiException` 才允许走 `DemoData.respond`，每条前面一行 `if (!demoAllowed) throw e`（TH-b 实测 4/4 一一对应）；
   正式构建里 `demoAllowed` 恒 false（`NetTestOverride.demoAllowed ?: BuildConfig.DEMO_ALLOWED`，release 那格是硬编码 `"false"`，TH-k 1/1）。
   兜底的 `Exception/Throwable/RuntimeException` 出口在 `Api.kt` 里归零（TH-l 的第三位）。
   行为侧由 `DemoExceptionIsolationTest` 7 条钉：ISE/NPE/CCE/LinkageError 四种探针在**演示构建与正式构建两种形态下**都必须原样穿出，
   并且 `server.requestCount == 0`（缺陷发生在组 URL 时，一个请求都不该发出去）；同簿另两条正向用例证明"传输 500 / 业务码失败"确实照常回落 ——
   **没有**把回落整个删掉来变绿。
2. **`attempt` 取代"有挂起点的 `runCatching`"，命名不抄指令原文**。§七 点名要一个 `safeApiCall()` 形状的 unified helper；
   工程里其实**已经有**这个形状：`apiCall`（`Api.kt:60`，`suspend inline`，先 `throw e` 穿透取消，五类 typed 分支，
   `ApiTest.kt:165` 还有一条"只做分类不吞异常"的用例）—— 但它**生产调用点实测 0**（判据 Z1=1 就是它自己的定义那一行）。
   本轮**没有**把 38 处读接口改成 `apiCall`（那是"改返回形状"的另一轮范围，混进来会让本轮的 diff 无法复核），
   也**没有**再发明一个同义 helper，而是立了 `core/net/Attempt.kt` 的 `attempt { }`：
   与 `runCatching` **唯一**区别是先 `catch (e: CancellationException) { throw e }`，其余收成 `Result`。
   `apiCall` 留在原地并登记为"已存在但没人用"，**不删**（删它要动 `ApiTest` 那条用例，是减量不是收口）。
3. **两处 `catch (e: Throwable)` 留着，且理由各不相同**：`Attempt.kt:22` 是"兜住一切**除取消之外**的东西，交给调用方判"，
   它自己不吞取消（TH-f 第二位钉的就是它先穿透）；`ActionGate.kt:63` 是"释放锁，然后原样 rethrow" ——
   把它改成类型化 catch 反而会让某些缺陷**不释放锁**，那比吞掉更糟。白名单写成"恰好 2"（TH-c 2/0/2），
   出现第 3 处就红，防止下一轮把"兜底"当成默认写法。
4. **`Repo` 的 38 处逐点配对，不是一把 sed**。`catch (e: Exception)` 每一处前面插入
   `catch (e: CancellationException) { throw e }`，改完 TD1/TD2 = **38/38**；
   同文件里 `openMeteoWeather` 的 JSON 块与 `NavIcon.valueOf` 那两处 `runCatching` **没动** —— try 首段里没有挂起点，
   给它们加取消出口是给不存在的通路开药（TH-e 的免检计数就是为这类判断留的口子，见第 6 点）。
5. **UI/服务侧按"取消到底从哪来"分三种收法，不是一种模板铺满**：
   `BusPostRoot` 开屏预取、`BusPostApp` 20 秒前台轮询、`NotifyWorker.doWork`、`DriverSdkNavCard` 的引擎调用、
   `Locator` 的取点、`HandoverScreen` 的照片上传 → 换 `attempt { … }`（TH-f 的 12 个调用点就是这批）；
   `Orders`/`Goods`/`Send`/`Widget`/`LoginViewModel`/`DriverLocationService`/`ScreenViewModel.work()` → 在既有
   `catch (e: Exception)` **前面**补一条先 `throw e` 的取消出口（TH-d 38/38 那批的 UI 侧对应物）；
   `WorkbenchScreen.kt:434-440` 的照片上传是第三种：**整圈 `runCatching` 仪式直接摘掉**，改成裸调用
   —— 那一圈原本只是"接住再原样抛出"，它唯一的实际作用就是在半空中接一次 `CancellationException`；
   给它补取消出口反而是给不存在的通路开药。**三种收法形状不同、结论同一条：没有任何一处把取消接在半空中。**
   `HomeScreen` 轮播**故意不是裸 `throw e`**：用户手指抢走滚动时的取消必须被咽下去（不然轮播会把自己停掉），
   所以写成"只有 Job 真的被取消了才 rethrow"，TH-e 的配对判据接受这种条件重抛（它要的是"取消不被收成失败"，不是某一行字面量）；
   `Locator` 那一处**照实交代可观测差为 0**（见下面"被推翻"第 ③ 条）。
6. **`TH-e` 这条判据本身被负控改了两次写法，是本轮最值钱的一条方法**：
   第一版是"数 `CancellationException` 关键字出现次数"，V-3 那一轮已经证明这种数法会**永远绿**
   （关键字在页面里出现 10 次，把两处守卫删掉计数照样 ≥2）；第二版扫一份 14 个文件的手写清单，
   结果**误判**了一处没有挂起点的 catch、而清单外的文件永远不进扫描面。
   最终版是"扫描面由内容决定"：逐个 `catch (e: Exception)` 看它 try **首段**有没有挂起点，有才要求同链先有抛出取消的出口；
   并且把**三个数一起打出来**（违例 0 / 挂起形状 48 / 免检 1），因为只打违例数的判据
   在"规则被写宽"和"破口被删干净"两种情况下输出一模一样。
   负控里有两条专门打这三个数：摘掉一处 `throw e`（TH-d 照样 38/38 绿 ⇒ 证"数子句"与"逐点配对"不是同一条）
   与"删掉一处挂起形状的 Exception 出口"（下限必须抓住扫描面缩水）。

**后果与遗留**：`check_throwable_probes.sh` 十三本探针里新增的这一本 rc=0（12 行读数 / 27 道闸门：
TH-a 7/5 · TH-b 4/4 · TH-c 2/0/2 · TH-d 38/38 · TH-e 0‖48/1 · TH-f 12/1 · TH-g 0 · TH-h 6/1/2 · TH-i 1 ·
TH-j 1/1/0 · TH-k 1/1 · TH-l 2/2/0）；负控十三本**全批复跑** rc 全 0、`✘` 与 `△` 全 0，
新簿 `throwable_negative_controls.sh` 实测 **✔ 行 19**（= 18 条破口 + 1 条末尾自检行）；
就地那五套因此全部重跑了一遍（⑲ 的升级规矩：**改源码文本 ⇒ 同轮核所有簿的锚点**，本轮 `Repo.kt`/`Api.kt`
被 52 处改写命中，是历来最大的一次锚点扰动面）。全量 `:app:testDebugUnitTest --rerun-tasks` →
`BUILD SUCCESSFUL / 24 actionable tasks: 24 executed`、**31 类 / 311 tests / 0 failures / 0 errors / 0 skipped**
（31 份 XML mtime 全为 **17:02:02**，逐份读出再自相加）；**+12 全部可归因**是机器比对的：
新增类只有 `DemoExceptionIsolationTest` **7** 与 `ApiCancellationTest` **5**，消失类 `[]`，
既有 29 类逐份条数一字未变（`CHANGED: []`）。APK **114,437,316 B** / **16:45:11**（16:4x 那次 `48 tasks: 12 executed / 36 up-to-date`），
收工复检 `48 tasks: 1 executed / 47 up-to-date` ⇒ **没有重新打包**，而这份 UP-TO-DATE 正是"包与当前源码内容自洽"的机器证明
（就地负控在 16:41–16:58 改过又恢复过真实源码，配合各簿 `cmp 一致`）；lint **0 error / 46 Warning / 32 Information = 78**
（XML 98,753 B / 16:47:20）与上一轮**逐项相同** ⇒ 没有引入新的 lint 类别。整批负控墙钟 **16:41–16:58 ≈ 17 分钟**（按 `/tmp/*_neg_*.log` 的 mtime 清点）。

**收台账这一遍自己又抓到四条"自述与实测不符"（都是真错，不是判据太脆，逐条当轮改掉）**：
① **负控条数又是推算的**：㉑ 里写"✔=18"，批处理按留档行数打的是 **19**（18 破口 + 1 自检行）⇒ 改成实测并写明构成，
   这是 ⑳ 第⑤条"次数只准抄留档产物"的**第三次**同族犯法；
② **`idempotency_test` 的 ✔=0 差点被读成"这本没跑"**：那个簿不打印 ✔ 标记（它的标记是 `[破口后] exit=1` / `[恢复] cmp 一致` /
   `[恢复后] exit=0`）⇒ 新立一条口径：**按标记 grep 出来的计数，先证那本簿用这套标记**；
③ **比对基线的类名写错会造出假差异**：手写成 `MissionMapContractTest` / `ProductOrderContractTest`（真实名无 `Contract` 后缀），
   第一次比对因此报"新增 5 类 / 消失 3 类"⇒ 改真实类名后才是 `[]`/`[]`。**"无孤儿类"这个结论依赖键名**；
④ **D-034 第 5 点原本把 `WorkbenchScreen` 写成"先穿透取消"**：实测那一处（`:434-440`）是**整圈 `runCatching` 仪式被摘掉**、
   改成裸调用，文件里既没有 `attempt {` 也没有取消子句 ⇒ 三种收法分开写清。
   ⚠ 这一条是**台账自述**而不是判据抓到的：TH-d/TH-e 只看 `catch (e: Exception)` 的形状，
   "本来就没有 catch"这一种收法**在任何判据里都是隐形的** —— 它靠的是读代码，读的时候正好在读到的一句假话上。
⑤ **同一条隐形还扩散到了"改了哪些文件"的清点**：本轮初稿按"三条 glob ∪ 编辑窗口 mtime"数出既有生产文件 **16** 个，
   而 `WorkbenchScreen.kt` 两条腿都不命中（无字面量可 glob、mtime 16:55 是就地簿 `cp` 恢复的时间戳）⇒ 实测 **17** 个。
   捞它的办法是**作差**：`comm -23` 拿"注释里引用 §三十一 的文件"与上面并集相比，差集恰好只有它一个。
   ⇒ 已升为 §八 ㉑ 第④条：**删除型改动不能只靠 glob 数，必须再做一次"本轮指令编号 vs 清单"的作差**
   （清单见 `THIRD_PARTY_NOTICES.md` §6 F-1 段、状态板 DONE「F-1」的"改动面清点"、审计 §80 七）。

尾巴（在册，不当已通过）：`apiCall` 生产调用点仍 **0**（只登记不谎称已用）；`runCatching` 在 main 仍剩 **34** 处
（本轮只收"try 首段有挂起点"的那批，`WorkbenchScreen.kt:1213` 的 `callDriver` 这类纯同步形状**没有挂起通路**、
记为"未收"而不是"已判无害"）；§二十 的 Worker 重试语义（真失败回 `retry` 而不是 `success`）**没做**；
§三十三 的四类失败分型仍只是台账口径，**没落进代码**；设备侧一律 `NOT VERIFIED ON DEVICE`
（真实取消来自手指划走页面 / Worker 被系统停止 / 进程被杀，JVM 只能造 `cancelAndJoin` 与 `withTimeoutOrNull` 两种）；
`release` 仍 `BLOCKED`、git 仍 `ENVIRONMENT_BLOCKED`。

## D-035 "别名键"的收口动作是**删读法**，不是给演示面补键：F-2 第 1–4 步（v2.1 落档 · Minutes 五处 · QuoteResult · NearbyResult）（2026-09-30，F-2 ①②③④ / 指令 v2.1 §三 · §四 · §二十四）

**做了什么**：① v2.1 原文落档 `docs/android/ANDROID_COMMAND_V2.1.md`（27,323 B / 1,618 行 / 35 节，逐字节回环校验通过）
+ `docs/android/ANDROID_V2.1_CHECKLIST.md`（V21-01…35 逐节对表）+ `tools/v21_requirement_census.sh`（需求普查，**113 行**）。
②③④ 三个 V21 节点各自立了一本判据簿并跑到 exit 0。

**为什么是删读法而不是补键**：一个键在**后端 VO 声明 + 后端 producer 赋值 + 小程序消费点 + 演示面下发**四面里都找不到生产者时，
它就不是"后端还没给"，而是**客户端自己造的第四个真相源**（与 D-033 同一条）。给演示面补上那个键，等于把它洗成"真契约"——
下一轮谁来读都会以为后端有。所以本轮的动作为：**只留后端实际键，删掉第二候选名**。

**实测读数（收工最后一遍，四簿同轮复跑）**：
- `check_eta_probes.sh` 新立 **T-aj 家族**：`T-aj 0 · T-aj2 0 · T-aj3 0 · T-aj4 7 · T-aj5 16 · T-aj6 6`，GUARD 别名模式在测试源码命中 **10**；末行"ETA 判据通过…**E-1 别名键归零**"，exit **0**。
  `T-aj` 数多候选读法、`T-aj2` 是**闭包判据**（换第一候选名也响）、`T-aj3` 盯演示面（demo 不得成为第四个真相源）、`T-aj4/T-aj5` 是**下限**（删掉真实键=契约断裂还静默）。
- `check_contract_defaults_probes.sh`（Q-* 族，§三 QuoteResult）：`Q-a 5 · Q-b 0 · Q-b2 0 · Q-c1 0 · Q-c2 0 · Q-c3 0 · Q-d 0 · Q-d2 0 · Q-e1 3 · Q-e2 4 · Q-f 7`，GUARD `pipeline=1 / 别名在测试源码=5`，exit **0**。
  五字段全可空 + 缺键走 `multiNumOrNull` 哨兵；界面只走 `amountText/amountValueText/feeBreakdownText`，
  **APK 内实测**（`unzip` 全部 15 个 dex 逐串 grep）："金额未给" **2** 处 / "后端无此口径" **1** 处 / **"暂无报价" 0 处**
  —— ⚠ 指令 §三 的字面文案是"暂无报价"，本轮**刻意不照抄**：那句话暗示"后端算过了、结论是没有报价"，
  而真实情形是**这个键不在报文里**（B/C 类冒充 A 类，正是 §五 的主题）。这一处偏差**写进清单**而不静默替换。
- 同一本簿的 **B-* 族**（§四 NearbyResult）：`B-a 2 · B-b 0 · B-c 1（只登记） · B-d 0 · B-e1 1 · B-e2 2 · B-e3 0 · B-f 0 · B-g 6`，GUARD-B **29**，exit **0**。
  `inService/located` 改 `Boolean? = null`，`isOffService` 只在 `inService == false` 时为真；`located` 目前**没有 UI 消费点**，本轮仍改可空——**默认值本身就是谎言**，不需要"有人读"才配修。
- 用例：`MinutesContractTest` **6** / `QuoteNullSafetyTest` **7** / `NearbyBooleanContractTest` **6**（XML 逐份读出，非控制台汇总）。

**三条口径要点（本轮踩出来的，别退回去）**：
① **`hits` 会把修好的事读成没修**：`optBoolean("…", true)` 的普查 `hits=2`，那 2 处在 `Models.kt:831/:849` 的 **KDoc** 里，
   内容是"旧读法长什么样"的反面说明。⇒ 普查每行补 `code=` 列（滤注释行的生效行数），本轮实测 `hits=2 / code=0`。
   ⚠ **不得为了把 2 消掉去删注释**——删了就把"这里曾经塌 true"的证据抹了，下一轮只会更容易重犯；
② **"0 命中"必须能区分"真归零"与"判据写错了"**：本族 8 个 0 值都配了 GUARD（别名模式必须在**测试源码**——判据扫描面之外——数得到 ≥5/≥10），
   这是 F-1 那条"grep 计数管道一旦失效，下面所有'预期 0'会集体变成假绿"的同一道题；
③ **正向下限与反向归零同轮立**：`T-aj4=7 / T-aj5=16 / Q-a=5 / DV-d=12` 这类"≥"抓的是"删得太过"（把真实键也删了、契约断裂还静默），
   `T-aj/T-aj2/Q-b/B-b` 抓的是"没删干净"。只立一边，另一边就是盲区。

**尾巴（在册，不当已通过）**：§二十四 点名的"五组 alias 做最终 contract audit"，本轮收的是**别名（第二候选读法）**：
五处四面取证后删净（`T-aj 0 / T-aj2 0 / T-aj3 0`），逐键证据记在 `docs/android/CONTRACT_EVIDENCE.md:15`（`plannedMinutes` 被删的 `minutes`/`offsetMinutes`）
与 `:18`（`plannedDurationMinutes` 被删的 `durationMinutes`——后端有这名字但属**另一个对象的另一种口径**）。
⚠ **别名删净 ≠ 默认值形状修净**：这五处里还剩**两处**是 §五 意义上的 B 类"缺键当 0"——
`plannedMinutes`（声明 `Models.kt:469 Int = 0`，读法 `:500 o.multiNum("plannedMinutes").toInt()`）与
`plannedDurationMinutes`（`:1337 Int = 0`，`:1381` 同形）；`waitDepartureMinutes:556`、`walkMinutes:1836` 已是 `Int?`。
⇒ 这两处落在 **DV-k=132 全项目基线内**，归下一轮"逐处三方核对"；本轮**没有**顺手改它们的默认值形状——
那要连渲染面与后端口径（"计划时长"与"已行驶时长"是不是同一件事）一起动，超出 §二十四 的范围，改了就是扩大战线。
`B-c` 登记的 **1** 处残留是 `SendScreen.kt:209 fresh = o.optBoolean("fresh")` —— 缺键当 **false**（方向与 §四 那条相反：把"没数据"说成"不新鲜"），
三面生产者**未核**，归入 §五 的 B/C 分类处置而不是本轮 §四 的口径。设备侧一律 `NOT VERIFIED ON DEVICE`。

## D-036 判据的扫描面必须按**契约**收，不按**成员名**收：§五 数字默认值普查（472 处 / A=0）+ 三处跨契约串味的实测修复（2026-09-30，F-2 ⑤ + 本轮台账往返）

**普查结论（`tools/default_value_census.py`，实测 **472** 处数值默认位）**：**B 398 / C 74 / A 0**（D 逐处、E 外部真实生产者另计）。
**A=0 不是"没人写过 0"，是结构性事实**：后端 App RespVO 全用包装类型（`Integer`/`Long`/`BigDecimal`/`Boolean`），
"后端真的给了一个零"与"后端没给"在报文上本来就长得一样 ⇒ 客户端任何把缺键读成 `0` 的写法都是 B/C 冒充 A。
明细在 `docs/android/DEFAULT_VALUE_AUDIT.md`，`docs/android/CONTRACT_EVIDENCE.md` 逐字段记 producer 与缺失行为。

**本轮只收 Earnings 一族**（不扩大战线）：`check_default_value_probes.sh` exit **0**
（`DV-a 0 · DV-b 0 · DV-c 0 · DV-d 12 · DV-e 9 · DV-f 2 · DV-g 8 · DV-g2 2 · DV-h 0 · DV-i 0 · DV-j 1 · DV-k 132 · DV-l 8`，
GUARD `1/29/34/7`），`default_value_negative_controls.sh` **16 ✔ / 0 ✘ / 0 △**，`EarningsContractTest` **8** 例。
**DV-k 是全项目基线下限（本轮 152→132）**：它只许往下走；新增一处非可空数值读法就会响，这条是整个 §五 的"别把战线偷偷铺回去"闸门。

**三处跨契约串味（都是本轮实测抓到并改对的，改的是扫描面、不是期望数字）**：
① **R-f 由 2 涨到 4**：第 4 步把 `NearbyResult.inService/located` 也改成 `multiBoolOrNull` 后，
   `check_reachability_probes.sh` 的 R-f（扫全 `$A` 数 helper 名）从 2 涨到 4 —— 涨的两处在 `Repo.kt:726/:735`，**归 B-\* 那本管**。
   ⇒ R-f 改成**声明 1 + `Reachability` 解析体内使用 1**，实测回到 **2**；同时留一行**不判红的残留打印**（全项目 `multiBoolOrNull` = **4**），
   被摘出去的那两处继续可见。这条与 ⑪ 的 **R-g** 是同一个先例：**收紧扫描面，而不是放宽期望值**。
② **Q-e1 由 4 响成假红**：同名 getter `val amountText: String get() = …` 在 `QuoteResult` 与 `EarningItem` 两个类里都存在，
   全文件计数把两条契约并成一条。⇒ 改成 `sed -n '/^data class QuoteResult/,/^}/p'` **只数本类类体**，实测 **3**（恰 3，不判"至少"），
   `EarningItem` 那两个 getter 由 **DV-g2=2** 钉；另配 GUARD-QR（类名锚点被改则整本不采信）+ 全文件/get-全-ui 两行残留打印（4 / 5）。
③ **原有的 Q-e1 负控本身就是假绿**（这条最严重）：它用整文件 `replace(old, '', 1)` 删 `val amountText: String get() = amount?.let`，
   而 `EarningItem` 在文件更前面、持有**逐字相同**的一行 ⇒ 那次破口删掉的是**另一条契约的 getter**，Q-e1 照样 3、簿照样绿，
   **破口从未被打进被判定量**。⇒ 新增类体内删行的 `delbody()`，并**同轮补一条反向（口径）控制** `keep Q-e1`：
   删掉 `EarningItem` 的同名 getter 时 Q-e1 **必须不响**，响了说明这条判据统计的还是别处的东西。
   簿子输出格式因此扩了一位：`✔ 破口 / ✘ 未响 / △ 存疑 / ◦ 反向控制`，末行 `[口径控制] 反向控制失效条数 = 0（预期 0）`，非 0 走 `exit 3`。
   实测 **22 ✔ / 0 ✘ / 0 △ + 1 ◦，KEEPFAIL=0**，基线与恢复后各判通过一次。

**工具自误三条（同轮修，都升为永久规矩）**：
④ `cmd args ctl Q-x "…"` 漏了分号 ⇒ `ctl` 被当**参数**吃掉，那条控制**从未执行**、沙箱保持脏状态，
   ✔ 计数少 1（21 而非 22）才发现。⇒ **改完脚本必须对 `tools/*.sh` 全量 `bash -n` 扫一遍**，并且**每本簿的 ✔ 条数要与簿头声明的破口条数对账**。
⑤ **APK 新鲜度只认包内内容**：`assembleDebug`+`lintDebug` 报了"12 executed"却仍留着旧包（114,437,316 B / mtime 16:45:11 / 里面**查不到**本轮新写的"金额未给"）；
   `--rerun-tasks` 后得 113,863,001 B / **19:33:42** / sha1 `7fd54a4a54acde1560c07dbed3a650e30b617f4e`，`unzip` 全部 dex 才 grep 到"金额未给"×2、"后端无此口径"×1。
   ⇒ gradle 打印任务被执行**不等于**产物变了；"打进包了"这类话只能由包内字符串支撑。
⑥ **注释过滤器的形状要跟 grep 的输出形状配对**：`:[0-9]+: *(\*|//)` 只在 `-r`（带文件名前缀）的输出上成立；
   单文件 `grep -n` 输出是 `行号:内容`，必须换成 `^[0-9]+:[[:space:]]*(\*|//)`。用错的那次 Q-e2 实测 **6** 而不是 4（把两条注释行当成了使用点）。

**尾巴（在册，不当已通过）**：§五 剩下的 **32 处 C 类**逐族重核**没做**（天气温度/风速 3、距离/米 5、`recommendedLane`/`lane` + `recommended|best|selected` 12、
商品/车辆/清单 `Models.kt` 7 处、`Repo.kt:616/:997`、`SendScreen` 草稿 `pickup?.id ?: 0L` 属 D 类）；
`ApiCancellationTest`/`DemoExceptionIsolationTest` 是 F-1 的，本轮**未新增取消面**。
本轮改动**未进 git**（`GIT_REPOSITORY_UNAVAILABLE`，工程无 git，不 `git init` 造假历史）；
`release` 仍 `BLOCKED`；设备侧一律 `NOT VERIFIED ON DEVICE`。

## D-037 测试 Worker 起不起来由**守护进程的 `file.encoding`** 决定：只在命令行覆盖 jvmargs，不改 `gradle.properties`（2026-09-30，F-2 本轮全量用例 · 判据 §八 ㉒ ⑤）

**现象**：`:app:testDebugUnitTest --rerun-tasks` 连报三次 BUILD FAILED，控制台里没有任何用例级信息，
只说 worker 启动失败；同一时刻四本判据簿与 15 本负控都 rc=0，源码一字未动。

**取证到字节**（`~/.gradle/.tmp/gradle-worker-classpath*txt` 就是 worker 的 `@argfile`，java 原生 launcher 用 **ANSI 代码页 936** 解码它）：
16:09:55–20:15:22 那批是 **GBK** 字节（25,821 B，路径里的 `袁` = `D4 AC`）⇒ worker 正常；
20:19:16 起那批是 **UTF-8** 字节（26,019 B，`袁` = `E5 A4 89`）⇒ 六个 worker 全灭
`ClassNotFoundException: …worker…GradleWorkerMain`。**这个文件用哪种编码写，取决于守护进程 JVM 的 `file.encoding`**，
而客户端 `java` 命令行的 `-Dfile.encoding=GBK` **传不到守护进程**。

**处置（关键取舍）**：`gradle.properties` 里写着 `org.gradle.jvmargs=-Xmx3072m -XX:MaxMetaspaceSize=768m -Dfile.encoding=UTF-8`
（mtime 09-29 02:29，本轮**未改**）。修法是在**命令行**覆盖：
`./gradlew -Dorg.gradle.jvmargs="-Xmx3072m -XX:MaxMetaspaceSize=768m -Dfile.encoding=GBK" :app:testDebugUnitTest --rerun-tasks`。
**没有**去改项目配置文件 —— 这与"wrapper 下载失败不许改代码来绕过"是同一条红线：**环境的问题留在命令行，不许把它洗进工程**。
试过并否掉的两条：`./gradlew --stop`（重启守护进程后 argfile 仍是 UTF-8）、改 `TEMP` 环境变量（路径不变，无效）。

**最阴的一条**：这个故障发生时 gradle 仍然打印 **`24 actionable tasks: 24 executed`**，而 `test-results/` 里 **0 份 XML**。
⇒ **"executed" 不是产物**，收工读数必须**逐份读 XML 再自相加**（本轮 35 份、mtime 全为 20:30:14 ⇒ 338 tests），
不抄控制台摘要，也不拿 `from-cache` 当"重跑过"。这条与 D-036 第⑤条（APK 新鲜度只认包内内容）是同一个方向的两面：
**任务计数与产物内容是两件事**。

**顺带抓到第二条口径（负控计数）**：四本"用例级"负控的日志是**混合编码**文件（脚本 echo 是 UTF-8、嵌进去的 gradle 用例名是 GBK），
`grep` 按**二进制**处理它们 ⇒ `grep '✔' | 计数` 那种管道只收到一句 `Binary file … matches`，三本被读成 **1**（真实 8 / 6 / 7）。
正确做法是**按字节模式数**（`\xe2\x9c\x94`）或加 `-a`，然后与批处理留档逐本对账：15 本 rc=0、逐本 ✔ 相加 **254 / ✘0 / △0 / ◦1**；
换成"整行含 ✔"是 256（多算那句图例，它同时含 ✔/△/✘ ⇒ 还会凭空读出 1 条"负控没响"），"含 ✔ 去图例"是 255。
⇒ **总数不是判据，那 15 行才是**；这是 ㉑ 第②条（按标记 grep 的计数要先证那本簿用这套标记）的第三种失败形状。

## D-038 客户端幂等闸门的正确形状是**一域一把、键同维度而不同值**；而"包内新鲜"只能由 dex 内容证明，不由 `UP-TO-DATE` 证明（2026-09-30，F-2 ⑥ / 指令 v2.1 第 6 步 · 判据 §八 ㉒ ④ 加强版）

**决定的第一件事：复用现成闸门，但**不复用同一个实例**。** 指令第 6 步写的是"复用既有 `ActionGate`，不重写闸门" ——
本轮的实现是：`ActionGate` 这个**类**一字未改（mtime **20:05:17**，早于本轮 20:49 起的编辑窗口；语义仍是 V-1 那套：
`tryLock` **丢弃**而非排队、成功保留 10 s、异常即放），`object Repo` 上却因此出现了**两把**实例：
`actionGate`（`Repo.kt:1160`，司机域十个出口）与 `userCreateGate`（`:453`，用户域两个出口）。
为什么不能共用一把：硬规则 1 要求两个业务域分开，共用一把就是"用户下一次单，去挡司机的一次 `leg/start`"——
那是**用防重复点击的名义制造跨域串扰**，比没有闸门更糟。
证据不是主张而是两条实测：判据 `U-c` 数 `= ActionGate()` 实例**恰好 2**（`U-c2`/`U-c3` 两个方向钉"不许跨域借用名字"），
而负控里**打穿 `U-a` 时 `I-a`/`I-b` 仍是 10/10**（两本判据互不顶数）；
用例侧另有 `用户域与司机域是两把闸不是一个实例`，用反射数 `Repo` 上 `ActionGate` 类型的字段 ⇒ 并成一把立刻红。

**键：抄维度，不抄值。** 后端 `UserIdempotentKeyResolver.java:21-25` 是 `MD5(方法签名 + 方法参数 + userId + userType)`，
注解只有两处（`AppProductOrderController.java:38`、`AppSendController.java:120`，都 `timeout=10s` + `message="订单正在创建中，请勿重复提交"`），
`Idempotent.java:61 deleteKeyWhenException=true`。本机把它翻译成 **端点 + 登录身份 + 整份报文（按 key 排序）** 的 SHA-256，
存进键表的只有 `create:<64 hex>`。⚠ **必须写清的那条边界**：本机键**不可能**等于后端那个 MD5 ——
`userId/userType` 的服务端表示在客户端拿不到，硬凑一个值就是"假造 API"（指令明令禁止）。
所以**本机放行 ≠ 后端会放行**，这条不是靠注释声明的，是由两条用例钉住的：
`后端回 900 时原样透出那句话且不再由本机代拦`（`code=900` 保持 `ApiException`、`message` 逐字透出、闸随即放开、第二次照样出网 `requestCount==2`），
以及 `业务失败后立刻允许重试`。
**明文个人信息不进键表**是一条独立的决定：键表挂在进程单例上、活得比页面久，
把收货人姓名/手机号/地址写进去就是留存（`U-g` + 用例里四段明文都不是子串）。
`AppAddressController.java:32` / `AppFeedbackController.java:28` 两处 create 后端**零** `@Idempotent` ⇒ **刻意不加闸**，
登记为 `U-j`（不判红、也**刻意不写负控**——能给"不判红"写负控的前提是它会让脚本 `exit 1`，而那正是不要的行为）。

**本轮最值钱的一条过程发现（已升为永久口径 §八 ㉒ ④ 加强版）**：
`:app:assembleDebug` 在 21:08 打印 `packageDebug` 执行过、`BUILD SUCCESSFUL in 4s`，
但盘上 `app-debug.apk` 仍是 **19:33:42 / 113,863,001 B / sha1 `7fd54a4a…`**，`unzip` 出 15 个 dex 全扫，
本轮新符号 `userCreateGate` **0 命中**；而**同轮**的 `intermediates/dex/debug/mergeProjectDexDebug` 是 **21:08:18 且含新符号**
⇒ **编译是新的，打包环节按 up-to-date 记账把包留旧了**。`--rerun-tasks`（**37 actionable tasks: 37 executed**）才得到
**21:13:59 / 113,863,001 B / sha1 `03ab6172…`** —— ⚠ **与那份旧包同字节而不同哈希**：
"体积没变"在这个案例里恰恰掩盖了"内容变了"，所以**跨轮引用 APK 必须带哈希与 `N executed`**（与 #51/E-1 那条同源）。
⇒ **`UP-TO-DATE` / `executed` / `BUILD SUCCESSFUL` 三种打印都不是"包内新鲜"的证明**，只说明 gradle 自己的记账状态。
配套的**0 命中反证**也第一次实跑到：同一批 dex、同一个脚本里，已知旧串 `gatedAction` 数到 **4**、
`本次请求未发给后端` 数到 **1**，所以"测试类 0 命中 / `Lokhttp3/mockwebserver/` 0 命中"是真的不在包里，而不是工具坏了。
⚠ 现场不可复现：`--rerun-tasks` 把中间产物重写（现 21:13:53），21:08:18 那份"有符号没进包"的证据已取不回来，
本机又无 git ⇒ 这一条**只记为当时观察到**，不作为已证归因。

**两条负控写法上的坑（都是本轮实测踩到，不是推演）**：
① **断言要锚分隔符** —— `grep -q "^× U-c"` 会把 `× U-c2 …` 也算命中，于是"U-c 的破口没响"也被打印成 `✔`。
   统一改成 `"^× $1 "`（尾随空格），`U-c` / `U-c2` / `U-c3` 才各自真的各管一个破口。
② **沙盒要整目录拷** —— `cp ui/driver/*.kt` 那次教训（部分拷贝把"下限"变成"沙盒的巧合"）在用户域原样重演：
   改成 `cp ui/user/*.kt` + 逐文件预检 `exit 2` 之后基线才立得住。
③ **测试复位按字段类型扫，不写死名单** —— `TestHost.resetActionGate()` 旧版写死字段名，本轮加新闸就漏过一把；
   闸门是进程级而 Gradle 全类共用一个 JVM，`SendOrderTest` 的 **6** 个 `createSendOrder(form)` 调用点提交**内容完全相同**的 `form`，
   不复位就会被自己的闸吞掉（改成筛 `ActionGate` 类型字段后 36 类 348 例全绿）。

**在册未核的历史数字（不许悄悄改）**：司机域负控的条数在台账里有三处写"12 条 / 12 ✔"
（状态板 V-1 尾巴一处、DONE 的 V-1 段两处），而 V-3 改沙盒之后的快照与本批复跑实测都是 **11**。
本轮**没有**去重写那三个历史数字（改历史会让所有引用它的审计段落集体漂移），
处置是就地加一条注解 + 在此登记：**凡引用"12"必须带日期与主题，条数一律以脚本当下的打印为准**（口径同 §八 ㉑）。
本轮实测值：`bash tools/idempotency_negative_controls.sh` → **25 行控制 = 24 ✔ / 0 ✘ / 0 △ + 1 ◦**（11 条 `I-*` + 13 条 `U-*` + `◦ U-j`），
基线与恢复后各判通过一次；同簿 `check_idempotency_probes.sh` 22 个读数全部到目标值、`RC=0`。

**没做到的（照实记）**：真机路径全部 `NOT VERIFIED ON DEVICE` —— 用户"手指划走页面"时闸门放不放，
在 JVM 里没法确定性地构造（`Api` 走 `Dispatchers.IO` 里的阻塞 `execute()`），取消语义只钉在 `ActionGateTest` 的"协程被取消不许泄闸"那条；
900 之后的**"自动回读订单状态"专属分支**仍未做（今天只是把后端那句话说给用户）；
`packageDebug` 那个 up-to-date 记账为何与包内容不一致，根因未证（只能排除到"不在编译环节"）。

## D-039 构建失败的分型**不进 `app/src/main`**：它在构建失败时没有运行期消费者，进了 main 就是业务侧的第四套真相（2026-10-01，F-2 ⑦ / 指令 v2.1 §三十三 · V21-33 · 判据 §八 ㉓）

**决策**：四类分型（`SOURCE_FAILURE` / `DEPENDENCY_FAILURE` / `ENVIRONMENT_FAILURE` / `DEVICE_UNAVAILABLE`）落三处 ——
① **唯一规则表** `tools/verification_failure_rules.txt`（三列：类别 · 字面量 · 出处）；
② **bash 分类器** `tools/classify_verification_failure.sh`（真实消费者，`verify_device.sh` 的构建分支与无设备分支都调它）；
③ **test 源集**的 `sealed interface VerificationFailure` + 四个 `object`（`app/src/test/java/com/shanxiang/buspost/verification/`）+ 8 条用例。
`app/src/main` 里四类符号 **0** 命中、`VerificationFailure` **0** 个文件（判据 `VF-c`，同轮反证 test 源集 **10** 命中以证 grep 没坏）。

**背景 / 备选**（三个候选都核过，不是先定了再找理由）：
- *进 `app/src/main`*（指令字面）：被否。**构建失败时 App 进程不存在**，这个类型在生产运行期没有任何调用点；
  更硬的反对理由是**它会在业务侧三套失败模型旁边长出第四套** —— `ApiException`（后端拒绝）· `NetworkException`（出不了网）·
  `Ack.error`（回执失败）已经各自有主，判据 `VF-d` 因此钉成 **1/1/1** 并禁止第四套。
- *只写 bash*：被否。指令 V21-30 点名的文件本就是 `VerificationFailureTest`（它在 **test** 源集），
  纯 bash 交付会让"类型层证据"缺失，而 V21-30 那一行就是照着这个类型名写的。
- *test 源集类型 + bash 分类器 + 单一规则表*（采纳）：两侧共读同一张表 ⇒ "分类知识"只有一份，
  Kotlin 侧负责**契约与用例**，bash 侧负责**在真构建日志上出结论**。

**代价（写清，不含糊）**：
- **等价性要额外证**：bash 用 `grep -aF`、Kotlin 用 `String.contains`，两者只在"纯文本子串 + 全 ASCII"时可证等价 ⇒
  规则表**禁止非 ASCII 与 glob 元字符**，前提由用例逐行验（不是靠"看起来都是子串"）。
- **多一个进程开销**：逐条字面量起一个 `grep` 在 Windows 上是 **18 s**，改成单次 `grep -a -F -f 表` + bash 归属 ⇒ **9 s**。
  ⚠ 抄这个耗时**要连"是否与 gradle 并行"一起抄**：判据簿单跑 9–10 s，与负控批并行时实测 12 s / 17 s。
- **`BRE` 一类假干净**：分类器一律 `-F`。旧写法里 `-` 与 `[]` 在 BRE 中不是字面量，会让"0 命中"其实是"没匹配上任何形状"
  （同一条坑在 `T-t` 上第一次被抓到，见判据 §八 ⑫）。

**本轮抓到的三条自误（都是判据自己的缺陷，不是注入失效）**：
1. **首轮负控 `✘ 9`**：把 `object EnvironmentFailure` **改名**，`VF-f` 只数"类别个数"所以照样 4 ⇒ 不打红。
   修法是**逐名点齐**（四个名字一条正则数到恰好 **4**）。⇒ 一般规则：**只数个数的判据对"改名"无感**。
2. **首轮负控 `△ 10`**：把分类器里的规则表路径换掉，响的却是别的判据 —— 因为消费者下限写成 `>=3` 而表本来有 **6** 个消费者。
   修法是**精确钉赋值行**（`RULES="$ROOT/tools/verification_failure_rules.txt"` 恰好 **1** 处，注释里的提及不算）。
   ⇒ 一般规则：**"至少 N 个消费者"这类下限对"N 掉到 N-1"无感**。两条都是**收紧判据**，**没有放宽任何期望数字**。
3. **出处列自己也会烂**：`Unrecognized VM option` 那行表里写 `:15`，样例实测在 **`:12`** —— "出处非空"判据看不见行号漂移。
   ⇒ 新增 `VF-a` **逐行回核闸**（`awk NR==n` + `index($0,lit)` 回到样例确认那一行真有这条字面量）+ **负控制 13**（把 `:12` 改成 `:15` ⇒ 必须点名打红）。
   另补 `no devices/emulators found` 的出处 `:9`。

**两条既成 bug / 口径缺陷（顺手登记，不假装是小问题）**：
- `verify_device.sh` 里原有的 `… | tail -3 || die` —— **管道退出码永远是最后一个命令（`tail`）的 0**，那条闸门**从不触发**。
  已修；`VF-e` 同轮新增"旧 `tail -3` 管道形态 = **0**"把它钉住。⇒ "看着有守卫其实没有"按红线登记。
- 负控批处理 `run_all_negative_controls_batch.sh` 按 `✔` 数行，而**就地改源码型**的簿只打印 `[破口后] gradle exit=1` ⇒
  `idempotency_test_negative_controls.sh` 被记成 **`✔=0 ✘=0` 的假空跑**（其实红了、`rc=0`）。
  修法是让该簿的判定行带上 `✔ IT-1` / `✘ IT-1` 前缀，与其余各簿同标记体系（**改的是打印口径，不是判据松紧**）。

**样例证据的边界**：`tools/fixtures/verification-failure/` **7 份 = 6 正向 + 1 反例**，全部是**本工作区真实跑出来的日志**
（含 `ScratchVfCapture.kt` 编译失败、init 脚本注入不存在的构件、缺 GBK jvmargs 的 worker、自己写错的 `-XX:MaxMetaspaceSize:768m`、
`git rev-parse` rc=128、`adb devices` 无设备），**不收合成日志**；`cmp` 逐份证与 `/tmp` 原件同字节，README 记 sha1 前 12 与两份 mtime。
⚠ 两份日志**长得几乎一样而判决不同**（都是 `GradleWorkerMain` 起不来）：差别在**上一行**是 `-Xmx` 参数错还是 `file.encoding` 解不开路径 ⇒
分类器**不许猜**，落不进唯一类就出 `AMBIGUOUS`/`UNCLASSIFIED`。反例那份是**用例失败**（不是构建失败），要求 **0 命中**、分类器 `rc=4`。

**`assembleRelease` 归 `UNCLASSIFIED` 是设计意图**：本轮 `:app:assembleRelease` `rc=1`，出自工程自带的 HTTPS 闸门
（`app/build.gradle.kts:171`，**没有**加 `-Pbuspost.allowInsecureRelease=true` 绕过）。它既不是四类里的任何一类，也不是"构建失败=源码坏了"，
而是**策略拒绝** ⇒ 分类器给 `UNCLASSIFIED`，`VF-b` 把它留在**反例**侧。⇒ **没有为了让它落进某一类而加规则行**。release 仍 `BLOCKED`。

**台账往返的两条老坑本轮又各踩一次（都已改成机器读取）**：
① 比对"本轮改了哪些用例类"时把上一轮那张逐类表**手抄**进脚本，漏了 `SendOrder 8` ⇒ 打出"新增一类"的**假差异**
（同一条坑的第三次：F-1 手写错类名、F-2 ①–⑤ 的 `replace('Test','')`）⇒ 改成从台账文本 `re.search` 解析基线；
② 就地负控会把 `app/build/test-results/` 洗成少数类 ⇒ **收工顺序必须是"负控在前、全量用例在后"**，
否则状态板那串 37/356 当场失去产物支撑（本轮实测到：全量跑完 23:52:59，23:53:23 就被就地批洗成 2 份）。

**状态**：已落地并被三条判据簿共同钉住（`check_verification_failure_probes.sh` **rc=0 / 18 行输出**、
`verification_failure_negative_controls.sh` **13 ✔ / 0 ✘ / 0 △ + 1 ◦**、`v21_requirement_census.sh` **117 行**，
V21-33 新增 `rules-table rows=16 kinds=4`、`fixture-logs total=7 positive=6 consumers=6`）。
**未收口**：分型行为一律 `NOT VERIFIED ON DEVICE`；规则表里 **7** 行出处是 `未实测:`（只登记不判红）；
指令 §三十三 的第 6 步 `assembleRelease` 仍 `BLOCKED`。

## D-040 负控簿自己有两处"永远绿"：并行没有互斥、空跑注入会被读成"判据不判"（2026-10-01，F-2 ⑦ 收工终检 / 判据 §八 ㉔）

**触发点不是代码，是读数**：终检要复跑 16 本负控，同一本簿在两份日志里给出三种答案。
`shell_routing_test_negative_controls.sh` 在状态板（`AI_ITERATION_STATE.md:826`）记的是 **M-1…M-7 全 ✔**（逐条
failures+errors `2/2/1/1/1/3/1`），本轮复跑却打出 **✔=6 ✘=1**，✘ 的正是 **M-4「司机域路由对用户会话放行 → 用例仍然全绿，
这条判据证明不了任何东西」**。如果照抄这句话，就等于把一条**其实生效**的判据登记成失效——
而且 `role_reconcile_test` 同批还报 **rc=2「恢复后基线不绿，本轮负控不采信」**。两条凑在一起才像是簿子自己在骗人。

**读日志读出来的事实链**（都是 `/tmp` 里当场那几份，不是复盘时补的话）：
1. 23:30:50 起跑的那批（`/tmp/vf_allneg_console.log`，批次结束 **00:21:23**）是我以为"已被我停掉"的那一批的**子进程还在继续**；
   我 00:03:22 又另起了一批（后台任务 `b4h62yxdu`，7 本）。**两批重叠了 18 分钟**，重叠时段里跑的是
   `idempotency_test / role_reconcile / role_reconcile_test / shell_routing / shell_routing_test / throwable / verification_failure`
   —— 正好就是报异常的那几本。
2. `shell_routing_test` 那轮的 **基线就是红的**：`[基线] → gradle exit=1`，红的是
   `ShellRoutingContractTest.kt:109/:116/:128` 三条（`/tmp/shell_neg_baseline.log`），全是 `deepLinkTarget` 的行为；
   与此同时**同一本簿的另一份副本**正在改 `nav/ShellDecision.kt`。所以那不是"用例坏了"，是**两个改同一文件的进程互相把对方的
   破口当成了基线**。
3. 同一本簿的 M-4、M-5 两步都抛了 `AssertionError`（锚点被同行还原掉），但 `case_run` **不看注入的退出码**，
   拿着**未修改**的源码继续跑 gradle；那次的输出是 `> Task :app:compileDebugKotlin FROM-CACHE` +
   `> Task :app:testDebugUnitTest FROM-CACHE` + `BUILD SUCCESSFUL in 13s` —— 缓存里那份绿色结果被端上来，
   于是"我没打破口"被打印成了"判据不判"。**✘ M-4 是一条假发现**。
   对照：M-1/M-2/M-3/M-5/M-6/M-7 全是 `BUILD FAILED`（✔ 需要真红，红是缓存给不出来的），
   所以**只有"绿"这一侧会被缓存污染**——这正是"0 命中要先证方法"那条规矩在负控里的翻版。

**三处机制缺陷与它们的暴露时间**（为什么以前一直没露）：
- **①「一次只允许跑一个改源码的负控脚本」只是注释**，16 本里没有任何一本执行它。以前没露是因为以前**真的**只跑一本。
- **② 退出码是死的**：`exception_test` / `idempotency_test` / `shell_routing_test` 三本的最后一行都是
  `bash tools/check_*_probes.sh | tail -1` ⇒ 管道的退出码是 `tail` 的 **0**，**这两本 + shell_routing_test 的 rc 永远读作"通过"**。
  另有 7 本结构簿末行是 `echo "[恢复后] $(… | tail -1)"`，rc 同样是 `echo` 的 0（那 7 本的判据结论至少还在**文本**里，
  批处理的 `末行:` 字段抄的就是它，本轮 10 本里没出现过 `×`，所以那批读数仍然可用）。
  这是同一个形状**第三次**踩（第一次 `verify_device.sh` 的 `… | tail -3 || die`，第二次是负控批按 `✔` 数行造成
  `idempotency_test` 记成 `✔=0` 的假空跑）。
- **③「基线不绿的负控一律不采信」这句话只印不判**：`shell_routing_test` 与 `idempotency_test` 打完
  `gradle exit=$?` 就往下走，红基线照样能产出 7 条 ✔。`exception_test` / `role_reconcile_test` / `navigation_test`
  是有闸门的（`exit 2`），本轮 `role_reconcile_test` 的 rc=2 就是那道真闸门报的——**它是唯一能报出这次污染的一本**。

**处置**（改的是**证据链**，不是判据松紧；一条判据的阈值都没动过）：
- 新增 `tools/negctl_lock.sh`：`mkdir` 原子锁 + `pid`/`owner` 两个文件 + **陈旧锁接管**（按 `ps -l -p $$ //W` 的
  **WINPID** 判持有进程还在不在——cygwin 的 `$$` 与 Windows PID 不同号，用 `$$` 判会永远判成"死了"；
  存活判定用 `tasklist //FI "PID eq N" //NH | awk '{print $2}' | grep -qx N`，**不能**用 `grep -c`，
  否则会撞上内存列里恰好出现的同数字串）。16 本全部在 `SRC=`/`ROOT=` 之后取锁、并把 `negctl_release` 合进自己原有的
  `trap … EXIT`（**簿里后登记的 trap 会顶掉前一个**，不合并就会漏恢复源码——这是本轮最容易写错的一处）。
- `run_all_negative_controls_batch.sh` **自己先取锁**，再 `export NEGCTL_LOCK_HELD=1` 让批内 16 本沿用同一把而不重复取
  （否则批内第一本会把后面全 [STOP] 掉）；另起第二批会被 `[STOP] rc=3` 挡住。顺带把每本**用时**记进读数行。
- `red()` 补两道闸：注入的 python 退出码非 0 ⇒ `✘ 锚点没命中…` + `exit 2`；**注入前后 `md5sum` 相同 ⇒ 空跑**，
  `exit 5` 并明写"拒绝记成判据失效"。空跑与判据失效必须是两种输出，否则簿子的缺陷会被算成用例的缺陷。
- 五本 `*_test` 簿补齐：基线红 `exit 2`、恢复后 `cmp` 不一致 `exit 4`、恢复后用例红 `exit 2`、恢复后判据簿红 `exit 6`。

**正/反例实测**（锁的 9 条，全部当场跑在 `/tmp` 上，不依赖 gradle）：
首次取锁 rc=0 → 同进程再取 **rc=3 [STOP]** → 并行子进程取 **rc=3** → 把 `pid` 改成不存在的 999999 后
**[接管陈旧锁] rc=0** → 释放后锁目录消失 → `NEGCTL_LOCK_HELD=1` 时子簿打印「[锁] 由批次持有」并 rc=0 →
批次持锁时第二批 **rc=3** → 释放后第三批 rc=0。
补丁后的 16 本 **`bash -n` 全过**；改前快照备份在 **`/tmp/negctl_prelock_20261001-002718/`**（16 份）。
另跑了一遍 **15 本 `check_*_probes.sh`：全 rc=0**（`缺键默认值/数字默认值/分域/契约信封/ETA/§54 异常上报/幂等/定位与上报/
导航引擎/可达性/角色同源/壳路由/状态映射/异常可信化/四类失败分型 判据通过`）
⇒ 两批并行**没有把源码留在破口状态**，本轮之后的复跑是从干净的树出发的。
> **【2026-10-01 就地更正，原句不回改】** 上面这句**已被实测证伪**。01:15:05 新增的"基线不绿 `exit 2`"闸门量出
> 树当时带着 `nav/ShellDecision.kt:36` 的一行未还原注入（`deepLinkTarget` 丢了 `role != Role.DRIVER &&` 守卫），
> 它在 01:25:00 才被手工还原。当时用来下这句结论的证据是 15 本 `check_*_probes.sh` 全 rc=0——那些探针是
> **结构判据**（锚点文本 / 文件存在 / 计数），**看不见这条语义破口**，所以"probes 全绿"推不出"树是干净的"。
> 完整事实链与还原后的读数见下面的 D-041 与 §八 ㉔ ⑦。教训登记成一条规矩：**"树干净"只能由跑用例证明，
> 不能由探针全绿或 `cmp 一致` 证明**（备份取自开始时的树，树脏则还原只是把脏稳定下来）。

**没有做的两件事，以及为什么**：
① **没有**把 `*_test` 簿的 `-Xmx2048m` 抬成配方里的 `3072m + MaxMetaspaceSize=768m`——先排除了内存这条：
基线红的是三条**用例断言**（`AssertionError at ShellRoutingContractTest.kt:109/116/128`），不是 OOM/Metaspace，
抬堆只会把"并行的另一本在改源码"这条真根因盖住。**在册**：这五本与全量用例的 JVM 参数不一致，将来若要统一，
得连同这条排除理由一起改。② **没有**加 `--rerun-tasks` 或 `--no-build-cache`：假绿来自**注入是空跑**，不是缓存本身；
关掉缓存只会让 16 本慢一倍而**不多证明任何事**，而 `md5` 空跑闸门直接钉的是那个真缺陷。

**在册待办**：修完后整套 16 本的**串行复跑读数**（`/tmp/final_neg_batch.log`，末行会打"共 16 本"）——
本轮之前那批 23:30→00:21:23 的读数中，**00:02:54 之后**的 7 本一律标注"与第二批并行，不采信"；
之前的 9 本（`contract_defaults / default_value / eta / exception / exception_test / idempotency / navigation /
navigation_test / reach`）在单批窗口内跑完，读数继续有效但**不带**修后 rc 语义（那 9 本末行是文本 verdict，rc 恒 0）。
> **【2026-10-01 01:41 这条待办已交】** 串行 16 本复跑于 01:20:15 结束：**15 本 rc=0 + 1 本被基线闸自拒**（`shell_routing_test` rc=2），
> 自拒那本还原后单本复跑 rc=0 / ✔=7 ⇒ 合并 **289 ✔ / 0 ✘ / 0 △**；逐本读数与 `✔` 口径拆分见 **D-041** 与状态板负控节。
> ⚠ **本条上面那句"两批并行没有把源码留在破口状态"已被本轮实测证伪**（树里带着一行未还原的注入跑了 40 分钟），
> 更正标注就地加在原句下面、原句未回改 —— 这一条不改变"互斥锁本身有效"的结论（本轮整批**没有再出现并行踩车**），
> 改变的是**"probes 全绿能推出什么"**：推不出"树干净"。

**本轮为这条决策落地的文件（两个都是自写 bash，零第三方代码引入 ⇒ `THIRD_PARTY_NOTICES.md` §1/§2 两张表不加行）**：
① `tools/negctl_lock.sh`（实测 **3,841 B / 75 行**，mtime 10-01 00:34:54）—— 互斥锁本体：`mkdir` 原子锁 +
按 **WINPID** 判活（cygwin `$$` 与 Windows PID 不同号；`tasklist //FI "PID eq N" //NH | awk '{print $2}' | grep -qx`，
⚠ 不能用 `grep -c`，那会撞上内存列里恰好相同的数字串把死进程读成活）+ 陈旧锁接管 + `NEGCTL_LOCK_HELD=1` 批次继承；
16 本 `*_negative_controls.sh` **全部**已挂（`grep -l negctl_lock` → **16/16**，每本命中 2 处）。
⚠ 一处实现约束值得记下：bash 的 `trap` 是**单槽**，`negctl_release` 必须**合进各簿原有的那条 `trap 'restore; …' EXIT`**——
单独再 `trap` 一次会顶掉 restore，那才是真把源码留在破口状态。
② `tools/after_negctl_run_full_tests.sh`（实测 **3,181 B / 73 行**，mtime 10-01 00:48:18，`bash -n` ok）——
把 ㉔ ⑤ 那条"负控在前、全量在后"的**顺序**也从记性变成机制：先等 `/tmp/negctl.lock` 释放**且**批次打印"共 16 本"，
再核 `rc` 行数与 `rc=0` 行数**都是 16** 才起 `:app:testDebugUnitTest --rerun-tasks`，随后**逐份 XML** 读 `testsuite`
属性自相加并打印 mtime 集合。三种情形不猜而是直接停：锁目录没了却没打结束标记 **exit 7**、等待超 `MAX_WAIT`（默认 5400 s）**exit 8**、
批次没结束就绝不起第二个 gradle。⚠ 它只打印**读数**，不替任何判据簿下"通过"的结论。

## D-041 修好的基线闸门第一次上岗就把源码钉红了：树里带着一行**没还原的注入**跑了 40 分钟（2026-10-01，F-2 ⑦ 收工终检 / 判据 §八 ㉔ ⑦）

**这是 D-040 那两个机制的验收，不是一条新缺陷的道歉**：串行 16 本复跑（`tools/run_all_negative_controls_batch.sh`，00:35:40 → 01:20:17）
里第 14 本 `shell_routing_test_negative_controls.sh` 打出 **rc=2 ✔=0 ✘=1**、末行
`✘ 基线未通过，本轮负控不采信（先看 /tmp/shell_neg_baseline.log 红的是哪条用例）`，**用时 12 s 就把自己停了**。
它没有跑 7 条破口、没有产出任何一条 ✔ ——这正是 D-040 要的行为：**宁可这本不跑，不拿错的树出七条读数为"生效"**。

**红的是哪条（`/tmp/shell_neg_baseline.log`，2,291 B，01:15:05）**：
```
> Task :app:testDebugUnitTest FAILED
ShellRoutingContractTest > 司机域深链在司机会话原样放行 FAILED
    java.lang.AssertionError at ShellRoutingContractTest.kt:109
10 tests completed, 1 failed
24 actionable tasks: 1 executed, 23 up-to-date
```

**读出来的事实（不是复盘的推测）**：`app/src/main/java/com/shanxiang/buspost/nav/ShellDecision.kt` 第 36 行当场是
```kotlin
    route in Routes.DRIVER_ONLY -> null            // ← 树里此时的样子
```
而同一本簿自己第 88/92 行的 `red()` 表达式里写着原始文本
```kotlin
    role != Role.DRIVER && route in Routes.DRIVER_ONLY -> null
```
（M-5 那条破口就是把前者换成后者）。⇒ 树里带着的是 **M-5 形状的注入**，而且**这一本自己没注入过**：
它的流程是"基线 → 注入 → 跑 → 还原"，基线就红了，注入那一步根本没执行。
`deepLinkTarget` 上方的注释（"在**用户会话**里拒绝"）与代码不一致，是第二条旁证。

**它是什么时候进去的、为什么所有簿都没发现**：
· **能确定的**：F-2 ⑥ 收工那遍全量用例是 21:07:20 **348 例 0 失败** ⇒ 破口出现在那之后；本轮批次 00:35:40 起跑时它已经在树里
（`ShellDecision.kt` 与 `Routes.kt` 的 mtime 一直是 01:15:05 —— 那是这本簿的 trap 用**它自己开头那份备份**还原的时刻，
备份本身就是脏的，所以 `cmp 一致` 顺理成章地成立）。
· **不能确定的**：具体是哪一次运行留下的。时间上唯一的可能窗口是 23:30→00:21 那批与我 00:03:22 另起的那批重叠的 18 分钟
（那期间 `shell_routing_test` 被跑过、也被我中途停过）。**没有 git、也没有当时那两本的完整单本日志留存，我不把这条写成已证实**。
· **为什么没被发现**：本轮之前那 15 本判据簿在 00:33 全部 rc=0，其中包括 `check_shell_routing_probes.sh` ——
它的 SR-* 钉的是**结构**（`BusPostRoot` 调用 `deepLinkTarget(`、`DRIVER_ONLY` 字面量条数、有没有第二份答案），
**不钉"这条分支带不带角色条件"**。⇒ D-040 里那句"两批并行**没有把源码留在破口状态**"（证据是 probes 全绿）**已被本轮实测证伪**，
就地加更正、不回改原句。

**为什么"cmp 一致"这一次没有保护我们**（这是这条决策真正的永久规矩）：
负控簿的还原证明的是"**与本书开头那一份备份逐字节一致**"，而**备份取自开始时的树** ——
如果开始时的树已经脏了，还原就**把脏稳定下来**，`cmp` 越是完美越是假象。
⇒ 唯一能证"树是干净的"的是**跑测试**（或另存一份与"本轮运行"无关的内容指纹），不是 cmp。
D-040 补的那道"基线不绿 exit 2"闸正好是这件事的机制，它上岗第一次就把这条坑量了出来。
⚠ 同时留一条**没做的**：给关键源文件存 **md5 白名单**（脏了就点名）本轮没实现——它需要一个可信的初始指纹来源，
而在没有版本库的机器上，那个来源本身也要有人证，暂时不假装有。

**修与验（顺序：先修源码 → 单本复跑 → 全量 → 打包/静态核）**：
① 手工把 `ShellDecision.kt:36` 还原成 `role != Role.DRIVER && route in Routes.DRIVER_ONLY -> null`
（还原依据就是簿内 `red()` 的 from 串，**不是我自己编的**；改后 **4,397 B / mtime 01:25:00**，
生产源文件**只动了这一行**，`Routes.kt` 当场核对 `const val DRIVER_EXCEPTION = "driverException"` 未被注入污染）。
② `bash tools/check_shell_routing_probes.sh` → **rc=0**（末行"壳路由判据通过"，`SR-h DRIVER_ONLY 字面量 = 1`）。
③ 单本 gradle：`:app:testDebugUnitTest --tests …ShellRoutingContractTest` → **rc=0 / BUILD SUCCESSFUL in 8s /
24 actionable: 2 executed, 2 from cache, 20 up-to-date**，XML 实测 `tests=10 fail=0 err=0 skip=0`（01:23:02）。
④ **单本复跑负控**（`bash tools/shell_routing_test_negative_controls.sh`，01:23:11 起，它自己取锁、跑完自己摘锁）：
**✔=7 / ✘=0**，M-1…M-7 **逐条把 gradle 跑红**（failures+errors = 2/2/1/**1**/1/3/1，M-4 这条这次是**真红**），
`[恢复] ShellDecision cmp 一致` + `[恢复] Routes cmp 一致` + `[恢复后] gradle exit=0`。
⇒ **D-040 认定的"M-4 是假发现"由此正面坐实**：判据一直生效，坏的是当时的树和当时的抄数方式。
⑤ **全量用例**（`tools/after_negctl_run_full_tests.sh` 接力，01:25:14 → 01:25:54）：`rc=0 / BUILD SUCCESSFUL in 38s`，
**37 份 XML mtime 全为 01:25:50**，逐份读属性自相加 = **356 tests / 0 failures / 0 errors / 0 skipped**。
基线**机器读取**（`re.search` 台账里那张"35 行逐类表（加总 338）"，不是手抄）后比对：
`ADDED {'UserCreateIdempotency': 10, 'VerificationFailure': 8}` / `REMOVED {}` / `CHANGED {}`，
且 338 + 18 = **356** 与实测相等 ⇒ 连续第 N 轮"没有无法归因的余数"。
⑥ ⚠ **同一条接力脚本先量到一件事**：脏树那一遍（01:20:31 → 01:21:30）**gradle rc=1 / BUILD FAILED in 57s /
37 类 356 例 / failures 1**，唯一那条红就是 `ShellRoutingContractTest fail=1`，其余 **36 类逐份全绿** ⇒
"这行注入只影响一条契约用例"是**测出来的**，不是推的。

**16 本串行复跑的读数（`/tmp/final_neg_batch.log`，00:35:40→01:20:17，修锁后第一遍；`✔` 口径见下面那条）**：
`contract_defaults` rc=0 ✔=22 ◦=1 181s · `default_value` rc=0 ✔=16 101s · `eta` rc=0 ✔=60 770s ·
`exception` rc=0 ✔=21 200s · `exception_test` rc=0 ✔=8 226s · `idempotency` rc=0 ✔=24 ◦=1 129s ·
`idempotency_test` rc=0 ✔=1 63s · `navigation` rc=0 ✔=27 145s · `navigation_test` rc=0 ✔=10 160s ·
`reach` rc=0 ✔=21 76s · `role_reconcile` rc=0 ✔=13 58s · `role_reconcile_test` rc=0 ✔=6 191s ·
`shell_routing` rc=0 ✔=13 48s · **`shell_routing_test` rc=2 ✔=0 ✘=1 12s（基线闸拒绝）** · `throwable` rc=0 ✔=19 124s ·
`verification_failure` rc=0 ✔=21 ◦=1 182s ⇒ **15 本 rc=0 + 1 本自拒**，`△` 全批 **0**；把第 ④ 步的单本复跑读数代入后为 **16/16 rc=0**。
`role_reconcile_test` 回到 **rc=0 ✔=6**（并行那一晚它是 rc=2）——这一条是 D-040 "被踩"判定的正面反证。

⚠ **批次 `✔` 的口径必须连口径一起抄**（否则下一轮一定误读）：批处理是 `grep -c '^✔'`，数的是**以 ✔ 开头的行**，
其中**包含门禁行**（`BASE` / `CMP` / `DIR` / `REC`），不是"破口条数"。实测分解：
`verification_failure` **21 = 13 条编号控制 + BASE 1 + CMP 5 + DIR 1 + REC 1** ⇒ 与 ㉓ 记的"13 ✔"**同一件事、不同口径**；
`idempotency_test` **1**（它只有 IT-1 一条控制，但那条一次打掉 **10** 例，日志里 10 行"失败用例名 + failures+errors = 10"是证据）；
`navigation_test` **10** = M1…M8 + 2 条门禁；`eta` **60** 与 09-30 的 **54** 差 **6**，
逐条对上就是 F-2 ② 新加的 **T-aj 家族 6 条**（`grep '^✔' | awk '{print $2}'` 家族分布里 `T-aj` 恰好 6 行）。

**分类器对这件事的态度（顺带把 ㉓ 的判据拿真实输入验了一遍）**：把那份真实红日志喂给
`bash tools/classify_verification_failure.sh /tmp/shell_neg_baseline.log` ⇒
**`VERDICT|UNCLASSIFIED|四类字面量一条都没命中：不许猜，去读原文，再决定是否给规则表加一行`，rc=4**。
⚠ 这是**设计意图**不是漏判：v2.1 §三十三 那四类讲的是**构建失败**的来源，而"构建成功、用例红"不在其中——
**用例红要修的是代码/用例，不是给规则表添一行**。本轮**没有**为了让它归类而改表。

**打包侧（照实记，不写成"重新构建通过"）**：01:27 那遍 `:app:assembleDebug :app:lintDebug` → **BUILD SUCCESSFUL in 1m 12s /
48 actionable tasks: 3 executed / 45 up-to-date**，三条 executed 全在 lint 侧（`generateDebugLintReportModel` / `lintAnalyzeDebug` / `lintDebug`），
**`packageDebug` 没执行** ⇒ 盘上 APK 仍是 09-30 21:13:59 那份（**113,863,001 B / sha1 `03ab61726030a19f431da275f0bfe3deaa976d2c`**，
与 D-038 记的那份**同哈希**）；三份 lint 报告 mtime 仍是 09-30 21:09:49，解析现有 XML = **0 error / 46 Warning / 32 Information = 78**。
⚠ 由此得一条**好消息但要说清前提**：脏树那 40 分钟**从未被打包**（21:13 之后到本轮之前没有跑过 assembleDebug，本轮也没跑），
所以**没有"带着 M-5 注入的产物"流出去过**——这条成立依赖的是"`packageDebug` 没执行 + sha1 未变"这两个实测事实，
不是依赖"我记得没打包"。

**在册尾巴（本轮不当已解决）**：① 破口的**来源**未证实（见上），且那 18 分钟窗口里跑过的**别的**簿有没有留下同类残留，
本轮只正面查了 `ShellDecision.kt` / `Routes.kt`（mtime 01:15:05 只有这两个）与 36 类逐份全绿这两条旁证；
② `tools/after_negctl_run_full_tests.sh` 数到 `rc=0` 只有 **15/16** 时**只打印不拦**（本轮正是靠这行打印发现的），
要不要改成硬拦（exit 9）留作下一轮决定——改了以后"已知自拒"也必须先登记才跑得完全量；
③ `navigation_test_negative_controls.sh` 的退出码仍是它自己的 **exit 1** 约定（其余四本已是 2/4/5/6）；
④ 结构型判据（SR-*）看不见"分支少了角色条件"这类**语义**破口，本轮是 gradle 用例补上的——
"结构判据全绿"永远不能单独当"源码没问题"的证据（判据 §六 那条老规矩的第 N 次应验）。

**尾② 已在 01:36 就地落地**（决定：**点名而不硬拦**）。`tools/after_negctl_run_full_tests.sh` 本轮两处改动：
① 三处 `grep` 补 `-a` —— 批次日志里 `eta` 那本的末行是**被截断的 UTF-8 多字节**，grep 于是把整份日志判成二进制、
在扫描尾补一句 "Binary file … matches"，**这句混在读数列表里**（01:21 那份 `/tmp/after_negctl.log` 实测就有它），
而 `grep -c` 给的数字**仍然是对的** ⇒ 属于"数字对、列表脏"那一类最难看的假整齐（同 §八 ㉒ ⑤ 的"executed 对、产物没有"是一个家族）。
② `n_readings≠16` 或 `n_zero≠16` 时打 `[警告]` 并把 **rc≠0 的本子逐本点名** + 写死"台账里不得引用本轮批次为『16 本全绿』"。
**没有**改成 exit 9 硬拦，理由与 01:20:31 那遍一样：**自拒的那一本恰恰要靠紧接着的全量那一遍才能定位**，硬拦会把诊断路径一起拦掉。
实测（01:36:17，拿**同一份真实批次日志**跑 dry-run，把脚本截到起 gradle 之前）：`bash -n` ok、放行判定正常、
读数列表 **16 行干净、不再有 Binary 句**、`[批次读数核对] rc 行 16 / 其中 rc=0 → 15（期望 16 / 16）`、
两条 `[警告]` 都打出来、点名行 = `shell_routing_test_negative_controls.sh rc=2 ✔=0 ✘=1 △=0 ◦=0 用时12s`。
脚本因此从 **3,181 B / 73 行** 变成 **4,378 B / 84 行**（mtime 01:36:07），仍是纯 bash、不进 APK。

**⑦ 收工台账面（01:41 全部落地，逐项对应本轮实测）**：D-040 的"在册待办"（串行 16 本复跑读数）**已交**——读数表与
`✔` 口径拆分落在 `AI_ITERATION_STATE.md` 负控节；`TEST_STATUS` 换成 **37 类 / 356 / 0**（含 01:21:27 那份 rc=1 / 1 failure 的脏树读数，
两份并列、历史值不回改）；`CURRENT_ANDROID_STATUS` 三态各补一行（`SOURCE_COMPLETE` 明写 **main 净改动 0 行**、
`BUILD_VERIFIED` 明写 **`packageDebug` UP-TO-DATE = 没重打包**、lint 报告 mtime 未刷新 ⇒ 那份 XML 只能当旧报告读数、
`DEVICE_VERIFIED` **仍是 NOT VERIFIED ON DEVICE 且不增不减**）；`CONTEXT_LOCK.md` §八 **㉔ 追加 ⑦**、§九 那行的 **①' 就地升级为"已跑完"并新立 ④'**；
`THIRD_PARTY_NOTICES.md` 新增一段登记"本轮改动生产源码 1 个文件（还原一行）+ 自写脚本 1 个"，并写明 **mtime 变了 8 个 main 文件里有 7 个只是原样写回**。

**⑧ 收工自检复跑（01:52–01:56，五组读数全部本轮实测，不沿用 01:25 那一遍）**：
① **15 本 `check_*_probes.sh` 逐本 rc 与末行**：`ok=15 bad=0`，末行分别是"分域 / 契约信封 / ETA（公交侧+货运侧+⑬ 里程口径+E-1 别名键归零）/
缺键默认值 / 数字默认值 / 定位与上报 / 第 8 步导航引擎 / 可达性 / 状态映射 / §54 异常上报 / 幂等 / 角色同源 / 壳路由 / 异常可信化 / 四类失败分型"各一句 **判据通过**
⇒ **`check_shell_routing_probes.sh` 的 rc=0 这里是"还原保住了结构锚点"的复测，不是"树干净"的证明**（⑦ 那条新规矩的原样适用：SR-* 看不见语义破口）。
② **表格自检 `python tools/check_ledger_tables.py` → `TOTAL FLAGS 0`（rc=0）**，本轮录入前基线：`AI_ITERATION_STATE 2840 / ANDROID_PARITY_AUDIT_V2 3283 / CONTEXT_LOCK 1156 / DECISION_LOG 2037 / THIRD_PARTY_NOTICES 351`。
③ **安全六条**逐条复跑（命令连 pattern 抄进常驻自检那一行）：main 本机地址 **0（rc=1）** · `.kts` **4 行** · `*.jks`/`*.keystore` **0** ·
`HttpLoggingInterceptor` **0** · 严格凭证 pattern **0（rc=1）** · key 字面量过滤后 **0（rc=1）**。
④ **真实性关键字**（只认 `tools/check_authenticity_counts.sh` 那一行输出，白名单九条未改、脚本 1,800 B / mtime 09-30 02:46）：
**窄 9 / 宽 44 / `GeoMath` 27 / `DemoData` 文件 4 / 假步进 0**。两个漂移都是**实测**、也都**归了能归的粒度**：
宽 45→44 的 −1 在逐文件构成里唯一差项是 `SendScreen` 2→1（该文件 mtime 09-30 20:50，剩下的那行是 `:593` 的"路网暂不可用，当前为直线估算"**禁止性说明**）——
**归到文件、归不到行**（无 git，那行原文没留档，"少了一行"是实测、"少的是哪一行"不是）；
`DemoData` 3→4 的第四个是 `ui/common/Bits.kt:39`（mtime **09-28 23:58，早于**"3 文件"那次读数）⇒ 判为**脚本口径漂移**，**不写成"本轮没涨"**，登记为待核尾巴。
⑤ ⚠ **本轮又制造了一次三口径互引的风险，照实记**：收工自检先用手写的 `grep -rniE "euclidean\|haversine\|straight" app/src/main --include=*.kt`
量出 **46 行（无白名单）/ 34 行（滤注释行）**，与脚本的 9 / 44 是**三套口径**（扫描面 `app/src/main` vs `app/src/main/java`、pattern 少 `mock\|fake\|连线\|直线`、白名单有无）
⇒ 那**两个手写数一律不采信**，台账只抄脚本那行。同理 `password = ""` 那条只在把凭证 pattern 的 `\{8,\}` 放宽成 `*` 时出现（同批三条都是空串，文件 mtime 09-28 23:59），
**红线判据是严格那版、0 命中成立**——"1 命中"与"0 命中"是两个口径，不是两件事。
**由此把 ⑦ 那条规矩再顶实一次**：口径不同的两个数并排放，比把一个数抄错更危险，因为它看起来像"变好了 / 变坏了"。



---

## D-042 F-2 ⑧ `safeApiCall()` 落点：改名不改签名、只迁 5 点；**判据下限不许推算**这条又被我自己抓住（2026-10-01）

**一句话**：§七 点名的那个 helper 工程里早就有（`apiCall`），本轮做的是**改名 + 让它真的有生产调用点**，
而不是新写一个第二份真相；配套 **SA-a…SA-f 判据簿 + 第 17 本负控**。三态读数见 `AI_ITERATION_STATE.md` 的
`CURRENT_ANDROID_STATUS`，证据文件与时间戳都在下面。

### 1 为什么改名而不改签名（先把上一轮的猜测证伪）

`Api.kt` 里的 `apiCall` 就是指令要的那个形状，于是走 **`apiCall` → `safeApiCall` 改名**，KDoc 写清它只分四类。
上一轮我判断"非挂起 lambda 形参包不住挂起调用，必须改成 `suspend () -> T`"——**这条是错的**：
`ApiTest.kt:171` 那条现成用例（inline + 非挂起形参里直接调 `Repo.xxx()`）实测能编译能跑（本轮 `ApiTest` 11 例仍全绿）。
签名保持不动的第二个理由是成本：全工程 38 个 Repo 读接口，改形参会一次制造几十个编译点，
而指令明写"不要为了一个 helper 制造 38 个编译问题"。**真正的病根不是签名，是休眠**——
改名前它自己的判据数得出定义行 `1`，生产调用点 **0**（`Z1b` 那一行就是这条证据）。

`attempt`（`core/net/Attempt.kt`）**保留**：它收 `Result`、服务非网络读（Worker / 定位 / 引擎 / 开屏预取 / 交接照片），
`safeApiCall` 服务网络读、要的是"**哪一类**失败"。两者不是重复 helper，SA-d 的下限就是替这句话站岗的。

### 2 五个迁移点分两级，台账不许记成"修了 5 个缺陷"

- **真实修复 2 处**：`linePolyline`（失败不再被洗成空列表，改 `getOrThrow()` 原样回抛同一个类型）、
  `logout`（后端撤销会话失败现在分类可见并打日志；**清本地仍无条件先做**——会话卫生优先于分类）。
- **形状归一 3 处**：天气预报段、`unreadCount`、`driverUnreadCount`。
  这三处旧的 `attempt { … }.getOrNull()` 里 `longOf` 本来就不抛 ⇒ **对外行为一字未变**，换的只是出口形状。
  `driverUnreadCount` 保留 `Prefs.driverId <= 0 → null 且不出网` 那道显式门
  （`ScalarEnvelopeContractTest` 有一条用例钉着它，改成穿透会让那条红——**先核契约再动手**，不是顺手统一）。

页面侧 `BusScreen` 新增 `polylineUnconfirmed`（不复用共享 `vm.error`：别的接口失败会把折线的病因覆盖掉），
`polylineReason()` / `polylineSubtitle()` 把"**这次没问到**（可重试）"与"**后端未回传**该线路折线"分开说；
旧形状 `reason = if (size >= 2) null else "后端没有…"` 归零——本轮唯一那句假话就是它。

### 3 ⚠ 本轮最实的一条：`attempt` 下限我**推算**错了，被自己的判据当场抓住

新簿 `SA-d` 与旧簿 `TH-f` 我都按"`12 − 2 = 10`"设了下限，两本判据**同时红**，实测是 **7**。
差 5 处、我只能归因 2 处——按纪律这时候**不许把下限随手改成 7 了事**，必须先解释缺口。归因链（全部实测/留档，不靠回忆）：

| 数 | 出处 | 是什么 |
| --- | --- | --- |
| **12** | `/tmp/vf_probe_check_throwable_probes.log`，mtime **2026-09-30 23:30:30** | F-2 ⑦ 收工时 `TH-f attempt 调用点/helper 穿透 = 12/1`，同一条判据、同一个扫描面（迁移**前**） |
| **5** | 本轮 5 个迁移点 | 我上轮以为"只有两处未读数来自 `attempt`"，**实际 5 处全部**原本是 `attempt { … }.getOrNull()` |
| **7** | 本轮实测 | 非 Repo 的 7 处（`BusPostApp:37`、`Locator:204`、`NotifyWorker:23`、`BusPostRoot:98/99`、`DriverSdkNavCard:134`、`HandoverScreen:147`）本轮一字未动 |

⇒ `12 − 5 = 7` 与实测吻合，**没有未归因缺失，也没有"树被顺手改过"**。两本判据的下限同时改成已归因的实测值 `≥7`，
注释里写明这条链；负控仍一键打破（把 `Locator` 那处换成 `runCatching` ⇒ 7→6 当场红，实测 ✔）。
**教训升格**：**判据下限只能实测，不许推算**——同一类错的第三次（D-034 的"✔=18"推算、F2-7i 的注入空跑、本轮的下限推算），
三次都不是代码错，是**我把"我以为的差值"当成了"量出来的基线"**。

### 4 另外两条本轮现场抓到的"永远绿 / 永不匹配"

- **SA 判据簿有一条死闸门**：`[ "$C_VM" = 1 ] || { echo "…": fail=1; }`——冒号代替分号让 `echo` 把 `:` 和 `fail=1`
  当成参数打印，`fail` 永远不会被置上。负控跑起来之前它是静默的；修法是把 `:` 换成 `;`。
- **负控第 17 条第一次报 rc=0**：它注入的是**沙盒副本里的那本判据簿**，`ctl` 跑的却是**真身那本**
  （`PR=$SRC/tools/…`）⇒ 破口根本没进被执行的那份。改成 `ctl` 第三参指定要跑哪一本。
  `verification_failure_negative_controls.sh` 早就用 `$PRSC` 处理过同一件事，**我没抄它的机制**——
  "打到判据簿自己头上"的破口必须连"执行哪一本"一起换，否则这条永远是摆设。
- **接力脚本的 `END_MARK` 写死「共 16 本」**：新写第 17 本之后永不匹配，会一路等到 `MAX_WAIT` 再 exit 8，
  而"等不到"看起来像"批次还没跑完"、不像"口径过期了"。现在批次与接力**共用同一个 glob** 推 `EXPECTED_BOOKS`，
  两边都不写死本数（`run_all_negative_controls_batch.sh` 的 `total` 本来就是 glob 数出来的，写死的是接力那一侧）。

### 5 实测读数（证据都在文件里，不靠这段文字的自述）

- **SA 判据簿** `tools/check_safe_call_probes.sh`：**15 行读数 / 34 道闸门，rc=0**。关键读数：
  GUARD `1`；helper 定义 `1` / 旧名残留 `0`（反证新名 `9`）；体内 `1/1/4/0`（兜底反证 Repo `38`）；
  使用点 `6`（Repo 5 + 页面 1）、五点点名 `1/1/1/1/1`；`?: return emptyList()` `0`（反证 `emptyList()` 在 Repo `24`）；
  `attempt` Repo `0` / 全 main `7`；`getOrThrow` `1` / 签名 `1`；页面状态 `1/2/1/2/1`；旧形状 `0/0/0`、新形状 `1`；
  用例 `12 / 取消 2 / 四类 3 / 不出网 3`（留档 `/tmp/f2_8_sa_probe.log`）。
- **SA 负控** `tools/safe_call_negative_controls.sh`：**17 条破口 + 3 条自检行（BASE / 空锚点 / RESTORE）**，
  实测 **✔=20 ✘=0 △=0**、rc=0、用时 80 s（留档 `/tmp/f2_8_sa_negctl.log`）。计数由脚本自己累加打印，不是事后 grep 推算。
- **负控全批**：`共 17 本`（02:39:11 → 03:10:35），逐本 **rc=0**，合并 **✔=309 / ✘=0 / △=0 / ◦=3**
  （`/tmp/final_neg_batch.log`）。就地型那几本的还原有 `cmp` 读数（`/tmp/shell_routing_test_negative_controls.run.log`
  末三行"[恢复] ShellDecision cmp 一致 / [恢复] Routes cmp 一致 / [恢复后] gradle exit=0"）；
  批次之外又补了一次残留扫描：`app/src/main` 里 `NEG-CONTROL` / `negControlProbe` **0 命中**，
  D-041 那个出事锚点 `ShellDecision.kt:36` 现值是 `role != Role.DRIVER && route in Routes.DRIVER_ONLY -> null`（原样）。
- **全量用例**（接力脚本在批次结束后单发起，`--rerun-tasks`）：`BUILD SUCCESSFUL in 30s`、
  **24 actionable tasks: 24 executed**（无 FROM-CACHE），**38 类 / 368 例 / 0 failures / 0 errors / 0 skipped**，
  XML mtime 集合只有 `03:11:16`（一份都没被负控洗过）。本轮点名：`SafeApiCallContractTest` **12**、
  `ApiTest` **11**、`DemoExceptionIsolationTest` **7**、`ApiCancellationTest` **5**、`VerificationFailureTest` **8**。
- **构建**：`:app:assembleDebug` rc=0，`BUILD SUCCESSFUL in 1m 11s`（48 tasks：10 executed / 38 up-to-date），
  `app-debug.apk` **114,437,929 B** mtime **03:12:23**。
- **lint**：第一次 `lintReportDebug` 是 **UP-TO-DATE**（XML 还是 09-30 21:09:49 那份）⇒ **不拿旧产物充当本轮读数**，
  就地 `:app:lintDebug --rerun-tasks` 重跑：`BUILD SUCCESSFUL in 1m 17s`、28 tasks 全 executed，
  `lint-results-debug.xml` **98,753 B / mtime 03:15:06**，**0 error** / 46 Warning + 32 Information = 78（与上一轮同数）。
- **判据簿 collateral**：`for b in tools/check_*.sh` **17 本全部 rc=0**（本轮新增那本没把别的簿顶红）。
- **核对表 census**（`tools/v21_requirement_census.sh`，rc=0，rows=118）：
  `V21-07` `attempt {` hits=**8** / files=**7** / **生效 7**（含注释那 1 行是 Repo KDoc 里写的"旧写法"原文）·
  `safeApiCall` hits=14 / 生效 9 · 旧名 `apiCall` **0**；`V21-30` 点名 18 类里 present **6**（新增 `SafeApiCallContractTest` 12 例）。
  ⚠ **03:44 台账收工复跑更正（`diff` 两份产物 `/tmp/v21_census_f2_7.txt` ↔ `/tmp/f2_8_ledger_census.log`，不是重算差值）**：
  上面那句"点名 18 类"口径不准 —— V21-30 的**名单实测 20 名**（指令 §三十 点名的 **18** 本 + ⑦ 加 `VerificationFailureTest` + ⑧ 加 `SafeApiCallContractTest`），
  present **6** / ABSENT **14**；且 `SafeApiCallContractTest` **不在**指令那 18 本里（⑧ 开工时把它说成"V21-30 点名的那本"是把"我加进对表"读成了"指令点名"）。
  行数 117→**118** 的**唯一新增行**就是那一本；其余差异全是**计数**变化（`V21-01` 判据簿 15→16 / 负控 16→17、`V21-07` 三处、`V21-22` 1→2、`V21-33` consumers 6→7）。
  ⚠ **口径提示**：`SUMMARY|rows=118` 是脚本自己末行的读数、**不含 SUMMARY 行**，同一份产物 `wc -l` 给 **119** —— 两个数都"对"，**但不可互引**（与 §八 ㉑"两个 dex 口径不可互引"同族）。
- **改动面**：新增 **3**（`SafeApiCallContractTest.kt` / `check_safe_call_probes.sh` / `safe_call_negative_controls.sh`）；
  改动 **9**（`Api.kt` 02:06:37、`Repo.kt`、`BusScreen.kt` 02:15:03、`ApiTest.kt` 02:17:08、
  `v21_requirement_census.sh` 02:18:50、`check_safe_call_probes.sh` 之后 02:30:42 的 `check_throwable_probes.sh`、
  `throwable_negative_controls.sh` 02:30:45、`after_negctl_run_full_tests.sh` 02:38:57、
  `run_all_negative_controls_batch.sh` 02:39:02）。
  ⚠ 另有一批 main 源码 mtime 落在 **02:52–03:06**（`ShellDecision.kt` / `Routes.kt` / `ActionGate.kt` /
  `DriverException*.kt` / `NavigationEngine.kt` / `WorkbenchScreen.kt` / `Repo.kt`），
  **那是就地型负控的注入与还原**，不是本轮的功能改动——按 mtime 报改动面会把它们错算进来，
  所以这一条清单是按"我改了什么"数、mtime 只用来交叉核对（两边都有读数时，别把机制的痕迹写成决定）。
- **台账收工复跑（2026-10-01 03:37–04:02，纯文档轮：不动 `app/src`、不再起 gradle）**：
  · **判据簿** `for b in tools/check_*.sh` **17 本全部 rc=0**（03:50:44→03:51:35 逐本留档 `/tmp/f2_8_ledger_probe_check_*.log`）；
    本轮新那本 `check_safe_call_probes.sh` 尾部实测 `SA-e 旧形状三处 = 0/0/0`、`SA-e 新形状 reason 走 polylineReason = 1`、
    `SA-f 用例条数/取消/四类/不出网 = 12/2/3/3` ⇒ `safeApiCall 落点判据通过`。
  · **真实性**（只认 `tools/check_authenticity_counts.sh`，03:51:35）：**窄 9 / 宽 46 / `GeoMath` 27 / `DemoData` 文件 4 / 假步进 0**。
    与 ⑦ 的差 **只有 `BusScreen.kt 2 → 4`** —— 这句是**两遍 17 文件构成表逐项 diff** 出来的（本轮脚本产物 ↔ 台账里 ⑦ 那份 17 行表），
    16 个文件逐项未变、两边加总 44 与 46 ⇒ **不是** `46 − 44 = 2` 的反推。那两条新命中就是 `:161` KDoc 与 `:165`"这次没问到该线路的折线"，都是禁止性表述。
  · ⚠ **同一行的两个口径，绝对值不可互引**（本轮实测到"谁被滤掉了"）：不带白名单的同 pattern 全树 **120** 行（台账里那句"89 行量级"是 ⑩–⑬ 期旧读数，
    中间各轮无此项留档 ⇒ 那 31 行**归不到轮次**）；`Api.kt` 唯一那 1 行 raw 命中（`:178` 提到 `MockWebServer`）被 `MockWebServer` 那条白名单滤掉
    ⇒ **脚本的构成表里没有 `Api.kt`**；`Repo.kt` 是 **7 → 3**（被"mock 标注 / 适合当日运输"那条滤掉 4 行）。
  · **census**：`tools/v21_requirement_census.sh` rc=0 / **rows=118**（03:45:08，`/tmp/f2_8_ledger_census.log`），与 ⑦ 产物 `diff` 的**唯一新增行**
    = `V21-30|SafeApiCallContractTest|present|tests=12`（口径提醒见上面第 5 节那条更正）。
  · **安全组**（`CONTEXT_LOCK.md` §八 ⑥ 四条 + 两份 manifest 的 cleartext，04:01:55 从**工程根**跑）：**0 / 4 / 0 / 0**；
    release `usesCleartextTraffic="true"` **0**、debug **1**（debug 那一处是刻意的，release 必须 false）；`app/build.gradle.kts` 那 4 行仍是 2 注释 + 2 构建期拦截。
    ⚠ 顺手多跑的一条 `(token|password|secret)…= "` 字面量 grep 给 **2** 命中（`MinePages.kt:171/172` 的 `password = ""` 表单重置）—— **不是密钥泄漏**，
    也**不在**在册六条里，登记在此只为了下一个抄这行数的人不必把它当成新异常。
  · **表格自检**：`python tools/check_ledger_tables.py`（无参数，五本手写台账）**TOTAL FLAGS 0 / rc=0**，报告落 `tools/ledger_table_report.txt`。
    ⚠ 这台自检本轮**真抓到过一次**（见下面那条）——它此前只被抓过一次"存在性"，这次是它第一次**判红我本人**。
- **⚠⚠ 本轮台账收口自己制造的一次静默破坏（登记的理由是它改的是台账、不是代码，`check_ledger_tables.py` 与 gradle 都救不了它）**：
  ① 向状态板"真实性关键字（宽口径）"那一行追加 ⑧ 读数时，把它断成了 **6 个物理行** ⇒ 表格在那一行之后静默断掉，
    脚本报 `1358 cells 3 vs header 4`（这正是该脚本注释里写的"拆行比并行更难发现"那一种）；续行必须并回同一行。
  ② 并回时用手写 python 以**文本模式**写回，Windows 默认换行把整本 `AI_ITERATION_STATE.md` 从 **LF 翻成 CRLF**
    （**440,679 → 443,725 B，+3,046 = 每行多一个 `\r`**、且 `diff` 表现为"整本重写"）。已按事前备份
    `/tmp/AI_ITERATION_STATE.before_joinrow_035601.md` 以二进制还原为 LF（还原后字节数**回到 440,679**、`CRLF=0`，
    与备份 `diff` 的唯一差异就是那一行的并回；此后又改了两次文案，现值 **441,886 B / CRLF 0 / flags 0**）。
  ⇒ **规矩**：改手写台账优先用编辑工具；非要写脚本改，就**显式指定换行或以二进制写**，且改完立刻比**字节数 + CRLF 计数**（`grep -c` 看不出来）。
    这与"禁止 `'w'` 先截断再报错"是同族缺陷：**文本模式的隐式转换和 `'w'` 的隐式截断一样，都是先破坏再报告**。
  · **本轮没有复跑 gradle**：盘上"树干净"的证明仍是 03:11:16 那遍 **38 类 / 368 例 / 0 失败**（旧读数，不是本轮产物）；
    本轮改动面实测为 `app/src` **0 个文件**（`find app/src -newermt "2026-10-01 03:37"` → **0**），动的只有五本台账/核对表与脚本自产物
    `tools/ledger_table_report.txt`。设备侧一律 `NOT VERIFIED ON DEVICE`，release 仍 **BLOCKED**。

### 6 后果与遗留（照实登记，不写"完成"）

- **设备侧仍 `NOT VERIFIED ON DEVICE`**：本轮 12 条新用例全部只有 JVM 证据；`BusScreen` 那两句文案在 JVM 里渲染不出来，
  只有静态判据（这是 SA-e 存在的理由，也是它的**上限**）。release 仍 **BLOCKED**（不绕 HTTPS 闸门、签名文件不进源码工程）。
- **在册未做**：F-2 ⑨–⑱（`runCatching` 审计、`NotifyWorker` 无条件 success、`DEVICE_UNAVAILABLE`、OSM 署名、
  依赖/APK 对照、契约证据文档、全量汇报）；§五 那 **32** 处 C 站点与 `plannedMinutes` / `plannedDurationMinutes`。
- **本轮新登记的缺陷（未动，扩大战线是指令禁止的）**：线路页首屏 `load()` 自动选中线路但**不拉折线**，
  于是 `polylineSubtitle()` 里"后端未回传该线路折线"那一句在**问过之前**就说了出口——
  与本轮修的那句是同一族（把"没问"说成"没有"）。修法要么首屏跟着选中拉一次，要么副标题再分一个"还没问"态；
  现在两处判据（SA-e 的 `E_SUB=1` 与 `E_OLD_SUB=0`）都**允许这个缺陷存在**，所以它只有用例之外的这段文字记账。

---

## D-043 F-2 ⑨ `runCatching` 协程取消审计：34 点逐点实测、只迁 4 点；**跨簿影响我按推理写了一次"无需改"，实跑是红的**（2026-10-01 09:44）

### 1 指令与落点
指令要求：基数 34 生效行逐个分类（A 纯同步 / B suspend / C Flow / D callback bridge / E navigation SDK / F location / G worker），
对 B–G **禁止吞 `CancellationException`**，改成 `try {…} catch (e: CancellationException) { throw e } catch (e: Exception) {…}`；
并明写"不要全局机械替换"、"要求 suspend cancellation violation = 0，但不要为了得到 0 而误删合法 runCatching"、
"每个修改点记录 文件/行号/原始行为/新行为/为什么取消敏感"。
落点：新增 `docs/android/RUNCATCHING_AUDIT.md`（六节：读数 / 4 处修改点 / 34 点分类 / A–G 口径 / 跨簿影响 / 在册缺陷与三态）、
`tools/runcatching_detector.py`（闭包检测器）、`tools/check_runcatching_probes.sh`（RC-a…RC-e）、`tools/runcatching_negative_controls.sh`。

### 2 实测读数（`bash tools/check_runcatching_probes.sh` rc=0）
`RC-a 生效行/生效文件/出现次数/含注释文件 = 30/16/46/22`（本轮前 34/17/44/22）· `RC-a2` 同管道反证 attempt 行 = **11** ·
`RC-b violations = 0` · `RC-b2 sites/候选/挂起名面 = 30/0/98` · `RC-c 四处字面量 = 1111 / 回退残留 0/0` ·
`RC-d runBlocking / suspendCancellableCoroutine = 0/0` · `RC-e 分类表 = 34 行 / 其中已迁 4`。
检测器原始行：`SUMMARY|sites=30|violations=0|candidates=0|suspend_names=98|suspend_decl_lines=101|run_blocking=0|scc=0`。
census 与 ⑧ 留档 `/tmp/f2_8_ledger_census.log` **逐行 diff**（`rows=118` 不变）：变的只有 3 行
——`V21-01` 判据簿 16→**17** / 负控 17→**18**、`V21-07` `attempt {` 8/7/7→**12/9/11**、`V21-17` `runCatching (main)` →**46/22/30**。

### 3 结论：34 点里只有 4 点的取消是真够得着的
判定不是"外层是不是 suspend/composable"，而是**包围边界是不是 suspend**：`Runnable{}`、`Handler.post{}`、
`remember{}`（`() -> T`）、`AndroidView factory/update`、`onClick: () -> Unit`、`internal var launch: ((String) -> Unit)?`
这些非 suspend lambda 会**截断**取消可达性；而 `let`/`run`/`also`/`forEach`/`onFailure` 是 inline，会透传。
两条结构免疫的腿都有数在判据里：全工程 `runBlocking = 0` 且 `suspendCancellableCoroutine = 0` ⇒
非 suspend 成员函数体内不可能挂起 ⇒ 那 **30 点保持原样**（块体是 JSON 解析、SDK 句柄、Handler/Intent，
删掉它们等于把错误处理删了）。⚠ 这两条腿只要有一边非 0，30 点的分类必须整表重做 —— 这就是 RC-d 存在的理由，
不是装饰。**没有**为了把 violation 做成 0 而删任何合法 `runCatching`（生效行只从 34 降到 30，降的正是迁走的 4 处）。

### 4 迁的 4 点与"行为一字未变"
`Repo.kt:356→359`（openMeteoWeather 的 daily 预报解析段）、`AmapNavigationEngine.kt:152→155`（算路是否被受理）、
`BusPostRoot.kt:84→85`（深链跳转）、`DriverNavScreen.kt:695→697`（TTS 设语种）。四处都用既有的通用出口
`core/net/Attempt.kt` 的 `attempt`，它与 `runCatching` 的唯一差别是**取消穿透**，两者都捕到 Throwable 级、都回 `Result`、
下游仍 `.getOrDefault(...)` ⇒ 对外行为不变（这一点是本轮能只动 4 个文件、且不动任何用例断言的前提）。

### 5 证据
- 编译：`:app:compileDebugKotlin` rc=0，`BUILD SUCCESSFUL in 27s`，15 actionable tasks（1 executed / 14 up-to-date），
  留档 `$TEMP/f2_9_compile.log`。
- 负控：`tools/runcatching_negative_controls.sh` rc=0 —— **12 条破口全部打破** + **2 条反向"必须不红"**（同行前缀不计、
  接收者不匹配只进 CANDIDATE）+ 2 条预检（检测器不在场 / 源文件缺失 ⇒ exit 2 而不是把"跑不了"读成 0），末行"恢复后基线仍为绿"。
- 同轮复跑：`check_safe_call_probes.sh` rc=0；`safe_call_negative_controls.sh` **18/18** 打破、RESTORE `11/11/11/11`；
  `throwable_negative_controls.sh` 19 行 ✔ / 0 条 ✘△；`shell_routing_negative_controls.sh` 13 ✔ / 0 ✘△、恢复后"壳路由判据通过"；
  17 本 `tools/check_*_probes.sh` 全量复扫（下面那条是唯一的红）。

### 6 ⚠ 本轮最大的一条：跨簿影响**写了推理当结论**
审计文档第五节我先写的是"SA 的锚点指 `Repo.kt:312`，与本轮 `Repo.kt:359` 不是同一行 ⇒ **无需改**"。
实跑（扫全簿）给的是 `check_safe_call_probes.sh` **rc=1**：`× SA-d Repo 又出现 1 处 attempt`。
破口根本不在那两条锚点上，而在 SA-d 的 `D_ATTEMPT_REPO`——它数的是 **Repo.kt 整本**的 `attempt {`，
⑧ 之后那个不变量是 0，⑨ 往里放了 1 处就破了。同轮处置（不是"调绿"）：
把 "=0" 收成 **"恰好 1 处 + 整行字面量点名"**（新增 `D_ATTEMPT_FORECAST` 钉 `val forecast = attempt {`），
全 main 下限同轮 **7→11**（= ⑧ 的 7 处 + ⑨ 迁入的 4 处，逐点归因在审计文档第二节），
并给 `safe_call_negative_controls.sh` **新增第 7b 条**（数量没变、只把那处换个名 ⇒ 只有点名字面量会红），
第 10 条文案随下限一起改。教训入 §八 ㉖：**"指向不同行"不等于"不影响那一本"**，跨簿影响只能是"把每一本扫一遍的实测"，
不能是我对那一本怎么写的推理。

### 7 本轮留下的临时测量脚本（不在任何判据里，未删除）
`tools/_rc_dump.py`（34 点块体逐点导出）、`tools/_rc_ctx.py`（每点包围上下文）、`tools/_rc_tail.py`（块尾配对检查）、
`tools/_rc_chain.py`（包围链：从 `runCatching` 往外层签名一路打印，第三节分类的证据生成器）。
四本都只读源码、无判据引用（`grep -rn '_rc_'` 除自身外 0 命中），按"任何新文件都要同步进清单"登记在此与 `THIRD_PARTY_NOTICES.md`。

### 8 三态与未做（不写完成度）
生产源码改 **4** 个文件、判据/文档/脚本 **7** 个文件（含本轮为 SA-d 的 2 处修改）⇒ `SOURCE_COMPLETE`；
`:app:compileDebugKotlin` 只验到 debug 面 ⇒ `BUILD_VERIFIED`（debug 面；全量 `test`/`lintDebug`/`assembleDebug` 在 ⑱）；
**`DEVICE_VERIFIED = NOT VERIFIED ON DEVICE`**（本机 `adb devices` 为空，RC-* 全为静态判据，取消行为只有 `ApiCancellationTest`
那一层有 JVM 证据，页面侧渲染不出来）。release 仍 **BLOCKED**（HTTPS 闸门未绕、签名文件未进工程）、git 仍 **ENVIRONMENT_BLOCKED**。
在册未做：⑩–⑱；§五那 **32** 处 C 站点、`DriverShell.kt:37` 角色写、线路页导航入口仅 DEBUG 可达、
线路页首屏不拉折线、`Prefs.kt:53` 脏值静默降级 —— 本轮一字未动，全部维持 `OPEN_DEFECT`。

---

## D-044 F-2 ⑰⑱ 收工验证：全量 391 例里那 **1 例红是真缺陷**、批次"exit 0"是假绿、eta 有两条判据**没有判别力**（2026-10-01 12:10–13:10）

### 1 指令与落点
⑰⑱ 要的是"不扩大 UI + 全量验证 + 回归扫描 + 状态落档"。落点：`AI_ITERATION_STATE.md` 状态板新增
`CURRENT_ANDROID_STATUS`（⑰⑱ 块）、`CONTEXT_LOCK.md` §九 新增 3 行、`docs/android/THIRD_PARTY_RUNTIME_MATRIX.md`
APK 行重锚、`tools/run_all_negative_controls_batch.sh` 补聚合退出码（第 4 节）。**没有新增任何页面、任何 Demo 功能、任何后端改动。**

### 2 实测读数（全部是产物读数，不是我的复述）
- **负控全批**：`bash tools/run_all_negative_controls_batch.sh /tmp/allneg_f2_18.txt` → 20 本，`=== 批次结束 12:45:59，共 20 本`。
  逐本 `rc=0` 18 本；`notify_result`/`oss_credits` 两本 `rc=2`（末行"✘ 真身没有 app/build/test-results/testDebugUnitTest/TEST-…xml"，
  即它们自己的预检：读数没有来源就不许交差）⇒ 全量用例跑完后**单独复跑这两本**：`rc=0`、`✘=0`、末行 `RESULT=PASS`
  （日志 `/tmp/negctl_retry_notify_result.log`、`/tmp/negctl_retry_oss_credits.log`）。
  ⚠ `eta_negative_controls.sh` `rc=0` 但 `✘=2` —— 见第 3 节，这是**本轮抓到的一条真缺陷（登记未修）**。
- **全量用例（debug）**：`:app:testDebugUnitTest --rerun-tasks` → **40 类 / 391 例 / failures 1 / errors 0 / skipped 0**（`/tmp/f2_18_relay.log`，12:49:43）。
  那 1 例是 `LoginViewModelTest."登录成功用的是后端 token"` @ `LoginViewModelTest.kt:84`（`assertFalse(vm.busy)`）。
- **全量用例（release）**：40 类 / 391 例 / **failures 3** / errors 0（12:03:21 产物），红的仍是 `EarningsContractTest`/`MinutesContractTest`/
  `NearbyBooleanContractTest` 各 1 例，用例名都含"演示"—— release 变体里没有演示面，这三条是**在册的演示面口径差**，不是新破口。
- **lint**：`:app:lintDebug --rerun-tasks` rc=0 → `lint-results-debug.txt` **0 errors, 46 warnings**。
- **APK**：`app-debug.apk` = **113,879,444 字节 @ 13:02:18**，sha256 `43a4389b83811f21d33e40fbc3af08fd…`；
  `app/src` 树指纹（146 个源文件的 path+len+content 摘要）= `6e263735675020c121a23081bafe2cd4`。
- **设备**：`adb devices` → `List of devices attached` **空**；`bash tools/verify_device.sh --no-build` 打印
  `[STOP] 没有处于 device 状态的设备 ⇒ DEVICE_UNAVAILABLE`，证据落在 `docs/device-evidence/20261001-130439/`（本轮新增两份文件，登记在第 6 节）。
- **git**：`git rev-parse --is-inside-work-tree` → `fatal: not a git repository` ⇒ `ENVIRONMENT_BLOCKED` 维持，**没有** `git init` 造假历史。
- **release 产物**：仍未产出（HTTPS 闸门 `app/build.gradle.kts:171` 保持、未加 `-Pbuspost.allowInsecureRelease=true`、签名文件不在工程内）⇒ `BLOCKED`。

### 3 那 1 例红不是 flake：`busy` 的终态排在回调之后
`LoginViewModel.submit` 成功支里 `onDone(chosen)`（`:138`）先跑、`busy = false` 在 `finally`（`:149`）里才落，
所以测试线程从回调里被唤醒的那一刻 `busy` 仍是 true。证据链：
①同一份源码单跑该类 **3 次全绿**（12:50 三次 `--tests` 隔离跑，`tests=6 fail=0`），②全批里红 —— 差别只在调度快慢，
③今早 01:21/01:25 两次全量都是 `fail=0`（`/tmp/after_negctl.log`、`/tmp/final_full_run.log`）。
⇒ 结论是**断言点站错了时序**，不是"环境抖动"，也不是可以划掉的 flake。
处置（改生产源码、不缩小断言）：`onDone` 之前显式 `busy = false`，`finally` 仍管失败支；
理由是产品侧本来就该如此——回调一进就导航，回调方读到的不该还是"正在提交"。
改后全量复跑：**40 类 / 391 例 / failures 0 / errors 0 / skipped 0**（12:53:xx，`/tmp/f2_18_full_after_loginfix.log`）。
⚠ 这条缺陷是**我 ⑨/⑧ 那两轮改协程写法时带出来的时序面**，不记成"测试写得不好"。

### 4 批次"exit 0"是假绿：本轮给它补了聚合退出码
`run_all_negative_controls_batch.sh` 只逐本打印 `rc=`，**没有任何聚合**：本轮两本 `rc=2` + 一本 `✘=2`，
整批仍然 exit 0（后台任务通知就是 `exit code 0`）。这就是 #125 那条"死退出码"的另一半：簿自己会红，批不会。
同轮机制修补（不是调绿）：批脚本累计 `rc_bad`/`cross_bad`，任一非空 ⇒ 打印 `[聚合] …` 两行后 **exit 1**，
并把"共 $total 本"那行留在末尾（`after_negctl_run_full_tests.sh` 的放行字符串 `共 20 本` 一字未动）。
`bash -n` 通过；模拟聚合（对现存 run.log 数 `^✘`）得 `eta(✘=2) notify_result(✘=1) oss_credits(✘=1)` —— 后两条是复跑前那份旧日志的残留，
下次整批会覆盖。⚠ **本轮没有为"让批次绿"去动 eta 那两条**，见下一节。

### 5 `eta_negative_controls.sh` 的两条 ✘ = 这两条判据**等于没判据**（登记 `OPEN_DEFECT`，本轮未修）
`/tmp/eta_negative_controls.run.log`（12:21:19，本轮产物）第 9 行、第 43 行：
```
✘ T-e  又拿 0 当『还没到时间』 → 负控制失效（exit 0），这条判据等于没判据
✘ T-w2 提醒又读回没有 ETA 键的 /send/arrangements（§68 复发） → 负控制失效（exit 0），这条判据等于没判据
```
含义要说准：把源码按这两条点名的破口形状改坏之后，`check_eta_probes.sh` 仍然 exit 0 ⇒ **T-e/T-w2 现在拦不住任何东西**。
第 ⑪/⑫/⑬ 轮把 ETA 侧收口时判据是真的响过（那几轮的负控记录里这两条是 ✔），
今天变成 ✘ 说明**锚点被后续改动漂掉了**，属"绿灯会自己烂"这一族，不是新写判据时的漏网。
本轮不修的理由要写清而不是含糊：修法要重锚 `check_eta_probes.sh` 的 T-e/T-w2 字面量并同轮复跑 ETA 全族负控，
那是 ⑱ 之后的独立一条（已排进 F-3 队列：与 §五 32 处 C 站点同批，因为 `distanceMeters`/`etaSeconds` 这几处就在那 32 处里）。
`D-⑰⑱-1`（ETA 两条判据无判别力）就是它。

### 6 本轮新增文件（按"任何新文件都要同步进清单"登记）
- `docs/device-evidence/20261001-130439/device-probe.log` + `summary.md` —— `verify_device.sh` 自己写的 0 台设备证据。
- `tools/run_all_negative_controls_batch.sh`（**改动**，非新增）：聚合退出码。
- 无新增源码文件、无新增判据簿、无新增测试类（⑰⑱ 只做验证与落档；F-3 §十八 的 10 本测试类在下一轮）。

### 7 三态（分开写，不混）
- `SOURCE_COMPLETE`：本轮生产源码只动 `ui/login/LoginViewModel.kt` 1 个文件 1 处（+2 行含注释）。
- `BUILD_VERIFIED`（debug 面）：`assembleDebug` + `lintDebug` + `testDebugUnitTest` 三条 rc=0，读数见第 2 节。
  release 面 **BLOCKED**（产物从未产出），`lintRelease` 未跑 ⇒ 不许把 debug 读数当全变体。
- `DEVICE_VERIFIED = NOT VERIFIED ON DEVICE`：0 台设备（实测），本轮 391 例全是 JVM 证据。
- `GIT = ENVIRONMENT_BLOCKED`（无仓库、不 `git init`）。

### 8 OPEN_DEFECT（F-3 开工基线，全部本轮实测复核过，不凭旧记忆补编号）
| 编号 | 内容 | 本轮实测 |
|---|---|---|
| §五 32 处 C 类 | 三面 0 生产者的数值/布尔非可空读法 | census `tools/default_value_census.py` → `rows=472 / B=398 / C=74`，其中数值/布尔非可空 **32** 处：`Models.kt` 18 + `Repo.kt` 11 + `SendScreen.kt` 3（逐点清单见本轮 chat 与 F-3 落档） |
| `DV-k` | 全项目数值/布尔非可空读法基线 | `bash tools/check_default_value_probes.sh` rc=0：**132**（只许降） |
| D-⑯-1…8 | `CONTRACT_EVIDENCE.md` 七.10 的 8 条 | 该表 8 行**无一划掉**（`grep -c 'D-⑯-'` = 16：表内 8 + 引用 8） |
| `PRODUCER_NOT_FOUND` | 逐字段台账里查不到生产者的键 | `CONTRACT_EVIDENCE.md` 命中 **6** 处（:151 orderNo、:160 remark、:180、:194 distanceKm(/send/legs)、:196、:206 amount） |
| 角色写 | 司机壳里按 tab 改角色 | `DriverShell.kt:38 AppState.setRole(Role.USER)`（**台账写的 :37 是漂的行号**）；main 里 `setRole`/`Prefs.role =` 共 4 个写点（`Repo.kt:171`、`MineScreen.kt:92`、`WorkbenchScreen.kt:180`、`DriverShell.kt:38`） |
| 导航入口 DEBUG-only | 真实落点不在用户线路页 | `grep BuildConfig.DEBUG` 命中 10 处，导航相关 7 处：`BusPostRoot.kt:138`、`RoutesScreen.kt:206,348`、`WorkbenchScreen.kt:627,684,963,1069`；`ui/user/BusScreen.kt` **0 命中** ⇒ 台账"线路页导航入口"这句是**错位描述**（要改写描述，不是按原样"修"） |
| 线路页首屏折线 | 没问过就说"后端未回传" | `BusScreen.kt:108-110` 首屏自动选线不拉折线，`polylineSubtitle()` 的 `else ->`（`:169-173`）仍会输出"后端未回传该线路折线" |
| `Prefs.kt:53` | 脏 role 值静默降级成 USER | 在册未动 |
| ETA 判据 | T-e / T-w2 无判别力 | 本轮新登记（第 5 节），未修 |
| 演示面口径 | release 3 例红 | 实测 failures=3，用例名含"演示" |
| `AlertEngine.resetWatermark()` | 定义在场、调用点 0 | `grep -rn resetWatermark app/src` = 2 行（定义 + 注释） |
| `sessionExpired()` | main 里只有 1 处（自身声明） | 死函数在册 |
| 矩阵覆盖面 | `THIRD_PARTY_RUNTIME_MATRIX.md` 只覆盖 debug 变体 | 未变 |
| DEVICE / RELEASE / GIT | 三条环境项 | 0 台设备 / release 产物从未产出 / 非 git 仓库 |

## D-045 F-3 ⑩⑪⑮：用户页那三格不是"后端没给"，是客户端一行都没读；读通之后新钉 12 例，但**这条新契约面还没有判据簿**（2026-10-01 13:5x–14:55）

### 1 指令与落点
⑩ 用户实时物流基础版（状态 / 时间线 / 当前任务 / 车辆位置 / 最后更新时间 / 路线状态；无位置就直说"暂无实时位置"，禁 `0,0`、固定点、随机点）、
⑪ 司机 GPS → `reportLocation` → 后端 → 用户 tracking（禁两端内存共享位置）、⑮ 用户物流只消费后端真实 geometry。
落点：`Models.kt` 新增 `LocationSource` 枚举（吸收并删除私有 `sourceOf`）+ `ParcelTrace` 三格；`Repo.kt:traceOf` 补三处读取；
`TraceView.kt:122-143` 三态文案；新测试类 `UserTrackingContractTest`（12 例）；新工具 `tools/check_doc_anchors.py`；
`docs/android/CONTRACT_EVIDENCE.md` 新 §八；顺带把 `docs/android/DEFAULT_VALUE_AUDIT.md` §四 被本轮插行漂掉的锚点重锚。
**没有新增页面、没有新增 Demo 功能、没有改后端、没有改微信小程序、没有动 HTTPS 闸门。**

### 2 实测读数（全部是产物读数，不是我的复述）
- **负控全批**：`bash tools/run_all_negative_controls_batch.sh` → 末行 `=== 批次结束 …，共 20 本`；逐本 `rc=0` **18 本**，
  `notify_result`/`oss_credits` 两本 `rc=2`（末行是它们自己的预检"真身没有 TEST-…xml"⇒ 没有读数来源就不许交差），
  接力全量跑完**单独复跑**这两本 ⇒ `RESULT=PASS`（`破口=12 反向=2 预检=2 命中=16 失效=0` / `破口=13 反向=2 预检=4 命中=19 失效=0`）。
  ⚠ `eta_negative_controls.sh` `rc=0` 但 `✘=2` ⇒ `D-⑰⑱-1`（T-e / T-w2 无判别力）**本轮复现，仍未修**。
- **全量用例（debug）**：`:app:testDebugUnitTest --rerun-tasks` → **41 类 / 403 例 / failures 0 / errors 0 / skipped 0**
  （XML 产物 mtime 14:23:58，`/tmp/after_negctl.log` 末行 `[结束] 14:24:03`、`AFTER_EXIT=0`）。类数从 D-044 的 40 变 41 = 本轮新增 `UserTrackingContractTest`。
- **20 本判据簿**：`tools/check_*.sh` 全 `rc=0`、`✘=0`。`check_default_value_probes.sh` 的 **DV-k = 132（只许降，本轮未升）**。
- **构建**：`:app:assembleDebug :app:lintDebug --rerun-tasks` → `BUILD SUCCESSFUL in 2m 24s`（`/tmp/f3_final_build.log:77`）、后台任务 exit 0。
- **lint**：`lint-results-debug.xml`（mtime 14:45）= **78 issues：Error 0 / Warning 46 / Information 32**；
  按 id：`AutoboxingStateCreation 32`、`DefaultLocale 10`、`UnusedResources 10`、`HardcodedText 8`、`GradleDependency 7`、`ModifierParameter 4`、`ObsoleteSdkInt 4`。
- **APK**：`app-debug.apk` = **113,895,828 字节 @ 14:44**（D-044 是 113,879,444 @ 13:02:18 ⇒ **+16,384 字节**）、
  `sha256 = bbe81edc011e2f56535c8222763a27ef9f8e95cb95153718922505e6841bcab0`。
  ⚠ 字节数不能单独当变更证据，必须连树指纹成对抄；而 D-044 那串 `6e263735675020c1…`（146 个源文件）的**算法没有记在案**，
  所以本轮起新读数**不可与那串比对**，只能连命令原文一起落档。命令（在工程根目录执行）：
  ```
  python -X utf8 - <<'PY'
  import hashlib,os
  from pathlib import Path
  h=hashlib.sha256(); n=0
  for p in sorted(Path('app/src').rglob('*')):
      if not p.is_file(): continue
      if 'build' in p.parts or '.git' in p.parts: continue
      h.update(p.as_posix().encode('utf-8')+b'\0'+p.read_bytes()+b'\0'); n+=1
  print('files=',n,'sha256=',h.hexdigest())
  PY
  ```
  本轮读数：**`files=153 sha256=845a088fce6bd7248b42cb6ad72f4bcbf38a1de57137976ea2c9f4ef923c863f`**（153 含 main+test 两侧源文件）。
- **包内容对照**：`tools/apk_oss_census.py` 对新 APK → **16 dex / 打包类 29,818 / 引用类型 36,637（去重 31,880）**，对 D-044 的 29,816 是 **+2**；
  `bash tools/check_oss_credits_probes.sh` 复跑 **rc=0**、`OS-d2 = 0/0/28`（矩阵↔census 零对不上）、`OS-e` 打包类 **29,818** ⇒
  **第三方类数这张表对的是当前包**（实测）。⚠ **那 +2 未归因**：dex 里 `LocationSource;` / `LocationSource$Companion;` / `LocationSource$WhenMappings;`
  是**三个**描述符而 delta 只有 2，且 13:02 那份 APK 已被覆盖、做不了逐类差集 ⇒ 只登记"增量未归因"（与 #140 同族），**不写"多出的就是这一族"**。
- **普查**：`tools/default_value_census.py` → `rows=474 / B=400 / C=74`（D-044 是 `472/398/74` ⇒ +2 行、**C 一条没动**）。
  新增两行落在 `Repo.kt:725`/`:727`（`carrierLocationSource`，生产者 `AppSendOrderRespVO.java:108`）且都被判 **B**；
  本轮另外三个读点 `Repo.kt:740`（`carrierTaskStatus` ← `AppSendOrderRespVO.java:118`）、`:746`（`carrierArrived` ← `:114`）同样归 B ⇒ **⑩⑪⑮ 没有制造新的无生产者键**。
- **真实性计数**：`check_authenticity_counts.sh` → 窄口径 9 / 宽口径 46 / `GeoMath` 27 / DemoData 文件 5 / **假步进 0**。
- **文档锚点**：`check_doc_anchors.py` 对 `CONTRACT_EVIDENCE.md` → `anchors=289 out_of_range=0 not_found=3 miss=0`
  （3 条 `not_found` 全是台账自己写的 `.../idempotent/package-info.java:8` 省略号路径，声明这句话本身也被扫成第 3 条）；
  对 `DEFAULT_VALUE_AUDIT.md` 重锚后 → `anchors=18 out_of_range=0 not_found=0 miss=0`，18 条逐条打印出的源码文本与表格结论对齐。
- **台账表格自检**：五本 FLAGS=0。
- **设备**：`bash tools/verify_device.sh --no-build` → `List of devices attached` **空** ⇒ `[STOP] … DEVICE_UNAVAILABLE`，
  新证据目录 `docs/device-evidence/20261001-145515/`（`device-check.log` / `device-probe.log` / `summary.md` 三份）。
  ⚠ 本轮直接敲 `adb` 是 `command not found`（PATH 里没有），能出读数是因为脚本自己解析 SDK 路径——别把"命令行 adb 可用"当成环境事实。
- **git**：`git rev-parse --is-inside-work-tree` → `fatal: not a git repository` ⇒ `ENVIRONMENT_BLOCKED` 维持，**没有** `git init` 造假历史。
- **release**：产物从未产出（`app/build.gradle.kts:171` HTTPS 闸门未动、未加 `-Pbuspost.allowInsecureRelease=true`、签名文件不在工程内）⇒ `BLOCKED`。

### 3 三方核对的关键结论（逐字段表在 `CONTRACT_EVIDENCE.md` §八，这里只记判定）
- 三格（车辆位置 / 上报时间 / 当前任务）在寄货与商城两侧的 VO 里**都有真实生产者**，缺的是客户端读取；
  `geoOf`（`Repo.kt:1174`）要求 `lat!=0 && lng!=0` 才建点 ⇒ `0.0` 不会被画成一个"真点"。
- `lastReportTime` 只有商城侧有（`AppProductOrderTraceRespVO.java:72` ← `ProductOrderServiceImpl.java:379`）；
  寄货 `/send/track` 侧 `PRODUCER_NOT_FOUND` ⇒ 恒"上报时间未给"。旧写法拿订单创建时间当车辆上报时刻，本轮用 `reportTimeOf`（不回落 `createTime`）收口。
- `carrierLocationSource` 值域只有 `REAL_FRESH`/`REAL_STALE`（`AppSendController.java:473-474`，阈值 `CARRIER_FRESH_MINUTES = 5` @ `:114`）⇒ `STALE` 不升格成"实时"。
- 自造别名 `positionSource` 后端与 `DemoData` 两侧 0 生产者，本轮删；`dataSource` 保留（`DemoData.kt:556` 真会写）。
- 位置来源文案收成一个映射点：`LocationSource.knownLabel`（`Models.kt:543-552`）+ `LocationSource.of`（`:554-562`），
  `BusVehicle.sourceText`（`:632`）改为委托它；委托后 `BusEtaContractTest` 13 例仍 0 红，这是"行为逐字未变"的证据。

### 4 本轮新增文件（按"任何新文件都要同步进清单"登记）
- `app/src/test/java/com/shanxiang/buspost/data/UserTrackingContractTest.kt`（182 行 / 12 例，走 MockWebServer 真信封）。
- `tools/check_doc_anchors.py`（台账 `文件:行号` 锚点的越界 / 文件缺失 / 同名多义聚合；`--strict-id` 两种指纹实测会假报 ⇒ 只当候选清单，不当判据）。
- `docs/android/CONTRACT_EVIDENCE.md` §八（新增节，非新文件）。
- `docs/device-evidence/20261001-145515/`（脚本自写的 0 台设备证据）。
- 生产源码改动只落在 3 个文件：`Models.kt`、`Repo.kt`、`TraceView.kt`（**未新增任何文件、未删除任何字段**）。
- 台账改动（同轮）：`DECISION_LOG.md` 本条 D-045、`AI_ITERATION_STATE.md` 状态板新增 ⑩⑪⑮ 块、`CONTEXT_LOCK.md` §九 新增 5 行、
  `docs/android/CONTRACT_EVIDENCE.md` §八、`docs/android/DEFAULT_VALUE_AUDIT.md` §四 18 条锚点重锚 + 一条"行号口径"注、
  `docs/android/THIRD_PARTY_RUNTIME_MATRIX.md` 第二节新增 14:44 产物对照。五本台账表格自检 FLAGS=0，五份文件 **CRLF 计数 0**（未被文本模式翻转）。

### 5 三态（分开写，不混）
- `SOURCE_COMPLETE`：⑩⑪⑮ 的读取侧与文案侧（含 §八 全表）——后端 VO 行号本轮逐条重 grep 复核过。
- `BUILD_VERIFIED`：上面第 2 节四条 gradle/用例读数（41 类 403 例 0 红、lint Error 0、APK 已产出、DV-k 132）。
- `DEVICE_VERIFIED = 0 条`：`TraceView.kt:122-143` 那两句新文案在 JVM 里从没渲染过，长什么样、会不会压坏行高都没看过；实测 0 台设备。
- `RELEASE = BLOCKED`、`GIT = ENVIRONMENT_BLOCKED`。

### 6 OPEN_DEFECT（本轮新增 D-⑩⑪⑮-1…4，详情与"为什么这轮不修"在 `CONTRACT_EVIDENCE.md` 八.1）
| 编号 | 内容 | 本轮实测 |
|---|---|---|
| D-⑩⑪⑮-1 | 演示面没种 `carrierTaskStatus`/`carrierArrivedTime` ⇒ 演示包念"后端未下发" | `grep -rn carrierTaskStatus app/src/main` = 4 命中 / 3 文件（`Models.kt:1225`、`Repo.kt:740`、`TraceView.kt:139`、`TraceView.kt:140`），`DemoData.kt` 0 处 |
| D-⑩⑪⑮-2 | 寄货域上报时间恒"未给" | `AppSendOrderRespVO.java:100-124` 无任何上报时间键 ⇒ `PRODUCER_NOT_FOUND` |
| D-⑩⑪⑮-3 | §七 指向 `Models.kt`/`Repo.kt` 的 **67 条** App 锚点只做到"范围合法"，未逐条内容重锚 | `check_doc_anchors.py` 计数 Models 44 + Repo 23；确证漂移 3 条：`Models.kt:742/753/757` → 现 `:761/772/776` |
| D-⑩⑪⑮-4 | 新契约面只有用例在钉，**无 `tools/check_*.sh` 锚点簿** | `check_location_probes.sh` 的 L-a…L-h 钉的是换算与上报报文，覆盖不到读取侧；扩 L-i…L-k 须同轮在 `navigation_negative_controls.sh` 加破口 ⇒ 排 ⑱ |

