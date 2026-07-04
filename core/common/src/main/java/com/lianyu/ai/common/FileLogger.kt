package com.lianyu.ai.common

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** File-based debug logger — bypasses vivo/ROM logcat filtering */
object FileLogger {
    private val file by lazy { File("/data/data/com.lianyu.ai/files/aiservice_debug.log") }

    fun log(tag: String, msg: String) {
        try {
            file.parentFile?.mkdirs()
            val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
            file.appendText("$ts [$tag] $msg\n")
        } catch (_: Exception) {}
    }
}