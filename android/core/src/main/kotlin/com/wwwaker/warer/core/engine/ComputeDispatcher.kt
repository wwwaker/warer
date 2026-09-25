package com.wwwaker.warer.core.engine

/**
 * 计算调度器：**"能力分类 → 本地 → 云端降级"这条策略的唯一出处**。
 *
 * 在此之前，这条策略在 `FormulaEditorScreen.evaluate()` 与
 * `CalculatorViewModel.compute()` 里各写了一遍，文案与降级顺序已经开始漂移。
 * 本对象把它收敛成一处，UI 只负责调用 + 展示 [ComputeOutcome]。
 *
 * 决策顺序（与旧实现的可见行为保持一致）：
 * ```
 * 1. 空命令                  → Failure(EMPTY)
 * 2. 分类为「本地可算」      → 本地求值成功则返回 LocalNumeric
 * 3. 云端可用？
 *    ├─ 不可用              → 按分类给出 CLOUD_UNAVAILABLE / LOCAL_FAILED
 *    └─ 可用                → 调用云端
 *         ├─ Success        → CloudSymbolic
 *         ├─ Rejected       → 再试本地兜底，否则 CLOUD_REJECTED
 *         └─ 抛异常          → 再试本地兜底，否则 CLOUD_UNREACHABLE
 * ```
 *
 * 注意第 2 步**失败后仍会尝试云端**：纯数值表达式若本地解析不了（例如写法怪异），
 * 云端可能给出符号解；这也是旧 `CalculatorViewModel` 的行为。
 *
 * 本函数是**阻塞的**。app 侧请在 IO 线程调用（`withContext(Dispatchers.IO)`）。
 */
object ComputeDispatcher {

    /**
     * @param command 交给计算引擎的命令文本（由 `CommandBuilder` 从公式 AST 编译而来）
     * @param cloud   云端符号引擎；传 `null` 表示当前不可用（未联网 / 未启用）
     */
    fun dispatch(command: String, cloud: CloudEngine? = null): ComputeOutcome {
        val expr = command.trim()
        if (expr.isEmpty()) {
            return ComputeOutcome.Failure(FailureKind.EMPTY, "公式还是空的")
        }

        val capability = CapabilityClassifier.classify(expr)

        // 1) 本地优先
        if (capability == Capability.LOCAL_NUMERIC) {
            LocalFirstEvaluator.tryEvaluate(expr)?.let { return ComputeOutcome.LocalNumeric(it) }
        }

        // 2) 没有云端可用：按分类给出不同的说法，不要都笼统说"算不了"
        if (cloud == null) {
            return when (capability) {
                Capability.CLOUD_SYMBOLIC -> ComputeOutcome.Failure(
                    FailureKind.CLOUD_UNAVAILABLE,
                    "这条命令超出本地数值引擎的能力范围（当前未启用联网引擎）"
                )
                Capability.LOCAL_NUMERIC -> ComputeOutcome.Failure(
                    FailureKind.LOCAL_FAILED,
                    "表达式不完整或格式有误，请检查括号与运算符"
                )
            }
        }

        // 3) 云端
        return try {
            when (val call = cloud.compute(expr)) {
                is CloudCallResult.Success -> ComputeOutcome.CloudSymbolic(call.result)
                is CloudCallResult.Rejected -> degradeOrReject(expr, call)
            }
        } catch (e: Exception) {
            degradeOrFail(expr, "网络错误: ${e.message ?: e::class.simpleName}")
        }
    }

    /** 云端明确拒绝：先看本地能否兜底，否则把云端的错误原因原样带给用户。 */
    private fun degradeOrReject(
        expr: String,
        rejected: CloudCallResult.Rejected
    ): ComputeOutcome {
        LocalFirstEvaluator.tryEvaluate(expr)?.let { return ComputeOutcome.LocalNumeric(it) }
        return ComputeOutcome.Failure(
            kind = FailureKind.CLOUD_REJECTED,
            message = "${rejected.type}: ${rejected.message}",
            position = rejected.position
        )
    }

    /** 云端不可达：先看本地能否兜底，否则报网络问题。 */
    private fun degradeOrFail(expr: String, message: String): ComputeOutcome {
        LocalFirstEvaluator.tryEvaluate(expr)?.let { return ComputeOutcome.LocalNumeric(it) }
        return ComputeOutcome.Failure(FailureKind.CLOUD_UNREACHABLE, message)
    }
}
