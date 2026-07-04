package com.lianyu.ai.database.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "token_usage",
    indices = [
        Index(value = ["companionId", "date", "deviceId"], unique = true),
        Index(value = ["date"]),
        Index(value = ["companionId"]),
        Index(value = ["companionId", "deviceId"])
    ]
)
data class TokenUsage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val companionId: Long,
    val date: String,
    val inputTokens: Long = 0L,
    val outputTokens: Long = 0L,
    val totalTokens: Long = 0L,
    val requestCount: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val deviceId: String = ""
) {
    fun addUsage(inputTokens: Long, outputTokens: Long): TokenUsage {
        return this.copy(
            inputTokens = this.inputTokens + inputTokens,
            outputTokens = this.outputTokens + outputTokens,
            totalTokens = this.totalTokens + inputTokens + outputTokens,
            requestCount = this.requestCount + 1,
            timestamp = System.currentTimeMillis()
        )
    }
}
