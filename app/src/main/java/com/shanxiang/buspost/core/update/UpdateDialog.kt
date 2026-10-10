package com.shanxiang.buspost.core.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.shanxiang.buspost.core.ui.BusButton
import com.shanxiang.buspost.core.ui.BusCard
import com.shanxiang.buspost.core.ui.BusRadius
import kotlinx.coroutines.launch

/**
 * 版本更新弹窗：显示新版本信息 + 下载进度 + 安装。
 */
@Composable
fun UpdateDialog(
    release: AppUpdater.ReleaseInfo,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var downloadDone by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = { if (!downloading) onDismiss() }) {
        BusCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("发现新版本", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${release.versionName}  (v${release.versionCode})",
                    fontSize = 13.sp,
                    color = Color(0xFF6B7280),
                )
                Spacer(Modifier.height(12.dp))

                Text("更新内容", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text(
                    release.changelog.take(500),
                    fontSize = 13.sp,
                    color = Color(0xFF374151),
                    lineHeight = 18.sp,
                )
                Spacer(Modifier.height(16.dp))

                when {
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
                                .background(Color(0xFFE5E7EB)),
                        ) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth().height(36.dp),
                                color = Color(0xFF3B82F6),
                                trackColor = Color.Transparent,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "下载中 ${(progress * 100).toInt()}%",
                            fontSize = 12.sp,
                            color = Color(0xFF6B7280),
                        )
                    }
                    else -> {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            TextButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("稍后再说", color = Color(0xFF6B7280))
                            }
                            Spacer(Modifier.width(8.dp))
                            BusButton(
                                text = "立即更新",
                                onClick = {
                                    downloading = true
                                    scope.launch {
                                        try {
                                            val apk = AppUpdater.downloadApk(
                                                context, release.downloadUrl
                                            ) { p -> progress = p }
                                            downloadDone = true
                                            AppUpdater.installApk(context, apk)
                                        } catch (_: Exception) {
                                            downloading = false
                                        }
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
