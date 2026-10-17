package com.shanxiang.buspost.ui.common

import com.shanxiang.buspost.core.ui.BusIcons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 全局悬浮胶囊的收口规则用例（判据 `tools/check_capsule_nav_probes.sh` CN-g 钉条数）。
 *
 * 为什么值得单独钉：这三条规则（必须有标签、禁用必须带原因、一栏最多几格）如果散在各屏的
 * `if` 里，就会出现"这一屏少一格、那一屏多一格、被截掉的那几格没人说"的三套答案 ——
 * 与本仓 `navPlanOfStops` / `detailSlotOf` 同一手法：判断收进纯函数，UI 只渲染。
 */
class CapsuleNavContractTest {

    private fun cell(
        key: String,
        label: String = "格",
        enabled: Boolean = true,
        reason: String = "",
    ) = CapsuleAction(
        key = key,
        label = label,
        icon = BusIcons.home,
        onClick = {},
        enabled = enabled,
        disabledReason = reason,
    )

    @Test
    fun `没有标签的格子不收上这一栏`() {
        val items = capsuleItemsOf(listOf(cell("a", ""), cell("b", "刷新")))
        assertEquals("空标签那一格不许占位：" + items.map { it.key }, listOf("b"), items.map { it.key })
    }

    @Test
    fun `禁用又不给原因的格子不收 给了原因才收`() {
        val bare = capsuleItemsOf(listOf(cell("a", "全览", enabled = false)))
        assertTrue("点不动又不说为什么 = 一个坏按钮，不许上栏", bare.isEmpty())

        val withReason = capsuleItemsOf(listOf(cell("a", "全览", enabled = false, reason = "还没算出路")))
        assertEquals("带原因的禁用格要收（它念的是原因）", 1, withReason.size)
        assertEquals("还没算出路", withReason.first().disabledReason)
    }

    @Test
    fun `一栏最多五格 多出来的算进 dropped`() {
        val six = (1..6).map { cell("k$it", "格$it") }
        val items = capsuleItemsOf(six)
        assertEquals("画面上的格子数封顶", CAPSULE_MAX, items.size)
        assertEquals("被截掉的那一格必须被数出来（不许静默消失）", 1, capsuleDropped(six))
    }

    @Test
    fun `不合格的格子不算装不下`() {
        // 五格合格 + 一格空标签：空标签是登记方的错，不该报成"这一栏装不下"
        val mixed = (1..5).map { cell("k$it", "格$it") } + cell("bad", "")
        assertEquals(CAPSULE_MAX, capsuleItemsOf(mixed).size)
        assertEquals("dropped 只数装不下的合格格", 0, capsuleDropped(mixed))
    }

    @Test
    fun `画面顺序就是登记顺序 这里不重排`() {
        val order = listOf(cell("c", "丙"), cell("a", "甲"), cell("b", "乙"))
        assertEquals("胶囊不许自己决定谁在前", listOf("c", "a", "b"), capsuleItemsOf(order).map { it.key })
    }

    @Test
    fun `格子有等宽下限 短文案那一格不跟着内容缩`() {
        // 两格的胶囊（选点屏 / 实时公交）以前按内容长：一格七个字、一格"刷新"，整枚歪着。
        // 这一条钉的是**观感那一半**：宽度下限要撑得住四个汉字 + 图标，且不跟着文案长短抖。
        assertTrue(
            "格子宽度下限要 ≥76dp（现 $CAPSULE_CELL_MIN_WIDTH_DP），否则短文案那一格会缩成一条",
            CAPSULE_CELL_MIN_WIDTH_DP >= 76,
        )
    }

    @Test
    fun `一屏不登记就没有这一栏`() {
        assertTrue("空登记必须收成空列表（画出来就是一条空槽占屏幕）", capsuleItemsOf(emptyList()).isEmpty())
        assertEquals(0, capsuleDropped(emptyList()))
    }
}
