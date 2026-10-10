package com.shanxiang.buspost.ui.common

import com.shanxiang.buspost.driver.LocationReportPolicy
import kotlin.math.roundToInt

/**
 * 3D 视觉层（指令 §八 ⑫⑬：地图倾斜 / 车辆方向 / 任务点立体感 / 当前与下一任务高亮）。
 *
 * 这一层只管**怎么画**，一个字都不碰业务状态：`TaskState / OrderState / LegState` 的推进
 * 仍然只能来自司机的主动动作 + 后端回读（硬规则：3D Marker 不允许影响业务）。
 * 所以这里全部是纯函数、不 import 任何 Android/高德类型 —— 它能在 JVM 里逐条钉住，
 * 而"加了 3D 之后业务有没有被改"这个问题就不会靠人眼 Review 来回答。
 *
 * 有两条是**明确不做**的，理由写在这里而不是留白：
 * · 车辆 marker 不旋转。指令要 `bearing = GPS bearing`，而车载那一格的 GPS 在**另一台设备**上，
 *   后端 `AppDriverLocationReqVO` / `AppDriverRouteRespVO` 逐字段读下来没有任何朝向字段，
 *   `RouteGuide.headingDegrees` 是折线几何**推断**出来的，把它当车辆朝向就是替后端下结论
 *   （同 §八 ㉗：同一个问题不能有两个答案）。旋转只给"我"这一格 —— 那是本机 GPS，`hasBearing` 拿得到。
 * · 不做 marker 逐帧插值。指令给了 `animateVehicle(durationMs=800)`，但"跳 20 米"这件事
 *   在真机上才看得出来，本机 0 台设备在环；写一个无法证伪的插值函数就是造 dead code。
 *   这里只把 800ms 交给 SDK 的相机动画（`CAMERA_ANIMATION_MS`），并且 SDK 插值的结果
 *   **不回写任何业务字段**，由判据簿按"上报路径不引用 visual/朝向"钉住。
 */
enum class GpsFixState {
    /** 有 fix 且点龄在停报阈值内 */
    LIVE,

    /** 有 fix 但点龄已越过停报阈值：只能降级显示，不能当成"车现在在这里" */
    STALE,

    /** 一条 fix 都没有 */
    UNAVAILABLE,
}

/** 任务点的强调层级：当前任务 / 下一个任务 / 不强调 */
enum class MarkerEmphasis {
    NONE,
    NEXT,
    CURRENT,
}

/**
 * 一个 marker 的视觉输入。全部可空/带默认值：调用方拿不到朝向时传 null，
 * 而不是传 0.0 —— 0° 与"未知"必须是两件事（V2.1 核对表 V21-15 点名的就是这个缺陷）。
 */
data class MarkerVisual(
    val headingDegrees: Double? = null,
    val emphasis: MarkerEmphasis = MarkerEmphasis.NONE,
    /** 降级态：定位不可用时"我"这一格沉到最底下，不冒充在线车辆 */
    val degraded: Boolean = false,
)

/** 新鲜度 + 朝向 + 那句要进脚注的实话 */
data class GpsFixVisual(
    val state: GpsFixState,
    val headingDegrees: Double?,
    /** 空串表示"没有要补充的"；否则是服务侧原话，页面不自己编 */
    val note: String,
)

/** 喂给高德 `MarkerOptions` 的那组 3D 参（这里不 import SDK，测试才能跑在 JVM 里） */
data class Marker3D(
    val flat: Boolean,
    val perspective: Boolean,
    val zIndex: Float,
    val altitudeMeters: Float,
    /** null = 不设置旋转，保持 marker 正立 */
    val rotateAngle: Float?,
)

/**
 * 3D 参的唯一生产者。
 */
object Map3DVisual {

    /**
     * 任务点浮空高度：要能看出"这是个可点的立体标记"，又不能高到脱离底图位置
     * —— 浮 8 米在乡道尺度上仍落在路肩上，浮几十米就变成"车在楼上"。
     */
    const val TASK_ALTITUDE_METERS = 8f

    /**
     * 地图倾斜角。取 35° 是因为高德在 tilt>45° 时地面 marker 的锚点位移肉眼可见地偏，
     * 司机要按点判断"到没到站"，偏得越多越危险；35° 已经足够看出立体感。
     */
    const val MAP_TILT_DEGREES = 35f

    /** 相机动画时长（指令给的 800ms） */
    const val CAMERA_ANIMATION_MS = 800L

    private const val Z_PLAIN = 2f
    private const val Z_NEXT = 5f
    private const val Z_CURRENT = 9f
    private const val Z_VEHICLE = 12f
    private const val Z_ME = 15f

    /** 定位不可用时"我"沉到任务点之下：不能让人以为车还在线 */
    private const val Z_ME_DEGRADED = 1f

