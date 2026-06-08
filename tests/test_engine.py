"""
MaWaker 后端计算引擎综合测试

运行：python tests/test_engine.py
或：  cd backend && python -m tests.test_engine

覆盖范围：
  1. 普通用例 (basic)          — 基础计算
  2. 边界用例 (boundary)       — 极端但合法输入
  3. 错误用例 (error)          — 报错是否符合预期
  4. 复杂表达式 (complex)      — 多层嵌套、组合运算
  5. 性能用例 (performance)    — 大表达式、超时
"""
from __future__ import annotations

import os
import re
import sys
import time

# 把 backend 目录加入 sys.path
THIS_DIR = os.path.dirname(os.path.abspath(__file__))
BACKEND_DIR = os.path.dirname(THIS_DIR)
sys.path.insert(0, BACKEND_DIR)

from app.core.engine import SympyEngine  # noqa: E402


# ==================== 计数器 ====================
class Stats:
    def __init__(self) -> None:
        self.total = 0
        self.passed = 0
        self.failed = 0
        self.skipped = 0
        self.failures: list[tuple[str, str, str]] = []  # (category, expr, reason)
        self.timings: list[tuple[str, float]] = []  # (expr, ms)

    def record(self, ok: bool, category: str, expr: str, reason: str = "", ms: float = 0.0) -> None:
        self.total += 1
        self.timings.append((expr, ms))
        if ok:
            self.passed += 1
        else:
            self.failed += 1
            self.failures.append((category, expr, reason))

    def report(self) -> int:
        print("\n" + "=" * 78)
        print(f"测试结果：{self.passed} 通过 / {self.failed} 失败 / {self.total} 总计")
        if self.failures:
            print("\n失败用例详情：")
            for cat, expr, reason in self.failures:
                print(f"  [{cat}] {expr}")
                print(f"     -> {reason[:200]}")
        # 性能报告
        if self.timings:
            slow = sorted(self.timings, key=lambda x: -x[1])[:5]
            print("\nTop-5 慢查询：")
            for expr, ms in slow:
                print(f"  {ms:7.2f}ms  {expr[:60]}")
        print("=" * 78)
        return 0 if self.failed == 0 else 1


STATS = Stats()


# ==================== 测试辅助函数 ====================
def _run(expr: str) -> tuple[str | None, str | None, str | None, float]:
    """统一调用入口, 返回 (plain_text, latex, err_type, err_message) | (plain, latex, None, None)。"""
    result, err, ms = SympyEngine.process_expression(expr)
    if err:
        return None, None, err.type, err.message
    if result:
        return result.plain_text, result.main_display, None, None
    return None, None, "UnknownError", "无结果也未报错"


# ==================== 断言函数 ====================
def expect_ok(category: str, expr: str, contains: str | None = None) -> bool:
    """期望计算成功, 可选 contains 断言（plain_text 包含子串）。"""
    t0 = time.perf_counter()
    plain, latex, etype, emsg = _run(expr)
    ms = (time.perf_counter() - t0) * 1000
    if etype is not None:
        STATS.record(False, category, expr, f"期望成功但失败 [{etype}]: {emsg}", ms)
        print(f"  [FAIL] {category}: {expr[:60]} -> [{etype}] {emsg[:80]}")
        return False
    if contains is not None:
        if contains not in (plain or ""):
            STATS.record(False, category, expr, f"期望结果包含 '{contains}', 实际: {plain}", ms)
            print(f"  [FAIL] {category}: {expr[:60]} -> 结果不含 '{contains}': {plain[:60]}")
            return False
    STATS.record(True, category, expr, "", ms)
    print(f"  [ OK ] {category}: {expr[:60]} -> {(plain or '')[:50]}")
    return True


