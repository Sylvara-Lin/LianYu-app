package com.lianyu.ai.feature.localmodel

import java.io.File

internal data class LocalModelFileDeletionResult(
    val deleted: Boolean,
    val errorMessage: String? = null
)

internal object LocalModelFileDeletion {
    fun delete(
        file: File,
        deleteFile: (File) -> Boolean = { it.delete() }
    ): LocalModelFileDeletionResult {
        if (!file.exists()) {
            return LocalModelFileDeletionResult(deleted = true)
        }

        val deleteSucceeded = runCatching { deleteFile(file) }.getOrElse { error ->
            return LocalModelFileDeletionResult(
                deleted = false,
                errorMessage = "Could not delete local model file: ${error.message}"
            )
        }

        if (deleteSucceeded && !file.exists()) {
            return LocalModelFileDeletionResult(deleted = true)
        }

        if (!file.exists()) {
            return LocalModelFileDeletionResult(deleted = true)
        }

        return LocalModelFileDeletionResult(
            deleted = false,
            errorMessage = "Could not delete local model file. Close the app and try again."
        )
    }
}
