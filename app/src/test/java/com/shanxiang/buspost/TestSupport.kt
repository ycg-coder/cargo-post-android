package com.shanxiang.buspost

import android.content.SharedPreferences
import com.shanxiang.buspost.core.net.Api
import com.shanxiang.buspost.core.net.NetTestOverride
import com.shanxiang.buspost.data.repo.Repo
import com.shanxiang.buspost.data.store.Prefs
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before

/**
 * JVM 单元测试里没有 Android 运行时，Prefs 的 SharedPreferences 只能换成内存版；
 * 通过反射注入，避免为了测试在生产代码里开设置口子。
 */
class MemoryPrefs : SharedPreferences {
    val values = ConcurrentHashMap<String, Any?>()

    override fun getAll(): MutableMap<String, *> = HashMap(values)
    override fun getString(key: String, def: String?): String? = values[key] as String? ?: def
    override fun getStringSet(key: String, def: MutableSet<String>?): MutableSet<String>? = def
    override fun getInt(key: String, def: Int) = values[key] as? Int ?: def
    override fun getLong(key: String, def: Long) = values[key] as? Long ?: def
    override fun getFloat(key: String, def: Float) = values[key] as? Float ?: def
    override fun getBoolean(key: String, def: Boolean) = values[key] as? Boolean ?: def
    override fun contains(key: String) = values.containsKey(key)
    override fun edit(): SharedPreferences.Editor = Editor()
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    private inner class Editor : SharedPreferences.Editor {
        override fun putString(key: String, value: String?): SharedPreferences.Editor = apply(key, value)
        override fun putStringSet(key: String, value: MutableSet<String>?): SharedPreferences.Editor = apply(key, value)
        override fun putInt(key: String, value: Int): SharedPreferences.Editor = apply(key, value)
        override fun putLong(key: String, value: Long): SharedPreferences.Editor = apply(key, value)
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor = apply(key, value)
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = apply(key, value)
        override fun remove(key: String): SharedPreferences.Editor = also { values.remove(key) }
        override fun clear(): SharedPreferences.Editor = also { values.clear() }
        override fun commit(): Boolean = true
        override fun apply() {}

        private fun apply(key: String, value: Any?): SharedPreferences.Editor {
            if (value == null) values.remove(key) else values[key] = value
            return this
        }
    }
}

object TestHost {
    private val mainExecutor = Executors.newSingleThreadExecutor()

    fun installPrefs(): MemoryPrefs {
        val prefs = MemoryPrefs()
        val field: Field = Prefs::class.java.getDeclaredField("sp")
        field.isAccessible = true
        field.set(Prefs, prefs)
        return prefs
    }

    /** viewModelScope 走 Dispatchers.Main，测试里给它一个真实线程，网络回来后能继续跑完 */
    fun installMainDispatcher() = Dispatchers.setMain(mainExecutor.asCoroutineDispatcher())

    /** 伪装正式构建：演示数据总开关关闭，任何失败都必须原样抛出 */
    fun strictBackend() { NetTestOverride.demoAllowed = false }

    /** 伪装 debug 默认形态：允许失败回落演示数据 */
    fun demoBackend() { NetTestOverride.demoAllowed = true }

    fun wipeApiSession() {
        Api.token = null
        Api.demoSession = false
        Api.onUnauthorized = null
        // 续期回调是 object Api 上的进程级状态：不复位的话，上一个用例装的 refresher
        // 会把下一个用例的 401 静默续掉，"该被踢回登录"的那条断言就永远绿不了
        Api.refresher = null
        Api.last401At = 0L
    }

    /**
     * V-1 的幂等闸挂在 `object Repo` 上，是**进程级**的：键与"成功后 10 秒窗口"会跨用例活着。
     * Gradle 默认所有测试类共用一个 JVM，于是"上一个用例刚确认过 handoverId=91"会把下一个用例的
     * 同键请求当重复吞掉（实测让 `ScalarEnvelopeContractTest` 的整链路用例拿到 null 请求后 NPE）。
     * 与 [installPrefs] 同一手法用反射复位，**不在生产代码上开测试专用的口子**。
     *
     * F-2 第 6 步起了用户域第二个闸门 `userCreateGate`， hazards 一模一样（`SendOrderTest` 八个用例里有
     * 六个提交**内容完全相同**的 `form`，不复位的话第二个用例就拿不到 POST 了），所以复位**按字段类型扫**
     * （凡是 `ActionGate` 类型的 `Repo` 字段都清），不写死字段名清单 —— 写死的那版本轮就漏过一把闸。
     */
    fun resetActionGate() {
        val gateNames = Repo::class.java.declaredFields
            .filter { it.type.simpleName == "ActionGate" }
            .map { it.name }
        for (gateName in gateNames) {
            val field = Repo::class.java.getDeclaredField(gateName)
            field.isAccessible = true
            val gate = field.get(Repo)
            for (name in arrayOf("locks", "heldUntil")) {
                val map = gate.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(gate)
                (map as java.util.concurrent.ConcurrentHashMap<*, *>).clear()
            }
        }
    }
}

/** 等条件成立（viewModelScope 里的协程是异步的，断言前必须等它跑完） */
fun awaitUntil(timeoutMs: Long = 5_000, condition: () -> Boolean) = runBlocking {
    withTimeout(timeoutMs) {
        while (!condition()) delay(20)
    }
    Unit
}

/** 每个用例起一个 MockWebServer，把 Api 指过去；后端行为完全由用例控制 */
abstract class ServerTest {
    protected val server = MockWebServer()
    protected lateinit var prefs: MemoryPrefs

    @Before
    fun setUpServer() {
        prefs = TestHost.installPrefs()
        TestHost.wipeApiSession()
        TestHost.resetActionGate()
        server.start()
        // 与真实配置同形：不带结尾斜杠，路径自身以 / 开头
        NetTestOverride.baseUrl = server.url("/").toString().trimEnd('/')
    }

    @After
    fun tearDownServer() {
        NetTestOverride.reset()
        TestHost.wipeApiSession()
        runCatching { server.shutdown() }
    }

    protected fun respond(body: String, code: Int = 200, contentType: String = "application/json") {
        server.enqueue(MockResponse().setResponseCode(code).setHeader("Content-Type", contentType).setBody(body))
    }

    /** 芋道信封：成功 */
    protected fun respondData(data: String) = respond("""{"code":0,"msg":"","data":$data}""")

    /** 芋道信封：HTTP 200 但业务失败，msg 是后端真实原因 */
    protected fun respondBizError(code: Int, msg: String) = respond("""{"code":$code,"msg":"$msg","data":null}""")

    protected fun lastRequest(): RecordedRequest = server.takeRequest()

    protected fun bodyOf(request: RecordedRequest) = request.body.readUtf8()
}
