# 恋语 (LianYu) 贡献者指南

> 欢迎加入恋语开源项目！本文档将帮助你快速上手，参与模块化开发。

---

## 目录

- [开发环境](#开发环境)
- [项目结构速览](#项目结构速览)
- [如何开始贡献](#如何开始贡献)
- [代码规范](#代码规范)
- [提交规范](#提交规范)
- [常见问题](#常见问题)

---

## 开发环境

### 必需工具

| 工具 | 版本 | 说明 |
|------|------|------|
| Android Studio | Hedgehog (2023.1.1) 或更高 | IDE |
| JDK | 17+ | Java 开发套件 |
| Android SDK | 35 | 编译 SDK |
| NDK | 30.0+ | 原生开发（安全模块需要） |
| Kotlin | 1.9.20 | 项目使用版本 |

### 推荐配置

1. **内存分配**：在 `Help > Edit Custom VM Options` 中设置：
   ```
   -Xms2048m
   -Xmx8192m
   ```

2. **Gradle 并行编译**：在 `gradle.properties` 中确认：
   ```properties
   org.gradle.parallel=true
   org.gradle.caching=true
   ```

### ⚠️ 环境约束（禁止修改）

> **项目环境配置由仓库统一维护，开发者不得擅自修改。**

以下文件/配置**严禁个人修改**，如有问题请提 Issue 或联系维护者：

| 配置项 | 文件路径 | 说明 |
|--------|----------|------|
| JDK 路径 | `gradle.properties` 中的 `org.gradle.java.home` | 统一指向项目指定的 JDK，确保全团队构建一致性 |
| Gradle 版本 | `gradle/wrapper/gradle-wrapper.properties` | 统一使用仓库指定的 Gradle 版本 |
| 依赖版本 | `gradle/libs.versions.toml` | 版本集中管理，禁止在模块 build.gradle 中硬编码版本号 |
| 仓库镜像 | `settings.gradle.kts` | 统一使用阿里云/Tencent 镜像，禁止私自更换 |

**违规后果**：修改环境配置会导致 CI/CD 构建失败、其他开发者无法编译，此类修改在 Code Review 中会被直接打回。

---

## 项目结构速览

```
LianYu/
├── app/                    ← 应用入口（Application + 导航）
├── core/                   ← 共享能力层
│   ├── common/            ← 工具类
│   ├── database/          ← Room 数据库
│   ├── network/           ← Retrofit 网络
│   ├── security/          ← JNI 安全加密
│   └── ui-common/         ← 主题 + 通用组件
├── feature/               ← 业务功能层
│   ├── companion/         ← AI伴侣管理
│   ├── chat/              ← 单聊
│   ├── groupchat/         ← 群聊
│   ├── memory/            ← 记忆系统
│   ├── settings/          ← 设置
│   ├── profile/           ← 个人中心
│   ├── update/            ← 应用更新
│   └── notification/      ← 通知与后台
└── docs/
    └── modularization/    ← 架构文档
```

### 快速理解模块

| 你想修改的功能 | 对应的模块 |
|---------------|-----------|
| 聊天界面/消息发送 | `feature:chat` |
| AI伴侣创建/编辑/列表 | `feature:companion` |
| 群聊功能 | `feature:groupchat` |
| AI记忆系统 | `feature:memory` |
| 主题/语言/设置 | `feature:settings` |
| 首页/个人中心/关于 | `feature:profile` |
| 通知推送/后台保活 | `feature:notification` |
| 应用内更新 | `feature:update` |
| 数据库表结构 | `core:database` |
| AI API 调用 | `core:network` |
| 主题颜色/通用组件 | `core:ui-common` |

---

## 如何开始贡献

### 第一步：Fork 仓库

1. 点击 GitHub 仓库右上角的 **Fork** 按钮
2. 克隆你的 Fork 到本地：
   ```bash
   git clone https://github.com/你的用户名/LianYu.git
   cd LianYu
   ```

### 第二步：创建分支

```bash
# 从 main 分支创建新分支
git checkout -b feature/你的功能名称

# 示例
git checkout -b feature/chat-voice-message
```

### 第三步：找到对应模块

根据你要修改的功能，定位到正确的模块目录：

```bash
# 例如修改聊天功能
cd feature/chat/src/main/java/com/lianyu/ai/feature/chat
```

### 第四步：开发并测试

1. 在对应模块中进行修改
2. 确保不引入模块间的循环依赖
3. 运行 `./gradlew assembleDebug` 验证构建
4. 在设备/模拟器上测试功能

### 第五步：提交代码

```bash
git add .
git commit -m "feat(chat): 添加语音消息发送功能

- 在 ChatScreen 中添加语音录制按钮
- 添加 VoiceMessageRecorder 组件
- 更新 ChatRepository 支持语音消息存储"

git push origin feature/chat-voice-message
```

### 第六步：创建 Pull Request

1. 在 GitHub 上打开你的 Fork
2. 点击 **Compare & pull request**
3. 填写 PR 描述，说明：
   - 修改了什么功能
   - 为什么需要这个修改
   - 如何测试
   - 相关 Issue（如有）

---

## 代码规范

### Kotlin 代码风格

- 使用 **4 空格缩进**
- 函数名使用 **camelCase**
- 类名使用 **PascalCase**
- 常量使用 **UPPER_SNAKE_CASE**
- 包名使用全小写

### Compose 规范

```kotlin
// Screen 命名：功能 + Screen
@Composable
fun ChatScreen(
    companionId: Long,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier  // 最后一个参数
) {
    // 实现
}

// ViewModel 命名：功能 + ViewModel
class ChatViewModel(
    private val repository: ChatRepository
) : ViewModel() {
    // 实现
}

// 组件命名：描述性名称
@Composable
fun ChatMessageBubble(
    message: ChatMessage,
    isFromUser: Boolean,
    modifier: Modifier = Modifier
) {
    // 实现
}
```

### 模块内文件组织

```
feature/xxx/
├── ui/
│   ├── screen/          # 页面级 Compose
│   ├── component/       # 可复用组件
│   └── viewmodel/       # ViewModel
├── data/
│   └── repository/      # Repository
└── di/                  # Hilt Module（如果使用）
```

### 依赖注入

推荐使用 Hilt。在 Feature 模块中创建 Module：

```kotlin
@Module
@InstallIn(ViewModelComponent::class)
object ChatModule {
    @Provides
    fun provideChatRepository(
        database: AppDatabase
    ): ChatRepository {
        return ChatRepository(database.chatMessageDao())
    }
}
```

---

## 提交规范

使用 **Conventional Commits** 规范：

```
<type>(<scope>): <subject>

<body>

<footer>
```

### Type 类型

| Type | 说明 |
|------|------|
| `feat` | 新功能 |
| `fix` | 修复 Bug |
| `docs` | 文档更新 |
| `style` | 代码格式（不影响功能） |
| `refactor` | 重构 |
| `perf` | 性能优化 |
| `test` | 测试相关 |
| `chore` | 构建/工具链 |

### Scope 范围

使用模块名作为 scope：

- `app` — 入口模块
- `core:database` — 数据库
- `core:network` — 网络
- `feature:chat` — 单聊
- `feature:companion` — 伴侣
- `feature:settings` — 设置
- ...

### 示例

```bash
# 新功能
feat(feature:chat): 添加消息长按复制功能

# Bug 修复
fix(core:network): 修复 DeepSeek API 超时问题

# 重构
refactor(feature:memory): 优化记忆提取算法

# 文档
docs: 更新模块化架构文档
```

---

## 常见问题

### Q: 我想新增一个功能，应该修改哪个模块？

**A:** 参考下表：

| 功能类型 | 操作 |
|---------|------|
| 扩展现有功能 | 修改对应的 `feature:*` 模块 |
| 全新独立功能 | 新建 `feature:新功能` 模块 |
| 通用工具/组件 | 添加到 `core:common` 或 `core:ui-common` |
| 数据库相关 | 修改 `core:database` |

### Q: 两个 Feature 需要共享代码怎么办？

**A:** 有三种方式：

1. **共享数据** → 通过 `core:database` 的 Entity
2. **共享工具** → 下沉到 `core:common`
3. **共享 UI 组件** → 下沉到 `core:ui-common`

**禁止** Feature 模块之间直接依赖！

### Q: 构建失败，提示找不到模块？

**A:** 检查以下几点：

1. `settings.gradle.kts` 中是否注册了模块？
   ```kotlin
   include(":feature:新模块")
   ```

2. 使用模块的 `build.gradle.kts` 中是否添加了依赖？
   ```kotlin
   implementation(project(":feature:新模块"))
   ```

3. 包名是否正确？
   - Feature 模块: `com.lianyu.ai.feature.xxx`
   - Core 模块: `com.lianyu.ai.xxx`

### Q: 如何运行单个模块的测试？

**A:**

```bash
# 运行 feature:chat 的测试
./gradlew :feature:chat:test

# 运行 core:database 的测试
./gradlew :core:database:test
```

### Q: 我想添加一个新的 AI 提供商，应该改哪里？

**A:**

1. 在 `core:network` 中添加新的 API 接口（参考 `OpenAiApi.kt`）
2. 在 `core:network/AiService.kt` 中添加调用逻辑
3. 在 `core:database/ApiConfig.kt` 的 `ApiProvider` enum 中添加新提供商
4. 在 `feature:settings` 中添加配置 UI

### Q: 修改数据库结构需要注意什么？

**A:**

1. **不要直接修改现有 Entity 的字段类型**（会导致崩溃）
2. 添加新字段时，设置默认值：
   ```kotlin
   val newField: String = ""
   ```
3. 修改 `AppDatabase` 中的版本号
4. **必须编写 Migration**：
   ```kotlin
   val MIGRATION_X_Y = object : Migration(X, Y) {
       override fun migrate(db: SupportSQLiteDatabase) {
           db.execSQL("ALTER TABLE table_name ADD COLUMN new_field TEXT NOT NULL DEFAULT ''")
       }
   }
   ```
5. 将 Migration 添加到 `MIGRATIONS` 数组中

---

## 获取帮助

- 查看 [MODULE_ARCHITECTURE.md](./MODULE_ARCHITECTURE.md) 了解架构设计
- 查看项目 [README.md](../README.md) 了解项目概况
- 提交 Issue 描述你遇到的问题
- 在 Discussion 中发起技术讨论

---

## 贡献者

感谢所有为恋语做出贡献的开发者！

<a href="https://github.com/linruoxo666/LianYu/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=linruoxo666/LianYu" />
</a>

---

> 让AI温暖你的每一天 💕
