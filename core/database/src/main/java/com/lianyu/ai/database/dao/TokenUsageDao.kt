package com.lianyu.ai.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lianyu.ai.database.model.TokenUsage

@Dao
interface TokenUsageDao {

    @Query("SELECT * FROM token_usage WHERE companionId = :companionId AND deviceId = :deviceId AND date = :date LIMIT 1")
    suspend fun getUsageByCompanionAndDate(companionId: Long, deviceId: String, date: String): TokenUsage?

    @Query("SELECT * FROM token_usage WHERE companionId = -1 AND deviceId = :deviceId AND date = :date LIMIT 1")
    suspend fun getGlobalUsageByDate(deviceId: String, date: String): TokenUsage?

    @Query("SELECT * FROM token_usage WHERE companionId = :companionId AND deviceId = :deviceId ORDER BY date DESC LIMIT :limit")
    suspend fun getUsageByCompanion(companionId: Long, deviceId: String, limit: Int = 30): List<TokenUsage>

    @Query("SELECT * FROM token_usage WHERE companionId = -1 AND deviceId = :deviceId ORDER BY date DESC LIMIT :limit")
    suspend fun getGlobalUsage(deviceId: String, limit: Int = 30): List<TokenUsage>

    @Query("SELECT * FROM token_usage WHERE deviceId = :deviceId AND date BETWEEN :startDate AND :endDate AND (companionId = :companionId OR :isGlobal = 1) ORDER BY date DESC")
    suspend fun getUsageByDateRange(deviceId: String, startDate: String, endDate: String, companionId: Long? = null, isGlobal: Boolean = false): List<TokenUsage>

    @Query("SELECT * FROM token_usage WHERE deviceId = :deviceId AND date = :date ORDER BY date DESC")
    suspend fun getTodayAllUsage(deviceId: String, date: String): List<TokenUsage>

    @Query("SELECT * FROM token_usage WHERE deviceId = :deviceId AND date >= :sinceDate ORDER BY date DESC")
    suspend fun getUsageSince(deviceId: String, sinceDate: String): List<TokenUsage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tokenUsage: TokenUsage): Long

    @Query("""
        INSERT OR REPLACE INTO token_usage (id, companionId, date, inputTokens, outputTokens, totalTokens, requestCount, timestamp, deviceId)
        VALUES (
            COALESCE((SELECT id FROM token_usage WHERE companionId = :companionId AND date = :date AND deviceId = :deviceId), NULL),
            :companionId,
            :date,
            COALESCE((SELECT inputTokens FROM token_usage WHERE companionId = :companionId AND date = :date AND deviceId = :deviceId), 0) + :inputTokens,
            COALESCE((SELECT outputTokens FROM token_usage WHERE companionId = :companionId AND date = :date AND deviceId = :deviceId), 0) + :outputTokens,
            COALESCE((SELECT totalTokens FROM token_usage WHERE companionId = :companionId AND date = :date AND deviceId = :deviceId), 0) + :inputTokens + :outputTokens,
            COALESCE((SELECT requestCount FROM token_usage WHERE companionId = :companionId AND date = :date AND deviceId = :deviceId), 0) + 1,
            :timestamp,
            :deviceId
        )
        """)
    suspend fun insertOrUpdate(
        companionId: Long,
        deviceId: String,
        date: String,
        inputTokens: Long,
        outputTokens: Long,
        timestamp: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM token_usage WHERE deviceId = :deviceId AND timestamp < :cutoffTimestamp")
    suspend fun deleteOldRecords(deviceId: String, cutoffTimestamp: Long): Int

    @Query("DELETE FROM token_usage WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM token_usage WHERE deviceId = :deviceId")
    suspend fun deleteAll(deviceId: String): Int

    @Query("SELECT * FROM token_usage WHERE deviceId = :deviceId ORDER BY date DESC")
    suspend fun getAllUsageSync(deviceId: String): List<TokenUsage>

    @Query("SELECT SUM(inputTokens) as totalInput, SUM(outputTokens) as totalOutput, SUM(totalTokens) as total, SUM(requestCount) as requests FROM token_usage WHERE deviceId = :deviceId AND date >= :sinceDate")
    suspend fun getTotalStats(deviceId: String, sinceDate: String): TotalStats?

    @Query("SELECT SUM(inputTokens) as totalInput, SUM(outputTokens) as totalOutput, SUM(totalTokens) as total, SUM(requestCount) as requests FROM token_usage WHERE companionId = :companionId AND deviceId = :deviceId AND date >= :sinceDate")
    suspend fun getTotalStatsByCompanion(companionId: Long, deviceId: String, sinceDate: String): TotalStats?

    data class TotalStats(
        val totalInput: Long?,
        val totalOutput: Long?,
        val total: Long?,
        val requests: Int?
    )
}
