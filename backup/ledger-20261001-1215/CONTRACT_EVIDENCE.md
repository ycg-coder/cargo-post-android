# 契约证据台账（CONTRACT_EVIDENCE）

> F-2 第 16 步的落档物：逐字段记 **producer / consumer / 缺失时的行为**，以及判它的那条命令。
> 一切数字都是 2026-09-30 当轮实测，命令原样抄在下面，可复跑。
> 三方 = 后端 `cargo-post-platform`（Java VO 声明 + service 赋值）、小程序 `cargo-post-miniprogram`（读点）、
> 演示面 `app/src/main/java/com/shanxiang/buspost/data/repo/DemoData.kt`（写点）。
> 三面全无生产者而客户端仍读的键 ⇒ **第四个真相源**，按 F-2 第 2 步口径删除。

## 一、分钟五组别名（F-2 第 2 步，已收口）

判据簿：`tools/check_eta_probes.sh` 的 T-aj…T-aj6 + GUARD；负控：`tools/eta_negative_controls.sh`（✔=60 △=0 ✘=0）。

| 字段 | 后端 producer | 小程序 consumer | 被删候选名 | 删的依据（实测命令与结果） |
|---|---|---|---|---|
| `Station.plannedMinutes` | `AppDriverStationRespVO:17`、`RouteStationRespVO:32`、`AppBusLineRespVO:54` | `bus/detail.js`（4 处）、`goods/trace/trace.js`（2 处） | `minutes`、`offsetMinutes` | Java 只有两处同名**局部变量**；`offsetMinutes` 后端 0 文件、小程序 0 文件 |
| `BusVehicle.waitDepartureMinutes` | `AppBusRespVO:47`、`AppBusNearbyRespVO:126` | `bus/index.js:449`、`index/index.js:575` | `waitMinutes` | 那是小程序局部变量名（`const waitMinutes = b.waitDepartureMinutes`），后端 0 文件 |
| `TransitPlan.minutes` | `OrderTopologyRespVO.Candidate:65 durationMinutes`（列表键 `candidates:49`） | 小程序不消费 candidates | `totalMinutes`、`minutes` | 两个都 0 生产者；`totalMinutes` 此前只有 DemoData 在发 |
| `ShiftTask.plannedDurationMinutes` | `AppDriverShiftRespVO:24`、`ShiftBaseVO:18`、`MonitoringShiftRespVO:26` | `driver/routes.js`（3 处）、`workbench.js`（2 处） | `durationMinutes` | 后端有这名字但属**另一个对象**的另一种口径（`AppDriverLegRespVO:62` / `Leg:92` = 段时长） |
| `Reachability.walkMinutes` | `AppSendReachabilityRespVO:32`（service:142 赋值） | 小程序不消费；Android 侧 `SendScreen` 渲染 `walkText` | `minutes` | 0 生产者 |

信封级 `totalDurationMinutes:44` 与候选级 `durationMinutes:65` 是两个口径，**不许混**（同一文件内两处，别当同义词）。

## 二、寄货试算 QuoteResult 五字段（F-2 第 3 步，已收口）

判据簿：`tools/check_contract_defaults_probes.sh` Q-a…Q-f + GUARD；负控：`tools/contract_defaults_negative_controls.sh`（21 条，✔=21）。
后端：`AppSendQuoteRespVO.java:13-31`（amount/itemFee/distanceFee/distanceKm/itemCount/pricePerItem/pricePerKm），
逐字段 set 见 `AppSendController.java:498-515`，算法见 `CargoPricingService.java:45-75`
（**只有 `distanceKm` 可为 null**；缺坐标时 `distanceFee` 是真 `ZERO` = A 类；`count` null/≤0 由后端归一为 1）。

| 字段 | 旧缺失行为 | 现口径 | 被删别名 |
|---|---|---|---|
| `amount` | 缺键 → `0.0` → UI 显示"¥0"（把没算出来说成免费） | `Double?` + `amountText = "金额未给"` | `fee`、`totalPrice`、`unitFee`、`mileageFee`、`count`（三面 0 生产者） |
| `itemFee` / `distanceFee` / `itemCount` / `distanceKm` | 同上塌 0 | `Double?/Int?` | 同上 |
| 客户端件数兜底 | `fallbackCount` 参数 + `coerceAtLeast(1)` | **通道关闭**（后端已 normalize，客户端不许替它说话） | — |

额外破口（同轮修）：`Repo.quoteSendFee` 用 `p.obj ?: JSONObject()` 喂解析，信封形状意外时整屏"¥0"。
测试：`QuoteNullSafetyTest` 7 用例。

## 三、附近公交两个布尔（F-2 第 4 步，已收口）

| 字段 | 后端 producer | 小程序 consumer（三态读法） | 旧 Android | 现口径 |
|---|---|---|---|---|
| `NearbyResult.inService` | `AppBusNearbyRespVO.java:65 Boolean`；`AppBusServiceImpl.java:481/:532` | `bus/index.js:240` `data.inService != null ? … : null`，`:288/:291` 判 `=== false`/`=== true`；`index/index.js:539/:554` `inService === false ? 'off' : 'empty'` | `optBoolean("inService", true)` ⇒ 缺失说成"正在运营" | `Boolean?`；`isOffService = inService == false`（只有真给 false 才进非运营支） |
| `NearbyResult.located` | 同 VO `:45`；`AppBusServiceImpl.java:256` 按经纬度合法性 set | 同上族 | `optBoolean("located", true)` | `Boolean?` |
| `realTransit` | `realTransitAvailable`（AMap POI 合并标记） | — | 读 `"realTransitAvailable","realTransit"` | 只认真实键；`realTransit` 三面 0 生产者 |
| `Api.bool(key, def)` | — | — | 带默认值的助手，main+test 实测 **0 调用点** | 已删（它是下一个缺失默认值的入口） |

关键事实：`DemoData.kt` 的 `/bus/nearby` 报文（:611-620）**一个都不写**这两个键 ⇒ 旧默认值在**每一次演示运行**里都在说谎。
测试：`NearbyBooleanContractTest` 6 用例（MockWebServer 真信封）。

