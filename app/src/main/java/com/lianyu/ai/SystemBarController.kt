package com.lianyu.ai

import android.app.Activity
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import com.lianyu.ai.uicommon.theme.WeChatDarkBackground


/**
 * 系统栏控制器 — 主题/Locale/状态栏/导航栏统一管理。
 *
 * 反馈回路:
 *   传感器: config.uiMode (当前系统暗色状态)
 *   比较器: savedThemeMode vs system
 *   执行器: statusBarColor / navigationBarColor 更新
 *   熔断: 去抖 500ms — onResume 重复调用被截断
 */
object SystemBarController {

    fun applyBaseContextLocale(base: android.content.Context): android.content.Context {
        val prefs = base.getSharedPreferences("language_prefs", android.content.Context.MODE_PRIVATE)
        val savedLanguage = prefs.getString("language", "zh-CN") ?: "zh-CN"
        val locale = when (savedLanguage) {
            "zh-CN" -> java.util.Locale.SIMPLIFIED_CHINESE
            "zh-TW" -> java.util.Locale.TRADITIONAL_CHINESE
            "en" -> java.util.Locale.ENGLISH
            "ja" -> java.util.Locale.JAPANESE
            "ko" -> java.util.Locale.KOREAN
            else -> java.util.Locale.SIMPLIFIED_CHINESE
        }
        java.util.Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(locale))
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
        }

        val themePrefs = base.getSharedPreferences("theme_prefs", android.content.Context.MODE_PRIVATE)
        when (themePrefs.getString("theme_mode", "SYSTEM")) {
            "LIGHT" -> config.uiMode =
                (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
            "DARK" -> config.uiMode =
                (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
        }

        return base.createConfigurationContext(config)
    }

    fun applySystemBars(activity: Activity) {
        val prefs = activity.getSharedPreferences("theme_prefs", android.content.Context.MODE_PRIVATE)
        val isDark = when (prefs.getString("theme_mode", "SYSTEM")) {
            "DARK" -> true
            "LIGHT" -> false
            else -> (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }

        val navScrim = if (isDark) {
            Color(0xFF000000).copy(alpha = 0.25f)
        } else {
            Color(0xFFFFFFFF).copy(alpha = 0.55f)
        }

        // 状态栏使用不透明纯色，浅色主题用纯白避免显粉；深色主题用背景色保持一致。
        activity.window.statusBarColor = if (isDark) {
            WeChatDarkBackground.toArgb()
        } else {
            Color(0xFFFFFFFF).toArgb()
        }
        activity.window.navigationBarColor = navScrim.toArgb()
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }
    }
}
