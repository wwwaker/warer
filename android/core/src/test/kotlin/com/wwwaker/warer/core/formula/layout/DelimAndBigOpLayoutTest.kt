package com.wwwaker.warer.core.formula.layout

import com.wwwaker.warer.core.formula.model.BigOp
import com.wwwaker.warer.core.formula.model.BigOpKind
import com.wwwaker.warer.core.formula.model.Delim
import com.wwwaker.warer.core.formula.model.DelimKind
import com.wwwaker.warer.core.formula.model.Frac
import com.wwwaker.warer.core.formula.model.Node
import com.wwwaker.warer.core.formula.model.Num
import com.wwwaker.warer.core.formula.model.Row
import com.wwwaker.warer.core.formula.model.Sym
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 伸缩括号与大算符的布局测试。
 */
class DelimAndBigOpLayoutTest {

    private val ctx = LayoutContext(FakeFontMetrics(), 1.0)
    private val fm = FakeFontMetrics()

    private fun rowOf(vararg nodes: Node) = Row(nodes.toList())

    private fun LayoutResult.texts() = commands.filterIsInstance<DrawCmd.Text>()
    private fun LayoutResult.polylines() = commands.filterIsInstance<DrawCmd.Polyline>()

    /** 单行内容，高度恰好 1em，低于伸缩阈值。 */
    private val shortContent = Row(listOf(Num("1"), Sym('+'), Num("2")))

    /** 含分式的内容，高度约 2.25em，高于伸缩阈值。 */
    private val tallContent = Row(listOf(Frac(Row(listOf(Num("1"))), Row(listOf(Num("2"))))))

    // ============================ 括号 ============================

    @Test
    fun `short content keeps using font glyphs`() {
        val r = FormulaLayout.layoutFormula(
            Delim(DelimKind.PAREN, shortContent, DelimKind.PAREN), ctx
        )
        assertTrue("矮内容不应走矢量路径", r.polylines().isEmpty())
        assertEquals(listOf("(", "1", "+", "2", ")"), r.texts().map { it.value })
    }

    @Test
    fun `tall content switches to two stretchy paths`() {
        val r = FormulaLayout.layoutFormula(
            Delim(DelimKind.PAREN, tallContent, DelimKind.PAREN), ctx
        )
        assertEquals(2, r.polylines().size)
        assertTrue(
            "伸缩时不应再出现括号字形",
            r.texts().none { it.value == "(" || it.value == ")" }
        )
    }

    @Test
    fun `stretchy delimiters span the padded content height`() {
        val content = FormulaLayout.measure(tallContent, ctx)
        val r = FormulaLayout.layoutFormula(
            Delim(DelimKind.PAREN, tallContent, DelimKind.PAREN), ctx
        )
        assertEquals(content.ascent + LayoutConstants.DELIM_PADDING, r.ascent, 1e-9)
        assertEquals(content.descent + LayoutConstants.DELIM_PADDING, r.descent, 1e-9)

        val ys = r.polylines().first().points.map { it.y }
        assertEquals(-r.ascent, ys.min(), 1e-9)
        assertEquals(r.descent, ys.max(), 1e-9)
    }

    @Test
    fun `left and right parentheses bulge outwards`() {
        val r = FormulaLayout.layoutFormula(
            Delim(DelimKind.PAREN, tallContent, DelimKind.PAREN), ctx
        )
        val left = r.polylines()[0].points
        val right = r.polylines()[1].points
        assertTrue("左括号应向左凸", left.first().x > left[left.size / 2].x)
        assertTrue("右括号应向右凸", right.first().x < right[right.size / 2].x)
    }

    @Test
    fun `content starts right after the left delimiter`() {
        val r = FormulaLayout.layoutFormula(
            Delim(DelimKind.PAREN, shortContent, DelimKind.PAREN), ctx
        )
        val open = r.texts().first { it.value == "(" }
        val one = r.texts().first { it.value == "1" }
        assertEquals(fm.width("("), one.x - open.x, 1e-9)
    }

    @Test
    fun `none kind occupies no horizontal space`() {
        val oneSided = FormulaLayout.measure(Delim(DelimKind.NONE, shortContent, DelimKind.PAREN), ctx)
        val bothSides = FormulaLayout.measure(Delim(DelimKind.PAREN, shortContent, DelimKind.PAREN), ctx)
        assertEquals(bothSides.width - fm.width("("), oneSided.width, 1e-9)
    }

    @Test
    fun `absolute value uses vertical bars`() {
        val r = FormulaLayout.layoutFormula(
            Delim(DelimKind.ABS, tallContent, DelimKind.ABS), ctx
        )
        assertEquals(2, r.polylines().size)
        val ys = r.polylines().first().points.map { it.y }
        assertEquals(-r.ascent, ys.min(), 1e-9)
        assertEquals(r.descent, ys.max(), 1e-9)
    }

