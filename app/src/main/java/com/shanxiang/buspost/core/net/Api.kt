package com.shanxiang.buspost.core.net

import android.content.Context
import android.util.Log
import com.shanxiang.buspost.BuildConfig
import com.shanxiang.buspost.data.repo.DemoData
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

/** 业务失败：后端回了 {code!=0}，或 4xx 的 HTTP 状态 */
open class ApiException(val code: Int, override val message: String) : Exception(message)

/** 401 / 会话失效：与业务失败分开，调用方必须走"清会话 + 跳登录"，不能当作普通错误文案 */
class UnauthorizedException(override val message: String = "登录已失效，请重新登录") : ApiException(401, message)

/** 传输层失败：DNS / 连接 / 超时 / 无响应体 / 响应不是合法 JSON。绝不与业务失败混用 */
class NetworkException(override val message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * 一次请求的严格结果。语义与 Retrofit/Result 一致：
 * - 失败就是失败，**不会**被演示数据填成"看起来成功的空壳"
 * - 真空数据是 Success(empty)，页面渲染 EmptyState，不是 NetworkError、也不是演示
 */
sealed interface ApiResult<out T> {
    data class Success<T>(val data: T, val server: Boolean = true) : ApiResult<T>
    data class BusinessError(val code: Int, val message: String) : ApiResult<Nothing>
    data class NetworkError(val message: String, val cause: Throwable? = null) : ApiResult<Nothing>
    data class Unauthorized(val message: String = "登录已失效，请重新登录") : ApiResult<Nothing>

    val isSuccess: Boolean get() = this is Success

    fun getOrNull(): T? = (this as? Success)?.data

    /** 失败时抛出对应异常，让 Repo 的 try/catch 拿到真实分类 */
    fun getOrThrow(): T = when (this) {
        is Success -> data
        is BusinessError -> throw ApiException(code, message)
        is NetworkError -> throw NetworkException(message, cause)
        is Unauthorized -> throw UnauthorizedException(message)
    }

    fun <R> map(transform: (T) -> R): ApiResult<R> = when (this) {
        is Success -> Success(transform(data), server)
        is BusinessError -> this
        is NetworkError -> this
        is Unauthorized -> this
    }
}

/**
 * 指令 v2.1 §七 点名的"常用网络请求统一 helper"，落点就在这里（F-2 第 8 步）。
 *
 * 三件事它**只做**这三件：
 * ① **取消穿透**：`CancellationException` 一律原样抛出，不收进 [ApiResult]——页面离场 / 外层超时不是"请求失败"，
 *    收成一次失败就会被写回一个已经不该再被写的画面（§七 禁止的那条链路）。
 * ② **只分类网络与业务这四类**：401 → [ApiResult.Unauthorized]、业务码 → [ApiResult.BusinessError]、
 *    传输层（含 [IOException]）→ [ApiResult.NetworkError]。
 * ③ **不兜底**：演示数据回填不在这里（见 `Api.execute` 的演示白名单），失败也不在这里变成空列表/0/`""`。
 *
 * ⚠ **它没有 `catch (e: Exception)`，这是刻意的**：`NullPointerException` / `IllegalStateException` /
 * `ContractParseException` / `DriverUnboundException` 这类穿透出去，由调用方决定怎么如实说。
 * 把它们收进 `ApiResult` 就是给"程序错误"发一张"业务失败"的通行证（§六 同一条主题）。
 *
 * 与 [com.shanxiang.buspost.core.net.attempt] 的分工（两个都有真实消费者，不是重复）：
 * `attempt` 是**通用**协程形状 helper（收 `Result`，Worker / 照片读取 / 引擎调用都在用，见 D-034 的命名理由），
 * `safeApiCall` 只服务**网络读**，因为这几处要的是"哪一类失败"而不是"有没有失败"。
 *
 * ⚠ 参数是 `block: () -> T` 而不是 `suspend () -> T`：本函数是 `inline`，
 * 挂起调用在展开点上是合法的（`ApiTest.kt:171` 今天就这么写并能编译）。
 * 改成 `suspend () -> T` 只会让**非挂起的字面量调用点**多一类适配问题，对"取消穿透"这件事没有任何增益，
 * 所以本轮**没动签名**——"签名包不住挂起调用"是我在动手前的一个猜测，被上面那条现成用例证伪了，照实记在这里。
 */
suspend inline fun <T> safeApiCall(block: () -> T): ApiResult<T> = try {
    ApiResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: UnauthorizedException) {
    ApiResult.Unauthorized(e.message)
} catch (e: ApiException) {
    ApiResult.BusinessError(e.code, e.message)
} catch (e: NetworkException) {
    ApiResult.NetworkError(e.message, e.cause)
} catch (e: IOException) {
    ApiResult.NetworkError(e.message ?: "网络异常", e)
}

/**
 * 一次请求的结果载体。demo=true 只有一种来源：显式开启的演示模式（BuildConfig.DEMO_ALLOWED），
 * 生产构建恒为 false，页面据此决定是否挂"演示数据"标签。
 */
class Payload(
    val obj: JSONObject?,
    val arr: JSONArray?,
    val demo: Boolean,
    /**
     * 后端 `CommonResult` 的 data 是**裸标量**时的原始值（未读数 `Long`、上传后的文件 URL `String`）。
     * obj/arr 两个通道都拿不到它 —— 这是审计 §22/§23 里"未读角标恒 0、上传 URL 恒空"的唯一病根。
     * 保留 `obj = {"v": …}` 是为了不破坏旧调用点，新代码一律走 [scalarLong] / [scalarStr]。
     */
    val scalar: Any? = null,
) {
    /** 数据确实来自后端（供 RemoteState.fromServer 使用） */
    val fromServer: Boolean get() = !demo

    /**
     * 返回 null 而不是 0：后端这次没给数（或给的不是数）与"未读数真是 0"是两回事，
     * 调用方必须能分开处理，否则又变成一个"静默显示 0"的读法（审计 §22/§52、需求"null 不得当 0"）。
     */
    fun scalarLong(): Long? = when (val v = scalar) {
        is Number -> v.toLong()
        is String -> v.trim().toLongOrNull()
        else -> null
    }

    /** 裸字符串标量（文件 URL 就是这种形态）；空串/空白按"没有值"处理 */
    fun scalarStr(): String? = (scalar as? String)?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * 「先标量、后对象键」的数值读法：后端可能回裸 `Long`（`CommonResult<Long>`），
     * 也可能回 `{"count": …}` 的对象形态，两种都认，标量优先。
     * 键不存在 / 值为 null 一律返回 null，**不返回 0** —— 0 只能来自后端真的给了 0。
     */
    fun longOf(vararg objectKeys: String): Long? {
        scalarLong()?.let { return it }
        val o = obj ?: return null
        for (key in objectKeys) {
            if (o.has(key) && !o.isNull(key)) {
                o.opt(key)?.let { v ->
                    when (v) {
                        is Number -> return v.toLong()
                        is String -> v.trim().toLongOrNull()?.let { return it }
                    }
                }
            }
        }
        return null
    }

    /** 同 [longOf] 的字符串版：先裸 String，再退对象键；空串按"没有值"返回 null */
    fun strOf(vararg objectKeys: String): String? {
        scalarStr()?.let { return it }
        val o = obj ?: return null
        for (key in objectKeys) {
            if (o.has(key) && !o.isNull(key)) {
                o.optString(key).trim().takeIf { it.isNotEmpty() }?.let { return it }
            }
        }
        return null
    }

    val list: List<JSONObject> get() {
        val out = ArrayList<JSONObject>()
        val src = arr ?: obj?.optJSONArray("list")
        if (src != null) for (i in 0 until src.length()) src.optJSONObject(i)?.let { out.add(it) }
        return out
    }

    /** 分页总条数 */
    val total: Int get() = obj?.optInt("total", list.size) ?: list.size

    fun str(key: String, def: String = ""): String = obj?.optString(key, def) ?: def
    fun int(key: String, def: Int = 0): Int = obj?.optInt(key, def) ?: def
    fun dbl(key: String, def: Double = 0.0): Double = obj?.optDouble(key, def) ?: def
    fun nested(key: String): JSONObject? = obj?.optJSONObject(key)
    fun arrOf(key: String): JSONArray? = obj?.optJSONArray(key)
}

/**
 * 单元测试注入点：把请求指向 MockWebServer，并分别验证"演示开/关"两种构建行为。
 * internal 且只在 test 源码里赋值；正式代码路径永远走 BuildConfig 的真值。
 */
internal object NetTestOverride {
    @Volatile var baseUrl: String? = null
    @Volatile var demoAllowed: Boolean? = null

    fun reset() {
        baseUrl = null
        demoAllowed = null
    }
}

/**
 * 缓存策略按端点**白名单**给，不再一刀切 60 秒（缺陷 #79 的第一格）。
 *
 * 旧写法给每一条 GET 都盖 `max-age=60`，于是"写完立刻回读"这一族会读到 60 秒内的旧答案 ——
 * 而全站的状态口径是"以回读为准"（§26/§27：段状态、交接状态、未读数都由回读决定）：
 * 司机点完"到达"后那一轮回读如果命中缓存，界面就把**刚才那一次动作**整个抹掉了，
 * 回执还写着"后端回读：运输中"，看着像后端没接受，其实是我们没去问。
 * 所以默认 no-store，只有"browse 类"（变化慢、重复进页面会重取）列进白名单。
 *
 * 独立成 `internal` 函数是为了能被用例直接判（`private class` 那个拦截器在 JVM 用例里摸不到）——
 * 不是为将来的调用方留口子：除拦截器外它没有别的调用点。
 */
private val CacheableGetPaths: Set<String> = setOf(
    "/transport/notice/list",
    "/transport/weather",
    "/transport/bus/lines",
    "/transport/bus/nearby",
    "/transport/product/list",
    "/transport/product/page",
    "/transport/product/get",
    "/transport/send/stations",
)

internal fun cachePolicyFor(path: String): String =
    if (CacheableGetPaths.any { suffix -> path.endsWith(suffix) }) "max-age=60" else "no-store"

internal class CacheControlInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (request.method != "GET") return response
        if (response.cacheControl.noStore) return response
        return response.newBuilder()
            .removeHeader("Pragma")
            .header("Cache-Control", cachePolicyFor(request.url.encodedPath))
            .build()
    }
}

