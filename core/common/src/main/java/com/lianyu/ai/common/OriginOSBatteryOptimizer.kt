package com.lianyu.ai.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * OriginOS / IQOO / Honor 电池优化专用引导工具。
 *
 * OriginOS 对 ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS 做了特殊拦截：
 * - 系统会静默失败（不弹窗、不报错、不跳转）
 * - 应用详情页中的「电池优化」入口也可能被隐藏或改名
 *
 * 本类通过多路径尝试 + 版本适配，确保能引导用户到正确的设置位置：
 * 1. 优先尝试 OriginOS 特有的电池管理 Activity（i管家/设置）
 * 2. 回退到系统标准电池优化设置页
 * 3. 最终兜底到应用详情页
 *
 * 支持 OriginOS 1.x / 2.x / 3.x / 4.x / 5.x / 6.x 及 FuntouchOS 过渡版本。
 */
object OriginOSBatteryOptimizer {

    /**
     * 检查当前应用是否已忽略电池优化（Doze 白名单）。
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        return NativePermissionRequester.isIgnoringBatteryOptimizations(context)
    }

    /**
     * 打开 OriginOS 电池优化设置页。
     *
     * 按优先级尝试以下路径：
     * 1. 标准 Android 电池优化请求（ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS），
     *    在 OriginOS 6 上部分机型仍可弹窗
     * 2. OriginOS 6+ / 新版设置 → 电池 → 后台耗电管理
     * 3. OriginOS 4+ / 新版 i管家 → 电池管理 → 后台高耗电（直接带包名参数）
     * 4. OriginOS 3.x → 设置 → 电池 → 后台耗电管理
     * 5. OriginOS 2.x / FuntouchOS → 权限管理 → 耗电管理
     * 6. 标准 Android 电池优化设置页（ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS）
     * 7. 应用详情页（最终兜底）
     *
     * @return 是否成功跳转（任意一个路径成功即返回 true）
     */
    fun openBatteryOptimizationSettings(context: Context): Boolean {
        if (!RomUtils.isVivo && !RomUtils.isHuawei) {
            // 非 vivo / 非 Honor/华为 设备，使用标准路径
            return openStandardBatterySettings(context) || openAppDetailsSettings(context)
        }

        // 路径0: 标准请求弹窗，在 OriginOS 部分机型仍可正常工作
        if (openRequestIgnoreBatteryOptimizations(context)) return true

        // OriginOS 6+ 优先路径：新版设置/电池管理页面
        if (RomUtils.isOriginOS6OrAbove()) {
            val intentO6 = Intent().apply {
                component = android.content.ComponentName(
                    "com.vivo.settings",
                    "com.vivo.settings.battery.BatteryManagerActivity"
                )
                putExtra("package_name", context.packageName)
                putExtra("target_page", "background_power")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (safeStartActivity(context, intentO6)) return true
        }

        // 路径1: OriginOS 4+ / 新版 i管家 电池管理（带包名参数直接定位）
        val intent1 = Intent().apply {
            component = android.content.ComponentName(
                "com.iqoo.secure",
                "com.iqoo.secure.safecenter.GuidePageActivity"
            )
            putExtra("package_name", context.packageName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        if (safeStartActivity(context, intent1)) return true

        // 路径2: OriginOS 3.x / 4.x 设置 → 电池 → 后台耗电管理
        val intent2 = Intent().apply {
            component = android.content.ComponentName(
                "com.vivo.settings",
                "com.vivo.settings.battery.BatteryOptimizationActivity"
            )
            putExtra("package_name", context.packageName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        if (safeStartActivity(context, intent2)) return true

        // 路径3: OriginOS 2.x / 部分机型 → 权限管理 → 耗电管理
        val intent3 = Intent().apply {
            component = android.content.ComponentName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.PurviewTabActivity"
            )
            putExtra("tab_index", 2) // 通常第3个 tab 是耗电管理
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        if (safeStartActivity(context, intent3)) return true

        // 路径4: i管家 → 电池管理（旧版入口）
        val intent4 = Intent().apply {
            component = android.content.ComponentName(
                "com.iqoo.secure",
                "com.iqoo.secure.safecenter.SmartManagerStateActivity"
            )
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        if (safeStartActivity(context, intent4)) return true

        // 路径5: 标准 Android 电池优化设置页
        if (openStandardBatterySettings(context)) return true

        // 路径6: 应用详情页（最终兜底）
        return openAppDetailsSettings(context)
    }

    /**
     * 打开 OriginOS 自启动设置页。
     *
     * 按优先级尝试：
     * 1. OriginOS 6+ 设置 → 应用与权限 → 自启动
     * 2. 新版权限管理 → 自启动
     * 3. i管家 → 应用管理 → 自启动
     * 4. 应用详情页兜底
     */
    fun openAutoStartSettings(context: Context): Boolean {
        if (!RomUtils.isVivo) {
            return openAppDetailsSettings(context)
        }

        // OriginOS 6+ 优先路径
        if (RomUtils.isOriginOS6OrAbove()) {
            val intentO6 = Intent().apply {
                component = android.content.ComponentName(
                    "com.vivo.settings",
                    "com.vivo.settings.application.AutostartManagerActivity"
                )
                putExtra("package_name", context.packageName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (safeStartActivity(context, intentO6)) return true
        }

        // 路径1: 新版权限管理 → 自启动（带包名直接定位）
        val intent1 = Intent().apply {
            component = android.content.ComponentName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
            )
            putExtra("package_name", context.packageName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        if (safeStartActivity(context, intent1)) return true

        // 路径2: iQOO 专用自启动管理
        val intent2 = Intent().apply {
            component = android.content.ComponentName(
                "com.iqoo.secure",
                "com.iqoo.secure.safecenter.BgStartUpManagerActivity"
            )
            putExtra("package_name", context.packageName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        if (safeStartActivity(context, intent2)) return true

        // 路径3: 旧版 i管家
        val intent3 = Intent().apply {
            component = android.content.ComponentName(
                "com.iqoo.secure",
                "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"
            )
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        if (safeStartActivity(context, intent3)) return true

        // 兜底: 应用详情页
        return openAppDetailsSettings(context)
    }

    /**
     * 打开 OriginOS 后台高耗电 / 耗电保护设置页。
     *
     * 这是 vivo/IQOO 上最重要的保活权限之一。
     */
    fun openBackgroundPowerSettings(context: Context): Boolean {
        if (!RomUtils.isVivo) {
            return openAppDetailsSettings(context)
        }

        // OriginOS 6+ 优先路径
        if (RomUtils.isOriginOS6OrAbove()) {
            val intentO6 = Intent().apply {
                component = android.content.ComponentName(
                    "com.vivo.settings",
                    "com.vivo.settings.battery.BatteryManagerActivity"
                )
                putExtra("package_name", context.packageName)
                putExtra("target_page", "background_power")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (safeStartActivity(context, intentO6)) return true
        }

        // 路径1: i管家 → 电池管理 → 后台高耗电（新版，带包名参数）
        val intent1 = Intent().apply {
            component = android.content.ComponentName(
                "com.iqoo.secure",
                "com.iqoo.secure.safecenter.GuidePageActivity"
            )
            putExtra("package_name", context.packageName)
            putExtra("target_page", "background_power")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        if (safeStartActivity(context, intent1)) return true

        // 路径2: i管家 → 电池管理（旧版）
        val intent2 = Intent().apply {
            component = android.content.ComponentName(
                "com.iqoo.secure",
                "com.iqoo.secure.safecenter.SmartManagerStateActivity"
            )
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        if (safeStartActivity(context, intent2)) return true

        // 路径3: 权限管理 → 耗电管理
        val intent3 = Intent().apply {
            component = android.content.ComponentName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.PurviewTabActivity"
            )
            putExtra("tab_index", 2)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        if (safeStartActivity(context, intent3)) return true

        // 路径4: vivo 电量管理（部分机型）
        val intent4 = Intent().apply {
            component = android.content.ComponentName(
                "com.vivo.abe",
                "com.vivo.abe.MainActivity"
            )
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        if (safeStartActivity(context, intent4)) return true

        // 兜底: 应用详情页
        return openAppDetailsSettings(context)
    }

    /**
     * 打开 OriginOS 后台弹出界面 / 悬浮窗 / 锁屏显示权限设置页。
     */
    fun openBackgroundPopupSettings(context: Context): Boolean {
        if (!RomUtils.isVivo) {
            return openAppDetailsSettings(context)
        }

        // OriginOS 6+ 优先路径
        if (RomUtils.isOriginOS6OrAbove()) {
            val intentO6 = Intent().apply {
                component = android.content.ComponentName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.KeepBGActivity"
                )
                putExtra("package_name", context.packageName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (safeStartActivity(context, intentO6)) return true
        }

        val candidates = listOf(
            "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.KeepBGActivity",
            "com.iqoo.secure" to "com.iqoo.secure.safecenter.KeepBGActivity",
            "com.vivo.abe" to "com.vivo.abe.activity.KeepBGActivity"
        )
        val component = RomUtils.findAvailableComponent(context, candidates)
        val intent = if (component != null) {
            Intent().setComponent(component)
        } else {
            openAppDetailsSettings(context)
            return true
        }
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        return safeStartActivity(context, intent)
    }

    /**
     * 一键打开 OriginOS 完整的后台保活设置引导。
     * 按顺序跳转：自启动 → 后台高耗电 → 电池优化。
     *
     * 由于不能同时打开多个 Activity，这里返回需要用户依次打开的 Intent 列表。
     */
    fun getFullBackgroundGuideSteps(context: Context): List<GuideStep> {
        return buildList {
            add(
                GuideStep(
                    title = "允许自启动",
                    description = "确保应用能在后台自动启动",
                    action = { openAutoStartSettings(context) }
                )
            )
            add(
                GuideStep(
                    title = "允许后台高耗电",
                    description = "防止系统清理后台进程",
                    action = { openBackgroundPowerSettings(context) }
                )
            )
            if (!isIgnoringBatteryOptimizations(context)) {
                add(
                    GuideStep(
                        title = "忽略电池优化",
                        description = "防止 Doze 模式限制后台运行",
                        action = { openBatteryOptimizationSettings(context) }
                    )
                )
            }
        }
    }

    /**
     * 获取当前 OriginOS 版本下，电池优化设置的路径说明（用于 UI 文案）。
     */
    fun getBatteryOptimizationGuideText(): String {
        return when {
            RomUtils.isOriginOS6OrAbove() -> {
                "设置 → 电池 → 后台耗电管理 → 找到「恋语」→ 允许后台运行 / 高耗电"
            }
            RomUtils.isOriginOS3OrAbove() -> {
                "设置 → 电池 → 后台耗电管理 → 找到「恋语」→ 允许后台高耗电"
            }
            RomUtils.isVivo -> {
                "i管家 → 电池管理 → 后台高耗电 → 找到「恋语」→ 允许"
            }
            else -> {
                "设置 → 电池 → 电池优化 → 找到「恋语」→ 不优化"
            }
        }
    }

    // ------------------------------------------------------------------
    // 私有辅助方法
    // ------------------------------------------------------------------

    /**
     * 打开标准 Android 电池优化设置页。
     */
    private fun openStandardBatterySettings(context: Context): Boolean {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return safeStartActivity(context, intent)
    }

    /**
     * 尝试直接弹系统“忽略电池优化”请求对话框。
     */
    private fun openRequestIgnoreBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return safeStartActivity(context, intent)
    }

    /**
     * 打开应用详情页（通用兜底）。
     */
    private fun openAppDetailsSettings(context: Context): Boolean {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return safeStartActivity(context, intent)
    }

    /**
     * 安全启动 Activity，失败返回 false 但不抛异常。
     */
    private fun safeStartActivity(context: Context, intent: Intent): Boolean {
        return try {
            // 先检查 Activity 是否存在
            val resolveInfo = context.packageManager.resolveActivity(intent, 0)
            if (resolveInfo != null) {
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 引导步骤数据类。
     */
    data class GuideStep(
        val title: String,
        val description: String,
        val action: () -> Boolean
    )
}
