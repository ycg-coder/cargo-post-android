# 数字默认值 A/B/C/D 分类台账（F-2 第 5 步 + F-3 第 ② 步）

> 首轮日期 2026-09-30（F-2 ⑤），第二轮日期 2026-10-01 17:3x（F-3 ②）。所有数字都是**当轮实测输出**，不是估计值；复现命令见文末。
> 判据簿：`tools/check_default_value_probes.sh`（F-2 是 DV-a…DV-l + GUARD；F-3 ② 扩到 DV-a…DV-s + GUARD + GUARD2，实测 exit 0）。
> 负控制簿：`tools/default_value_negative_controls.sh`（F-2 16 条 ✔=16；F-3 ② 31 条 **✔=31 ✘=0 △=0**，基线与恢复双绿）。
> 分类脚本：`tools/default_value_census.py`（扫描范围由内容决定，不手挑文件）。

## 一、口径定义

| 类 | 含义 | 允许的呈现 | 禁止 |
|---|---|---|---|
| **A 真零值** | 后端真的下发了 0（`BigDecimal ZERO`、`int 0`） | 直接显示 0 | — |
| **B 缺字段** | 报文里没有这个键，或 VO 是包装类型且该次为 null | "未给 / 后端无此口径" | **禁止显示成 0** |
| **C 异常** | 网络/解析失败，或这个键**三面都无生产者**（客户端自造） | 错误态 / 未知态 | **禁止显示成真实值** |
| **D UI 占位** | 只服务于布局的本地字面量（骨架屏、索引、动画） | 视觉占位 | 禁止进入业务结论字段 |
| **E 外部真实生产者** | 高德逆地理/天气、OpenWeather 等第三方报文 | 有值就显示 | 键同样可缺席 ⇒ **按 B 判据处理** |

"三面"= 后端 producer（Java VO 字段声明 + service 赋值）、小程序 consumer（读点）、演示面 writer（`DemoData.kt`）。
三面全无生产者而客户端仍在读的键 = **第四个真相源**，属 C 类。

## 二、实测基数

| 指标 | 改前 | 改后 | 命令 |
|---|---|---|---|
| main 源码带默认值读法总站点 | 477 | 472 | `python tools/default_value_census.py --write` |
| 其中判为 B（后端 VO 是包装类型 ⇒ 可缺席） | 400 | 398 | 同上 |
| 其中判为 C（后端 VO 0 命中） | 77 | 74 | 同上 |
| 判为 A（后端原始类型声明 ⇒ 恒下发） | **0** | **0** | 同上 |
| 数值/布尔**非可空**读法（`multiNum(`/`multiBool(`/`opt*`） | 152 | **132**（DV-k 钉住的基线） | `bash tools/check_default_value_probes.sh` |
| 其中"三面 0 生产者"的数值/布尔读法 | 35 | 32 | `tools/default_value_census.py` |

**A 类为 0 本身就是结论**：`cargo-post-platform` 的 App 侧 RespVO 字段清一色是 `Long/Integer/BigDecimal/Boolean/String` 包装类型
（census 扫后端全模块字段声明，primitive 命中 0），所以**每一个非可空读法都站在"后端可以不给"的字段上**。
这就是 §五 说的"缺失字段被默认值伪装"的结构性来源，而不是个别笔误。

## 二之二、F-3 ② 轮实测基数（2026-10-01 17:3x，与 §二 同一条命令）

| 指标 | 本轮读数 | §二 那一轮的读数 | 口径 / 命令 |
|---|---|---|---|
| 带默认值读法总站点 | **467** | 472 | `python tools/default_value_census.py --write` |
| 判为 B | **402** | 398 | 同上 |
| 判为 C（后端全模块 0 命中） | **65** | 74 | 同上 |
| 判为 A | **0** | 0 | 同上 |
| C 的形态拆分 | 字符串 **38** / 数值布尔 **25**（其中**非可空 19**）/ 数组 **2** | 未拆 | 规则见下 |
| C 的文件分布 | `Repo.kt` 31 / `Models.kt` 27 / `SendScreen.kt` 7 | 未拆 | 同上 |
| `DV-k` 数值/布尔非可空读法基线 | **116** | 132 | `bash tools/check_default_value_probes.sh`（只许降） |

