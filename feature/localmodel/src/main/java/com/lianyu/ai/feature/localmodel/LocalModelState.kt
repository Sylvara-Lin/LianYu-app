package com.lianyu.ai.feature.localmodel

enum class LocalModelUiStatus {
    NOT_DOWNLOADED,
    DOWNLOADING,
    DOWNLOADED,
    ENABLED,
    FAILED
}

data class LocalModelUiState(
    val modelId: String = LocalModel.Gemma4E2B.id,
    val model: LocalModel = LocalModel.Gemma4E2B,
    val isSelected: Boolean = false,
    val status: LocalModelUiStatus = LocalModelUiStatus.NOT_DOWNLOADED,
    val progressPercent: Int = 0,
    val errorMessage: String? = null
)

object LocalModelStateResolver {
    fun resolve(
        isFilePresent: Boolean,
        isEnabled: Boolean,
        downloadId: Long?,
        progress: Int?,
        error: String?
    ): LocalModelUiState {
        val clampedProgress = progress?.coerceIn(0, 100) ?: 0
        return when {
            error != null -> LocalModelUiState(
                status = LocalModelUiStatus.FAILED,
                errorMessage = error
            )
            downloadId != null -> LocalModelUiState(
                status = LocalModelUiStatus.DOWNLOADING,
                progressPercent = clampedProgress
            )
            isFilePresent && isEnabled -> LocalModelUiState(
                status = LocalModelUiStatus.ENABLED,
                progressPercent = 100
            )
            isFilePresent -> LocalModelUiState(
                status = LocalModelUiStatus.DOWNLOADED,
                progressPercent = 100
            )
            else -> LocalModelUiState(status = LocalModelUiStatus.NOT_DOWNLOADED)
        }
    }
}
