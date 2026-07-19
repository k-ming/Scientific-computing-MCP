"""OpenAI 兼容网关，内部转调 Kiro CLI headless 模式。

对外暴露 ``POST /v1/chat/completions``，安卓 app（或任何 OpenAI 兼容客户端）
可直接使用。网关内部：

1. 从对话消息中拼出一个 prompt；
2. 调用 ``kiro-cli chat --no-interactive --trust-all-tools <prompt>``，
   通过环境变量 ``KIRO_API_KEY`` 认证；
3. Kiro CLI 端到端执行（含其自身配置的 MCP 工具，如 scicalc），返回最终文本；
4. 网关把输出包装成 OpenAI Chat Completions 响应格式返回。

鉴权：客户端需在 ``Authorization: Bearer <GATEWAY_TOKEN>`` 中携带网关令牌，
与真正的 KIRO_API_KEY 隔离，避免把 Kiro key 下发到移动端。
"""

from __future__ import annotations

import os
import time
import uuid
import shutil
import asyncio
import logging

from fastapi import FastAPI, Header, HTTPException
from fastapi.responses import JSONResponse
from pydantic import BaseModel

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("kiro-gateway")

# 网关自身的访问令牌（与 KIRO_API_KEY 分离）。留空则不校验（不推荐）。
GATEWAY_TOKEN = os.environ.get("GATEWAY_TOKEN", "")
# Kiro CLI 可执行文件名，容器内应已安装并在 PATH 中。
KIRO_CLI = os.environ.get("KIRO_CLI_BIN", "kiro-cli")
# 单次执行超时（秒）。
KIRO_TIMEOUT = int(os.environ.get("KIRO_TIMEOUT", "180"))
# 授权的工具范围；留空表示信任全部工具。
TRUST_TOOLS = os.environ.get("KIRO_TRUST_TOOLS", "")

app = FastAPI(title="Kiro OpenAI-compatible Gateway")


class Message(BaseModel):
    role: str
    content: str | None = None


class ChatRequest(BaseModel):
    model: str | None = None
    messages: list[Message]
    # 其余 OpenAI 字段接收但忽略。
    stream: bool | None = False


def build_prompt(messages: list[Message]) -> str:
    """把 OpenAI 消息数组压平成单个 prompt 字符串。

    headless 模式只接受单个 prompt，因此把 system/user/assistant 历史
    拼接成带角色标注的文本，交给 Kiro CLI。
    """
    parts: list[str] = []
    for m in messages:
        if not m.content:
            continue
        role = m.role.lower()
        if role == "system":
            parts.append(f"[System]\n{m.content}")
        elif role == "assistant":
            parts.append(f"[Assistant]\n{m.content}")
        elif role == "tool":
            parts.append(f"[Tool Result]\n{m.content}")
        else:
            parts.append(f"[User]\n{m.content}")
    return "\n\n".join(parts).strip()


async def run_kiro(prompt: str) -> str:
    """调用 Kiro CLI headless 并返回其标准输出文本。"""
    if not shutil.which(KIRO_CLI):
        raise HTTPException(status_code=500, detail=f"未找到 {KIRO_CLI}，请确认已安装 Kiro CLI。")

    args = [KIRO_CLI, "chat", "--no-interactive"]
    if TRUST_TOOLS.strip():
        args.append(f"--trust-tools={TRUST_TOOLS.strip()}")
    else:
        args.append("--trust-all-tools")
    args.append(prompt)

    logger.info("运行 Kiro CLI: %s ... (prompt %d 字符)", " ".join(args[:4]), len(prompt))

    proc = await asyncio.create_subprocess_exec(
        *args,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.PIPE,
        env={**os.environ},
    )
    try:
        stdout, stderr = await asyncio.wait_for(proc.communicate(), timeout=KIRO_TIMEOUT)
    except asyncio.TimeoutError:
        proc.kill()
        raise HTTPException(status_code=504, detail="Kiro CLI 执行超时。")

    out = stdout.decode("utf-8", errors="replace").strip()
    err = stderr.decode("utf-8", errors="replace").strip()

    if proc.returncode != 0:
        logger.error("Kiro CLI 失败 (code=%s): %s", proc.returncode, err)
        raise HTTPException(status_code=502, detail=f"Kiro CLI 执行失败: {err or out}")

    return out or "(Kiro 未返回文本输出)"


def check_auth(authorization: str | None) -> None:
    if not GATEWAY_TOKEN:
        return  # 未配置令牌则不校验。
    expected = f"Bearer {GATEWAY_TOKEN}"
    if authorization != expected:
        raise HTTPException(status_code=401, detail="无效的网关令牌。")


@app.get("/health")
async def health() -> dict:
    return {"status": "ok", "kiro_cli_found": bool(shutil.which(KIRO_CLI))}


@app.get("/v1/models")
async def list_models() -> dict:
    """返回一个占位模型列表，方便部分客户端做模型选择。"""
    return {
        "object": "list",
        "data": [{"id": "kiro", "object": "model", "owned_by": "kiro"}],
    }


@app.post("/v1/chat/completions")
async def chat_completions(
    req: ChatRequest,
    authorization: str | None = Header(default=None),
) -> JSONResponse:
    check_auth(authorization)

    prompt = build_prompt(req.messages)
    if not prompt:
        raise HTTPException(status_code=400, detail="messages 为空。")

    answer = await run_kiro(prompt)

    now = int(time.time())
    response = {
        "id": f"chatcmpl-{uuid.uuid4().hex[:24]}",
        "object": "chat.completion",
        "created": now,
        "model": req.model or "kiro",
        "choices": [
            {
                "index": 0,
                "message": {"role": "assistant", "content": answer},
                "finish_reason": "stop",
            }
        ],
        "usage": {"prompt_tokens": 0, "completion_tokens": 0, "total_tokens": 0},
    }
    return JSONResponse(response)
