package com.wwwaker.warer.core.formula.convert

import com.wwwaker.warer.core.formula.model.BigOp
import com.wwwaker.warer.core.formula.model.BigOpKind
import com.wwwaker.warer.core.formula.model.Delim
import com.wwwaker.warer.core.formula.model.DelimKind
import com.wwwaker.warer.core.formula.model.Frac
import com.wwwaker.warer.core.formula.model.Func
import com.wwwaker.warer.core.formula.model.Node
import com.wwwaker.warer.core.formula.model.Num
import com.wwwaker.warer.core.formula.model.Row
import com.wwwaker.warer.core.formula.model.Script
import com.wwwaker.warer.core.formula.model.Sqrt
import com.wwwaker.warer.core.formula.model.Sym
import com.wwwaker.warer.core.formula.model.toPlainText

/**
 * 公式编译结果。
 *
 * @param command  交给计算引擎的命令文本（与既有后端 / 本地引擎语法一致）
 * @param warnings 编译过程中的提示；非空表示公式含有引擎暂时无法处理的构造，
 *                 UI 应当展示给用户，而不是静默丢弃信息
 */
data class CompiledFormula(
    val command: String,
    val warnings: List<String> = emptyList()
) {
    val hasWarnings: Boolean get() = warnings.isNotEmpty()
}

/**
 * 把二维公式 AST 编译成**一维命令文本**。
 *
 * 这是"公式编辑器"与"计算引擎"之间唯一的桥：编辑器只认 AST，引擎只认文本命令，
 * 二者通过本文件解耦——换引擎不需要动编辑器，换编辑器也不需要动引擎。
 *
 * 编译规则：
 *
 * | AST | 命令 |
 * |---|---|
 * | `Frac(1, 2)` | `(1)/(2)` |
 * | `Script(x, sup=2)` | `x^(2)` |
 * | `Script(sin(x), sup=2)` | `(sin(x))^(2)` |
 * | `Sqrt(2)` | `sqrt(2)` |
 * | `Sqrt(8, index=3)` | `(8)^(1/(3))` |
 * | `Delim(PAREN, x+1)` | `(x+1)` |
 * | `Delim(ABS, x)` | `abs(x)` |
 * | `Row[BigOp(∫,0,1), sin(x), d, x]` | `integrate(sin(x), x, 0, 1)` |
 * | `Row[BigOp(lim, x→0), sin(x)/x]` | `limit(sin(x)/x, x, 0)` |
 *
 * 注意 [BigOp] **不包含操作数**（见其文档），因此积分 / 极限必须结合所在 [Row] 的
 * 后续兄弟节点才能编译——这正是本文件存在的核心理由。
 */
object CommandBuilder {

    /** 编译整棵公式树。 */
    fun compile(root: Node): CompiledFormula {
        val warnings = mutableListOf<String>()
        val command = compileNode(root, warnings)
        return CompiledFormula(command = command.trim(), warnings = warnings.distinct())
    }

    // ============================ 分派 ============================

    private fun compileNode(node: Node, warnings: MutableList<String>): String = when (node) {
        is Sym -> node.ch.toString()
        is Num -> node.text
        is Func -> node.name
        is Row -> compileRow(node, warnings)
        is Frac -> "(${compileRow(node.numerator, warnings)})/(${compileRow(node.denominator, warnings)})"
        is Script -> compileScript(node, warnings)
        is Sqrt -> compileSqrt(node, warnings)
        is Delim -> compileDelim(node, warnings)
        is BigOp -> compileLoneBigOp(node, warnings)
    }

    // ============================ 行（含大算符吞噬） ============================

    private fun compileRow(row: Row, warnings: MutableList<String>): String {
        val out = StringBuilder()
        var index = 0

        while (index < row.children.size) {
            val child = row.children[index]
            if (child is BigOp) {
                // 大算符把右侧所有兄弟节点当作自己的操作数
                val operand = row.children.subList(index + 1, row.children.size).toList()
                out.append(compileBigOp(child, operand, warnings))
                index = row.children.size
            } else {
                out.append(compileNode(child, warnings))
                index++
            }
        }
        return out.toString()
    }

    private fun compileBigOp(op: BigOp, operand: List<Node>, warnings: MutableList<String>): String =
        when (op.kind) {
            BigOpKind.INTEGRAL -> compileIntegral(op, operand, warnings)
            BigOpKind.LIM -> compileLimit(op, operand, warnings)
            BigOpKind.SUM, BigOpKind.PRODUCT -> {
                warnings += "${op.kind.glyph} 暂不支持计算：当前引擎没有对应的命令"
                "${op.toPlainText()}(${compileRow(Row(operand), warnings)})"
            }
        }

    // ============================ 积分 ============================

