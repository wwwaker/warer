package com.wwwaker.warer_android.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.wwwaker.warer_android.data.engine.GraphDetector
import com.wwwaker.warer_android.ui.component.InputPanel
import com.wwwaker.warer_android.ui.component.ResultPanel
import com.wwwaker.warer_android.ui.component.ScientificKeyboard
import com.wwwaker.warer_android.ui.viewmodel.CalculatorViewModel

@Composable
fun CalculatorScreen(
    viewModel: CalculatorViewModel,
    navController: NavHostController
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
    ) {
        // Scrollable area: input + preview + result
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp)
        ) {
            InputPanel(
                input = uiState.input,
                previewLatex = uiState.previewLatex,
                bracketHint = uiState.bracketHint,
                onInputChange = { viewModel.onInputChange(it) },
                onCursorPositionChange = { viewModel.setCursorPosition(it) },
                onAutoFix = { viewModel.autoFixBrackets() },
                canPlot = uiState.input.isNotBlank() && Regex("""[a-zA-Z]""").containsMatchIn(
                    uiState.input.replace(Regex("""^[yY]\s*=\s*"""), "")
                ),
                onPlot = {
                    val rawExpr = uiState.input.trim()
                    if (rawExpr.isEmpty()) return@InputPanel
                    val detection = GraphDetector.detect(rawExpr)
                    if (detection.expr.isNotEmpty()) {
                        viewModel.addGraphFunction(
                            expr = detection.expr,
                            fnType = detection.type,
                            xExpr = detection.xExpr,
                            yExpr = detection.yExpr
                        )
                        navController.navigate("graph") {
                            popUpTo("calculator") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            ResultPanel(
                output = uiState.output,
                isComputing = uiState.isComputing
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Keyboard
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            ScientificKeyboard(
                onKeyPressed = { key ->
                    when (key) {
                        "COMPUTE" -> viewModel.compute()
                        "BACKSPACE" -> viewModel.backspace()
                        "CLEAR" -> viewModel.clearInput()
                        else -> viewModel.appendInput(key)
                    }
                }
            )
        }
    }
}
