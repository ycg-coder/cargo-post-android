package com.shanxiang.buspost.core.map

import com.shanxiang.buspost.data.model.Geo
import com.shanxiang.buspost.data.model.GeoMath
import kotlin.math.abs

/**
 * 一条被批准上路、或已被拒收的真实道路路线。
 *
 * 只有 [Accepted] 里的 [RealRoadRoute] 可以被画出来、被拿去算里程。
 * [Rejected] 必须带原因，页面据此显示"路线不可用 + 原因 + 重试"，不做任何连线示意。
 */
sealed class RouteCheck {
    data class Accepted(val route: RealRoadRoute) : RouteCheck()

    /** reason 面向用户，detail 面向日志与调试 HUD */
    data class Rejected(val reason: String, val detail: String = "") : RouteCheck()
}

/**
 * 真实道路路线：折线、路网里程、路网 ETA 必须同源。
 *
 * [roadDistanceMeters] 为 0 表示来源没给里程 —— 此时页面显示"—"，
 * 绝不允许用两点直线距离补一个数上去。
 */
data class RealRoadRoute(
    val source: RouteSource,
    val crs: CoordinateSystem,
    val points: List<Geo>,
    /** 路网实际驾驶里程（米）；0 = 来源未提供 */
    val roadDistanceMeters: Int = 0,
    /** 路网预计耗时（秒）；0 = 来源未提供 */
    val roadEtaSeconds: Int = 0,
) {
    val available: Boolean get() = points.size >= 2

    /** 折线首末点的直线距离：只用于合理性校验和调试对比，不是里程 */
    val straightDistanceMeters: Int
        get() = if (points.size < 2) 0 else GeoMath.distance(points.first(), points.last()).toInt()

    /** 路网里程 / 首末直线距离：市区绕路通常 1.1~2.5，越界说明数据有问题 */
    val windiness: Double
        get() = if (straightDistanceMeters <= 0 || roadDistanceMeters <= 0) 0.0
        else roadDistanceMeters.toDouble() / straightDistanceMeters

    /**
     * 画到底图上的那一份点：底图与后端站点都是 GCJ-02，来源声明 WGS-84 时必须先换算，
     * 否则整条线会系统性偏出 ~500 米，看起来像"司机偏航"。
     * [points] 保持后端原样，换算只发生在这一层（审计 §17、D-008）。
     */
    val gcj02Points: List<Geo>
        get() = CoordinateTransform.convertAll(points, crs, CoordinateSystem.GCJ02)
}

/**
 * 真实道路校验器：正式链路里任何一条折线都必须过这里，过不了就没有路线可画。
 *
 * 注意 [GeoMath.distance]（Haversine）在本文件里只作为**下界判据**使用：
 * 道路里程不可能短于首末点直线距离，用它来识别"把直线当里程"的假数据。
 * 它不参与、也不得参与任何里程或 ETA 产出。
 */
object RealRoadRouteValidator {

    /** 折线允许的最短长度：低于 2 点根本不构成线 */
    private const val MIN_POINTS = 2

    /** 道路里程与首末直线距离的合理上限，超过基本是坐标或投影出错 */
    private const val MAX_WINDINESS = 12.0

    fun validate(
        sourceRaw: String?,
        points: List<Geo>,
        roadDistanceMeters: Int = 0,
        roadEtaSeconds: Int = 0,
        crsRaw: String? = null,
        stationGeos: List<Geo> = emptyList(),
    ): RouteCheck {
        val source = RouteSource.parse(sourceRaw)
        if (!source.real) {
            val sourceText = if (source == RouteSource.UNKNOWN) "未提供来源" else source.label
            return RouteCheck.Rejected(
                reason = "路线来源不可信（$sourceText）",
                detail = "provider=${sourceRaw.orEmpty().ifEmpty { "<empty>" }} 归一为 $source",
            )
        }
        if (points.size < MIN_POINTS) {
            return RouteCheck.Rejected(
                // 措辞跟着来源走：这道闸门现在同时守后端折线和高德折线，
                // 固定说"后端没有下发"会让高德那条路失败时被念成后端的锅。
                reason = "${source.label}没有返回道路折线",
                detail = "provider=${source.name} points=${points.size}",
            )
        }
        if (!points.all { isValidGeo(it) }) {
            return RouteCheck.Rejected(reason = "道路折线含非法坐标", detail = "存在经纬度越界或为 0 的点")
        }
        if (points.distinct().size < MIN_POINTS) {
            return RouteCheck.Rejected(reason = "道路折线所有点重合", detail = "折线退化成一个点")
        }

        val route = RealRoadRoute(
            source = source,
            crs = CoordinateSystem.parse(crsRaw),
            points = points,
            roadDistanceMeters = roadDistanceMeters.coerceAtLeast(0),
            roadEtaSeconds = roadEtaSeconds.coerceAtLeast(0),
        )

        // 两个点且正好是两个站 = 把站点连了一条线，包装成"真实道路"也不行
        if (route.points.size == 2 && stationGeos.size == 2 && sameShape(route.points, stationGeos)) {
            return RouteCheck.Rejected(
                reason = "所谓路线只是两个站点的连线",
                detail = "provider=${source.name} 折线 == 站点首末坐标",
            )
        }

        val straight = route.straightDistanceMeters
        if (route.roadDistanceMeters > 0 && straight > 0) {
            // 道路不可能比直线短；给出更小的"里程"就是假数
            if (route.roadDistanceMeters < straight) {
                return RouteCheck.Rejected(
                    reason = "路网里程小于首末点直线距离，数据不可信",
                    detail = "road=${route.roadDistanceMeters}m straight=${straight}m",
                )
            }
            if (route.windiness > MAX_WINDINESS) {
                return RouteCheck.Rejected(
                    reason = "路网里程与直线距离比例异常，数据不可信",
                    detail = "windiness=%.1f road=%dm straight=%dm".format(route.windiness, route.roadDistanceMeters, straight),
                )
            }
        }
        return RouteCheck.Accepted(route)
    }

    fun isValidGeo(g: Geo): Boolean =
        !g.lat.isNaN() && !g.lng.isNaN() &&
            abs(g.lat) <= 90.0 && abs(g.lng) <= 180.0 &&
            (g.lat != 0.0 || g.lng != 0.0)

    private fun sameShape(a: List<Geo>, b: List<Geo>): Boolean =
        a.indices.all { abs(a[it].lat - b[it].lat) < 1e-7 && abs(a[it].lng - b[it].lng) < 1e-7 }
}
