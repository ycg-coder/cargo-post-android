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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanxiang.buspost.core.loc.Locator
import com.shanxiang.buspost.core.ui.BusButton
import com.shanxiang.buspost.core.ui.BusCard
import com.shanxiang.buspost.core.ui.BusIcons
import com.shanxiang.buspost.core.ui.BusRadius
import com.shanxiang.buspost.core.ui.BusTag
import com.shanxiang.buspost.core.ui.BusTextField
import com.shanxiang.buspost.core.ui.BusTone
import com.shanxiang.buspost.core.ui.LocalBusFontScale
import com.shanxiang.buspost.core.ui.LocalBusTokens
import com.shanxiang.buspost.data.model.Geo
import com.shanxiang.buspost.data.model.Reachability
import com.shanxiang.buspost.data.model.ReachabilityState
import com.shanxiang.buspost.data.model.Station
import com.shanxiang.buspost.data.repo.Repo
import com.shanxiang.buspost.nav.Routes
import com.shanxiang.buspost.ui.common.BackTopBar
import com.shanxiang.buspost.ui.common.LocalNavController
import com.shanxiang.buspost.ui.common.ScreenViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * 寄货地址选择页的 ViewModel
 */
class SendAddressPickerViewModel : ScreenViewModel() {
    var addressInput by mutableStateOf("")
    var selectedGeo by mutableStateOf<Geo?>(null)
    var addressText by mutableStateOf("")
    var reachability by mutableStateOf<Reachability?>(null)
    var reachDemo by mutableStateOf(false)
    var reachError by mutableStateOf("")
    var nearbyStations by mutableStateOf<List<Station>>(emptyList())
    var stationsDemo by mutableStateOf(false)
    var searching by mutableStateOf(false)

    fun loadNearbyStations(context: android.content.Context) = work {
        val geo = Locator.once(context)
        val f = Repo.sendStations()
        nearbyStations = f.items
        stationsDemo = f.demo
        // 如果有定位，按距离排序
        if (geo != null) {
            nearbyStations = nearbyStations.sortedBy { s ->
                if (s.geo.lat != 0.0 || s.geo.lng != 0.0) {
                    distanceMeters(geo.lat, geo.lng, s.geo.lat, s.geo.lng)
                } else {
                    Double.MAX_VALUE
                }
            }
        }
        mergeDemo(f.demo)
        error = f.error
    }

    fun searchAddress(address: String) = work(reset = false) {
        if (address.isBlank()) {
            error = "请输入地址"
            return@work
        }
        searching = true
        error = ""
        try {
            val result = Repo.forwardGeo(address)
            if (result == null) {
                error = "未找到该地址，请检查输入是否正确"
                return@work
            }
            val geo = result.geo
            selectedGeo = geo
            addressText = result.formatted.ifEmpty { address }
            // 检查可达性
            val r = Repo.reachability(geo)
            reachability = r.value
            reachDemo = r.value != null && r.demo
            reachError = r.error.orEmpty()
        } finally {
            searching = false
        }
    }

    fun selectStation(station: Station) {
        selectedGeo = station.geo
        addressText = station.name
        reachability = null
    }

    private fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLng / 2) * Math.sin(dLng / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }
}

/**
 * 寄货地址选择页：支持三种方式选择取货/送货地址
 * 1. 手动输入地址 → 正向地理编码 → 显示可达性
 * 2. 选择附近站点
 * 3. 地图选点
 */
