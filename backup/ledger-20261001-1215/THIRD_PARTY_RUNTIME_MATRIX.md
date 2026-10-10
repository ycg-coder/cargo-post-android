# 第三方运行时矩阵：Gradle 声明 ↔ APK 实测 ↔ 许可证（F-2 第 15 步）

判据 `tools/check_oss_credits_probes.sh`（OS-a…OS-f），行为面用例 `core/about/OpenSourceCreditsContractTest`，
测量脚本 `tools/apk_oss_census.py`，App 内入口见 `THIRD_PARTY_NOTICES.md` §4 与本轮新增的
`设置 → 关于 →「地图与开源许可」`（`ui/user/OssLicensesScreen.kt`）。

## 一、怎么测的（口径先说清，否则下一轮会把两个数互引）

| 环节 | 命令 / 来源 | 说了什么 | 没说什么 |
| --- | --- | --- | --- |
| Gradle dependency | `./gradlew :app:dependencies --configuration debugRuntimeClasspath`（留档 `$TEMP/f2_10_deps.txt`） | 声明与**冲突解析后**的版本（如 `okio:3.6.0 -> 3.9.0`） | 不等于进了 APK |
| Actual APK | `python tools/apk_oss_census.py app/build/outputs/apk/debug/app-debug.apk --strings` | 解析 16 个 `classes*.dex` 的 `class_defs` ⇒ **打包类** 29,816 个；`type_ids` 全表 ⇒ **引用类型** 36,632（去重 31,878） | 只测了 debug 变体（见第五节） |
| License | 构件自带 POM / 上游 LICENSE，已在 `THIRD_PARTY_NOTICES.md` 逐条留痕 | 许可证名与性质 | — |
| NOTICE | `THIRD_PARTY_NOTICES.md` 的节号 | 该组件在哪一节被登过 | — |

⚠ **两个数不能混**：`org.json` 在 dex 里 referenced=12 而 defined=0 —— 类是 Android 框架提供的，
那个 jar 只挂在 `testImplementation` 上，**没进包**。只看"引用到的类型"就会把它错写成 `INCLUDED_IN_APP`。
⚠ DEX 版本实测为 `dex\n038`：`string_id_item` 在这版里没有 `utf16_size` 字段（用段间距反推出宽度 = 4 字节），
照抄旧文档里的 8 字节会直接把解析跑出界（本轮踩过一次，脚本现在按 span 自适应并在推不出时 `exit 2`）。

## 二、`INCLUDED_IN_APP`：APK 里真有它的字节码

`Actual APK` 列的整数 = `class_defs` 里该包前缀下的**打包类数**（实测 2026-10-01 10:50 那次 `assembleDebug` 的产物，
APK = 114,447,105 字节；这是 ⑭ 补了署名入口**之后**重打的包，`OS-d2` 判据会把这张表的每一个数与 census 的当下读数逐个对齐）。

⚠ 这一节的 17 行 + 第三节那 1 行构成"进了包的第三方代码"全集；census 里另外 9 条前缀实测为 0，列在第四节。
名单里新增一条却忘了在这一节加行，`OS-d2` 会因为"矩阵有数没实测 / 实测有数没矩阵"而红。

| Component | Gradle dependency | Actual APK | License | NOTICE |
| --- | --- | --- | --- | --- |
| AndroidX Core | `androidx.core:core-ktx:1.15.0` | INCLUDED_IN_APP（`androidx/core/` 打包类 1,105） | Apache License 2.0 | §1 第 1 行 |
| AndroidX AppCompat | `androidx.appcompat:appcompat:1.7.0` | INCLUDED_IN_APP（`androidx/appcompat/` 462） | Apache License 2.0 | §1 第 2 行 |
| AndroidX Activity Compose | `androidx.activity:activity-compose:1.9.3` | INCLUDED_IN_APP（`androidx/activity/` 211） | Apache License 2.0 | §1 第 3 行 |
| Jetpack Compose（BOM） | `androidx.compose:compose-bom:2024.12.01` | INCLUDED_IN_APP（`androidx/compose/ui/` 2,337） | Apache License 2.0 | §1 第 4 行 |
| Compose Material 3 | `androidx.compose.material3:material3:1.3.1` | INCLUDED_IN_APP（`androidx/compose/material3/` 1,745） | Apache License 2.0 | §1 第 4 行 |
| Compose Material Icons Extended | `androidx.compose.material:material-icons-extended:1.7.6` | INCLUDED_IN_APP（`androidx/compose/material/icons/` 11,400） | Apache License 2.0 | §1 第 4 行 |
| AndroidX Lifecycle | `androidx.lifecycle:lifecycle-runtime-ktx:2.8.7` | INCLUDED_IN_APP（`androidx/lifecycle/` 277） | Apache License 2.0 | §1 第 5 行 |
| AndroidX Navigation Compose | `androidx.navigation:navigation-compose:2.8.5` | INCLUDED_IN_APP（`androidx/navigation/` 300） | Apache License 2.0 | §1 第 6 行 |
| AndroidX WorkManager | `androidx.work:work-runtime-ktx:2.9.1` | INCLUDED_IN_APP（`androidx/work/` 432） | Apache License 2.0 | §1 第 7 行（⑩ 的退避常量实测同源） |
| Kotlin Stdlib | `org.jetbrains.kotlin:kotlin-stdlib:2.0.21` | INCLUDED_IN_APP（`kotlin/` 1,000） | Apache License 2.0 | §1 第 8 行 |
| Kotlin Coroutines | `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0` | INCLUDED_IN_APP（`kotlinx/coroutines/` 877） | Apache License 2.0 | §1 第 9 行 |
| OkHttp | `com.squareup.okhttp3:okhttp:4.12.0` | INCLUDED_IN_APP（`okhttp3/` 319） | Apache License 2.0 | §1 第 10 行 |
| Okio（传递依赖） | `com.squareup.okio:okio:3.9.0` | INCLUDED_IN_APP（`okio/` 114） | Apache License 2.0 | §1 **本轮补登**（旧版漏登：它不在 `build.gradle.kts` 里，是 OkHttp 带进来的） |
| kotlinx-serialization-core（传递依赖） | `org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.3` | INCLUDED_IN_APP（`kotlinx/serialization/` 255） | Apache License 2.0 | §1 **本轮补登**（同上，传递依赖） |
| Coil | `io.coil-kt:coil-compose:2.7.0` | INCLUDED_IN_APP（`coil/` 340） | Apache License 2.0 | §1 第 11 行 |
| ZXing Core | `com.google.zxing:core:3.5.3` | INCLUDED_IN_APP（`com/google/zxing/` 322） | Apache License 2.0 | §1 第 12 行 |
| ZXing Android Embedded | `com.journeyapps:zxing-android-embedded:4.3.0` | INCLUDED_IN_APP（`com/journeyapps/` 78） | Apache License 2.0 | §1 第 13 行 |

