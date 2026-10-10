---
name: ANDROID_V2.1_CHECKLIST
created_at: 2026-09-30
command_file: docs/android/ANDROID_COMMAND_V2.1.md
command_body_bytes: 17904
command_sections: 35
census_cmd: bash tools/v21_requirement_census.sh /tmp/v21_census.txt
census_rc: 0
census_rows: 113   # = 产物里 `SUMMARY|rows=113` 那一行的读数（SUMMARY 自身未计入，文件实为 114 行）
census_taken_at: 2026-09-30 18:0x（本轮，源码未改动前）
status: ACTIVE
---

# V2.1 指令完成度清单（唯一合法依据 = 已归档的指令原文）

## 0. 这份文件的用法（硬约束，违反即作废）

1. **完成度只允许按 `COMMAND_ID` + 该节点名的要求书写**。行号、段落印象、旧台账里的说法都不是依据。
2. **禁止凭记忆写"§xxx 已完成"**。要引用某个节号，必须先从 `docs/android/ANDROID_COMMAND_V2.1.md` 里
   精确读出来，复核命令（实测可用）：
   ```bash
   cd cargo-post-android
   grep -cE '^={5,}$' docs/android/ANDROID_COMMAND_V2.1.md   # 分隔块
   grep -nE '^(一|二|三|四|五|六|七|八|九|十|十一|十二|十三|十四|十五|十六|十七|十八|十九|二十|二十一|二十二|二十三|二十四|二十五|二十六|二十七|二十八|二十九|三十|三十一|三十二|三十三|三十四|三十五)、' docs/android/ANDROID_COMMAND_V2.1.md
   ```
3. **两套编号系统不可互换**：
   - `v2.0` = 桌面 `新建 DOCX 文档 (3).docx`，阿拉伯数字 **§0–§130**（跨表见 `ANDROID_PARITY_AUDIT_V2.md` §70）；
   - `v2.1` = 本清单，中文数字 **一–三十五**（35 节，先前只存在于会话里，本轮才落档）。
   旧台账中形如"指令 §52 / §54"指 **v2.0**；形如"§六 / §七 / §三十一"指 **v2.1**。写状态时必须带前缀。
4. **状态四态**：`✅ 已完成` / `⚠ 部分完成` / `❌ 未完成` / `⏸ 环境或设备阻塞`。
   任何一格从 ⚠/❌ 改成 ✅，必须**在同一格补上本轮实测命令与其输出**，且命令是在源码改完之后跑的。
5. **"0 命中"必须连同 pattern 与根目录一起抄**。本轮 census 第一版就因此错过：本机 grep 不认 `-rOE`
   里的 `-O`，整列计数命令直接报 `unknown option` 而输出 0，看起来像"全部不存在"；
   另一条同源假 0：把 `--include=*.kt --include=*.xml` 套在**显式给出的单文件**（`app/build.gradle.kts`）上时，
   `.kts` 不在白名单 ⇒ `compileSdk` / `targetSdk` / `navi-3dmap` 三行全读 0，实际各命中 1/1/2。
   ⇒ `tools/v21_requirement_census.sh` 现在带 **GUARD 行**（计数管道自检，坏了 exit 3）且对显式文件不套 `--include`。

## 1. 本轮 census 读数（`bash tools/v21_requirement_census.sh /tmp/v21_census.txt`，rc=0，113 行）

| 读数 | 值 | 说明 |
|---|---|---|
| `GUARD\|pipeline` | measured=1 / expect=1 | 计数管道可用（否则本轮所有 0 不采信） |
| `V21-01` 判据簿 | `check_*probes.sh` = **12**，`*_negative_controls.sh` = **13** | §一"重新扫描"的可交付物 |
| `V21-30` 测试类 | 18 个点名类里 **2 present**（`ApiCancellationTest` 5 例 / `DemoExceptionIsolationTest` 7 例），**16 ABSENT** | §三十 的账面进度 |

**同一条命令的第二遍（2026-09-30 19:4x，F-2 步 ②③④⑤ 落地之后复测，`bash tools/v21_requirement_census.sh /tmp/v21_census_now2.txt` → rc=0，`SUMMARY|rows=113`）**：

| 读数 | 值 | 与上一遍的差异 |
|---|---|---|
| `V21-01` 判据簿 | `check_*probes.sh` = **14**，`*_negative_controls.sh` = **15** | +2 本：`check_contract_defaults_probes.sh`(Q-*/B-*) 与 `check_default_value_probes.sh`(DV-*)，配套两份负控簿 |
| `V21-22` `val amount\|itemFee\|distanceFee: Double?` | `code=`**4**（上一遍 0） | F-2 第 3 步落了可空声明 |
| `V21-23` `optBoolean("…", true)` | `hits=`**2** / `code=`**0** | F-2 第 4 步把两处生效读法改掉，剩下 2 处是 KDoc 里"旧读法长什么样"的说明 ⇒ **`hits` 会把修好的事读成没修**，故本轮给普查每行补 `code=`（滤掉注释行的生效行数） |
| `V21-30` 测试类 | present **4 / 18**（新增 `QuoteNullSafetyTest` 7、`NearbyBooleanContractTest` 6） | F-2 第 3、4 步各一本 |

