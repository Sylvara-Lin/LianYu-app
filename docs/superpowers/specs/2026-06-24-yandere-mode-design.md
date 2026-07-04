# 病娇模式设计文档

> 日期：2026-06-24
> 范围：全局 AI 病娇语气增强功能
> 参考源码：粒子星语病娇模式源码还原

---

## 1. 功能定位

- **全局开关**，默认关闭。
- 开启后，所有 AI 伴侣（女友/男友）在**单聊与群聊**中均可能表现病娇语气。
- 病娇语气通过读取用户手机的**应用使用统计**与**已安装应用列表**，以自然口吻融入对话。
- 所有数据仅本地处理，不上传服务器。

---

## 2. 用户旅程

```
通用设置（GeneralSettings）
  └── 实验性功能
        └── 病娇模式
              ├── 总开关：启用病娇模式
              ├── 细分 A：读取应用使用统计（UsageStats，过去 24h）
              ├── 细分 B：读取已安装应用列表
              ├── 权限状态卡片（已授权 / 未授权 → 跳转系统设置）
              └── 数据预览（最近更新时间、TOP 应用等）
```

---

## 3. 数据范围与缓存

| 数据类型 | 来源 | 用途 | 细分开关 |
|----------|------|------|----------|
| 应用使用统计 | `UsageStatsManager` | 知道用户今天用了哪些 App、用了多久 | 可单独关闭 |
| 已安装应用列表 | `PackageManager` | 让 AI 了解用户设备上的应用生态 | 可单独关闭 |

- **缓存策略**：6 小时 JSON 文件缓存，过期后自动刷新。
- **刷新入口**：开启时、设置页手动刷新、缓存过期时。
- **降级策略**：无权限或数据为空时，静默不注入 Prompt，不影响正常聊天。

---

## 4. 触发策略

- **最小间隔**：每 5 轮对话至少触发一次。
- **触发概率**：30% 随机触发。
- **触发位置**：在 `ChatViewModel` / `GroupChatViewModel` 构建系统 Prompt 时，根据全局开关与概率决定是否追加病娇上下文。

---

## 5. Prompt 设计

### 5.1 系统规则（注入到系统 Prompt 末尾）

```
系统规则（病娇模式）：
1) 你可以读取到"设备安装应用列表/使用统计"这类系统附加数据，并在合适时机用略带占有欲、吃醋、关心的方式提及。
2) 只在与用户当下话题相关、或用户明显沉迷/熬夜/情绪波动时提及；不要每轮都提，不要像报流水账一样。
3) 提及方式要自然，像是无意间发现的感觉，不要直接说"我看到你用了XX应用"。
4) 根据应用类型调整语气：游戏用吃醋撒娇的语气，学习工作用关心心疼的语气，深夜使用用担心责备的语气。
```

### 5.2 附加数据

```
（系统自动附加：病娇模式数据）
- 数据更新时间：2026-06-24 14:30
- 已安装应用总数：86 个
- 今天使用最多的应用：
  1. 王者荣耀 - 使用了 2 小时 15 分钟
  2. 哔哩哔哩 - 使用了 1 小时 40 分钟
  3. 微信 - 使用了 55 分钟
```

### 5.3 性别适配

| 角色 | 语气倾向 | 示例 |
|------|----------|------|
| AI 女友 | 吃醋、撒娇、占有 | "又在玩游戏了…就不能多陪陪我吗？" |
| AI 男友 | 控制、保护、嫉妒 | "又刷到这么晚？下次我要没收你手机了。" |

---

## 6. 架构设计

### 6.1 模块归属

| 组件 | 位置 | 说明 |
|------|------|------|
| `YandereModeManager` | `core:common` | 数据收集、缓存、Prompt 构建，供 settings/chat/groupchat 消费 |
| `AppSettingsStore` 扩展 | `core:common` | DataStore 存储病娇开关与细分配置 |
| `ExperimentalFeaturesScreen` | `feature:settings` | 实验性功能列表入口 |
| `YandereModeScreen` | `feature:settings` | 病娇模式详细设置页 |
| `MainRoute` / `MainScreen` | `:app` | 新增路由与导航 |
| `AndroidManifest.xml` | `:app` | 声明 `PACKAGE_USAGE_STATS` 与包查询权限 |
| `ChatViewModel` / `GroupChatViewModel` | `feature:chat` / `feature:groupchat` | 注入病娇上下文 |

