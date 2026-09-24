package com.wwwaker.warer.core.formula.convert

import com.wwwaker.warer.core.formula.model.BigOp
import com.wwwaker.warer.core.formula.model.BigOpKind
import com.wwwaker.warer.core.formula.model.Delim
import com.wwwaker.warer.core.formula.model.DelimKind
import com.wwwaker.warer.core.formula.model.Frac
import com.wwwaker.warer.core.formula.model.Func
import com.wwwaker.warer.core.formula.model.Node
import com.wwwaker.warer.core.formula.model.Num
import com.wwwaker.warer.core.formula.model.Row
import com.wwwaker.warer.core.formula.model.Script
import com.wwwaker.warer.core.formula.model.Sqrt
import com.wwwaker.warer.core.formula.model.Sym
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 公式 AST → 计算命令的编译测试。
 *
 * 这是编辑器与计算引擎之间的契约，必须逐条锁定：
 * 一旦编译结果变化，引擎侧的行为就会跟着变。
 */
class CommandBuilderTest {

    private fun t(s: String) = Num(s)
    private fun v(c: Char) = Sym(c)
    private fun rowOf(vararg nodes: Node) = Row(nodes.toList())
    private fun compile(node: Node) = CommandBuilder.compile(node)

    // ============================ 叶节点与行 ============================

    @Test
    fun `leaves map to their own text`() {
        assertEquals("12", compile(t("12")).command)
        assertEquals("x", compile(v('x')).command)
        assertEquals("sin", compile(Func("sin")).command)
    }

    @Test
    fun `row concatenates children in order`() {
        assertEquals("2+3", compile(rowOf(t("2"), v('+'), t("3"))).command)
    }

    @Test
    fun `empty formula compiles to an empty command`() {
        assertEquals("", compile(Row()).command)
        assertFalse(compile(Row()).hasWarnings)
    }

    // ============================ 结构节点 ============================

    @Test
    fun `fraction compiles with parenthesised division`() {
        assertEquals(
            "(1)/(2)",
            compile(Frac(Row(listOf(t("1"))), Row(listOf(t("2"))))).command
        )
    }

    @Test
    fun `superscript compiles to power`() {
        assertEquals(
            "x^(2)",
            compile(Script(Row(listOf(v('x'))), superscript = Row(listOf(t("2"))))).command
        )
    }

    @Test
    fun `complex base is parenthesised so precedence is preserved`() {
        val node = Script(
            Row(listOf(Func("sin"), v('('), v('x'), v(')'))),
            superscript = Row(listOf(t("2")))
        )
        assertEquals("(sin(x))^(2)", compile(node).command)
    }

    @Test
    fun `subscript is dropped with an explicit warning`() {
        val result = compile(
            Script(Row(listOf(v('x'))), subscript = Row(listOf(v('n'))))
        )
        assertEquals("x", result.command)
        assertTrue(result.hasWarnings)
    }

    @Test
    fun `square root compiles to sqrt`() {
        assertEquals("sqrt(2)", compile(Sqrt(Row(listOf(t("2"))))).command)
    }

    @Test
    fun `nth root compiles to an equivalent power`() {
        assertEquals(
            "(8)^(1/(3))",
            compile(Sqrt(Row(listOf(t("8"))), index = Row(listOf(t("3"))))).command
        )
    }

    @Test
    fun `delimiters map to the matching engine construct`() {
        assertEquals(
            "(x+1)",
            compile(Delim(DelimKind.PAREN, Row(listOf(v('x'), v('+'), t("1"))), DelimKind.PAREN)).command
        )
        assertEquals(
            "abs(x)",
            compile(Delim(DelimKind.ABS, Row(listOf(v('x'))), DelimKind.ABS)).command
        )
        assertEquals(
            "floor(x)",
            compile(Delim(DelimKind.FLOOR, Row(listOf(v('x'))), DelimKind.FLOOR)).command
        )
    }

    // ============================ 积分（算符吞噬 + 微分记号） ============================

    /** `∫_0^1 sin(x)dx` */
    private fun integralSin(): Node = rowOf(
        BigOp(BigOpKind.INTEGRAL, lower = Row(listOf(t("0"))), upper = Row(listOf(t("1")))),
        Func("sin"), v('('), v('x'), v(')'), v('d'), v('x')
    )

