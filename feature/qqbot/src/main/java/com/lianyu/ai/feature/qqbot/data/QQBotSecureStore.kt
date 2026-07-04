package com.lianyu.ai.feature.qqbot.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 安全存储 QQ Bot 敏感凭证（AppID / ClientSecret）。
 */
internal class QQBotSecureStore(context: Context) {
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

    fun getAccountJson(): String? = prefs.getString(KEY_ACCOUNT_JSON, null)
    fun setAccountJson(json: String) = prefs.edit().putString(KEY_ACCOUNT_JSON, json).apply()
    fun clearAccount() = prefs.edit().remove(KEY_ACCOUNT_JSON).apply()

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "qqbot_secure_store"
        private const val KEY_ACCOUNT_JSON = "account_json"
    }
}
