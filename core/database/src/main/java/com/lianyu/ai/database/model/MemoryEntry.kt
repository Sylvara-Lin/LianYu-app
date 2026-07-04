package com.lianyu.ai.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Entity(tableName = "memory_entries")
@Serializable
@SerialName("E5")
data class MemoryEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val companionId: Long,
    val content: String,
    val category: MemoryCategory = MemoryCategory.FACT,
    val importance: Float = 0.5f,
    val context: String = "",
    val accessCount: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val lastAccessed: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "''")
    val deviceId: String = ""
)

enum class MemoryCategory {
    FACT, EMOTION, PREFERENCE, EVENT, HABIT, RELATIONSHIP
}

@Entity(tableName = "temp_memory")
@Serializable
@SerialName("E6")
data class TempMemory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val companionId: Long,
    val userInput: String,
    val botResponse: String,
    val timestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "''")
    val deviceId: String = ""
)
