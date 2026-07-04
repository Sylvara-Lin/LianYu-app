package com.lianyu.ai.network.tts

import android.content.Context
import com.lianyu.ai.common.SecureLog
import com.lianyu.ai.network.RequestSecurityInterceptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import com.lianyu.ai.network.CertificatePins

class BaiduTtsProvider : TtsProviderInterface, ConfigurableTtsProvider {

    private val client = run {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
        RequestSecurityInterceptor.enforceTls(builder)
        builder.certificatePinner(CertificatePins.certificatePinner)
        builder.build()
    }

    private var accessToken: String? = null
    private var tokenExpireTime: Long = 0
    private var config: TtsConfig = TtsConfig()

    override fun updateConfig(config: TtsConfig) {
        this.config = config
    }

    override suspend fun synthesize(context: Context, text: String, voiceId: String?): String? = withContext(Dispatchers.IO) {
        try {
            val apiKey = config.baiduApiKey
            val secretKey = config.baiduSecretKey

            if (apiKey.isBlank()) {
                SecureLog.w("BaiduTts", "Baidu TTS not configured")
                return@withContext null
            }

            val token = getAccessToken(apiKey, secretKey) ?: return@withContext null

            val url = "https://tsn.baidu.com/text2audio"

            val requestBody = FormBody.Builder()
                .add("tex", URLEncoder.encode(text, "UTF-8"))
                .add("tok", token)
                .add("cuid", context.packageName)
                .add("ctp", "1")
                .add("lan", "zh")
                .add("spd", "5")
                .add("pit", "5")
                .add("vol", "5")
                .add("per", voiceId ?: "3")
                .add("aue", "3")
                .build()

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.bytes()

            if (!response.isSuccessful || body == null) {
                SecureLog.e("BaiduTts", "HTTP ${response.code}")
                return@withContext null
            }

            val contentType = response.header("Content-Type", "") ?: ""
            if (contentType.contains("application/json")) {
                val jsonStr = String(body)
                SecureLog.e("BaiduTts", "API error: $jsonStr")
                return@withContext null
            }

            val outputDir = File(context.cacheDir, "tts_audio")
            outputDir.mkdirs()
            val outputFile = File(outputDir, "baidu_${System.currentTimeMillis()}.mp3")
            outputFile.writeBytes(body)

            outputFile.absolutePath
        } catch (e: Exception) {
            SecureLog.e("BaiduTts", "Synthesis failed", e)
            null
        }
    }

    override fun getVoices(): List<TtsVoice> {
        return listOf(
            TtsVoice("0", "度小美", "女", "zh-CN", "标准女声"),
            TtsVoice("1", "度小宇", "男", "zh-CN", "标准男声"),
            TtsVoice("3", "度逍遥", "男", "zh-CN", "情感男声"),
            TtsVoice("4", "度丫丫", "女", "zh-CN", "情感女声"),
            TtsVoice("5", "度小娇", "女", "zh-CN", "情感女声"),
            TtsVoice("103", "度米朵", "女", "zh-CN", "童声"),
            TtsVoice("106", "度博文", "男", "zh-CN", "情感男声"),
            TtsVoice("110", "度小童", "女", "zh-CN", "童声"),
            TtsVoice("111", "度小萌", "女", "zh-CN", "情感女声"),
            TtsVoice("5003", "度逍遥(精品)", "男", "zh-CN", "精品情感男声"),
            TtsVoice("5118", "度小鹿", "女", "zh-CN", "精品情感女声")
        )
    }

    override suspend fun testConnection(): Boolean {
        return try {
            config.baiduApiKey.isNotBlank() && config.baiduSecretKey.isNotBlank()
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun getAccessToken(apiKey: String, secretKey: String): String? = withContext(Dispatchers.IO) {
        if (accessToken != null && System.currentTimeMillis() < tokenExpireTime) {
            return@withContext accessToken
        }

        try {
            val url = "https://aip.baidubce.com/oauth/2.0/token?grant_type=client_credentials&client_id=$apiKey&client_secret=$secretKey"

            val request = Request.Builder()
                .url(url)
                .post(FormBody.Builder().build())
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string()

            if (!response.isSuccessful || body == null) {
                return@withContext null
            }

            val json = JSONObject(body)
            accessToken = json.getString("access_token")
            val expiresIn = json.getLong("expires_in")
            tokenExpireTime = System.currentTimeMillis() + (expiresIn - 60) * 1000

            accessToken
        } catch (e: Exception) {
            SecureLog.e("BaiduTts", "Get access token failed", e)
            null
        }
    }
}
