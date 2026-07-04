package com.lianyu.ai.network.provider

import com.lianyu.ai.database.model.ApiConfig
import com.lianyu.ai.database.model.ChatMessage
import com.lianyu.ai.network.Message
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

/**
 * Provider interface for AI chat APIs.
 * Each provider (OpenAI, DeepSeek, Claude, Gemini, DashScope) implements
 * its own URL construction, header setup, request body formatting,
 * response parsing, and stream handling.
 */
interface AiProvider {
    /**
     * Non-streaming chat. Returns response text.
     */
    suspend fun chat(
        messages: List<Message>,
        config: ApiConfig
    ): String

    /**
     * Non-streaming chat with reasoning content extraction.
     * Returns Pair(responseText, reasoningContent).
     */
    suspend fun chatWithReasoning(
        messages: List<Message>,
        config: ApiConfig
    ): Pair<String, String?>

    /**
     * Lightweight non-streaming chat (used by judge/generation helpers).
     */
    suspend fun chatLight(
        messages: List<Message>,
        config: ApiConfig,
        temperature: Double,
        maxTokens: Int
    ): String

    /**
     * Test connection — sends a minimal request and returns the response.
     */
    suspend fun chatForTest(
        messages: List<Message>,
        config: ApiConfig
    ): String

    /**
     * Vision chat — sends an image along with the conversation.
     */
    suspend fun chatVision(
        history: List<ChatMessage>,
        systemPrompt: String,
        lastUserMessage: String,
        imageBase64: String,
        mimeType: String,
        config: ApiConfig,
        client: OkHttpClient
    ): String

    companion object {
        /** Shared JSON instance for all providers */
        val json: Json = Json { ignoreUnknownKeys = true }

        /** Shared OkHttpClient for all providers */
        lateinit var okHttpClient: OkHttpClient

        /** Key selector: returns (startIndex, allKeys) for round-robin */
        var keySelector: (ApiConfig) -> Pair<Int, List<String>> = { config ->
            0 to config.getAllApiKeys()
        }

        /** Key failure handler: marks a key as failed for cooldown */
        var keyFailureHandler: (String) -> Unit = {}

        /**
         * Check if a model requires fixed temperature=1 (e.g., kimi-k2.6).
         */
        fun requiresFixedTemperature(model: String): Boolean {
            return model.contains("kimi-k2.6", ignoreCase = true) ||
                   model.contains("k2.6", ignoreCase = true)
        }
    }
}
