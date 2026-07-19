# Kiro OpenAI 兼容网关

把 **Kiro CLI headless 模式**包装成一个 **OpenAI 兼容的 `/v1/chat/completions`
接口**，让安卓 app（或任何 OpenAI 兼容客户端）能用你的 Kiro API Key 间接调用
Kiro 模型，并借助 Kiro CLI 自身配置的 scicalc MCP 完成科学计算。

## 为什么需要它

Kiro 的 API Key **不是**一个可直接 HTTP 调用的模型接口——它用于认证 **Kiro CLI**
的 headless 模式（`kiro-cli chat --no-interactive`）。安卓设备无法运行 CLI，也没有
公开的模型 REST 端点。因此本网关在服务器上运行，充当"翻译层"：

```
安卓 App ──OpenAI 格式──> 网关(/v1/chat/completions)
                             │  内部调用
                             ▼
                      kiro-cli chat --no-interactive  (KIRO_API_KEY 认证)
                             │  Kiro CLI 自己连 MCP
                             ▼
                      scicalc-mcp:8000  (科学计算工具)
                             │
                             ▼
                      端到端结果 ──包装成 OpenAI 响应──> App
```

关键点：工具调用由 **Kiro CLI 端到端自己完成**（headless 模式不会把 tool_calls
暴露给外部执行），所以 MCP 配置在网关容器里（`kiro-mcp.json`），而不在 app 里。

## 组成

| 文件 | 作用 |
| --- | --- |
| `app.py` | FastAPI 网关，暴露 `/v1/chat/completions`、`/v1/models`、`/health` |
| `Dockerfile` | 安装 Kiro CLI + 网关依赖 |
| `kiro-mcp.json` | 挂载给容器内 Kiro CLI 的 MCP 配置，指向 `scicalc-mcp:8000` |
| `.env.example` | 环境变量模板（KIRO_API_KEY、GATEWAY_TOKEN） |

## 部署

网关已集成进上级目录的 `docker-compose.yml`，与 scicalc-mcp、Caddy 一同编排。

1. 在 `docker-compose.yml` 所在目录创建 `.env`（参考 `.env.example`）：
   ```
   KIRO_API_KEY=你的-kiro-api-key
   GATEWAY_TOKEN=一段自己生成的长随机串
   ```

2. 构建并启动：
   ```bash
   docker compose up -d --build
   ```

3. Caddy 已配置路由：
   - `https://<IP>.nip.io/v1/*`   → 网关（LLM 接口）
   - `https://<IP>.nip.io/mcp`     → scicalc MCP（供其它 MCP 客户端直连）
   - `https://<IP>.nip.io/health`  → 网关健康检查

4. 验证：
   ```bash
   curl https://<IP>.nip.io/health
   curl -X POST https://<IP>.nip.io/v1/chat/completions \
     -H "Authorization: Bearer <GATEWAY_TOKEN>" \
     -H "Content-Type: application/json" \
     -d '{"model":"kiro","messages":[{"role":"user","content":"求 [[1,2],[3,4]] 的逆矩阵"}]}'
   ```

## 安卓 app 配置

在 app 的「设置」页填：

| 项 | 值 |
| --- | --- |
| LLM Base URL | `https://<IP>.nip.io/v1` |
| LLM API Key | 你设置的 `GATEWAY_TOKEN`（不是 Kiro key！） |
| LLM 模型名称 | `kiro`（任意，网关会忽略） |
| MCP URL | 留空。工具由服务器端 Kiro CLI 调用，app 不直连 MCP |

这样 Kiro key 只存在于服务器，绝不下发到手机端。

## 注意事项与限制

- **首次运行较慢**：Kiro CLI 每次调用是一个完整 agent 会话，延迟明显高于普通
  模型 API（数秒到数十秒），已设 180s 超时，可用 `KIRO_TIMEOUT` 调整。
- **无对话状态复用**：headless 每次是独立会话，本网关把历史消息拼进单个 prompt
  传入，长对话会变长。若需要更好的多轮体验，可自行裁剪历史。
- **非流式**：当前返回完整响应，未实现 SSE 流式输出。
- **CLI 输出解析**：网关直接透传 CLI 的 stdout 文本。若 CLI 输出包含额外的日志/
  格式修饰，可能需要在 `run_kiro` 中做清洗。首次部署后请用 curl 观察实际输出，
  按需调整。
- **订阅要求**：API Key 认证仅对 Kiro Pro/Pro+/Pro Max/Power 订阅开放；企业账号
  需管理员开启 API key 生成。
