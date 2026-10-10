package com.shanxiang.buspost.data

import com.shanxiang.buspost.TestHost
import com.shanxiang.buspost.core.net.NetTestOverride
import com.shanxiang.buspost.data.model.BusLine
import com.shanxiang.buspost.data.model.DriverProfile
import com.shanxiang.buspost.data.model.HandoverTask
import com.shanxiang.buspost.data.model.PickupTask
import com.shanxiang.buspost.data.model.Product
import com.shanxiang.buspost.data.model.TransitPlan
import com.shanxiang.buspost.data.repo.DemoData
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F-3 ②「三面全无生产者的键」契约测试（本轮收口的六个族）。
 *
 * fixture 的键名逐个抄后端 VO，不是抄 Android 的候选名列表：
 * - 商品 `AppProductRespVO.java:12-34` 十二键 = id/name/fromVillage/price/unit/image/imageUrl/badge/
 *   description/stock/status/sort —— 没有划线原价，也没有销量口径；
 * - 司机档案 `AppDriverProfileRespVO.java:11-23` 七键 = driverId/name/mobile/vehicleId/plateNo/
 *   cargoCapacity/cargoCapacityKg —— 没有"已装重量"，也没有认证/审核位；
 * - 揽件 `AppDriverPickupRespVO.java:13-33` 十一键、交接 `AppDriverHandoverRespVO.java:14-62` 十七键
 *   都只有 `photoUrl`（已入库凭证），没有任何"勾选态/必须拍照"的要求位；
 * - 候选方案 `OrderTopologyRespVO.Candidate:60-69` = mode/modeName/legCount/transferCount/
 *   distanceKm/durationMinutes/score/reason/chosen；
 * - 班次 `AppDriverShiftRespVO.java:30 loadedCount`（件数，与档案的 cargoCapacity 配成一套口径）。
 *
 * 每一族都要证明三件事：真键读得到、自造别名不再被采纳、**缺键不塌成一个看起来像结论的默认值**。
 * 最后一类断言是本轮的重点：`已售 0` / `未认证` / `载重 0 kg` / `推荐走 1 车道` / `库存 0 ⇒ 已售罄`
 * 都不是"后端说了零"，而是"客户端替后端编了一个零"。
 */
class NoProducerKeyFamilyContractTest {

    /** 这个类不继承 ServerTest，闸门复位要自己钉住：演示形态只在这两条用例里开，用完立刻还给进程。 */
    @After
    fun closeDemoGate() {
        NetTestOverride.reset()
    }

    private fun obj(vararg pairs: Pair<String, Any?>): JSONObject {
        val o = JSONObject()
        pairs.forEach { (k, v) -> o.put(k, v ?: JSONObject.NULL) }
        return o
    }

    /** ① 商品族：划线价与销量没有生产者，库存缺键不许变成"已售罄" */
    @Test
    fun `商品划线价与销量不再被采纳 缺库存不下售罄结论`() {
        val legacy = Product.parse(
            obj(
                "id" to 2001L, "name" to "土鸡蛋", "price" to 28.0, "unit" to "份",
                "originPrice" to 35.0, "sales" to 167, "stock" to 12,
            ),
        )
        assertNull("originPrice 三面 0 生产者，不许再当契约键", legacy.originPrice)
        assertNull("sales 三面 0 生产者，不许渲染成已售 167", legacy.sales)
        assertEquals(12, legacy.stock)
        assertEquals("库存 12", legacy.stockText)
        assertFalse(legacy.soldOut)

        // 后端 Integer stock 可缺席：缺键=null，而 null 既不是 0 也不是售罄
        val missing = Product.parse(obj("id" to 2001L, "name" to "土鸡蛋", "price" to 28.0))
        assertNull(missing.stock)
        assertFalse("库存没给不许自动判成售罄（旧写法塌 0 直接把下单按钮锁死）", missing.soldOut)
        assertEquals("库存未给", missing.stockText)
        assertFalse("未给态文案里不许出现阿拉伯数字", missing.stockText.matches(Regex("\\d")))

        // 真给了 0 才是售罄，原样透传
        val zero = Product.parse(obj("id" to 2001L, "price" to 28.0, "stock" to 0))
        assertEquals(0, zero.stock)
        assertTrue(zero.soldOut)
        assertEquals("库存 0", zero.stockText)
    }

