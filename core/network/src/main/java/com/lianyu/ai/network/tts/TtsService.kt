package com.lianyu.ai.network.tts

import android.content.Context
import com.lianyu.ai.common.SecureLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class TtsService(private val context: Context) {

    private val providers = mutableMapOf<TtsProvider, TtsProviderInterface>()
    private var currentProvider: TtsProvider = TtsProvider.ANDROID
    private val androidTts = AndroidTtsProvider()
    private val sherpaLocalTts = SherpaLocalTtsProvider()
    private var currentConfig: TtsConfig = TtsConfig.fromSharedPreferences(context)

    init {
        providers[TtsProvider.ALIYUN] = AliyunTtsProvider()
        providers[TtsProvider.BAIDU] = BaiduTtsProvider()
        providers[TtsProvider.XUNFEI] = XunfeiTtsProvider()
        providers[TtsProvider.MICROSOFT] = MicrosoftTtsProvider()
        providers[TtsProvider.VOLCENGINE] = VolcengineTtsProvider()
        providers[TtsProvider.SILICONFLOW] = SiliconFlowTtsProvider()
        providers[TtsProvider.SHERPA_LOCAL] = sherpaLocalTts
        providers[TtsProvider.ANDROID] = androidTts
    }

    /** 本地离线 TTS 模型管理器（下载/校验/启用），供设置页 UI 消费 */
    val localTtsManager: LocalTtsModelManager by lazy {
        LocalTtsModelManager.getInstance(context)
    }

    fun setProvider(provider: TtsProvider) {
        currentProvider = provider
        SecureLog.i("TtsService", "Switched TTS provider to ${provider.displayName}")
    }

    fun getCurrentProvider(): TtsProvider = currentProvider

    fun getAvailableProviders(): List<TtsProvider> = TtsProvider.entries.toList()

    fun updateConfig(config: TtsConfig) {
        currentConfig = config
        providers.forEach { (provider, providerInterface) ->
            if (providerInterface is ConfigurableTtsProvider) {
                providerInterface.updateConfig(config)
            }
        }
        SecureLog.i("TtsService", "TTS configuration updated")
    }

    fun getConfig(): TtsConfig = currentConfig

    suspend fun synthesize(text: String, voiceId: String? = null): String? = withContext(Dispatchers.IO) {
        try {
            if (currentProvider == TtsProvider.ANDROID && !androidTts.isInitialized()) {
                androidTts.initialize(context)
            }
            val provider = providers[currentProvider]
                ?: throw IllegalStateException("Provider ${currentProvider.name} not initialized")

            SecureLog.d("TtsService", "Synthesizing text with ${currentProvider.displayName}, length=${text.length}")
            val result = provider.synthesize(context, text, voiceId)

            if (result != null) {
                SecureLog.i("TtsService", "TTS synthesis successful: $result")
            } else {
                SecureLog.w("TtsService", "TTS synthesis returned null")
            }
            result
        } catch (e: Exception) {
            SecureLog.e("TtsService", "TTS synthesis failed", e)
            null
        }
    }

    fun getVoices(provider: TtsProvider = currentProvider): List<TtsVoice> {
        return providers[provider]?.getVoices() ?: emptyList()
    }

    suspend fun testProvider(provider: TtsProvider): Boolean = withContext(Dispatchers.IO) {
        try {
            val p = providers[provider] ?: return@withContext false

            if (p is ConfigurableTtsProvider) {
                p.updateConfig(currentConfig)
            }

            val isConfigured = currentConfig.isProviderConfigured(provider)
            if (!isConfigured && provider != TtsProvider.ANDROID) {
                SecureLog.w("TtsService", "Provider ${provider.displayName} not configured")
                return@withContext false
            }

            p.testConnection()
        } catch (e: Exception) {
            SecureLog.e("TtsService", "Test provider ${provider.displayName} failed", e)
            false
        }
    }

    suspend fun testWithSampleText(provider: TtsProvider = currentProvider, text: String = "你好，这是一个语音合成测试。"): String? {
        return try {
            if (provider == TtsProvider.ANDROID && !androidTts.isInitialized()) {
                androidTts.initialize(context)
            }

            val p = providers[provider] ?: return null
            if (p is ConfigurableTtsProvider) {
                p.updateConfig(currentConfig)
            }

            SecureLog.i("TtsService", "Testing TTS with sample text for ${provider.displayName}")
            p.synthesize(context, text, null)
        } catch (e: Exception) {
            SecureLog.e("TtsService", "Test synthesis failed", e)
            null
        }
    }

    companion object {
        @Volatile
        private var instance: TtsService? = null

        fun getInstance(context: Context): TtsService {
            return instance ?: synchronized(this) {
                instance ?: TtsService(context.applicationContext).also { instance = it }
            }
        }
    }
}
