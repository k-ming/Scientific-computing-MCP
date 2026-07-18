# 使用精简的官方 Python 镜像作为基础。
FROM python:3.12-slim

# 避免生成 .pyc 文件并让日志实时输出。
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

# Matplotlib 在容器内需要一个可写的配置目录。
ENV MPLCONFIGDIR=/tmp/matplotlib

# 远程部署默认使用 HTTP 传输，监听 8000 端口。
ENV MCP_TRANSPORT=streamable-http \
    MCP_HOST=0.0.0.0 \
    MCP_PORT=8000

WORKDIR /app

# 先复制依赖清单以充分利用镜像层缓存。
# README.md 必须一并复制，因为 pyproject.toml 通过 readme 字段引用它，
# 缺少会导致 hatchling 构建时报 "Readme file does not exist"。
COPY pyproject.toml requirements.txt README.md ./
COPY src ./src

# 安装项目及其依赖。
RUN pip install --upgrade pip && pip install .

# 使用非 root 用户运行，提升安全性。
RUN useradd --create-home --uid 10001 appuser
USER appuser

EXPOSE 8000

# 启动 MCP 服务。
CMD ["scicalc-mcp"]
