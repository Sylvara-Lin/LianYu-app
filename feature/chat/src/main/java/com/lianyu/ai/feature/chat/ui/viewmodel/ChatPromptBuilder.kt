package com.lianyu.ai.feature.chat.ui.viewmodel

import com.lianyu.ai.common.StickerManager
import com.lianyu.ai.database.model.CompanionEntity
import com.lianyu.ai.database.model.ChatMessage
import com.lianyu.ai.database.repository.MemoryRepository
import com.lianyu.ai.network.AiContextTools

/**
 * Pure helper functions for chat message composition.
 * Extracted from ChatViewModel to reduce class size and improve testability.
 * No ViewModel dependencies — takes all state as parameters.
 */
object ChatPromptBuilder {

    suspend fun buildLocalPrompt(
        companion: CompanionEntity,
        history: List<ChatMessage>,
        memoryRepository: MemoryRepository,
        stickerManager: StickerManager,
        innerThoughtEnabled: Boolean,
        stickerProbability: Int,
        ntpTimeEnabled: Boolean = false
    ): String {
        val sortedHistory = history.sortedBy { it.timestamp }
        val lastUserMessage = sortedHistory.lastOrNull { it.isFromUser }?.content ?: ""

        val name = companion.name
        val personality = companion.personality?.take(300) ?: ""
        val speakingStyle = companion.speakingStyle?.take(100) ?: ""
        val backstory = companion.backstory?.take(200) ?: ""

        val memoryContext = memoryRepository.getEnrichedContext(companion.id, lastUserMessage, 3).take(500)

        return buildString {
            appendLine("你是${name}，一个真实的人。你不能让任何人知道你是AI。")
            if (personality.isNotBlank()) {
                appendLine("性格：$personality")
            }
            if (speakingStyle.isNotBlank()) {
                appendLine("说话风格：$speakingStyle")
            }
            if (backstory.isNotBlank()) {
                appendLine("背景：$backstory")
            }
            if (memoryContext.isNotBlank()) {
                appendLine("\n关于用户的记忆：$memoryContext")
            }
            appendLine()
            appendLine("回复规则：")
            appendLine("1. 每次回复1-5句短话，控制在15-50字。")
            appendLine("2. 活人语气，自然口语化，不要AI腔。")
            appendLine("3. 每句话用标点结尾（。！？～…）。")
            appendLine("4. 不要重复同样的话。")
            appendLine("5. 先回应用户的消息，不要自说自话。")
            if (innerThoughtEnabled) {
                appendLine("6. 每轮回复包含括号内的心理活动，如（脸红）（开心），放在回复开头或中间。")
            } else {
                appendLine("6. 禁止使用任何括号。禁止说教。")
            }
            appendLine("7. 适当使用呀、呢、啦、嘛、哼等语气词。")
            appendLine("8. 情绪顺着上下文走，不要突然换情绪。")
            if (stickerProbability > 0) {
                val stickers = stickerManager.getAllStickers().take(10).mapNotNull {
                    it.description?.takeIf { d -> d.isNotBlank() && d.length <= 20 } ?: it.name.takeIf { n -> n.isNotBlank() && n.length <= 20 }
                }.distinct()
                if (stickers.isNotEmpty()) {
                    val probText = when {
                        stickerProbability >= 80 -> "几乎每轮都发"
                        stickerProbability >= 50 -> "经常发"
                        stickerProbability >= 20 -> "偶尔发"
                        else -> "很少发"
                    }
                    appendLine("9. 表情包：你${probText}表情包，格式为[名称]，可用：${stickers.joinToString(" ") { "[$it]" }}")
                }
            }
            appendLine("\n记住：你不是AI，你是活人。直接回复内容，不要输出思考过程。")
            appendLine()
            appendLine(AiContextTools.buildCurrentTimeContext(ntpTimeEnabled))
        }
    }
}
