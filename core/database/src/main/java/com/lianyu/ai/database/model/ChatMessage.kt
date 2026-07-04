package com.lianyu.ai.database.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = CompanionEntity::class,
            parentColumns = ["id"],
            childColumns = ["companionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("companionId"), Index("timestamp")]
)
@Serializable
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val companionId: Long,
    val content: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val type: MessageType = MessageType.TEXT,
    val searchContent: String = "",
    val fileFormat: FileFormat = FileFormat.TEXT,
    val linkString: String = ""
) {
    val role: String get() = if (isFromUser) "user" else "assistant"
    val isFromAssistant: Boolean get() = !isFromUser
    val isSystem: Boolean get() = false
}
