package com.shanxiang.buspost.ui.common

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanxiang.buspost.core.ui.BusCard
import com.shanxiang.buspost.core.ui.BusIcons
import com.shanxiang.buspost.core.ui.BusRadius
import com.shanxiang.buspost.core.ui.BusTag
import com.shanxiang.buspost.core.ui.BusTone
import com.shanxiang.buspost.core.ui.LocalBusFontScale
import com.shanxiang.buspost.core.ui.LocalBusTokens
import com.shanxiang.buspost.core.ui.RoadLine
import com.shanxiang.buspost.core.ui.SectionTitle
import com.shanxiang.buspost.core.ui.SourceTag
import com.shanxiang.buspost.core.ui.StationTimeline
import com.shanxiang.buspost.core.ui.TicketCard
import com.shanxiang.buspost.core.ui.TimelineStop
import com.shanxiang.buspost.data.model.ParcelTrace
import com.shanxiang.buspost.data.model.TransportLeg

/**
 * 轨迹视图：寄货单与商城订单共用。车票头 → 线路图 → 时间线 → 换乘段。
 * 折线缺失时 RouteMap 显示「暂无真实路线」，不用站点连线顶替；站点顺序由下面的时间线交代。
 */
@Composable
fun TraceView(
    trace: ParcelTrace,
    statusLabel: String,
    modifier: Modifier = Modifier,
    tone: BusTone = BusTone.PRIMARY,
    onReplan: (() -> Unit)? = null,
) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val context = LocalContext.current

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TicketCard(
            serial = trace.orderNo,
            stubValue = trace.etaMinutes?.toString() ?: "—",
            stubLabel = if (trace.etaMinutes != null) "分钟预计到" else "接口未给\n到站分钟",
            stubColor = if (trace.etaMinutes != null && trace.etaMinutes in 1..10) t.danger else t.primary,
        ) {
            Column(Modifier.weight(1f)) {
                Text(statusLabel, color = t.ink, fontSize = (16 * scale).sp, fontWeight = FontWeight.Bold)
                Text(
                    listOf(trace.goodsName, trace.targetStation).filter { it.isNotEmpty() }.joinToString(" · "),
                    color = t.textSecondary,
                    fontSize = (12.5 * scale).sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Row(
                    Modifier.padding(top = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (trace.vehiclePlate.isNotEmpty()) BusTag("承运 ${trace.vehiclePlate}", tone = tone)
                    if (trace.driverName.isNotEmpty()) BusTag("司机 ${trace.driverName}")
                }
                SourceTag(trace.dataSource, Modifier.padding(top = 6.dp))
            }
        }

        if (trace.driverMobile.isNotEmpty()) {
            BusCard(
                onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + trace.driverMobile))) },
                modifier = Modifier.fillMaxWidth(),
                padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(BusIcons.phone, null, tint = t.primary, modifier = Modifier.size(18.dp))
                    Text(
                        "联系司机 ${trace.driverName.ifEmpty { trace.driverMobile }}",
                        color = t.ink,
                        fontSize = (13.5 * scale).sp,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    Text(trace.driverMobile, color = t.textTertiary, fontSize = (12.5 * scale).sp)
                }
            }
        }

        SectionTitle(
            "线路图",
            subtitle = if (trace.polyline.size >= 2) "实际道路轨迹" else "暂无道路轨迹",
        )
        RouteMap(
            polyline = trace.polyline,
            stations = trace.stations,
            me = trace.vehicleGeo,
            highlightStationId = trace.stations.firstOrNull { it.name == trace.targetStation }?.id ?: 0L,
            height = 190.dp,
            reason = if (trace.polyline.size >= 2) null else "这一单还没有真实道路轨迹，可能是班车尚未发车或后端未回传折线。",
            onReplan = onReplan,
        )
        // 承运车辆位置：后端没给坐标就直说"暂无实时位置"，不许用固定点/0,0/随机点顶一个位置上来。
        // 新鲜度文案走 LocationSource.knownLabel 这个单一映射点（§8），页面不拼枚举码；
        // 后端没发新鲜度键（/product-order/trace 就是）时只能说"新鲜度未给"，不许升格成"实时"。
        Text(
            text = if (trace.vehicleGeo != null) {
                "车辆位置：" + (trace.locationSource.knownLabel ?: "新鲜度未给") +
                    if (trace.lastReportTime.isEmpty()) "，上报时间未给" else "，上报时间 ${trace.lastReportTime}"
            } else {
                "暂无实时位置"
            },
            color = t.textTertiary,
            fontSize = (12.5 * scale).sp,
            modifier = Modifier.padding(top = 6.dp),
        )
        // 当前任务：后端按派单经停明细下发作业状态名（AppSendController:395）；
        // 未派单或非下单人/收件人（/track 走 toProgressVO）时整块没有，就直说没有，不许拿订单状态名顶一个。
        Text(
            text = if (trace.carrierTaskStatus.isNotEmpty()) {
                "当前任务：" + trace.carrierTaskStatus +
                    if (trace.carrierTaskTime.isEmpty()) "" else "（更新于 ${trace.carrierTaskTime}）"
            } else {
                "当前任务：暂无数据（尚未派单，或无权查看明细）"
            },
            color = t.textTertiary,
            fontSize = (12.5 * scale).sp,
            modifier = Modifier.padding(top = 4.dp),
        )

        if (trace.nodes.isNotEmpty()) {
            SectionTitle("流转记录")
            BusCard(modifier = Modifier.fillMaxWidth()) {
                StationTimeline(trace.nodes.map { TimelineStop(it.label, it.time, it.hint, it.state) })
            }
        }

        if (trace.legs.isNotEmpty()) {
            SectionTitle("分段承运", subtitle = "${trace.legs.size} 段，含中转交接")
            trace.legs.forEach { LegCard(it) }
        }
    }
}

