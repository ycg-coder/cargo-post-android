package com.shanxiang.buspost.core.net

import com.shanxiang.buspost.ServerTest
import com.shanxiang.buspost.TestHost
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * 指令 §六：演示构建的失败回落面**只准**是"传输失败"和"业务失败"两类真实后端问题。
 * `IllegalStateException` / `NullPointerException` / `ClassCastException` / `LinkageError` 这些客户端缺陷
 * 一旦被同一个 catch 接住，就会在演示包里变成一份"看起来成功"的数据 —— 缺陷再也复现不了，
 * 而看的人会以为后端就是这样。这一类是**只有演示开关打开时才可能踩到**的破口，
 * 所以必须在这里钉住：探针异常原样穿出、且一次请求都没上网络。
 */
class DemoExceptionIsolationTest : ServerTest() {

    /** 探针：把缺陷挂在 URL 组装这一步（rawExecute 内部、发请求之前），形状与真实 NPE 一致 */
    private fun probe(fault: Throwable) = object : Any() {
        override fun toString(): String = throw fault
    }

    private fun expectEscape(fault: Throwable) = runBlocking {
        try {
            Api.get("/app-api/transport/notice/list", mapOf("probe" to probe(fault)))
            fail("客户端缺陷被洗成了演示数据：${fault.javaClass.simpleName}")
        } catch (e: Throwable) {
            // ⚠ 这里不能用 assertSame(探针, 抛出物)：`withContext` 的协程边界会做一次 stack-trace recovery，
            // 抛出来的是**同一类型、同一 cause 链**的副本，探针本体在 cause 里（实测 caught 的 trace 带
            // `_COROUTINE._BOUNDARY._`）。类型不变 + 本体在链上 + 一次请求没发出去，才是这一层真正的契约。
            assertSame("穿出过程中没有任何一层把它换成别的类型（换成 NetworkException 就是被接住过）",
                fault.javaClass, e.javaClass)
            assertTrue(
                "cause 链里必须找得到探针本体：${e.stackTraceToString().take(200)}",
                generateSequence<Throwable>(e as Throwable?) { it.cause }.any { it === fault },
            )
        }
        assertEquals("缺陷发生在组 URL 时：一个请求都不该发到后端", 0, server.requestCount)
    }

    @Test
    fun `演示构建不吃 IllegalStateException`() = expectEscape(IllegalStateException("探针：状态机不自洽"))

    @Test
    fun `演示构建不吃 NullPointerException`() = expectEscape(NullPointerException("探针：空引用"))

    @Test
    fun `演示构建不吃 ClassCastException`() = expectEscape(ClassCastException("探针：类型猜错了"))

    @Test
    fun `演示构建不吃 LinkageError`() = expectEscape(LinkageError("探针：依赖在运行时对不上"))

    @Test
    fun `正式构建同样原样抛出缺陷`() = runBlocking {
        TestHost.strictBackend()
        expectEscape(IllegalStateException("探针：正式包也该把缺陷交出去"))
    }

    @Test
    fun `传输失败仍然按开关回落演示数据`() = runBlocking {
        TestHost.demoBackend()
        respond("<html>Bad Gateway</html>", 500, "text/html")
        val p = Api.get("/app-api/transport/notice/list")
        assertTrue("演示构建里传输失败该回落", p.demo)
        assertTrue("回落要给出真内容，不是空壳", p.list.isNotEmpty())
    }

    @Test
    fun `业务失败仍然按开关回落演示数据`() = runBlocking {
        TestHost.demoBackend()
        respondBizError(1009004001, "商品库存不足")
        val p = Api.get("/app-api/transport/notice/list")
        assertTrue(p.demo)
        assertNotNull(p.arr)
    }
}