## 四、司机运营统计 earnings（F-2 第 5 步，本轮收口）

判据簿：`tools/check_default_value_probes.sh` DV-a…DV-l + 4 条 GUARD；负控：`tools/default_value_negative_controls.sh`（**✔=16 △=0 ✘=0**）。
分类全表与 A/B/C/D 口径：`docs/android/DEFAULT_VALUE_AUDIT.md`。

后端：`AppDriverEarningsRespVO.java:13-26`（`shiftCount/todayOrders/todayAmount/totalOrders/totalAmount/pendingCount/records`），
生产者 `DriverAppServiceImpl.java:365-391`；明细 `AppDriverEarningsRecordRespVO.java:14-26`
（`orderNo/goodsName/weightKg/totalAmount/status/statusName/createTime`），生产者 `toRecord:1846-1859`
—— **`goodsName/weightKg` 只在 `CargoOrderDO` 命中时才 set**，所以天然可缺席。
小程序：`pages/driver/earnings/earnings.js:52-69`，读 `r.totalAmount / r.weightKg / r.statusName / r.createTime /
e.todayAmount / e.totalAmount / e.totalOrders / e.shiftCount / e.pendingCount`。

| 字段/键 | 三面结论 | 旧缺失行为 | 现口径 |
|---|---|---|---|
| `today`/`total`/`orderCount`/`items` | 真实键为 `todayAmount/totalAmount/totalOrders/records`；`todayEarnings/totalEarnings/orderCount/items/details` **0 生产者** | 缺键塌 0 | `Double?/Int?`，只读真实键；`todayText="未给"` |
| `EarningItem.amount` | 真实键 `totalAmount`；`amount`/`fee` 0 生产者 | **真机每笔显示 ¥0** | `Double?` + `amountText="金额未给"` |
| `EarningItem.date` | 真实键 `createTime`（**EpochMillis**）；`date`/`day` 0 生产者 | 日期整列空、按日图并成一坨 | 由 `createTime` 派生 `MM-dd` |
| `EarningItem.weightKg` | 真实键，可缺席 | 0 kg | `Double?` + `weightText="重量未给"` |
| `week` / `month` / `totalWeight` | **后端全模块 grep 0 命中**、小程序不读、旧 DemoData 自造 452.0/1830.5/246.8 | 恒 0 ⇒ "本周 0 元" | 解析层不再读任何键，字段恒 null，UI 显示"未给 / 后端无此口径" |
| `EarningItem.type` | 0 生产者（`type`/`bizType`） | 恒空 | 不再读；字段保留（默认 ""） |
| 今日/累计单数口径 | `todayOrders`（:16）与 `totalOrders`（:20）是两个字段 | 徽标把 `totalOrders` 标成"今日 X 单" | 徽标改用 `todayOrders`，两口径不互串 |

时间口径证据：`YudaoJacksonAutoConfiguration.java:43,62` 把 `LocalDateTime` 注册成
`TimestampLocalDateTimeSerializer`（**LocalDateTime ↔ EpochMillis**），`Long` 走 `NumberSerializer`。
所以后端发的 `createTime` 是 13 位毫秒数；旧 `ts()` 只处理 ISO 串，会把 `1790558040000` 原样显示。
现 `ts()` 对 `length in 12..14` 的纯数字串按本机时区转 `yyyy-MM-dd HH:mm`，ISO 串分支保留（外部面仍发字符串）。
测试：`EarningsContractTest` 8 用例。

## 五、无 git 情况下怎么复核这些数字

本工作区**没有 git**（`GIT_REPOSITORY_UNAVAILABLE`，不许 `git init` 造假历史），所以逐字段结论的可复核性靠三件东西：
1. 本文件的 `文件:行号` 引用（对着源码读，不靠记忆）；
2. `tools/check_*.sh` 判据簿（exit 0/1，含 GUARD 正控制，0 命中必须能反证 grep 还数得动）；
3. `tools/*_negative_controls.sh`（逐条注入 ⇒ 确认每条判据真的能变红，而不是"永不为红"）。

设备侧一律 **NOT VERIFIED ON DEVICE**（`DEVICE_UNAVAILABLE`）。

## 六、幂等契约（F-2 第 6 步，本轮取证）

后端 `@Idempotent` **全仓命中 2 处**（`grep -rn "@Idempotent" --include=*.java` 排除 `/test/`，
第三处命中是 `.../idempotent/package-info.java:8` 的一句说明文字，**不是挂载点**）：

| 端点 | 注解落点 | 注解参数（原文） | Android 侧对应出口 |
|---|---|---|---|
| `POST /app-api/transport/product-order/create` | `AppProductOrderController.java:38` | `timeout = 10, timeUnit = SECONDS, keyResolver = UserIdempotentKeyResolver.class, message = "订单正在创建中，请勿重复提交"` | `Repo.createProductOrder`（`Repo.kt:506` 取 path → `:516` 进 `gatedUserCreate`） |
| `POST /app-api/transport/send/create` | `AppSendController.java:120` | 同上（逐字相同） | `Repo.createSendOrder`（`:558` → `:559`） |

| 契约要素 | 后端源码位置 | 实测内容 | 客户端照搬的是哪一半 |
|---|---|---|---|
| 键的**维度** | `UserIdempotentKeyResolver.java:20-25` | `SecureUtil.md5(方法签名 + 方法参数 + userId + userType)` | **维度照搬**：端点 + 登录身份 + 整份报文（`Repo.kt:464-476` 三段 `digest.update`，报文按 key 排序以消除遍历顺序） |
| 键的**值** | 同上 | `MD5(...)`，其中 `userId/userType` 取自**服务端会话**（`WebFrameworkUtils.getLoginUserId()/getLoginUserType()`） | **值不照搬、也无法照搬**：客户端拿不到服务端那两个表示，硬凑即是"假造 API"。⇒ **本机放行 ≠ 后端放行**，由下面那条 900 用例兜住 |
| 命中重复时回什么 | `IdempotentAspect.java:52` | `throw new ServiceException(GlobalErrorCodeConstants.REPEATED_REQUESTS.getCode(), idempotent.message())` ⇒ **HTTP 200 + `code=900` + 注解里那句 message** | `Api` 抛 `ApiException(900, msg)` → `Repo.kt:59 errorTextOf` 的 `is ApiException -> e.message` → `ScreenViewModel.kt:46` 原样进 `error`（**不换通用文案**） |
| 失败后键是否释放 | `Idempotent.java:61` | `deleteKeyWhenException() default true` | **同口径**：`ActionGate.submit` 的 `catch` 里 `heldUntil.remove(key)`（`ActionGate.kt:63-65`） |
| 成功后的窗口 | 两处注解 | `timeout = 10, TimeUnit.SECONDS` | `SUCCESS_HOLD_MS = 10_000L`（`ActionGate.kt:73`，V-1 已同口径） |

