package com.shanxiang.buspost.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #48 更新通路的**判定层**契约。
 *
 * 这本用例要钉住的是"四种'没有新版本'长得一样，但只有两种真的是没有"这件事：
 *  · 仓库里一条 Release 都没发过（GitHub 对 `releases/latest` 回 **404**）⇒ 只能说"还没发布过可更新的版本"；
 *  · 被限流 / 网络挂了 / 报文里没有 APK 资源 ⇒ 只能说"没查到"，并带原话；
 *  · 只有真的拿到版本号且不高于本机，才许说"已是最新版本"。
 * 旧状态是**这条通路整个不存在**（全仓 `checkUpdate`/FileProvider/`REQUEST_INSTALL_PACKAGES` 实测 0 命中），
 * 所以每条用例都对应"补上之后最容易被写歪的一格"。
 */
class AppUpdateContractTest {

    /**
     * 造一条 GitHub `releases/latest` 的报文（键名照 `AppUpdate.parseRelease` 实际读的四个键写）。
     * ⚠ 不用嵌套三引号拼 `size`：上一版把 `if (withSize) """, "size":$size"""` 写在三引号串里，
     *   编译过了但**拼出来的是断掉的 JSON** ⇒ `withSize=false` 那格测的是"报文坏了"而不是"没给 size"，
     *   实测红了才发现（`parseRelease` 返回 null）。改成显式两段拼接，坏不了。
     */
    private fun apkJson(
        tag: String = "v12",
        name: String = "app-release.apk",
        size: String = "48300000",
        withSize: Boolean = true,
        prerelease: Boolean = false,
        url: String = "https://github.com/ycg-coder/cargo-post-android/releases/download/v12/app-release.apk",
        contentType: String = "application/vnd.android.package-archive",
    ): String {
        val sizeField = if (withSize) ",\"size\":$size" else ""
        return "{\"tag_name\":\"$tag\",\"name\":\"乡邮智联 $tag\",\"prerelease\":$prerelease,\"draft\":false," +
            "\"body\":\"修了几个问题\",\"html_url\":\"https://github.com/x/y/releases/tag/$tag\"," +
            "\"assets\":[{\"name\":\"$name\",\"browser_download_url\":\"$url\"," +
            "\"content_type\":\"$contentType\"$sizeField}]}"
    }

    private val cur = CurrentVersion(versionCode = 11, versionName = "1.0.11-android")

    @Test
    fun `拿到比本机新的正式版就该报有更新`() {
        val o = decideUpdate(apkJson(), 200, null, cur)
        assertTrue("实际：$o", o is UpdateOutcome.Available)
        val r = (o as UpdateOutcome.Available).release
        assertEquals("v12", r.tagName)
        assertEquals(48300000L, r.apkSize)
        assertTrue(r.apkUrl.endsWith("/app-release.apk"))
    }

    /** 版本号只认 tag 里的数字（CI 打的是 `v<versionCode>`）；字符串比 `versionName` 会在 9→10 进位时判错 */
    @Test
    fun `比较用 versionCode 不是版本名字符串`() {
        assertEquals(12, releaseCodeOf("v12"))
        assertEquals(9, releaseCodeOf("V9"))
        // 反例：如果拿字符串比，"1.0.10" 会被判成比 "1.0.9" 旧 ⇒ 用户永远收不到更新且界面一句错都不报
        val o = decideUpdate(apkJson(tag = "v12"), 200, null, CurrentVersion(11, "1.0.9-android"))
        assertTrue("1.0.9 的设备必须收到 v12：$o", o is UpdateOutcome.Available)
    }

    @Test
    fun `tag 不是纯数字时报没查到而不是没更新`() {
        val o = decideUpdate(apkJson(tag = "1.0.12-android"), 200, null, cur)
        assertTrue("实际：$o", o is UpdateOutcome.Failed)
        assertTrue((o as UpdateOutcome.Failed).reason.contains("对不上号"))
        // 这一条是收紧的拒绝侧：不许把"我读不懂这个版本号"演成"你已经是最新"
        assertFalse(updateText(o, cur).contains("已是最新"))
    }

    @Test
    fun `版本相同或更旧才是真的已最新`() {
        val same = decideUpdate(apkJson(tag = "v11"), 200, null, cur)
        assertTrue("$same", same is UpdateOutcome.UpToDate)
        val older = decideUpdate(apkJson(tag = "v10"), 200, null, cur)
        assertTrue("$older", older is UpdateOutcome.UpToDate)
    }

    /** 仓库一条 Release 都没发过：`gh release list` 实测为空，所以这是新用户第一次点进来必然遇到的一格 */
    @Test
    fun `仓库还没有发布过时不许说已最新`() {
        val o = decideUpdate(null, 404, "外部接口 404", cur)
        assertEquals(UpdateOutcome.NoReleaseYet, o)
        val text = updateText(o, cur)
        assertTrue(text, text.contains("还没有可更新"))
        assertFalse(text, text.contains("已是最新"))
    }

    @Test
    fun `未认证限流要说成没查到并带原话`() {
        val body = """{"message":"API rate limit exceeded for 1.2.3.4."}"""
        assertTrue(isRateLimited(403, body))
        val o = decideUpdate(null, 403, "外部接口 403", cur)
        assertTrue("$o", o is UpdateOutcome.Failed)
        assertFalse(updateText(o, cur).contains("已是最新"))
    }

