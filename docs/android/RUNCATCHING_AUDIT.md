# runCatching 协程取消形状逐点审计（F-2 第 9 步）

本轮只回答一个问题：**哪些 `runCatching` 会真的吞掉 `CancellationException`，哪些不会**。
判据 `tools/check_runcatching_probes.sh`（RC-a…RC-e），反证 `tools/runcatching_negative_controls.sh`，
闭包检测器 `tools/runcatching_detector.py`，行为面沿用 F-1 已有的 `ApiCancellationTest`。

## 一、结论读数（实测，两套口径不可互引）

| 指标 | 本轮前 | 本轮后 | 口径 |
| --- | --- | --- | --- |
| runCatching 出现次数 | 44 | 46 | `grep -ro`，含注释与同行两次出现 |
| runCatching 生效行 | 34 | 30 | `grep -rn` 去掉注释行（RC-a 钉这个数） |
| runCatching 生效文件 | 17 | 16 | 同上按文件去重（`ui/BusPostRoot.kt` 整文件退出） |
| runCatching 含注释文件 | 22 | 22 | `grep -rl`，含只在注释里提到它的文件 |
| **unsafe total（块内含挂起调用）** | **0** | **0** | 检测器 violations（RC-b） |
| **suspend cancellation violation** | **0** | **0** | 同 RC-b：取消真能被抛进块里的点 |
| 取消可达作用域内的 runCatching | 4 | 0 | 逐点词法链复核（第三节），迁走见第二节 |
| `attempt {` 调用点 | 7 | 11 | TH-f 计数（同轮把下限 7→11，见第五节） |
| runBlocking / suspendCancellableCoroutine | 0 / 0 | 0 / 0 | 免疫前提（RC-d） |
| 全工程 `suspend fun` 名 / 声明行 | 98 / 101 | 98 / 101 | 检测器的闭包输入面（RC-d 下限 98） |

⚠ "出现次数 44→46" 与"生效行 34→30"**方向相反**，不是矛盾：本轮删掉 4 处代码里的 `runCatching`，
同时在 4 处留下了说明为什么换形状的注释（注释里共提到 6 次）。计数口径混用会把这个 +2 读成"改多了"。

⚠ **第 10 步之后的在册面（2026-10-01 复跑实测）**：出现次数 46→**47**、含注释文件 22→**23**，
原因是新增的 `notify/NotifyOutcome.kt` 在 KDoc 里提了一次 `runCatching`（说明分类器为什么用 `throw` 而不是 `runCatching`），
它是第 23 个文件 ⇒ 生效行 **30**、生效文件 **16**、violations **0** 三个实质数一个没动。
RC-a 的四个目标已同步抬到 30/16/**47**/**23**，本节上方那张表按 ⑨ 当时口径保留，不改写。

## 二、本轮修改的 4 点（文件 / 行号 / 原始行为 / 新行为 / 为什么属于协程取消敏感点）

| # | 文件:行号（补丁前 → 后） | 类 | 原始行为 | 新行为 | 为什么是取消敏感点 |
| --- | --- | --- | --- | --- | --- |
| 1 | `data/repo/Repo.kt:356 → 359` | B（`private suspend fun openMeteoWeather` 体内） | `val forecast = runCatching { …16 行 JSON 解析… }.getOrDefault(emptyList())` | `val forecast = attempt { … }.getOrDefault(emptyList())` | 外层是 suspend 函数，而 `runCatching` 是 inline ⇒ 块内**新增挂起调用是合法写法**。今天块里没有挂起点，所以还没有 bug，但那只是当前写法给的运气，不是结构保证 |
| 2 | `core/navigation/AmapNavigationEngine.kt:152 → 155` | B+E（`override suspend fun calculateRoute` 体内） | `val accepted = runCatching { navi.calculateDriveRoute(…) }.getOrDefault(false)` | `val accepted = attempt { … }.getOrDefault(false)` | 同上。且这一处的失败分支已经在屏幕上说话："算路请求没有被导航引擎受理"——取消若被吞，就把"调用方已取消"报成"高德没受理"，是一条假故障归因 |
| 3 | `ui/BusPostRoot.kt:84 → 85` | C（`LaunchedEffect(pending) {}` 体内，经 inline `let`） | `deepLinkTarget(route, Prefs.role)?.let { runCatching { nav.navigate(it) } }` | `deepLinkTarget(route, Prefs.role)?.let { attempt { nav.navigate(it) } }` | `LaunchedEffect` 的块是 `suspend CoroutineScope.() -> Unit`，`let` 内联 ⇒ 这段作用域可挂起；取消被吞等于"用户已经离开还要继续跳深链" |
| 4 | `ui/driver/DriverNavScreen.kt:695 → 697` | C（`LaunchedEffect(ready.value) {}` 体内） | `if (ready.value) runCatching { engine?.setLanguage(Locale.CHINESE) }` | `if (ready.value) attempt { engine?.setLanguage(Locale.CHINESE) }` | 同上：这一屏只在 DEBUG 可达，但组合被取消时不该读成"语种设置失败" |

