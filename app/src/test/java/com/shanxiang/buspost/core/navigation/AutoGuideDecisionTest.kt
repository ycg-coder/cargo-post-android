package com.shanxiang.buspost.core.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「自动进导航」的判定（用户口径 A）。四条各钉一个条件：
 * 删掉纯函数里任何一项，都必须让这里的一条用例红 —— 否则这条自动化就是没有闸门的行为。
 */
class AutoGuideDecisionTest {

    @Test
    fun `三件齐了才自动进导航`() {
        assertTrue(shouldAutoOpenGuide(canNavigate = true, hasNavigableStop = true, alreadyOpenedThisProcess = false))
    }

    @Test
    fun `引擎不可用或拿不到真实起点时不跳`() {
        assertFalse(
            "canNavigate=false 还跳，等于没有起点也去导航",
            shouldAutoOpenGuide(canNavigate = false, hasNavigableStop = true, alreadyOpenedThisProcess = false),
        )
    }

    @Test
    fun `调度没给任务点时不跳`() {
        assertFalse(
            "没有可导的点还跳，就是导航自己决定去哪里",
            shouldAutoOpenGuide(canNavigate = true, hasNavigableStop = false, alreadyOpenedThisProcess = false),
        )
    }

    @Test
    fun `一个进程只自动跳一次否则司机结束导航后出不去`() {
        assertFalse(
            "已经自动跳过还再跳，「结束导航」就成了无效按钮（真机实测过这个死循环）",
            shouldAutoOpenGuide(canNavigate = true, hasNavigableStop = true, alreadyOpenedThisProcess = true),
        )
    }
}
