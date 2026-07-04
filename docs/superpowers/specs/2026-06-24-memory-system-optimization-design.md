# AI 记忆管理系统优化设计文档

**日期**: 2026-06-24
**状态**: 已批准
**作者**: AI Assistant + User

## 一、背景与目标

### 1.1 现有问题

当前 LianYu 项目的 AI 记忆系统存在以下核心问题：

1. **群聊零记忆能力**：`feature/groupchat` 模块完全未接入记忆系统，群聊中 AI 无法回忆任何用户信息
2. **记忆隔离无法共享**：记忆严格按 `companionId` 隔离，不同角色无法共享用户信息
3. **中文去重失效**：记忆去重使用空格分词（`split(" ")`），对中文文本无效，导致重复记忆堆积
4. **关键词提取 Bug**：`extractMemoryKeywords()` 正则期望 `【核心记忆】` 格式，但实际输出 `[FACT]` 格式，导致上下文压缩时记忆关键词丢失
5. **contextLimit 参数未使用**：`getEnrichedContext()` 硬编码检索 10 条，传入的 `contextLimit` 参数被忽略
6. **LIKE 查询无索引**：`content LIKE '%query%'` 全表扫描，记忆数量增大后查询性能下降
7. **每次对话都触发记忆检索**：5 处调用点无缓存层
8. **临时记忆无 TTL**：仅按数量（20条）清理，不按时间过期

### 1.2 优化目标

- 实现群聊与私聊之间的跨会话记忆同步
- 替换 prompt-based core memory 为高效结构化存储
- 建立分层记忆架构（短期/中期/长期）
- 内存占用降低 40-60%
- 检索延迟 P99 < 20ms，同步延迟 P99 < 10ms
- 不修改数据库结构（完全独立于 Room 数据库）

## 二、架构设计

### 2.1 双层记忆架构

#### 全局用户记忆池（UserGlobalMemory）
- **范围**：跨所有会话共享的用户级记忆
- **内容**：用户偏好、事实、习惯、关系等不依赖特定角色的信息
- **同步**：所有会话（单聊/群聊）均可读写
- **目的**：解决"换个角色就忘了用户是谁"的问题

#### 角色级记忆（RoleMemory）
- **范围**：按 `companionId` 隔离的角色互动记忆
- **内容**：与特定角色的对话事件、情感时刻、约定等
- **同步**：仅该角色相关会话可访问
- **目的**：保持角色互动的连贯性

#### 群聊会话记忆（GroupSessionMemory）
- **范围**：按 `groupId` 隔离的群聊事件记忆
- **内容**：群聊事件、角色互动、群约定
- **同步**：该群所有AI角色可共享访问
- **目的**：让群聊角色能回忆群聊历史

### 2.2 分层记忆架构（时间+重要度驱动）

#### 短期记忆（ShortTerm）
- **生命周期**：当前会话，≤5分钟未访问
- **存储**：纯内存（`ConcurrentHashMap`）
- **内容**：最近对话上下文、临时情感状态
- **淘汰**：LRU + TTL，超时自动清除
- **容量限制**：每个会话 ≤50 条

#### 中期记忆（MidTerm）
- **生命周期**：最近7天
- **存储**：内存缓存 + JSON文件持久化
- **内容**：近期事件、对话摘要、互动记录
- **晋级**：重要度≥0.7的短期记忆自动晋级
- **降级**：7天未访问且重要度<0.5自动降级到归档
- **容量限制**：内存缓存 LRU ≤500 条

#### 长期记忆（LongTerm）
- **生命周期**：永久（直到手动删除或重要度降至阈值以下）
- **存储**：JSON文件持久化 + 内存索引
- **内容**：核心事实、用户偏好、重要约定、关系
- **晋级**：重要度≥0.8且被访问≥3次的中期记忆晋级

## 三、存储设计

### 3.1 文件结构（完全独立于数据库）

