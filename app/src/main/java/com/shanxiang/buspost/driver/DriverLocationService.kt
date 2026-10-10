package com.shanxiang.buspost.driver

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.shanxiang.buspost.core.loc.Locator
import com.shanxiang.buspost.core.loc.TrackFix
import com.shanxiang.buspost.data.model.Geo
import com.shanxiang.buspost.data.model.GeoMath
import com.shanxiang.buspost.data.repo.Repo
import com.shanxiang.buspost.data.store.Prefs
import com.shanxiang.buspost.notify.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 司机位置上报前台服务：行车中按 `Prefs.reportIntervalSec` 向 /driver/location 上报一次，
 * 村民端"车快到了"和地图车辆移动都依赖这条链路 —— 小程序受后台限制做不到。
 *
 * 三条纪律（P1-3 ⑦）：
 * 1. **只发最新的点**（[LocationReportPolicy]）：失败期间的旧点被新点顶替并计入 `Running.dropped`，
 *    绝不排队重放 —— 后端没有客户端时间字段，补发旧点等于在轨迹上写一个时间错误的"当前位置"；
 * 2. **定位源断了就停报**：点龄超过 45 秒不再上报，界面与前台通知写明原因，
 *    而不是让服务"活着"、后端"最新位置"却停留在半小时前；
 * 3. **坐标系只有一个出口**：所有 `Geo` 都来自 `Locator.trackFixes`（已换算 GCJ-02），
 *    这里不允许再出现 `Geo(location.latitude, location.longitude)`。
 */
class DriverLocationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val ledger = ReportLedger()

    /** 当前最新的采样；只在 `observe` 里被更新，上报侧只读 */
    @Volatile
    private var latest: TrackFix? = null

    /** 上一次写进前台通知的正文，相同就不重复 post */
    private var notifiedText: String = ""

    /** 静止检测：上一次上报的坐标，用于判断车辆是否移动 */
    @Volatile
    private var lastReportedGeo: Geo? = null

    /** 静止检测阈值：移动距离小于此值（米）且在静止窗口内，跳过上报 */
    private companion object {
        const val STATIONARY_THRESHOLD_M = 20.0
        const val STATIONARY_WINDOW_MS = 60_000L
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notif = Notifier.ongoing(this, ID, "乡邮智联 · 位置上报中", "正在为乘车安排与到车提醒上传车辆位置")
        runCatching {
            ServiceCompat.startForeground(
                this,
                ID,
                notif,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
            )
        }.onFailure {
            // Android 14 对后台启动前台服务有更严限制，失败时不崩溃，界面会提示手动开启
            Running.ok = false
            stopSelf()
            return START_NOT_STICKY
        }
        Running.ok = true
        Running.startedAt = System.currentTimeMillis()
        // 进程被系统回收后由 START_STICKY 重建，此时内存里的 shiftId 已丢：从本地恢复在途班次，
        // 否则服务会"活着但不传"，司机以为车的位置一直在同步。
        if (Running.shiftId <= 0L) Running.shiftId = Prefs.driverShiftId
        observe()
        heartbeat()
        return START_STICKY
    }

    /**
     * 采样循环。`Locator.trackFixes` 正常返回只意味着"定位源被关掉或注册失败"
     * （权限中途被撤、用户关了定位开关），此时旧代码的 `collect` 会静默结束，
     * 服务继续按节拍上报最后那个点 —— 现在改成写明原因并重试注册。
     */
    private fun observe() {
        scope.launch {
            while (isActive) {
                if (!Locator.allowed(this@DriverLocationService)) {
                    Running.locator.value = "定位权限未授予或已被撤销"
                    delay(RESTART_DELAY_MS)
                    continue
                }
                Locator.trackFixes(this@DriverLocationService, minTimeMs = SAMPLE_INTERVAL_MS).collect { fix ->
                    Running.locator.value = ""
                    latest = fix
                    ledger.onFix(fix.elapsedRealtimeMs)
                    Running.dropped.value = ledger.superseded
                    Running.geo.value = fix.geo
                    Running.speed.value = fix.speedKmh
                    Running.fixAtElapsedMs.value = fix.elapsedRealtimeMs
                    Running.accuracyMeters.value = fix.accuracyMeters
                    Running.provider.value = fix.provider
                    // 朝向只服务于地图上的"我"这一格：后端 `AppDriverLocationReqVO` 没有这个键，
                    // 上报报文（下面 `send()`）一个字都不读它 —— 视觉层不参与业务链路。
                    Running.headingDegrees.value = fix.headingDegrees
                }
                Running.locator.value = "定位监听已退出（系统定位被关闭或注册失败），正在重试"
                delay(RESTART_DELAY_MS)
            }
        }
    }

    private fun heartbeat() {
        scope.launch {
            while (isActive) {
                val waited = tick()
                delay(waited)
            }
        }
    }

    /**
     * 一拍：先问策略"该发还是该停"，再执行。返回"下次间隔（毫秒）"。
     * 失败不再被静默吞掉：连续失败按 2 倍退避（上限 [LocationReportPolicy.MAX_BACKOFF_MS]），
     * 前台通知与工作台都能看出"最后一次成功是多久以前"与"停报的原因"。
     */
    private suspend fun tick(): Long {
        val baseSec = Prefs.reportIntervalSec
        val wait = LocationReportPolicy.intervalMs(baseSec, Running.failStreak)
        val step = LocationReportPolicy.decide(
            latest = latest,
            nowMs = Locator.elapsed(),
            driverBound = Prefs.driverId > 0L,
            shiftId = Running.shiftId,
        )
        return when (step) {
            is LocationReportPolicy.Step.Hold -> {
                // 定位源本身掉了就报它的状态，比"点过期"更接近司机能处理的事实
                Running.waiting.value = listOf(Running.locator.value, step.reason)
                    .filter { it.isNotEmpty() }
                    .joinToString(" · ")
                notifyWaiting()
                wait
            }
            is LocationReportPolicy.Step.Send -> send(step, baseSec)
        }
    }

    private suspend fun send(step: LocationReportPolicy.Step.Send, baseSec: Int): Long {
        val fix = step.fix
        val lastGeo = lastReportedGeo
        if (lastGeo != null) {
            val distanceM = GeoMath.distance(lastGeo, fix.geo)
            if (distanceM < STATIONARY_THRESHOLD_M) {
                Running.waiting.value = "车辆静止中（移动 ${distanceM.toInt()}m < ${STATIONARY_THRESHOLD_M.toInt()}m）"
                notifyWaiting()
                return LocationReportPolicy.intervalMs(baseSec, Running.failStreak)
            }
        }
        Running.shiftNote.value = LocationReportPolicy.shiftNote(step.shiftId ?: 0L)
        return try {
            Repo.reportLocation(step.shiftId, fix.geo, fix.speedKmh.toDouble())
            lastReportedGeo = fix.geo
            Running.failStreak = 0
            Running.reported.value += 1
            Running.lastSuccessAt = System.currentTimeMillis()
            Running.lastError.value = ""
            Running.waiting.value = ""
            ledger.onAck()
            Running.dropped.value = ledger.superseded
            notify("${Prefs.vehiclePlate.ifEmpty { "车辆" }} ${fix.speedKmh} km/h · 已上报 ${Running.reported.value} 次" +
                droppedSuffix())
            LocationReportPolicy.intervalMs(baseSec, 0)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e !is com.shanxiang.buspost.core.net.NetworkException &&
                e !is com.shanxiang.buspost.core.net.ApiException
            ) {
                // 程序缺陷不进退避重试：对着同一个 bug 退避，只会把"这里有缺陷"洗成"网络在抖"，
                // 并让服务无限循环显示"上报失败 N 次"（司机以为重试就好）。诚实做法是把缺陷报出去。
                Running.lastError.value = "客户端缺陷：${e.javaClass.simpleName}"
                throw e
            }
            // 这个点没被确认：它会被下一个新点顶替（合并语义），不排队重放
            Running.failStreak += 1
            Running.lastError.value = e.message ?: "位置上报失败"
            val backoff = LocationReportPolicy.intervalMs(baseSec, Running.failStreak)
            notify(
                "${Prefs.vehiclePlate.ifEmpty { "车辆" }} ${fix.speedKmh} km/h · " +
                    "上报失败 ${Running.failStreak} 次，${backoff / 1000} 秒后重试" + droppedSuffix(),
            )
            backoff
        }
    }

    /** 丢弃点数进通知：弱网一段时间后，司机要能看见"少了多少个点"，而不是以为一直在传 */
    private fun droppedSuffix(): String =
        if (ledger.superseded <= 0) "" else " · 已丢弃 ${ledger.superseded} 个未确认点"

    private fun notifyWaiting() {
        val reason = Running.waiting.value.ifEmpty { "等待定位信号" }
        notify("${Prefs.vehiclePlate.ifEmpty { "车辆" }} · $reason")
    }

    private fun notify(text: String) {
        if (text == notifiedText) return
        notifiedText = text
        runCatching {
            ServiceCompat.startForeground(
                this@DriverLocationService,
                ID,
                Notifier.ongoing(this@DriverLocationService, ID, "乡邮智联 · 位置上报中", text),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
            )
        }.onFailure { notifiedText = "" }
    }

    override fun onDestroy() {
        Running.reset()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val ID = 4101

        /** 采样周期：与上报周期解耦，上报慢的时候点龄仍然小，策略才能分辨"信号断了"和"在按时传" */
        private const val SAMPLE_INTERVAL_MS = 5_000L

        /** 定位流退出后重新注册的间隔 */
        private const val RESTART_DELAY_MS = 5_000L

        fun start(context: Context) {
            if (!Locator.allowed(context)) return
            val intent = Intent(context, DriverLocationService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ContextCompat.startForegroundService(context, intent)
                else context.startService(intent)
            }
        }

        fun stop(context: Context) = context.stopService(Intent(context, DriverLocationService::class.java))
    }
}

