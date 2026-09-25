package com.wwwaker.warer_android.ui.screen

import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.compose.rememberNavController
import com.wwwaker.warer_android.data.settings.SettingsManager
import com.wwwaker.warer_android.navigation.Screen
import com.wwwaker.warer_android.navigation.WarerNavGraph
import com.wwwaker.warer_android.ui.component.DrawerMenu
import com.wwwaker.warer_android.ui.formula.FormulaEditorViewModel
import com.wwwaker.warer_android.ui.viewmodel.CalculatorViewModel
import com.wwwaker.warer_android.ui.viewmodel.HistoryViewModel
import kotlinx.coroutines.launch

@Composable
fun MainScaffold(
    settingsManager: SettingsManager,
    viewModel: CalculatorViewModel,
    formulaViewModel: FormulaEditorViewModel,
    historyViewModel: HistoryViewModel
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // NavController 上移到 Scaffold 层，使侧栏菜单也能触发导航
    val navController = rememberNavController()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                DrawerMenu(
                    settingsManager = settingsManager,
                    onClose = { scope.launch { drawerState.close() } },
                    onOpenFormulaDemo = {
                        scope.launch { drawerState.close() }
                        navController.navigate(Screen.FORMULA_DEMO) { launchSingleTop = true }
                    },
                    onOpenFormulaEditor = {
                        scope.launch { drawerState.close() }
                        navController.navigate(Screen.FORMULA_EDITOR) { launchSingleTop = true }
                    }
                )
            }
        },
        gesturesEnabled = drawerState.isOpen
    ) {
        WarerNavGraph(
            viewModel = viewModel,
            formulaViewModel = formulaViewModel,
            historyViewModel = historyViewModel,
            onOpenDrawer = { scope.launch { drawerState.open() } },
            navController = navController
        )
    }
}
