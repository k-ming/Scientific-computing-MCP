# scicalc-mcp

一个提供数学与科学计算能力的 MCP（Model Context Protocol）服务，底层使用
NumPy、pandas、SciPy 和 Matplotlib。可在本地通过 stdio 供 Kiro 使用，也可以
容器化后以 Kubernetes 方式部署到 Google Cloud（GKE），供 Kiro 通过 HTTP 远程访问。

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

## 在 Kiro 中使用（远程 HTTP）

```json
{
  "mcpServers": {
    "scicalc-remote": {
      "url": "http://<EXTERNAL_IP>/mcp",
      "disabled": false,
      "autoApprove": []
    }
  }
}
```

## 项目结构

```
python_mcp/
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
├── Dockerfile
├── pyproject.toml
└── requirements.txt
```
