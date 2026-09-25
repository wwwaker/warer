package com.wwwaker.warer_android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wwwaker.warer_android.ui.formula.FormulaEditorViewModel
import com.wwwaker.warer_android.ui.screen.MainScaffold
import com.wwwaker.warer_android.ui.theme.WarerAndroidTheme
import com.wwwaker.warer_android.ui.viewmodel.CalculatorViewModel
import com.wwwaker.warer_android.ui.viewmodel.HistoryViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as WarerApplication

        setContent {
            val settingsManager = remember { app.settingsManager }
            val themeMode by settingsManager.themeMode.collectAsState(initial = "system")
            val historyDao = remember { app.database.historyDao() }
            val calculatorViewModel: CalculatorViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return CalculatorViewModel(historyDao = historyDao) as T
                    }
                }
            )
            val historyViewModel: HistoryViewModel = viewModel()

            // 公式编辑器是主界面的一部分，状态必须活到 Activity 级，否则切 Tab 会丢输入
            val formulaEditorViewModel: FormulaEditorViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return FormulaEditorViewModel(historyDao = historyDao) as T
                    }
                }
            )

            WarerAndroidTheme(
                themeMode = themeMode,
                dynamicColor = true
            ) {
                MainScaffold(
                    settingsManager = settingsManager,
                    viewModel = calculatorViewModel,
                    formulaViewModel = formulaEditorViewModel,
                    historyViewModel = historyViewModel
                )
            }
        }
    }
}
