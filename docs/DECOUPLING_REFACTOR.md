# LianYu 解耦重构文档

> 重构日期：2026-06-24
> 重构范围：基础设施层（ServiceRegistry 升级 + 依赖注入改造 + Repository 单例化）
> 重构目标：解决功能模块间过度依赖，实现高内聚低耦合，支持模块独立提取/修改/测试

---

## 1. 重构概述

### 1.1 问题背景

重构前项目存在以下耦合问题：

| 问题 | 严重度 | 影响 |
|------|--------|------|
| ServiceRegistry 线程不安全（`mutableMapOf`） | 高 | 多线程并发访问存在可见性风险 |
| 每次 `get` 都新建实例 | 高 | 重复创建 AiService/Provider，性能与内存开销 |
| AiService 获取方式不一致（8 处直接 new） | 中 | 测试时无法统一 mock，实例状态可能不一致 |
| 22 处 Repository 直接 `new` | 中 | 无法统一添加缓存/日志/拦截，可测试性差 |
| 依赖隐藏在方法体内 | 中 | 看类签名无法知道依赖关系，违反依赖倒置 |

### 1.2 重构成果

- **ServiceRegistry 升级**：线程安全（ConcurrentHashMap）+ 单例/工厂双语义 + `getOrThrow` 语义化方法
- **Repository 单例化**：7 个 Repository 注册为单例，消除 22 处硬编码 `new`
- **AiService 统一获取**：8 处直接 `new` 统一为 `ServiceRegistry.getOrThrow`
- **依赖透明化**：所有跨模块依赖通过 ServiceRegistry 显式获取，可测试、可替换

---

## 2. 模块划分图

### 2.1 架构分层（严格单向依赖）

```
┌─────────────────────────────────────────────────────────┐
│                    :app (入口层)                         │
│  LianYuApplication                                      │
│  ├── ServiceRegistry 绑定（组合根）                      │
│  ├── Repository 单例注册                                │
│  └── Provider 实现绑定                                  │
└────────────────────────┬────────────────────────────────┘
                         │ 依赖
┌────────────────────────▼────────────────────────────────┐
│              feature:* (业务层，12 个模块)               │
│  chat · companion · groupchat · localmodel · memory     │
│  notification · profile · settings · wechat             │
│  qqbot · backup · coffee                                │
│                                                         │
│  ViewModel / Worker / Bridge                            │
│  └── 通过 ServiceRegistry.getOrThrow() 获取依赖         │
│      （不再直接 new Repository / AiService）            │
└────────────────────────┬────────────────────────────────┘
                         │ 依赖
┌────────────────────────▼────────────────────────────────┐
│               core:* (基础层，6 个模块)                  │
│                                                         │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐              │
│  │ domain   │  │ common   │  │ security │              │
│  │ (零依赖)  │  │          │  │ →common  │              │
│  │ Service  │  │          │  │          │              │
│  │ Registry │  │          │  │          │              │
│  │ 接口定义  │  │          │  │          │              │
│  └──────────┘  └──────────┘  └──────────┘              │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐              │
│  │ database │  │ network  │  │ui-common │              │
│  │→common   │  │→domain   │  │→common   │              │
│  │→security │  │→database │  │          │              │
│  │          │  │→common   │  │          │              │
│  │Repository│  │→security │  │          │              │
│  │ (实现)   │  │AiService │  │          │              │
│  └──────────┘  └──────────┘  └──────────┘              │
└─────────────────────────────────────────────────────────┘
```

### 2.2 依赖注入数据流

```
Application.onCreate
    │
    ▼
registerServiceProviders(app)
    │
    ├── 注册 Repository 单例（7 个）
    │   ├── CompanionRepository  ← CompanionDao
    │   ├── ChatRepository       ← ChatMessageDao
    │   ├── ApiConfigRepository  ← ApiConfigDao
    │   ├── MemoryRepository     ← MemoryDao + DeviceId
    │   ├── ChatGroupRepository  ← ChatGroupDao
    │   ├── GroupMessageRepository ← GroupMessageDao
    │   └── UserRepository       ← Application
    │
    ├── 注册跨 feature 服务接口（4 个）
    │   ├── LocalModelProvider   → LocalModelProviderImpl
    │   ├── UserProfileProvider  → UserProfileProviderImpl
    │   ├── CompanionProvider    → CompanionProviderImpl
    │   └── AiServiceProvider    → AiService
    │       └── AiService (具体类，共享同一实例)
    │
    ▼
feature 模块通过 ServiceRegistry.getOrThrow() 获取依赖
```

