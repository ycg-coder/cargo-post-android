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
| `POST /app-api/transport/product-order/create` | `AppProductOrderController.java:38` | `timeout = 10, timeUnit = SECONDS, keyResolver = UserIdempotentKeyResolver.class, message = "订单正在创建中，请勿重复提交"` | `Repo.createProductOrder`（`Repo.kt:498` 取 path → `:516` 进 `gatedUserCreate`） |
| `POST /app-api/transport/send/create` | `AppSendController.java:120` | 同上（逐字相同） | `Repo.createSendOrder`（`:558` → `:559`） |

| 契约要素 | 后端源码位置 | 实测内容 | 客户端照搬的是哪一半 |
|---|---|---|---|
| 键的**维度** | `UserIdempotentKeyResolver.java:20-25` | `SecureUtil.md5(方法签名 + 方法参数 + userId + userType)` | **维度照搬**：端点 + 登录身份 + 整份报文（`Repo.kt:456-468` 三段 `digest.update`，报文按 key 排序以消除遍历顺序） |
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
`AppDriverHandoverRespVO.java:63→:62`（remark）、`Models.kt:1275→:1273`（orderNo）、
`Models.kt:1454→` 该格的 `levelOf` 标签错指（`levelOf` 实为 `:1254-1261`，四个字段应为 `:1269`/`:1274`/`:1275`/`:1276`/`:1277`）、
`Models.kt:807→:816` 与 `:489→:488`（两处 distance 读点）、`Models.kt:1777-1782→:1774-1782`（quote 五键实际在 `:1775/1776/1777/1780/1781`）、
`Repo.kt:1202→:1165`（`NavStep.etaSeconds`，块是 `:1157-1166`）、`Repo.kt:86→:87`（`Ack.amount` 默认值，`:86` 是 `pickupCode`）、
以及 §七.3 那格里的 `:1273` 同批改正。
另有 **2 处不是行号错、是判断错**：七.5 曾把 `BusScreen.kt:299` 记成 `BusLine.distanceMeters` 的消费点（那一行读的是 `Station` 的，
且它的 `distanceKm` 候选有生产者，所以站点距离是真值），以及 `MultiLegPlanner.java:413` 是包级 `static` 而非"私有"——
两处的更正写在 D-⑯-5 里，不抹掉初版说法以免看不出这是修正。
剩下 2 条"解析不出"的是我的抽取器的误报，不是台账错：`.../idempotent/package-info.java:8`（省略号路径，实际存在且内容对）、
`SecureUtil.md5(...)`（正则把 `.md`+`5` 当成了 `文件:行号`）。

### 七.1 notification envelope

| 字段 | Android parser | 后端 producer | 正常样例 | 缺失样例 | 缺失处理 | 测试类 |
| --- | --- | --- | --- | --- | --- | --- |
| 列表本体 `data.list` | `Api.kt:160-165` `Payload.list`（`arr ?: obj.optJSONArray("list")`）→ `Repo.kt:844-851` `Feed(p.list.map{NotifyMsg.parse(it)}, p.demo, total = p.total)` | `AppNotificationController.java:33-42` 返回 `CommonResult<PageResult<…>>`；`PageResult.java:18 private List<T> list`（`total` 在 `:15`） | `{"list":[{"id":9,"readStatus":0,"content":"车已装车","eventType":"LOAD"}],"total":1}`（`NotificationWorkerResultTest.kt:82` 原样） | `{"code":0,"msg":"","data":null}` | `Api.kt:342-343` ⇒ 空 `Payload` ⇒ `list` 空集合、**不抛**、不回落演示数据；`Feed.error=null` 与 `Feed.cause=null`，结局是"没有新消息"而不是"失败" | `ApiTest.kt:22-41`（`assertNull(empty.arr)`）、`RepoTest.kt:53-61`（502 才是 error）、`NotificationWorkerResultTest.kt:78-102` |
| `data` 直接是数组（司机侧消息 / 交接 / 段列表） | 同上 `arr` 分支（`Api.kt:447-451` `toPayload`） | `AppDriverController.java:135-140`（handovers）、`:149-154`（legs）返回 `CommonResult<List<…>>`；`:236`（messages）返回 `PageResult` | `[{"id":91,"status":4,"statusName":"交接完成","photoUrl":"…"}]`（`ScalarEnvelopeContractTest.kt:198`） | `data: []` | 空集合，同上一条 | `ScalarEnvelopeContractTest.kt:198-202`、`DriverExceptionContractTest.kt:37` |
| `total` | `Api.kt:178` `obj?.optInt("total", list.size)` | `PageResult.java:15` | `:1` | 键不在 | **回落本页条数**（不是"后端总条数"），分页页脚因此可能偏小 | ⚠ 缺键面**无用例**（`ApiTest.kt:29` 只钉在场面）→ 见七.10 |
| `unread-count` 裸标量 | `Repo.kt:1038-1040` `longOf("count","unreadCount")` | `AppNotificationController.java:44-48` `CommonResult<Long>`（`data` 就是数字本体，没有键） | `{"code":0,"data":3}` | `data: null` | `Api.kt:132-146` ⇒ `null`（`Long?`），**与 0 分家**，UI 不显示"0 条未读" | `ScalarEnvelopeContractTest.kt:36-59` |
| `readStatus`（Integer 0/1，不是布尔） | `Models.kt:1470` `multiBool("readStatus","isRead","read") \|\| multiNum("readStatus") == 1.0` | `AppNotificationRespVO.java:31`（DO `:34`） | `"readStatus":0` | 键不在 | `false`（未读），是安全方向 | `NotificationWorkerResultTest.kt:82` 用的就是 `"readStatus":0` |
| `eventType` / `eventTypeName` / `level` / `actionRequired` | `Models.kt:1446`（多别名先取成 `evt`，再同时喂 `type:1269` 与 `eventType:1274`）、`:1275`（eventTypeName）、`:1276`（level）、`:1277`（actionRequired） | VO `:16` / `:19`（**由 controller `:40-41` 现算**，不来自 DB）/ `:34` / `:37` | `"eventType":"LOAD"` | 键不在 | 名 `""`；`level` ⇒ `MsgLevel.UNKNOWN`（`Models.kt:1243-1250`），不冒充普通级别 | `DomainSplitTest.kt:65/83/145` 覆盖空信封 |
| **`orderNo`** | `Models.kt:1473` `multiStr("orderNo","bizOrderNo","no")` | **PRODUCER_NOT_FOUND**：VO 字段清单 `AppNotificationRespVO.java:13-46` 内无此项；DO `TransportUserNotificationDO.java:15-39` 只有 `orderId/planId/legId/eventId`；`sql/mysql/transport-schema.sql:651-672` 无 `order_no` 列；admin `NotificationRespVO.java:10-38` 亦无。（对照：`/legs`、`/handovers` 是**显式多join 才产出** `orderNo` 的，`DriverAppServiceImpl.java:1615`/`:1647`） | — | 恒缺失 | ⇒ `""`；**渲染侧四处消费点全带 `isNotEmpty()` 闸门**（`NotificationScreen.kt:218`、`DriverMessagesScreen.kt:188` 与 `:222`、`AlertEngine.kt:75`/`:96`）⇒ 表现是"没有单号标签、不能点开轨迹"，**不是假单号** | 无用例（键本身不存在，无法造正常样例）→ 见七.10 |

### 七.2 handover photo

| 字段 | Android parser | 后端 producer | 正常样例 | 缺失样例 | 缺失处理 | 测试类 |
| --- | --- | --- | --- | --- | --- | --- |
| 上传返回的地址 | `Repo.kt:1316-1319` `p.strOf("url","fileUrl") ?: ""`，`strOf` 先试裸标量（`Api.kt:149` + `:125`） | `AppFileController.java:34-42` 返回 `CommonResult<String>` ⇒ `data` **就是 URL 字符串**，没有 `url` 键 | `data: "https://cdn.example.com/9/ab.jpg"` | `data: null` | `Api.kt:438-439` **抛** `NetworkException("上传未返回文件地址")`；`HandoverScreen.kt:155-158` 见到 `uploaded.isEmpty()` 就**不提交交接**并说"凭证照片上传失败，交接未提交" | `ScalarEnvelopeContractTest.kt:136-149`（裸串）、`:151-155`（对象形态）、`:164-169`（`data:null` 必须抛） |
| 提交体 `photoUrl` | `Repo.kt:1495-1506` 键名 `photoUrl` | `AppDriverHandoverConfirmReqVO.java:19 private String photoUrl;`（同类只有 `:12 driverId`/`:16 handoverId`/`:19`）；落库 `HandoverServiceImpl.java:193` 签名 `(handoverId, toDriverId, photoUrl)`，`:222-224` 仅 `isNotEmpty` 才覆盖 | `"photoUrl":"https://cdn.example.com/h/91.jpg"` | 空串 | `Api.post` 把空串字段整个丢掉（不是发 `""`）；后端 `:222-224` 也保留旧值 | `ScalarEnvelopeContractTest.kt:179-202`（链路）、`:209-220`（`!body.contains("photoUrl")` 且回读 `""`） |
| 回读 `photoUrl` | `Models.kt:1867` `multiStr("photoUrl","handoverPhotoUrl")`，声明 `:1518 val photoUrl: String = ""` | `AppDriverHandoverRespVO.java:50`，赋值 `DriverAppServiceImpl.java:1657` | 见上 `:198` 样例 | 键不在 / 显式 `null` | ⇒ `""` ⇒ `HandoverScreen.kt:344` 不画照片；`:169` 走"已提交，但回读里没有凭证照片" | `ScalarEnvelopeContractTest.kt:241-246`（`photoUrl:null` ⇒ `""`） |
| **`remark`（提交）** | `Repo.kt:1514` body 带 `remark`，输入框 `HandoverScreen.kt:269`（label "备注"） | **PRODUCER_NOT_FOUND**：请求 VO 无该字段（上面三条就是全部），`HandoverServiceImpl.java:193` 形参里也没有；无 `fail-on-unknown-properties` ⇒ **静默丢弃** | — | 恒丢弃 | 用户写的备注不会落库。当前**没有任何一处 UI 说它已保存**（回执文案只说照片与状态，`HandoverScreen.kt:165-173`），所以不是谎报，但是**收了的输入被吞** → 见七.10 缺陷 D-⑯-2 | `DriverActionIdempotencyContractTest` 只钉闸门，不覆盖 remark |
| `remark`（回读） | — | 反而是**有生产者**的：`AppDriverHandoverRespVO.java:62` ← `DriverAppServiceImpl.java:1661` | `"remark":"件数已清点"` | 键不在 ⇒ `""` | 读写两侧不对称：读得到别人填的，写不进去自己填的 | 无用例 |

