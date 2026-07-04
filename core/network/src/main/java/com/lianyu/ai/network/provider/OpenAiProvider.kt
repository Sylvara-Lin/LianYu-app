package com.lianyu.ai.network.provider

import com.lianyu.ai.database.model.ApiConfig
import com.lianyu.ai.database.model.ApiProvider
import com.lianyu.ai.database.model.ChatMessage
import com.lianyu.ai.database.model.MessageType
import com.lianyu.ai.network.ChatCompletionResponse
import com.lianyu.ai.network.Message
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Provider for all OpenAI-compatible APIs.
 *
 * Handles: OpenAI, DeepSeek, DashScope (Qwen), Gemini (via OpenAI-compatible endpoint),
 * Kimi, Xiaomi MiMo, Zhipu, SiliconFlow, OpenRouter, Groq, Custom, Partner.
 *
 * All these providers share the same /chat/completions endpoint with
 * Authorization: Bearer (or api-key for Xiaomi) header.
 */
open class OpenAiCompatibleProvider : AiProvider {

    // ── Helpers ──────────────────────────────────────────────────────

    private fun normalizeBaseUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        return if (
            trimmed.endsWith("/v1") ||
            trimmed.endsWith("/v1beta") ||
            trimmed.endsWith("/v4") ||
            trimmed.endsWith("/openai") ||
            trimmed.endsWith("/compatible-mode/v1")
        ) {
            trimmed
        } else {
            "$trimmed/v1"
        }
    }

    private fun chatUrl(config: ApiConfig): String {
        return "${normalizeBaseUrl(config.baseUrl).trimEnd('/')}/chat/completions"
    }

    private fun usesMaxCompletionTokens(provider: ApiProvider): Boolean {
        return provider == ApiProvider.XIAOMI
    }

    private fun prefersApiKeyHeader(provider: ApiProvider): Boolean {
        return provider == ApiProvider.XIAOMI
    }

    private fun stripThinkingContent(content: String): String {
        var result = content
        // XML/HTML 风格思考标签
        result = result.replace(Regex("(?is)<think[^>]*>[\\s\\S]*?</think\\s*>"), "")
        result = result.replace(Regex("(?is)<thinking[^>]*>[\\s\\S]*?</thinking\\s*>"), "")
        result = result.replace(Regex("(?is)<thought[^>]*>[\\s\\S]*?</thought\\s*>"), "")
        result = result.replace(Regex("(?is)<reflection[^>]*>[\\s\\S]*?</reflection\\s*>"), "")
        // Markdown 风格思考标题
        result = result.replace(Regex("(?im)^#{1,3}\\s*(思考|思维|推理|分析|Thinking|Reasoning|Analysis|Thought)\\s*\\n[\\s\\S]*?(?=\\n#{1,3}\\s|$)"), "")
        // 【思考】/【推理】等方括号包裹的思考块
        result = result.replace(Regex("(?is)【(思考|思维|推理|分析)】[\\s\\S]*?【/(思考|思维|推理|分析)】"), "")
        // 行内 [思考] ... [/思考] 格式
        result = result.replace(Regex("(?is)\\[(思考|思维|推理|分析|thought|thinking)]\\s*[\\s\\S]*?\\[/\\1]"), "")
        return result.trim()
    }

    private fun buildAuthHeader(provider: ApiProvider, key: String): Pair<String, String> {
        return if (prefersApiKeyHeader(provider)) {
            "api-key" to key
        } else {
            "Authorization" to "Bearer $key"
        }
    }

    private fun buildMaxTokensParam(provider: ApiProvider): String {
        return if (usesMaxCompletionTokens(provider)) "max_completion_tokens" else "max_tokens"
    }

    // ── AiProvider implementation ────────────────────────────────────

    override suspend fun chat(
        messages: List<Message>,
        config: ApiConfig
    ): String {
        return chatWithReasoning(messages, config).first
    }

    override suspend fun chatWithReasoning(
        messages: List<Message>,
        config: ApiConfig
    ): Pair<String, String?> {
        val safeTemp = config.temperature.coerceIn(0.1f, 1.5f)
        val url = chatUrl(config)

        val (startIdx, allKeys) = AiProvider.keySelector(config)
        var lastException: Exception? = null

        val jsonArray = org.json.JSONArray()
        for (msg in messages) {
            val msgObj = org.json.JSONObject()
            msgObj.put("role", msg.role)
            msgObj.put("content", msg.content)
            jsonArray.put(msgObj)
        }

        for (i in 0 until allKeys.size) {
            val keyIndex = (startIdx + i) % allKeys.size
            val currentKey = allKeys[keyIndex]
            try {
                val jsonBody = org.json.JSONObject()
                if (config.model.isNotBlank()) jsonBody.put("model", config.model)
                jsonBody.put("messages", jsonArray)
                if (!AiProvider.requiresFixedTemperature(config.model)) {
                    jsonBody.put("temperature", safeTemp.toDouble())
                }
                val maxTokens = config.maxTokens ?: 800
                if (maxTokens > 0) {
                    jsonBody.put(buildMaxTokensParam(config.provider), maxTokens)
                }

                val (headerName, headerValue) = buildAuthHeader(config.provider, currentKey)
                val request = okhttp3.Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json")
                    .addHeader(headerName, headerValue)
                    .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = AiProvider.okHttpClient.newCall(request).execute()
                val body = response.body?.string() ?: throw Exception("Empty response")

                if (!response.isSuccessful) {
                    val errorMsg = if (body.trimStart().startsWith("{")) {
                        runCatching {
                            AiProvider.json.decodeFromString<ChatCompletionResponse>(body).error?.message
                        }.getOrNull()
                    } else null
                    throw Exception(errorMsg ?: "HTTP ${response.code}: 服务器返回错误页面")
                }

                // Detect HTML error pages from servers that don't return proper JSON errors
                val trimmedBody = body.trimStart()
                if (trimmedBody.startsWith("<!") || trimmedBody.startsWith("<html", ignoreCase = true)) {
                    throw Exception("服务器返回了网页而非API响应 (HTTP ${response.code})，请检查API密钥/地址是否正确")
                }
                val parsed = AiProvider.json.decodeFromString<ChatCompletionResponse>(body)
                if (parsed.error != null) {
                    throw Exception(parsed.error.message ?: "API返回错误")
                }

                val message = parsed.choices?.firstOrNull()?.message
                var content = message?.content ?: throw Exception("API返回空内容")
                val reasoning = message.reasoning_content
                content = stripThinkingContent(content)
                return Pair(content, reasoning)
            } catch (e: Exception) {
                lastException = e
                AiProvider.keyFailureHandler(currentKey)
                if (i < allKeys.size - 1) continue else throw lastException
            }
        }

        throw lastException ?: Exception("所有 API Key 均请求失败")
    }

    override suspend fun chatLight(
        messages: List<Message>,
        config: ApiConfig,
        temperature: Double,
        maxTokens: Int
    ): String {
        val url = chatUrl(config)
        val allKeys = config.getAllApiKeys()
        var lastException: Exception? = null

        val lightClient = AiProvider.okHttpClient.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()

        for (keyIndex in allKeys.indices) {
            val currentKey = allKeys[keyIndex]
            try {
                val jsonArray = org.json.JSONArray()
                for (msg in messages) {
                    val msgObj = org.json.JSONObject()
                    msgObj.put("role", msg.role)
                    msgObj.put("content", msg.content)
                    jsonArray.put(msgObj)
                }
                val jsonBody = org.json.JSONObject()
                if (config.model.isNotBlank()) jsonBody.put("model", config.model)
                jsonBody.put("messages", jsonArray)
                if (!AiProvider.requiresFixedTemperature(config.model)) {
                    jsonBody.put("temperature", temperature)
                }
                jsonBody.put(buildMaxTokensParam(config.provider), maxTokens)

                val (headerName, headerValue) = buildAuthHeader(config.provider, currentKey)
                val request = okhttp3.Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json")
                    .addHeader(headerName, headerValue)
                    .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = lightClient.newCall(request).execute()
                val body = response.body?.string() ?: throw Exception("Empty response")
                if (!response.isSuccessful) {
                    throw Exception("HTTP ${response.code}")
                }
                val parsed = AiProvider.json.decodeFromString<ChatCompletionResponse>(body)
                if (parsed.error != null) throw Exception(parsed.error.message ?: "API error")

                return parsed.choices?.firstOrNull()?.message?.content ?: ""
            } catch (e: java.net.SocketTimeoutException) {
                lastException = e
                if (keyIndex < allKeys.size - 1) continue else throw lastException
            } catch (e: Exception) {
                lastException = e
                if (keyIndex < allKeys.size - 1) continue else throw lastException
            }
        }

        throw lastException ?: Exception("所有 API Key 均请求失败")
    }

    override suspend fun chatForTest(
        messages: List<Message>,
        config: ApiConfig
    ): String {
        val url = chatUrl(config)

        val (startIdx, allKeys) = AiProvider.keySelector(config)
        var lastException: Exception? = null

        val jsonArray = org.json.JSONArray()
        for (msg in messages) {
            val msgObj = org.json.JSONObject()
            msgObj.put("role", msg.role)
            msgObj.put("content", msg.content)
            jsonArray.put(msgObj)
        }

        for (i in 0 until allKeys.size) {
            val keyIndex = (startIdx + i) % allKeys.size
            val currentKey = allKeys[keyIndex]
            try {
                val jsonBody = org.json.JSONObject()
                if (config.model.isNotBlank()) jsonBody.put("model", config.model)
                jsonBody.put("messages", jsonArray)
                if (!AiProvider.requiresFixedTemperature(config.model)) {
                    jsonBody.put("temperature", 0.7)
                }
                jsonBody.put(buildMaxTokensParam(config.provider), 100)

                val (headerName, headerValue) = buildAuthHeader(config.provider, currentKey)
                val request = okhttp3.Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json")
                    .addHeader(headerName, headerValue)
                    .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = AiProvider.okHttpClient.newCall(request).execute()
                val body = response.body?.string() ?: throw Exception("Empty response")

                if (!response.isSuccessful) {
                    val errorMsg = if (body.trimStart().startsWith("{")) {
                        runCatching {
                            AiProvider.json.decodeFromString<ChatCompletionResponse>(body).error?.message
                        }.getOrNull()
                    } else null
                    throw Exception(errorMsg ?: "HTTP ${response.code}: 服务器返回错误页面")
                }

                // Detect HTML error pages from servers that don't return proper JSON errors
                val trimmedBody = body.trimStart()
                if (trimmedBody.startsWith("<!") || trimmedBody.startsWith("<html", ignoreCase = true)) {
                    throw Exception("服务器返回了网页而非API响应 (HTTP ${response.code})，请检查API密钥/地址是否正确")
                }
                val parsed = AiProvider.json.decodeFromString<ChatCompletionResponse>(body)
                if (parsed.error != null) {
                    throw Exception(parsed.error.message ?: "API返回错误")
                }

                var content = parsed.choices?.firstOrNull()?.message?.content
                    ?: throw Exception("API返回空内容")
                content = stripThinkingContent(content)
                return content
            } catch (e: Exception) {
                lastException = e
                AiProvider.keyFailureHandler(currentKey)
                if (i < allKeys.size - 1) continue else throw lastException
            }
        }

        throw lastException ?: Exception("所有 API Key 均请求失败")
    }

    override suspend fun chatVision(
        history: List<ChatMessage>,
        systemPrompt: String,
        lastUserMessage: String,
        imageBase64: String,
        mimeType: String,
        config: ApiConfig,
        client: OkHttpClient
    ): String {
        val safeTemp = config.temperature.coerceIn(0.1f, 1.5f)
        val url = chatUrl(config)
        val allKeys = config.getAllApiKeys()
        if (allKeys.isEmpty()) {
            throw Exception("API Key 为空，请检查视觉模型配置")
        }
        var lastException: Exception? = null

        for (keyIndex in allKeys.indices) {
            val currentKey = allKeys[keyIndex]
            try {
                val messagesJson = org.json.JSONArray()

                // System message
                val systemMsg = org.json.JSONObject()
                systemMsg.put("role", "system")
                systemMsg.put("content", systemPrompt)
                messagesJson.put(systemMsg)

                val recentHistory = history.takeLast(12)
                recentHistory.forEach { msg ->
                    if (msg.isFromUser && msg.type == MessageType.IMAGE) {
                        val userMessageJson = org.json.JSONObject()
                        userMessageJson.put("role", "user")

                        val contentArray = org.json.JSONArray()
                        val textPart = org.json.JSONObject()
                        textPart.put("type", "text")
                        textPart.put("text", "请仔细观察这张图片，描述你看到的内容，并根据上下文进行回复。")
                        contentArray.put(textPart)

                        val imagePart = org.json.JSONObject()
                        imagePart.put("type", "image_url")
                        val imageUrlObj = org.json.JSONObject()
                        imageUrlObj.put("url", "data:$mimeType;base64,$imageBase64")
                        imagePart.put("image_url", imageUrlObj)
                        contentArray.put(imagePart)

                        userMessageJson.put("content", contentArray)
                        messagesJson.put(userMessageJson)
                    } else {
                        val msgObj = org.json.JSONObject()
                        msgObj.put("role", if (msg.isFromUser) "user" else "assistant")
                        msgObj.put("content", msg.content)
                        messagesJson.put(msgObj)
                    }
                }

                val jsonBody = org.json.JSONObject()
                if (config.model.isNotBlank()) jsonBody.put("model", config.model)
                jsonBody.put("messages", messagesJson)
                if (!AiProvider.requiresFixedTemperature(config.model)) {
                    jsonBody.put("temperature", safeTemp.toDouble())
                }
                val maxTokens = config.maxTokens ?: 800
                if (maxTokens > 0) {
                    jsonBody.put(buildMaxTokensParam(config.provider), maxTokens)
                }

                val (headerName, headerValue) = buildAuthHeader(config.provider, currentKey)
                val request = okhttp3.Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json")
                    .addHeader(headerName, headerValue)
                    .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: throw Exception("Empty response")

                if (!response.isSuccessful) {
                    val errorMsg = if (body.trimStart().startsWith("{")) {
                        runCatching {
                            AiProvider.json.decodeFromString<ChatCompletionResponse>(body).error?.message
                        }.getOrNull()
                    } else null
                    throw Exception(errorMsg ?: "HTTP ${response.code}: 服务器返回错误页面")
                }

                // Detect HTML error pages from servers that don't return proper JSON errors
                val trimmedBody = body.trimStart()
                if (trimmedBody.startsWith("<!") || trimmedBody.startsWith("<html", ignoreCase = true)) {
                    throw Exception("服务器返回了网页而非API响应 (HTTP ${response.code})，请检查API密钥/地址是否正确")
                }
                val parsed = AiProvider.json.decodeFromString<ChatCompletionResponse>(body)
                if (parsed.error != null) {
                    throw Exception(parsed.error.message ?: "API返回错误")
                }

                val message = parsed.choices?.firstOrNull()?.message
                var content = message?.content ?: throw Exception("API返回空内容")
                content = stripThinkingContent(content)
                return content
            } catch (e: java.net.SocketTimeoutException) {
                lastException = e
                if (keyIndex < allKeys.size - 1) continue else throw e
            } catch (e: Exception) {
                lastException = e
                if (keyIndex < allKeys.size - 1) continue else throw lastException
            }
        }

        throw lastException ?: Exception("所有 API Key 均请求失败")
    }
}
