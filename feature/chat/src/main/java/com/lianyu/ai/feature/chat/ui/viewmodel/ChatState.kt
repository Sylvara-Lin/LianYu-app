package com.lianyu.ai.feature.chat.ui.viewmodel

import androidx.compose.runtime.Stable
import com.lianyu.ai.database.model.ChatMessage
import com.lianyu.ai.database.model.CompanionEntity
import com.lianyu.ai.uicommon.model.ApiProviderInfo

/**
 * ChatViewModel 打包状态 — 统一替换 25 个零散 StateFlow。
 *
 * 控制论: 单点状态降低扰动传播链，combine() 保证一致性。
 */
@Stable
data class ChatState(
    val companionData: CompanionEntity? = null,
    val messages: List<ChatMessage> = emptyList(),
    val visibleMessages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val isTyping: Boolean = false,
    val typingText: String = "",
    val isRegenerating: Boolean = false,
    val reasoningText: String = "",
    val isReasoning: Boolean = false,
    val currentApi: ApiProviderInfo? = null,
    val availableApis: List<ApiProviderInfo> = emptyList(),
    val userName: String = "我",
    val userAvatar: String? = null,
    val queueDepth: Int = 0,
    val hasMoreMessages: Boolean = false,
    val isLoadingMore: Boolean = false,
    val languageWarning: String? = null,
    /** 流水线阶段: IDLE → VALIDATE → CLASSIFY → ENCRYPT → SEND → CONFIRM → DONE */
    val pipelineStage: String = "IDLE",
    /** 流水线错误信息 */
    val pipelineError: String? = null
)
