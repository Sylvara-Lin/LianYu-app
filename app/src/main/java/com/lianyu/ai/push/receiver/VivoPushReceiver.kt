package com.lianyu.ai.push.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lianyu.ai.common.SecureLog
import com.lianyu.ai.push.dispatch.PushMessageDispatcher

/**
 * vivo 推送消息接收器（兼容实现）。
 *
 * 未引入 vivo Push SDK aar 时，作为普通 BroadcastReceiver 接收系统级 Push 广播；
 * 引入 aar 后应改回继承 `com.vivo.push.sdk.OpenClientPushMessageReceiver`，
 * 并重写 onReceiveRegId / onTransmissionMessage 等方法以获得完整回调。
 */
class VivoPushReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "VivoPush"

        // vivo Push 常见 intent extra 键名（按官方文档与实测汇总）
        private val CONTENT_KEYS = listOf("content", "msg", "message", "payload", "data")
        private val REG_ID_KEYS = listOf("regId", "reg_id", "clientId", "token")
        private val TITLE_KEYS = listOf("title", "notificationTitle", "notifyTitle")
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        intent ?: return

        val action = intent.action
        SecureLog.d(TAG, "onReceive action=$action")

        // 1. 解析并持久化 regId
        val regId = findFirstStringExtra(intent, REG_ID_KEYS)
        if (!regId.isNullOrBlank()) {
            PushMessageDispatcher.onTokenReceived(context, "vivo", regId)
        }

        // 2. 解析透传 / 通知内容
        val title = findFirstStringExtra(intent, TITLE_KEYS)
        val content = findFirstStringExtra(intent, CONTENT_KEYS)
        if (!content.isNullOrBlank()) {
            PushMessageDispatcher.onMessageReceived(context, title, content, intent.extras?.toPayloadMap())
        }
    }

    private fun findFirstStringExtra(intent: Intent, keys: List<String>): String? {
        for (key in keys) {
            intent.getStringExtra(key)?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return null
    }

    private fun android.os.Bundle?.toPayloadMap(): Map<String, String>? {
        this ?: return null
        return keySet()
            .filterNotNull()
            .mapNotNull { key ->
                getString(key)?.takeIf { it.isNotBlank() }?.let { key to it }
            }
            .toMap()
            .takeIf { it.isNotEmpty() }
    }
}
