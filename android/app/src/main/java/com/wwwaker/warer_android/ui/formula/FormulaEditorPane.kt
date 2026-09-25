package com.wwwaker.warer_android.ui.formula

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wwwaker.warer.core.formula.convert.CommandBuilder
import com.wwwaker.warer.core.formula.convert.CompiledFormula
import com.wwwaker.warer.core.formula.model.BigOp
import com.wwwaker.warer.core.formula.model.BigOpKind
import com.wwwaker.warer.core.formula.model.Delim
import com.wwwaker.warer.core.formula.model.DelimKind
import com.wwwaker.warer.core.formula.model.Frac
import com.wwwaker.warer.core.formula.model.Func
import com.wwwaker.warer.core.formula.model.Node
import com.wwwaker.warer.core.formula.model.Num
import com.wwwaker.warer.core.formula.model.Row
import com.wwwaker.warer.core.formula.model.Script
import com.wwwaker.warer.core.formula.model.Sqrt
import com.wwwaker.warer.core.formula.model.Sym
import com.wwwaker.warer.core.formula.model.toPlainText

/**
 * 公式编辑器面板 —— 主界面与实验页共用的**唯一**输入/结果区域。
 *
 * 完整链路：
 * ```
 * 按键 → FormulaEditor（纯函数改 AST）→ EditHistory（快照撤销）
 *      → FormulaEditorView（布局 + Canvas 渲染 + 光标）
 *      → CommandBuilder（AST → 引擎命令）
 *      → ComputeDispatcher（能力分类 → 本地 / 云端）
 *      → 结果：符号解转成 AST 后用 FormulaView 渲染成真正的公式
 * ```
 *
 * UI 层只做两件事：**画出来** 和 **把事件转给 ViewModel**。所有判断逻辑都在 `:core`。
 *
 * @param onPlot 非空时在操作行显示「绘图」入口，参数是当前编译出的引擎命令。
 */
@Composable
fun FormulaEditorPane(
    viewModel: FormulaEditorViewModel,
    modifier: Modifier = Modifier,
    onPlot: ((String) -> Unit)? = null
) {
    val ui by viewModel.uiState.collectAsState()
    val editorScroll = rememberScrollState()

    // 命令随公式实时重编译：用户能随时看到"自己输入的东西会变成什么"
    val compiled = remember(ui.editor.root) { CommandBuilder.compile(ui.editor.root) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CommandPreview(compiled)

        // 编辑区
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(editorScroll)
                .padding(vertical = 6.dp)
        ) {
            FormulaEditorView(
                state = ui.editor,
                // 点击定位只移动光标：AST 未变，因此不会被记入撤销历史
                onCaretChange = { viewModel.setCaret(it) }
            )
        }

        ui.outcome?.let { OutcomeCard(it) }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.submit() },
                enabled = !ui.computing,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (ui.computing) "计算中…" else "计 算")
            }
            onPlot?.let { plot ->
                val canPlot = ui.editor.root.let {
                    Regex("""[a-zA-Z]""").containsMatchIn(compiled.command)
                }
                OutlinedButton(
                    onClick = { plot(compiled.command) },
                    enabled = canPlot,
                    modifier = Modifier.weight(0.7f)
                ) {
                    Text("绘图")
                }
            }
            OutlinedButton(
                onClick = { viewModel.clear() },
                modifier = Modifier.weight(0.6f)
            ) {
                Text("清空")
            }
        }

        EditorKeyboard(
            canUndo = ui.canUndo,
            canRedo = ui.canRedo,
            onUndo = { viewModel.undo() },
            onRedo = { viewModel.redo() },
            onTemplate = { viewModel.insertStructure(it) },
            onAtom = { viewModel.insert(it) },
            onNextSlot = { viewModel.focusNextEmptySlot() },
            onBackspace = { viewModel.backspace() },
            onLeft = { viewModel.moveLeft() },
            onRight = { viewModel.moveRight() }
        )
    }
}

// ============================ 展示组件 ============================

