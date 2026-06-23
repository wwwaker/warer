package com.wwwaker.warer_android.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wwwaker.warer_android.data.api.RetrofitClient
import com.wwwaker.warer_android.data.settings.SettingsManager
import kotlinx.coroutines.launch

@Composable
fun DrawerMenu(
    settingsManager: SettingsManager,
    onClose: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var baseUrl by remember { mutableStateOf("") }
    var themeMode by remember { mutableStateOf("system") }
    var precisionValue by remember { mutableFloatStateOf(15f) }
    var healthStatus by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        settingsManager.baseUrl.collect { baseUrl = it }
    }
    LaunchedEffect(Unit) {
        settingsManager.themeMode.collect { themeMode = it }
    }
    LaunchedEffect(Unit) {
        settingsManager.precision.collect { precisionValue = it.toFloat() }
    }

    Column(
        modifier = Modifier
            .width(300.dp)
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        Text(
            text = "Warer",
            style = MaterialTheme.typography.headlineLarge
        )
        Text(
            text = "v1.0.0",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // Base URL
        Text("后端地址", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("http://121.40.223.203/") },
            singleLine = true
        )
        Spacer(modifier = Modifier.height(4.dp))
        Button(
            onClick = {
                scope.launch {
                    settingsManager.setBaseUrl(baseUrl)
                    RetrofitClient.updateBaseUrl(baseUrl)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("保存地址")
        }

        // Health check
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                scope.launch {
                    healthStatus = "检查中..."
                    try {
                        val response = RetrofitClient.getApiService().healthCheck()
                        healthStatus = if (response.status == "ok") "连接成功 ✓" else "异常: ${response.status}"
                    } catch (e: Exception) {
                        healthStatus = "连接失败: ${e.message}"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("测试连接")
        }
        healthStatus?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = if (it.contains("✓")) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // Theme mode
        Text("主题模式", style = MaterialTheme.typography.titleMedium)
        listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { (mode, label) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                RadioButton(
                    selected = themeMode == mode,
                    onClick = {
                        themeMode = mode
                        scope.launch { settingsManager.setThemeMode(mode) }
                    }
                )
                Text(label)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // Precision
        Text("精度: ${precisionValue.toInt()}", style = MaterialTheme.typography.titleMedium)
        Slider(
            value = precisionValue,
            onValueChange = { precisionValue = it },
            onValueChangeFinished = {
                scope.launch { settingsManager.setPrecision(precisionValue.toInt()) }
            },
            valueRange = 1f..50f,
            steps = 48
        )
    }
}