⚠ **`hits` 与 `code` 不可互引**：`hits` 是 `grep -o` 的**出现次数**（一行多次算多条），`code` 是 `grep -n` 滤注释后的**行数**。
本轮补 `code=` 之后，`hits=2 / code=0` 这种组合就是"字面还在、但只在注释里"的正式表达方式 —— 不要为了消掉那个 2 去删注释（注释是三方实测的证据）。

## 2. 逐节清单

| ID | 节号（v2.1，逐字标题） | 点名要求（原文符号） | 本轮实测（同一份 census） | 状态 | 归属 |
|---|---|---|---|---|---|
| V21-01 | 一、先重新扫描当前工程，不要相信旧报告 | 17 个指定文件 + 18 组全项目搜索 | 判据簿 12 + 负控 13 在册；本轮对 §二–§三十四 的每条点名符号重新测过（见下） | ✅ | 本轮 |
| V21-02 | 二、P0：彻底修复 USER / DRIVER 域隔离 | `RouteDomain` / `domainOf()` / `canEnter()` / USER_ONLY 与 SHARED 名单 | `enum class RouteDomain`=**0**、`fun domainOf`=**0**、`fun canEnter`=**0**、`USER_ONLY`=**0**、`SHARED`=**0**；`DRIVER_ONLY`=3 命中/2 文件 | ❌ | 未在 F-2 十八步内（与"不要重做双域架构"冲突，见 §4） |
| V21-03 | 三、P0：修复 DriverShell 的严重角色污染 | 取消 `AppState.setRole(Role.USER)`；`AppDomain` / `accountRole` 双状态 | `AppState.setRole(Role.USER)`=**1 处生效**（`ui/driver/DriverShell.kt:37`，读码确认非注释）；`enum class AppDomain`=0、`activeDomain`=0、`accountRole`=0 | ❌ | 同上；本轮只登记不复修 |
| V21-04 | 四、P0：Root 启动角色不能只相信本地 Prefs | `SESSION_BOOTSTRAPPING` / 服务器确认前不进业务 Shell / "未知不等于否定" | `startDestinationFor`=3 命中/2 文件（仍按 `Prefs.loggedIn, Prefs.role` 直接建壳）；`BOOTSTRAPPING`=0；但 `restoreSession`=2、`reconcileRole`=3、`serverSaysDriver`=3 在册（V-2b 那一轮的三态对账） | ⚠ | 半个：三态"未知≠否定"已有，启动门与 BOOTSTRAPPING 态没有 |
| V21-05 | 五、P0：重新整理 SessionManager | 唯一 `SessionManager` + `SessionSnapshot(loggedIn,userId,accountRole,driverId,activeDomain)` | `SessionManager`=**0**、`SessionSnapshot`=**0**；角色仍由 `Prefs` / `AppState` / `Repo` 三处各自维护 | ❌ | 不在 F-2 十八步内 |
| V21-06 | 六、P0：修复 Api.kt 的 Throwable 吞异常 | 只允许 IOException/ApiException 回落演示数据；Throwable 直抛 | `catch (x: Throwable)`=4 命中/4 文件，其中**生效 2 处**（`core/net/Attempt.kt:22` 收成 Result、`data/repo/ActionGate.kt:63` 清理后 rethrow，均在白名单）、**注释 2 处**（`Api.kt:232`、`LoginViewModel.kt:63`）；`Api.kt` 内 IOException 出口=7、ApiException 出口=5 | ✅ | F-1 已收（判据 TH-a…TH-l + 18 破口负控） |
| V21-07 | 七、P0：所有 suspend 层正确处理 CancellationException | `catch(CancellationException){throw}` 前置 + 统一 helper | `attempt {`=12 命中/7 文件；`catch (x: CancellationException)`=44 命中/4 文件；`safeApiCall`=**2**（只在 `Attempt.kt` 自己的注释/命名里）；`\bapiCall\b`=1（仅定义，**生产调用点 0**）〔**2026-10-01 F-2 第 8 步复测**（留档 `/tmp/f2_8_ledger_census.log`，与 ⑦ 那份逐行 `diff` 过）：`attempt {` hits=**8** / files=**7** / **生效 7**（那 1 行注释是 `Repo` KDoc 写的"旧写法"原文）· `safeApiCall` hits=**14** / files=4 / **生效 9** · 旧名 `\bapiCall\b`=**0/0/0** ⇒ 改名彻底、无残留双真相；helper 定义 **1** / 生产调用点 **6**（Repo **5** + 页面 **1**，⑧ 之前是 **0**）；`catch (x: CancellationException)` 那 **44** 未动（本步没碰取消穿透，它由 F-1 的 TH-* 钉着）。判据 `check_safe_call_probes.sh` **15 行读数 / 34 闸门 rc=0**，负控 `safe_call_negative_controls.sh` **✔=20 / ✘=0 / △=0**〕 | ⚠ | 取消穿透已收（F-1）；**helper 已改名并有真实调用点（F-2 ⑧，只迁指令点名的 5 个高风险点）**；⚠ **其余 33 个 Repo 读接口今日一个都没过这条形状** —— 是指令给的规模上限（"不要为了一个 helper 制造 38 个编译问题"），不是漏检 |
| V21-08 | 八、P0：重新设计 AMap Navigation 生命周期 | `NavigationService` 单例持有、页面只观察、离开不自动 stop、`NavigationSession` | `NavigationService`=**0**；`NavigationSession`=17 命中/3 文件（引擎内部会话，不是应用级服务）；`NavigationEngines.create`=1（仍在 `DriverSdkNavCard` 的 `remember{}`）；`engine.release()`=1（`DriverSdkNavCard.kt:68` onDispose） | ❌ | 引擎生命周期仍随页面，正是本节点名的形态 |
| V21-09 | 九、P0：导航必须绑定当前真实路线 | `AcceptedRouteSession` + `routeFingerprint` 校验后才 `startGpsNavi()` | `AcceptedRouteSession`=**0**、`routeFingerprint`=**0**；`startGpsNavi`=4 命中/3 文件（无指纹闸门） | ❌ | 不在 F-2 十八步内 |
| V21-10 | 十、P0：修复 AMap 算路回调关联 | `requestId`/`generation` 严格代际 + `errorCode==0` 才算成功 | `generation`=**0**；`requestId`=1；`errorCode`=2 命中/1 文件（`AmapNavigationEngine.kt`）；`CompletableDeferred`=3 命中/1 文件（单槽） | ⚠ | 有 errorCode 判定，无代际号 |
| V21-11 | 十一、P1：AMap SDK 版本审计 | 先取证再升级；升级后查 `abiFilters`；升不动记 `AMAP_NAV_VERSION_BLOCKED` | `navi-3dmap`=2 命中/1 文件（`app/build.gradle.kts`，仍 10.0.600_3dmap10.0.600）；`abiFilters`=**0**（未显式声明） | ⚠ | 版本取证在 N-1 那一轮，"是否升到 V11.3.100"未做 |
| V21-12 | 十二、P1：真正处理 Android 16 配置 | 评估 compileSdk/targetSdk 36，不兼容记真实 blocker | `compileSdk`=1 命中（**实测 `= 35`**，`app/build.gradle.kts:63`）、`targetSdk`=1 命中（**实测 `= 34`**，:68）、`minSdk = 26`（:67） | ❌ | 数字未动；不在 F-2 十八步内 |
| V21-13 | 十三、P1：RouteRepository 不能继续无限期内存缓存 | `RouteCacheEntry`（含 graphVersion/expiresAt）+ TTL + 线程安全 | `RouteCacheEntry`=**0**、`expiresAt`=**0**、`graphVersion`=**0**；`LinkedHashMap`=2、`MAX_ENTRIES`=2（`core/map/RouteRepository.kt:17,20`，只有条数上限与插入序淘汰）；`Mutex`=5/1 文件、`ConcurrentHashMap`=3/1 文件（都不在 RouteRepository） | ❌ | 无 TTL、无图版本校验、非线程安全 |
| V21-14 | 十四、P1：路线缓存区分"真实路线"与"导航会话" | 拆出 `NavigationSessionStore` | `NavigationSessionStore`=**0** | ❌ | 不在 F-2 十八步内 |
| V21-15 | 十五、P1：Locator GPS 完整性 | mock 定位不进正常轨迹；`heading()` 无 bearing 返回 null | `isFromMockProvider`=**0**；`fun heading`=1 命中，实为 `Locator.kt:217` `fun heading(location: Location): Double = if (hasBearing) … else 0.0` ⇒ **正是本节点名的缺陷**（0° 与"未知"不可分） | ❌ | 不在 F-2 十八步内 |
| V21-16 | 十六、P1：GPS provider 去重 | `LocationQualitySelector` + 距离/时间双去重 | `LocationQualitySelector`=**0** | ❌ | 同上 |
| V21-17 | 十七、P1：DriverLocationService.start() 禁止吞异常 | `StartLocationResult` 五态密封结果 | `StartLocationResult`=**0**；`runCatching`（main 全量）**三种口径实测**：出现次数 **44**（`grep -rho`）/ 行数 **43**（`grep -rn`）/ 滤注释后生效行 **34** / **22** 个文件 ⇒ 台账里那个"仍剩 34"与本行的 44 **不是矛盾**，抄数必须连口径一起抄 | ❌ | 与 F-2 第 9 步（runCatching 审计）同族，但五态结果没做；第 9 步要动的基数取 **34** |
| V21-18 | 十八、P1：权限流程完善 | 按操作触发；后台定位单独引导 | `requestMultiplePermissions\|RequestPermission`=4 命中/2 文件；`BACKGROUND_LOCATION`=1（Manifest 已声明） | ⚠ | 有运行时申请，未见"按操作分步引导"的成文流程 |
| V21-19 | 十九、P1：AlertEngine 完全按域隔离 | 域内只打域接口；去掉一次性 `primed`，用可推进水位 | `primed`=**4 命中/1 文件**（`notify/AlertEngine.kt:17,31,41,113`，首轮一次性吞历史消息）；`lastSeenNotifyId`=9/3、`lastSeenDriverMsgId`=12/3 | ❌ | 水位有、域隔离与"确认后推进"没有 |
| V21-20 | 二十、P1：NotifyWorker 不能所有错误都 Result.success() | 网络→retry、认证失效→failure、无新消息→success；`BackoffPolicy.EXPONENTIAL` | `Result.retry`=**0**；`Result.failure`=2/1；`Result.success`=3/2（`notify/NotifyWorker.kt:24` 无条件 `return Result.success()`）；`BackoffPolicy`=**0** | ❌ | F-1 收工台账里就点名的"没做"项 ⇒ **F-2 第 10 步** |
| V21-21 | 二十一、P1：TraceScreen 修复刷新一致性 | 自动/手动刷新互斥；`lastSuccessfulSyncAt` 与 `lastAttemptAt` 分开 | `autoRefresh`=4/1（`ui/user/TraceScreen.kt:85-87` `while(autoRefresh){ delay(30_000); load() }`，无 Mutex/在飞保护）；`lastSuccessfulSyncAt`=**0**；`updatedAgo`=3/1 且 `:72` 在 load 起点赋值 ⇒ 失败也会刷新"更新时间" | ❌ | 不在 F-2 十八步内 |
| V21-22 | 二十二、P1：Repo parser 严禁"缺失就当 0" | `QuoteResult` 金额改 `Double?` 或抛 `ContractParseException` | **2026-09-30 F-2 第 3 步复测**：`val amount\|itemFee\|distanceFee: Double?`=**4 生效行**（步 ① 那遍是 0；这 4 行 = QuoteResult 的 amount/itemFee/distanceFee **3 处** + §五 那轮 `EarningItem.amount` 一处，普查按字面数、跨契约不区分，本契约的三条由 Q-a=5 逐类体钉住）；`ContractParseException`=hits 1 / **code 0**（唯一那一处是说明文字，我们走的是"改可空"这一支，不是抛）；`QuoteResult`=9 命中/3 文件。判据簿 `check_contract_defaults_probes.sh` Q-a…Q-f 实测 Q-a=5、Q-b/Q-b2/Q-c1/c2/c3/Q-d/Q-d2=**0** | ✅ | **F-2 第 3 步已收**（Q-* 11 条破口负控 ✔） |
| V21-23 | 二十三、P1：NearbyResult 严禁缺失 boolean 默认 true | `inService`/`located` 缺失=null，UI 不得渲染成"运营中/已定位" | **2026-09-30 F-2 第 4 步复测**：`optBoolean("…", true)`=hits **2** / **code 0**（那 2 处是 `Models.kt:831/:849` KDoc 里"旧读法长什么样"的说明文字，滤注释判据不计 ⇒ 生效读法归零）；`inService`=11 命中/3 文件，其中生效 4 行。判据 B-a=2、B-b/B-d/B-e3/B-f=**0**、B-e1=1、B-e2=2、B-g=6 | ✅ | **F-2 第 4 步已收**（B-* 8 条破口负控 ✔） |
| V21-24 | 二十四、P1：分钟字段五组 alias 做最终 contract audit | 每个别名找到 producer 才保留，找不到就删 | **2026-09-30 F-2 第 2 步复测**：五个名字的 `hits` 仍非 0（`plannedMinutes` 12/`waitDepartureMinutes` 8/`totalMinutes` 2/`plannedDurationMinutes` 5/`walkMinutes` 8），但那是**字段名本身**（模型声明 + 页面消费 + 注释），本节点名要审的是**多候选读法里的第二候选键**：`T-aj`=**0**、`T-aj2`（四个已删别名键字面量，闭包判据）=**0**、`T-aj3`（演示面）=**0**、`T-aj4`（五处真实键单键读法）=7、`T-aj5`（单键 `*Minutes` 读法总量）=16、`T-aj6`（`MinutesContractTest` 用例）=6 | ✅ | **F-2 第 2 步已收**；⚠ `hits` 与 `code` 两列口径不同（出现次数 vs 生效行），不可互引 |
| V21-25 | 二十五、P1：百度外跳坐标修复 | GCJ02 → BD09 → URI，统一走 `CoordinateTransformService`，禁止手工偏移 | `CoordinateTransformService`=**0**；`gcj02ToBd09()` **已存在**（`core/map/CoordinateSystem.kt:96`）；`openBaidu`=2/1，`DriverSdkNavCard.kt:282` 把 `${to.lat},${to.lng}` 原样塞进 `destination=latlng:` ⇒ **转换函数没被这条 URI 使用** | ❌ | 缺陷确切；不在 F-2 十八步内 |
| V21-26 | 二十六、P1：Splash 取消无意义附近公交请求 | 闪屏只做 session restore | `nearbyBuses(null)`=**1 命中**（`ui/BusPostRoot.kt:99` `async { attempt { Repo.nearbyBuses(null) } }`） | ❌ | 同上 |
| V21-27 | 二十七、P1：Deep Link 安全 | parse → 白名单 → 域闸 → 参数校验 → navigate | `deepLink`=6/4；`nav/ShellDecision.kt:34 deepLinkTarget(route, role)` 已在 `BusPostRoot.kt:84` 前置；但 `USER_ONLY`=0 ⇒ 只挡"用户会话进司机路由"，**挡不住司机进用户路由**；且 `runCatching { nav.navigate(it) }` 吞导航异常 | ⚠ | 半边有闸 |
| V21-28 | 二十八、P2：Manifest 权限审计 | 逐项找真实使用，没有就删 | `uses-permission`=**13 处**（Manifest 实测），逐项对表未做 | ❌ | 不在 F-2 十八步内 |
| V21-29 | 二十九、P2：Repo 按领域拆分 | UserRepository/DriverRepository/OrderRepository/ParcelRepository/RouteRepository/LocationRepository/NotificationRepository | `UserRepository`=**0**、`DriverRepository`=**0**；`object Repo`+`class Repo`=2/2（仍是单一 façade）；`RouteRepository` 有（属地图层，不是本节点名的第七拆法） | ❌ | 同上 |
| V21-30 | 三十、P2：建立严格测试 | 点名 18 个测试类 | **2026-09-30 F-2 步 ②–⑤ 复测**：present **4 / 18**（`ApiCancellationTest` 5、`DemoExceptionIsolationTest` 7、**`QuoteNullSafetyTest` 7**、**`NearbyBooleanContractTest` 6**），ABSENT 14；不在 18 名单内、本轮另立的 `MinutesContractTest` **6** 与 `EarningsContractTest` **8** 由 `T-aj6` / `DV-l` 各自钉住。⚠ `tests=` 是普查从 `@Test` 注解现读的**源码计数**，与 gradle XML 的用例数是两个口径（后者见状态板 35 类 / 338 例）〔**2026-10-01 F-2 第 7 步复测**：名单实测 **19** 名（普查脚本 V21-30 循环本轮加进 `VerificationFailureTest` 一行），present **5 / 19**（新增 `VerificationFailureTest` **8** 例），ABSENT **14**；`UserCreateIdempotencyTest` 于 F-2 ⑥ 建成、**不在这 19 个名单内**，由 `U-*` 那本钉住。读数以 `bash tools/v21_requirement_census.sh` 的 `V21-30\|…` 行为准，本轮全表 **117 行**〕〔**2026-10-01 F-2 第 8 步复测**：名单实测 **20** 名（指令 §三十 点名的 **18** 本 + ⑦ 加 `VerificationFailureTest` + ⑧ 加 `SafeApiCallContractTest`），present **6 / 20**（新增 `SafeApiCallContractTest` **12** 例，普查从 `@Test` 注解现读），ABSENT **14**；本轮全表 `SUMMARY\|rows=118`。⚠ **更正 ⑦ 那条口径**：`SafeApiCallContractTest` **不在**指令点名的 18 本里，它是本步（§七 / V21-07）新立后**加进对表**的 —— ⑧ 开工时把它说成"V21-30 点名的那本"是**把"我加的"读成"指令点的"**，与 F-1/E-1 那几次"键名归一化造出假差异"同族。留档 `/tmp/f2_8_ledger_census.log`（与 ⑦ 那份 `diff`：唯一新增行就是 `V21-30` 的 `SafeApiCallContractTest → present, tests=12`）〕 | ⚠ | 8 本里已落 3 本（⑧ 起：`SafeApiCallContractTest` 已立）；`ThirdPartyNoticeTest`/`NotificationWorkerResultTest` 等仍未建 |
| V21-31 | 三十一、硬门禁 | 正式路线无 Haversine/直线；导航无 `delay(4000)`/`stepIndex+1`；正式代码不吞 Throwable；suspend 不吞取消；司机不得 `setRole(USER)` | `delay(4_000)`=**0**；`stepIndex + 1`=1 命中且**该处是文档注释**（`AmapNavigationEngine.kt:35` 写"没有定时器、没有 stepIndex + 1"）⇒ 生效 0；`haversine/Haversine`=11 命中/6 文件（口径见既有真实性判据簿，不在此重述）；**`AppState.setRole(Role.USER)`=1 处生效 ⇒ 本门禁红** | ⚠ | 5 条里 4 条有判据、角色污染这条**实测未清零** |
| V21-32 | 三十二、Build 配置 | `gradlew` 可执行位；构建产物不进源码包 | `stat -c %A gradlew` = **-rwxr-xr-x**（可执行位正常）；`.gitignore` 存在且含 gradle 条目；源码 ZIP 的白名单化未做 | ⚠ | 打包脚本侧，不在 F-2 十八步内 |
| V21-33 | 三十三、验证流程 | 构建失败分四类：SOURCE/DEPENDENCY/ENVIRONMENT/DEVICE | **已落地（2026-10-01 F-2 第 7 步 / D-039），但落点与字面不同并已登记**：`app/src/main` 里 `VerificationFailure`=**0**、四类符号=**0**（这是**红线本体**，不是未做）；真实落点三处 —— 规则表 `tools/verification_failure_rules.txt`（**16** 行 / **4** 类，出处逐行回核：指对 **9** / 指错 **0**）、bash 分类器 `tools/classify_verification_failure.sh`（被 `verify_device.sh` 两个分支调用；判不进唯一类就出 `AMBIGUOUS`/`UNCLASSIFIED`，**不猜**）、**test 源集**类型 + `VerificationFailureTest` **8** 例。证据样例 `tools/fixtures/verification-failure/` **7** 份（6 正向 + 1 反例 rc=4，全部本机真实跑出的日志、`cmp` 可证同字节）。判据簿 **18 行读数 rc=0**、负控 **13 ✔ / 0 ✘ / 0 △ + 1 ◦**。⚠ **§三十三 七步里的第 6 步 `assembleRelease` 仍 `BLOCKED`**（HTTPS 闸门 `app/build.gradle.kts:171`；分类器给它 `UNCLASSIFIED` 是**设计意图** —— 策略拒绝不是四类构建失败，**没有加规则行也没有绕闸门**） | ⚠ | 分型已完成；**第 6 步 release 与第 11 步设备边界仍在册**（`NOT VERIFIED ON DEVICE`） |
| V21-34 | 三十四、阶段结束状态 | SOURCE_COMPLETE / BUILD_VERIFIED / DEVICE_VERIFIED 分开写，设备没验就写 NOT VERIFIED ON DEVICE | 台账里三态分写已在做；源码里 0 命中（本属状态口径，不该进源码，如实标注） | ⚠ | F-2 第 17 步继续按此口径 |
| V21-35 | 三十五、这轮不要做 | 8 条禁止事项 | 本轮未做大规模 Compose 迁移 / 未重写整个 Repository / 未接 Valhalla / 未接 MapLibre 导航 / 未碰小程序 / 未改调度算法 / 未改 GraphHopper 后端 / 未用 fake route 顶功能空缺 | ✅ | 持续约束 |