@Composable
private fun LegCard(leg: TransportLeg) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val done = leg.done
    val running = leg.active
    BusCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(BusRadius.m))
                        .background(t.primaryLight),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${leg.seq.coerceAtLeast(1)}",
                        color = t.primaryOnLight,
                        fontSize = (13 * scale).sp,
                        fontWeight = FontWeight.Black,
                    )
                }
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(
                        "${leg.fromStation.ifEmpty { "起点" }} → ${leg.toStation.ifEmpty { "终点" }}",
                        color = t.ink,
                        fontSize = (14 * scale).sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        listOf(leg.plate, leg.driverName).filter { it.isNotEmpty() }.joinToString(" · ")
                            .ifEmpty { "承运信息待分配" },
                        color = t.textSecondary,
                        fontSize = (12 * scale).sp,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                BusTag(leg.statusLabel, tone = leg.statusTone)
            }
            if (leg.transferStation.isNotEmpty()) {
                Text(
                    "在 ${leg.transferStation} 换乘交接",
                    color = t.clayDeep,
                    fontSize = (12 * scale).sp,
                    modifier = Modifier.padding(top = 7.dp),
                )
            }
            // 里程/计划耗时只认后端 distanceKm/durationMinutes，没给就整行不显示（§72 F4）
            leg.legPlanText?.let {
                Text(
                    it,
                    color = t.textSecondary,
                    fontSize = (12 * scale).sp,
                    modifier = Modifier.padding(top = 7.dp),
                )
            }
            RoadLine(progress = if (done) 1f else if (running) 0.55f else -1f, modifier = Modifier.padding(top = 9.dp))
            if (!done) {
                val actionText = leg.action.ifEmpty { "下一步未下发" }
                Text(
                    if (leg.etaMinutes != null) "预计 ${leg.etaMinutes} 分钟 · $actionText"
                    else "后端未给到站分钟 · $actionText",
                    color = t.textTertiary,
                    fontSize = (11.5 * scale).sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}
