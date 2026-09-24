package com.wwwaker.warer.core.formula.edit

import com.wwwaker.warer.core.formula.layout.FakeFontMetrics
import com.wwwaker.warer.core.formula.layout.FormulaLayout
import com.wwwaker.warer.core.formula.layout.LayoutContext
import com.wwwaker.warer.core.formula.layout.Point2
import com.wwwaker.warer.core.formula.model.BigOp
import com.wwwaker.warer.core.formula.model.BigOpKind
import com.wwwaker.warer.core.formula.model.Frac
import com.wwwaker.warer.core.formula.model.Node
import com.wwwaker.warer.core.formula.model.Num
import com.wwwaker.warer.core.formula.model.Row
import com.wwwaker.warer.core.formula.model.Script
import com.wwwaker.warer.core.formula.model.Sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * 点击定位测试。
 *
 * 核心是一条**往返性质**：对公式里每一个插入点，把它自己的锚点坐标当作点击位置送进去，
 * 应当落回**同一个位置**。这条性质比逐条硬编码坐标强得多 ——
 * 它自动覆盖全部结构组合，且排版常量调整后依然成立。
 *
 * 注意断言的是"位置相同"而不是"光标相等"：同一位置可能存在多个逻辑上不同的插入点
 * （例如 `x²` 的根行起点与底数行起点都在 x=0），它们视觉上完全重合，无法也不应区分。
 */
class CaretHitTestTest {

    private val ctx = LayoutContext(FakeFontMetrics(), 1.0)

    private fun t(s: String) = Num(s)
    private fun rowOf(vararg nodes: Node) = Row(nodes.toList())

    /** 光标 →（锚点坐标）→ 点击 → 新光标，断言位置不变。 */
    private fun assertRoundTrip(root: Row, caret: Caret) {
        val before = locateCaret(root, caret, ctx)!!
        val hit = hitTestCaret(root, Point2(before.x, before.baselineY), ctx)!!
        val after = locateCaret(root, hit, ctx)!!

        assertEquals(
            "点击 $caret 的锚点后应落回同一位置，实际落在 $hit",
            before.x,
            after.x,
            1e-9
        )
        assertEquals(before.baselineY, after.baselineY, 1e-9)
    }

    // ============================ 往返性质 ============================

    @Test
    fun `every insert point maps back to its own position`() {
        // 覆盖嵌套：根号里的分式、分式里的根号
        val root = rowOf(
            Frac(
                numerator = Row(listOf(Sqrt(Row(listOf(t("2")))))),
                denominator = Row(listOf(t("1"), t("3")))
            )
        )

        root.collectRows().forEach { (path, row) ->
            (0..row.children.size).forEach { offset ->
                assertRoundTrip(root, Caret(path, offset))
            }
        }
    }

    @Test
    fun `round trip also holds for scripts and big operators`() {
        val root = rowOf(
            Script(Row(listOf(t("x"))), superscript = Row(listOf(t("2")))),
            BigOp(BigOpKind.SUM, lower = Row(listOf(t("0"))), upper = Row(listOf(t("n"))))
        )

        root.collectRows().forEach { (path, row) ->
            (0..row.children.size).forEach { offset ->
                assertRoundTrip(root, Caret(path, offset))
            }
        }
    }

    @Test
    fun `round trip holds for an empty root`() {
        assertRoundTrip(Row(), Caret())
    }

    // ============================ 各结构的命中 ============================

    @Test
    fun `clicking the numerator lands in the numerator and the denominator in the denominator`() {
        val root = rowOf(Frac(Row(listOf(t("1"))), Row(listOf(t("2")))))
        val numerator = locateCaret(root, Caret(listOf(CaretStep(0, 0)), 0), ctx)!!
        val denominator = locateCaret(root, Caret(listOf(CaretStep(0, 1)), 0), ctx)!!

        val hitNumerator =
            hitTestCaret(root, Point2(numerator.x, numerator.baselineY), ctx)!!
        val hitDenominator =
            hitTestCaret(root, Point2(denominator.x, denominator.baselineY), ctx)!!

        assertEquals(listOf(CaretStep(0, 0)), hitNumerator.path)
        assertEquals(listOf(CaretStep(0, 1)), hitDenominator.path)
    }

    @Test
    fun `clicking inside a radical lands in the radicand`() {
        val root = rowOf(Sqrt(Row(listOf(t("8")))))
        val radicand = locateCaret(root, Caret(listOf(CaretStep(0, 0)), 0), ctx)!!

        val hit = hitTestCaret(root, Point2(radicand.x, radicand.baselineY), ctx)!!

        assertEquals(listOf(CaretStep(0, 0)), hit.path)
    }

    @Test
    fun `clicking a summation limit lands in that limit`() {
        val root = rowOf(
            BigOp(BigOpKind.SUM, lower = Row(listOf(t("0"))), upper = Row(listOf(t("n"))))
        )
        val upper = locateCaret(root, Caret(listOf(CaretStep(0, 1)), 0), ctx)!!

        val hit = hitTestCaret(root, Point2(upper.x, upper.baselineY), ctx)!!

        assertEquals(listOf(CaretStep(0, 1)), hit.path)
    }

    // ============================ 边界情形 ============================

    @Test
    fun `clicking far to the right lands at the end of the row`() {
        val root = rowOf(t("1"), t("2"))
        val width = FormulaLayout.measure(root, ctx).width

        val hit = hitTestCaret(root, Point2(width + 3.0, 0.0), ctx)!!

        assertEquals(Caret(emptyList(), 2), hit)
    }

    @Test
    fun `clicking far to the left lands at the start of the row`() {
        val root = rowOf(t("1"), t("2"))

        val hit = hitTestCaret(root, Point2(-4.0, 0.0), ctx)!!

        assertEquals(Caret(emptyList(), 0), hit)
    }

    @Test
    fun `clicking outside the formula still returns a caret`() {
        val root = rowOf(t("1"))

        // 关键：绝不能返回 null —— "点了没反应"是最令人困惑的情况
        assertNotNull(hitTestCaret(root, Point2(-5.0, 9.0), ctx))
        assertNotNull(hitTestCaret(root, Point2(5.0, -9.0), ctx))
    }

    @Test
    fun `empty formula yields the root caret anywhere`() {
        assertEquals(Caret(), hitTestCaret(Row(), Point2(0.0, 0.0), ctx))
        assertEquals(Caret(), hitTestCaret(Row(), Point2(3.0, -4.0), ctx))
    }

    @Test
    fun `clicking between numerator and denominator stays in the outer row`() {
        // 分数线区域既不属于分子也不属于分母，应落在包含整个分式的根行上
        val root = rowOf(Frac(Row(listOf(t("1"))), Row(listOf(t("2")))))
        val layout = FormulaLayout.layoutFormula(root, ctx)

        // 基线上方 AXIS_HEIGHT 处正是分数线中心
        val hit = hitTestCaret(root, Point2(0.0, -0.25), ctx)!!

        assertEquals(emptyList<CaretStep>(), hit.path)
        assertNotNull(layout)
    }
}
