package com.lianyu.ai.common

import android.content.Context
import android.os.Build
import android.view.Window
import android.view.WindowManager

object FrameRateManager {
    private const val PREFS_NAME = "frame_rate_prefs"
    private const val KEY_FRAME_RATE = "frame_rate"
    private const val KEY_AUTO_MODE = "frame_rate_auto"

    enum class FrameRate(val value: Int, val label: String) {
        AUTO(-1, "自动"),
        RATE_60(60, "60Hz"),
        RATE_90(90, "90Hz"),
        RATE_120(120, "120Hz"),
        RATE_144(144, "144Hz"),
        RATE_165(165, "165Hz")
    }

    fun getSupportedFrameRates(context: Context): List<FrameRate> {
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        }

        val supportedRates = mutableListOf<FrameRate>()
        supportedRates.add(FrameRate.AUTO)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val modes = display?.supportedModes
            val maxRate = modes?.maxOfOrNull { it.refreshRate.toInt() } ?: 60
            if (maxRate >= 60) supportedRates.add(FrameRate.RATE_60)
            if (maxRate >= 90) supportedRates.add(FrameRate.RATE_90)
            if (maxRate >= 120) supportedRates.add(FrameRate.RATE_120)
            if (maxRate >= 144) supportedRates.add(FrameRate.RATE_144)
            if (maxRate >= 165) supportedRates.add(FrameRate.RATE_165)
        } else {
            supportedRates.add(FrameRate.RATE_60)
        }

        return supportedRates.distinct()
    }

    fun applyFrameRate(window: Window, frameRate: FrameRate) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            when (frameRate) {
                FrameRate.AUTO -> {
                    window.attributes.preferredRefreshRate = 0f
                }
                else -> {
                    val display = window.windowManager.defaultDisplay
                    val modes = display.supportedModes
                    val targetMode = modes.minByOrNull {
                        kotlin.math.abs(it.refreshRate - frameRate.value)
                    }
                    targetMode?.let {
                        window.attributes.preferredRefreshRate = it.refreshRate
                    }
                }
            }
            window.attributes = window.attributes
        }
    }

    fun saveFrameRate(context: Context, frameRate: FrameRate) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_FRAME_RATE, frameRate.value)
            .putBoolean(KEY_AUTO_MODE, frameRate == FrameRate.AUTO)
            .apply()
    }

    fun getSavedFrameRate(context: Context): FrameRate {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isAuto = prefs.getBoolean(KEY_AUTO_MODE, true)
        if (isAuto) return FrameRate.AUTO

        val value = prefs.getInt(KEY_FRAME_RATE, -1)
        return FrameRate.entries.find { it.value == value } ?: FrameRate.AUTO
    }
}