/**
 * 挂起地发一次请求，并让**协程取消穿透到 socket**（缺陷 #79 的第二格）。
 *
 * 旧形状是 `withContext(Dispatchers.IO) { newCall(request).execute() }`：线程阻塞在 `execute()` 里，
 * 协程被取消（用户离开这一屏）时**没人去取消这条 Call** —— 请求照样跑完，占着连接池与流量，
 * 弱网下最坏一次能把三四个已离场页面的请求全拖在网上。`enqueue` + `invokeOnCancellation { cancel() }`
 * 才是"取消 = 这一次不该再有结果"的落地；被取消后到达的响应直接 close，不进任何人手里。
 */
private suspend fun OkHttpClient.callCancellable(request: Request): Response =
    suspendCancellableCoroutine { cont ->
        val call: Call = newCall(request)
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (cont.isActive) cont.resume(response) else response.close()
            }
        })
    }

object Api {
    private const val TAG = "BUS_API"
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    private const val CACHE_SIZE = 10L * 1024 * 1024 // 10 MB

    val baseUrl: String get() = NetTestOverride.baseUrl ?: BuildConfig.BASE_URL

    /**
     * 演示数据总开关：release 构建恒为 false（build.gradle 里硬写），
     * debug 默认 true，可用 `-Pbuspost.demo=false` / `BUSPOST_DEMO=false` 关掉做纯后端联调。
     */
    val demoAllowed: Boolean get() = NetTestOverride.demoAllowed ?: BuildConfig.DEMO_ALLOWED

