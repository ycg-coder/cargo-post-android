package com.shanxiang.buspost.ui.user

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 缺陷 #47 的两条纯函数：商城"排序/筛选要用全店口径"与"空态那句到底是谁没给"。
 *
 * 为什么钉在这一层：`GoodsViewModel` 的 `shown`/`countText` 依赖 SnapshotState 与取数协程，
 * 而这两条判断本身是纯的 —— 把"要不要全量"与"空态怎么说"抽成函数，页面只渲染结论，
 * 才能在没有 Robolectric 的工程里把话术钉住（与 D-080 公告栏、#42 天气同一套分工）。
 */
class GoodsScopeContractTest {

    @Test
    fun `只有综合排序加无筛选时才允许按分页看`() {
        assertFalse(goodsNeedsCatalog(sortIndex = 0, categoryIndex = 0, query = ""))
        assertFalse(goodsNeedsCatalog(sortIndex = 0, categoryIndex = 0, query = "   "))
        assertTrue("价格档一开就是在全店里比高低", goodsNeedsCatalog(sortIndex = 1, categoryIndex = 0, query = ""))
        assertTrue("销量档同上（降序那档也要全量）", goodsNeedsCatalog(sortIndex = 3, categoryIndex = 0, query = ""))
        assertTrue(goodsNeedsCatalog(sortIndex = 0, categoryIndex = 2, query = ""))
        assertTrue(goodsNeedsCatalog(sortIndex = 0, categoryIndex = 0, query = "鸡蛋"))
    }

    /** 分类是**本机按商品名分的**，所以"没结果"有三种成因；把"清单还没取回来"说成"卖完了"是最坏的一种 */
    @Test
    fun `全店清单还没回来时不许宣布没货`() {
        val copy = goodsEmptyCopy(categoryIndex = 2, query = "", catalog = null)
        assertEquals("正在取全店在架清单", copy.title)
        assertTrue("必须说出此刻只在已加载的几页里筛：${copy.subtitle}", copy.subtitle.contains("已加载"))
        assertFalse("这一格不许出现『没货』", copy.title.contains("没货") || copy.subtitle.contains("没货"))
    }

    @Test
    fun `全量在手而这一类确实没有时说清是没上架`() {
        val copy = goodsEmptyCopy(categoryIndex = 2, query = "", catalog = emptyList())
        assertEquals("在架清单里没有这一类", copy.title)
        assertFalse("库存是每件商品自己的字段，这一格不许替它宣布", copy.title.contains("没货"))
        assertTrue("要说清分类是本机按名字分的：${copy.subtitle}", copy.subtitle.contains("按商品名分的"))
    }

    @Test
    fun `搜索没命中时把词念回去`() {
        val copy = goodsEmptyCopy(categoryIndex = 0, query = "蜂蜜", catalog = emptyList())
        assertEquals("在架清单里没搜到「蜂蜜」", copy.title)
        assertEquals(
            "关键词与分类同时命中时，先说这一类（页面按 categoryIndex 优先分支）",
            "在架清单里没有这一类",
            goodsEmptyCopy(categoryIndex = 5, query = "蜂蜜", catalog = emptyList()).title,
        )
    }

    @Test
    fun `什么都没筛时保持原来的说法`() {
        val copy = goodsEmptyCopy(categoryIndex = 0, query = "", catalog = emptyList())
        assertEquals("山货还没上新", copy.title)
        assertEquals("农户正在备货，过会儿再来看看", copy.subtitle)
    }
}
