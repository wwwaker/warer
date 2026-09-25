package com.wwwaker.warer.core.formula.convert

import com.wwwaker.warer.core.formula.model.Delim
import com.wwwaker.warer.core.formula.model.DelimKind
import com.wwwaker.warer.core.formula.model.Frac
import com.wwwaker.warer.core.formula.model.Func
import com.wwwaker.warer.core.formula.model.Num
import com.wwwaker.warer.core.formula.model.Row
import com.wwwaker.warer.core.formula.model.Script
import com.wwwaker.warer.core.formula.model.Sqrt
import com.wwwaker.warer.core.formula.model.Sym
import com.wwwaker.warer.core.formula.model.toPlainText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LaTeX → AST 的测试。
 *
 * **用例全部取自后端实测输出**（本地直跑 `SympyEngine.process_expression` 得到，
 * 见会话记录里的 47 条探针）。这样保证转换器覆盖的是真实形态，而不是想象的 LaTeX。
 */
class LatexToAstTest {

    private fun text(latex: String): String = LatexToAst.parse(latex).toPlainText()

    // ============================ 结构断言 ============================

    @Test
    fun `plain integer`() {
        assertEquals(Row(listOf(Num("4"))), LatexToAst.parse("4"))
    }

    @Test
    fun `decimal from nsolve keeps full precision`() {
        assertEquals(Row(listOf(Num("1.4142135623731"))), LatexToAst.parse("1.4142135623731"))
    }

    @Test
    fun `fraction becomes a Frac node`() {
        assertEquals(
            Row(listOf(Frac(Row(listOf(Num("1"))), Row(listOf(Num("3")))))),
            LatexToAst.parse("\\frac{1}{3}")
        )
    }

    @Test
    fun `square root becomes a Sqrt node`() {
        assertEquals(
            Row(listOf(Sqrt(Row(listOf(Sym('x')))))),
            LatexToAst.parse("\\sqrt{x}")
        )
    }

    @Test
    fun `superscript becomes a Script node`() {
        assertEquals(
            Row(listOf(Script(base = Row(listOf(Sym('x'))), superscript = Row(listOf(Num("2")))))),
            LatexToAst.parse("x^{2}")
        )
    }

    @Test
    fun `subscript becomes a Script node`() {
        assertEquals(
            Row(listOf(Script(base = Row(listOf(Sym('C'))), subscript = Row(listOf(Num("1")))))),
            LatexToAst.parse("C_{1}")
        )
    }

    // ============================ 实测输出（求导 / 积分） ============================

    @Test
    fun `derivative of x squared`() {
        assertEquals("2 x", text("2 x"))
    }

    @Test
    fun `product rule result keeps the juxtaposition spaces`() {
        assertEquals(
            "x cos(x) + sin(x)",
            text("x \\cos{\\left(x \\right)} + \\sin{\\left(x \\right)}")
        )
    }

    @Test
    fun `negative reciprocal keeps the fraction`() {
        assertEquals("- (1)/(x^(2))", text("- \\frac{1}{x^{2}}"))
    }

    @Test
    fun `derivative of sqrt x has a nested sqrt in the denominator`() {
        assertEquals("(1)/(2 sqrt(x))", text("\\frac{1}{2 \\sqrt{x}}"))
    }

    @Test
    fun `indefinite integral of x squared`() {
        assertEquals("(x^(3))/(3)", text("\\frac{x^{3}}{3}"))
    }

    @Test
    fun `integral producing erf uses operatorname`() {
        assertEquals(
            "(sqrt(π) erf(x))/(2)",
            text("\\frac{\\sqrt{\\pi} \\operatorname{erf}{\\left(x \\right)}}{2}")
        )
    }

    @Test
    fun `definite integral of gaussian`() {
        assertEquals("sqrt(π)", text("\\sqrt{\\pi}"))
    }

    @Test
    fun `implicit multiplication in a polynomial`() {
        assertEquals(
            "2 x + cos(x)",
            text("2 x + \\cos{\\left(x \\right)}")
        )
    }

    // ============================ 实测输出（解方程 / 序列） ============================

    @Test
    fun `solution list becomes a bracketed row`() {
        assertEquals("[-2, 2]", text("\\left[ -2, \\  2\\right]"))
    }

    @Test
    fun `single solution list`() {
        assertEquals("[-1]", text("\\left[ -1\\right]"))
    }

    @Test
    fun `empty solution set`() {
        assertEquals(Row(listOf(Sym('∅'))), LatexToAst.parse("\\emptyset"))
    }

