package com.shanxiang.buspost.ui.driver

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanxiang.buspost.BuildConfig
import com.shanxiang.buspost.core.ui.BusRadius
import com.shanxiang.buspost.core.ui.BusTokens
import com.shanxiang.buspost.core.ui.LocalBusFontScale
import com.shanxiang.buspost.core.ui.LocalBusTokens
import com.shanxiang.buspost.data.model.Geo
import com.shanxiang.buspost.data.model.MissionStop
import com.shanxiang.buspost.data.model.RouteGuide
import com.shanxiang.buspost.ui.common.BusMap
import com.shanxiang.buspost.ui.common.GpsFixState
import com.shanxiang.buspost.ui.common.GpsFixVisual
import com.shanxiang.buspost.ui.common.Map3DVisual
import com.shanxiang.buspost.ui.common.MapMarker
import com.shanxiang.buspost.ui.common.RouteMap

/**
 * 司机任务地图（P1-1）：高德真地图 + 调度任务点。
 *
 * 分工在这一层写死：
 * · 顺序来自 `/driver/route` 的 `stops[].visitSequence`（调度决定去哪里），本页不重排；
 * · 折线只装通过 `RealRoadRouteValidator` 的真实道路（导航决定怎么走），本页不补线；
 * · 本页只画，不推进业务 —— 到站/交接必须是司机主动动作 + 后端回读，禁止 GPS 自动完成业务。
 *
 * 没有可信折线时（F-3 ⑧）：两个分支念的是**同一句** [noRouteReason]，那句话由
 * `MissionMarkers.missionRouteReasonOf` 一个出口给出 —— 本页不再自带任何"后端没有下发"式兜底，
 * 因为请求失败时 `guide` 是 `RouteGuide.EMPTY`，本机其实**不知道**后端说过什么。
 * 两个分支都不存在"把任务点连起来当路"这条路径。
 */
@Composable
fun DriverMissionMap(
    guide: RouteGuide,
    /** 无路线处的那句说明：只能由调用方经 `missionRouteReasonOf` 给出，本页不自己编 */
    noRouteReason: String,
    modifier: Modifier = Modifier,
    me: Geo? = null,
    /**
     * 本机 GPS 的视觉状态（新鲜度三态 + 朝向 + 那句实话），由调用方从 `Running` 组装。
     * null = 服务还没给出结论：按"不降级、无朝向"画，不编一个正北出来。
     */
    meGps: GpsFixVisual? = null,
    focusStopId: Long = 0L,
    height: Dp = 236.dp,
    onReplan: (() -> Unit)? = null,
) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    var mapFailed by remember { mutableStateOf(false) }

    val orderedStops = MissionMarkers.ordered(guide.stops)
    // 降级只读服务的结论（点龄越过停报阈值 / 一条 fix 都没有），本页不建第二个新鲜度时钟
    val degraded = meGps != null && meGps.state != GpsFixState.LIVE
    val markers: List<MapMarker> = MissionMarkers.stopMarkers(guide.stops, focusStopId) +
        listOfNotNull(
            MissionMarkers.vehicleMarker(guide),
            MissionMarkers.meMarker(me, meGps?.headingDegrees, degraded),
        )
    val paths = MissionMarkers.paths(guide)
    val canUseRealMap = BuildConfig.AMAP_KEY.isNotBlank() && !mapFailed && markers.isNotEmpty()

    Column(modifier.fillMaxWidth()) {
        if (canUseRealMap) {
            BusMap(
                markers = markers,
                paths = paths,
                modifier = Modifier.fillMaxWidth().height(height),
                focus = me ?: guide.vehicleGeo,
                tiltDegrees = Map3DVisual.MAP_TILT_DEGREES,
                onUnavailable = { mapFailed = true },
            )
        } else {
            RouteMap(
                polyline = guide.polyline,
                stations = guide.stations,
                me = me,
                highlightStationId = focusStopId,
                height = height,
                reason = noRouteReason,
                onReplan = onReplan,
            )
        }

        if (paths.isEmpty() && guide.stops.isNotEmpty()) {
            // 真地图分支没有 RouteMap 自带的不可用态，这里补同样一句实话：底图照旧，但没有路
            Text(
                noRouteReason,
                color = t.textSecondary,
                fontSize = (11.5 * scale).sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }

        if (guide.stops.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            MissionStopStrip(stops = orderedStops, focusStopId = focusStopId)
        }

        Text(
            missionFootnote(guide, meGps?.note.orEmpty()),
            color = t.textTertiary,
            fontSize = (11 * scale).sp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** 任务点条：序号 / 动作 / 件数（或人数）/ 状态配色，数据全部来自后端 `stops` */
@Composable
private fun MissionStopStrip(stops: List<MissionStop>, focusStopId: Long) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    LazyRow(
        contentPadding = PaddingValues(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        modifier = Modifier.fillMaxWidth().height(66.dp),
    ) {
        items(stops, key = { "${it.visitSequence}-${it.stationId}-${it.orderId}" }) { stop ->
            val highlighted = focusStopId != 0L && stop.stationId == focusStopId
            Column(
                Modifier
                    .clip(RoundedCornerShape(BusRadius.m))
                    .background(if (highlighted) t.primaryLight else t.card)
                    .padding(horizontal = 11.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(stopStatusColor(t, stop)))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        (if (stop.visitSequence > 0) "${stop.visitSequence} · " else "") +
                            stop.name.ifEmpty { "任务点" },
                        color = t.ink,
                        fontSize = (12.5 * scale).sp,
                        fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        with(MissionMarkers) { listOfNotNull(stop.actionLabel.ifEmpty { null }, stop.statusLabel.ifEmpty { null }).joinToString(" · ") },
                        color = t.textSecondary,
                        fontSize = (11 * scale).sp,
                        maxLines = 1,
                    )
                    with(MissionMarkers) { stop.quantityText() }?.let { q ->
                        Spacer(Modifier.width(5.dp))
                        Text(q, color = t.clay, fontSize = (11 * scale).sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

/**
 * 脚注（司机话术）：本班站数 + 下一站 + 道路里程。后端没给道路里程就明说"暂无"，
 * **不拿直线距离凑一个数**，也不把"接口/来源 OSM_GRAPHHOPPER"这类内部口径念给司机（改走日志）。
 * `gpsNote` 非空时并进同一句（⑬ 的降级/不可用警告）—— 不额外加控件，也不重复那句原因：
 * 它是 `LocationReportPolicy` 给的原话，经 `Running.waiting` 转过来。
 */
internal fun missionFootnote(guide: RouteGuide, gpsNote: String = ""): String = buildString {
    append("本班 ").append(guide.stops.size).append(" 站")
    if (guide.nextStation.isNotEmpty()) append(" · 下一站 ").append(guide.nextStation)
    append(
        if (guide.roadDistanceMeters > 0) " · 道路里程 " + formatRoadMeters(guide.roadDistanceMeters)
        else " · 道路里程暂无，开始导航后给实时剩余",
    )
    if (gpsNote.isNotEmpty()) append(" · ").append(gpsNote)
}

internal fun stopStatusColor(t: BusTokens, stop: MissionStop) = when {
    // 失败永远独立成色，不能和"已完成"共用
    stop.isFailed -> t.danger
    stop.isDone -> t.statusDone
    stop.isActive -> t.clay
    else -> t.statusPending
}

internal fun formatRoadMeters(meters: Int): String = when {
    meters <= 0 -> "接口未给"
    meters < 1000 -> "$meters 米"
    else -> "%.1f 公里".format(meters / 1000.0)
}
