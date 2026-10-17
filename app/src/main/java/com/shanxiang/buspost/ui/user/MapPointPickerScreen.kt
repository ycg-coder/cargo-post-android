package com.shanxiang.buspost.ui.user

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.shanxiang.buspost.core.loc.Locator
import com.shanxiang.buspost.core.ui.BusElevation
import com.shanxiang.buspost.core.ui.BusIcons
import com.shanxiang.buspost.core.ui.BusRadius
import com.shanxiang.buspost.core.ui.LocalBusFontScale
import com.shanxiang.buspost.core.ui.LocalBusTokens
import com.shanxiang.buspost.core.ui.glassmorphic
import com.shanxiang.buspost.core.ui.glassSheen
import com.shanxiang.buspost.data.model.Geo
import com.shanxiang.buspost.data.repo.Repo
import com.shanxiang.buspost.ui.common.BackTopBar
import com.shanxiang.buspost.ui.common.CapsuleAction
import com.shanxiang.buspost.ui.common.CapsuleActions
import com.shanxiang.buspost.ui.common.LocalNavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val DEFAULT_CENTER = Geo(29.5630, 106.5516)

/**
 * 地图选点页：用户在地图上点击选择位置，底部显示逆地理地址和坐标。
 * 用于寄货页的取货/送货地址选择。
 */
