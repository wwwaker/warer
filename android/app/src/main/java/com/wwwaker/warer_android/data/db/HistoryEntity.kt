package com.wwwaker.warer_android.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: String = "calculation",
    val input: String = "",
    val source: String? = null,
    val latex: String? = null,
    val plainText: String? = null,
    val numericValue: Double? = null,
    val isSymbolic: Boolean = false,
    val variables: String? = null,
    val errorMessage: String? = null,
    val fnType: String? = null,
    val expr: String? = null,
    val xExpr: String? = null,
    val yExpr: String? = null,
    val graphColor: String? = null,
    val executionTime: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
