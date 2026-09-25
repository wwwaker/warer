package com.wwwaker.warer_android.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wwwaker.warer_android.ui.formula.FormulaDemoScreen
import com.wwwaker.warer_android.ui.formula.FormulaEditorScreen
import com.wwwaker.warer_android.ui.formula.FormulaEditorViewModel
import com.wwwaker.warer_android.ui.screen.CalculatorScreen
import com.wwwaker.warer_android.ui.screen.GraphScreen
import com.wwwaker.warer_android.ui.screen.HistoryScreen
import com.wwwaker.warer_android.ui.viewmodel.CalculatorViewModel
import com.wwwaker.warer_android.ui.viewmodel.HistoryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarerNavGraph(
    viewModel: CalculatorViewModel,
    formulaViewModel: FormulaEditorViewModel,
    historyViewModel: HistoryViewModel,
    onOpenDrawer: () -> Unit = {},
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Warer") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "菜单")
                    }
                },
                actions = {},
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar {
                Screen.tabs().forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().route ?: Screen.Calculator.route) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Calculator.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Calculator.route) {
                CalculatorScreen(
                    viewModel = viewModel,
                    formulaViewModel = formulaViewModel,
                    navController = navController
                )
            }
            composable(Screen.Graph.route) {
                GraphScreen(viewModel = viewModel)
            }
            composable(Screen.History.route) {
                HistoryScreen(
                    calculatorViewModel = viewModel,
                    historyViewModel = historyViewModel,
                    navController = navController
                )
            }
            // 开发用：原生公式排版演示（无底部 Tab，从侧栏菜单进入）
            composable(Screen.FORMULA_DEMO) {
                FormulaDemoScreen()
            }
            // 公式编辑器独立页（与计算页共用同一个面板）
            composable(Screen.FORMULA_EDITOR) {
                FormulaEditorScreen()
            }
        }
    }
}
