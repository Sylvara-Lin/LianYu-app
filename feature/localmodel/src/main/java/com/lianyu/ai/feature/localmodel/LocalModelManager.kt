package com.lianyu.ai.feature.localmodel

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import androidx.core.net.toUri
import com.lianyu.ai.common.localmodel.LocalModelPreferencesState
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

class LocalModelManager(context: Context) {
    private val appContext = context.applicationContext
    val allModels = LocalModelCatalog.all
    private var model: LocalModel = LocalModelCatalog.default
    private val preferences = LocalModelPreferences(appContext)
    private val downloadManager =
        appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(LocalModelUiState(model = model))
    val state: StateFlow<LocalModelUiState> = _state.asStateFlow()

    private val pollingJobs = ConcurrentHashMap<String, Job>()

    init {
        scope.launch {
            preferences.selectedModelId.collect { modelId ->
                model = LocalModelCatalog.findById(modelId)
                refresh()
            }
        }
        scope.launch {
            preferences.state.collect { prefs ->
                updateStateFromPreferences(
                    prefs,
                    preferences.getModelDownloadId(model.id).first()
                )
            }
        }
        allModels.forEach { downloadModel ->
            scope.launch {
                preferences.getModelDownloadId(downloadModel.id).collect { downloadId ->
                    if (downloadModel.id == model.id) {
                        updateStateFromPreferences(preferences.state.first(), downloadId)
                    }
                    if (downloadId != null) {
                        startPolling(downloadId, downloadModel)
                    }
                }
            }
        }
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        updateStateFromPreferences(
            preferences.state.first(),
            preferences.getModelDownloadId(model.id).first()
        )
    }

    suspend fun startDownload(modelId: String? = null) = withContext(Dispatchers.IO) {
        if (modelId != null) {
            model = LocalModelCatalog.findById(modelId)
            preferences.selectModel(model.id)
        }

        val downloadModel = model
        val file = downloadModel.modelFile(appContext)
        if (file.exists()) {
            if (isDownloadedFileValid(file, downloadModel)) {
                preferences.setModelDownloadId(downloadModel.id, null)
                preferences.setPendingAutoEnable(false)
                preferences.setEnabled(true)
                updateStateFromPreferences(preferences.state.first(), null)
                return@withContext
            }
            file.delete()
        }

        file.parentFile?.mkdirs()
        preferences.setEnabled(false)
        preferences.setPendingAutoEnable(true)

        val downloadUri = validatedDownloadUri(downloadModel) ?: run {
            preferences.setEnabled(false)
            preferences.setPendingAutoEnable(false)
            _state.value = LocalModelUiState(
                model = downloadModel,
                modelId = downloadModel.id,
                status = LocalModelUiStatus.FAILED,
                errorMessage = "Model download source is not trusted."
            )
            return@withContext
        }

        val request = DownloadManager.Request(downloadUri)
            .setTitle(downloadModel.displayName)
            .setDescription("Downloading on-device text model")
            .setDestinationUri(file.toUri())
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

        val downloadId = downloadManager.enqueue(request)
        preferences.setModelDownloadId(downloadModel.id, downloadId)
        _state.value = LocalModelUiState(
            model = downloadModel,
            modelId = downloadModel.id,
            status = LocalModelUiStatus.DOWNLOADING,
            progressPercent = 0
        )
        startPolling(downloadId, downloadModel)
    }

    suspend fun cancelDownload() = withContext(Dispatchers.IO) {
        preferences.getModelDownloadId(model.id).first()?.let { downloadManager.remove(it) }
        preferences.setModelDownloadId(model.id, null)
        preferences.setPendingAutoEnable(false)
        preferences.setEnabled(false)
        model.modelFile(appContext).delete()
        pollingJobs.remove(model.id)?.cancel()
        updateStateFromPreferences(preferences.state.first(), null)
    }

