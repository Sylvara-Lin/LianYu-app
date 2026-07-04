package com.lianyu.ai.push.service

import android.app.Service
import android.content.Intent
import android.os.Bundle
import com.lianyu.ai.common.SecureLog
import com.lianyu.ai.push.dispatch.PushMessageDispatcher

/**
 * OPPO 推送兼容服务（ColorOS 旧版本）。
 *
 * 与 [OppoPushService] 逻辑一致，仅用于接收旧版 ColorOS 的 action。
 */
class OppoPushServiceLegacy : Service() {

    companion object {
        private const val TAG = "OppoPushLegacy"

        private val CONTENT_KEYS = listOf("payload", "message", "msg", "content", "data")
        private val TITLE_KEYS = listOf("title", "notificationTitle", "notifyTitle")
    }

    override fun onCreate() {
        super.onCreate()
        SecureLog.d(TAG, "OppoPushServiceLegacy created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent ?: return super.onStartCommand(intent, flags, startId)

        SecureLog.d(TAG, "onStartCommand action=${intent.action}")

        val title = findFirstStringExtra(intent, TITLE_KEYS)
        val content = findFirstStringExtra(intent, CONTENT_KEYS)
        if (!content.isNullOrBlank()) {
            PushMessageDispatcher.onMessageReceived(this, title, content, intent.extras?.toPayloadMap())
        }

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent?) = null

    private fun findFirstStringExtra(intent: Intent, keys: List<String>): String? {
        for (key in keys) {
            intent.getStringExtra(key)?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return null
    }

    private fun Bundle?.toPayloadMap(): Map<String, String>? {
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
