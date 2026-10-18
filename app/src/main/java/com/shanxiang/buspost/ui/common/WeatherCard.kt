package com.shanxiang.buspost.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanxiang.buspost.core.ui.BusCard
import com.shanxiang.buspost.core.ui.BusIcons
import com.shanxiang.buspost.core.ui.BusRadius
import com.shanxiang.buspost.core.ui.LocalBusFontScale
import com.shanxiang.buspost.core.ui.LocalBusTokens
import com.shanxiang.buspost.core.ui.LocalBusTouch
import com.shanxiang.buspost.data.model.WeatherInfo

/**
 * 天气胶囊 —— 对应小程序 .weather-pill（站牌头内的一行浓缩）。
 * 数据源优先级与小程序一致：高德天气（Web服务 key）→ open-meteo → 本地模拟（mock=true 时必须标注）。
 */
@Composable
fun WeatherPill(
    weather: WeatherInfo,
    expanded: Boolean,
    locationName: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val touch = LocalBusTouch.current
    // 三档来源都没给结论：这行只报"未取到"，不铺一个 0°C 的假读数，也不挂"4日预报"的假可点态
    val unavailable = weather.text.isEmpty() && weather.forecast.isEmpty()
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(BusRadius.pill))
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.22f))
            .border(
                1.dp,
                androidx.compose.ui.graphics.Color.White.copy(alpha = 0.30f),
                RoundedCornerShape(BusRadius.pill),
            )
            .then(if (unavailable) Modifier else Modifier.clickable(onClick = onToggle))
            // 真机 v163 量到这颗胶囊的命中框只有 91px=33dp（13sp 字 + 上下 7dp 留白），
            // 而它写着"4日预报"是在承诺可点 —— 抬到令牌下限（普通 48dp / 驾驶 56dp），字与留白的排布不动。
            // ⚠ 先写过 defaultMinSize，真机 v173 复量只到 105px=38dp（没到下限）；换成 heightIn 是把 min
            //   **压进子节点的测量约束**而不是量完再补。改后的实际像素以 UB-k 的归档 dump 为准。
            .heightIn(min = touch.min)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!unavailable) {
            Icon(BusIcons.weatherOf(weather.text), null, tint = t.onPrimary, modifier = Modifier.size(15.dp))
        }
        Text(
            if (unavailable) {
                "天气未取到"
            } else {
                buildString {
                    append("${weather.temperature}°C ${weather.text}")
                    if (locationName.isNotEmpty()) append(" · $locationName")
                }
            },
            color = t.onPrimary,
            fontSize = (13 * scale).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (unavailable) Modifier else Modifier.weight(1f).padding(start = 7.dp),
        )
        if (!unavailable) {
            Text(
                if (expanded) "收起" else "4日预报",
                color = t.onPrimary,
                fontSize = (11.5f * scale).sp,
            )
            Icon(
                if (expanded) BusIcons.expandLess else BusIcons.expandMore,
                contentDescription = null,
                tint = t.onPrimary,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

/**
 * 天气详情卡 —— 对应小程序 .weather-card（默认收起，点胶囊展开）。
 * 左侧：金色圆底现象图标 + 温度/现象 + 湿度风力；右侧：农事提示；底部：4 日预报。
 */
@Composable
fun WeatherDetailCard(
    weather: WeatherInfo,
    loading: Boolean,
    updateTime: String,
    modifier: Modifier = Modifier,
) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    BusCard(
        modifier = modifier.fillMaxWidth(),
        border = true,
    ) {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    // 金色圆底现象图标（稻谷金浅底承载深金图标，对比度达标）
                    Box(
                        Modifier.size(48.dp).clip(CircleShape).background(t.goldLight),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            BusIcons.weatherOf(weather.text),
                            contentDescription = weather.text,
                            tint = t.goldDeep,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                    Column(Modifier.padding(start = 11.dp)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                "${weather.temperature}°",
                                color = t.primaryOnLight,
                                fontSize = (26 * scale).sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                weather.text,
                                color = t.textSecondary,
                                fontSize = (14 * scale).sp,
                                modifier = Modifier.padding(start = 7.dp, bottom = 3.dp),
                            )
                        }
                        Row(Modifier.padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            if (weather.humidity.isNotEmpty()) WeatherTag(weather.humidity)
                            if (weather.wind.isNotEmpty()) WeatherTag(weather.wind)
                        }
                        // 更新状态：加载中 / 三档来源都没给（现在不再兜一组本地数，所以只说没取到）
                        // ⚠ 时间与来源**都要露出来**：旧写法是 `updateTime` 一非空就把来源整行盖掉，
                        //   于是高德天气限流那天，屏上完全看不出这组数其实来自 open-meteo（D-173 真机抓的）。
                        val meta = when {
                            loading -> "更新中…"
                            weather.mock -> "天气未取到（高德与 open-meteo 都没返回）" +
                                if (weather.primaryFailure.isEmpty()) "" else "；${weather.primaryFailure}"
                            else -> listOfNotNull(
                                updateTime.ifEmpty { null },
                                if (weather.source.isEmpty()) null else "来源 ${weather.source}",
                                weather.primaryFailure.ifEmpty { null }?.let { "主源没用上：$it" },
                            ).joinToString(" · ")
                        }
                        if (meta.isNotEmpty()) {
                            Text(
                                meta,
                                color = if (weather.mock) t.clayDeep else t.textTertiary,
                                fontSize = (11 * scale).sp,
                                modifier = Modifier.padding(top = 5.dp),
                            )
                        }
                    }
                }
                if (weather.tips.isNotEmpty()) {
                    Text(
                        weather.tips,
                        color = t.textSecondary,
                        fontSize = (11.5f * scale).sp,
                        modifier = Modifier.width(120.dp),
                    )
                }
            }

            if (weather.forecast.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    weather.forecast.take(4).forEach { d ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(d.day, color = t.textTertiary, fontSize = (11.5f * scale).sp)
                            Spacer(Modifier.height(4.dp))
                            Icon(
                                BusIcons.weatherOf(d.condition.ifEmpty { d.icon }),
                                contentDescription = d.condition,
                                tint = t.goldDeep,
                                modifier = Modifier.size(19.dp),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${d.tempLo}° ${d.tempHi}°",
                                color = t.ink,
                                fontSize = (11.5f * scale).sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherTag(text: String) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    Box(
        Modifier
            .clip(RoundedCornerShape(BusRadius.s))
            .background(t.track)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(text, color = t.textSecondary, fontSize = (11 * scale).sp, maxLines = 1)
    }
}
