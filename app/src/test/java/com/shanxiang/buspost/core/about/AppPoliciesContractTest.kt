package com.shanxiang.buspost.core.about

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 隐私政策 / 用户协议的内容契约（#49 之后补的那一格：登录页有《服务协议》四个字，正文哪儿都没有）。
 *
 * 这本用例钉的不是"文案好不好"，而是**说明与现实不能两张皮**：
 * · 协议不许承诺本项目明令不做的服务（网上预约乘车、网约车、订座）；
 * · 隐私政策不许写本应用其实没有的能力（在线支付已开通、人脸登录、广告/埋点、自动检查更新）；
 * · 每一条都要有正文，不许留占位；两份都必须带"这是功能级说明，不是法律文本"的免责抬头；
 * · 更新日期是一个真实日期，且两份一致（改内容不改日期 = 用户看到的"最新"是假的）。
 */
class AppPoliciesContractTest {

    private val kinds = listOf(PolicyKind.PRIVACY, PolicyKind.TERMS)

    private fun all(d: PolicyDoc) = d.sections.flatMap { it.lines }

    /*
     * 第一版这两组判据我写成"整词不许出现"，实测自己红了两条：
     * 正文里"不提供网上预约乘车""本应用不是出租车/网约车平台""没有接入任何广告 SDK"
     * 都是**必须有的否定句**，被裸词 bans 一起打掉了。判据要禁的是"把没做的说成做了"，
     * 不是禁提这个词 —— 所以改成：禁肯定式措辞（正面主张）+ 要求对应的否定句在场。
     */
    /** 肯定式承诺 = 给用户一个取不到的服务（本项目硬规则：不提供网上预约乘车、不是网约车平台） */
    private val bannedPromises = listOf(
        "可在线预约乘车", "支持预约乘车", "预约乘车服务", "在线订座", "可网约", "提供网约车", "随叫随到",
    )

    /** 未落地的能力，不许用肯定式写（支付是模拟模式、人脸没有、广告/埋点没有、更新要人主动点） */
    private val notEnabled = listOf(
        "已开通在线支付", "支持在线支付", "在线支付已", "已支持人脸", "支持人脸识别登录",
        "接入广告", "含广告", "数据统计上报", "自动检查更新", "后台自动下载",
    )

    /** 反过来：这些"没做/不做"必须写明，缺一条就是拿沉默糊过去 */
    private val mustState = mapOf(
        PolicyKind.TERMS to listOf("不提供网上预约乘车", "逢站必停", "网约车"),
        PolicyKind.PRIVACY to listOf("没有接入任何广告", "不做人脸识别登录", "只有你主动", "会话令牌"),
    )

    @Test
    fun `两份说明都有分条正文且没有空条目`() {
        for (k in kinds) {
            val d = AppPolicies.of(k)
            assertTrue(k.name, d.sections.isNotEmpty())
            for (s in d.sections) {
                assertTrue("${k.name} 有条目为空", s.heading.isNotBlank() && s.lines.isNotEmpty())
                s.lines.forEach { line -> assertTrue("${k.name} 有空行", line.isNotBlank()) }
            }
        }
    }

    /** 页面（`PolicyScreen`）直接把这些字段渲染到屏幕上，占位符会被用户看见 */
    @Test
    fun `正文里没有占位符与待补记号`() {
        val marks = listOf("TODO", "待补", "XXX", "xxx", "???", "占位")
        for (k in kinds) {
            val text = (listOf(AppPolicies.of(k).title, AppPolicies.of(k).subtitle) + all(AppPolicies.of(k))).joinToString("|")
            marks.forEach { m -> assertTrue("${k.name} 含 $m", !text.contains(m)) }
        }
    }

    @Test
    fun `协议不许用肯定式承诺本项目明令不做的服务`() {
        val d = AppPolicies.of(PolicyKind.TERMS)
        bannedPromises.forEach { b ->
            all(d).forEach { line ->
                assertFalse("服务边界那条写了「$b」，与「不提供网上预约乘车」打架：$line", line.contains(b))
            }
        }
    }

    @Test
    fun `两份说明都不许把没做的能力写成已有`() {
        for (k in kinds) {
            val text = all(AppPolicies.of(k))
            notEnabled.forEach { b ->
                assertTrue("${k.name} 写了「$b」", text.none { it.contains(b) })
            }
        }
    }

    /** 与上一条配对：光"没写"不算数，必须正面写出"我们不做这件事" */
    @Test
    fun `没做的能力必须以否定句写明而不是不提`() {
        for ((k, needles) in mustState) {
            val text = all(AppPolicies.of(k)).joinToString("|")
            needles.forEach { n -> assertTrue("${k.name} 少了「$n」这一句", text.contains(n)) }
        }
    }



    /** 隐私政策必须交代真实发生的三件事：本机存会话令牌、位置用途、只有主动点才访问发布接口 */
    @Test
    fun `隐私政策必须交代实际发生的数据动作`() {
        val text = all(AppPolicies.of(PolicyKind.PRIVACY)).joinToString("|")
        assertTrue(text, text.contains("位置"))
        assertTrue(text, text.contains("退出登录"))
    }

    @Test
    fun `两份都带免责抬头`() {
        for (k in kinds) {
            val d = AppPolicies.of(k)
            assertTrue(k.name, d.disclaimer.contains("不构成法律意见"))
            assertEquals("${k.name} 的标题不该混用", if (k == PolicyKind.PRIVACY) "隐私政策" else "用户协议", d.title)
        }
    }

    @Test
    fun `更新日期是真实日期且两份一致`() {
        val p = AppPolicies.of(PolicyKind.PRIVACY).updatedOn
        val t = AppPolicies.of(PolicyKind.TERMS).updatedOn
        assertEquals("两份说明的日期必须同轮改动", p, t)
        val m = Regex("""^\d{4}-\d{2}-\d{2}$""").matches(p)
        assertTrue("日期格式 $p", m)
        val y = p.substring(0, 4).toInt()
        assertTrue("年份不像话：$p", y in 2024..2030)
    }
}
