import java.net.URI
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

/**
 * 配置来源优先级（**源码工程里不再内置任何真实服务器地址**）：
 *
 *   1. `-Pbuspost.baseUrl=https://…`        —— CI（GitHub Actions secrets）注入
 *   2. 环境变量 `BUSPOST_BASE_URL`           —— 本地 / CI 均可
 *   3. `local.properties` 的 `buspost.baseUrl` —— 仅开发机，该文件已 gitignore
 *
 * 三者都没配时，debug 仍然能编译（BASE_URL 为空串，App 启动即报「未配置后端地址」），
 * release 则**直接构建失败**——正式包里带一个指向 127.0.0.1 的地址比编译失败危险得多。
 *
 * ⚠ 路径拼接口径：Repo 里的路径已带 `/app-api/...` 前缀，所以 baseUrl 只写到前缀为止。
 *   · 走 nginx：https://your-domain/api      （转发时剥掉 /api）
 *   · 直连后端：http://127.0.0.1:48080
 *   写成 https://your-domain/api/app-api 会拼出双重前缀 → 404。
 */
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun cfg(key: String): String? =
    (project.findProperty("buspost.$key") as String?)
        ?.takeIf { it.isNotBlank() }
        ?: System.getenv("BUSPOST_" + key.uppercase())?.takeIf { it.isNotBlank() }
        ?: localProps.getProperty("buspost.$key")?.takeIf { it.isNotBlank() }

/** 高德两类 key 沿用 local.properties 的裸键名（amap.key / amap.webKey），也接受 -P / 环境变量覆盖。 */
fun secret(key: String, env: String): String? =
    (project.findProperty(key) as String?)?.takeIf { it.isNotBlank() }
        ?: System.getenv(env)?.takeIf { it.isNotBlank() }
        ?: localProps.getProperty(key)?.takeIf { it.isNotBlank() }

/**
 * 发布版签名配置。⚠ 签名文件与口令**不允许放在源码工程内**（即便 gitignore 了也容易被误加）。
 * 默认从工程同级目录 `../buspost-signing-backup/keystore.properties` 读，
 * CI 上改用 `-Pbuspost.signingProps=/绝对路径/keystore.properties` 或环境变量 `BUSPOST_SIGNING_PROPS`
 * （配合把 jks 以 secret 还原到 runner 临时目录）。缺配置时 release 未签名，不阻断 debug 开发。
 */
val signingPropsPath =
    (cfg("signingProps") ?: System.getenv("BUSPOST_SIGNING_PROPS")
        ?: rootProject.file("../buspost-signing-backup/keystore.properties").canonicalPath)
val signingProps = Properties().apply {
    val f = File(signingPropsPath)
    if (f.exists()) f.inputStream().use { load(it) }
}

val releaseBaseUrl = cfg("baseUrl")
val isCiRelease = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }

// versionCode 走 CI 的 GITHUB_RUN_NUMBER（每次 workflow 运行自增），本地开发用 1。
// 这样每个 CI 构建都有唯一版本号，app 内更新才能正确比较新旧。
val ciVersionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