    /** ② 司机档案族：载重只认 cargoCapacityKg，已装重量与认证状态恒 null，车牌只认 plateNo */
    @Test
    fun `司机档案只认后端七键里的真实键`() {
        val real = DriverProfile.parse(
            obj(
                "driverId" to 61L, "name" to "王有德", "mobile" to "13700001234",
                "plateNo" to "渝A·8K231", "cargoCapacity" to 24, "cargoCapacityKg" to 200,
            ),
        )
        assertEquals(200.0, real.capacityKg!!, 1e-9)
        assertEquals(24, real.cargoCapacity)
        assertEquals("渝A·8K231", real.plate)

        // 演示面旧形状：capacityKg / usedKg / verified / plate 四个键都只有 DemoData 在发
        val fake = DriverProfile.parse(
            obj(
                "driverId" to 61L, "plate" to "渝A·8K231",
                "capacityKg" to 200, "usedKg" to 78.5, "verified" to true,
            ),
        )
        assertNull("capacityKg 不是后端键，真实键叫 cargoCapacityKg", fake.capacityKg)
        assertNull("后端没有已装重量口径", fake.usedKg)
        assertNull("后端没有认证/审核键，不许替后端判已认证", fake.verified)
        // plate 是非空 String，读不到真键就是空串：旧写法在这里把自造名 plate 当真键，
        // 于是演示包看着正常、真机整列空白（AppDriverProfileRespVO.java:19 只有 plateNo）
        assertEquals("plate 是自造名，车牌的真实键是 plateNo", "", fake.plate)

        // cargoCapacity 是 Integer，可缺席 ⇒ 件数上限没给就不能算出百分比
        val noCap = DriverProfile.parse(obj("driverId" to 61L, "cargoCapacityKg" to 200))
        assertNull(noCap.cargoCapacity)
        assertNull(noCap.freePercent(4))
        assertNull(real.freePercent(null))
        assertEquals(83.3333, real.freePercent(4)!!, 1e-3)
        assertEquals(100.0, real.freePercent(0)!!, 1e-9)
        // 后端真给"装超了"就透传 0，不裁成负数也不当成没给
        assertEquals(0.0, real.freePercent(30)!!, 1e-9)
    }

    /** ③④ 揽件勾选与交接拍照要求：后端两个 VO 共 28 个键里没有任何要求位 ⇒ 恒 null */
    @Test
    fun `揽件勾选与交接拍照要求不被采纳`() {
        val pickup = PickupTask.parse(
            obj("orderId" to 5001L, "orderNo" to "FH001", "checked" to true, "done" to true),
        )
        assertNull("AppDriverPickupRespVO 十一键里没有勾选态", pickup.checked)

        val handover = HandoverTask.parse(
            obj("id" to 91L, "orderNo" to "FH001", "photoUrl" to "https://x/1.jpg",
                "needPhoto" to true, "photoRequired" to true, "forcePhoto" to true),
        )
        assertNull("AppDriverHandoverRespVO 只有 photoUrl，没有任何拍照要求位", handover.needPhoto)
        // 已入库凭证仍然是真实口径，撤掉要求位不能把回读一起撤掉
        assertEquals("https://x/1.jpg", handover.photoUrl)
    }

    /** ⑤ 候选方案族：名称/推荐/段数各认 Candidate 的一个真键 */
    @Test
    fun `候选方案只认 modeName chosen legCount durationMinutes`() {
        val real = TransitPlan.parse(
            obj("modeName" to "班车联运", "chosen" to true, "legCount" to 2, "durationMinutes" to 86,
                "reason" to "换乘窗口 20 分钟"),
        )
        assertEquals("班车联运", real.name)
        assertEquals(true, real.recommended)
        assertEquals(2, real.legs)
        assertEquals(86, real.minutes)
        assertEquals("换乘窗口 20 分钟", real.reason)

        val fake = TransitPlan.parse(
            obj("name" to "方案 A", "planName" to "方案 A", "title" to "方案 A",
                "recommended" to true, "best" to true, "selected" to true,
                "legs" to 3, "segments" to 3, "explain" to "x", "description" to "y"),
        )
        assertEquals("九个键名里只有 modeName 是真键", "", fake.name)
        assertNull("chosen 才是推荐标记，recommended/best/selected 都是自造名", fake.recommended)
        assertNull("legCount 才是段数", fake.legs)
        assertEquals(0, fake.minutes)
        assertEquals("", fake.reason)

        // chosen 是 Boolean 包装类，显式 null 与缺键同结论：不许读成"不推荐"
        assertNull(TransitPlan.parse(obj("modeName" to "直达", "chosen" to null)).recommended)
    }

