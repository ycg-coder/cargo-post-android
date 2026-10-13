package com.shanxiang.buspost.ui.user

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #39「收藏线路首页入口」契约测试。三件事是逐行读生产者定下来的（`FavoriteLines.kt` 顶部记着出处）：
 * - 收藏**只有本机**：`Prefs.kt:228-254` 存 DataStore，后端 0 个收藏端点、也没有收藏表
 *   ⇒ 界面不许出现"已同步/换机恢复"这类话；
 * - 线路名字只存在于 `GET /app-api/transport/bus/lines`（`Repo.kt:852-857`）的返回里，本地只有 id
 *   ⇒ 首页那一格不显名字、不显计数；
 * - 旧写法 `if (favLines.isNotEmpty())` 会把"线路列表取失败"念成"没有收藏"
 *   ⇒ 本轮拆成"本机记着几条 / 这一轮显示几条 / 为什么显示不出来"三格。
 */
class FavoriteLinesContractTest {

    @Test
    fun `首页那一格的文案唯一出处且不提同步`() {
        assertEquals("收藏线路", FAVORITE_ENTRY_LABEL)
        assertTrue(FAVORITE_LOCAL_NOTE.contains("这台设备"))
        assertFalse("不许出现同步类承诺：后端没有收藏接口", FAVORITE_LOCAL_NOTE.contains("同步到"))
        assertFalse("同样不许暗示能换机恢复", FAVORITE_LOCAL_NOTE.contains("恢复"))
    }

    @Test
    fun `一条没收藏时整栏不出现`() {
        assertFalse(favoriteRowVisible(0))
        assertTrue(favoriteRowVisible(1))
    }

    @Test
    fun `标题数的是本机记着的条数`() {
        assertEquals("收藏线路 · 本机 3 条", favoriteRowTitle(3))
        assertEquals("收藏线路 · 本机 1 条", favoriteRowTitle(1))
    }

    @Test
    fun `全显示出来时不多说一句`() {
        assertNull(favoriteUnresolvedText(3, 3, null))
        assertNull(favoriteUnresolvedText(0, 0, null))
        assertNull(favoriteUnresolvedText(2, 5, null))
    }

    @Test
    fun `取失败那一格绝不念成没有收藏`() {
        val t = favoriteUnresolvedText(4, 0, "网络不可用") ?: "（没给文案）"
        assertTrue(t, t.contains("没取到"))
        assertTrue("必须把失败原因带出来，不能只说取不到：" + t, t.contains("网络不可用"))
        assertFalse("失败那一格不许念成线路下架：" + t, t.contains("下架"))
        assertTrue(t, t.contains("不是没有收藏"))
    }

    @Test
    fun `列表拿到了才可以说当前列表里没有`() {
        val t = favoriteUnresolvedText(5, 3, null) ?: "（没给文案）"
        assertTrue("本机数与显示数要同时出现，不许只报差值：" + t, t.contains("2 条") && t.contains("5 条"))
        assertTrue(t, t.contains("当前线路列表里没有"))
        val blank = favoriteUnresolvedText(3, 1, "") ?: "（没给文案）"
        assertTrue("空串是失败但没原因，仍归没取到那一格：" + blank, blank.contains("没给出原因"))
        assertFalse("拿不到原因也不许滑到'线路下架'那一格：" + blank, blank.contains("下架"))
    }

    @Test
    fun `空收藏那一格与未命中那一格互不顶替`() {
        assertEquals(FAVORITE_EMPTY_HINT, favoriteEmptyText(0))
        assertNull("有收藏时不许念空态", favoriteEmptyText(2))
        assertNull(favoriteEmptyText(1))
    }

    @Test
    fun `本机记着几条与这一轮显示几条是两个数`() {
        val title = favoriteRowTitle(5)
        val note = favoriteUnresolvedText(5, 3, null)!!
        assertTrue(title.contains("5 条"))
        assertTrue(note.contains("2 条"))
        assertFalse("标题不许跟着显示数变小：" + title, title.contains("3 条"))
    }

    @Test
    fun `说明句给出可执行的下一步而不是只报空`() {
        assertTrue(FAVORITE_EMPTY_HINT, FAVORITE_EMPTY_HINT.contains("星标"))
    }
}
