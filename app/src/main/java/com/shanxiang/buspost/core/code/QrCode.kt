package com.shanxiang.buspost.core.code

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * 订单二维码生成 —— 对应小程序 utils/qrcode-render.js。
 *
 * 参数与小程序逐项对齐：
 *  · 纠错等级 M（小程序 `qrcode(0, 'M')`，typeNumber 0 = 自动定版）
 *  · 白底 + 深色模块，quiet zone 4 模块（QR 标准要求）
 *  · 深色模块默认用主题墨色 #2B2B28（小程序 `darkColor || '#2B2B28'`），与全站观感一致
 *  · 编码内容就是订单号 / 取件码原文（司机扫码装车、妥投、取件核销都读这一串）
 */
object QrCode {

    /** 全站墨色，与 app.wxss --color-ink 同值 */
    private val INK = Color.rgb(0x2B, 0x2B, 0x28)

    /**
     * 生成二维码位图。
     * @param text 编码内容（订单号 / 取件码）
     * @param sizePx 输出边长（正方形，建议 ≥ 256，小尺寸会扫不出来）
     * @param darkColor 深色模块颜色，默认主题墨色
     */
    fun bitmap(text: String, sizePx: Int = 512, darkColor: Int = INK): Bitmap? {
        if (text.isBlank()) return null
        val size = sizePx.coerceIn(96, 2048)
        return runCatching {
            val hints = mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 4,
            )
            val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
            val pixels = IntArray(size * size)
            val white = Color.WHITE
            for (y in 0 until size) {
                val row = y * size
                for (x in 0 until size) {
                    pixels[row + x] = if (matrix[x, y]) darkColor else white
                }
            }
            Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, size, 0, 0, size, size)
            }
        }.getOrNull()
    }
}
