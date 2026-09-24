package com.wwwaker.warer.core.formula.layout

/**
 * 盒模型尺寸（单位 em）。
 *
 * 采用"基线上方 = [ascent]，基线下 = [descent]"的经典约定，
 * 父节点据此对齐子节点（例如 [com.wwwaker.warer.core.formula.model.Row] 让所有子节点共享基线）。
 */
data class Measured(
    val width: Double,
    /** 基线以上的高度（>= 0）。 */
    val ascent: Double,
    /** 基线以下的深度（>= 0）。 */
    val descent: Double
) {
    /** 盒子总高度。 */
    val height: Double get() = ascent + descent
}

/**
 * 布局上下文：携带字体度量、**当前缩放层级**，以及布局观察者。
 *
 * 嵌套结构（如上下标里的上下标）通过 [scaled] 逐层缩小，
 * 所有子节点算出的尺寸都已经换算到**根节点**的 em 单位，因此父节点无需再做二次换算。
 *
 * [visitors] 挂在这里而不是作为 `place` 的参数，是为了避免改动整个放置函数的签名 ——
 * `ctx` 本来就贯穿全树，观察者搭它的"顺风车"即可。列表形式是为了让
 * "画光标"和"点击定位"能在**同一次布局**里各取所需。
 */
data class LayoutContext(
    val metrics: FontMetrics,
    val scale: Double = 1.0,
    val visitors: List<RowVisitor> = emptyList()
) {
    fun scaled(factor: Double) = copy(scale = scale * factor)
}

/**
 * 一次完整布局的产物。
 *
 * [commands] 使用"基线坐标"：x 从 0 起，y 以基线为 0、向下为正。
 * 渲染时按 `(0, ascent)` 平移即可得到左上角为原点的坐标。
 */
data class LayoutResult(
    val measured: Measured,
    val commands: List<DrawCmd>
) {
    val width: Double get() = measured.width
    val ascent: Double get() = measured.ascent
    val descent: Double get() = measured.descent
}
