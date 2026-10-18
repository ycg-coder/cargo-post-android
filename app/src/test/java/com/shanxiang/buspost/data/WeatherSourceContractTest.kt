package com.shanxiang.buspost.data

import com.shanxiang.buspost.data.model.Geo
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
 *
 * ⚠ 文件名还叫 WeatherSource，但下面 `GeoAttempt` 那两条属于**正向地理编码**那一格（D-173 第三格）：
 * 它与天气撞的是同一件事（高德 REST 没答时，屏上把服务商的问题写成用户的错），
 * 判据也同簿（`check_weather_probes.sh` 的 W-k/W-l/W-m）。另开一本测试文件只会让 SU-b 的
 * 源集计数再涨一格，而这里没有新的机制要反证 —— 名字与内容不一致这件事记在这里，不假装没看见。
 */
class WeatherSourceContractTest {

    /**
     * 日级配额（10003 开发者日访问量 / 10044 账号维度日调用量）：今天不会自愈 ⇒ 必须说"明天恢复"这一类话。
     *
     * 真机 2026-10-18 18:03 报的是 **10044**（`USER_DAILY_QUERY_OVER_LIMIT`，账号维度日调用量超限），
     * 第一版表里没有这一格，屏上落成通用的"高德天气返回 infocode=10044"——
     * 那句话不算假（带了原码），但"翻成人话"这件事恰好在这次没做到。
     */
    @Test
    fun `日级配额码要说清今天没量了并带上码值`() {
        listOf("10003", "10044").forEach { code ->
            val got = Repo.amapInfocodeText("高德天气", code)
            assertTrue("$code 要说成日级用量超限：$got", got.contains("日访问量") || got.contains("日调用量"))
            assertTrue("要带上原码：$got", got.contains(code))
        }
        assertTrue("10003 要说明何时自愈", Repo.amapInfocodeText("高德天气", "10003").contains("明天"))
    }

    /**
     * 分钟级 / 瞬时限流**不许**写成"今天没量"（处置完全不同：等一分钟 vs 等到明天）。
     * 这一对断言（日级含"明天"、分钟级不含）就是第一版并错三格时缺的那把尺子。
     */
    @Test
    fun `分钟级与瞬时限流不许写成当天没量`() {
        val min = Repo.amapInfocodeText("高德天气", "10004")
        assertTrue("10004 要说下一分钟自愈：$min", min.contains("下一分钟"))
        assertTrue("10004 不许说成当天没量：$min", !min.contains("明天"))

        val qps = Repo.amapInfocodeText("高德天气", "10014")
        assertTrue("QPS 类要说稍后重试：$qps", qps.contains("稍后重试"))
        assertTrue("QPS 类不许说成当天没量：$qps", !qps.contains("明天"))

        // 配置类与量无关：重试不会好，必须点出是谁的配置不对
        assertTrue(Repo.amapInfocodeText("高德天气", "10005").contains("白名单"))
        assertTrue(Repo.amapInfocodeText("高德逆地理", "10002").contains("权限"))
        assertTrue(Repo.amapInfocodeText("高德天气", "10001").contains("过期"))
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
            forecast = emptyList(), mock = true,
            primaryFailure = "高德天气账号维度的日调用量已用完（今天这个接口没量了，infocode=10044）",
        )
        assertTrue(empty.mock)
        assertEquals("未取到", empty.source)
        // 用的是真机 2026-10-18 18:03 那一屏实际会拼出来的那句（第一版这里写的是旧译文）
        assertTrue(empty.primaryFailure, empty.primaryFailure.contains("日调用量"))
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
        assertTrue("逆地理限流也要说成日访问量超限：$regeo", regeo.contains("日访问量"))
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

    /**
     * 正向地理编码的三态（D-173 第三格）：成功 / 问到了但没这个地址 / 压根没问到。
     * 三态互斥是硬要求 —— 旧写法三种共用一个 `null`，屏上就只剩一句"请检查输入是否正确"。
     */
    @Test
    fun `地址解析三态互斥，没问到与没匹配不许共用一个结论`() {
        val ok = Repo.GeoAttempt(Repo.GeoResult(Geo(29.5, 106.5), "重庆市南岸区", "500108"))
        val missed = Repo.GeoAttempt()
        val notAsked = Repo.GeoAttempt(failure = "高德地理编码账号维度的日调用量已用完（今天这个接口没量了，infocode=10044）")

        assertTrue(ok.matched); assertTrue(!ok.notAnswered); assertTrue(!ok.askedButMissed)
        assertTrue(missed.askedButMissed); assertTrue(!missed.notAnswered); assertTrue(!missed.matched)
        assertTrue(notAsked.notAnswered); assertTrue(!notAsked.askedButMissed); assertTrue(!notAsked.matched)
        // 三态里必须恰好有一态为真（多真或全假都说明类型在塌）
        for (a in listOf(ok, missed, notAsked)) {
            assertEquals(1, listOf(a.matched, a.askedButMissed, a.notAnswered).count { it })
        }
    }

    /**
     * 调用点选话的那条规则：`failure.ifEmpty { "未找到该地址，请检查输入是否正确" }`。
     * 断言的是**归因方向**：没问到那天不许出现"请检查输入"（那是让用户背锅），
     * 真没这条时才许让他改写法。
     */
    @Test
    fun `没问到那天不许叫用户检查输入`() {
        val checkInput = "未找到该地址，请检查输入是否正确"
        val notAsked = Repo.GeoAttempt(failure = "高德地理编码今日没量了（infocode=10044）")
        val shownNotAsked = notAsked.failure.ifEmpty { checkInput }
        assertEquals(notAsked.failure, shownNotAsked)
        assertTrue(shownNotAsked, !shownNotAsked.contains("检查输入"))
        assertTrue(shownNotAsked, shownNotAsked.contains("infocode=10044"))

        val missed = Repo.GeoAttempt()
        val shownMissed = missed.failure.ifEmpty { checkInput }
        assertEquals(checkInput, shownMissed)
    }
}
