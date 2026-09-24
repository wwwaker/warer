package com.wwwaker.warer.core.formula.edit

/**
 * 撤销 / 重做历史。
 *
 * 之所以能做得这么轻，是因为 AST **不可变**、编辑操作是**纯函数**：
 * 一次编辑产生一棵全新的树，所以"撤销"只是把状态换回上一个快照，
 * 不需要为每种操作再实现一个逆操作 —— 那才是撤销功能 bug 的高发区
 * （逆操作与正向操作一旦不一致，就会出现"撤销后状态错乱"这类难查的问题）。
 *
 * 入栈策略由 [commit] **自动判断**，调用方不需要区分"编辑"和"移动光标"：
 * - AST 与光标都没变 → 忽略；
 * - 只有光标变了 → 更新当前状态但**不入栈**（纯导航不算一次编辑，
 *   否则连按方向键会把历史刷满，真正的编辑反而被挤掉）；
 * - AST 变了 → 旧状态压入撤销栈，并清空重做栈（分支后旧的重做路径已失效）。
 */
class EditHistory(
    initial: EditorState = EditorState(),
    /** 历史深度上限；超出后丢弃最旧的一条，避免长时间编辑无限占用内存。 */
    private val limit: Int = DEFAULT_LIMIT
) {
    private val past = ArrayDeque<EditorState>()
    private val future = ArrayDeque<EditorState>()

    /** 当前状态，由本类维护；调用方应始终以它为准。 */
    var current: EditorState = initial
        private set

    val canUndo: Boolean get() = past.isNotEmpty()
    val canRedo: Boolean get() = future.isNotEmpty()

    /** 可撤销步数（测试与调试用）。 */
    val undoDepth: Int get() = past.size

    /** 可重做步数（测试与调试用）。 */
    val redoDepth: Int get() = future.size

    /** 提交一个新状态，是否入栈由本类判断（见类文档）。 */
    fun commit(next: EditorState) {
        if (next == current) return

        if (next.root == current.root) {
            // 只移动了光标：更新位置但不占用历史
            current = next
            return
        }

        past.addLast(current)
        while (past.size > limit) past.removeFirst()
        current = next
        future.clear()
    }

    /** @return 是否真的撤销了一步（历史为空时返回 false）。 */
    fun undo(): Boolean {
        val previous = past.removeLastOrNull() ?: return false
        future.addLast(current)
        current = previous
        return true
    }

    /** @return 是否真的重做了一步（重做栈为空时返回 false）。 */
    fun redo(): Boolean {
        val next = future.removeLastOrNull() ?: return false
        past.addLast(current)
        current = next
        return true
    }

    /** 丢弃全部历史并重置状态（用于"清空"）。 */
    fun reset(state: EditorState = EditorState()) {
        past.clear()
        future.clear()
        current = state
    }

    companion object {
        const val DEFAULT_LIMIT = 100
    }
}
