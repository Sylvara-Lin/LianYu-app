package com.lianyu.ai.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lianyu.ai.database.model.ChatMessage
import com.lianyu.ai.database.model.FileFormat
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    // Cursor pagination: read messages older than a millisecond timestamp.
    @Query("SELECT * FROM chat_messages WHERE companionId = :companionId AND timestamp < :beforeTimestamp ORDER BY timestamp DESC LIMIT :limit")
    fun getMessagesBefore(companionId: Long, beforeTimestamp: Long, limit: Int): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages WHERE companionId = :companionId AND timestamp < :beforeTimestamp ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getMessagesBeforeSync(companionId: Long, beforeTimestamp: Long, limit: Int): List<ChatMessage>

    @Query("SELECT * FROM chat_messages WHERE companionId = :companionId ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentMessagesForCompanion(companionId: Long, limit: Int): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages WHERE companionId = :companionId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessagesSync(companionId: Long, limit: Int): List<ChatMessage>

    @Query("SELECT * FROM chat_messages WHERE companionId = :companionId ORDER BY timestamp ASC")
    suspend fun getMessagesForCompanionSync(companionId: Long): List<ChatMessage>

    @Query("SELECT * FROM chat_messages WHERE companionId = :companionId ORDER BY timestamp DESC LIMIT 1")
    fun getLastMessageForCompanion(companionId: Long): Flow<ChatMessage?>

    @Query("SELECT * FROM chat_messages WHERE id = :messageId")
    suspend fun getMessageById(messageId: Long): ChatMessage?

    @Query("SELECT * FROM chat_messages WHERE companionId = :companionId AND searchContent LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT :limit")
    suspend fun searchMessages(companionId: Long, query: String, limit: Int): List<ChatMessage>

    @Query("SELECT * FROM chat_messages WHERE companionId = :companionId AND fileFormat = :fileFormat ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getMessagesByFileFormat(companionId: Long, fileFormat: FileFormat, limit: Int): List<ChatMessage>

    @Query("SELECT * FROM chat_messages WHERE companionId = :companionId AND fileFormat = :fileFormat AND timestamp < :beforeTimestamp ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getMessagesByFileFormatBefore(companionId: Long, fileFormat: FileFormat, beforeTimestamp: Long, limit: Int): List<ChatMessage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage): Long

    @Delete
    suspend fun deleteMessage(message: ChatMessage): Int

    @Query("DELETE FROM chat_messages WHERE companionId = :companionId")
    suspend fun deleteMessagesForCompanion(companionId: Long): Int

    @Query("SELECT COUNT(*) FROM chat_messages WHERE companionId = :companionId AND isFromUser = 0")
    suspend fun getAiMessageCount(companionId: Long): Int

    @Query("UPDATE chat_messages SET content = :content, searchContent = :searchContent WHERE id = :messageId")
    suspend fun updateMessageContent(messageId: Long, content: String, searchContent: String): Int
}
