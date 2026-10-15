package com.shanxiang.buspost.ui.user

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanxiang.buspost.core.ui.BusRadius
import com.shanxiang.buspost.core.ui.BusTag
import com.shanxiang.buspost.core.ui.BusTone
import com.shanxiang.buspost.core.ui.LocalBusTokens
import com.shanxiang.buspost.core.ui.LocalBusFontScale
import com.shanxiang.buspost.data.model.BusLine
import com.shanxiang.buspost.data.model.Station

/**
 * 站牌（对标车来了/高德："这一站途经哪些车、几点到"）。
 *
 * 这是全站此前缺的那一根轴：报文里既有"线路→经停站"（`BusLine.stations`，
 * 由 `points` 解析，Models.kt:873），每个站上又带 `plannedMinutes`（Models.kt:497/:532），
 * 但界面只做过"选一条线、看它停哪些站"，从没反过来按站聚合过。
 * 纯客户端就能补，不需要新接口。
 */
data class BoardArrival(
    val routeId: String,
    val routeName: String,
    val terminal: String,
    /** 计划到站分钟；后端没给这个键时为 null —— 不许拿 0 冒充"马上到" */
    val minutes: Int?,
    /** 这一站是不是线路的起点站（起点站念"首发"比念"0 分钟"诚实） */
    val isOrigin: Boolean,
)

/**
 * 按站名聚合途经线路。[lines] 是已经取回来的附近线路，[stationName] 要查的那一站。
 *
 * 只认**站名精确相等**：这一带的站点名有重名（`/send/stations` 1805 行里名字可重复、id 才唯一），
 * 但附近线路里能同时出现同名站的，本来就是"同一个位置的不同叫法"这一种情况，
 * 按 id 反而配不上 —— 线路报文里的站是 `points` 解出来的，没有 id。
 */
fun stationBoard(lines: List<BusLine>, stationName: String): List<BoardArrival> {
    if (stationName.isBlank()) return emptyList()
    val out = ArrayList<BoardArrival>()
    for (l in lines) {
        val stops = l.stations
        val idx = stops.indexOfFirst { it.name == stationName }
        if (idx < 0) continue
        val minutes = stops[idx].plannedMinutes
        out += BoardArrival(
            routeId = l.id,
            routeName = l.name,
            // 不论这一站在线路的哪一头，车都是**开往线路终点**；曾经写成 `else l.from`，
            // 那是把"从哪来"当"开往哪去"念给用户（用例抓出来的）。
            terminal = l.to,
            minutes = if (idx == 0) null else if (minutes > 0) minutes else null,
            isOrigin = idx == 0,
        )
    }
    // 有分钟数的排前面，且按"越早到越上面"；没给分钟数的整体沉底，彼此保持后端顺序
    return out.sortedWith(
        compareBy({ a: BoardArrival -> if (a.minutes == null) 1 else 0 }, { a -> a.minutes ?: Int.MAX_VALUE })
    )
}

/** 站牌上一行该怎么说：三种情况三种说法，不许把"后端没给"说成"0 分钟" */
fun BoardArrival.arrivalText(): String = when {
    isOrigin -> "首发站 · 从这上车"
    minutes != null -> "计划 $minutes 分钟"
    else -> "到站时间没给"
}

@Composable
fun StationBoardCard(
    station: Station,
    arrivals: List<BoardArrival>,
    modifier: Modifier = Modifier,
) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "站牌 · ${station.name}",
                color = t.ink,
                fontSize = (15 * scale).sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            BusTag("${arrivals.size} 条途经", tone = BusTone.NEUTRAL)
        }
        if (arrivals.isEmpty()) {
            // 空态必须说清是"这些附近线路里没查到停靠这一站的"，不是"这一站没车经过"
            Text(
                "附近线路里没查到停靠这一站的班次（换一站看看，或在下面搜线路名）",
                color = t.textSecondary,
                fontSize = (12 * scale).sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        arrivals.take(8).forEach { a ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    a.routeName,
                    color = t.ink,
                    fontSize = (13.5f * scale).sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "开往 ${a.terminal}",
                    color = t.textSecondary,
                    fontSize = (11.5f * scale).sp,
                    modifier = Modifier.padding(end = 8.dp),
                )
                BusTag(
                    a.arrivalText(),
                    tone = if (a.minutes != null) BusTone.PRIMARY else BusTone.NEUTRAL,
                )
            }
        }
        if (arrivals.size > 8) {
            Text(
                "另有 ${arrivals.size - 8} 条",
                color = t.textTertiary,
                fontSize = (11 * scale).sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }
    }
}
