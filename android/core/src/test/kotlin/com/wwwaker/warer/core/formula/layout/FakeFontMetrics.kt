package com.wwwaker.warer.core.formula.layout

/**
 * 可预测的字体度量：每个字符固定宽度，上伸 / 下延固定。
 *
 * 用它替代真实字体，使布局测试的期望值可以手算出来，从而**完全确定、与平台无关**。
 */
class FakeFontMetrics(
    private val charWidth: Double = 0.5,
    override val ascent: Double = 0.75,
    override val descent: Double = 0.25
) : FontMetrics {

    override fun width(text: String): Double = text.length * charWidth
}