    @Volatile
    var token: String? = null

    /** 401 时由 App 层注入：清理会话并跳回登录 */
    @Volatile
    var onUnauthorized: (() -> Unit)? = null

    /**
     * 会话续期回调，由 App 层注入（`Repo.refreshTokenOnce()`）。
     *
     * 为什么要有它：后端 access token 只有 1800 秒，而 `/member/auth/refresh-token` 一直存在却从没被调用过
     * ⇒ 30 分钟后的第一条业务请求必然 401，然后清盘、跳登录 —— 用户看到的是"每次进 App 都要重新登录"，
     * 实际上他根本没退出过。返回 true 表示已换到新 token（本层直接重试原请求，用户无感）。
     */
    @Volatile
    var refresher: (suspend () -> Boolean)? = null

    /** 登录走的是演示通道时为 true：演示会话下的 401 不踢回登录页（仅 demoAllowed 构建可能出现） */
    @Volatile
    var demoSession: Boolean = false

    /** 401 广播节流时刻：internal 只为单元测试可在用例之间复位 */
    @Volatile
    internal var last401At = 0L

    private var httpCache: Cache? = null

    fun initCache(context: Context) {
        if (httpCache == null) {
            httpCache = Cache(File(context.cacheDir, "http_cache"), CACHE_SIZE)
        }
    }

