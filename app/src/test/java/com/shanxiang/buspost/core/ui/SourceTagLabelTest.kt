package com.shanxiang.buspost.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「来源」那一颗标签的措辞契约（D-177 / #132 第三族）。
 *
 * 旧状态是 `else -> source`：后端或演示面给出一个没见过的枚举，就把内部词原样印到屏上。
 * 这一族最容易被写歪的方向有两个，所以两头都钉：
 *  · **认得出来的**必须还是那四句人话（这一格存在的目的是"别把推算当实测"，#41/#55 那条诚实线，不许顺手美化）；
 *  · **认不出来的**不许把原词透出去，也不许悄悄不给标签（那等于把"我们看不懂这条标注"演成"没有标注"）。
 */
class SourceTagLabelTest {

    @Test
    fun `认得出来的四种来源各有一句人话`() {
        assertEquals("现实公交", sourceTagLabel("REAL_TRANSIT"))
        assertEquals("实时", sourceTagLabel("REAL_FRESH"))
        assertEquals("位置推算", sourceTagLabel("SIMULATED"))
        assertEquals("演示数据", sourceTagLabel("DEMO_V2"))
        // 中文写法同样要认（后端两处字段历史上给过中文标签）
        assertEquals("实时", sourceTagLabel("实时上报"))
        assertEquals("位置推算", sourceTagLabel("班次计划推算"))
    }

    /** 没有来源 = 这一格不出现（返回 null 而不是"未知"，避免造出一个没人说过的状态） */
    @Test
    fun `没有来源时是空而不是一个词`() {
        assertNull(sourceTagLabel(null))
        assertNull(sourceTagLabel(""))
        assertNull(sourceTagLabel("   "))
    }

    /** 拒绝侧（收紧型改动必须钉这一边）：没见过的枚举原样透出去就是这次要修的病 */
    @Test
    fun `认不出来的来源不许把内部词印到屏上`() {
        for (raw in listOf("locationSource", "AMAP_BACKUP", "gps_provider_v3", "SOURCE_TYPE_9")) {
            val label = sourceTagLabel(raw)
            assertTrue("屏上读到了原词 $raw ⇒ $label", label != raw)
            assertFalse("标签里不许带内部词：$label", label!!.contains("_"))
            assertEquals("认不出来只有这一句说法", "来源没认出来", label)
        }
    }

    /**
     * `REAL_` 打头但表里没有的后缀，**故意**读成「实时」而不是"认不出来"：
     * 这一族的前提是"REAL 前缀 = 真实上报"，把它推成"我们看不懂这条标注"是把已知信息说没了。
     * 记在测试里是因为它是个真选择，不是漏网 —— 反过来（一律说认不出来）会丢掉"这不是推算"那一格。
     */
    @Test
    fun `REAL 打头的陌生后缀仍按真实上报说`() {
        assertEquals("实时", sourceTagLabel("REAL_XXX"))
    }

    /** 四句 + 认不出来那一句，五句必须互不相同（两两共用迟早对不上某一格） */
    @Test
    fun `每种说法各不相同时才说明分支没被合并`() {
        val labels = listOf("REAL_TRANSIT", "REAL_FRESH", "SIMULATED", "DEMO_V2", "WHAT_IS_THIS")
            .mapNotNull { sourceTagLabel(it) }
        assertEquals("五格五种说法：$labels", 5, labels.toSet().size)
    }
}
