package com.wwwaker.warer.core.formula.edit

import com.wwwaker.warer.core.formula.model.Node
import com.wwwaker.warer.core.formula.model.Row
import com.wwwaker.warer.core.formula.model.Frac
import com.wwwaker.warer.core.formula.model.Script
import com.wwwaker.warer.core.formula.model.Sym
import com.wwwaker.warer.core.formula.model.slots

/**
 * 编辑器操作集。
 *
 * 所有操作都是**纯函数**：传入不可变的 AST 与光标，返回新的 AST 与光标，
 * 原树永远不被修改。因此**撤销 / 重做只需保存根节点快照**，无需任何逆操作。
 */
object FormulaEditor {

    /** 在光标处插入一个叶子节点，光标右移一位。 */
    fun insert(root: Row, caret: Caret, node: Node): EditResult {
        val target = root.resolve(caret.path) ?: return EditResult(root, caret)
        val index = caret.offset.coerceIn(0, target.children.size)
        val newTarget = target.copy(
            children = target.children.toMutableList().also { it.add(index, node) }
        )
        return EditResult(
            root = root.updateRow(caret.path) { newTarget },
            caret = caret.copy(offset = index + 1)
        )
    }

    /**
     * 插入一个结构节点（模板键的核心），并把光标送进它的**第一个可用槽位**。
     * 例如插入 `∫` 后光标直接落在下限里，可以马上输入。
     *
     * **左侧操作数吸收**：若模板的第一个槽位在视觉上位于"光标左侧"（`x²` 的底数、
     * `a/b` 的分子），则把光标左边紧邻的节点吸收进去。
     *
     * 例：输入 `2` 再按 `□²` 应得到 `2²`。若不吸收，`2` 会留在外面、
     * 模板的空底数另外收一个字符，`2 → □² → 5` 就被编译成 `25` 而不是 `2^(5)` ——
     * 输入看着没问题，算出来的结果却完全不同（真机上就是这么错的）。
     */
    fun insertStructure(root: Row, caret: Caret, node: Node): EditResult {
        val target = root.resolve(caret.path) ?: return EditResult(root, caret)
        val index = caret.offset.coerceIn(0, target.children.size)

        val absorbed = target.children.getOrNull(index - 1)
            ?.takeIf { it.canBeLeftOperand() }
            ?.let { node.withAbsorbedLeftOperand(it) }

        val newChildren = target.children.toMutableList()
        val insertAt: Int
        val effective: Node
        if (absorbed != null) {
            // 模板顶替掉被吸收的那个节点，位置随之左移一格
            insertAt = index - 1
            newChildren[insertAt] = absorbed
            effective = absorbed
        } else {
            insertAt = index
            newChildren.add(insertAt, node)
            effective = node
        }
        val newRoot = root.updateRow(caret.path) { target.copy(children = newChildren) }

        // 优先进入第一个"空"槽位；若模板自带内容（例如 x² 的底数已经是 x），
        // 则退而求其次进入第一个存在的槽位。
        val slots = effective.slots()
        val firstSlot = slots.indexOfFirst { it != null && it.children.isEmpty() }
            .takeIf { it >= 0 }
            ?: slots.indexOfFirst { it != null }

        return if (firstSlot >= 0) {
            EditResult(newRoot, Caret(caret.path + CaretStep(insertAt, firstSlot), 0))
        } else {
            EditResult(newRoot, Caret(caret.path, insertAt + 1))
        }
    }

    /**
     * 把 [previous] 作为模板的"左侧操作数"填入其第一个槽位；不适用时返回 null（不吸收）。
     *
     * 只有确实位于左侧、语义无歧义的两种结构参与吸收：
     * - [Script] 的**底数**：`2` + `□²` → `2²`
     * - [Frac] 的**分子**：`2` + `a/b` → `2/□`（与 Desmos 的 `/` 键行为一致）
     *
     * 根号 / 括号 / 求和号等**不吸收**：它们的内容要么"包在里面"、要么"跟在后面"，
     * 把左侧内容吸进去会改变原意 —— `2√3` 是 2 乘以 √3，不是 √23。
     */
    private fun Node.withAbsorbedLeftOperand(previous: Node): Node? = when (this) {
        is Script -> if (base.children.isEmpty()) copy(base = Row(listOf(previous))) else null
        is Frac -> if (numerator.children.isEmpty()) {
            copy(numerator = Row(listOf(previous)))
        } else {
            null
        }
        else -> null
    }

