package com.shanxiang.buspost.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 通知的锁屏可见性（任务 #47 的第一格）。
 *
 * `Notifier.push` 以前**从没设过** `setVisibility` / `setPublicVersion`，而正文由 `AlertEngine`
 * 直接拼站名与到站分钟数（`AlertEngine.kt:69/90/118`）⇒ 手机放在桌上不解锁就能看到
 * "某村 3 分钟"，等于把行程挂在锁屏上（与 #36 记的"PII 无脱敏"同一族）。
 *
 * 只钉得住的形状是"哪条渠道需要遮掩 + 遮掩句本身不含行程信息"；至于真的没设 publicVersion
 * 会怎样，属于系统渲染行为，JVM 测不到 ⇒ 那半留给真机复拍，不在此处假装已验。
 */
class NotificationPrivacyContractTest {

    @Test
    fun `行程类渠道必须给出锁屏替代句`() {
        // WEATHER 也在名单里点名：天气正文本身不含行程（D-158 之后更是只带天况/气温/建议），
        // 但这一格的规矩是"白名单只放确认无个人信息的那一条，其余默认遮掩"——
        // 天气要不要放进白名单（锁屏直接可见）是产品口径，不在这里顺手放宽。
        for (ch in listOf(Channels.ORDER, Channels.SHIFT, Channels.ARRIVAL, Channels.WEATHER)) {
            val pub = lockScreenTextFor(ch)
            assertTrue("$ch 应被遮掩", pub != null)
            assertTrue("遮掩句要让用户知道该解锁看：$pub", pub!!.contains("解锁"))
            // 遮掩句里不许残留任何行程形状的信息（数字=分钟数/车次，"村"=收件站点名）
            assertFalse("遮掩句不含分钟数：$pub", pub.any { it.isDigit() })
            assertFalse("遮掩句不含站名用字：$pub", pub.contains("村") || pub.contains("站"))
        }
    }

    @Test
    fun `后台服务提示不遮掩 以免用户以为通知坏了`() {
        // 常驻通知正文是"正在上报位置"这类无个人信息的服务状态；
        // 给它套一句"解锁查看"反而让人误以为通知坏了，且它与行程无关。
        assertNull(lockScreenTextFor(Channels.SERVICE))
    }

    @Test
    fun `未知渠道按最保守的处理`() {
        // 新渠道忘了登记时宁可遮掩，也不许默认把正文摊到锁屏上
        val pub = lockScreenTextFor("some-future-channel")
        assertTrue("新渠道忘了登记时必须默认遮掩，而不是默认摊开正文", pub != null)
    }
}
