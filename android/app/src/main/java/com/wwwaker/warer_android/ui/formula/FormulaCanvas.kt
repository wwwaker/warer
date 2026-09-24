package com.wwwaker.warer_android.ui.formula

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.TextUnit
import com.wwwaker.warer.core.formula.layout.DrawCmd
import com.wwwaker.warer.core.formula.layout.LayoutResult

/** 空槽位占位框的透明度：它是**提示**，不能盖过真正的公式内容。 */
private const val SLOT_ALPHA = 0.16f

/** 空槽位占位框的圆角（em）。 */
private const val SLOT_CORNER_RADIUS_EM = 0.07

/**
 * 把 `:core` 产出的 [LayoutResult] 画到任意 Canvas 上。
 *
 * 需要做两件换算：
 * 1. **单位**：core 用 em，这里乘以字号得到像素；
 * 2. **原点**：core 用"基线坐标"（y 向上为负），这里整体下移 [LayoutResult.ascent]，
 *    换算成常规的"左上角为原点"坐标。
 *
 * 之所以做成 `DrawScope` 扩展，是为了能嵌进任何已有的 Canvas / 自绘组件里。
 */
fun DrawScope.drawFormula(
    result: LayoutResult,
    measurer: TextMeasurer,
    fontSize: TextUnit,
    color: Color
) {
    val unit = fontSize.toPx()

    translate(top = (result.ascent * unit).toFloat()) {
        result.commands.forEach { cmd ->
            when (cmd) {
                is DrawCmd.Text -> {
                    val style = TextStyle(
                        color = color,
                        fontSize = fontSize * cmd.scale.toFloat()
                    )
                    val layout = measurer.measure(cmd.value, style)
                    drawText(
                        textLayoutResult = layout,
                        topLeft = Offset(
                            (cmd.x * unit).toFloat(),
                            (cmd.baselineY * unit).toFloat() - layout.firstBaseline
                        )
                    )
                }

                is DrawCmd.Rect -> drawRect(
                    color = color,
                    topLeft = Offset((cmd.x * unit).toFloat(), (cmd.y * unit).toFloat()),
                    size = Size((cmd.width * unit).toFloat(), (cmd.height * unit).toFloat())
                )

                is DrawCmd.Polyline -> {
                    val path = Path()
                    cmd.points.forEachIndexed { index, point ->
                        val px = (point.x * unit).toFloat()
                        val py = (point.y * unit).toFloat()
                        if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }
                    drawPath(
                        path = path,
                        color = color,
                        style = Stroke(
                            width = (cmd.thickness * unit).toFloat(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }

                is DrawCmd.Slot -> drawRoundRect(
                    // 弱化颜色：占位框是**提示**，不能盖过真正的公式内容
                    color = color.copy(alpha = SLOT_ALPHA),
                    topLeft = Offset((cmd.x * unit).toFloat(), (cmd.y * unit).toFloat()),
                    size = Size((cmd.width * unit).toFloat(), (cmd.height * unit).toFloat()),
                    cornerRadius = CornerRadius((SLOT_CORNER_RADIUS_EM * unit).toFloat())
                )
            }
        }
    }
}
