package com.shanxiang.buspost.data

import com.shanxiang.buspost.ServerTest
import com.shanxiang.buspost.TestHost
import com.shanxiang.buspost.data.repo.Repo
import com.shanxiang.buspost.data.store.Prefs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 2026-10-17 真机抓到的一格（司机「我的路线」顶部）：
 * 界面念成 `当前： → · 状态暂不可用` —— 两个站名是空的，看着像在加载。
 *
 * 破口原文（改前）：`Repo.currentLeg()` 写的是 `TransportLeg.parse(p.obj ?: JSONObject())`，
 * 而后端在没有当前段时（那两条段都被判异常）回的是 **`data:null`** ⇒ 空报文被 parse 成一个
 * **legId 为空、站名为空、status=-1 的幽灵段**，交给页面就渲染出那行空位。
 * 同屏的段卡却有名字：它读的是 `/driver/legs` 那一条出口，两处不同源 ⇒ 顶部这一格从来没被单独验过。
 *
 * 口径：**"没有当前段" 与 "有一段但字段没给" 与 "这一格没取到" 是三件事**，
 * 前者该是 null（页面整行不出现），后者该带 error（页面说"没读到"），中间那种才由
 * `RoutesScreen.legPairText` 明说"站点名未给"。
 * 判"有没有段"用 `legId`：它是这一格唯一的身份字段。
 */
class CurrentLegContractTest : ServerTest() {

    /**
     * `Repo.driverIdParam()` 在未绑定时**直接抛** `DriverUnboundException`、请求根本不发
     * （Repo.kt:1111-1118）。所以这一格必须先绑司机 —— 否则下面三条"应当为 null"
     * 会因为"根本没发请求"而成立，那是**空集上的真**（用例在判脚手架，不在判解析点）。
     */
    private fun currentLeg() = runBlocking {
        TestHost.strictBackend()
        Prefs.driverId = 61L
        Repo.currentLeg()
    }

    /** ⓪ 先钉住"未绑定"这一格：它必须是 error，而不是"没有当前段" */
    @Test
    fun `未绑定司机时是 error 不是空段`() {
        Prefs.driverId = 0L
        respondData("""{"legId":881,"fromStationName":"海棠溪","toStationName":"上海城"}""")
        val one = runBlocking { Repo.currentLeg() }
        assertNull(one.value)
        assertNotNull("未绑定应当报「这一格没取到」，而不是替后端说「确实没有段」", one.error)
    }

    /** ① 后端回 `data:null` ⇒ 没有当前段，不许冒出一个空段 */
    @Test
    fun `data 为 null 时不造出幽灵段`() {
        respondData("null")
        assertNull("data:null 被 parse 成了空段：顶部会念成「当前： → 」", currentLeg().value)
    }

    /** ② 空对象同形：`{}` 也没有身份 */
    @Test
    fun `data 是空对象时也不造出幽灵段`() {
        respondData("{}")
        assertNull(currentLeg().value)
    }

    /** ③ 正对照：真有段时照常返回 —— 否则①②只是把这一格永远判成 null */
    @Test
    fun `带 legId 的段照常返回`() {
        respondData(
            """{"legId":881,"legSequence":2,"fromStationName":"海棠溪","toStationName":"上海城",
               "plateNo":"渝A·B5201","status":2,"statusName":"待执行"}""".trimIndent(),
        )
        val one = currentLeg()
        val leg = one.value
        assertNotNull("有身份的段被吞掉了：error=${one.error} demo=${one.demo}", leg)
        assertEquals("881", leg!!.legId)
        assertEquals("海棠溪 → 上海城", "${leg.fromStation} → ${leg.toStation}")
        assertEquals(2, leg.seq)
    }

    /** ④ 业务失败不是"确实没有"：必须带 error，页面才不能说"今天没有当前段" */
    @Test
    fun `业务失败落进 error 而不是空段`() {
        respondBizError(500, "系统异常")
        val one = currentLeg()
        assertNull(one.value)
        assertNotNull("失败被读成了「没有当前段」", one.error)
    }
}
