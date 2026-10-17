# ANDROID_PARITY_AUDIT_V2 —— 小程序 → Android → 后端 三方对齐审计（V2）

生成时间：2026-09-29 ｜ 审计方式：代码级三方交叉核对（非真机、非抓包回放）
所有 `文件:行` 均在生成当日重新打开确认；后端行号以本轮修改后的工作树为准。

> **行号时效（续修之后）**：本文档写作后又推进了 P1-1 / P1-1b（§44、§62、§32、§63 已修），
> 被改动文件的行号会漂移 —— 例如 `WorkbenchScreen.kt` 的发车 `:190→:181`、到站 `:236→:234`、收班 `:263→:261`，
> `Repo.kt` 的 `depart :941→:945`、`reportLocation :966→:970`。矩阵行已按实测改到新行号。
> 另有一处**写作当时就不准**的引用（§F 的"下一站距离"原写 `Models.kt:863`，该行其实是轨迹状态注释，
> `nextStopRoadDistanceMeters` 在 `Models.kt:1928`），本轮一并改正 —— 记下来是为了说明：
> 行号会错，**判据不能只有行号**，读本文档时用函数名/grep 定位，不要按行号硬找。
> 各条目"当前行为"里引用的旧行号保留不动（它们是定案当时的事实）。
>
> **2026-09-29（P1-2 ⑥ 之后）再漂移一次**：`Repo.kt` 头部新增 `DriverUnboundException` / `DRIVER_UNBOUND` /
> `driverIdParam()`，正文整体下移约 14 行 —— `depart :945→:975`、`arrive :947→:977`、`reportLocation :970→:1000`、
> `legAction :985→:1014`、`driverProfile→:778`、`bindDriver→:178`，`driverIdParam()` 自身在 `:772`。
> 这已经是本文档第二次遇到"行号白名单自己漂了"（§55 C 的 `Repo\.kt:2[45][0-9]` 同因失效），
> 所以新增的自检一律用**内容/函数名**判据，不再用行号判据。

---

## 1. 审计目的

回答一个问题：**Android App 的每一个界面，显示的是不是后端真实业务数据，走的是不是小程序同一条业务闭环？**

不是"Android 好不好看"，也不是"功能列表齐不齐"，而是三件事：

1. **契约真值**：Android 读的字段名/单位/参数位置，后端是否真的下发；
2. **状态真值**：Android 显示的"已完成/已交接/已取消"，是不是服务端权威状态的回读结果；
3. **几何与位置真值**：地图上画的线、卡片上的距离，是不是真实路网与真实坐标（`CONTEXT_LOCK.md` 硬规则）。

凡"UI 能点、有反馈、但后端没有这条链路 / 返回值被丢弃 / 本地自己推进状态"，一律单列，不记为完成。

---

## 2. 判定口径（标签定义）

| 标签 | 含义 | 判定条件 |
|---|---|---|
| `REAL_COMPLETE` | 真实完整 | 端点存在、字段对得上、状态来自服务端回读、几何/坐标来源可信 |
| `REAL_PARTIAL` | 真实但不完整 | 主链路真，但少参数/少字段/少边界态（不影响正确性，影响可用性） |
| `CONTRACT_MISMATCH` | 契约不一致 | 字段名、单位、参数位置（query vs body）、枚举取值与后端不一致 → 静默变 0/空/错标签，或直接 400 |
| `UI_ONLY` | 仅界面 | 界面有交互与反馈，但没有服务端读回，状态是本机推出来的 |
| `LOCAL_ONLY` | 仅本机 | 数据只在内存/Prefs 里变化，刷新即回到服务端真相 |
| `DEMO_ONLY` | 仅演示 | 由 `DemoData` 生成，只在 `BuildConfig.DEMO_ALLOWED=true` 的构建里可能出现 |
| `MISSING` | 三方皆缺 | 小程序有/后端有/Android 无，或整条能力在任何一层都不存在 |
| `BLOCKED` / `ENVIRONMENT_BLOCKED` | 受阻 | 受 key、SDK 依赖、真机、后端数据等环境条件限制，本轮无法验证或无法实现 |

**不许用"需要优化"充当结论**：§17 起每一条 `REAL_PARTIAL / CONTRACT_MISMATCH / UI_ONLY / LOCAL_ONLY / DEMO_ONLY / MISSING / BLOCKED`
都给出 文件 / 类 / 函数 / 当前行为 / 后端真实行为 / 小程序行为 / 问题 / 修改方案 / 测试方案 九个字段。

---

## 3. 基线与范围

| 端 | 位置 | 本轮核对范围 |
|---|---|---|
| 小程序（参考 UX 与 API 契约） | `cargo-post-miniprogram/` | `utils/api.js` 69 个函数、`pages/send`、`pages/orders`、`pages/bus`、`pages/driver/{routes,workbench,handover}`、`pages/parcel` |
| Android | `cargo-post-android/app/src/main/java/com/shanxiang/buspost/` | `core/net/Api.kt`、`core/loc/Locator.kt`、`core/map/*`、`data/repo/Repo.kt`(约 60 个网络函数)、`data/model/Models.kt`、`ui/user/*`、`ui/driver/*`、`ui/common/*` |
| 后端（唯一真实业务后端） | `cargo-post-platform/`（GitHub `cgp-team/cargo-post-platform`，yudao 多模块） | `yudao-module-transport` 的 `controller/app/**`、`service/**`、`enums/**`；`yudao-module-member`、`yudao-module-infra` 的 app 控制器 |

后端不可替换：不重建 API、不改端口（48080）、不改 `/app-api` 前缀，Android 只允许向后端对齐。

---

## 4. 使用的硬规则（判违规的依据）

摘自 `CONTEXT_LOCK.md`（PROJECT HARD RULES，此处只列与本审计直接相关的 8 条）：

1. 正式 App 禁止任何：直线路线 / 两点连线 / Euclidean 驾驶距离 / Mock polyline / Fake GPS / Fake Lane / Fake turn；
2. 绝对不能让手机正式包使用 `127.0.0.1` / `localhost`；
3. 调度决定去哪里，导航决定怎么走 —— 两者不得混在一个数据源里；
4. 禁止 GPS 自动完成业务（到站/交接/妥投必须由服务端状态机推进）；
5. 宁可没有路线，不允许假路线；宁可暂时不能导航，不允许假导航；
6. 不要为了"兼容旧接口"把 euclidean 转换成 OSM —— 必须真实来源；
7. 不要删除字段以免破坏旧兼容（加字段可以，改名/删字段必须三方同步）；
8. SDK 当前 API 能力不确定时，必须先查当前依赖的 API，不能自行编造类/方法名。

---

## 5. 结论摘要

按**详细条目**（§17–§52 共 36 条，加 P1-1 前置的新条目 §62、P1-1b 前置的新条目 §63、P1-2 ⑥ 前置的新条目 §64、
P1-3 ⑦ 前置的新条目 §65 = 40 条）统计；矩阵表的行更细（一个能力一行，多行可指向同一条目），所以行数不等于条目数。

| 标签 | 条目数 | 条目 |
|---|---|---|
| `REAL_COMPLETE` | 41 | 见 §53 清单（含本轮修复的公交轨迹截断、来源白名单五处同步、§50 安全复核） |
| `CONTRACT_MISMATCH` | 15 | §17 §18 §22 §23 §25 §28 §34 §35 §36 §38 §39 §52 §62 §63 **§65** —— **最危险的一类**：不报错，静默显示 0 / 空 / 错标签。其中 §17/§65（⑦）、§22/§23/§52（P2-1）**已修**，余下 11 条待办 |
| `REAL_PARTIAL` | 9 | §19 §20 §31 §32 §33 §37 §43 §45 §51 |
| `UI_ONLY` / `LOCAL_ONLY` | 5 | §24 §26 §27 §29 §64 —— 司机端状态推进与"本机角色/路由 vs 后端业务域"不一致集中在这 |
| `DEMO_ONLY` | 4 | §30 §46 §47（§24 同时兼有）—— 车道引导、线路弯曲几何、演示回执与横幅 |
| `MISSING` | 5 | §21 §40 §41 §42 §44 —— 段异常上报链路、`/send/page` 状态过滤、寄货取消/退款/评价、`BusMap` 未接线 |
| `BLOCKED` / `ENVIRONMENT_BLOCKED` | 11 | §57 —— 导航 SDK 依赖、逐转向契约、真机 GPS、AMAP key、后端空数据、`mvn`、GraphHopper bbox、release 签名、debug APK 产物、分域改动的真机四条回归（⑥ 新增）、**无 Robolectric 导致的定位/上报真机三条回归（⑦ 新增）** |

**审计之后已动手修掉的条目（判据一律见各条"本轮状态"小节，没有实测输出的不算修）**：
§44（`BusMap` 已接线，真机渲染仍 `ENVIRONMENT_BLOCKED`）、§62（Android 侧字段补齐 + 契约断言；后端是否真下发待联调）、
§32（GPS 门槛删除 + 道路距离优先展示）、§63（旧直线字段退出 UI 读点）、
**§29 + §64（P1-2 ⑥ 分域复核：`driverId` 只能来自后端档案、角色切换先过 `bindDriver`、司机通知与深链留在司机域）**、
**§17 + §45 + §65（P1-3 ⑦ 定位与上报：换算收到唯一出口并实测 480.6 m、上报改合并语义并写明丢点、`shiftId` 不再硬拦、
BD-09 往返缺陷修好、折线渲染层新增 GCJ-02 出口。§45 的"待发队列"是**有意不做**，代价记在 D-019，不算已解决的能力）**、
**§22 + §23 + §52 + §7（P2-1，2026-09-29 标量信封与交接照片闭环：`Payload` 新增标量通道并按类型填值、
未读角标收进 `AppState` 唯一写入口并按 max 合并、交接凭证照片"缺 URL 不提交 + 回读有照片才说成功"、
连带修掉 `HandoverTask.status` 读数字导致的"已完成交接永远留在待办"。真机端到端仍 `ENVIRONMENT_BLOCKED`）**。
其余条目**状态不变**，尤其 §24 仍是 6 处假步进命中。

**本轮新发现、暂只登记不动后端的两条**（都是"注释/字段名与真实契约不符"，属 §49 那类命名漂移的样本，不是新故障）：
① `NotifyMsg.orderNo`（`Models.kt`）是**幻字段** —— `AppNotificationRespVO` 与 `TransportUserNotificationDO` 都只有 `orderId`，
没有 `orderNo`，所以 `AlertEngine` 里 `"trace/${a.orderNo}"` 这类深链在真机上恒为空；小程序在缺 `orderNo` 时是回落到
`trace?id=<orderId>` 的。修法要连带决定"轨迹详情页按 orderNo 还是 orderId 寻址"，涉及后端查询参数语义，故单独排期（不夹在信封修复里做）。
② `AppDriverHandoverRespVO:52` 的 `@Schema(example)` 写"0待确认 1已确认 2有争议"，与 `TransportHandoverStatusEnum`(0..7) 不符 ——
以后端**枚举**为准（`statusName` 才是给人读的那个），注释错误只在 Android 侧记账，不改后端注释以外的任何行为。

另有：§48（参考实现自身违规的"不复刻"决策 9 项）、§49（漂移根因与契约守卫建议）、§50（安全复核通过 7 项 + 待办 2 项）。

**Top 8（按"用户会被误导的程度"排序）**：

1. **§17 坐标系**（**已于 P1-3 ⑦ 修掉**，此项保留是为了说明它当时为什么排第一）：`Locator` 把系统 `LocationManager` 的 **WGS-84** 原样发给所有 GCJ-02 接口，而工程里已经写好的 `CoordinateTransform` **零生产调用点** → 全 App 的定位类请求系统性偏移约 150–700 m（重庆主城，⑦ 实测 **480.6 m**）。
2. **§22 标量信封**（**已于 P2-1 修掉**，保留原文说明它为什么排第二）：后端 `CommonResult<Long>` / `CommonResult<String>` 被 `Api.toPayload` 包成 `{"v": …}`，而 `Repo` 读的是 `count|unreadCount` / `url|fileUrl` → **未读角标永远 0、上传后的图片地址永远空**。
3. **§24 假导航**：`DriverNavScreen` 仍用 `stepIndex++ / stepping / delay(4000)` 自动播转向；工程依赖里 **根本没有高德导航 SDK**（只有 `com.amap.api:3dmap:10.0.600`）→ 属硬规则 5 违规，且修复受依赖阻塞（§57）。
4. **§26/§27/§28 司机状态自推**：`RoutesScreen`/`HandoverScreen` 在 ack 之后本地改写状态（甚至丢弃 ack），把"段序号"当"段状态"，`status >= 4` 判完成 → 与 `TransportLegStatusEnum`（0..11 + 99）完全不符。
5. **§31 取消提示反向失真**：商城单的取消路径经复核是**合规**的（`PUT /product-order/cancel?id=` 后回读后端），但 `OrderDetailScreen:101` 固定显示"已在演示模式下取消，真实环境需后端确认"——在真实构建里把成功说成演示，用户会重复取消或打电话核实。寄货单则是三方都没有取消入口（§41）。
6. **§34 寄货可达性契约**：Android 读 `nearestStation/distance/tip`，后端下发 `recommendedStation{name,…}/distanceKm(公里)/message` → **站名空 + 单位错**；小程序读的是后端真字段。
7. **§36 运输段字段全空**：`TransportLeg.parse` 读 `seq/fromStation/toStation/plate`，后端下发 `legSequence/fromStationName/toStationName/plateNo` → 司机段列表显示成 `→`、无车牌。
8. **§39 参数位置错**：`/send/confirm-station-action` 是 `@RequestParam`，Android 把 `orderId` 放 JSON body → **必定 400**；`WorkbenchScreen:263` 把 `"shift-$id"` 当 `legId` 发给 `@NotNull Long` → **必定 400**。

> 本轮同时修掉并验证的后端违规（§54）：公交线路"道路轨迹"里的两站直线补点。

---

## 6. 矩阵读法

后面 8 张矩阵表按业务域组织。四列含义：

- **小程序**：参考实现的实际行为（含它自己的违规，标注出来，**不作为 Android 的对齐目标**）；
- **Android**：当前实现的真实行为（读代码，不读注释）；
- **后端**：唯一真值（端点 + 字段 + 状态机）；
- **判定**：§2 的标签 + 详细条目编号（`→§n`）。

---

## 7. 矩阵 A：用户端 · 寄货（send）

| 能力 | 小程序 | Android | 后端 | 判定 |
|---|---|---|---|---|
| 站点列表 | `api.js:269 listSendStations` | `Repo.kt:463 sendStations` | `AppSendController.java:482 GET /send/stations` | `REAL_COMPLETE` |
| 可达性评估 | `send.js:452-456` 读 `reachable/recommendedStation/serviceMode`；`send.wxml:182` 读 `distanceKm/walkMinutes/distanceProvider` | `Repo.kt:481-496` 读 `nearestStation/distance/tip` | `AppSendController.java:517-522` + `AppSendReachabilityRespVO:20-56`（`reachable/reasonCode/serviceMode/distanceKm/walkMinutes/distanceProvider/recommendedStation/message`） | `CONTRACT_MISMATCH` →§34 |
| 路线预估 | `api.js:282 previewSendRoute` | `Repo.kt:499-508` 读 `distanceKm/durationMinutes/provider` | `POST /send/route-preview` → `RoutePreviewRespVO` | `REAL_COMPLETE` |
| 费用试算 | `api.js:290 quoteSendFee` | `Repo.kt:471` | `POST /send/quote`（`AppSendQuoteRespVO`） | `REAL_COMPLETE` |
| 下单 | `api.js:254 createSendOrder` | `Repo.kt:432-433` | `POST /send/create`（`@Valid @RequestBody`） | `REAL_COMPLETE` |
| 我的寄货分页 | `orders.js:164` **不传 status** | `Repo.kt:425-426` **传 status** | `AppSendController.java:134-136 page(@Valid PageParam)` 只收分页 | `MISSING`（后端缺筛选参数）→§40 |
| 包裹追踪 | `api.js:264 trackParcel` | `Repo.kt:449-450` | `GET /send/track?no=` | `REAL_COMPLETE` |
| 多段 legs | `api.js:449 getParcelLegs` | `Repo.kt:456-457` | `GET /send/legs?no=`（`AppSendLegRespVO:176` 有 `statusName`） | `REAL_PARTIAL` →§36 |
| 联运拓扑 | `api.js:454 getParcelTopology` | `Repo.kt:588-589` + `traceOf` 统一解析 | `GET /send/topology?no=` | `REAL_COMPLETE` |
| 我的安排 | `api.js:295 getMyArrangements` | `Repo.kt:514-515` | `GET /send/arrangements` | `REAL_COMPLETE` |
| 站点交接确认 | `api.js:300` **query** `?orderId=` | `Repo.kt:521-522` **JSON body** | `AppSendController.java:127-129 @RequestParam("orderId")` | `CONTRACT_MISMATCH`（必 400）→§39 |
| 取消 / 退款 / 评价寄货单 | 无 | 无入口（`grep` 无 `/send/cancel` 调用） | 无端点 | `MISSING` →§41（商城单取消是另一回事，见 §31） |

---

## 8. 矩阵 B：用户端 · 实时公交（bus）

| 能力 | 小程序 | Android | 后端 | 判定 |
|---|---|---|---|---|
| 线路列表（附近） | `api.js:386 getRealtimeBusLines(lat,lng,radius)` | `Repo.kt:608 busLines(geo,radius)` | `AppBusController.java:38-46 GET /bus/lines` | `REAL_COMPLETE` |
| 附近站点/车辆（分层） | `api.js:398` 传 `district` 兜底 | `BusScreen.kt:130`、`BusPostRoot.kt:98`、`HomeScreen.kt:168` **均未传 district** | `AppBusController.java:56-65` 支持 `district`，`AppBusServiceImpl.java:257` 返回 `locationLevel=DISTRICT` | `REAL_PARTIAL` →§37 |
| 线路道路轨迹 | `api.js:391 getBusLinePolyline` | `Repo.kt:667-671 linePolyline`（空→地图显示不可用） | `GET /bus/line-polyline`，本轮改为"断线即截断，绝不补直线" | `REAL_COMPLETE`（本轮修复）→§54 |
| 车辆卡片字段 | `bus/index.js` 读 `plateNo/busId/etaMinutes/distanceToNextStationKm` | `Models.kt:521-539`（`plateNo` **未读**、`busId` 只当 shiftCode 兜底） | `AppBusRespVO.java:11-62`（`busId/plateNo/shiftCode/etaMinutes/etaToNextStationMinutes/distanceToNextStationKm/speedKmh/dataSource`） | `CONTRACT_MISMATCH` →§35 |
| 站点距离单位 | `bus` 页按 `distanceKm` 显示 | `Models.kt:567 distanceMeters = multiNum("distance","meters")` | `AppBusNearbyRespVO.NearbyStation.distanceKm` | `CONTRACT_MISMATCH`（键名+单位）→§35 |
| ETA 为空时的显示 | 显示"—" | `Models.kt:524` `etaMinutes` 默认 0 → UI 出现"0 分钟" | `AppBusServiceImpl.fillEta` 不可用时**故意留 null**（`:772`） | `CONTRACT_MISMATCH` →§38 |
| 15 s 静默轮询 | `pages/bus/index.js:113-127` | `BusScreen.kt:249-254` `LaunchedEffect` + `delay(15_000)`，失败保留旧数据 | — | `REAL_COMPLETE` |
| 定位状态呈现 | `pages/bus/index.js:254` `district ? 根据X区展示 : 定位不可用`；`utils/location.js` 另有 `poorAccuracy` | `BusScreen.kt:122-123` 用 `Locator.once()` 把三种状态折叠成 `me == null`；只有 `HomeScreen.kt:111/622` 分四态 | 后端用 `located`/`locationLevel`(PRECISE/DISTRICT/UNKNOWN) 明示口径 | `REAL_PARTIAL` →§51 |
| 真地图组件 | 原生 `map` | `ui/common/BusMap.kt:99`（唯一真地图实现，**P1-1 已由 `DriverMissionMap` 接线**；真机渲染待验证） | — | `MISSING`→ 已接线（§44、§57 `ENVIRONMENT_BLOCKED`） |

---

## 9. 矩阵 C：用户端 · 商城（product / product-order）

| 能力 | 小程序 | Android | 后端 | 判定 |
|---|---|---|---|---|
| 商品列表/分页/详情 | `api.js:220/225/230` | `Repo.kt:356/372/379` | `/product/list`、`/product/page`、`/product/get` | `REAL_COMPLETE` |
| 下单 | `api.js:237` | `Repo.kt:384-401` | `POST /product-order/create` | `REAL_COMPLETE` |
| 我的订单分页（含 status） | `orders.js:104-107` 传 `status` | `Repo.kt:651-652` 传 `status` | `AppProductOrderController.java:48` 支持 status | `REAL_COMPLETE` |
| 取消商城订单 | `api.js:247` `?id=` query | `Repo.kt:413-414`（`Api.put(path, body, query)`，id 在 query） | `AppProductOrderController.java:58-61 @RequestParam("id")` | `REAL_COMPLETE` |
| 订单溯源 | `api.js:213` | `Repo.kt:416-417` | `GET /product-order/trace?id=` | `REAL_COMPLETE` |
| 状态文案 | 优先后端 `statusName`（`parcel.js:617`） | `Models.kt:860` 优先 `statusName`，兜底 `productStatusLabel`：1→"配送中" | `ProductOrderStatusEnum:14-20`：1 = **已发货** | `REAL_PARTIAL`（兜底文案漂移）→§33 |
| 取消态判定 | — | `Models.kt:862 cancelled = status == 3` | 3 = CANCELLED ✔ | `REAL_COMPLETE` |

---

## 10. 矩阵 D：消息中心 / 通知

| 能力 | 小程序 | Android | 后端 | 判定 |
|---|---|---|---|---|
| 分页 | `api.js:412` | `Repo.kt:675-676` | `GET /notification/page` | `REAL_COMPLETE` |
| 未读数 | `api.js:417` 直接把 `res` 当数字用 | `Repo.kt:682-683` `obj.multiNum("count","unreadCount")` | `AppNotificationController.java:44-48 CommonResult<Long>`（**裸数字**） | `CONTRACT_MISMATCH`（角标恒 0）→§22 |
| 标记已读 | `api.js:422` `?id=` | `Repo.kt:685`（query ✔） | `@PutMapping("/read") @RequestParam("id")` | `REAL_COMPLETE` |
| 全部已读 | `api.js:427` body `{orderId}` | `Repo.kt:687-688` query `orderId`（可选） | `:58-61 @RequestParam(required=false)` | `REAL_COMPLETE` |
| 司机消息 / 未读 / 已读 | `api.js:471/476/481` | `Repo.kt:1017-1032`（⑥ 后行号，原 `:992-1007`；三处都改走 `driverIdParam()`） | `AppDriverController.java:226-240` | `REAL_PARTIAL`（未读裸数字同 §22）→§23 |
| 角标状态写入 | 单一来源（`unread-count`） | **2026-10-18 按现状重写**：`AppState.unread` 只有 `reportUserUnread`(`AppState.kt:223`) 一个写入函数，调用点 `AlertEngine.kt:40`（列表+标量）、`ParcelScreen.kt:100`（**只给标量**）、`NotificationScreen.kt:109`（列表+标量）；发现时的形态是"三处各写各的、谁最后写谁赢" | `/notification/page` 的 `readStatus` + `/notification/unread-count` 裸 Long | 已收口，剩 `ParcelScreen.kt:100` 依赖裸标量 →§52 |

---

## 11. 矩阵 E：我的 / 地址 / 反馈 / 会话

| 能力 | 小程序 | Android | 后端 | 判定 |
|---|---|---|---|---|
| 登录（密码/短信） | `api.js:125/140` | `Repo.kt:97-105` | `/member/auth/login`、`/sms-login` | `REAL_COMPLETE` |
| 微信小程序登录 | `api.js:135 wechatMiniAppLogin` | 无（Android 不适用） | `/member/auth/social-login` | `MISSING`（合理缺省）→§42 |
| 会话保存 | token 存 storage | `Repo.kt:117-140` 只存 `accessToken`，**丢弃 `refreshToken`/`expiresTime`** | 登录返回 `AppAuthLoginRespVO(refreshToken, expiresTime)` | `REAL_PARTIAL` →§19 |
| 401 处理 | `api.js:14 handle401` 重登并重放队列 | `Api.kt` 抛 `UnauthorizedException` → `AppState.kickToLogin()`，**无刷新重试** | `member/auth/refresh-token` 存在 | `REAL_PARTIAL` →§20 |
| 用户资料补全 | `api.js:152` | `Repo.kt:143-145 refreshUser` | `GET /member/user/get` | `REAL_COMPLETE` |
| 地址 CRUD | `api.js:169-185` | `Repo.kt:692-710` | `AppAddressController.java:32-48`（delete 为 `@RequestParam("id")`，Android 走 query ✔） | `REAL_COMPLETE` |
| 区域树 | `api.js:189` | `Repo.kt:713-714` | `/system/area/tree` | `REAL_COMPLETE` |
| 修改密码 | `api.js:162` | `Repo.kt:727-728` | `PUT /member/user/update-password` | `REAL_COMPLETE` |
| 意见反馈 | `api.js:203/208` | `Repo.kt:730-738` | `/transport/feedback/create`、`/page` | `REAL_COMPLETE` |
| 公告 | `api.js:196` | `Repo.kt:198-199` | `/transport/notice/list` | `REAL_COMPLETE` |
| 文件上传 | `api.js:486-492`（`res` 即 URL 字符串） | `Repo.kt:1012-1014` 读 `url/fileUrl/data` | `AppFileController.java:34-38 CommonResult<String>` | `CONTRACT_MISMATCH`（URL 恒空）→§22 |
| 司机模式切换 | `mine.js:79-82` 只 `requireLogin` 后 `reLaunch` 到司机工作台；真正的门在页面里：`workbench.js:163-168` profile 为空即 toast"未找到司机档案，请联系管理员"且**不发任何司机请求**，`:147/603/669/799` 每个动作前 `if (!this.driverId) return` | `MineScreen.kt:163` → `MineViewModel.enterDriverMode`(`:85-97`) 先 `Repo.bindDriver()`，未授予 driverId 不进司机域并显示"未绑定司机档案…"；`Repo.driverIdParam()`(`Repo.kt:764`) 是 19 个司机调用点的唯一 driverId 入口，未绑定直接抛（`driverId=0` 的 URL 构造不出来） | 司机端所有按 id 的接口 `@RequestParam driverId` 且 `requireCurrentDriver` 做归属校验（`DriverAppService.java:91-96`） | `REAL_COMPLETE`（**⑥ 前为 `LOCAL_ONLY`**；真机四条仍 `ENVIRONMENT_BLOCKED`）→§29、§64 |
| 「用户版」退出 / 通知落地页 / Intent 深链 | `driver-tab-bar/index.js:41-44` 用 `switchTab` 回用户 tab（页面路径即域，没有本机角色变量）；`notification.js:184-188` 司机模式一律 `navigateBack()`，注释写明"司机消息里的 orderId 是货运单 id，没有对应的村民端页面"；小程序无 Intent 深链 | `DriverShell.kt:38` 退出回写 `Role.USER`（否则 `AlertEngine` 继续轮司机域）；`AlertEngine.kt:54` 司机通知 `route = DRIVER_MESSAGES`、单号进正文；`BusPostRoot.kt:82` + `deepLinkAllowed`(`:183-184`) 按 `Routes.DRIVER_ONLY`(`:37`) 拦角色不符的深链 | 角色在后端不存在（同一会员既可能是村民也可能是司机，域由 token + driverId 决定） | `REAL_COMPLETE`（⑥ 修；**此前没有任何矩阵行抓到这三处串域**）→§64 |

---

## 12. 矩阵 F：司机端 · 工作台（workbench）

| 能力 | 小程序 | Android | 后端 | 判定 |
|---|---|---|---|---|
| 档案/班次/取件/任务/收益 | `api.js:308-328` | `Repo.kt:744-779` | `AppDriverController.java:41-66` | `REAL_COMPLETE` |
| 发车 | `workbench.js:572` ack 后才 `setData` | `Repo.kt:937` → `WorkbenchScreen.kt:181 act("发车")` | `POST /driver/depart`（`@RequestBody`） | `REAL_COMPLETE` |
| 到站 | `workbench.js:815-819` ack 必需；随后 `:823` 本地 `points[ti].reached = true` | `WorkbenchScreen.kt:234 Repo.arrive(id, sid)` | `POST /driver/arrive` | `REAL_COMPLETE`（Android 未复刻小程序的本地 reached 标记，这是**正确的**） |
| 下一站距离 | `workbench.js:24-31 + :759` **客户端 Haversine 米** | 用后端 `nextStopRoadDistanceMeters` / `roadDistanceMeters`（`Models.kt:1180`）；导航 HUD 曾读旧直线字段，本轮已改（§63） | `AppDriverRouteRespVO:76-83`（直线 + 道路两套距离都下发） | `REAL_COMPLETE`（Android 比小程序更合规） |
| 任务点属性（序号/动作/件数/状态） | `workbench.js:218-248` 读 `visitSequence/actionName/quantity/statusName`，按 `planId` 分组同站聚合 | `Repo.kt:858` 把 `stops` 塞进 `Station.parse` → 已改为 `MissionStop` 读全 9 个字段，`RouteGuide.stops` + 工作台时间线消费（P1-1b） | `AppDriverRouteRespVO.java:90-112` + `DriverAppServiceImpl.java:487-503`（`status` 是 `TaskItemStatusEnum` 0..8） | `CONTRACT_MISMATCH`→ Android 侧已修，待后端端到端（§62） |
| 装车 / 妥投 | `workbench.js:882-909` | `WorkbenchScreen.kt:321-357` | `/driver/product-load`、`/pickup-confirm`、`/deliver`、`/product-deliver` | `REAL_COMPLETE` |
| 取件核销 | `api.js:405` | `Repo.kt:944-945` | `POST /driver/pickup-verify` | `REAL_COMPLETE` |
| "完成配送"按钮 | `handover.js` 用真实 `leg.id` | `WorkbenchScreen.kt:261 Repo.legAction("complete", "shift-$id")` | `AppDriverLegActionReqVO:15-16 @NotNull Long legId` | `CONTRACT_MISMATCH`（必 400）→§39 |
| 位置上报 | `api.js:368` `{driverId,shiftId,longitude,latitude,speedKmh}`，`speedKmh` 前台恒 0 | `Repo.reportLocation(shiftId: Long?, …)` + `LocationReportPolicy`（3..60 s 退避、45 s 新鲜度闸、只发最新点、丢点计数可见）；坐标已是 GCJ-02（§17） | `POST /driver/location`；`AppDriverLocationReqVO` **无客户端时间字段**，服务端 `LocalDateTime.now()` 盖章；`shiftId` 可选（按当前活跃段兜底） | `REAL_PARTIAL`→ **Android 侧已修（⑦）** →§45。**有意不做重放队列**：带旧坐标重放＝给旧位置盖新章＝假上报（D-019）；代价＝弱网中间点不可恢复地丢失，界面明写"已丢弃 N 个未确认点" |
| 上报坐标系（设备 → 后端 / 底图） | `utils/location.js:5` 统一 `wx.getLocation({type:'gcj02'})`，页面禁止直调 | `Locator.toGcj02` 是**唯一出口**（`LocationManager` 出 WGS-84 → 转一次），实测重庆偏移 **480.6 m**；折线另有 `RealRoadRoute.gcj02Points` | 后端接口与 AMap 底图均为 GCJ-02（`AppSendReachabilityReqVO` 等明文标注） | `CONTRACT_MISMATCH`→ **已修（⑦）** →§17、§65。残留：`inChina()` 是矩形（东南亚会被误转，已用测试钉住）；OEM 网络定位若本身是 GCJ-02 会转两次 → §57 `ENVIRONMENT_BLOCKED` |
| 到站距离与"确认到达"门槛 | `workbench.js:21/759/762/775` 客户端 Haversine + 50 m 门槛 | 门槛已删（`canArrive`/`ARRIVE_RADIUS_METERS` 全工程 0 命中，实测），展示优先后端道路值，直线值必须带"不是道路里程"标注 | `AppDriverRouteRespVO:81` 已下发 `nextStopRoadDistanceMeters`；`AppDriverArriveReqVO:11-20` **不吃坐标不校验距离** | `REAL_PARTIAL`→ Android 侧已修（§32，与小程序**有意分歧**：不复刻被禁止的 GPS 门槛） |
| 导航 HUD 剩余里程 | `routes.js` 无对应（小程序只做 `wx.openLocation`） | 曾读 `nextStationDistanceMeters`（后端直线值）当"剩余"，现改读 `nextStopRoadDistanceMeters`（`DriverNavScreen.kt:244`） | `AppDriverRouteRespVO:76`（直线）与 `:81`（道路）是两套不同含义的数 | `CONTRACT_MISMATCH`→ 已修（§63，违反 `CONTEXT_LOCK.md` §四"UI 只准读 `*Road*` 系列"） |
| 写路径防重 | 页面普遍有 `submitting` 标志 | `ScreenViewModel.work`(`:35-47`) 无重入保护，`SendScreen` 无 `busy` | `/send/create` 有 `@Idempotent(10s)`，窗口外不拦 | `REAL_PARTIAL` →§43 |

---

## 13. 矩阵 G：司机端 · 运输段（legs）与交接（handover）

| 能力 | 小程序 | Android | 后端 | 判定 |
|---|---|---|---|---|
| 段列表 | `api.js:444 getDriverLegs` | `Repo.kt:818-820 driverLegs` | `GET /driver/legs` → `AppDriverLegRespVO` | `REAL_PARTIAL`（字段名不对，见 §36） |
| 当前段 | `api.js:461` | `Repo.kt:825-827` | `GET /driver/current-leg` | 同上 |
| 段动作 | `handover.js:95-104` 状态→动作映射**与枚举一致**；`:127` 提交后 `:131 await loadAll()` **整表重载** | `RoutesScreen.kt:102-104` ack 后本地 `copy(status = nextStatus(...))`；`HandoverScreen.kt:107-109` **丢弃 ack** 并用 `legStep(status)+1` 推进 | `POST /driver/leg/{accept,navigate,arrive-origin,load,start,arrive-dest,handover-start,handover-confirm,complete}`（`AppDriverController.java:163-224`，状态由服务端推进） | `UI_ONLY` →§26、§27 |
| 段状态含义 | `handover.js:95-104`：1 accept、2 navigate、4 arrive-origin、5 load、6 start、7 arrive-dest、8(+required) handover-start、8 complete、9 handover-confirm、10 complete | `RoutesScreen.kt:109-114 nextStatus` 造 2/3/4；`:174/:259 status >= 4` 判完成；`:332-340 statusLabel(0..5)`；`:343-349` 动作表 | `TransportLegStatusEnum:24-36` 0..11 + 99（4=前往起点、8=已到达终点、11=已完成） | `CONTRACT_MISMATCH`（Android 用另一套状态机）→§28 |
| 交接确认 | `handover.js:192-196`（可无照片，二次确认弹窗） | `HandoverScreen.kt:129-130` ack 后本地写 `status="已交接"` | `POST /driver/handover/confirm`（`@RequestBody`） | `UI_ONLY` →§27 |
| 段异常上报 | 无 | 无（`grep reportLegException` 无命中） | 服务层**已有** `case "exception"`（`DriverAppServiceImpl.java:1498`），但 controller 无 `/leg/exception` | `MISSING` →§21、§41 |

---

## 14. 矩阵 H：导航与地图（司机端）

| 能力 | 小程序 | Android | 后端 | 判定 |
|---|---|---|---|---|
| 起步导航 | `routes.js:160-165` `wx.openLocation`（交给系统地图） | `DriverNavScreen` 自绘转向卡 + 步进 | `AppDriverRouteRespVO` **无 steps/lanes 字段** | `MISSING`（三方都没有逐转向数据）→§24 |
| 地图线 | `routes.js:122-124` **站点直连**（违规，不作为目标） | `MapCanvas`：无可信折线 → `RouteUnavailableState`（`:67`） | `polyline + routeProvider + routeAvailable + routeComplete` | `REAL_COMPLETE`（Android 优于参考实现）→§53 |
| 实时导航引擎 | — | 无 `core/navigation`（目录不存在），无 `AMapNaviListener` 任何引用 | — | `BLOCKED` →§57 |
| 车道引导 | — | `DemoData.kt:348/358-372` 造 `lanes/recommendedLane` | — | `DEMO_ONLY` →§46 |
| 线路弯曲几何 | — | `DemoData.kt:29-46 lineOf()` 站间插两个侧向偏移点"模拟山路弯曲" | — | `DEMO_ONLY` →§46 |

---

## 15. 矩阵 I：Android 独有能力（无小程序对应）

| 能力 | 实现 | 判定 |
|---|---|---|
| 桌面小组件（下一班到站） | `Widget` + `Repo.nearbyBuses` | `REAL_PARTIAL`（§17 坐标系**已修**，仍依赖 §22 未读数） |
| 快捷设置磁贴 | `qs tile` | 同上 |
| 逆地理编码 | `Repo.kt:225 reverseGeo` | `REAL_PARTIAL` →§17 —— **坐标系部分已修**（⑦：入参坐标现由 `Locator` 出口统一转 GCJ-02，正是高德 regeo 要求的系）；`AMAP_WEB_KEY` 为空时返回 `ReverseGeo.EMPTY`，真机 key 校验仍 `ENVIRONMENT_BLOCKED` |
| 天气 | `Repo.kt:243-304`（AMAP 天气 + Open-Meteo 兜底） | `REAL_COMPLETE`（外部数据源，非本项目后端） |
| 演示横幅 `DemoNote` | `ui/common/Bits.kt:43-46` **函数体首行 `return`，横幅永不显示** | `DEMO_ONLY`/诚实性违规 →§47 |

---

## 16. 为什么"小程序行为"不等于对齐目标

小程序是参考 UX 与 API 契约来源，但它在**几何与位置真实性**上有 4 处自身违规，Android 若照抄就会违反硬规则：

1. `pages/driver/routes/routes.js:122-124`：地图线是站点直连；
2. `routes.js:186-188`：`currentStop` 缺失时用 `(now - departure)/duration × (stopCount-1)` **用墙钟猜位置**；
3. `workbench.js:24-31 + :740/:742/:759/:780`：下一站距离与进度百分比用客户端 Haversine；
4. `workbench.js:1027-1030`：`setTimeout(…, 1500)` 自行 `continueToNextStation()`，**不发任何请求**。

本审计对这些点的判定一律是"后端为准，Android 保持不复刻"。

## 17. 定位坐标系：Android 发 WGS-84，后端与小程序都是 GCJ-02

判定：`CONTRACT_MISMATCH`（P0，系统性偏移，影响 6 个端点）→ **Android 侧已修**（P1-3 ⑦，2026-09-29；
两条残留局限见"本轮状态"，端到端与 OEM 二次偏移仍是真机项）

- **文件**：`app/src/main/java/com/shanxiang/buspost/core/loc/Locator.kt`
- **类 / 函数**：`Locator.cached`(`:76-77`)、`Locator.onceFix`(`:125`、`:130`、`:135`)、`Locator.locations`(`:94-102`)；消费方 `Repo.nearbyBuses`(`:626-630`)、`Repo.busLines`(`:616-620`)、`Repo.reachability`(`:489-490`)、`Repo.reportLocation`(`:966-978`)、`Repo.reverseGeo`(`:210`)、`DriverLocationService.observe`(`:61-69`)
- **当前行为**：`Locator` 三处出口都是 `Geo(it.latitude, it.longitude)` —— 即 `android.location.LocationManager` 的原始 **WGS-84** 坐标，未经任何换算就进了查询串和 POST body。工程里已经写好完整的换算器 `core/map/CoordinateSystem.kt:36 object CoordinateTransform`（`wgs84ToGcj02` / `convertAll` / `inChina` 判定齐全），但 `grep -rn "CoordinateTransform" app/src/main/java` **除定义文件外零命中** —— 一个生产调用点都没有。
- **后端真实行为**：`AppSendReachabilityReqVO.java:11/15` 明确 `@Schema(description = "当前位置纬度(经度)(GCJ-02)")`；`/bus/nearby` 的站点集合来自高德 REAL_TRANSIT POI（GCJ-02），`AppBusServiceImpl.java:252-257` 用 Haversine 在 GCJ-02 空间里做半径过滤；`AppDriverRouteRespVO.java:59-61` 的 `currentLongitude/currentLatitude` 与 `polyline` 同为 GCJ-02，`:54-56` 的 `deviationMeters/deviated` 由服务端拿上报坐标和道路折线比对得出。
- **小程序行为**：`utils/location.js:5` 规定"业务层只认这里，页面禁止直接调 wx.getLocation"，统一 `wx.getLocation({type:'gcj02'})`；`pages/bus/index.js:9` 注释"统一 GCJ-02"；`pages/driver/workbench/workbench.js:122`"设备定位（GCJ-02）"。
- **问题**：重庆主城 WGS-84 与 GCJ-02 偏差约 **150–700 m**。后果分三层：① `reachability` 用偏移后的坐标选"最近交接站点"，可能选错站，用户按 App 提示走到错误站点；② `/bus/nearby` 半径过滤把真实最近的站过滤掉、把 500 m 外的站当最近，"距你 0.3km"实际是 0.8 km；③ 位置上报进了监控与**偏航判定**，`deviated` 会在司机完全正常的线路上被置真 → 后台告警误报，`RouteCorridorService` 的走廊匹配整段错位。这不是显示问题，是数据污染：上报的坐标进了 `transport_driver_position`，管理端看到的轨迹本身就偏。
- **修改方案**（按硬规则"业务代码不得手写偏移公式"，只允许一个入口）：
  1. `Locator` 内部完成换算，业务层继续拿 `Geo` 但拿到的一定是 GCJ-02：在 `LocFix` 构造处调 `CoordinateTransform.convert(Geo(...), CoordinateSystem.WGS84, CoordinateSystem.GCJ02)`（`inChina` 判定已经在换算器里，境外点原样返回，不会二次伤害）；
  2. `Locator.locations` 的 `collect` 出口同样换算，`DriverLocationService.observe` 因此自动得到 GCJ-02；`Running.geo`（用于地图画自车）本来就是 GCJ-02 底图，一致；
  3. **不要**在每个 `Repo.*` 里各转一次——那会重复偏移；改动只落在 `Locator` 一个类里，`CoordinateTransform` 从"死代码"变成唯一真值；
  4. 给 `Locator` 加一个 debug-only 开关（`BuildConfig.DEBUG`）打印换算前后的差值米数，用于真机核对（不得进 release 日志）；
  5. 同步在 `docs/REAL_ROAD_ROUTING_ARCHITECTURE.md` 的坐标系一节登记"设备定位唯一出口 = `Locator`，唯一换算入口 = `CoordinateTransform`"。
- **测试方案**：
  - 单测 `CoordinateTransformTest`：重庆主城固定点 `(29.5325, 106.5765)` WGS→GCJ 后与公开参考值差 < 5 m，且 `convert(convert(x, WGS→GCJ), GCJ→WGS)` 往返误差 < 1e-6；境外点（东京 35.68, 139.69）往返 **不变换**；
  - 单测 `LocatorCoordinateTest`（Robolectric 注 `Location`）：`onceFix` 返回的 `Geo` 与输入 raw 不相等且落在 GCJ 侧（经度偏移量 > 0.0004）；
  - 契约测试：`Repo.reachability` 请求体断言带上的坐标必须是换算后的（用 MockWebServer 抓 body 比对）；
  - 真机：同一位置分别在小程序与 App 发起"可达性评估"，`recommendedStation.id` 必须一致——这是唯一的端到端验收口径；
  - 回归：上报位置后管理端 `MonitoringServiceImpl` 的 `deviated` 在正常线路上应为 false。
- **本轮状态**：**已修**（P1-3 ⑦，2026-09-29）。按修改方案 1/2/3/5 逐条落地，只多出一处偏离（方案 4 的开关形态）：
  - **换算真实发生**：`CoordinateTransform` 的生产调用点从 **0 个**变成 **2 个** —— `Locator.toGcj02`(`Locator.kt:99-107`)
    与 `RealRoadRoute.gcj02Points`(`RealRoadRouteValidator.kt`，见 §65)。判据 `bash tools/check_location_probes.sh`
    的 **L-a = 2**（不是"≥1"：多于 2 就说明有人在自己那儿转第二次）。
  - **三处出口都收进一个函数**：`cachedLocation` / `onceFix` 的三个 `LocFix(...)` / `fixOf` 一律走 `gcjOf(...)`；
    `Locator` 之外没有任何 `Geo(l.latitude, l.longitude)` 构造点（**L-b = 0**，注释行按结构过滤而非按行号），
    所以 §17 列的 6 个消费方（`nearbyBuses`/`busLines`/`reachability`/`reportLocation`/`reverseGeo`/`DriverLocationService.observe`）
    是**自动**拿到 GCJ-02 的，一处都没漏 —— 这正是方案 3"改动只落在 `Locator` 一个类里"想要的形态。
  - **实测偏移量（不是估算）**：`(29.5325, 106.5765)` WGS-84 → `(29.529603, 106.580187)` GCJ-02，
    两点间 **480.6 m**，落在本条"问题"段写的 150–700 m 区间内；`CoordinateTransformTest` 用
    `往返误差 < 1e-9 度` 与 `偏移量落在 100..900 m` 两条断言把它钉住。方案里的"与公开参考值差 < 5 m"
    改为断言**区间**：本机没有可用的公开参考实现，写"差 < 5 m"就是没有依据的自述（宁可区间，不做假精确）。
  - **方案 5（跨仓文档登记）已做**：`cargo-post-platform/docs/REAL_ROAD_ROUTING_ARCHITECTURE.md` §4 的坐标系表新增一行
    "Android 客户端边界 = `Locator.toGcj02` + `CoordinateTransform` + `RealRoadRoute.gcj02Points`"，
    并补一段"Android 侧补记（P1-3 ⑦）"写明：该表原有的"司机 GPS = GCJ-02"这一行**在 ⑦ 之前对 Android 不成立**
    （是愿望不是事实），以及两条残留局限。
    顺手核对时发现文档里引用的函数名 **`is_in_china` 不存在** —— 真实名字是 `out_of_china`(`coordinate.py:29-35`)，已改；
    并把两侧判定**逐条对过**：Kotlin `inChina()` 与 Python `out_of_china` 是**同一个矩形**
    （经度 `72.004..137.8347`、纬度 `0.8293..55.8271`），所以"东南亚点被误转"是**两侧共有的局限**，不是 Android 独有 bug
    —— 这条原本是写成"未对拍"的待办，实测之后改成了事实。
  - **方案 4 的偏差**：debug 开关确实加了（`BuildConfig.DEBUG && gcj != wgs84` 才打印），但它多一个副作用 ——
    为了量出偏移米数必须调 `GeoMath.distance`，于是 `grep GeoMath` 基线 19 → **22**。
    该调用点在 `:100` 的注释里按 §55 的规则写明"量的是**两个坐标系之间的偏移量**，只用于真机核对，不是道路里程、不是远近展示"。
  - **两条残留（不得当作已解决）**：① `inChina()` 是**矩形**判定，`(15.0,105.0)`（东南亚）也在框内会被误转 ——
    已用 `境外不变换的边界与本矩形的局限` 一条用例把这个**已知局限**钉住，而不是假装它不存在；
    ② 部分 OEM 的网络定位**本身就回 GCJ-02**，无条件换算等于转两次 —— 只有真机能判，记 §57 `ENVIRONMENT_BLOCKED`。
  - **仍未做的验收项**：本条测试方案里的 Robolectric 用例（注入 `Location` 断言 `onceFix` 落 GCJ 侧）
    **没做** —— 工程无 Robolectric（§61 口径说明）。改成的替代是 `LocatorCoordinateTest` 6 例：
    测 `toGcj02` 与 `CoordinateTransform.convert` 等价、`TrackFix` 字段、`LocFix` 四态文案，
    即**纯函数层可测，Android `Location` 对象不可测**；契约测试（MockWebServer 抓 body 比对坐标）
    由 `DriverStateTest` 的报文键集合用例顶了一半（键名对，数值不比对，因为 fixture 坐标是构造的）。
    端到端"与小程序同点比对 `recommendedStation.id`"仍是真机项，未跑。

---

## 18. 货运状态文案表用了"顺序编号"，与 `TransportOrderStatusEnum` 的"枚举码"完全错位

判定：`CONTRACT_MISMATCH`（P0，把"待客户操作"显示成"已完成"）

- **文件**：`app/src/main/java/com/shanxiang/buspost/data/model/Models.kt`、`app/src/main/java/com/shanxiang/buspost/ui/user/TraceScreen.kt`
- **类 / 函数**：`freightStatusLabel`(`Models.kt:482-486`)、`freightTone`(`:393-398`)、`TraceViewModel`/`TraceScreen`(`:110-111`、`:181`)
- **当前行为**：`freightStatusLabel` 按 **0..8 顺序**给文案：`0 已创建 / 1 待审核 / 2 待客户操作 / 3 待入池 / 4 已入池 / 5 已分配 / 6 已发车 / 7 已完成 / 8,9 已取消`，且注释断言"与小程序 parcel.js STATUS_TEXT 同源"。`ParcelTrace` 本身有 `statusLabel`（`:675`，优先后端 `statusName`），但 `TraceScreen` 两处都没用它，直接 `freightStatusLabel(tr.status)`。
- **后端真实行为**：`TransportOrderStatusEnum.java:19-45` 是 **0..13 的枚举码**，其中 `1 已入池 / 2 已分配 / 3 已发车 / 4 已完成 / 5 已取消 / 6 待审核 / 7 待客户操作 / 8 待入池 / 9 部分完成 / 10 运输中 / 11 换乘中 / 12 派送中 / 13 异常`。审核前置态（6/7/8）是**插在前面**的，不是顺序号。
- **小程序行为**：`pages/parcel/parcel.js:15-25` 的 `STATUS_FLOW` 是**按枚举码 keyed** 的：`{0 已创建}{6 待审核}{7 待客户操作}{8 待入池}{1 已入池}{2 已分配}{3 已发车}{4 已完成}{5 已取消}`；`:617`/`:477`/`:260` 一律 `o.statusName || statusText(o.status)` —— **后端文案优先**；`:724` 时间轴用 `STATUS_FLOW.findIndex` 按顺序判定，注释明确"而非数值比较，兼容审核前置状态 6/7/8"。
- **问题**：Android 的表整体错位一格以上，且把注释写成了"同源"（假断言，正是审计要防的那类）。逐码对照：后端 `4 已完成` → App 显示"已入池"（用户以为货没到）；后端 `7 待客户操作` → App 显示"已完成"（**用户以为送达，实际还等他送到站点**）；后端 `3 已发车` → "待入池"；后端 `5 已取消` → "已分配"；`9..13`（部分完成/运输中/换乘中/派送中/异常）全落 `else -> "处理中"`，异常单与派送中在 App 上长得一样。`TraceScreen.kt:181` 还把错文案写进分享文本，错误会外溢到剪贴板/聊天记录。
- **修改方案**：
  1. `freightStatusLabel` 改成按**枚举码**分支，逐条对齐 `TransportOrderStatusEnum`（0..13 全覆盖），并在函数上方注释里写出枚举源文件名与"码表 keyed，不是顺序"这一句，防止后人再排一次序；
  2. `freightTone` 同步：`4 已完成`→OK，`5 已取消`/`13 异常`→危险（新增 `BusTone.DANGER` 或复用现有告警色），`10/11/12`→PRIMARY，`6/7/8`→WARN；
  3. `TraceScreen.kt:110` 改 `statusLabel = tr.statusLabel`（优先后端 `statusName`，本地表只兜底），`:181` 同样改用 `tr.statusLabel`；
  4. 不删除字段、不改协议：只改客户端映射；后端 `statusName` 已在 `AppSendOrderRespVO` 下发（`toRespVO` 里填）。
- **测试方案**：
  - 表驱动单测 `FreightStatusLabelTest`：对 0..13 **每个码**断言期望文案，尤其 `assertEquals("已完成", freightStatusLabel(4))`、`assertEquals("待客户操作", freightStatusLabel(7))`、`assertEquals("异常", freightStatusLabel(13))`；
  - 反漂移断言：单测里把 `TransportOrderStatusEnum` 的值集合硬编码进注释来源（Android 侧无法直接引用 Java 枚举，则写一个 `TRANSPORT_ORDER_STATUS_CODES = (0..13)` 常量并在测试里遍历断言 `freightStatusLabel` 返回值不等于兜底"处理中"的码 ≥ 0 个）；
  - Robolectric：mock `/send/track` 返回 `{status:7, statusName:"待客户操作"}`，断言 TraceScreen 顶部标签渲染为"待客户操作"（走 `statusName` 优先），去掉 `statusName` 后仍为"待客户操作"（走本地表）。

---

## 19. 登录成功后丢弃 `refreshToken`，会话无法续期

判定：`REAL_PARTIAL`（P1）

- **文件**：`app/src/main/java/com/shanxiang/buspost/data/repo/Repo.kt`、`app/src/main/java/com/shanxiang/buspost/data/store/Prefs.kt`
- **类 / 函数**：`Repo.saveSession`(`:117-140`)；`Prefs.refreshToken`(`:23-25`，键存在、`:154` 会被清理，但**全工程没有任何写入**)
- **当前行为**：`saveSession` 只取 `accessToken`/`token`，写入 `Api.token` + `Prefs.token`，其余 `refreshToken`/`expiresTime` 直接丢。`Account.parse` 也不保留 refreshToken。
- **后端真实行为**：`AppAuthLoginRespVO` 同时返回 `accessToken`、`refreshToken`、`expiresTime`；`AppAuthController.java:66-71` 提供 `POST /member/auth/refresh-token?refreshToken=…`（`@PermitAll`，`@RequestParam`），返回新的 `AppAuthLoginRespVO`。access token 有 TTL，过期后所有业务接口 401。
- **小程序行为**：`utils/api.js:14 handle401` 在 401 时用 refreshToken 重登并把挂起的请求排队重放。
- **问题**：会话到点必掉，用户在使用中被踢回登录页；司机在途（`DriverLocationService` 正在上报）被踢后，`clearSessionLocal()` 会连 `driverId`/`driverShiftId` 一起清掉（`Prefs.kt:151-178`），位置上报直接停摆（`DriverLocationService.kt:135` 的 `Prefs.driverId <= 0L` 分支），而前台通知只显示"当前没有在途班次"——司机不会知道是登录过期了。
- **修改方案**：① `saveSession` 增 `Prefs.refreshToken = p.str("refreshToken")`、`Prefs.tokenExpiresAt = 解析 expiresTime`；② 见 §20 的刷新与重放；③ 被踢时若 `Running.shiftId > 0`，通知文案明确"登录已过期，位置上报已暂停，请重新登录"；④ 不改后端、不加兼容字段。
- **测试方案**：单测 `SessionPersistenceTest`：mock 登录响应含 refreshToken，断言 `Prefs.refreshToken` 非空；`logout()` 后为空（已有 clearSession 用例覆盖，补 refreshToken 断言）。

---

## 20. 401 只踢人不重放：没有刷新流程，也没有请求队列

判定：`REAL_PARTIAL`（P1）

- **文件**：`app/src/main/java/com/shanxiang/buspost/core/net/Api.kt`
- **类 / 函数**：`Api.kick`(`:270-283`)、`Api.execute`(`:238-245` 的 401 分支)
- **当前行为**：`resp.code == 401 || bizCode == 401` → 认证类路径抛 `ApiException`（正确，避免把"密码错"当会话失效），其余走 `kick()`：2 秒节流后回调 `AppState.kickToLogin()` 并抛 `UnauthorizedException`。**从不尝试刷新，也从不重放原请求**。
- **后端真实行为**：同 §19，refresh-token 端点存在且免登录。
- **小程序行为**：`api.js:14` 401 → 静默重登 → 期间新请求进队列，成功后重放，用户无感。
- **问题**：一次过期 = 一次中断全部在途请求（`ParcelScreen.load` 三路并发会同时抛），页面同时挂 3 条错误；且 `Repo` 里大量 `runCatching{}`/`catch` 把 `UnauthorizedException` 当普通失败吞掉（例如 `Repo.unreadCount`(`:691`)、`Repo.nearbyBuses`(`:640`)），于是"会话失效"在 UI 上表现为"附近没有公交""暂无订单"——把认证问题伪装成空数据，是硬规则"真实空数据才是空"的反面。
- **修改方案**：① `Api` 内加 `refreshMutex`：首个 401 触发 `POST /member/auth/refresh-token`（用 §19 存的 refreshToken），成功则更新 `Api.token`/`Prefs.token` 并**重放当前请求一次**；失败才 `kick()`；② 刷新期间的并发请求挂到同一 mutex（最多 1 次刷新，避免风暴）；③ 给 `Repo` 的错误分类加一档：`UnauthorizedException` 不得渲染成 `EmptyState`，要么已由 Api 重放成功，要么页面显示"登录已过期"；④ 保持"认证接口 401 = 凭证不对，不触发刷新"这一现有正确行为。
- **测试方案**：MockWebServer 脚本化：第 1 次业务请求 401 → 断言紧接着发出 `auth/refresh-token` → 返回新 token → 断言**同一业务请求被重放且这次 200**，`Repo` 拿到正常数据、页面无错误条；refresh 也 401 → 断言 `AppState.kickToLogin()` 被调且 token 清空；并发 3 请求同时 401 → 断言只刷新 1 次（` Recorded request count`）。

---

## 21. 异常态（订单 13 / 段 99）在 App 里没有任何呈现，也没有上报入口

判定：`MISSING`（P1，与 §41 同根）

- **文件**：`app/src/main/java/com/shanxiang/buspost/ui/driver/RoutesScreen.kt`、`data/model/Models.kt`
- **类 / 函数**：`RoutesScreen.statusLabel`(`:332-339`)、`RoutesScreen.nextActionOf`(`:343-349`)、`TransportLeg.estimated`(`Models.kt:763`)
- **当前行为**：状态标签表只有 0..5，`99` 落到 `else -> "—"`；动作表 `leg.status <= 1 → accept`、`==2 → arrive-origin`、`==3 → arrive-dest`、`==5 → accept`、`else → complete`，所以 **99（异常）的段照样给司机一个"完成"按钮**；全工程 `grep reportLegException` / `"exception"` 在 Android 侧零命中。
- **后端真实行为**：`TransportLegStatusEnum.java:36 EXCEPTION(99, "异常")`；`DriverAppServiceImpl.java:1529` 已实现 `case "exception" -> reportLegException(leg, reqVO.getRemark())`，`:1505-1516` 会把段置异常、订单置 `TransportOrderStatusEnum.EXCEPTION(13)`、记录 `ORDER_EXCEPTION` 事件并向**管理端和货主双向推通知**；`MultiLegServiceImpl.java:391` 把异常态当终态对待。但 `AppDriverController.java:163-224` 的 9 个 `/leg/{action}` 端点里**没有 `/leg/exception`**，能力到不了任何客户端。
- **小程序行为**：`handover.js:95-104` 的动作映射同样不含 exception，也没有上报入口——小程序也不完整，但这不构成 Android 可以省略的理由。
- **问题**：需求里"车辆故障/道路中断上报"（后端注释标 §108）整条链路只缺最后一公里：服务层就绪、缺 controller 与 App 入口。司机遇到异常只能干等着，或者错误地点"完成"（后端 `advanceLegStatus`/`completeLeg` 会拒绝非法推进，结果是点了没反应的 409/业务错）。同时订单 13 在用户侧显示"处理中"，货主不知道自己该改约。
- **修改方案**：① 后端补 `@PostMapping("/leg/exception")` → `driverAppService.legAction("exception", reqVO)`（两行，复用已有实现与 `@Valid AppDriverLegActionReqVO`，`remark` 字段已存在）；② Android `RoutesScreen` 动作表改为**从后端状态推导**（见 §26/§28），新增"上报异常"入口（填 remark，提交后 `Repo.driverLegs()` 整表重载，绝不在本机写 99）；③ 状态文案表补 6..11 与 99，`99` 用危险色并把"完成"按钮换成"上报异常/等待重调度"；④ `SendOrder`/`ParcelTrace` 对 `status == 13` 给专门的异常卡片文案（复用 §18 的表）。
- **测试方案**：后端：`AppDriverControllerTest`（或现有 `DriverAppServiceImplTest` 扩一条）——`POST /driver/leg/exception {legId, remark}` 后断言段状态 = 99、订单状态 = 13、`TransportOrderEventDO` 有 `ORDER_EXCEPTION`、`sendToAdmin`/`sendToOrderUser` 各 1 次（Mockito verify）；Android：Robolectric 断言 status=99 的段渲染出"异常"且不存在"完成"按钮，点击"上报异常"发出的请求路径与 body 精确匹配。

---

## 22. `CommonResult` 的标量 `data` 被包成 `{"v": …}`，`Repo` 读不到 → 未读角标恒 0、上传 URL 恒空

判定：`CONTRACT_MISMATCH`（P0，静默失效，最难发现的一类）

- **文件**：`app/src/main/java/com/shanxiang/buspost/core/net/Api.kt`、`data/repo/Repo.kt`
- **类 / 函数**：`Api.toPayload`(`:356-360`，`else -> Payload(JSONObject().put("v", data), null, demo)`)；受害调用点 `Repo.unreadCount`(`:690-691` `obj?.multiNum("count","unreadCount")`)、`Repo.uploadPhoto`(`:1020-1022` `obj?.multiStr("url","fileUrl","data")`)、`Repo.saveAddress`(`:715` 同类读法，无害但同根)、`Repo.kt:1039`（司机未读，见 §23）
- **当前行为**：OkHttp 拿到 `{"code":0,"data":12}` 时，`root.opt("data")` 是一个 **JSON number/boolean/String**（不是对象也不是数组），`toPayload` 把它塞进 `{"v":12}`。而 `Payload.str/multiStr/multiNum` 只认业务键名，不认 `v` → 一律返回默认值。
- **后端真实行为**：`AppNotificationController.java:44-48` 是 `CommonResult<Long> unreadCount()`（裸数字）；`AppFileController.java:34-38` 是 `CommonResult<String> uploadFile(...)`（**裸 URL 字符串**）；`AppDriverController.java:239-243` 是 `CommonResult<Long> messagesUnreadCount`。这些都是 yudao `CommonResult<T>` 的正常形态。
- **小程序行为**：`api.js:417`/`api.js:486-492` 直接把 `res` 当数字 / 当 URL 字符串用（小程序封装里 `resolve(res.data)` 不做对象假设），所以它一直是对的。
- **问题**：① 未读角标**永远 0**：`AppState.unread`（`ParcelScreen.kt:100`、`NotificationScreen.kt:109`、`AlertEngine.kt:40` 三处写入（**发现时的行号已漂，2026-10-18 按真位改；这三处后来收成 `AppState` 里的一个合并器（真位见 §52 那一格）**））里前两处走 `Repo.unreadCount()`，恒 0 → 首页"消息"红点永不出现，用户收不到"货主待客户操作"这类提醒；② **上传 URL 永远空**：`HandoverScreen.confirm`(`:121-126`) 拿到 `url = ""`，`photoUrl` 传空串给 `Repo.confirmHandover`，后端 `HandoverServiceImpl.java:222-223` 见空就**不写 photoUrl** → 交接凭证照片实际从未入库，而 UI 显示"交接已提交后端"。这是"看起来成功、数据没落库"的双层失真，比报错更坏。
- **修改方案**：在 `Payload` 上补一个**标量出口**，而不是去改后端信封（硬规则：不重设计后端）：
  1. `Payload` 增 `val scalar: Any?` 与 `fun scalarStr(vararg fallback: String): String` / `fun scalarNum(): Double`，`toPayload` 的非对象/非数组分支同时填 `scalar` 与保留 `{"v"}`（旧调用点不破，符合"不删字段保兼容"）；
  2. `Repo.unreadCount` 改 `p.scalarNum().ifEmpty{ multiNum("count","unreadCount") }` 口径：先标量、后对象键；`Repo.uploadPhoto` 改先 `scalarStr()`（即后端裸 String），再退回 `url/fileUrl`；`Repo.kt:1039` 同步；
  3. `Repo.saveAddress` 的 `url` 读取删掉（那是 `CommonResult<Boolean>`，本就没有 URL），避免读者误以为地址接口返回 URL；
  4. **失败必须可见**：`HandoverScreen` 在 `photoUrl` 为空但 `uri != null`（用户上传过照片）时，receipt 必须写"照片上传失败，凭证未入库"，不得显示"交接已提交后端"。
- **测试方案**：`ApiEnvelopeTest`（现有 `ApiTest`/错误模型测试类里加）：`{"code":0,"data":12}` → `scalarNum()==12.0`；`{"code":0,"data":"https://x/y.jpg"}` → `scalarStr()=="https://x/y.jpg"`；`{"code":0,"data":{"a":1}}` → `scalar==null` 且 `obj` 正常；`{"code":0,"data":[…]}` → `list` 正常。`RepoTest`（MockWebServer）：`/notification/unread-count` 回 `data:7` → 断言 `Repo.unreadCount()==7`；`/infra/file/upload` 回裸 URL → 断言 `Ack.url` 非空。端到端：交接上传照片后 `GET /driver/handovers` 里 `photoUrl` 必须非空。

- **本轮状态（P2-1，2026-09-29，DONE）**：按修改方案 1–4 全做完，逐条对得上：
  ① `Payload` 新增 `scalar` 字段 + `scalarLong()/scalarStr()/longOf()/strOf()`，`toPayload` 的非对象非数组分支
  同时填 `scalar` **并保留** `{"v": …}`（旧调用点零破坏，符合"不删字段"兼容规则）；
  ② `Repo.unreadCount`/`Repo.driverUnreadCount` 返回类型从 `Int` 改为 **`Long?`**（后端就是 `CommonResult<Long>`），
  读法改 `longOf("count","unreadCount")` = 先标量后对象键；`Repo.uploadPhoto` 改 `strOf("url","fileUrl")`；
  ③ `Repo.saveAddress` 的 `url` 死读法删除（那两个接口是 `CommonResult<Boolean>`，从来没有 URL）；
  ④ 交接照片改成**真实闭环**：`HandoverScreen.confirm` 先上传拿 URL，**URL 为空就不提交**（理由见下），
  提交后整表回读 `/driver/handovers`，回执只写回读到的事实，并删除本机 `copy(status = "已交接")` 的自我标记。
  - **为什么是"不提交"而不是"照样提交但写清失败"**：`HandoverServiceImpl.confirmHandover:196-197` 对已 `COMPLETED`
    的交接**直接 return**（幂等），一旦先把状态落成"交接完成"，`photoUrl` 再也补不进去（`:222-224` 只在非空时写）。
    所以"先提交后补照片"这条路在后端契约下不存在，缺凭证必须挡在提交前。
  - **顺带抓出并修掉的同一病根第三处**：`HandoverTask.status` 读的是 `multiStr("status","statusLabel")`，
    而 `AppDriverHandoverRespVO.status` 是**数字**（`TransportHandoverStatusEnum` 0..7），文案在 `statusName`。
    表现：状态胶囊渲染成"0/1/4"，且 `isDone()`（判"已交接/完成"）**永远不成立** → 已完成的交接一直挂在待办里。
    现改 `statusName` 优先；并新增 `HandoverTask.photoUrl` 解析 + 卡片用 `AsyncImage` 画回读到的凭证。
  - **连带后果（此前没记全）**：`Repo.uploadPhoto` 恒空不只是"交接没凭证"——`WorkbenchScreen.photoPicked`
    的守卫（"照片未真实上传就不提交作业"）在正式构建里**永远命中**，等于**装车/妥投整个动作被这把锁死**。
    §22 修好之后这两个动作才第一次真正可用。
  - 判据：`bash tools/check_envelope_probes.sh`（8 条 E-a…E-h，退出码）；负控制逐条实测过：
    外部直写角标 → E-a=1、旧对象键读法 → E-c=1、本机改状态 → E-d=1、页面不画 photoUrl → E-f=1/2、
    拦停文案重复 → E-g=2、`scalar = data` 重复 → E-h=2、`longOf/strOf` 多一处 → E-b=4，全部报红；
    复原后 8 条全绿、退出码 0。
  - 测试：`ScalarEnvelopeContractTest` 17 例（`5 / 0 / null / 502 / 对象形态 / 非数字字符串` 六种信封 +
    角标 max 合并与"读不到不写 0" + 司机域未绑定不上网络 + 上传裸 URL/对象 URL/缺 data 抛错 +
    **上传→confirm→回读**整链路（断言发出的 body 里真带着那个 URL）+ 空 photoUrl 时字段整个不发送 + 交接单解析形态两支）。
  - **没做**：真机端到端（后端未起、无设备）——"拍照→入库→再查询看得到图"只在 MockWebServer 上验证到报文与解析这一层；
    `photoUrl` 落库与图片可访问性属 `ENVIRONMENT_BLOCKED`。`needPhoto` 后端不下发（`AppDriverHandoverRespVO` 无此字段），
    所以"必须拍照"仍是客户端侧软约束，本轮按后端事实保留可选、只把文案改成如实描述。

---

## 23. 司机未读消息同样中招（与 §22 同根，单独列是因为它是司机侧唯一提醒）

判定：`CONTRACT_MISMATCH`（P0）

- **文件**：`app/src/main/java/com/shanxiang/buspost/data/repo/Repo.kt`
- **类 / 函数**：司机消息段(`:999-1007`)，`multiNum("count","unreadCount")`
- **当前行为**：读 `{"count"|"unreadCount"}`，后端给的是裸 `Long` → 恒 0。
- **后端真实行为**：`AppDriverController.java:239-243` `CommonResult<Long> messagesUnreadCount(@RequestParam driverId)`；`/driver/messages`(`:226-237`) 返回 `PageResult<AppNotificationRespVO>`，Android `driverMessages`(`:992-997`) 解析正确。
- **小程序行为**：`api.js:476 getDriverUnreadCount` 直接把返回值当数字。
- **问题**：司机端"新异常通知/待交接提醒"没有角标，而 §21 让异常根本进不到 App，两者叠加 = 司机侧提醒体系为空。
- **修改方案**：随 §22 的标量出口一起改；司机首页把未读数与 `AlertEngine` 的本地提醒合并显示，并在 `driverId <= 0`（未绑定）时显示"未绑定司机档案"而不是 0。
- **测试方案**：MockWebServer `data:3` → 断言 3；`data:0` → 断言 0 且**不是错误态**；driverId=0 → 断言不请求或请求后显示未绑定态。

- **本轮状态（P2-1，2026-09-29，DONE）**：
  - 标量出口随 §22 一起落地：`Repo.driverUnreadCount(): Long?` 走 `Payload.longOf("count","unreadCount")`，与用户侧同一通道（`Repo.kt:1090`）。
  - **未绑定不请求**：`driverIdParam()` 在 `driverId <= 0` 时抛 `DriverUnboundException`，`runCatching` 吞掉后返回 null，请求根本不上网络。测试实测 `server.requestCount == 0`（`ScalarEnvelopeContractTest.司机未绑定角标时一个请求都不发`）；判定条件是"请求数"这种外部行为，不是"返回 null"这种自说自话。
  - 绑定后带后端授予的 driverId：`/driver/messages/unread-count?driverId=61` 断言在 URL 上，值 61 来自 `/driver/profile` 而不是本机猜测。
  - "显示未绑定而不是 0"这一条：角标侧不虚构（null → 保持上一个值，未绑定态初值 0 ⇒ tab 无红点，等于"没有可信提醒"而不是"后端说没有提醒"）；数据侧 `WorkbenchScreen.kt:292` 的 `error = listOfNotNull(p.error, s.error, k.error, t.error, g.error).firstOrNull()` 里 `driverTasks()`/`routeGuide()` 未绑定时返回 `DRIVER_UNBOUND`，首页仍显示"未绑定司机档案：后端没有该账号的 driverId，请联系站点核对工号"。**没做**：`/driver/profile` 与 `/driver/shifts` 按后端契约是登录态识别、不受守卫，所以未绑定时首页确实能拿到 profile 的 0 件数据 —— 这是后端口径，不是客户端缺陷。
  - "本地提醒与未读数合并显示"：tab 角标与系统通知都读 `AppState.driverUnread`（`DriverShell.kt:25`），`AlertEngine.kt:37/47` 与 `DriverMessagesScreen.kt:103/107` 都通过 `reportDriverUnread(serverCount, listCount)` 单一入口写入（见 §52）。

---

## 24. `DriverNavScreen` 仍是假导航：`stepIndex++ / stepping / delay(4000)` 自动播转向

判定：`UI_ONLY` + `DEMO_ONLY`（P0，违反硬规则"宁可暂时不能导航，不允许假导航"）

- **文件**：`app/src/main/java/com/shanxiang/buspost/ui/driver/DriverNavScreen.kt`、`app/build.gradle.kts`
- **类 / 函数**：`NavViewModel.stepIndex`(`:94`)、`stepping`(`:103`)、`step`(`:105`)、`next()`(`:144-146`)、`LaunchedEffect(vm.stepping)`(`:180-186`，`while (vm.stepping) { delay(4_000L); vm.next() }`)、`LaunchedEffect(vm.stepIndex, vm.voice)`(`:194`，含 `:197` 播报"…米后…")、UI 绑定(`:342` 自动步进开关、`:357-359` 手工点步骤)
- **当前行为**：转向卡、进度环、语音播报的推进动力是**墙钟 4 秒**，与车辆真实位置无关：`stepIndex` 自增 → `progress = stepIndex/(steps-1)`(`:248`) → 高亮站点 `stations[min(stepIndex, …)]`(`:261`)。
- **后端真实行为**：`AppDriverRouteRespVO` **没有任何 steps/lanes 字段**（`grep` 该类只有 `stops/polyline/routeProvider/routeAvailable/deviationMeters/nextStop*`）；逐转向数据只存在于 `DemoData.kt:682 "steps"`。因此 `Repo.routeGuideOf`(`:863` `multiArr("steps","guidance","instructions")`) 在真实构建里得到**空数组** → 假步进在真机上连内容都没有。
- **小程序行为**：`pages/driver/routes/routes.js:160-165` 起步导航直接 `wx.openLocation` 交给系统地图——它**不自己造转向**。
- **问题**：① 硬规则明令禁止 fake turn / fake lane / 自动步进，本文件是当前工程里最大的一处违规（此前状态记录声称"已修"，本轮复核证明未修，见 §54 与 DECISION_LOG）；② 更危险的是它给司机"已经在导航"的确定感：进度、剩余距离、播报都会照常走，而车可能还在站里装货；③ `lanes`/`recommendedLane` 是演示造的（§46），车道引导在真机上属于凭空。
- **修改方案**（分两步，第一步不依赖任何新依赖，先做）：
  1. **立刻去假**：删除 `stepping`/`delay(4_000)`/`next()` 自动推进；`stepIndex` 只能由"真实位置沿已校验折线的投影进度"推导（`GeoMath` 已有投影/方位工具，`core/map/RouteRepository` 已给出 `RouteCheck.Accepted`）；`steps` 为空时（真实构建必然为空）整个转向卡/车道卡/自动播报**不渲染**，页面显示 §57 记录的"后端未下发逐转向数据"状态，地图与到站提醒（真实坐标 + 真实折线）照常可用；语音播报只在"到站"这种可证实事件上触发。
  2. **真导航**（P1-2，受依赖阻塞）：新增 `core/navigation/{NavigationEngine, NavigationState, AmapNavigationEngine}`，`NavigationEngine` 只是接口（`start(routeId, waypoints)/state: StateFlow<NavigationState>/stop`），实现里用高德导航 SDK 的 `AMapNavi` + `AMapNaviListener.onNaviInfoUpdate` 回调喂 `NavigationState`，Compose 侧只渲染 state。**先决条件**：`app/build.gradle.kts:215` 目前只有 `com.amap.api:3dmap:10.0.600`，必须先把导航 SDK 依赖加进来并**解包 aar 核对真实类与方法签名**（硬规则：不确定 SDK API 就先查当前依赖，绝不编造 `AMap3DModel` 之类）。依赖拉不到就记 `BLOCKED`，不许用假步进顶替。
  3. `DriverNavScreen` 旧实现不删除文件（硬规则），改为 DEBUG ONLY：`BuildConfig.DEBUG` + 演示开关双条件才允许进入该路由，并挂 `DemoNote`；正式包进入该路由显示"导航能力未就绪"。
- **测试方案**：① 静态断言：`grep -n "delay(4_000\|stepping\|stepIndex + 1" app/src/main/java/com/shanxiang/buspost/ui/driver/*.kt` 在生产源码集里零命中（把这条 grep 加进 `AI_ITERATION_STATE.md` 的自检行）；② Robolectric：喂一串固定 `Geo` 位置序列，断言 `stepIndex` 单调不倒退且**位置不变时 stepIndex 不变**（这是"不再靠墙钟"的直接判据）；③ 真实构建（`DEMO_ALLOWED=false`）断言 `RouteGuide.steps` 为空时导航页无转向卡；④ 真机：车辆静止 60 秒，屏幕进度必须不动——这一条在假实现上是必然失败的，正是回归的价值。

---

## 25. 交接卡片字段名与 `AppDriverHandoverRespVO` 大面积对不上

判定：`CONTRACT_MISMATCH`（P1）

- **文件**：`app/src/main/java/com/shanxiang/buspost/data/model/Models.kt`
- **类 / 函数**：`HandoverTask.parse`(`:1131-1149`)
- **当前行为**：读 `fromStation|currentStation`、`toStation|nextStation`、`fromPlate|vehiclePlate`、`toPlate|nextPlate`、`status|statusLabel`、`action|actionLabel|nextAction`、`needPhoto|photoRequired|forcePhoto`、`createTime|planTime`。
- **后端真实行为**：`AppDriverHandoverRespVO.java:11-62` 只有 `id, orderId, orderNo, goodsName, stationId, stationName, fromDriverId, fromDriverName, toDriverId, toDriverName, itemCount, weightKg, photoUrl, status(Integer), statusName(String), handoverTime, remark`。**没有** from/to 站点、没有车牌、没有 needPhoto、没有 action、没有 createTime。
- **小程序行为**：`pages/driver/handover/handover.js:55-70` 直接 `{...h}` 透传后端字段，并只用 `handoverTime`（`formatBackendTime(h.handoverTime)`）——它按后端真字段渲染。
- **问题**：`status = multiStr("status","statusLabel")` 命中后端的**数字** `status`，卡片上出现"1""2"这种裸码而不是 `statusName`；`fromStation/toStation/fromPlate/toPlate` 恒空，交接卡中间段"谁交给谁、在哪交"只有司机姓名可用（`fromDriverName/toDriverName` 解析正确，`transferStation` 用 `stationName` 兜对了）；`needPhoto` 恒 false → `HandoverScreen` 里"凭证缺失"分支（`:131-136`）永不触发，是死代码；`time` 读 `createTime` → 恒空（该用 `handoverTime`，而 `handoverTime` 其实已被单独解析，页面显示的却是 `time` 位置）。
- **修改方案**：① `status` 改 `status = o.multiStr("statusName").ifEmpty { handoverStatusText(o.multiNum("status").toInt()) }`，并新增与后端 `TransportHandoverStatusEnum` 对齐的小表（不许自己编 0..N）；② 站点用 `stationName` 填 `transferStation`，`fromStation/toStation` 改成 `fromStationName/toStationName`（若后端将来补字段可直接命中，保持"不删字段"）；③ 删掉 `needPhoto` 的假来源，改由 UI 侧规则决定（后端 `HandoverServiceImpl.java:332-333` 允许照片可选，故 Android 文案改为"建议上传凭证"，不再显示"凭证缺失"）；④ `time` 用 `handoverTime` 优先。
- **测试方案**：`HandoverTaskParseTest`：用**后端真实样例报文**（从 `/driver/handovers` 抓一条或按 VO 手写字段名）断言 `status=="待交接"`、`transferStation=="…"`、`time` 非空；再加一条防御断言：只给 `status:1` 不给 `statusName` 时，渲染的是中文文案而不是 "1"。

---

## 26. `RoutesScreen` 在 ack 之后用本机造的下一状态覆盖列表

判定：`UI_ONLY`（P0）

- **文件**：`app/src/main/java/com/shanxiang/buspost/ui/driver/RoutesScreen.kt`
- **类 / 函数**：`RoutesViewModel.action`(`:98-106`，`:104` `legs = legs.map { … copy(statusLabel = labelOf(action), status = nextStatus(leg.status, action)) }`)
- **当前行为**：`Repo.legAction(action, leg.legId)` 发出后，**不看返回**（`Ack` 只有 `demo` 位），立刻在本机把这条段的 `status`/`statusLabel` 改成客户端猜的值；`receipt` 直接写"$action 已提交后端"。
- **后端真实行为**：`AppDriverController.java:163-224` 全部是 `CommonResult<Boolean>`，**响应里没有新状态**；真实状态由 `DriverAppServiceImpl.java:1482-1498` → `MultiLegServiceImpl.advanceLegStatus/completeLeg` 在服务端推进，并且会做归属校验（`:1478-1480` 非本人段抛 `LEG_NOT_ASSIGNED`）。要拿到权威状态**必须重新 `GET /driver/legs`**。
- **小程序行为**：`handover.js:127` 提交 → `:131` `await this.loadAll()` **整表重载**（legs + handovers + currentLeg 三路），UI 只渲染服务端回读值。
- **问题**：① 服务端拒绝（未归属、状态机不允许、订单已取消）时 `Api` 会抛 `ApiException`（`Api.kt:253` `bizCode != 0`），这条路径是安全的；但只要业务成功而**后续副作用失败**（例如 `arrive-dest` 生成了交接但没生成下一条腿），本机显示的就是与库内不一致的状态，司机按假状态继续操作；② `nextStatus` 造的值本身是错的（见 §28），于是"完成"标签会被贴到只是"前往起点"的段上；③ 与 `WorkbenchScreen` 的正确做法（`:190` 之后回读）不一致，同一工程两套纪律。
- **修改方案**：① `action()` 成功后改为 `val f = Repo.driverLegs(); legs = f.items`，本机不做任何 `copy(status=…)`；② `receipt` 文案与 `Ack.demo` 绑定（演示态必须写明"未提交后端"），真实态写"已提交，状态以后端回读为准"；③ 重载失败时保留旧列表 + 挂错误条（沿用 `Repo` 既有 `Feed.error` 约定，绝不渲染成"没有段"）；④ `current` 也一并从 `/driver/current-leg` 回读。
- **测试方案**：Robolectric + MockWebServer：断言 `POST /driver/leg/accept` 之后**紧跟**一次 `GET /driver/legs`；mock 列表回 `status=2` 时 UI 显示"司机已接单"，mock 回 `status=7` 时显示"运输中"——同一动作、两种回读、两种渲染，证明 UI 真值来自后端；再 mock 一次重载失败，断言旧列表仍在屏上且有重试入口。
- **本轮状态（⑧ 段状态机收口，2026-09-29）**：①②④ 已落地 —— `RoutesViewModel.action` 现在是
  `Repo.legAction` → `Repo.driverLegs()` 整表回读，`legs` 只来自后端；本机 `copy(status = …)` 与 `nextStatus`/`labelOf` 三个函数已删除，
  判据 `tools/check_status_probes.sh` S-la=0 / S-lb=0 / S-le=2（两处入口都带 `finally` 复位 busy）。
  回读后同码时回执写"可能未被接受，请下拉刷新核对"，不写成功。③ 的 `Feed.error` 沿用：重载失败保留旧列表 + 错误条。
  **未做**：Robolectric/MockWebServer 那层（§60 第 7 项的测试框架投入），当前由 `DriverLegStateMapperTest`（11 例）+
  `DriverLegRespContractTest`（4 例）在 JVM 侧覆盖"动作合法性 + 报文解析"，网络往返顺序仍属 `ENVIRONMENT_BLOCKED`。

---

## 27. `HandoverScreen.advanceLeg` 丢弃 ack 并用"步骤序号 +1"推进交接状态

判定：`UI_ONLY`（P0）

- **文件**：`app/src/main/java/com/shanxiang/buspost/ui/driver/HandoverScreen.kt`
- **类 / 函数**：`advanceLeg`(`:101-115`，`:107` `Repo.legAction(action, leg.legId)` 返回值直接丢；`:108-109` `val ni = (legStep(leg.status)+1).coerceAtMost(LEG_ACTIONS.lastIndex)` → `currentLeg = leg.copy(status = ni, statusLabel = LEG_ACTIONS[ni].second)`)；`confirm`(`:117-136`，`:129-130` `items = items.map { … copy(status = "已交接", actionLabel = "", needPhoto = false) }`)
- **当前行为**：两处都先用**数组下标**当状态：`legStep(status)` 把后端枚举码映射成 `LEG_ACTIONS` 的下标，再 +1 当作新状态显示；交接确认后在本机把该条改成"已交接"，不重新 `GET /driver/handovers`。
- **后端真实行为**：`/driver/handover/confirm`(`AppDriverController.java:142-146`) → `HandoverServiceImpl.confirmHandover`(`:193-223`) 会写交接状态、照片、并联动订单/事件；`AppDriverLegActionReqVO` 里只有 `driverId/legId/photoUrl/remark`，**响应是 `CommonResult<Boolean>`**，没有新状态。真实状态只能从 `/driver/handovers`、`/driver/current-leg` 回读。
- **小程序行为**：`handover.js:131` `await loadAll()` 重载三路数据后再渲染；`:192-196` 交接确认允许无照片但必须二次确认弹窗，**不预先改本地状态**。
- **问题**：① 状态空间被替换成"按钮序号"：一旦 `LEG_ACTIONS` 顺序与后端状态机不完全一致（现实中已经不一致，见 §28），推进出来的状态是虚构的，司机看到"已完成"而库里还在 9（交接中）；② 交接单"已交接"是本地标记，若后端因 `handoverId` 已确认/司机不匹配而回 `false` 语义（业务码非 0 会抛，所以能兜住一部分），但**并发场景**下（另一司机先确认）本机会长期显示错的；③ 与 `Repo.confirmHandover` 的 `photoUrl` 空串问题叠加（§22），形成"显示已交接 + 库里无凭证"。
- **修改方案**：① `advanceLeg` 成功后调用 `Repo.driverLegs()` + `Repo.currentLeg()` 回读，`LEG_ACTIONS` 只用于**决定下一个动作名**，不再用于决定状态值；② `confirm` 成功后 `items = Repo.handovers().items` 整表回读，删除 `copy(status = "已交接")`；③ `photoUrl` 解析按 §22 标量出口修好，并在 `uri != null && url.isEmpty()` 时把 receipt 明确写成"凭证照片未入库"；④ 保留"照片可选"这一与后端一致的行为，但 UI 文案不许暗示"已上传"。
- **测试方案**：断言动作后发出 `GET /driver/handovers`（顺序敏感）；mock 回读返回 `statusName:"交接中"` → UI 显示"交接中"（而不是本地写的"已交接"）；mock 上传返回裸 URL → 断言 `photoUrl` 非空并出现在 confirm body；Mockito/Robolectric 断言 `LEG_ACTIONS` 长度变化不会改变任何**已发出**请求（防止下标再次当状态用）。
- **本轮状态（⑧ 段状态机收口，2026-09-29）**：① 已落地并更进一步 —— `LEG_ACTIONS` 数组与 `legStep()` **整个删除**，
  段动作与时间线全部出自 `DriverLegStateMapper`（`LegDriverAction` 带后端目标码，不再存在"下标即状态"）；
  `advanceLeg` 现在是 `Repo.legAction` → `Repo.currentLeg()` 回读，回执只写后端回来的 `statusLabel`，同码时写"可能未被接受"。
  ② 的 `copy(status = "已交接")` 在 P2-1 已删（现为整表回读 + 凭证照片回读判据，见 §22/§52）。
  ④ 新增一条与后端一致的门禁：段卡在码 9（交接中）时**不**发无照片的 `handover-confirm`，
  按钮改为"去交接列表拍照确认"（`LegDriverAction.HANDOVER_CONFIRM.needsPhoto`），凭证流程只在交接列表那一条上走。

---

## 28. 段状态机契约冲突：Android 自造 0..4 状态与动作表，与 `TransportLegStatusEnum`(0..11+99) 不同源

判定：`CONTRACT_MISMATCH`（P0，含后端注释本身漂移）

- **文件**：`app/src/main/java/com/shanxiang/buspost/ui/driver/RoutesScreen.kt`、`ui/driver/HandoverScreen.kt`；`cargo-post-platform/.../driver/vo/AppDriverLegRespVO.java`
- **类 / 函数**：`RoutesScreen.nextStatus`(`:109-114`)、`labelOf`(`:116-121`)、`statusLabel`(`:332-339`)、`nextActionOf`(`:343-349`)、时间轴 `done = leg.status >= 4`(`:174`)、卡片 `done = leg.status >= 4`(`:259`)；`HandoverScreen.LEG_ACTIONS`/`legStep`(`:101-115` 使用处)
- **当前行为**：`nextStatus` 把 `accept→2`、`load|start|navigate→3`、`arrive-dest|handover-confirm|complete→4`；`statusLabel` 认为 `3="运输中" 4="已完成" 5="异常"`；两处 `done` 判据是 `status >= 4`。
- **后端真实行为**：`TransportLegStatusEnum.java:24-36`：`0 已规划 / 1 已分配 / 2 司机已接单 / 3 等待发车 / 4 前往起点 / 5 已到达起点 / 6 装货中 / 7 运输中 / 8 已到达终点 / 9 交接中 / 10 派送中 / 11 已完成 / 99 异常`。所以 Android 的每个映射都错一格以上：`navigate` 真实是 4（前往起点），`load` 是 6，`start` 是 7，`arrive-dest` 是 8，`handover-confirm` 之后是 9/10，`complete` 才是 11；而 `status >= 4` 把"前往起点/已到达起点/装货中/运输中/交接中"**全判成已完成**；`5="异常"` 更是把后端的"已到达起点"显示成异常。
- **小程序行为**：`handover.js:95-104` 的状态→动作映射与枚举**一致**（1 accept、2 navigate、4 arrive-origin、5 load、6 start、7 arrive-dest、8(+required) handover-start、8 complete、9 handover-confirm、10 complete），并且 `parcel.js:446` 直接展示 `activeLeg.statusName`。`Models.kt:758-760` 的 `TransportLeg.done = status == 11` / `active = status in 7..10` 是**正确的**——同一个工程里同时存在正确与错误两套判据，`RoutesScreen` 用的是错的。
- **问题**：这是本次审计里最结构性的一条：司机看到的段进度可能整体提前 4~7 格，一条正在装货的段显示"已完成"，调度以为货在路上。且 `AppDriverLegRespVO.java:37` 附近对 `status` 的注释与枚举实际值不一致（注释漂移），后续人照着注释写映射会再次踩坑。
- **修改方案**：① 在 Android 侧建立**唯一**段状态表 `core/model/LegStatus.kt`（或由 `TransportLeg` 提供 `done/active/actionLabel`），按 0..11+99 全码覆盖，语义直接从 `TransportLegStatusEnum` 抄并在注释里写明源文件与行号；`RoutesScreen`/`HandoverScreen` 一律改用它，删除本地 `statusLabel`/`nextStatus`/`labelOf`/`done = status >= 4`；② 推进一律走 §26 的后端回读，客户端**不再计算下一状态**（`nextStatus` 整个函数应消失）；③ 动作可用性由服务端状态推导：`status==0|1 → accept`；`2 → navigate`；`4 → arrive-origin`；`5 → load`；`6 → start`；`7 → arrive-dest`；`8 → handover-start`(handoverRequired) 或 `complete`；`9 → handover-confirm`；`10 → complete`；`11 → 无动作`；`99 → 等待重调度 + 上报入口`（§21）；④ 后端顺手修 `AppDriverLegRespVO` 的状态注释，使其列出 0..11+99（只改注释，不动字段，符合"不删字段保兼容"）。
- **测试方案**：单测 `LegStatusTest`：遍历 0..11 与 99，断言 `label` 与 `done/active` 与枚举语义一致，特别 `assertFalse(LegStatus.isDone(4))`、`assertTrue(LegStatus.isDone(11))`、`assertEquals("前往起点", LegStatus.label(4))`；动作表单测：对每个状态断言"允许的动作集合"（把 §21 的 99 → 无 complete 作为用例）；Robolectric：`/driver/legs` 回 `status:4` → 卡片显示"前往起点"且按钮是"到达装货站"；后端测试：`AppDriverLegRespVO` 注释与枚举一致（可用反射遍历枚举值断言注释包含每个中文标签）。
- **本轮状态（⑧ 段状态机收口，2026-09-29）**：①②③ 已落地 —— `ui/driver/DriverLegStateMapper.kt` 是唯一出口
  （`LegStatus` 表在 `data/model/ServerStatus.kt`，`TransportLeg.done/active/statusLabel/statusTone` 委托到它），
  `RoutesScreen` 的两处 `done = status >= 4`、私有 `statusLabel`/`nextStatus`/`labelOf`/`nextActionOf` 与
  `HandoverScreen.LEG_ACTIONS`/`legStep` 全部删除，判据 S-a=0（数字比较，注释除外）/ S-c=0（页面 `when(数字)`）/ S-la=0 / S-lb=0。
  **并修正本审计自己写错的三格**（`canTransit` 实测，`TransportLegStatusEnum.java:42-55`）：
  ① `0 → accept` 非法（PLANNED 只允许到 ASSIGNED/EXCEPTION，派单是调度的事），映射器给"等待调度派单"；
  ② `2 → arrive-origin` 非法（2 只能到 3/4/99），下一步是 `navigate`；
  ③ `8 → complete` 非法（ARRIVED_DESTINATION 只允许 9/10/99），末端段停在 8 时改为再发一次 `arrive-dest` 转派送
  —— 小程序 `handover.js:101` 那一格同样会被服务端拒，不作为对齐目标。
  守卫测试 `DriverLegStateMapperTest.每个下一步动作的目标状态都是后端允许的流转` 把 `TRANSITIONS` 逐码抄成期望值，
  任何人再往映射器里塞非法动作都会红。④（改后端注释）属于后端仓库，本轮未动，记在 BLOCKED 之外的待办里。

## 29. "切换为司机模式"不校验司机绑定，直接进入司机壳

判定：`LOCAL_ONLY`（P1）

- **文件**：`app/src/main/java/com/shanxiang/buspost/ui/user/MineScreen.kt`
- **类 / 函数**：`MineScreen` 的 `MineRow(BusIcons.bus, "切换为司机模式", …)`(`:143-149`)，`AppState.setRole(Role.DRIVER)`(当时写在 AppState.kt 第 72-75 行，只写 Prefs；这个方法今天已被 `setAccountRole` 取代，见 AppState 那条 KDoc，所以这里刻意不留冒号行号，免得长出一条指向已删代码的活锚点)
- **当前行为**：点一下就把本机角色改成 DRIVER 并 `nav.navigate(Routes.DRIVER_SHELL)`，**不调 `Repo.bindDriver()`**，也不看 `Prefs.driverId` 是否 > 0。
- **后端真实行为**：司机身份来自 `GET /driver/profile` 返回的 `driverId`；`Repo.bindDriver`(`:165-177`) 已经写得很正确——`profile.driverId > 0` 才落盘，否则返回错误"该账号未绑定司机档案，请联系站点核对工号"。登录路径 `LoginViewModel:117-123` **有**这个校验（选 DRIVER 角色登录时若未绑定就拦住），所以工程里存在两套纪律：登录有、切换没有。
- **小程序行为**：小程序没有"切换角色"按钮，角色由后端 profile 决定（`api.js` 无角色入参）——Android 这个入口本身就是本地概念。
- **问题**：① 非司机账号点进来后，`Prefs.driverId` 仍是 0，于是 `/driver/legs?driverId=0`、`/driver/tasks?driverId=0` 全带 0 去问后端，返回空列表 → 页面显示"暂无任务"，**掩盖了"你没有司机档案"这个真因**（违反"真实空数据才是空"的另一面：不是空数据的情况不能渲染成空）；② `driverId` 是 `Prefs` 里的残留值时更糟：`Prefs.clearSession`(`:150-164`) 只在退出登录时清，同设备换账号若走的是"直接登录另一个账号"路径，旧 `driverId` 可能还在，A 的司机端会读到 B 的班次数据（后端有归属校验时才侥幸安全，但这是越权探测面）；③ `DriverLocationService.kt:130` 依赖 `Prefs.driverId > 0`，未绑定时位置上报"看起来在跑但一句不发"。
- **修改方案**：① `MineRow` 的点击改成 `work { val b = Repo.bindDriver(); if ((b.value?.driverId ?: 0L) > 0) { AppState.setRole(DRIVER); nav.navigate(DRIVER_SHELL) } else { error = b.error } }`，与 `LoginViewModel` 同一条纪律；② 司机壳顶部加一条 `driverId <= 0` 的硬状态条"未绑定司机档案"，所有 `/driver/*` 调用在 `driverId <= 0` 时**直接短路不发请求**（在 `Repo` 层加守卫，避免 0 号查询打到后端）；③ 角色切换时若 `Prefs.userId` 变了就无条件 `clearSession` 后再绑定。
- **测试方案**：Robolectric：mock `/driver/profile` 回 `{driverId:0}` → 断言仍停留在用户端且出现"未绑定"文案、`nav` 未跳转；mock 回 `{driverId:7}` → 断言 `Prefs.driverId==7` 且进入司机壳；断言 `driverId==0` 时 `MockWebServer` 收到 0 条 `/driver/` 请求。

**本轮状态（P1-2 ⑥，2026-09-29）：①②③ 全部已修，测试口径改为 JVM（工程没有 Robolectric，见 §61）**

- **① 角色切换入口**：`MineViewModel.enterDriverMode(onGranted)`(`MineScreen.kt:112-124`) 先 `Repo.bindDriver()`，
  `(bound.value?.driverId ?: 0L) <= 0L` 时写 `error` 并 `return@work`，**不进司机壳**；`MineRow` 的点击实参已换成它，
  错误按 danger 色渲染在"我的服务"卡片内。与 `LoginViewModel` 走的是同一个后端档案，不再是两套纪律。
- **② `Repo` 层守卫**：新增 `driverIdParam()`(`Repo.kt:1113`)，未绑定（`Prefs.driverId <= 0`）直接抛
  `DriverUnboundException`(`:65`) → `errorTextOf`(`:53`) 翻成 `DRIVER_UNBOUND`(`:68`)。
  19 个司机请求调用点全部改走它，`mapOf("driverId" to driverId)` 字面量拼装实测 **0** 处；
  抛异常发生在参数求值之前，所以 `driverId=0` 的 URL **构造不出来**，不是"构造了但被拦住"。
  返回 `error` 后各页现有的 `ErrorState` 会显示"未绑定司机档案…"，因此没有另加司机壳横幅（这是本条唯一偏离原方案之处，如实记）。
  **豁免**：`driverProfile`(`:779`) / `driverShifts`(`:795`) / `driverPickups`(`:802`) / `driverEarnings`(`:817`) 这 4 个
  身份引导接口按 token 识别（小程序 `api.js:313-324` 同口径，URL 里没有 `driverId`），若给它们也加守卫，
  绑定就永远发生不了 —— 所以守卫只覆盖其余 19 个调用点（`driverMessages`/`driverUnreadCount` 是例外，后端确实要 `driverId`）。
- **③ 换账号残留**：`Prefs.clearDriverBinding()`(新增) 只清司机域键；`saveSession` 在写新 token 之前调用它，
  未退出直接登录第二个账号也不再继承上一位的 `driverId`（越权探测面关闭）。
- **实测**：`DomainSplitTest`（7 例，JVM + MockWebServer）钉住：未绑定时 7 个 GET + 5 个 POST 一个请求都不发
  （`server.requestCount == 0`）、绑定后带上后端授予的 `driverId=61`、身份引导接口不受守卫影响、
  换账号不继承、后端回空档案时掉绑定、**回 `data:null`/演示回落时保留既有绑定**（否则一次网络抖动就把真司机锁死）、
  以及 `MineViewModel` 两分支（`driverId:0` 不进司机域且文案正确 / `driverId:61` 进入并落 `Prefs.role`）。
  常驻判据：`bash tools/check_domain_probes.sh`（P-a/P-a2/P-b = 0，P-b2 = 21，P-c/P-d/P-e/P-f = 2/2/1/5）。

---

## 30. 演示态可见性被关闭：写操作返回"成功"，但用户没有任何横幅告诉他这是演示

判定：`DEMO_ONLY` + 诚实性违规（P1，与 §47 同根）

- **文件**：`app/src/main/java/com/shanxiang/buspost/ui/common/Bits.kt`、`core/net/Api.kt`、`data/repo/DemoData.kt`
- **类 / 函数**：`DemoNote`(`Bits.kt:43-49` 函数体首行 `return`，下面的横幅实现整段注释)、`Api.demoSession`/`Api.kick`(`:270-276` 演示构建把 401 记为演示态)、`DemoData` 写路径分支(`:710-712` `/send/create` 返回 `orderId 5009 / orderNo SH… / pickupCode 6174`)
- **当前行为**：`DEMO_ALLOWED=true` 的构建里，接口不通就回落到 `DemoData` 的**伪造响应**，包括写操作的成功回执；`Payload.demo=true` 一路传到 `vm.demo`；而唯一向用户说明"这是演示数据"的组件 `DemoNote` 被无条件 `return` 掉了。页面里所有 `DemoNote(...)` 调用（如 `OrderDetailScreen.kt:99`、`TraceScreen.kt:99`）因此都是空操作。
- **后端真实行为**：后端此时根本没有落库——`/send/create` 没被调用，没有订单、没有取件码。
- **小程序行为**：小程序无演示回落机制（`api.js` 失败就是失败），不存在这一类失真。
- **问题**：这是"把成功说得过头"的违规：`SendScreen` 会把 `pickupCode`（6174 这个**固定假码**）显示在取件码卡片上，用户会拿它去站点交接；`HandoverScreen`/`WorkbenchScreen` 的 receipt 虽有"演示模式：未提交后端"字样，但取件码、订单号已经上屏，用户记住的是号码不是那行小字。硬规则要求"宁可显示不可用，不许冒充数据"，所以正确做法是：**要么横幅必现，要么演示数据在写路径上不给成功回执**。
- **修改方案**：① `DemoNote` 恢复渲染，但按构建区分：`BuildConfig.DEBUG` 保留现在的静默降级，release 构建下**必须显示**（用编译期条件而非注释）；② `DemoData` 的写路径分支改为返回 `ApiException`/失败语义（演示构建也不假造 `orderId/pickupCode`），只读接口继续回落；③ `SendScreen` 在 `ack.demo` 时不清草稿、不显示取件码，只显示"演示模式：本次未生成真实运单"；④ `Api.demoAllowed` 的 release 值保持 `false`（`app/build.gradle.kts:103` 已保证），这条要写进 §56 回归清单防止被改回。
- **测试方案**：Robolectric：`demo=true` 时断言页面存在 `DemoNote` 文本节点（当前必然失败，即回归基线）；`Repo.createSendOrder` 在演示构建下断言**不返回** `pickupCode`；grep 自检：`Bits.kt` 的 `DemoNote` 体内首行不得是裸 `return`（允许 `if (!demo) return`）。

**本轮复核（P1-2 ⑥，2026-09-29）：状态不变，但把"空转范围"量出来了**
`Bits.kt:46` 的 `DemoNote` 体内首行仍是裸 `return`（横幅实现整段注释），调用点实测 **24 处**：司机侧 6 处
（`DriverMessagesScreen.kt:129`、`DriverNavScreen.kt:221`、`EarningsScreen.kt:84`、`HandoverScreen.kt:187`、`RoutesScreen.kt`、`WorkbenchScreen.kt`）
+ 用户侧 18 处。所以"司机端把用户域演示态带进来"这一疑虑的正解是：**两个域的演示态都不可见**，同一条根因。
不依赖 `DemoNote` 的写操作回执仍正常（`HandoverScreen.kt:66-67`、`RoutesScreen.kt:103` 的"演示模式：未提交后端"），
这说明修法只要恢复 `DemoNote` 渲染就能一次覆盖 24 处，不必逐页改。**本轮未动**：属演示诚实性而非分域（硬规则 1），
按 §58 顺序排在分域之后；本条的 grep 判据（体内首行不得为裸 `return`）现在仍为红，是**待修的回归基线**，不是新增违规。

---

## 31. 取消商城订单后的提示把"真实成功"说成"演示模式取消"

判定：`REAL_PARTIAL`（P1；此前记录的"假取消"经复核**不成立**，本条是修正后的真问题）

- **文件**：`app/src/main/java/com/shanxiang/buspost/ui/user/OrderDetailScreen.kt`
- **类 / 函数**：`OrderDetailViewModel.cancel`(`:76-85`)、渲染处(`:101-103`)
- **当前行为**：`Repo.cancelProductOrder(id)` → 成功后重新 `productOrders(pageSize=50)` 与 `productOrderTrace(id)` **回读后端**，然后 `cancelled = true`；UI 只要 `cancelled` 就固定显示"已在演示模式下取消，真实环境需后端确认。"
- **后端真实行为**：`AppProductOrderController.java:58-61` `@PutMapping("/cancel") @RequestParam("id")`，Android 走 `Api.put(path, emptyMap(), mapOf("id" to id))`(`Repo.kt:661`) 参数位置正确；取消后订单 `status=3` (`ProductOrderStatusEnum.CANCELLED`)。
- **小程序行为**：`api.js:247 cancelProductOrder(id)` 同样 query；`orders.js:80-110` 取消后重载列表。
- **问题**：写路径本身是**合规**的（以后端回读为唯一真值），但提示语在真实构建里谎称"演示模式"，用户会以为这次取消没生效、去重复取消或打电话问站点。属于文案与 `ack.demo` 脱钩的诚实性缺陷——与 §24 相反的另一种失真：把真的说成假的。
- **修改方案**：`cancelled` 改为携带演示位：`var cancelDemo by mutableStateOf(false)`，`cancel()` 里 `cancelDemo = 回读到的那条 order?.status == 3` 且 `demo` 标志；文案分三支：真实且已取消 →"已取消（后端确认）"；真实但状态未变 →"后端未受理取消：${order?.statusLabel}"；演示 → 现有文案。判断一律基于**回读状态**，不基于请求是否抛错。
- **测试方案**：Robolectric 三支用例：`{status:3}` → 断言文案含"后端确认"且不含"演示"；`{status:0}` → 断言文案为"后端未受理取消"；`demo=true` → 断言文案含"演示模式"。另加断言：`MockWebServer` 记录到 `PUT /product-order/cancel?id=…`（参数在 query）。

**本轮复核（P1-2 ⑥，2026-09-29）：状态不变**（`OrderDetailScreen.kt:102` 仍固定输出"已在演示模式下取消，真实环境需后端确认。"，
`grep -rn "已在演示" app/src/main/java` 实测 1 处命中）。分域复核不涵盖文案与 `ack.demo` 的绑定，
本条按 §58 顺序仍排在演示诚实性一组（与 §30/§47 一起修最省事：三支文案都只需要一个 `demo` 位）。

---

## 32. 到站距离与"确认到达"门槛用客户端 Haversine，且后端根本不吃这个值

判定：`REAL_PARTIAL`（P1，硬规则 5 边缘违规）

- **文件**：`app/src/main/java/com/shanxiang/buspost/ui/driver/WorkbenchScreen.kt`
- **类 / 函数**：`WorkbenchViewModel.distanceToTarget`(`:208-214`)、`canArrive`(`:220-222`)、渲染处(`:552` `distanceMeters = vm.distanceToTarget()`、`:862` "距「X」约 N 米，进入 50 米内即可确认到达")
- **当前行为**：`GeoMath.distance(me, target.geo)`（Haversine 直线米数）同时用于展示文案与"是否点亮确认到达"的门槛；无定位时 `d < 0` → `canArrive()` 直接返回 true。
- **后端真实行为**：`AppDriverRouteRespVO.java:81-83` 专门下发了 `nextStopRoadDistanceMeters` / `nextStopRoadEtaSeconds`（真实道路值，来源受白名单约束），`:76-78` 另有 `nextStopDistanceMeters`（直线，仅示意）；`/driver/arrive`(`AppDriverArriveReqVO.java:11-20`) 只收 `shiftId + stationId`，**不吃坐标、不做距离校验**——到站是司机声明，不是 GPS 判定。
- **小程序行为**：`workbench.js:21 ARRIVE_RADIUS_METERS = 50`、`:759`+`:762`+`:775` 同样用客户端 Haversine 决定 `canArrive`——小程序这处本身违规（§16 已列），Android 只是复刻了它。
- **问题**：① 文案把直线米数放在"距目标站"的位置上，与同页来自后端的道路距离口径混用，司机按直线数字判断"还有 200 米"而实际要绕行 1.2 km；② "GPS 门槛决定业务按钮"是硬规则明令禁止的方向（"禁止 GPS 自动完成业务"的镜像问题：不许用 GPS 自动**放行**业务）；③ `d<0 → true` 让门槛在弱信号下形同虚设，规则既严又不管用，纯粹的随机行为。
- **修改方案**：① 展示优先用后端 `nextStopRoadDistanceMeters`（`Models.kt` 已解析的道路字段），有值就显示道路米数并标注"道路距离"；无值显示"—"，**不得回落到直线值**（除非文案显式写成"直线约 N 米（非道路里程）"，与 `BusScreen.kt:617` 已有的诚实标注同一口径）；② `canArrive()` 去掉 GPS 门槛：`确认到达` 始终可点（后端本就按声明受理），弱信号时在按钮下方提示"当前定位精度 X 米，到站将由你确认"；③ `ARRIVE_RADIUS_METERS` 常量若仍被别处用于"自动到站提醒"，只允许产生**提醒**，不允许改变按钮可用性。
- **测试方案**：单测：`distanceToTarget` 在有 `nextStopRoadDistanceMeters=1200`、直线=300 时返回 1200；两者都缺时返回 -1 且 UI 显示"—"（断言不出现 300）；`canArrive()` 在 `Running.geo=null` 与 `d=5000` 两种情况下都可用（防门槛回潮）；Robolectric 点击后断言发出的 body 只含 `shiftId/stationId/driverId`。

**本轮状态（P1-1b，2026-09-29）：Android 侧已修**
- ① 展示：`MissionMarkers.nextStopDistanceText(roadMeters, straightMeters, targetName)` —— 道路值优先并写明"（后端路网）"；
  道路值缺失时只给带标注的直线远近"直线约 N 米（仅表示远近，不是道路里程）；道路距离接口未给"；
  两者都没有就写"接口未给"，**文案里不出现任何数字**。
- ② 门槛：`canArrive()`、`ARRIVE_RADIUS_METERS` 常量、VM 里那个从未被写入也从未被读的 `canArrive` 状态位**全部删除**。
  实测：`grep -rn "canArrive\|ARRIVE_RADIUS_METERS" app/src/main/java` → **0 命中**（这条判据已进 §56 与常驻自检，防止有人为了"体验"把 50 米门槛加回来）。
  「确认到达」现在 `enabled = !busy`，与后端语义一致：`Repo.arrive`(`Repo.kt:983`) 的 body 只有 `driverId/shiftId/stationId`，
  **不含坐标**，到站是司机声明而不是 GPS 判定。
- ③ 工程里不存在"GPS 自动到站提醒"，所以常量直接删除而不是降级成提醒 —— 若将来加提醒，只能产生提示，**不得改变按钮可用性**。
- **与小程序有意分歧**：`workbench.js:21/759/762/775` 的 50 m 门槛本身是被禁止的方向（§16 已列为参考实现违规、§48 定为不复刻项）。
  本条不追求"三方一致"，理由是硬规则「禁止 GPS 自动完成业务」及其镜像「禁止 GPS 自动放行业务」。
- **测试实做情况（如实）**：文案契约由 `MissionMapTest.距离文案优先后端道路值` 覆盖
  （断言 1200 道路值出现、直线 300 **不出现**；无值时不出现数字）—— 全量 `98 tests / 0 failures`、`assembleDebug` BUILD SUCCESSFUL、`lintDebug` 0 error。
  本节原方案里的 **Robolectric 点击断言 body** 未做（工程当前没有 Robolectric 配置），
  改用代码事实（`Repo.arrive` 的 map 只有三个键）+ 门槛 grep 代替；仍列入待办，不声称已完成。

---

## 33. 商城订单状态兜底文案与 `ProductOrderStatusEnum` 漂移（1 = 已发货，不是配送中）

判定：`REAL_PARTIAL`（P2，仅在后端未给 `statusName` 时显现）

- **文件**：`app/src/main/java/com/shanxiang/buspost/data/model/Models.kt`
- **类 / 函数**：`productStatusLabel`(`:379-381`)，被 `ProductOrder.statusLabel`(`:341`)、`:871` 与 `TraceScreen` 类页面复用
- **当前行为**：`0 待发货 / 1 配送中 / 2 已完成 / 3 已取消 / else 处理中`。
- **后端真实行为**：`ProductOrderStatusEnum.java:14-20`：`0 PENDING_DELIVERY 待发货 / 1 DELIVERED 已发货 / 2 COMPLETED 已完成 / 3 CANCELLED 已取消`。
- **小程序行为**：`orders.js` / `parcel.js:260` 一律 `o.statusName || 本地表`，本地表与枚举一致（"已发货"）。
- **问题**：1 的含义差一档——"已发货"（车已带出但可能还在途中）被写成"配送中"，与 `TransportOrderStatusEnum` 的 10/12（运输中/派送中）语义混叠，用户对同一辆车看到两种进度说法；`Models.kt:876 cancelled = status == 3` 经复核是**正确**的（3 就是 CANCELLED），本条只改文案。
- **修改方案**：`1 -> "已发货"`；并把 `else -> "处理中"` 改为 `else -> ""`（让上层落到 `statusName`，未知码不编造说法）。不改字段、不改协议。
- **测试方案**：表驱动单测逐码断言 0..3 文案；断言 `productStatusLabel(7) == ""`；Robolectric 断言后端给 `statusName:"已发货"` 时优先用它（现有 `:341` 逻辑），给空时本地表也是"已发货"（双表一致）。

---

## 34. 可达性评估：站名与距离字段名/单位都不对，错误值还会写进下单报文

判定：`CONTRACT_MISMATCH`（P0）

- **文件**：`app/src/main/java/com/shanxiang/buspost/data/repo/Repo.kt`、`ui/user/SendScreen.kt`
- **类 / 函数**：`Repo.reachability`(`:489-504`)、`SendScreen` 的表单组装(`:307-309`)与渲染(`:589-590`、`:776-777`、`:940`)
- **当前行为**：读 `reachable`（✔）、`nearestStation|stationName`、`distance|distanceMeters`、`walkMinutes|minutes`（✔）、`tip|message`；`SendScreen.kt:940` 直接文案"最近可接站点 ${reach.nearestStation}，约 ${(reach.distanceMeters/1000.0)} 公里"，`:307` 把 `reach?.nearestStation.orEmpty()` 作为 `originalAddress` 提交。
- **后端真实行为**：`AppSendReachabilityRespVO.java:19-55`：`reachable` / `reasonCode`(USER_LOCATION_UNREACHABLE | NO_SAFE_HANDOFF_POINT) / `serviceMode`(NEAREST_STATION | DOOR_PICKUP) / `distanceKm`(BigDecimal, **公里**) / `walkMinutes` / `distanceProvider`(AMAP_ROAD | HAVERSINE) / `recommendedStation{id,name,longitude,latitude,distanceKm}` / `message`。请求侧 `AppSendReachabilityReqVO.java:11-15` 声明 GCJ-02（见 §17）。
- **小程序行为**：`send.js:452-456` 读 `reachable/recommendedStation/serviceMode`；`send.wxml:182` 读 `distanceKm/walkMinutes/distanceProvider` —— 全部是后端真字段。
- **问题**：① `nearestStation` 恒空 → 提示文案变成"最近可接站点 ，约 0 公里"，用户不知道去哪个站；② `distanceMeters` 恒 0 → `SendScreen:589`、`:776` 的 `if (distanceMeters > 0)` 判断使"距离/步行"整行**消失**，看起来像后端没数据；③ `distanceKm` 是公里而 Android 变量名叫 meters，即使键名对上也会**再差 1000 倍**（`:940` 还除了一次 1000）；④ `originalAddress` 提交空串，运单上的起讫信息缺失；⑤ `reasonCode`/`serviceMode`/`distanceProvider` 一个都没解析，所以"当前位置不适合车辆进入"与"附近无可用站点"两种失败原因在 UI 上长得一样，`HAVERSINE`（后端自报的直线估算来源）与 `AMAP_ROAD` 也无法区分标注——违反"来源必须如实"的规则。
- **修改方案**：① `Reachability` 模型改为后端字段口径：`recommendedStationName`（读 `recommendedStation.name`）、`distanceKm`、`distanceProvider`、`serviceMode`、`reasonCode`、`message`；保留旧字段名做兼容但不再作为主键读取（遵守"不删字段"）；② 渲染层用 `distanceKm` 直接格式化，`distanceProvider == "HAVERSINE"` 时追加"（直线估算）"、`"AMAP_ROAD"` 时追加"（道路距离）"；③ `originalAddress` 改为提交推荐站点名 + 详细地址（后端 `recommendedStation.id` 若可取到就优先用 id，`AppSendOrderCreateReqVO` 有站点字段的按现契约填），字段为空时**禁止提交**并提示"未取到推荐站点，请手动选择"；④ `reachable=false` 时按 `reasonCode` 分支文案；⑤ 修 §17 的坐标系后复测本条。
- **测试方案**：`RepoContractTest`（MockWebServer 回一份**按 VO 手写**的报文）：断言 `recommendedStationName=="双碑站"`、`distanceKm==1.4`、`distanceProvider=="AMAP_ROAD"`；断言不再出现把 km 当 m 的路径（同一报文 UI 文本必须含"1.4 公里"）；Robolectric：`reachable=false + reasonCode=NO_SAFE_HANDOFF_POINT` 断言文案含"附近没有可用站点"；提交前若站名为空断言按钮禁用且**无 POST 发出**。

**本轮状态**：已修（⑩，2026-09-29，D-022）。`ReachabilityState` 三态建成、解析只认后端真字段、来源如实标注、下单报文两处污染堵住；
判据落成 `tools/check_reachability_probes.sh`（R-a…R-p，21 条）+ `tools/reach_negative_controls.sh`（逐条破口实测全部报红）。
数字：R-a 0 / R-b 0 / R-c 0 / R-c2 1 / R-d 0 / R-e 25 / R-f 2 / R-g 2 / R-h 0 / R-i 1 / R-j 17 / R-k 1 / R-k2 0 / R-l 5 / R-m 2 / R-n 3 / R-n2 0 / R-n3 2 / R-o 1 / R-o2 2 / R-p 1。

**与上面"修改方案 ③"的分歧（按 §5 以源码为准，不改后端）**：方案 ③ 建议把 `originalAddress` 改成"提交推荐站点名 + 详细地址"，
但 `AppSendOrderCreateReqVO.java:60` 的注释正是 **"位置 ≠ 车辆能到的地方：原始地址与服务站分开保存，不互相覆盖"**，
`TransportOrderServiceImpl:172` 又原样落库 —— 照方案 ③ 做就是把旧代码那个"用站点名覆盖用户位置"的错做得更彻底。
本轮实际做法：`originalAddress` 只填**用户自己的位置**（`Locator` 坐标 → `Repo.reverseGeo` 的 `formatted`，兜 `displayName`），
站点另走 `pickupStationId`；"禁止提交"改成两条更小的闸：① `validateStep1` 仍要求取货站点非空（推荐站点落不进可选列表就退回手选），
② `pickupServiceModeOrNull` 只在后端 `serviceMode` 合法**且**其推荐站点就是本单取货站点时才回传，否则丢键让服务端自己推导
（`TransportOrderServiceImpl:202-203`：客户端值非空才采信）。空 `originalAddress` 不拦提交 —— 小程序 `send.js:709` 本身就是
`this.data.originalAddress || ''`，且运单还有 `定位坐标` 行兜底；但 UI **不再藏这一行**（R-p 钉住"文字地址未取到，本单只提交了经纬度"）。

**测试方案落地情况**：17 例契约测试（`ReachabilityContractTest`，MockWebServer 按 VO 手写报文，含 §49 的逐字段"非默认值"守卫）全部实跑通过；
其中"Robolectric 断言文案"那半段改用**探针 + 报文断言**替代 —— 工程没有 Robolectric 依赖，为了跑一条文案断言引入整个依赖不划算，
所以文案判据落在 R-n/R-n2/R-n3/R-o/R-p 这五条内容探针上（`UNKNOWN` 分支里出现"可就近/可以运输"即判红）。
"提交前若站名为空断言无 POST 发出"改由 `SendOrderTest.表单不全时提交闸门拦住且不发请求` 覆盖（`server.requestCount == 0`）。


---

## 35. 车辆卡片与线路"全程"字段名/口径与后端不符

判定：`CONTRACT_MISMATCH`（P1）

- **文件**：`app/src/main/java/com/shanxiang/buspost/data/model/Models.kt`
- **类 / 函数**：`BusVehicle.parse`(`:526-556`)、`BusLine.parse`(`:568-580`，`:578 distanceMeters = multiNum("distance","meters")`)
- **当前行为**：车牌读 `plate|vehiclePlate|plateNumber`；`busId` 被当成 `shiftCode` 的兜底(`:541`)；`etaToNextStationMinutes`/`progress` 未读；`dataSource|source`（✔）；线路全程读 `distance|meters`。
- **小程序行为**：`bus/index.js` 与 `api.js:386/398` 按后端字段展示 `plateNo / etaMinutes / distanceToNextStationKm`。
- **后端真实行为**：`AppBusRespVO.java:11-62` 字段是 `busId(Long)` / `plateNo` / `shiftCode` / `routeName` / `etaMinutes(Integer)` / `etaToNextStationMinutes(Double)` / `distanceToNextStationKm` / `progress` / `speedKmh` / `dataSource`；`AppBusNearbyRespVO.NearbyBus`(`:78-150`) 用 `plateNo`、`status(String)`；`AppBusLineRespVO.java:28` 用 `distanceKm`（公里）；`AppBusNearbyRespVO.NearbyStation.distanceKm`(`:189`)。
- **问题**：① `plate` 恒空，车辆卡没有车牌（`plateNo` 三个候选名都没覆盖，而工程里其它模块读的是 `plate` —— 命名不统一是本条根因）；② `shiftCode` 在没有班次号时兜到 `busId`，把数字主键当业务班次码显示（"班次 7001"这种）；③ `BusLine.distanceMeters` 恒 0 → `BusScreen.kt:496` 永远显示"全程 —"；④ `etaToNextStationMinutes`（更精确的小数值）与 `progress` 都没用，进度条只能用 `1 - eta/40` 这类客户端造的说法（`BusScreen.kt:448`）。⑤ 注意 `Station.parse`(`:426-435`) 对 `distanceKm↔meters` 的处理是**正确**的（先米后公里换算），所以本条只针对 `BusLine`/`BusVehicle`，不要误改。
- **修改方案**：① `BusVehicle.plate` 的候选名加 `plateNo`；`shiftCode` 只读 `shiftCode`，取消 `busId` 兜底；② 新增 `etaToNextStationMinutes: Double?` 与 `progress: Int?` 字段（不删旧字段），展示优先精确值，`progress` 有值就用后端进度、没有就**不画进度条**（不得由 `etaMinutes` 反推）；③ `BusLine` 用 `distanceKm` 并换算成米填进现有 `distanceMeters`（与 `Station` 同口径，字段名保留以免破坏调用点）；④ `AppBusRespVO.status` 是 Integer 而 `NearbyBus.status` 是 String，`statusOf` 要两态兼容（补一条测试锁死）。
- **测试方案**：两份**分开**的样例报文（`/bus/realtime` 与 `/bus/nearby`）各跑一遍 parse，断言 `plate=="渝A·12345"`、`shiftCode=="K1-0830"`、`distanceMeters==12400.0`（由 `distanceKm:12.4` 来）、`status` 两种形态都能解析；再加一条反向断言：报文里只有 `busId` 没有 `shiftCode` 时，`shiftCode` 为空字符串（不许拿主键顶上）。
- **本轮状态（⑪ 公交 ETA 真实化，2026-09-29）**：**①④ 已落地、② 只落了 `progress` 那一半、③ 未做**。
  ① `BusVehicle.plate` 候选名补 `plateNo`（放最前）、`id` 候选名补 `busId`（旧代码这两个真实键都没覆盖，
     真后端下车辆编号与车牌**静默变空**）；`shiftCode` 只读 `shiftCode|busCode`，**取消 `busId` 兜底**。
  ②（**只做了一半，如实记**）`progress: Int?` 落地，`roadProgress` **只认后端 `progress`**；
     但方案 ② 要求的 `etaToNextStationMinutes: Double?`（`AppBusRespVO.java:50` 那个更精确的小数值）**仍未进模型** ——
     `grep -rn etaToNextStationMinutes app/src` 现在只命中 `DemoData`（兜底按真实 VO 形状发这个键，好让"被丢掉"看得见）
     与本页文档，`BusVehicle.parse` 里没有它。⇒ 真后端下精确值继续被降级成整数 `etaMinutes`，**这是本条未收口的部分**；
     `BusScreen` 里那句客户端自造的 `1 - eta/40` 删除（**注意：本条方案 ② 原文写的是"不得由 `etaMinutes` 反推"，
     是代码违背台账，不是台账写错** —— ⑪ 的 `DECISION_LOG.md` D-023 初稿把它记反过，已当场纠正并留痕）。
     台账没说到、本轮补上的一条：`AppBusServiceImpl:125-127` 是 `getProgress()!=null ? getProgress() : 0`，
     所以**"后端给 0"与"后端没给"是两个状态**，客户端只透传、不合并。
  ④ `statusOf` 两态兼容落地并锁进测试（`BusEtaContractTest` 的"lines 端点整数状态 1/2/0 三种码各有说法"）：
     字符串 RUNNING/IDLE/ARRIVED/NO_LOCATION + 整数 1/0/2，**整数 2 是停用不是"已到终点站"**，
     旧码表里那个 `3` 后端从不发、已删（判据 T-f1=0 / T-f2=1）。
  ③ **已做（本轮实测更正，原记"未做"）**：`BusLine` 的 parse 现在读真实键 `distanceKm = o.multiNumOrNull("distanceKm")`（`Models.kt:888`），
     旧字段 `distanceMeters` 改成可空、且不再读 `distance|meters` 那对无生产者别名（`Models.kt:866`，审计 §72 那一轮收的）。
     原记的坏法（`multiNum` 把没读到塌成 0.0、页面按 `> 0` 判）已经不存在：后端没给就是 null，页面说"未给"。
     后端真实键与出处不变：`AppBusLineRespVO.java:28 distanceKm` ← `AppBusServiceImpl:179 vo.setDistanceKm(route.getDistanceKm())`，**可能为 null**。
     顺带记一条同源事实：`AppBusServiceImpl:422` 的 `bus.setDistanceKm(located ? GeoDistanceUtil.haversineKm(...) : null)` ——
     `NearbyBus.distanceKm` 是**用户与车辆之间的直线公里数**且没定位时为 null，所以它只能用于排序与"离你 N km"，
     **不得**当道路里程展示，也不得参与任何可用性判定（⑪ 的 `DemoData` nearby 兜底按同形镜像，注释已写明）。
     修法照方案 ③：读 `distanceKm` 后 ×1000 填进现有 `distanceMeters`（字段名保留、调用点不动，与 `Station` 同口径），
     并补一条 `distanceKm:12.4 → distanceMeters==12400.0` 的契约断言。**排在 ⑫（§38 货运侧塌缩收口）之后做**，
     两者同属"读不到的键塌成 0"这一族，但分属不同契约，判据不合并。

---

## 36. `TransportLeg` 字段名整体不匹配，且段 VO 本来就没有几何

判定：`CONTRACT_MISMATCH`（P0，司机段列表显示成"→"）

- **文件**：`app/src/main/java/com/shanxiang/buspost/data/model/Models.kt`
- **类 / 函数**：`TransportLeg.parse`(`:761-788`)
- **当前行为（㊶ / D-062 现读 `TransportLeg.parse` 后更正本行；原文写的是 #261 之前的形状）**：`seq` 读 `seq|legSeq|legSequence|sort`；起点读 `fromStation|fromStationName|startStation|pickupStation`；终点读 `toStation|toStationName|endStation|deliveryStation`；车牌读 `plateNo|vehiclePlate|plate`；折线读 `navigationPolyline|polyline|track`；段坐标只读后端那四个真键（`AppSendLegRespVO.java:24-33` 的 `fromLongitude`），走 `multiNumOrNull` ⇒ 缺键=null，见 `Models.kt:1205-1208` 的 `fromLatitude`；`etaMinutes` 只认一个名字，见 `Models.kt:1213-1224`。
   旧行里的 `startLatitude`/`endLongitude` 那批自造别名现在在 `Models.kt` 里 **0 命中**（已撤读，G-1）。
- **后端真实行为**：`AppDriverLegRespVO.java:11-74` 下发 `id / orderId / orderNo / legSequence / fromStationId / fromStationName / toStationId / toStationName / status(Integer) / statusName / estimatedDeparture / estimatedArrival / actualArrival / vehicleId / plateNo / distanceKm / durationMinutes / navigationSource / handoverRequired / handoverId / handoverStatusName`。**该 VO 没有任何坐标或折线字段**，段级几何只能来自 `/driver/route`（`AppDriverRouteRespVO.polyline/currentSegmentPolyline`）。`estimated = navigationSource=="ESTIMATED"` 这类值在 Android 侧的解析路径本身没错，但后端该字段现在只会给白名单来源（§54）。
- **小程序行为**：`parcel.js:446` 直接拼 `activeLeg.legSequence / fromStationName / toStationName / statusName`；`routes.js`/`handover.js` 透传字段名，从不自造。
- **问题**：① `seq` = 0（`legSequence` 没被认），段顺序全并列，时间轴退化成无序；② 起终点站名为空 → 段卡与 `RoutesScreen.kt:174-176` 时间轴显示成 `" → "`，司机看不出这段跑哪；③ 车牌为空（`plateNo`）；④ `fromGeo`/`toGeo`/`navigationPolyline` 恒 null —— 对**司机段**契约（`AppDriverLegRespVO.java:11-74` 只有 `navigationSource` 这类字段，无坐标无折线）这句话到今天仍然成立；对**用户段**契约已不成立：`AppSendLegRespVO.java:24-33` 真下发 `fromLongitude` 那四个坐标键，㊶ 起解析层读得到（站点档案无坐标时后端就给 null，仍不许本机补线）；⑤ `statusName` 在 `statusLabel` 的候选名里 ✔，标签本身能显示——但曾被 §28 的本地表覆盖，两处问题叠加（§28/§34 那半已收口）。
- **修改方案（㊶ 更正落点：本轮实际走的是"按三方核对撤读死别名"，不是原文那句"只加不减"）**：① `seq`/`fromStation`/`toStation`/`plate` 的候选名已补上后端真键（`legSequence`/`fromStationName`/`toStationName`/`plateNo`），但**旧别名不再一律保留** —— 后端 0 生产者、小程序 0 消费者、演示面 0 下发的那批（`startLatitude`/`endLongitude`/`eta` 等）按 E-1/F-3 ②/G-1 撤读，因为"只加不减"正是让自造名一直续命的机制；② `TransportLeg` 的字段声明保留（未删字段），但段级几何现在明确按契约分两面：司机段无几何、用户段有坐标无折线；③④ 按 §28/§34 走 `routeGuide` 同源折线与 `handoverRequired` 分支（已落地）。
- **测试方案**：以 `AppDriverLegRespVO` 字段名手写报文跑 parse，断言 `seq==2`、`fromStation=="双碑站"`、`plate=="渝A·12345"`、`handoverRequired==true`；再加"旧字段仍可用"的兼容断言（`legSeq/fromStation/vehiclePlate` 那份报文仍能解析）；Robolectric 断言段卡文本不含孤立箭头（`label.contains("→")` 且两侧非空）；后端补一条 `AppDriverLegRespVOTest` 断言不含几何字段（防止有人误加假几何）。
- **本轮状态（⑧ 段状态机收口，2026-09-29）**：①④ 已落地 —— `TransportLeg.parse` 的候选名补上
  `legSequence / fromStationName / toStationName / plateNo`（**旧名一律保留**，只加不减），并新增
  `handoverRequired / handoverId / handoverStatusName` 三个字段（默认 `false / 0 / ""`，缺键就是缺键，不猜）——
  `handoverRequired` 是 §28 映射器分叉"交接还是派送"的唯一依据。契约测试 `DriverLegRespContractTest`（4 例）
  按后端真实键名手写 fixture，逐字段断言"不是靠默认值蒙过去"，并反向锁死"后端不下发段级几何 → 解析结果必须为空"。
  ②③ 未做：`navigationPolyline/fromGeo/toGeo` 仍在模型里（未删字段；⚠ ㊶ 限定一句 —— `fromGeo`/`toGeo` 现在对**用户段**有真生产者（`AppSendLegRespVO.java:24-33` 的 `toLongitude`/`toLatitude`，G-1 起按那四个真键读、缺键=null），"后端不下发"只对**司机段** VO 成立），段卡渲染路径要改到 `routeGuide` 的折线，
  与 §44/§46 的地图接线同批处理。

---

## 37. `/bus/nearby` 的 district 兜底没人传：无定位 = 直接空列表

判定：`REAL_PARTIAL`（P1）

- **文件**：`app/src/main/java/com/shanxiang/buspost/ui/BusPostRoot.kt`、`ui/user/BusScreen.kt`、`ui/user/HomeScreen.kt`、`widget/NextBusWidgetProvider.kt`
- **类 / 函数**：三处调用 `Repo.nearbyBuses(...)` 都没传第三参 `district`(`BusScreen.kt:252`、`HomeScreen.kt:192`、`NextBusWidgetProvider.kt:48`)；声明在 `Repo.kt:953`(`district: String = ""`)。第四处原本是闪屏那一笔，#78 第二格撤的是"取了不用"，与本条的缺参问题无关
- **当前行为**：无定位时传 `geo = null`，`district` 空串 → 请求只有 `radius`；后端三个可选入参都为空。
- **后端真实行为**：`AppBusController.java:56-65` `nearby(latitude, longitude, radius, district)`；`AppBusServiceImpl.java:257` `locationLevel = located ? PRECISE : (district 非空 ? DISTRICT : UNKNOWN)`；`:312-319` 明确实现了"无精确坐标时按 district 模糊匹配自建站点的名称/地址"这条兜底路径；`AppBusNearbyRespVO.located/locationLevel` 就是给前端做诚实标注用的。
- **小程序行为**：`pages/bus/index.js:220` `const district = hasCoords ? '' : ((loc && loc.district) || '')`，`:226` 把它传给 `api.getNearbyRealtimeBuses`（`api.js:398-399` 四参），`:254` 用 `district ? '根据${district}展示' : '定位不可用'` 标注来源。
- **问题**：Android 的 `Repo` 层已经支持 district，缺的是**调用点没接**：室内/弱信号/新装机场景 `Locator.once` 返回 null 时，用户看到的是"附近暂无公交"，而正确行为是按沙坪坝区（或已有逆地理结果）列出站点。`HomeScreen` 已经取到过 `ReverseGeo.district`(`Repo.kt:227` 解析了) 却没往下传，等于把已有的区域信息扔了。桌面小组件(`:45`) 更极端：永远传 `null` 坐标且无 district，所以小组件在任何情况下都拿不到"用户附近"的数据。
- **修改方案**：① 定义统一的取数函数（例如 `Repo.nearbyBestEffort(fix: LocFix, geo: ReverseGeo?)`）：有坐标走坐标；无坐标但 `fix.denied` 或超时，用上次逆地理的 `district`；两者都没有就发**空参数请求**并渲染"定位不可用"状态而不是空列表；② 四个调用点全部改用它，`locationLevel` 与 `located` 必须解析并在页面上标注（"根据沙坪坝区展示"）；③ 小组件把缓存的 district 写进 `Prefs` 供刷新时使用（不引入新权限）。
- **测试方案**：MockWebServer 断言三种输入下 query 的精确形态：有坐标→`latitude&longitude&radius`；无坐标有 district→只 `district`；都没有→无这三个参数且 `locationLevel=UNKNOWN` 被解析；Robolectric：`geo=null, district="沙坪坝区"` 时页面出现"根据沙坪坝区展示"且列表非空（后端 mock 回 DISTRICT 数据）；`UNKNOWN` 时出现"定位不可用"而不是"暂无"。

---

## 38. ETA 为空被渲染成"0 分钟"并标红成"马上到"

判定：`CONTRACT_MISMATCH`（P0，用户可感知的假信息）

- **文件**：`app/src/main/java/com/shanxiang/buspost/data/model/Models.kt`、`ui/user/BusScreen.kt`、`ui/user/HomeScreen.kt`
- **类 / 函数**：`BusVehicle.etaMinutes`(`:536` `multiNum(...).toInt()`，null→0)、`nearestStationEtaMinutes`(`:550`)；渲染 `BusScreen.kt:377`(`stubValue = "${v.etaMinutes}"`)、`:369`(`if (v.etaMinutes <= 5) t.danger`)、`:427`(进度 `1 - eta/40`)、`HomeScreen.kt:1163`("分钟到车")、`:751`("X分")、`HomeScreen.kt:191`(`arriving = filter { etaMinutes in 1..45 }`)
- **当前行为**：`etaMinutes`/`etaToNextStationMinutes` 是 `Integer/Double`，后端**故意留 null** 表示"算不出来"，Android 用 `multiNum` 归 0，UI 于是显示"0"并按 ≤5 分钟染成危险色。
- **小程序行为**：`bus/index.js` 对 null ETA 显示"—"（不是 0）。
- **问题**：`0 分钟` + 红色 = "车已经到站了"，这正是硬规则反对的"把没有说成有"。连带影响：`HomeScreen.kt:178` 的 `in 1..45` 过滤把 0 排除，所以首页"到车提醒"少一辆，而公交页那辆显示"0 分" —— 两个页面自相矛盾；`BusScreen.kt:329` 的 `sortedBy { etaMinutes }` 会把所有 null 的车排在最前（"最快到站"其实是"不知道"）；`:427` 进度条恒为满格。
- **修改方案**：① `etaMinutes`/`nearestStationEtaMinutes` 改成 `Int?`（null 表示后端没给），新增解析用 `multiNumOrNull`（若 `Payload/JSONObject` 扩展里没有就补一个，遵守"不删旧字段"）；② UI 统一：`null → "—"`，且**不进入** danger 分支、不参与排序首选项（`sortedBy { it.etaMinutes ?: Int.MAX_VALUE }`）、进度条在 null 时不画；③ `AlertEngine`/到车提醒只消费非 null 值；④ 若 `etaToNextStationMinutes` 有值而 `etaMinutes` 为 null，用精确值取整并保留"约"字。
- **测试方案**：单测：`{"etaMinutes":null}` → `etaMinutes == null`；`{}` → null；`{"etaToNextStationMinutes":6.7}` → 展示"约 7 分钟"；Robolectric 断言 null 车辆卡片文本为"—"且颜色不是 danger；排序断言 null 车不排在 3 分钟车前面；`in 1..45` 改为 `!= null && in 1..45` 后首页行为不变（回归）。
- **本轮状态（⑪ 公交 ETA 真实化，2026-09-29）**：① ② ③ 已落地，④ **没有做**（下面说明为什么）。
  **先回答"后端到底怎么表达算不出 ETA"**（按 §5 读真实 VO，不猜）：`AppBusServiceImpl:134`/`:719` 是 `setEtaMinutes(null)`，
  HTTP 体里是**显式 `"etaMinutes": null`**（MVC mapper 没开 NON_NULL，`JsonUtils.java:48` 那个 NON_NULL 只影响工具用的 mapper 实例）；
  而且两条算 ETA 的路都 `Math.max(1, …)`（`:132` 按进度、`:728` 按 `ceil`），**0 从来不是合法的到站分钟** ——
  所以客户端读出来的 0 只可能是自己编的。这条实测结论决定了后面每一步。
  ① `BusVehicle` 的 `etaMinutes`/`waitDepartureMinutes`/`nearestStationEtaMinutes`/`stopsToNearestStation`/`progress`/
  `speedKmh`/`distanceToNextStationKm`/`distanceKm` 全部改可空（`Models.kt:512-551`，判据 T-c 实测 8），解析统一走
  `multiNumOrNull(...)?:toInt()`；`geo` 也改成 `Geo?`（`:513-514`：后端 `latitude/longitude` 可空，旧的 `geo.lat != 0.0` 哨兵会把
  没位置的车画到北纬 0 度）。顺带修掉三处只在真接口上暴露的静默契约破口：
  `busId`/`plateNo` 不在旧的候选名列表里（车辆编号与车牌恒空）、`statusOf` 把整数码 2 读成"已到站"并自编 3→NO_LOCATION、
  `sourceOf` 把 `dataSource=REAL` 升格成 `REAL_FRESH`（线路页于是谎称"实时"，而新鲜度键 `/bus/lines` 根本没有）。
  ② 渲染统一到**一个映射点**：`etaValueText`/`etaUnitText`/`etaCaptionText`/`stationText`/`nextDistanceShortText`/`roadProgress`/
  `providerText`（`Models.kt:761-818`）。没给就是"—"+"到站时间未给"/"暂无到站预估"，`到站`与`发车`分开说（`:601` vs `:600`）；
  进度条只由后端 `progress` 驱动（旧的 `1 - eta/40` 自编 0-100 已删，判据 T-d1=0、T-d2=2）；
  车辆卡的"现实公交/项目线路"标签删掉 —— 那是拿 `routeProvider.contains("REAL")` 判的，真实值是 `SCHEDULE`/`AMAP`/…，
  永远判不到，线路来源在 `NearbyLine.dataSource`（`REAL_TRANSIT`/`PROJECT_TRANSIT`）不在车辆上。
  ③ 消费方全部先判 null：`AlertEngine:90` 改 `mapNotNull`、`HomeScreen:163` `!= null && in 1..45`、
  `ParcelScreen:83` `filter { it.etaMinutes != null }`、`NextBusWidgetProvider:102-106` 与 `BusScreen:368-370`/`HomeScreen:758-759`
  改读 getter；排序改 `byUserDistance()`（`Models.kt:837-847`，与后端 `:436-437` `nullsLast` 同口径 ——
  旧实现 `sortedBy { etaMinutes }` 把"不知道"排成"最快到站"）。
  ④ **未采纳**"用 `etaToNextStationMinutes` 取整顶上去"：`AppBusRespVO:49-50` 那个键在 nearby 端点根本没有（`NearbyBus` 20 个字段里没有它），
  而语义是"车→它的下一站"，不是"车→用户在等的那站"；拿它填 `etaMinutes` 等于换个方向再编一次。
  现在 `SCHEDULE` 如实标成"按班次计划时长估算"（`providerText`），`EUCLIDEAN` 标成"来源未识别"，都不说成真实路网。
  **范围**：本轮只做**公交**ETA 族。`SendOrder`(`Models.kt:878`)、`TransportLeg`(`:968`)、`ParcelTrace`(`Repo.kt:663`/`:950`)
  仍是 `etaMinutes: Int` + `multiNum`，页面侧还有 6 处 `etaMinutes > 0`（`TraceView:64/65/186`、`DriverNavScreen:424`、`RoutesScreen:301`、`ParcelScreen:431`），
  已作为**残留**打印在 `tools/check_eta_probes.sh` 的输出里（判据 T-e 只覆盖 notify/widget/data，故意不把货运侧的旧口径混进来充数）。
  **新增判据**：`tools/check_eta_probes.sh` T-a…T-q 共 25 条，负控制 `tools/eta_negative_controls.sh` 25 条全部 exit 1 且报对本条
  （2026-09-29 实测：基线"公交 ETA 判据通过" → 逐条破坏 → 恢复后仍通过）。
  同一轮把 `tools/check_reachability_probes.sh` 的 R-g 从"全 Models.kt 计数"改成"只数 `data class Reachability` 体内"：
  §38 给 `BusVehicle` 新加的 `distanceKm: Double?` 让原来的"恰好 2"变成 3 —— **这不是可达性回潮，是两条不同契约的字段被一条判据混 counted**，
  按内容重划边界而不是把预期改成 3（改数字就等于以后 BusVehicle 再少一个可空字段也看不见）。
  **测试**：`BusEtaContractTest` 9 例 / 全量 215 例（原 206）0 失败；`assembleDebug` BUILD SUCCESSFUL；lint 0 errors / 46 warnings。

---

## 39. 参数位置错误与假 legId：两个必 400 的写路径

判定：`CONTRACT_MISMATCH`（P0，功能直接不可用）

- **文件**：`app/src/main/java/com/shanxiang/buspost/data/repo/Repo.kt`、`ui/user/ParcelScreen.kt`、`ui/driver/WorkbenchScreen.kt`
- **类 / 函数**：`Repo.confirmStationAction`(`:529-530`，`Api.post(path, mapOf("orderId" to orderId))` 即 JSON body)、`WorkbenchViewModel.finish`(`:260-268`，`Repo.legAction("complete", "shift-$id")`)
- **当前行为**：① 交接确认把 `orderId` 放进 body；② "收班"把**班次 id 拼成字符串** `"shift-12"` 当 `legId` 发出去。
- **后端真实行为**：`AppSendController.java:127-129` 是 `@PostMapping("/confirm-station-action")` + `@RequestParam("orderId") Long orderId` —— **只吃 query/form 参数**，JSON body 不会被绑定，缺参 → `MissingServletRequestParameterException`(400)。`AppDriverLegActionReqVO.java:14-16` 是 `@NotNull Long legId`，Jackson 无法把 `"shift-12"` 转 Long → 反序列化失败 400。段级动作要求真实 `legId`（来自 `/driver/legs`）。
- **小程序行为**：`api.js:300` 的站点交接用 `?orderId=`（query，正确）；`handover.js` 用 `leg.id` 真值。
- **问题**：① "客户确认已送到指定站点"这个闭环动作在 App 里**永远失败**，`待客户操作(7)` 的订单卡在这一步无法进入 `待入池(8)`，用户只能去小程序操作；`ParcelScreen.confirmStation`(`:89-92`) 抛错后 `load()` 不执行，页面只显示错误条；② "收班"永远失败，`docked`/`Running.shiftId` 因为在 `ack` 之后赋值而不会置位（这反而是对的），结果是**位置上报服务无法停止**，司机的前台通知一直挂着"位置上报中"，电池与隐私观感都差。
- **修改方案**：① `confirmStationAction` 改 `Api.post(path, emptyMap(), mapOf("orderId" to orderId))`（`Api` 已支持 query 重载，`Repo.kt:457` 就是范例）；② `finish()` 改用真实 `legId`：从 `Repo.currentLeg()`（`/driver/current-leg`，`Repo.kt:869-871` 已有）拿 `legId`，拿不到就退化为"收班不调段接口，只停上报 + 提示由调度端收班"，**绝不拼假 id**；③ 加一条编译期/测试期守卫：`Repo.legAction` 参数类型从 `String` 改为 `Long`，让 `"shift-$id"` 无法通过编译（这类"字符串假装 id"的 bug 一次性根除）；④ 收班与停服务解耦：`finish()` 里先 `Running.shiftId = 0`/停 `DriverLocationService`（这是本机行为，不需要后端同意），再尝试段动作。
- **测试方案**：MockWebServer 断言 `/send/confirm-station-action?orderId=123`（query 中存在 orderId、body 为空）；断言 `/driver/leg/complete` body 的 `legId` 是数字且等于 `/driver/current-leg` 回读的 id；类型改 `Long` 后编译期即失败（保留这条作为回归证据）；Robolectric：点收班后断言 `DriverLocationService` 被 stop、`Prefs.driverShiftId == 0`。
- **本轮状态（⑧ 段状态机收口，2026-09-29）**：两个必 400 的写路径都已改掉，且 **② 按后端真实语义改，没有采纳本条原建议**。
  ① `Api.post` 加 query 重载（`core/net/Api.kt`：`post(path, body = emptyMap(), query = emptyMap())`），`Repo.confirmStationAction`(`:551-552`) 改为
  `Api.post("$T/send/confirm-station-action", emptyMap(), mapOf("orderId" to orderId))` —— 对上 `AppSendController.java:127-129` 的 `@RequestParam`。
  ② **收班不再打段接口**：`WorkbenchViewModel.finish`(`:267-…`) 改为 `Repo.arrive(shiftId, 末站 id)`，
  因为 `/driver/arrive` 只吃 `shiftId + stationId`，到末站时后端把执行记录收口为已完成（`DriverAppServiceImpl.java:914-920`）。
  本条原建议"从 `Repo.currentLeg()` 拿 `legId` 再发 `complete`"是**错的**：班次（客运域）与运输段（货运域）不是同一个 id 空间，
  拿段动作去收班次违反硬规则 1，且中间段 `complete` 会被服务端守卫拒绝（`DriverAppServiceImpl.java:1545-1546` `if (curSeq < maxSeq) throw LEG_TRANSITION_ILLEGAL`），故未采纳。
  末站 id 缺失时**不提交、不假装成功**，回执写"后端没给末站编号，收班提交不了——请到末站后用「到站」收口本班"。
  ⚠ **本条改法的已知代价（读后端源码读出来的，不是猜的）**：`/driver/arrive` 有**防跳站**
  （`DriverAppServiceImpl:900-911`：`targetSeq < currentSeq || targetSeq > currentSeq + 1` → `DRIVER_STATION_ORDER_ILLEGAL`），
  所以"没走到末站前一格就点收班"会被**服务端拒**——这是**故意的**：客户端不去猜"现在能不能收"（猜就是把本机导航视图当业务真值），
  错误如实进 `error`，下一次 `load()` 按后端班次状态把 `Running.shiftId` 复位（`WorkbenchScreen.kt:292-299`）。
  本条原方案 ④ 写"先 `Running.shiftId = 0`/停服务，再尝试段动作"，实现里保留的是"提交后才清本机位"，
  因为先清 `shiftId` 会让 `Repo.arrive` 的身份参数失去依据 —— 顺序按代码现状记，不按原方案记。
  ③ 判据（`tools/check_status_probes.sh`）：S-le `Repo.legAction` 调用点 = **2**（本条修完从 3 降到 2，多出来的那一点正是这个假 `legId`），
  修完 `:app:assembleDebug :app:testDebugUnitTest` BUILD SUCCESSFUL、189 tests / 0 failures。
  ④ **遗留**：`Repo.legAction` 形参仍是 `String`（本条 ③ 的编译期守卫未做，改签名会波及 §26/§27 两处调用点与既有测试，与"不推翻已完成代码"冲突，
  留到段动作接口类型化那一批）；MockWebServer / Robolectric 层仍缺（见 `AI_ITERATION_STATE.md` 的 `ENVIRONMENT_BLOCKED`），
  所以"query 里确实带上了 orderId"目前靠代码读证 + 探针计数，**没有网络往返级断言**。

---

## 40. `/send/page` 没有 status 过滤，App 的状态筛选条在寄货页静默失效

判定：`MISSING` + `UI_ONLY`（P1）

- **文件**：`app/src/main/java/com/shanxiang/buspost/ui/user/OrdersScreen.kt`、`data/repo/Repo.kt`
- **类 / 函数**：`OrdersViewModel.statusTabs/statusParam`(`:88-90`)、`fetch()`(`:124-146`，商城分支 `:126` 传 `status = statusParam`，寄货分支 `:135` **不传**)、`Repo.sendOrders`(`:433-434`，签名里有 `status: Int?` 并拼进 query)
- **当前行为**：界面上寄货来源也有"全部/待发货/已发货/已完成/已取消"筛选条，切到寄货时 `switchSource`(`:148-152`) 会把 `statusIndex` 复位为 0，但用户之后仍可在寄货下选中某个状态，此时请求不带 status，列表照旧返回全部数据 —— **筛选条选中了，列表没筛**。
- **后端真实行为**：`AppSendController.java:134-136` `page(@Valid PageParam pageParam)`，只有 `pageNo/pageSize`，`getMySendPage(userId, pageParam)` 不吃状态；而商城侧 `AppProductOrderController.java:48` 支持 status。
- **小程序行为**：`orders.js:155-157` 同样"切来源时状态筛选重置为全部"（注释还写明"寄货订单的状态语义与商城不同"），`:164` 的 `pageMySendOrders({pageNo,pageSize})` 不带 status —— 与后端一致，且小程序的 tabs 在寄货来源下会隐藏（需按此对齐）。
- **问题**：① 商城 tabs 的取值 `statusParam = statusIndex - 1` 恰好对上 `ProductOrderStatusEnum(0..3)` ✔，但寄货 tabs 的语义（待发货/已发货）对货运单根本不成立（货运是 0..13 另一套码，见 §18）；② Android 的 `Repo.sendOrders(status=…)` 形参给人"支持筛选"的错觉，是 §22 那类"参数写了但契约不支持"的翻版。
- **修改方案**：① 后端补 `@RequestParam(value="status", required=false) Integer status` 并在 `getMySendPage` 里加条件（`TransportOrderStatusEnum` 校验取值范围，非法值忽略而不是抛错）；② Android 在寄货来源下把 tabs 换成 §18 的货运状态集合，`fetch()` 传 `status = freightStatusParam`；③ 在后端参数落地之前，**先隐藏寄货 tabs**（客户端一行条件），避免"选了不生效"的中间态；④ 若决定不加后端参数，就把 `Repo.sendOrders` 的 `status` 形参删掉并在注释写明"后端不支持"——但不要保留假参数（这一条与"不删字段保兼容"不冲突，因为该参数从未生效）。
- **测试方案**：后端单测：`page(status=4)` 只返回已完成，`page(status=null)` 返回全部，`page(status=999)` 不报错按全部；Android：Robolectric 断言寄货来源下 tabs 不可见（或选中后 query 含 status）；断言商城来源 `statusParam` 与 `ProductOrderStatusEnum` 码表一致（0..3）。

---

## 41. 后端缺失端点：段异常上报（服务层已实现）、寄货取消 / 退款 / 评价

判定：`MISSING`（P1，后端侧）

- **文件**：`cargo-post-platform/.../driver/AppDriverController.java`、`.../send/AppSendController.java`、`.../driver/impl/DriverAppServiceImpl.java`
- **类 / 函数**：`AppDriverController` 的 9 个 `/leg/{action}`(`:163-224`，无 `exception`)；`AppSendController` 全部 11 个端点（`:118 create`、`:127 confirm-station-action`、`:134 page`、`:146 track`、`:160 legs`、`:191 topology`、`:482 stations`、`:489 route-preview`、`:498 quote`、`:517 reachability`、`:526 arrangements`，无 cancel/refund/review）；`DriverAppServiceImpl.java:1498` 的 `case "exception"`、`:1505 reportLegException`
- **当前行为**：三端都没有入口。`grep -rn "refund\|/review" cargo-post-platform/yudao-module-transport/.../controller/app/` 零命中。
- **后端真实行为**：异常处理能力**已经存在**（`forceLegStatus(…, EXCEPTION, remark)` + 订单置 `EXCEPTION(13)` + 事件记录 + 管理端/货主双向通知，`:1505-1516`），只是没有 controller 暴露；取消寄货单在管理端有 `TransportOrderStatusEnum.CANCELLED(5)` 与后台流转，App 侧无路径。
- **小程序行为**：`handover.js`/`api.js` 也没有异常、取消、退款、评价 —— 这是三方共同的能力缺口，不是 Android 落后。
- **问题**：需求文档里的"车辆故障/道路中断上报"（后端注释标 §108）无法从任何客户端触发，异常只能靠调度端人工发现；寄货单一旦下错，用户没有任何自助出口（只能打电话），而商城单反倒能取消（§31），两侧体验不一致。
- **修改方案**：① `AppDriverController` 加 `@PostMapping("/leg/exception")` → `driverAppService.legAction("exception", reqVO)`，`@Valid AppDriverLegActionReqVO` 复用（`remark` 已有），并在服务层校验 `remark` 非空（异常必须有原因）；② 寄货取消新增 `POST /send/cancel?orderId=`，仅允许 `status ∈ {0 CREATED, 6 待审核, 7 待客户操作, 8 待入池}`（入池后需调度端确认，避免司机已在途时用户自行取消），置 `CANCELLED(5)` + 事件 + 通知；③ 退款与评价属于业务未定义范围，**不要凭空造端点**，在 `DECISION_LOG.md` 记为待产品确认项；④ Android 侧对应入口见 §21。
- **测试方案**：后端：`legAction("exception")` 断言段=99、订单=13、事件表有 `ORDER_EXCEPTION`、通知 verify 两次；`/send/cancel` 对 `status=3 已发车` 断言被拒（业务异常码），对 `status=7` 断言置 5 且推通知；对不存在/他人订单断言越权异常。Android：入口存在性 + 请求形状测试（见 §21）。

---

## 42. 微信社交登录 / 订阅消息：Android 不适用，但保留的接口同样受 §22 影响

判定：`MISSING`（合理缺省，P2 记录项）

- **文件**：`app/src/main/java/com/shanxiang/buspost/data/repo/Repo.kt`
- **类 / 函数**：`subscribeTemplates`(`:1010-1016`)，注释已写明"安卓侧改走系统通知，保留接口以便对齐后端契约"
- **当前行为**：不发起社交登录；订阅模板接口保留但无 UI 调用点。
- **小程序行为**：`api.js:135 wechatMiniAppLogin`、`api.js:486-492`（订阅模板 / 文件上传）。
- **后端真实行为**：`/member/auth/social-login` 只在小程序 `code` 场景有意义；`AppFileController.java:34-38` 的裸 `String` 返回受 §22 影响。
- **问题**：不是缺陷，但要显式记录，否则后续审计会把"Android 没有社交登录"再报一遍。真实风险只有：若将来把 `unreadCount` 那套标量读法复制到这些接口，会重现 §22。
- **修改方案**：在 §53 的 REAL_COMPLETE/合理缺省清单里登记；`subscribeTemplates` 若确认无人调用，标注 `// 未接线：系统通知替代` 或直接移除（与"避免半成品实现"一致）——本轮先只标注，不删（遵守"不要直接删除文件/字段"的保守原则）。
- **测试方案**：契约测试断言 `/member/auth/social-login` 不在 Android 的调用清单里（防止误接）；`grep -rn "subscribeTemplates" app/src/main/java` 只允许出现在 `Repo.kt` 定义处。

---

## 43. 写路径没有重入保护：基类 `work()` 不防双击，靠后端幂等兜一半

判定：`REAL_PARTIAL`（P1）

- **文件**：`app/src/main/java/com/shanxiang/buspost/ui/common/ScreenViewModel.kt`、`ui/user/SendScreen.kt`、`ui/user/ProductDetailScreen.kt`（等 `work {}` 提交类页面）
- **类 / 函数**：`ScreenViewModel.work`(`:35-47`，`viewModelScope.launch { … block() … }`，**无 `inFlight` 判定**，`finally { loading = false }`)；`SendScreen` 的提交块(`:300-330`) 全程没有 `busy` 标志（`grep -n busy ui/user/SendScreen.kt` 零命中）
- **当前行为**：用户连点两次"提交寄货"就发两个 `POST /send/create`。司机端几个屏幕自己加了 `busy`（`RoutesScreen.kt:99`、`HandoverScreen.kt:103`、`WorkbenchScreen.kt:421`），纪律不统一。
- **后端真实行为**：`AppSendController.java:118-120` 挂了 `@Idempotent(timeout = 10, timeUnit = SECONDS, keyResolver = UserIdempotentKeyResolver.class, message = "订单正在创建中，请勿重复提交")` —— 10 秒内同人第二次创建会被拦，返回业务异常；`Api.kt:253` `bizCode != 0` → 抛 `ApiException` → `work` 的 catch 把它显示成错误条。**超过 10 秒的重复提交后端不拦**，会产生两张真实运单。`/product-order/create` 一类的写接口同样没有前端防重。
- **小程序行为**：`send.js`/下单流程有 `submitting` 标志 + 按钮 disabled（小程序页面普遍有 `data.submitting`）。
- **问题**：① 依赖服务端幂等窗口做 UX 是脆弱的：窗口外双击 = 重复下单 = 重复取件码与重复费用；② 错误条显示"订单正在创建中，请勿重复提交"时，第一次其实**已成功**，用户以为没提交，再点第三次，形成连环；③ `loading` 在第一个请求 `finally` 里就置 false，按钮瞬间恢复可点，等于没有保护。
- **修改方案**：① 在 `ScreenViewModel` 加 `private var inFlight = false`，`work` 里 `if (inFlight) return` + `try/finally` 复位（一次性覆盖全部页面，比逐屏补 `busy` 更不易漏）；② 提交类按钮额外用 `enabled = !vm.loading && !vm.submitted` 且在**成功回读**（`SendScreen:317-324` 已经回读列表）之后才允许再次操作；③ `errorTextOf` 对幂等冲突（后端 message 含"正在创建中"）特殊处理为"上一次提交已受理，正在确认结果"并触发一次列表回读，而不是让用户重投；④ 后端保持现状（不改幂等窗口，属既有正确设计）。
- **测试方案**：Robolectric：连点 5 次提交按钮，`MockWebServer` 断言只收到 1 条 `/send/create`；后端测试：10 秒内二次创建返回幂等冲突码、窗口外二次创建生成两条不同 `orderNo`（记录真实行为，供文案设计依据）；`errorTextOf(幂等冲突)` 单测断言文案不含"请重试"。

---

## 44. 真地图组件 `BusMap` 已实现但零调用点：全站还在用自绘 `RouteMap`

判定：`MISSING`（能力存在但未接线，P1）

- **文件**：`app/src/main/java/com/shanxiang/buspost/ui/common/BusMap.kt`
- **类 / 函数**：`BusMap`(`:88`)，内含 `MapView` 生命周期对齐(`:101-135`)、`AMap`/`CameraUpdateFactory`/`PolylineOptions`/`MarkerOptions`(`:18-25`)；文件头注释(`:85`)写明"退回自绘 RouteMap（MapCanvas.kt），保证地图不可用不会变成空白"
- **当前行为**：`grep -rn "BusMap(" app/src/main/java` 只有定义行 —— 没有任何屏幕用它。实际渲染线网的是 `MapCanvas.kt:55 RouteMap`（Canvas 自绘），调用点为 `TraceView.kt:113-120`、`BusScreen.kt:403-406`、`:574-576`、`DriverNavScreen.kt:258`。
- **小程序行为**：公交与路线页用原生 `<map>`（`pages/bus/*`、`routes.js:122-124`，但它的线是站点直连，属 §16 违规，不作为目标）。
- **后端真实行为**：与前端组件无关；后端只保证 `polyline + routeProvider + routeAvailable + routeComplete`（§54）。
- **问题**：① 高德 SDK 依赖（`app/build.gradle.kts:215 com.amap.api:3dmap:10.0.600`）与 `com.amap.api.v2.apikey`（`AndroidManifest.xml:55-58`）都在包里，却没有真地图在用 → APK 体积与隐私声明（SDK 采集）都付了成本却没得到能力；② 自绘 `RouteMap` 没有底图，"这条路沿着哪条江哪座山"完全看不出来，山区线路的司机对示意 Canvas 的阅读成本明显高于真地图；③ `BusMap` 的 `MapView` 生命周期代码没经过真机验证就长期闲置，一旦接线就是首次上真机。
- **修改方案**：① 先接线**一个**只读屏幕（P1-1 `DriverMissionMap`）验证生命周期与 key 有效性，通过后再把公交详情页的地图换成 `BusMap`（保留 `RouteMap` 作为 SDK 不可用/key 未配时的降级，这一降级逻辑注释里已经设计好）；② `RouteMap` 与 `BusMap` 二选一由一个 `rememberMapAvailability()` 判定（`BuildConfig.AMAP_KEY` 非空 + `AMapUtils.checkKey` 通过 + 非模拟器）；③ 隐私合规补一条：`BusMap` 首次上屏前必须已展示过 SDK 隐私协议同意（高德 SDK 要求 `MapsInitializer.updatePrivacyShow/updatePrivacyAgree`，**该 API 名称需按 §57 的先解包核对流程确认后再写**）；④ 不得把 key 硬编码进源码，沿用 `@string/amap_key` 单一真值。
- **测试方案**：① 依赖核对：解包 aar 确认 `com.amap.api.maps.MapView` 与隐私接口的真实签名（写进 `THIRD_PARTY_NOTICES.md`）；② Robolectric 断言 `AMAP_KEY` 为空时渲染 `RouteMap` 且不创建 `MapView`；③ 真机（`ENVIRONMENT_BLOCKED` 期间记为未验证）：公交详情页出现高德底图、`onResume/onPause/onDestroy` 转发后不黑屏、logcat 无 key 校验失败；④ 体积对照：记录接线前后 `app-debug.apk` 大小与 `DEX` 方法数，确认没有重复打包。

## 45. 位置上报有退避但没有队列：失败期间的点永久丢失

判定：`REAL_PARTIAL`（P1）→ **Android 侧已修**（P1-3 ⑦：合并语义 + 45 秒新鲜度闸 + 丢点可见；**未做重放队列，理由见"本轮状态"**）

- **文件**：`app/src/main/java/com/shanxiang/buspost/driver/DriverLocationService.kt`、`data/repo/Repo.kt`
- **类 / 函数**：`reportOnce`(`:85-123`)、`observe`(`:61-69`)、`heartbeat`(`:71-78`)；`Repo.reportLocation`(`:966-978`)
- **当前行为**：`observe` 每 5 秒更新内存里的 `last`；`heartbeat` 按 `Prefs.reportIntervalSec`(3..60 s) 上报一次，失败按 `base * 2^min(streak,3)` 退避、上限 `MAX_INTERVAL_MS`，并把失败次数写进前台通知（这部分做得对）。但**待发点只存内存单槽位** `last`：一次失败后新定位覆盖旧值，退避期间产生的所有点直接丢弃；进程被杀也不补报。
- **后端真实行为**：`/driver/location`(`AppDriverController.java:128-132`)；`AppDriverLocationReqVO` 收 `driverId/shiftId/longitude/latitude/speedKmh`。
  ~~`shiftId` 必带——`Repo.kt:999-1000` 注释已写明~~ → **该注释是陈旧的**：`shiftId` 已无 `@NotNull`，服务层按当前活跃段兜底（见"本轮状态"最后一节）。
  服务端把它当"当前/最新位置"用于监控与走廊匹配（`MonitoringServiceImpl`、`RouteCorridorService`），**没有补报历史点的语义**，所以丢点在服务端是不可恢复的轨迹缺口。
- **小程序行为**：`workbench.js:592` 有 `REAL/模拟` 来源区分，失败同样不入队。
- **问题**：① 隧道/山区无信号 20 分钟后恢复，管理端轨迹上是一段直线跳跃（前端画图会连两点，看起来像"抄近路"），这正是"宁可没有，不许看起来像"的反例——虽然这条线是管理端画的，但缺口源于客户端；② 上报的坐标还是 WGS-84（§17），与折线不同系，走廊匹配偏差被放大；③ `speedKmh` 只在有 fix 时带，退避期间上报节奏与定位节奏脱钩。
- **修改方案**：① 在 `DriverLocationService` 内加一个**有界环形队列**（例如最多 200 条，落 `Room`/文件皆可，优先文件以免加依赖），每条含 `lat/lng/speed/locateTime/provider` 与 §17 换算后的 GCJ-02 值；上报成功才出队，失败保留并按序重试；② 队满时**丢最旧**并在前台通知显示"当前队列 N 条，已丢弃 M 条"——不许静默丢；③ 后端保持"最新位置"语义不动（不重设计后端），但客户端在报文里带 `locateTime`（若 `AppDriverLocationReqVO` 无此字段，则**先查 VO**，没有就不塞未知字段，改为在补报时按时间顺序发送，让服务端自然收敛到最后一条，同时在 §49 契约项里登记为待后端确认）；④ 与 §17 一起修坐标，避免队列里存了两套系的点。
- **测试方案**：Robolectric/Paparazzi 之外的 JVM 测试：脚本化"上 3 点、第 2 次请求失败"，断言队列长度变化与最终三次上报的顺序（mock server 收到的坐标序列 = 产生序列）；断言队满时通知文案含"已丢弃"；断言服务 `onDestroy` 后队列持久化仍在（重进 App 能续报）；真机项记入 §57（弱信号场景需实车验证）。
- **本轮状态**：**已修，但实现与原方案 ① 不同 —— 队列被后端事实否决**（P1-3 ⑦，2026-09-29，D-019）。
  - **为什么不做重放队列**（三条后端事实，逐条读码核对）：
    ① `AppDriverLocationReqVO.java` 全文只有 `driverId / shiftId / longitude / latitude / speedKmh`，
       **没有任何客户端时间字段** —— 原方案 ③ 的"报文里带 `locateTime`"在这里只能塞未知字段，`Api.rawExecute` 也不会替我发明它；
    ② `DriverAppServiceImpl.reportLocation` 给每条上报盖 `LocalDateTime.now()`（服务器时间），
       并按上报次数**追加** `VehicleLocationTrackDO` 轨迹行 —— 于是重放一个 3 分钟前隧道里的点，
       服务端记录的是"司机**此刻**在这个坐标"。这不是补报，是**用真实坐标制造虚假的实时性**，
       正撞用户红线"宁可没有上报，不允许假上报"；
    ③ 原方案自己写了"若 VO 无此字段则**先查 VO**，没有就不塞未知字段" —— 查了，确实没有，所以按该条的分支停止塞字段；
       继续做队列就只能"按时间顺序重放，让服务端自然收敛到最后一条"，那依然会在轨迹表里留下若干条**时间全错**的历史行。
  - **实际实现（合并语义 + 新鲜度闸 + 丢点可见）**：新增 `driver/LocationReportPolicy.kt`（纯函数，JVM 可测）：
    `intervalMs(baseSec, failStreak)` 保留原有 3..60 s 区间与 `base * 2^min(streak,3)`、上限 60 s（这部分原方案说"做得对"，未动）；
    `decide(latest, nowMs, driverBound, shiftId)` 产出 `Send(fix, shiftId?)` 或 `Hold(原因)`；
    `ReportLedger` 只做两件事 —— 未 ack 期间每来一个新点 `superseded += 1`（**取代**，不是排队），ack 后 `pendingFixAtMs` 归零。
    `DriverLocationService.tick()` 每轮只发**当前最新那一个** fix，前台通知与工作台都打印"已丢弃 N 个未确认点"
    （判据 **L-g = 1** 钉的是**通知侧那一个拼装点** `droppedSuffix()`，工作台 InfoRow 不在计数内，见 §55 I）。
  - **新增的第四件事（原方案没有）**：`MAX_FIX_AGE_MS = 45_000` 新鲜度闸。5 秒采样 × 9 次连续拿不到 → `Hold`
    并写明"定位信号已中断 N 秒，暂停上报（不拿过期点冒充当前位置）"。这条是本条"问题"段第 ③ 点
    （上报节奏与定位节奏脱钩）的真正解法：**过期的点宁可不发**，而不是发出去让服务端给它盖一个新章。
  - **代价如实记（本条没有"解决"的部分）**：弱网期间的中间点**不可恢复地丢失**，管理端轨迹缺口仍在；
    进程被杀后不补报，恢复后从最新 fix 重新开始；`superseded` 是**本机内存计数**，重启归零、不入后端。
    与原方案相比少了一个能力（续报），多了一个保证（不制造虚假实时性）。这个取舍记在 D-019，不写成"队列已完成"。
  - **顺带修掉本条第 ② 点**：上报的坐标现在已是 GCJ-02（§17），队列里"存了两套系的点"这个风险不复存在 ——
    因为不存队列，每个 `TrackFix` 出厂即 GCJ-02（`Locator.trackFixes` → `fixOf` → `gcjOf`）。
  - **`shiftId` 硬阻塞已解除（本条"后端真实行为"段的旧结论作废）**：原文写"`shiftId` 必带——`Repo.kt:1428-1429` 注释已写明"，
    那条**注释是陈旧的**：后端已把 `shiftId` 改为可选（`AppDriverLocationReqVO` 无 `@NotNull`，
    服务层 `currentLegShiftId()` 按当前活跃段兜底）。Android 侧原来 `Running.shiftId <= 0` 就一句不发，
    实际后果是**运输段模式（无班次）的司机永远不上报**，监控上看是车凭空消失。
    现 `reportLocation(shiftId: Long?, …)` 在 `<= 0` 时**不带该键**（`Api.rawExecute` 剔空值），
    `LocationReportPolicy.shiftNote()` 把语义讲给司机："上报未带班次：车辆位置照常更新，班次归属由后端按当前活跃段判定"。
    判据 **L-d = 0**（旧拦停文案"当前没有在途班次，发车后再上报位置"不得复现）。
  - **测试方案偏差**：原方案三条只有第一条的**等价物**做了 —— `LocationReportPolicyTest` 15 例
    （三种 `Hold`、`Send` 带/不带班次、"重试只发最新点"、区间 3..60、退避 20/40/60/60、账本 4 例）
    + `DriverStateTest` 两条报文用例（无班次仍发且 body 键集恰为 `{driverId, longitude, latitude, speedKmh}`；
    反向钉 `locateTime/clientTime/gpsTime/reportTime/time` 为 0，判据 **L-c = 0**）。
    "断言 `onDestroy` 后队列持久化仍在"**故意不做**（无队列）；Robolectric 那条没有框架，`fixOf` 与前台服务重试环
    仍是 `ENVIRONMENT_BLOCKED`（§57）。
  - **另发现两处与上报链路相邻的真 bug**：`Locator.onceFix` 在所有 Provider 关闭时会随 flow 正常结束而抛
    `NoSuchElementException`（已改 `runCatching{…}.getOrNull()`，超时/关闭统一退回旧缓存或 `UNAVAILABLE`）；
    `observe` 原先一次注册死了就再也不订（现 `while (isActive)` + `delay(RESTART_DELAY_MS)` 重试并在通知里写明原因）。

---

---

## 46. `DemoData` 造道路几何、造车道、造运单号与取件码

判定：`DEMO_ONLY`（P0，硬规则 5/11 直接冲突）

- **文件**：`app/src/main/java/com/shanxiang/buspost/data/repo/DemoData.kt`
- **类 / 函数**：`lineOf`(`:29-46`)、`guideOf` 的 steps/lanes 构造(`:344-375`)、`/driver/route` 演示回执(`:698-703`，含 `"track" to polylineJson(...)`、`"nodes" to nodesJson("MP20260928001", 5, "渝A·8K231", "K1-0830")`)、`/auth/login` 演示回执(`:705-707`，`userId 10086 / token "demo-android-token" / refreshToken "demo-refresh-token"`)、`/send/create` 演示回执(`:710-712`)
- **当前行为**：① `lineOf` 在相邻两站之间**插两个带侧向偏移的伪点**，注释写"模拟山路弯曲"——这是纯生成的假道路几何（比直线更坏，因为它看起来像真路）；② 逐转向 steps 里给每个 `icon` 配 `lanes`（"左转专用/公交专用/应急车道"）与 `recommendedLane`，`distanceMeters` 用 `GeoMath.distance`（Haversine）算；③ 造 `MP2026…` 运单号、`渝A·8K231` 车牌、`K1-0830` 班次、`6174` 取件码；④ 造 token 与 refreshToken。
- **后端真实行为**：真实几何只来自 `algorithm/app/routing`（AMAP → GraphHopper/RoadGraph → `available=false`）与 `RealRoadProviders` 白名单标签；`AppDriverRouteRespVO` **无 steps/lanes**；运单号/取件码由服务端生成并落库；登录 token 由 `member/auth` 签发。
- **小程序行为**：小程序没有离线演示回落，所有号码都是后端的。
- **问题**：`DEMO_ALLOWED` 是唯一的防线（`app/build.gradle.kts:92` debug 可读工程外配置但**默认 false**、`:104` release 硬编码 `false`；闸门读点是 `Api.kt:274`，回落构造入口 `Api.kt:457-460` 在非演示构建里直接 `throw IllegalStateException("生产构建禁止演示数据")`）。**机制是对的**，但只要 debug 包流出去（现在 `app-debug.apk` 就在仓库根，见 §57），这些假几何/假车道/假取件码就会以"真数据的样子"出现在屏幕上。⚠ 原行的两处引用是旧的（`:91`/`:103`/`Api.kt:184`），㊶ 现读更正；旧句里"§47 的横幅又被注释掉了"那半属 §47 在册项，不随本行改。
- **修改方案**：① `lineOf` 的侧向偏移点**删掉**：演示线路要么用后端缓存过的真实折线（若有），要么只给站点顺序、`polyline` 留空，让 `RouteUnavailableState` 正常显示；② `guideOf` 不再造 `lanes/recommendedLane`，`distanceMeters` 也不由 Haversine 造——演示态就直接显示"演示：无逐转向数据"；③ 演示的运单号/取件码统一加 `DEMO-` 前缀（例如 `DEMO-MP2026…`），让人一眼不可能误认，同时保留可解析结构；④ 演示 token 前缀已是 `demo-` ✔（`Repo.saveSession:118` 与 `LoginViewModel:115` 都按前缀判定，这条纪律要留在 §56 清单里）；⑤ 演示数据只在 `BuildConfig.DEMO_ALLOWED` 为真时**编进** APK（用 `sourceSets` 排除而非运行时判断更佳）；⑥ 上述改动全部要过 `RealRoadRouteValidator` 的既有测试，证明"演示几何缺席"不会让地图崩成空白。
- **测试方案**：① 单测：`RealRoadRouteValidatorTest` 断言 `lineOf` 不再产出侧向偏移点（对同一站序列，产物点数 == 站数）；② 契约测试：`DemoData` 里不存在键名 `lanes`/`recommendedLane`（grep 断言，防回潮）；③ JVM 测试：所有演示运单号断言以 `DEMO-` 开头；④ 构建测试：`DEMO_ALLOWED=false` 的构建里 `DemoData` 不可达（`Api.kt:184` 抛错路径有单测覆盖）；⑤ 自检 grep 见 §56。

---

## 47. `DemoNote` 函数体首行 `return`：唯一的数据来源提示被注释掉

判定：`DEMO_ONLY` + 诚实性违规（P0，与 §30/§46 同一条链）

- **文件**：`app/src/main/java/com/shanxiang/buspost/ui/common/Bits.kt`
- **类 / 函数**：`DemoNote`(`:43-49`)，实现体首行是无条件 `return`，下面的横幅 UI 整段落在 `/* … */` 里
- **当前行为**：所有页面里的 `DemoNote(vm.demo, text = …)` 调用（`TraceScreen.kt:99`、`OrderDetailScreen.kt:99`、`BusScreen`、`DriverNavScreen` 等）都渲染为空；文件头注释写明"根据UI复刻方案，演示数据横幅已移除……如需重新启用，取消下方 return 语句即可"。
- **后端真实行为**：后端不关心前端提示；但 `AppBusNearbyRespVO.located/locationLevel/dataSource`、`AppDriverRouteRespVO.routeAvailable/routeProvider` 这套**自报来源**的字段，正是为了让前端能如实标注——工程其他部分（`RouteMap` 的 subtitle、`ReceiptSheet`）已经在用这些字段做标注，唯独演示态的总提示被关了。
- **小程序行为**：小程序无演示态，无需对应。
- **问题**：`DemoNote` 是"数据来源诚实"这条硬规则的**唯一 UI 出口**。关掉它之后，`ReceiptSheet` 里的"演示模式：未提交后端"小字成为仅剩提示，而它只出现在部分写操作之后；只读页面（公交、追踪、订单详情）在演示构建里与真实构建**长得一模一样**。§46 的假几何 + 本条的零提示 = 用户无从分辨。此外注释把责任推给"UI 复刻方案"，但复刻方案针对的是视觉风格，不是数据真实性标注——这是一个被误读的需求导致的合规回退，必须纠正记录。
- **修改方案**：① 首行 `return` 改为 `if (!demo || !BuildConfig.DEMO_ALLOWED) return`，真实构建天然不显示（视觉诉求已满足），演示构建必显示；② 样式收敛为不干扰复刻视觉的细条（12 sp、页面顶部、无动画），或按"UI 复刻方案"要求改成非黄色的中性提示——但**不得为零**；③ 在 release 构建里 `DEMO_ALLOWED=false`，`DemoNote` 因此恒不显示，与 §46 第 ⑤ 点一致，不引入额外风险；④ 把这条判定写进 `DECISION_LOG.md`（覆盖此前"横幅已移除"的记录，说明真实诉求是"样式收敛"而非"取消提示"）。
- **测试方案**：Robolectric 两支：`demo=true` → 断言存在提示文本节点（当前失败，作为回归基线）；`demo=false` → 断言不存在；编译期断言：`BuildConfig.DEBUG=false` 的构建产物里 grep 不到"演示数据"字符串（证明真实包不带提示通道，反向验证 DEMO_ALLOWED 关严）。

---

## 48. 参考实现（小程序）自身违规清单：明确"不复刻"

这些点在小程序里存在，Android **不得照抄**；本审计一律以后端为准（硬规则："小程序是 UX 参考，不是真值来源"）。

| # | 小程序位置 | 它的做法 | Android 现状 | 判定与依据 |
|---|---|---|---|---|
| 1 | `pages/driver/routes/routes.js:122-124` | 地图线 = 站点直连 | `MapCanvas` 无可信折线 → `RouteUnavailableState`(`MapCanvas.kt:67`) | Android 正确，保持（`REAL_COMPLETE`） |
| 2 | `routes.js:186-188` | `currentStop` 缺失时用 `(now-departure)/duration × (stopCount-1)` 用墙钟猜车位 | 用后端 `currentLongitude/Latitude`(`AppDriverRouteRespVO:59-61`) | Android 正确，保持 |
| 3 | `workbench.js:24-31` + `:740/:742/:759/:780` | 下一站距离与进度用客户端 Haversine | 混合：卡片用后端道路值(`:81`)，`canArrive`/`distanceToTarget` 仍用 Haversine | **两边都不对**，见 §32 |
| 4 | `workbench.js:1027-1030` | `setTimeout(…, 1500)` 自行 `continueToNextStation()`，不发请求 | `DriverNavScreen` 的 `delay(4000)` 自动步进是同一类问题 | **两边都违规**，见 §24 |
| 5 | `workbench.js:592` | 上报有 `REAL/模拟` 来源二分 | 只报真实 GPS，无模拟分支（`:93` 未绑定/无班次时不发） | Android 正确，保持 |
| 6 | `handover.js:131` | 提交后 `await loadAll()` 整表回读 | `RoutesScreen`/`HandoverScreen` 本机改写 | 小程序正确，Android 应照抄它 → §26/§27 |
| 7 | `parcel.js:15-25` | 状态表按**枚举码** keyed，且优先 `statusName` | `freightStatusLabel` 按顺序编号 → 错位 | 小程序正确，Android 应照抄它 → §18 |
| 8 | `pages/bus/index.js:220/254` | 无坐标时传 `district` 兜底并标注"根据X区展示" | 四个调用点都不传 | 小程序正确 → §37 |
| 9 | `api.js:417/486-492` | 裸标量响应直接当数字/URL 用 | `{"v"}` 信封读不到 → 恒 0/恒空 | 小程序正确 → §22 |

> 结论：三方对齐 ≠ 向小程序看齐。第 1、2、5、6、7、8、9 条上小程序是对的（Android 落后或已对齐）；第 3、4 条上小程序是错的（Android 也错，需按后端修）。

---

## 49. 根因：字段命名三方不统一，且没有契约守卫

`CONTRACT_MISMATCH` 13 条里有 11 条是同一个模式：**Android 的 `multiStr("a","b","c")` 候选名列表里没有后端真实键名**（§25/§34/§35/§36/§37/§38/§40 等），失败方式是"取默认值"而不是"报错"，所以永远不会有人发现。

- **表现**：同一个业务概念在三方有三个名字 —— 车牌 `plateNo`(后端) / `plate`+`vehiclePlate`(Android) / `plate`(小程序自造变量)；站名 `fromStationName`(后端) / `fromStation`(Android)；距离 `distanceKm`(后端，公里) / `distanceMeters`(Android 变量名，米) / `distance`(候选名)。
- **为什么没暴露**：`multiStr/multiNum` 的默认值语义（空串/0）让"字段不存在"和"字段就是 0"不可区分；`Payload.total`(`Api.kt:94`)、`etaMinutes`、`distanceMeters` 全踩过这个坑。
- **建议措施**（三条，都是客户端/工程侧，不改后端）：
  1. **每个 RespVO 一份 Android 侧的样例报文 fixture**（按后端字段名手写，放 `src/test/resources/contract/`），parse 函数逐字段断言非默认值——上面的 §18/§25/§34/§35/§36 的测试方案都属于这一类，集中管理；
  2. **未知字段告警**：`Payload` 解析处加一个 debug-only 的"候选名全未命中"日志（`Log.w("contract", "$path 未命中 $keys，实际键=${o.keySet()}")`），真机联调时一眼看出漂移；release 不输出（避免泄漏字段结构，见 §50）；
  3. **命名收敛**：新模型字段一律以后端字段名为准（可另设 UI 友好名，但 parse 候选名**第一个必须是后端真名**），把这条写进 `AGENTS.md`/本文档 §56 的自检项，避免下一轮再造 `plate|vehiclePlate|plateNumber` 这种"猜三连"。
- **测试方案**：加一条守卫测试：遍历 `contract/` 下所有 fixture，对每个 parse 结果断言"没有一个字段是靠默认值蒙过去的"（做法：fixture 里每个字段给唯一可辨识值，断言逐字段相等）；这条测试会一次性把 §25/§34/§35/§36 全暴露出来，是本轮最高性价比的回归资产。

---

## 50. 构建与会话安全现状（复核通过项 + 两项待办）

判定：`REAL_COMPLETE`（复核）+ 待办 2 项

**已复核通过（本轮实际执行）**：

- `app/build.gradle.kts:164-172`：release 若 baseUrl 主机是 `localhost/127.0.0.1/10.0.2.2/::1` → **构建直接 error**（`:167-168`），非 HTTPS 同样 error（`:170-171`），落实"正式包不得指向本机"；`:72` 未配地址时 BASE_URL 为空串（debug 可编译、App 启动报"未配置后端地址"）。注意 `--include=*.kt` 类过滤会**完全跳过** `.kts` 文件，验证这条必须单独 grep（见 §55 E）。
- `:103` release 强制 `DEMO_ALLOWED=false`；`Api.kt:184` 对非演示构建的回落路径 `throw`（两道门）。
- `app/src/release/AndroidManifest.xml:10` `usesCleartextTraffic="false"`，debug 才允许明文（`:9`）——落实"release 关闭 cleartext HTTP"。
- 签名文件在源码树之外：`find . -name "*.jks" -o -name "*.keystore"` 零命中；`:44-50` 默认读同级目录 `../buspost-signing-backup/keystore.properties`，`.gitignore:11/:16` 已排除 `local.properties`、`keystore.properties`。
- 高德 key 不进源码：`AndroidManifest.xml:55-58` 用 `@string/amap_key`，由 `build.gradle.kts:76 resValue("string","amap_key", amapKey)` 在构建期注入，`grep -rn "amap_key" app/src/` 只命中 manifest ✔。
- 源码内无真实凭证：`grep -rniE "password\s*=\s*\"|secret|token\s*=\s*\"[0-9a-f]{20,}" app/src/main` 仅命中 `MinePages.kt:94 password = ""`（空串，表单初值）；`DemoData` 的 token 是 `demo-android-token` 假值（受 §46 第 ④ 点约束保留前缀纪律）。
- 会话清理：`Prefs.clearSession`(`:150-164`) 无条件清 `token/refreshToken/userId/mobile/nickname/avatar/gender/role/driverId/driverName/vehiclePlate/driverShiftId`；`Repo.logout`(`:182-186`) 先尽力通知后端再无条件清本地（正确）。

**待办 2 项**：

1. `app-debug.apk`（63 MB）目前在**仓库根目录**：APK 里有 `AndroidManifest`、类结构与可能的字符串常量，属可反编译资产，且 debug 包带 `DEMO_ALLOWED=true`（含 §46 的假几何）。方案：移入 `build/`（已 gitignore）或产物目录，正式交付走未签名 release + 外部签名。→ 记入 §57 与 §60。
2. `Api` 目前不打日志（`grep HttpLoggingInterceptor` 零命中），这是对的（避免 token/报文进 logcat）。但 §49 建议 2 的"候选名未命中"日志必须是 `BuildConfig.DEBUG` 门控且只打印键名、不打印值——写实现时按这条约束执行，别顺手引入全量 body 日志。

**测试方案**：把上面 7 条 grep/find 固化成 `scripts/audit-security.sh`（本轮已具备全部命令），在 §55 的执行清单里作为一步；新增断言：`aapt dump badging app-release-unsigned.apk | grep -i cleartext` 不得为 true。

---

## 51. "定位不可用 / 权限被拒 / 精度很差"三种状态只在首页区分，公交页与小组件不区分

判定：`REAL_PARTIAL`（P1）

- **文件**：`app/src/main/java/com/shanxiang/buspost/ui/user/BusScreen.kt`、`widget/NextBusWidgetProvider.kt`、`ui/user/HomeScreen.kt`
- **类 / 函数**：`Locator.LocFix`(`Locator.kt:113-137` 已带 `denied/fromCache/accuracy`)、`HomeScreen`(`:107-108` 四态注释、`:595`+`:622` 渲染 `fix.denied`)；`BusScreen.kt:122-123` `val geo = Locator.once(context); me = geo`（丢掉 `LocFix`，只留可空的 `Geo`）
- **当前行为**：公交页用 `Locator.once()`（返回 `Geo?`），把"用户拒绝授权""室内无信号""精度 3000 米"三种情况统一折叠成 `me == null`，随后按"附近没有公交/暂无线路"渲染；小组件更是永远 `geo = null`（§37）。
- **后端真实行为**：`AppBusNearbyRespVO.located`(`:45`) 与 `locationLevel`(`:42` PRECISE/DISTRICT/UNKNOWN) 是后端**明确告知**前端"我这次是按什么口径给你筛的"；`AppBusServiceImpl.java:257` 生成这三个级别。
- **小程序行为**：`pages/bus/index.js:254` `district ? '根据${district}展示' : '定位不可用'`，且 `utils/location.js` 的 `poorAccuracy` 有独立提示。
- **问题**：违反"不许把失败渲染成空"这条纪律的**残留**位置：`BusScreen` 的 `Repo.nearbyBuses` 失败已经有 error 通道（对），但"定位拿不到"没有通道 —— 用户以为这地方没公交，实际是没给权限。这与 §37（不传 district）是同一个缺口的两个半边：先分清状态，才知道该走 district 兜底。
- **修改方案**：① `BusScreen` 改调 `Locator.onceFix(context, force=…)` 并把 `LocFix` 存进 VM（与 `HomeScreen` 同一模式，抽成共享的 `LocationState` 渲染条）；② `denied →` "定位权限未开启，去设置"按钮；`UNAVAILABLE →` "当前定位不可用，可按区县展示"（触发 §37 的 district 路径）；`fromCache || accuracy > 1000 →` "定位精度 X 米，结果可能不准"；③ 解析并展示后端 `located/locationLevel`（`NearbyResult` 已有字段则复用，没有就加只读字段——只加不减）；④ 小组件用 `Prefs` 里缓存的 district/上次坐标，并在文案里注明数据来源与时间。
- **测试方案**：Robolectric 三支：`LocFix.DENIED` 断言出现"权限未开启"且列表区不显示"暂无"；`UNAVAILABLE + district` 断言走 district 请求（MockWebServer 校验 query）；`accuracy=3000` 断言出现"精度"提示。小组件：断言无坐标时用缓存 district 且渲染含"上次更新 HH:mm"。

---

## 52. 未读角标有两个写入源，其中一个恒 0：角标"时灵时不灵"

判定：`CONTRACT_MISMATCH`（P1，§22 的连带后果）

- **文件**：`app/src/main/java/com/shanxiang/buspost/notify/AlertEngine.kt`、`ui/user/ParcelScreen.kt`、`ui/user/NotificationScreen.kt`
- **类 / 函数**（2026-10-18 按现状重写：这一格原来是"三处直写 `AppState.unread.value`、谁最后写谁赢"，§52 那轮已经收成一个合并器）：写入唯一入口 `AppState.reportUserUnread`(`AppState.kt:223`) 与 `AppState.reportDriverUnread`(`AppState.kt:228`)，两者都走 `reportUnread`(`AppState.kt:238`)；调用点 `AlertEngine.kt:40`、`ParcelScreen.kt:100`、`NotificationScreen.kt:109`、`DriverMessagesScreen.kt:101`。
- **当前行为**：`AppState.unread` / `AppState.driverUnread` 各只有一个写入函数。合并规则是 `serverCount`（后端 `unread-count`，全量真值）与 `listCount`（本页列表数出的未读）取 **max**，两个都读不到时返回 null ⇒ **保持原值**，"这次没读到"绝不写成 0（红点自己闪没就是这么来的）；不与上一次取 max，否则读完消息角标降不下来。`ParcelScreen.kt:100` 只给 `serverCount`、`listCount` 传 null，是本机唯一"标量单独说话"的一处 —— 它依赖 §22 那个裸 `Long` 接口，后端返回 0 时角标就是 0（这一格仍未消除，见 §22）。
- **后端真实行为**：`/notification/unread-count` 给裸 `Long`（§22）；`/notification/page` 给 `PageResult{list,total}`，`total` 是**全量未读+已读**总数，不能当未读用。
- **小程序行为**：`api.js:417` 用裸数字，配合 `handle401` 重放，值是对的。
- **问题**：谁最后写谁赢 —— 用户从首页进消息中心（`NotificationScreen` 写 0）再退回，角标消失；20 秒轮询的 `AlertEngine` 又把它写回真实值。表现是"红点会自己闪没"，很难复现，因此一直没人当 bug 报。司机侧 `AppState.driverUnread` 同样两条写入路径（`AlertEngine:36` 列表计数 vs `Repo:1003` 标量 0）。
- **修改方案**：① 统一"未读只有一个来源"：把 `AppState.unread`/`driverUnread` 的写入集中到 `AlertEngine`（列表计数法），其他页面**只读不写**；② 修 §22 的标量出口后，让 `Repo.unreadCount()` 成为可信的**补充**（首屏冷启动时先取标量接口，列表到手后以列表计数覆盖，二者取 max 而不是取最后写入）；③ 若保留列表计数为主，注意分页 20 条只能数出"前 20 条里的未读"，需要在 `Feed.total` 之外拿后端 `unread-count` 真值 —— 因此 §22 与本条必须**一起**修，单独改哪一条都不完整。
- **测试方案**：Robolectric 序列断言：依次触发 `AlertEngine.check`（mock 列表 3 未读）→ `ParcelScreen.load`（mock 标量接口回 `data:5`）→ 断言 `AppState.unread.value == 5`（取 max 且标量已能读到）；再断言 `data:0` 时回到 3（后端权威，不出现 0 抹掉已有提醒）；`NotificationScreen` 里断言不再出现对 `AppState.unread` 的赋值（grep 守卫）。

- **本轮状态（P2-1，2026-09-29，DONE）**：
  - **修法与原方案有一处有意的偏离**：①的"集中到 `AlertEngine`"改成了"集中到 `AppState` 的唯一一个写入口"。理由：`AlertEngine` 只是四个写入者之一，把入口设在某个调用方身上，下一个调用方（新页面、新轮询）仍然可以绕过它直接 `.value =`；入口设在状态所有者身上才是结构约束。实现：`private val _unread = MutableStateFlow(0)` + `val unread = _unread.asStateFlow()`（`driverUnread` 同形，`AppState.kt:27-28`），外部**编译期就写不了** `.value`，只能走 `reportUserUnread(serverCount, listCount)` / `reportDriverUnread(...)`。
  - ②"取 max 而不是取最后写入"：`reportUnread() = listOfNotNull(serverCount?.toInt(), listCount).maxOrNull()`，返回 null（两个来源都读不到）时**保持上一个值**，不写 0。这直接消灭了"红点自己闪没"：网关 502 / 未绑定 / 解析失败都不再产生"后端说没有未读"的假事实。
  - ③"标量与列表都要"：7 个写入点全部改成同时传两路 —— `AlertEngine.kt`(3 处)、`NotificationScreen`、`ParcelScreen`、`DriverMessagesScreen`(2 处)。列表侧只在 `feed.error == null` 时传 `items.count { !it.read }`，失败传 null，避免"请求失败 = 列表里没有未读"。
  - 与 §22 的耦合按审计要求一起修完了：只改 §52 会把恒 0 的标量当权威写进角标；只改 §22 则三处直写仍然互相覆盖。两条现在同时成立。
  - **测试**：审计写的是 Robolectric，本机测试栈没有 Robolectric（JVM-only + MockWebServer）。这里不需要，因为 `AppState.unread` 是纯 `StateFlow`、不碰 Android framework，直接用 JVM 断言覆盖了同一条序列：`标量与列表取 max 且读不到时不写 0`、`用户与司机角标不串域`（`ScalarEnvelopeContractTest`，共 17 例 0 失败）。判据 `check_envelope_probes.sh` 的 E-a 用 grep 守住"角标外部直写 = 0 处"，实测会把 `AppState.kt` 自己的 `_unread.value` 排除掉（按内容过滤，不按行号）。
  - **没做**：真机冷启动看红点是否稳定（DEVICE/ENVIRONMENT_BLOCKED，同 §22）。

## 53. `REAL_COMPLETE` 清单（41 条，本轮逐条读码复核）

这些是**已经对齐后端、且有代码依据**的部分。列出来是为了：① 修复时不要顺手改坏；② 证明"未列出的才是问题"，避免整体重写。

| # | 能力 | 依据 |
|---|---|---|
| 1 | 寄货站点列表 | `Repo.kt:463` ↔ `AppSendController.java:482` |
| 2 | 路线预估（`distanceKm/durationMinutes/provider`） | `Repo.kt:499-508` ↔ `POST /send/route-preview` |
| 3 | 费用试算 | `Repo.kt:471` ↔ `/send/quote` |
| 4 | 寄货下单（`@Valid @RequestBody` 字段对齐） | `Repo.kt:432-433` ↔ `AppSendController.java:118-123` |
| 5 | 下单后**以服务端回读**判定成功（不认本地 ack） | `SendScreen.kt:317-324` |
| 6 | 包裹追踪 | `Repo.kt:449-450` ↔ `GET /send/track?no=` |
| 7 | 联运拓扑 + `traceOf` 统一解析 | `Repo.kt:588-589`、`:533+` ↔ `GET /send/topology` |
| 8 | 我的安排 | `Repo.kt:514-515` ↔ `GET /send/arrangements` |
| 9 | 公交附近线路（传 lat/lng/radius） | `Repo.kt:608-612` ↔ `AppBusController.java:41-46` |
| 10 | 线路折线**按需查询**，空则不画 | `Repo.kt:667-671`、`BusScreen.kt:137-140`、`:169` ↔ `/bus/line-polyline` |
| 11 | 公交线路轨迹"断线即截断，绝不补直线"（本轮后端修） | `AppBusServiceImpl.fetchRoutePolyline` + `AppBusLineRespVO:36` |
| 12 | 15 秒静默轮询、失败保留旧数据 | `BusScreen.kt:249-254` |
| 13 | 商品列表/分页/详情 | `Repo.kt:356/372/379` |
| 14 | 商城下单 body 与 `AppProductOrderCreateReqVO` 逐字段一致 | `Repo.kt:384-401` ↔ `order/vo/AppProductOrderCreateReqVO.java:13-38` |
| 15 | 商城订单分页（带 status） | `Repo.kt:406-407` ↔ `AppProductOrderController.java:48` |
| 16 | 取消商城订单（id 在 **query**） | `Repo.kt:413-414` ↔ `:58-61 @RequestParam("id")` |
| 17 | 商城订单溯源 | `Repo.kt:416-417` ↔ `/product-order/trace` |
| 18 | 商城取消态判定 `status == 3` | `Models.kt:862` ↔ `ProductOrderStatusEnum:20` |
| 19 | 通知分页 | `Repo.kt:675-676` ↔ `AppNotificationController.java:36-42` |
| 20 | 标记单条已读（query） | `Repo.kt:685` ↔ `:50-54` |
| 21 | 全部已读（`orderId` 可选，走 query） | `Repo.kt:687-688` ↔ `:56-62` |
| 22 | 密码登录 / 短信登录 | `Repo.kt:97-105` ↔ `AppAuthController` |
| 23 | 发码失败如实抛错（不假称"已发送"） | `Repo.kt:109-112` |
| 24 | 用户资料补全 | `Repo.kt:143-145` ↔ `GET /member/user/get` |
| 25 | 地址 CRUD（delete 走 query） | `Repo.kt:692-710` ↔ `AppAddressController.java:32-48` |
| 26 | 区域树 | `Repo.kt:713-714` ↔ `/system/area/tree` |
| 27 | 修改密码 | `Repo.kt:727-728` |
| 28 | 意见反馈提交与列表 | `Repo.kt:730-738` ↔ `/transport/feedback/*` |
| 29 | 公告列表 | `Repo.kt:198-199` ↔ `/transport/notice/list` |
| 30 | 天气（AMAP + Open-Meteo 兜底，外部源如实标注） | `Repo.kt:243-304` |
| 31 | 司机档案/班次/取件/任务/收益（任务按 driverId） | `Repo.kt:744-779` ↔ `AppDriverController.java:41-70` |
| 32 | 发车（`@RequestBody`，`shiftId` 必填） | `Repo.kt:933` ↔ `:79-84` |
| 33 | 到站（只提交 shiftId+stationId，状态由后端回读） | `Repo.kt:935-936` ↔ `:86-91` + `WorkbenchScreen.kt:228-238` |
| 34 | 动作后**统一 `syncFromServer()`**（司机端正确范式） | `WorkbenchScreen.kt:410-418` |
| 35 | 下一站距离取后端道路值 `nextStopRoadDistanceMeters` | `Models.kt:1180` ↔ `AppDriverRouteRespVO:81` |
| 36 | 装车/取件核销/妥投/商城装车妥投字段一致 | `Repo.kt:938-951` ↔ `AppDriverOrderActionReqVO.java:12-22` |
| 37 | 司机交接列表解析（`driverId` 入 query） | `Repo.kt:812-814` ↔ `:135-140` |
| 38 | 段模型 `done == 11` / `active in 7..10`（正确，只是被 §28 覆盖） | `Models.kt:744-746` ↔ `TransportLegStatusEnum:35-36` |
| 39 | 站点距离 `distanceKm ↔ meters` 双单位兼容（正确范式） | `Models.kt:412-421` |
| 40 | 通知 `readStatus(Integer)` 兼容读取 | `Models.kt:906` ↔ `AppNotificationRespVO:31` |
| 41 | 地图：无可信折线 → `RouteUnavailableState`，不补线 | `MapCanvas.kt:51/67/191` + `core/map/{RouteSource,RealRoadRouteValidator,RouteRepository}` |

**工程纪律层面同样已达标（不计入 41，但属同批资产）**：`bindDriver` 不造 7001(`Repo.kt:162-177`)、`saveSession` 不造 demo token(`:117-140`)、`logout` 无条件清本地(`:182-186`)、`Prefs.clearSession` 连司机身份一起清(`:150-164`)、`Api` 对 `code!=0` 抛业务异常(`Api.kt:253`)、认证接口 401 不广播踢人(`:238-243`)、真实空数据渲染 EmptyState 不回填演示(`:254-257`)、`DriverLocationService` 退避可见(`:85-123`)、无在途班次不用 `shiftId=0` 占位(`:93-97`)、GPS 单一来源（仅 `Locator`）、§50 的 7 条构建/安全项。

---

## 54. 本轮已修（后端线路轨迹真实性 + 测试可执行性）

1. **公交线路"道路轨迹"不再补直线** —— `AppBusServiceImpl.fetchRoutePolyline`：
   - 删除 `addFallbackPoint` 两站直线注入；
   - 逐段要求真实路网几何，来源如实累积：单一来源用该来源，混用记 `MIXED`；
   - 任一段拿不到真实道路几何 → **在该段截断**，只返回已取到的前缀，`complete=false` 时**只进 5 分钟内存缓存、不落库**（避免把截断几何永久固化成"这条路长这样"）；
   - `persistPolyline` / `loadStoredPolyline` 读写两侧都过 `RealRoadProviders.labelOf/isRealRoad` 白名单，非白名单来源（如历史脏数据里的 `euclidean`）读不出来也写不进去；
   - `through`（直达）分支按 `RealRoadProviders.AMAP` 落库，来源真实；
   - `AppBusLineRespVO:36` 的 Schema 从"取不到时前端可回退 points"改成"取不到为 null / 断线返回截断前缀，**前端不得用 points 直线补线**"。
2. **同源纪律横向铺开 3 处** —— `RouteCorridorService.corridor()` 读门、`MonitoringServiceImpl` 的 `corridorSource`/`roadProvider`、`DispatchServiceImpl` 预热路径的 `fromStorage` 标记（回读几何不再被二次落库抹成 AMAP）。
3. **后端 JUnit 终于能真跑** —— 本机无 `mvn`（`which mvn` 确认），改为 `javac @argfile` + `java -cp … RunTests`（JUnit Platform Launcher）：
   - `C:\temp\check-backend-tests.sh` 编译 `src/test/java` 全部 36 个测试类并可按类执行；
   - `C:\temp\RunTests.java` 用 `LauncherDiscoveryRequestBuilder` + `SummaryGeneratingListener` 出汇总（本地仓库无 console-standalone jar）；
   - 结果：**309 tests 全绿**，主源码编译 `classes=476 status=0`；
   - 期间踩坑记录（防再踩）：`@argfile` 被 GBK 解码 → 路径必须走 ASCII 联结目录 `C:\m2repo`、`C:\proj`；`-cp` 必须**单行**（`sed -n '2p'` 带换行会把 classpath 断成两截，报"无效的标记"）；类名提取要 `sed 's|^C:/proj/.*/src/test/java/||; s|\.java$||; s|/|.|g'`。
4. **新增 4 条公交截断回归测试** —— `AppBusServiceImplTest`：`linePolyline_withoutRoadSource_returnsNullNotStationLine`、`linePolyline_truncatesAtFirstSegmentWithoutRoad`（4 站、A→B 有道路、B→C 无 → 断言只剩 3 点、第 2 点经度 104.050、无 ≥104.200 的点、`algorithmClient` 恰好调 2 次）、`linePolyline_acceptsGraphHopperOsmSource`、`linePolyline_rejectsNonRoadProviderLabel`（provider=`euclidean` → null）。
5. **审计自身的两条假阳性被复核推翻**（记录以免下一轮重复误报）：`Api.put(path, body, query)` 下 `cancelProductOrder/readNotification/deleteAddress` 参数位置本来就对；`Models.kt:876 cancelled = status == 3` 对商城单是正确的。
6. **"假导航已修"的旧记录被推翻**：`DriverNavScreen` 的 `stepIndex++/stepping/delay(4_000L)/next()` 仍在（`:94/:103/:138-146/:180-186/:194/:342/:357-359`），`core/navigation` 目录不存在，工程无任何 `AMapNavi*` 引用，依赖只有 `com.amap.api:3dmap:10.0.600` → 该能力判为 `BLOCKED`（§57），本轮不再把它记成"已完成"。
7. 本文档（`ANDROID_PARITY_AUDIT_V2.md`）与 `DECISION_LOG.md` D-014、`AI_ITERATION_STATE.md` 同步更新。

---

## 55. 回归执行清单（命令 + 预期，任何人可复跑）

```bash
# A. 后端主源码 + 测试（本机无 mvn，用 javac/launcher 方案）
bash C:/temp/check-backend-tests.sh            # 预期：classes=476 status=0；36 个测试类编译通过
bash C:/temp/check-backend-tests.sh AppBusServiceImplTest   # 预期：21 tests 全绿（含 4 条截断用例）

# B. Android 编译 + 单测 + lint
cd cargo-post-android && ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
# 预期：BUILD SUCCESSFUL；lint 无新增 error；testDebugUnitTest 0 失败

# C. 真实性自检 grep（窄口径。目标"只剩带标注的展示出口 + 禁止性说明"，**不是必须为 0**）
#   ⚠ 本行原先写"过滤后必须为空；2026-09-29 实测为空"——那句已经不成立，也不该拿它当现状抄。
#   2026-09-29（⑩/⑪）实测 **4**；⑫ 之后 **9**；**⑬ 收工复跑仍是 9**（本轮没开新的距离展示出口）。
#   这 9 行的构成与"为什么不算违规"写在下面第 1258 行起那段，以及 `AI_ITERATION_STATE.md`"常驻自检"的窄口径行。
#   判据是"违规（§24 假步进）不得新增、正当类不得变成伪造几何"，不是"必须为 0" —— 为了 0 去扩白名单就是把假话说干净。
cd cargo-post-android/app/src/main/java
grep -rniE "euclidean|haversine|straight|mock|fake|demo stepping" . \
  | grep -vE -e "core/map/(RealRoadRouteValidator|CoordinateSystem|RouteSource)\.kt" \
              -e "NavIcon\.STRAIGHT|enum class NavIcon" -e "isFakeBoldText" \
              -e "MockWebServer" -e "WeatherCard\.kt" -e "DemoData\.kt" \
              -e "Repo\.kt:.*(mock 标注|适合当日运输)" \
              -e "Models\.kt:[0-9]+: *(//|\*|/\*)" -e "Models\.kt:.*(Haversine 直线距离|mock|直线估算|不是道路里程|路网不可用)"
# **⑩ 把这行的 Models 段换掉了**（原来是 `Models\.kt:(1[1-4][0-9]|2[0-9][0-9])`，行号且**少一个结尾冒号**）：
#   `Models.kt:1447` 里的前缀 "144" 就满足 `1[1-4][0-9]`，于是 **Models.kt 1100..1499 整段命中被静默吞掉**，
#   实测 ⑩ 当天被吞 7 行（其中 6 行正是本轮新写的可达性来源说明）。改成按内容过滤后窄口径仍是 **4**、宽口径仍是 **30**，
#   数字没变但机制变了：以后 `Models.kt:13xx` 出现一行真违规（`distanceMeters = haversine(...)` 那种）现在看得见。
#   与上面 ⑥ 的"行号白名单漏放两行"是同一类缺陷的两个方向 —— **行号要么漂出真违规，要么宽到吞掉真违规，只能按内容**。
# 注意 `-vE -e` 的写法：给了 -e 之后，第一个非选项参数会被当成文件名，直接 `grep -vE "A" -e "B"` 会报 "No such file"。
# **Repo 那条白名单在 ⑥ 之后换过形态**：原来是 `Repo\.kt:2[45][0-9]`（行号），⑥ 在文件头插入 14 行后
# 天气兜底漂到 :255/:261/:266，行号白名单当场漏放两行，窄判据从 4 涨到 6 —— 那不是新违规，是判据自己漂了。
# 换成内容白名单（`mock 标注|适合当日运输`）后：窄口径实测 **4**、宽口径（含全部禁止性说明）实测 **30**，与基线一致。
# 结论写进 §56：**行号白名单会漂，内容白名单不会**；新写的判据一律按内容/函数名，不按行号。
# 被过滤掉的 4 类命中各有正当理由，不要为了"绿"而扩大白名单：
#   · RouteSource.kt：EUCLIDEAN/STRAIGHT/MOCK 是**拒用枚举 + 别名映射表**，出现是为了拒绝，不是使用；
#   · NavIcon.STRAIGHT（Models.kt:1881/140 等 6 处）：**转向图标"直行"**，与几何直线无关，禁止因关键字顺手改名或删除；
#   · 天气 mock 链（Models.kt:209-245、Repo.kt:255/261/266（⑥ 后行号，原 :240-251）、WeatherCard.kt:37/144/151）：mock=true 且界面显示"网络异常，展示本地天气"，属允许显式标注的兜底；
#   · Api.kt:105 注释提到 MockWebServer（测试注入点）；MapCanvas.kt:136 的 isFakeBoldText 是字体加粗。
# 反过来说：**不要把 `demo` 单独加进 pattern**。`Payload.demo` / `Api.demoAllowed` / `DEMO_ALLOWED` 是贯穿全工程的
# 诚实性开关，加进来实测产生 242 条正当命中，自检立刻失去意义。演示面要用引用点查：
#   grep -rln "DemoData" app/src/main/java   # 基线恰好 3 个文件：Api.kt（!demoAllowed 先 throw 才回落）、DemoData.kt 自身、Bits.kt(DemoNote)
# UI/Repo 里任何直接 import DemoData 都是违规（硬规则 18：正式包禁止 demo 兜底）。
# Haversine/直线并不在实现里以关键字出现，审计它的正确方式是 grep "GeoMath"。2026-09-29（⑦ 后）全量命中 22 行 =
#   14 处调用 + 5 处 import（Locator:17、RealRoadRouteValidator:4、MapCanvas:44、DriverNavScreen:69、BusScreen:55）
#   + 1 处对象声明（Models.kt:111）+ 2 处注释（RealRoadRouteValidator:58、Locator:100）。14 处调用的处置：
#   · 允许（下界判据）：RealRoadRouteValidator 的 `straightLineMeters` —— 用直线距离当"道路里程下界"，是 P0-3（docs/REAL_ROAD_ROUTING_ARCHITECTURE.md）的刻意设计；
#   · 允许（几何工具，非里程）：MapCanvas bounds、Repo 的 `bearing`（航向角）、DriverNavScreen 偏线横向距离；
#   · 允许（⑦ 新增，**坐标系偏移量**）：`Locator.kt:103` —— 只在 `BuildConfig.DEBUG` 里打印 WGS84→GCJ02 的差值米数用于真机核对。
#     `:100` 的注释写明"量的是**两个坐标系之间的偏移量**，只用于真机核对，不是道路里程、不是远近展示"；
#     它**不进任何界面文案、不参与任何可用性判定**，这正是 §56 那条"新增调用点必须自带用途说明"要求的样子，不是绕过判据。
#   · 允许（显式标注的远近，不是道路里程）：BusScreen.kt:616-617，:596 的文案已写明"仅表示远近，不是道路里程"；
#   · 允许（P1-1b 起，§32 已修）：WorkbenchScreen.kt:212（行号随改动漂移）—— 现在藏在 private
#     `straightDistanceToTarget()` 里，唯一消费者是 `MissionMarkers.nextStopDistanceText`，
#     输出文案固定带"直线约 N 米（仅表示远近，不是道路里程）"，且不再参与任何按钮可用性；
#   · 演示专属（§46，受 DEMO_ALLOWED 约束）：DemoData.kt:353-357/536/540。
# 判据：除 DemoData 外，任何 GeoMath 生产调用点都必须有"这是远近/下界/几何，不是道路里程"的显式说明，否则即为违规；
#       尤其**禁止**再出现"距离值 → 按钮 enabled / 业务状态"的用法（§32 的门槛形态）。

# D. 假步进自检（§24 的验收判据；P0-7/P1-2 完成前**必定非零命中**，这就是它仍是 UI_ONLY 的证据）
grep -n "stepping\|delay(4_000\|stepIndex + 1" ui/driver/DriverNavScreen.kt
# 2026-09-29 实测：6 处命中（:103 状态位、:146 手动"下一步"、:180/:182/:183 自动播转向循环、:342 开关）。
# 目标：命中数 0；达成前禁止把 §24 标为已修，也不允许删除本行或缩小 pattern 来"通过"。

# E. 安全自检（§50）—— 注意：--include 会把 build.gradle.kts(.kts) 整类排除，必须分两条跑
cd cargo-post-android
grep -rn "127\.0\.0\.1\|localhost" app/src/main --include=*.kt --include=*.xml        # 预期：空（2026-09-29 实测空）
grep -n  "127\.0\.0\.1\|localhost" app/build.gradle.kts                               # 预期：4 行 = 2 行注释(:18/:22) + 构建期拦截(:167/:168)
find . -name "*.jks" -o -name "*.keystore"                                            # 预期：空（签名材料在工程外 ../buspost-signing-backup）
grep -rn "HttpLoggingInterceptor" app/src/main                                        # 预期：空
grep -rniE "(access.?token|apikey|api_key|secret|password|passwd)\s*[:=]\s*\"[A-Za-z0-9_\-]{8,}\"" \
  app/src/main --include=*.kt                                                          # 预期：空（2026-09-29 实测空）
grep -rn "AMAP_KEY\|amap_key" app/src/main --include=*.kt --include=*.xml \
  | grep -v "@string/amap_key\|BuildConfig.AMAP_KEY"                                   # 预期：空——key 只经 resValue/BuildConfig 注入，源码无字面量
```

# F. 业务推进不被定位放行（§32/§63 的防回潮判据；2026-09-29 实测两条都为空）
cd cargo-post-android
grep -rn "canArrive\|ARRIVE_RADIUS_METERS" app/src/main/java                       # 预期：空。有命中=把 GPS 门槛加回来了
grep -rn "nextStationDistanceMeters" app/src/main/java/com/shanxiang/buspost/ui     # 预期：空。有命中=旧直线字段又被当里程显示
#   这两个 grep 是"结构判据"，比"我记得我删了"可靠：删除即归零，回潮立刻非零。
#   `data` 层仍有 1 处声明 + 1 处解析（不删字段的兼容要求），所以第二条只在 ui 包下判空，不要改成全工程判空。

# G. 任务点字段没被读回去（§62 的防回潮判据）
grep -rn "MissionStop" app/src/main/java/com/shanxiang/buspost | wc -l   # 2026-09-29 实测 20（模型 / Repo / MissionMarkers / DriverMissionMap / WorkbenchScreen 及其 import）
grep -rn "timelineOf\|\.ordered(" app/src/main/java/com/shanxiang/buspost/ui/driver | wc -l   # 实测 3：顺序只有一处来源
#   若哪天有人把工作台时间线换回 `shift.stations` 自己排序，这两条会掉到判据以下；判据是下限不是精确值，
#   数量本身会随重构浮动，所以真正的硬约束仍然是那条 `时间线同样只跟随后端序号` 用例。

# H. 用户域 / 司机域分域自检（§29 + §64；判据已落成脚本，别再手敲）
bash tools/check_domain_probes.sh
# 2026-09-29（P1-2 ⑥）实测：P-a=0  P-a2=0  P-b=0  P-b2=21  P-c=2  P-d=2  P-e=1  P-f=5，末行打印"分域判据通过"（退出码 0）
#   P-a/P-a2：用户域（ui/user、ui/login、ui/common、widget、qs、nav）里 0 个司机读点、0 处 `Running.*` / `Prefs.driver*`；
#   P-b：`mapOf("driverId" to driverId)` 字面量拼装 0 —— 未绑定时 `driverId=0` 的 URL **构造不出来**，
#        因为 `driverIdParam()` 在参数求值前就抛 `DriverUnboundException`（§29 ②）；
#   P-b2：`driverIdParam` 引用 22 = 1 KDoc + 1 声明 + 20 调用点（P2-1 新增 `driverUnreadCount()` 后由 21 变 22）。
#        写裸词不写 `driverIdParam()`（实测会少算 KDoc 里那次引用），数字对不上时无法区分"少了一个调用点"还是"注释被删了"；
#   P-c：角色切换入口 2（`MineScreen` 先 `bindDriver` / `DriverShell` 退出用户版回写 `Role.USER`）；
#   P-d：深链分域闸 2（`Routes.DRIVER_ONLY` 声明 + `BusPostRoot.deepLinkAllowed` 判据）；
#   P-e：司机通知落司机页 1（`AlertEngine.kt:85` = `Routes.DRIVER_MESSAGES`）；脚本还打印全部 3 处 `route = `，
#        人眼复核 `trace/*` 只能出现在司机分支 `return`(`:57`) 之后的用户分支里；
#   P-f：`DRIVER_UNBOUND` 引用 5 = Repo 的映射 + 异常类 + 常量，MineScreen 的 import + 使用。
# 与 §55 C 同一课：**这些判据全部按内容/函数名，不按行号**；行号白名单会随一次插入整段失效。

# I. 定位与上报自检（§17 + §45 + §65；判据已落成脚本，别再手敲）
bash tools/check_location_probes.sh
# 2026-09-29（P1-3 ⑦）实测：L-a=2  L-b=0  L-c=0  L-d=0  L-e=0  L-f=0  L-g=1  L-h=0，末行打印"定位与上报判据通过"（退出码 0）
#   L-a：`CoordinateTransform` 的**真实生产调用点恰好 2**（`Locator.toGcj02` 出口 + `RealRoadRoute.gcj02Points`）。
#        是"恰好"不是"至少"：多于 2 就说明有人在自己那儿转第二次，多次偏移正是 §17 原本要避免的事；
#   L-b：`Geo(..latitude` 构造点越出 `Locator` = 0 —— 定位出口只有一处，换算只在那一处发生；
#        注释行按**结构**过滤（先剥掉行首空白再判断是否 `//`），不按行号；
#   L-c：上报报文里出现客户端时间键（`locateTime/clientTime/gpsTime/reportTime/time`）= 0 ——
#        后端 VO 没有这些字段，塞进去就是"未知字段"；这条是 D-019"不做重放"的契约前提，前提没了测试先红；
#   L-d：旧拦停文案"当前没有在途班次，发车后再上报位置" = 0 —— 后端 `shiftId` 已可选，无班次也必须能上报；
#   L-e：手写偏移公式（`*Math.PI / 180.0`、`sqrt(300.0 * (1.0 - ` 一类）越出 `CoordinateSystem.kt` = 0；
#   L-f：第二定位源 `AMapLocationClient` = 0（jar 里**有**这个类，所以 0 不是"不可能"而是"没混用"，接了要显式改判据）；
#   L-g：`DriverLocationService.kt` 里"已丢弃"出现 **1** 次 —— 判据范围是**前台通知那一个拼装点**（`droppedSuffix()`），
#        含义是"删掉它 → 弱网丢点重新变成静默丢"。`WorkbenchScreen` 的 `InfoRow("未确认已丢弃", …)` 是第二个展示面，
#        **不在这条计数里**；引用时不要说成"全工程只有一处已丢弃"，那是把判据范围吹大了。
#   L-h：绕过 `convert()` 直接调 `wgs84ToGcj02` 等底层 = 0（保证 `inChina` 判定与来源标签不被跳过）。
# 负控制实测：人为在 `Repo` 加一处 `Geo(l.latitude, l.longitude)`、人为写一段手写偏移公式，L-b / L-e 当场报红并 exit 1
#  ——判据不是装饰。与 H 组同样**按内容/函数名**，不用行号白名单。

# J. 标量信封 / 未读角标 / 交接凭证照片自检（§22 + §23 + §52 + §7；P2-1 新增）
bash tools/check_envelope_probes.sh
# 2026-09-29（P2-1）实测：E-a=0  E-b=3  E-c=0  E-d=0  E-e=1  E-f=1/1  E-g=1  E-h=1，末行打印"契约信封判据通过"（退出码 0）
#   E-a：`AppState.unread` / `driverUnread` 的**外部** `.value =` 写入 = 0 —— 两个流已是 `asStateFlow()`，
#        这条 grep 是第二道闸（万一有人把 `private val _x` 改回公开 `MutableStateFlow`，编译不报错，判据报红）；
#   E-b：`Payload.longOf/strOf` 在 `Repo` 的调用点 = 3（`unreadCount` / `driverUnreadCount` / `uploadPhoto`）。
#        是"恰好 3"不是"≥1"：多于 3 说明有人在把本该是对象的响应硬按标量读，少于 3 说明某条链退回旧写法；
#   E-c：`multiNum("count"` / `multiStr("url","fileUrl")` 这类**旧对象键读裸标量**的写法残留 = 0；
#   E-d：`items.map { it.copy(status = …) }` 本机改写交接状态 = 0（§26/§27 那一类"ack 之后自己覆盖列表"的形态）；
#   E-e：`status = o.multiStr("statusName"` = 1 —— 交接状态优先读后端给人读的 `statusName`，
#        精确到"赋值 + 首个键"，否则 `HandoverTask` 的 KDoc 里那句说明也会被算成一个合法读点（首版实测就是这样虚高到 2）；
#   E-f：凭证照片的回读闭环 1/1 —— 模型侧 `photoUrl = o.multiStr("photoUrl"` 1 处，页面侧 `model = task.photoUrl` 1 处。
#        只数前者会漏掉"解析了但没人显示"，只数后者会漏掉"显示了但字段没解析"，两头都要有；
#   E-g：上传失败拦停文案 = 1（整句 `凭证照片上传失败，交接未提交`）。首版用"交接未提交"当 pattern 实测是 2
#        ——演示分支文案里也含这四个字，那是正当命中，于是**收窄 pattern** 而不是把目标改成 2；
#   E-h：`toPayload` 里 `scalar = data` = 1 —— 信封的标量出口只有一处，防止有人再加第二条 `data` 通道。
# 负控制实测（Git Bash 里逐条做，每条做完还原）：给 `AppState.unread` 加一处外部赋值 → E-a 报红；
# 把 `Repo.unreadCount` 改回 `multiNum` → E-b/E-c 同时报红；把 `HandoverTask` 的 `statusName` 首选键删掉 → E-e 报红。
# **教训（与 H/I 同一课）**：第一版脚本是在 Windows `python subprocess.run(["bash", …])` 下跑的，那个 `bash` 解析到
# `bash.exe` → WSL 转发，输出是 UTF-16 乱码、exit=1 全是假的。判据必须在真正的 Git Bash 里跑，
# 且"看到 exit 0"不够 —— 要看到 8 个数字和末行那句中文通过。

# K. 状态映射 / 司机段状态机自检（§18 + §8 + §10 + §26–§28 + §36；⑨ 新增）
bash tools/check_status_probes.sh
# 2026-09-29（⑨）实测：S-a=0 S-b=0 S-c=0 S-c2=2 S-c3=6 S-d=6 S-e=10 S-f=0 S-g=0 S-h=3 S-i=64 S-j=4 S-k=17
#   S-la=0 S-lb=0 S-lc=2 S-ld=2 S-le=2，末行打印"状态映射判据通过"（退出码 0）
#   S-a：段状态机残留数字比较（`leg.status >= N` / `== N`）= 0 —— 只许以**注释**形式提到旧写法，命中先剥注释再数；
#   S-b：用**文案子串**猜状态（`contains("完成")` 一类）= 0；
#   S-c：页面里**数字分支**的 `when(…status)` 排码表 = 0（第三张本地表就是靠这条抓住的）；
#   S-c2：车辆运行态 `when(v.status)` = **恰好 2**（`HomeScreen` + `BusScreen`）—— 那是 Android 自有枚举不是后端码，
#         归零反而说明有人把车辆状态也塞进了别的出口，所以这里是"2"而不是"0"；
#   S-c3：审核码走 `ReviewStatus.of` = 6（`ReviewMapper` 2 + `ReviewCard` 2 + `SendScreen` 2，构成变了要重数）；
#   S-d：`ServerStatus.kt` 的枚举数 = **6**（货运/段/商城/审核/任务点/交接）—— 加一套后端枚举必须同时加测试，
#        这里从 6 变成 7 而 S-k 用例数没涨，就是"建了表没钉测试"；
#   S-e：未知码占位文案（`STATUS_UNAVAILABLE_LABEL`）引用点 = 10（下限 ≥6）；
#   S-f：错误注释指纹"待派单"= 0（§18 那条把货运 1 写成"配送中"的注释已删）；
#   S-g：`DemoData` 里重复的货运码表 = 0（演示数据不得再养一张第二表）；
#   S-h：缺键 -1 哨兵 = **恰好 3**（`TransportLeg` / `MissionStop` / `Repo.traceOf`）—— "读不到"必须是 -1 而不是 0；
#   S-i：页面/核心引用映射枚举的点数 = 64（下限 ≥20，**改道越多越涨**，掉下来说明有人绕过单源）；
#   S-j：`docs/STATUS_MAPPING.md` 的段码表行数 = 4（下限 ≥1）；
#   S-k：`StatusMappingContractTest` 的 `@Test` 数 = 17（下限 ≥15）；
#   S-la：`nextStatus()` / `legStep()` / `LEG_ACTIONS` = 0 —— §26/§27 收口的硬判据，**本机自造下一个状态必须一个不剩**；
#   S-lb：`copy(status = …)` 改写后端段状态 = 0；
#   S-lc：出现段动作字面量的文件数 ≤ 3（实测 **2**：`DriverLegStateMapper.kt` + `DemoData.kt`；
#         `Repo.legAction` 只收 `action: String` 形参、不写字面量，所以不在计数里。演示报文里有 `"action" to "load"` 这类，
#         是演示面自己的假数据，归 §46 处理，不算第二段状态机）；
#   S-ld：司机页引用 `DriverLegStateMapper` 的文件数 ≥ 2（`RoutesScreen` + `HandoverScreen`）；
#   S-le：页面直调 `Repo.legAction` 的点数 = **2**，且两处后面都必须紧跟回读（`RoutesViewModel.action` → `Repo.driverLegs()`，
#        `HandoverScreen.advanceLeg` → `Repo.currentLeg()`）。这条从 3 降到 2 **不是删了调用点**，
#        而是 §39 那个把 `"shift-$id"` 当 `legId` 的假调用被换成 `/driver/arrive`（班次收口本来就不该打段接口）；
#        回归时"数对了但回读被删"同样是破口 —— 数完要看那两处的下一行还在不在。
# 与 H/I/J 同口径：**按内容/函数名**过滤，不用行号白名单；注释行按结构剥（先剥行首空白再判 `//`）。

# L. 可达性三态自检（§34 + §11 + §14；⑩ 新增）
bash tools/check_reachability_probes.sh
# 2026-09-29（⑩）实测：R-a=0 R-b=0 R-c=0 R-c2=1 R-d=0 R-e=25 R-f=2 R-g=2 R-h=0 R-i=1 R-j=17 R-k=1 R-k2=0
#   R-l=5 R-m=2 R-n=3 R-n2=0 R-n3=2 R-o=1 R-o2=2 R-p=1，末行打印"可达性判据通过"（退出码 0）
#   R-a：界面按布尔 `reachable` 分支 = 0 —— 三态之后"能显示可运"的唯一入口是 `state == REACHABLE`，退回布尔就等于把 UNKNOWN 说成不可运；
#   R-b：界面读 `distanceMeters` = 0（后端契约里没这个键，读到它必然恒 0）；
#   R-c/R-c2 是一对**互锁**判据：生产代码里的 `"STATION"` = 0（不在 `ServiceModeEnum` 五码内，落库后单据没法解释），
#        同时测试报文里的 `"serviceMode":"STATION"` = 1 —— 反面 fixture 被"顺手清理"，R-c 就退化成没人证的空判据；
#   R-d：`"originalAddress" to …(nearestStation|stationName|…)` = 0（用户位置与取货站点是两个字段，VO:60 明令不互相覆盖）；
#   R-f：`multiBoolOrNull` **恰好 2**（Models 声明 1 + parse 使用 1）—— 缺键走 `multiBool` 就是塌成 false；
#   R-g：`walkMinutes: Int?` 与 `distanceKm: Double?` = 2，非空化即 §14 的 null→0 回潮；
#   R-j：只数**行首生效的** `@Test`（被注释掉的 `//@Test` 字样还在但不算用例 —— 这是负控制实测出来的口径修正）；
#   R-k/R-m：数**出现次数**不是行数（`grep -o | wc -l`），否则两个出口挤在同一行就看不见；
#   R-n2/R-o/R-p 是**语义**判据不是结构判据：分支存在不等于分支说得对，所以单独数"UNKNOWN 里出现可运话术"（=0）、
#        "可上门却没给就近站点却不提示手选"（=1）、"文字地址没取到时整行被藏"（≥1）。
# 负控制：`bash tools/reach_negative_controls.sh`（⑩ 首次把 21 条逐条破口跑完，全 ✔）。
#   **这一轮的教训**：R-j/R-k 两条第一版负控制"失效"，抓出来的不是代码问题而是**判据本身太弱**
#   （`grep -c "@Test"` 会把注释掉的注解照数、`grep -c` 把同行两处调用数成一处），先改判据再复跑才有效。
#   同轮还实测到：**21 条判据全绿的源码可以同时编译不过**（一次误删换行把两行并成一行，探针毫无反应）——
#   探针只证语义，`./gradlew :app:assembleDebug` 才证可构建，两者不可互相替代。
# 2026-09-29（⑪ 复跑）：**R-g 的口径改了，数字没改**。`BusVehicle` 新增可空 `distanceKm` 之后，
#   整文件 `grep -cE "val (walkMinutes: Int\?|distanceKm: Double\?)"` 会数到 3 —— 因为两个契约撞了同一个字段名。
#   处置是**把别的契约的字段摘出这条判据的作用域**（`sed -n '/^data class Reachability/,/^) {/p'`），
#   **不是**把预期从 2 抬到 3：抬数字等于这条判据从此不判任何东西。负控制复跑 **21 ✔**。

# M. 公交 ETA 自检（§38 + §14 + 顺带登记的 §68/§69；⑪ 新增）
bash tools/check_eta_probes.sh
# 2026-09-29（⑪）实测：T-a=0 T-b=19 T-c=8 T-d1=0 T-d2=2 T-e=0 T-f1=0 T-f2=1 T-g=0 T-h1=2 T-h2=0 T-i=7
#   T-j1=1 T-j2=0 T-j3=1 T-j4=12 T-j5=1 T-k=9 T-l=0 T-m=4 T-n=3 T-o1=1 T-o2=5 T-p=3 T-q=1，
#   末行打印"公交 ETA 判据通过"（退出码 0）；脚本尾部另**打印残留**（货运侧 4 处 `multiNum("etaMinutes").toInt()`
#   + 页面 6 处 `etaMinutes > 0`），残留不参与判红，是留给 ⑫ 的靶子。
#   T-a：ETA 家族 `?: 0` 塌缩 = 0。立论依据是后端源码而不是症状：`AppBusServiceImpl:132`
#        （`Math.max(1, round((100-progress)/100f*duration))`）与 `:728`（`max(1, ceil)`）**都下探到 1 分钟**，
#        `:134` 拿不到线路时长直接 `setEtaMinutes(null)`，`:665` 注释原文"不臆造到站分钟" ⇒ 0 在契约里不合法；
#        HTTP 响应保留显式 null 键（MVC 的 Jackson 不是 NON_NULL，`JsonUtils.java:48` 那个 NON_NULL 只是工具 mapper），
#        所以"没给"在报文里长得像 `"etaMinutes": null`，`optInt(...,0)` 与 `?: 0` 都会把它读成 0。
#   T-c：**恰好 8**，且只数 `data class BusVehicle` 类体（与上面 R-g 同一条纪律：一条判据只管一个契约）。
#   T-d1/T-d2 是一对：前者数"客户端自编的进度公式"（=0），后者数"进度条由后端 `roadProgress` 驱动"（=2，线路页 + 首页）。
#        只钉一边会留另一边回潮 —— `1 - eta/40` 那个 40 是 Android 自选常数，后端没有对应量，
#        而 `:125-127` 的 `progress` 默认 0 让"后端给 0"与"后端没给"在反推下塌成同一件事。
#   T-e：作用域写死在 `notify widget data`，**不要**扩成整个 `ui` —— 货运侧还没收口，扩了就是假报红。
#   T-f1/T-f2/T-g：`statusOf` 只留两个端点真实会发的码（字符串 RUNNING/IDLE/ARRIVED/NO_LOCATION + 整数 1/0/2）；
#        旧码表里那个 3 后端从不发，而整数 2 是"停用"不是"已到终点站"（旧映射会把停运车辆显示成到站，还会触发提醒）。
#   T-j1..T-j5：演示兜底**按端点分形状**（nearby 发字符串 status + `locationSource` + `routeProvider=SCHEDULE` + `nearestStation*`，
#        lines 发整数 status + `progress` + `speedKmh` + `etaToNextStationMinutes`），"没给"写 `JSONObject.NULL`（12 处）而不是省键；
#        T-j2=0 钉的是 §69 —— **真实车辆 VO 里没有 `routeId`**，演示面也不许再发这个键，否则兼容代码永远"看起来能用"。
#   T-p：**≥3 不是 =2**（`TransportLeg` 也读 `plateNo`）。写死 2 会在别人补兼容时假报红，假报红多了判据就没人看。
#   T-q：§68 那条"到站提醒的数据源没有 `etaMinutes`"必须还在文档里 —— 结构性缺口被"顺手修掉"后没人知情，比缺口本身更糟。
# 负控制：`bash tools/eta_negative_controls.sh`（25 条逐条破口，**25/25 ✔**，基线与恢复后各跑一次判据都通过）。
#   本轮四条第一版"注入破口后 exit 0"，根因**全是判据本身太弱**，与 ⑩ 的 R-j/R-k 同一课：
#   T-d1 的正则漏了 `!!` 形态、T-e 的锚点选了工程里不存在的 `fun scan(`（真实入口是 `suspend fun check(context)`）、
#   T-l 的锚点是已被自己替换掉的文案、T-o2 的 `sed` 只替到 5 处里的 1 处（"改坏了"却测不出，因为另外 4 处还绿）。
#   **先改判据再复跑，不许改代码去过判据。**
# 2026-09-29（⑫ 把负控扩到 41 条、⑬ 扩到 50 条）复跑后的当前数字以 `AI_ITERATION_STATE.md`"常驻自检"的 ETA 行为准。
#   ⚠ **本块上面那行是 ⑪ 的快照，不要当现状抄**：⑬ 之后 `T-c 8 → 9`（`BusVehicle` 多了 `etaToNextStationMinutes`）、
#   `T-b 19 → 22 → 30`、`T-k 9 → 12`、`T-z 7 → 10`、`T-o2 5 → 3`、`T-v` 由"≥3"钉成"=5"并新增 `T-v2`，
#   另加 ⑬ 的 `T-aa…T-ah` 九条，末行文案已变成"ETA 判据通过（公交侧 + 货运侧 + ⑬ 里程口径）"。
#   引用判据数字时必须连同"是哪一轮 + 哪条 pattern"一起抄，这是本工程为 ①/③ 两条口径不一致付出过两次重跑的代价后定下的规矩。


**每项都要留输出**：`309 tests`、`BUILD SUCCESSFUL`、grep 空结果，是本轮及后续每一轮的唯一验收凭证；没有输出就等于没跑。

---

## 56. 不许回潮清单（写进 `AI_ITERATION_STATE.md` 的常驻自检）

| 不变量 | 判据 | 关联条目 |
|---|---|---|
| 绝不补直线/欧氏几何 | `fetchRoutePolyline` 无 `addFallbackPoint`；`MapCanvas` 无几何 → `RouteUnavailableState` | §54、矩阵 H |
| 道路来源只能来自白名单 | 读写两侧都有 `RealRoadProviders.labelOf/isRealRoad`（当前 5 处） | §54、§36 |
| 截断几何不落库 | `complete=false` 分支不调 `persistPolyline` | §54 |
| 坐标只走一个换算入口 | `CoordinateTransform` 生产调用点**恰好 2**（`Locator.toGcj02` + `RealRoadRoute.gcj02Points`）；`Geo(..latitude` 不越出 `Locator`；手写偏移公式不越出 `CoordinateSystem.kt`。判据 = §55 I 组 L-a/L-b/L-e/L-h | §17、§45、§65 |
| 没有假导航 | `grep stepping/delay(4_000/stepIndex + 1` 零命中 | §24 |
| 客户端不算下一状态 | `nextStatus` 类函数不存在；动作后必发 `GET /driver/legs` | §26/§27/§28 |
| 状态表按枚举码 keyed 且后端 `statusName` 优先 | `freightStatusLabel`/`LegStatus`/`productStatusLabel` 三支测试 | §18/§28/§33 |
| 演示数据不冒充 | `DemoNote` 在 `demo=true` 时必现；`DemoData` 无 `lanes`、无侧向偏移点、号码带 `DEMO-` | §30/§46/§47 |
| 标量信封只有一个出口，且没人再按对象键读它 | `toPayload` 里 `scalar = data` 恰好 1 处（E-h）；`Payload.longOf/strOf` 在 `Repo` 恰好 3 个调用点（E-b，多于 3 = 有人把对象响应硬当标量读，少于 3 = 某条链退回旧写法）；`multiNum("count")` / `multiStr("url","fileUrl")` 旧读法残留 0（E-c） | §22/§23/§52 |
| **角标不许被就地写没** | `unread`/`driverUnread` 是 `private _x + asStateFlow()`，外部 `.value =` 零命中（E-a）；写入一律 `report*(serverCount, listCount)`，两路取 max、**都读不到就保持上一个值**（不写 0）。这是结构性约束，编译期就拦住了"页面顺手写 0" | §52 |
| **没有凭证照片就不说"已上传"，也没人本机改写交接状态** | 缺 URL 时先拦在提交前（文案 `凭证照片上传失败，交接未提交`，E-g=1）；成功文案只在**回读列表里那条交接真的带 photoUrl** 时才出现（E-f=1/1：模型解析 + 页面渲染）；`items.map { it.copy(status = …) }` 零命中（E-d=0） | §7/§25/§26/§27 |
| 空 ≠ 失败 | `Unauthorized/无权限/无定位` 三态各有独立文案 | §20/§37/§51 |
| 参数位置正确 | `@RequestParam` 一律走 query；`legId: Long` 类型化 | §39 |
| **定位不放行业务、不当里程** | `canArrive\|ARRIVE_RADIUS_METERS` 零命中；`nextStationDistanceMeters` 在 `ui` 包下零命中；GeoMath 调用点一律带"非道路里程"说明 | §32、§63 |
| **任务点顺序/状态只有一个来源** | `MissionMarkers.ordered`（`visitSequence`）与 `stateOf`（`TaskItemStatusEnum`）是唯一入口；`status == 7` 才算完成、8 必须是 FAILED | §62、§18 |
| **司机身份只来自后端档案** | `mapOf("driverId" to driverId)` 零命中；20 个司机调用点全部经 `driverIdParam()`（未绑定直接抛，不发请求；P2-1 加 `driverUnreadCount` 后由 19 变 20）；4 个身份引导接口（profile/shifts/pickups/earnings）按 token 识别，**不许**加守卫，否则永远绑定不上 | §29、§64 |
| **角色与业务域同步** | `AppState.setRole` 恰好 2 处（进司机域先过 `bindDriver`、退出用户版回写 `Role.USER`）；`DRIVER_ONLY` 恰好 2 处（声明 + 深链闸）；司机通知 `route` 恰好在 `DRIVER_MESSAGES`，司机分支内零 `trace/` | §64 |
| **上报报文不得带客户端时间** | `locateTime/clientTime/gpsTime/reportTime/time` 在上报 body 里零出现（L-c=0 + `DriverStateTest` 反向断言）。**理由不是洁癖**：后端 VO 没有这些字段且用 `LocalDateTime.now()` 盖章，带时间戳重放历史点＝给旧位置盖新章＝假上报（D-019） | §45、§17 |
| **丢点必须可见，过期点不得冒充当前** | 前台通知里的丢弃计数**只有一个拼装点**（`droppedSuffix()`，L-g=1；判据范围见 §55 I —— 工作台 `InfoRow` 是第二个展示面，不在计数内）；`MAX_FIX_AGE_MS = 45_000` 之外一律 `Hold` 并写明原因；`LocationReportPolicy.decide` 是纯函数且有 15 例覆盖 | §45 |
| **可达性三态不许塌成两态、来源不许撒谎** | 界面只按 `ReachabilityState` 分支（R-a=0），`UNKNOWN` 分支里出现"可就近/可以运输"即判红（R-n2=0）且必须保留"不代表车辆能到"（R-n3≥1）；`distanceKm/walkMinutes` 必须可空（R-g=2）且页面只走 `distanceText/walkText`（R-h=0）；`HAVERSINE` 一律标"直线估算，不是道路里程"（R-i=1）；服务方式只有一个出口 `pickupServiceModeOrNull`（R-m=2，按出现次数数），非法码 `"STATION"` 在生产代码零命中、测试报文里必须留一份反面 fixture（R-c=0 与 R-c2=1 **互锁**）；解析出口只有 `Reachability.parse` 一处（R-k=1，按出现次数不是行数）；可上门却没给站点时不许静默（R-o=1/R-o2=2） | §34、§11、§14 |
| release 无明文/无本机地址/无签名文件/无 key 字面量 | §50 的 7 条命令 | §50 |

---

## 57. `BLOCKED` / `ENVIRONMENT_BLOCKED`（如实记录，不用假实现顶替）

| 项 | 状态 | 缺什么 | 解锁动作 |
|---|---|---|---|
| 真实导航引擎（P1-2） | `BLOCKED` —— **已用当前依赖实测确认，不是推测** | 依赖只有 `com.amap.api:3dmap:10.0.600`（`app/build.gradle.kts:215`）。实测该 jar：`unzip -l` 里**没有任何 `com/amap/api/navi/` 类**（命中的 303 条"navi"全是厂商基础包 `com/autonavi/…`），`javap -cp <jar> com.amap.api.navi.AMapNavi` → **ClassNotFoundException**。工程里亦无 `AMapNavi*` 引用、无 `core/navigation` 目录 → 导航 SDK 确实不在课上 | 加导航 SDK（`com.amap.api:navi-3dmap`），**先解包 aar/javap 核对 `AMapNavi` / `AMapNaviListener.onNaviInfoUpdate(NaviInfo)` / `AMapNaviView` 的真实签名**，再写 `core/navigation`；拿不到依赖就维持 `BLOCKED`，页面显示"导航能力未就绪"，**绝不用假步进顶替**（硬规则 20） |
| 高德定位 SDK（P1-3 的候选 GPS 源） | 可用（同一 jar 内置）但**未启用** —— ⑦ 实测判据 **L-f = 0**（`AMapLocationClient` 零引用），单一 GPS 源仍是 `LocationManager` | `unzip -l` 实测有 `com/amap/api/location/{AMapLocationClient, AMapLocationClientOption, AMapLocationListener, AMapLocationQualityReport, CoordinateConverter, CoordUtil}.class`。注意 `build.gradle.kts:214` 已说明：**再单独引 `com.amap.api:location` 会 duplicate class** | `Locator` 若改用高德定位，必须走 `AMapLocationClient`（回 GCJ-02）与 `LocationManager`（回 WGS-84）**二选一**，不能两套混用；§17 的修法是"在 `Locator` 出口统一一次换算"，改 SDK 不改变这条要求。**换源时必须同时改 L-f 判据**（高德源自带 GCJ-02，`Locator.toGcj02` 那条无条件换算就要按 provider 分流，否则又变成转两次） |
| 逐转向/车道数据 | `MISSING`（契约层） | `AppDriverRouteRespVO` 无 steps/lanes；只有 `DemoData` 造 | 需后端决定是否接入高德 `steps`（`AlgorithmRouteRespDTO` 侧）；未接入前 App 端**不渲染**转向卡（§24 方案第 1 步） |
| 真机 GPS / 弱信号 / **停报与丢弃可见性** | `ENVIRONMENT_BLOCKED`（⑦ 更新：原"上报队列续报"一项**已改为不做续报**，见 §45 本轮状态与 D-019） | 无实车、无 Android 设备在环 | 真机清单四条：① 静止/遮蔽 45 秒后通知与工作台出现"定位信号已中断 N 秒，暂停上报"，且**恢复后自动续报**；② 隧道出/入后工作台"已丢弃 N 个未确认点"的数与实际相符（合并语义，不是补报）；③ 弱网退避实测 3→20→40→60 s 封顶；④ GCJ 偏移修复后与小程序**同点**比对 `recommendedStation.id` |
| **OEM 网络定位是否已回 GCJ-02（二次偏移风险）** | `ENVIRONMENT_BLOCKED` —— **⑦ 新发现的、修好 §17 之后仍然存在的风险** | `Locator` 现在**无条件**按 WGS-84 处理输入并转一次。若某厂商的 `Network` provider 本身就返回 GCJ-02，就是**转两次**（重庆会多偏约 500 m），而 JVM 测试测不出来 | 真机：同一地点分别取 `GPS` 与 `network` provider 的原始值（debug 日志 `BUS_LOC` 打印偏移米数与 provider 名），网络定位偏移≈0 就说明它已是 GCJ-02，届时必须**按 provider 决定是否换算**（这属于新证据驱动的改动，现在凭猜加白名单就是假精确） |
| **无 Robolectric → 定位/上报的 Android 侧无覆盖** | `ENVIRONMENT_BLOCKED` —— ⑦ 新增。`Locator.fixOf` / `trackFixes` / `DriverLocationService.observe` 的 `while(isActive)` 重试环 / 前台通知文案**都没有测试框架能覆盖** | 工程只有 JVM 单测；`android.location.Location`、`SystemClock.elapsedRealtime()`、`NotificationCompat` 都拿不到真值（`testOptions.isReturnDefaultValues` 只让它们返回默认值，不产生行为） | 已做的替代：把可判定逻辑全部抽成纯函数（`LocationReportPolicy` 15 例、`CoordinateTransformTest` 12 例、`LocatorCoordinateTest` 6 例）+ §55 I 组结构判据。补测要先引入 Robolectric 或 Compose UI 测试框架，属工程资产投入，记在 §60 |
| 分域（§29/§64）的 Compose 侧行为 | `ENVIRONMENT_BLOCKED` —— 工程**没有 Robolectric/Compose UI 测试**，`DomainSplitTest` 只能测到 Repo 与 ViewModel 层 | 设备在环：① 非司机账号点"司机模式"应停在我的页并出现"未绑定司机档案"；② 司机通知点击必须落在司机消息中心；③ 点"用户版"后 20 秒内 Logcat 不再出现 `/driver/messages`；④ 杀掉进程从通知栏重进不得落入司机壳 | 真机回归四条，逐条截图归档；未跑之前不得把 §64 的这四条写成"已验证"，只写"代码已改 + 判据为绿" |
| 高德 key 有效性（接线 `BusMap` 前） | `ENVIRONMENT_BLOCKED` | key 走 `local.properties`/`-P`/环境变量，构建期 `resValue` 注入，源码里没有 | 真机验证 `MapView` 生命周期 + key 校验通过；未验证前不批量接线（§44） |
| 后端联调数据 | `ENVIRONMENT_BLOCKED` | 本地库多为空/演示态，`/driver/legs`、`/bus/line-polyline` 常返回空 | 造一份最小真实路网数据集（含 `navigationSource` 白名单值）用于端到端；不得用假数据代替 |
| `mvn` 不可用 | `ENVIRONMENT_BLOCKED`（已绕过） | `which mvn` 确认未安装 | 已用 javac + JUnit Platform Launcher 跑通 309 tests（§54 第 3 点）；仍建议 CI 里补 `.mvn/wrapper` 以便他人复跑 |
| GraphHopper 服务态 | 运行中，端口 **8080**（admin 8081），profiles `[car, bus]`，bbox 重庆 | 覆盖范围有限，域外线路必然 `available=false`（这是正确行为） | 记录为"域外 → 截断/不可用"，不许扩 bbox 来"让路线看起来有" |
| release 签名与产物 | `BLOCKED`（有意） | keystore 在 `../buspost-signing-backup/`（含 `buspost-release.jks`+`keystore.properties`），**禁止进源码树**；`find . -name "*.jks"` 零命中 ✔ | 正式包走未签名 release 产物 + 外部签名；CI 用 `-Pbuspost.signingProps=` |
| 仓库根 `app-debug.apk`（63,772,976 B） | 待清理（§50 待办 1） | debug 包含 `DEMO_ALLOWED=true` 与演示几何 | 移入 `build/outputs`（已 gitignore）或产物目录，不提交 |
| **交接凭证照片的真机端到端（§7 P0）** | `ENVIRONMENT_BLOCKED` —— P2-1 只做到"代码 + MockWebServer 契约"，**没有**做到"手机拍照 → 后端落库 → 重新查询还能看到照片" | 设备在环 + 后端 `infra/file` 存储真实可用（本地库多为空/演示态）。MockWebServer 能证明"客户端只在回读带 photoUrl 时才说成功"，**证明不了后端真的会存图**（`/admin/infra/file/upload` 需登录态，App 只有会员 token，这条链本身也待联调核实） | 真机四条：① 拍照确认后重新下拉刷新，卡片仍显示那张照片；② 数据库 `transport_handover.photo_url` 是真实 URL 不是空；③ 故意断网选照 → 显示"上传失败，交接未提交"且后端**没有**新增已完成记录（幂等返回，不会被误标记）；④ 不选照片直接确认 → 允许提交，文案写"未附凭证照片"，不得假装已上传。跑完之前 §7 只能说"代码闭环完成"，不能说"照片链路已验证" |

---

## 58. 修复顺序 · P0（先止血：错误信息会直接误导人）

1. **§17 坐标系** —— 唯一入口在 `Locator` 落 `CoordinateTransform`；影响 6 个端点 + 监控偏航判定，且写路径已污染数据，越早修越少脏数据。
   → **DONE（P1-3 ⑦，2026-09-29）**：换算落在 `Locator.toGcj02`（所有出口）+ `RealRoadRoute.gcj02Points`（渲染层），
   实测偏移 480.6 m，判据 §55 I 组 L-a=2 / L-b=0 / L-e=0 / L-h=0。残留两条**不是待办而是已知局限**：
   `inChina()` 矩形判定（已用测试钉住）、OEM 网络定位可能本身回 GCJ-02（真机项，§57）。
2. **§22 标量信封（+§23、§52）** —— 一个 `Payload.scalar*` 改动同时救回未读角标与交接凭证照片；`photoUrl` 恒空是"数据没落库但显示成功"，优先级等同坐标。
   → **DONE（P2-1，2026-09-29，判据 §55 J 组 + `ScalarEnvelopeContractTest` 17 例）**。两处超出原计划的实测发现：
   ① `HandoverTask.status` 读的是数字 `status`（后端 0..7），界面渲染成"0/4"且 `isDone()` 恒不成立 —— **已完成交接一直留在待办列表**，
   这是同一信封病根的第三处，改成 `statusName` 优先；
   ② `WorkbenchScreen.photoPicked` 的诚实守卫在修好 `uploadPhoto` 之前，使正式构建的**装车/妥投动作被完全死锁**
   （照片永远为空 ⇒ 一律拦停），所以 §22 不只是"角标不亮"，它锁死了司机端的妥投链路。
   **未做**：真机端到端（§57 新增一行，`ENVIRONMENT_BLOCKED`）。
3. **§28 + §26 + §27 段状态机** —— 建 `LegStatus`（0..11+99）唯一表；删 `nextStatus`/本地 `status>=4`/`legStep+1`；动作后一律回读 `GET /driver/legs`、`/driver/handovers`。这是"司机看到的进度"的地基，必须整组改，单改一处会更不一致。
   → **DONE（⑨，2026-09-29，D-021，判据 §55 K 组）**：本条在 ⑨ 收口时漏了在这里打勾，⑩ 复跑 K 组时补记（S-la/S-lb 全工程 0 命中）。
4. **§34 可达性契约** —— 站名/单位/来源；顺带堵住空 `originalAddress` 进运单。
   → **DONE（⑩，2026-09-29，D-022）**：三态 `ReachabilityState` + 按 VO 解析 + 来源如实（HAVERSINE 明写"直线估算，不是道路里程"）；
   判据 §55 L 组 21 条 + `ReachabilityContractTest` 17 例。**方案 ③ 那句"提交推荐站点名"被源码推翻**，理由与替代闸见 §34 的"本轮状态"段；
   空 `originalAddress` 不拦提交（与小程序 `send.js:709` 同口径），但 UI 不再藏"寄货位置"这一行。
5. **§36 段字段名 + 段无几何** —— `legSequence/fromStationName/toStationName/plateNo/handoverRequired`；把段卡几何来源改到 `routeGuide`。
6. **§39 两个必 400 写路径** —— `confirm-station-action` 走 query；`Repo.legAction` 参数类型 `String → Long`（编译期根除假 id），收班不再拼 `shift-$id`。
7. **§24 假导航去假** —— 删自动步进；`steps` 为空则不渲染转向卡/车道卡；旧屏转 DEBUG ONLY（§51 P1-4）。依赖仍缺则保持 `BLOCKED`，不做假实现。
8. **§47 + §30 演示态可见性** —— `DemoNote` 恢复（`demo && DEMO_ALLOWED`），`DemoData` 写回执不造假取件码。
9. **§46 DemoData 假几何/假车道清除** —— `lineOf` 去侧向偏移；不再生成 `lanes/recommendedLane`；号码加 `DEMO-` 前缀。
10. **§18 货运状态表按枚举码** —— 含 `TraceScreen` 改用 `tr.statusLabel`；顺带 §33 文案与 `else -> ""`。
11. **§38 ETA null 不渲染成 0** —— `Int?` + "—" + 排序/进度/提醒三处联动。

## 59. 修复顺序 · P1

1. §21 + §41 异常上报链路（后端 2 行 controller + Android 入口 + 99/13 呈现）。
2. §37 + §51 district 兜底与定位四态（同一条 `nearbyBestEffort` 一起改）。
3. §44 `BusMap` 接线（先司机任务地图 P1-1，验证生命周期后再换公交详情）→ **DONE（P1-1，2026-09-29）**：
   `DriverMissionMap` 已接线（真图优先、无 key/无几何回落，`24 tests` 时绿）；残留的是**真机渲染/key 校验**（§57 `ENVIRONMENT_BLOCKED`），
   公交详情等其余页面仍用自绘 `RouteMap`，接线与否要按真机结论决定，不批量替换。
4. §45 位置上报队列（有界、失败不静默丢、与 §17 同批）。**分域复核（P1-2 ⑥）已通过**：上报只在司机域
   （调用点 `WorkbenchScreen.kt:445` / `DriverNavScreen.kt:313`，`reportOnce()` 在无班次/无 driverId 时一句不发，见 §64），
   本条剩下的仍是**队列本身**（有界与失败可见），不是分域。
   → **DONE（P1-3 ⑦，2026-09-29，但实现与原方案不同）**：**没有做重放队列** —— 后端 `AppDriverLocationReqVO`
   无客户端时间字段、`reportLocation` 用 `LocalDateTime.now()` 盖章，重放＝给旧位置盖新章＝假上报（D-019）。
   改为"合并语义（只发最新点）+ 45 秒新鲜度闸 + 丢点计数可见"，并把 `shiftId` 从硬拦改成可选（无班次也上报）。
   原方案的"失败不静默丢"这一半**做到了**（L-g=1 出口 + 工作台/通知都显示"已丢弃 N 个未确认点"），
   "有界队列 / 续报"这一半**有意不做**；缺的能力（弱网中间点恢复）记在 §45"本轮状态"的代价段，不算已解决。
5. §19 + §20 refreshToken 落地 + 401 刷新重放（顺序：先存 token，再写重放，最后统一"会话失效不得渲染成空"）。
6. §25 交接卡片字段 + `needPhoto` 死分支处理。
7. §29 角色切换补 `bindDriver` + `driverId <= 0` 不发司机请求 → **DONE（P1-2 ⑥，2026-09-29）**：
   三处全改（入口先过档案、`Repo.driverIdParam()` 做结构守卫、`Prefs.clearDriverBinding()` 断掉换账号残留），
   并顺带把复核发现的 **§64** 三处串域修掉（用户版退出回写角色、司机通知落司机页、Intent 深链角色闸）。
   `DomainSplitTest` 7 例 + `bash tools/check_domain_probes.sh` 判据；司机壳顶部专用横幅未做（沿用各 tab 既有 `ErrorState`），已如实记在 §29"本轮状态"。
8. §32 到站距离与 GPS 门槛（展示优先道路值，按钮可用性不绑 GPS）→ **DONE（P1-1b，2026-09-29）**：
   门槛删除后 `canArrive|ARRIVE_RADIUS_METERS` 全工程 0 命中（§55 F 实测），距离文案道路值优先、直线值必带标注并有断言；
   原方案的 Robolectric body 断言未做，已如实记在 §32"本轮状态"里。
9. §40 后端 `/send/page` 加 status + 寄货 tabs 换成货运状态集（过渡期先隐藏）。
10. §43 `work()` 重入保护 + 幂等冲突文案特殊处理。
11. §50 待办：`app-debug.apk` 移出仓库根。

## 60. 修复顺序 · P2 / 工程资产

1. §49 契约测试资产（`src/test/resources/contract/` fixture + "全字段非默认值"守卫测试）——这一条能把 §25/§34/§35/§36 类问题在 CI 里一次性锁死，性价比最高，建议紧随 P0 之后做。
2. §42 未接线接口处置（`subscribeTemplates` 标注或移除）。
3. `THIRD_PARTY_NOTICES.md`（高德 3dmap / GraphHopper / OSM / 逆地理与天气数据源的许可证与归属；只提取实际用到的模块，保留原始许可文本）。
4. 真实道路离线缓存：本地缓存**必须带原始 provider 与抓取时间**，回放时来源不变（不许把缓存标成实时，也不许把 OSM 缓存伪装成 AMAP）。
5. 调试用"路线真实性 HUD"（debug-only：显示当前折线 provider、点数、`routeComplete`、截断位置）。
6. 未签名 release 产物流程 + §55 命令进 CI。
7. **测试框架投入（⑥/⑦ 两轮共同暴露的缺口）**：工程只有 JVM 单测，**没有 Robolectric / Compose UI 测试**，
   于是 §29/§64（角色与深链）、§17/§45（`Location` / `SystemClock` / 前台服务重试环 / 通知文案）
   这几轮的 Android 侧行为**只能靠 grep 判据 + 代码走查 + 真机回归**（§57 三行 `ENVIRONMENT_BLOCKED`）。
   补法：引 `testImplementation(libs.robolectric)` + `androidTestImplementation(compose-ui-test)`，
   然后按 §17/§29/§32/§45 各条"测试方案"里**原本就写好但没做**的 Robolectric 用例逐条补齐。
   这是**依赖与工程配置**投入（要过 `assembleDebug`/`lintDebug` 与 GBK 路径那套坑），不是几行代码，
   所以排在这里而不是某轮的"顺手做掉"。

## 61. 这一轮"完成"的标准（不给含糊结论）

- 本文档 §17–§52 的每一条都在**代码或决策记录**上有对应处置：修掉 / 明确不做并记原因 / 记 `BLOCKED`；三选一，不允许"待优化"这种第四态。
- 每条 P0 修复必须同时交付：实现 + 对应测试（§54/§55 的命令可复跑）+ `DECISION_LOG.md` 条目。
- §55 分组判据（口径不同，引用时要说明是哪一条）：
  **C 窄口径的目标不是 0**（⚠ 本行原写"必须为空（实测 4 行）"，那句已不成立也不该当现状抄：⑩/⑪ 实测 4 → ⑫ 起 **9**，
  涨的 5 行全是口径说明注释，判据语义见 §55 C 抬头）、**E 安全组必须为空**（`.kts` 那条除注释与构建期拦截外必须为空；
  key 那条**引用 §55 E 的完整命令**（它带 `grep -v "@string/amap_key\|BuildConfig.AMAP_KEY"`，实测 0；只按裸 token 数会得出 2 处合法引用），**F 必须为空**；
  **D（假步进）当前 6 处命中、目标 0**，在 §24 真修好之前它就是 `UI_ONLY` 的证据，禁止删判据来"通过"；
  G 是下限判据（数量随重构浮动，硬约束在测试用例里）；
  **H 是数字判据**（`bash tools/check_domain_probes.sh` 打印 8 个数字 + 退出码），预期组合见该块内的实测行；
  **I 是数字判据**（`bash tools/check_location_probes.sh`，⑦ 新增）：2026-09-29 实测
  **L-a 2 / L-b 0 / L-c 0 / L-d 0 / L-e 0 / L-f 0 / L-g 1 / L-h 0**，末行"定位与上报判据通过"、退出码 0；
  其中 L-a 是**恰好**而非"至少"（多一处换算＝转两次），L-g 是 `DriverLocationService.kt` 内"已丢弃"的出现次数恰为 1
  （判据范围是前台通知那一个拼装点，工作台 InfoRow 是第二个展示面、不在计数内 —— 不要把这条读成"全工程只有一处已丢弃"），
  负控制已实测：人为加一处 `Geo(l.latitude…)` 或手写偏移公式，L-b/L-e 当场报红并 exit 1。
  **J 是数字判据**（`bash tools/check_envelope_probes.sh`，P2-1 新增）：2026-09-29 实测
  **E-a 0 / E-b 3 / E-c 0 / E-d 0 / E-e 1 / E-f 1/1 / E-g 1 / E-h 1**，末行"契约信封判据通过"、退出码 0；
  逐条含义、"恰好"口径与负控制实测都写在 §55 J 块里，不要在别处复述成"全工程只有一处某字样"。
  **K 是数字判据**（`bash tools/check_status_probes.sh`，⑨ 新增）：2026-09-29 实测
  **S-a 0 / S-b 0 / S-c 0 / S-c2 2 / S-c3 6 / S-d 6 / S-e 10 / S-f 0 / S-g 0 / S-h 3 / S-i 64 / S-j 4 / S-k 17 /
  S-la 0 / S-lb 0 / S-lc 2 / S-ld 2 / S-le 2**，末行"状态映射判据通过"、退出码 0；
  硬判据是 **S-la/S-lb 归零 + S-le 恰为 2 且两处都带回读**，逐条口径（哪些是"恰好"、哪些是下限）写在 §55 K 块里。
  **L 是数字判据**（`bash tools/check_reachability_probes.sh`，⑩ 新增）：实测 21 条全绿 + 负控制 21 ✔，数字见 §55 L 块；
  ⑪ 复跑时 **R-g 改成只数 `data class Reachability` 类体**（`BusVehicle` 也有可空 `distanceKm`，整文件计数会串契约）。
  **M 是数字判据**（`bash tools/check_eta_probes.sh`，⑪ 新增）：2026-09-29 实测
  **T-a 0 / T-b 19 / T-c 8 / T-d1 0 / T-d2 2 / T-e 0 / T-f1 0 / T-f2 1 / T-g 0 / T-h1 2 / T-h2 0 / T-i 7 /
  T-j1 1 / T-j2 0 / T-j3 1 / T-j4 12 / T-j5 1 / T-k 9 / T-l 0 / T-m 4 / T-n 3 / T-o1 1 / T-o2 5 / T-p 3 / T-q 1**，
  末行"公交 ETA 判据通过"、退出码 0；负控制 `bash tools/eta_negative_controls.sh` **25/25 ✔**。
  硬判据是 **T-a 归零 + T-d1/T-d2 成对 + T-c 恰为 8**；T-a 的作用域**只到公交链路**，货运侧 4+6 处是该脚本打印的残留（⑫ 靶子）。
- 用例数只增不减：后端 `309 tests`；Android `testDebugUnitTest` 现在 **19 个测试类**（**这句是 ⑪ 当时的话，不要当现状抄**：⑫ 是 20 类 224、**⑬ 是 21 类 233**，见本节末尾两行与 `AI_ITERATION_STATE.md` 状态板）
  （生产化阶段 6 类，P0/P1 期间新增 `RealRoadRouteValidatorTest`/`RouteGuideContractTest`/`MissionMapTest`，
  P1-2 ⑥ 新增 `DomainSplitTest`，**P1-3 ⑦ 新增 `LocationReportPolicyTest`/`CoordinateTransformTest`/`LocatorCoordinateTest`**，
  **P2-1 新增 `ScalarEnvelopeContractTest`**，**⑨ 新增 `StatusMappingContractTest`(17)/`DriverLegStateMapperTest`(11)/`DriverLegRespContractTest`(4)**），
  2026-09-29（⑦）实测逐类为
  LocatorCoordinate **6** / CoordinateTransform **12** / RealRoadRouteValidator 16 / Api 11 / DomainSplit 7 /
  Repo 11 / RouteGuideContract 12 / DriverState **11**（⑦ 加 2 条报文用例，原 9）/ LocationReportPolicy **15** /
  MissionMap 18 / LoginViewModel 6 / ProductOrder 7 / SendOrder 8 = **140 tests、0 failures、0 errors、0 skipped**；
  2026-09-29（P2-1）实测**全量 157 tests / 0 failures / 0 errors / 0 skipped**（新增 `ScalarEnvelopeContractTest` 17 例）；
  **2026-09-29（⑨）实测 `app/build/test-results/testDebugUnitTest/*.xml` 汇总 = 17 类 / 189 tests / 0 failures / 0 errors / 0 skipped**
  （+32 = `StatusMappingContractTest` 17 + `DriverLegStateMapperTest` 11 + `DriverLegRespContractTest` 4）。
  P1-1 时两组 `24 tests`，P1-1b 后同两组 `30 tests`、全量 `98 tests`，⑥ 后全量 `105 tests`，**⑦ 后全量 `140 tests`，P2-1 后全量 `157 tests`**。
  `:app:assembleDebug :app:lintDebug` BUILD SUCCESSFUL（P2-1 最后一次改动后复跑，`BUILD SUCCESSFUL in 48s`，退出码 0），
  **计数读 `lint-results-debug.txt` 的末行 `0 errors, 48 warnings`** ——
  `lint-results-debug.xml` 的根节点**没有** `errors`/`warnings` 属性（实测 `None`），想自动读数只能用 txt 末行或数 `<issue>` 子节点（本轮 79 个节点 = 48 warning + 31 info，**不要**照抄别处的"XML 里读 errors 属性"）；
  **⑨ 最后一次改动后复跑 `:app:assembleDebug :app:testDebugUnitTest` → `BUILD SUCCESSFUL in 40s`（全量），
  文档收口后再增量复跑 → `BUILD SUCCESSFUL in 18s`、43 tasks 全 UP-TO-DATE、退出码 0**，
  `:app:lintDebug` 复跑 `BUILD SUCCESSFUL in 4s`（分析任务 UP-TO-DATE，报告对应 ⑨ 的代码状态），末行仍是 **0 errors、48 warnings**。
  **⑩ 实测全量 18 类 / 206 tests / 0 failures**（+17 = `ReachabilityContractTest`），lint 末行变成 **0 errors、46 warnings**
  —— 少的这 2 条**没有逐条追因**（没有 diff 两次报告，只核对了末行），所以别把 48 当基线抄，也不要写成"⑩ 修掉了两条 warning"。
  **⑪ 实测全量 19 类 / 215 tests / 0 failures / 0 errors / 0 skipped**（+9 = `BusEtaContractTest`；
  `assembleDebug` `BUILD SUCCESSFUL`、37 tasks UP-TO-DATE）；lint 复跑 **0 errors、46 warnings**
  （`lintAnalyzeDebug` executed 而 `lintReportDebug` UP-TO-DATE、报告字节数不变 31139 ⇒ 结论未变）。
  **⑫ 实测全量 20 类 / 224 tests / 0 failures / 0 errors / 0 skipped**（+9 = `SendEtaContractTest`，`cleanTest` 后仍需 `--rerun` 才脱离 UP-TO-DATE）；
  **⑬ 实测全量 21 类 / 233 tests / 0 failures / 0 errors / 0 skipped**（`--rerun` executed、21 份 XML mtime 全部 17:58:22；
  `+9` 里只能归因 `+7`：新建 `DistanceCaliberContractTest` 4 例 + `BusEtaContractTest` 9→12，**剩 2 条无法归因**，
  因为 ⑫ 只抄了汇总没抄逐类表、事后重建不了 ⇒ **从 ⑬ 起每轮必须抄逐类表**，表现在 `AI_ITERATION_STATE.md`"常驻自检"末行）；
  ⑫/⑬ lint 末行均 **0 errors、46 warnings**（⑬ 收工复跑 gradle 报 `lintAnalyzeDebug`/`lintReportDebug`/`testDebugUnitTest`/
  `compileDebugKotlin` **全部 UP-TO-DATE**，即按内容哈希确认产物对应当前源码 —— 本段上面 ⑪ 那条"对时间戳"的纪律已按此**收窄**：
  mtime 只能让人起疑，判"产物是否对应当前输入"要看 gradle 报 executed 还是 UP-TO-DATE，无歧义要 `--rerun-tasks`）。
  ⚠ **⑪ 踩到的时间戳坑，写进流程**：那一轮我先把"215 tests / 46 warnings"抄进台账时，测试 XML 是 14:05 的、
  lint 报告却停在 13:55（比最后一次 `DemoData` 编辑早 36 秒）—— **每个构建产物各自对时间戳**，
  不能因为"这轮跑过 gradle"就把几个数字一起当成新鲜的；对不上就复跑再抄。
  **口径说明**：工程里没有 Robolectric，所以 §29/§30/§31/§32/**§17/§45** 各条"测试方案"里写的 Robolectric 用例
  一律以 **JVM + MockWebServer 的 ViewModel/Repo/纯函数层用例**替代（`LoginViewModelTest` 已证明 `viewModelScope` 可测），
  纯 Compose 渲染断言与 Android 框架对象（`Location` / `SystemClock` / 前台通知）**没有测试覆盖**，
  靠 grep 判据 + 真机回归，标 `ENVIRONMENT_BLOCKED`（§57 ⑦ 新增三行）。
- 不声称"100% 完成"或"全部对齐"：真实导航与真机相关项在 §57 里仍是 `BLOCKED`/`ENVIRONMENT_BLOCKED`，这些状态会一直保留到有实际依赖与设备为止。

---

## 62. `/driver/route` 任务点字段整体没被读：地图标记与任务列表拿不到序号 / 动作 / 件数 / 状态

判定：`CONTRACT_MISMATCH`（P1-1 的前置项，不修就只能画出"匿名圆点"）

- **文件**：`app/src/main/java/com/shanxiang/buspost/data/repo/Repo.kt`、`app/src/main/java/com/shanxiang/buspost/data/model/Models.kt`
- **类 / 函数**：`Repo.routeGuideOf`(`:866` `o.multiArr("stations","stops").map { Station.parse(it) }`)、`Station.parse`(`Models.kt:426-451`，键名集合是 `sequenceNo|seq|sort` / `orderCount|cargoCount|count` / `plannedMinutes|minutes|offsetMinutes`)
- **当前行为**：`/driver/route` 的 `stops[]` 被塞进**给公交站用的 `Station`** 里解析。后端任务点字段一个都不在 `Station.parse` 的键名集合内 → `sequenceNo = 0`、`orderCount = 0`、`plannedMinutes = 0`，而 `actionName / status / statusName / orderId / orderNo / estimatedArrivalTime` **在 `Station` 模型里连字段都没有**。结果：任何按 `guide.stations` 画的任务点都是"位置正确、内容全空"的圆点，页面上无法区分第 1 站和第 5 站，也读不出这站是揽收还是派送、几件货、做没做。
- **后端真实行为**：`AppDriverRouteRespVO.java:90-112` 的 `Stop` 下发
  `stationId / stationName / longitude / latitude / visitSequence / actionType / actionName / orderId / orderNo / quantity / estimatedArrivalTime / plannedDepartureTime / status / statusName`；
  赋值点 `DriverAppServiceImpl.java:487-503`（`statusName` 走 `TaskItemStatusEnum.nameOf(status)`，
  状态码 0 待执行 / 1 行驶中 / 2 已到站 / 3 上车中 / 4 下车中 / 5 揽收中 / 6 派送中 / 7 已完成 / 8 失败，见 `enums/dispatch/TaskItemStatusEnum.java:26-34`）。
  注意这是**另一套状态**，与 `TransportLegStatusEnum`(0..11+99，§28) 不同源，不能混用同一张文案表。
- **小程序行为**：`pages/driver/workbench/workbench.js:218-248` 明确按 `visitSequence` 排序、逐站读
  `actionName / quantity / statusName`（`:240` 还有 `statusName || '待执行'` 的兜底），
  并且按 `planId` 分组、同站聚合动作。这一部分是**符合契约的**，可以作为 UX 参考（区别于 §48 那 9 条不复刻项）。
- **问题**：① 这是"静默显示 0 / 空"那一类，不报错、不崩溃，所以此前一直没人发现；
  ② 它直接挡住 P1-1：任务点 Marker 的业务化（序号 / 动作 / 件数 / 状态配色）在数据层就没有输入；
  ③ 把两类语义不同的东西（公交站 vs 调度任务点）压进同一个 `Station`，是 §49 命名漂移的又一实例，
  而且 `Station.parse` 同时服务 `/bus/nearby`，**不能**为了任务点去改它的键名集合——那会让公交侧的 `distanceKm` 等既有兼容逻辑一起被动。
- **修改方案**：
  1. 新增 `data class MissionStop(id, name, geo, visitSequence, actionType, actionName, orderId, orderNo, quantity, status, statusName, estimatedArrival, plannedDeparture)`
     + `MissionStop.parse(JSONObject)`，键名**严格按 `AppDriverRouteRespVO.Stop`**，只额外兼容 `stationName|name`；
     `statusName` 优先用后端串，本地兜底表按 `TaskItemStatusEnum` 的九码建（不建 0..n 顺序表，避免重演 §18）。
  2. `RouteGuide` **增加** `stops: List<MissionStop>` 字段（默认 `emptyList()`），`stations` 原样保留不动 ——
     遵守"不要删除字段以免破坏旧兼容"；`Repo.routeGuideOf` 同一数组同时产出两者，公交侧继续用 `stations`，任务地图用 `stops`。
  3. `ui/driver/DriverMissionMap.kt` 只读 `stops`；标记内容 = `visitSequence` + `actionName` + `quantity` 件 + `statusName` 配色，
     配色由 `status` 决定（未完成 primary / 当前 clay / 已完成 gold / **8 失败必须 danger 且不得显示成"已完成"**）。
     排序只用后端给的 `visitSequence`，客户端不得重排任务点（硬规则 3：调度决定去哪里）。
  4. 状态推进仍然必须"司机主动动作 + 后端确认回读"（硬规则：禁止 GPS 自动完成业务）；
     `status == 2 已到站` 在地图上只是颜色与信息展示，**不触发**任何写操作。
- **测试方案**：
  1. `MissionStopParseTest`：用后端真实字段名写一条 fixture（`visitSequence/actionName/quantity/status/statusName`），
     断言五个值全部非默认；再断言 `status = 8` 的文案是"失败"而不是"已完成"（钉住 §18 那类顺序表错误）。
  2. 反例守卫：把 fixture 的键名换成 `seq/count/status` 之类旧写法，断言 `visitSequence/quantity == 0`
     —— 让"键名漂移就当场红"，而不是静默显示 0。
  3. `RouteGuideContractTest` 补一条：`/driver/route` 响应同时被 `stations` 与 `stops` 读到，
     `stops.size == stations.size`，且 `stations` 侧既有断言不回归。
  4. 真机（`ENVIRONMENT_BLOCKED`）：地图标记的序号顺序必须与 `RoutesScreen` 列表一致，同站多单聚合正确。

**本轮状态（P1-1 + P1-1b，2026-09-29）：Android 侧已修**
- 1–3 步全部落地：`MissionStop`（键名严格按 VO，兜底表按 `TaskItemStatusEnum` 的九码建，不做顺序表）、
  `RouteGuide.stops`（带默认值，`stations` 原样保留）、`MissionMarkers.ordered/timelineOf/stateOf` +
  `DriverMissionMap` 的任务点条与 `WorkbenchScreen` 的「调度任务点」时间线。
  顺序的唯一来源是 `visitSequence`，UI 侧不再各排各的；`FAILED` 作为 `StopState` 第四态进 `StationTimeline`
  （失败点红、与"已完成"的绿分开，连接线也不画成已走通）。
- 4 步落地：状态推进仍然只有 `act("到站") → Repo.arrive → syncFromServer 回读`；
  时间线里的"已完成"计数只数后端 `status == 7` 的点（`ShiftCard` 的 `x/N 已完成`），GPS 与点击都不参与。
  `status == 2 已到站` 在地图上只有一种用途：颜色与详情文案，不触发任何写操作。
- 测试实做：`MissionMapTest` 18 例（新增 6 例覆盖时间线序号/动作件数/`status=7` 唯一完成/`FAILED` 优先于导航目标/
  ETA 缺失不显示/距离文案）+ `RouteGuideContractTest` 12 例 → `30 tests, 0 failures`；全量 `98 tests, 0 failures`。
- 仍待后端联调（`ENVIRONMENT_BLOCKED`）：本地库 `/driver/route` 的 `stops[]` 多为空，
  九个字段的端到端真值（尤其同站多单聚合）只能在有真实班次数据后核对；**不得**为了让地图"看起来有内容"造演示 stops（§46、硬规则 18）。

---

## 63. 导航 HUD 把后端**直线**剩余距离当"剩余里程"显示 —— 违反"UI 只准读 `*Road*` 系列"

判定：`CONTRACT_MISMATCH`（静默错标签，`CONTEXT_LOCK.md` §四 字段兼容规则违规）

- **文件**：`app/src/main/java/com/shanxiang/buspost/ui/driver/DriverNavScreen.kt`
- **类 / 函数**：`DriverNavScreen` 的转向 HUD 渲染块（原 `:244`，`NavHud(remaining = …)` 实参）
- **当前行为（修前）**：
  `remaining = if (g.nextStationDistanceMeters > 0 && vm.stepIndex == g.currentStep) g.nextStationDistanceMeters else step.distanceMeters`
  —— 把 `RouteGuide.nextStationDistanceMeters` 放在"剩余里程"这个位置上直接显示。
  而这个字段的 KDoc 自己就写着"后端 `nextStopDistanceMeters` 是 Haversine 直线距离，不是道路里程。
  保留只为不破坏兼容，**正式界面禁止读它**"（`Models.kt:1164-1168`），
  即代码行为与同一文件里的禁令相矛盾；`CONTEXT_LOCK.md` §四 的"新旧字段并存时 UI 只准读 `*Road*` 系列"同样被破。
- **后端真实行为**：`AppDriverRouteRespVO` 同时下发 `:76 nextStopDistanceMeters`（直线，仅示意）与
  `:81 nextStopRoadDistanceMeters`（真实路网值，来源受 `RealRoadProviders` 白名单约束）。两者语义不同，不可互换。
- **小程序行为**：司机导航只调 `wx.openLocation` 交给外部地图（§12），小程序里没有"剩余里程"这一栏，
  所以这一条**没有对齐对象** —— Android 是自家实现读错字段，不是复刻小程序。
- **问题**：① 硬规则 7/8 的 UI 侧违规：直线值出现在里程位置，且**没有任何标注**（对比 §32 允许的是"直线约 N 米（仅表示远近，不是道路里程）"这种带标注写法）；
  ② 与同页 `:279/:284` 的诚实文案自相矛盾 —— 那里写着"旧字段是直线值，不显示"，HUD 却在显示；
  ③ 司机按剩余里程判断加油/接续时效，直线 800 米在实际绕行 2.4 公里时会误导；
  ④ 属"静默错标签"类，不崩溃、不报错，因此此前 61 节审计也没抓到 —— 记这一条补上。
- **修改方案**：HUD 只读道路字段：
  `remaining = if (g.nextStopRoadDistanceMeters > 0 && vm.stepIndex == g.currentStep) g.nextStopRoadDistanceMeters else step.distanceMeters`；
  `nextStationDistanceMeters` **字段与解析保留**（不删字段以免破坏旧兼容），但 UI 读点必须为 0；
  `step.distanceMeters` 分支不变（真实构建里后端不下发 `steps`，HUD 因 `step == null` 不渲染；该分支只在受 `DEMO_ALLOWED` 约束的演示面出现，属 §46 台账）。
  判据用 grep，不靠自述：`grep -rn "nextStationDistanceMeters" app/src/main/java/com/shanxiang/buspost/ui` → **必须为空**。
- **测试方案**：
  1. 静态判据（已实测）：上述 grep = 0 命中；`data` 层仍有 1 处声明 + 1 处解析（兼容性保留，允许）。
  2. 契约层已有断言不回归：`RouteGuideContractTest:174` 钉住"后端给直线 7300 时 `nextStationDistanceMeters=7300` 且 `nextStopRoadDistanceMeters=0`"
     —— 一旦有人把 HUD 改回读直线字段，这条测试仍能证明"直线字段确实存在"，而 grep 判据负责证明"UI 没在读"。
  3. 真机（`ENVIRONMENT_BLOCKED`）：HUD 数值应与地图上真实折线的剩余段一致，与直线距离明显不同的弯道路段做对照。
- **本轮状态**：已修（P1-1b）。全量 `:app:testDebugUnitTest` 98 tests 0 failures、`assembleDebug` BUILD SUCCESSFUL。
  **注意**：`§24` 的假步进（`stepping/delay(4_000)/stepIndex+1`，6 处命中）与 `crossTrackMeters()` 本地偏航判定**仍在该文件里未动**，
  本条只修了里程字段选择，不得据此把 §24 标为已修。

---

## 64. 司机域与用户域在三处互相串门：退出"用户版"不改角色、司机通知深链乘客页、Intent 深链绕过角色

判定：`LOCAL_ONLY`（P1；硬规则 1"用户端与司机端是两个业务域，必须分开"的 UI/后台侧违规 —— **本轮已修**）

- **文件**：`ui/driver/DriverShell.kt`、`notify/AlertEngine.kt`、`ui/BusPostRoot.kt`、`nav/Routes.kt`
- **类 / 函数**：`DriverShell`(`:32-43`，tab `it == 3` 的退出口)、`AlertEngine.runOnce` 的司机分支(`:37-58`)、
  `BusPostRoot` 的深链 `LaunchedEffect`(`:74-81`) 与新增的 `deepLinkAllowed`(`:183-184`)、`Routes.DRIVER_ONLY`(`:34-37`)
- **当前行为（修前）**：
  1. 点"用户版"只 `nav.navigate(Routes.USER_SHELL)`，**不写 `Prefs.role`**。于是屏幕上已是乘客端，
     `AlertEngine`（前台 20 秒轮询 + `NotifyWorker` 15 分钟）仍按 DRIVER 分支拉 `/driver/messages`，
     用户端铃铛角标也取 `AppState.driverUnread` —— 数字是真的，含义是错的。
  2. 司机通知的 `route` 拼成 `"trace/$orderNo"`，即**村民端订单轨迹页**；司机点一条调度通知被扔到乘客侧页面，
     而真正有已读/运单动作的司机消息中心（`Routes.DRIVER_MESSAGES`）反而没有入口。
  3. 深链没有角色闸：`AppState.pendingRoute` 里只要残留着上一个角色时期点剩的司机路由
     （`driver/nav` / `driver/messages` / `handover`），用户会话点一条通知就直接落进司机工作台，
     页面随即拿 `Prefs.driverId` 发司机请求 —— 这是"角色没切但进了司机页"的唯一通道，
     §29 的页面级守卫拦不住它（守卫在页面里，路由已经把人送进来了）。
  4. **复核通过项**（记在这里免得被当成待修）：位置上报只在司机域 —— `driver/DriverLocationService` 的两个调用点
     `ui/driver/WorkbenchScreen.kt:445` 与 `ui/driver/DriverNavScreen.kt:313` 都在司机壳内，用户域零调用点；
     且 `reportOnce()`(`:93`) 在 `Running.shiftId <= 0 || Prefs.driverId <= 0` 时**一句不发**并写明"当前没有在途班次，发车后再上报位置"
     （`start()` 只校验定位权限，不承担分域职责）。§45 的"上报串域"担忧经逐调用点核对**不成立**。
- **后端真实行为**：`AppDriverController.java:226-232` 的 `/driver/messages` 里 `driverId` 是 `required = true`，
  且第一句就是 `driverAppService.requireCurrentDriver(driverId)`；该方法的契约写在 `DriverAppService.java:91-96`
  —— "以登录会员身份解析当前司机，并校验传入 driverId 与之一致，否则任意会员可越权拉取该司机的站内通知
  （订单号 / 收件人手机等 PII 泄露）"。所以带着 0 号或别人的 `driverId` 去问，**要么业务异常要么被拒**，
  前端拿到的空列表不代表"没有消息"（与 §29 同根）。用户侧通知是另一套会员维度接口
  （`AppNotificationController`），两套未读数不能共用一个角标。
- **小程序行为**：`components/driver-tab-bar/index.js:41-44` 退出用户版走 `wx.switchTab('/pages/index/index')`，
  小程序没有"本机角色"变量，域由**页面路径**决定，切走即不再跑司机侧轮询；
  `pages/notification/notification.js:184-188` 的注释就是这条的正解——"司机消息（任务/交接提醒）里的 `orderId`
  是货运单 id，没有对应的村民端页面：回工作台看任务"，`driverMode` 下一律 `navigateBack()`；
  `pages/driver/workbench/workbench.js:147/603/669/799` 每个司机动作前 `if (!this.driverId) return`，
  `:163-168` profile 为空时 toast"未找到司机档案，请联系管理员"并且一个司机请求都不发。
  小程序不存在 Intent 深链，第 3 条**没有对齐对象**（Android 自家实现自家违规）。
- **问题**：① 角色是本机状态，却决定后台发哪一域的请求 —— 状态与路由不同步就是"屏幕乘客、后台司机"；
  ② 司机通知落到乘客页 = 通知点进去看不到可做的动作，还顺手把货运单 id 塞进村民端路由（§41：寄货单在村民端无详情入口）；
  ③ 深链绕过所有页面级分域守卫，也让 §29 的 `driverId=0` 后果在用户会话里复现；
  ④ 未读数串域属于"静默错标签"，不崩溃不报错，所以 §4/§5 的矩阵里此前没有任何一行抓到它。
- **修改方案**（全部已落地）：
  1. `DriverShell.kt:38` 退出前 `AppState.setRole(Role.USER)`，注释写明"本机角色决定后台提醒读哪个域的接口"；
     `Prefs.role` 是 `AlertEngine` 唯一的分支依据，所以这一行就把后台轮询带回用户域。
  2. `AlertEngine.kt:79-84` 司机分支：`route = Routes.DRIVER_MESSAGES`，`orderNo` 进正文（`"${content}（运单 $no）"`），
     不再拼 `trace/*`；用户分支(`:60-89`)继续按 `orderNo → trace/*` 走（那是村民端合法入口）。
     判据用内容而不是行号：`grep -c 'route = Routes.DRIVER_MESSAGES'` = 1，且司机分支内 `trace/` 命中 0。
  3. `Routes.kt:78-81` `DRIVER_ONLY = setOf(DRIVER_NAV, DRIVER_MESSAGES, HANDOVER)`；
     `BusPostRoot.kt:82` 深链条件加 `&& deepLinkAllowed(route)`，`:183-184`
     `route !in Routes.DRIVER_ONLY || Prefs.role == Role.DRIVER` —— 不允许就留在当前页，**不猜、不跳、不崩**。
  4. 用户进司机域必须先过后端档案：`MineViewModel.enterDriverMode`(`MineScreen.kt:111-123`)（§29 ①）。
  5. 身份残留：`Prefs.clearDriverBinding()` 在 `Repo.saveSession` 里先清，后端回"非演示且有条目却无 driverId"时跟着掉（§29 ③）。
- **测试方案**：`DomainSplitTest`（JVM + MockWebServer；工程**没有 Robolectric**，见 §61 的口径说明）——
  第 1 例断未绑定时 7 个 GET + 5 个 POST 全部 `requestCount == 0`；第 4/5/6 例分别断换账号不继承、
  非空档案无 driverId 掉绑定、`data:null` 与演示回落**保留**绑定；第 7 例断 `enterDriverMode` 两分支。
  常驻判据：`bash tools/check_domain_probes.sh` 的 P-c（角色切换入口=2）/ P-d（深链闸=2）/ P-e（司机通知落司机页=1）。
  **未做（如实记）**：`DriverShell` 退出与 `deepLinkAllowed` 是 Compose / 导航侧逻辑，没有 UI 测试框架能覆盖，
  目前只有 grep 判据 + 代码走查；真机回归（点通知、杀进程后从通知栏重进、换账号）标 `ENVIRONMENT_BLOCKED`。
- **本轮状态**：已修（P1-2 ⑥，2026-09-29）。数字见 §61 与 `AI_ITERATION_STATE.md` 的自检表。

---

## 65. 折线没有"渲染前的坐标系边界"，且换算器里藏着一个 700 m 的偏移项缺失 —— ⑦ 把 `CoordinateTransform` 变成活代码时暴露

判定：`CONTRACT_MISMATCH`（P1，⑦ 前置新条目；**已修**。矩阵里此前没有任何一行覆盖"折线的坐标系"，只覆盖了"定位的坐标系"）

- **文件**：`core/map/CoordinateSystem.kt`、`core/map/RealRoadRouteValidator.kt`、`core/map/RouteRepository.kt`、`data/repo/Repo.kt`
- **类 / 函数**：`CoordinateTransform.gcj02ToBd09`(`:96-101`)、`bd09ToGcj02`(`:103-108`)、`inChina`(`:60`)、
  `RealRoadRoute`(`:26-52`，含新增 `gcj02Points` getter)、`Repo.routeGuideOf` 的 `polyline` 与 `heading` 两处
- **当前行为（修前，两处）**：
  1. **折线没有坐标系边界**：`RealRoadRoute` 只暴露 `points`（原始来源坐标）与 `crs` 标签，校验器认真地解析并保留了
     `crsRaw`，但**没有任何地方消费它**。`Repo.routeGuideOf` 直接 `polyline = accepted?.points`，
     `RouteGuide.polyline` 因此可能是 WGS-84；`heading` 也是拿 `accepted.points`（可能是 WGS-84）与
     `vehicleGeo`（⑦ 之后确定是 GCJ-02）比方向 —— 一个**跨系相减**。
  2. **换算器里藏着错**：`gcj02ToBd09` 只做了极坐标旋转，**漏掉了百度固定偏移 `+0.006` / `+0.0065`**，
     `bd09ToGcj02` 同样没减回去。实测往返残差 **6.5e-3 度 ≈ 700 m**（`0.006°`≈667.9 m、`0.0065°` 在重庆纬度≈629.8 m）。
- **后端真实行为**：`AppDriverRouteRespVO.java:59-61` 的 `polyline` 与 `currentLongitude/currentLatitude` **同为 GCJ-02**（§17 已记）；
  算法服务侧 `docs/REAL_ROAD_ROUTING_ARCHITECTURE.md` 明确 GraphHopper 吃 WGS-84、出 WGS-84，
  Python 链路里"GCJ↔WGS 只转一次"由 `test_route_falls_back_to_graphhopper_and_returns_gcj02` 钉住；
  后端下发的 `crs` 字段是**给客户端用的标签**，不是装饰 —— 客户端不读它，标签就白标了。
- **小程序行为**：小程序不做本地路径计算，`polyline` 一律直接喂给高德组件（同系），所以**它没有这个问题**，
  也没有对齐对象 —— 这是 Android 独有的跨系风险（GraphHopper/OSM 这一档在客户端侧才可能带 WGS-84 标签）。
- **问题**：① 一旦某条折线来源声明 `crs=WGS84`（GraphHopper 直连、离线缓存回放、或后端将来把算法服务的原始输出透传），
  客户端会把它画在 GCJ-02 底图上，**整条线偏移约 500 m 且看起来完全正常** —— 不崩、不报错、不出现"路线不可用"，
  司机跟着偏掉的线开车，这正是 §4/§5 类"静默错标签"里最贵的一种（硬规则 5/20 的视觉面违规）；
  ② `heading` 跨系相减给出的航向可以错几十度，转向卡的"下一个动作"方向感随之失真；
  ③ BD-09 那条在 ⑦ 之前**没人撞上**，因为 `CoordinateTransform` 生产调用点是 0（死代码里的错不会被使用暴露）。
  把死代码变活之前必须先审它 —— 否则等于把一把没校对过的尺子发下去量。
- **修改方案**（全部已落地，遵循"不删字段、不改协议"）：
  1. `RealRoadRoute` 新增 `val gcj02Points: List[Geo] get() = CoordinateTransform.convertAll(points, crs, GCJ02)`；
     **`points` 保持原样**（原始来源 + `crs` 标签是可审计事实，不许就地改写），换算只发生在读的一侧；
     同系时 `convertAll` 原样返回（有测试钉住"GCJ02 折线不得二次偏移"）。
  2. `Repo.routeGuideOf` 的 `polyline` 改读 `accepted?.gcj02Points`，`heading` 也改在 GCJ-02 空间里算
     —— 于是**渲染层拿到的每个点都与底图同系**，跨系比较归零。
  3. `gcj02ToBd09` / `bd09ToGcj02` 补回固定偏移项，并在函数上写明"少加就是约 700 米的系统性偏差"；
     `inChina()` 的**矩形局限**不假装解决，改为写测试钉住（见下），并在 §17"本轮状态"与 `CONTEXT_LOCK.md` §九 各留一行。
  4. 判据固化（§55 I 组）：**L-a = 2**（`CoordinateTransform` 恰好两个生产调用点：`Locator.toGcj02` + `gcj02Points`）、
     **L-h = 0**（不许绕过 `convert()` 直接调底层偏移函数，否则 `inChina` 判定与系标签会被跳过）、
     **L-e = 0**（手写偏移公式不得越出 `CoordinateSystem.kt`）。
- **测试方案**：`CoordinateTransformTest` 12 例（JVM，无需 Robolectric）——
  `换算给出实测的那一个点`（重庆 `(29.5325,106.5765)` → `(29.529603,106.580187)`，并断言偏移落在 `100..900 m` 区间）、
  `WGS 与 GCJ 往返误差不超过一厘米`（1e-9 度）、`BD09 与 GCJ02 互为反算`（1e-5 度；**修前是 6.5e-3 度，这条就是抓出漏项的那条**）、
  `境外点一律不偏移`、`已知限制：国境判断是矩形，东南亚点也会被偏移`（`(15.0,105.0)` —— **把局限钉住而不是隐藏**）、
  `同系换算原样返回`、`convertAll 保序等长并逐项与单点换算一致`、`坐标系标签解析覆盖后端与引擎的真实写法`、
  `来源声明 WGS84 的折线画到底图前必须换算，但原始点保持不变`、`来源声明 GCJ02 的折线不得二次偏移`。
  **未做（如实记）**：折线偏移在**真机上是否可见**（把同一班次画到高德底图上与小程序对比）仍是 `ENVIRONMENT_BLOCKED`；
  后端目前对 `/driver/route` 的 `polyline` 一律标 GCJ-02，所以"crs=WGS84 的真折线"这条路径**还没有真实数据穿过**，
  现在是契约前置防御，端到端要等 GraphHopper 直连下发原始系数据时复测。
- **本轮状态**：已修（P1-3 ⑦，2026-09-29，D-019）。与 §17/§45 一并记入 `CONTEXT_LOCK.md` §九。

---

## 66. 可达性：字段名/单位/来源三处口径错，错误值还会**写进下单报文** —— ⑩ 按后端 VO 重做时顺带推翻本审计自己的一条建议

判定：`CONTRACT_MISMATCH`（P0，**已修**）→ 附带两条方法论问题（`UI_ONLY` 判据太弱 / 台账建议与源码冲突）

- **文件**：`data/model/Models.kt`（`Reachability`）、`data/repo/Repo.kt:542`、`data/repo/DemoData.kt:490`、`ui/user/SendScreen.kt`
- **修前行为**：读 `reachable|ok`、`nearestStation|stationName`、`distance|distanceMeters`、`walkMinutes|minutes`；
  界面拿布尔 `reachable` 分支、把 `distanceMeters/1000.0` 当公里显示（后端本来就是**公里**，于是差 1000 倍），
  `originalAddress` 提交 `reach?.nearestStation.orEmpty()`（恒空），`pickupServiceMode` 写死 `"STATION"`。
- **后端真实行为**（逐个复核过源码，不照抄本台账）：`AppSendReachabilityRespVO.java:19-56` —— `reachable(Boolean)` /
  `reasonCode(String)` / `serviceMode(String)` / **`distanceKm`（BigDecimal，公里）** / `walkMinutes(Integer)` /
  `distanceProvider(String)` / **`recommendedStation` 是嵌套对象** `{id,name,longitude,latitude,distanceKm}` / `message(String)`。
  `AppSendReachabilityService.java`（逐行复核）：`REACHABLE_RADIUS_KM = 0.3`(`:42`)、步行速度 `1.2 m/s`(`:44`)、
  三条早退分支 `:53-57`（没给位置）/ `:68-72`（没站点）/ `:93-97`（拿不到道路距离后仍无站点）、
  `provider` 初始 `"HAVERSINE"`(`:103`)、来源白名单判定 `RealRoadProviders.isRealRoad(...) ? "AMAP_ROAD" : "EUCLIDEAN"`(`:122`)、
  **可运最终由道路里程重新判定** `reachable = km <= REACHABLE_RADIUS_KM`(`:129`)、
  `reasonCode` **有三个值**（`ROAD_UNREACHABLE` `:55` / `NO_SAFE_HANDOFF_POINT` `:70` 与 `:95` / `USER_LOCATION_UNREACHABLE` `:138`，
  且可运时 `:138` 显式置 null）、`serviceMode` 在 `DOOR_PICKUP`/`NEAREST_STATION` 二选一(`:139-140`)、`walkMinutes` 公式(`:152-154`)。
  这**推翻本文件 §34 原记的两处**：① `distanceProvider` 不是"两个来源值"而是**三个**（`AMAP_ROAD`/`HAVERSINE`/`EUCLIDEAN`，
  且后端自己承认默认给的是直线估算）；② `reasonCode` 不是两个而是**三个**（台账漏了"没给位置"那条 `ROAD_UNREACHABLE`）。
  VO 的 `@Schema`（`:22`/`:34`）也漏列，属注释漂移 —— 以代码为准（§5）。
- **本审计自己的建议也是错的（§5 的又一次现场兑现）**：§34 修改方案 ③ 建议"`originalAddress` 改为提交推荐站点名 + 详细地址"。
  `AppSendOrderCreateReqVO.java:60` 的注释写的正是"**位置 ≠ 车辆能到的地方：原始地址与服务站分开保存，不互相覆盖**"，
  `TransportOrderServiceImpl:172` 原样落库 —— 照建议做等于把"站点名冒充用户位置"这个错固化。
  实际做法：`originalAddress` 只填用户自己的位置（`Locator` 坐标 → `Repo.reverseGeo`，`formatted` 兜 `displayName`），
  站点走 `pickupStationId`；"禁止提交"换成两条更小的闸（`validateStep1` 要求取货站点非空；
  `pickupServiceModeOrNull` 只在服务方式合法**且**其推荐站点就是本单取货站点时才回传，否则丢键由服务端推导，
  依据 `TransportOrderServiceImpl:202-203`"客户端值非空才采信"）。空 `originalAddress` 不拦提交 —— 小程序 `send.js:709`
  本身就是 `originalAddress || ''`；但 UI **不再藏这一行**（R-p）。
- **`reachable = true` 分支原来会静默**：后端判定可上门却没给出站点时，卡片只显示"可上门"不说去哪个站。
  补成与不可达分支对称的手选提示（R-o=1 / R-o2=2），**这是本轮实测出来的、台账没写的第 4 处**。
- **判据太弱这件事是负控制抓出来的**（写进 §55 L 组）：`grep -c "@Test"` 会把注释掉的 `//@Test` 一起数（用例被删光也"通过"）、
  `grep -c "Reachability.parse("` 数的是**行**，两个出口挤一行就看不见 —— 改成 `^[[:space:]]*@Test` 与 `grep -o | wc -l` 后，
  两条负控制才从"失效"变"报红"。同轮实测：**21 条判据全绿的源码可以同时编译不过**（误删一个换行把两行并成一行），
  所以内容探针只证语义、`assembleDebug` 才证可构建，谁也不能替谁。
- **测试与实测数字**：`ReachabilityContractTest` 17 例（MockWebServer 报文往返；含 §49 的逐字段"非默认值"守卫、
  缺键/显式 null/非布尔三态、`recommendedStation` 只给 id 没有 name 时不编名字、请求体只带经纬度）；
  `tools/check_reachability_probes.sh` R-a 0 / R-b 0 / R-c 0 / R-c2 1 / R-d 0 / R-e 25 / R-f 2 / R-g 2 / R-h 0 / R-i 1 /
  R-j 17 / R-k 1 / R-k2 0 / R-l 5 / R-m 2 / R-n 3 / R-n2 0 / R-n3 2 / R-o 1 / R-o2 2 / R-p 1（"可达性判据通过"，exit 0）；
  `tools/reach_negative_controls.sh` 21 条破口全部 ✔；全量 **206 tests / 0 failures / 0 errors / 0 skipped**（18 类）、
  `assembleDebug` BUILD SUCCESSFUL、`lintDebug` **0 errors, 46 warnings**（比 ⑨ 的 48 少 2：`Models.kt` 的 `String.format` 补了显式 `Locale`）。
- **未做（如实记，`ENVIRONMENT_BLOCKED`）**：① 真机 `reachable=true` 时自动选中推荐站点、`false` 时点"使用推荐站点"的可见行为
  —— 无 Robolectric，Compose 渲染只能靠内容探针；② 后端 `distanceProvider` 在生产是否真回 `AMAP_ROAD` 需连 48080 实数据复测；
  ③ 逆地理没配 key 时 `originalAddress` 为空的那条路径只能在真机上看"文字地址未取到"这行是否真出现。

---

## 67. 后端 `ReviewReasonCodeEnum` 在 Android 有**两张**码→文案表：`ReviewMapper` 与 `Reachability.reasonText`

判定：`DUPLICATE_TABLE`（P1，§18"单一映射"的漏网之鱼；本轮 ⑩ 复核后端枚举时暴露，**未在本轮改**）

- **文件**：`core/utils/ReviewMapper.kt:10-22`（`REASON_TEXT_MAP`，11 个码 → 短标签）与
  `data/model/Models.kt:1508-1515`（`reasonText()`，3 个码 → 可执行长句）。
- **现状**：两处各自 `when`/`mapOf` 一份同一个后端枚举的词表。`ReviewMapper` 自己的 KDoc 就写着
  "**禁止在页面散落硬编码**"，而 `reasonText` 正是第二处散落 —— 只是它不在页面里，所以躲过了 ⑨ 那轮 S-* 探针的视野。
- **为什么算违规而不是"两种语气"**：后端加一个码（`ReviewReasonCodeEnum` 现在有 11 个值）只会改一处，
  另一处就退化成 `else -> 原样带出码`。可达性侧实测只有 3 个码能回来（`ROAD_UNREACHABLE:55` /
  `NO_SAFE_HANDOFF_POINT:70,:95` / `USER_LOCATION_UNREACHABLE:138`），所以**今天没有用户可见的错误**，
  但判据要按"词表只有一个来源"来立，而不是按"今天恰好没出错"。
- **修改方案**（下一步做，顺序不可颠倒）：① 词表搬到 §18 那个唯一出口 `data/model/ServerStatus.kt`
  （新增 `ReviewReasonCode`：`fromCode` 未知码 → null 而不是 0、`label` 用后端中文名）；
  ② `ReviewMapper` 与 `Reachability.reasonText` 都改成**读同一张表拿名字**，
  "请送到服务站点交接"这类**动作语气留在调用点**拼接（那是业务文案，不是码表）；
  ③ `docs/STATUS_MAPPING.md` 补一节原因码表；
  ④ **注意 S-d 探针的坑**：它按 `grep -c "^enum class" ServerStatus.kt` 判**恰好 6**，加第 7 套枚举必须
  同时把预期改到 7、并给 `StatusMappingContractTest` 加逐码断言（S-k ≥15），否则"建了表没钉测试"正好是 §55 K 组警告的那种形态；
  ⑤ 新增 R-* 或 S-* 判据数"同一个码字面量出现在几个文件里"（当前 `"ROAD_UNREACHABLE"` = 2 个文件 → 目标 1）。
- **实测依据**：`grep -rn "USER_LOCATION_UNREACHABLE\|NO_SAFE_HANDOFF_POINT\|ROAD_UNREACHABLE" app/src/main/java`
  → `ReviewMapper.kt:15/18/19` + `Models.kt:1495/1507/1508` + `DemoData.kt:181/431`（演示报文里出现是正当的）
  = 词表两处。后端侧 `ReviewReasonCodeEnum.java:15-25` 共 11 个值，Android 短标签表覆盖 11 个、长句表覆盖 3 个。

## 68. 到站提醒的数据源**根本没有 ETA 这个键**：`/send/arrangements` 给不出"还有几分钟到"，而唯一能给的那个是 Haversine 估的 —— ⑪ 读 VO 时暴露

- **断言**：`notify/AlertEngine.kt` 的"车快到了"提醒和 `Arrangement.etaMinutes`（`Models.kt:1503-1536`）
  这条链路在**真后端下永远不响**，而且不是"读法写错了"，是**端点里就没有这个字段**。
- **实测依据**（逐字段读 VO，不猜）：
  - `AppSendArrangementRespVO.java:8-43` 共 11 个键：`orderId/orderNo/status/statusName/boardingStationId/
    boardingStationName/alightingStationId/alightingStationName/planId/planStatus/vehiclePlateNo`。
    `grep -c "etaMinutes" ` = **0**，也没有 `targetStation/shiftCode/dataSource`。
  - 真正带 ETA 的是另两个端点：`AppSendOrderRespVO.java:100 etaMinutes`（`:249` 由
    `estimatedArrivalTime` 与 `now()` 的 `Duration` 相减得出，**只有未来时间才给值，否则 null**）与
    `AppSendOrderRespVO.java:110 carrierEtaMinutes`（`AppSendController.java:478`）。
- **为什么不能顺手把 `carrierEtaMinutes` 搬过来当"还有几分钟到站"**：它出自
  `GeoDistanceUtil.computeKmAndMinutes(...)`，而 `GeoDistanceUtil.java:6/:18/:21/:70-77` 写明是
  **Haversine 大圆距离 + `DEFAULT_AVG_SPEED_KMH = 25.0` 折算分钟**。VO 的 @Schema（`:105`）自己也写了
  "（Haversine）"。按硬规则"正式 App 禁止任何 Euclidean/Haversine 驾驶距离"，这个值**可以展示，但必须打成估算**，
  不许写成"还有 8 分钟到"这种读起来像实时路网到站时间的文案，更不许拿去当放行条件。
- **本轮处置（只记录，不改代码）**：`Arrangement.etaMinutes` 保留可空类型并在 KDoc 标注本条；
  探针 **T-q** 钉住"审计 §68"字样还在（`tools/check_eta_probes.sh`，负控制见 `tools/eta_negative_controls.sh`）。
  **不做的事**：不为了"让提醒响"去 `MultiStr` 造一个不存在的键，也不用班次时间反推 —— 那正是 §14 禁止的 null→业务语义。
- **下一轮（⑫）方案**：把到站提醒的数据源换成 `/send/page`（或 `/send/track`）的 `etaMinutes`，
  并按来源分支文案：`etaMinutes`（计划到达时间差）→ "预计还有 N 分钟到达"；
  只有 `carrierEtaMinutes` 时 → "车距目的站约 N 分钟（按直线距离估算，非道路里程）"，两者都不许催单。
  迁移时 `AlertEngine` 的 `notify widget data` 侧要同步（T-e 判据），别把提醒的判定留在页面里。

## 69. 车辆端点**没有任何一个带 `routeId`**：车辆卡"看这条线"必须按 `routeName` 回查，首页车辆行只能是无状态可点 —— ⑪ 修 ETA 时顺带发现的第四个静默断链

- **断言**：`/bus/nearby` 与 `/bus/lines` 返回的车辆对象里读不到线路 id，所以旧代码
  `nav.navigate(Routes.busDetail(v.routeId))` 在真后端下**恒为 `busDetail("")`**：不崩、不报错，只是点进一条空白详情。
- **实测依据**：`AppBusRespVO.java` 与 `AppBusNearbyRespVO.java` 里 `routeId` 出现 **0 次**（车辆只带
  `busId/plateNo/shiftCode/routeName`）；`routeId` 只在 `AppBusLineRespVO.java:13`（`Long`）上出现，
  而 `AppBusNearbyRespVO` 的 `NearbyLine` 内部类只有 `routeName/startStation/endStation/dataSource` 四个键 ——
  **连首页"线路"列表也没有 id**。
- **口径冲突的额外一步**：行 VO 的 `routeId` 是 `Long`，`BusLine.parse`（`Models.kt:868`）用
  `multiStr("routeId","lineId","id")` 读成字符串，`/bus/line-polyline` 又按 `routeId` 字符串查询（`Repo.kt:1014`），
  数值→字符串这条转换只有一处，留在 `BusLine` 里是对的，不要在页面再转一次。
- **本轮处置**：
  - 车辆卡按名字回查行 id：`ui/user/BusScreen.kt` 的 `lineIdOf(v)` =
    `lines.firstOrNull { it.name.isNotEmpty() && it.name == v.lineName }?.id?.ifEmpty { null } ?: v.routeId`，
    解析不到就把"看这条线"入口置灰（**没有 id 要说没有**，不能点进空白页）。
  - 首页车辆行**如实保持 inert**：`ui/user/HomeScreen.kt` 用同样的表达式解析，但 `nearby.lines` 里没有 id，
    所以真后端下这行不跳详情 —— 这是"少一个功能"而不是"一个假的可点状态"，符合 §14。
  - `BusVehicle.routeId` 字段**保留不删**（避免破坏旧演示报文与旧接口兼容），KDoc（`Models.kt:525-530`）写明
    "真接口下恒为空串"，探针 **T-j2** 数演示兜底里 `routeId` 出现次数 = **0**（车辆兜底不许再造这个键）。
- **风险留档**：按 `routeName` 匹配隐含"线路名全局唯一"。后端 `AppBusLineRespVO` 另有 `routeCode:16`，
  如果将来出现重名线路，正确做法是**让 `/bus/nearby` 的车辆带上 `routeId`**（后端补键），
  不是在 Android 侧把名字做 hash 或取第一条匹配 —— 后者会把用户带上一条同名不同线的线路，属于编数据。

---

## 70. 指令 v2.0（0–130 段）到货：先重建基线，再逐条对表 —— 结果是一张"已成立 / 等价物 / 根本不成立"的三色清单

**为什么单开一节**：v2.0 §1 的第一条要求就是"开始前必须**扫描**当前 Android 项目，而不是假定以前文件仍完全一致"。
本节就是那次扫描的产物。**本轮业务代码零改动**（`git` 不可用，故以"本轮无 `.kt` 写入"为凭：`app/src/main` 最新 `.kt` 仍是
⑪ 的 `DemoData.kt` 注释编辑 13:56:54），产出全在台账与两份新文件。

### 基线（全部实测，非回忆）

| 项 | 实测值 | 取法 |
|---|---|---|
| main 源码 | **71** 个 `.kt` | `find app/src/main/java -name '*.kt' \| wc -l` |
| 测试 | **20** 个文件 / **19** 个类 / 生效 `@Test` **215** | 类数比文件数少 1，因为 `TestSupport.kt` 是 0 例的工具文件 |
| 依赖 | **26** 条声明 = **22** 条 `implementation` + **4** 条 `testImplementation` | 逐行数 `sed -n '/^dependencies/,/^}/p' app/build.gradle.kts` |
| 本地二进制 | **无**（`app/libs` 不存在） | `ls app/libs` → 目录不存在 |
| 包结构 | `core/{net,map,loc,ui,code,utils}` `data/{model,repo,store}` `ui/{user,driver,login,common}` `driver/` `nav/` `notify/` `qs/` `widget/` | `find -type d` |
| 产物（`--rerun-tasks` 后） | 215 tests / 0 failures / 0 errors / 0 skipped；lint `0 errors, 46 warnings`；APK 57,368,755 B | gradle 报告"34 executed / 37 executed"，非 UP-TO-DATE |

### 一、扫描挖出的三条**新**事实（都不在指令的措辞里，也不在此前 69 节里）

1. **§52「所有司机动作必须幂等」的落地度是 0**。
   `grep -rn "clientRequestId\|requestId\|Mutex\|withLock\|actionLock\|inFlight" app/src/main` 命中 **0**。
   现有写路径（`Repo.kt:1378-1400` 的 depart/arrive/pickup-confirm/deliver/pickup-verify/product-load/product-deliver、
   `:1038` 的 `legAction`、`:1044` 的交接确认）**每次点击直接 POST**，连"按钮 loading + 本地锁"这一层都没有。
   ⚠ 修法顺序有讲究：**先查后端有没有幂等机制**（指令 §52 自己也说"如果后端已有幂等机制：复用"），
   查完才知道是"带 `clientRequestId`"还是"只做本地 Mutex + 服务端回读"。**不许先在 Android 造一个后端不收的字段**（§123）。
2. **Phase 1 的验收线"司机绝不能看到普通用户首页"目前是 0 条用例**。
   `grep -rn "USER_SHELL\|DRIVER_SHELL\|startDestination" app/src/test` 命中 **0**。
   代码侧其实成立，靠三条：`BusPostRoot` 的 `start` 按 `Prefs.role` 选壳、`Routes.DRIVER_ONLY` + `deepLinkAllowed()`、
   以及深链那一行把**两个壳都排除在深链之外**（注意读法：`route != USER_SHELL && route != DRIVER_SHELL` 是**不放行**，不是放行）。
   但"读代码成立"不等于指令 §95/§97 要的**用例**——重构一次就能静默回潮，这正是 §29 那一类问题的成因。
3. **异常上报（指令 §54）是"有旗标、没有页面、没有端点"**：
   `DriverLegStateMapper.kt:61/:104` 算出 `canReportException`（并且 `DriverLegStateMapperTest.kt:174-176` 三条形为用例钉住了它），
   但 `.actionable` 在 `ui/driver/` 里**零消费者**，`Repo.kt` 里也**没有任何 exception 上报调用**。
   ⇒ 状态机知道"这一段可以报异常"，界面上却没有任何入口。**下一步不是先写页面**：
   §54 明确要求"后端若现有 enum 不完整：先读取真实 enum，不要自己发一套后端无法接受的 type"。
   所以 V-3（登记时一度写成"⑯"，与路线图位次撞号，已改前缀）的第一步是去 `yudao-module-transport` 找异常类型枚举与 endpoint，找到才谈 UI。

### 二、指令 §128 的 `ANDROID_APP_2_READY` 逐项状态（**只有全绿才允许标记，当前不成立**）

| # | 验收项 | 状态 | 依据 |
|---|---|---|---|
| 1 | 单 APK | ✅ | 单模块 `:app`，无 `UserApp`/`DriverApp` |
| 2 | USER / DRIVER 双域 | ✅ | `UserShell` / `DriverShell` + `DomainSplitTest` 7 例 |
| 3 | RoleRouter | 🟡 等价物 | 无 `shell/RoleRouter.kt`；等价逻辑在 `BusPostRoot` 的 `start` when 块 + `deepLinkAllowed()` |
| 4 | SessionManager | ❌ | 无 `SessionManager`/`SessionState`；会话散在 `Prefs` + `Api.token` + `Repo.saveSession`。且 §4 要的 `roles` 字段**后端没有**（见下"三-1"） |
| 5–6 | 用户首页 / 订单 | ✅ | `HomeScreen` `OrdersScreen` `OrderDetailScreen` |
| 7 | 用户物流 | 🟡 | `ParcelScreen`/`TraceScreen` 在，但 §68 提醒数据源与 ⑫ 的货运 ETA 未收口 |
| 8–9 | 司机工作台 / 任务地图 | ✅ / 🟡 | `WorkbenchScreen`、`DriverMissionMap`；真机渲染 `ENVIRONMENT_BLOCKED` |
| 10–11 | 真实道路路线 / GraphHopper | ✅ | `RouteSource.real` 白名单 + 后端 GraphHopper（D-010/D-012） |
| 12 | route validator | ✅ | `RealRoadRouteValidator` + 16 例 |
| 13–14 | GPS / 后台定位 | ✅(真机未验) | `Locator` 单一源 + `DriverLocationService`（45 s 新鲜度闸）；`NOT VERIFIED ON DEVICE` |
| 15 | navigation abstraction | ❌ | `BLOCKED` #2：导航 SDK 未进依赖（jar 内 `com/amap/api/navi/` 零类） |
| 16 | AMap 或可工作的 NavigationEngine | ❌ | 同上；今日只有**地图** SDK，没有导航引擎 |
| 17–18 | reroute / off-route | ❌ | 无 `OffRouteDetector` / `RerouteController`（依赖 15） |
| 19 | task state server authoritative | ✅ | D-002 + D-021（段状态机单出口，POST 后必回读） |
| 20 | handover persistence | 🟡 | 代码闭环（P2-1）；端到端"拍照→落库→回读"未验 |
| 21 | exception | ❌ | 见"一-3" |
| 22 | real ETA / null-safe | 🟡 | 公交侧 ✅（⑪）；货运侧 ❌（⑫，4+6 处 `null→0` 残留） |
| 23 | route cache | 🟡 | `RouteRepository` 只缓存过校验的路线（§43 ✅），但是**内存** `MAX_ENTRIES=12`，非 §42 的 Room |
| 24 | no straight-line production route | ✅ | 硬规则 5–9/19/20 + 探针 ①③ |
| 25 | **no fake navigation** | ❌ | `DriverNavScreen` 假步进 §八 ④ 实测 **6 处**未归零（不得为变绿而删判据） |
| 26 | no DemoData release | ✅ | `build.gradle.kts` **debug 与 release 都默认 false**（`:92` debug 只能由工程外 `-Pbuspost.demo=true`/local.properties 临时开、`:104` release 硬编码不受属性影响）；闸门读点 `Api.kt:201`、回落入口 `Api.kt:457-460` 抛错；登录页演示入口在 release 不出现（㊶ 现读更正，原行那句"debug `DEMO_ALLOWED=true`"已被用户 2026-10-06「目前不需要演示数据」顶替，见 D-062） |
| 27–29 | tests / lint / debug APK | ✅ | 215 tests 0 failures；0 errors 46 warnings；assembleDebug SUCCESSFUL（`--rerun-tasks` 真跑） |
| 30 | real-device verification | ❌ | **NOT VERIFIED ON DEVICE**（规则 L） |
| 31 | THIRD_PARTY_NOTICES | ✅ | 本轮建档（此前**不存在**，尽管 §五/D-009 早就要求） |
| 32–34 | 三份台账 | ✅ | CONTEXT_LOCK §〇/§九、AI_ITERATION_STATE §106 状态板、DECISION_LOG D-024 |

**读法**：按 `#` 列 34 个验收项逐个计：**✅ 20 / 🟡 6 / ❌ 8**。13、14 记 ✅ 的口径是「代码与判据已成立」；「在真机上验过」是独立的第 30 项，那里记 ❌ —— 两件事不许并成一个 ✅。**任何一条 ❌ 都在册**，所以 `ANDROID_APP_2_READY` 这个标记**今天不许打**。

### 三、三条"指令字面做不到 / 不该做"的对表结论（都带后端证据，不是偷懒）

1. **§3/§4 的 `AppRole` + `SessionUser.roles: Set<AppRole>` 不做**：`AppAuthLoginRespVO` 只有
   `userId/accessToken/refreshToken/expiresTime/openid` 五个键；`member/controller` 全量 grep `userRole|private String role|roleName`
   命中 **0**；`Repo.login` 上送的只有 `mobile/password`。**角色既不上送也不回传** ⇒
   唯一后端可证的司机身份是 `AppDriverProfileRespVO.driverId`，而工程今天正是以它为准（`bindDriver()` 不授予 driverId 进不了司机域）。
   `DISPATCHER/ADMIN` 无契约 ⇒ 按 §3 自己的要求"不为没有后端契约的角色制造假页面"。
2. **§5「不要问用户选角色」保留现状并记代价**：后端不回 role，登录页的选择器是唯一入口意图；删它需要先补后端契约（§124 路径）。
3. **§16「RouteSource 里不要有 UNKNOWN/MOCK/DEMO/EUCLIDEAN」反向做**：这些字符串后端与历史数据真会回，
   删了枚举它们只能塌进 `UNKNOWN`，届时"这是直线"与"没认出来"共用一个值、拒收文案退化成"来源未知"。
   保留但标 `real=false`，拒收时能说出"路线来源不可信（直线估算）"。不变量是**`real=false` 进不了正式链路**。
   已在 `CONTEXT_LOCK.md` §〇 写成"⚠ 与指令字面不一致"，防止下一轮有人照 §16 删枚举。

### 四、推翻我自己先前记录的五处（本轮的"扫描"首先扫到了自己）

| 我先前写的 | 原文/实测 | 
|---|---|
| "§125 要求 THIRD_PARTY_NOTICES.md" | **§100** 才是 Third-party Notice；§125 是"司机安全降级" |
| "§104 要 13 份文档" | 原文 **7 份** |
| 我起草 CONTEXT_LOCK 时写的"指令 §166–167 禁止未知→回退" | 该文最大段号 **130**，**这条引用不存在**；已换成 §32 + 硬规则 19 |
| 指路牌里写 `docs/REAL_ROAD_ROUTING_ARCHITECTURE.md` | `find` 实测**只在后端仓** `../cargo-post-platform/docs/`，本仓 `docs/` 下没有 |
| "MineScreen 直接 `setRole(DRIVER)` 是 §4 违规" | 读代码后**撤销**：它先 `Repo.bindDriver()`，`driverId<=0` 就 return，KDoc 自己写明这是修掉的旧违规 |

### 五、本轮否掉的一条方法论（⑪ 亲手写进台账的）

⑪ 写的是"**每个构建产物各自对时间戳**，产物必须晚于最后一次源码编辑"。本轮照做立刻撞上矛盾：`assembleDebug` 报 UP-TO-DATE，
而 APK 13:32:48 早于 main 源码 13:56:54。真因是**构建缓存保留产物原时间戳** —— mtime 只能证明"任务没重跑"，不能证明"产物不对应当前输入"。
接着我改用"APK 字节 SHA 变了"来判陈旧，**同样错**：APK 的 zip 条目内嵌打包时间，同源码重打包字节必变
（实测 `--rerun-tasks` 前后 `6e7ee85c… → e70ac37d…`，期间源码一字未改）。
⇒ **有效判据**：看 gradle 报 `executed` 还是 `up-to-date`；要无歧义就 `--rerun-tasks` 逼它真跑。
本轮三个产物因此全部重跑（37 + 34 tasks executed），时间戳自然晚于源码，但**结论成立靠的是任务真被执行，不是靠读时钟**。

---

## 71. 货运侧 ETA 家族：`/send/page` **不发** `etaMinutes`、承运分钟是 Haversine 估的、两个段契约**根本没有 ETA 键** —— ⑫ 逐字段读 VO 读出来的四条

**发现方式**：第 ⑫ 轮按"先读后端源码，台账一律复核"开工（⑨/⑩/⑪ 三轮共同的教训：Android 模型里有的字段，后端未必发）。
读的是 `AppSendOrderRespVO.java` / `AppSendController.java` / `AppSendArrangementRespVO.java` / `AppSendLegRespVO.java` /
`AppDriverLegRespVO.java` / `GeoDistanceUtil.java`，**不是**客户端的 `Models.kt`。

### 一、四条契约事实

1. **列表端点不填班次 ETA**。`AppSendOrderRespVO:96` 的 `etaMinutes` 只由 `AppSendController:246-250` 的 `fillEta` 写
   （`estimatedArrivalTime` 减 `now()`，**只有未来时间才给值，否则显式 null**），而 `/send/page` 走
   `fillCarrierBatch`（`:142`），**不调 `fillEta`** ⇒ 列表项里连这个键都不存在。
   客户端旧写法 `multiNum("etaMinutes","eta","carrierEtaMinutes").toInt()` 把"没有键"读成 **0**，页面再用
   `if (etaMinutes > 0)` 判"还没到" ⇒ 恒假：每条寄货单在 UI 上都表现为"不提醒、不显示"，
   而看起来像"数据还没到点"。**这就是 §14"没有值不许有业务语义"在货运侧的形态。**
2. **一个字段吞了两种口径**。`carrierEtaMinutes`（`AppSendOrderRespVO:106`，由 `:478` 填）是 `GeoDistanceUtil` 的
   **Haversine 直线公里 ÷ 25 km/h** 估算，与"车到你的送达站还几分钟"不是一回事。旧解析把它列进 `etaMinutes` 的候选键，
   等于把服务端算的直线估算冒充成用户眼中的道路 ETA —— 直接撞硬规则
   （"正式 App 禁止任何：直线路线 / Haversine 驾驶距离"、"宁可没有路线，不允许假路线"）。
3. **承运那一块可能整体不填**。位置缺失或过期（`REAL_STALE`）时后端**跳过**整个承运块 ⇒ `carrierEtaMinutes` 天然可空，
   而且**新鲜度是它的前置条件**：拿过期位置算出的分钟数提醒用户出门，比不提醒更糟。
4. **两个段契约都没有 ETA 键**：`AppSendLegRespVO` / `AppDriverLegRespVO` 逐字段核过，没有任何"剩余分钟"字段
   ⇒ `TransportLeg.etaMinutes` 真接口下**恒 null**。这与 ⑪ 的 §68（`/send/arrangements` 的 11 个键里没有 `etaMinutes`）
   同属"客户端在读一个后端从没发过的字段"。

### 二、修法（本轮已落地）

- **模型**：`SendOrder.etaMinutes: Int?`（班次口径）与 `SendOrder.carrierEtaMinutes: Int? = null`（承运直线估算）**拆成两个字段**，
  候选键不再互为别名；`TransportLeg.etaMinutes` / `ParcelTrace.etaMinutes` / `RouteGuide.etaMinutes` 改 `Int?`；
  `RoutePreview.distanceKm: Double?` / `durationMinutes: Int?`（`RoutePreviewRespVO` 里本就是可空类型，且明确写"不可用时为 null"）。
- **口径标注**：所有承运分钟的界面文案一律带 **"（直线估算）"**（实测 5 处：`AlertEngine:101`、`HomeScreen:234`、
  `ParcelScreen:254/342/444`）；`ParcelScreen` 把班次标签与承运标签**并排分列**，不合并成同一个数。
- **§68 迁移**：到站提醒与两个用户侧列表的数据源从 `/send/arrangements` 换成 `/send/page` 的承运字段，
  过滤链 `carrierLocationSource != "REAL_STALE"` → `mapNotNull { carrierEtaMinutes }` → `in 1..10`。
  提醒因此从"真后端下恒不响"变成"能响，且如实说这是估算"。
- **"没给"要说没给**：`接口未给到站分钟` / `到站分钟未下发` / `全程时长未给` / `里程未给 · 时长未给`（探针 T-z 实测 7 处）。

### 三、判据（`tools/check_eta_probes.sh`：按内容 + 按 `data class` 类体范围，不按行号）

T-r1 send 2/2 · T-r2 leg 1/1 · T-r3 trace 1/1 · T-r4 guide 1/1 · T-r5 preview 2/2 · T-s 0 · T-t 0 · T-u 0 · T-v 5 · T-v2 0 ·
T-w1 1 · T-w2 0 · T-x 9 · T-y 3 · T-z 7。负控制扩到 **41 条全 ✔（0 ✘ / 0 △）**。三条判据设计的理由必须一起抄走：

- **T-v2 是闭包条**：T-v 只数"带口径的行数"，新增一处**不带**口径的展示它看不见；T-v2 改成按行配对
  （同一行出现 `carrierEtaMinutes` + "分钟"就必须含"（直线估算）"），预期 0。
- **T-s 的正则刻意排除 `durationMinutes`**：`TransportLeg.durationMinutes` 同病，但属"段计划时长"另一段契约
  （后端该键可空），并进同一条判据就又是 ⑪ 修掉的串契约计数 ⇒ 改为在探针尾部**打印残留 2**，交给下一轮。
- **T-t 原本是一条死判据**：写成 `grep 'multiNumOrNull\("etaMinutes", …\)'`，BRE 里 `\(`/`\)` 是**分组**而不是字面圆括号，
  **永远匹配不到**；注入破口后探针仍 exit 0 才暴露 ⇒ 改 `-F`。同轮还修了 **T-o2 的 sed**（小写 `etaMinutes` 匹配不到
  `carrierEtaMinutes` 的大写 `E`，⑫ 之前那条控制是**假绿**），改成 `s/\([eE]\)taMinutes != null/\1taMinutes != -1/g`。
  **教训**：负控制不只测"代码会不会坏"，也测"判据会不会根本不会响" —— 一条永不报红的判据比没有判据更危险，因为它让人以为有人在守。

### 四、本轮状态：`DONE`（探针 41 条负控制全 ✔ + 20 类 224 例 0 失败 + `assembleDebug`/`lintDebug` executed）

**未收口的同族尾巴（不冒充已修）**：`TransportLeg.distanceKm = 0.0` / `durationMinutes = 0` 仍是非空默认值（后端两键皆可空）；
`BusLine` 仍读 `distance|meters` 而真实键是 `AppBusLineRespVO:28 distanceKm`；`etaToNextStationMinutes`
（`AppBusRespVO:50`，比整数分钟精确）**至今没进模型** —— 后两项即轮次 ⑬。
`Arrangement.etaMinutes` 保留为无人消费的兼容字段（**不删字段**，避免破坏旧解析），ui/notify 的 import 消费点已实测归零。

---

## 72. 同一个 `etaMinutes` 键在两个端点说的是两件事、`etaToNextStationMinutes` 从没进过模型、三个 `distanceKm` 是三种口径 —— ⑬ 读出来的六条

**发现方式**：⑫ 收工时列的两条尾巴（"⑬ 留给它的"）＋"先读后端源码，台账一律复核"。
读的是 `AppBusRespVO.java` / `AppBusNearbyRespVO.java` / `AppBusLineRespVO.java` / `AppDriverLegRespVO.java` /
`AppSendQuoteRespVO.java` / `RoutePreviewRespVO.java` / `AppDriverPositionRespVO.java` / `AppBusServiceImpl.java`，
**不是**客户端的 `Models.kt`。

### 一、六条契约事实

1. **`etaMinutes` 是一个键名、两种语义**。`/bus/lines`（`AppBusRespVO:44`）上它是
   `AppBusServiceImpl:130-132` 算的"**跑完剩余线路**要几分钟"= 剩余进度 × 班次计划时长；
   `/bus/nearby` 上它是 `fillEta:726-731` 的"**到下一站**要几分钟"。客户端过去把两个端点喂进同一个
   `etaCaptionText`，于是 lines 的车被界面说成"约 64 分钟到下一站"，而 64 分钟其实是全程剩余。
   唯一可观测的判别子是 **`progress` 只在 lines 形状里出现**（`NearbyBus` 没有这个键）。
2. **精确到站分钟从来没进过模型**。`AppBusRespVO:49-50` 的 `etaToNextStationMinutes`（`Double`，
   小数分钟）才是"到下一站"的精确值，Android 侧 ⑬ 之前**根本没有这个字段** —— 只能退到整数 `etaMinutes`。
3. **线路里程的真实键是 `distanceKm`（公里）**。`AppBusLineRespVO:27-28` 是 `Double`、
   `AppBusServiceImpl:179` 原样透传 `route.getDistanceKm()`，上游 `MonitoringServiceImpl:126` 可空 ⇒
   没给就是 null。而旧解析读的是 `distance|meters` 两个**契约里不存在**的键（所以恒 0）。
4. **段里程/段时长两个键都可空**：`AppDriverLegRespVO.java:58-62` 是 `BigDecimal distanceKm` +
   `Integer durationMinutes`。⑫ 把这两个键列为"打印残留 2"，本轮收口 —— 界面 `legPlanText` 缺哪个就不提哪个，
   两个都缺整行不显示（与小程序 `handover.wxml:26` 的 `wx:if="{{currentLeg.distanceKm}}"` 同口径）。
   注意语义：`durationMinutes` 是**计划**耗时不是剩余，文案写"计划 N 分钟"。
5. **`QuoteResult.distanceKm` 既Nullable又是估算口径**。`AppSendQuoteRespVO.java:21-22` 的 Schema 原文
   "**直线距离 × 路网系数**；站点缺坐标时为 null" ⇒ 它既可能没有值，本身也**不是道路里程**。
   硬规则要求"估算必须标口径"，所以那一行文案固定写成"计价里程 X km（直线距离×路网系数，非道路里程）"，
   缺值写"计价里程未给"。
6. **司机位置报文里没有状态**。`GET /transport/driver/position`（`AppDriverController.java:258-263`，
   端点**确实存在**）返回的 `AppDriverPositionRespVO` 只有 `longitude`/`latitude`/`dataSource` 三个键。
   `RoutesScreen:144` 过去无条件渲染 `"在途 ${nextStation}"` —— 站名恒空、"在途"是本机替后端宣布的。

顺带实测到的一条同类破口（同一味病的第四个实例）：`Repo.routePreview` 写的是
`multiNumOrNull("distanceKm", "distance")` 与 `("durationMinutes", "minutes")`。`RoutePreviewRespVO`
五个键里从来没有 `distance`/`minutes`，而 `distance` 在 `BusLine` 那一族里是**米** ⇒ 留着它等于"缺公里键就拿米当公里"。
全模块 app 侧 VO 里**没有任何**名为 `eta` 或 `minutes` 的字段（实测 grep 为空），这类别名一律算自造。

### 二、修法（本轮已落地）

- **模型**：`BusVehicle` 加 `etaToNextStationMinutes: Double? = null`（解析**只认这一个键名**，
  不许把 `etaMinutes` 列进候选 —— 两者口径不同）；派生出 `nextStopMinutes`（精确值优先，
  否则**只有 nearby 形状**〔`progress == null`〕才允许把 `etaMinutes` 当到站分钟）、
  `remainingRouteMinutesText`（lines 形状的如实说法："剩余全程约 N 分钟（按线路进度与班次计划时长估算，不是到下一站）"）；
  `etaValueText/etaUnitText/etaCaptionText` 三个 getter 按端点分支，lines 又没精确值时大数字位**空着**（"—"）而不是摆一个错的分钟数。
- **精度口径与小程序对齐**：`Math.max(1, ceil(精确值))`（`pages/bus/detail.js:132-137` 同一式子），0.2 分钟显示"1 分钟"不显示"0"。
- **三个 `distanceKm` 各自可空**：`BusLine.distanceKm: Double? = null`、`TransportLeg.distanceKm: Double? = null` +
  `durationMinutes: Int? = null`、`QuoteResult.distanceKm: Double? = null`。
  旧 `BusLine.distanceMeters` **不删字段**（兼容旧报文），但界面一律改读 `distanceKm`。
- **口径落到模型 getter**：`QuoteResult.distanceCaliberText`、`TransportLeg.legPlanText`、
  `BusVehicle.positionOnlyTag` —— 只有落在 getter 上，JVM 契约测试才断言得到（本工程的单测没有 Robolectric）。
- **`RoutePreview` 解析收回模型**：新增 `RoutePreview.parse(o)`，Repo 不再就地拼候选键名。
- **界面**：`BusScreen:495` 全程里程、`SendScreen:559` 计价里程、`TraceView:186` 段卡里程、
  `RoutesScreen:144` 位置标签，全部改成"没给就不显示/说未给"。

### 三、判据（`tools/check_eta_probes.sh` 新增 T-aa…T-ah，按内容 + 按 `data class` 类体范围）

T-aa 1（精确到站分钟可空声明）· T-ab 1（向上取整口径唯一）· T-ac 0（`distance`/`meters`/`minutes` 自造别名）·
T-ad1 6（六个 `distanceKm` 全可空）· T-ad2 2（⑫ 残留收口，**探针尾部那条"残留 2"的打印已退役成红判据**）·
T-ae 0（闭包：界面凡读 `.distanceKm` 必须先判 null）· T-af ≥4（⑬ 里程口径契约测试）· T-ag 1（演示面 lines 真下发精确到站分钟）·
T-ah 0（位置报文不许渲染成"在途"）。**T-c 8 → 9**（BusVehicle 多了第 9 个可空数值字段）。
负控制扩到 **50 条全 ✔（0 ✘ / 0 △）**。两条"判据自己出问题"的现场必须一起抄走：

- **T-k 被本轮自己改成了假绿**：⑬ 给 `BusEtaContractTest` 加了用例（9 → 11），而 pin 还是 `-ge 9` ⇒
  "注释掉一条用例"的负控制从红变灰（`exit 0`），是重跑负控表才暴露的，现在 pin 跟到实测条数（12）。
  **教训**：`-ge` 型计数判据在**加**用例的同一条 commit 里必须抬下限，否则判据静默失效 ——
  这条与 §71 的"T-t 是条死判据（BRE 转义）"是同一类：**判据不会响，比没有判据更危险**。
- **T-ah 第一次响，响的是我自己写的注释**：新增的说明注释里引用了旧文案 `"在途 ${nextStation}"`，
  探针按关键字数把它算成了破口。处置是**按内容过滤注释行**（`grep -vE ':[0-9]+: *(\*|//)'`，与 T-s/T-v 同一写法），
  不是把注释删掉 —— 删说明凑绿等于把这一轮为什么改过抹掉。

### 四、本轮状态：`DONE`（21 类 233 例 0 失败 + 50 条负控制全 ✔ + `assembleDebug`/`lintDebug` executed）

证据：`compileDebugKotlin`/`compileDebugUnitTestKotlin`/`packageDebug`/`testDebugUnitTest`/`lintDebug` 全为 executed（非 UP-TO-DATE）；
6 套探针 exit 0；常驻自检 **宽口径 42 → 44**（新增 2 行都是本轮写的**口径标注**：`Models.kt` 里
`distanceCaliberText` 的计价里程文案、`SendScreen.kt:559` 来源注释。前者写在 :1641、复核时已在 :1649 ——
**本轮自己又演示了一次"行号会漂，按内容找"**，引这两行请用关键字 `distanceCaliberText` / `直线距离 × 路网系数`）、
**窄口径 9 不变**、**`GeoMath` 27 不变**（本轮没有新算一次直线）、
`DemoData` 引用仍是 3 个文件、安全组 E1/E3/E4/E5/E6 全 0、E2 仍是 4 行（2 注释 + 2 构建期拦截）。
⚠ **E6 那句要连 pattern 抄**：判据本体（§55 E 那条 `grep -rn "AMAP_KEY\|amap_key" … | grep -v "@string/amap_key\|BuildConfig.AMAP_KEY"`）
实测就是 **0**，没问题；出问题的是 `AI_ITERATION_STATE.md` 常驻自检里那句简写"AMAP_KEY 字面量 … 全部 0 命中"——
照它按裸 token 跑 `grep -rn "AMAP_KEY" app/src/main` 会得出 **2 命中**（清单注释 + `DriverMissionMap.kt:71` 的
`BuildConfig.AMAP_KEY.isNotBlank()`，都是引用不是密钥值），看起来像红线破了。简写行已改为"照抄 §55 E 的六条命令"。
**收工最后一遍**（两套负控都跑完并原样恢复之后，逐条输出见 `AI_ITERATION_STATE.md` 的"⑬ 收工最后一遍"）：
6 套探针重跑**全部 exit 0**、ETA 负控 **50 ✔ / 0 ✘ / 0 △**、可达性负控 **21 ✔**、
窄 **9** / 宽 **44**（按文件加总正好 44，构成与本节上面那行一致）/ `GeoMath` **27** / `DemoData` **3 文件** / 假步进 **6 处**（`:103/:146/:180/:182/:183/:343`，§24 未修）；
`testDebugUnitTest + assembleDebug + lintDebug` 合跑 **`53 actionable tasks: 1 executed, 52 up-to-date`** ——
这 52 个 UP-TO-DATE 在这里是**好消息**：gradle 按输入内容哈希判定，说明负控脚本恢复后的源码与已验证产物逐字节一致
（对照本节上面那句"全为 executed"，那是 `--rerun` 当时的输出，两者说的是不同时刻的同一件事）；
测试 XML 逐份加总仍是 **`suites=21 tests=233 skipped=0 failures=0 errors=0`**（21 份 mtime 17:58:22），APK **57,941,278 B**。

**未收口的同族尾巴（不冒充已修）**：① ETA 候选名里还剩 **6 处自造别名 `"eta"`**（`Models.kt:702/929/1048/1608`、
`Repo.kt:579/947`）—— 其中 `Repo.kt:1258` 的 `RouteGuide` 读的是**导航引擎自己的报文**，不是 app VO，
改之前必须先核那份契约（这正是 §71 里 `T-s` 排除 `durationMinutes` 的同一理由：别把两段契约并进一条判据）；
② `QuoteResult` 的三个金额字段（`amount/itemFee/distanceFee`）仍是 `multiNum` 非空读法，缺键塌成 0.0，
`distanceFee > 0` 才显示"里程费"—— 同病不同族（金额不是 ETA）；③ `TransportLeg` 的
`navigationPolyline/fromGeo/toGeo` 后端**不下发**（⑫ 实测；⚠ ㊶ 限定范围：只对**司机段** VO 成立 —— 用户段 `AppSendLegRespVO.java:24-33` 下发 `fromLongitude` 那四个坐标键、解析层已按真键读（G-1），站点档案无坐标时后端自己给 null），段卡上的"在途进度"因此仍只能是状态驱动的示意，
不是真实道路位置。①② 并入下一轮，③ 属 #49 `core/navigation` 的真道路改造线（BLOCKED on 高德导航 SDK）。


## 73. 司机写路径**没有一处幂等**，而后端四个接口重复提交会真留脏数据 —— V-1 逐条读后端后的结论与做法

**发现方式**：状态板 V-1（指令 §52，P0 `CONTRACT_MISMATCH`）＋用户指令"后端没有对应的东西安卓可以自己写，只是要符合架构要求"。
**开工顺序按 §52 的硬约束走**：先读后端，读不到机制才允许在客户端做。读的是
`AppDriverController.java` / `DriverAppServiceImpl.java` / `MultiLegServiceImpl.java` / `HandoverServiceImpl.java` /
`OrderEventServiceImpl.java` / `TransportLegStatusEnum.java` / `Idempotent.java` / `IdempotentAspect.java` /
`UserIdempotentKeyResolver.java` / `GlobalErrorCodeConstants.java`，**不是**客户端的 `Repo.kt`。

### 一、七条契约事实（逐条带出处）

1. **`AppDriverController` 全类零 `@Idempotent`**：`grep -c "@Idempotent" …/AppDriverController.java` = **0**；
   框架这个注解在 app 侧只装在两个下单接口（`AppProductOrderController.java:36-41`、`AppSendController.java:119-124`，
   都是 `timeout = 10, TimeUnit.SECONDS` + "订单正在创建中，请勿重复提交"）。
2. **幂等键是服务端自己算的，客户端不发键**。`UserIdempotentKeyResolver` 用
   `md5(methodName + argsStr + userId + userType)`；`IdempotentAspect` 重复时抛
   `REPEATED_REQUESTS`（`GlobalErrorCodeConstants.java:36` = **900**），`deleteKeyWhenException()` 默认 true。
3. **11 个司机 ReqVO 里没有任何请求级唯一键**：`clientRequestId` / `requestId` / `bizNo` 全部不存在 ⇒
   Android 侧**没有可复用的键**，也**绝不允许自造一个后端不收的字段**（这条写进了 `ActionGate` 的类注释，
   并钉成判据 **I-h**）。
4. **`depart` 重发会把已收口的班次倒带**：`DriverAppServiceImpl:861-868` —— 执行记录状态不是"在途"时被改回在途，
   并且 `arriveTime = null`、`currentStationId` 退回首站。
5. **`pickup-confirm` 重发会真的多算一件**：`PICKUP_CONFIRM_STATUSES = List.of(0,1,2,3)`（`:118`）含"已发车(3)"，
   所以第二次 CAS 走 3→3 成功（`:1069-1075`），随后 `incrementLoadedCount*` 再 +1（`:1078-1087`）
   ⇒ 容量被虚占，后来的货撞 `DRIVER_CARGO_FULL`。
6. **`leg/complete` 与 `leg/arrive-dest`（需换乘）重发各多一条事件 + 多条推送**：
   `advanceLegStatus` 与 `updateOrderStatus` 都是"同状态即跳过、不报错"
   （`MultiLegServiceImpl:706-710`、`DriverAppServiceImpl:1595-1597`），跳过不是失败，于是后面的
   `orderEventService.record` 照跑，而它是**无条件 insert**（`OrderEventServiceImpl:41-58`，无唯一索引兜底）；
   换乘那一支同形（`HandoverServiceImpl:107-145`：建单/改状态/推订单三处都幂等跳过，`record(HANDOVER_REQUIRED)`
   与两条通知照旧执行）。
7. **`leg/arrive-dest` 不需换乘那一支是例外**：第二次撞 `TransportLegStatusEnum:53`
   （`DELIVERING → {COMPLETED, EXCEPTION}`）抛 `LEG_TRANSITION_ILLEGAL` ⇒ 不产生脏数据，但司机看到一条莫名报错。

### 二、做法（后端没有 ⇒ 客户端写，但只写在架构允许的位置）

- **位置**：`data/repo/ActionGate.kt`（新）+ `Repo` 内的单一出口 `gatedAction(key) { … }`。
  选 `object Repo` 是因为它是**进程单例** —— 页面 `busy` 旗随 ViewModel 销毁（切壳 `popUpTo inclusive`），
  只有这一层能跨页面持有锁；分层上它仍在 data 层，UI 不新增依赖（硬规则：调度决定去哪里、导航决定怎么走，
  幂等属于"提交"这一层）。
- **三条刻意的取舍**：① `tryLock` 而非 `withLock` —— 排队等于第一次完成后**照样发给后端**，
  这里要的是"丢弃"（判据 I-d 同时钉 `tryLock = 1` 与 `.withLock( = 0`）；
  ② 成功后继续持有 **10 秒**窗口，与后端 `@Idempotent(timeout = 10, SECONDS)` 同口径，不是拍脑袋；
  ③ 异常立即释放（等价后端 `deleteKeyWhenException = true`），否则一次抖动就把这个动作永久锁死。
- **收口 10 条写路径**：`depart/arrive/pickupConfirm/deliver/pickupVerify/productLoad/productDeliver/legAction/confirmHandover/readDriverMessage`，
  键一律带目标标识（`arrive:$shiftId:$stationId`、`leg/$action:$legId`…），不同班次/不同段/不同订单互不阻塞。
- **两条有意豁免并写明理由**（判据 I-c 反向钉住，谁把上报接进闸门就报红）：
  `reportLocation` 是周期上报，⑦ 已在 `DriverLocationService` 做了"合并 + 新鲜度闸 + 丢点可见"，
  这里的"丢弃重复"会把正常新点位当重复扔掉 = **偷点**；`uploadPhoto` 只取文件地址，真正的提交是后面那次 confirm。
- **回执诚实**：`Ack` 新增 `notSent`（I-i）。被拦下**既不是成功也不是失败**，四个回执点
  （工作台 `runAction`、线路 `action`、交接 `advanceLeg` + `confirm`）各加一支，文案一律"本次请求未发给后端……以下为后端当前状态"，
  判据 **I-f** 钉住这些行里不许出现"已提交后端"。旧写法 `if (busy) return` 之后在 `work {}` **内部**才置位，
  它的先后手取决于 `viewModelScope` 的 `Main.immediate` 同步启动 —— 本工程无 Robolectric 证不了，
  所以改成置位在 `work {}` **之前**（I-g = 0）。

### 三、验证证据（2026-09-29 20:00 实测）

- **新用例 11 条**：`ActionGateTest` 5 条（并发同键只执行一次 / 不同目标互不阻塞 / 异常后立即可重试 /
  窗口边界用注入假时钟 `SUCCESS_HOLD_MS-1` 与 `+2` 两侧 / 协程取消不泄漏锁）
  + `DriverActionIdempotencyContractTest` 6 条（判据只用 `server.requestCount`：两次并发**恰好 1 次上网络**、
  不同班次各发各的、位置上报两次**都必须上网络**、业务拒绝后可立即重试（2 次）、
  段动作按动作分键、标记已读走同一道闸）。
- **用例级负控** `tools/idempotency_test_negative_controls.sh`：把闸门的窗口检查与锁检查两行摘掉后
  `testDebugUnitTest` **exit 1、10 条红**，**唯一没红的是"位置上报有意不进闸门"** —— 它断言的正是"不许拦"，
  摘掉闸门它当然还是通过，这条的负控是 I-c（反向破口）。随后原样恢复：`cmp` 一致、gradle exit 0、判据仍通过。
- **新判据** `tools/check_idempotency_probes.sh`：I-a 10 · I-b 10 · I-c 0 · I-d 1/0 · I-e 4 · I-f 0 · I-g 0 · I-h 0 · I-i 1 · I-j 11，exit 0；
  **负控** `tools/idempotency_negative_controls.sh` **12 条逐条破口全 ✔（0 ✘ / 0 △）**，基线与恢复后各通过一次。
- **顺手固化的新脚本** `tools/check_authenticity_counts.sh`（**不是本轮机制的一部分，是抄数工具**）：
  一次打印窄口径 / 宽口径 / `GeoMath` / `DemoData` 文件数 / 假步进 5 个数，并列出宽口径按文件构成（加总须等于总数）。
  动机是本轮真实踩到的坑：把这几条长 pattern 用 `eval` 拼出来跑，shell 把 `haversine(...)` 当命令执行，
  `command not found` + **输出 0 行**，读起来正是"违规归零"。⇒ **命令进脚本、台账只写脚本名与当轮数字**
  （本轮实测：窄 **9** / 宽 **44** / `GeoMath` **27** / `DemoData` **3** / 假步进 **6**）。
- **本轮自己引入的回归，被全量首跑抓到**：`--rerun-tasks` 那一次 `testDebugUnitTest` **1 例红**
  （`ScalarEnvelopeContractTest.kt:191` NPE）。根因不是旧代码，是**本轮新闸门**：它挂在进程单例 `Repo` 上，
  而 Gradle 默认所有测试类共用一个 JVM ⇒ 上一个用例的 `handover-confirm:91` 把 10 秒窗口带进了下一个用例，
  `takeRequest()` 拿到 null。处置是 `TestHost.resetActionGate()`（反射清 `locks`/`heldUntil`，
  与 `installPrefs` 同一手法，**不在生产代码上开测试专用的口子**），修后 **23 类 244 例 0 失败**、逐类加总 244、
  XML 全部 20:00:21。⚠ 记这一条是因为它证明了**"我加的机制会不会污染别人的判据"必须靠全量跑，不能只跑自己的新用例**。
- **全量**：`testDebugUnitTest + assembleDebug + lintDebug` → **BUILD SUCCESSFUL / gradle exit 0**
  （`53 actionable tasks: 7 executed, 46 up-to-date`；`compileDebugUnitTestKotlin`/`testDebugUnitTest`/`packageDebug`/
  `assembleDebug`/`lintAnalyzeDebugUnitTest`/`lintReportDebug`/`lintDebug` 均 executed），
  `suites=23 tests=244 skipped=0 failures=0 errors=0`，lint 报告 **0 Error / 46 Warning**（数字与 ⑬ 相同），
  APK **57,941,374 B**（⑬ 是 57,941,278，**+96 B**）。
  ⚠ 本段先前记的是 **57,941,378（+100 B）**，收工 `stat -c %s` 重量得到 **57,941,374**。两次读数都可能是当时的真值
  （中间还有一次"加上 `readDriverMessage` 那条闸门"的改动会改变 dex 体积），**无法回溯核对**，
  所以这里只保留**当前产物**的读数并说明出处：当前 APK 由 UP-TO-DATE 的 `packageDebug` 对应，即对应当前源码。
  ⇒ 纪律：**字节数只能当场 `stat`，不能从上一遍抄**；发现两个读数不一致时**不要挑一个写进台账**，要写"哪个是当前产物测得的"。
  收工最后一遍（写完四本台账之后）：`testDebugUnitTest --rerun + assembleDebug + lintDebug` →
  `53 actionable tasks: **2 executed / 51 up-to-date**`（executed 的恰是 `--rerun` 的测试与 `lintDebug`，
  其余按内容哈希 UP-TO-DATE ⇒ 产物对应当前源码），XML 全部 20:22:49 仍 `suites=23 tests=244 skipped=0 failures=0 errors=0`。
- **七套判据全 exit 0**：分域 / 信封 / ETA / 定位上报 / 可达性 / 状态映射 / **幂等（新）**；
  旧负控复跑 **ETA 50 ✔（0 ✘ / 0 △）**、**可达性 21 ✔（0 ✘ / 0 △）**。
- **常驻自检无漂移**：窄口径 **9**、宽口径 **44**（按文件加总正好 44，构成与 ⑬ 逐项一致）、`GeoMath` **27**、
  `DemoData` **3 文件**、假步进 **6 处**（`:103/:146/:180/:182/:183/:343`，§24 未修）、
  `canArrive|ARRIVE_RADIUS_METERS` **0**、`nextStationDistanceMeters` 在 `ui/` **0**、`MissionStop` **20** / `timelineOf|.ordered(` **3**；
  §55 E 六条逐条复跑：`127.0.0.1|localhost` 在 `app/src/main` **0**、`app/build.gradle.kts` **4 行**（`:18/:22` 注释 + `:167/:168` 拦截）、
  `*.jks|*.keystore` **0**、`HttpLoggingInterceptor` **0**、凭证字面量 **0**、key 字面量（带 `grep -v`）**0**。
- ⚠ **一条"命令没跑到"的现场复现**（正是 §六 ④ 那一族）：本轮第一次把窄/宽口径写成 `eval` 拼接时，
  pattern 里的 `|` 被 shell 当管道执行，输出 `haversine: command not found` 且计数 **0** ——
  看着像"违规归零"，其实是一条命令都没跑。改回脚本文件里直接写 pattern 才是 9 / 44。

### 四、尾巴（不冒充已修）

① 后端**有**幂等的两个 `create` 接口，重复回的是 **HTTP 200 + `code=900`**，
   Android 现在只把 `e.message`（"订单正在创建中，请勿重复提交"）当错误条显示；
   严格说 900 应当是"正在处理中 + 回读"，不是失败 —— 未做，属用户侧下单线，登记不并入本轮。
② `notSent` 的四条文案**只由 grep 判据钉住**，没有 Robolectric/设备 ⇒ 真机上"这句话显示在哪、多久消失"未验证（§60 同一笔账）。
③ 闸门的键桶**不做淘汰**：基数受"动作 × 班次/段/订单"约束，但长进程下 `locks` 只增不减 —— 有意如此，
   淘汰正在使用的 `Mutex` 等于把闸拆掉；这条是已知局限，不是待办。
④ §24 假步进 6 处、`"eta"` 自造别名 6 处、`QuoteResult` 金额 null→0、OSM/ODbL 应用内署名、git/真机 `ENVIRONMENT_BLOCKED` 全部原样在册。
## 74. Phase 1 那句"司机绝不能看到用户首页"此前**零条用例**：判定内联在 @Composable 里，没有 Robolectric 就只能先把决策提成纯函数 —— V-2 的做法与四条口径

**发现方式**：指令 v2.0 §1 对表时登记的队列 V-2（Phase 1 验收句）。实测基线：
`grep -rn "USER_SHELL\|DRIVER_SHELL\|startDestination" app/src/test` 命中 **0** ——
三条决策（按角色选壳、`Routes.DRIVER_ONLY` 深链闸、深链不许直投壳）全部只有读证。
**开工顺序**：先核"这条验收到底在哪里判定"，再核"本机测得到测不到"，最后才动代码。

### 一、四条现状事实（当场 `grep`，不抄台账）

1. **判定地点是导航图，不是可调用函数**。冷启动落点是 `BusPostRoot` 里 `val start = when { !Prefs.loggedIn -> ... }`，
   深链闸门是**文件级私有** `deepLinkAllowed(route)` —— 两者都内联在 `@Composable` 上下文里，
   **没有一个是能被 JVM 用例调用的函数**。而工程只有 JVM 用例（MockWebServer + 真实 `org.json`），**无 Robolectric**
   ⇒ `NavHost` 渲染不出来，"司机壳里到底有没有用户首页"这一问在本机**不可测**。
   为一条验收引入 Robolectric 是**框架级投入**（§60 那笔账），不在验收轮顺手做。
2. **跨域导航点全工程只有 2 处，且两处都是故意的**：`ui/user/MineScreen.kt:201`（`nav.navigate(Routes.DRIVER_SHELL)`，
   外面那层 `enterDriverMode` 先 `Repo.bindDriver()` 才给进）
   与 `ui/driver/DriverShell.kt:39`（`nav.navigate(Routes.USER_SHELL)`，:38 紧跟 `AppState.setRole(Role.USER)`，就是"用户版"出口）。
   ⇒ 任何"用户目录不许出现 `DRIVER_SHELL` 字样"式的判据都会把真业务判成违规；判据必须**给这条出口留名额**。
3. **`Routes.DRIVER_ONLY` 已经是一份真相**（`setOf(DRIVER_NAV, DRIVER_MESSAGES, HANDOVER)`）
   ⇒ 本轮**不再新建"tab 集合真值源"**，那会变成第二份真相，改一处忘一处的老病（§34/§39 同类）。
4. **角色有两个来源**：`Prefs.role`（本机恢复）与后端 `/driver/profile`（`Repo.bindDriver()`，`driverId > 0` 才写）。
   绑定那一侧的行为已由 `DomainSplitTest.切换司机模式先过后端档案` 钉住 ⇒ 本轮**不重复测**，
   两个纯函数只声明"读 `Prefs`，不读 `AppState.role`"（冷启动时 `AppState` 还是内存态）。

### 二、修法：只收敛结构，不动 UI

新建 `nav/ShellDecision.kt`，两个 `internal` 纯函数：

```kotlin
internal fun startDestinationFor(loggedIn: Boolean, role: Role): String
internal fun deepLinkTarget(route: String, role: Role): String?
```

`BusPostRoot` 改成调用它们，删掉内联 `when` 与私有 `deepLinkAllowed`；`Routes.kt:34` 那条指向已删函数的
过期 KDoc 同步改指 `deepLinkTarget`（**过期注释也是假台账**）。导航图、路由表、页面结构一字未动 ——
这是登记时写明的"结构收敛，不是重构 UI"。语义上刻意保留的两点：
① 壳路由本身不接受深链（通知只能投"页"，不能投"壳"）；② **陌生路由原样返回**，交给 `NavHost` 拒绝，
纯函数不静默吞掉 —— 负控 M-6 专门钉这条，因为 `else -> null` 看着更保守，实际把"路由不存在"这个事实变成了界面的沉默。

### 三、证据（V-2 收口实测）

- 用例：`app/src/test/.../nav/ShellRoutingContractTest.kt` **9 条**（含一条**反向**："司机域深链在司机会话必须原样放行" ——
  只测被拒那一侧的话，闸门收紧过头不会红）。全量 `--rerun-tasks`：**24 类 / 253 tests / 0 failures / 0 errors / 0 skipped**，
  24 份 XML mtime 全为 20:53:59，逐类加总 253，**+9 全部可归因**于新簿。
- 判据：`tools/check_shell_routing_probes.sh` **SR-a…SR-h 共 13 条**全过（`SR-a 9 / SR-b 1·0 / SR-c 1·0 / SR-c2 0 /
  SR-d 2·0 / SR-e 0 / SR-f 0 / SR-f2 1 / SR-g 1·0 / SR-h 1`，末行"壳路由判据通过"，exit 0）。
- 负控：结构侧 `tools/shell_routing_negative_controls.sh` **12 条逐条破口 = 12 ✔ / 0 ✘ / 0 △**（基线与恢复后各判通过一次）；
  用例侧 `tools/shell_routing_test_negative_controls.sh` **6 条（M-1…M-6）逐个把纯函数破口，每条 gradle 必须红且红的是对应用例，
  `cmp` 证原样恢复后复跑全绿**。
- 构建：`assembleDebug` + `lintDebug` BUILD SUCCESSFUL（12 executed / 36 up-to-date）；APK **57,941,374 B**
  与 V-1 末次读数**同字节** ⇒ 本轮**不看总量看符号**：14 个 dex 全扫，`ShellDecisionKt` 命中 **3**、旧符号 `deepLinkAllowed`
  命中 **0**；`ShellDecisionKt.class` 1,746 B。lint **0 error**（`lint-results-debug.xml` 97,380 B = 46 Warning + 31 Information，
  ⚠ 与 V-1 记的"txt 31,126 B"**不是同一个产物口径**，不许当同一条比较）。
- 真实性与安全组无漂移：窄口径 9 / 宽口径 44 / `GeoMath` 27 / `DemoData` 3 文件 / 假步进 **6 处未修**（§24 目标 0）/
  安全六条 0·4·0·0·0·0。

### 四、四条口径教训（都是判据自己失效之后才改对的）

1. **"预期 0"的判据必须先证它能命中非 0**：SR-e/SR-f 第一次就是 `0/0`，看着像红线守住了，
   真实原因是 pattern 里的 `|` 在 **BRE** 下是字面字符 —— GNU grep 3.0 实测同一条带 `-E` 命中 1、不带命中 0。
   这与 §73 尾部那条"pattern 被 shell 当管道执行、输出 0"是同一类假绿的两个变体。
2. **判据文件必须先验存在再计数**：`grep` 找不到文件时输出为空，`[ "" = 0 ]` 会被读成"通过"。
   那次负控在没拷全的目录树上（`cp -r app/src/test $SC/app/src/test` 会产出 `test/test/…`）打印了 12 个 ✔，
   而它自己打的 `[基线]` 明明是"判据失败"。修法是**探针内部**加文件预检 + `exit 2`，
   并在负控开头加"基线不通过则一律不采信"的闸门。
3. **注释行不算代码**：负控的 `drop` 把代码改写成 `// NEG-CONTROL 摘掉：<needle>`，`grep -c` 照样数到
   （SR-c/SR-f2/SR-g 当时全假绿）。这几条改走 `grep -n … | grep -vE "$NC"`，
   且 `NC` 必须写成 `(^|[0-9]+:)[[:space:]]*(\*|//)` 才同时吃得下带行号与不带行号两种输出形态。
4. **合法跨域点给"恰好 1"个名额而不是归零**（SR-f2）：`DriverShell` 的"用户版"出口是真业务，
   且必须紧跟 `AppState.setRole(Role.USER)` —— 判据同时钉"只有一处"和"那一处带着切角色"，
   这样新增跨域入口或把切角色摘掉都会报红。

### 五、本轮事故（数据丢失与恢复，写成规矩）

收工写台账时一条 `io.open(p, 'w', encoding='n')`（编码名打错）**在编码校验之前就把 `AI_ITERATION_STATE.md` 截成 0 字节**。
工程无 git；恢复靠 `.qoder-cn/file-history` 的 ⑬ 收工快照（132,332 B）作基座 + 会话日志里该时间点之后
每一条改动该文件的 `Edit`/`Write`/`Bash` 在**沙箱副本**里按序重放（48 条 bash rc=0、11 条 Edit 命中、
1 条两侧文本都已存在而跳过、1 条 bash 的 SyntaxError 与原始那次完全一致 ⇒ 重放忠实），
还原 164,888 B / 1,308 行后与截断前当场读过的行号逐条对齐（88/101/106/114/302/318–323）才算成立。
⇒ 新规矩三条：**就地重写一律"写临时文件 + `os.replace`"**；**动台账前先 `cp` 一份带时间戳的基座**；
**恢复的判据是"行号 + 锚点双向对齐"，不是"看着差不多"**。

### 六、尾巴（不冒充已修）

① **V-2b：冷启动不复核 `/driver/profile`** —— 本机残留 `DRIVER` 角色而档案已失效的人会直接落在司机壳里。
   现状是**诚实的错**（`ErrorState(DRIVER_UNBOUND)` + 0 条司机请求，不是假空列表），但人在错壳里，唯一出口是"用户版"tab。
   要修是在会话恢复处加一次档案复核并据此降级角色 —— **新机制，不在验收轮顺手加**。
② **真机未验**：`buspost://app/…` 通知深链只有 JVM 侧纯函数判据；Intent 解析与 `NavHost` 找不到路由时
   `runCatching` 是否兜住只能真机验（`ENVIRONMENT_BLOCKED`）。
③ 导航图本身仍**无可测判据**（无 Robolectric）—— 本轮测的是"决定进哪张图/接受哪个深链"这两条**决策**，
   不是"图里有哪些页"。后者仍是读证，登记不并入本轮。

---

## 75. 指令 §54 要的"七类异常"在后端**一个枚举都没有**：分类只能成为客户端话术，落进自由文本 `remark`；而这条链路的真实落点是 `varchar(500)`，越界不是截断、是 MySQL 1406 整笔回滚 —— V-3 读出来的六条后端事实与做法

**发现方式**：指令 v2.0 §54（司机异常上报，七个分类逐字列出）。按 V-1/V-2 的老规矩，**先读后端再动键盘**。

### 一、六条后端事实（全部当场读源码，不抄台账）

1. **`AppDriverLegActionReqVO` 只有四个键**：`driverId / legId / photoUrl / remark`（`AppDriverLegActionReqVO.java:9-22`）。
   **没有任何异常类型、等级、code 键** —— 想发 `type: ROAD_BLOCKED` 就是自造契约，指令 §54 与硬规则都禁止。
2. **`exception` 这个动作在服务层早就存在**：`DriverAppServiceImpl.java:1529` `case "exception" -> reportLegException(leg, reqVO.getRemark())`，
   同一段 `default -> throw exception(BAD_REQUEST)` ⇒ 动作名是服务层的白名单，客户端只能沿用它、不能发明新的。
3. **缺的只是 HTTP 入口**：`reportLegException`（`:1503-1519`）做齐了"段置异常 → 订单置异常 → 记事件 → 告警后台 → 通知下单人"，
   而 `AppDriverController` 此前**没有**把这条路接出来。本轮补 `@PostMapping("/leg/exception")`（`AppDriverController.java:226-231`），
   六行、只调 `driverAppService.legAction("exception", reqVO)`，**不改服务层、不新增字段、不动别的端点**（跨仓改动，见下面"尾巴"）。
4. **`remark` 的真实落点是两张 `varchar(500)`，不是 `transport_leg.remark`**：
   `orderEventService.record(..., "第 N 段异常：" + remark)` → `transport_order_event.detail varchar(500)`；
   `sendToAdmin(..., "订单 {orderId} 第 N 段异常：" + remark + "，请重调度")` → `transport_user_notification.content varchar(500)`。
   ⚠ **上一轮那条"`transport_leg.remark varchar(255)` 是落点"的判断被本轮证伪**：`MultiLegServiceImpl.applyLegStatus:933`
   只写 `status` 与 `actual*` 并走 CAS，整个文件 `grep remark` **零命中**。客户端预算取 **200**（前缀最坏约 40 字，500−40 还有大余量），
   越界的后果不是"被截断"而是**严格模式下 1406 → 整笔事务回滚**，即"报了个异常，段状态也没改成"。
5. **状态机允许非终态一律转异常**：`TransportLegStatusEnum.java:42-55` 每个非 `COMPLETED` 条目的可达集合里都带 `EXCEPTION`；
   `forceLegStatus:732` 与 `advanceLegStatus` 共用 `canTransit` 守卫（`:714/:746`），且 `EXCEPTION → EXCEPTION` 因"目标==当前"早返回（幂等）。
   ⇒ 重复上报后端不炸，但**页面不该给入口**：`DriverLegStateMapper.canReportException` 收口，`COMPLETED(11)/99` 不再让报。
6. **后端零 `@Idempotent`**（V-1 已实测）⇒ 防连点只有客户端进程内的 `ActionGate`。所以"被本地闸拦下"必须是一句**独立回执**，
   不能与"后端已接受"混成一句话。

### 二、做法：分类留在客户端，出口只有一个

- `ui/driver/DriverExceptionReport.kt`（**无 Compose 依赖的纯决策层**）：七个分类的 `enum` + `buildExceptionRemark(reason, detail)` +
  `exceptionDetailBudget()`。KDoc 明写"这是客户端话术不是后端枚举"，产出**只有一句文本**：`道路阻断：塌方，中断约 3 小时`，
  超 200 以 `…` 收尾并保住前缀。
- `DriverExceptionScreen.kt`：三条**不出网**分支（没选段 / 该段已终态 / remark 构造不出来）+ 五种回执
  （`notSent` / `demo` / 回读找不到该段 / 回读状态没变 → "可能未被接受" / 回读确实是异常）。
  **页面没有一处"上报成功"** —— 接受与否只有后端回读说得算。
- 出口仍是 `Repo.legAction("exception", legId, mapOf("remark" to remark))`，自动进 V-1 的幂等闸；报文实测三个键
  `driverId/legId/remark`（`photoUrl` 不上送），由契约测试用 `keysOf(body)` 精确钉住。
- 路由 `driverException` **刻意不带参数**：`deepLinkTarget` 是按**精确集合成员**判的，写成 `driverException/{legId}` 就绕过分域闸；
  段号改走 `AppState.openException(legId)` 的一次性意图（与 `parcelIntent` 同手法）。

### 三、证据（V-3 收口实测）

- 用例：`DriverExceptionReportTest` **7** + `DriverExceptionContractTest` **6** = 本轮新增 13 条；
  `ShellRoutingContractTest` 9 → **10**（新增"司机域路由都是无参数的字面量"）。
  全量 `:app:testDebugUnitTest`：**26 类 / 267 tests / 0 failures / 0 errors / 0 skipped**
  （V-2 收口是 24 类 / 253 ⇒ **+2 类 / +14 例**，14 = 13 新增 + 1 条壳路由新例，全部可归因）。
- 判据：`tools/check_exception_probes.sh` **EX-a…EX-i** 全过，实测
  `7/6 · 1/0 · 0/1 · 7/0 · 1/1 · 1/1/1/1 · 5/1/0 · 0 · 1/1/1/1`，末行"§54 异常上报判据通过"，exit 0。
- 负控：结构侧 `exception_negative_controls.sh` **21 ✔ / 0 ✘ / 0 △**；
  用例侧 **新脚本** `exception_test_negative_controls.sh` X-1…X-8 **8 ✔**（**第一遍是 7 ✔ / 1 ✘**，见四.1）；
  同轮复跑 `eta 50 ✔ / reach 21 ✔ / idempotency 11 ✔ / shell_routing 13 ✔`，每套基线与恢复后都各判通过一次。
- 构建：`:app:testDebugUnitTest :app:assembleDebug :app:lintDebug` 一趟 **BUILD SUCCESSFUL in 2m18s**
  （53 tasks：**13 executed** / 40 up-to-date）、GRADLE_EXIT=0。APK **57,941,374 B**（与 V-2 同字节 ⇒ 又验证一次"同字节不等于没变"），
  符号级复核改看 dex：14 份全扫 → `DriverExceptionScreen` **27** / `DriverExceptionReport` **2** / `ShellDecision` **3** / `DriverLegStateMapper` **4**。
  lint **0 error**（`lint-results-debug.xml` 78 条 = 46 Warning + 32 Information）；新页面只摊上一条 `AutoboxingStateCreation`(Information)，
  与全工程 16 个文件同一形态（`mutableStateOf` 是本工程既有写法），**不为一Information 破例改一个文件**。
- 安全与真实性无漂移：`app/src/main` 里 `127.0.0.1|localhost` **0**、`*.jks|*.keystore` **0**、`HttpLoggingInterceptor` **0**、
  `app/build.gradle.kts` 仍 4 行（2 注释 + 2 构建期拦截）；新文件里 `token|secret|password` 命中 3 处**全是 `BusTokens`（设计令牌）**，无凭证；
  本轮**没有**新增任何直线/估算距离计算（假步进仍 **6 处未修**，§24）。

### 四、四条教训（都是负控第一次跑抓出来的，不是设计时想到的）

1. **用例拿常量证常量 = 没有断言**。`assertEquals(EXCEPTION_REMARK_MAX, remark.length)` 把上限从 200 抬到 2000 之后**照样全绿** ——
   负控 X-4 第一次跑就打出了 `✘ 用例仍然全绿`。已改成字面量 `200`，并把"这个数从哪来"（`varchar(500)` − 后端前缀 − 余量）写进注释。
   同一条教训在 `exceptionDetailBudget` 的用例上一起改掉了。
2. **"恰好 N"的判据必须钉生效代码的确切字面量**。EX-i 原来数 `canReportException` 出现次数（实测 **10**），摘掉两处守卫仍然 ≥2，
   **永不报红**；EX-e 原来 `grep '… = 200'` 是**子串**匹配，改成 2000 一样命中。
   两条都改成锚到整行/行尾的字面量，并给**每一条字面量各配一个破口**（结构负控从 19 条变 21 条）。
   同轮把被 §54 顶掉的两条一起改：`check_status_probes.sh` S-le `=2 → =3` 且新增 **S-le2**（逐文件验"写完必回读"），
   `check_idempotency_probes.sh` I-e 下限 `4 → 5`。**下限跟着生效代码走，不是随手放宽。**
3. **只 copy 一部分文件的负控沙盒，会把"下限"变成沙盒的巧合**。`idempotency_negative_controls.sh` 只 copy 三个司机页面，
   而 I-e 扫的是整个 `ui/driver` —— 加第四个页面后**基线**直接红（不是某条破口红），那次却仍打出 11 个 ✔，
   因为它没有基线闸门。改成 `cp … ui/driver/*.kt` 整目录 + 开头"基线不绿则整份不采信"，
   并给 `eta_negative_controls.sh` / `reach_negative_controls.sh` 补上同样缺失的基线闸门（两者复跑 50 ✔ / 21 ✔）。
4. **加一条司机域路由要同一轮动五处**：`Routes.DRIVER_ONLY` 字面量、探针 `DRV` 正则、SR-h 负控锚点、
   `司机域路由集合就是那四条`那条用例、SR-a 用例下限 9→10。少动任何一处，得到的不是假绿就是假红。

### 五、尾巴（不冒充已修）

① **跨仓改动没落地**：`AppDriverController.java:226-231` 在本机 `cargo-post-platform` 里编译/测试过，
   但工作区**不是 git 仓库**（`ENVIRONMENT_BLOCKED`，§121 的 checkpoint commit 做不了），也**没部署到 48080 的实例**上
   ⇒ 真机端到端"司机报异常 → 后台收到告警 → 调度重排"本轮**未验证**。能声称的只有 JVM 契约层闭环。
② **`ActionGate` 仍是进程内**：杀进程重启后 10 秒保留窗失效（V-1 就登记过），异常上报同样继承这个边界；
   要跨进程防重得后端给键，而后端没有（本文件 §73 与 §一.1）。
③ 七类分类是**客户端话术**，与后端 `TransportOrderEventTypeEnum.ORDER_EXCEPTION` 之间没有码表关系。
   将来后端若真加异常类型枚举，客户端这层要**改成读后端码**而不是继续拼字符串 —— 已写进 `DriverExceptionReport.kt` 的 KDoc。



## 76. "撤档"这件事在客户端**从来没生效过**：后端的否定形状是 `{"code":0,"data":null}`，而 `Api` 把它读成**空对象**——V-2b 读出来的四条契约事实、被当场推翻的第一版设计，以及"保守的前置条件其实把红线写成了死代码"

**发现方式**：V-2 收口时登记的尾巴 **V-2b**（指令 v2.0 对表 §70 里那条"冷启动不复查 `/driver/profile`"）。
开工第一步照 V-1/V-2/V-3 的老规矩 —— **先读后端、再读小程序、最后读自家 `Api` 的信封拆法**。
三方读完的结论是：**偏离的那一方是 Android 自己**，而且偏离点不是"少问一次请求"，
是**判据里一句 `data 非空` 的前置条件，把唯一能让撤档生效的分支变成了不可达代码**。

### 一、四条契约事实（全部当场读源码，`file:line` 为证，不抄台账转述）

1. **后端说"这账号不是司机"只有一种形状**：`DriverAppServiceImpl.profile():167-181` 拿 `currentDriverOrNull()`，
   为 `null` 就 `success(null)` ⇒ HTTP 200 + **`{"code":0,"data":null}`**。
   `currentDriverOrNull():1746-1756` 的三条空路径：会员 id 为空 / 手机号空白 / 查无司机行（站点撤档就是第三条）。
2. **`/driver/profile` 是要鉴权的**（`AppDriverController.java:41-44` 这个端点上**没有** `@PermitAll`）
   ⇒ 它回 401 走的是 `Api` 的会话失效分支，**与"这账号没有司机档案"是两回事**，不许并进"否定"。
3. **Android 侧把 `data:null` 读成空对象而不是 null**：`Api.kt:314-315`
   `if (data == null || data === JSONObject.NULL) return Payload(JSONObject(), null, demo = false)`
   ⇒ 调用方当场能观测到的四元组是 `obj 非 null`、`length()==0`、`driverId=0`、`demo=false`、`error=null`。
   **这一条是整轮的题眼**：判据如果要求"`data` 非空"，就等于要求后端用一种它不会用的形状说话。
4. **小程序对同一端点是直接早退**：`pages/driver/workbench/workbench.js:155-175` 拿到 `!profile` 就停，
   `utils/api.js:66-96` 干脆 resolve `body.data`（`null` 就是 `null`）。
   ⇒ 三方对齐里 Android 既不是"信息不足"也不是"后端少给字段"，是**自己多加了一道后端不会满足的条件**。

### 二、这条前置条件挡住了什么（修前 / 修后实测对照）

| 观测点 | 修前（本轮开工时的实况） | 修后（本轮收工实测） |
| --- | --- | --- |
| 撤档账号 `Prefs.driverId` | **保留上一个账号的 61**（清绑定分支不可达） | 归 0（`else if (!p.demo) Prefs.clearDriverBinding()`） |
| 撤档账号进入司机工作台后的请求数 | **5 条**（档案 + `shifts` + `pickups` + `tasks` + `routeGuide`） | **1 条**（只有 `/driver/profile`，`server.requestCount = 1`） |
| 本机角色 | 永远停在 `DRIVER`，人困在司机壳里看 `DRIVER_UNBOUND` | 降回 `USER`，并**先停前台定位服务再弹回用户壳** |
| `bindDriver()` 与 `driverProfile()` | 同一端点**两套判据**（一套带 `o.length() > 0`，一套不带） | 同一端点**一条判据**（RR-b 数到旧判据残留 **0/0**） |

⚠ **第二行是本轮最实在的产出**：`driverShifts()` / `driverPickups()` **不走** `driverIdParam()` 守卫
（后端按登录会员识别身份，小程序 `utils/api.js:313-324` 同样不传 `driverId`）⇒ ⑥ 那套分域守卫**结构上管不到这两个接口**，
"未绑定就一个请求都不发"这句话以前只对带 `driverId` 的接口成立。能挡住它们的只有**早退**，所以判据 RR-c 钉的是
"早退行号 < 第一条后续请求行号"这个**相对次序**（实测 180 / 193 / 196），而不是任何关键字。

### 三、做法（"后端没有的东西安卓可以自己写，只是要符合架构要求"——本轮连新措辞都没加）

- `nav/ShellDecision.kt` 两个 `internal` **三态**纯函数（无 Robolectric ⇒ 沿用 V-2 的"决策抽成纯函数"手法）：
  `serverSaysDriver(driverId, failed, demo): Boolean?`（`failed || demo → null`＝**未知不等于否定**；
  `driverId > 0 → true`；否则 `false`）、`reconcileRole(stored, server): Role`（**只在 `server == false` 时把 DRIVER 降成 USER**）。
- **只降级、不升级**（硬规则 1）：档案回 `driverId>0` 也**不会**把用户搬进司机壳 —— 进司机域必须是用户显式动作 +
  `bindDriver()` 成功。用例 `档案授予身份时不把用户角色升级成司机` 钉住（角色仍 USER，但 `Prefs.driverId` 被正当写入）。
- **未知不降级也不减请求**：档案失败 / 演示回落时 `demoted=false`，请求照发、绑定照留
  （用例 `档案接口失败时不降级也不少发请求`：requestCount 仍 **5**、角色仍 DRIVER；
  `演示回落不算后端否定`：`demoBackend` + 5×500 之后仍 DRIVER/driverId 61）。
  这条是**跑班司机不能被一次网络抖动锁在司机壳外面**的对称面，与"撤档要掉身份"同等重要。
- 工作台 `WorkbenchViewModel.syncFromServer()` 的降级块清的是**全套本机视图**：`profile / shift / pickups / tasks / guide`、
  `Running.shiftId`、`Prefs.driverShiftId`，`error = DRIVER_UNBOUND`（**复用⑥那句文案，不另编一套**）。
- @Composable 侧 `LaunchedEffect(vm.demoted)`：**先 `DriverLocationService.stop(context)` 再 `navigate(USER_SHELL)` +
  `popUpTo(DRIVER_SHELL){inclusive}`** —— 沿用 `DriverShell` "用户版"那条既有跨域写法，**没有新增第三种跨域点**
  （SR-f2 仍是"恰好 1"；RR-d 的 `RD4=2` 是 `WorkbenchScreen.kt` + `DriverShell.kt` 两个文件的合数）。
  顺序写死在同一条判据里，因为"降了级但还在替已撤档的班次上报位置"正好是硬规则 10 与 §17 要拦的那件事。

### 四、判据与负控（本轮实测数字，逐条可复跑）

- 新簿 **`tools/check_role_reconcile_probes.sh`**：`RR-a 5/4 · RR-b 0/0 · RR-b3 1 · RR-c 180/196, 1/1, RET 193 ·
  RR-d 1/1/2, 1/1 · RR-e 3/2/3`，末行"角色同源判据通过"（exit 0）。**RR-b 用 awk 抠 `driverProfile` / `bindDriver`
  两个函数体再数**，不数整文件（函数上方 KDoc 里就写着被推翻的 `o.length() > 0`，整文件数会假红）。
- `SR-d 2→4` + 同轮新写 **SR-d2 / SR-d3**（钉两个函数的**整行签名**）：实测 `SR-d 4/0`、`SR-d2/d3 1/1`。
  只数函数个数证不到三态 —— 把 `Boolean?` 退回 `Boolean`（＝把"未知"与"否定"重新合并）不减函数、不改调用点。
- 用例 **+2 类 / +9 例**：`RoleReconcileTest` **5**、`WorkbenchRoleReconcileContractTest` **4**；
  全量 `--rerun-tasks` **24 tasks: 24 executed**，**28 类 / 276 tests / 0 failures / 0 errors / 0 skipped**（28 份 XML mtime 23:56:47）。
  `DomainSplitTest`（7）里那条 `档案接口没有内容时保留既有司机身份` 的**前提被本节①③证伪**，
  **改写**成 HTTP 500 的 `档案接口失败时保留既有司机身份`（没删、没减量，推翻过程写进用例注释）。
- 负控两套：结构 **13 ✔ / 0 ✘ / 0 △**（基线与恢复后各判通过一次，沙箱整目录拷）；
  用例 **RRt-1…RRt-6 全 ✔**，六次红逐条 `failures+errors = 1/2/3/2/1/2`，`[恢复] gradle exit=0` + `cmp 三个源文件与备份一致`。
- 构建与产物：`:app:assembleDebug` **37 tasks: 3 executed**（真重打包，APK 23:59:33 / 57,941,374 B）；
  `:app:lintDebug` **28 tasks: 8 executed** ⇒ **0 error / 46 Warning / 32 Information**，
  本轮改动的五个文件在 lint XML 里 **0 点名**；11 套探针**全部 exit 0**；真实性与安全组一条没涨
  （窄 9 / 宽 44 / `GeoMath` 27 / `DemoData` 3 文件 / 假步进仍 6 处未修）。

### 五、本轮**判据与测量自身**被抓到的六条（都比代码问题贵，全部已改）

1. **第一版设计发明了后端没有的中间态**：`Repo.driverProfile()` 要返回 `ProfileAnswer`、纯函数多收一个 `deniedByServer`。
   读完 ①③ 之后发现"这轮算不算有答复"**调用方当场观测得到**（`failed` / `demo` / `driverId`），
   中间类型等于把 `Repo` 内部的判定搬到调用方再判一遍 ⇒ **整块回退**，`One<DriverProfile>` 原样保留
   （实测全工程 `ProfileAnswer|deniedByServer` **0 命中**）。**教训：判据的入参必须是能观测的量。**
2. **"保守的前置条件"是本轮最贵的一种假绿**：`o.length() > 0` 看起来是"没数据就别乱清"，实际是把后端唯一的否定
   形状排除在判据之外 ⇒ 撤档永不生效，而且**旧用例把它写成"未知"来保护**（`DomainSplitTest` 那条），
   于是这条保护反过来把缺陷钉成了"正确行为"。**凡遇到"这样更安全"的前置条件，先问后端会不会这样说话。**
3. **负控的"预期 0 / 恰好 N"必须钉整行**：RR-d 的"demoted 没人观测"第一次注破口后脚本 **exit 0**（该红不红），
   因为 `grep -c 'LaunchedEffect(vm.demoted)'` 把**被注释掉的那行**也数了进去 ⇒ 改成整行锚定
   `^    LaunchedEffect(vm.demoted) {$` 之后才真的红。同族：RR-e 的 `Prefs\.role =` 把 `AlertEngine.kt:34/:44` 的
   **比较** `Prefs.role ==` 也数成赋值（实测 4 而不是 2）⇒ 收紧成 `Prefs\.role = [a-z]`。
4. **跨文件的"恰好 2"不能只扫一个文件**：RR-d 的 `RD4` 期望 2，第一版只扫 `WorkbenchScreen.kt` 得 1
   （另一处在 `DriverShell.kt`）⇒ 改的是**扫描范围**，**不是把期望从 2 降到 1**（降期望就是凑绿）。
5. **`| tail` 会把退出码偷走**：第一遍"11 套探针全部 exit 0"的表是用 `bash "$s" 2>&1 | tail -6` 打的，
   `$?` 取到的是 **`tail` 的退出码**，那一张表的"退出码"一栏**全部不作证** ⇒ 改成 `out=$(bash "$s" 2>&1); rc=$?` 重跑。
   "每项都要留输出"里的输出**包括退出码**，取错退出码等于没取。
6. **本轮自己也写错了一句台账**：先写"四本台账从没记过 release 的 HTTPS 闸门"，`grep` 之后被推翻 ——
   闸门在 `AI_ITERATION_STATE.md`"生产化阶段"、`DECISION_LOG.md:85`、本文件 §1104 三处都记过；
   **真正没记的是"release APK 从未产出过"** 与开关名 `-Pbuspost.allowInsecureRelease`（四本台账 0 命中）。
   台账里被证据推翻的话要**当场改正并留下推翻过程**，不许只留结论。

### 六、三条尾巴（`NOT VERIFIED` / `BLOCKED`，不许写成已修）

1. **真机半边未验**：`adb devices` → `List of devices attached` **空**；`bash tools/verify_device.sh --no-build` 实测
   `[STOP] 没有处于 device 状态的设备。` **exit 1** ⇒ 真实后端上的撤档冷启动行为（是否真只发 1 条、角色是否真降、
   `LaunchedEffect(vm.demoted)` 的时序）与 `buspost://` 深链落地都仍是 `NOT VERIFIED ON DEVICE`，本轮只有 JVM + MockWebServer 证据。
2. **`assembleRelease` 从未产出过 release APK**：本轮第一次实跑即被挡在**配置期**
   （`app/build.gradle.kts:171`：`release 包必须使用 HTTPS（当前 http://1.15.29.107/api）`，exit 1）。
   **没有加 `-Pbuspost.allowInsecureRelease=true` 绕过** —— 那条内网开关正是"release 关闭 cleartext HTTP"要拦的东西，
   不是要绕的障碍。解开顺序：先把 `buspost.baseUrl` 换成真实 https 域名，再补 keystore（`../buspost-signing-backup` 仍缺）。
3. **降级点在"进壳之后"，不是"落壳之前"**：两个纯函数各只有 **1** 个调用点（RR-c 实测），复核发生在
   `WorkbenchViewModel.syncFromServer()` ⇒ 撤档残留 DRIVER 的人**仍会先落进司机壳一次**，再被弹回用户壳。
   要做到"冷启动第一眼就在用户壳"，得在 `NavHost` 起来之前先跑一次网络往返（启动期多一个阻塞点），
   那是**第二次机制改动**，本轮不做、也不谎称做到。

---

## 77. "高德导航 SDK 不在依赖里"这条 BLOCKED 是怎么解除的：javap 取证清单、两处**只能记成假设**的口径，以及判据工具自己的六条坑（2026-09-30，N-1 / 路线图第 8 步）

**背景**：`AI_ITERATION_STATE.md` 的 BLOCKED #2 与 `THIRD_PARTY_NOTICES.md` §3 第 3 点记的是同一件事 ——
实际解析到的 `com.amap.api:3dmap:10.0.600` 里 `com/amap/api/navi/` **零类**，所以 §18 的 `NavigationEngine`
无从下手；而指令 §22 又禁止凭博客或记忆写 API。本轮先把依赖换成
`com.amap.api:navi-3dmap:10.0.600_3dmap10.0.600`（N-1-a），再在"**只用 javap 核对过的签名**"这条纪律下写引擎（N-1-b）。
**这条偏差从此由"未验证"变成"已接入 + 真机未验"**，两者的差别不是工作量，是有没有证据。

### 一、取证：每一个高德符号都来自实际 jar 的 javap 输出

核对对象是 Gradle 真正解析到的那个 jar（50,991,398 字节）。javap 落盘在
`C:/temp/amap-navi-probe-20260930/`（`javap_AMapNavi.txt` / `AMapNaviPath` / `NaviInfo` / `NaviPoi` / `NaviType` / `AMapNaviListener`）。
⚠ **收台账时该目录里那份 51 MB 的 jar 副本已删**（只是取证用的复制件，**原件在模块缓存**：
`~/.gradle/caches/modules-2/files-2.1/com.amap.api/navi-3dmap/10.0.600_3dmap10.0.600/<hash>/navi-3dmap-10.0.600_3dmap10.0.600.jar`，
本轮"七"的体积归因就是直接对它 `zipfile` 加和的）⇒ 复跑 javap 请指向模块缓存那份，别以为取证目录里还有 jar；
**temp 目录本身不是长期凭证**，能在册复跑的只有"模块缓存 jar + `app/build.gradle.kts` 里的坐标"两样。
工程里的高德 import 共 **9** 条（判据 N-j 实测 9/0/0：白名单外 0、引擎层之外 0），逐条对得上：

| 用到的符号 | javap 实测签名 → 落到代码里的决定 |
|---|---|
| `AMapNavi.getInstance(Context)` | `public static synchronized … throws com.amap.api.maps.AMapException` ⇒ 取实例必须包 `runCatching`，取不到就报"实例不可用"，不猜 |
| `calculateDriveRoute(NaviPoi, NaviPoi, List<NaviPoi>, int)` | 五个重载中的第 21 行那条；返回 `boolean` 是**受理与否**，不等于"算成功" ⇒ 受理与结果分两段判（`accepted` + `pending` 回调） |
| `getNaviPath()` / `getNaviPaths()` | 只取 `getNaviPath()`（选中的那条）。多路径的"选哪条"未验，**不引入** `getNaviPaths()` |
| `startNavi(int)` / `stopNavi()` / `static destroy()` | `startNavi` 的返回值直接成为 `NavigationSession.Running.startedBySdk`；**`destroy()` 故意不调** —— 进程单例，页面退出就销毁会把下一次算路一起带走 |
| `addAMapNaviListener` / `removeAMapNaviListener` | 配 `SimpleNaviListener`（抽象类，只覆这层真正要的六个回调） |
| `AMapNaviPath.getCoordList()/getAllLength()/getAllTime()` | `List<NaviLatLng>` / `int` / `int`；折线**只搬运不换算**（见 §三） |
| `NaviInfo.getCurStep()/getPathRetainDistance()/getPathRetainTime()/getCurStepRetainDistance()/getCurrentRoadName()/getNextRoadName()/getIconType()/getRouteRemainLightCount()` | 一帧八个读数，全部 `int`/`String` ⇒ `guidanceOf` 只做单向归一 |
| `NaviPoi(String, LatLng, String)` / `NaviType.GPS` | `NaviType` 是 `int` 常量类（`NONE/GPS/EMULATOR/CRUISE`），**不是 enum** ⇒ 不能 switch 它的名字 |
| `AMapCalcRouteResult.getErrorCode()/getErrorDescription()/getErrorDetail()` | 失败原因三件都取得到 ⇒ `Rejected.detail` 永不为空 |

**两条"查无此 API"的结论和上面那张表一样重要**：

1. `AMapNavi` **没有公开的 `isInited()`**（`grep -i init javap_AMapNavi.txt` 零命中）⇒
   "就绪与否"只能由 `onInitNaviSuccess/Failure` 推出来。这就是 `EngineReadiness` 必须有**第三态**
   `Initializing` 的原因：只有两态就被迫把"回调还没回"念成"这台手机不能导航"。
2. `PathPlanningStrategy` 用 `javap -v` 扫过 `Deprecated` 属性：`DRIVING_DEFAULT`、`DRIVING_SHORTEST_DISTANCE`、
   `DRIVING_MULTIPLE_PRIORITY_SPEED_COST_DISTANCE`、`DRIVING_MULTIPLE_SHORTEST_TIME_DISTANCE` **带弃用**，
   `DRIVING_AVOID_CONGESTION`(=4) 不带 ⇒ 取后者。**这是取证换掉的，不是偏好换掉的**
   （第一版写 `DRIVING_DEFAULT` 时编译器就在报弃用警告，而"高德文档推荐默认值"这种理由不构成依据）。

### 二、两处只能记成假设、不能记成已证的口径

javap 给类型不给语义，下面两条**只有真机能证**，先按最保守的方式接住：

1. **单位口径**：`getPathRetainDistance()` 假定为米、`getPathRetainTime()` 假定为秒（高德公开文档口径），
   字段名跟着写成 `remainMeters/remainSeconds`。真实道路闸门里那条"路网里程不得短于首末直线距离"
   只能挡**报小**的那一半（公里当米报会立刻小于直线而被拒收）；**挡不住报大**（米当公里报会通过闸门、只是屏上数字难看）。
   ⇒ 记为在册假设，真机对一次高德官方导航页的剩余里程即判。
2. **`getCurStep()` 的语义**：是否 0 起、是否等于路径 `steps` 的下标，都未证。所以它在这套代码里
   **只用来判"这一帧构不构成推进"**（`hasStepProgress = sdkStepIndex >= 0`）和原样显示，
   不参与任何业务状态推进 —— 段状态、订单状态仍然只从后端读（硬规则"服务器权威"）。

### 三、结构与诚实性约束（为什么长成两个文件）

- **判据层零 `android.*` / 零 `com.amap.*`**：工程只有 JVM 单测（无 Robolectric），
  "引擎可用不可用 / SDK 这一帧给了没给 / 算出来的线能不能上路"三个判断必须能被用例钉住，
  所以它们住在 `core/navigation/NavigationEngine.kt`（纯函数 `readinessOf` / `guidanceOf` / `naviRouteCheck` /
  `nextNavigableStop`），真正调 SDK 的 `AmapNavigationEngine.kt` 只薄薄包一层。与 `nav/ShellDecision.kt` 同一手法。
- **`readiness` / `guidance` 是 `StateFlow` 而不是 getter**：getter 会逼页面每帧去读一次，
  那等于用本机定时器的节奏猜引擎的节奏（§24 假步进的入口就是这样开出来的）。状态只能由回调推。
- **算路产物只过那唯一一道闸门**：`naviRouteCheck` → `RealRoadRouteValidator.validate(sourceRaw=AMAP, crsRaw=GCJ02)`，
  引擎里不再判第二次（§26"同一个问题不能有第二个答案"）。实测 N-e = **1/0/1**（全工程 validate 调用点 1、
  SDK 层自判 0、唯一出口 1）。
- **不做二次坐标换算**：`getCoordList()` 本身就是 GCJ-02，再套 `Locator.toGcj02` 就是 §17 的二次偏移。
  钉法有两处：用例 `高德折线不做任何二次换算`（`assertEquals(road, route.gcj02Points)`）+ 判据 N-c = **0**。
- **同一时刻只允许一次算路在飞**：高德的回执是进程级单回调，两个并发请求会互相顶掉、
  前一次只能报成"引擎超时"。宁可明说"已有一次算路在等回执"。
- **目的地只读后端 `visitSequence`**（硬规则 3）：`nextNavigableStop` 不重排、不按距离自选；
  而且**只跳 `COMPLETED`(7)，不跳 `FAILED`(8)** —— 后端说"这次操作没成"，没说"不用去那儿"，
  客户端跳掉它等于替调度改路线（M6/M7 两条负控专门钉这一点）。
- **实现只有两种**：`AmapNavigationEngine`（真调 SDK）与 `BlockedNavigationEngine`（一帧都不造），
  工厂没有第三种返回（N-f 实测 1/1/2）。
- **顺带改的一处措辞**：`RealRoadRouteValidator` 里 `reason = "后端没有下发道路折线"` →
  `"${source.label}没有返回道路折线"`。这道闸门现在同时守后端折线和高德折线，固定说"后端"
  会把高德的失败念成后端的锅。改前三份判据/文档 grep 实测零耦合，改后 12 套探针全 rc=0。

### 四、判据与工具自身的八条坑（本轮新增，②③⑥⑦⑧ 五条是永久规矩）

1. **`Class.forName(...) != null` 不是"没有就返回 false"**：类不存在时它**抛** `ClassNotFoundException`，
   于是"这台设备没带导航组件"会变成一次崩溃。⇒ 收成唯一一个 `naviClassPresent()`（`runCatching{…}.isSuccess`），
   且类名写成**字符串常量** `NAVI_CLASS_NAME`：写成 `AMapNavi::class.java` 会在编译期固化引用，
   R8 把类裁掉时这里不报错，"没有"就被读成"有"。
2. **`local code; code=$?` 取到的是 `local` 的退出码（0），不是 gradle 的** ——
   第一版用例级负控因此把**七次真红**报成"用例仍然全绿（exit=0 failures=1/19）"。
   负控工具自己说谎比没有负控更危险：它给出了"验过了"的假凭证。
   ⇒ 永久规矩：变量先单独声明，`code=$?` **紧跟**命令，不许跨语句；退出码一律 `out=$(… 2>&1); rc=$?`。
3. **文件级 `grep -v` 是**无界豁免****：为了放过 AMap 折点搬运那一行，我最初把整个
   `AmapNavigationEngine.kt` 从 L-b 里剔掉 —— 负控 BREAK2 实测"豁免文件里再多一处手写坐标"仍然 rc=0。
   豁免范围一旦按文件给，它就能无限长大，而 L-b 防的正是"坐标系出口不唯一"。
   ⇒ 永久规矩：豁免必须**计数** —— 新增 L-b2 钉 `Geo([a-zA-Z]*\.latitude` 在该文件里**恰好 1 处**，
   四个方向（多一处 / 少一处 / 豁免扩到别的文件 / 判据层出现手写 WGS-84）都实测会红。
4. **破了编译的变异不算证词**：M5 第一版在函数头上插一段代码，留下悬空实参 → gradle 连 class 都没产出（NOXML）。
   那是"编译破了"，不是"用例抓住了假路线"。⇒ 变异必须**整块替换**且保持可编译，
   判决分四态：✔（exit≠0 且产物 failures≠0）/ ✘（两者都干净）/ △（exit 与产物不一致，按未收口）/ ✘ NOXML。
5. **从 python 里 spawn `python -` 会挂死**：子进程把程序从 stdin 读进来，而它的 stdin 是父 python 的、
   永远等不到 EOF。本轮 `_verify_anchors.py` 就是这样吊住两个进程。⇒ 子进程给文件参数，或显式喂 stdin。
6. **"按注释切脚本"的自检本身会静默骗人**：`sed -n "/# M1 /,/case_run M1 /p"` 提取变异体，
   八条全报"mutated=OK"，实际一条都没注入到真文件（切错了范围）。
   ⇒ 要复刻 shell 的引号规则就得真复刻（扫第一个**未被反斜杠转义**的闭合引号），近似不得；
   ⇒ 永久规矩：自检脚本自己也要有"锚点没命中就 exit 2"的硬失败，否则它报的是"我跑了"，不是"我改到了"。
7. **没核实出处就去"修正"一个有出处的数**（本轮我自己犯的，四处台账都被我改错过一遍）：
   看到 "+68.6 MB" 我怀疑它没有基线，就把它改成 `58,410,069 B / +68.1 MB` —— 而 58,410,069
   是**更早一轮**（APK mtime 14:33:58）的在册读数，不是换依赖前**紧邻**的那一次；
   紧邻基线在状态板 `BUILD_STATUS`（V-2b 真重打包，**57,941,374 B**）。
   ⇒ 永久规矩：**改一个数之前先把它的出处找出来**，出处不成立才改；"我觉得这句没依据"不等于"这个数错了"。
   四处现已全部改回 **+68,575,313 B ≈ +68.6 MB**，并补上"跨轮对照、非受控 A/B"的限定。
   ⚠ **同一族的反面**也在本轮出现：六 ③ 原写"自写 Kotlin 约 500 行，撑不起 68 MB，差额只能是 `.so` + 类"——
   那是一句**没有实测的量级话**，却混在一整页实测数字里 ⇒ 收台账时补测成"七"（高德 `.so` 净增 **+43,681,128 B**）。
   ⇒ 规矩有两侧：**有出处的数别乱改**，**没实测的话别当结论写**。
8. **两个 gradle 调用同时在飞会造出假红**：后台全量取证还没结束时前台起了
   `--tests … NavigationContractTest`，实测 `failures=19/19` —— 不是用例红了，是构建锁互相踩。
   `./gradlew --stop` 后单跑 → `exit=0 failures=0/19`。⇒ 永久规矩：一台机器一次只跑一个 gradle 调用，
   要并行就等前一个的 `BUILD SUCCESSFUL` 落盘再起。

### 五、实测数字（2026-09-30，全部本轮实跑）

- **用例**：`NavigationContractTest` **19** 条全绿；全量 `--rerun-tasks` **29 类 / 295 tests / 0 failures / 0 errors / 0 skipped**，
  `24 actionable tasks: 24 executed`（新鲜度只认 executed，不认 BUILD SUCCESSFUL 那句）。
- **判据**：12 套探针 rc=0（authenticity / domain / envelope / eta / exception / idempotency / location / **navigation** /
  reachability / role_reconcile / shell_routing / status）。新簿 N-a…N-l 实测：
  `19 / 0 / 0 / 0 / 1-0-1 / 1-1-2 / 1-1-1-1-1-1 / 2-2-0 / 1-0-0 / 9-0-0 / 7-0 / 0`
  （用例数 / 定时器 / 二次换算 / 假来源 / 闸门 / 两种实现 / 六类调用点 / StateFlow-订阅-轮询 /
  navi-3dmap-并存-并存 / SDK import / 「高德没给」-本机宣布成功 / 过时断言残留）。
- **结构负控** 18 条 ✔ / 0 ✘ / 0 △（15 条 N-* + 3 条打的是 L-b/L-b2 那条坐标豁免）。
- **用例级负控**：基线 `exit=0 failures=0/19`；M1…M8 **全部 gradle exit=1 且红在 `NavigationContractTest`**
  （M5 红 4 例，其余各 1 例）；`cmp` 恢复核对与基线**逐字节相同**；恢复后复跑 exit=0 全绿。
- **构建**：`assembleDebug` rc=0，APK **126,516,687** 字节
  （紧邻的换依赖前基线 = 状态板 `BUILD_STATUS` 记的 **57,941,374** 字节 ⇒ **+68,575,313 B / 约 +68.6 MB**；
  ⚠ 归因**已改成实测**（见本节末"七、体积归因的实测版"），并且这仍是**跨轮对照**，**不是**为体积做过受控 A/B）；
  `lintDebug` rc=0，**0 errors / 46 warnings / 32 information**，`core/navigation/*` 与 `DriverSdkNavCard.kt`
  在报告里 **0 命中**（`DriverNavScreen.kt` 只有既有的 2 条 Information 级 `AutoboxingStateCreation`）。
- **安全自查**（本机无 git，用 ripgrep 代 `git grep`）：main 源集凭证字面量 **0**、源码树 `*.jks`/`*.keystore` **0**
  （`.gitignore:11,14-17` 覆盖 `local.properties`/`*.jks`/`*.keystore`/`keystore.properties`/`release-keystore/`）、
  release 合并清单 `usesCleartextTraffic="false"`（debug 才 true）、
  `127.0.0.1|localhost` 在配置里 **4 处全为注释 + 构建期拦截**（`app/build.gradle.kts:167-168`），
  高德 key 只经 `secret("amap.key", …)` 从**被忽略的** `local.properties` 读入（32 位十六进制，未进源码）。
- **真机**：`bash tools/verify_device.sh --no-build` → `List of devices attached` **空**、
  `[STOP] 没有处于 device 状态的设备。` **exit 1** ⇒ 规则 L：`NOT VERIFIED ON DEVICE`。

### 六、这一轮解除了什么、没解除什么

**解除**：#2 那条"导航 SDK 不在依赖里 ⇒ §18 无从下手"的 BLOCKED 不再成立；
第 8 步从"纸面架构"变成"有生产调用点、有判据、有两类负控的代码"。

**没解除**（全部在册，不得当作已解决）：
① **真机零验证** —— `onInitNaviSuccess` 到底回不回、静默不回调在多长时间内发生、§二两条口径、
后台定位权限、TTS 播报、导航视图，都要设备；
② **`Initializing` 与"永远不回来"在 JVM 层不可分** —— 代码给了 20 秒算路上限，
但**没有**给"初始化回调等多久算失败"的上限，因为那需要一台真机来定这个数，编一个数就是假判据；
③ APK 体积 **126,516,687 B**，较换依赖前最后一次在册读数 **57,941,374 B**（状态板 `BUILD_STATUS`，
V-2b 那一次真重打包，APK mtime 23:59:33）多 **+68,575,313 B ≈ +68.6 MB / 2.18 倍** ——
⚠ **跨轮对照，不是"换依赖前后"的受控 A/B**（本轮没有为量体积把依赖回退重编一次），
归因**已实测**（见本节"七"）：高德 `.so` 净增 **+43,681,128 B**，其余落在 dex 与 assets 上，
"约 500 行 Kotlin"那句量级推断作废；连同专有许可证条款（`THIRD_PARTY_NOTICES.md` §3 已回填真实版本）一起在册；
④ 司机导航屏**仍以后端 `routeGuide` 为唯一线路事实**，本机引擎只是"后端这一班没给折线时"的另一条真实来源，
两者互不顶替（判据 N-l 钉死旧文案"无需地图 SDK"残留 = 0）；
⑤ `#51` 旧 `DriverNavScreen` → DEBUG ONLY、§24 假步进 6 处 → 0、`release` 产物（https baseUrl → keystore）、
git checkpoint 四条**一律未动**。

### 七、APK 体积归因：从"量级推断"改成实测（收台账时补的一轮，2026-09-30）

**为什么要补**：六 ③ 原来写的是"本轮自写 Kotlin 约 500 行，撑不起 68 MB，差额只能是 `.so` + 类"——
那是一句**听起来像结论的量级话**。它没错到哪里去，但它**没有实测支撑**，而本轮其余数字都是实测的，
混在一页里就会让人以为这句也是。所以补测：直接读**已发布的 APK** 与**模块缓存里那两件 jar** 的 zip 目录项。

**测量口径（三条都是当轮实跑）**：
- **包内 `lib/`**（`app/build/outputs/apk/debug/app-debug.apk`，126,516,687 B 那一份）解压后 **77,129,060 B**；
  其中高德 `libAMapSDK_NAVI_v10_0_600.so` = arm64-v8a 41,461,168 + armeabi-v7a 27,076,716 = **68,537,884 B**，
  `libneonui_shared.so`（4,571,144 + 3,897,628）+ `libneonuijni_public.so`（49,376 + 35,636）= **8,553,784 B**，
  两者加总 **77,091,668 B**（与 `lib/` 总额差 **37,392 B**，是包里其他依赖的 `.so` ⇒ **不要**把这 37 KB 算到高德头上）。
- **模块缓存里的两件 jar**（同一个 python `zipfile` 循环，逐条 `lib/` 项相加，目录项 0 B 已计入无影响）：
  `navi-3dmap-10.0.600_3dmap10.0.600.jar` 50,991,398 B / 2,403 entries / `lib/` 9 项 / 解压 **77,091,668 B**；
  `3dmap-10.0.600.jar` 18,923,462 B / 1,432 entries / `lib/` 5 项 / 解压 **33,410,540 B**
  （只有 `libAMapSDK_MAP_v10_0_600.so` 两件：19,998,832 + 13,411,708）。
- **⇒ 仅高德原生库净增 77,091,668 − 33,410,540 = +43,681,128 B**，占 **+68,575,313 B** 差额的 **63.7%**；
  剩下 **24,894,185 B** 是**残差**（差额减原生库），**不是**"测出来 dex 涨了多少"——
  本轮只测了**当前包**的 dex（**15** 份，`classes.dex` 解压 42,929,392 B）与 assets（428 条 / 22,545,847 B）**绝对量**，
  上一轮包里的这两项**没有留档**，所以**分不出增量**。全包解压 **166,142,033 B**、压缩 **126,516,687 B**。

**三条边界（缺一条这段就会变成新的假结论）**：
① 仍是**跨轮对照**，不是受控 A/B —— 本轮没有为了量体积把依赖回退重编一次，所以"换依赖 → +43.68 MB 原生库"是
**两份包相减**得到的，中间若还夹着别的改动，本方法**分不出来**（V-2b 那一轮到本轮之间只动了导航这一件事，是我的判断，不是测量）；
② `.so` 是**jar 内 `lib/<abi>/` 带进来的**（`<packaging>jar</packaging>`，不是 aar）⇒ 它进不进包由 AGP 对
jar 内 `lib/` 的处理决定，**别按"aar 才会带 `.so`"的直觉去解释**；
③ 本轮**没有**减体积的任何动作，也没有开 `abiFilters` / `enableResourceCompression` 之类；
把 68 MB 记清楚是为了**下一轮若有人提“包太大”时，知道能动的和不能动的各在哪**。

## 78. 把旧导航页关进 DEBUG 之前，必须先确认正式包还剩一条能走的路 —— #51 的难处不在删代码，在"删假留真"这两件事的先后顺序（2026-09-30，D-003 执行 / §24 假步进 6 → 0）

**背景**：`DECISION_LOG.md` D-003 从立那条起就挂着"待实施"，因为它同时压着三句会打架的话 ——
①"D-003：旧屏降级为 DEBUG ONLY"，②用户硬规则"**不要直接删除文件**"，③§24"假步进 6 处 → 0"。
而第四句是本轮才显形的：`宁可暂时不能导航，不允许假导航` **不等于**"把假导航拆掉之后就可以没有导航"。
这一轮真正的工程量在那句第四上面。

### 一、动手前的三次复核，前两次各自推翻了一个"看起来显然"的设计

1. **`grep -rn "DriverSdkNavCard("` 全工程只有一个调用点，而它就挂在要被关掉的旧屏里**。
   ⇒ 第一版设计（"给 `DriverNavScreen` 上 `BuildConfig.DEBUG` 闸，其余不动"）一旦执行，
   release 里**唯一**能驱动本机高德引擎的入口会跟着那道闸一起消失 —— "删掉假导航"变成"顺手删掉真导航"。
   这条不是推演，是判据 N-g 第六位（生产调用点）实测出来的：把它从"≥1"改成"**恰好 1 且锚在 `WorkbenchScreen.kt`**"之后，
   旧结构（调用点在旧屏）当场就是红的。
2. **小程序司机端"导航"的语义是"交给外部地图"**：`pages/driver/workbench/workbench.wxml` 那一行入口，
   实现在 `utils/nav.js` + `routes.js:160` 的 `wx.openLocation` / `openAmapNav`。
   ⇒ 只在屏内嵌一个高德引擎**不算对齐**：司机点"导航"想的是"把我送到那儿，用我自己的地图 App"。
   所以外部地图交接（高德 / 百度）必须活在 release 路径里，而不是跟旧屏一起进 DEBUG。
3. **`TransportLeg` 只有站名，没有任何坐标字段**（§36 那条在册偏差的本体）。
   ⇒ 线路页那个"导航"按钮在 release 里**拼不出** `uri.amap.com/navigation?destination=lat,lng` ——
   没有目的地坐标。硬要能跳，就只能拿站名猜一个点，那是 §36 明令禁止的"用直线/猜值补几何"。
   **结论：这一条入口只能 DEBUG-only，并把理由写在代码里**，而不是为了"看起来功能齐全"让它假装能跳。

### 二、最终落位：谁在 release 可达、谁只在 DEBUG

- **release 可达（正式导航面）**：`WorkbenchScreen` 的"本班指引"那一节里，
  `DriverMissionMap`（后端下发折线 + 任务点）+ `DriverSdkNavCard`（本机高德引擎，`NavigationEngines.create` 的产物）
  + 卡片底部新增的**外部地图交接行**（"交给高德 / 交给百度"）。
  三者共用同一个目的地口径 `nextNavigableStop(guide)` —— **只读后端 `visitSequence`**，调度没给可去的点时，
  按钮 `enabled = false` 且文案是"外部地图：没有任务点可以交给它（导航不自己决定去哪里）"，
  而不是退回"挑第一个有坐标的点"。这条是硬规则 3 在 UI 层的落点，负控里专门有一条打它。
- **只在 DEBUG**：路由 `Routes.DRIVER_NAV` 的注册、三个入口按钮（工作台两处 + 线路页一处）、以及旧屏组件本体。
  旧屏文案改为"旧导航页（调试）/ 打开旧导航页（调试·非真实导航）"，页内第一屏是横幅不是指引。
- **一字未动**：`DriverNavScreen.kt` 文件本体、`Routes.DRIVER_NAV` 常量、`onNav` / `onOpenNav` 形参
  （兼容规则"不要删除字段以免破坏旧兼容"）。**被物理删除的只有那套假步进机器**。

### 三、"上闸"的写法是被判据的形状逼出来的，不是为了好看

N-n 要判的是"**每一处** `Routes.DRIVER_NAV` 的使用/注册都自带闸门"。纯 JVM 无 Robolectric ⇒ 只能钉文本结构，
而跨行的 `if (BuildConfig.DEBUG) {\n nav.navigate(…)\n}` 用单行 grep 判不出来（"下一行有没有那个 if"不是 grep 的能力）。
⇒ 闸与用**同一物理行**：
`if (BuildConfig.DEBUG) composable(Routes.DRIVER_NAV) { DriverNavScreen() }`、
`onNav = { if (BuildConfig.DEBUG) nav.navigate(Routes.DRIVER_NAV) },`。
代价是这几行偏长、可读性略降；收益是"把闸拆掉"这件事从此**必须改动那一行**，而改动会被判据抓到（负控实测：拆掉注册闸 → N-n 红）。

**两道闸而不是三道**：① 注册处不注册（正式包这条路由根本不存在）；② 组件自己第一句
`if (!BuildConfig.DEBUG) { DebugEntryClosed(); return }`。第二道的存在理由写进注释：
`nav/ShellDecision.kt` 把深链解析整段包在 `runCatching` 里，残留入口或旧深链打进来时**不能让它在 release 渲染出任何指引内容** ——
第一道闸挡的是"正常路径"，第二道挡的是"我漏看的那条路径"。**没有加第三道**（编译期 source set 拆分）：
那要把旧屏搬进 `app/src/debug/`，而"不删除文件"这条硬规则下搬运=移动=历史断档，收益不值。

### 四、§24 假步进 6 → 0：删出来的，同时把口径**抬高**

`check_authenticity_counts.sh` 里那条 `S`（假步进）原来只 grep `DriverNavScreen.kt` 一个文件 ——
那意味着"把 4 秒定时器挪到隔壁文件"就能让指标归零。本轮把口径改成**整个 `ui/driver` 目录**、含注释行的裸计数，
并把这句理由钉在脚本注释里。**没有为变绿放宽任何一条白名单**（`WL` 九条一字未改）。

**本轮自己踩的那坑，值得单独记**：第一版复测 `假步进 = 2` 而不是 0，命中的是**我这一轮新写的注释** ——
KDoc 里出现了字面 `stepping`、注释里出现了 `delay(4_000`。
⇒ 改的是**我的措辞**（改成"4 秒定时器逐条翻页""4 秒定时器自己往后翻一条"），**不是**判据口径。
这与 §八"0 命中有三种失效"同族，只是方向相反：**写反了注释会被自己的判据抓住，说明判据在起作用，不该被当成判据太脆。**

**宽口径 49 → 45 的归因（实测，不是推断）**：逐文件构成里只有 `DriverNavScreen.kt` 从 **8 → 4**，
其余 16 个文件**逐项未变**（`ParcelScreen` 7 / `WorkbenchScreen` 5 / `MissionMarkers` 5 / `Repo` 3 / `MapCanvas` 3 /
`HomeScreen` 3 …）。剩下的 4 行全部是**禁止性说明**（"旧字段是直线值，不显示""这里不用站点连线顶替"），
被删掉的 4 行属于那套假步进机器。窄口径 **9**、`GeoMath` **27**、`DemoData` **3** 三个数本轮**未变** ⇒
这一轮**没有新增任何直线计算点，也没有新增展示出口**。

### 五、"非真实导航"必须是屏幕上的事实，不只是文件名

D-003 原文要求"在页面上显著标注"。落三处，判据 N-o 钉 ≥2/1：
① 页首 `BusCard(warm = true)` 横幅："非真实导航：这一屏只在调试包打开 … 车道箭头是按车道名文字猜的图形 …
正式导航走工作台的『本班任务地图』+ 本机高德引擎（`DriverSdkNavCard`）"；
② 分节标题改成"旧导航页（调试入口 · 非真实导航）"；
③ `LaneGuideStrip` 下面一行："车道名来自后端，箭头图形是本页按名字猜的（**后端没有车道图形字段**）——
调试看效果用，不算车道级引导"。

③ 那条是本轮新立的口径：**"猜出来的图形"和"编出来的数据"是两件事，但都必须说出来**。
`NavIcon.STRAIGHT` 这个枚举成员**保留**（它是"没认出来的那条"的占位图标），
它出现在 `check_authenticity_counts.sh` 的白名单里（`NavIcon\.STRAIGHT|enum class NavIcon`）——
**这是有意豁免，豁免的是"图标名"，不是"路线几何"**：`core/map/` 那三个真实道路文件的豁免是另一条，
本轮没有把它们扩到任何新文件。若哪天 `STRAIGHT` 出现在构造折点的路径上，宽口径会红。

### 六、判据簿扩建 N-m…N-p（同一轮把新规矩变成会红的东西）

| 判据 | 实测 | 钉的是什么 |
|---|---|---|
| N-m | `司机 UI 目录假步进形态 = 0`（目标 0） | §24 在**整个目录**层归零，换文件躲不过 |
| N-n | `未加闸的 DRIVER_NAV 使用/注册带闸/文件自守 = 0/1/1`（目标 0/1/1） | 每一处使用点自带闸门（首位 0）+ 注册处有闸 + 组件自守各 1 |
| N-o | `「非真实导航」标注/车道箭头猜测说明 = 2/1`（目标 ≥2/1） | D-003 那句"显著标注"落在界面上 |
| N-p | `旧屏引擎挂载/高德/百度/旧屏外部地图函数 = 0/1/1/0`（目标 0/1/1/0） | 能力**已经搬走**：旧屏不再挂引擎、release 导航面上高德+百度交接各 1、旧屏里不再残留 `openAmap/openBaidu` |

**N-g 第六位重锚**（本轮唯一的判据语义变更）：从"全工程 `DriverSdkNavCard(` ≥1"改成
"`WorkbenchScreen.kt` 里**恰好** 1 处"。两个方向都会红：**0 = 正式包没有导航面**（本轮要避免的那个事故），
**≥2 = 两处各自 new 一个引擎**（进程级单回调被两个持有者抢，就是 §77 三"同一时刻只允许一次算路在飞"那颗雷的 UI 版）。

### 七、两类负控：结构 27 条 + 用例 8 条（本轮都实跑）

**结构负控** `tools/navigation_negative_controls.sh`（`/tmp` 副本，纯文本，不编译）：本轮从 18 条扩到 **27 条破口**
（24 条打 N-*，3 条专打 L-b/L-b2 那条坐标豁免）。新增九条里最值钱的三条：
"把 `delay(4_000)` 塞回引擎"（N-b/N-m 双双红）、"注册处去掉 `BuildConfig.DEBUG`"（N-n 红）、
"把外部地图的目的地换成 `Geo(0.0, 0.0)`"（N-p 红 —— 交接对象必须还是调度那个点）。
本轮实测：`✔ 基线：副本原样 exit 0` + **27 × ✔ / 0 ✘ / 0 △**。

**用例级负控** `tools/navigation_test_negative_controls.sh`（**就地改源码再原样恢复**，一次只跑一个）：
本轮**照跑**，没有用"这条脚本只锚 `NavigationEngine.kt`、而我没改它"来跳过 —— 因为 `NavigationEngine.kt`
的 mtime（01:34）晚于上一次该脚本实跑，**mtime 不能证明"上次跑的时候树就是现在这样"**。
实测：`[基线] ✔ exit=0 failures=0/19` → M1…M8 **八次全部 gradle exit=1 且红在 `NavigationContractTest`**
（M5 红 **4** 例、其余各 **1** 例）→ `✔ 恢复核对：与改前基线逐字节相同` → `[恢复后复跑] exit=0 failures=0/19` → `RC=0`。
恢复后立刻复跑 `check_navigation_probes.sh` / `check_location_probes.sh` → 双双 rc=0。

### 八、实测数字（2026-09-30，全部当轮实跑，无一项沿用上一轮）

- **编译**：`:app:compileDebugKotlin` rc=0（`1 executed / 14 up-to-date`）。
- **十二套探针全部 rc=0**：`check_authenticity_counts / domain / envelope / eta / exception / idempotency /
  location / navigation / reachability / role_reconcile / shell_routing / status`。末行逐条实抄：
  `分域判据通过` / `契约信封判据通过` / `ETA 判据通过（公交侧 + 货运侧 + ⑬ 里程口径）` / `§54 异常上报判据通过` /
  `幂等判据通过` / `定位与上报判据通过` / `第 8 步导航引擎判据通过` / `可达性判据通过` / `角色同源判据通过` /
  `壳路由判据通过` / `状态映射判据通过`；`check_authenticity_counts.sh` rc=0 且打的是**数**
  （窄 **9** / 宽 **45** / `GeoMath` **27** / `DemoData` **3** 文件 / 假步进 **0**）。
  取码一律 `out=$(bash "$s" 2>&1); rc=$?`。
- **导航判据 N-a…N-p 十六条全在目标**：`19 / 0 / 0 / 0 / 1-0-1 / 1-1-2 / 1-1-1-1-1-1 / 2-2-0 / 1-0-0 / 9-0-0 / 7-0 / 0 / 0 / 0-1-1 / 2-1 / 0-1-1-0`。
- **全量测试 + 打包**：`:app:testDebugUnitTest :app:assembleDebug` rc=0（`7 executed / 36 up-to-date`），
  计数只从 `app/build/test-results/testDebugUnitTest/TEST-*.xml` 读：**29 类 / 295 tests / 0 failures / 0 errors**；
  APK **126,516,687 B**（与上一轮同一量级，本轮没有动依赖，也没有动体积相关开关）。
  ⚠ 新鲜度只认那五个数字里的 `7 executed`：本轮**没有** `--rerun-tasks`，295 这个数是"当轮产物 + 上轮已验证未变更"的合成口径，不是全部重跑。
- **真机**：**仍然没有设备在环** ⇒ 本轮全部结论只到"结构 + JVM 用例"这一层。
  旧屏在真机上是否真的打不开、`DebugEntryClosed()` 那一屏长什么样、外部地图在装了/没装高德 App 的两条分支、
  `nextNavigableStop` 为空时按钮禁用态在窄屏是否折行 —— 四条一律 `NOT VERIFIED ON DEVICE`，**不得**当作已验证。

### 九、做了什么、没做什么（尾巴全在册）

**做了**：D-003 由"待实施"变**已实施（结构 + 用例级证据，真机未验）**；§24 假步进 **6 → 0**（删机器 + 抬口径）；
正式导航面重新落位到工作台（引擎 + 外部地图交接，目的地只认调度）；判据簿 12 → **16** 条、结构负控 18 → **27** 条。

**没做 / 不能算做完**：
① **真机零验证**（同上，且这条从本轮起覆盖了"闸门本身"—— DEBUG 闸没在设备上验过）；
② **线路页 release 导航入口不做**，因为它缺的是**后端契约**（`TransportLeg` 无坐标）。要补得先决定"段几何从哪来"
（§36/§44 同一条路），**不许**在这一轮拿站名反查坐标补上 —— 那是 §36 禁止的那类补法；
③ `release` 产物仍 `BLOCKED`（`assembleRelease` 配置期即失败：release 包必须使用 HTTPS → 先 https baseUrl 再补 keystore）；
④ git checkpoint 仍 `ENVIRONMENT_BLOCKED`（本机无仓库）；
⑤ 旧屏里那些"按名字猜"的图形（`laneIcon()`）**没有**被改成"不显示"—— 它在 DEBUG 里作为调试参考留着，
并由 N-o 钉住"必须说明它是猜的"。**这是选择，不是遗漏**：把它删掉会更"干净"，但 D-003 的原文就要留着兼容入口。

## 79. 候选键名列表是本项目里**最像兼容、最不像造假**的一处缺陷 —— E-1 删掉 6 处"三方都没有生产者"的 `"eta"`，并把"判据和负控自己会不会红"当成同轮必检项（2026-09-30，E-1 / 队列首项 / D-033）

### 一、为什么这一条要从 §38 那一族里单拎出来

§38 那一族（T-a…T-z）拦的是 **`null → 0`**：后端没给值，客户端不许把它说成一个数。本轮删掉的东西**不走这条路** ——
`multiNumOrNull("etaMinutes", "eta", "minutes")` 在 `etaMinutes` 缺键时读到的是**一个确实存在的数**，
屏幕上出现的是"车距站约 15 分钟"这样一句**有根据的话**，而那个"根据"是客户端自己写的第二个键名。

它的形状与 §12/§14 明令禁止的"按键名猜语义"一模一样，只是猜的对象从**值**换成了**键**；
而且它**读起来像防御性兼容**：删掉它看起来会"少一条兼容路径"，于是每一轮都容易写成"以后再说"（本轮之前已经说了四轮）。
⇒ 判据必须问一个此前没有问过的问题：**这个键，谁在写。**
后端 / 小程序 / 演示面三方都没有生产者的键名，不是兼容，是**第四个真相源**。

### 二、三方实测，以及"判据判得到"的反证（反证用改动前那棵树，不造假破口）

| 面 | 查法（本轮当轮实跑，不沿用） | 结果 |
|---|---|---|
| 后端 | `cargo-post-platform/yudao-module-transport` 全模块 grep `"eta"` | **0 命中**；同一条 grep 换成 `etaMinutes` → java 里 **13 处**（⇒ 不是搜错了地方） |
| 小程序 | `cargo-post-miniprogram` 与 `miniprogram` 两棵树 grep `"eta"` / `.eta` （排除 `etaMinutes`/`EtaSeconds`） | 各 **0 命中** |
| 演示面 | `DemoData.kt` grep `"eta"` | **0 处**（而 `"etaMinutes"` 有多个写入点） |

⇒ 该键**从来没有生产者**，所以"删掉它会不会破坏兼容"这个问题在事实上不存在。

**反证（这是本轮最值钱的一步，与本轮前几轮的"注入一个假破口"不同）**：
上一轮留下的整份源码副本 `/tmp/ledger_replay`（mtime 09-29 21:18）就是**改动前的树**，把同一组三条 grep 打在上面：
**T-ai = 6 / T-ai2 = 6 / T-ai3 = 0**；打在当前树上：**0 / 0 / 0**。
前两位证明 T-ai、T-ai2 **判得到**（不是永远返回 0 的装饰判据）；
第三位那个"改动前后都是 0"是"演示面从来没有这个键"的直接证据 —— 也正是本轮**拒绝**给 demo 补一个键的理由：
补了就等于**亲手拆掉自己刚立的论据**，下一轮再也证不出这个键不该存在。

### 三、6 处落点（"改前行号"来自上面那份旧树副本，"改后行号"是当前实测）

| # | 改前 → 改后 | 所属解析出口 | 改前读法 | 改后 |
|---|---|---|---|---|
| 1 | `Models.kt:710` → `:721` | `BusVehicle.parse`（`/bus/nearby` 与 `/bus/lines` 两种形状共用） | `multiNumOrNull("etaMinutes", "eta", "minutes")?.toInt()` | `multiNumOrNull("etaMinutes")?.toInt()` |
| 2 | `Models.kt:926` → `:937` | `SendOrder.parse`（`/send/page`） | `("etaMinutes", "eta")` | `("etaMinutes")` |
| 3 | `Models.kt:1045` → `:1056` | `TransportLeg.parse`（段） | 同上 | 同上 |
| 4 | `Models.kt:1605` → `:1623` | `Arrangement.parse`（`/send/arrangements` —— 该 VO 本来就没有 ETA 键，所以它**恒 null**） | 同上 | 同上 |
| 5 | `Repo.kt:584` → `:592` | `traceOf()` → `ParcelTrace`（轨迹全程分钟） | 同上 | 同上 |
| 6 | `Repo.kt:954` → `:962` | `routeGuideOf()` → `RouteGuide`（导航全程分钟的**最后一条兜底**） | 同上 | 同上 |

两点必须写清，否则这张表会被读成"六个一样的改动"：
- **#1 还多带一个裸 `"minutes"`**。§12/§38 早立过"`minutes` 这种通用名不许当 ETA 别名"（`T-ac` 当年钉的是里程/时长那一族），
  但 `etaMinutes` 这一族当时只修了**塌缩**、没修**键名列表** —— 本轮补齐。
- **#4 与 #6 是两个相反方向**：#4 那个键在后端根本不出现，删别名只是让"恒 null"这件事变得诚实；
  #6 的兜底**仍然保留 `etaMinutes` 这个键名**，因为**演示面真的会发它**（`DemoData` 的 `/driver/route` 分支，本轮实测仍是全工程唯一生产者）。
  连着键一起删会把演示能力带走 —— 用户硬规则说的是"不要**创建**新的 Demo 功能"，没有授权砍掉既有的，
  而且"能力少一条"和"来源假一条"是两件事：这条能力在正式包上**本来就不生效**（真实报文没这个键，见 §四）。

### 四、被本次实测复核 / 推翻的既有登记

- **复核成立，一字未改**：`AppSendArrangementRespVO` 的 11 个键里没有 ETA（§68/§72 的结论）；
  `AppDriverLegRespVO` 里 `eta` **0 命中**（段契约没有 ETA 键）；后端两条算 ETA 的路都 `Math.max(1, …)`
  （`AppBusServiceImpl:132`/`:728`）⇒ **0 从来不是合法到站分钟**，客户端读出的 0 只可能是自己编的。
- **本轮被推翻的一条说法**（写在 `RouteGuide.etaMinutes` 的 KDoc 里）：原文"…或后端原始 payload 里真给的那个键"，
  读起来像"正式接口也可能给 `etaMinutes`"。**实测：真实报文里没有这个键**，唯一生产者是演示面 ⇒ 注释按事实改写，
  并把它曾经带的那个"第四个来源"写明是已删的自造别名。**这不是文字问题**：留着那句话，下一轮就会有人拿它当
  "所以读 `eta` 也合理"的依据 —— 过期注释是这类缺陷的孵化器。
- **顺手核清的一条口径（与 §77 二那条单位未验是同一条线的不同事实）**：`roadEtaSeconds` / `roadDistanceMeters`
  是**客户端字段名，不是 JSON 键**；真实键是 `routeDurationSeconds`（`AppDriverRouteRespVO:48`）与
  `routeDistanceMeters`（`:45`），`Repo.kt:1294-1295` 的读法**已经**以真实键打头（本轮实测该行仍是这两个键在第一候选位）
  ⇒ 正式包的全程分钟只可能来自通过校验的真实道路耗时，与 #51 的"release 导航面"结论接得上。

### 五、"打印不判红"的残留升格成三条判据，同轮抬两条计数下限

| 判据 | 实测（旧树 → 当前树） | 钉的是什么 |
|---|---|---|
| **T-ai** | 6 → **0** | `etaMinutes` 读法里带第二候选名的处数。本轮删的正是这 6 处；换文件、换写法都躲不掉 |
| **T-ai2** | 6 → **0** | **闭包**判据：main 源码里裸 `"eta"` 字面量 —— 不许在别处再长出一个读这个键的地方 |
| **T-ai3** | 0 → **0** | **反诱惑**判据：演示面 `"eta"` 键数。不许靠给 demo 补生产者来给别名"续命" |

同轮抬下限（**不是顺手，是被 §72 那颗雷教过**）：**T-k 12 → ≥13**、**T-x 9 → ≥12**，注释里"实测 N 条"一并改。
上一轮的失败形状是：用例 9 → 11 而 pin 还写 9 ⇒ "注释掉一条用例"的负控制**从红变灰**，本轮不再犯。

新增用例 4 条：`BusEtaContractTest` 12 → **13**（`自造别名 eta 与 minutes 不是契约键，只带它们的报文必须读成 null`）、
`SendEtaContractTest` 9 → **12**（订单/段、轨迹、路线引导各一条）。
**每条"只带别名 ⇒ 必须 null"都配一条"真实键照常工作"**（`etaMinutes=7`→7、`carrierEtaMinutes=6`→6、
`routeDurationSeconds`→道路耗时）—— 否则"我把整条读法删了"也能骗过前者。这是 §六"0 命中有三种失效"的同族：
**判据与用例都要能证明自己有资格报 0。**

### 六、本轮**判据与负控自己**被抓到的五条缺陷（这五条比那六行改动更值得抄）

1. **正则手滑写成双引号**（`multiNum(OrNull)?\("etaMinutes", *""`）⇒ **永不命中 = 永远绿**。
   抓法不是回去审代码，是**立刻打一遍旧树**：写错时它打旧树也返回 0，写对之后返回 **6**。
   **一眼顶十遍审**。凡新增"目标为 0"的判据，同轮必须有一次"打在应该有破口的树上"的读数。
2. **闭包判据少了 `grep -n`**：`grep -rn` 不带 `-n` 时输出没有 `:行号:` 前缀，
   于是 `grep -vE ':[0-9]+: *(\*|//)'` 这条"排除注释"的过滤器**形同虚设** ⇒ T-ai2 被本轮自己写的说明注释算成 2、探针 rc=1。
   **修的是判据的输入形状，不是删注释** —— 注释是真话，删注释等于删证据。
3. **`T-t` 的负控被本轮改动作废**：E-1 删掉的那一行正是 `T-t` 的 sed 锚点 ⇒ 破口没注进去、用例照跑照绿（**静默失效**）。
   当场被抓到是因为簿会比对"注入前后判据读数是否变化"：实测 `✔=53 ✘=1`，修锚点后 **`✔=54 ✘=0`**。
4. **`SR-f` 的负控被 #51 作废，而且是跨簿的**：#51 把 `onNav = { nav.navigate(Routes.DRIVER_NAV) }` 改成带闸那一行，
   而 `shell_routing` 簿里 SR-f 锚的正是那行旧文本 —— 上一轮**只复跑了自己那本簿**，于是这条负控**静默绿了一整轮**。
   本轮修锚点（并在脚本里写明"跨簿锚点也必须随代码同轮核 —— mtime 与'范围不相交'都不算证据"）后 **`✔=13 ✘=0`**。
5. **台账里"负控成本"那两个数是推算出来的**（不是本轮最严重、但形状最该记的一条）：
   草稿按脚本结构写"四套 32 次、连角色簿 40 次、约 7 分钟、04:14 起跑"。
   收口时按 `/tmp/*_neg_*.log` 的**留档体数**清点：异常 **10** / 幂等 **2** / 壳路由 **9** / 导航 **10** / 角色 **8** = **39 次**，
   分段墙钟 04:04:13–04:06:32、04:13:36–04:15:53、04:16、04:16:47–04:18:35、04:18:44–04:20:51 ⇒ **约 9 分钟**。
   幂等簿按结构该有"基线 + 破口 + 恢复"三份，实跑只落了两份 —— **差额本身就是那条"没有输出就等于没跑"的现行**。
   ⇒ 永久口径：**凡台账里的次数与时长，只准抄留档产物（日志体数、mtime），不准抄"每套 N 条 × 每条约 X 秒"的心算**；
   四处已写错的副本（D-033 第 6 条、CONTEXT_LOCK §八 ⑳、状态板 NEXT 段、本节上一版）同轮一并改成实测值。

⇒ **§78 那条永久规矩的适用范围被本轮实测写窄了**。原文防的是"跳过同一本轮次"，
实际发生的失效路径是"**锚点是源码文本**，任何一次改写文案 / 换写法都会作废它 —— 包括上一轮改的、在**另一个文件**里的"。
升级后的写法（CONTEXT_LOCK §八 ⑳、D-033 第 6 条）：**改源码文本 ⇒ 同一轮要核所有簿的注入锚点，或直接复跑所有簿**；
本轮代价实测：**39** 次单类 gradle 调用、五段合计约 **9 分钟**（明细见下面第七节）。

### 七、实测数字（2026-09-30，全部当轮实跑，无一项沿用上一轮）

- **全量构建 + 测试**：`./gradlew :app:testDebugUnitTest :app:assembleDebug --rerun-tasks`
  → `BUILD SUCCESSFUL in 1m 34s`、**`43 actionable tasks: 43 executed`**（本轮第一次拿到 43/43 全量口径，见下面的体积说明）。
- **测试产物**：`app/build/test-results/testDebugUnitTest/` **29** 份 XML，合计 **299** tests / **0** failures / **0** errors / **0** skipped；
  与 §78 那轮的 295 之差 = 本轮新增 **4** 条（逐类核对：`BusEtaContractTest` **13**、`SendEtaContractTest` **12**、
  `NavigationContractTest` 仍 **19** —— 三条都等于各自判据的 pin 值 T-k/T-x/N-a）。
  ⚠ **"+4 全部可归因"是机器比对出来的**：把 29 份 XML 的逐类计数与 §78 那份逐类表对着跑，**全表只有两行不同**
  （`BusEtaContractTest` 12→13、`SendEtaContractTest` 9→12），29 个类名两侧一一对上、**没有孤儿类**
  —— 没有这一步，"+4"与"某类偷偷少了 3 条、另一类多了 7 条"是同一个读数。
- **APK**：`app-debug.apk` **113,863,001 B**（mtime 04:08:39）。
  ⚠ 与 §78 记的 **126,516,687 B** 差 **12,653,686 B**，**这一轮不许当成"体积优化"**：本轮的包是 `43/43 executed` 的全量重编，
  上一轮的包是增量口径，且旧包已被覆盖、本机无 git ⇒ 唯一可复核的解释是依赖图里 `3dmap` 已被 `navi-3dmap` 顶替
  （两件 `libAMapSDK_MAP_*.so` 压缩后 14,377,503 B，与差额同量级、差 1,723,817 B ≈ 12%）——
  **登记为"可复核的解释"，不登记为"已证归因"**。永久口径：**跨轮引用 APK 字节必须同时记 `executed` 数**，否则不可比。
- **十二套探针全 rc=0**（`out=$(bash "$s" 2>&1); rc=$?` 取码，末行逐条实抄）：
  `分域判据通过 / 契约信封判据通过 / `**`ETA 判据通过（公交侧 + 货运侧 + ⑬ 里程口径 + E-1 别名键归零）`**` / §54 异常上报判据通过 /
  幂等判据通过 / 定位与上报判据通过 / 第 8 步导航引擎判据通过 / 可达性判据通过 / 角色同源判据通过 / 壳路由判据通过 / 状态映射判据通过`，
  外加 `check_authenticity_counts.sh rc=0` 只打数：窄 **9** / 宽 **45** / `GeoMath` **27** / `DemoData` **3** 文件 / **假步进 0**。
- **结构负控七套（逐套实跑，✘ 与 △ 必须同时为 0）**：
  `eta` **✔=54**（本轮 +4 条打 T-ai 家族）· `navigation` **27** · `shell_routing` **13**（SR-f 锚点已修）·
  `role_reconcile` **13** · `exception` **21** · `idempotency` **11** · `reachability` **21** —— 全部 `✘=0 △=0`。
- **就地（会真跑 gradle 的）五套用例级负控全绿**：
  异常 `X-1…X-8` 八次破口**每次 exit=1 且红在异常那一类**（`failures+errors` 依次为 2/3/1/2/2/2/1/1）·
  幂等破口 `[破口后] exit=1`、红 **10** 条 → `[恢复] cmp 一致` → `[恢复后] exit=0` ·
  壳路由 `M-1…M-7`（1~3 条）· 导航 `M1…M8`（M5 红 4 例、其余各 1 例）→ `✔ 恢复核对：与改前基线逐字节相同` → `exit=0 failures=0/19` ·
  角色 `RRt-1…RRt-6`（1~3 条）。
- **别名键本身的三处计数**（判据口径）：`multiNum…("etaMinutes", "eta", …)` 形态 **6 → 0**（T-ai）、
  闭包里其余"把 `eta` 当键名读"的位置 **0**（T-ai2）、演示面**没有**长出 `"eta"` 生产者（T-ai3）。
  反证打在改动前那棵整树副本（`/tmp/ledger_replay`，mtime 09-29 21:18）上：同一 grep **返回 6** ⇒ 这两个 0 有资格被报出来。

### 八、做了什么、没做什么（尾巴全在册）

**做了**：解析层 6 处自造别名候选键删除（`Models.kt` 四处 + `Repo.kt` 两处，改动只在读法，不动字段名）；
`RouteGuide` 的 KDoc 按实测重写（后端**从不**给 `etaMinutes`，那个键是客户端自己起的名字）；
契约用例 +4 条并同轮抬 T-k/T-x 两条 pin；T-ai/T-ai2/T-ai3 三条判据 + ETA 簿 +4 条负控；
跨簿锚点失效这条永久规矩**适用范围被实测写窄并升级**；本轮判据/负控自己的五条缺陷逐条抓出并改掉。

**没做（在册尾巴，逐条都是"没做"不是"不需要做"）**：
① 其余 `Minutes` 家族第二候选名 **5 处**（`Models.kt` 479 `plannedMinutes` / 729 `waitDepartureMinutes` /
   1110 `totalMinutes` / 1328 `plannedDurationMinutes` / 1802 `walkMinutes`）**一处没动** ——
   判据里以"·"行如实打印、不当已通过；下一轮队首要**逐端点重核契约**，**不许**把 E-1 的结论直接抄过去
   （`waitMinutes` 这类完全可能是小程序真在用的键，手里没那份实测就是猜）。
② `QuoteResult` 那处"缺键折成 `0.0`"的**同类形状**仍在（E-1 只清了 `eta` 这一族）；
③ `Repo.optBoolean(…, true)` 默认真的那两处仍在册；
④ **设备侧一字未验**（`NOT VERIFIED ON DEVICE`）：本轮结论全部落在"读法 + JVM 用例 + 判据"这一层，
   屏幕上"这一行有没有少一条信息"没有任何证据；
⑤ `release` 产物仍 `BLOCKED`（`assembleRelease` 配置期即失败：release 必须 HTTPS ⇒ 先改 baseUrl 再补 keystore）；
⑥ git checkpoint 仍 `ENVIRONMENT_BLOCKED`（本机无仓库）；
⑦ **没有**为了让 T-ai 变 0 去放宽任何一条既有判据，**没有**改九条真实性白名单，**没有**新增文件（故 `THIRD_PARTY_NOTICES.md`
   §1–§4 四张许可证表一字未动，依赖坐标仍实测 **20** 条）。


## 80. "演示数据"这四个字曾经替一个 NullPointerException 背了锅 —— F-1 把失败分成两类，并让"取消"不再是失败（2026-09-30，指令 v2.1 §六 · §七 · §三十一 / D-034 / 判据 §八 ㉑）

### 一、这条缺陷为什么在下面四层里，而不是在页面上

页面上的"空态 / 错误条 / 演示标签 / 取消之后不再刷新画面"全部是**下游读数**：
`Api` 把一次失败说成什么，`Repo` 就照那个形状回 `One(null, error = …)`，界面就渲染什么。
所以两处谎话都不在 UI 层，而在出网那一层与协程那一层：

- **§六 那一半**：`Api.execute` / `Api.upload` 原先用 `catch (e: Throwable)` 兜住一切再回落 `DemoData`。
  后果不是"演示数据多了"，而是**缺陷获得了一个合法身份**：`IllegalStateException` / `NullPointerException` /
  `ClassCastException` / `LinkageError` / `OutOfMemoryError` / `AssertionError` 都会被写成"后端给了演示数据"，
  界面上一切正常，堆栈在那一刻丢掉 —— 这类缺陷**再也复现不了**，因为现场被替换成了演示态。
- **§七 那一半**：`catch (e: Exception)` 会接住 `CancellationException`（它继承 `Exception`），`runCatching` 接住 `Throwable`。
  于是"页面已划走 / ViewModel 已销毁 / 外层超时 / Worker 被系统停止"这一类**取消**被写成一次"加载失败"，
  代码继续往下把 `null`、把 `Result.success()`、把 UI 状态写回一个已经不该再被写的地方。
  `NotifyWorker.doWork()` 是最直白的一处：吞掉取消之后 `return Result.success()`，等于替 WorkManager 宣布"这一轮跑过了"。

两句谎话结构相同：**客户端替后端宣布了一个结果**（"后端有数据"），或**替调度方宣布了一个结果**（"这轮做完了"）。
这与 §26/§27"状态一律以回读为准"、V-3"宁可没有上报，不允许假上报"是同一条红线的三个方向。

### 二、动手之前量的三份事实（不是印象，都是当轮 grep）

1. `app/src/main` 里生效的 `catch (e: Throwable)` 共 **6** 处（F-1-a 分类读数），`catch (e: Exception | Throwable)` **69** 处。
2. 分级之后真正会吞取消的是"try 首段里有挂起点"的那一批：`catch (e: Exception)` 全 main **49** 处
   （`grep -rn "catch (e: Exception) {"` 裸数与排除注释行数**相同** ⇒ 这一族没有被注释顶替的风险），
   其中挂起形状 **48** 处、非挂起 **1** 处（`DriverSdkNavCard.kt:471` 唤起外部地图，`startActivity` 不可被取消）。
3. §七 点名要建的那个"unified helper"形状，**工程里已经有了**：`apiCall`（`Api.kt:60`，`suspend inline`、
   先 `throw e` 穿透取消、五类 typed 分支，`ApiTest.kt:165` 有"只做分类不吞异常"一条用例）——
   但**生产调用点实测 0**。这条读数决定了本轮的命名与范围（见 D-034 第 2 点），也决定了它**不能**被写成"本轮建了 safeApiCall"。

### 三、做了什么（三层，逐层都有会红的数）

- **回落面白名单化**：`Api.execute` / `Api.upload` 各只留 `NetworkException` + `ApiException` 两类回落，
  每一处前面一行 `if (!demoAllowed) throw e`（**4/4 一一对应**），正式构建里 `demoAllowed` 恒 false
  （`NetTestOverride.demoAllowed ?: BuildConfig.DEMO_ALLOWED` + release 的 `DEMO_ALLOWED` 硬编码 `"false"`）。
  `Api.kt` 里兜底的 `Exception/Throwable/RuntimeException` 出口归零。
- **取消穿透**：`Repo` 38 处逐点插入 `catch (e: CancellationException) { throw e }`；`ScreenViewModel.work()`（所有页面重载都过的漏斗）
  同样补上（`load()` 本来就有）；`OrdersScreen`/`GoodsScreen`/`SendScreen`/`NextBusWidgetProvider`/`LoginViewModel`/`DriverLocationService`
  逐处补；有挂起点的 `runCatching` 换成新立的 `attempt { }`（12 个调用点：开屏预取 2、前台轮询、Worker、引擎算路、照片读取与上传、定位取点、`Handover`、`Workbench` 等）。
- **两处保留 + 一处特例**：`Attempt.kt:22` 与 `ActionGate.kt:63` 的 `catch (e: Throwable)` 保留且**各自有理由**
  （前者先穿透取消再兜其余、把判断交给调用方；后者是"清 `heldUntil` 后原样 rethrow"，改成类型化会让某些缺陷**不释放锁**）；
  `HomeScreen` 轮播写成**条件重抛**（只有 Job 真被取消才 rethrow）—— 用户手指抢走滚动产生的取消必须咽下，否则轮播会自己停掉。

### 四、用例（§三十 那两个命名类，12 条，全绿）

- `DemoExceptionIsolationTest` **7** 条：探针是一个 `toString()` 会抛的查询值（在 `rawExecute` 组 URL 时抛，请求根本发不出去），
  ISE / NPE / CCE / LinkageError 四种在**演示形态与正式形态两种**下都必须原样穿出，且 `server.requestCount == 0`；
  另两条正向用例证"传输 500 / 业务码失败"仍照常回落演示数据（`p.demo` 为真）—— **没有**为了变绿把回落整个删掉。
- `ApiCancellationTest` **5** 条：`attempt` 让超时取消原样送达 `withTimeoutOrNull`（并断言那次赋值根本没发生）、
  `attempt` 把真实失败收成 `Result`（类型恒等）、**旧写法对照条**（见下面第五节第 ③ 条）、
  `Repo` 读接口不把取消写成一次普通失败（`setBodyDelay(1_200ms)` + 150ms 取消 + `cancelAndJoin`，断言 `feed == null` 且抛出的是 `CancellationException`）、
  真实失败仍回带 `error` 的 Feed（不回归）。

### 五、三条被实测推翻的前提（照实记，不许顺着指令原文写台账）

1. **"Repo 里每个 runCatching 都要改"不成立**：`openMeteoWeather` 的 JSON 块与 `NavIcon.valueOf` 那两处
   try 首段里没有任何挂起点，给它们加取消出口是给不存在的通路开药。**改成判据**：TH-e 的免检计数（恰 1）把"哪些可以不检"变成公开的数。
2. **"`ScreenViewModel` 整仓缺取消处理"半对半错**：`work()`（:41）确实缺，本轮补上；同文件 `load()`（:110）**早就有**。
   ⇒ 指令层面的"审计每一个 catch"要落到**函数粒度**，按文件一刀切会漏也会多改。
3. **`Locator` 那一处改动可观测差为 0，本轮照实写**：`withTimeoutOrNull` 即使内层块自己吞掉 `TimeoutCancellationException`
   并正常返回，框架仍会把结果强制取消 ⇒ 旧写法 `runCatching { locations(context).first() }` 与新写法
   在**超时这条路径上行为完全一致**。这一处改的是**形状**（不再给下一位读者留"这里似乎在吞取消"的误导），
   而"差为 0"这件事由 `ApiCancellationTest` 的对照条钉成实测，不是口头承认。

### 六、判据与负控自己的四条缺陷（本轮自抓，全部改掉）

1. **`bash` 里的引号嵌套**：三条失败消息用 ASCII 双引号包中文短语（`…一次"加载失败"`），在 `echo "…"` 里会截断字符串、
   把短语打没。⇒ 消息统一改「」，与既有各簿一致。
2. **`awk` 字段号数错**：TH-e 的 python 摘要行从 `VIOLATIONS n` 换成 `TOTAL … SHAPED … SKIPPED … ORPHAN … VIOLATIONS n` 之后，
   三个读数全部错位（判据打印出 `= SKIPPED`、并把"≥47"判成非整数）。⇒ 改 `$10/$4/$6`，并在负控里保留一条"扫描面缩水必须红"的破口，
   这样字段再错位会当场报红而不是静默读到一个字符串。
3. **python 在 Windows 上把中文打到 GBK 控制台**：诊断行经 `grep` 时被判定为二进制 ⇒ `Binary file (standard input) matches`，
   破口清单**整段消失**（判据仍然 rc=1，只是没人看得见是哪一处）。⇒ 诊断行改成**纯 ASCII**（`UNPAIRED/SKIPPED/TOTAL`），
   中文只留在 bash 自己 echo 的消息里；过滤器加 `-a`。
4. **"豁免面"必须有数**：TH-e 只打违例数时，"把扫描规则写宽"与"把破口删干净"输出一样。
   ⇒ 三个数一起打（违例 0 / 挂起形状 48 / 免检 1），免检那位钉成**恰好 1**，负控里有两条专打它（塞一个挂起调用进去 ⇒ 红）。

### 七、实测数字（2026-09-30 收工，全部当轮实跑；负控批处理跑完之后又重测了一遍）

- **全量用例**：`./gradlew -Dfile.encoding=GBK :app:testDebugUnitTest --rerun-tasks` → `BUILD SUCCESSFUL in 57s`、
  **`24 actionable tasks: 24 executed`**、rc=0。产物 `app/build/test-results/testDebugUnitTest/` **31** 份 XML，
  逐份读出再自相加 ⇒ **311 tests / 0 failures / 0 errors / 0 skipped**，31 份 mtime 全为 **17:02:02**。
  ⚠ 这一遍是**必须**的：本轮五套**就地**用例级负控（异常 / 幂等 / 壳路由 / 导航 / 角色）都带 `--tests` 过滤，
  跑完会把 `test-results/` 洗成只剩一类 ⇒ E-1 那份"299"的产物支撑在 16:4x 那批复跑中已不存在，**不能沿用**。
- **+12 的归因是机器比对出来的**（不是回忆，也不是"总数对得上就算"）：把 31 份 XML 的逐类计数与 §79 那张 29 类表对着跑，
  结果 **新增类只有 `DemoExceptionIsolationTest` 7 与 `ApiCancellationTest` 5**、**消失类 `[]`**、
  **既有 29 类逐份条数一字未变（`CHANGED: []`）** ⇒ "311 = 299 + 12"这一句才有资格写。
  ⚠ **比对基线自己也错过**：第一次把类名手写成 `MissionMapContractTest` / `ProductOrderContractTest` / `SendOrderTest`
  （真实类名是 `MissionMapTest` / `ProductOrderTest` / `SendOrderTest`，**没有 `Contract` 后缀**），
  于是打出"新增 5 类、消失 3 类"的**假差异**。改成真实类名后才是 `[]`/`[]`。
  ⇒ 教训与 ⑳ 第①条同族：**"无孤儿类"这个结论依赖基线表的键名，键名写错时它会以"多算了 8 条"的形式骗人**。
- **APK**：`app-debug.apk` **114,437,316 B** / mtime **16:45:11**（16:4x 那次 `:app:assembleDebug :app:lintDebug`
  → `BUILD SUCCESSFUL`、**`48 actionable tasks: 12 executed / 36 up-to-date`**）。
  收工复检再跑同一组任务 → **`BUILD SUCCESSFUL in 3s`、`48 actionable tasks: 1 executed / 47 up-to-date`** ⇒ **没有重新打包**，
  照实记 UP-TO-DATE。**但这一遍不是走过场**：gradle 的 UP-TO-DATE 是按**输入内容哈希**给的，
  而本轮五套就地负控在 16:41–16:58 之间改过又 `cp` 恢复过真实源码，
  "打包相关任务全部 UP-TO-DATE"正是**盘上这份 16:45 的包与当前源码内容自洽**的机器证明（配合各簿末尾的 `cmp 一致`）。
- **lint**：`lint-results-debug.xml` **98,753 B** / mtime **16:47:20**（html 258,534 B / txt 31,452 B 同批），
  `ElementTree` 逐条数 `issue@severity` = **0 error / 46 Warning / 32 Information = 78** ——
  与 §79 那轮**逐项相同** ⇒ 本轮 main 树里**改动的既有生产文件 17 个 + 新增自写生产文件 1 个（`Attempt.kt`，24 行）= 18 个文件参与改动**，
  它们**没有引入任何新的 lint 类别**（新增的 `Attempt.kt` 在 `location/@file` 里 0 点名）。
  ⚠ 这个 17 的**来路要写清**（它是 `THIRD_PARTY_NOTICES.md` §6 同一张清单的数，两条腿一起跑出来的）：
  三条内容 glob（`attempt {` 7 个 / 生效 CE 子句 4 个 / `if (!demoAllowed) throw e` 1 个）去重 = 10（含新的 `Attempt.kt`）
  ∪ 编辑窗口 08:13–08:28 的 mtime 清单 = **并集 17**（含 `Attempt.kt`）⇒ 既有 **16** ——
  再用"注释里引用 §三十一 的文件"与并集作差，捞出**第 17 个** `ui/driver/WorkbenchScreen.kt`：
  它的收法是**整圈 `runCatching` 仪式摘掉**，所以**glob 里没有它的字面量、mtime 窗口里没有它的时间戳**（16:55 是就地簿 `cp` 恢复），
  两条常规清点腿**同时漏掉一个真实改动**。⇒ 记进 §八 ㉑：删除型改动要靠作差，不能只靠 glob。
- **十三套探针全 rc=0**（`out=$(bash "$s" 2>&1); rc=$?`，末行逐条实抄）：
  `分域判据通过 / 契约信封判据通过 / ETA 判据通过（公交侧 + 货运侧 + ⑬ 里程口径 + E-1 别名键归零） / §54 异常上报判据通过 /
  幂等判据通过 / 定位与上报判据通过 / 第 8 步导航引擎判据通过 / 可达性判据通过 / 角色同源判据通过 / 壳路由判据通过 /
  状态映射判据通过 / `**`异常可信化判据通过（§六 缺陷不进演示数据 / §七 取消不被吞）`** ——
  外加 `check_authenticity_counts.sh` rc=0（只打数）。⇒ 本轮**没有**为了让新判据变绿而动任何一条既有判据。
- **负控十三本全批复跑（`✘` 与 `△` 必须同时为 0，实测逐本如此）**：
  结构簿 `eta` ✔=54 · `exception` 21 · `idempotency` 11 · `navigation` 27 · `reach` 21 · `role_reconcile` 13 ·
  `shell_routing` 13 · **`throwable` 19**；就地簿 `exception_test` 8 · `idempotency_test` **0** · `navigation_test` 10 ·
  `role_reconcile_test` 6 · `shell_routing_test` 7。
  ⚠ **两个"计数语义"必须写清，否则这两个数会被读错**：
  ① `throwable` 的 **19** = **18 条破口 + 1 条末尾自检行**（`✔ 恢复后基线仍为绿`）；
     破口条数由脚本静态数得（`grep -cE '^ctl '` = 16，另有两条写在 `kill_test` 同一行 ⇒ 16+2=18）。
     台账草稿原本写"✔=18"，那是**按结构推的**；批处理按留档行数打的是 **19** ⇒ 改成实测，与 ⑳ 第⑤条同一条规矩。
  ② `idempotency_test` 的 **✔=0** **不是"这本没有负控"** —— 那个簿**不打印 ✔ 标记**，它的三条标记是
     `[破口后] gradle exit=1` / `[恢复] cmp 一致` / `[恢复后] gradle exit=0`，rc=0 来自簿内自己的闸门。
     ⇒ 永久口径补充：**按标记 grep 出来的计数，必须先证那本簿用这套标记**；否则一个 0 既能被读成"没测"、
     也能被读成"测过而全绿"，两种都是假的。
- **就地簿的分段墙钟**（按 `/tmp/*_neg_*.log` 的 mtime 清点，不推算）：异常 16:41–16:43（基线 + X-1…X-8 + 恢复）、
  幂等 16:44（单份 `idem_test_neg.log`）、壳路由 16:55–16:57（基线 + M-1…M-7 + `shell_neg_recover.log`）、
  导航 16:48–16:51（基线 + M1…M8 + `nav_neg_after.log`）、角色 16:52–16:55（基线 + RRt-1…RRt-6 + `rr_neg_restore.log`）、
  throwable 簿在沙箱内跑（`/tmp/th_neg.20414`，不落 `*_neg_*.log`）16:57–16:58 收尾 ⇒ **整批 16:41–16:58，约 17 分钟**。

### 八、做了什么、没做什么（尾巴全在册）

**做了**：`Api.execute` 的演示回落面从"兜住 Throwable"收成**只兜住 `NetworkException` / `ApiException` 两类**（TH-l 2/2/0），
其余程序错误原样抛出（TH-c 全 main `catch(Throwable)` 生效 **2** 处且都在白名单：`Attempt.kt:22` 收成 Result、
`ActionGate.kt:63` 清锁后 rethrow；Api.kt 内 **0**）；取消穿透在 **Repo 38/38** 逐点配对、
UI/服务侧按"取消从哪来"分三种收法（`attempt {}` 替换挂起形状 / 整圈 `runCatching` 仪式摘掉 / 条件重抛）；
统一 helper 落地为 `core/net/Attempt.kt`（与 `runCatching` 唯一区别是先 rethrow `CancellationException`）；
新用例 **12** 条（`DemoExceptionIsolationTest` 7 + `ApiCancellationTest` 5）；
新判据 **TH-a…TH-l**（12 行读数 / 27 道闸门）+ 新负控簿 **18 条破口**；
"扫描面由内容决定""三个 0 各有出生证明""`assert old not in new`"三条方法写进判据与脚本；
跨簿注入锚点按 ⑲ 的升级规矩**全部复跑**（不是只复跑本轮新加判据那本）。

**没做（在册尾巴，逐条都是"没做"不是"不需要做"）**：
① **`apiCall` 仍是"已存在但没人用"**：生产调用点实测 **0**（判据里 Z1=1 是它自己的定义行）。
   §七 点名要的 unified helper 我立的是 `attempt{}`，因为它的用场超出网络层；`apiCall` **没删**（删它要动 `ApiTest` 那条用例，是减量不是收口）。
② **`runCatching` 在 main 里仍剩 34 处**（Z2 只打数不判红）：本轮只收了"try 里有挂起点"的那一批（TH-g 七个点名形状 → 0），
   其余（如 `WorkbenchScreen.kt:1774` 的 `callDriver`，纯同步 `startActivity`）**没有挂起通路 ⇒ 不在本轮缺陷范围内**，
   照实记为"未收"而不是"已判无害"。
③ **§二十 的 Worker 重试语义没做**：真失败该回 `Result.retry` 而不是 `success` —— 本轮只收了"取消不许谎报这一轮跑过了"（TH-j）。
④ **失败四类分型（§三十三 SOURCE/DEPENDENCY/ENVIRONMENT/DEVICE_UNAVAILABLE）没落进代码**：
   本轮落的是"缺陷 vs 失败"两类与"取消不是失败"；四分类仍只是台账口径。
⑤ **设备侧一字未验**（`NOT VERIFIED ON DEVICE`）：真实取消来自"手指划走页面 / Worker 被系统停止 / 进程被杀"，
   JVM 用例只能造 `cancelAndJoin` 与 `withTimeoutOrNull` 两种；页面离开后不再往 StateFlow 上写这件事**没有设备证据**。
⑥ `release` 仍 `BLOCKED`（`assembleRelease` 配置期即被 HTTPS 闸门挡下）、git 仍 `ENVIRONMENT_BLOCKED`（本机无仓库）。
⑦ **没有**新增任何依赖或第三方源码：依赖坐标实测仍 **20** 条，四张许可证表一字未动
   （本轮唯一的新生产文件 `core/net/Attempt.kt` 是本工程自写，登记见 `THIRD_PARTY_NOTICES.md` §6）。

## 81. 段坐标那四个别名盖住了真键，而 5 条红是"用例在替闸门说话" —— G-1…G-4 一批的收口读数（2026-10-06 16:0x 起手 → 20:56:25 APK 终值 → 21:03:34 设备闸门；台账 D-062 / §八 ㊶）

### ① 这一轮动的四格，各自凭什么算"做了"

**G-1 段坐标：撤读四个三面零生产者的自造别名，改按后端真键读**

- 三方核对：Android 解析在 `TransportLeg.parse`（`Models.kt:1047-1136`）；后端 `AppSendLegRespVO.java:24-33` 下发的就是
  `fromLongitude / fromLatitude / toLongitude / toLatitude` 四个键，且 @Schema 自己写了"站点档案无坐标时为 null，
  前端不得据此画直线补线"；小程序侧读的也是这四个 ⇒ 旧解析里的 `startLatitude/endLongitude` 一批是**三面 0 生产者**，已撤读
  （现读全工程 `startLatitude|endLongitude` **0** 命中、`fromLatitude` **1** 命中＝就是这一处）。
- 四个坐标改走 `multiNumOrNull` ⇒ 缺键 = null，不再塌成 `0.0` 让地图把点落进海里。顺带把 DV-k 基线 **115 → 111**（少 4 处非空读法），
  这是**收紧**；判据簿 5 处字面锚（说明行 / 打印行 / CSV 注释 / 闸值 / 桥的报错文案）同步重锚，DV-w 口径桥实测 **111 = 109 行读法 + 2 行函数声明**。
  ⚠ DV 负控注入的是**一条 `multiNum` 行**而不是硬编码的旧数字 ⇒ 111+1=112 > 111 仍然红，红的理由是"回涨"而不是"簿子过期"。

**G-2 任务族：只留后端有的字段，并把"加载后从未渲染"的 tasks 画出来**

- 后端 `AppDriverTaskRespVO.java` 现读 **19** 个 `private` 字段（逐条数出来的）。`DriverTask`（`Models.kt:2088-2103`）只剩
  `planId`（缺键 = -1）+ `taskWindowStart` / `taskWindowEnd`（走 `reportTimeOf`，**不许回落 `createTime`** 去给任务段编一个没发生过的窗口）
  + `MissionStop.parseTaskItem`；`taskType/type/bizType`、`goodsName/cargoName`、`itemCount/count`、`actionLabel/taskName`、`planTime`
  这几族幻影键在**任务族**已 0 读取点（⚠ 别的模型里同名键还没逐族核对，见 ③）。货名后端根本没有 ⇒ 界面不再显示货名。
- 面板：`WorkbenchScreen.kt:710-768` 的"今日运营任务"，同站多动作由 `DriverTaskSegment.group` 聚一条（口径抄小程序 workbench
  行首取该站 `visitSequence` 最小的那条）；段的先后只跟后端整表的升序，**本机不重排**（调度决定去哪里）。
  接口没给那一支写的是 **"任务段接口未给"**（`WorkbenchScreen.kt:853`），不是"今天没任务"。
- 同轮：用例 `DriverTaskRespContractTest`、判据 `DV-y`、负控制（#265/#266）。

**G-3 去演示数据：闸门一格没放宽，红的是四本用例自己的前提**

- 构建面：debug 的 `demo` 属性缺省即 **false**（只能由工程外 `-Pbuspost.demo=true` / local.properties 临时开），release 硬编码 `false`；
  闸门读点在 `Api.kt:201`，演示回落入口 `Api.kt:457-460` 抛 `生产构建禁止演示数据`。
- 默认 strict 之后 **5 条红，分布在 4 个类**（用例名逐条抄自构建日志原文）：
  `EarningsContractTest > 演示面 earnings 报文与后端契约同形`、`MinutesContractTest > 演示包里的方案时长能被解析层读出（防止 parser 与 DemoData 键名漂移）`、
  `NearbyBooleanContractTest > 演示面 nearby 不含这两个键 解析结果为未知`、`NoProducerKeyFamilyContractTest` 的两条（`演示报文里不再有那批无生产者键`、
  `演示报文仍能被解析层读出非零的真实口径`）。⚠ **根因不是代码坏了**：这五条量的是"演示报文键形 ↔ 解析层是否漂移"，
  报文来自 `DemoData.respond(...)`，而闸门按定义要拒演示回落 —— 是**用例在替闸门说话**。
- 做法：在这五条用例里**显式** `TestHost.demoBackend()`（`TestSupport.kt:79`），并给两本**不继承 `ServerTest`** 的类
  （`MinutesContractTest`、`NoProducerKeyFamilyContractTest`）各钉一条 `@After { NetTestOverride.reset() }`，防演示形态顺着共享 JVM 漏给后面
  没要求它的类。**`wrap()`、`Api.kt:201`、闸值一字未动** —— 放宽闸门就能全绿，而那正是本轮拒绝的做法。

**G-4 UI 布局系统化：26 个页面级容器调用点里 24 个并到 `spacedBy = 12.dp`**

- 本轮批量收了 18 个屏（`ui/driver/` 7 ＋ `ui/user/` 11，mtime 全落在 20:28:25 那一秒＝同一批脚本写入），缺值口径统一"未取到就说不取到"
  （如 `MineScreen.kt:303` 的 `"—"`）。
- ⚠ **这一格没有判据簿**：`tools/*.sh`、`tools/*.py` 里 `BusPage|spacedBy` **0 命中** ⇒ G-4 的证据只有"编译过 ＋ 532 条用例过 ＋ lint 0 error"，
  间距本身**没有被量**；仍不一致的 2 处是 `HomeScreen.kt:309` 与 `OrdersScreen.kt:206-208`（都是 10.dp）。
  ⚠ 本轮**没有**为了这两处再动 main 源码并重打包 —— 改了就要重新取证，代价写在这儿而不是顺手改。

### ② 终值读数（唯一采信遍，全部实测）

| 项 | 读数 |
|---|---|
| gradle | rc=**0**、`BUILD SUCCESSFUL in 1m 32s`、**`53 actionable tasks: 53 executed`**（`:app:testDebugUnitTest :app:lintDebug :app:assembleDebug --rerun-tasks`） |
| 用例 | **532** 条 / failures **0** / errors **0** / skipped **0**（**50** 个测试类，`TEST-*.xml` mtime 20:56:34） |
| lint | **75** 条 = Warning **46** ＋ Information **29**，errors **0**（计数式是 `<issue[\s>]`，`<issue>` 打不中） |
| APK | **113,928,596 B** @2026-10-06 20:56:25.897 / sha256 `571c3cbaecae15c59e6bf83c696a032e6f30b7e73279572c4c717d151eb9a44f` |
| 确定性正控 | 与 20:46:13 那一包**逐字节同、sha256 同** —— 20:5x 之后只动了 test 源码 ⇒ 本轮不需要为"包变了没有"专门重打一次 |
| 改树指纹 | `TREEV2 files=248 sha256=d6e7ea61e44deacd2a02f6d0a30bc41e9c97a9aa0db1aadf1cb08651566ebdb3`（改前 `07cff2c1…`，**同文件数**⇒ 本轮 0 新增 / 0 删除文件） |
| 判据簿 | **23/23 rc=0**（含 `check_default_value_probes.sh` 的 DV-k=111、`check_doc_anchor_probes.sh`、`check_test_suite_hygiene_probes.sh`） |
| census | DV 口径桥 **111 = 109 ＋ 2**；V2.1 核对表 **119** 行 rc=0 |
| 表格自检 | `tools/check_ledger_tables.py` rc=**0**、**TOTAL FLAGS 0** |
| 设备 | rc=**1** ⇒ `DEVICE_UNAVAILABLE`、**0 台在环**，证据目录 `docs/device-evidence/20261006-210334` |
| 后端 | 48080 未监听 ⇒ 本轮**没有**真机联调；后端侧只做了逐字段读 VO，**0 处后端代码改动** |

### ③ 未收口（逐条带"为什么不在这轮做"，不冒充已修）

① ~~**ParcelScreen 的计数诚实化**（#269）：分页总数与"失败不念 0"仍未收口~~ ⇒ **已由 §82 收口**（第三遍 rc=0、用例终值 23:17:32；残留 `D-G3b-1` 与"网络层缺键回落"两格挂在 §82 ⑥，不冒充已修）。
② **5 屏下拉刷新**：本轮 0 处，代价是这几屏只能靠"进入页面重取"。
③ **未路由的 `SettingsScreen` 副本**：`ui/user/SettingsScreen.kt:55`（带 `onBack`，无默认值）与 `ui/user/MinePages.kt:838`（无参）同名并存，
   路由 `ui/BusPostRoot.kt:142`（`Routes.SETTINGS` 那一行）解析到后者 ⇒ 前者是 dead UI。⚠ **本机无 git**（`GIT=ENVIRONMENT_BLOCKED`），删文件不可回滚，**本轮只登记不删**。
④ **剩余别名族**：折线仍读 `navigationPolyline|polyline|track`，坐标仍接受 `latitude|lat` / `longitude|lng` 混名 —— 本轮撤的只是"三面 0 生产者"
   那批；这两族要逐端点核后端 producer 才动，所以先留（与 #159 同一件事）。
⑤ **同名幻影键在别的模型里**：`goodsName|cargoName`、`itemCount|count`、`bizType` 在商品/交接/通知族里还在读（`Models.kt:997`、`Models.kt:1158`、
   `Models.kt:1026` 一带），本轮只做了任务族 ⇒ "这一族干净"不等于"这些键名都干净"。
⑥ **G-4 无判据、无设备**：见 ①-G4 与 ② 那两行。

### ④ 三次自伤（第三次／第二次／头一次踩，写在这儿因为它们每轮都会重演）

- 并发把**就地注入**的负控簿又起了一次 ⇒ **8 条假红**（driver PID 还在跑）。⇒ 停批只按 driver PID 判，就地簿要让它自己恢复，
  半途 kill 会把注入态留在盘上；证明手段是 `find -newermt`，不是"我看它停了"。
- `-Dorg.gradle.jvmargs` 没整体加引号被 shell 拆词 ⇒ `Unknown command-line option '-X'`（第三次踩）。⇒ gradle 的三条 JVM 参数当作**一个引号 token**。

- **落档那一步自己把锚点账写红了**（头一次）：㊶ 写完后 `tools/check_doc_anchor_probes.sh` 首跑 rc=**1** —— DA-d 新增 **5** 个不在册 aid、DA-e ID-MISS **293** 顶过闸值 **286**，而红的 **7** 条记录全是本轮新写的锚点。机制：标识符是按**离锚点中心最近**分配的，我把旁白（旧别名那几个名字）留在句尾、又让 `etaMinutes` 挨着上一条锚点，于是每条锚点都被要求包含别人的名字 ⇒ 台账读起来对、机器读起来全红。
  ⇒ 规矩：**每条新锚点后面紧跟一个"该窗口里真有的标识符"当见证**（`fromLongitude`/`fromLatitude`/`navigationSource`/`Routes.SETTINGS`），不指锚点的旁白**另起一行**（无锚点的行不进取词面）。**没动 286 那道闸、没重抄在册表**：复跑 rc=**0**、ID-MISS **286**（回到基线、未降）、新增 **0**、消失 **0**（在册那几条仍 ID-MISS ⇒ 见证词没把旧红误判绿）、OK **974 → 981**、DA-f **599 / 298**。逐条见 D-062 §9。

### ⑤ 三态与旧限制

- `SOURCE_COMPLETE` = G-1…G-4 四格（G-4 只到"改过"，不到"量过"）；`BUILD_VERIFIED` = ② 那遍；**`DEVICE_VERIFIED = BLOCKED`**。
- `release` 仍 `BLOCKED`（`assembleRelease` 配置期被 HTTPS 闸门挡下），`git` 仍 `ENVIRONMENT_BLOCKED`（本机无仓库），
  48080 默认不起 ⇒ 本轮所有后端读数来自**读源码**，不来自调用它。

## 82. #269（G-3b）收口：寄/购两格改读后端分页 `total`，"这次没问到"不再念成 0（2026-10-06 22:4x 判据现读 → 23:17:32 用例终值 → 23:18:28 设备闸门 → 2026-10-07 04:07 干净重打；台账 D-063 / §八 ㊷）

### ① 三方对齐：`total` 一直在树上，是客户端去数了"这一页拿回几条"

| 面 | 本轮实测 | 结论 |
|---|---|---|
| 后端 | 寄货页与商城页两个分页接口的返回语句是同一句形状（`new PageResult<>(list, pageResult.getTotal())`），而分页结果对象声明的就是 `total` | **0 处后端改动**、**0 个新接口**：总数本来就在线上 |
| 客户端 | 网络层那一行的 `optInt("total", list.size)` 是**缺键才回落**去数本页 ⇒ 回落形状属于网络层那份契约 | 本轮**不判它、不改它**，只把页面上的读法改成"读后端总数" |
| 页面 | 两个计数改成可空 ＋ 一个唯一口径出口：只有请求成功才写数字，失败写「未取到」 | 旧写法在两件事上撒谎：**第 21 单起少念**（本页 pageSize=20）与**接口挂了念成"你 0 单"** |

⚠ 三方的第三方（小程序）那一格本轮**没量** —— 上面这一行只对齐了"后端给什么、Android 读什么"，不许读成"三方口径已一致"。

### ② 行为面钉的是三件事，不是一个数

- 137 与"本页 2 条"要能分开：断的是**读到后端总数**，不是"取回几条就念几条"。
- 一支失败不影响另一支：寄货失败那格念「未取到」，商城那格照旧念数 ⇒ "一处接口挂了 = 整页归零"这条老路被用例堵死。
- 还没加载完时两格都是空 ＋ **一次请求都没发出去**（断的是请求计数，不是文案）⇒ 页面初次进入不许念任何数字。

新用例是一本**新文件**（`ui/user/ParcelCountContractTest`，4 例），未新增任何一格 UI、未动任何一处调用点线程。

### ③ 判据与同轮负控（逐值只记在 D-063 §4，本簿不复述，免得同一读数出现两个出处）

`DV-z` 一族 ＋ `GUARD5`（块 **19** 行）整本 rc=**0**；负控 **10** 条注入整本 **70 ✔ / 0 △ / 0 ✘**、基线与恢复后同为绿。
⚠ 这一族**只按页判**：扫到网络层会把合法回落一起判死 —— 与 #262 那族"只按块判"是同一条理由。

### ④ 终值读数（⚠ 含一处对 ㊷ 起稿的更正：**不是一遍跑成的，是三遍**）

| 项 | 本轮实测 |
|---|---|
| gradle | **三遍**：第 1 遍编译红（4 个具名实参错）→ 第 2 遍 `testDebugUnitTest` 红（接口路径靠猜）→ 第 3 遍 **BUILD SUCCESSFUL in 23s**、**53 actionable tasks: 6 executed / 47 up-to-date**、rc=**0**；⚠ 那三份日志当时落在 `%TEMP%`，**收尾已不在盘上** ⇒ "每一遍红在哪"这句现在不可复测，可复测的只有下面三行 |
| 用例 | XML **51 类 / 536 例 / 0 failures / 0 errors / 0 skipped**、51 份 XML 的 mtime 集合 = 单值 **23:17:32**（收尾重测）；⚠ **536 = 在册 532 ＋ 本轮 4** |
| lint | XML **94,681 B** @23:17:40、**75 条 = Warning 46 ＋ Information 29**、Error **0** ⇒ 与 ㊶ **同数、不构成回归**（html 254,149 B / txt 30,521 B） |
| 改树指纹 | `TREEV2 files=249 sha256=de7d9526…`（㊶ 是 248 / d6e7ea61…）⇒ **+1 新文件 / 0 删除** |
| APK | 盘上这份 **113,928,596 B**、mtime **2026-10-07 04:07:01**、sha256 `06f562a4…`（全串在 D-063 §6）⇒ 见下面 ⑤，**它才是这一轮的终值包**，起稿时那份 114,516,464 B 已被顶掉 |
| 设备 | rc=**1** ⇒ `DEVICE_UNAVAILABLE`、**0 台在环**，证据 `docs/device-evidence/20261006-231828`；48080 未监听 ⇒ **0 次真机联调** |

### ⑤ 包内字节账：`#140` 从这一轮起有**可复现口径**，但历史那一半仍未闭环

- 「包涨了 **+587,868 B**」这句在 ㊷ 起稿时被当成一次真实体积变化登记了。实测结论相反：**同一棵源码树**（`find app/src/main tools -newermt "2026-10-06 23:17:33"` = **0** 命中）下，增量产物与 `--rerun-tasks` 产物的**条目数据只差 −73 B**，而文件差 **+587,868 B** ⇒ 涨的全在**不被中央目录引用的字节**那一层，与内容无关。
- 干净重打这份的字节账**闭合**：局部头 152,777 ＋ 条目数据 113,688,365 ＋ **签名块 4,096** ＋ 中央目录 83,336 ＋ EOCD 22 = **113,928,596** = 文件大小。⇒ "未引用字节"里**全部是 v2 签名块**（`APK Sig Block 42`），**洞 = 0**。
- ⚠ 由此收窄一条旧说法：㊶ ⑨ 的"重打同包 = 免费的确定性正控"**只在同一 task 状态下成立**；增量产物与全重跑产物**不逐字节相同**（本条就是同输入的反例）。
- ⚠ 另一条更正（原话是"干净包 hole_total = 0"，那是**量法**错了不是结论错了）：量包内空洞要把签名块单独算一格，否则正常的签名包会被念成"有洞"。逐段读数与"哪一份还能重测、哪一份已不可复测"只记在 **D-063 §8**。
- `#140` 状态：**口径已闭环**（下一轮只报"条目级差量 ＋ 未引用区域差量"两个数，不再报混在一起的"包涨了 N KB"）；**历史那一半仍未闭环** —— 两份对照包都已被原地顶掉、本机无 git，不可回滚。

### ⑥ 未收口（逐条带"为什么不在这轮做"）

① `D-G3b-1`：「我的」页里同一条规则还手写着 **3** 处内联形态 ⇒ 挪到 `#159` 同批做。**没顺手改**的理由写下来：那个文件在册锚点多、行号一漂就把 DA-e 的 **286** 顶穿，而顶穿之后唯一能做的"变绿"是下调闸值 = **改窄闸门**，不是收口。
② 网络层缺键回落（"数本页"）仍在：本轮只保证**页面**不再用它冒充总数；回落本身要不要撤，是网络层那份契约的独立一轮。
③ `#140` 历史那一半（见 ⑤ 末行）。
④ 设备那一格本轮仍是**闸门 rc=1**；2026-10-08 起本机装了 Android Studio、AVD 已建，但**四次冷启都没进系统**（细节登记在 `#270`）⇒ 在拿到第一个真实 `DEVICE_*` 读数之前，`DEVICE_VERIFIED` 不因"装了 Studio"而改变状态。

### ⑦ 收尾那一遍：判据里有一格**真的红过**，而 TREEV2 从此不能当"还是同一棵树"的证据

- 写后终检 **23 本判据里 1 本红**：红在 `SU-b`（反证那一格的源集 `.kt` 数 **53 ≠ 52**），起因就是本轮新建的那本计数契约用例。
  那条判据是**"恰好 N"的摩擦设计** —— 加文件必须同轮把数改对，所以这不是回归，也不是可以绕开的噪音。
- 改对之前先把三句一起读：**4** 条 `@Test`（新那本不是空壳）／SU-a **51/0**／无 `@Test` 的夹具仍是 **2**（仍是那两份，没混进第三种文件）
  ⇒ 基线 **52 → 53** 与增量归因同时落进脚本注释；复跑该本 rc=**0**，配套结构负控整本 rc=**0**、恢复后仍绿，且定向 grep 显示**没有别的簿要跟着重锚**。
  ⚠ 这一格的红是**真实改动自己证活的**（比注入硬）：它在一个合法新增文件上确实红了，改对之后才绿。
- **TREEV2 变了两回、文件数没变**：构建时 **249 / `de7d9526…`** → 现在 **249 / `e1c1334c…`**。变处在 `tools/` 那半棵树：一本判据脚本的基线，
  加上 **`tools/ledger_table_report.txt` —— 表格自检每跑一遍就重写的生成物**。
  ⇒ 口径改为：**"这一包出自这一棵树"只按 `find app/src -newermt "2026-10-06 23:17:33"` 判（本轮实测 0 命中），不按 TREEV2 的 sha 判**；
  报 TREEV2 必须连"它数了哪些树、里面含不含生成物"一起报（㊷ ⑩ 的第三面）。
- ⚠ 顺序上的自伤：本轮先落档（把构建时那串指纹抄进状态板）、后改判据 ⇒ 台账里那个数**一写完就过期**。
  规矩：**判据与工具的改动排在落档之前**，落档那一步只做"抄一遍现成的绿"。逐本读数只记在 **D-063 §9**，本节不复述。

### ⑧ 三态

`SOURCE_COMPLETE` = 计数诚实化那一格（§81 ③ ① 由本节关闭）；`BUILD_VERIFIED` = ④ 那三遍里的第三遍 ＋ 收尾重测的用例/lint；**`DEVICE_VERIFIED = BLOCKED`**（闸门跑了、rc=1）；`release` 仍 `BLOCKED`（HTTPS 闸门，未绕）；`git` 仍 `ENVIRONMENT_BLOCKED`（本机无仓库）。


## 83. 「后端没发的键」不会报错，它只会把一个 `0.0` 演成"后端给的金额" —— #277 契约对表收尾：两族 Ack 别名撤读 ＋ 站点可达性那一格在后端（2026-10-08 12:2x 起手 → 12:50:47 APK 终值 → 12:56:27 设备闸门；台账 D-064 / §八 ㊸）

### ① 这一轮买到的是什么：48080 上是真后端

以往七轮的后端读数全部来自**读源码**（48080 没起）。本轮后端起来了（实测 **LISTENING = 2**），
所以 §十 那五节的证据是**真 HTTP 回执**：非零值逐个归因（**13** 处），零值逐个分类（**8** 行里 **5** 行为零，
分成"状态机拒 / 业务规则拒 / 契约不匹配"三类，只有后两类算缺陷）。
⚠ 这一句要说准：**"后端起来了"不等于"设备在环"** —— 设备闸门仍 rc=**1**、**0 台在环**（见 ⑥）。

### ② 两条 Ack 别名族：同一个错法，在两个下单端点上各犯了一次

- **寄货单**：解析读 `orderNo|bizOrderNo|no` ＋ `pickupCode|code` ＋ `fee|amount`，而后端那个 VO 的生产者键是 `orderNo / pickupCode / totalAmount`；
  五个候选键里 **4 个三面 0 生产者** ⇒ 金额恒塌成 `0.0`。
- **商城单**：解析读 `orderNo|bizOrderNo|no` ＋ `amount|totalPrice`，而创建回执 VO **只有 `id` 与 `orderNo`** ⇒ 同一个 `0.0` 错法。
- 两处都收成为"只认生产者键"，并在行内写清**金额的真相源在哪**（寄货读列表回读的 `totalAmount`；商城单不许在创建回执处造数）。
- ⚠ **这两条都不是"当下可见的错"**：`Ack.amount` 目前没有被页面消费（成功页那张车票的副券读的是**报价**那一路），
  所以本轮修的是**埋着的错**。这不影响它值不值得修 —— 但影响我能不能说"用户看到的数字变了"（**不能说**）。

### ③ 夹具与演示回落必须跟着改形状，否则同一行解析互相掩护

只改解析层会留下一个更坏的状态：演示回落那一份数据仍按旧别名装键，于是"后端给的金额是 0"和
"演示数据本来就没这个键"在同一行代码上**看起来都是合法的**。本轮三处一起动：
解析层（生产者键）＋ 测试夹具两处 ＋ `DemoData` 的 `/send/create` 回落形状（`fee` → `totalAmount`）。
配 **2** 条具名负控用例：一条只发那五个自造别名、断三个字段留空 / `0.0`；
另一条给商城单回执塞 `amount/totalPrice/no`，并**先断这一次真出过网**（被本机幂等闸拦下的那次是空回执，拿它证别名是假正控）。

### ④ 改 `Repo` 那一行时的硬约束：行数不能变

`Repo` 里躺着 **9** 个在册锚点。第一版我多写了两行注释 ⇒ 被钉住的行号当场漂了，
而锚点簿红了之后**唯一**能把它变绿的动作是下调 ID-MISS 闸值 —— 那是**改窄闸门**，不是收口。
⇒ 改成**一行尾注**复位，复跑锚点簿 新增 **0** / 消失 **0**。
这条与 ㊷ ④（`MineScreen` 那 3 处内联因此本轮不动）是同一课的两面：**在册锚点多的文件，改动预算是行数，不是字符数**。

### ⑤ 后端那一格：选择面与下单面的规则不同源，用户就会在"第一屏"撞墙

`/send/stations` 走的是不过滤可达性的裸列表查询，而下单用 `StationAccessUtil.vehicleAccessible()` 硬校验。
本机实测：**1814** 条未删除站点里 `vehicle_access=0` 的有 **9** 条（id **1–8、101**），且**恰好排在最前面**。
⇒ 在唯一一处 app 面出口按下单同源规则过滤（1 import ＋ 1 行），管理面**不动**（它要看得见全部站点）。
⚠ **补丁未经编译、未经在线复验**：本机没有 Maven、无 wrapper，在跑的 jar 是 2025-09-13 的产物 ⇒ 登记 **#278**。
同一格的正控（这个才是本轮能给的运行时证据）：可达站点对 **102→103** 直接打未打补丁的 jar，
`/send/create` 回 **code=0**、`{"id":251,"orderNo":"TP20261008122257715","status":8,"totalAmount":8.76,"reviewStatus":1,…}`
⇒ **下单闭环本身是通的**，卡点只在站点选择面。

### ⑥ 终值（逐本判据 rc、锚点簿读数与用例/lint/APK 明细只记在 **D-064 §3–§4**，本节不复述，免得同一读数出现两个出处）

| 项 | 本轮读数 | 一句话口径 |
|---|---|---|
| gradle | 一遍绿（**15 executed / 38 up-to-date**） | 收尾才起全量，改动过程中不起 |
| 用例 | **51 类 / 538 例 / 0 / 0 / 0** | ⚠ **538 = 在册 536 ＋ 本轮 2**，两条都是具名负控用例 |
| lint | Error **0** / Warning **46** / Information **29** | ⚠ **三份报告产物 mtime 仍是 11:08:01**（`lintReportDebug` UP-TO-DATE）⇒ 这一格是**复用产物**，不是今天的重测 |
| APK | **113,928,596 B** / sha `3bbf06f3…` @12:50:47 | 上一包被原地顶掉 ⇒ 只登记，不声称可复测（`#140` 原状） |
| 体积 | 未压缩 **166,336,545 → 166,336,481（−64 B）** 而文件字节数**相同** | D-063 §8 ⑤"同字节 ≠ 没变"**第二次正样本** |
| 判据 | **23/23 rc=0**（22 本 `*probes*` ＋ 补跑 1 本 `check_authenticity_counts.sh`） | ⚠ 起稿时报成 **22/22** 是**换了通配基数**，D-064 §6 ② 登记 |
| 表格自检 | **TOTAL FLAGS 0** | — |
| 设备 | rc=**1** ⇒ `DEVICE_UNAVAILABLE`、**0 台** | 证据 `docs/device-evidence/20261008-125627` |

### ⑦ 本轮四条自伤（都登记，不但书）

① **把复用产物念成今天的读数**（lint）；② **换了基数还报成同一件事**（22/22 vs 23/23）；
③ **又把负控批接在 `tail` 后面** —— 一本十五分钟的批只留下最后 14 行，而那本的退出码因为末句是 `echo` 天生不可信
⇒ 本轮对它的说法只能是**半截结果**（可见 13 ✔ / 0 ✘ / 0 △，其余不声称），三处注入锚点改用定向 grep 逐条确认；
④ **误判过一次"5 个写端点参数位置错了"**：把 `Api.post/put` 的 body/query 两个位置读反了（注释里本来就写着）。
⇒ 按源码更正、**未据此改过任何一行代码**；这条不计入"查出的缺陷"（㊷ ⑧"引用要连出处一起抄"的位置版）。
逐条细节与改法只记在 **D-064 §6**。

### ⑧ 三态

`SOURCE_COMPLETE` = 两族 Ack 别名撤读（客户端，**已进包**）＋ 站点可达性过滤（后端，**仅源码**）；
`BUILD_VERIFIED` = ⑥ 那一遍（⚠ lint 那一格是复用产物，按口径另计）；
**`DEVICE_VERIFIED = BLOCKED`**（闸门 rc=**1**、**0 台在环**）；`release` 仍 `BLOCKED`（HTTPS 闸门保持）；`GIT` 仍 `ENVIRONMENT_BLOCKED`（本机无仓库）。

---

## 84. 设备第一次在环说的三件事：句子会裸起句号、返回键会退掉 App、而我的解析器会造假缺陷 —— #270/#273/#274 设备轨道首轮真读数（2026-10-08 15:24 走查起手 → 15:39:47 登录闭环 → 15:53 强制重启复核 → 16:1x 证据落盘；台账 D-065 / §八 ㊹ / 证据 `docs/device-evidence/20261008-155801/`）

### ① 这一轮买到的是什么：一台在环的 x86_64 AVD

前八轮的 `DEVICE_STATUS` 全是 `DEVICE_UNAVAILABLE`（上一本证据簿里那行 adb 原文 `no devices/emulators found` 还留着）。
本轮 `adb devices -l` **rc=0、1 台在环**：`emulator-5554 / sdk_gphone64_x86_64 / SDK 35`，已装 `versionName=1.0.0-android`（minSdk 26 / targetSdk 34）。
⚠ 按用户口径：**只用本机 x86_64 模拟器**，不接共享开发机；设备包与交付包是两个东西（②）。

### ② 三个 APK 混在同一条路径上，读数不许互相顶名

| 包 | 字节 | sha256 前 8 | 关系 |
|---|---|---|---|
| 设备包 | **114,239,840** | `83dc930a` | 真机取证的那一个（备份 `D:\android-dev\dev\apk-device-20261008.apk`）|
| 被顶掉的前一份 | 114,239,931 | `77e441fe` | baseUrl 多写了 `/api` 前缀 ⇒ 网络全失败的那一包；文件已不在盘上，只剩日志里的字节数 |
| 交付包（D-064 ⑥） | 113,928,596 | `3bbf06f3` | **从未装到设备上** |

⇒ 本轮所有设备读数**只**归 `83dc930a`。把"真机验证过"写进交付包那一格，就是 §八 ㊷ ⑪ 那族"复用产物念成今天读数"的设备版。

### ③ 登录闭环：客户端进首页 ＋ 后端三张表同一秒落行

`send-sms-code{scene:1}` → 读 `system_sms_code` 拿验证码（**local profile 固定 `9999`**，不是 App 算的）→ `sms-login`。
App 侧进首页（截图 label 集就是首页），后端侧 `system_sms_code` **id=712** 于 15:39:33 发、15:39:47 `used=1`，
`system_login_log` **id=5523** 同秒 `result=0`，`system_oauth2_access_token` **id=108627** 同秒新增（`user_id=4`，30 分钟过期）。
上一条 **711**（15:24:36）仍 `used=0` ⇒ 这次出过网，不是本地状态。**这是本工程第一次前后端双向对上的登录。**

### ④ 逐屏走查读数（首页 / 四 tab / 收货地址 / 寄件三步 / 实时公交）

- 定位用 `cmd location providers` 注入合川 `30.03,106.30` 后，反编址 → 站点 → 线路 → 距离一起跟着变（`附近 2 个站点 / 1 条线路`），
  说明"定位→地址→附近公交"这条链在环上是通的；AVD 出厂 mock 点在加州（`39.237255,-123.150032`）。
- 寄件向导第 1 步渲染的是 **`可达性暂不可用 / 后端没有返回可达性判定（不代表车辆能到） / 请手动选择取货站点；本单不声称车辆可上门`**
  ⇒ honest-fallback 那一支在设备上工作（后端过滤那一半仍挂 **#278**，本轮没动后端）。
- 全量 logcat（5,224,729 B）里 App 侧 **FATAL EXCEPTION=0 / UnsatisfiedLinkError=0**。
  ⚠ 计数旁边要跟着归因：`NetworkException` 的 **4** 次属于 Google 的 `HeterodyneSyncer`，`AndroidRuntime` 的 **359** 次命中属于 uiautomator 启动横幅里的字符串 —— **都不是本 App 的崩溃，但也不是"0 命中所以没事"，是"命中了但不是它的"**。

### ⑤ 确认缺陷三处与补丁（三处都已改，两处在两个页面同形）

| # | 设备实测现象 | 补丁 |
|---|---|---|
| ① | 非运营时段副标题渲染成 **`。附近共 1 条公交线路`**（裸句号开头）| 首页与实时公交页那两格的多段副标题改成 `listOfNotNull(...).joinToString("；")`：**片段自己不带连接词**，缺字段就不进句子；顺带撤掉那处 `!!` |
| ② | 底栏在**商城/快递/我的**任一 tab 按系统返回，**三次全部直接退到桌面**（`mCurrentFocus=…nexuslauncher`）| 村民壳加 `BackHandler(enabled = selected != 0) { selected = 0 }`：非首页返回 = 回首页；首页返回仍退出（那是正确行为，不改） |
| ③ | 司机壳同形状（工作台/我的路线/我的收益/用户版）| 同形处理。**两个壳都用全限定名、调用插在函数体最末**：司机壳里那两行是在册锚点，加 `import` 会把它们整体下移一行并点亮 DA 闸（§八 ㊹ ④）|

### ⑥ 一条我自己读出来的假缺陷：宫格与底栏"对无障碍不可见"

第一遍走查报的是 `实时公交/我的快递/首页/商城 self.click=false ancestors_clickable=0`，据此我写下"底栏与宫格在 a11y 树里不可点"。
**这条判错了。** 用带父子深度的解析器重读同一份层级文件：**45** 个节点里 `clickable=true` 有 **12** 个 ——
每个 tile 是一个 `View(clickable=true)`，label 是它的子 `TextView`；底栏四个 tab 同样各有一个可点父节点（bounds 覆盖整格）。
⇒ 根因是**按"文本→节点→看该节点自己的 flag"匹配**：点击语义住在父节点上，扁平匹配必然找不到"既带这段文本又可点"的节点。这已经是本段第二次（第一次是"`附近公交` 是死控件"）。
**剩下的只有一条低度项**（挂 **#285**，不判红）：可点父节点自身 `text=''`/`desc=''`，标签没 merge 上去 ⇒ TalkBack 会先念"按钮"再单独念标签，影响**读的顺序**、不影响**点得到**。

### ⑦ 四条"看起来像缺陷、其实不是"（逐条写清为什么，免得下轮再查）

① `已更新：07:39` 不是时区缺陷 —— 当时 AVD 的 `persist.sys.timezone=GMT`；改时区后 **必须重启 App 进程**才生效（`SimpleDateFormat` 读的是进程启动时的默认时区），重启后同一格渲染 `已更新：15:52`。
② `约 10773.74 公里` 不是距离算错，也不是 null-island —— 那是 AVD 默认加州定位点的真实直线距离。
③ `附近公交` 标题不可点是**设计** —— 它是区块头；进实时公交页的入口是同一行右侧的 `更多`（实测跳到实时公交页）。
④ `/api` 前缀只属于共享开发机的 nginx；本地 jar 必须 `http://10.0.2.2:48080` 直连（`BuildConfig.BASE_URL` 实测就是这个串，`Api` 那边是 `baseUrl + path` 纯拼接）。上一条包 `77e441fe` 的网络全失败是**配置**病根，不是网络层缺陷。

### ⑧ 环境：`Unable to start the daemon process` 在这一台机器上是 commit charge 见底

补丁后第一次构建报的是 `FAILURE: Build failed … Unable to start the daemon process`，
而正文里那行 `os::commit_memory(…, 266338304, 0) failed; error='页面文件太小'`（**DOS errno 1455**）才是根因 ——
**同一串 JVM 参数 45 分钟前刚成功过**（`build-1528.log`，BUILD SUCCESSFUL in 50s）。
实测两个数：杀 Kotlin 编译守护进程（1,616 MB PM）⇒ FreeCommit **0.31 → 1.98 GB**；再优雅关掉模拟器（qemu **4,615 MB PM**）⇒ **4.98 GB**。
⇒ 规矩（§八 ㊹ ⑤）：**报构建失败先量 `FreeVirtualMemory`，再读日志**；这一台机器上的瓶颈一直是 commit charge，不是物理内存条。
另两条本轮实测的工具坑：Git Bash 里 `taskkill` 必须写 `//PID … //F`（单反斜杠被 MSYS 路径转换吞掉且**静默失败**）；`adb emu geo fix` 回 `OK` 而不改位置（**假成功**，必须回读 `dumpsys location`）。

### ⑨ 三态

`SOURCE_COMPLETE` = ⑤ 那三处补丁**已落源码**（含两处同形缺陷一起改）；
`BUILD_VERIFIED` = 本轮那一遍 `assembleDebug` 终值（见 D-065 的表，含字节 / sha256 / rc）；
`DEVICE_VERIFIED` = **PARTIAL** —— 前八轮这一格一直是 `BLOCKED`，本轮第一次有真读数（登录闭环、逐屏 label、honest-fallback、崩溃计数），
但**⑤ 那三处补丁还没上设备复拍** ⇒ 复拍完才能把这一格写成 PASS；`release` 仍 `BLOCKED`（HTTPS 闸门保持）；`GIT` 仍 `ENVIRONMENT_BLOCKED`。

## 85. 顶栏那句话是谁许可说的：三种定位形状各拍一张，以及"哪条注入命令生效"根本不是我能在台账里写死的事 —— #274 二次复拍收口（2026-10-08 16:3x 撤硬编码 → 17:14 重打包 → 17:2x 无 fix → 17:44 有 fix → 17:5x 服务范围外 ＋ 负控 134/134；台账 D-066 / §八 ㊺ / 证据 `docs/device-evidence/20261008-172000/`）

### ① 这一族的形状：三句话必须分得开
`位置未取到`（我没拿到 fix）／`东城区东华门街道`（我拿到了 fix、逆地理给了名字）／`暂无可选村庄：接口未返回站点`（我请求了、后端没给）。
这三句在前一轮是**混着的**：硬编码村名会让第一句变成第三句的假版本，无闸门的逆地理名会让第一句变成第二句的假版本。
⇒ 判据 `L-v / L-w / L-x` 三条、负控 5 条，全在 `tools/check_location_probes.sh` 与 `tools/navigation_negative_controls.sh` 里；本轮复跑 **12/12 rc=0**。

### ② 三处破口与它们各自的成因（**成因全是设备读数，不是源码推理**）
| 号 | 设备上看到的 | 生产者在哪 | 收法 |
|---|---|---|---|
| L-v | 定位打到加州，顶栏念 `云山村` | `Prefs.currentVillage` 默认值 ＋ 村庄选择器 `ifEmpty` 填充表（**两处**各一份硬编码） | 两处都撤；顶栏 `locationName.ifEmpty { village.ifEmpty { "位置未取到" } }`；基线 **1** = 只剩输入框 `placeholder = "如：云山村 3 组…"` |
| L-w | 无 fix 时顶栏念逆地理查出来的区县名 | `HomeScreen` 第 **172** 行的 `g ?: Geo(29.5325, 106.5765)` 是**查询坐垫子**，第 **174** 行原来无条件 `locationName = rg.displayName` | 闸门 `if (!locationManual && g != null)`；基线 **0**（只数不带闸门的赋值）|
| L-x | 撤掉填充表后点顶栏，对话框只剩标题＋"关闭" | `names` 为空时没人说话（第 **515–553** 行） | `Text("暂无可选村庄：接口未返回站点")`；基线 **1** |

### ③ 三种定位形状的设备读数（都挂 `8899e8b0` / **114,517,352 B**）
| 形状 | `dumpsys location` 回读 | 界面 | verdict |
|---|---|---|---|
| 无 fix（`location_mode 0`）| 无 fix | `位置未取到` ＋ `定位不可用，附近公交与天气暂按县城中心展示` ＋ 无任何硬编码村名 | **L-w PASS** |
| 有 fix・服务区内（合川 `30.03,106.30`）| `Location[gps 30.030498,106.300498 hAcc=5.0 …]`（**无 `mock` 标记**）| 顶栏 `合川区钓鱼城街道`、`附近共 1 条公交线路`、`25°C 阴 · 合川区钓鱼城街道`；选择器 **2 条** = `合川汽车客运中心 / 步步高公交站` | **L-x 正控 PASS** |
| 有 fix・服务范围外（北京 `39.90,116.40`）| `Location[gps 39.899998,116.400000 hAcc=5.0 …]` | 顶栏 `东城区东华门街道`、`附近共` 那一行不出现；选择器空态 `暂无可选村庄：接口未返回站点`、**条目 0** | **L-x 空态 PASS** |
| 上一轮两处补丁 | — | 副标题 `当前不在运营时间`／`附近共 1 条公交线路` 无裸 `。`；司机壳 `BACK` 从 我的路线/我的收益 ⇒ 回工作台 `True`、从工作台 ⇒ 出 App `in_app=False`（`3ab4ace0`）| **PASS** |

### ④ 后端真值把界面那两句钉成"真话"
无坐标 ⇒ `located:false` / `nearbyStations:[]` / `lineCount:0`；合川点 ⇒ 两站（`2245` 3.92 km、`2244` 5.57 km）＋ `lineCount:1`；北京点 ⇒ `located:true` 但 `nearbyStations:[]`。
⇒ 顺带**反证**：16:42 那次能出现"附近共 1 条公交线路"说明**当时 App 确实有 fix**，所以我先前那句"无 fix 时也会出现那句"是错的，而 dwalk12/13/14 拿不到读数只能归因到注入通路。

### ⑤ 本轮最重要的一条：**推翻 §84／D-065 的注入结论**
旧格写"`adb emu geo fix` 假成功，必须走 `cmd location providers` 那一族"。本轮实测反过来：
- `cmd location providers add-test-provider gps` 把真 GNSS provider 顶掉，本 boot 后期它自己滑成 `enabled=false / allowed=false / last location=null`，passive 里只剩 mock 默认点 `37.42,-122.16`（带 `mock`、`hAcc=100.0`）⇒ `Locator.usable()` 两条名字都空 ⇒ `onceFix` 回落 `UNAVAILABLE`。**这是环境，不是 App 缺陷。**
- 正解 = `remove-test-provider gps` ＋ `adb emu geo fix <lon> <lat>`，**且必须落在 App 正在 `requestLocationUpdates` 的那 9 秒窗口里**：`mStarted=false` / `ProviderRequest[OFF]` 时 HAL 把喂进去的点**静默丢掉**（dwalk18：20 次喂点、`et` 一个数没动）。
- 又抓到第二种"回 OK ≠ 改成了"：`geo fix` 的**负经度**（`-122.1600`）被 adb 当参数吃掉，回 `OK` 而位置不动。
- ⇒ 规矩：**注入是否生效只能由 `dumpsys location` 的回读判定**，回读要同时看点位、provider 的 `enabled/allowed/last location`、`mStarted`；台账里**不许**再写"必须用某一条命令"。

### ⑥ 一笔自纠的误归因
`reshoot-lx2/3/4.txt` 把空态读数标成"有 fix 但落在服务范围外（加州点）"，而同一行 `顶栏那一格='位置未取到'` 说明当时**根本没有 fix** ⇒ 真正测到的形状是"无 fix ⇒ 空态"。旧文件**一字不改**（它们是注入通路塌掉那三遍的过程证据），这一格由 ③ 第三行用真 fix ＋ 境内服务范围外的点重拍补齐。⇒ **场景标签必须由系统层回读支撑**，不能由"我注入了什么"支撑。

### ⑦ 终值与三态
构建 **BUILD SUCCESSFUL in 38s**、**43 tasks：8 executed / 35 up-to-date**（17:14）；APK **114,517,352 B** / `8899e8b0`；负控全批 **134 ✔ / 0 ✘ / 0 △**、rc=**0**、**3214 s**；判据 `L-*` **12/12 rc=0**。
`SOURCE_COMPLETE` ＋ `BUILD_VERIFIED` ＋ **`DEVICE_VERIFIED` 由 PARTIAL 升 PASS**（三种形状 ＋ 上一轮两处补丁都有读数，且每支配一条系统层回读）；`release` 仍 `BLOCKED`；`GIT` 仍 `ENVIRONMENT_BLOCKED`；**#245 那半格（`isFromMockProvider` 全 main 0 读取点）本轮再次演示了代价** —— 我能分辨某个点是不是模拟的，全靠 `dumpsys` 那个 `mock` 标记，App 自己从没看过这一位。

## 86. 一个输入框的"看得见"要拿像素证明，而不是拿源码证明 —— #283/#288/#289 三格改到改前改后成对读数（2026-10-08 18:0x 取证 → 18:27:35 重打包 → 18:53 装机回读 → 19:0x 落档；台账 D-067 / §八 ㊻ / 证据 `docs/device-evidence/20261008-181700/`）

### ① 这一轮和 §84/§85 的区别：证据的**级别**变了

前两轮的设备在环买到的是"句子对不对、返回键退到哪"。这一轮买的是**控件边界本身**：三格在源码里看都是"写了描边 / 写了灰字 / 写了居中"，而源码写了 ≠ 屏幕上存在。所以每一格都要求**同一个 x/y 带的改前读数与改后读数并列**，只有改后那一栏的写法在审计里不算数。

| 格 | 改前（设备像素） | 改后（设备像素） | 判据钉住的方向 |
|---|---|---|---|
| 输入框边界 | y=1342 只有 `(245,248,252)`×`(247,250,253)` = **1.02:1**，0 个边框段 | 同一行出现 `(199,213,224)` 的 3px 描边段；1.02:1 **未变** | 描边是**唯一**可视边界 ⇒ 只能 1 处（`FF-a`） |
| 禁用「登录」 | 板 `(155,191,226)`、界内最亮 `(200,220,239)` ⇒ **1.37:1** | 板 `(228,235,242)`、界内最暗 `(90,86,75)` ⇒ **6.09:1** | 灰板 + 灰字**两支齐**才不许红（`FF-g` = 1/1） |
| 切换登录方式 | `[53,1666][208,1772]` = **59.0×40.4dp**、中心 **x=130** | `[413,1685][667,1811]` = **96.8×48.0dp**、中心 **x=540 = 屏宽/2** | 没有宽度支撑的 `Arrangement.End` 不许回来（`FF-h` = 0） |

设备 `emulator-5554`，1080x2400，density 420 ⇒ **1dp = 2.625px**，dp 尺寸是把 px 边界除以 2.625 得到的，不是估的。

### ② 本轮最重要的一条：**"只取最暗像素"这个读法本身是坏的**

第一版对比脚本对禁用按钮只取 bounds 内**最暗**像素。改后那一版（灰字压灰板）读出来 6.09:1，看着没问题；可**改前那一版是白字比蓝板更亮**，最暗像素就是板自己 ⇒ "板 = 字"、ratio 恒 **1.00**，两版读数一样，这条判据**当场没有分辨力**。若我就抄了那个 1.00，台账会写成"禁用态只有 1.00:1，改后提升到 6.09:1"——**前半句是脚本的坏读法造出来的假事实**。
改法：界内最亮/最暗各取一次，**取离板色更远的那个**，两个 ratio 一起进日志。顺带另一处坏读法：用"既不是底也不是填充"粗筛边框，会把 x≈89..93 的**字形抗锯齿边缘**当成边框段 ⇒ 改成「x≥53 且既非 `paper` 也非 `field` 的**第一段**」。
⇒ **进 §八 ㊻**：这属于"目标值类读数要先证明这条读法能分辨改前改后"，和"目标 0 要反证"是同一条规矩的两个方向。

### ③ #288 的验收口径：一个出口、两支都拍到、且**互斥**

`LoginViewModel` 有 **4** 个 `notice =` 写入口，改前渲染点 **0** 个（发没发没人说）。改后 `FF-c = 1`，两支都有 bounds：成功支「验证码已发送」`[53,1829][239,1892]`；失败支「先填写 11 位手机号」`[53,1829][320,1892]`，而**同一帧**里「验证码已发送」命中 **0** 次。互斥这一条不是顺手记的：判据只数"渲染点 1 处"，若实现是"两处各渲染一半"，成功支也能读到字，但失败时会把两句一起念出来。

`FF-d`（自造时效）**0 处**的诚实性依据：后端 `expire-times` = **10m**，而 `Repo.sendSmsCode` 把响应体整个丢掉、客户端从未拿到过期时间。⇒ 屏幕上不能出现"5 分钟内有效"这种客户端自己编的承诺；负控第 4 条就是把这句话等回去，红给 `FF-d`。

### ④ #289 逐框聚焦：一条"共享状态也能过源码检查"的破口

聚焦手机号行后该行描边 **3px `(199,213,224)` → 6px `(46,123,191)`**，同屏**未**聚焦的验证码框仍是 3px。若把 `focused` 提成两个框共享的状态，源码里 `onFocusEvent` 的处数一样、判据 `FF-f` 不会红，但屏幕上两个框一起亮 ⇒ 用户分不清自己在哪个框里打字。⇒ **这一格只有设备读数能守**，`FF-f = 1` 只是它的下限。

### ⑤ 一次撤回：#282 不是缺陷，是我没去基准仓对原文

「`公交带货 · 快递进村 · 农产品出山`」和「`客货邮 · 城乡直达`」被我当成"同一位置两句话不一致"。本轮按 §八 既立的规矩去第三方真相源核对：`cargo-post-miniprogram/pages/index/index.wxml` 的 `top-sub` 与 `pages/login/login.wxml` 的 `hero-sub` 都是 **`客货邮 · 城乡直达`**，小程序分享标题是 **`咱村的客货邮——公交带货、快递进村、农产品出山`**，App 侧在 `core/ui/SplashOverlay.kt`。⇒ 两句**分别**服务"这是哪儿"和"这东西干什么"，各有基准出处，**0 行代码为它改动**，状态记 **WITHDRAWN**。这是"看着不一致先对原文"第三次（前两次见 §八 ㉘/㊺）——三次都是**同一类错误**：我拿自己的记忆当了契约来源。

### ⑥ 两本新簿：改的是**注入**，不是断言

`tools/check_form_field_probes.sh`（FF-a…FF-h，读数 `1 / 2 / 1 / 0 / 0 / 1 / 1/1 / 0`，rc=0）与 `tools/form_field_negative_controls.sh`（10 条破口，**✔ 10 / △ 0**，rc=0）。
第一版负控在 `SELF-MATCH` 闸门上崩（把回退写成 `t.lineDeep.copy(alpha = alphaMod)`，而 `alphaMod` 已不存在），报错的是**注入器不是判据** ⇒ 新增追加型注入器 `grow()`，把回退写成真实两步（先声明 `val alphaMod = if (dimmed) 0.45f else 1f`、再让板色套它）。第 1 条红名单是 **`FF-a,FF-b,`** 两条：删描边那一刀必然同时删掉第二个 `RoundedCornerShape(BusRadius.field)` —— 这是"删边框"这个回归本来的样子，**如实登记而不是放松判据凑单条红**。第 10 条不动源码、只把副本判据簿的 `[ "$FE" = 0 ]` 改成 `[ "$FE" = 9 ]`，用来反证"目标 0"那几格不是永不为红的摆设。

### ⑦ 终值与三态

构建 **BUILD SUCCESSFUL in 13s**、**37 actionable tasks：4 executed / 33 up-to-date**（只 `:app:assembleDebug`，与 §85 那 43 张任务图不是同一口径，两串数字不许互引）；APK **114,208,801 B** @ `2026-10-08 18:27:35` / sha256 **`d7d2d801…0703b`**；装机回读 `lastUpdateTime=2026-10-08 10:27:57` 而同一回读的 `date` = `18:53:58 CST` ⇒ **整 8 小时**（设备按 GMT 念），对账必须连 `date` 一起抄。判据面**全批 24/24 rc=0**；负控本轮**只有 2 本单本实测**（新簿 10 ✔ ＋ 跨簿相关 `throwable` 18 ✔），28 本全批**未跑**，台账按两行分开记。体积较上一包 **−308,551 B**，只动 3 个源文件文本、无新依赖新资源 ⇒ 登记 **「增量未归因」**，不写成优化也不写成回归。

`SOURCE_COMPLETE` ＋ `BUILD_VERIFIED` ＋ `DEVICE_VERIFIED`（#283/#288/#289 三格成对读数，每支配一条方法学自纠）；`release` 仍 `BLOCKED`；`GIT` 仍 `ENVIRONMENT_BLOCKED`；**新挂 #294** —— 红色 `error` 描边只在 `ui/driver/DriverExceptionScreen.kt` 有唯一非空 `error` 实参（`ui/` 内 `BusTextField(` 共 **29** 个调用点，只有这一格传），那一格的 danger 描边还只有源码证据、没有成对像素读数。旧账 **#140 / #159 / #245 / #278 / #279–#281 / #284** 原样在册；**#282 由 PENDING 改 WITHDRAWN**。TalkBack 朗读顺序仍只有形状证据。

## 87. "工具报的读数"不等于"缺陷"：布局走查第一次能下判决，以及一条判据读错了一层闸门 — #305／#313／#314（2026-10-09 00:1x 起手 → 02:43:47 交付包 → 05:01:18–05:05:32 判据全批 → 05:2x 落档；台账 D-068 ／ §八 ㊼ ／ 证据 `docs/device-evidence/20261009-020840/`）

### ① 这一轮买到的不是行数，是**判决能力**

前面几轮的设备在环买到的是"句子对不对、返回键退到哪"（§85）、"控件边界在不在"（§86）。本轮 `tools/layout_audit.py`（153 行 / 4,881 B）第一次把**一份 `uiautomator` dump 变成可判的三格**：屏外、兄弟文字重叠、触控 < 48dp（这台机器 420dpi ⇒ 1dp = 2.625px ⇒ 下限 **126 px**）。它报出两条可疑读数，**两条都去同一份 xml 的节点属性里对质**，于是一轮里同时得到两种结论：

| 工具那条读数 | 对质到的东西 | 判决 |
|---|---|---|
| "页首标题与副标题兄弟重叠"（`r68-08`） | 两个节点都是 `android.widget.TextView`、下面那个 `clickable=true`、PNG 上两行**明显分开** | **假报** ⇒ 是工具的读法局限（只看 bounds 相交）。**0 行代码为它改动**，工具本轮**不改**（改了就没有"工具也会报错"这条证据了） |
| 「旧导航页（调试）」入口那一格（`r68-15`） | `clickable="true"`、`text=""`、`content-desc=""`、`NAF="true"`、`bounds="[17,1993][83,2104]"` ⇒ **66×111 px = 25.1×42.3 dp** | **真缺陷** ⇒ 立 **#313**：两个维度都低于 48dp，且 TalkBack 念不出这是哪儿（标签住在邻近的兄弟文字里） |

⇒ 规矩（与 §六"0 命中要反证"同族）：**布局类读数必须先分清是"渲染坏了"还是"解析坏了"**，判据是节点自己的 `class` / `clickable` / `text` / `content-desc`，不是"两张 bounds 相交"。这一格和 §85 那条"读 a11y 树必须带父子深度"是同一条规矩的两次兑现。

### ② L-1 到近：把"说不说、说什么"从 Compose 里搬进 JVM 可测的纯层

三本新 main（`ui/driver/MissionApproach.kt` 134 / 5,793、`ApproachFeed.kt` 76 / 3,964、`ApproachAnnouncer.kt` 119 / 5,125）＋ 两本新 test（各 **11** 条 `@Test`）。分层是刻意的：**阈值与句子**在纯层（不 import `Context`、不碰 `TextToSpeech`，两本用例钉的就是这一层），**出声**只在出口层，所以出口层自己**不写任何阈值**。

三档声道逐档降级、每一档都回写屏上：系统 TTS ⇒（初始化失败 / 无中文语音数据 / `speak()` 返回失败）通知栏 `Channels.SHIFT` ⇒（通知也被系统关掉）只剩屏幕那一行、并明写"本机没出声：既没有可用语音，通知也被系统关着"。这句是真话的前提是 `areNotificationsEnabled()` 的读数 —— **不是"我以为会响"**。

判据侧新加两格：`NAC12` 钉导航卡片那一行点名"到近作业播报"、`NAC13` 钉「我的」里 `SettingRow` 的字面 —— 两边**逐字相等**才算点名，改一边就指错地方；两条注入各实测 `exit 1`。同轮 `NAC2`（页面调用开关话术的次数）由 **2 → 3**，脚本里写下增量出处（这一格是"恰好 N"的摩擦设计：加一行必须同轮把数改对）。

### ③ 本轮欠下的两笔，都写在自己新写的那本出口层里（登记 #314）

- `AnnounceChannel { TTS, NOTIFICATION, TEXT_ONLY }` 是**死声明**：`grep -rn 'AnnounceChannel' app/src` 只命中定义那一行、**0 个读取点**，而它的注释自己写着"屏上要说清楚"，屏上实际走的是 `_status` 的自由文本 ⇒ 声明与实现两套口径。
- 本机 TTS 与高德的内置语音是**两个引擎**，导航中两路声音会叠着说，**没有仲裁**；要仲裁得读高德的 `isTtsPlaying()`，而它在 x86_64 那侧至今没有设备证据（导航 `.so` 是 arm-only 那一族旧账）。

⚠ 两笔都**没有**在本轮"顺手改掉"：交付包 `86c8e8df` 与那 26 张设备读数挂在同一版源码上，删一行就要重打包并重对字节/dex 那一格。**这是权衡、不是遗漏**，所以写成挂账而不是写成已完成。第二条同样不许反过来写成"确认会叠"——**没测过**。

### ④ 一条判据读错了一层闸门（本轮最值钱的一条自纠）

`L-w`（不许拿县城垫子的逆地理结果当用户位置）本轮报 **1**，而代码是**拦住的**：`ui/user/HomeScreen.kt` 那句逆地理赋值靠的是**上层块**的空值闸门（`if (g == null) { … } else { … }`），旧判据只在**赋值行同一行**里找空值判断 ⇒ 合法形状被误读成破口。判据改成"赋值行**或**上层块都算"，这一改**是缩小判据面**，所以两边都得证：

| 形状 | 期望 | 实测 |
|---|---|---|
| 撤掉真闸门（块判断换成常量） | 红 | `exit 1` ✔ |
| 闸门只写在尾注释里、代码是常量条件 | 红 | `exit 1` ✔ |
| 闸门写在赋值行本身（旧写法，合法） | **不许红** | `exit 0` ✔ |
| 闸门原样在（证上面两条红不是判据恒红） | **不许红** | `exit 0` ✔ |

⇒ 规矩：**"同层任何一处有闸门就算"这种放宽，必须同时配一条"合法形状不红"的反证**，否则判据会悄悄退化成恒绿。另一笔同轮的坑记在 §八 ㊼：那两条"必须不红"的反证一开始被**驱动脚本的标签**念成了 `✘ 负控制失效`（旧 helper 只认 exit 1），实际是判据正确、标签错 —— 永久簿为此新增 `ctl_loc_keep`（期望 exit 0），把"两个方向"变成簿子自己会说的话。

### ⑤ 跨簿影响：这一轮我没有兑现上一轮的承诺（代价如实登记）

上一轮在 `AI_ITERATION_STATE.md` 的 `NEXT` 里写死"**下一次收工必跑负控全批 28 本**"。本轮只跑了**三本单簿 ＋ 一次 244 个去重锚点的存活审计**（`books=28` / 语料 **232** 个文件 / live **243** / DEAD **1**）。这条替代办法**能答**"我改的源码文本被哪几本负控当字面锚点"，**答不了**"某本簿注入面里的别的形状有没有被我连带打红" —— 所以台账里**没有出现**"负控全批绿"这句，补跑挂在 NEXT 第一条。那 **1** 条 DEAD（`verification_failure_negative_controls.sh` 里的 `DEVICE_UNAVAILABLE` 字面串）**只登记不修**：要么字面串该更新、要么注入器本来就造不出那句，两者修法不同，本轮没证据分辨。

### ⑥ 五本红判据簿的账（全部是本轮自己造的，无一格靠放松断言变绿）

`MR-i`（工作台重排把失败支条件扩宽、`reason` 改读后端那句 ⇒ 锚点按今天的形状重钉，四格目标仍 `1/1` 与 `1/1/1`）／ `NAC2`（**2→3**，同轮写下增量出处）／ `SU-b`（**53→55**，被本轮新建的两本 test 推动，与前例 `52→53` 同规格）／ `L-w`（判据读错一层闸门，见 §④）／ `DA`（**18** 处陈旧锚点顶成 新增 298 > 闸 286 ⇒ 逐条 grep 现读重锚，终值 新增 **0**／消失 **0**／`ID-MISS` 286 → **278**）。⚠ 在册表由 **262** 行收成 **252** 行，那 **10** 行的消失**本轮未逐条归因**（`D-068-3`）—— 只抄现读，不编解释。

### ⑦ 终值与三态

判据全批 **23** 本 / rc=0 **23** / red **0**（`C:/temp/r69_pass.log`，05:01:18 → 05:05:32；⚠ 这个 **23** 是 `ls tools/check_*probes.sh | wc -l` 的现读，与 §86 那句 `24/24` 用了不同的尺子，两个数**不互引**）。用例按 variant 分开：**debug 53 份 XML / 560 tests / failures 0**；**release 47 / 476 / failures 5**（`EarningsContractTest`、`MinutesContractTest`、`NearbyBooleanContractTest`、`NoProducerKeyFamilyContractTest`×2，D-054 那一族在册旧红）—— 那 5 条**没有**并进 debug 那句 0。交付包 **114,551,597 B** @02:43:47 / `86c8e8df…`；26 张设备读数挂在**联调包 114,551,589 B / `4fb22eec…`**（差 **8 B**，两个文件不是一个），两行**分开记**。体积对 D-067 交付 `d7d2d801` 114,208,801 B 的差 **+342,796 B** 登记为**「增量未归因」**（#140 / D-⑧-1 那一族，未闭）。三态：`SOURCE_COMPLETE` ＋ `BUILD_VERIFIED` ＋ **`DEVICE_VERIFIED` 部分**（本轮买到布局判决，**没有**买到"到近那句话在屏上响过"）；`release` 仍 `BLOCKED`。**NEXT**：① 补跑负控全批 28 本；② #313；③ #314；④ 旧账 #245 / #140 / #159 / #278–#281 / #283 / #284 / #296 / #299 / #304 / #306 / #307 原样。