    suspend fun selectModel(modelId: String) = withContext(Dispatchers.IO) {
        model = LocalModelCatalog.findById(modelId)
        preferences.selectModel(model.id)
        LocalAiService.getInstance(appContext).setActiveModel(model)
        if (!model.modelFile(appContext).exists()) {
            preferences.setEnabled(false)
            preferences.setPendingAutoEnable(false)
        }
        updateStateFromPreferences(
            preferences.state.first(),
            preferences.getModelDownloadId(model.id).first()
        )
    }

    suspend fun enable() = withContext(Dispatchers.IO) {
        if (!model.modelFile(appContext).exists()) {
            _state.value = LocalModelUiState(
                model = model,
                modelId = model.id,
                status = LocalModelUiStatus.FAILED,
                errorMessage = "Model file is missing. Download ${model.displayName} again."
            )
            return@withContext
        }
        LocalAiService.getInstance(appContext).setActiveModel(model)
        preferences.setEnabled(true)
        updateStateFromPreferences(preferences.state.first(), null)
    }

    suspend fun disable() = withContext(Dispatchers.IO) {
        preferences.setEnabled(false)
        // [M12 FIX] shutdownEngine 已改为 suspend（内部走 mutex.withLock 保护 engine）
        LocalAiService.getInstance(appContext).shutdownEngine()
        updateStateFromPreferences(
            preferences.state.first(),
            preferences.getModelDownloadId(model.id).first()
        )
    }

    suspend fun deleteDownloadedModel() = withContext(Dispatchers.IO) {
        preferences.getModelDownloadId(model.id).first()?.let { downloadManager.remove(it) }
        preferences.setModelDownloadId(model.id, null)
        preferences.setPendingAutoEnable(false)
        preferences.setEnabled(false)
        // [M12 FIX] shutdownEngine 已改为 suspend
        LocalAiService.getInstance(appContext).shutdownEngine()
        val deletion = LocalModelFileDeletion.delete(model.modelFile(appContext))
        pollingJobs.remove(model.id)?.cancel()
        if (!deletion.deleted) {
            _state.value = LocalModelUiState(
                model = model,
                modelId = model.id,
                status = LocalModelUiStatus.FAILED,
                errorMessage = deletion.errorMessage
            )
            return@withContext
        }
        updateStateFromPreferences(preferences.state.first(), null)
    }

    fun close() {
        pollingJobs.values.forEach { it.cancel() }
        pollingJobs.clear()
        scope.cancel()
    }

    private fun startPolling(downloadId: Long, downloadModel: LocalModel) {
        if (pollingJobs[downloadModel.id]?.isActive == true) return
        pollingJobs[downloadModel.id] = scope.launch {
            try {
                while (true) {
                    val shouldContinue = pollDownload(downloadId, downloadModel)
                    if (!shouldContinue) break
                    delay(1_000)
                }
            } finally {
                pollingJobs.remove(downloadModel.id)
            }
        }
    }

    private suspend fun pollDownload(downloadId: Long, downloadModel: LocalModel): Boolean {
        val progress = queryDownload(downloadId)
        if (progress == null) {
            if (downloadModel.id == model.id) {
                val prefs = preferences.state.first()
                _state.value = LocalModelStateResolver.resolve(
                    isFilePresent = downloadModel.modelFile(appContext).exists(),
                    isEnabled = prefs.isGemmaEnabled,
                    downloadId = downloadId,
                    progress = 0,
                    error = null
                ).copy(model = downloadModel, modelId = downloadModel.id)
            }
            return true
        }

        when (progress.status) {
            DownloadManager.STATUS_SUCCESSFUL -> {
                handleDownloadComplete(downloadModel)
                return false
            }
            DownloadManager.STATUS_FAILED -> {
                preferences.setModelDownloadId(downloadModel.id, null)
                if (downloadModel.id == model.id) {
                    preferences.setPendingAutoEnable(false)
                    preferences.setEnabled(false)
                    _state.value = LocalModelUiState(
                        model = downloadModel,
                        modelId = downloadModel.id,
                        status = LocalModelUiStatus.FAILED,
                        progressPercent = progress.percent,
                        errorMessage = "Download failed. Reason code: ${progress.reason}"
                    )
                }
                return false
            }
            else -> {
                if (downloadModel.id == model.id) {
                    val prefs = preferences.state.first()
                    _state.value = LocalModelStateResolver.resolve(
                        isFilePresent = downloadModel.modelFile(appContext).exists(),
                        isEnabled = prefs.isGemmaEnabled,
                        downloadId = downloadId,
                        progress = progress.percent,
                        error = null
                    ).copy(model = downloadModel, modelId = downloadModel.id)
                }
                return true
            }
        }
    }

