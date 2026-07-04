package com.lianyu.ai.common

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object PermissionManager {

    const val CAMERA = Manifest.permission.CAMERA
    const val RECORD_AUDIO = Manifest.permission.RECORD_AUDIO
    const val READ_MEDIA_IMAGES = Manifest.permission.READ_MEDIA_IMAGES
    const val READ_MEDIA_VIDEO = Manifest.permission.READ_MEDIA_VIDEO
    const val READ_EXTERNAL_STORAGE = Manifest.permission.READ_EXTERNAL_STORAGE
    const val WRITE_EXTERNAL_STORAGE = Manifest.permission.WRITE_EXTERNAL_STORAGE
    const val POST_NOTIFICATIONS = Manifest.permission.POST_NOTIFICATIONS

    fun hasPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun hasPermissions(context: Context, permissions: List<String>): Boolean {
        return permissions.all { hasPermission(context, it) }
    }

    fun shouldShowRationale(activity: Activity, permission: String): Boolean {
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    }

    fun getCameraPermissions(): Array<String> {
        return arrayOf(CAMERA)
    }

    fun getAudioPermissions(): Array<String> {
        return arrayOf(RECORD_AUDIO)
    }

    fun getImagePickPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(READ_MEDIA_IMAGES)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            arrayOf(READ_EXTERNAL_STORAGE)
        } else {
            emptyArray()
        }
    }

    fun getVideoPickPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(READ_MEDIA_VIDEO)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            arrayOf(READ_EXTERNAL_STORAGE)
        } else {
            emptyArray()
        }
    }

    fun getAllRequiredPermissionsForMedia(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(READ_MEDIA_IMAGES, READ_MEDIA_VIDEO)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            arrayOf(READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE)
        } else {
            emptyArray()
        }
    }

    fun createPermissionLauncher(
        activity: FragmentActivity,
        onGranted: () -> Unit,
        onDenied: (List<String>) -> Unit
    ): ActivityResultLauncher<Array<String>> {
        return activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val deniedPermissions = permissions.filter { !it.value }.keys.toList()
            if (deniedPermissions.isEmpty()) {
                onGranted()
            } else {
                onDenied(deniedPermissions)
            }
        }
    }

    fun createSinglePermissionLauncher(
        activity: FragmentActivity,
        onGranted: () -> Unit,
        onDenied: () -> Unit
    ): ActivityResultLauncher<String> {
        return activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                onGranted()
            } else {
                onDenied()
            }
        }
    }

    fun requestCameraPermission(
        launcher: ActivityResultLauncher<String>,
        onAlreadyGranted: (() -> Unit)? = null
    ) {
        launcher.launch(CAMERA)
    }

    fun requestAudioPermission(
        launcher: ActivityResultLauncher<String>,
        onAlreadyGranted: (() -> Unit)? = null
    ) {
        launcher.launch(RECORD_AUDIO)
    }

    fun requestImagePickPermission(
        launcher: ActivityResultLauncher<Array<String>>
    ) {
        launcher.launch(getImagePickPermissions())
    }

    fun getPermissionDescription(permission: String): String {
        return when (permission) {
            CAMERA -> "相机权限"
            RECORD_AUDIO -> "麦克风权限"
            READ_MEDIA_IMAGES, READ_EXTERNAL_STORAGE -> "存储/相册权限"
            READ_MEDIA_VIDEO -> "视频读取权限"
            WRITE_EXTERNAL_STORAGE -> "写入存储权限"
            POST_NOTIFICATIONS -> "通知权限"
            else -> "未知权限"
        }
    }

    fun getPermissionRationale(permission: String): String {
        return when (permission) {
            CAMERA -> "需要相机权限才能拍摄照片或视频"
            RECORD_AUDIO -> "需要麦克风权限才能录制语音"
            READ_MEDIA_IMAGES, READ_EXTERNAL_STORAGE -> "需要存储权限才能选择图片"
            READ_MEDIA_VIDEO -> "需要存储权限才能选择视频"
            WRITE_EXTERNAL_STORAGE -> "需要写入权限才能保存文件"
            POST_NOTIFICATIONS -> "需要通知权限才能接收消息提醒"
            else -> "需要此权限以使用完整功能"
        }
    }

    fun canPickImageWithoutPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
    }

    fun canRecordAudioWithoutPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
    }
}