package com.wwwaker.warer_android.ui.screen

import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import com.wwwaker.warer_android.data.settings.SettingsManager
import com.wwwaker.warer_android.navigation.WarerNavGraph
import com.wwwaker.warer_android.ui.component.DrawerMenu
import com.wwwaker.warer_android.ui.viewmodel.CalculatorViewModel
import com.wwwaker.warer_android.ui.viewmodel.HistoryViewModel
import kotlinx.coroutines.launch

@Composable
fun MainScaffold(
    settingsManager: SettingsManager,
    viewModel: CalculatorViewModel,
    historyViewModel: HistoryViewModel
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                DrawerMenu(
                    settingsManager = settingsManager,
                    onClose = { scope.launch { drawerState.close() } }
                )
            }
        },
        gesturesEnabled = drawerState.isOpen
    ) {
        WarerNavGraph(
            viewModel = viewModel,
            historyViewModel = historyViewModel,
            onOpenDrawer = { scope.launch { drawerState.open() } }
        )
    }
}
