package com.shanxiang.buspost.ui.driver

import com.shanxiang.buspost.core.ui.BusDialogAction
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanxiang.buspost.core.ui.BusButton
import com.shanxiang.buspost.core.ui.LocalBusFontScale
import com.shanxiang.buspost.core.ui.LocalBusTokens

/**
 * 手写签名板（#20 阶段 B）。
 *
 * 采集到的是**折点**（[InkPoint]），"这算不算签了"由 [HandoverSignature] 判（纯层、JVM 有用例）；
 * 这一侧只做两件事：把折点画出来、按固定尺寸导出 PNG。
 * 导出尺寸与设备像素密度无关（画完按实测画布尺寸缩放到固定像素），因为签名要进库存证，
 * 同一次签在 440dpi 与 320dpi 上得是同一张图，不能跟着 dp/sp 漂。
 */
@Composable
fun SignaturePadDialog(
    onCancel: () -> Unit,
    onSigned: (ByteArray) -> Unit,
) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val done = remember { mutableStateListOf<List<InkPoint>>() }
    val live = remember { mutableStateListOf<InkPoint>() }
    var canvasW by remember { mutableIntStateOf(0) }
    var canvasH by remember { mutableIntStateOf(0) }
    var inked by remember { mutableIntStateOf(0) }
    var problem by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("签收人手写签名") },
        text = {
            Column {
                Text(
                    "在下方空白处签下姓名或画押。至少两划才算签成（这条判据在纯层，用例逐条钉着）。",
                    fontSize = 12.sp * scale,
                    color = t.textSecondary,
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .background(Color.White)
                        .pointerInput(Unit) {
                            canvasW = size.width
                            canvasH = size.height
                            detectDragGestures(
                                onDragStart = { p ->
                                    live.clear()
                                    live.add(InkPoint(p.x, p.y))
                                },
                                onDrag = { change, drag ->
                                    change.consume()
                                    val last = live.lastOrNull() ?: return@detectDragGestures
                                    live.add(InkPoint(last.x + drag.x, last.y + drag.y))
                                },
                                onDragEnd = {
                                    if (live.isNotEmpty()) {
                                        done.add(live.toList())
                                        live.clear()
                                        inked = HandoverSignature.strokeCount(done.toList())
                                    }
                                },
                                onDragCancel = { live.clear() },
                            )
                        },
                ) {
                    Canvas(Modifier.fillMaxWidth().height(160.dp)) {
                        (done + listOf(live)).forEach { stroke -> stroke.drawOn(this) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        inked == 0 -> "还没有落笔"
                        !HandoverSignature.hasInk(done.toList()) -> "已画 $inked 划：还差一笔才算签成"
                        else -> "已画 $inked 划，可以确认了"
                    },
                    fontSize = 11.sp * scale,
                    color = t.textTertiary,
                )
                problem?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, fontSize = 11.sp * scale, color = t.danger)
                }
            }
        },
        confirmButton = {
            BusButton(
                text = "确认签名",
                onClick = {
                    val shot = done.toList()
                    val w = canvasW
                    val h = canvasH
                    problem = when {
                        !HandoverSignature.hasInk(shot) ->
                            "笔数不够：至少要签下两划才算签名"
                        w <= 0 || h <= 0 ->
                            "签名板还没量到画布尺寸，这次导不出来；交接不会提交，关掉重开一次"
                        else -> {
                            val png = renderSignaturePng(w, h, shot)
                            if (png == null) {
                                "这台机器上没能把签名导出成图片，交接不会提交"
                            } else {
                                onSigned(png)
                                null
                            }
                        }
                    }
                },
            )
        },
        dismissButton = {
            Row {
                BusDialogAction(
                    text = "清除重签",
                    onClick = {
                        done.clear()
                        live.clear()
                        inked = 0
                        problem = null
                    },
                )
                BusDialogAction(text = "不签了", onClick = onCancel)
            }
        },
    )
}

/** 折点连成折线画到 Compose 画布上（单点的毛刺不画，与 [HandoverSignature] 的门槛同向） */
private fun List<InkPoint>.drawOn(scope: DrawScope) {
    if (size < 2) return
    val path = Path().apply {
        moveTo(first().x, first().y)
        this@drawOn.drop(1).forEach { lineTo(it.x, it.y) }
    }
    scope.drawPath(
        path,
        Color.Black,
        style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

/**
 * 画到固定尺寸的位图再压 PNG。返回 null = 这台机器上没导出来，调用方必须因此**不提交**，
 * 不许退化成"当成没签名继续提交"（那样等于把这笔交接记成"这人确实没签"）。
 */
private fun renderSignaturePng(viewWidth: Int, viewHeight: Int, strokes: List<List<InkPoint>>): ByteArray? = try {
    val bmp = androidx.core.graphics.createBitmap(
        HandoverSignature.EXPORT_WIDTH_PX, HandoverSignature.EXPORT_HEIGHT_PX, android.graphics.Bitmap.Config.ARGB_8888,
    )
    try {
        val canvas = android.graphics.Canvas(bmp)
        canvas.drawColor(android.graphics.Color.WHITE)
        canvas.scale(
            HandoverSignature.EXPORT_WIDTH_PX.toFloat() / viewWidth.toFloat(),
            HandoverSignature.EXPORT_HEIGHT_PX.toFloat() / viewHeight.toFloat(),
        )
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.BLACK
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 4f
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
        }
        strokes.forEach { s ->
            if (s.size < 2) return@forEach
            val path = android.graphics.Path()
            path.moveTo(s.first().x, s.first().y)
            s.drop(1).forEach { path.lineTo(it.x, it.y) }
            canvas.drawPath(path, paint)
        }
        val out = java.io.ByteArrayOutputStream()
        if (bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)) out.toByteArray() else null
    } finally {
        bmp.recycle()
    }
} catch (e: OutOfMemoryError) {
    null
}
