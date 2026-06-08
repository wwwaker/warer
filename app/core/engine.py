import re
import time
import concurrent.futures
from typing import Any

import sympy
from sympy.parsing.sympy_parser import parse_expr, standard_transformations, implicit_multiplication_application
from sympy.matrices.common import NonSquareMatrixError, NonInvertibleMatrixError

from app.models.schemas import ComputeResult, ErrorDetail

COMPUTE_TIMEOUT = 30  # seconds

# Types that don't have .free_symbols
_SCALAR_TYPES = (int, float, complex, sympy.Integer, sympy.Float, sympy.Rational)


def _friendly_math_error(exc: Exception) -> ErrorDetail:
    """Map SymPy math errors to user-friendly Chinese messages."""
    err_name = type(exc).__name__
    msg = str(exc) or err_name

    # 矩阵相关
    if 'NonSquareMatrixError' in err_name or 'DMNonSquareMatrixError' in err_name:
        return ErrorDetail(
            type="MathError",
            message=f"该矩阵操作要求方阵（非方阵无法执行）。提示：转置(T)和求秩(rank)可用于任何矩阵。",
        )
    if 'NonInvertibleMatrixError' in err_name or 'not invertible' in msg.lower() or 'det == 0' in msg:
        return ErrorDetail(
            type="MathError",
            message=f"矩阵不可逆（行列式为 0）。提示：奇异矩阵没有逆矩阵，请检查输入。",
        )
    if 'ShapeError' in err_name or 'shape' in msg.lower():
        return ErrorDetail(
            type="MathError",
            message=f"矩阵/数组维度不匹配：{msg[:80]}",
        )

    # nsolve 数值求解
    if 'Could not find root' in msg or "within given tolerance" in msg:
        return ErrorDetail(
            type="MathError",
            message="在给定初值附近未找到根。提示：尝试不同的初始猜测值，或先用 solve 找近似解。",
        )
    if 'Cannot convert expression to float' in msg or "to float" in msg:
        return ErrorDetail(
            type="MathError",
            message=f"nsolve 初值必须是数字，无法转换为浮点数：{msg[:60]}",
        )
    if 'iteration' in msg.lower() or 'diverged' in msg.lower():
        return ErrorDetail(
            type="MathError",
            message="数值迭代不收敛。提示：尝试调整初值或简化表达式。",
        )

    # linsolve 非线性
    if 'NonlinearError' in err_name or 'nonlinear' in msg.lower():
        return ErrorDetail(
            type="MathError",
            message=f"linsolve 仅支持线性方程组。提示：非线性方程组请用 solve。",
        )

    # series/taylor
    if 'Number of terms should be nonnegative' in msg or 'nonnegative' in msg.lower():
        return ErrorDetail(
            type="MathError",
            message="series/taylor 阶数必须是非负整数。",
        )

    # dsolve
    if 'IndexError' in err_name:
        return ErrorDetail(
            type="MathError",
            message=f"dsolve 解析失败：方程格式可能不正确。{msg[:60]}",
        )

    # limit
    if 'does not converge' in msg.lower() or 'diverges' in msg.lower():
        return ErrorDetail(
            type="MathError",
            message="极限不存在或发散。",
        )

    # division by zero
    if 'division by zero' in msg.lower() or 'zerodivision' in err_name.lower():
        return ErrorDetail(
            type="MathError",
            message="除数不能为 0。",
        )

    return None


def _validate_brackets(expr: str) -> str | None:
    """Validate bracket matching, return error message or None."""
    stack: list[tuple[str, int]] = []
    pairs = {')': '(', ']': '[', '}': '{'}
    for i, ch in enumerate(expr):
        if ch in '([{':
            stack.append((ch, i))
        elif ch in ')]}':
            if not stack or stack[-1][0] != pairs[ch]:
                opener = stack[-1][0] if stack else None
                opener_name = {'(': '圆括号', '[': '方括号', '{': '花括号'}.get(opener or '', '?')
                close_name = {')': '圆括号', ']': '方括号', '}': '花括号'}[ch]
                if not stack:
                    return f"第 {i+1} 字符处多了 {close_name} 闭合"
                return f"第 {i+1} 字符处 {close_name} 不匹配：期望 {opener_name} 闭合"
            stack.pop()
    if stack:
        ch, pos = stack[-1]
        ch_name = {'(': '圆括号', '[': '方括号', '{': '花括号'}[ch]
        return f"第 {pos+1} 字符处的 {ch_name} 未闭合"
    return None


def _pre_check_expr(expr: str) -> str | None:
    """Pre-validate the expression before dispatch, return user-friendly error or None."""
    if not expr.strip():
        return "表达式不能为空"
    bracket_err = _validate_brackets(expr)
    if bracket_err:
        return f"括号不匹配：{bracket_err}"
    # 检查末尾是否有未完成的运算符
    stripped = expr.rstrip()
    if stripped and stripped[-1] in '+-*/^,':
        return f"表达式不完整：末尾出现了未完成的运算符 '{stripped[-1]}'。请补全表达式"
    # 检查连续的运算符 (比如 ++, --, **+ 等)
    # 先把 ** 和 *** 排除掉 (它们是幂运算, 由 _preprocess 处理)
    tmp = re.sub(r'\*{2,}', '', stripped)
    dup_match = re.search(r'[+\-*/^]{2,}', tmp)
    if dup_match:
        return f"运算符重复：'{dup_match.group()}'。请检查表达式"
    # 检查除数为零
    if re.search(r'/\s*0(?![.\d])', stripped):
        return "除数不能为 0。提示：请检查表达式中是否有除以 0 的运算"
    return None


