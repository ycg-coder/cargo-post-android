package com.shanxiang.buspost.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.shanxiang.buspost.BuildConfig
import com.shanxiang.buspost.core.net.Api
import com.shanxiang.buspost.core.net.ApiException
import com.shanxiang.buspost.core.net.NetworkException
import kotlinx.coroutines.CancellationException
import java.io.File
import java.io.IOException

/**
 * 更新通路的执行层（#48）：取 latest release → 判定 → 下载到私有缓存 → 交给系统安装器。
 *
 * 判定与文案都在 [AppUpdate] 的纯函数里（JVM 可测）；这一层只负责"真的去拿、真的落盘、真的唤起安装"，
 * 并且保证每一格的失败原因来自真实响应：
 * 没查到就说没查到（带原话），仓库里还没有 Release 就说"还没发布过可更新的版本"，绝不演成"已是最新"。
 */
object AppUpdater {

    /** latest release 接口地址；仓库坐标来自 `BuildConfig.UPDATE_REPO`（build.gradle 可覆盖，不写死在这一层） */
    fun latestUrl(): String = "https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest"

    /** 一次网络往返的原始结果：`code` 200=拿到报文；404=仓库没有 Release；其余/异常=这一问没成功 */
    internal data class Raw(val code: Int, val body: String?, val error: String?)

    /**
     * 取报文并把"GitHub 的 404"原样带出去。
     * `Api.rawGet` 对非 2xx 抛 [ApiException]（带 HTTP 码），404 正是 `releases/latest` 在**还没有任何发布**时的回答，
     * 所以这里不能把它压成一句"请求失败"，否则 `decideUpdate` 无法区分"没发布过"与"这次没问到"。
     */
    // 参数名叫 `fetch` 而不是 `get`：判据 TH-e 靠"try 段里有没有挂起点"决定要不要查取消配对，
    // 它认得出 `fetch(`，认不出一个匿名 lambda 参数 —— 名字选错就会让这一格掉进"免检"，
    // 而这一格恰恰是最需要"取消必须先穿透"的地方（见下面第一条 catch）。
    internal suspend fun fetchRaw(fetch: suspend (String) -> String): Raw = try {
        Raw(200, fetch(latestUrl()), null)
    } catch (e: CancellationException) {
        throw e
    } catch (e: ApiException) {
        Raw(e.code, null, e.message)
    } catch (e: NetworkException) {
        Raw(0, null, e.message ?: "网络连接失败")
    } catch (e: Exception) {
        Raw(0, null, e.message ?: "更新检查失败")
    }

    /** 一次完整检查。默认走真实 `Api.rawGet`；测试注入自己的取数函数（与天气那一路同一套注入方式）。 */
    suspend fun check(
        current: CurrentVersion = CurrentVersion(BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME),
        fetch: suspend (String) -> String = { url -> Api.rawGet(url) },
    ): UpdateOutcome {
        val raw = fetchRaw(fetch)
        return decideUpdate(raw.body, raw.code, raw.error, current)
    }

    /**
     * 下载 APK 到应用私有缓存并返回落盘文件。
     * 字节数与 release 声明的 `size` 不符就是失败，且**删掉半个包再抛** —— 留着它下一次会装出解释不了的"安装失败"。
     */
    suspend fun download(
        context: Context,
        release: Release,
        // `block` 同上一条理由：让 TH-e 看得见这里在等一个挂起的下载
        block: suspend (url: String, dest: File) -> Long,
    ): File {
        val dir = File(context.cacheDir, APK_DIR).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val dest = File(dir, "app-release.apk")
        val written = try {
            block(release.apkUrl, dest)
        } catch (e: CancellationException) {
            dest.delete()
            throw e
        } catch (e: Exception) {
            dest.delete()
            throw e
        }
        val declared = release.apkSize
        if (declared != null && written != declared) {
            dest.delete()
            throw IOException("安装包不完整：写了 $written 字节，发布页说 $declared 字节")
        }
        if (written <= 0L) {
            dest.delete()
            throw IOException("安装包是空的")
        }
        return dest
    }

    /**
     * 真实下载出口：流式写盘，返回字节数（不整包进内存）。
     * 进度按 HTTP 响应头的 `Content-Length` 算 —— 头里没给长度（分块传输）就一次都不回调，
     * 界面那一格停在 0% 而不是编一个"看着在动"的假进度。
     */
    suspend fun httpDownload(url: String, dest: File, onProgress: (Float) -> Unit = {}): Long =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val conn = java.net.URL(url).openConnection()
                conn.connectTimeout = 20_000
                conn.readTimeout = 60_000
                val declared = conn.contentLengthLong
                conn.getInputStream().use { input ->
                    dest.outputStream().use { output ->
                        var total = 0L
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            total += n
                            if (declared > 0L) onProgress((total.toFloat() / declared.toFloat()).coerceIn(0f, 1f))
                        }
                        total
                    }
                }
            } catch (e: IOException) {
                // 这一串会**原样上屏**（更新弹窗与「设置 → 关于 → 检查更新」都读它）。
                // OkHttp 的原文长这样，是真机 2026-10-15 上屏过的：
                //   failed to connect to github.com/20.205.243.166 (port 443) from /10.17.200.82 (port 38122) after 20000ms
                // 内网地址、随机源端口、毫秒超时数对用户没有信息量，只显得像坏了 ——
                // 原文留在 cause 里进 logcat，上屏只给"连不上谁 + 现在能干什么"。
                val host = url.substringAfter("://", "").substringBefore("/").ifEmpty { "更新服务器" }
                throw NetworkException("安装包没下载下来：连不上 $host。换一张能出网的 SIM/Wi-Fi 再点一次「立即更新」，或让站点帮忙装。", e)
            }
        }

    /**
     * 唤起系统安装器。URI 必须走 [FileProvider]：`file://` 在 API 24+ 会直接 `FileUriExposedException`，
     * 而那种崩溃会被用户读成"这个 app 更新不了"。
     */
    fun installIntent(context: Context, apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", apk)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** 有没有"安装未知应用"授权（本机 minSdk=26，这一问恒适用） */
    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    /** 没授权时把这一步交给用户：直接跳系统那一页，不静默失败、也不假装装上了 */
    fun unknownSourcesIntent(): Intent = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${BuildConfig.APPLICATION_ID}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private const val APK_DIR = "apk"
}
