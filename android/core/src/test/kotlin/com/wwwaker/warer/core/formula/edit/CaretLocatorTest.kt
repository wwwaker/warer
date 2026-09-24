package com.wwwaker.warer.core.formula.edit

import com.wwwaker.warer.core.formula.layout.DrawCmd
import com.wwwaker.warer.core.formula.layout.FakeFontMetrics
import com.wwwaker.warer.core.formula.layout.FormulaLayout
import com.wwwaker.warer.core.formula.layout.LayoutConstants
import com.wwwaker.warer.core.formula.layout.LayoutContext
import com.wwwaker.warer.core.formula.model.BigOp
import com.wwwaker.warer.core.formula.model.BigOpKind
import com.wwwaker.warer.core.formula.model.Frac
import com.wwwaker.warer.core.formula.model.Node
import com.wwwaker.warer.core.formula.model.Num
import com.wwwaker.warer.core.formula.model.Row
import com.wwwaker.warer.core.formula.model.Script
import com.wwwaker.warer.core.formula.model.Sqrt
import com.wwwaker.warer.core.formula.model.toPlainText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 光标定位测试。
 *
 * 这些用例同时是"`slots()` 顺序必须与布局递归顺序一致"这一不变量的守门员 ——
 * 一旦两者错位，光标就会被画到别的行上。
 */
class CaretLocatorTest {

    private val ctx = LayoutContext(FakeFontMetrics(), 1.0)
    private val fm = FakeFontMetrics()

    private fun t(s: String) = Num(s)
    private fun rowOf(vararg nodes: Node) = Row(nodes.toList())

    // ============================ 基本定位 ============================

    @Test
    fun `caret in an empty document sits at the origin`() {
        val probe = locateCaret(Row(), Caret(), ctx)!!
        assertEquals(0.0, probe.x, 1e-9)
        assertEquals(0.0, probe.baselineY, 1e-9)
    }

    @Test
    fun `caret offset walks along the row`() {
        val root = rowOf(t("1"), t("2"))
        assertEquals(0.0, locateCaret(root, Caret(emptyList(), 0), ctx)!!.x, 1e-9)
        assertEquals(fm.width("1"), locateCaret(root, Caret(emptyList(), 1), ctx)!!.x, 1e-9)
        assertEquals(fm.width("1") * 2, locateCaret(root, Caret(emptyList(), 2), ctx)!!.x, 1e-9)
    }

    @Test
    fun `invalid caret path yields null`() {
        assertNull(locateCaret(rowOf(t("1")), Caret(listOf(CaretStep(3, 0)), 0), ctx))
    }

    // ============================ 与布局几何一致 ============================

    @Test
    fun `numerator caret sits above and denominator caret below the baseline`() {
        val root = rowOf(Frac(Row(listOf(t("1"))), Row(listOf(t("2")))))
        val numerator = locateCaret(root, Caret(listOf(CaretStep(0, 0)), 0), ctx)!!
        val denominator = locateCaret(root, Caret(listOf(CaretStep(0, 1)), 0), ctx)!!

        assertTrue("分子内的光标应在基线上方", numerator.baselineY < 0.0)
        assertTrue("分母内的光标应在基线下方", denominator.baselineY > 0.0)
    }

    @Test
    fun `caret anchors are derived from the same geometry as the layout`() {
        val root = rowOf(Frac(Row(listOf(t("1"))), Row(listOf(t("2")))))
        val layout = FormulaLayout.layoutFormula(root, ctx)
        val numerator = locateCaret(root, Caret(listOf(CaretStep(0, 0)), 0), ctx)!!

        // 分子是最高的部分，因此它的上边界应当就是整个分式的上边界。
        // 这条断言正是发现"分式 ascent 漏算分子 descent"的探针。
        assertEquals(-layout.ascent, numerator.baselineY - numerator.ascent, 1e-9)
    }

    @Test
    fun `caret inside a script uses the scaled font size`() {
        val root = rowOf(Script(Row(listOf(t("x"))), superscript = Row(listOf(t("2")))))
        val probe = locateCaret(root, Caret(listOf(CaretStep(0, 1)), 1), ctx)!!

        assertEquals(
            fm.ascent * LayoutConstants.SCRIPT_SCALE,
            probe.ascent,
            1e-9
        )
    }

    @Test
    fun `radicand and index keep their slot order from the layout`() {
        val root = rowOf(Sqrt(Row(listOf(t("8"))), index = Row(listOf(t("3")))))
        val radicandEnd = locateCaret(root, Caret(listOf(CaretStep(0, 0)), 1), ctx)!!
        val indexEnd = locateCaret(root, Caret(listOf(CaretStep(0, 1)), 1), ctx)!!

        assertEquals("三个行：根 / 被开方数 / 根指数", 3, radicandEnd.visitedRowCount)
        assertTrue("根指数应位于根号左侧", indexEnd.x < radicandEnd.x)
        assertTrue("根指数应抬得更高", indexEnd.baselineY < radicandEnd.baselineY)
    }

