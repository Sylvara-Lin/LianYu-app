package com.lianyu.ai.feature.chat.data

import com.lianyu.ai.common.AppSettingsStore
import com.lianyu.ai.common.ChatConstants
import com.lianyu.ai.common.SecureLog
import com.lianyu.ai.database.model.ChatMessage
import com.lianyu.ai.database.repository.ChatRepository
import com.lianyu.ai.database.repository.filterDecrypted

/**
 * 统一解析聊天上下文配置，解决 ViewModel/AiService 中上下文数值硬编码、
 * 与用户设置不一致的问题。
 *
 * - 读取 [AppSettingsStore] 中的 contextLimit / compressionMode
 * - 对 AI 侧历史消息获取数量做安全上限，防止长对话时 OOM
 * - 提供 decrypted history 的轻量 LRU 缓存，避免同一 companion 在连续多轮中重复解密
 */
class ChatContextResolver(
    private val appSettingsStore: AppSettingsStore,
    private val chatRepository: ChatRepository
) {

    companion object {
        // 数值常量已收敛到 ChatConstants，此处保留文档注释。
        /**
         * AI 上下文最多从数据库拉取的消息条数。
         * 用户设置可低于此值，但不会超过它，避免 400+ 句时长对话把全部历史解密进内存。
         */
        const val MAX_AI_CONTEXT_FETCH = ChatConstants.MAX_AI_CONTEXT_FETCH

        /**
         * UI 层内存中保留的最大消息条数（older + recent）。
         * 超过时丢弃最旧的历史，避免 LazyColumn + StateFlow 组合导致内存持续增长。
         */
        const val MAX_UI_MESSAGES = ChatConstants.MAX_UI_MESSAGES

        private const val CONTEXT_CACHE_SIZE = ChatConstants.CONTEXT_CACHE_SIZE
    }

    /** 缓存 key = companionId，value = 最近一次解析出的上下文（按 lastMessageId 失效） */
    private val contextCache = object : LinkedHashMap<Long, Pair<Long, List<ChatMessage>>>(
        CONTEXT_CACHE_SIZE, 0.75f, true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Pair<Long, List<ChatMessage>>>?): Boolean {
            return size > CONTEXT_CACHE_SIZE
        }
    }

    private val cacheLock = Any()

    /** 用户设置的原始上下文条数上限（1..10000） */
    suspend fun getContextLimit(): Int = appSettingsStore.getContextLimit()

    /** 实际用于数据库拉取的有效条数，受 [MAX_AI_CONTEXT_FETCH] 限制 */
    suspend fun getEffectiveFetchLimit(): Int {
        val configured = getContextLimit()
        return if (configured > MAX_AI_CONTEXT_FETCH) {
            SecureLog.w(
                "ChatContextResolver",
                "User contextLimit=$configured exceeds safe cap $MAX_AI_CONTEXT_FETCH, capping"
            )
            MAX_AI_CONTEXT_FETCH
        } else {
            configured.coerceAtLeast(1)
        }
    }

    /** 当前压缩模式（off / local / ai） */
    suspend fun getCompressionMode(): String = appSettingsStore.getContextCompressionMode()

    /**
     * 获取用于 AI 请求的历史消息。
     * 结果会按 lastMessageId 做短期缓存，连续多轮对话可减少重复解密。
     */
    suspend fun getHistoryForAi(companionId: Long): List<ChatMessage> {
        val limit = getEffectiveFetchLimit()
        val lastMessage = chatRepository.getRecentMessagesSync(companionId, 1).firstOrNull()
        val lastMessageId = lastMessage?.id ?: 0L

        synchronized(cacheLock) {
            val cached = contextCache[companionId]
            if (cached != null && cached.first == lastMessageId) {
                SecureLog.d("ChatContextResolver", "AI context cache hit for companion=$companionId")
                return cached.second
            }
        }

        SecureLog.d("ChatContextResolver", "Fetching AI context for companion=$companionId, limit=$limit")
        val history = chatRepository.getRecentMessagesSync(companionId, limit).filterDecrypted()
        synchronized(cacheLock) {
            contextCache[companionId] = lastMessageId to history
        }
        return history
    }

    /** 获取追问/重生成等辅助流程使用的少量最近历史 */
    suspend fun getShortHistoryForAi(companionId: Long, shortLimit: Int): List<ChatMessage> {
        val effectiveLimit = shortLimit.coerceAtMost(MAX_AI_CONTEXT_FETCH)
        return chatRepository.getRecentMessagesSync(companionId, effectiveLimit).filterDecrypted()
    }

    /** 清空指定 companion 的上下文缓存；退出聊天时调用 */
    fun clearCache(companionId: Long) {
        synchronized(cacheLock) {
            contextCache.remove(companionId)
        }
    }

    /** 清空全部上下文缓存；内存紧张时调用 */
    fun clearAllCache() {
        synchronized(cacheLock) {
            contextCache.clear()
        }
    }

    /**
     * 对 UI 消息列表做上限保护。
     * 返回裁剪后的列表（保留最近的 [MAX_UI_MESSAGES] 条），以及是否发生了裁剪。
     */
    fun capUiMessages(allMessages: List<ChatMessage>): Pair<List<ChatMessage>, Boolean> {
        if (allMessages.size <= MAX_UI_MESSAGES) return allMessages to false
        val dropped = allMessages.size - MAX_UI_MESSAGES
        SecureLog.w("ChatContextResolver", "UI messages capped: dropped $dropped oldest messages")
        return allMessages.takeLast(MAX_UI_MESSAGES) to true
    }
}
