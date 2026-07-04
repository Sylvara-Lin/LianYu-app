package com.lianyu.ai.feature.wechat.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lianyu.ai.feature.wechat.data.WeChatChatBridge
import com.lianyu.ai.feature.wechat.data.WeChatMessageRepository
import com.lianyu.ai.feature.wechat.data.WeChatTokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 后台处理微信消息并触发 AI 回复的 Worker。
 *
 * 当 App 不在前台时，收到微信消息会 enqueue 此 Worker，
 * 在后台完成 AI 生成并发送回复到微信。
 */
class WeChatAiReplyWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val wechatUserId = inputData.getString(KEY_WECHAT_USER_ID) ?: ""
            val messageText = inputData.getString(KEY_MESSAGE_TEXT) ?: ""

            if (wechatUserId.isBlank() || messageText.isBlank()) {
                return@withContext Result.failure()
            }

            val tokenStore = WeChatTokenStore(applicationContext)

            // 检查自动回复是否开启
            if (!tokenStore.getAutoReply()) {
                return@withContext Result.success()
            }

            val repository = WeChatServiceLocator.messageRepository(applicationContext)
            val bridge = WeChatChatBridge(applicationContext, repository)

            try {
                bridge.handleTextMessage(wechatUserId, messageText)
            } finally {
                bridge.close()
            }

            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("WeChatAiReplyWorker", "Error processing message", e)
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME_PREFIX = "wechat_ai_reply_"
        const val KEY_WECHAT_USER_ID = "wechat_user_id"
        const val KEY_MESSAGE_TEXT = "message_text"

        private val networkConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun enqueue(context: Context, wechatUserId: String, messageText: String) {
            val inputData = Data.Builder()
                .putString(KEY_WECHAT_USER_ID, wechatUserId)
                .putString(KEY_MESSAGE_TEXT, messageText)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<WeChatAiReplyWorker>()
                .setInputData(inputData)
                .setConstraints(networkConstraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "$WORK_NAME_PREFIX$wechatUserId",
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                workRequest
            )
        }
    }
}
