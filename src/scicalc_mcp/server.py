"""科学计算 MCP 服务的入口。

传输方式通过环境变量 ``MCP_TRANSPORT`` 选择：

* ``stdio``          - 默认值，用于本地在 Kiro 等 IDE 中使用。
* ``streamable-http``- 用于远程部署（例如部署到 GKE）。绑定到
  ``MCP_HOST``:``MCP_PORT``（默认 0.0.0.0:8000），路径为 ``/mcp``。
"""

from __future__ import annotations

import os

from mcp.server.fastmcp import FastMCP

from scicalc_mcp.tools import (
    dataframe,
    linear_algebra,
    plotting,
    scientific,
    statistics,
)


def build_server() -> FastMCP:
    # 从环境变量读取监听地址与端口，远程部署时可覆盖。
    host = os.environ.get("MCP_HOST", "0.0.0.0")
    port = int(os.environ.get("MCP_PORT", "8000"))
    mcp = FastMCP("scicalc-mcp", host=host, port=port)

    # 逐个注册各领域的工具模块。
    linear_algebra.register(mcp)
    statistics.register(mcp)
    scientific.register(mcp)
    dataframe.register(mcp)
    plotting.register(mcp)
    return mcp


def main() -> None:
    # 根据环境变量选择传输方式，默认使用 stdio。
    transport = os.environ.get("MCP_TRANSPORT", "stdio")
    mcp = build_server()
    if transport in ("http", "streamable-http"):
        mcp.run(transport="streamable-http")
    elif transport == "sse":
        mcp.run(transport="sse")
    else:
        mcp.run(transport="stdio")


if __name__ == "__main__":
    main()
