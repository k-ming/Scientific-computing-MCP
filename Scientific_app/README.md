# Scientific Agent（安卓）

一个安卓 App，作为一个 **agent** 运行：它连接一个大语言模型（LLM），并接入
`scicalc-mcp` 科学计算 MCP 服务，让模型能够通过工具调用完成矩阵运算、统计、
积分求根、数据分析与绘图等任务。

## 关于"接入 Kiro 的模型"

需要澄清一个前提：**Kiro 本身不对外提供可用 API Key 直接调用的模型接口**。
Kiro 是 IDE，其背后使用 Claude 等模型，但没有暴露"Kiro 模型 API"给第三方 App。

因此本 App 采用通用做法——连接一个 **OpenAI 兼容的 `chat/completions` 接口**。
你在设置里填入 Base URL、API Key、模型名称即可，可指向任意兼容该协议的模型
服务或代理网关。这样既满足"用 API Key 接入模型"的需求，又保持后端可替换。

## 架构

```
安卓 App（Agent）
   ├── LlmClient   ── OpenAI 兼容接口（API Key）      负责"思考"与决定调用哪个工具
   └── McpClient   ── scicalc MCP（streamable-http）  提供 19 个科学计算工具

  Agent 循环（AgentOrchestrator）：
    用户提问 → LLM 决定调用工具 → App 通过 MCP 执行 → 结果回传 LLM → 得出最终答案
```

- `data/llm/LlmClient.kt`：调用 LLM，支持 tool calling，把 MCP 工具转成 OpenAI tools 格式。
- `data/mcp/McpClient.kt`：MCP streamable-http 客户端，处理 initialize / tools/list /
  tools/call，并解析 SSE 响应。
- `agent/AgentOrchestrator.kt`：驱动"思考—调用工具—回传"的多轮循环。
- `ui/`：Jetpack Compose 界面，分「对话」与「设置」两个页签。
- `data/AppSettings.kt`：用 DataStore 持久化配置。

## 配置项（设置页）

| 项 | 说明 |
| --- | --- |
| LLM Base URL | OpenAI 兼容接口地址，如 `https://api.example.com/v1` |
| LLM API Key | 模型服务的 API Key |
| LLM 模型名称 | 例如具体的对话模型名 |
| MCP URL | 部署好的 scicalc MCP，如 `https://<IP>.nip.io/mcp` |
| MCP Bearer Token | 可选，MCP 服务若启用鉴权时填写 |
| 系统提示词 | 约束 agent 行为，已内置默认值 |

MCP 服务的部署方式见上级目录 `../README.md`（docker-compose + Caddy 自动 HTTPS）。

## 构建

需要 Android Studio（或命令行 Gradle）。项目使用 Kotlin + Jetpack Compose。

```bash
# 在 Scientific_app 目录下
./gradlew assembleDebug        # 生成 debug APK
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

> 首次构建会下载 Gradle 与依赖。若用 Android Studio，直接 Open 该目录后运行即可。
> 注意：本骨架未包含 `gradlew` 可执行脚本与 wrapper jar，建议用 Android Studio
> 打开自动生成，或本机已装 Gradle 8.7 时用 `gradle wrapper` 生成。

## 使用流程

1. 安装并打开 App。
2. 进入「设置」，填写 LLM 与 MCP 参数，保存。
3. 回到「对话」，提问，例如：
   - “求矩阵 [[1,2],[3,4]] 的逆矩阵”
   - “对 sin(x)*x 在 0 到 pi 上积分”
   - “这组数据的均值和标准差：3,5,7,9,11”
4. App 会显示模型的思考、对 MCP 工具的调用与结果，最后给出中文解答。

## 安全说明

- API Key 与 Token 目前以明文存于 DataStore（示例用途）。用于生产时建议改用
  `EncryptedSharedPreferences` 或 Android Keystore 加密。
- App 通过 HTTPS 访问 LLM 与 MCP。请确保 MCP 服务端已启用 HTTPS 与访问控制。
