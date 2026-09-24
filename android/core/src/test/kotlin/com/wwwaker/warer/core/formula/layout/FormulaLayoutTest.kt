package com.wwwaker.warer.core.formula.layout

import com.wwwaker.warer.core.formula.model.Frac
import com.wwwaker.warer.core.formula.model.Node
import com.wwwaker.warer.core.formula.model.Num
import com.wwwaker.warer.core.formula.model.Row
import com.wwwaker.warer.core.formula.model.Script
import com.wwwaker.warer.core.formula.model.Sqrt
import com.wwwaker.warer.core.formula.model.Sym
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 布局引擎的回归测试。
 *
 * 断言方式有两条原则：
 * 1. **尺寸断言尽量引用 [LayoutConstants]** —— 这样后续调优排版常量不会让测试失效；
 * 2. **关系断言优先于数值断言** —— 例如"分子在基线上方、分母在下方"比具体数字更能表达意图。
 */
class FormulaLayoutTest {

    private val ctx = LayoutContext(FakeFontMetrics(), 1.0)
    private val fm = FakeFontMetrics()

    private fun rowOf(vararg nodes: Node) = Row(nodes.toList())

    private fun LayoutResult.texts(): List<DrawCmd.Text> =
        commands.filterIsInstance<DrawCmd.Text>()

    /** 只取被缩放过的文本（即上下标），用于与主体文本区分。 */
    private fun LayoutResult.scriptTexts(): List<DrawCmd.Text> =
        texts().filter { kotlin.math.abs(it.scale - LayoutConstants.SCRIPT_SCALE) < 1e-9 }

    // ============================ Row ============================

    @Test
    fun `row sums child widths and takes the tallest extent`() {
        val m = FormulaLayout.measure(rowOf(Num("12"), Sym('+'), Num("3")), ctx)
        assertEquals(2.0, m.width, 1e-9)
        assertEquals(fm.ascent, m.ascent, 1e-9)
        assertEquals(fm.descent, m.descent, 1e-9)
    }

    @Test
    fun `row places children left to right on one shared baseline`() {
        val r = FormulaLayout.layoutFormula(rowOf(Num("1"), Sym('+'), Num("2")), ctx)
        val t = r.texts()
        assertEquals(listOf("1", "+", "2"), t.map { it.value })
        assertEquals(listOf(0.0, 0.5, 1.0), t.map { it.x })
        assertTrue("所有子节点应共享基线", t.all { it.baselineY == 0.0 })
    }

    /**
     * 空行不是"没有内容"，而是"**等着被输入的槽位**"：它按一个字符来度量，
     * 并产出一个占位框。
     *
     * 早期版本按 0 尺寸处理，导致空分母的基线落到分数线上、光标穿进分数线里
     * （真机暴露）。这条用例锁定修正后的语义。
     */
    @Test
    fun `an empty row is a slot waiting for input`() {
        val r = FormulaLayout.layoutFormula(Row(), ctx)

        assertEquals(LayoutConstants.SLOT_MIN_WIDTH, r.width, 1e-9)
        assertEquals(fm.ascent, r.ascent, 1e-9)
        assertEquals(fm.descent, r.descent, 1e-9)

        val slot = r.commands.filterIsInstance<DrawCmd.Slot>().single()
        assertEquals(0.0, slot.x, 1e-9)
        assertEquals(LayoutConstants.SLOT_MIN_WIDTH, slot.width, 1e-9)
        // 占位框按"一个字"的高度绘制，而不是整行高度
        assertEquals(-LayoutConstants.SLOT_ASCENT, slot.y, 1e-9)
        assertEquals(
            LayoutConstants.SLOT_ASCENT + LayoutConstants.SLOT_DESCENT,
            slot.height,
            1e-9
        )
    }