```
memory/
└── {deviceId}/                    # 用户隔离
    ├── global/                    # 全局用户记忆
    │   ├── index.json             # 倒排索引
    │   ├── long_term.json         # 长期记忆
    │   └── mid_term.json          # 中期记忆
    ├── companion_{id}/            # 角色级记忆
    │   ├── index.json
    │   ├── long_term.json
    │   └── mid_term.json
    └── group_{id}/                # 群聊会话记忆
        ├── index.json
        └── mid_term.json
```

### 3.2 记忆条目数据结构

```kotlin
data class MemoryItem(
    val id: String,                  // UUID
    val content: String,             // 记忆内容
    val category: MemoryCategory,    // FACT/EMOTION/PREFERENCE/EVENT/HABIT/RELATIONSHIP
    val importance: Float,           // 0.0-1.0
    val timestamp: Long,             // 创建时间
    val lastAccessed: Long,          // 最后访问时间
    val accessCount: Int,            // 访问次数
    val source: MemorySource,        // CHAT/GROUP_CHAT/MANUAL
    val sourceId: Long,              // companionId 或 groupId
    val tags: List<String>,          // 语义标签
    val expireAt: Long?              // 过期时间(null=永久)
)

enum class MemorySource { CHAT, GROUP_CHAT, MANUAL }
```

### 3.3 压缩技术

- **内容压缩**：重复模式合并（如多次"喜欢咖啡"合并为一条，accessCount递增）
- **索引压缩**：倒排索引使用位图压缩
- **文件压缩**：JSON文件定期压缩归档（gzip，可选）

## 四、记忆同步机制

### 4.1 同步流程

```
单聊对话 → 提取记忆 → 写入角色记忆池
                      ↓
                 全局同步过滤器 → 重要记忆写入全局池
                      ↓
群聊对话 → 提取记忆 → 写入群聊记忆池
                      ↓
                 全局同步过滤器 → 重要记忆写入全局池
```

### 4.2 同步过滤器（记忆筛选）

**晋级全局池条件**：
- 重要度 ≥ 0.7
- 类别 ∈ {FACT, PREFERENCE, HABIT, RELATIONSHIP}

**排除条件**：
- EMOTION 类（情感是角色特定的）
- EVENT 类（事件是会话特定的）

**去重**：
- 基于 Jaccard 相似度系数 > 0.6 合并
- 中文分词后计算词集合交集

### 4.3 同步方向

- **单聊→全局**：用户偏好、事实自动同步
- **群聊→全局**：群聊中提及的用户信息自动同步
- **全局→单聊**：注入相关全局记忆到角色上下文
- **全局→群聊**：注入相关全局记忆到群聊上下文

### 4.4 同步延迟控制

- **内存层同步**：<10ms（直接内存操作）
- **文件持久化**：异步写入（不阻塞对话）
- **索引更新**：批量延迟更新（100ms窗口）

## 五、索引系统

### 5.1 倒排索引结构

```kotlin
data class MemoryIndex(
    val keywordToIds: Map<String, Set<String>>,        // 关键词→记忆ID
    val categoryToIds: Map<MemoryCategory, Set<String>>,// 类别→记忆ID
    val importanceSorted: List<String>,                 // 按重要度排序的ID列表
    val timeSorted: List<String>                        // 按时间排序的ID列表
)
```

### 5.2 中文分词

- 基于标点符号 + 停用词的简易分词（不引入外部依赖）
- 分词规则：按 `，。！？、；：""''（）【】《》\n\r\t ` 等分割
- 过滤停用词（的、了、是、我、你、他等单字）
- 最小词长 2 字符

### 5.3 查询优化

- **热查询缓存**：最近5个查询结果缓存（LRU）
- **分层查询**：先查内存索引，未命中再查文件
- **限制返回**：默认返回 top-5 最相关记忆
- **查询流程**：分词 → 索引交集 → 重要度排序 → 取topN

## 六、性能优化

### 6.1 内存占用优化

