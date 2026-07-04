package com.lianyu.ai.network.provider

import com.lianyu.ai.database.model.ApiConfig
import com.lianyu.ai.database.model.ChatMessage
import com.lianyu.ai.database.model.MessageType
import com.lianyu.ai.network.AnthropicMessage
import com.lianyu.ai.network.AnthropicRequest
import com.lianyu.ai.network.AnthropicResponse
import com.lianyu.ai.network.Message
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Provider for Anthropic Claude API.
 *
 * Uses /messages endpoint with x-api-key header and anthropic-version.
 * Request/response formats differ from OpenAI-compatible APIs.
 */
class ClaudeProvider : AiProvider {

    // ── Helpers ──────────────────────────────────────────────────────

    private fun messagesUrl(config: ApiConfig): String {
        return "${config.baseUrl.trim().removeSuffix("/")}/messages"
    }

    private fun toAnthropicMessages(messages: List<Message>): List<AnthropicMessage> {
        return messages.filter { it.role != "system" }.map {
            AnthropicMessage(
                role = if (it.role == "user") "user" else "assistant",
                content = it.content ?: ""
            )
        }
    }

    // ── AiProvider implementation ────────────────────────────────────

    override suspend fun chat(
        messages: List<Message>,
        config: ApiConfig
    ): String {
        // chatWithReasoning for Claude returns no reasoning (reasoning is null)
        return chatWithReasoning(messages, config).first
    }

