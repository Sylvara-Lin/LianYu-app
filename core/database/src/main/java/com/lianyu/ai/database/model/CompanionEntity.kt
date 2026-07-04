package com.lianyu.ai.database.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Entity(
    tableName = "companions",
    indices = [
        Index(value = ["createdAt"], name = "index_companions_created_at")
    ]
)
@Serializable
@SerialName("E2")
data class CompanionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val avatarUrl: String? = null,
    val age: Int? = null,
    val personality: String,
    val backstory: String? = null,
    val speakingStyle: String? = null,
    val tags: String? = null,
    val rawPrompt: String? = null,
    val systemPrompt: String? = null,
    val intimacy: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
