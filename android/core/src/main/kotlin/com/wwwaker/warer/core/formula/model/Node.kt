package com.wwwaker.warer.core.formula.model

/**
 * 公式的抽象语法树（AST）。
 *
 * 三条设计原则：
 * 1. **不可变**（全部 `val`）→ 撤销 / 重做可用"根节点快照栈"实现，几乎零成本；
 * 2. **[Row] 是唯一的容器类型** → 所有可编辑上下文（分子、分母、根号内、上下标、括号内）都是 [Row]，
 *    光标模型因此只需处理一种容器；
 * 3. **不含任何渲染信息** → 布局完全由 `core/formula/layout` 从 AST 推导，二者互不影响。
 */
sealed interface Node

/** 水平序列：唯一的容器，也是光标所在的最小上下文。 */
data class Row(val children: List<Node> = emptyList()) : Node

/** 单字符：变量、运算符、括号等。 */
data class Sym(val ch: Char) : Node

/** 数字串：整体参与光标移动与删除，不按字符拆开。 */
data class Num(val text: String) : Node

/** 函数名（sin / cos / log …）。 */
data class Func(val name: String) : Node

/** 分式：分子 / 分数线 / 分母。 */
data class Frac(val numerator: Row, val denominator: Row) : Node

/** 上下标：`base^sup`、`base_sub` 或 `base_sub^sup`。 */
data class Script(
    val base: Row,
    val superscript: Row? = null,
    val subscript: Row? = null
) : Node

/** 根号；[index] 非空时表示 n 次方根。 */
data class Sqrt(val radicand: Row, val index: Row? = null) : Node

/** 伸缩括号的种类。[pathWidth] 是以 em 为单位的矢量宽度（内容较高时使用）。 */
enum class DelimKind(
    val leftChar: Char,
    val rightChar: Char,
    val pathWidth: Double
) {
    /** 不绘制该侧（用于 `{` 这类单侧符号）。 */
    NONE(' ', ' ', 0.0),
    PAREN('(', ')', 0.45),
    BRACKET('[', ']', 0.40),
    BRACE('{', '}', 0.42),
    ABS('|', '|', 0.12),
    NORM('‖', '‖', 0.30),
    FLOOR('⌊', '⌋', 0.42),
    CEIL('⌈', '⌉', 0.42),
    ANGLE('⟨', '⟩', 0.45)
}

/** 括号：内容较矮时用字体字形，较高时改用矢量路径（可伸缩）。 */
data class Delim(
    val left: DelimKind,
    val content: Row,
    val right: DelimKind
) : Node

/** 大算符的上下限摆放方式。 */
enum class LimitsPlacement {
    /** 上下限放在算符正上 / 正下（∑ ∏）。 */
    ABOVE_BELOW,

    /** 上下限放在算符右上方 / 右下方（∫）。 */
    SIDE,

    /** 只有下限放在正下方（lim）。 */
    BELOW
}

/** 大算符的种类。[scale] 是字形相对字号的缩放倍数。 */
enum class BigOpKind(
    val glyph: String,
    val limits: LimitsPlacement,
    val scale: Double
) {
    INTEGRAL("∫", LimitsPlacement.SIDE, 1.8),
    SUM("∑", LimitsPlacement.ABOVE_BELOW, 1.6),
    PRODUCT("∏", LimitsPlacement.ABOVE_BELOW, 1.6),
    LIM("lim", LimitsPlacement.BELOW, 1.0)
}

/**
 * 大算符及其上下限。
 *
 * 注意：算符**不包含操作数**——`∫₀¹ sin(x)dx` 应表示为
 * `Row[ BigOp(INTEGRAL, 0, 1), sin(x), d, x ]`，操作数作为同级节点跟随。
 */
data class BigOp(
    val kind: BigOpKind,
    val lower: Row? = null,
    val upper: Row? = null
) : Node

/**
 * 把 AST 还原为可交给计算引擎的**纯文本形式**，与既有后端 / 本地引擎的语法保持一致。
 *
 * 例：
 * - `Frac(1, 2)`          → `(1)/(2)`
 * - `Script(x, sup=2)`    → `x^(2)`
 * - `Sqrt(2)`             → `sqrt(2)`
 * - `Delim(PAREN, x+1)`   → `(x+1)`
 *
 * 说明：`BigOp` 目前输出为可读的算符形式（如 `∑_(0)^(n)`），
 * 转换成 `integrate(...)` 这类引擎语法需要结合其所在的 `Row` 上下文，属于后续 `convert` 的职责。
 */
fun Node.toPlainText(): String = when (this) {
    is Sym -> ch.toString()
    is Num -> text
    is Func -> name
    is Row -> children.joinToString("") { it.toPlainText() }
    is Frac -> "(${numerator.toPlainText()})/(${denominator.toPlainText()})"
    is Script -> buildString {
        append(base.toPlainText())
        // 按数学书写习惯：先下标、后上标（x_1^2）
        subscript?.let { append("_(").append(it.toPlainText()).append(')') }
        superscript?.let { append("^(").append(it.toPlainText()).append(')') }
    }
    is Sqrt -> "sqrt(${radicand.toPlainText()})"
    is Delim -> {
        val body = content.toPlainText()
        when (left) {
            DelimKind.ABS, DelimKind.NORM -> "abs($body)"
            DelimKind.FLOOR -> "floor($body)"
            DelimKind.CEIL -> "ceil($body)"
            DelimKind.BRACKET -> "[$body]"
            else -> "($body)"
        }
    }
    is BigOp -> buildString {
        append(kind.glyph)
        lower?.let { append("_(").append(it.toPlainText()).append(')') }
        upper?.let { append("^(").append(it.toPlainText()).append(')') }
    }
}
