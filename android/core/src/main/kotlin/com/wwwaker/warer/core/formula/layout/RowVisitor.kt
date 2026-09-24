package com.wwwaker.warer.core.formula.layout

/**
 * 布局过程的观察者。
 *
 * 布局是**唯一**知道几何的地方，因此所有"需要知道某个插入点在哪"的功能
 * （绘制光标、点击定位）都通过本接口在遍历过程中**顺带采集**，
 * 而不是事后再算一遍 —— 后者一旦排版常量或算法调整就会悄悄漂移。
 *
 * 遍历顺序为**深度优先**，且与 `Row.collectRows()` 的顺序严格一致：
 * ```
 * enterRow(row)
 *   beforeChild(第 0 个插入点) → 放置子节点 0
 *   beforeChild(第 1 个插入点) → 放置子节点 1
 *   …
 *   endRow(最后一个插入点)
 * ```
 * 因此行内共 `children.size + 1` 个插入点。
 */
interface RowVisitor {

    /** 进入一个行。[x] 为行左边缘，[baselineY] 为行基线，[measured] 为该行的盒模型。 */
    fun enterRow(x: Double, baselineY: Double, measured: Measured)

    /** 即将放置一个子节点，[cursorX] 即该子节点左边缘（也就是对应的插入点位置）。 */
    fun beforeChild(cursorX: Double)

    /** 该行的所有子节点放置完毕，[cursorX] 为行尾（最后一个插入点）。 */
    fun endRow(cursorX: Double)
}
