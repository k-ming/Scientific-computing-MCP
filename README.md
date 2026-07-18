# scicalc-mcp

一个提供数学与科学计算能力的 MCP（Model Context Protocol）服务，底层使用
NumPy、pandas、SciPy 和 Matplotlib。可在本地通过 stdio 供 Kiro 使用，也可以
容器化后部署到 Google Cloud，供 Kiro 通过 HTTPS 远程访问。远程部署提供两种方式：
以 Kubernetes（GKE）部署，或在单台虚拟机上用 docker-compose 搭配 Caddy 自动 HTTPS。

## 功能工具

共注册 19 个工具，按领域划分：

- 线性代数（NumPy）：`matrix_multiply`、`matrix_inverse`、`determinant`、
  `solve_linear_system`、`eigen`
- 统计（NumPy/SciPy）：`describe`、`correlation`、`t_test`、`linear_regression`
- 科学计算（SciPy）：`integrate_function`、`find_root`、`minimize_function`、
  `interpolate_values`
- 数据处理（pandas）：`dataframe_describe`、`dataframe_groupby_aggregate`、
  `dataframe_correlation_matrix`
- 绘图（Matplotlib，返回 PNG）：`plot_line`、`plot_function`、`plot_histogram`

> 涉及函数表达式的工具（积分、求根、绘图等）使用受限的安全求值器，仅允许
> 白名单内的数学函数与常量，避免任意代码执行。

## 本地开发

```powershell
# 创建并激活虚拟环境（若尚未创建）
python -m venv .venv
.\.venv\Scripts\Activate.ps1

# 安装项目及依赖
python -m pip install -e .

# 冒烟测试：列出已注册的工具
python scripts\smoke_test.py
```

默认传输方式为 `stdio`，适合本地在 IDE 中直接调用。

## 在 Kiro 中使用（本地 stdio）

编辑用户级或工作区级的 `mcp.json`（用户级路径：`~/.kiro/settings/mcp.json`）：

```json
{
  "mcpServers": {
    "scicalc": {
      "command": "d:\\dev\\Python\\python_mcp\\.venv\\Scripts\\scicalc-mcp.exe",
      "args": [],
      "env": { "MCP_TRANSPORT": "stdio" },
      "disabled": false,
      "autoApprove": []
    }
  }
}
```

保存后在 Kiro 的 MCP Server 视图中重新连接即可。

## 容器化

```powershell
# 构建镜像
docker build -t scicalc-mcp:latest .

# 本地以 HTTP 传输方式运行（映射到 8000 端口）
docker run --rm -p 8000:8000 scicalc-mcp:latest
```

容器默认使用 `streamable-http` 传输，端点为 `http://<host>:8000/mcp`。

## 部署到 Google Cloud（GKE）

前置条件：已安装 `gcloud`、`kubectl`，并已创建 GKE 集群与 Artifact Registry 仓库。

```powershell
# 1) 设置变量（按需替换）
$PROJECT_ID = "your-project-id"
$REGION     = "asia-east1"
$REPO       = "mcp"
$IMAGE      = "$REGION-docker.pkg.dev/$PROJECT_ID/$REPO/scicalc-mcp:latest"

# 2) 配置 Docker 认证并构建、推送镜像
gcloud auth configure-docker "$REGION-docker.pkg.dev"
docker build -t $IMAGE .
docker push $IMAGE

# 3) 获取集群凭据
gcloud container clusters get-credentials <CLUSTER_NAME> --region $REGION --project $PROJECT_ID

# 4) 将 k8s/deployment.yaml 中的镜像地址替换为 $IMAGE，然后应用清单
kubectl apply -f k8s\deployment.yaml
kubectl apply -f k8s\service.yaml
kubectl apply -f k8s\ingress.yaml

# 5) 查看 Ingress 分配的外部 IP
kubectl get ingress scicalc-mcp
```

Ingress 就绪后，MCP 端点为 `http://<EXTERNAL_IP>/mcp`。

> 生产环境建议启用 HTTPS：预留全局静态 IP、创建 `ManagedCertificate`，并在
> `k8s/ingress.yaml` 中取消对应注释。此外应在服务前增加鉴权（例如网关层的
> 认证、IAP 或 API 网关），因为该服务本身不含访问控制。

## 部署到单台服务器（docker-compose + 自动 HTTPS）

如果不需要 Kubernetes，只想在一台虚拟机（如 GCE 实例）上快速跑起来并让
Kiro 通过 HTTPS 远程访问，用 docker-compose 搭配 Caddy 反向代理是最简单的方式。
项目根目录已提供 `docker-compose.yml` 与 `Caddyfile`。

### 为什么需要 HTTPS + 域名

