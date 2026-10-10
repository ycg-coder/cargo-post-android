package com.shanxiang.buspost.data

import com.shanxiang.buspost.ServerTest
import com.shanxiang.buspost.TestHost
import com.shanxiang.buspost.data.model.Earnings
import com.shanxiang.buspost.data.repo.DemoData
import com.shanxiang.buspost.data.repo.Repo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 指令 F-2 第 5 步「数字默认值按 A（真零值）/B（缺字段）/C（网络异常）/D（UI 占位）分类，禁止把 B/C 当 A 说」
 * 首要修复点的契约测试：司机运营统计（`/transport/driver/earnings`）。
 *
 * 破口原文（改前）：`Repo.driverEarnings` 用 `multiNum("todayAmount","todayEarnings","today")`、
 * `multiNum("weekAmount","week")`、`multiNum("totalWeight","weight")`、`multiArr("items","details","records")`、
 * `multiNum("amount","fee")`、`multiStr("date","day")` 六处读法，全部走**缺键即 0 / 空串**的非可空通道。
 *
 * 三方实测（2026-09-30，逐字段核）：
 * | 键 | 后端生产者 | 小程序消费者 | 结论 |
 * |---|---|---|---|
 * | todayAmount / totalAmount / totalOrders / todayOrders / shiftCount / pendingCount / records | AppDriverEarningsRespVO.java:14-26，生产者 DriverAppServiceImpl.java:365-391 逐个 set | earnings.js:64-69 读前五个 | 真实键，但**全是包装类型（Long/BigDecimal）⇒ 可缺席**，所以非可空读法仍属 B→A |
 * | records[].totalAmount / weightKg / statusName / createTime / orderNo / goodsName | AppDriverEarningsRecordRespVO.java:14-26，toRecord:1846-1859 | earnings.js:57-61 | 真实键；goodsName/weightKg **只在 CargoOrderDO 命中时才 set** ⇒ 天然可缺席 |
 * | weekAmount、monthAmount、totalWeight | `grep -rn` 后端 yudao-module-transport/src/main **0 命中** | 小程序不读 | 三面全无生产者，旧值 452.0/1830.5/246.8 是 DemoData 自造的第四个真相源 |
 * | items、details、amount、fee、date、day、type、bizType、todayEarnings、totalEarnings、orderCount、count | 0 生产者 | 0 读点 | 自造别名 ⇒ 真机上明细每笔都渲染成 ¥0 |
 *
 * createTime 口径：`YudaoJacksonAutoConfiguration.java:43,62` 把 LocalDateTime 注册成
 * `TimestampLocalDateTimeSerializer`（EpochMillis），所以后端发的是 13 位毫秒数而不是 ISO 串；
 * 旧 `ts()` 只处理 ISO 串，会把 "1790558040000" 原样显示。⑤ 钉住这条契约。
 *
 * 测试走 `Repo.driverEarnings()`（MockWebServer 真信封）而不是只测模型：破口在解析点，
 * 只测 Earnings 的构造默认值会放过"解析层又把缺键读成 0"这一整类复发。
 */
class EarningsContractTest : ServerTest() {

    private fun earnings(dataJson: String): Earnings = runBlocking {
        TestHost.strictBackend()
        respondData(dataJson)
        Repo.driverEarnings().value!!
    }

    private val fullPayload = """
        {"todayAmount":86.5,"totalAmount":24680.0,"totalOrders":18,"todayOrders":4,
         "shiftCount":3,"pendingCount":2,
         "records":[{"orderNo":"SH1","goodsName":"土鸡蛋 2 箱","totalAmount":14.0,"weightKg":12.5,
                     "status":3,"statusName":"已发车","createTime":1790558040000}]}
    """.trimIndent()

    /** ① 真实键照常工作：七个顶层键 + 明细的 totalAmount/weightKg/statusName 都要落到模型上 */
    @Test
    fun `后端真实键全部生效`() {
        val e = earnings(fullPayload)
        assertEquals(86.5, e.today!!, 1e-9)
        assertEquals(24680.0, e.total!!, 1e-9)
        assertEquals(18, e.orderCount)
        assertEquals(4, e.todayOrders)
        assertEquals(3, e.shiftCount)
        assertEquals(2, e.pendingCount)
        assertEquals(1, e.items.size)
        val item = e.items.first()
        assertEquals(14.0, item.amount!!, 1e-9)
        assertEquals(12.5, item.weightKg!!, 1e-9)
        assertEquals("已发车", item.statusName)
        assertEquals("SH1", item.orderNo)
    }

    /** ② 自造别名键不再被采纳：明细读 amount/fee、列表读 items、顶层读 todayEarnings/totalEarnings/orderCount */
    @Test
    fun `别名键不再成为第四个真相源`() {
        val e = earnings(
            """{"todayEarnings":99.0,"totalEarnings":99.0,"orderCount":77,
                "items":[{"amount":5.0,"fee":6.0,"date":"09-28","orderNo":"X"}],
                "details":[{"amount":7.0,"orderNo":"Y"}]}"""
        )
        assertNull("`todayEarnings`/`totalEarnings` 无生产者，缺 todayAmount 必须是未知", e.today)
        assertNull(e.total)
        assertNull("`orderCount`/`count` 无生产者，真实键是 totalOrders", e.orderCount)
        assertTrue("`items`/`details` 无生产者，真实键是 records", e.items.isEmpty())
        assertEquals("未给", e.todayText)
        assertEquals("累计收益未给", e.totalText)
        assertEquals("累计单数未给", e.orderCountText)
    }

