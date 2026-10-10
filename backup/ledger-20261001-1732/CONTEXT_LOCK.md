# CONTEXT_LOCK — 不可协商的项目硬约束

> 每个会话开工前先读本文件。本文件里的规则**优先级高于**任何临时便利、临时性能优化、"先跑通再说"的冲动。
> 若某条规则与新需求冲突：**先记录到 `DECISION_LOG.md` 并说明代价**，不要静默放宽。

## 〇、ANDROID APP MODEL（指令 v2.0 §105 要求的原文块，2026-09-29 加入）

```
ANDROID APP MODEL
=================
One APK
USER domain
DRIVER domain
Shared Core
Mini Program:
FROZEN
Web:
ACTIVE
Driver:
Safety critical
Routing:
REAL ROAD ONLY
```

逐行对应到本工程的**实际落点**（这一节不是装饰：写不出落点的行就是空头承诺）：

| 模型行 | 工程里的实体 | 实测 |
|---|---|---|
| One APK | 单模块 `app/`、单 `applicationId`，无 `UserApp`/`DriverApp` | `settings.gradle.kts` 只 include `:app` |
| USER domain | `ui/user/UserShell.kt` + `ui/user/` 13 个页面 | 存在 |
| DRIVER domain | `ui/driver/DriverShell.kt` + `ui/driver/` 其余 12 个文件（2026-09-30 实测目录内共 13 个 `.kt`；§54 那批加了 `DriverExceptionScreen/DriverExceptionReport`，上一版写的"10"是 V-3 之前抄的，已按实测抬） | 存在。**准入的权威在后端**：`Repo.driverProfile()` 回三态（V-2b），本地 `Prefs.role` 只是**落点**不是判据 —— 明确否定即降回 USER 并停位置上报 |
| Shared Core | `core/{net,map,loc,ui,code,utils}`、`data/{model,repo,store}` | 两域共用同一套 `Api`/`Repo`，**没有** `UserApi`/`DriverApi` |
| Mini Program: FROZEN | `../cargo-post-miniprogram` 只作**只读对齐基准**，不改其业务逻辑 | 规则 A |
| Web: ACTIVE | 调度大屏继续吃现有后端 | 规则 A |
| Driver: Safety critical | 硬规则 2 + §125 降级：地图/导航失败**不得**让当前任务消失（`RouteCheck.Rejected` 只影响渲染面） | 见审计 §64 |
| Routing: REAL ROAD ONLY | `RouteSource.real` 白名单 + `RealRoadRouteValidator` + `RouteRepository`（只缓存过校验的路线） | 规则 G |

**⚠ 与指令字面不一致的两处，在此明说，不要"看起来一致"地改代码**：

1. 指令 §16 说 `RouteSource` 里**不要**有 `UNKNOWN / MOCK / DEMO / EUCLIDEAN / STRAIGHT_LINE`。
   本工程反过来**保留**这些值，但一律带 `real = false`（`core/map/RouteSource.kt` 枚举的 `real` 形参）。
   理由：后端/历史数据真会回 `euclidean`、`haversine`、`straight` 这类字符串，
   `parse()` 必须有地方把它们归一到一个**被拒收**的值。若删掉这些枚举，它们只能落进 `UNKNOWN`，
   于是"这条折线是直线"和"这条折线来源没认出来"变成同一个值 —— 拒收时说不清原因。
   保留带毒标签 = **拒绝时有原因可说**（`RealRoadRouteValidator` 在 `if (!source.real)` 那一支
   直接回"路线来源不可信（直线估算）"）。指令 §32「不允许一个引擎失败就直线」与本文件硬规则 19
   要的是**结果**（直线绝不进正式链路），不是**枚举表里不许出现这些字**。判据按结果查。
2. 指令 §105 与本文件第二节都写了 `NavigationEngine` / 导航层。
   ~~但**它今天不存在**：高德**导航** SDK 未进依赖（`3dmap:10.0.600` jar 里 `com/amap/api/navi/` 零类），
   所以导航层是 `BLOCKED`（任务 #49），不是"已完成"。~~
   **2026-09-30 状态变更（N-1 / D-031）**：依赖已换成 `com.amap.api:navi-3dmap:10.0.600_3dmap10.0.600`
   （javap 实测 jar 内确有 `com/amap/api/navi/` 类，取证见审计 §77 一），
   `core/navigation/` 两层引擎 + `DriverSdkNavCard` 生产调用点 + 19 条用例 + N-a…N-l 判据已落地，
   **2026-09-30 #51 / D-032 追加**：正式导航面重挂到工作台（调用点从旧屏搬进 `WorkbenchScreen`，判据 N-g 第六位改成"恰好 1 且锚在该文件"），
   旧屏降级为 DEBUG ONLY，判据簿扩到 **N-a…N-p 十六条**、结构负控扩到 27 条（明细审计 §78、判据 §八 ⑲）。
   **这条 BLOCKED 解除**。但它换成的不是"已完成"，而是一条**新的、更窄的**在册偏差：
   **导航层的运行时行为在真机上零验证**（§九最后一行）—— 模型块不能变成状态声明，反之
   "代码写完了"也不能变成"能导航了"；后者只有设备能签。

## 一、PROJECT HARD RULES（20 条，原文保留）

1. 用户端与司机端是两个业务域，必须分开：用户关心货在哪，司机关心这一班要做什么。
2. 司机端是全项目最高优先级，司机的作业闭环决定系统能不能真的跑起来。
3. 调度（Dispatch）决定去哪里 —— 任务点的顺序、增删、改派只能由后端调度产生。
4. 导航（Navigation）决定怎么走 —— 导航只负责"当前任务点到下一个任务点之间的真实道路"。
5. 所有道路必须是 REAL ROAD：真实路网算出来的折线、真实路网算出来的距离、真实路网算出来的时间。
6. 禁止直线路线（straight line / 两点连线）。
7. 禁止 Euclidean / 欧氏距离当作里程。
8. 禁止把 Haversine 大圆距离当作驾驶距离。
9. 禁止 Mock polyline。
10. 禁止 Fake GPS。
11. 禁止 Fake Lane（车道信息只能来自真实数据源）。
12. 禁止 Fake turn / fake steps（编造转向步骤）。
13. 重庆 OSM（`chongqing-260921.osm.pbf`）是合法的真实路网数据源。
14. GraphHopper / RoadGraph 在 OSM 之上，是合法的路径引擎。
15. 实时逐向导航（turn-by-turn）优先使用高德导航 SDK（AMap Navigation）——中国境内路况与转向最可信。
16. 车道级引导只显示真实车道数据；没有就整条不显示，不许画占位车道。
17. 3D 只是视觉层：3D 里出现的路线仍然是真实道路，**宁可 3D 少一点，不允许 3D 里的路线是假路线**。
18. 正式包（release）禁止任何 demo 兜底、假数据回退、演示占位业务数据。
19. 当不存在真实路线时：显示"路线不可用 + 原因 + 重试"，**绝不补线**。
20. 宁可没有路线，不允许假路线；宁可暂时不能导航，不允许假导航。

## 二、分层与职责（不得越界）

```
调度层（后端 DriverRoute / 任务点序列）
        ↓ 只给"顺序 + 任务点"
导航层（NavigationEngine → AMap Navi / GraphHopper+OSM）
        ↓ 只给"这一段真实道路 + 真实转向 + 真实里程/ETA"
渲染层（DriverMissionMap 2D/3D、Compose UI）
        ↓ 只画，不决策、不补数据
```

- 渲染层**不得**重排任务点、不得合成折线、不得推算 ETA。
- 导航层**不得**改动业务状态（发车/到站/交接/完成）。
- **禁止 GPS 自动完成业务**：业务推进必须"司机主动动作 + 后端确认回读"，位置只用于展示与上报。

## 三、真实性来源白名单（routeProvider）

| 允许 | 含义 | 可用于 |
|---|---|---|
| `AMAP` | 高德路网/导航 SDK | 正式折线、里程、ETA、转向、车道 |
| `OSM_GRAPHHOPPER` | 重庆 OSM + GraphHopper | 正式折线、里程、ETA、转向 |
| `OSM_ROAD_GRAPH` | 自建 RoadGraph | 正式折线、里程、ETA |
| `VALHALLA` | 备用引擎（当前 BLOCKED） | 同上，接入后才可用 |
| `CACHE_REAL_ROAD` | 上述来源的本地离线缓存 | 正式（须带原始 provider 与抓取时间） |

| 禁止出现在正式链路 | 处理方式 |
|---|---|
| `EUCLIDEAN` / `STRAIGHT` / `TWO_POINT` | `RealRoadRouteValidator` 直接拒绝 |
| `MOCK` / `FAKE` / `DEMO` / `ESTIMATED` / `UNKNOWN` | 拒绝；UI 走 `RouteUnavailableState` |
| 空 polyline（< 2 点） | 拒绝；不补线 |

## 四、字段兼容规则

- **不要为了兼容旧接口把 euclidean 转换成 OSM** —— 来源必须真实，转换不改变来源。
- **不要删除字段**以免破坏旧兼容：`nextStopDistanceMeters` 保留但只允许 DEBUG/调试读取；
  新增 `nextStopRoadDistanceMeters` / `nextStopRoadEtaSeconds` 承载真实路网值。
- 新旧字段并存时，UI 只准读 `*Road*` 系列。
- **两种口径不许共用一个字段名**（⑫ 立规，`DECISION_LOG` D-025 第 1 条）：`SendOrder.etaMinutes`（班次口径）与
  `carrierEtaMinutes`（后端 `GeoDistanceUtil` 的 **Haversine 直线 ÷ 25 km/h**）曾是同一字段的候选键，
  现在拆成两个字段，且**禁止再互为别名**（判据 T-t）。承运那一半要出现在界面上，**必须同行带"（直线估算）"**（T-v/T-v2）。
- **同一个键在两个端点说的是两件事时，按"能观测到的形状"分家，不按名字猜**（⑬ 立规，D-026 第 1 条）：
  `etaMinutes` 在 `/bus/lines` 是"跑完剩余线路"（`AppBusServiceImpl:130-132`），在 `/bus/nearby` 是"到下一站"
  （`fillEta:726-731`）。客户端**唯一**的判别量是 `progress` 只在 lines 形状里出现 ⇒ 大数字走"剩余线路"，
  只有 `running && progress == null` 才允许把 `etaMinutes` 念成"下一站"，且**必须**向上取整到 ≥1（`T-ab`，
  与小程序 `detail.js:132-137`、后端 `Math.max(1,…)` 同口径）。分不出来的那支就显示"—"：
  **宁可大数字空着，不许摆一个口径错的数** —— 空数只是少一条信息，错数会被用户当信息用。
- **三个 `distanceKm` 是三种口径，不许并成一条判据**（⑬）：`BusLine.distanceKm`（线路全长，
  `AppBusLineRespVO:27-28`，上游 `MonitoringServiceImpl:126` 可 null）、`TransportLeg.distanceKm`（本段计划里程，
  `AppDriverLegRespVO:58-62`，`BigDecimal` 可空）、`QuoteResult.distanceKm`（**直线距离 × 路网系数**，
  `AppSendQuoteRespVO:21-22` 的 @Schema 原文就这么写）。前两个没给就"未给"，第三个**有值也必须同行带口径**。
  判据是 T-ad1（六处可空声明逐类体数）+ T-ae（闭包：界面裸读 `.distanceKm` 未判 null = 0）。
- **一条后端事实，别再按 Android 模型猜**：`/send/page` 的列表项**里没有 `etaMinutes` 这个键** ——
  `AppSendOrderRespVO:96` 那个字段只由 `AppSendController:246-250` 的 `fillEta` 写（且只有未来时间才给值，否则显式 null），
  而列表走 `fillCarrierBatch`（`:142`）不调它。`/send/arrangements` 的 11 个键里同样没有 ETA（§68），
  `AppSendLegRespVO` / `AppDriverLegRespVO` **两个段契约都没有 ETA 键**。⇒ 凡是"读不到就显示 0 / 不显示"的地方，先怀疑键是否存在，而不是怀疑时序。
  **E-1 按 VO 逐字段复量，这行一字未改地仍然成立**（`AppSendOrderRespVO:96 etaMinutes` / `:106 carrierEtaMinutes`；
  `AppBusRespVO:44/:50`；`AppBusNearbyRespVO` 内 `NearbyBus:123/:126/:144`；`AppDriverRouteRespVO:42/:45/:48/:78/:83`；
  `AppDriverLegRespVO` 里 `eta` **0 命中**）—— 复量的动机是本轮要删别名键，必须知道**真实键各自姓什么**。
- **"多认一个键名"不是兼容，是自造来源**（E-1 立规，D-033 / 判据 §八 ⑳）：解析层曾把 ETA 读成
  `multiNumOrNull("etaMinutes", "eta", …)` —— 看着像防御性兼容，实际是客户端**凭空加了一个生产者**。
  判据要问的是"**这个键谁在写**"：2026-09-30 三方实测 `"eta"` 在后端 / 小程序两棵树 / 演示面各 **0 命中**
  （同口径 `etaMinutes` 在后端 java 有 **13** 处），所以它从来没有生产者，**删它不可能破坏真兼容**。
  反过来，若为了让别名"合法"而给演示面补一个 `"eta"` 键，那是**把自造的键洗成契约**，下一轮就再也证不出它不该存在
  —— 因此同轮立 **T-ai3**（演示面 `"eta"` 键 = 0）钉死这条诱惑。
  与 §38 那条红线的分工要看清：`null → 0` 是"没给说成给了 0"，**别名键是"读到一个确实存在、但没人承诺过的数"**，
  前者由 T-a 抓，后者**只有** T-ai 这一族抓得到。

## 五、第三方与开源

- 可以研究、复用、修改开源代码；**必须保留第三方许可证 / NOTICE**。
- 不要复制整个巨大仓库；只提取实际需要的模块、类、逻辑。
- 每个复用文件记入 `THIRD_PARTY_NOTICES.md`：项目 / URL / License / 复用了哪些文件 / 用途。
  **该文件已于 2026-09-29 建档**（此前本条与 D-009 都是"有规则、没有台账"）。
  当前实测结论：工程**未复制任何第三方源码文件**，全部能力走 Maven 构件；
  MapLibre Navigation / GraphHopper Navigation Example / Valhalla Mobile 属"计划复用、尚未引入"，已按 §100 预登记。
- ⚠ **一条一直没被认领的署名义务**（建档时才暴露）：屏上真实道路折线源自 OSM 数据（ODbL 1.0），
  而 `grep -rni "openstreetmap" app/src/main` 命中 **0** —— App 里从没署过名。已记入 §九 与队列，别当"内部项目就不用署名"。
- **绝不自己假造 API**：SDK 能力不确定时先查当前依赖的实际 API（`./gradlew :app:dependencies`、解包 aar、 javap），
  不能编造 `AMap3DModel` 之类不存在的类。能力拿不到就标 `BLOCKED`。

## 六、环境与构建诚实性

- Gradle wrapper 下载失败：**不要修改代码来绕过**。
- 某个开源 SDK Gradle 编译困难：标 `BLOCKED`，不要魔改成本地假实现。
- Gradle 下载/网络受限：记录 `ENVIRONMENT_BLOCKED`，不能假装成功。
- 真机才能验证的东西（GPS、导航 SDK、前台服务）：没有真机就明说"未验证"，不要用 JVM 测试冒充。
- 交付报告不得写"100% 完成"；只写已验证项 + 未验证项 + BLOCKED 项。
- **产物新鲜度只认 gradle 的 `executed` / `up-to-date` 判定，不认时间戳、也不认字节哈希**（2026-09-29 实测推翻 ⑪ 的"逐产物对时间戳"口径）：
  构建缓存**保留产物原 mtime**，所以"APK 比最后一次源码编辑旧"可以完全正常；
  而 APK 的 zip 条目内嵌打包时间，所以"同源码重打包 SHA 变了"**也不能**证明旧的（实测 `--rerun-tasks` 前后哈希必变）。
  要无歧义就 `--rerun-tasks` 逼它真跑，并连同 executed 任务数一起抄进台账 —— **结论的依据是"任务真被执行"，不是"时钟显示晚于"**。
- **判据自己也要被证明会响**（⑫ 实测两条"永远绿"的判据，`DECISION_LOG` D-025 第 6 条）：
  ① `grep 'multiNumOrNull\("etaMinutes", …\)'` —— BRE 里 `\(`/`\)` 是**分组**不是字面圆括号，这条**永远匹配不到**，
  注入破口后探针仍 exit 0；改 `-F` 才会响。② 负控制的 sed 写 `s/etaMinutes != null/…/`（小写），
  而代码读的是 `carrierEtaMinutes != null`（大写 E），**破口根本没注进去**，该控制在 ⑫ 之前是**假绿**。
  ⇒ 固定口径：**新判据与它的负控制必须同轮交付**，且每条控制都要 `✔`（不是"跑过"，是逐条看到 ✔）；
  一条永不报红的判据比没有判据更危险 —— 它让人以为有人在守。
- **同类陷阱**：数"字段声明"的判据必须**限定在 `data class` 类体内**（`sed -n '/^data class X/,/^) {/p'`），
  整文件 `grep -c` 会把别的契约的同名字段（`distanceKm` 在三个类里都有）算进来；
  给负控制注入破口时也**别用全局 sed** —— 用 `tools/eta_negative_controls.sh` 里的 `sbysed`（按类体边界替换），
  否则一次改掉四个类，报红的是"别的那条"，等于没测到目标判据。
  ⑬ 的 `T-ad1`（六个类的 `distanceKm`）就是这条纪律的正面用例：**"六个"只有在逐类体数的时候才是六个**。
- **⑬ 又被自己的判据咬了两次，两次都是"看起来在守、其实不响"**（`DECISION_LOG` D-026 第 8 条）：
  ① **`-ge` 型计数下限不跟着加用例抬**：`T-k ≥9` 在本轮加完用例（实测 11、后 12）之后仍然 exit 0 ——
  它的负控制第一次跑就报 ✘，因为"删五条用例"和"加三条用例"输出一样。⇒ 固定口径：
  **加用例与抬下限必须在同一轮**，且负控制必须包含"把计数改小"这一支。
  ② **关键字型判据会把"说明为什么改"的注释当成破口**：`T-ah` 第一次响，响的是我引用的旧文案
  `"在途 ${nextStation}"`。处置是按内容过滤注释行（`grep -vE ':[0-9]+: *(\*|//)'`，与 T-s/T-v 同写法），
  **不是把注释删掉** —— 注释是这一轮的因果链，删了就只剩一个"对的结果"。⇒ 关键字型判据从第一天起就要写清
  **数的是生效代码还是文本**，否则它抓到的第一个人是写说明的人。
  ③ 同轮还有两条**路径/锚点写错**的"安静不响"：§八 ④ 与 ⑥ 的命令都以 `cd app/src/main/java` 为前提却写了
  工程根的路径，照抄会得到 `No such file or directory` + 计数 0，与"真的修好了"在输出上不可区分（④ 假步进实测仍是 6 处）。
- **⑬ 立的过程规矩**（不是代码问题，但会直接污染"抄数"，所以与判据同等级）：
  ① **负控脚本一次只跑一个**：`eta_negative_controls.sh` 会 `sed` 改源码再恢复，两份同时在跑就是互相覆盖，
  重叠窗口里读到的源码状态与写进台账的数字都可能不是同一件事。等它打印 `[恢复后] … 判据通过` 再碰源码。
  ⚠ **这条 2026-09-30 写下的规矩当时只是注释，没有任何东西执行它** —— 2026-10-01 真的两批并行了一次，代价见 ㉔ / D-040；
  现在有机制了：每本就地型负控开头 `negctl_lock`，抢不到锁就 `[STOP]` + rc=3（批次脚本持锁后 `export NEGCTL_LOCK_HELD=1` 让批内各本沿用同一把）。
  ② **台账跑重复块自检**：本轮发现 `AI_ITERATION_STATE.md` 有两块逐字节重复的内容（785 + 47 行），
  删前用 `difflib` 证明确实相同、删后确认每个 `##` 标题只出现一次（去重后 966 行；收工写"⑬ 收工最后一遍"时 1007 行，
  再补口径说明后又读到 1039 / 1040 / **1041** —— **行数不是不变量，"每个标题恰好出现一次"才是**，抄行数一律当场重跑 `wc -l`）。
  台账一旦有重复块，任何"按行号引用台账"的做法都会指到错的那一份 —— 这与"行号会漂"是同一条纪律的第二种失效形态。
  ③ **`mtime` 不能判"产物过期"**（本轮用它误判过一次 lint）：三个源文件的 mtime 是负控脚本原样恢复留下的，
  gradle 按输入内容哈希报 `compileDebugKotlin`/`testDebugUnitTest`/`lintAnalyzeDebug`/`lintReportDebug` **全部 UP-TO-DATE**
  ⇒ 产物对应当前源码。要无歧义只能 `--rerun-tasks`（`--rerun` 只作用于命名到的任务，不会带依赖重跑）。
  这条**收窄**了 ⑪ 写的"每个构建产物各自对时间戳"：时间戳用来起疑，不用来下结论。
  ④ **"0 命中"必须连同 pattern 一起抄**（收工最后一遍抓到，起因是安全组那行台账只写了语义没写命令）：
  `AMAP_KEY` 按裸 token `grep -rn "AMAP_KEY" app/src/main` 实测 **2 命中**（`AndroidManifest.xml:55` 的注释 +
  `DriverMissionMap.kt:71` 的 `BuildConfig.AMAP_KEY.isNotBlank()`，两处都是**引用**不是密钥值），
  **判据本体没坏**：审计 §55 E 那条 key 命令自带 `grep -v "@string/amap_key\|BuildConfig.AMAP_KEY"`，照原样复跑就是 **0**
  （key 真值只在 `local.properties` 的 `amap.key`，经 `app/build.gradle.kts:74` 注入 `buildConfigField`/`resValue`）；
  坏的是**状态板那行的简写**（只写"AMAP_KEY 字面量 … 全部 0 命中"没附命令）。简写行已改为"照抄 §55 E 的六条命令"。
  照简写按裸 token 抄会得出 2、看起来像"密钥进了源码"。这是"行号会漂"那一族的**第三个方向**：
  **离开命令的"0"不是判据，只是写字的人自己的结论** —— 抄数的人要么误报红线破了，要么把判据改成他数得出的那个数，两种都比写全 pattern 贵。


- **V-1（司机动作幂等）立的过程规矩**（三条都是**被自己的回归或误读抓出来的**，不是先想好的）：
  ① **新机制必须跑全量，只跑自己的新用例不算验证。** 闸门 `ActionGate` 是 `object Repo` 的字段 ⇒ 进程单例，
  它 10 秒的保留窗**跨测试类存活**：新两类跑完再跑老类，`ScalarEnvelopeContractTest.kt:191` 的
  `Repo.confirmHandover` 被本地闸吞掉，`server.takeRequest(...)` 返回 null → **NPE**。
  **而只跑新增那两类永远是绿的。** 处置是测试侧 `TestHost.resetActionGate()`（反射清 `locks`/`heldUntil`，
  与既有 `installPrefs` 同手法）—— **没有**为变绿放宽闸门，也**没有**在生产 `Repo` 上开一个只为测试的 `reset` 口子。
  ⇒ 推论：**引入任何跨用例存活的进程状态（单例、`Prefs`、缓存、闸门），必须在同一轮给测试侧复位**；
  以及 `Repo` 是单例 ⇒ 同一 JVM 里各用例必须用**互不相同的实体 id**。
  ② **管道退出码不是构建退出码。** 一次 `./gradlew … | tail -25` 被我当成"exit 0 = 构建成功"，
  实际那次 gradle 是 **BUILD FAILED**（`ActionGateTest` 里 `Unresolved reference 'ApiException'`）——
  管道的 `$?` 是最后一个命令（`tail`）的。⇒ 判构建一律
  `./gradlew … > /tmp/x.log 2>&1; echo GRADLE_EXIT=$?`，或 `set -o pipefail`。**这是"判据本身太弱"那一族里
  成本最高的一种**：它给的是**成功的假象**，而不是失败的假象。
  ③ **`eval` 拼出来的 grep 会伪装成"违规已归零"。** 复测窄/宽口径时用 `eval` 组装 pattern，
  shell 把 `haversine(…)` 当命令执行，报 `haversine: command not found` + 输出 **0 行**，
  读起来像"直线关键字全清了"。真数是 窄 9 / 宽 44 / `GeoMath` 27。
  ⇒ 这类"每轮手抄的长 pattern"已固化成脚本 `tools/check_authenticity_counts.sh`（**判据不进 eval**），
  并把"命令写成脚本、台账只写脚本名 + 当轮数字"升为默认做法。
  ④ 台账自身也算产物：本轮一次编辑吃掉行尾换行，把"签名文件 / 日志拦截器"那行并进"本机地址"行，
  肉眼扫过去看不出来 ⇒ 用**结构自检**（表内每行以 `| ` 开头、行内不得出现 ` || `、每个 `##` 恰好一次）复核。
  ⇒ **永久规矩：表格行是"单物理行"，两个方向都会坏**（并成一行 / 拆成多行），而**拆行比并行更难发现**——
  拆开的续行看起来就是普通正文，渲染时整张表在那一行之后**静默断掉**。N-1 收台账时抓到 **6 个坏表行**
  （§九 一行被拆成三条物理行、已至少坏一轮；另有 **5 行**把**未转义的裸 `|`** 写进代码段，整行变成 5~8 格：
  `AI_ITERATION_STATE.md` 常驻自检 R/I/SR 三行、本文件 §九 两行），全部按 `\|` 约定或合并修好。
  ⇒ 判据落成脚本：**`python tools/check_ledger_tables.py`**（口径 = 同一张表内每行**未转义**竖线数等于表头行；
  **不是全局常数**，三列表 4、五列表 6）。本轮实测：五本台账 **TOTAL FLAGS 0 / rc=0**，
  报告落 `tools/ledger_table_report.txt`；三条负控（M1 拆行 / M2 剥掉一个 `\` / M3 两行并一行）**各自 rc=1**，
  未注入的原件副本 rc=0 —— 即"0 flags"是判据能红的情况下量出来的，不是空跑。

- **V-2（壳路由与分域闸）立的过程规矩**（四条判据教训 + 一条台账事故，除 ① 外都是本轮被自己的产物抓出来的）：
  ① **验收点落在"渲染结果"上时，先把决策提成纯函数，再让判据去数 UI 的调用点。** 工程没有 Robolectric ⇒
  `NavHost` 的 `startDestination`、深链跳转在用例里跑不出来，"司机绝不能看到普通用户首页"这条 Phase 1 验收
  只能落在两个 `internal` 函数上（`nav/ShellDecision.kt` 的 `startDestinationFor` / `deepLinkTarget`）+ 结构探针。
  ⇒ 关键推论：**提出来的那个函数必须是唯一答案**，否则会出现"新函数全绿、UI 走的还是旧逻辑"这种最危险的绿。
  所以 SR-b/SR-c 各配一条**反向**判据：`BusPostRoot.kt` 里旧的 `val start = when` 残留 = 0、旧的私有
  `deepLinkAllowed` 全工程残留 = 0。**这两条比正向的那两条重要**，且都是"期望 0"——见 ②。
  ② **"期望 0"的判据，交付的同一轮必须证明它能非 0。** 本轮把三条规矩固化进了脚本本体：
  **先验文件存在**（`grep` 找不到文件时输出是空的，与"真的 0 命中"在 stdout 上不可区分 ⇒ 计数循环前先
  `[ -f "$f" ] || exit 2`，本轮基线就是被这条救回来的）；**负控先验基线**（负控脚本开头先照抄跑一遍判据，
  基线不绿则整份负控不采信 —— 破口注进一份本来就红的判据里，"变红"没有任何信息量）；
  **注释行不是代码**（`NC='(^|[0-9]+:)[[:space:]]*(\*|//)'` 过滤，否则"说明这里为什么禁止"的那行注释
  会被数成破口，沿用 T-ah / I-h 的同款处置：**加过滤，不删注释**）。
  另：多分支 `|` 的正则必须 `grep -E` —— 本轮 `SR-e`/`SR-f` 第一次全绿是因为 BRE 把 `A|B` 当字面量，
  判据永远不会响（与 ⑫ 的 T-t 同一个坑，第三次踩）。
  ③ **产物对不对，看符号与内容哈希，不看总大小。** 本轮 `assembleDebug` 出的 APK 与上一轮**同字节**
  （57,941,374 B），第一反应像"构建根本没重跑"；真数是 `dex` 里 `ShellDecisionKt` **3 处符号**、旧函数名
  `deepLinkAllowed` **0 处** ⇒ 确实重编了，同字节是压缩后的巧合。同一条也适用于 lint：XML 97,380 B 与
  V-1 的 txt 31,126 B 是**两种口径的产物**，拿大小对比会得出"告警变多了"这个不存在的结论。
  ⇒ 固定口径：**比较产物先比较它是哪种产物**（格式/任务名），再比内容（符号、`executed`、哈希）。
  ④ **"改用例数"与"改判据下限"必须同轮**（沿用 ⑬ ①）：`SR-a` 判 `@Test` 条数 `≥9`，本轮新类恰好 9 条。
  下一轮再加用例时这条**不会自动变红也不会自动抬**，所以负控里专门有一支"把计数改小必须让判据变红"。
  ⑤ **台账写入事故（本轮真实发生并已恢复）**：一次就地重写用了 `io.open(path,'w',encoding=<错的名字>)`，而
  **截断发生在编码校验之前** —— 文件先被清成 0 字节，之后才抛 `LookupError`，`AI_ITERATION_STATE.md` 全损。
  恢复方式：CLI 的 file-history 基座（132,332 B 的破坏前快照）+ 逐条重放本会话 72 次编辑，得到 164,888 B /
  1,308 行，再应用本轮两行自检表改写 → 现值 **165,127 B / 1,309 行**。**判定"恢复完成"用的是破坏前实测读到的
  那 12 个行号+锚点**（`88 BLOCKED` / `97 NEXT` / `101 TEST_STATUS` / `235 ## CURRENT` / `302 ## 常驻自检` /
  `318–324` 自检行…）逐一对上，不是"内容看着差不多"。⇒ 三条永久规矩：
  **动台账之前先 `cp -p` 一份带时间戳的基座**；**永不 `open(台账, 'w')`，一律写 `*.tmp` 再 `os.replace`**；
  **恢复必须自带判据（行号/锚点/字节/行数四项实测），无判据的恢复等于二次破坏**。
  （同一条事故也说明：**没有版本库时，"随手重写整本台账"这个动作本身就是风险** —— 见 §九 `ENVIRONMENT_BLOCKED` 那行。）

