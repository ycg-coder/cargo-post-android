package com.shanxiang.buspost.ui.driver

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationManagerCompat
import com.shanxiang.buspost.data.store.Prefs
import com.shanxiang.buspost.notify.Channels
import com.shanxiang.buspost.notify.Notifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/** 一句话实际走了哪个声道；屏上要说清楚，不能"以为出声了" */
enum class AnnounceChannel { TTS, NOTIFICATION, TEXT_ONLY }

/**
 * 到近播报的**出口**：把 [ApproachFeed] 的一帧交给 [ApproachTracker]，再把判出来的那句话发出去。
 *
 * 三层职责分开是刻意的：说不说、说什么，全在纯层（[MissionApproach] / [ApproachTracker]，JVM 可测）；
 * 这一层只管"用什么出声"和"没出声时司机怎么知道"，所以它自己不决定任何阈值。
 *
 * 三档声道，逐档降级且**每一档都写回屏上**：
 * ① 系统 TTS（`android.speech.tts`）—— 业务内容只能本机说：高德的内置语音只播它自己的转向句子
 *    （引擎层那条「本机不合成高德的话」的红线管的是那句，不针对本机的作业提醒）；
 * ② 系统 TTS 起不来 / 没有中文语音数据 / speak() 返回失败 ⇒ 转通知栏（`Channels.SHIFT` 是 HIGH，会震会响）；
 * ③ 通知也被系统关掉（未授权 / 用户关了渠道）⇒ 只剩屏幕那一行，并且明说"本机没出声"。
 *
 * ⚠ 已知未收口：本机 TTS 与高德内置语音是**两个引擎**，导航中两路声音会叠着说，本机没有做仲裁
 * （高德的 `isTtsPlaying()` 只在导航引擎可用时读得到，x86_64 那侧至今没有设备证据）。
 */
class ApproachAnnouncer(private val context: Context) {

    private val tracker = ApproachTracker()

    private val _last = MutableStateFlow<String?>(null)

    /** 最近说出去的完整文本（屏上回显；null = 还没说过） */
    val last: StateFlow<String?> = _last

    private val _status = MutableStateFlow("还没进入要说话的距离")

    /** 这一层现在的出声能力（初始化中 / 可用 / 不可用 + 原因），屏上直接显示 */
    val status: StateFlow<String> = _status

    private var tts: TextToSpeech? = null
    private var ttsInit: Int = PENDING

    @Synchronized
    internal fun ensureTts() {
        if (tts != null || ttsInit != PENDING) return
        ttsInit = PENDING
        tts = TextToSpeech(context) { status ->
            ttsInit = status
            if (status != TextToSpeech.SUCCESS) {
                _status.value = "语音引擎初始化失败，到近提醒转通知栏"
            } else {
                val lang = tts?.setLanguage(Locale.CHINA)
                _status.value = if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED)
                    "没有中文语音数据，到近提醒转通知栏"
                else "语音引擎就绪，到近会读出来"
            }
        }
    }

    /** 换班 / 重新拉取班次后重新武装：让同一批点能重新说话 */
    fun reset() = tracker.reset()

    /** @return 这一帧真正说出去的那句（null = 按判据不说话，或开关关着） */
    fun onFrame(frame: ApproachFrame): String? {
        if (frame is ApproachFrame.Silent) {
            _status.value = frame.reason
            return null
        }
        val ready = frame as ApproachFrame.Ready
        if (!Prefs.approachVoice) return null
        val text = tracker.next(ready.stop, ready.meters, ready.source) ?: return null
        _last.value = text
        deliver(text)
        return text
    }

    private fun deliver(text: String) {
        val engine = tts
        if (ttsInit == TextToSpeech.SUCCESS && engine != null) {
            val r = engine.speak(text, TextToSpeech.QUEUE_ADD, null, "approach")
            if (r == TextToSpeech.SUCCESS) {
                _status.value = "已读出来（系统语音）"
                return
            }
        }
        // 走到这里 = 本机没出声。通知栏是唯一还能出声的地方，但它同样可能被系统关掉。
        val mgr = NotificationManagerCompat.from(context)
        if (mgr.areNotificationsEnabled()) {
            Notifier.push(
                context = context,
                channel = Channels.SHIFT,
                title = "到近作业提醒",
                body = text,
                notificationId = NOTIFICATION_ID,
            )
            _status.value = "系统语音没出声，已转通知栏（点了会进工作台）"
        } else {
            _status.value = "未出声：既没有可用语音，通知也被系统关着 —— 只有这一行字"
        }
    }

    fun shutdown() {
        tracker.reset()
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsInit = PENDING
    }

    private companion object {
        const val PENDING = Int.MIN_VALUE
        const val NOTIFICATION_ID = 4801
    }
}
