package com.wwwaker.warer.core.engine

/**
 * 一次计算的失败原因。
 *
 * **必须区分得这么细，是因为给用户的说法完全不同**：
 * "公式是空的" / "表达式写错了" / "这功能要联网" 是三件不同的事，
 * 笼统说一句"算不了"会让用户无从下手（见 docs/SESSION_SUMMARY.md 3.3）。
 */
enum class FailureKind {
    /** 公式为空。 */
    EMPTY,

    /** 本地引擎本应能算，但表达式不完整或格式有误。 */
    LOCAL_FAILED,

    /** 需要云端符号引擎，但当前未启用 / 不可用。 */
    CLOUD_UNAVAILABLE,

    /** 云端引擎明确拒绝了该表达式（语法错误 / 无解 / 数学错误）。 */
    CLOUD_REJECTED,

    /** 云端调用失败（网络错误、服务不可达）。 */
    CLOUD_UNREACHABLE
}

/**
 * 计算调度器的统一输出。
 *
 * UI 只认这一种类型，不再各自维护"本地结果 / 云端结果 / 三种错误"的分支——
 * 这是"能力分层"这条策略**只有一个出处**的关键（见 docs/PROJECT_CONTEXT.md 6.2）。
 */
sealed interface ComputeOutcome {

    /** Tier 0：本地数值解。 */
    data class LocalNumeric(val result: NumericResult) : ComputeOutcome

    /** Tier 3：云端符号解。 */
    data class CloudSymbolic(val result: CloudSymbolicResult) : ComputeOutcome

    data class Failure(
        val kind: FailureKind,
        val message: String,
        val position: Int? = null
    ) : ComputeOutcome
}