    @Test
    fun `imaginary unit is kept as a symbol`() {
        assertEquals("[- i, i]", text("\\left[ - i, \\  i\\right]"))
    }

    @Test
    fun `eigenvalue dictionary becomes a brace delimited row`() {
        // 注：断言结构而不是 toPlainText —— toPlainText 把 BRACE 兜底成圆括号，
        // 而渲染真正依赖的是 DelimKind.BRACE。
        val row = LatexToAst.parse("\\left\\{ 2 : 1, \\  3 : 1, \\  5 : 1\\right\\}")
        val delim = row.children.single() as Delim
        assertEquals(DelimKind.BRACE, delim.left)
        assertEquals(DelimKind.BRACE, delim.right)
        assertEquals("2 : 1, 3 : 1, 5 : 1", delim.content.toPlainText())
    }

    @Test
    fun `linsolve solution tuple nests correctly`() {
        val row = LatexToAst.parse(
            "\\left\\{\\left( \\frac{11}{7}, \\  \\frac{9}{7}\\right)\\right\\}"
        )
        val outer = row.children.single() as Delim
        assertEquals(DelimKind.BRACE, outer.left)

        val inner = outer.content.children.single() as Delim
        assertEquals(DelimKind.PAREN, inner.left)
        assertEquals("(11)/(7), (9)/(7)", inner.content.toPlainText())
    }

    // ============================ 实测输出（级数 / 微分方程 / 极限） ============================

    @Test
    fun `series with big-O remainder`() {
        assertEquals(
            "x - (x^(3))/(6) + (x^(5))/(120) + O(x^(6))",
            text("x - \\frac{x^{3}}{6} + \\frac{x^{5}}{120} + O\\left(x^{6}\\right)")
        )
    }

    @Test
    fun `dsolve equation keeps the equals sign and constants`() {
        assertEquals(
            "f(x) = C_(1) sin(x) + C_(2) cos(x)",
            text("f{\\left(x \\right)} = C_{1} \\sin{\\left(x \\right)} + C_{2} \\cos{\\left(x \\right)}")
        )
    }

    @Test
    fun `infinity limit`() {
        assertEquals(Row(listOf(Sym('∞'))), LatexToAst.parse("\\infty"))
    }

    @Test
    fun `absolute value uses an absolute delimiter`() {
        assertEquals("abs(x)", text("\\left|{x}\\right|"))
    }

    @Test
    fun `log of e from sympy`() {
        assertEquals("log(e)", text("\\log{\\left(e \\right)}"))
    }

    @Test
    fun `greek letters are mapped to glyphs`() {
        assertEquals("θ + π", text("\\theta + \\pi"))
    }

    // ============================ 不支持的形态 ============================

    @Test
    fun `matrix latex is reported as unsupported`() {
        val matrix = "\\left[\\begin{matrix}1 & 2\\\\3 & 4\\end{matrix}\\right]"
        assertFalse(LatexToAst.isSupported(matrix))
    }

    @Test
    fun `parsing a matrix throws so the caller can degrade to plain text`() {
        val matrix = "\\left[\\begin{matrix}1 & 2\\\\3 & 4\\end{matrix}\\right]"
        var threw = false
        try {
            LatexToAst.parse(matrix)
        } catch (_: UnsupportedLatexException) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun `unknown command is rejected instead of being silently dropped`() {
        var threw = false
        try {
            LatexToAst.parse("\\weirdcmd{x}")
        } catch (_: UnsupportedLatexException) {
            threw = true
        }
        assertTrue(threw)
    }

    // ============================ 括号种类 ============================

    @Test
    fun `all delimiter kinds observed in practice map correctly`() {
        assertEquals(DelimKind.PAREN, firstDelim("\\left( x \\right)").left)
        assertEquals(DelimKind.BRACKET, firstDelim("\\left[ x \\right]").left)
        assertEquals(DelimKind.ABS, firstDelim("\\left| x \\right|").left)
        assertEquals(DelimKind.BRACE, firstDelim("\\left\\{ x \\right\\}").left)
        assertEquals(DelimKind.NORM, firstDelim("\\left\\| x \\right\\|").left)
    }

    private fun firstDelim(latex: String): Delim {
        val row = LatexToAst.parse(latex)
        return row.children.first() as Delim
    }

    // ============================ 函数 ============================

    @Test
    fun `function name is a Func node followed by its parenthesised argument`() {
        val row = LatexToAst.parse("\\cos{\\left(x \\right)}")
        assertEquals(Func("cos"), row.children[0])
        assertTrue(row.children[1] is Row)
    }
}
