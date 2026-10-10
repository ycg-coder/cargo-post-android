package com.shanxiang.buspost.data.repo

import com.shanxiang.buspost.core.map.RouteCacheRead
import com.shanxiang.buspost.core.map.RouteCheck
import com.shanxiang.buspost.core.map.RouteRepository
import com.shanxiang.buspost.data.model.Geo
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 离线真实道路缓存的契约（赛道 ⑭ / D-059，指令 v2.1 §十三）。
 *
 * 这本用例钉三件事，每一件都是"这一轮把缓存做成了什么"的可测回答：
 * · **provider 与抓取时间必须原样留痕** —— 缓存里那份来源是后端当时给的**原文**，
 *   不是 `RouteSource` 归一后的标签（"gaode"/"AMAP"/"高德" 都会塌成同一个枚举，拿归一值回执就是替后端补话）；
 * · **过期条目不许替现在说话** —— 指令原话是"当前时间 < expiresAt"，相等即过期，
 *   而且读到的那一格**结构上拿不到几何**（[RouteCacheRead.Stale] 没有 route 字段），同时当场被删；
 * · **披露不是放行** —— 命中时那句话只报"本机存着什么"，不宣布几何被接进了地图，
 *   也不替 F-3 ⑧ 的无路线三态那句"这里不画任何示意线"说话（话术唯一出口只有一处，见 §八 ㉗）。
 *
 * ⚠ TTL 的数值（30 分钟）是**本机自定口径**，后端不发任何新鲜度键 ⇒ 用例只断言边界与过期行为，
 * 不把它写成"路网数据的有效期"。
 */
class RouteOfflineCacheContractTest {

    private val road = listOf(Geo(29.60, 106.60), Geo(29.62, 106.63), Geo(29.65, 106.65))

    @Before
    fun resetCache() {
        RouteRepository.clear()
    }

    private fun accept(key: String, providerRaw: String, nowMillis: Long = System.currentTimeMillis()) {
        val check = RouteRepository.resolve(
            key = key, sourceRaw = providerRaw, points = road,
            roadDistanceMeters = 9_200, nowMillis = nowMillis,
        )
        assertTrue("夹具本身要先被校验器收下：$check", check is RouteCheck.Accepted)
    }

    @Test
    fun `命中时披露抓取时间与后端当时的来源原文`() {
        accept("driverRoute:11", "gaode")
        val text = routeFailureTextOf("driverRoute:11", "请求失败", nowMillis = System.currentTimeMillis())
        assertTrue(text, text.startsWith("请求失败"))
        assertTrue(text, text.contains("不足 1 分钟"))
        // 原文照抄：归一枚举是"高德路网"，后端说的是"gaode"，两个都要能对上
        assertTrue(text, text.contains("gaode"))
        assertTrue(text, text.contains("高德路网"))
        // 披露不越权：这句话自己承认几何没接进地图，也不替无路线态承诺"不画线"
        assertTrue(text, text.contains("还没接进地图"))
        assertTrue(text, !text.contains("不画任何示意线"))
    }

    @Test
    fun `没有缓存时失败原因原样返回`() {
        // 这一格最容易被写坏：缓存空着却要说出"本机存过一条……"就是编造
        val cause = "网络连接不上"
        assertTrue(routeFailureTextOf("driverRoute:13", cause) == cause)
        assertTrue(RouteRepository.cached("driverRoute:13") is RouteCacheRead.Absent)
    }

    @Test
    fun `过期条目只留下记录不留几何并且读到即删`() {
        val written = System.currentTimeMillis()
        accept("driverRoute:14", "AMAP", nowMillis = written)
        val staleAt = written + RouteRepository.MAX_AGE_MILLIS
        val text = routeFailureTextOf("driverRoute:14", "这次没问到", nowMillis = staleAt)
        assertTrue(text, text.contains("已超过本机自定的新鲜度口径"))
        // 过期那一格不报来源原文：几何早就不能替现在说话了，来源跟着说就成了"路线还在"的暗示
        assertTrue(text, !text.contains("AMAP"))
        // 读到过期条目这一次就把它删了：再读是"没有这一条"，而不是"还有一条旧的"
        assertTrue(RouteRepository.cached("driverRoute:14", staleAt + 1) is RouteCacheRead.Absent)
        assertTrue("过期条目没被删掉：size=${RouteRepository.size()}", RouteRepository.size() == 0)
    }

    @Test
    fun `抓取时间来自写入那一次的时钟入参而不是读的那一刻`() {
        // 夹具故意用一个"过去"的时钟写进去：生产代码若现场取时（System.currentTimeMillis()），
        // 这条会被披露成"刚抓的"，而 30 分钟那条边界就永远测不到（NAB5/NAB6 的行为半边）。
        val written = 1_000L
        accept("driverRoute:16", "amap", nowMillis = written)
        val text = routeFailureTextOf("driverRoute:16", "这次没问到", nowMillis = written + 5 * 60_000L)
        assertTrue(text, text.contains("5 分钟"))
        assertTrue(text, !text.contains("不足 1 分钟"))
    }

    @Test
    fun `演示面来源从未进过缓存`() {
        val check = RouteRepository.resolve(
            key = "driverRoute:15", sourceRaw = "DEMO", points = road, roadDistanceMeters = 9_200,
        )
        assertTrue(check is RouteCheck.Rejected)
        assertTrue(RouteRepository.cached("driverRoute:15") is RouteCacheRead.Absent)
        assertTrue(routeFailureTextOf("driverRoute:15", "失败") == "失败")
    }

    @Test
    fun `并发读写不越界也不留坏条目`() {
        val threads = 8
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        val failures = CopyOnWriteArrayList<Throwable>()
        repeat(threads) { t ->
            pool.execute {
                try {
                    start.await()
                    repeat(30) { i ->
                        val key = "busLine:${t}_$i"
                        RouteRepository.resolve(
                            key = key, sourceRaw = "amap", points = road, roadDistanceMeters = 9_200,
                        )
                        val read = RouteRepository.cached(key)
                        if (read is RouteCacheRead.Hit && read.entry.route.points.size < 2) {
                            throw AssertionError("命中却拿不到几何")
                        }
                        RouteRepository.cached("busLine:${t}_${i}_没写过")
                    }
                } catch (e: Throwable) {
                    failures.add(e)
                } finally {
                    done.countDown()
                }
            }
        }
        start.countDown()
        assertTrue("并发用例超时没跑完", done.await(30, TimeUnit.SECONDS))
        pool.shutdownNow()
        assertTrue(failures.joinToString { it.toString() }, failures.isEmpty())
        // 上限是硬约束：并发写入也不能把缓存撑过 MAX_ENTRIES
        assertTrue("缓存条数越界：${RouteRepository.size()}", RouteRepository.size() <= RouteRepository.MAX_ENTRIES)
    }

    @Test
    fun `时长口径只分三档且负数不炸`() {
        assertTrue(offlineCacheAgeText(0L) == "不足 1 分钟")
        assertTrue(offlineCacheAgeText(-5_000L) == "不足 1 分钟")
        assertTrue(offlineCacheAgeText(5 * 60_000L) == "5 分钟")
        assertTrue(offlineCacheAgeText(90 * 60_000L) == "1 小时 30 分")
    }
}
