package com.shanxiang.buspost.ui.user

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.shanxiang.buspost.core.ui.BusButton
import com.shanxiang.buspost.core.ui.BusIcons
import com.shanxiang.buspost.core.ui.BusRadius
import com.shanxiang.buspost.core.ui.LocalBusFontScale
import com.shanxiang.buspost.core.ui.LocalBusTokens
import com.shanxiang.buspost.data.model.Geo
import com.shanxiang.buspost.data.repo.Repo
import com.shanxiang.buspost.ui.common.BackTopBar
import com.shanxiang.buspost.ui.common.LocalNavController
import kotlinx.coroutines.delay

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

    LaunchedEffect(Unit) {
        // 如果没有初始位置，用当前位置
        if (selectedGeo == null) {
            val current = Locator.once(context)
            if (current != null) {
                selectedGeo = current
            }
        }
    }

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

                            // 点击地图选择位置
                            aMap.setOnMapClickListener { latLng ->
                                val geo = Geo(latLng.latitude, latLng.longitude)
                                selectedGeo = geo
                                updateMarker(aMap, marker, geo)?.let { marker = it }
                            }

                            // 初始位置
                            selectedGeo?.let { geo ->
                                val latLng = LatLng(geo.lat, geo.lng)
                                aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                                updateMarker(aMap, marker, geo)?.let { marker = it }
                            }
                        }
                        mapView = this
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

            // 中心十字准星（提示用户点击选点）
            if (selectedGeo == null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable(enabled = false) {},
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        BusIcons.location,
                        null,
                        tint = t.primary.copy(alpha = 0.6f),
                        modifier = Modifier.size(48.dp),
                    )
                }
            }
        }

        // 底部信息面板
        Column(
            Modifier
                .fillMaxWidth()
                .background(t.card)
                .clip(RoundedCornerShape(topStart = BusRadius.l, topEnd = BusRadius.l))
                .padding(16.dp),
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

                Spacer(Modifier.height(12.dp))

                // 确认按钮
                BusButton(
                    text = "确认选择此位置",
                    onClick = {
                        nav.previousBackStackEntry?.savedStateHandle?.set("selectedGeo", selectedGeo)
                        nav.popBackStack()
                    },
                    icon = BusIcons.check,
                )
            } else {
                Text(
                    "点击地图选择位置",
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
