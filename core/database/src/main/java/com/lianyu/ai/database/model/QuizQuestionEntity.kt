package com.lianyu.ai.database.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "quiz_questions",
    indices = [
        Index(value = ["category"], name = "index_quiz_category"),
        Index(value = ["difficulty"], name = "index_quiz_difficulty")
    ]
)
data class QuizQuestionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val question: String,
    val options: String,
    val correctIndex: Int,
    val category: String,
    val difficulty: String = "MEDIUM",
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val checksum: String = ""
)