/** 服务状态对外可见，工作台界面据此显示"上报中/未开启" */
object Running {
    @Volatile var ok: Boolean = false
    var startedAt: Long = 0L

    /** 当前执行中的班次 ID：位置上报携带它才会写班次轨迹；<=0 表示本机没有班次（后端可兜底） */
    @Volatile var shiftId: Long = 0L

    /** 连续失败次数：驱动退避间隔，成功后归零 */
    @Volatile var failStreak: Int = 0

    /** 最后一次**上报成功**的时刻（墙钟），工作台据此显示"多久没传了" */
    @Volatile var lastSuccessAt: Long = 0L

    val geo = MutableStateFlow<Geo?>(null)
    val speed = MutableStateFlow(0)
    val reported = MutableStateFlow(0)
    val waiting = MutableStateFlow("")
    val lastError = MutableStateFlow("")

    /**
     * 最新采样的采集时刻（`SystemClock.elapsedRealtime` 口径）。
     * 与 [com.shanxiang.buspost.core.loc.Locator.elapsed] 同轴，界面用它算"这个点多新"。
     */
    val fixAtElapsedMs = MutableStateFlow(0L)

    /** 最新点的精度半径（米）；0 = 定位源没给 */
    val accuracyMeters = MutableStateFlow(0.0)

    /** 定位源标识（gps / network），空 = 还没有点 */
    val provider = MutableStateFlow("")

    /** 本机 GPS 朝向（度），null = 定位源没给 bearing。**不参与上报**，只给视觉层读 */
    val headingDegrees = MutableStateFlow<Double?>(null)

    /** 被更新的点顶替、且从未被后端确认的点数（合并语义下的"丢点"，必须可见） */
    val dropped = MutableStateFlow(0)

    /** 定位源状态说明，空 = 正常在采；非空时上报会停 */
    val locator = MutableStateFlow("")

    /** 未带班次上报时的说明，空 = 有班次 */
    val shiftNote = MutableStateFlow("")

    fun reset() {
        ok = false
        shiftId = 0L
        failStreak = 0
        lastSuccessAt = 0L
        reported.value = 0
        geo.value = null
        lastError.value = ""
        fixAtElapsedMs.value = 0L
        accuracyMeters.value = 0.0
        provider.value = ""
        headingDegrees.value = null
        dropped.value = 0
        locator.value = ""
        shiftNote.value = ""
    }
}