    private suspend fun handleDownloadComplete(downloadModel: LocalModel) {
        val file = downloadModel.modelFile(appContext)
        try {
            if (!isDownloadedFileValid(file, downloadModel)) {
                file.delete()
                preferences.setModelDownloadId(downloadModel.id, null)
                if (downloadModel.id == model.id) {
                    preferences.setPendingAutoEnable(false)
                    preferences.setEnabled(false)
                    _state.value = LocalModelUiState(
                        model = downloadModel,
                        modelId = downloadModel.id,
                        status = LocalModelUiStatus.FAILED,
                        errorMessage = "Downloaded model did not pass integrity validation."
                    )
                }
                return
            }

            preferences.setModelDownloadId(downloadModel.id, null)
            if (downloadModel.id == model.id) {
                preferences.setPendingAutoEnable(false)
                preferences.setEnabled(true)
                updateStateFromPreferences(preferences.state.first(), null)
            }
        } catch (e: Exception) {
            preferences.setModelDownloadId(downloadModel.id, null)
            if (downloadModel.id == model.id) {
                preferences.setPendingAutoEnable(false)
                preferences.setEnabled(false)
                _state.value = LocalModelUiState(
                    model = downloadModel,
                    modelId = downloadModel.id,
                    status = LocalModelUiStatus.FAILED,
                    errorMessage = e.message ?: "Failed to validate downloaded model."
                )
            }
        }
    }

    private fun updateStateFromPreferences(
        prefs: LocalModelPreferencesState,
        currentDownloadId: Long?
    ) {
        _state.value = LocalModelStateResolver.resolve(
            isFilePresent = model.modelFile(appContext).exists(),
            isEnabled = prefs.isGemmaEnabled,
            downloadId = currentDownloadId,
            progress = if (currentDownloadId == null) null else queryDownload(currentDownloadId)?.percent,
            error = null
        ).copy(model = model, modelId = model.id)
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

    private fun isDownloadedFileValid(file: File, expectedModel: LocalModel): Boolean {
        if (!file.exists()) return false
        val minimumExpectedBytes = expectedModel.expectedBytes - 50_000_000L
        val maximumExpectedBytes = expectedModel.expectedBytes + 50_000_000L
        val actualBytes = file.length()
        if (actualBytes !in minimumExpectedBytes..maximumExpectedBytes) return false
        return sha256(file).equals(expectedModel.sha256, ignoreCase = true)
    }

    private fun validatedDownloadUri(expectedModel: LocalModel): Uri? {
        val uri = Uri.parse(expectedModel.downloadUrl)
        if (uri.scheme != "https") return null
        val host = uri.host?.lowercase() ?: return null
        if (host == "huggingface.co") {
            if (!uri.path.orEmpty().startsWith("/litert-community/")) return null
            if (!uri.path.orEmpty().contains("/resolve/")) return null
            return uri
        }
        if (host == "modelscope.cn") {
            if (!uri.path.orEmpty().contains("/resolve/")) return null
            return uri
        }
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
            } else {
                0
            }
    }
}

private fun Cursor.intColumn(columnName: String): Int =
    getInt(getColumnIndexOrThrow(columnName))

private fun Cursor.longColumn(columnName: String): Long =
    getLong(getColumnIndexOrThrow(columnName))
