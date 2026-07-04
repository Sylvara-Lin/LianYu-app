package com.lianyu.ai.feature.chat.ui.viewmodel

import com.lianyu.ai.feature.chat.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Zero-dependency debug log to file — bypasses vivo logcat filtering.
 *
 * 🔒 SecurityConstants.Level.MEDIUM: All output guarded by BuildConfig.DEBUG.
 *    In release builds, R8 inlines the constant and dead-code-eliminates
 *    the entire method body. No log file is created on user devices.
 */
object ChatDebugLog {
    private val file by lazy {
        if (!BuildConfig.DEBUG) return@lazy null
        File("/data/data/com.lianyu.ai/files/chatvm_debug.log")
    }

    fun log(msg: String) {
        if (!BuildConfig.DEBUG) return
        try {
            val currentFile = file ?: return
            currentFile.parentFile?.mkdirs()
            val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
            currentFile.appendText("$ts $msg\n")
        } catch (_: Exception) {}
    }
}