## 3. F-2 十八步 ↔ 本清单的对应（本轮真正动手的范围）

| F-2 步 | 落在哪些 V21 行 | 本轮动作 |
|---|---|---|
| 1 落档 v2.1 原文 | V21-01 | 已完成：`ANDROID_COMMAND_V2.1.md`（27,323 B / 1,618 行 / 35 节，逐字节回环校验通过）+ 本清单 + `tools/v21_requirement_census.sh` |
| 2 五处 Minutes 别名 | V21-24 | **已完成**（F-2 第 2 步）：五处逐端点四面核对（Android 解析 / 后端 VO+producer / 小程序消费 / 演示面下发）后**删除自造第二候选名**，只留后端实际键；配套 `MinutesContractTest`。**判据**：`check_eta_probes.sh` 新立 **T-aj 家族**，收工实测 `T-aj 0 · T-aj2 0 · T-aj3 0 · T-aj4 7 · T-aj5 16 · T-aj6 6`，GUARD 10，簿末 exit 0。⚠ **别名删净 ≠ 默认值形状修净**：这五处里 `plannedMinutes`（`Models.kt:469 Int = 0` / 读法 `:500 multiNum(…).toInt()`）与 `plannedDurationMinutes`（`:1337` / `:1381` 同形）仍是 §五 意义上的 **B 类缺键当 0**，落在 **DV-k=132 基线内**、归 §五 下一轮逐处核（本轮不顺手改，改它要连后端口径一起动）；逐键证据在 `CONTRACT_EVIDENCE.md:15/:18` |
| 3 `QuoteResult` 金额 | V21-22 | **已完成**：五个数值字段改可空 + 缺键走 `multiNumOrNull` 哨兵；界面只走 `amountText`/`amountValueText`/`feeBreakdownText`。**⚠ 与指令字面有一处刻意偏差**：指令写"UI 显示『暂无报价』"，实际文案是 **"金额未给" / "未给"** —— 因为"暂无报价"暗示"后端算过了、结果是没报价"，而真实情形是**这个键不在报文里**，两者是不同的事实（A 类真零值与 B/C 类的区别正是 §五 的主题）。APK 内实测："金额未给" **2** 处、"后端无此口径" **1** 处、**"暂无报价" 0 处**（`unzip` 全部 15 个 dex 逐串 grep，不是台账自述）；`QuoteNullSafetyTest` **7** 例。**判据**：`check_contract_defaults_probes.sh` 的 **Q-\*** 族，收工实测 `Q-e1 3`（`QuoteResult` **类体内**那三个 `String` getter，恰 3 不判"至少"）· `Q-e2 4`（`SendScreen.kt` 生效使用行，闭包判据），簿末"缺键默认值判据通过" exit 0，负控 **22 ✔ / 0 ✘ / 0 △ + 1 ◦**（`KEEPFAIL=0`） |
| 4 `optBoolean(..., true)` | V21-23 | **已完成**：`NearbyResult.inService/located` 改 `Boolean?`（UNKNOWN 不渲染成"运营中"）；`NearbyBooleanContractTest` **6** 例。**判据**：同一本簿的 **B-\*** 族 exit 0；`v21_requirement_census.sh` 该 pattern `hits=2 / code=0` —— 那 2 处命中**全在 KDoc 里**（写着"原来这里塌成 UNKNOWN"的反面说明），**不是残留破口**，⚠ 不得为了消掉 2 去删注释 |
| 5 数字默认值 A/B/C/D | V21-22 全族 | **已完成（本轮只做普查 + 收口 Earnings 一族）**：`tools/default_value_census.py` 实测 **472** 处数值默认位，分类 **B 398 / C 74 / A 0 / D 逐处**；A=0 的结构性依据：后端 App RespVO 全用包装类型，"真零值"在这些接口里不存在。**判据**：`check_default_value_probes.sh` exit 0（`DV-a 0 · DV-b 0 · DV-c 0 · DV-d 12 · DV-e 9 · DV-f 2 · DV-g 8 · DV-g2 2 · DV-h 0 · DV-i 0 · DV-j 1 · DV-k 132 · DV-l 8`，GUARD `1/29/34/7`），负控 **16 ✔**；`EarningsContractTest` 已立。**在册待逐族做**的 32 处 C（天气/里程/车道/商品/草稿）**本轮未动**，DV-k 只许从 132 往下减、不许往上 |
| 6 用户端 create 幂等 | —（v2.0 §52 的镜像） | **已完成**（2026-09-30）：**复用既有 `ActionGate` 类**（该类一字未改，mtime 20:05:17 早于本轮 20:49 起的编辑窗口），但**新立一把实例** `userCreateGate`（`Repo.kt:453`）—— 与司机域 `actionGate`（`:1160`）**各一把**，这是硬规则 1 要求的形状（共用一把=用户下单去挡司机动作）。两个出口 `createProductOrder`（`:506/:516`）/ `createSendOrder`（`:558/:559`）都经 `gatedUserCreate`（`:488-492`）；键 = `create:` + SHA-256(**端点 + 登录身份 + 整份报文按 key 排序**)（`:464-476`），抄后端 `UserIdempotentKeyResolver.java:21-25` 的**维度**、**只存摘要不存明文**。⚠ **刻意的偏差并已写清**：本机键**不可能**等于服务端那个 MD5（拿不到 `userId/userType` 的服务端表示，凑一个就是假造 API）⇒ **本机放行 ≠ 后端放行**，由 `后端回 900 时原样透出那句话且不再由本机代拦` 这条用例钉住。`AppAddressController.java:32` / `AppFeedbackController.java:28` 后端**零** `@Idempotent` ⇒ **刻意不加闸**，登记为 `U-j`（不判红、无负控）。**实测**：`UserCreateIdempotencyTest` **10** 例（连点 5 次 ⇒ `requestCount==1`、业务失败后立刻可重试、3 个并发 async 只出 1 个请求）；判据 `check_idempotency_probes.sh` 22 个读数到目标（`U-a 2 · U-b 2 · U-c 2 · U-c2 2 · U-c3 2 · U-d 3 · U-e 2 · U-f 0 · U-g 1 · U-h 10 · U-i 2/2 · U-j 4`，`I-*` 十条一字未动）exit 0；负控 `idempotency_negative_controls.sh` **25 行 = 24 ✔ / 0 ✘ / 0 △ + 1 ◦**，基线与恢复后各判通过一次；全量 **36 类 / 348 例 / 0 失败**（36 份 XML mtime 21:07:20）。⚠ **包内新鲜度是本轮新学的一条**：`assembleDebug` 打印 `packageDebug` 执行过而盘上 APK 仍是 19:33 那份、15 个 dex 里 `userCreateGate` **0** 命中，`--rerun-tasks`（37 executed）才得到 21:13:59 那份（**同字节 113,863,001 B、不同 sha1**）⇒ 见判据 §八 ㉒ ④ 加强版 |
| 7 `VerificationFailure` 四类 | V21-33 | **已完成**（2026-10-01 / D-039）：**只用于工程验证与自检输出、不塞业务错误通路** —— 这条约束不是靠注释声明的，是由判据钉住的：`VF-c` 数 `app/src/main` 里四类符号 **0** / `VerificationFailure` **0** 个文件（同轮反证 test 源集 **10** 命中以证 grep 没坏）、`VF-d` 数业务三套 `ApiException` · `NetworkException` · `Ack.error` 各 **1**（**1/1/1**，长出第四套就红）。真实消费者是 `tools/classify_verification_failure.sh`（读唯一规则表 `tools/verification_failure_rules.txt`，**16** 行 / **4** 类），`verify_device.sh` 的构建分支与无设备分支都调它；判不进唯一类时出 `AMBIGUOUS` / `UNCLASSIFIED` 而**不猜**。类型层证据在 **test 源集**（`sealed interface` + 四个 `object` + `VerificationFailureTest` **8** 例）。实测：判据簿 **rc=0 / 18 行输出**、负控 **13 ✔ / 0 ✘ / 0 △ + 1 ◦**、样例 **7** 份（6 正向 + 1 反例 rc=4，全部本机真实产出、`cmp` 可证）。⚠ **§三十三 的第 6 步 `assembleRelease` 本轮 `rc=1` → 分类器判 `UNCLASSIFIED` 是设计意图**（HTTPS 闸门是策略拒绝、不是四类构建失败），**没有为它加规则行、也没有绕闸门**，release 仍 `BLOCKED` |
| 8 `safeApiCall()` + 5 个高风险点 | V21-07 | **已完成**（2026-10-01 / F-2 ⑧ / D-042）：**改名而不是新写第二份真相** —— 工程里那个 `apiCall` 定义体就是 §七 要的形状（四类分型 + 取消透传 + **刻意没有** `catch (e: Exception)`），所以走 `apiCall` → `safeApiCall`，**签名一字不动**（上一轮"非挂起形参包不住挂起调用"的判断被 `ApiTest.kt:171` 那条现成用例证伪：`inline` + 非挂起形参里直接调 `Repo.xxx()` 实测能编译能跑，本轮 `ApiTest` 11 例仍全绿）。**只迁指令点名的 5 点，不制造 38 个编译问题**，且这 5 点分两级、台账不许记成"修了 5 个缺陷"：**真实修复 2 处** —— `linePolyline`（失败不再被洗成空列表，改 `getOrThrow()` 原样回抛同一个类型）、`logout`（撤销会话失败现在分类可见并打日志，**清本地仍无条件先做**）；**形状归一 3 处** —— 天气预报段、`unreadCount`、`driverUnreadCount`（旧的 `attempt { … }.getOrNull()` 里 `longOf` 本来就不抛 ⇒ **对外行为一字未变**）。`attempt`（`core/net/Attempt.kt`）**保留不并入**：它收 `Result`、服务非网络读，`safeApiCall` 服务网络读、要"哪一类失败"，SA-d 的 `attempt` 全 main **≥7** 下限就是替这句分工站岗的。页面侧 `ui/user/BusScreen.kt` 新增 `polylineUnconfirmed`（**不复用共享 `vm.error`**：别的接口失败会把折线的病因覆盖掉），`polylineReason()` / `polylineSubtitle()` 把"这次没问到（可重试）"与"后端未回传该线路折线"分开说；旧形状 `reason = if (size >= 2) null else "后端没有…"` 归零 —— **本轮唯一那句假话就是它**。**实测**：`SafeApiCallContractTest` **12** 例（`core/net`，10,418 B / 214 行）；判据 `check_safe_call_probes.sh` **15 行读数 / 34 道闸门 rc=0**（SA-a 定义 1·旧名 0·反证 9 / SA-b 体内 1·1·4·0，兜底反证 Repo 38 / SA-c 使用点 6 = Repo 5 + 页面 1，五点逐一点名 / SA-d 伪装形状 0，反证 `emptyList()` 在 Repo 24；`attempt` Repo **0** · 全 main **7** / SA-e 页面 1·2·1·2·1 + 旧形状 0/0/0 / SA-f 用例 12·取消 2·四类 3·不出网 3）；负控 `safe_call_negative_controls.sh` **17 条破口 + 3 条自检行 = ✔ 20 / ✘ 0 / △ 0**、rc=0、80 s；负控全批 **`共 17 本`、逐本 rc=0**（02:39:11→03:10:35，合并 ✔=309 / ✘=0 / △=0 / ◦=3）；全量 **38 类 / 368 例 / 0 失败**（03:11:16，`24 actionable tasks: 24 executed` 无 FROM-CACHE）；`assembleDebug` rc=0 / APK **114,437,929 B**（03:12:23）；lint `--rerun-tasks` 重跑 **98,753 B**（03:15:06）/ **0 error** / 46 Warning + 32 Information = 78。⚠ **三条已登记的教训**（详见判据 §八 ㉕）：**判据下限只能实测、不许推算**（我按"12 − 2 = 10"设下限，两本同时红、实测 **7**，归因链 `12 − 5 = 7` 全靠留档产物）· **死闸门** `echo "…": fail=1;`（冒号代替分号 ⇒ 那条闸永不生效）· **打到判据簿自己头上的破口要连"执行哪一本"一起换**（第 17 条第一次报 rc=0 就是因为 `ctl` 跑的是真身那本）。⚠ **两处仍不是"已通过"**：① **其余 33 个 Repo 读接口的失败语义这一轮一个都没看**（指令给的规模上限，不是漏检）；② **本轮新登记、一字未动**的缺陷 —— 线路页首屏 `load()` 自动选中线路却**不拉折线** ⇒ `polylineSubtitle()` 那句"后端未回传该线路折线"在**问过之前**就说了出口（与本轮修的那句同族：把"没问"说成"没有"），SA-e 的 `E_SUB=1` 与 `E_OLD_SUB=0` 都**允许它存在**，动它=扩大战线。设备侧 12 例全部只有 JVM 证据、那两句文案在 JVM 里渲染不出来 ⇒ **`NOT VERIFIED ON DEVICE`**；release 仍 **BLOCKED**（HTTPS 闸门未绕） |
| 9 `runCatching` 协程审计 | V21-17 | **口径已核对**（本轮实测三种数）：**44** 出现次数（`grep -rho`，一行可含两次）/ **43** 行（`grep -rn`）/ **34** 生效行（滤掉注释行）/ **22** 文件 ⇒ 与 `CONTEXT_LOCK.md`、状态板记的"全 main 仍剩 **34**"**不矛盾**（那是滤注释后的行数，本行的 44 是出现次数）；第 9 步要动的基数取 **34** |
| 10 Worker 重试语义 | V21-20 | `NotifyWorker.kt:24` 的无条件 success 改掉；`NotificationWorkerResultTest` |
| 11–12 开源署名与依赖对照 | — | OSM/ODbL App 内可见 + APK 实际依赖对表 |
| 13 `CONTRACT_EVIDENCE.md` | V21-22/23/24 | **已建**：`docs/android/CONTRACT_EVIDENCE.md` 逐字段记 **后端 VO 声明 + producer 赋值点 + 小程序消费点 + 被删掉的候选名 + 依据**（例：`:15` `Station.plannedMinutes`、`:18` `ShiftTask.plannedDurationMinutes`）；配套 `docs/android/DEFAULT_VALUE_AUDIT.md` 记 §五 的 472 处 A/B/C/D 分类 |
| 14–18 测试/lint/构建/台账/汇报 | V21-30/33/34 | 全量实测，四类失败分型如实 |

## 4. 两条指令的冲突点（不自作主张，摆在这里）

F-2 开头写"不要重做 USER / DRIVER 双域架构"，而 v2.1 §二–§五 点名的正是这套架构的收口
（`RouteDomain` / `domainOf` / `canEnter` / `AppDomain` / `accountRole` / `SessionManager` / `SessionSnapshot`
/ `SESSION_BOOTSTRAPPING`），本轮实测这些符号**在源码里全为 0**，且硬门禁 §三十一 点名的
`AppState.setRole(Role.USER)` 仍在 `ui/driver/DriverShell.kt:37` 生效。

⇒ 处理办法：**本清单如实记 ❌ 并保留符号证据，本轮不推进 §二–§五**（也不谎称已做）。
若下一轮要收口，需要明确授权"在不动现有双域壳的前提下，只加一层 ActiveDomain/AccountRole 分离与
deep link 的 USER_ONLY 闸"——那是一个有硬门槛的改动（`Routes.kt` 的完整域表 + `ShellDecision` 判定 +
`AlertEngine` 读角色的地方），不是补一个函数就能绿的。