**形态拆分规则**（本轮新定，逐字可复现）：对 census 输出中 `class=="C"` 的行按读法形状分类 ——
`multiArr|optJSONArray` ⇒ arr；`multi(Num|Bool)(OrNull)?\(` 或 `opt(Int|Double|Boolean|Long)\(` ⇒ numbool，
其中**不含 `OrNull`** 的子集就是"非可空"；其余 `multiStr|optString` ⇒ str。

⚠ **§二 最后那行"32 处"这个数在本轮不可复现**（登记 `D-②-4`）：它出自 F-2 轮的一次性 grep，
`tools/default_value_census.py` 从来没有保留这一列（脚本只有 `class` 与 `c_no_producer` 两个聚合）。
本轮在同一份 JSON 上按上面的规则量到的是 **C∩数值布尔 = 25，其中非可空 = 19**。
所以台账里**不写"32 收掉了 13 处"这种减法结论**，只写"本轮把 §四之二点名的六族收口后，
剩余 C 中的非可空数值/布尔读法实测为 **19**"。要让它成为可跨轮比对的指标，得先在 census 补一列，而不是继续沿用 32。

⚠ **census 的 B 口径是"后端全模块有同名声明"，不是"本端点的 RespVO 有该键"**（本轮实测发现，登记 `D-②-1`）：
`AppDriverProfileRespVO.java:11-23` 总共 7 个键（`driverId`/`name`/`mobile`/`vehicleId`/`plateNo`/`cargoCapacity`/`cargoCapacityKg`），
`/transport/driver/profile` 的报文里根本没有 `lineName`/`routeName`/`shiftCode`/`company`；但 census 因为
`MonitoringShiftRespVO.java:17`（`shiftCode`）、`:20` 与 `MonitoringMapDataRespVO.java:45`（`routeName`）声明过同名键，把后两者判成了 **B**。
⇒ census 的 B 会**低估**"这个端点其实不给"的比例。端点级结论必须回到该端点 RespVO 的字段表逐条看
（本轮 `CONTRACT_EVIDENCE.md` §九 就是按端点核的，六族的真键与行号都记在那张表里）。

⚠ **`fillVehicle` 是条件赋值**（`DriverAppServiceImpl.java:176-179` 只在有活跃绑定时 `ifPresent` 调用，`:183-190` 才写 `plateNo/cargoCapacity/cargoCapacityKg`）
⇒ 这三个"census 判 B"的键在**无绑定车辆**那次请求里真的会缺席。本轮把 `capacityKg`/`cargoCapacity` 改可空、
`freePercent(loadedCount)` 任一未给就返回 `null`，正是为了让这一条真实缺席路径显示"未给"而不是"载重 0 / 空闲 100%"。

## 三、本轮收口点：司机运营统计 `/transport/driver/earnings`

选它的理由：它是 C 类里唯一"整屏结论都来自无生产者键"的端点，且演示面把假数据写得和真契约一模一样，
所以连演示路径都看不出破口。