- **V-2b（角色与后端画像同源）立的过程规矩**（四条，① ④ 是被自己的产物抓的，② ③ 是被负控抓的）：
  ① **"从未 / 从没记过"这类全称否定，写进台账之前必须先 grep 并把命令与输出留在当轮。**
  本轮一次草稿写"四本台账从没记过 release 的 HTTPS 闸门"，grep 当场证伪 ——
  `AI_ITERATION_STATE.md` 的生产化阶段、`DECISION_LOG.md:85`、审计 §1104 都记过 `build.gradle.kts:160-176` 那条配置期校验。
  全称否定是**最容易写错、最难被发现**的一类句子：它不指向某个具体行，没法靠"点开看看"验证，而它一旦进了台账，
  下一轮就会拿它当依据去做"补记"甚至"补实现"。⇒ 已把两处相关表述改成事实，并把这次自纠本身记进审计 §76 第六条。
  ② **守卫类逻辑动手之前，先实测"后端的否定长什么样"。** 这是本轮的根因级教训：
  后端撤档回 `{"code":0,"data":null}`，而 `Api.kt:314-315` 把 null data 读成**空 JSONObject** ——
  旧判据 `o.length() > 0` 于是让"清绑定"成为**不可达代码**：函数写得再对，永远不执行。
  ⇒ "撤销 / 降级 / 清空 / 登出"这类分支的失败形态不是抛异常，而是**安静地不执行**，单测里不配一条
  "否定真的走到了"就完全看不出来（本轮 `DomainSplitTest` 那条旧用例正是把 `data:null` 归进"未知"，方向反了，已连 KDoc 一起改）。
  ③ **单向守卫要钉成用例，不靠口头说明。** `reconcileRole` 只在"明确否定"时把 DRIVER 降成 USER，
  **永不反向升级**（升级等于替后端造角色，指令 §5/§123 禁止）。这条"单向"由
  `只有明确否定才把司机角色降级` + `降级是单向的：永远不会把用户升成司机` 两条用例钉住 ——
  否则下一轮"顺手做成双向同步更智能"会全绿通过。
  ④ **同一轮里"修代码"和"修判据"是一对，缺一就是假绿。** 本轮 `SR-d` 下限 2 → 4 与新增 `SR-d2/d3`、
  RR 全套判据，是和那两个纯函数**同一轮**落的；反过来若只改代码，"三态退回布尔"这种退化没有任何东西会报红。
  判据自身的四条缺陷（awk 收窄范围 / 相对行号 / 整行字面量 / `Prefs\.role = [a-z]` 避开读）明细见 §八 ⑰。

## 七、安全红线（沿用生产化阶段）

- 签名文件禁止进入源码工程；release keystore / 真实 token / 密码 / secret 不得出现在源码（`git grep` 自查）。
- 源码中不得固定真实服务器 IP；正式包绝不能使用 `127.0.0.1` / `localhost`。
- release 关闭 cleartext HTTP。
- 旧文件**不要直接删除**：`DriverNavScreen` 降级为 DEBUG ONLY / 兼容入口，而不是删掉。

## 八、每次开工 / 收工自查（命令必须实测，不许凭印象写"通过"）

在正式代码里 grep。命中分三类处置：**修** / 降到 debug source set / 在命中处写明"这是禁止说明或已标注的非里程值"。

```bash
cd app/src/main/java
# ① 几何与伪造关键字。注意：不要把 `demo` 加进这个 pattern —— `Payload.demo` / `Api.demoAllowed` /
#    `DEMO_ALLOWED` 是贯穿全工程的诚实性开关，加进来会有 242 条正当命中，自检就废了。演示面单独用 ② 查。
#    另：给了 -e 之后第一个非选项参数会被当成文件名，所以全部模式都要 -e。
#    ⇒ **V-1 起这两条（窄/宽口径）连同 GeoMath / DemoData / 假步进的计数固化为脚本**：
#      `bash tools/check_authenticity_counts.sh` 一次打印 5 个数 + 宽口径按文件构成（加总须等于宽口径总数）。
#      固化理由不是省事：**手抄 + `eval` 会把 `haversine(...)` 当成命令执行**，报 `command not found` 后输出 0 行，
#      读起来正好是"违规已归零"（见 §六 V-1 ③）。台账只写脚本名 + 当轮数字，不再重抄 pattern。
grep -rniE "euclidean|haversine|straight|straightLine|mock|fake|stepping|two.?point|连线|直线" . \
  | grep -vE -e "core/map/(RealRoadRouteValidator|CoordinateSystem|RouteSource)\.kt" \
             -e "NavIcon\.STRAIGHT|enum class NavIcon" -e "isFakeBoldText" -e "MockWebServer" \
             -e "DemoData\.kt" -e "WeatherCard\.kt" -e "Repo\.kt:.*(mock 标注|适合当日运输)" \
             -e "Models\.kt:[0-9]+: *(//|\*|/\*)" -e "Models\.kt:.*(Haversine 直线距离|mock|直线估算|不是道路里程|路网不可用)"
#    ⚠ ⑩ 之前的写法是 `-e "Models\.kt:(1[1-4][0-9]|2[0-9][0-9])"`（**行号**），它少了一个结尾冒号，
#    于是 `Models.kt:1444` 里的 "144" 前缀就命中 `1[1-4][0-9]` —— **Models.kt 1100..1499 整段的命中都被静默吞掉**
#    （⑩ 实测被吞 7 行，其中 6 行是本轮新写的可达性说明）。改成按内容过滤后总数仍是 30，
#    但差别是：以后有人在 `Models.kt:13xx` 写一行真违规（例如 `distanceMeters = haversine(...)`）现在看得见。
#    这条与 §55 C 记录的"⑥ 行号白名单漏放两行"是同一类缺陷的**反向形态**：行号白名单要么漂出真违规，要么宽到吞掉真违规。
# 2026-09-29（P1-2 ⑥ 后）基线：**30 行** = 4 违规 + 19 禁止性说明 + 4 带标注远近值（含唯一直线展示出口）+ 3 straight* 标识符。
#   **⑨（段状态机收口）后复跑仍是 30 行**，其中只有行号漂：WorkbenchScreen 201/204/889→203/206/909、
#   207/218→209/220，SendScreen 503→504（内容一字未改，都是 §39 加 KDoc 造成的位移）。
#   白名单写法变更：天气兜底那三行**按内容**过滤（`Repo\.kt:.*(mock 标注|适合当日运输)`），不再按行号。
#   原因：⑥ 在 Repo.kt 头部插入 14 行，兜底代码从 :241/:247/:252 漂到 :255/:261/:266，行号白名单 `2[45][0-9]` 当场漏掉两行，
#   探针从 30 涨到 32 —— 那不是新违规，是白名单自己漂了。**行号白名单会漂，内容白名单不会**（改文案就重新报红，是 fail-closed）。
#   **⑩（可达性真实化）按上面那条改过的 pattern 复跑仍是 30 行**，按文件的构成是
#   `DriverNavScreen 8 / WorkbenchScreen 5 / MissionMarkers 5 / MapCanvas 3 / Repo 3 / BusScreen 2 / SendScreen 1 / DriverMissionMap 1 / TraceView 1 / BusMap 1`
#   —— 抄数时请连同"是哪一条 pattern"一起抄，89 行（无白名单）与 30 行（有白名单）不可比。
#   **⑪（公交 ETA 真实化）复跑仍是 30 行**（本轮没有新增直线/伪造关键字命中；`DemoData` 那 4 处新的
#   `GeoMath.distance` 命中 `DemoData\.kt` 白名单，所以 ① 数不到它们 —— 这正是 ③ 那条计数存在的原因：
#   **① 抓"说了假话"，③ 抓"算了直线"，两条不可互相替代**）。
#   **⑫（货运侧 ETA 家族）复跑：宽口径 30 → 42，窄口径 4 → 9，`GeoMath` 仍是 27。**
#   涨的 12 行构成 = `ParcelScreen 7`（5 处带"（直线估算）"口径的界面文案 + 2 处来源说明注释）
#   + `HomeScreen 3` + `AlertEngine 2`，窄口径那新增 5 行是同一批**口径说明注释**（AlertEngine:90、HomeScreen:233、
#   ParcelScreen:340/441/442），内容全在说"这个分钟数是后端 Haversine ÷ 25km/h 估的，不许当道路 ETA"；
#   违规部分（§24 假步进 4 行）数字未变。**③ 保持 27 是关键**：客户端没有新算一次直线，只是把服务端
#   `GeoDistanceUtil` 已经算好的 `carrierEtaMinutes` 读出来并标注口径 —— 这正是"① 涨、③ 不涨"该有的样子。
#   42 行按文件的构成：`DriverNavScreen 8 / ParcelScreen 7 / WorkbenchScreen 5 / MissionMarkers 5 /
#   HomeScreen 3 / MapCanvas 3 / Repo 3 / BusScreen 2 / AlertEngine 2 / SendScreen 1 / DriverMissionMap 1 /
#   TraceView 1 / BusMap 1`（与 ⑩ 的构成相比，⑫ 只是让 `ParcelScreen`/`HomeScreen`/`AlertEngine` 三本旧书进了榜，
#   其余文件的命中数一字未动 —— 没有新文件、没有新违规类型）。
#   处置选的是**写明口径**而不是扩大白名单把它压回 30：为让数字好看而删掉口径说明，等于把假话说干净。
#   **⑬（精确到站 ETA + 三个 `distanceKm` 口径）复跑：宽口径 42 → 44，窄口径 9 未变，`GeoMath` 27 未变。**
#   涨的 2 行 = `Models.kt` 的 `distanceCaliberText`（"计价里程 … km（直线距离×路网系数，非道路里程）"）
#   + `SendScreen.kt` 上方那句来源注释 —— **两条都是口径说明，不是新算法**：⑬ 第一次把后端
#   `AppSendQuoteRespVO:21-22` 那个"直线 × 路网系数"的计价里程摆上界面，摆的时候必须同行带口径
#   （§71 给承运分钟立的规矩，⑬ 把它推广到"里程"这一族）。44 行按文件的构成：
#   `DriverNavScreen 8 / ParcelScreen 7 / WorkbenchScreen 5 / MissionMarkers 5 / Repo 3 / MapCanvas 3 /
#   HomeScreen 3 / SendScreen 2 / BusScreen 2 / AlertEngine 2 / TraceView 1 / Models 1 / DriverMissionMap 1 / BusMap 1`
#   —— 与 ⑫ 相比只动了 `SendScreen 1→2` 与 `Models 0→1`，违规（§24 假步进 4 行）与"唯一展示出口"一字未变。
#   ⚠ 这两行的**行号**在写完几分钟后就漂了（`distanceCaliberText` 实测从 :1641 到 :1649）——
#   本节所有行号都只是"当时实测"，**引用一律按内容找**（本轮第 N 次现场复现同一条纪律）。
#   ③ 保持 27、窄口径保持 9 是这轮的关键读数：⑬ 把三个 `distanceKm` 改成可空并给两处加了口径，
#   **没有新增任何直线计算点，也没有新增任何"距离 → 可用性"的判定**。
#   判据是"违规不得新增、正当类不得变成伪造几何"，不是"必须为 0"：
#   · 违规（§24，未修）：DriverNavScreen.kt :103/:180/:182/:343 `stepping` —— 这 4 行归零才算修好；
#   · 禁止性说明（正当）：Repo:699/900/941（⑥ 前是 674/852/893，Repo 头部插了守卫代码整体下移）、
#     BusMap:58、MapCanvas:50/188/233、TraceView:47、
#     DriverNavScreen:244/265/280/285、BusScreen:484、DriverMissionMap:167、MissionMarkers:94/95、
#     WorkbenchScreen:203/206/909（⑨ 前是 201/204/889，§39 的 KDoc 让它漂了）—— 都在说"不拿站点连线顶替 / 不用直线补 / 直线不当门槛"；
#   · 带标注的远近（正当，且是**唯一出口**）：BusScreen:498、SendScreen:504（D-013；⑨ 前是 503）、
#     MissionMarkers:101/102 —— 文案固定写"仅表示远近，不是道路里程"；
#   · 标识符含 straight（正当）：MissionMarkers:97、WorkbenchScreen:209/220（`straightMeters` / `straightDistanceToTarget()`；
#     ⑨ 之前记的是 :207/218，§39 在其上方加了 KDoc 所以漂了两行 —— 又是"行号白名单会漂"的现场，按内容找）。
# 口径对照：本条是**宽口径**探针（⑩/⑪ 30 行 → ⑫ 42 行）；`ANDROID_PARITY_AUDIT_V2.md` §55 C 是**窄口径**，
# P1-1b 后实测 **4 行**（就是上面"唯一出口"与其两个调用点），⑫ 后 **9 行**（+5 全是口径说明注释，见上），
# ⑬ 后**仍是 9 行**（这轮改的是"三个 distanceKm 判 null"与一处口径文案，没开新的距离展示出口），
# 修 §32 之前是 0 行 —— 因为那时工作台根本不做距离展示出口。
# 两条探针用途不同，引用时必须说明是哪一条，不要互相"对不上"，更不要为了让窄口径归零而删掉带标注文案。

# ② 演示面（DemoData 引用点）：必须只有 3 个文件
grep -rln "DemoData" . | sed 's#^\./##'
#   Api.kt（`if (!demoAllowed) throw` 之后才回落）、DemoData.kt 自身、Bits.kt（DemoNote）。
#   任何 UI/Repo 直接 import DemoData 都是违规（正式包不得有 demo 兜底，硬规则 18）。
#   ⑬ 复跑仍是这 3 个文件（新字段 `etaToNextStationMinutes` 的演示形状只在 DemoData 里发，见 T-ag）。

# ③ 直线距离的真实调用点（关键字不出现，只能这样查）
grep -rn "GeoMath" .    # ⑪ 实测 **27 行**（⑦ 后基线 22）：DemoData 11 / BusScreen 3 / RealRoadRouteValidator 3 / Locator 3 / DriverNavScreen 2 / MapCanvas 2 / WorkbenchScreen 1 / Repo 1 / Models 1
#   （P1-2 ⑥ 后是 19 = 13 + 4 + 1 + 1；⑦ 在 Locator.kt 加了 +1 import、+1 调试日志调用、+1 说明注释；
#    ⑪ 的 +5 **全部落在 DemoData**：:623/:628/:633/:638 四处给 nearby 兜底造 `distanceKm`（镜像后端
#    `AppBusNearbyRespVO:137` 那个"用户与车辆的直线远近"，只用于排序与"离你 N km"），:616 是它们的用途注释。
#    **生产代码没有新增直线调用点** —— Models 那一行是 `byUserDistance()` 的 KDoc。抄数时按文件构成抄，别看总数。）
#   §32 修后没有"违规调用点"了，但判据要读准：WorkbenchScreen 那一处**仍在**（现在 :214，藏在 private
#   straightDistanceToTarget() 里），它被允许**仅因为**唯一消费者是带标注文案且不再参与按钮可用性。
#   新增调用点必须自带"这是远近/下界/几何，不是道路里程"的说明，且**禁止**出现"距离值 → 按钮 enabled / 业务状态"的用法。
#   ⑦ 新增的那一处（Locator.kt:103，`BuildConfig.DEBUG` 里打印 WGS84→GCJ02 的差值米数）**已按同一条规则写明**：
#   :100 的注释原文是"这里的 GeoMath.distance 量的是**两个坐标系之间的偏移量**，只用于真机核对，不是道路里程、不是远近展示"。
#   这不是"为了过判据而写的废话注释" —— 它规定的就是这个数的用途边界：只准 debug 日志核对，不进任何界面文案、不参与任何可用性判定。
#   **⑬ 复跑仍是 27 行，构成一字未改**（本轮把 `BusLine`/`TransportLeg`/`RoutePreview` 的 `distanceKm` 改成可空、
#   给两处加了口径，**没有新增任何直线计算点**）—— 这是 ⑬ 最该抄的一个数：口径说明涨（① 42→44）而计算点不涨，
#   说明改动确实落在"把话说清楚"这一侧，不是"多算一次直线"那一侧。

# ④ 假步进（§24 的验收判据，当前**未达标**，不许为了让它变绿而删掉这条）
grep -n "stepping\|delay(4_000\|stepIndex + 1" com/shanxiang/buspost/ui/driver/DriverNavScreen.kt   # ⑬ 复跑 6 处（:103/:146/:180/:182/:183/:343），目标 0
#   ⚠ 这条命令原先写的是 `ui/driver/DriverNavScreen.kt`（少了 `com/shanxiang/buspost/` 前缀），
#   在 `app/src/main/java` 下直接跑会报 "No such file or directory"、计数**假归零到 0**。
#   ⑬ 收工时照抄本行命令就撞上了 —— 记下来是因为它和第 ①/⑥ 条是同一种缺陷：**判据的路径/锚点写错，
#   报错方式是"安静地没有命中"**，与"真的修好了"在输出上长得一模一样。引用这条数之前先看它有没有走到 grep。

# ⑤ 结构判据：删除即归零、回潮立刻非零（§32 / §63 / §62 的防回潮；路径仍相对 app/src/main/java）
grep -rn "canArrive\|ARRIVE_RADIUS_METERS" .                     # 空——非空说明 GPS 门槛又被加回来放行到站
grep -rn "nextStationDistanceMeters" com/shanxiang/buspost/ui    # 空——非空说明旧直线字段又被当里程显示（data 层的 1 声明 + 1 解析是刻意兼容，别改成全工程判空）
grep -rn "MissionStop" com/shanxiang/buspost | wc -l              # 实测 20（下限判据）
grep -rn "timelineOf\|\.ordered(" com/shanxiang/buspost/ui/driver | wc -l   # 实测 3：任务点顺序只有一处来源
#   ⑬ 复跑这四个数一字未变（0 / 0 / 20 / 3）。⑬ 大量改的是 `distanceKm` 的可空性与口径文案，
#   没有碰 GPS 门槛、旧直线里程展示出口和任务点单源 —— 这一条是"这轮没有把结构判据顺手改松"的证据，
#   所以它必须被复跑并抄数，即使它"没有新内容"。

# ⑥ 安全：--include 会漏掉 .kts，构建脚本要单独查
grep -rn "127\.0\.0\.1\|localhost" app/src/main                 # 空
grep -n  "127\.0\.0\.1\|localhost" app/build.gradle.kts         # 只剩注释(:18/:22) + 构建期拦截(:167/:168)
find . -name "*.jks" -o -name "*.keystore"                      # 空（签名材料在工程外 ../buspost-signing-backup）
grep -rn "HttpLoggingInterceptor" app/src/main                  # 空（报文与 token 不进 logcat）
#   ⚠ 这四条是**工程根目录**口径，而本节开头的 `cd app/src/main/java` 会让它们全部"空对空"通过 ——
#   与 ④ 那条同一病灶。跑法：从工程根另开一次，或把路径换成 `.`（⑬ 就是从根跑的）。
#   ⑬ 实测（根目录口径）：`127.0.0.1|localhost` 在 `app/src/main` **0** 命中；`app/build.gradle.kts` 仍是
#   4 行（2 注释 + 2 构建期拦截）；`*.jks`/`*.keystore` 在工程内 **0**；`HttpLoggingInterceptor` **0**；
#   `app/src/{debug,release}/AndroidManifest.xml` 的 `usesCleartextTraffic`：debug `true` / release `false` 未变。
#   本轮没有新增网络出口，也没有把任何演示面带进 release（② 仍是 3 个文件）。

# ⑦ 用户域 / 司机域分域探针（硬规则 1 + 审计 §29/§64；P1-2 ⑥ 加入）
# 判据已落成脚本，直接跑并把 8 个数字抄进 AI_ITERATION_STATE.md 的常驻自检表：
bash tools/check_domain_probes.sh        # 路径相对 app/src/main/java，退出码非 0 即分域破口
#   P-a 用户域司机读点 0 · P-a2 用户域在途/司机档案读点 0 · P-b driverId 字面量拼装 0
#   P-b2 driverIdParam 引用 22（1 KDoc + 1 声明 + 20 调用点；P2-1 新增 driverUnreadCount 后 19→20）· P-c 角色切换入口 2
#   P-d 深链分域闸 2 · P-e 司机通知落司机页 1 · P-f 未绑定文案引用点 5
#   名字要写全，不能用 `Repo\.driver` 前缀草草了事：`Repo.product(` / `Repo.productsPage(` 是商城接口，
#   前缀式 pattern 会把用户域的正常调用报成违规，判据一旦误报就没人再看它。
#   P-b2 用裸词 `driverIdParam`（P2-1 实测 22）而不是 `driverIdParam()`（实测 21）：后者漏掉声明行本身的 KDoc 引用，
#   数字对不上就没人能判断是"少了一个调用点"还是"注释被删了"。
#   司机通知的 trace/* 检查看 `route = ` 三处命中：:53 属于司机分支（必须是 Routes.DRIVER_MESSAGES），
#   :70/:87 属于用户分支（:37-58 的司机分支先 return，落不到 trace/*）。

# ⑧ 定位与上报探针（硬规则 10「禁止 Fake GPS」+ §17 单一换算入口 + §45 不假上报；P1-3 ⑦ 加入）
bash tools/check_location_probes.sh    # 退出码非 0 即定位/上报破口
#   L-a 2（`CoordinateTransform` 真实生产调用点：Locator 出口 + RealRoadRoute.gcj02Points）
#   L-b 0（`Geo(..latitude)` 构造点不得越出 Locator —— 换算只能在**一处**发生，多处转就多次偏移）
#   L-c 0（上报报文不得出现客户端时间键 locateTime/clientTime/gpsTime/reportTime/time —— 后端 VO 没有这些字段）
#   L-d 0（旧"当前没有在途班次，发车后再上报位置"拦停文案 —— 后端 shiftId 已可选，无班次也要能上报）
#   L-e 0（手写偏移公式不得越出 CoordinateSystem.kt —— §17 修改方案第 1 条"业务代码不得手写偏移公式"）
#   L-f 0（第二定位源：`AMapLocationClient` 没接就是 0，接了就要显式改判据，禁止两套 GPS 混用）
#   L-g 1（`DriverLocationService.kt` 里"已丢弃"的出现次数 —— 判据范围是**前台通知那一个拼装点** `droppedSuffix()`；
#          删掉它就等于弱网丢点重新变成静默丢。`WorkbenchScreen` 的 InfoRow 是第二个展示面，**不在这条计数里**，
#          引用时不要说成"全工程只有一处已丢弃"）
#   L-h 0（绕过 `convert()` 直接调底层偏移函数）
#   与 ⑥/⑦ 同一课：判据全部按**内容/函数名**，不按行号；每条都配了负控制（L-b/L-e 已实测会被人为破口报红）。

# ⑨ 标量信封 / 角标 / 交接凭证照片探针（硬规则 12「宁可暂时不能导航，不允许假导航」的兄弟条：
#    **宁可没有上报，不允许假上报**；审计 §22/§23/§52/§7；P2-1 加入）
bash tools/check_envelope_probes.sh    # 退出码非 0 即契约信封破口
#   E-a 0（`unread`/`driverUnread` 的**外部** `.value =` 直写 —— 两个流已是 `asStateFlow()`，这条防有人把它们改回公开可变）
#   E-b 3（`Payload.longOf/strOf` 在 Repo 的调用点**恰好** 3：unreadCount / driverUnreadCount / uploadPhoto。
#          多了 = 有人把对象响应硬当标量读；少了 = 某条链退回旧写法）
#   E-c 0（旧对象键读裸标量残留：`multiNum("count"` / `multiStr("url","fileUrl")` 这类写法必须清零）
#   E-d 0（`items.map { it.copy(status = …) }` 本机改写交接状态 —— §26/§27 那一类"ack 之后自己覆盖列表"）
#   E-e 1（`status = o.multiStr("statusName"` —— 交接状态优先读后端给人读的 `statusName`，
#          pattern 精确到"赋值+首键"，否则模型 KDoc 里那句说明会被算成一个合法读点而虚高）
#   E-f 1/1（凭证照片闭环两头都有：模型解析 `photoUrl = o.multiStr("photoUrl"` 1 处 + 页面渲染 `model = task.photoUrl` 1 处。
#          只数前者会漏"解析了没人显示"，只数后者会漏"显示了没解析"）
#   E-g 1（上传失败拦停整句 `凭证照片上传失败，交接未提交` —— 缺凭证必须先挡在提交前，
#          因为后端对已完成交接是幂等直接 return，先提交后补等于永远补不上）
#   E-h 1（`toPayload` 里 `scalar = data` 恰好 1 处 —— 信封的标量出口唯一，禁止再开第二条 data 通道）
#   负控制已在 Git Bash 逐条实测会报红。**注意**：`python subprocess.run(["bash", …])` 在 Windows 上解析到
#   `bash.exe`→WSL 转发，输出是 UTF-16 乱码且 exit 码全假；判据必须在真正的 Git Bash 里跑，
#   且要看到 8 个数字 + 末行中文"契约信封判据通过"，不能只看 exit 0。