@Composable
fun SendAddressPickerScreen(
    mode: String = "pickup",
    vm: SendAddressPickerViewModel = viewModel(),
) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val nav = LocalNavController.current
    val context = LocalContext.current

    val title = if (mode == "pickup") "选择取货地址" else "选择送货地址"

    LaunchedEffect(Unit) {
        vm.loadNearbyStations(context)
    }

    Column(Modifier.fillMaxSize()) {
        BackTopBar(title = title)

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 方式一：手动输入地址
            BusCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(BusIcons.location, null, tint = t.primary, modifier = Modifier.size(20.dp))
                        Text(
                            "手动输入地址",
                            color = t.ink,
                            fontSize = (14 * scale).sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }

                    BusTextField(
                        value = vm.addressInput,
                        onValueChange = { vm.addressInput = it },
                        label = "地址",
                        placeholder = ADDRESS_EXAMPLE_HINT,
                        icon = BusIcons.location,
                        minLines = 2,
                        singleLine = false,
                    )

                    BusButton(
                        text = if (vm.searching) "查询中…" else "查询地址",
                        onClick = { vm.searchAddress(vm.addressInput) },
                        loading = vm.searching,
                        icon = BusIcons.search,
                    )
                }
            }

            // 查询结果
            if (vm.selectedGeo != null) {
                BusCard(modifier = Modifier.fillMaxWidth(), warm = true) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                BusIcons.location,
                                null,
                                tint = t.primary,
                                modifier = Modifier.size(18.dp).padding(top = 2.dp),
                            )
                            Column(Modifier.padding(start = 8.dp).weight(1f)) {
                                Text(
                                    vm.addressText,
                                    color = t.ink,
                                    fontSize = (13 * scale).sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    "经度 ${String.format("%.6f", vm.selectedGeo!!.lng)}，纬度 ${String.format("%.6f", vm.selectedGeo!!.lat)}",
                                    color = t.textTertiary,
                                    fontSize = (11 * scale).sp,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }

                        // 可达性
                        vm.reachability?.let { reach ->
                            val isReachable = reach.state == ReachabilityState.REACHABLE
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (isReachable) BusIcons.checkCircle else BusIcons.warning,
                                    null,
                                    tint = if (isReachable) t.primary else t.danger,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    if (isReachable) "车辆可送达" else "超出配送范围",
                                    color = if (isReachable) t.primary else t.danger,
                                    fontSize = (12 * scale).sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 6.dp),
                                )
                            }
                            reach.distanceKm?.let { km ->
                                Text(
                                    "距离：${String.format("%.1f", km)} 公里",
                                    color = t.textSecondary,
                                    fontSize = (11 * scale).sp,
                                )
                            }
                            if (vm.reachDemo) {
                                Text("演示数据，非后端真实判定", color = t.clayDeep, fontSize = (10 * scale).sp)
                            }
                        }

                        // 确认按钮
                        BusButton(
                            text = "确认使用此地址",
                            onClick = {
                                nav.previousBackStackEntry?.savedStateHandle?.set("selectedGeo", vm.selectedGeo)
                                nav.previousBackStackEntry?.savedStateHandle?.set("selectedAddress", vm.addressText)
                                nav.popBackStack()
                            },
                            icon = BusIcons.check,
                        )
                    }
                }
            }

            // 方式二：选择附近站点
            if (vm.nearbyStations.isNotEmpty()) {
                BusCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(BusIcons.bus, null, tint = t.primary, modifier = Modifier.size(20.dp))
                            Text(
                                "附近站点",
                                color = t.ink,
                                fontSize = (14 * scale).sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                            BusTag(
                                "${vm.nearbyStations.size} 个",
                                tone = BusTone.PRIMARY,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }

                        vm.nearbyStations.take(10).forEach { station ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(BusRadius.m))
                                    .background(t.track.copy(alpha = 0.3f))
                                    .clickable {
                                        vm.selectStation(station)
                                        nav.previousBackStackEntry?.savedStateHandle?.set("selectedStation", station)
                                        nav.popBackStack()
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    BusIcons.location,
                                    null,
                                    tint = t.textTertiary,
                                    modifier = Modifier.size(16.dp),
                                )
                                Column(Modifier.padding(start = 8.dp).weight(1f)) {
                                    Text(
                                        station.name,
                                        color = t.ink,
                                        fontSize = (13 * scale).sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    if (station.distanceMeters > 0) {
                                        Text(
                                            "距你 %.1f 公里".format(station.distanceMeters / 1000.0),
                                            color = t.textTertiary,
                                            fontSize = (11 * scale).sp,
                                        )
                                    }
                                    if (station.routes.isNotEmpty()) {
                                        Text(
                                            "途经：${station.routes.joinToString("、")}",
                                            color = t.textTertiary,
                                            fontSize = (10 * scale).sp,
                                            modifier = Modifier.padding(top = 2.dp),
                                        )
                                    }
                                }
                                Icon(
                                    BusIcons.next,
                                    null,
                                    tint = t.textTertiary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }

            // 方式三：地图选点
            BusCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            nav.navigate(Routes.MAP_POINT_PICKER)
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(BusIcons.map, null, tint = t.primary, modifier = Modifier.size(20.dp))
                    Column(Modifier.padding(start = 8.dp).weight(1f)) {
                        Text(
                            "地图选点",
                            color = t.ink,
                            fontSize = (14 * scale).sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "在地图上点击选择位置",
                            color = t.textSecondary,
                            fontSize = (11 * scale).sp,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Icon(BusIcons.next, null, tint = t.textTertiary, modifier = Modifier.size(18.dp))
                }
            }

            vm.error?.let {
                Text(it, color = t.danger, fontSize = (12 * scale).sp)
            }
        }
    }
}