fun qu(v: String) = "\"${v.replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
    namespace = "com.shanxiang.buspost"
    /**
     * compileSdk 36（Android 16）：不是"追新"，是**依赖的硬门**——
     * androidx.activity 1.11+ / core-ktx 1.17+ 的 AAR 元数据里写死 `minCompileSdk=36`，
     * 停在 35 会让 `checkDebugAarMetadata` 直接失败。
     * 真机 Redmi Pad Pro 本身就跑 Android 16 / SDK 36，编译面与运行面终于对齐。
     */
    compileSdk = 36

    defaultConfig {
        applicationId = "com.shanxiang.buspost"
        minSdk = 26
        // targetSdk 34 → 36：Android 15/16 起 edge-to-edge 强制、后台/权限行为收紧，
        // 用 34 编译会在新系统上按"兼容模式"运行（界面不会真全屏、部分限制不触发），
        // 那不是"能跑"，是"跑在旧行为里"。对齐 36 才能暴露真实行为。
        targetSdk = 36
        // 版本号（#48 更新通路的根）：本机与 PR 构建恒为 1；CI 的每次运行按 `GITHUB_RUN_NUMBER` 递增。
        // 这里不能继续写死 1 —— App 内检查更新**只比 versionCode**，
        // 所有 Release 共用 1 的话，用户永远看不到更新，而且界面上一句错都不报（它只会老实说"已是最新"）。
        val ciRun = providers.environmentVariable("GITHUB_RUN_NUMBER").orNull?.trim()?.takeIf { it.isNotEmpty() }
        versionCode = ciRun?.toIntOrNull() ?: 1
        versionName = if (ciRun != null) "1.0.$ciRun-android" else "1.0.0-android"
        // 发布所在的仓库坐标：GitHub Releases 是这条通路唯一的版本事实来源（用户 2026-10-12 定的口径）。
        // 留成可覆盖的配置项，是为了 fork / 改名时不用回来动代码，也不用发新包。
        val updateRepo = cfg("updateRepo")
            ?: providers.environmentVariable("BUSPOST_UPDATE_REPO").orNull
            ?: "ycg-coder/cargo-post-android"
        buildConfigField("String", "UPDATE_REPO", qu(updateRepo))

        buildConfigField("String", "BASE_URL", qu(releaseBaseUrl ?: ""))
        // Android 地图 SDK 的 key（「Android 平台」类型，绑包名 + 签名 SHA1）——注入到 manifest meta-data。
        val amapKey = secret("amap.key", "AMAP_KEY") ?: ""
        buildConfigField("String", "AMAP_KEY", qu(amapKey))
        resValue("string", "amap_key", amapKey)
        // 天气 REST 用的 key（「Web 服务」类型）——不注入 manifest，只给 OkHttp 请求拼 key 参数。
        buildConfigField("String", "AMAP_WEB_KEY", qu(secret("amap.webKey", "AMAP_WEB_KEY") ?: ""))

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            /**
             * 演示数据总开关。**release 恒为 false**，所以发布包里 Api 失败就是失败，
             * 绝不会悄悄回落成 DemoData。**debug 也默认 false**（指令 v2.0 §16「不许假数据」/ 用户 2026-10-06
             * 「目前不需要演示数据」）：后端不通就是错误条，不再回填成界面数据。
             * 临时要演示形态：`-Pbuspost.demo=true`（旧 debug 默认值就是这一档）。
             */
            buildConfigField("boolean", "DEMO_ALLOWED", (cfg("demo")?.toBoolean() ?: false).toString())
            /**
             * 演示账号只从工程外配置读（local.properties 已 gitignore / -P / CI secret）。
             * 源码里不许留任何可用口令：口令只进 debug，release 恒为空串。
             */
            buildConfigField("String", "DEMO_MOBILE", qu(cfg("demoMobile") ?: ""))
            buildConfigField("String", "DEMO_PASSWORD", qu(cfg("demoPassword") ?: ""))
        }
        release {
            // ⚠ 今天刻意留在 false：R8 构建与"必留项扫 dex/mapping"已经跑过并全绿（10-16 实测：
            //   未裁剪 78,882,356 B / dex 55,847,808 B ⇒ 裁剪后 65,791,585 B / dex 6,909,516 B，−16.6%），
            //   但**运行时那一遍还没在这台设备上走过** —— 本机没有发布签名库，debug 签名装不进
            //   已装的 release 签名包（要卸载重装 = 丢登录态），所以不许凭"扫包绿"就翻这个开关。
            //   要翻的人必须：① 重跑 tools/release_minify_evidence.sh；② 用发布签名包真机走查核心屏。
            //   判据：tools/check_startup_probes.sh 的 ST-e（两处 false + 取证脚本在场）。
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            /**
             * 只发 64 位 ARM。量出来的账（真机 2026-10-15 拉回 v77 逐条目统计）：
             * 包内 native 库共 73.6 MB，其中 `lib/armeabi-v7a` 占 29.6 MB —— 装到 arm64 手机上
             * 这一半**永远不会被加载**，却每次都跟着下载。用户侧的直接症状就是"下载得很慢"：
             * 整包 110 MB，其中约四分之一是死重。
             * debug 不加这一条，保持全 ABI，免得换台机器/模拟器就装不上测试包。
             *
             * ⚠ 这是**设备支持面**的收窄：只有 32 位 ARM 的老机器将装不了正式版
             *   （minSdk 26 起这种机型已极少，且高德导航 SDK 本身连 x86 都没出，模拟器从来就装不上）。
             *   要回退：删掉下面这三行即可，产物名与 CI、App 内更新通路都不用动。
             */
            ndk {
                abiFilters += "arm64-v8a"
            }
            // 硬编码 false，不受任何属性/环境变量影响：正式版禁止演示数据。
            buildConfigField("boolean", "DEMO_ALLOWED", "false")
            buildConfigField("String", "DEMO_MOBILE", "\"\"")
            buildConfigField("String", "DEMO_PASSWORD", "\"\"")
            // 发布版签名：storeFile 按相对 keystore.properties 自身所在目录解析，
            // 这样签名材料与配置文件可以整体放在工程外的备份目录里。
            // ⚠ 两种形状都必须认（队列 #90）：CI 还原材料时写的是 `storeFile=buspost-release.jks`（就在 props 同目录），
            //   而本地那份备份里写的是 `release-keystore/buspost-release.jks` —— 那个子目录在备份目录里并不存在，
            //   jks 与 props 是平级放的。只认前者时本地 release **静默产出未签名 APK**（只 warn 一句，不阻断），
            //   而上传那一步是 `ls *.apk | head -1`，未签名的那只照样会被当成"发布包"。
            signingProps.getProperty("storeFile")?.let { sf ->
                val dir = File(signingPropsPath).parentFile
                val candidates = listOf(dir.resolve(sf), dir.resolve(File(sf).name))
                val store = candidates.firstOrNull { it.exists() }
                if (store == null) {
                    logger.error("✘ 签名文件找不到，两种形状都试过：${candidates.joinToString { it.canonicalPath }} " +
                        "—— release 将产出**未签名** APK，这只包不许进任何发布通路。")
                } else {
                    signingConfig = signingConfigs.create("release") {
                        storeFile = store
                        storePassword = signingProps.getProperty("storePassword")
                        keyAlias = signingProps.getProperty("keyAlias")
                        keyPassword = signingProps.getProperty("keyPassword")
                    }
                }
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // 注：jvmTarget 已迁到文件末尾的 `kotlin { compilerOptions { … } }`。
    // 原先的 `android { kotlinOptions { jvmTarget = "17" } }` 从 Kotlin 2.0 起就是废弃写法，
    // 在 2.3 上会刷 deprecation 警告，且它只能设字符串、拿不到类型检查（写错到 2.3 才报错）。

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        // 单元测试跑在 JVM 上：android.util.Log 等桩函数返回默认值而不是抛「not mocked」。
        unitTests.isReturnDefaultValues = true
    }
}