| 模型字段 | 后端生产者 | 小程序消费者 | 旧读法 | 结果 |
|---|---|---|---|---|
| `today` | `AppDriverEarningsRespVO:18 todayAmount` | `earnings.js:65` | `multiNum("todayAmount","todayEarnings","today")` | 真实键可用，但缺键塌 0 |
| `total` | `:22 totalAmount` | `earnings.js:64` | `multiNum("totalAmount","totalEarnings","total")` | 同上 |
| `orderCount` | `:20 totalOrders` | `earnings.js:66` | `multiNum("orderCount","totalOrders","count")` | 真实键排第二，前两个是别名 |
| `items` | `:26 records` | `earnings.js:52-61` | `multiArr("items","details","records")` | `items`/`details` 无生产者 |
| `EarningItem.amount` | `AppDriverEarningsRecordRespVO:20 totalAmount` | `earnings.js:59 r.totalAmount` | `multiNum("amount","fee")` | **真机上每笔渲染 ¥0** |
| `EarningItem.date` | `:26 createTime`（EpochMillis） | `earnings.js:57` | `multiStr("date","day")` | **真机上日期整列为空，按日收益图并成一坨** |
| `week` | 后端全模块 **0 命中** | 不读 | `multiNum("weekAmount","week")` | 恒 0 ⇒ "本周 0 元"是谎报 |
| `month` | 后端全模块 **0 命中** | 不读 | `multiNum("monthAmount","month")` | 恒 0 |
| `totalWeight` | 后端全模块 **0 命中** | 不读 | `multiNum("totalWeight","weight")` | 恒 0 ⇒ 页签"今日 0 kg" |
| `type` | 0 生产者 | 不读 | `multiStr("type","bizType")` | 恒空 |

改前 `weekAmount=452.0 / monthAmount=1830.5 / totalWeight=246.8 / items[].amount / date="09-28"`
**只有 `DemoData.kt` 在发**，即第四个真相源；真机对接后端时这一屏的六个数字里只有三个来自后端，另三个恒为 0。

补丁（全部为可空化 + 只读真实键，不删字段以免打断旧调用点）：

- `Models.kt`：`Earnings` 9 个数值字段、`EarningItem` 2 个数值字段改 `Double?/Int?`；
  新增 8 + 2 个"未给"文案 getter（`todayText/weekText/monthText/totalText/todayBadgeText/orderCountText/todayOrdersText/totalWeightText`、
  `amountText/weightText`），使缺失态能在 JVM 里被断言而不只是被 grep 看见。
- `Repo.kt:1013-1037`（`driverEarnings()` 解析体，行号为 2026-10-01 14:4x 重量值）：解析体改成只读 12 处真实键；`week/month/totalWeight/type` 不再读任何键。
- `Repo.kt` `monthDay()`：`ts()` 归一后是 `"yyyy-MM-dd HH:mm"`，旧实现 `takeLast(5)` 会取出 `09:14` 当日期，现按日期段取 `MM-dd`。
- `Models.kt` `ts()`：新增 EpochMillis 分支。证据是 `YudaoJacksonAutoConfiguration.java:43,62`
  把 `LocalDateTime` 注册成 `TimestampLocalDateTimeSerializer`（后端发 13 位毫秒数，不是 ISO 串）；
  旧实现会把 `1790558040000` 原样显示在时间位上。ISO 串分支保留，两种真实形态都不塌。
- `EarningsScreen.kt`：徽标、三格金额卡、累计三行、明细行、按日收益图共 8 个渲染点改走文案 getter；
  `MoneyCell(value: Double?)` 未给时显示"未给"且不显示单位"元"；`DayChart` 只对有真实金额的记录成图，
  被排除的笔数在副标题里如实标出（`$skipped 笔金额未给，未计入`）。
- `EarningsScreen.kt`：徽标口径修正 —— 今日单数用 `todayOrders`，累计单数用 `totalOrders`（旧代码把 `totalOrders` 标成"今日 X 单"）。
- `DemoData.kt:712-731`：earnings 报文改成与后端同形（`records` / `records[].totalAmount` / `createTime` 发毫秒数），
  撤掉 `weekAmount/monthAmount/totalWeight/todayEarnings/totalEarnings/orderCount/items/date/amount/type`。

测试：`EarningsContractTest`（8 用例，走 MockWebServer 真信封，不是只测模型）——
①真实键全部生效 ②别名键不再被采纳 ③week/month/totalWeight 恒未给 ④空报文全未给且文案不含阿拉伯数字
⑤EpochMillis 与 ISO 两种口径 ⑥缺重量显示"重量未给"而非 0 kg ⑦今日/累计两口径不互串 ⑧演示面与后端同形。

## 四、C 类实测 verdict（F-3 ② 轮：先给本轮收口的六族，再给剩余 65 处的分布）

