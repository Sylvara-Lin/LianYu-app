<div align="center">

# LianYu / 恋语

**一款基于 Kotlin + Jetpack Compose 的 Android AI 虚拟陪伴应用开源版**

让开发者可以自由研究、二次开发和接入自己的 AI 服务。

<p>
  <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android 8+" />
  <img src="https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/Architecture-Modular-FF69B4?style=for-the-badge" alt="Modular" />
</p>

</div>

---

## ✨ 项目简介

LianYu（恋语）是一个 Android AI 伴侣应用框架，包含聊天、角色、群聊、记忆、主题、资料页、本地模型和多 API 配置等模块。这个仓库是面向社区的开源版本，保留应用主体功能与模块化架构，方便你接入自己的模型服务、定制角色系统或继续扩展 UI/功能。

## 🌱 开源版说明

为了适合公开发布，本仓库已经做了开源化清理：

- 移除内置私有 AI 中继和个人服务器配置。
- 移除内置 API key、token、secret、私有 endpoint。
- 移除私有安全壳、Native VMP、反调试、请求签名和证书绑定实现。
- 保留 `core:security` 的最小兼容 stub，便于项目继续编译，也方便下游自行替换安全实现。
- API 需要用户在应用设置中自行配置，不默认连接任何私有服务。

> 如果你要发布自己的分支，请确认不要提交真实 API key、服务器地址、签名证书或本地配置文件。

## 🚀 功能特性

- 💬 **AI 伴侣聊天**：支持多角色对话、上下文消息和聊天记录。
- 🧑‍🤝‍🧑 **伴侣管理**：创建、编辑、维护虚拟伴侣资料。
- 👥 **群聊框架**：支持群组角色互动的基础能力。
- 🧠 **记忆模块**：本地记忆条目、上下文召回与管理。
- 🎨 **主题与界面**：深色优先、WeChat 风格、Compose UI、液态玻璃效果。
- ⚙️ **多 API 配置**：OpenAI-compatible、自定义接口和多厂商配置框架。
- 🤖 **本地模型框架**：预留 LiteRT-LM / on-device inference 接入能力。
- 🔔 **通知与保活**：前台服务、WorkManager、开机恢复任务。
- 📦 **模块化架构**：feature/core 分层，适合学习和二次开发。

## 🧱 模块架构

```text
:app
  └─→ feature:*  (chat, companion, groupchat, memory, profile, settings, ...)
        └─→ core:* (common, database, domain, network, security, ui-common)

:shell  已从开源版移除
```

核心约束：

- `:app` 只负责 Application、Activity、导航和 ServiceRegistry 绑定。
- `feature:*` 不直接依赖其他 feature 模块。
- `core:*` 不依赖 feature 模块。
- 跨模块通信通过 `core:domain` 接口和 `ServiceRegistry` 完成。

## 🛠 技术栈

| 分类 | 技术 |
|---|---|
| Language | Kotlin 2.2.10 |
| UI | Jetpack Compose / Material 3 |
| Architecture | Feature-based modular architecture |
| Database | Room |
| Async | Kotlin Coroutines / Flow |
| Network | Retrofit / OkHttp |
| Background | WorkManager / Foreground Service |
| Local AI | LiteRT-LM integration framework |
| Min SDK | 26 |
| Compile SDK | 35 |
| JDK | 17 |

## ⚡ 快速开始

### 1. 克隆仓库

```bash
git clone https://github.com/Sylvara-Lin/LianYu-app.git
cd LianYu-app
```

### 2. 使用 Android Studio 打开

建议使用较新的 Android Studio，并确保本机可用 JDK 17。仓库内 Gradle wrapper 和依赖版本已经固定，不建议随意升级。

### 3. 构建 Debug 包

```bash
./gradlew :app:assembleDebug
```

如果你修改了 core 模块或依赖关系，可以执行：

```bash
./gradlew clean :app:assembleDebug
```

## 🔑 API 配置

开源版不内置任何可用 API。请在应用内设置页添加自己的配置：

- `baseUrl`：OpenAI-compatible API 地址，例如 `https://api.openai.com/v1/`。
- `apiKey`：你自己的服务密钥。
- `model`：模型名，例如 `gpt-4o-mini` 或你的服务支持的模型。
- `formatHint`：自定义 provider 可按需要指定协议格式。

