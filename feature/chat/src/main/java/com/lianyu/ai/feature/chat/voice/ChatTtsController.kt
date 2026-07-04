package com.lianyu.ai.feature.chat.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.audiofx.Equalizer
import com.lianyu.ai.common.SecureLog
import com.lianyu.ai.common.TimeoutBudgets
import com.lianyu.ai.network.tts.ChatTtsConfig
import com.lianyu.ai.network.tts.ChatTtsMode
import com.lianyu.ai.network.tts.TtsService
import com.lianyu.ai.network.tts.TtsTextCleaner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Collections

/**
 * 朗读状态（供 UI 显示）。
 */
enum class ChatTtsState {
    IDLE,
    SPEAKING
}

/**
 * 聊天页分段队列 TTS 控制器。
 *
 * 参考反编译代码 [H:\aiyu\chat_tts] 的：
 * - `MainActivity$speakText$deferredJob$1` （异步合成）
 * - `MainActivity$processNextAudio$1`     （队列消费协程）
 * - `playAudioFile`                        （MediaPlayer 播放 + 临时文件清理）
 * - `applyMediaPlayerBeautify`            （Equalizer 美化）
 * - `seenVoiceContents: Set<String>`      （防重复）
 *
 * 关键设计点（与参考一致）：
 * 1. 流式分段朗读：AI 回复按句子边界切分后逐段入队，边合成边朗读，不等全部完成
 * 2. 防重复：[seenVoiceContents] 记录已朗读内容（基于 trim 后的文本）
 * 3. 队列串行：[audioQueue] + [isSpeaking] 保证一次只播一个，避免音频叠加
 * 4. 临时文件清理：播完即删 [file.delete]
 * 5. 错误恢复：单条失败也继续 [processNextAudio]
 * 6. 音频美化：可选 [applyMediaPlayerBeautify] 用 Android Equalizer 加预设
 * 7. 括号内容跳过：用户可配置 [ChatTtsConfig.skipParentheses] 跳过 AI 的"内心戏"
 *
 * LianYu 特有的安全加固：
 * - **互斥保护**：[callActiveProvider] 返回 true（语音通话激活）时禁用朗读，
 *   避免与 [com.lianyu.ai.feature.chat.ui.screen.VoiceCallScreen] 共用的 TtsService 单例
 *   和 AudioManager / 通信设备发生冲突。
 * - **作用域安全**：使用应用级 [scope]（[com.lianyu.ai.common.ApplicationScopeProvider.scope]），
 *   跨页面存活；[stop] 由 ChatViewModel.onCleared 调用以释放 MediaPlayer。
 * - **音频属性**：用 [AudioAttributes.USAGE_MEDIA] 而非 USAGE_VOICE_COMMUNICATION，
 *   避免与通话抢通信设备（RISK 3）。
 * - **内存加固**：[seenVoiceContents] 上限 200 条，超出清空避免无界增长。
 *
 * @param context 应用上下文（仅用于日志，不持有 Activity）
 * @param ttsService TTS 合成服务（与 VoiceCallScreen 共享同一单例）
 * @param scope 应用级 CoroutineScope，跨页面存活
 * @param configProvider 配置提供者（每次调用读取最新配置）
 * @param callActiveProvider 通话激活状态提供者（互斥保护）
 */
