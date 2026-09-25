package com.wwwaker.warer_android.ui.formula

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wwwaker.warer.core.engine.CloudEngine
import com.wwwaker.warer.core.engine.ComputeDispatcher
import com.wwwaker.warer.core.engine.ComputeOutcome
import com.wwwaker.warer.core.formula.convert.CommandBuilder
import com.wwwaker.warer.core.formula.edit.Caret
import com.wwwaker.warer.core.formula.edit.EditHistory
import com.wwwaker.warer.core.formula.edit.EditorState
import com.wwwaker.warer.core.formula.edit.FormulaEditor
import com.wwwaker.warer.core.formula.model.Node
import com.wwwaker.warer.core.formula.model.Row
import com.wwwaker.warer_android.data.db.HistoryDao
import com.wwwaker.warer_android.data.db.HistoryEntity
import com.wwwaker.warer_android.data.engine.RetrofitCloudEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 公式编辑器的界面状态。
 *
 * @param editor   当前 AST + 光标
 * @param outcome  最近一次计算结果
 * @param computing 是否正在等待结果
 */
data class FormulaEditorUiState(
    val editor: EditorState = EditorState(),
    val outcome: EditorOutcome? = null,
    val computing: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false
)

/**
 * 编辑器状态持有者。
 *
 * 为什么必须是 ViewModel 而不是 `remember`：编辑器现在是**主界面的一部分**，
 * 切到「图像 / 历史」再回来时不能把用户输入的公式丢掉。
 * `remember` 会随 NavHost 的 composable 一起被销毁，所以状态要放在 ViewModel 里。
 *
 * 职责边界：
 * - 编辑操作全部委托给 `:core` 的纯函数（[FormulaEditor] / [EditHistory]）；
 * - 计算交给 `:core` 的 [ComputeDispatcher]（能力分类 → 本地 → 云端降级）；
 * - 本类只负责"把状态存住 + 切线程 + 记历史"，不含任何业务判断。
 */
class FormulaEditorViewModel(
    private val historyDao: HistoryDao? = null,
    private val cloud: CloudEngine = RetrofitCloudEngine
) : ViewModel() {

    private val history = EditHistory()

    private val _uiState = MutableStateFlow(FormulaEditorUiState())
    val uiState: StateFlow<FormulaEditorUiState> = _uiState.asStateFlow()

    // ============================ 编辑 ============================

    fun setCaret(caret: Caret) = commit(uiState.value.editor.copy(caret = caret))

    fun insert(node: Node) = commit(
        FormulaEditor.insert(uiState.value.editor.root, uiState.value.editor.caret, node)
    )

    fun insertStructure(node: Node) = commit(
        FormulaEditor.insertStructure(uiState.value.editor.root, uiState.value.editor.caret, node)
    )

    fun backspace() = commit(
        FormulaEditor.backspace(uiState.value.editor.root, uiState.value.editor.caret)
    )

    fun moveLeft() = commit(
        uiState.value.editor.copy(
            caret = FormulaEditor.moveLeft(uiState.value.editor.root, uiState.value.editor.caret)
        )
    )

    fun moveRight() = commit(
        uiState.value.editor.copy(
            caret = FormulaEditor.moveRight(uiState.value.editor.root, uiState.value.editor.caret)
        )
    )

    fun focusNextEmptySlot() = commit(
        uiState.value.editor.copy(
            caret = FormulaEditor.focusNextEmptySlot(
                uiState.value.editor.root,
                uiState.value.editor.caret
            )
        )
    )

    fun undo() {
        if (history.undo()) refreshOutcomeCleared()
    }

    fun redo() {
        if (history.redo()) refreshOutcomeCleared()
    }

    fun clear() {
        history.reset()
        _uiState.update {
            it.copy(editor = history.current, outcome = null, canUndo = false, canRedo = false)
        }
    }

    /** 用一棵现成的公式树替换当前内容（例如从别处导入）。 */
    fun load(root: Row) {
        history.reset(EditorState(root = root, caret = Caret()))
        _uiState.update {
            it.copy(editor = history.current, outcome = null, canUndo = false, canRedo = false)
        }
    }

    /**
     * 提交一次编辑。
     *
     * 调用方不需要区分"编辑"和"仅移动光标"——[EditHistory.commit] 自己判断是否入栈。
     * 这里只额外负责一件事：**公式变了就作废上一次的结果**，避免结果与输入不一致。
     */
    private fun commit(next: EditorState) {
        val rootChanged = next.root != history.current.root
        history.commit(next)
        _uiState.update {
            it.copy(
                editor = history.current,
                canUndo = history.canUndo,
                canRedo = history.canRedo,
                outcome = if (rootChanged) null else it.outcome
            )
        }
    }

    private fun refreshOutcomeCleared() {
        _uiState.update {
            it.copy(
                editor = history.current,
                canUndo = history.canUndo,
                canRedo = history.canRedo,
                outcome = null
            )
        }
    }

    // ============================ 计算 ============================

    /**
     * 提交计算：编译 → [ComputeDispatcher]（能力分类 → 本地 → 云端）→ 展示。
     *
     * 调度本身是**阻塞**的，所以放到 IO 线程；界面只等一个 [ComputeOutcome]，
     * 不再自己判断"该本地还是该云端"——那条策略统一在 `:core` 里。
     */
    fun submit() {
        val command = CommandBuilder.compile(uiState.value.editor.root).command
        if (command.isBlank()) {
            _uiState.update {
                it.copy(outcome = EditorOutcome("提示", "公式还是空的", isError = true))
            }
            return
        }

        _uiState.update { it.copy(computing = true, outcome = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                ComputeDispatcher.dispatch(command, cloud)
            }
            _uiState.update { it.copy(computing = false, outcome = result.toEditorOutcome()) }
            record(command, result)
        }
    }

    /** 成功的计算才记入历史；失败不记（历史里堆一堆报错没有意义）。 */
    private fun record(command: String, result: ComputeOutcome) {
        val dao = historyDao ?: return
        val entity = when (result) {
            is ComputeOutcome.LocalNumeric -> HistoryEntity(
                type = "calculation",
                input = command,
                source = "local",
                plainText = result.result.plainText,
                numericValue = result.result.numericValue
            )

            is ComputeOutcome.CloudSymbolic -> HistoryEntity(
                type = "calculation",
                input = command,
                source = "cloud",
                latex = result.result.latex,
                plainText = result.result.plainText,
                numericValue = result.result.numericApproximation?.toDoubleOrNull(),
                isSymbolic = result.result.isSymbolic,
                variables = result.result.variables.ifEmpty { null }?.joinToString(","),
                executionTime = result.result.executionTime.ifEmpty { null }
            )

            is ComputeOutcome.Failure -> return
        }
        viewModelScope.launch { dao.insert(entity) }
    }
}
