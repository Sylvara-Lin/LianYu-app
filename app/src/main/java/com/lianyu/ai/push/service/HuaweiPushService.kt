package com.lianyu.ai.push.service

import com.huawei.hms.push.HmsMessageService
import com.huawei.hms.push.RemoteMessage
import com.lianyu.ai.common.SecureLog
import com.lianyu.ai.push.dispatch.PushMessageDispatcher

/**
 * 华为 HMS 推送服务。
 */
class HuaweiPushService : HmsMessageService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        SecureLog.d("HuaweiPush", "onNewToken")
        PushMessageDispatcher.onTokenReceived(this, "huawei", token)
    }

    override fun onMessageReceived(message: RemoteMessage?) {
        super.onMessageReceived(message)
        message ?: return
        val title = message.notification?.title
        val body = message.notification?.body
        val data = message.dataOfMap
        PushMessageDispatcher.onMessageReceived(this, title, body, data)
    }
}
