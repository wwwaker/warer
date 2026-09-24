package com.wwwaker.warer.core.formula.model

/**
 * 结构节点的"槽位"——即它内部可编辑的 [Row] 区域。
 *
 * 约定：
 * - **顺序 = 用户填充顺序**（Tab 依次跳转的顺序），同时也是左右光标进入槽位的顺序；
 * - **该顺序必须与 `FormulaLayout` 中递归放置子节点的顺序完全一致** ——
 *   光标定位依赖"布局遍历到的第几个 Row"来匹配光标路径，两者错位会导致光标画在错误位置；
 * - 元素为 `null` 表示该槽位当前不存在（例如 `x²` 没有下标），但**下标位置保持稳定**；
 * - 叶子节点没有槽位。
 *
 * | 节点 | 槽位 |
 * |---|---|
 * | [Frac] | 分子、分母 |
 * | [Script] | 底数、上标、下标 |
 * | [Sqrt] | 被开方数、根指数 |
 * | [Delim] | 括号内容 |
 * | [BigOp] | 下限、上限 |
 */
fun Node.slots(): List<Row?> = when (this) {
    is Row -> listOf(this)
    is Frac -> listOf(numerator, denominator)
    is Script -> listOf(base, superscript, subscript)
    is Sqrt -> listOf(radicand, index)
    is Delim -> listOf(content)
    is BigOp -> listOf(lower, upper)
    is Sym, is Num, is Func -> emptyList()
}

/** 用 [slot] 替换第 [slotIndex] 个槽位；索引非法时原样返回。 */
fun Node.withSlot(slotIndex: Int, slot: Row): Node = when (this) {
    is Row -> if (slotIndex == 0) slot else this

    is Frac -> when (slotIndex) {
        0 -> copy(numerator = slot)
        1 -> copy(denominator = slot)
        else -> this
    }

    is Script -> when (slotIndex) {
        0 -> copy(base = slot)
        1 -> copy(superscript = slot)
        2 -> copy(subscript = slot)
        else -> this
    }

    is Sqrt -> when (slotIndex) {
        0 -> copy(radicand = slot)
        1 -> copy(index = slot)
        else -> this
    }

    is Delim -> if (slotIndex == 0) copy(content = slot) else this

    is BigOp -> when (slotIndex) {
        0 -> copy(lower = slot)
        1 -> copy(upper = slot)
        else -> this
    }

    is Sym, is Num, is Func -> this
}
