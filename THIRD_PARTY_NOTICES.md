# THIRD_PARTY_NOTICES — 第三方开源与专有依赖声明

对应指令 v2.0 §100–§103 与 `DECISION_LOG.md` D-009（"第三方开源复用需登记 NOTICE"）。
本文件是**许可证台账**：谁在 APK 里、什么许可证、有没有复制源码、署名欠不欠。

**当前总结论（实测）**：本工程**未复制、未改写、未移植任何第三方源码文件**。
全部第三方能力通过 Maven 依赖以二进制/源码包形式引入，清单取自 `app/build.gradle.kts` 的 `dependencies` 块。

- 登记日期：2026-09-29
- 依赖清单来源：`app/build.gradle.kts`（声明版本，非猜测）
- 复核方式：`./gradlew :app:dependencies` 以实际解析结果为准；升级版本时**必须回到本文件改行**

---

## 1. 随 APK 分发的运行时依赖

| Artifact | 版本 | 许可证 | 上游 | 用途 | 源码复制 |
|---|---|---|---|---|---|
| `androidx.core:core-ktx` | 1.15.0 | Apache License 2.0 | https://developer.android.com/jetpack/androidx/releases/core | AndroidX 基础扩展 | 否 |
| `androidx.appcompat:appcompat` | 1.7.0 | Apache License 2.0 | https://developer.android.com/jetpack/androidx/releases/appcompat | Activity/主题兼容 | 否 |
| `androidx.activity:activity-compose` | 1.9.3 | Apache License 2.0 | https://developer.android.com/jetpack/androidx/releases/activity | Compose 宿主 | 否 |
| `androidx.compose.*`（BOM 管理：ui / ui-graphics / ui-tooling-preview / foundation / material3 / material-icons-extended） | 见 BOM | Apache License 2.0 | https://developer.android.com/jetpack/androidx/releases/compose | UI 框架 | 否 |
| `androidx.lifecycle:*`（runtime-ktx / runtime-compose / viewmodel-compose / service） | 2.8.7 | Apache License 2.0 | https://developer.android.com/jetpack/androidx/releases/lifecycle | 生命周期、前台服务基类 | 否 |
| `androidx.navigation:navigation-compose` | 2.8.5 | Apache License 2.0 | https://developer.android.com/jetpack/androidx/releases/navigation | 路由（`Routes.kt` / `BusPostRoot.kt`） | 否 |
| `androidx.work:work-runtime-ktx` | 2.9.1 | Apache License 2.0 | https://developer.android.com/jetpack/androidx/releases/work | 后台轮询（`NotifyWorker.kt`） | 否 |
| `org.jetbrains.kotlin:kotlin-stdlib` | 见 Gradle 插件 | Apache License 2.0 | https://github.com/JetBrains/kotlin | 语言运行时 | 否 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.9.0 | Apache License 2.0 | https://github.com/Kotlin/kotlinx.coroutines | 协程 | 否 |
| `com.squareup.okhttp3:okhttp` | 4.12.0 | Apache License 2.0 | https://square.github.io/okhttp/ | 网络层（`Api.kt`） | 否 |
| `io.coil-kt:coil-compose` | 2.7.0 | Apache License 2.0 | https://coil-kt.github.io/Coil/ | 图片加载 | 否 |
| `com.google.zxing:core` | 3.5.3 | Apache License 2.0 | https://github.com/zxing/zxing | 二维码编解码（`core/code/`） | 否 |
| `com.journeyapps:zxing-android-embedded` | 4.3.0 | Apache License 2.0 | https://journeyapps.github.io/zxing-android-embedded/ | 扫码取景（`Scanner.kt`） | 否 |
| `com.squareup.okio:okio` | **3.6.0 → 3.9.0**（OkHttp 带的传递依赖，实测解析结果 `f2_10_deps.txt:771`） | Apache License 2.0 | https://github.com/square/okio | I/O 原语（OkHttp 内部） | 否 |
| `org.jetbrains.kotlinx:kotlinx-serialization-core` | 1.6.3（传递依赖，同上实测） | Apache License 2.0 | https://github.com/Kotlin/kotlinx.serialization | 序列化运行时核心 | 否 |

⚠ **最后两行是 F-2 ⑮（2026-10-01）补登的**：`app/build.gradle.kts` 里没有这两条坐标（声明条数实测仍 **20**），
它们只在 `debugRuntimeClasspath` 的解析结果里出现，而 `tools/apk_oss_census.py` 对该 APK `class_defs` 实测
`okio/` 打包类 **114**、`kotlinx/serialization/` 打包类 **255** —— "随包分发"这件事由包里的字节码说话，
不由 `build.gradle.kts` 有没有写说话。旧版上表按"声明清单"逐条抄，因此漏了这两条；
逐行证据见 `docs/android/THIRD_PARTY_RUNTIME_MATRIX.md` 第二节。

Apache-2.0 要求：保留 LICENSE 与 NOTICE，改动需标注。上表依赖均以官方发布的构件原样使用，
**未修改其任何源文件**，因此不产生衍生作品署名义务；构件自带的 `LICENSE`/`NOTICE` 由打包工具保留在构件内。

## 2. 仅测试期依赖（不进 APK）

| Artifact | 版本 | 许可证 | 用途 |
|---|---|---|---|
| `junit:junit` | 4.13.2 | Eclipse Public License 1.0 | 单元测试 |
| `com.squareup.okhttp3:mockwebserver` | 4.12.0 | Apache License 2.0 | 契约测试的假 HTTP 端点 |
| `org.json:json` | 20240303 | The JSON License（"AS IS"，无担保） | 让 `org.json` 在 JVM 单测里可运行 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test` | 1.9.0 | Apache License 2.0 | `runBlocking` / 调度器控制 |

`MockWebServer` 只用于测试断言请求契约，**不构成运行时兜底数据**；正式包不含测试源集。

## 3. 专有 SDK：高德地图（不是开源，不能与 MIT 混谈）

| Artifact | 实际解析到的版本 | 许可证（取自构件自带 POM） | 性质 |
|---|---|---|---|
| `com.amap.api:navi-3dmap` | `10.0.600_3dmap10.0.600` | **The AMap Software License, Version 1.0**（`http://www.amap.com/licenses/LICENSE-1.0.txt`） | **专有软件**，受《高德地图开放平台服务协议》约束，非 OSI 开源许可 |
| ~~`com.amap.api:3dmap` `10.0.600`~~ | 已被上一行**顶替**（2026-09-30 N-1-a），不再单独声明 | 同上（`3dmap-10.0.600.pom` 实测同一条许可证） | `navi-3dmap` 的 artifact 名里那个 `_3dmap10.0.600` 就是它内置的地图构件版本 |

要点：

1. §100 明确 "AMap 不等同于上述 OSS，按其 SDK 使用协议处理" —— 因此本文件把它单列，**不给它标 MIT/Apache**。
2. 隐私政策已按官方要求放置：`docs/privacy-policy-amap-sdk.md`。
3. ~~版本以**实际解析到的构件**为准。历史教训（`AI_ITERATION_STATE.md` BLOCKED 记录）：
   当前解析到的 `3dmap:10.0.600` 里**没有任何 `com/amap/api/navi/` 类**，所以导航 SDK 能力属于"未验证"，
   **禁止按博客或记忆猜 API**（指令 §22、规则 E）。~~
   **该条已于 2026-09-30 解除**：换成 `navi-3dmap` 后 javap 实测 `com/amap/api/navi/` 确有类，
   取证清单（含**没有公开 `isInited()`**、`PathPlanningStrategy.DRIVING_DEFAULT` 带 `Deprecated` 这两条
   "查无此 API / 弃用"结论）写在 `ANDROID_PARITY_AUDIT_V2.md` §77 一。
   **纪律原样保留**：接入任何高德符号前必须 `javap` 真实确认 class/package/method，确认不到就不写、并记 `BLOCKED`。
4. ~~引入新的高德构件（如 `navi-3dmap`）时，本表必须新增一行，并回填**真实下载到的版本号**。~~
   **本条已履行**（上表第一行即真实版本，取自 Gradle 解析到的 `navi-3dmap-10.0.600_3dmap10.0.600.pom`）。
5. **代价如实登记**：debug APK **126,516,687 字节**（`stat` 实测，2026-09-30），
   紧邻的换依赖前基线是 `AI_ITERATION_STATE.md` 状态板 `BUILD_STATUS` 记的 **57,941,374 字节**（V-2b 那一次真重打包）
   ⇒ **+68,575,313 字节（约 +68.6 MB / 2.18 倍）**。
   归因**已实测**（审计 §77 七）：涨的主体是专有构件自带的原生库 —— 包内高德 `.so` 解压量由换依赖前的
   **33,410,540 B** 增到 **77,091,668 B**，净增 **+43,681,128 B**（占差额 63.7%）；
   其余 **+24,894,185 B 只是残差**（上一轮包的 dex/assets 未留档，增量分不出来），
   原来"约 500 行 Kotlin 不可能吃掉 68 MB"那句量级话已作废；
   **但这仍不是受控 A/B**（没有为量体积单独回退重编过一次），要一个干净的对照得等下一轮专测体积。
   专有构件不按"开源复用"计，但**按体积与许可证两条都要能被追溯**，所以这两个数写在这里而不是只写在提交说明里。

## 4. 不在 APK 内、但决定屏上内容的上游（署名义务在这里）

| 上游 | 许可证 | 与 App 的关系 | 现状 |
|---|---|---|---|
| OpenStreetMap 数据（服务端 `chongqing-260921.osm.pbf`） | **ODbL 1.0** | App 上绘制的真实道路折线由 OSM 数据经 GraphHopper 算出，属于 ODbL 意义上的 Derivative/Produced Work | ⚠ **App 内尚未显示 OSM 署名**，见下 |
| GraphHopper（服务端路由引擎） | Apache License 2.0 | App 通过后端路由接口消费其结果，不随 APK 分发 | 无需在 APK 内附许可证；来源标签 `OSM_GRAPHHOPPER` 已在界面上如实显示 |
| 后端 `cargo-post-platform`（芋道 yudao 骨架 + 业务模块） | MIT（yudao 主仓声明） | App 的唯一真实业务后端 | App 端不复制其后端代码，仅按契约调用 |

**未结清的义务（登记，不静默）**：ODbL 1.0 §4.0 要求在展示 ODbL 数据衍生内容时给出署名。
当前 `grep -rni "openstreetmap" app/src/main` 命中数为 **0**（只有我自己写的"数据来源"注释，与此无关）。
处置：在展示真实道路折线/线路轨迹的地图页，来源标注里为 OSM 派生来源追加 "道路数据 © OpenStreetMap contributors (ODbL)"，
落点与判据在 `AI_ITERATION_STATE.md` 队列项里登记，实现时回来把本行改成"已署名 + 文件:行号"。

## 5. 计划复用但**尚未引入**的开源项目（§28–§31、§115）

以下项目在指令里被列为允许 copy/adapt/port 的 MIT 来源。**当前工程一行代码都没有引入**，
所以它们不出现在 `app/build.gradle.kts` 里，也不产生即时许可证义务。真正引入时必须在本节补：
source repo / commit 或 tag / 许可证全文 / 被复制或修改的文件清单（§102）。

| 项目 | 许可证（引入时按当时上游 LICENSE 复核） | 计划用途 | 阶段 |
|---|---|---|---|
| MapLibre Navigation Android | MIT（上游 README 声明，引入时复核） | Turn-by-turn 导航内核、reroute 逻辑 | Phase 8 / #49 之后 |
| GraphHopper Navigation Example | MIT | 导航状态机、转向指令映射的参考实现 | 仅参考，不整仓复制 |
| Valhalla Mobile | MIT | 设备端离线 routing / map matching | Phase 8，第一阶段只留 `OfflineRoutingEngine` 接口 |
| MapLibre GL Native/Android | BSD-3-Clause + 字体/瓦片各自条款 | 离线地图渲染替换 AMap | Phase 8 |

规则（§101、§103）：
- 允许 copy / adapt / port，条件只有三个 —— **许可证兼容、出处登记、版权保留**。
- **禁止删除 copyright 头**，禁止因为"本项目非商业"就不附 license。
- 只提取实际需要的类与逻辑，不整仓复制；复制过来的每个文件在本表登记到文件粒度。
- 本项目主仓为 MIT（§102）：不得引入与之冲突的二次许可条款。

## 6. 本文件的维护判据

新增/升级任何依赖，或从上面第 5 节真正复制了代码，必须同步本文件，否则视为未完成该轮。
自检（本机 GBK 环境）：

```bash
# 声明的依赖必须逐条能在本文件里找到；本文件提到的版本必须能在 gradle 解析结果里找到
sed -n '/^dependencies/,/^}/p' app/build.gradle.kts | grep -o '"[^"]*:[^"]*:[^"]*"' 
grep -n "版本\|| .*$" THIRD_PARTY_NOTICES.md | head
```

**V-2b（2026-09-30）复核结果**：本文件**未新增行** —— 实测依赖仍是 **20 条坐标**
（`sed -n '/^dependencies/,/^}/p' app/build.gradle.kts | grep -c '"[^"]*:[^"]*:[^"]*"'` → `20`），
`app/build.gradle.kts` 修改时间 `Sep 29 02:06`（本轮一字未动），第三方代码引入 **0 行**。
本轮新文件 5 个均为本项目自写、不随 APK 分发，按 CONTEXT_LOCK 纪律登记在
`CONTEXT_LOCK.md` §八 ⑰ 与 `DECISION_LOG.md` D-030：
`app/src/test/.../nav/RoleReconcileTest.kt`、`app/src/test/.../ui/driver/WorkbenchRoleReconcileContractTest.kt`、
`tools/check_role_reconcile_probes.sh`、`tools/role_reconcile_negative_controls.sh`、
`tools/role_reconcile_test_negative_controls.sh`。
⚠ 本节的存在不代表"轮轮都要写一行"：**只有依赖或复制代码发生变化才动上表**，否则只在此补一行实测数（与 §6 判据一致）。

**N-1（2026-09-30，第 8 步导航引擎）复核结果 —— 这一次上表真的动了**：
`com.amap.api:3dmap:10.0.600` → `com.amap.api:navi-3dmap:10.0.600_3dmap10.0.600`（**顶替**，不是并存），
坐标条数实测**仍是 20**（同一条自检命令 `grep -c` → `20`），§3 表已按第 4 点的新规矩
**回填真实下载到的版本号与构件自带 POM 里的许可证名**（`The AMap Software License, Version 1.0`，
逐字取自 `navi-3dmap-10.0.600_3dmap10.0.600.pom`，不是从网页抄的）。
第三方**源码**引入仍为 **0 行**：本轮没有 copy / adapt / port 任何外部文件，全部新代码是本工程自写
（判据 N-j 实测高德 import 9 条全部只出现在 `AmapNavigationEngine.kt`，走的是 Maven 构件而非源码）。
本轮新增文件 7 个（3 生产 + 1 测试 + 3 判据工具，另改 `DriverNavScreen.kt` / `RealRoadRouteValidator.kt` /
`check_location_probes.sh`），按 CONTEXT_LOCK 纪律登记在 §八 ⑱ 与 `DECISION_LOG.md` D-031：
`app/src/main/java/.../core/navigation/NavigationEngine.kt`、`.../core/navigation/AmapNavigationEngine.kt`、
`.../ui/driver/DriverSdkNavCard.kt`、`app/src/test/java/.../core/navigation/NavigationContractTest.kt`、
`tools/check_navigation_probes.sh`、`tools/navigation_negative_controls.sh`、`tools/navigation_test_negative_controls.sh`。

**N-1-d 收台账复跑（2026-09-30）**：上表**再未变动** —— 依赖坐标实测仍 **20** 条，本轮只把体积归因从量级话换成实测数，
第三方源码引入仍 **0 行**。新增自写文件 **2** 个（都不随 APK 分发）：
`tools/check_ledger_tables.py`（五本台账的表格行结构自检，判据脚本）、`tools/ledger_table_report.txt`（它写出的当轮报告）。

**#51（2026-09-30，旧导航页降级为 DEBUG ONLY / D-032）复核结果 —— 本轮**零新增文件**，所以 §1/§2/§3/§4 四张表一字未动**：
依赖坐标实测仍 **20** 条（同一条自检命令 `sed -n '/^dependencies/,/^}/p' app/build.gradle.kts | grep -c '"[^"]*:[^"]*:[^"]*"'` → `20`），
没有引入任何新的运行时或测试期构件；第三方**源码**引入仍 **0 行**（本轮没有 copy / adapt / port 任何外部文件）。
本轮**新增自写文件 0 个**，只改了 **8** 个既有文件（`find -newermt` 实测列表）：
生产源 5 个 —— `ui/BusPostRoot.kt`、`ui/driver/WorkbenchScreen.kt`、`ui/driver/RoutesScreen.kt`（三处 DEBUG 闸 + 正式导航面重挂）、
`ui/driver/DriverSdkNavCard.kt`（承接外部地图交接与两个 `open*` helper，**搬移**不是复制）、
`ui/driver/DriverNavScreen.kt`（删假步进机器 + 自守闸 + 非真实导航标注）；判据工具 3 个 ——
`tools/check_authenticity_counts.sh`（假步进口径抬到目录级）、`tools/check_navigation_probes.sh`（N-m…N-p）、
`tools/navigation_negative_controls.sh`（27 条破口）。⇒ 按 CONTEXT_LOCK 的登记纪律，本轮只需同步 §八 ⑲ 与 `DECISION_LOG.md` D-032，
**许可证表无需变更这件事本身就是"没引入新依赖"的证据**（若引入，第一张表就会多一行）。
⚠ 一条留给下一轮的读数提示：用例级负控脚本会 `cp` **原样恢复** `core/navigation/NavigationEngine.kt`，
所以它的 **mtime 会晚于本轮的其它改动**（本轮实测 mtime 02:59，而 `cmp` 已证与改前基线**逐字节相同**）——
**mtime 变了不等于内容变了**，按 §六"gradle 按内容哈希判"的同一口径读它。

**E-1（2026-09-30，删掉解析层自造的 ETA 别名键 / D-033）复核结果 —— 本轮同样零新增文件，四张表一字未动**：
依赖坐标实测仍 **20** 条（同一条自检命令 `sed -n '/^dependencies/,/^}/p' app/build.gradle.kts | grep -c '"[^"]*:[^"]*:[^"]*"'` → `20`），
第三方**源码**引入仍 **0 行**。本轮改的是**读 JSON 的键名列表**，与构件无关 —— 但按登记纪律仍要写明：
**本轮没有、也不允许**用"引入一个新库来'规范化'字段映射"（如某个 JSON binding）绕过它，那会把问题换成一个新许可证条目。
本轮**新增自写文件 0 个**，只改了 **7** 个既有文件（`find -newermt "2026-09-30 02:30"` 实测列表，其中 UI/导航那批是 #51 的）：
生产源 2 个 —— `data/model/Models.kt`（四处 `etaMinutes` 读法退回单键，`BusVehicle` 那处另删裸 `"minutes"`）、
`data/repo/Repo.kt`（`traceOf` / `routeGuideOf` 两处）；测试 2 个 —— `BusEtaContractTest.kt` 12 → **13**、
`SendEtaContractTest.kt` 9 → **12**；判据工具 3 个 —— `tools/check_eta_probes.sh`（T-ai / T-ai2 / T-ai3 + T-k/T-x 抬下限）、
`tools/eta_negative_controls.sh`（**50 → 54** 条破口，并修 `T-t` 被本轮删动作废的锚点）、
`tools/shell_routing_negative_controls.sh`（修 `SR-f` 被 **#51** 作废的锚点 —— 跨簿，见 D-033 第 6 条）。
⚠ 本轮 `find` 会额外列出 `DriverExceptionReport/Screen.kt`、`ActionGate.kt`、`NavigationEngine.kt`、`nav/Routes.kt`、
`nav/ShellDecision.kt`、`ui/driver/WorkbenchScreen.kt` 七个**mtime 晚于内容改动**的文件：那是四套**就地**负控脚本
`cp` 原样恢复留下的时间戳，每套末尾都有 `cmp 一致` 的实测行（不是遗漏、不是本轮改动）。
**许可证表无需变更这件事本身就是"没引入新依赖"的证据**（若引入，第一张表就会多一行）。

