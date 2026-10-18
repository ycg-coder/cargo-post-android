package com.shanxiang.buspost.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.util.LruCache
import com.amap.api.maps.model.BitmapDescriptor
import com.amap.api.maps.model.BitmapDescriptorFactory
import kotlin.math.roundToInt

/**
 * 任务点带字钉 / 站点圆环的位图（D-169 用户口径④「加强区别站点和任务点，还可以在任务点加上具体任务标记」）。
 *
 * 这一层只管**怎么画**：画不画字、画哪个字、这一格算站点还是任务点，全部在装配层
 * （`MissionMarkers.badgeOf` / `stationLikeOf`，纯函数、JVM 里逐条可断言）。
 * 这里只吃三样输入：`badge` + 色相 + `station`。
 *
 * 颜色**不走 `Color(0x…)` 字面量**：色相直接沿用 `BusMap` 里那个 `markerHue`（默认 marker 用的那一份），
 * 所以"失败是红的、完成是绿的、未开始是蓝的"这条规则仍然只有一处定义 ——
 * 换成位图 marker 不会跟默认 marker 分叉成两套配色（那正是 #31 在册的那类债）。
 *
 * 位图按 `形状|字|色相|密度` 缓存：`BusMap` 的 update 块每次标记集变了就整批重贴，
 * 不缓存等于司机每滑动一次屏幕重画十几个 44dp 位图。
 */
internal object MarkerBadgeDraw {

    /** 带字钉：圆心 17dp、钉尖落在坐标点上（锚点 [PIN_ANCHOR_Y]） */
    private const val PIN_W_DP = 44f
    private const val PIN_H_DP = 56f
    private const val PIN_R_DP = 17f
    private const val PIN_TAIL_HALF_DP = 7f
    private const val PIN_TAIL_Y_DP = 53f
    private const val PIN_ANCHOR_Y = PIN_TAIL_Y_DP / PIN_H_DP

    /** 站点圆环：白底 + 有色环 + 中心一点，整体比任务钉小一圈 */
    private const val RING_SIZE_DP = 30f
    private const val RING_STROKE_DP = 4.5f
    private const val RING_DOT_DP = 3.2f

    private const val GLYPH_DP = 20f

    private val typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    private val cache = LruCache<String, BitmapDescriptor>(48)

    /** 任务钉的锚点（钉尖）：位图里画的是"钉"，锚必须落在尖上，否则点位偏出去半枚钉 */
    const val TASK_ANCHOR_Y: Float = PIN_ANCHOR_Y

    /**
     * 一张 marker 图标。[badge] 为空时画的是**没有字的钉**（后端没给动作码或码不在表里），
     * 调用方决定要不要用它 —— 传空串不会自动退回默认 marker。
     */
    fun of(context: Context, badge: String, hue: Float, station: Boolean): BitmapDescriptor {
        val density = context.resources.displayMetrics.density
        val key = "${if (station) 's' else 't'}|$badge|${hue.roundToInt()}|${density.roundToInt()}"
        cache.get(key)?.let { return it }
        val drawn = if (station) ringBitmap(density, hue) else pinBitmap(density, hue, badge)
        return BitmapDescriptorFactory.fromBitmap(drawn).also { cache.put(key, it) }
    }

    /** 色相 → 实心色：饱和度与明度按"白字压得住"来定，不是照抄高德默认 marker 的那组值 */
    private fun fillColor(hue: Float): Int = Color.HSVToColor(floatArrayOf(hue, 0.82f, 0.88f))

    private fun pinBitmap(density: Float, hue: Float, badge: String): Bitmap {
        val w = (PIN_W_DP * density).roundToInt()
        val h = (PIN_H_DP * density).roundToInt()
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fillColor(hue) }
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * density
            color = Color.WHITE
        }
        val canvas = Canvas()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { canvas.setBitmap(it) }
        val cx = w / 2f
        val cy = PIN_R_DP * density
        val r = PIN_R_DP * density
        // 钉尖：从圆下沿两侧收到一点。先画尖再画圆，接缝被圆盖住，白边也就只描圆那一圈
        val tail = Path().apply {
            moveTo(cx - PIN_TAIL_HALF_DP * density, cy + r * 0.6f)
            lineTo(cx, PIN_TAIL_Y_DP * density)
            lineTo(cx + PIN_TAIL_HALF_DP * density, cy + r * 0.6f)
            close()
        }
        canvas.drawPath(tail, fill)
        canvas.drawCircle(cx, cy, r, fill)
        canvas.drawCircle(cx, cy, r - edge.strokeWidth / 2f, edge)
        if (badge.isNotEmpty()) {
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                typeface = this@MarkerBadgeDraw.typeface
                textSize = GLYPH_DP * density
                textAlign = Paint.Align.CENTER
            }
            val fm = text.fontMetrics
            canvas.drawText(badge, cx, cy - (fm.ascent + fm.descent) / 2f, text)
        }
        return bmp
    }

    private fun ringBitmap(density: Float, hue: Float): Bitmap {
        val size = (RING_SIZE_DP * density).roundToInt()
        val canvas = Canvas()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { canvas.setBitmap(it) }
        val c = size / 2f
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = RING_STROKE_DP * density
            color = fillColor(hue)
        }
        val base = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val outer = size / 2f - ring.strokeWidth / 2f
        // 白底先铺：圆环中间留空的话，深色底图与导航路线会从洞里穿过去，那一格就读成"没有点"
        canvas.drawCircle(c, c, outer - ring.strokeWidth / 2f, base)
        canvas.drawCircle(c, c, outer, ring)
        canvas.drawCircle(c, c, RING_DOT_DP * density, base)
        return bmp
    }
}