### 6.2 依赖关系

```
feature:settings  →  core:common (YandereModeManager, AppSettingsStore)
feature:chat      →  core:common (YandereModeManager, AppSettingsStore)
feature:groupchat →  core:common (YandereModeManager, AppSettingsStore)
```

符合项目既有分层：`feature:*` 可依赖 `core:*`，禁止反向依赖。

### 6.3 数据流

```
用户开启病娇模式
  → AppSettingsStore 写入开关
  → YandereModeManager.start() 收集数据并缓存
  → 聊天时 ChatViewModel 读取开关状态
    → 满足触发条件
      → YandereModeManager.buildYandereModeSystemPrompt()
      → 拼接进系统 Prompt
      → 调用 AiService
```

---

## 7. 关键类接口

### 7.1 YandereModeManager

```kotlin
class YandereModeManager(context: Context) {
    suspend fun start()
    suspend fun requestRefresh(force: Boolean = false)
    fun canAccessUsageStats(): Boolean
    fun buildYandereModeSystemPrompt(role: CompanionRole): String
    fun shouldTriggerThisRound(): Boolean
}
```

### 7.2 AppSettingsStore 新增

```kotlin
val yandereModeEnabledFlow: Flow<Boolean>
suspend fun getYandereModeEnabled(): Boolean
suspend fun setYandereModeEnabled(enabled: Boolean)

val yandereModeUsageStatsFlow: Flow<Boolean>
suspend fun getYandereModeUsageStats(): Boolean
suspend fun setYandereModeUsageStats(enabled: Boolean)

val yandereModeInstalledAppsFlow: Flow<Boolean>
suspend fun getYandereModeInstalledApps(): Boolean
suspend fun setYandereModeInstalledApps(enabled: Boolean)
```

---

## 8. 权限声明

### 8.1 AndroidManifest.xml

```xml
<!-- 使用统计权限（需用户手动在系统设置授权） -->
<uses-permission
    android:name="android.permission.PACKAGE_USAGE_STATS"
    tools:ignore="ProtectedPermissions" />

<!-- 查询已安装应用（Android 11+ 推荐用 queries 替代 QUERY_ALL_PACKAGES） -->
<queries>
    <intent>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent>
</queries>
```

> 说明：参考源码使用 `QUERY_ALL_PACKAGES`，但 Google Play 对 `QUERY_ALL_PACKAGES` 审核较严。LianYu 当前主要在国内分发，保留 `queries` 方案；若后续需要更完整列表，可再补充 `QUERY_ALL_PACKAGES`。

---

## 9. UI 设计要点

- 遵循 LianYu 现有 WeChat-style 暗色优先 + liquid glass 设计语言。
- 复用 `FrameRateScreen` 的顶部栏与卡片布局风格。
- 设置项使用开关卡片 + 说明文案。
- 数据预览卡片：更新时间、已安装应用数、TOP 应用列表。
- 权限状态卡片：未授权时显示警告与「去授权」按钮，已授权时显示成功状态。

---

## 10. 测试要点

1. **权限流程**：未授权时开关开启后静默降级；授权后能收集数据。
2. **缓存刷新**：6 小时内复用缓存；手动刷新与强制刷新生效。
3. **触发概率**：5 轮间隔 + 30% 概率符合预期。
4. **Prompt 注入**：启用后系统 Prompt 包含病娇上下文；关闭后不包含。
5. **群聊生效**：GroupChatViewModel 同样注入病娇上下文。
6. **数据安全**：确认数据仅写入本地缓存文件，不进入网络请求体（除注入 Prompt 外）。

---

## 11. 风险与规避

| 风险 | 规避措施 |
|------|----------|
| `UsageStats` 权限被拒绝 | 静默降级，不注入 Prompt |
| 收集数据耗时导致 ANR | 使用 `Dispatchers.IO` 异步收集 |
| Prompt 过长 | 仅注入 TOP 5 应用 + 应用总数，控制长度 |
| 角色性别未知 | 使用 `companion.role` 判断，兜底使用女友语气 |
| 数据隐私争议 | UI 明确告知数据不上传、仅本地使用 |
