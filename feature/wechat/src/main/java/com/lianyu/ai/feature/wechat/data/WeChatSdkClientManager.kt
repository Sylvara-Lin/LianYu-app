package com.lianyu.ai.feature.wechat.data

import com.github.wechat.ilink.sdk.ILinkClient
import com.github.wechat.ilink.sdk.core.config.ILinkConfig
import com.github.wechat.ilink.sdk.core.context.ContextKey
import com.github.wechat.ilink.sdk.core.context.ConversationContext
import com.github.wechat.ilink.sdk.core.context.ResumeContext
import com.github.wechat.ilink.sdk.core.login.LoginContext
import com.github.wechat.ilink.sdk.core.login.LoginStatus
import com.github.wechat.ilink.sdk.core.model.WeixinMessage as SdkWeixinMessage
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class WeChatSdkClientManager(
    private val tokenStore: WeChatTokenStore
) {
    private val mutex = Mutex()
    private var client: ILinkClient? = null

    suspend fun startLogin(): WeChatQrCode = withContext(Dispatchers.IO) {
        val sdkClient = mutex.withLock {
            client?.close()
            ILinkClient.builder()
                .config(defaultConfig())
                .build()
                .also { client = it }
        }

        val qrContent = sdkClient.executeLogin().orEmpty()
        WeChatQrCode(
            statusToken = sdkClient.qrcode ?: qrContent,
            displayContent = qrContent.ifBlank { sdkClient.qrcode.orEmpty() }
        )
    }

    suspend fun pollLoginStatus(): A0 = withContext(Dispatchers.IO) {
        val sdkClient = mutex.withLock { client }
            ?: throw IllegalStateException("请先获取微信登录二维码")

        val future = sdkClient.loginFuture
            ?: throw IllegalStateException("微信登录尚未开始")

        if (!future.isDone) {
            val status = sdkClient.loginStatus.status
            val message = when (status) {
                LoginStatus.Status.SCANNED -> "等待手机确认"
                LoginStatus.Status.WAITING -> "等待扫码"
                else -> "等待登录"
            }
            throw IllegalStateException(message)
        }

        val context = runCatching { future.get() }.getOrElse { error ->
            val status = sdkClient.loginStatus
            throw IllegalStateException(status.errorMessage ?: error.message ?: "微信登录失败", error)
        }

        val account = context.toAccount()
        tokenStore.saveAccount(account)
        persistResumeContext(sdkClient.exportResumeContext(), account)
        account
    }

    suspend fun getUpdates(): List<SdkWeixinMessage> = withContext(Dispatchers.IO) {
        val sdkClient = ensureLoggedInClient()
        val messages = sdkClient.getUpdates()
        tokenStore.getAccount()?.let { account ->
            persistResumeContext(sdkClient.exportResumeContext(), account)
        }
        messages
    }

    suspend fun sendText(toUserId: String, text: String, contextToken: String? = null): Unit = withContext(Dispatchers.IO) {
        if (!contextToken.isNullOrBlank()) {
            val account = tokenStore.getAccount()
                ?: throw IllegalStateException("未登录微信")
            tokenStore.saveContextToken(account.accountId, toUserId, contextToken)
            rebuildFromStoredSession()
        }

        val sdkClient = ensureLoggedInClient()
        sdkClient.sendText(toUserId, text)
        tokenStore.getAccount()?.let { account ->
            persistResumeContext(sdkClient.exportResumeContext(), account)
        }
        Unit
    }

    suspend fun sendImage(
        toUserId: String,
        imageBytes: ByteArray,
        fileName: String,
        description: String? = null,
        contextToken: String? = null
    ): Unit = withContext(Dispatchers.IO) {
        if (!contextToken.isNullOrBlank()) {
            val account = tokenStore.getAccount()
                ?: throw IllegalStateException("未登录微信")
            tokenStore.saveContextToken(account.accountId, toUserId, contextToken)
            rebuildFromStoredSession()
        }

        val sdkClient = ensureLoggedInClient()
        sdkClient.sendImage(toUserId, imageBytes, fileName, description.orEmpty())
        tokenStore.getAccount()?.let { account ->
            persistResumeContext(sdkClient.exportResumeContext(), account)
        }
        Unit
    }

    suspend fun downloadMedia(cdnInfo: com.lianyu.ai.feature.wechat.data.model.M7): ByteArray? = withContext(Dispatchers.IO) {
        return@withContext try {
            val sdkClient = ensureLoggedInClient()

            val encryptedQueryParam = cdnInfo.encryptQueryParam
            val aesKey = cdnInfo.aesKey

            if (encryptedQueryParam.isNullOrBlank() || aesKey.isNullOrBlank()) {
                Log.w(TAG, "Missing CDN encryption info")
                return@withContext null
            }

            Log.d(TAG, "Downloading media via SDK...")

            val sdkCdnMedia = com.github.wechat.ilink.sdk.core.model.CDNMedia().apply {
                encrypt_query_param = encryptedQueryParam
                aes_key = aesKey
            }

            val mediaBytes = sdkClient.downloadMedia(sdkCdnMedia)

            if (mediaBytes != null && mediaBytes.size > 0) {
                Log.d(TAG, "Successfully downloaded ${mediaBytes.size} bytes")
                mediaBytes
            } else {
                Log.w(TAG, "SDK returned empty/null media")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download media", e)
            null
        }
    }

    /**
     * 通知某个用户的 contextToken 已更新，需要重建 client
     */
    @Volatile
    private var stale = false

    suspend fun notifyContextTokenUpdated(userId: String) {
        stale = true
        Log.i(TAG, "ContextToken updated for $userId, marking client stale")
    }

    suspend fun cancelLogin(): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            client?.cancelLogin()
        }
        Unit
    }

    suspend fun closeAndClear(): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            client?.close()
            client = null
            stale = false
        }
        tokenStore.clearAccount()
        Unit
    }

    private suspend fun rebuildFromStoredSession() {
        stale = true
    }

    private suspend fun ensureLoggedInClient(): ILinkClient {
        return mutex.withLock {
            val existing = client
            if (existing != null && existing.isLoggedIn && !stale) {
                existing
            } else {
                stale = false
                buildClientFromStore().also { client = it }
            }
        }
    }

    private suspend fun buildClientFromStore(): ILinkClient {
        val resumeContext = buildResumeContext()
            ?: throw IllegalStateException("未登录微信")

        return ILinkClient.builder()
            .config(defaultConfig())
            .resumeContext(resumeContext)
            .build()
    }

    private suspend fun buildResumeContext(): ResumeContext? {
        val account = tokenStore.getAccount() ?: return null
        val loginContext = LoginContext(
            account.botToken,
            account.ilinkUserId,
            account.ilinkBotId,
            account.baseUrl.ifBlank { DEFAULT_BASE_URL }
        )

        val contexts = tokenStore.getContextTokens(account.accountId)
            .mapValues { (userId, token) ->
                ConversationContext(ContextKey(account.ilinkBotId, userId)).apply {
                    latestContextToken = token
                }
            }

        return ResumeContext.builder(loginContext)
            .updatesCursor(tokenStore.getCursor())
            .conversationContexts(contexts)
            .build()
    }

    private suspend fun persistResumeContext(resumeContext: ResumeContext?, account: A0) {
        if (resumeContext == null) return

        resumeContext.updatesCursor?.let { cursor ->
            tokenStore.saveCursor(cursor)
        }

        resumeContext.conversationContexts.forEach { context ->
            val userId = context.key?.userId
            val token = context.latestContextToken
            if (!userId.isNullOrBlank() && !token.isNullOrBlank()) {
                tokenStore.saveContextToken(account.accountId, userId, token)
            }
        }
    }

    private fun LoginContext.toAccount(): A0 {
        return A0(
            botToken = botToken.orEmpty(),
            ilinkBotId = botId.orEmpty(),
            ilinkUserId = userId.orEmpty(),
            baseUrl = baseUrl?.takeIf { it.isNotBlank() } ?: DEFAULT_BASE_URL
        )
    }

    private fun defaultConfig(): ILinkConfig {
        return ILinkConfig.builder()
            .connectTimeoutMs(10000)
            .readTimeoutMs(15000)
            .writeTimeoutMs(10000)
            .loginTimeoutMs(LOGIN_TIMEOUT_MS)
            .heartbeatEnabled(false)
            .channelVersion("1.0.3")
            .build()
    }

    private companion object {
        const val TAG = "WeChatSdkClientManager"
        const val DEFAULT_BASE_URL = "https://ilinkai.weixin.qq.com"
        const val LOGIN_TIMEOUT_MS = 5 * 60 * 1000L
    }
}
