"""单变量数学表达式的安全求值。

只暴露经过筛选的数学函数与常量白名单。表达式在编译时禁用了内置函数，
因此诸如 ``__import__('os')`` 之类的任意代码无法执行。
"""

from __future__ import annotations

import math
from typing import Callable

# 从 math 模块中筛选出的允许使用的函数白名单。
_ALLOWED_NAMES: dict[str, object] = {
    name: getattr(math, name)
    for name in (
        "sin", "cos", "tan", "asin", "acos", "atan", "atan2",
        "sinh", "cosh", "tanh", "exp", "log", "log10", "log2",
        "sqrt", "pow", "fabs", "floor", "ceil", "factorial",
        "degrees", "radians", "hypot", "gamma", "erf", "erfc",
    )
}
_ALLOWED_NAMES.update({"pi": math.pi, "e": math.e, "tau": math.tau, "abs": abs})

# 最小化且安全的全局命名空间：不暴露任何内置函数。
_SAFE_GLOBALS: dict[str, object] = {"__builtins__": {}}


def make_scalar_func(expression: str) -> Callable[[float], float]:
    """将以 ``x`` 为变量的表达式编译为可调用对象 f(x) -> float。

    当表达式使用了不允许的名称，或编译/求值失败时，抛出 ValueError。
    """
    # 先编译表达式，语法错误在此阶段暴露。
    try:
        code = compile(expression, "<expression>", "eval")
    except SyntaxError as exc:
        raise ValueError(f"无效的表达式: {exc}") from exc

    # 校验表达式引用的每个名称，只允许白名单中的名称以及变量 x。
    for name in code.co_names:
        if name not in _ALLOWED_NAMES and name != "x":
            raise ValueError(f"表达式中不允许使用名称 '{name}'")

    def func(x: float) -> float:
        # 每次调用时构造局部命名空间，注入当前的 x 值。
        local = dict(_ALLOWED_NAMES)
        local["x"] = x
        try:
            return float(eval(code, _SAFE_GLOBALS, local))  # noqa: S307 - 已沙箱化
        except Exception as exc:  # noqa: BLE001
            raise ValueError(f"在 x={x} 处求值失败: {exc}") from exc

    return func