**F-1（2026-09-30，异常可信化：程序错误不进演示数据 / 取消不当失败 / D-034）复核结果 —— 上表仍一字未动**：
依赖坐标实测仍 **20** 条（同一条自检命令 `sed -n '/^dependencies/,/^}/p' app/build.gradle.kts | grep -c '"[^"]*:[^"]*:[^"]*"'` → `20`），
`app/build.gradle.kts` mtime `2026-09-30 00:40:50`（**本轮一字未动**，早于本轮 08:13 的编辑窗口），第三方**源码**引入仍 **0 行**。
本轮做的事是"把已有的异常通路改直"，不需要任何新构件；⚠ 也**没有**借这轮引入任何 `runCatching`/结果包装类的第三方库 ——
那只会把一个许可证条目换成另一个，且 `Attempt` 的形状本来就该由本工程的取消语义定。

本轮**新增自写文件 5 个**（`find app/src tools -type f -newermt` 与路径实测）：
① `app/src/main/java/com/shanxiang/buspost/core/net/Attempt.kt`（**24 行 / 1314 字节，随 APK 分发**）——
    它是本工程自写的 `suspend inline fun <T> attempt(block: suspend () -> T): Result<T)`，
    **不是从任何第三方拷来的 Either/Result 单子**：全仓没有 copyright 头、没有 LICENSE 文件随它进来，
    所以 §1/§3 两张表都没有它的行；它进 APK 这件事**只增加"自写代码"的体积**，不增加署名义务；
② `app/src/test/java/com/shanxiang/buspost/core/net/DemoExceptionIsolationTest.kt`（§三十 点名的命名用例类，7 例）；
③ `app/src/test/java/com/shanxiang/buspost/core/net/ApiCancellationTest.kt`（§三十 点名的另一个，5 例）；
④ `tools/check_throwable_probes.sh`（TH-a…TH-l 判据）；⑤ `tools/throwable_negative_controls.sh`（18 条破口 + 1 行恢复后基线自检）。
②–⑤ 只在测试期 / 判据期存在，**不进 APK**（与 §2 同一口径）。

本轮**改动既有生产文件 17 个** —— 清单由**内容**得，不由 mtime 得（三种收法各有一种探针形状）：
`attempt {` 命中 7 个（`BusPostApp` / `core/loc/Locator` / `data/repo/Repo` / `notify/NotifyWorker` / `ui/BusPostRoot` /
`ui/driver/DriverSdkNavCard` / `ui/driver/HandoverScreen`）+ 生效 `catch (e: CancellationException)` 子句命中 4 个
（`core/net/Api` / `core/net/Attempt`（新）/ `data/repo/Repo` / `ui/login/LoginViewModel`）+ `if (!demoAllowed) throw e` 命中 1 个（`core/net/Api`）
⇒ 并集去重 16 个（含 `Attempt.kt`），再加上编辑窗口 08:13–08:28 内的 `driver/DriverLocationService` / `ui/common/ScreenViewModel` /
`ui/user/Orders|Goods|Send|Home` / `widget/NextBusWidgetProvider` ⇒ 既有生产文件 **17** 个。
⚠ **必须点名的一条方法读数**：`WorkbenchScreen.kt:549-555` 的收法是**整圈 `runCatching` 仪式摘掉**，
所以它**同时不在** mtime 窗口清单里（16:55 是被就地簿 `cp` 恢复的时间戳）、**也不在**任何一条内容 glob 里
（既无 `attempt {`、无 CE 子句、无 demoAllowed 出口）—— 它是 `comm -23` 拿"注释里引用 §三十一 的文件"与上面并集作差才被找出来的。
⇒ **"改了哪些文件"这件事，靠 glob 数出来的清单会漏掉'删除型'改动**，本轮的 17 是并集 + 作差两条都跑过之后的数，不是估的。
⚠ mtime 口径同上一条目（E-1）：`16:43–16:57` 那一批（`DriverExceptionReport.kt`、`DriverExceptionScreen.kt`、`ActionGate.kt`、
`NavigationEngine.kt`、`Repo.kt`、`WorkbenchScreen.kt`、`ShellDecision.kt`、`Routes.kt`）是**四套就地负控簿 `cp` 原样恢复**留下的时间戳，
每套末尾都打了 `cmp 一致`；按 §六"gradle 按内容哈希判 UP-TO-DATE"的同一口径读它们 ——
本轮收工复检验证了这一点：`:app:assembleDebug :app:lintDebug` → **48 actionable tasks: 1 executed / 47 up-to-date**，
APK 体积/哈希未变（`114,437,316 B`，mtime 16:45:11），说明这批恢复**确实没有改内容**。

**F-2 ①–⑤（2026-09-30，"缺失字段被默认值伪装"：v2.1 落档 + §三 QuoteResult + §四 NearbyResult + §五 数字默认值普查 / D-035 · D-036）复核结果 —— 四张许可证表仍一字未动**：
依赖坐标实测仍 **20** 条（同一条自检命令 `sed -n '/^dependencies/,/^}/p' app/build.gradle.kts | grep -c '"[^"]*:[^"]*:[^"]*"'` → `20`），
`app/build.gradle.kts` mtime 实测 **09-30 00:40:50**（本轮一字未动，早于本轮 17:40 起的编辑窗口），第三方**源码**引入仍 **0 行**。
⚠ 本轮**特别要写清**的一件事：§五 的普查给出 **472** 处带默认值读法，**没有任何一处**是靠"引入一个 JSON 映射库 / Optional 库 / 校验库"来收的 ——
那只会给 §1 增加一个新许可证条目，而问题本身是"读法替后端说话"，与库无关（同 E-1 那条禁令）。

本轮**新增自写文件 11 个**（`find app/src tools docs -type f -newermt "2026-09-30 17:35"` 与路径实测，全部**不随 APK 分发**，除四份文档随仓库走之外没有二进制进包）：
① 文档 4 份：`docs/android/ANDROID_COMMAND_V2.1.md`（v2.1 原文落档，27,323 B / 1,618 行 / 35 节）、
   `docs/android/ANDROID_V2.1_CHECKLIST.md`（V21-01…35 逐节对表 + F-2 十八步映射 + **两条指令的冲突点**）、
   `docs/android/DEFAULT_VALUE_AUDIT.md`（§五 的 472/B 398/C 74/A 0 分类明细）、`docs/android/CONTRACT_EVIDENCE.md`（逐字段记 producer 与被删候选名）；
② 判据工具 6 个：`tools/v21_requirement_census.sh`（需求普查 113 行，本轮补 `code=` 列）、
   `tools/default_value_census.py`、`tools/check_default_value_probes.sh`（DV-a…DV-l）、`tools/default_value_negative_controls.sh`（16 条破口）、
   `tools/check_contract_defaults_probes.sh`（Q-* + B-*）、`tools/contract_defaults_negative_controls.sh`（22 条破口 + 1 条**反向口径控制**）；
③ 测试 4 个类（**只在测试期存在，不进 APK**，与 §2 同一口径）：`data/MinutesContractTest.kt`（6 例）、
   `data/QuoteNullSafetyTest.kt`（7 例）、`data/NearbyBooleanContractTest.kt`（6 例）、`data/EarningsContractTest.kt`（8 例）；
④ 另记 `tools/run_all_negative_controls_batch.sh`（本轮新写的**批处理**，逐本打印 `rc/✔/✘/△/◦`）与 `tools/ledger_table_report.txt`（它跑出的台账表格自检报告）。

本轮**改动既有文件**（内容口径，非 mtime 口径）：生产源 **7** 个 ——
`data/model/Models.kt`（`QuoteResult` 五字段可空 + 三个 `*Text` getter、`NearbyResult.inService/located` 三态、`Earnings`/`EarningItem` 可空化、五处 `*Minutes` 自造第二候选名删净）、
`data/repo/Repo.kt`（`inService`/`located` 走 `multiBoolOrNull`，现 `:726/:735`）、`data/repo/DemoData.kt`（演示面不再下发 `weekAmount`/`monthAmount`/`totalWeight`/`todayEarnings`/`totalEarnings`/`orderCount`/`items`/`date`/`amount`/`type` 这些**无生产者键**，改同形真实键；`:714` 留了一行"已删"说明）、
`ui/user/SendScreen.kt`、`ui/driver/EarningsScreen.kt`、`ui/user/BusScreen.kt`、`ui/user/HomeScreen.kt`（四个界面改走 `*Text` getter，不再渲染 UNKNOWN 为"运营中"、不再打印 `¥0`）；
判据工具 **4** 个 —— `tools/check_eta_probes.sh`（新立 T-aj 家族）、`tools/eta_negative_controls.sh`（破口 **54 → 60**）、
`tools/check_reachability_probes.sh`（**R-f 扫描面收紧**）、`tools/run_all_negative_controls_batch.sh`（新建后同轮用）。
⚠ **一条存疑 mtime，如实登记而不是并进清单**：`app/src/test/.../data/DistanceCaliberContractTest.kt` 的 mtime **18:33:56** 落在本轮编辑窗口内，
但文件里**没有本轮任何字面量**（`F-2`/新 getter/新用例名都 0 命中）、用例条数仍是 **4**（与 F-1 收工读数一字不差）⇒
本轮**不**把它计入改动面。它也不在任何就地簿的恢复清单里（`eta` 簿是把测试文件 `cp` 进沙箱 `$SC`，不碰原件），
所以这是一个**解释不了的 mtime**，按 §六"宁可留一个问号，不编一个归因"处理。
⚠ 同一轮里 `ActionGate.kt`（20:05:17）与 `Api.kt`（18:43:34）那类"晚于本轮内容改动"的时间戳是**就地负控簿 `cp` 原样恢复**留下的，
本轮负控批处理跑到 20:0x 仍在进行，所以这些 mtime 一律**不作**改动证据（改动证据只看内容）。
⚠ **本节最后一条与许可证无关、但属于同一道闸门**：本轮实测**APK 新鲜度只认包内内容** ——
报了"12 executed"的那次构建留着的是 16:45 的旧包（里面查不到本轮字样），
`:app:assembleDebug --rerun-tasks` 才得到 **113,863,001 B / 19:33:42 / sha1 `7fd54a4a54acde1560c07dbed3a650e30b617f4e`**，
`unzip` 全部 **15** 个 dex 后 grep 到"金额未给"×2、"后端无此口径"×1、**"暂无报价"×0**。



**F-2 ⑥（2026-09-30，用户端 `create` 幂等 / v2.1 清单第 6 项 / D-038）复核结果 —— §1 与 §2 两张表仍一字未动**：
依赖坐标实测仍 **20** 条（同一条自检命令 → `20`），`app/build.gradle.kts` mtime 实测 **09-30 00:40:50**（早于本轮 20:49 起的编辑窗口，
本轮一字未动），`gradle.properties` **09-29 02:29:42**（同样未动：编码问题只在**命令行**用 `-Dorg.gradle.jvmargs` 处置，
没把环境缺陷写进工程配置），第三方**源码引入**仍 **0 行**、**新增依赖 0 个**。
⚠ 本轮**没有**为幂等引任何库：闸门是 V-1 自写的 `ActionGate`（`app/src/main/java/com/shanxiang/buspost/data/repo/ActionGate.kt`，
**本轮该类一字未改** —— 只新增了一个实例），键摘要用的是 JDK 自带 `java.security.MessageDigest`（`Repo.kt:505-517`），
两者都**不产生新的许可证条目**。
本轮**新增自写文件 1 个**：**测试期**类 `app/src/test/java/com/shanxiang/buspost/data/repo/UserCreateIdempotencyTest.kt`（10 例，
**只在测试期存在、不进 APK**，与 §2 同一口径）。它的包内实测（**本轮把这条从"应当如此"升级成"量过"**）：
`unzip` 出 APK 全部 **15** 个 dex 逐字节计数 ⇒ `Lcom/shanxiang/buspost/data/repo/UserCreateIdempotencyTest;` = **0**、
`Lcom/shanxiang/buspost/data/repo/DriverActionIdempotencyContractTest;` = **0**、`Lokhttp3/mockwebserver/` = **0**
（`mockwebserver` 是 `testImplementation`，见 §2 表内 4.12.0 / Apache License 2.0 那行）。
⚠ **这三个 0 是按"先证方法再信 0"读的**：同一个脚本、同一批 dex 里，本轮新串 `userCreateGate` 数到 **1**、
`gatedUserCreate` **4**、`create:` **1**，旧串 `gatedAction` **4**、`本次请求未发给后端` **1** 都命中非 0
⇒ 计数工具是好的，那个 0 才是"确实不在包内"。
另记本轮的一条**打包新鲜度**发现（与许可证无关，但它是 §6 维护判据里"产物与声明要对表"的那道闸）：
21:08 那次 `:app:assembleDebug` 报 `packageDebug` 执行过而盘上仍是 19:33:42 那份（`userCreateGate` 在 15 个 dex 里 **0** 命中），
`--rerun-tasks`（**37 executed**）后得到 **21:13:59 / 113,863,001 B / sha1 `03ab61726030a19f431da275f0bfe3deaa976d2c`** ——
⚠ **与旧那份同字节而不同哈希**，所以本文件里"体积没变"从来不能读成"内容没变"（详见 DECISION_LOG D-038 与判据 §八 ㉒ ④ 加强版）。



**F-2 ⑦（2026-10-01，构建失败四类分型 / v2.1 清单第 7 项 / V21-33 / D-039）复核结果 —— §1 与 §2 两张表仍一字未动，新增行 0**：
依赖坐标实测仍 **20** 条（同一条自检命令 `sed -n '/^dependencies/,/^}/p' app/build.gradle.kts | grep -c '"[^"]*:[^"]*:[^"]*"'` → `20`，
→ `20`，本轮 00:16:30 复跑仍 `20`），`app/build.gradle.kts` mtime 实测 **09-30 00:40:50 / 11,008 B**（早于本轮 **22:15** 起的编辑窗口 ⇒ 一字未动），
`gradle.properties` **09-29 02:29:42 / 920 B**（同样未动：Windows/GBK 那条环境缺陷只在**命令行**用
`-Dorg.gradle.jvmargs=… -Dfile.encoding=GBK` 处置，没把环境缺陷写进工程配置），第三方**源码引入**仍 **0 行**、**新增依赖 0 个**。
本轮**没有**为分型引任何库：分类器是**自写的 bash**（`tools/classify_verification_failure.sh`，116 行，只调 `grep -a -F -f`），
规则表是**自写的三列 TSV**（`tools/verification_failure_rules.txt`，36 行文件 / **16 条规则行** = SOURCE_FAILURE **3** ·
DEPENDENCY_FAILURE **4** · ENVIRONMENT_FAILURE **6** · DEVICE_UNAVAILABLE **3**，实测每行字段数都是 3）⇒ 两者都**不产生新的许可证条目**。

本轮**新增文件 14 个，全部自写、全部不进 APK**（与 §2 的"测试期"同一口径）：
`tools/verification_failure_rules.txt`（3,275 B）、`tools/classify_verification_failure.sh`（5,647 B）、
`tools/check_verification_failure_probes.sh`（12,200 B）、`tools/verification_failure_negative_controls.sh`（12,891 B）、
`tools/fixtures/verification-failure/README.md`（11,307 B）**+ 7 份 `.log`**（合计 1,839 行，最大一份
`environment-failure-worker-classnotfound.log` **1,660 行**）、
`app/src/test/java/com/shanxiang/buspost/verification/VerificationFailure.kt`（4,853 B / 90 行）与
`VerificationFailureTest.kt`（7,157 B / 126 行，**8** 例）。⚠ 落点在 **test 源集而不在 `app/src/main`** 是本轮的刻意决定，
理由与三条备选一起记在 D-039；`tools/` 更是不参与编译的目录。

**为什么"引用工具报错原文"不构成本文件的新一行**（这是本轮唯一需要正面回答的许可证问题）：
规则表那 **16** 个字面量全是**本工作区自己跑出来的 stdout/stderr 子串**（Gradle / kotlinc / JDK / adb 的报错短语），
引它们=引**事实文本**，不=引该工具的源码或构件；Gradle 与 Kotlin 本就是构建工具（Apache-2.0），已在 §1 的构建链口径内。
实测字面量列**逐条纯 ASCII**（`awk -F'\t'` 只看第 2 列，含非 ASCII 的字面量 **0** 条）——
⚠ 这个"全 ASCII"是**等价性的前提**而不是卫生要求：分类器用 `grep -aF`、JVM 用例用 `String.contains`，
只有纯文本子串才让两边可互相证等价（用例逐行验，判据 `VF-b` 6/6）；**含非 ASCII 的表行会被分类器直接拒收**。
另外 **7 份样例日志里没有凭据**：`grep -rniE "token|password|secret|authorization|bearer|api[-_]?key|cookie"` →
匹配文件数 **0**；`grep -cE "127\.0\.0\.1|localhost"` → 七份全 **:0**（"正式包不碰本机回环"那条硬规则连日志快照也过）。

**包内实测（本轮把"不进 APK"从推论升级成量过的数）**：对 `21:13:59` 那份 APK `unzip` 出全部 **15** 个 dex 后逐串
`grep -a -o -F … | wc -l`（**出现次数**口径）⇒ `Lcom/shanxiang/buspost/verification/` = **0**、`VerificationFailure` = **0**、
`SourceFailure` = **0**、`DeviceUnavailable` = **0**。⚠ **这四个 0 同样是"先证方法再信 0"**：同一批 dex、同一条命令里
`userCreateGate` 数到 **1**、`gatedUserCreate` **4**、`DriverLegStateMapper` **5** 都非 0 ⇒ 计数工具是好的，那个 0 才是"确实不在包内"。



**F-2 ⑦ 收工终检（2026-10-01，负控簿互斥与退出码 / D-040）复核结果 —— §1 与 §2 两张表仍一字未动，新增行 0**：
依赖坐标实测仍 **20** 条（同一条自检命令 `sed -n '/^dependencies/,/^}/p' app/build.gradle.kts | grep -c '"[^"]*:[^"]*:[^"]*"'` → **20**，
本轮 00:44 复跑仍 `20`），`app/build.gradle.kts` mtime 实测 **09-30 00:40:50 / 11,008 B**、`gradle.properties` **09-29 02:29:42 / 920 B**
（两文件与上一条目所记**完全一致** ⇒ D-040 这一轮**没有**碰构建配置，也没有把 Windows/GBK 的环境缺陷写进工程）；
第三方**源码引入**仍 **0 行**、**新增依赖 0 个**。本轮改的全是 `tools/` 下**不参与编译**的 bash。

本轮**新增自写文件 1 个**：`tools/negctl_lock.sh`（实测 **3,841 B / 75 行**，mtime 10-01 00:34:54）。
它是纯 bash + 系统自带 `mkdir`/`ps`/`tasklist`，**没有引入任何第三方代码或库**，所以本文件不因此多一行；
它解决的问题是"负控簿会就地改源码"这件事**原先只写在注释里、没有任何机制执行**（详见 D-040 与判据 §八 ㉔）。

