package com.shanxiang.buspost.ui.driver

import com.shanxiang.buspost.ServerTest
import com.shanxiang.buspost.TestHost
import com.shanxiang.buspost.awaitUntil
import com.shanxiang.buspost.data.model.LegStatus
import com.shanxiang.buspost.data.store.Prefs
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * §54 司机异常上报的**报文与回执**契约（真网络，MockWebServer 计数）。
 *
 * 三条各钉一段真实风险：
 * · 报文里只允许出现后端 `AppDriverLegActionReqVO` 认识的键 —— 自造 `type`/`exceptionType` 在这里当场红；
 * · "没选分类 / 段已终态"必须**一个字节都不出网**（判据是 `requestCount`，不是"我看代码觉得没发"）；
 * · 回执只写后端回读到的事实：回读没变异常就不许说"上报成功"（宁可没有上报，不允许假上报）。
 *
 * ⚠ `Repo` 是进程单例、幂等闸的键跨用例活着，所以每个用例的 legId 都不同（9711/9712/9713/9714）。
 */
class DriverExceptionContractTest : ServerTest() {

    @Before
    fun bindDriver() {
        Prefs.driverId = 7701L
        TestHost.installMainDispatcher()
    }

    private fun legJson(legId: String, status: Int, seq: Int = 2) =
        """{"legId":"$legId","legSequence":$seq,"fromStationName":"山香村服务站","toStationName":"白沙镇站点","status":$status,"handoverRequired":false}"""

    private fun feed(legId: String, status: Int) =
        """{"code":0,"msg":"","data":{"list":[${legJson(legId, status)}],"total":1}}"""

    private fun one(legId: String, status: Int) =
        """{"code":0,"msg":"","data":${legJson(legId, status)}}"""

    private fun enqueueLegs(legId: String, status: Int) {
        respond(feed(legId, status))
        respond(one(legId, status))
    }

    private fun exceptionRequests(): List<RecordedRequest> =
        (0 until server.requestCount).map { server.takeRequest() }
            .filter { it.path!!.contains("/driver/leg/") }

    private fun keysOf(body: JSONObject): Set<String> = body.keys().asSequence().toSet()

    @Test
    fun `报文只有后端认识的三个键`() {
        TestHost.strictBackend()
        enqueueLegs("9711", LegStatus.IN_TRANSIT.code)
        val vm = DriverExceptionViewModel()
        vm.load()
        awaitUntil { vm.legs.isNotEmpty() }

        // ROAD_BLOCKED 是 §54 的第二项
        vm.selectReason(1)
        vm.onDetailChange("塌方，中断约 3 小时")
        respondData("true")
        respond(feed("9711", LegStatus.EXCEPTION.code))
        vm.submit()
        awaitUntil { vm.receipt != null }

        val request = exceptionRequests().single()
        assertEquals("POST", request.method)
        assertEquals("/app-api/transport/driver/leg/exception", request.path)
        val body = JSONObject(bodyOf(request))
        assertEquals(setOf("driverId", "legId", "remark"), keysOf(body))
        assertEquals(7701L, body.getLong("driverId"))
        assertEquals("9711", body.getString("legId"))
        assertEquals("道路阻断：塌方，中断约 3 小时", body.getString("remark"))
        assertTrue("后端回读已是异常，回执要写出这一点", vm.receipt!!.contains("异常"))
    }

    /** 没选分类：后端对空 remark 兜的是"司机上报异常"，那等于让后端替司机编原因 —— 不许发 */
    @Test
    fun `没选分类时一个字节都不出网`() {
        TestHost.strictBackend()
        enqueueLegs("9712", LegStatus.IN_TRANSIT.code)
        val vm = DriverExceptionViewModel()
        vm.load()
        awaitUntil { vm.legs.isNotEmpty() }

        val before = server.requestCount
        vm.submit()
        assertEquals("未选分类不该产生任何请求", before, server.requestCount)
        assertTrue(vm.receipt!!.contains("未发给后端"))
        assertTrue(vm.receipt!!.contains("先选一项"))
    }

    /** 「其他」是七项里唯一不自带语义的，空说明同样不许出网 */
    @Test
    fun `选其他却不写情况时不出网`() {
        TestHost.strictBackend()
        enqueueLegs("9713", LegStatus.IN_TRANSIT.code)
        val vm = DriverExceptionViewModel()
        vm.load()
        awaitUntil { vm.legs.isNotEmpty() }

        vm.selectReason(DriverExceptionReason.values().lastIndex)
        val before = server.requestCount
        vm.submit()
        assertEquals(before, server.requestCount)
        assertTrue(vm.receipt!!.contains("写清具体情况"))
    }

    /** 已完成(11)/已异常(99) 的段没有上报出口：状态归后端，本机不再往终态段上补报 */
    @Test
    fun `终态段不接受上报`() {
        TestHost.strictBackend()
        for ((legId, status) in listOf("9714" to LegStatus.COMPLETED.code, "9715" to LegStatus.EXCEPTION.code)) {
            enqueueLegs(legId, status)
            val vm = DriverExceptionViewModel()
            vm.load()
            awaitUntil { vm.legs.isNotEmpty() }
            vm.selectReason(0)
            val before = server.requestCount
            vm.submit()
            assertEquals("$legId(status=$status) 不应产生请求", before, server.requestCount)
            assertTrue(vm.receipt!!.contains("未发给后端"))
        }
    }

    /** 幂等闸（V-1/§52）覆盖这条新写路径：连点两次只有一次真的上网络 */
    @Test
    fun `连点两次只有一次真的出网`() {
        TestHost.strictBackend()
        enqueueLegs("9716", LegStatus.IN_TRANSIT.code)
        val vm = DriverExceptionViewModel()
        vm.load()
        awaitUntil { vm.legs.isNotEmpty() }
        vm.selectReason(0)

        respondData("true")
        respond(feed("9716", LegStatus.EXCEPTION.code))
        vm.submit()
        awaitUntil { vm.receipt != null && !vm.busy }
        val first = vm.receipt
        // 第二次落在 V-1 的 10 秒保留窗里：闸门返回 notSent，一个字节都不出网
        vm.submit()
        awaitUntil { vm.receipt != first }

        assertEquals("第二次点击必须被本地闸门拦下", 1, exceptionRequests().size)
        assertTrue(vm.receipt!!.contains("未发给后端"))
    }

    /** 后端没把段改成异常 → 回执不许出现"已上报/成功"，只能明说可能未被接受 */
    @Test
    fun `回读状态没变时不许谎报成功`() {
        TestHost.strictBackend()
        enqueueLegs("9717", LegStatus.IN_TRANSIT.code)
        val vm = DriverExceptionViewModel()
        vm.load()
        awaitUntil { vm.legs.isNotEmpty() }
        vm.selectReason(3)

        respondData("true")
        respond(feed("9717", LegStatus.IN_TRANSIT.code))
        vm.submit()
        awaitUntil { vm.receipt != null }

        assertTrue(vm.receipt!!.contains("可能未被接受"))
        assertTrue(vm.receipt!!.contains("请下拉刷新核对"))
        assertEquals(1, exceptionRequests().size)
    }
}