为什么用 `attempt` 而不是逐点手写 `try { … } catch (e: CancellationException) { throw e } catch (e: Exception) { … }`：
`core/net/Attempt.kt:18` 就是这个形状的具名版本（`suspend fun <T> attempt(block: suspend () -> T): Result<T>`，
先穿透 `CancellationException`，其余照旧收进 `Result`），是 F-1（D-034）为同一条红线立的通用出口，
`core/loc/Locator.kt:204`、`notify/NotifyWorker.kt:23`、`ui/BusPostRoot.kt:98` 已在用（其中 Locator 与
core/navigation 同级，都是 core 层引 `core.net.attempt`，没有新的跨层依赖）。用它会**同时**被 TH-f 数到；
自己再写四份 inline try/catch 就是给同一件事造第五个真相源。
**对外行为一字未变**：`attempt` 与 `runCatching` 都捕获到 Throwable 级、都返回 `Result`、下游 `.getOrDefault()`
的取值不变，唯一差别是取消穿透；本轮没有新增运行时分支，也没有改任何文案。

## 三、34 点全分类（4 已迁 + 30 保持）

免疫论证只有两条腿，两条都在 RC-d 里被数着：

1. **块内没有挂起调用**（RC-b violations=0）：检测器拿全工程 98 个 `suspend fun` 名 ∪ 一组无歧义挂起入口
   （delay / withContext / withTimeout(OrNull) / await / join / receive / send / collect / emit /
   coroutineScope / supervisorScope / …）与每个块体的调用名求交集，并区分接收者：`Api.get(` 算命中，
   `main.post(`（Handler）、`sp.getString(`（SharedPreferences）不算——朴素名字交集曾把后两者误报为挂起，
   所以这个"0"必须是核过接收者的 0。
2. **非 suspend 成员函数结构上不可能挂起**：全工程 `runBlocking = 0`、`suspendCancellableCoroutine = 0`。
   Kotlin 里非 suspend 函数要挂起只能靠这两者之一；两者都是 0 ⇒ 落在这类作用域里的 `runCatching`
   永远等不到 `CancellationException`，改它只是造一段不可达的 catch。
   ⚠ 这是**会失效的前提**不是永久事实：谁引入了 `runBlocking` 或 `suspendCancellableCoroutine`，
   RC-d 立刻变红，下表 38 行的"否"必须整表重判。

下表行号为 **N-4（2026-10-05，赛道 ⑪）补丁后**逐条 grep 复位的行号（本轮改过源码的两个文件整体下移：`AmapNavigationEngine.kt` 因 ⑭/N-2/N-3/⑪ 四次新增而累计下移，⑪ 这一处 310→357 是 **+47**、349→399 与 395→445 是 **+50**（各处不等，因为插入点分散在多个函数之间）；`DriverNavScreen.kt` 因 ⑪ 撤掉本机阈值判定而下移 **+18**（696→714、702→720、711→729）。这 12 行本轮已按字面复位，其中 `:355`（instance）与 `:711`（speak）在 ±3 容差下**侥幸还绿**也一并复位；`Repo.kt`/`Notifier.kt`/`WorkbenchScreen.kt`/`MinePages.kt` 本轮未改源码，行号沿用 N-3 那遍复位；
⚠ 这张表**不在** `check_doc_anchor_probes.sh` 的七本台账名单里，行号漂了没有任何闸门会响，只能靠改过源码的那一轮手工复位）。"取消可达"列写"否"的依据是包围它的那个非 suspend 边界
（成员函数 / 函数类型里没有 suspend 的 lambda / `Runnable`），不是"看起来像同步"。

