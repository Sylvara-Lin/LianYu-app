package com.lianyu.ai.feature.memory.engine

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 记忆 JSON 文件持久化层
 * 完全独立于 Room 数据库，按 deviceId 用户隔离
 *
 * 文件结构:
 * memory/{deviceId}/
 *   ├── global/
 *   │   ├── index.json
 *   │   ├── short.json
 *   │   ├── mid.json
 *   │   └── long.json
 *   ├── companion_{id}/
 *   │   ├── index.json
 *   │   ├── short.json
 *   │   ├── mid.json
 *   │   └── long.json
 *   └── group_{id}/
 *       ├── index.json
 *       └── mid.json
 */
class MemoryStore(private val context: Context, private val deviceId: String) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    private val baseDir: File by lazy {
        File(context.filesDir, "memory/$deviceId").apply { mkdirs() }
    }

    /**
     * 获取作用域目录
     */
    private fun scopeDir(scope: MemoryScope, id: Long): File {
        val dirName = when (scope) {
            MemoryScope.GLOBAL -> "global"
            MemoryScope.COMPANION -> "companion_$id"
            MemoryScope.GROUP -> "group_$id"
        }
        return File(baseDir, dirName).apply { mkdirs() }
    }

    /**
     * 获取分层文件
     */
    private fun tierFile(scope: MemoryScope, id: Long, tier: MemoryTier): File {
        return File(scopeDir(scope, id), "${tier.name.lowercase()}.json")
    }

    /**
     * 获取索引文件
     */
    private fun indexFile(scope: MemoryScope, id: Long): File {
        return File(scopeDir(scope, id), "index.json")
    }

    /**
     * 加载某层的所有记忆
     */
    fun loadTier(scope: MemoryScope, id: Long, tier: MemoryTier): List<MemoryItem> {
        val file = tierFile(scope, id, tier)
        if (!file.exists()) return emptyList()
        return runCatching {
            json.decodeFromString<List<MemoryItem>>(file.readText())
        }.getOrElse {
            emptyList()
        }
    }

    /**
     * 保存某层的所有记忆（全量覆盖）
     */
    fun saveTier(scope: MemoryScope, id: Long, tier: MemoryTier, items: List<MemoryItem>) {
        val file = tierFile(scope, id, tier)
        runCatching {
            file.writeText(json.encodeToString(items))
        }
    }

    /**
     * 加载索引
     */
    fun loadIndex(scope: MemoryScope, id: Long): SerializedIndex? {
        val file = indexFile(scope, id)
        if (!file.exists()) return null
        return runCatching {
            json.decodeFromString<SerializedIndex>(file.readText())
        }.getOrNull()
    }

    /**
     * 保存索引
     */
    fun saveIndex(scope: MemoryScope, id: Long, index: SerializedIndex) {
        val file = indexFile(scope, id)
        runCatching {
            file.writeText(json.encodeToString(index))
        }
    }

    /**
     * 删除整个作用域目录
     */
    fun deleteScope(scope: MemoryScope, id: Long) {
        scopeDir(scope, id).deleteRecursively()
    }

    /**
     * 检查作用域是否存在
     */
    fun exists(scope: MemoryScope, id: Long): Boolean {
        return scopeDir(scope, id).exists()
    }

    /**
     * 获取作用域占用空间（字节）
     */
    fun size(scope: MemoryScope, id: Long): Long {
        val dir = scopeDir(scope, id)
        if (!dir.exists()) return 0
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}
