package com.wwwaker.warer.core.formula.convert

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

/** 遇到本转换器覆盖范围之外的 LaTeX 时抛出，调用方应降级为纯文本展示。 */
class UnsupportedLatexException(message: String) : IllegalArgumentException(message)

/** 常见的希腊字母命令 → 字形，符号解里会大量出现。 */
private val GREEK_GLYPHS: Map<String, Char> = buildMap {
    put("alpha", 'α'); put("beta", 'β'); put("gamma", 'γ'); put("delta", 'δ')
    put("epsilon", 'ε'); put("varepsilon", 'ε'); put("zeta", 'ζ'); put("eta", 'η')
    put("theta", 'θ'); put("vartheta", 'ϑ'); put("iota", 'ι'); put("kappa", 'κ')
    put("lambda", 'λ'); put("mu", 'μ'); put("nu", 'ν'); put("xi", 'ξ')
    put("rho", 'ρ'); put("sigma", 'σ'); put("varsigma", 'ς'); put("tau", 'τ')
    put("upsilon", 'υ'); put("phi", 'φ'); put("varphi", 'φ'); put("chi", 'χ')
    put("psi", 'ψ'); put("omega", 'ω')
    put("Gamma", 'Γ'); put("Delta", 'Δ'); put("Theta", 'Θ'); put("Lambda", 'Λ')
    put("Xi", 'Ξ'); put("Pi", 'Π'); put("Sigma", 'Σ'); put("Upsilon", 'Υ')
    put("Phi", 'Φ'); put("Psi", 'Ψ'); put("Omega", 'Ω')
}

/**
 * LaTeX → 公式 AST（[Node]）。
 *
 * **为什么必须存在**：后端符号引擎返回的是 LaTeX（`sympy.latex()`），
 * 而排版引擎吃的是 AST。若跳过这一步，符号结果只能显示成 `2*x + cos(x)`
 * 这种纯文本，体验明显掉档（见 docs/SESSION_SUMMARY.md 第 7 节 P0 风险）。
 *
 * **覆盖面**按实测的后端输出形态圈定（不是通用 LaTeX 解析器）：
 * 后端从不产出 `\cdot` / `*`，乘法一律是**并置空格**、除法一律是 `\frac`，
 * 因此子集比通用 LaTeX 小得多，可以精确覆盖：
 *
 * | LaTeX | AST |
 * |---|---|
 * | `\frac{a}{b}` | `Frac(a, b)` |
 * | `\sqrt{a}` / `\sqrt[n]{a}` | `Sqrt(a, index)` |
 * | `x^{2}` / `C_{1}` | `Script` |
 * | `\cos{\left(x \right)}` | `Row[Func("cos"), Row[Delim(PAREN, x)]]` |
 * | `\left(…\right)` `\left[…]` `\left\|…\right\|` `\left\{…\right\}` | `Delim` |
 * | `\pi` `\infty` `\emptyset` | `Sym('π')` / `Sym('∞')` / `Sym('∅')` |
 * | `\operatorname{erf}(x)` | `Row[Func("erf"), …]` |
 * | `2 x + \cos{\left(x \right)}` | `Row[Num(2), Sym(' '), Sym('x'), …]` |
 *
 * **不覆盖**：矩阵（`\begin{matrix}`）、多行环境。这些由 [isSupported] 报 false，
 * 调用方降级为 `plain_text` 展示 —— 真要渲染矩阵需要给 AST 增加 `Matrix` 节点
 * 并改造排版引擎，属于独立的一大块工作。
 */
object LatexToAst {

    /** 多行 / 矩阵环境，本转换器不支持。 */
    private val UNSUPPORTED_ENVIRONMENTS =
        Regex("""\\begin\{(matrix|pmatrix|bmatrix|vmatrix|array|cases|aligned|align)\}""")

    /** 该 LaTeX 能否被安全地转成 AST。 */
    fun isSupported(latex: String): Boolean = !UNSUPPORTED_ENVIRONMENTS.containsMatchIn(latex)

    /**
     * 解析 LaTeX。
     *
     * @throws UnsupportedLatexException 超出覆盖范围（含矩阵），或存在无法解析的残留
     */
    fun parse(latex: String): Row {
        if (!isSupported(latex)) {
            throw UnsupportedLatexException("暂不支持渲染该结果（包含矩阵等结构）")
        }
        val parser = Parser(latex)
        val row = parser.parseRow()
        if (!parser.atEnd()) {
            throw UnsupportedLatexException("无法完整解析 LaTeX，剩余：${parser.rest().take(24)}")
        }
        return row
    }

    // ============================ 解析器 ============================

    private class Parser(private val src: String) {
        private var pos = 0

