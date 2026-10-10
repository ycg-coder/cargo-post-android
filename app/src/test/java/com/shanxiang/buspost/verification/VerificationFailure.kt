package com.shanxiang.buspost.verification

import java.io.File

/**
 * 指令 v2.1 §三十三 / F-2 第 7 步要求"构建失败必须区分四类，不能笼统写构建失败"。
 *
 * **为什么这个类型在 test 源集，不在 `app/src/main`**（这条是本步骤唯一偏离指令字面之处，写在最显眼处）：
 * 构建失败发生时 App 进程根本不存在，客户端代码没有任何一处能观察到它。放进 `src/main` 只会
 * 得到一个"随包分发、零消费者"的类型，并且紧挨着业务侧已有的三套失败真相
 * （`ApiException` / `NetworkException` / `Ack.error`）长出第四套 —— 那正是 F-2 第 7 步自己禁止的事。
 * 所以分型落在两处**都有真实消费者**的地方：规则表 `tools/verification_failure_rules.txt`（唯一数据源）、
 * 本类型 + [VerificationFailureTest]（在测试期读同一张表，钉住它的结构性质）。
 * `tools/check_verification_failure_probes.sh` 的 VF-c 负责证明 `src/main` 里这四类符号仍是 0 命中。
 *
 * 分类的**数据**只有一份（规则表），本文件里的匹配只是"子串包含"这一种语义的两次使用：
 * bash 侧用 `grep -aF`、这里用 [String.contains] —— 两者等价的前提是字面量不含正则元字符且全 ASCII，
 * 而这个前提由 [VerificationFailureTest] 逐行验。前提一旦被破坏，用例先红。
 */
sealed interface VerificationFailure {

    /** 台账与分类器输出一律用这个拼写，不用中文同义词（四条拼写必须与规则表逐字节相同）。 */
    val marker: String

    object SourceFailure : VerificationFailure {
        override val marker = "SOURCE_FAILURE"
    }

    object DependencyFailure : VerificationFailure {
        override val marker = "DEPENDENCY_FAILURE"
    }

    object EnvironmentFailure : VerificationFailure {
        override val marker = "ENVIRONMENT_FAILURE"
    }

    object DeviceUnavailable : VerificationFailure {
        override val marker = "DEVICE_UNAVAILABLE"
    }

    /** 规则表里的一行：类别 + 字面量 + 出处。出处以 `未实测` 开头者 = 本工程从没产出过这条文案。 */
    data class Rule(val marker: String, val literal: String, val provenance: String) {
        val unobserved: Boolean get() = provenance.startsWith("未实测")
    }

    companion object {
        val CASES: List<VerificationFailure> =
            listOf(SourceFailure, DependencyFailure, EnvironmentFailure, DeviceUnavailable)

        val MARKERS: Set<String> = CASES.map { it.marker }.toSet()

        const val RULES_PATH = "tools/verification_failure_rules.txt"
        const val FIXTURE_DIR = "tools/fixtures/verification-failure"
        const val NEGATIVE_FIXTURE = "negative-control-tests-failed-not-a-build-failure.log"

        /**
         * 样例日志一律按 **ISO_8859_1** 读：这些日志的编码并不统一（worker 那份混有 GBK 字节与
         * EF BF BD 替换符）。按字节读 + 全 ASCII 字面量，才与 `grep -aF` 的行为一致；
         * 用 UTF-8 解码会在非法序列上丢字符，导致"两侧数出来不一样"这种无法复现的分歧。
         */
        fun readBytesAsText(file: File): String = file.readText(Charsets.ISO_8859_1)

        /** 规则表 = 跳过 `#` 行、必须恰好三个 TAB 字段。字段数不对要直接失败，不许静默丢行。 */
        fun rules(rulesFile: File): List<Rule> =
            rulesFile.readText(Charsets.UTF_8).lineSequence()
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .map { line ->
                    val parts = line.split("\t")
                    require(parts.size == 3) {
                        "规则表字段数不是 3（TAB 分隔）：$line —— 静默丢行会让某一类失去判据而没人发现"
                    }
                    Rule(parts[0].trim(), parts[1].trim(), parts[2].trim())
                }
                .toList()

        /** 命中的**类别**集合（不是规则条数：同一类命中多行是正常的）。空集 = 分类器该输出 UNCLASSIFIED。 */
        fun markersIn(text: String, rules: List<Rule>): Set<String> =
            rules.filter { text.contains(it.literal) }.map { it.marker }.toSet()

        /** 向上找 settings.gradle.kts：测试的工作目录不固定（模块目录 / 工程目录都出现过）。 */
        fun projectRoot(): File {
            var dir: File? = File(".").absoluteFile
            while (dir != null) {
                if (File(dir, "settings.gradle.kts").isFile) return dir
                dir = dir.parentFile
            }
            error("找不到工程根（任何上级目录都没有 settings.gradle.kts）——这是 ENVIRONMENT_FAILURE，不许记成用例失败")
        }
    }
}
