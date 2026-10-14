package com.shanxiang.buspost.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.shanxiang.buspost.data.model.Geo
import com.shanxiang.buspost.data.model.GeoMath
import com.shanxiang.buspost.data.model.Station

/**
 * 步行去站点：这一族判定的唯一出处（实时公交页的内嵌详情与线路详情页共用，两处各写一遍必然分家）。
 *
 *  口径：交给高德算**真实步行路线**，App 自己不算路、也不拿直线距离冒充路线长度。
 * 页面上一切"约 X 公里"若来自 [GeoMath.distance]，都必须带"直线"字样（§72 同一条规矩）。
 */

/** 有可用坐标的站点：两头都非零才算。`lat != 0 || lng != 0` 会把只填了一头的脏行当成有效点。 */
private fun placeable(stations: List<Station>): List<Station> =
    stations.filter { it.geo.lat != 0.0 && it.geo.lng != 0.0 }

/**
 * 离你最近的那个站。**按真实远近挑，不按站序挑** ——
 * 旧写法是"这条线时间轴里第一个 800 米内的站，没有就退回线路起点站"，
 * 于是从首页点进详情的人，按钮上写的是"步行导航到「上海城」"，而起点站在三公里外。
 */
internal fun nearestWalkTarget(stations: List<Station>, me: Geo?): Station? {
    val ok = placeable(stations)
    if (me == null || ok.isEmpty()) return null
    return ok.minByOrNull { GeoMath.distance(me, it.geo) }
}

/**
 * 唤起步行导航：优先本机高德（`t=2` 是步行，`dev=0` 声明传入已是 GCJ-02 —— 全站坐标都已在
 * [com.shanxiang.buspost.core.loc.Locator.toGcj02] 换算过，这里再转一次就是双重偏移），
 * 没装高德退到网页版。**返回 false 表示两条路都没走通**，调用方要如实说，不许静默。
 *
 * 收 (name, geo) 而不是 Station：调用方手里可能只有一张线路卡上的"站名 + 坐标"，
 * 为了唤起跑导航去现造一个假 id 的 Station，等于把数据模型编进交互层。
 */
internal fun openWalkRoute(context: Context, name: String, geo: Geo): Boolean {
    val appUri = Uri.parse(
        "amapuri://route/plan/?dlat=${geo.lat}&dlon=${geo.lng}" +
            "&dname=${Uri.encode(name)}&dev=0&t=2",
    )
    // 只接 ActivityNotFoundException：这里唯一预期的失败就是"这台机器没有能处理这个 intent 的应用"。
    // 宽口径的 catch 会把 SecurityException 之类的真问题一起吞成"退到网页版"（RC-a/异常可信化两本都钉这个）。
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, appUri).apply {
                setPackage("com.autonavi.minimap")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
        return true
    } catch (e: ActivityNotFoundException) {
        // 没装高德 → 退到网页版
    }
    val webUri = Uri.parse(
        "https://uri.amap.com/navigation?to=${geo.lng},${geo.lat}," +
            "${Uri.encode(name)}&mode=walk&coordinate=gaode",
    )
    return try {
        context.startActivity(Intent(Intent.ACTION_VIEW, webUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
