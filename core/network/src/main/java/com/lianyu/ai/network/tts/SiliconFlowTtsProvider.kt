package com.lianyu.ai.network.tts

import android.content.Context
import com.lianyu.ai.common.SecureLog
import com.lianyu.ai.network.CertificatePins
import com.lianyu.ai.network.RequestSecurityInterceptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 硅基流动 TTS Provider - 基于 CosyVoice2 大模型
 *
 * API: POST https://api.siliconflow.cn/v1/audio/speech
 * 模型: FunAudioLLM/CosyVoice2-0.5B (默认，可配置)
 *
 * 支持功能：
 * - 预设音色列表 (CosyVoice2 常用音色)
 * - 自定义音色 voice_id (通过 https://voice.gbkgov.cn/ 生成)
 * - 独立 API Key 或复用全局 Key
 * - 自定义 TTS URL（自部署服务）
 * - 速度/增益/采样率调节
 */
class SiliconFlowTtsProvider : TtsProviderInterface, ConfigurableTtsProvider {

    private val client = run {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
        RequestSecurityInterceptor.enforceTls(builder)
        builder.certificatePinner(CertificatePins.certificatePinner)
        builder.build()
    }

    private var config: TtsConfig = TtsConfig()

    override fun updateConfig(config: TtsConfig) {
        this.config = config
    }

    override suspend fun synthesize(context: Context, text: String, voiceId: String?): String? = withContext(Dispatchers.IO) {
        try {
            // Determine URL and API key
            val apiKey: String
            val finalVoice: String
            val model: String
            val url: String

            // Check if using custom TTS endpoint
            val customUrl = config.customTtsUrl.ifBlank { "" }
            val actualUseCustom = customUrl.isNotBlank()

            if (actualUseCustom) {
                url = customUrl
                apiKey = config.customTtsApiKey.ifBlank { "" }
                model = config.customTtsModel.ifBlank { "FunAudioLLM/CosyVoice2-0.5B" }
                finalVoice = config.customTtsVoiceId.ifBlank {
                    voiceId ?: config.siliconflowCustomVoiceId.ifBlank { "FunAudioLLM/CosyVoice2-0.5B:anna" }
                }
            } else {
                url = "https://api.siliconflow.cn/v1/audio/speech"
                // API Key: use dedicated key or global key
                apiKey = if (config.siliconflowUseGlobalKey) {
                    getGlobalApiKey(context) ?: config.siliconflowApiKey
                } else {
                    config.siliconflowApiKey
                }
                model = config.siliconflowTtsModel.ifBlank { "FunAudioLLM/CosyVoice2-0.5B" }
                finalVoice = voiceId ?: config.siliconflowCustomVoiceId.ifBlank { "FunAudioLLM/CosyVoice2-0.5B:anna" }
            }

            if (apiKey.isBlank()) {
                SecureLog.w("SiliconFlowTts", "API Key not configured")
                return@withContext null
            }

            val sampleRate = config.siliconflowSampleRate
            val speed = config.siliconflowSpeed.toDoubleOrNull() ?: 1.0
            val gain = config.siliconflowGain.toDoubleOrNull() ?: 0.0

            val jsonBody = JSONObject().apply {
                if (model.isNotBlank()) put("model", model)
                put("input", text)
                put("voice", finalVoice)
                put("response_format", "mp3")
                put("sample_rate", sampleRate)
                put("stream", false)
                put("speed", speed)
                put("gain", gain)
            }.toString()

            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer $apiKey")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.bytes()

            if (!response.isSuccessful || body == null) {
                SecureLog.e("SiliconFlowTts", "HTTP ${response.code}: ${response.message}")
                return@withContext null
            }

            val outputDir = File(context.cacheDir, "tts_audio")
            outputDir.mkdirs()
            val outputFile = File(outputDir, "siliconflow_${System.currentTimeMillis()}.mp3")
            outputFile.writeBytes(body)

            SecureLog.i("SiliconFlowTts", "TTS success: ${outputFile.absolutePath}")
            outputFile.absolutePath
        } catch (e: Exception) {
            SecureLog.e("SiliconFlowTts", "Synthesis failed", e)
            null
        }
    }

    override fun getVoices(): List<TtsVoice> {
        return listOf(
            // CosyVoice2 预设音色
            TtsVoice("FunAudioLLM/CosyVoice2-0.5B:anna", "Anna", "女", "zh-CN", "温柔知性女声 (默认)"),
            TtsVoice("FunAudioLLM/CosyVoice2-0.5B:alex", "Alex", "男", "zh-CN", "沉稳磁性男声"),
            TtsVoice("FunAudioLLM/CosyVoice2-0.5B:bella", "Bella", "女", "zh-CN", "活泼甜美女声"),
            TtsVoice("FunAudioLLM/CosyVoice2-0.5B:benjamin", "Benjamin", "男", "en-US", "美式英语男声"),
            TtsVoice("FunAudioLLM/CosyVoice2-0.5B:charles", "Charles", "男", "en-GB", "英式英语男声"),
            TtsVoice("FunAudioLLM/CosyVoice2-0.5B:chloe", "Chloe", "女", "en-US", "美式英语女声"),
            TtsVoice("FunAudioLLM/CosyVoice2-0.5B:david", "David", "男", "zh-CN", "阳光开朗男声"),
            TtsVoice("FunAudioLLM/CosyVoice2-0.5B:diana", "Diana", "女", "en-US", "优雅美式女声"),
            TtsVoice("FunAudioLLM/CosyVoice2-0.5B:emma", "Emma", "女", "zh-CN", "亲切邻家女声"),
            TtsVoice("FunAudioLLM/CosyVoice2-0.5B:grace", "Grace", "女", "zh-CN", "端庄大气女声"),
            TtsVoice("FunAudioLLM/CosyVoice2-0.5B:henry", "Henry", "男", "zh-CN", "成熟稳重男声"),
            TtsVoice("FunAudioLLM/CosyVoice2-0.5B:jack", "Jack", "男", "zh-CN", "青春活力男声"),
            TtsVoice("FunAudioLLM/CosyVoice2-0.5B:luna", "Luna", "女", "zh-CN", "梦幻空灵女声"),
            TtsVoice("FunAudioLLM/CosyVoice2-0.5B:sarah", "Sarah", "女", "en-US", "知性美式女声"),
            TtsVoice("FunAudioLLM/CosyVoice2-0.5B:sophia", "Sophia", "女", "zh-CN", "温柔治愈女声"),
            TtsVoice("FunAudioLLM/CosyVoice2-0.5B:william", "William", "男", "en-US", "标准美式男声"),
            // Custom voice hint
            TtsVoice("__custom__", "自定义音色", "自定义", "zh-CN", "使用自定义 voice_id (在设置页面填入)")
        )
    }

    override suspend fun testConnection(): Boolean {
        return try {
            val hasKey = config.siliconflowApiKey.isNotBlank() || config.siliconflowUseGlobalKey
            hasKey || config.customTtsUrl.isNotBlank()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 尝试从全局 API 配置中获取 API Key
     */
    private fun getGlobalApiKey(context: Context): String? {
        return try {
            val prefs = context.getSharedPreferences("api_settings", Context.MODE_PRIVATE)
            val currentApiName = prefs.getString("current_api_provider", null) ?: return null
            // Try to get the key for the current active API provider
            prefs.getString("api_key_$currentApiName", null)
                ?: prefs.getString("api_key_SILICONFLOW", null)
        } catch (e: Exception) {
            null
        }
    }
}
