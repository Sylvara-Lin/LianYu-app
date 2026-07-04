package com.lianyu.ai.network.stt

import android.content.Context

interface SttProviderInterface {
    suspend fun recognize(context: Context, audioPath: String): String?
    suspend fun recognizeFromFile(context: Context, audioPath: String, mimeType: String): String?
    suspend fun testConnection(): Boolean
}

data class SttResult(
    val text: String,
    val confidence: Float = 0f,
    val isFinal: Boolean = true,
    val provider: String = ""
)