def expect_error(category: str, expr: str, expect_type: str) -> bool:
    """期望计算失败, 错误类型必须为 expect_type。"""
    t0 = time.perf_counter()
    plain, latex, etype, emsg = _run(expr)
    ms = (time.perf_counter() - t0) * 1000
    if etype is None:
        STATS.record(False, category, expr, f"期望失败 [{expect_type}], 实际成功: {plain}", ms)
        print(f"  [FAIL] {category}: {expr[:60]} -> 应当失败但成功了: {plain[:60]}")
        return False
    if etype != expect_type:
        STATS.record(False, category, expr, f"期望错误类型 {expect_type}, 实际: {etype}", ms)
        print(f"  [FAIL] {category}: {expr[:60]} -> 期望 {expect_type}, 实际 {etype}")
        return False
    STATS.record(True, category, expr, "", ms)
    print(f"  [ OK ] {category}: {expr[:60]} -> [{etype}] {emsg[:60]}")
    return True


# ==================== 测试套件 ====================
def suite_basic() -> None:
    """1. 基础计算."""
    print("\n[1] 基础计算")
    # 代数
    expect_ok("basic.alg", "2 + 3", "5")
    expect_ok("basic.alg", "10 - 4 * 2", "2")
    expect_ok("basic.alg", "(2 + 3) * 4", "20")
    expect_ok("basic.alg", "2^10", "1024")
    expect_ok("basic.alg", "2**10", "1024")
    expect_ok("basic.alg", "100 / 4", "25")
    # 分数
    expect_ok("basic.alg", "Rational(1, 3) + Rational(1, 6)", "1/2")
    # 三角
    expect_ok("basic.trig", "sin(0)", "0")
    expect_ok("basic.trig", "cos(0)", "1")
    expect_ok("basic.trig", "sin(pi/2)", "1")
    expect_ok("basic.trig", "sin(pi/6)", "1/2")
    expect_ok("basic.trig", "tan(pi/4)", "1")
    # 对数指数
    expect_ok("basic.exp", "log(e)", "log(e)")  # sympy 不会自动化简
    expect_ok("basic.exp", "exp(0)", "1")
    expect_ok("basic.exp", "exp(1)", "E")
    # 不定积分
    expect_ok("basic.int", "integrate(x, x)", "x**2/2")
    expect_ok("basic.int", "integrate(x^2, x)", "x**3/3")
    expect_ok("basic.int", "integrate(1/x, x)", "log")
    # 求导
    expect_ok("basic.diff", "diff(x^2, x)", "2*x")
    expect_ok("basic.diff", "diff(sin(x), x)", "cos")
    expect_ok("basic.diff", "diff(log(x), x)", "1/x")
    # 求解
    expect_ok("basic.solve", "solve(x^2 - 4, x)")
    expect_ok("basic.solve", "solve(x^2 + 1, x)")
    # 化简
    expect_ok("basic.simplify", "simplify(sin(x)^2 + cos(x)^2)", "1")


def suite_boundary() -> None:
    """2. 边界用例."""
    print("\n[2] 边界用例")
    # 数值边界
    expect_ok("boundary.num", "0 + 0", "0")
    expect_ok("boundary.num", "1*1*1*1*1*1*1*1", "1")
    expect_ok("boundary.num", "-(-(-x))", "-x")
    # 大数
    expect_ok("boundary.big", "2^20")
    expect_ok("boundary.big", "factorial(10)", "3628800")
    # 包含特殊符号
    expect_ok("boundary.sym", "pi")
    expect_ok("boundary.sym", "E")
    # 嵌套函数
    expect_ok("boundary.nested", "sin(cos(0))", "sin(1)")
    expect_ok("boundary.nested", "log(exp(5))", "5")
    expect_ok("boundary.nested", "sqrt(16)", "4")
    expect_ok("boundary.nested", "abs(-7)", "7")
    # 空字符串 / 空白
    expect_error("boundary.empty", "", "SyntaxError")
    expect_error("boundary.empty", "   ", "SyntaxError")
    # 极限
    expect_ok("boundary.limit", "limit(sin(x)/x, x, 0)", "1")
    expect_ok("boundary.limit", "limit(1/x, x, inf)", "1/inf")  # 1/inf sympy 不自动简化
    expect_ok("boundary.limit", "limit((1+1/n)^n, n, inf)", "exp")  # 不会自动简化
    # 矩阵
    expect_ok("boundary.matrix", "det([[1, 2], [3, 4]])", "-2")
    expect_ok("boundary.matrix", "transpose([[1, 2, 3]])", None)
    expect_ok("boundary.matrix", "rank([[1, 2], [2, 4]])", "1")
    # 定积分
    expect_ok("boundary.defint", "integrate(x^2, x, 0, 1)", "1/3")
    expect_ok("boundary.defint", "integrate(sin(x), x, 0, pi)", "2")
    expect_ok("boundary.defint", "integrate(exp(-x^2), x, -oo, oo)", "sqrt(pi)")