    /**
     * 回归测试：**目标行后面还嵌着别的行**时，行尾插入点也必须算对。
     *
     * 真机暴露的问题：探针把"当前是否目标行"和"行内偏移"放在自身字段里，
     * 一旦进入嵌套的子行就会被覆盖且无法恢复；回到父行做 [RowVisitor.endRow] 时
     * 已经匹配不上，于是 x 停留在行首 0 —— 表现为"光标跑到分数左边"。
     *
     * 旧用例里目标行恰好都是**最后**访问到的行，所以一直没暴露。
     */
    @Test
    fun `row end anchor is found even when nested rows are visited after it`() {
        // 根行只有 1 个子节点（分式），而分式内部还嵌着分子 / 分母两个行
        val root = rowOf(Frac(Row(listOf(t("1"))), Row(listOf(t("2")))))
        val totalWidth = FormulaLayout.measure(root, ctx).width

        val endAnchor = locateCaret(root, Caret(emptyList(), 1), ctx)!!

        assertEquals("根行行尾应等于整棵公式宽度", totalWidth, endAnchor.x, 1e-9)
    }

    /**
     * 回归测试：空分母的基线必须落在"**分数线下方一个间隙 + 一个上伸部**"处。
     *
     * 这是真机暴露的问题：空行按 0 度量时 `den.ascent = 0`，
     * 分母基线就只剩"分数线下方一个间隙"这一小段，紧贴分数线；
     * 而绘制光标时基线还要再往上抬一个整字体的 ascent，
     * 于是光标竖线的上端**穿进分数线里**（用户看到的就是"光标停在分数线中间"）。
     *
     * 断言写成"间隙 + 一个上伸部"，是因为这才是"一个字符该在的位置"这个物理含义 ——
     * 只断言"在分数线下方"是不够的：旧代码同样满足，抓不住 bug。
     */
    @Test
    fun `an empty denominator keeps its baseline a full ascender below the rule`() {
        val root = rowOf(Frac(Row(listOf(t("1"))), Row()))
        val bar = FormulaLayout.layoutFormula(root, ctx)
            .commands.filterIsInstance<DrawCmd.Rect>().single()

        val anchor = locateCaret(root, Caret(listOf(CaretStep(0, 1)), 0), ctx)!!

        assertEquals(
            "空分母的基线应位于分数线下方 (间隙 + 上伸部) 处",
            LayoutConstants.FRACTION_GAP + fm.ascent,
            anchor.baselineY - (bar.y + bar.height),
            1e-9
        )
    }

    @Test
    fun `a later row must not overwrite an already resolved anchor`() {
        // 回归用例：根指数是最后访问的行，随后根行结束。
        // 若探针离开目标行后仍继续写入 x，光标会被挪到根行末尾（= 整棵根号的总宽度）。
        val root = rowOf(Sqrt(Row(listOf(t("8"))), index = Row(listOf(t("3")))))
        val indexEnd = locateCaret(root, Caret(listOf(CaretStep(0, 1)), 1), ctx)!!
        val totalWidth = FormulaLayout.measure(root, ctx).width

        assertTrue(
            "根指数末尾的光标不应被根行末尾覆盖（x=" + indexEnd.x + ", total=" + totalWidth + "）",
            indexEnd.x < totalWidth
        )
    }

    @Test
    fun `big operator limits keep their slot order from the layout`() {
        val root = rowOf(
            BigOp(BigOpKind.SUM, lower = Row(listOf(t("0"))), upper = Row(listOf(t("n"))))
        )
        val lower = locateCaret(root, Caret(listOf(CaretStep(0, 0)), 1), ctx)!!
        val upper = locateCaret(root, Caret(listOf(CaretStep(0, 1)), 1), ctx)!!

        assertTrue("上限应高于下限", upper.baselineY < lower.baselineY)
    }

    // ============================ 与编辑操作配合 ============================

    @Test
    fun `prefilled template sends the caret straight into the empty slot`() {
        val node = Script(Row(listOf(Num("x"))), superscript = Row())

        val inserted = FormulaEditor.insertStructure(Row(), Caret(emptyList(), 0), node)
        assertEquals(Caret(listOf(CaretStep(0, 1)), 0), inserted.caret)

        val typed = FormulaEditor.insert(inserted.root, inserted.caret, t("2"))
        assertEquals("x^(2)", typed.root.toPlainText())

        // 光标确实落在上标那一行
        val anchor = locateCaret(typed.root, typed.caret, ctx)!!
        val baseAnchor = locateCaret(typed.root, Caret(listOf(CaretStep(0, 0)), 1), ctx)!!
        assertTrue("上标应高于底数", anchor.baselineY < baseAnchor.baselineY)
    }
}
