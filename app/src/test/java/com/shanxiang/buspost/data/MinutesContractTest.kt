package com.shanxiang.buspost.data

import com.shanxiang.buspost.TestHost
import com.shanxiang.buspost.core.net.NetTestOverride
import com.shanxiang.buspost.data.model.BusVehicle
import com.shanxiang.buspost.data.model.Reachability
import com.shanxiang.buspost.data.model.ShiftTask
import com.shanxiang.buspost.data.model.Station
import com.shanxiang.buspost.data.model.TransitPlan
import com.shanxiang.buspost.data.repo.DemoData
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 指令 v2.1 二十四「分钟字段五组 alias 做最终 contract audit」的契约测试（F-2 第 2 步）。
 *
 * fixture 的键名逐个抄自后端 VO，不抄 Android 的候选名列表。三方实测（2026-09-30，命令与命中数见
 * `docs/android/CONTRACT_EVIDENCE.md` 的"分钟五组别名"一节）：
 *
 * | 字段 | 后端生产者 | 小程序消费者 | 被删掉的候选名 | 删的理由 |
 * |---|---|---|---|---|
 * | `Station.plannedMinutes` | AppDriverStationRespVO:17、RouteStationRespVO:32、AppBusLineRespVO:54 | bus/detail.js（4 处）、goods/trace/trace.js（2 处） | `minutes`、`offsetMinutes` | 后端**没有任何名为 minutes/offsetMinutes 的字段**（Java 里只有两处同名局部变量）；`offsetMinutes` 后端 0 文件、小程序 0 文件 |
 * | `BusVehicle.waitDepartureMinutes` | AppBusRespVO:47、AppBusNearbyRespVO:126 | bus/index.js:449、index/index.js:575 | `waitMinutes` | 那是**小程序自己的局部变量名**（`const waitMinutes = b.waitDepartureMinutes`），后端 0 文件 |
 * | `TransitPlan.minutes` | OrderTopologyRespVO.Candidate:65 `durationMinutes`（列表键 `candidates`:49） | 小程序不消费 candidates | `totalMinutes`、`minutes` | 两个都 0 生产者；`totalMinutes` 此前**只有 DemoData 在发**（演示数据自造键） |
 * | `ShiftTask.plannedDurationMinutes` | AppDriverShiftRespVO:24、ShiftBaseVO:18、MonitoringShiftRespVO:26 | driver/routes.js（3 处）、workbench.js（2 处） | `durationMinutes` | 后端有这个名字，但属于**另一个对象**的另一种口径（段时长 AppDriverLegRespVO:62 / Leg:92），不是班次总时长 |
 * | `Reachability.walkMinutes` | AppSendReachabilityRespVO:32（service:142 赋值） | 小程序不消费；Android 侧 SendScreen 三处渲染 `walkText` | `minutes` | 0 生产者 |
 *
 * 每组的判据都成对写：**真实键照常工作** + **别名不再被采纳**。只写后者会漏掉"整条读法被删光"这种假修复；
 * 只写前者则等于没测（旧代码本来就过）。
 */
class MinutesContractTest {

    /** 这个类不继承 ServerTest，所以闸门复位得自己钉住：用例显式开过演示闸门后必须还给进程（Gradle 里所有类共用一个 JVM，泄漏出去就是后面的类静默回落演示数据）。 */
    @After
    fun closeDemoGate() {
        NetTestOverride.reset()
    }

    private fun obj(vararg pairs: Pair<String, Any?>): JSONObject {
        val o = JSONObject()
        pairs.forEach { (k, v) -> o.put(k, v ?: JSONObject.NULL) }
        return o
    }

    /** ① 站点计划分钟：真实键工作，`minutes` / `offsetMinutes` 不再接 */
    @Test
    fun `站点只认 plannedMinutes，minutes 与 offsetMinutes 不再被采纳`() {
        assertEquals(
            12,
            Station.parse(obj("id" to 5L, "name" to "石桥驿站", "plannedMinutes" to 12)).plannedMinutes,
        )
        // 别名单独出现：读成"没给"（multiNum 缺键 = 0，页面靠 `> 0` 门槛不显示假分钟数）
        assertEquals(0, Station.parse(obj("id" to 5L, "name" to "X", "minutes" to 30)).plannedMinutes)
        assertEquals(0, Station.parse(obj("id" to 5L, "name" to "X", "offsetMinutes" to 45)).plannedMinutes)
        // 真实键优先：即使报文里同时混着旧别名，也不许被旧别名抢走
        assertEquals(
            8,
            Station.parse(obj("plannedMinutes" to 8, "minutes" to 30, "offsetMinutes" to 45)).plannedMinutes,
        )
    }

