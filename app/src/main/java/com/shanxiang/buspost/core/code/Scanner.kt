package com.shanxiang.buspost.core.code

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

/**
 * 扫码（装车 / 妥投 / 取件核销）—— 对应小程序 `wx.scanCode({ scanType: ['qrCode','barCode'] })`。
 *
 * 三种结局要分开说（缺陷 #55/#54 那一族：把两件事说成一件）：
 * ① 扫到了 → `onResult(内容)`；
 * ② 用户自己按了返回/取消 → `onResult(null)`，页面提示可以怎么继续；
 * ③ **这台机器打不开扫码器**（无相机、扫码 activity 不存在）→ `onUnavailable()`。
 * 旧写法把 ③ 也塞进 `onResult(null)`，于是"点了没反应"被念成"你取消了"，
 * 司机再点一次还是没反应 —— 而 ③ 的正确出口是换一条提交路径，不是再点一次。
 */
class BusScanner(private val onResult: (String?) -> Unit) {

    /** 由 Compose 侧用 rememberBusScanner 创建 */
    internal var launch: ((String) -> Unit)? = null

    fun scan(prompt: String = "扫描订单二维码 / 条码") {
        launch?.invoke(prompt)
    }
}

@Composable
fun rememberBusScanner(onResult: (String?) -> Unit, onUnavailable: () -> Unit = {}): BusScanner {
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
        runCatching { launcher.launch(options) }.onFailure { onUnavailable() }
    }
    return scanner
}
