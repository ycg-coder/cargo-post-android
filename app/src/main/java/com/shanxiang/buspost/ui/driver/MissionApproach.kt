package com.shanxiang.buspost.ui.driver

import com.shanxiang.buspost.data.model.MissionStop

/**
 * 远近这个数的来源。它决定播报里能不能出现数字：
 * [STRAIGHT] 是两点直线，司机按它开车会走错（要绕田埂），所以它只用来判"到没到跟前"，不出口成里程（审计 §32）。
 */
enum class DistanceSource { BACKEND_ROAD, ENGINE_ROAD, STRAIGHT, NONE }

/** 由外到内四档；[UNKNOWN] 是没有可用定位，不参与排序语义 */
enum class ApproachTier { UNKNOWN, FAR, ADVISORY, PREPARING, AT_STOP }

data class ApproachThresholds(
    val advisoryMeters: Int = 500,
    val preparingMeters: Int = 150,
    val atStopMeters: Int = 60,
    /**
     * 往外跳档要多走的余量。GPS 抖动 ±15 m 会让读数在 60 上下反复穿越阈值，
     * 没有这一档就是同一条路上一分钟响三次。
     */
    val releaseMarginMeters: Int = 30,
)

object MissionApproach {

    /** 越大越远；[ApproachTier.UNKNOWN] 排最外，这样"有定位了"总是被当成往里走、立刻接受 */
    internal fun rank(tier: ApproachTier): Int = when (tier) {
        ApproachTier.AT_STOP -> 0
        ApproachTier.PREPARING -> 1
        ApproachTier.ADVISORY -> 2
        ApproachTier.FAR -> 3
        ApproachTier.UNKNOWN -> 99
    }

    /** 这一档的外沿：从这一档往外跳，要超过它再加余量才算"真的走了" */
    internal fun outerEdge(tier: ApproachTier, th: ApproachThresholds): Double = when (tier) {
        ApproachTier.AT_STOP -> th.atStopMeters.toDouble()
        ApproachTier.PREPARING -> th.preparingMeters.toDouble()
        ApproachTier.ADVISORY -> th.advisoryMeters.toDouble()
        // FAR / UNKNOWN 没有外沿，任何读数都算待在外头
        else -> -1.0
    }

    fun tierOf(meters: Double?, th: ApproachThresholds = ApproachThresholds()): ApproachTier {
        if (meters == null || meters.isNaN() || meters < 0) return ApproachTier.UNKNOWN
        return when {
            meters <= th.atStopMeters -> ApproachTier.AT_STOP
            meters <= th.preparingMeters -> ApproachTier.PREPARING
            meters <= th.advisoryMeters -> ApproachTier.ADVISORY
            else -> ApproachTier.FAR
        }
    }

    /**
     * 这一档要念的一句话；[ApproachTier.FAR] / [ApproachTier.UNKNOWN] 返回 null —— 没话说就别说。
     *
     * 站点名、动作、件数全部取自后端 `stops` 原字段（与 [MissionMarkers] 同一批出口），
     * 后端没给动作名就没有"准备××"这半句，不编一个动作出来。
     * 到点那句只说「可确认到达」，不说「已到达」：`/driver/arrive` 按司机声明受理，本机不替他确认。
     */
    fun announceText(
        stop: MissionStop,
        tier: ApproachTier,
        meters: Double?,
        source: DistanceSource,
    ): String? {
        if (tier == ApproachTier.UNKNOWN || tier == ApproachTier.FAR) return null
        val label = stop.name.ifEmpty { "任务点" }
        val job = with(MissionMarkers) {
            listOfNotNull(stop.actionLabel.ifEmpty { null }, stop.quantityText()).joinToString(" ")
        }
        // 0 米不是"还有 0 米"，是没量出来；formatRoadMeters 对 <=0 的说法是"接口未给"，别把它念进播报里
        val dist = when {
            meters == null || meters.isNaN() || meters < 1.0 -> null
            source == DistanceSource.BACKEND_ROAD -> "道路还有 " + formatRoadMeters(meters.toInt())
            source == DistanceSource.ENGINE_ROAD -> "还有 " + formatRoadMeters(meters.toInt())
            else -> null
        }
        return when (tier) {
            ApproachTier.AT_STOP -> if (job.isEmpty()) "已到「$label」附近，可确认到达"
            else "已到「$label」附近，$job，可确认到达"
            ApproachTier.PREPARING -> listOfNotNull(
                "快到「$label」" + (dist?.let { "，$it" } ?: ""),
                if (job.isEmpty()) null else "准备$job",
            ).joinToString("，")
            else -> listOfNotNull(
                "「$label」" + (dist?.let { " $it" } ?: " 在前方"),
                job.ifEmpty { null },
            ).joinToString("，")
        }
    }
}

/**
 * 每来一帧定位问一次"现在该说什么"。
 *
 * 一个任务点的一档只说一次（[spoken]），换目标点或重挂目标才重新武装 ——
 * 否则司机在阈值线上堵会儿车就要被同一句话轰炸。
 */
class ApproachTracker(private val th: ApproachThresholds = ApproachThresholds()) {

    private var armedStopId = Long.MIN_VALUE
    private var tier = ApproachTier.UNKNOWN
    private val spoken = mutableSetOf<String>()

    /** 班次重新拉取、或目标点变了时调用：让下一次靠近能重新说话 */
    fun reset() {
        armedStopId = Long.MIN_VALUE
        tier = ApproachTier.UNKNOWN
        spoken.clear()
    }

    /** @return 这一帧要播的话；null 表示闭嘴 */
    fun next(stop: MissionStop, meters: Double?, source: DistanceSource): String? {
        if (stop.stationId != armedStopId) {
            armedStopId = stop.stationId
            tier = ApproachTier.UNKNOWN
            spoken.clear()
        }
        val raw = MissionApproach.tierOf(meters, th)
        if (raw == ApproachTier.UNKNOWN) {
            tier = raw
            return null
        }
        val goingOutward = MissionApproach.rank(raw) > MissionApproach.rank(tier)
        if (goingOutward && (meters ?: 0.0) <= MissionApproach.outerEdge(tier, th) + th.releaseMarginMeters) {
            return null
        }
        tier = raw
        if (!spoken.add("${stop.stationId}:${raw.name}")) return null
        return MissionApproach.announceText(stop, raw, meters, source)
    }
}