### 七.3 leg status

| 字段 | Android parser | 后端 producer | 正常样例 | 缺失样例 | 缺失处理 | 测试类 |
| --- | --- | --- | --- | --- | --- | --- |
| `status`（Integer） | `Models.kt:1222` `if (o.has("status")) multiNum("status").toInt() else -1` | `AppDriverLegRespVO.java:38` ← `DriverAppServiceImpl.java:1621`；码表 `TransportLegStatusEnum.java:24-36`（`PLANNED(0)`…`COMPLETED(11)`、`EXCEPTION(99)`） | `"status":7` ⇒ 运输中 | 键不在 | ⇒ `-1` ⇒ `LegStatus.of()` 返回 `null`（`ServerStatus.kt:113`）⇒ `statusLabel="状态暂不可用"`（`:211`、`Models.kt:1049`）、tone NEUTRAL、`nextAction=null`（`DriverLegStateMapper.kt:108`）、`canReportException=false`（`:88`）、等待理由"后端没给段状态"（`:128-130`） | `DriverLegRespContractTest.kt:89-99`、`StatusMappingContractTest.kt:135-152`/`:88-95`、`DriverLegStateMapperTest.kt:129-135`/`:92`/`:176` |
| 同上 | 同上 | 同上 | `"status":12`（表外码） | — | 与缺键**同解**（`of()`⇒`null`），不回落 0、不自称完成 | `StatusMappingContractTest.kt:88-95`（`labelOf(12)`）、`:135-144`（`"status":88`） |
| **`status: null`** | 走 `has()` 真 ⇒ `multiNum` 跳过 `NULL` ⇒ **`0`** | 后端 `Integer` 未赋值时按 Jackson 默认**会发显式 `null`**（全仓无 `default-property-inclusion`） | — | `{"status":null}` | ⚠ **落进 `PLANNED(0)`：把"后端没给"说成"已规划"**。与键不在场（`-1`）不对称 | ⚠ 无用例 → 七.10 缺陷 D-⑯-1 |
| `statusName` | `Models.kt:1223` `multiStr("statusLabel","statusName","stateLabel")`，优先服务端文案（`Models.kt:1160`） | `AppDriverLegRespVO.java:41` ← `:1622` `TransportLegStatusEnum.nameOf(...)`；`nameOf` 对表外码返回 `""`（`:80-82`） | `"statusName":"运输中"` | 键不在 ⇒ `""` ⇒ 回落本地 label | 同左 | `DriverLegRespContractTest.kt:108-122` |
| 状态机可达性 | `DriverLegStateMapper.kt:38-52` 抄了一份 `TRANSITIONS` | `TransportLegStatusEnum.java:42-55`（`Map.ofEntries`），`:58 canTransit` | — | — | 表外码 ⇒ 不给动作 | `DriverLegStateMapperTest.kt:38-69` 逐条对齐后端表 |
| ⚠ 后端自相矛盾 | — | `AppDriverLegRespVO.java:37` 注释写 `"0待分配 1已分配 2运输中 3已到达 4已交接 5异常"`，与真正的生产者 `TransportLegStatusEnum.java:24-36` 在 2/3/4/5 上**全不一致**（2=司机已接单、7=运输中、9=交接中、5=已到达起点） | — | — | 端上按枚举、不按注释；谁照注释解码就全错 → 七.10 缺陷 D-⑯-3（后端文档） | 无用例（这是后端注释与枚举的分歧，端上解码已被 `DriverLegRespContractTest` 钉住） |

### 七.4 Minutes 家族

| 字段 | Android parser | 后端 producer | 正常样例 | 缺失样例 | 缺失处理 | 测试类 |
| --- | --- | --- | --- | --- | --- | --- |
| `etaMinutes`（公交） | `Models.kt:808` `multiNumOrNull(...)?.toInt()` ⇒ `Int?` | `AppBusRespVO.java:44` ← `AppBusServiceImpl.java:132`/`:134`；`AppBusNearbyRespVO.java:123` ← `:719/720/728/745/768` | `"etaMinutes":6` | 键不在 / `null` | `null` ⇒ `etaValueText="—"`、`etaCaptionText="到站时间未给"` | `BusEtaContractTest.kt:66-79`、`:220-232` |
| `waitDepartureMinutes` | `Models.kt:735` 同上 | `AppBusRespVO.java:47` ← `:136`；`AppBusNearbyRespVO.java:126` ← `:720` | `3` | 键不在 | `null`（不许塌 0） | `MinutesContractTest.kt:59-70`（含显式 `null` 与别名 `waitMinutes` 不再被认） |
| `etaToNextStationMinutes` | `Models.kt:823` **无别名**、保 `Double?` 不截断 | `AppBusRespVO.java:50` ← `AppBusServiceImpl.java:140`（`/realtime`）；**`/nearby` 的 `NearbyBus` 没有这个字段** ⇒ 该端点 PRODUCER_NOT_FOUND | `3.7` | nearby 报文里没有 | `null` ⇒ UI 退到 `etaMinutes` 或"到站时间未给"（`Models.kt:614-623`） | `BusEtaContractTest.kt:190-200` 正面钉住"nearby 没这键 ⇒ null" |
| `nearestStationEtaMinutes` | `Models.kt:828` | `AppBusNearbyRespVO.java:144` ← `:681`（`fillNearestStationEta:641`）；真名**不是** `nearestStationMinutes`（后者全仓 0 命中，含 `.py/.sql/.md/.ts/.js`） | `8` | 键不在 | `null` | `MinutesContractTest.kt:108-117` 同族口径 |
| `stopsToNearestStation` | `Models.kt:745` | `AppBusNearbyRespVO.java:147` ← `:667` `Math.max(0, index-nextIndex)` | `2` | 键不在 | `null` | 同上 |
| `plannedMinutes` | `Models.kt:539` `multiNum(...).toInt()` ⇒ **缺键 0**（非空字段） | `AppBusLineRespVO.java:54` ← `AppBusServiceImpl.java:188`；`AppDriverStationRespVO.java:17` ← `DriverAppServiceImpl.java:1911-1914`；`RouteStationRespVO.java:32`（admin） | `12` | 键不在 / 别名 `minutes`、`offsetMinutes` | **0**，且别名一律不认 | `MinutesContractTest.kt:42-55`（期望值就是 0） |
| `plannedDurationMinutes` | `Models.kt:1550` `multiNum ⇒ 0` | `AppDriverShiftRespVO.java:24` ← `DriverAppServiceImpl.java:229` | `55` | 别名 `durationMinutes` | `0` | `MinutesContractTest.kt:94-104` |
| `durationMinutes`（方案级） | `Models.kt:1182` `TransitPlan.minutes` 读 `durationMinutes`，⇒ `0` | `RoutePreviewRespVO.java:19` ← `AppSendRouteInfoService.java:87`/`:130` | `210` | 别名 `totalMinutes`（无生产者） | `0`；别名不认 | `MinutesContractTest.kt:74-90`（"totalMinutes 没有生产者"） |
| `walkMinutes` | `Models.kt:2231` `multiNumOrNull ⇒ null` | `AppSendReachabilityRespVO.java:32` | `12` | 键不在 / 别名 `minutes` | `null` ⇒ `walkText` 不带数字 | `MinutesContractTest.kt:108-117`、`ReachabilityContractTest.kt:138-155` |
| `roadEtaSeconds` / `NavStep.etaSeconds` | `Repo.kt:1236`、`Repo.kt:1157` 都是 `multiNum ⇒ 0`（`NavStep` 的映射块是 `:1157-1166`） | `AppDriverRouteRespVO.java:48 routeDurationSeconds`、`:78 nextStopEtaSeconds`、`:83 nextStopRoadEtaSeconds` | `routeDurationSeconds:600` | 键不在 | **0**（0 秒在路上与"未知"没分家）→ 七.10 缺陷 D-⑯-4 | 无用例 |

### 七.5 distance / duration（段里程与段时长）

