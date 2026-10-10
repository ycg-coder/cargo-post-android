package com.shanxiang.buspost.ui.user

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanxiang.buspost.core.ui.BusButton
import com.shanxiang.buspost.core.ui.BusButtonVariant
import com.shanxiang.buspost.core.ui.BusCard
import com.shanxiang.buspost.core.ui.BusIcons
import com.shanxiang.buspost.core.ui.BusLazyPage
import com.shanxiang.buspost.core.ui.BusTag
import com.shanxiang.buspost.core.ui.BusTextField
import com.shanxiang.buspost.core.ui.BusTone
import com.shanxiang.buspost.core.ui.EmptyState
import com.shanxiang.buspost.core.ui.LocalBusFontScale
import com.shanxiang.buspost.core.ui.LocalBusTokens
import com.shanxiang.buspost.core.ui.SectionTitle
import com.shanxiang.buspost.data.model.BusVehicle
import com.shanxiang.buspost.data.model.Geo
import com.shanxiang.buspost.data.model.NearbyResult
import com.shanxiang.buspost.data.model.Station
import com.shanxiang.buspost.data.model.byUserDistance
import com.shanxiang.buspost.data.repo.Repo
import com.shanxiang.buspost.nav.Routes
import com.shanxiang.buspost.ui.common.BackTopBar
import com.shanxiang.buspost.ui.common.LocalNavController
import com.shanxiang.buspost.ui.common.ScreenViewModel

class AddressSearchViewModel : ScreenViewModel() {
    var address by mutableStateOf("")
    var searchResult by mutableStateOf<Repo.GeoResult?>(null)
    var nearby by mutableStateOf<NearbyResult?>(null)
    var vehicles by mutableStateOf<List<BusVehicle>>(emptyList())
    var stations by mutableStateOf<List<Station>>(emptyList())
    var searching by mutableStateOf(false)
    var locationName by mutableStateOf("")

    fun search() = work {
        if (address.isBlank()) return@work
        searching = true
        try {
            val result = Repo.forwardGeo(address)
            searchResult = result
            if (result != null) {
                locationName = result.formatted
                val nb = Repo.nearbyBuses(result.geo)
                nearby = nb.value
                vehicles = nb.value?.vehicles.orEmpty().byUserDistance()
                stations = nb.value?.stations.orEmpty()
                mergeDemo(nb.demo)
            }
        } finally {
            searching = false
        }
    }

    fun clear() {
        address = ""
        searchResult = null
        nearby = null
        vehicles = emptyList()
        stations = emptyList()
        locationName = ""
    }
}

@Composable
fun AddressSearchScreen(
    vm: AddressSearchViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val nav = LocalNavController.current

    // 从首页地址簿跳转过来时，自动填入地址并查询
    androidx.compose.runtime.LaunchedEffect(nav) {
        val initialAddress = nav.previousBackStackEntry?.savedStateHandle?.get<String>("initialAddress")
        if (!initialAddress.isNullOrEmpty() && vm.address.isEmpty()) {
            vm.address = initialAddress
            nav.previousBackStackEntry?.savedStateHandle?.remove<String>("initialAddress")
            vm.search()
        }
    }

    BusLazyPage(spacedBy = 12.dp) {
        item {
            BackTopBar(title = "手动地址查公交", subtitle = "输入地址，查看该地公交与服务")
        }

        item {
            BusCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        "输入具体地址",
                        color = t.ink,
                        fontSize = (14 * scale).sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    BusTextField(
                        value = vm.address,
                        onValueChange = { vm.address = it },
                        placeholder = "如：XX省XX市XX县XX镇XX村",
                        singleLine = false,
                        minLines = 2,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BusButton(
                            text = "查询",
                            onClick = { vm.search() },
                            enabled = vm.address.isNotBlank() && !vm.searching,
                            modifier = Modifier.weight(1f),
                        )
                        if (vm.searchResult != null) {
                            BusButton(
                                text = "清除",
                                onClick = { vm.clear() },
                                variant = BusButtonVariant.OUTLINE,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }

        if (vm.searching) {
            item {
                BusCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(20.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(t.primary.copy(alpha = 0.2f)),
                        )
                        Text(
                            " 正在查询地址...",
                            color = t.textSecondary,
                            fontSize = (13 * scale).sp,
                        )
                    }
                }
            }
        }

        vm.searchResult?.let { result ->
            item {
                BusCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(BusIcons.checkCircle, null, tint = t.primary, modifier = Modifier.size(20.dp))
                            Text(
                                " 地址解析成功",
                                color = t.primary,
                                fontSize = (14 * scale).sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            result.formatted,
                            color = t.ink,
                            fontSize = (14 * scale).sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            "坐标：${"%.4f".format(result.geo.lat)}, ${"%.4f".format(result.geo.lng)}",
                            color = t.textSecondary,
                            fontSize = (12 * scale).sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        if (result.adcode.isNotEmpty()) {
                            BusTag("区域码 ${result.adcode}", tone = BusTone.NEUTRAL, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }

            // 附近公交
            if (vm.vehicles.isNotEmpty()) {
                item {
                    SectionTitle("附近公交 · ${vm.vehicles.size} 辆在跑")
                }
                items(vm.vehicles.take(5), key = { it.plate + it.lineName }) { v ->
                    BusCard(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    v.lineName.ifEmpty { v.plate.ifEmpty { "班车" } },
                                    color = t.ink,
                                    fontSize = (16 * scale).sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                )
                                if (v.statusLabel.isNotEmpty()) {
                                    BusTag(v.statusLabel, tone = BusTone.PRIMARY)
                                }
                            }
                            Text(
                                "${v.startStation} → ${v.endStation}",
                                color = t.textSecondary,
                                fontSize = (12 * scale).sp,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            v.etaCaptionText.takeIf { it.isNotEmpty() }?.let {
                                Text(
                                    it,
                                    color = t.primaryOnLight,
                                    fontSize = (12 * scale).sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
            } else if (vm.nearby != null) {
                item {
                    EmptyState(
                        title = "该地址附近暂无实时公交",
                        subtitle = "可能是非运营时间或该区域暂无公交覆盖",
                        icon = BusIcons.bus,
                    )
                }
            }

            // 附近站点
            if (vm.stations.isNotEmpty()) {
                item {
                    SectionTitle("附近站点 · ${vm.stations.size} 个")
                }
                items(vm.stations.take(6), key = { it.id }) { st ->
                    BusCard(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(BusIcons.location, null, tint = t.primaryOnLight, modifier = Modifier.size(16.dp))
                            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                                Text(
                                    st.name,
                                    color = t.ink,
                                    fontSize = (14 * scale).sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                if (st.distanceMeters > 0) {
                                    Text(
                                        "距搜索点 ${"%.0f".format(st.distanceMeters)} 米",
                                        color = t.textSecondary,
                                        fontSize = (12 * scale).sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 操作提示
            item {
                BusCard(modifier = Modifier.fillMaxWidth(), warm = true) {
                    Column {
                        Text(
                            "提示",
                            color = t.clayDeep,
                            fontSize = (13 * scale).sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "当前查看的是「${result.formatted}」附近的公交与服务。如需下单寄货或购物，请确保收货地址也在该区域。",
                            color = t.textSecondary,
                            fontSize = (12 * scale).sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }

        if (!vm.searching && vm.searchResult == null && vm.address.isNotEmpty()) {
            item {
                EmptyState(
                    title = "未找到该地址",
                    subtitle = "请检查地址是否完整，或尝试添加省市区信息",
                    icon = BusIcons.location,
                )
            }
        }

        item { Spacer(Modifier.height(20.dp)) }
    }
}