        fun atEnd(): Boolean {
            skipSpaces()
            return pos >= src.length
        }

        fun rest(): String = src.substring(pos.coerceAtMost(src.length))

        /**
         * 解析一个水平序列，直到行尾 / `}` / `]` / `\right`。
         *
         * 并置的原子之间若在源码里有空白，会插入一个 [Sym] 空格节点。
         * 这一步不能省：后端用**并置空格**表示乘法，`x \cos{\left(x \right)}`
         * 若直接丢掉空格会被渲染成 `xcos(x)`，而 `2 x` 也会挤成 `2x`。
         * 行首的空白则丢弃，避免在括号/分式内产生无意义的缩进。
         */
        fun parseRow(
            stopAtRight: Boolean = false,
            stopAtBrace: Boolean = false,
            stopAtBracket: Boolean = false
        ): Row {
            val children = mutableListOf<Node>()

            while (true) {
                val hadSpace = skipSpaces()
                if (pos >= src.length) break
                if (stopAtBrace && src[pos] == '}') break
                if (stopAtBracket && src[pos] == ']') break
                if (stopAtRight && peekCommand() == "right") break

                val node = parseAtomWithScript() ?: continue

                if (hadSpace && children.isNotEmpty()) {
                    children.add(Sym(' '))
                }
                children.add(node)
            }
            return Row(children)
        }

        // ---------------------------- 原子 ----------------------------

        /** 解析一个原子，随后尝试吸收紧随其后的上下标。 */
        private fun parseAtomWithScript(): Node? {
            val base = parseAtom() ?: return null

            var superscript: Row? = null
            var subscript: Row? = null
            while (true) {
                // 探测上下标时必须**无损**：若这里把尾随空格吃掉又没找到 `^`/`_`，
                // 外层 parseRow 就再也看不到那段空白，`2 x` 会被并置成 `2x`。
                val mark = pos
                skipSpaces()
                when {
                    eat('^') -> superscript = parseScriptArgument()
                    eat('_') -> subscript = parseScriptArgument()
                    else -> {
                        pos = mark
                        break
                    }
                }
            }

            if (superscript == null && subscript == null) return base
            return Script(
                base = Row(listOf(base)),
                superscript = superscript,
                subscript = subscript
            )
        }

        /** 上下标的参数：`{…}` 分组，或单个原子（`x^2` 这种省略花括号的写法）。 */
        private fun parseScriptArgument(): Row {
            skipSpaces()
            if (pos < src.length && src[pos] == '{') {
                return parseGroup()
            }
            val atom = parseAtom() ?: return Row()
            return Row(listOf(atom))
        }

        private fun parseAtom(): Node? {
            skipSpaces()
            if (pos >= src.length) return null

            val c = src[pos]
            return when {
                c == '\\' -> parseCommand(readCommand())
                c == '{' -> parseGroup()
                c.isDigit() || c == '.' -> Num(readNumber())
                c.isLetter() -> {
                    pos++
                    Sym(c)
                }
                else -> {
                    pos++
                    parseSymbolChar(c)
                }
            }
        }

        // ---------------------------- 命令 ----------------------------

        private fun parseCommand(name: String): Node? = when (name) {
            "frac" -> Frac(parseGroup(), parseGroup())
            "sqrt" -> parseSqrt()
            "left" -> parseLeftRight()
            "operatorname" -> Func(readGroupText())

            "pi" -> Sym('π')
            "infty", "infinity" -> Sym('∞')
            "emptyset", "varnothing" -> Sym('∅')

            "cdot" -> Sym('·')
            "times" -> Sym('×')
            "div" -> Sym('÷')
            "pm" -> Sym('±')
            "to", "rightarrow", "longrightarrow" -> Sym('→')

            in GREEK_GLYPHS -> Sym(GREEK_GLYPHS.getValue(name))

            "sin", "cos", "tan", "cot", "sec", "csc",
            "sinh", "cosh", "tanh",
            "arcsin", "arccos", "arctan",
            "log", "ln", "exp", "erf", "erfc" -> Func(name)

            // 间距 / 无输出的命令，直接跳过
            " ", ",", ";", "!", ":", "quad", "qquad" -> null

            "begin", "end" -> throw UnsupportedLatexException("暂不支持渲染多行 / 矩阵环境")

            else -> throw UnsupportedLatexException("未知的 LaTeX 命令：\\$name")
        }

        private fun parseSqrt(): Node {
            skipSpaces()
            val index = if (pos < src.length && src[pos] == '[') {
                pos++
                val row = parseRow(stopAtBracket = true)
                expect(']')
                row
            } else {
                null
            }
            return Sqrt(radicand = parseGroup(), index = index)
        }