本轮**改动既有文件 17 个**（全部 `tools/`，`ls -la --time-style` 实测）：
`tools/run_all_negative_controls_batch.sh`（**1,736 B / 27 行**，00:35:07 —— 改成"批次持锁 + `export NEGCTL_LOCK_HELD=1` 让批内 16 本沿用同一把锁"，
否则批内第一本会把后面全 `[STOP]`）；`tools/*_negative_controls.sh` **16 本全部含锁**（实测 `grep -l negctl_lock` → **16/16**，每本命中 **2** 处 = source 行 + 调用行），
其中 **5 本**除锁之外还补了"空跑注入"与"死退出码"两道闸（mtime/大小：`exception_test` 7,063 B/120 行、`idempotency_test` 4,967 B/82 行、
`navigation_test` 8,331 B/177 行、`role_reconcile_test` 6,897 B/128 行、`shell_routing_test` 7,149 B/118 行），其余 **11 本** mtime 统一为 00:27:38（只加了那 3 行）。
⚠ **改前的 16 份快照留在 `/tmp/negctl_prelock_20261001-002718/`（路径登记在 `/tmp/negctl_backup_dir.txt`）**：
本工程**没有 git**（也不许 `git init` 造假历史），所以"改了什么、能不能退回"只能靠这份快照 + `cmp` 逐字节证明。
`bash -n` 对 16 本实测 **ok=16 / bad=0**（00:43 复跑）。

本轮实际**新增自写文件是 2 个而不是 1 个**（上一条只登记了 `negctl_lock.sh`，这条补齐并修正它）：
`tools/after_negctl_run_full_tests.sh`（实测 **3,181 B / 73 行**，mtime 10-01 00:48:18，`bash -n` ok）——
它把"负控全批在前、全量用例在后"这条原本只写在台账里的**顺序**做成机制：等锁释放 + 等批次打印"共 16 本" +
核 `rc=0` 行数等于 **16** 之后才起第二个 gradle。同样是纯 bash + 系统命令，**不引入第三方代码**、**不参与编译**、
⚠ **不进 APK**（`tools/` 整个目录都不进包，这条口径与 §2 的"测试期"一致）。

**F-2 ⑦ 收工终检（2026-10-01 01:40，串行复跑 + 基线闸抓到未还原注入 / D-041、判据 §八 ㉔ ⑦）复核结果 —— §1 与 §2 两张表仍一字未动，新增行 0**：
依赖坐标实测仍 **20** 条（同一条自检命令 `sed -n '/^dependencies/,/^}/p' app/build.gradle.kts | grep -c '"[^"]*:[^"]*:[^"]*"'` → **20**，01:41 复跑），
`app/build.gradle.kts` **09-30 00:40:50 / 11,008 B**、`gradle.properties` **09-29 02:29:42 / 920 B** —— 两文件与上两条目所记**完全一致**
⇒ 这一轮**仍然**没有碰构建配置、没有把 Windows/GBK 的码页缺陷写进工程（配方只活在命令行，见 D-037）；
第三方**源码引入 0 行**、**新增依赖 0 个**、**新增许可证条目 0 行**。

本轮（01:15→01:41 这一段）改动面：
· **生产源码 1 个文件**：`app/src/main/java/com/shanxiang/buspost/nav/ShellDecision.kt`
  —— 实测 **4,397 B / mtime 10-01 01:25:00**，改动内容是把 `deepLinkTarget` 里那行**还原**成
  `role != Role.DRIVER && route in Routes.DRIVER_ONLY -> null`（脏态实测 4,374 B，缺 `role != Role.DRIVER &&` 守卫）。
  这是**还原**而不是新增行为：不引入任何第三方代码、不改依赖、不改许可证口径；
  还原依据取自 `tools/shell_routing_test_negative_controls.sh:88/92` 的 from 串（**不自创源码形状**）。
· ⚠ **一个容易读错的数**：`find app/src/main -newermt 2026-10-01` 实测点出 **8 个** `.kt`
  （`NavigationEngine 01:08:39` / `ActionGate 01:03:33` / `Repo 01:14:04` / `Routes 01:25:00` / `ShellDecision 01:25:00` /
  `DriverExceptionReport 01:00:20` / `DriverExceptionScreen 01:00:20` / `WorkbenchScreen 01:14:04`）——
  其中 **7 个只是"就地型负控把同一份字节写回去"**（各簿尾 `[恢复] … cmp 一致` 逐本实测），
  **mtime 变不等于内容变**；唯一一次真有字节差的就是上面那条还原。
· **自写脚本改 1 个**：`tools/after_negctl_run_full_tests.sh`（实测 **4,378 B / 84 行**，mtime 01:36:07，`bash -n` ok）——
  补了三处 `grep -a`（批次日志含被截断的 UTF-8，不加 `-a` 会让 grep 判成二进制、往读数列表里混进一句
  "Binary file … matches"），并把"批次 `rc=0` 不足 16 本"从**只打印**改成**逐本点名 + 明写不得引用为本轮批次全绿**
  （刻意**不硬拦**：自拒那一本正是要靠紧接着的全量那一遍才能定位）。纯 bash + 系统命令，**不进 APK**、不产生许可证条目。
· **构建/lint 复跑读数（01:28:33）**：`assembleDebug + lintDebug` rc=0 / `BUILD SUCCESSFUL in 1m12s` /
  **48 actionable tasks: 3 executed, 45 up-to-date** ⇒ `packageDebug` **UP-TO-DATE = 没有重新打包**，
  盘上 APK 仍 **09-30 21:13:59 / 113,863,001 B / sha1 `03ab61726030a19f431da275f0bfe3deaa976d2c`（一字未变）**；
  lint 三份产物 mtime 仍 **09-30 21:09:49**（98,753 B / 0 error / 46 Warning + 32 Information = 78）
  ⇒ ⚠ **那份 XML 描述的是 21:09 的树**，从它里面读"本轮改动的文件 0 点名"**只能算旧报告读数、不能当本轮证据**（已按这条写进状态板）。
  `:app:assembleRelease` **未复跑**（HTTPS 闸门未动、结论沿用 rc=1 / `UNCLASSIFIED` / `BLOCKED`，**没有**加 `-Pbuspost.allowInsecureRelease=true`）。

**F-2 ⑧（2026-10-01，`apiCall` → `safeApiCall` 改名 + 5 个迁移点 / D-042、判据 §八 ㉕）登记结果 —— §1 与 §2 两张表仍一字未动，新增许可证行 0**：
依赖坐标实测仍 **20** 条（与上两条目**逐字同一条**自检命令：
`sed -n '/^dependencies/,/^}/p' app/build.gradle.kts | grep -c '"[^"]*:[^"]*:[^"]*"'` → **20**，**03:43** 复跑），
`app/build.gradle.kts` **09-30 00:40:50 / 11,008 B**、`gradle.properties` **09-29 02:29:42 / 920 B** —— 两文件与上两条目所记**完全一致**
⇒ 本轮**没有**碰构建配置、**没有**新增依赖、**没有**引入任何第三方源码（第三方源码引入仍 **0 行**）。

本轮**新增自写文件 3 个**（`wc -c` / `wc -l` 实测于 2026-10-01 **03:37–03:43**（台账收口时段），全部**不进 APK**、不产生许可证条目）：
· `app/src/test/java/com/shanxiang/buspost/core/net/SafeApiCallContractTest.kt` —— **10,418 B / 214 行**，mtime 02:21:19。
  只在 **test 源集**（与 §2 的"测试期"口径一致），与被测 helper 同包 `com.shanxiang.buspost.core.net`。
· `tools/check_safe_call_probes.sh` —— **13,222 B / 142 行**，mtime 02:30:05，`bash -n` ok；纯 bash + 系统命令。
· `tools/safe_call_negative_controls.sh` —— **14,024 B / 207 行**，mtime 02:36:07，`bash -n` ok；纯 bash + 系统命令。

本轮**改动既有文件**（生产侧 3 个 + 测试侧 1 个 + `tools/` 5 个，均为本工程自写代码，**不涉及第三方**）：
· 生产：`app/src/main/java/com/shanxiang/buspost/core/net/Api.kt`（改名 + KDoc，02:06:37 / 21,225 B）、
  `app/src/main/java/com/shanxiang/buspost/data/repo/Repo.kt`（5 个迁移点，66,448 B —— ⚠ 盘上 mtime **03:02:47 落在负控批的注入/还原窗口内**，
  **不代表本轮改动时刻**，这条口径见判据 §八 ㉕ 与 D-042 §5）、`app/src/main/java/com/shanxiang/buspost/ui/user/BusScreen.kt`
  （`polylineUnconfirmed` 与两句文案，02:15:03 / 28,505 B）—— **没有**替换成任何第三方实现、**没有**新增网络库。
· 测试：`app/src/test/java/com/shanxiang/buspost/core/net/ApiTest.kt`（02:17:08，同名的旧调用点跟着改名，用例数 11 未变）。
· `tools/` 5 个（实测大小/行数/mtime）：`v21_requirement_census.sh` 10,435 B/157 行/02:18:50、
  `check_throwable_probes.sh` 16,059 B/219 行/02:30:42、`throwable_negative_controls.sh` 10,567 B/165 行/02:30:45、
  `after_negctl_run_full_tests.sh` 5,154 B/89 行/02:38:57、`run_all_negative_controls_batch.sh` 1,828 B/28 行/02:39:02。
  ⚠ 后两个是**机制修复**（把写死的"共 16 本"换成 glob 推出来的 `EXPECTED_BOOKS`），不是引入新能力；`tools/` 全目录**不参与编译**。
  本轮 `bash -n tools/*.sh` 全量复跑：**total=40 / ok=40 / bad=0**。

⚠ **OSM 署名本轮仍是一笔欠账**（本轮只复测、没动它，那是 F-2 ⑭ 的活）：`grep -rni "openstreetmap" app/src/main` 实测 **0** 命中、
`grep -rni "odbl" app/src/main` 同样 **0** 命中 ⇒ 真实道路折线源自 OSM/ODbL 而 App 内从没署名，**§3 那条外部数据条目不等于界面署名**。
构建侧本轮读数（`:app:assembleDebug` rc=0、APK **114,437,929 B** / mtime **03:12:23**）**不改变上面任何一条许可证口径**：
包体积变化来自本工程自己的源码与 dex 布局，本轮**没有**新增任何第三方 aar/jar。

**F-2 ⑧ 台账收口那一遍（2026-10-01 03:37–04:04，纯文档轮）复核结果 —— §1 与 §2 两张表一字未动，新增行 0**：
依赖坐标实测仍 **20** 条（同一条自检命令 `sed -n '/^dependencies/,/^}/p' app/build.gradle.kts | grep -c '"[^"]*:[^"]*:[^"]*"'`），
`app/build.gradle.kts` **11,008 B / mtime 09-30 00:40:50**、`gradle.properties` **920 B / 09-29 02:29:42** ⇒ 这一遍**没有**碰构建配置、
第三方**源码引入 0 行**、**新增依赖 0 个**、**新增许可证条目 0 行**；工程内**新增自写文件 0 个**（`find app/src -newermt "2026-10-01 03:37"` → **0**，
本轮只动五本手写台账 + `docs/android/ANDROID_V2.1_CHECKLIST.md`，外加脚本自产物 `tools/ledger_table_report.txt`）。
唯一落在工程外的新文件是两份改前备份 `/tmp/AI_ITERATION_STATE.before_joinrow_035601.md`（440,679 B）——
它是"无 git 工程怎么证明台账被还原过"的凭据，路径已登记在 D-042 与判据 §八 ㉕，**不进 APK、不产生许可证条目**。

## F-2 ⑨（`runCatching` 协程取消审计，2026-10-01 09:45）新增自写文件 8 个 —— **第三方源码引入 0 行 / 新增依赖 0 个 / 新增许可证条目 0 行**

| 文件 | 行 / B | 进不进 APK | 署名义务 |
| --- | --- | --- | --- |
| `docs/android/RUNCATCHING_AUDIT.md` | 139 / 16,214 | 否（文档） | 无 |
| `tools/runcatching_detector.py` | 181 / 7,793 | 否（判据期） | 无 |
| `tools/check_runcatching_probes.sh` | 106 / 8,747 | 否 | 无 |
| `tools/runcatching_negative_controls.sh` | 201 / 11,778 | 否 | 无 |
| `tools/_rc_dump.py` `tools/_rc_ctx.py` `tools/_rc_tail.py` `tools/_rc_chain.py` | 本轮逐点分类的**临时测量脚本** | 否 | 无 |

`_rc_*.py` 四本只读 `app/src`、不被任何判据簿引用（`grep -rn '_rc_'` 除自身外 0 命中）；按用户"任何新文件都要同步进清单"
这条登记在此与 D-043，**不删**（工程无 git，删了就再没有凭据可查）。
`tools/runcatching_detector.py` 的检测逻辑是本轮按 `runCatching` 的块体配对 + 挂起名闭包自写的，
**没有引用任何第三方分析器**（ASM/ProGuard/detekt 都不在依赖树里，见 §2 依赖坐标实测 20 条未变）。
本轮改动既有文件 6 个：`data/repo/Repo.kt`、`core/navigation/AmapNavigationEngine.kt`、`ui/BusPostRoot.kt`、
`ui/driver/DriverNavScreen.kt`（生产源码 4 个，全部只换错误处理的**形状**，对外行为一字未变）
+ `tools/check_safe_call_probes.sh`、`tools/safe_call_negative_controls.sh`（SA-d 与 ⑨ 的冲突，见 D-043 第 6 节）。

## F-2 ⑩（NotifyWorker 失败语义 + 结果契约测试，2026-10-01 09:52–10:23）新增自写文件 4 个 —— **第三方源码引入 0 行 / 新增依赖 0 个 / 新增许可证条目 0 行**

| 文件 | 行 / B | 进不进 APK | 署名义务 |
| --- | --- | --- | --- |
| `app/src/main/java/com/shanxiang/buspost/notify/NotifyOutcome.kt` | 106 / 5,788 | **是**（生产源集） | 无（本工程自写密封类，不派生自任何第三方代码） |
| `app/src/test/java/com/shanxiang/buspost/notify/NotificationWorkerResultTest.kt` | 252 / 13,741 | 否（test 源集） | 无 |
| `tools/check_notify_result_probes.sh` | 144 / 11,828 | 否（判据期，`bash`） | 无 |
| `tools/notify_result_negative_controls.sh` | 217 / 14,592 | 否（负控，`bash`） | 无 |

自检命令与实测（mtime 均于 10:23 前落定）：`sed -n '/^dependencies/,/^}/p' app/build.gradle.kts | grep -c '"[^"]*:[^"]*:[^"]*"'` → **20**（未变）；
`app/build.gradle.kts` **11,008 B / 09-30 00:40:50**、`gradle.properties` **920 B / 09-29 02:29:42** —— 两文件与 ⑧⑨ 所记**逐字一致**
⇒ ⑩ **没有**碰构建配置、**没有**新增 aar/jar、**没有**复制任何第三方源码；⑩ 用到的第三方 API 只有 androidx.work 的既有类型
（`CoroutineWorker` / `Result.success|retry|failure` / `PeriodicWorkRequestBuilder`，§1 已在册，Apache License 2.0），
`NotifyOutcome.kt` 自身 `import` 全部指向本工程 `core/net`、`data/repo` 与 JDK（实测 6 行 import，0 个新坐标）。
本轮改动既有文件 3 个（均自写）：`data/repo/Repo.kt` 67,480 B / 09:52:28、`notify/NotifyWorker.kt` 3,776 B / 73 行 / 09:58:42、
`notify/AlertEngine.kt` 8,815 B / 162 行 / 10:01:21。

## F-2 ⑭⑮（App 内 OSM/ODbL 署名最小入口 + 三方对照矩阵，2026-10-01 10:41–11:04）新增自写文件 7 个 + §1 补登 2 行 —— **第三方源码引入 0 行 / 新增依赖 0 个**

| 文件 | 行 / B | mtime | 进不进 APK | 署名义务 |
| --- | --- | --- | --- | --- |
| `app/src/main/java/com/shanxiang/buspost/core/about/OpenSourceCredits.kt` | 80 / 5,790 | 10:41:54 | **是** | 无（自写数据源；它**承载**别人的许可证，本身不是第三方构件） |
| `app/src/main/java/com/shanxiang/buspost/ui/user/OssLicensesScreen.kt` | 105 / 4,502 | 10:42:44 | **是** | 无（Compose 屏，只渲染上一行的数据源） |
| `app/src/test/java/com/shanxiang/buspost/core/about/OpenSourceCreditsContractTest.kt` | 103 / 5,511 | 10:50:08 | 否（test 源集） | 无 |
| `docs/android/THIRD_PARTY_RUNTIME_MATRIX.md` | 93 / 10,693 | 10:54:14 | 否（文档） | 无 |
| `tools/apk_oss_census.py` | 188 / 9,438 | 10:56:54 | 否（判据期，纯标准库 `zipfile/struct/re`） | 无 |
| `tools/check_oss_credits_probes.sh` | 206 / 18,817 | 11:01:06 | 否 | 无 |
| `tools/oss_credits_negative_controls.sh` | 255 / 15,434 | 11:04:01 | 否 | 无 |

**§1 本轮补登 2 行**（`com.squareup.okio:okio` 3.9.0、`org.jetbrains.kotlinx:kotlinx-serialization-core` 1.6.3，均 Apache License 2.0）：
这是把**已经在 `debugRuntimeClasspath` 解析结果里、也已经被 census 数出字节码**的两个传递依赖补进台账，
**不是新增依赖** —— 声明侧坐标实测仍 **20** 条（同一条自检命令于 **11:16:03** 复跑 → **20**），`app/build.gradle.kts` **11,008 B / 09-30 00:40:50**、
`gradle.properties` **920 B / 09-29 02:29:42** 一字未变。台账里这两行此前是缺的（§1 只列声明的 20 条，漏了传递依赖）。

本轮改动既有文件 3 个（均自写，接线的最小面，未重排既有 UI）：`nav/Routes.kt` 2,490 B / 53 行 / 10:42:53（`OSS_LICENSES` 路由登记）、
`ui/BusPostRoot.kt` 9,114 B / 187 行 / 10:43:16（composable 注册，CRLF 行尾）、`ui/user/MinePages.kt` 42,716 B / 958 行 / 10:43:16
（设置页一行入口，CRLF 行尾）；另 `OpenSourceCreditsContractTest.kt` 的在册条数口径在本轮由 16 改 **17**（§1 补登之后 included 实测 17 行 + 专有 1 行 + 未进包 6 行，`awk` 逐段计数）。

⚠ **对 ⑦/⑧ 那笔"OSM 署名欠账"的更正（历史行不删，只在此更正）**：⑧ 段写的 `grep -rni "openstreetmap" app/src/main` 实测 **0**、
`odbl` 实测 **0**，是 ⑭ 之前的真实状态；⑭ 落地后同一条命令实测 **各 3 命中**（`core/about/OpenSourceCredits.kt:27/:28/:76` 与 `:29`、
`ui/BusPostRoot.kt:135`、`ui/user/OssLicensesScreen.kt:29`），APK 内 `© OpenStreetMap contributors` 与 `ODbL` 两串各实测 **2 次**（dex；`resources.arsc` 0 次）。
⇒ 那笔欠账在**源码与字节**两侧都已消；但**界面是否真渲染出来仍是 `DEVICE_VERIFIED` 未到位**（⑪ 实测 0 台设备），这条不能顺带写成已验证。

