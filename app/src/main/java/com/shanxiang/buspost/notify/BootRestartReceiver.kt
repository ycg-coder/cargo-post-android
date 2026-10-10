package com.shanxiang.buspost.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 开机 / 覆盖安装后重新排上消息轮询：系统会清掉未完成的周期任务 */
class BootRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            NotifyWorker.schedule(context.applicationContext)
        }
    }
}
