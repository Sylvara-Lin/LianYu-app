package com.lianyu.ai.network.tts

import android.content.Context
import com.lianyu.ai.common.SecureLog
import com.lianyu.ai.network.RequestSecurityInterceptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import com.lianyu.ai.network.CertificatePins

class VolcengineTtsProvider : TtsProviderInterface, ConfigurableTtsProvider {

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
            val appId = config.volcengineAppId
            val token = config.volcengineToken
            val cluster = config.volcengineCluster.ifBlank { "volcano_tts" }

            if (appId.isBlank() || token.isBlank()) {
                SecureLog.w("VolcengineTts", "Volcengine TTS not configured")
                return@withContext null
            }

            val url = "https://openspeech.bytedance.com/api/v1/tts"

            val requestBody = JSONObject().apply {
                put("app", JSONObject().apply {
                    put("appid", appId)
                    put("cluster", cluster)
                    put("token", token)
                })
                put("user", JSONObject())
                put("audio", JSONObject().apply {
                    put("voice_type", voiceId ?: "BV001_streaming")
                    put("encoding", "mp3")
                    put("speed_ratio", 1.0)
                    put("volume_ratio", 1.0)
                    put("pitch_ratio", 1.0)
                })
                put("request", JSONObject().apply {
                    put("reqid", UUID.randomUUID().toString())
                    put("text", text)
                    put("text_type", "plain")
                    put("operation", "query")
                })
            }.toString()

            val request = Request.Builder()
                .url(url)
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.bytes()

            if (!response.isSuccessful || body == null) {
                SecureLog.e("VolcengineTts", "HTTP ${response.code}")
                return@withContext null
            }

            val outputDir = File(context.cacheDir, "tts_audio")
            outputDir.mkdirs()
            val outputFile = File(outputDir, "volcengine_${System.currentTimeMillis()}.mp3")
            outputFile.writeBytes(body)

            outputFile.absolutePath
        } catch (e: Exception) {
            SecureLog.e("VolcengineTts", "Synthesis failed", e)
            null
        }
    }

    override fun getVoices(): List<TtsVoice> {
        return listOf(
            TtsVoice("BV001_streaming", "BV1-豆包超自然音色", "女", "zh-CN", "超拟真女声，适合日常对话"),
            TtsVoice("BV002_streaming", "BV2-豆包超自然音色", "男", "zh-CN", "超拟真男声，适合新闻播报"),
            TtsVoice("BV113_streaming", "通用女声", "女", "zh-CN", "标准通用女声"),
            TtsVoice("BV114_streaming", "通用男声", "男", "zh-CN", "标准通用男声"),
            TtsVoice("BV401_streaming", "中文男声-解说", "男", "zh-CN", "专业解说风格"),
            TtsVoice("BV402_streaming", "中文女声-解说", "女", "zh-CN", "专业解说风格"),
            TtsVoice("BV403_streaming", "中文男声-广播", "男", "zh-CN", "广播播音风格"),
            TtsVoice("BV404_streaming", "中文女声-广播", "女", "zh-CN", "广播播音风格"),
            TtsVoice("BV405_streaming", "中文男声-情感", "男", "zh-CN", "情感表达丰富"),
            TtsVoice("BV406_streaming", "中文女声-情感", "女", "zh-CN", "情感表达丰富"),
            TtsVoice("BV407_streaming", "中文男声-客服", "男", "zh-CN", "客服服务专用"),
            TtsVoice("BV408_streaming", "中文女声-客服", "女", "zh-CN", "客服服务专用"),
            TtsVoice("BV409_streaming", "中文男声-文学", "男", "zh-CN", "文学作品朗读"),
            TtsVoice("BV410_streaming", "中文女声-文学", "女", "zh-CN", "文学作品朗读"),
            TtsVoice("BV411_streaming", "中文童声", "女", "zh-CN", "儿童语音"),
            TtsVoice("BV412_streaming", "英文女声", "女", "en-US", "标准美式英语女声"),
            TtsVoice("BV413_streaming", "英文男声", "男", "en-US", "标准美式英语男声"),
            TtsVoice("BV121_streaming", "东北话女声", "女", "zh-CN", "东北方言特色"),
            TtsVoice("BV122_streaming", "四川话女声", "女", "zh-CN", "四川方言特色"),
            TtsVoice("BV123_streaming", "粤语女声", "女", "zh-TW", "粤语标准发音")
        )
    }

    override suspend fun testConnection(): Boolean {
        return try {
            config.volcengineAppId.isNotBlank() && config.volcengineToken.isNotBlank()
        } catch (e: Exception) {
            false
        }
    }
}
