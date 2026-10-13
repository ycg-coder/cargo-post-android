package com.shanxiang.buspost.ui.driver

import com.shanxiang.buspost.data.model.Notice
import com.shanxiang.buspost.data.repo.RemoteState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 司机端「平台公告」那一栏的契约（标记说明里司机端首页那一格，走后端现成的 `/notice/list`）。
 *
 * 这一本只钉一件事的三个面：**"这一轮没取到"不得演成"今天没有公告"**。
 * 司机看到"暂无公告"就不会再刷新，而改线、停运这类公告恰恰最容易在弱网那一刻取不到 ——
 * 与 #38/#42 是同一类缺陷（把未知收成否定），所以三种说法在这里必须是三个不同的返回值。
 */
class DriverNoticesContractTest {

    private fun n(id: Long, title: String, content: String = "正文$id") = Notice(id, title, content, "2026-10-12 08:30")

    private val three = listOf(n(1, "10 月 13 日 3 路班线临时改线"), n(2, "中秋节前安全装载提示"), n(3, "站点交接单填写口径更新"))

    private fun ok(list: List<Notice>, demo: Boolean = false) =
        RemoteState(data = list, success = true, demo = demo)

    private fun failed(err: String) = RemoteState<List<Notice>>(error = err)

    /** 还没取过结论 / 在飞：success=false 且没错误 */
    private val notAsked = RemoteState<List<Notice>>(loading = true)

    @Test
    fun `没取到时只能说没取到，不许写成暂无公告`() {
        val b = noticeBanner(failed("网络不给力，请下拉重试"))
        assertTrue("失败必须占格（不占格就等于藏起来）", b.show)
        assertTrue("失败栏要出现失败字样：${b.title}", b.title.contains("没取到"))
        assertFalse("绝不能出现「暂无公告」：${b.title} / ${b.body}", b.title.contains("暂无") || b.body.contains("暂无"))
        assertTrue("原话要带给司机：${b.body}", b.body.contains("网络不给力，请下拉重试"))
        assertTrue("失败态要给出下一步动作（刷新）：${b.body}", b.body.contains("刷新"))
    }

    @Test
    fun `失败时不报条数`() {
        val b = noticeBanner(failed("加载失败，请下拉重试"))
        assertFalse("失败栏写了条数就是替后端下结论：${b.title}", b.title.contains("条"))
        assertFalse("失败时不得携带任何公告条目：${b.notices}", b.notices.isNotEmpty())
    }

    @Test
    fun `成功且为空时整栏不出现`() {
        val b = noticeBanner(ok(emptyList()))
        assertFalse("后端确实没有公告，不需要占一格播报：${b.title}", b.show)
        assertFalse("空不是失败", b.failed)
    }

    @Test
    fun `还没取过结论时既不出现也不宣称任何事`() {
        val b = noticeBanner(notAsked)
        assertFalse("在飞/从未发过不该出现", b.show)
        assertFalse("未知不得升格成失败", b.failed)
        assertEquals("未知态不带措辞", "", b.title)
        assertEquals("", b.body)
    }

    @Test
    fun `有公告时显示条数并给出一条正文`() {
        val b = noticeBanner(ok(three))
        assertTrue(b.show)
        assertFalse(b.failed)
        assertTrue("标题要说清几条：${b.title}", b.title.contains("3 条"))
        assertEquals("10 月 13 日 3 路班线临时改线", b.body)
        assertEquals("公告", b.tag)
    }

    @Test
    fun `点开时整份公告都在，不许只显示第一条`() {
        val b = noticeBanner(ok(three))
        assertEquals(3, b.notices.size)
        val lines = noticeDetailLines(b.notices)
        assertEquals("列出的行数必须等于公告数（少一行就是有条公告被藏了）", 3, lines.size)
        assertEquals(three.map { it.title }, lines.map { it.first })
        assertEquals(three.map { it.content }, lines.map { it.second })
    }

    @Test
    fun `演示公告必须与平台公告两种说法`() {
        val demo = noticeBanner(ok(three, demo = true))
        val real = noticeBanner(ok(three))
        assertEquals("演示公告", demo.tag)
        assertEquals("公告", real.tag)
        assertFalse("演示栏不得冒充真实公告的来源标签", demo.tag == real.tag)
    }

    @Test
    fun `标题或正文缺失时说实话，不编内容也不留白`() {
        val b = noticeBanner(ok(listOf(n(7, "", "到站时间顺延二十分钟"))))
        assertTrue("整栏没标题也要能显示", b.show)
        assertTrue("缺标题要说缺：${b.body}", b.body.contains("没有标题"))

        val lines = noticeDetailLines(listOf(n(8, "只有一条没有正文的公告", ""), n(9, "", "")))
        assertEquals(2, lines.size)
        assertTrue("正文缺失要原样说缺失：${lines[0].second}", lines[0].second.contains("没有正文"))
        assertTrue("标题缺失要说缺（带后端给的 id，不是编一个）：${lines[1].first}", lines[1].first.contains("没有标题"))
        assertTrue("空标题不得渲染成空白行：${lines[1].first}", lines[1].first.isNotBlank())
    }

    @Test
    fun `失败与有公告两种说法不可能被写成同一句`() {
        val a = noticeBanner(failed("加载失败，请下拉重试"))
        val b = noticeBanner(ok(three))
        val c = noticeBanner(ok(emptyList()))
        val d = noticeBanner(notAsked)
        assertTrue("标题必须两两不同", listOf(a.title, b.title, c.title, d.title).distinct().size >= 3)
        assertTrue("标签必须把失败单独分出来", listOf(a.tag, b.tag).distinct().size == 2)
    }
}
