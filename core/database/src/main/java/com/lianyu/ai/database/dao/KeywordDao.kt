package com.lianyu.ai.database.dao

import androidx.room.*
import com.lianyu.ai.database.model.KeywordEntity

@Dao
interface KeywordDao {

    @Query("SELECT * FROM keywords WHERE isEnabled = 1 ORDER BY level, id")
    suspend fun getAllEnabled(): List<KeywordEntity>

    @Query("SELECT * FROM keywords WHERE level = :level AND isEnabled = 1 ORDER BY id")
    suspend fun getByLevel(level: String): List<KeywordEntity>

    @Query("SELECT * FROM keywords WHERE type = :type AND isEnabled = 1 ORDER BY id")
    suspend fun getByType(type: String): List<KeywordEntity>

    @Query("SELECT COUNT(*) FROM keywords")
    suspend fun count(): Int

    @Query("SELECT SUM(CASE WHEN isEnabled = 1 THEN 1 ELSE 0 END) FROM keywords")
    suspend fun countEnabled(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(keywords: List<KeywordEntity>): List<Long>

    @Update
    fun update(keyword: KeywordEntity): Int

    @Query("UPDATE keywords SET isEnabled = :enabled WHERE id = :id")
    fun setEnabled(id: Int, enabled: Boolean): Int

    @Delete
    fun delete(keyword: KeywordEntity): Int

    @Query("DELETE FROM keywords")
    fun deleteAll(): Int

    @Transaction
    @Query("SELECT * FROM keywords ORDER BY id")
    suspend fun getAllWithChecksum(): List<KeywordEntity>
}
