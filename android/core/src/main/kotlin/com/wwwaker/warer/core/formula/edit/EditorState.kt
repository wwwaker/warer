package com.wwwaker.warer.core.formula.edit

import com.wwwaker.warer.core.formula.model.Row

/**
 * 编辑器状态：**一棵 AST + 一个光标位置**。
 *
 * 这是编辑器的全部可变状态 —— 除此之外没有任何需要同步的东西。
 * 正因如此，"撤销"只需保存这个值的快照即可（见 [EditHistory]），
 * 而不必为每个操作再写一个逆操作。
 */
data class EditorState(
    val root: Row = Row(),
    val caret: Caret = Caret()
)

/**
 * 一次编辑的结果。
 *
 * 它和 [EditorState] 是同一个东西 —— 编辑的结果就是新的编辑器状态，
 * 用别名保留这个叫法是为了在编辑操作处读起来更贴切（`FormulaEditor.insert(...)` 返回一次编辑结果）。
 */
typealias EditResult = EditorState
