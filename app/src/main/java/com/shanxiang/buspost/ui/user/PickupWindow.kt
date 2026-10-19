package com.shanxiang.buspost.ui.user

import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 「最早取货时间」这一格的判定（页面只渲染这里的结论；分工同 #54 乘车安排、#55 票面、#56 钱包）。
 *
 * 契约是逐行读出来的，三条决定了这一格能说什么：
 * 1. **这个键只能写、这一版查不回来**：`AppSendOrderCreateReqVO.java:76` 收 `earliestPickupTime`，
 *    `TransportOrderServiceImpl.java:149` 落进 `transport_order.earliest_pickup_time`，
 *    而 `AppSendOrderRespVO.java`（`/send/page`、`/send/track` 共用）此前**没有**这个键
 *    ⇒ 提交之后用户在"我的寄货"里看不到自己填了什么，界面不许把本机缓存念成后端回读。
 * 2. **填了会真的改变派单**，不是备注：`DispatchServiceImpl.java:4076-4090` 拿它筛任务窗口
 *    （`earliestPickupTime` 晚于窗口结束 ⇒ 本批不派，理由串"取货时间晚于窗口结束"），
 *    `:3513` 又把它作为 `readyTime` 下发给算法（注释原话：不送进算法就只能按批窗口判，迟到单照样被报成可行）。
 * 3. **不填 = 不设下限**：`AlgorithmOrderDTO.java:74` "没填即 null、算法不凭空判" ⇒ 未选时整个键不发出去，
 *    而不是发 0 或发现在（0 会被后端解成 1970-01-01，见下）。
 *
 * 出网格式：后端把 `LocalDateTime` 注册成 **Epoch 毫秒数字**
 * （`TimestampLocalDateTimeDeserializer.java:24-27` 走 `Instant.ofEpochMilli(p.getValueAsLong())`）。
 * ⚠ 那一行对**非数字串不报错、静默返回 0** ⇒ 发 "2026-10-13 15:00:00" 这种串不会失败，
 *   会安静地变成 1970 年，界面还显示"已填写"。这一页的写法是唯一形状：`Long` 毫秒。
 */

/** 服务区域时区：出网的毫秒按它算（与 `BusScreen.kt:890` 同一口径，不跟设备时区走） */
internal val PICKUP_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")

/** 可选天数：只有"今天起 3 天"，再远的班次后端没有对应生产者可保证，本机不承诺 */
internal const val PICKUP_DAY_COUNT = 3

/** 未选那一格的文案：这是"不限制"，不是"今天随时"，也不许被念成后端记了个时间 */
internal const val PICKUP_UNSET_TEXT = "随时可取（不限制最早取货时间）"

/** 回读缺席那一格：没填与后端没回读在报文的同一个形状上，本机分不开就照实说分不开 */
internal const val PICKUP_ABSENT_TEXT = "系统没给这一项（下单时没填也会这样，本机分不开）"

/** 后果披露：只说后端真做了的两件事，不承诺"今天能到"，也不替它算具体哪一班 */
internal const val PICKUP_DISPATCH_NOTE =
    "填了最早取货时间，后端会按它筛当批任务窗口：晚于窗口结束的这一批不派，留给下一班；不填则不设下限。"

/** 单日整点候选（0-23）：不预设"营业时间"，因为后端没有任何接口把班次窗口下发给用户端 */
private val PICKUP_HOURS = (0..23).toList()

/**
 * 候选项：`dayOffset` 相对今天的天数，`hour` 是整点。
 * 今天那些**已经过去的整点根本不出现在候选里**（不是置灰后还能点），所以这一格无法被提交成过去。
 * 23 点以后"今天"整档为空 ⇒ 只剩明天/后天，界面不显示空档位。
 */
internal data class PickupOption(val dayOffset: Int, val hour: Int)

/** 日期档标签：只说相对天，不印具体日期以外的承诺 */
internal fun pickupDayLabel(today: LocalDate, dayOffset: Int): String = when (dayOffset) {
    0 -> "今天 ${today.format(PICKUP_DATE_FMT)}"
    1 -> "明天 ${today.plusDays(1).format(PICKUP_DATE_FMT)}"
    2 -> "后天 ${today.plusDays(2).format(PICKUP_DATE_FMT)}"
    else -> "第 $dayOffset 天后"
}

