package com.wwwaker.warer_android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wwwaker.warer_android.data.db.HistoryDao
import com.wwwaker.warer_android.data.db.HistoryEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 一条待绘制的函数。 */
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
    val graphFunctions: List<GraphFn> = emptyList()
)

/**
 * 计算页与绘图页共享的状态。
 *
 * **注意：输入与计算的职责已经不在这里了。** 公式编辑器有自己的一套
 * （[com.wwwaker.warer_android.ui.formula.FormulaEditorViewModel] 持有 AST/光标/结果，
 * 计算走 `:core` 的 `ComputeDispatcher`）。这里只剩"待绘制的函数列表"，
 * 因为绘图页需要跨 Tab 共享它。
 *
 * 历史原因：本类曾同时承载"单行文本输入 + 本地/云端调度"，
 * 那套逻辑在公式编辑器接管主界面后已被删除，避免同一个策略存在两份实现。
 */
class CalculatorViewModel(
    private val historyDao: HistoryDao? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalculatorUiState())
    val uiState: StateFlow<CalculatorUiState> = _uiState.asStateFlow()

    private var graphCounter = 0

    private val COLORS = listOf(
        "#5b5ef0", "#0ea5a0", "#e5484d", "#f5a623",
        "#30a46c", "#e84393", "#8b5cf6", "#f472b6"
    )

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

        val dao = historyDao
        if (saveToHistory && dao != null) {
            val color = COLORS[(_uiState.value.graphFunctions.size - 1) % COLORS.size]
            viewModelScope.launch {
                dao.insert(
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
}