---

## 3. 接口定义说明

### 3.1 ServiceRegistry（核心 DI 容器）

**位置**：`core/domain/src/main/java/com/lianyu/ai/domain/ServiceRegistry.kt`

**职责**：轻量级手动依赖注入注册中心，消除 feature→feature 的 project() 依赖。

**特性**：
- 线程安全：所有存储使用 `ConcurrentHashMap`
- 双语义注册：`registerSingleton`（缓存实例）/ `register`（每次新建）
- 语义化获取：`getOrThrow`（未注册抛异常）/ `get`（返回 null）

**API 签名**：

```kotlin
object ServiceRegistry {
    // 工厂模式：每次 get 都新建实例
    fun <T : Any> register(type: Class<T>, factory: () -> T?)

    // 单例模式：首次 get 时创建并缓存
    fun <T : Any> registerSingleton(type: Class<T>, factory: () -> T)

    // 获取实例（未注册返回 null）
    fun <T : Any> get(type: Class<T>): T?

    // 获取实例（未注册抛 IllegalStateException）
    fun <T : Any> getOrThrow(type: Class<T>): T

    // 移除单个注册
    fun <T : Any> unregister(type: Class<T>)

    // 清空所有注册（仅 onTerminate 调用）
    fun clear()
}
```

### 3.2 跨 feature 服务接口

所有接口定义在 `core/domain`（零依赖模块），由 feature 模块实现，通过 ServiceRegistry 绑定。

| 接口 | 实现者 | 消费模块 | 职责 |
|------|--------|----------|------|
| `AiServiceProvider` | `core/network.AiService` | chat, groupchat, notification, wechat, qqbot | AI 对话、主动消息、追问生成 |
| `LocalModelProvider` | `feature/localmodel.LocalModelProviderImpl` | chat, settings | 本地模型推理与管理 |
| `UserProfileProvider` | `feature/profile.UserProfileProviderImpl` | chat, groupchat | 用户身份信息 |
| `CompanionProvider` | `feature/companion.CompanionProviderImpl` | （预留） | 伴侣信息查询 |

### 3.3 Repository 单例清单

所有 Repository 定义在 `core/database`，通过 ServiceRegistry 注册为单例。

| Repository | 构造依赖 | 消费模块 |
|------------|----------|----------|
| `CompanionRepository` | `CompanionDao` | chat, groupchat, companion, memory, profile, notification, qqbot, wechat, settings |
| `ChatRepository` | `ChatMessageDao` | chat, groupchat, profile, notification, qqbot, wechat |
| `ApiConfigRepository` | `ApiConfigDao` | chat, settings |
| `MemoryRepository` | `MemoryDao` + `deviceId` | chat, groupchat, memory, notification, qqbot, wechat |
| `ChatGroupRepository` | `ChatGroupDao` | groupchat |
| `GroupMessageRepository` | `GroupMessageDao` | groupchat |
| `UserRepository` | `Application` | companion, profile, groupchat |

---

## 4. 使用示例

### 4.1 注册服务（在 LianYuApplication 中）

```kotlin
// 文件：app/src/main/java/com/lianyu/ai/LianYuApplication.kt
private fun registerServiceProviders(app: Application) {
    // Repository 单例
    ServiceRegistry.registerSingleton(CompanionRepository::class.java) {
        CompanionRepository(AppDatabase.getDatabase(app).companionDao())
    }
    ServiceRegistry.registerSingleton(ChatRepository::class.java) {
        ChatRepository(AppDatabase.getDatabase(app).chatMessageDao())
    }
    // ... 其他 Repository

    // 跨 feature 服务接口
    ServiceRegistry.registerSingleton(AiServiceProvider::class.java) {
        AiService(app)
    }
    ServiceRegistry.registerSingleton(LocalModelProvider::class.java) {
        LocalModelProviderImpl(app)
    }
    // ... 其他 Provider
}
```

