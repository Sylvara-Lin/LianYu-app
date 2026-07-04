package com.lianyu.ai.feature.qqbot.data.network

import com.lianyu.ai.common.SecureLog
import com.lianyu.ai.feature.qqbot.data.QQBotTokenStore
import com.lianyu.ai.feature.qqbot.data.model.QQGatewayPayload
import com.lianyu.ai.feature.qqbot.data.model.QQHelloData
import com.lianyu.ai.feature.qqbot.data.model.QQReadyData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import com.lianyu.ai.common.ChatConstants
import com.lianyu.ai.network.NetworkConstants
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class QQBotWebSocketClient(
    private val tokenStore: QQBotTokenStore,
    private val apiClient: QQBotApiClient,
    private val onEvent: suspend (QQGatewayPayload) -> Unit,
    private val onConnectionStateChange: ((ConnectionState) -> Unit)? = null
) {
    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        RECONNECTING,
        AUTH_FAILED
    }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(NetworkConstants.QQ_BOT_WS_CONNECT_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
        .readTimeout(NetworkConstants.QQ_BOT_WS_READ_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
        .writeTimeout(NetworkConstants.QQ_BOT_WS_WRITE_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
        .pingInterval(NetworkConstants.QQ_BOT_WS_PING_INTERVAL_SECONDS.toLong(), TimeUnit.SECONDS)
        .build()

    private val connectMutex = Mutex()
    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null

    private val isConnected = AtomicBoolean(false)
    private val isConnecting = AtomicBoolean(false)
    private val lastSequence = AtomicLong(0)
    private val reconnectAttempt = AtomicInteger(0)

    @Volatile
    private var sessionId: String? = null

    private val opDispatch = 0
    private val opHeartbeat = 1
    private val opIdentify = 2
    private val opResume = 6
    private val opReconnect = 7
    private val opInvalidSession = 9
    private val opHello = 10
    private val opHeartbeatAck = 11

    // Intents: C2C(1<<25) | GROUP_AT_MESSAGE(1<<30) | GUILDS(1<<0) | GUILD_MESSAGES(1<<9) | DIRECT_MESSAGE(1<<12) | INTERACTION(1<<26)
    private val intents = (1 shl 25) or (1 shl 30) or (1 shl 0) or (1 shl 9) or (1 shl 12) or (1 shl 26)

    suspend fun connect() = connectMutex.withLock {
        if (isConnected.get() || isConnecting.get()) return
        isConnecting.set(true)
        onConnectionStateChange?.invoke(ConnectionState.CONNECTING)
        try {
            val account = tokenStore.getAccount()
                ?: throw IllegalStateException("未配置 QQ Bot 账号")
            // [FIX] Bug3: 如果重试次数较多，强制刷新 token（可能是 token 本身有问题）
            val retryCount = reconnectAttempt.get()
            if (retryCount >= 3) {
                SecureLog.w(TAG, "Reconnect attempt $retryCount, clearing token cache for fresh token")
                apiClient.clearApiCache()
            }
            val restApi = apiClient.createAuthenticatedRestApi()
            val gateway = restApi.getGateway()
            if (!gateway.isSuccessful || gateway.body() == null) {
                val errorBody = gateway.errorBody()?.string()
                SecureLog.e(TAG, "获取 QQ Gateway 失败: ${gateway.code()} body=$errorBody")
                throw IllegalStateException("获取 QQ Gateway 失败: ${gateway.code()} $errorBody")
            }
            val gatewayUrl = gateway.body()!!.url
            SecureLog.i(TAG, "Gateway URL: $gatewayUrl")
            val request = Request.Builder().url(gatewayUrl).build()

            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(ws: WebSocket, response: Response) {
                    SecureLog.i(TAG, "WebSocket connected")
                }

                override fun onMessage(ws: WebSocket, text: String) {
                    handleMessage(text)
                }

                override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                    SecureLog.w(TAG, "WebSocket closing: $code $reason")
                    cleanupConnectionState()
                }

                override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                    SecureLog.w(TAG, "WebSocket closed: $code $reason")
                    cleanupConnectionState()
                    scheduleReconnect()
                }

                override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                    SecureLog.e(TAG, "WebSocket failure: ${t.message}", t)
                    cleanupConnectionState()
                    scheduleReconnect()
                }
            })
        } catch (e: Exception) {
            SecureLog.e(TAG, "connect failed: ${e.message}", e)
            isConnecting.set(false)
            // [FIX] Bug3: connect 失败也清除 token 缓存，下次重连重新获取
            apiClient.clearApiCache()
            // [FIX] Bug4: 连续失败超过阈值时通知 UI
            if (reconnectAttempt.get() >= 5) {
                onConnectionStateChange?.invoke(ConnectionState.AUTH_FAILED)
            }
            scheduleReconnect()
        }
    }

    fun disconnect() {
        reconnectJob?.cancel()
        heartbeatJob?.cancel()
        webSocket?.close(1000, "manual disconnect")
        webSocket = null
        isConnected.set(false)
        isConnecting.set(false)
        onConnectionStateChange?.invoke(ConnectionState.DISCONNECTED)
    }

    fun destroy() {
        disconnect()
        scope.cancel()
    }

    private fun handleMessage(text: String) {
        try {
            val payload = json.decodeFromString<QQGatewayPayload>(text)
            payload.s?.let {
                lastSequence.set(it.toLong())
                scope.launch { tokenStore.setLastSequence(it.toLong()) }
            }
            when (payload.op) {
                opDispatch -> {
                    if (payload.t == "READY") {
                        payload.d?.let {
                            val ready = json.decodeFromJsonElement<QQReadyData>(it)
                            sessionId = ready.sessionId
                            // 内存已更新，异步持久化，不阻塞回调线程
                            scope.launch { tokenStore.setSessionId(ready.sessionId) }
                            SecureLog.i(TAG, "READY: sessionId=${ready.sessionId}, bot=${ready.user?.username}")
                        }
                        // [FIX] Bug2: 只有收到 READY 才算真正在线
                        isConnected.set(true)
                        isConnecting.set(false)
                        reconnectAttempt.set(0)
                        onConnectionStateChange?.invoke(ConnectionState.CONNECTED)
                    }
                    scope.launch { onEvent(payload) }
                }
                opHello -> {
                    val hello = payload.d?.let { json.decodeFromJsonElement<QQHelloData>(it) }
                    // [PERF] 优先使用内存中的 sessionId/seq，避免在 WebSocket 回调线程 runBlocking 读 DataStore
                    val savedSessionId = sessionId
                    val savedSeq = lastSequence.get()
                    if (!savedSessionId.isNullOrBlank() && savedSeq > 0) {
                        val resumed = sendResume(savedSessionId, savedSeq)
                        if (!resumed) {
                            // Resume 失败（token 过期），sendResume 内部已降级为 Identify
                            // 如果降级的 Identify 也失败了，触发重连
                            SecureLog.e(TAG, "Resume failed (no valid token), reconnect required")
                            reconnect()
                            return
                        }
                    } else {
                        // [FIX] Bug1: sendIdentify 可能因 token 过期失败，失败后触发重连重试
                        val identified = sendIdentify()
                        if (!identified) {
                            SecureLog.e(TAG, "Identify failed, will reconnect to retry")
                            // 不设 isConnected=true，让重连逻辑接管
                            reconnect()
                            return
                        }
                    }
                    startHeartbeat(hello?.heartbeatInterval ?: 41250)
                    // [FIX] Bug2: isConnected 不在这里设，等 READY 事件
                    isConnecting.set(false)
                }
                opReconnect -> {
                    SecureLog.w(TAG, "Server requested reconnect")
                    reconnect()
                }
                opInvalidSession -> {
                    SecureLog.w(TAG, "Invalid session, clearing session state")
                    sessionId = null
                    scope.launch {
                        tokenStore.setSessionId(null)
                        tokenStore.setLastSequence(0)
                    }
                    reconnect()
                }
                opHeartbeatAck -> {
                    // ignore
                }
                else -> SecureLog.d(TAG, "Unhandled op: ${payload.op}")
            }
        } catch (e: Exception) {
            SecureLog.e(TAG, "Failed to handle gateway message: $text", e)
        }
    }

    /**
     * 发送 Identify 鉴权帧。
     * @return true 表示成功发送，false 表示 token 不可用（需重连重试）
     */
    private fun sendIdentify(): Boolean {
        // [PERF FIX] 优先使用内存缓存的 token
        var token = apiClient.getCachedToken()
        // [FIX] Bug1: token 过期时尝试同步刷新一次（WebSocket 回调线程，
        // 使用 scope.launch 替代 runBlocking 避免阻塞回调线程）
        if (token == null) {
            SecureLog.w(TAG, "Cached token is null/expired, attempting refresh...")
            // 无法在 WebSocket 回调中同步等待，返回 false 让重连逻辑处理
            return false
        }
        if (token == null) {
            SecureLog.e(TAG, "Cannot send identify: no valid token available")
            return false
        }
        val d = JsonObject(
            mapOf(
                "token" to JsonPrimitive("QQBot $token"),
                "intents" to JsonPrimitive(intents),
                "shard" to JsonArray(listOf(JsonPrimitive(0), JsonPrimitive(1))),
                "properties" to JsonObject(
                    mapOf(
                        PROPERTY_OS to JsonPrimitive("android"),
                        PROPERTY_BROWSER to JsonPrimitive("LianYuQQBot"),
                        PROPERTY_DEVICE to JsonPrimitive("LianYuAndroid")
                    )
                )
            )
        )
        val payload = QQGatewayPayload(op = opIdentify, d = d)
        send(json.encodeToString(payload))
        SecureLog.i(TAG, "Identify sent successfully")
        return true
    }

    /**
     * 发送 Resume 恢复帧。
     * @return true 表示成功发送，false 表示 token 不可用（降级为 Identify）
     */
    private fun sendResume(sessionIdValue: String, seq: Long): Boolean {
        var token = apiClient.getCachedToken()
        // [PERF FIX] 使用 scope.launch 替代 runBlocking 避免阻塞 WebSocket 回调线程
        if (token == null) {
            SecureLog.w(TAG, "Cached token is null/expired for resume, will reconnect with fresh token")
            scope.launch {
                try {
                    val account = tokenStore.getAccount() ?: return@launch
                    apiClient.getOrRefreshToken(account)
                } catch (e: Exception) {
                    SecureLog.e(TAG, "Token refresh failed during resume", e)
                }
            }
            return false
        }
        if (token == null) {
            SecureLog.e(TAG, "Cannot send resume: no valid token, falling back to identify")
            return sendIdentify()
        }
        val d = JsonObject(
            mapOf(
                "token" to JsonPrimitive("QQBot $token"),
                "session_id" to JsonPrimitive(sessionIdValue),
                "seq" to JsonPrimitive(seq)
            )
        )
        val payload = QQGatewayPayload(op = opResume, d = d)
        send(json.encodeToString(payload))
        SecureLog.i(TAG, "Resume sent with sessionId=$sessionIdValue, seq=$seq")
        return true
    }

    /**
     * [FIX] Bug1+Bug3: 带 token 刷新的 Identify 重试，供外部在 connect() 失败后调用。
     * 会先强制刷新 token（清除缓存），再走完整的 connect 流程。
     */
    suspend fun connectWithFreshToken() = connectMutex.withLock {
        if (isConnected.get()) return
        // 清除旧的 token 缓存，强制重新获取
        apiClient.clearApiCache()
        scope.launch {
            tokenStore.setAccessToken(null)
            tokenStore.setTokenExpireAt(0)
        }
        SecureLog.i(TAG, "Token cache cleared, retrying connect with fresh token")
        connect()
    }

    private fun startHeartbeat(intervalMs: Long) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            val period = (intervalMs * 0.8).toLong().coerceAtLeast(5000)
            while (isActive && isConnected.get()) {
                delay(period)
                if (isConnected.get()) {
                    send(json.encodeToString(QQGatewayPayload(op = opHeartbeat, d = JsonPrimitive(lastSequence.get()))))
                }
            }
        }
    }

    private fun send(text: String) {
        val sent = webSocket?.send(text) ?: false
        if (!sent) SecureLog.w(TAG, "Failed to send websocket message")
    }

    private fun cleanupConnectionState() {
        val wasConnected = isConnected.get()
        isConnected.set(false)
        isConnecting.set(false)
        heartbeatJob?.cancel()
        heartbeatJob = null
        if (wasConnected) {
            onConnectionStateChange?.invoke(ConnectionState.DISCONNECTED)
        }
    }

    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            val attempt = reconnectAttempt.incrementAndGet()
            val delayMs = (attempt * ChatConstants.QQ_BOT_RECONNECT_BACKOFF_BASE_MS).coerceAtMost(ChatConstants.QQ_BOT_RECONNECT_MAX_DELAY_MS)
            SecureLog.i(TAG, "Scheduling reconnect in ${delayMs}ms (attempt $attempt)")
            onConnectionStateChange?.invoke(ConnectionState.RECONNECTING)
            delay(delayMs)
            if (isActive) {
                connect()
            }
        }
    }

    private fun reconnect() {
        disconnect()
        scheduleReconnect()
    }

    companion object {
        private const val TAG = "QQBotWS"
        private const val PROPERTY_OS = "\$os"
        private const val PROPERTY_BROWSER = "\$browser"
        private const val PROPERTY_DEVICE = "\$device"
    }
}
