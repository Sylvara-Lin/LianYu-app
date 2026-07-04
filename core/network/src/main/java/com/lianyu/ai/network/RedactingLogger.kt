package com.lianyu.ai.network

import okhttp3.logging.HttpLoggingInterceptor

/**
 * OkHttp logging logger that redacts sensitive headers (Authorization, x-api-key)
 * and masks request/response bodies to prevent credential and conversation leaks.
 *
 * Replaces API key values with [REDACTED] and truncates body content.
 */
internal class RedactingLogger : HttpLoggingInterceptor.Logger {
    private val sensitiveHeaders = setOf(
        "Authorization", "authorization",
        "x-api-key", "X-Api-Key", "X-API-KEY"
    )
    private val bodyMaxLength = 80

    override fun log(message: String) {
        val sanitized = sanitize(message)
        android.util.Log.d("OkHttp", sanitized)
    }

    private fun sanitize(message: String): String {
        var result = message

        // Redact sensitive header values: "Authorization: Bearer sk-xxx" → "Authorization: [REDACTED]"
        for (header in sensitiveHeaders) {
            result = result.replace(
                Regex("($header:\\s*).*", RegexOption.IGNORE_CASE),
                "$1[REDACTED]"
            )
        }

        // Truncate body content to prevent conversation data in logcat
        if (result.length > bodyMaxLength + 20) {
            result = result.take(bodyMaxLength) + "...[truncated]"
        }

        return result
    }
}
