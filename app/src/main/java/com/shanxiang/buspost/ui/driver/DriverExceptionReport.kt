package com.shanxiang.buspost.ui.driver

import com.shanxiang.buspost.data.model.TransportLeg

/**
 * 指令 §54 的七个异常分类。
 *
 * **这是客户端的选项目录，不是后端枚举** —— 契约是实测来的：`AppDriverLegActionReqVO` 只有
 * `driverId / legId(@NotNull) / photoUrl / remark` 四个字段，**没有任何异常类型码**。
 * §54 原文因此写死了边界："先读取真实 enum。不要自己发一套后端无法接受的 type。"
 * 于是分类**落在 remark 文本的开头**，后台告警、订单事件时间线、用户通知读到的都是同一句话。
 *
 * ⚠ 旧版这里还写着"`case "exception"` 只把 remark 交给 reportLegException、照片无人接"——
 *   那句到今天不再成立：队列 #60 之后，后端把 `photoUrl` 记进订单事件的 `extra_data`，
 *   后台订单详情的时间线会画出来。类型码这件事没变（仍然只有自由文本 + 一张照片）。
 */
internal enum class DriverExceptionReason(val label: String) {
    VEHICLE_FAULT("车辆故障"),
    ROAD_BLOCKED("道路阻断"),
    RECEIVER_ABSENT("收件人不在"),
    CARGO_ABNORMAL("货物异常"),
    HANDOVER_IMPOSSIBLE("无法交接"),
    WEATHER("天气影响"),
    OTHER("其他");

    /** 「其他」后面不写具体情况，通知里就只剩"第 N 段异常：其他"，调度无从处置 */
    val requiresDetail: Boolean get() = this == OTHER
}

/**
 * remark 的客户端上限。真实落点两处，实测都是 varchar(500)：
 * · `transport_order_event.detail`（`MultiLegServiceImpl#recordLegEvent` 的 EXCEPTION 分支写 `"第 N 段异常：" + remark`）；
 * · `transport_user_notification.content`（`reportLegException` 给后台的 `"订单 X 第 N 段异常：" + remark + "，请重调度"`，
 *   按 19 位订单号 + 3 位段序算，固定开销最坏 38 字符）。
 *
 * 取 200 而不是 462：超限会让 MySQL 报 1406、`@Transactional` 整笔回滚 —— 司机看到"上报失败"但其实什么都没记下。
 * 200 同时也是通知气泡里能读完的长度。**上限输入由 UI 侧限住，这里只做最后一道截断。**
 */
internal const val EXCEPTION_REMARK_MAX = 200

/**
 * 异常上报的请求体（队列 #60）。`photoUrl` **只在真的上传成功时才带**：
 * 后端那一格是"没带就不写"（与交接确认 `photoUrl` 同一条纪律），传空串会把这一格写成一条空地址，
 * 事后看起来像"司机传了一张没有内容的照片"。
 */
internal fun exceptionActionBody(remark: String, photoUrl: String = ""): Map<String, String> =
    if (photoUrl.isEmpty()) mapOf("remark" to remark)
    else mapOf("remark" to remark, "photoUrl" to photoUrl)

/**
 * 照片没传上去时的那句回执（队列 #60 / 真机抓的取证不全）。
 *
 * 为什么要单独一个纯函数、还要把原因带进句子里：真机上试过一次真提交，照片传不上、
 * 而回执只念"没传上去"——司机和调度都分不出是登录态过期、后端存储没配、还是网络断了，
 * 而**这一段的提交机会已经用掉了**（段转异常后不再接受重复上报）。
 * 空原因不许留成"原因："后面一片空白，那比没有更让人误判。
 */
internal fun photoDropText(why: String): String {
    val w = why.trim().take(90)
    return "异常已提交，但现场照片没传上去（后端那一格仍是空）——原因：${if (w.isEmpty()) "后端与本机都没给原因" else w}。" +
        "可让调度补录或重报下一段"
}

/** 选定分类后，补充说明最多还能写多少字（`：` 占一位） */
internal fun exceptionDetailBudget(reason: DriverExceptionReason?): Int =
    if (reason == null) EXCEPTION_REMARK_MAX
    else (EXCEPTION_REMARK_MAX - reason.label.length - 1).coerceAtLeast(0)

/**
 * 分类 + 说明 → 发给后端的 remark。**返回 null 表示这一次不许发**，两条拒绝各有各的来处：
 * · 没选分类：后端对空 remark 兜的是"司机上报异常"（`reportLegException:1506`），
 *   本机不能连"是什么事"都不知道就让后端替司机编一句；
 * · 选了「其他」却没写情况：同上，落到调度手上只是一句废话。
 *
 * 非 null 的结果一律 ≤ [EXCEPTION_REMARK_MAX]，超出以省略号收尾。
 */
internal fun buildExceptionRemark(reason: DriverExceptionReason?, detail: String): String? {
    if (reason == null) return null
    val text = detail.trim()
    if (reason.requiresDetail && text.isEmpty()) return null
    val joined = if (text.isEmpty()) reason.label else "${reason.label}：$text"
    return if (joined.length <= EXCEPTION_REMARK_MAX) joined
    else joined.substring(0, EXCEPTION_REMARK_MAX - 1) + "…"
}

/**
 * 段的订单标识。**段序是"每张单内"的序号**：`AppDriverLegRespVO.legSequence` 由后端按单算
 * （`MultiLegServiceImpl` 的 `legSequence` 从 1 起），司机一趟同时挂两三张单时，列表里会出现
 * 两行都念"第 1 段"（缺陷 #43）—— 报错了对象，而异常上报是不可撤销的一段状态写入。
 *
 * 单号取**尾 6 位**：19 位全号在 11.5sp 的行里会挤掉状态名，而这段文字的唯一用途就是在
 * 同屏两三行里认出自己的那一张。尾号缺失（后端没给 / 演示包）必须明说"单号未给"，
 * 不许悄悄退回来只念"第 N 段"—— 那正是缺陷本身。
 */
internal fun legOrderTag(orderNo: String): String =
    orderNo.trim().takeIf { it.isNotEmpty() }
        ?.let { "单号尾 ${it.takeLast(6)} · " }
        ?: "单号未给 · "

/** 选段行的第二行：订单标识 + 段序 + 状态，末尾按"还能不能报"补一句 */
internal fun legOptionTitle(leg: TransportLeg, actionable: Boolean): String =
    "${legOrderTag(leg.orderNo)}第 ${leg.seq} 段 · ${leg.statusLabel}" +
        (if (actionable) "" else "（不可再报）")

/** 提交后回执里那一格：同样要带订单标识，否则"后端回读：第 1 段"念的还是那笔歧义账 */
internal fun legReceiptText(leg: TransportLeg): String =
    "${legOrderTag(leg.orderNo)}第 ${leg.seq} 段「${leg.statusLabel}」"
