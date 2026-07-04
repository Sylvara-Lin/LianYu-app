package com.lianyu.ai.push.vendor

import android.app.Application
import com.lianyu.ai.common.SecureLog
import com.lianyu.ai.push.PushConfig
import com.lianyu.ai.push.dispatch.PushMessageDispatcher

/**
 * OPPO PUSH 初始化器。
 *
 * SDK 版本：3.0.0（本地 aar）
 */
object OppoPushInitializer {

    private const val TAG = "OppoPush"

    fun register(application: Application) {
        if (PushConfig.OPPO_APP_KEY.isBlank() || PushConfig.OPPO_APP_SECRET.isBlank()) {
            SecureLog.w(TAG, "OPPO push keys not configured, skip")
            return
        }

        try {
            val pushManagerClass = Class.forName("com.heytap.msp.push.PushManager")
            val callbackClass = Class.forName("com.heytap.msp.push.callback.ICallBackResultService")
            val instance = pushManagerClass.getMethod("getInstance").invoke(null)
            val proxy = java.lang.reflect.Proxy.newProxyInstance(
                callbackClass.classLoader,
                arrayOf(callbackClass)
            ) { _, method, args ->
                when (method.name) {
                    "onRegister" -> {
                        val code = (args?.getOrNull(0) as? Int) ?: -1
                        val regId = args?.getOrNull(1) as? String
                        SecureLog.d(TAG, "onRegister code=$code regId=$regId")
                        if (code == 0 && !regId.isNullOrBlank()) {
                            PushMessageDispatcher.onTokenReceived(application, "oppo", regId)
                        }
                    }
                    "onUnRegister", "onSetPushTime", "onGetPushStatus", "onGetNotificationStatus" -> {
                        SecureLog.d(TAG, "${method.name}")
                    }
                }
                null
            }
            pushManagerClass.getMethod(
                "register",
                Application::class.java,
                String::class.java,
                String::class.java,
                callbackClass
            ).invoke(instance, application, PushConfig.OPPO_APP_KEY, PushConfig.OPPO_APP_SECRET, proxy)
            SecureLog.d(TAG, "OPPO push register called")
        } catch (e: ClassNotFoundException) {
            SecureLog.w(TAG, "OPPO Push SDK not found, please add aar to app/libs")
        } catch (e: Exception) {
            SecureLog.e(TAG, "Failed to register OPPO push", e)
        }
    }
}
