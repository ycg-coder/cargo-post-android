package com.shanxiang.buspost.ui.driver

import com.shanxiang.buspost.data.model.HandoverTask
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 交接签收签名（#20 阶段 B）的纯层契约。逐字段读的生产者：
 * `AppDriverHandoverRespVO.java:52-53`（新增的 `signUrl` 回读键）、
 * `AppDriverHandoverConfirmReqVO.java:23`（提交键）、
 * `HandoverServiceImpl.java:241-243`（写入侧纪律：**没带就不写**，空串不落库）、
 * `V034__handover_sign_url.sql`（列可空、默认 NULL，没有"是否核对过"的第二个状态）。
 *
 * 钉四件事：
 * · "签没签"的门槛只有一处（两划、每划三折点）——手掌压屏、随手一划都不算签成；
 * · 签了但没拿到 URL ⇒ 拦停提交；没签 ⇒ 不拦（后端那一格保持 NULL，不等于"核对过这人没签"）；
 * · 回执四种回读组合各一句，任何一句都不许把"缺"念成确定的否定结论；
 * · 模型缺 `signUrl` 键时是空串（不塌成 null 也不编一个地址）。
 */
class HandoverSignatureContractTest {

    private fun pts(vararg pairs: Pair<Float, Float>) = pairs.map { InkPoint(it.first, it.second) }

    @Test
    fun `一划再长也不算签成——门槛是两划`() {
        val oneLongStroke = listOf(pts(0f to 0f, 5f to 3f, 10f to 6f, 20f to 9f, 40f to 12f))
        assertEquals(1, HandoverSignature.strokeCount(oneLongStroke))
        assertFalse(HandoverSignature.hasInk(oneLongStroke))
    }

    @Test
    fun `两划各达门槛才算签成`() {
        val two = listOf(
            pts(0f to 0f, 4f to 2f, 9f to 1f),
            pts(12f to 8f, 16f to 3f, 22f to 9f),
        )
        assertEquals(2, HandoverSignature.strokeCount(two))
        assertTrue(HandoverSignature.hasInk(two))
    }

    @Test
    fun `按下即抬手的毛刺不数进划数`() {
        val jitter = listOf(pts(3f to 3f, 4f to 3f), pts(9f to 9f, 10f to 9f, 11f to 9f), pts(1f to 1f, 2f to 2f))
        // 三笔里只有一笔够 3 个折点 ⇒ 仍不足两划
        assertEquals(1, HandoverSignature.strokeCount(jitter))
        assertFalse(HandoverSignature.hasInk(jitter))
    }

    @Test
    fun `空列表与只有空划都不算签`() {
        assertFalse(HandoverSignature.hasInk(emptyList()))
        assertFalse(HandoverSignature.hasInk(listOf(emptyList(), emptyList(), emptyList())))
    }

    @Test
    fun `签了但上传没拿到 URL 必须拦停提交`() {
        assertTrue(HandoverSignature.shouldBlockSubmit(signed = true, signUrl = ""))
    }

    @Test
    fun `没签时不拦停——那一格由后端保持 NULL`() {
        assertFalse(HandoverSignature.shouldBlockSubmit(signed = false, signUrl = ""))
    }

    @Test
    fun `签了且拿到 URL 时不拦停`() {
        assertFalse(HandoverSignature.shouldBlockSubmit(signed = true, signUrl = "https://x/y.png"))
    }

    @Test
    fun `回执四种组合各说一句，缺谁就点名谁`() {
        assertEquals(
            "交接已提交，凭证照片与签名均已入库",
            HandoverSignature.receiptFor(photoStored = true, signStored = true),
        )
        assertEquals(
            "交接已提交，凭证照片已入库；签名那一格回读里没有",
            HandoverSignature.receiptFor(photoStored = true, signStored = false),
        )
        assertEquals(
            "交接已提交，签名已入库；这一单没附凭证照片",
            HandoverSignature.receiptFor(photoStored = false, signStored = true),
        )
        assertEquals(
            "交接已提交，照片与签名两格后端都回读为空",
            HandoverSignature.receiptFor(photoStored = false, signStored = false),
        )
    }

    @Test
    fun `回执不把缺签名念成核对过的否定结论`() {
        // 「没签」与「后端从没被问过有没有签」在这一格里必须是同一句描述：只报回读为空，
        // 不许出现"该司机拒绝签名/未核验身份"这类后端从没给过的事实。
        val text = HandoverSignature.receiptFor(photoStored = true, signStored = false)
        assertTrue(text.contains("回读里没有"))
        assertFalse(text.contains("拒绝"))
        assertFalse(text.contains("未核验"))
    }

    @Test
    fun `模型缺 signUrl 键时是空串而不是编一个地址`() {
        val task = HandoverTask.parse(JSONObject().put("id", 7).put("statusName", "待交接"))
        assertEquals("", task.signUrl)
    }

    @Test
    fun `模型读到 signUrl 就原样带出，不跟着 photoUrl 走`() {
        val task = HandoverTask.parse(
            JSONObject().put("id", 7).put("photoUrl", "https://x/p.jpg").put("signUrl", "https://x/s.png"),
        )
        assertEquals("https://x/s.png", task.signUrl)
        assertEquals("https://x/p.jpg", task.photoUrl)
    }

    @Test
    fun `导出尺寸是定值——同一签名不随设备密度漂`() {
        assertEquals(1024, HandoverSignature.EXPORT_WIDTH_PX)
        assertEquals(480, HandoverSignature.EXPORT_HEIGHT_PX)
    }
}
