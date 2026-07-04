package com.lianyu.ai.network.tts

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import androidx.core.net.toUri
import com.lianyu.ai.common.SecureLog
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 本地 TTS 模型状态（镜像 [LocalModelUiState]）。
 */
data class LocalTtsUiState(
    val model: LocalTtsModel = LocalTtsCatalog.default,
    val modelId: String = LocalTtsCatalog.default.id,
    val status: LocalTtsUiStatus = LocalTtsUiStatus.NOT_DOWNLOADED,
    val progressPercent: Int = 0,
    val currentFileIndex: Int = 0,
    val totalFiles: Int = 0,
    val errorMessage: String? = null
)

enum class LocalTtsUiStatus {
    NOT_DOWNLOADED,    // 文件缺失
    DOWNLOADING,       // 下载中
    READY,             // 文件就绪，未启用
    ENABLED,           // 已启用
    FAILED             // 失败
}

/**
 * 本地离线 TTS 模型管理器：下载 / 校验 / 启用。
 *
 * 镜像 [com.lianyu.ai.feature.localmodel.LocalModelManager] 的模式，
 * 核心差异：**多文件顺序下载**（VITS 需 model.onnx + tokens.txt + lexicon.txt）。
 *
 * 持久化到 DataStore `"local_tts_settings"`（见 [LocalTtsPreferences]）。
 */
class LocalTtsModelManager private constructor(private val context: Context) {

    private val appContext = context.applicationContext
    private val preferences = LocalTtsPreferences(appContext)
    private val downloadManager =
        appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var model: LocalTtsModel = LocalTtsCatalog.default

    private val _state = MutableStateFlow(LocalTtsUiState(model = model))
    val state: StateFlow<LocalTtsUiState> = _state.asStateFlow()

    private val pollingJobs = ConcurrentHashMap<String, Job>()

    init {
        scope.launch {
            preferences.selectedModelId.collect { modelId ->
                model = LocalTtsCatalog.findById(modelId)
                refresh()
            }
        }
        scope.launch {
            preferences.modelState(model.id).collect { prefs ->
                updateStateFromPreferences(prefs)
            }
        }
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        updateStateFromPreferences(preferences.modelState(model.id).first())
    }

    /**
     * 开始下载模型。多文件按 [LocalTtsModel.files] 顺序逐个下载。
     * 若文件已全部存在且校验通过，直接 READY。
     */
    suspend fun startDownload(modelId: String? = null) = withContext(Dispatchers.IO) {
        if (modelId != null) {
            model = LocalTtsCatalog.findById(modelId)
            preferences.selectModel(model.id)
        }
        val downloadModel = model
        if (downloadModel.isAllFilesPresent(appContext) && isAllFilesValid(downloadModel)) {
            preferences.setEnabled(downloadModel.id, false)
            preferences.setPendingAutoEnable(downloadModel.id, false)
            preferences.setDownloadId(downloadModel.id, null)
            preferences.setDownloadFileIndex(downloadModel.id, 0)
            updateStateFromPreferences(preferences.modelState(downloadModel.id).first())
            return@withContext
        }
        // 找到第一个缺失或校验失败的文件，从它开始下载
        val startIdx = downloadModel.files.indexOfFirst { mf ->
            val f = downloadModel.file(appContext, mf.fileName)
            !f.exists() || !isFileValid(f, mf)
        }.coerceAtLeast(0)
        preferences.setEnabled(downloadModel.id, false)
        preferences.setPendingAutoEnable(downloadModel.id, true)
        preferences.setDownloadFileIndex(downloadModel.id, startIdx)
        enqueueFileDownload(downloadModel, startIdx)
    }

    suspend fun cancelDownload() = withContext(Dispatchers.IO) {
        val prefs = preferences.modelState(model.id).first()
        prefs.downloadId?.let { downloadManager.remove(it) }
        preferences.setDownloadId(model.id, null)
        preferences.setPendingAutoEnable(model.id, false)
        preferences.setEnabled(model.id, false)
        preferences.setDownloadFileIndex(model.id, 0)
        pollingJobs.remove(model.id)?.cancel()
        // 删除部分下载的文件
        model.files.forEach { mf ->
            model.file(appContext, mf.fileName).takeIf { it.exists() }?.let {
                if (!isFileValid(it, mf)) it.delete()
            }
        }
        updateStateFromPreferences(preferences.modelState(model.id).first())
    }