        private fun parseLeftRight(): Node {
            val left = parseDelimiterKind()
            val content = parseRow(stopAtRight = true)

            skipSpaces()
            if (peekCommand() != "right") {
                throw UnsupportedLatexException("\\left 缺少配对的 \\right")
            }
            readCommand() // 消费 \right

            val right = parseDelimiterKind()
            return Delim(left = left, content = content, right = right)
        }

        private fun parseDelimiterKind(): DelimKind {
            skipSpaces()
            if (pos >= src.length) throw UnsupportedLatexException("定界符缺失")

            return when (val c = src[pos]) {
                '(' -> { pos++; DelimKind.PAREN }
                ')' -> { pos++; DelimKind.PAREN }
                '[' -> { pos++; DelimKind.BRACKET }
                ']' -> { pos++; DelimKind.BRACKET }
                '|' -> { pos++; DelimKind.ABS }
                '{' -> { pos++; DelimKind.BRACE }
                '}' -> { pos++; DelimKind.BRACE }
                '.' -> { pos++; DelimKind.NONE }
                '\\' -> when (val cmd = readCommand()) {
                    "{" -> DelimKind.BRACE
                    "}" -> DelimKind.BRACE
                    "|" -> DelimKind.NORM
                    "lfloor" -> DelimKind.FLOOR
                    "rfloor" -> DelimKind.FLOOR
                    "lceil" -> DelimKind.CEIL
                    "rceil" -> DelimKind.CEIL
                    "langle" -> DelimKind.ANGLE
                    "rangle" -> DelimKind.ANGLE
                    else -> throw UnsupportedLatexException("未知定界符：\\$cmd")
                }
                else -> throw UnsupportedLatexException("未知定界符：$c")
            }
        }

        // ---------------------------- 分组 ----------------------------

        /** `{ … }` 分组，返回其中的水平序列。 */
        private fun parseGroup(): Row {
            skipSpaces()
            if (pos >= src.length || src[pos] != '{') {
                throw UnsupportedLatexException("期望 '{'，实际：${rest().take(16)}")
            }
            pos++
            val row = parseRow(stopAtBrace = true)
            expect('}')
            return row
        }

        /** 读取 `{ … }` 并原样返回其中文本（用于 `\operatorname{erf}`）。 */
        private fun readGroupText(): String {
            skipSpaces()
            if (pos >= src.length || src[pos] != '{') {
                throw UnsupportedLatexException("期望 '{'，实际：${rest().take(16)}")
            }
            pos++
            val start = pos
            var depth = 1
            while (pos < src.length) {
                when (src[pos]) {
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            val text = src.substring(start, pos)
                            pos++
                            return text
                        }
                    }
                }
                pos++
            }
            throw UnsupportedLatexException("未闭合的 '{'")
        }

        // ---------------------------- 基础工具 ----------------------------

        private fun parseSymbolChar(c: Char): Node = when (c) {
            '+', '-', '=', '<', '>', ':', ',', '(', ')', '[', ']', '|', '/', ';' -> Sym(c)
            else -> throw UnsupportedLatexException("无法处理的字符：$c")
        }

        private fun readNumber(): String {
            val start = pos
            while (pos < src.length && (src[pos].isDigit() || src[pos] == '.')) pos++
            return src.substring(start, pos)
        }

        /** 跳过空白；返回是否真的跳过了至少一个空白字符。 */
        private fun skipSpaces(): Boolean {
            val start = pos
            while (pos < src.length && src[pos].isWhitespace()) pos++
            return pos > start
        }

        private fun eat(c: Char): Boolean {
            if (pos < src.length && src[pos] == c) {
                pos++
                return true
            }
            return false
        }

        private fun expect(c: Char) {
            skipSpaces()
            if (pos >= src.length || src[pos] != c) {
                throw UnsupportedLatexException("期望 '$c'，实际：${rest().take(16)}")
            }
            pos++
        }

        /** 读取 `\foo` / `\ ` / `\\` / `\{` 这类命令，返回命令名。 */
        private fun readCommand(): String {
            // 调用前已确认 src[pos] == '\\'
            pos++
            if (pos >= src.length) return ""
            val c = src[pos]
            if (c.isLetter()) {
                val start = pos
                while (pos < src.length && src[pos].isLetter()) pos++
                return src.substring(start, pos)
            }
            pos++
            return c.toString()
        }

        /** 非消耗地窥探下一个命令名；不是命令时返回 null。 */
        private fun peekCommand(): String? {
            if (pos >= src.length || src[pos] != '\\') return null
            val start = pos + 1
            if (start >= src.length) return ""
            val c = src[start]
            if (c.isLetter()) {
                var i = start
                while (i < src.length && src[i].isLetter()) i++
                return src.substring(start, i)
            }
            return c.toString()
        }
    }
}