### 四之一、本轮收口的六族（数值/布尔，F-3 指令 ② 点名的"32 处"里能三方核到真键的那批）

| 族 | 改前读法（自造别名） | 真实键与后端声明行号 | 改后 | 钉它的用例 |
|---|---|---|---|---|
| 商品 `Product` | `multiNum("originPrice",…)` / `multiNum("sales",…)` / 非可空 `stock` | `AppProductRespVO.java:30 private Integer stock;`（`originPrice`/`sales` 后端 0 命中） | 只读 `stock`（`Models.kt:313 stock: Int?`），`soldOut` 先过 null ⇒ **缺键不判"已售罄"**；`stockText`（`:328`）未给时"库存未给"且不含数字 | `NoProducerKeyFamilyContractTest`① |
| 司机档案 `DriverProfile` | `multiStr("plate","vehiclePlate","carNo")`、`capacityCount`、`usedKg`、`verified` 非可空 | `AppDriverProfileRespVO.java:19 plateNo / :21 cargoCapacity / :23 cargoCapacityKg`（**全 VO 只有 7 个键**，`verified`/`usedKg` 三面 0 命中） | `plate = multiStr("plateNo")`（`:1374`）、`capacityKg/cargoCapacity` 改 `Double?/Int?` 单真键（`:1378/:1383`）、`verified` 恒 `null`（`:1353`）；百分比收进 `freePercent(loadedCount)`（`:1361`，任一未给 ⇒ `null`） | 同上② + `CargoGauge` 可空口径参数（DV-q） |
| 揽件 `PickupOrder` 勾选态 | `multiBool("checked",…)` | `AppDriverPickupRespVO.java:13-33` **11 个键里没有勾选态** | `checked: Boolean? = null`（`:1479`），UI 不再把"没给"当"没勾" | 同上③ |
| 交接 `HandoverTask` 拍照要求 | `multiBool("needPhoto",…)` | `AppDriverHandoverRespVO.java:14-62` **17 个键里没有要求位**，只有 `photoUrl:50` 是真回读键 | `needPhoto: Boolean? = null`（`:1582`），照片仍可上传 | 同上④ |
| 候选方案 `Candidate` | `name/planName/title/recommended/best/selected/legs/segments/explain` 九个自造名 | `OrderTopologyRespVO.Candidate:61 modeName / :62 legCount / :69 chosen` | 只读三个真键；`recommended: Boolean?`（`:1178`）、`legs: Int?`（`:1179`） | 同上⑤ |
| `steps` / 车道族 | 别名 + 缺键塌 0 | `AppDriverRouteRespVO.java:17-86` 的键里**没有** `steps/recommendedLane/etaSeconds/current` | 读点保留但改 `multiNumOrNull`（`Repo.kt:1176/:1177`）⇒ 缺键是 `null` 而不是"车道 0 / 还有 0 秒"；**这些站点仍计在 C 里**（诚实：后端真的不发） | `RouteGuideContractTest`、`check_eta_probes.sh` 残留段 |

演示面同轮撤掉了上述被证伪的键（DV-r = 0：`DemoData.kt` 里不许再发这些键，否则它就成了第四个真相源），
并新增 `NoProducerKeyFamilyContractTest`（7 例，走 MockWebServer 真信封）——
其中"别名键不再被采纳"和"演示报文不含被撤键"两类断言，是防止下一轮有人把别名悄悄加回来的唯一机器证据。

### 四之二、剩余 C 类 65 处的实测分布（本轮逐站点量出来的，不是沿用上轮的行号）

