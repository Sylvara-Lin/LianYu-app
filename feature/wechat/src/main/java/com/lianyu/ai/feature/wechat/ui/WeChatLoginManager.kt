package com.lianyu.ai.feature.wechat.ui

import com.lianyu.ai.feature.wechat.data.A0
import com.lianyu.ai.feature.wechat.data.WeChatMessageRepository
import com.lianyu.ai.feature.wechat.data.WeChatQrCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class WeChatLoginManager(
    private val repository: WeChatMessageRepository
) {

    private var qrPollJob: Job? = null

    suspend fun getQrCode(): Result<WeChatQrCode> = repository.getQrCode()

    fun startQrPolling(
        qrCode: String,
        scope: CoroutineScope,
        onSuccess: (A0) -> Unit,
        onExpired: () -> Unit,
        onTimeout: () -> Unit
    ) {
        qrPollJob?.cancel()
        qrPollJob = scope.launch {
            val completed = withTimeoutOrNull(QR_LOGIN_TIMEOUT_MS) {
                while (isActive) {
                    val result = repository.pollQrCodeStatus(qrCode)
                    result.onSuccess { account ->
                        onSuccess(account)
                        qrPollJob?.cancel()
                        return@withTimeoutOrNull true
                    }.onFailure { error ->
                        val msg = error.message.orEmpty()
                        if (msg.contains("过期") || msg.contains("expired", ignoreCase = true)) {
                            onExpired()
                            qrPollJob?.cancel()
                            return@withTimeoutOrNull true
                        }
                        if (msg.contains("超时") || msg.contains("timeout", ignoreCase = true)) {
                            onTimeout()
                            qrPollJob?.cancel()
                            return@withTimeoutOrNull true
                        }
                    }
                    delay(QR_POLL_INTERVAL_MS)
                }
                true
            }
            if (completed != true && isActive) {
                onTimeout()
            }
        }
    }

    fun cancelQrPolling() {
        qrPollJob?.cancel()
    }

    fun clear() {
        qrPollJob?.cancel()
        qrPollJob = null
    }

    private companion object {
        const val QR_LOGIN_TIMEOUT_MS = 5 * 60 * 1000L
        const val QR_POLL_INTERVAL_MS = 2000L
    }
}
