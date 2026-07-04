package com.lianyu.ai.database.repository

import android.util.Log
import com.lianyu.ai.common.SaltStore
import com.lianyu.ai.database.dao.QuizQuestionDao
import com.lianyu.ai.database.model.QuizQuestionEntity
import java.security.MessageDigest

class QuizQuestionRepository(private val quizQuestionDao: QuizQuestionDao) {

    companion object {
        private const val TAG = "QuizRepository"
    }

    private fun getSalt(): String = SaltStore.getSalt("quiz")

    suspend fun getAllEnabled(): List<QuizQuestionEntity> {
        val questions = quizQuestionDao.getAllEnabled()
        if (!verifyIntegrity(questions)) {
            Log.e(TAG, "题库数据完整性校验失败！可能被篡改")
        }
        return questions.filter { verifyItemIntegrity(it) }
    }

    suspend fun getRandom(count: Int): List<QuizQuestionEntity> {
        val questions = quizQuestionDao.getRandom(count)
        return questions.filter { verifyItemIntegrity(it) }
    }

    suspend fun getRandomByCategory(category: String, count: Int): List<QuizQuestionEntity> {
        val questions = quizQuestionDao.getRandomByCategory(category, count)
        return questions.filter { verifyItemIntegrity(it) }
    }

    suspend fun count(): Int = quizQuestionDao.count()

    suspend fun countEnabled(): Int = quizQuestionDao.countEnabled()

    suspend fun countByCategory(category: String): Int = quizQuestionDao.countByCategory(category)

    fun insertAll(questions: List<QuizQuestionEntity>) {
        val protectedQuestions = questions.map { it.copy(checksum = calculateChecksum(it)) }
        quizQuestionDao.insertAll(protectedQuestions)
    }

    fun setEnabled(id: Int, enabled: Boolean): Int {
        return quizQuestionDao.setEnabled(id, enabled)
    }

    private fun calculateChecksum(question: QuizQuestionEntity): String {
        val data = "${question.question}|${question.options}|${question.correctIndex}|${question.category}|${getSalt()}"
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(data.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    private fun verifyItemIntegrity(question: QuizQuestionEntity): Boolean {
        if (question.checksum.isEmpty()) return true
        val expectedChecksum = calculateChecksum(question)
        val isValid = question.checksum == expectedChecksum
        if (!isValid) {
            Log.w(TAG, "题目 ID=[${question.id}] 校验失败，可能已被篡改")
        }
        return isValid
    }

    private fun verifyIntegrity(questions: List<QuizQuestionEntity>): Boolean {
        val allValid = questions.all { verifyItemIntegrity(it) }
        if (!allValid) {
            Log.e(TAG, "数据库中存在被篡改的题库记录")
        }
        return allValid
    }

    suspend fun getDatabaseHash(): String {
        val allQuestions = quizQuestionDao.getAllWithChecksum()
        val data = allQuestions.joinToString("|") { "${it.id}:${it.checksum}" }
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(data.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
