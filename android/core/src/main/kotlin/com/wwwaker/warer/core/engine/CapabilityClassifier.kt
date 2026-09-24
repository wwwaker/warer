package com.wwwaker.warer.core.engine

/**
 * 能力分类器：判断一个表达式是否必须交给"重量级"引擎处理。
 *
 * 当前是旧 Android 端 `CalculatorViewModel.needsCloud()` 的**原样搬运**，
 * 目的是在重构期间保持行为 100% 一致（由单元测试逐条锁定）。
 *
 * 后续演进方向：从"本地 / 云端"二选一，升级为 Tier 0~3 的能力判定。
 * 见 docs/PROJECT_CONTEXT.md 第 6.2 节「能力分层模型」。
 */
object CapabilityClassifier {

    /**
     * @return true 表示本地数值引擎无法胜任，需要交给云端符号引擎。
     */
    fun needsCloud(input: String): Boolean {
        val cloudKeywords = Regex(
            "\\b(diff|derivative|int|integrate|solve|nsolve|dsolve|linsolve|limit|series|taylor|simplify|matrix|det|inv|transpose|eigenvals|eigenvects|rank|inverse)\\b",
            RegexOption.IGNORE_CASE
        )
        if (cloudKeywords.containsMatchIn(input)) return true
        if (Regex("""\[\[.+?\]\]""").containsMatchIn(input)) return true

        val knownFuncs = setOf(
            "sin", "cos", "tan", "asin", "acos", "atan",
            "sinh", "cosh", "tanh", "ln", "log", "exp", "sqrt",
            "abs", "ceil", "floor", "round"
        )
        val localConstants = setOf("e", "pi", "E", "PI")

        val withoutFuncs = knownFuncs.fold(input) { acc, func ->
            acc.replace(Regex("\\b$func\\b", RegexOption.IGNORE_CASE), "")
        }
        val varMatches = Regex("""\b([a-zA-Z])\b(?!\s*\()""").findAll(withoutFuncs)
        if (varMatches.any { !localConstants.contains(it.groupValues[1]) }) return true

        return false
    }
}
