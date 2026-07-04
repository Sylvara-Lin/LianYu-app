package com.lianyu.ai.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lianyu.ai.database.model.ChatGroup
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatGroupDao {
    @Query("SELECT * FROM chat_groups ORDER BY updatedAt DESC")
    fun getAllGroups(): Flow<List<ChatGroup>>

    @Query("SELECT * FROM chat_groups ORDER BY updatedAt DESC")
    suspend fun getAllGroupsSync(): List<ChatGroup>

    @Query("SELECT * FROM chat_groups WHERE id = :id")
    suspend fun getGroupById(id: Long): ChatGroup?

    @Query("SELECT * FROM chat_groups WHERE id = :id")
    fun getGroupByIdFlow(id: Long): Flow<ChatGroup?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: ChatGroup): Long

    @Update
    suspend fun updateGroup(group: ChatGroup): Int

    @Delete
    suspend fun deleteGroup(group: ChatGroup): Int

    @Query("UPDATE chat_groups SET updatedAt = :timestamp WHERE id = :id")
    suspend fun updateTimestamp(id: Long, timestamp: Long = System.currentTimeMillis()): Int
}
