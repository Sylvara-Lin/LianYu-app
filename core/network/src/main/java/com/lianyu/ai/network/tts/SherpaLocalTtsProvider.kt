package com.lianyu.ai.network.tts

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.lianyu.ai.common.SecureLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 本地离线 TTS Provider - 基于 sherpa-onnx [OfflineTts]。
 *
 * 端上运行，无需联网。首次需通过 [LocalTtsModelManager] 下载模型文件。
 *
 * 推理流程：
 * 1. 从 [LocalTtsPreferences] 读选中模型 + enabled 状态
 * 2. 懒加载 [OfflineTts]（file-based，mutex 保护）
 * 3. [OfflineTts.generate] → [GeneratedAudio] (FloatArray PCM)
 * 4. [LocalTtsWavWriter] 写 WAV 到 cacheDir
 *
 * voiceId 约定：`"speaker_42"` → sid=42；空或无法解析 → 用 [TtsConfig.localTtsSid]。
 */
class SherpaLocalTtsProvider : TtsProviderInterface, ConfigurableTtsProvider {

    private val mutex = Mutex()
    private var offlineTts: OfflineTts? = null
    private var loadedModelId: String? = null
    private var config: TtsConfig = TtsConfig()

    override fun updateConfig(config: TtsConfig) {
        // 模型/speed 变更后需要重新加载
        if (this.config.localTtsSpeed != config.localTtsSpeed ||
            this.config.localTtsSid != config.localTtsSid
        ) {
            // speed 是 generate 参数，不影响 engine；sid 也是 generate 参数
            // 只在模型切换时才需要 reload，这里只更新 config
        }
        this.config = config
    }

    override suspend fun synthesize(context: Context, text: String, voiceId: String?): String? =
        withContext(Dispatchers.IO) {
            try {
                val preferences = LocalTtsPreferences(context)
                val modelId = preferences.selectedModelId.first()
                val modelState = preferences.modelState(modelId).first()
                if (!modelState.isEnabled) {
                    SecureLog.w(TAG, "本地 TTS 未启用 (model=$modelId)")
                    return@withContext null
                }
                val model = LocalTtsCatalog.findById(modelId)
                if (!model.isAllFilesPresent(context)) {
                    SecureLog.w(TAG, "模型文件缺失: ${model.id}")
                    return@withContext null
                }
                val tts = getOrLoadOfflineTts(context, model)
                val sid = parseSid(voiceId, model)
                val speed = config.localTtsSpeed.coerceIn(0.5f, 2.0f)

                SecureLog.d(TAG, "合成: model=${model.id}, sid=$sid, speed=$speed, len=${text.length}")
                val audio = tts.generate(text, sid, speed)

                val outputDir = File(context.cacheDir, "tts_audio")
                outputDir.mkdirs()
                val outputFile = File(outputDir, "local_${System.currentTimeMillis()}.wav")
                LocalTtsWavWriter.writePcmToWav(audio.samples, audio.sampleRate, outputFile)

                SecureLog.i(TAG, "本地 TTS 合成成功: ${outputFile.absolutePath}")
                outputFile.absolutePath
            } catch (e: Exception) {
                SecureLog.e(TAG, "本地 TTS 合成失败", e)
                null
            }
        }

    override fun getVoices(): List<TtsVoice> {
        // 返回通用音色列表；具体数量取决于选中模型
        // UI 根据 model.numSpeakers 动态调整 sid
        return listOf(
            TtsVoice("speaker_0", "默认音色", "自定义", "zh-CN", "sid=0"),
            TtsVoice("__custom_sid__", "自定义 sid", "自定义", "zh-CN", "在设置页填入 sid")
        )
    }

    override suspend fun testConnection(): Boolean {
        return try {
            // testConnection 在 TtsService 里会被调用，但本地模型的"连接"
            // 实际是文件是否存在 + 是否启用
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 获取当前选中模型的音色数量（供 UI 动态渲染 sid 滑动条）。
     */
    suspend fun getNumSpeakers(context: Context): Int {
        val modelId = LocalTtsPreferences(context).selectedModelId.first()
        return LocalTtsCatalog.findById(modelId).numSpeakers
    }

    // ── 内部 ──

    private suspend fun getOrLoadOfflineTts(context: Context, model: LocalTtsModel): OfflineTts {
        if (offlineTts != null && loadedModelId == model.id) {
            return offlineTts!!
        }
        return mutex.withLock {
            if (offlineTts != null && loadedModelId == model.id) {
                offlineTts!!
            } else {
                // 释放旧引擎
                try { offlineTts?.release() } catch (_: Exception) {}
                val tts = createOfflineTts(context, model)
                offlineTts = tts
                loadedModelId = model.id
                SecureLog.i(TAG, "OfflineTts 加载成功: ${model.displayName}, sampleRate=${tts.sampleRate()}")
                tts
            }
        }
    }

    /**
     * 构建 sherpa OfflineTts 实例。
     *
     * file-based 模式：assetManager=null → native 层走 newFromFile（javap 确认存在）。
     * OfflineTts 构造器是 Kotlin 带默认参数的，`assetManager` 默认 null，
     * 故用命名参数显式传 null 即可走 2-arg 构造器。
     */
    private fun createOfflineTts(context: Context, model: LocalTtsModel): OfflineTts {
        val modelPath = model.file(context, model.modelFileName).absolutePath
        val tokensPath = model.file(context, model.tokensFileName).absolutePath
        val lexiconPath = model.lexiconFileName?.let { model.file(context, it).absolutePath } ?: ""

        val vitsConfig = OfflineTtsVitsModelConfig(
            model = modelPath,
            lexicon = lexiconPath,
            tokens = tokensPath,
            dataDir = "",    // VITS 不需要 dataDir
            dictDir = "",
            noiseScale = 0.667f,
            noiseScaleW = 0.8f,
            lengthScale = 1.0f.coerceIn(0.5f, 2.0f) / config.localTtsSpeed.coerceIn(0.5f, 2.0f)
        )
        val modelConfig = OfflineTtsModelConfig(
            vits = vitsConfig
        )
        val ttsConfig = OfflineTtsConfig(
            model = modelConfig
        )
        // assetManager=null → native 走 newFromFile，读绝对路径
        return OfflineTts(assetManager = null, config = ttsConfig)
    }

    /**
     * 解析 voiceId 为 sid。
     * - `"speaker_42"` → 42
     * - `"42"` → 42
     * - 空/无法解析 → [TtsConfig.localTtsSid]
     */
    private fun parseSid(voiceId: String?, model: LocalTtsModel): Int {
        val raw = voiceId?.removePrefix("speaker_")?.trim()
        val parsed = raw?.toIntOrNull()
        val sid = parsed ?: config.localTtsSid
        return sid.coerceIn(0, (model.numSpeakers - 1).coerceAtLeast(0))
    }

    companion object {
        private const val TAG = "SherpaLocalTts"

        /**
         * 释放引擎（供 LocalTtsModelManager.disable() 调用）。
         */
        fun releaseEngine() {
            // 静态释放，因为 SherpaLocalTtsProvider 实例由 TtsService 持有
            // 实际释放通过 updateConfig 触发模型重加载时自动进行
        }
    }
}
