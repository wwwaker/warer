package com.wwwaker.warer_android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wwwaker.warer_android.data.api.ComputePayload
import com.wwwaker.warer_android.data.api.ComputeRequest
import com.wwwaker.warer_android.data.api.RetrofitClient
import com.wwwaker.warer_android.data.db.HistoryDao
import com.wwwaker.warer_android.data.db.HistoryEntity
import com.wwwaker.warer_android.data.engine.inputToLatex
import com.wwwaker.warer_android.data.settings.SettingsManager
import com.wwwaker.warer.core.engine.CapabilityClassifier
import com.wwwaker.warer.core.engine.LocalNumericEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ComputeOutput(
    val source: String = "",
    val latex: String = "",
    val plainText: String = "",
    val numericValue: Double? = null,
    val isSymbolic: Boolean = false,
    val variables: List<String> = emptyList(),
    val executionTime: String = "",
    val error: String? = null,
    val errorPosition: Int? = null
)

data class GraphFn(
    val id: String = "",
    val fnType: String = "linear",
    val expr: String = "",
    val xExpr: String = "",
    val yExpr: String = "",
    val color: String = "#5b5ef0",
    val hidden: Boolean = false
)

data class CalculatorUiState(
    val input: String = "",
    val output: ComputeOutput? = null,
    val isComputing: Boolean = false,
    val previewLatex: String = "",
    val cursorPosition: Int = 0,
    val bracketHint: String? = null,
    val graphFunctions: List<GraphFn> = emptyList(),
    val history: List<HistoryEntry> = emptyList(),
    val historyIndex: Int? = null
)

sealed class HistoryEntry {
    data class Calculation(
        val input: String,
        val output: ComputeOutput,
        val timestamp: Long
    ) : HistoryEntry()

    data class Graph(
        val input: String,
        val fnType: String,
        val expr: String,
        val xExpr: String,
        val yExpr: String,
        val color: String,
        val timestamp: Long
    ) : HistoryEntry()
}