本轮 APK 侧读数（`:app:assembleDebug` rc=0，实测 10:50:46）：**114,447,105 B**、`classes*.dex` **16** 个、打包类(defined) **29,816**、
引用类型 **36,632**（去重 31,878）。census 判定 **INCLUDED_IN_APP 19 前缀 / NOT_IN_APK 9 前缀**，9 条"期望 0"反证位实测全部 **0**。
构建产物**没有**引入任何新的第三方 aar/jar：体积增量来自本工程自己的源码与 dex 布局。

解释器自产物登记（不是源码、不参与编译、不进 APK；工程无 git，故登记不删）：
`tools/__pycache__/runcatching_detector.cpython-313.pyc` 12,263 B / 09:28、`tools/__pycache__/apk_oss_census.cpython-313.pyc` 11,471 B / 10:52。

## F-3 ⑤（线路页折线三态，2026-10-02 09:0x–10:2x）新增自写文件 2 个 —— **第三方源码引入 0 行 / 新增依赖 0 个 / 许可证义务 0 条**

| 文件 | 行 / B | mtime | 进不进 APK | 署名义务 |
| --- | --- | --- | --- | --- |
| `app/src/main/java/com/shanxiang/buspost/core/map/PolylineDecision.kt` | 69 / 4,183 | 09:00:58 | **是** | 无（自写纯函数；不引任何第三方符号，只做本工程三态文案的派生） |
| `app/src/test/java/com/shanxiang/buspost/core/map/PolylineDecisionContractTest.kt` | 169 / 7,513 | 09:04:30 | 否（test 源集） | 无 |

**§1 本轮不补登、不改表**：声明侧坐标实测仍 **20** 条（本轮重度量：`grep -oE '"[a-zA-Z0-9_.-]+:[a-zA-Z0-9_.-]+:[^"]*"' app/build.gradle.kts | wc -l` → **20**，实测 10:50:52），
`app/build.gradle.kts` **11,008 B / 09-30 00:40:50**、`gradle.properties` **920 B / 09-29 02:29:42** 一字未变 ⇒ 本轮**没碰构建脚本**，
APK 里第三方 aar/jar 的集合与 F-2 ⑮ 那遍**同一份**——本轮实测凭证：`tools/check_oss_credits_probes.sh` @10:18:09 `OS-e INCLUDED/NOT/期望0却内置/前缀行数 = **19/9/0/28**`（与 F-2 ⑮ 的 19/9 逐位同）、`python tools/apk_oss_census.py app/build/outputs/apk/debug/app-debug.apk` 于 **10:51:47** 重跑 rc=0 打 `APK 字节数 = 113895828 / dex 份数 = 16 / defined = 29825 / referenced = 36662（去重 31890）`。

本轮改动既有文件 1 个（源码）+ 2 个（判据/负控，均自写）：`ui/user/BusScreen.kt` **31,184 B / 630 行 / 09:05:32**（LF；三态接线 + 请求序号双条件闸，
未重排既有 UI、未新增任何屏幕）、`tools/check_safe_call_probes.sh` **24,761 B / 231 行 / 09:10:06**（SA-e 重锚 + SA-e2/e3/e4）、
`tools/safe_call_negative_controls.sh` **19,428 B / 269 行 / 09:11:15**（破口 19–26，累计 26 条 + 3 自检行）。
另有两份**既有台账**的就地改锚（不含新文件、不涉第三方构件）：`ANDROID_PARITY_AUDIT_V2.md` 11 行、`docs/android/CONTRACT_EVIDENCE.md` 1 行 —— 对照表在 `DECISION_LOG.md` **D-049 第 7 节**。

本轮 APK 侧读数（`:app:assembleDebug` + `:app:lintDebug --rerun-tasks`，实测 10:21:54 / 10:22:58）：**113,895,828 B**、
sha256 `c82980329a7037ace44cdee39903fcbf6251e624cc43b5fbc76c3d3ac01fe4cc`、`classes*.dex` **16** 个、打包类(defined) **29,825**、
引用类型 **36,662**（去重 31,890）。⚠ **本轮代码确实进包是字节侧实测的**：`PolylineDecision` 在 `classes8.dex`(3) / `classes13.dex`(1)、`polylinePhase` 在 `classes13.dex`(1)。
lint **79 issues = Warning 46 + Information 32 / Error 0**，`PolylineDecision`、`BusScreen` **0 点名**。
体积相对 F-2 ⑮ 那遍（114,447,105 B）**变小**，与 09:18 的增量产物（114,480,990 B）差 **585,162 B** —— 这笔**未归因**（增量 D8 dex 合并布局 vs 全量重打只是**假设**，
增量产物已被覆盖、无对照物），归 #140 那格继续在册，**不写成结论**。

解释器自产物：`tools/__pycache__/` 本轮**没有新增**（仍两个：`runcatching_detector.cpython-313.pyc` 12,263 B / 09-28 起算 09:28、
`apk_oss_census.cpython-313.pyc` 11,471 B / 10-01 10:52 —— mtime 均为 10-01，本轮未重编译出新的 `.pyc`）。


## F-3 ⑥（USER 业务闭环与分域深链复核，2026-10-02 17:3x–22:1x）新增自写文件 2 个 —— **第三方源码引入 0 行 / 新增依赖 0 个 / 许可证义务 0 条**

| 文件 | 行 / B | mtime | 进不进 APK | 署名义务 |
| --- | --- | --- | --- | --- |
| `app/src/main/java/com/shanxiang/buspost/ui/user/BusDetailSlot.kt` | 51 / 2,206 | 17:45:18 | **是** | 无（自写纯函数；不引任何第三方符号，只做本工程详情页四态的派生） |
| `app/src/test/java/com/shanxiang/buspost/nav/RouteDomainDeepLinkTest.kt` | 168 / 9,237 | 17:44:57 | 否（test 源集，包内实测 ABSENT） | 无 |

**§1 本轮不补登、不改表**：声明侧坐标实测仍 **20** 条（`grep -oE '"[a-zA-Z0-9_.-]+:[a-zA-Z0-9_.-]+:[^"]*" app/build.gradle.kts | wc -l` → **20**，实测 22:0x），
`app/build.gradle.kts` **11,008 B / 09-30 00:40:50**、`gradle.properties` **920 B / 09-29 02:29:42** 一字未变 ⇒ 本轮**没碰构建脚本**，
APK 里第三方 aar/jar 的集合与 F-2 ⑮ / F-3 ⑤ 那两遍**同一份**；本轮实测凭证：`tools/check_oss_credits_probes.sh` 批后单本补跑 **rc=0 / 命中=19 / 失效=0**（19:19:41）。

本轮改动既有源码 **6 个**（`nav/Routes.kt` 102 / 5,235、`MainActivity.kt` 70 / 2,773、`notify/Notifier.kt` 117 / 5,197、
`notify/AlertEngine.kt` 162 / 9,103、`widget/NextBusWidgetProvider.kt` 123 / 5,160、`ui/user/BusScreen.kt` 664 / 33,625 @19:20:14）
+ **2 本 tools**（`check_shell_routing_probes.sh` 167 / 15,128 @18:06:31、`shell_routing_negative_controls.sh` 258 / 12,687 @18:13:47）。
⚠ **改动面按内容证据取，不按 mtime**：`nav/Routes.kt`/`nav/ShellDecision.kt` 现读 mtime **19:03:59** 落在负控批窗口内（批结束 19:08:50），
那是就地型负控的**还原动作**推进的时间戳；`ui/BusPostRoot.kt` mtime 是 **10-01 19:35:35**（F-3 ③ 那轮），**本轮没有改它**。
终值指纹 `files=160 sha256=b87ad1ab…`（22:0x 复算，与 19:2x 那份同串）⇒ 上述两个时间戳的漂移**不涉及内容**。
换行符：两个新文件与 `BusScreen.kt` 全部 **LF**（python 字节计数 `b'
'` = 0/51、0/168、0/664）；
被改的 CRLF 文件（Routes 102/102、MainActivity 70/70、Notifier 117/117、AlertEngine 162/162）改完仍是 CRLF，**没有混行尾**。

本轮 APK 侧读数（`:app:assembleDebug --rerun-tasks` rc=0 / **37/37 executed**，实测 19:21:24）：**113,895,828 B**、
sha256 `0e0a064689f9e44b471ad5798e3a88d4d15b420948c22d13cd04229771c24d9a`、`classes*.dex` **16** 个、打包类(defined) **29,828**、
引用类型 **36,677**（去重 31,894）。⚠ **本轮代码确实进包是字节侧实测的**：`Lcom/shanxiang/buspost/ui/user/DetailSlot;` 在 `classes13.dex`(3)、
`…BusDetailSlotKt;` 在 `classes13.dex`(2)；对 D-049 的 defined 29,825 是 **+3**，**第三个只归因到"未点名"这一层**（#140 同格，不写成结论）。
lint `:app:lintDebug --rerun-tasks` rc=0 / **28/28 executed**、XML **98,753 B @19:23:03**、`<issue>` **78** = Warning **46** + Information **32** / **Error 0**，
本轮三个新符号（`DetailSlot` / `detailSlotOf` / `RouteDomainDeepLinkTest`）**0 点名**。⚠ **与 D-049 那份「同样 98,753 B、但记的是 79 条」字节数相同而分项不同**
（46+32=78，D-049 那格的三个数本身不自洽）⇒ 旧报告已被本轮覆盖、无从比对，那 1 条差**跨轮不可归因**，**不写成改善**。

解释器自产物：`tools/__pycache__/` 本轮**没有新增 .pyc**（三个文件 mtime 分别 10-01 09:28 / 10-01 10:52 / 10-02 10:56）。
⚠ **补登一条 ⑤ 段的漏项**：`check_ledger_tables.cpython-313.pyc` **5,184 B / 10-02 10:56** —— ⑤ 那段写"本轮没有新增（仍两个）"时它是 10:2x 的真实状态，
10:56 那次台账自检又编译了一次，那句话因此是**写早了的话**；按"历史行不回改"只在当下更正，⑤ 段原句不动。


## F-3 ⑦⑨（DRIVER 逐按钮对真后端 + 任务点统一模型，2026-10-02 23:0x–2026-10-03 02:2x）新增自写文件 **1** 个 —— **第三方源码引入 0 行 / 新增依赖 0 个 / 许可证义务 0 条**

| 文件 | 行 / B | mtime | 进不进 APK | 署名义务 |
| --- | --- | --- | --- | --- |
| `app/src/test/java/com/shanxiang/buspost/ui/driver/DriverActionHonestyContractTest.kt` | 231 / 12,736 | 01:51:03 | 否（test 源集，16 份 dex 按描述符实测 **0 命中 = ABSENT**） | 无（自写用例；只用既有 JUnit + MockWebServer + OkHttp 依赖，未引入任何新构件） |

**§1 本轮不补登、不改表**：声明侧坐标实测仍 **20** 条（`grep -oE '"[a-zA-Z0-9_.-]+:[a-zA-Z0-9_.-]+:[^"]*"' app/build.gradle.kts` 再 `wc -l`，02:4x 现读），
`app/build.gradle.kts` **11,008 B / 09-30 00:40:50**、`gradle.properties` **920 B / 09-29 02:29:42** 一字未动 ⇒ 本轮**没碰构建脚本**，
APK 里第三方 aar/jar 的集合与 F-2 ⑮ / F-3 ⑤ / F-3 ⑥ 那三遍**同一份**；本轮实测凭证：`tools/oss_credits_negative_controls.sh` 批后单本补跑 **rc=0 / 命中=19 / 失效=0 / SUMMARY 破口=13 反向=2 预检=4**（`/tmp/f86_negctl_deferred.log`，`RESULT=PASS`）。

本轮改动既有源码 **2 个 main**（`data/model/Models.kt` 2,065 / 106,787 @02:00:51、`ui/driver/WorkbenchScreen.kt` 1,247 / 58,376 @02:00:51）
+ **1 个既有 test**（`app/src/test/java/com/shanxiang/buspost/driver/DriverStateTest.kt` 227 / 10,719 @00:24:25，补 `揽件单类型决定作业要求` 里那两条跨域断言）
+ **4 本 tools**（`check_idempotency_probes.sh` 273 / 25,730 @02:24:04、`idempotency_negative_controls.sh` 260 / 20,035 @01:52:43、
`idempotency_test_negative_controls.sh` 217 / 13,869 @02:01:10、`v21_requirement_census.sh` 162 / 11,023 @23:26:09）。
⚠ **改动面按内容证据取，不按 mtime**（D-050 §4 那条坑第二次遇到）：`ActionGate.kt`(02:00:50)、`Repo.kt`/`AppDomain.kt`/`AppState.kt`(01:20:47)、
`Routes.kt`/`ShellDecision.kt`(01:28:39)、`NavigationEngine.kt`(01:15:59)、`DriverExceptionScreen.kt`/`DriverExceptionReport.kt`(01:03:27) 的 mtime
全部落在**就地型负控的还原时刻**；其中 `ActionGate.kt`、`Repo.kt`、`Routes.kt`、`ShellDecision.kt` 等**本轮没有内容改动** ⇒ 只靠 `find -newermt` 会把它们误列进改动面（工程无 git，这几格只能靠"动作记录 + 判据点名"）。
换行符：新簿与四本 tools 全部 **LF**（`tr -cd '\r' | wc -c` 现读 = **0**，行数与 `wc -l` 一致）；被改的两个 main 文件改后 CR 仍为 **0**（各自原样，**没有混行尾**）。

本轮 APK 侧读数（`assembleDebug + testDebugUnitTest + lintDebug` **同一条命令** rc=0 / **53/53 executed**，打包产物 mtime 02:12:08）：**113,895,828 B**、
sha256 `fbe111b1ffff4a2a8407e0bd29aa108160ce93f965ecd29804e442122441ff0e`、`classes*.dex` **16** 个、打包类(defined) **29,828**、
引用类型 **36,677**（去重 31,894）。⚠ **这一格的 defined 与 D-050 逐项相同、且归因得清**：本轮 main 源集**没有新增类型**（唯一的新 `.kt` 在 test 源集，
`Models.kt`/`WorkbenchScreen.kt` 都只改函数体）⇒ defined **不该变**，实测确实没变（这与 D-050 那轮"defined +3 只归因到 2/3"是相反方向的一格，#140 的历史那一半继续在册）。
描述符点名（`unzip` 出 16 份 dex 后按字节扫，`/tmp/f86_dex_census.log`）：`Lcom/shanxiang/buspost/ui/driver/DriverActionHonestyContractTest;` **0（ABSENT，符合 test 源集预期）**、
`…WorkbenchViewModel;` **3**、`…PickupTask;` **8**（`classes3`×4 / `classes4`×3 / `classes12`×1）。
lint XML **98,753 B @02:13:17**、**issue 元素 78** = Warning **46** + Information **32** / **Error 0**，本轮新符号 **0 点名**。
⚠ **更正一笔跨账（历史行不回改，只在此登记）**：D-049 记 "79 issues"、D-050 记 "78 与 79 不自洽 ⇒ 跨轮不可归因" —— 本轮对同一份报告实测三种计数式：
`str.count('<issue')` = **79**、`re.findall(r'<issue[\s>]')` = **78**、`severity=` 分项 **46 + 32 = 78** ⇒ 那个 79 是**把根标签 `<issues …>` 数了进去的计数式错**，
两轮的条数**本来就一致**（都是 78），那 1 条差**不是**少了 1 条 issue。口径从此固定为：**只数 `issue` 元素，且要求分项和等于总数**（`DECISION_LOG.md` D-051 §7 + `CONTEXT_LOCK.md` §八 ㉚ ⑦）。
树指纹 **files=161 sha256=`969caf6371c0b6ac8550a0071d100cadc37180113649d9cd856bdb08122a1cb4`**（160→161 = 本轮唯一那个新文件，归因完整）。

解释器自产物：`tools/__pycache__/` 本轮**没有新增 `.pyc`**（02:4x 现读仍三个：`runcatching_detector.cpython-313.pyc` 12,263 B / 10-01 09:28、
`apk_oss_census.cpython-313.pyc` 11,471 B / 10-01 10:52、`check_ledger_tables.cpython-313.pyc` 5,184 B / 10-02 10:56 —— 本轮三本工具都是**按脚本直接执行**，
不产生自身 `.pyc`；`apk_oss_census.py` 那遍读的是终值包，也没有重编译）。

## F-3 ⑧（任务地图无路线态收口，2026-10-03 02:5x–05:3x）新增自写文件 **1** 个（test 源集）+ tools **3** 本 —— **第三方源码引入 0 行 / 新增依赖 0 个 / 新增许可证条目 0 行**

| 文件 | 行 / B | mtime | 进不进 APK | 署名义务 |
| --- | --- | --- | --- | --- |
| `app/src/test/java/com/shanxiang/buspost/ui/driver/MissionRouteStateContractTest.kt` | 252 / 12,173 | 03:15:16 | 否（test 源集；终值包 16 份 dex 按描述符点名 `Lcom/shanxiang/buspost/ui/driver/MissionRouteStateContractTest;` 实测 **0 = ABSENT**，`/tmp/f8_dex_fp.log`） | 无（自写用例；只用既有 JUnit + MockWebServer + OkHttp，未引入任何新构件） |
| `tools/check_mission_route_probes.sh` | 176 / 16,994 | 03:30:49 | 否（不在打包面内） | 无（自写判据簿，MR-a…MR-k） |
| `tools/mission_route_negative_controls.sh` | 276 / 11,655 | 03:31:38 | 否 | 无（自写结构负控，27 条注入） |
| `tools/mission_route_test_negative_controls.sh` | 168 / 10,489 | 05:01:17 | 否 | 无（自写行为负控，MT-1/MT-2/MT-3） |

**§1 本轮不补登、不改表**：声明侧坐标实测仍 **20** 条（`grep -oE '"[a-zA-Z0-9_.-]+:[a-zA-Z0-9_.-]+:[^"]*"' app/build.gradle.kts \| wc -l`，05:5x 现读），
`app/build.gradle.kts` **11,008 B / 09-30 00:40:50**、`gradle.properties` **920 B / 09-29 02:29:42** 一字未动（`/tmp/f8_hardrules.log` @03:39:58）⇒ 本轮**没碰构建脚本**，
APK 里第三方 aar/jar 的集合与 F-2 ⑮ / F-3 ⑤ / F-3 ⑥ / F-3 ⑦⑨ 那四遍**同一份**；本轮实测凭证：`tools/oss_credits_negative_controls.sh` 在终值链内（05:03–05:13 窗口）**rc=0 / 末行 `RESULT=PASS`**
（⚠ 同一本在 03:35:30→04:37:36 那个全批里是 **rc=2**，前置未满足：真身没有 test-results XML ⇒ 台账**不得**写"全批 21 本全绿"，只能写"批内 rc=2、终值单本 rc=0"）。