# ⑩ 状态映射 / 司机段状态机探针（硬规则「服务端是唯一真值」+ §二"客户端只读不推"；
#    审计 §18/§8/§10/§26–§28/§36/§39；⑨ 加入）
bash tools/check_status_probes.sh        # 退出码非 0 即状态映射/段状态机破口
#   S-a 0（段状态残留数字比较 `leg.status >= / == N`，注释按结构剥掉后再数）
#   S-b 0（用文案子串猜状态，`contains("完成")` 那一类）
#   S-c 0（页面里**数字分支**的 `when(…status)` 本地码表 —— 第三张表就是靠这条抓住的）
#   S-c2 2（车辆运行态 `when(v.status)` **恰好** 2：HomeScreen + BusScreen，那是 Android 自有枚举不是后端码，
#          归零反而说明有人把车辆状态塞进了别的出口；所以这里是"2"而不是"0"，别当违规去消）
#   S-c3 6 / S-d 6（`ReviewStatus.of` 引用点 / `ServerStatus.kt` 的枚举数 = 货运·段·商城·审核·任务点·交接；
#          S-d 从 6 变 7 而 S-k 用例数没涨 = 建了表没钉测试）
#   S-e 10（未知码 `STATUS_UNAVAILABLE_LABEL` 引用点，下限 ≥6）
#   S-f 0（错误注释指纹"待派单"）· S-g 0（`DemoData` 里重复的货运码表）
#   S-h 3（缺键 -1 哨兵**恰好** 3：`TransportLeg` / `MissionStop` / `Repo.traceOf` —— 读不到必须是 -1 不是 0，§14）
#   S-i 64（页面/核心引用映射枚举的点数，下限 ≥20：**改道越多越涨**，掉下来说明有人绕过单源）
#   S-j 4（`docs/STATUS_MAPPING.md` 段码表行数）· S-k 17（`StatusMappingContractTest` 用例数，下限 ≥15）
#   S-la 0（`nextStatus()` / `legStep()` / `LEG_ACTIONS` —— §26/§27 的硬判据，本机自造下一个状态必须一个不剩）
#   S-lb 0（`copy(status = …)` 改写后端段状态）
#   S-lc 2（出现段动作字面量的文件数 ≤3；实测命中 = 映射器 + DemoData 的演示报文，
#          `Repo.legAction` 只收 `action: String` 形参所以不在计数里 —— **判据的说明也会错，数对了要回头看命中的是哪两类文件**）
#   S-ld 2（引用 `DriverLegStateMapper` 的司机页数 ≥2：RoutesScreen + HandoverScreen，不含映射器自身）
#   S-le 2（页面直调 `Repo.legAction` **恰好** 2，且两处后面必须紧跟回读 `Repo.driverLegs()` / `Repo.currentLeg()`；
#          ⑨ 之前是 3，多的那一个正是把 `"shift-$id"` 当 `legId` 的假调用。这条要连着看下一行在不在，光数数会放走"不发回读"的回潮）
#   与 ⑦⑧⑨ 同一课：全部按**内容/函数名**过滤，不用行号白名单；每条"恰好"与"下限"的口径写进脚本 echo 行，
#   抄数时连口径一起抄，不要只抄数字。

# ⑪ 可达性三态探针（硬规则「只有 REACHABLE 能说可以运输」+ §14；审计 §34/§11/§66；⑩ 轮建成、本轮补登此表）
bash tools/check_reachability_probes.sh  # 退出码非 0 即可达性破口
bash tools/reach_negative_controls.sh    # 21 条逐条破口，2026-09-29 复跑 21/21 ✔
#   R-a 0（界面按布尔 `reachable` 分支）· R-b 0（读后端没有的 `distanceMeters`）
#   R-c 0 / R-c2 1（**互锁的一对**：生产代码 `"STATION"` 归零 + 测试报文留一份反面 fixture；
#          删了 fixture 这条判据就没人证）· R-d 0（站点名顶替用户原始地址）
#   R-e ≥10（`ReachabilityState` 引用点，实测 25）· R-f 2（`multiBoolOrNull` 声明 1 + parse 使用 1）
#   R-g 2（`walkMinutes: Int?` 与 `distanceKm: Double?`**只数 `data class Reachability` 类体** ——
#          `BusVehicle` 也有可空 `distanceKm`，整文件计数会把两个契约的字段混进一条判据；⑪ 摘出去而不是抬数字）
#   R-h 0（裸拼 walkMinutes）· R-i 1（直线来源如实标注）· R-j ≥17（只数行首生效的 `@Test`）
#   R-k 1 / R-k2 0（解析出口数**出现次数不是行数**；不许直接构造模型）
#   R-l 5（演示兜底真实形状字段数）· R-m 2（服务方式唯一出口，出现次数：定义 1 + 调用 1）
#   R-n ≥2 / R-n2 0 / R-n3 ≥1（UNKNOWN 分支存在 / 分支里不许出现"可就近|可以运输" / 免责话还在）
#   R-o 1 / R-o2 2 / R-p ≥1（可上门却没站点必须提示手选 / "站点不在可选列表"可达+不可达各一处 / 文字地址缺失不藏行）
#   R-n2/R-o/R-p 是**语义**判据不是结构判据：分支存在不等于分支说得对。
#   ⚠ ⑩ 的原话保留在这里："21 条判据全绿的源码可以同时编译不过"。

# ⑫+⑬ ETA / 里程口径探针（公交侧 ⑪ + 货运侧 ⑫ + 里程与精确到站 ⑬；
#    硬规则「宁可没有 ETA，不允许 0 分钟」与「直线估算必须自报口径」，§38 + §68 + §71 + §72；
#    ⑪ 建到 T-l，⑫ 扩到 T-z，⑬ 扩到 T-ah 并把 ⑫ 的两条"尾部打印残留"升格成判红）
bash tools/check_eta_probes.sh           # 退出码非 0 即破口（末行应为"ETA 判据通过（公交侧 + 货运侧 + ⑬ 里程口径）"）
bash tools/eta_negative_controls.sh      # ⑬ 后 **50 条**逐条人为破口，每条必须 exit 1 且报的是那一条（2026-09-29 实测 50 ✔ / 0 ✘ / 0 △）
#   T-a 0（ETA 家族 `?: 0` 塌缩处数；⑫ 起**货运侧不再是脚本尾部打印的残留**，由 T-r/T-s/T-u 判红）
#   T-b ≥10（`multiNumOrNull` 用量，⑬ 实测 **30** = 声明 1 + 公交侧 + ⑫ 货运侧 + ⑬ 的里程/时长可空读法）
#   T-c 9（`BusVehicle` 可空数值字段**恰好** 9，`sed -n '/^data class BusVehicle/,/^) {/p'` 只数类体 ——
#          与上面 R-g 是同一条纪律。⑫ 前是 8，⑬ 的 +1 是 `etaToNextStationMinutes: Double?`。
#          ⚠ 这条从 8 改 9 是在**读代码之前**加的：先确认 `AppBusRespVO:49-50` 真有这个键，再动判据）
#   T-d1 0（客户端自编进度：正则 `etaMinutes[a-zA-Z!?.]* *[/*+-] *(40|60)`，允许 `!!` 才抓得住）
#   T-d2 2（`progress = v.roadProgress` 在 `ui/user` **恰好** 2：线路页 + 首页；进度条只由后端键驱动）
#   T-e 0（公交/提醒路径把 0 当"还没到"，作用域是 `notify widget data`，**不要**扩成全 ui —— 会假报红）
#   T-f1 0 / T-f2 1（码表里不许有后端从不发的整数码 3；整数 "2" 必须读成停用而不是"已到终点站"）
#   T-g 0（中文状态串硬映射；两个端点都不发中文码）
#   T-h1 2 / T-h2 0（Repo 车辆解析出口恰好 2：nearby 列表 + 单车详情；不许直接构造 `BusVehicle`）
#   T-i 7（`byUserDistance` 引用点恰好 7 = 声明 1 + import 3 + 调用 3；排序口径只有一个来源）
#   T-j1 1 / T-j2 0 / T-j3 1 / T-j4 ≥8 / T-j5 1（演示兜底形状：`progress` 只在 lines 分支写、
#          车辆报文**不许**出现 `routeId`（真实车辆 VO 没这个键，§69）、估算来源只发 `SCHEDULE`、
#          "没给"用 `JSONObject.NULL` 显式写出（实测 12）而不是省键，与后端报文同形）
#   T-k ≥12（`BusEtaContractTest` 生效用例数，⑬ 实测 12）· T-l 0（用户端/卡片裸拼 ETA）
#          ⚠ **⑬ 把这条判据改成了假绿，是本轮回自己身上的最重一次**：下限写的是 `≥9`，而 ⑬ 加完用例后
#          实测 11 —— 11 ≥ 9 依然 exit 0，"加了一条用例"和"删了五条用例"在这条判据下**输出完全相同**。
#          负控制当时报的是 ✘（注入破口后探针仍 0）。⇒ 规矩：**`-ge` 型计数判据必须在加用例的同一轮抬下限**，
#          抬到"当前实测值"而不是"当初的旧值"；留着旧下限就等于留一条永不响的判据（与 §六 ⑫ 那两条同类）。
#   T-m ≥4 / T-n 3（"没给"类如实文案处数 + 桌面卡片同口径 getter）
#   T-o1 ≥1 / T-o2 ≥3（提醒用 `mapNotNull` 滤 null；页面先判 null 再比较。**⑫ 起 pattern 是 `[eE]taMinutes != null`**，
#          因为寄货侧读的是 `carrierEtaMinutes`（大写 E），实测 3 = 首页 2 + 寄货页 1；小写 pattern 会数不到、
#          并且会让它的负控制 sed 变成假绿 —— 见下面"同轮复现的两条老坑"③）
#   T-p ≥3（真实键 `busId`/`plateNo` 读法，实测 3 不是 2 —— `TransportLeg` 也读 `plateNo`；
#          **写死 2 会在别人补兼容时假报红**，假报红多了判据就没人看）
#   T-q ≥1（§68"到站提醒的数据源根本没有 etaMinutes"必须还在文档里，防止缺口被顺手修掉后无人知情）
# ---- ⑫ 货运侧新增 ----
#   T-r1 send 2/2 · T-r2 leg 1/1 · T-r3 trace 1/1 · T-r4 guide 1/1 · T-r5 preview 2/2
#          （五个契约的 ETA/里程/时长**声明为可空**的处数，全部 `sed -n '/^data class X/,/^) {/p'` 限定类体 ——
#           `distanceKm` 在 `SendOrder`/`BusVehicle`/`RoutePreview` 三个类里同现，整文件数会把两个契约串成一条）
#   T-s 0（解析层不许用 `multiNum` 读到站分钟这一族；正则**刻意排除 `durationMinutes`**：
#          `TransportLeg.durationMinutes` 同病但另属"段计划时长"契约 ⇒ 它出现在**尾部打印的残留 2**，不判红）
#   T-t 0（班次 ETA 的候选键里不许再混进 `carrierEtaMinutes` —— 直线估算与班次到达时间是两件事。
#          ⚠ 这条**必须用 `grep -F`**：BRE 里 `\(`/`\)` 是分组不是字面括号，转义写法永远 0）
#   T-u 0（`ui/` + `notify/` 全域不许再拿 `etaMinutes > 0` 当"还没到时间"；T-e 只管公交链路，两条作用域不同别合并）
#   T-v 5（承运分钟带"（直线估算）"的**界面文案**行数，实测钉死 5 = 提醒 1 + 首页 1 + 寄货页 3）
#   T-v2 0（**闭包条**：同一行出现 `carrierEtaMinutes` + "分钟"就必须同行含"（直线估算）"。
#           只数 T-v 的总数拦不住"新增一处不带口径的展示"，这条才是硬规则的可执行形态）
#   T-w1 1 / T-w2 0（到站提醒的数据源必须是 `/send/page`，且不许读回没有 ETA 键的 `/send/arrangements`）
#   T-x ≥9（`SendEtaContractTest` 生效用例数）· T-y 3（`carrierLocationSource != "REAL_STALE"` 恰好 3：
#          提醒 + 首页 + 寄货页 —— **新鲜度是承运 ETA 的前置条件**，后端在位置缺失/过期时整块不填）
#   T-z ≥4（货运侧"没给"的如实文案处数，⑬ 实测 10 = ⑫ 的 7 + ⑬ 段里程/线路里程/计价里程的"未给"三支）
# ---- ⑬ 里程口径 + 精确到站 ETA 新增 ----
#   T-aa 1（`etaToNextStationMinutes: Double?` 的类体声明**恰好** 1 —— `AppBusRespVO:49-50` 那个精确 ETA
#          从 ⑪ 起一直是"DemoData 发、模型不读"，⑬ 才把它接进来；用 `sbysed` 数类体，理由同 R-g/T-c）
#   T-ab 1（`Math.max(1, ceil(...))` 的向上取整**恰好** 1 处 —— 与小程序 `pages/bus/detail.js:132-137` 同口径，
#          后端 `AppBusServiceImpl:132/:728` 也是 `Math.max(1,…)` ⇒ **0 在契约里不合法**，0.2 分钟不许显示 0）
#   T-ac 0（里程/时长读法里不许出现自造别名键 `distance` / `meters` / `minutes`（裸键）——
#          真实键只有 `distanceKm` / `durationMinutes`；这条抓的是"候选键名越加越长"那种把不存在的键当兼容）
#   T-ad1 6（`distanceKm` 可空声明处数**恰好** 6：车辆 / 线路 / 段 / 试算 / 路线预估 / 可达性 ——
#          这是"三个 distanceKm 是三种口径"的可执行形态：整文件计数会串契约，所以逐类体数）
#   T-ad2 2（`TransportLeg` 的 `distanceKm` + `durationMinutes` 可空 = 2 —— ⑫ 时它是**脚本尾部打印的残留**，
#          ⑬ 升格成判红；尾部残留从"2"变成"6 处 `eta` 自造别名"这一条，见脚本尾部）
#   T-ae 0（**闭包条**：界面读 `.distanceKm` 必须走带口径/未给分支的 getter，不许裸读后 `if (>0)`）
#   T-af ≥4（`DistanceCaliberContractTest` 生效用例数 —— 同 T-k 的规矩：加用例同轮抬下限）
#   T-ag 1（新字段在**演示形状**里也必须出现一次：`DemoData` 的 lines 分支发 `etaToNextStationMinutes`，
#          否则"模型有字段但只有真后端能触发"这条路永远测不到（与 T-j1/T-j3 同一族））
#   T-ah 0（界面不许把 `/driver/position` 那**三个键**的报文说成"在途 + 下一站"：`AppDriverPositionRespVO`
#          只有 longitude/latitude/dataSource，没有 status/nextStation ⇒ RoutesScreen 改读 `positionOnlyTag`。
#          ⚠ 这条**第一次响是响在我自己写的注释上**（注释里引用了旧文案 `"在途 ${nextStation}"` 当"改前/改后"
#          说明）。处置是按内容剥注释行（`grep -vE ':[0-9]+: *(\*|//)'`，与 T-s/T-v 同写法），**不是删注释** ——
#          删掉说明来换绿，等于把"这轮为什么改"抹掉，下一轮就没人知道这里曾经错过）
#   ⚠ 同轮复现的老坑：① 判据全绿可以同时编译不过（一次编辑吃掉行尾换行，探针毫无反应）——
#      语义判据与 `assembleDebug` 不可互相替代；② 负控制"注入破口后 exit 0"的根因全是**判据本身太弱**
#      （锚点选了不存在的函数名、正则漏了 `!!`、sed 只替到一部分）；③ **⑫ 新抓到的两类假绿**：
#      T-t 的 BRE 分组写法让它永远不会响；T-o2 的负控制 sed 用小写 `etaMinutes`，而代码已改读
#      `carrierEtaMinutes`（大写 E），破口根本没注进去。⇒ 固定口径：**新判据与负控制同轮交付，逐条看到 ✔**；
#      给负控制注入字段声明类破口时用 `eta_negative_controls.sh` 里的 `sbysed`（按类体边界替换），
#      全局 sed 会一次改掉四个类的同名字段，报红的是别条判据，等于没测到目标。
#
# ⑭ V-1 司机动作幂等探针（指令 §52；审计 §73、DECISION_LOG D-027）—— **新增两套脚本 + 一条用例级负控**
#     ⚠ F-2 第 6 步（2026-09-30 / D-038）把**用户域 `U-*` 族并入同一本脚本**：`I-*` 十条读数一字未动，
#       本本的标题与负控条数因此不再是 V-1 那一轮的值（历史上三处写"12 条 ✔"，见下方 U-* 末尾那条 ⚠）
bash tools/check_idempotency_probes.sh        # 退出码非 0 即幂等破口（末行"幂等判据通过"）
bash tools/idempotency_negative_controls.sh   # 逐条破口。**2026-09-30 F-2 ⑥ 实测：25 行控制 = 24 ✔ / 0 ✘ / 0 △ + 1 ◦**
                                        #    （11 条 I-* + 13 条 U-* + 1 条 ◦=U-j）；只在 /tmp 副本动手。
                                        #    ⚠ 2026-09-29 V-1 那轮登记的是"12 条 / 12 ✔"，V-3 沙盒改整目录后**实测一直是 11 条**
                                        #       —— 三处台账（状态板 V-1 尾巴一处 + DONE 的 V-1 段两处）都抄的是 12，而 V-3 快照与本批复跑实测都是 **11**。
                                        #       本轮**不改写历史数字**，只在此登记：凡引用"12"必须带日期与主题，
                                        #       以 `check_idempotency_probes.sh` 当下的打印为准（口径同 §八 ㉑）
bash tools/idempotency_test_negative_controls.sh   # ⚠ 这条**就地改源码再原样恢复**，一次只跑一个，
                                        #    跑完必须看到 [恢复] cmp 一致 + "[恢复后] gradle exit=0" + 判据通过
#   I-a 10（十个司机写函数的**第一步**就是 gatedAction，按"签名后两行"判，不看行号）
#   I-b 10（`gatedAction("` 出现次数 = 10：与 I-a 是两个方向 —— I-a 防"绕开"，I-b 防"凭空多设一个出口"）
#   I-c 0 （**反向**判据：`reportLocation` 体内不许出现 gatedAction。周期上报被吞就是偷点，
#          豁免不是漏做，所以必须钉住，否则下一轮"顺手把上报也套上"看起来更像加了防护）
#   I-d 1/0（`tryLock()` 一次 + `.withLock(` 零次 —— withLock 是**排队**，第一次完成后照样发给后端；
#          只数前者能被"换成 withLock 但保留一句 tryLock 注释"糊过去，所以两条一起数）
#   I-e 5   I-f 0（§54 起五个回执点都在，且这些行里不许出现"已提交后端"。I-e 数的是**生效分支**，
#          注释里那句 "`ack.notSent` 走的是独立的一支" 靠 `grep -vE ':[0-9]+: *(\*|//)'` 排除。
#          ⚠ 下限与负控沙盒必须同一轮动：这个脚本原先只 copy 三个司机页面，加第四个页面后基线直接红，
#             改成 `cp … ui/driver/*.kt` 整目录 + 基线闸门才成立 —— 部分 copy 的沙盒会把"下限"变成"沙盒的巧合")
#   I-g 0   （按钮旗标不得在 `work {}` **内部**才置位：旧形态的先后手取决于 viewModelScope 的 Main.immediate
#          同步启动，本工程无 Robolectric 证不了 ⇒ 不依赖调度器语义）
#   I-h 0   （**红线**：`clientRequestId|requestId|bizNo|idempotencyKey|X-Request-Id` 进报文 = 0。
#          ⚠ 第一次跑出来是 1，命中的是我写在 `ActionGate` 类注释里那句"`clientRequestId`/`requestId`/`bizNo` 全部不存在"
#          —— 处置与 T-ah 同：加**注释行过滤**，不删注释。离开命令的"1"会被下一轮读成"有人把假字段发出去了"）
#   I-i 1   I-j 11（`Ack.notSent` 字段声明 / 幂等用例条数，行首锚定 `^[[:space:]]*@Test`，
#          注释掉的 `//@Test` 不算 —— 负控 I-j 两条形态专门证这两点）
#   ---- 以下 U-* 六条是 F-2 第 6 步（用户域 create 幂等 / D-038）并入同一本脚本的，I-* 未动 ----
#   U-a 2   U-b 2（两个下单出口都走 `gatedUserCreate(path, …)`。U-a 按函数体判、U-b 数调用点字面，
#          与 I-a/I-b 同两个方向：防"绕开"与防"凭空多设一个出口"）
#   U-c 2 / U-c2 2 / U-c3 2（**硬规则 1 的机器形式**：`= ActionGate()` 实例数恰好 2（一域一把），
#          `userCreateGate` / `actionGate` 各自声明+使用 = 2。三条各管一个失效方向：
#          U-c 防"并成一把"，U-c2 防"用户域偷偷提交到司机那把"，U-c3 防"司机域偷偷提交到用户那把"。
#          实证：打穿 U-a 时 I-a/I-b 仍 10/10 —— 两本判据互不顶数，这就是分两本的目的）
#   U-d 3   （键的三个维度各一条 `digest.update(...)`：端点 / `Prefs.mobile` / 整份报文，
#          抄后端 `UserIdempotentKeyResolver.java:21-25`。**少一个维度就是误拦或漏拦**：
#          少"报文"＝改了数量还被拦，少"端点"＝商城单挡住寄货单，少"身份"＝换账号继承上一位的窗口）
#   U-e 2   U-f 0（用户域两个页面的 `ack.notSent` 分支都在，且这些行里不许出现"已提交后端"——
#          与 I-f 同口径：**被拦下既不是成功也不是失败**，说"已提交"就是假回执）
#   U-g 1   （键只以摘要形式落表：`return "create:\$hex"`。**明文个人信息不许进键表**，
#          用例另用 `^create:[0-9a-f]{64}$` + 姓名/手机号/地址/备注四段都不是子串钉死）
#   U-h 10  （`UserCreateIdempotencyTest` 生效 `@Test` 条数，沿用 I-j 的行首锚定口径）
#   U-i 2/2 （⚠ 判据写法本轮改过一次：原先字面 grep `Api.post(` 只命中 1（两处已走闸的 create 调的是
#          `gatedUserCreate` 里的 `Api.post(path,`，不在页面里）⇒ **是判据错不是代码错**。
#          改为数**端点声明面** `val path = "$T/[a-z-]*/create"` = 2 并与走闸数**对账**（`U-i == U-b`），
#          这是"以后有人新增下单接口没走闸"唯一兜得住的口子）
#   U-j 4   （**登记项，不判红**：用户域 create 端点总数 4 = 2 个下单（有 @Idempotent）+ 地址 + 反馈（后端**零** @Idempotent）。
#          数字涨了就回去逐条核后端注解；**给它写负控等于要求它判红，而那正是不要的行为** ⇒ 负控本打印 `◦` 而非 `✔`）
#   ⚠ 负控断言**必须锚分隔符**：`grep -q "^× U-c"` 会把 `× U-c2 …` 也算命中 ⇒ **假 ✔**（本轮实测踩到）。
#      统一改成 `"^× $1 "`；同理沙盒要 `cp ui/user/*.kt` **整目录** —— I-e 那次"部分 copy 把下限变成沙盒巧合"
#      的教训在用户域原样重演了一次。
#   ⚠ 测试侧复位**按字段类型扫**，不写死名单：`TestHost.resetActionGate()` 旧版写死字段名，本轮加新闸就漏过；
#      闸门挂在 `object Repo` 上是进程级、Gradle 全类共用一个 JVM，`SendOrderTest` 六个用例提交内容完全相同的
#      `form`，不复位会被自己的闸吞掉（这是"跨用例存活状态必须有同轮复位 + 一条负控"的第二次兑现）。
#   ⚠ 本轮新增的一条过程纪律（由我自己引入的回归换来）：**新机制必须跑全量，只跑自己的新用例不算验证**。
#      `--rerun-tasks` 首跑 1 例红（`ScalarEnvelopeContractTest.kt:191` NPE），根因是闸门挂在进程单例 `Repo` 上、
#      把 10 秒窗口带进了别人的用例（Gradle 默认所有测试类共用一个 JVM）。
#      ⇒ `TestHost.resetActionGate()` 反射清 `locks`/`heldUntil`（与 `installPrefs` 同一手法，
#        **不在生产代码上开测试专用的口子**）。凡是"跨用例存活的状态"都必须有同轮的复位与一条负控。
#
# ⑮ V-2 壳路由与分域闸探针（指令 v2.0 Phase 1 验收 / 审计 §29、§74、DECISION_LOG D-028）
#   —— **新增三个脚本 + 一个新的决策文件**。为什么这轮必须配探针：工程无 Robolectric，`NavHost` 的
#      `startDestination` 与深链跳转在用例里渲染不出来 ⇒ 行为由 `nav/ShellDecision.kt`（两个 `internal`
#      纯函数）钉，而"决策是不是真的接到了 UI 上、UI 里有没有第二份答案"**只能按结构钉**。
bash tools/check_shell_routing_probes.sh            # 退出码非 0 即分域闸破了（末行"壳路由判据通过"）
bash tools/shell_routing_negative_controls.sh       # 13 条结构破口；2026-09-29（§54 同轮）实测 13 ✔ / 0 ✘。
                                            #    只在 /tmp 副本动手（纯文本判据，不需要编译）⇒ 可与 gradle 并行
bash tools/shell_routing_test_negative_controls.sh  # ⚠ 7 条用例级破口（M-1…M-7）**就地改源码再原样恢复**，
                                            #    一次只跑一个（**这句从 2026-10-01 起有机制兜了**：`tools/negctl_lock.sh` 的 `negctl_lock` 抢 `/tmp/negctl.lock`，抢不到直接 `[STOP]` + rc=3，见 ㉔ / D-040）、不得与别的 gradle 并发；跑完必须看到
                                            #    [恢复] cmp 一致 + "[恢复后] gradle exit=0" + 判据重新全绿
#   SR-a 10   （`ShellRoutingContractTest` 的 `@Test` 条数，行首锚定 `^[[:space:]]*@Test`；
#              §54 新增"司机域路由都是无参数的字面量"一条 ⇒ 9 → 10，同一轮抬下限）
#   SR-b 1/0  （`BusPostRoot` 确实调用 `startDestinationFor(`，且旧的 `val start = when` 残留 = 0。
#              **反向那半才是重点**：新函数全绿而 UI 走旧逻辑，是最危险的一种绿）
#   SR-c 1/0  （深链确实过 `deepLinkTarget(`，且旧私有 `deepLinkAllowed` 全工程残留 = 0 —— 防"两份闸门"）
#   SR-d 4/0  （四个 `internal` 函数都在（2 条壳路由 + 2 条角色同源），且决策文件不引 `androidx.compose`：
#              一旦被依赖拖回去就重新变成测不了的东西。⚠ V-2b 把下限 2 → 4 是**新增那两个纯函数的同一轮**做的）
#   SR-d2/d3 1/1（`serverSaysDriver` 的返回类型仍是 `Boolean?`、`reconcileRole` 的签名仍是 `(Role, Boolean?)`：
#              三态退回布尔 = 把"读不到"当成"不是司机"，这条钉的是**类型**不是行为，行为在 RR/用例里）
#   SR-e 0    SR-f 0（用户侧目录不引用司机域路由 / 司机侧目录不引用用户业务页 —— 硬规则 1 的静态面）
#   SR-f2 1   （唯一合法的跨域行：司机壳的"用户版"出口，且必须与 `AppState.setRole(Role.USER)` 同一行。
#              ⚠ 这条**不是**"期望 0"而是"期望恰好 1"：写成 0 会把功能判成破口，写成 ≥0 等于没有判据）
#   SR-g 1/0  （未登录不接受深链；通知构造里不得直接投壳路由 —— 进壳只应由角色决定）
#   SR-h 1    （`Routes.DRIVER_ONLY` 仍是 `DRIVER_NAV/DRIVER_MESSAGES/HANDOVER/DRIVER_EXCEPTION` 那四条字面量：
#              漏一条 = 给用户开闸，多一条 = 把司机入口锁死。§54 加第四条时**同一轮**动了四处：
#              集合字面量、探针正则、结构负控锚点、`司机域路由集合就是那四条` 那条用例 —— 少动一处就是假绿或假红）
#   ⚠ 本轮三条判据自身的教训（都由负控制第一次跑的时候抓到，不是设计时想到的）：
#      ① `SR-e`/`SR-f` 的正则里有 `|`，**必须 `grep -E`** —— 用 BRE 时它永远不会响，
#         第一次"全绿"就是这个原因（与 ⑫ 的 T-t 同一个坑，第三次踩）；
#      ② 计数之前**先验文件存在**（脚本开头那段 `for f in …; [ -f "$f" ] || exit 2`）：
#         `grep` 找不到文件时输出为空，与"真的 0 命中"在 stdout 上不可区分；
#      ③ **负控脚本开头必须先照抄跑一遍判据、证基线全绿**，基线不绿则整份负控不采信
#         （往一份本来就红的判据里注破口，"变红"没有任何信息量）。
#
# ⑯ §54 司机异常上报探针（指令 v2.0 §54 / 审计 §75、DECISION_LOG D-029）
#   —— 后端只有自由文本 `remark`（`AppDriverLegActionReqVO` 三个键 driverId/legId/remark），
#      **没有任何异常类型枚举** ⇒ 七个分类是**客户端话术**，落进 remark 文本，绝不发自造 `type`。
bash tools/check_exception_probes.sh               # 退出码非 0 即 §54 的契约破了（末行"§54 异常上报判据通过"）
bash tools/exception_negative_controls.sh          # 21 条结构破口；2026-09-29 实测 21 ✔ / 0 ✘ / 0 △（可与 gradle 并行）
bash tools/exception_test_negative_controls.sh     # ⚠ 8 条用例级破口（X-1…X-8）**就地改源码再原样恢复**，
                                            #    一次只跑一个（**这句从 2026-10-01 起有机制兜了**：`tools/negctl_lock.sh` 的 `negctl_lock` 抢 `/tmp/negctl.lock`，抢不到直接 `[STOP]` + rc=3，见 ㉔ / D-040）、不得与别的 gradle 并发；两份 .bak 都要 cmp 一致
