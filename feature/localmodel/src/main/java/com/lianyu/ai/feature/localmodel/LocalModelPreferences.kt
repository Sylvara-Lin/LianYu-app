package com.lianyu.ai.feature.localmodel

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lianyu.ai.common.localmodel.LocalModelStateManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

private val Context.localModelSelectionDataStore by preferencesDataStore(name = "local_model_selection")

class LocalModelPreferences(private val context: Context) {

    private val selectionDataStore = context.applicationContext.localModelSelectionDataStore
    private val selectedModelIdKey = stringPreferencesKey("selected_model_id")

    val selectedModelId: Flow<String> = selectionDataStore.data.map { prefs ->
        prefs[selectedModelIdKey] ?: "gemma_4_e2b"
    }

    val state: Flow<com.lianyu.ai.common.localmodel.LocalModelPreferencesState> =
        selectedModelId.flatMapLatest { modelId ->
            LocalModelStateManager.state(context, modelId)
        }

    suspend fun selectModel(modelId: String) {
        selectionDataStore.edit { prefs ->
            prefs[selectedModelIdKey] = modelId
        }
    }

    // Per-model download state
    fun getModelDownloadId(modelId: String): Flow<Long?> {
        val key = longPreferencesKey("${modelId}_download_id")
        return selectionDataStore.data.map { prefs ->
            prefs[key]
        }
    }

    suspend fun setModelDownloadId(modelId: String, downloadId: Long?) {
        val key = longPreferencesKey("${modelId}_download_id")
        selectionDataStore.edit { prefs ->
            if (downloadId == null) {
                prefs.remove(key)
            } else {
                prefs[key] = downloadId
            }
        }
    }

    suspend fun setEnabled(enabled: Boolean) {
        val modelId = selectedModelId.first()
        LocalModelStateManager.setEnabled(context, modelId, enabled)
    }

    suspend fun setPendingAutoEnable(pending: Boolean) {
        val modelId = selectedModelId.first()
        LocalModelStateManager.setPendingAutoEnable(context, modelId, pending)
    }

    suspend fun setDownloadId(downloadId: Long?) {
        val modelId = selectedModelId.first()
        LocalModelStateManager.setDownloadId(context, modelId, downloadId)
    }
}
