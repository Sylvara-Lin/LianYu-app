# AI 记忆管理系统优化实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现独立的AI记忆管理系统，支持跨会话（群聊↔私聊）记忆同步，分层存储（短期/中期/长期），不动数据库，降低内存占用40-60%。

**Architecture:** 内存+JSON文件双层持久化，全局+角色双层记忆架构，倒排索引+中文分词，时间+重要度驱动的分层管理。

**Tech Stack:** Kotlin, kotlinx.serialization.json, ConcurrentHashMap, Android Context文件存储

**设计文档:** `docs/superpowers/specs/2026-06-24-memory-system-optimization-design.md`

---

## 文件结构

### 新建文件（core/common 模块）
- `core/common/src/main/java/com/lianyu/ai/common/memory/MemoryItem.kt` — 记忆数据模型
- `core/common/src/main/java/com/lianyu/ai/common/memory/MemoryTokenizer.kt` — 中文分词
- `core/common/src/main/java/com/lianyu/ai/common/memory/MemoryIndex.kt` — 倒排索引
- `core/common/src/main/java/com/lianyu/ai/common/memory/MemoryStore.kt` — JSON文件持久化
- `core/common/src/main/java/com/lianyu/ai/common/memory/MemoryManager.kt` — 核心管理器（单例）

### 修改文件
- `core/common/build.gradle.kts` — 添加 kotlinx-serialization-json 依赖
- `core/network/src/main/java/com/lianyu/ai/network/AiService.kt` — 接入新记忆系统
- `feature/groupchat/src/main/java/com/lianyu/ai/feature/groupchat/GroupChatViewModel.kt` — 群聊接入记忆+底层优化

---

## Task 1: 添加依赖

**Files:**
- Modify: `core/common/build.gradle.kts`

- [ ] **Step 1: 添加 kotlinx-serialization-json 依赖**

在 `core/common/build.gradle.kts` 的 dependencies 块添加：
```kotlin
implementation(libs.kotlinx.serialization.json)
```

- [ ] **Step 2: 验证依赖解析**

Run: `./gradlew :core:common:dependencies --configuration implementation | Select-String "serialization"`
Expected: 显示 kotlinx-serialization-json

---

## Task 2: 创建 MemoryItem 数据模型

**Files:**
- Create: `core/common/src/main/java/com/lianyu/ai/common/memory/MemoryItem.kt`

- [ ] **Step 1: 创建记忆数据模型**

```kotlin
package com.lianyu.ai.common.memory

import kotlinx.serialization.Serializable

@Serializable
enum class MemoryCategory { FACT, EMOTION, PREFERENCE, EVENT, HABIT, RELATIONSHIP }

@Serializable
enum class MemorySource { CHAT, GROUP_CHAT, MANUAL }

@Serializable
enum class MemoryTier { SHORT, MID, LONG }

@Serializable
enum class MemoryScope { GLOBAL, COMPANION, GROUP }

@Serializable
data class MemoryItem(
    val id: String,
    val content: String,
    val category: MemoryCategory,
    val importance: Float,
    val timestamp: Long,
    val lastAccessed: Long,
    val accessCount: Int = 1,
    val source: MemorySource,
    val sourceId: Long,
    val scope: MemoryScope,
    val tags: List<String> = emptyList(),
    val expireAt: Long? = null,
    val tier: MemoryTier = MemoryTier.SHORT
)
```

---

## Task 3: 创建 MemoryTokenizer（中文分词）

**Files:**
- Create: `core/common/src/main/java/com/lianyu/ai/common/memory/MemoryTokenizer.kt`

- [ ] **Step 1: 实现中文分词器**

基于标点+停用词的简易分词，不引入外部依赖。

