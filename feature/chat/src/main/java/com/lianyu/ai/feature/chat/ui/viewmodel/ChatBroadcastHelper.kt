package com.lianyu.ai.feature.chat.ui.viewmodel

import android.content.Intent
import com.lianyu.ai.common.SecureLog
import com.lianyu.ai.common.wechat.WeChatBroadcast

/**
 * WeChat broadcast helper — sends proactive AI messages to the WeChat bridge.
 *
 * Extracted from ChatViewModel to reduce class size.
 * Takes Application context as parameter to remain stateless.
 */
internal object ChatBroadcastHelper {

    /**
     * Broadcast a WeChat proactive message for the given companion and message ID.
     *
     * @param application The Android Application context.
     * @param companionId The companion ID for this chat.
     * @param messageId The database ID of the message to broadcast.
     * @param finalContent Optional final text content to include in the broadcast.
     */
    fun broadcastWeChatMessage(
        application: android.app.Application,
        companionId: Long,
        messageId: Long,
        finalContent: String? = null
    ) {
        val intent = Intent(WeChatBroadcast.ACTION_SEND_PROACTIVE)
            .setPackage(application.packageName)
            .putExtra(WeChatBroadcast.EXTRA_COMPANION_ID, companionId)
            .putExtra(WeChatBroadcast.EXTRA_MESSAGE_ID, messageId)
        if (!finalContent.isNullOrBlank()) {
            intent.putExtra(WeChatBroadcast.EXTRA_FINAL_CONTENT, finalContent)
        }
        application.applicationContext.sendBroadcast(intent)
        SecureLog.d("ChatViewModel", "Broadcast WeChat proactive message, companionId=$companionId, messageId=$messageId, hasFinalContent=${!finalContent.isNullOrBlank()}")
    }

    /**
     * Broadcast AI message to WeChat, skipping empty or zero-width-space content.
     */
    fun broadcastAiMessage(
        application: android.app.Application,
        companionId: Long,
        messageId: Long,
        finalContent: String
    ) {
        if (finalContent.isNotBlank() && finalContent != "\u200B") {
            broadcastWeChatMessage(application, companionId, messageId, finalContent)
        }
    }
}
