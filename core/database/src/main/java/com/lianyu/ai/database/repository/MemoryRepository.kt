package com.lianyu.ai.database.repository

import com.lianyu.ai.database.dao.MemoryDao
import com.lianyu.ai.database.model.MemoryCategory
import com.lianyu.ai.database.model.MemoryEntry
import com.lianyu.ai.database.model.TempMemory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MemoryRepository(private val memoryDao: MemoryDao, private val deviceId: String) {

    private fun decrypt(memory: MemoryEntry): MemoryEntry {
        val decryptedContext = try {
            MemoryCrypto.decrypt(memory.context)
        } catch (_: Exception) {
            memory.context
        }
        return memory.copy(context = decryptedContext)
    }

    fun getMemoriesForCompanion(companionId: Long): Flow<List<MemoryEntry>> {
        return memoryDao.getMemoriesForCompanion(companionId, deviceId)
            .map { list -> list.map { decrypt(it) } }
    }

    fun getMemoriesByCategory(companionId: Long, category: MemoryCategory): Flow<List<MemoryEntry>> {
        return memoryDao.getMemoriesByCategory(companionId, deviceId, category)
            .map { list -> list.map { decrypt(it) } }
    }

    suspend fun searchMemories(companionId: Long, query: String, limit: Int = 5): List<MemoryEntry> {
        return memoryDao.searchMemories(companionId, deviceId, query, limit)
            .map { decrypt(it) }
    }

    suspend fun getEnrichedContext(companionId: Long, lastUserMessage: String, contextLimit: Int): String {
        // [M7 FIX] 尊重 contextLimit 参数：原硬编码 limit=10，调用方传入的 contextLimit 无效。
        val memories = searchMemories(companionId, lastUserMessage, limit = contextLimit.coerceIn(1, 20))
        if (memories.isEmpty()) return ""
        return memories.joinToString("\n") { memory ->
            "[${memory.category.name}] ${memory.content}"
        }
    }

    suspend fun addMemory(
        companionId: Long,
        content: String,
        category: MemoryCategory = MemoryCategory.FACT,
        importance: Float = 0.5f,
        context: String = ""
    ) {
        val existing = memoryDao.searchMemories(companionId, deviceId, content, 1)
        val similar = existing.firstOrNull()?.let { entry ->
            // [H2 FIX] 用字符 bigram Jaccard 替代空格分词：中文输入几乎不含空格，
            // 原 split(" ") 对整句返回单元素，Jaccard 要么 0 要么 1，去重形同虚设。
            // bigram 对中文短句有合理粒度，能识别语义相近的记忆。
            if (jaccardSimilarity(content, entry.content) > 0.7f) entry else null
        }

        if (similar != null) {
            val updated = similar.copy(
                context = encryptContext(similar.context),
                importance = minOf(1.0f, similar.importance + 0.1f),
                accessCount = similar.accessCount + 1,
                lastAccessed = System.currentTimeMillis()
            )
            memoryDao.insertMemory(updated)
        } else {
            val memory = MemoryEntry(
                companionId = companionId,
                content = content,
                category = category,
                importance = importance,
                context = encryptContext(context),
                deviceId = deviceId
            )
            memoryDao.insertMemory(memory)
        }
    }

    suspend fun deleteMemory(memory: MemoryEntry) {
        memoryDao.deleteMemory(memory)
    }

    suspend fun updateMemory(memory: MemoryEntry) {
        val updated = memory.copy(
            context = encryptContext(memory.context),
            lastAccessed = System.currentTimeMillis()
        )
        memoryDao.insertMemory(updated)
    }

    suspend fun deleteMemoriesForCompanion(companionId: Long) {
        memoryDao.deleteMemoriesForCompanion(companionId, deviceId)
    }

    fun getRecentTempMemories(companionId: Long, limit: Int = 20): Flow<List<TempMemory>> {
        return memoryDao.getRecentTempMemories(companionId, deviceId, limit)
    }

    suspend fun addTempMemory(companionId: Long, userInput: String, botResponse: String) {
        val tempMemory = TempMemory(
            companionId = companionId,
            userInput = userInput,
            botResponse = botResponse,
            deviceId = deviceId
        )
        memoryDao.insertTempMemory(tempMemory)
        memoryDao.cleanupOldTempMemories(companionId, deviceId, 20)
    }

    suspend fun deleteTempMemoriesForCompanion(companionId: Long) {
        memoryDao.deleteTempMemoriesForCompanion(companionId, deviceId)
    }

    suspend fun extractAndSaveMemories(companionId: Long, userInput: String, aiResponse: String? = null) {
        val trimmedInput = userInput.trim()
        if (trimmedInput.length < 2) return

        if (aiResponse != null) {
            addTempMemory(companionId, trimmedInput, aiResponse)
        }

        if (aiResponse == null) return

        if (containsAny(trimmedInput, listOf(
            "我叫", "我是", "我来自", "我工作", "职业是", "我的", "我姓",
            "我住在", "我住", "我学", "专业是", "我是做", "我在",
            "年龄", "岁", "生日", "星座", "血型", "身高", "体重",
            "电话", "微信", "qq", "邮箱", "地址", "公司", "学校"
        ))) {
            addMemory(companionId, trimmedInput, MemoryCategory.FACT, 0.8f)
        }

        // [H3 FIX] 收紧关键词：原列表含单字"爱/恨/怕/想/要/心/累/哭/笑"等高频字，几乎匹配每句话，
        // 导致记忆库膨胀。改为更具体的短语，减少误判。
        if (containsAny(trimmedInput, listOf(
            "我喜欢", "我讨厌", "我爱吃", "我不爱吃", "我最爱", "我不喜欢",
            "我最讨厌", "我反感", "我厌恶", "我热衷", "我痴迷", "我感兴趣", "我没兴趣",
            "好吃", "难吃", "好看", "难看", "好听", "好玩", "无聊"
        ))) {
            addMemory(companionId, trimmedInput, MemoryCategory.PREFERENCE, 0.7f)
        }

        if (containsAny(trimmedInput, listOf(
            "我很开心", "我很难过", "我很生气", "我很感动", "我很兴奋",
            "好开心", "好难过", "好生气", "好感动", "好失望",
            "好累", "好烦", "好爽", "好委屈", "好害怕", "好担心",
            "压力大", "心情不好", "心情很好", "情绪不好",
            "想哭", "哭了", "笑死", "笑哭了"
        ))) {
            addMemory(companionId, trimmedInput, MemoryCategory.EMOTION, 0.7f)
        }

        if (containsAny(trimmedInput, listOf(
            "今天", "昨天", "明天", "上周", "下周", "周末", "放假", "考试",
            "出差", "旅行", "聚会", "约会", "面试", "入职", "离职", "搬家"
        ))) {
            addMemory(companionId, trimmedInput, MemoryCategory.EVENT, 0.6f)
        }
    }

    private fun encryptContext(context: String): String {
        if (context.isBlank()) return ""
        return try {
            MemoryCrypto.encrypt(context)
        } catch (_: Exception) {
            context
        }
    }

    private fun containsAny(text: String, keywords: List<String>): Boolean {
        return keywords.any { text.contains(it) }
    }

    /**
     * [H2 FIX] 字符 bigram Jaccard 相似度：对中文短句有合理粒度。
     * 例："我喜欢吃苹果" 与 "我喜欢吃香蕉" → bigram 重叠高 → 判定为相似。
     */
    private fun jaccardSimilarity(a: String, b: String): Float {
        if (a.length < 2 || b.length < 2) {
            return if (a == b) 1.0f else 0.0f
        }
        val bigrams1 = a.windowed(2).toSet()
        val bigrams2 = b.windowed(2).toSet()
        val intersection = bigrams1.intersect(bigrams2).size
        val union = bigrams1.union(bigrams2).size
        return if (union == 0) 0.0f else intersection.toFloat() / union
    }
}
