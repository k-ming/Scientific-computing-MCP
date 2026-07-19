# Kiro OpenAI 兼容网关：内含 Kiro CLI + FastAPI 网关。
FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

# 安装 Kiro CLI 所需的基础工具。
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates bash git \
    && rm -rf /var/lib/apt/lists/*

# 安装 Kiro CLI（官方安装脚本）。
RUN curl -fsSL https://cli.kiro.dev/install | bash

# 确保 kiro-cli 在 PATH 中（安装脚本通常装到 /root/.local/bin 或 /usr/local/bin）。
ENV PATH="/root/.local/bin:/usr/local/bin:${PATH}"

WORKDIR /app
COPY requirements.txt ./
RUN pip install --upgrade pip && pip install -r requirements.txt
COPY app.py ./

EXPOSE 9000

CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "9000"]
