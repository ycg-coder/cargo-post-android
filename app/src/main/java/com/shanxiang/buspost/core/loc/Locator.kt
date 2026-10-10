package com.shanxiang.buspost.core.loc

import android.Manifest
import android.annotation.SuppressLint // 见下面两处 MissingPermission 的理由：闸门在 allowed() 里，lint 穿不过这个函数
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.shanxiang.buspost.BuildConfig
import com.shanxiang.buspost.core.map.CoordinateSystem
import com.shanxiang.buspost.core.map.CoordinateTransform
import com.shanxiang.buspost.core.net.attempt
import com.shanxiang.buspost.data.model.Geo
import com.shanxiang.buspost.data.model.GeoMath
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 一次定位的完整结果。
 * 页面需要它来区分四种定位状态（对齐小程序 index.js:24-27）：
 *   denied 权限被拒 → 引导「去设置」；
 *   geo 为空且未拒绝 → 「定位不可用」；
 *   accuracyMeters > 100 → 「精度较低，点此重新定位」；> 500 加「区域名可能不准」；
 *   用户手动选点 → 「已手动选择位置，点此可修改」。
 */
data class LocFix(
    val geo: Geo?,
    /** 精度半径（米）；<=0 表示定位源未给出精度 */
    val accuracyMeters: Double = 0.0,
    val denied: Boolean = false,
    /** true 表示命中缓存（不是刚采到的 fix） */
    val fromCache: Boolean = false,
) {
    val available: Boolean get() = geo != null
    /** 精度较低：误差大于 100m 时「附近公交」的结果会被偏差带偏 */
    val poorAccuracy: Boolean get() = available && accuracyMeters > 100.0
    /** 精度很差：误差大于 500m，区域名可能落到隔壁区 */
    val veryPoorAccuracy: Boolean get() = available && accuracyMeters > 500.0
    /** 精度文案（对齐小程序 locationAccuracyText） */
    val accuracyText: String
        get() = when {
            accuracyMeters <= 0.0 -> ""
            accuracyMeters >= 1000 -> "${(accuracyMeters / 1000).toInt()}km"
            else -> "${accuracyMeters.toInt()}m"
        }

    companion object {
        val DENIED = LocFix(null, 0.0, denied = true)
        val UNAVAILABLE = LocFix(null, 0.0, denied = false)
    }
}

/**
 * 一次持续定位采样（司机上报链路用）。
 *
 * `elapsedRealtimeMs` 是**本机收到这条 fix 的单调时刻**（`SystemClock.elapsedRealtime` 口径），
 * 不是 `Location.time` —— 后者由定位源给出，弱信号或 GPS 周号翻转时能偏差几天，
 * 用它算新鲜度会得出"点很新"的假结论。上报侧只认前者。
 */
data class TrackFix(
    /** 已换算成 GCJ-02 的业务坐标，见 [Locator.toGcj02] */
    val geo: Geo,
    val speedKmh: Int,
    val accuracyMeters: Double,
    val elapsedRealtimeMs: Long,
    val provider: String,
    /** GPS 朝向，度；null = 定位源没给 bearing。不能退化成 0.0 —— 那样"未知"和"正北"就分不开（V21-15） */
    val headingDegrees: Double? = null,
) {
    /** 相对 nowMs 的年龄（毫秒）；负数按 0 处理（时钟回拨） */
    fun ageMs(nowMs: Long): Long = (nowMs - elapsedRealtimeMs).coerceAtLeast(0L)
}

/**
 * 定位统一走系统 LocationManager：不引 play-services（这台机器拉新依赖不稳），
 * 且乡下车载/手机 GPS 直接可用。司机前台上报与附近公交共用这一套。
 */
object Locator {

    private const val TAG_SHIFT = "BUS_LOC"
    private const val ACCURACY_FILTER_M = 200.0

