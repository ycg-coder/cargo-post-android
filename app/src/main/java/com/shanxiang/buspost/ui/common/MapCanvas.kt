package com.shanxiang.buspost.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanxiang.buspost.core.ui.BusButton
import com.shanxiang.buspost.core.ui.BusButtonVariant
import com.shanxiang.buspost.core.ui.BusIcons
import com.shanxiang.buspost.core.ui.BusRadius
import com.shanxiang.buspost.core.ui.LocalBusFontScale
import com.shanxiang.buspost.core.ui.LocalBusTokens
import com.shanxiang.buspost.data.model.BusVehicle
import com.shanxiang.buspost.data.model.Geo
import com.shanxiang.buspost.data.model.GeoMath
import com.shanxiang.buspost.data.model.Station

/**
 * 线路图：只画后端下发的真实道路 polyline，不接地图 SDK 也能保证"画出来的就是路"。
 *
 * 折线缺失 = 没有路线。这里绝不拿站点坐标连一条线顶替：站点两点之间的直线跟实际行驶道路
 * 可能差几公里，司机和村民都会把它当成路看。缺失时渲染 [RouteUnavailableState]，
 * 让上层给出原因和重试入口。
 */
@Composable
fun RouteMap(
    polyline: List<Geo>,
    stations: List<Station>,
    modifier: Modifier = Modifier,
    vehicles: List<BusVehicle> = emptyList(),
    me: Geo? = null,
    highlightStationId: Long = 0L,
    height: Dp = 210.dp,
    reason: String? = null,
    onReplan: (() -> Unit)? = null,
) {
    if (polyline.size < 2) {
        RouteUnavailableState(modifier, height, reason, onReplan)
        return
    }

    val t = LocalBusTokens.current
    val all = ArrayList<Geo>()
    all.addAll(polyline)
    stations.forEach { all.add(it.geo) }
    vehicles.forEach { it.geo?.let { g -> all.add(g) } }
    me?.let { all.add(it) }

    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .background(t.card, RoundedCornerShape(BusRadius.l)),
    ) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val pad = 30.dp.toPx()
            val bounds = GeoMath.bounds(all) ?: return@Canvas
            val (sw, ne) = bounds
            // 经纬度是 Double，画布坐标必须是 Float，否则 Offset 无公共构造函数
            val lngSpan = (ne.lng - sw.lng).toFloat().coerceAtLeast(0.0035f)
            val latSpan = (ne.lat - sw.lat).toFloat().coerceAtLeast(0.0035f)
            val scale = minOf((size.width - pad * 2) / lngSpan, (size.height - pad * 2) / latSpan)
            val usedW = lngSpan * scale
            val usedH = latSpan * scale
            val offX = (size.width - usedW) / 2f
            val offY = (size.height - usedH) / 2f
            fun project(g: Geo) = Offset(
                offX + (g.lng - sw.lng).toFloat() * scale,
                offY + (ne.lat - g.lat).toFloat() * scale,
            )

            // 底纹：等高线感的浅网格，避免大片纯色
            val grid = t.line.copy(alpha = if (t.isDark) 0.25f else 0.4f)
            var gx = pad
            while (gx < size.width - pad) {
                drawLine(grid, Offset(gx, pad / 2), Offset(gx, size.height - pad / 2), 1.dp.toPx())
                gx += 34.dp.toPx()
            }

            val path = Path().apply {
                polyline.forEachIndexed { i, g ->
                    val p = project(g)
                    if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                }
            }
            drawPath(path, t.lineDeep, style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round))
            drawPath(path, t.primary, style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round))
            drawPath(
                path,
                Color.White.copy(alpha = if (t.isDark) 0.35f else 0.75f),
                style = Stroke(
                    width = 1.4.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 16f), 0f),
                ),
            )

            val labelPaint = android.graphics.Paint().apply {
                isAntiAlias = true
                color = t.textSecondary.toArgb()
                textSize = 11.sp.toPx()
            }
            val platePaint = android.graphics.Paint().apply {
                isAntiAlias = true
                color = t.onPrimary.toArgb()
                textSize = 10.sp.toPx()
                textAlign = android.graphics.Paint.Align.CENTER
                isFakeBoldText = true
            }

            stations.forEachIndexed { i, s ->
                val p = project(s.geo)
                val hot = s.id == highlightStationId
                val r = if (hot) 7.5.dp.toPx() else 5.dp.toPx()
                if (hot) {
                    drawCircle(t.clay.copy(alpha = 0.22f), r + 6.dp.toPx(), p)
                }
                drawCircle(t.card, r + 1.8.dp.toPx(), p)
                drawCircle(if (hot) t.clay else t.primaryDark, r, p)
                val firstOrLast = i == 0 || i == stations.size - 1
                if (firstOrLast || hot) {
                    drawIntoCanvas { c ->
                        val y = if (i % 2 == 0) p.y - 12.dp.toPx() else p.y + 20.dp.toPx()
                        c.nativeCanvas.drawText(s.name, p.x, y, labelPaint)
                    }
                }
            }

            me?.let { g ->
                if (g.lat != 0.0) {
                    val p = project(g)
                    drawCircle(t.gold.copy(alpha = 0.25f), 13.dp.toPx(), p)
                    drawCircle(t.gold, 5.5.dp.toPx(), p)
                }
            }

            vehicles.forEach { v ->
                val vg = v.geo ?: return@forEach
                val p = project(vg)
                val w = 15.dp.toPx()
                val h = 11.dp.toPx()
                val body = if (v.isReal) t.primary else t.clay
                drawRoundRect(body, Offset(p.x - w, p.y - h / 2), Size(w * 2, h), CornerRadius(w / 3))
                drawRoundRect(
                    Color.White.copy(alpha = 0.85f),
                    Offset(p.x - w + 3.dp.toPx(), p.y - h / 2 + 2.4.dp.toPx()),
                    Size(w * 2 - 6.dp.toPx(), h - 4.8.dp.toPx()),
                    CornerRadius(2.dp.toPx()),
                )
                drawIntoCanvas { c ->
                    c.nativeCanvas.drawText(v.plate, p.x, p.y - h - 3.dp.toPx(), platePaint)
                }
            }
        }
    }
}

