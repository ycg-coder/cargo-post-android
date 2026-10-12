package com.shanxiang.buspost.ui.driver

import com.shanxiang.buspost.data.model.Notice
import com.shanxiang.buspost.data.repo.RemoteState

/**
 * 司机端「平台公告」那一栏的全部判定（界面只渲染这里给的结论，与 #42 天气那一格同一套分工）。
 *
 * 为什么要有这一层：公告通路后端早就有（`/notice/list`，用户端首页一直在用），司机端此前一条都不显示；
 * 而接到司机端最容易被写坏的地方是**把"这一轮没取到"演成"今天没有公告"** —— 司机看到"暂无公告"就不会再刷新，
 * 真出事的公告（改线、停运）恰恰最容易在弱网那一刻取不到。所以三种说法在这里就必须是三个值。
 */
data class NoticeBanner(
    /** 这一栏要不要占一格：拉成功且后端确实没有公告 → 不占（"没有公告"不值得播报） */
    val show: Boolean,
    /** 失败态：必须占格，而且措辞只能是"没取到"，绝不许写成"暂无公告" */
    val failed: Boolean,
    /** 来源标签的文字：判定放在这里，页面不再自己拼第二套说法 */
    val tag: String,
    val title: String,
    val body: String,
    /** 这一批是演示数据（release 恒 false）：演示公告不得冒充平台公告 */
    val demo: Boolean,
    /** 点开看正文时列出的公告：全量给，页面不许只留第一条就把其余藏掉 */
    val notices: List<Notice>,
)

private const val EMPTY_TAG = "公告"
private const val DEMO_TAG = "演示公告"
private const val FAILED_TAG = "没取到"

/**
 * 三种空分得开：
 * · `error` 非空 → 失败栏（这一轮请求根本没成功，条数未知，不能说"0 条"）。
 * · 还没取过结论（`success=false` 且没错误：在飞 / 从未发过）→ 整栏不出现，也不替后端下任何结论。
 * · 取成功且列表为空 → 整栏不出现（真没有）。
 */
fun noticeBanner(state: RemoteState<List<Notice>>): NoticeBanner {
    val err = state.error
    if (err != null) {
        return NoticeBanner(
            show = true,
            failed = true,
            tag = FAILED_TAG,
            title = "平台公告没取到",
            body = "$err（下拉刷新或点「刷新」再取一次）",
            demo = false,
            notices = emptyList(),
        )
    }
    if (!state.success) {
        return NoticeBanner(false, false, EMPTY_TAG, "", "", demo = state.demo, notices = emptyList())
    }
    val list = state.data ?: emptyList()
    if (list.isEmpty()) {
        return NoticeBanner(false, false, EMPTY_TAG, "", "", demo = false, notices = emptyList())
    }
    val head = list.firstOrNull { it.title.isNotBlank() } ?: list.first()
    return NoticeBanner(
        show = true,
        failed = false,
        tag = if (state.demo) DEMO_TAG else EMPTY_TAG,
        title = "平台公告 ${list.size} 条",
        body = head.title.ifBlank { "（这条公告没有标题，点开看正文）" },
        demo = state.demo,
        notices = list,
    )
}

/** 点开那一栏之后的列表行：正文缺失时说明缺失，不编一句"暂无内容"糊过去 */
fun noticeDetailLines(notices: List<Notice>): List<Pair<String, String>> = notices.map { n ->
    val head = n.title.ifBlank { "（第 ${n.id} 号公告没有标题）" }
    head to n.content.ifBlank { "（这条公告没有正文）" }
}
