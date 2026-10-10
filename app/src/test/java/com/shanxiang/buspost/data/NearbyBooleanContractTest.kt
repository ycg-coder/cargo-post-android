package com.shanxiang.buspost.data

import com.shanxiang.buspost.ServerTest
import com.shanxiang.buspost.TestHost
import com.shanxiang.buspost.data.model.Geo
import com.shanxiang.buspost.data.model.multiBoolOrNull
import com.shanxiang.buspost.data.repo.DemoData
import com.shanxiang.buspost.data.repo.Repo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 指令 F-2 第 4 步「NearbyResult 严禁缺失 boolean 默认 true」的契约测试。
 *
 * 破口原文：`Repo.nearbyBuses` 里两处 `o.optBoolean("inService", true)` / `o.optBoolean("located", true)` ——
 * 键不存在时直接当"是"，而首页与线路页拿它判 `off`（非运营时段）空态：缺失不但会被说成"正在运营"，
 * 还会连带影响 empty/data 那一支的选择。
 *
 * 三方实测（2026-09-30）：
 * | 位置 | 证据 |
 * |---|---|
 * | 后端 producer | AppBusNearbyRespVO.java:65 `Boolean inService`、:45 `Boolean located`；AppBusServiceImpl.java:256 按经纬度合法性 setLocated、:481/:532 setInService |
 * | 小程序 consumer | **按三态读**：pages/bus/index.js:240 `data.inService != null ? data.inService : null`，:288/:291 分别判 `=== false` / `=== true`；pages/index/index.js:539/:554 `inService === false ? 'off' : 'empty'` |
 * | 演示面 writer | DemoData.kt 的 /bus/nearby 报文（:611-620）**一个都不写**这两个键 ⇒ 旧默认值在演示路径上恒为"正在运营 / 定位成功" |
 * ⇒ 缺失必须是 UNKNOWN(null)；小程序那句 `inService === false ? 'off' : 'empty'` 就是本轮对齐的目标口径。
 *
 * 测试走 `Repo.nearbyBuses`（MockWebServer 真信封），而不是只测模型：破口在解析点上，
 * 只测 NearbyResult 的构造默认值会放过"解析层又把缺键读成 true"这一整类复发。
 */
class NearbyBooleanContractTest : ServerTest() {

    private fun nearby(dataJson: String) = runBlocking {
        TestHost.strictBackend()
        respondData(dataJson)
        Repo.nearbyBuses(Geo(30.9, 110.3)).value!!
    }

    /** ① 后端真给 true：照常是"运营中"，不进 off 支 */
    @Test
    fun `inService 为 true 时正常显示`() {
        val r = nearby("""{"nearbyStations":[],"lines":[],"vehicles":[],"inService":true,"located":true}""")
        assertEquals(true, r.inService)
        assertEquals(true, r.located)
        assertFalse(r.isOffService)
    }

    /** ② 后端真给 false：off 支必须还能走到（改成三态不许把"否"也吞掉） */
    @Test
    fun `inService 为 false 时才允许判非运营`() {
        val r = nearby("""{"nearbyStations":[],"lines":[],"vehicles":[],"inService":false}""")
        assertEquals(false, r.inService)
        assertTrue("只有真给 false 才允许说非运营", r.isOffService)
    }

    /** ③ 缺键 = UNKNOWN：既不说"运营中"，也不说"非运营" */
    @Test
    fun `缺 inService 键是未知而不是 是`() {
        val r = nearby("""{"nearbyStations":[],"lines":[],"vehicles":[]}""")
        assertNull(r.inService)
        assertFalse("缺失不许进 off 支（旧代码的默认 true 走不到这里，但缺失说成『是』同样是谎言）", r.isOffService)
        assertNull(r.located)
    }

    /** ④ 后端显式发 null（Boolean 字段没赋值时就是 null）与漏发键同解 */
    @Test
    fun `显式 null 的 inService 与 located 保持未知`() {
        val r = nearby("""{"nearbyStations":[],"lines":[],"vehicles":[],"inService":null,"located":null}""")
        assertNull(r.inService)
        assertNull(r.located)
    }

    /** ⑤ 演示面报文（两个键都不写）经同一解析器必须得到 UNKNOWN —— 旧默认值在这里恒为 true */
    @Test
    fun `演示面 nearby 不含这两个键 解析结果为未知`() {
        // 演示闸门现在 debug 也默认关；这一条量的是演示报文的键形与解析层的自洽，显式打开，@After 复位。
        TestHost.demoBackend()
        val p = DemoData.respond("/bus/nearby", mapOf("latitude" to 30.9, "longitude" to 110.3), null, "GET")
        val o = p.obj!!
        assertFalse("演示面若开始写这两个键，本条的『无生产者』前提就变了，需要重新核三方", o.has("inService"))
        assertFalse(o.has("located"))
        assertNull(o.multiBoolOrNull("inService"))
        assertNull(o.multiBoolOrNull("located"))
    }

    /** ⑥ 同族的别名键：真实键只有 realTransitAvailable，裸 realTransit 三面都无生产者，不再被采纳 */
    @Test
    fun `真实公交标记只认 realTransitAvailable`() {
        val given = nearby("""{"nearbyStations":[],"lines":[],"vehicles":[],"realTransitAvailable":true}""")
        assertTrue(given.realTransit)
        val alias = nearby("""{"nearbyStations":[],"lines":[],"vehicles":[],"realTransit":true}""")
        assertFalse("`realTransit` 是客户端自造别名，不许成为第四个真相源", alias.realTransit)
    }
}
