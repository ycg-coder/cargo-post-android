package com.shanxiang.buspost.core.utils

import com.shanxiang.buspost.data.model.ReviewStatus

/**
 * 承运审核 reasonCode → 文案映射（与后端 ReviewReasonCodeEnum 对齐，禁止在页面散落硬编码）。
 * 后端只返回原因码，前端统一在此映射文案。
 */
object ReviewMapper {
    private val REASON_TEXT_MAP = mapOf(
        "PROHIBITED_GOODS" to "禁运品",
        "DANGEROUS_GOODS" to "危险品",
        "OVER_WEIGHT" to "超重",
        "OVER_SIZE" to "超尺寸",
        "ROAD_UNREACHABLE" to "道路不可达",
        "DETOUR_TOO_LARGE" to "绕行代价过大",
        "PASSENGER_SERVICE_CONFLICT" to "与客运服务冲突",
        "NO_SAFE_HANDOFF_POINT" to "无安全交接点",
        "USER_LOCATION_UNREACHABLE" to "当前位置车辆无法进入",
        "CUSTOMER_ACTION_REQUIRED" to "需您到指定站点交接",
        "MANUAL_REVIEW_REQUIRED" to "需人工确认"
    )

    /**
     * 逗号分隔原因码字符串 → 中文文案（如 "危险品、超重"）；空串返回 ''
     */
    fun reasonText(reasonCodes: String?): String {
        if (reasonCodes.isNullOrEmpty()) return ""
        return reasonCodes
            .split(",")
            .filter { it.isNotBlank() }
            .map { code -> REASON_TEXT_MAP[code] ?: code }
            .joinToString("、")
    }

    /**
     * reviewStatus → 标题映射
     * 1=可运, 2=需操作, 3=待人工, 4=不可运
     */
    fun reviewTitle(status: Int): String = when (ReviewStatus.of(status)) {
        ReviewStatus.PASSED -> "承运审核通过"
        ReviewStatus.CONDITIONAL -> "需要您配合操作"
        ReviewStatus.MANUAL_REVIEW -> "等待人工审核"
        ReviewStatus.REJECTED -> "暂无法承运"
        else -> "审核结果"
    }

    /**
     * reviewStatus → 提示文案
     */
    fun reviewHint(status: Int): String = when (ReviewStatus.of(status)) {
        ReviewStatus.PASSED -> "您的货物符合运输条件，司机将尽快安排取货"
        ReviewStatus.CONDITIONAL -> "请按以下要求完成操作后，系统将继续处理您的订单"
        ReviewStatus.MANUAL_REVIEW -> "调度员正在确认运输方案，请耐心等待"
        ReviewStatus.REJECTED -> "很抱歉，当前条件下无法安排运输，建议您调整信息后重新提交"
        else -> ""
    }
}
