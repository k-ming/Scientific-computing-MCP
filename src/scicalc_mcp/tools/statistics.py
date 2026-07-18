"""描述性与推断性统计工具（NumPy + SciPy）。"""

from __future__ import annotations

import numpy as np
from mcp.server.fastmcp import FastMCP
from scipy import stats

Vector = list[float]


def register(mcp: FastMCP) -> None:
    @mcp.tool()
    def describe(data: Vector) -> dict:
        """返回数值样本的汇总统计量。

        包含样本数、均值、标准差（样本）、方差、最小值、最大值、
        中位数以及第 25/75 百分位数。
        """
        # 空样本无法统计，直接报错。
        arr = np.asarray(data, dtype=float)
        if arr.size == 0:
            raise ValueError("data 至少需要包含一个数值")
        # 单个样本时标准差/方差按 0 处理，避免自由度为 0 的告警。
        return {
            "count": int(arr.size),
            "mean": float(np.mean(arr)),
            "std": float(np.std(arr, ddof=1)) if arr.size > 1 else 0.0,
            "variance": float(np.var(arr, ddof=1)) if arr.size > 1 else 0.0,
            "min": float(np.min(arr)),
            "max": float(np.max(arr)),
            "median": float(np.median(arr)),
            "q1": float(np.percentile(arr, 25)),
            "q3": float(np.percentile(arr, 75)),
        }

    @mcp.tool()
    def correlation(x: Vector, y: Vector, method: str = "pearson") -> dict:
        """计算两个样本之间的相关系数。

        参数:
            x: 第一个样本。
            y: 第二个样本（长度与 x 相同）。
            method: 'pearson' 或 'spearman'。
        """
        # 两个样本长度必须一致。
        a = np.asarray(x, dtype=float)
        b = np.asarray(y, dtype=float)
        if a.shape != b.shape:
            raise ValueError("x 与 y 的长度必须相同")
        # 根据方法选择皮尔逊或斯皮尔曼相关系数。
        if method == "pearson":
            r, p = stats.pearsonr(a, b)
        elif method == "spearman":
            r, p = stats.spearmanr(a, b)
        else:
            raise ValueError("method 必须为 'pearson' 或 'spearman'")
        return {"coefficient": float(r), "p_value": float(p), "method": method}

    @mcp.tool()
    def t_test(
        sample_a: Vector,
        sample_b: Vector | None = None,
        popmean: float = 0.0,
    ) -> dict:
        """执行 t 检验。

        若提供 sample_b，则执行独立双样本 t 检验；
        否则对 sample_a 与 popmean 执行单样本 t 检验。
        """
        a = np.asarray(sample_a, dtype=float)
        # 未提供第二个样本时走单样本检验，否则走双样本检验。
        if sample_b is None:
            t_stat, p = stats.ttest_1samp(a, popmean)
            kind = "one-sample"
        else:
            b = np.asarray(sample_b, dtype=float)
            t_stat, p = stats.ttest_ind(a, b)
            kind = "two-sample"
        return {"t_statistic": float(t_stat), "p_value": float(p), "test": kind}

    @mcp.tool()
    def linear_regression(x: Vector, y: Vector) -> dict:
        """拟合简单线性回归 y = slope * x + intercept。"""
        # 两个样本长度必须一致。
        a = np.asarray(x, dtype=float)
        b = np.asarray(y, dtype=float)
        if a.shape != b.shape:
            raise ValueError("x 与 y 的长度必须相同")
        # 使用 scipy 的 linregress 拟合并返回关键指标。
        result = stats.linregress(a, b)
        return {
            "slope": float(result.slope),
            "intercept": float(result.intercept),
            "r_squared": float(result.rvalue**2),
            "p_value": float(result.pvalue),
            "std_err": float(result.stderr),
        }