@Composable
fun MapPointPickerScreen(
    initialLat: Double = 0.0,
    initialLng: Double = 0.0,
    title: String = "在地图上选择位置",
) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val nav = LocalNavController.current
    val context = LocalContext.current

    var selectedGeo by remember {
        mutableStateOf(
            if (initialLat != 0.0 || initialLng != 0.0) Geo(initialLat, initialLng) else null
        )
    }
    var addressText by remember { mutableStateOf("") }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var marker by remember { mutableStateOf<Marker?>(null) }
    var locating by remember { mutableStateOf(selectedGeo == null) }

    LaunchedEffect(Unit) {
        if (selectedGeo == null) {
            // 用较长超时，确保拿到真实位置；Locator 内部已有缓存→GPS→旧缓存三级回落
            val current = Locator.once(context, timeoutMs = 15000L)
            selectedGeo = current ?: DEFAULT_CENTER
            locating = false
        }
    }

    // 定位完成或初始坐标变化时，移动相机
    LaunchedEffect(selectedGeo, mapView) {
        val map = mapView?.map ?: return@LaunchedEffect
        val geo = selectedGeo ?: return@LaunchedEffect
        val latLng = LatLng(geo.lat, geo.lng)
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
        updateMarker(map, marker, geo)?.let { marker = it }
    }

    // 缺陷 #77：这一页过去只 `onCreate`/`onResume`，离场从不 `onPause`/`onDestroy`
    // ⇒ 高德的 SurfaceView 与纹理每开一次选点页漏一份（`BusMap` 早就按宿主对齐做了，这里补齐同一形状）。
    DisposableEffect(mapView) {
        val owned = mapView
        onDispose {
            owned?.onPause()
            owned?.onDestroy()
        }
    }

    // 这一页的两个功能按钮搬进**全局悬浮胶囊**（用户口径："点进去具体界面的悬浮导航栏就是具体界面的功能按钮"，
    // 以及本轮的"能悬浮的都可以做悬浮按钮不要阻挡"）。原来它们是分散在两处的：
    // 「回到我的位置」是地图右下角一枚自绘白方块，「确认选择此位置」是面板末尾一颗通栏按钮 ——
    // 两处各有各的高度，谁都不给彼此让位（白方块还会压在面板上）。
    // 搬进胶囊后：一屏只有一个浮层、确认这一格在没点出位置时是**带着原因**的禁用格而不是灰掉的一颗按钮。
    var relocating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    CapsuleActions(
        listOf(
            CapsuleAction(
                key = "relocate",
                label = if (relocating) "定位中" else "回到我的位置",
                icon = BusIcons.myLocation,
                enabled = !relocating,
                disabledReason = "正在取当前位置",
                onClick = {
                    relocating = true
                    scope.launch {
                        val loc = Locator.once(context, timeoutMs = 10000L)
                        if (loc != null) {
                            selectedGeo = loc
                            mapView?.map?.let { aMap ->
                                aMap.moveCamera(
                                    CameraUpdateFactory.newLatLngZoom(LatLng(loc.lat, loc.lng), 15f),
                                )
                            }
                        }
                        relocating = false
                    }
                },
            ),
            CapsuleAction(
                key = "confirm",
                label = "确认选点",
                icon = BusIcons.check,
                selected = selectedGeo != null,
                enabled = selectedGeo != null,
                disabledReason = "先在地图上点一下要的位置",
                onClick = {
                    selectedGeo?.let { geo ->
                        nav.previousBackStackEntry?.savedStateHandle?.set("selectedGeo", geo)
                        nav.popBackStack()
                    }
                },
            ),
        ),
    )

    Column(Modifier.fillMaxSize()) {
        BackTopBar(title = title)

        Box(Modifier.weight(1f)) {
            // 高德地图
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        onCreate(null)
                        onResume()
                        map?.let { aMap ->
                            aMap.uiSettings.isZoomControlsEnabled = true
                            aMap.uiSettings.isCompassEnabled = true

                            aMap.setOnMapClickListener { latLng ->
                                val geo = Geo(latLng.latitude, latLng.longitude)
                                selectedGeo = geo
                                updateMarker(aMap, marker, geo)?.let { marker = it }
                            }
                        }
                        mapView = this
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

            // 定位中提示
            if (locating) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.15f))
                        .clickable(enabled = false) {},
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        androidx.compose.material3.CircularProgressIndicator(
                            color = t.primary,
                            modifier = Modifier.size(36.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "正在获取位置…",
                            color = t.ink,
                            fontSize = (13 * scale).sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            // 「回到我的位置」原来在这里 —— 地图右下角一枚自绘白方块，已经搬进悬浮胶囊（见上面的 CapsuleActions）。
            // 地图下沿因此整块空出来了：不再有一枚浮在地图上的方格去压面板，也不占地图高度。
        }

        // 底部信息面板：只念地址与坐标，操作都交给悬浮胶囊。
        // 玻璃面（半透明渐变 + 顶边一道高光）而不是实色卡：它压在地图边上，实色块会让这一屏看起来像被切掉一截。
        Column(
            Modifier
                .fillMaxWidth()
                .glassmorphic(
                    t,
                    shape = RoundedCornerShape(topStart = BusRadius.l, topEnd = BusRadius.l),
                    shadowElevation = BusElevation.raised,
                )
                .glassSheen(t, shape = RoundedCornerShape(topStart = BusRadius.l, topEnd = BusRadius.l))
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            if (selectedGeo != null) {
                // 地址信息
                if (addressText.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            BusIcons.location,
                            null,
                            tint = t.primary,
                            modifier = Modifier.size(18.dp).padding(top = 2.dp),
                        )
                        Text(
                            addressText,
                            color = t.ink,
                            fontSize = (14 * scale).sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(start = 8.dp).weight(1f),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // 坐标
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        BusIcons.navigation,
                        null,
                        tint = t.textTertiary,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        "经度 ${String.format("%.6f", selectedGeo!!.lng)}，纬度 ${String.format("%.6f", selectedGeo!!.lat)}",
                        color = t.textTertiary,
                        fontSize = (11 * scale).sp,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }

                Spacer(Modifier.height(10.dp))

                // 那颗通栏「确认选择此位置」已经搬进悬浮胶囊；这一行只负责把人指过去，
                // 而不是留一句"确认后…"让人自己找出口。
                Text(
                    if (selectedGeo != null) "点下方悬浮栏的「确认选点」" else "在地图上点一下要的位置",
                    color = t.textSecondary,
                    fontSize = (12.5 * scale).sp,
                )
            } else {
                Text(
                    "正在取当前位置，或在地图上点一下",
                    color = t.textSecondary,
                    fontSize = (13 * scale).sp,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }

    // 逆地理编码
    LaunchedEffect(selectedGeo) {
        selectedGeo?.let { geo ->
            delay(300) // 防抖
            val result = Repo.reverseGeo(geo)
            addressText = result.formatted.ifEmpty { result.displayName }
        }
    }
}

private fun updateMarker(aMap: AMap, oldMarker: Marker?, geo: Geo): Marker? {
    oldMarker?.remove()
    return aMap.addMarker(
        MarkerOptions()
            .position(LatLng(geo.lat, geo.lng))
            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            .title("选中的位置")
            .draggable(false),
    )
}