请勿把真实 API key 提交到仓库。

## 📁 目录结构

```text
app/                 Android 应用入口、导航、ServiceRegistry 绑定
core/common/         通用工具、设置、日志、安全内容过滤基础能力
core/database/       Room 数据库、Entity、DAO、Repository
core/domain/         跨模块接口与领域数据类
core/network/        AI 服务网关、Provider 调用、网络客户端
core/security/       开源版 no-op 兼容 stub
core/ui-common/      通用 Compose 组件和主题
feature/chat/        聊天功能
feature/companion/   伴侣创建与编辑
feature/groupchat/   群聊功能
feature/memory/      记忆管理
feature/profile/     用户资料页
feature/settings/    设置与 API 配置
feature/localmodel/  本地模型框架
feature/notification/通知与后台任务
```

## 🧩 二次开发建议

- 新功能优先放在 `feature:*` 模块。
- 通用接口放在 `core:domain`，避免 feature 之间互相依赖。
- 通用 UI 放在 `core:ui-common`。
- 私有服务、密钥、证书、签名配置请使用本地文件或 CI secret 注入，不要提交。
- 如果你需要更强安全能力，可以替换 `core:security` 中的 no-op stub。

## 🤝 贡献

欢迎提交 Issue、PR、功能建议和文档改进。

推荐流程：

1. Fork 本仓库。
2. 创建功能分支。
3. 保持改动聚焦并通过构建。
4. 提交 PR，并说明改动原因和测试结果。

## 💖 Contributors

<table>
  <tr>
    <td align="center"><a href="https://github.com/Sylvara-Lin"><img src="https://github.com/Sylvara-Lin.png?size=96" width="72"/><br/><sub><b>Sylvara-Lin</b></sub></a></td>
    <td align="center"><a href="https://github.com/Clove"><img src="https://github.com/Clove.png?size=96" width="72"/><br/><sub><b>Clove</b></sub></a></td>
    <td align="center"><a href="https://github.com/2164312714-svg"><img src="https://github.com/2164312714-svg.png?size=96" width="72"/><br/><sub><b>2164312714-svg</b></sub></a></td>
    <td align="center"><a href="https://github.com/3092054815-byte"><img src="https://github.com/3092054815-byte.png?size=96" width="72"/><br/><sub><b>3092054815-byte</b></sub></a></td>
    <td align="center"><a href="https://github.com/doromy118"><img src="https://github.com/doromy118.png?size=96" width="72"/><br/><sub><b>doromy118</b></sub></a></td>
    <td align="center"><a href="https://github.com/HI-IR"><img src="https://github.com/HI-IR.png?size=96" width="72"/><br/><sub><b>HI-IR</b></sub></a></td>
  </tr>
  <tr>
    <td align="center"><a href="https://github.com/jianghep"><img src="https://github.com/jianghep.png?size=96" width="72"/><br/><sub><b>jianghep</b></sub></a></td>
    <td align="center"><a href="https://github.com/jiuicy"><img src="https://github.com/jiuicy.png?size=96" width="72"/><br/><sub><b>jiuicy</b></sub></a></td>
    <td align="center"><a href="https://github.com/liuwanwan1"><img src="https://github.com/liuwanwan1.png?size=96" width="72"/><br/><sub><b>liuwanwan1</b></sub></a></td>
    <td align="center"><a href="https://github.com/summerpalace2"><img src="https://github.com/summerpalace2.png?size=96" width="72"/><br/><sub><b>summerpalace2</b></sub></a></td>
    <td align="center"><a href="https://github.com/Vespera-Su"><img src="https://github.com/Vespera-Su.png?size=96" width="72"/><br/><sub><b>Vespera-Su</b></sub></a></td>
  </tr>
</table>

更多信息见 [CONTRIBUTORS.md](CONTRIBUTORS.md)。

> GitHub 右侧自动 Contributors 列表由提交作者和 co-author 记录生成。README 中的贡献者区用于稳定展示社区成员。

## 📄 License

请在发布前根据你的开源策略补充 LICENSE 文件。若你希望社区可自由使用和二次开发，常见选择包括 Apache-2.0、MIT 或 GPL-3.0。

---

<div align="center">

Made with 💗 for AI companion app builders.

</div>