/**
 * Kotlin 编译选项（现代写法）。jvmTarget 必须与上面 `compileOptions` 的 Java 目标一致，
 * 否则 AGP 会在 `compileDebugKotlin` 报 "Inconsistent JVM-target compatibility"。
 */
kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

/**
 * release 的后端地址做静态校验，把「正式包指向本机 / 走明文 HTTP」这类事故挡在构建期。
 * 确实需要自签内网测试时显式传 `-Pbuspost.allowInsecureRelease=true`。
 */
fun validateReleaseBaseUrl() {
    val allowInsecure = (cfg("allowInsecureRelease") ?: "false").toBoolean()
    val url = releaseBaseUrl
        ?: error(
            "release 构建缺少后端地址。三选一：\n" +
                "  -Pbuspost.baseUrl=https://your-domain/api\n" +
                "  BUSPOST_BASE_URL=https://your-domain/api\n" +
                "  local.properties: buspost.baseUrl=https://your-domain/api\n" +
                "（debug 构建不需要该项，可留空。）"
        )
    if (allowInsecure) return
    val host = runCatching { URI(url).host ?: "" }.getOrDefault("")
    if (host.isEmpty()) error("buspost.baseUrl 不是合法 URL：$url")
    if (host == "localhost" || host == "127.0.0.1" || host == "10.0.2.2" || host == "::1") {
        error("release 包的后端地址不得指向本机（$host）——手机上 127.0.0.1 就是手机自己。")
    }
    if (!url.startsWith("https://")) {
        error("release 包必须使用 HTTPS（当前 $url）。内网测试请显式加 -Pbuspost.allowInsecureRelease=true。")
    }
}

