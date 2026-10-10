package com.shanxiang.buspost.data.repo

import com.shanxiang.buspost.ServerTest
import com.shanxiang.buspost.TestHost
import com.shanxiang.buspost.core.net.ApiException
import com.shanxiang.buspost.data.store.Prefs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * 用户端"创建订单"的重复提交（F-2 第 6 步 / v2.1 清单第 6 项）。
 *
 * 钉的是**三个维度**与**两种释放**：
 * · 键的维度 = 端点 + 登录身份 + 整份报文（抄后端 `UserIdempotentKeyResolver.java:21-25` 的
 *   `MD5(方法签名 + 方法参数 + userId + userType)`，不是本机发明的）。少一个维度就会误拦或漏拦 ——
 *   少"报文"就是"改了数量还被拦"，少"端点"就是"下商城单挡住寄货单"，少"身份"就是"换账号继承上一位的窗口"；
 * · 释放的两种：业务异常立刻放（与后端 `Idempotent.java:61` 的 `deleteKeyWhenException = true` 同口径）、
 *   成功才占 10 秒（与两处注解的 `@Idempotent(timeout = 10, SECONDS)` 同口径）。
 *
 * ⚠ 取消（用户划走页面 / ViewModel 销毁）**不在这里钉**：`Api` 走的是 `withContext(Dispatchers.IO)` 里的
 * **阻塞** `execute()`（`Api.kt:239`/`:295`），JVM 用例没法确定性地"在请求在途时取消"。
 * 取消即放闸的语义钉在闸门本体（`ActionGateTest` 的"协程被取消不许泄闸"那条），两个域用的是同一个类，
 * 不在这里用一份假确定性重复一遍。真机上"手指划走"那条路径一律 `NOT VERIFIED ON DEVICE`。
 */
class UserCreateIdempotencyTest : ServerTest() {

    private val productBody = mapOf<String, Any?>(
        "productId" to 7L,
        "quantity" to 2,
        "receiverName" to "李秀兰",
        "receiverMobile" to "13800004021",
        "receiverAddress" to "山香村 3 组",
        "remark" to "白天有人在",
        "userMobile" to MOBILE,
    )

    private suspend fun submitOrder(quantity: Int = 2, address: String = "山香村 3 组"): Ack =
        Repo.createProductOrder(
            productId = 7L,
            quantity = quantity,
            receiverName = "李秀兰",
            receiverMobile = "13800004021",
            receiverAddress = address,
            remark = "白天有人在",
        )

    private fun ok(orderNo: String) = MockResponse().setBody("""{"code":0,"msg":"","data":{"orderNo":"$orderNo"}}""")

    @Test
    fun `连点五次同一份订单只有第一次真的出网`() {
        TestHost.strictBackend()
        Prefs.mobile = MOBILE
        server.enqueue(ok("SC20260930001"))
        val acks = runBlocking { (1..5).map { submitOrder() } }
        assertEquals("被拦下的四次一个字节都不许出网", 1, server.requestCount)
        assertEquals(1, acks.count { !it.notSent })
        assertEquals(4, acks.count { it.notSent })
    }

