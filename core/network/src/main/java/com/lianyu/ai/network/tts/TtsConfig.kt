package com.lianyu.ai.network.tts

import android.content.Context
import com.lianyu.ai.common.SecureLog

data class TtsConfig(
    val aliyunKeyId: String = "",
    val aliyunKeySecret: String = "",
    val aliyunAppKey: String = "",
    val baiduApiKey: String = "",
    val baiduSecretKey: String = "",
    val xunfeiAppId: String = "",
    val xunfeiApiKey: String = "",
    val xunfeiApiSecret: String = "",
    val azureSubscriptionKey: String = "",
    val azureRegion: String = "eastasia",
    val volcengineAppId: String = "",
    val volcengineToken: String = "",
    val volcengineCluster: String = "",
    // SiliconFlow TTS (CosyVoice2)
    val siliconflowApiKey: String = "",
    val siliconflowCustomVoiceId: String = "",
    val siliconflowUseGlobalKey: Boolean = true,
    val siliconflowTtsModel: String = "FunAudioLLM/CosyVoice2-0.5B",
    val siliconflowSpeed: String = "1.0",
    val siliconflowGain: String = "0",
    val siliconflowSampleRate: Int = 44100,
    // Custom TTS endpoint
    val customTtsUrl: String = "",
    val customTtsApiKey: String = "",
    val customTtsModel: String = "",
    val customTtsVoiceId: String = "",
    // Local offline TTS (sherpa-onnx)
    val localTtsSpeed: Float = 1.0f,
    val localTtsSid: Int = 0
) {
    fun isProviderConfigured(provider: TtsProvider): Boolean {
        return when (provider) {
            TtsProvider.ANDROID -> true
            TtsProvider.ALIYUN -> aliyunKeyId.isNotBlank() && aliyunKeySecret.isNotBlank() && aliyunAppKey.isNotBlank()
            TtsProvider.BAIDU -> baiduApiKey.isNotBlank() && baiduSecretKey.isNotBlank()
            TtsProvider.XUNFEI -> xunfeiAppId.isNotBlank() && xunfeiApiKey.isNotBlank() && xunfeiApiSecret.isNotBlank()
            TtsProvider.MICROSOFT -> azureSubscriptionKey.isNotBlank()
            TtsProvider.VOLCENGINE -> volcengineAppId.isNotBlank() && volcengineToken.isNotBlank()
            TtsProvider.SILICONFLOW -> siliconflowUseGlobalKey
                || siliconflowApiKey.isNotBlank()
                || customTtsUrl.isNotBlank()
            TtsProvider.SHERPA_LOCAL -> true
        }
    }

    companion object {
        fun fromSharedPreferences(context: Context): TtsConfig {
            val prefs = context.getSharedPreferences("tts_settings", Context.MODE_PRIVATE)
            return TtsConfig(
                aliyunKeyId = prefs.getString("aliyun_key", "") ?: "",
                aliyunKeySecret = prefs.getString("aliyun_secret", "") ?: "",
                aliyunAppKey = prefs.getString("aliyun_app_key", "") ?: "",
                baiduApiKey = prefs.getString("baidu_key", "") ?: "",
                baiduSecretKey = prefs.getString("baidu_secret", "") ?: "",
                xunfeiAppId = prefs.getString("xunfei_app_id", "") ?: "",
                xunfeiApiKey = prefs.getString("xunfei_key", "") ?: "",
                xunfeiApiSecret = prefs.getString("xunfei_secret", "") ?: "",
                azureSubscriptionKey = prefs.getString("azure_key", "") ?: "",
                azureRegion = prefs.getString("azure_region", "eastasia") ?: "eastasia",
                volcengineAppId = prefs.getString("volcengine_app_id", "") ?: "",
                volcengineToken = prefs.getString("volcengine_token", "") ?: "",
                volcengineCluster = prefs.getString("volcengine_cluster", "") ?: "",
                siliconflowApiKey = prefs.getString("sf_api_key", "") ?: "",
                siliconflowCustomVoiceId = prefs.getString("sf_custom_voice_id", "") ?: "",
                siliconflowUseGlobalKey = prefs.getBoolean("sf_use_global_key", true),
                siliconflowTtsModel = prefs.getString("sf_tts_model", "FunAudioLLM/CosyVoice2-0.5B") ?: "FunAudioLLM/CosyVoice2-0.5B",
                siliconflowSpeed = prefs.getString("sf_speed", "1.0") ?: "1.0",
                siliconflowGain = prefs.getString("sf_gain", "0") ?: "0",
                siliconflowSampleRate = prefs.getInt("sf_sample_rate", 44100),
                customTtsUrl = prefs.getString("custom_tts_url", "") ?: "",
                customTtsApiKey = prefs.getString("custom_tts_api_key", "") ?: "",
                customTtsModel = prefs.getString("custom_tts_model", "") ?: "",
                customTtsVoiceId = prefs.getString("custom_tts_voice_id", "") ?: "",
                localTtsSpeed = prefs.getFloat("local_tts_speed", 1.0f),
                localTtsSid = prefs.getInt("local_tts_sid", 0)
            )
        }

        fun saveToSharedPreferences(context: Context, config: TtsConfig) {
            val prefs = context.getSharedPreferences("tts_settings", Context.MODE_PRIVATE)
            prefs.edit().apply {
                putString("aliyun_key", config.aliyunKeyId)
                putString("aliyun_secret", config.aliyunKeySecret)
                putString("aliyun_app_key", config.aliyunAppKey)
                putString("baidu_key", config.baiduApiKey)
                putString("baidu_secret", config.baiduSecretKey)
                putString("xunfei_app_id", config.xunfeiAppId)
                putString("xunfei_key", config.xunfeiApiKey)
                putString("xunfei_secret", config.xunfeiApiSecret)
                putString("azure_key", config.azureSubscriptionKey)
                putString("azure_region", config.azureRegion)
                putString("volcengine_app_id", config.volcengineAppId)
                putString("volcengine_token", config.volcengineToken)
                putString("volcengine_cluster", config.volcengineCluster)
                putString("sf_api_key", config.siliconflowApiKey)
                putString("sf_custom_voice_id", config.siliconflowCustomVoiceId)
                putBoolean("sf_use_global_key", config.siliconflowUseGlobalKey)
                putString("sf_tts_model", config.siliconflowTtsModel)
                putString("sf_speed", config.siliconflowSpeed)
                putString("sf_gain", config.siliconflowGain)
                putInt("sf_sample_rate", config.siliconflowSampleRate)
                putString("custom_tts_url", config.customTtsUrl)
                putString("custom_tts_api_key", config.customTtsApiKey)
                putString("custom_tts_model", config.customTtsModel)
                putString("custom_tts_voice_id", config.customTtsVoiceId)
                putFloat("local_tts_speed", config.localTtsSpeed)
                putInt("local_tts_sid", config.localTtsSid)
                apply()
            }
            SecureLog.i("TtsConfig", "Configuration saved to SharedPreferences")
        }
    }
}
