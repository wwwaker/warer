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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wwwaker.warer.core.engine.CapabilityClassifier
import com.wwwaker.warer.core.engine.LocalFirstEvaluator
import com.wwwaker.warer.core.engine.NumericResult
import com.wwwaker.warer.core.formula.convert.CommandBuilder
import com.wwwaker.warer.core.formula.convert.CompiledFormula
import com.wwwaker.warer.core.formula.edit.EditHistory
import com.wwwaker.warer.core.formula.edit.EditorState
import com.wwwaker.warer.core.formula.edit.FormulaEditor
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
import java.math.BigDecimal
import java.math.MathContext

/**
 * 公式编辑器实验页 —— 「阶段 1」的交付界面。
 *
 * 完整链路：
 * ```
 * 按键 → FormulaEditor（纯函数改 AST）
 *      → EditHistory（撤销 / 重做，快照式）
 *      → FormulaEditorView（布局 + Canvas 渲染 + 光标）
 *      → CommandBuilder（AST → 引擎命令）
 *      → LocalFirstEvaluator（能力分类 → Tier 0 数值求值）
 *      → 结果
 * ```
 *
 * UI 层只做两件事：**画出来** 和 **把事件转给纯函数**。所有判断逻辑都在 `:core`，被单元测试覆盖。
 */
@Composable
fun FormulaEditorScreen(modifier: Modifier = Modifier) {
    val history = remember { EditHistory() }
    var state by remember { mutableStateOf(history.current) }
    var outcome by remember { mutableStateOf<EditorOutcome?>(null) }
    val editorScroll = rememberScrollState()

    // 命令随公式实时重编译：用户能随时看到"自己输入的东西会变成什么"
    val compiled = remember(state.root) { CommandBuilder.compile(state.root) }

    /**
     * 提交一次变更。
     *
     * 调用方不需要区分"编辑"和"仅移动光标"—— [EditHistory.commit] 自己判断是否入栈。
     * 这里只额外负责一件事：**公式变了就作废上一次的结果**，避免结果与输入不一致。
     */
    fun commit(next: EditorState) {
        val rootChanged = next.root != state.root
        history.commit(next)
        state = history.current
        if (rootChanged) outcome = null
    }

    fun undo() {
        if (history.undo()) {
            state = history.current
            outcome = null
        }
    }

    fun redo() {
        if (history.redo()) {
            state = history.current
            outcome = null
        }
    }

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
                state = state,
                // 点击定位只移动光标：AST 未变，因此不会被记入撤销历史
                onCaretChange = { commit(state.copy(caret = it)) }
            )
        }

        outcome?.let { ResultPanel(it) }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { outcome = evaluate(state.root) },
                modifier = Modifier.weight(1f)
            ) {
                Text("计 算")
            }
            OutlinedButton(
                onClick = {
                    history.reset()
                    state = history.current
                    outcome = null
                },
                modifier = Modifier.weight(0.6f)
            ) {
                Text("清空")
            }
        }

        EditorKeyboard(
            canUndo = history.canUndo,
            canRedo = history.canRedo,
            onUndo = ::undo,
            onRedo = ::redo,
            onTemplate = {
                commit(FormulaEditor.insertStructure(state.root, state.caret, it))
            },
            onAtom = { commit(FormulaEditor.insert(state.root, state.caret, it)) },
            onNextSlot = {
                commit(
                    state.copy(
                        caret = FormulaEditor.focusNextEmptySlot(state.root, state.caret)
                    )
                )
            },
            onBackspace = { commit(FormulaEditor.backspace(state.root, state.caret)) },
            onLeft = {
                commit(state.copy(caret = FormulaEditor.moveLeft(state.root, state.caret)))
            },
            onRight = {
                commit(state.copy(caret = FormulaEditor.moveRight(state.root, state.caret)))
            }
        )
    }
}

// ============================ 求值 ============================

/** 一次求值的结果，供结果面板展示。 */
private data class EditorOutcome(
    val label: String,
    val value: String,
    val isError: Boolean = false
)

/**
 * 求值流程：编译 → 能力分类 → 本地求值。
 *
 * **关键点：三种失败要给出不同的说法，不能都笼统说"算不了"。**
 * 用户需要知道"是表达式写错了"还是"这功能需要联网"。
 */
private fun evaluate(root: Row): EditorOutcome {
    val compiled = CommandBuilder.compile(root)

    if (compiled.command.isBlank()) {
        return EditorOutcome("提示", "公式还是空的", isError = true)
    }

    if (CapabilityClassifier.needsCloud(compiled.command)) {
        return EditorOutcome(
            label = "需要云端符号引擎",
            value = "这条命令超出本地数值引擎的能力范围（当前未启用联网引擎）",
            isError = true
        )
    }

    val local = LocalFirstEvaluator.tryEvaluate(compiled.command)
        ?: return EditorOutcome(
            label = "无法计算",
            value = "表达式不完整或格式有误，请检查括号与运算符",
            isError = true
        )

    return EditorOutcome("数值解", formatResult(local))
}

/**
 * 结果展示规则：
 * - **精确形式更短**（`14`、`1/2`）→ 展示精确形式，再补一个小数近似帮助判断量级；
 * - **原始值更长**（`1/23`、`√2` 的 17 位 double）→ 只展示 10 位有效数字。
 *
 * 后者很重要：否则会把 `0.04347826086956522 ≈ 0.04347826087` 整行丢给用户，
 * 既不精确（后面那串数字都是噪声）又难读。
 */
private fun formatResult(result: NumericResult): String {
    val exact = result.plainText
    val rounded = result.numericValue?.let {
        BigDecimal(it).round(MathContext(10)).stripTrailingZeros().toPlainString()
    } ?: return exact

    return when {
        exact == rounded -> exact
        exact.length <= rounded.length -> "$exact   ≈ $rounded"
        else -> rounded
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
private fun ResultPanel(outcome: EditorOutcome) {
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
            Text(
                text = outcome.value,
                style = MaterialTheme.typography.titleMedium,
                color = content
            )
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
