package com.wwwaker.warer_android.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wwwaker.warer_android.ui.component.FunctionPanel
import com.wwwaker.warer_android.ui.formula.FormulaDemoScreen
import com.wwwaker.warer_android.ui.formula.FormulaEditorScreen
import com.wwwaker.warer_android.ui.screen.CalculatorScreen
import com.wwwaker.warer_android.ui.screen.GraphScreen
import com.wwwaker.warer_android.ui.screen.HistoryScreen
import com.wwwaker.warer_android.ui.viewmodel.CalculatorViewModel
import com.wwwaker.warer_android.ui.viewmodel.HistoryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarerNavGraph(
    viewModel: CalculatorViewModel,
    historyViewModel: HistoryViewModel,
    onOpenDrawer: () -> Unit = {},
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    var showFnPanel by remember { mutableStateOf(false) }

    // Function panel dialog (outside Scaffold to avoid layout interference)
    if (showFnPanel) {
        AlertDialog(
            onDismissRequest = { showFnPanel = false },
            title = {
                Text("函数与模板", style = MaterialTheme.typography.titleMedium)
            },
            text = {
                FunctionPanel(
                    onInsert = { viewModel.appendInput(it) },
                    onTemplate = { viewModel.onInputChange(it) },
                    modifier = Modifier.padding(0.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = { showFnPanel = false }) {
                    Text("关闭")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Warer") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "菜单")
                    }
                },
                actions = {
                    if (currentDestination?.route == Screen.Calculator.route) {
                        IconButton(onClick = { showFnPanel = true }) {
                            Text(
                                text = "f(x)",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
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
            // 公式编辑器实验页（阶段 1 交付）
            composable(Screen.FORMULA_EDITOR) {
                FormulaEditorScreen()
            }
        }
    }
}