本轮改动既有源码 **4 个 main**（`ui/driver/MissionMarkers.kt` 172 / 9,076、`ui/driver/DriverMissionMap.kt` 189 / 8,428、`ui/common/MapCanvas.kt` 251 / 10,640、`ui/driver/WorkbenchScreen.kt` 1,298 / 61,812）
+ **既有 test 改动 0 本**（`app/src/test/java/com/shanxiang/buspost/ui/driver/MissionMapTest.kt` 259 / 12,792 / mtime 仍 09-29 05:31:21，`cmp` 与 `/tmp/f8_bak/` 那份**一字相同** ⇒ 不列进改动面）。
⚠ **改动面按内容证据取，不按 mtime**（D-050 §4 那条坑**第三次**遇到）：`MissionMarkers.kt` 与 `WorkbenchScreen.kt` 现在的 mtime 是 **05:03:16**，那是行为负控 MT 就地还原的时刻，
不是本轮写源码的时刻（真实写入时刻见 `/tmp/f8_file_facts.log`：03:15:39 与 03:14:23）；同一批里 `Models.kt`/`Repo.kt`/`Routes.kt`/`ShellDecision.kt`/`ActionGate.kt`/`NavigationEngine.kt`/`DriverExceptionScreen.kt`
的 mtime 全部落在**结构负控的还原时刻**（03:56–04:32）而**本轮没有内容改动** ⇒ 只靠 `find -newermt` 会把它们误列进改动面（工程无 git，这几格只能靠"动作记录 + 备份 `diff`"）。
逐文件净增删（`diff` 现数，备份在 `/tmp/f8_bak/`）：**`MissionMarkers.kt` +35 −0（一条删除行都没有）**、`DriverMissionMap.kt` +7 −4、`MapCanvas.kt` +2 −1、`WorkbenchScreen.kt` +66 −15；
字节侧同向：6,299→9,076 / 8,242→8,428 / 10,469→10,640 / 58,376→61,812。
换行符：新用例与三本 tools 全部 **LF**（`tr -cd '\r' | wc -c` = **0**）；被改的四个 main 文件改后 CR 与各自原样一致 —— 三个 **0**，
`MapCanvas.kt` 仍是 **CR=251 = 整文件 CRLF**（本轮**没有**把它换成 LF，也没有造出混合行尾）。

本轮 APK 侧读数（终值链 05:01:48→05:13:40）：盘上产物 **114,199,320 B @04:50**、sha256 `4dfe63621aed10cf801a272b55e75dd157005da4c423fe600b1721c45f43f80b`、
`classes*.dex` **16** 个、打包类(defined) **29,829**、引用类型 **36,680**（去重 **31,895**）（`/tmp/f8_apk_lint.log`、`/tmp/f87_dex_census.log`）。
⚠ **05:13 那遍 `assembleDebug` 是 `48 actionable tasks: 1 executed, 47 up-to-date`** ⇒ 这个 APK 就是 04:50 那个字节对象，**不是**"重打一遍得到同一份"的证明（#140 的历史那一半继续在册）。
⚠ **defined 与 D-051 逐项不同（29,828 → 29,829，+1；referenced +3；去重 +1），而本轮 main 源集同样"只改函数体、没有新增顶层类型"** ⇒
D-051 那句"没有新增类型 ⇒ defined 不该变"的推断**被本轮自己的实测证伪**（登记 **`D-⑧-1`**）。能核到的只有**族**：`MissionMarkers` 只有 **2** 个描述符（`MissionMarkers` + `…$ordered$$inlined$sortedBy$1`，**没有 `MissionMarkersKt` facade**），
`WorkbenchScreenKt$WorkbenchScreen$5$$ExternalSyntheticLambda0…16` 一族现数 **17** 个合成壳 ⇒ +1 极可能是新构起的 lambda/合成壳，但**没有改动前的类清单可比**，**不写成"就是那个壳"**；
本轮起把 **1,353** 个 app 描述符清单归档成 `/tmp/f87_dex_classlist.txt` 作下一轮基线（⚠ 它在 `/tmp`，不在工程里 —— 下一轮开工先把它抄进台账，否则这格还是关不掉）。
其余点名：`…DriverMissionMapKt;` **2**、`…WorkbenchScreenKt;` **2**（`classes3`）—— 顶层 `@Composable` 的文件 facade 带 `Kt` 后缀，扫裸名 `DriverMissionMap;` 会得 **0** 并误判成"没打进包"。
lint XML **98,753 B @04:51:22**、**issue 元素 78** = Warning **46** + Information **32** / **Error 0**，本轮新簿与三个改动文件在报告里 **0 点名**；
⚠ 同一份文件再数一遍 `str.count('<issue')` 仍给 **79** —— §八 ㉚ ⑦ 已定性为**把根标签 `<issues …>` 数进去的计数式错**（口径固定为"只数 `issue` 元素且要求分项和等于总数"），本轮照原样复现、**不改历史数**。
树指纹 **files=162 sha256=`42f5bbf946818654c31574ec311ef61289ea79732b7b58b5aa3cfbbd441b8fd5`**（161→162 = 本轮唯一那个新源文件，归因完整；**03:39 与 05:13 两次同值** ⇒ 27 条结构注入 + MT 三条 + 批次那 19 本的就地改写全部逐字节复原）。
⚠ **指纹扫描面 = `app/src` 内容**（`tools/`、`docs/` 不在里面）⇒ 这个值**不能**用来证明本轮那三本 tools 没动，那三本另有上面的行/字节/mtime 账（§八 ㉛ ⑧）。

## F-3 ⑬⑭⑯⑰（导航契约 / 路线绑定 / 缓存指纹 + 两件工具改动，2026-10-03 06:2x–14:2x）新增自写文件 **4** 本（全在 `tools/`，**0** 个进 APK）—— **第三方源码引入 0 行 / 新增依赖 0 个 / 新增许可证条目 0 行**

| 文件 | 行 / B | mtime | 进不进 APK | 署名义务 |
| --- | --- | --- | --- | --- |
| `tools/check_doc_anchor_probes.sh` | 100 / 7,596 | 14:16:53 | 否（判据簿不在打包面内） | 无（自写，DA-a…DA-f 六格）。⚠ **这格是本轮最后一次改动之后的数**：D-053 §4 那行记的是 98 / 7,296，差的 2 行是把这本簿收尾句里写死的那串本轮实测值（anchors=1216、越界 0、ID-MISS 只升不降）换成**打印本次真实读数**——不改就是台账每加一条锚点、这本簿多说一次假话；改后复跑 rc=0 且负控单本复跑 ✔=8，见 D-053 §8 |
| `tools/doc_anchor_negative_controls.sh` | 103 / 5,871 | 13:49:11 | 否 | 无（自写结构负控：8 条注入 + 1 条 POS 正控制）。批内 ✔=8 / ✘=0 / △=0；上面那次收尾句改动后又单本复跑一遍，✔=8、`RESULT=PASS`、两遍读数 anchors=**1239** / ID-MISS=**286** 同值 |
| `tools/fixtures/doc_anchor_pins.txt` | 264 / 22,383 | 11:32:55 | 否（夹具，不是代码） | 无 —— ⚠ **整文件 CRLF：CR=264 = 行数**，是 `--pin-write` 在 Windows 下写出的行尾，不是手写。"当前可用"是实测的（在册 262 / 新增 0 / 消失 0 就落在这份 CRLF 夹具上），**但为什么可用没证**（可能是 `splitlines()` 把行尾的 CR 吃掉、CR 没进 aid，也可能是别的）⇒ 登记 `D-⑰-1`，本轮**不改行尾**（改了那份可比物就没了） |
| `tools/route_alias_test_negative_controls.sh` | 152 / 8,042 | 10:49:10 | 否 | 无（自写就地型行为负控 RA-1…RA-5，逐键撤读；每条都带 `cmp` 复原 + `[恢复后] gradle exit=0`） |

**本轮 0 新增 `.kt`**（⑭ 的绑定形状落在既有 `core/navigation/NavigationEngine.kt` 与 `AmapNavigationEngine.kt` 两本里，行为用例落在既有 `NavigationContractTest`，⑬⑯ 只把解析层别名名单收窄）⇒ "新增自写源文件"这一格为 **0**。⚠ 但 APK 里 `com/shanxiang/buspost/**` 描述符 **1,353 → 1,358**（md5 `80d4a600b2d7d3657f229e4b0b69f6cb` → `19f4bf8e9493e7ba184a7b4c65c8e1ed`），**出的 0 条、进的 5 条**逐名对上 ⑭ 那五个类型（`AcceptedRouteSession` / `SdkPathShape` / `RouteBinding` 及其 `Bound`、`Unbound`）⇒ **"没有新文件但有新类型"是正常形状**，不许读成"漏登记了一个文件"；`D-⑧-1` 因此关掉，方法学是**先用 04:50 那个旧包重导一遍清单并与 D-052 那份比 md5，等式对上才去动新包**（提取器自己先要能复现基线）。

**§1 本轮不补登、不改表**：声明侧坐标实测仍 **20** 条（对 `app/build.gradle.kts` 的坐标字面量计数，14:23:16 现读），`app/build.gradle.kts` **11,008 B / 09-30 00:40:50**、`gradle.properties` **920 B / 09-29 02:29:42** 一字未动 ⇒ 本轮**没碰构建脚本**，APK 内第三方 aar/jar 集合与 F-2 ⑮ / F-3 ⑤ / ⑥ / ⑦⑨ / ⑧ 那五遍**同一份**；OSM/ODbL 那笔既有署名债本轮**未增未减**。
⚠ **这一段的凭证不是"整批全绿"**：负控批在册 **24** 本、批内 rc=0 **22** 本，`notify_result`、`oss_credits` 两本批内 **rc=2**（前置未满足：批跑时 test-results XML 还没生成），两本在终值树上**单本补跑 rc=0**（命中 **16** / **19**、失效 **0**；行首 ✔ 计数分别 **17** / **20** ⇒ "命中"与"✔ 行数"是两种数，不可互抄）。
本轮**改动**的既有自写工具（不产生新条目，只记改动面）：`tools/default_value_census.py` 314 / 15,627、`tools/check_doc_anchors.py` 269 / 14,481、`tools/check_runcatching_probes.sh` 106 / 9,134、`tools/runcatching_negative_controls.sh` 209 / 12,479、`tools/check_default_value_probes.sh` 364 / 33,200、`tools/v21_requirement_census.sh` 162 / 11,023、`tools/navigation_test_negative_controls.sh` 255 / 12,323 —— 逐本 CR=**0**（14:23:16 现数，按字节数 CR，**不是**用 `grep -c` 数 CR；后者对本仓所有纯 LF 文件都会报出"等于行数"的假数）。
解释器自产物：`tools/__pycache__/` 本轮**没有新增 `.pyc`**（14:23 现读仍 3 个：`runcatching_detector.cpython-313.pyc` 12,263 B / 10-01 09:28、`apk_oss_census.cpython-313.pyc` 11,471 B / 10-01 10:52、`check_ledger_tables.cpython-313.pyc` 5,184 B / 10-02 10:56 —— 本轮跑的三本 `.py` 都是**按脚本直接执行**，Python 不为命令行入口脚本写 `.pyc`）。
⚠ **本文件自身的行尾前置读数**（上一轮写这本时工具静默抹掉过第 510 行行尾那个**孤立 CR 字节**）：本轮写这一段**前** CR=**1**、写**后** CR=**1**，那一处孤立 CR 仍在第 510 行；行数由 **597 → 615**。本段是**纯 LF 追加**（临时文件 + `os.replace`，没有就地重写）。


## F-3 ⑱⑳（测试套件卫生判据 + 点名的 10 本测试类逐本归位，2026-10-04 16:2x–17:2x）新增自写文件 **2** 本（全在 `tools/`，**0** 个进 APK）—— **第三方源码引入 0 行 / 新增依赖 0 个 / 新增许可证条目 0 行**

| 文件 | 行 / B | mtime | 进不进 APK | 署名义务 |
| --- | --- | --- | --- | --- |
| `tools/check_test_suite_hygiene_probes.sh` | 92 / 6,900 | 16:56:43 | 否（判据簿不在打包面内） | 无（自写，SU-a…SU-e 五格，末行「测试套件卫生判据通过」）。⚠ 同轮把口径三件套一起现读（17:2x）：`ls tools/check_*.sh` = **23**、`ls tools/check_*probes.sh` = **22**、`ls tools/*_negative_controls.sh` = **25**、`ls tools/*negative_controls*.sh` = **26**（最后一条多吞批次脚本本身）⇒ 四个数各测各的、不可互引；本册让 `check_*.sh` 由 22 → **23** |
| `tools/test_suite_hygiene_negative_controls.sh` | 117 / 5,665 | 16:57:12 | 否 | 无（自写结构负控 P1…P7 七条破口，实测 ✔=7 / ✘=0 / △=0 / rc=0）。⚠ **这是本轮唯一跑过的一条负控**，在册 25 本全批未复跑 —— 理由与性质见下面「负控面」那格：那是**有理由的未做**，不是绿灯，也不构成下轮可以不跑的先例 |

**本轮 0 新增 `.kt`**（点名的 10 本测试类**一本都没新建**，逐本落到既有具名用例，归位表见 `DECISION_LOG.md` D-054 §2）⇒ 「新增自写源文件」这一格为 **0**。旁证两支：`app/src` 里 `*.kt` 现读 **134** 本、test 源集 **49** 本，`find app/src -name '*.kt' -newermt '2026-10-04 00:00'` 命中 **0** —— ⚠ mtime 只当旁证，主凭证是下面那条树指纹。
⚠ **包级第二证**：`com/shanxiang/buspost/**` 描述符现导 **1,358 条 / md5 `19f4bf8e9493e7ba184a7b4c65c8e1ed`**，与上一轮落在 `/tmp/f150_classlist_new.txt` 的那份 **diff 0 行**（同一条提取器、16 份 dex 按字节扫）⇒ 本轮**既没有新文件、也没有新类型**。上一轮那格是「0 个新文件但 5 个新类型」，本轮两支都是 0 —— 两种形状都必须由描述符清单实测得出，不许由「我没加文件」这句意图得出。

**§1 本轮不补登、不改表**：声明侧坐标实测仍 **20** 条（`grep -oE '"[a-zA-Z0-9_.-]+:[a-zA-Z0-9_.-]+:[^"]*"' app/build.gradle.kts` 再数行数，17:2x 现读），`app/build.gradle.kts` **11,008 B / 09-30 00:40:50**、`gradle.properties` **920 B / 09-29 02:29:42** 一字未动 ⇒ 本轮**没碰构建脚本**。
⚠ **这一轮「第三方集合未变」第一次拿到包级逐字节证据**：`app/build/outputs/apk/debug/app-debug.apk` **113,895,828 B @16:53:06 / sha256 `a00e1f930404a8960f2e984f1904aa8d064ed12542236bd03ff2273c28b46721`**，与 D-053 终值那格**同字节、同 sha256**（前几轮这一格只能写「集合同一份」的推断，本轮是两个哈希逐字符相同）⇒ APK 内第三方 aar/jar/dex 集合与 ⑬⑭⑯⑰ 那一遍**同一份**，`tools/apk_oss_census.py` 与 OS-* 矩阵本轮**无须重跑**——这句「无须」由哈希相同支撑，不由意图支撑；OSM/ODbL 那笔既有署名债**未增未减**。

⚠ **负控面：本轮只跑新册那一条，在册 25 本全批未复跑**，登记为**有理由的未做**。理由是可复现的内容证据，不是 mtime、也不是「本轮改动面与判据面不相交」的推断：`app/src` 树指纹（D-045 §2 那段 python 逐字复用）开工第一读与本轮最后一读逐字同串 —— `files=162 sha256=42863d1c941598a2578f942893f9ae0e38ec614cf11e1fef5506f8129cfe7422`，且与 D-053 终值同串。负控的注入锚点是源码**文本**，「文本逐字节相同」这件事由该哈希证明。**这不是先例**：下一轮只要 `app/src` 动一个字节，全批必须复跑。

本轮**没有改动任何既有自写工具**（`find tools -maxdepth 1 -type f -newermt '2026-10-03 15:00'` 现读只命中上面那两本新册与下面那份报告）⇒ 上一轮那张「本轮改动的既有工具」表本轮不列，空着比抄旧表诚实。
解释器与脚本自产物：**`tools/ledger_table_report.txt` 301 B @17:01:52**（`check_ledger_tables.py` 写出的当轮报告，非手写、无署名义务）；`tools/__pycache__/` 本轮**没有新增 `.pyc`**（17:2x 现读仍 3 个：`runcatching_detector.cpython-313.pyc` 12,263 B / 10-01 09:28、`apk_oss_census.cpython-313.pyc` 11,471 B / 10-01 10:52、`check_ledger_tables.cpython-313.pyc` 5,184 B / 10-02 10:56 —— 本轮跑的 `.py` 都按脚本直接执行，Python 不为命令行入口脚本写 `.pyc`）；设备侧 **`docs/device-evidence/20261004-170406/` 三份文件**（`device-check.log` 55 B、`device-probe.log` 37 B、`summary.md` 372 B，均 17:04:07）—— 是 `verify_device.sh` 在 `DEVICE_UNAVAILABLE` 那一支写出的证据，非第三方构件、无署名义务。
⚠ **release 那一格与许可证无关，但要在这里说一句，免得上面「无须重跑」被读成「全绿」**：`./gradlew test`（双 variant、`--rerun-tasks`）rc=**1**，红在 `:app:testReleaseUnitTest` 的 **5** 例（逐条具名、根因与「账面 3 → 实测 5」的归因见 D-054 §5，新登记 `D-⑱-1`）；`testDebugUnitTest` 产物 47 份 XML / **476 例 / failures 0 / errors 0 / skipped 0**。本轮改的是**计数**不是行为，且这五条红全在本工程自写用例里，**0** 个第三方类参与 ⇒ 对本段的第三方判定无影响。
⚠ **本文件自身的行尾前置读数**（第 510 行行尾那个孤立 CR 字节曾被工具静默抹掉，上一轮已登记同款）：本轮写这一段**前** CR=**1**、写**后** CR=**1**，那一处孤立 CR 仍在第 510 行；行数由 **615 → 635**。本段是**纯 LF 追加**（临时文件 + `os.replace`，没有就地重写、没有先截断的写法）。


## 赛道⑨（N-2，2026-10-04 22:0x – 10-05 01:3x）新增自写文件 **0** 本 / 新增第三方构件 **0** / 声明坐标仍 **20** 条 —— 本轮只改既有文件，不产生新条目，只记改动面

| 本轮改动的文件（**全是既有文件**） | 行 / B / CR（本轮重量过） | mtime | 进不进 APK | 本轮在这一格里做了什么 |
| --- | --- | --- | --- | --- |
| `app/src/main/java/.../core/navigation/NavigationEngine.kt` | **525 / 24,782 / 0** | 00:29:49 ⚠ | 是 | 两张纯表（转向 **57** 行 / 播报类型 **6** 行）＋ 表外 `else -> null` ＋ 新类型 `NavigationVoice` ＋ 接口 `voice` 流 ＋ `startGpsNavi` 多一个入参 |
| `app/src/main/java/.../core/navigation/AmapNavigationEngine.kt` | **365 / 19,079 / 0** | 22:54:13 | 是 | 两个 `onGetNavigationText` 重载都转发（构造点 **2**）、`stopNavi` 清帧（**1**）、把语音开关交给 SDK |
| `app/src/main/java/.../ui/driver/DriverSdkNavCard.kt` | **334 / 16,518 / 0** | 23:02:19 | 是 | 订阅 `engine.voice` ＋ 转向/播报两格读数（**不扩 UI**：无新按钮、无新屏、无新路由） |
| `app/src/test/java/.../core/navigation/NavigationContractTest.kt` | **623 / 31,377 / 0** | 23:03:54 | 否（test 源集不进包） | 34→**37** 例，新增三例各钉一种坏事 |
| `tools/check_navigation_probes.sh` | **351 / 33,101 / 0** | 23:18:36 | 否（判据簿不在打包面内） | 新立 **N-y** 一组读数（目标值见 `DECISION_LOG.md` D-055 §4） |
| `tools/navigation_negative_controls.sh` | **375 / 20,145 / 0** | 23:23:04 | 否 | 新增 **13** 条结构注入（含「表外码编一个动作」「本机自己起 TTS」「停止导航不清帧」） |
| `tools/navigation_test_negative_controls.sh` | **290 / 14,119 / 0** | 23:20:17 | 否 | 新增行为注入 **M16 / M17 / M18** |
| `tools/check_runcatching_probes.sh` | **106 / 9,347 / 0** | 23:34:09 | 否 | **只重锚注入锚点**（RC-a `32/16/49/23`、RC-b2 `32/0/98`、RC-e `36/4`），**目标值一条没动** |
| `tools/runcatching_negative_controls.sh` | **216 / 13,145 / 0** | 23:21:30 | 否 | 同上：跨簿重锚后单本复跑 rc=0 / ✔=**20** |

