package com.wwwaker.warer_android.ui.formula

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * 公式编辑器的独立页面（开发用入口，从侧栏菜单进入）。
 *
 * 正文全部在 [FormulaEditorPane] 里 —— 主界面「计算」页用的也是同一个面板，
 * 所以这里只是一层薄包装，避免出现第二套输入实现。
 */
@Composable
fun FormulaEditorScreen(modifier: Modifier = Modifier) {
    FormulaEditorPane(
        viewModel = viewModel(),
        modifier = modifier
    )
}
