package com.wwwaker.warer_android.ui.formula

import com.wwwaker.warer.core.engine.ComputeOutcome
import com.wwwaker.warer.core.engine.FailureKind
import com.wwwaker.warer.core.engine.NumericResult
import com.wwwaker.warer.core.formula.convert.SymbolicDisplay
import com.wwwaker.warer.core.formula.convert.SymbolicResultDisplay
import com.wwwaker.warer.core.formula.model.Node
import java.math.BigDecimal
import java.math.MathContext

/**
 * 一次计算的展示模型。
 *
 * @param label    结果类型（数值解 / 符号解 / 精确解）或失败原因
 * @param value    文本形式；[rendered] 为空时直接展示它
 * @param isError  是否按错误样式展示
 * @param rendered 符号结果转成的公式 AST；非空时**渲染成真正的公式**而不是一串文本。
 *                 转不动的（例如矩阵）留空。
 */
data class EditorOutcome(
    val label: String,
    val value: String,
    val isError: Boolean = false,
    val rendered: Node? = null
)

/**
 * 把调度结果翻译成展示模型。
 *
 * **关键：结果类型必须显式标注，失败原因必须各不相同**——
 * 用户需要知道拿到的是"数值解 / 符号解 / 精确解"，以及失败时到底是
 * "表达式写错了"还是"这功能需要联网"。
 */
fun ComputeOutcome.toEditorOutcome(): EditorOutcome = when (this) {
    is ComputeOutcome.LocalNumeric -> EditorOutcome("数值解", formatResult(result))

    is ComputeOutcome.CloudSymbolic -> {
        val label = if (result.isSymbolic) "符号解" else "精确解"
        when (val display = SymbolicResultDisplay.of(result.latex, result.plainText)) {
            is SymbolicDisplay.Formula ->
                EditorOutcome(label, display.plainText, rendered = display.node)
            is SymbolicDisplay.Plain -> EditorOutcome(label, display.plainText)
        }
    }

    is ComputeOutcome.Failure -> EditorOutcome(kind.label(), message, isError = true)
}

private fun FailureKind.label(): String = when (this) {
    FailureKind.EMPTY -> "提示"
    FailureKind.LOCAL_FAILED -> "无法计算"
    FailureKind.CLOUD_UNAVAILABLE -> "需要云端符号引擎"
    FailureKind.CLOUD_REJECTED -> "云端返回错误"
    FailureKind.CLOUD_UNREACHABLE -> "云端不可用"
}

/**
 * 结果展示规则：
 * - **精确形式更短**（`14`、`1/2`）→ 展示精确形式，再补一个小数近似帮助判断量级；
 * - **原始值更长**（`1/23`、`√2` 的 17 位 double）→ 只展示 10 位有效数字。
 *
 * 后者很重要：否则会把 `0.04347826086956522 ≈ 0.04347826087` 整行丢给用户，
 * 既不精确（后面那串数字都是噪声）又难读。
 */
fun formatResult(result: NumericResult): String {
    val exact = result.plainText
    val rounded = result.numericValue?.let {
        BigDecimal(it).round(MathContext(10)).stripTrailingZeros().toPlainString()
    } ?: return exact

    return when {
        exact == rounded -> exact
        exact.length <= rounded.length -> "$exact   ≈ $rounded"
        else -> rounded
    }
}
