package com.lianyu.ai.database.repository

import com.lianyu.ai.database.dao.ApiConfigDao
import com.lianyu.ai.database.model.ApiConfig
import com.lianyu.ai.database.model.ApiProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ApiConfigRepository(
    private val apiConfigDao: ApiConfigDao,
    private val secretCodec: SecretCodec = S0
) {
    interface SecretCodec {
        fun encrypt(plaintext: String): String
        fun decrypt(value: String): String?
        fun isEncrypted(value: String): Boolean
    }

    fun getAllConfigs(): Flow<List<ApiConfig>> = apiConfigDao.getAllConfigs().map { list ->
        list.mapNotNull { decryptForUse(it, secretCodec) }
    }

    fun getConfigsByProvider(provider: ApiProvider): Flow<List<ApiConfig>> = 
        apiConfigDao.getConfigsByProvider(provider).map { list ->
            list.mapNotNull { decryptForUse(it, secretCodec) }
        }

    suspend fun getConfigById(id: Long): ApiConfig? =
        apiConfigDao.getConfigById(id)?.let { decryptAndMigrateIfNeeded(it) }

    suspend fun getConfigByProvider(provider: ApiProvider): ApiConfig? =
        apiConfigDao.getConfigByProvider(provider)?.let { decryptAndMigrateIfNeeded(it) }

    suspend fun getActiveConfig(): ApiConfig? =
        apiConfigDao.getActiveConfig()?.let { decryptAndMigrateIfNeeded(it) }

    fun getAllConfiguredConfigs(): Flow<List<ApiConfig>> = apiConfigDao.getAllConfiguredConfigs().map { list ->
        list.mapNotNull { decryptForUse(it, secretCodec) }
    }

    suspend fun getActiveEnabledConfig(): ApiConfig? =
        apiConfigDao.getActiveEnabledConfig()?.let { decryptAndMigrateIfNeeded(it) }

    suspend fun saveConfig(config: ApiConfig) = apiConfigDao.insertConfig(encryptForStorage(config, secretCodec))

    suspend fun updateConfig(config: ApiConfig) = apiConfigDao.updateConfig(encryptForStorage(config, secretCodec))

    suspend fun deleteConfigById(id: Long) = apiConfigDao.deleteConfigById(id)

    suspend fun deleteConfig(provider: ApiProvider) = apiConfigDao.deleteConfig(provider)

    suspend fun disableOtherConfigs(id: Long) = apiConfigDao.disableOtherConfigs(id)

    suspend fun enableConfig(id: Long) = apiConfigDao.enableConfig(id)

    private suspend fun decryptAndMigrateIfNeeded(config: ApiConfig): ApiConfig? {
        val decrypted = decryptForUse(config, secretCodec) ?: return null
        if (needsSecretMigration(config, secretCodec)) {
            apiConfigDao.updateConfig(encryptForStorage(decrypted, secretCodec))
        }
        return decrypted
    }

    companion object {
        fun encryptForStorage(config: ApiConfig, codec: SecretCodec): ApiConfig {
            if (config.apiKey.isBlank() || codec.isEncrypted(config.apiKey)) return config
            return config.copy(apiKey = codec.encrypt(config.apiKey))
        }

        fun decryptForUse(config: ApiConfig, codec: SecretCodec): ApiConfig? {
            if (config.apiKey.isBlank()) return config
            if (!codec.isEncrypted(config.apiKey)) return config
            val plaintext = codec.decrypt(config.apiKey)
            if (plaintext == null) {
                android.util.Log.e("ApiConfigRepo", "Failed to decrypt apiKey for config id=${config.id}, provider=${config.provider}")
                return null
            }
            return config.copy(apiKey = plaintext)
        }

        fun needsSecretMigration(config: ApiConfig, codec: SecretCodec): Boolean =
            config.apiKey.isNotBlank() && !codec.isEncrypted(config.apiKey)
    }
}