| 族 | 站点（实测行号） | 处数 | verdict |
|---|---|---|---|
| 天气实况/预报（高德 + OpenWeather） | `Models.kt:276,277,278,279,280,285,287,291,292`、`Repo.kt:326-330,337,339,342,344,345,346,382,384,387,389,390` | **25** | **E 类**（外部真实生产者，键可缺席 ⇒ 按 B 判据）。census 只查后端所以判成 C，不是"客户端自造"。缺温度显示 0℃ 要先定 UI 未给态口径 ⇒ 随 `D-⑯-*` 单独收口 |
| 逆地理（高德 regeo） | `Repo.kt:269,270,272,273,274,275` | **6** | **E 类**（`adcode/province/city/district/township/formatted_address` 都是高德报文字段） |
| 本地模拟器 | `Models.kt:296`（`mock/simulated`）、`:757/:758`（`simLatitude/simLongitude`）、`:794`（`simRunning`） | **4** | **D 类**：生产者是 `RouteSource.kt` 与 App 自己的运行态，不冒充后端契约。`simRunning` 仍是自造键，待删别名 |
| 本地寄货草稿 | `SendScreen.kt:202,204,206,207`（`optString`）、`:209,213,214`（`optBoolean/optLong`） | **7** | **D 类**（`:188/192/193` App 自写自读）。⚠ 写侧 `pickup?.id ?: 0L` 把"未选站点"存成 `0L`，读回来就像真实 id ⇒ 与 §11 幂等/校验同批处理 |
| 里程/米制旧别名 | `Models.kt:502,845,1998`、`Repo.kt:1133,1168` | **5** | **C 类别名**（旧报文兼容位，保留读法但已改 `multiNumOrNull`）⇒ 随 F-3 ⑬/⑯ 与 `D-⑰⑱-1` 一起收 |
| `steps`/车道/段状态 | `Repo.kt:1102`（arr）、`:1104`（`current`）、`:1176`（`recommendedLane`）、`:1177`（`etaSeconds`）、`:707`（`done/finished/completed`） | **5** | **C 类**：整族无后端生产者，属司机段状态机族（与 `DriverLegStateMapper` 同批核） |
| 线路/车辆字符串族 | `Models.kt:1131`（`transferStation/handoverStation`）、`:1161/:1606`（`toPlate/nextPlate`）、`:1376`（`company`） | **4** | **C 类**：`handoverStation/nextPlate/company` 三面 0 命中；`transferStation`/`toPlate` 只有 `DemoData.kt` 在发（实测各 3 处）⇒ 演示面续命型，收口要先定"这屏该由哪个端点供给" |
| 寄/收件人字符串族 | `Models.kt:1000,1001,1002`（`senderName/senderMobile/senderAddress`）、`:1490`（`senderName/customerName`） | **4** | **C 类**：后端 0 命中、`DemoData.kt` 各 5/5/1 处在发 ⇒ 与商品族同法处理，但要先定端点（寄件人字段在 `AppSendOrderRespVO` 里叫什么） |
| 区域/空态文案 | `Models.kt:1712`（`provinceName/cityName/districtName`）、`:1717`（`regionText/areaText`）、`Repo.kt:828`（`runningEmptyText/emptyText`） | **3** | **C 类**：`regionText/areaText/emptyText` 三面 0 命中；`provinceName` 演示面 2 处在发 |
| 账号角色 | `Models.kt:227`（`multiStr("role","userRole")`） | **1** | **C 类**：后端与演示面都 **0 生产者** ⇒ 这个 `when` 恒走 `fallbackRole`。本轮不动它，因为它的收口方式是 F-3 ③（`AccountRole` 与 `ActiveDomain` 分离 + "accountRole 不许因 UI 页面切换而改"），改在 ② 就会和 ③ 的判据抢同一个锚点 |
| 坐标基准 | `Repo.kt:1118`（`coordinateSystem/crs`） | **1** | **C 类**：后端不发，App 自己按 `crs` 决定要不要换算 ⇒ 随 F-3 ⑭（route fingerprint）核 |
| **合计** | — | **65** | 与 census `class=="C"` 逐行相加一致（E 31 / D 11 / 真待收口 23） |

**锚点口径**：上表的行号是 2026-10-01 17:3x 由 `tools/default_value_census.py --write` 的 JSON 直接导出（`class=="C"` 过滤），
不是手抄；本轮之后任何在 `Models.kt`/`Repo.kt` 插行的改动都会让它重新漂移，下一轮开工必须先跑 `python -X utf8 tools/check_doc_anchors.py docs/android/DEFAULT_VALUE_AUDIT.md`。

