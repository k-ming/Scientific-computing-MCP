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
import re
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

# 匹配 ANSI 转义序列（颜色、光标控制等），用于清洗 CLI 输出。
ANSI_ESCAPE_RE = re.compile(r"\x1b\[[0-9;]*[A-Za-z]")


def clean_cli_output(text: str) -> str:
    """去除 Kiro CLI 输出中的 ANSI 控制码与提示符，得到纯净文本。"""
    # 移除 ANSI 转义序列。
    cleaned = ANSI_ESCAPE_RE.sub("", text)
    # 逐行去掉行首的 CLI 提示符 "> " 及多余空白。
    lines = [re.sub(r"^\s*>\s?", "", line) for line in cleaned.splitlines()]
    return "\n".join(lines).strip()


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


# 网关自身的占位模型名，收到它时不切换 Kiro 模型（用账号默认）。
DEFAULT_MODEL_ALIAS = "kiro"


async def _run_cli(args: list[str], timeout: int) -> tuple[int, str, str]:
    """运行一个 kiro-cli 子命令，返回 (returncode, stdout, stderr)。"""
    proc = await asyncio.create_subprocess_exec(
        *args,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.PIPE,
        env={**os.environ},
    )
    stdout, stderr = await asyncio.wait_for(proc.communicate(), timeout=timeout)
    return (
        proc.returncode or 0,
        stdout.decode("utf-8", errors="replace").strip(),
        stderr.decode("utf-8", errors="replace").strip(),
    )


async def set_default_model(model: str) -> None:
    """通过 settings 把 Kiro 的默认对话模型设为指定值。

    headless 的 chat 命令没有 --model 参数，因此用 chat.defaultModel 设置项
    来切换模型。留空或占位别名则不改动，沿用账号默认模型。
    """
    if not model or model == DEFAULT_MODEL_ALIAS:
        return
    try:
        code, _out, err = await _run_cli(
            [KIRO_CLI, "settings", "chat.defaultModel", model], timeout=30
        )
        if code != 0:
            logger.warning("设置模型 %s 失败: %s", model, err)
    except Exception as e:  # noqa: BLE001
        logger.warning("设置模型 %s 异常: %s", model, e)


async def run_kiro(prompt: str, model: str | None = None) -> str:
    """调用 Kiro CLI headless 并返回其标准输出文本。"""
    if not shutil.which(KIRO_CLI):
        raise HTTPException(status_code=500, detail=f"未找到 {KIRO_CLI}，请确认已安装 Kiro CLI。")

    # 若客户端指定了具体模型，先设为默认模型。
    if model:
        await set_default_model(model)

    args = [KIRO_CLI, "chat", "--no-interactive"]
    if TRUST_TOOLS.strip():
        args.append(f"--trust-tools={TRUST_TOOLS.strip()}")
    else:
        args.append("--trust-all-tools")
    args.append(prompt)

    logger.info("运行 Kiro CLI: %s ... (prompt %d 字符, model=%s)", " ".join(args[:4]), len(prompt), model or "默认")

    try:
        code, out, err = await _run_cli(args, timeout=KIRO_TIMEOUT)
    except asyncio.TimeoutError:
        raise HTTPException(status_code=504, detail="Kiro CLI 执行超时。")

    if code != 0:
        logger.error("Kiro CLI 失败 (code=%s): %s", code, err)
        raise HTTPException(status_code=502, detail=f"Kiro CLI 执行失败: {err or out}")

    return clean_cli_output(out) or "(Kiro 未返回文本输出)"


def check_auth(authorization: str | None) -> None:
    if not GATEWAY_TOKEN:
        return  # 未配置令牌则不校验。
    expected = f"Bearer {GATEWAY_TOKEN}"
    if authorization != expected:
        raise HTTPException(status_code=401, detail="无效的网关令牌。")


@app.get("/health")
async def health() -> dict:
    return {"status": "ok", "kiro_cli_found": bool(shutil.which(KIRO_CLI))}


def _extract_model_id(item: dict) -> str | None:
    """从单个模型对象里提取模型标识。"""
    return (
        item.get("model_id")
        or item.get("id")
        or item.get("model_name")
        or item.get("name")
        or item.get("model")
    )


def _parse_models(raw: str) -> list[str]:
    """解析 kiro-cli chat --list-models --format json 的输出，提取模型 ID 列表。

    实际输出结构为：{"models":[{"model_id":"...","model_name":"...",...}], "default_model":"auto"}
    同时兼容纯数组、纯文本等其它可能格式。
    """
    raw = clean_cli_output(raw)
    try:
        import json as _json
        data = _json.loads(raw)
        items = None
        if isinstance(data, dict):
            items = data.get("models") or data.get("data")
        elif isinstance(data, list):
            items = data
        if isinstance(items, list):
            names: list[str] = []
            for item in items:
                if isinstance(item, str):
                    names.append(item)
                elif isinstance(item, dict):
                    name = _extract_model_id(item)
                    if name:
                        names.append(str(name))
            if names:
                return names
    except Exception:  # noqa: BLE001
        pass
    # 纯文本回退：逐行清洗。
    models = []
    for line in raw.splitlines():
        s = line.strip().lstrip("*-•>").strip()
        s = re.sub(r"\s*\((current|default|active)\)\s*$", "", s, flags=re.I)
        if s and " " not in s:
            models.append(s)
    return models


@app.get("/v1/models")
async def list_models() -> dict:
    """列出 Kiro 当前可用的模型，OpenAI /v1/models 格式。"""
    ids: list[str] = []
    if shutil.which(KIRO_CLI):
        try:
            code, out, err = await _run_cli(
                [KIRO_CLI, "chat", "--list-models", "--format", "json"], timeout=30
            )
            if code == 0:
                ids = _parse_models(out)
            else:
                logger.warning("列出模型失败: %s", err)
        except Exception as e:  # noqa: BLE001
            logger.warning("列出模型异常: %s", e)

    # 拉取失败时，至少给出占位别名（表示用账号默认模型）。
    if not ids:
        ids = [DEFAULT_MODEL_ALIAS]

    return {
        "object": "list",
        "data": [{"id": mid, "object": "model", "owned_by": "kiro"} for mid in ids],
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

    answer = await run_kiro(prompt, model=req.model)

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
