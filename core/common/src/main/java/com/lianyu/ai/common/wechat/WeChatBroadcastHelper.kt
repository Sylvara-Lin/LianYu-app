package com.lianyu.ai.common.wechat

import android.content.Context
import android.content.Intent

/**
 * WeChat 广播工具函数。
 *
 * 消除 ChatViewModel / GroupChatViewModel / CompanionMessageWorker 中
 * 重复的 broadcastWeChatMessage 实现。
 */
object WeChatBroadcastHelper {

    /**
     * 发送 WeChat 主动消息广播。
     *
     * @param context 应用上下文
     * @param companionId 伴侣/角色 ID
     * @param messageId 消息 ID
     * @param finalContent 可选最终内容（null 时接收者从 DB 读取）
     */
    fun broadcast(
        context: Context,
        companionId: Long,
        messageId: Long,
        finalContent: String? = null
    ) {
        val intent = Intent(WeChatBroadcast.ACTION_SEND_PROACTIVE)
            .setPackage(context.packageName)
            .putExtra(WeChatBroadcast.EXTRA_COMPANION_ID, companionId)
            .putExtra(WeChatBroadcast.EXTRA_MESSAGE_ID, messageId)
        if (!finalContent.isNullOrBlank()) {
            intent.putExtra(WeChatBroadcast.EXTRA_FINAL_CONTENT, finalContent)
        }
        context.applicationContext.sendBroadcast(intent)
    }
}
