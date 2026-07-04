package com.lianyu.ai.push.service

import android.app.Service
import android.content.Intent
import android.os.Bundle
import com.lianyu.ai.common.SecureLog
import com.lianyu.ai.push.dispatch.PushMessageDispatcher

/**
 * OPPO 推送透传消息服务（兼容实现）。
 *
 * OPPO Push SDK 会通过 Intent 将透传消息投递到本 Service。
 * 这里解析 Intent 中常见的 payload / message / content 等 extra，
 * 统一分发给本地通知系统。
 */
class OppoPushService : Service() {

    companion object {
        private const val TAG = "OppoPush"

        private val CONTENT_KEYS = listOf("payload", "message", "msg", "content", "data")
        private val TITLE_KEYS = listOf("title", "notificationTitle", "notifyTitle")
    }

    override fun onCreate() {
        super.onCreate()
        SecureLog.d(TAG, "OppoPushService created")
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