    suspend fun selectModel(modelId: String) = withContext(Dispatchers.IO) {
        model = LocalTtsCatalog.findById(modelId)
        preferences.selectModel(model.id)
        if (!model.isAllFilesPresent(appContext)) {
            preferences.setEnabled(model.id, false)
            preferences.setPendingAutoEnable(model.id, false)
        }
        updateStateFromPreferences(preferences.modelState(model.id).first())
    }

    suspend fun enable() = withContext(Dispatchers.IO) {
        if (!model.isAllFilesPresent(appContext)) {
            _state.value = LocalTtsUiState(
                model = model,
                modelId = model.id,
                status = LocalTtsUiStatus.FAILED,
                errorMessage = "模型文件缺失，请先下载或手动放置"
            )
            return@withContext
        }
        preferences.setEnabled(model.id, true)
        updateStateFromPreferences(preferences.modelState(model.id).first())
        SecureLog.i(TAG, "本地 TTS 模型已启用: ${model.displayName}")
    }

    suspend fun disable() = withContext(Dispatchers.IO) {
        preferences.setEnabled(model.id, false)
        updateStateFromPreferences(preferences.modelState(model.id).first())
        SecureLog.i(TAG, "本地 TTS 模型已禁用")
    }

    suspend fun deleteDownloadedModel() = withContext(Dispatchers.IO) {
        cancelDownload()
        model.modelDir(appContext).deleteRecursively()
        updateStateFromPreferences(preferences.modelState(model.id).first())
        SecureLog.i(TAG, "已删除本地 TTS 模型文件: ${model.displayName}")
    }

    fun close() {
        pollingJobs.values.forEach { it.cancel() }
        pollingJobs.clear()
        scope.cancel()
    }

    // ── 内部：多文件下载链 ──

    private suspend fun enqueueFileDownload(downloadModel: LocalTtsModel, fileIndex: Int) {
        if (fileIndex >= downloadModel.files.size) {
            // 所有文件下载完成
            preferences.setDownloadId(downloadModel.id, null)
            preferences.setDownloadFileIndex(downloadModel.id, 0)
            if (preferences.modelState(downloadModel.id).first().pendingAutoEnable) {
                preferences.setEnabled(downloadModel.id, true)
                preferences.setPendingAutoEnable(downloadModel.id, false)
            }
            updateStateFromPreferences(preferences.modelState(downloadModel.id).first())
            SecureLog.i(TAG, "所有模型文件下载完成: ${downloadModel.displayName}")
            return
        }
        val modelFile = downloadModel.files[fileIndex]
        val targetFile = downloadModel.file(appContext, modelFile.fileName)
        targetFile.parentFile?.mkdirs()
        if (targetFile.exists()) targetFile.delete()

        val downloadUri = validatedDownloadUri(modelFile.downloadUrl)
        if (downloadUri == null) {
            preferences.setPendingAutoEnable(downloadModel.id, false)
            preferences.setEnabled(downloadModel.id, false)
            preferences.setDownloadId(downloadModel.id, null)
            _state.value = LocalTtsUiState(
                model = downloadModel,
                modelId = downloadModel.id,
                status = LocalTtsUiStatus.FAILED,
                currentFileIndex = fileIndex,
                totalFiles = downloadModel.files.size,
                errorMessage = "${modelFile.fileName} 未配置下载源，请手动放置文件"
            )
            return
        }
        val request = DownloadManager.Request(downloadUri)
            .setTitle("${downloadModel.displayName} (${fileIndex + 1}/${downloadModel.files.size})")
            .setDescription("Downloading ${modelFile.fileName}")
            .setDestinationUri(targetFile.toUri())
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        val downloadId = downloadManager.enqueue(request)
        preferences.setDownloadId(downloadModel.id, downloadId)
        preferences.setDownloadFileIndex(downloadModel.id, fileIndex)
        _state.value = LocalTtsUiState(
            model = downloadModel,
            modelId = downloadModel.id,
            status = LocalTtsUiStatus.DOWNLOADING,
            progressPercent = 0,
            currentFileIndex = fileIndex,
            totalFiles = downloadModel.files.size
        )
        startPolling(downloadId, downloadModel, fileIndex)
    }

