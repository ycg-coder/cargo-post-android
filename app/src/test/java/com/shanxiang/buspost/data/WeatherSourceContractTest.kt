package com.shanxiang.buspost.data

import com.shanxiang.buspost.data.repo.Repo
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 高德天气限流当天的契约（D-173）。
 *
 * 用户口径："高德限流了，请注意"。核下来的现状比"没数据"更麻烦：
 * 高德那一路 `status != "1"` 时旧写法直接 `return null`，**错误码被丢掉**，
 * 然后 open-meteo 顶上 —— 屏上看着一切正常。而 `WeatherCard` 的 meta 那行
 * 优先显示"更新于 HH:mm"，把 `source` 整行盖掉，于是限流当天**没有任何一处**
 * 说得出"这组数其实来自 open-meteo、高德为什么没给"。
 * `Repo.kt` 里那句旧注释"界面已经明写来源高德天气"因此是假的（已改）。
 *
 * 这一本钉三件事：① 认得的码翻成人话，认不得的**带上原 infocode**（不许塌成"网络不好"）；
 * ② 主源失败时 `primaryFailure` 非空、成功时为空（两侧都要有值可判，不能只测失败侧）；
 * ③ 两路都没给时，`source` 说的是"未取到"而不是留一个看起来像数的空壳。
 */
class WeatherSourceContractTest {

    @Test
    fun `配额类码翻成今日配额已用完并带上码值`() {
        listOf("10003", "10004", "10005").forEach { code ->
            val got = Repo.amapWeatherFailureOf(JSONObject("""{"status":"0","infocode":"$code"}"""))
            assertTrue("$code 要说成配额：$got", got.contains("配额"))
            assertTrue("要带上原码：$got", got.contains(code))
        }
    }

    @Test
    fun `平台不匹配那一类要说清是 key 用错了地方`() {
        val got = Repo.amapWeatherFailureOf(JSONObject("""{"status":"0","infocode":"10009"}"""))
        assertTrue(got, got.contains("Web 服务"))
    }

    @Test
    fun `认不得的码不许塌成一句笼统话`() {
        val got = Repo.amapWeatherFailureOf(JSONObject("""{"status":"0","infocode":"20003"}"""))
        assertEquals("高德天气返回 infocode=20003", got)
        // 连 infocode 都没有：也不能说成"网络不好"（那是替用户下结论）
        assertTrue(Repo.amapWeatherFailureOf(JSONObject("""{"status":"0"}""")).contains("没返回可用数据"))
    }

    @Test
    fun `兜底壳的 source 说的是没取到，不是一个看起来像数的空壳`() {
        val empty = com.shanxiang.buspost.data.model.WeatherInfo(
            text = "", temperature = 0, feel = 0, wind = "",
            source = "未取到", icon = "", humidity = "", tips = "",
            forecast = emptyList(), mock = true, primaryFailure = "高德天气今日配额已用完（infocode=10004）",
        )
        assertTrue(empty.mock)
        assertEquals("未取到", empty.source)
        assertTrue(empty.primaryFailure.contains("配额"))
        // 温度 0 在 mock 态**不许**被渲染成"0°C"：页面靠 mock 决定整行不画（§14 没有值不许有业务语义）
        assertEquals(0, empty.temperature)
    }

    /**
     * 逆地理与天气撞的是同一批高德 REST 码，说法必须出自同一张表（只换接口名）。
     *
     * 为什么这条要钉：`reverseGeo` 以前任何一种失败都回同一个 `EMPTY`，限流与"报文缺字段"
     * 与"key 没配"在调用方眼里一模一样，于是天气那一格只能说"还没拿到区域码"——
     * 把服务商的配额问题写成了本机的定位问题（D-173 同一族的第二格）。
     */
    @Test
    fun `逆地理与天气共用同一张码表，只换接口名`() {
        val weather = Repo.amapInfocodeText("高德天气", "10003")
        val regeo = Repo.amapInfocodeText("高德逆地理", "10003")
        assertTrue("逆地理限流也要说配额：$regeo", regeo.contains("配额"))
        assertTrue("要带上原码：$regeo", regeo.contains("10003"))
        assertEquals(
            "同一个码只能接口名不同",
            regeo.replace("高德逆地理", "高德天气"),
            weather,
        )
        // 认不得的码同样不许塌成笼统话
        assertEquals("高德逆地理返回 infocode=20003", Repo.amapInfocodeText("高德逆地理", "20003"))
    }

    /**
     * 区域码为空有两种成因，不能共用一句话（D-173）。
     * 传话 = 高德逆地理答过但没给；空串 = 本机压根没定位到，谁也没被问过。
     */
    @Test
    fun `区域码空着的两种成因各有各的话，限流不许写成没定位到`() {
        val notLocated = Repo.noAdcodeReasonOf("")
        assertTrue(notLocated, notLocated.contains("还没拿到区域码"))
        // 中性那句不许把责任推给服务商，也不许出现"配额"——这一格根本没问过
        assertTrue(notLocated, !notLocated.contains("配额"))

        val limited = Repo.noAdcodeReasonOf("高德逆地理今日配额已用完（infocode=10003）")
        assertEquals("高德逆地理今日配额已用完（infocode=10003）", limited)
        // 关键断言：服务商给过原因时，不许被中性那句覆写掉
        assertTrue(limited, !limited.contains("还没拿到区域码"))
    }
}
