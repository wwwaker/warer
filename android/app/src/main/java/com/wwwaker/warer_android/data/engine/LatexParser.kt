package com.wwwaker.warer_android.data.engine

sealed class LToken {
    data class Number(val value: String) : LToken()
    data class Variable(val value: String) : LToken()
    data class Operator(val value: String) : LToken()
    data class Func(val value: String) : LToken()
    data object LParen : LToken()
    data object RParen : LToken()
    data object LSq : LToken()
    data object RSq : LToken()
    data object Comma : LToken()
    data object Caret : LToken()
    data object Slash : LToken()
}

private val KNOWN_FUNCS = setOf(
    "sin", "cos", "tan", "asin", "acos", "atan",
    "sinh", "cosh", "tanh",
    "ln", "log", "exp", "sqrt",
    "abs", "ceil", "floor", "round",
    "diff", "derivative", "integrate", "int",
    "simplify", "solve", "nsolve", "dsolve", "linsolve",
    "limit", "series", "taylor",
    "det", "inv", "inverse", "transpose", "eigenvals", "eigenvects", "rank"
)

private fun tokenize(input: String): List<LToken> {
    val tokens = mutableListOf<LToken>()
    var i = 0
    val s = input.replace(Regex("""^[yY]\s*=\s*"""), "")

    while (i < s.length) {
        val ch = s[i]

        if (ch.isWhitespace()) { i++; continue }

        if (ch.isDigit() || ch == '.') {
            val num = StringBuilder()
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) { num.append(s[i++]) }
            tokens.add(LToken.Number(num.toString()))
            continue
        }

        if (ch.isLetter()) {
            val name = StringBuilder()
            while (i < s.length && s[i].isLetter()) { name.append(s[i++]) }
            val nameStr = name.toString()
            if (KNOWN_FUNCS.contains(nameStr)) {
                tokens.add(LToken.Func(nameStr))
            } else if (nameStr == "pi") {
                tokens.add(LToken.Variable("\\pi"))
            } else if (nameStr == "theta") {
                tokens.add(LToken.Variable("\\theta"))
            } else if (nameStr == "i" || nameStr == "j") {
                tokens.add(LToken.Variable("i"))
            } else if (nameStr == "e" && (i >= s.length || !s[i].isLetter())) {
                tokens.add(LToken.Variable("e"))
            } else {
                tokens.add(LToken.Variable(nameStr))
            }
            continue
        }

        when (ch) {
            '^' -> { tokens.add(LToken.Caret); i++; continue }
            '/' -> { tokens.add(LToken.Slash); i++; continue }
            '(' -> { tokens.add(LToken.LParen); i++; continue }
            ')' -> { tokens.add(LToken.RParen); i++; continue }
            '[' -> { tokens.add(LToken.LSq); i++; continue }
            ']' -> { tokens.add(LToken.RSq); i++; continue }
            ',' -> { tokens.add(LToken.Comma); i++; continue }
            '*' -> {
                if (i + 1 < s.length && s[i + 1] == '*') {
                    tokens.add(LToken.Caret)
                    i += 2
                    if (i < s.length && s[i] == '*') {
                        tokens.add(LToken.Operator("\\cdot "))
                        i++
                    }
                    continue
                }
                tokens.add(LToken.Operator("\\cdot "))
                i++; continue
            }
            '·' -> { tokens.add(LToken.Operator("\\cdot ")); i++; continue }
            '+' -> { tokens.add(LToken.Operator("+")); i++; continue }
            '-' -> { tokens.add(LToken.Operator("-")); i++; continue }
        }
        i++
    }

    return tokens
}

private fun insertImplicitMultiplication(tokens: List<LToken>): List<LToken> {
    if (tokens.size <= 1) return tokens
    val result = mutableListOf(tokens[0])

    for (i in 1 until tokens.size) {
        val prev = tokens[i - 1]
        val cur = tokens[i]

        val needMul = when {
            prev is LToken.Number && (cur is LToken.Variable || cur is LToken.LParen || cur is LToken.Func) -> true
            prev is LToken.RParen && (cur is LToken.Variable || cur is LToken.Number || cur is LToken.LParen || cur is LToken.Func) -> true
            prev is LToken.Variable && (cur is LToken.LParen || cur is LToken.Number || cur is LToken.Func) -> true
            else -> false
        }

        if (needMul) {
            result.add(LToken.Operator("\\cdot "))
        }
        result.add(cur)
    }

    return result
}

