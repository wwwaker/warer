package com.wwwaker.warer_android.ui.formula

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.wwwaker.warer.core.formula.edit.Caret
import com.wwwaker.warer.core.formula.edit.EditorState
import com.wwwaker.warer.core.formula.edit.hitTestCaret
import com.wwwaker.warer.core.formula.edit.locateCaret
import com.wwwaker.warer.core.formula.layout.FormulaLayout
import com.wwwaker.warer.core.formula.layout.LayoutContext
import com.wwwaker.warer.core.formula.layout.Point2

/**
 * 公式编辑器视图：渲染 AST、在光标处画一条竖线，并支持**点击定位**。
 *
 * 光标几何不是"猜"出来的 —— 它由 `:core` 在布局过程中记录
 * （见 `CaretProbe` / `CaretMap`），因此与排版天然一致，改排版常量不会让它跑偏。
 *
 * @param onCaretChange 用户点击公式时回调新的光标位置
 */
@Composable
fun FormulaEditorView(
    state: EditorState,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 32.sp,
    color: Color = LocalContentColor.current,
    caretColor: Color = MaterialTheme.colorScheme.primary,
    fontFamily: FontFamily = FontFamily.Serif,
    onCaretChange: (Caret) -> Unit = {}
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = remember(color, fontFamily) {
        TextStyle(color = color, fontFamily = fontFamily)
    }

    val fontSizePx = with(density) { fontSize.toPx() }
    val metrics = remember(measurer, density, fontSize, style) {
        ComposeFontMetrics(measurer, density, fontSize, style)
    }
    val baseCtx = remember(metrics) { LayoutContext(metrics) }

    val layout = remember(state.root, baseCtx) {
        FormulaLayout.layoutFormula(state.root, baseCtx)
    }
    val anchor = remember(state.root, state.caret, baseCtx) {
        locateCaret(state.root, state.caret, baseCtx)
    }

    // 空公式时也要留出可见、可点的空间
    val widthEm = maxOf(layout.width, MIN_WIDTH_EM)
    val heightEm = maxOf(layout.ascent + layout.descent, MIN_HEIGHT_EM)

    val widthDp = with(density) { (widthEm * fontSizePx).toFloat().toDp() }
    val heightDp = with(density) { (heightEm * fontSizePx).toFloat().toDp() }

    Canvas(
        modifier = modifier
            .size(widthDp, heightDp)
            .pointerInput(state.root, fontSizePx, baseCtx) {
                detectTapGestures { offset ->
                    // 屏幕像素 → 公式坐标系：em、基线原点、y 向下为正。
                    // 触摸坐标是 Float、排版几何是 Double，这里统一到 Double 再算。
                    val unitPx = fontSizePx.toDouble()
                    val emX = offset.x / unitPx
                    val emY = offset.y / unitPx - layout.ascent
                    hitTestCaret(state.root, Point2(emX, emY), baseCtx)?.let(onCaretChange)
                }
            }
    ) {
        val unit = fontSize.toPx()
        drawFormula(layout, measurer, fontSize, color)

        if (anchor != null) {
            // 行本身可能很矮（例如空行），给光标一个最小高度，否则会看不见
            val ascent = maxOf(anchor.ascent, MIN_CARET_ASCENT_EM)
            val descent = maxOf(anchor.descent, MIN_CARET_DESCENT_EM)
            translate(top = (layout.ascent * unit).toFloat()) {
                val caretX = (anchor.x * unit).toFloat()
                drawLine(
                    color = caretColor,
                    start = Offset(caretX, ((anchor.baselineY - ascent) * unit).toFloat()),
                    end = Offset(caretX, ((anchor.baselineY + descent) * unit).toFloat()),
                    strokeWidth = (CARET_WIDTH_EM * unit).toFloat(),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

private const val MIN_WIDTH_EM = 0.6
private const val MIN_HEIGHT_EM = 1.5
private const val MIN_CARET_ASCENT_EM = 0.65
private const val MIN_CARET_DESCENT_EM = 0.15
private const val CARET_WIDTH_EM = 0.06
