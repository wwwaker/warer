package com.wwwaker.warer_android.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.ui.unit.sp
import com.wwwaker.warer_android.ui.theme.MonospaceTypography

@Composable
fun InputPanel(
    input: String,
    previewLatex: String,
    bracketHint: String?,
    onInputChange: (String) -> Unit,
    onCursorPositionChange: (Int) -> Unit,
    onAutoFix: () -> Unit,
    canPlot: Boolean = false,
    onPlot: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Expression input field with plot button
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 12.dp, end = 4.dp)
            ) {
                BasicTextField(
                    value = input,
                    onValueChange = { onInputChange(it) },
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 10.dp),
                    textStyle = MonospaceTypography.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        if (input.isEmpty()) {
                            Text(
                                text = "输入表达式...",
                                style = MonospaceTypography,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                        innerTextField()
                    }
                )

                if (canPlot) {
                    Spacer(modifier = Modifier.width(4.dp))
                    FilledTonalIconButton(
                        onClick = onPlot,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ShowChart,
                            contentDescription = "绘图",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // KaTeX preview
        if (previewLatex.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                KatexWebView(
                    latex = previewLatex,
                    displayMode = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp, max = 80.dp)
                )
            }
        }

        // Bracket hint
        if (bracketHint != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAutoFix() }
            ) {
                Text(
                    text = "⚠️ $bracketHint",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