## 三、随包分发但**不是开源组件**：不能并进第二节

| Component | Gradle dependency | Actual APK | License | NOTICE |
| --- | --- | --- | --- | --- |
| 高德地图导航 SDK | `com.amap.api:navi-3dmap:10.0.600_3dmap10.0.600` | INCLUDED_IN_APP（`com/amap/api/` 1,361，另有 `com/autonavi/` 459 —— 同一构件带进来的第二棵包树：ae 190 / base 174 / amap 37 等三段族） | The AMap Software License, Version 1.0 —— **专有软件**，非 OSI 开源许可 | §3 第 1 行 |

## 四、`REFERENCE_ONLY`：参考过 / 服务端在用 / 只在测试源集，APK 里没有它的字节码

这一节的每一条，census 的 `class_defs` 计数实测为 **0**。把这里的任何一行写进第二节，就是把"参考过"谎报成"分发过"。
"打包类 0"是 census 的当下读数（不是"推测它不该在"），所以每条都写了它对应的前缀；OS-d2 会双向对齐：矩阵写了前缀却没写 0，或 census 有 0 而矩阵没这行，都会红。

| Component | Gradle dependency | Actual APK | License | NOTICE |
| --- | --- | --- | --- | --- |
| GraphHopper（服务端算路引擎） | 无（不在端上依赖树里） | REFERENCE_ONLY（`com/graphhopper/` 打包类 0） | Apache License 2.0 | §4 第 2 行 |
| OSMDroid | 无 | REFERENCE_ONLY（`org/osmdroid/` 打包类 0） | Apache License 2.0 | §4（本轮实测补记：端上从未引入） |
| GDAL / proj.4 | 无 | REFERENCE_ONLY（`org/gdal/` 打包类 0） | MIT（proj）/ MDAL 系 | §4（同上） |
| OpenStreetMap 矢量数据 | 无（数据文件在服务端 `chongqing-260921.osm.pbf`） | REFERENCE_ONLY（**APK 不含 pbf/瓦片/离线地图包**；包里只有署名文案本身，实测见第五节第 4 条） | **ODbL 1.0** | §4 第 1 行 |
| cargo-post-platform 后端（芋道 yudao 骨架） | 无 | REFERENCE_ONLY（`org/springframework/` 打包类 0） | MIT（yudao 主仓声明） | §4 第 3 行 |
| JUnit | `junit:junit:4.13.2`（testImplementation） | REFERENCE_ONLY（`org/junit/` 打包类 0） | Eclipse Public License 1.0 | §2 第 1 行 |
| MockWebServer | `com.squareup.okhttp3:mockwebserver:4.12.0`（test） | REFERENCE_ONLY（`okhttp3/mockwebserver/` 打包类 0） | Apache License 2.0 | §2 第 2 行 |
| `org.json:json`（只为 JVM 单测提供 `org.json`） | `org.json:json:20240303`（test） | REFERENCE_ONLY（`org/json/` 打包类 0，而引用类型 12 —— 运行时由 Android 框架提供） | The JSON License（"AS IS"） | §2 第 3 行 |
| kotlinx-coroutines-test | `org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0`（test） | REFERENCE_ONLY（`kotlinx/coroutines/test/` 打包类 0） | Apache License 2.0 | §2 第 4 行 |
| MapLibre GL/Navigation、Valhalla、GraphHopper Navigation Example | 无 | REFERENCE_ONLY（`org/maplibre/` 打包类 0；Valhalla 与那份 Example 端上从未引入任何构件，靠 `:app:dependencies` 实测无该依赖，不另设前缀） | MIT / BSD-3-Clause / MIT | §5（**计划复用但尚未引入**） |