    @Test
    fun `definite integral swallows the rest of the row and reads the differential`() {
        val result = compile(integralSin())
        assertEquals("integrate(sin(x), x, 0, 1)", result.command)
        assertFalse(result.hasWarnings)
    }

    @Test
    fun `integrand stops before the differential`() {
        // ∫_0^1 x^2 dx 中的 dx 不应进入被积函数
        val node = rowOf(
            BigOp(BigOpKind.INTEGRAL, lower = Row(listOf(t("0"))), upper = Row(listOf(t("1")))),
            v('x'), v('^'), t("2"), v(' '), v('d'), v('x')
        )
        assertEquals("integrate(x^2, x, 0, 1)", compile(node).command)
    }

    @Test
    fun `differential with a space still gets recognised`() {
        val node = rowOf(
            BigOp(BigOpKind.INTEGRAL, lower = Row(listOf(t("0"))), upper = Row(listOf(t("1")))),
            v('t'), v('d'), v(' '), v('t')
        )
        assertEquals("integrate(t, t, 0, 1)", compile(node).command)
    }

    @Test
    fun `integral without limits becomes indefinite`() {
        val node = rowOf(BigOp(BigOpKind.INTEGRAL), v('x'), v('^'), t("2"))
        val result = compile(node)
        assertEquals("integrate(x^2, x)", result.command)
        assertFalse(result.hasWarnings)
    }

    @Test
    fun `integral missing one limit is reported`() {
        val node = rowOf(
            BigOp(BigOpKind.INTEGRAL, lower = Row(listOf(t("0")))),
            v('x'), v('d'), v('x')
        )
        val result = compile(node)
        assertEquals("integrate(x, x)", result.command)
        assertTrue(result.hasWarnings)
    }

    @Test
    fun `empty integrand counts as one`() {
        val node = rowOf(BigOp(BigOpKind.INTEGRAL))
        assertEquals("integrate(1, x)", compile(node).command)
    }

    // ============================ 极限 ============================

    @Test
    fun `limit reads its target from the lower slot`() {
        val node = rowOf(
            BigOp(BigOpKind.LIM, lower = Row(listOf(v('x'), v('→'), t("0")))),
            Func("sin"), v('('), v('x'), v(')'), v('/'), v('x')
        )
        val result = compile(node)
        assertEquals("limit(sin(x)/x, x, 0)", result.command)
        assertFalse(result.hasWarnings)
    }

    @Test
    fun `limit without a target is reported`() {
        val node = rowOf(BigOp(BigOpKind.LIM, lower = Row(listOf(v('x')))), t("1"))
        val result = compile(node)
        assertEquals("limit(1, x, 0)", result.command)
        assertTrue(result.hasWarnings)
    }

    // ============================ 不支持的算符 ============================

    @Test
    fun `summation is flagged as unsupported but still readable`() {
        val node = rowOf(
            BigOp(BigOpKind.SUM, lower = Row(listOf(t("0"))), upper = Row(listOf(v('n')))),
            v('x')
        )
        val result = compile(node)
        assertTrue(result.hasWarnings)
        assertEquals("∑_(0)^(n)(x)", result.command)
    }

    @Test
    fun `a lone big operator is reported`() {
        val result = compile(BigOp(BigOpKind.INTEGRAL))
        assertTrue(result.hasWarnings)
    }

    // ============================ 组合 ============================

    @Test
    fun `commands compose through nested structures`() {
        // (1 + √2) / (x²)
        val node = Frac(
            numerator = Row(listOf(t("1"), v('+'), Sqrt(Row(listOf(t("2")))))),
            denominator = Row(listOf(Script(Row(listOf(v('x'))), superscript = Row(listOf(t("2"))))))
        )
        assertEquals("(1+sqrt(2))/(x^(2))", compile(node).command)
    }

    @Test
    fun `duplicate warnings are reported only once`() {
        val node = rowOf(
            Script(Row(listOf(v('a'))), subscript = Row(listOf(t("1")))),
            Script(Row(listOf(v('b'))), subscript = Row(listOf(t("2"))))
        )
        assertEquals(1, compile(node).warnings.size)
    }
}
