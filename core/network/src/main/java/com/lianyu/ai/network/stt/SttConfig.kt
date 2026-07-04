package com.lianyu.ai.network.stt

import android.content.Context

data class SttConfig(
    val siliconflowApiKey: String = "",
    val preferOffline: Boolean = true
) {
    fun isProviderConfigured(provider: SttProvider): Boolean {
        return when (provider) {
            SttProvider.ANDROID -> true
            SttProvider.SHERPA_ONNX -> true
            SttProvider.SILICONFLOW -> siliconflowApiKey.isNotBlank()
        }
    }

    companion object {
        fun fromSharedPreferences(context: Context): SttConfig {
            val prefs = context.getSharedPreferences("stt_settings", Context.MODE_PRIVATE)
            return SttConfig(
                siliconflowApiKey = prefs.getString("sf_stt_api_key", "") ?: "",
                preferOffline = prefs.getBoolean("prefer_offline_stt", true)
            )
        }

        fun saveToSharedPreferences(context: Context, config: SttConfig) {
            val prefs = context.getSharedPreferences("stt_settings", Context.MODE_PRIVATE)
            prefs.edit().apply {
                putString("sf_stt_api_key", config.siliconflowApiKey)
                putBoolean("prefer_offline_stt", config.preferOffline)
                apply()
            }
        }
    }
}
