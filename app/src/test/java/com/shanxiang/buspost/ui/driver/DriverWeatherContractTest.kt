package com.shanxiang.buspost.ui.driver

import com.shanxiang.buspost.data.model.WeatherInfo
import com.shanxiang.buspost.data.repo.weatherTipsFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #42「天气提醒司机和用户应该文案不一样」的契约。
 *
 * 这一本钉四件事，缺一件就回到原来的样子：
 * · **两份说法必须真的不一样**：旧写法全工程只有一条 `farmTips`，六条清一色货运口径
 *   （"给山货加盖苫布"），司机端拿到的是"对着司机说寄件人的话"；
 * · **司机侧只说动作，不冒充知道路面**：我们没有路面传感，写一句"路面湿滑"就是造第二条真相；
 * · **天气没取到时两份都空**，页面那一栏不出现（§14：没有值不许有业务语义，也不许兜一句万能提示）；
 * · **司机端那一栏的取数闸门**：15 分钟，且时钟倒退时不得变成"每次回读都发一次 REST"。
 */
class DriverWeatherContractTest {

    /** 逐分支点名：条件抄自高德实况 `dayweather` 与 open-meteo `weatherText()` 的真实产出词 */
    private val rows = listOf(
        "小雨" to 14,
        "中雨" to 9,
        "大雨" to 22,
        "雷阵雨" to 25,
        "雨夹雪" to 1,
        "大雪" to -3,
        "雾" to 8,
        "霾" to 12,
        "晴" to 33,
        "晴" to 21,
        "多云" to 18,
        "阴" to 16,
        "大风" to -2,
        "浮尘" to 19,
    )

    /** 司机侧动作词表：每条司机说法至少带一个"因天气而变的操作"，否则就是把用户那句话换个标点 */
    private val driverActions = listOf("检查", "减速", "雾灯", "延后", "通风", "放缓", "留意", "按班次")

    /** 路面断言词表：这些是在替后端/传感器说话，本机一个路面观测量都没有 */
    private val roadClaims = listOf("路面湿滑", "已结冰", "塌方", "封路", "畅通", "路面积水")

    @Test
    fun `同一场天气的两份说法必须不同，且都非空`() {
        for ((cond, temp) in rows) {
            val (user, driver) = weatherTipsFor(cond, temp)
            assertTrue("$cond 用户侧为空", user.isNotEmpty())
            assertTrue("$cond 司机侧为空", driver.isNotEmpty())
            assertNotEquals("$cond 两侧同文（回到只有一份文案的样子）", user, driver)
        }
    }

    @Test
    fun `司机侧每条都要落一个可执行动作`() {
        for ((cond, _) in rows) {
            val driver = weatherTipsFor(cond, 20).second
            assertTrue(
                "$cond 司机侧没有动作词（$driver）",
                driverActions.any { driver.contains(it) },
            )
        }
    }

    @Test
    fun `司机侧不得声称知道路面实际状况`() {
        for ((cond, temp) in rows) {
            val driver = weatherTipsFor(cond, temp).second
            roadClaims.forEach { banned ->
                assertTrue("$cond 司机侧写了「$banned」：$driver", !driver.contains(banned))
            }
        }
    }

    @Test
    fun `天气没取到时两份说法都为空`() {
        assertEquals("" to "", weatherTipsFor("", 20))
    }

    @Test
    fun `没取到天气时司机端那一栏不出现`() {
        // 与 Repo.weather 的空态生产者同形：mock=true 且两个 tips 都是空串
        val empty = WeatherInfo(
            text = "", temperature = 0, feel = 0, wind = "", source = "未取到",
            icon = "", humidity = "", tips = "", driverTips = "", mock = true,
        )
        assertEquals("", DriverWeatherTip.lineOf(null))
        assertEquals("", DriverWeatherTip.lineOf(empty))
    }

    @Test
    fun `取到天气时司机端显示的是司机那份说法`() {
        val w = WeatherInfo(
            text = "中雨", temperature = 9, feel = 7, wind = "风 东北 3级", source = "高德天气",
            tips = "给山货加盖苫布", driverTips = "拉大跟车距离",
        )
        assertEquals("拉大跟车距离", DriverWeatherTip.lineOf(w))
    }

    @Test
    fun `取数闸门是15分钟而不是每次回读都发请求`() {
        val quarter = 15 * 60 * 1000L
        assertTrue("从没取过就必须取", DriverWeatherTip.shouldFetch(0L, 100_000L))
        assertFalse("14 分钟就重取（等于每轮回读都发 REST）", DriverWeatherTip.shouldFetch(100_000L, 100_000L + quarter - 1))
        assertTrue("整 15 分钟应重取", DriverWeatherTip.shouldFetch(100_000L, 100_000L + quarter))
        assertTrue("一小时没取应重取", DriverWeatherTip.shouldFetch(100_000L, 100_000L + quarter * 4))
    }

    /** 时钟倒退（单调时钟理论上不倒，但 `at` 可能来自上一轮更晚的读数）时不得每刷一次就打一次 REST */
    @Test
    fun `时间戳比当前读数还新时不重复取数`() {
        assertFalse("时间戳倒挂时每轮回读都会重发天气请求", DriverWeatherTip.shouldFetch(500_000L, 100_000L))
    }
}