    /**
     * 回归测试：空分母不能让分式的**下延变成负数**。
     *
     * 空行按 0 度量时 `den.ascent = 0`，而分母顶边本就在基线上方 0.13em 处，
     * 于是 descent = -0.13（负！）—— 分式的盒子底部跑到了基线以上，
     * 父节点据此对齐、光标据此绘制，全都错位。
     */
    @Test
    fun `an empty denominator still yields a positive descent`() {
        val m = FormulaLayout.measure(Frac(Row(listOf(Num("1"))), Row()), ctx)

        assertTrue("空分母不应让分式的下延变成负数（实际 ${m.descent}）", m.descent > 0.0)
    }

    // ============================ 分式 ============================

    @Test
    fun `fraction width is the wider of numerator and denominator plus padding`() {
        val m = FormulaLayout.measure(
            Frac(Row(listOf(Num("1"))), Row(listOf(Num("234")))),
            ctx
        )
        assertEquals(1.5 + 2 * LayoutConstants.FRACTION_SIDE_PADDING, m.width, 1e-9)
    }

    @Test
    fun `fraction bar is centred on the math axis and spans the full width`() {
        val r = FormulaLayout.layoutFormula(
            Frac(Row(listOf(Num("1"))), Row(listOf(Num("2")))), ctx
        )
        val bar = r.commands.filterIsInstance<DrawCmd.Rect>().single()
        assertEquals(0.0, bar.x, 1e-9)
        assertEquals(r.width, bar.width, 1e-9)
        assertEquals(LayoutConstants.RULE_THICKNESS, bar.height, 1e-9)
        assertEquals(-LayoutConstants.AXIS_HEIGHT, bar.y + bar.height / 2, 1e-9)
    }

    @Test
    fun `numerator sits above and denominator below the baseline`() {
        val r = FormulaLayout.layoutFormula(
            Frac(Row(listOf(Num("1"))), Row(listOf(Num("2")))), ctx
        )
        val t = r.texts()
        val numY = t.first { it.value == "1" }.baselineY
        val denY = t.first { it.value == "2" }.baselineY
        assertTrue("分子应位于基线上方", numY < 0.0)
        assertTrue("分母应位于基线下方", denY > 0.0)
        assertTrue(numY < denY)
    }

    /** 分式回归测试的公共输入：1/2。 */
    private fun fracLayout(): LayoutResult =
        FormulaLayout.layoutFormula(Frac(Row(listOf(Num("1"))), Row(listOf(Num("2")))), ctx)

    private fun LayoutResult.rule(): DrawCmd.Rect =
        commands.filterIsInstance<DrawCmd.Rect>().single()

    @Test
    fun `fraction rule is centred on the math axis`() {
        val bar = fracLayout().rule()
        assertEquals(-LayoutConstants.AXIS_HEIGHT, bar.y + bar.height / 2, 1e-9)
    }

    /**
     * 分子盒底边应恰好位于分数线上方一个 [LayoutConstants.FRACTION_GAP]。
     *
     * 断言的是**几何关系**，而不是复算实现里那个表达式 —— 后者会与实现"同错同对"，永远通过。
     */
    @Test
    fun `numerator box bottom sits one gap above the rule`() {
        val r = fracLayout()
        val bar = r.rule()
        val num = r.texts().first { it.value == "1" }

        val numeratorBoxBottom = num.baselineY + fm.descent
        assertEquals(bar.y - LayoutConstants.FRACTION_GAP, numeratorBoxBottom, 1e-9)
    }

    /**
     * 分母盒顶边应恰好位于分数线下方一个 [LayoutConstants.FRACTION_GAP]。
     *
     * 这是一条**回归测试**：分母顶边偏移曾经少了一个负号，导致分母直接被顶到分数线上；
     * 旧的断言复算了同一个错误表达式，所以没能拦住，最终是在真机截图上发现的。
     */
    @Test
    fun `denominator box top sits one gap below the rule`() {
        val r = fracLayout()
        val bar = r.rule()
        val den = r.texts().first { it.value == "2" }

        val denominatorBoxTop = den.baselineY - fm.ascent
        assertEquals(bar.y + bar.height + LayoutConstants.FRACTION_GAP, denominatorBoxTop, 1e-9)
    }