- 短期记忆：限制每个会话 ≤50 条
- 中期记忆内存缓存：LRU 限制总条目 ≤500
- 长期记忆：仅索引在内存，内容按需加载
- 预计内存占用降低 40-60%

### 6.2 检索延迟优化

- 内存索引查询：<5ms
- 文件加载：异步预加载热数据
- 查询缓存：LRU 缓存最近查询

### 6.3 内存清理/GC

- **定时清理**：每5分钟清理过期短期记忆
- **LRU淘汰**：内存缓存超限时淘汰最久未访问
- **文件归档**：中期记忆超7天归档到长期或删除

## 七、用户隔离与容错

### 7.1 用户隔离

- 按 `deviceId` 隔离（复用现有机制）
- 文件路径包含 deviceId：`memory/{deviceId}/global/...`

### 7.2 容错机制

- **文件读写失败**：降级为纯内存模式
- **索引损坏**：从 JSON 文件重建索引
- **同步失败**：记录日志，下次重试，不影响主对话
- **降级策略**：新系统失败时回退到现有 `MemoryRepository`

## 八、与现有系统集成

### 8.1 不动数据库

- 完全独立于 Room 数据库
- 现有 `memory_entries` 表保持不变（向后兼容）
- 新系统作为记忆层，逐步替代 prompt-based 注入

### 8.2 AiService 改造

- 新增 `MemoryManager`（单例）替代直接调用 `MemoryRepository.getEnrichedContext()`
- `MemoryManager` 统一管理全局+角色+群聊记忆
- 注入格式优化：结构化注入而非纯文本拼接

### 8.3 群聊接入记忆

- `GroupChatViewModel` 调用 `MemoryManager` 获取群聊记忆
- 群聊回复后提取记忆写入群聊记忆池
- 群聊角色可访问全局用户记忆

## 九、模块设计

### 9.1 核心类

```
core/common/
└── memory/
    ├── MemoryManager.kt           # 单例，统一入口
    ├── MemoryItem.kt              # 记忆数据模型
    ├── MemoryIndex.kt             # 倒排索引
    ├── MemoryStore.kt             # JSON文件持久化
    ├── MemoryTokenizer.kt         # 中文分词
    ├── MemoryExtractor.kt         # 记忆提取
    ├── MemorySyncFilter.kt        # 同步过滤器
    └── MemoryQueryCache.kt        # 查询缓存
```

### 9.2 接口设计

```kotlin
class MemoryManager {
    // 获取记忆上下文（注入AI对话）
    suspend fun getMemoryContext(
        companionId: Long?,
        groupId: Long?,
        query: String,
        limit: Int = 5
    ): String

    // 保存记忆（对话后提取）
    suspend fun saveMemory(
        content: String,
        category: MemoryCategory,
        importance: Float,
        source: MemorySource,
        sourceId: Long
    ): String

    // 手动管理
    suspend fun getMemories(scope: MemoryScope, id: Long): List<MemoryItem>
    suspend fun deleteMemory(id: String)
    suspend fun updateMemory(id: String, content: String?, importance: Float?)
}
```

## 十、测试方案

### 10.1 基准测试

- 记忆检索延迟：P50 < 5ms, P99 < 20ms
- 同步延迟：P99 < 10ms
- 内存占用：对比优化前后

### 10.2 场景测试

- 单聊→群聊记忆同步准确性
- 多并发会话记忆隔离
- 重启后记忆恢复完整性
- 中文记忆去重有效性

## 十一、实施顺序

1. 核心架构：MemoryItem、MemoryIndex、MemoryManager 骨架
2. 持久化层：MemoryStore（JSON文件读写）
3. 分层架构：短期/中期/长期记忆管理
4. 索引系统：倒排索引 + 中文分词
5. 同步机制：全局+角色双层同步
6. AiService 改造：接入新记忆系统
7. 群聊接入：GroupChatViewModel 记忆集成
8. 群聊底层优化：上下文全可见/串行调度/统一回复函数
9. 构建验证
