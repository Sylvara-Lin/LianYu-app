package com.lianyu.ai.common

import android.util.Log

/**
 * SecureLog — 安全日志包装器
 *
 * - isDebug 由 LianYuApplication onCreate 中调用 SecureLog.init(isDebug) 注入
 */

object SecureLog {
    private const val TAG = "LianYu"

    /**
     * Release 构建应通过 LianYuApplication 在 onCreate 中注入 false。
     * 在初始化前默认为 true（调试安全——宁可泄露日志也不丢失安全事件）。
     */
    @Volatile
    private var isDebug: Boolean = false

    /**
     * 由 Application 调用以设置日志级别。
     * 应在任何其他组件使用日志之前调用。
     */
    fun init(debug: Boolean) {
        isDebug = debug
    }

    // 所有日志方法

    @JvmStatic
    fun d(subtag: String, msg: String) {
        if (isDebug) Log.d(TAG, "[$subtag] $msg")
    }

    @JvmStatic
    fun i(subtag: String, msg: String) {
        if (isDebug) Log.i(TAG, "[$subtag] $msg")
    }

    @JvmStatic
    fun w(subtag: String, msg: String) {
        if (isDebug) Log.w(TAG, "[$subtag] $msg")
    }

    @JvmStatic
    fun e(subtag: String, msg: String) {
        if (isDebug) Log.e(TAG, "[$subtag] $msg")
    }

    @JvmStatic
    fun e(subtag: String, msg: String, tr: Throwable) {
        if (isDebug) Log.e(TAG, "[$subtag] $msg", tr)
    }

    /**
     * CRITICAL: 仅在发生严重错误时调用，release 也输出
     * 🔒 SecurityConstants.Level.MEDIUM: 使用 android.util.Log 而非 System.err
     *    System.err.println 在 ProGuard 剥离后仍存在，且可能泄露到 logcat。
     *    改用 Log.wtf() (What a Terrible Failure) — Android 原生严重错误级别。
     */
    @JvmStatic
    fun critical(msg: String) {
        // Log.wtf is always visible in logcat, even in release builds
        // But does NOT route to stderr — stays within Android's log buffer
        Log.wtf(TAG, "[CRITICAL] $msg")
    }

    /**
     * SECURITY: 安全相关日志，release 中完全静默
     */
    @JvmStatic
    fun security(msg: String) {
        if (isDebug) Log.d(TAG, "[SEC] $msg")
    }

    /**
     * API: API 请求/响应日志，release 中完全静默
     */
    @JvmStatic
    fun api(subtag: String, msg: String) {
        if (isDebug) Log.d(TAG, "[API][$subtag] $msg")
    }

    /**
     * NETWORK: 网络层日志（轮询、重试、连接状态等）
     */
    @JvmStatic
    fun network(subtag: String, msg: String) {
        if (isDebug) Log.d(TAG, "[NET][$subtag] $msg")
    }

    /**
     * CHUNK: 分段/流式响应日志
     */
    @JvmStatic
    fun chunk(subtag: String, msg: String) {
        if (isDebug) Log.v(TAG, "[CHUNK][$subtag] $msg")
    }

    /**
     * TYPING: 对方正在输入中状态日志
     */
    @JvmStatic
    fun typing(subtag: String, msg: String) {
        if (isDebug) Log.d(TAG, "[TYPING][$subtag] $msg")
    }

    /**
     * PERF: 性能日志，记录耗时操作
     */
    @JvmStatic
    fun perf(subtag: String, msg: String) {
        if (isDebug) Log.d(TAG, "[PERF][$subtag] $msg")
    }

    /**
     * 带耗时统计的日志
     */
    inline fun <T> timed(subtag: String, operation: String, block: () -> T): T {
        val start = System.currentTimeMillis()
        return try {
            block()
        } finally {
            val elapsed = System.currentTimeMillis() - start
            perf(subtag, "$operation took ${elapsed}ms")
        }
    }
}