    /** ⑥ 演示面同形：撤掉的键不许留在报文里给别名续命 */
    @Test
    fun `演示报文里不再有那批无生产者键`() {
        // 这两条量的是演示报文键形本身（防 parser 与 DemoData 漂移），不是"接口挂了要不要回落"，
        // 所以显式开闸；演示闸门现在 debug 也默认关，闸门本身不松。
        TestHost.demoBackend()
        val profile = DemoData.respond("/driver/profile", emptyMap(), null, "GET").obj!!
        listOf("capacityKg", "usedKg", "verified", "plate").forEach {
            assertFalse("司机档案报文里不许再有 $it（会给解析层的自造别名续命）", profile.has(it))
        }
        assertTrue(profile.has("plateNo"))
        assertTrue(profile.has("cargoCapacityKg"))

        val product = DemoData.respond("/product/get", mapOf("id" to 2001), null, "GET").obj!!
        assertFalse("商品报文不许再发 originPrice", product.has("originPrice"))
        assertFalse("商品报文不许再发 sales", product.has("sales"))
        assertTrue(product.has("stock"))

        everyItem("/driver/pickups") { it.has("checked") || it.has("done") }
        everyItem("/driver/handovers") { it.has("needPhoto") || it.has("photoRequired") || it.has("forcePhoto") }

        val plans = DemoData.respond("/send/topology", emptyMap(), null, "GET").obj!!.optJSONArray("plans")
        assertNotNullPlans(plans)
        for (i in 0 until plans!!.length()) {
            val c = plans.getJSONObject(i)
            assertFalse("候选方案不许再发 name", c.has("name"))
            assertFalse("候选方案不许再发 recommended", c.has("recommended"))
            assertTrue(c.has("modeName"))
            assertTrue(c.has("chosen"))
        }
    }

    /** ⑦ 解析层与演示面自洽：改完键名两侧都要还能对上，否则演示包就是另一份假数据 */
    @Test
    fun `演示报文仍能被解析层读出非零的真实口径`() {
        TestHost.demoBackend()
        val p = DriverProfile.parse(DemoData.respond("/driver/profile", emptyMap(), null, "GET").obj!!)
        assertEquals("渝A·8K231", p.plate)
        assertEquals(200.0, p.capacityKg!!, 1e-9)
        assertEquals(24, p.cargoCapacity)
        assertNull(p.usedKg)
        assertNull(p.verified)

        val shift = DemoData.respond("/driver/shifts", emptyMap(), null, "GET").arr!!
        val loaded = (0 until shift.length()).map { shift.getJSONObject(it).getInt("loadedCount") }
        // 早班已装 4 件、其余两班未装车：件数口径的空余百分比都能算出来（两个数都有真实生产者）
        assertTrue(loaded.all { p.freePercent(it) != null })

        val plans = DemoData.respond("/send/topology", emptyMap(), null, "GET").obj!!.optJSONArray("plans")
        val read = (0 until plans!!.length()).map { TransitPlan.parse(plans.getJSONObject(it)) }
        assertEquals(listOf("方案 A · 双河口换乘", "方案 B · 直达共配仓"), read.map { it.name })
        assertEquals(listOf(86, 142), read.map { it.minutes })
        assertEquals(listOf(2, 1), read.map { it.legs })
        assertEquals(listOf(true, false), read.map { it.recommended })
    }

    private fun assertNotNullPlans(arr: JSONArray?) {
        assertTrue("演示拓扑报文里没有 plans", arr != null && arr.length() > 0)
    }

    /** 数组型报文逐条断言：任何一条还带着被撤的键就红 */
    private fun everyItem(path: String, bad: (JSONObject) -> Boolean) {
        val arr = DemoData.respond(path, emptyMap(), null, "GET").arr
        assertTrue("$path 的演示报文不是数组", arr != null && arr.length() > 0)
        for (i in 0 until arr!!.length()) {
            assertFalse("$path 第 $i 条仍在发无生产者键（会给解析层的别名续命）", bad(arr.getJSONObject(i)))
        }
    }

    /** BusLine 的旧米制读法留作兼容，但缺键必须是 null（与 ⑬ 的口径一致，防这轮改可空时改宽） */
    @Test
    fun `线路旧米制键保留但不许缺键塌 0`() {
        assertEquals(12400.0, BusLine.parse(obj("routeId" to 7, "distance" to 12400)).distanceMeters!!, 1e-9)
        assertNull(BusLine.parse(obj("routeId" to 7)).distanceMeters)
    }
}