class ChatTtsController(
    private val context: Context,
    private val ttsService: TtsService,
    private val scope: CoroutineScope,
    private val configProvider: () -> ChatTtsConfig,
    private val callActiveProvider: () -> Boolean
) {
    /** 音频合成 deferred 队列（串行消费） */
    private val audioQueue = ArrayDeque<Deferred<File?>>()

    /** 是否正在朗读（队列消费中） */
    @Volatile
    private var isSpeaking = false

    /** 当前播放器（单实例，播完释放） */
    @Volatile
    private var mediaPlayer: MediaPlayer? = null

    /** 已朗读内容集合（防重复，线程安全，有界） */
    private val seenVoiceContents = Collections.synchronizedSet(LinkedHashSet<String>())

    private val _state = MutableStateFlow(ChatTtsState.IDLE)
    val state: StateFlow<ChatTtsState> = _state.asStateFlow()

    private val _currentText = MutableStateFlow("")
    val currentText: StateFlow<String> = _currentText.asStateFlow()

    /**
     * 是否应当自动朗读（mode==READ_ALOUD 且通话未激活）。
     * 在 [com.lianyu.ai.feature.chat.ui.viewmodel.ChatViewModel.finalizeResponse] 末尾调用。
     */
    fun shouldAutoPlay(): Boolean {
        return configProvider().mode == ChatTtsMode.READ_ALOUD && !callActiveProvider()
    }

    // ── mode 2: 朗读模式入口 ──

    /**
     * 朗读一段文本（参考 `speakText`）。
     *
     * 流程：互斥检查 → 模式检查 → 文本清洗 → 防重复 → 异步合成入队 → 触发消费。
     * 仅在 [ChatTtsMode.READ_ALOUD] 模式下生效。
     */
    fun speakText(text: String) {
        // 互斥保护：通话激活时不朗读，避免与通话 MediaPlayer 抢通信设备
        if (callActiveProvider()) {
            SecureLog.d("ChatTtsController", "speakText skipped: voice call active")
            return
        }
        val cfg = configProvider()
        if (cfg.mode != ChatTtsMode.READ_ALOUD) return

        // 文本清洗（括号跳过 + 贴纸移除 + Markdown 移除 + 指令标签移除）
        val cleaned = TtsTextCleaner.clean(text, cfg.skipParentheses)
        if (cleaned.isBlank()) return

        // 防重复：基于 trim 后的文本
        if (cfg.autoDedup && !seenVoiceContents.add(cleaned)) {
            SecureLog.d("ChatTtsController", "speakText skipped: duplicate content")
            return
        }
        // 内存加固：上限 200 条，超出清空（参考未提及，作为加固）
        if (seenVoiceContents.size > MAX_SEEN_CONTENTS) {
            seenVoiceContents.clear()
            seenVoiceContents.add(cleaned)
        }

        // 异步合成音频（参考 MainActivity$speakText$deferredJob$1）
        val deferred = scope.async(Dispatchers.IO) {
            try {
                val audioPath = withTimeoutOrNull(TimeoutBudgets.TTS_SYNTH_MS) {
                    ttsService.synthesize(cleaned)
                }
                audioPath?.let { File(it) }?.takeIf { it.exists() }
            } catch (e: Exception) {
                SecureLog.e("ChatTtsController", "synthesize failed", e)
                null
            }
        }

        synchronized(audioQueue) { audioQueue.addLast(deferred) }

        // 触发队列消费（参考：if (!isTtsSpeaking) processNextAudio()）
        if (!isSpeaking) processNextAudio()
    }

    // ── 队列消费协程（参考 MainActivity$processNextAudio$1）──

    /**
     * 消费队列下一条音频。
     *
     * - 队列空 → 置 IDLE，返回
     * - 取下一条 deferred → await
     *   - 文件存在 → 主线程播放
     *   - 文件为空 → 跳过坏的，继续下一个
     * - 异常 → 错误恢复，继续下一个
     */
    private fun processNextAudio() {
        val next: Deferred<File?>? = synchronized(audioQueue) {
            if (audioQueue.isEmpty()) {
                isSpeaking = false
                _state.value = ChatTtsState.IDLE
                _currentText.value = ""
                return
            }
            isSpeaking = true
            audioQueue.removeFirst()
        }
        if (next == null) return

        scope.launch {
            try {
                val file = next.await()
                if (file != null && file.exists()) {
                    _state.value = ChatTtsState.SPEAKING
                    withContext(Dispatchers.Main) {
                        playAudioFile(file)
                    }
                } else {
                    // 跳过坏的，继续下一个（参考 processNextAudio 递归）
                    processNextAudio()
                }
            } catch (e: Exception) {
                SecureLog.e("ChatTtsController", "queue item failed", e)
                // 错误恢复：单条失败也继续
                processNextAudio()
            }
        }
    }

    // ── MediaPlayer 播放（参考 playAudioFile）──

    /**
     * 播放音频文件。
     *
     * - onPrepared → start + 可选美化
     * - onCompletion → release + 删临时文件 + 播下一个
     * - onError → release + 删临时文件 + 播下一个
     *
     * 注意：用 [AudioAttributes.USAGE_MEDIA] 而非 USAGE_VOICE_COMMUNICATION，
     * 避免与 VoiceCallScreen 抢通信设备。
     */
    private fun playAudioFile(file: File) {
        try {
            mediaPlayer?.release()
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(file.absolutePath)
                setOnPreparedListener { mp ->
                    mp.start()
                    val cfg = configProvider()
                    if (cfg.beautify) {
                        applyMediaPlayerBeautify(mp.audioSessionId)
                    }
                }
                setOnCompletionListener { mp ->
                    mp.release()
                    mediaPlayer = null
                    _state.value = ChatTtsState.IDLE
                    // 临时文件清理：播完即删
                    file.delete()
                    // 播下一个
                    processNextAudio()
                }
                setOnErrorListener { mp, what, extra ->
                    SecureLog.e("ChatTtsController", "MediaPlayer error: what=$what extra=$extra")
                    mp.release()
                    mediaPlayer = null
                    _state.value = ChatTtsState.IDLE
                    // 临时文件清理：出错也删
                    file.delete()
                    // 错误恢复：继续下一个
                    processNextAudio()
                    true
                }
                prepareAsync()
            }
            mediaPlayer = player
        } catch (e: Exception) {
            SecureLog.e("ChatTtsController", "playAudioFile failed", e)
            _state.value = ChatTtsState.IDLE
            file.delete()
            processNextAudio()
        }
    }

    // ── 音频美化（参考 applyMediaPlayerBeautify，Android Equalizer 预设）──

    /**
     * 用 Android [Equalizer] 给 MediaPlayer 加预设（优先选 VOICE 相关预设）。
     * 部分设备不支持 Equalizer，try/catch 静默降级。
     */
    private fun applyMediaPlayerBeautify(audioSessionId: Int) {
        var equalizer: Equalizer? = null
        try {
            equalizer = Equalizer(0, audioSessionId)
            // 优先选 VOICE 相关预设（如 "VOICE"，部分设备有）
            val voicePreset = (0 until equalizer.numberOfPresets.toInt())
                .firstOrNull { idx ->
                    runCatching { equalizer.getPresetName(idx.toShort()) }
                        .getOrNull()
                        ?.contains("VOICE", ignoreCase = true) == true
                } ?: 0
            equalizer.usePreset(voicePreset.toShort())
            equalizer.enabled = true
            SecureLog.d("ChatTtsController", "Equalizer applied: preset=$voicePreset")
        } catch (e: Exception) {
            SecureLog.w("ChatTtsController", "Equalizer unavailable: ${e.message}")
            // 部分设备/会话不支持，静默降级
        } finally {
            // 注意：Equalizer 在 MediaPlayer release 后会失效，这里不主动 release
            // （随 MediaPlayer 生命周期结束）
        }
    }

    // ── mode 1: 语音条模式（仅合成，返回路径，由 UI 展示 VoiceMessageBubble）──

    /**
     * 仅合成音频（不播放），返回文件路径。
     * 用于 [ChatTtsMode.VOICE_BAR] 模式：UI 把路径写入 ChatMessage.linkString + type=VOICE，
     * 复用现有 [com.lianyu.ai.uicommon.component.VoiceMessageBubble] 渲染。
     */
    suspend fun synthesizeOnly(text: String): String? {
        if (callActiveProvider()) return null
        val cfg = configProvider()
        if (cfg.mode != ChatTtsMode.VOICE_BAR) return null
        val cleaned = TtsTextCleaner.clean(text, cfg.skipParentheses)
        if (cleaned.isBlank()) return null
        return try {
            withTimeoutOrNull(TimeoutBudgets.TTS_SYNTH_MS) { ttsService.synthesize(cleaned) }
        } catch (e: Exception) {
            SecureLog.e("ChatTtsController", "synthesizeOnly failed", e)
            null
        }
    }

    // ── 控制 ──

    /**
     * 停止朗读并清空队列（用户手动停止 / ViewModel.onCleared 调用）。
     * 释放 MediaPlayer，避免泄漏。
     */
    fun stop() {
        synchronized(audioQueue) { audioQueue.clear() }
        mediaPlayer?.apply {
            try { stop() } catch (_: Exception) { }
            try { release() } catch (_: Exception) { }
        }
        mediaPlayer = null
        isSpeaking = false
        _state.value = ChatTtsState.IDLE
        _currentText.value = ""
    }

    /** 清空已朗读记录（切换角色 / 用户主动） */
    fun clearSeen() {
        seenVoiceContents.clear()
    }

    companion object {
        /** seenVoiceContents 上限，超出清空避免无界增长 */
        private const val MAX_SEEN_CONTENTS = 200
    }
}
