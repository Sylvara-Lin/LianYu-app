package com.lianyu.ai.domain

enum class ModelStatus {
    NOT_DOWNLOADED,
    DOWNLOADING,
    DOWNLOADED,
    ENABLED,
    FAILED
}

data class ModelState(
    val modelId: String = "",
    val displayName: String = "",
    val downloadUrl: String = "",
    val expectedBytes: Long = 0L,
    val isSelected: Boolean = false,
    val status: ModelStatus = ModelStatus.NOT_DOWNLOADED,
    val progressPercent: Int = 0,
    val errorMessage: String? = null
)
