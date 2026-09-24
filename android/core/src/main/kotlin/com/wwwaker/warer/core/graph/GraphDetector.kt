package com.wwwaker.warer.core.graph

data class DetectionResult(
    val type: String, // linear, polar, parametric, implicit
    val expr: String,
    val xExpr: String = "",
    val yExpr: String = ""
)

object GraphDetector {

    private val KNOWN_FUNC_REGEX = Regex(
        """\b(sin|cos|tan|asin|acos|atan|sinh|cosh|tanh|asinh|acosh|atanh|log|ln|exp|sqrt|abs|cbrt|sign|ceil|floor|round|nthRoot|factorial)\s*\(""",
        RegexOption.IGNORE_CASE
    )

    fun detect(expr: String): DetectionResult {
        val trimmed = expr.trim()
        if (trimmed.isEmpty()) return DetectionResult("linear", "")

        val explicit = tryExplicitNotation(trimmed)
        if (explicit != null) return explicit

        return heuristicDetection(trimmed)
    }

    private fun tryExplicitNotation(input: String): DetectionResult? {
        val lower = input.lowercase().trim()

        if (lower.startsWith("y(")) {
            val inner = parseParenContent(input, "y(".length)
            if (inner != null) return DetectionResult("linear", inner)
        }

        if (lower.startsWith("r(")) {
            val inner = parseParenContent(input, "r(".length)
            if (inner != null) return DetectionResult("polar", inner)
        }
        if (lower.startsWith("theta(")) {
            val inner = parseParenContent(input, "theta(".length)
            if (inner != null) return DetectionResult("polar", inner)
        }

        if (lower.startsWith("t(")) {
            val result = parseTwoArgContent(input, "t(".length)
            if (result != null) {
                return DetectionResult("parametric", input, result.first, result.second)
            }
        }

        if (lower.startsWith("f(")) {
            val inner = parseParenContent(input, "f(".length)
            if (inner != null) return DetectionResult("implicit", inner)
        }

        return null
    }

    private fun parseParenContent(input: String, startPos: Int): String? {
        var depth = 1
        var i = startPos

        while (i < input.length && depth > 0) {
            val ch = input[i]
            if (ch == '(') depth++
            else if (ch == ')') {
                depth--
                if (depth == 0) break
            }
            i++
        }

        if (depth != 0) return null
        if (i != input.length - 1) return null

        val inner = input.substring(startPos, i).trim()
        return inner.ifEmpty { null }
    }

    private fun parseTwoArgContent(input: String, startPos: Int): Pair<String, String>? {
        var depth = 1
        var i = startPos
        var commaPos = -1

        while (i < input.length && depth > 0) {
            val ch = input[i]
            if (ch == '(') depth++
            else if (ch == ')') {
                depth--
                if (depth == 0) break
            }
            if (depth == 1 && ch == ',' && commaPos == -1) {
                commaPos = i
            }
            i++
        }

        if (depth != 0) return null
        if (i != input.length - 1) return null
        if (commaPos == -1) return null

        val xExpr = input.substring(startPos, commaPos).trim()
        val yExpr = input.substring(commaPos + 1, i).trim()

        if (xExpr.isEmpty() || yExpr.isEmpty()) return null

        return Pair(xExpr, yExpr)
    }

    private fun heuristicDetection(input: String): DetectionResult {
        val lower = input.lowercase()

        if (Regex("""\btheta\b""").containsMatchIn(lower)) {
            return DetectionResult("polar", input)
        }

        if (Regex("""^(?:diff|int(?:egrate)?|solve|simplify)\s*\(""", RegexOption.IGNORE_CASE).containsMatchIn(input.trim())) {
            return DetectionResult("linear", input)
        }

        val noPrefix = input.replace(Regex("""^y\s*=\s*""", RegexOption.IGNORE_CASE), "").trim()
        val knownFuncStripped = KNOWN_FUNC_REGEX.replace(noPrefix, "")
        if (Regex("""\by\b""").containsMatchIn(knownFuncStripped)) {
            return DetectionResult("implicit", noPrefix)
        }

        return DetectionResult("linear", input)
    }
}
