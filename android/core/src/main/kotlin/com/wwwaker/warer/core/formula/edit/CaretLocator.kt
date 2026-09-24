package com.wwwaker.warer.core.formula.edit

import com.wwwaker.warer.core.formula.layout.CaretMap
import com.wwwaker.warer.core.formula.layout.CaretProbe
import com.wwwaker.warer.core.formula.layout.CaretSlot
import com.wwwaker.warer.core.formula.layout.FormulaLayout
import com.wwwaker.warer.core.formula.layout.LayoutContext
import com.wwwaker.warer.core.formula.layout.Point2
import com.wwwaker.warer.core.formula.model.Row
import kotlin.math.abs

/**
 * 计算光标在公式坐标系中的锚点（em、基线原点、y 向下为正）。
 *
 * 实现思路：先把光标路径换算成"深度优先遍历中的第几个 Row"，
 * 再让布局引擎在遍历时把该行的位置记录下来（见 [CaretProbe]）——**几何信息只有布局一个来源**。
 *
 * @return 定位失败（路径非法）时返回 null。
 */
fun locateCaret(root: Row, caret: Caret, ctx: LayoutContext): CaretProbe? {
    val rowIndex = root.collectRows().indexOfFirst { it.first == caret.path }
    if (rowIndex < 0) return null

    val probe = CaretProbe(targetRowIndex = rowIndex, offset = caret.offset)
    FormulaLayout.layoutFormula(root, ctx.copy(visitors = ctx.visitors + probe))
    return if (probe.resolved) probe else null
}

/**
 * 点击定位：在全部插入点中挑出**最接近** [point] 的那一个。
 *
 * 打分方式是 `水平距离 + 竖直溢出距离`。两个量都是 em，**量纲一致**，
 * 因此可以直接相加，不需要任何"魔数权重"—— 这点很重要：否则以后每调一次排版常量，
 * 这个权重就得跟着重新猜，而且没法解释。
 *
 * 直觉：
 * - 点在某行的竖直范围内（溢出距离为 0）且水平最靠近某个插入点 → 就落在那里；
 * - 点得偏上 / 偏下时，先被"溢出距离"筛掉的是远处的行，符合视觉预期；
 * - 即使点在公式之外的空白处也**一定有答案**（最近的插入点），因此不会出现
 *   "点了没反应"这种最令人困惑的情况。
 *
 * @param point 公式坐标系下的点击位置（em、基线原点、y 向下为正）
 * @return 命中的光标；公式为空时返回 `Caret()`（根行偏移 0）
 */
fun hitTestCaret(root: Row, point: Point2, ctx: LayoutContext): Caret? {
    val map = CaretMap()
    FormulaLayout.layoutFormula(root, ctx.copy(visitors = ctx.visitors + map))
    if (map.slots.isEmpty()) return null

    val best = map.slots.minByOrNull { slot ->
        abs(slot.x - point.x) + verticalOvershoot(slot, point)
    } ?: return null

    val path = root.collectRows().getOrNull(best.rowIndex)?.first ?: return null
    return Caret(path, best.offset)
}

/** 点落在行的竖直范围之外时返回溢出的距离；在范围内返回 0。 */
private fun verticalOvershoot(slot: CaretSlot, point: Point2): Double = when {
    point.y < slot.top -> slot.top - point.y
    point.y > slot.bottom -> point.y - slot.bottom
    else -> 0.0
}