    /** 运算符 / 标点不能作为"左侧操作数"被吸收，否则会把 `+`、`(` 之类吸进去。 */
    private val NON_OPERAND_SYMBOLS = setOf(
        '+', '-', '*', '/', '^', '=', ',', '.', '(', ')', ' ', '→'
    )

    private fun Node.canBeLeftOperand(): Boolean =
        !(this is Sym && ch in NON_OPERAND_SYMBOLS)

    /**
     * 退格：
     * - 行内还有内容 → 删除光标左侧的一个节点（**结构节点是整体删除的**）；
     * - 已在行首 → 退到父级中"拥有本槽位的结构节点"之前（先逃出结构，再按一次才会删掉它）。
     */
    fun backspace(root: Row, caret: Caret): EditResult {
        val target = root.resolve(caret.path) ?: return EditResult(root, caret)

        if (caret.offset > 0 && target.children.isNotEmpty()) {
            val index = (caret.offset - 1).coerceAtMost(target.children.size - 1)
            val newTarget = target.copy(
                children = target.children.toMutableList().also { it.removeAt(index) }
            )
            return EditResult(root.updateRow(caret.path) { newTarget }, caret.copy(offset = index))
        }

        return EditResult(root, moveLeft(root, caret))
    }

    /** 左移：行内左移；已在行首则退到父级。 */
    fun moveLeft(root: Row, caret: Caret): Caret {
        if (caret.offset > 0) return caret.copy(offset = caret.offset - 1)

        val last = caret.path.lastOrNull() ?: return caret
        return Caret(caret.path.dropLast(1), last.childIndex)
    }

    /**
     * 右移：行内右移；已在行尾则依次尝试
     * ① 进入所属结构的下一个槽位（分子 → 分母）；② 退到父级中该结构之后。
     */
    fun moveRight(root: Row, caret: Caret): Caret {
        val target = root.resolve(caret.path) ?: return caret
        if (caret.offset < target.children.size) return caret.copy(offset = caret.offset + 1)

        val last = caret.path.lastOrNull() ?: return caret
        val parentPath = caret.path.dropLast(1)
        val parent = root.resolve(parentPath) ?: return caret

        val owner = parent.children.getOrNull(last.childIndex)
        if (owner != null) {
            val slots = owner.slots()
            for (slotIndex in (last.slotIndex + 1) until slots.size) {
                if (slots[slotIndex] != null) {
                    return Caret(parentPath + CaretStep(last.childIndex, slotIndex), 0)
                }
            }
        }
        return Caret(parentPath, last.childIndex + 1)
    }

    /** 跳到下一个空槽位（Tab）。所有槽位都已填满时原样返回。 */
    fun focusNextEmptySlot(root: Row, caret: Caret): Caret =
        focusEmptySlot(root, caret, forward = true)

    /** 跳到上一个空槽位。 */
    fun focusPreviousEmptySlot(root: Row, caret: Caret): Caret =
        focusEmptySlot(root, caret, forward = false)

    private fun focusEmptySlot(root: Row, caret: Caret, forward: Boolean): Caret {
        val rows = root.collectRows()
        val currentIndex = rows.indexOfFirst { it.first == caret.path }
        if (currentIndex < 0) return caret

        val empties = rows.withIndex().filter { it.value.second.children.isEmpty() }
        if (empties.isEmpty()) return caret

        val next = if (forward) {
            empties.firstOrNull { it.index > currentIndex } ?: empties.first()
        } else {
            empties.lastOrNull { it.index < currentIndex } ?: empties.last()
        }
        return Caret(next.value.first, 0)
    }
}
