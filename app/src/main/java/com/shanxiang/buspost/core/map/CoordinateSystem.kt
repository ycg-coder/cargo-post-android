package com.shanxiang.buspost.core.map

import com.shanxiang.buspost.data.model.Geo
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 坐标系。后端与高德是 GCJ-02，OSM/GraphHopper 是 WGS-84，百度另有一套 BD-09。
 * 混用会在境内造成 50~500 米系统性偏移，看起来像"偏航"，其实是坐标系错了。
 */
enum class CoordinateSystem {
    WGS84,
    GCJ02,
    BD09,
    ;

    companion object {
        fun parse(raw: String?): CoordinateSystem = when (raw?.trim()?.uppercase()?.replace("-", "")?.replace("_", "")) {
            "GCJ02", "GCJ", "AMAP", "GAODE" -> GCJ02
            "WGS84", "WGS", "OSM", "GPS" -> WGS84
            "BD09", "BAIDU" -> BD09
            else -> GCJ02 // 后端契约默认值：AppDriverRouteRespVO 声明的是 GCJ-02
        }
    }
}

/**
 * 坐标系换算。全项目只有这一个入口，业务代码不得手写偏移公式。
 *
 * 算法是国内公开的 GCJ-02 偏移标准实现（Krasovsky 椭球参数 + 正弦扰动），
 * 精度约 1~2 厘米，足够把 GraphHopper 的 WGS-84 折线画到高德底图上。
 * 境外点不偏移：GCJ-02 只在中国境内定义。
 */
object CoordinateTransform {

    private const val A = 6378245.0 // 长半轴
    private const val EE = 0.00669342162296594323 // 偏心率平方
    private const val X_PI = Math.PI * 3000.0 / 180.0

    fun convert(geo: Geo, from: CoordinateSystem, to: CoordinateSystem): Geo {
        if (from == to || !inChina(geo)) return geo
        val gcj = when (from) {
            CoordinateSystem.GCJ02 -> geo
            CoordinateSystem.WGS84 -> wgs84ToGcj02(geo)
            CoordinateSystem.BD09 -> bd09ToGcj02(geo)
        }
        return when (to) {
            CoordinateSystem.GCJ02 -> gcj
            CoordinateSystem.WGS84 -> gcj02ToWgs84(gcj)
            CoordinateSystem.BD09 -> gcj02ToBd09(gcj)
        }
    }

    fun convertAll(points: List<Geo>, from: CoordinateSystem, to: CoordinateSystem): List<Geo> =
        if (from == to) points else points.map { convert(it, from, to) }

    /** 粗略国境内判断：只用于决定是否偏移，不作为业务边界 */
    fun inChina(geo: Geo): Boolean {
        if (abs(geo.lat) > 55.827 || abs(geo.lng) > 135.088) return false
        return geo.lng in 72.004..137.8347 && geo.lat in 0.8293..55.8271
    }

    fun wgs84ToGcj02(geo: Geo): Geo {
        if (!inChina(geo)) return geo
        var dLat = transformLat(geo.lng - 105.0, geo.lat - 35.0)
        var dLng = transformLng(geo.lng - 105.0, geo.lat - 35.0)
        val radLat = geo.lat / 180.0 * Math.PI
        var magic = sin(radLat)
        magic = 1 - EE * magic * magic
        val sqrtMagic = sqrt(magic)
        dLat = dLat * 180.0 / ((A * (1 - EE)) / (magic * sqrtMagic) * Math.PI)
        dLng = dLng * 180.0 / (A / sqrtMagic * Math.cos(radLat) * Math.PI)
        return Geo(geo.lat + dLat, geo.lng + dLng)
    }

    /** 反向换算用两次正算逼近，误差远小于一个车道宽度 */
    fun gcj02ToWgs84(gcj: Geo): Geo {
        if (!inChina(gcj)) return gcj
        var wgs = gcj
        var guess = wgs84ToGcj02(wgs)
        var dLat = guess.lat - gcj.lat
        var dLng = guess.lng - gcj.lng
        var i = 0
        while ((abs(dLat) > 1e-9 || abs(dLng) > 1e-9) && i < 30) {
            wgs = Geo(wgs.lat - dLat, wgs.lng - dLng)
            guess = wgs84ToGcj02(wgs)
            dLat = guess.lat - gcj.lat
            dLng = guess.lng - gcj.lng
            i++
        }
        return wgs
    }

    fun gcj02ToBd09(geo: Geo): Geo {
        val z = sqrt(geo.lng * geo.lng + geo.lat * geo.lat) + 0.00002 * sin(geo.lat * X_PI)
        val theta = Math.atan2(geo.lat, geo.lng) + 0.000003 * cos(geo.lng * X_PI)
        // 百度在这一层之上再加固定偏移（纬度 +0.006、经度 +0.0065），少加就是约 700 米的系统性偏差
        return Geo(z * sin(theta) + 0.006, z * cos(theta) + 0.0065)
    }

    fun bd09ToGcj02(geo: Geo): Geo {
        val x = geo.lng - 0.0065
        val y = geo.lat - 0.006
        val z = sqrt(x * x + y * y) - 0.00002 * sin(y * X_PI)
        val theta = Math.atan2(y, x) - 0.000003 * cos(x * X_PI)
        return Geo(z * sin(theta), z * cos(theta))
    }

    private fun transformLat(x: Double, y: Double): Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * Math.PI) + 20.0 * sin(2.0 * x * Math.PI)) * 2.0 / 3.0
        ret += (20.0 * sin(y * Math.PI) + 40.0 * sin(y / 3.0 * Math.PI)) * 2.0 / 3.0
        ret += (160.0 * sin(y / 12.0 * Math.PI) + 320.0 * sin(y * Math.PI / 30.0)) * 2.0 / 3.0
        return ret
    }

    private fun transformLng(x: Double, y: Double): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * Math.PI) + 20.0 * sin(2.0 * x * Math.PI)) * 2.0 / 3.0
        ret += (20.0 * sin(x * Math.PI) + 40.0 * sin(x / 3.0 * Math.PI)) * 2.0 / 3.0
        ret += (150.0 * sin(x / 12.0 * Math.PI) + 300.0 * sin(x / 30.0 * Math.PI)) * 2.0 / 3.0
        return ret
    }
}
