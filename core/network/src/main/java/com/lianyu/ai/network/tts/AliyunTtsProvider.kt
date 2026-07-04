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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import com.lianyu.ai.network.CertificatePins

class AliyunTtsProvider : TtsProviderInterface, ConfigurableTtsProvider {

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
            val accessKeyId = config.aliyunKeyId
            val accessKeySecret = config.aliyunKeySecret
            val appKey = config.aliyunAppKey

            if (accessKeyId.isBlank()) {
                SecureLog.w("AliyunTts", "Aliyun TTS not configured, using fallback")
                return@withContext null
            }

            val token = getToken(accessKeyId, accessKeySecret) ?: return@withContext null

            val endpoint = "nls-gateway-cn-shanghai.aliyuncs.com"
            val path = "/stream/v1/tts"
            val url = "https://$endpoint$path"

            val requestBody = FormBody.Builder()
                .add("appkey", appKey)
                .add("text", text)
                .add("format", "mp3")
                .add("sample_rate", "16000")
                .add("voice", voiceId ?: "xiaoyun")
                .add("volume", "50")
                .add("speech_rate", "0")
                .add("pitch_rate", "0")
                .build()

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .addHeader("X-NLS-Token", token)
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.bytes()

            if (!response.isSuccessful || body == null) {
                SecureLog.e("AliyunTts", "HTTP ${response.code}")
                return@withContext null
            }

            val contentType = response.header("Content-Type", "") ?: ""
            if (contentType.contains("application/json") || contentType.contains("text/")) {
                val jsonStr = String(body)
                SecureLog.e("AliyunTts", "API error: $jsonStr")
                return@withContext null
            }

            val outputDir = File(context.cacheDir, "tts_audio")
            outputDir.mkdirs()
            val outputFile = File(outputDir, "aliyun_${System.currentTimeMillis()}.mp3")
            outputFile.writeBytes(body)

            outputFile.absolutePath
        } catch (e: Exception) {
            SecureLog.e("AliyunTts", "Synthesis failed", e)
            null
        }
    }

    override fun getVoices(): List<TtsVoice> {
        return listOf(
            TtsVoice("xiaoyun", "小云", "女", "zh-CN", "标准女声"),
            TtsVoice("xiaogang", "小刚", "男", "zh-CN", "标准男声"),
            TtsVoice("ruoxi", "若兮", "女", "zh-CN", "温柔女声"),
            TtsVoice("siqi", "思琪", "女", "zh-CN", "温柔女声"),
            TtsVoice("sijia", "思佳", "女", "zh-CN", "标准女声"),
            TtsVoice("sicheng", "思诚", "男", "zh-CN", "标准男声"),
            TtsVoice("aiqi", "艾琪", "女", "zh-CN", "温柔女声"),
            TtsVoice("aijia", "艾佳", "女", "zh-CN", "标准女声"),
            TtsVoice("aicheng", "艾诚", "男", "zh-CN", "标准男声"),
            TtsVoice("aida", "艾达", "男", "zh-CN", "标准男声"),
            TtsVoice("ninger", "宁儿", "女", "zh-CN", "标准女声"),
            TtsVoice("ruilin", "瑞琳", "女", "zh-CN", "标准女声")
        )
    }

    override suspend fun testConnection(): Boolean {
        return try {
            config.aliyunKeyId.isNotBlank() && 
            config.aliyunKeySecret.isNotBlank() && 
            config.aliyunAppKey.isNotBlank()
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun getToken(accessKeyId: String, accessKeySecret: String): String? = withContext(Dispatchers.IO) {
        if (accessToken != null && System.currentTimeMillis() < tokenExpireTime) {
            return@withContext accessToken
        }

        try {
            val endpoint = "nls-meta.cn-shanghai.aliyuncs.com"
            val url = "https://$endpoint/pop/v2018-05-18/tokens"

            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            dateFormat.timeZone = TimeZone.getTimeZone("UTC")
            val timestamp = dateFormat.format(Date())

            val params = mapOf(
                "AccessKeyId" to accessKeyId,
                "Action" to "CreateToken",
                "Format" to "JSON",
                "RegionId" to "cn-shanghai",
                "SignatureMethod" to "HMAC-SHA1",
                "SignatureNonce" to System.currentTimeMillis().toString(),
                "SignatureVersion" to "1.0",
                "Timestamp" to timestamp,
                "Version" to "2019-02-28"
            )

            val sortedParams = params.toSortedMap()
            val canonicalizedQuery = sortedParams.entries.joinToString("&") { "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}" }

            val stringToSign = "POST&${URLEncoder.encode("/", "UTF-8")}&${URLEncoder.encode(canonicalizedQuery, "UTF-8")}"

            val mac = javax.crypto.Mac.getInstance("HmacSHA1")
            mac.init(javax.crypto.spec.SecretKeySpec("${accessKeySecret}&".toByteArray(), "HmacSHA1"))
            val signature = java.util.Base64.getEncoder().encodeToString(mac.doFinal(stringToSign.toByteArray()))

            val fullUrl = "$url?$canonicalizedQuery&Signature=${URLEncoder.encode(signature, "UTF-8")}"

            val request = Request.Builder()
                .url(fullUrl)
                .post(FormBody.Builder().build())
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful || responseBody == null) {
                SecureLog.e("AliyunTts", "Token request failed: HTTP ${response.code}")
                return@withContext null
            }

            val json = JSONObject(responseBody)
            val tokenObj = json.getJSONObject("Token")
            accessToken = tokenObj.getString("ID")
            val expireTime = tokenObj.getLong("ExpireTime")
            tokenExpireTime = expireTime * 1000 - 60000

            accessToken
        } catch (e: Exception) {
            SecureLog.e("AliyunTts", "Get token failed", e)
            null
        }
    }
}
