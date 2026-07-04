# 固定数据库环境方案（v22 锁定）

## 1. 概述

LianYu 项目使用 **Room 2.7.2**（Android SQLite ORM）作为本地持久化层。为避免功能开发过程中频繁升级数据库版本（v17→v21 共 4 次升级，均为新增列），现建立**固定版本数据库环境**：一次性升级到 v22 并永久锁定，后续所有功能扩展通过 `ext_json` 扩展列吸收，不再执行 ALTER TABLE。

> **重要认知**：Room/SQLite 是嵌入式数据库，不存在服务端数据库的"连接池大小""内存分配"等配置参数。SQLite 的运行时参数（WAL 模式、page_size 等）由 Room 框架管理，开发者无需也无法调整。本方案聚焦于 **Schema 版本锁定** 与 **扩展字段预留**。

---

## 2. 环境配置

### 2.1 软件版本

| 组件 | 版本 | 锁定状态 |
|------|------|----------|
| Room Runtime | 2.7.2 | ✅ 锁定（`gradle/libs.versions.toml`） |
| Room Compiler (KSP) | 2.7.2 | ✅ 锁定 |
| Room KTX | 2.7.2 | ✅ 锁定 |
| SQLite | Android 系统内置 | 由 compileSdk 35 决定 |
| kotlinx-serialization-json | 项目 catalog 版本 | ✅ 锁定 |
| 数据库 Schema 版本 | **22** | ✅ **永久锁定** |

### 2.2 依赖配置

`core/database/build.gradle.kts`：

```kotlin
dependencies {
    api(libs.androidx.room.runtime)   // 2.7.2
    api(libs.androidx.room.ktx)       // 2.7.2
    ksp(libs.androidx.room.compiler)  // 2.7.2
    implementation(libs.kotlinx.serialization.json)
}
```

### 2.3 Schema 导出配置

```kotlin
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
```

Schema JSON 导出目录：`core/database/schemas/com.lianyu.ai.database.AppDatabase/`

当前基线文件：`22.json`（v22 锁定后的 Schema 快照）

### 2.4 SQLite 运行时参数

Room 默认配置（无需手动调整）：

| 参数 | 值 | 说明 |
|------|----|------|
| journal_mode | WAL | Room 默认启用 WAL，支持并发读写 |
| foreign_keys | ON | Room 默认启用外键约束 |
| synchronous | NORMAL | WAL 模式下的安全默认值 |

---

## 3. 版本锁定机制

### 3.1 锁定规则

1. **数据库版本永久锁定为 22**
2. **禁止新增 Entity 列**（ALTER TABLE）
3. **禁止修改现有列的类型或约束**
4. 所有功能扩展所需的实体属性，存入 `ext_json` 列

### 3.2 ext_json 扩展列

6 个业务表已添加 `ext_json TEXT NOT NULL DEFAULT '{}'` 列：

| 表 | 实体 | ext_json 用途示例 |
|----|------|-------------------|
| companions | CompanionEntity | 自定义角色属性、外观特征、关系标签 |
| chat_messages | ChatMessage | 消息元数据、情感标记、来源标注 |
| chat_groups | ChatGroup | 群聊规则、主题、公告 |
| group_messages | GroupMessage | 群消息元数据、回复引用 |
| memory_entries | MemoryEntry | 记忆衰减参数、关联图谱 |
| api_configs | ApiConfig | 厂商专属参数、代理配置 |

### 3.3 ext_json 读写 API

```kotlin
// 读取（类型安全，自动反序列化）
val tag: String? = companion.extJson.readExt("customTag")
val flags: List<String>? = companion.extJson.readExt("flags")

// 写入（返回新 JSON 字符串，配合 copy 使用）
val newExt = companion.extJson.writeExt("customTag", "value")
companion.copy(extJson = newExt)

// 移除
val cleared = companion.extJson.removeExt("customTag")

// 判断是否存在
if (companion.extJson.hasExt("customTag")) { ... }
```

API 定义：`core/database/src/main/java/com/lianyu/ai/database/model/ExtJson.kt`

### 3.4 CI 守卫脚本

`scripts/check-database-schema.ps1` 在 CI 流水线中执行三项检测：

1. **版本号检测**：`AppDatabase.kt` 中 `version` 必须为 22
2. **Schema 文件检测**：`schemas/` 目录不得出现 `23.json` 或更高版本
3. **Schema 完整性检测**：`22.json` 不得被修改（git diff 检查）

任一检测失败，CI 中断并提示开发者使用 `ext_json`。

---

## 4. 环境搭建步骤

### 4.1 新开发者搭建

1. **克隆仓库**

   ```bash
   git clone <repo-url>
   cd lianyu
   ```

2. **确认环境**
   - JDK 17（项目内置 `D:\and studio\jbr`）
   - Android SDK compileSdk 35
   - Gradle 9.4.1（wrapper 已配置）

3. **首次构建**

   ```bash
   ./gradlew assembleDebug
   ```

4. **验证数据库环境**

   ```bash
   ./scripts/check-database-schema.ps1
   ```

   输出 `ALL CHECKS PASSED` 即环境正常。

### 4.2 功能开发流程

当新功能需要存储额外数据时：

1. **评估**：新属性是否属于已有表的扩展？
   - 是 → 使用 `ext_json`
   - 否（需要全新表）→ 提交架构评审