| 文件:行号 | 类 | 块内含挂起 | 取消可达作用域 | 本轮动作 | 判定依据（包围点） |
| --- | --- | --- | --- | --- | --- |
| data/repo/Repo.kt:366 | B | 否 | 是（suspend fun 体内） | 已迁 attempt | 见第二节 #1 |
| core/navigation/AmapNavigationEngine.kt:297 | B+E | 否 | 是（suspend fun 体内） | 已迁 attempt | 见第二节 #2 |
| ui/BusPostRoot.kt:87 | C | 否 | 是（LaunchedEffect + inline let） | 已迁 attempt | 见第二节 #3 |
| ui/driver/DriverNavScreen.kt:720 | C | 否 | 是（LaunchedEffect） | 已迁 attempt | 见第二节 #4 |
<!-- RC-ROWS-BEGIN -->
| MainActivity.kt:68 | A/D | 否 | 否 | 保持 | `private fun askNotificationOnce()`；块内是 `ActivityResultLauncher.launch` |
| core/code/QrCode.kt:33 | A | 否 | 否 | 保持 | `fun bitmap(text, sizePx, darkColor)`，纯 Bitmap/Writer 生成 |
| core/code/Scanner.kt:38 | D | 否 | 否 | 保持 | `scanner.launch = { prompt -> }`，声明是 `internal var launch: ((String) -> Unit)?`（Scanner.kt:19，函数类型里没有 suspend） |
| core/loc/Locator.kt:129 | F | 否 | 否 | 保持 | `private fun usable(lm)`，块内 `lm.allProviders` |
| core/loc/Locator.kt:133 | F | 否 | 否 | 保持 | `private fun cachedLocation(...)`，块内 `lm.getLastKnownLocation(p)` |
| core/loc/Locator.kt:165 | F | 否 | 否 | 保持 | `val registration = Runnable { }`——`Runnable.run()` 非 suspend，`forEach` 内联也穿不过它 |
| core/loc/Locator.kt:166 | F | 否 | 否 | 保持 | 同 165（`.onFailure { runCatching { … } }` 的退化重试支） |
| core/loc/Locator.kt:171 | F | 否 | 否 | 保持 | 外层 `awaitClose { }` 确是 suspend，但块被 `main.post { }`（Handler，非内联）截断 |
| core/navigation/AmapNavigationEngine.kt:372 | E | 否 | 否 | 保持 | `fun prepare()`，块内 `navi.addAMapNaviListener(l)` |
| core/navigation/AmapNavigationEngine.kt:512 | E | 否 | 否 | 保持 | `override fun startGpsNavi()` 内读回 SDK 当前路径（F-3 ⑭ 新增），块内 `navi.naviPath` 是属性 getter，非挂起 |
| core/navigation/AmapNavigationEngine.kt:522 | E | 否 | 否 | 保持 | N-2（赛道 ⑨）新增：`override fun startGpsNavi()` 里把语音开关交给高德，块内 `navi.setUseInnerVoice(voiceOn)` 是普通 boolean setter，非挂起；这里**用不了** `attempt`（它是 `suspend fun`，`startGpsNavi` 不是） |
| core/navigation/AmapNavigationEngine.kt:523 | E | 否 | 否 | 保持 | `override fun startGpsNavi()`，块内 `navi.startNavi(NaviType.GPS)` |
| core/navigation/AmapNavigationEngine.kt:532 | E | 否 | 否 | 保持 | `override fun stopNavi()` |
| core/navigation/AmapNavigationEngine.kt:557 | E | 否 | 否 | 保持 | `override fun release()` |
| core/navigation/AmapNavigationEngine.kt:563 | E | 否 | 否 | 保持 | `private fun instance(): AMapNavi?`，块内 `AMapNavi.getInstance(context)` |
| core/navigation/AmapNavigationEngine.kt:630 | E | 否 | 否 | 保持 | 顶层 `internal fun naviClassPresent(loader)`，块内 `Class.forName` |
| core/net/Api.kt:445 | A | 否 | 否 | 保持 | `private fun JSONObjectOrNull(text)`，块内 `JSONObject(text)` |
| data/repo/Repo.kt:1172 | A | 否 | 否 | 保持 | `private fun navStepOf(it)`（表达式函数），块内 `NavIcon.valueOf(...)` |
| data/store/Prefs.kt:53 | A | 否 | 否 | 保持 | `var role` 的属性 getter；⚠ 另记 OPEN_DEFECT：脏值被 `.getOrDefault(Role.USER)` 静默降级，不属本轮取消议题 |
| driver/DriverLocationService.kt:54 | F/G | 否 | 否 | 保持 | `override fun onStartCommand(...)`，块内 `startForeground` |
| driver/DriverLocationService.kt:193 | F/G | 否 | 否 | 保持 | `private fun notify(text)` |
| driver/DriverLocationService.kt:221 | G | 否 | 否 | 保持 | `companion object` 里的 `fun start(context)` |
| notify/Notifier.kt:87 | A | 否 | 否 | 保持 | `object Notifier` 内非 suspend 函数，块内 `NotificationManagerCompat.notify` |
| ui/common/BusMap.kt:137 | E（视图回调，原误记 C） | 否 | 否 | 保持 | `AndroidView(factory = { ctx -> })`，形参类型 `(Context) -> T`；早期按 C 类的登记是**错的**（见第四节） |
| ui/common/BusMap.kt:142 | E（视图回调） | 否 | 否 | 保持 | `update = { view -> }`，形参类型 `(T) -> Unit` |
| ui/common/BusMap.kt:145 | E（视图回调） | 否 | 否 | 保持 | 同 142（约 54 行的地图设置块，全非 suspend） |
| ui/driver/DriverNavScreen.kt:714 | A | 否 | 否 | 保持 | `remember(context) { }` 的计算块是 `() -> T`，非 suspend |
| ui/driver/DriverNavScreen.kt:729 | A | 否 | 否 | 保持 | `private fun speak(engine, text)`，块内 `engine.speak(...)` |
| ui/driver/WorkbenchScreen.kt:1290 | A | 否 | 否 | 保持 | `private fun callDriver(context, mobile)` |
| ui/user/HomeScreen.kt:260 | A | 否 | 否 | 保持 | `onOpenSettings: () -> Unit`（HomeScreen.kt:622 的形参声明） |
| ui/user/MinePages.kt:901 | A | 否 | 否 | 保持 | `onClick = { }`，`() -> Unit` |
| ui/user/SendScreen.kt:200 | A | 否 | 否 | 保持 | `SendViewModel.private fun restore()`，块内 `JSONObject(raw)` |
| core/navigation/AmapNavigationEngine.kt:382 | E | 否 | 否 | 保持 | `fun prepare()` 的 released 出口：`runCatching { navi.removeAMapNaviListener(l) }`，包围点是非 suspend 成员函数 |
| core/navigation/AmapNavigationEngine.kt:687 | E | 否 | 否 | 保持 | `private fun loadOnce()` 里 `System.loadLibrary(name)`：缺 `.so` 抛的是 `UnsatisfiedLinkError`（不是 `Exception`），只有 catch 到 Throwable 才不拖崩启动 |
| ui/driver/DriverGuideScreen.kt:303 | E（视图回调） | 否 | 否 | 保持 | `AndroidView(factory = { ctx -> })` 构造 `AMapNaviView`；失败经 `.getOrElse` 写 `failed = “高德导航视图在本机装不起来：…”` 并回一个空 View —— 如实降级，不画假导航（2026-10-09 新增那一屏，与 `BusMap.kt:137` 同形） |
| ui/driver/DriverGuideScreen.kt:333 | E（视图回调） | 否 | 否 | 保持 | `update = { v -> }` 重设夜间/车道/视角，形参类型 `(T) -> Unit`，非 suspend |
| ui/driver/DriverGuideScreen.kt:341 | E（视图回调） | 否 | 否 | 保持 | `DisposableEffect(Unit) { onDispose { } }` 里的 `v.onPause()` |
| ui/driver/DriverGuideScreen.kt:342 | E（视图回调） | 否 | 否 | 保持 | 同 341 的 `v.onDestroy()`：销毁两步各自包，一步抛不许顶掉另一步 |
<!-- RC-ROWS-END -->

