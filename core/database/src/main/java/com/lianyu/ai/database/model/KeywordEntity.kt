package com.lianyu.ai.database.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "keywords",
    indices = [
        Index(value = ["level"], name = "index_keywords_level"),
        Index(value = ["type"], name = "index_keywords_type")
    ]
)
data class KeywordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val keyword: String,
    val pattern: String?,
    val level: String,
    val type: String,
    val banDays: Int,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val checksum: String = ""
)
