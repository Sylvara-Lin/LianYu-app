package com.lianyu.ai.network.tts

import android.content.Context
import com.lianyu.ai.common.SecureLog
import com.lianyu.ai.network.RequestSecurityInterceptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import com.lianyu.ai.network.CertificatePins

class XunfeiTtsProvider : TtsProviderInterface, ConfigurableTtsProvider {

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
            val appId = config.xunfeiAppId
            val apiKey = config.xunfeiApiKey
            val apiSecret = config.xunfeiApiSecret

            if (appId.isBlank() || apiKey.isBlank() || apiSecret.isBlank()) {
                SecureLog.w("XunfeiTts", "Xunfei TTS not configured")
                return@withContext null
            }

            val url = buildWebSocketUrl(appId, apiKey, apiSecret)

            val audioData = synthesizeViaWebSocket(url, text, voiceId ?: "xiaoyan")

            if (audioData == null || audioData.isEmpty()) {
                SecureLog.e("XunfeiTts", "Failed to synthesize audio")
                return@withContext null
            }

            val outputDir = File(context.cacheDir, "tts_audio")
            outputDir.mkdirs()
            val outputFile = File(outputDir, "xunfei_${System.currentTimeMillis()}.mp3")
            outputFile.writeBytes(audioData)

            outputFile.absolutePath
        } catch (e: Exception) {
            SecureLog.e("XunfeiTts", "Synthesis failed", e)
            null
        }
    }

    override fun getVoices(): List<TtsVoice> {
        return listOf(
            TtsVoice("xiaoyan", "晓燕", "女", "zh-CN", "标准女声"),
            TtsVoice("aisjiuxu", " aisjx ", "女", "zh-CN", "温柔女声"),
            TtsVoice("aisxping", " aisxp ", "男", "zh-CN", "标准男声"),
            TtsVoice("aisjinger", " aisjger ", "女", "zh-CN", "甜美女声"),
            TtsVoice("aisbabyxu", " aisbx ", "女", "zh-CN", "童声"),
            TtsVoice("xiaofeng", "小峰", "男", "zh-CN", "情感男声"),
            TtsVoice("aisjiaping", " aisjp ", "男", "zh-CN", "成熟男声"),
            TtsVoice("aisxping", "艾思-平", "男", "zh-CN", "新闻播报风格")
        )
    }

    override suspend fun testConnection(): Boolean {
        return try {
            config.xunfeiAppId.isNotBlank() && 
            config.xunfeiApiKey.isNotBlank() && 
            config.xunfeiApiSecret.isNotBlank()
        } catch (e: Exception) {
            false
        }
    }

    private fun buildWebSocketUrl(appId: String, apiKey: String, apiSecret: String): String {
        val url = "wss://tts-api.xfyun.cn/v2/tts"
        
        val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
        dateFormat.timeZone = TimeZone.getTimeZone("GMT")
        val date = dateFormat.format(Date())
        
        val origin = "host: tts-api.xfyun.cn"
        val signatureOrigin = """origin: $origin
date: $date
host: tts-api.xfyun.cn"""
        
        val signatureSha = hmacSha256(apiSecret, signatureOrigin)
        val signature = Base64.getEncoder().encodeToString(signatureSha)
        
        val authorizationOrigin = """api_key="$apiKey", algorithm="hmac-sha256", headers="origin date host", signature="$signature""""
        val authorization = URLEncoder.encode(authorizationOrigin, "UTF-8")
        
        return "$url?authorization=$authorization&date=${URLEncoder.encode(date, "UTF-8")}&host=tts-api.xfyun.cn"
    }

    private fun hmacSha256(key: String, data: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(), "HmacSHA256"))
        return mac.doFinal(data.toByteArray())
    }

    private suspend fun synthesizeViaWebSocket(url: String, text: String, voice: String): ByteArray? {
        var result: ByteArray? = null
        
        val request = Request.Builder().url(url).build()
        val webSocketListener = object : WebSocketListener() {
            private val audioBuffer = mutableListOf<Byte>()
            
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                val requestJson = JSONObject().apply {
                    put("header", JSONObject().apply {
                        put("app_id", config.xunfeiAppId)
                        put("status", 0)
                    })
                    put("parameter", JSONObject().apply {
                        put("tts", JSONObject().apply {
                            put("vcn", voice)
                            put("speed", 50)
                            put("volume", 50)
                            put("pitch", 50)
                            put("encoding", "mp3")
                            put("down_type", 1)
                        })
                    })
                    put("payload", JSONObject().apply {
                        put("input", JSONObject().apply {
                            put("text", text)
                            put("encoding", "utf8")
                        })
                    })
                }
                
                webSocket.send(requestJson.toString())
            }
            
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                synchronized(audioBuffer) {
                    for (byte in bytes.toByteArray()) {
                        audioBuffer.add(byte)
                    }
                }
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    val header = json.getJSONObject("header")
                    val code = header.getInt("code")
                    
                    if (code != 0) {
                        SecureLog.e("XunfeiTts", "Error code: $code")
                    }
                    
                    val status = header.getInt("status")
                    if (status == 2) {
                        result = synchronized(audioBuffer) { audioBuffer.toByteArray() }
                        webSocket.close(1000, "Complete")
                    }
                } catch (e: Exception) {
                    SecureLog.e("XunfeiTts", "Parse message error", e)
                }
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                SecureLog.e("XunfeiTts", "WebSocket failure", t)
            }
        }
        
        client.newWebSocket(request, webSocketListener)
        
        Thread.sleep(5000)
        
        return result
    }
}
