"""基于 Matplotlib 的绘图工具，返回 PNG 图像。"""

from __future__ import annotations

import io

import matplotlib
import numpy as np

matplotlib.use("Agg")  # 无界面后端，服务器/容器环境必需
import matplotlib.pyplot as plt  # noqa: E402
from mcp.server.fastmcp import FastMCP  # noqa: E402
from mcp.server.fastmcp.utilities.types import Image  # noqa: E402

from scicalc_mcp.safe_eval import make_scalar_func  # noqa: E402

Vector = list[float]


def _fig_to_image(fig) -> Image:
    # 将 Matplotlib 图形写入内存缓冲并封装为 MCP 图像内容。
    buffer = io.BytesIO()
    fig.savefig(buffer, format="png", dpi=100, bbox_inches="tight")
    plt.close(fig)  # 及时关闭图形，避免内存泄漏
    return Image(data=buffer.getvalue(), format="png")


def register(mcp: FastMCP) -> None:
    @mcp.tool()
    def plot_line(
        x: Vector,
        y: Vector,
        title: str = "",
        xlabel: str = "x",
        ylabel: str = "y",
    ) -> Image:
        """根据 x/y 数据绘制折线图，返回 PNG 图像。"""
        # x 与 y 长度必须一致。
        if len(x) != len(y):
            raise ValueError("x 与 y 的长度必须相同")
        fig, ax = plt.subplots(figsize=(6, 4))
        ax.plot(x, y, marker="o", markersize=3)
        ax.set_title(title)
        ax.set_xlabel(xlabel)
        ax.set_ylabel(ylabel)
        ax.grid(True, alpha=0.3)
        return _fig_to_image(fig)

    @mcp.tool()
    def plot_function(
        expression: str,
        start: float,
        stop: float,
        points: int = 200,
        title: str = "",
    ) -> Image:
        """在区间 [start, stop] 上绘制单变量函数曲线。

        参数:
            expression: 以 'x' 为变量的数学表达式，例如 "sin(x)/x"。
            start: 定义域左边界。
            stop: 定义域右边界。
            points: 采样点数（2-5000）。
            title: 可选的图标题。
        """
        # 限制采样点数量，防止过大或过小。
        if not 2 <= points <= 5000:
            raise ValueError("points 必须在 2 到 5000 之间")
        # 编译表达式后在采样点上逐点求值。
        func = make_scalar_func(expression)
        xs = np.linspace(start, stop, points)
        ys = [func(float(v)) for v in xs]
        fig, ax = plt.subplots(figsize=(6, 4))
        ax.plot(xs, ys)
        ax.set_title(title or f"y = {expression}")
        ax.set_xlabel("x")
        ax.set_ylabel("y")
        ax.grid(True, alpha=0.3)
        return _fig_to_image(fig)

    @mcp.tool()
    def plot_histogram(data: Vector, bins: int = 10, title: str = "") -> Image:
        """绘制数值样本的直方图，返回 PNG 图像。"""
        # 分箱数量至少为 1。
        if bins < 1:
            raise ValueError("bins 必须 >= 1")
        fig, ax = plt.subplots(figsize=(6, 4))
        ax.hist(data, bins=bins, edgecolor="black", alpha=0.75)
        ax.set_title(title or "Histogram")
        ax.set_xlabel("value")
        ax.set_ylabel("frequency")
        ax.grid(True, alpha=0.3)
        return _fig_to_image(fig)
