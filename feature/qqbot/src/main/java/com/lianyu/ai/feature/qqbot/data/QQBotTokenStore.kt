package com.lianyu.ai.feature.qqbot.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lianyu.ai.feature.qqbot.data.model.QQBotAccount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.qqbotDataStore: DataStore<Preferences> by preferencesDataStore(name = "qqbot_prefs")

class QQBotTokenStore(context: Context) {
    private val dataStore = context.applicationContext.qqbotDataStore
    private val secureStore = QQBotSecureStore(context.applicationContext)
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private val AUTO_REPLY_KEY = booleanPreferencesKey("qqbot_auto_reply")
        private val NOTIFY_ENABLED_KEY = booleanPreferencesKey("qqbot_notify_enabled")
        private val FORWARD_ENABLED_KEY = booleanPreferencesKey("qqbot_forward_enabled")
        private val DEFAULT_COMPANION_ID_KEY = longPreferencesKey("qqbot_default_companion_id")
        private val USER_COMPANION_MAP_KEY = stringPreferencesKey("qqbot_user_companion_map")
        private val CUSTOM_BOT_NAME_KEY = stringPreferencesKey("qqbot_custom_bot_name")
        private val ACCESS_TOKEN_KEY = stringPreferencesKey("qqbot_access_token")
        private val TOKEN_EXPIRE_AT_KEY = longPreferencesKey("qqbot_token_expire_at")
        private val SESSION_ID_KEY = stringPreferencesKey("qqbot_session_id")
        private val LAST_SEQUENCE_KEY = longPreferencesKey("qqbot_last_sequence")
    }

    val accountFlow: Flow<QQBotAccount?> = dataStore.data.map {
        secureStore.getAccountJson()?.let { json.decodeFromString(it) }
    }

    suspend fun getAccount(): QQBotAccount? =
        secureStore.getAccountJson()?.let { json.decodeFromString(it) }

    suspend fun saveAccount(account: QQBotAccount) {
        secureStore.setAccountJson(json.encodeToString(account))
    }

    suspend fun clearAccount() {
        secureStore.clearAccount()
        dataStore.edit { prefs ->
            prefs.remove(ACCESS_TOKEN_KEY)
            prefs.remove(TOKEN_EXPIRE_AT_KEY)
            prefs.remove(SESSION_ID_KEY)
            prefs.remove(LAST_SEQUENCE_KEY)
        }
    }

    suspend fun isLoggedIn(): Boolean = getAccount() != null

    val autoReplyFlow: Flow<Boolean> = dataStore.data.map { it[AUTO_REPLY_KEY] ?: true }
    suspend fun getAutoReply(): Boolean = autoReplyFlow.first()
    suspend fun setAutoReply(enabled: Boolean) = dataStore.edit { it[AUTO_REPLY_KEY] = enabled }

    val notifyEnabledFlow: Flow<Boolean> = dataStore.data.map { it[NOTIFY_ENABLED_KEY] ?: true }
    suspend fun getNotifyEnabled(): Boolean = notifyEnabledFlow.first()
    suspend fun setNotifyEnabled(enabled: Boolean) = dataStore.edit { it[NOTIFY_ENABLED_KEY] = enabled }

    val forwardEnabledFlow: Flow<Boolean> = dataStore.data.map { it[FORWARD_ENABLED_KEY] ?: true }
    suspend fun getForwardEnabled(): Boolean = forwardEnabledFlow.first()
    suspend fun setForwardEnabled(enabled: Boolean) = dataStore.edit { it[FORWARD_ENABLED_KEY] = enabled }

    val defaultCompanionIdFlow: Flow<Long?> = dataStore.data.map { it[DEFAULT_COMPANION_ID_KEY] }
    suspend fun getDefaultCompanionId(): Long? = defaultCompanionIdFlow.first()
    suspend fun setDefaultCompanionId(companionId: Long?) = dataStore.edit { prefs ->
        if (companionId != null) prefs[DEFAULT_COMPANION_ID_KEY] = companionId else prefs.remove(DEFAULT_COMPANION_ID_KEY)
    }

    val customBotNameFlow: Flow<String?> = dataStore.data.map { it[CUSTOM_BOT_NAME_KEY] }
    suspend fun getCustomBotName(): String? = customBotNameFlow.first()
    suspend fun setCustomBotName(name: String?) = dataStore.edit { prefs ->
        if (name != null) prefs[CUSTOM_BOT_NAME_KEY] = name else prefs.remove(CUSTOM_BOT_NAME_KEY)
    }

    suspend fun getAccessToken(): String? = dataStore.data.first()[ACCESS_TOKEN_KEY]
    suspend fun setAccessToken(token: String?) = dataStore.edit { prefs ->
        if (token != null) prefs[ACCESS_TOKEN_KEY] = token else prefs.remove(ACCESS_TOKEN_KEY)
    }

    suspend fun getTokenExpireAt(): Long = dataStore.data.first()[TOKEN_EXPIRE_AT_KEY] ?: 0L
    suspend fun setTokenExpireAt(timestamp: Long) = dataStore.edit { it[TOKEN_EXPIRE_AT_KEY] = timestamp }

    suspend fun getSessionId(): String? = dataStore.data.first()[SESSION_ID_KEY]
    suspend fun setSessionId(sessionId: String?) = dataStore.edit { prefs ->
        if (sessionId != null) prefs[SESSION_ID_KEY] = sessionId else prefs.remove(SESSION_ID_KEY)
    }

    suspend fun getLastSequence(): Long = dataStore.data.first()[LAST_SEQUENCE_KEY] ?: 0L
    suspend fun setLastSequence(seq: Long) = dataStore.edit { it[LAST_SEQUENCE_KEY] = seq }

    private val userCompanionMapFlow: Flow<Map<String, Long>> = dataStore.data.map { prefs ->
        prefs[USER_COMPANION_MAP_KEY]?.let {
            try { json.decodeFromString(it) } catch (_: Exception) { emptyMap() }
        } ?: emptyMap()
    }

    suspend fun getCompanionIdForQQUser(qqUserId: String): Long? = userCompanionMapFlow.first()[qqUserId]

    suspend fun setCompanionIdForQQUser(qqUserId: String, companionId: Long) {
        val current = userCompanionMapFlow.first().toMutableMap()
        current[qqUserId] = companionId
        dataStore.edit { it[USER_COMPANION_MAP_KEY] = json.encodeToString(current) }
    }

    suspend fun removeQQUserMapping(qqUserId: String) {
        val current = userCompanionMapFlow.first().toMutableMap()
        current.remove(qqUserId)
        dataStore.edit { it[USER_COMPANION_MAP_KEY] = json.encodeToString(current) }
    }

    suspend fun getAllQQUserMappings(): Map<String, Long> = userCompanionMapFlow.first()
}
