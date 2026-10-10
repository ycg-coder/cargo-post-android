package com.shanxiang.buspost.core.about

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 指令 v2.1 第 ⑭ 步的契约用例：App 里的 OSM/ODbL 署名与"实际进 APK 的第三方组件"名单。
 *
 * 测缝说明（诚实口径）：这一屏是 Compose，本机没有设备也没有 Robolectric，**渲染不出来**。
 * 所以这里钉的是屏唯一的数据源 [OpenSourceCredits]——三条署名文案一字不差、名单的分类不混淆；
 * "屏确实把这三行显示出来、入口确实可达"由 `tools/check_oss_credits_probes.sh`（OS-b）按源码字面量钉，
 * "组件到底进没进包"由 `tools/apk_oss_census.py` 读 `classes*.dex` 的 class_defs 实测（OS-e）。
 * **DEVICE_VERIFIED 不在这里主张**：没有真机点开过这个页面。
 */
class OpenSourceCreditsContractTest {

    /** ⑭ 点名的三行，一个字都不能漂：这是 ODbL 1.0 §4 的署名义务落点 */
    @Test
    fun `OSM 署名三行逐字钉住`() {
        assertEquals("© OpenStreetMap contributors", OpenSourceCredits.OSM_ATTRIBUTION)
        assertEquals("OpenStreetMap 数据用于道路/地图相关功能", OpenSourceCredits.OSM_USAGE)
        assertEquals("数据许可：Open Database License (ODbL)", OpenSourceCredits.OSM_DATA_LICENSE)
    }

    /** 署名说的是数据来源，不许被写成"APK 里内置了 OSM 地图包" */
    @Test
    fun `署名口径是数据来源而非内置瓦片`() {
        assertTrue(OpenSourceCredits.OSM_WHERE.contains("APK 内不含"))
        assertTrue(OpenSourceCredits.OSM_WHERE.contains("折线"))
    }

    /** 在册面：17 条开源 + 1 条专有 + 6 条未进包。名单变了必须连同矩阵与台账一起改 */
    @Test
    fun `三份名单的在册条数`() {
        assertEquals(17, OpenSourceCredits.included.size)
        assertEquals(1, OpenSourceCredits.proprietary.size)
        assertEquals(6, OpenSourceCredits.referenceOnly.size)
    }

    /** 每条内置组件都要有 group:name:version 坐标、许可证、以及"凭什么说它在包里"的证据列 */
    @Test
    fun `内置组件逐条有坐标许可证与证据`() {
        (OpenSourceCredits.included + OpenSourceCredits.proprietary).forEach { lib ->
            assertTrue("${lib.name} 的名字是空的", lib.name.isNotBlank())
            assertTrue("${lib.name} 的许可证是空的", lib.license.isNotBlank())
            assertTrue("${lib.name} 没有写进包证据", lib.inApkEvidence.isNotBlank())
            assertEquals(
                "${lib.gradleCoordinate} 必须是 group:name:version 三段（解析后的真实版本，不写区间）",
                3,
                lib.gradleCoordinate.split(":").filter { it.isNotBlank() }.size,
            )
        }
    }

    /** 指令原文"不要把'参考过'写成'内置'"：服务端与测试源集的东西一旦混进内置名单就是谎报分发 */
    @Test
    fun `参考过的东西不许混进内置名单`() {
        val banned = listOf("GraphHopper", "OSMDroid", "osmdroid", "GDAL", "proj", "yudao", "芋道", "JUnit", "junit", "mockwebserver", "MockWebServer", "cargo-post-platform")
        (OpenSourceCredits.included + OpenSourceCredits.proprietary).forEach { lib ->
            val hay = lib.name + " " + lib.gradleCoordinate
            banned.forEach { b -> assertFalse("$hay 里出现了 $b：它不在 APK 里", hay.contains(b, ignoreCase = true)) }
        }
    }

    /** 未进包那一栏必须自己承认没进包，六条都要写出为什么 */
    @Test
    fun `未进包名单每条都带原因`() {
        OpenSourceCredits.referenceOnly.forEach {
            assertTrue("「$it」没写清它在哪儿/为什么不在包里", it.length > 12)
        }
        assertTrue(OpenSourceCredits.referenceOnly.any { it.contains("GraphHopper") })
        assertTrue(OpenSourceCredits.referenceOnly.any { it.contains("OSMDroid") })
        assertTrue(OpenSourceCredits.referenceOnly.any { it.contains("OpenStreetMap 矢量数据") })
    }

    /** 内置坐标不许重复：同一构件出现两遍意味着矩阵里有两行互相矛盾的判定 */
    @Test
    fun `内置坐标不重复`() {
        val coords = (OpenSourceCredits.included + OpenSourceCredits.proprietary).map { it.gradleCoordinate }
        assertEquals(coords.size, coords.toSet().size)
    }

    /**
     * 高德 SDK 随包分发但**不是开源组件**：它必须待在 proprietary 里，
     * 许可证列要点明"专有/非 OSI"，不能被并进 Apache 2.0 那张 OSS 名单里。
     */
    @Test
    fun `高德 SDK 记在专有栏而不是开源栏`() {
        val amap = OpenSourceCredits.proprietary.single { it.gradleCoordinate.startsWith("com.amap.api:") }
        assertTrue(amap.license.contains("AMap"))
        assertTrue(amap.license.contains("专有"))
        assertTrue(OpenSourceCredits.included.none { it.gradleCoordinate.startsWith("com.amap.api:") })
    }

    /** okio / kotlinx-serialization 是传递依赖，不在 build.gradle.kts 里声明，但确实进包 —— 名单必须按包实测收它们 */
    @Test
    fun `传递依赖也按实测进名单`() {
        assertTrue(OpenSourceCredits.included.any { it.gradleCoordinate.startsWith("com.squareup.okio:okio:") })
        assertTrue(OpenSourceCredits.included.any { it.gradleCoordinate.startsWith("org.jetbrains.kotlinx:kotlinx-serialization-core:") })
    }
}
