package com.lianyu.ai.database.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Secure token store backed by EncryptedSharedPreferences.
 * Replaces the plaintext "auth_prefs" SharedPreferences.
 *
 * All token-class values (auth_token, refresh_token) are encrypted at rest
 * using AES-256 GCM with a key stored in Android Keystore.
 */
class AuthTokenStore(context: Context) {
    private val prefs: SharedPreferences
    private val legacyPrefs: SharedPreferences

    init {
        val appContext = context.applicationContext
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        prefs = EncryptedSharedPreferences.create(
            appContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        legacyPrefs = appContext.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        migrateLegacyPlaintextPrefs()
    }

    // --- Token lifecycle ---

    var authToken: String?
        get() = prefs.getString(KEY_AUTH_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_AUTH_TOKEN, value).apply()

    var refreshToken: String?
        get() = prefs.getString(KEY_REFRESH_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_REFRESH_TOKEN, value).apply()

    // --- User profile (non-sensitive but bundled for convenience) ---

    var userEmail: String?
        get() = prefs.getString(KEY_USER_EMAIL, null)
        set(value) = prefs.edit().putString(KEY_USER_EMAIL, value).apply()

    var userNickname: String?
        get() = prefs.getString(KEY_USER_NICKNAME, null)
        set(value) = prefs.edit().putString(KEY_USER_NICKNAME, value).apply()

    var userAvatar: String?
        get() = prefs.getString(KEY_USER_AVATAR, null)
        set(value) = prefs.edit().putString(KEY_USER_AVATAR, value).apply()

    // --- Bulk operations ---

    fun saveLoginResult(
        token: String,
        refreshToken: String?,
        email: String,
        nickname: String?,
        avatar: String?
    ) {
        prefs.edit()
            .putString(KEY_AUTH_TOKEN, token)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putString(KEY_USER_EMAIL, email)
            .putString(KEY_USER_NICKNAME, nickname ?: email)
            .putString(KEY_USER_AVATAR, avatar)
            .apply()
    }

    fun isLoggedIn(): Boolean = !authToken.isNullOrBlank()

    fun clear() {
        prefs.edit().clear().apply()
        legacyPrefs.edit().clear().apply()
    }

    private fun migrateLegacyPlaintextPrefs() {
        if (prefs.contains(KEY_AUTH_TOKEN) || prefs.contains(KEY_REFRESH_TOKEN)) {
            legacyPrefs.edit().clear().apply()
            return
        }

        val legacyAuthToken = legacyPrefs.getString(KEY_AUTH_TOKEN, null)
        val legacyRefreshToken = legacyPrefs.getString(KEY_REFRESH_TOKEN, null)
        val legacyUserEmail = legacyPrefs.getString(KEY_USER_EMAIL, null)
        val legacyUserNickname = legacyPrefs.getString(KEY_USER_NICKNAME, null)
        val legacyUserAvatar = legacyPrefs.getString(KEY_USER_AVATAR, null)

        if (legacyAuthToken.isNullOrBlank() && legacyRefreshToken.isNullOrBlank()) {
            legacyPrefs.edit().clear().apply()
            return
        }

        prefs.edit()
            .putString(KEY_AUTH_TOKEN, legacyAuthToken)
            .putString(KEY_REFRESH_TOKEN, legacyRefreshToken)
            .putString(KEY_USER_EMAIL, legacyUserEmail)
            .putString(KEY_USER_NICKNAME, legacyUserNickname ?: legacyUserEmail)
            .putString(KEY_USER_AVATAR, legacyUserAvatar)
            .apply()
        legacyPrefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "auth_store_encrypted"
        private const val LEGACY_PREFS_NAME = "auth_prefs"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_NICKNAME = "user_nickname"
        private const val KEY_USER_AVATAR = "user_avatar"
    }
}