    /** measure 与 place 必须一致：盒顶就是分子盒顶，盒底就是分母盒底。 */
    @Test
    fun `measured box agrees with where children are actually placed`() {
        val r = fracLayout()
        val num = r.texts().first { it.value == "1" }
        val den = r.texts().first { it.value == "2" }

        assertEquals(r.ascent, -(num.baselineY - fm.ascent), 1e-9)
        assertEquals(r.descent, den.baselineY + fm.descent, 1e-9)
    }

    @Test
    fun `fraction children are horizontally centred in the box`() {
        val r = FormulaLayout.layoutFormula(
            Frac(Row(listOf(Num("1"))), Row(listOf(Num("234")))), ctx
        )
        val wide = r.texts().first { it.value == "234" }
        val narrow = r.texts().first { it.value == "1" }
        assertEquals((r.width - 1.5) / 2, wide.x, 1e-9)
        assertEquals((r.width - 0.5) / 2, narrow.x, 1e-9)
    }

    // ============================ 上下标 ============================

    @Test
    fun `superscript is scaled down and raised above the baseline`() {
        val r = FormulaLayout.layoutFormula(
            Script(Row(listOf(Sym('x'))), superscript = Row(listOf(Num("2")))), ctx
        )
        val sup = r.scriptTexts().single()
        assertEquals("2", sup.value)
        assertEquals(LayoutConstants.SCRIPT_SCALE, sup.scale, 1e-9)
        assertTrue("上标应位于基线上方", sup.baselineY < 0.0)
    }

    @Test
    fun `superscript top clears the base top`() {
        val r = FormulaLayout.layoutFormula(
            Script(Row(listOf(Sym('x'))), superscript = Row(listOf(Num("2")))), ctx
        )
        val sup = r.scriptTexts().single()
        val supTop = sup.baselineY - fm.ascent * LayoutConstants.SCRIPT_SCALE
        val baseTop = -fm.ascent
        assertTrue("上标顶部应高于主体顶部", supTop < baseTop)
    }

    @Test
    fun `subscript is scaled down and lowered below the baseline`() {
        val r = FormulaLayout.layoutFormula(
            Script(Row(listOf(Sym('x'))), subscript = Row(listOf(Num("1")))), ctx
        )
        val sub = r.scriptTexts().single()
        assertEquals(LayoutConstants.SCRIPT_SCALE, sub.scale, 1e-9)
        assertTrue("下标应位于基线下方", sub.baselineY > 0.0)
    }

    @Test
    fun `script sits to the right of the base`() {
        val r = FormulaLayout.layoutFormula(
            Script(Row(listOf(Sym('x'))), superscript = Row(listOf(Num("2")))), ctx
        )
        val base = r.texts().first { it.value == "x" }
        val sup = r.scriptTexts().single()
        assertTrue(sup.x >= base.x + fm.width("x") + LayoutConstants.SCRIPT_GAP - 1e-9)
    }

    @Test
    fun `taller base pushes the superscript higher`() {
        val shortBase = FormulaLayout.layoutFormula(
            Script(Row(listOf(Sym('x'))), superscript = Row(listOf(Num("2")))), ctx
        ).scriptTexts().single().baselineY

        val tallBase = FormulaLayout.layoutFormula(
            Script(
                base = Row(listOf(Frac(Row(listOf(Num("1"))), Row(listOf(Num("2")))))),
                superscript = Row(listOf(Num("3")))
            ),
            ctx
        ).scriptTexts().single().baselineY

        assertTrue("主体越高，上标抬升越多", tallBase < shortBase)
    }

