package com.wwwaker.warer.core.formula.edit

import com.wwwaker.warer.core.formula.model.Row

/**
 * 光标路径的一段。
 *
 * 含义：在父 [Row] 中找到第 [childIndex] 个子节点（一个结构节点），
 * 再进入它的第 [slotIndex] 个槽位。槽位定义见
 * [com.wwwaker.warer.core.formula.model.slots]。
 */
data class CaretStep(val childIndex: Int, val slotIndex: Int)

/**
 * 光标位置。
 *
 * - [path] 为**空**表示光标就在根行里；
 * - [offset] 是当前行内的插入位置（`0` 表示最左，`children.size` 表示最右）。
 *
 * 之所以用"路径 + 偏移"而不是一个扁平下标，是因为公式是树形的：
 * 同一个偏移在不同行里含义完全不同，而路径能唯一定位到"哪一行的哪个字符间隙"。
 */
data class Caret(
    val path: List<CaretStep> = emptyList(),
    val offset: Int = 0
)
