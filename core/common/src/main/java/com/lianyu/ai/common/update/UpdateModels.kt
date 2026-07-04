package com.lianyu.ai.common.update

data class UpdateInfo(
    val versionName: String = "",
    val versionCode: Int = 0,
    val updateUrl: String = "",
    val updateLog: String = "",
    val fileSize: Long = 0L,
    val publishDate: String = "",
    val isForceUpdate: Boolean = false
)

data class DownloadProgress(
    val progress: Int = 0,
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val status: DownloadStatus = DownloadStatus.IDLE
)

enum class DownloadStatus {
    IDLE, DOWNLOADING, PAUSED, COMPLETED, FAILED
}

enum class UpdateCheckState {
    IDLE, CHECKING, AVAILABLE, LATEST, ERROR
}
