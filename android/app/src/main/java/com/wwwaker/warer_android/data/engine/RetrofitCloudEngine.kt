package com.wwwaker.warer_android.data.engine

import com.wwwaker.warer.core.engine.CloudCallResult
import com.wwwaker.warer.core.engine.CloudEngine
import com.wwwaker.warer.core.engine.CloudSymbolicResult
import com.wwwaker.warer_android.data.api.ComputePayload
import com.wwwaker.warer_android.data.api.ComputeRequest
import com.wwwaker.warer_android.data.api.RetrofitClient
import kotlinx.coroutines.runBlocking

/**
 * [CloudEngine] 的 Retrofit 实现 —— 把后端契约翻译成 `:core` 的抽象。
 *
 * 存在的意义：`:core` 只认 [CloudEngine] 这个接口，**不认识 Retrofit / JSON**，
 * 于是"能力分层 + 降级策略"可以整体在 JVM 单测里跑，而不必起模拟器。
 * 这一层是唯一知道 HTTP 的地方。
 *
 * 线程模型：[CloudEngine.compute] 是阻塞的，调用方（[com.wwwaker.warer.core.engine.ComputeDispatcher]）
 * 约定在 IO 线程调用，所以这里用 [runBlocking] 把 Retrofit 的 suspend 接口桥接成阻塞调用。
 *
 * 错误约定（与 `:core` 对齐）：
 * - 后端返回 `error` → [CloudCallResult.Rejected]（可展示给用户）；
 * - 抛异常 → 网络 / 服务不可达，交由调度器降级。
 */
object RetrofitCloudEngine : CloudEngine {

    override fun compute(command: String): CloudCallResult {
        val response = runBlocking {
            RetrofitClient.getApiService().compute(
                ComputeRequest(payload = ComputePayload(expression = command))
            )
        }

        response.error?.let { error ->
            return CloudCallResult.Rejected(
                type = error.type,
                message = error.message,
                position = error.position
            )
        }

        val result = response.result
            ?: return CloudCallResult.Rejected(
                type = "EmptyResponse",
                message = "云端没有返回结果"
            )

        return CloudCallResult.Success(
            CloudSymbolicResult(
                latex = result.mainDisplay,
                plainText = result.plainText,
                numericApproximation = result.numericApproximation,
                isSymbolic = result.isSymbolic,
                variables = result.variables,
                executionTime = response.executionTime
            )
        )
    }
}