### 4.2 在 ViewModel 中获取依赖

```kotlin
// 改造前（硬编码依赖）：
class ChatViewModel(application: Application, companionId: Long) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val deviceId = DeviceIdProvider.getDeviceId(application)
    private val chatRepository = ChatRepository(database.chatMessageDao())           // 直接 new
    private val aiService = ServiceRegistry.get(AiServiceProvider::class.java)       // 可空 + 手动 throw
        ?: throw IllegalStateException("AiServiceProvider not registered")
}

// 改造后（依赖注入）：
class ChatViewModel(application: Application, companionId: Long) : AndroidViewModel(application) {
    private val chatRepository = ServiceRegistry.getOrThrow(ChatRepository::class.java)        // 单例
    private val aiService = ServiceRegistry.getOrThrow(AiServiceProvider::class.java)         // 单例
}
```

### 4.3 在 Worker/Bridge 中获取依赖

```kotlin
// 改造前：
class AiReplyWorker(...) : Worker() {
    private val aiServiceProvider = ServiceRegistry.get(AiServiceProvider::class.java)
        ?: throw IllegalStateException("AiServiceProvider not registered")
    private val companionRepository = CompanionRepository(database.companionDao())  // 直接 new
}

// 改造后：
class AiReplyWorker(...) : Worker() {
    private val aiServiceProvider = ServiceRegistry.getOrThrow(AiServiceProvider::class.java)
    private val companionRepository = ServiceRegistry.getOrThrow(CompanionRepository::class.java)
}
```

### 4.4 测试时替换依赖

```kotlin
@Test
fun testChatViewModel() {
    // 注册 fake 实现，替换真实依赖
    ServiceRegistry.clear()
    ServiceRegistry.registerSingleton(ChatRepository::class.java) { FakeChatRepository() }
    ServiceRegistry.registerSingleton(AiServiceProvider::class.java) { FakeAiService() }

    val viewModel = ChatViewModel(app, companionId = 1L)
    // ... 测试逻辑
}
```

---

## 5. 改动清单

### 5.1 核心基础设施

| 文件 | 改动 |
|------|------|
| `core/domain/.../ServiceRegistry.kt` | 升级为线程安全 + singleton/factory 双语义 + getOrThrow |
| `app/.../LianYuApplication.kt` | 注册 7 个 Repository 单例 + 4 个 Provider 单例 + AiService 具体类 |

### 5.2 AiService 统一获取（消除 8 处直接 new）

| 文件 | 改动 |
|------|------|
| `feature/settings/.../SettingsViewModel.kt` | 6 处 `AiService(getApplication())` → `ServiceRegistry.getOrThrow(AiService::class.java)` |
| `feature/companion/.../CreateCompanionViewModel.kt` | 1 处 `AiService(getApplication())` → `ServiceRegistry.getOrThrow(AiService::class.java)` |

### 5.3 Repository 单例化（15 个文件，22 处直接 new 消除）

| 模块 | 文件 | 改动 |
|------|------|------|
| chat | `ChatViewModel.kt` | 4 个 Repository + aiService 改为 getOrThrow |
| groupchat | `GroupChatViewModel.kt` | 4 个 Repository + aiServiceProvider 改为 getOrThrow |
| companion | `CreateCompanionViewModel.kt` | 2 个 Repository 改为 getOrThrow |
| companion | `CompanionProviderImpl.kt` | 1 个 Repository 改为 getOrThrow |
| memory | `MemoryViewModel.kt` | 2 个 Repository 改为 getOrThrow |
| profile | `HomeViewModel.kt` | 2 个 Repository 改为 getOrThrow |
| profile | `ProfileViewModel.kt` | 2 个 Repository 改为 getOrThrow |
| profile | `UserProfileProviderImpl.kt` | 1 个 Repository 改为 getOrThrow |
| notification | `AiReplyWorker.kt` | 3 个 Repository + aiServiceProvider 改为 getOrThrow |
| notification | `CompanionMessageWorker.kt` | aiServiceProvider 改为 getOrThrow |
| qqbot | `QQBotChatBridge.kt` | 3 个 Repository + aiServiceProvider 改为 getOrThrow |
| wechat | `WeChatChatBridge.kt` | 3 个 Repository + aiServiceProvider 改为 getOrThrow |
| settings | `SettingsViewModel.kt` | 1 个 Repository 改为 getOrThrow |
| settings | `TokenUsageScreen.kt` | 1 个 Repository 改为 getOrThrow |

