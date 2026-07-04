package com.lianyu.ai.feature.qqbot.data

import android.content.Context
import com.lianyu.ai.common.DeviceIdProvider
import com.lianyu.ai.database.AppDatabase
import com.lianyu.ai.database.model.ChatMessage
import com.lianyu.ai.database.model.MessageType
import com.lianyu.ai.database.repository.ChatRepository
import com.lianyu.ai.database.repository.CompanionRepository
import com.lianyu.ai.database.repository.MemoryRepository
import com.lianyu.ai.database.repository.filterDecrypted
import com.lianyu.ai.domain.AiChatMessage
import com.lianyu.ai.domain.AiCompanionInfo
import com.lianyu.ai.domain.AiMessageType
import com.lianyu.ai.domain.AiResponse
import com.lianyu.ai.domain.AiServiceProvider
import com.lianyu.ai.domain.ServiceRegistry
import com.lianyu.ai.feature.qqbot.data.model.QQInboundEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QQBotChatBridge(
    private val context: Context,
    private val qqBotRepository: QQBotMessageRepository,
    private val tokenStore: QQBotTokenStore
) {
    private val database = AppDatabase.getDatabase(context)
    private val deviceId = DeviceIdProvider.getDeviceId(context)
    private val chatRepository = ChatRepository(database.chatMessageDao())
    private val companionRepository = CompanionRepository(database.companionDao())
    private val memoryRepository = MemoryRepository(database.memoryDao(), deviceId)
    private val mappingManager = QQBotUserMappingManager(tokenStore, companionRepository)
    private val aiServiceProvider: AiServiceProvider by lazy {
        ServiceRegistry.get(AiServiceProvider::class.java)
            ?: throw IllegalStateException("AiServiceProvider not registered in ServiceRegistry")
    }
    private val bridgeJob = SupervisorJob()
    private val bridgeScope = CoroutineScope(bridgeJob + Dispatchers.IO)

    private var eventCollectionJob: kotlinx.coroutines.Job? = null

    /**
     * 在 Service 中启动持续的事件监听与自动回复。
     * 与 UI 生命周期解耦，保证切到后台后仍能自动回复。
     */
    fun start() {
        if (eventCollectionJob?.isActive == true) {
            android.util.Log.d("QQBotBridge", "Already started")
            return
        }
        android.util.Log.i("QQBotBridge", "Starting event collection")
        eventCollectionJob = bridgeScope.launch {
            qqBotRepository.incomingEvents.collect { event ->
                val autoReply = tokenStore.getAutoReply()
                val text = qqBotRepository.extractText(event)
                android.util.Log.d("QQBotBridge", "Event received, autoReply=$autoReply, text=$text")
                if (!autoReply) return@collect
                val key = qqBotRepository.getReplyKey(event)
                // 连发合并：如果同 key 已有活跃回复 job，取消旧 job，
                // 短暂延迟聚合后续消息（2 秒窗口），用最新事件发起回复。
                val existingJob = qqBotRepository.getActiveReplyJob(key)
                if (existingJob?.isActive == true) {
                    android.util.Log.d("QQBotBridge", "Cancelling active job for $key to merge new message")
                    existingJob.cancel()
                    qqBotRepository.removeActiveReplyJob(key)
                }
                // 等待 2 秒聚合窗口，收到连发消息时会取消当前 job 重新合并
                val currentEventText = text
                val job = bridgeScope.launch {
                    try {
                        delay(2000L) // 连发聚合窗口
                        handleIncomingEventStreaming(event)
                    } finally {
                        qqBotRepository.removeActiveReplyJob(key)
                    }
                }
                qqBotRepository.setActiveReplyJob(key, job)
            }
        }
    }

    fun stop() {
        eventCollectionJob?.cancel()
        eventCollectionJob = null
    }

    /**
     * 流式处理接入事件：使用 [AiServiceProvider.sendMessageStream] 按句分段即时发送，
     * 大幅降低用户感知的首字延迟。每收到完整句子或 ≥30 字立即发送到 QQ，
     * 段间最小间隔 500ms 防止 QQ API 频率限制。
     */
    suspend fun handleIncomingEventStreaming(event: QQInboundEvent) = withContext(Dispatchers.IO) {
        try {
            val qqUserId = when (event) {
                is QQInboundEvent.C2CMessage -> event.userOpenid
                is QQInboundEvent.GroupAtMessage -> "${event.groupOpenid}:${event.memberOpenid}"
                is QQInboundEvent.GuildMessage -> "${event.channelId}:${event.authorId}"
                is QQInboundEvent.DirectMessage -> "${event.guildId}:${event.authorId}"
            }
            android.util.Log.d("QQBotBridge", "Handling streaming event, qqUserId=$qqUserId")
            if (qqUserId.isBlank()) return@withContext

            val text = qqBotRepository.extractText(event)
            android.util.Log.d("QQBotBridge", "Extracted text: $text")
            if (text.isBlank()) return@withContext

            if (com.lianyu.ai.common.BanManager.isBanned(context)) {
                android.util.Log.w("QQBotBridge", "Banned, skip")
                return@withContext
            }

            val companionId = mappingManager.getOrCreateMapping(qqUserId) ?: run {
                android.util.Log.w("QQBotBridge", "No companion mapping for $qqUserId")
                return@withContext
            }
            android.util.Log.d("QQBotBridge", "Mapped to companionId=$companionId")
            val companion = companionRepository.getCompanionById(companionId) ?: run {
                android.util.Log.w("QQBotBridge", "Companion not found: $companionId")
                return@withContext
            }

            val filterResult = com.lianyu.ai.common.ContentFilter.checkInput(text)
            if (filterResult.isViolating) {
                android.util.Log.w("QQBotBridge", "Input blocked: ${filterResult.reason}")
                com.lianyu.ai.common.BanManager.recordViolation(context, filterResult.level)
                val blockedResponse = "抱歉，我无法处理这个话题。"
                sendReply(event, blockedResponse)
                persistBlockedMessage(companionId, blockedResponse)
                return@withContext
            }

            val userMessage = ChatMessage(
                companionId = companionId,
                content = text,
                isFromUser = true,
                timestamp = System.currentTimeMillis()
            )
            chatRepository.sendMessage(userMessage)
            companionRepository.updateTimestamp(companionId)

            val history = chatRepository.getRecentMessagesSync(companionId, limit = 30).filterDecrypted()
            android.util.Log.d("QQBotBridge", "Calling AI with ${history.size} history messages")

            // [R1 FIX] 改为非流式调用：原 sendMessageStream 违反「AI 输入/输出禁止流式」铁律，
            // 且流式分段路径完全没有 ContentFilter.checkOutputSafety 检查，不安全输出直接发到 QQ。
            // 现在全文接收 → 安全检查 → 分段发送。
            val response = try {
                aiServiceProvider.sendMessage(companion.toAiCompanionInfo(), history.toAiChatMessages(), 0)
            } catch (e: Exception) {
                android.util.Log.e("QQBotBridge", "sendMessage failed", e)
                null
            }

            if (response == null || response.content.isBlank()) {
                val fallback = "抱歉，我暂时无法处理这条消息。"
                sendReply(event, fallback)
                persistBlockedMessage(companionId, fallback)
                return@withContext
            }

            // [R1 FIX] 全文安全检查：在发送和入库前对完整 AI 输出做 ContentFilter 校验。
            val outputSafety = com.lianyu.ai.common.ContentFilter.checkOutputSafety(response.content)
            val safeText = if (!outputSafety.isSafe) {
                android.util.Log.w("QQBotBridge", "AI output blocked: ${outputSafety.reason}")
                "抱歉，我无法回应这个话题。"
            } else {
                response.content
            }

            // 按句子边界分段发送（频率保护），但入库存完整回复
            var lastSendTime = 0L
            val minGapMs = 500L
            val sentences = splitIntoSentences(safeText)
            for (sentence in sentences) {
                if (sentence.isBlank()) continue
                val elapsed = System.currentTimeMillis() - lastSendTime
                if (elapsed < minGapMs && lastSendTime > 0) {
                    delay(minGapMs - elapsed)
                }
                if (tokenStore.getForwardEnabled()) {
                    sendReply(event, sentence)
                }
                lastSendTime = System.currentTimeMillis()
            }

            // 入库完整 AI 回复
            val aiMessage = ChatMessage(
                companionId = companionId,
                content = safeText,
                isFromUser = false,
                timestamp = System.currentTimeMillis()
            )
            chatRepository.sendMessage(aiMessage)

            // 增加亲密度 + 异步提取记忆
            companionRepository.increaseIntimacy(companionId, 2)
            bridgeScope.launch {
                runCatching {
                    memoryRepository.extractAndSaveMemories(companionId, text, safeText)
                }
            }

        } catch (e: Exception) {
            android.util.Log.e("QQBotBridge", "Error handling incoming event", e)
        }
    }

    private suspend fun sendReply(event: QQInboundEvent, text: String) {
        val result = qqBotRepository.sendTextMessage(event, text)
        result.onFailure { e ->
            android.util.Log.e("QQBotBridge", "Failed to send QQ reply: ${e.message}", e)
        }
    }

    private suspend fun persistBlockedMessage(companionId: Long, text: String) {
        chatRepository.sendMessage(
            ChatMessage(
                companionId = companionId,
                content = text,
                isFromUser = false,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    private fun cleanReplyText(text: String): String {
        return text.trim()
            .replace(Regex("\\s+"), " ")
            .replace(Regex("^[\\[\\]\\s，。！？、]+"), "")
            .replace(Regex("[\\[\\]\\s，。！？、]+$"), "")
            .trim()
    }

    /**
     * [R1 FIX] 将完整 AI 回复按句子边界拆分为可分次发送的段落。
     * 替代原流式收集的分段逻辑——现在全文接收后再拆分，安全检查已对全文完成。
     */
    private fun splitIntoSentences(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val delimiters = charArrayOf('。', '！', '？', '!', '?', '\n')
        val result = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            val idx = text.indexOfAny(delimiters, startIndex = start)
            if (idx < 0) {
                val remaining = text.substring(start).trim()
                if (remaining.isNotEmpty()) result.add(remaining)
                break
            }
            val end = idx + 1
            val sentence = text.substring(start, end).trim()
            if (sentence.isNotEmpty()) result.add(sentence)
            start = end
        }
        return result
    }

    fun close() {
        bridgeJob.cancel()
    }

    private fun com.lianyu.ai.database.model.CompanionEntity.toAiCompanionInfo() = AiCompanionInfo(
        id = id, name = name, personality = personality,
        age = age, backstory = backstory, speakingStyle = speakingStyle,
        systemPrompt = systemPrompt
    )

    private fun com.lianyu.ai.database.model.ChatMessage.toAiChatMessage() = AiChatMessage(
        isFromUser = isFromUser, content = content, timestamp = timestamp,
        type = when (type) {
            MessageType.IMAGE -> AiMessageType.IMAGE
            else -> AiMessageType.TEXT
        },
        companionId = companionId
    )

    private fun List<com.lianyu.ai.database.model.ChatMessage>.toAiChatMessages() = map { it.toAiChatMessage() }
}
