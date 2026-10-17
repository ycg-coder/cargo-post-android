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
    /**
     * 「现在为哪一组点集取景」。传了值就变成**取景一次就不再抢镜头**：只有这个标记变了（换了线路）
     * 才重新自适应视口，而用户自己拖过地图之后连这个也不再自动做，直到他换一条线。
     * 不传（默认 null）保持旧行为：每次点集变化都重新取景 —— 司机任务地图要跟着车走，那是故意的。
     *
     * 2026-10-17 用户报「实时公交地图固定在上面滑动不了」：实时公交页每几秒轮询一次车辆位置，
     * 点集一直在变 ⇒ 旧的无条件 `animateCamera` 每轮都把镜头拽回去，用户拖动等于没拖。
     */
    fitTag: String? = null,
    onUnavailable: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val t = LocalBusTokens.current
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var unavailable by remember { mutableStateOf(false) }
    var prevMarkersKey by remember { mutableStateOf(0) }
    var prevPathsKey by remember { mutableStateOf(0) }
    var userMoved by remember { mutableStateOf(false) }
    var fittedTag by remember { mutableStateOf<String?>(null) }

    val currentMarkersKey = markers.hashCode()
    val currentPathsKey = paths.hashCode()

    // 换点集（换线路）= 用户主动要看新的那一组 ⇒ 允许重新取景一次，并把"他拖过"这一格清掉
    LaunchedEffect(fitTag) { userMoved = false }

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
            try {
                view.onCreate(null)
                // ⚠ 必须在这里显式 onResume：factory 是在 composition 里才把 MapView 造出来的，
                //   那一刻宿主的 lifecycle **早就 RESUMED 了**，上面那个 observer 只能收到"之后"的事件，
                //   ON_RESUME 永远不会再补发一次 ⇒ onResume() 一次都没被调过 ⇒ 高德**一直黑屏**
                //   （官方要求 onCreate 之后必须 onResume 才开始出图）。
                //   真机症状就是：这一格看起来"地图没显示、只有站点和路线"——其实那正是
                //   黑屏之后被降级去画的自绘兜底图。
                view.onResume()
            } catch (e: Exception) {
                // 以前是 runCatching{}.onFailure { unavailable = true } —— 异常整个丢掉，
                // 于是"为什么不可用"永远查不到，只能反复猜。日志留原文，界面那句仍走兜底文案。
                android.util.Log.e("BUS_MAP", "MapView 初始化失败（onCreate/onResume）", e)
                unavailable = true
            }
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
                    isRotateGesturesEnabled = false
                    isTiltGesturesEnabled = tiltDegrees > 0f
                }
                // 蓝点图层（与上面那个按钮是两回事）：只有调用方确认已授权才开 ——
                // SDK 不会替我们弹权限框，未授权时开图层等于画一个永远不来位置的点。
                // 不另设 MyLocationStyle：本 SDK 版本的样式常量名与文档不一致（实测
                // `LOCATE_TYPE_LOCATION_CENTER_MAP` 不可解析），默认样式已是"定位到并把地图居中"。
                aMap.isMyLocationEnabled = showMyLocation
                // 用户一碰地图，镜头就交还给他：fitTag 模式下此后不再自动拽回（换线路才恢复）
                aMap.setOnMapTouchListener { _ -> userMoved = true }
                val markersChanged = currentMarkersKey != prevMarkersKey
                val pathsChanged = currentPathsKey != prevPathsKey
                if (markersChanged || pathsChanged) {
                    aMap.clear()
                    prevMarkersKey = currentMarkersKey
                    prevPathsKey = currentPathsKey
                }

                if (pathsChanged) paths.forEach { p ->
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

                if (markersChanged) markers.forEach { m -> markerOptionsOf(m)?.let { aMap.addMarker(it) } }

                // 视口自适应：点集包围盒 + 留白（小程序 include-points 等价能力）
                val all = markers.map { it.geo } + paths.flatMap { it.validated().orEmpty() }
                // tilt 先立即置好，再让视口走动画：反过来会让每次位置更新都重播一次"抬起来"，
                // 而 ⑫ 要的 tilt 是固定视角。真机上 tilt 是否被 bounds 更新重置尚未复核（§无设备）。
                // ⚠ fitTag 非空时整段镜头都要让位给用户：轮询一直在改点集，无条件取景 = 他拖不动（2026-10-17 报）。
                val wantCamera = fitTag == null || (!userMoved && fittedTag != fitTag)
                if (wantCamera) {
                    if (fitTag != null) fittedTag = fitTag
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
                }
            }.onFailure { unavailable = true }
        },
    )
}

/** 高德默认 marker 的色相值（0=红 / 30≈橙 / 120=绿 …），按语义分配 */
/**
 * [MapMarker] → 高德 `MarkerOptions` 的**唯一**换算出口。
 *
 * 为什么必须是函数而不是留在 `BusMap` 的 update 块里：车道级导航页（`DriverGuideScreen` 的
 * `AMapNaviView.getMap()`）要贴同一批任务点标记，而配色与 3D 形状规则一旦在两处各写一遍，
 * 就会出现"工作台地图上失败是红的、导航面上失败是绿的"。3D 那三项仍然只认 [Map3DVisual]。
 *
 * 坐标 (0,0) 回 null = 这一个点不画：任务点没坐标时后端给的是 0/0，画上去就是几内亚湾外一个标记，
 * 司机看到的是一个**存在但不存在**的站（与 `hasGeo` 那条同一口径）。
 */
internal fun markerOptionsOf(m: MapMarker): MarkerOptions? {
    if (m.geo.lat == 0.0 && m.geo.lng == 0.0) return null
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
    return options
}

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