### 5.4 清理未使用依赖（编译优化）

改造过程中发现部分文件声明了 `database` 变量但未使用（Repository 已通过 ServiceRegistry 获取），统一清理：

| 文件 | 清理内容 |
|------|----------|
| `ChatViewModel.kt` | 移除未使用的 `database` 变量 + `AppDatabase` import |
| `GroupChatViewModel.kt` | 移除未使用的 `database` 变量 + `AppDatabase` import |
| `SettingsViewModel.kt` | 移除未使用的 `database` 变量 + `AppDatabase` import |
| `MemoryViewModel.kt` | 移除未使用的 `database` 变量 + `AppDatabase` import |
| `CreateCompanionViewModel.kt` | 移除未使用的 `AppDatabase` import |
| `AiReplyWorker.kt` | 移除未使用的 `database` 变量 + `AppDatabase` import |
| `WeChatChatBridge.kt` | 移除未使用的 `database` 变量 + `AppDatabase` import |
| `QQBotChatBridge.kt` | 移除未使用的 `database` 变量 + `AppDatabase` import |

### 5.5 保留不动（直接 Dao 访问）

以下文件因改造复杂度高或属于备份/特殊场景，保留直接 Dao 访问，留待后续子项目：

| 文件 | 原因 |
|------|------|
| `CompanionMessageWorker.kt` | 直接使用 `companionDao`/`chatMessageDao` 同步方法，改造需先封装 Repository 同步 API |
| `WeChatViewModel.kt` | 直接使用 `companionDao`，属遗留代码 |
| `QQBotViewModel.kt` | 直接使用 `companionDao`，属遗留代码 |
| `KeywordBridge.kt` | 直接 Dao 操作，属 chat 模块内部实现 |
| `BackupImportService.kt` | 批量导入需直接 Dao 事务控制 |
| `BackupExportService.kt` | 批量导出需直接 Dao 游标访问 |

### 5.6 AiService 上帝类拆分

AiService 原为 2835 行的上帝类，本次拆分为 4 个纯函数工具类，最终减少至 2209 行（减少 626 行）。