```kotlin
package com.lianyu.ai.common.memory

object MemoryTokenizer {
    private val delimiters = Regex("[，。！？、；：""''（）【】《》\\s\\n\\r\\t,.!?;:\"'()<>\\[\\]@#￥%…&*+=|/\\\\-]")
    private val stopWords = setOf("的","了","是","我","你","他","她","它","们","这","那","有","不","在","也","都","就","要","会","能","和","与","或","但","而","如","因","为","所","以","于","把","被","让","使","给","对","向","从","到","由","用","以","按","照","根据","这个","那个","什么","怎么","为什么","哪里","哪个","哪些","一些","一点","一下","一直","一定","一样","这种","那种","这样","那样","这里","那里","他们","她们","它们","我们","你们","自己","别人","大家","现在","以前","以后","已经","正在","将要","可以","应该","需要","必须","可能","也许","大概","或许","确实","真的","其实","只是","只有","只要","只能","只好","不仅","而且","并且","或者","还是","虽然","但是","然而","不过","如果","即使","尽管","无论","除非","一旦","一边","一方面","另一方面","由于","所以","因此","于是","然后","接着","最后","首先","其次","再次","另外","此外","而且","不仅","不但","不光","只不过","而不是","而非","以免","以便","从而","进而","况且","何况","甚至","纵然","哪怕","即便","就算","假如","假使","倘若","倘使","要是","若是","万一","一旦","一时","一向","一直","一阵","一些","一点","一下","一次","一切","所有","整个","全部","完全","充分","足够","十分","非常","特别","尤其","格外","分外","异常","相当","颇","挺","蛮","怪","够","多","多么","这么","那么","这些","那些","什么","怎么","为什么","怎样","如何","多少","几","若干","许多","大量","少量","少许","一点","一些","一下","一次","一切","所有","整个","全部","完全","充分","足够","十分","非常","特别","尤其","格外","分外","异常","相当")

    fun tokenize(text: String): List<String> {
        return text.split(delimiters)
            .map { it.trim() }
            .filter { it.length >= 2 && it !in stopWords }
            .distinct()
    }

    fun similarity(a: String, b: String): Float {
        val tokensA = tokenize(a).toSet()
        val tokensB = tokenize(b).toSet()
        if (tokensA.isEmpty() || tokensB.isEmpty()) return 0f
        val intersection = tokensA.intersect(tokensB).size
        val union = tokensA.union(tokensB).size
        return intersection.toFloat() / union.toFloat()
    }
}
```

---

## Task 4: 创建 MemoryIndex（倒排索引）

**Files:**
- Create: `core/common/src/main/java/com/lianyu/ai/common/memory/MemoryIndex.kt`

- [ ] **Step 1: 实现倒排索引**

```kotlin
package com.lianyu.ai.common.memory

import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class SerializedIndex(
    val keywordToIds: Map<String, List<String>>,
    val categoryToIds: Map<String, List<String>>,
    val importanceSorted: List<String>,
    val timeSorted: List<String>
)

class MemoryIndex {
    private val keywordToIds = ConcurrentHashMap<String, MutableSet<String>>()
    private val categoryToIds = ConcurrentHashMap<MemoryCategory, MutableSet<String>>()
    private val importanceSorted = ConcurrentHashMap<String, Float>()
    private val timeSorted = ConcurrentHashMap<String, Long>()

    fun add(item: MemoryItem) {
        val tokens = MemoryTokenizer.tokenize(item.content)
        tokens.forEach { token ->
            keywordToIds.getOrPut(token) { ConcurrentHashMap.newKeySet() }.add(item.id)
        }
        categoryToIds.getOrPut(item.category) { ConcurrentHashMap.newKeySet() }.add(item.id)
        importanceSorted[item.id] = item.importance
        timeSorted[item.id] = item.timestamp
        item.tags.forEach { tag ->
            keywordToIds.getOrPut(tag) { ConcurrentHashMap.newKeySet() }.add(item.id)
        }
    }

    fun remove(id: String) {
        keywordToIds.values.forEach { it.remove(id) }
        categoryToIds.values.forEach { it.remove(id) }
        importanceSorted.remove(id)
        timeSorted.remove(id)
        keywordToIds.entries.removeAll { it.value.isEmpty() }
    }

    fun search(query: String, category: MemoryCategory? = null, limit: Int = 5): List<String> {
        val tokens = MemoryTokenizer.tokenize(query)
        if (tokens.isEmpty()) {
            return importanceSorted.entries
                .sortedByDescending { it.value }
                .take(limit)
                .map { it.key }
        }
        val candidateScores = ConcurrentHashMap<String, Int>()
        tokens.forEach { token ->
            keywordToIds[token]?.forEach { id ->
                candidateScores.compute(id) { _, v -> (v ?: 0) + 1 }
            }
        }
        var result = candidateScores.entries.sortedByDescending { it.value }.map { it.key }
        if (category != null) {
            val catIds = categoryToIds[category] ?: emptySet()
            result = result.filter { it in catIds }
        }
        return result.take(limit)
    }

    fun serialize(): SerializedIndex {
        return SerializedIndex(
            keywordToIds = keywordToIds.mapValues { it.value.toList() },
            categoryToIds = categoryToIds.mapKeys { it.key.name }.mapValues { it.value.toList() },
            importanceSorted = importanceSorted.entries.sortedByDescending { it.value }.map { it.key },
            timeSorted = timeSorted.entries.sortedByDescending { it.value }.map { it.key }
        )
    }

    fun deserialize(data: SerializedIndex) {
        keywordToIds.clear()
        categoryToIds.clear()
        importanceSorted.clear()
        timeSorted.clear()
        data.keywordToIds.forEach { (k, v) ->
            keywordToIds[k] = ConcurrentHashMap.newKeySet<String>().apply { addAll(v) }
        }
        data.categoryToIds.forEach { (k, v) ->
            val cat = MemoryCategory.valueOf(k)
            categoryToIds[cat] = ConcurrentHashMap.newKeySet<String>().apply { addAll(v) }
        }
        data.importanceSorted.forEach { id ->
            importanceSorted[id] = 1f
        }
        data.timeSorted.forEach { id ->
            timeSorted[id] = 0L
        }
    }

    fun clear() {
        keywordToIds.clear()
        categoryToIds.clear()
        importanceSorted.clear()
        timeSorted.clear()
    }
}
```

