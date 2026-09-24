package com.wwwaker.warer.core.formula.layout

/**
 * 一个光标插入点的几何信息（单位 em、基线坐标、y 向下为正）。
 *
 * @param rowIndex 所属行在深度优先遍历中的序号（与 `Row.collectRows()` 一致）
 * @param offset   行内偏移（`0` 最左，`children.size` 最右）
 * @param x        插入点的 x
 * @param top      所在行的上边界
 * @param bottom   所在行的下边界
 */
data class CaretSlot(
    val rowIndex: Int,
    val offset: Int,
    val x: Double,
    val top: Double,
    val bottom: Double
)

/**
 * 采集公式中**全部**光标插入点，用于点击定位。
 *
 * 与 [CaretProbe] 的区别：探针只关心预先指定的某一行（画光标用），
 * 而命中表要全部 —— 因为点击之前根本不知道用户会点在哪里。
 *
 * 内部用**栈**维护行状态，不能只留一个"当前行"字段：遍历是深度优先的，
 * 子行结束后还要**回到父行继续放置**，父行剩余插入点同样要记录。
 */
class CaretMap : RowVisitor {

    private class Frame(
        val rowIndex: Int,
        val top: Double,
        val bottom: Double
    ) {
        var childIndex = 0
    }

    private val collected = mutableListOf<CaretSlot>()
    private val stack = ArrayDeque<Frame>()
    private var nextRowIndex = 0

    /** 按遍历顺序排列的全部插入点。 */
    val slots: List<CaretSlot> get() = collected

    override fun enterRow(x: Double, baselineY: Double, measured: Measured) {
        stack.addLast(
            Frame(
                rowIndex = nextRowIndex++,
                top = baselineY - measured.ascent,
                bottom = baselineY + measured.descent
            )
        )
    }

    override fun beforeChild(cursorX: Double) {
        val frame = stack.lastOrNull() ?: return
        collected += CaretSlot(
            rowIndex = frame.rowIndex,
            offset = frame.childIndex,
            x = cursorX,
            top = frame.top,
            bottom = frame.bottom
        )
        frame.childIndex++
    }

    override fun endRow(cursorX: Double) {
        // 行尾插入点，同时覆盖"空行只有一个插入点"的情况
        val frame = stack.removeLastOrNull() ?: return
        collected += CaretSlot(
            rowIndex = frame.rowIndex,
            offset = frame.childIndex,
            x = cursorX,
            top = frame.top,
            bottom = frame.bottom
        )
    }
}
