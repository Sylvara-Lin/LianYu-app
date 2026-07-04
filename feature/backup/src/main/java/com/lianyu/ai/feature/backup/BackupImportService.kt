package com.lianyu.ai.feature.backup

import android.content.Context
import androidx.room.withTransaction
import com.lianyu.ai.common.DeviceIdProvider
import com.lianyu.ai.database.AppDatabase
import com.lianyu.ai.database.model.*
import com.lianyu.ai.database.repository.ChatMessageCrypto
import com.lianyu.ai.database.repository.MemoryCrypto
import com.lianyu.ai.feature.backup.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 数据导入服务 — 清空现有数据，将 [BackupData] 写入数据库并重新加密。
 *
 * 导入策略：替换模式 — 先清空所有相关表，再按序插入。
 * 清空顺序（满足 FK 约束）：group_messages → chat_messages → memory_entries → temp_memory → token_usage → chat_groups → companions
 * 插入顺序（满足 FK 约束）：companions → chat_groups → chat_messages → group_messages → memory_entries → temp_memory → token_usage
 */
class BackupImportService(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val deviceId = DeviceIdProvider.getDeviceId(context)

    suspend fun import(data: BackupData): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // [R3 FIX] 整个清空+插入序列包在单一 Room 事务里：
            // 原实现中途失败会留下半清半填的库，无回滚。事务保证原子性——要么全成功，要么全回滚。
            db.withTransaction {
                // 1. 清空（先删子表，再删父表）
                db.groupMessageDao().let { dao ->
                    data.chatGroups.forEach { dao.deleteMessagesForGroup(it.id) }
                }
                db.chatMessageDao().let { dao ->
                    data.companions.forEach { dao.deleteMessagesForCompanion(it.id) }
                }
                // 兜底清空（处理不在导入数据中的残留记录）
                db.memoryDao().deleteAllMemories(deviceId)
                db.memoryDao().deleteAllTempMemories(deviceId)
                db.tokenUsageDao().deleteAll(deviceId)
                db.groupMessageDao().let { dao ->
                    db.chatGroupDao().getAllGroupsSync().forEach { dao.deleteMessagesForGroup(it.id) }
                }
                db.chatMessageDao().let { dao ->
                    db.companionDao().getAllCompanionsSync().forEach { dao.deleteMessagesForCompanion(it.id) }
                }
                db.chatGroupDao().getAllGroupsSync().forEach { db.chatGroupDao().deleteGroup(it) }
                db.companionDao().getAllCompanionsSync().forEach { db.companionDao().deleteCompanion(it) }

                // 2. 插入（先插父表，再插子表）
                data.companions.forEach { s ->
                    db.companionDao().insertCompanion(
                        CompanionEntity(
                            id = s.id, name = s.name, avatarUrl = s.avatarUrl, age = s.age,
                            personality = s.personality, backstory = s.backstory,
                            speakingStyle = s.speakingStyle, tags = s.tags,
                            rawPrompt = s.rawPrompt, systemPrompt = s.systemPrompt,
                            intimacy = s.intimacy, createdAt = s.createdAt,
                            updatedAt = s.updatedAt
                        )
                    )
                }

                data.chatGroups.forEach { s ->
                    db.chatGroupDao().insertGroup(
                        ChatGroup(
                            id = s.id, name = s.name, avatarUrl = s.avatarUrl,
                            companionIds = s.companionIds, createdAt = s.createdAt,
                            updatedAt = s.updatedAt
                        )
                    )
                }

                data.chatMessages.forEach { s ->
                    val msg = ChatMessage(
                        id = s.id, companionId = s.companionId, content = s.content,
                        isFromUser = s.isFromUser, timestamp = s.timestamp,
                        type = safeEnum<MessageType>(s.type),
                        searchContent = s.searchContent.ifEmpty { s.content },
                        fileFormat = safeEnum<FileFormat>(s.fileFormat),
                        linkString = s.linkString
                    )
                    db.chatMessageDao().insertMessage(ChatMessageCrypto.encryptForStorage(msg))
                }

                data.groupMessages.forEach { s ->
                    val msg = GroupMessage(
                        id = s.id, groupId = s.groupId, companionId = s.companionId,
                        content = s.content, timestamp = s.timestamp,
                        searchContent = s.searchContent.ifEmpty { s.content },
                        fileFormat = safeEnum<FileFormat>(s.fileFormat),
                        linkString = s.linkString
                    )
                    db.groupMessageDao().insertMessage(ChatMessageCrypto.encryptForStorage(msg))
                }

                data.memoryEntries.forEach { s ->
                    val encryptedContext = if (s.context.isNotBlank()) {
                        try { MemoryCrypto.encrypt(s.context) } catch (_: Exception) { s.context }
                    } else ""
                    db.memoryDao().insertMemory(
                        MemoryEntry(
                            id = s.id, companionId = s.companionId, content = s.content,
                            category = safeEnum<MemoryCategory>(s.category),
                            importance = s.importance, context = encryptedContext,
                            accessCount = s.accessCount, timestamp = s.timestamp,
                            lastAccessed = s.lastAccessed, deviceId = deviceId
                        )
                    )
                }

                data.tempMemories.forEach { s ->
                    db.memoryDao().insertTempMemory(
                        TempMemory(
                            id = s.id, companionId = s.companionId,
                            userInput = s.userInput, botResponse = s.botResponse,
                            timestamp = s.timestamp, deviceId = deviceId
                        )
                    )
                }

                data.tokenUsages.forEach { s ->
                    db.tokenUsageDao().insert(
                        TokenUsage(
                            id = s.id, companionId = s.companionId, date = s.date,
                            inputTokens = s.inputTokens, outputTokens = s.outputTokens,
                            totalTokens = s.totalTokens, requestCount = s.requestCount,
                            timestamp = s.timestamp, deviceId = deviceId
                        )
                    )
                }
            }
        }
    }

    /** 安全枚举解析：无效值回退到默认 */
    private inline fun <reified T : Enum<T>> safeEnum(name: String): T {
        return try { enumValueOf<T>(name) }
        catch (_: Exception) { enumValues<T>().first() }
    }
}
