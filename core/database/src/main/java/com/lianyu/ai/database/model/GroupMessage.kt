package com.lianyu.ai.database.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Entity(
    tableName = "group_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatGroup::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["groupId"]),
        Index(value = ["groupId", "timestamp"]),
        Index(value = ["groupId", "fileFormat"])
    ]
)
@Serializable
@SerialName("E1")
data class GroupMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val groupId: Long,
    val companionId: Long,
    /** Encrypted-at-rest message body. Use GroupMessageRepository for decrypted reads. */
    val content: String,
    /** Millisecond timestamp used as cursor for paged reads. */
    val timestamp: Long = System.currentTimeMillis(),
    /** Queryable plaintext index for fuzzy search. */
    val searchContent: String = content,
    /** Queryable file category. */
    val fileFormat: FileFormat = FileFormat.TEXT,
    /** Encrypted-at-rest link string for one or more files/resources. */
    val linkString: String = ""
)
