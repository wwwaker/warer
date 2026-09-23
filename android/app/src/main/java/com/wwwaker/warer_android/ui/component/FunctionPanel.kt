package com.wwwaker.warer_android.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class FnGroup(
    val name: String,
    val items: List<FnItem>
)

data class FnItem(
    val label: String,
    val value: String
)

data class Template(
    val label: String,
    val expr: String,
    val category: String
)

private val FN_GROUPS = listOf(
    FnGroup("三角函数", listOf(
        FnItem("sin", "sin("), FnItem("cos", "cos("), FnItem("tan", "tan("),
        FnItem("asin", "asin("), FnItem("acos", "acos("), FnItem("atan", "atan("),
        FnItem("sinh", "sinh("), FnItem("cosh", "cosh("), FnItem("tanh", "tanh(")
    )),
    FnGroup("符号变量", listOf(
        FnItem("x", "x"), FnItem("y", "y"), FnItem("t", "t"),
        FnItem("θ", "theta"), FnItem("π", "pi"), FnItem("e", "e")
    )),
    FnGroup("高级运算", listOf(
        FnItem("求导", "diff("), FnItem("积分", "integrate("),
        FnItem("求解", "solve("), FnItem("数值求根", "nsolve("),
        FnItem("化简", "simplify("), FnItem("极限", "limit("),
        FnItem("展开", "series("), FnItem("微分方程", "dsolve(")
    )),
    FnGroup("矩阵运算", listOf(
        FnItem("行列式", "det("), FnItem("逆矩阵", "inv("),
        FnItem("转置", "transpose("), FnItem("特征值", "eigenvals("),
        FnItem("特征向量", "eigenvects("), FnItem("秩", "rank(")
    ))
)

private val TEMPLATES = listOf(
    Template("求导", "diff(x^3 + 2*x, x)", "微积分"),
    Template("不定积分", "integrate(x^2, x)", "微积分"),
    Template("定积分", "integrate(x^2, x, 0, 1)", "微积分"),
    Template("解方程", "solve(x^2 - 4, x)", "方程"),
    Template("数值求根", "nsolve(x^5 - x - 1, x, 1)", "方程"),
    Template("化简", "simplify(sin(x)^2 + cos(x)^2)", "化简"),
    Template("极限", "limit(sin(x)/x, x, 0)", "分析"),
    Template("展开", "series(sin(x), x, 0, 5)", "分析"),
    Template("微分方程", "dsolve(diff(f(x), x) - f(x), f(x))", "方程"),
    Template("矩阵行列式", "det([[1,2],[3,4]])", "矩阵"),
    Template("矩阵逆", "inv([[2,1],[1,2]])", "矩阵"),
    Template("特征值", "eigenvals([[2,0],[0,3]])", "矩阵")
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FunctionPanel(
    onInsert: (String) -> Unit,
    onTemplate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPanel by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf("函数") }

    if (!showPanel) {
        FilledTonalButton(
            onClick = { showPanel = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("f(x) 函数与模板")
        }
        return
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = { selectedTab = "函数" }) {
                    Text(
                        text = "函数",
                        fontWeight = if (selectedTab == "函数") androidx.compose.ui.text.font.FontWeight.Bold
                                   else androidx.compose.ui.text.font.FontWeight.Normal
                    )
                }
                TextButton(onClick = { selectedTab = "模板" }) {
                    Text(
                        text = "模板",
                        fontWeight = if (selectedTab == "模板") androidx.compose.ui.text.font.FontWeight.Bold
                                   else androidx.compose.ui.text.font.FontWeight.Normal
                    )
                }
            }

            if (selectedTab == "函数") {
                FN_GROUPS.forEach { group ->
                    var expanded by remember { mutableStateOf(false) }
                    TextButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = group.name,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (expanded) "▲" else "▼",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    AnimatedVisibility(visible = expanded) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            group.items.forEach { item ->
                                FilterChip(
                                    selected = false,
                                    onClick = { onInsert(item.value) },
                                    label = { Text(item.label) }
                                )
                            }
                        }
                    }
                }
            } else {
                val categories = TEMPLATES.map { it.category }.distinct()
                categories.forEach { category ->
                    Text(
                        text = category,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TEMPLATES.filter { it.category == category }.forEach { template ->
                            FilterChip(
                                selected = false,
                                onClick = { onTemplate(template.expr) },
                                label = { Text(template.label) }
                            )
                        }
                    }
                }
            }
        }
    }
}
