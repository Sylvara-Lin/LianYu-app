package com.lianyu.ai.feature.wechat.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.wechatDataStore: DataStore<Preferences> by preferencesDataStore(name = "wechat_prefs")

@Serializable
@SerialName("A0")
data class A0(
    val botToken: String,
    val ilinkBotId: String,
    val ilinkUserId: String,
    val baseUrl: String = "https://ilinkai.weixin.qq.com",
    val accountId: String = "default"
)

class WeChatTokenStore(context: Context) {

    private val dataStore = context.applicationContext.wechatDataStore
    private val secureStore = WeChatSecureStore(context.applicationContext)
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private val CURSOR_KEY = stringPreferencesKey("wechat_cursor")
        private val AUTO_REPLY_KEY = booleanPreferencesKey("wechat_auto_reply")
        private val NOTIFY_ENABLED_KEY = booleanPreferencesKey("wechat_notify_enabled")
        private val FORWARD_ENABLED_KEY = booleanPreferencesKey("wechat_forward_enabled")
        private val DEFAULT_COMPANION_ID_KEY = longPreferencesKey("wechat_default_companion_id")
        private val USER_COMPANION_MAP_KEY = stringPreferencesKey("wechat_user_companion_map")
        private val CUSTOM_BOT_NAME_KEY = stringPreferencesKey("wechat_custom_bot_name")
    }

    // ==================== Account (encrypted) ====================

    val accountFlow: Flow<A0?> = dataStore.data.map {
        // Read from secure store, not DataStore
        secureStore.getAccountJson()?.let { json.decodeFromString(it) }
    }

    suspend fun getAccount(): A0? =
        secureStore.getAccountJson()?.let { json.decodeFromString(it) }

    suspend fun saveAccount(account: A0) {
        secureStore.setAccountJson(json.encodeToString(account))
    }

    suspend fun clearAccount() {
        secureStore.clearAccount()
        secureStore.clearContextTokens()
        dataStore.edit { prefs ->
            prefs.remove(CURSOR_KEY)
        }
    }

    // ==================== Auto Reply ====================

    val autoReplyFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[AUTO_REPLY_KEY] ?: true
    }

    suspend fun getAutoReply(): Boolean = autoReplyFlow.first()

    suspend fun setAutoReply(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[AUTO_REPLY_KEY] = enabled
        }
    }

    // ==================== Notify Enabled ====================

    val notifyEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[NOTIFY_ENABLED_KEY] ?: true
    }

    suspend fun getNotifyEnabled(): Boolean = notifyEnabledFlow.first()

    suspend fun setNotifyEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[NOTIFY_ENABLED_KEY] = enabled
        }
    }

    // ==================== Forward Enabled ====================

    val forwardEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[FORWARD_ENABLED_KEY] ?: true
    }

    suspend fun getForwardEnabled(): Boolean = forwardEnabledFlow.first()

    suspend fun setForwardEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[FORWARD_ENABLED_KEY] = enabled
        }
    }

    // ==================== Default Companion ====================

    val defaultCompanionIdFlow: Flow<Long?> = dataStore.data.map { prefs ->
        prefs[DEFAULT_COMPANION_ID_KEY]
    }

    suspend fun getDefaultCompanionId(): Long? = defaultCompanionIdFlow.first()

    suspend fun setDefaultCompanionId(companionId: Long?) {
        dataStore.edit { prefs ->
            if (companionId != null) {
                prefs[DEFAULT_COMPANION_ID_KEY] = companionId
            } else {
                prefs.remove(DEFAULT_COMPANION_ID_KEY)
            }
        }
    }

    // ==================== Custom Bot Name ====================

    val customBotNameFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[CUSTOM_BOT_NAME_KEY]
    }

    suspend fun getCustomBotName(): String? = customBotNameFlow.first()

    suspend fun setCustomBotName(name: String?) {
        dataStore.edit { prefs ->
            if (name != null) {
                prefs[CUSTOM_BOT_NAME_KEY] = name
            } else {
                prefs.remove(CUSTOM_BOT_NAME_KEY)
            }
        }
    }

    // ==================== User -> Companion Mapping ====================

    private val userCompanionMapFlow: Flow<Map<String, Long>> = dataStore.data.map { prefs ->
        prefs[USER_COMPANION_MAP_KEY]?.let {
            try {
                json.decodeFromString(it)
            } catch (_: Exception) {
                emptyMap()
            }
        } ?: emptyMap()
    }

    suspend fun getCompanionIdForWechatUser(wechatUserId: String): Long? {
        val map = userCompanionMapFlow.first()
        return map[wechatUserId]
    }

    suspend fun setCompanionIdForWechatUser(wechatUserId: String, companionId: Long) {
        val current = userCompanionMapFlow.first().toMutableMap()
        current[wechatUserId] = companionId
        dataStore.edit { prefs ->
            prefs[USER_COMPANION_MAP_KEY] = json.encodeToString(current)
        }
    }

    suspend fun removeWechatUserMapping(wechatUserId: String) {
        val current = userCompanionMapFlow.first().toMutableMap()
        current.remove(wechatUserId)
        dataStore.edit { prefs ->
            prefs[USER_COMPANION_MAP_KEY] = json.encodeToString(current)
        }
    }

    suspend fun getAllWechatUserMappings(): Map<String, Long> = userCompanionMapFlow.first()

    suspend fun getWechatUserIdsForCompanionId(companionId: Long): List<String> {
        val map = userCompanionMapFlow.first()
        return map.filter { it.value == companionId }.keys.toList()
    }

    // ==================== Context Tokens (encrypted) ====================
    // Key format: "accountId:userId" -> contextToken

    private suspend fun getContextTokens(): Map<String, String> =
        secureStore.getContextTokensJson()?.let {
            try { json.decodeFromString(it) } catch (_: Exception) { emptyMap() }
        } ?: emptyMap()

    private suspend fun saveContextTokens(tokens: Map<String, String>) {
        secureStore.setContextTokensJson(json.encodeToString(tokens))
    }

    suspend fun getContextToken(accountId: String, userId: String): String? {
        return getContextTokens()["$accountId:$userId"]
    }

    suspend fun saveContextToken(accountId: String, userId: String, token: String) {
        val current = getContextTokens().toMutableMap()
        current["$accountId:$userId"] = token
        saveContextTokens(current)
    }

    suspend fun getContextTokens(accountId: String): Map<String, String> {
        val prefix = "$accountId:"
        return getContextTokens()
            .filterKeys { it.startsWith(prefix) }
            .mapKeys { (key, _) -> key.removePrefix(prefix) }
    }

    // ==================== getUpdates Cursor ====================

    val cursorFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[CURSOR_KEY] ?: ""
    }

    suspend fun getCursor(): String = cursorFlow.first()

    suspend fun saveCursor(cursor: String) {
        dataStore.edit { prefs ->
            prefs[CURSOR_KEY] = cursor
        }
    }

    // ==================== Helper ====================

    suspend fun isLoggedIn(): Boolean = getAccount() != null
}