**刻意不加闸的两处**（同一条 grep 的可证伪部分）：
`AppAddressController.java`（`yudao-module-member/.../app/address/`）与 `AppFeedbackController.java`
（`yudao-module-transport/.../app/transport/feedback/`）的 create **零** `@Idempotent`
⇒ 客户端单方面拦它们＝替后端发明规则，所以 `U-j` 只做**登记**（用户域 create 端点总数 4，其中 2 处刻意裸奔），
不判红、**也不写负控**（能给"不判红"写负控的前提是让它 `exit 1`，而那正是不要的行为）。

**测试**：`UserCreateIdempotencyTest` 10 例。指令点名的两条是硬断言（连点 5 次 ⇒ `server.requestCount == 1`；
业务失败后立刻可重试 ⇒ `requestCount == 2`）；另外三条把上表的边界钉住 ——
`键只由三个维度决定且不含明文个人信息`（`^create:[0-9a-f]{64}$` + 姓名/手机号/地址/备注四段都不是子串 + `null ≠ ""` + 与 map 顺序无关）、
`后端回 900 时原样透出那句话且不再由本机代拦`、`用户域与司机域是两把闸不是一个实例`（反射数 `ActionGate` 类型字段）。
**未验证**：真机"手指划走页面"那条路径一律 `NOT VERIFIED ON DEVICE` —— `Api` 走 `Dispatchers.IO` 里的阻塞 `execute()`，
JVM 里没法确定性地"在请求在途时取消"，取消即放闸的语义只钉在 `ActionGateTest` 的"协程被取消不许泄闸"那条。

## 七、指令 ⑯ 点名字段的逐字段三方对照（2026-10-01 11:2x 补）

口径与 §一–§六 相同：**字段 / Android parser / 后端 producer / 正常样例 / 缺失样例 / 缺失处理 / 测试类**。
后端一侧本轮全部直读 `cargo-post-platform` 的 VO 声明与 service 赋值行（下面每条都带 `file:line`），
**没有**采信 `docs/testing/system-test-audit.md`——第七.9 节记了它与代码的三处冲突。
Android 一侧的读法收敛在两个 helper 族里：`Models.kt:37/51/60`（`multiStr/multiNum/multiBool`，缺键落 `""`/`0.0`/`false`）与
`Models.kt:75/85`（`multiNumOrNull/multiBoolOrNull`，**缺键与显式 `null` 同解为 `null`**）；信封族在 `Api.kt`（`Payload`）。
所以"缺失处理"这一列写的不是猜测，而是这两族之一的既定落点，加上渲染层的显式分支。

**本节的行号是量出来的，不是抄的**（11:5x 复核）：把 §七 里每条 `文件名:行号` 抽出来，在两个仓里解析后与磁盘当前内容逐条比对，
共 **218 条可解析锚点全部落在有效行内**（越界 0 条），其中 **9 处与实文不符、已就地改正**：
`AppDriverHandoverRespVO.java:63→:62`（remark）、`Models.kt:1272→:1273`（orderNo）、
`Models.kt:1275→` 该格的 `levelOf` 标签错指（`levelOf` 实为 `:1254-1261`，四个字段应为 `:1269`/`:1274`/`:1275`/`:1276`/`:1277`）、
`Models.kt:818→:816` 与 `:489→:488`（两处 distance 读点）、`Models.kt:1774-1779→:1774-1782`（quote 五键实际在 `:1775/1776/1777/1780/1781`）、
`Repo.kt:1166→:1165`（`NavStep.etaSeconds`，块是 `:1157-1166`）、`Repo.kt:86→:87`（`Ack.amount` 默认值，`:86` 是 `pickupCode`）、
以及 §七.3 那格里的 `:1273` 同批改正。
另有 **2 处不是行号错、是判断错**：七.5 曾把 `BusScreen.kt:289` 记成 `BusLine.distanceMeters` 的消费点（那一行读的是 `Station` 的，
且它的 `distanceKm` 候选有生产者，所以站点距离是真值），以及 `MultiLegPlanner.java:413` 是包级 `static` 而非"私有"——
两处的更正写在 D-⑯-5 里，不抹掉初版说法以免看不出这是修正。
剩下 2 条"解析不出"的是我的抽取器的误报，不是台账错：`.../idempotent/package-info.java:8`（省略号路径，实际存在且内容对）、
`SecureUtil.md5(...)`（正则把 `.md`+`5` 当成了 `文件:行号`）。

### 七.1 notification envelope

