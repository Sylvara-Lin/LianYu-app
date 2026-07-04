package com.lianyu.ai.feature.localmodel

import android.content.Context
import java.io.File

sealed class LocalModel(
    val id: String,
    val displayName: String,
    val fileName: String,
    val downloadUrl: String,
    val sha256: String,
    val expectedBytes: Long
) {
    fun modelFile(context: Context): File {
        val root = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = File(root, "models")
        return File(dir, fileName)
    }

    data object Gemma4E2B : LocalModel(
        id = "gemma_4_e2b",
        displayName = "Gemma 4 E2B",
        fileName = "gemma-4-E2B-it.litertlm",
        downloadUrl = "https://modelscope.cn/models/litert-community/gemma-4-E2B-it-litert-lm/resolve/1bacc155f57965ae45832a9ccfe96cdfcb0e94e9/gemma-4-E2B-it.litertlm",
        sha256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
        expectedBytes = 2_588_147_712L
    )

    data object Gemma3_1B : LocalModel(
        id = "gemma_3_1b",
        displayName = "Gemma 3 1B Int4",
        fileName = "gemma3-1b-it-int4.litertlm",
        downloadUrl = "https://modelscope.cn/models/litert-community/Gemma3-1B-IT/resolve/ea05e64beb281629d682a58fdf681d0c5116f93f/gemma3-1b-it-int4.litertlm",
        sha256 = "1325ae366d31950f137c9c357b9fa89448b176d76998180c08ceaca78bba98be",
        expectedBytes = 584_417_280L
    )

    data object ShieldGemma2B : LocalModel(
        id = "shieldgemma_2b",
        displayName = "ShieldGemma 2B",
        fileName = "shieldgemma-2b.litertlm",
        downloadUrl = "",  // 手动部署：放入 models/ 目录
        sha256 = "",
        expectedBytes = 0L
    )
}

object LocalModelCatalog {
    val default: LocalModel = LocalModel.Gemma4E2B
    val all: List<LocalModel> = listOf(LocalModel.Gemma4E2B, LocalModel.Gemma3_1B)

    fun findById(modelId: String): LocalModel =
        all.find { it.id == modelId } ?: default
}
