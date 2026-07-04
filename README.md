<div align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="120" height="120" alt="LianYu Logo" />

# LianYu / 恋语

### 面向 Android 的 AI 虚拟陪伴应用开源框架

**AI Companion · Modular Android · Jetpack Compose · Local Model Ready · Open API Framework**

<p>
  <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android 8+" />
  <img src="https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/Architecture-Feature%20Modular-FF69B4?style=for-the-badge" alt="Modular" />
  <img src="https://img.shields.io/badge/Open%20Source-Public%20Edition-22C55E?style=for-the-badge" alt="Open Source" />
</p>

<p>
  <a href="#-快速开始">快速开始</a> ·
  <a href="#-功能全景">功能全景</a> ·
  <a href="#-系统架构">系统架构</a> ·
  <a href="#-api-配置">API 配置</a> ·
  <a href="#-贡献者">贡献者</a>
</p>

</div>

---

## 目录

- [项目定位](#-项目定位)
- [开源版声明](#-开源版声明)
- [功能全景](#-功能全景)
- [产品体验](#-产品体验)
- [系统架构](#-系统架构)
- [模块说明](#-模块说明)
- [技术栈](#-技术栈)
- [快速开始](#-快速开始)
- [API 配置](#-api-配置)
- [本地模型](#-本地模型)
- [数据与隐私](#-数据与隐私)
- [开发规范](#-开发规范)
- [构建与发布](#-构建与发布)
- [路线图](#-路线图)
- [贡献者](#-贡献者)

---

## 🌌 项目定位

**LianYu（恋语）** 是一款 Android AI 虚拟陪伴应用的开源框架。它不是一个简单的聊天 Demo，而是一个围绕“长期陪伴、角色人格、记忆沉淀、多模态扩展、本地模型接入、模块化工程治理”构建的完整应用骨架。

本项目适合：

- 想学习 **Kotlin + Jetpack Compose 大型 Android 工程架构** 的开发者。
- 想二次开发 AI 伴侣、AI 角色聊天、AI 社交陪伴类产品的团队。
- 想接入 OpenAI-compatible API、本地模型、私有模型网关的个人或组织。
- 想参考 Room / Flow / Compose / WorkManager / 多模块拆分实践的 Android 工程师。

它提供了一套完整但可替换的产品基座：

```text
角色系统 + 聊天系统 + 记忆系统 + 群聊系统 + 设置系统 + 本地模型框架 + 通知框架 + 主题框架
```

你可以把它当作：

- 一个 AI companion app starter kit。
- 一个 Compose 多模块工程样板。
- 一个可继续商业化或社区化的虚拟陪伴应用底座。
- 一个可替换后端、替换模型、替换 UI 风格的产品壳。

---

## 🌱 开源版声明

为了适合公开发布，本仓库已经整理为 **Public Edition**。

### 已移除

- 私有 AI 中继服务配置。
- 内置 Clove / cloveapi 相关服务器逻辑。
- 个人服务器地址、内置 token、内置 secret、私有 API key。
- 私有证书 pinning 和面向私有后端的请求签名。
- Native 安全壳、VMP、反调试、OLLVM、shell payload、打包加固脚本。
- 发布 APK、私有安全文档、历史临时推送目录。

### 已保留

- App 主体功能和模块化架构。
- AI Provider 配置框架。
- Room 数据库和 Repository 层。
- Compose UI 与主题系统。
- 本地模型接入框架。
- 通知、WorkManager、微信/QQ Bot 等功能模块骨架。
- `core:security` 最小 no-op 兼容层，方便下游替换实现。

### 需要你自行配置

- AI API Base URL。
- API Key。
- 模型名称。
- 推送平台参数。
- 签名证书。
- 是否接入自己的安全/加密/风控方案。

> Public Edition 默认不会连接任何私有服务器，也不提供可直接使用的内置模型服务。

---

## ✨ 功能全景

### 1. AI 伴侣聊天

- 单人伴侣聊天。
- 多轮上下文对话。
- 消息历史持久化。
- AI 回复失败处理。
- 消息输入队列和异步处理。
- 用户消息与 AI 消息区分展示。
- 适配 OpenAI-compatible Chat Completions 风格接口。

### 2. 角色与伴侣管理

- 创建虚拟伴侣。
- 编辑角色资料。
- 角色头像、名称、设定、描述管理。
- 默认伴侣 seed 框架。
- 角色资料可扩展到人格、语气、背景故事、互动风格。

### 3. 群聊框架

- 群组数据结构。
- 群消息持久化。
- 多角色互动基础。
- 可扩展为多人 AI 剧场、虚拟社群、AI 角色扮演房间。

### 4. 记忆系统

- 记忆条目管理。
- MemoryEntry 数据模型。
- 可扩展长期记忆、用户偏好、角色关系、重要事件。
- 为后续向量检索、RAG、上下文召回预留工程空间。

### 5. API 配置中心

支持多种 Provider 类型和自定义接口：

- OpenAI
- Claude / Anthropic
- Gemini
- DeepSeek
- DashScope / 通义千问
- Kimi
- OpenRouter
- Groq
- SiliconFlow
- 讯飞星火
- 自定义 OpenAI-compatible API
- 自定义中继占位

开源版不内置任何密钥。所有 API 均需要用户自行填写。

### 6. 本地模型框架

- LiteRT-LM 接入结构。
- 本地模型管理器。
- 模型下载、校验、激活状态框架。
- LocalAiService 单例和引用计数设计。
- 适合继续扩展端侧 Gemma / 小模型 / 分类器能力。

### 7. 主题与 UI 体系

- Jetpack Compose UI。
- Material 3。
- 深色优先风格。
- WeChat 风格交互。
- 液态玻璃 / Frosted glass 视觉组件。
- Skeleton / shimmer / spring animation 等现代动效基础。
- 硬件性能分级控制动画强度。

### 8. 通知与后台任务

- Foreground Service。
- WorkManager 周期任务。
- BootReceiver 开机恢复。
- 通知渠道初始化。
- 可扩展主动消息、陪伴提醒、定时互动。

### 9. 微信与 QQ Bot 功能骨架

- 微信轮询服务框架。
- 微信主动消息广播接收框架。
- QQ Bot 前台服务和 WebSocket 基础能力。
- 适合二次开发为跨平台陪伴助手。

### 10. 咖啡 / 工具类扩展模块

- 作为独立 feature 模块存在。
- 展示了业务功能如何以 feature 模块方式接入主应用。
- 可作为后续插件化、工具型功能扩展示例。

---

## 🧭 产品体验

LianYu 的目标体验不是“问答工具”，而是“持续陪伴”。因此工程上围绕以下体验设计：

| 体验目标 | 工程支撑 |
|---|---|
| 角色有记忆 | Room MemoryEntry + 可扩展检索层 |
| 聊天自然连续 | 多轮上下文 + 消息队列 |
| 视觉沉浸 | Compose + 深色主题 + 动效组件 |
| 可长期运行 | WorkManager + Foreground Service |
| 可自由接模型 | 多 Provider + Custom API |
| 可本地化 | LiteRT-LM 框架 |
| 可二次开发 | feature/core 模块化架构 |

---

## 🏗 系统架构

### 总体分层

```text
┌─────────────────────────────────────────────┐
│                    :app                     │
│ Application / Activity / Navigation / DI    │
└──────────────────────┬──────────────────────┘
                       │
┌──────────────────────▼──────────────────────┐
│                 feature:*                   │
│ chat / companion / groupchat / memory       │
│ profile / settings / localmodel / wechat    │
│ notification / qqbot / backup / coffee      │
└──────────────────────┬──────────────────────┘
                       │
┌──────────────────────▼──────────────────────┐
│                   core:*                    │
│ common / database / domain / network        │
│ security(stub) / ui-common                  │
└─────────────────────────────────────────────┘
```

### 依赖方向

```text
:app
  └─→ feature modules
        └─→ core modules
```

规则：

- feature 模块之间不直接依赖。
- core 模块不依赖 feature。
- `core:domain` 定义跨模块接口。
- `ServiceRegistry` 负责运行时绑定。
- `:app` 保持轻量，只做入口、导航和服务注册。

### 数据流

```text
UI Event
  ↓
ViewModel
  ↓
Repository / Service
  ↓
Room / Network / Local Model
  ↓
StateFlow / Flow
  ↓
Compose UI
```

---

## 📦 模块说明

| 模块 | 责任 |
|---|---|
| `:app` | 应用入口、主 Activity、导航图、ServiceRegistry 绑定 |
| `core:common` | 通用工具、日志、设置、内容安全基础组件 |
| `core:database` | Room 数据库、Entity、DAO、Repository |
| `core:domain` | 跨模块接口和领域数据类 |
| `core:network` | AI 服务网关、Provider 适配、TTS/STT 网络能力 |
| `core:security` | 开源版 no-op 兼容 stub，可由下游替换 |
| `core:ui-common` | 通用 Compose 组件、主题、动效、视觉组件 |
| `feature:chat` | 聊天界面、聊天 ViewModel、语音通话等 |
| `feature:companion` | 伴侣创建、编辑、资料维护 |
| `feature:groupchat` | 群聊和群组管理 |
| `feature:memory` | 记忆管理和记忆展示 |
| `feature:profile` | 主页、用户资料、统计、入口卡片 |
| `feature:settings` | 设置页、API 配置、主题和模型配置入口 |
| `feature:localmodel` | 本地模型管理和推理服务框架 |
| `feature:notification` | 通知、保活、WorkManager 后台任务 |
| `feature:wechat` | 微信相关服务框架 |
| `feature:qqbot` | QQ Bot 服务框架 |
| `feature:backup` | 数据备份相关能力 |
| `feature:coffee` | 业务扩展示例模块 |

---

## 🛠 技术栈

| 分类 | 技术 |
|---|---|
| Language | Kotlin 2.2.10 |
| UI | Jetpack Compose, Material 3 |
| Architecture | Feature-based modular architecture |
| Reactive | Kotlin Coroutines, Flow, StateFlow |
| Database | Room |
| Network | Retrofit, OkHttp |
| Serialization | kotlinx.serialization |
| Image | Coil |
| Background | WorkManager, Foreground Service |
| Local AI | LiteRT-LM integration framework |
| Min SDK | 26 |
| Target SDK | 35 |
| Compile SDK | 35 |
| JDK | 17 |
| Build | Gradle 9.4.1, AGP 9.2.1 |

---

## ⚡ 快速开始

### 1. 克隆

```bash
git clone https://github.com/Sylvara-Lin/LianYu-app.git
cd LianYu-app
```

### 2. 打开项目

使用 Android Studio 打开根目录。

建议环境：

- Android Studio 新版本。
- JDK 17。
- Android SDK 35。
- Gradle 使用仓库自带 wrapper。

### 3. 构建 Debug

```bash
./gradlew :app:assembleDebug
```

如果你修改了 core 模块或 Gradle 依赖：

```bash
./gradlew clean :app:assembleDebug
```

### 4. 安装运行

生成 APK 后可在：

```text
app/build/outputs/apk/debug/
```

找到 debug 包。

---

## 🔑 API 配置

开源版默认没有任何可用 API。

你需要在 App 设置页中配置：

| 字段 | 说明 |
|---|---|
| Provider | 选择 OpenAI / DeepSeek / Gemini / Custom 等 |
| Base URL | API 服务地址 |
| API Key | 你自己的服务密钥 |
| Model | 模型名称 |
| Format Hint | 自定义接口格式提示 |
| Temperature | 回复随机性 |
| Max Tokens | 最大输出 token |

### OpenAI-compatible 示例

```text
Base URL: https://api.openai.com/v1/
Model: gpt-4o-mini
API Key: sk-********************************
```

### 自定义模型网关

如果你的后端兼容 Chat Completions，可以使用：

```text
Provider: Custom
Base URL: https://your-domain.example/v1/
Model: your-model-name
```

> 请不要把真实 API Key 写入源码、README、Issue 或 PR。

---

## 🤖 本地模型

项目保留本地模型框架，适合继续扩展端侧推理：

- 模型管理器。
- 下载状态。
- SHA-256 校验。
- 激活状态持久化。
- LocalAiService 单例。
- Native Engine 生命周期管理。

本地模型适合：

- 离线回复。
- 安全分类。
- 小模型角色扮演。
- 边缘设备推理实验。

注意：大模型文件体积较大，建议使用 Git LFS、Release Assets 或应用内下载，不建议直接长期放在 Git 普通对象中。

---

## 🔐 数据与隐私

Public Edition 的原则：

- 不内置个人服务器。
- 不内置 API key。
- 不上传用户聊天内容到项目作者服务器。
- 不包含私有请求签名方
案。
- 不包含私有加密壳和反调试实现。

你的分支可以自行接入：

- 本地数据库加密。
- 自有后端网关。
- 企业密钥管理。
- 证书 pinning。
- 风控和审计。

仓库里的 `core:security` 是兼容 stub，不代表生产安全方案。

---

## 🧪 开发与质量

常用命令：

```bash
# Debug 构建
./gradlew :app:assembleDebug

# 清理后构建
./gradlew clean :app:assembleDebug

# 查看 app 依赖
./gradlew app:dependencies --configuration implementation

# 运行可用测试
./gradlew test
```

建议 PR 提交前至少运行：

```bash
./gradlew :app:assembleDebug
```

---

## 🧑‍💻 开发规范

### 模块边界

- 新功能优先放入 `feature:*`。
- 通用 UI 放入 `core:ui-common`。
- 通用工具放入 `core:common`。
- 跨模块接口放入 `core:domain`。
- 不要让 feature 直接依赖另一个 feature。

### 状态管理

推荐：

```text
StateFlow -> Compose collectAsState()
SharedFlow -> 一次性事件
ViewModel -> 用户行为入口
Repository -> 数据读写
```

### API Key

禁止提交：

- `local.properties`
- `.env`
- keystore
- jks
- 真实 API key
- 真实 token
- 私有服务器密钥

---

## 📱 截图

当前仓库没有附带完整截图集。你可以在自己的分支中添加：

```text
docs/screenshots/home.png
docs/screenshots/chat.png
docs/screenshots/settings.png
docs/screenshots/profile.png
```

推荐 README 展示：

| Home | Chat | Settings | Profile |
|---|---|---|---|
| screenshot | screenshot | screenshot | screenshot |

---

## 🗺 路线图

### Short Term

- [ ] 补充正式 LICENSE。
- [ ] 增加截图和演示视频。
- [ ] 提供 API 配置示例文档。
- [ ] 增加贡献指南。
- [ ] 整理 Issue 模板和 PR 模板。

### Mid Term

- [ ] 抽象 Provider 插件化接口。
- [ ] 增强本地模型下载和管理体验。
- [ ] 增加角色卡导入导出。
- [ ] 增加记忆检索和向量召回。
- [ ] 增加多语言文档。

### Long Term

- [ ] 构建插件生态。
- [ ] 支持更多端侧模型。
- [ ] 支持桌面端或跨端共享数据。
- [ ] 建立社区角色市场。
- [ ] 完善自动化测试和 CI。

---

## ❓ FAQ

### 这个仓库能直接聊天吗？

可以运行 App，但需要你自己配置可用的 AI API。

### 是否包含作者的服务器？

不包含。Public Edition 已移除私有服务器和内置中继配置。

### 是否包含加固壳？

不包含。安全壳、VMP、反调试、私有 Native 实现已移除。

### 为什么还有 `core:security`？

为了保持工程结构和兼容调用点，开源版保留 no-op stub。你可以替换为自己的安全实现。

### GitHub 提示大文件怎么办？

部分模型和 AAR 体积较大。后续可以迁移到 Git LFS 或 Release Assets。

---

## 🤝 贡献方式

欢迎任何形式的贡献：

- Bug report
- Feature request
- UI 优化
- 文档补充
- Provider 适配
- 本地模型实验
- 架构重构建议

推荐流程：

1. Fork 仓库。
2. 创建分支。
3. 保持改动聚焦。
4. 本地运行构建。
5. 提交 PR 并说明测试结果。

---

## 💖 贡献者

<table>
  <tr>
    <td align="center"><a href="https://github.com/2164312714-svg"><img src="https://github.com/2164312714-svg.png?size=96" width="72"/><br/><sub><b>Clove.</b></sub><br/><sub>2164312714-svg</sub></a></td>
    <td align="center"><a href="https://github.com/17rrr"><img src="https://github.com/17rrr.png?size=96" width="72"/><br/><sub><b>17rrr</b></sub><br/><sub>17rrr</sub></a></td>
    <td align="center"><a href="https://github.com/3092054815-byte"><img src="https://github.com/3092054815-byte.png?size=96" width="72"/><br/><sub><b>着魔</b></sub><br/><sub>3092054815-byte</sub></a></td>
    <td align="center"><a href="https://github.com/doromy118"><img src="https://github.com/doromy118.png?size=96" width="72"/><br/><sub><b>doromy118</b></sub><br/><sub>doromy118</sub></a></td>
    <td align="center"><a href="https://github.com/HI-IR"><img src="https://github.com/HI-IR.png?size=96" width="72"/><br/><sub><b>HI-IR</b></sub><br/><sub>HI-IR</sub></a></td>
    <td align="center"><a href="https://github.com/jianghep"><img src="https://github.com/jianghep.png?size=96" width="72"/><br/><sub><b>jianghep</b></sub><br/><sub>jianghep</sub></a></td>
  </tr>
  <tr>
    <td align="center"><a href="https://github.com/jiuicy"><img src="https://github.com/jiuicy.png?size=96" width="72"/><br/><sub><b>玖熙</b></sub><br/><sub>jiuicy</sub></a></td>
    <td align="center"><a href="https://github.com/liuwanwan1"><img src="https://github.com/liuwanwan1.png?size=96" width="72"/><br/><sub><b>liuwanwan</b></sub><br/><sub>liuwanwan1</sub></a></td>
    <td align="center"><a href="https://github.com/summerpalace2"><img src="https://github.com/summerpalace2.png?size=96" width="72"/><br/><sub><b>GGY</b></sub><br/><sub>summerpalace2</sub></a></td>
    <td align="center"><a href="https://github.com/Vespera-Su"><img src="https://github.com/Vespera-Su.png?size=96" width="72"/><br/><sub><b>祈愿小苏</b></sub><br/><sub>Vespera-Su</sub></a></td>
    <td align="center"><a href="https://github.com/whitequeen306"><img src="https://github.com/whitequeen306.png?size=96" width="72"/><br/><sub><b>whitequeen306</b></sub><br/><sub>whitequeen306</sub></a></td>
    <td align="center"><a href="https://github.com/Sylvara-Lin"><img src="https://github.com/Sylvara-Lin.png?size=96" width="72"/><br/><sub><b>Sylvara-Lin</b></sub><br/><sub>Sylvara-Lin</sub></a></td>
  </tr>
</table>

更多信息见 [CONTRIBUTORS.md](CONTRIBUTORS.md)。

> GitHub 右侧自动 Contributors 列表由真实提交作者生成。README 中的贡献者区用于稳定展示社区成员。

---

## 📄 License

本项目采用 **Apache License 2.0** 开源协议。

Apache-2.0 是较严谨且适合工程化项目的宽松协议，允许使用、修改、分发和商业使用，同时包含明确的版权保留、专利授权、免责声明和责任限制条款。

请阅读：

- [LICENSE](LICENSE)
- [NOTICE](NOTICE)

---

<div align="center">

**LianYu / 恋语**  
Made with 💗 for AI companion app builders.

</div>
