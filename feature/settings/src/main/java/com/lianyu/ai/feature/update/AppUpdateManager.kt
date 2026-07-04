package com.lianyu.ai.feature.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import com.lianyu.ai.common.update.DownloadProgress
import com.lianyu.ai.common.update.DownloadStatus
import com.lianyu.ai.common.update.UpdateCheckState
import com.lianyu.ai.common.update.UpdateInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class AppUpdateManager(private val context: Context) {

    companion object {
        // Update URL configured per build — empty = disabled
        private const val UPDATE_API_URL = ""
    }

    private val okHttpClient = OkHttpClient()

    // App 级作用域，下载任务跨越 UI 页面生命周期（用户可退出检查更新页，下载继续）
    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 防重入：多次点击下载按钮时取消上一个任务，避免并发写同一 APK 文件
    @Volatile
    private var downloadJob: Job? = null

    private val _updateCheckState = MutableStateFlow(UpdateCheckState.IDLE)
    val updateCheckState: StateFlow<UpdateCheckState> = _updateCheckState.asStateFlow()

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()

    private val _downloadProgress = MutableStateFlow(DownloadProgress())
    val downloadProgress: StateFlow<DownloadProgress> = _downloadProgress.asStateFlow()

    private val _showUpdateDialog = MutableStateFlow(false)
    val showUpdateDialog: StateFlow<Boolean> = _showUpdateDialog.asStateFlow()

    fun getCurrentVersionName(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.5.1"
        } catch (e: Exception) {
            "1.5.1"
        }
    }

    suspend fun checkForUpdates() {
        _updateCheckState.value = UpdateCheckState.CHECKING
        try {
            withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url(UPDATE_API_URL)
                    .header("Accept", "application/vnd.github.v3+json")
                    .build()
                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    _updateCheckState.value = UpdateCheckState.ERROR
                    return@withContext
                }
                val body = response.body?.string() ?: run {
                    _updateCheckState.value = UpdateCheckState.ERROR
                    return@withContext
                }
                val release = try {
                    Json { ignoreUnknownKeys = true }
                        .decodeFromString<GitHubRelease>(body)
                } catch (e: Exception) {
                    _updateCheckState.value = UpdateCheckState.ERROR
                    return@withContext
                }
                val latestVersion = release.tagName.removePrefix("v")
                val currentVersion = getCurrentVersionName()

                if (latestVersion.isNotBlank() && compareVersion(latestVersion, currentVersion) > 0) {
                    val apkAsset = release.assets.firstOrNull {
                        it.name.endsWith(".apk") && it.browserDownloadUrl.isNotEmpty()
                    }
                    _updateInfo.value = UpdateInfo(
                        versionName = "v$latestVersion",
                        versionCode = 0,
                        updateUrl = apkAsset?.browserDownloadUrl ?: release.htmlUrl,
                        updateLog = release.body ?: "",
                        fileSize = apkAsset?.size ?: 0L,
                        publishDate = release.publishedAt.take(10),
                        isForceUpdate = false
                    )
                    _updateCheckState.value = UpdateCheckState.AVAILABLE
                    val prefs = context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)
                    if (prefs.getString("ignored_version", "") != "v$latestVersion") {
                        _showUpdateDialog.value = true
                    }
                } else {
                    _updateCheckState.value = UpdateCheckState.LATEST
                }
            }
        } catch (e: Exception) {
            _updateCheckState.value = UpdateCheckState.ERROR
        }
    }

    fun checkInstallPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun requestInstallPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            activity.startActivity(intent)
        }
    }

    /**
     * 启动 APK 下载（fire-and-forget）。
     *
     * - 在 [downloadScope]（Dispatchers.IO）上执行，用户离开页面后下载继续。
     * - [downloadJob] 防重入：再次调用时取消上一个任务，避免并发写 `update.apk` 文件损坏。
     * - 进度通过 [_downloadProgress] StateFlow 推送到 UI，线程安全。
     */
    fun startDownload(url: String) {
        _downloadProgress.value = DownloadProgress(status = DownloadStatus.DOWNLOADING)
        downloadJob?.cancel()
        downloadJob = downloadScope.launch {
            try {
                val request = Request.Builder().url(url).build()
                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    _downloadProgress.value = _downloadProgress.value.copy(status = DownloadStatus.FAILED)
                    return@launch
                }
                val body = response.body ?: run {
                    _downloadProgress.value = _downloadProgress.value.copy(status = DownloadStatus.FAILED)
                    return@launch
                }
                val totalBytes = body.contentLength()
                val inputStream = body.byteStream()
                val apkFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "update.apk")
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8192)
                    var downloadedBytes = 0L
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        val progress = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt() else 0
                        _downloadProgress.value = DownloadProgress(
                            progress = progress,
                            totalBytes = totalBytes,
                            downloadedBytes = downloadedBytes,
                            status = DownloadStatus.DOWNLOADING
                        )
                    }
                }
                _downloadProgress.value = DownloadProgress(
                    progress = 100,
                    totalBytes = totalBytes,
                    downloadedBytes = totalBytes,
                    status = DownloadStatus.COMPLETED
                )
                installApk(apkFile)
            } catch (e: Exception) {
                _downloadProgress.value = _downloadProgress.value.copy(status = DownloadStatus.FAILED)
            }
        }
    }

    private fun installApk(apkFile: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.lianyu.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e2: Exception) {
                _downloadProgress.value = _downloadProgress.value.copy(status = DownloadStatus.FAILED)
            }
        }
    }

    fun ignoreThisVersion() {
        _updateInfo.value?.let { info ->
            val prefs = context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("ignored_version", info.versionName).apply()
        }
        _showUpdateDialog.value = false
    }

    fun dismissUpdate() {
        _showUpdateDialog.value = false
    }

    private fun compareVersion(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLength = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLength) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 > p2) return 1
            if (p1 < p2) return -1
        }
        return 0
    }

    /**
     * 取消下载任务并释放协程作用域，避免 AppUpdateManager 被回收后作用域泄漏。
     * 调用方持有 AppUpdateManager 实例期间无需调此方法；仅在显式销毁时使用。
     */
    fun release() {
        downloadJob?.cancel()
        downloadScope.cancel()
    }

    @Serializable
    private data class GitHubRelease(
        @SerialName("tag_name") val tagName: String = "",
        val name: String = "",
        val body: String = "",
        @SerialName("html_url") val htmlUrl: String = "",
        @SerialName("published_at") val publishedAt: String = "",
        val assets: List<GitHubAsset> = emptyList()
    )

    @Serializable
    private data class GitHubAsset(
        val name: String = "",
        @SerialName("browser_download_url") val browserDownloadUrl: String = "",
        val size: Long = 0L
    )
}