Kiro 出于安全考虑，**远程 MCP 服务的地址必须是 `https://` 或 `localhost`**，
纯 `http://<公网IP>` 会被直接忽略并提示：

```
Remote MCP Servers must use https or localhost, ignoring.
```

这带来两个要求：**需要一个域名** + **需要 TLS 证书**。下面用免费方案同时解决。

### nip.io：免注册的免费域名

Let's Encrypt 之类的证书颁发机构不会给裸 IP 签发证书，必须有域名。而
[nip.io](https://nip.io) 提供了一个巧妙的免费泛解析服务，把任意 IP 直接变成域名，
**无需注册、无需配置 DNS**。它的解析规则是：

```
<IP>.nip.io  →  自动解析回该 IP
```

例如服务器公网 IP 是 `34.4.106.148`，那么 `34.4.106.148.nip.io` 会自动解析到
`34.4.106.148`。这样就有了一个可用于申请证书的合法域名，且完全免费。

### Caddy：自动申请并续期证书

[Caddy](https://caddyserver.com) 是一个反向代理，最大的便利是**自动向 Let's Encrypt
申请免费 TLS 证书并自动续期**，无需手动操作 certbot。工作流程如下：

```
公网请求 https://<IP>.nip.io/mcp
        │
        ▼
   Caddy (443)  ── 首次启动时通过 80 端口的 HTTP-01 验证向 Let's Encrypt 申请证书
        │           证书持久化在 caddy_data 卷中，90 天有效期到期前自动续期
        ▼
  scicalc-mcp:8000 (仅在 compose 内部网络暴露，不直接对公网开放)
```

`Caddyfile` 的核心配置就是把域名指向内部服务：

```
34.4.106.148.nip.io {
    reverse_proxy scicalc-mcp:8000
}
```

Caddy 看到这个域名后会自动完成"申请证书 → 启用 HTTPS → 反向代理"整个链路。

### 部署步骤

前置条件：服务器已安装 Docker 与 docker compose，且 GCP 防火墙已放通
**80 端口**（Let's Encrypt 证书验证与续期所需）与 **443 端口**（对外 HTTPS 服务）。
8000 端口无需再对公网开放，流量统一经由 Caddy 的 443。

```bash
# 1) 把 Caddyfile 中的 IP 换成你服务器的实际公网 IP
#    34.4.106.148.nip.io  →  <你的IP>.nip.io

# 2) 构建并启动（scicalc-mcp + caddy 两个容器）
docker compose up -d --build

# 3) 查看状态，两个容器都应为 Up；caddy 日志中出现证书申请成功信息
docker compose ps
docker compose logs caddy

# 4) 验证 HTTPS 链路（返回 406 属正常，说明服务健康）
curl -i https://<你的IP>.nip.io/mcp
```

> Windows 上用 curl 若报 `schannel ... 0x80092013 吊销功能无法检查吊销`，
> 是本地无法联系 CA 的证书吊销检查服务所致，与服务器无关。加
> `--ssl-no-revoke` 参数即可跳过该检查进行验证。

## 在 Kiro 中使用（远程 HTTPS）

配置 `mcp.json`，URL 使用 nip.io 域名的 HTTPS 地址：

```json
{
  "mcpServers": {
    "scicalc-remote": {
      "url": "https://<你的IP>.nip.io/mcp",
      "disabled": false,
      "autoApprove": []
    }
  }
}
```

保存后在 Kiro 的 MCP Server 视图中重新连接。

> **安全提醒**：该服务本身不含任何鉴权，一旦经 HTTPS 暴露到公网，任何人都能
> 调用并消耗算力。建议将 443 端口的防火墙来源限制为你自己的公网 IP（同时保留
> 80 端口开放以便证书续期），或在 Caddy 层增加认证。

## 项目结构

```
Scientific-computing-MCP/
├── src/scicalc_mcp/
│   ├── server.py          # 服务入口，选择传输方式并注册工具
│   ├── safe_eval.py       # 数学表达式安全求值器
│   └── tools/             # 按领域拆分的工具模块
│       ├── linear_algebra.py
│       ├── statistics.py
│       ├── scientific.py
│       ├── dataframe.py
│       └── plotting.py
├── scripts/smoke_test.py  # 列出已注册工具的冒烟测试
├── k8s/                   # GKE 部署清单
│   ├── deployment.yaml
│   ├── service.yaml
│   └── ingress.yaml
├── docker-compose.yml     # 单机部署：scicalc-mcp + Caddy 自动 HTTPS
├── Caddyfile              # Caddy 反向代理与 nip.io 域名的 TLS 配置
├── Dockerfile
├── pyproject.toml
└── requirements.txt
```
