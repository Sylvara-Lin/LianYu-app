package com.lianyu.ai.network.stt

import android.content.Context
import com.lianyu.ai.common.SecureLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class SttService(private val context: Context) {

    private val providers = mutableMapOf<SttProvider, SttProviderInterface>()
    private var currentProvider: SttProvider = SttProvider.ANDROID
    private val androidStt = AndroidSttProvider()
    private val siliconflowStt = SiliconFlowSttProvider()

    init {
        providers[SttProvider.ANDROID] = androidStt
        providers[SttProvider.SILICONFLOW] = siliconflowStt
    }

    fun setProvider(provider: SttProvider) {
        currentProvider = provider
        SecureLog.i("SttService", "Switched STT provider to ${provider.displayName}")
    }

    fun getCurrentProvider(): SttProvider = currentProvider

    fun getAvailableProviders(): List<SttProvider> = SttProvider.entries.toList()

    suspend fun recognize(audioPath: String): String? = withContext(Dispatchers.IO) {
        try {
            val audioFile = File(audioPath)
            if (!audioFile.exists()) {
                SecureLog.e("SttService", "Audio file not found: $audioPath")
                return@withContext null
            }

            if (currentProvider == SttProvider.ANDROID && !androidStt.isInitialized()) {
                androidStt.initialize(context)
            }
            if (currentProvider == SttProvider.SILICONFLOW && !siliconflowStt.isInitialized()) {
                siliconflowStt.initialize(context)
            }

            val provider = providers[currentProvider]
                ?: throw IllegalStateException("Provider ${currentProvider.name} not initialized")

            SecureLog.d("SttService", "Recognizing audio with ${currentProvider.displayName}, file=$audioPath")
            val result = provider.recognize(context, audioPath)

            if (result != null && result.isNotBlank()) {
                SecureLog.i("SttService", "STT recognition successful: ${result.take(50)}...")
            } else {
                SecureLog.w("SttService", "STT recognition returned empty or null")
            }
            result?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            SecureLog.e("SttService", "STT recognition failed", e)
            null
        }
    }

    suspend fun recognizeWithFileUpload(audioPath: String, mimeType: String = "audio/mp4"): String? = withContext(Dispatchers.IO) {
        try {
            val audioFile = File(audioPath)
            if (!audioFile.exists()) {
                SecureLog.e("SttService", "Audio file not found for upload: $audioPath")
                return@withContext null
            }

            val provider = providers[currentProvider]
                ?: throw IllegalStateException("Provider ${currentProvider.name} not initialized")

            SecureLog.d("SttService", "Recognizing audio via file upload with ${currentProvider.displayName}")
            val result = provider.recognizeFromFile(context, audioPath, mimeType)

            if (result != null && result.isNotBlank()) {
                SecureLog.i("SttService", "STT file upload recognition successful: ${result.take(50)}...")
            } else {
                SecureLog.w("SttService", "STT file upload recognition returned empty or null")
            }
            result?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            SecureLog.e("SttService", "STT file upload recognition failed", e)
            null
        }
    }

    suspend fun testProvider(provider: SttProvider): Boolean = withContext(Dispatchers.IO) {
        try {
            val p = providers[provider] ?: return@withContext false
            p.testConnection()
        } catch (e: Exception) {
            SecureLog.e("SttService", "Test provider ${provider.displayName} failed", e)
            false
        }
    }

    companion object {
        @Volatile
        private var instance: SttService? = null

        fun getInstance(context: Context): SttService {
            return instance ?: synchronized(this) {
                instance ?: SttService(context.applicationContext).also { instance = it }
            }
        }
    }
}
