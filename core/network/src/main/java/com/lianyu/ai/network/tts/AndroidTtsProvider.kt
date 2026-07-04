package com.lianyu.ai.network.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.lianyu.ai.common.SecureLog
import com.lianyu.ai.common.TimeoutBudgets
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class AndroidTtsProvider : TtsProviderInterface {
    private var tts: TextToSpeech? = null
    private val initLock = Any()
    // 标记 TTS 引擎是否真正就绪（回调 SUCCESS + 语言设置完成）。
    // 并发调用 initialize() 时，未就绪的调用方挂起等待，而非提前返回 true。
    @Volatile private var ready: Boolean = false

    override suspend fun synthesize(context: Context, text: String, voiceId: String?): String? {
        // [P2 REVIEW FIX] synthesize 的 deferred.await() 无超时，若 TTS 回调丢失会永久挂起。
        // 包 withTimeoutOrNull 防泄漏（超时返回 null，调用方已有 null 处理逻辑）。
        return withTimeoutOrNull(TimeoutBudgets.TTS_SYNTH_MS) {
            val t = tts ?: return@withTimeoutOrNull null
            val deferred = CompletableDeferred<Unit>()

            try {
                t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { deferred.complete(Unit) }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { deferred.complete(Unit) }
                })
                t.setSpeechRate(1.0f)
                t.setPitch(1.0f)
                t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_${System.currentTimeMillis()}")
                deferred.await()
            } catch (e: Exception) {
                SecureLog.e("AndroidTTS", "speak error", e)
            }
            null
        }
    }

    override fun getVoices(): List<TtsVoice> = listOf(
        TtsVoice("default", "系统默认语音", "Female", "zh", "使用系统内置TTS引擎")
    )

    override suspend fun testConnection(): Boolean = true

    fun isInitialized(): Boolean = ready

    /**
     * 初始化 Android 系统 TTS 引擎。
     *
     * [TextToSpeech] 的构造回调在系统 binder 线程异步触发，用 [CompletableDeferred]
     * 桥接为 suspend —— 调用方挂起等待初始化完成，不阻塞任何线程。
     *
     * 由 [TtsService.synthesize] / [TtsService.testWithSampleText] 在
     * `withContext(Dispatchers.IO)` suspend 上下文中调用，因此本方法声明为 suspend。
     */
    suspend fun initialize(context: Context): Boolean {
        // synchronized 与 suspend await 不兼容（await 在临界区内会报 "suspension point inside critical section"）。
        // 拆分两阶段：① synchronized 内构造 TextToSpeech 并拿 deferred；② 离开锁后 await 等回调。
        // [P2 REVIEW FIX] 用 ready 标记替代 tts!=null 判断：tts 在阶段1就非 null，但引擎可能未就绪。
        // 并发调用方在 ready==true 前挂起等待，而非提前返回 true 导致 synthesize 永久挂起。
        if (ready) return true

        // 阶段1：在锁内构造（快速返回 deferred，不挂起）
        data class Setup(val deferred: CompletableDeferred<Unit>, val successRef: AtomicBoolean)
        val setup: Setup? = synchronized(initLock) {
            if (ready) return@synchronized null  // 已被并发线程完整初始化
            // 注意：tts 可能已非 null（阶段1已构造但阶段3未完成），此时复用已有 tts 的 deferred
            // 简化：若 tts!=null 且 !ready，说明另一个协程正在阶段2 await，这里不重复构造，
            // 而是创建一个新的 deferred 等待同一个回调——但 TextToSpeech 只有一个 listener。
            // 实际并发场景下，第二个调用方会在 ready 检查处阻塞（见下），这里只需不重复构造。
            if (tts != null && !ready) return@synchronized null  // 正在初始化中，让调用方走等待路径
            try {
                val deferred = CompletableDeferred<Unit>()
                val successRef = AtomicBoolean(false)
                tts = TextToSpeech(context.applicationContext) { status ->
                    if (status == TextToSpeech.SUCCESS) successRef.set(true)
                    deferred.complete(Unit)
                }
                Setup(deferred, successRef)
            } catch (e: Exception) {
                tts?.shutdown()
                tts = null
                SecureLog.e("AndroidTTS", "init setup failed", e)
                return@synchronized null
            }
        }
        if (setup == null) {
            // 并发线程正在初始化或已完成：若已完成则 ready==true 返回 true；
            // 若正在初始化则自旋等待 ready（短时间，TTS 初始化通常 < 3s）
            var spins = 0
            while (!ready && spins < 100) {
                delay(50)
                spins++
            }
            return ready
        }

        // 阶段2：锁外 await 回调
        runCatching { setup.deferred.await() }

        // 阶段3：回到锁内设置语言或清理
        synchronized(initLock) {
            return if (setup.successRef.get() && tts != null) {
                tts?.language = Locale.CHINESE
                ready = true
                true
            } else {
                tts?.shutdown()
                tts = null
                ready = false
                false
            }
        }
    }

    fun stopSpeaking() {
        tts?.stop()
    }

    fun release() {
        synchronized(initLock) {
            tts?.shutdown()
            tts = null
            ready = false
        }
    }

    companion object {
        const val PROVIDER_ID = "android_tts"
    }
}
