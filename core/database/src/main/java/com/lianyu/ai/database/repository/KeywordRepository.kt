package com.lianyu.ai.database.repository

import android.util.Log
import com.lianyu.ai.common.SaltStore
import com.lianyu.ai.database.dao.KeywordDao
import com.lianyu.ai.database.model.KeywordEntity
import java.security.MessageDigest

class KeywordRepository(private val keywordDao: KeywordDao) {

    companion object {
        private const val TAG = "KeywordRepository"
    }

    private fun getSalt(): String = SaltStore.getSalt("keyword")

    suspend fun getAllEnabled(): List<KeywordEntity> {
        val keywords = keywordDao.getAllEnabled()
        if (!verifyIntegrity(keywords)) {
            Log.e(TAG, "关键词数据完整性校验失败！可能被篡改")
        }
        return keywords.filter { verifyItemIntegrity(it) }
    }

    suspend fun getByLevel(level: String): List<KeywordEntity> {
        return keywordDao.getByLevel(level).filter { verifyItemIntegrity(it) }
    }

    suspend fun getByType(type: String): List<KeywordEntity> {
        return keywordDao.getByType(type).filter { verifyItemIntegrity(it) }
    }

    suspend fun count(): Int = keywordDao.count()

    suspend fun countEnabled(): Int = keywordDao.countEnabled()

    fun insertAll(keywords: List<KeywordEntity>) {
        val protectedKeywords = keywords.map { it.copy(checksum = calculateChecksum(it)) }
        keywordDao.insertAll(protectedKeywords)
    }

    fun setEnabled(id: Int, enabled: Boolean): Int {
        return keywordDao.setEnabled(id, enabled)
    }

    private fun calculateChecksum(keyword: KeywordEntity): String {
        val data = "${keyword.keyword}|${keyword.pattern}|${keyword.level}|${keyword.type}|${keyword.banDays}|${getSalt()}"
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(data.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    private fun verifyItemIntegrity(keyword: KeywordEntity): Boolean {
        if (keyword.checksum.isEmpty()) return true
        val expectedChecksum = calculateChecksum(keyword)
        val isValid = keyword.checksum == expectedChecksum
        if (!isValid) {
            Log.w(TAG, "关键词 [${keyword.keyword}] 校验失败，可能已被篡改")
        }
        return isValid
    }

    private fun verifyIntegrity(keywords: List<KeywordEntity>): Boolean {
        val allValid = keywords.all { verifyItemIntegrity(it) }
        if (!allValid) {
            Log.e(TAG, "数据库中存在被篡改的关键词记录")
        }
        return allValid
    }

    suspend fun getDatabaseHash(): String {
        val allKeywords = keywordDao.getAllWithChecksum()
        val data = allKeywords.joinToString("|") { "${it.id}:${it.checksum}" }
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(data.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