private val PICKUP_DATE_FMT = java.time.format.DateTimeFormatter.ofPattern("MM-dd")
private val PICKUP_HOUR_FMT = java.time.format.DateTimeFormatter.ofPattern("HH:mm")

/** 把候选按天分档，并剔掉今天已经过去的整点（`now` 由调用方给，用例用固定时钟）；整天为空就不显示那一档 */
internal fun pickupOptions(now: ZonedDateTime): List<Pair<String, List<PickupOption>>> =
    (0 until PICKUP_DAY_COUNT).map { day ->
        pickupDayLabel(now.toLocalDate(), day) to
            PICKUP_HOURS.map { h -> PickupOption(day, h) }.filter { pickupMillisOf(now, day, it.hour) != null }
    }.filter { it.second.isNotEmpty() }

/**
 * 选中项 → 出网毫秒；**不合法一律 null**（不发 0、不发过去、不发字符串）。
 * 今天那一档里早于 `now` 的整点返回 null，界面因此拿不到它，也就点不到。
 */
internal fun pickupMillisOf(now: ZonedDateTime, dayOffset: Int, hour: Int): Long? {
    if (dayOffset !in 0 until PICKUP_DAY_COUNT) return null
    if (hour !in PICKUP_HOURS) return null
    val instant = now.toLocalDate().plusDays(dayOffset.toLong())
        .atTime(hour, 0)
        .atZone(now.zone)
        .toInstant()
        .toEpochMilli()
    return if (instant > now.toInstant().toEpochMilli()) instant else null
}

/** 提交前的最后一道：草稿恢复出来的旧时刻（隔夜草稿）在这里变成"不发"，而不是发一个过去的时间 */
internal fun pickupSubmitValue(millis: Long?, nowMillis: Long): Long? =
    millis?.takeIf { it > 0L && it > nowMillis }

/** 界面上那一行的值：未选念"随时可取"，选了念"某时刻之后可取" */
internal fun pickupSelectionText(millis: Long?): String = millis?.let {
    java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(it), PICKUP_ZONE)
        .format(PICKUP_HOUR_FMT) + " 之后可取"
} ?: PICKUP_UNSET_TEXT

/**
 * 回读那一格：**只认数字毫秒**。后端序列化端发的是数字（`YudaoJacksonAutoConfiguration` 注册的
 * `TimestampLocalDateTimeSerializer`），一旦看见字符串就当没给——宁可不显示，
 * 也不重复后端那个"非数字串静默变 0"的形状。
 */
internal fun pickupMillisFromJson(o: JSONObject, key: String = "earliestPickupTime"): Long? =
    (o.opt(key) as? Number)?.toLong()?.takeIf { it > 0L }

/** 回读文本：有值才念时刻，缺席念 [PICKUP_ABSENT_TEXT]（这一格不许塌成"随时可取"） */
internal fun pickupReadbackText(millis: Long?): String = millis?.let {
    // ⚠ 只能从 LocalDateTime 换算再取日期：LocalDate 上的 ofInstant 重载是 API 34，
    //   minSdk 26 上会被 lint 判 NewApi（本轮就是被 :app:lintDebug 抓出来的——只跑 assembleDebug + 用例挡不住它）
    val at = java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(it), PICKUP_ZONE)
    at.toLocalDate().format(PICKUP_DATE_FMT) + " " + at.format(PICKUP_HOUR_FMT) + " 之后"
} ?: PICKUP_ABSENT_TEXT

/**
 * 提交回执那一格的结论：把"发没发"和"后端回没回读"分开说。
 * [submitted] = 本次真的带出网的毫秒（null = 用户没填）；[readback] = 重拉列表时后端给的毫秒。
 */
internal fun pickupSubmitConclusion(submitted: Long?, readback: Long?): String = when {
    submitted == null -> "本次没有填最早取货时间：后端按不设下限处理（$PICKUP_UNSET_TEXT）。"
    readback != null -> "已按 ${pickupReadbackText(readback)} 提交，后端已回读这一项。"
    else -> "已提交 ${pickupReadbackText(submitted)}，但列表这次没回读这一项——$PICKUP_ABSENT_TEXT"
}
