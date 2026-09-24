package com.wwwaker.warer.core.formula.edit

import com.wwwaker.warer.core.formula.model.Row
import com.wwwaker.warer.core.formula.model.slots
import com.wwwaker.warer.core.formula.model.withSlot

/**
 * 按 [path] 解析出目标行；路径非法（越界 / 指向不存在的槽位）时返回 `null`。
 */
fun Row.resolve(path: List<CaretStep>): Row? {
    var current = this
    for (step in path) {
        val child = current.children.getOrNull(step.childIndex) ?: return null
        val slot = child.slots().getOrNull(step.slotIndex) ?: return null
        current = slot
    }
    return current
}

/**
 * 用 [transform] 替换 [path] 指向的那一行，并**沿途重建整棵树**。
 *
 * 因为 AST 不可变，这里天然是纯函数：原树不受任何影响，
 * 只有从根到目标行的这一条路径上的节点被复制。
 */
fun Row.updateRow(path: List<CaretStep>, transform: (Row) -> Row): Row {
    if (path.isEmpty()) return transform(this)

    val step = path.first()
    val child = children.getOrNull(step.childIndex) ?: return this
    val slot = child.slots().getOrNull(step.slotIndex) ?: return this

    val newSlot = slot.updateRow(path.drop(1), transform)
    val newChild = child.withSlot(step.slotIndex, newSlot)
    val newChildren = children.toMutableList().also { it[step.childIndex] = newChild }
    return copy(children = newChildren)
}

/**
 * 深度优先（= 视觉阅读顺序）收集所有行及其路径。
 *
 * 这是后续实现"左右光标视觉展平"和"Tab 跳槽位"的共同基础。
 */
fun Row.collectRows(
    path: List<CaretStep> = emptyList(),
    out: MutableList<Pair<List<CaretStep>, Row>> = mutableListOf()
): List<Pair<List<CaretStep>, Row>> {
    out.add(path to this)
    children.forEachIndexed { childIndex, child ->
        child.slots().forEachIndexed { slotIndex, slot ->
            if (slot != null) {
                slot.collectRows(path + CaretStep(childIndex, slotIndex), out)
            }
        }
    }
    return out
}
