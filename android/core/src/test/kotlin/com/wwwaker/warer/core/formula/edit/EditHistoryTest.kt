package com.wwwaker.warer.core.formula.edit

import com.wwwaker.warer.core.formula.model.Frac
import com.wwwaker.warer.core.formula.model.Num
import com.wwwaker.warer.core.formula.model.Row
import com.wwwaker.warer.core.formula.model.Sym
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 撤销 / 重做的测试。
 *
 * 这里刻意**通过真实的编辑操作**来驱动历史（而不是手工拼 `EditorState`），
 * 这样测的是"编辑 + 历史"的实际配合，而不是历史类自己的算术。
 */
class EditHistoryTest {

    private fun type(history: EditHistory, text: String) {
        text.forEach { ch ->
            val s = history.current
            history.commit(FormulaEditor.insert(s.root, s.caret, Num(ch.toString())))
        }
    }

    private fun insertFrac(history: EditHistory) {
        val s = history.current
        history.commit(FormulaEditor.insertStructure(s.root, s.caret, Frac(Row(), Row())))
    }

    private fun rootText(history: EditHistory): String =
        history.current.root.children.joinToString("") {
            when (it) {
                is Num -> it.text
                is Sym -> it.ch.toString()
                is Frac -> "/"
                else -> "?"
            }
        }

    // ============================ 基本 ============================

    @Test
    fun `fresh history has nothing to undo or redo`() {
        val history = EditHistory()
        assertFalse(history.canUndo)
        assertFalse(history.canRedo)
        assertEquals(EditorState(), history.current)
    }

    @Test
    fun `undo on empty history is a no-op`() {
        val history = EditHistory()
        assertFalse(history.undo())
        assertEquals(EditorState(), history.current)
    }

    @Test
    fun `redo on empty history is a no-op`() {
        val history = EditHistory()
        assertFalse(history.redo())
        assertEquals(EditorState(), history.current)
    }

    // ============================ 撤销 ============================

    @Test
    fun `undo steps back one edit at a time`() {
        val history = EditHistory()
        type(history, "12")

        assertEquals("12", rootText(history))

        assertTrue(history.undo())
        assertEquals("1", rootText(history))

        assertTrue(history.undo())
        assertEquals("", rootText(history))

        assertFalse(history.canUndo)
    }

    @Test
    fun `undo restores the caret to where the edit happened`() {
        val history = EditHistory()
        type(history, "12")
        assertEquals(2, history.current.caret.offset)

        history.undo()
        assertEquals(1, history.current.caret.offset)
    }

    @Test
    fun `structural edit can be undone as a whole`() {
        val history = EditHistory()
        insertFrac(history)
        assertEquals("/", rootText(history))

        history.undo()
        assertEquals("", rootText(history))
    }

    @Test
    fun `backspace can be undone`() {
        val history = EditHistory()
        type(history, "12")

        val s = history.current
        history.commit(FormulaEditor.backspace(s.root, s.caret))
        assertEquals("1", rootText(history))

        history.undo()
        assertEquals("12", rootText(history))
    }

    // ============================ 重做 ============================

    @Test
    fun `redo reapplies an undone edit`() {
        val history = EditHistory()
        type(history, "12")
        history.undo()

        assertEquals("1", rootText(history))
        assertTrue(history.redo())
        assertEquals("12", rootText(history))
        assertFalse(history.canRedo)
    }

    @Test
    fun `undo then redo returns to the exact same state`() {
        val history = EditHistory()
        type(history, "12")
        insertFrac(history)
        val before = history.current

        history.undo()
        history.redo()

        assertEquals(before, history.current)
    }

    // ============================ 光标移动不入栈 ============================

    @Test
    fun `moving the caret alone does not create an undo step`() {
        val history = EditHistory()
        type(history, "1")
        val depthAfterTyping = history.undoDepth

        // 仅移动光标
        history.commit(history.current.copy(caret = Caret(offset = 0)))

        assertEquals(0, history.current.caret.offset)
        assertEquals(depthAfterTyping, history.undoDepth)
    }

    @Test
    fun `caret-only commit still updates the current state`() {
        val history = EditHistory()
        val moved = EditorState(Row(listOf(Num("7"))), Caret(offset = 1))

        history.commit(moved)

        // 根节点变了 → 算一次编辑
        assertEquals(1, history.undoDepth)

        history.commit(moved.copy(caret = Caret(offset = 0)))
        assertEquals(0, history.current.caret.offset)
        assertEquals(1, history.undoDepth)
    }

    // ============================ 分支会清空重做栈 ============================

    @Test
    fun `a new edit after undo clears the redo stack`() {
        val history = EditHistory()
        type(history, "12")
        history.undo()
        assertTrue(history.canRedo)

        type(history, "9")

        assertFalse(history.canRedo)
        assertEquals("19", rootText(history))
    }

    // ============================ 深度上限 ============================

    @Test
    fun `history depth is capped and drops the oldest entry`() {
        val history = EditHistory(limit = 3)
        type(history, "12345")

        assertEquals(3, history.undoDepth)

        // 只能退回 3 步
        repeat(3) { assertTrue(history.undo()) }
        assertFalse(history.undo())
        assertEquals("12", rootText(history))
    }

    // ============================ reset ============================

    @Test
    fun `reset clears everything`() {
        val history = EditHistory()
        type(history, "12")
        history.undo()

        history.reset()

        assertEquals(EditorState(), history.current)
        assertFalse(history.canUndo)
        assertFalse(history.canRedo)
    }

    @Test
    fun `committing an identical state changes nothing`() {
        val history = EditHistory()
        type(history, "1")
        val expected = history.current

        history.commit(history.current)

        assertEquals(expected, history.current)
        assertEquals(1, history.undoDepth)
    }
}
