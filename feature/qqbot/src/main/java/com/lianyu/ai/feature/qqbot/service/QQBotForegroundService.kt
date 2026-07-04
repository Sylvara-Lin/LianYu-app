package com.lianyu.ai.feature.qqbot.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * QQ Bot 前台服务。
 *
 * 负责在应用切到后台后维持 WebSocket 长连接，接收 QQ 消息事件。
 * Android 15 前台服务 6 小时超时后，需在 onTimeout 中重新调度。
 */
class QQBotForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        serviceScope.launch {
            try {
                val repository = QQBotServiceLocator.messageRepository(this@QQBotForegroundService)
                Log.i(TAG, "Connecting QQ Bot...")
                repository.connect()
                Log.i(TAG, "QQ Bot WebSocket connect invoked")
                // [FIX] 在 Service 作用域内启动自动回复，避免 ViewModel 被回收后无法回复
                val bridge = QQBotServiceLocator.chatBridge(this@QQBotForegroundService)
                bridge.start()
                Log.i(TAG, "QQ Bot chat bridge started")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect QQ Bot", e)
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        runCatching {
            QQBotServiceLocator.chatBridge(this).stop()
            QQBotServiceLocator.messageRepository(this).disconnect()
        }
        serviceScope.cancel()
    }

    override fun onTimeout(startId: Int) {
        super.onTimeout(startId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        stopSelf()
        start(this)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "QQ 机器人",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持 QQ 机器人消息通道在线"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("QQ 机器人运行中")
            .setContentText("正在接收 QQ 消息")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val TAG = "QQBotFgService"
        private const val CHANNEL_ID = "qqbot_foreground"
        private const val NOTIFICATION_ID = 0x7162

        fun start(context: Context) {
            val intent = Intent(context, QQBotForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, QQBotForegroundService::class.java))
        }
    }
}
