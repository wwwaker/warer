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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.wwwaker.warer_android.data.engine.GraphDetector
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

    val modes = listOf(
        "linear" to "y = f(x)",
        "polar" to "r = f(θ)",
        "parametric" to "t → (x, y)",
        "implicit" to "f(x, y)"
    )

    Column(modifier = Modifier.fillMaxSize()) {
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

        // Graph view
        Box(modifier = Modifier.weight(1f)) {
            GraphWebView(
                functions = uiState.graphFunctions,
                xMin = xMin, xMax = xMax,
                yMin = yMin, yMax = yMax,
                modifier = Modifier.fillMaxSize()
            )

            // Viewport controls overlay
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ViewportButton("+x") {
                    val span = xMax - xMin
                    xMin += span * 0.1; xMax += span * 0.1
                }
                ViewportButton("-x") {
                    val span = xMax - xMin
                    xMin -= span * 0.1; xMax -= span * 0.1
                }
                ViewportButton("+y") {
                    val span = yMax - yMin
                    yMin += span * 0.1; yMax += span * 0.1
                }
                ViewportButton("-y") {
                    val span = yMax - yMin
                    yMin -= span * 0.1; yMax -= span * 0.1
                }
                ViewportButton("缩放+") {
                    val cx = (xMin + xMax) / 2; val cy = (yMin + yMax) / 2
                    val xs = (xMax - xMin) * 0.5; val ys = (yMax - yMin) * 0.5
                    xMin = cx - xs; xMax = cx + xs
                    yMin = cy - ys; yMax = cy + ys
                }
                ViewportButton("缩放-") {
                    val cx = (xMin + xMax) / 2; val cy = (yMin + yMax) / 2
                    val xs = (xMax - xMin) * 1.5; val ys = (yMax - yMin) * 1.5
                    xMin = cx - xs; xMax = cx + xs
                    yMin = cy - ys; yMax = cy + ys
                }
                ViewportButton("重置") {
                    xMin = -10.0; xMax = 10.0
                    yMin = -10.0; yMax = 10.0
                }
            }
        }

        // Function list
        if (uiState.graphFunctions.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 2.dp
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .padding(vertical = 4.dp)
                ) {
                    items(
                        items = uiState.graphFunctions,
                        key = { it.id }
                    ) { fn ->
                        GraphFnRow(
                            fn = fn,
                            onToggleVisibility = { viewModel.toggleGraphFnVisibility(fn.id) },
                            onRemove = { viewModel.removeGraphFunction(fn.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewportButton(label: String, onClick: () -> Unit) {
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
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Color indicator
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(parseColor(fn.color))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Expression label
        Text(
            text = buildFnLabel(fn),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            maxLines = 1,
            modifier = Modifier.weight(1f)
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

private fun parseColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        Color(0xFF5B5EF0)
    }
}