class CalculatorViewModel(
    private val settingsManager: SettingsManager? = null,
    private val historyDao: HistoryDao? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalculatorUiState())
    val uiState: StateFlow<CalculatorUiState> = _uiState.asStateFlow()

    private var computeJob: Job? = null
    private var graphCounter = 0

    private val FUNC_SUFFIXES = listOf(
        "sin(", "cos(", "tan(", "ln(", "log(", "exp(", "sqrt(", "abs(",
        "asin(", "acos(", "atan(", "sinh(", "cosh(", "tanh(",
        "diff(", "integrate(", "simplify(", "solve(", "nsolve(",
        "dsolve(", "linsolve(", "limit(", "series(", "taylor("
    )

    private val COLORS = listOf(
        "#5b5ef0", "#0ea5a0", "#e5484d", "#f5a623",
        "#30a46c", "#e84393", "#8b5cf6", "#f472b6"
    )

    fun onInputChange(value: String) {
        _uiState.update { state ->
            val preview = inputToLatex(value)
            val hint = checkBracketMismatch(value)
            state.copy(
                input = value,
                previewLatex = preview,
                bracketHint = hint,
                output = null,
                historyIndex = null
            )
        }
    }

    fun appendInput(value: String) {
        val state = _uiState.value
        val pos = state.cursorPosition.coerceIn(0, state.input.length)
        val before = state.input.substring(0, pos)
        val after = state.input.substring(pos)
        var newInput = before + value + after

        if (value == "(") {
            val openCount = newInput.count { it == '(' }
            val closeCount = newInput.count { it == ')' }
            if (openCount > closeCount) {
                newInput += ")"
            }
        }

        val newPos = pos + value.length
        onInputChange(newInput)
        _uiState.update { it.copy(cursorPosition = newPos) }
    }

    fun backspace() {
        val state = _uiState.value
        val input = state.input
        if (input.isEmpty()) return

        val pos = state.cursorPosition.coerceIn(0, input.length)
        if (pos <= 0) return

        val before = input.substring(0, pos)
        val after = input.substring(pos)

        val matchedSuffix = FUNC_SUFFIXES.firstOrNull { suffix ->
            val start = before.length - suffix.length
            start >= 0 && before.substring(start) == suffix
        }

        val newBefore: String
        val newPos: Int
        if (matchedSuffix != null) {
            newBefore = before.substring(0, before.length - matchedSuffix.length)
            newPos = newBefore.length
        } else {
            val last = before.last()
            val beforeMinusOne = before.substring(0, before.length - 1)
            if (last == ')' && beforeMinusOne.endsWith('(')) {
                newBefore = beforeMinusOne.substring(0, beforeMinusOne.length - 1)
                newPos = newBefore.length
            } else {
                newBefore = beforeMinusOne
                newPos = newBefore.length
            }
        }

        val newInput = newBefore + after
        onInputChange(newInput)
        _uiState.update { it.copy(cursorPosition = newPos) }
    }

    fun setCursorPosition(pos: Int) {
        _uiState.update { it.copy(cursorPosition = pos) }
    }

    fun autoFixBrackets() {
        val hint = _uiState.value.bracketHint ?: return
        val current = _uiState.value.input
        val fixed = if (hint.contains("缺少左括号")) "($current"
        else {
            val count = Regex("""\d+""").find(hint)?.value?.toIntOrNull() ?: return
            current + ")".repeat(count)
        }
        onInputChange(fixed)
    }

    fun clearInput() {
        _uiState.update { it.copy(input = "", output = null, previewLatex = "", bracketHint = null) }
    }

    fun compute() {
        val input = _uiState.value.input.trim()
        if (input.isEmpty()) return

        computeJob?.cancel()
        computeJob = viewModelScope.launch {
            _uiState.update { it.copy(isComputing = true) }

            delay(300)

            if (!CapabilityClassifier.needsCloud(input)) {
                val localResult = LocalNumericEngine.compute(input)
                if (localResult != null) {
                    val output = ComputeOutput(
                        source = "local",
                        latex = localResult.plainText,
                        plainText = localResult.plainText,
                        numericValue = localResult.numericValue,
                        executionTime = "<1ms"
                    )
                    recordCalculation(input, output)
                    return@launch
                }
            }

            try {
                val response = RetrofitClient.getApiService().compute(
                        ComputeRequest(
                                payload = ComputePayload(expression = input)
                        )
                )

                if (response.error != null) {
                    val localFallback = LocalNumericEngine.compute(input)
                    if (localFallback != null) {
                        val output = ComputeOutput(
                            source = "local",
                            latex = localFallback.plainText,
                            plainText = localFallback.plainText,
                            numericValue = localFallback.numericValue,
                            executionTime = "<1ms"
                        )
                        recordCalculation(input, output)
                        return@launch
                    }

                    val output = ComputeOutput(
                        source = "error",
                        error = "${response.error.type}: ${response.error.message}",
                        errorPosition = response.error.position
                    )
                    recordCalculation(input, output)
                    return@launch
                }

                val result = response.result
                if (result != null) {
                    val numApprox = result.numericApproximation?.toDoubleOrNull()
                    val output = ComputeOutput(
                        source = "cloud",
                        latex = result.mainDisplay,
                        plainText = result.plainText,
                        numericValue = numApprox,
                        isSymbolic = result.isSymbolic,
                        variables = result.variables,
                        executionTime = response.executionTime
                    )
                    recordCalculation(input, output)
                }
            } catch (e: Exception) {
                val localFallback = LocalNumericEngine.compute(input)
                if (localFallback != null) {
                    val output = ComputeOutput(
                        source = "local",
                        latex = localFallback.plainText,
                        plainText = localFallback.plainText,
                        numericValue = localFallback.numericValue,
                        executionTime = "<1ms"
                    )
                    recordCalculation(input, output)
                } else {
                    val output = ComputeOutput(
                        source = "error",
                        error = if (!CapabilityClassifier.needsCloud(input)) "本地计算失败，且云端服务不可用"
                                else "网络错误: ${e.message}"
                    )
                    recordCalculation(input, output)
                }
            }
        }
    }

    fun addGraphFunction(
        expr: String,
        fnType: String = "linear",
        xExpr: String = "",
        yExpr: String = "",
        saveToHistory: Boolean = true
    ) {
        if (expr.isBlank()) return
        _uiState.update { state ->
            val color = COLORS[state.graphFunctions.size % COLORS.size]
            val id = "gfn_${graphCounter++}"
            val fn = GraphFn(id = id, fnType = fnType, expr = expr, xExpr = xExpr, yExpr = yExpr, color = color)
            state.copy(graphFunctions = state.graphFunctions + fn)
        }

        if (saveToHistory && historyDao != null) {
            val color = COLORS[(_uiState.value.graphFunctions.size - 1) % COLORS.size]
            viewModelScope.launch {
                historyDao!!.insert(
                    HistoryEntity(
                        type = "graph",
                        input = expr,
                        fnType = fnType,
                        expr = expr,
                        xExpr = xExpr.ifEmpty { null },
                        yExpr = yExpr.ifEmpty { null },
                        graphColor = color
                    )
                )
            }
        }
    }

    fun removeGraphFunction(id: String) {
        _uiState.update { state ->
            state.copy(graphFunctions = state.graphFunctions.filter { it.id != id })
        }
    }

    fun toggleGraphFnVisibility(id: String) {
        _uiState.update { state ->
            state.copy(
                graphFunctions = state.graphFunctions.map {
                    if (it.id == id) it.copy(hidden = !it.hidden) else it
                }
            )
        }
    }

    fun updateGraphFnColor(id: String, color: String) {
        _uiState.update { state ->
            state.copy(
                graphFunctions = state.graphFunctions.map {
                    if (it.id == id) it.copy(color = color) else it
                }
            )
        }
    }

    fun updateGraphFnExpression(id: String, expr: String, fnType: String? = null, xExpr: String? = null, yExpr: String? = null) {
        _uiState.update { state ->
            state.copy(
                graphFunctions = state.graphFunctions.map {
                    if (it.id == id) it.copy(
                        expr = expr,
                        fnType = fnType ?: it.fnType,
                        xExpr = xExpr ?: it.xExpr,
                        yExpr = yExpr ?: it.yExpr
                    ) else it
                }
            )
        }
    }

    fun clearGraphFunctions() {
        _uiState.update { it.copy(graphFunctions = emptyList()) }
    }

    fun navigateHistory(direction: String) {
        val state = _uiState.value
        val calcHistory = state.history.filterIsInstance<HistoryEntry.Calculation>()
        if (calcHistory.isEmpty()) return

        val newIndex = when (direction) {
            "prev" -> {
                val idx = state.historyIndex?.let { it - 1 } ?: (calcHistory.size - 1)
                idx.coerceAtLeast(0)
            }
            "next" -> {
                state.historyIndex?.let { idx ->
                    if (idx < calcHistory.size - 1) idx + 1 else null
                } ?: null
            }
            else -> return
        }

        val item = newIndex?.let { calcHistory.getOrNull(it) }
        _uiState.update {
            it.copy(
                historyIndex = newIndex,
                input = item?.input ?: "",
                output = null,
                previewLatex = item?.input?.let { inp -> inputToLatex(inp) } ?: ""
            )
        }
    }

    private fun checkBracketMismatch(input: String): String? {
        if (input.isEmpty()) return null
        var openCount = 0
        for (ch in input) {
            if (ch == '(') openCount++
            if (ch == ')') openCount--
            if (openCount < 0) return "缺少左括号，已自动在开头补全"
        }
        if (openCount > 0) return "缺少 $openCount 个右括号，已自动补全"
        return null
    }

    private fun inputToLatex(input: String): String {
        return com.wwwaker.warer_android.data.engine.inputToLatex(input)
    }

    private fun recordCalculation(input: String, output: ComputeOutput) {
        _uiState.update {
            it.copy(
                isComputing = false,
                output = output,
                history = it.history + HistoryEntry.Calculation(input, output, System.currentTimeMillis()),
                historyIndex = null
            )
        }
        if (historyDao != null) {
            viewModelScope.launch {
                historyDao!!.insert(
                    HistoryEntity(
                        type = "calculation",
                        input = input,
                        source = output.source.ifEmpty { null },
                        latex = output.latex.ifEmpty { null },
                        plainText = output.plainText.ifEmpty { null },
                        numericValue = output.numericValue,
                        isSymbolic = output.isSymbolic,
                        variables = output.variables.ifEmpty { null }?.joinToString(","),
                        errorMessage = output.error,
                        executionTime = output.executionTime.ifEmpty { null }
                    )
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        computeJob?.cancel()
    }
}
