package com.wwwaker.warer.core.engine

import org.mariuszgromada.math.mxparser.Expression

/**
 * 本地数值计算的结果。
 *
 * @param numericValue 数值结果；矩阵等非标量结果时为 null
 * @param plainText    给用户看的文本形式（分数会智能还原为 1/2 这类写法）
 */
data class NumericResult(
    val numericValue: Double?,
    val plainText: String
)

/**
 * Tier 0 —— 本地数值引擎。
 *
 * 当前是旧 Android 端 `CalculatorViewModel.computeLocal()` 及其两个辅助函数的**原样搬运**
 * （`preprocessImplicitMultiplication` / `smartRound`），目的是保持行为 100% 一致，
 * 由单元测试逐条锁定。后续再替换为更强的分层实现。
 *
 * 依赖：mXparser（纯 Java，无 Android 依赖）。
 */
object LocalNumericEngine {

    /**
     * 尝试本地求值。返回 null 表示本地无法得出有限数值结果（应交给上层降级处理）。
     */
    fun compute(input: String): NumericResult? {
        return try {
            // 注意：**不要**把 `^` 换成 `**`。
            // mXparser 的幂运算符就是 `^`，换成 `**` 会让它解析失败 ——
            // 表现为"任何带指数的表达式都算不出来"（真机上就是这么错的）。
            // 这段替换是从 Web 端（mathjs）移植时带过来的遗留，mathjs 两种写法都支持，
            // 但 mXparser 只认 `^`。
            val preprocessed = input
                .replace(Regex("""^[yY]\s*=\s*"""), "")
                .replace(Regex("""\bln\b""", RegexOption.IGNORE_CASE), "log")
                .replace("π", "pi")

            val withImplicitMul = preprocessImplicitMultiplication(preprocessed)

            val expr = Expression(withImplicitMul)
            val result = expr.calculate()

            if (result.isFinite()) {
                val rounded = smartRound(result)
                NumericResult(result, rounded)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /** 在 数字/变量/括号 相邻处补上隐式乘号：`2x` → `2*x`、`(x+1)(x-1)` → `(x+1)*(x-1)`。 */
    internal fun preprocessImplicitMultiplication(input: String): String {
        data class Token(val type: String, val value: String)

        val tokens = mutableListOf<Token>()
        var i = 0
        val s = input

        while (i < s.length) {
            val ch = s[i]
            when {
                ch.isDigit() || ch == '.' -> {
                    var num = ""
                    while (i < s.length && (s[i].isDigit() || s[i] == '.')) { num += s[i++] }
                    tokens.add(Token("num", num))
                }
                ch.isLetter() -> {
                    var name = ""
                    while (i < s.length && s[i].isLetter()) { name += s[i++] }
                    val knownFuncs = setOf(
                        "sin", "cos", "tan", "ln", "log", "exp", "sqrt", "abs",
                        "asin", "acos", "atan", "sinh", "cosh", "tanh",
                        "ceil", "floor", "round"
                    )
                    tokens.add(
                        if (knownFuncs.contains(name)) Token("func", name)
                        else Token("var", name)
                    )
                }
                ch == '(' -> { tokens.add(Token("lparen", "(")); i++ }
                ch == ')' -> { tokens.add(Token("rparen", ")")); i++ }
                else -> { tokens.add(Token("op", ch.toString())); i++ }
            }
        }

        val result = StringBuilder()
        for (j in tokens.indices) {
            val cur = tokens[j]
            val prev = if (j > 0) tokens[j - 1] else null

            if (prev != null) {
                val needMul =
                    (prev.type == "num" && (cur.type == "var" || cur.type == "lparen" || cur.type == "func")) ||
                    (prev.type == "rparen" && (cur.type == "var" || cur.type == "num" || cur.type == "lparen" || cur.type == "func")) ||
                    (prev.type == "var" && (cur.type == "lparen" || cur.type == "num" || cur.type == "func"))
                if (needMul) result.append('*')
            }

            result.append(cur.value)
        }

        return result.toString()
    }

    /** 把浮点结果智能还原为整数或常见分数（1/2、1/3 …），避免出现 0.3333333333333333。 */
    internal fun smartRound(value: Double, tolerance: Double = 1e-10): String {
        val nearestInt = kotlin.math.round(value)
        if (kotlin.math.abs(value - nearestInt) < tolerance) return nearestInt.toLong().toString()

        val commonFractions = listOf(
            0 to 1,
            1 to 6, 1 to 4, 1 to 3, 1 to 2, 2 to 3, 3 to 4, 5 to 6,
            1 to 8, 3 to 8, 5 to 8, 7 to 8,
            1 to 12, 5 to 12, 7 to 12, 11 to 12
        )
        for ((num, den) in commonFractions) {
            val fractionValue = num.toDouble() / den
            if (kotlin.math.abs(value - fractionValue) < tolerance) {
                return if (num == 0) "0" else if (den == 1) num.toString() else "$num/$den"
            }
            if (kotlin.math.abs(value + fractionValue) < tolerance) {
                return if (num == 0) "0" else if (den == 1) (-num).toString() else "-$num/$den"
            }
        }

        var bestNum = 1; var bestDen = 1
        var bestError = kotlin.math.abs(value - kotlin.math.round(value))
        for (den in 2..20) {
            val num = kotlin.math.round(value * den).toInt()
            val error = kotlin.math.abs(value - num.toDouble() / den)
            if (error < bestError) {
                bestError = error; bestNum = num; bestDen = den
            }
        }
        if (bestError < tolerance * 100 && bestDen <= 20) {
            return if (bestDen == 1) bestNum.toString() else "$bestNum/$bestDen"
        }

        return value.toBigDecimal().stripTrailingZeros().toPlainString()
    }
}
