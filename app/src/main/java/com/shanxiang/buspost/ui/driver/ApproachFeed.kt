package com.shanxiang.buspost.ui.driver

import com.shanxiang.buspost.data.model.MissionStop
import com.shanxiang.buspost.data.model.RouteGuide

/**
 * 到近播报这一帧的输入：**同一个任务点 + 到这个点的道路里程 + 这个里程的口径**。
 *
 * 拆成 Ready / Silent 两支而不是返回 null，是因为"这一帧不说话"必须能在屏幕上说得出原因
 * （硬规则「没取到就说不取到」）；静默的两种原因完全不同：后端没点名、后端没给里程。
 */
sealed class ApproachFrame {
    data class Ready(
        val stop: MissionStop,
        val meters: Double,
        val source: DistanceSource,
    ) : ApproachFrame()

    /** @param reason 这一帧为什么不说话，直接是司机能看懂的那句 */
    data class Silent(val reason: String) : ApproachFrame()
}

/**
 * 谁和谁配对：`[nextStopRoadDistanceMeters]` 这段道路里程，只有后端自己点名的那个任务点能配它。
 *
 * 两条可用口径（都是道路值，直线值一律不进这里 —— 见审计 §32 与 [DistanceSource.STRAIGHT]）：
 * ① **引擎口径**：本机正在导到某个任务点时，高德那帧 `remainMeters` 就是"到这个点"的剩余道路里程，
 *    配对由本机上一次的算路目的地决定，实时；
 * ② **后端口径**：`nextStopName` 与 `nextStopRoadDistanceMeters` 出自后端同一次循环
 *    （`DriverAppServiceImpl.fillNextActionAndNav:649-717`），配对由后端说，实时性跟着 `/driver/route` 的刷新。
 *
 * 禁止的做法：客户端自己挑一个"下一个没做完的点"（`nextNavigableStop`）去配后端那段里程 ——
 * 后端的 `next` 是第一个 status≠7 的明细，不看坐标；客户端那个还额外要求 `hasGeo`，
 * 两者在"后端认为下一站没坐标"时就会错开，一说一配就串站。
 */
object ApproachFeed {

    fun fromEngine(guide: RouteGuide, navigatingStopId: Long, engineRemainMeters: Int?): ApproachFrame {
        val stop = guide.stops.firstOrNull { it.stationId == navigatingStopId }
            ?: return ApproachFrame.Silent("本机导航的目的地不在后端任务点里，这段剩余里程配不到点")
        if (engineRemainMeters == null || engineRemainMeters <= 0) {
            return ApproachFrame.Silent("高德这一帧没给剩余里程")
        }
        return ApproachFrame.Ready(stop, engineRemainMeters.toDouble(), DistanceSource.ENGINE_ROAD)
    }

    fun fromBackend(guide: RouteGuide): ApproachFrame {
        val name = guide.nextStopName
        if (name.isEmpty()) {
            return ApproachFrame.Silent(
                if (guide.nextActionName.isEmpty()) "后端没给下一任务点，也没有可配的道路里程"
                else "后端说下一任务是「${guide.nextActionName}」，但没点名是哪个任务点",
            )
        }
        if (guide.nextStopRoadDistanceMeters <= 0) {
            return ApproachFrame.Silent("后端点名了「$name」，但这一段没有真实道路里程")
        }
        // 名字能对上未完成任务点时，动作与件数一起带上（对不上就只说后端给的名字与动作话术，不编件数）
        val matched = guide.stops.firstOrNull { !it.isDone && it.name == name }
            ?: MissionStop(name = name, actionName = guide.nextActionName)
        return ApproachFrame.Ready(matched, guide.nextStopRoadDistanceMeters.toDouble(), DistanceSource.BACKEND_ROAD)
    }

    /** 引擎口径优先（实时），拿不到再退后端口径；两支都空时才静默 */
    fun of(
        guide: RouteGuide,
        navigatingStopId: Long?,
        engineRemainMeters: Int?,
    ): ApproachFrame {
        if (navigatingStopId != null) {
            val byEngine = fromEngine(guide, navigatingStopId, engineRemainMeters)
            if (byEngine is ApproachFrame.Ready) return byEngine
        }
        return fromBackend(guide)
    }
}