2. **使用 ext_json 存储数据**

   ```kotlin
   // 写入
   val updated = companion.copy(
       extJson = companion.extJson.writeExt("voicePitch", 1.2f)
   )
   companionDao.updateCompanion(updated)

   // 读取
   val pitch: Float? = companion.extJson.readExt("voicePitch")
   ```

3. **提交前验证**

   ```bash
   ./scripts/check-database-schema.ps1
   ```

### 4.3 例外情况：新增表

如果功能需要全新的表（非扩展现有表），仍需升级版本：

1. 提交架构评审，说明为何不能用 ext_json
2. 在 `AppDatabase.kt` 添加新 Entity 和 `MIGRATION_22_23`
3. 更新 `version = 23` 和 `MIGRATIONS` 数组
4. 更新本文件中的锁定版本号
5. 更新 CI 脚本中的 `$LockedVersion`

> 此流程需团队评审通过后执行，个人不得擅自升级。

---

## 5. 兼容性测试报告

### 5.1 迁移路径验证

| 起始版本 | 目标版本 | 迁移方式 | 验证状态 |
|----------|----------|----------|----------|
| 17 | 22 | MIGRATION_17_18 → ... → MIGRATION_21_22 | ✅ 通过 |
| 18 | 22 | MIGRATION_18_19 → ... → MIGRATION_21_22 | ✅ 通过 |
| 19 | 22 | MIGRATION_19_20 → ... → MIGRATION_21_22 | ✅ 通过 |
| 20 | 22 | MIGRATION_20_21 → MIGRATION_21_22 | ✅ 通过 |
| 21 | 22 | MIGRATION_21_22 | ✅ 通过 |
| 22 | 22 | 无需迁移 | ✅ 通过 |

### 5.2 ext_json 列验证

| 表 | 列名 | 类型 | 默认值 | NOT NULL | 验证 |
|----|------|------|--------|----------|------|
| companions | extJson | TEXT | '{}' | ✅ | ✅ |
| chat_messages | extJson | TEXT | '{}' | ✅ | ✅ |
| chat_groups | extJson | TEXT | '{}' | ✅ | ✅ |
| group_messages | extJson | TEXT | '{}' | ✅ | ✅ |
| memory_entries | extJson | TEXT | '{}' | ✅ | ✅ |
| api_configs | extJson | TEXT | '{}' | ✅ | ✅ |

### 5.3 编译验证

| 模块 | 编译状态 |
|------|----------|
| core:database (KSP + Kotlin) | ✅ BUILD SUCCESSFUL |
| feature:backup (Kotlin) | ✅ BUILD SUCCESSFUL |

### 5.4 向后兼容性

- **旧版备份恢复**：BackupData 中的 Snapshot 类已添加 `extJson` 字段（默认值 `"{}"`），旧版备份文件（无 extJson）可正常导入
- **序列化兼容**：`@Serializable` 实体的 extJson 字段有默认值，kotlinx.serialization 自动处理缺失字段
- **DAO 查询兼容**：所有 DAO 使用 `SELECT *`，Room 自动映射新列

---

## 6. 保障措施

### 6.1 技术保障

| 措施 | 说明 |
|------|------|
| CI 守卫脚本 | `scripts/check-database-schema.ps1` 检测版本变更和 Schema 修改 |
| Schema JSON 基线 | `22.json` 作为不可变基线，git diff 检测任何修改 |
| ext_json API | 类型安全的读写函数，避免手写 JSON 字符串 |
| 迁移脚本完备 | v1→v22 全链路迁移脚本（21 个 Migration），覆盖所有历史版本 |

### 6.2 流程保障

| 措施 | 说明 |
|------|------|
| 版本升级评审 | 任何版本升级需团队评审，个人不得擅自修改 |
| 文档同步 | 版本升级时必须同步更新本文件和 CI 脚本 |
| 代码审查 | PR 审查时检查是否误改 Entity 定义 |
| 备份兼容性 | 版本升级时必须同步 BackupData 的 Snapshot 类 |

### 6.3 应急预案

| 场景 | 处理方式 |
|------|----------|
| 开发者误改 Entity | CI 脚本拦截，PR 无法合并 |
| ext_json 数据损坏 | JSON 解析失败时返回 null，不影响 App 运行 |
| 迁移失败 | AppDatabase.openVerifiedDatabase 自动从 recovery 备份恢复 |
| 需要新增表 | 按第 4.3 节流程评审后执行 |

---

## 7. 文件索引

| 文件 | 用途 |
|------|------|
| `core/database/src/main/java/com/lianyu/ai/database/AppDatabase.kt` | 数据库定义、版本号、迁移脚本 |
| `core/database/src/main/java/com/lianyu/ai/database/model/ExtJson.kt` | ext_json 读写 API |
| `core/database/src/main/java/com/lianyu/ai/database/model/*.kt` | 实体定义（含 extJson 字段） |
| `core/database/schemas/com.lianyu.ai.database.AppDatabase/22.json` | v22 Schema 基线 |
| `scripts/check-database-schema.ps1` | CI 守卫脚本 |
| `feature/backup/src/main/java/com/lianyu/ai/feature/backup/model/BackupData.kt` | 备份 Snapshot 定义（含 extJson） |
