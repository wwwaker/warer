package com.wwwaker.warer_android.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.wwwaker.warer.core.graph.GraphDetector
import com.wwwaker.warer_android.ui.formula.FormulaEditorPane
import com.wwwaker.warer_android.ui.formula.FormulaEditorViewModel
import com.wwwaker.warer_android.ui.viewmodel.CalculatorViewModel

/**
 * 计算页 —— **公式编辑器就是主输入**。
 *
 * 这里不再有单行文本框，也没有 KaTeX WebView：输入与结果都由
 * [FormulaEditorPane]（自研 Compose Canvas 排版引擎）承担，因此断网也能正常显示。
 */
@Composable
fun CalculatorScreen(
    viewModel: CalculatorViewModel,
    formulaViewModel: FormulaEditorViewModel,
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    FormulaEditorPane(
        viewModel = formulaViewModel,
        modifier = modifier,
        onPlot = { command ->
            val detection = GraphDetector.detect(command)
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
}
