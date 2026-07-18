"""基于 NumPy 的线性代数工具。"""

from __future__ import annotations

import numpy as np
from mcp.server.fastmcp import FastMCP

Matrix = list[list[float]]
Vector = list[float]


def register(mcp: FastMCP) -> None:
    @mcp.tool()
    def matrix_multiply(a: Matrix, b: Matrix) -> Matrix:
        """矩阵相乘 A (m x n) 与 B (n x p)。

        参数:
            a: 左矩阵，按行组成的列表。
            b: 右矩阵，按行组成的列表。

        返回:
            乘积矩阵 A @ B，按行组成的列表。
        """
        # 转为浮点数组后做矩阵乘法，再转回普通列表返回。
        result = np.asarray(a, dtype=float) @ np.asarray(b, dtype=float)
        return result.tolist()

    @mcp.tool()
    def matrix_inverse(matrix: Matrix) -> Matrix:
        """计算方阵的逆矩阵。

        当矩阵奇异或非方阵时抛出清晰的错误。
        """
        # 校验必须为方阵。
        arr = np.asarray(matrix, dtype=float)
        if arr.ndim != 2 or arr.shape[0] != arr.shape[1]:
            raise ValueError("矩阵必须为方阵 (n x n)")
        # 奇异矩阵会触发 LinAlgError，转换为友好的错误信息。
        try:
            return np.linalg.inv(arr).tolist()
        except np.linalg.LinAlgError as exc:
            raise ValueError(f"矩阵不可逆: {exc}") from exc

    @mcp.tool()
    def determinant(matrix: Matrix) -> float:
        """计算方阵的行列式。"""
        # 校验必须为方阵后计算行列式。
        arr = np.asarray(matrix, dtype=float)
        if arr.ndim != 2 or arr.shape[0] != arr.shape[1]:
            raise ValueError("矩阵必须为方阵 (n x n)")
        return float(np.linalg.det(arr))

    @mcp.tool()
    def solve_linear_system(a: Matrix, b: Vector) -> Vector:
        """求解线性方程组 A x = b 得到 x。

        参数:
            a: 系数矩阵 (n x n)。
            b: 长度为 n 的右端向量。
        """
        # 使用 numpy 求解，无解或奇异时给出清晰错误。
        arr = np.asarray(a, dtype=float)
        rhs = np.asarray(b, dtype=float)
        try:
            return np.linalg.solve(arr, rhs).tolist()
        except np.linalg.LinAlgError as exc:
            raise ValueError(f"无法求解方程组: {exc}") from exc

    @mcp.tool()
    def eigen(matrix: Matrix) -> dict:
        """计算方阵的特征值与特征向量。

        返回包含 'eigenvalues' 和 'eigenvectors' 的字典（每一列为一个特征向量）。
        复数结果以 [实部, 虚部] 的数对形式返回。
        """
        arr = np.asarray(matrix, dtype=float)
        values, vectors = np.linalg.eig(arr)

        def encode(x: np.ndarray) -> list:
            # 若为复数，则拆分为 [实部, 虚部] 数对；否则直接转列表。
            if np.iscomplexobj(x):
                return [[float(v.real), float(v.imag)] for v in np.ravel(x)]
            return x.tolist()

        return {
            "eigenvalues": encode(values),
            "eigenvectors": [encode(vectors[:, i]) for i in range(vectors.shape[1])],
            "complex": bool(np.iscomplexobj(values)),
        }
