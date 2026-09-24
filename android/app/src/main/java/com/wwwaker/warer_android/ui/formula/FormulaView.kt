package com.wwwaker.warer_android.ui.formula

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.wwwaker.warer.core.formula.layout.FormulaLayout
import com.wwwaker.warer.core.formula.layout.LayoutContext
import com.wwwaker.warer.core.formula.model.Node

/**
 * 渲染一个公式 AST。
 *
 * 数据流：
 * ```
 * Node ──(ComposeFontMetrics)──▶ LayoutResult ──(drawFormula)──▶ Canvas
 * ```
 *
 * 组件尺寸由公式本身决定（按字号换算），因此布局结果完全由 `:core` 的排版算法给出。
 */
@Composable
fun FormulaView(
    node: Node,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 28.sp,
    color: Color = LocalContentColor.current,
    fontFamily: FontFamily = FontFamily.Serif
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val result = remember(node, fontSize, color, fontFamily, density) {
        val style = TextStyle(color = color, fontFamily = fontFamily)
        FormulaLayout.layoutFormula(
            node,
            LayoutContext(ComposeFontMetrics(measurer, density, fontSize, style))
        )
    }

    val fontSizePx = with(density) { fontSize.toPx() }
    val widthDp = with(density) { (result.width * fontSizePx).toFloat().toDp() }
    val heightDp = with(density) { ((result.ascent + result.descent) * fontSizePx).toFloat().toDp() }

    Canvas(modifier = modifier.size(widthDp, heightDp)) {
        drawFormula(result, measurer, fontSize, color)
    }
}
