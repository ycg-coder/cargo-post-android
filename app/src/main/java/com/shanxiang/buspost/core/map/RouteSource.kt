package com.shanxiang.buspost.core.map

/**
 * 路线数据来源。`real = true` 的来源才允许进入正式链路；
 * 其余来源一律走"路线不可用"，不允许借道渲染。
 *
 * 后端 `AppDriverRouteRespVO.routeProvider` 给的是标准标签
 * （`AMAP` / `OSM_GRAPHHOPPER` / `OSM_ROAD_GRAPH` / `MIXED`，没有路线时为空或 `unavailable`），
 * 白名单与后端 `service/geo/RealRoadProviders` 一一对应，两边必须同步改；
 * `parse` 因此要能吞下大小写与历史写法，不能被字符串形态蒙过去。
 */
enum class RouteSource(val real: Boolean, val label: String) {
    AMAP(true, "高德路网"),
    OSM_GRAPHHOPPER(true, "重庆 OSM · GraphHopper"),
    OSM_ROAD_GRAPH(true, "重庆 OSM · RoadGraph"),
    VALHALLA(true, "Valhalla"),
    CACHE_REAL_ROAD(true, "真实道路离线缓存"),
    MIXED(true, "逐段真实路网混合"),
    EUCLIDEAN(false, "直线估算"),
    STRAIGHT(false, "两点连线"),
    MOCK(false, "模拟数据"),
    DEMO(false, "演示数据"),
    ESTIMATED(false, "经验估算"),
    UNKNOWN(false, "来源未知"),
    ;

    companion object {
        /** 同义词表：后端/历史数据/文档里的各种写法都归一到同一个枚举。键必须是小写归一后的形态 */
        private val ALIASES: Map<String, RouteSource> = mapOf(
            "amap" to AMAP,
            "gaode" to AMAP,
            "高德" to AMAP,
            "osm_graphhopper" to OSM_GRAPHHOPPER,
            "graphhopper" to OSM_GRAPHHOPPER,
            "osm" to OSM_GRAPHHOPPER,
            "osm_road_graph" to OSM_ROAD_GRAPH,
            "roadgraph" to OSM_ROAD_GRAPH,
            "localgraph" to OSM_ROAD_GRAPH,
            "local_graph" to OSM_ROAD_GRAPH,
            "valhalla" to VALHALLA,
            "cache" to CACHE_REAL_ROAD,
            "offline_cache" to CACHE_REAL_ROAD,
            "mixed" to MIXED,
            "euclidean" to EUCLIDEAN,
            "euclidean_fallback" to EUCLIDEAN,
            "haversine" to EUCLIDEAN,
            "greatcircle" to EUCLIDEAN,
            "great_circle" to EUCLIDEAN,
            "straight" to STRAIGHT,
            "straightline" to STRAIGHT,
            "two_point" to STRAIGHT,
            "twopoint" to STRAIGHT,
            "station_join" to STRAIGHT,
            "linear" to STRAIGHT,
            "mock" to MOCK,
            "fake" to MOCK,
            "sim" to MOCK,
            "simulated" to MOCK,
            "simulation" to MOCK,
            "demo" to DEMO,
            "estimated" to ESTIMATED,
            "estimate" to ESTIMATED,
            "approx" to ESTIMATED,
        )

        fun parse(raw: String?): RouteSource {
            val key = raw?.trim()?.lowercase()?.replace('-', '_')?.replace(" ", "") ?: return UNKNOWN
            if (key.isEmpty()) return UNKNOWN
            ALIASES[key]?.let { return it }
            // 直连枚举名（大写形态）也接受，新增来源不必改两处
            return entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: UNKNOWN
        }
    }
}
