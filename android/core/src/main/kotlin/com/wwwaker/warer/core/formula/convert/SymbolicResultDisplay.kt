package com.wwwaker.warer.core.formula.convert

import com.wwwaker.warer.core.formula.model.Node

/**
 * 符号结果的展示形态。
 *
 * 后端返回 LaTeX，但它不一定都在 [LatexToAst] 的覆盖范围内（例如矩阵）。
 * 因此展示前先尝试转 AST，转得成就**渲染成真正的公式**，转不成就退回纯文本
 * —— 绝不能因为转不动就什么都不显示。
 */
sealed interface SymbolicDisplay {

    /** 能转成 AST：交给排版引擎渲染，同时保留纯文本备用。 */
    data class Formula(val node: Node, val plainText: String) : SymbolicDisplay

    /** 转不动（矩阵等）：只能显示后端给的纯文本。 */
    data class Plain(val plainText: String) : SymbolicDisplay
}

/**
 * 决定符号结果怎么展示。
 *
 * 这是纯函数、放在 `:core` 的原因与其它逻辑一致：**判断只写一次且可被 JVM 单测锁定**，
 * UI 层只负责"拿结果去画"。
 */
object SymbolicResultDisplay {

    fun of(latex: String, plainText: String): SymbolicDisplay = try {
        SymbolicDisplay.Formula(LatexToAst.parse(latex), plainText)
    } catch (_: UnsupportedLatexException) {
        SymbolicDisplay.Plain(plainText)
    }
}