| 字段 | Android parser | 后端 producer | 正常样例 | 缺失样例 | 缺失处理 | 测试类 |
| --- | --- | --- | --- | --- | --- | --- |
| 列表本体 `data.list` | `Api.kt:160-165` `Payload.list`（`arr ?: obj.optJSONArray("list")`）→ `Repo.kt:852-859` `Feed(p.list.map{NotifyMsg.parse(it)}, p.demo, total = p.total)` | `AppNotificationController.java:33-42` 返回 `CommonResult<PageResult<…>>`；`PageResult.java:18 private List<T> list`（`total` 在 `:15`） | `{"list":[{"id":9,"readStatus":0,"content":"车已装车","eventType":"LOAD"}],"total":1}`（`NotificationWorkerResultTest.kt:82` 原样） | `{"code":0,"msg":"","data":null}` | `Api.kt:342-343` ⇒ 空 `Payload` ⇒ `list` 空集合、**不抛**、不回落演示数据；`Feed.error=null` 与 `Feed.cause=null`，结局是"没有新消息"而不是"失败" | `ApiTest.kt:22-41`（`assertNull(empty.arr)`）、`RepoTest.kt:53-61`（502 才是 error）、`NotificationWorkerResultTest.kt:78-102` |
| `data` 直接是数组（司机侧消息 / 交接 / 段列表） | 同上 `arr` 分支（`Api.kt:447-451` `toPayload`） | `AppDriverController.java:135-140`（handovers）、`:149-154`（legs）返回 `CommonResult<List<…>>`；`:236`（messages）返回 `PageResult` | `[{"id":91,"status":4,"statusName":"交接完成","photoUrl":"…"}]`（`ScalarEnvelopeContractTest.kt:198`） | `data: []` | 空集合，同上一条 | `ScalarEnvelopeContractTest.kt:198-202`、`DriverExceptionContractTest.kt:37` |
| `total` | `Api.kt:168` `obj?.optInt("total", list.size)` | `PageResult.java:15` | `:1` | 键不在 | **回落本页条数**（不是"后端总条数"），分页页脚因此可能偏小 | ⚠ 缺键面**无用例**（`ApiTest.kt:29` 只钉在场面）→ 见七.10 |
| `unread-count` 裸标量 | `Repo.kt:869-871` `longOf("count","unreadCount")` | `AppNotificationController.java:44-48` `CommonResult<Long>`（`data` 就是数字本体，没有键） | `{"code":0,"data":3}` | `data: null` | `Api.kt:132-146` ⇒ `null`（`Long?`），**与 0 分家**，UI 不显示"0 条未读" | `ScalarEnvelopeContractTest.kt:36-59` |
| `readStatus`（Integer 0/1，不是布尔） | `Models.kt:1270` `multiBool("readStatus","isRead","read") \|\| multiNum("readStatus") == 1.0` | `AppNotificationRespVO.java:31`（DO `:34`） | `"readStatus":0` | 键不在 | `false`（未读），是安全方向 | `NotificationWorkerResultTest.kt:82` 用的就是 `"readStatus":0` |
| `eventType` / `eventTypeName` / `level` / `actionRequired` | `Models.kt:1264`（多别名先取成 `evt`，再同时喂 `type:1269` 与 `eventType:1274`）、`:1275`（eventTypeName）、`:1276`（level）、`:1277`（actionRequired） | VO `:16` / `:19`（**由 controller `:40-41` 现算**，不来自 DB）/ `:34` / `:37` | `"eventType":"LOAD"` | 键不在 | 名 `""`；`level` ⇒ `MsgLevel.UNKNOWN`（`Models.kt:1254-1261`），不冒充普通级别 | `DomainSplitTest.kt:65/83/145` 覆盖空信封 |
| **`orderNo`** | `Models.kt:1273` `multiStr("orderNo","bizOrderNo","no")` | **PRODUCER_NOT_FOUND**：VO 字段清单 `AppNotificationRespVO.java:13-46` 内无此项；DO `TransportUserNotificationDO.java:15-39` 只有 `orderId/planId/legId/eventId`；`sql/mysql/transport-schema.sql:651-672` 无 `order_no` 列；admin `NotificationRespVO.java:10-38` 亦无。（对照：`/legs`、`/handovers` 是**显式多join 才产出** `orderNo` 的，`DriverAppServiceImpl.java:1615`/`:1647`） | — | 恒缺失 | ⇒ `""`；**渲染侧四处消费点全带 `isNotEmpty()` 闸门**（`NotificationScreen.kt:206`、`DriverMessagesScreen.kt:170` 与 `:222`、`AlertEngine.kt:74`/`:96`）⇒ 表现是"没有单号标签、不能点开轨迹"，**不是假单号** | 无用例（键本身不存在，无法造正常样例）→ 见七.10 |

### 七.2 handover photo

| 字段 | Android parser | 后端 producer | 正常样例 | 缺失样例 | 缺失处理 | 测试类 |
| --- | --- | --- | --- | --- | --- | --- |
| 上传返回的地址 | `Repo.kt:1319-1322` `p.strOf("url","fileUrl") ?: ""`，`strOf` 先试裸标量（`Api.kt:149` + `:125`） | `AppFileController.java:34-42` 返回 `CommonResult<String>` ⇒ `data` **就是 URL 字符串**，没有 `url` 键 | `data: "https://cdn.example.com/9/ab.jpg"` | `data: null` | `Api.kt:438-439` **抛** `NetworkException("上传未返回文件地址")`；`HandoverScreen.kt:155-158` 见到 `uploaded.isEmpty()` 就**不提交交接**并说"凭证照片上传失败，交接未提交" | `ScalarEnvelopeContractTest.kt:136-149`（裸串）、`:151-155`（对象形态）、`:164-169`（`data:null` 必须抛） |
| 提交体 `photoUrl` | `Repo.kt:1256-1264` 键名 `photoUrl` | `AppDriverHandoverConfirmReqVO.java:19 private String photoUrl;`（同类只有 `:12 driverId`/`:16 handoverId`/`:19`）；落库 `HandoverServiceImpl.java:193` 签名 `(handoverId, toDriverId, photoUrl)`，`:222-224` 仅 `isNotEmpty` 才覆盖 | `"photoUrl":"https://cdn.example.com/h/91.jpg"` | 空串 | `Api.post` 把空串字段整个丢掉（不是发 `""`）；后端 `:222-224` 也保留旧值 | `ScalarEnvelopeContractTest.kt:179-202`（链路）、`:209-220`（`!body.contains("photoUrl")` 且回读 `""`） |
| 回读 `photoUrl` | `Models.kt:1541` `multiStr("photoUrl","handoverPhotoUrl")`，声明 `:1518 val photoUrl: String = ""` | `AppDriverHandoverRespVO.java:50`，赋值 `DriverAppServiceImpl.java:1657` | 见上 `:198` 样例 | 键不在 / 显式 `null` | ⇒ `""` ⇒ `HandoverScreen.kt:344` 不画照片；`:169` 走"已提交，但回读里没有凭证照片" | `ScalarEnvelopeContractTest.kt:241-246`（`photoUrl:null` ⇒ `""`） |
| **`remark`（提交）** | `Repo.kt:1259` body 带 `remark`，输入框 `HandoverScreen.kt:269`（label "备注"） | **PRODUCER_NOT_FOUND**：请求 VO 无该字段（上面三条就是全部），`HandoverServiceImpl.java:193` 形参里也没有；无 `fail-on-unknown-properties` ⇒ **静默丢弃** | — | 恒丢弃 | 用户写的备注不会落库。当前**没有任何一处 UI 说它已保存**（回执文案只说照片与状态，`HandoverScreen.kt:165-173`），所以不是谎报，但是**收了的输入被吞** → 见七.10 缺陷 D-⑯-2 | `DriverActionIdempotencyContractTest` 只钉闸门，不覆盖 remark |
| `remark`（回读） | — | 反而是**有生产者**的：`AppDriverHandoverRespVO.java:62` ← `DriverAppServiceImpl.java:1661` | `"remark":"件数已清点"` | 键不在 ⇒ `""` | 读写两侧不对称：读得到别人填的，写不进去自己填的 | 无用例 |

