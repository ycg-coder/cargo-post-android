package com.shanxiang.buspost.core.update

import org.json.JSONObject

/**
 * 检查更新的**纯判定层**（#48）：只吃 GitHub Releases API 的报文与本机 versionCode，产出"这一格该说什么"。
 *
 * 放在 `core/update` 且不带 Android 依赖的原因是工程里没有 Robolectric：
 * 只有纯函数才能让 JVM 用例逐格钉住"没查到"与"已是最新"是两句不同的话（这是本条通路最容易被写歪的一格）。
 *
 * 数据源按用户 2026-10-12 的决定 = **GitHub Releases 单源**（`https://api.github.com` 的 latest release），
 * 不新增后端版本表：后端 `grep app_version/checkUpdate` 实测 0 命中，把天气那套"绕一层只会抹平来源"的理由同样用在这里。
 */

/** 一次"有没有新版本"的结论。四个分支**互斥**，且 `NotAvailable` 与 `Failed` 绝对不许合并。 */
sealed class UpdateOutcome {
    /** 有可比对的正式版，且比本机新 */
    data class Available(val release: Release) : UpdateOutcome()

    /** 查到了正式版，且版本号不高于本机 */
    data class UpToDate(val currentTag: String) : UpdateOutcome()

    /**
     * 仓库里**还没有任何 Release**（GitHub 对 `releases/latest` 回 404）。
     * 这一格必须说"还没发布过可更新的版本"，不能说"已是最新" —— 前者是事实，后者是替没发生的事下结论。
     */
    object NoReleaseYet : UpdateOutcome()

    /** 请求没成功（网络、限流、非 200、报文不认识）：只能如实报"没查到"，并带原话 */
    data class Failed(val reason: String) : UpdateOutcome()
}

/** GitHub release 里我们真正用得上的四件事，其余键（上传时间、作者、tarball）一律不读。 */
data class Release(
    val tagName: String,
    /** 资源名固定为 `app-release.apk` 的那一条；没有它 = 这个 Release 不能就地自装 */
    val apkUrl: String,
    /** `assets[].size`（字节）：下载完要按它核对，缺键就是 null（不塌成 0） */
    val apkSize: Long?,
    val body: String,
    val prerelease: Boolean,
)

/** 本机版本：`versionCode` 是唯一的比较依据，`versionName` 只用来给人看。 */
data class CurrentVersion(val versionCode: Int, val versionName: String)

/**
 * `tag_name` → `versionCode` 的口径：发布时打的是 `v<versionCode>`（CI 那步 `gh release create "v$N"`），
 * 所以剥掉前导 `v`/`V` 之后必须是**纯数字**才认。
 *
 * ⚠ 不许退回"比 `versionName` 字符串"：`1.0.9` 与 `1.0.10` 按字符串比会得出"1.0.10 更小"，
 * 而这恰恰是版本号进位时用户第一次真的收不到更新的那个坑。
 */
internal fun releaseCodeOf(tagName: String): Int? {
    val t = tagName.trim().removePrefix("v").removePrefix("V")
    if (t.isEmpty() || !t.all { it.isDigit() }) return null
    return t.toIntOrNull()
}

/** 从 `assets[]` 里挑出可安装的那一个 APK：按 `name == app-release.apk`，再退到 `content_type` 是 APK。 */
private fun pickApk(o: JSONObject): Pair<String, Long?>? {
    val assets = o.optJSONArray("assets") ?: return null
    for (i in 0 until assets.length()) {
        val a = assets.optJSONObject(i) ?: continue
        val name = a.optString("name")
        val type = a.optString("content_type")
        val url = a.optString("browser_download_url")
        if (url.isEmpty()) continue
        if (name == APK_ASSET_NAME || type == APK_CONTENT_TYPE) {
            // size 缺键给 null，不塌成 0：0 会被"下载完字节数 == 0"当成校验通过
            val size = if (a.has("size") && !a.isNull("size")) a.optLong("size") else null
            return url to size
        }
    }
    return null
}

/**
 * 解析 `GET /repos/{owner}/{repo}/releases/latest` 的报文。
 * 返回 null = 报文里根本没有可自装的 APK 资源（这时调用方给 `Failed`，不是 `UpToDate`）。
 */
internal fun parseRelease(text: String): Release? {
    val o = try {
        JSONObject(text)
    } catch (e: Exception) {
        return null
    }
    val tag = o.optString("tag_name")
    if (tag.isEmpty()) return null
    val apk = pickApk(o) ?: return null
    return Release(
        tagName = tag,
        apkUrl = apk.first,
        apkSize = apk.second,
        body = o.optString("body"),
        prerelease = o.optBoolean("prerelease", false),
    )
}

