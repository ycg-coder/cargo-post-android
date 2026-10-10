# 数字默认值 A/B/C/D 分类台账（F-2 第 5 步）

> 本轮日期：2026-09-30。所有数字都是**当轮实测输出**，不是估计值；复现命令见文末。
> 判据簿：`tools/check_default_value_probes.sh`（DV-a…DV-l + GUARD）。
> 负控制簿：`tools/default_value_negative_controls.sh`（16 条注入，实测 ✔=16 △=0 ✘=0）。
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

## 四、C 类 32 处的 verdict（本轮未动的部分，附不动的理由）

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

**为什么不一遍改完 398 个 B 类站点**：F-2 明确要求"不要一次性制造 38 个编译问题""不要扩大战线"。
可空化会沿调用点传播到 UI，无差别改会退化成大规模 UI 重构（第 17 步禁止项）。
本步的强制手段是 `DV-k` 基线护栏：**132 这个数字只能降不能升**，加上逐族的三方核对，
下一轮按族推进时旧轮成果不会被回退（每族都要配负控制，像本轮 16 条一样逐条证明能响）。

## 五、复现命令

```bash
cd cargo-post-android
python tools/default_value_census.py --write          # 基数表（A/B/C 分类 + 逐站点 keys 与后端声明命中）
bash tools/check_default_value_probes.sh             # DV-a…DV-l + 4 条 GUARD，exit 0
bash tools/default_value_negative_controls.sh        # 16 条注入逐条变红 + 基线/恢复双绿
./gradlew -Dorg.gradle.jvmargs="-Xmx3072m -XX:MaxMetaspaceSize=768m -Dfile.encoding=GBK" :app:testDebugUnitTest --rerun
# 实测：classes=35 tests=338 failures=0 errors=0 skipped=0
```

设备侧结论：**NOT VERIFIED ON DEVICE**（`DEVICE_UNAVAILABLE`），本轮全部结论只到 JVM 契约测试与静态判据这一层。
