package com.shanxiang.buspost.core.code

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

/**
 * 扫码（装车 / 妥投 / 取件核销）—— 对应小程序 `wx.scanCode({ scanType: ['qrCode','barCode'] })`。
 *
 * 与小程序同口径：扫码失败或用户取消都不抛异常，返回 null 由页面走「手输单号兜底」，
 * 保证"相机不可用 / 码磨损"时作业流程不被堵死。
 */
class BusScanner(private val onResult: (String?) -> Unit) {

    /** 由 Compose 侧用 rememberBusScanner 创建 */
    internal var launch: ((String) -> Unit)? = null

    fun scan(prompt: String = "扫描订单二维码 / 条码") {
        launch?.invoke(prompt)
    }
}

@Composable
fun rememberBusScanner(onResult: (String?) -> Unit): BusScanner {
    val scanner = remember { BusScanner(onResult) }
    val launcher = rememberLauncherForActivityResult(ScanContract()) { result ->
        onResult(result.contents?.takeIf { it.isNotBlank() })
    }
    scanner.launch = { prompt ->
        val options = ScanOptions()
            .setDesiredBarcodeFormats(ScanOptions.QR_CODE, ScanOptions.CODE_128, ScanOptions.CODE_39)
            .setPrompt(prompt)
            .setBeepEnabled(true)
            .setOrientationLocked(false)
        runCatching { launcher.launch(options) }.onFailure { onResult(null) }
    }
    return scanner
}

/** 相机权限被拒时的兜底提示文案（与小程序手输兜底同语义） */
fun scanFallbackHint(context: Context): String =
    "相机不可用时，可直接输入单号完成核验"
