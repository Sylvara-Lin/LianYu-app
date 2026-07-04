package com.lianyu.ai.feature.chat.ui.viewmodel

import com.lianyu.ai.database.model.ChatMessage
import com.lianyu.ai.database.model.CompanionEntity
import com.lianyu.ai.database.model.MessageType
import com.lianyu.ai.domain.AiChatMessage
import com.lianyu.ai.domain.AiCompanionInfo
import com.lianyu.ai.domain.AiMessageType

/**
 * Domain type conversion helpers — pure extension functions with no ViewModel dependencies.
 *
 * Extracted from ChatViewModel to reduce class size and improve testability.
 */
internal fun CompanionEntity.toAiCompanionInfo(): AiCompanionInfo = AiCompanionInfo(
    id = id, name = name, personality = personality,
    age = age, backstory = backstory, speakingStyle = speakingStyle,
    systemPrompt = systemPrompt
)

internal fun ChatMessage.toAiChatMessage(): AiChatMessage = AiChatMessage(
    isFromUser = isFromUser, content = content, timestamp = timestamp,
    type = when (type) {
        MessageType.IMAGE -> AiMessageType.IMAGE
        else -> AiMessageType.TEXT
    },
    companionId = companionId
)

internal fun List<ChatMessage>.toAiChatMessages(): List<AiChatMessage> = map { it.toAiChatMessage() }
