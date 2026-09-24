package com.wwwaker.warer.core.formula.layout

/**
 * 字体度量回调 —— 布局引擎与渲染框架之间的唯一接缝。
 *
 * `:core` 模块不含 Compose / Android API，因此"量一个字有多宽"这件事必须由渲染端提供：
 * - App 侧：用 Compose 的 `TextMeasurer` 实现；
 * - 单元测试：用可预测的假实现（见 `FakeFontMetrics`）。
 *
 * **单位约定**：本模块所有长度一律使用 **em**（即字号倍数），与坐标系统保持一致。
 */
interface FontMetrics {

    /** 返回字符串在 1em 字号下的宽度（em）。字号缩放由调用方自行乘算。 */
    fun width(text: String): Double

    /** 字体上伸部高度（em）：如 `A`、`1` 高于基线的高度。 */
    val ascent: Double

    /** 字体下延部深度（em）：如 `g`、`y` 低于基线的深度。 */
    val descent: Double
}
