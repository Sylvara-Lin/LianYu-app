package com.lianyu.ai.feature.backup

import android.content.Context
import com.lianyu.ai.common.DeviceIdProvider
import com.lianyu.ai.database.AppDatabase
import com.lianyu.ai.database.model.ChatMessage
import com.lianyu.ai.database.model.GroupMessage
import com.lianyu.ai.database.repository.ChatMessageCrypto
import com.lianyu.ai.database.repository.MemoryCrypto
import com.lianyu.ai.feature.backup.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 数据导出服务 — 读取全部用户数据，解密后组装为 [BackupData]。
 */
class BackupExportService(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val deviceId = DeviceIdProvider.getDeviceId(context)

    suspend fun export(): BackupData = withContext(Dispatchers.IO) {
        val companions = db.companionDao().getAllCompanionsSync().map { it.toSnapshot() }
        val chatMessages = mutableListOf<ChatMessageSnapshot>()
        val chatGroups = db.chatGroupDao().getAllGroupsSync().map { it.toSnapshot() }
        val groupMessages = mutableListOf<GroupMessageSnapshot>()
        val memoryEntries = db.memoryDao().getAllMemoriesSync(deviceId).map { it.toDecryptedSnapshot() }
        val tempMemories = db.memoryDao().getAllTempMemoriesSync(deviceId).map { it.toSnapshot() }
        val tokenUsages = db.tokenUsageDao().getAllUsageSync(deviceId).map { it.toSnapshot() }

        // 读取每条 companion 的聊天消息（已解密）
        for (c in companions) {
            val raw = db.chatMessageDao().getMessagesForCompanionSync(c.id)
            chatMessages.addAll(raw.map { ChatMessageCrypto.decryptFromStorage(it).toSnapshot() })
        }

        // 读取每个 group 的群聊消息（已解密）
        for (g in chatGroups) {
            val raw = db.groupMessageDao().getMessagesForGroupSync(g.id)
            groupMessages.addAll(raw.map { ChatMessageCrypto.decryptFromStorage(it).toSnapshot() })
        }

        BackupData(
            exportedAt = System.currentTimeMillis(),
            appVersion = context.packageManager
                .getPackageInfo(context.packageName, 0).versionName ?: "unknown",
            companions = companions,
            chatMessages = chatMessages,
            chatGroups = chatGroups,
            groupMessages = groupMessages,
            memoryEntries = memoryEntries,
            tempMemories = tempMemories,
            tokenUsages = tokenUsages
        )
    }
}

// --- Entity → Snapshot 映射扩展 ---

private fun com.lianyu.ai.database.model.CompanionEntity.toSnapshot() = CompanionSnapshot(
    id = id, name = name, avatarUrl = avatarUrl, age = age,
    personality = personality, backstory = backstory, speakingStyle = speakingStyle,
    tags = tags, rawPrompt = rawPrompt, systemPrompt = systemPrompt,
    intimacy = intimacy, createdAt = createdAt, updatedAt = updatedAt
)

private fun ChatMessage.toSnapshot() = ChatMessageSnapshot(
    id = id, companionId = companionId, content = content, isFromUser = isFromUser,
    timestamp = timestamp, type = type.name, searchContent = searchContent,
    fileFormat = fileFormat.name, linkString = linkString
)

private fun com.lianyu.ai.database.model.ChatGroup.toSnapshot() = ChatGroupSnapshot(
    id = id, name = name, avatarUrl = avatarUrl, companionIds = companionIds,
    createdAt = createdAt, updatedAt = updatedAt
)

private fun GroupMessage.toSnapshot() = GroupMessageSnapshot(
    id = id, groupId = groupId, companionId = companionId, content = content,
    timestamp = timestamp, searchContent = searchContent,
    fileFormat = fileFormat.name, linkString = linkString
)

private fun com.lianyu.ai.database.model.MemoryEntry.toDecryptedSnapshot(): MemoryEntrySnapshot {
    val decryptedContext = try {
        MemoryCrypto.decrypt(context)
    } catch (_: Exception) {
        context
    }
    return MemoryEntrySnapshot(
        id = id, companionId = companionId, content = content, category = category.name,
        importance = importance, context = decryptedContext, accessCount = accessCount,
        timestamp = timestamp, lastAccessed = lastAccessed, deviceId = deviceId
    )
}

private fun com.lianyu.ai.database.model.TempMemory.toSnapshot() = TempMemorySnapshot(
    id = id, companionId = companionId, userInput = userInput, botResponse = botResponse,
    timestamp = timestamp, deviceId = deviceId
)

private fun com.lianyu.ai.database.model.TokenUsage.toSnapshot() = TokenUsageSnapshot(
    id = id, companionId = companionId, date = date, inputTokens = inputTokens,
    outputTokens = outputTokens, totalTokens = totalTokens, requestCount = requestCount,
    timestamp = timestamp, deviceId = deviceId
)
