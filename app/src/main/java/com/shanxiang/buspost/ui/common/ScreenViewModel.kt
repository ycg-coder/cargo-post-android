package com.shanxiang.buspost.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shanxiang.buspost.core.net.Api
import com.shanxiang.buspost.data.repo.Feed
import com.shanxiang.buspost.data.repo.One
import com.shanxiang.buspost.data.repo.RemoteState
import kotlinx.coroutines.launch

/**
 * 页面级 ViewModel 基类：把"在加载 / 请求失败 / 真的没数据 / 演示数据"四件事分开。
 * Api 层已是严格的（失败抛类型化异常，不再回落演示数据），所以这里 error 非空就代表后端没给数据，
 * 页面必须显示错误条 + 重试，而不是渲染成空列表让用户以为"没有内容"。
 */
abstract class ScreenViewModel : ViewModel() {
    var loading by mutableStateOf(true)
        protected set

    /** 上一次请求的失败原因；成功后必须清空，否则错误条会一直挂着 */
    var error by mutableStateOf<String?>(null)
        protected set

    /** 当前展示的数据是否来自演示模式（release 恒为 false） */
    var demo by mutableStateOf(false)
        protected set

    /** 请求成功且后端确实返回空集合 —— 只有这个为真才允许渲染 EmptyState */
    var empty by mutableStateOf(false)
        protected set

    protected fun work(reset: Boolean = true, block: suspend () -> Unit) {
        viewModelScope.launch {
            if (reset) loading = true
            error = null
            try {
                block()
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 换页 / ViewModel 销毁 / 新一轮覆盖旧轮都会取消这一轮：取消不是"加载失败"，
                // 吞掉它就会把 error 与 loading=false 写在已经离场的画面上（指令 §七）。
                throw e
            } catch (e: Exception) {
                error = com.shanxiang.buspost.data.repo.errorTextOf(e)
            } finally {
                loading = false
            }
        }
    }

    /** 多个接口并行取数后统一判定是否演示数据 */
    protected fun mergeDemo(vararg flags: Boolean) {
        demo = flags.any { it }
    }

    /** 会话已失效：本地立刻清干净，页面不再拿旧 token 反复重试 */
    protected fun sessionExpired(): Boolean = error?.contains("登录已失效") == true

    /** 演示模式下 Api 会把失败回填成演示数据，error 为空是预期的 */
    protected fun demoAllowed() = Api.demoAllowed
}

/**
 * 分页取数器：统一 pageNo/pageSize 口径，覆盖首屏、下拉刷新、上拉加载、末页、空态、失败重试。
 * 失败时**保留已加载的页**（pageNo 不回退），用户下拉即可重试，不会把已经看到的内容清空。
 */
class Paginator<T>(val pageSize: Int = 20) {
    var items by mutableStateOf<List<T>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var loadingMore by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var demo by mutableStateOf(false)
        private set
    var hasMore by mutableStateOf(false)
        private set

    /** 还没成功取过一次：为 true 时页面才显示骨架屏，之后刷新都用 loading 指示条 */
    var initialized by mutableStateOf(false)
        private set

    private var pageNo = 0

    val isEmpty: Boolean get() = initialized && error == null && items.isEmpty()

    /** append=true 上拉加载下一页；false 首屏或下拉刷新（回到第 1 页并整体替换） */
    suspend fun load(append: Boolean, fetch: suspend (pageNo: Int, pageSize: Int) -> Feed<T>) {
        if (append) {
            if (loadingMore || loading || !hasMore) return
            loadingMore = true
        } else {
            loading = true
            error = null
        }
        val next = if (append) pageNo + 1 else 1
        try {
            val feed = fetch(next, pageSize)
            if (feed.error != null) throw IllegalStateException(feed.error)
            val fresh = feed.items
            items = if (append) items + fresh else fresh
            pageNo = next
            demo = feed.demo
            hasMore = fresh.size >= pageSize && (feed.total <= 0 || items.size < feed.total)
            error = null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            error = com.shanxiang.buspost.data.repo.errorTextOf(e)
        } finally {
            loading = false
            loadingMore = false
            initialized = true
        }
    }

    /** 写操作成功后重拉第一页：让后端成为列表的唯一真值 */
    suspend fun reload(fetch: suspend (pageNo: Int, pageSize: Int) -> Feed<T>) {
        pageNo = 0
        load(append = false, fetch = fetch)
    }

    fun state() = RemoteState(
        data = items,
        loading = loading,
        error = error,
        demo = demo,
        fromServer = initialized && !demo && error == null,
        success = initialized && error == null,
    )
}

/** Feed → 页面状态：error 与"成功但空"分开表达 */
fun <T> Feed<T>.toState(): RemoteState<List<T>> = RemoteState(
    data = items,
    error = error,
    demo = demo,
    fromServer = !demo && error == null,
    success = error == null,
)

fun <T> One<T>.toState(): RemoteState<T> = RemoteState(
    data = value,
    error = error,
    demo = demo,
    fromServer = !demo && error == null,
    success = error == null,
)