    /** ② 待发车分钟：只认后端的 waitDepartureMinutes，`waitMinutes` 是小程序局部变量名 */
    @Test
    fun `待发车只认 waitDepartureMinutes，waitMinutes 读成 null`() {
        assertEquals(
            6,
            BusVehicle.parse(obj("busId" to 11L, "waitDepartureMinutes" to 6)).waitDepartureMinutes,
        )
        assertNull(BusVehicle.parse(obj("busId" to 11L, "waitMinutes" to 6)).waitDepartureMinutes)
        // 显式 null 不许顺着候选名去接 waitMinutes（旧写法正是这里凭空给出一个分钟数）
        assertNull(
            BusVehicle.parse(obj("busId" to 11L, "waitDepartureMinutes" to null, "waitMinutes" to 9))
                .waitDepartureMinutes,
        )
    }

    /** ③ 换乘方案时长：只认 Candidate 的 durationMinutes，`totalMinutes` / `minutes` 不再接 */
    @Test
    fun `换乘方案时长只认 durationMinutes，totalMinutes 与 minutes 不再被采纳`() {
        val real = TransitPlan.parse(obj("name" to "方案 A", "legCount" to 2, "durationMinutes" to 86))
        assertEquals(86, real.minutes)

        val legacy = TransitPlan.parse(obj("name" to "方案 A", "legCount" to 2, "totalMinutes" to 99))
        assertEquals("totalMinutes 没有生产者，不许再当契约键", 0, legacy.minutes)

        assertEquals(
            0,
            TransitPlan.parse(obj("name" to "方案 A", "minutes" to 77)).minutes,
        )
        // 信封级的 totalDurationMinutes（整单总时长，另一个对象另一个口径）也不许流进这里
        assertEquals(
            0,
            TransitPlan.parse(obj("name" to "方案 A", "totalDurationMinutes" to 210)).minutes,
        )
    }

    /** ④ 班次总时长：只认 plannedDurationMinutes，段时长 durationMinutes 不许冒充 */
    @Test
    fun `班次总时长只认 plannedDurationMinutes，段时长 durationMinutes 读成 0`() {
        assertEquals(
            120,
            ShiftTask.parse(obj("shiftId" to 7L, "plannedDurationMinutes" to 120)).plannedDurationMinutes,
        )
        // 同一个键名在段对象里是"这一段多久"，在班次对象里没有生产者 ⇒ 冒充即错口径
        assertEquals(
            0,
            ShiftTask.parse(obj("shiftId" to 7L, "durationMinutes" to 55)).plannedDurationMinutes,
        )
    }

    /** ⑤ 步行分钟：只认 walkMinutes，`minutes` 不再接；缺键时 walkText 必须说"接口未给" */
    @Test
    fun `步行分钟只认 walkMinutes，缺键时 walkText 不编数字`() {
        assertEquals(22, Reachability.parse(obj("reachable" to true, "walkMinutes" to 22)).walkMinutes)
        assertNull(Reachability.parse(obj("reachable" to true, "minutes" to 9)).walkMinutes)

        val given = Reachability.parse(obj("reachable" to true, "walkMinutes" to 22))
        assertTrue(given.walkText.contains("22"))
        val missing = Reachability.parse(obj("reachable" to true, "minutes" to 9))
        assertNull(missing.walkMinutes)
        assertTrue("缺键时 walkText 不许带出别名里的数字，实际=${missing.walkText}", !missing.walkText.contains("9"))
    }

    /** ⑥ 演示面与解析层自洽：DemoData 改了键名以后，解析层仍要读出非零方案时长 */
    @Test
    fun `演示包里的方案时长能被解析层读出（防止 parser 与 DemoData 键名漂移）`() {
        // 这一条量的是演示报文键形与解析层的自洽，不是"接口挂了要不要回落"，所以显式开闸；
        // 类级 @After 负责还给进程。
        TestHost.demoBackend()
        val p = DemoData.respond("/send/topology", emptyMap(), null, "GET")
        val plans = p.obj?.optJSONArray("plans")
        assertTrue("演示拓扑报文里没有 plans", plans != null && plans.length() > 0)
        val read = (0 until plans!!.length()).map { TransitPlan.parse(plans.getJSONObject(it)) }
        // 每一套方案都要有非零时长：读成 0 就意味着两侧键名又对不上了
        assertTrue(
            "演示方案时长读成 0，parser 与 DemoData 漂移：${read.map { it.minutes }}",
            read.all { it.minutes > 0 },
        )
        assertEquals(listOf(86, 142), read.map { it.minutes })
    }
}
