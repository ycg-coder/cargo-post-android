package com.shanxiang.buspost.ui.user

import com.shanxiang.buspost.data.model.SendOrder
import com.shanxiang.buspost.data.repo.Repo
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZonedDateTime

/**
 * #57「最早取货时间」契约测试。契约是逐行读后端读出来的（`PickupWindow.kt` 顶部记着三处生产者）：
 * - 写入侧 `AppSendOrderCreateReqVO.java:76` → `TransportOrderServiceImpl.java:149` 落库，
 *   `DispatchServiceImpl.java:4076-4090` 拿它筛任务窗口、`:3513` 作为 `readyTime` 下发算法 ⇒ 这不是备注，会真的改派单；
 * - 出网格式是 **Epoch 毫秒数字**（`TimestampLocalDateTimeDeserializer.java:24-27`），
 *   而那一个实现对**非数字串不报错、静默返回 0** ⇒ 发串不会失败，会安静变成 1970 年；
 * - 回读侧 `AppSendOrderRespVO` 本轮才补上这个键 ⇒ 缺席有两层含义（没填 / 后端没回读），本机分不开。
 */
class PickupWindowContractTest {

    /** 固定时钟：2026-10-13 14:20（服务时区），于是"今天 14:00"这一档必须已经过期 */
    private val now = ZonedDateTime.of(2026, 10, 13, 14, 20, 0, 0, PICKUP_ZONE)

