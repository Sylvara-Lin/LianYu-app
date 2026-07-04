package com.lianyu.ai.database.dao

import androidx.room.*
import com.lianyu.ai.database.model.QuizQuestionEntity

@Dao
interface QuizQuestionDao {

    @Query("SELECT * FROM quiz_questions WHERE isEnabled = 1 ORDER BY category, id")
    suspend fun getAllEnabled(): List<QuizQuestionEntity>

    @Query("SELECT * FROM quiz_questions WHERE category = :category AND isEnabled = 1 ORDER BY RANDOM() LIMIT :count")
    suspend fun getRandomByCategory(category: String, count: Int): List<QuizQuestionEntity>

    @Query("SELECT * FROM quiz_questions WHERE isEnabled = 1 ORDER BY RANDOM() LIMIT :count")
    suspend fun getRandom(count: Int): List<QuizQuestionEntity>

    @Query("SELECT COUNT(*) FROM quiz_questions")
    suspend fun count(): Int

    @Query("SELECT SUM(CASE WHEN isEnabled = 1 THEN 1 ELSE 0 END) FROM quiz_questions")
    suspend fun countEnabled(): Int

    @Query("SELECT COUNT(*) FROM quiz_questions WHERE category = :category AND isEnabled = 1")
    suspend fun countByCategory(category: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(questions: List<QuizQuestionEntity>): List<Long>

    @Update
    fun update(question: QuizQuestionEntity): Int

    @Query("UPDATE quiz_questions SET isEnabled = :enabled WHERE id = :id")
    fun setEnabled(id: Int, enabled: Boolean): Int

    @Delete
    fun delete(question: QuizQuestionEntity): Int

    @Query("DELETE FROM quiz_questions")
    fun deleteAll(): Int

    @Transaction
    @Query("SELECT * FROM quiz_questions ORDER BY id")
    suspend fun getAllWithChecksum(): List<QuizQuestionEntity>
}