## 五、这份矩阵没覆盖到的（不许把它读成"全都验过了"）

1. **只测了 debug 变体**。release 变体一次都没解析成功：任何带 release 配置的 gradle 调用都在
   `app/build.gradle.kts:171` 的 HTTPS 闸门处失败（实测 `:app:dependencies --configuration releaseRuntimeClasspath`
   → `BUILD FAILED in 2s`，`What went wrong: release 包必须使用 HTTPS（当前 http://1.15.29.107/api）`，
   留档 `$TEMP/f2_10_release_gate_evidence.txt`）。指令禁止用 `-Pbuspost.allowInsecureRelease=true` 绕闸门，
   所以 release 的依赖解析与 APK 实测 = **NOT VERIFIED**，`RELEASE = BLOCKED`。
   debug 与 release 的 runtime 依赖声明是同一份 `dependencies {}`，但**这句话是推理不是实测**，两态分开写。
2. **没有真机**（⑪ 实测 `adb devices` 0 台）⇒ 第二节那 17 行说的是"字节码在包里"，
   不是"这些库在设备上跑通过"。`SOURCE_COMPLETE / BUILD_VERIFIED / DEVICE_VERIFIED` 三态里，这里只到 BUILD_VERIFIED。
3. **class_defs 计数按包前缀**，同名不同源的包（例如 `okhttp3/mockwebserver/` 会同时落在 `okhttp3/` 下）——
   脚本因此对每条 label 里带"期望 0"的前缀单独设反证位，一旦非 0 就 `exit 1`。
   ⚠ 反证位靠 **label 里的"期望 0"字样**来认，不靠硬编码包名清单：加了一条新前缀却忘了同步清单，
   那条"期望 0"就永远不会红（本轮加 `kotlinx/coroutines/test/` 时差点踩中，2026-10-01）。
4. **APK 里的署名文案实测已非 0**（⑭ 补丁之前那次构建实测 0/0，重打之后）：
   `© OpenStreetMap contributors`、`Open Database License`、`ODbL` 三串在 dex 里**各 2 次**，`resources.arsc` 里 0 次；
   `OpenStreetMap` 共 5 次。按 dex 分布实测为：定义 `OpenSourceCredits` 的 `classes16.dex` 3 次
   （署名 + `OSM_USAGE` + `referenceOnly` 里"OpenStreetMap 矢量数据…"那条），渲染它的 `classes12.dex` 2 次
   （`const val` 被内联到调用方各自带来的副本）。
   ⚠ 这只说明"字符串进了包"，**不说明"设备上渲染得出来"**——没有设备，Compose 屏这一半留在 DEVICE_VERIFIED，
   不许因为 census 数到了字节就把它写成设备可见。

## 六、这份矩阵不回答的那一半（⑯ 的落点，实测于 2026-10-01 11:2x）

这份矩阵只回答"**构件在不在包里**"。字段级问题（谁生产、谁解析、缺键落成什么、由哪本用例钉住）在
`docs/android/CONTRACT_EVIDENCE.md`。**写入本节时**（11:2x）该台账实测 117 行、6 个二级节，⑯ 点名的
notification envelope、handover photo、leg status 三组**当时还没有逐字段条目**（`grep -ic → envelope 0 / handover 0 / photo 0`；
`leg 1`，而那 1 处是 §一 表格里顺带提到的 VO 名 `AppDriverLegRespVO:62`，不是逐字段行）。
**该缺口已由同轮 §七 补上**，补完后的当下读数（11:48:11，`wc -l` 240 行、`grep -c '^## '` 7、`grep -c '^### '` 10）：
envelope 6 / handover 10 / photo 4 / leg 15。⚠ 那组"期望 0"是**本节写入时的一次性快照**，§七 落盘后已不可复现，
它不属于任何判据簿（`grep -rn 'CONTRACT_EVIDENCE' tools/` 无命中，只有本矩阵被 `check_oss_credits_probes.sh:26` 引用，
且该簿只读 `^\|` 表格行与表头，不读本节的叙述行），所以不会也不会被拿来当红线；要复现 0 只能把 §七 删掉，那是错的方向。
另：矩阵正文的每个数字都由 `OS-d2` 与当下 census 双向对齐（正/反 0/0，对上的对数 28），
所以这里的数是**量出来的**；而 §五 第 1 条说的那句"debug 与 release 共用同一份 `dependencies {}`"是**推理**，两态分开写。
本节与 §七 的分工不许混读：矩阵答"在不在包里"，台账答"这个键谁生产、缺了落成什么"，
**两边都不能拿对方的行当自己的证据**——字段级 `PRODUCER_NOT_FOUND` 不等于"字节码不在包里"，反之亦然。
