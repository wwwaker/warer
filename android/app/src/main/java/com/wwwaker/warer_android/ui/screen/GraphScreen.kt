package com.wwwaker.warer_android.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wwwaker.warer.core.graph.GraphDetector
import com.wwwaker.warer_android.ui.component.GraphWebView
import com.wwwaker.warer_android.ui.viewmodel.CalculatorViewModel
import com.wwwaker.warer_android.ui.viewmodel.GraphFn

@Composable
fun GraphScreen(viewModel: CalculatorViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    var selectedMode by remember { mutableStateOf("linear") }
    var exprInput by remember { mutableStateOf("") }
    var xExprInput by remember { mutableStateOf("") }
    var yExprInput by remember { mutableStateOf("") }

    var xMin by remember { mutableDoubleStateOf(-10.0) }
    var xMax by remember { mutableDoubleStateOf(10.0) }
    var yMin by remember { mutableDoubleStateOf(-10.0) }
    var yMax by remember { mutableDoubleStateOf(10.0) }

    var isFullscreen by remember { mutableStateOf(false) }

    // Expression edit dialog state
    var editingFn by remember { mutableStateOf<GraphFn?>(null) }
    var editExprText by remember { mutableStateOf("") }
    var editXExpr by remember { mutableStateOf("") }
    var editYExpr by remember { mutableStateOf("") }

    // Color picker dialog state
    var showColorPickerForId by remember { mutableStateOf<String?>(null) }

    val modes = listOf(
        "linear" to "y = f(x)",
        "polar" to "r = f(θ)",
        "parametric" to "t → (x, y)",
        "implicit" to "f(x, y)"
    )

    val colorOptions = listOf(
        "#5b5ef0", "#0ea5a0", "#e5484d", "#f5a623",
        "#30a46c", "#e84393", "#8b5cf6", "#f472b6"
    )

    // Expression edit dialog
    val editingFnValue = editingFn
    if (editingFnValue != null) {
        val fn = editingFnValue
        AlertDialog(
            onDismissRequest = { editingFn = null },
            title = { Text("编辑函数表达式") },
            text = {
                Column {
                    if (fn.fnType == "parametric") {
                        OutlinedTextField(
                            value = editXExpr,
                            onValueChange = { editXExpr = it },
                            label = { Text("x(t)") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = editYExpr,
                            onValueChange = { editYExpr = it },
                            label = { Text("y(t)") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        OutlinedTextField(
                            value = editExprText,
                            onValueChange = { editExprText = it },
                            label = { Text("表达式") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editExprText.isNotBlank() || fn.fnType == "parametric") {
                        viewModel.updateGraphFnExpression(
                            id = fn.id,
                            expr = if (fn.fnType == "parametric") "t($editXExpr, $editYExpr)" else editExprText,
                            xExpr = if (fn.fnType == "parametric") editXExpr else null,
                            yExpr = if (fn.fnType == "parametric") editYExpr else null
                        )
                    }
                    editingFn = null
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { editingFn = null }) { Text("取消") } }
        )
    }

    // Color picker dialog
    val colorPickerFnId = showColorPickerForId
    if (colorPickerFnId != null) {
        AlertDialog(
            onDismissRequest = { showColorPickerForId = null },
            confirmButton = {
                TextButton(onClick = { showColorPickerForId = null }) { Text("关闭") }
            },
            title = { Text("选择颜色") },
            text = {
                Column {
                    colorOptions.chunked(4).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            row.forEach { color ->
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(parseColorHex(color))
                                        .clickable {
                                            viewModel.updateGraphFnColor(colorPickerFnId, color)
                                            showColorPickerForId = null
                                        }
                                        .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (!isFullscreen) {
            // Mode selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                modes.forEach { (mode, label) ->
                    FilterChip(
                        selected = selectedMode == mode,
                        onClick = { selectedMode = mode },
                        label = { Text(label, fontSize = 13.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }

            // Input area
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    when (selectedMode) {
                        "parametric" -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = xExprInput,
                                    onValueChange = { xExprInput = it },
                                    label = { Text("x(t)") },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = yExprInput,
                                    onValueChange = { yExprInput = it },
                                    label = { Text("y(t)") },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        else -> {
                            OutlinedTextField(
                                value = exprInput,
                                onValueChange = { exprInput = it },
                                label = {
                                    Text(
                                        when (selectedMode) {
                                            "polar" -> "r = f(θ)"
                                            "implicit" -> "f(x, y) = 0"
                                            else -> "y = f(x)"
                                        }
                                    )
                                },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            when (selectedMode) {
                                "parametric" -> {
                                    if (xExprInput.isNotBlank() && yExprInput.isNotBlank()) {
                                        val detection = GraphDetector.detect("t($xExprInput, $yExprInput)")
                                        viewModel.addGraphFunction(
                                            expr = detection.expr,
                                            fnType = "parametric",
                                            xExpr = xExprInput,
                                            yExpr = yExprInput
                                        )
                                        xExprInput = ""
                                        yExprInput = ""
                                    }
                                }
                                else -> {
                                    if (exprInput.isNotBlank()) {
                                        val detection = GraphDetector.detect(exprInput)
                                        viewModel.addGraphFunction(
                                            expr = detection.expr,
                                            fnType = detection.type,
                                            xExpr = detection.xExpr,
                                            yExpr = detection.yExpr
                                        )
                                        exprInput = ""
                                    }
                                }
                            }
                        },
                        enabled = when (selectedMode) {
                            "parametric" -> xExprInput.isNotBlank() && yExprInput.isNotBlank()
                            else -> exprInput.isNotBlank()
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("添加")
                    }
                }
            }
        }

        // Graph view
        Box(modifier = Modifier.weight(1f)) {
            GraphWebView(
                functions = uiState.graphFunctions,
                xMin = xMin, xMax = xMax,
                yMin = yMin, yMax = yMax,
                modifier = Modifier.fillMaxSize()
            )

            // Fullscreen toggle (top-right corner)
            IconButton(
                onClick = { isFullscreen = !isFullscreen },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
            ) {
                Icon(
                    if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    contentDescription = if (isFullscreen) "退出全屏" else "全屏",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Viewport controls (bottom-right, only reset button)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                GraphViewportButton("重置") {
                    xMin = -10.0; xMax = 10.0
                    yMin = -10.0; yMax = 10.0
                }
            }
        }

        if (!isFullscreen) {
            // Function list
            if (uiState.graphFunctions.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 2.dp
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .padding(vertical = 4.dp)
                    ) {
                        items(
                            items = uiState.graphFunctions,
                            key = { it.id }
                        ) { fn ->
                            GraphFnRow(
                                fn = fn,
                                onToggleVisibility = { viewModel.toggleGraphFnVisibility(fn.id) },
                                onRemove = { viewModel.removeGraphFunction(fn.id) },
                                onEditExpression = {
                                    editingFn = fn
                                    if (fn.fnType == "parametric") {
                                        editXExpr = fn.xExpr
                                        editYExpr = fn.yExpr
                                    } else {
                                        editExprText = fn.expr
                                    }
                                },
                                onEditColor = { showColorPickerForId = fn.id }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GraphViewportButton(label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(36.dp),
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
        shadowElevation = 2.dp
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.clickable(onClick = onClick)
        ) {
            Text(
                text = label,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GraphFnRow(
    fn: GraphFn,
    onToggleVisibility: () -> Unit,
    onRemove: () -> Unit,
    onEditExpression: () -> Unit,
    onEditColor: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Color indicator (clickable)
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(parseColorHex(fn.color))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .clickable { onEditColor() }
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Expression label (clickable to edit)
        Text(
            text = buildFnLabel(fn),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            maxLines = 1,
            modifier = Modifier
                .weight(1f)
                .clickable { onEditExpression() }
        )

        // Visibility toggle
        IconButton(onClick = onToggleVisibility, modifier = Modifier.size(32.dp)) {
            Icon(
                if (fn.hidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                contentDescription = if (fn.hidden) "显示" else "隐藏",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Delete
        IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.Close,
                contentDescription = "删除",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

private fun buildFnLabel(fn: GraphFn): String {
    return when (fn.fnType) {
        "parametric" -> "x=${fn.xExpr}, y=${fn.yExpr}"
        "polar" -> "r=${fn.expr}"
        "implicit" -> "f=${fn.expr}"
        else -> "y=${fn.expr}"
    }
}

private fun parseColorHex(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        Color(0xFF5B5EF0)
    }
}
