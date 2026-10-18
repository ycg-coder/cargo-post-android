package com.shanxiang.buspost.ui.driver

import com.shanxiang.buspost.data.model.MissionStop

/**
 * 到车**持续**提醒的纯层（D-170）。
 *
 * 用户口径⑤原文：「车辆要到了会持续弹窗提醒司机该做什么直到司机完成，但是不能影响驾驶」。
 * 这一句里两半是互相顶的：模态弹窗会吃掉地图手势、吃掉返回键、把高德的转向卡压在下面
 * （2026-10-10 真机就为"自绘条压住放大图 50%"红过一次），行车中那是最坏的一种控件。
 * 所以这一格落成**常驻的非模态一行 + 到点重复出声**："持续"落在两处 ——
 * 不办完不下线（那一行），以及没办完会再说一遍（出声，有间隔、有上限）。
 * 这个取舍可以被推翻，写在这里就是为了推翻时知道要改哪一行：
 * 要真弹窗改的是 `WorkbenchScreen` 那一格的挂点，要改节奏改下面那两个常数。
 *
 * 这一层不 import Android、不 import SDK、不读时钟（时间由调用方递进来），
 * 所以"什么时候响、响第几遍、换站怎么清账"全能在 JVM 里逐条断言。
 */
object ApproachReminder {

    /** 同一站两次出声的最小间隔：乡道 60 米内堵会儿车，不该被同一句话轰炸 */
    const val REPEAT_INTERVAL_MS = 60_000L

    /** 同一站最多出声几遍：三遍还不办就不再响，靠常驻那一行继续说话 */
    const val MAX_REPEATS = 3

    /**
     * "要到了"的档：150 米（[ApproachTier.PREPARING]）与 60 米（[ApproachTier.AT_STOP]）。
     * 500 米那一档不提醒 —— 乡道上 500 米可能还要过两个弯，那时候说"到站要做什么"太早，
     * 而且那一档 [ApproachTracker] 已经说过一句"快到了"。
     */
    fun isNear(tier: ApproachTier): Boolean =
        tier == ApproachTier.PREPARING || tier == ApproachTier.AT_STOP

    /**
     * 常驻那一行在不在：车在近处 **且** 后端没说这一站办完。
     * 失败态（8）也不提醒 —— 那一格要的是"回去处理异常"，不是"到站办业务"，
     * 而"办完"只由后端回读得到（`/driver/arrive` 之后重读 `stops`），本机不替他判。
     */
    fun shouldShow(stop: MissionStop?, tier: ApproachTier): Boolean =
        stop != null && isNear(tier) && !stop.isDone && !stop.isFailed

    /** 那一行念什么：站名 · 动作 · 件数/人数。三个字段全取后端，本机一个字都不补 */
    fun cardText(stop: MissionStop): String = with(MissionMarkers) {
        listOfNotNull(
            stop.name.ifEmpty { "任务点#${stop.stationId}" },
            stop.actionLabel.ifEmpty { null },
            stop.quantityText(),
        ).joinToString(" · ")
    }

    /**
     * 这一帧该不该挂着那一格，以及挂的是哪一站 —— **"要不要提醒"只有这一个判点**。
     *
     * 两个面各自把自己的口径递进来（工作台是后端口径 `ApproachFeed.of`，全屏导航是引擎口径
     * `ApproachFeed.fromStops`），判"近不近、办没办完"这件事只有这一份，
     * 免得出现"卡片上在提醒、底部那一行说没到"这种同屏两话。
     */
    fun stopOf(frame: ApproachFrame?): MissionStop? {
        val ready = frame as? ApproachFrame.Ready ?: return null
        return ready.stop.takeIf { shouldShow(it, MissionApproach.tierOf(ready.meters)) }
    }
}

/**
 * 重复出声那本账。与 [ApproachTracker] 故意分成两本，不是偷懒：
 * 那一本管"往里走了一档，说一句**新的**"（每站每档一次，防的是阈值线上堵车轰炸）；
 * 这一本管"到了这一站还没办完，过一会儿把**同一句**再说一遍"（每站最多 [ApproachReminder.MAX_REPEATS] 遍）。
 * 合成一本的话，"换站"与"办完"两种清账时机就会互相顶 —— 而这两件事在真机上确实会同时发生
 * （司机在 60 米里点了确认到达，站没换、状态先变）。
 */
class ReminderTracker {

    private var stopId = Long.MIN_VALUE
    private var repeats = 0
    private var lastAtMs = 0L

    /** 换班 / 手动重挂目标时清账（与 `ApproachTracker.reset` 同一时机由调用方决定） */
    fun reset() {
        stopId = Long.MIN_VALUE
        repeats = 0
        lastAtMs = 0L
    }

    /**
     * 这一拍要不要出声。[stop] 传的是 [ApproachReminder.stopOf] 判出来的那一站（null = 此刻不该提醒）。
     *
     * ⚠ 第一次看到这一站时**不出声**：那一句是 [ApproachTracker] 的活（它按档位说"快到了 / 可确认到达"），
     * 这里只负责"说过之后还没办，过一阵把同一句再说一遍"。两本同时开口就是同一秒两句话。
     */
    fun next(stop: MissionStop?, nowMs: Long): String? {
        if (stop == null) return null
        if (stop.stationId != stopId) {
            stopId = stop.stationId
            repeats = 0
            lastAtMs = nowMs      // 第一次看到这一站只记时刻，不响
            return null
        }
        if (repeats >= ApproachReminder.MAX_REPEATS) return null
        if (nowMs - lastAtMs < ApproachReminder.REPEAT_INTERVAL_MS) return null
        repeats += 1
        lastAtMs = nowMs
        return ApproachReminder.cardText(stop)
    }
}
