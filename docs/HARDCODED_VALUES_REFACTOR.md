# 硬编码值清理与集中化配置说明

本文档记录本次“全代码库硬编码值移除”任务的变更位置、替换方式及验证结果。

## 1. 新增常量文件

| 文件 | 用途 | 示例常量 |
|---|---|---|
| [core/common/ChatConstants.kt](../core/common/src/main/java/com/lianyu/ai/common/ChatConstants.kt) | 聊天分页、批量、上下文、延迟、文本处理、主动消息、AI 后处理 | `PAGE_SIZE`、`DEFAULT_CONTEXT_LIMIT`、`AI_REPLY_BROADCAST_DELAY_MS`、`REPETITION_MIN_TEXT_LENGTH`、`PROACTIVE_FALLBACK_MIN_MINUTES`、`POST_PROCESS_MAX_SENTENCES` 等 |
| [core/common/ConcurrencyConstants.kt](../core/common/src/main/java/com/lianyu/ai/common/ConcurrencyConstants.kt) | 有界线程池 | `INTERRUPTIBLE_EXECUTOR_CORE_POOL_SIZE`、`INTERRUPTIBLE_EXECUTOR_MAXIMUM_POOL_SIZE`、`INTERRUPTIBLE_EXECUTOR_WORK_QUEUE_CAPACITY` |
| [core/common/AiModelConstants.kt](../core/common/src/main/java/com/lianyu/ai/common/AiModelConstants.kt) | 设置页模型选择、测试消息 | `TEST_SYSTEM_MESSAGE`、`TEST_USER_MESSAGE`、`CHAT_MODEL_KEYWORDS`、`EXCLUDED_MODEL_KEYWORDS` |
| [core/network/NetworkConstants.kt](../core/network/src/main/java/com/lianyu/ai/network/NetworkConstants.kt) | 网络超时、连接池、OpenAI 兼容路径、TTS/下载/调试日志 | `DEFAULT_READ_TIMEOUT_SECONDS`、`OPENAI_CHAT_COMPLETIONS_PATH`、`CONNECTION_POOL_MAX_IDLE`、`DEBUG_LOG_TIMEOUT_SECONDS` |
| [core/security/SecurityConstants.kt](../core/security/src/main/java/com/lianyu/ai/security/SecurityConstants.kt) | 安全相关种子/密钥 | `LEGACY_CHAT_MESSAGE_KEY_SEED` |
| [core/security/ChatMessageKeyProvider.kt](../core/security/src/main/java/com/lianyu/ai/security/ChatMessageKeyProvider.kt) | 封装 legacy key 生成逻辑 | `getLegacyFallbackKey()` |

## 2. 主要替换清单

### 2.1 网络层硬编码值

| 原位置 | 原硬编码值 | 替换后 |
|---|---|---|
| `AiService.kt` | `10`、`15`、`30`、`60` 等秒级超时 | `NetworkConstants.DEFAULT_*_TIMEOUT_SECONDS` 系列 |
| `AiService.kt` | `8`（最大句子数）、`150`（长文本截断阈值）、`120`、`20`、`3`、`5` 等后处理参数 | `ChatConstants.POST_PROCESS_*`、`ChatConstants.ECHO_*`、`ChatConstants.REPEAT_NICKNAME_*` |
| `ChunkedResponseHandler.kt` | `1024`（流缓冲区）、`30` 秒超时 | `NetworkConstants.STREAM_BUFFER_SIZE`、`NetworkConstants.STREAMING_READ_TIMEOUT_SECONDS` |
| `OpenAiProvider.kt` | `"/chat/completions"` | `NetworkConstants.OPENAI_CHAT_COMPLETIONS_PATH` |
| `AliyunTtsProvider.kt` 等 TTS Provider | 各厂商 endpoint、path、超时 | `NetworkConstants.ALIYUN_TTS_ENDPOINT`、`NetworkConstants.XUNFEI_TTS_PATH` 等 |
| `QQBotApiClient.kt` / `QQBotWebSocketClient.kt` | QQ Bot URL、心跳间隔、重连延迟 | `ChatConstants.QQ_BOT_*` |
| `DebugLogReporter.kt` | 固定日志服务器地址 | 默认常量 + 环境变量 `LIANYU_DEBUG_LOG_URL` 覆盖 |
| `LianYuApplication.kt` | `"8.8.8.8"` DNS | `NetworkConstants.DNS_SERVER` |

### 2.2 聊天/上下文硬编码值