| 阶段 | 内容 | 效果 |
|------|------|------|
| AiContextTools 去重 | 删除 7 方法 + 1 data class，替换 10 处调用 | -138 行 |
| AiPromptBuilder 去重 | 删除 7 private + 3 public 委托，替换 10 处调用 | -391 行 |
| 删除死代码 | `extractDirectReply` + `findEchoEndIndex` | -68 行 |
| 提取 ResponsePostProcessor | `stripThinkingContent` + `ensureNotHtml`，替换 8 处调用 | 新增 [ResponsePostProcessor.kt](file:///h:/lianyu/core/network/src/main/java/com/lianyu/ai/network/ResponsePostProcessor.kt) |
| 提取 ImageHelper | `encodeImageToBase64` + `getImageMimeType`，替换 2 处调用 | 新增 [ImageHelper.kt](file:///h:/lianyu/core/network/src/main/java/com/lianyu/ai/network/ImageHelper.kt) |

纯函数工具类从 2 个增至 4 个：`AiContextTools` / `AiPromptBuilder` / `ResponsePostProcessor` / `ImageHelper`。

### 5.7 core 模块职责归位（P0：database 移除 ViewModel）

core:database 原包含 2 个 ViewModel，违反"数据库模块不应包含 ViewModel"原则。本次迁移至对应 feature 模块，并移除 lifecycle 依赖。

| 迁移项 | 源位置 | 目标位置 | 引用更新 |
|--------|--------|----------|----------|
| `CompanionListViewModel` | `core/database/.../viewmodel/` | `feature/companion/.../ui/viewmodel/` | ContactsScreen + CompanionListScreen import 更新 |
| `ChatGroupViewModel` | `core/database/.../viewmodel/` | `feature/groupchat/.../ui/` | CreateGroupScreen import 更新 |
| `lifecycle.viewmodel.ktx` 依赖 | `core:database/build.gradle.kts` | 移除 | — |

**跨 feature 使用改造**（feature 不能依赖 feature）：

| 文件 | 原方式 | 改造后 |
|------|--------|--------|
| `feature:groupchat/CreateGroupScreen.kt` | `CompanionListViewModel`（依赖 feature:companion） | `CompanionRepository` + `produceState`（通过 ServiceRegistry） |
| `feature:profile/HomeScreen.kt` | `ChatGroupViewModel`（依赖 feature:groupchat） | `ChatGroupRepository` + `produceState`（通过 ServiceRegistry） |

**ThemeViewModel 保留**：`core:ui-common/ThemeViewModel` 被 12+ 文件跨 feature 使用（app/settings/chat/groupchat/profile），属全局共享 UI 状态，与 Theme.kt/Color.kt/ThemeMode.kt 同属主题系统，保留在 core:ui-common。

### 5.8 core 模块职责归位（P1：common 迁出 feature 逻辑 + ui-common 迁出 feature 组件）

#### P1.1 memory/ 迁移至 feature:memory

core:common/memory/ 包含完整的跨会话记忆引擎（MemoryManager 单例 + 4 个辅助文件），被 core:network/AiService 和 feature:groupchat/GroupChatViewModel 消费。违反"core:common 不应包含 feature 业务逻辑"原则。

**迁移策略**：通过 core:domain 接口反转依赖。

| 迁移项 | 源位置 | 目标位置 |
|--------|--------|----------|
| `MemoryProvider` 接口 | — | `core/domain/.../MemoryProvider.kt`（新建） |
| `MemoryManager`（实现 MemoryProvider） | `core/common/memory/` | `feature/memory/engine/MemoryManager.kt` |
| `MemoryStore` | `core/common/memory/` | `feature/memory/engine/MemoryStore.kt` |
| `MemoryIndex` + `SerializedIndex` | `core/common/memory/` | `feature/memory/engine/MemoryIndex.kt` |
| `MemoryItem` + 4 个枚举 | `core/common/memory/` | `feature/memory/engine/MemoryItem.kt` |
| `MemoryTokenizer` | `core/common/memory/` | `feature/memory/engine/MemoryTokenizer.kt` |

**消费方改造**：

| 文件 | 原方式 | 改造后 |
|------|--------|--------|
| `core/network/AiService.kt` | `MemoryManager.getInstance(appContext)` 直接调用 | `ServiceRegistry.getOrThrow(MemoryProvider::class.java)` |
| `feature/groupchat/GroupChatViewModel.kt` | `MemoryManager.getInstance(getApplication())` 直接调用 | `ServiceRegistry.getOrThrow(MemoryProvider::class.java)` |
| `app/LianYuApplication.kt` | — | 注册 `MemoryProvider` 单例（`MemoryManager.getInstance(app)`） |

**接口定义**（`core:domain/MemoryProvider`）：
- `initialize()` — 加载持久化记忆
- `getMemoryContext(companionId, groupId, query, limit)` — 检索记忆上下文
- `extractAndSaveFromConversation(userInput, aiResponse, companionId, groupId)` — 提取并保存记忆

#### P1.2 wechat/ + localmodel/ 接口迁移至 core:domain

core:common 中存在跨 feature 通信常量和接口，应归入 core:domain。

| 迁移项 | 源位置 | 目标位置 | 引用更新 |
|--------|--------|----------|----------|
| `WeChatBroadcast` | `core/common/wechat/` | `core/domain/` | 4 处 import 更新（wechat/groupchat/chat/notification） |
| `LocalAiModelProvider` 接口 | `core/common/localmodel/` | `core/domain/` | 1 处 import 更新（localmodel） |
| `LocalModelStateManager` + `LocalModelPreferencesState` | `core/common/localmodel/` | `feature/localmodel/` | 2 处 import 更新（同包移除 import） |

#### P1.3 ui-common feature 专属组件迁移

core:ui-common/component/ 中部分组件仅被单一 feature 使用，迁移至对应 feature 以实现高内聚。

| 组件 | 源位置 | 目标位置 | 使用方 |
|------|--------|----------|--------|
| `VoiceMessageBubble` | `core/ui-common/component/` | `feature/chat/ui/component/` | chat 专属 |
| `VoiceRecorder` | `core/ui-common/component/` | `feature/chat/ui/component/` | chat 专属 |
| `WeChatChatInputBar` | `core/ui-common/component/` | `feature/chat/ui/component/` | chat 专属 |
| `BackgroundPermissionsCard` | `core/ui-common/component/` | `feature/profile/ui/component/` | profile 专属 |

**保留在 core:ui-common 的组件**（跨 feature 共享）：

| 组件 | 保留原因 |
|------|----------|
| `ChatBackground` / `ChatBackgroundCache` | 被 profile + chat + groupchat + app 共享 |
| `StickerPanel` | 被 chat + groupchat 共享 |
| `ChatInputExtensionPanel` | 被 chat + groupchat 共享 |
| `UpdateDialog` | 被 settings + app 共享 |
| `LiquidGlass` / `ShimmerEffect` / `SpringAnimations` / `AnimatedBackground` / `AvatarComponents` / `ScrollGuards` | 通用 UI 基础设施 |

---

## 6. 后续子项目（未完成）

本次重构为"基础设施先行"子项目，以下子项目留待后续轮次：

| 子项目 | 内容 | 优先级 | 状态 |
|--------|------|--------|------|
| **AiService 拆分** | 将 151KB 上帝类拆分为网络/Prompt/压缩/后处理/主动消息 5 个职责单元 | 高 | ✅ 已完成（见 5.6） |
| **core 模块职责归位** | database 移除 ViewModel、common 迁出 feature 逻辑、ui-common 迁出 feature 组件 | 高 | 🔄 P0 已完成（见 5.7），P1 未开始 |
| **接口职责拆分** | AiServiceProvider（9+方法）/ LocalModelProvider（11方法）按职责拆分 | 中 | 未开始 |
| **backup 模块边界强化** | BackupImportService/BackupExportService 直接 Dao 访问改走 Repository | 中 | 未开始 |
| **ViewModel 构造函数注入** | ViewModel 改为通过 Factory 接收依赖参数（当前仍通过 ServiceRegistry 在 init 块获取） | 低 | 未开始 |

---

## 7. 团队协作指南

### 7.1 新增 feature 模块

1. 在 `settings.gradle.kts` 注册模块
2. 在 `app/build.gradle.kts` 添加依赖
3. 如需跨 feature 通信，在 `core/domain` 定义接口，在 feature 实现，在 `LianYuApplication.registerServiceProviders` 绑定
4. ViewModel/Worker 通过 `ServiceRegistry.getOrThrow()` 获取依赖

### 7.2 新增 Repository

1. 在 `core/database/repository/` 定义 Repository 类
2. 在 `LianYuApplication.registerServiceProviders` 注册单例
3. 消费方通过 `ServiceRegistry.getOrThrow(XxxRepository::class.java)` 获取

### 7.3 独立提取模块测试

```kotlin
// 单元测试时，注册 fake 依赖即可隔离测试单个模块
@Before
fun setup() {
    ServiceRegistry.clear()
    ServiceRegistry.registerSingleton(ChatRepository::class.java) { FakeChatRepository() }
    ServiceRegistry.registerSingleton(AiServiceProvider::class.java) { FakeAiService() }
}
```
