import re
import time
import concurrent.futures
from typing import Any

import sympy
from sympy.parsing.sympy_parser import parse_expr, standard_transformations, implicit_multiplication_application

from app.models.schemas import ComputeResult, ErrorDetail

COMPUTE_TIMEOUT = 30  # seconds


class SympyEngine:
    _TRANSFORMATIONS = standard_transformations + (implicit_multiplication_application,)

    _CMD_PATTERN = re.compile(
        r'^(diff|derivative|int(?:egrate)?|solve)\s*\(', re.IGNORECASE
    )
    _SIMPLIFY_KEYWORDS = re.compile(r'^simplify\s*\(\s*(.+)\s*\)$', re.IGNORECASE | re.DOTALL)

    @classmethod
    def _preprocess(cls, expr: str) -> str:
        expr = re.sub(r'^[yY]\s*=\s*', '', expr)
        expr = re.sub(r'\^', '**', expr)
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

        try:
            expr = expr.strip()
            if not expr:
                return None, ErrorDetail(type="SyntaxError", message="表达式不能为空"), 0.0

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
            else:
                latex_display = sympy.latex(result_expr)
                plain_text = str(result_expr)

                is_symbolic = result_expr.free_symbols != set()
                variables = sorted(str(s) for s in result_expr.free_symbols)

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
                type="SympifyError",
                message=f"无法解析表达式: {e}",
            ), elapsed

        except SyntaxError as e:
            elapsed = (time.perf_counter() - start) * 1000
            return None, ErrorDetail(
                type="SyntaxError",
                message=str(e),
            ), elapsed

        except Exception as e:
            elapsed = (time.perf_counter() - start) * 1000
            err_name = type(e).__name__
            msg = str(e)
            msg_lower = msg.lower()
            if 'unclosed' in msg_lower or 'unexpected' in msg_lower or 'eof' in msg_lower:
                return None, ErrorDetail(
                    type="SyntaxError",
                    message="括号未闭合或语法不完整",
                ), elapsed
            if 'invalid' in msg_lower or 'could not parse' in msg_lower:
                return None, ErrorDetail(
                    type="SyntaxError",
                    message=f"无法解析表达式",
                ), elapsed
            return None, ErrorDetail(
                type="InternalServerError",
                message=f"计算过程中发生错误: {err_name}: {msg}",
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
    def _has_nested_command(cls, inner_expr: str) -> bool:
        return bool(re.match(cls._CMD_PATTERN, inner_expr.strip()))

    @classmethod
    def _dispatch_with_timeout(cls, expr: str) -> tuple[Any, str]:
        with concurrent.futures.ThreadPoolExecutor(max_workers=1) as executor:
            future = executor.submit(cls._dispatch, expr)
            try:
                return future.result(timeout=COMPUTE_TIMEOUT)
            except concurrent.futures.TimeoutError:
                raise TimeoutError(f"计算超时（超过 {COMPUTE_TIMEOUT} 秒），请简化表达式")

    @classmethod
    def _dispatch(cls, expr: str) -> tuple[Any, str]:
        # solve can omit the variable argument, handle it separately
        solve_match = re.match(r'^solve\s*\(', expr, re.IGNORECASE)
        if solve_match:
            return cls._handle_solve(expr)

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