class SympyEngine:
    _TRANSFORMATIONS = standard_transformations + (implicit_multiplication_application,)

    _CMD_PATTERN = re.compile(
        r'^(diff|derivative|int(?:egrate)?|solve|nsolve|dsolve|linsolve|limit|series|taylor)\s*\(', re.IGNORECASE
    )
    _MATRIX_CMD_PATTERN = re.compile(
        r'^(det|inv|transpose|eigenvals|eigenvects|rank|inverse)\s*\(', re.IGNORECASE
    )
    _SIMPLIFY_KEYWORDS = re.compile(r'^simplify\s*\(\s*(.+)\s*\)$', re.IGNORECASE | re.DOTALL)
    _MATRIX_LITERAL = re.compile(r'^\s*\[\[.+\]\]\s*$', re.DOTALL)

    @classmethod
    def _preprocess(cls, expr: str) -> str:
        expr = re.sub(r'^[yY]\s*=\s*', '', expr)
        expr = re.sub(r'\^', '**', expr)
        # 折叠 ***  -> ** (e.g. 2***2 -> 2**2; the third * is treated as implicit multiply)
        expr = re.sub(r'\*{3,}', '**', expr)
        expr = re.sub(r'\bln\b', 'log', expr)
        expr = re.sub(r'(\d)([a-zA-Z(])', r'\1*\2', expr)
        expr = re.sub(r'(\))(\d)', r'\1**\2', expr)
        expr = re.sub(r'(\))([a-zA-Z(])', r'\1*\2', expr)
        # 处理 sin(x)^2 -> (sin(x))^2
        expr = re.sub(r'(\w+\([^)]*\))\^(\d+)', r'(\1)**\2', expr)
        return expr

    @classmethod
    def _split_command_args(cls, expr: str) -> tuple[str, str] | None:
        m = cls._CMD_PATTERN.match(expr)
        if not m:
            return None

        cmd = m.group(1).lower()
        inner_start = m.end()
        depth = 1
        i = inner_start
        last_comma = -1

        while i < len(expr) and depth > 0:
            ch = expr[i]
            if ch == '(':
                depth += 1
            elif ch == ')':
                depth -= 1
            elif ch == ',' and depth == 1:
                last_comma = i
            i += 1

        if depth != 0 or last_comma == -1:
            return None

        inner_expr = expr[inner_start:last_comma].strip()
        var = expr[last_comma + 1:i - 1].strip()

        op_type = 'integrate' if cmd.startswith('int') else 'diff'
        return inner_expr, var

    @classmethod
    def process_expression(cls, expr: str) -> tuple[ComputeResult | None, ErrorDetail | None, float]:
        start = time.perf_counter()

        # Pre-validation
        expr = expr.strip()
        pre_err = _pre_check_expr(expr)
        if pre_err:
            return None, ErrorDetail(type="SyntaxError", message=pre_err), 0.0

        try:
            result_expr, operation = cls._dispatch_with_timeout(expr)
            elapsed = (time.perf_counter() - start) * 1000

            # Handle list results (e.g. from solve)
            if isinstance(result_expr, (list, tuple)):
                if len(result_expr) == 0:
                    latex_display = r'\emptyset'
                    plain_text = 'no solutions'
                    is_symbolic = False
                    variables = []
                    numeric_approx = None
                else:
                    latex_display = sympy.latex(result_expr)
                    plain_text = str(result_expr)
                    is_symbolic = any(
                        getattr(s, 'free_symbols', set()) != set()
                        for s in result_expr
                    )
                    variables = sorted(
                        str(s) for sol in result_expr if hasattr(sol, 'free_symbols')
                        for s in sol.free_symbols
                    )
                    numeric_approx = str([sol.evalf(n=15) for sol in result_expr]) if is_symbolic else None
            # Handle dict results (e.g. from eigenvals, which returns {eigenvalue: multiplicity})
            elif isinstance(result_expr, dict):
                latex_display = sympy.latex(result_expr)
                plain_text = str(result_expr)
                is_symbolic = any(
                    getattr(k, 'free_symbols', set()) != set()
                    for k in result_expr.keys()
                )
                variables = sorted(
                    str(s) for k in result_expr.keys() if hasattr(k, 'free_symbols')
                    for s in k.free_symbols
                )
                numeric_approx = None
                if is_symbolic:
                    try:
                        numeric_approx = str({k.evalf(n=15): v for k, v in result_expr.items()})
                    except Exception:
                        pass
            else:
                latex_display = sympy.latex(result_expr)
                plain_text = str(result_expr)

                # Handle scalar types (int, float, etc.) that don't have .free_symbols
                if isinstance(result_expr, _SCALAR_TYPES):
                    is_symbolic = False
                    variables = []
                    numeric_approx = None
                else:
                    is_symbolic = getattr(result_expr, 'free_symbols', set()) != set()
                    variables = sorted(str(s) for s in getattr(result_expr, 'free_symbols', set()))

                    numeric_approx = None
                    if is_symbolic:
                        try:
                            numeric_approx = str(result_expr.evalf(n=15))
                        except Exception:
                            pass

            compute_result = ComputeResult(
                is_symbolic=is_symbolic,
                main_display=latex_display,
                plain_text=plain_text,
                numeric_approximation=numeric_approx,
                variables=variables,
            )

            return compute_result, None, elapsed

        except sympy.SympifyError as e:
            elapsed = (time.perf_counter() - start) * 1000
            return None, ErrorDetail(
                type="SyntaxError",
                message=f"无法解析表达式：语法可能不完整或包含未识别符号。提示：检查括号、运算符和变量名。",
            ), elapsed

        except SyntaxError as e:
            elapsed = (time.perf_counter() - start) * 1000
            return None, ErrorDetail(
                type="SyntaxError",
                message=str(e) if str(e) else "语法错误",
            ), elapsed

        except Exception as e:
            elapsed = (time.perf_counter() - start) * 1000
            err_name = type(e).__name__
            msg = str(e) or err_name

            # 1. Try friendly math error mapping
            friendly = _friendly_math_error(e)
            if friendly:
                return None, friendly, elapsed

            # 2. Common patterns
            msg_lower = msg.lower()
            if 'unclosed' in msg_lower or 'unexpected eof' in msg_lower:
                return None, ErrorDetail(
                    type="SyntaxError",
                    message="括号未闭合或语法不完整",
                ), elapsed
            if 'invalid syntax' in msg_lower or 'could not parse' in msg_lower or "unexpected token" in msg_lower:
                return None, ErrorDetail(
                    type="SyntaxError",
                    message="无法解析表达式：检查括号配对、运算符和变量名是否正确",
                ), elapsed

            # 3. Fall back to internal error
            return None, ErrorDetail(
                type="InternalServerError",
                message=f"计算过程中发生错误 ({err_name})：{msg[:120]}",
            ), elapsed

    @classmethod
    def _handle_solve(cls, expr: str) -> tuple[Any, str]:
        """Handle solve(expr) and solve(expr, var)."""
        m = re.match(r'^solve\s*\(', expr, re.IGNORECASE)
        inner_start = m.end()
        depth = 1
        i = inner_start
        last_comma = -1

        while i < len(expr) and depth > 0:
            ch = expr[i]
            if ch == '(':
                depth += 1
            elif ch == ')':
                depth -= 1
            elif ch == ',' and depth == 1:
                last_comma = i
            i += 1

        if last_comma == -1:
            inner_expr = expr[inner_start:i - 1].strip()
            var = None
        else:
            inner_expr = expr[inner_start:last_comma].strip()
            var = expr[last_comma + 1:i - 1].strip()

        preprocessed = cls._preprocess(inner_expr)
        parsed = cls._parse(preprocessed)

        if var:
            var_sym = sympy.Symbol(var)
            solutions = sympy.solve(parsed, var_sym)
        else:
            solutions = sympy.solve(parsed)

        return solutions, 'solve'

    @classmethod
    def _handle_nsolve(cls, expr: str) -> tuple[Any, str]:
        m = re.match(r'^nsolve\s*\(', expr, re.IGNORECASE)
        inner_start = m.end()
        depth = 1
        i = inner_start
        commas = []

        while i < len(expr) and depth > 0:
            ch = expr[i]
            if ch == '(':
                depth += 1
            elif ch == ')':
                depth -= 1
            elif ch == ',' and depth == 1:
                commas.append(i)
            i += 1

        if len(commas) < 2:
            raise SyntaxError("nsolve 需要 3 个参数: nsolve(expr, var, guess)，例如 nsolve(x^5-x-1, x, 1)")

        first_comma = commas[0]
        second_comma = commas[1]

        inner_expr = expr[inner_start:first_comma].strip()
        var = expr[first_comma + 1:second_comma].strip()
        guess_str = expr[second_comma + 1:i - 1].strip()

        preprocessed = cls._preprocess(inner_expr)
        parsed = cls._parse(preprocessed)
        var_sym = sympy.Symbol(var)

        try:
            guess_val = float(guess_str)
        except ValueError:
            guess_val = float(sympy.sympify(guess_str))

        result = sympy.nsolve(parsed, var_sym, guess_val)
        return result, 'nsolve'

    @classmethod
    def _handle_dsolve(cls, expr: str) -> tuple[Any, str]:
        m = re.match(r'^dsolve\s*\(', expr, re.IGNORECASE)
        inner_start = m.end()
        depth = 1
        i = inner_start
        commas = []

        while i < len(expr) and depth > 0:
            ch = expr[i]
            if ch == '(':
                depth += 1
            elif ch == ')':
                depth -= 1
            elif ch == ',' and depth == 1:
                commas.append(i)
            i += 1

        if len(commas) < 1:
            raise SyntaxError("dsolve 需要参数: dsolve(eq, f(x))，例如 dsolve(diff(f(x), x) - f(x), f(x))")

        first_comma = commas[0]
        eq_str = expr[inner_start:first_comma].strip()

        if len(commas) >= 2:
            second_comma = commas[1]
            func_str = expr[first_comma + 1:second_comma].strip()
        else:
            func_str = expr[first_comma + 1:i - 1].strip()

        eq_str = eq_str.replace('^', '**')
        eq_parsed = cls._parse_strict(eq_str)

        func_sym = sympy.sympify(func_str)
        result = sympy.dsolve(eq_parsed, func_sym)
        return result, 'dsolve'

    @classmethod
    def _handle_linsolve(cls, expr: str) -> tuple[Any, str]:
        m = re.match(r'^linsolve\s*\(', expr, re.IGNORECASE)
        inner_start = m.end()
        depth = 1
        bracket_depth = 0
        i = inner_start
        commas = []

        while i < len(expr) and depth > 0:
            ch = expr[i]
            if ch == '(':
                depth += 1
            elif ch == ')':
                depth -= 1
            elif ch == '[':
                bracket_depth += 1
            elif ch == ']':
                bracket_depth -= 1
            elif ch == ',' and depth == 1 and bracket_depth == 0:
                commas.append(i)
            i += 1

        if len(commas) < 1:
            raise SyntaxError("linsolve 需要参数: linsolve([eq1, eq2, ...], [x, y, ...])，例如 linsolve([x+y-1, x-y-3], [x, y])")

        first_comma = commas[0]
        eqs_str = expr[inner_start:first_comma].strip()
        vars_str = expr[first_comma + 1:i - 1].strip()

        if not (eqs_str.startswith('[') and eqs_str.endswith(']')):
            raise SyntaxError("linsolve 的方程列表需用方括号包裹，例如 linsolve([x+y-1, x-y-3], [x, y])")
        if not (vars_str.startswith('[') and vars_str.endswith(']')):
            raise SyntaxError("linsolve 的变量列表需用方括号包裹，例如 linsolve([x+y-1, x-y-3], [x, y])")

        eqs_inner = eqs_str[1:-1].strip()
        vars_inner = vars_str[1:-1].strip()

        if not eqs_inner:
            raise SyntaxError("linsolve 的方程列表不能为空，例如 linsolve([x+y-1], [x, y])")
        if not vars_inner:
            raise SyntaxError("linsolve 的变量列表不能为空，例如 linsolve([x+y-1], [x, y])")

        eq_strs = [e.strip() for e in eqs_inner.split(',') if e.strip()]
        if not eq_strs:
            raise SyntaxError("linsolve 的方程列表不能为空，例如 linsolve([x+y-1], [x, y])")

        parsed_eqs = []
        for idx, eq_s in enumerate(eq_strs, 1):
            try:
                eq_s = eq_s.replace('^', '**')
                parsed_eqs.append(sympy.sympify(eq_s))
            except Exception as e:
                raise SyntaxError(f"linsolve 第 {idx} 个方程无法解析 '{eq_s}': {e}")

        var_names = [v.strip() for v in vars_inner.split(',') if v.strip()]
        if not var_names:
            raise SyntaxError("linsolve 的变量列表不能为空")
        var_syms = [sympy.Symbol(v) for v in var_names]

        result = sympy.linsolve(parsed_eqs, var_syms)
        return result, 'linsolve'

    @classmethod
    def _handle_limit(cls, expr: str) -> tuple[Any, str]:
        m = re.match(r'^limit\s*\(', expr, re.IGNORECASE)
        inner_start = m.end()
        depth = 1
        i = inner_start
        commas = []

        while i < len(expr) and depth > 0:
            ch = expr[i]
            if ch == '(':
                depth += 1
            elif ch == ')':
                depth -= 1
            elif ch == ',' and depth == 1:
                commas.append(i)
            i += 1

        if len(commas) < 2:
            raise SyntaxError("limit 需要 3 个参数: limit(expr, var, point)，例如 limit(sin(x)/x, x, 0)")

        first_comma = commas[0]
        second_comma = commas[1]

        inner_expr = expr[inner_start:first_comma].strip()
        var = expr[first_comma + 1:second_comma].strip()
        rest = expr[second_comma + 1:i - 1].strip()

        # Optional 4th argument: direction '+' or '-'
        direction = None
        if ',' in rest:
            # Split on the last top-level comma
            extra_comma = rest.rfind(',')
            point_str = rest[:extra_comma].strip()
            direction = rest[extra_comma + 1:].strip().strip("'\"")
        else:
            point_str = rest

        if not point_str:
            raise SyntaxError("limit 缺少极限点，例如 limit(sin(x)/x, x, 0)")

        preprocessed = cls._preprocess(inner_expr)
        parsed = cls._parse(preprocessed)
        var_sym = sympy.Symbol(var)
        point = sympy.sympify(point_str)

        if direction in ('+', '-'):
            result = sympy.limit(parsed, var_sym, point, direction)
        else:
            result = sympy.limit(parsed, var_sym, point)
        return result, 'limit'

    @classmethod
    def _handle_series(cls, expr: str) -> tuple[Any, str]:
        m = re.match(r'^(?:series|taylor)\s*\(', expr, re.IGNORECASE)
        inner_start = m.end()
        depth = 1
        i = inner_start
        commas = []

        while i < len(expr) and depth > 0:
            ch = expr[i]
            if ch == '(':
                depth += 1
            elif ch == ')':
                depth -= 1
            elif ch == ',' and depth == 1:
                commas.append(i)
            i += 1

        if len(commas) < 2:
            raise SyntaxError("series/taylor 需要 3-4 个参数: series(expr, var, point, n)，例如 series(sin(x), x, 0, 5)")

        first_comma = commas[0]
        second_comma = commas[1]

        inner_expr = expr[inner_start:first_comma].strip()
        var = expr[first_comma + 1:second_comma].strip()

        if len(commas) >= 3:
            third_comma = commas[2]
            point_str = expr[second_comma + 1:third_comma].strip()
            n_str = expr[third_comma + 1:i - 1].strip()
        else:
            rest = expr[second_comma + 1:i - 1].strip()
            point_str = '0'
            n_str = rest

        preprocessed = cls._preprocess(inner_expr)
        parsed = cls._parse(preprocessed)
        var_sym = sympy.Symbol(var)
        point = sympy.sympify(point_str)

        try:
            n = int(n_str)
        except ValueError:
            n = int(sympy.sympify(n_str))

        result = sympy.series(parsed, var_sym, point, n)
        return result, 'series'

    @classmethod
    def _handle_definite_integral(cls, expr: str) -> tuple[Any, str]:
        """Definite integral: integrate(expr, var, lower, upper).
        Examples:
            integrate(x^2, x, 0, 1) -> 1/3
            integrate(sin(x), x, 0, pi) -> 2
            integrate(1/x, x, 1, inf) -> oo (improper integral)
        """
        m = re.match(r'^integrate\s*\(', expr, re.IGNORECASE)
        inner_start = m.end()
        depth = 1
        i = inner_start
        commas: list[int] = []

        while i < len(expr) and depth > 0:
            ch = expr[i]
            if ch == '(':
                depth += 1
            elif ch == ')':
                depth -= 1
            elif ch == ',' and depth == 1:
                commas.append(i)
            i += 1

        if depth != 0:
            raise SyntaxError("integrate 的括号未闭合")

        if len(commas) < 3:
            raise SyntaxError(
                "定积分需要 4 个参数: integrate(被积函数, 变量, 下限, 上限)，"
                "例如 integrate(x^2, x, 0, 1)。不定积分用 integrate(expr, var)"
            )
        if len(commas) > 3:
            raise SyntaxError(
                "定积分只能有 4 个参数: integrate(被积函数, 变量, 下限, 上限)。"
                f"当前提供了 {len(commas) + 1} 个参数"
            )

        c1, c2, c3 = commas[0], commas[1], commas[2]
        inner_expr = expr[inner_start:c1].strip()
        var_str = expr[c1 + 1:c2].strip()
        lower_str = expr[c2 + 1:c3].strip()
        upper_str = expr[c3 + 1:i - 1].strip()

        if not inner_expr:
            raise SyntaxError("integrate 的被积函数不能为空，例如 integrate(x^2, x, 0, 1)")
        if not var_str:
            raise SyntaxError("integrate 的变量不能为空，例如 integrate(x^2, x, 0, 1)")
        if not lower_str:
            raise SyntaxError("integrate 的积分下限不能为空，例如 integrate(x^2, x, 0, 1)")
        if not upper_str:
            raise SyntaxError("integrate 的积分上限不能为空，例如 integrate(x^2, x, 0, 1)")

        preprocessed = cls._preprocess(inner_expr)
        parsed = cls._parse(preprocessed)
        var_sym = sympy.Symbol(var_str)
        lower = sympy.sympify(lower_str)
        upper = sympy.sympify(upper_str)

        # Use sympy.integrate for definite integral
        result = sympy.integrate(parsed, (var_sym, lower, upper))
        return result, 'definite_integrate'

    @classmethod
    def _has_nested_command(cls, inner_expr: str) -> bool:
        return bool(re.match(cls._CMD_PATTERN, inner_expr.strip()))

    @classmethod
    def _parse_matrix_arg(cls, expr: str, inner_start: int, close_idx: int) -> sympy.Matrix:
        """Parse the inner argument of a matrix command and convert to sympy.Matrix."""
        inner_expr = expr[inner_start:close_idx].strip()

        if not inner_expr:
            raise SyntaxError("矩阵操作需要参数，例如 det([[1,2],[3,4]])。提示：矩阵字面量需用 [[...]] 包裹。")

        # Reject obviously empty input
        if inner_expr in ('[]', '[[]]', '[ ]', '[ [ ] ]', '[ [] ]'):
            raise SyntaxError("矩阵不能为空，例如 [[1, 2], [3, 4]]")

        # If it's a nested matrix literal like [[1,2],[3,4]]
        if cls._MATRIX_LITERAL.match(inner_expr):
            return cls._parse_matrix_literal(inner_expr)

        # Detect 1D list - must have 2D form for matrix
        if inner_expr.startswith('[') and inner_expr.endswith(']') and not inner_expr.startswith('[['):
            raise SyntaxError(
                f"矩阵字面量需要二维形式，但 '{inner_expr[:30]}' 是一维。"
                f"请使用 [[行1], [行2]] 形式，例如 [[1,2],[3,4]]"
            )

        # If it's a nested command call like inv([[1,2],[3,4]]), evaluate it
        nested_matrix = re.match(r'^(det|inv|inverse|transpose|eigenvals|eigenvects|rank)\s*\(', inner_expr, re.IGNORECASE)
        if nested_matrix:
            nested_result, _ = cls._handle_matrix_op(inner_expr)
            if isinstance(nested_result, sympy.Matrix):
                return nested_result
            raise SyntaxError(f"嵌套矩阵操作 '{inner_expr}' 未返回矩阵")

        # If it's a symbol/variable reference
        preprocessed = cls._preprocess(inner_expr)
        try:
            parsed = cls._parse(preprocessed)
        except Exception as e:
            raise SyntaxError(f"矩阵参数 '{inner_expr}' 解析失败：{e}")

        if isinstance(parsed, sympy.Matrix):
            return parsed
        if parsed.is_Matrix:
            return parsed
        raise SyntaxError(
            f"矩阵操作需要矩阵作为参数，但得到的是标量/表达式：'{inner_expr}'。"
            f"提示：直接使用矩阵字面量如 [[1,2],[3,4]]，或先用变量名引用已定义的矩阵。"
        )

    @classmethod
    def _parse_matrix_literal(cls, expr: str) -> sympy.Matrix:
        """Parse [[a,b],[c,d]] style matrix literal into sympy.Matrix."""
        preprocessed = cls._preprocess(expr)
        try:
            parsed = cls._parse(preprocessed)
        except Exception as e:
            raise SyntaxError(f"矩阵字面量 '{expr[:40]}...' 解析失败：{e}")

        if isinstance(parsed, sympy.Matrix):
            if parsed.shape == (0, 0):
                raise SyntaxError("矩阵不能为空，至少需要一个元素，例如 [[1, 2], [3, 4]]")
            return parsed
        # parse_expr with evaluate=False may return a nested list
        if isinstance(parsed, list):
            if len(parsed) == 0:
                raise SyntaxError("矩阵不能为空，例如 [[1, 2], [3, 4]]")
            try:
                mat = sympy.Matrix(parsed)
            except Exception as e:
                raise SyntaxError(
                    f"矩阵字面量格式错误：'{expr[:40]}'，请使用 [[行1], [行2]] 形式（例如 [[1, 2], [3, 4]]）"
                )
            if mat.shape == (0, 0):
                raise SyntaxError("矩阵不能为空，例如 [[1, 2], [3, 4]]")
            return mat
        try:
            mat = sympy.Matrix(parsed)
            if mat.shape == (0, 0):
                raise SyntaxError("矩阵不能为空，例如 [[1, 2], [3, 4]]")
            return mat
        except Exception as e:
            raise SyntaxError(f"无法将 '{expr[:40]}...' 转换为矩阵：{e}")

    @classmethod
    def _handle_matrix_op(cls, expr: str) -> tuple[Any, str]:
        """Handle matrix operations: det, inv, transpose, eigenvals, eigenvects, rank."""
        m = cls._MATRIX_CMD_PATTERN.match(expr)
        cmd = m.group(1).lower()

        inner_start = m.end()
        depth = 1
        bracket_depth = 0
        i = inner_start

        while i < len(expr) and depth > 0:
            ch = expr[i]
            if ch == '(':
                depth += 1
            elif ch == ')':
                depth -= 1
            elif ch == '[':
                bracket_depth += 1
            elif ch == ']':
                bracket_depth -= 1
            elif ch == ',' and depth == 1 and bracket_depth == 0:
                # 矩阵操作只接受一个参数，遇到逗号表示多参数错误
                raise SyntaxError(
                    f"{cmd} 只接受一个矩阵参数，例如 {cmd}([[1,2],[3,4]])"
                )
            i += 1

        if depth != 0:
            raise SyntaxError(f"{cmd} 的括号未闭合")

        close_idx = i - 1
        mat = cls._parse_matrix_arg(expr, inner_start, close_idx)

        if not mat:
            raise SyntaxError(f"{cmd} 的参数为空")

        if cmd in ('det',):
            if mat.shape[0] != mat.shape[1]:
                raise NonSquareMatrixError(f"det 需要方阵，但当前是 {mat.shape[0]}x{mat.shape[1]}")
            result = mat.det()
            op_name = 'det'
        elif cmd in ('inv', 'inverse'):
            if mat.shape[0] != mat.shape[1]:
                raise NonSquareMatrixError(f"inv 需要方阵，但当前是 {mat.shape[0]}x{mat.shape[1]}")
            result = mat.inv()
            op_name = 'inv'
        elif cmd == 'transpose':
            result = mat.T
            op_name = 'transpose'
        elif cmd == 'eigenvals':
            if mat.shape[0] != mat.shape[1]:
                raise NonSquareMatrixError(f"eigenvals 需要方阵，但当前是 {mat.shape[0]}x{mat.shape[1]}")
            result = mat.eigenvals()
            op_name = 'eigenvals'
        elif cmd == 'eigenvects':
            if mat.shape[0] != mat.shape[1]:
                raise NonSquareMatrixError(f"eigenvects 需要方阵，但当前是 {mat.shape[0]}x{mat.shape[1]}")
            result = mat.eigenvects()
            op_name = 'eigenvects'
        elif cmd == 'rank':
            result = mat.rank()
            op_name = 'rank'
        else:
            raise SyntaxError(f"未知的矩阵操作: {cmd}")

        return result, op_name

    @classmethod
    def _dispatch_with_timeout(cls, expr: str) -> tuple[Any, str]:
        with concurrent.futures.ThreadPoolExecutor(max_workers=1) as executor:
            future = executor.submit(cls._dispatch, expr)
            try:
                return future.result(timeout=COMPUTE_TIMEOUT)
            except concurrent.futures.TimeoutError:
                raise TimeoutError(f"计算超时（超过 {COMPUTE_TIMEOUT} 秒），请简化表达式")

    @classmethod
    def _try_matrix_expression(cls, expr: str) -> tuple[Any, str] | None:
        """Try to parse expr as a matrix arithmetic expression (A*B, A+B, A-B, A^-1, etc.).
        Returns (result, op_name) or None if expr is not a matrix expression.
        """
        # Quick check: must contain at least one [[ pattern
        if '[[' not in expr:
            return None

        # Tokenize respecting bracket depth. We support: matrix literal, identifier, +, -, *, /, ^, (, )
        tokens: list[tuple[str, str]] = []
        i = 0
        n = len(expr)
        while i < n:
            ch = expr[i]
            if ch.isspace():
                i += 1
                continue
            # Matrix literal [[...]]
            if ch == '[':
                depth = 0
                start = i
                while i < n:
                    if expr[i] == '[':
                        depth += 1
                    elif expr[i] == ']':
                        depth -= 1
                        if depth == 0:
                            i += 1
                            break
                    i += 1
                if depth != 0:
                    return None
                tokens.append(('matrix', expr[start:i]))
                continue
            # Identifier
            if ch.isalpha() or ch == '_':
                j = i
                while j < n and (expr[j].isalnum() or expr[j] == '_'):
                    j += 1
                tokens.append(('id', expr[i:j]))
                i = j
                continue
            # Number
            if ch.isdigit() or ch == '.':
                j = i
                dot_seen = False
                while j < n and (expr[j].isdigit() or (expr[j] == '.' and not dot_seen)):
                    if expr[j] == '.':
                        dot_seen = True
                    j += 1
                tokens.append(('num', expr[i:j]))
                i = j
                continue
            # Operators
            if ch in '+-*/^()':
                tokens.append(('op', ch))
                i += 1
                continue
            return None

        # First pass: parse all matrix literals to sympy Matrix objects
        mat_objects: list[Any] = []
        processed: list[tuple[str, Any]] = []
        for t_type, t_val in tokens:
            if t_type == 'matrix':
                try:
                    preprocessed = cls._preprocess(t_val)
                    parsed = cls._parse(preprocessed)
                    if isinstance(parsed, list):
                        m = sympy.Matrix(parsed)
                    elif isinstance(parsed, sympy.Matrix):
                        m = parsed
                    else:
                        return None
                    mat_objects.append(m)
                    processed.append(('matrix', m))
                except Exception:
                    return None
            elif t_type == 'num':
                processed.append(('num', sympy.sympify(t_val)))
            elif t_type == 'id':
                # An id with no matrix context is a scalar symbol
                processed.append(('num', sympy.Symbol(t_val)))
            else:
                processed.append(('op', t_val))

        # Now do a proper recursive descent parser that handles matrix multiplication correctly
        # We use MatMul for * between matrices, and ordinary Mul otherwise.
        from sympy import MatMul, Add, Mul, Pow

        def parse_primary(processed: list[tuple[str, Any]], pos: int) -> tuple[Any, int]:
            """Parse a primary (atom or parenthesized expression)."""
            if pos >= len(processed):
                raise ValueError("Unexpected end")
            t_type, t_val = processed[pos]
            if t_type == 'op' and t_val == '(':
                inner, new_pos = parse_expr(processed, pos + 1)
                if new_pos >= len(processed) or processed[new_pos] != ('op', ')'):
                    raise ValueError("Unbalanced parens")
                return inner, new_pos + 1
            if t_type == 'matrix' or t_type == 'num':
                return t_val, pos + 1
            raise ValueError(f"Unexpected token: {t_type} {t_val}")

        def parse_unary(processed: list[tuple[str, Any]], pos: int) -> tuple[Any, int]:
            """Parse unary - and ^."""
            if pos < len(processed) and processed[pos] == ('op', '-'):
                val, new_pos = parse_unary(processed, pos + 1)
                return -val, new_pos
            if pos < len(processed) and processed[pos] == ('op', '+'):
                return parse_unary(processed, pos + 1)
            return parse_primary(processed, pos)

        def parse_power(processed: list[tuple[str, Any]], pos: int) -> tuple[Any, int]:
            """Parse base^exponent."""
            base, new_pos = parse_unary(processed, pos)
            if new_pos < len(processed) and processed[new_pos] == ('op', '^'):
                exponent, new_pos = parse_unary(processed, new_pos + 1)
                return Pow(base, exponent), new_pos
            return base, new_pos

        def parse_term(processed: list[tuple[str, Any]], pos: int) -> tuple[Any, int]:
            """Parse term: power (* or / power)* using matrix-aware multiplication."""
            left, new_pos = parse_power(processed, pos)
            while new_pos < len(processed) and processed[new_pos][0] == 'op' and processed[new_pos][1] in ('*', '/'):
                op = processed[new_pos][1]
                right, new_pos = parse_power(processed, new_pos + 1)
                # Use MatMul if either operand is a Matrix
                if op == '*':
                    if isinstance(left, sympy.Matrix) or isinstance(right, sympy.Matrix):
                        left = MatMul(left, right).doit()
                    else:
                        left = left * right
                else:  # /
                    if isinstance(right, sympy.Matrix):
                        # A / B = A * B^-1
                        left = MatMul(left, Pow(right, -1)).doit()
                    else:
                        left = left / right
            return left, new_pos

        def parse_expr(processed: list[tuple[str, Any]], pos: int) -> tuple[Any, int]:
            """Parse expression: term ((+ | -) term)*."""
            left, new_pos = parse_term(processed, pos)
            while new_pos < len(processed):
                op_t = processed[new_pos]
                if op_t != ('op', '+') and op_t != ('op', '-'):
                    break
                op = op_t[1]
                right, new_pos = parse_term(processed, new_pos + 1)
                if op == '+':
                    left = left + right
                else:
                    left = left - right
            return left, new_pos

        try:
            result, final_pos = parse_expr(processed, 0)
        except Exception:
            return None
        if final_pos != len(processed):
            return None

        if not (isinstance(result, sympy.Matrix) or (hasattr(result, 'is_Matrix') and result.is_Matrix)):
            return None
        return result, 'matrix_expr'

    @classmethod
    def _dispatch(cls, expr: str) -> tuple[Any, str]:
        # Matrix commands
        matrix_match = cls._MATRIX_CMD_PATTERN.match(expr)
        if matrix_match:
            return cls._handle_matrix_op(expr)

        # Matrix arithmetic: A*B, A+B, A-B, A^-1
        if '[' in expr:
            mat_result = cls._try_matrix_expression(expr)
            if mat_result is not None:
                return mat_result

        # Bare matrix literal: [[1,2],[3,4]]
        if cls._MATRIX_LITERAL.match(expr.strip()):
            mat = cls._parse_matrix_literal(expr)
            return mat, 'matrix'

        dsolve_match = re.match(r'^dsolve\s*\(', expr, re.IGNORECASE)
        if dsolve_match:
            return cls._handle_dsolve(expr)

        linsolve_match = re.match(r'^linsolve\s*\(', expr, re.IGNORECASE)
        if linsolve_match:
            return cls._handle_linsolve(expr)

        limit_match = re.match(r'^limit\s*\(', expr, re.IGNORECASE)
        if limit_match:
            return cls._handle_limit(expr)

        series_match = re.match(r'^(?:series|taylor)\s*\(', expr, re.IGNORECASE)
        if series_match:
            return cls._handle_series(expr)

        nsolve_match = re.match(r'^nsolve\s*\(', expr, re.IGNORECASE)
        if nsolve_match:
            return cls._handle_nsolve(expr)

        solve_match = re.match(r'^solve\s*\(', expr, re.IGNORECASE)
        if solve_match:
            return cls._handle_solve(expr)

        # Definite integral: integrate(expr, var, lower, upper) - 4-arg form
        # We dispatch on the number of top-level commas inside the parens.
        definite_match = re.match(r'^integrate\s*\(', expr, re.IGNORECASE)
        if definite_match:
            # Count top-level commas to decide between 2-arg (indefinite) and 4-arg (definite)
            depth = 1
            arg_count = 1
            saw_content = False
            for ch_idx in range(definite_match.end(), len(expr)):
                ch = expr[ch_idx]
                if ch == '(':
                    depth += 1
                    saw_content = True
                elif ch == ')':
                    depth -= 1
                    if depth == 0:
                        break
                elif ch == ',' and depth == 1:
                    arg_count += 1
                elif not ch.isspace():
                    saw_content = True
            if not saw_content:
                # Empty parens: integrate()
                raise SyntaxError(
                    "integrate 至少需要 1 个参数: integrate(expr, var) 或 integrate(expr, var, lower, upper)"
                )
            if arg_count == 3:
                # 3 args: integrate(expr, var, lower) - missing upper bound
                raise SyntaxError(
                    "定积分需要 4 个参数: integrate(被积函数, 变量, 下限, 上限)，"
                    "例如 integrate(x^2, x, 0, 1)。不定积分用 integrate(expr, var)"
                )
            if arg_count >= 4:
                return cls._handle_definite_integral(expr)
            # else: fall through to _split_command_args for the 1-/2-arg indefinite form

        split = cls._split_command_args(expr)
        if split:
            inner_expr, var = split
            if cls._has_nested_command(inner_expr):
                raise SyntaxError("暂不支持嵌套命令（如 diff(solve(...))），请分步计算")
            preprocessed = cls._preprocess(inner_expr)
            parsed = cls._parse(preprocessed)
            var_sym = sympy.Symbol(var)

            cmd = cls._CMD_PATTERN.match(expr).group(1).lower()
            if cmd.startswith('int'):
                return sympy.integrate(parsed, var_sym), 'integrate'
            else:
                return sympy.diff(parsed, var_sym), 'diff'

        m = cls._SIMPLIFY_KEYWORDS.match(expr)
        if m:
            inner_expr = cls._preprocess(m.group(1))
            parsed = cls._parse(inner_expr)
            return sympy.simplify(parsed), 'simplify'

        preprocessed = cls._preprocess(expr)
        parsed = cls._parse(preprocessed)
        simplified = sympy.simplify(parsed)
        return simplified, 'auto_simplify'

    @classmethod
    def _parse(cls, expr_str: str) -> sympy.Expr:
        try:
            return parse_expr(
                expr_str,
                transformations=cls._TRANSFORMATIONS,
                evaluate=False,
            )
        except Exception:
            return parse_expr(
                expr_str,
                transformations=cls._TRANSFORMATIONS,
                evaluate=True,
            )

    @classmethod
    def _parse_strict(cls, expr_str: str) -> sympy.Expr:
        try:
            return parse_expr(
                expr_str,
                transformations=standard_transformations,
                evaluate=False,
            )
        except Exception:
            return parse_expr(
                expr_str,
                transformations=standard_transformations,
                evaluate=True,
            )