#   EX-a 7/6  （纯函数用例 / 契约用例条数，行首锚定；删用例必须让这里红）
#   EX-b 1/0  （页面只经 `Repo.legAction(` 单一出口；司机页自发热 `Api.post` = 0 —— 绕过幂等闸与 401 收口）
#   EX-c 0/1  （报文里自造 `type` 类键 = 0，契约测试钉住"后端认识的三个键" = 1）
#   EX-d 7/0  （七个分类条目齐、标签逐字未被改写）
#   EX-e 1/1  （上限常量恰好 200（**行尾锚定**）+ 截断路径在）
#   EX-f 1/1/1/1（路由常量 / 导航图注册 / 线路台入口 / 段号进入意图）
#   EX-g 5/1/0 （"未发给后端"三支 + 回读不一致一支都在；页面不许出现"上报成功"）
#   EX-h 0    （页面不得 `copy(status = …)` 本机自封异常状态）
#   EX-i 1/1/1/1（四道终态守卫的**确切字面量**各一处：出网前 / 按钮 / 入口 / 选段）
#   ⚠ 本轮两条判据自身出问题（负控第一次跑抓到，都是"永不报红的判据比没有判据更危险"）：
#      ① EX-e 写 `grep -c '… = 200'` —— 子串匹配，把上限改成 **2000 照样命中**，负控打出 ✔ 假绿。
#         改法：`grep -cE '… = 200$'` 行尾锚定。凡"某个数值必须恰好是这个数"的判据都要钉到行尾。
#      ② EX-i 数 `canReportException` 出现次数（实测 10）—— 摘掉两处守卫仍 ≥2，永远不红。
#         改法：钉**生效代码的四条确切字面量**各 =1，并给每一条各配一个破口（负控从 19 条变 21 条）。
#         同轮 `check_status_probes.sh` 的 S-le 也按这个改法：`= 2` → `= 3` 且新增 S-le2 逐文件验"写完必回读"，
#         `check_idempotency_probes.sh` 的 I-e 下限 4 → 5 —— **下限跟着生效代码走，不是随手放宽**。
#
# ⑰ V-2b 角色与后端画像同源探针（审计 §76、DECISION_LOG D-030 / 队列 V-2b）
#   —— 后端的**否定形状**是 `{"code":0,"data":null}`，而 `Api.kt:314-315` 把 null data 读成**空 JSONObject**
#      ⇒ 旧判据里的 `o.length() > 0` 让"撤档"成为不可达代码。行为由两份用例钉（`RoleReconcileTest` 5 条纯函数
#      + `WorkbenchRoleReconcileContractTest` 4 条 MockWebServer 计数），这里钉**结构**：早退是否在四条请求之前、
#      出口是否"改角色+出司机壳+停位置上报"三件同块 —— 没有 Robolectric，这些只能按**行号顺序与整行字面量**复核。
bash tools/check_role_reconcile_probes.sh            # 退出码非 0 即同源判据破了（末行"角色同源判据通过"）
bash tools/role_reconcile_negative_controls.sh       # 13 条结构破口；2026-09-30 实测 13 ✔ / 0 ✘（纯文本判据，可与 gradle 并行）
bash tools/role_reconcile_test_negative_controls.sh  # ⚠ 6 条用例级破口（RRt-1…RRt-6）**就地改源码再原样恢复**，
                                            #    一次只跑一个（**这句从 2026-10-01 起有机制兜了**：`tools/negctl_lock.sh` 的 `negctl_lock` 抢 `/tmp/negctl.lock`，抢不到直接 `[STOP]` + rc=3，见 ㉔ / D-040）、不得与别的 gradle 并发；两份 .bak 都要 cmp 一致 +
                                            #    "[恢复后] gradle exit=0" + 判据重新全绿
#   RR-a 5/4  （纯函数用例 / 行为契约用例条数，行首锚定 `^[[:space:]]*@Test`；删用例必须让这里红）
#   RR-b 0/0  RR-b3 1（`driverProfile()`/`bindDriver()` 两个函数体里旧 `o.length() > 0` 残留 = 0；
#              "只有 `!p.demo` 才清绑定"的整行字面量 = 1 —— 演示回落不是后端否定）
#   RR-c 180/196、1/1、193（`if (server == false)` 的**行号必须小于** `Repo.driverShifts()` 的行号，
#              否则守卫形同不存在；两个纯函数各恰好 1 个调用点；早退分支的 `return` 落在 DEN+1..DEN+22 之内）
#   RR-d 1/1/2、1/1（降级块里 `DriverLocationService.stop(` / `nav.navigate(Routes.USER_SHELL)` /
#              `demoted` 声明与观测 / 两处出司机壳的导航各钉整行字面量 —— 半条链路（只改角色不停上报）最危险）
#   RR-e 3/2/3（`AppState.setRole(` 调用点、写角色的字面量点、三处白名单 —— **页面不得自己改 `Prefs.role`**）
#   ⚠ 本轮四条判据自身的问题（前三条由负控第一次跑抓到，第四条是自己复算抓到的）：
#      ① RR-b **必须先取函数体再计数**：`grep -c 'length() > 0'` 全文件数会把别的正当判据算成残留，
#         改成 awk 只截 `driverProfile()`/`bindDriver()` 两个体 —— **范围判据要跟着"这句判据在防什么"走**。
#      ② RR-c 用**相对行号**（DEN < SFT、RET ∈ DEN+1..DEN+22），不写绝对行号：绝对行号加 14 行 KDoc 就漂
#         （与 ⑥ 的"行号白名单会漂，内容白名单不会"同一族，这次是**顺序**判据，漂了不报错、只是永远绿）。
#      ③ RR-d 一律**整行字面量**而非子串：负控抓到过"把整行注释掉，`grep -c` 照样数到 1"。
#      ④ RR-e 必须匹配 `Prefs\.role = [a-z]` 而不是前缀 `Prefs\.role =` —— 后者撞上 `AlertEngine` 里两处
#         `Prefs.role == Role.DRIVER` 的**读**，实测数出 4 而不是 2；把读成写，判据自己先失真。
#      ⑤ 又一次踩管道退出码：`bash xxx.sh 2>&1 | tail -6; echo $?` 取到的是 `tail` 的 0。
#         ⇒ 与 §六 V-2 ② 同族（那次是 `gradlew | tail` 把 BUILD FAILED 读成成功），本轮升为永久规矩：
#         **取退出码一律 `out=$(bash "$s" 2>&1); rc=$?`，"每项都要留输出"里的输出包括退出码。**

# ⑱ 第 8 步导航引擎探针（审计 §77、DECISION_LOG D-031 / 任务 #49、#84–#87）
#   —— 与 ⑰ 相反的方向：这里钉的不是"后端给了什么"，而是"**SDK 这一帧到底给了没给**"。
#      没有 Robolectric、没有真机 ⇒ 判据只能钉结构（谁调 SDK、谁造数、谁有资格宣布"在导航"），
#      行为由 `NavigationContractTest` 19 条用例钉。
bash tools/check_navigation_probes.sh             # 退出码非 0 即导航引擎判据破了（末行"第 8 步导航引擎判据通过"）
bash tools/navigation_negative_controls.sh        # 18 条结构破口（/tmp 沙箱，纯文本判据，可与 gradle 并行）；2026-09-30 实测 18 ✔ / 0 ✘ / 0 △
bash tools/navigation_test_negative_controls.sh   # ⚠ 8 条用例级破口 M1…M8：**就地改源码再原样恢复**，
                                         #    一次只跑一个（**这句从 2026-10-01 起有机制兜了**：`tools/negctl_lock.sh` 的 `negctl_lock` 抢 `/tmp/negctl.lock`，抢不到直接 `[STOP]` + rc=3，见 ㉔ / D-040）、不得与别的 gradle 并发；结束必须看到
                                         #    "[基线] ✔" + 八行 ✔ + "cmp 逐字节相同" + "[恢复后复跑] exit=0"
