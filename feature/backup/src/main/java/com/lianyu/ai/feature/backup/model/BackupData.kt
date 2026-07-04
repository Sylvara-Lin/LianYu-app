package com.lianyu.ai.feature.backup.model

import com.lianyu.ai.database.model.FileFormat
import com.lianyu.ai.database.model.MemoryCategory
import com.lianyu.ai.database.model.MessageType
import kotlinx.serialization.Serializable

/**
 * 导出/导入数据根容器。
 *
 * 所有加密字段在导出时已解密为明文，导入后由 [BackupImportService] 重新加密存储。
 *
 * @param version 格式版本号，用于向后兼容
 * @param exportedAt 导出时间戳 (epoch millis)
 * @param appVersion 导出来源的 app versionName
 * @param companions 陪聊对象
 * @param chatMessages 一对一聊天消息（已解密）
 * @param chatGroups 群聊定义
 * @param groupMessages 群聊消息（已解密）
 * @param memoryEntries 长期记忆（已解密）
 * @param tempMemories 短期记忆
 * @param tokenUsages Token 使用统计
 */
@Serializable
data class BackupData(
    val version: Int = 1,
    val exportedAt: Long,
    val appVersion: String,
    val companions: List<CompanionSnapshot>,
    val chatMessages: List<ChatMessageSnapshot>,
    val chatGroups: List<ChatGroupSnapshot>,
    val groupMessages: List<GroupMessageSnapshot>,
    val memoryEntries: List<MemoryEntrySnapshot>,
    val tempMemories: List<TempMemorySnapshot>,
    val tokenUsages: List<TokenUsageSnapshot>
)

@Serializable
data class CompanionSnapshot(
    val id: Long,
    val name: String,
    val avatarUrl: String? = null,
    val age: Int? = null,
    val personality: String,
    val backstory: String? = null,
    val speakingStyle: String? = null,
    val tags: String? = null,
    val rawPrompt: String? = null,
    val systemPrompt: String? = null,
    val intimacy: Int = 0,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class ChatMessageSnapshot(
    val id: Long,
    val companionId: Long,
    val content: String,
    val isFromUser: Boolean,
    val timestamp: Long,
    val type: String = "TEXT",
    val searchContent: String = "",
    val fileFormat: String = "TEXT",
    val linkString: String = ""
)

@Serializable
data class ChatGroupSnapshot(
    val id: Long,
    val name: String,
    val avatarUrl: String? = null,
    val companionIds: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class GroupMessageSnapshot(
    val id: Long,
    val groupId: Long,
    val companionId: Long,
    val content: String,
    val timestamp: Long,
    val searchContent: String = "",
    val fileFormat: String = "TEXT",
    val linkString: String = ""
)

@Serializable
data class MemoryEntrySnapshot(
    val id: Long,
    val companionId: Long,
    val content: String,
    val category: String = "FACT",
    val importance: Float = 0.5f,
    val context: String = "",
    val accessCount: Int = 1,
    val timestamp: Long,
    val lastAccessed: Long,
    val deviceId: String = ""
)

@Serializable
data class TempMemorySnapshot(
    val id: Long,
    val companionId: Long,
    val userInput: String,
    val botResponse: String,
    val timestamp: Long,
    val deviceId: String = ""
)

@Serializable
data class TokenUsageSnapshot(
    val id: Long,
    val companionId: Long,
    val date: String,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val totalTokens: Long = 0,
    val requestCount: Int = 0,
    val timestamp: Long,
    val deviceId: String = ""
)