    @Test
    fun `subscript and superscript can coexist`() {
        val r = FormulaLayout.layoutFormula(
            Script(
                Row(listOf(Sym('x'))),
                superscript = Row(listOf(Num("2"))),
                subscript = Row(listOf(Num("1")))
            ),
            ctx
        )
        val scripts = r.scriptTexts()
        assertEquals(2, scripts.size)
        assertTrue(scripts.any { it.value == "2" && it.baselineY < 0.0 })
        assertTrue(scripts.any { it.value == "1" && it.baselineY > 0.0 })
    }

    // ============================ 根号 ============================

    @Test
    fun `sqrt pads the radicand vertically`() {
        val r = FormulaLayout.layoutFormula(Sqrt(Row(listOf(Num("2")))), ctx)
        assertEquals(
            fm.ascent + LayoutConstants.RADICAL_PADDING + LayoutConstants.RULE_THICKNESS,
            r.ascent,
            1e-9
        )
        assertEquals(fm.descent + LayoutConstants.RADICAL_PADDING, r.descent, 1e-9)
    }

    @Test
    fun `radicand keeps the node baseline`() {
        val r = FormulaLayout.layoutFormula(Sqrt(Row(listOf(Num("2")))), ctx)
        assertEquals(0.0, r.texts().single().baselineY, 1e-9)
    }

    @Test
    fun `radical overbar spans the full width of the node`() {
        val r = FormulaLayout.layoutFormula(Sqrt(Row(listOf(Num("1234")))), ctx)
        val poly = r.commands.filterIsInstance<DrawCmd.Polyline>().single()
        assertEquals(4, poly.points.size)
        assertEquals(r.width, poly.points.last().x, 1e-9)
    }

    @Test
    fun `radical extends both above and below the baseline`() {
        val r = FormulaLayout.layoutFormula(Sqrt(Row(listOf(Num("2")))), ctx)
        val ys = r.commands.filterIsInstance<DrawCmd.Polyline>().single().points.map { it.y }
        assertTrue("根号应高于基线", ys.min() < 0.0)
        assertTrue("根号应低于基线", ys.max() > 0.0)
    }

    @Test
    fun `taller radicand yields a taller radical`() {
        val small = FormulaLayout.layoutFormula(Sqrt(Row(listOf(Num("2")))), ctx)
        val large = FormulaLayout.layoutFormula(
            Sqrt(Row(listOf(Frac(Row(listOf(Num("1"))), Row(listOf(Num("2"))))))), ctx
        )
        assertTrue(large.ascent > small.ascent)
        assertTrue(large.descent > small.descent)
    }

    @Test
    fun `nth root reserves horizontal space for the index`() {
        val plain = FormulaLayout.layoutFormula(Sqrt(Row(listOf(Num("2")))), ctx)
        val indexed = FormulaLayout.layoutFormula(
            Sqrt(Row(listOf(Num("2"))), index = Row(listOf(Num("3")))), ctx
        )
        assertTrue("带指数的根号应更宽", indexed.width > plain.width)
        assertTrue(
            "指数应以缩小字号绘制",
            indexed.texts().any {
                kotlin.math.abs(it.scale - LayoutConstants.RADICAL_INDEX_SCALE) < 1e-9
            }
        )
    }

    // ============================ 嵌套组合 ============================

    @Test
    fun `nested structures compose without error and produce a positive box`() {
        // ∫ 形式暂未实现，这里用最深的组合：分式里放根号，根号里放分式
        val node = Frac(
            numerator = Row(listOf(Sqrt(Row(listOf(Num("2")))))),
            denominator = Row(
                listOf(
                    Frac(Row(listOf(Num("1"))), Row(listOf(Num("3")))),
                    Sym('+'),
                    Sym('x')
                )
            )
        )
        val r = FormulaLayout.layoutFormula(node, ctx)
        assertTrue(r.width > 0.0)
        assertTrue(r.ascent > 0.0)
        assertTrue(r.descent > 0.0)
        assertTrue(r.commands.isNotEmpty())
    }

    /** 便于断言总高度。 */
    private fun LayoutResult.height(): Double = ascent + descent
}
