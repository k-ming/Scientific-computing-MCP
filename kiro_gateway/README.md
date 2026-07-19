# Kiro OpenAI 兼容网关

把 **Kiro CLI headless 模式**包装成一个 **OpenAI 兼容的 `/v1/chat/completions`
接口**，让安卓 app（或任何 OpenAI 兼容客户端）能用你的 Kiro API Key 间接对话，
并借助 Kiro 内置的 `execute_bash` 工具完成科学计算。

## 为什么需要它

Kiro 的 API Key **不是**一个可直接 HTTP 调用的模型接口——它用于认证 **Kiro CLI**
的 headless 模式（`kiro-cli chat --no-interactive`）。安卓设备无法运行 CLI，也没有
公开的模型 REST 端点。因此本网关在服务器上运行，充当"翻译层"：

```
安卓 App ──OpenAI 格式──> 网关(/v1/chat/completions)
                             │  内部调用
                             ▼
                      kiro-cli chat --no-interactive  (KIRO_API_KEY 认证)
                             │  Kiro 端到端执行，用内置工具计算
                             ▼
                      execute_bash 跑 Python (numpy 等) 得出结果
                             │
                             ▼
                      端到端结果 ──包装成 OpenAI 响应──> App
```

## 重要：关于 MCP 的实测结论

最初设想是让容器内的 Kiro CLI 通过 MCP 调用 `scicalc-mcp` 服务。**实测发现这条
路走不通**：

- 在 **headless / API Key 模式**下，Kiro CLI 会报
  `Failed to retrieve MCP settings; MCP functionality disabled`，MCP 被禁用。
- 原因是 Kiro 的 MCP 采用 **fail-closed** 设计（见
  [governance 文档](https://kiro.dev/docs/enterprise/governance/mcp/)）：CLI 必须
  成功从 Kiro 的 governance API 拉取 MCP 策略才会启用 MCP；API Key 会话下这一步
  失败，于是 MCP 整体关闭。这不是配置错误，`mcp.json` 格式是正确的。

**因此实际方案是**：不依赖 MCP，让 Kiro 使用内置的 `execute_bash` 工具，在容器内
运行 Python 完成矩阵运算、统计、积分等计算（Kiro 会自行编写并执行脚本）。效果与
scicalc 类似，因为 scicalc 底层也是 NumPy/SciPy。

> 你部署的 `scicalc-mcp` 服务仍然可用——Kiro **桌面端**可通过
> `https://<IP>.nip.io/mcp` 正常直连使用它。只是 headless 网关这条路用不上它。

## 组成

| 文件 | 作用 |
| --- | --- |
| `app.py` | FastAPI 网关，暴露 `/v1/chat/completions`、`/v1/models`、`/health` |
| `Dockerfile` | 安装 Kiro CLI（含 unzip 等依赖）+ 网关依赖 |
| `.env.example` | 环境变量模板（KIRO_API_KEY、GATEWAY_TOKEN） |

## 环境变量

| 变量 | 说明 |
| --- | --- |
| `KIRO_API_KEY` | Kiro headless 认证的 API Key（在 https://app.kiro.dev/ 生成） |
| `GATEWAY_TOKEN` | 网关自身访问令牌，客户端用它做 Bearer 鉴权 |
| `KIRO_TIMEOUT` | 单次执行超时（秒），默认 180 |
| `KIRO_TRUST_TOOLS` | 信任的工具范围，留空则 `--trust-all-tools`。当前设为 `execute_bash,fs_read` |

## 部署

网关已集成进上级目录的 `docker-compose.yml`，与 scicalc-mcp、Caddy 一同编排。

1. 在 `docker-compose.yml` 所在目录创建 `.env`（参考 `.env.example`）：
   ```
   KIRO_API_KEY=你的-kiro-api-key
   GATEWAY_TOKEN=一段自己生成的长随机串   # openssl rand -hex 32
   ```

2. 构建并启动：
   ```bash
   docker compose up -d --build
   ```

   > 仅修改 `.env`（如更换 token）或环境变量时，无需 `--build`，`docker compose up -d`
   > 即可让容器重新创建并生效。只有改动代码或 Dockerfile 才需要 `--build`。

3. Caddy 路由：
   - `https://<IP>.nip.io/v1/*`   → 网关（对话接口）
   - `https://<IP>.nip.io/mcp`     → scicalc MCP（供 Kiro 桌面端等 MCP 客户端直连）
   - `https://<IP>.nip.io/health`  → 网关健康检查

4. 验证：
   ```bash
   curl https://<IP>.nip.io/health
   curl -X POST https://<IP>.nip.io/v1/chat/completions \
     -H "Authorization: Bearer <GATEWAY_TOKEN>" \
     -H "Content-Type: application/json" \
     -d '{"model":"kiro","messages":[{"role":"user","content":"计算 2,3,4,7,8 的方差"}]}'
   ```

## 安卓 app 配置

在 app 的「设置」页填：

| 项 | 值 |
| --- | --- |
| LLM Base URL | `https://<IP>.nip.io/v1` |
| LLM API Key | 你设置的 `GATEWAY_TOKEN`（不是 Kiro key！） |
| LLM 模型名称 | `kiro`（任意，网关会忽略） |
| MCP URL | **留空**。工具由服务器端 Kiro 使用内置能力完成，app 不直连 MCP |

这样 Kiro key 只存在于服务器，绝不下发到手机端。app 的 agent 循环在无 MCP、
LLM 不返回 tool_calls 时会退化为普通对话，正好契合本方案。

## 安全说明

- **工具信任已收紧**为 `execute_bash,fs_read`（禁用 fs_write、use_aws 等），
  通过 `KIRO_TRUST_TOOLS` 配置。但 `execute_bash` 本身能执行任意命令，因此真正的
  访问边界依赖 `GATEWAY_TOKEN` 的保密性。
- **务必保护 `GATEWAY_TOKEN`**：任何持有它的人都能通过网关让 Kiro 在容器内执行
  命令。定期轮换（`openssl rand -hex 32`），切勿提交到版本库或公开粘贴。
- `.env` 不要提交到 Git。
- 如需更强隔离，可考虑给网关容器加资源限制、只读文件系统或限制出站网络。

## 已知限制

- **首次运行较慢**：headless 每次是完整 agent 会话，延迟高于普通模型 API
  （数秒到数十秒），可用 `KIRO_TIMEOUT` 调整。
- **无对话状态复用**：headless 每次独立会话，网关把历史消息拼进单个 prompt 传入，
  长对话 prompt 会变长。
- **非流式**：当前返回完整响应，未实现 SSE 流式输出。
- **订阅要求**：API Key 认证仅对 Kiro Pro/Pro+/Pro Max/Power 订阅开放。
