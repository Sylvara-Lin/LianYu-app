package com.lianyu.ai.feature.memory.engine

import kotlinx.serialization.Serializable

@Serializable
enum class MemoryCategory { FACT, EMOTION, PREFERENCE, EVENT, HABIT, RELATIONSHIP }

@Serializable
enum class MemorySource { CHAT, GROUP_CHAT, MANUAL }

@Serializable
enum class MemoryTier { SHORT, MID, LONG }

@Serializable
enum class MemoryScope { GLOBAL, COMPANION, GROUP }

/**
 * 记忆条目数据模型
 * 支持全局/角色/群聊三种作用域，短期/中期/长期三层存储
 */
@Serializable
data class MemoryItem(
    val id: String,
    val content: String,
    val category: MemoryCategory,
    val importance: Float,
    val timestamp: Long,
    val lastAccessed: Long,
    val accessCount: Int = 1,
    val source: MemorySource,
    val sourceId: Long,
    val scope: MemoryScope,
    val tags: List<String> = emptyList(),
    val expireAt: Long? = null,
    val tier: MemoryTier = MemoryTier.SHORT
) {
    /**
     * 判断记忆是否过期
     */
    fun isExpired(now: Long): Boolean {
        return expireAt != null && now > expireAt
    }

    /**
     * 判断是否应晋级到长期记忆
     * 条件：重要度>=0.8 且 访问次数>=3
     */
    fun shouldPromoteToLong(): Boolean {
        return importance >= 0.8f && accessCount >= 3
    }

    /**
     * 判断是否应从中期降级
     * 条件：7天未访问且重要度<0.5
     */
    fun shouldDemoteFromMid(now: Long): Boolean {
        val sevenDays = 7L * 24 * 60 * 60 * 1000
        return (now - lastAccessed) > sevenDays && importance < 0.5f
    }

    /**
     * 访问后更新访问信息
     */
    fun touch(now: Long): MemoryItem {
        return copy(lastAccessed = now, accessCount = accessCount + 1)
    }
}
