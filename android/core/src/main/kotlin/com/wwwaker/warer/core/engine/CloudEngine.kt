package com.wwwaker.warer.core.engine

/**
 * 云端符号引擎返回的原始结果。
 *
 * 字段与后端 `POST /v1/compute` 响应里的 `result` 一一对应，但本模块**不**依赖
 * Retrofit / JSON —— 具体协议解析由 app 侧的 [CloudEngine] 实现负责。
 * 这样 `:core` 仍然可以纯 JVM 单测（不变量 ⑤）。
 *
 * @param latex              后端给出的 `main_display`，即 SymPy 的 `latex()` 输出
 * @param plainText          后端给出的 `plain_text`，即 SymPy 的 `str()` 输出
 * @param numericApproximation 符号结果的数值近似（后端 `numeric_approximation`）
 * @param isSymbolic         结果是否仍含自由符号
 * @param variables          结果中出现的自由变量
 * @param executionTime      后端上报的耗时文本，如 `12.3ms`
 */
data class CloudSymbolicResult(
    val latex: String,
    val plainText: String,
    val numericApproximation: String? = null,
    val isSymbolic: Boolean = true,
    val variables: List<String> = emptyList(),
    val executionTime: String = ""
)

/**
 * 一次云端调用的返回值。
 *
 * 刻意把"网络故障"与"引擎拒绝"分开：
 * - **引擎正常工作但表达式有问题** → [Rejected]（带错误类型，可直接展示给用户）；
 * - **网络 / 服务不可达** → 由 [CloudEngine.compute] **抛异常**。
 *
 * 二者在降级策略上不同（拒绝要展示具体原因，不可达要提示网络问题），所以不能混为一谈。
 */
sealed interface CloudCallResult {

    data class Success(val result: CloudSymbolicResult) : CloudCallResult

    data class Rejected(
        val type: String,
        val message: String,
        val position: Int? = null
    ) : CloudCallResult
}

/**
 * 云端符号引擎的最小抽象（Tier 3）。
 *
 * **实现必须是阻塞的**：调用方 [ComputeDispatcher] 不关心线程模型，
 * app 侧负责把它放到 IO 线程执行（`withContext(Dispatchers.IO)`）。
 * 这样换来两个好处：`:core` 不需要引入协程依赖；单测里可以直接返回假结果。
 *
 * 实现约定：
 * - 引擎正常返回但拒绝该表达式 → 返回 [CloudCallResult.Rejected]；
 * - 网络 / 服务不可达 / 反序列化失败 → **抛异常**。
 *
 * 声明成 `fun interface` 是为了单测里能用 lambda 直接构造。
 */
fun interface CloudEngine {
    fun compute(command: String): CloudCallResult
}
