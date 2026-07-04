package com.lianyu.ai.feature.wechat.data

data class WeChatIncomingMessageDecision(
    val shouldNotify: Boolean,
    val shouldAutoReply: Boolean
)

object WeChatIncomingMessagePolicy {
    fun evaluate(
        isAppInForeground: Boolean,
        notifyEnabled: Boolean,
        autoReplyEnabled: Boolean,
        messageText: String
    ): WeChatIncomingMessageDecision {
        return WeChatIncomingMessageDecision(
            shouldNotify = notifyEnabled && !isAppInForeground,
            shouldAutoReply = autoReplyEnabled && messageText.isNotBlank()
        )
    }
}