| 字段 | Android parser | 后端 producer | 正常样例 | 缺失样例 | 缺失处理 | 测试类 |
| --- | --- | --- | --- | --- | --- | --- |
| `distanceKm`（司机段） | `Models.kt:1231` `multiNumOrNull`，**不给 `distance` 别名**（单位事故） | `AppDriverLegRespVO.java:59` ← `DriverAppServiceImpl.java:1628` | `12.4` | 键不在 / `null` / 给了 `distance:8000` | `null` ⇒ `legPlanText` 该项消失；两者都缺 ⇒ **整行不出**（`Models.kt:1061-1068`） | `DriverLegRespContractTest.kt:108-122`、`DistanceCaliberContractTest.kt:51-81` |
| `distanceKm`（用户段 `/send/legs`） | 同上 | **PRODUCER_NOT_FOUND**：`AppSendLegRespVO.java:62-83` 九个字段里没有 `distanceKm`/`durationMinutes`（DO 有：`TransportLegDO.java:40`/`:42`，但 `AppSendController.java:172-185` 没拷进 VO） | — | 恒缺失 | ⇒ `null` ⇒ 时间轴不写"本段 X km" | `DriverLegRespContractTest.kt:126-140` |
| `durationMinutes`（段） | `Models.kt:1194` ⇒ `null` | `AppDriverLegRespVO.java:62` ← `:1629`；`/send/legs` 无 | `35` | 键不在 | `null` | 同两行 |
| `distance` / `meters`（裸键） | `Models.kt:843` `BusLine.distanceMeters = multiNum("distance","meters")`（字段声明 `:799` 自陈"旧字段：契约里没有这两个键，只保留解析以兼容旧报文；界面一律改读 `distanceKm`"）、`Models.kt:474` `Station` 的米制候选 `distance/distanceM/meters` | **PRODUCER_NOT_FOUND**：整个 `yudao-module-transport` 里 `private … distance;` / `private … meters;` / `getDistance()` 三种形状**各 0 命中**（`grep -rEc` 实测 0/0/0；只剩 `MultiLegPlanner.java:413` 的包级 `static double distance(StationDO, StationDO)`——那是 Haversine 工具方法，不作为响应字段序列化） | — | 恒缺失 | `BusLine.distanceMeters` ⇒ **恒 0.0**，且本轮复核 `grep -rn '\.distanceMeters' app/src/main` 后**已无消费点**（线路页读 `line.distanceKm`，`BusScreen.kt:591-592` 缺键说"里程未给"）。`Station.distanceMeters` 的米制候选同样不命中，但 `:487` 的 `distanceKm` 候选**有生产者**（`AppBusNearbyRespVO.java:120`/`:138`/`:189`）⇒ `BusScreen.kt:299`/`:384`/`:389`、`HomeScreen.kt:478`、`SendScreen.kt:1154` 上的是公里×1000 换算的真值，**不是恒 0 的那一个** | `DistanceCaliberContractTest.kt:36-48`（钉的是"米制不许当公里读"，不是这条缺失面）→ 七.10 缺陷 D-⑯-5 |
| `routeDistanceMeters` / `nextStopRoadDistanceMeters` | `Repo.kt:1276`（别名表第一位就是真名）、`Repo.kt:1119` | `AppDriverRouteRespVO.java:45`、`:81` ← `DriverAppServiceImpl.java:521`/`:717` | `24600` | 键不在 | `0` | `RouteGuideContractTest`（覆盖来源判定，不覆盖该键缺面） |
| `distanceKm`（线路级） | `Models.kt:888` ⇒ `null` | `AppBusLineRespVO.java:28` ← `AppBusServiceImpl.java:179`；`NearbyBus:138` ← `:422` | `12.4` | 键不在 | `null` | `DistanceCaliberContractTest.kt:36-48` |

### 七.6 amount / itemFee / distanceFee

| 字段 | Android parser | 后端 producer | 正常样例 | 缺失样例 | 缺失处理 | 测试类 |
| --- | --- | --- | --- | --- | --- | --- |
| `amount` `itemFee` `distanceFee` `itemCount` `distanceKm` | `Models.kt:1763-1771` 五键全 `multiNumOrNull` ⇒ `null`（`:1775`/`:1776`/`:1777`/`:1780`/`:1781`） | `AppSendQuoteRespVO.java:13/16/19/22/25` ← `AppSendController.java:507-513` ← `CargoPricingService.java:53`（件数费）`:65`（里程费）`:69`（合计） | `{"amount":11.0,"itemFee":5.0,"distanceFee":6.0,"itemCount":2,"distanceKm":4.0}` | `{"itemFee":5.0,"distanceFee":6.0,"itemCount":2}` 或直接 `JSONObject()` | `"未给"` / `"金额未给"` / `"件数费未给 · 里程费未给（件数未给 · 计价里程未给）"`；`0.0` 是**真零**、与 `null` 分家（`Models.kt:1746-1747`） | `QuoteNullSafetyTest.kt:59-65`/`:69-75`/`:79-89`/`:93-101`/`:105-113`/`:117-122` |
| 别名 `fee`/`totalPrice`/`unitFee`/`mileageFee`/`count` | 已删（`Models.kt:1721-1730` 记录） | 这些名字在 quote VO 里都不存在 | — | — | 不再被认 ⇒ `null` | `QuoteNullSafetyTest.kt:79-89` |
| **`amount`（下单回执）** | `Repo.kt:646` `Ack(amount = o.multiNum("totalAmount"))`（寄货下单）、`Repo.kt:524` 商城下单回执**不取**金额（`AppProductOrderCreateRespVO` 只有 id/orderNo） | **键名已对齐**（㊻ 2026-10-09 实测：客户端改读 `totalAmount`，`amount`/`fee` 两个响应键**已不再被读**）；创建/轨迹返回的是 `AppSendOrderRespVO.java:63 private BigDecimal totalAmount`（`AppProductOrderRespVO.java:19` 同名）；`amount`/`fee` 作为**响应键**都不存在 | `"totalAmount":11.00` | 键不在 | ⇒ 键对上了，但缺键仍由 `multiNum` 塌 **0.0**（`Repo.kt:87` 默认值 `val amount: Double = 0.0`，同类的 `pickupCode` 在 `:86`）。今天没有消费点（`grep -rn "ack\.amount" app/src/main` → **0**），所以**还不是可见假数**；一旦有页面直接显示就是"¥0 收据" → 七.10 缺陷 D-⑯-6 | 无用例 |
| 列表侧订单金额 | `Models.kt:966` `SendOrder.fee = multiNum("fee","totalPrice","amount","totalAmount")` ⇒ 缺 `0.0`；`Models.kt:405` `ProductOrder.amount` 同族 | `totalAmount` ✓ 在场（见上） | `11.00` | 键不在 | `0.0` ⇒ `ParcelScreen.kt:447` 直接渲染 `¥0`；这是 §四 B/C→A 盘点里已登记的一类 | `EarningsContractTest.kt:112`/`:93`/`:143`（收益面已收口，寄货列表面未覆盖） |

### 七.7 inService / located

| 字段 | Android parser | 后端 producer | 正常样例 | 缺失样例 | 缺失处理 | 测试类 |
| --- | --- | --- | --- | --- | --- | --- |
| `inService` | `Repo.kt:985` `multiBoolOrNull("inService")` ⇒ `Boolean?`（`Models.kt:827`） | `AppBusNearbyRespVO.java:65` ← `AppBusServiceImpl.java:481`（有车 `STATUS_RUNNING`）否则 `:532`（当前时刻落在某班次窗口内），调用点 `:469 fillServiceWindow` | `"inService":false` | 键不在 / `null` | `null` ⇒ `isOffService = inService == false` 为 **false**（`Models.kt:852`）⇒ 走空态支而不是"非运营"支；只有真给 `false` 才允许说非运营 | `NearbyBooleanContractTest.kt:60-66`（缺键）/`:69-74`（显式 null）/`:52-57`（false 才进 off） |
| `located` | `Repo.kt:994` 同族 ⇒ `Boolean?`（`Models.kt:842`） | `AppBusNearbyRespVO.java:45` ← `AppBusServiceImpl.java:256`，判据在 `:254-255`（经纬度非空**且**在 ±90/±180 内） | `"located":true` | 键不在 / `null` | `null` ⇒ 当前**无 UI 消费者**（`Models.kt:837-841` 自陈"默认值本身就是谎言"）⇒ 省略而非声称定位成功 | `NearbyBooleanContractTest.kt:60-66`/`:69-74` |
| `realTransitAvailable` | `Repo.kt:984` `multiBool`（非空 ⇒ 缺 `false`），故意不给 `realTransit` 别名 | `AppBusNearbyRespVO.java:50` ← `:372` | `true` | 键不在 | `false`（保守方向） | `NearbyBooleanContractTest.kt:88-94`（别名不认） |
| `vehicles` vs `buses` | `Repo.kt:969` `multiArr("vehicles","buses")` | 后端产 **`buses`**：`AppBusNearbyRespVO.java:27` ← `AppBusServiceImpl.java:438`；`vehicles` 作为响应键只在 admin `DispatchValidateRespVO.java:16` 与**入站**请求 `AlgorithmPlanReqDTO.java:38` | `"buses":[…]` | 两个键都不在 | 空列表（`Models.kt:95-100` `multiArr`）；别名链第一位永不命中 ⇒ 实际靠第二位读到，**功能正确但用例写的是不存在的形状** → 七.10 缺陷 D-⑯-7 | `NearbyBooleanContractTest` 全部样例写 `"vehicles":[]` |

### 七.8 信封与序列化的两条全局口径（会影响"缺失样例"怎么造）

