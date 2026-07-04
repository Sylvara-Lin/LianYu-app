package com.lianyu.ai.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lianyu.ai.database.model.MemoryCategory
import com.lianyu.ai.database.model.MemoryEntry
import com.lianyu.ai.database.model.TempMemory
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memory_entries WHERE companionId = :companionId AND deviceId = :deviceId ORDER BY timestamp DESC")
    fun getMemoriesForCompanion(companionId: Long, deviceId: String): Flow<List<MemoryEntry>>

    @Query("SELECT * FROM memory_entries WHERE companionId = :companionId AND deviceId = :deviceId AND category = :category ORDER BY timestamp DESC")
    fun getMemoriesByCategory(companionId: Long, deviceId: String, category: MemoryCategory): Flow<List<MemoryEntry>>

    @Query("SELECT * FROM memory_entries WHERE companionId = :companionId AND deviceId = :deviceId AND content LIKE '%' || :query || '%' ORDER BY importance DESC LIMIT :limit")
    suspend fun searchMemories(companionId: Long, deviceId: String, query: String, limit: Int = 5): List<MemoryEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryEntry): Long

    @Delete
    suspend fun deleteMemory(memory: MemoryEntry): Int

    @Query("DELETE FROM memory_entries WHERE companionId = :companionId AND deviceId = :deviceId")
    suspend fun deleteMemoriesForCompanion(companionId: Long, deviceId: String): Int

    @Query("SELECT COUNT(*) FROM memory_entries WHERE companionId = :companionId AND deviceId = :deviceId")
    suspend fun getMemoryCount(companionId: Long, deviceId: String): Int

    @Query("UPDATE memory_entries SET accessCount = accessCount + 1, lastAccessed = :now WHERE id = :memoryId")
    suspend fun updateAccessCount(memoryId: Long, now: Long = System.currentTimeMillis()): Int

    @Query("SELECT * FROM temp_memory WHERE companionId = :companionId AND deviceId = :deviceId ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentTempMemories(companionId: Long, deviceId: String, limit: Int = 20): Flow<List<TempMemory>>

    @Query("SELECT * FROM temp_memory WHERE companionId = :companionId AND deviceId = :deviceId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentTempMemoriesSync(companionId: Long, deviceId: String, limit: Int = 20): List<TempMemory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTempMemory(tempMemory: TempMemory): Long

    @Query("DELETE FROM temp_memory WHERE companionId = :companionId AND deviceId = :deviceId")
    suspend fun deleteTempMemoriesForCompanion(companionId: Long, deviceId: String): Int

    @Query("SELECT * FROM memory_entries WHERE deviceId = :deviceId ORDER BY timestamp DESC")
    suspend fun getAllMemoriesSync(deviceId: String): List<MemoryEntry>

    @Query("SELECT * FROM temp_memory WHERE deviceId = :deviceId ORDER BY timestamp DESC")
    suspend fun getAllTempMemoriesSync(deviceId: String): List<TempMemory>

    @Query("DELETE FROM memory_entries WHERE deviceId = :deviceId")
    suspend fun deleteAllMemories(deviceId: String): Int

    @Query("DELETE FROM temp_memory WHERE deviceId = :deviceId")
    suspend fun deleteAllTempMemories(deviceId: String): Int

    @Query("DELETE FROM temp_memory WHERE companionId = :companionId AND deviceId = :deviceId AND id NOT IN (SELECT id FROM temp_memory WHERE companionId = :companionId AND deviceId = :deviceId ORDER BY timestamp DESC LIMIT :keepCount)")
    suspend fun cleanupOldTempMemories(companionId: Long, deviceId: String, keepCount: Int = 20): Int
}