---

## Task 5: 创建 MemoryStore（JSON文件持久化）

**Files:**
- Create: `core/common/src/main/java/com/lianyu/ai/common/memory/MemoryStore.kt`

- [ ] **Step 1: 实现 JSON 文件持久化**

```kotlin
package com.lianyu.ai.common.memory

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class MemoryStore(private val context: Context, private val deviceId: String) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val baseDir: File by lazy {
        File(context.filesDir, "memory/$deviceId").apply { mkdirs() }
    }

    private fun scopeDir(scope: MemoryScope, id: Long): File {
        val dirName = when (scope) {
            MemoryScope.GLOBAL -> "global"
            MemoryScope.COMPANION -> "companion_$id"
            MemoryScope.GROUP -> "group_$id"
        }
        return File(baseDir, dirName).apply { mkdirs() }
    }

    private fun tierFile(scope: MemoryScope, id: Long, tier: MemoryTier): File {
        return File(scopeDir(scope, id), "${tier.name.lowercase()}.json")
    }

    private fun indexFile(scope: MemoryScope, id: Long): File {
        return File(scopeDir(scope, id), "index.json")
    }

    fun loadTier(scope: MemoryScope, id: Long, tier: MemoryTier): List<MemoryItem> {
        val file = tierFile(scope, id, tier)
        if (!file.exists()) return emptyList()
        return runCatching {
            json.decodeFromString<List<MemoryItem>>(file.readText())
        }.getOrElse { emptyList() }
    }

    fun saveTier(scope: MemoryScope, id: Long, tier: MemoryTier, items: List<MemoryItem>) {
        val file = tierFile(scope, id, tier)
        runCatching {
            file.writeText(json.encodeToString(items))
        }
    }

    fun loadIndex(scope: MemoryScope, id: Long): SerializedIndex? {
        val file = indexFile(scope, id)
        if (!file.exists()) return null
        return runCatching {
            json.decodeFromString<SerializedIndex>(file.readText())
        }.getOrNull()
    }

    fun saveIndex(scope: MemoryScope, id: Long, index: SerializedIndex) {
        val file = indexFile(scope, id)
        runCatching {
            file.writeText(json.encodeToString(index))
        }
    }

    fun deleteScope(scope: MemoryScope, id: Long) {
        scopeDir(scope, id).deleteRecursively()
    }
}
```

---

## Task 6: 创建 MemoryManager（核心管理器）

**Files:**
- Create: `core/common/src/main/java/com/lianyu/ai/common/memory/MemoryManager.kt`

- [ ] **Step 1: 实现核心管理器**

包含分层记忆管理、同步机制、查询接口。详见实现代码。

---

## Task 7: 改造 AiService 接入新记忆系统

**Files:**
- Modify: `core/network/src/main/java/com/lianyu/ai/network/AiService.kt`

- [ ] **Step 1: 在 AiService 中注入 MemoryManager**
- [ ] **Step 2: 替换 getEnrichedContext 调用为 MemoryManager.getMemoryContext**
- [ ] **Step 3: 替换 extractAndSaveMemories 为 MemoryManager.saveMemory**

---

## Task 8: 群聊接入记忆系统

**Files:**
- Modify: `feature/groupchat/src/main/java/com/lianyu/ai/feature/groupchat/GroupChatViewModel.kt`

- [ ] **Step 1: 在群聊 prompt 中注入记忆上下文**
- [ ] **Step 2: 群聊回复后提取记忆**

---

## Task 9: 群聊底层优化

**Files:**
- Modify: `feature/groupchat/src/main/java/com/lianyu/ai/feature/groupchat/GroupChatViewModel.kt`

- [ ] **Step 1: 上下文全可见+身份保留（废弃隔离历史）**
- [ ] **Step 2: 串行调度（废弃并发）**
- [ ] **Step 3: 统一回复函数（合并双版本）**
- [ ] **Step 4: 时间感知注入**
- [ ] **Step 5: 性格特征提取升级**
- [ ] **Step 6: 消息拆分智能化**

---

## Task 10: 构建验证

- [ ] **Step 1: 运行 `./gradlew :core:common:assembleDebug`**
- [ ] **Step 2: 运行 `./gradlew :core:network:assembleDebug`**
- [ ] **Step 3: 运行 `./gradlew :feature:groupchat:assembleDebug`**
- [ ] **Step 4: 运行 `./gradlew assembleDebug`**