1. `CommonResult.java:27/33/37`（`code/msg/data`）＋ `PageResult.java:15/18`（`total/list`）⇒ 端上 `Api.kt:173-181` 的 `list`/`total`
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
| D-⑯-5 | `BusLine.distanceMeters` 读的 `distance`/`meters` 两个键后端都不产 ⇒ 恒 0；本轮复核 `grep -rn '\.distanceMeters' app/src/main` 后**它已没有任何消费点**（线路页改读 `distanceKm`），是一个只保留解析的死字段 | 删字段撞常驻指令"不要删除字段以免破坏旧兼容"，留着就要在注释里钉死"恒 0 不代表真实里程"——两案都请用户裁决。⚠ **本节初版把 `BusScreen.kt:299` 记成它的消费点是错的**：那一行读的是 `Station.distanceMeters`（`Models.kt:473` 的 `distanceKm` 候选有生产者，所以站点距离是真值），已在七.5 改正 |
| D-⑯-6 | `Ack.amount` 的**键名错配**已收（㊻ 实测改读真键 `totalAmount`，`Repo.kt:572`），剩两半：缺键仍塌 0.0（`multiNum` 非 `multiNumOrNull`）、且目前无消费点 | 补用例要在 `Repo.kt:572` 的缺键形状上定"0 元收据"算不算谎报（与 D-⑯-1 同口径），属第 ⑱ 步之后的补丁 |
| D-⑯-7 | `NearbyBooleanContractTest` 的样例写 `"vehicles"`，后端产 `"buses"` ⇒ 用例形状不等于线上形状 | 解析侧别名正确、功能不受影响；改样例属于测试数据修订，需同轮复跑 B-* 判据 |
| D-⑯-8 | `Payload.total` 缺键回落 `list.size`、`NotifyMsg` 单条缺键（`id⇒0`）都无用例 | 补用例是纯增量，下一轮做 |

**三态**：本节全部落在 `SOURCE_COMPLETE`（读源码与 VO 得出）。`BUILD_VERIFIED` 只覆盖已跑过的 9+14 例用例；
`DEVICE_VERIFIED` 一条都没有（⑪ 实测 0 台设备）——特别是七.2 的 `HandoverScreen.kt:165-173` 七种回执分支与七.3 的等待理由，
JVM 里从没渲染过，不许因为表格里有"缺失处理"这一列就读成设备上验过。

## 八、车辆位置 / 上报时间 / 当前任务（F-3 ⑩⑪⑮，2026-10-01 14:4x 量）

同一口径（字段 / Android parser / 后端 producer / 正常样例 / 缺失样例 / 缺失处理 / 测试类）。
本节的所有 `文件:行号` 都是**本轮改完源码之后重新量的**：`tools/check_doc_anchors.py` 跑全文（含本节）实测
`anchors=289 out_of_range=0 not_found=3 miss=0`。这 3 条 `not_found` 不是新缺陷，全是台账自己写下的
`.../idempotent/package-info.java:8` 省略号路径 —— 抽取器把省略号当真实文件名，且**声明这件事的这句话本身也被扫成第 3 条**，
所以每次复述计数就会 +1；实文件存在、内容对。DUP（同名文件多义）0 条。

根因先记清楚：这三格原来不是"后端没给"，而是**客户端一行都没读**——寄货侧的 `carrier*`、商城侧的 `current*`、
作业侧的 `carrierTaskStatus/carrierArrivedTime/carrierArrived` 在 `traceOf` 里全部零引用，页面却把"我没读"渲染成"它没有"。

| 字段 | Android parser | 后端 producer | 正常样例 | 缺失样例 | 缺失处理 | 测试类 |
| --- | --- | --- | --- | --- | --- | --- |
| `carrierLongitude` / `carrierLatitude`（寄货轨迹的承运车辆位置） | `Repo.kt:1400` `geoOf()`：候选 `carrierLatitude,currentLatitude,latitude,lat,vehicleLatitude`（经度同族），**`lat!=0 && lng!=0` 才建 `Geo`**；消费点 `Repo.kt:865`（`vehicleGeo = geo`） | `AppSendOrderRespVO.java:100`/`:102` ← `AppSendController.java:475`/`:476`（在 `fillCarrierLiveInfo`，`:439`；由 `:262` 详情、`:371` 列表两处调用） | `{"carrierLatitude":30.5,"carrierLongitude":106.5}` | 键不在 / 显式 `null` / 两个都 `0.0` | ⇒ `Geo = null` ⇒ `TraceView.kt:122-130` 出**"暂无实时位置（后端未下发承运车辆坐标）"**；不许用 `0,0`、固定点、上一秒的点或随机点顶替 | `UserTrackingContractTest.kt:46`（读得到）、`:70`（缺键⇒null 且 `locationSource=UNKNOWN`）、`:93`（显式 0 不许画成一个真点） |
| `currentLongitude` / `currentLatitude`（商城订单轨迹的车辆位置） | 同一个 `geoOf`（`:1174`），候选键里 `currentLatitude`/`currentLongitude` 排第 2 位 | `AppProductOrderTraceRespVO.java:68`/`:70` ← `ProductOrderServiceImpl.java:377`/`:378`（端点 `AppProductOrderController.java:69`）；司机侧另有 `AppDriverRouteRespVO.java:59`/`:61` ← `DriverAppServiceImpl.java:510`/`:511` | `{"currentLatitude":29.55,"currentLongitude":106.57}` | 键不在 | 同上（`null` ⇒ "暂无实时位置"） | `UserTrackingContractTest.kt:137`（商城读得到车）、`:148`（商城没有新鲜度键 ⇒ 位置读得到也不许说"实时"） |
| `servicePointLongitude` / `servicePointLatitude` | 不在 `geoOf` 的候选里（这是**站点**坐标，不是车辆） | `AppSendOrderRespVO.java:46`/`:48` | 有 | — | 站点坐标绝不冒充车辆位置 | `UserTrackingContractTest.kt:84` |
| `carrierLocationSource`（新鲜度） | `Repo.kt:875` → `LocationSource.of`（`Models.kt:543`，唯一解析点），落 `ParcelTrace.locationSource`（`:1205`，默认 `UNKNOWN`）；文案只从 `knownLabel`（`:543-552`）出 | `AppSendOrderRespVO.java:108` ← `AppSendController.java:474`，判据 `:473`＋阈值 `CARRIER_FRESH_MINUTES = 5`（`:114`）；值域只有 `REAL_FRESH` / `REAL_STALE` | `"carrierLocationSource":"REAL_FRESH"` ⇒ `knownLabel="实时"` | 键不在 / 后端没合并位置（`fillCarrierLiveInfo` 提前 return） | ⇒ `UNKNOWN`，`knownLabel = null` ⇒ UI 说**"新鲜度未给"**；`REAL_STALE` 只说"位置可能过期"，**不升格**成实时 | `UserTrackingContractTest.kt:59`（STALE 不升格）、`:148` |
| `lastReportTime`（车辆上报时刻） | `Repo.kt:892` `reportTimeOf("lastReportTime","reportTime","gpsTime")`，helper 在 `Models.kt:130`：`ts(multiStr(*keys))`，**不追加 `createTime` 回落**（追加的就是 `timeOf`，`:124`） | **只有商城侧有**：`AppProductOrderTraceRespVO.java:72` ← `ProductOrderServiceImpl.java:379`（`location.getReportTime()`）。**寄货 `/send/track` 侧 PRODUCER_NOT_FOUND**：`AppSendOrderRespVO.java:100-124` 里没有任何上报时间键，`grep -rn "reportTime" controller/app/transport/send/` 只命中 `AppSendController.java:273-276` 的内部形参与判据 | 商城：`"lastReportTime":1759309920000`（epoch 毫秒，`ts()` 转可读串） | 键不在 / 寄货侧恒缺 | ⇒ `""` ⇒ `TraceView.kt:127-129` 拼**"上报时间未给"**；旧写法会拿订单创建时间当车辆上报时刻（= 造一个时刻），本轮收口 | `UserTrackingContractTest.kt:113`（缺键 ⇒ `""`，且不等于 `createTime`）、`:122`（在场 ⇒ 时区无关正则，且明确 `assertNotEquals` 创建时刻） |
| `carrierTaskStatus`（当前任务） | `Repo.kt:893` `multiStr("carrierTaskStatus")` ⇒ `ParcelTrace.carrierTaskStatus`（`Models.kt:1214`，默认 `""`） | `AppSendOrderRespVO.java:118` ← `AppSendController.java:395`（`TaskItemStatusEnum.nameOf(status)`，码表 `TaskItemStatusEnum.java:24-32`）；前置 `:215-217`：**没有派单明细就直接 return**，所以未派单时这个键天然缺席 | `"carrierTaskStatus":"派送中"` | 键不在（未派单 / 无权看作业明细） | ⇒ `""` ⇒ `TraceView.kt:136-143` 出**"当前任务：后端未下发（尚未派单，或本单无权查看作业明细）"**；不许拿订单状态名 `statusName` 顶一个作业状态 | `UserTrackingContractTest.kt:159`（读得到）、`:171`（未派单 ⇒ 空串且 `assertNotEquals(statusName, carrierTaskStatus)`） |
| `carrierArrivedTime` | `Repo.kt:894` `reportTimeOf("carrierArrivedTime")` ⇒ `:1230` | `AppSendOrderRespVO.java:120` ← `AppSendController.java:405`（`item.getUpdateTime()`，服务器时间） | `"carrierArrivedTime":1759312000000` | 键不在 | ⇒ `""` ⇒ "（更新于 …）"整段不出现，只留状态名 | 同 `:159` 一条里一起钉 |
| `carrierArrived` / `driverArrived` | `Repo.kt:899` `multiBool("carrierArrived","driverArrived","arrived")` | `AppSendOrderRespVO.java:114` ← `AppSendController.java:403`；商城侧 `AppProductOrderTraceRespVO.java:46` | `true` | 键不在 | ⇒ `false`（保守方向：不说"已到达"） | `UserTrackingContractTest.kt:159` |
| `positionSource`（**自造别名，本轮删**） | 曾是 `Repo.kt:822` 的候选之一；现在 `dataSource` 只剩 `carrierLocationSource`/`dataSource` 两键 | **PRODUCER_NOT_FOUND**：后端 VO 与 `DemoData` 两侧都 0 生产者（`grep` 实测） | — | 恒不命中 | 删掉 ⇒ 不再假装读得到一个永不存在的键；`dataSource` 保留是因为演示面 `DemoData.kt:556` 真会写它 | `UserTrackingContractTest.kt:102`（给了 `positionSource` 也不再被采纳） |
| 位置来源文案的**单一映射点** | `LocationSource.knownLabel`（`Models.kt:531-540`）+ `LocationSource.of`（`:554-562`）；`BusVehicle.sourceText`（`:632`）改为**委托**同一映射，`UNKNOWN` 故意不给文案（两侧兜底能力不同），兜底分支读 `dataSource` 里的 `STALE`/`REAL` | 公交侧对应 `AppBusNearbyRespVO.java:65`/`:45` 一族（§七.7），承运侧对应 `AppSendController.java:474` | `"REAL_FRESH"` | 键不在 | 不许再出现第二个拼码点；`BusEtaContractTest` 13 例 0 红即"委托后行为逐字未变"的证据 | `BusEtaContractTest.kt`（13/0）、`UserTrackingContractTest.kt:46`/`:59` |