⚠ **那个 00:29:49 不是内容写入时刻**：它是用例级负控脚本（头部 `NE=` 指向该文件、`trap` / `restore()` 从备份 `cp` 回真身）留下的**还原写回** ⇒ 这一列本轮只能证"这些文件被动过"，不能证"改成什么"；改动面的主凭证是 `app/src` 树指纹 `files=162 sha256=5fa87a2dd4c49657829e07936bcd8b79d993a473ac3354583ca38bd93537a772`（对上一轮 `42863d1c…` 同文件数、不同内容串 ⇒ **只改内容、没加文件**；真加了源文件 `files` 必须涨到 163）。

**第三方源码引入本轮 = 0 行**，但有一处必须写清归属：那两张表里的标识符与数值（`IconType` **57** 条 = `0..54` ＋ `65/66`、`NaviTTSType` **6** 条 = `1..6`）是**从高德专有 SDK 那颗 jar 的 `javap -constants` 输出逐条抄来的**（jar sha256 `9fa3cfb81ff7ab5712c36f0af2c3effd0281c573a052e0221f3d81358ba203cc`，与 D-053/D-054 引用的是**同一颗**，本轮没换依赖）。
⇒ 这**不是**"开源代码引入"，落点虽在自写文件里，许可证义务仍走上面 **§3**（高德专有）那一条；⚠ **换依赖那天这两张表必须重取**，不许沿用 —— 这条约束由判据 N-y 的两个读数（`57 / 6`）钉着，不靠注释。

**§1 本轮不补登、不改表**：声明坐标实测仍 **20** 条（对 `app/build.gradle.kts` 现数、去重后 20），`app/build.gradle.kts` 与 `gradle.properties` **一字未动** ⇒ 无新增依赖、无新增 `.so` / 字体 / 图标。

⚠ **本轮"第三方集合未变"这句话的证据来源换了，必须写明**：上一轮靠的是 APK **同字节**，而本轮 APK **变了**（**113,912,212 B @01:13:50 / sha256 `96d7904b4552e9cd2a0997f4aae94a1f4705b398a407d26ceca4d0e211c4128e`**，对上一包 `a00e1f93…` 为 **+16,384 B**）⇒ 同字节这条旁证不再可用，本轮改用**当前这一包**的矩阵读数：`bash tools/check_oss_credits_probes.sh` rc=0、`OS-d2 = 0/0/28`（矩阵 ↔ census **零对不上**）、`OS-e` 末项打包类 **29,835**。
⚠ **反面教训一并落档**：`com/shanxiang/buspost/**` 那份类描述符清单（本轮 **1,359 条 / md5 `849f8708fecd93a6c8c7c8b4532bc186`**）**不能**拿来当第三方证据 —— 它的作用域本身不含第三方，拿它对上一包 `comm` 出的 `com/amap`、`okhttp3`、`kotlin`、`androidx` 全是 **0/0**，那两个 0 的含义是「清单里没有这一族」，不是「第三方没变」（我第一次就是这么读错的，纠正后才有上面那句）。
这份清单本轮的正当用途只有一个：**dex defined 29,834 → 29,835 那 +1 逐条指到了 `NavigationVoice`（自写类型）**，且**入集里没有第三方描述符**。

解释器与脚本自产物：`tools/__pycache__/` 现读 **3** 份 `.pyc`（最新一份 2026-10-02 10:56:00，**早于**本轮 22:0x 起的窗口）⇒ 本轮**没有新增** `.pyc`；`tools/` 目录里比 2026-10-04 21:00 更新的文件**只有上面那 5 本册子**加 `tools/ledger_table_report.txt`（`check_ledger_tables.py` 写出的当轮报告，非手写、无署名义务）⇒ **本轮新建文件数 = 0**。

⚠ **本文件自身的行尾前置读数**（第 **510** 行行尾那个孤立 CR 字节曾被工具静默抹掉，前两轮已登记同款）：本轮写这一段**前** CR=**1**、LF 行数=**635**；写**后** CR=**1**、LF 行数=**665**，那一处孤立 CR 仍在第 **510** 行 ⇒ 本轮追加走「备份 ＋ 临时文件 ＋ `os.replace`」，CR 与行数**逐字节**数、断言通过才换入，未用文本模式整本重写。

## 赛道⑩（N-3，2026-10-05 09:2x – 12:2x）新增自写文件 **0** 本 / 新增第三方构件 **0** / 声明坐标仍 **20** 条 —— 本轮只改既有文件，不产生新条目，只记改动面

| 本轮改动的文件（**全是既有文件**） | 行 / B / 孤立 CR（本轮重量过，python 字节计数） | mtime | 进不进 APK | 本轮在这一格里做了什么 |
| --- | --- | --- | --- | --- |
| `app/src/main/java/.../core/navigation/NavigationEngine.kt` | **707 / 33,859 / 0** | 11:30:56 ⚠ | 是 | `LaneFrame` ＋ `LaneDisplay.Single／Groups／Cleared` ＋ 两张纯表（车道名字 **27** 行 / 话术 **22** 行，⚠ 两数不等是设计）＋ 两个 `else -> null` ＋ 唯一话术出口 `laneCellLabel`／`laneDisplayTextOf` ＋ 接口 `lane` 流 |
| `app/src/main/java/.../core/navigation/AmapNavigationEngine.kt` | **414 / 21,987 / 0** | 09:54:47 | 是 | **本轮唯一新增的那一条 SDK import**（`com.amap.api.navi.model.AMapLaneInfo`）＋ 两个 `showLaneInfo` 重载**都**接 ＋ `hideLaneInfo` 写 `Cleared` ＋ `stopNavi()` 清车道帧（**1** 处，`release()` 不清） |
| `app/src/main/java/.../ui/driver/DriverSdkNavCard.kt` | **343 / 17,225 / 0** | 09:57:19 | 是 | 订阅 `engine.lane` ＋ **1** 格车道读数（**不扩 UI**：无新按钮、无新屏、无新路由；开关关掉时那一格**不出现**） |
| `app/src/test/java/.../core/navigation/NavigationContractTest.kt` | **760 / 39,027 / 0** | 09:59:11 | 否（test 源集不进包） | 37→**45** 例，八例各钉一种坏事（表外编名字 / 硬翻那五条 / 按下标配对 / 没给≠没有 / 多组不替用户挑） |
| `tools/check_navigation_probes.sh` | **440 / 42,215 / 0** | 10:04:44 | 否（判据簿不在打包面内） | 新立 **N-z** 一组八行读数（目标值见 `DECISION_LOG.md` D-056 §4），并把 N-a 下限同轮抬到 **≥45**、N-j 白名单同轮加那**一个**名字（9→**10**） |
| `tools/navigation_negative_controls.sh` | **451 / 24,551 / 0** | 10:05:32 | 否 | 新增 **18** 条结构注入（含「表外码编一个车道名」「四格并成两格」「两个数组被按下标配对」「旧屏那台猜图形的表进正式面」） |
| `tools/navigation_test_negative_controls.sh` | **368 / 17,905 / 0** | 10:17:14 | 否 | 新增行为注入 **M19…M26**（八条，注入跑完 `cmp` 逐字节复原再取下一条基线） |
| `docs/android/RUNCATCHING_AUDIT.md` | **154 / 18,303 / 0** | 10:34:44 | 否 | **只重锚**（9 行行号 ＋ 顶部那句口径括注），**行数一字未增删、目标值一条没动** |
| `tools/runcatching_negative_controls.sh` | **216 / 13,145 / 0** | 10:34:44 | 否 | 同上：2 条注入字面串 ＋ 2 条描述重锚，重跑 rc=0 / ✔=**20** |

⚠ **那个 11:30:56 不是内容写入时刻**：它是行为负控脚本（头部指向该文件、`trap` / `restore()` 从备份 `cp` 回真身）留下的**还原写回**（同款上一轮记在 00:29:49 那一格）⇒ 这一列本轮只能证"这些文件被动过"，不能证"改成什么"；改动面的主凭证是 `app/src` 树指纹 **`files=162 sha256=7089591423e3a1d56ca320b2b6b7c9b8c98db3d45082128cbc9e86d6f290cb5d`**，且这一串本轮**现读了三次**（批次启动后 / 批次结束后 / 全量终值后）**三次同串**。

**第三方源码引入本轮 = 0 行**，但归属必须写清：车道两张表里的标识符与数值（`LaneAction` **27** 条 = `0..25` ＋ `LANE_ACTION_NULL = 255`，⚠ **不是 0..26**；话术表 **22** 条，`13/14/15/17/20` 那五个码本机解释不了故留空）是**从高德专有 SDK 那颗 jar 的 `javap -constants` 输出逐条抄来的**（jar 仍是**同一颗** navi-3dmap 10.0.600，50,991,398 B / sha256 `9fa3cfb81ff7ab5712c36f0af2c3effd0281c573a052e0221f3d81358ba203cc`，与上面 §3 与赛道⑨那一节同串）⇒ 这**不是**"开源代码引入"，落点虽在自写文件里，许可证义务仍走 **§3**（高德专有）那一条；⚠ **换依赖那天这两张表必须重取**，这条不靠注释，由 N-z 的两个读数（`27 / 22`）钉着。

**§1 本轮不补登、不改表**：声明坐标实测仍 **20** 条（对 `app/build.gradle.kts` 现数），`app/build.gradle.kts` 11,008 B @09-30 00:40:50、`gradle.properties` 920 B @09-29 02:29:42 **一字未动** ⇒ 无新增依赖、无新增 `.so` / 字体 / 图标；本轮**也没有**引入任何车道箭头图形、图标资源或字体（"画图形"需要一份码↔图形的对应关系，本机没有任何证据 ⇒ 不做，见 D-056 §9）。

⚠ **"第三方集合未变"这句话本轮靠什么证据**：本轮 APK **113,912,212 B @12:09:29 / sha256 `161f34abc383c6b8e23322e4136fa69e619f8cf03e6e412b621a34f42dda1736`** —— 与上一包**同字节（+0 B）而哈希不同**（上一包 `96d7904b…`），所以"同字节"这条旁证本轮**不成立**，改用**当前这一包逐项对**：`check_oss_credits_probes.sh` rc=0，`OS-d2 = 0/0/28`（矩阵↔census 零对不上）、`OS-e` 末项打包类 **29,842**（对的是**当前这一包**而不是上一轮的数）。⚠ 体积那一半**仍未归因**（不做差量实验就不写归因，`#140` / `D-⑧-1` 不闭环）。

**本轮新加的一手机制（替下一轮把证据留住）**：打完包立刻把上一版 APK 复制留档（现读 113,912,212 B），并把 `com/shanxiang/buspost/**` 那份类描述符清单落档（**1,366 条 / md5 `30c488351f942f5bdd7edba309872c96`**）⇒ 下一轮做类清单差集时不再被"上一版包被下一次 assemble 覆盖"打掉。本轮据此报的差集：**defined 29,835 → 29,842（+7）／referenced 36,690 → 36,703／去重 31,901 → 31,908**，出=**0** / 入=**7** 且七条**全部具名**（5 个新具名类型 ＋ 判据层文件门面那两个脱糖 lambda 壳）；⚠ 方法学验证在前：拿留存的上轮包重导 = **1,359 条 / md5 `849f8708fecd93a6c8c7c8b4532bc186`**，与赛道⑨那一节记的两级读数**逐字同串**，提取器才有资格报差集。上一节那条反面教训照抄不回：**这份清单的作用域不含第三方**，它只能证"入集里没有第三方描述符"，不能当第三方证据用。

解释器与脚本自产物：`tools/__pycache__/` 现读 **3** 份 `.pyc`（最新一份 2026-10-02 10:56:00，**早于**本轮 09:2x 起的窗口）⇒ 本轮**没有新增** `.pyc`；`tools/` 与 `docs/android/` 里比 2026-10-05 09:00 更新的文件**只有上面那 6 本册子**（三本导航判据/负控 ＋ 两本 runcatching ＋ 本核对表那一行）加本轮台账本身与 `tools/ledger_table_report.txt`（`check_ledger_tables.py` 写出的当轮报告，收工终检那一遍刷新）。

⚠ **本文件自身的行尾前置读数**（第 **510** 行那一处 CR 字节曾被工具静默抹掉，前两轮已登记同款；本轮现读它是**成对**出现的，孤立 CR=**0**、CRLF=**1**，位置在那一行的**文本内部**而不是行尾）：本轮写这一段**前** CR=**1**、LF 行数=**665**；写**后** CR=1、LF 行数=693，那一处 CR 仍在第 **510** 行 ⇒ 本轮追加走「整本备份 ＋ 命中次数守卫 ＋ 临时文件 ＋ `os.replace`」，**逐字节**数断言通过才换入，未用文本模式整本重写（这也是本轮把"行尾只准用字节计数、同一量不许混用两个量具"写进 `CONTEXT_LOCK.md` §八 ㉟ ⑨ 的同一条线）。
## 赛道⑪（N-4 / D-057，2026-10-05）偏航重算只能由高德的回调说 —— 依赖与归属这一格的答案是"没有变化"

**新增第三方构件 = 0、新增自写文件 = 0、依赖声明文件一字未动**：本轮 `app/src` 动 **5** 个文件、新增 **0** 个（树指纹现读 `files=162 sha256=919278aa538dadd8a6441ad2731d262073b35cd09dbc4e3c5fd59cd40609393b`，对上一轮记的 `70895914…` 是**同文件数、不同内容串** ⇒ 没加源文件）。`app/build.gradle.kts` 11,008 B @09-30 00:40:50、`gradle.properties` 920 B @09-29 02:29:42 **未动**，声明坐标实测仍 **20** 条；jar 仍是**同一颗** navi-3dmap 10.0.600，50,991,398 B / sha256 `9fa3cfb81ff7ab5712c36f0af2c3effd0281c573a052e0221f3d81358ba203cc`（与上面 §3、赛道⑨、赛道⑩ 那几节**逐字同串**）。⚠ 本轮**没有**引入新的 SDK 类型：两条重算广播与两条算路回执都挂在**已经在接的** `SimpleNaviListener` 上，所以许可证义务仍走 **§3**（高德专有）那一条，**不新增条目、不补登 §1**（§1 声明坐标本轮没动）。

**没有引入任何图形 / 字体 / 图标**：偏航与重算这一族本轮只出**文本读数**（五格：`null` / `Yaw` / `TrafficJam` / `EngineInitiatedSuccess(code)` / `EngineInitiatedFailure(code)`），没有偏航图标、没有新 asset —— "不扩 UI"与"没有证据就不做图形映射"在这里是同一条（和赛道⑩ 那两张表把 `13/14/15/17/20` 五个码留空同一个理由）。

**§1 本轮不补登、不改表的凭证**（从批次留档逐字现抄，不回忆）：`bash tools/check_oss_credits_probes.sh` **RESULT=PASS**，`OS-d2 = 0/0/28`（矩阵↔census 零对不上）、`OS-e` 末项打包类 **29,847**、`OS-f = 9/0/0`、`OS-f2 = 11111`。对上一节记的 **29,842** 恰好 **+5**，与本轮类清单差集"出 **0** / 入 **5**（五条全部具名）"是**两个独立量具给同一个增量** ⇒ 这一格不需要归因实验。

⚠ **"第三方集合未变"这句话本轮靠什么**：本轮 APK **113,945,900 B / sha256 `3cf3c1ed…`**，对留存的上轮包（113,912,212 B）**+33,688 B 未归因** ⇒ 体积那一半**仍不闭环**（`#140` / `D-⑧-1` 同族，不做差量实验就不写归因）。本轮能说出口的是"**当前这一包**逐项对得上"（上面 OS-* 那几个读数），不是"和上一包字节相同"。

**写这一段之前对 `THIRD_PARTY_NOTICES.md` 全文件的现读**：bytes=**93,689** / LF=**693** / CR=**1** / CRLF=**1**（**孤立 CR=0**）—— 那唯一的一个 CR 落在**第 510 行的文本内部**，是那句写进正文的 `b'\r\n'` 字面串落盘时被解释成真回车换行（转义少写一层）留下的，**既不是行尾、也不是孤立字节**（上一节"行尾前置读数"已经这么记过，本轮一开始没去读它，另两本台账因此抄进了"孤立 CR / CRLF=0 / 第 63,222 字节"三个错读数，已在 D-057 就地改正）。本轮写这一段**没有**归一行尾、**没有**动那对字节：读写全走二进制，追加走「整本备份 ＋ 基线守卫 ＋ 临时文件 ＋ `os.replace`」，并写后断言 CR 的字节下标仍是 56,318、且原文件全部字节仍是新文件的**前缀**。⚠ 写后的字节数与行数**不在这一行自报**（自指读数写完就变，这一族的教训见 D-056 / D-057），以收工终检那份现读为准。

## 赛道⑫⑬（N-5 / D-058，2026-10-05 15:5x – 00:55:53）3D 立体感这一格：新增自写文件 **3** 本 / 新增第三方构件 **0** / 声明坐标仍 **20** 条

| 文件 | 行 / B | mtime | 进不进 APK | 署名义务 |
| --- | --- | --- | --- | --- |
| `app/src/main/java/com/shanxiang/buspost/ui/common/Map3DVisual.kt` | 现读 **189 / 8,657**（LF，CR=0，sha256 前缀 `91904e0707d30015`） | 2026-10-06 **00:01:34** —— ⚠ 这是**行为负控就地还原后**的落盘时刻，不是"我改它"的时刻（mtime 整族作废，见下面第 18 行那句） | **是**（main 源集；终值包 dex 按描述符点名 `Lcom/shanxiang/buspost/ui/common/Map3DVisual;` 实测**命中 3**（`classes12.dex` 2 ＋ `classes3.dex` 1；口径 = 16 个 `.dex` 逐个读原始字节找描述符串，日志 `/tmp/n7l_dex_named.txt`）。同族另五个类型：`Marker3D;` **3** / `MarkerVisual;` **6** / `MarkerEmphasis;` **7** / `GpsFixState;` **7** / `GpsFixVisual;` **6** / `Map3DVisual$WhenMappings;` **2**） | **无第三方归属**：纯 JVM 视觉层，不 import 任何 Android／高德类型（`MR-l` 钉那五个 SDK 立体调用**只在 `BusMap` 一处**、BusMap 之外 **0**；`MR-r` 钉"视觉层 import 业务模型 = **0**"，反证 BusMap＋出口对照 **10** 处），只消费既有 Kotlin 标准库与本项目自己的 `LocationReportPolicy` |
| `app/src/test/java/com/shanxiang/buspost/ui/common/Map3DVisualContractTest.kt` | 现读 242 行 / 11,546 B / 16 条 `@Test`（首跑那一份 226 / 10,488，见 D-058 §4） | 2026-10-05 **21:47:25**（= 三条装饰断言改完、21:48 单类复跑之前那一次落盘） | 否（test 源集；同一口径点名 `Lcom/shanxiang/buspost/ui/common/Map3DVisualContractTest;` 实测 **0 = 真没进包**。⚠ 这个 0 只能从 `.dex` 原始字节读：包导出那份**类清单文本**是裸类名形态（不含 `L`／`;`），拿描述符去那个文件里数也是 0，但那是**口径差异不是证据** —— `/tmp/f87_dex_classlist.txt` 同形） | 无（自写用例；只用既有 JUnit，**未引入任何新构件**） |