    private fun startPolling(downloadId: Long, downloadModel: LocalTtsModel, fileIndex: Int) {
        if (pollingJobs[downloadModel.id]?.isActive == true) return
        pollingJobs[downloadModel.id] = scope.launch {
            try {
                while (true) {
                    val shouldContinue = pollDownload(downloadId, downloadModel, fileIndex)
                    if (!shouldContinue) break
                    delay(1_000)
                }
            } finally {
                pollingJobs.remove(downloadModel.id)
            }
        }
    }

    private suspend fun pollDownload(
        downloadId: Long,
        downloadModel: LocalTtsModel,
        fileIndex: Int
    ): Boolean {
        val progress = queryDownload(downloadId)
        if (progress == null) {
            // DownloadManager 查询失败，继续等待
            return true
        }
        when (progress.status) {
            DownloadManager.STATUS_SUCCESSFUL -> {
                handleFileDownloadComplete(downloadId, downloadModel, fileIndex)
                return false
            }
            DownloadManager.STATUS_FAILED -> {
                preferences.setDownloadId(downloadModel.id, null)
                preferences.setPendingAutoEnable(downloadModel.id, false)
                preferences.setEnabled(downloadModel.id, false)
                _state.value = LocalTtsUiState(
                    model = downloadModel,
                    modelId = downloadModel.id,
                    status = LocalTtsUiStatus.FAILED,
                    progressPercent = progress.percent,
                    currentFileIndex = fileIndex,
                    totalFiles = downloadModel.files.size,
                    errorMessage = "${downloadModel.files[fileIndex].fileName} 下载失败 (code: ${progress.reason})"
                )
                return false
            }
            else -> {
                _state.value = LocalTtsUiState(
                    model = downloadModel,
                    modelId = downloadModel.id,
                    status = LocalTtsUiStatus.DOWNLOADING,
                    progressPercent = progress.percent,
                    currentFileIndex = fileIndex,
                    totalFiles = downloadModel.files.size
                )
                return true
            }
        }
    }

    private suspend fun handleFileDownloadComplete(
        downloadId: Long,
        downloadModel: LocalTtsModel,
        fileIndex: Int
    ) {
        val modelFile = downloadModel.files[fileIndex]
        val file = downloadModel.file(appContext, modelFile.fileName)
        try {
            if (!isFileValid(file, modelFile)) {
                file.delete()
                preferences.setDownloadId(downloadModel.id, null)
                preferences.setPendingAutoEnable(downloadModel.id, false)
                preferences.setEnabled(downloadModel.id, false)
                _state.value = LocalTtsUiState(
                    model = downloadModel,
                    modelId = downloadModel.id,
                    status = LocalTtsUiStatus.FAILED,
                    currentFileIndex = fileIndex,
                    totalFiles = downloadModel.files.size,
                    errorMessage = "${modelFile.fileName} 校验失败"
                )
                return
            }
            // 当前文件 OK，继续下一个
            preferences.setDownloadId(downloadModel.id, null)
            val nextIndex = fileIndex + 1
            if (nextIndex >= downloadModel.files.size) {
                // 全部完成
                preferences.setDownloadFileIndex(downloadModel.id, 0)
                if (preferences.modelState(downloadModel.id).first().pendingAutoEnable) {
                    preferences.setEnabled(downloadModel.id, true)
                    preferences.setPendingAutoEnable(downloadModel.id, false)
                }
                updateStateFromPreferences(preferences.modelState(downloadModel.id).first())
                SecureLog.i(TAG, "所有模型文件下载完成: ${downloadModel.displayName}")
            } else {
                enqueueFileDownload(downloadModel, nextIndex)
            }
        } catch (e: Exception) {
            preferences.setDownloadId(downloadModel.id, null)
            preferences.setPendingAutoEnable(downloadModel.id, false)
            preferences.setEnabled(downloadModel.id, false)
            _state.value = LocalTtsUiState(
                model = downloadModel,
                modelId = downloadModel.id,
                status = LocalTtsUiStatus.FAILED,
                currentFileIndex = fileIndex,
                totalFiles = downloadModel.files.size,
                errorMessage = e.message ?: "校验异常"
            )
        }
    }

