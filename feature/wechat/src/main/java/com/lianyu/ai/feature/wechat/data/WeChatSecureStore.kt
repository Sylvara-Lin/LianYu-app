package com.lianyu.ai.feature.wechat.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Secure storage for WeChat sensitive credentials.
 * Wraps EncryptedSharedPreferences (AES-256-GCM, Android Keystore key).
 *
 * Stores:
 *   - botToken: WeChat bot authentication token
 *   - contextTokens: per-user chat context tokens
 */
internal class WeChatSecureStore(context: Context) {
    private val prefs: SharedPreferences

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        prefs = EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // --- Account (botToken is the critical secret) ---

    fun getAccountJson(): String? = prefs.getString(KEY_ACCOUNT_JSON, null)
    fun setAccountJson(json: String) = prefs.edit().putString(KEY_ACCOUNT_JSON, json).apply()
    fun clearAccount() = prefs.edit().remove(KEY_ACCOUNT_JSON).apply()

    // --- Context tokens (per-user chat session tokens) ---

    fun getContextTokensJson(): String? = prefs.getString(KEY_CONTEXT_TOKENS_JSON, null)
    fun setContextTokensJson(json: String) = prefs.edit().putString(KEY_CONTEXT_TOKENS_JSON, json).apply()
    fun clearContextTokens() = prefs.edit().remove(KEY_CONTEXT_TOKENS_JSON).apply()

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "wechat_secure_store"
        private const val KEY_ACCOUNT_JSON = "account_json"
        private const val KEY_CONTEXT_TOKENS_JSON = "context_tokens_json"
    }
}
