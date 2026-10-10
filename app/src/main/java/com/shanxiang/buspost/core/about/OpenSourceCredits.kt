package com.shanxiang.buspost.core.about

/**
 * 一条实际进 APK 的第三方组件。三列都不是抄来的：
 *  - [gradleCoordinate]：`./gradlew :app:dependencies --configuration debugRuntimeClasspath` 的解析结果（版本是解析后落定的那个）；
 *  - [inApkEvidence]：`tools/apk_oss_census.py` 对该 APK `classes*.dex` 的 `class_defs` 计数——**打包类 > 0** 才算内置；
 *  - [license]：构件自带 POM / 上游 LICENSE 复核，见 THIRD_PARTY_NOTICES.md。
 *
 * ⚠ 只在这里列"进了包的"。GraphHopper、OSMDroid、GDAL/proj、后端芋道都在 [referenceOnly] 里，
 *   写成内置就是把"参考过"谎报成"分发过"。
 */
data class OssLibrary(
    val name: String,
    val gradleCoordinate: String,
    val license: String,
    val inApkEvidence: String,
)

/**
 * 设置 → 关于乡邮智联 → 地图与开源许可 的数据源（指令 v2.1 第 ⑭ 步）。
 *
 * 单独放在 `core/about` 且**不依赖 Compose**，是因为这个界面在没有设备的机器上跑不了 Compose，
 * 而署名的三条文案与"到底进没进包"这件事必须能被 JVM 用例逐条钉住（`OpenSourceCreditsContractTest`）。
 */
object OpenSourceCredits {
    /** ⑭ 点名的三行 OSM 署名，一字不差地由界面渲染 */
    const val OSM_ATTRIBUTION = "© OpenStreetMap contributors"
    const val OSM_USAGE = "OpenStreetMap 数据用于道路/地图相关功能"
    const val OSM_DATA_LICENSE = "数据许可：Open Database License (ODbL)"

    /**
     * OSM 数据到屏幕上这条路：数据在服务端（`chongqing-260921.osm.pbf`），经 GraphHopper 算出真实道路折线，
     * 端上只画后端下发的折线。所以这句署名说的是**数据来源**，不是"App 里带了 OSM 瓦片"。
     */
    const val OSM_WHERE = "本机的道路折线来自后端下发的 OSM 数据算路结果，APK 内不含 OSM 瓦片或离线地图包"

    /** 实际打进 APK 的开源组件（Apache License 2.0 等），逐条有 class_defs 证据 */
    val included: List<OssLibrary> = listOf(
        OssLibrary("AndroidX Core", "androidx.core:core-ktx:1.15.0", "Apache License 2.0", "androidx/core/ 打包类"),
        OssLibrary("AndroidX AppCompat", "androidx.appcompat:appcompat:1.7.0", "Apache License 2.0", "androidx/appcompat/ 打包类"),
        OssLibrary("AndroidX Activity Compose", "androidx.activity:activity-compose:1.9.3", "Apache License 2.0", "androidx/activity/ 打包类"),
        OssLibrary("Jetpack Compose（BOM 2024.12.01）", "androidx.compose:compose-bom:2024.12.01", "Apache License 2.0", "androidx/compose/ui/ 打包类"),
        OssLibrary("Compose Material 3", "androidx.compose.material3:material3:1.3.1", "Apache License 2.0", "androidx/compose/material3/ 打包类"),
        OssLibrary("Compose Material Icons Extended", "androidx.compose.material:material-icons-extended:1.7.6", "Apache License 2.0", "androidx/compose/material/icons/ 打包类"),
        OssLibrary("AndroidX Lifecycle", "androidx.lifecycle:lifecycle-runtime-ktx:2.8.7", "Apache License 2.0", "androidx/lifecycle/ 打包类"),
        OssLibrary("AndroidX Navigation Compose", "androidx.navigation:navigation-compose:2.8.5", "Apache License 2.0", "androidx/navigation/ 打包类"),
        OssLibrary("AndroidX Work Manager", "androidx.work:work-runtime-ktx:2.9.1", "Apache License 2.0", "androidx/work/ 打包类"),
        OssLibrary("Kotlin Stdlib", "org.jetbrains.kotlin:kotlin-stdlib:2.0.21", "Apache License 2.0", "kotlin/ 打包类"),
        OssLibrary("Kotlin Coroutines", "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0", "Apache License 2.0", "kotlinx/coroutines/ 打包类"),
        OssLibrary("OkHttp", "com.squareup.okhttp3:okhttp:4.12.0", "Apache License 2.0", "okhttp3/ 打包类"),
        OssLibrary("Okio（OkHttp 传递依赖）", "com.squareup.okio:okio:3.9.0", "Apache License 2.0", "okio/ 打包类"),
        OssLibrary("kotlinx-serialization-core（传递依赖）", "org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.3", "Apache License 2.0", "kotlinx/serialization/ 打包类"),
        OssLibrary("Coil", "io.coil-kt:coil-compose:2.7.0", "Apache License 2.0", "coil/ 打包类"),
        OssLibrary("ZXing Core", "com.google.zxing:core:3.5.3", "Apache License 2.0", "com/google/zxing/ 打包类"),
        OssLibrary("ZXing Android Embedded", "com.journeyapps:zxing-android-embedded:4.3.0", "Apache License 2.0", "com/journeyapps/ 打包类"),
    )

    /**
     * 高德导航 SDK：随 APK 分发，但**不是开源组件**，不能混进上面的 OSS 名单里说一句 Apache 2.0 了事。
     * 许可证文本与"不得反向代理/不得掩码 key"这些约束见 THIRD_PARTY_NOTICES.md 第三节。
     */
    val proprietary: List<OssLibrary> = listOf(
        OssLibrary(
            "高德地图导航 SDK（navi-3dmap）",
            "com.amap.api:navi-3dmap:10.0.600_3dmap10.0.600",
            "The AMap Software License, Version 1.0（专有软件，非 OSI 开源许可）",
            "com/amap/api/ 打包类",
        ),
    )

    /** 参考过 / 服务端在用，但**不在 APK 里**——census 对这几条实测 defined=0 */
    val referenceOnly: List<String> = listOf(
        "GraphHopper（服务端算路引擎，Apache License 2.0）",
        "OSMDroid（未引入，com.android 侧无其字节码）",
        "GDAL / proj.4（数据处理链在服务端，未引入）",
        "OpenStreetMap 矢量数据（在服务端，随算路结果下发折线，APK 不含 pbf/瓦片）",
        "cargo-post-platform 后端（芋道 yudao 骨架，MIT；端上不复制其后端代码）",
        "JUnit / MockWebServer / org.json:json / kotlinx-coroutines-test（只在 test 源集，不进 APK）",
    )
}
