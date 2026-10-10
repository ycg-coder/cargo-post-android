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
⚠ **必须点名的一条方法读数**：`ui/driver/WorkbenchScreen.kt:434-440` 的收法是**整圈 `runCatching` 仪式摘掉**，
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
**本轮该类一字未改** —— 只新增了一个实例），键摘要用的是 JDK 自带 `java.security.MessageDigest`（`Repo.kt:464-476`），
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
`ui/BusPostRoot.kt:131`、`ui/user/OssLicensesScreen.kt:29`），APK 内 `© OpenStreetMap contributors` 与 `ODbL` 两串各实测 **2 次**（dex；`resources.arsc` 0 次）。
⇒ 那笔欠账在**源码与字节**两侧都已消；但**界面是否真渲染出来仍是 `DEVICE_VERIFIED` 未到位**（⑪ 实测 0 台设备），这条不能顺带写成已验证。

本轮 APK 侧读数（`:app:assembleDebug` rc=0，实测 10:50:46）：**114,447,105 B**、`classes*.dex` **16** 个、打包类(defined) **29,816**、
引用类型 **36,632**（去重 31,878）。census 判定 **INCLUDED_IN_APP 19 前缀 / NOT_IN_APK 9 前缀**，9 条"期望 0"反证位实测全部 **0**。
构建产物**没有**引入任何新的第三方 aar/jar：体积增量来自本工程自己的源码与 dex 布局。

解释器自产物登记（不是源码、不参与编译、不进 APK；工程无 git，故登记不删）：
`tools/__pycache__/runcatching_detector.cpython-313.pyc` 12,263 B / 09:28、`tools/__pycache__/apk_oss_census.cpython-313.pyc` 11,471 B / 10:52。
