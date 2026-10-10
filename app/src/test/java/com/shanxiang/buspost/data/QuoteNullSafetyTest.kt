package com.shanxiang.buspost.data

import com.shanxiang.buspost.data.model.QuoteResult
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 指令 F-2 第 3 步「QuoteResult 缺键不许塌成 0.0」的契约测试。
 *
 * 破口原文：`amount/itemFee/distanceFee/itemCount` 四个字段都是非空 `Double`/`Int`，读法一律 `multiNum`，
 * 而 `Repo.quoteSendFee` 写的是 `QuoteResult.parse(p.obj ?: JSONObject(), itemCount)` —— 信封里没有 obj
 * （接口 200 但形状不对、或演示面返回别的形状）时四个字段全被读成 0，寄货页显示"¥0 / 件数费 ¥0"，
 * 把"后端没给金额"说成"这件货免费"。
 *
 * 三方实测（2026-09-30）：
 * | 位置 | 证据 |
 * |---|---|
 * | 后端 producer | AppSendQuoteRespVO.java:13-25 只有 amount/itemFee/distanceFee/distanceKm/itemCount/pricePerItem/pricePerKm；AppSendController.java:506-513 逐字段 set；CargoPricingService.java:45-75 里只有 distanceKm 会为 null（:55 站点缺坐标），distanceFee 缺坐标时**真给 0**（:56） |
 * | 小程序 consumer | send.wxml:282-287 只读 quote.amount / quote.itemFee / quote.distanceFee / quote.itemCount / quote.distanceKm，且 :287 对 distanceKm 已写 `!= null` 判断 |
 * | 演示面 writer | DemoData.kt:472-476 也只写这五个键 |
 * ⇒ 旧别名 `fee`/`totalPrice`/`unitFee`/`mileageFee`/`count` 三面都没有生产者（`count` 在 java 里的 9 处命中全在
 * DashboardController / BigScreenServiceImpl / JobController，与 /send/quote 报文无关），按 §二 同一条规则删除。
 *
 * 缺失态用可空类型表达，不引入 ContractParseException：本文件的 ETA / 里程家族已经全是这个口径，
 * 同一个解析层留两套缺失语义只会让下一个字段选错边。
 */
class QuoteNullSafetyTest {

    private fun obj(vararg pairs: Pair<String, Any?>): JSONObject {
        val o = JSONObject()
        pairs.forEach { (k, v) -> o.put(k, v ?: JSONObject.NULL) }
        return o
    }

    /** ① 真实形状（后端五个键都给）照常工作，数字与文案都不许变 */
    @Test
    fun `五个真实键齐全时金额件数费里程费件数都正常`() {
        val q = QuoteResult.parse(
            obj("amount" to 11.0, "itemFee" to 5.0, "distanceFee" to 6.0, "itemCount" to 2, "distanceKm" to 24.6),
        )
        assertEquals(11.0, q.amount!!, 1e-9)
        assertEquals(5.0, q.itemFee!!, 1e-9)
        assertEquals(6.0, q.distanceFee!!, 1e-9)
        assertEquals(2, q.itemCount)
        assertEquals(24.6, q.distanceKm!!, 1e-9)
        assertEquals("11", q.amountValueText)
        assertEquals("¥11", q.amountText)
        assertTrue(q.feeBreakdownText.contains("件数费 ¥5"))
        assertTrue(q.feeBreakdownText.contains("+ 里程费 ¥6"))
        assertTrue(q.feeBreakdownText.contains("2 件"))
    }

    /** ② 缺 amount 键：null + 文案说"未给"，绝不显示 ¥0（缺字段冒充真零值 = 把免费说给用户看） */
    @Test
    fun `缺 amount 键不许显示成免费`() {
        val q = QuoteResult.parse(obj("itemFee" to 5.0, "distanceFee" to 6.0, "itemCount" to 2))
        assertNull(q.amount)
        assertEquals("未给", q.amountValueText)
        assertEquals("金额未给", q.amountText)
        assertFalse("金额文案里不许出现 0 元", q.amountText.contains("0"))
    }

    /** ③ 后端显式发 null 与漏发键是同一件事：都不许塌成 0.0 */
    @Test
    fun `显式 null 的 amount 同样保持未给`() {
        val q = QuoteResult.parse(obj("amount" to null, "itemFee" to null, "distanceFee" to null, "itemCount" to null))
        assertNull(q.amount)
        assertNull(q.itemFee)
        assertNull(q.distanceFee)
        assertNull(q.itemCount)
    }

    /** ④ 五个自造别名键都不再被采纳 —— 只写真实键名的封闭性判据 */
    @Test
    fun `fee totalPrice unitFee mileageFee count 不再被当作契约键`() {
        val q = QuoteResult.parse(
            obj("fee" to 9.9, "totalPrice" to 8.8, "unitFee" to 7.7, "mileageFee" to 6.6, "count" to 3),
        )
        assertNull("`fee` 无生产者，不许进 amount", q.amount)
        assertNull(q.itemFee)
        assertNull(q.distanceFee)
        assertNull("`count` 是 dashboard/bigscreen 的键，不是 itemCount", q.itemCount)
        assertNull(q.distanceKm)
        assertEquals("金额未给", q.amountText)
    }

    /** ⑤ A 类真零值与 B 类缺键必须分得开：后端无坐标时 distanceFee 真给 0（CargoPricingService:56），不叠加；缺键说明未给 */
    @Test
    fun `里程费为 0 是不叠加，里程费缺失是未给`() {
        val zero = QuoteResult.parse(obj("amount" to 5.0, "itemFee" to 5.0, "distanceFee" to 0.0, "itemCount" to 1))
        assertEquals(0.0, zero.distanceFee!!, 1e-9)
        assertFalse("后端真给 0 时按小程序口径不显示里程费那一截", zero.feeBreakdownText.contains("里程费"))

        val missing = QuoteResult.parse(obj("amount" to 5.0, "itemFee" to 5.0, "itemCount" to 1))
        assertNull(missing.distanceFee)
        assertTrue("缺键必须明说未给，不能和『0』共用同一句文案", missing.feeBreakdownText.contains("里程费未给"))
    }

    /** ⑥ 信封里没有 obj（Repo 的 `p.obj ?: JSONObject()` 这条路）⇒ 整句都是未给，一个数字都不许出现 */
    @Test
    fun `空报文解析成全未给而不是 0 元`() {
        val q = QuoteResult.parse(JSONObject())
        assertNull(q.amount)
        assertEquals("未给", q.amountValueText)
        val line = q.feeBreakdownText
        assertEquals("件数费未给 · 里程费未给（件数未给 · 计价里程未给）", line)
        assertFalse("全未给的文案里不该有任何金额数字", line.contains("¥"))
        assertFalse("全未给的文案里不该出现 0", line.contains("0"))
    }

    /** ⑦ itemCount=0 是真零值，原样透传成"0 件"，不许被当成"没给"（与 TransportLeg 0 km 同一条规则） */
    @Test
    fun `件数为 0 时如实显示 0 件`() {
        val q = QuoteResult.parse(obj("amount" to 6.0, "itemFee" to 0.0, "distanceFee" to 6.0, "itemCount" to 0))
        assertEquals(0, q.itemCount)
        assertTrue(q.feeBreakdownText.contains("0 件"))
        assertTrue("件数费真给 0 要显示 ¥0，与『未给』区分", q.feeBreakdownText.contains("件数费 ¥0"))
    }
}
