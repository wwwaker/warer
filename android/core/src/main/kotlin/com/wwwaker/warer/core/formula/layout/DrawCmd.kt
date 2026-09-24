package com.wwwaker.warer.core.formula.layout

/**
 * 与渲染框架无关的绘制指令。
 *
 * **坐标系约定**（全模块统一）：
 * - 单位：**em**（字号倍数）
 * - 原点：当前公式的水平起点 与 **基线**
 * - y 轴**向下为正**，因此"基线上方"是负数
 *
 * 渲染端只需：① 把 em 乘以字号；② 按 [LayoutResult.ascent] 做一次平移，即可得到
 * 以左上角为原点的常规坐标。
 */
sealed interface DrawCmd {

    /** 文本。[x] 为左边缘，[baselineY] 为基线位置，[scale] 为相对根字号的倍数。 */
    data class Text(
        val value: String,
        val x: Double,
        val baselineY: Double,
        val scale: Double = 1.0
    ) : DrawCmd

    /** 实心矩形（分数线、上横线等）。[x]/[y] 为左上角。 */
    data class Rect(
        val x: Double,
        val y: Double,
        val width: Double,
        val height: Double
    ) : DrawCmd

    /** 折线（伸缩根号等），以 [thickness] 为线宽描边。 */
    data class Polyline(
        val points: List<Point2>,
        val thickness: Double
    ) : DrawCmd

    /**
     * 空槽位占位框（左上角 + 尺寸），提示用户"这里可以输入"。
     *
     * 由布局引擎在每个**空行**处产出，渲染端应当用**弱化颜色**绘制 ——
     * 它是提示而非内容，不能盖过真正的公式。
     */
    data class Slot(
        val x: Double,
        val y: Double,
        val width: Double,
        val height: Double
    ) : DrawCmd
}

/** 二维点（em，基线坐标，y 向下为正）。 */
data class Point2(val x: Double, val y: Double)
