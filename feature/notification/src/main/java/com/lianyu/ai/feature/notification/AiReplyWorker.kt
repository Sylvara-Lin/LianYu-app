package com.lianyu.ai.feature.notification

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lianyu.ai.common.AppForegroundTracker
import com.lianyu.ai.common.DeviceIdProvider
import com.lianyu.ai.database.AppDatabase
import com.lianyu.ai.database.model.ChatMessage
import com.lianyu.ai.database.model.MessageType
import com.lianyu.ai.database.repository.ChatRepository
import com.lianyu.ai.database.repository.CompanionRepository
import com.lianyu.ai.database.repository.MemoryRepository
import com.lianyu.ai.database.repository.ChatMessageCrypto
import com.lianyu.ai.database.repository.filterDecrypted
import com.lianyu.ai.domain.AiChatMessage
import com.lianyu.ai.domain.AiCompanionInfo
import com.lianyu.ai.domain.AiMessageType
import com.lianyu.ai.domain.AiServiceProvider
import com.lianyu.ai.domain.ServiceRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AiReplyWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val aiServiceProvider: AiServiceProvider by lazy {
        ServiceRegistry.get(AiServiceProvider::class.java)
            ?: throw IllegalStateException("AiServiceProvider not registered")
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val companionId = inputData.getLong(KEY_COMPANION_ID, -1L)
            val userMessageContent = inputData.getString(KEY_USER_MESSAGE) ?: ""

            if (companionId == -1L || userMessageContent.isBlank()) {
                return@withContext Result.failure()
            }

            // 封禁检查：已封禁用户不触发 AI 回复
            if (com.lianyu.ai.common.BanManager.isBanned(applicationContext)) {
                return@withContext Result.failure()
            }

            // 防御性输入安全检查
            val inputCheck = com.lianyu.ai.common.ContentFilter.checkInput(userMessageContent)
            if (inputCheck.isViolating) {
                com.lianyu.ai.common.BanManager.recordViolation(applicationContext, inputCheck.level)
                return@withContext Result.failure()
            }

            val database = AppDatabase.getDatabase(applicationContext)
            val companionRepository = CompanionRepository(database.companionDao())
            val chatRepository = ChatRepository(database.chatMessageDao())
            val memoryRepository = MemoryRepository(database.memoryDao(), DeviceIdProvider.getDeviceId(applicationContext))

            try {
                val companionModel = companionRepository.getCompanionById(companionId)
                if (companionModel == null) {
                    return@withContext Result.failure()
                }

                val history = chatRepository.getRecentMessagesSync(companionId, limit = 50)
                    .map { ChatMessageCrypto.decryptFromStorage(it) }
                    .filterDecrypted()

                val response = aiServiceProvider.sendMessage(
                    companionModel.toAiCompanionInfo(),
                    history.toAiChatMessages()
                )
                val trimmedResponse = response.content.trim()

                if (trimmedResponse.isNotEmpty()) {
                    // 安全检查：拦截 AI 违规输出
                    val outputSafety = com.lianyu.ai.common.ContentFilter.checkOutputSafety(trimmedResponse)
                    val safeResponse = if (!outputSafety.isSafe) {
                        android.util.Log.w("AiReplyWorker", "AI output blocked by safety filter: ${outputSafety.reason}")
                        com.lianyu.ai.common.BanManager.recordViolation(applicationContext, outputSafety.level)
                        "抱歉，我无法回应这个话题。"
                    } else {
                        trimmedResponse
                    }

                    val aiMessage = ChatMessage(
                        companionId = companionId,
                        content = safeResponse,
                        isFromUser = false
                    )
                    chatRepository.sendMessage(aiMessage)
                    companionRepository.updateTimestamp(companionId)
                    companionRepository.increaseIntimacy(companionId, 2)

                    memoryRepository.extractAndSaveMemories(companionId, userMessageContent, safeResponse)

                    if (!AppForegroundTracker.isInForeground) {
                        val notificationPreview = if (safeResponse.length > 50) {
                            safeResponse.take(50) + "..."
                        } else safeResponse
                        NotificationHelper.showCompanionMessageNotification(
                            applicationContext,
                            companionModel.name,
                            notificationPreview,
                            companionId
                        )
                    }
                }
            } finally {
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun com.lianyu.ai.database.model.CompanionEntity.toAiCompanionInfo() = AiCompanionInfo(
        id = id, name = name, personality = personality,
        age = age, backstory = backstory, speakingStyle = speakingStyle,
        systemPrompt = systemPrompt
    )

    private fun ChatMessage.toAiChatMessage() = AiChatMessage(
        isFromUser = isFromUser, content = content, timestamp = timestamp,
        type = when (type) {
            MessageType.IMAGE -> AiMessageType.IMAGE
            else -> AiMessageType.TEXT
        },
        companionId = companionId
    )

    private fun List<ChatMessage>.toAiChatMessages() = map { it.toAiChatMessage() }

    companion object {
        private const val WORK_NAME_PREFIX = "ai_reply_"
        const val KEY_COMPANION_ID = "companion_id"
        const val KEY_USER_MESSAGE = "user_message"

        private val networkConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun enqueue(context: Context, companionId: Long, userMessage: String) {
            val inputData = Data.Builder()
                .putLong(KEY_COMPANION_ID, companionId)
                .putString(KEY_USER_MESSAGE, userMessage)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<AiReplyWorker>()
                .setInputData(inputData)
                .setConstraints(networkConstraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "$WORK_NAME_PREFIX$companionId",
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                workRequest
            )
        }
    }
}
