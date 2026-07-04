package com.lianyu.ai.feature.wechat.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lianyu.ai.feature.wechat.data.WeChatTokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WeChatBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // goAsync 延长 BroadcastReceiver 生命周期，避免协程挂起后 pendingResult 失效
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val tokenStore = WeChatTokenStore(context.applicationContext)
                if (tokenStore.isLoggedIn()) {
                    WeChatPollingService.start(context)
                }
            } catch (_: Exception) {
                // 忽略异常，确保 finish 被调用
            } finally {
                try { pendingResult.finish() } catch (_: Exception) {}
            }
        }
    }
}
