package com.wwwaker.warer.core.formula.edit

import com.wwwaker.warer.core.formula.convert.CommandBuilder
import com.wwwaker.warer.core.formula.model.BigOp
import com.wwwaker.warer.core.formula.model.BigOpKind
import com.wwwaker.warer.core.formula.model.Frac
import com.wwwaker.warer.core.formula.model.Node
import com.wwwaker.warer.core.formula.model.Num
import com.wwwaker.warer.core.formula.model.Row
import com.wwwaker.warer.core.formula.model.Script
import com.wwwaker.warer.core.formula.model.Sqrt
import com.wwwaker.warer.core.formula.model.Sym
import com.wwwaker.warer.core.formula.model.toPlainText
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 编辑操作集的测试。
 *
 * 这些用例锁定了"模板插入 + 槽位跳转"的核心交互契约，
 * 也是后续实现在 UI 上的光标行为时唯一需要保证不破的东西。
 */
class FormulaEditorTest {

    private fun t(s: String) = Num(s)
    private fun rowOf(vararg nodes: Node) = Row(nodes.toList())

    /** 在根行末尾插入一个结构节点（模拟按模板键）。 */
    private fun insertAtEnd(root: Row, node: Node): EditResult =
        FormulaEditor.insertStructure(root, Caret(emptyList(), root.children.size), node)

    private val numerator = CaretStep(0, 0)
    private val denominator = CaretStep(0, 1)

    // ============================ 左侧操作数吸收 ============================

    /**
     * 回归测试：`2` → `□²` 应得到 `2²`。
     *
     * 真机暴露的问题：模板不吸收左侧节点时，`2` 会留在外面、模板空底数另外收一个字符，
     * 于是 `2 → □² → 5` 被编译成 `25` 而不是 `2^(5)` —— 输入看着没问题，结果完全不同。
     */
    @Test
    fun `superscript absorbs the node on its left as the base`() {
        val result = insertAtEnd(rowOf(t("2")), Script(Row(), superscript = Row()))

        assertEquals("2^()", result.root.toPlainText())
        assertEquals(Caret(listOf(CaretStep(0, 1)), 0), result.caret)
    }

    @Test
    fun `superscript template after a number wraps it as the base of a power`() {
        val inserted = insertAtEnd(rowOf(t("2")), Script(Row(), superscript = Row()))
        val typed = FormulaEditor.insert(inserted.root, inserted.caret, t("5"))

        assertEquals("2^(5)", typed.root.toPlainText())
        assertEquals("2^(5)", CommandBuilder.compile(typed.root).command)
    }

    @Test
    fun `fraction absorbs the node on its left as the numerator`() {
        // 2 → a/b 应得到 2/□，并把光标送进分母（与 Desmos 的 / 键一致）
        val result = insertAtEnd(rowOf(t("2")), Frac(Row(), Row()))

        assertEquals("(2)/()", result.root.toPlainText())
        assertEquals(Caret(listOf(CaretStep(0, 1)), 0), result.caret)
    }

    @Test
    fun `operators are not absorbed`() {
        // 1+ → □² 不能把 + 吸成底数
        val result = insertAtEnd(rowOf(t("1"), Sym('+')), Script(Row(), superscript = Row()))

        assertEquals("1+^()", result.root.toPlainText())
        assertEquals(Caret(listOf(CaretStep(2, 0)), 0), result.caret)
    }

    @Test
    fun `radicals do not absorb their left neighbour`() {
        // 2 → √ 不应变成 √2：`2√3` 是 2 乘以 √3，不是 √23
        val result = insertAtEnd(rowOf(t("2")), Sqrt(Row()))

        assertEquals("2sqrt()", result.root.toPlainText())
        assertEquals(Caret(listOf(CaretStep(1, 0)), 0), result.caret)
    }

    @Test
    fun `nothing is absorbed when the caret is at the start of the row`() {
        val result = FormulaEditor.insertStructure(
            rowOf(t("2")),
            Caret(emptyList(), 0),
            Script(Row(), superscript = Row())
        )

        assertEquals("^()2", result.root.toPlainText())
        assertEquals(Caret(listOf(CaretStep(0, 0)), 0), result.caret)
    }

    // ============================ 基础插入 ============================

