"""基于 SciPy 的工具：优化、积分、插值、求根。"""

from __future__ import annotations

import numpy as np
from mcp.server.fastmcp import FastMCP
from scipy import integrate, interpolate, optimize

from scicalc_mcp.safe_eval import make_scalar_func

Vector = list[float]


def register(mcp: FastMCP) -> None:
    @mcp.tool()
    def integrate_function(expression: str, lower: float, upper: float) -> dict:
        """对单变量函数在区间 [lower, upper] 上做数值积分。

        参数:
            expression: 以 'x' 为变量的数学表达式，例如 "sin(x) * x**2"。
                支持标准数学函数与常量（pi、e）。
            lower: 积分下限。
            upper: 积分上限。

        返回:
            定积分的值以及绝对误差估计。
        """
        # 将表达式编译为安全的可调用函数后使用 quad 积分。
        func = make_scalar_func(expression)
        value, abserr = integrate.quad(func, lower, upper)
        return {"value": float(value), "abs_error": float(abserr)}

    @mcp.tool()
    def find_root(expression: str, guess: float = 0.0) -> dict:
        """在初始猜测附近求解 f(x) = 0 的根。

        参数:
            expression: 以 'x' 为变量的数学表达式。
            guess: 求解器的起始点。
        """
        # 使用割线法求根，需要两个起始点，取 guess 与 guess+1。
        func = make_scalar_func(expression)
        sol = optimize.root_scalar(func, x0=guess, x1=guess + 1.0, method="secant")
        if not sol.converged:
            raise ValueError("求根未收敛")
        return {"root": float(sol.root), "iterations": int(sol.iterations)}

    @mcp.tool()
    def minimize_function(expression: str, guess: float = 0.0) -> dict:
        """在初始猜测附近求单变量函数的局部极小值。

        参数:
            expression: 以 'x' 为变量的数学表达式。
            guess: 优化器的起始点。
        """
        # minimize 处理的是向量参数，这里用 v[0] 取出标量。
        func = make_scalar_func(expression)
        result = optimize.minimize(lambda v: func(v[0]), x0=[guess])
        if not result.success:
            raise ValueError(f"优化失败: {result.message}")
        return {"x": float(result.x[0]), "value": float(result.fun)}

    @mcp.tool()
    def interpolate_values(
        x: Vector,
        y: Vector,
        query: Vector,
        kind: str = "linear",
    ) -> Vector:
        """在指定查询点上进行插值求值。

        参数:
            x: 已知的 x 坐标（需严格递增）。
            y: 已知的 y 坐标。
            query: 需要求值的 x 位置。
            kind: 'linear'、'quadratic' 或 'cubic'。
        """
        # x 与 y 长度必须一致，构造插值函数并允许外推。
        xa = np.asarray(x, dtype=float)
        ya = np.asarray(y, dtype=float)
        if xa.shape != ya.shape:
            raise ValueError("x 与 y 的长度必须相同")
        f = interpolate.interp1d(xa, ya, kind=kind, fill_value="extrapolate")
        return f(np.asarray(query, dtype=float)).tolist()