    private fun millisOfLocalText(millis: Long?): String =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(millis ?: -1L), PICKUP_ZONE).toString()

    @Test
    fun `今天已过去的整点根本不在候选里`() {
        val today = pickupOptions(now).first().second
        assertEquals("14:20 时今天的第一个可选整点是 15:00", PickupOption(0, 15), today.first())
        assertTrue("候选里不许出现 14 点及更早", today.none { it.hour <= 14 })
    }

    @Test
    fun `23 点以后今天整档消失而不是留一个空的今天`() {
        val late = ZonedDateTime.of(2026, 10, 13, 23, 30, 0, 0, PICKUP_ZONE)
        val groups = pickupOptions(late)
        assertEquals("只剩明天与后天两档", 2, groups.size)
        assertEquals(1, groups.first().second.first().dayOffset)
    }

    @Test
    fun `候选只有三天`() {
        assertEquals(PICKUP_DAY_COUNT, pickupOptions(now).size)
    }

    @Test
    fun `越界的档位不产出毫秒`() {
        assertNull(pickupMillisOf(now, -1, 15))
        assertNull(pickupMillisOf(now, PICKUP_DAY_COUNT, 15))
        assertNull(pickupMillisOf(now, 0, -1))
        assertNull(pickupMillisOf(now, 0, 24))
        assertNull("今天 14:00 已经过了", pickupMillisOf(now, 0, 14))
    }

    @Test
    fun `毫秒按服务时区算而不是设备时区`() {
        val m = pickupMillisOf(now, 0, 15)
        assertNotNull(m)
        assertEquals("2026-10-13T15:00", millisOfLocalText(m))
    }

    @Test
    fun `提交值只收未来正数毫秒`() {
        val future = pickupMillisOf(now, 1, 9)!!
        assertEquals(future, pickupSubmitValue(future, now.toInstant().toEpochMilli()))
        val nowMillis = now.toInstant().toEpochMilli()
        assertNull("没选不发", pickupSubmitValue(null, nowMillis))
        assertNull("0 不发（后端会把 0 解成 1970）", pickupSubmitValue(0L, nowMillis))
        assertNull("负数不发", pickupSubmitValue(-5L, nowMillis))
        assertNull("正好等于当前不发", pickupSubmitValue(nowMillis, nowMillis))
        assertNull("过去不发", pickupSubmitValue(nowMillis - 1000L, nowMillis))
    }

    @Test
    fun `报文里的这一键未选时是 null 而不是 0 或字符串`() {
        val vm = SendViewModel()
        assertNull(vm.buildSendForm()["earliestPickupTime"])
        assertFalse(
            "null 会被 Api.kt:308 整个键丢掉，界面不许自己拼串绕过",
            vm.buildSendForm()["earliestPickupTime"] is String,
        )
    }

    @Test
    fun `报文里的这一键选了之后必须是 Long`() {
        val vm = SendViewModel()
        vm.pickupDay = 2
        vm.pickupHour = 9
        val v = vm.buildSendForm()["earliestPickupTime"]
        assertTrue("出网只能是 Epoch 毫秒数字：${v?.javaClass}", v is Long)
        assertTrue((v as Long) > System.currentTimeMillis())
    }

    @Test
    fun `隔夜草稿恢复出的旧时刻整个键不发而不是发一个过去的时间`() {
        val vm = SendViewModel()
        vm.pickupDay = 0
        vm.pickupHour = 3
        assertNull("凌晨 3 点在今天 14:20 之后不可能还有值", vm.pickupSubmitMillis(now))
    }

    @Test
    fun `回读只认数字毫秒`() {
        assertEquals("1760000000000", pickupMillisFromJson(JSONObject("""{"earliestPickupTime":1760000000000}"""))?.toString())
        assertNull("非数字串一律当没给：后端那一侧会静默变 0，本机不重复这个形状",
            pickupMillisFromJson(JSONObject("""{"earliestPickupTime":"2026-10-13 15:00:00"}""")))
        assertNull("数字串也当没给（multiNum 那种 coerce 正是事故来源）",
            pickupMillisFromJson(JSONObject("""{"earliestPickupTime":"1760000000000"}""")))
        assertNull(pickupMillisFromJson(JSONObject("""{"earliestPickupTime":0}""")))
        assertNull(pickupMillisFromJson(JSONObject("""{}""")))
    }

    @Test
    fun `SendOrder 解析走同一条只认数字的规则`() {
        val withValue = SendOrder.parse(JSONObject("""{"orderNo":"SH1","earliestPickupTime":1760000000000}"""))
        assertEquals("1760000000000", withValue.earliestPickupMillis?.toString())
        val asText = SendOrder.parse(JSONObject("""{"orderNo":"SH1","earliestPickupTime":"2026-10-13 15:00:00"}"""))
        assertNull("串形态不许被读成 1970 年", asText.earliestPickupMillis)
        val absent = SendOrder.parse(JSONObject("""{"orderNo":"SH1"}"""))
        assertNull(absent.earliestPickupMillis)
    }

    @Test
    fun `未选那一格念不限制而不是后端记了个时间`() {
        assertEquals(PICKUP_UNSET_TEXT, pickupSelectionText(null))
        assertTrue(pickupSelectionText(pickupMillisOf(now, 0, 15)).startsWith("15:00"))
    }

    @Test
    fun `缺席那一格不许塌成随时可取`() {
        assertEquals(PICKUP_ABSENT_TEXT, pickupReadbackText(null))
        assertFalse("没给就是没给，不许被兜成'不限制'", pickupReadbackText(null) == PICKUP_UNSET_TEXT)
    }

    @Test
    fun `三种回执说法各不相同`() {
        val unset = pickupSubmitConclusion(null, null)
        val both = pickupSubmitConclusion(1760000000000L, 1760000000000L)
        val noRead = pickupSubmitConclusion(1760000000000L, null)
        assertEquals(3, setOf(unset, both, noRead).size)
        assertFalse("没填的时候不许说'已提交'", unset.contains("已提交"))
        assertTrue("回读缺席要说分不开", noRead.contains(PICKUP_ABSENT_TEXT))
    }

    @Test
    fun `取值按整点对齐所以本地幂等键不会被这个键顶开`() {
        // 明天 09:00 —— 用 day=1 而不是今天某一整点，否则这条用例自己就随时钟漂（今天 18:00 到晚上就过期了）
        val vm = SendViewModel()
        vm.pickupDay = 1
        vm.pickupHour = 9
        val earlier = ZonedDateTime.of(2026, 10, 13, 14, 59, 0, 0, PICKUP_ZONE)
        val later = ZonedDateTime.of(2026, 10, 13, 17, 30, 0, 0, PICKUP_ZONE)
        val m1 = vm.pickupSubmitMillis(earlier)
        val m2 = vm.pickupSubmitMillis(later)
        assertEquals("同一天里两次点击必须拿到同一个出网值", m1, m2)
        val k1 = Repo.userCreateKey("/app-api/transport/send/create", mapOf("earliestPickupTime" to m1))
        val k2 = Repo.userCreateKey("/app-api/transport/send/create", mapOf("earliestPickupTime" to m2))
        assertEquals("报文键值没变 ⇒ 本地幂等键没变，连点两次仍会被拦", k1, k2)
        assertTrue(millisOfLocalText(m1).endsWith("T09:00"))
    }

    @Test
    fun `日期档位标签带具体日期且只到后天`() {
        val labels = pickupOptions(now).map { it.first }
        assertEquals(3, labels.size)
        assertTrue(labels[0].startsWith("今天"))
        assertTrue(labels[1].startsWith("明天"))
        assertTrue(labels[2].startsWith("后天"))
        assertTrue("标签里要带具体日期，不能只写'今天'", labels[0].contains("10-13"))
        assertTrue(labels[2].contains("10-15"))
    }

    @Test
    fun `后果说明在纯层只有一份且带窗口那件真事`() {
        assertTrue(PICKUP_DISPATCH_NOTE.contains("窗口"))
        assertTrue("不许承诺具体送达时间", !PICKUP_DISPATCH_NOTE.contains("送达"))
    }
}
