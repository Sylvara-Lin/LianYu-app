package com.lianyu.ai.network.tts

import android.content.Context
import java.io.File

/**
 * 本地离线 TTS 模型元数据。多文件支持（VITS 需 model.onnx + tokens.txt + lexicon.txt）。
 *
 * 镜像 [com.lianyu.ai.feature.localmodel.LocalModel] 的 sealed class 模式，
 * 但 sherpa-onnx 一个模型由多个文件组成，故用 [files] 列表。
 *
 * @property id 唯一标识，用于 DataStore key
 * @property displayName UI 展示名
 * @property files 模型文件列表（model.onnx / tokens.txt / lexicon.txt 等）
 * @property numSpeakers 说话人数量（aishell3=174，单音色=1）
 * @property modelType sherpa OfflineTts 子模型类型
 */
sealed class LocalTtsModel(
    val id: String,
    val displayName: String,
    val files: List<LocalTtsModelFile>,
    val numSpeakers: Int,
    val modelType: LocalTtsModelType
) {
    /**
     * 模型存储目录：`<filesDir>/models/tts/<id>/`
     * 与 [com.lianyu.ai.feature.localmodel.LocalModel.modelFile] 同根（`models/` 子目录），
     * 但额外加 `tts/` 前缀避免与 LLM 模型文件混淆。
     */
    fun modelDir(context: Context): File {
        val root = context.getExternalFilesDir(null) ?: context.filesDir
        return File(File(root, "models"), "tts/$id").also { it.mkdirs() }
    }

    /** 取模型目录下某个文件的绝对路径 */
    fun file(context: Context, fileName: String): File = File(modelDir(context), fileName)

    /** 所有文件是否都已存在（不校验内容） */
    fun isAllFilesPresent(context: Context): Boolean =
        files.all { file(context, it.fileName).exists() }

    /** model.onnx 的文件名（约定 [files] 第一个元素是主模型） */
    val modelFileName: String get() = files.first { it.isMainModel }.fileName

    /** tokens.txt 文件名 */
    val tokensFileName: String get() = files.first { it.role == LocalTtsFileRole.TOKENS }.fileName

    /** lexicon.txt 文件名（可能不存在，如 Kokoro 用 dictDir） */
    val lexiconFileName: String? get() = files.firstOrNull { it.role == LocalTtsFileRole.LEXICON }?.fileName

    /**
     * VITS 中文多音色模型（aishell3, 174 speakers）。
     *
     * 占位条目：downloadUrl / sha256 留空，用户需手动放置文件或后续填入下载源。
     * 文件放入 `<filesDir>/models/tts/vits_zh_aishell3/` 后 refresh 即可启用。
     */
    data object Aishell3 : LocalTtsModel(
        id = "vits_zh_aishell3",
        displayName = "VITS 中文 aishell3 (174音色)",
        numSpeakers = 174,
        modelType = LocalTtsModelType.VITS,
        files = listOf(
            LocalTtsModelFile(
                fileName = "model.onnx",
                downloadUrl = "",  // 占位：待填下载源
                sha256 = "",       // 占位：待填 SHA-256
                expectedBytes = 1_200_000_000L,
                role = LocalTtsFileRole.MAIN_MODEL,
                isMainModel = true
            ),
            LocalTtsModelFile(
                fileName = "tokens.txt",
                downloadUrl = "",
                sha256 = "",
                expectedBytes = 0L,
                role = LocalTtsFileRole.TOKENS,
                isMainModel = false
            ),
            LocalTtsModelFile(
                fileName = "lexicon.txt",
                downloadUrl = "",
                sha256 = "",
                expectedBytes = 0L,
                role = LocalTtsFileRole.LEXICON,
                isMainModel = false
            )
        )
    )

    /**
     * 自定义模型兜底：用户手动放置任意 sherpa-onnx VITS 模型文件。
     * 文件放入 `<filesDir>/models/tts/custom/` 后 refresh 即可启用。
     * 单音色。
     */
    data object Custom : LocalTtsModel(
        id = "custom",
        displayName = "自定义 VITS 模型",
        numSpeakers = 1,
        modelType = LocalTtsModelType.VITS,
        files = listOf(
            LocalTtsModelFile(
                fileName = "model.onnx",
                downloadUrl = "",
                sha256 = "",
                expectedBytes = 0L,
                role = LocalTtsFileRole.MAIN_MODEL,
                isMainModel = true
            ),
            LocalTtsModelFile(
                fileName = "tokens.txt",
                downloadUrl = "",
                sha256 = "",
                expectedBytes = 0L,
                role = LocalTtsFileRole.TOKENS,
                isMainModel = false
            ),
            LocalTtsModelFile(
                fileName = "lexicon.txt",
                downloadUrl = "",
                sha256 = "",
                expectedBytes = 0L,
                role = LocalTtsFileRole.LEXICON,
                isMainModel = false
            )
        )
    )
}

/** sherpa OfflineTts 子模型类型，决定用哪个 ModelConfig */
enum class LocalTtsModelType {
    VITS,    // OfflineTtsVitsModelConfig
    MATCHA,  // OfflineTtsMatchaModelConfig
    KOKORO   // OfflineTtsKokoroModelConfig
}

/** 模型文件在推理 config 中的角色 */
enum class LocalTtsFileRole {
    MAIN_MODEL,  // model.onnx / acousticModel
    TOKENS,      // tokens.txt
    LEXICON,     // lexicon.txt
    VOCODER,     // matcha 的 vocoder.onnx
    VOICES,      // kokoro 的 voices.bin
    DATA_DIR     // espeak-ng-data 目录
}

/**
 * 单个模型文件的元数据。
 *
 * @property fileName 文件名（相对 modelDir）
 * @property downloadUrl 下载 URL；空串 = 不自动下载，靠手动放置
 * @property sha256 期望 SHA-256；空串 = 跳过 SHA 校验（仅检查大小）
 * @property expectedBytes 期望文件大小（字节）；0 = 跳过大小校验
 * @property role 在推理 config 中的角色
 * @property isMainModel 是否是主模型文件（model.onnx）
 */
data class LocalTtsModelFile(
    val fileName: String,
    val downloadUrl: String,
    val sha256: String,
    val expectedBytes: Long,
    val role: LocalTtsFileRole = LocalTtsFileRole.MAIN_MODEL,
    val isMainModel: Boolean = false
)

object LocalTtsCatalog {
    val default: LocalTtsModel = LocalTtsModel.Aishell3
    val all: List<LocalTtsModel> = listOf(LocalTtsModel.Aishell3, LocalTtsModel.Custom)

    fun findById(modelId: String): LocalTtsModel =
        all.find { it.id == modelId } ?: default
}
