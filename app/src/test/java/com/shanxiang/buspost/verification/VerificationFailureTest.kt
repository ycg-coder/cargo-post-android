package com.shanxiang.buspost.verification

import com.shanxiang.buspost.verification.VerificationFailure.Companion.FIXTURE_DIR
import com.shanxiang.buspost.verification.VerificationFailure.Companion.NEGATIVE_FIXTURE
import com.shanxiang.buspost.verification.VerificationFailure.Companion.RULES_PATH
import com.shanxiang.buspost.verification.VerificationFailure.Companion.markersIn
import com.shanxiang.buspost.verification.VerificationFailure.Companion.projectRoot
import com.shanxiang.buspost.verification.VerificationFailure.Companion.readBytesAsText
import com.shanxiang.buspost.verification.VerificationFailure.Companion.rules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 指令 v2.1 §三十三 / F-2 第 7 步：四类失败分型的**结构性质**测试。
 *
 * 这里不重跑 gradle（构建期失败无法在测试期复现），而是把分类器赖以工作的三件事钉住：
 * ① 规则表的类别与指令点名的四类逐字相同；② 规则表与 bash 分类器的匹配语义**可证等价**
 * （字面量全 ASCII、无正则元字符 ⇒ `grep -aF` 与 `String.contains` 同义）；
 * ③ 六份**真实产出**的样例日志各自只落进一个类别，而"用例失败"那份一份都不落。
 *
 * 样例日志是本工作区当场跑出来的，不是抄的：录法见 tools/fixtures/verification-failure/README.md
 * （worker 起不来 = 去掉 GBK 开关复现 D-037；编译失败 = 临时塞一个引用不存在类的文件，录完删掉；
 * 依赖解析失败 = 只用 `-I` 注入一个不存在的坐标，**没有改过 app/build.gradle.kts**；
 * 设备缺席 = `adb shell` 在无设备时的真实报错；非 git 工作区 = `git rev-parse` 的 fatal）。
 */
class VerificationFailureTest {

    private val root = projectRoot()
    private val rulesFile = File(root, RULES_PATH)
    private val table by lazy { rules(rulesFile) }
    private val fixtures by lazy {
        File(root, FIXTURE_DIR).listFiles { f -> f.isFile && f.name.endsWith(".log") }?.toList().orEmpty()
    }

    /** 样例文件名前缀就写了它该是哪一类 —— 期望值放在文件名里，而不是在用例里再抄一份表。 */
    private val prefixToMarker = linkedMapOf(
        "source-failure-" to "SOURCE_FAILURE",
        "dependency-failure-" to "DEPENDENCY_FAILURE",
        "environment-failure-" to "ENVIRONMENT_FAILURE",
        "device-unavailable-" to "DEVICE_UNAVAILABLE",
    )

    private fun positiveFixtures() = fixtures.filter { it.name != NEGATIVE_FIXTURE }

    @Test
    fun `规则表里的类别恰好是指令点名的四类`() {
        assertEquals(
            "类别拼写必须与 §三十三 逐字相同（大小写、下划线都在判据里）",
            VerificationFailure.MARKERS, table.map { it.marker }.toSet()
        )
        assertEquals("四类，不多不少", 4, VerificationFailure.CASES.size)
        assertEquals("规则表类别不能出现第五种", emptySet<String>(), table.map { it.marker }.toSet() - VerificationFailure.MARKERS)
    }

    @Test
    fun `字面量全 ASCII 且不含正则元字符，所以 bash 与 JVM 的匹配语义等价`() {
        val badMeta = table.filter { it.literal.any { c -> "{}()[]*+?^\$.\\".contains(c) } }
        assertTrue("字面量一旦带上正则元字符，grep -E 与 Regex 就会分岔：${badMeta.map { it.literal }}", badMeta.isEmpty())
        val nonAscii = table.filter { it.literal.any { c -> c.code > 0x7F } }
        assertTrue(
            "非 ASCII 字面量在混合编码日志（GBK 字节 + EF BF BD）上会安静读成 0，" +
                "而读成 0 与真的没有长得一模一样：${nonAscii.map { it.literal }}",
            nonAscii.isEmpty()
        )
        assertTrue("字面量不许为空或纯空白", table.all { it.literal.isNotBlank() })
    }

    @Test
    fun `同一字面量不跨类别重复，也不在同一类别里重复`() {
        val dupes = table.groupBy { it.literal }.filterValues { it.size > 1 }
        assertTrue("重复字面量会让同一份日志命中两个类别，分类器只能报歧义：${dupes.keys}", dupes.isEmpty())
    }

    @Test
    fun `每一类都有多条判据，且至少一条的出处是本轮实测`() {
        for (marker in VerificationFailure.MARKERS) {
            val rows = table.filter { it.marker == marker }
            assertTrue("$marker 至少两条字面量（单条判据一删就没兜底），实测 ${rows.size} 条", rows.size >= 2)
            assertTrue("$marker 至少一条出处为『实测』（不许整类都建立在没见过的文案上）", rows.any { !it.unobserved })
            assertTrue("$marker 的出处列不能为空", rows.all { it.provenance.isNotBlank() })
        }
    }

    @Test
    fun `每份真实样例只落进一个类别，且落进的正是文件名声明的那一类`() {
        val files = positiveFixtures()
        assertTrue("样例目录至少要有四份失败日志，实测 ${files.size} 份", files.size >= 4)
        for (f in files) {
            val expected = prefixToMarker.entries.firstOrNull { f.name.startsWith(it.key) }
            assertNotNull("样例文件名前缀必须声明类别（$f.name）", expected)
            val got = markersIn(readBytesAsText(f), table)
            assertEquals("${f.name} 命中了 ${got.size} 个类别，分类器会报 AMBIGUOUS", setOf(expected!!.value), got)
        }
    }

    @Test
    fun `四类各有至少一份真实样例，删掉任何一类的证据都会红`() {
        val covered = positiveFixtures().flatMap { markersIn(readBytesAsText(it), table) }.toSet()
        assertEquals("样例覆盖的类别必须等于四类", VerificationFailure.MARKERS, covered)
    }

    @Test
    fun `用例失败不是构建失败：那份日志一条字面量都不许命中`() {
        val negative = File(root, "$FIXTURE_DIR/$NEGATIVE_FIXTURE")
        assertTrue("缺少『用例失败』反例样例：$NEGATIVE_FIXTURE（没有它就无法证明分类器不会过度命中）", negative.isFile)
        val text = readBytesAsText(negative)
        // 这份日志里确实有 "Task :app:testDebugUnitTest FAILED" 与 "BUILD FAILED" —— 正是最容易被误分类的形状
        assertTrue("反例样例必须真的含 BUILD FAILED，否则它证不了什么", text.contains("BUILD FAILED"))
        assertEquals("用例失败的日志应让分类器输出 UNCLASSIFIED，实测命中：", emptySet<String>(), markersIn(text, table))
    }

    @Test
    fun `规则表少一列时拒绝加载，而不是静默丢掉那一行`() {
        val broken = File.createTempFile("vf_rules_broken", ".txt")
        try {
            broken.writeText("SOURCE_FAILURE\te: file:\n")
            val thrown = runCatching { rules(broken) }.exceptionOrNull()
            assertTrue("少一列必须抛异常（静默丢行 = 那一类从此没有判据），实测：$thrown", thrown is IllegalArgumentException)
        } finally {
            broken.delete()
        }
    }
}
