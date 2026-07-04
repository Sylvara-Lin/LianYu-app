package com.lianyu.ai.feature.coffee.data

import com.lianyu.ai.common.TimeoutBudgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 瑞幸 MCP HTTP 客户端。
 *
 * 基于 JSON-RPC 2.0 over Streamable HTTP 协议，调用瑞幸 MCP Server。
 * 响应可能是普通 JSON 或 SSE（text/event-stream），本客户端统一解析。
 *
 * 响应层级（两层信封，务必区分）：
 * 1. MCP JSON-RPC 层：{ jsonrpc, id, result: { content: [{ type:"text", text:"<json字符串>" }], isError } }
 *    —— parseMcpEnvelope 负责提取 result.content[0].text
 * 2. 瑞幸业务层：{ code, msg, data, success }
 *    —— extractBusinessData 负责校验 code/success 并返回 data
 *
 * 标准流程：
 * 1. initialize 握手（获取 session-id）
 * 2. notifications/initialized 通知
 * 3. tools/call 调用
 *
 * 安全约束（来自 SKILL.md）：
 * - 真实请求必须使用完整 Bearer Token，禁止占位符
 * - 调用过程对用户隐身，不暴露工具名/参数/原始返回
 */
class LuckinMcpClient(
    private val client: OkHttpClient = defaultClient
) {
    companion object {
        private const val MCP_URL = "https://gwmcp.lkcoffee.com/order/user/mcp"
        private const val JSON_RPC = "2.0"

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            explicitNulls = false
        }

        private val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(TimeoutBudgets.MCP_CONNECT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(TimeoutBudgets.MCP_READ_MS, TimeUnit.MILLISECONDS)
            .writeTimeout(TimeoutBudgets.MCP_WRITE_MS, TimeUnit.MILLISECONDS)
            .build()
    }

    // 会话状态（按 token 隔离）
    private var sessionToken: String? = null
    private var sessionId: String? = null
    private val initialized = AtomicBoolean(false)
    private val initMutex = Mutex()
    private var requestId = 1

    /**
     * 调用 MCP 工具，返回瑞幸业务信封里的 `data` 节点。
     *
     * @param token 用户瑞幸 MCP Token（完整原文，非占位符）
     * @param toolName 工具名（如 queryShopList）
     * @param arguments 工具参数（已序列化为 JsonElement）
     * @return 瑞幸业务信封 {code,msg,data,success} 中的 `data`（JsonElement）
     * @throws McpException Token 无效 / MCP 错误 / 瑞幸业务 code != 0
     */
    suspend fun callTool(
        token: String,
        toolName: String,
        arguments: JsonObject
    ): JsonElement = withContext(Dispatchers.IO) {
        require(token.isNotBlank()) { "Token 不能为空" }

        ensureInitialized(token)

        val request = buildToolCallRequest(token, toolName, arguments)
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            val body = response.body?.string().orEmpty()
            response.close()
            // 401/403 可能是 session 过期，重置初始化状态
            if (response.code == 401 || response.code == 403) {
                initialized.set(false)
                sessionId = null
            }
            throw McpException(
                "MCP 请求失败: HTTP ${response.code}",
                isAuthError = response.code == 401 || response.code == 403
            )
        }

        // 步骤1: 解析 MCP JSON-RPC 信封，取出 result.content[0].text（一段 JSON 字符串）
        val textPayload = parseMcpTextPayload(response)

        // 步骤2: 解析瑞幸业务信封 {code,msg,data,success}，校验并返回 data
        extractBusinessData(textPayload)
    }

    /**
     * 确保 MCP 会话已初始化（线程安全）
     */
    private suspend fun ensureInitialized(token: String) {
        if (initialized.get() && sessionToken == token && sessionId != null) return

        initMutex.withLock {
            if (initialized.get() && sessionToken == token && sessionId != null) return

            doInitialize(token)
        }
    }

    /**
     * MCP 初始化握手
     * 步骤1: 发送 initialize 请求，获取 session-id
     * 步骤2: 发送 notifications/initialized 通知
     */
    private suspend fun doInitialize(token: String) {
        val initRequest = buildJsonObject {
            put("jsonrpc", JSON_RPC)
            put("method", "initialize")
            put("id", requestId++)
            put("params", buildJsonObject {
                put("protocolVersion", "2025-03-26")
                put("capabilities", buildJsonObject {})
                put("clientInfo", buildJsonObject {
                    put("name", "LianYu")
                    put("version", "1.0.0")
                })
            })
        }

        val initPayload = json.encodeToString(JsonObject.serializer(), initRequest)
        val request = Request.Builder()
            .url(MCP_URL)
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .post(initPayload.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            response.body?.string()
            response.close()
            throw McpException(
                "MCP 初始化失败: HTTP ${response.code}",
                isAuthError = response.code == 401 || response.code == 403
            )
        }

        sessionId = response.header("Mcp-Session-Id")
        sessionToken = token

        // 读取并解析响应（确认握手成功），不消费业务 data
        readResponseBody(response)

        // 步骤2: 发送 notifications/initialized 通知（无响应体）
        val notifyRequest = buildJsonObject {
            put("jsonrpc", JSON_RPC)
            put("method", "notifications/initialized")
        }

        val notifyPayload = json.encodeToString(JsonObject.serializer(), notifyRequest)
        val notifyReq = Request.Builder()
            .url(MCP_URL)
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .apply { sessionId?.let { header("Mcp-Session-Id", it) } }
            .post(notifyPayload.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val notifyResponse = client.newCall(notifyReq).execute()
        notifyResponse.close()

        initialized.set(true)
    }

    /**
     * 构建 tools/call 请求
     */
    private fun buildToolCallRequest(
        token: String,
        toolName: String,
        arguments: JsonObject
    ): Request {
        val rpcRequest = buildJsonObject {
            put("jsonrpc", JSON_RPC)
            put("method", "tools/call")
            put("id", requestId++)
            put("params", buildJsonObject {
                put("name", toolName)
                put("arguments", arguments)
            })
        }

        val payload = json.encodeToString(JsonObject.serializer(), rpcRequest)

        return Request.Builder()
            .url(MCP_URL)
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .apply { sessionId?.let { header("Mcp-Session-Id", it) } }
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    /**
     * 解析 MCP JSON-RPC 信封，提取 result.content[0].text（瑞幸业务 JSON 字符串）。
     *
     * 支持两种传输格式：
     * 1. 普通 JSON：直接解析为 JSON-RPC 信封
     * 2. SSE（text/event-stream）：逐行读取 data: 行，拼接后解析
     */
    private suspend fun parseMcpTextPayload(response: Response): String {
        val rawText = readResponseBody(response)

        if (rawText.isBlank()) {
            throw IOException("MCP 响应为空")
        }

        val rpcResponse = try {
            json.parseToJsonElement(rawText).jsonObject
        } catch (e: Exception) {
            throw IOException("MCP 响应解析失败: ${e.message}\n原始内容: $rawText", e)
        }

        // 检查 JSON-RPC 错误
        val error = rpcResponse["error"] as? JsonObject
        if (error != null) {
            val code = error["code"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
            val message = error["message"]?.jsonPrimitive?.contentOrNull ?: "未知错误"
            // 会话过期错误，重置初始化状态
            if (code == -32001 || message.contains("session", ignoreCase = true)) {
                initialized.set(false)
                sessionId = null
            }
            throw McpException(
                "MCP 错误[$code]: $message",
                isAuthError = code == -32001 ||
                    message.contains("oauth", ignoreCase = true) ||
                    message.contains("token", ignoreCase = true)
            )
        }

        val result = rpcResponse["result"] as? JsonObject
            ?: throw IOException("MCP 响应缺少 result 字段")

        val isError = result["isError"]?.jsonPrimitive?.contentOrNull
            ?.toBooleanStrictOrNull() ?: false

        val contentArray = result["content"]
        val textContent = if (contentArray is JsonArray) {
            contentArray.firstOrNull()
                ?.let { (it as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull }
        } else {
            null
        }

        if (isError) {
            throw McpException(textContent ?: "MCP 工具执行出错")
        }

        return textContent ?: throw IOException("MCP 响应缺少 content[0].text")
    }

    /**
     * 解析瑞幸业务信封 { code, msg, data, success }，校验后返回 data。
     *
     * - code != 0 或 success == false → 抛 McpException(msg)
     * - data 缺失 → 抛 IOException
     * - data 为基本类型（如 cancelOrder 返回 true）→ 原样返回 JsonPrimitive
     */
    private fun extractBusinessData(textPayload: String): JsonElement {
        val envelope = try {
            json.parseToJsonElement(textPayload).jsonObject
        } catch (e: Exception) {
            throw IOException("瑞幸业务响应解析失败: ${e.message}\n原始: $textPayload", e)
        }

        val code = envelope["code"]?.jsonPrimitive?.intOrNull
            ?: envelope["code"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
        val msg = envelope["msg"]?.jsonPrimitive?.contentOrNull
        val success = envelope["success"]?.jsonPrimitive?.contentOrNull
            ?.toBooleanStrictOrNull() ?: (code == 0)

        if (code != null && code != 0 || !success) {
            val message = msg?.takeIf { it.isNotBlank() } ?: "瑞幸接口返回错误 (code=$code)"
            throw McpException(
                message,
                isAuthError = message.contains("token", ignoreCase = true) ||
                    message.contains("登录", ignoreCase = true) ||
                    message.contains("授权", ignoreCase = true)
            )
        }

        return envelope["data"]
            ?: throw IOException("瑞幸业务响应缺少 data 字段: $textPayload")
    }

    /**
     * 读取响应体，支持 SSE 和普通 JSON
     */
    private suspend fun readResponseBody(response: Response): String {
        val contentType = response.header("Content-Type").orEmpty()
        val body = response.body ?: throw IOException("响应体为空")

        val rawText = if (contentType.contains("text/event-stream", ignoreCase = true)) {
            // SSE 流：加 withTimeout 防止协程永久阻塞
            withTimeout(TimeoutBudgets.MCP_SSE_READ_MS) {
                readSseStream(body.byteStream())
            }
        } else {
            body.string()
        }
        response.close()
        return rawText
    }

    /**
     * 读取 SSE 流，提取所有 data: 行内容并拼接。
     */
    private fun readSseStream(inputStream: java.io.InputStream): String {
        val reader = BufferedReader(InputStreamReader(inputStream))
        val sb = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            if (line!!.startsWith("data:")) {
                sb.append(line!!.removePrefix("data:").trim())
            }
        }
        reader.close()
        return sb.toString()
    }
}

/**
 * MCP 调用异常。
 * @param isAuthError 是否为认证错误（Token 无效/过期），用于 UI 提示重新获取 Token
 */
class McpException(message: String, val isAuthError: Boolean = false) : IOException(message)
