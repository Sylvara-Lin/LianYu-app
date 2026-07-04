package com.lianyu.ai.feature.chat.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lianyu.ai.common.ChatDetailSettingsDataStoreProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class CompanionChatDetailSettings(
    val backgroundKey: String? = null,
    val useGlobalBackground: Boolean = true,
    val proactiveEnabled: Boolean = true,
    val proactiveIntervalMinutes: Int = 180,       // 手动输入的主动消息间隔（分钟），替代 preset
    val proactiveMinIntervalMinutes: Int = 60,      // 最小间隔（分钟）
    val proactiveMaxIntervalMinutes: Int = 720,     // 最大间隔（分钟）
    val proactiveDailyLimit: Int = 6,
    val allowNewTopic: Boolean = true,
    val allowLateNightMessage: Boolean = false,
    val allowFollowUpMessage: Boolean = true,
    val doNotDisturbEnabled: Boolean = false,
    val dndStartMinutes: Int = 23 * 60,
    val dndEndMinutes: Int = 8 * 60,
    val allowPriorityMessageInDnd: Boolean = false,
    val blocked: Boolean = false,
    val wechatSyncEnabled: Boolean = true,
    val stickerProbability: Int = 30, // AI发送表情包的概率 0-100
    val ttsEnabled: Boolean = false, // 是否启用AI回复转语音
    val ttsProbability: Int = 50, // AI回复转语音的概率 0-100
    val ntpTimeEnabled: Boolean = false, // 是否启用NTP精确时间感知（关闭则使用设备本地时间）
    val updatedAt: Long = System.currentTimeMillis()
)

class ChatDetailSettingsStore(context: Context) {

    private val dataStore = ChatDetailSettingsDataStoreProvider.get(context)
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private val SETTINGS_MAP_KEY = stringPreferencesKey("companion_chat_detail_settings_map")
    }

    val settingsMapFlow: Flow<Map<Long, CompanionChatDetailSettings>> = dataStore.data.map { prefs ->
        prefs[SETTINGS_MAP_KEY]?.let(::decodeSettingsMap) ?: emptyMap()
    }

    fun settingsFlow(companionId: Long): Flow<CompanionChatDetailSettings> =
        settingsMapFlow.map { it[companionId] ?: CompanionChatDetailSettings() }

    suspend fun getSettings(companionId: Long): CompanionChatDetailSettings =
        settingsFlow(companionId).first()

    suspend fun updateSettings(companionId: Long, transform: (CompanionChatDetailSettings) -> CompanionChatDetailSettings) {
        dataStore.edit { prefs ->
            val currentMap = prefs[SETTINGS_MAP_KEY]?.let(::decodeSettingsMap)?.toMutableMap() ?: mutableMapOf()
            val currentSettings = currentMap[companionId] ?: CompanionChatDetailSettings()
            currentMap[companionId] = transform(currentSettings).copy(updatedAt = System.currentTimeMillis())
            prefs[SETTINGS_MAP_KEY] = json.encodeToString(currentMap)
        }
    }

    suspend fun replaceSettings(companionId: Long, settings: CompanionChatDetailSettings) {
        updateSettings(companionId) { settings }
    }

    suspend fun resetSettings(companionId: Long) {
        dataStore.edit { prefs ->
            val currentMap = prefs[SETTINGS_MAP_KEY]?.let(::decodeSettingsMap)?.toMutableMap() ?: mutableMapOf()
            currentMap.remove(companionId)
            prefs[SETTINGS_MAP_KEY] = json.encodeToString(currentMap)
        }
    }

    private fun decodeSettingsMap(raw: String): Map<Long, CompanionChatDetailSettings> =
        runCatching { json.decodeFromString<Map<Long, CompanionChatDetailSettings>>(raw) }.getOrElse { emptyMap() }
}