@Composable
private fun CommandPreview(compiled: CompiledFormula) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = "交给计算引擎的命令",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = compiled.command.ifBlank { "（空）" },
                style = MaterialTheme.typography.bodyLarge
            )
            compiled.warnings.forEach { warning ->
                Text(
                    text = "注意：$warning",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun OutcomeCard(outcome: EditorOutcome) {
    val container = if (outcome.isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val content = if (outcome.isError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    Surface(
        color = container,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = outcome.label,
                style = MaterialTheme.typography.labelSmall,
                color = content
            )
            val rendered = outcome.rendered
            if (rendered != null) {
                // 符号结果渲染成真正的二维公式；太宽时横向滚动而不是折行
                Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    FormulaView(node = rendered, fontSize = 30.sp, color = content)
                }
            } else {
                Text(
                    text = outcome.value,
                    style = MaterialTheme.typography.titleMedium,
                    color = content
                )
            }
        }
    }
}

// ============================ 键盘 ============================

@Composable
private fun EditorKeyboard(
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onTemplate: (Node) -> Unit,
    onAtom: (Node) -> Unit,
    onNextSlot: () -> Unit,
    onBackspace: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit
) {
    val templates = remember { templateNodes() }
    val digits = remember { digitNodes() }
    val operators = remember { operatorNodes() }
    val letters = remember { letterNodes() }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        KeyRow(templates.map { (label, node) -> label to { onTemplate(node) } })
        KeyRow((digits + operators).map { node -> node.toPlainText() to { onAtom(node) } })
        KeyRow(letters.map { node -> node.toPlainText() to { onAtom(node) } })

        // 编辑操作行：撤销 / 重做放在最左，与方向键分开放，避免误触
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            KeyChip("↶", enabled = canUndo, action = onUndo)
            KeyChip("↷", enabled = canRedo, action = onRedo)
            KeyChip("←", action = onLeft)
            KeyChip("→", action = onRight)
            KeyChip("跳到空位", action = onNextSlot)
            KeyChip("⌫", action = onBackspace)
        }
    }
}

@Composable
private fun KeyRow(keys: List<Pair<String, () -> Unit>>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        keys.forEach { (label, action) -> KeyChip(label, action = action) }
    }
}

@Composable
private fun KeyChip(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    action: () -> Unit
) {
    OutlinedButton(
        onClick = action,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minWidth = 44.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.titleMedium)
    }
}

// ============================ 键位内容 ============================

private fun templateNodes(): List<Pair<String, Node>> = listOf(
    "a/b" to Frac(Row(), Row()),
    "□²" to Script(Row(), superscript = Row()),
    "□ₙ" to Script(Row(), subscript = Row()),
    "□ₙ²" to Script(Row(), superscript = Row(), subscript = Row()),
    "√" to Sqrt(Row()),
    "∛" to Sqrt(Row(), index = Row()),
    "( )" to Delim(DelimKind.PAREN, Row(), DelimKind.PAREN),
    "[ ]" to Delim(DelimKind.BRACKET, Row(), DelimKind.BRACKET),
    "| |" to Delim(DelimKind.ABS, Row(), DelimKind.ABS),
    "∫" to BigOp(BigOpKind.INTEGRAL, lower = Row(), upper = Row()),
    "∑" to BigOp(BigOpKind.SUM, lower = Row(), upper = Row()),
    "∏" to BigOp(BigOpKind.PRODUCT, lower = Row(), upper = Row()),
    "lim" to BigOp(BigOpKind.LIM, lower = Row())
)

private fun digitNodes(): List<Node> =
    "1234567890".map { Num(it.toString()) }

private fun operatorNodes(): List<Node> =
    listOf('+', '-', '*', '/', '^', '=', ',', '.', '(', ')').map { Sym(it) }

private fun letterNodes(): List<Node> = buildList {
    listOf('x', 'y', 'a', 'b', 'c', 'n', 'k').forEach { add(Sym(it)) }
    add(Func("sin"))
    add(Func("cos"))
    add(Func("tan"))
    add(Func("ln"))
    add(Func("log"))
}
