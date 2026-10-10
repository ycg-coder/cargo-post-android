package com.shanxiang.buspost.qs

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.shanxiang.buspost.MainActivity
import com.shanxiang.buspost.data.store.Prefs
import com.shanxiang.buspost.notify.NotifyWorker

/**
 * Android 快速设置磁贴（API 24+）—— Android 专属能力，小程序无法实现。
 * 从通知栏下拉即可一键开关到车提醒，无需打开应用。
 * 点击磁贴切换状态，长按打开应用设置页。
 */
@RequiresApi(Build.VERSION_CODES.N)
class ArrivalAlertTileService : TileService() {

    companion object {
        private const val TAG = "ArrivalAlertTile"
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        // 切换开关状态
        val newState = !Prefs.arrivalAlertEnabled
        Prefs.arrivalAlertEnabled = newState

        // 如果开启，立即触发一次检查
        if (newState) {
            NotifyWorker.runNow(applicationContext)
        }

        // 更新磁贴 UI
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val enabled = Prefs.arrivalAlertEnabled

        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(com.shanxiang.buspost.R.string.app_name)
        tile.contentDescription = if (enabled) "到车提醒已开启" else "到车提醒已关闭"

        tile.updateTile()
    }
}