### 七.3 leg status

| 字段 | Android parser | 后端 producer | 正常样例 | 缺失样例 | 缺失处理 | 测试类 |
| --- | --- | --- | --- | --- | --- | --- |
| `status`（Integer） | `Models.kt:1098` `if (o.has("status")) multiNum("status").toInt() else -1` | `AppDriverLegRespVO.java:38` ← `DriverAppServiceImpl.java:1621`；码表 `TransportLegStatusEnum.java:24-36`（`PLANNED(0)`…`COMPLETED(11)`、`EXCEPTION(99)`） | `"status":7` ⇒ 运输中 | 键不在 | ⇒ `-1` ⇒ `LegStatus.of()` 返回 `null`（`ServerStatus.kt:113`）⇒ `statusLabel="状态暂不可用"`（`:211`、`Models.kt:1060`）、tone NEUTRAL、`nextAction=null`（`DriverLegStateMapper.kt:108`）、`canReportException=false`（`:88`）、等待理由"后端没给段状态"（`:128-130`） | `DriverLegRespContractTest.kt:89-99`、`StatusMappingContractTest.kt:135-152`/`:88-95`、`DriverLegStateMapperTest.kt:129-135`/`:92`/`:176` |
| 同上 | 同上 | 同上 | `"status":12`（表外码） | — | 与缺键**同解**（`of()`⇒`null`），不回落 0、不自称完成 | `StatusMappingContractTest.kt:88-95`（`labelOf(12)`）、`:135-144`（`"status":88`） |
| **`status: null`** | 走 `has()` 真 ⇒ `multiNum` 跳过 `NULL` ⇒ **`0`** | 后端 `Integer` 未赋值时按 Jackson 默认**会发显式 `null`**（全仓无 `default-property-inclusion`） | — | `{"status":null}` | ⚠ **落进 `PLANNED(0)`：把"后端没给"说成"已规划"**。与键不在场（`-1`）不对称 | ⚠ 无用例 → 七.10 缺陷 D-⑯-1 |
| `statusName` | `Models.kt:1099` `multiStr("statusLabel","statusName","stateLabel")`，优先服务端文案（`Models.kt:1060`） | `AppDriverLegRespVO.java:41` ← `:1622` `TransportLegStatusEnum.nameOf(...)`；`nameOf` 对表外码返回 `""`（`:80-82`） | `"statusName":"运输中"` | 键不在 ⇒ `""` ⇒ 回落本地 label | 同左 | `DriverLegRespContractTest.kt:108-122` |
| 状态机可达性 | `DriverLegStateMapper.kt:38-52` 抄了一份 `TRANSITIONS` | `TransportLegStatusEnum.java:42-55`（`Map.ofEntries`），`:58 canTransit` | — | — | 表外码 ⇒ 不给动作 | `DriverLegStateMapperTest.kt:38-69` 逐条对齐后端表 |
| ⚠ 后端自相矛盾 | — | `AppDriverLegRespVO.java:37` 注释写 `"0待分配 1已分配 2运输中 3已到达 4已交接 5异常"`，与真正的生产者 `TransportLegStatusEnum.java:24-36` 在 2/3/4/5 上**全不一致**（2=司机已接单、7=运输中、9=交接中、5=已到达起点） | — | — | 端上按枚举、不按注释；谁照注释解码就全错 → 七.10 缺陷 D-⑯-3（后端文档） |

### 七.4 Minutes 家族