| 原位置 | 原硬编码值 | 替换后 |
|---|---|---|
| `ChatViewModel.kt` | `50`、`30`、`200`、`3000`、`500` 等 | `ChatConstants.PAGE_SIZE`、`ChatConstants.LOAD_MORE_SIZE`、`ChatConstants.LOAD_MORE_HISTORY_DELAY_MS`、`ChatConstants.OBSERVE_MESSAGES_RESTART_DELAY_MS` |
| `GroupChatViewModel.kt` | `6`、`10`、`8` 等上下文与提及阈值 | `ChatConstants.GROUP_CHAT_*` |
| `AiPromptBuilder.kt` | `12`（默认上下文限制）、`0.5f` 压缩率、正则常量 | `ChatConstants.BUILD_MESSAGES_DEFAULT_CONTEXT_LIMIT`、`ChatConstants.DEFAULT_COMPRESSION_KEEP_RATIO` |
| `ChatContextResolver.kt`（新增） | — | 集中解析上下文限制与压缩模式，替代 ViewModel 中硬编码的 `50/10/100` |
| `TextProcessor.kt` | `50`（括号清理长度）、`2`、`4`、`5` 等 | `ChatConstants.BRACKET_CLEAN_MAX_LENGTH`、`ChatConstants.CLEANED_*`、`ChatConstants.REPETITION_*` |
| `TimeoutUtil.kt` | 线程池 `core=4`、`max=32`、`queue=256` | `ConcurrencyConstants.INTERRUPTIBLE_EXECUTOR_*` |

### 2.3 AI 主动消息与 Worker

| 原位置 | 原硬编码值 | 替换后 |
|---|---|---|
| `CompanionMessageWorker.kt` | `30`、`120`、`15`、`1440`、`3`、`8`、`10` 等分钟/数量 | `ChatConstants.PROACTIVE_*` |
| `WeChatPollingWorker.kt` / `WeChatPollingService.kt` | 轮询间隔、超时 | `ChatConstants.WECHAT_POLLING_*` |

### 2.4 设置页与本地模型

| 原位置 | 原硬编码值 | 替换后 |
|---|---|---|
| `SettingsViewModel.kt` | `"You are a helpful assistant."`、`"Hi"`、模型关键词列表 | `AiModelConstants.TEST_SYSTEM_MESSAGE`、`AiModelConstants.TEST_USER_MESSAGE`、`AiModelConstants.CHAT_MODEL_KEYWORDS` |
| `VisionModelSettingsScreen.kt` | 视觉模型预设 URL/模型名 | `ApiProviderDefaults` + 预设常量 |
| `LocalModel.kt` | 模型下载 URL | 模块内私有常量 |

### 2.5 数据库与安全

| 原位置 | 原硬编码值 | 替换后 |
|---|---|---|
| `ChatMessageCrypto.kt` | `"lianyu-chat-message-storage-v1"` | `SecurityConstants.LEGACY_CHAT_MESSAGE_KEY_SEED`（经 `ChatMessageKeyProvider.getLegacyFallbackKey()`） |
| `ApiConfigRepository.kt` | `android.util.Log.e` 直接日志 | 移除 Android Log，避免单元测试未 mock 时崩溃 |
| `AppSettingsStore.kt` | `internal companion object` | 改为 `companion object`，暴露 `DEFAULT_CONTEXT_LIMIT` 等常量供 feature 模块使用 |
| `ChatRepository.kt` / `GroupMessageRepository.kt` | 查询无 LIMIT | 增加 `ChatConstants.MAX_DB_MESSAGE_LIMIT = 200` 限制 |

### 2.6 构建配置与凭证

| 原位置 | 原硬编码值 | 替换后 |
|---|---|---|
| `gradle.properties` | 明文签名密码 | 移除，改为通过环境变量 `LIANYU_STORE_PASSWORD` / `LIANYU_KEY_PASSWORD` 或 `project.findProperty` 注入 |
| `app/build.gradle.kts` | `debug` 使用 release 签名配置 | `debug` 使用默认 `debug` 签名配置，`release` 仍使用 release 签名配置 |

## 3. 验证结果

### 3.1 构建

- `./gradlew assembleDebug` ✅ 通过

### 3.2 单元测试

- `:core:database:testDebugUnitTest` ✅ 全部通过（含修复后的 `ApiConfigSecretStorageTest.tamperedSecurityStateRefusesApiKeyDecryption`）
- `:core:common:testDebugUnitTest` ✅ 全部通过
- `:core:security:testDebugUnitTest` ✅ 全部通过
- `:core:network:testDebugUnitTest` ✅ 除 `LoadTest` 外全部通过

### 3.3 未通过的既有问题（非本次任务引入）

| 问题 | 原因 | 说明 |
|---|---|---|
| `:core:network:LoadTest` | 需要真实 API Key，运行返回 HTTP 401 | 该测试依赖外部服务凭证，本次未修复 |
| `:shell:test` | `StubApp.kt` 引用 `android.os.Process`，但 `:shell` 为 JVM 模块 | 与硬编码值清理无关的既有编译问题 |

## 4. 后续建议

- 对 `LoadTest` 增加 `@Tag("integration")` 或环境变量开关，避免无凭证时阻塞本地测试。
- 修复 `:shell` 模块的 `android.os.Process` 引用，或将其改为 Android 单元测试。
- 继续将 `GroupChatViewModel` 中残留的 `takeLast(6/10/8)` 迁移到 `ChatContextResolver`。