    @Test
    fun `insert appends a leaf and advances the caret`() {
        val result = FormulaEditor.insert(rowOf(t("1")), Caret(emptyList(), 1), t("2"))
        assertEquals("12", result.root.toPlainText())
        assertEquals(Caret(emptyList(), 2), result.caret)
    }

    @Test
    fun `insert at the beginning shifts the caret accordingly`() {
        val result = FormulaEditor.insert(rowOf(t("2")), Caret(emptyList(), 0), t("1"))
        assertEquals("12", result.root.toPlainText())
        assertEquals(Caret(emptyList(), 1), result.caret)
    }

    @Test
    fun `edits never mutate the original tree`() {
        val root = rowOf(t("1"))
        FormulaEditor.insert(root, Caret(emptyList(), 1), t("2"))
        assertEquals("1", root.toPlainText())
    }

    @Test
    fun `invalid caret paths are ignored instead of crashing`() {
        val root = rowOf(t("1"))
        val result = FormulaEditor.insert(root, Caret(listOf(CaretStep(9, 9)), 0), t("x"))
        assertEquals("1", result.root.toPlainText())
    }

    // ============================ 结构插入 + 槽位跳转 ============================

    @Test
    fun `insertStructure sends the caret into the first slot`() {
        val result = insertAtEnd(Row(), Frac(Row(), Row()))
        assertEquals(1, result.root.children.size)
        assertEquals(Caret(listOf(numerator), 0), result.caret)
    }

    @Test
    fun `typing right after a fraction template lands in the numerator`() {
        val inserted = insertAtEnd(Row(), Frac(Row(), Row()))
        val typed = FormulaEditor.insert(inserted.root, inserted.caret, t("1"))
        assertEquals("(1)/()", typed.root.toPlainText())
    }

    @Test
    fun `tab moves from the numerator to the denominator`() {
        val inserted = insertAtEnd(Row(), Frac(Row(), Row()))
        val typed = FormulaEditor.insert(inserted.root, inserted.caret, t("1"))

        val next = FormulaEditor.focusNextEmptySlot(typed.root, typed.caret)
        assertEquals(Caret(listOf(denominator), 0), next)

        val finished = FormulaEditor.insert(typed.root, next, t("2"))
        assertEquals("(1)/(2)", finished.root.toPlainText())
    }

    @Test
    fun `tab does nothing once every slot is filled`() {
        val inserted = insertAtEnd(Row(), Frac(Row(), Row()))
        val first = FormulaEditor.insert(inserted.root, inserted.caret, t("1"))
        val second = FormulaEditor.insert(
            first.root,
            FormulaEditor.focusNextEmptySlot(first.root, first.caret),
            t("2")
        )
        assertEquals(second.caret, FormulaEditor.focusNextEmptySlot(second.root, second.caret))
    }

    @Test
    fun `tab can step backwards through empty slots`() {
        // ∫ 模板带两个空槽：下限、上限
        val inserted = insertAtEnd(Row(), BigOp(BigOpKind.INTEGRAL, lower = Row(), upper = Row()))

        val onUpper = FormulaEditor.focusNextEmptySlot(inserted.root, inserted.caret)
        assertEquals(Caret(listOf(denominator), 0), onUpper)

        assertEquals(
            Caret(listOf(numerator), 0),
            FormulaEditor.focusPreviousEmptySlot(inserted.root, onUpper)
        )
    }

    @Test
    fun `tab wraps around to the first empty slot`() {
        val inserted = insertAtEnd(Row(), BigOp(BigOpKind.INTEGRAL, lower = Row(), upper = Row()))
        val onUpper = FormulaEditor.focusNextEmptySlot(inserted.root, inserted.caret)
        assertEquals(
            Caret(listOf(numerator), 0),
            FormulaEditor.focusNextEmptySlot(inserted.root, onUpper)
        )
    }

    @Test
    fun `integral template fills the lower limit before the upper one`() {
        val inserted = insertAtEnd(
            Row(),
            BigOp(BigOpKind.INTEGRAL, lower = Row(), upper = Row())
        )
        assertEquals(Caret(listOf(numerator), 0), inserted.caret)

        val lower = FormulaEditor.insert(inserted.root, inserted.caret, t("0"))
        val onUpper = FormulaEditor.focusNextEmptySlot(lower.root, lower.caret)
        assertEquals(Caret(listOf(denominator), 0), onUpper)

        val upper = FormulaEditor.insert(lower.root, onUpper, t("1"))
        assertEquals("∫_(0)^(1)", upper.root.toPlainText())
    }