### 八.1 本节新增的 OPEN_DEFECT（登记，不动判据）

| 编号 | 缺陷 | 为什么这轮不修 |
| --- | --- | --- |
| D-⑩⑪⑮-1 | `DemoData` 没有种 `carrierTaskStatus`/`carrierArrivedTime`（`grep -rn carrierTaskStatus app/src/main` 实测 4 处命中、3 个文件：`Models.kt:1214`/`Repo.kt:732`/`TraceView.kt:139`/`TraceView.kt:140`，**`DemoData.kt` 0 处**），所以**演示面**这三格里"当前任务"永远念"后端未下发" | 演示数据的口径属于 ⑰⑱"不扩大 UI/不改演示面语义"，且这条是诚实文案而不是假数；要补得连 `DemoData` 的注释契约（`:103`/`:115`）一起改，排进 F-3 ② 同批 |
| D-⑩⑪⑮-2 | 寄货 `/send/track` **没有上报时间键**（见上表 PRODUCER_NOT_FOUND 那格），所以用户页的"上报时间"在寄货域恒为"未给" | 补键要改后端 VO 与 `fillCarrierLiveInfo`（常驻指令"不要重新设计后端"），且 ⑲ 明确本轮不做后端改动 ⇒ 如实登记 |
| D-⑩⑪⑮-3 | 本轮在 `Models.kt`（`reportTimeOf`、`LocationSource` 块、`ParcelTrace` 三字段）与 `Repo.kt`（`traceOf` 三行、`geoOf`）插了行，§七 里指向这两个文件的 **67 条 App 侧锚点**（`Models.kt` 44 + `Repo.kt` 23，`check_doc_anchors.py` 计数）只做到"行号仍在有效范围内"，**没有逐条重验内容**；其中确证已漂的是 `Models.kt:731`/`:753`/`:757`（现分别为 `:761`/`:772`/`:776`） | 逐条内容重锚既不是 grep 能自证的（两种指纹都会假报：helper 型锚点的字段名本来不出现在定义行），又不能靠猜填数字。已把工具与口径登记，重锚排进 F-3 ② 与 §五 那 32 处同批 |
| D-⑩⑪⑮-4 | 本节新增的判据面（`carrier*`/`current*`/`reportTimeOf` 不回落/`positionSource` 不认）目前**只有用例（`UserTrackingContractTest` 12 例）在钉，没有 `tools/check_*.sh` 锚点簿**；`check_location_probes.sh` 的 L-a…L-h 钉的是坐标换算与上报报文，覆盖不到读取侧 | 扩 L-i…L-k 必须同轮在 `navigation_negative_controls.sh`（L 家族的负控在那本里）加破口，属 ⑱；本轮先把缺口写明，不让它伪装成"已判据化" |

**三态**：本节到 `BUILD_VERIFIED` 为止——`UserTrackingContractTest` 12 例 0 红、全量 41 类 403 例 0 失败（14:23:58 同一批产物）、
20 本 `check_*.sh` 全 rc=0 ✘=0、负控 20 本里 18 本 rc=0。`DEVICE_VERIFIED` **0 条**：
`TraceView.kt:122-143` 这两行文案在 JVM 里没有渲染过，"暂无实时位置"到底长什么样、会不会把行高压坏，都没看过。

## 九、无生产者键六族（F-3 ②，2026-10-01 17:3x 逐端点量）

指令 ② 要的是"每处先判 producer，再形成 contract→enum→mapper→UI"。下面是本轮收口的六族，
**每格都是当轮 grep/读文件得到的行号**，不是从上一节抄的。小程序列只写实测读点（`cargo-post-miniprogram`，本轮没改它）。

| 族 / 端点 | 后端生产者（文件:行） | 小程序消费者 | 演示面 writer | App 改后读法 | 钉它的用例 |
| --- | --- | --- | --- | --- | --- |
| 商品 `/product/page`（`AppProductRespVO.java:12-34`，**12 个键**：`id/name/fromVillage/price/unit/image/imageUrl/badge/description/stock/status/sort`） | `:30 Integer stock` | `goods/detail/detail.js:70` `p.stock != null && Number(p.stock) <= 0`、`:129` 读 `product.stock` | `DemoData.kt` `stock` 1 处（真键，保留） | `Models.kt:299 stock: Int?` 只读 `stock`；`:310 originPrice`、`:315 sales` **三面 0 生产者 ⇒ 读法删除**；`soldOut` 先过 null ⇒ 缺键不判售罄；`stockText`（`:328`）未给="库存未给"不含数字 | `NoProducerKeyFamilyContractTest` ① |
| 司机档案 `/transport/driver/profile`（`AppDriverProfileRespVO.java:11-23`，**7 个键**：`driverId/name/mobile/vehicleId/plateNo/cargoCapacity/cargoCapacityKg`） | `:19 plateNo`、`:21 cargoCapacity`、`:23 cargoCapacityKg`；赋值 `DriverAppServiceImpl.java:173-176` + `fillVehicle:183-190`（**只在有活跃绑定时调用** ⇒ 无车时这三键真的缺席） | `workbench.js:182` `profile.plateNo \|\| ''`、`:183` `profile.cargoCapacity \|\| 0`、`:82` 注释写明该字段是"空余仓位百分比" | `DemoData.kt` `plateNo` 2 / `cargoCapacity` 1 / `cargoCapacityKg` 1 | `Models.kt:1363 plate = multiStr("plateNo")`（旧读 `plate/vehiclePlate/carNo` 三个自造名 ⇒ 真机车牌整列空白）、`:1378 capacityKg: Double?`、`:1383 cargoCapacity: Int?`；`verified`/`usedKg` **不再读任何键**（`:1353` 恒 null）；百分比唯一出口 `freePercent(loadedCount)`（`:1361`，任一未给 ⇒ null） | 同上② + `check_default_value_probes.sh` DV-m/DV-q |
| 揽件 `/driver/pickup`（`AppDriverPickupRespVO.java:13-33`，**11 个键**：`orderId/bizType/orderNo/orderType/goodsName/weightKg/receiverName/receiverMobile/receiverAddress/mailNo/pickupCode`） | 勾选态**不存在**（11 键里没有一个表示"用户勾了没勾"） | 未读（小程序揽件页按 `pickupCode` 走，不读勾选） | 无 | `Models.kt:1506 checked: Boolean? = null`，UI 不把"没给"当"没勾" | 同上③ |
| 交接 `/driver/handover`（`AppDriverHandoverRespVO.java:14-62`，**17 个键**：`id/orderId/orderNo/goodsName/stationId/stationName/fromDriverId/fromDriverName/toDriverId/toDriverName/itemCount/weightKg/photoUrl/status/statusName/handoverTime/remark`） | 拍照**要求位不存在**；`photoUrl:50` 是真实回读键（写入侧 `AppDriverHandoverConfirmReqVO.java:19`） | `handover.wxml:55` 读 `item.goodsName/item.itemCount/item.weightKg` | `photoUrl` 保留 | `Models.kt:1571 needPhoto: Boolean? = null`；`photoUrl` 仍读真键 | 同上④ |
| 候选方案 `/transport/order/topology`（`OrderTopologyRespVO.Candidate:60-69`，实测键 `modeName:61`、`legCount:62`、`chosen:69`） | 同上三键 | 小程序**不读**（`modeName`/`chosen` 在 `cargo-post-miniprogram` 全域 0 命中） | `DemoData.kt` `modeName` 2 / `legCount` 2 / `chosen` 2（真键，保留） | 九个自造名（`name/planName/title/recommended/best/selected/legs/segments/explain`）解析体 0 命中（DV-n2=0）；`recommended: Boolean?`（`:1178`）、`legs: Int?`（`:1179`） | 同上⑤ + DV-n/DV-n2 |
| `steps` / 车道 / 段状态（`AppDriverRouteRespVO.java:17-86` 的键表里**没有** `steps/guidance/instructions/recommendedLane/etaSeconds/current/done`） | **PRODUCER_NOT_FOUND**（整族） | 不读 | `DemoData.kt` 已撤（DV-r=0） | 读点保留但改 `multiNumOrNull`：`Repo.kt:1168 recommendedLane`、`:1177 etaSeconds` ⇒ 缺键是 null 而不是"车道 0 / 还有 0 秒"；`:1102`/`:1104`/`:707` **仍计在 C 类**（诚实：后端真的不发，等 ⑬⑭⑯ 收） | `RouteGuideContractTest`、`check_eta_probes.sh` 残留段 |

