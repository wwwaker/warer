package com.wwwaker.warer_android.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OutputConfig(
    val format: String = "latex",
    val precision: Int = 15,
    val simplify: Boolean = true
)

@Serializable
data class ComputePayload(
    val expression: String,
    @SerialName("engine_hint")
    val engineHint: String = "auto",
    @SerialName("output_config")
    val outputConfig: OutputConfig = OutputConfig()
)

@Serializable
data class ComputeRequest(
    val payload: ComputePayload
)

@Serializable
data class ComputeResult(
    @SerialName("is_symbolic")
    val isSymbolic: Boolean = true,
    @SerialName("main_display")
    val mainDisplay: String = "",
    @SerialName("plain_text")
    val plainText: String = "",
    @SerialName("numeric_approximation")
    val numericApproximation: String? = null,
    val variables: List<String> = emptyList()
)

@Serializable
data class ComputeError(
    val type: String,
    val message: String,
    val position: Int? = null
)

@Serializable
data class ComputeResponse(
    @SerialName("status_code")
    val statusCode: Int = 200,
    @SerialName("execution_time")
    val executionTime: String = "",
    val result: ComputeResult? = null,
    val error: ComputeError? = null
)

@Serializable
data class HealthResponse(
    val status: String
)