RC-e 的计数口径：`<!-- RC-ROWS-BEGIN -->` 与 `END` 之间的物理行（32 行，不含表头与分隔行）；
"已迁 attempt" 4 行写在标记块**上方**，两条相加 = 35 = 本轮审计的在册基数。

## 四、分类口径（A–G，指令 ⑨ 的七类怎么落在这套代码上）

- **A 纯同步、无协程取消语义**：块体与包围函数都不涉及协程（JSON / Bitmap / 枚举 / Intent）。
- **B suspend**：直接被包围在 `suspend fun` 体内 ⇒ 块内可挂起，是本轮必改的两处。
- **C 协程构建器 / Flow**：`LaunchedEffect` / `launch` / `async` / `withContext` / `callbackFlow` 的 suspend 块内。
  ⚠ 只有**函数类型带 suspend** 的块才算。`AndroidView` 的 `factory`/`update`、`remember` 的计算块、
  `onClick`/`onOpenSettings` 这类回调形参都不是——审计初稿把 `BusMap.kt` 三点与 `DriverNavScreen.kt:691`
  记进了 C 类，那是**按所在 composable 的外观猜的**，词法链核对后改成 A/E。这族误分类如果被写进判据，
  下一轮就会因为"改了也不会红"而永远修不掉。
- **D callback bridge**：SDK / 系统回调桥（zxing `launcher.launch`、`ActivityResultLauncher`）。
- **E navigation SDK / 视图句柄**：高德 `AMapNavi` / `MapView` 的句柄调用。
- **F location**：`LocationManager` / provider 注册与取点。
- **G worker / service**：`DriverLocationService` 的前台服务与上报支路。
  `notify/NotifyWorker.kt` 本轮 **0 处 runCatching**（它 F-1 时已走 `attempt`），⑩ 要查的是它的返回值语义不是取消。

