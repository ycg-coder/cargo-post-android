package com.shanxiang.buspost.data.repo

import com.shanxiang.buspost.core.map.RouteCacheRead
import com.shanxiang.buspost.core.map.RouteRepository

/**
 * "这一班这次没问到，但本机存着什么"这句话的唯一出口（赛道 ⑭ / D-059）。
 *
 * 这份缓存现在只有一个生产读点，就是这里，而且它**只披露、不供几何**：
 * 命中时也只在失败原因后面补一句"本机有一条什么时候、由谁给的几何"，
 * 折线仍然不画 —— 把离线几何接进任务地图要给"无路线三态"（F-3 ⑧）加第四格，
 * 那句话的判据族（MR/MV）要同轮重锚，不在这轮顺手做，登记在册（`D-⑭-2`）。
 *
 * ⚠ 三格各自一句、不许并格，也不许把"有一条缓存"念成"路线还在"：
 * [RouteCacheRead.Absent] 那一句原样返回 —— 没有缓存就没有额外可说的，不编。
 * 命中那格直接打 `providerRaw` 原文、不给"后端没说来源"的兜底：写进缓存的前提是
 * [RouteSource] 归一后 `real = true`，而空串只会归一成 `UNKNOWN` ⇒ "命中却没有来源原文"这条路走不到，
 * 为它补一句就是给不存在的分支写文案。
 */
internal fun routeFailureTextOf(
    key: String,
    cause: String,
    nowMillis: Long = System.currentTimeMillis(),
): String = when (val read = RouteRepository.cached(key, nowMillis)) {
    is RouteCacheRead.Hit ->
        "$cause（本机存有一条 ${offlineCacheAgeText(read.ageMillis)}前抓取的${read.entry.route.source.label}几何，" +
            "后端当时给的来源原文是「${read.entry.providerRaw}」；这份缓存几何还没接进地图）"
    is RouteCacheRead.Stale ->
        "$cause（本机有过一条缓存几何，是 ${offlineCacheAgeText(read.ageMillis)}前抓取的，" +
            "已超过本机自定的新鲜度口径，读到就删了它）"
    RouteCacheRead.Absent -> cause
}

/**
 * 时长的中文口径：只由"本机现在"减"本机当时写入的那一刻"得到，不碰网络、不碰后端时间。
 * 分钟以下一律"不足 1 分钟"，避免出现"0 分钟前"这种读起来像"刚刚 yet 又要重试"的说法。
 */
internal fun offlineCacheAgeText(ageMillis: Long): String {
    val safe = if (ageMillis < 0) 0 else ageMillis
    val minutes = safe / 60_000
    return when {
        minutes < 1 -> "不足 1 分钟"
        minutes < 60 -> "$minutes 分钟"
        else -> "${minutes / 60} 小时 ${minutes % 60} 分"
    }
}
