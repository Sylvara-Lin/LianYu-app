package com.lianyu.ai.network.tts

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.localTtsDataStore by preferencesDataStore(name = "local_tts_settings")

/**
 * 本地 TTS 模型偏好状态（镜像 [LocalModelPreferencesState]）。
 *
 * @property isEnabled 当前选中模型是否已启用
 * @property pendingAutoEnable 下载完成后自动启用
 * @property downloadId 当前 in-flight 下载的 DownloadManager id
 * @property downloadFileIndex 多文件下载中当前文件索引（0-based）
 */
data class LocalTtsPreferencesState(
    val isEnabled: Boolean = false,
    val pendingAutoEnable: Boolean = false,
    val downloadId: Long? = null,
    val downloadFileIndex: Int = 0
)

/**
 * 本地离线 TTS 偏好持久化。
 *
 * 镜像 [com.lianyu.ai.feature.localmodel.LocalModelPreferences] +
 * [com.lianyu.ai.common.localmodel.LocalModelStateManager] 模式，
 * 但合并为单文件并放 core:network（自包含，不污染 core:common）。
 *
 * DataStore name: `"local_tts_settings"`
 * Keys 按 modelId 参数化（`<modelId>_enabled` 等），避免 LocalModelManager 的 gemma 硬编码 quirk。
 */
class LocalTtsPreferences(private val context: Context) {

    private val dataStore = context.applicationContext.localTtsDataStore
    private val selectedModelIdKey = stringPreferencesKey("selected_model_id")

    val selectedModelId: Flow<String> = dataStore.data.map { prefs ->
        prefs[selectedModelIdKey] ?: LocalTtsCatalog.default.id
    }

    /** 当前选中模型的完整状态（合并 enabled / pending / downloadId / fileIndex） */
    fun modelState(modelId: String): Flow<LocalTtsPreferencesState> = dataStore.data.map { prefs ->
        LocalTtsPreferencesState(
            isEnabled = prefs[booleanPreferencesKey("${modelId}_enabled")] ?: false,
            pendingAutoEnable = prefs[booleanPreferencesKey("${modelId}_pending_auto_enable")] ?: false,
            downloadId = prefs[longPreferencesKey("${modelId}_download_id")],
            downloadFileIndex = prefs[intPreferencesKey("${modelId}_download_file_index")] ?: 0
        )
    }

    suspend fun selectModel(modelId: String) {
        dataStore.edit { prefs -> prefs[selectedModelIdKey] = modelId }
    }

    suspend fun setEnabled(modelId: String, enabled: Boolean) {
        dataStore.edit { prefs -> prefs[booleanPreferencesKey("${modelId}_enabled")] = enabled }
    }

    suspend fun setPendingAutoEnable(modelId: String, pending: Boolean) {
        dataStore.edit { prefs -> prefs[booleanPreferencesKey("${modelId}_pending_auto_enable")] = pending }
    }

    suspend fun setDownloadId(modelId: String, downloadId: Long?) {
        dataStore.edit { prefs ->
            val key = longPreferencesKey("${modelId}_download_id")
            if (downloadId == null) prefs.remove(key) else prefs[key] = downloadId
        }
    }

    suspend fun setDownloadFileIndex(modelId: String, index: Int) {
        dataStore.edit { prefs -> prefs[intPreferencesKey("${modelId}_download_file_index")] = index }
    }
}