| 字段 | Android parser | 后端 producer | 正常样例 | 缺失样例 | 缺失处理 | 测试类 |
| --- | --- | --- | --- | --- | --- | --- |
| `etaMinutes`（公交） | `Models.kt:742` `multiNumOrNull(...)?.toInt()` ⇒ `Int?` | `AppBusRespVO.java:44` ← `AppBusServiceImpl.java:132`/`:134`；`AppBusNearbyRespVO.java:123` ← `:719/720/728/745/768` | `"etaMinutes":6` | 键不在 / `null` | `null` ⇒ `etaValueText="—"`、`etaCaptionText="到站时间未给"` | `BusEtaContractTest.kt:66-79`、`:220-232` |
| `waitDepartureMinutes` | `Models.kt:753` 同上 | `AppBusRespVO.java:47` ← `:136`；`AppBusNearbyRespVO.java:126` ← `:720` | `3` | 键不在 | `null`（不许塌 0） | `MinutesContractTest.kt:59-70`（含显式 `null` 与别名 `waitMinutes` 不再被认） |
| `etaToNextStationMinutes` | `Models.kt:757` **无别名**、保 `Double?` 不截断 | `AppBusRespVO.java:50` ← `AppBusServiceImpl.java:140`（`/realtime`）；**`/nearby` 的 `NearbyBus` 没有这个字段** ⇒ 该端点 PRODUCER_NOT_FOUND | `3.7` | nearby 报文里没有 | `null` ⇒ UI 退到 `etaMinutes` 或"到站时间未给"（`Models.kt:625-634`） | `BusEtaContractTest.kt:190-200` 正面钉住"nearby 没这键 ⇒ null" |
| `nearestStationEtaMinutes` | `Models.kt:762` | `AppBusNearbyRespVO.java:144` ← `:681`（`fillNearestStationEta:641`）；真名**不是** `nearestStationMinutes`（后者全仓 0 命中，含 `.py/.sql/.md/.ts/.js`） | `8` | 键不在 | `null` | `MinutesContractTest.kt:108-117` 同族口径 |
| `stopsToNearestStation` | `Models.kt:763` | `AppBusNearbyRespVO.java:147` ← `:667` `Math.max(0, index-nextIndex)` | `2` | 键不在 | `null` | 同上 |
| `plannedMinutes` | `Models.kt:500` `multiNum(...).toInt()` ⇒ **缺键 0**（非空字段） | `AppBusLineRespVO.java:54` ← `AppBusServiceImpl.java:188`；`AppDriverStationRespVO.java:17` ← `DriverAppServiceImpl.java:1911-1914`；`RouteStationRespVO.java:32`（admin） | `12` | 键不在 / 别名 `minutes`、`offsetMinutes` | **0**，且别名一律不认 | `MinutesContractTest.kt:42-55`（期望值就是 0） |
| `plannedDurationMinutes` | `Models.kt:1381` `multiNum ⇒ 0` | `AppDriverShiftRespVO.java:24` ← `DriverAppServiceImpl.java:229` | `55` | 别名 `durationMinutes` | `0` | `MinutesContractTest.kt:94-104` |
| `durationMinutes`（方案级） | `Models.kt:1159` `TransitPlan.minutes` 读 `durationMinutes`，⇒ `0` | `RoutePreviewRespVO.java:19` ← `AppSendRouteInfoService.java:87`/`:130` | `210` | 别名 `totalMinutes`（无生产者） | `0`；别名不认 | `MinutesContractTest.kt:74-90`（"totalMinutes 没有生产者"） |
| `walkMinutes` | `Models.kt:1920` `multiNumOrNull ⇒ null` | `AppSendReachabilityRespVO.java:32` | `12` | 键不在 / 别名 `minutes` | `null` ⇒ `walkText` 不带数字 | `MinutesContractTest.kt:108-117`、`ReachabilityContractTest.kt:138-155` |
| `roadEtaSeconds` / `NavStep.etaSeconds` | `Repo.kt:1111`、`Repo.kt:1165` 都是 `multiNum ⇒ 0`（`NavStep` 的映射块是 `:1157-1166`） | `AppDriverRouteRespVO.java:48 routeDurationSeconds`、`:78 nextStopEtaSeconds`、`:83 nextStopRoadEtaSeconds` | `routeDurationSeconds:600` | 键不在 | **0**（0 秒在路上与"未知"没分家）→ 七.10 缺陷 D-⑯-4 | 无用例 |

### 七.5 distance / duration（段里程与段时长）

| 字段 | Android parser | 后端 producer | 正常样例 | 缺失样例 | 缺失处理 | 测试类 |
| --- | --- | --- | --- | --- | --- | --- |
| `distanceKm`（司机段） | `Models.kt:1107` `multiNumOrNull`，**不给 `distance` 别名**（单位事故） | `AppDriverLegRespVO.java:59` ← `DriverAppServiceImpl.java:1628` | `12.4` | 键不在 / `null` / 给了 `distance:8000` | `null` ⇒ `legPlanText` 该项消失；两者都缺 ⇒ **整行不出**（`Models.kt:1072-1079`） | `DriverLegRespContractTest.kt:108-122`、`DistanceCaliberContractTest.kt:51-81` |
| `distanceKm`（用户段 `/send/legs`） | 同上 | **PRODUCER_NOT_FOUND**：`AppSendLegRespVO.java:13-37` 九个字段里没有 `distanceKm`/`durationMinutes`（DO 有：`TransportLegDO.java:40`/`:42`，但 `AppSendController.java:172-185` 没拷进 VO） | — | 恒缺失 | ⇒ `null` ⇒ 时间轴不写"本段 X km" | `DriverLegRespContractTest.kt:126-140` |
| `durationMinutes`（段） | `Models.kt:1108` ⇒ `null` | `AppDriverLegRespVO.java:62` ← `:1629`；`/send/legs` 无 | `35` | 键不在 | `null` | 同两行 |
| `distance` / `meters`（裸键） | `Models.kt:816` `BusLine.distanceMeters = multiNum("distance","meters")`（字段声明 `:799` 自陈"旧字段：契约里没有这两个键，只保留解析以兼容旧报文；界面一律改读 `distanceKm`"）、`Models.kt:488` `Station` 的米制候选 `distance/distanceM/meters` | **PRODUCER_NOT_FOUND**：整个 `yudao-module-transport` 里 `private … distance;` / `private … meters;` / `getDistance()` 三种形状**各 0 命中**（`grep -rEc` 实测 0/0/0；只剩 `MultiLegPlanner.java:413` 的包级 `static double distance(StationDO, StationDO)`——那是 Haversine 工具方法，不作为响应字段序列化） | — | 恒缺失 | `BusLine.distanceMeters` ⇒ **恒 0.0**，且本轮复核 `grep -rn '\.distanceMeters' app/src/main` 后**已无消费点**（线路页读 `line.distanceKm`，`BusScreen.kt:533-534` 缺键说"里程未给"）。`Station.distanceMeters` 的米制候选同样不命中，但 `:487` 的 `distanceKm` 候选**有生产者**（`AppBusNearbyRespVO.java:120`/`:138`/`:189`）⇒ `BusScreen.kt:289`/`:384`/`:389`、`HomeScreen.kt:388`、`SendScreen.kt:1154` 上的是公里×1000 换算的真值，**不是恒 0 的那一个** | `DistanceCaliberContractTest.kt:36-48`（钉的是"米制不许当公里读"，不是这条缺失面）→ 七.10 缺陷 D-⑯-5 |
| `routeDistanceMeters` / `nextStopRoadDistanceMeters` | `Repo.kt:1110`（别名表第一位就是真名）、`Repo.kt:1127` | `AppDriverRouteRespVO.java:45`、`:81` ← `DriverAppServiceImpl.java:521`/`:717` | `24600` | 键不在 | `0` | `RouteGuideContractTest`（覆盖来源判定，不覆盖该键缺面） |
| `distanceKm`（线路级） | `Models.kt:819` ⇒ `null` | `AppBusLineRespVO.java:28` ← `AppBusServiceImpl.java:179`；`NearbyBus:138` ← `:422` | `12.4` | 键不在 | `null` | `DistanceCaliberContractTest.kt:36-48` |

