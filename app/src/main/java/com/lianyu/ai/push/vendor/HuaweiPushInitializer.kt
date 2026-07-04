package com.lianyu.ai.push.vendor

import android.app.Application
import com.huawei.hms.aaid.HmsInstanceId
import com.huawei.hms.common.ApiException
import com.huawei.hms.push.HmsMessaging
import com.lianyu.ai.common.SecureLog
import com.lianyu.ai.push.dispatch.PushMessageDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * 华为 HMS PUSH 初始化器。
 *
 * SDK 版本：6.12.0.300（Maven 依赖）
 */
object HuaweiPushInitializer {

    private const val TAG = "HuaweiPush"

    fun register(application: Application) {
        try {
            // 自动初始化，在 HmsMessageService.onNewToken 中接收 token
            HmsMessaging.getInstance(application).isAutoInitEnabled = true
            requestToken(application)
        } catch (e: Exception) {
            SecureLog.e(TAG, "Failed to register Huawei push", e)
        }
    }

    private fun requestToken(application: Application) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val token = HmsInstanceId.getInstance(application)
                    .getToken(application.packageName, HmsMessaging.DEFAULT_TOKEN_SCOPE)
                if (!token.isNullOrBlank()) {
                    SecureLog.d(TAG, "Huawei token obtained")
                    PushMessageDispatcher.onTokenReceived(application, "huawei", token)
                }
            } catch (e: ApiException) {
                SecureLog.e(TAG, "Huawei getToken failed: ${e.statusCode}", e)
            } catch (e: Exception) {
                SecureLog.e(TAG, "Huawei getToken error", e)
            }
        }
    }
}