private class LParser(private val tokens: List<LToken>) {
    private var pos = 0
    var errorMessage: String? = null

    private fun peek(): LToken? = if (pos < tokens.size) tokens[pos] else null

    private fun advance(): LToken? = if (pos < tokens.size) tokens[pos++] else null

    fun parse(): String = parseExpression()

    private fun parseExpression(): String {
        val parts = mutableListOf<String>()
        parts.add(parseTerm())

        while (true) {
            val op = peek()
            if (op !is LToken.Operator || op.value == "\\cdot ") break
            advance()
            val right = parseTerm()
            parts.add(" ${op.value} $right")
        }

        return parts.joinToString("")
    }

    private fun parseTerm(): String {
        var result = parsePower()

        while (true) {
            when (val next = peek()) {
                is LToken.Operator -> {
                    if (next.value == "\\cdot ") {
                        advance()
                        val right = parsePower()
                        result = "$result \\cdot $right"
                    } else break
                }
                is LToken.Slash -> {
                    advance()
                    val denominator = parsePower()
                    result = "\\frac{$result}{$denominator}"
                }
                else -> break
            }
        }

        return result
    }

    private fun parsePower(): String {
        val base = parseUnary()

        if (peek() is LToken.Caret) {
            advance()
            val next = peek()
            if (next is LToken.Operator || next is LToken.Slash || next is LToken.Comma || next is LToken.RParen || next is LToken.RSq || next == null) {
                if (errorMessage == null) {
                    errorMessage = "表达式不完整：'$base ^' 后面缺少指数"
                }
                return "$base^{\\square}"
            }
            val exp = parsePower()
            if (exp.isEmpty()) {
                if (errorMessage == null) {
                    errorMessage = "表达式不完整：'$base ^' 后面缺少指数"
                }
                return "$base^{\\square}"
            }

            val funcMatch = Regex("""^(\\\w+)\\left\((.+)\\right\)$""").find(base)
            if (funcMatch != null) {
                val funcName = funcMatch.groupValues[1]
                val args = funcMatch.groupValues[2]
                return "$funcName^{$exp}{\\left($args\\right)}"
            }

            return "$base^{$exp}"
        }

        return base
    }

    private fun parseUnary(): String {
        if (peek() is LToken.Operator) {
            val op = peek() as LToken.Operator
            if (op.value == "-") {
                advance()
                val operand = parseAtom()
                return "-$operand"
            }
        }
        return parseAtom()
    }

    private fun parseArgsUntilRParen(): List<String> {
        val args = mutableListOf<String>()
        while (pos < tokens.size) {
            val t = peek()
            when {
                t == null -> break
                t is LToken.RParen -> { advance(); break }
                t is LToken.Comma -> { advance(); continue }
                else -> args.add(parseExpression())
            }
        }
        return args
    }

    private fun parseMatrixLiteral(): String {
        val savedPos = pos
        advance() // consume first '['

        if (peek() is LToken.LSq) {
            val rows = mutableListOf<String>()
            while (pos < tokens.size) {
                val t = peek()
                when {
                    t == null -> break
                    t is LToken.RSq -> {
                        advance()
                        if (peek() is LToken.RSq) {
                            advance()
                            break
                        }
                        continue
                    }
                    t is LToken.LSq -> {
                        advance()
                        val cells = mutableListOf<String>()
                        while (pos < tokens.size) {
                            val ct = peek()
                            when {
                                ct == null -> break
                                ct is LToken.RSq -> { advance(); break }
                                ct is LToken.Comma -> { advance(); continue }
                                else -> cells.add(parseExpression())
                            }
                        }
                        rows.add(cells.joinToString(" & "))
                        if (peek() is LToken.Comma) advance()
                        continue
                    }
                    else -> break
                }
            }
            if (rows.isNotEmpty()) {
                return """\begin{pmatrix}${rows.joinToString(" \\\\ ")}\end{pmatrix}"""
            }
            return ""
        } else {
            pos = savedPos
            return ""
        }
    }