    @Test
    fun `网络失败与业务失败都归到没查到而不是没更新`() {
        assertTrue(decideUpdate(null, 0, "网络连接失败", cur) is UpdateOutcome.Failed)
        assertTrue(decideUpdate("", 200, null, cur) is UpdateOutcome.Failed)
        assertTrue(decideUpdate("<html>not json</html>", 200, null, cur) is UpdateOutcome.Failed)
    }

    /** 只有正式版会推：预发布（CI 手工试验包）不该催着农村用户装 */
    @Test
    fun `预发布不推给正式用户`() {
        val o = decideUpdate(apkJson(prerelease = true), 200, null, cur)
        assertTrue("$o", o is UpdateOutcome.UpToDate)
    }

    /** 没有可安装资源 = 这一版没法就地自装，必须报出来，而不是"查到了但没话可说" */
    @Test
    fun `发布里没有 APK 资源时报失败`() {
        val noAssets = """{"tag_name":"v12","assets":[]}"""
        assertTrue(decideUpdate(noAssets, 200, null, cur) is UpdateOutcome.Failed)
        // 名字与类型都不对才是"没有可安装资源"：GitHub 给 sources 的 content_type 是 application/zip，
        // 只改名而类型仍是 APK 时 `pickApk` 会按类型认下来（那是它设计上的第二条通道，不是漏洞）
        val wrongAsset = apkJson(name = "sources.zip", contentType = "application/zip")
        assertTrue("实际：${decideUpdate(wrongAsset, 200, null, cur)}", decideUpdate(wrongAsset, 200, null, cur) is UpdateOutcome.Failed)
        val byNameOnly = apkJson(name = "app-release.apk", contentType = "application/zip")
        assertTrue("按资源名也该认下来：$byNameOnly", decideUpdate(byNameOnly, 200, null, cur) is UpdateOutcome.Available)
    }

    /** `size` 缺键不许塌成 0：下载校验拿它比字节数，0 会让"半个包"通过 */
    @Test
    fun `安装包大小没给就是 null 不是零`() {
        val r = parseRelease(apkJson(withSize = false))
        assertTrue(r != null)
        assertNull(r!!.apkSize)
    }

    /** 结论到文案是一一映射：四个分支四种说法，不许两两共用 */
    @Test
    fun `四种结论各有自己的说法`() {
        val texts = listOf(
            updateText(UpdateOutcome.Available(parseRelease(apkJson())!!), cur),
            updateText(UpdateOutcome.UpToDate(cur.versionName), cur),
            updateText(UpdateOutcome.NoReleaseYet, cur),
            updateText(UpdateOutcome.Failed("限流"), cur),
        )
        assertEquals("四句应互不相同：$texts", 4, texts.distinct().size)
        assertTrue(texts[0], texts[0].contains("发现新版本"))
        assertTrue(texts[2], texts[2].contains("还没有可更新"))
        assertTrue(texts[3], texts[3].contains("没查到"))
    }

    /**
     * 更新公告那一格（两个升级弹窗共用的出口）。
     * 2026-10-19 用户点名"更新弹窗要写上更新公告"，所以这三格是那句要求的**下限**：
     * 有就原样给，没有就明说没有，太长要说截了多少 —— 唯独不许本机编一句。
     */
    @Test
    fun `公告空就说没写 不编一句提升体验`() {
        assertEquals("这条发布没写更新公告。", announcementText(""))
        assertEquals("这条发布没写更新公告。", announcementText("   \n  "))
        assertFalse("不许出现没人写过的漂亮话", announcementText("").contains("优化"))
    }

    @Test
    fun `公告有就原样给 一个字都不改`() {
        val note = "- 闪屏页重做\n- 公交卡片能进地图了"
        assertEquals(note, announcementText(note))
        assertEquals(note, announcementText("\n" + note + "\n\n"))
    }

    @Test
    fun `公告超长要说明截了多少而不是安静切`() {
        val long = "字".repeat(ANNOUNCEMENT_MAX + 37)
        val shown = announcementText(long)
        assertTrue(shown.startsWith("字".repeat(ANNOUNCEMENT_MAX)))
        assertTrue("要报出总字数与显示了多少：$shown", shown.contains("共 ${long.length} 字"))
        assertTrue(shown.contains("只显示前 $ANNOUNCEMENT_MAX 字"))
    }

    /**
     * 发的包不许出现开发专业词汇（2026-10-19 用户口径）。
     * 这里只钉**更新这一路**会上屏的那几句：判定层的四条 `reason` + 四种结论的文案。
     * 结果码改走 logcat（`AppUpdater.check` 里那条 `BUS_UPDATE`），所以摘掉码不算把信息弄丢。
     */
    @Test
    fun `更新通路那几句不含开发词`() {
        val banned = listOf("HTTP", "http", "接口", "报文", "APK", "infocode", "GitHub", "JSON", "json")
        val sentences = listOf(
            updateText(decideUpdate(null, 403, null, cur), cur),
            updateText(decideUpdate(null, 500, null, cur), cur),
            updateText(decideUpdate("""{"tag_name":"v12","assets":[]}""", 200, null, cur), cur),
            updateText(decideUpdate(apkJson(tag = "nightly-3"), 200, null, cur), cur),
            updateText(UpdateOutcome.Available(parseRelease(apkJson())!!), cur),
            updateText(UpdateOutcome.NoReleaseYet, cur),
            announcementText("修了三个问题"),
        )
        for (s in sentences) {
            for (w in banned) {
                assertFalse("这句屏上要出现开发词「$w」：$s", s.contains(w))
            }
        }
    }
}