    private fun compileIntegral(
        op: BigOp,
        operand: List<Node>,
        warnings: MutableList<String>
    ): String {
        val (body, variable) = splitDifferential(operand)
        // 剥离微分记号后被积函数可能残留空格，必须 trim，否则会拼出 `integrate(x^2 , x, …)`
        val integrand = compileRow(Row(body), warnings).trim().ifBlank { "1" }
        val varName = variable ?: "x"

        val lower = op.lower?.let { compileRow(it, warnings) }?.trim()?.takeIf { it.isNotBlank() }
        val upper = op.upper?.let { compileRow(it, warnings) }?.trim()?.takeIf { it.isNotBlank() }

        return when {
            lower != null && upper != null -> "integrate($integrand, $varName, $lower, $upper)"
            lower != null || upper != null -> {
                warnings += "积分缺少一端上下限，已按不定积分处理"
                "integrate($integrand, $varName)"
            }
            else -> "integrate($integrand, $varName)"
        }
    }

    /**
     * 从操作数尾部剥离微分记号 `d<变量>`（如 `… dx`、`… dt`）。
     *
     * 允许 `d` 与变量之间夹空格，也允许尾部有空格。
     *
     * @return 被积函数节点 + 积分变量名；未找到微分记号时变量名为 null
     */
    private fun splitDifferential(operand: List<Node>): Pair<List<Node>, String?> {
        val lastIndex = operand.indexOfLast { !isSpace(it) }
        if (lastIndex < 1) return operand to null

        val varName = variableName(operand[lastIndex]) ?: return operand to null

        var dIndex = lastIndex - 1
        while (dIndex >= 0 && isSpace(operand[dIndex])) dIndex--
        if (dIndex < 0) return operand to null

        val d = operand[dIndex]
        if (d !is Sym || (d.ch != 'd' && d.ch != 'D')) return operand to null

        return operand.subList(0, dIndex).toList() to varName
    }

    // ============================ 极限 ============================

    private fun compileLimit(
        op: BigOp,
        operand: List<Node>,
        warnings: MutableList<String>
    ): String {
        val body = compileRow(Row(operand), warnings).trim().ifBlank { "1" }
        val target = op.lower?.let { parseLimitTarget(it, warnings) }

        if (target == null) {
            warnings += "lim 缺少趋近目标，应形如 x→0"
            return "limit($body, x, 0)"
        }
        return "limit($body, ${target.first}, ${target.second})"
    }

    /** 解析 lim 的下限槽位，形如 `x→0`；返回 (变量名, 趋近值)。 */
    private fun parseLimitTarget(row: Row, warnings: MutableList<String>): Pair<String, String>? {
        val arrow = row.children.indexOfFirst { isArrow(it) }
        if (arrow <= 0 || arrow >= row.children.size - 1) return null

        val variable = compileRow(Row(row.children.subList(0, arrow).toList()), warnings).trim()
        val value = compileRow(Row(row.children.subList(arrow + 1, row.children.size).toList()), warnings).trim()
        if (variable.isEmpty() || value.isEmpty()) return null

        return variable to value
    }

    private fun isArrow(node: Node): Boolean = node is Sym && node.ch == '→'

    // ============================ 上下标 / 根号 / 括号 ============================

    private fun compileScript(node: Script, warnings: MutableList<String>): String {
        val base = compileRow(node.base, warnings)
        val superscript = node.superscript?.let { compileRow(it, warnings) }?.takeIf { it.isNotBlank() }
        val subscript = node.subscript?.let { compileRow(it, warnings) }?.takeIf { it.isNotBlank() }

        if (subscript != null) {
            warnings += "下标不参与计算，已被忽略"
        }
        if (superscript == null) return base

        // 底数不是单个字符时必须加括号，否则 sin(x)^2 的优先级会出错
        val safeBase = if (base.length == 1) base else "($base)"
        return "$safeBase^($superscript)"
    }

    private fun compileSqrt(node: Sqrt, warnings: MutableList<String>): String {
        val body = compileRow(node.radicand, warnings)
        val index = node.index?.let { compileRow(it, warnings) }?.takeIf { it.isNotBlank() }

        // n 次方根没有专门的命令语法，用等价的幂形式表达
        return if (index == null) "sqrt($body)" else "($body)^(1/($index))"
    }

    private fun compileDelim(node: Delim, warnings: MutableList<String>): String {
        val body = compileRow(node.content, warnings)
        return when (node.left) {
            DelimKind.ABS, DelimKind.NORM -> "abs($body)"
            DelimKind.FLOOR -> "floor($body)"
            DelimKind.CEIL -> "ceil($body)"
            DelimKind.BRACKET -> "[$body]"
            else -> "($body)"
        }
    }

    // ============================ 兜底 ============================

    /** 大算符不在行内（没有操作数）时的兜底。 */
    private fun compileLoneBigOp(op: BigOp, warnings: MutableList<String>): String {
        warnings += "${op.kind.glyph} 后面没有表达式，无法计算"
        return op.toPlainText()
    }

    // ============================ 工具 ============================

    private fun isSpace(node: Node): Boolean = node is Sym && node.ch == ' '

    private fun variableName(node: Node): String? =
        if (node is Sym && node.ch.isLetter()) node.ch.toString() else null
}
