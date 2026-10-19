package com.shanxiang.buspost.core.update

import com.shanxiang.buspost.core.ui.BusDialogAction
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.shanxiang.buspost.core.net.attempt
import com.shanxiang.buspost.core.ui.BusButton
import com.shanxiang.buspost.core.ui.BusCard
import com.shanxiang.buspost.core.ui.BusRadius
import com.shanxiang.buspost.core.ui.LocalBusTokens
import kotlinx.coroutines.launch

/**
 * 版本更新弹窗：显示新版本信息 + 下载进度 + 安装。
 *
 * 只在 [UpdateOutcome.Available] 时才被构造出来（[MainActivity] 那一格判的就是这个分支），
 * 所以这里不需要"查失败了也说成没更新"那一套 —— 失败的原因不在这个弹窗里演。
 * 下载与装包都走 [AppUpdater] 那一条已经验证过的通路（字节数比对 + FileProvider 授权 + 未知来源授权门槛），
 * 不在这页另起一套下载实现。
 */
@Composable
fun UpdateDialog(
    release: Release,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val t = LocalBusTokens.current
    val scope = rememberCoroutineScope()
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var downloadDone by remember { mutableStateOf(false) }
    /** 下载或唤起安装器那一步的原话：只在真出事时出现，不写"已更新" */
    var problem by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = { if (!downloading) onDismiss() }) {
        BusCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("发现新版本", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                val size = release.apkSize
                Text(
                    buildString {
                        append(release.tagName)
                        if (release.prerelease) append("（预发布）")
                        // 发布页没写 size 就说没写，不报一个猜的数字
                        if (size != null) append("  ·  安装包约 ${(size + 512_000) / 1_000_000} MB")
                        else append("  ·  发布页没给安装包大小")
                    },
                    fontSize = 13.sp,
                    color = t.textSecondary,
                )
                Spacer(Modifier.height(12.dp))

                Text("更新公告", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                // 公告原文是发布作者写在仓库 `RELEASE_NOTES.md` 里、CI 发版时抄进这一格的那段话，
                // 本机不重写、不补写：空的就说"没写"，超长的就说"截了多少"（[announcementText]）。
                // 这里给一个**可滚**的高度而不是 `.take(500)`：一版改了五六件事的长公告，
                // 安静地切一半比不显示更容易骗人 —— 用户会以为那就是全部。
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 190.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        announcementText(release.body),
                        fontSize = 13.sp,
                        color = t.ink,
                        lineHeight = 19.sp,
                    )
                }
                Spacer(Modifier.height(16.dp))

                when {
                    problem != null -> {
                        Text(
                            problem.orEmpty(),
                            fontSize = 13.sp,
                            color = t.danger,
                            lineHeight = 18.sp,
                        )
                        Spacer(Modifier.height(10.dp))
                        BusButton(
                            text = "知道了",
                            onClick = onDismiss,
                        )
                    }
                    downloadDone -> {
                        BusButton(
                            text = "稍后安装",
                            onClick = onDismiss,
                        )
                    }
                    downloading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                                .clip(RoundedCornerShape(BusRadius.s))
                                .background(t.track),
                        ) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth().height(36.dp),
                                color = t.primary,
                                trackColor = Color.Transparent,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "下载中 ${(progress * 100).toInt()}%",
                            fontSize = 12.sp,
                            color = t.textSecondary,
                        )
                    }
                    else -> {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            BusDialogAction(
                                text = "稍后再说",
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            BusDialogAction(
                                text = "立即更新",
                                primary = true,
                                onClick = {
                                    downloading = true
                                    scope.launch {
                                        // `attempt` 而不是 runCatching：这一步在挂起作用域里，
                                        // 把取消收成一次普通失败会盖掉"用户已经离开这一页"。
                                        val r = attempt { installOrAskPermission(context, release) { p -> progress = p } }
                                        downloading = false
                                        downloadDone = r.isSuccess
                                        r.exceptionOrNull()?.let { problem = it.message ?: "更新没成功" }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 下载 → 唤起系统安装器；缺"安装未知应用"授权时先把系统那一页打开，并原样告诉用户下一步做什么。
 * 与「设置 → 关于 → 检查更新」（[com.shanxiang.buspost.ui.common.UpdateSection]）走同一组 [AppUpdater] 出口。
 */
private suspend fun installOrAskPermission(
    context: Context,
    release: Release,
    onProgress: (Float) -> Unit,
) {
    if (!AppUpdater.canInstall(context)) {
        context.startActivity(AppUpdater.unknownSourcesIntent())
        throw IllegalStateException("还没允许本应用安装未知应用：已打开系统设置页，授权后再回来点一次「立即更新」")
    }
    val apk = AppUpdater.download(context, release) { url, dest -> AppUpdater.httpDownload(url, dest, onProgress) }
    context.startActivity(AppUpdater.installIntent(context, apk))
}