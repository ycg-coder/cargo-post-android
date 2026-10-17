package com.shanxiang.buspost.notify

import com.shanxiang.buspost.core.AppDomain
import com.shanxiang.buspost.data.repo.weatherTipsFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 天气提醒消息的契约（D-158）。
 *
 * 用户口径："还有分角色和具体天气的提醒消息你也没完成"。核完的现状是：
 * **屏上**的天气卡早就是分角色的（`weatherTipsFor` 返回两份说法，`DriverWeatherContractTest` 8 条在钉），
 * 但**推送**那一条不是：`weatherAlertText` 自己又抄了一张"天气→建议"表，而且只有一份正文，
 * 里面同时写着"出行请带雨具"（乘客的话）和"山货装车加盖苫布"（寄件/司机的话）
 * ⇒ 同一条提醒推给两种人，总有一半在读跟自己无关的话；两张表一漂移还会说出两句不同的建议。
 *
 * 这一本钉四件事：① 推送取的是**当前角色那一份**说法；② 不再存在第二张表；
 * ③ 严重度与图标的档位（2026-10-15 真机那条"正文写雾、图标是🌧"的回归）；
 * ④ 现象词表在通知与卡片两侧**同一批**（浮尘以前一边响一边不响）。
 */
class WeatherAlertContractTest {

    private val HEAVY = AlertEngine.WeatherSeverity.HEAVY
    private val LIGHT = AlertEngine.WeatherSeverity.LIGHT

    /** 前缀形状是「<图标> 恶劣天气预警|天气提醒」；图标可能是 1 个也可能是 2 个 UTF-16 单元，按空格切才稳 */
    private fun glyph(cond: String, temp: Int, s: AlertEngine.WeatherSeverity = LIGHT): String =
        AlertEngine.weatherAlertText(s, cond, temp, "", AppDomain.USER).first.substringBefore(" ")

    @Test
    fun `推送正文取的是当前角色那一份说法`() {
        val driver = AlertEngine.weatherAlertText(LIGHT, "中雨", 9, "北风2级", AppDomain.DRIVER).second
        val user = AlertEngine.weatherAlertText(LIGHT, "中雨", 9, "北风2级", AppDomain.USER).second
        val tips = weatherTipsFor("中雨", 9)
        assertEquals("司机收到的是司机那份", tips.second, driver.substringAfter("。"))
        assertEquals("用户收到的是用户那份", tips.first, user.substringAfter("。"))
        assertNotEquals("两侧同文 = 回到只有一份文案的样子", driver, user)
        // 角色错配的具体形状：司机那句不许出现乘客口径的"带雨具"，乘客那句不许出现"雾灯/跟车距离"
        assertTrue("司机句里混进了乘客动作：$driver", !driver.contains("带雨具"))
        assertTrue("用户句里混进了驾驶动作：$user", !user.contains("雾灯") && !user.contains("跟车距离"))
    }

    @Test
    fun `通知里不许存在第二张天气到建议的表`() {
        // 正文 = 事实句 + 天气卡那一份建议，一字不增：这样两处显示永远同源
        for ((cond, temp) in listOf("小雨" to 14, "雾" to 8, "晴" to 33, "多云" to 18)) {
            val body = AlertEngine.weatherAlertText(LIGHT, cond, temp, "", AppDomain.USER).second
            assertEquals("$cond 的正文与建议句不同源", weatherTipsFor(cond, temp).first, body.substringAfter("。"))
            assertTrue("$cond 正文该带天况与气温：$body", body.contains(cond) && body.contains("${temp}°C"))
        }
    }

    @Test
    fun `图标跟天况走 雾与高温低温都不许顶着一张下雨图`() {
        // 2026-10-15 真机：正文「当前天气：雾」而图标是 🌧 —— 用户读到的信号是图，不是那行小字
        assertEquals("🌫", glyph("雾", 8))
        assertEquals("🌫", glyph("霾", 12))
        assertEquals("🌫", glyph("浮尘", 19))
        assertEquals("🌧", glyph("小雨", 14))
        assertEquals("❄", glyph("雨夹雪", 1))
        assertEquals("☀", glyph("晴", 39))
        assertEquals("🧊", glyph("晴", -12))
        assertEquals("⛅", glyph("多云", 18))
        assertEquals("重度必须一眼可辨", "⚠️", glyph("暴雨", 22, HEAVY))
    }

    @Test
    fun `严重度表逐档点名`() {
        listOf("暴雨", "大雨", "大雪", "冰雹", "雷暴", "台风").forEach {
            assertEquals("$it 该是重度", HEAVY, AlertEngine.weatherSeverity(it, 10))
        }
        listOf("小雨" to 14, "中雨" to 9, "雾" to 8, "霾" to 12, "浮尘" to 19).forEach { (c, t) ->
            assertEquals("$c 该是轻度", LIGHT, AlertEngine.weatherSeverity(c, t))
        }
        assertEquals("高温", LIGHT, AlertEngine.weatherSeverity("晴", 38))
        assertEquals("低温", LIGHT, AlertEngine.weatherSeverity("晴", -10))
        // 不响的那一档也要点名：晴好/多云/阴天不该被推送打扰（旧实现没有这一格的反证）
        listOf("晴" to 21, "多云" to 18, "阴" to 16).forEach { (c, t) ->
            assertNull("$c $t° 不该预警", AlertEngine.weatherSeverity(c, t))
        }
        assertNull("天气没取到 = 不推", AlertEngine.weatherSeverity("", 20))
    }

    @Test
    fun `浮尘这类现象在通知与卡片两侧同一批词`() {
        // 旧实现：severity 只认"沙尘"、tips 只认"雾/霾/沙" ⇒ 高德实况常见的"浮尘"两边都漏
        assertTrue(AlertEngine.weatherSeverity("浮尘", 19) != null)
        val tips = weatherTipsFor("浮尘", 19)
        assertTrue("浮尘的用户句该讲能见度：${tips.first}", tips.first.contains("能见度"))
        assertTrue("浮尘的司机句该讲雾灯/降速：${tips.second}", tips.second.contains("雾灯"))
    }

    @Test
    fun `强对流那支排在降水之后`() {
        // "雷阵雨"带雨字，该走降水那句（苫布/刹车），不该被说成"延后或改日"
        assertTrue(weatherTipsFor("雷阵雨", 25).first.contains("苫布"))
        // 不带雨雪字的强对流才说延后
        listOf("冰雹" to 4, "台风" to 27).forEach { (c, t) ->
            val tips = weatherTipsFor(c, t)
            assertTrue("$c 用户句该说延后/改日：${tips.first}",
                tips.first.contains("延后") || tips.first.contains("改日"))
            assertTrue("$c 司机句要有可执行动作：${tips.second}",
                tips.second.contains("延后") || tips.second.contains("停"))
        }
    }

    @Test
    fun `标题按严重度分档 不带正文`() {
        val heavy = AlertEngine.weatherAlertText(HEAVY, "暴雨", 22, "南风3级", AppDomain.DRIVER).first
        val light = AlertEngine.weatherAlertText(LIGHT, "小雨", 14, "", AppDomain.DRIVER).first
        assertTrue("重度标题该是预警：$heavy", heavy.contains("恶劣天气预警"))
        assertTrue("轻度标题该是提醒：$light", light.contains("天气提醒"))
        assertTrue("标题不许把正文塞进来：$heavy", !heavy.contains("气温"))
    }
}