**没有引入任何新的第三方构件，也没有引入任何模型／资产文件**：`app/build.gradle.kts` **226 行 / 11,008 B / mtime 2026-09-30 00:40:50 / sha256 前缀 `1a65667d76a63683`**、`gradle.properties` **13 行 / 920 B / mtime 2026-09-29 02:29:42 / `a7d8573167ee9880`** 一字未动（两个前缀与赛道⑪ 那节同串），声明坐标实测仍 **20** 条（`grep -oE '"[A-Za-z0-9._-]+:[A-Za-z0-9._-]+:[A-Za-z0-9._+-]+"' app/build.gradle.kts | sort -u` 现读 20，首 `androidx.activity:activity-compose:1.9.3`、含 `compose-bom:2024.12.01` … `mockwebserver:4.12.0`，与 D-057 同串）；jar 仍是**同一颗** navi-3dmap 10.0.600，**50,991,398 B** / sha256 `9fa3cfb81ff7ab5712c36f0af2c3effd0281c573a052e0221f3d81358ba203cc`（与上面 §3、赛道⑨、赛道⑩、赛道⑪ 那几节**逐字同串**，本轮**重新解包复算**过，不是引用上一节的抄录：**2,403 条目 / 1,855 个 `.class`** 同批现读，jar mtime 2026-09-30 00:41:44）。
⚠ **本轮特意没有走"3D 模型"这条路**：`GL3DModel` / `GL3DModelOptions` / `GLTFOverlayOptions` 在 jar 里**存在**（条目名命中 **2**；含 `GLTFOverlay` 一族共 **5** 条：`GLTFOverlayOptions` / `GLTFOverlayOptionsCreator` / `GL3DModelOptions` / `GLTFOverlay` / `GL3DModel`），但按"条目名"与"1,855 个 `.class` 字节里搜字符串"两条口径各自数过，`.obj` / `.glb` / `.gltf` / `.fbx` 全部命中 **0**（`/tmp/n5_gl3d_deepscan.txt`），而 `GL3DModelOptions.vertexData(List<Float>, List<Float>)` 要的是**裸顶点 + 法线数组** ⇒ 用它就等于**我自己造一台货车的几何**。所以：**没有新增 asset、没有新增模型坐标、`res/` 一个字节没动、§1 不补登、不新增许可证条目**。
本轮真正动用的 SDK 面只有五个已在同一颗 jar 里、且**逐个签名核对过**的调用：`MarkerOptions.perspective(boolean)` / `setFlat(boolean)` / `zIndex(float)` / `rotateAngle(float)` / `altitude(float)` ＋ `CameraUpdateFactory.changeTilt(float)` ＋ `UiSettings.setTiltGesturesEnabled(boolean)` ⇒ 归属仍走 **§3**（高德专有）那一条。⚠ 后三个是 **float** 不是 double，所以视觉层那两个字段定成了 `Float` —— 这不是风格选择，是签名的约束。

**新增第三本（判据面，不在打包面内）**：`tools/map3d_visual_negative_controls.sh` 现读 **278 行 / 18,066 B / CR=0 / sha256 前缀 `9d11900d880a2223`**，mtime 2026-10-05 **23:53:32** —— ⚠ 那正是本轮批次结束的那一刻 ⇒ 它**落在批次 glob 之后**，被 `run_all_negative_controls_batch.sh` 数进"共 26 本"（口径与赛道⑪ 那句"25 本不含本簿"不同，差异写进 D-058 §5，不合并成一句"全批都一样"）。

**改动既有文件（main 6 本 + tools 2 本，test 0 本）**：`ui/driver/MissionMarkers.kt` **223 / 11,683 / `0f5f50e83680f4fb`**、`ui/common/BusMap.kt` **262 / 11,147 / `72e5ac679d552f3c`**（`MapMarker` 那一格多了 `visual`；`kind/title/detail` 的语义一字未改，配色语义也没动）、`ui/driver/DriverMissionMap.kt` **208 / 9,509 / `eb3509858984e547`**、`ui/driver/WorkbenchScreen.kt` **1,313 / 62,813 / `0d648a51a0a537f3`**、`core/loc/Locator.kt` **229 / 11,786 / `8aad6a008456a5f0`**（CRLF，**CR=229**；`fun heading` 返回 `Double?`）、`driver/DriverLocationService.kt` **294 / 13,171 / `db403832764ec4c8`**（CR=294；朝向只在 `Running` 里存一份、断链清 `null`、**不进上报报文**）＋ `tools/check_mission_route_probes.sh`（**311 / 30,980 / `97b6a80621068038`**，MR-l…MR-r ＋15 行打印读数）＋ `tools/mission_route_negative_controls.sh`（**456 / 19,511 / `1bff69e74dd2de58`**，结构注入 27 → **49**）。
⚠ **本轮没有逐文件的"改前基线"**（起手只做整树指纹），所以改动面按**动作记录 + 判据簿的整行字面量**记账，不按 mtime：跑过负控批之后 mtime 整族作废（§八 ㊱ ⑧），本轮再次兑现 —— 负控之后 `Map3DVisual.kt` **8,657 B / `91904e0707d30015`** 与 `MissionMarkers.kt` **11,683 B / `0f5f50e83680f4fb`** 的落点指纹与批次之前**同一串**（`/tmp/n7l_filestats.txt` 00:26 现读），而它们的 mtime 已经变成 00:01:34 ⇒ 同一个文件、两个读数、只有一个有意义。整树指纹：终值链 `[1/8]` **164 文件 / `1b6d1ff3487cc6f3c516d679a84d154a26cc898a3103b00b86aee09010fc651e`**，批后两本补跑与结构簿单跑**之后各复量一次同串**（补跑那一格 00:2x 实测；结构簿单跑那一格见 D-058 §5）。

**§1 本轮不补登的凭证**（终值链现读，不回忆）：`bash tools/check_oss_credits_probes.sh` **rc=0 / 末两行 `SUMMARY|rows=7|…` ＋ `RESULT=PASS`**（00:07 落盘），`OS-d2 = 0/0/28`（矩阵↔census **正反对账都是 0 条对不上**，对上的 **28** 对）、`OS-e = 19/9/0/28/2/2/`**29854**（末项 = census 直接读 APK 的 **class_defs 总数**，脚本里 `OS-e` 那一行写明"不读任何自述"）、`OS-f = 9/0/0` ＋ `OS-f2 = 11111`（点名用例五条全在场）。⚠ 本轮**新增了一个 main 顶层类型** `Map3DVisual`（与 `MarkerVisual` / `MarkerEmphasis` / `GpsFixVisual` / `GpsFixState` 等同住一本文件）⇒ 打包类计数**必然会变**，这一格是"有理由的变"，与 D-⑧-1 那句"没新增类型却变了"不是同一件事。⚠ **但那个数本轮不做归因**：赛道⑪ 记的是 **29,847**，本轮 **29,854** = **+7**，与下面类清单差集"入 7"**同数**——这是一条**旁证**，不是归因（class_defs 是**整包**计数，任何一处依赖或内联变化都会动它）。

**APK 侧**：本轮包 **113,928,596 B** / sha256 前缀 `96c7849c6da30d7e…` @2026-10-06 **00:04:46**，对赛道⑪ 留存的 `113,945,900 B / sha256 3cf3c1ed…`（`n5_apk_prev_D057.apk`）**Δ = −17,304 B**；类清单 `app/build/outputs/apk/debug/app-debug.apk.classes.txt` **1,371 / md5 `7a022c22f9eeab4e57c8987e922840ac`** → **1,378 / `513f615714c1eca9f0822a7e8b1ca882`**，python **集合差**（⚠ 不是 `comm` —— 两边排序键不同那笔账见 §八 ㊱ ⑫）读得 **入 7 / 出 0**，入榜七条具名 `GpsFixState` / `GpsFixVisual` / `Map3DVisual` / `Map3DVisual$WhenMappings` / `Marker3D` / `MarkerEmphasis` / `MarkerVisual`（全部来自本轮那两本新文件之一）。⚠ **体积增量仍不归因**（`#140` / `D-⑧-1` 同族）：这是**连续第 4 个未归因读数**（⑨ +16,384 → ⑩ +0 而 sha 变 → ⑪ +33,688 → ⑫⑬ **−17,304**），**负数同样不许写成"优化"**：不做差量实验就不写"这 X KB 是那个类"。

**写这一段之前对 `THIRD_PARTY_NOTICES.md` 全文件的现读**：bytes=**97,126** / LF=**704** / CR=**1** / CRLF=**1**（**孤立 CR=0**，那个 CR 仍在第 510 行文本内部、字节下标 **56,318**）—— 本轮**没有**归一行尾、**没有**动那对字节；追加后断言原文件全部字节是新文件的**前缀**、且 CR 下标未变。⚠ 写后的字节数与行数**不在这一行自报**（自指读数写完就变，见 D-056 / D-057），以收工终检那份现读为准。

## 赛道⑭（N-6 / D-059，2026-10-06 07:52 第一份 ⑭ 侧现读判据 → 10:59:29 终值链结束）离线真实道路缓存与失败披露：新增自写文件 **3** 本 / 新增第三方构件 **0** / 声明坐标仍 **20** 条

| 文件 | 行 / B / 落点指纹 | 进不进 APK | 署名义务 |
| --- | --- | --- | --- |
| `app/src/main/java/com/shanxiang/buspost/data/repo/OfflineRouteDisclosure.kt` | 现读 **46 / 2,501**（LF，CR=0，sha256 前缀 `7ca3e0e4db591b46`） | **是**（main 源集；口径 = 16 个 `.dex` 逐个读原始字节找描述符串，`Lcom/shanxiang/buspost/data/repo/OfflineRouteDisclosureKt;` 命中 **2**，两处全在 `classes12.dex`） | **无第三方归属**：`internal` 纯 JVM 话术层，只有 `routeFailureTextOf` / `offlineCacheAgeText` 两个函数，**不 import 任何 Android／高德类型**；判据 `N-ab 披露读点/交出几何/三格各自一句/并句措辞 = 1/0/3/0` 里那个 **0** 就是这条界限（披露层拿不到 `MapPath` / `.polyline` / `Geometry`） |
| `app/src/test/java/com/shanxiang/buspost/data/repo/RouteOfflineCacheContractTest.kt` | 现读 **147 / 7,454**（LF，CR=0，`21ee4709b636fbba`）；行首四个空格缩进的 `@Test` 现读 **7** 条 | 否（test 源集；同一口径点名 `Lcom/shanxiang/buspost/data/repo/RouteOfflineCacheContractTest;` 实测 **0 = 真没进包**） | 无（自写用例；只用既有 JUnit，**未引入任何新构件**） |

⚠ **只有上面这两本在 APK 判定面上**，第三本不在——这句每轮都要重说，因为"登记了"和"打包了"是两件事。

**新增第三本（行为负控面，不在打包面内）**：`tools/offline_route_cache_negative_controls.sh` 现读 **270 行 / 16,790 B / CR=0 / sha256 前缀 `4e9b373ff9fa9a0e`**，**10** 条破口 B-1…B-10 逐条配对具名用例。⚠ **批次计数口径**：它在本轮批次（08:36:59 → 10:41:15）起跑**之前**就已落盘，于是 glob `tools/*_negative_controls.sh` 这轮数到 **27** 本、上轮 **26** 本。与赛道⑫⑬ 那节"本簿落在批次 glob **之后**才被数进 26 本"是**同一把 glob、相反方向**的两次发生 ⇒ 差异写在这里，**不合并成"全批都一样"**。

**改动既有文件（main 3 本 ＋ test 1 本 ＋ tools 3 本，全部 LF / CR=0）**：`core/map/RouteRepository.kt` **150 / 7,445 / `1d22989b07047efd`**（`RouteCacheRead` 三态与 `RouteCacheEntry` 就长在这本里，所以"新形状"不等于"新文件"）、`data/repo/Repo.kt` **1,355 / 70,595 / `29a8506bfc8da3ba`**（只在 `routeGuide()` 的失败支加一处披露调用点）、`core/map/RouteSource.kt` **74 / 3,067 / `062308fd4bda0c55`**（只改 `real` / `label` 的注释口径，枚举成员一字未增删）、`app/src/test/.../core/map/RealRoadRouteValidatorTest.kt` **234 / 11,107 / `052c3758386b2f20`**（16 → **18** 条）＋ `tools/check_navigation_probes.sh` **636 / 65,302 / `41fbca55938faefb`**（新增 NAB1…NAB24，打印 9 行；`N-x` 目标同轮由 `0/1` 改 `1/1`）、`tools/navigation_negative_controls.sh` **649 / 38,503 / `a321018d98ee59c0`**、`tools/check_test_suite_hygiene_probes.sh` **97 / 7,570 / `468acf79c8b3236c`**（`SU-b` 基数 50 → **51**，两个下限一个字没抬）。

**没有引入任何新的第三方构件，也没有新增 asset**：`app/build.gradle.kts` **226 行 / 11,008 B / sha256 前缀 `1a65667d76a63683`**、`gradle.properties` **13 行 / 920 B / `a7d8573167ee9880`** 一字未动（两个前缀本轮**重新现读**，与赛道⑨、⑩、⑪、⑫⑬ 各节同串，不是抄录）；声明坐标现读仍 **20** 条（命令 `grep -oE '"[A-Za-z0-9._-]+:[A-Za-z0-9._-]+:[A-Za-z0-9._+-]+"' app/build.gradle.kts` 去重后计数）；jar 仍是**同一颗** navi-3dmap 10.0.600，本轮重新从 Gradle 缓存取到并复算：**50,991,398 B** / sha256 `9fa3cfb81ff7ab5712c36f0af2c3effd0281c573a052e0221f3d81358ba203cc`（与上面 §3、赛道⑨、赛道⑩、赛道⑪、赛道⑫⑬ 那几节**逐字同串**）。⇒ **§1 声明表不补登、不新增许可证条目**（本轮新增第三方构件 = **0**）。

**§1 本轮不补登的凭证**（收工现读，不回忆）：`bash tools/check_oss_credits_probes.sh` **rc=0 / 末行 `RESULT=PASS`**，`OS-d2 = 0/0/28`（矩阵↔census 正反对账都是 **0** 条对不上、对上 **28** 对）、`OS-e = 19/9/0/28/2/2/`**29859**、`OS-f = 9/0/0` ＋ `OS-f2 = 11111`（点名用例五条全在场）；行为那本补跑 `SUMMARY|book=oss_credits|破口=13 反向=2 预检=4|命中=19 失效=0` ＋ `RESULT=PASS`（10:59:28，`n8_oss_rerun.log`）。⚠ 本轮**新增了 main 侧类型**（一个文件门面类 `OfflineRouteDisclosureKt` ＋ `RouteCacheEntry` ＋ `RouteCacheRead` 及其三个子型）⇒ 打包类计数**必然会变**，这一格是"有理由的变"。赛道⑫⑬ 记 **29,854** → 本轮现读 **29,859** = **+5**，与下面类清单集合差"入 **6** / 出 **1**"的净增量**同数**——⚠ 这是**旁证不是归因**（class_defs 是**整包**计数，任何一处依赖或内联变化都会动它）。

**APK 侧**：本轮包 **113,928,596 B** / sha256 `3bfa396a3a7069adf4546c22557f50adf9b7e776bc184559df81a8ca0e0afee8` @2026-10-06 **10:49:09**，对**起手前 `cp -p` 留档**的 D-058 包（**113,928,596 B** / `96c7849c6da30d7e70db9a6fa4edb9a3ae69b98f53698697b3202b35852d7958` @00:04:46）**Δ = +0 而 sha256 变了**。⚠ **同字节数 ≠ 同内容**：这是**连续第 5 个未归因读数**（⑨ +16,384 → ⑩ +0 而 sha 变 → ⑪ +33,688 → ⑫⑬ −17,304 → ⑭ +0 而 sha 变），**"字节数一样"不许读成"包没变"**，"包小了/没变"同样不许读成优化——不做差量实验就不写归因（`#140` / `D-⑧-1` 同族，仍未闭）。类清单（全类名**描述符**口径、python **集合差**，不是 `comm`）：旧包 16 个 dex / `defined_app` **1,378** / 清单 md5 `513f615714c1eca9f0822a7e8b1ca882`，新包 16 个 dex / **1,383** / `422d5a531abce287bf68053e32ef3918` ⇒ **入 6 / 出 1**；入榜具名 `core/map/RouteCacheEntry`、`core/map/RouteCacheRead`、`RouteCacheRead$Absent`、`RouteCacheRead$Hit`、`RouteCacheRead$Stale`、`data/repo/OfflineRouteDisclosureKt`；出榜具名 `core/map/RouteRepository$CachedRoute`。⚠ **本轮第一次两半边证据同时成立**：出榜那一条在**上轮**类清单里现读命中 **1**、在**本轮包的 `.dex` 原始字节**里同口径命中 **0**（同脚本同时点名 `RouteCacheRead` / `$Hit` / `$Stale` / `OfflineRouteDisclosureKt` 全部非 0，所以这个 0 不是扫描面写错）⇒ "三态把 `CachedRoute` 顶掉了"这一句不再只靠差集单条腿。

**写这一段之前对 `THIRD_PARTY_NOTICES.md` 全文件的现读**：bytes=**106,403** / LF=**726** / CR=**1** / CRLF=**1**（**孤立 CR=0**）——那唯一的一个 CR 仍在**第 510 行的文本内部**、字节下标 **56,318**，是历史某轮把 `b'\r\n'` 字面串写进正文时落盘少转义一层留下的，**既不是行尾、也不是孤立字节**（D-057 / D-058 两节已把这条纠过一次，本轮照旧不复述错读数）。本轮写这一段**没有**归一行尾、**没有**动那对字节：读走 `newline=''`、追加走「整本备份 ＋ 基线守卫 ＋ 临时文件 ＋ `os.replace`」，并写后断言①原文件全部字节仍是新文件的**前缀**、②CR 的字节下标仍是 **56,318**、③新增内容里 **CR=0**。⚠ 写后的字节数与行数**不在这一行自报**（自指读数写完就变，见 D-056 / D-057），以收工终检那份现读为准。

## 赛道⑮（N-7 / D-060，2026-10-06 11:4x 取证 → 11:5x gradle 终读）新增自写文件 **0** 本 / 新增第三方构件 **0** 个 / **新增许可证条目 0 行** —— 本轮一行代码都没引入，所以本节不产生任何许可义务；它的作用是把 §5 那四行"计划复用但尚未引入"里 MapLibre / Valhalla 那两问**用测量回答掉**

### 1. 本轮没引入任何东西（所以 §1 / §2 / §5 的表都没改）

