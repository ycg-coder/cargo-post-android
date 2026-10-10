package com.shanxiang.buspost.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.shanxiang.buspost.MainActivity
import com.shanxiang.buspost.R
import com.shanxiang.buspost.data.model.BusVehicle
import com.shanxiang.buspost.data.model.byUserDistance
import com.shanxiang.buspost.data.repo.Repo
import com.shanxiang.buspost.nav.Routes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 下一班到车桌面小组件 —— Android 专属能力，小程序无法实现。
 * 2x1 单元格大小，显示最近一班车的线路名、ETA、下一站信息。
 * 点击 widget 打开应用直达实时公交页，点击刷新按钮手动更新数据。
 */
class NextBusWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH = "com.shanxiang.buspost.WIDGET_REFRESH"
        private var lastVehicles: List<BusVehicle> = emptyList()
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            // 手动刷新：重新加载数据并更新所有 widget 实例
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val nearby = Repo.nearbyBuses(null, Repo.nearbyRadius(0.0))
                    lastVehicles = nearby.value?.vehicles.orEmpty()
                        .byUserDistance()
                        .take(1)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                // 更新所有 widget
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(
                    android.content.ComponentName(context, NextBusWidgetProvider::class.java)
                )
                for (appWidgetId in appWidgetIds) {
                    updateAppWidget(context, appWidgetManager, appWidgetId)
                }
            }
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_next_bus)

        // 点击整个 widget 打开应用
        val intent = Intent(context, MainActivity::class.java).apply {
            // 旧写法是 putExtra("route", "bus")：MainActivity 只读 intent.dataString，那个 extra 永远没人取，
            // 于是类注释里"直达实时公交页"是句假话（实际落在冷启动默认页）。改成真深链，同一条分域闸也顺带走了。
            data = Uri.parse(Routes.deepLink(Routes.BUS))
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

        // 点击刷新按钮
        val refreshIntent = Intent(context, NextBusWidgetProvider::class.java).apply {
            action = ACTION_REFRESH
        }
        val refreshPendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widget_refresh, refreshPendingIntent)

        // 填充数据
        val vehicle = lastVehicles.firstOrNull()
        if (vehicle != null) {
            views.setTextViewText(R.id.widget_line_name, vehicle.lineName.ifEmpty { "班车" })
            // 后端没给 ETA 时大数字是"—"、单位置空：旧写法 print null→0 的 etaMinutes，会显示"0 分钟到站"
            views.setTextViewText(R.id.widget_eta, vehicle.etaValueText)
            views.setTextViewText(R.id.widget_eta_unit, vehicle.etaUnitText)
            views.setTextViewText(
                R.id.widget_next_station,
                vehicle.etaCaptionText,
            )
            views.setViewVisibility(R.id.widget_content, android.view.View.VISIBLE)
            views.setViewVisibility(R.id.widget_empty, android.view.View.GONE)
        } else {
            views.setViewVisibility(R.id.widget_content, android.view.View.GONE)
            views.setViewVisibility(R.id.widget_empty, android.view.View.VISIBLE)
        }

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
