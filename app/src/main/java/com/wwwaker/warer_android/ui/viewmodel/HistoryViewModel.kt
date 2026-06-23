package com.wwwaker.warer_android.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wwwaker.warer_android.WarerApplication
import com.wwwaker.warer_android.data.db.HistoryDao
import com.wwwaker.warer_android.data.db.HistoryEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class HistoryUiState(
    val entries: List<HistoryEntity> = emptyList(),
    val groupedEntries: List<HistoryGroup> = emptyList(),
    val searchQuery: String = "",
    val filterType: String = "all", // all, calculation, graph
    val isLoading: Boolean = true
)

data class HistoryGroup(
    val dateLabel: String,
    val entries: List<HistoryEntity>
)

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val dao: HistoryDao = (application as WarerApplication).database.historyDao()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filterType = MutableStateFlow("all")
    val filterType: StateFlow<String> = _filterType.asStateFlow()

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(_searchQuery, _filterType) { query, filter ->
                Pair(query, filter)
            }.flatMapLatest { (query, filter) ->
                when {
                    query.isNotBlank() -> dao.searchHistory(query)
                    filter != "all" -> dao.getHistoryByType(filter)
                    else -> dao.getAllHistory()
                }
            }.collect { entries ->
                _uiState.update {
                    it.copy(
                        entries = entries,
                        groupedEntries = groupByDate(entries),
                        isLoading = false
                    )
                }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilterType(type: String) {
        _filterType.value = type
    }

    fun deleteEntry(entry: HistoryEntity) {
        viewModelScope.launch { dao.delete(entry) }
    }

    fun clearAll() {
        viewModelScope.launch { dao.deleteAll() }
    }

    fun deleteEntryById(id: Long) {
        viewModelScope.launch {
            val entry = _uiState.value.entries.find { it.id == id }
            if (entry != null) dao.delete(entry)
        }
    }

    private fun groupByDate(entries: List<HistoryEntity>): List<HistoryGroup> {
        if (entries.isEmpty()) return emptyList()

        val calendar = Calendar.getInstance()
        val today = calendar.time

        calendar.add(Calendar.DAY_OF_YEAR, -1)
        val yesterday = calendar.time

        calendar.add(Calendar.DAY_OF_YEAR, -5)
        val thisWeek = calendar.time

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayStr = dateFormat.format(today)
        val yesterdayStr = dateFormat.format(yesterday)

        val groups = mutableListOf<HistoryGroup>()
        var currentDate = ""
        var currentEntries = mutableListOf<HistoryEntity>()

        for (entry in entries) {
            val entryDateStr = dateFormat.format(Date(entry.timestamp))

            val label = when (entryDateStr) {
                todayStr -> "今天"
                yesterdayStr -> "昨天"
                else -> {
                    if (entry.timestamp >= thisWeek.time) "本周"
                    else entryDateStr
                }
            }

            if (label != currentDate && currentEntries.isNotEmpty()) {
                groups.add(HistoryGroup(currentDate, currentEntries.toList()))
                currentEntries.clear()
            }
            currentDate = label
            currentEntries.add(entry)
        }

        if (currentEntries.isNotEmpty()) {
            groups.add(HistoryGroup(currentDate, currentEntries))
        }

        return groups
    }

    fun saveCalculation(
        input: String,
        source: String,
        latex: String?,
        plainText: String?,
        numericValue: Double?,
        isSymbolic: Boolean,
        variables: List<String>?,
        errorMessage: String?,
        executionTime: String?
    ) {
        viewModelScope.launch {
            dao.insert(
                HistoryEntity(
                    type = "calculation",
                    input = input,
                    source = source,
                    latex = latex,
                    plainText = plainText,
                    numericValue = numericValue,
                    isSymbolic = isSymbolic,
                    variables = variables?.joinToString(","),
                    errorMessage = errorMessage,
                    executionTime = executionTime,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    fun saveGraph(
        input: String,
        fnType: String,
        expr: String,
        xExpr: String?,
        yExpr: String?,
        color: String?
    ) {
        viewModelScope.launch {
            dao.insert(
                HistoryEntity(
                    type = "graph",
                    input = input,
                    fnType = fnType,
                    expr = expr,
                    xExpr = xExpr,
                    yExpr = yExpr,
                    graphColor = color,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }
}