// 只有真正要产出 release 时才校验，否则 `assembleDebug` / 单元测试会被无关配置挡住。
if (isCiRelease) validateReleaseBaseUrl()

dependencies {
    /**
     * Compose BOM 2026.06.01 —— **不是最新那版，是被 AAR 元数据卡住的合法上限**。
     *
     *   BOM 2026.09.00 → Compose ui/foundation 1.12.1 → minCompileSdk=37 + minAgp=9.1.0 ❌
     *   BOM 2026.06.01 → Compose ui/foundation 1.11.4 → minCompileSdk=35 + minAgp=8.6.0 ✅
     *   （两版的 material3 都是 1.4.0）
     *
     * 也就是说 Compose 1.12 那条线的入场券是 AGP 9 + compileSdk 37，而 2026.06.01 是
     * **留在 AGP 8 上能拿到的最后一版 Compose**，material3 已经是 1.4.0 最新稳定版。
     * 验收口径：compose 版本升级要能被 `checkDebugAarMetadata` 放行，不是"数字更大"。
     */
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.appcompat:appcompat:1.8.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    // 大屏适配（material3-adaptive 1.2.0：minCompileSdk 35 / minAgp 8.6.0，与本次目标一致；
    // 1.3.0 起要求 minCompileSdk 37 / AGP 9.1，不可用）。
    // 注意：**在用它的那一处代码落地之前不加这一行** —— 声明一个没有人 import 的依赖，
    // 等于在依赖清单里留一句"我们支持大屏"的空话。列表-详情双栏（ListDetailPaneScaffold）开工时再打开。
    // implementation("androidx.compose.material3.adaptive:adaptive:1.2.0")

    // lifecycle 2.10.0 是**整组统一上限**：viewmodel-compose 2.11.0 已要求 minCompileSdk=37，
    // 单独把 runtime 提到 2.11 会让组内传递依赖被解析到 2.11 而一并踩雷，所以四个一起停在 2.10.0。
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-service:2.10.0")

    // navigation-compose 2.10.0+ 同样跳到 minCompileSdk=37；2.9.4 是 8.x 线的终点。
    implementation("androidx.navigation:navigation-compose:2.9.4")

    // 后台任务：消息轮询提醒 + 开机重排（小程序做不到的能力）
    implementation("androidx.work:work-runtime-ktx:2.12.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")

    // 二维码：生成（寄货成功票根 / 取件码）+ 识别（司机扫码装车、妥投、取件核销）
    // ——— 对应小程序 wx.scanCode 与本项目 utils/qrcode.js 的能力
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // 高德地图（对应小程序原生 map 组件 + utils/transit-amap.js 的现实公交层）。
    // 注意：3dmap 已内置 location / search 类，单独再引 com.amap.api:location 会 duplicate class。
    // ⑧（赛道第 8 步）换包：导航类只在 `navi-3dmap` 里，而它**自带** 3dmap 10.0.600 的全部公开类，
    // 所以是**顶替**而不是并存（并存 = duplicate class，与 `com.amap.api:location` 同一颗雷）。
    // 证据见 `THIRD_PARTY_NOTICES.md` §3 与审计 §77：老 jar 的公开 API 类在新 jar 逐个都在
    //（唯一少的是内部混淆类 `com/autonavi/config/a`），本工程引用的 8 个 `com.amap.api.maps.*` 符号全部命中。
    implementation("com.amap.api:navi-3dmap:10.0.600_3dmap10.0.600")

    testImplementation("junit:junit:4.13.2")
    // android.jar 里的 org.json 在 JVM 单元测试中是抛异常的桩，补一份真实实现放在 classpath 前面。
    testImplementation("org.json:json:20240303")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
