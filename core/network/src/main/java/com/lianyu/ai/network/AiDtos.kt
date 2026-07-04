package com.lianyu.ai.network

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

interface OpenAiApi {
    @POST
    suspend fun chatCompletion(
        @Url url: String,
        @Header("Authorization") authorization: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body request: ChatCompletionRequest
    ): ChatCompletionResponse
}

interface AnthropicApi {
    @POST
    suspend fun chatCompletion(
        @Url url: String,
        @Header("x-api-key") apiKey: String,
        @Header("anthropic-version") version: String = "2023-06-01",
        @Header("Content-Type") contentType: String = "application/json",
        @Body request: AnthropicRequest
    ): AnthropicResponse
}

interface GeminiApi {
    @POST
    suspend fun generateContent(
        @Url url: String,
        @Query("key") apiKey: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body request: GeminiRequest
    ): GeminiResponse
}

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<Message>,
    val temperature: Float = 0.7f,
    val max_tokens: Int? = null,
    val top_p: Float = 0.9f,
    val frequency_penalty: Float = 0.0f,
    val presence_penalty: Float = 0.0f,
    val stream: Boolean = false,
    val tools: List<ToolDefinition>? = null,
    val tool_choice: String? = null
)

@Serializable
data class ToolDefinition(
    val type: String = "function",
    val function: ToolFunction
)

@Serializable
data class ToolFunction(
    val name: String,
    val description: String,
    val parameters: ToolParameters
)

@Serializable
data class ToolParameters(
    val type: String = "object",
    val properties: Map<String, ToolProperty>,
    val required: List<String>? = null
)

@Serializable
data class ToolProperty(
    val type: String,
    val description: String
)

@Serializable
data class Message(
    val role: String,
    val content: String? = null,
    val reasoning_content: String? = null,
    val tool_calls: List<ToolCallRaw>? = null,
    val tool_call_id: String? = null
)

@Serializable
data class ToolCallRaw(
    val id: String,
    val type: String = "function",
    val function: ToolCallFunction
)

@Serializable
data class ToolCallFunction(
    val name: String,
    val arguments: String
)

@Serializable
data class VisionMessage(
    val role: String,
    val content: List<ContentPart>
)

@Serializable
data class ContentPart(
    val type: String,
    val text: String? = null,
    val image_url: ImageUrl? = null
)

@Serializable
data class ImageUrl(
    val url: String
)

@Serializable
data class ChatCompletionResponse(
    val choices: List<Choice>? = null,
    val error: ErrorDetail? = null
)

@Serializable
data class Choice(
    val message: Message? = null,
    val delta: Message? = null,
    val finish_reason: String? = null
)

@Serializable
data class ErrorDetail(
    val message: String? = null
)

@Serializable
data class AnthropicRequest(
    val model: String,
    val messages: List<AnthropicMessage>,
    val system: String? = null,
    val max_tokens: Int = 4096,
    val temperature: Float = 0.7f,
    val stream: Boolean = false
)

@Serializable
data class AnthropicMessage(
    val role: String,
    val content: String
)

@Serializable
data class AnthropicResponse(
    val content: List<AnthropicContent>? = null,
    val error: AnthropicError? = null
)

@Serializable
data class AnthropicContent(
    val text: String? = null
)

@Serializable
data class AnthropicError(
    val message: String? = null
)

@Serializable
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
    val generationConfig: GeminiGenerationConfig? = null
)

@Serializable
data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart>
)

@Serializable
data class GeminiPart(
    val text: String
)

@Serializable
data class GeminiGenerationConfig(
    val temperature: Float? = null,
    val maxOutputTokens: Int? = null
)

@Serializable
data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null,
    val error: GeminiError? = null
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null
)

@Serializable
data class GeminiError(
    val message: String? = null
)

@Serializable
data class ModelsListResponse(
    val data: List<ModelInfo>? = null,
    val error: ErrorDetail? = null
)

@Serializable
data class ModelInfo(
    val id: String? = null
)