### 七.6 amount / itemFee / distanceFee

| 字段 | Android parser | 后端 producer | 正常样例 | 缺失样例 | 缺失处理 | 测试类 |
| --- | --- | --- | --- | --- | --- | --- |
| `amount` `itemFee` `distanceFee` `itemCount` `distanceKm` | `Models.kt:1774-1782` 五键全 `multiNumOrNull` ⇒ `null`（`:1775`/`:1776`/`:1777`/`:1780`/`:1781`） | `AppSendQuoteRespVO.java:13/16/19/22/25` ← `AppSendController.java:507-513` ← `CargoPricingService.java:53`（件数费）`:65`（里程费）`:69`（合计） | `{"amount":11.0,"itemFee":5.0,"distanceFee":6.0,"itemCount":2,"distanceKm":4.0}` | `{"itemFee":5.0,"distanceFee":6.0,"itemCount":2}` 或直接 `JSONObject()` | `"未给"` / `"金额未给"` / `"件数费未给 · 里程费未给（件数未给 · 计价里程未给）"`；`0.0` 是**真零**、与 `null` 分家（`Models.kt:1757-1758`） | `QuoteNullSafetyTest.kt:59-65`/`:69-75`/`:79-89`/`:93-101`/`:105-113`/`:117-122` |
| 别名 `fee`/`totalPrice`/`unitFee`/`mileageFee`/`count` | 已删（`Models.kt:1718-1727` 记录） | 这些名字在 quote VO 里都不存在 | — | — | 不再被认 ⇒ `null` | `QuoteNullSafetyTest.kt:79-89` |
| **`amount`（下单回执）** | `Repo.kt:537` `Ack(amount = multiNum("amount","totalPrice"))`、`Repo.kt:585` `multiNum("fee","amount")` | **键名错配 PRODUCER_NOT_FOUND**：创建/轨迹返回的是 `AppSendOrderRespVO.java:63 private BigDecimal totalAmount`（`AppProductOrderRespVO.java:19` 同名）；`amount`/`fee` 作为**响应键**都不存在 | `"totalAmount":11.00` | 恒不命中 | ⇒ `Ack.amount` **恒 0.0**（`Repo.kt:87` 默认值 `val amount: Double = 0.0`，同类的 `pickupCode` 在 `:86`）。今天没有消费点（`grep -rn "ack\.amount" app/src/main` → **0**），所以**还不是可见假数**；一旦有页面直接显示就是"¥0 收据" → 七.10 缺陷 D-⑯-6 | 无用例 |
| 列表侧订单金额 | `Models.kt:977` `SendOrder.fee = multiNum("fee","totalPrice","amount","totalAmount")` ⇒ 缺 `0.0`；`Models.kt:419` `ProductOrder.amount` 同族 | `totalAmount` ✓ 在场（见上） | `11.00` | 键不在 | `0.0` ⇒ `ParcelScreen.kt:447` 直接渲染 `¥0`；这是 §四 B/C→A 盘点里已登记的一类 | `EarningsContractTest.kt:112`/`:93`/`:143`（收益面已收口，寄货列表面未覆盖） |

### 七.7 inService / located

| 字段 | Android parser | 后端 producer | 正常样例 | 缺失样例 | 缺失处理 | 测试类 |
| --- | --- | --- | --- | --- | --- | --- |
| `inService` | `Repo.kt:817` `multiBoolOrNull("inService")` ⇒ `Boolean?`（`Models.kt:838`） | `AppBusNearbyRespVO.java:65` ← `AppBusServiceImpl.java:481`（有车 `STATUS_RUNNING`）否则 `:532`（当前时刻落在某班次窗口内），调用点 `:469 fillServiceWindow` | `"inService":false` | 键不在 / `null` | `null` ⇒ `isOffService = inService == false` 为 **false**（`Models.kt:863`）⇒ 走空态支而不是"非运营"支；只有真给 `false` 才允许说非运营 | `NearbyBooleanContractTest.kt:60-66`（缺键）/`:69-74`（显式 null）/`:52-57`（false 才进 off） |
| `located` | `Repo.kt:826` 同族 ⇒ `Boolean?`（`Models.kt:853`） | `AppBusNearbyRespVO.java:45` ← `AppBusServiceImpl.java:256`，判据在 `:254-255`（经纬度非空**且**在 ±90/±180 内） | `"located":true` | 键不在 / `null` | `null` ⇒ 当前**无 UI 消费者**（`Models.kt:848-852` 自陈"默认值本身就是谎言"）⇒ 省略而非声称定位成功 | `NearbyBooleanContractTest.kt:60-66`/`:69-74` |
| `realTransitAvailable` | `Repo.kt:816` `multiBool`（非空 ⇒ 缺 `false`），故意不给 `realTransit` 别名 | `AppBusNearbyRespVO.java:50` ← `:372` | `true` | 键不在 | `false`（保守方向） | `NearbyBooleanContractTest.kt:88-94`（别名不认） |
| `vehicles` vs `buses` | `Repo.kt:813` `multiArr("vehicles","buses")` | 后端产 **`buses`**：`AppBusNearbyRespVO.java:27` ← `AppBusServiceImpl.java:438`；`vehicles` 作为响应键只在 admin `DispatchValidateRespVO.java:16` 与**入站**请求 `AlgorithmPlanReqDTO.java:38` | `"buses":[…]` | 两个键都不在 | 空列表（`Models.kt:95-100` `multiArr`）；别名链第一位永不命中 ⇒ 实际靠第二位读到，**功能正确但用例写的是不存在的形状** → 七.10 缺陷 D-⑯-7 | `NearbyBooleanContractTest` 全部样例写 `"vehicles":[]` |

