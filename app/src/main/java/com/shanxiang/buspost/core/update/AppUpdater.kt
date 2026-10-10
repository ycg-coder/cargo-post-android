package com.shanxiang.buspost.core.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import com.shanxiang.buspost.BuildConfig
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * app 内自更新：检查 GitHub Releases → 下载 APK → 触发系统安装。
 *
 * 流程：开发者 push 到 main → CI 构建 release APK → 创建 GitHub Release 并附上 APK →
 * 旧版 app 启动时调 GitHub API 发现新版本 → 弹窗 → 下载 → 安装。
 */
object AppUpdater {
    private const val TAG = "APP_UPDATER"
    private const val GITHUB_REPO = "ycg-coder/cargo-post-android"
    private const val APK_ASSET_NAME = "app-release.apk"

    private val http = OkHttpClient()

    data class ReleaseInfo(
        val versionCode: Int,
        val versionName: String,
        val changelog: String,
        val downloadUrl: String,
        val forceUpdate: Boolean,
    )

    /**
     * 检查 GitHub 是否有新版本。返回 null 表示已是最新或检查失败（静默，不打扰用户）。
     */
    suspend fun checkForUpdate(): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github.v3+json")
                .build()
            val response = http.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "GitHub API 返回 ${response.code}")
                return@withContext null
            }
            val json = JSONObject(response.body?.string() ?: return@withContext null)
            val tagName = json.optString("tag_name", "")
            val newVersionCode = parseVersionCode(tagName) ?: return@withContext null
            if (newVersionCode <= BuildConfig.VERSION_CODE) return@withContext null

            val assets = json.optJSONArray("assets")
            var downloadUrl = ""
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i)
                    if (asset?.optString("name") == APK_ASSET_NAME) {
                        downloadUrl = asset.optString("browser_download_url", "")
                        break
                    }
                }
            }
            if (downloadUrl.isEmpty()) {
                Log.w(TAG, "Release $tagName 没有 APK 附件")
                return@withContext null
            }

            val body = json.optString("body", "修复问题，提升稳定性")
            val name = json.optString("name", tagName)

            ReleaseInfo(
                versionCode = newVersionCode,
                versionName = name,
                changelog = body,
                downloadUrl = downloadUrl,
                forceUpdate = false,
            )
        } catch (e: Exception) {
            Log.w(TAG, "检查更新失败: ${e.message}")
            null
        }
    }

    private fun parseVersionCode(tag: String): Int? {
        // 支持格式: v46, V46, 46, release-46
        val num = tag.removePrefix("v").removePrefix("V").removePrefix("release-").trim()
        return num.toIntOrNull()
    }

    /**
     * 下载 APK 到缓存目录。返回 APK 文件，失败抛 IOException。
     * onProgress 回调下载进度 0.0 ~ 1.0。
     */
    suspend fun downloadApk(
        context: Context,
        url: String,
        onProgress: (Float) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(dir, APK_ASSET_NAME)
        if (target.exists()) target.delete()

        val request = Request.Builder().url(url).build()
        val response = http.newCall(request).execute()
        if (!response.isSuccessful) throw IOException("下载失败: HTTP ${response.code}")

        val body = response.body ?: throw IOException("响应体为空")
        val total = body.contentLength()
        var downloaded = 0L

        target.outputStream().use { out ->
            body.byteStream().use { input ->
                val buf = ByteArray(8192)
                var read: Int
                while (input.read(buf).also { read = it } != -1) {
                    out.write(buf, 0, read)
                    downloaded += read
                    if (total > 0) onProgress(downloaded.toFloat() / total)
                }
            }
        }
        target
    }

    /**
     * 触发系统安装界面。Android 8+ 需要 REQUEST_INSTALL_PACKAGES 权限，
     * 未授予时系统会自动引导用户去设置页开启。
     */
    fun installApk(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