/**
 * 结论判定。`httpCode`/`error` 与 `body` 三者的关系是这本判据的重点：
 *
 * · 404 且报文是 GitHub 那句 `Not Found` ⇒ `NoReleaseYet`（**不是** UpToDate）；
 * · 403/429（未认证 60 次/时的限流）⇒ `Failed("…")`，页面不许把它显示成"已是最新"；
 * · 200 但解析不出 APK 资源 ⇒ `Failed`；
 * · 预发布（`prerelease=true`）默认**不推**：正式版用户不该被 CI 的手工试验包催着装。
 *
 * ⚠ 2026-10-19 发布口径：这几句 `reason` 是**要上屏**的（「设置 → 关于 → 检查更新」那一行与升级弹窗），
 *   所以里面不再写 HTTP / 接口 / 报文 / APK 这些开发词，改成"谁没给 + 下一步做什么"。
 *   结果码本身没有丢 —— 它在 `AppUpdater.check` 那一格进 logcat（`BUS_UPDATE`）。
 *   这一族措辞由 `check_release_wording_probes.sh` 钉住，改回带码的那句会红。
 */
internal fun decideUpdate(body: String?, httpCode: Int, error: String?, current: CurrentVersion): UpdateOutcome {
    if (httpCode == 404) return UpdateOutcome.NoReleaseYet
    if (httpCode == 403 || httpCode == 429) {
        return UpdateOutcome.Failed(
            if (error != null) error else "问更新服务器问得太频繁，它让我们一小时后再问。稍后再点一次「检查更新」。"
        )
    }
    if (error != null) return UpdateOutcome.Failed(error)
    if (httpCode != 200 || body.isNullOrEmpty()) {
        return UpdateOutcome.Failed("更新服务器这会儿没回话。稍后再点一次「检查更新」。")
    }
    val release = parseRelease(body)
        ?: return UpdateOutcome.Failed("最新那一版里没有能直接装的安装包，请让站点人工给一个。")
    if (release.prerelease) return UpdateOutcome.UpToDate(current.versionName)
    val code = releaseCodeOf(release.tagName)
        ?: return UpdateOutcome.Failed("更新页写的版本「${release.tagName}」跟本机对不上号，请让站点确认要装哪一版。")
    return if (code > current.versionCode) UpdateOutcome.Available(release)
    else UpdateOutcome.UpToDate(current.versionName)
}

/**
 * 更新公告的展示口径 —— **纯函数**，JVM 用例逐格钉着（界面上那两格升级弹窗都读这一个出口）。
 *
 * 公告原文是发布作者写在 `RELEASE_NOTES.md` 里、由 CI 抄进 GitHub Release 的那一段，
 * 本机**不重写、不补写**，只做两件事：
 *  · 空的就说空 —— 不许塞一句"修复了一些问题，提升使用体验"，那句话谁都没写过；
 *  · 长了要**说明一共多少、这里显示了多少** —— 安静地切一刀会让用户以为那就是全部公告。
 */
internal const val ANNOUNCEMENT_MAX = 1200

internal fun announcementText(body: String): String {
    val t = body.trim()
    return when {
        t.isEmpty() -> "这条发布没写更新公告。"
        t.length <= ANNOUNCEMENT_MAX -> t
        else -> t.take(ANNOUNCEMENT_MAX) + "\n…（公告共 " + t.length + " 字，这里只显示前 " + ANNOUNCEMENT_MAX + " 字）"
    }
}

/** GitHub 未认证限流的判别：`X-RateLimit-Remaining: 0` 时报文里带 `rate limit`，别把它演成"没有新版本" */
internal fun isRateLimited(httpCode: Int, body: String?): Boolean =
    (httpCode == 403 || httpCode == 429) && body?.contains("rate limit", ignoreCase = true) == true

/** 一屏只该说一句话：把结论翻成界面文案（`Failed` 与 `NoReleaseYet` 不共用一句） */
internal fun updateText(o: UpdateOutcome, current: CurrentVersion): String = when (o) {
    is UpdateOutcome.Available -> "发现新版本 ${o.release.tagName}（你现在的是 ${current.versionName}）"
    is UpdateOutcome.UpToDate -> "已是最新版本（${current.versionName}）"
    UpdateOutcome.NoReleaseYet -> "还没有可更新的新版本"
    is UpdateOutcome.Failed -> "没查到新版本：${o.reason}"
}

private const val APK_ASSET_NAME = "app-release.apk"
private const val APK_CONTENT_TYPE = "application/vnd.android.package-archive"
