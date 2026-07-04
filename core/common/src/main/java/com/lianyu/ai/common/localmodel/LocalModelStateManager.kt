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
    private object Keys {
        val gemmaEnabled = booleanPreferencesKey("gemma_4_e2b_enabled")
        val pendingAutoEnable = booleanPreferencesKey("gemma_4_e2b_pending_auto_enable")
        val downloadId = longPreferencesKey("gemma_4_e2b_download_id")
    }

    fun isLocalModelEnabled(context: Context): Flow<Boolean> {
        return context.applicationContext.localModelDataStore.data.map { prefs ->
            prefs[Keys.gemmaEnabled] ?: false
        }
    }

    fun state(context: Context): Flow<LocalModelPreferencesState> {
        return context.applicationContext.localModelDataStore.data.map { prefs ->
            LocalModelPreferencesState(
                isGemmaEnabled = prefs[Keys.gemmaEnabled] ?: false,
                pendingAutoEnable = prefs[Keys.pendingAutoEnable] ?: false,
                downloadId = prefs[Keys.downloadId]
            )
        }
    }

    suspend fun setEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.localModelDataStore.edit { prefs ->
            prefs[Keys.gemmaEnabled] = enabled
        }
    }

    suspend fun setPendingAutoEnable(context: Context, pending: Boolean) {
        context.applicationContext.localModelDataStore.edit { prefs ->
            prefs[Keys.pendingAutoEnable] = pending
        }
    }

    suspend fun setDownloadId(context: Context, downloadId: Long?) {
        context.applicationContext.localModelDataStore.edit { prefs ->
            if (downloadId == null) {
                prefs.remove(Keys.downloadId)
            } else {
                prefs[Keys.downloadId] = downloadId
            }
        }
    }
}