| 检查 | 实测 | 口径 |
|---|---|---|
| `app/build.gradle.kts` 是否出现 MapLibre / Valhalla / GraphHopper | **0** 处（`grep -ci` 整文件） | 现读 |
| 依赖声明行数 | **26** 行：`implementation` **22** ＋ `testImplementation` **4**（另有 1 行 `val composeBom = platform(…)` 是 BOM 赋值，不算声明行） | python 正则逐行 |
| ⚠ **历史台账那句"声明坐标仍 20 条"本轮复现失败** | 三种口径都得不到 20：§1 表数据行 **15**、§1＋§2 含表头 **21**、gradle 声明行 **26** ⇒ 登记为**不可比读数**，**不改历史行**，本轮起新行只写 26 并注明口径 | 同上 |
| APK 内 `org/maplibre/` 打包类 | **0**（矩阵行维持 `REFERENCE_ONLY`，本轮结束时再量一次仍为 0） | dex 类清单 |
| 本机 gradle 缓存里这三个厂商的构件 | 本轮**之前** 148 个 group 中 **0** 个；本轮之后 gl 13.6.1 与 nav 5.0.0 各 **1** 份**只落在 Temp 取证目录**，未进 `~/.gradle` 的项目依赖图 | 目录计数 |

⇒ §6 那句"新增/升级任何依赖，或从上面第 5 节真正复制了代码，必须同步本文件，否则视为未完成该轮"的**反向适用**：本轮没新增，就不写；
写了反而是假登记。**§5 那张表一字未动**，它下面补的是"这两行能不能做"的测量结论，不是新增条目。

### 2. §5 那四行的可行性，本轮量到的答案

| §5 项目 | 本轮实测 | verdict |
|---|---|---|
| **MapLibre GL Native/Android**（BSD-3-Clause + 字体/瓦片条款） | 坐标 `org.maplibre.gl:android-sdk`，Central **107** 版（9.2.1→**13.6.1**）；本工程镜像（aliyun public）**19/19 路径 200** 拉得到；`.aar`，minSdk **23**；`libmaplibre.so` **4 ABI**（单个 9.4–13.4 MB，raw ≈ 48.9 MB）；清单带 `<uses-feature android:name="android.hardware.vulkan.version" android:required="true">` | **不可引入**（不是许可问题，是编译问题）：Kotlin 2.0.21 对它 **`error` + rc=1** ——`actual metadata version is 2.2.0, but the compiler version 2.0.0 can read versions up to 2.1.0`。压制它需要 `-Xskip-metadata-version-check`，属本指令禁止的"放宽校验" |
| **MapLibre Navigation Android**（MIT） | `org.maplibre.navigation:navigation-core-android` **16** 版（5.0.0…5.0.0-pre14）；AAR 仅 **467,686 B**，但**其 pom 把 gl 钉在 13.5.0 = 197,373,956 B**（13.6.1 只有 18,451,147 B，差 **10.7 倍**）；minSdk **23**；元数据 **2.4.0** ⇒ 同一台编译器 `unresolved reference 'JsonKt'`，类都看不见 | **不可引入**（同上，且门槛更高：要 ≥2.4 才有元数据可读） |
| **Valhalla Mobile**（MIT） | **Maven Central 无任何 valhalla 路由构件**：全文检索 numFound=**2** 且两条都无关，`org.valhalla` / `com.valhalla` / `io.valhalla` 三前缀 **404** | **无构件可取证**。"设备端 Valhalla"意味着自带 NDK 原生构建＋离线数据，本轮不猜、不写代码 |
| **GraphHopper Navigation Example**（MIT，仅参考） | Central 检索**两次 curl exit 28（超时），一次都没成功** | **未测出** —— 这一格只许写"没量到"，既不写"没有"也不写"有" |

**duplicate class 这一问的答案是"不撞"**（与 §3 高德的专有关系不冲突）：gl **534** ＋ nav **228** 类全部在 `org/maplibre/` 下，
高德 `com.amap.api:navi-3dmap` 的 **1,855** 类在 `com/amap`（1,363）＋ `com/autonavi`（459）＋ `com/alibaba`（33），
**python 集合差 = 0 个同名类**；原生库侧 MapLibre 只有 `libmaplibre.so`，高德那 6 个 `.so` 无同名 soname。
⚠ 但这只排除"duplicate class"这一种炸法，排除不了上面那条**元数据门槛**，也排除不了下面这一格：

**§5 那句"Valhalla 第一阶段只留 `OfflineRoutingEngine` 接口"从未落地**：全仓 grep `OfflineRoutingEngine` = **3** 处，
**全部**在本文件与其两份备份（`backup/ledger-20261001-1215/`、`THIRD_PARTY_NOTICES.md.bak-20260930-013956`）里，`app/` `docs/` `tools/` **0** 处。
⇒ 本轮**不补写这个接口**（为不引入的实现造一个空接口 = 把"还没接"写成"有位置"），只把这句的真值如实登记为 `PRODUCER_NOT_FOUND`。

### 3. 许可证侧的三条硬结论（引入仍要守的规矩，未变）

1. **§101 / §103 那三条件本轮一次都没触发**：许可证兼容、出处登记、版权保留 —— 前提是有代码进来，本轮 **0 行**。
2. **MapLibre 是 BSD-3-Clause，与本工程 MIT 不冲突**；⚠ 真正的许可负担在**数据**那一侧不在代码：底图瓦片/字体/OSM 道路网各有条款，
   而本工程 §4 已登记的 OSM/ODbL 署名入口（`check_oss_credits_probes.sh` 的 OS-d2 / OS-e / OS-f）目前只对**现有**来源负责。
   **换渲染器 = 换署名对象**，那一格要等真引入时同轮做，本轮不预判。
3. **高德 §3 的专有地位不因"备用方案存在"而改变**：`N-i = 1/0/0` 维持，`navi-3dmap` 顶替 `3dmap` 的关系一字未动。

### 4. 复现命令（本轮全部数字的来源，Temp 一次性目录，不碰仓库）

```bash
cd %TEMP%/n7 && bash run_kc.sh          # 打印 GL_RC=1 / NAV_RC=1，正文见 kc_run2.log
# minSdk / Vulkan 那两格：unzip x_*/*.aar && cat AndroidManifest.xml   （⚠ 不是二进制 AXML，头 8 字节 3c3f786d6c = "<?xml ve"）
# aapt2 直接读 AAR 会报 `failed to deserialize proto AndroidManifest.xml` rc=1 —— 那只说明调用方式，不说明内容不可读
```
## L-1／㊼（D-068，2026-10-09 05:01:18 判据全批起跑 → 05:05:32 终值）到近三档声道分层 ＋ 布局走查工具进环：新增自写文件 **7** 本 / 新增第三方构件 **0** 个 / 声明坐标 **20** 条（本轮现读复现）/ **新增许可证条目 0 行**

### 1. 新增自写文件（main **3** ＋ test **2** ＋ tools **2**；全部 LF / CR=0 现读）

| 文件 | 行 / B / 落点指纹 | 进不进 APK | 署名义务 |
| --- | --- | --- | --- |
| `app/src/main/java/com/shanxiang/buspost/ui/driver/MissionApproach.kt` | 现读 **134 / 5,793**（CR=0，sha256 前缀 `fad505cd6d1bcb8d`） | **是**（main 源集；口径 = 本轮交付包的 **16** 个 `.dex` 逐个读原始字节找描述符串，命中 **5** 格：`MissionApproach;` **2** ＋ `ApproachTracker;` **2** ＋ `ApproachThresholds;` **4** ＋ `DistanceSource;` **7** ＋ `ApproachTier;` **5**，**五格全在 `classes3.dex`**） | **无第三方归属**：纯 JVM 阈值／相位／句子层，**不 import `Context`、不 import `android.speech.tts`** —— 这条界限就是本轮分层要买的东西，判据 `N-ac 开关话术出口(纯层)/页面调用点/骨架在纯层/在页面 = 1/3/1/0` 里那个 **1** 与末尾的 **0** 合起来就是它 |
| `app/src/main/java/com/shanxiang/buspost/ui/driver/ApproachFeed.kt` | 现读 **76 / 3,964**（CR=0，`19062838c977281f`） | **是**（`ApproachFeed;` **2** ＋ `ApproachFrame;` **4** ＋ `ApproachFrame$Ready;` **2** ＋ `ApproachFrame$Silent;` **2**，同 in `classes3.dex`） | 无（只把既有引擎帧与既有任务点模型拼成一支帧，**未引入任何新构件**） |
| `app/src/main/java/com/shanxiang/buspost/ui/driver/ApproachAnnouncer.kt` | 现读 **119 / 5,125**（CR=0，`ad25a54c309b7f4e`） | **是**（`ApproachAnnouncer;` **2** ＋ `AnnounceChannel;` **3**，同 in `classes3.dex`）⚠ 那个 **3** 是"声明＋枚举符号"的计数，不是"有人在读"：`AnnounceChannel` 全仓 grep 只命中**定义那一行**、**0** 个读取点，于是**打包面上带着一格死声明**。已登记 `#314`（为什么不顺手删：交付包与 **26** 张设备读数都挂在同一版源码上，代价见 D-068 §2 —— **这是权衡不是遗漏**） | 无新增第三方：用的三个出口全是**已有依赖或系统 API**（`android.speech.tts.TextToSpeech`＝系统、`androidx.core.app.NotificationManagerCompat`＝§1 已登的 `androidx.core:core-ktx:1.15.0`、`java.util.Locale`＝JDK），所以**这一本不产生新的许可条目** |
| `app/src/test/java/com/shanxiang/buspost/ui/driver/MissionApproachContractTest.kt` | 现读 **142 / 7,448**（CR=0，`bf6b66aa8ba84bbb`）；行首四个空格缩进的 `@Test` 现读 **11** 条 | 否（同一口径点名 `…MissionApproachContractTest;` 实测 **0** ⇒ 真没进包） | 无（自写用例，只用既有 JUnit） |
| `app/src/test/java/com/shanxiang/buspost/ui/driver/ApproachFeedContractTest.kt` | 现读 **179 / 8,922**（CR=0，`ce10781aec812a92`）；`@Test` 现读 **11** 条 | 否（同口径实测 **0**） | 无（同上） |
| `tools/layout_audit.py` | 现读 **153 / 4,881**（CR=0，`7379eb9d84f729f1`） | **否**（不在 `app/src`，不参与打包） | 无（自写脚本，只用 Python 标准库） |
| `tools/ui_capture.sh` | 现读 **27 / 1,475**（CR=0，`c20ac8e25e6a50f4`） | **否**（同上） | 无（自写脚本；调的是本机 `adb` 可执行文件，**不随包分发**） |

⚠ **"登记了"和"打包了"仍是两件事，这句每轮重说**：上面 **7** 本里只有前 **3** 本在 APK 判定面上（后 **2** 本 test 实测 **0**、后 **2** 本 tools 不参与打包）。
**新增资源 0 个**：本轮 **7** 本新文件全是 `.kt` / `.py` / `.sh`，`res/` 与 `assets/` **没有新落点** ⇒ 不涉及图片/字体/瓦片那一侧的署名对象变化（赛道⑮ 那句"换渲染器＝换署名对象"本轮没有被触发）。

### 2. 改动的既有文件（main **13** ＋ tools **8**，本轮**没有**新增任何一本）

`core/ui/BusTheme.kt` **186 / 7,353 / `9dba847011c37faf`**、`core/ui/Components.kt` **751 / 29,329 / `1399287933972bb5`**、`core/ui/Primitives.kt` **511 / 21,065 / `6ac3ddd0937d10b4`**、`data/model/Models.kt` **2,156 / 113,893 / `cd06f9c9acfafa16`**、`data/repo/DemoData.kt` **869 / 55,191 / `0420b5e797ba5794`**、`data/repo/Repo.kt` **1,354 / 70,961 / `1bff0d99c9f590fe`**、`data/store/Prefs.kt` **197 / 7,748 / `2fb146f1586deb0c`**、`ui/common/WeatherCard.kt` **220 / 9,321 / `3778b7d9cd5a09dd`**、`ui/driver/DriverSdkNavCard.kt` **387 / 20,602 / `7acc82517b222441`**、`ui/driver/WorkbenchScreen.kt` **1,434 / 71,028 / `67daf2a6ac6248ef`**、`ui/user/BusScreen.kt` **670 / 34,178 / `73a732d4855e8c5e`**、`ui/user/HomeScreen.kt` **1,015 / 47,462 / `931fc5a8a6b587d3`**、`ui/user/MinePages.kt` **966 / 43,334 / `aaaf4eceebe668f1`**；tools：`check_location_probes.sh` **118 / 9,618 / `91b4cb7a9f706a90`**、`check_navigation_probes.sh` **700 / 73,129 / `8ff0c0b8a7b712de`**、`check_mission_route_probes.sh` **316 / 31,635 / `5e2f0d9c27187039`**、`check_test_suite_hygiene_probes.sh` **108 / 8,865 / `c703ec5124f98ec3`**、`check_doc_anchor_probes.sh` **107 / 8,632 / `f8852ca78c0b7eb1`**、`navigation_negative_controls.sh` **760 / 48,315 / `f6383771195e6f93`**、`mission_route_negative_controls.sh` **459 / 19,897 / `fe8853ce7e2f4988`**、`fixtures/doc_anchor_pins.txt` **254 / 21,599 / `dd8b0ffb864536b6`**。

⚠ **行尾口径本轮要说准，不许写"全部 LF"**：上面 **21** 本里 **18** 本 CR=0，**3** 本是**整文件 CRLF** —— `DemoData.kt` CR/LF = **869/869**、`Prefs.kt` **197/197**、`MinePages.kt` **966/966**（CR 与 LF **逐行配对** ⇒ **孤立 CR=0**，与赛道⑤ 那几节记的同族既有形状一致，本轮没有把谁换的行尾）。`tools/fixtures/doc_anchor_pins.txt` 同理（CR=**254** / LF=**254**，本轮**重写**过它：锚点重锚那 **18** 处，见 D-068 §4 的 `DA` 行）。

### 3. 依赖与构件侧：本轮 **0 个新增**，但历史那两串指纹已过期（如实登记）

**本轮改动面里没有 `app/build.gradle.kts`、也没有 `gradle.properties`**（这两本的 mtime 现读 **2026-10-06 20:27** 与 **2026-10-07 22:08**，都**早于**本轮尺子窗口 `'2026-10-08 22:30'` ⇒ 不是本轮动的）。⚠ 但它们的**现读指纹与赛道⑭ 那一节记的两串不一致**：

| 文件 | 赛道⑭（2026-10-06 10:5x）登记 | 本轮现读 | verdict |
|---|---|---|---|
| `app/build.gradle.kts` | **226 行 / 11,008 B / `1a65667d76a63683`** | **227 行 / 11,134 B / `c4549608682f2d4d`** | **历史读数已过期**，差额 **＋1 行 / ＋126 B**；本轮**没有**归因（改动落在 D-067 之前的联调段，本机无 git、无留档副本可比对）⇒ 与 `#140` 同一族"未归因"，**不写成回归、也不写成"没问题"** |
| `gradle.properties` | **13 行 / 920 B / `a7d8573167ee9880`** | **16 行 / 994 B / `7e42cf47ac2d97eb`** | 同上：**＋3 行 / ＋74 B 未归因** |

**坐标数这一格本轮两种口径都复现了**（不是"复现失败"那一格的重复）：
- 版本化坐标去重 **20** 条：`grep -oE '"[A-Za-z0-9._-]+:[A-Za-z0-9._-]+:[A-Za-z0-9._+-]+"' app/build.gradle.kts` 去重计数 —— 与赛道⑭ 那句"声明坐标现读仍 **20** 条"**同串同数**；
- 声明行 **26** 行（`implementation` **22** ＋ `testImplementation` **4**；另 **1** 行 `val composeBom = platform(...)` 是 BOM 赋值不计） —— 与赛道⑮ 表里那行 **26** 同数。
⇒ 赛道⑮ 那句"三种口径都得不到 20"**本轮不再适用**：那两个数各自都按自己的口径复现，**不合并成一句"坐标是 N 条"**。⚠ 本轮坐标**一条没增、一条没减**（**20 → 20**），所以 **§1 声明表不补登、§2 不新增行**。

**jar 仍是同一颗**：`com.amap.api:navi-3dmap:10.0.600_3dmap10.0.600`（`app/build.gradle.kts` 的声明行本轮现读仍在，未被顶替），Gradle 缓存里那颗本轮**重新取到并复算**：**50,991,398 B** / sha256 `9fa3cfb81ff7ab5712c36f0af2c3effd0281c573a052e0221f3d81358ba203cc`（与 §3、赛道⑨、⑩、⑪、⑫⑬、⑭ 各节**逐字同串**）⇒ **新增第三方构件 = 0、新增许可证条目 = 0 行**。

### 4. §1 本轮不补登的凭证（收工现读，不回忆）

`bash tools/check_oss_credits_probes.sh` 于本轮判据全批内 **rc=0 / 31s / 末行 `RESULT=PASS`**，格值现读：`OS-a = 111/1/4`、`OS-b = 1/1/1/1/1/0`、`OS-c = 17/1/6/19`、`OS-d = 0/18/3/18/10/9`、**`OS-d2 = 0/0/28`**（矩阵↔census 正反对账都是 **0** 条对不上、对上 **28** 对）、**`OS-e = 19/9/0/28/2/2/29891`**、`OS-f = 9/0/0` ＋ **`OS-f2 = 11111`**（日志 `C:/temp/r69_pass.log` 第 **385–398** 行）。⚠ 打包类那一格 **29,891** 相对赛道⑭ 那格的 **29,859** 差 **+32**，但**这 +32 不是本轮增量**：中间跨了 ⑮…㊻（D-060…D-067）**六轮**改动，而 `class_defs` 是**整包**计数 ⇒ 这一格只许读成"跨轮累计、本轮未归因"，**不许**写成"L-1 三本类加了 32 个类"（本轮新增的具名类型一共只有 **11** 个描述符格，见上面第 **1** 节那 **5＋4＋2** 格）。

### 5. APK 侧（口径分开；本轮没做类清单集合差）

**本轮 canonical**：`app/build/outputs/apk/debug/app-debug.apk` **114,551,597 B** @2026-10-09 **02:43:47** / sha256 前缀 `86c8e8df78d2dc1c`（完整串见 D-068 §6）—— 上面第 **1** 节那 **5** 格描述符命中数就是**从这颗包**的 **16** 个 dex 里读出来的，两半证据同一颗包。
⚠ **26 张设备读数不挂在这颗包上**：它们来自联调包 **114,551,589 B** / `4fb22eec…`（差 **8 B**、是两个文件），第 **1** 节表格里"进不进 APK"那一列**只对 canonical 负责**。
**类清单集合差本轮未做**：D-067 那颗包（`d7d2d801` / **114,208,801 B**）已被覆盖、本机无 git ⇒ 无从导旧清单，所以本轮**不给**"入 N / 出 N"这一句；体积差 **+342,796 B** 按 `#140` / `D-⑧-1` 那一族登记为**「增量未归因」**，既不写成"新增了功能"，也不写成回归。

**写这一段之前对 `THIRD_PARTY_NOTICES.md` 全文件的现读**：bytes=**120,009** / LF=**796** / CR=**1** / CRLF=**1**（**孤立 CR=0**）—— 那唯一的一个 CR 仍在**第 510 行的文本内部**、字节下标 **56,318**（本轮**重新按下标读**，不是抄上一节的数），与赛道⑭ 那一节末尾那段的描述一致。本轮写这一段**没有**归一行尾、**没有**动那对字节：读走 `newline=''`、追加走「整本备份（`.r70bak/`）＋ 基线守卫（长度与 CR 下标）＋ 临时文件 ＋ `os.replace`」，写后断言①原文件全部字节仍是新文件的**前缀**、②CR 的字节下标仍是 **56,318**、③新增内容里 **CR=0**。⚠ 写后的字节数与行数**不在这一行自报**（自指读数写完就变，见 D-056 / D-057），以收工终检那份现读为准。
