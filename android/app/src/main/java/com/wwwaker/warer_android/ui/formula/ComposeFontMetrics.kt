package com.wwwaker.warer_android.ui.formula

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import com.wwwaker.warer.core.formula.layout.FontMetrics

/**
 * 用 Compose 的 [TextMeasurer] 实现 `:core` 布局引擎所需的 [FontMetrics]。
 *
 * **单位换算约定**：`:core` 里的 `1em` = 这里的 [fontSize]，
 * 因此所有度量结果都除以字号像素值换算成 em 返回。
 *
 * 之所以要做这层适配，是为了让 `:core` 保持"零 Android 依赖"——
 * 排版算法完全不知道字体的存在，只知道"某串文本宽 0.5em"。
 *
 * @param fontSize  真实绘制字号。**度量必须使用同一个字号**，因此本类不接受
 *   "自带字号的样式"而是强制写入 —— 早期版本由调用方在 [TextStyle] 里传字号，
 *   一旦漏传就会按默认 14sp 度量、再按真实字号换算成 em，所有度量值整体缩小若干倍，
 *   表现为分数线过窄、上下间距错乱。这类单位错配单元测试发现不了，只能在真机上暴露，
 *   所以这里从结构上消除它。
 * @param baseStyle 只提供颜色 / 字体族等与字号无关的属性。
 */
class ComposeFontMetrics(
    private val measurer: TextMeasurer,
    density: Density,
    fontSize: TextUnit,
    baseStyle: TextStyle
) : FontMetrics {

    /** 度量样式：字号由本类强制指定。 */
    private val style: TextStyle = baseStyle.copy(fontSize = fontSize)

    private val fontSizePx: Float = with(density) { fontSize.toPx() }

    private val widthCache = HashMap<String, Double>()

    override fun width(text: String): Double {
        if (text.isEmpty()) return 0.0
        return widthCache.getOrPut(text) {
            measurer.measure(text, style).size.width.toDouble() / fontSizePx
        }
    }

    /** 用 "Ag" 作探针：'A' 提供上伸部，'g' 提供下延部。 */
    private val probe by lazy { measurer.measure("Ag", style) }

    override val ascent: Double
        get() = probe.firstBaseline.toDouble() / fontSizePx

    override val descent: Double
        get() = (probe.size.height - probe.firstBaseline).toDouble() / fontSizePx
}