    /**
     * 换人/退登时清掉磁盘响应缓存（缺陷 #79 的第三格）。
     *
     * 以前 `Prefs.clearSession()` 只清 token 与档案，`cacheDir/http_cache` 里上一位的
     * 班次、订单、地址还要按 `max-age` 再活一会儿 —— 同设备换账号登录时，第一屏能看到**别人的**数据。
     * 这是会话卫生问题，不是性能问题，所以清不干净不能靠"反正缓存会过期"。
     *
     * ⚠ 只做 `evictAll()`，不删目录、也不换新实例：`http` 是 `by lazy`，客户端一旦建成就长期引用
     * 这一个 `Cache`。删掉目录会让它往不存在的 journal 上写（OkHttp 把整个缓存标成 fatal，之后所有
     * GET 再也存不进去，等于悄悄把缓存关掉）；换实例则会有两个 `Cache` 抢同一份 journal。
     */
    fun clearCache() {
        val cache = httpCache ?: return
        runCatching { cache.evictAll() }
            .onFailure { Log.w(TAG, "HTTP 缓存没清干净：${it.javaClass.simpleName} ${it.message}") }
    }

    /**
     * access token 的到期时刻（毫秒，0=没记过）。由 `Prefs.expiresAt` 在登录/续期两处一起写入。
     * 缺陷 #79 的第四格：后端一直在发 `expiresTime`，本机存了却**从没读过** ——
     * 于是明知过期也照样发出去，等一个必然的 401 才被踢回登录页，用户看到的是"没退出却要重登"。
     */
    @Volatile
    var tokenExpiresAt: Long = 0L

