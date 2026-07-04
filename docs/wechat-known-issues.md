# 微信 OpenClaw 通道 — 已知未闭环问题

> 由 Codex 源码分析检测，按影响体验程度排序。当前分支：`feature/wechat-openclaw-channel`

---

## P0 — 最影响实际体验

### 1. 后台实时收消息不完整（非真正实时）

**问题描述**：
- 当前后台依赖 `WorkManager` 每 15 分钟轮询一次（`WeChatPollingWorker`）。
- 代码注释写着 "use a foreground service for real-time polling"，但实际没有实现微信前台轮询 `Service`。
- 结果：用户发消息后，最长可能要等 15 分钟才能收到并自动回复。

**相关代码**：
- `feature/wechat/src/main/java/com/lianyu/ai/feature/wechat/service/WeChatPollingWorker.kt:50-51`

**修复方向**：
- 新增一个 `ForegroundService`（或 `Service + startForeground`），在绑定微信后启动，内部用长轮询/ WebSocket 实时拉取消息。
- `WorkManager` 可保留作为 Service 死掉后的兜底保活机制。

---

### 2. 前台场景自动回复不完整

**问题描述**：
- `WeChatMessageRepository.pollMessages()` 中，只有当 `!AppForegroundTracker.isInForeground` 时，才会触发通知和 `WeChatAiReplyWorker`。
- 如果 App 在前台（但用户不在微信设置页），消息被 `_incomingMessages.tryEmit(msg)` 发射到 `SharedFlow`，但没有消费者订阅处理。
- 结果：前台时收到微信消息，自动回复可能不触发。

**相关代码**：
- `feature/wechat/src/main/java/com/lianyu/ai/feature/wechat/data/WeChatMessageRepository.kt:129-144`

**修复方向**：
- 前台消息也应该被消费处理。可以在 `Application` 或某个常驻组件中订阅 `incomingMessages`，统一调度 `WeChatAiReplyWorker`。
- 或者去掉 `isInForeground` 的条件判断，让通知只在后台弹出，但 Worker 始终执行。

---

## P1 — 功能缺失

### 3. 微信只实现了文本消息

**问题描述**：
- `IlinkModels.kt` 已定义图片、语音、文件、视频等完整数据模型（`ImageItem`、`VoiceItem`、`FileItem`、`VideoItem`），以及 `getUploadUrl` / `sendTyping` 接口。
- 但 `WeChatMessageRepository` 中：
  - `sendTextMessage()` 只组装 `type = 1`（TEXT）的 `MessageItem`。
  - `extractText()` 只从 `itemList` 中提取 `textItem?.text`。
- 结果：用户发送图片/语音/文件时，bot 无法解析内容；bot 也无法发送非文本消息。

**相关代码**：
- `feature/wechat/src/main/java/com/lianyu/ai/feature/wechat/data/WeChatMessageRepository.kt:161-215`
- `feature/wechat/src/main/java/com/lianyu/ai/feature/wechat/data/model/IlinkModels.kt:69-109`

**修复方向**：
- 扩展 `sendXxxMessage()` 系列方法，支持图片/语音/文件/视频。
- 扩展 `extractXxx()`，让 AI 能"看到"图片/语音内容（可结合多模态模型或简单回复"暂不支持处理该类型消息"）。
- 接入 `getUploadUrl` + 直传 CDN 完成媒体发送。

---

### 4. 微信用户 ↔ AI 伴侣映射没有管理页面

**问题描述**：
- `WeChatUserMappingManager` 只有 `getOrCreateMapping()`，逻辑是：微信用户先发消息 → 自动绑定到默认/第一个 AI 伴侣。
- 没有 UI 可以查看当前有哪些微信用户被映射、修改某个用户对应哪个伴侣、删除映射。
- "主动转发 AI 消息到微信"也依赖这个映射，如果映射不对，转发就会发给错误的微信用户。

**相关代码**：
- `feature/wechat/src/main/java/com/lianyu/ai/feature/wechat/data/WeChatUserMappingManager.kt`

**修复方向**：
- 在 `WeChatSettingsScreen` 或新增 `WeChatUserMappingScreen` 中展示映射列表。
- 提供修改/删除/手动添加映射的操作。
- `WeChatTokenStore` 需要支持查询所有已映射的微信用户列表。

---

## P2 — 配置与测试缺失

### 5. 主动消息频率配置不完整

**问题描述**：
- `CompanionMessageWorker` 中有 `scheduleWithIntimacy()` 和 `calculateInterval()`，实现了"亲密度越高，主动消息越频繁"的逻辑。
- 但：
  - `scheduleWithIntimacy()` **从未被外部调用**（只在 `scheduleNextWithIntimacy` 内部使用）。
  - `WeChatSettingsScreen` 没有提供 UI 来写入 `custom_interval_minutes` / `use_custom_interval`。
  - 亲密度调度这套逻辑目前没有真正接入主流程。

**相关代码**：
- `feature/notification/src/main/java/com/lianyu/ai/feature/notification/CompanionMessageWorker.kt:97-118`
- `feature/wechat/src/main/java/com/lianyu/ai/feature/wechat/ui/WeChatSettingsScreen.kt`

**修复方向**：
- 在 `WeChatSettingsScreen`（或全局设置页）增加：
  - 开关：是否启用亲密度动态调度
  - 输入框：自定义间隔分钟数
- 确保 `scheduleWithIntimacy()` 被正确调用（例如在亲密度变化时重新调度）。

---

### 6. 微信模块缺少单元测试

**问题描述**：
- `feature:wechat:testDebugUnitTest` 执行结果为 `SKIPPED` / `NO-SOURCE`。
- 虽然有 `feature/notification/src/test/...` 等测试，但微信模块自身的业务逻辑（消息解析、映射管理、发送构建）没有自动化验证。
- "自动消息 → 微信转发"这条完整链路也缺少端到端测试。

**相关代码**：
- `feature/wechat/src/test/java/com/lianyu/ai/feature/wechat/data/WeChatIncomingMessagePolicyTest.kt`（可能为空或未注册到测试任务）

**修复方向**：
- 为 `WeChatMessageRepository`、`WeChatUserMappingManager`、`IlinkClient` 等核心类编写单元测试。
- 用 `MockWebServer` 模拟 ilink 接口，验证轮询/发送/登录流程。
- 确保 `feature:wechat:testDebugUnitTest` 能真正执行。

---

## 修复优先级建议

| 优先级 | 问题 | 影响 |
|--------|------|------|
| P0 | 1. 后台实时收消息 | 用户感知最强（消息延迟 0~15min） |
| P0 | 2. 前台自动回复 | 导致"有时能回有时不能"的不稳定感 |
| P1 | 4. 用户映射管理页 | 影响多用户/多伴侣场景 |
| P1 | 3. 非文本消息支持 | 基础功能缺失 |
| P2 | 5. 主动消息频率配置 | 影响 AI "陪伴感" |
| P2 | 6. 单元测试 | 影响长期可维护性 |