### 四之三、F-2 轮（2026-09-30）原判据表：保留原文，但**行号已被四之二取代**

> 下面这张表是上一轮的记录。它的站点行号是 F-2 轮量的，本轮实测显示其中多处已经漂到别的内容
> （例：`Models.kt:491-493` 现在落在一个 `) {` 上、`:785` 现在是 `etaToNextStationMinutes` 而不是 `simRunning`、
> `:1170` 现在是一行注释）。**结论（哪族属哪类、为什么不在这轮改）仍然成立，行号不要再引用。**

| 族 | 站点 | 真实生产者在哪 | verdict |
|---|---|---|---|
| 天气实况/预报 | `Models.kt:279,280,285,291-293`、`Repo.kt:327-330,342,345` | 高德 `daytemp/nighttemp/dayweather/winddirection/windpower/temperature/humidity`、OpenWeather `temperature/weathercode/windspeed` | **E 类**（外部真实生产者，键可缺席）。按 B 判据处理，但"缺温度显示 0℃"要先定 UI 未给态口径 ⇒ 下一轮单独收口 |
| 里程口径 | `Models.kt:491-493,817-824,1917`、`Repo.kt:1116,1133,1168` | 后端实发 `distanceKm`（`AppSendReachabilityRespVO`）/`routeDistanceMeters` | **C 类别名**，与 §13 里程口径同族，已在 `check_eta_probes.sh` 的残留段登记（5 处） |
| 车道/推荐 | `Models.kt:1170,1171,1178`、`Repo.kt:1170` | `OrderTopologyRespVO.Candidate` 实发 `modeName:61 / chosen:69` | **C 类别名**，已在 `check_eta_probes.sh` 残留段登记（12 处） |
| 本地模拟器 | `Models.kt:296` | `RouteSource.kt:55,58` 自己写 `mock/simulated` | **D 类**（生产者=App 本地，不是后端契约），不属本轮"伪装后端字段" |
| 模拟器运行态 | `Models.kt:785 simRunning`（字段声明 `Models.kt:615`） | 读侧唯一，`simulated` 有本地生产者、`simRunning` 无 | 半 C：`simRunning` 是自造键，待下一轮删别名 |
| 商品/车辆/清单 | `Models.kt:328,331,508,1310,1311,1312,1434,1534` | 需逐端点核（商品 VO、车辆 VO、交接 VO） | **待逐端点三方核对**：每处要跑 census 的三步（VO 声明 → service 赋值 → 小程序读点）才能定删/改可空，本轮不猜 |
| 本地寄货草稿 | `SendScreen.kt:209,213,214` | `SendScreen.kt:188,192,193` App 自写自读 | **D 类**。但写侧 `pickup?.id ?: 0L` 把"未选站点"存成 0L，读回来就是真实 id ⇒ 记残留，与 §11 幂等/校验同批处理 |
| 任务段完成态 | `Repo.kt:707,1104` | `current/done/finished/completed` 均非后端键（后端 TaskItem 用 `status`） | **C 类**，属司机段状态机族，与 §10/§26-28 的 `DriverLegStateMapper` 同批核 |

**行号口径**：上表的 App 侧锚点在 2026-10-01 的 F-3 ⑩⑪⑮ 轮用 `tools/check_doc_anchors.py` 逐条重量过（该轮 `Models.kt`/`Repo.kt` 有插行，
不重锚就会自己烂）。唯一没重量的是"商品/车辆/清单"一行 —— 它的结论本来就是"待逐端点三方核对"，行号仍是 §四 初量值（实测已 ±3 漂移，
`Models.kt:331` 现对得上、`328/508/1310-1312/1434/1534` 只是同族附近的旧读数），随 #141 收口时一并重锚。