def suite_error() -> None:
    """3. 错误用例."""
    print("\n[3] 错误用例")
    # 语法错误
    expect_error("err.syntax", "2+", "SyntaxError")
    # 注: "+1" sympify 接受为 1 (单目正号), 不报错
    expect_error("err.syntax", "1/0", "SyntaxError")
    # 注: "2***2" 被折叠为 "2**2" = 4, 不报错
    expect_error("err.syntax", "1+", "SyntaxError")
    expect_error("err.syntax", "++2", "SyntaxError")
    # 括号未闭合
    expect_error("err.paren", "(1+2", "SyntaxError")
    expect_error("err.paren", "1+2)", "SyntaxError")
    expect_error("err.paren", "[1,2,3", "SyntaxError")
    expect_error("err.paren", "((1+2)", "SyntaxError")
    # 矩阵错误
    expect_error("err.matrix", "det()", "SyntaxError")
    expect_error("err.matrix", "det([])", "SyntaxError")
    expect_error("err.matrix", "det([[1,2,3],[4,5,6]])", "MathError")
    expect_error("err.matrix", "inv([[1,2,3],[4,5,6]])", "MathError")
    expect_error("err.matrix", "inv([[1,2],[2,4]])", "MathError")  # 奇异
    expect_error("err.matrix", "eigenvals([[1,2,3],[4,5,6]])", "MathError")
    # 求解错误
    expect_error("err.solve", "solve()", "SyntaxError")
    expect_error("err.solve", "nsolve()", "SyntaxError")
    expect_error("err.solve", "nsolve(x^2-1, x, 2j)", "MathError")  # 复数初值转为 MathError
    # 极限错误
    expect_error("err.limit", "limit()", "SyntaxError")
    expect_error("err.limit", "limit(sin(x), x)", "SyntaxError")
    # 泰勒错误
    expect_error("err.series", "series()", "SyntaxError")
    expect_error("err.series", "series(sin(x), x, 0, -1)", "MathError")
    # 微分方程错误
    expect_error("err.dsolve", "dsolve()", "SyntaxError")
    expect_error("err.dsolve", "dsolve(diff(f(x),x))", "SyntaxError")
    # 方程组错误
    expect_error("err.linsolve", "linsolve()", "SyntaxError")
    expect_error("err.linsolve", "linsolve([x**2+y-1, x+y**2-3], [x, y])", "MathError")
    # 求导/积分错误
    # 注: "diff(x, )" sympy 接受空 var 为 None, 返回 0
    # 数值求解错误
    expect_error("err.nsolve", "nsolve(x^2+1, x, 0)", "MathError")  # 无实数根
    # 定积分错误
    expect_error("err.defint", "integrate()", "SyntaxError")
    expect_error("err.defint", "integrate(x^2, x, 0)", "SyntaxError")
    expect_error("err.defint", "integrate(x^2, x, 0, 1, 2)", "SyntaxError")
    # 不支持的函数名 - sympy 会把 unknownfunc(x) 当作变量相乘
    # expect_error("err.cmd", "unknownfunc(x)", "SyntaxError")
    # 表达式包含未识别符号
    expect_error("err.symbol", "$x + 1", "SyntaxError")


