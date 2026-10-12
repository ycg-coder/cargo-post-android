package com.shanxiang.buspost.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.LatLngBounds
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.PolylineOptions
import com.shanxiang.buspost.core.map.RealRoadRouteValidator
import com.shanxiang.buspost.core.map.RouteCheck
import com.shanxiang.buspost.data.model.Geo
import com.shanxiang.buspost.core.ui.LocalBusTokens

/** 地图上的一个标记 */
data class MapMarker(
    val geo: Geo,
    val title: String,
    /** KIND_* 决定配色：起 / 终 / 站 / 车（真实）/ 车（模拟）/ 我 / 任务点四态 */
    val kind: Int = KIND_STOP,
    /** 详情串（AMap 点marker 后信息窗第二行）；任务点用它承载"动作 · 件数 · 状态" */
    val detail: String = "",
    /**
     * 3D 视觉参数（朝向 / 强调层级 / 是否降级）。默认值就是"不强调、不旋转"，
     * 所以任何调用点忘记传它时，画出来的是普通 marker —— 而不是靠一个编造的朝向。
     */
    val visual: MarkerVisual = MarkerVisual(),
) {
    companion object {
        const val KIND_START = 0
        const val KIND_END = 1
        const val KIND_STOP = 2
        const val KIND_BUS_REAL = 3
        const val KIND_BUS_SIM = 4
        const val KIND_ME = 5
        const val KIND_TASK_TODO = 6
        const val KIND_TASK_ACTIVE = 7
        const val KIND_TASK_DONE = 8
        const val KIND_TASK_FAILED = 9
    }
}

/**
 * 一条要画在地图上的线。
 *
 * 只接受真实道路折线：绘制前会过 [RealRoadRouteValidator]，来源不可信（含空的 [source]）
 * 就整条不画。**没有"站点连线"这个角色** —— 把两个站连起来画在高德底图上，
 * 看上去就是一条路，而司机按它开会走错。
 */
data class MapPath(
    val points: List<Geo>,
    val colorRole: Int = ROLE_MAIN,
    /** 道路来源，取值见 core/map/RouteSource，例如 amap / osm_graphhopper */
    val source: String = "",
    val roadDistanceMeters: Int = 0,
) {
    companion object {
        const val ROLE_MAIN = 0
        const val ROLE_ACCENT = 1
        const val ROLE_MUTED = 2
    }

    /** 过不了校验就没有线可画 */
    fun validated(): List<Geo>? = when (
        val check = RealRoadRouteValidator.validate(
            sourceRaw = source,
            points = points,
            roadDistanceMeters = roadDistanceMeters,
        )
    ) {
        is RouteCheck.Accepted -> check.route.points
        is RouteCheck.Rejected -> null
    }
}

/**
 * 高德地图容器 —— 对应小程序原生 `<map>` 组件（真实底图 + 拖动缩放 + marker/polyline）。
 *
 * 失败兜底：key 类型不匹配 / 无网络时高德 SDK 会初始化失败，此时 [onUnavailable] 回调让页面
 * 退回自绘 RouteMap（MapCanvas.kt），保证"地图不可用"不会变成空白区域。
 */