    private fun updateStateFromPreferences(prefs: LocalTtsPreferencesState) {
        val isFilePresent = model.isAllFilesPresent(appContext)
        val status = when {
            prefs.isEnabled && isFilePresent -> LocalTtsUiStatus.ENABLED
            prefs.downloadId != null -> LocalTtsUiStatus.DOWNLOADING
            isFilePresent -> LocalTtsUiStatus.READY
            else -> LocalTtsUiStatus.NOT_DOWNLOADED
        }
        val progress = if (prefs.downloadId != null) {
            queryDownload(prefs.downloadId)?.percent ?: 0
        } else 0
        _state.value = LocalTtsUiState(
            model = model,
            modelId = model.id,
            status = status,
            progressPercent = progress,
            currentFileIndex = prefs.downloadFileIndex,
            totalFiles = model.files.size
        )
    }

    private fun queryDownload(downloadId: Long): DownloadProgress? {
        val query = DownloadManager.Query().setFilterById(downloadId)
        return downloadManager.query(query)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            DownloadProgress(
                status = cursor.intColumn(DownloadManager.COLUMN_STATUS),
                reason = cursor.intColumn(DownloadManager.COLUMN_REASON),
                downloadedBytes = cursor.longColumn(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),
                totalBytes = cursor.longColumn(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            )
        }
    }

    // ── 校验 ──

    private fun isAllFilesValid(model: LocalTtsModel): Boolean =
        model.files.all { mf -> isFileValid(model.file(appContext, mf.fileName), mf) }

    private fun isFileValid(file: File, modelFile: LocalTtsModelFile): Boolean {
        if (!file.exists()) return false
        // 大小校验（expectedBytes=0 跳过）
        if (modelFile.expectedBytes > 0L) {
            val min = modelFile.expectedBytes - 50_000_000L
            val max = modelFile.expectedBytes + 50_000_000L
            if (file.length() !in min..max) return false
        }
        // SHA-256 校验（空 sha256 跳过）
        if (modelFile.sha256.isNotBlank()) {
            return sha256(file).equals(modelFile.sha256, ignoreCase = true)
        }
        return true
    }

    private fun validatedDownloadUri(url: String): Uri? {
        if (url.isBlank()) return null
        val uri = Uri.parse(url)
        if (uri.scheme != "https") return null
        val host = uri.host?.lowercase() ?: return null
        // sherpa 模型托管在 huggingface.co / modelscope.cn / github.com
        if (host in ALLOWED_HOSTS) return uri
        return null
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private data class DownloadProgress(
        val status: Int,
        val reason: Int,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) {
        val percent: Int
            get() = if (totalBytes > 0L) {
                ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
            } else 0
    }

    companion object {
        private const val TAG = "LocalTtsModelManager"
        private val ALLOWED_HOSTS = setOf("huggingface.co", "modelscope.cn", "github.com")

        @Volatile
        private var instance: LocalTtsModelManager? = null

        fun getInstance(context: Context): LocalTtsModelManager {
            return instance ?: synchronized(this) {
                instance ?: LocalTtsModelManager(context.applicationContext).also { instance = it }
            }
        }
    }
}

private fun Cursor.intColumn(columnName: String): Int =
    getInt(getColumnIndexOrThrow(columnName))

private fun Cursor.longColumn(columnName: String): Long =
    getLong(getColumnIndexOrThrow(columnName))
