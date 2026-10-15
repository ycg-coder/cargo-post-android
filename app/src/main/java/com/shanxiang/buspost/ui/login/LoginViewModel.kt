package com.shanxiang.buspost.ui.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shanxiang.buspost.core.net.Api
import com.shanxiang.buspost.core.net.ApiException
import com.shanxiang.buspost.core.net.NetworkException
import com.shanxiang.buspost.core.net.UnauthorizedException
import com.shanxiang.buspost.data.model.Role
import com.shanxiang.buspost.data.repo.Repo
import com.shanxiang.buspost.data.store.Prefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 日志标签：`sendSmsCode` / `login` 的**客户端缺陷**分支要把堆栈落在这儿才可能被复现（审计 §80 二）。 */
private const val TAG = "BUS_LOGIN"

class LoginViewModel : ViewModel() {

    var role by mutableStateOf(Prefs.role)
    var mode by mutableStateOf(0) // 0 密码登录，1 验证码登录
    var mobile by mutableStateOf(Prefs.mobile)
    var password by mutableStateOf("")
    var code by mutableStateOf("")
    var agreed by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var notice by mutableStateOf<String?>(null)
    var countdown by mutableStateOf(0)

    /** 演示入口只在显式开启演示模式的构建里出现；release 恒为 false，页面上也不该有这个按钮 */
    val demoEntryAvailable: Boolean get() = Api.demoAllowed

    // 码长跟着后端契约走：`AppAuthSmsLoginReqVO.code` 是 `@Length(min = 4, max = 6)` + 全数字。
    // 旧写法 `4..8` 的上半段是空话：输入框本来就 `take(6)`（LoginScreen.kt:148），
    // 而 7-8 位后端永远发不出来也永远不收 ⇒ 能提交的那一格不存在，等于在校验里撒了个没人的范围。
    val canSubmit: Boolean
        get() = mobile.length == 11 && agreed && !busy &&
            (if (mode == 0) password.length >= 6 else code.length in 4..6)

    /**
     * 改手机号 = 换了一个人：上一位留下的发送成功语、倒计时、失败原因全部作废。
     * 真机实测（2026-10-15，v70）：先用 13800000000 以司机身份登录被拒（"该账号未绑定司机档案"），
     * 把号码改成司机号 13800138001 之后，那句判决还挂在输入框下面 —— 界面在拿**另一个人**的
     * 后端结论回答这一位。倒计时同理：60 秒是给上一个号码的，留着它等于这一位永远点不到"获取"。
     */
    fun onMobileChange(next: String) {
        val cleaned = next.filter { it.isDigit() }.take(11)
        if (cleaned == mobile) return
        mobile = cleaned
        code = ""
        notice = null
        error = null
        countdown = 0
    }

    fun sendSms() {        if (mobile.length != 11) {
            error = "先填写 11 位手机号"
            return
        }
        error = null
        busy = true
        viewModelScope.launch {
            val sent = try {
                Repo.sendSmsCode(mobile)
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                notice = e.loginMessage("验证码发送失败")
                false
            } catch (e: ApiException) {
                notice = e.loginMessage("验证码发送失败")
                false
            } catch (e: Exception) {
                // 既不是连不上后端、也不是后端业务码：这是客户端缺陷。
                // ⚠ 原来这里写的是 `catch (e: Throwable)` —— 那连 LinkageError / OutOfMemoryError 都会一起被洗成
                // 一句"验证码发送失败"，而且堆栈丢掉，缺陷再也复现不了。现在：类型化失败走文案，缺陷进日志。
                android.util.Log.e(TAG, "sendSmsCode 客户端缺陷（不是网络问题）", e)
                notice = "验证码发送失败：客户端缺陷 ${e.javaClass.simpleName}"
                false
            }
            busy = false
            if (sent) {
                notice = "验证码已发送"
                countdown = 60
                while (countdown > 0) {
                    delay(1000)
                    countdown -= 1
                }
            }
        }
    }

    /**
     * 演示入口走的仍是同一条登录链路，只是账号来自工程外配置。
     * 口令不写进源码：debug 从 local.properties 的 buspost.demoMobile / buspost.demoPassword 注入 BuildConfig，
     * release 两个字段恒为空串且演示入口本身不显示。
     */
    fun demoEnter(onDone: (Role) -> Unit) {
        if (!demoEntryAvailable) {
            error = "当前构建不支持演示登录"
            return
        }
        val demoMobile = com.shanxiang.buspost.BuildConfig.DEMO_MOBILE
        val demoPassword = com.shanxiang.buspost.BuildConfig.DEMO_PASSWORD
        if (demoMobile.isEmpty() || demoPassword.length < 6) {
            error = "演示账号未配置：在 local.properties 写 buspost.demoMobile 与 buspost.demoPassword（只对本机 debug 生效）"
            return
        }
        mobile = demoMobile
        password = demoPassword
        agreed = true
        if (mode == 1) mode = 0
        submit(onDone)
    }

    fun submit(onDone: (Role) -> Unit) {
        if (mobile.length != 11) {
            error = "请输入 11 位手机号"
            return
        }
        if (mode == 0 && password.length < 6) {
            error = "密码至少 6 位"
            return
        }
        if (mode == 1 && code.length !in 4..6) {
            // 说清是"几位到几位"，而不是"请输入短信验证码" —— 用户对着一个 6 位框被打回时，
            // 需要知道是自己少输了、还是这框压根不收（后端 @Length(min=4,max=6)）
            error = "请输入 4-6 位短信验证码"
            return
        }
        if (!agreed) {
            error = "请先阅读并同意服务协议"
            return
        }
        error = null
        busy = true
        val chosen = role
        viewModelScope.launch {
            try {
                val account = if (mode == 0) Repo.login(mobile, password, chosen) else Repo.smsLogin(mobile, code, chosen)
                // 登录成功以服务端 accessToken 为准：真登录成功后演示标记必须清零
                if (!account.token.startsWith("demo-")) Api.demoSession = false
                Repo.refreshUser(chosen)
                if (chosen == Role.DRIVER) {
                    val bound = Repo.bindDriver()
                    if ((bound.value?.driverId ?: 0L) <= 0L) {
                        error = bound.error ?: "该账号未绑定司机档案，可切到用户端或联系站点核对工号"
                        return@launch
                    }
                }
                Prefs.loginRedirect = ""
                // onDone 会立刻导航到业务壳；busy 必须先进终态，否则回调方读到的还是"正在提交"
                busy = false
                onDone(chosen)
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                error = e.loginMessage("登录失败")
            } catch (e: ApiException) {
                error = e.loginMessage("登录失败")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "login 客户端缺陷（不是网络问题）", e)
                error = "登录失败：客户端缺陷 ${e.javaClass.simpleName}"
            } finally {
                busy = false
            }
        }
    }
}

/**
 * 登录相关的错误文案：把后端/网络的真实原因说出来。
 * 不再出现"请检查网络或改用演示账号"这类把失败洗成用户操作问题的提示。
 */
private fun Throwable.loginMessage(prefix: String): String = when (this) {
    is UnauthorizedException -> message
    // 账号密码错（芋道 1009003000 等）要把后端 msg 直接透出来，用户才知道该改什么
    is ApiException -> message.ifEmpty { "$prefix：账号或密码不正确" }
    is NetworkException -> "网络连不上后端，请检查网络或确认服务器地址配置"
    else -> message ?: prefix
}
