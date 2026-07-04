package com.lianyu.ai.common

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * 原生权限请求工具类。
 *
 * 针对 IQOO / vivo / OPPO 等国产 ROM 的权限跳转适配问题，本类提供统一的解决方案：
 * 1. 直接使用 Android 原生权限弹窗（ActivityResultContracts）请求运行时权限
 * 2. 对于无法通过运行时权限解决的设置（如自启动、后台高耗电），统一跳转到应用详情页
 * 3. 针对 OriginOS / IQOO 等 ROM，仍保留 ROM 特定的 Activity 路径跳转以提升跳转成功率
 *    （标准 Android 设置页在 OriginOS 上常被系统拦截或入口变更）。
 */
object NativePermissionRequester {

    /**
     * 获取应用需要请求的所有运行时权限列表。
     */
    fun getAllRequiredPermissions(): Array<String> {
        return buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            // 相机和麦克风权限在需要时单独请求，不在启动时批量请求
        }.toTypedArray()
    }

    /**
     * 检查是否已授予所有必要权限。
     */
    fun hasAllRequiredPermissions(context: Context): Boolean {
        return getAllRequiredPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * 检查是否需要显示权限理由说明。
     */
    fun shouldShowRationale(activity: FragmentActivity, permission: String): Boolean {
        return activity.shouldShowRequestPermissionRationale(permission)
    }

    /**
     * 创建一个批量权限请求 Launcher，支持 rationale / 永久拒绝的差异化处理。
     *
     * @param activity FragmentActivity
     * @param onAllGranted 所有权限都被授予时的回调
     * @param onResult 详细的权限请求结果回调，方便调用方做 rationale / 永久拒绝引导
     */
    fun createBatchPermissionLauncher(
        activity: FragmentActivity,
        onAllGranted: () -> Unit,
        onResult: (PermissionResult) -> Unit
    ): ActivityResultLauncher<Array<String>> {
        return activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val deniedPermissions = permissions.filter { !it.value }.keys.toList()
            if (deniedPermissions.isEmpty()) {
                onAllGranted()
                return@registerForActivityResult
            }

            val grantedPermissions = permissions.filter { it.value }.keys.toList()
            val rationalePermissions = deniedPermissions.filter { activity.shouldShowRequestPermissionRationale(it) }
            val permanentlyDeniedPermissions = deniedPermissions.filter { !activity.shouldShowRequestPermissionRationale(it) }

            onResult(
                PermissionResult(
                    grantedPermissions = grantedPermissions,
                    deniedPermissions = deniedPermissions,
                    rationalePermissions = rationalePermissions,
                    permanentlyDeniedPermissions = permanentlyDeniedPermissions
                )
            )
        }
    }

    /**
     * 创建单个权限请求 Launcher。
     */
    fun createSinglePermissionLauncher(
        activity: FragmentActivity,
        onGranted: () -> Unit,
        onDenied: () -> Unit
    ): ActivityResultLauncher<String> {
        return activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) onGranted() else onDenied()
        }
    }

    /**
     * 请求所有必要权限（一次性弹窗）。
     */
    fun requestAllPermissions(launcher: ActivityResultLauncher<Array<String>>) {
        launcher.launch(getAllRequiredPermissions())
    }

    /**
     * 打开应用详情页（通用设置页，所有 ROM 都支持）。
     * 用于引导用户手动开启自启动、后台运行等无法通过运行时权限解决的设置。
     */
    fun openAppDetailsSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        safeStartActivity(context, intent)
    }

    /**
     * 打开通知设置页。
     */
    fun openNotificationSettings(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        safeStartActivity(context, intent)
    }

    /**
     * 打开电池优化设置页。
     *
     * [FIX] 2026-06-23: 在 OriginOS/IQOO/Honor 设备上，使用专用的 OriginOSBatteryOptimizer
     * 替代标准 ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS，解决点击无反应问题。
     */
    fun openBatteryOptimizationSettings(context: Context) {
        if (RomUtils.isVivo || RomUtils.isHuawei) {
            OriginOSBatteryOptimizer.openBatteryOptimizationSettings(context)
            return
        }
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        safeStartActivity(context, intent)
    }

    /**
     * 请求忽略电池优化（直接弹系统对话框）。
     *
     * [FIX] 2026-06-22: 在 IQOO/OriginOS/Honor 上，系统可能拦截 ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
     * 导致 Intent 无法启动。添加 try-catch 兜底，失败时跳转到专用设置页。
     */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // OriginOS/IQOO/Honor 等 ROM 可能拦截此 Intent，兜底跳转到专用设置页
            SecureLog.w("NativePermissionRequester", "requestIgnoreBatteryOptimizations failed: ${e.message}, fallback to settings page")
            openBatteryOptimizationSettings(context)
        }
    }

    /**
     * 检查是否已忽略电池优化。
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * 获取权限的中文描述，用于用户提示。
     */
    fun getPermissionDescription(permission: String): String {
        return when (permission) {
            Manifest.permission.POST_NOTIFICATIONS -> "通知权限"
            Manifest.permission.CAMERA -> "相机权限"
            Manifest.permission.RECORD_AUDIO -> "麦克风权限"
            Manifest.permission.READ_MEDIA_IMAGES -> "读取图片权限"
            Manifest.permission.READ_MEDIA_VIDEO -> "读取视频权限"
            Manifest.permission.READ_EXTERNAL_STORAGE -> "读取存储权限"
            Manifest.permission.WRITE_EXTERNAL_STORAGE -> "写入存储权限"
            else -> "未知权限"
        }
    }

    /**
     * 获取权限的引导说明。
     */
    fun getPermissionRationale(permission: String): String {
        return when (permission) {
            Manifest.permission.POST_NOTIFICATIONS -> "需要通知权限才能接收虚拟恋人的消息提醒"
            Manifest.permission.CAMERA -> "需要相机权限才能拍摄照片"
            Manifest.permission.RECORD_AUDIO -> "需要麦克风权限才能录制语音消息"
            Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_EXTERNAL_STORAGE -> "需要存储权限才能选择图片"
            else -> "需要此权限以使用完整功能"
        }
    }

    private fun safeStartActivity(context: Context, intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            // 最终兜底：尝试打开设置主页面
            try {
                val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallback)
            } catch (_: Exception) {
                // ignore
            }
        }
    }

    /**
     * 批量权限请求结果。
     */
    data class PermissionResult(
        val grantedPermissions: List<String> = emptyList(),
        val deniedPermissions: List<String> = emptyList(),
        val rationalePermissions: List<String> = emptyList(),
        val permanentlyDeniedPermissions: List<String> = emptyList()
    ) {
        val allGranted: Boolean get() = deniedPermissions.isEmpty()
    }
}