    @Test
    fun `norm uses two vertical bars on each side`() {
        val r = FormulaLayout.layoutFormula(
            Delim(DelimKind.NORM, tallContent, DelimKind.NORM), ctx
        )
        assertEquals("两侧各两条竖线", 4, r.polylines().size)
    }

    // ============================ 大算符 ============================

    @Test
    fun `sum places limits above and below the operator`() {
        val r = FormulaLayout.layoutFormula(
            BigOp(BigOpKind.SUM, lower = Row(listOf(Num("0"))), upper = Row(listOf(Sym('n')))),
            ctx
        )
        val op = r.texts().first { it.value == "∑" }
        val upper = r.texts().first { it.value == "n" }
        val lower = r.texts().first { it.value == "0" }

        assertEquals("算符本身应在基线上", 0.0, op.baselineY, 1e-9)
        assertTrue("上限在算符上方", upper.baselineY < op.baselineY)
        assertTrue("下限在算符下方", lower.baselineY > op.baselineY)
    }

    @Test
    fun `above-below limits are centred on the operator`() {
        val r = FormulaLayout.layoutFormula(
            BigOp(BigOpKind.SUM, lower = Row(listOf(Num("12345"))), upper = Row(listOf(Sym('n')))),
            ctx
        )
        val op = r.texts().first { it.value == "∑" }
        val lower = r.texts().first { it.value == "12345" }
        val opCentre = op.x + fm.width("∑") * BigOpKind.SUM.scale / 2
        val lowerCentre = lower.x + fm.width("12345") * LayoutConstants.BIGOP_LIMIT_SCALE / 2
        assertEquals(opCentre, lowerCentre, 1e-9)
    }

    @Test
    fun `sum width is the widest of operator and limits`() {
        val m = FormulaLayout.measure(
            BigOp(BigOpKind.SUM, lower = Row(listOf(Num("12345"))), upper = Row(listOf(Sym('n')))),
            ctx
        )
        assertEquals(5 * fm.width("1") * LayoutConstants.BIGOP_LIMIT_SCALE, m.width, 1e-9)
    }

    @Test
    fun `integral places limits to the right`() {
        val r = FormulaLayout.layoutFormula(
            BigOp(BigOpKind.INTEGRAL, lower = Row(listOf(Num("0"))), upper = Row(listOf(Num("1")))),
            ctx
        )
        val op = r.texts().first { it.value == "∫" }
        val upper = r.texts().first { it.value == "1" }
        val lower = r.texts().first { it.value == "0" }

        assertTrue("∫ 的上下限应在其右侧", upper.x > op.x && lower.x > op.x)
        assertTrue(upper.baselineY < op.baselineY)
        assertTrue(lower.baselineY > op.baselineY)
        assertEquals("上下限左对齐", upper.x, lower.x, 1e-9)
    }

    @Test
    fun `integral sign is enlarged`() {
        val r = FormulaLayout.layoutFormula(BigOp(BigOpKind.INTEGRAL), ctx)
        assertEquals(BigOpKind.INTEGRAL.scale, r.texts().single().scale, 1e-9)
    }

    @Test
    fun `lim keeps normal size and puts its limit underneath`() {
        val r = FormulaLayout.layoutFormula(
            BigOp(BigOpKind.LIM, lower = Row(listOf(Num("0")))), ctx
        )
        val op = r.texts().first { it.value == "lim" }
        val lower = r.texts().first { it.value == "0" }

        assertEquals("lim 不应被放大", 1.0, op.scale, 1e-9)
        assertTrue("下限应在下方", lower.baselineY > op.baselineY)

        val opCentre = op.x + fm.width("lim") / 2
        val lowerCentre = lower.x + fm.width("0") * LayoutConstants.BIGOP_LIMIT_SCALE / 2
        assertEquals(opCentre, lowerCentre, 1e-9)
    }

    @Test
    fun `big operator composes with a following operand`() {
        val node = Row(
            listOf(
                BigOp(BigOpKind.INTEGRAL, lower = Row(listOf(Num("0"))), upper = Row(listOf(Num("1")))),
                Sym(' '),
                Num("sin"), Sym('('), Sym('x'), Sym(')'),
                Sym('d'), Sym('x')
            )
        )
        val r = FormulaLayout.layoutFormula(node, ctx)
        assertTrue(r.width > 0.0)
        assertTrue(r.commands.isNotEmpty())
        assertTrue("积分号应明显高于普通文本", r.ascent > fm.ascent)
    }
}
