"""冒烟测试：验证服务器能构建，且各工具已正确注册。"""

import asyncio

from scicalc_mcp.server import build_server


async def main() -> None:
    # 构建服务器并列出已注册的工具，确认各模块都成功加载。
    mcp = build_server()
    tools = await mcp.list_tools()
    names = sorted(t.name for t in tools)
    print(f"已注册工具数量: {len(names)}")
    for name in names:
        print(f"  - {name}")


if __name__ == "__main__":
    asyncio.run(main())