    private fun parseAtom(): String {
        val token = peek() ?: return ""

        return when (token) {
            is LToken.Number -> { advance(); token.value }
            is LToken.Variable -> { advance(); token.value }
            is LToken.LSq -> {
                val matrix = parseMatrixLiteral()
                if (matrix.isNotEmpty()) return matrix
                advance()
                "\\left["
            }
            is LToken.RSq -> { advance(); "\\right]" }
            is LToken.Func -> {
                advance()
                parseFunction(token.value)
            }
            is LToken.LParen -> {
                advance()
                val inner = parseExpression()
                if (peek() is LToken.RParen) advance()
                val next = peek()
                val hasAddition = inner.contains(" + ") || inner.contains(" - ")
                val wrapInParens = next is LToken.Caret || hasAddition || next is LToken.Slash || next is LToken.Operator
                if (wrapInParens) return "\\left($inner\\right)"
                inner
            }
            else -> { advance(); "" }
        }
    }

    private fun parseFunction(funcName: String): String {
        val funcLatexMap = mapOf(
            "sin" to "\\sin", "cos" to "\\cos", "tan" to "\\tan",
            "asin" to "\\arcsin", "acos" to "\\arccos", "atan" to "\\arctan",
            "sinh" to "\\sinh", "cosh" to "\\cosh", "tanh" to "\\tanh",
            "ln" to "\\ln", "log" to "\\log", "exp" to "\\exp",
            "abs" to "\\left|", "ceil" to "\\lceil", "floor" to "\\lfloor",
            "diff" to "\\frac{d}{dx}", "derivative" to "\\frac{d}{dx}",
            "integrate" to "\\int", "int" to "\\int",
            "simplify" to "\\text{simplify}",
            "solve" to "\\text{solve}", "nsolve" to "\\text{nsolve}",
            "dsolve" to "\\text{dsolve}", "linsolve" to "\\text{linsolve}",
            "limit" to "\\lim", "series" to "\\text{series}",
            "taylor" to "\\text{taylor}",
            "det" to "\\det", "inv" to "\\operatorname{inv}",
            "inverse" to "\\operatorname{inv}", "transpose" to "\\operatorname{T}",
            "eigenvals" to "\\text{eigenvals}", "eigenvects" to "\\text{eigenvects}",
            "rank" to "\\text{rank}"
        )

        val latexName = funcLatexMap[funcName] ?: "\\operatorname{$funcName}"

        when (funcName) {
            "sqrt" -> {
                if (peek() is LToken.LParen) {
                    advance()
                    val args = parseArgsUntilRParen()
                    return "\\sqrt{${args.joinToString(",\\ ")}}"
                }
                return "\\sqrt{}"
            }
            "abs" -> {
                if (peek() is LToken.LParen) {
                    advance()
                    val args = parseArgsUntilRParen()
                    return "\\left|${args.joinToString(",\\ ")}\\right|"
                }
                return "\\left|\\right|"
            }
            "limit" -> {
                if (peek() is LToken.LParen) {
                    advance()
                    val args = parseArgsUntilRParen()
                    if (args.size >= 3) {
                        var arrow = "\\to ${args[2]}"
                        if (args.size >= 4) {
                            val dir = args[3].replace("['\"]", "")
                            if (dir == "+") arrow = "\\to ${args[2]}^{+}"
                            else if (dir == "-") arrow = "\\to ${args[2]}^{-}"
                        }
                        return "\\lim_{${args[1]} $arrow} ${args[0]}"
                    }
                    return "$latexName\\left(${args.joinToString(",\\ ")}\\right)"
                }
            }
            "diff", "derivative" -> {
                if (peek() is LToken.LParen) {
                    advance()
                    val args = parseArgsUntilRParen()
                    if (args.size >= 2) {
                        return "\\frac{d}{d ${args[1]}}\\left(${args[0]}\\right)"
                    }
                    return "\\frac{d}{dx}\\left(${args.joinToString(",\\ ")}\\right)"
                }
            }
            "integrate", "int" -> {
                if (peek() is LToken.LParen) {
                    advance()
                    val args = parseArgsUntilRParen()
                    if (args.size >= 4) {
                        return "\\int_{${args[2]}}^{${args[3]}} ${args[0]} \\, d${args[1]}"
                    }
                    if (args.size >= 2) {
                        return "\\int ${args[0]} \\, d${args[1]}"
                    }
                    return "\\int ${args.joinToString(",\\ ")} \\, dx"
                }
            }
            "transpose" -> {
                if (peek() is LToken.LParen) {
                    advance()
                    val args = parseArgsUntilRParen()
                    return "\\left(${args.joinToString(",\\ ")}\\right)^{T}"
                }
            }
        }

        if (peek() is LToken.LParen) {
            advance()
            val args = parseArgsUntilRParen()
            return "$latexName\\left(${args.joinToString(",\\ ")}\\right)"
        }

        return latexName
    }
}

