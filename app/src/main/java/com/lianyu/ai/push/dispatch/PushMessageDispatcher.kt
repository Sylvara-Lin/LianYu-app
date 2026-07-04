package com.lianyu.ai.push.dispatch

import android.content.Context
import com.lianyu.ai.common.SecureLog
import com.lianyu.ai.feature.notification.NotificationHelper

/**
 * 厂商推送消息统一分发器。
 *
 * 负责把各厂商 SDK 收到的 token / 消息统一路由到本地通知系统，
 * 并持久化 token 供服务端推送时使用。
 */
object PushMessageDispatcher {

    private const val TAG = "PushDispatcher"
    private const val PREFS_NAME = "vendor_push_tokens"

    fun onTokenReceived(context: Context, vendor: String, token: String) {
        SecureLog.d(TAG, "Token received from $vendor")
        saveToken(context, vendor, token)
        // TODO: 将 token 上报到业务服务器，服务端按厂商通道下发消息
    }

    fun onMessageReceived(context: Context, title: String?, content: String?, payload: Map<String, String>?) {
        SecureLog.d(TAG, "Message from vendor: title=$title content=$content")
        val safeTitle = title?.takeIf { it.isNotBlank() } ?: "恋语"
        val safeContent = content?.takeIf { it.isNotBlank() } ?: "您有一条新消息"
        NotificationHelper.showCompanionMessageNotification(context, safeTitle, safeContent, companionId = 0L)
    }

    fun saveToken(context: Context, vendor: String, token: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("${vendor}_token", token)
            .apply()
    }

    fun getToken(context: Context, vendor: String): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("${vendor}_token", null)
    }
}
