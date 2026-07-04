package com.lianyu.ai.domain

/**
 * Provides access to the on-device AI model.
 * Implemented by feature:localmodel.
 */
interface LocalModelProvider {
    suspend fun isAvailable(): Boolean
    suspend fun generateResponse(prompt: String, context: String): String
    fun getModelName(): String
    fun getModelVersion(): String

    /** Model management — consumed by feature:settings */
    fun getAvailableModels(): List<ModelInfo>
    fun getModelState(modelId: String): ModelState
    fun getAllModelStates(): Map<String, ModelState>
    suspend fun downloadModel(modelId: String)
    suspend fun cancelDownload(modelId: String)
    suspend fun enableModel(modelId: String)
    suspend fun disableModel(modelId: String)
    suspend fun deleteModel(modelId: String)
}