    @Test
    fun `exponent template fills the base before the exponent`() {
        val inserted = insertAtEnd(Row(), Script(Row(), superscript = Row()))
        assertEquals(Caret(listOf(numerator), 0), inserted.caret)

        val base = FormulaEditor.insert(inserted.root, inserted.caret, Sym('x'))
        val onExponent = FormulaEditor.focusNextEmptySlot(base.root, base.caret)
        assertEquals(Caret(listOf(denominator), 0), onExponent)

        val exponent = FormulaEditor.insert(base.root, onExponent, t("2"))
        assertEquals("x^(2)", exponent.root.toPlainText())
    }

    // ============================ 退格 ============================

    @Test
    fun `backspace deletes the node before the caret`() {
        val result = FormulaEditor.backspace(rowOf(t("1"), t("2")), Caret(emptyList(), 2))
        assertEquals("1", result.root.toPlainText())
        assertEquals(Caret(emptyList(), 1), result.caret)
    }

    @Test
    fun `backspace removes a structure as a single unit`() {
        val root = rowOf(Frac(Row(listOf(t("1"))), Row(listOf(t("2")))), t("3"))
        val result = FormulaEditor.backspace(root, Caret(emptyList(), 2))
        assertEquals("(1)/(2)", result.root.toPlainText())
        assertEquals(Caret(emptyList(), 1), result.caret)
    }

    @Test
    fun `backspace at the start of a slot escapes to the parent instead of deleting`() {
        val root = rowOf(Frac(Row(), Row()))
        val result = FormulaEditor.backspace(root, Caret(listOf(numerator), 0))
        assertEquals("()/()", result.root.toPlainText())
        assertEquals(Caret(emptyList(), 0), result.caret)
    }

    @Test
    fun `backspace at the very start of the document does nothing`() {
        val root = rowOf(t("1"))
        val result = FormulaEditor.backspace(root, Caret(emptyList(), 0))
        assertEquals("1", result.root.toPlainText())
        assertEquals(Caret(emptyList(), 0), result.caret)
    }

    // ============================ 左右移动 ============================

    @Test
    fun `move right enters the next slot of the owning structure`() {
        val root = rowOf(Frac(Row(listOf(t("1"))), Row(listOf(t("2")))))
        val atEndOfNumerator = Caret(listOf(numerator), 1)
        assertEquals(Caret(listOf(denominator), 0), FormulaEditor.moveRight(root, atEndOfNumerator))
    }

    @Test
    fun `move right past the last slot returns to the parent row`() {
        val root = rowOf(Frac(Row(listOf(t("1"))), Row(listOf(t("2")))), t("3"))
        val atEndOfDenominator = Caret(listOf(denominator), 1)
        assertEquals(Caret(emptyList(), 1), FormulaEditor.moveRight(root, atEndOfDenominator))
    }

    @Test
    fun `move left at the start of a slot escapes to the parent row`() {
        val root = rowOf(Frac(Row(listOf(t("1"))), Row(listOf(t("2")))))
        val atStartOfDenominator = Caret(listOf(denominator), 0)
        assertEquals(Caret(emptyList(), 0), FormulaEditor.moveLeft(root, atStartOfDenominator))
    }

    @Test
    fun `move left inside a row just decrements the offset`() {
        val result = FormulaEditor.moveLeft(rowOf(t("1"), t("2")), Caret(emptyList(), 2))
        assertEquals(Caret(emptyList(), 1), result)
    }

    // ============================ 遍历顺序 ============================

    @Test
    fun `collectRows visits rows in visual reading order`() {
        val root = rowOf(
            t("1"),
            Frac(Row(listOf(t("2"))), Row(listOf(t("3")))),
            t("4")
        )
        assertEquals(
            listOf(
                emptyList(),
                listOf(CaretStep(1, 0)),
                listOf(CaretStep(1, 1))
            ),
            root.collectRows().map { it.first }
        )
    }

    @Test
    fun `resolve follows a nested path`() {
        val root = rowOf(Frac(Row(listOf(t("7"))), Row()))
        assertEquals("7", root.resolve(listOf(numerator))?.toPlainText())
        assertEquals(null, root.resolve(listOf(CaretStep(0, 9))))
        assertEquals(null, root.resolve(listOf(CaretStep(9, 0))))
    }
}
