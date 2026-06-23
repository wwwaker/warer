package com.wwwaker.warer_android.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    data object Calculator : Screen("calculator", "计算", Icons.Default.Calculate)
    data object Graph : Screen("graph", "图像", Icons.AutoMirrored.Filled.ShowChart)
    data object History : Screen("history", "历史", Icons.Default.History)

    companion object {
        fun tabs(): List<Screen> = listOf(Calculator, Graph, History)
    }
}
