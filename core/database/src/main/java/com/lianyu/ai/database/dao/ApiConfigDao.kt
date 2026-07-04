package com.lianyu.ai.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lianyu.ai.database.model.ApiConfig
import com.lianyu.ai.database.model.ApiProvider
import kotlinx.coroutines.flow.Flow

@Dao
interface ApiConfigDao {
    @Query("SELECT * FROM api_configs ORDER BY id DESC")
    fun getAllConfigs(): Flow<List<ApiConfig>>

    @Query("SELECT * FROM api_configs WHERE provider = :provider ORDER BY id DESC")
    fun getConfigsByProvider(provider: ApiProvider): Flow<List<ApiConfig>>

    @Query("SELECT * FROM api_configs WHERE id = :id")
    suspend fun getConfigById(id: Long): ApiConfig?

    @Query("SELECT * FROM api_configs WHERE provider = :provider AND apiKey IS NOT NULL AND apiKey != '' ORDER BY id DESC LIMIT 1")
    suspend fun getConfigByProvider(provider: ApiProvider): ApiConfig?

    @Query("SELECT * FROM api_configs WHERE apiKey IS NOT NULL AND apiKey != '' AND isEnabled = 1 ORDER BY id DESC LIMIT 1")
    suspend fun getActiveConfig(): ApiConfig?

    @Query("SELECT * FROM api_configs WHERE apiKey IS NOT NULL AND apiKey != '' ORDER BY id DESC")
    fun getAllConfiguredConfigs(): Flow<List<ApiConfig>>

    @Query("SELECT * FROM api_configs WHERE apiKey IS NOT NULL AND apiKey != '' AND isEnabled = 1 ORDER BY id DESC LIMIT 1")
    suspend fun getActiveEnabledConfig(): ApiConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: ApiConfig): Long

    @Update
    suspend fun updateConfig(config: ApiConfig): Int

    @Query("DELETE FROM api_configs WHERE id = :id")
    suspend fun deleteConfigById(id: Long): Int

    @Query("DELETE FROM api_configs WHERE provider = :provider")
    suspend fun deleteConfig(provider: ApiProvider): Int

    @Query("UPDATE api_configs SET isEnabled = 0 WHERE id != :id")
    suspend fun disableOtherConfigs(id: Long): Int

    @Query("UPDATE api_configs SET isEnabled = 1 WHERE id = :id")
    suspend fun enableConfig(id: Long): Int
}
