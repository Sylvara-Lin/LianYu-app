package com.lianyu.ai.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * 电池优化与自启动设置辅助类。
 *
 * 已针对 OPPO / vivo / 小米 / 华为 / 三星 / 一加等厂商做设置页精确跳转，
 * 优先尝试 ROM 新版本路径，失败自动回退旧版路径或应用详情页。
 */
object BatteryOptimizationHelper {

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        return OppoVivoAdaptationHelper.isIgnoringBatteryOptimizations(context)
    }

    fun requestIgnoreBatteryOptimizations(context: Context) {
        OppoVivoAdaptationHelper.requestIgnoreBatteryOptimizations(context)
    }

    fun openBatteryOptimizationSettings(context: Context) {
        OppoVivoAdaptationHelper.openBatteryOptimizationSettings(context)
    }

    /**
     * 打开自启动 / 应用启动管理设置页。
     */
    fun openAutoStartSettings(context: Context) {
        if (RomUtils.isOppoOrVivo()) {
            OppoVivoAdaptationHelper.openAutoStartSettings(context)
            return
        }

        val intent = Intent().apply {
            when {
                RomUtils.isXiaomi -> {
                    component = android.content.ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"
                    )
                }
                RomUtils.isHuawei -> {
                    component = android.content.ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.optimize.process.ProtectActivity"
                    )
                }
                Build.MANUFACTURER.equals("samsung", ignoreCase = true) -> {
                    component = android.content.ComponentName(
                        "com.samsung.android.lool",
                        "com.samsung.android.sm.ui.battery.BatteryActivity"
                    )
                }
                Build.MANUFACTURER.equals("oneplus", ignoreCase = true) -> {
                    component = android.content.ComponentName(
                        "com.oneplus.security",
                        "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
                    )
                }
                else -> {
                    action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    data = Uri.parse("package:${context.packageName}")
                }
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        safeStartActivity(context, intent)
    }

    /**
     * 打开后台高耗电 / 耗电保护 / 神隐模式等设置页。
     * OPPO / vivo 使用专门适配路径。
     */
    fun openBackgroundPowerSettings(context: Context) {
        if (RomUtils.isOppoOrVivo()) {
            OppoVivoAdaptationHelper.openBackgroundPowerSettings(context)
            return
        }
        openAppDetailsSettings(context)
    }

    /**
     * 打开应用详情页（通用兜底）。
     */
    fun openAppDetailsSettings(context: Context) {
        OppoVivoAdaptationHelper.openAppDetailsSettings(context)
    }

    /**
     * 打开通知设置页。
     */
    fun openNotificationSettings(context: Context) {
        OppoVivoAdaptationHelper.openNotificationSettings(context)
    }

    private fun safeStartActivity(context: Context, intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            openAppDetailsSettings(context)
        }
    }
}
