package com.shanxiang.buspost.core.map

import com.shanxiang.buspost.data.model.Geo
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 一次缓存读取的结果（指令 v2.1 §十三 点名的 `cached()` 那一问，赛道 ⑭ / D-059）。
 *
 * 三格各自独立、不许并格：[Absent] 是"本机没有这一条"，[Stale] 是"有过、但旧到不能替现在说话"，
 * 只有 [Hit] 把几何交出去。⚠ [Stale] 故意**不带** [RealRoadRoute] —— 过期路线拿不到，
 * 不靠调用方自觉；指令那句"正式路线不允许返回过期路线"落在这里是结构性的，不是约定性的。
 */
sealed class RouteCacheRead {

    data class Hit(val entry: RouteCacheEntry, val ageMillis: Long) : RouteCacheRead()

    /** 没有这一条：进程重启过、被淘汰过、或这条 key 从来没写进来过 */
    object Absent : RouteCacheRead()

    /**
     * 有记录但已过期，读取当场把它删了。
     * 带 provider 与抓取时间，是"本机确实存过一条什么"这句话还能诚实说出口的唯一依据；
     * [ageMillis] 只说明"这条记录存在多久了"，不说明"路线还成立"。
     */
    data class Stale(val providerRaw: String, val fetchedAtMillis: Long, val ageMillis: Long) : RouteCacheRead()
}

/**
 * 一条缓存条目：几何 + **原始 provider**（后端 `routeProvider` 给的那个字符串，照抄、不归一）
 * + 抓取时间 + 过期时间。
 *
 * 原始 provider 单独留一份的理由：[RealRoadRoute.source] 是归一后的枚举（`RouteSource`），
 * "gaode"/"AMAP"/"高德" 都会塌成 `AMAP` —— 拿它回执会把后端没说的话替后端说了。
 * 诚实披露要的是后端**当时给的原文**，所以这一格不能被枚举顶替。
 *
 * ⚠ 指令 §十三 还点了 graphVersion 与 canonical fingerprint（origin/waypoints/destination/profile/图版本）
 * 两件：本端点三面（后端 `AppDriverRouteRespVO` / 小程序 / 演示面）0 生产者（本轮实测），
 * 写进代码就是本机凭空造一个"看起来可信的东西" ⇒ 本轮不做，代价与理由登记在 D-059。
 */
data class RouteCacheEntry(
    val route: RealRoadRoute,
    /** 后端当时给的来源原文；空串就是"后端没说"，不补任何默认标签 */
    val providerRaw: String,
    val fetchedAtMillis: Long,
    val expiresAtMillis: Long,
    /** 写入序号：`ConcurrentHashMap` 没有插入序，淘汰最旧的那一条只能靠它（旧的 `LinkedHashMap` 是靠顺序） */
    val sequence: Long,
)

/**
 * 真实道路路线的唯一入口。
 *
 * `Repo` 解析 JSON 之后必须把折线交到这里过 [RealRoadRouteValidator]，
 * 页面只消费 [RouteCheck.Accepted] 的 [RealRoadRoute]；被拒收时得到 [RouteCheck.Rejected] 的原因，
 * 从而渲染"路线不可用"，而不是各自兜底。
 *
 * 缓存只存**同时过了两道闸**的路线：校验器认它（来源可信 + 几何自洽）**并且**后端自己说这一班有真实道路
 * （`routeAvailable`）。赛道 ⑭ 之前 `resolve` 跑在 `serverAvailable` 之前，后端否决落在写之后 ——
 * 那个先后顺序缺陷（`D-⑱-3`）由 [resolve] 的 `serverAvailable` 入参关掉，写入与否在同一个决定里出。
 *
 * ⚠ 读取口径（本轮起）：这份缓存现在**有一个**生产读点（`Repo.routeGuide()` 的失败支，只读不画线），
 * 所以 F-3 ⑰ 那句"只写不读、TTL 本轮不做"当场作废 —— TTL/过期不返回/线程安全/来源与抓取时间四件
 * 就在这一轮做；`graphVersion` 与指纹那两件因为没有生产者，仍然没做，登记在 D-059。
 */