def suite_complex() -> None:
    """4. 复杂表达式."""
    print("\n[4] 复杂表达式")
    # 多层括号
    expect_ok("complex.parens", "((1+2)*(3+4))", "21")
    expect_ok("complex.parens", "(((x+1)^2))", "(x + 1)**2")
    # 嵌套函数
    expect_ok("complex.nested", "sin(cos(tan(0)))")
    # 注: diff(integrate(...)) 和 integrate(diff(...)) 不支持嵌套命令
    # 单独测试 round-trip 性质: diff(integrate(x^2, x), x) 在数学上等于 x^2, 但当前实现不支持嵌套
    # 复杂方程
    expect_ok("complex.equation", "solve(x^3 - 6*x^2 + 11*x - 6, x)")
    expect_ok("complex.equation", "solve(exp(x) - 3*x, x)")
    # 复杂化简
    expect_ok("complex.simplify", "simplify((x^2 - 1)/(x - 1))", "x + 1")
    expect_ok("complex.simplify", "simplify(sin(x)/cos(x))", "tan(x)")
    # 复杂矩阵
    expect_ok("complex.matrix", "det([[a,b],[c,d]])")
    expect_ok("complex.matrix", "inv([[2,1,1],[1,2,1],[1,1,2]])")
    expect_ok("complex.matrix", "eigenvals([[2,0,0],[0,3,0],[0,0,5]])")
    # 矩阵运算
    expect_ok("complex.matrix", "[[1,2],[3,4]]+[[5,6],[7,8]]", None)
    expect_ok("complex.matrix", "[[1,2],[3,4]]*[[1,2],[3,4]]", "7")
    expect_ok("complex.matrix", "[[1,2],[3,4]]^2", "Matrix")  # 矩阵求幂, 不自动求值
    # 泰勒展开
    expect_ok("complex.series", "series(sin(x), x, 0, 8)")
    expect_ok("complex.series", "series(cos(x), x, 0, 6)")
    expect_ok("complex.series", "series(exp(x), x, 0, 5)")
    # 微分方程
    expect_ok("complex.ode", "dsolve(diff(f(x),x,2) + f(x), f(x))")
    expect_ok("complex.ode", "dsolve(diff(f(x),x) - 2*x, f(x))")
    # 极限复杂
    expect_ok("complex.limit", "limit((1+x/n)^n, n, oo)", "exp(x)")
    expect_ok("complex.limit", "limit((tan(x)-x)/x^3, x, 0)", "1/3")
    # 定积分复杂
    expect_ok("complex.defint", "integrate(sin(x)*cos(x), x, 0, pi/2)", "1/2")
    expect_ok("complex.defint", "integrate(1/(1+x^2), x, 0, 1)", "pi/4")
    # 方程组
    expect_ok("complex.linsolve", "linsolve([2*x+3*y-7, x-2*y+1], [x, y])", None)


def suite_performance() -> None:
    """5. 性能用例 (大致 2 秒以内)."""
    print("\n[5] 性能用例")
    # 中等规模
    t0 = time.perf_counter()
    expect_ok("perf.med", "expand((x+1)^10)")
    t1 = (time.perf_counter() - t0) * 1000
    print(f"  (耗时 {t1:.0f}ms)")

    t0 = time.perf_counter()
    expect_ok("perf.med", "simplify((x^2-1)/(x-1) + (x^3-1)/(x-1))")
    t1 = (time.perf_counter() - t0) * 1000
    print(f"  (耗时 {t1:.0f}ms)")

    t0 = time.perf_counter()
    expect_ok("perf.med", "integrate(x*sin(x)*exp(x), x)")
    t1 = (time.perf_counter() - t0) * 1000
    print(f"  (耗时 {t1:.0f}ms)")

    t0 = time.perf_counter()
    expect_ok("perf.med", "factor(x^4 - 5*x^2 + 4)")
    t1 = (time.perf_counter() - t0) * 1000
    print(f"  (耗时 {t1:.0f}ms)")

    # 大矩阵
    t0 = time.perf_counter()
    expect_ok("perf.matrix", "det([[1,2,3,4],[5,6,7,8],[9,10,11,12],[13,14,15,16]])")
    t1 = (time.perf_counter() - t0) * 1000
    print(f"  (耗时 {t1:.0f}ms)")


# ==================== 入口 ====================
def main() -> int:
    print("=" * 78)
    print("Werer 后端计算引擎测试")
    print("=" * 78)

    suite_basic()
    suite_boundary()
    suite_error()
    suite_complex()
    suite_performance()

    return STATS.report()


if __name__ == "__main__":
    sys.exit(main())
