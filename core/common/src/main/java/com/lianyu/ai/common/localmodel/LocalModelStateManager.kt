package com.lianyu.ai.common.localmodel

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.localModelDataStore by preferencesDataStore(name = "local_model_settings")

data class LocalModelPreferencesState(
    val isGemmaEnabled: Boolean = false,
    val pendingAutoEnable: Boolean = false,
    val downloadId: Long? = null
)

object LocalModelStateManager {
    private fun enabledKey(modelId: String) = booleanPreferencesKey("${modelId}_enabled")
    private fun pendingKey(modelId: String) = booleanPreferencesKey("${modelId}_pending_auto_enable")
    private fun downloadIdKey(modelId: String) = longPreferencesKey("${modelId}_download_id")

    fun isLocalModelEnabled(context: Context, modelId: String = "gemma_4_e2b"): Flow<Boolean> {
        return context.applicationContext.localModelDataStore.data.map { prefs ->
            prefs[enabledKey(modelId)] ?: false
        }
    }

    fun state(context: Context, modelId: String = "gemma_4_e2b"): Flow<LocalModelPreferencesState> {
        return context.applicationContext.localModelDataStore.data.map { prefs ->
            LocalModelPreferencesState(
                isGemmaEnabled = prefs[enabledKey(modelId)] ?: false,
                pendingAutoEnable = prefs[pendingKey(modelId)] ?: false,
                downloadId = prefs[downloadIdKey(modelId)]
            )
        }
    }

    suspend fun setEnabled(context: Context, modelId: String, enabled: Boolean) {
        context.applicationContext.localModelDataStore.edit { prefs ->
            prefs[enabledKey(modelId)] = enabled
        }
    }

    suspend fun setPendingAutoEnable(context: Context, modelId: String, pending: Boolean) {
        context.applicationContext.localModelDataStore.edit { prefs ->
            prefs[pendingKey(modelId)] = pending
        }
    }

    suspend fun setDownloadId(context: Context, modelId: String, downloadId: Long?) {
        context.applicationContext.localModelDataStore.edit { prefs ->
            if (downloadId == null) {
                prefs.remove(downloadIdKey(modelId))
            } else {
                prefs[downloadIdKey(modelId)] = downloadId
            }
        }
    }
}