#   N-a 19（判据层用例条数，行首锚定 `^[[:space:]]*@Test`；删用例必须让这里红）
#   N-b 0（引擎里不许有定时器 / `stepIndex + 1` / `delay(` —— 推进只能来自 `onNaviInfoUpdate`）
#   N-c 0（不许出现第二次坐标换算：`toGcj02` / `CoordinateTransform` 在 `core/navigation/` 内 0 命中）
#   N-d 0（不许有假来源：`straightLine` / `Haversine` / `Euclidean` / 造 polyline 的写法 0 命中）
#   N-e 1/0/1（`RealRoadRouteValidator.validate` 全工程 1 处、SDK 层自判 0 处、唯一出口 1 处 —— §26 的"一个答案"）
#   N-f 1/1/2（工厂两种返回、`NavigationEngine` 实现恰好 2 个 —— **没有第三种**）
#   N-g 1/1/1/1/1/≥1（`NavigationEngines.create` / 算路 / 开始导航 / release / 目的地 / **生产调用点**各一处：
#             引擎只被 new 出来没人调，等于第 8 步没落地；调用点就是 `DriverSdkNavCard`）
#   N-h ≥2/≥2/0（`readiness`+`guidance` 是 `StateFlow`、页面 `collectAsStateWithLifecycle` 订阅、**轮询 0**）
#   N-i 1/0/0（`navi-3dmap` 声明 1、旧 `3dmap` 并存 0、`com.amap.api:location` 并存 0 —— duplicate class 那颗雷）
#   N-j 9/0/0（高德 import 总数 / 白名单外 / 出现在引擎层之外 —— SDK 类型只许住在 `AmapNavigationEngine.kt`）
#   N-k ≥5/0（"高德没给"这类如实文案的条数 / **本机自己宣布导航成功**的措辞 0）
#   N-l 0（过时陈述残留："无需地图 SDK" —— 接了 SDK 之后这句变成假话，改代码必须同轮改声明）
#   ⚠ 本轮八条判据/工具自身的坑（**五条升为永久规矩：②③⑥⑦⑧**）：
#      ① `Class.forName(X) != null` 在类缺失时是**抛异常**不是回 false；`AMapNavi::class.java` 会在编译期固化引用，
#         R8 裁掉类时"没有"被读成"有" ⇒ 全工程只留一个 `naviClassPresent()`，且**类名写字符串常量**。
#      ② **永久规矩：`local code; code=$?` 读到的是 `local` 的退出码（0）**。本轮第一版用例级负控因此把
#         七次真红报成"用例仍然全绿（exit=0 failures=1/19）"—— 负控工具说谎比没有负控更危险。
#         取码必须**紧跟**命令、不跨语句。
#      ③ **永久规矩：文件级 `grep -v` 是无界豁免**。为放过 AMap 折点搬运那一行而剔掉整个文件，
#         负控实测"豁免文件里再多一处手写坐标"仍 rc=0 ⇒ 豁免一律**计数**（L-b2 钉恰好 1 处，四个方向都验红）。
#      ④ 破了编译的变异**不算证词**：M5 第一版在函数头上插代码留下悬空实参 → 没有测试产物（NOXML）。
#         判决必须四态分开：✔ / ✘ 全绿 / △ exit 与产物不一致 / ✘ NOXML。
#      ⑤ 从 python 里 spawn `python -` 会继承父进程 stdin **永远挂住**（本轮吊住两个进程）。
#      ⑥ **永久规矩：近似切脚本做自检会静默骗人**（`sed -n "/# M1 /,/case_run M1 /p"`）八条全报"命中"、实际**一条都没注入**
#         ⇒ 自检脚本必须有"锚点没命中就 exit 2"的硬失败；复刻 shell 引号规则就要真复刻（扫未转义的闭合引号）。
#      ⑦ **永久规矩：改一个数之前先找它的出处**。本轮怀疑 "+68.6 MB" 无基线就改成 `+68.1 MB / 58,410,069 B`，
#         而 58,410,069 是**更早一轮**（APK mtime 14:33:58）的在册值；换依赖前**紧邻**的基线是状态板
#         `BUILD_STATUS` 的 **57,941,374 B** ⇒ 真实差额 **+68,575,313 B ≈ +68.6 MB**，四处台账同轮改回。
#         "我觉得这句没依据"不等于"这个数错了"；**出处不成立才改**。
#      ⑧ **永久规矩：一台机器一次只跑一个 gradle 调用**。后台全量取证未完时前台起 `--tests`，
#         实测 `failures=19/19`（构建锁互踩）；`./gradlew --stop` 后单跑 `exit=0 failures=0/19` ——
#         并行跑出来的红**不是语义的红**，把它写进台账就是假证词。
#
# ⑲ #51 旧屏 DEBUG ONLY + §24 假步进归零探针（审计 §78、DECISION_LOG D-032 / 任务 #51、#88–#91）
#   —— 与 ⑱ 的分工：⑱ 钉的是"引擎那一层不许作假"，⑲ 钉的是"正式包还剩一条能走的路，且旧屏那条路只在调试包通"。
#      这一族全是**结构判据**（无 Robolectric、无设备），行为仍由 ⑱ 那 19 条用例钉。
#   判据簿 12 → **16** 条（N-m…N-p 为本轮新增），结构负控 18 → **27** 条破口。
#   N-m 司机 UI 目录假步进形态 = **0**（目标 0）—— 口径从"只看旧屏一个文件"抬成**整个 `ui/driver` 目录**，
#       含注释行裸计数；否则"把 4 秒定时器挪到隔壁文件"就等于归零。**九条白名单一字未改**。
#   N-n 未加闸的 DRIVER_NAV 使用 / 注册带闸 / 文件自守 = **0/1/1**（目标 0/1/1）
#       —— 闸与用**同一物理行**（跨行的 `if (BuildConfig.DEBUG) { … }` 单行 grep 判不出来）。
#   N-o 「非真实导航」标注 / 车道箭头猜测说明 = **2/1**（目标 ≥2/1）—— D-003 那句"显著标注"落在界面上。
#   N-p 旧屏引擎挂载 / 高德 / 百度 / 旧屏外部地图函数 = **0/1/1/0**（目标 0/1/1/0）
#       —— **能力是搬走的不是复制的**：旧屏里 `DriverSdkNavCard(` 与 `openAmap/openBaidu` 归零，正式面上高德+百度交接各 1。
#   N-g 第六位**重锚**：从"全工程 `DriverSdkNavCard(` ≥1"改成"`WorkbenchScreen.kt` 里**恰好 1**"，
#       两个方向都红：0 = 正式包没有导航面（本轮差点造出的事故），≥2 = 两处各 new 一个引擎（进程级单回调被抢，§77 三那颗雷）。
#       ⚠ **收紧判据的正当性必须来自"旧结构当场是红的"这个实测**，不是来自"看起来更严"。
#   bash tools/check_navigation_probes.sh             # 2026-09-30 实测十六条全在目标、末行"第 8 步导航引擎判据通过"
#   bash tools/navigation_negative_controls.sh        # 27 条结构破口（/tmp 沙箱，可与 gradle 并行）
#                                       #    2026-09-30 实测：`✔ 基线：副本原样 exit 0` + 27 × ✔ / 0 ✘ / 0 △
#   bash tools/navigation_test_negative_controls.sh   # 用例级 M1…M8 本轮**照跑**（见下面那条永久规矩）
#                                       #    实测：基线 exit=0 failures=0/19 → 八次全红且红在 NavigationContractTest
#                                       #    （M5 红 4 例）→ 恢复逐字节相同 → 复跑 exit=0 failures=0/19，RC=0
#   ⚠ 本轮三条新教训（③ 升为永久规矩）：
#      ① **"删假"之前必须先验"真"还在不在**：本轮 `DriverSdkNavCard(` 全工程唯一的调用点就在要被关掉的旧屏里 ——
#         直接上闸会让 release 失去导航能力，而且**失去得很安静**（编译过、探针绿、测试全绿，少的是一个功能）。
#         ⇒ 收法：判据里给"生产调用点"编一个数并锚到具体文件（N-g 第六位），让"少掉正式能力"变成一件会红的事。
#      ② **自己写的注释会被自己的判据抓住**：第一版复测 `假步进 = 2`，命中的是本轮新写 KDoc 里的字面
#         `stepping` 与 `delay(4_000` ⇒ 改**措辞**（"4 秒定时器逐条翻页"），**不改口径**。
#         与"0 命中有三种失效"同族而方向相反：**判据抓到写反的注释，说明它在起作用**，别把它当判据太脆。
#      ③ **永久规矩：不得用"改动范围与判据范围不相交"当跳过负控的理由 —— 除非那个不相交本身是实测出来的**。
#         本轮本可以主张"用例级负控只锚 `NavigationEngine.kt`，我没改它"，但该文件 mtime（01:34）晚于上一次实跑，
#         **mtime 证明不了"上次跑时的树就是现在这样"** ⇒ 跳过就退化成自述。照跑，且跑完复测两套探针 rc=0。
#
# ⑳ E-1 解析层"自造别名键"归零探针（审计 §79、DECISION_LOG D-033 / 任务 #92–#97）
#   —— 与 ETA 簿其余判据的分工：T-a…T-z 钉的是"没给不许塌成 0"，⑳ 钉的是**另一条失效路径**：
#      候选键名列表里那个**三方都没有的键**。它不会塌成 0，它会读成一个**来路不明的真值**，
#      所以 §38 那条红线拦不住它（读的确实是一个存在的数）。
#   三方实测（2026-09-30 当轮重跑，不是沿用）：后端 `yudao-module-transport` 里 `"eta"` 字面量 **0 命中**
#     （同口径 `etaMinutes` 在 java 里 **13 处** ⇒ grep 没找错地方）；`cargo-post-miniprogram` 与 `miniprogram`
#     两棵树裸 `eta` 用法各 **0**；`DemoData.kt` `"eta"` **0 处**。⇒ **该键从来没有生产者**，删读法不破坏任何真兼容。
#     反证用的是**改动前那棵树的整份副本**（`/tmp/ledger_replay`，mtime 09-29 21:18）：同一组 grep 打在那棵树上
#     **T-ai=6 / T-ai2=6 / T-ai3=0**，打在当前树上 **0 / 0 / 0** ⇒ "0"不是"判据判不到"，
#     而 T-ai3 那个"改动前后都是 0"正是"演示面从来没有这个键"的直接证据。
#   删掉的 6 处落点：`Models.kt` BusVehicle:721（还多一个裸 `"minutes"`）/ SendOrder:937 / TransportLeg:1056 /
#     Arrangement:1623，`Repo.kt` traceOf:592 / routeGuideOf:962 —— 全部退回只认 `etaMinutes` 一个键。
#   新判据三条（全在 tools/check_eta_probes.sh，实测 0/0/0）：
#     T-ai  `multiNum(OrNull)?("etaMinutes", "` 第二候选名处数 = **0**
#     T-ai2 main 源码里裸 `"eta"` 字面量 = **0**（闭包判据，注释行按 `:[0-9]+: *(\*|//)` 排除）
#     T-ai3 演示面 `"eta"` 键 = **0**（反诱惑：不许给 demo 补一个键来给别名"续命"，那样 T-ai 的三方论据当场作废）
#   用例同轮 12 → **13**（BusEtaContractTest）、9 → **12**（SendEtaContractTest），**下限在同一轮抬**：T-k ≥13、T-x ≥12。
#     两条"只带别名"的用例形状：显式 `"eta" to 15` 而 `etaMinutes` 缺键/null ⇒ 必须读成 null；
#     再各配一条"真实键照常工作"（7 / 6 / RouteGuide 走道路耗时），否则"整条读法删掉"也能骗绿。
#   ⚠ 本轮判据与负控**自己**被抓到五条缺陷（都是真缺陷，不是判据太脆）：
#     ① 新 pattern 手滑写成双引号 `*""` ⇒ 永不命中 = **静默假绿**；抓法不是回去再审一遍，是**把同一条 grep 打在改动前的整树副本**
#        （`/tmp/ledger_replay`）上：写对时旧树 **6** / 当前树 **0**，写错时**两边都是 0** ⇒ 这个"6"就是这条判据的出生证明。
#     ② 闭包判据少了 `grep -n` ⇒ 排除注释的那条过滤器形同虚设（T-ai2 被自己的说明注释算成 2，探针 rc=1）。
#        **修判据不删注释**：注释是真话，缺的是输出形状。
#     ③ **T-t 负控被本轮改动作废**：E-1 删掉的那行正是 `T-t` 的 sed 锚点 ⇒ 锚点失配、破口没注进去、用例照跑照绿。
#        实测从 ✔=53 ✘=1 起，修锚点后 **✔=54 ✘=0**。
#     ④ **SR-f 负控被 #51 作废**（跨簿！）：#51 把 `onNav = { nav.navigate(…) }` 改成带闸那一行，
#        而 `shell_routing` 簿的 SR-f 锚的就是旧文本 —— 上一轮只复跑了"自己那本簿"，没复跑它，于是**静默失效一整轮**。
#        修锚点后 ✔=13 ✘=0。
#     ⑤ **台账里"负控成本"那两个数是推算的**：草稿按脚本结构写"四套 32 / 连角色簿 40 次、约 7 分钟、04:14 起跑"，
#        收口时按 `/tmp/*_neg_*.log` 的**留档体数与 mtime**清点 ⇒ **39 次 / 约 9 分钟**（幂等簿按结构该有三份日志、实跑只落两份）。
#        ⇒ 永久口径：**台账里的次数与时长只准抄留档产物，不准抄"每套 N 条 × 每条几秒"的心算**；四处已写错的副本同轮改成实测值。
#   ⇒ **⑲ 那条永久规矩的适用范围本轮被实测写窄了，正式升级**：改源码文本 ⇒ **同一轮要核所有簿的注入锚点**
#     （或直接复跑所有簿），不是只核"本轮新加判据那本"。**锚点是源码文本不是符号名**，
#     任何一次改写文案/换写法都会作废它，包括"上一轮改的、在另一个文件里的"。
#     代价实测：五套就地簿本轮共 **39** 次单类 gradle 调用（异常 10 / 幂等 2 / 壳路由 9 / 导航 10 / 角色 8，
#     按 `/tmp/*_neg_*.log` 留档体数清点；按脚本结构推算是 40 —— **台账记实测体数不记推算值**）。
#     分段墙钟 04:04:13–04:06:32 / 04:13:36–04:15:53 / 04:16 / 04:16:47–04:18:35 / 04:18:44–04:20:51，
#     **合计约 9 分钟**（daemon 已热、每次只跑一个测试类）。这是每轮的固定成本，不是本轮意外。
#   bash tools/check_eta_probes.sh                    # 2026-09-30 实测 rc=0，末行"ETA 判据通过（公交侧 + 货运侧 + ⑬ 里程口径 + E-1 别名键归零）"
#   bash tools/eta_negative_controls.sh               # 2026-09-30 实测 ✔=54 ✘=0 △=0（本轮 +4 条打 T-ai 家族）
#   bash tools/shell_routing_negative_controls.sh     # 2026-09-30 实测 ✔=13 ✘=0 △=0（SR-f 锚点已修）
#   ⚠ 尾巴（**打印不判红**，在册待逐端点核）：其余 `Minutes` 家族读法仍有 **5 处**第二候选名
#     （Models.kt 479 `plannedMinutes` / 729 `waitDepartureMinutes` / 1110 `totalMinutes` / 1328 `plannedDurationMinutes` /
#      1802 `walkMinutes`）。本轮**没有**顺手删 —— 它们的键可能是小程序真在用的（手里没这份实测就是猜），
#     而"猜出来的兼容"正是这一族缺陷本身。已登记为下一轮队首。
# ㉑ F-1 异常可信化探针（指令 v2.1 §六 / §七 / §三十一；审计 §80、DECISION_LOG D-034 / 任务 #98–#102）
#   —— 与 ⑳ 的分工：⑳ 钉"键不许自造"（成功那一侧的第四个真相源），㉑ 钉**失败那一侧**：
#     "程序缺陷"与"真失败"分成两类之后，两类都不许被写成对方。
#     §六：演示构建的回落面只准是「传输失败 / 业务失败」两类；IllegalState / NullPointer / ClassCast / LinkageError /
#           OutOfMemory / Assertion 一律原样抛出 —— 回落一次就是把 bug 说成"后端给了演示数据"，堆栈同时丢掉，缺陷再也复现不了。
#     §七：协程取消不是失败。`catch (e: Exception)` **会**接住 CancellationException（它继承 Exception），
#           `runCatching` 接住 Throwable ⇒ "页面已经离开"被写成一次"加载失败"，还继续往已离场的 StateFlow 上写。
#   实测读数（2026-09-30 收工 `bash tools/check_throwable_probes.sh` → rc=0，12 行读数 / 27 道闸门，
#     闸门行数由 `grep -nE '^\[ "\$T' tools/check_throwable_probes.sh | wc -l` 实数得，不是按 TH 条数乘出来的）：
#     TH-a 7/5 · TH-b 4/4 · TH-c 2/0/2 · TH-d 38/38 · TH-e 0 ‖ 挂起形状 48 / 免检 1 · TH-f 12/1 · TH-g 0
#     TH-h 6/1/2 · TH-i 1 · TH-j 1/1/0 · TH-k 1/1 · TH-l 2/2/0
#   ⚠ 四条本轮新立的方法（都是复测或负控抓出来的，不是先想清楚的）：
#     ① **"配对"既不数关键字出现次数，也不靠手写文件清单**：TH-e 第一版扫一份 14 个文件的清单，结果
#        `DriverSdkNavCard.kt:291`（唤起外部地图，try 里**一个挂起点都没有**）被判成违例，而清单外的文件永远是盲区。
#        改成**扫描面由内容决定**：每个 `catch (e: Exception)` 先看它 try 的**首段**有没有挂起点
#        （`Repo.` / `Api.` / `delay(` / `.await(` / `.first(` / `fetch(` / `block(` / `animateScrollToPage` …），
#        有才要求同链先有取消出口；并且**"扫到多少挂起形状""免检几处"两个数一起打出来** ——
#        只打违例数的判据，在"规则被写宽"与"破口被删干净"两种情况下输出一模一样。
#     ② **三个"目标 0"各有出生证明**（18 条负控逐条打）：TH-c 的 Api.kt=0 由"把 IOException 出口改回 Throwable"打出 exit 1；
#        TH-e 的违例 0 由"摘掉一处 `throw e`、保留子句字面量"打出 —— 那一次 **TH-d 照样 38/38 绿**，
#        正好证"数子句"与"逐点配对"不是同一条判据（前者对写法敏感、后者对语义敏感）；
#        TH-l 的兜底 0 由"NetworkException 出口换成 RuntimeException"打出。
#     ③ **注释不许顶替生效代码，负控脚本自己也要防**：`swap` 现在强制 `assert old not in new` ——
#        把 `if (!demoAllowed) throw e` 换成"同样字符串的注释"，TH-b 会数到注释里那一句，破口看着还在 ⇒ 假绿。
#        同一条坑 V-3 / ⑳ 都犯过，本轮把它写成了脚本里的机器闸，而不是提示。
#     ④ **"改了哪些文件"这件事，glob 清单会漏掉删除型改动**：本轮收法有三种，第三种（`WorkbenchScreen.kt:434-440`
#        整圈 `runCatching` 仪式摘掉）在 TH-d 的"数子句"、TH-e 的"逐点配对"、以及任何 `attempt {` / CE 子句 /
#        `demoAllowed` 出口 glob 里**全都隐形**；它的 mtime（16:55）又是就地簿 `cp` 恢复留下的，连 mtime 窗口也抓不到。
#        本轮是靠**作差**捞出它的：把"注释里引用 §三十一 的文件"与（三条 glob 并集 ∪ 编辑窗口 mtime）相比 ⇒
#        既有生产文件 **16 → 17**（明细清单见 `THIRD_PARTY_NOTICES.md` §6 F-1 段、审计 §80 七）。
#        同一条坑在判据侧是"归零判据抓不到'整块删掉'的改法"（§八 ⑳ 第②条的延伸），在台账侧就是"自述不实"。
#   bash tools/throwable_negative_controls.sh   # 2026-09-30 全批复跑实测：rc=0，**✔ 行 19 / ✘=0 / △=0**
#     ⚠ 这 19 = **18 条破口**（`grep -cE '^ctl '` 得 16，另有两条写在 `kill_test` 同一行上 ⇒ 16+2=18）
#       **+ 1 条末尾自检行**（`✔ 恢复后基线仍为绿`）。台账原来抄的"✔=18"是**按脚本结构数的**，
#       复跑批处理按留档行数出来是 **19** ⇒ 与 ⑳ 第⑤条同一条规矩：**次数只准抄实测输出，不准抄推算**。
#     ⚠ 同一批里 `idempotency_test_negative_controls.sh` 打出 **✔=0**：那个簿**根本不打印 ✔ 标记**
#       （它的三行标记是 `[破口后] gradle exit=1` / `[恢复] cmp 一致` / `[恢复后] gradle exit=0`），
#       而它 rc=0 是簿内闸门给的 ⇒ **"✔=0" 不是"这个簿没有负控"**。凡是按标记 grep 出来的计数，
#       都要先证那本簿**用这套标记**，否则一个 0 会被读成"什么都没测"（也可以被读成"测过了"）。
#   ⚠ 尾巴（在册，不当已通过）：
#     · §七 点名要建的 unified helper 这个形状**工程里已经有了**：`apiCall`（`Api.kt:60`，`suspend inline`、先穿透取消、
#       五类 typed 分支、`ApiTest.kt:165` 有"只做分类不吞异常"一条用例）—— 但**生产调用点实测 0**（判据里 Z1=1 就是它自己的定义）。
#       本轮**没有**把它接进任何路径：接它要同时改 38 处读接口的返回形状，那是另一轮的范围。只登记，不谎称已用。
#     · `runCatching` 在 main 里仍剩 **34** 处（Z2 只打数不判红），本轮只收了"里面有挂起点"的那一批（TH-g 七个点名形状 → 0）；
#       `catch (e: Throwable)` 含注释共 **4** 行（Z3），生效 **2** 处且都在白名单（Attempt 收成 Result / ActionGate 清理后 rethrow）。
#     · §二十 的 Worker 重试语义（真失败该回 `Result.retry` 而不是 `success`）**本轮没做**，只收了"取消不许谎报这一轮跑过了"。
#     · 设备侧一律 `NOT VERIFIED ON DEVICE`：真实的取消来自"手指划走页面 / Worker 被系统停止 / 进程被杀"，
#       JVM 用例只能造 `cancelAndJoin` 与 `withTimeoutOrNull` 这两种。
#
# ㉒ F-2 ①–⑤ "缺失字段被默认值伪装"三本判据簿（指令 v2.1 §三/§四/§五/§二十四；D-035 / D-036）
#   bash tools/check_contract_defaults_probes.sh   # §三 QuoteResult（Q-*）+ §四 NearbyResult（B-*）同一本，实测 rc=0
#     Q-a 5（五个数值字段可空声明）· Q-b/Q-b2/Q-c1/Q-c2/Q-c3/Q-d/Q-d2 0 · Q-e1 3 · Q-e2 4 · Q-f 7（用例）
#     B-a 2（inService/located 可空）· B-b/B-d/B-e3/B-f 0 · B-e1 1 · B-e2 2 · B-g 6 · B-c 1（**只登记不判红**：`SendScreen.kt:209 optBoolean("fresh")` 缺键当 false）
#     GUARD pipeline=1 / 别名在测试源码 5 / GUARD-B 29
#   bash tools/contract_defaults_negative_controls.sh   # 实测 rc=0，**22 ✔ / 0 ✘ / 0 △ + 1 ◦**，末行 `[口径控制] 反向控制失效条数 = 0（预期 0）`（非 0 → exit 3）
#   bash tools/check_default_value_probes.sh       # §五 数字默认值（DV-*），实测 rc=0
#     DV-a/b/c 0 · DV-d 12（真实键单键读法**下限**）· DV-e 9 · DV-f 2 · DV-g 8 · DV-g2 2 · DV-h/i 0 · DV-j 1 · DV-k 132 · DV-l 8
#     GUARD 1/29/34/7。⚠ **DV-k 是全项目基线，只许往下走**（152→132 是本轮收口量，新增一处非可空数值读法就响）
#   bash tools/default_value_negative_controls.sh  # 实测 rc=0，**16 ✔ / 0 ✘ / 0 △**
#   T-aj 家族在 `tools/check_eta_probes.sh` 里（不另起一本）：T-aj/aj2/aj3 0 · T-aj4 7 · T-aj5 16 · T-aj6 6，GUARD 10，rc=0
#   普查两件套：`python tools/default_value_census.py --write`（472 站点 / B 398 / C 74 / **A 0**）、`bash tools/v21_requirement_census.sh`（113 行）
#   ⚠ **本簿的四条新永久规矩**（都是本轮实测抓出来的，不是先想清楚的）：
#     ① **判据的扫描面按"契约"收，不按"成员名"收**：`val amountText: String get() = amount?.let` 在 `QuoteResult` 与 `EarningItem`
#        两个类里**逐字相同** ⇒ 整文件计数把两条契约并成一条；`multiBoolOrNull` 同理（§四 落码后 R-f 由 2 涨到 4，涨的两处归 B-*）。
#        修法是**把扫描面收进类体/函数体**（`sed -n '/^data class QuoteResult/,/^}/p'`），⚠ **不是把期望数字改松**；
#        并且**同轮留一行不判红的残留打印**（全文件 getter 4 / 全 ui 使用 5 / 全项目 multiBoolOrNull 4），被摘出去的点必须继续可见。
#        先例是 ⑪ 的 R-g（"收紧而不是放宽"），本轮把这条推广到 helper 名与 getter 名两类；
#     ② **同名成员会让"负控"变成假的**：原有那条 Q-e1 控制用整文件 `replace(old,'',1)` 删那一行，
#        删到的是 `EarningItem` 的（在文件更前面）⇒ **破口从未打进被判定量、簿照样绿**。⇒ 新增类体内删行的 `delbody()`，
#        并补**反向（口径）控制 `keep`**：删**另一条契约**的同名 getter 时本条**必须不响**，响了说明它统计的还是别处。
#        标记因此扩成 `✔ 破口 / ✘ 未响 / △ 存疑 / ◦ 反向控制`，**按标记 grep 出来的计数要先证那本簿用这套标记**（㉑ 第②条的延伸）；
#     ③ **hits 会把修好的事读成没修**：`optBoolean("…", true)` 普查 `hits=2 / code=0`，那 2 处是 `Models.kt:831/:849` KDoc 里
#        "旧读法长什么样"的反面说明 ⇒ 普查每行补 `code=`（滤注释后的生效行数）。⚠ **不得为了消掉 2 去删注释**；
#        同一条坑在 `runCatching` 上是 **44 出现次数 / 43 行 / 34 生效行 / 22 文件** ⇒ 抄这种数**必须连口径一起抄**；
#     ④ **产物新鲜度只认包内内容**：报了"12 executed"的构建仍留着 16:45 的旧包（查不到本轮字样），
#        `--rerun-tasks` 才得到 19:33:42 / 113,863,001 B / sha1 `7fd54a4a…`，`unzip` 15 个 dex 才 grep 到"金额未给"×2、"后端无此口径"×1。
#        ⚠ 顺带纠一条本轮台账里的假话：状态板初稿写"UI 显示『暂无报价』（APK 内 2 处）"，实测该串 **0** 处（真实文案是"金额未给"/"未给"），
#        **改的是文案描述而不是文案**（没有为了让台账好看去改代码）。
#     ④-加强版（**F-2 ⑥ / D-038，2026-09-30**）：**`UP-TO-DATE` / `executed` / `BUILD SUCCESSFUL` 三种打印都不是"包内新鲜"的证明** ——
#        本轮 21:08 那次 `:app:assembleDebug` 打印 `packageDebug` **执行过**（`11 executed, 37 up-to-date`），
#        而盘上 APK 仍是 **19:33:42 / 113,863,001 B / sha1 `7fd54a4a…`**、15 个 dex 里本轮新符号 `userCreateGate` **0 命中**，
#        **同轮** `intermediates/dex/debug/mergeProjectDexDebug` 却是 **21:08:18 且含新符号** ⇒ 陈旧在**打包环节的 up-to-date 记账**，不在编译。
#        `--rerun-tasks`（**37 executed**）后得到 **21:13:59 / 113,863,001 B / sha1 `03ab6172…`**
#        ⇒ ⚠ **与旧那份同字节而不同哈希**："体积没变"在这里恰恰掩盖了"内容变了"，**跨轮引用 APK 必须同时带 sha1 与 `N executed`**。
#        **配套的第二条：0 命中必须先证方法** —— 同一个脚本、同一批 dex 里，已知旧串 `gatedAction` 数到 **4**、
#        `本次请求未发给后端` **1**，所以"测试类 0 / `Lokhttp3/mockwebserver/` 0"是真的不在包里，而不是 grep 坏了；
#        ⚠ 反之若一批里已知串也数不到 **0**，那结论只能是"工具坏了"，**不许**把它抄成"内容不存在"。
#        ⚠ 代价也要写在册：改一行类注释（如 `ActionGate.kt:13` 那句"司机动作的本地幂等闸"已因两把实例而过时）
#        同样是**内容改动**，会让刚验过的包作废 ⇒ 要么连同重打包一起收，要么**登记**，不许"改了文档而产物已过期"。
#     ⑤ **Gradle 守护进程的 `-Dfile.encoding` 决定测试 Worker 能不能启动 —— "N executed" 与"产物存在"是两件事**：
#        本机路径带非 ASCII（`C:\Users\袁\Desktop\测试\…`），Gradle 的 java 原生 launcher 用 **ANSI 代码页 936** 解码
#        Worker 的 `@argfile`（`~/.gradle/.tmp/gradle-worker-classpath*txt`）⇒ 该文件必须是 **GBK** 字节。
#        argfile 用 GBK 还是 UTF-8 由**守护进程 JVM** 的 `file.encoding` 决定（实测：GBK 那批 **25,821 B**、`袁` = `D4 AC`，
#        Worker 正常；UTF-8 那批 **26,019 B**、`袁` = `E5 A4 89`，六个 Worker 全灭 `ClassNotFoundException:
#        …worker…GradleWorkerMain`）。⚠ **客户端 `java` 命令行加 `-Dfile.encoding=GBK` 传不到守护进程**，
#        而 `gradle.properties` 里写的是 `-Dfile.encoding=UTF-8`（mtime 09-29 02:29，本轮**没有**改它 ——
#        改项目配置来绕环境问题与"wrapper 下载失败不许改代码绕"是同一条红线）。
#        正确做法：**只在命令行**覆盖 `-Dorg.gradle.jvmargs="-Xmx3072m -XX:MaxMetaspaceSize=768m -Dfile.encoding=GBK"`。
#        ⚠ 最阴的一点：这个故障下 `--rerun-tasks` 仍然打印 **`24 actionable tasks: 24 executed` / BUILD SUCCESSFUL 的 rc 由
#        启动失败决定，但增量场景里会出现"报 executed 而 `test-results/` 里 0 份 XML"** ⇒ **"executed" 不是产物**，
#        收工读数必须**逐份读 XML 再自相加**（本轮 35 份、mtime 全为 20:30:14）；
#        ⚠ 同一条坑的第二种形状：**全量用例不许与就地负控批处理并行**（批处理会换源码、自己带 `--tests` 过滤调用 gradle，
#        并把 `test-results/` 洗成只剩一类）⇒ 一台机器同一时刻只有一个 gradle 调用，负控批跑完再跑全量（见 ⑱ 第⑧条）。
#   ⚠ 尾巴（在册，不当已通过）：§五 的 **32 处** C 类站点**未逐族修**（见本文件 §九 那行）；
#     `plannedMinutes` / `plannedDurationMinutes` 两处读法形状仍是 `Int = 0` + `multiNum(…).toInt()`（别名已删净，默认值形状未修）；
#     F-2 第 6–18 步（用户端 create 幂等 / `VerificationFailure` 四类 / `safeApiCall` 五点 / `runCatching` 审计 / Worker 重试 /
#     设备边界 / OSM 署名 / 依赖对照）**本轮全部未做**，OSM 在 App 内实测仍 **0** 命中。
#     ⚠ **上面这句是 F-2 ①–⑤ 那一遍写的，不回改**：第 6 步已于 **F-2 ⑥** 收口（㉒ 的 ④-加强版 / ⑤ / D-038）、
#       第 7 步已于 **F-2 ⑦** 收口（下面那节 ㉓ / D-039）；**8–18 仍在册**，OSM App 内署名到本轮实测仍 **0** 命中。
#       ⚠ **2026-10-01 F-2 ⑧ 再补一句（上面那句是 ⑦ 那轮的口径，不回改）**：第 8 步（`safeApiCall` 五点）已于 **⑧** 收口
#         （下面那节 **㉕** / D-042）⇒ 在册范围现在是 **9–18**；OSM App 内署名本轮复测仍 **0** 命中（那是 ⑭ 的活）。
#       同一条"普查行数"也在涨：`tools/v21_requirement_census.sh` 从 ①–⑤ 的 **113 行** → ⑥ 的 **113**（⑥ 没加行）→ ⑦ 的 **117 行**（V21-30 多一本 + V21-33 新开三行）→ ⑧ 的 **118 行**。
#         ⚠ **⑧ 这一句是 03:44 重新跑普查 + `diff` 两份产物量出来的，不是由"118−117=1"推出来的**（正是 ㉕ ① 那条规矩的当场应用）：
#         末行 `SUMMARY|rows=118` 是脚本自己的口径（**不含 SUMMARY 行**；同一份产物 `wc -l` 是 **119**，差的就是那一行 ⇒ 抄数要抄同一个来源）。
#         唯一的**新增行**是 `V21-30|SafeApiCallContractTest|present|tests=12`（名单 ⑦ **19** 名 → ⑧ **20** 名，present 5→**6**、ABSENT **14** 未变）；
#         其余全是**计数变了、行数没变**：`V21-01` 判据簿 15→**16** / 负控 16→**17**、`V21-07` `attempt {` code **12→7** · `safeApiCall` hits **2→14**/code **0→9** · 旧名 `apiCall` **1→0**
#         （该行标签 ⑧ 起写作 `legacy apiCall expect 0`，**行数没变**）、`V21-22` `ContractParseException` hits/files **1/1→2/2 而 code 仍 0**（新那处是 `Api.kt:70` 的 KDoc）、
#         `V21-33` fixture consumers **6→7**（多的那个是 `docs/android/ANDROID_V2.1_CHECKLIST.md`，mtime 00:06:02 晚于 ⑦ 那次普查）。留档 `/tmp/f2_8_ledger_census.log`。
#
# ㉓ F-2 ⑦ 构建失败四类分型（指令 v2.1 §三十三 / V21-33；D-039）
#   bash tools/check_verification_failure_probes.sh    # 实测 rc=0，输出 **18 行 = GUARD 1 + VF 读数 16 + 结论 1**
#     GUARD 计数管道 **1**（目标 1）
#     VF-a 规则表类别数 **4** · 行数 **16**（下限 14）· 字段数不对的行 **0** / 出处为空的行 **0** · 出处行号回核 **指对 9 / 指错 0 / 样例缺失 0**
#     VF-b 真实样例判对 **6/6**（判错 0）· 用例失败那份拒绝分类 **1**（分类器 rc=**4**）· 四类被样例覆盖 **4**
#     VF-c `app/src/main` 里四类符号 **0** / `VerificationFailure` **0**（反证：同一模式在 test 源集 **10**，少一个 0 就是 grep 坏了）
#     VF-d 业务三套错误通路 **1/1/1**（不许长出第四套）· 分类器内写死的类别名 **0**
#     VF-e `verify_device.sh` 引用分类器 **1** · 笼统那句"构建失败，先修编译错误" **0** / 旧 `tail -3` 管道形态 **0**
#     VF-f 四类 object / 逐名点齐 / 用例条数 **4 / 4 / 8**
#     VF-g 规则表的消费者文件数 **6**（下限 ≥3）· 分类器读的就是这张表 **1**（赋值行，注释里的提及不算）
#   bash tools/verification_failure_negative_controls.sh   # 实测 rc=0，**13 ✔ / 0 ✘ / 0 △ + 1 ◦**，收尾 `cmp 一致` × N + 真身目录没多出文件
#   ⚠ 耗时口径：**单跑 9–10 s**，与负控批（里面在跑 gradle）并行时 **12 s / 17 s** ⇒ 抄这个数连"是否与 gradle 并行"一起抄；
#     判据簿本身不碰 gradle，所以并行只影响墙上时间、不影响判定。
#   ⚠ **本簿的五条新永久规矩**（同样是本轮实测抓出来的）：
#     ① **落点：构建失败的分型不进 `app/src/main`** —— 构建失败时 App 进程根本不存在，这个类型没有生产运行期消费者；
#        而业务侧的失败已经有 `ApiException` / `NetworkException` / `Ack.error` 三套，再放一套进 main 就是**第四份真相**。
#        所以真实消费者是 **bash 分类器**（`tools/classify_verification_failure.sh`，被 `verify_device.sh` 两个分支调用），
#        Kotlin 侧只留 `sealed interface` + 四个 `object` 当**类型层证据**（test 源集），两边共读**唯一一张规则表**
#        `tools/verification_failure_rules.txt`（三列：类别 / 字面量 / 出处；`未实测:` 出处的行**只登记不判红**）。
#        ⚠ 这不是"违背指令"而是**把指令的字面落点换成可证落点**：V21-30 点名的文件本就是 `VerificationFailureTest`（test 源集），
#        核对表 `docs/android/ANDROID_V2.1_CHECKLIST.md` 的 V21-33 行已写明这处偏离；
#     ② **只数"个数"的判据对"改名"无感**：首轮负控 `✘ 9`（把 `object EnvironmentFailure` 改名，`VF-f` 仍数到 4 而没红）、
#        `△ 10`（换规则表路径，响的却是别的判据，因为 `VF-g` 的消费者下限是 `>=3`）。
#        修法是**逐名点齐**（`grep -cE 'object (SourceFailure|DependencyFailure|EnvironmentFailure|DeviceUnavailable) : VerificationFailure'` 必须 = 4）
#        和**精确钉赋值行**（`RULES="$ROOT/tools/verification_failure_rules.txt"` 在分类器里恰好 **1** 处），⚠ **不是把期望数字改松**；
#     ③ **出处那一列自己也会烂**：规则表里 `Unrecognized VM option` 写的 `:15` 实测在 **`:12`** —— "非空"判据看不见行号漂移。
#        ⇒ 新增 **VF-a 逐行回核闸**：拿表里的 `file:line` 回到样例里，用 `awk NR==n` + `index($0,lit)` 确认那一行**真有**这条字面量；
#        并补**负控制 13**（把 `:12` 改成 `:15` ⇒ VF-a 必须点名打红）。ASCII 安全口径：只在 ASCII `:` 上切、只认 `.log` 尾巴；
#     ④ **基线必须机器读取**：比对"改了哪些类"时把上一轮台账那张逐类表**手抄**进脚本，漏了 `SendOrder 8` 一行
#        ⇒ 打出"新增一类"的**假差异**。这已是同一条坑的**第三次**（F-1 手写错类名、F-2 ①–⑤ 的 `replace('Test','')`、本轮的手抄漏行）
#        ⇒ 改成从台账文本 `re.search` 解析基线，跑完必须 `ADDED {}` / `REMOVED {}` / `CHANGED {}` 三个集合都对得上才算归因。
#     ⑤ 顺带三条环境事实：**Windows 逐条字面量起一个 grep = 18 s**，改成单次 `grep -a -F -f 表` + bash 归属 = 9 s；
#        分类器**不用 BRE**（`-` 与 `[]` 会让"0 命中"变成假干净，一律 `-F` 纯文本子串，规则表因此禁止非 ASCII 与 glob 元字符 ——
#        这样 bash 的 `grep -aF` 与 Kotlin 的 `String.contains` 才是**可证等价**的两种实现，前提由用例逐行验）；
#        `verify_device.sh` 里那句 `… | tail -3 || die` **管道退出码永远是 tail 的 0** ⇒ 那条闸门**从不触发**，已修（这类"看着有守卫其实没有"要按红线登记）。
#   ⚠ 尾巴（在册）：`assembleRelease` 本轮 rc=1，分类器给 **UNCLASSIFIED** —— 这是**设计意图**（HTTPS 闸门 `app/build.gradle.kts:171`
#     属策略拒绝，不是四类构建失败），**没有**为了让它落进某一类而加规则行；四类分型与设备侧行为一律 `NOT VERIFIED ON DEVICE`。
#
# ㉔ 负控簿**自己**有两处"永远绿"：并行没有互斥、空跑注入会被读成"判据不判"（2026-10-01，F-2 ⑦ 收工终检；D-040）
#   本轮抓到的不是业务缺陷，是**证据链的缺陷**——而且这两处都属于"输出看着像守卫、其实从没执行"的那一类（同 §八 ㉓ ⑤ 的 `| tail -3 || die`）。
#   ① 事实链（照抄日志时间戳，不概括）：
#      · 16 本全批在 23:30:50 → 00:21:23 跑过一遍（pass-1），**我自己在 00:03:22 又起了第二批**（pass-2，7 本）⇒ 重叠 18 分钟；
#        重叠窗口里的 7 本（`idempotency_test` / `role_reconcile` / `role_reconcile_test` / `shell_routing` / `shell_routing_test` / `throwable` / `verification_failure`）
#        **读数一律不采信**；此前 9 本在单批窗口内跑完，读数仍可用（但那 9 本 rc 恒 0，结论只写在文本里）。
#      · 被踩出来的两种坏输出：`role_reconcile_test` rc=2「恢复后基线不绿」（其实是另一本正在改同一棵源码树）；
#        `shell_routing_test` 的 M-4/M-5 注入直接 `AssertionError`（锚点被同行还原掉了），而 `case_run` **不看注入的退出码**，
#        继续拿**未修改**的源码跑 gradle ⇒ 撞上 `FROM-CACHE` 的绿色 ⇒ 打印成「✘ 用例仍然全绿，这条判据证明不了任何东西」——
#        **一条假发现**，指控的 `ShellRoutingContractTest.kt:109 / :116 / :128` 三条判据其实都是生效的。
#      · ⚠ **方向性**：负控的"红"这一侧 build cache 给不出来，所以只有**绿侧**会被污染 ⇒ "空跑"必然伪装成"判据失效"，不会反过来。
#   ② 三处机制缺陷与已落地的修法：
#      **A 无互斥** ⇒ 新增 `tools/negctl_lock.sh`（3,841 B / 75 行）：`mkdir` 原子锁 + 按 **WINPID** 判活
#        （cygwin 的 `$$` 与 Windows 的 PID 不同号 ⇒ `ps -l -p $$ //W`；存活判定 `tasklist //FI "PID eq N" //NH | awk '{print $2}' | grep -qx`，
#        ⚠ **不能用 `grep -c`**——会撞上内存列里恰好相同的数字串，把死进程读成活的）。抢不到 ⇒ `[STOP]` + **rc=3**；持有者已死 ⇒ `[接管陈旧锁]`。
#        16 本每本开头两行（实测 `grep -c negctl_lock` 每本 **2**、`grep -l` **16/16**）；批次脚本先持锁再 `export NEGCTL_LOCK_HELD=1`
#        ⇒ 批内各本沿用同一把（否则批内第一本会把后面 15 本全 `[STOP]`，那把锁就成了新的假红来源）。
#        ⚠ bash 的 `trap` 是**单槽**：`negctl_release` 必须**合进各簿原有的那条 `trap 'restore; …' EXIT`**，单独再 `trap` 一次会顶掉 restore，
#        那才是真的把源码留在破口状态。
#      **B 空跑注入** ⇒ `red()` 加两道闸（5 本 `*_test` 簿）：python 退出码非 0 → `✘ 锚点没命中，破口根本没注入` **exit 2**；
#        注入前后 `md5sum < file` 相同 → `✘ 注入后文件字节未变（空跑），拒绝记成判据失效` **exit 5**。
#        ⚠ **exit 5 与"判据不判"必须分成两种输出**：前者是**本书的缺陷**，后者才是**用例的缺陷**，混成一条就会把工具坏了写成代码有问题。
#      **C 死退出码** ⇒ 原先每本簿尾都是 `bash tools/check_X_probes.sh | tail -1`，整本 rc 恒为 `tail` 的 **0**（同一条坑**第三次**踩：
#        第一次 `verify_device.sh` 的 `… | tail -3 || die`，第二次是批处理按 `✔` 数行把 `idempotency_test` 记成 `✔=0` 假空跑）；
#        改成 `> /tmp/x.log 2>&1; PROBE_RC=$?; tail -1 /tmp/x.log; [ "$PROBE_RC" = 0 ] || exit 6`。
#        同一轮补齐的还有：基线不绿 **exit 2**、恢复时 `cmp` 不一致（`MISMATCH`）**exit 4**、恢复后 gradle 仍红 **exit 2**。
#      ⚠ 退出码口径**尚未统一**：`navigation_test_negative_controls.sh` 的基线红/恢复红仍用 **exit 1**（本书自己的老约定），
#        其余四本已是 2/4/5/6。登记为在册尾巴，不当已通过。
#   ③ 修复本身的证据（本轮实测，不靠"应该有效"）：`bash -n` 16 本 **ok=16 / bad=0**（00:43 复跑）；
#      锁的行为在隔离锁目录下复跑过——持有人活着时第二本 **rc=3 被拒**、持有人退出后接管陈旧锁**成功**、
#      `NEGCTL_LOCK_HELD=1` 时打印「由批次持有，本簿沿用不另取」；15 本 `check_*_probes.sh` **全部 rc=0**；
#      改前 16 份快照在 `/tmp/negctl_prelock_20261001-002718/`（本工程无 git，回退只能靠它 + `cmp`）。
#   ④ **两件刻意没做**：不抬 `-Xmx2048m`（内存已被排除成本轮故障原因，抬了就是"顺手改环境"而不是修缺陷）；
#      不加 `--rerun-tasks` / `--no-build-cache`（本轮的缺陷是**空跑**，不是缓存；绕开缓存只会让下一次空跑更难被发现）。
#   ⑤ 由此固化的收工顺序（写死在这里，别再靠记性）：**负控全批在前、全量用例在后**——
#      就地型负控会换源码、自带 `--tests` 过滤，并把 `app/build/test-results/` 洗成只剩一类；
#      一台机器同一时刻**只允许一个 gradle 调用**（㉑ 第 ⑧ 条），负控批跑完（打印"共 16 本"）之后才跑 `:app:testDebugUnitTest --rerun-tasks`。
#      ⚠ **2026-10-01 F-2 ⑧ 口径更正（那句"共 16 本"是当时 16 本的读数，不回改）**：批次结束的判据是**末行"共 N 本"，N 由 glob 数出来**
#        （`EXPECTED_BOOKS=$(ls tools/*_negative_controls.sh | wc -l)`，本轮实测 **17**）。把 N 写死在接力那一侧，第 17 本登记那天起这条
#        收工顺序就**永不匹配**（等到 `MAX_WAIT` 再 exit 8，而"等不到"读起来像"批次还没跑完"）——详见下面 **㉕ ④** / D-042 §4。
#   ⑥ ⚠ 尾巴（在册）：pass-1 / pass-2 那 7 本的历史读数**不回改**，只在 `AI_ITERATION_STATE.md` 原处加日期标注；
#      修锁后的串行全批复跑读数见同文件的负控段落（`/tmp/final_neg_batch.log`），未跑完之前**不得引用任何"16 本全绿"**。
#   ⑦ **串行复跑跑完了（00:35:40 → 01:20:15），而它抓到的第一件事就是源码本身**（2026-10-01，D-041）：
#      · 读数：**15 本 rc=0 + 1 本 rc=2 自拒**，`✘` 合计 1（就是那行自拒）、`△` 合计 **0**、`◦` 合计 3；
#        还原后把自拒那本单本复跑（✔=7 / ✘=0）⇒ **合并读数 289 ✔ / 0 ✘ / 0 △**。⚠ `✔` 列口径是 `grep -c '^✔'`
#        （**以 ✔ 开头的行数，含门禁行**）：本轮 282 行 = **266 行编号控制 + 16 行门禁行**（GUARD/BASE/CMP/DIR/REC/"恢复核对"/"全部破口已回滚"，
#        集中在 `contract_defaults` 3、`default_value` 3、`navigation_test` 2、`verification_failure` 8 四本）；
#        三个易读数：`idempotency_test` **只有 1 条**（IT-1 一条闸门打掉 10 例）、`navigation_test` 10 = M1…M8 + 2 门禁、`eta` 60 = 旧 54 + T-aj 家族 6。
#      · **新立的"基线不绿 exit 2"闸第一次上岗就拦下一条真实事件**：`shell_routing_test` 在 01:15:05 报基线 gradle **exit=1**，
#        红的是 `ShellRoutingContractTest.kt:109` —— 树里 `nav/ShellDecision.kt:36` 的 `deepLinkTarget` **带着一行没还原的负控注入**
#        （`role != Role.DRIVER &&` 守卫被删，脏态 4,374 B）。01:25:00 按**簿内 `red()` 的 from 串**原样还原（4,397 B），
#        随后单本复跑 7 ✔ / 全量 **37 类 356 例 0 失败** / `ADDED={VerificationFailure: 8}` `REMOVED={}` `CHANGED={}`。
#      · ⚠ **归因边界**（能说什么、不能说什么）：能确定"这行是**注入没还原**"（形状与 M-5 那条破口逐字相同、且簿子自带还原逻辑）；
#        **不能**确定由哪一本哪一分钟跑下的——无 git，`shell_routing_test_negative_controls.run.log` 已被后一次运行覆盖。
#      · **由此升为常驻规矩**：**"树干净"只能由跑用例证明**。`cmp 与备份一致` 只证"与本书**开头**那份备份一致"，
#        而备份是从**开始时的树**取的 ⇒ 树若已脏，还原只是把脏稳定下来；`check_*_probes.sh` 全 rc=0 也只证**结构锚点在位**——
#        本轮这 15 本探针在**那棵脏树上同样全绿**（SR-* 看不见"分支带不带角色条件"这种语义）。两者都**不能**拿来替代"跑一遍用例"。
#      · 同轮顺手修掉接力脚本自己的两处：`tools/after_negctl_run_full_tests.sh` 里三处 `grep` **缺 `-a`** ⇒ 批次日志被判定为二进制、
#        扫描尾补一句 "Binary file … matches" **混在读数列表里**（`grep -c` 不受影响，所以是"数字对、列表脏"这种最难看的假整齐）；
#        `rc=0` 不是 16 时**只打印不拦** ⇒ 现在逐本点名并明写"不得引用为本轮批次全绿"。**不硬拦**是刻意的：
#        自拒的那一本恰恰要靠紧接着的全量那一遍才能定位（01:20:31 那遍 rc=1 就是这么来的）。
#      · **01:52–01:56 收工自检复跑**（读数落 `AI_ITERATION_STATE.md` 常驻自检三行 + `DECISION_LOG.md` D-041 ⑧）：
#        15 本 `check_*_probes.sh` **ok=15 / bad=0**、`python tools/check_ledger_tables.py` **TOTAL FLAGS 0**、
#        安全六条 **0·4·0·0·0·0**、真实性关键字（只认 `tools/check_authenticity_counts.sh`）**窄 9 / 宽 44 / GeoMath 27 / DemoData 文件 4 / 假步进 0**。
#        ⚠ **这一遍又一次演示了"probes 全绿不证树干净"**：`check_shell_routing_probes.sh` 的 rc=0 在这里只等于"结构锚点在位"，
#        树干净那件事仍然是 01:25:50 那遍 **356 例 0 失败** 说的（④ 那条规矩的同轮复述，不是新证据）。
#      · ⚠ **新登记两条"口径漂移"，都不许写成"本轮没涨/涨了"**：宽口径 **45→44** 的 −1 只能归到**文件**粒度
#        （逐文件构成里唯一差项 `SendScreen` 2→1；无 git，那一行原文没留档 ⇒ "少了一行"是实测、"少的是哪一行"不是）；
#        `DemoData` 文件 **3→4** 的第四个是 `ui/common/Bits.kt:39`，其 **mtime 09-28 23:58 早于**"3 文件"那次读数 ⇒
#        判为**历史 D 与现脚本 `grep -rln` 扫描面不同**，是**口径漂移不是内容增长**，登记为待核尾巴。
#      · ⚠ **本轮自己制造的一次三口径风险，照实记**：收工时先手写 `grep -rniE "euclidean\|haversine\|straight" app/src/main --include=*.kt`
#        量出 46（无白名单）/ 34（滤注释行），与脚本的 9/44 是**三套口径**（扫描面、pattern、白名单三处都不同）⇒ 那两个手写数**一律不采信**。
#        同一形状的还有凭证 pattern：严格 `\{8,\}` **0 命中**，把下限放宽成 `*` 才出现 1 命中（`MinePages.kt:171 password = ""`，空串、且该文件 mtime 09-28 23:59）
#        ⇒ **口径不同的两个数并排放，比抄错一个数更危险，因为它看着像"变好了/变坏了"**（与 §八 ㉑ 的"两个 dex 口径不可互引"同族）。
#      · ⚠ 尾巴：`DECISION_LOG.md` 里 D-040 那句"两批并行**没有把源码留在破口状态**"（当时靠 probes 全绿下的结论）
#        **已就地标注证伪**，原句不回改。
# ㉕ **判据下限只能实测、不许推算**；新簿的闸门必须**逐条打红一遍**才算装配完成（2026-10-01，F-2 ⑧ / D-042）
#   ① 本轮把 `attempt` 的下限按"迁移前 12 − 我以为只迁走 2 处 = 10"写进**两本**簿（`SA-d` 与 `TH-f`），两本**同时红**、实测 **7**。
#      ⇒ 纪律是**先归因、再改数**：归因链每一环都落在留档产物上（⑦ 收工的 `/tmp/vf_probe_check_throwable_probes.log`，
#      2026-09-30 23:30:30，同一条判据、同一个扫描面）——5 个迁移点**全部**原本是 `attempt { … }.getOrNull()` ⇒ `12 − 5 = 7`，
#      非 Repo 那 7 处本轮一字未动。**"我以为的差值"不是"量出来的基线"**，同一个错我在本工程里犯了三次
#      （D-034 的"✔=18"推算、F2-7i 的注入空跑、本轮的下限推算），**三次都不是代码错，是我把推算当成了实测**。
#   ② **新写一本判据簿，当天必须逐条把它打破一遍**：SA 簿里那条 `[ "$C_VM" = 1 ] || { echo "…": fail=1; }` 用**冒号**代替分号
#      ⇒ `echo` 把 `:` 与 `fail=1` 当参数打印，**那条闸门永不生效**，且在负控跑起来之前完全静默。
#      ⇒ 凡"写死期望值"的行，要问的不是"它现在绿吗"，而是"**我把它打破过吗**"（与 §八 ㉓ ⑤ 那条 `| tail -3 || die` 同族：形状像守卫、其实从没执行）。
#   ③ **破口打进判据簿自己头上时，必须连"执行哪一本"一起换**：SA 负控第 17 条注入的是**沙盒副本**里的那本簿，`ctl` 跑的却是**真身**
#      ⇒ 破口根本没进被执行的那份，永远 rc=0。修法是给 `ctl` 加第三参指定跑哪本。
#      ⚠ `verification_failure_negative_controls.sh` 早就用 `$PRSC` 处理过同一件事，**本轮我没抄它的机制**——
#      与"先核契约再动手"是同一条规矩的两侧：**同族机制先查在册簿子里有没有现成的**，别重新发明一遍再重新踩一遍。
#   ④ **会随扩容变化的"本数"不许写死**：接力脚本的 `END_MARK` 原本写「共 16 本」，登记第 17 本那天起**永不匹配**，
#      会一路等到 `MAX_WAIT` 再 exit 8 —— 而"等不到"看起来像"批次还没跑完"，**不像**"口径过期了"。
#      现在批次与接力**共用同一个 glob**（`ls tools/*_negative_controls.sh | wc -l`）推 `EXPECTED_BOOKS`，两边都不写死本数；
#      历史那句"16 本全绿"的读数**不回改**，只在原处加日期标注（本文件上面 ⑤ 那行、`AI_ITERATION_STATE.md` 的 ⑦ 段都是历史口径）。
#   ⑤ 本轮实测（供下一轮抄数，全部可在盘上复现）：SA 判据簿 **15 行读数 / 34 道 SA 闸门 + 1 GUARD，rc=0**；SA 负控 **17 条破口，✔=20 / ✘=0 / △=0**；
#      负控全批 **`共 17 本`、逐本 rc=0，合并 ✔=309 / ✘=0 / △=0 / ◦=3**（02:39:11→03:10:35）；全量 **38 类 / 368 例 / 0 失败**（38 份 XML mtime 单一集合 03:11:16）；
#      `:app:assembleDebug` APK **114,437,929 B**（03:12:23）；lint 第一次 `lintReportDebug` **UP-TO-DATE** ⇒ 旧产物不当本轮读数，
#      就地 `--rerun-tasks` 重跑得 **0 error / 46 Warning + 32 Information = 78**；`for b in tools/check_*.sh` **17 本全部 rc=0**；
#      census **118 行**（`V21-07` `attempt` 生效 **7** · `safeApiCall` 生效 **9** · 旧名 **0**；`V21-30` 点名 18 类 present **6**）。
#   ⑥ ⚠ **仍是偏差、不当已解决**：`app/src/main` 那 **3** 个文件的两处真实行为变化**全部 `NOT VERIFIED ON DEVICE`**
#      （工程无 Robolectric ⇒ `BusScreen` 那两句文案在 JVM 里渲染不出来，SA-e 只能钉"分支与文案位点在位"，钉不了"屏幕上念的是这句"）；
#      线路页首屏自动选中线路却**不拉折线**那条**同族假话**（把"没问"说成"没有"）已登记 §九，本轮**一字未动**（动它=扩大战线）。
#
# ㉖ **跨簿影响只能是实测，不能是我对那一本怎么写的推理**（2026-10-01 09:44，F-2 ⑨ / D-043）
#   ① 本轮往 `Repo.kt` 放了 1 处 `attempt`，我在审计文档第五节写下"SA 的锚点指 `Repo.kt:312`、与本轮的 `:359`
#      **不是同一行** ⇒ 无需改；SA 簿复跑确认 rc=0"。**两句都是错的**：那句"rc=0"当时根本没跑（自我陈述当证据），
#      而实际扫全簿给的是 `check_safe_call_probes.sh` **rc=1** `× SA-d Repo 又出现 1 处 attempt` ——
#      破口不在锚点行号上，在 SA-d 数 **整本文件** 的 `D_ATTEMPT_REPO`（⑧ 之后不变量 = 0）。
#      ⇒ 纪律：**改了源码文本，就把每一本簿真跑一遍**，别只对"我以为相关的那几行"；"指向不同行"≠"不影响那一本"。
#   ② 同轮处置不是把判据调绿，而是把不变量**改准**：`=0` → **恰好 1 处 + 整行字面量点名**（`D_ATTEMPT_FORECAST`），
#      全 main 下限 7 → **11**（⑧ 的 7 + ⑨ 迁入的 4，逐点归因在 `docs/android/RUNCATCHING_AUDIT.md` 第二节），
#      并给 SA 负控**新增第 7b 条**："数量没变、只把那处换个名" ⇒ 只有点名字面量会红。
#      ⚠ 凡是把"0"改成"N"的豁免，都必须**逐处点名**并配一条"点名锚点被动"的负控，否则 N 会被用来藏任意 N 处。
#   ③ 本轮实测（供下一轮抄数，全部可在盘上复现）：`check_runcatching_probes.sh` rc=0，
#      RC-a **30/16/46/22**（本轮前 34/17/44/22，两套口径方向相反不是矛盾，见审计第一节）、RC-a2 **11**、
#      RC-b **0**、RC-b2 **30/0/98**、RC-c **1111 / 0/0**、RC-d **0/0**、RC-e **34/4**；
#      RC 负控 rc=0 —— 12 条破口全打破 + **2 条反向"必须不红"**（同行前缀不计、接收者不匹配只算 CANDIDATE）
#      + 2 条预检（检测器/源文件缺失 ⇒ **exit 2**，不许把"跑不了"读成 0）；SA 负控 **18/18**、TH 负控 19 ✔、SR 负控 13 ✔，三本恢复后基线均绿；
#      `:app:compileDebugKotlin` rc=0（`BUILD SUCCESSFUL in 27s`，15 tasks：1 executed / 14 up-to-date）。
#   ④ **30 点"保持"不是"30 点已验证无缺陷"**，是"包围边界非 suspend ⇒ 取消够不着"；这条论证的两条腿
#      （全工程 `runBlocking = 0`、`suspendCancellableCoroutine = 0`）由 **RC-d 钉着**：任一非 0，30 点的分类**整表重做**。
#      下一轮若看到 RC-d 变红，不要把它当成"一个计数超了"，要当成"第三节的结论全部失效"。
```

- `mock/fake` 仅允许出现在：测试、`BuildConfig.DEBUG` 分支、debug source set、注释里说明"禁止"、
  以及**带 `mock=true` 且界面明写来源**的兜底（当前只有天气，见审计 §46）。
- 构建校验：`./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`（本机需 `-Dfile.encoding=GBK`，见 `DECISION_LOG.md`）。

## 九、当前已知偏差（在册，不得当作已解决）

| 偏差 | 状态 | 台账 |
|---|---|---|
| ~~`Locator` 发系统 WGS-84 给 GCJ-02 接口，`CoordinateTransform` 零生产调用点~~ | **已修**（P1-3 ⑦）：换算落在 `Locator.toGcj02`（所有出口唯一）+ `RealRoadRoute.gcj02Points`（渲染层），`CoordinateTransform` 从 0 个生产调用点变成 2 个；实测重庆点偏移 **480.6 m**。**两条残留**：① `inChina()` 是**矩形**判定，东南亚（如 15.0,105.0）也在框内会被误转（已用测试钉住这个已知局限，不是"已解决"；本轮逐条对过 —— 与后端 `coordinate.py:29-35` 的 `out_of_china` 是**同一个矩形**，所以这是**两侧共有**的局限，不是 Android 独有 bug）；② 部分 OEM 的网络定位**本身回 GCJ-02**，再转一次就是二次偏移 —— 只有真机可判，记 `ENVIRONMENT_BLOCKED`。§17 方案 5 的跨仓登记已做（后端 `docs/REAL_ROAD_ROUTING_ARCHITECTURE.md` §4 新增"Android 客户端边界"行 + 补记，并修掉文档里不存在的函数名 `is_in_china` → `out_of_china`） | 审计 §17/§65、D-008、D-019 |
| ~~位置上报失败期间的点永久丢失（原方案：有界待发队列）~~ | **已修（改法与原方案不同）**：后端 `AppDriverLocationReqVO` **没有任何客户端时间字段**，`DriverAppServiceImpl.reportLocation` 用 `LocalDateTime.now()` 落库，重放历史点等于**给旧位置盖新章**。所以做的是**合并语义**（只发最新 fix）+ 45 秒新鲜度闸（过期就停报并写明原因）+ 丢弃计数可见。**代价如实记**：弱网期间的中间点不可恢复地丢了，界面显示"已丢弃 N 个未确认点"，不装作补得回来 | 审计 §45/§65、D-019 |
| ~~`CommonResult` 标量信封读不到 → 未读角标恒 0、上传 URL 恒空、交接照片未入库~~ | **已修**（P2-1）：`Payload` 新增标量通道（按 `data` 的**实际类型**填值，对象/数组两支一字未动），`Repo` 三处改走 `longOf/strOf`；`unreadCount`/`driverUnreadCount` 返回 `Long?`（读不到=null，不冒充 0），`uploadPhoto` 拿到真实 URL；**连带**修掉 `HandoverTask.status` 读数字（后端 0..7）导致"已完成交接永远留在待办"，改 `statusName` 优先。**判据 §八 ⑨ + `ScalarEnvelopeContractTest` 17 例**。**未做**：真机"拍照→后端落库→回读还能看到照片"的端到端，记 `ENVIRONMENT_BLOCKED`（审计 §57 新增一行），跑完之前只能说代码闭环，不能说照片链路已验证 | 审计 §22/§23/§52/§7 |
| **角标"红点自己闪没"（三个写入源，其中一个恒 0）** | 与上一行同批修完：`AppState.unread`/`driverUnread` 改成 `private _x + asStateFlow()`，外部**编译期写不了** `.value`，只能走 `report*(serverCount, listCount)` 一个入口；两路**取 max**，两路都读不到时**保持上一个值**（不写 0）。7 个原写入点全部改造（`AlertEngine` 3 + `NotificationScreen` + `ParcelScreen` + `DriverMessagesScreen` 2） | 审计 §52、§八 ⑨ E-a |
| **`NotifyMsg.orderNo` 是幻字段**（后端 `AppNotificationRespVO`/`TransportUserNotificationDO` 都只有 `orderId`，没有 `orderNo`）→ 用户通知深链 `trace/<orderNo>` 在真机上恒为空 | **新发现，未修**（P2-1 登记）。小程序缺 `orderNo` 时回落 `trace?id=<orderId>`，Android 没有回落。修法要先定"轨迹详情页按单号还是按 id 寻址"，涉及后端查询参数语义，**不夹在信封修复里做** | 审计 §5 末尾、§49 |
| ~~段状态机两套（客户端 `nextStatus` 2/3/4 vs `TransportLegStatusEnum` 0..11+99）~~ | **已修**（⑨）：状态码表只有 `data/model/ServerStatus.kt`（6 套后端枚举，未知码→null、缺键→-1、文案优先后端 `statusName`），司机动作只有 `ui/driver/DriverLegStateMapper.kt`（**没有任何函数返回"下一个码"**）；三张本地表（`RoutesScreen.nextStatus/labelOf/私有 nextActionOf`、`HandoverScreen.LEG_ACTIONS/legStep`、两处 `done = status >= 4`）删除，POST 后必回读。判据 §八 ⑩（S-la/S-lb=0、S-le=2 且两处带回读）+ `DriverLegStateMapperTest` 11 例（把后端 `TRANSITIONS:42-55` 逐码抄成期望）。**三条台账建议被源码推翻**：`0→accept`、`2→arrive-origin`、`8→complete` 都过不了 `canTransit`（`MultiLegServiceImpl.advanceLegStatus:700`→`:714`），小程序 `handover.js:101` 那一格同样会被拒 ⇒ 属"三方一致但一致地错"，不对齐。**残留**：`Repo.legAction` 形参仍是 `String`（§39 建议的 `Long` 编译期守卫未做） | 审计 §26/§27/§28/§18、D-021 |
| **`TransportLeg` 声明了段级几何（`navigationPolyline/fromGeo/toGeo`），后端 VO 从不下发** | **未修（刻意不修）**：字段按兼容规则保留，**渲染路径已摘掉**，几何一律来自 `Repo.routeGuide()`；契约测试正面断言"按真实 VO 报文解析后几何必须为空"。真正的"按段画真道路"要与 §44/§46 的地图接线同批做，**不许**在这一批里用直线补几何 | 审计 §36 ②③、D-021 |
| **两个必 400 的写路径**（`confirm-station-action` 参数位置、收班把 `"shift-$id"` 当 `legId`） | **已修**（⑨）：`Api.post` 加 query 重载 ⇒ `orderId` 走 query（对上 `AppSendController.java:129` 的 `@RequestParam`）；收班改 `POST /driver/arrive` 到末站（`DriverAppServiceImpl:914-920`），末站 id 缺失则**不提交**并给出口文案。**未做**：无 MockWebServer ⇒ "query 里真的带上 orderId"仍是代码读证而非拦截器级断言（`ENVIRONMENT_BLOCKED`） | 审计 §39、D-021 |
| ~~`DriverNavScreen` 仍是墙钟驱动的假步进（4 秒自动播转向）~~ | **已修**（2026-09-30 #51 / D-032）：那套机器（`var stepping` + `LaunchedEffect` 里的 4 秒循环 + DEBUG 那个"逐条推进"开关）**物理删除**，`next()` 收窄成 `showNextStep()`（司机自己翻页，不推进车的位置与班次状态）。判据口径同时**抬高**：假步进从"只看旧屏一个文件"改成 grep **整个 `ui/driver` 目录**含注释裸计数（N-m），实测 **6 → 0**，**九条白名单一字未改**。宽口径 49 → 45 的归因是实测的：只有 `DriverNavScreen.kt` 8 → 4，其余 16 个文件逐项未变，剩的 4 行全是禁止性说明。**两条残留**：① 旧屏是**降级**不是删除（用户硬规则"不要直接删除文件"），文件本体、`Routes.DRIVER_NAV` 常量、`onNav`/`onOpenNav` 形参全在，release 下组件自守只渲染一句 `DebugEntryClosed()`；② **闸门本身零真机验证**（下一行） | 审计 §24/§78、D-015、D-032、判据 §八 ⑲ |
| **正式导航面换了位置，而"换对了"目前只成立到结构与 JVM 用例这一层** | #51 把 release 可达的导航面重挂到工作台：`DriverMissionMap`（后端折线）+ `DriverSdkNavCard`（本机高德引擎）+ 卡片底部"交给高德 / 交给百度"（对齐小程序 `wx.openLocation` 那条"交给外部地图"的语义，目的地只读 `nextNavigableStop`）。判据能钉的只有结构（N-p 0/1/1/0、N-g 第六位恰好 1、N-n 0/1/1），**四条一律 `NOT VERIFIED ON DEVICE`**（补进上一行那条未验家族）：① 正式包里点旧入口是不是真的只看到 `DebugEntryClosed` 那一屏；② 外部地图在"装了高德 App"与"没装"两条分支上的实际行为；③ 没有任务点时按钮禁用态在窄屏是否折行；④ §77 二那两条单位/语义口径本轮**一字未改**，仍未验 | 审计 §77 二/§78、D-031、D-032、判据 §八 ⑲ |
| **线路页的"导航"入口只在 DEBUG 可达（release 那一屏没有导航按钮）** | **新登记的偏差，刻意不补**：`TransportLeg` 只有站名、**没有任何坐标字段**（§36 那条在册偏差的本体）⇒ 在 release 里拼不出 `destination=lat,lng`，硬要能跳就得拿站名反查一个点，那正是 §36 禁止的补法。**解锁条件**：后端下发段几何或站点坐标（§36/§44 同一条路），届时放出入口并同轮补判据。**代价如实记**：司机在线路页要导航得先回工作台那一节；**功能少一条，比功能假一条好** | 审计 §36/§44/§78、D-032 |
| ~~`WorkbenchScreen` 用客户端 Haversine 当"确认到达"GPS 门槛~~ | **已修**（P1-1b）：门槛与常量删除，`canArrive\|ARRIVE_RADIUS_METERS` 实测 0 命中；直线值只剩带标注的展示出口。**与小程序有意分歧**，不复刻被禁止的 GPS 放行 | 审计 §32、D-017 |
| ~~导航 HUD 把后端直线值当"剩余里程"~~ | **已修**（P1-1b）：改读 `nextStopRoadDistanceMeters`，`ui` 包内旧字段读点实测 0；字段与解析按兼容规则保留 | 审计 §63、D-017 |
| ~~用户点"司机模式"直接 `setRole(DRIVER)`，不经后端档案；`driverId<=0` 时 19 个司机请求照样发 `driverId=0` 换回一片空列表~~ | **已修**（P1-2 ⑥）：`MineViewModel.enterDriverMode` 先 `bindDriver()`，未授予 driverId 不进司机域；`Repo.driverIdParam()` 是唯一 driverId 入口（未绑定直接抛 `DriverUnboundException`，请求根本不出网），字面量拼装实测 0 | 审计 §29、D-018 |
| ~~「用户版」退出只换路由不改 `Prefs.role`，`AlertEngine` 继续按司机域轮询 `/driver/messages`；司机通知深链乘客侧 `trace/*`；带 `legId` 的司机请求在用户态可被本地通知拉起~~ | **已修**（P1-2 ⑥）：退出写回 `Role.USER`；司机通知落 `DRIVER_MESSAGES` 并把单号放正文；`Routes.DRIVER_ONLY` + `deepLinkAllowed()` 做角色闸 | 审计 §29/§45/§64、D-018 |
| ~~换账号（不退出直接登录）继承上一位司机的 `driverId`；后端回空档案时本机身份不清~~ | **已修**（P1-2 ⑥）：`saveSession` 先 `clearDriverBinding()`；档案/绑定返回非演示且有条目却无 driverId 时跟着掉绑定。**回 `data:null` / 演示回落算"未知"，保留既有绑定**（否则一次抖动就把真司机锁死） | 审计 §29/§64、D-018 |
| ~~高德**导航** SDK 未进依赖，后端也不下发逐转向数据~~ | **已解除**（2026-09-30 N-1）：依赖顶替为 `com.amap.api:navi-3dmap:10.0.600_3dmap10.0.600`（坐标数仍 20 条，N-i 实测 1/0/0），`core/navigation/` 两层引擎 + `DriverSdkNavCard` 调用点 + 19 用例 + N-a…N-l + 18 结构负控 + 8 用例级负控全绿。**"后端不下发逐转向"这半条不变** —— 后端仍不给 turn-by-turn，屏幕上的转向只有两个来源：后端 `routeGuide` 的段/站，与本机高德。**没有第三个来源** | 审计 §24/§57/§77、D-016/D-031 |
| **本机导航层的运行时行为零验证**（上一条解除后**替换出来的新偏差**，不得当作已解决） | `NOT VERIFIED ON DEVICE`（`verify_device.sh --no-build` 实测 exit 1、设备列表空）。四件具体未知：① `onInitNaviSuccess/Failure` 到底回不回、缺 key/无网/离线时**静默不回调**持续多久 ⇒ `Initializing` 与"永远不回来"在 JVM 层**不可分**，所以这层**故意没有**"初始化等待上限"那个数（编一个数就是假判据）；② `getPathRetainDistance/Time` 的**单位**、`getCurStep()` 的**语义**（javap 只给类型不给口径，明细审计 §77 二）；③ 后台定位权限与 TTS 播报；④ APK 体积 **126,516,687 B**（较换依赖前紧邻的在册读数 **57,941,374 B**（状态板 `BUILD_STATUS`）多 **+68,575,313 B ≈ +68.6 MB / 2.18 倍**，⚠ 跨轮对照、非受控 A/B；归因**已实测**：包内高德 `.so` 净增 **+43,681,128 B**（两件 jar 的 `lib/` 解压量相减），其余 **24,894,185 B 只是残差**（上一轮包的 dex/assets 未留档，增量分不出来），明细在审计 §77 七）与高德专有许可证（已回填 `THIRD_PARTY_NOTICES.md` §3） | 审计 §77 二/六/七、D-031、判据 §八 ⑱ |
| ~~`BusMap`（唯一真地图）零调用点，全站用自绘 `RouteMap`~~ | **已接线**（P1-1：`DriverMissionMap` 优先真图，无 key/无几何回落）；残留 `ENVIRONMENT_BLOCKED` = 真机渲染与 key 校验未验证 | 审计 §44、D-016 |
| `/driver/route` 任务点 9 个业务字段整体没被读（顺序/动作/件数/状态丢失） | Android 侧**已修**（`MissionStop` + `RouteGuide.stops` + 地图标记 + 工作台时间线，状态色四态含 FAILED）；后端是否真下发待端到端联调 | 审计 §62、D-016/D-017 |
| 真机项全部未验证：地图渲染、AMAP key、GPS 精度、前台服务上报续报、任务点顺序与状态色的实车核对；⑦ 新增三条 —— 弱信号下 45 秒新鲜度闸的**实际停报表现**、前台通知文案（"已丢弃 N 个未确认点"是否真出现在通知栏）、`Locator.fixOf` 的 WGS→GCJ 出口（工程**无 Robolectric**，JVM 只能测纯函数） | `ENVIRONMENT_BLOCKED`（无设备在环，JVM 单测不冒充） | 审计 §57、本文件第六节 |
| `app-debug.apk`（63 MB）仍在仓库根目录 | 待移出（`.gitignore:27` 已拦住，不入库） | 审计 §50、§60 |
| ~~公交 ETA 没给时渲染成"0 分钟 / 马上到"，进度条由 `1 - eta/40` 自造~~ | **已修**（⑪）：`AppBusServiceImpl:132/:728` 两处 ETA 都是 `Math.max(1,…)`、`:134` 算不出直接 `setEtaMinutes(null)` ⇒ **0 在契约里不合法**，且 HTTP 响应保留显式 null 键（MVC mapper 不过滤 null）。模型 8 个数值字段改可空 + `multiNumOrNull`；`roadProgress` **只认后端 `progress`**（`:125-127` 的"后端给 0"与"没给"是两件事，反推会把两者搅平）；文案走 `etaValueText`/`etaCaptionText`/`nextDistanceText`/`providerText`，删掉"现实公交/项目线路"那种替后端背书的标签；`byUserDistance()` nullsLast 对齐 `:436-437`。**顺带修掉三处只在真后端下现形的静默断链**：`busId`/`plateNo` 不在旧候选名里（车辆 id 与车牌恒空）、`statusOf` 把整数 2 读成"已到终点站"（2 是停用）且码表里有个后端从不发的 3、`sourceOf` 把 `dataSource=REAL` 升格成 `REAL_FRESH`（`/bus/lines` 没有 `locationSource` 键）。**判据 §八 ⑫ + `BusEtaContractTest` 9 例 + 25 条负控制** | 审计 §38/§35、D-023 |
| **到站提醒的数据源根本没有 ETA 这个键**（`AppSendArrangementRespVO.java:8-43` 共 11 个键，无 `etaMinutes`） | **已修**（⑫ 迁数据源，§68 收口）：提醒与两个用户侧列表改读 `/send/page` 的**承运**字段，过滤链 `carrierLocationSource != "REAL_STALE"` → `mapNotNull { carrierEtaMinutes }` → `in 1..10`，文案**"车快到了 · 还有约 N 分钟（直线估算）"**。⇒ 提醒从"真后端下结构性不响"变成"能响，且如实说这是估算"。`carrierEtaMinutes` 出自 `GeoDistanceUtil` 的 **Haversine + 25 km/h**（@Schema 自己标了直线），所以**每一处展示都必须同行带口径**（判据 T-v=5 / T-v2=0）。`Arrangement.etaMinutes` **保留可空字段**（不删字段，兼容旧解析），ui/notify 的 import 消费点实测**归零** ⇒ 现在是"字段留着、没人读"的状态，探针尾部如实打印 | 审计 §68/§71、D-023、D-025、判据 T-w1/T-w2/T-y |
| **车辆端点没有任何一个带 `routeId`**（两个车辆 VO 里 grep 0 次；`routeId` 只在 `AppBusLineRespVO:13`） | **部分已修**（⑪）：旧 `busDetail(v.routeId)` 在真后端下恒为 `busDetail("")` —— 不崩不报错，只是点进空白详情。线路页改按 `routeName` 回查行 id（`BusScreen.lineIdOf`），解析不到就置灰；首页 `NearbyLine` 只有 4 个键、**连线路列表也没 id**，所以那一行**如实不可点**（少一个功能可接受，"看着能点其实点不开"是骗人）。`BusVehicle.routeId` 按兼容规则**保留不删**，KDoc 写明"真接口下恒为空串"，T-j2 钉住演示报文不许再造这个键。**根治在后端**：重名线路出现时应由 `/bus/nearby` 的车辆带上 `routeId`，客户端不许取首条匹配或给名字做 hash | 审计 §69、D-023 |
| ~~货运侧 ETA 家族仍是 null→0 塌缩（`Models.kt` 两处 + `Repo.kt` 两处 + 页面 6 处 `etaMinutes > 0`）~~ | **已修**（⑫）：`SendOrder.etaMinutes` / `TransportLeg.etaMinutes` / `ParcelTrace.etaMinutes` / `RouteGuide.etaMinutes` / `RoutePreview.distanceKm·durationMinutes` 全部改可空，读法一律 `multiNumOrNull(...)?.toInt()`；`routePreview()` 去掉 `etaMinutes` 别名（那个键在该契约里根本不存在）。页面不再出现 `etaMinutes > 0`（T-u=0），"没给"的如实文案 7 处（T-z）。**读出来的四条契约事实**（`/send/page` 不填班次 ETA、一个字段吞了两种口径、承运块整体可能不填、两个段契约没有 ETA 键）记在审计 §71 与本文件 §四 | 审计 §38 残留/§71、D-023、D-025、判据 T-r1…T-r5/T-s/T-u |
| ~~`TransportLeg.distanceKm = 0.0` / `durationMinutes = 0` 仍是非空默认值~~（`AppDriverLegRespVO:58-62` 这两个键都可空） | **已修**（⑬）：两字段改 `Double?` / `Int?`，读法走 `multiNumOrNull`；段卡文案新增 getter `legPlanText` —— **缺哪个就不提哪个，两个都缺整行不显示**（`TraceView` 用 `?.let` 渲染，不再打印 "0.0 km / 计划 0 分钟"）。⑫ 时它是探针尾部**打印的残留 2**，⑬ 把它升格成判红 `T-ad2 = 2`；`T-s` 那句"明确排除 `durationMinutes`"的理由（别把两段契约并进一条判据）仍然成立，排除的是**正则作用域**，不是放弃这个字段。同批未修：`Repo.kt` 的 `optBoolean(…, true)`（下一行） | 审计 §71/§72、D-025、D-026、判据 T-ad1/T-ad2/T-ae |
| ~~ETA 家族候选键名里那个**三方都没有的** `"eta"`（6 处：`Models.kt` BusVehicle/SendOrder/TransportLeg/Arrangement + `Repo.kt` traceOf/routeGuideOf）~~ | **已修**（2026-09-30 E-1 / D-033）：六处读法全部退回只认 `etaMinutes` 一个键（BusVehicle 那处还多带一个裸 `"minutes"`）。**为什么它比 `null→0` 更难抓**：T-a…T-z 判的是"没给不许塌成 0"，而别名键读出来的是**一个确实存在的数**，来源是客户端自己编的 —— 后端 / 小程序 / 演示面三方实测 `"eta"` 各 **0 命中**（同口径 `etaMinutes` 在后端 java 有 13 处），所以它是**凭空多出来的第四个真相源**。收法是**删读法**，不是给演示面补一个键来"让它合法"（后者会把自造键洗成契约，故同轮立 T-ai3 把这条诱惑钉死）。判据三条实测 **0/0/0**，用例同轮 12→**13** 与 9→**12**（含"只带别名必须读成 null"与"真实键照常工作"两条一对，防"整条读法删掉"骗绿）。**残留**：其余 `Minutes` 家族 **5 处**第二候选名（`Models.kt` 479/729/1110/1328/1802）本轮**未动**，理由是这些键可能是小程序真在用的而手里没这份实测 —— 已登记为下一轮队首，判据里以"·"行如实打印，**不当已通过**〔**F-2 第 2 步已收（2026-09-30 / D-035）**：这五处逐端点四面核对后，自造第二候选名**删净**（`T-aj/T-aj2/T-aj3` 实测 0/0/0，逐键证据在 `docs/android/CONTRACT_EVIDENCE.md:15/:18`）；本行当时那句"可能是小程序真在用的"**被部分证实** —— `walkMinutes` 确是真键（`AppSendReachabilityRespVO:32`），处置是**保留真实键、只删别名**而不是整条读法拆掉。⚠ 别名删净 ≠ 默认值形状修净：`plannedMinutes`（`Models.kt:469 Int = 0` / 读法 `:500 multiNum(…).toInt()`）与 `plannedDurationMinutes`（`:1337` / `:1381`）**仍是 B 类缺键当 0**，落在 `DV-k=132` 基线内，归下面 §五 那行的下一轮〕 | 审计 §79、D-033、判据 §八 ⑳ |
| ~~`Repo.kt` 的 `inService`/`located` 用 `optBoolean(…, true)` —— 缺键时**替后端默认"在服务 / 已定位"**~~ | **已修（2026-09-30 F-2 第 4 步 / D-035）**：两字段改 `Boolean? = null`，读法走 `o.multiBoolOrNull("inService")` / `("located")`（现落在 `Repo.kt:726/:735`），`NearbyResult.isOffService` 只在 `inService == false` 时为真 ⇒ **"没给"不再是"运营中"**。四面取证：后端 `AppBusNearbyRespVO.java:65 Boolean inService`（`AppBusServiceImpl:481/:532` 赋值）、`:45 Boolean located`（`:256` 按经纬度合法性赋值）；小程序按**三态**读（`pages/bus/index.js:240` `!= null ? … : null`，`:288/:291` 分判 `=== false` / `=== true`；`pages/index/index.js:539/:554` 同口径）；旧 DemoData **根本不写这两个键** ⇒ 演示面也在替后端默认。**`located` 当前没有 UI 消费点仍改可空**（默认值本身就是谎言，不需要"有人读"才配修）。判据 `B-a 2 · B-b 0 · B-d 0 · B-e1 1 · B-e2 2 · B-e3 0 · B-f 0 · B-g 6`，`NearbyBooleanContractTest` **6** 例，负控 **22 ✔ + 1 ◦**。**残留（登记在册，不当已通过）**：`B-c` 打印的 **1** 处 `SendScreen.kt:209 fresh = o.optBoolean("fresh")` —— 缺键当 **false**（把"没数据"说成"不新鲜"），三面生产者未核 ⇒ 归 §五 的 B/C 分类处置 | 审计 §38 残留 → D-035、判据 §八 ㉒ |
| 演示 nearby 仍发旧键 `stations`（`DemoData.kt:394`，真实键是 `nearbyStations`）；`DemoData.kt:644` 仍应答 `/bus/realtime` 而**全工程无调用点** | **未修（⑪ 登记）**：`Repo.kt:667` 已按真实键优先读，所以兼容能过、但兜底形状与契约不同形（R-l 那类"演示面形状贴合真实契约"的判据目前只覆盖可达性）；死端点留着会误导后来人以为有这条接口。**处置原则照旧**：不删文件、不删字段，改形状或加"该端点无调用点"的注释说明，别顺手清成"看不见" | 审计 §38 残留、§49 |
| ~~§35 还剩两项未收口：`etaToNextStationMinutes`（`AppBusRespVO:49-50` 的精确 ETA）**没进模型**、`BusLine.distanceMeters` 仍读 `distance\|meters` 而真实键是 `distanceKm`（`AppBusLineRespVO:27-28`，上游 `MonitoringServiceImpl:126` 可 null）~~ | **已修**（⑬）：模型新增 `etaToNextStationMinutes: Double?`（**只认这一个键**，不并进 `etaMinutes` 的候选名列表 —— 它俩口径不同），`nextStopMinutes` 的取数顺序是"精确值优先 → 否则仅当 `running && progress == null` 才把 `etaMinutes` 念成到下一站"，取整口径抄小程序 `pages/bus/detail.js:132-137` 与后端 `Math.max(1, ceil(…))`（0.2 分钟不许显示 0）。`BusLine.distanceKm: Double?` 改可空并按真实键读，线路页"全程"从恒显示"—"变成"后端给就说、不给才说没给"。判据 T-aa/T-ab/T-ag/T-ad1 + `BusEtaContractTest` 12 例。**没解决的那半**：`/bus/lines` 与 `/bus/nearby` 的 `etaMinutes` 语义分岔仍只能靠 `progress` 是否存在来推断（后端没有任何判别键），推断不了就显示"—" —— 根治要后端把口径写进键名 | 审计 §35/§72、D-023、D-026、判据 §八 ⑫+⑬ |
| **`/driver/position` 只有 3 个键（longitude/latitude/dataSource），司机页却把它念成"在途 + 下一站"**（`AppDriverPositionRespVO`，端点 `AppDriverController.java:258-263`） | **已修**（⑬）：`RoutesScreen` 那句 `BusTag("在途 ${it.nextStation}")` 改读 `BusVehicle.positionOnlyTag` —— 有 `dataSource` 就说"位置来源 X（未给状态）"，没有就说"位置来源未给"。**这一处是 ⑫ 那条"照抄下一轮"的直接产物**：当时我据此断言"端点不存在"并写进台账，⑬ 读 `AppDriverController` 才发现端点在、只是我查错了文件，于是把**错的结论**换成了**对的字段边界**（改口记录在 D-026 第 6 条，没有抹掉 ⑫ 的原话）。判据 T-ah = 0（注释行按内容过滤） | 审计 §72、D-025、D-026、判据 T-ah |
| ~~**ETA/里程候选名里还剩 6 处自造别名 `"eta"`**（`Models.kt` 的四处 `multiNumOrNull("etaMinutes", "eta", …)`，实测现落在 :721/:937/:1056/:1616；`Repo.kt:587/947`）~~；`QuoteResult` 的三个金额字段（`amount/itemFee/distanceFee`）仍用 `multiNum` 非空读法，缺键塌成 0.0 | **两半都已修**：别名那一半 **2026-09-30 E-1 归零**（六处读法收窄成单键，`T-ai/T-ai2/T-ai3` 实测 0/0/0，收口记录见上面那行 —— 两行抄的行号口径不同，**这一行保留 ⑬ 当时的实测位置、不回改**）；金额那一半 **F-2 第 3 步收口**（D-035）：`QuoteResult` 五个数值字段（`amount/itemFee/distanceFee/itemCount/distanceKm`）全改 `Double?`/`Int?` + `multiNumOrNull`，界面只走 `amountText`/`amountValueText`/`feeBreakdownText` 三个 getter，缺键显示"金额未给"/"未给"（**刻意不照抄指令字面的"暂无报价"** —— 那句暗示"后端算过了、结论是没有报价"，与"这个键不在报文里"是两种事实；APK 内实测"暂无报价" **0** 处）。`fallbackCount` 兜底通道与 `coerceAtLeast` 一并归零（Q-c1/c2=0：客户端件数不许替后端说话）。判据 `Q-a 5 · Q-b 0 · Q-b2 0 · Q-c1/c2/c3 0 · Q-d 0 · Q-d2 0 · Q-e1 3 · Q-e2 4 · Q-f 7`，`QuoteNullSafetyTest` **7** 例。⑬ 那段"别把两段契约并进一条判据"的告诫**本轮又被验证一次**：`Q-e1` 原本按成员名扫全 `Models.kt`，于是把 `EarningItem` 的同名 getter 也算进来（→ 已收成 `QuoteResult` **类体**，见判据 §八 ㉒）；`walkMinutes` 经四面核对**确实是真键**（`AppSendReachabilityRespVO:32`），没有照 E-1 的处置删掉它 | 审计 §72 → §79 / D-033 / D-035、判据 §八 ⑳+㉒ |

| **§五 数字默认值的结构性普查已完成，但只收了 Earnings 一族**：472 处带默认值读法 = **B 398 / C 74 / A 0**，其中数值/布尔**非可空**读法基线 **132** 处（`DV-k` 钉住，本轮由 152 降下来） | **普查已落档、修复未铺开**（2026-09-30 F-2 第 5 步 / D-036）：明细 `docs/android/DEFAULT_VALUE_AUDIT.md`（含"改前/改后"两列基数与 A=0 的结构性依据：后端 App RespVO 字段清一色包装类型，primitive 命中 **0** ⇒ 每个非可空读法都站在"后端可以不给"的字段上）、逐字段生产者 `docs/android/CONTRACT_EVIDENCE.md`。**为什么不一遍改完 398 个 B 类站点**：指令 §五 同时写着"禁止 B/C 冒充 A"与"不要扩大战线、不要一次性制造 38 个编译问题"⇒ 本轮只把 **C 类里唯一"整屏结论都来自无生产者键"的 `/transport/driver/earnings`** 收掉（`week/month/totalWeight/type/items[].amount/date` 六处：`AppDriverEarningsRespVO` 全模块 0 命中，**只有 `DemoData.kt` 在发**）。**在册待逐族做的 32 处**：天气温度/风速 3、距离/米 5、`recommendedLane`/`lane` + `recommended|best|selected` 12、商品·车辆·清单 `Models.kt` 7、`Repo.kt:616`/`:997`、`SendScreen` 草稿 `pickup?.id ?: 0L`（这条是 D 类本地占位，与 B/C 不同性质）。⚠ **`DV-k` 只许往下走**：新增一处非可空数值读法就响，这条是防止下一轮"顺手把战线铺回去"的闸门 | D-036、判据 §八 ㉒、`tools/check_default_value_probes.sh` |

| ⚠ **`VerificationFailure` 四类的落点与指令 v2.1 §三十三的字面不同**（F-2 第 7 步 / V21-33）：类型**不在 `app/src/main`**，而在 **test 源集**（`app/src/test/java/com/shanxiang/buspost/verification/VerificationFailure.kt`）+ 一份 **bash 规则表/分类器**（`tools/verification_failure_rules.txt` · `tools/classify_verification_failure.sh`） | **已落地并登记为"刻意偏离"**（2026-10-01 / D-039）：理由是**运行期没有消费者** —— 构建失败时 App 进程根本不存在，把 `sealed interface` 放进 `main` 只会让它成为编译期装饰；而业务侧失败已有 `ApiException` · `NetworkException` · `Ack.error` 三套，再塞一套进 main 就是**第四份真相**（判据 `VF-d` 把这三套钉成 **1/1/1**、把 main 里的四类符号钉成 **0/0**，同轮反证 test 源集 **10** 命中以证 grep 没坏）。**真实消费者**是 `verify_device.sh` 的构建分支与无设备分支，两侧共读同一张规则表 ⇒ bash 侧 `grep -aF` 与 JVM 侧 `String.contains` 的等价性由用例逐行验（规则表因此禁止非 ASCII 与 glob 元字符）。样例只用**本工作区实测产出的日志**（`tools/fixtures/verification-failure/`，7 份 = 6 正向 + 1 反例 rc=4），**不收合成日志**；出处列逐行回核（`VF-a` 指对 9 / 指错 0）。⚠ **两处仍不是"已通过"**：① `assembleRelease` 本轮 rc=1 而分类器判 **UNCLASSIFIED** —— HTTPS 闸门（`app/build.gradle.kts:171`）是**策略拒绝**、不属于四类构建失败，**没有为让它归类而加规则行，也没有为它绕过闸门**，release 仍在 `BLOCKED`；② 分型行为一律 `NOT VERIFIED ON DEVICE`（真机上"构建失败长什么样"这件事本机不可验） | D-039、判据 §八 ㉓、`tools/check_verification_failure_probes.sh` + `tools/verification_failure_negative_controls.sh` |
| ⚠ **负控簿自己有两处"永远绿"**：① 16 本全批与第二批**并行 18 分钟**而"一次只跑一个"这句话**没有任何机制执行**；② 注入是**空跑**时输出与"判据不判"长得一模一样（未修改的源码撞上 `FROM-CACHE` 的绿色被读成"这条判据证明不了任何东西"）；③ 簿尾 `bash check_X_probes.sh \| tail -1` 让**整本退出码恒为 tail 的 0**，那本判据簿红着也报"通过" | **机制已落地**（2026-10-01 / D-040、判据 §八 ㉔）：`tools/negctl_lock.sh` 挂进 **16/16** 本（抢不到锁 `[STOP]` + rc=3、按 WINPID 判活、陈旧锁可接管、批次持锁时 `NEGCTL_LOCK_HELD=1` 让批内沿用同一把）；`red()` 补"锚点没命中 **exit 2** / 注入后字节没变 **exit 5**"两道闸，另补基线红 **exit 2**、`cmp` 不一致 **exit 4**、恢复后判据簿 rc≠0 **exit 6**；顺序也做成了机制（`tools/after_negctl_run_full_tests.sh`）。⚠ **三处仍是偏差，不当已解决**：**①'** ~~修锁后的**串行 16 本复跑本轮还没跑完**（00:35:40 起，截至 00:50 完成 2 本）~~ **2026-10-01 01:20:15 已跑完**：15 本 rc=0 + 1 本被基线闸自拒（rc=2），还原后单本复跑补上 ⇒ 合并读数 **289 ✔ / 0 ✘ / 0 △**（`✔` 口径见 §八 ㉔ ⑦）； 两批并行那 7 本的历史读数**只在原处加日期标注、不回改**；**②'** 退出码口径**尚未统一** —— `navigation_test_negative_controls.sh` 的基线红/恢复红仍用 **exit 1**（该书自己的老约定），其余四本已是 2/4/5/6；**③'** 五本 `*_test` 簿的 `-Xmx2048m` 与全量配方 `-Xmx3072m -XX:MaxMetaspaceSize=768m` **不一致**（本轮实测已排除"内存不足"是这次故障的原因，所以**没有顺手抬堆**；要统一得连那条排除理由一起改）；**④'**（2026-10-01 01:15 串行复跑**新登记**）**基线闸第一次上岗就抓到"树里带着一行没还原的负控注入"**——`nav/ShellDecision.kt:36` 的 `deepLinkTarget` 缺 `role != Role.DRIVER &&` 守卫，当场按簿内 from 串还原（脏 4,374 B → 4,397 B）、单本复跑 rc=0 / ✔=7、全量 **356 例 0 失败**；⚠ **归因不完整这条仍是偏差**（无 git、`shell_routing_test_negative_controls.run.log` 已被后一次运行覆盖 ⇒ 只能确定"是注入没还原"，**不能**确定哪一本哪一分钟下的）；由此升为常驻规矩：**"树干净"只能由跑用例证明**——`cmp 与备份一致` 只证"与本书开头那份备份一致"（备份取自开始时的树），15 本 `check_*_probes.sh` 全 rc=0 只证结构锚点在位（本轮它们在那棵脏树上**同样全绿**）——见 §八 ㉔ ⑦ / **D-041** | D-040、判据 §八 ㉔、`tools/negctl_lock.sh` + `tools/after_negctl_run_full_tests.sh` |
| ⚠ **`safeApiCall` 只落了 5 个点，而 ⑧ 自己新登记的那句假话仍在树里**（F-2 第 8 步 / V21-07 · V21-30）：38 个 Repo 读接口今日只有 **5** 个走 `safeApiCall`（SA-c 实测使用点 **6** = Repo 5 + 页面 1），其余仍走 `attempt { … }` / `runCatching`（`runCatching` 全 main **生效行 34** 就是 ⑨ 的基数） | **已落地并登记为"按指令给的规模上限做的"**（2026-10-01 / D-042）：指令明写"不要为了一个 helper 制造 38 个编译问题"，所以这一步选的是**真实修复 2 处**（`linePolyline` 不再把失败洗成空列表 · `logout` 的撤销会话失败分类可见，**清本地仍无条件先做**）+ **形状归一 3 处**（天气预报段、`unreadCount`、`driverUnreadCount`，`longOf` 本来就不抛 ⇒ 对外行为一字未变，**不许写成"修了三个缺陷"**）。⚠ **两处仍不是"已通过"**：**①'** helper **休眠**这一族只治好了被钉住的那半边 —— "定义在、生产调用点 **0**"（⑧ 之前的真实状态）现在由 SA-c 钉成 **≥6** 且五个点逐一点名 `1/1/1/1/1`，但**其余 33 个读接口的失败语义这一轮一个都没看**；**②'** 本轮**新登记、一字未动**的缺陷：线路页首屏 `load()` 自动选中线路却**不拉折线** ⇒ `polylineSubtitle()` 那句"后端未回传该线路折线"在**问过之前**就说了出口（与本轮修的那句**同族**：把"没问"说成"没有"）。SA-e 的两条（`E_SUB=1` 与 `E_OLD_SUB=0`）**都允许它存在**，动它=扩大战线；修法要么首屏跟着选中拉一次，要么副标题再分一个"还没问"态。设备侧那 12 例只有 JVM 证据、`BusScreen` 的两句文案在 JVM 里渲染不出来 ⇒ 仍 **`NOT VERIFIED ON DEVICE`** | D-042、判据 §八 ㉕、`tools/check_safe_call_probes.sh` + `tools/safe_call_negative_controls.sh` |
| ⚠ **改手写台账这件事本身有两条"静默破坏"，本轮（F-2 ⑧ 台账收口 03:37–04:04）两条都自己踩中并被当场抓出**：**① 追加即拆行** —— 往状态板"真实性关键字（宽口径）"那一行末尾续写，把一行变成了 **6 个物理行**，整张表在那一行之后**静默断掉**（渲染不报错、下一轮抄表的人看不见），`python tools/check_ledger_tables.py` 报 `1358 cells 3 vs header 4` ⇒ 这台自检此前只在"证明脚本没坏"时被跑，这次是它第一次**判红我本人**；**② 文本模式换行翻转** —— 并回那 6 行时用手写 python 以文本模式写回，Windows 默认换行把整本 `AI_ITERATION_STATE.md` 从 **LF 翻成 CRLF**（**440,679 → 443,725 B，每行多一个 `\r`**；`grep`/`wc -l` 都看不出来，`diff` 只表现为"整本重写"）⇒ 已按事前备份 `/tmp/AI_ITERATION_STATE.before_joinrow_035601.md` 以**二进制**还原为 LF，并 `diff` 证明与备份的唯一差异就是那一行的并回 | **规矩（本轮起，改台账一律照做）**：优先用编辑工具改；非要写脚本改，就**显式指定换行或以二进制写**，改完立刻比 **字节数 + CRLF 计数**（不是行数）并跑一次表格自检；改前先落 `/tmp` 备份，因为**本工程无 git**，"改了什么、能不能退回"只有备份 + `cmp` 能证明。这与"禁止 `'w'` 先截断再报错"是同族缺陷：**文本模式的隐式转换和 `'w'` 的隐式截断一样，都是先破坏再报告**。读数影响：`app/src` 这一轮 **0 个文件**被碰（`find app/src -newermt "2026-10-01 03:37"` → **0**），所以"树干净"的证据仍是 03:11:16 那遍 **38 类 / 368 例 / 0 失败** 的**旧读数**，本轮没有复跑 gradle | D-042 第 5 节末两条、判据 §八 ㉕、状态板 DONE「F-2 ⑧」的"台账收口复跑"条 |
| ⚠ **F-2 ⑨（`runCatching` 取消审计，2026-10-01 09:44）留下三条必须传给下一轮的偏差**：**①** 我在审计文档里把"跨簿影响"写成了**推理**（"SA 锚点指 `Repo.kt:312`，与本轮 `:359` 不是同一行 ⇒ 无需改；SA 簿复跑确认 rc=0"），实跑给的是 **rc=1** —— 破口在 SA-d 数整本文件的 `D_ATTEMPT_REPO`（⑧ 的不变量 0），不在行号上；那句 "rc=0" 当时**根本没跑**。**②** 那 30 点"保持"**不等于**"30 点无缺陷"，它靠的是**结构免疫**（包围边界非 suspend），而这条论证的两条腿 `runBlocking = 0` / `suspendCancellableCoroutine = 0` 只要有一边非 0 就**整表作废**（RC-d 钉着，红了要重做第三节，不是"一个计数超了"）。**③** 4 处迁移只有 JVM 静态判据 + `compileDebugKotlin`，取消行为只在 `ApiCancellationTest` 那一层有 JVM 证据 ⇒ 页面侧（深链跳转 / TTS 设语种）**`NOT VERIFIED ON DEVICE`**；release 仍 BLOCKED、git 仍 ENVIRONMENT_BLOCKED | **规矩（本轮起）**：改过源码文本，就把 `tools/check_*.sh` **每一本真跑一遍**并把读数抄进文档，不许只对"我以为相关的那几行"下结论；把"0"改成"N"的豁免必须**逐处点名** + 配一条"点名锚点被动"的负控（SA 负控第 7b 条是这一族的样板）| D-043、判据 §八 ㉖、`docs/android/RUNCATCHING_AUDIT.md` 第五节、`tools/check_runcatching_probes.sh`（RC-a…RC-e）+ `tools/runcatching_negative_controls.sh` |

### 指令 v2.0 对表新增（2026-09-29 扫描实测，全部在册）

| 偏差 | 状态（实测依据） | 台账 |
|---|---|---|
| ~~司机动作毫无幂等保护~~ **V-1 已修（2026-09-29）** | 后端**确实没有**可复用的键：`AppDriverController` 的 `@Idempotent` 命中 **0**（全模块只在两个 `create` 上），键由服务端自算 `md5(methodName+argsStr+userId+userType)`，11 个司机 ReqVO 无任何唯一键字段 ⇒ 按"后端没有的安卓可以自己写、但要符合架构要求"的授权，闸门落在**进程单例 `Repo`**（新 `ActionGate`：`tryLock` **丢弃**而非 `withLock` 排队；成功保留 10 s = 后端 `@Idempotent(timeout=10)` 同口径；异常即释放 = `deleteKeyWhenException=true`）。10 条一次性写路径收进单一出口 `gatedAction(key)`，键按后端实体（`leg/$action:$legId` 让同一段 start/complete 互不干扰）；`Ack.notSent` + 四个回执点说"本次请求未发给后端"（不说成功也不说失败；⚠ **V-3 加异常上报页后是五个，判据 `I-e` 同轮 4→5**，本行写"四个"是 V-1 当时的事实）；`reportLocation`/`uploadPhoto` **有意豁免**并由**反向判据 I-c** 钉住（吞周期点＝偷点）。§52 的动机也已对着后端源码核过：`depart` 会把班次倒回在途、`pickup-confirm` 的 3→3 让已装件数再 +1、`leg/complete` 与交接 `arrive-dest` 是 skip-不抛 而 `orderEventService.record` 无条件 insert（**非交接 `arrive-dest` 是例外**，第二次抛 `LEG_TRANSITION_ILLEGAL`）| 审计 §73、D-027、DONE 的 V-1 段 |
| **`code=900`（REPEATED_REQUESTS）在用户端被当普通错误显示** —— **F-2 ⑥ 修了"透出"这一半，"回读状态"那一半仍未修** | **后端契约（本轮重新读源码，不是沿用）**：`@Idempotent` 全后端**只有两处** —— `AppProductOrderController.java:38` 与 `AppSendController.java:120`，都是 `timeout = 10, TimeUnit.SECONDS, keyResolver = UserIdempotentKeyResolver.class, message = "订单正在创建中，请勿重复提交"`；命中回 **HTTP 200 + `code=900`**（`IdempotentAspect.java:52` → `GlobalErrorCodeConstants.java:36`），语义是"你的重复已被挡住、正在处理中"。**已修的一半（有实测）**：① 用户侧两个 `create` 现在各走**本域自己的闸门**（新行见下），连点不再出网；② 后端真回 900 时**原样透出那句话**，不塌成"加载失败"那种通用文案（链路实测：`Api` 把 900 抛成 `ApiException(code=900)` ⇒ `Repo.kt:59 errorTextOf` 的 `is ApiException -> e.message` ⇒ `ScreenViewModel.kt:46` 直接把它写进 `error`），钉在 `UserCreateIdempotencyTest` 的 `后端回 900 时原样透出那句话且不再由本机代拦`；③ **本机不替后端记住它的键**：900 之后闸已放（`deleteKeyWhenException=true` 同口径），第二次照样出网、由后端再判一次（同一条用例实测 `requestCount == 2`）。**仍未修的一半（在册）**：900 之后页面**没有"去回读状态"的专属分支** —— 今天只是把那句话说给用户，不主动拉一次订单列表确认那张单最终有没有成。⚠ 动它之前先核 `Api.rawExecute` 的信封读法与后端键粒度（`md5(methodName+argsStr+userId+userType)`，参数变了就不是同一个键），**不许**为了让文案好看把 900 静默吞成成功 | 审计 §73 尾部、V-1 尾巴、**F-2 ⑥ / D-038** |
| **用户端 `create` 毫无幂等保护**（指令 v2.1 第 6 步点名）—— **F-2 ⑥ 已修（2026-09-30）** | 后端在 `AppProductOrderController.java:38` / `AppSendController.java:120` **确实有** `@Idempotent`，所以这一步**不是补后端的缺，而是别让用户把两次点击都打到后端**。按硬规则 1 落成**两把闸**：`object Repo` 上 `ActionGate` 类型字段实测**恰好 2**（`actionGate` `Repo.kt:1160` 司机域 / `userCreateGate` `:453` 用户域，判据 `U-c` 数实例数、`U-c2`/`U-c3` 两个方向钉"不许互相顶名"）；两个下单出口 `createProductOrder`（`:506/:516`）/ `createSendOrder`（`:558/:559`）都经 `gatedUserCreate`（`:488-492`），键 = `create:` + SHA-256(端点 + `Prefs.mobile` + 整份报文按 key 排序)（`:464-476`），**只存摘要不存明文**（键表是进程级、活得比页面久，写进姓名/手机号/地址就是个人信息留存）。⚠ **诚实边界（最要紧的一条，别抄错）**：本机键与后端键**同维度而不同值** —— 本机拿不到服务端那个 `userId/userType` 的表示，硬凑一个就是假造 API，所以**本机放行 ≠ 后端会放行**，这条由 900 那两条用例钉住，不靠注释。⚠ `AppAddressController.java:32` / `AppFeedbackController.java:28` 两处 create 后端**零** `@Idempotent` ⇒ **有意不加闸**（单方面拦＝替后端发明规则），登记为判据 `U-j`（只登记、不判红、也不写负控） | 判据 §八 ⑭、`U-*` 行、DONE「F-2 ⑥」、D-038 |
| 指令 §3/§4 要的 `AppRole`（含 DISPATCHER/ADMIN 预留）与 `SessionManager`/`SessionState` **不存在**；今日角色是 `enum class Role { USER, DRIVER }` + `Prefs.role` | **不照字面做，按契约做**（实测依据三条）：① 后端 `AppAuthLoginRespVO` 只有 `userId/accessToken/refreshToken/expiresTime/openid` **五个键，没有任何 role 键**；② `member/controller` 全量 grep `userRole\|private String role\|roleName` 命中 0；③ `Repo.login` 的报文只有 `mobile/password`（`Repo.smsLogin` 同）——**角色既不上送也不回传**。⇒ 唯一后端可证的司机身份是 `AppDriverProfileRespVO.driverId`，而工程今天正是以它为准（`bindDriver()` 不授予 driverId 就进不了司机域）。硬造 `roles: Set<AppRole>` 就是指令 §123 禁止的"替后端编字段"。`DISPATCHER/ADMIN` 无契约，**不做页面**（指令 §3 同条要求） | 审计 §70、D-024、D-018 |
| 登录页有"选择身份"两键选择器，指令 §5 明令"不要问用户选角色" | **保留并如实记代价**：后端不回 role，选择器是**唯一**的入口意图来源，且它不是权威 —— 真正的门禁是 `Repo.bindDriver()`。删掉它需要后端先下发角色（缺键 → 见上一行）。这是"契约不足"不是"实现偷懒"，指令 §124 的处置是登记 `MISSING_BACKEND_*` 类事实而非偷偷 mock | 审计 §70、D-024 |
| ~~Phase 1 验收"司机绝不能看到普通用户首页"：代码路径成立，**用例数 0**~~ **V-2 已修（2026-09-29，用例数 0 → 9）** | 原判断照旧成立（`grep -rn "USER_SHELL\|DRIVER_SHELL\|startDestination" app/src/test` 当时命中 0；现状靠三条：`start` 按角色选壳、`Routes.DRIVER_ONLY` + `deepLinkAllowed()`、深链两支都不允许直接跳壳）。处置：**没有 Robolectric 就渲染不出 `NavHost`** ⇒ 把两条决策提成 `nav/ShellDecision.kt` 的两个 `internal` 纯函数（`startDestinationFor` / `deepLinkTarget`），`BusPostRoot` 只调用它们；`ShellRoutingContractTest` **9 条**钉行为，`tools/check_shell_routing_probes.sh` **13 个判定**钉"决策真的接到 UI 上、且 UI 里没有第二份答案"（SR-a…SR-h；2026-09-29 收工复跑实测 `9 / 1·0 / 1·0 / 2·0 / 0 / 0 / 1 / 1·0 / 1`，`bash tools/shell_routing_negative_controls.sh` 12 ✔ / 0 ✘，用例级 M-1…M-6 六条各自把 gradle 跑红）。**没有为变绿放宽闸门，也没有动 Compose 调用点的线程语义。** ⚠ 诚实边界：**导航图本身仍未被测试** —— `startDestination` 的实际渲染、`popUpTo` 行为要 Robolectric 或真机，本轮钉的是决策函数 + 结构接点 | 审计 §74、D-028、队列 V-2 |
| ~~**V-2 残留（登记为 V-2b）：进壳的两个输入今日不同源** —— 落点只读 `Prefs.role`（本地），而"是不是司机"的**权威**在后端画像里~~ **V-2b 已修（2026-09-30）** | **修的是"后端说你不是司机"这一半**：`Repo.driverProfile()` 出口收成三态（`serverSaysDriver(driverId, failed, demo)` 返回 `Boolean?`，**失败与演示都是未知、不是否定**），进壳侧 `WorkbenchScreen.syncFromServer()` 用 `reconcileRole` 判降级，**只有明确否定**才把 `Role.DRIVER` 降回 `Role.USER`，并且**单向**（永远不会把用户升成司机 —— 那是指令 §5 禁止的"替后端编角色"）。**修前修后实测**：撤档后 `Prefs.driverId` 61→0、请求 5 条→**1 条**（`driverShifts/driverPickups/driverUnread/通知` 四条在早退之前不再发出）、角色 DRIVER→USER、两套判据收成一条。返修根因：后端否定是 `{"code":0,"data":null}` 而 `Api.kt:314-315` 读成**空 JSONObject**，旧代码的 `o.length() > 0` 让撤档不可达。**证据**：`RoleReconcileTest` 5 条 + `WorkbenchRoleReconcileContractTest` 4 条 + 判据 §八 ⑰（RR-a…RR-e）+ 结构负控 13 ✔ + 用例级负控 RRt-1…RRt-6 全 ✔。⚠ **三条残留（不得当作已解决）**：① **真机未验证** —— 深链 `buspost://app/<tail>` 点开落哪个壳、降级时前台通知是否真的停，都要 `DEVICE_STATUS: NOT VERIFIED ON DEVICE`（无 Robolectric、无设备）；② **降级点在"进壳之后"不是"落壳之前"** —— 两个纯函数各只有 1 个调用点（RR-c 实测），`startDestinationFor` 用的仍是本地那一份，冷启动那一帧还是可能先进司机壳再被弹回；要治得把画像拉取提到 `BusPostRoot` 之前，那是**第二次机制改动**，本轮不做、也不谎称做到；③ **`release` 产物从未产出** —— `assembleRelease` 在配置期即失败（`release 包必须使用 HTTPS（当前 http://1.15.29.107/api）`），按"不要修改代码来绕过"原样记 `BLOCKED`，解锁顺序 = 先有 https baseUrl 再补 keystore | 审计 §76、D-030、判据 §八 ⑰、队列 V-2b |
| **V-3 尾巴（登记在册）：§54 的七个分类是客户端话术，与后端没有任何码表关系**（`AppDriverLegActionReqVO` 实测只有 `driverId/legId/photoUrl/remark` 四键，**无异常类型枚举**） | **Android 侧已实现**（2026-09-29，V-3）：分类只拼进 `remark` 一句自由文本（`道路阻断：塌方，中断约 3 小时`，上限 200 = 后端两张 `varchar(500)` 减掉服务层自加前缀"第 N 段异常：" 的余量），**报文里没有自造 `type`**（契约测试 `keysOf(body)` 钉死三键）。后端服务层 `reportLegException`（`DriverAppServiceImpl:1503-1519`）本来就把"段转异常 + 订单转异常 + 记事件 + 告警后台 + 通知下单人"做完，本轮只补 `AppDriverController.java:226-231` 六行 HTTP 入口。**三条未修**：① 跨仓那六行**无 git 可提交**（工作区非仓库）且**未部署到 48080** ⇒ 端到端未验证；② `ActionGate` 仍是进程内（V-1 已登记，本条继承）；③ 若后端将来真加异常类型枚举，这层必须**改成读后端码**而不是继续拼字符串（已写进 `DriverExceptionReport.kt` 的 KDoc） | 审计 §75、D-029、判据 §八 ⑯ |
| 指令 §42 的离线缓存（Room）未落，路线缓存只活在进程里 | **（结构修复说明，2026-09-30）**：本行左格在 §六 ④ 那类结构自检中发现已丢失（上一版残留正文以"："开头、整行只有 2 个竖线字符（Markdown 渲染不出来）），按残留正文补回名称，**正文其余一字未改**）：`core/map/RouteRepository` 是**内存** `LinkedHashMap`（`MAX_ENTRIES = 12`，淘汰最旧），满足 §43"只缓存真实路线"（未过校验不入缓存，且来源突变不可信时**主动删键**），不满足"杀进程还在"。`Prefs.kt` 头部明写"测试项目不引 Room，减少构建面"—— 这是**既有取舍**，不是遗漏；要按 §42 落 Room 需单列一轮（构建面 + 迁移 + 测试基座一起动） | 审计 §70、D-024 |
| ~~OSM 数据署名（ODbL 1.0）~~ **F-2 ⑭ 已结清（本行 ⑰⑱ 复核时才发现自己过期了）** | ~~未结清：App 内 `grep -rni "openstreetmap" app/src/main` 命中 **0**。~~ 本轮实测：`openstreetmap` **3** 处、`ODbL` **3** 处，落点 `core/about/OpenSourceCredits.kt`；`check_oss_credits_probes.sh` rc=0（`OS-d2 0/0/28`、`OS-e …署名串/ODbL串 = 2/2`、`OS-f 用例 9/0/0`）。⚠ **这一行的教训是"台账自己会过期"**：⑭ 那轮改了源码却没回改本行，于是"未结清"这句在 12:00 之后就是假的 —— 判据是绿的、台账是红的，两边不一致时必须以实测为准改台账（D-044 第 6 节） | `THIRD_PARTY_NOTICES.md` §4、D-040、`docs/android/THIRD_PARTY_RUNTIME_MATRIX.md` |
| ⚠ **`eta_negative_controls.sh` 的两条判据现在没有判别力**（F-2 ⑰⑱ 实测，登记未修 = `D-⑰⑱-1`） | `/tmp/eta_negative_controls.run.log`（12:21:19）第 9、43 行：`✘ T-e 又拿 0 当『还没到时间』 → 负控制失效（exit 0），这条判据等于没判据`、`✘ T-w2 提醒又读回没有 ETA 键的 /send/arrangements（§68 复发） → 负控制失效`。 ⇒ 把这两处按点名的破口形状改坏，`check_eta_probes.sh` **仍 exit 0**。⑪/⑫/⑬ 那三轮它们是真响过的 ⇒ 属"**绿灯会自己烂**"（锚点被后续改动漂掉），不是新判据漏网。修法要重锚 T-e/T-w2 字面量并同轮复跑 ETA 全族负控，已排进 F-3（与 §五 那 32 处同批，因为 `distanceMeters`/`etaSeconds` 就在其中 32 处里） | D-044 第 5 节、`docs/android/DEFAULT_VALUE_AUDIT.md` §四 |
| ⚠ **负控批次"exit 0"曾经不代表全绿**（F-2 ⑰⑱ 已补机制） | 本轮实测：批内两本 `rc=2`（`notify_result`、`oss_credits`，预检"真身没有 test-results XML ⇒ 读数没有来源"）**加** `eta` 一本 `✘=2`，而 `run_all_negative_controls_batch.sh` 整批仍 exit 0 —— 它只逐本打印 `rc=`，**没有聚合**。同轮修补：累计 `rc_bad`/`cross_bad`，任一非空打印 `[聚合] …` 后 **exit 1**，`共 $total 本` 那行留在末尾（`after_negctl_run_full_tests.sh` 的放行字符串一字未动）。⚠ **口径**：`rc=2` 不是缺陷而是簿自己的预检，处置是"跑完全量用例后单独复跑这两本"（复跑 `rc=0 / ✘=0 / RESULT=PASS`） | D-044 第 4 节、判据 §八 ㉓ |
| ⚠ **"线路页导航入口只在 DEBUG 可达"这句台账描述是错位描述**（F-2 ⑰⑱ 实测） | `grep -rn BuildConfig.DEBUG app/src/main` **10** 处，导航相关 **7** 处全在司机侧（`BusPostRoot.kt:138`、`RoutesScreen.kt:206,348`、`WorkbenchScreen.kt:627,684,963,1069`），而**用户线路页 `ui/user/BusScreen.kt` 命中 0** ⇒ 要改的是这句话的落点描述（司机线路页/工作台），不是"给用户线路页加个导航按钮"。⚠ 这 7 处里 `BusPostRoot:138` + 两个"旧导航页（调试）"按钮是 **D-003 假导航红线要求的**，不许当业务开关拆掉；F-3 ④⑫ 要新增的是**按 `NavigationEngine.readiness` 决定可点/不可点**的真导航入口（含 `NO_ROUTE` 态） | D-044 第 8 节、本文件上方"正式导航面换了位置"那一行 |
| ⚠ **`LoginViewModel` 成功回调时 `busy` 仍为 true**（F-2 ⑰⑱ 已修，登记根因） | 全量用例实测：`LoginViewModelTest.kt:84 assertFalse(vm.busy)` 红（12:49，`failures=1/391`）。根因在生产码 `submit`：`onDone(chosen)`（`:138`）先跑、`busy = false` 在 `finally`（`:149`）才落 ⇒ 回调被唤醒那一刻状态还没进终态。单跑该类 3 次全绿、01:21/01:25 两轮全量也绿 ⇒ **调度敏感不等于可以当 flake 划掉**；处置是改生产码顺序（`onDone` 前显式 `busy = false`，`finally` 仍管失败支），**没有**弱化断言。改后全量 **40 类/391 例/0 失败** | D-044 第 3 节 |
| ⚠ **APK 字节数不能当变更证据**（F-2 ⑰⑱ 实测坐实） | 10:50 `114,447,105` → 12:05 `114,748,896` → 13:02 `113,879,444`，而三次之间源码只差 2 行；同输入重打两次（13:02 / 13:04）**字节数与 sha256 全等**（`43a4389b…`，树指纹 `6e263735…`），`OS-e` 打包类 **29,816** 未变 ⇒ 确定性成立、摆动来自构建产物面而非源码。⚠ 12:05 那份已被覆盖、无法回比 ⇒ 历史"+301,791 B"归因**仍未闭环**；新规：**今后每条 APK 读数必须连 sha256 + `app/src` 树指纹一起抄**。**这条规矩在 F-3 ⑩⑪⑮ 轮暴露出自己的洞**：那串 `6e263735…`（146 个源文件）的**算法从来没记在案**，所以本轮的 `845a088f…`（153 个文件）**不能与它比对** —— 规矩升级为"**指纹读数必须连命令原文一起落档**"，命令已逐字写在 `DECISION_LOG.md` **D-045 第 2 节** | D-044 第 2 节、D-045 第 2 节、`docs/android/THIRD_PARTY_RUNTIME_MATRIX.md` 第二节 |
| ⚠ **用户实时物流页那三格"后端未下发"，实际是客户端一行都没读**（F-3 ⑩⑪⑮ 实测根因） | **已修**（2026-10-01 / D-045）：`Repo.kt:traceOf` 补 `carrier*`（寄货）、`current*`（商城）、`carrierTaskStatus/carrierArrivedTime/carrierArrived` 七处读取（`:725/:726/:727/:739/:740/:741/:746`）；车辆位置只在 `geoOf`（`:1174`）的 `lat!=0 && lng!=0` 通过时成立，否则 `TraceView.kt:122-130` 直说"暂无实时位置"，**不用 `0,0`/固定点/随机点顶**；新鲜度只从 `LocationSource.knownLabel` 一处拼码（`REAL_STALE` 不升格成"实时"）；`lastReportTime` 走新 helper `reportTimeOf`（`Models.kt:130`）**不回落 `createTime`** —— 旧写法拿订单创建时间当车辆上报时刻＝造一个时刻；删掉唯一自造别名 `positionSource`（后端与 `DemoData` 两侧 0 生产者），**没删任何后端字段**。**两条残留如实记**：D-⑩⑪⑮-1（演示面没种 `carrierTaskStatus`/`carrierArrivedTime` ⇒ 演示包念"后端未下发"）、D-⑩⑪⑮-2（寄货 `/send/track` 无上报时间键，`PRODUCER_NOT_FOUND`） | 审计 §八（`CONTRACT_EVIDENCE.md`）+ 八.1、D-045、`UserTrackingContractTest` 12 例 |
| ⚠ **新契约面只有用例在钉、没有 `tools/check_*.sh` 锚点簿**（F-3 ⑩⑪⑮ 登记，未修 = `D-⑩⑪⑮-4`） | `check_location_probes.sh` 的 L-a…L-h 钉的是坐标换算与上报**报文**，覆盖不到**读取侧**（`carrier*`/`current*`/`reportTimeOf` 不回落/`positionSource` 不认这四条现在只由 12 例用例守着）。扩 L-i…L-k 必须**同轮**在 `navigation_negative_controls.sh`（L 家族的负控在那本里）加破口，否则又造出一条"只能证明自己的判据"。**不做的事**：不为了让台账写"已判据化"而补一条没有判别力的 grep | D-045 第 6 节、`CONTRACT_EVIDENCE.md` 八.1 |
| ⚠ **本轮插行让 §七 的 67 条 App 侧锚点只剩"范围合法"**（F-3 ⑩⑪⑮ 登记，未修 = `D-⑩⑪⑮-3`） | `Models.kt` 44 + `Repo.kt` 23 条（`tools/check_doc_anchors.py` 计数，`out_of_range=0`）里**确证已漂**的是 `Models.kt:742/:753/:757` → 现 `:761/:772/:776`；逐条**内容**重锚不能靠 grep 自证（helper 型锚点的字段名本来不出现在定义行，两种指纹都会假报），也不能靠猜填数字 ⇒ 排进 F-3 ② 与那 32 处 C 站点同批。**同轮已重锚的另一本**：`DEFAULT_VALUE_AUDIT.md` §四 的 18 条锚点逐条对齐源码文本（`out_of_range=0 / not_found=0 / miss=0`），只有"商品/车辆/清单"那一行按它自己的结论（待逐端点核对）标了旧读数口径 | D-045 第 6 节、`tools/check_doc_anchors.py` 读数 |
| ⚠ **`sourceOf` 这个名字已经不存在**（F-3 ⑩⑪⑮ 的标识更正，防止下一轮按旧名去找） | 私有 `sourceOf` 并入 `enum class LocationSource`（`Models.kt:535`）：唯一解析点 `LocationSource.of`（`:554`）、唯一拼码点 `knownLabel`（`:543-552`），`BusVehicle.sourceText`（`:632`）改为**委托**同一映射（委托后 `BusEtaContractTest` 13 例仍 0 红）。所以 `AI_ITERATION_STATE.md:2523`、`:2535` 与工程根的 `ANDROID_PARITY_AUDIT_V2.md:841`（**这本台账不在 `docs/android/` 下**）里写的 `sourceOf` 是**历史名**，历史行**不回改**，抄当前实现要按新名 | D-045 第 3 节 |
| ⚠ **"命令行 adb 可用"不是环境事实**（F-3 ⑩⑪⑮ 实测） | 本轮直接 `adb devices` → `adb: command not found`（PATH 里没有），而 `bash tools/verify_device.sh --no-build` 仍能出读数（脚本自己解析 SDK 路径）⇒ 实测 `List of devices attached` **空** → `[STOP] DEVICE_UNAVAILABLE`，新证据 `docs/device-evidence/20261001-145515/`。**下一轮别写"adb 在 PATH 里"这类前置条件**，要跑设备就调脚本 | D-045 第 2 节、本文件第六节 |
| 指令 §121 要求每轮 `git status` / `git diff --stat` / checkpoint commit | **ENVIRONMENT_BLOCKED**：`git rev-parse --is-inside-work-tree` → `fatal: not a git repository`。工作区不是 git 仓库（父目录留有 `_commit-backup-20260912-*`、`_git-rescue-20260912-*`，说明版本库早先已脱离）。**不做的事**：不为了"有 checkpoint"而 `git init` 出一个新历史冒充连续，不把 commit 写进交付口径 | 本文件第六节、审计 §70 |
| 指令 §104 要求 `docs/android/` 七份文档 | **以映射代替复制**：七份内容今日分别落在 `CONTEXT_LOCK.md` / `AI_ITERATION_STATE.md` / `DECISION_LOG.md` / `ANDROID_PARITY_AUDIT_V2.md` / **跨仓** `../cargo-post-platform/docs/REAL_ROAD_ROUTING_ARCHITECTURE.md`（本仓 `docs/` 下没有此文件）/ `THIRD_PARTY_NOTICES.md`。同一条事实抄两份必然漂移（本工程已经为"两套状态表"付过代价，见 D-021），所以建 `docs/android/README.md` 做**指路牌**而不建七份副本；指令写的是"最终增加"，真要拆分需一轮专门收口 | D-024 |
| 指令 §107 建议的 `D-020…D-027` 编号**与本工程既有编号冲突**（D-020/021/022/023 已用于 2026-09-29 的四轮，且历史上存在**两个 D-009**） | **不重排历史编号**（重排会让审计里所有"D-0xx"引用集体失效）。改为在 `DECISION_LOG.md` 末尾放一张**指令条目 → 实际决策**映射表；引用旧号时必须带主题，因为 `D-009` 单独出现是**有歧义**的（这是台账自身的缺陷，如实记在此） | D-024、`DECISION_LOG.md` 映射表 |

## 十、指令原文在哪（别再凭记忆引用段号）

- 载体：桌面 `新建 DOCX 文档 (3).docx`（Autonomous Engineering Command **v2.0**，段号 **0–130**，是先前 79 段版的超集）。
- 取法固定为"解 zip → 抽 `w:t` → 落 UTF-8 文件 → 用读文件的方式看"，脚本已沉淀：**`tools/extract_docx.py`**。

```bash
python tools/extract_docx.py "C:/Users/袁/Desktop/新建 DOCX 文档 (3).docx" _docx_dump.txt
# 实测输出：paragraphs=2471 chars=32314，段号覆盖 0–130（131 个小节标题）
```

- ⚠ **口径提醒**：本机控制台是 GBK，直接打印正文必乱码 —— 只能落文件再读。
  另外 `grep -c "^[0-9][0-9]*\. "` 会数出 **141**，比 131 多，因为文档内部的编号列表（"1. 用户端…"那种）也匹配这个式子；
  要数小节标题得看**最大编号是否为 130**，不要只看命中条数。
- 抽取物是**临时文件**（不属于工程，用完即删，别让它进仓库 —— 本轮已删掉 `测试/_docx_dump.txt`）。
- ⚠ **段号必须现查现引**：D-024 记了两次现场事故 —— 我按记忆写"§125 要求 THIRD_PARTY_NOTICES"，
  原文里 **§100** 才是 Third-party Notice、§125 是"司机安全降级"；另一次我写下"指令 §166–167 禁止未知→回退"，
  而**该文最大段号是 130**，这条引用根本不存在。
  引用指令段号的正确动作是从抽取结果里 `grep` 出来照着抄，不是回想。