**口径要点（本轮新钉的三条，都会烂如果只写"已修"）**：
- `soldOut` 的 null 判序与小程序 `detail.js:70` 逐字同形 ⇒ 两客户端对"缺库存"的行为一致，这是**对齐**不是巧合。
- `freePercent` 用**件数口径**（`cargoCapacity − loadedCount`，`loadedCount` 的生产者是班次 `AppDriverShiftRespVO` 的 `loadedCount`）
  而不是重量口径（`usedKg` 三面 0 生产者）⇒ 工作台仪表盘的百分比从此只有一个来源，页面不许自己除（DV-q3=0：页面裸读 `usedKg` 归零）。
- 演示面只发**后端真键**（本轮 census 的 `DemoData` 命中数与上表一致）。DV-r 这条判据的作用就是说清楚：
  演示面一旦把被撤的别名发回来，它就又变成第四个真相源，判据当场响。

### 九.1 本节新增 OPEN_DEFECT（登记，不动判据）

| 编号 | 缺陷 | 实测证据 | 为什么这轮不修 |
| --- | --- | --- | --- |
| D-②-1 | census 的 B 类口径是"**后端全模块**有同名声明"，不是"本端点 RespVO 有该键" ⇒ B 会低估缺席比例 | `AppDriverProfileRespVO.java` 全文 24 行、7 个键，没有 `routeName/shiftCode`；但 `MonitoringShiftRespVO.java:17/20` 与 `MonitoringMapDataRespVO.java:45` 声明过同名键，census 把这两个读点判成 B | 给 census 加"端点级"列要改它的扫描模型（现在是全模块字段表），改坏会让 §二/§二之二/`DV-k` 的既有基线全部失去可比性。本轮先把口径写明、端点级结论交给本节这张表 |
| D-②-2 | `Models.kt:227` 的 `multiStr("role","userRole")` **三面 0 生产者** ⇒ `Account.role` 恒走 `fallbackRole` | `grep -rn '"role"\|userRole' cargo-post-platform/yudao-module-transport/src/main/java` = 0；`DemoData.kt` = 0 | 这就是指令 ③ 的落点（`AccountRole` 与 `ActiveDomain` 分离 + `accountRole` 不许因页面切换被改）。在 ② 里改它会和 ③ 的判据抢同一锚点 ⇒ 排 ③ |
| D-②-3 | 字符串族剩余 **11** 处未收（寄/收件人 4、线路与车辆 4〔含 `company`〕、区域与空态 3） | `DEFAULT_VALUE_AUDIT.md` §四之二 逐站点行号；其中 `senderName/senderMobile/transferStation/toPlate/company/provinceName` 有 `DemoData.kt` 在发（实测 5/5/3/3/1/2 处），`handoverStation/nextPlate/regionText/areaText/emptyText` 三面全 0 | 这批的难点不是默认值而是**端点归属**：寄件人字段在后端到底走 `AppSendOrderRespVO` 的哪几个键、线路页的 `transferStation` 该由哪个端点供给，需要先定契约再改可空，硬改会把 UI 推到"整屏未给"。⑲ 禁止本轮扩大 Compose 改造 ⇒ 登记待 ⑥⑦ 同批 |
| D-②-4 | 在册"32 处 C 站点"这个数**不可复现**：census 从未保留"三面 0 生产者 ∩ 数值布尔"这一列 | `grep -n "numbool\|no_producer" tools/default_value_census.py` 只见 `class`/`c_no_producer` 两个聚合；本轮在同一份 JSON 上按 §二之二 规则量到 C=65、其中数值布尔 25、非可空 19 | 不修的话下一轮仍然只能引用一个来历不明的数。修法是在 census 里补一列并把基线钉进 `DV-*`，属工具改动 ⇒ 排进 ⑰（与"Route API/Cache 终检"同轮做，避免一次改两个工具面）|

## 十、真后端在线契约核对（HTTP 层实测，2026-10-08 12:1x–12:2x）

前九节都是**静态**三方对照（读源码 + 读 VO）。这一节是本仓库第一次**动态**核对：把客户端 61 个调用面
按 `Api.kt` 的真实参数顺序原样打到跑在 `127.0.0.1:48080` 的真后端上，逐条记 `code` 与响应键名。

**复跑方式**（后端在环才有意义）：

```
# 1) 私有 Redis（不动共享 6379）：redis-server D:/android-dev/redis-6390.conf
# 2) 后端：java -jar yudao-server.jar --spring.profiles.active=local \
#            --spring.data.redis.host=127.0.0.1 --spring.data.redis.port=6390 --spring.data.redis.database=0
# 3) 探针：export MYSQL_PWD=<本地 root 口令，见 application-local.yaml> 后
#          python3 D:/android-dev/live_contract.py   # 53 条读+写
#          python3 D:/android-dev/live_writes.py     # 真实 id 复跑写路径
```

`live_contract.py` 不发“猜的”请求：每个端点的方法、body/query 归属都照 `Repo.kt` 的 `Api.get/post/put/delete`
调用点抄，登录用 `13800000000`（用户面）与 `13800138001`（司机面）各取一个 token。

### 10.1 首轮读数与 13 条非 0 的归因

合计 **53 条，code=0 40 条**。13 条非 0 逐条查过，只有 1 条是真缺陷：

| 端点 | code | 后端原因（实测 msg） | 判定 |
|---|---|---|---|
| `/member/address/list` | 500→**0** | 本地库缺 `member_address` 表 | **已修**：补跑 `sql/mysql/transport-menu.sql` 的建表段，复测回 `["areaId","areaName","defaultStatus","detailAddress","id","mobile","name"]` |
| `/member/auth/social-auth-redirect` | 500 | 系统异常 | **非客户端调用面**：61 个调用点里没有它（小程序走 `/member/social-user/get-subscribe-template-list`，实测回 28 条）。登记为后端侧观察，不算客户端缺陷 |
| `/send/create` | 400 | 请求参数不正确:货物名称不能为空 | 探针自身漏键——客户端 `buildSendForm()` 是发 `goodsName` 的，见 10.3 |
| `/product-order/trace`、`/product-order/cancel` | 1005010000 | 订单不存在 | 探针用了合成 id 999999 |
| `/driver/depart`、`/driver/arrive` | 1005005000 | 班次不存在 | 同上，10.2 换真实 shiftId 后两条都 code=0 |
| `/driver/pickup-verify` | 1005006000 | 订单不存在 | 同上；真实取件码不在只读面里，未强跑 |
| `/driver/leg/load` | 1005016000 | 运输段不存在 | 同上，10.2 换真实 legId 后变成状态机拒（见下） |
| `/driver/handover/confirm` | 1005016002 | 交接记录不存在 | 同上；`/driver/handovers` 实测空列表，本机无该形状数据 |
| `/notification/read`、`/driver/messages/read` | 1005016007 | 无权操作该通知 | 探针用了 id=1（不属于本账号）；10.2 换自己列表里的 id 后两条都 code=0 |
| `/send/confirm-station-action` | 1005006000 | 订单不存在 | 合成 id；10.2 换真实 orderId 后是状态机拒 |
| `/member/address/delete` | 1004004000 | 用户收件地址不存在 | 合成 id；10.2 改成“刚 create 回来的那条”后 code=0 |

`/driver/position` 只回 `["dataSource"]`、`/driver/route` 回 `null`：与 §八 的静态结论一致（司机没在环上报时后端
就是不给坐标），客户端据此显示「未取到」，不是解析缺陷。

### 10.2 真实 id 复跑：把“探针缺陷”和“契约缺陷”分开

`live_writes.py` 先从只读端点取回**本账号真实持有**的 id，再打写端点。8 条里 code=0 有 5 条：

| 写端点 | id 来源 | 实测 |
|---|---|---|
| `/member/address/delete` | 上一条 create 返回的自增 id | **0** `true` |
| `/transport/notification/read` | `/notification/page` 第一条（该账号 10 条） | **0** `true` |
| `/driver/messages/read` | `/driver/messages` 第一条（5 条） | **0** `true` |
| `/driver/depart` | `/driver/shifts` 第一条（2 条班次） | **0** `true` |
| `/driver/arrive` | 同一班次 + 其 `stops[].stationId` | **0** `true` |
| `/driver/leg/load` | `/driver/legs` 第一条 | 1005016012 运输段当前状态不允许该操作（刚被 depart 推走） |
| `/send/confirm-station-action` | `/send/page` 第一条 | 1005010008 订单当前状态不允许该操作 |
| `/send/create` | 站点 1→2 | 1005016009 所选站点车辆无法进入 —— 见 10.3 |

后三条是**状态机/业务规则**拒，不是契约不匹配；`ErrorCodeConstants` 里逐条对得上。

