"""基于 pandas 的表格数据分析工具。"""

from __future__ import annotations

import io

import pandas as pd
from mcp.server.fastmcp import FastMCP


def _read_csv(csv_text: str) -> pd.DataFrame:
    # 将 CSV 文本解析为 DataFrame，空文本直接报错。
    if not csv_text.strip():
        raise ValueError("csv_text 为空")
    return pd.read_csv(io.StringIO(csv_text))


def register(mcp: FastMCP) -> None:
    @mcp.tool()
    def dataframe_describe(csv_text: str) -> dict:
        """解析 CSV 文本并返回各列的汇总统计。

        参数:
            csv_text: 包含表头行的原始 CSV 内容。

        返回:
            包含 'shape'、'columns'、'dtypes' 以及 'describe'
            （DataFrame.describe 的结果）的字典。
        """
        df = _read_csv(csv_text)
        described = df.describe(include="all").to_dict()
        # 将不可序列化的值（NaN、numpy 类型）转换为普通 float/None。
        clean = {
            col: {k: (None if pd.isna(v) else _to_scalar(v)) for k, v in stats.items()}
            for col, stats in described.items()
        }
        return {
            "shape": list(df.shape),
            "columns": list(df.columns),
            "dtypes": {c: str(t) for c, t in df.dtypes.items()},
            "describe": clean,
        }

    @mcp.tool()
    def dataframe_groupby_aggregate(
        csv_text: str,
        group_by: str,
        value_column: str,
        agg: str = "mean",
    ) -> dict:
        """按某列分组并对某个数值列做聚合。

        参数:
            csv_text: 包含表头行的原始 CSV 内容。
            group_by: 用于分组的列名。
            value_column: 需要聚合的数值列。
            agg: 'mean'、'sum'、'min'、'max'、'count'、'median'、'std' 之一。
        """
        df = _read_csv(csv_text)
        # 校验分组列与数值列都存在。
        for col in (group_by, value_column):
            if col not in df.columns:
                raise ValueError(f"数据中不存在列 '{col}'")
        # 校验聚合方式在允许范围内。
        allowed = {"mean", "sum", "min", "max", "count", "median", "std"}
        if agg not in allowed:
            raise ValueError(f"agg 必须是 {sorted(allowed)} 之一")
        grouped = df.groupby(group_by)[value_column].agg(agg)
        return {str(k): _to_scalar(v) for k, v in grouped.items()}

    @mcp.tool()
    def dataframe_correlation_matrix(csv_text: str) -> dict:
        """返回数值列之间的皮尔逊相关系数矩阵。"""
        df = _read_csv(csv_text)
        # 只保留数值列，没有数值列则报错。
        numeric = df.select_dtypes(include="number")
        if numeric.empty:
            raise ValueError("未找到任何数值列")
        corr = numeric.corr()
        return {
            col: {c: _to_scalar(v) for c, v in row.items()}
            for col, row in corr.to_dict().items()
        }


def _to_scalar(value):
    """尽力将 numpy/pandas 标量转换为原生 Python 类型。"""
    # numpy 标量提供 .item()，普通值则原样返回。
    try:
        return value.item()
    except AttributeError:
        return value
