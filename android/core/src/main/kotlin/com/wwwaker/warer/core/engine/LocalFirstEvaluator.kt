package com.wwwaker.warer.core.engine

/**
 * 本地优先求值：**先判定能力、再交给本地数值引擎**。
 *
 * 这是 Tier 0~3 能力分层调度器的种子（见 docs/PROJECT_CONTEXT.md 第 6.2 节）。
 * 目前只实现"本地能算就本地算，算不了就返回 null 让上层去想办法"，
 * 后续会在这里插入 Tier 1（符号轻量）与 Tier 2（本地 CAS）两级。
 *
 * 把它单独抽出来的意义：**"什么该在本地算"这条策略只有一个出处**，
 * 不会在 ViewModel、编辑器、绘图等各个调用点各写一遍而逐渐漂移。
 */
object LocalFirstEvaluator {

    /**
     * 尝试本地求值。
     *
     * @return 本地算出的数值结果；返回 `null` 表示本地无法胜任，
     *         调用方应当转交云端符号引擎或给出明确提示（**不要静默失败**）。
     */
    fun tryEvaluate(command: String): NumericResult? {
        if (command.isBlank()) return null
        if (CapabilityClassifier.needsCloud(command)) return null
        return LocalNumericEngine.compute(command)
    }
}