### 10.3 本轮真正查出的两个缺陷

**① `/send/stations` 会把下不了单的站点推给寄货页（后端面）**

`AppSendController.stations()` 走 `stationService.getSimpleList()`，而它是裸 `mapper.selectList()`——不过滤可达性；
下单却在 `TransportOrderServiceImpl.requireEnabledStation()` 里用 `StationAccessUtil.vehicleAccessible()` 硬校验。
本机实测：表内未删除站点 1814 条，其中 `vehicle_access=0` 的有 **9 条（id 1–8、101）**，而这 9 条恰好排在列表最前面，
也就是说用户在寄货页选“第一屏”站点就会撞 `1005016009`。

改法是在**唯一一处** app 面出口按下单同源规则过滤（`AppSendController.stations()`，只 1 行 + 1 个 import）。
⚠ 诚实登记：本机**没有 Maven**（`which mvn` 空、无 wrapper，`yudao-server.jar` 是 2025-09-13 的产物），
所以这个补丁**只做了源码级修改，未经编译与在线复验**；运行时证据来自未打补丁的 jar。
用可读的可达站点对（`vehicle_access=1` 的最小两个 id 102→103）直接打同一个 jar，`/send/create` 实测 **code=0**，
回执 `{"id":251,"orderNo":"TP20261008122257715","status":8,"totalAmount":8.76,"reviewStatus":1,"pickupServiceMode":"STATION_TO_STATION",...}`
—— 下单闭环本身是通的，卡点只在站点选择面。

**② `/send/create` 回执解析在读三个永不出现的键（客户端面，已修）**

`Repo.createSendOrder()` 原先读 `multiStr("orderNo","bizOrderNo","no")`、`multiStr("pickupCode","code")`、
`multiNum("fee","amount")`。后端 `AppSendOrderRespVO` 的**生产者键**是 `orderNo / pickupCode / totalAmount`，
`bizOrderNo`、`no`、`code`、`fee`、`amount` 在 Controller/VO/小程序三面 **0 生产者**（实测回执里也确实只有 `totalAmount`）。
后果：订单金额恒读成 0.0——`Ack.amount` 目前没有被页面消费，所以是“埋着”的错，不是当下可见的错。

收口：解析层只留真实生产者键；夹具（`SendOrderTest` 两处）与 `DemoData.kt` 的 `/send/create` 回落形状
同步改成 `totalAmount`；并按 §E-1 的既有做法补一条具名负控用例 `后端从不发的自造别名不再被采纳`
（只发 `fee/amount/no/code/bizOrderNo` 时三个字段必须留空/0.0）。

### 10.4 `@RequestParam` 写端点三方对齐（顺带钉住）

扫描 8 个非 GET 且用 `@RequestParam` 收参的端点，逐个与 `Repo.kt` 的调用形状比对：`Api.post/put(path, body, query)`
第 2 个位置参数是 body、第 3 个才是 query（`Api.kt` 的文档注释已写明这一点）。8 条**全部一致**，
`/notification/read-all` 的 `orderId` 是 `required=false`，实测空参调用回 `true`。
过程中我自己误判过一次“5 个写端点参数位置错了”，是把位置读反了——已按源码更正，未据此改任何代码。

### 10.5 同一族的第二处：`/product-order/create` 的回执在读三个后端从不发的键（客户端面，已修）

顺着 10.3 ② 的同一把尺子量商城单：`AppProductOrderController.create()` 的回执 VO **只有两个字段**
（`AppProductOrderCreateRespVO`：`id`、`orderNo`），而 `Repo.createProductOrder()` 原先写的是
`multiStr("orderNo", "no")` 与 `multiNum("amount", "totalPrice")` —— `no`、`amount`、`totalPrice`
三个键在这个端点的 Controller/VO/小程序三面 **0 生产者**（列表 VO 里那个金额叫 `totalAmount`，回执里没有）。
`orderNo` 是真生产者，保留。

后果与 10.3 ② 同类且更轻：`Ack.amount` 在这条路径上恒为默认值，而 `GoodsDetailScreen` 只消费
`notSent / demo / orderNo`，所以**当下不可见**。修法是撤读，不是补键——回执没有金额就不装出有金额。

⚠ **诚实归因**：这一处**不是判据抓出来的**，是逐行读后端 VO 读出来的。现有的别名面判据（Q-b 只扫
`Models.kt` 的 QuoteResult、DV 的别名表不含 `totalPrice`/`no`）**覆盖不到这一行**，改前改后两本判据都绿
⇒ 登记为判据缺口 **`D-CE-1`**：`createProductOrder` 的别名撤读没有结构判据，只有一条具名行为用例
`商城单回执只认 orderNo（后端那个 VO 就只有 id 与 orderNo）` 钉住（先红后绿：改前那条会拿到 `SC-DEAD` / `66.0`）。

## 十七、`/send/topology`「查件票面」逐字段取证（D-085，2026-10-13 实测）

判据簿：`tools/check_parcel_ticket_probes.sh`（PT-a…PT-l）；负控：`tools/parcel_ticket_negative_controls.sh`（41 条破口）；
行为用例：`app/src/test/java/com/shanxiang/buspost/ui/user/ParcelTicketContractTest.kt`（15 条）。
生产者：`AppSendController.java:208-215`（路由与非归属分支）、`TransportTopologyServiceImpl.java:92-176`（逐段赋值条件）、
`OrderTopologyRespVO.java:16-139`（字段与可空性）。**这一整页此前是死代码**：`Repo.parcelTopology()`（`Repo.kt:918`）全仓 0 消费者。

| 键 | 后端 producer（VO 行） | 只有这些条件才非空 | 小程序 consumer | App 读法与缺失行为 |
|---|---|---|---|---|
| `orderNo` `orderStatus` `orderStatusName` | `:21/:23/:25` | `order != null`（`ServiceImpl:92-99`） | `parcel.js:439` 只读 legs/handovers | 新增读 `orderStatusName`；空 ⇒ 票面念「后端没给」 |
| `originStationName` `destinationStationName` | `:27/:29` | 站点档案查不到就是 null（`:97-98`） | 不读 | 新增读法（此前 App 只认自造的 `startStation`/`endStation`）；null ⇒ 念「后端没给」，不兜站名 |
| `planningMode` `planningModeName` `planNo` `planReason` | `:32/:34/:36/:46` | `plan != null`（`:100-109`） | `planReason` 读（`parcel.js:439-440`） | 票面抬头四行；缺席 ⇒ 念「后端没给」 |
| `totalLegs` `transferCount` | `:38/:40` Integer | 有段时被覆盖（`:118-123`） | 不读 | `multiNumOrNull`；**缺 ≠ 0 段 / 0 次** |
| `totalDistanceKm` | `:42` BigDecimal | 段里程和 > 0 才覆盖（`:125-129`） | 不读 | 必须带口径：段全 `AMAP` 才说真实道路；含 `ESTIMATED` 就说「含估算段，不是真实道路里程」（依据 `MultiLegPlanner.java:187` 直线估算、`transport-schema.sql:257` Haversine） |
| `totalDurationMinutes` | `:44` Integer | **各段之和 + 每次换乘 20 分钟**（`:134`，@Schema 只写"总时长(分钟)"没提这条口径） | 不读 | 措辞强制带「含 N 次换乘、每次 20 分钟」；换乘次数没给就写「是否已计入未知」，不替后端宣布 |
| `candidates` | `:49` List | `multiLegService.preview` 抛异常时保持 null（`:172-174`） | 不读 | 空 ⇒ 念「方案对比没给，不表示这单只有一个方案」 |
| `Candidate.chosen` | `:69` Boolean | 可缺席 | 不读 | 三态：true/false/null 各一句，null 念「后端没说（chosen 未给）」，不许塌成"不推荐" |
| `Candidate.durationMinutes` `distanceKm` `transferCount` | `:65/:64/:63` | 可缺席 | 不读 | 全部 `multiNumOrNull`；改前 `minutes` 塌成 0 ⇒ 票面会念「这个方案 0 分钟」 |
| `Handover.fromPlateNo` `toPlateNo` | `:118/:119` | 未派单为 null（`:330-344`） | 不读 | **真键名**；改前认 `fromPlate`/`toPlate`/`vehiclePlate`（三面 0 生产者）⇒ 真接口下"谁交给谁"整行念空，只有演示包念得出台词 |
| `Handover.arrivedAt` `handoverStartedAt` `handoverCompletedAt` | `:123/:124/:125` | 可缺席 | 不读 | 完成 > 开始 > 到达；改前认 `handoverTime`/`confirmTime`（三面 0 生产者）⇒ 同上 |
| `Handover.itemCount` | `:120` Integer | 可缺席 | 不读 | `Int?`；塌成 0 会被念成「0 件」 |
| `Handover.statusName` `exceptionReason` | `:122/:126` | 可缺席 | 不读 | 异常原因空 ⇒ 念「无异常记录」，不许念成「有异常」 |
| `Timeline.eventTypeName` `eventTime` `detail` | `:133/:134/:135` | `order != null` 才填（`:176`） | 不读 | 新增读法（改前只认 `label/title/nodeName`、`time/createTime`）⇒ 拓扑时间线整列念空；时刻必须过 `nodeTimeOf` 归一（后端把 `LocalDateTime` 序列化成 Epoch 毫秒，裸切串会切出 `"17590"`） |
| 非归属人访问 | `AppSendController.java:214-215` | 返回 `new OrderTopologyRespVO()` = **全字段 null 的空结构，不是错误码** | — | 票面四格之一 `Absent`：只说「后端这次没给这张单的票面内容」，绝不念成「包裹不存在」，并保留「回追踪页」出口 |