    /**
     * 设备坐标 → 业务坐标的**唯一**换算入口（审计 §17）。
     *
     * `LocationManager` 按平台规范返回 WGS-84，而后端 VO、高德底图、小程序 `wx.getLocation({type:'gcj02'})`
     * 全是 GCJ-02；不换算就是 150~700 米的系统性偏移，且这条偏移会写进
     * `transport_vehicle_location` 与轨迹表，被服务端的走廊匹配和偏航判定当成事实。
     * 境外点由 [CoordinateTransform.inChina] 判定后原样返回，不会二次伤害。
     *
     * 业务代码不得再手写偏移公式，也不得直接 `Geo(location.latitude, location.longitude)`。
     */
    fun toGcj02(wgs84: Geo): Geo {
        val gcj = CoordinateTransform.convert(wgs84, CoordinateSystem.WGS84, CoordinateSystem.GCJ02)
        if (BuildConfig.DEBUG && gcj != wgs84) {
            // 这里的 GeoMath.distance 量的是**两个坐标系之间的偏移量**，只用于真机核对，不是道路里程、不是远近展示。
            // 境内正常应在几十到几百米：接近 0 说明定位源已经给了 GCJ-02（部分国产 ROM 的 network 源如此，
            // 属 §17 的已知不确定性）；上千米说明坐标本身有问题。
            Log.d(TAG_SHIFT, "WGS84→GCJ02 偏移 ${GeoMath.distance(wgs84, gcj).toInt()}m @ ${wgs84.lat},${wgs84.lng}")
        }
        return gcj
    }

    private fun gcjOf(l: Location): Geo = toGcj02(Geo(l.latitude, l.longitude))

    /** `Location` → 业务采样，坐标系、速度与朝向在这一个地方定型 */
    fun fixOf(l: Location, elapsedRealtimeMs: Long): TrackFix =
        TrackFix(
            geo = gcjOf(l),
            speedKmh = speedKmh(l),
            accuracyMeters = accuracyOf(l),
            elapsedRealtimeMs = elapsedRealtimeMs,
            provider = l.provider ?: "",
            headingDegrees = heading(l),
        )