## 五、本轮对其他判据簿的影响（同轮改，不留静默作废）

| 簿 | 影响 | 同轮处置 |
| --- | --- | --- |
| `check_throwable_probes.sh` TH-f | `attempt {` 调用点 7 → 11 | 下限同轮 7→11，注释写明 4 处逐点归因；别的口径一字未放宽 |
| `throwable_negative_controls.sh` TH-f 那条 | 原文案"调用点 7→6"在新基线上不再变红（11−1=10 仍 ≥7）——**一条会静默失效的反证** | 判据抬成 `-ge 11` 后破口重新命中，文案改"11→10"；由 TH 簿与 RC 簿各自复跑证明 |
| `shell_routing_negative_controls.sh:82` | SR-c 的注入锚点是 `deepLinkTarget(route, Prefs.role)?.let { runCatching { nav.navigate(it) } }` 整行原文；该行本轮改形 ⇒ 锚点不命中会 `ANCHOR-MISS` 退出 2（不会假绿，但整本 SR 负控跑不完） | 锚点同步改成新的 `attempt` 整行原文，并复跑 SR 负控 |
| `check_safe_call_probes.sh` SA-d | **实跑打红了**（第一次扫全簿时 rc=1：`× SA-d Repo 又出现 1 处 attempt`）。本簿原先写的"锚点指 `Repo.kt:312`、与 `Repo.kt:359` 不是同一行 ⇒ 无需改"是**推理不是读数**：破口不在那两条锚点，而在 SA-d 的 `D_ATTEMPT_REPO`——它数的是 Repo.kt **整本**的 `attempt {`，⑧ 之后不变量为 0，本轮 ⑨ 往 Repo 放了 1 处 ⇒ 归零不变量被打破。教训：跨簿影响只能以"把每一本扫一遍的实测"为准，"指向不同行"不等于"不影响那一本" | 把 SA-d 从"=0"收成"恰好 1 处 + 整行字面量点名"（`D_ATTEMPT_FORECAST` 钉 `val forecast = attempt {`），全 main 下限同轮 7→11（= ⑧ 的 7 处 + 本轮迁入的 4 处）；`safe_call_negative_controls.sh` 新增第 7b 条打破"数量没变但被换名"，第 10 条文案随下限一起改。SA 簿复跑 rc=0，SA 负控 18/18 命中、RESTORE 干净 |
| `check_safe_call_probes.sh:54` / `safe_call_negative_controls.sh:127` 那两条锚点 | 复核结论不变：`val forecast = safeApiCall {` 指 `Repo.kt:312` 的后端预报迁移点，本轮一字未动；SA 负控注入产生的 `val forecast = attempt {` 只在沙盒副本里 | 无需改；由 SA 负控簿整本复跑（18/18）证明两条注入仍命中判据 |
| `v21_requirement_census.sh` | "runCatching (main)" 是只打数行，口径含注释 ⇒ 读数 44→46，而生效行 34→30 | 第一节把两套口径并排列出，禁止互引 |
| `v21_requirement_census.sh` 全表 | 与 ⑧ 留档产物 `/tmp/f2_8_ledger_census.log` **逐行 diff**（09:5x 复跑，`rows=118` 不变）：变的只有 3 行 —— `V21-01` 判据簿 16→**17** / 负控 17→**18**（本轮新那两本）、`V21-07` `attempt {` 8/7/7→**12/9/11**、`V21-17` `runCatching (main)` **46/22/30**。新增的 `RUNCATCHING_AUDIT.md` **不产生行**（census 不数文档），所以 rows 仍是 118 —— 别把"行数没变"读成"本轮没动东西" | 读数留档 `$TEMP/f2_9_census.log`；第一节口径照旧 |

