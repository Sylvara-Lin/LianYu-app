package com.lianyu.ai.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lianyu.ai.database.model.CompanionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CompanionDao {
    @Query("SELECT * FROM companions ORDER BY updatedAt DESC")
    fun getAllCompanions(): Flow<List<CompanionEntity>>

    @Query("SELECT * FROM companions WHERE id = :id")
    suspend fun getCompanionById(id: Long): CompanionEntity?

    @Query("SELECT * FROM companions WHERE id = :id")
    fun getCompanionByIdFlow(id: Long): Flow<CompanionEntity?>

    @Query("SELECT * FROM companions ORDER BY updatedAt DESC")
    suspend fun getAllCompanionsSync(): List<CompanionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompanion(companion: CompanionEntity): Long

    @Update
    suspend fun updateCompanion(companion: CompanionEntity): Int

    @Delete
    suspend fun deleteCompanion(companion: CompanionEntity): Int

    @Query("UPDATE companions SET updatedAt = :timestamp WHERE id = :id")
    suspend fun updateTimestamp(id: Long, timestamp: Long = System.currentTimeMillis()): Int

    @Query("UPDATE companions SET intimacy = intimacy + :amount WHERE id = :id")
    suspend fun increaseIntimacy(id: Long, amount: Int = 1): Int

    @Query("SELECT intimacy FROM companions WHERE id = :id")
    suspend fun getIntimacy(id: Long): Int?
}
