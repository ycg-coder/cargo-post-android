package com.shanxiang.buspost.data.repo

import com.shanxiang.buspost.core.net.ApiException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V-1（指令 §52）幂等闸本身的五条行为契约。
 *
 * 这里**不接 MockWebServer**，请求用 [CompletableDeferred] 手动放行，
 * 于是"第二次提交发生在第一次仍在途中"是构造出来的事实，而不是靠线程时序碰运气。
 * 时间用注入的假时钟，窗口边界可以精确到毫秒。
 */
class ActionGateTest {

    @Test
    fun `同一目标同一动作并发提交只有一次真的执行`() = runTest {
        val gate = ActionGate()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var executions = 0
        coroutineScope {
            val first = async {
                gate.submit("depart:9101") {
                    executions++
                    entered.complete(Unit)
                    release.await()
                    "sent"
                }
            }
            entered.await()
            val second = gate.submit("depart:9101") { executions++; "dup" }
            assertEquals(GateResult.Duplicate, second)
            release.complete(Unit)
            val result = first.await()
            assertTrue(result is GateResult.Sent)
            assertEquals("sent", (result as GateResult.Sent).value)
        }
        assertEquals("被拦下的那一次绝不能执行请求体", 1, executions)
    }

    @Test
    fun `不同目标之间互不阻塞`() = runTest {
        val gate = ActionGate()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        coroutineScope {
            val first = async {
                gate.submit("arrive:9201:1") { entered.complete(Unit); release.await(); "a" }
            }
            entered.await()
            val other = gate.submit("arrive:9201:2") { "b" }
            assertTrue("键里带了目标 id，另一站台的到站不该被挡住", other is GateResult.Sent)
            val sameTargetOtherAction = gate.submit("depart:9201") { "c" }
            assertTrue(sameTargetOtherAction is GateResult.Sent)
            release.complete(Unit)
            assertTrue(first.await() is GateResult.Sent)
        }
    }

    @Test
    fun `后端拒绝后同一个动作立刻可以重试`() = runTest {
        val gate = ActionGate()
        var attempts = 0
        val failed = runCatching {
            gate.submit("deliver:9301") { attempts++; throw ApiException(1_005_002_000, "订单状态不允许装车") }
        }
        assertTrue(failed.isFailure)
        val retry = gate.submit("deliver:9301") { attempts++; "ok" }
        assertEquals(2, attempts)
        assertTrue("失败还持有窗口 = 一次抖动就把这个动作永久锁死", retry is GateResult.Sent)
        assertEquals("ok", (retry as GateResult.Sent).value)
    }

    @Test
    fun `成功后的窗口内重复提交被丢弃_窗口过了恢复可提交`() = runTest {
        var clock = 1_000L
        val gate = ActionGate { clock }
        var executions = 0
        suspend fun call(): GateResult<String> = gate.submit("depart:9401") { executions++; "x" }
        assertTrue(call() is GateResult.Sent)
        // 窗口内：正好比 heldUntil 早 1ms
        clock += ActionGate.SUCCESS_HOLD_MS - 1
        assertEquals(GateResult.Duplicate, call())
        assertEquals(1, executions)
        // 窗口外：与后端 @Idempotent(timeout = 10, SECONDS) 同口径，10 秒后允许再次提交
        clock += 2
        assertTrue(call() is GateResult.Sent)
        assertEquals(2, executions)
    }

    @Test
    fun `协程被取消后锁不泄漏`() = runTest {
        val gate = ActionGate()
        val entered = CompletableDeferred<Unit>()
        val job = launch {
            gate.submit("depart:9501") { entered.complete(Unit); awaitCancellation() }
        }
        entered.await()
        job.cancelAndJoin()
        val after = gate.submit("depart:9501") { "again" }
        assertTrue("取消没释放锁的话，这个按钮就永远点不动了", after is GateResult.Sent)
    }
}
