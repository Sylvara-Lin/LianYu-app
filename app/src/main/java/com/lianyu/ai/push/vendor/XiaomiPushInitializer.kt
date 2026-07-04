package com.lianyu.ai.push.vendor

import android.app.Application
import com.lianyu.ai.common.SecureLog
import com.lianyu.ai.push.PushConfig

/**
 * 小米 PUSH 初始化器。
 *
 * 请先从小米开放平台下载 MiPush SDK aar 放置到 app/libs，
 * 并在 app/build.gradle.kts 中取消 `implementation(files("libs/xiaomi-push-xxx.aar"))` 的注释。
 * 然后创建 com.lianyu.ai.push.receiver.XiaomiPushReceiver 继承 com.xiaomi.mipush.sdk.PushMessageReceiver。
 */
object XiaomiPushInitializer {

    private const val TAG = "XiaomiPush"

    fun register(application: Application) {
        if (PushConfig.XIAOMI_APP_ID.isBlank() || PushConfig.XIAOMI_APP_KEY.isBlank()) {
            SecureLog.w(TAG, "Xiaomi push keys not configured, skip")
            return
        }

        try {
            val clazz = Class.forName("com.xiaomi.mipush.sdk.MiPushClient")
            clazz.getMethod(
                "registerPush",
                android.content.Context::class.java,
                String::class.java,
                String::class.java
            ).invoke(null, application, PushConfig.XIAOMI_APP_ID, PushConfig.XIAOMI_APP_KEY)
            SecureLog.d(TAG, "Xiaomi push registerPush called")
        } catch (e: ClassNotFoundException) {
            SecureLog.w(TAG, "MiPush SDK not found, please add aar to app/libs")
        } catch (e: Exception) {
            SecureLog.e(TAG, "Failed to register Xiaomi push", e)
        }
    }
}
