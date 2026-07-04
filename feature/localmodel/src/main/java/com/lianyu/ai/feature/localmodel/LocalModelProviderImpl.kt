package com.lianyu.ai.feature.localmodel

import android.content.Context
import com.lianyu.ai.domain.LocalModelProvider
import com.lianyu.ai.domain.ModelInfo
import com.lianyu.ai.domain.ModelState
import com.lianyu.ai.domain.ModelStatus
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/**
 * Adapter bridging domain LocalModelProvider to LocalAiService + LocalModelManager.
 */
class LocalModelProviderImpl(context: Context) : LocalModelProvider {

    private val appContext = context.applicationContext
    private val manager = LocalModelManager(appContext)
    private val aiService: LocalAiService? by lazy {
        try { LocalAiService.getInstance(appContext) } catch (_: Exception) { null }
    }

    override suspend fun isAvailable(): Boolean {
        val state = manager.state.value
        return LocalAiService.isNativeLibrarySupported &&
            state.status == LocalModelUiStatus.ENABLED &&
            LocalModelCatalog.all.any { it.modelFile(appContext).exists() }
    }

    override suspend fun generateResponse(prompt: String, context: String): String {
        val ai = aiService ?: throw IllegalStateException("Local AI service not available")
        ai.acquire()
        return try {
            withContext(Dispatchers.IO) {
                ai.generate(
                    systemPrompt = context,
                    historyPrompt = "",
                    userPrompt = prompt
                )
            }
        } finally {
            // [M12 FIX] close 已改为 suspend（内部走 mutex.withLock 保护 engine 关闭）
            ai.close()
        }
    }

    override fun getModelName(): String =
        aiService?.activeModel?.displayName ?: "None"

    override fun getModelVersion(): String =
        aiService?.activeModel?.id ?: "none"

    override fun getAvailableModels(): List<ModelInfo> =
        LocalModelCatalog.all.map { model ->
            ModelInfo(
                id = model.id,
                displayName = model.displayName,
                fileName = model.fileName,
                downloadUrl = model.downloadUrl,
                expectedBytes = model.expectedBytes
            )
        }

    override fun getModelState(modelId: String): ModelState {
        val managerState = manager.state.value
        val model = LocalModelCatalog.findById(modelId)
            ?: return ModelState(
                modelId = modelId,
                displayName = "Unknown",
                downloadUrl = "",
                expectedBytes = 0L,
                isSelected = false,
                status = ModelStatus.NOT_DOWNLOADED
            )
        val fileExists = model.modelFile(appContext).exists()
        val isSelected = managerState.modelId == modelId
        return ModelState(
            modelId = modelId,
            displayName = model.displayName,
            downloadUrl = model.downloadUrl,
            expectedBytes = model.expectedBytes,
            isSelected = isSelected,
            status = when {
                isSelected && managerState.status == LocalModelUiStatus.ENABLED -> ModelStatus.ENABLED
                isSelected && managerState.status == LocalModelUiStatus.DOWNLOADING -> ModelStatus.DOWNLOADING
                isSelected && managerState.status == LocalModelUiStatus.FAILED -> ModelStatus.FAILED
                fileExists -> ModelStatus.DOWNLOADED
                else -> ModelStatus.NOT_DOWNLOADED
            },
            progressPercent = managerState.progressPercent,
            errorMessage = managerState.errorMessage
        )
    }

    override fun getAllModelStates(): Map<String, ModelState> =
        getAvailableModels().associate { it.id to getModelState(it.id) }

    override suspend fun downloadModel(modelId: String) {
        manager.startDownload(modelId)
    }

    override suspend fun cancelDownload(modelId: String) {
        manager.cancelDownload()
    }

    override suspend fun enableModel(modelId: String) {
        manager.selectModel(modelId)
        manager.enable()
    }

    override suspend fun disableModel(modelId: String) {
        manager.disable()
    }

    override suspend fun deleteModel(modelId: String) {
        manager.deleteDownloadedModel()
    }
}