## 六、不在本轮范围、且**不因为本轮而消失**的在册项

`DriverNavScreen.kt` 整屏仍是 DEBUG-only 调试面（#51）；`Prefs.kt:53` 的角色脏值静默降级（第三节已点名）；
§五那 32 处 C 站点、`DriverShell.kt:37` 的角色写、线路页导航入口仅 DEBUG 可达、线路页首屏不拉折线
（`polylineSubtitle` 把"没问"说成"没有"）—— 全部维持 `OPEN_DEFECT`，本轮一字未动，也不在本轮判据里冒充已修。

> **2026-10-01 F-3 ② 轮改读（本段上面那行保留原文）**：那"**32 处**"这个数已被实测**推翻为不可复现**（登记 `D-②-4`），
> 同一份 census JSON 按可复现规则重建的基数是 **C=65**（形态：字符串 38 / 数值布尔 25，其中严格非可空 19 / 数组 2；
> 归属：**E 类 31、D 类 11、真待收口 23**），`DV-k` 已由 132 降到 **116**；六族已收口，当前项 = **F-3 ③**。
> 权威读数见 `DEFAULT_VALUE_AUDIT.md` §二之二 与 `DECISION_LOG.md` **D-046**。
> 同一条里 `DriverShell.kt:37` 也是**漂的行号**，现实测在 `:38`。

三态：本轮改的是**生产源码 4 个文件** ⇒ SOURCE_COMPLETE；`:app:compileDebugKotlin` rc=0
（`BUILD SUCCESSFUL in 27s`，留档 `$TEMP/f2_9_compile.log`）⇒ BUILD_VERIFIED 只有 debug 面；
**DEVICE_VERIFIED = NOT VERIFIED ON DEVICE**（本机无设备，RC-* 全为静态判据，取消行为在 JVM 里
只有 `ApiCancellationTest` 那一层能测，页面侧渲染不出来）。
