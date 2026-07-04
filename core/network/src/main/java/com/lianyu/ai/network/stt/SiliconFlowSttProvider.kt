package com.lianyu.ai.network.stt

import android.content.Context
import com.lianyu.ai.common.SecureLog
import com.lianyu.ai.network.CertificatePins
import com.lianyu.ai.network.RequestSecurityInterceptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 硅基流动 STT Provider - 基于 SenseVoice 模型
 *
 * API: POST https://api.siliconflow.cn/v1/audio/transcriptions
 * 模型: FunAudioLLM/SenseVoiceSmall (多语言语音识别)
 *
 * 作为 sherpa-onnx 离线识别的 API 备选方案
 */
class SiliconFlowSttProvider : SttProviderInterface {

    private val client = run {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
        RequestSecurityInterceptor.enforceTls(builder)
        builder.certificatePinner(CertificatePins.certificatePinner)
        builder.build()
    }

    private var apiKey: String = ""
    private var isInit = false

    fun initialize(context: Context) {
        val config = SttConfig.fromSharedPreferences(context)
        apiKey = config.siliconflowApiKey.ifBlank {
            // Try global API key
            getGlobalApiKey(context) ?: ""
        }
        isInit = true
    }

    fun isInitialized(): Boolean = isInit

    override suspend fun recognize(context: Context, audioPath: String): String? = withContext(Dispatchers.IO) {
        try {
            val audioFile = File(audioPath)
            if (!audioFile.exists()) {
                SecureLog.e("SiliconFlowStt", "Audio file not found: $audioPath")
                return@withContext null
            }

            if (!isInit) initialize(context)
            if (apiKey.isBlank()) {
                SecureLog.w("SiliconFlowStt", "API Key not configured")
                return@withContext null
            }

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", audioFile.name,
                    audioFile.asRequestBody("audio/mp4".toMediaType()))
                .addFormDataPart("model", "FunAudioLLM/SenseVoiceSmall")
                .build()

            val request = Request.Builder()
                .url("https://api.siliconflow.cn/v1/audio/transcriptions")
                .post(requestBody)
                .addHeader("Authorization", "Bearer $apiKey")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string()

            if (!response.isSuccessful || body == null) {
                SecureLog.e("SiliconFlowStt", "HTTP ${response.code}: ${response.message}")
                return@withContext null
            }

            val json = JSONObject(body)
            val text = json.optString("text", "")

            if (text.isNotBlank()) {
                SecureLog.i("SiliconFlowStt", "STT success: ${text.take(50)}...")
                text
            } else {
                SecureLog.w("SiliconFlowStt", "STT returned empty text")
                null
            }
        } catch (e: Exception) {
            SecureLog.e("SiliconFlowStt", "Recognition failed", e)
            null
        }
    }

    override suspend fun recognizeFromFile(context: Context, audioPath: String, mimeType: String): String? {
        return recognize(context, audioPath)
    }

    override suspend fun testConnection(): Boolean {
        return try {
            isInit && apiKey.isNotBlank()
        } catch (e: Exception) {
            false
        }
    }

    private fun getGlobalApiKey(context: Context): String? {
        return try {
            val prefs = context.getSharedPreferences("api_settings", Context.MODE_PRIVATE)
            prefs.getString("api_key_SILICONFLOW", null)
        } catch (e: Exception) {
            null
        }
    }
}