    @Test
    fun `被拦下的那次不许带回上一次的订单号`() {
        TestHost.strictBackend()
        Prefs.mobile = MOBILE
        server.enqueue(ok("SC20260930001"))
        val (first, second) = runBlocking { Pair(submitOrder(), submitOrder()) }
        assertEquals("SC20260930001", first.orderNo)
        assertTrue(second.notSent)
        assertEquals("拦下就是一次空回执，页面拿不到单号才不会张冠李戴", "", second.orderNo)
        assertEquals("", second.pickupCode)
        assertEquals(0.0, second.amount, 0.001)
        assertFalse("没出网更不许标成演示数据（那是另一种'后端给了东西'）", second.demo)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `商城单回执只认 orderNo（后端那个 VO 就只有 id 与 orderNo）`() = runBlocking {
        // 2026-10-08 逐行读 AppProductOrderCreateRespVO：字段只有 id、orderNo。
        // amount / totalPrice / no 是客户端三面 0 生产者的自造别名，只发它们时回执必须留空，
        // 而不是塌成一个看起来像后端给的 0.0 / "SC-DEAD"。
        TestHost.strictBackend()
        Prefs.mobile = MOBILE
        server.enqueue(
            MockResponse().setBody(
                """{"code":0,"msg":"","data":{"id":9001,"amount":66.0,"totalPrice":66.0,"no":"SC-DEAD"}}"""
            )
        )
        val ack = submitOrder()
        assertFalse("这一次是真出网，不是被本机闸拦下（拦下的空回执证不了别名）", ack.notSent)
        assertEquals(1, server.requestCount)
        assertEquals("", ack.orderNo)
        assertEquals(0.0, ack.amount, 0.001)
    }

    @Test
    fun `改了数量或地址的第二次必须真的出网`() {
        TestHost.strictBackend()
        Prefs.mobile = MOBILE
        server.enqueue(ok("SC1"))
        server.enqueue(ok("SC2"))
        server.enqueue(ok("SC3"))
        val acks = runBlocking { listOf(submitOrder(quantity = 2), submitOrder(quantity = 3), submitOrder(quantity = 3, address = "白朝村驿站")) }
        assertTrue("报文不同就是另一张单，本机闸门不许比后端更严", acks.none { it.notSent })
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `业务失败后立刻允许重试`() {
        TestHost.strictBackend()
        Prefs.mobile = MOBILE
        respondBizError(1005002001, "商品库存不足")
        val failed = runBlocking { runCatching { submitOrder() } }
        assertTrue("业务失败必须原样抛出", failed.isFailure)
        assertEquals(1, server.requestCount)

        server.enqueue(ok("SC-OK"))
        val retry = runBlocking { submitOrder() }
        assertFalse("失败还占着窗口，用户就 10 秒下不了单，而后端随时收", retry.notSent)
        assertEquals("SC-OK", retry.orderNo)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `后端回 900 时原样透出那句话且不再由本机代拦`() {
        TestHost.strictBackend()
        Prefs.mobile = MOBILE
        // 后端 @Idempotent 的 message 原文：AppProductOrderController.java:38 → IdempotentAspect.java:52 → GlobalErrorCodeConstants.java:36
        respondBizError(900, "订单正在创建中，请勿重复提交")
        val e = runBlocking { runCatching { submitOrder() }.exceptionOrNull() }
        assertTrue("900 必须还是 ApiException，不能塌成网络异常", e is ApiException)
        assertEquals(900, (e as ApiException).code)
        assertEquals("后端给的这句话就是失败原因，不许换成'加载失败'那种通用文案", "订单正在创建中，请勿重复提交", e.message)

        respondBizError(900, "订单正在创建中，请勿重复提交")
        val second = runBlocking { runCatching { submitOrder() } }
        assertTrue("本机不替后端记住它的键：900 之后闸已放，第二次照样出网、由后端再判一次", second.isFailure)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `键只由三个维度决定且不含明文个人信息`() {
        Prefs.mobile = MOBILE
        val mall = "/app-api/transport/product-order/create"
        val base = Repo.userCreateKey(mall, productBody)
        assertTrue("键是 create: + 十六进制摘要", Regex("^create:[0-9a-f]{64}$").matches(base))
        for (secret in listOf("李秀兰", "13800004021", "山香村 3 组", "白天有人在")) {
            assertFalse("收货人姓名/手机号/地址/备注都不许以可读形式进键表：$secret", base.contains(secret))
        }
        assertEquals("同一份报文同一端点同一身份必须同键（否则拦不住重复点击）", base, Repo.userCreateKey(mall, productBody))
        assertNotEquals("端点是维度之一：商城单不许挡寄货单", base, Repo.userCreateKey("/app-api/transport/send/create", productBody))
        assertNotEquals("报文是维度之一：数量不同就是另一张单", base, Repo.userCreateKey(mall, productBody + ("quantity" to 3)))
        Prefs.mobile = "13900001111"
        assertNotEquals("登录身份是维度之一：换账号不许继承上一位的窗口", base, Repo.userCreateKey(mall, productBody))
        Prefs.mobile = MOBILE
        assertNotEquals(
            "值为 null 与值为空串不许同键（'后端字段缺失'与'用户把它清空了'是两件事）",
            Repo.userCreateKey("/p", mapOf("a" to null, "b" to 1)),
            Repo.userCreateKey("/p", mapOf("a" to "", "b" to 1)),
        )
        assertEquals(
            "键只看内容，不看 map 的遍历顺序（同一份表单换个装法不许变成两次提交）",
            Repo.userCreateKey("/p", mapOf("a" to "1", "b" to "2")),
            Repo.userCreateKey("/p", mapOf("b" to "2", "a" to "1")),
        )
    }

    @Test
    fun `两个下单端点各走各的窗口`() {
        TestHost.strictBackend()
        Prefs.mobile = MOBILE
        server.enqueue(ok("SC1"))
        server.enqueue(MockResponse().setBody("""{"code":0,"msg":"","data":{"orderNo":"BS1","pickupCode":"8421"}}"""))
        val form = mapOf<String, Any?>("goodsName" to "山村土蜂蜜", "itemCount" to 2, "photoUrl" to "https://cdn.example.com/c.jpg")
        val acks = runBlocking { listOf(submitOrder(), Repo.createSendOrder(form)) }
        assertTrue("同一秒里下一张商城单和一单寄货不该互相挡", acks.none { it.notSent })
        assertEquals(2, server.requestCount)
        val dup = runBlocking { Repo.createSendOrder(form) }
        assertTrue(dup.notSent)
        assertEquals("重复的那一次没有再多发一个请求", 2, server.requestCount)
    }

    @Test
    fun `用户域与司机域是两把闸不是一个实例`() {
        val gates = Repo::class.java.declaredFields.filter { it.type.simpleName == "ActionGate" }.map { it.name }.sorted()
        assertEquals("两把闸：一域一把。共用一把的话一次下单会去挡司机动作，硬规则 1 就破了", listOf("actionGate", "userCreateGate"), gates)
    }

    @Test
    fun `演示回落那次确实出过网随后的重复点击仍被拦`() {
        TestHost.demoBackend()
        Prefs.mobile = MOBILE
        server.enqueue(MockResponse().setResponseCode(502).setHeader("Content-Type", "text/html").setBody("<html>bad gateway</html>"))
        val fallback = runBlocking { submitOrder() }
        assertTrue("debug 形态回落演示单必须标出来", fallback.demo)
        assertFalse(fallback.notSent)
        assertEquals(1, server.requestCount)

        val again = runBlocking { submitOrder() }
        assertTrue("回落也是'发出去过'，10 秒内的第二次点击同样不许再出网", again.notSent)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `闸门不会把等待中的请求排进队列`() {
        // 这条钉的是"丢弃"而不是"排队"：换成 Mutex.withLock 的写法，第二次点击会在第一次完成后**照样发给后端**，
        // 那正是后端 @Idempotent 要拦的东西，本机不许把它放过去。
        TestHost.strictBackend()
        Prefs.mobile = MOBILE
        server.enqueue(
            MockResponse()
                .setBody("""{"code":0,"msg":"","data":{"orderNo":"SC-SLOW"}}""")
                .setBodyDelay(600, TimeUnit.MILLISECONDS)
        )
        val acks = runBlocking {
            coroutineScope { (1..3).map { async { submitOrder() } }.map { it.await() } }
        }
        assertEquals(1, server.requestCount)
        assertEquals(1, acks.count { !it.notSent })
        assertEquals(2, acks.count { it.notSent })
    }

    companion object {
        private const val MOBILE = "13800004021"
    }
}
