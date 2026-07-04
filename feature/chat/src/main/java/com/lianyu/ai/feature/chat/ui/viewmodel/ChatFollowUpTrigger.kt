package com.lianyu.ai.feature.chat.ui.viewmodel

import com.lianyu.ai.common.ContentFilter
import com.lianyu.ai.common.SecureLog
import com.lianyu.ai.database.model.ChatMessage
import com.lianyu.ai.database.repository.ChatRepository
import com.lianyu.ai.database.repository.filterDecrypted
import com.lianyu.ai.domain.AiServiceProvider
import com.lianyu.ai.domain.AiCompanionInfo
import com.lianyu.ai.domain.AiChatMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Follow-up question trigger — after AI replies, probabilistically sends a follow-up
 * question to keep the conversation going.
 *
 * Extracted from ChatViewModel to reduce class size.
 */
internal object ChatFollowUpTrigger {

    private val QUESTION_REGEX = Regex("[?？]|吗|呢|什么|怎么|为什么|多少|哪|谁|几|是不是|有没有|能不能|会不会|要不要|好不好")

    /**
     * Trigger a follow-up question if conditions are met:
     * 1) Settings allow follow-up messages
     * 2) AI reply doesn't already contain a question
     * 3) 50% probability
     *
     * @param scope Coroutine scope to launch the follow-up in (application-level).
     * @param aiContent The AI's reply content.
     * @param allowFollowUp Whether follow-up is enabled in settings.
     * @param companionId The companion ID.
     * @param companion The companion info for prompt building.
     * @param chatRepository Repository for fetching history and saving messages.
     * @param aiService AI service for generating the follow-up question.
     * @param broadcastCallback Callback to broadcast the follow-up message to WeChat.
     */
    fun triggerFollowUpIfNeeded(
        scope: CoroutineScope,
        aiContent: String,
        allowFollowUp: Boolean,
        companionId: Long,
        companion: AiCompanionInfo,
        chatRepository: ChatRepository,
        aiService: AiServiceProvider,
        broadcastCallback: (Long, String) -> Unit
    ) {
        if (!allowFollowUp) return
        if (QUESTION_REGEX.containsMatchIn(aiContent)) return
        if (kotlin.random.Random.nextFloat() > 0.5f) return

        scope.launch {
            try {
                delay(2000L + kotlin.random.Random.nextLong(3000L))

                val history = chatRepository.getRecentMessagesSync(companionId, 10).filterDecrypted()
                val followUp = aiService.generateFollowUpQuestion(
                    companion, history.toAiChatMessages(), aiContent
                ) ?: return@launch

                // 追问消息安全检查
                val followUpSafety = ContentFilter.checkOutputSafety(followUp)
                if (!followUpSafety.isSafe) {
                    SecureLog.w("ChatViewModel", "Follow-up safety violation: ${followUpSafety.reason}")
                    return@launch
                }

                val followUpMsg = ChatMessage(
                    companionId = companionId,
                    content = followUp,
                    isFromUser = false,
                    timestamp = System.currentTimeMillis()
                )
                val msgId = chatRepository.sendMessageAndGetId(followUpMsg)
                broadcastCallback(msgId, followUp)
                SecureLog.d("ChatViewModel", "Follow-up question sent: $followUp")
            } catch (e: Exception) {
                SecureLog.w("ChatViewModel", "Follow-up question failed: ${e.message}")
            }
        }
    }
}