/**
 * 路线不可用状态：把"没有真实道路数据"如实呈现成一个空位，而不是画一条不存在的路。
 * 站点顺序由页面别处的时间线/时刻表承担，地图上不靠连线交代顺序。
 */
@Composable
fun RouteUnavailableState(
    modifier: Modifier = Modifier,
    height: Dp = 210.dp,
    reason: String? = null,
    onReplan: (() -> Unit)? = null,
) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(BusRadius.l))
            .background(t.track),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val inset = 7.dp.toPx()
            drawRoundRect(
                color = t.lineDeep.copy(alpha = 0.75f),
                topLeft = Offset(inset, inset),
                size = Size(size.width - inset * 2, size.height - inset * 2),
                cornerRadius = CornerRadius(BusRadius.m.toPx(), BusRadius.m.toPx()),
                style = Stroke(
                    width = 1.6.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(11f, 9f), 0f),
                ),
            )
        }
        Column(
            Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(BusIcons.route, contentDescription = null, tint = t.textTertiary, modifier = Modifier.size((24 * scale).dp))
            Text(
                "暂无真实路线",
                color = t.ink,
                fontSize = (14.5 * scale).sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                // F-3 ⑧：这一格是"调用方没给原因"时的兜底，所以它只说自己不知道，不替后端下结论
                reason ?: "现在没有可画的真实道路折线（具体原因未取得）。为避免显示与实际行驶无关的假路线，这里不做任何连线示意。",
                color = t.textSecondary,
                fontSize = (12 * scale).sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 5.dp),
            )
            if (onReplan != null) {
                BusButton(
                    text = "重新规划",
                    small = true,
                    variant = BusButtonVariant.OUTLINE,
                    onClick = onReplan,
                    modifier = Modifier.padding(top = 11.dp),
                )
            }
        }
    }
}