fun inputToLatex(input: String): String {
    if (input.isBlank()) return ""

    // Handle solve/nsolve: show as "expression = 0"
    val solveMatch = Regex("""^(solve|nsolve)\s*\(""", RegexOption.IGNORE_CASE).find(input)
    if (solveMatch != null) {
        val firstArg = extractFirstArg(input, solveMatch.value.length)
        if (firstArg != null) {
            val parsed = inputToLatexInner(firstArg)
            if (parsed.isNotEmpty()) return "$parsed = 0"
        }
    }

    // Handle linsolve: show as system of equations
    val linsolveMatch = Regex("""^linsolve\s*\(""", RegexOption.IGNORE_CASE).find(input)
    if (linsolveMatch != null) {
        val firstArg = extractFirstArg(input, linsolveMatch.value.length)
        if (firstArg != null) {
            val eqs = parseEquationList(firstArg)
            if (eqs.isNotEmpty()) {
                val eqLines = eqs.map { eq ->
                    val parsed = inputToLatexInner(eq)
                    "$parsed = 0"
                }
                return """\begin{cases}${eqLines.joinToString(" \\\\ ")}\end{cases}"""
            }
        }
    }

    return inputToLatexInner(input)
}

private fun extractFirstArg(input: String, parenStart: Int): String? {
    var parenDepth = 1
    var bracketDepth = 0
    var i = parenStart
    while (i < input.length && parenDepth > 0) {
        val ch = input[i]
        when (ch) {
            '(' -> parenDepth++
            ')' -> parenDepth--
            '[' -> bracketDepth++
            ']' -> bracketDepth--
            ',' -> if (parenDepth == 1 && bracketDepth == 0) return input.substring(parenStart, i).trim()
        }
        i++
    }
    if (parenDepth == 0) {
        val inner = input.substring(parenStart, i - 1).trim()
        return inner.ifEmpty { null }
    }
    return null
}

private fun parseEquationList(raw: String): List<String> {
    var s = raw.trim()
    if (s.startsWith("[") && s.endsWith("]")) {
        s = s.substring(1, s.length - 1).trim()
    }
    val eqs = mutableListOf<String>()
    var depth = 0
    var start = 0
    for (i in s.indices) {
        val ch = s[i]
        when {
            ch == '(' || ch == '[' -> depth++
            ch == ')' || ch == ']' -> depth--
            ch == ',' && depth == 0 -> {
                eqs.add(s.substring(start, i).trim())
                start = i + 1
            }
        }
    }
    eqs.add(s.substring(start).trim())
    return eqs.filter { it.isNotEmpty() }
}

private fun inputToLatexInner(input: String): String {
    return try {
        val rawTokens = tokenize(input)
        if (rawTokens.isEmpty()) return ""
        val tokens = insertImplicitMultiplication(rawTokens)
        val parser = LParser(tokens)
        parser.parse()
    } catch (_: Exception) {
        input.replace("^", "^")
    }
}
