package com.shanxiang.buspost.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.shanxiang.buspost.core.ui.BusRadius
import com.shanxiang.buspost.core.ui.LocalBusTokens
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import com.shanxiang.buspost.BuildConfig
import com.shanxiang.buspost.core.net.attempt
import com.shanxiang.buspost.core.ui.BusDialogAction
import com.shanxiang.buspost.core.ui.BusIcons
import com.shanxiang.buspost.core.ui.InfoRow
import com.shanxiang.buspost.core.update.AppUpdater
import com.shanxiang.buspost.core.update.CurrentVersion
import com.shanxiang.buspost.core.update.Release
import com.shanxiang.buspost.core.update.UpdateOutcome
import com.shanxiang.buspost.core.update.updateText
import kotlinx.coroutines.launch

/**
 * 设置页「关于」里的"检查更新"那一行（#48）。
 *
 * 界面只做三件事：显示结论、接住用户那一下点击、把安装/授权交给系统。
 * **判定不在这一层**（全在 `core/update/AppUpdate.kt` 的纯函数里，JVM 用例逐格钉着），
 * 所以"没查到"与"已是最新"在这里也不可能被写成同一句。
 */
@Composable
fun UpdateCheckRow() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val current = CurrentVersion(BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME)

    var busy by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<UpdateOutcome?>(null) }
    var downloading by remember { mutableStateOf(false) }
    // 下载进度。以前这一格**根本没有进度状态**：`httpDownload` 的 onProgress 走默认空函数，
    // 回调被丢在地上，用户点完「立即更新」只看到一句静态的"正在下载新版本…"，
    // 而包是 78 MB —— 没有任何东西告诉他是没开始、还是在动。（冷启动那个弹窗一直有进度条，
    // 两条路共用同一个下载出口，只有一边画了，这就是"同一个动作两种反馈"。）
    val t = LocalBusTokens.current
    var progress by remember { mutableFloatStateOf(0f) }
    /** 安装那一步的原话（含"还差授权"那一格）：只在真出事时出现，不预置、不美化、不写"已更新" */
    var problem by remember { mutableStateOf<String?>(null) }

    InfoRow(
        label = "检查更新",
        value = when {
            busy -> "正在查…"
            downloading -> "下载中 ${(progress * 100).toInt()}%"
            outcome == null -> "看有没有新版本"
            else -> updateText(outcome!!, current)
        },
        icon = BusIcons.info,
        showNext = !busy && !downloading,
        onClick = {
            if (!busy && !downloading) {
                busy = true
                scope.launch {
                    // `attempt` 而不是 `runCatching`：这里在挂起作用域里，
                    // 用 runCatching 会把"用户离开这一页"的取消收成一次普通失败（判据 RC-b 点名的形状）。
                    outcome = AppUpdater.check(current)
                    busy = false
                }
            }
        },
    )

    when (val o = outcome) {
        is UpdateOutcome.Available -> AlertDialog(
            onDismissRequest = { outcome = null },
            title = { Text("发现新版本") },
            text = {
                val size = o.release.apkSize
                Text(
                    buildString {
                        append("版本 ${o.release.tagName}。")
                        if (size != null) append(" 安装包约 ${(size + 512_000) / 1_000_000} MB。")
                        // 更新说明是发布作者在 GitHub 上写的那段原文，本机不重写、不补写
                        if (o.release.body.isNotEmpty()) append("\n\n").append(o.release.body.take(600))
                    }
                )
            },
            confirmButton = {
                BusDialogAction(
                    "立即更新",
                    onClick = {
                        downloading = true
                        scope.launch {
                            progress = 0f
                            val r = attempt { installOrAskPermission(context, o.release) { p -> progress = p } }
                            downloading = false
                            r.exceptionOrNull()?.let { problem = it.message ?: "更新没成功" }
                        }
                    }
                )
            },
            dismissButton = { BusDialogAction("稍后再说", onClick = { outcome = null }) },
        )

        else -> Unit
    }

    if (downloading) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .height(8.dp)
                .clip(RoundedCornerShape(BusRadius.s))
                .background(t.goldLight),
        ) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = t.primary,
                trackColor = Color.Transparent,
            )
        }
    }

    problem?.let { reason ->
        AlertDialog(
            onDismissRequest = { problem = null },
            title = { Text("这次没更新成") },
            text = { Text(reason) },
            confirmButton = { BusDialogAction("知道了", onClick = { problem = null }) },
        )
    }
}

/**
 * 下载 → 唤起系统安装器。缺"安装未知应用"授权时**先跳系统那一页并原样报回来**，
 * 不静默失败，也不替用户假设"授权点了"。
 */
private suspend fun installOrAskPermission(
    context: Context,
    release: Release,
    onProgress: (Float) -> Unit = {},
) {
    if (!AppUpdater.canInstall(context)) {
        context.startActivity(AppUpdater.unknownSourcesIntent())
        throw IllegalStateException("还没允许本应用安装未知应用：已打开系统设置页，授权后再回来点一次「立即更新」")
    }
    val apk = AppUpdater.download(context, release) { url, dest -> AppUpdater.httpDownload(url, dest, onProgress) }
    context.startActivity(AppUpdater.installIntent(context, apk))
}