    /** 上一次"按 expiresAt 主动续期"的时刻：挡住时钟偏快时的续期风暴（见 rawExecute 那三重心护栏） */
    @Volatile
    internal var lastProactiveRenewAt = 0L

    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .apply { httpCache?.let { cache(it) } }
            .addNetworkInterceptor(CacheControlInterceptor())
            .build()
    }

    suspend fun get(path: String, query: Map<String, Any?> = emptyMap()): Payload =
        execute(path, "GET", query, null)

    /**
     * `query` 是给"后端用 `@RequestParam` 收参数"的 POST 端点的（审计 §39：
     * `/send/confirm-station-action` 的 orderId 在 query，塞进 JSON body 会直接 400）。
     */
    suspend fun post(
        path: String,
        body: Map<String, Any?> = emptyMap(),
        query: Map<String, Any?> = emptyMap(),
    ): Payload = execute(path, "POST", query, body)

    suspend fun put(
        path: String,
        body: Map<String, Any?> = emptyMap(),
        query: Map<String, Any?> = emptyMap(),
    ): Payload = execute(path, "PUT", query, body)

    suspend fun delete(path: String, query: Map<String, Any?> = emptyMap()): Payload =
        execute(path, "DELETE", query, null)

    /**
     * 严格请求：任何失败都抛出类型化异常（NetworkException / UnauthorizedException / ApiException）。
     * 只有在演示模式显式开启的构建里，才允许把**传输失败与业务失败**回落成 DemoData，且会打警告日志。
     * ⚠ 回落面**刻意只列这两个类型**：`IllegalStateException` / `NullPointerException` / `ClassCastException` /
     * `LinkageError` 这类**程序缺陷不在此列，原样抛出** —— 早先这里写的是 `catch (e: Throwable)`，
     * 等于让 bug 在演示构建里被洗成一份"看起来成功"的演示数据（JVM 用例 `DemoExceptionIsolationTest` 钉住这条）。
     */
    private suspend fun execute(
        path: String,
        method: String,
        query: Map<String, Any?>,
        body: Map<String, Any?>?,
    ): Payload = withContext(Dispatchers.IO) {
        try {
            rawExecute(path, method, query, body)
        } catch (e: CancellationException) {
            throw e
        } catch (e: NetworkException) {
            if (!demoAllowed) throw e
            Log.w(TAG, "$method $path 传输失败(${e.message}) —— 演示模式开启，回落演示数据")
            DemoData.respond(path, query, body, method)
        } catch (e: ApiException) {
            if (!demoAllowed) throw e
            Log.w(TAG, "$method $path 业务失败(code=${e.code}) —— 演示模式开启，回落演示数据")
            DemoData.respond(path, query, body, method)
        }
    }

    private suspend fun rawExecute(
        path: String,
        method: String,
        query: Map<String, Any?>,
        body: Map<String, Any?>?,
        attempt: Int = 0,
    ): Payload {
        if (baseUrl.isEmpty()) throw netFail(
            "这台设备还没填站点服务器地址，连不上。请联系站点的装机人员。",
            "config: local.properties 的 buspost.baseUrl 为空",
        )
        // 本机已经知道这个 access token 过期了（后端一直发 expiresTime，此前只写不读）：
        // 先静默续一次再发，省掉"必然 401 → 清盘 → 跳登录"那一整圈。
        // 三重护栏：① 认证接口自己不触发（续期走 /auth/，否则递归）；② 只在一分钟内主动续一次 ——
        // 设备时钟快过真实时间时，"已过期"会每条请求都成立，没有这道闸就是一请求一次续期的风暴；
        // ③ 续不上也无所谓，照旧发出去走原来的 401 出口（那条路是现成的，不会因为这里改了而变弱）。
        val nowMillis = System.currentTimeMillis()
        if (attempt == 0 && !path.contains("/auth/") &&
            tokenExpiresAt in 1..nowMillis && nowMillis - lastProactiveRenewAt > 60_000L
        ) {
            lastProactiveRenewAt = nowMillis
            if (refresher?.invoke() == true) Log.i(TAG, "$path 按 expiresAt 判定会话已过期 —— 已先静默续期")
        }
        val url = buildString {
            append(baseUrl)
            append(path)
            val q = query.filterValues { it != null && it.toString().isNotEmpty() }
            if (q.isNotEmpty()) {
                append('?')
                q.entries.forEachIndexed { i, (k, v) ->
                    if (i > 0) append('&')
                    append(k).append('=').append(java.net.URLEncoder.encode(v.toString(), "UTF-8"))
                }
            }
        }
        val jsonBody = JSONObject().apply {
            body?.forEach { (k, v) -> if (v != null && v.toString().isNotEmpty()) put(k, v) }
        }
        val requestBody = jsonBody.toString().toRequestBody(JSON_MEDIA)
        val request = Request.Builder()
            .url(url)
            .apply {
                when (method) {
                    "GET" -> get()
                    "DELETE" -> delete(if (jsonBody.length() > 0) requestBody else null)
                    "PUT" -> put(requestBody)
                    else -> post(requestBody)
                }
            }
            .apply {
                token?.takeIf { it.isNotEmpty() }?.let { header("Authorization", "Bearer $it") }
                header("App-Channel", "android")
            }
            .build()

        val response = try {
            http.callCancellable(request)
        } catch (e: IOException) {
            throw netFail(
                "连不上站点服务器。请看看手机能不能上网，再刷新一次。",
                "connect: ${e.javaClass.simpleName} ${e.message}",
            )
        }
        return response.use { resp ->
            val text = try {
                resp.body?.string() ?: ""
            } catch (e: IOException) {
                throw netFail(
                    "站点服务器的回话没读完整。请刷新再试。",
                    "read-body: ${e.javaClass.simpleName} ${e.message}",
                )
            }
            val root = JSONObjectOrNull(text)
            val bizCode = root?.optInt("code", -1) ?: -1
            val reason401 = root?.optString("msg")?.takeIf { it.isNotEmpty() } ?: "登录已失效，请重新登录"
            if (resp.code == 401 || bizCode == 401) {
                // 认证接口的 401 是"账号密码/验证码不对"，不是"会话失效"：
                // 抛普通 ApiException 把后端原因原样交给登录页，绝不广播踢人回登录页
                if (path.contains("/auth/")) throw ApiException(401, reason401)
                // 会话到期 ≠ 用户要重新登录：先静默续一次，成功就重试本次请求（用户无感）。
                // 只试一次（attempt < 1）；续期接口自身命中上面的 /auth/ 分支，不会递归。
                val refreshed = if (attempt < 1) refresher?.invoke() else false
                if (refreshed == true) {
                    Log.i(TAG, "$path 401 —— 已静默续期，重试一次")
                    return rawExecute(path, method, query, body, attempt + 1)
                }
                throw kick(path, reason401)
            }
            if (root == null) {
                if (resp.isSuccessful) throw netFail(
                    "站点服务器发回来的内容读不懂。请刷新再试；一直这样请联系站点。",
                    "bad-json http=${resp.code} head=${text.take(160)}",
                )
                throw businessOrTransport(resp.code, text)
            }
            if (bizCode == -1) throw businessOrTransport(resp.code, text)
            if (bizCode != 0) throw ApiException(bizCode, root.optString("msg", "请求失败"))
            // 真实空数据就是空：Payload 结构完整、list 为空，页面渲染 EmptyState，不再造演示数据
            val data = root.opt("data")
            if (data == null || data === JSONObject.NULL) return Payload(JSONObject(), null, demo = false)
            toPayload(data, demo = false)
        }
    }

    private fun businessOrTransport(httpCode: Int, text: String): Exception {
        val snippet = text.take(60).replace(Regex("\\s+"), " ")
        // 服务器回的那 60 字原文以前是**拼在屏上那句后面**的（一段 HTML 错误页也能上屏）。
        // 现在它只进日志；屏上留的是"谁没应答 + 下一步"。
        return if (httpCode in 500..599) {
            netFail(
                "站点服务器这会儿不接受询问。过一会儿再刷新一次。",
                "server http=$httpCode body=$snippet",
            )
        } else {
            apiFail(
                httpCode,
                "站点服务器拒绝了这次操作。请刷新再试；一直这样请联系站点。",
                "client http=$httpCode body=$snippet",
            )
        }
    }

    /** 未授权统一出口：真实构建踢回登录；演示构建只标记会话为演示态 */
    private fun kick(path: String, message: String): UnauthorizedException {
        if (demoAllowed) {
            demoSession = true
            Log.i(TAG, "$path 返回 401 —— 演示模式，按演示数据处理")
        } else {
            val now = System.currentTimeMillis()
            if (now - last401At > 2000) {
                last401At = now
                onUnauthorized?.invoke()
            }
        }
        return UnauthorizedException(message)
    }

    /** 绝对地址的裸 GET（天气等第三方开放接口用，不带 Authorization 头） */
    suspend fun rawGet(url: String): String = withContext(Dispatchers.IO) {
        val resp = try {
            http.callCancellable(Request.Builder().url(url).get().build())
        } catch (e: IOException) {
            throw netFail(
                "连不上外部服务。请看看手机能不能上网，稍后再试一次。",
                "rawGet connect: ${e.javaClass.simpleName} ${e.message}",
            )
        }
        resp.use { r ->
            val body = try {
                r.body?.string() ?: ""
            } catch (e: IOException) {
                throw netFail(
                    "外部服务的回话没读完整。稍后再试一次。",
                    "rawGet read-body: ${e.javaClass.simpleName} ${e.message}",
                )
            }
            if (!r.isSuccessful) throw apiFail(
                r.code,
                "外部服务这会儿没回话（天气这类第三方数据就是这一路）。稍后再试。",
                "rawGet http=${r.code}",
            )
            if (body.isEmpty()) throw netFail("外部服务回了空内容。稍后再试。", "rawGet 空响应体 http=${r.code}")
            body
        }
    }

    /**
     * 图片上传：成功才返回真实 url；失败一律抛出异常。
     * 司机装车/妥投凭证必须是真实上传的图，演示模式关闭时不存在"假 url"。
     */
    suspend fun upload(path: String, bytes: ByteArray, fileName: String): Payload = withContext(Dispatchers.IO) {
        try {
            rawUpload(path, bytes, fileName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: NetworkException) {
            if (!demoAllowed) throw e
            Log.w(TAG, "upload $path 传输失败(${e.message}) —— 演示模式开启，回落演示数据")
            DemoData.respond(path, emptyMap(), null, "POST")
        } catch (e: ApiException) {
            if (!demoAllowed) throw e
            Log.w(TAG, "upload $path 业务失败(code=${e.code}) —— 演示模式开启，回落演示数据")
            DemoData.respond(path, emptyMap(), null, "POST")
        }
    }

    private suspend fun rawUpload(path: String, bytes: ByteArray, fileName: String): Payload {
        if (baseUrl.isEmpty()) throw netFail(
            "这台设备还没填站点服务器地址，连不上。请联系站点的装机人员。",
            "config: local.properties 的 buspost.baseUrl 为空",
        )
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", fileName, bytes.toRequestBody("application/octet-stream".toMediaType()))
            .build()
        val request = Request.Builder()
            .url(baseUrl + path)
            .post(body)
            .apply { token?.takeIf { it.isNotEmpty() }?.let { header("Authorization", "Bearer $it") } }
            .build()
        val resp = try {
            http.callCancellable(request)
        } catch (e: IOException) {
            throw netFail(
                "照片传不出去：连不上站点服务器。请换一张能上网的卡或 Wi-Fi 再试。",
                "upload connect: ${e.javaClass.simpleName} ${e.message}",
            )
        }
        return resp.use { r ->
            val text = try {
                r.body?.string() ?: ""
            } catch (e: IOException) {
                throw netFail(
                    "照片传上去后的回话没读完整。请重拍再传一次。",
                    "upload-read: ${e.javaClass.simpleName} ${e.message}",
                )
            }
            if (r.code == 401) throw kick(path, "登录已失效，请重新登录")
            val root = JSONObjectOrNull(text) ?: throw netFail(
                "照片传上去后服务器回的内容读不懂。请重拍再传一次。",
                "upload bad-json http=${r.code} head=${text.take(160)}",
            )
            val code = root.optInt("code", -1)
            if (code == 401) throw kick(path, "登录已失效，请重新登录")
            if (code == -1) throw businessOrTransport(r.code, text)
            if (code != 0) throw ApiException(code, root.optString("msg", "上传失败"))
            val data = root.opt("data")
            if (data == null || data === JSONObject.NULL) throw netFail(
                "照片传成功了，但服务器没回图片地址。请重传一次。",
                "upload no-url http=${r.code} body=${text.take(160)}",
            )
            toPayload(data, demo = false)
        }
    }

    private fun JSONObjectOrNull(text: String): JSONObject? =
        if (text.isBlank()) null else runCatching { JSONObject(text) }.getOrNull()

    private fun toPayload(data: Any, demo: Boolean): Payload = when (data) {
        is JSONArray -> Payload(null, data, demo)
        is JSONObject -> Payload(data, null, demo)
        else -> Payload(JSONObject().put("v", data), null, demo, scalar = data)
    }

    /**
     * 演示模式专用的构造入口（DemoData 回填响应用）。
     * 生产构建调用到这里会抛错，防止任何一条路径把演示数据当真实数据端出去。
     */
    fun wrap(data: Any?): Payload {
        if (!demoAllowed) throw IllegalStateException("生产构建禁止演示数据")
        return toPayload(data ?: JSONObject(), demo = true)
    }
    /**
     * 上屏的话与留在 logcat 的话**分两头说**（2026-10-19 发布口径：这一版是要发的包，
     * 屏上不许出现结果码、IP、端口、毫秒、"接口/报文"这类开发词）。
     *
     * ⚠ 这不是把原因藏起来：屏上那句照样说清**谁没应答、用户下一步做什么**（#109 定的口径，
     * 拿"网络不太好"盖掉一切才是缺陷）。丢掉的只有开发者才读得懂的那半截，
     * 它整条进 logcat 的 `BUS_NET`，装机与排查的人照旧读得到 —— **撤的是措辞，不是证据**。
     */
    private fun netFail(userWords: String, detail: String): NetworkException {
        Log.w(TAG, "BUS_NET $detail")
        return NetworkException(userWords)
    }

    private fun apiFail(httpCode: Int, userWords: String, detail: String): ApiException {
        Log.w(TAG, "BUS_NET $detail")
        return ApiException(httpCode, userWords)
    }

}
