package com.lianyu.ai.feature.wechat.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.lianyu.ai.common.TimeoutBudgets
import com.lianyu.ai.feature.wechat.R
import com.lianyu.ai.feature.wechat.data.WeChatMessageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 微信 ilink 长轮询前台服务。
 *
 * 职责：
 * 1. 登录后以 ForegroundService 形式保持运行，实时调用 pollMessages() 拉取微信消息
 * 2. 消息到达后由 Repository 内部处理（保存 contextToken、emit 到 flow、触发 AI 回复）
 * 3. WorkManager 每 15 分钟兜底轮询作为保活补充
 *
 * 启动时机：WeChatViewModel 登录成功后启动；注销时停止。
 */
open class WeChatPollingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null
    private var timedOut = false

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        if (pollJob == null || pollJob?.isActive != true) {
            startPolling()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTimeout(startId: Int) {
        super.onTimeout(startId)
        timedOut = true
        Log.w(TAG, "Foreground service timeout reached, scheduling restart and stopping gracefully")
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
            // ignore cleanup errors
        }
        stopSelf(startId)
        // Fallback to WorkManager polling immediately; service restart is handled
        // when the app next comes to foreground or on BOOT_COMPLETED.
        runCatching { WeChatPollingWorker.schedule(applicationContext) }
    }

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        pollJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startPolling() {
        pollJob = serviceScope.launch {
            val repository = WeChatServiceLocator.messageRepository(applicationContext)

            while (isActive) {
                if (!repository.isLoggedIn()) {
                    Log.d(TAG, "Not logged in, stop polling")
                    stopSelf()
                    return@launch
                }

                try {
                    Log.d(TAG, "Polling messages...")
                    // [P1 FIX] 与 Worker 统一为 WECHAT_POLL_TIMEOUT_MS（原 20000 与 Worker 15000 不一致）
                    val result = repository.pollMessages(timeoutMs = TimeoutBudgets.WECHAT_POLL_TIMEOUT_MS)
                    if (result.isFailure) {
                        val error = result.exceptionOrNull()
                        Log.w(TAG, "Poll failed: ${error?.message}")
                        val delayMs = when {
                            error?.message?.contains("timeout", ignoreCase = true) == true -> 3000L
                            error?.message?.contains("connection", ignoreCase = true) == true -> 5000L
                            else -> 5000L
                        }
                        delay(delayMs)
                    } else {
                        val messages = result.getOrNull()?.messages.orEmpty()
                        if (messages.isNotEmpty()) {
                            Log.d(TAG, "Received ${messages.size} messages")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Polling error", e)
                    delay(5000)
                }
            }
        }
    }

    private fun createNotification(): Notification {
        val channelId = CHANNEL_ID
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = CHANNEL_DESCRIPTION
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        } ?: Intent(Intent.ACTION_MAIN).apply {
            `package` = packageName
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("微信通道")
            .setContentText("正在实时接收微信消息...")
            .setSmallIcon(R.drawable.ic_wechat_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val TAG = "WeChatPollingService"
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "wechat_polling"
        private const val CHANNEL_NAME = "微信消息轮询"
        private const val CHANNEL_DESCRIPTION = "保持微信消息实时接收"

        fun start(context: Context) {
            val intent = Intent().setClassName(context.packageName, SHELL_SERVICE_CLASS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent().setClassName(context.packageName, SHELL_SERVICE_CLASS)
            context.stopService(intent)
        }

        private const val SHELL_SERVICE_CLASS = "com.lianyu.ai.feature.wechat.service.WeChatPollingService"
    }
}
