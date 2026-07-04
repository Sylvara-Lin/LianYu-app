package com.lianyu.ai.database.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Entity(tableName = "chat_groups")
@Serializable
@SerialName("E3")
data class ChatGroup(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val avatarUrl: String? = null,
    val companionIds: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun getCompanionIdList(): List<Long> {
        return companionIds.split(",").mapNotNull { it.trim().toLongOrNull() }
    }
}