### 七.8 信封与序列化的两条全局口径（会影响"缺失样例"怎么造）

1. `CommonResult.java:27/33/37`（`code/msg/data`）＋ `PageResult.java:15/18`（`total/list`）⇒ 端上 `Api.kt:160-168` 的 `list`/`total`
   读法**只对分页三处成立**（`notification/page`、`driver/messages`、`send/page`）；`driver/handovers`、`driver/legs` 的 `data` 是裸数组。
2. 后端**没有**配 `default-property-inclusion`（`application.yaml` 内 `jackson` 段实测无该项）⇒ 声明了但没赋值的字段会以
   **显式 `null` 出现在 JSON 里**（键在场）。端上 `multiNumOrNull/multiBoolOrNull`（`Models.kt:75-93`）把"键不在"和"值为 null"
   **同解为 null**，这是七.3 表里 `status` 与 `inService` 两行"缺失处理"不同的根因：`inService` 用的是 `OrNull`，`status` 用的是 `has()`+`multiNum`。
3. `LocalDateTime` 序列化成 **epoch 毫秒数字**，不是 ISO 串（`YudaoJacksonAutoConfiguration.java:43` +
   `TimestampLocalDateTimeSerializer.java:60`），端上 `Models.kt:109-118 ts()` 就是按 12–14 位数字认的 ⇒ 两侧口径对得上。
4. 路径前缀由 `WebProperties.java:20`（`/app-api` ↔ `**.controller.app.**`）决定，端上 URL 常量（`Repo.kt:113` 的 `$T`）与之对齐。

### 七.9 后端接口文档与代码的冲突（所以本台账只认 VO + service 行号）

- `docs/testing/system-test-audit.md:121` 称 `buses[].lastLocationTime/updatedAt` 是 "string \| ISO"；代码发 epoch 数字（上一条）。
- 同文件 `:115/:116` 称取值含 `SIMULATED`；`grep -rn SIMULATED yudao-module-transport/src/main/java` → **0 命中**，实际常量是
  `AppBusNearbyRespVO.java:22 SOURCE_REAL="REAL"`、`:24 SOURCE_NONE="NONE"`。
- 同文件 `:108-126` 的 `/nearby` 字段表**漏列** `inService`、`realTransitAvailable`、`lines`、`lineCount`、`waitDepartureMinutes`、
  `stopsToNearestStation`、`nearestStationEtaMinutes`——七.4/七.7 已证明它们**都有生产者**。拿这张表判"无生产者"会判错。
- `docs/api/` 下只有 `algorithm-api.yaml`（算法服务），**没有** app-api 的 swagger 导出 ⇒ VO 是唯一契约源。

### 七.10 本节新增的 OPEN_DEFECT（本轮只登记，不动判据）

| 编号 | 缺陷 | 为什么这轮不修 |
| --- | --- | --- |
| D-⑯-1 | `/driver/legs` 的 `status: null` 落进 `PLANNED(0)`，与"键不在 ⇒ -1"不对称，且无测试 | 修法要给 `status` 换 `hasNonNull` 语义，会同时抬 `StatusMappingContractTest`/`DriverLegRespContractTest` 的期望面与 SM 判据目标；指令 ⑰⑱ 要求本轮只做验证与落档 |
| D-⑯-2 | 交接 `remark` 发出后被后端静默丢弃（写不进去、读得到别人的） | 要么改 UI 说明（撞 ⑰"不扩大 UI"），要么改后端（撞常驻指令"不要重新设计后端"）。留给用户裁决 |
| D-⑯-3 | `AppDriverLegRespVO.java:37` 的状态注释与真码表冲突 | 在后端仓，非本工程 |
| D-⑯-4 | `roadEtaSeconds`/`NavStep.etaSeconds`/`MissionStop.estimatedArrivalMillis` 三处 `multiNum ⇒ 0` 无缺键用例 | 同 D-⑯-1 的口径问题，需要先定"0 秒"算不算谎报 |
| D-⑯-5 | `BusLine.distanceMeters` 读的 `distance`/`meters` 两个键后端都不产 ⇒ 恒 0；本轮复核 `grep -rn '\.distanceMeters' app/src/main` 后**它已没有任何消费点**（线路页改读 `distanceKm`），是一个只保留解析的死字段 | 删字段撞常驻指令"不要删除字段以免破坏旧兼容"，留着就要在注释里钉死"恒 0 不代表真实里程"——两案都请用户裁决。⚠ **本节初版把 `BusScreen.kt:289` 记成它的消费点是错的**：那一行读的是 `Station.distanceMeters`（`Models.kt:487` 的 `distanceKm` 候选有生产者，所以站点距离是真值），已在七.5 改正 |
| D-⑯-6 | `Ack.amount` 读 `amount`/`fee`，后端产 `totalAmount` ⇒ 恒 0.0（目前无消费点） | 改读法要同时给 `Repo.kt:537`/`:585` 加用例，属第 ⑱ 步之后的补丁 |
| D-⑯-7 | `NearbyBooleanContractTest` 的样例写 `"vehicles"`，后端产 `"buses"` ⇒ 用例形状不等于线上形状 | 解析侧别名正确、功能不受影响；改样例属于测试数据修订，需同轮复跑 B-* 判据 |
| D-⑯-8 | `Payload.total` 缺键回落 `list.size`、`NotifyMsg` 单条缺键（`id⇒0`）都无用例 | 补用例是纯增量，下一轮做 |

**三态**：本节全部落在 `SOURCE_COMPLETE`（读源码与 VO 得出）。`BUILD_VERIFIED` 只覆盖已跑过的 9+14 例用例；
`DEVICE_VERIFIED` 一条都没有（⑪ 实测 0 台设备）——特别是七.2 的 `HandoverScreen.kt:165-173` 七种回执分支与七.3 的等待理由，
JVM 里从没渲染过，不许因为表格里有"缺失处理"这一列就读成设备上验过。