    fun allowed(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun mgr(context: Context): LocationManager? =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private fun usable(lm: LocationManager): List<String> =
        runCatching { lm.allProviders }.getOrDefault(emptySet())
            .filter { it == LocationManager.NETWORK_PROVIDER || it == LocationManager.GPS_PROVIDER }

    // 两个调用点（onceFix / latest）都在同一本文件里先过 `allowed(context)`，lint 的权限数据流穿不过那个
    // 函数 ⇒ 报 MissingPermission。这里压的是**检查器的视野**，不是运行时闸门：没授权照样走不到这一行。
    @SuppressLint("MissingPermission")
    private fun cachedLocation(lm: LocationManager, maxAgeMs: Long): Location? =
        usable(lm).mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
            .filter { it.time > 0 && System.currentTimeMillis() - it.time <= maxAgeMs }
            .maxByOrNull { it.time }

    /**
     * 持续定位流。注册必须发生在有 Looper 的线程上，所以统一 post 到主线程执行，
     * 协程取消时再移除监听（removeUpdates 线程安全）。
     *
     * 流**正常结束**只有一种含义：注册前置条件不满足（无 provider / 权限被撤），`close()` 了它。
     * 消费方必须把"流结束"当成"定位源掉了"来处理，不能假装还在采（司机上报侧的重启逻辑见
     * `DriverLocationService.observe`）。
     */
    // 函数体第一行就是 `if (lm == null || providers.isEmpty() || !allowed(context)) { close() … }`
    // ⇒ 没有授权时这条流直接结束，`requestLocationUpdates` 根本不会被调用；压的是 lint 穿不过 allowed() 这件事。
    @SuppressLint("MissingPermission")
    fun locations(context: Context, minTimeMs: Long = 3000L, minDistanceM: Float = 0f): Flow<Location> = callbackFlow {
        val lm = mgr(context)
        val providers = lm?.let { usable(it) }.orEmpty()
        if (lm == null || providers.isEmpty() || !allowed(context)) {
            close()
            return@callbackFlow
        }
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (accuracyOf(location) > 0 && accuracyOf(location) < ACCURACY_FILTER_M) {
                    trySend(location)
                } else if (accuracyOf(location) <= 0) {
                    trySend(location)
                }
            }

            @Deprecated("LocationManager 旧接口")
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }
        val main = Handler(Looper.getMainLooper())
        val registration = Runnable {
            providers.forEach { p ->
                runCatching { lm.requestLocationUpdates(p, minTimeMs, minDistanceM, listener, main.looper) }
                    .onFailure { runCatching { lm.requestLocationUpdates(p, minTimeMs, minDistanceM, listener) } }
            }
        }
        main.post(registration)
        awaitClose {
            main.post { runCatching { lm.removeUpdates(listener) } }
        }
    }

    /**
     * 司机上报用的采样流：[locations] + 一次性坐标系换算 + 本机单调时刻。
     * 换算只在这里发生，消费方拿到的 `geo` 一定是 GCJ-02。
     */
    fun trackFixes(context: Context, minTimeMs: Long = 5_000L): Flow<TrackFix> =
        locations(context, minTimeMs).map { fixOf(it, elapsed()) }

    /** 单调时钟（毫秒）：不受系统时间被定位源或用户改写影响 */
    fun elapsed(): Long = android.os.SystemClock.elapsedRealtime()

    /** 一次性定位：先用 60 秒内的缓存，拿不到再等一次真实 fix；超时返回 null，由页面回落到县城中心 */
    suspend fun once(context: Context, timeoutMs: Long = 9000L): Geo? = onceFix(context, timeoutMs).geo

    /**
     * 一次性定位（带精度与拒绝标志）。force=true 跳过缓存，用于用户点「重新定位」。
     * 页面据此渲染定位状态条，避免「拿不到定位」被误当成「附近没有公交」。
     */
    suspend fun onceFix(context: Context, timeoutMs: Long = 9000L, force: Boolean = false): LocFix {
        val lm = mgr(context) ?: return LocFix.UNAVAILABLE
        if (!allowed(context)) return LocFix.DENIED
        if (!force) {
            cachedLocation(lm, 60_000L)?.let {
                return LocFix(gcjOf(it), accuracyOf(it), denied = false, fromCache = true)
            }
        }
        // 流被 close（设备关了所有定位源）时 first() 会抛 NoSuchElementException：
        // 这不是"定位超时"，是一次崩溃。吞成 null，让下面的缓存回落与 UNAVAILABLE 接管。
        // attempt 而非 runCatching：runCatching 连 withTimeoutOrNull 用来判超时的 TimeoutCancellationException
        // 也一并接住，超时只剩"getOrNull 恰好给 null"这一条巧合路径；形状对了才不用赌巧合（指令 §七）。
        val loc = withTimeoutOrNull(timeoutMs) { attempt { locations(context).first() }.getOrNull() }
        if (loc != null) {
            return LocFix(gcjOf(loc), accuracyOf(loc), denied = false)
        }
        // 超时：退回 5 分钟内的旧缓存，并如实带上它的精度（可能很差，页面会提示"区域名可能不准"）
        val stale = cachedLocation(lm, 5 * 60_000L)
            ?: return LocFix.UNAVAILABLE
        return LocFix(gcjOf(stale), accuracyOf(stale), denied = false, fromCache = true)
    }

    private fun accuracyOf(l: Location): Double = if (l.hasAccuracy()) l.accuracy.toDouble() else 0.0

    fun speedKmh(location: Location): Int = if (location.hasSpeed()) (location.speed * 3.6f).toInt() else 0

    /**
     * 朝向：**没有 bearing 就返回 null，不返回 0.0**。
     *
     * 旧口径把"定位源没给"折成 0.0，而 0.0 在地图上就是正北 —— 室内网络定位几乎每次都"朝北"，
     * V2.1 核对表 V21-15 点名的正是这个不可区分。速度可以拿 0 当"没给"（静止是真的会 0），
     * 朝向不行：静止时 `hasBearing()` 仍可能为 false，此时任何旋转都是编出来的。
     */
    fun heading(location: Location): Double? = if (location.hasBearing()) location.bearing.toDouble() else null
}