    /**
     * STALE 那一句：阈值只有一个生产者（[LocationReportPolicy.MAX_FIX_AGE_MS]，就是服务侧
     * 决定"暂停上报"用的同一个数字），这里不写第二个毫秒数，也不重复政策的整句话。
     */
    const val STALE_NOTE = "定位降级：定位数据较旧，显示的是最后一次可信采样"

    /** UNAVAILABLE 且服务侧给不出原因时的兜底：只说"没拿到"，不说"后端说没有" */
    const val NO_FIX_NOTE = "暂时没拿到当前位置：显示的是调度安排，不是实时车位"

    /**
     * 新鲜度判据。**不新建第二个时钟**：输入是服务已经写进 `Running` 的那两格，
     * 阈值是政策用的同一个常量，所以"屏幕觉得降级"和"服务已经停报"不会各说各话。
     */
    fun gpsFixStateOf(hasFix: Boolean, fixAgeMs: Long): GpsFixState = when {
        !hasFix -> GpsFixState.UNAVAILABLE
        fixAgeMs > LocationReportPolicy.MAX_FIX_AGE_MS -> GpsFixState.STALE
        else -> GpsFixState.LIVE
    }

    /**
     * 组装视觉层要的三格。`holdReason` 是服务侧在途状态里那句等待原因的原话（策略给出的原因串），
     * 页面只转述、不重写；为空时说明这一拍没有 Hold，只能用本层自己的短句。
     */
    fun gpsFixVisual(
        hasFix: Boolean,
        fixAgeMs: Long,
        holdReason: String,
        headingDegrees: Double?,
    ): GpsFixVisual {
        val state = gpsFixStateOf(hasFix, fixAgeMs)
        return GpsFixVisual(
            state = state,
            headingDegrees = if (state == GpsFixState.LIVE) headingDegrees else null,
            note = footnoteOf(state, holdReason),
        )
    }

    /** 角度归一到 [0,360)；null 原样返回 null（"没有朝向"绝不能变成 0°） */
    fun rotateOf(headingDegrees: Double?): Float? =
        headingDegrees?.let { (((it % 360.0) + 360.0) % 360.0).toFloat() }

    /** 朝向的可读形式：未给就明说未给，不写"正北" */
    fun headingText(headingDegrees: Double?): String {
        val normalized = rotateOf(headingDegrees)
            ?: return "朝向暂未知"
        // 四舍五入可能把 359.6° 抬成 360°，而 360° 与 0° 是同一个方向：显示上收回来，别写出一个不存在的角
        val shown = normalized.roundToInt().let { if (it == 360) 0 else it }
        return "朝向 $shown°（GPS）"
    }

    /**
     * 脚注那句实话。LIVE 返回空串 —— 正常的时候不该在地图上多一句话。
     */
    fun footnoteOf(state: GpsFixState, holdReason: String): String = when (state) {
        GpsFixState.LIVE -> ""
        GpsFixState.STALE ->
            if (holdReason.isBlank()) STALE_NOTE else "定位降级：$holdReason"
        GpsFixState.UNAVAILABLE ->
            if (holdReason.isBlank()) NO_FIX_NOTE else "暂时没拿到当前位置（$holdReason）：显示的是调度安排，不是实时车位"
    }

    /**
     * 每个 kind 的 3D 形状。只有任务点浮空、只有"我"旋转 —— 两条都由这里单点决定，
     * 页面侧不再各自 `setFlat`/`setRotateAngle`。
     */
    fun marker3DOf(kind: Int, visual: MarkerVisual): Marker3D {
        val zIndex = when {
            kind == MapMarker.KIND_ME && visual.degraded -> Z_ME_DEGRADED
            kind == MapMarker.KIND_ME -> Z_ME
            kind == MapMarker.KIND_BUS_REAL || kind == MapMarker.KIND_BUS_SIM -> Z_VEHICLE
            visual.emphasis == MarkerEmphasis.CURRENT -> Z_CURRENT
            visual.emphasis == MarkerEmphasis.NEXT -> Z_NEXT
            else -> Z_PLAIN
        }
        val isTask = kind == MapMarker.KIND_TASK_TODO ||
            kind == MapMarker.KIND_TASK_ACTIVE ||
            kind == MapMarker.KIND_TASK_DONE ||
            kind == MapMarker.KIND_TASK_FAILED
        return Marker3D(
            // flat=true 让车标始终正对着看：倾斜底图上车标"躺"在路面上会读不出方向
            flat = kind == MapMarker.KIND_BUS_REAL || kind == MapMarker.KIND_BUS_SIM ||
                kind == MapMarker.KIND_ME,
            perspective = true,
            zIndex = zIndex,
            altitudeMeters = if (isTask) TASK_ALTITUDE_METERS else 0f,
            // 车辆不转：后端没有朝向字段，折线推断不算 GPS bearing
            rotateAngle = if (kind == MapMarker.KIND_ME) rotateOf(visual.headingDegrees) else null,
        )
    }
}