object RouteRepository {

    /**
     * 线程安全用 `ConcurrentHashMap`（指令 §十三 点名"不要裸 LinkedHashMap"，并给了 Mutex / ConcurrentHashMap 两个优先项）。
     * 这里选后者的理由：缓存的读点不只在 suspend 里（引擎侧、用例侧都要读），
     * `kotlinx.coroutines.sync.Mutex` 会把"取一条缓存"变成挂起函数，逼调用链一起挂起。
     */
    private val accepted = ConcurrentHashMap<String, RouteCacheEntry>(8)

    private val writeSequence = AtomicLong()

    /** 内存里最多留几条，够覆盖司机一趟班次与几条常看线路 */
    const val MAX_ENTRIES = 12

    /**
     * 一条缓存能替"现在"说话多久：30 分钟。
     *
     * ⚠ 这个数值**没有外部权威来源**：后端不发任何新鲜度/有效期键（本轮逐字段读 `AppDriverRouteRespVO` 核对），
     * 所以它是本机自定口径 —— 台账与用例都按这一点写，不许写成"后端要求的 TTL"或"路网数据的有效期"。
     */
    const val MAX_AGE_MILLIS = 30L * 60L * 1000L

    fun resolve(
        key: String,
        sourceRaw: String?,
        points: List<Geo>,
        roadDistanceMeters: Int = 0,
        roadEtaSeconds: Int = 0,
        crsRaw: String? = null,
        stationGeos: List<Geo> = emptyList(),
        serverAvailable: Boolean = true,
        nowMillis: Long = System.currentTimeMillis(),
    ): RouteCheck {
        val check = RealRoadRouteValidator.validate(
            sourceRaw = sourceRaw,
            points = points,
            roadDistanceMeters = roadDistanceMeters,
            roadEtaSeconds = roadEtaSeconds,
            crsRaw = crsRaw,
            stationGeos = stationGeos,
        )
        if (check is RouteCheck.Accepted && serverAvailable) {
            accepted[key] = RouteCacheEntry(
                route = check.route,
                providerRaw = sourceRaw.orEmpty(),
                fetchedAtMillis = nowMillis,
                expiresAtMillis = nowMillis + MAX_AGE_MILLIS,
                sequence = writeSequence.incrementAndGet(),
            )
            evictOverflow()
        } else {
            // 来源不可信、几何不过、或后端自己说这一班没有真实道路 —— 三种都不许留着旧条目，
            // 否则"刷新过了"会被读成"没刷新也一样"。
            accepted.remove(key)
        }
        return check
    }

    /** 这份缓存唯一的读取出口。`nowMillis` 由调用方给，是为了让过期边界可测，不是为了省一次取时。 */
    fun cached(key: String, nowMillis: Long = System.currentTimeMillis()): RouteCacheRead {
        val entry = accepted[key] ?: return RouteCacheRead.Absent
        val age = (nowMillis - entry.fetchedAtMillis).coerceAtLeast(0L)
        // 指令 §十三 的原话是"当前时间 < expiresAt"：相等即过期，边界不给情面。
        if (nowMillis >= entry.expiresAtMillis) {
            // 两参 remove：只删自己刚读到的那一份。别的线程期间写进新条目时，这一笔不能误删新数据。
            accepted.remove(key, entry)
            return RouteCacheRead.Stale(entry.providerRaw, entry.fetchedAtMillis, age)
        }
        return RouteCacheRead.Hit(entry, age)
    }

    fun clear() {
        accepted.clear()
    }

    /** 当前留了几条（用例侧观察淘汰用；不给可变引用） */
    fun size(): Int = accepted.size

    private fun evictOverflow() {
        while (accepted.size > MAX_ENTRIES) {
            val oldest = accepted.entries.minByOrNull { it.value.sequence } ?: return
            if (!accepted.remove(oldest.key, oldest.value)) return
        }
    }
}