@Composable
fun BusMap(
    markers: List<MapMarker>,
    paths: List<MapPath> = emptyList(),
    modifier: Modifier = Modifier,
    /** 是否把视口自适应到全部点（对应小程序 include-points） */
    fitAll: Boolean = true,
    zoom: Float = 13f,
    focus: Geo? = null,
    /**
     * 底图倾斜角（指令 ⑫"地图倾斜"）。默认 0 = 不倾斜也不开放倾斜手势：
     * 只有任务地图这类需要立体感的场景才显式传值，其他页面保持与小程序 `<map>` 一致。
     */
    tiltDegrees: Float = 0f,
    /**
     * 是否画"我的位置"蓝点（真机反馈"实时公交地图不显示自己位置"的那一格）。
     * 由调用方按**运行时权限**传值：高德的按钮开关与图层开关是两个独立设置，
     * 以前只开了 `isMyLocationButtonEnabled`（`BusMap.kt:157-166`），于是按钮在、点了没蓝点。
     */
    showMyLocation: Boolean = false,
    onUnavailable: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val t = LocalBusTokens.current
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var unavailable by remember { mutableStateOf(false) }

    // MapView 生命周期必须与宿主对齐，否则会漏内存 / 黑屏
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView?.onPause()
                Lifecycle.Event.ON_DESTROY -> mapView?.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView?.onDestroy()
        }
    }

    LaunchedEffect(unavailable) {
        if (unavailable) onUnavailable()
    }

    if (unavailable) return

    AndroidView<MapView>(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            val view = MapView(ctx)
            runCatching { view.onCreate(null) }.onFailure { unavailable = true }
            mapView = view
            view
        },
        update = { view ->
            val aMap = runCatching { view.map }.getOrNull()
            if (aMap == null) {
                unavailable = true
            } else runCatching {
                aMap.mapType = AMap.MAP_TYPE_NORMAL
                aMap.uiSettings.apply {
                    isZoomControlsEnabled = false
                    isCompassEnabled = true
                    isMyLocationButtonEnabled = true
                    isScaleControlsEnabled = true
                    // 与小程序 `<map>` 一致：可拖动、可双指缩放
                    isRotateGesturesEnabled = false
                    // 倾斜手势跟着倾斜角开：没要立体视角的页面不该多出一个能改变读图几何的手势
                    isTiltGesturesEnabled = tiltDegrees > 0f
                }
                // 蓝点图层（与上面那个按钮是两回事）：只有调用方确认已授权才开 ——
                // SDK 不会替我们弹权限框，未授权时开图层等于画一个永远不来位置的点。
                // 不另设 MyLocationStyle：本 SDK 版本的样式常量名与文档不一致（实测
                // `LOCATE_TYPE_LOCATION_CENTER_MAP` 不可解析），默认样式已是"定位到并把地图居中"。
                aMap.isMyLocationEnabled = showMyLocation
                aMap.clear()

                paths.forEach { p ->
                    // 来源不可信就不画：高德底图上的任何一条线都会被司机当成路
                    val pts = p.validated() ?: return@forEach
                    val color = when (p.colorRole) {
                        MapPath.ROLE_ACCENT -> t.clay.toArgb()
                        MapPath.ROLE_MUTED -> t.lineDeep.toArgb()
                        else -> t.primary.toArgb()
                    }
                    aMap.addPolyline(
                        PolylineOptions()
                            .addAll(pts.map { LatLng(it.lat, it.lng) })
                            .width(9f)
                            .color(color),
                    )
                }

                markers.forEach { m ->
                    if (m.geo.lat == 0.0 && m.geo.lng == 0.0) return@forEach
                    // 3D 形状只认 Map3DVisual 这一个出口，页面侧不许再各自 setFlat / setAltitude / setRotateAngle
                    val v3d = Map3DVisual.marker3DOf(m.kind, m.visual)
                    var options = MarkerOptions()
                        .position(LatLng(m.geo.lat, m.geo.lng))
                        .title(m.title)
                        .snippet(m.detail)
                        .icon(BitmapDescriptorFactory.defaultMarker(markerHue(m.kind)))
                        .anchor(0.5f, 0.5f)
                        .setFlat(v3d.flat)
                        .perspective(v3d.perspective)
                        .zIndex(v3d.zIndex)
                    // 朝向与浮空都是"有值才设"：null 与 0 米保持 SDK 默认（不旋转、贴地）
                    v3d.rotateAngle?.let { angle -> options = options.rotateAngle(angle) }
                    if (v3d.altitudeMeters > 0f) options = options.altitude(v3d.altitudeMeters)
                    aMap.addMarker(options)
                }

                // 视口自适应：点集包围盒 + 留白（小程序 include-points 等价能力）
                val all = markers.map { it.geo } + paths.flatMap { it.validated().orEmpty() }
                // tilt 先立即置好，再让视口走动画：反过来会让每次位置更新都重播一次"抬起来"，
                // 而 ⑫ 要的 tilt 是固定视角。真机上 tilt 是否被 bounds 更新重置尚未复核（§无设备）。
                if (tiltDegrees > 0f) aMap.moveCamera(CameraUpdateFactory.changeTilt(tiltDegrees))
                when {
                    fitAll && all.size >= 2 -> {
                        val b = all.fold(
                            LatLngBounds.builder().include(LatLng(all[0].lat, all[0].lng)),
                        ) { acc, g -> acc.include(LatLng(g.lat, g.lng)) }
                        aMap.animateCamera(
                            CameraUpdateFactory.newLatLngBounds(b.build(), 80),
                            Map3DVisual.CAMERA_ANIMATION_MS,
                            null,
                        )
                    }
                    focus != null -> aMap.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(LatLng(focus.lat, focus.lng), zoom),
                        Map3DVisual.CAMERA_ANIMATION_MS,
                        null,
                    )
                    all.size == 1 -> aMap.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(LatLng(all[0].lat, all[0].lng), zoom),
                        Map3DVisual.CAMERA_ANIMATION_MS,
                        null,
                    )
                }
            }.onFailure { unavailable = true }
        },
    )
}

/** 高德默认 marker 的色相值（0=红 / 30≈橙 / 120=绿 …），按语义分配 */
private fun markerHue(kind: Int): Float = when (kind) {
    MapMarker.KIND_START -> 210f
    MapMarker.KIND_END -> 30f
    MapMarker.KIND_BUS_REAL -> 210f
    MapMarker.KIND_BUS_SIM -> 30f
    MapMarker.KIND_ME -> 45f
    // 任务点四态：未开始蓝 / 进行中橙 / 已完成绿 / 失败红 —— 失败绝不能借用"完成"的绿
    MapMarker.KIND_TASK_TODO -> 240f
    MapMarker.KIND_TASK_ACTIVE -> 30f
    MapMarker.KIND_TASK_DONE -> 120f
    MapMarker.KIND_TASK_FAILED -> 0f
    else -> 240f
}

private fun androidx.compose.ui.graphics.Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255f).toInt().coerceIn(0, 255),
    (red * 255f).toInt().coerceIn(0, 255),
    (green * 255f).toInt().coerceIn(0, 255),
    (blue * 255f).toInt().coerceIn(0, 255),
)

/** 地图不可用时的占位说明（由调用方决定是否展示自绘回退） */
@Composable
fun MapUnavailableHint(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize())
}
