package com.wwwaker.warer.core.engine

/**
 * 表达式所需的计算能力层级。
 *
 * 目前只区分两级——本地数值（Tier 0）与云端符号（Tier 3）。
 * 中间的 Tier 1/2（本地符号轻量 / 本地 CAS）尚未实现，见
 * docs/PROJECT_CONTEXT.md 第 6.2 节「能力分层模型」。
 */
enum class Capability {
    /** 本地数值引擎即可胜任（永远可用，不联网）。 */
    LOCAL_NUMERIC,

    /** 超出本地数值能力，需要云端符号引擎。 */
    CLOUD_SYMBOLIC
}

/**
 * 能力分类器：判断一个表达式是否必须交给"重量级"引擎处理。
 *
 * [needsCloud] 是旧 Android 端 `CalculatorViewModel.needsCloud()` 的**原样搬运**，
 * 目的是保持行为 100% 一致（由单元测试逐条锁定）。
 * [classify] 是给它加上一个显式的能力标签，供 [ComputeDispatcher] 与 UI 使用。
 */
object CapabilityClassifier {

    /** 把表达式归入一个能力层级。 */
    fun classify(input: String): Capability =
        if (needsCloud(input)) Capability.CLOUD_SYMBOLIC else Capability.LOCAL_NUMERIC

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
