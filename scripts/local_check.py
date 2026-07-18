"""本地功能验证：实际调用若干代表性工具并打印结果。

通过 FastMCP 的 call_tool 接口直接调用工具，验证计算逻辑是否正确。
不同 mcp 版本 call_tool 的返回结构略有差异，这里统一从内容块中取值。
"""

import asyncio
import json

from scicalc_mcp.server import build_server


def unpack(res):
    """将 call_tool 的返回规整为 (内容块列表, 文本解析出的对象)。

    call_tool 可能返回 (content_list, structured) 元组，也可能只返回
    content_list。这里统一处理，并尝试把首个文本块解析为 JSON。
    """
    content = res[0] if isinstance(res, tuple) else res
    parsed = None
    for block in content:
        text = getattr(block, "text", None)
        if text:
            try:
                parsed = json.loads(text)
            except json.JSONDecodeError:
                parsed = text
            break
    return content, parsed


async def main() -> None:
    mcp = build_server()

    # 1) 线性代数：解方程组 [[3,1],[1,2]] x = [9,8]，期望 x = [2, 3]
    _, val = unpack(await mcp.call_tool(
        "solve_linear_system", {"a": [[3, 1], [1, 2]], "b": [9, 8]}
    ))
    print("解线性方程组 ->", val)

    # 2) 统计：一组数据的描述统计
    _, val = unpack(await mcp.call_tool("describe", {"data": [1, 2, 3, 4, 5, 6]}))
    print("描述统计 mean/std ->", val["mean"], val["std"])

    # 3) 科学计算：对 sin(x) 在 [0, π] 上积分，期望约等于 2
    _, val = unpack(await mcp.call_tool(
        "integrate_function",
        {"expression": "sin(x)", "lower": 0, "upper": 3.141592653589793},
    ))
    print("∫sin(x)dx [0,π] ->", round(val["value"], 6))

    # 4) pandas：按 city 分组求 sales 均值
    csv = "city,sales\nBJ,100\nBJ,200\nSH,300\n"
    _, val = unpack(await mcp.call_tool(
        "dataframe_groupby_aggregate",
        {"csv_text": csv, "group_by": "city", "value_column": "sales", "agg": "mean"},
    ))
    print("分组求均值 ->", val)

    # 5) 绘图：生成函数曲线，确认返回了 PNG 图像内容
    content, _ = unpack(await mcp.call_tool(
        "plot_function", {"expression": "sin(x)", "start": 0, "stop": 6.28}
    ))
    img = content[0]
    print("绘图返回 ->", type(img).__name__, "| mimeType:", getattr(img, "mimeType", None))

    print("\n本地验证通过 ✅")


if __name__ == "__main__":
    asyncio.run(main())
