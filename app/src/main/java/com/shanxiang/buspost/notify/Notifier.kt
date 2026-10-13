package com.shanxiang.buspost.notify

import android.annotation.SuppressLint // 见 push() 上那句理由
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.app.PendingIntent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import com.shanxiang.buspost.MainActivity
import com.shanxiang.buspost.R
import com.shanxiang.buspost.nav.Routes

/**
 * 通知渠道：小程序只能靠订阅消息一次性提醒，App 可以按业务分渠道，
 * 用户能在系统设置里单独关掉"班次任务"而保留"到车提醒"。
 */
object Channels {
    const val ORDER = "order"
    const val SHIFT = "shift"
    const val ARRIVAL = "arrival"
    const val SERVICE = "service"
    const val WEATHER = "weather"

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        listOf(
            channel(ORDER, "订单动态", "寄货、商城订单的状态变化", NotificationManager.IMPORTANCE_DEFAULT),
            channel(SHIFT, "班次与任务", "发车、到站、装车、交接任务", NotificationManager.IMPORTANCE_HIGH),
            channel(
                ARRIVAL, "到车提醒", "班车距离你的站点还有几分钟，提前出门",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 260, 160, 260)
            },
            channel(SERVICE, "服务状态", "司机位置上报等后台服务提示", NotificationManager.IMPORTANCE_LOW),
            channel(
                WEATHER, "天气预警", "暴雨、大雪、高温等恶劣天气提醒，影响出行与装车",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
            },
        ).forEach { mgr.createNotificationChannel(it) }
    }

    private fun channel(id: String, name: String, desc: String, importance: Int) =
        NotificationChannel(id, name, importance).apply {
            description = desc
        }

    /** 消息类型 → 渠道，未知类型归入订单动态 */
    fun forType(type: String): String = when (type.uppercase()) {
        "ARRIVAL", "NEAR" -> ARRIVAL
        "DEPART", "SHIFT", "HANDOVER", "LOAD" -> SHIFT
        else -> ORDER
    }
}

/**
 * 锁屏可见性口径（纯函数，JVM 用例可断言）。
 *
 * 返回非 null = 这条通知的正文含"你在等哪班车 / 寄到哪 / 几分钟到"这类**看一眼就能反推行程**的信息，
 * 锁屏必须换成这句不含内容的话（并配 publicVersion）；返回 null = 该渠道正文不含个人信息（如后台服务提示），
 * 保持系统默认可见，不多这一道遮掩。
 *
 * 为什么现在才收：`Notifier.push` 从来没设过 `setVisibility`/`setPublicVersion`，
 * 而通知正文由 `AlertEngine` 直接拼站名与分钟数（`AlertEngine.kt:69/90/118`）——
 * 锁屏未解锁就能看到"XX 村 3 分钟"，与任务 #36 记的"PII 无脱敏"同一族。
 */
internal fun lockScreenTextFor(channel: String): String? = when (channel) {
    // 白名单只放"正文确认不含个人信息"的那一条；其余（含以后新增、忘了登记的渠道）默认遮掩。
    // 反过来写会埋雷：加一条新渠道时忘了改这里，默认就是"锁屏摊开正文"。
    Channels.SERVICE -> null
    else -> "乡邮智联有新提醒，解锁查看详情"
}

object Notifier {

    // 没授权时那次 `notify()` 抛 SecurityException，下面那段捕获已经把它收成一条
    // `BUS_NOTIFY: 通知被系统拒绝` 的日志并且不打断业务 —— 这就是运行时处理，压的只是 lint 的视野。
    @SuppressLint("MissingPermission")
    fun push(
        context: Context,
        channel: String,
        title: String,
        body: String,
        notificationId: Int,
        route: String? = null,
        bigText: Boolean = false,
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            if (route != null) data = Uri.parse(Routes.deepLink(route))
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            context, notificationId, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(if (bigText) NotificationCompat.BigTextStyle().bigText(body) else null)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setWhen(System.currentTimeMillis())
            // 品牌色着色：Android 8+ 小图标是单色蒙版，不给 color 就只能灰白一片
            .setColor(ContextCompat.getColor(context, R.color.bus_primary))
        lockScreenTextFor(channel)?.let { pub ->
            builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(
                    NotificationCompat.Builder(context, channel)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle("乡邮智联")
                        .setContentText(pub)
                        .build(),
                )
        }
        if (channel == Channels.ARRIVAL) {
            builder.setPriority(NotificationCompat.PRIORITY_HIGH).setDefaults(NotificationCompat.DEFAULT_VIBRATE)
        }
        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        }.onFailure {
            // 33+ 缺少 POST_NOTIFICATIONS 运行时授权时这里会抛 SecurityException，静默跳过不打断业务
            android.util.Log.w("BUS_NOTIFY", "通知被系统拒绝: ${it.message}")
        }
    }

    fun ongoing(context: Context, id: Int, title: String, body: String): android.app.Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            // 壳路由**故意**投 driverShell：分域闸对壳路由一律返回 null（进哪个壳只由角色决定），
            // 于是这条点开的效果是"回到本角色自己的壳"。改成投某个具体页面就等于给前台服务开了一条免闸通道。
            data = Uri.parse(Routes.deepLink(Routes.DRIVER_SHELL))
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return NotificationCompat.Builder(context, Channels.SERVICE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context, id, intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .build()
    }
}