    override suspend fun chatWithReasoning(
        messages: List<Message>,
        config: ApiConfig
    ): Pair<String, String?> {
        val systemPrompt = messages.firstOrNull { it.role == "system" }?.content
        val anthropicMessages = toAnthropicMessages(messages)

        val request = AnthropicRequest(
            model = config.model,
            messages = anthropicMessages,
            system = systemPrompt,
            max_tokens = config.maxTokens ?: 800,
            temperature = config.temperature
        )

        val url = messagesUrl(config)

        val response = AiProvider.okHttpClient.newBuilder().build().let { client ->
            val jsonBody = AiProvider.json.encodeToString(AnthropicRequest.serializer(), request)
            val httpRequest = okhttp3.Request.Builder()
                .url(url)
                .addHeader("x-api-key", config.apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()

            val httpResponse = client.newCall(httpRequest).execute()
            val body = httpResponse.body?.string() ?: throw Exception("Empty response")

            if (!httpResponse.isSuccessful) {
                val errorMsg = runCatching {
                    AiProvider.json.decodeFromString<AnthropicResponse>(body).error?.message
                }.getOrNull()
                throw Exception(errorMsg ?: "HTTP ${httpResponse.code}")
            }

            AiProvider.json.decodeFromString<AnthropicResponse>(body)
        }

        if (response.error != null) {
            throw Exception(response.error.message ?: "API返回错误")
        }

        val text = response.content?.firstOrNull()?.text
            ?: throw Exception("API返回空内容")

        return Pair(text, null)
    }

    override suspend fun chatLight(
        messages: List<Message>,
        config: ApiConfig,
        temperature: Double,
        maxTokens: Int
    ): String {
        // Claude doesn't have a separate "light" mode — use the same /messages endpoint
        val systemPrompt = messages.firstOrNull { it.role == "system" }?.content
        val anthropicMessages = toAnthropicMessages(messages)

        val request = AnthropicRequest(
            model = config.model,
            messages = anthropicMessages,
            system = systemPrompt,
            max_tokens = maxTokens,
            temperature = temperature.toFloat()
        )

        val url = messagesUrl(config)
        val jsonBody = AiProvider.json.encodeToString(AnthropicRequest.serializer(), request)
        val httpRequest = okhttp3.Request.Builder()
            .url(url)
            .addHeader("x-api-key", config.apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        val httpResponse = AiProvider.okHttpClient.newCall(httpRequest).execute()
        val body = httpResponse.body?.string() ?: throw Exception("Empty response")

        if (!httpResponse.isSuccessful) {
            throw Exception("HTTP ${httpResponse.code}")
        }

        val response = AiProvider.json.decodeFromString<AnthropicResponse>(body)
        if (response.error != null) throw Exception(response.error.message ?: "API error")

        return response.content?.firstOrNull()?.text ?: ""
    }

    override suspend fun chatForTest(
        messages: List<Message>,
        config: ApiConfig
    ): String {
        val systemPrompt = messages.firstOrNull { it.role == "system" }?.content
        val anthropicMessages = toAnthropicMessages(messages)

        val request = AnthropicRequest(
            model = config.model,
            messages = anthropicMessages,
            system = systemPrompt,
            max_tokens = 5,
            temperature = config.temperature
        )

        val url = messagesUrl(config)

        val httpRequest = okhttp3.Request.Builder()
            .url(url)
            .addHeader("x-api-key", config.apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(
                AiProvider.json.encodeToString(AnthropicRequest.serializer(), request)
                    .toRequestBody("application/json".toMediaType())
            )
            .build()

        val httpResponse = AiProvider.okHttpClient.newCall(httpRequest).execute()
        val body = httpResponse.body?.string() ?: throw Exception("Empty response")

        if (!httpResponse.isSuccessful) {
            val errorMsg = runCatching {
                AiProvider.json.decodeFromString<AnthropicResponse>(body).error?.message
            }.getOrNull()
            throw Exception(errorMsg ?: "HTTP ${httpResponse.code}")
        }

        val response = AiProvider.json.decodeFromString<AnthropicResponse>(body)
        if (response.error != null) {
            throw Exception(response.error.message ?: "API返回错误")
        }

        return response.content?.firstOrNull()?.text
            ?: throw Exception("API返回空内容")
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
        val anthropicMessages = org.json.JSONArray()

        val recentHistory = history.takeLast(12)
        recentHistory.forEach { msg ->
            if (msg.isFromUser && msg.type == MessageType.IMAGE) {
                val userMsgObj = org.json.JSONObject()
                userMsgObj.put("role", "user")

                val contentArray = org.json.JSONArray()

                val textPart = org.json.JSONObject()
                textPart.put("type", "text")
                textPart.put("text", "请仔细观察这张图片，描述你看到的内容，并根据上下文进行回复。")
                contentArray.put(textPart)

                val imagePart = org.json.JSONObject()
                imagePart.put("type", "image")
                val sourceObj = org.json.JSONObject()
                sourceObj.put("type", "base64")
                sourceObj.put("media_type", mimeType)
                sourceObj.put("data", imageBase64)
                imagePart.put("source", sourceObj)
                contentArray.put(imagePart)

                userMsgObj.put("content", contentArray)
                anthropicMessages.put(userMsgObj)
            } else if (msg.isFromUser || !msg.isFromUser) {
                val msgObj = org.json.JSONObject()
                msgObj.put("role", if (msg.isFromUser) "user" else "assistant")
                msgObj.put("content", msg.content)
                anthropicMessages.put(msgObj)
            }
        }

        val requestBody = org.json.JSONObject()
        if (config.model.isNotBlank()) requestBody.put("model", config.model)
        requestBody.put("messages", anthropicMessages)
        requestBody.put("system", systemPrompt)
        requestBody.put("max_tokens", config.maxTokens ?: 800)
        requestBody.put("temperature", config.temperature.toDouble())

        val url = messagesUrl(config)

        val request = okhttp3.Request.Builder()
            .url(url)
            .addHeader("x-api-key", config.apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw Exception("Empty response")

        if (!response.isSuccessful) {
            val errorMsg = runCatching {
                val errorJson = org.json.JSONObject(body)
                errorJson.getJSONObject("error")?.getString("message")
            }.getOrNull() ?: "HTTP ${response.code}"
            throw Exception(errorMsg)
        }

        val responseJson = org.json.JSONObject(body)
        if (responseJson.has("error")) {
            throw Exception(responseJson.getJSONObject("error").getString("message") ?: "API返回错误")
        }

        val contents = responseJson.getJSONArray("content")
        if (contents.length() > 0) {
            return contents.getJSONObject(0).getString("text") ?: throw Exception("API返回空内容")
        }

        throw Exception("API返回空内容")
    }
}
