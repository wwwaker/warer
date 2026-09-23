package com.wwwaker.warer_android.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ScientificKeyboard(
    onKeyPressed: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val keyShape = RoundedCornerShape(6.dp)
    val keyBg = MaterialTheme.colorScheme.surfaceVariant
    val accentColor = MaterialTheme.colorScheme.primary
    val keyBgAction = MaterialTheme.colorScheme.errorContainer
    val keyTextColor = MaterialTheme.colorScheme.onSurface
    val keyTextAccent = MaterialTheme.colorScheme.onPrimary
    val keyTextAction = MaterialTheme.colorScheme.onErrorContainer

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            KeyBtn("sin(", keyShape, keyBg, keyTextColor) { onKeyPressed("sin(") }
            KeyBtn("cos(", keyShape, keyBg, keyTextColor) { onKeyPressed("cos(") }
            KeyBtn("tan(", keyShape, keyBg, keyTextColor) { onKeyPressed("tan(") }
            KeyBtn("ln(", keyShape, keyBg, keyTextColor) { onKeyPressed("ln(") }
            KeyBtn("⌫", keyShape, keyBg, keyTextColor) { onKeyPressed("BACKSPACE") }
            KeyBtn("C", keyShape, keyBgAction, keyTextAction) { onKeyPressed("CLEAR") }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            KeyBtn("7", keyShape, keyBg, keyTextColor) { onKeyPressed("7") }
            KeyBtn("8", keyShape, keyBg, keyTextColor) { onKeyPressed("8") }
            KeyBtn("9", keyShape, keyBg, keyTextColor) { onKeyPressed("9") }
            KeyBtn("÷", keyShape, keyBg, keyTextColor) { onKeyPressed("/") }
            KeyBtn("x²", keyShape, keyBg, keyTextColor) { onKeyPressed("^2") }
            KeyBtn("√(", keyShape, keyBg, keyTextColor) { onKeyPressed("sqrt(") }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            KeyBtn("4", keyShape, keyBg, keyTextColor) { onKeyPressed("4") }
            KeyBtn("5", keyShape, keyBg, keyTextColor) { onKeyPressed("5") }
            KeyBtn("6", keyShape, keyBg, keyTextColor) { onKeyPressed("6") }
            KeyBtn("×", keyShape, keyBg, keyTextColor) { onKeyPressed("*") }
            KeyBtn("|x|", keyShape, keyBg, keyTextColor) { onKeyPressed("abs(") }
            KeyBtn("^", keyShape, keyBg, keyTextColor) { onKeyPressed("^") }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            KeyBtn("1", keyShape, keyBg, keyTextColor) { onKeyPressed("1") }
            KeyBtn("2", keyShape, keyBg, keyTextColor) { onKeyPressed("2") }
            KeyBtn("3", keyShape, keyBg, keyTextColor) { onKeyPressed("3") }
            KeyBtn("−", keyShape, keyBg, keyTextColor) { onKeyPressed("-") }
            KeyBtn("π", keyShape, keyBg, keyTextColor) { onKeyPressed("pi") }
            KeyBtn("e", keyShape, keyBg, keyTextColor) { onKeyPressed("e") }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            KeyBtn("0", keyShape, keyBg, keyTextColor) { onKeyPressed("0") }
            KeyBtn(".", keyShape, keyBg, keyTextColor) { onKeyPressed(".") }
            KeyBtn(",", keyShape, keyBg, keyTextColor) { onKeyPressed(",") }
            KeyBtn("+", keyShape, keyBg, keyTextColor) { onKeyPressed("+") }
            KeyBtn("(", keyShape, keyBg, keyTextColor) { onKeyPressed("(") }
            KeyBtn(")", keyShape, keyBg, keyTextColor) { onKeyPressed(")") }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            KeyBtn("θ", keyShape, keyBg, keyTextColor) { onKeyPressed("theta") }
            KeyBtn("x", keyShape, keyBg, keyTextColor) { onKeyPressed("x") }
            KeyBtn("y", keyShape, keyBg, keyTextColor) { onKeyPressed("y") }
            KeyBtn("t", keyShape, keyBg, keyTextColor) { onKeyPressed("t") }
            WideKeyBtn("=", keyShape, accentColor, keyTextAccent) { onKeyPressed("COMPUTE") }
        }
    }
}

@Composable
private fun RowScope.KeyBtn(
    label: String,
    shape: RoundedCornerShape,
    bg: Color,
    textColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1.3f)
            .clip(shape)
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = textColor,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun RowScope.WideKeyBtn(
    label: String,
    shape: RoundedCornerShape,
    bg: Color,
    textColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .weight(2.1f)
            .aspectRatio(2.1f)
            .clip(shape)
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            textAlign = TextAlign.Center
        )
    }
}