⚠ **上面这段"已重量过"的口径在 F-3 ② 轮又烂了一次**，原因是本轮自己动的就是 `Models.kt` 与 `Repo.kt`（六族的解析体与 KDoc 都在这两个文件里插了行）。
`check_doc_anchors.py` 本轮实测 `anchors=18 out_of_range=0 not_found=0 miss=0` —— **行号合法不等于内容对得上**：
`:491-493` 现在打印的是 `) {`、`:785` 现在是 `etaToNextStationMinutes`、`:1170` 现在是一行注释。
⇒ 这张表的历史结论保留（见 §四之三 的警告行），**当前有效的锚点一律看 §四之二**（它是 census JSON 直接导出的）。
"商品/车辆/清单"那一行的"待逐端点三方核对"本轮已经做掉一部分：商品 `stock`、车辆/档案 `plateNo`+`cargoCapacity`+`cargoCapacityKg`、
交接 `photoUrl`/17 键表、揽件 11 键表、候选方案 `modeName`/`legCount`/`chosen` —— 逐端点表在 `CONTRACT_EVIDENCE.md` §九；
剩下没做的（`senderName` 族、`transferStation/toPlate` 族、`regionText` 族）已在 §四之二按实测行号登记为 C 类待收口。

**为什么不一遍改完 398 个 B 类站点**：F-2 明确要求"不要一次性制造 38 个编译问题""不要扩大战线"。
可空化会沿调用点传播到 UI，无差别改会退化成大规模 UI 重构（第 17 步禁止项）。
本步的强制手段是 `DV-k` 基线护栏：**132 这个数字只能降不能升**，加上逐族的三方核对，
下一轮按族推进时旧轮成果不会被回退（每族都要配负控制，像本轮 16 条一样逐条证明能响）。

**F-3 ② 轮的读数**：`DV-k` 由 **132 降到 116**（判据里那条"≤116"就是新下限，只能继续往下），
负控制由 16 条扩到 **31 条**（`✔=31 ✘=0 △=0`，基线与恢复双绿），判据由 DV-a…DV-l 扩到 **DV-a…DV-s + GUARD + GUARD2**（exit 0）。
护栏按承诺生效了：本轮六族每族都配了注入（真键读法被换回别名 / 演示面把撤掉的键发回来 / 用例注释掉一条 /
`CargoGauge` 的可空参数改回非空 / 页面裸读 `usedKg`），逐条证明能响，没有一条是"注进去不红"的。

## 五、复现命令

```bash
cd cargo-post-android
python tools/default_value_census.py --write          # 基数表（A/B/C 分类 + 逐站点 keys 与后端声明命中）
bash tools/check_default_value_probes.sh             # F-2：DV-a…DV-l + GUARD；F-3 ②：DV-a…DV-s + GUARD + GUARD2，exit 0
bash tools/default_value_negative_controls.sh        # F-2 16 条 / F-3 ② 31 条注入逐条变红 + 基线/恢复双绿
./gradlew -Dorg.gradle.jvmargs="-Xmx3072m -XX:MaxMetaspaceSize=768m -Dfile.encoding=GBK" :app:testDebugUnitTest --rerun
# F-2 轮实测：classes=35 tests=338 failures=0 errors=0 skipped=0
# F-3 ② 轮实测（2026-10-01 17:33:46 的 XML 产物）：classes=42 tests=410 failures=0 errors=0 skipped=0
```

C 类形态拆分（§四之二那张表的来源）是同一条 census 命令的 JSON，过滤规则逐字写在 §二之二，
不额外手挑站点；逐端点的真键与行号在 `docs/android/CONTRACT_EVIDENCE.md` §九。

设备侧结论：**NOT VERIFIED ON DEVICE**（`DEVICE_UNAVAILABLE`），两轮全部结论只到 JVM 契约测试与静态判据这一层。
F-3 ② 轮新增的六族文案（`库存未给` / `载重未给` / `认证状态未给` 等）在 JVM 里被断言过字符串，
但**从没在任何屏幕上渲染过** ⇒ 长什么样、会不会压坏行高，仍是设备侧空白。
