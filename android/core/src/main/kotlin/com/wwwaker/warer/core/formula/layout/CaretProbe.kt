package com.wwwaker.warer.core.formula.layout

/**
 * 光标探针：在**布局过程中**顺带记录光标锚点的位置。
 *
 * 为什么要在布局里"顺便"记录，而不是单独再算一遍？
 * 因为光标位置完全由排版几何决定（分子中心、上下限间距、括号内留白……）。
 * 如果单独实现一套，一旦排版常量或算法调整而定位逻辑忘记同步，光标就会跑偏。
 * 挂在布局过程中记录，**几何信息只有一个来源，永远不会漂移**。
 *
 * 工作方式：
 * - 布局引擎每进入一个 [com.wwwaker.warer.core.formula.model.Row] 就调用一次 [enterRow]，
 *   行的编号按**深度优先（视觉顺序）**递增 —— 这个顺序与
 *   `Row.collectRows()` 的顺序严格一致；
 * - 每放置一个子节点之前调用 [beforeChild]，全部放完后调用 [endRow]；
 * - 当访问到第 [targetRowIndex] 个行时，记录该行内第 [offset] 个插入点的坐标。
 *
 * **每一行的状态必须独立保存**（见 [Frame]）：遍历是深度优先的，
 * 子行结束后还要回到父行继续放置。早期版本把这些状态放在探针自身字段里，
 * 于是"进入子行"会覆盖父行的状态且无法恢复，导致
 * **目标行的行尾插入点永远算不出来**（光标停在行首）。
 * 这类 bug 只在"目标行后面还嵌着别的行"时出现，正是真机上才暴露出来的。
 *
 * 单位与坐标系同 [DrawCmd]：em、基线原点、y 向下为正。
 */
class CaretProbe(
    /** 目标行在深度优先遍历中的序号。 */
    val targetRowIndex: Int,
    /** 目标插入点在该行内的偏移。 */
    val offset: Int
) : RowVisitor {

    /** 每一行一次访问的状态，随遍历入栈 / 出栈。 */
    private class Frame(val isTarget: Boolean) {
        var childIndex = 0
    }

    private val stack = ArrayDeque<Frame>()
    private var rowIndex = -1

    /** 光标插入点的 x 坐标（em）。 */
    var x: Double = 0.0
        private set

    /** 目标行的基线 y 坐标（em）。 */
    var baselineY: Double = 0.0
        private set

    /** 目标行的上伸部与下延部，用于决定光标竖线的高度。 */
    var ascent: Double = 0.0
        private set
    var descent: Double = 0.0
        private set

    /** 目标行是否已被访问到。 */
    var resolved: Boolean = false
        private set

    /** 布局过程中实际访问到的行数（诊断 / 断言用）。 */
    var visitedRowCount: Int = 0
        private set

    /** 由布局引擎在进入每个行时调用。 */
    override fun enterRow(x: Double, baselineY: Double, measured: Measured) {
        rowIndex++
        visitedRowCount = rowIndex + 1

        val isTarget = rowIndex == targetRowIndex
        stack.addLast(Frame(isTarget))

        if (isTarget) {
            resolved = true
            this.x = x
            this.baselineY = baselineY
            this.ascent = measured.ascent
            this.descent = measured.descent
        }
    }

    /** 由布局引擎在放置该行的每个子节点之前调用，[cursorX] 即该子节点左边缘 = 插入点位置。 */
    override fun beforeChild(cursorX: Double) {
        val frame = stack.lastOrNull() ?: return
        if (frame.isTarget && frame.childIndex == offset) x = cursorX
        frame.childIndex++
    }

    /**
     * 由布局引擎在该行所有子节点放置完毕后调用（覆盖"插在行尾"的情况）。
     *
     * 这里必须出栈：**行的状态是它自己的**，不能留在探针字段里给父行用。
     */
    override fun endRow(cursorX: Double) {
        val frame = stack.removeLastOrNull() ?: return
        if (frame.isTarget && frame.childIndex == offset) x = cursorX
    }
}
