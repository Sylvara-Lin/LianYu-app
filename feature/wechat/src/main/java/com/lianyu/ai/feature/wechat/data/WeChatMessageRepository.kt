package com.lianyu.ai.feature.wechat.data

import android.content.Context
import android.util.Log
import com.lianyu.ai.common.AppForegroundTracker
import com.lianyu.ai.common.TimeoutBudgets
import com.lianyu.ai.feature.wechat.data.model.M0
import com.lianyu.ai.feature.wechat.data.model.M1Type
import com.lianyu.ai.feature.wechat.service.WeChatAiReplyWorker
import com.lianyu.ai.feature.wechat.service.WeChatNotificationHelper
import com.lianyu.ai.feature.wechat.service.WeChatServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class WeChatMessageRepository(
    context: Context,
    private val sdkClientManager: WeChatSdkClientManager,
    private val tokenStore: WeChatTokenStore
) {
    private val appContext = context.applicationContext
    private val processScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _incomingMessages = MutableSharedFlow<M0>(
        extraBufferCapacity = 100,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val incomingMessages: Flow<M0> = _incomingMessages.asSharedFlow()

    val accountFlow = tokenStore.accountFlow
    val isLoggedInFlow = tokenStore.accountFlow.map { it != null }
    suspend fun isLoggedIn(): Boolean = tokenStore.isLoggedIn()

    private val activeReplyJobs = ConcurrentHashMap<String, kotlinx.coroutines.Job>()

    suspend fun getQrCode(): Result<WeChatQrCode> = withContext(Dispatchers.IO) {
        runCatching { sdkClientManager.startLogin() }
    }

    suspend fun pollQrCodeStatus(qrCode: String): Result<A0> = withContext(Dispatchers.IO) {
        runCatching { sdkClientManager.pollLoginStatus() }
    }

    suspend fun logout() {
        sdkClientManager.closeAndClear()
        activeReplyJobs.values.forEach { it.cancel() }
        activeReplyJobs.clear()
        processScope.cancel()
    }

    /**
     * 销毁 Repository，取消所有协程和订阅。
     * 应在 WeChat 功能完全退出时调用。
     */
    fun destroy() {
        processScope.cancel()
        activeReplyJobs.values.forEach { it.cancel() }
        activeReplyJobs.clear()
    }

    suspend fun pollMessages(timeoutMs: Long = TimeoutBudgets.WECHAT_POLL_TIMEOUT_MS): Result<WeChatPollResult> = withContext(Dispatchers.IO) {
        runCatching {
            val account = tokenStore.getAccount()
                ?: throw IllegalStateException("未登录微信")

            val rawMessages = sdkClientManager.getUpdates()
            val messages = rawMessages.map { WeChatSdkMessageMapper.toAppMessage(it) }

            if (messages.isNotEmpty()) {
                messages.forEach { msg -> handleIncomingMessageFast(account, msg) }
            }

            WeChatPollResult(messages)
        }
    }

    suspend fun sendTextMessage(
        toUserId: String,
        text: String,
        contextToken: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            tokenStore.getAccount()
                ?: throw IllegalStateException("未登录微信")
            sdkClientManager.sendText(toUserId, text, contextToken)
        }.mapFailure(::toUserFacingException)
    }

    suspend fun sendImageMessage(
        toUserId: String,
        imageBytes: ByteArray,
        fileName: String,
        description: String? = null,
        contextToken: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            tokenStore.getAccount()
                ?: throw IllegalStateException("未登录微信")
            sdkClientManager.sendImage(toUserId, imageBytes, fileName, description, contextToken)
        }.mapFailure(::toUserFacingException)
    }

    suspend fun getContextToken(userId: String): String? {
        val account = tokenStore.getAccount() ?: return null
        return tokenStore.getContextToken(account.accountId, userId)
    }

    fun extractText(message: M0): String {
        return message.itemList?.firstNotNullOfOrNull { item ->
            item.textItem?.text
        } ?: ""
    }

    private fun handleIncomingMessageFast(account: A0, message: M0) {
        message.fromUserId?.let { userId ->
            message.contextToken?.let { token ->
                processScope.launch {
                    val existingToken = tokenStore.getContextToken(account.accountId, userId)
                    if (existingToken != token) {
                        tokenStore.saveContextToken(account.accountId, userId, token)
                        sdkClientManager.notifyContextTokenUpdated(userId)
                    }
                }
            }
        }

        _incomingMessages.tryEmit(message)

        val text = extractText(message)
        val isImageMessage = isImageMessage(message)

        Log.d(TAG, "Received message: from=${message.fromUserId}, type=${message.messageType}, hasText=${text.isNotBlank()}, isImage=$isImageMessage")

        if (text.isBlank() && !isImageMessage) {
            Log.d(TAG, "Skipping message: no text content and not an image")
            return
        }

        if (isImageMessage) {
            Log.d(TAG, "Detected image message, processing with vision AI")
            handleImageMessage(account, message)
            return
        }

        processScope.launch {
            try {
                val notifyEnabled = tokenStore.getNotifyEnabled()
                val autoReplyEnabled = tokenStore.getAutoReply()

                val decision = WeChatIncomingMessagePolicy.evaluate(
                    isAppInForeground = AppForegroundTracker.isInForeground,
                    notifyEnabled = notifyEnabled,
                    autoReplyEnabled = autoReplyEnabled,
                    messageText = text
                )

                if (decision.shouldNotify) {
                    WeChatNotificationHelper.showIncomingMessageNotification(appContext, message)
                }

                val fromUserId = message.fromUserId ?: return@launch
                if (!decision.shouldAutoReply || fromUserId.isBlank()) return@launch

                val existingJob = activeReplyJobs[fromUserId]
                if (existingJob?.isActive == true) {
                    Log.d(TAG, "AI reply already in progress for $fromUserId, skipping duplicate enqueue")
                    return@launch
                }

                Log.d(TAG, "Starting immediate AI reply for $fromUserId")
                val job = processScope.launch {
                    try {
                        val bridge = WeChatServiceLocator.chatBridge(appContext)
                        bridge.handleTextMessage(fromUserId, text)
                    } catch (e: Exception) {
                        Log.e(TAG, "Direct AI reply failed, falling back to Worker", e)
                        WeChatAiReplyWorker.enqueue(appContext, fromUserId, text)
                    } finally {
                        activeReplyJobs.remove(fromUserId)
                    }
                }
                activeReplyJobs[fromUserId] = job
            } catch (e: Exception) {
                Log.e(TAG, "Error in handleIncomingMessageFast", e)
            }
        }
    }

    private fun isImageMessage(message: M0): Boolean {
        return message.messageType == M1Type.IMAGE.value ||
            message.itemList?.any { it.type == M1Type.IMAGE.value && it.imageItem != null } == true
    }

    private fun handleImageMessage(account: A0, message: M0) {
        val fromUserId = message.fromUserId ?: run {
            Log.w(TAG, "Image message missing fromUserId")
            return
        }

        processScope.launch {
            try {
                val notifyEnabled = tokenStore.getNotifyEnabled()
                val autoReplyEnabled = tokenStore.getAutoReply()

                if (notifyEnabled) {
                    WeChatNotificationHelper.showIncomingMessageNotification(appContext, message)
                }

                if (!autoReplyEnabled || fromUserId.isBlank()) {
                    Log.d(TAG, "Auto-reply disabled or userId blank for image from $fromUserId")
                    return@launch
                }

                val existingJob = activeReplyJobs[fromUserId]
                if (existingJob?.isActive == true) {
                    Log.d(TAG, "AI reply already in progress for $fromUserId, skipping image")
                    return@launch
                }

                Log.d(TAG, "Starting AI vision reply for image from $fromUserId")
                val job = processScope.launch {
                    try {
                        val bridge = WeChatServiceLocator.chatBridge(appContext)
                        bridge.handleImageMessage(fromUserId, message)
                    } catch (e: Exception) {
                        Log.e(TAG, "AI vision reply failed for image", e)
                        WeChatAiReplyWorker.enqueue(appContext, fromUserId, "[图片]")
                    } finally {
                        activeReplyJobs.remove(fromUserId)
                    }
                }
                activeReplyJobs[fromUserId] = job
            } catch (e: Exception) {
                Log.e(TAG, "Error handling image message", e)
            }
        }
    }

    private fun toUserFacingException(error: Throwable): Throwable {
        val message = error.message.orEmpty()
        return if (message.contains("missing latest context token", ignoreCase = true)) {
            IllegalStateException("缺少 context_token，请先让对方发送消息", error)
        } else {
            error
        }
    }

    companion object {
        private const val TAG = "WeChatMsgRepo"
    }
}

data class WeChatPollResult(
    val messages: List<M0>
)

data class WeChatQrCode(
    val statusToken: String,
    val displayContent: String
)

private inline fun <T> Result<T>.mapFailure(transform: (Throwable) -> Throwable): Result<T> {
    return fold(
        onSuccess = { Result.success(it) },
        onFailure = { Result.failure(transform(it)) }
    )
}