    /** ③ 无生产者口径（week/month/totalWeight）恒为未知：后端根本没这三个字段，显示 0 就是谎报收益 */
    @Test
    fun `week month totalWeight 三面均无生产者 只能是未给`() {
        val real = earnings(fullPayload)
        assertNull(real.week)
        assertNull(real.month)
        assertNull(real.totalWeight)
        assertEquals("未给", real.weekText)
        assertEquals("未给", real.monthText)
        assertEquals("后端无此口径", real.totalWeightText)
        // 演示面就算还写着这些键，解析层也不许接
        val faked = earnings(
            """{"todayAmount":1.0,"weekAmount":452.0,"monthAmount":1830.5,"totalWeight":246.8,"records":[]}"""
        )
        assertNull(faked.week)
        assertNull(faked.month)
        assertNull(faked.totalWeight)
    }

    /** ④ 空报文：一个数字都不许塌成 0，且所有文案都不含阿拉伯数字（"0 元"这种免费/零收益的暗示） */
    @Test
    fun `空报文全未给且文案不出现 0`() {
        val e = earnings("""{"records":[]}""")
        assertNull(e.today)
        assertNull(e.total)
        assertNull(e.orderCount)
        assertNull(e.todayOrders)
        assertTrue(e.items.isEmpty())
        listOf(e.todayText, e.weekText, e.monthText, e.totalText, e.orderCountText, e.todayOrdersText)
            .forEach { text ->
                assertFalse("「$text」把缺失渲染成了真实数字", text.any { it.isDigit() })
            }
        assertEquals("今日收益未给", e.todayBadgeText)
    }

    /** ⑤ createTime 是 EpochMillis（后端 Jackson 契约）：必须转成可读串，且日期段取 MM-dd 供按日成图 */
    @Test
    fun `createTime 按后端 EpochMillis 契约解析`() {
        val expected = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(1790558040000L))
        val item = earnings(fullPayload).items.first()
        assertEquals("13 位毫秒数不能原样进 UI", expected, item.createTime)
        assertEquals("09-28", item.date)
        // 旧 ISO 串口径照常工作（高德等外部面仍是字符串）
        val iso = earnings(
            """{"records":[{"orderNo":"Z","createTime":"2026-09-28T09:14:00"}]}"""
        ).items.first()
        assertEquals("2026-09-28 09:14", iso.createTime)
        assertEquals("09-28", iso.date)
    }

    /** ⑥ 后端只在 CargoOrderDO 命中时才 set goodsName/weightKg ⇒ 缺席是正常态，不许读成 0 kg */
    @Test
    fun `记录缺重量时显示未给而不是 0 kg`() {
        val item = earnings("""{"records":[{"orderNo":"SH9","totalAmount":12.0}]}""").items.first()
        assertNull(item.weightKg)
        assertEquals("重量未给", item.weightText)
        assertEquals("¥12", item.amountText)
        // 金额缺席同理：不许显示 "+0"
        val noAmount = earnings("""{"records":[{"orderNo":"SH8","weightKg":3.0}]}""").items.first()
        assertNull(noAmount.amount)
        assertEquals("金额未给", noAmount.amountText)
        assertEquals("3 kg", noAmount.weightText)
    }

    /** ⑦ 口径不互串：todayOrders 是"今日"，totalOrders 是"累计"，旧代码把 totalOrders 标成了今日 X 单 */
    @Test
    fun `今日与累计两个口径各读各的键`() {
        val e = earnings(fullPayload)
        assertEquals("4 单", e.todayOrdersText)
        assertEquals("18 单", e.orderCountText)
        assertTrue("今日格与累计格不许同值", e.todayOrders != e.orderCount)
    }

    /** ⑧ 演示面与后端同形：写的是 records/todayAmount/totalAmount/totalOrders + EpochMillis，旧自造键全部撤掉 */
    @Test
    fun `演示面 earnings 报文与后端契约同形`() {
        // 这一条读的是演示报文**本身的键形**（防 parser 与 DemoData 键名漂移），不是"接口挂了要不要回落"。
        // 演示闸门现在 debug 也默认关（`Api` 里那句"生产构建禁止演示数据"），所以这里显式打开这一档；
        // ServerTest 的 @After 会把 NetTestOverride 整个复位，不留到下一个类。
        TestHost.demoBackend()
        val p = DemoData.respond("/driver/earnings", emptyMap(), null, "GET")
        val o = p.obj!!
        listOf("todayAmount", "totalAmount", "totalOrders", "todayOrders", "shiftCount", "pendingCount", "records")
            .forEach { assertTrue("演示面必须写后端真实键 $it", o.has(it)) }
        listOf(
            "weekAmount", "monthAmount", "totalWeight", "todayEarnings", "totalEarnings", "orderCount",
            "items",
        ).forEach { assertFalse("演示面不许再自造 $it", o.has(it)) }
        val record = o.getJSONArray("records").getJSONObject(0)
        assertTrue(record.has("totalAmount"))
        assertFalse(record.has("amount"))
        assertFalse(record.has("date"))
        assertFalse(record.has("fee"))
        assertFalse(record.has("type"))
        assertTrue("createTime 按后端契约发毫秒数", record.getLong("createTime") > 1_000_000_000_000L)
    }
}