复跑命令（原样可执行）：`bash tools/check_parcel_ticket_probes.sh`（本轮 rc=0：PT-f 键基数 11 / 白名单外 0、PT-l 用例 15）、
`bash tools/parcel_ticket_negative_controls.sh`（本轮 ✔=41 ✘=0 △=0）。

⚠ 三面取证的边界：本轮**没有真接口回包**（本机后端未起这张单的登录态），"票面看到 n 段真交接"这一格仍未被演示；
页面渲染一律 `NOT VERIFIED ON DEVICE`。另记一条判据缺口 **`D-CE-2`**：`R-n3` 那条"删免责话"的负控从写下那天起
就一直 sed 一句不存在的「不代表车辆能到」（10-12 话术收紧时改写了），静默落空被记成"判据不判"——
本轮补了 `R-n4`（钉现存那句免责话恰好 4 处）并把注入器换成会报落空的 `swap1`，`reach` 负控由 ✘=1 转 ✘=0。


## 十八、`earliestPickupTime`「最早取货时间」逐字段取证（D-087，2026-10-13 实测）

### 18.1 键的三面（写 / 存 / 读）

| 面 | 生产者 | 结论 |
| --- | --- | --- |
| 写入 | `AppSendOrderCreateReqVO.java:76` `private LocalDateTime earliestPickupTime`（`@Schema(description = "最早取货时间")`，无校验注解） | App 的 `/send/create` 收这个键，此前 App 从来没发过 |
| 落库 | `TransportOrderServiceImpl.java:149` `.earliestPickupTime(reqVO.getEarliestPickupTime())` → `TransportOrderDO.java:26` → `transport_order.earliest_pickup_time`（`transport-schema.sql:148`） | 真落库，不是被丢弃的键 |
| 消费 | `DispatchServiceImpl.java:4076-4090`（`filterByTaskWindow`，调用点 `:591`，在 `doCreateSmartPlan` 里）+ `:3513` `.readyTime(toOffset(order.getEarliestPickupTime()))` → `AlgorithmOrderDTO.java:74` | **会真的改派单**：晚于任务窗口结束 ⇒ 本批不派并记理由串；同时作为揽收下限下发算法 |
| 回读 | `AppSendOrderRespVO.java`（`/send/page` `:139`、`/send/track` `:154` 共用 `toRespVO`）**本轮之前 0 命中** | 只写不读 ⇒ 本轮补一个键闭合（`toRespVO` 走 `BeanUtils.toBean`，同名同型自动映射） |

⚠ 一条不能引用的账面结论：`V024__key_indexes_and_unique_keys.sql:10` 的**注释**写着
`WHERE status=READY_FOR_POOL ORDER BY earliest_pickup_time`，索引确实按 `:14` 建了，
但全仓 `grep orderByAsc(TransportOrderDO::getEarliestPickupTime)` = **0 处** ⇒ "入池按取货时间排序"这句没有代码生产者，界面与台账都不许念。

### 18.2 出网格式（这一条决定了整个实现）

`TimestampLocalDateTimeDeserializer.java:24-27`：

```java
return LocalDateTime.ofInstant(Instant.ofEpochMilli(p.getValueAsLong()), ZoneId.systemDefault());
```

- 认的是 **Long 毫秒**（`YudaoJacksonAutoConfiguration` 注册的就是这一对（de）serializer，
  App 侧 `Models.kt:104-114` 的 `ts()` 早就按这个契约处理 13 位数字）；
- `p.getValueAsLong()` 对**非数字串不抛错、静默返回 0** ⇒ 发 `"2026-10-13 15:00:00"` 不会 400，
  会安静存成 **1970-01-01**，而界面上一切正常。这是本轮所有"只认数字 / 不许拼串"判据的来源；
- `ZoneId.systemDefault()`：`deploy/docker-compose.yml:21` 与 `yudao-server/Dockerfile:12` 都是 `TZ=Asia/Shanghai`，
  MySQL URL 亦 `serverTimezone=Asia/Shanghai` ⇒ 本机用固定 `PICKUP_ZONE = Asia/Shanghai` 生成毫秒
  （与 `BusScreen.kt:913` 同口径），设备换时区不会把墙钟挪走。

### 18.3 小程序对照

`cargo-post-miniprogram` 全域 `earliestPickupTime` / `pickupTime` / 取货时间 = **0 命中** ⇒ 没有界面形状可对标。
字段语义只有"下限"，因此界面上**不做"上午/下午"这类假装是区间的选项**，只有"哪天几点之后"。

### 18.4 后端仍缺的三条（本轮没替它编）

1. `@Future`/范围校验缺席（`审计报告_联动_2026-09-21.md:326` 早就登记过这一行）⇒ 填过去的时间后端照收照存；
   App 侧只是**不发**过去，不等于后端安全。
2. `latestDeliveryTime` 在 App 的 create VO 里**没有字段**（只有 admin 侧 `TransportOrderBaseVO` 有），
   而 `filterByTaskWindow` 的 `expired` 那一支读的就是它 ⇒ 用户端单子的时效上限永远为 null，
   那一支对 App 单**永不生效**。
3. 班次/任务窗口没有任何 app-api 生产者（`currentBatch()`、`formatWindow()` 都在服务端），
   所以界面只能说"晚于窗口结束本批不派"，不能说"你这单排到哪一班"。

### 18.5 判据缺口登记

**`D-CE-3`**：`earliestPickupTime` 的**回读键在部署前后形状相同**（都是"没有值"），
本机无法区分"用户没填"与"云上还没部署回读键" ⇒ 措辞只能是一句合并的"后端没给这一项（下单时没填也会这样，本机分不开）"。
这不是界面可以修的，要么后端给这一键加"未填也显式回 null 且带旗标"，要么接受合并格；本轮选择如实合并。

## 十九、「收藏线路」三面取证：本机 / 列表 / 后端（D-088，2026-10-13 实测）

### 19.1 三面里只有两面存在

| 面 | 生产者 | 结论 |
| --- | --- | --- |
| 存储 | `Prefs.kt:233-259`（`favoriteLineIds` 逗号分隔字符串 → `Set<String>`；`toggleFavoriteLine` / `isFavoriteLine`） | 只有 id，**没有名字**；DataStore 本机私有目录 |
| 名字 | `Repo.kt:844-849` `GET /app-api/transport/bus/lines` → `BusLine.parse`（`Models.kt:854-857`：`id = multiStr("routeId","lineId","id")`、`name = multiStr("routeName","lineName","name")`） | 名字**只在列表响应里**；列表不取就永远显不出名字 |
| 后端 | `yudao-module-transport` 的 app 端点包 / `yudao-module-member` / transport 建表 SQL —— 对收藏 **0 命中** | **没有任何收藏接口**，因此"同步/换机恢复/云端收藏"这类话在本 App 里一律是假话 |

⚠ 一条**看起来像证据但不是**的命中：`sql/mysql/ruoyi-vue-pro.sql:2667` 的 `system_menu` 里有一行名字含「收藏」。
那是上游芋道框架自带的后台菜单种子数据（权限项），与线路收藏无关；引用它之前先看它是 `system_menu` 的 INSERT，不是业务表。

### 19.2 旧形状为什么算缺陷（不是风格问题）

`BusScreen.kt:678-680`：
```kotlin
val favIds = Prefs.favoriteLineIds
val favLines = vm.lines.filter { it.id in favIds }
if (favLines.isNotEmpty()) { …渲染整栏… }
```
`vm.lines` 来自 `Repo.busLines(geo)`（`BusScreen.kt:253-255`），而旧写法只取 `lf.items`、把 `lf.error` 丢掉。
于是**三种完全不同的事共用一个形状**：没收藏（`favIds` 空）、线路列表这一轮没取到（error 非空）、
收藏的线路确实不在当前列表里（下架/换号）—— 后两种都会让整栏消失，界面把"没取到"念成"没有收藏"。
这与 D-079（政策页）、D-080（公告栏"没取到 vs 后端确实没有"）、#42（天气按角色）是同一族病。

本轮的拆法：整栏闸门换成 `favoriteRowVisible(favIds.size)`（**本机记着几条**），
未命中那一格 `favoriteUnresolvedText(idCount, resolvedCount, linesError)` 分两支，
失败原因原样带出；`linesError` 从 `Feed.error` 接出来（`BusScreen.kt:132/246/714`）。

### 19.3 用例先红的一条（红因在纯层，不在判据）

`favoriteUnresolvedText(3, 1, "")` 第一版返回"可能已下架"那一支 ——
因为纯层用 `linesError != null` 判"取失败"，而**空串是"失败但没给出原因"**，被当成"没有失败"。
⇒ 修的是纯层（空串/空白归失败支，文案补"没给出原因"），**不是把断言放宽**。第二次跑 9 例全绿。
⚠ 反面做法是把期望改成"空串算下架"来让它绿——那等于把语义洞抄成契约。

### 19.4 登记为判据缺口

**`D-CE-4`**：`check_status_probes.sh` **没有配对的负控簿**（`tools/status_negative_controls.sh` 不存在，本轮实测）。
它对 HomeScreen 的那个"恰好 2"锚点（`when (v.status)`）只能证明"这一轮复跑没红"，不能证明"判得动"。
⇒ 在册待补；补之前不许把 `check_status_probes.sh` 的绿当成"这一格有人管"。
