package com.wwwaker.warer.core.formula.convert

import com.wwwaker.warer.core.formula.model.toPlainText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 符号结果展示形态的测试：能解析的走公式渲染，解析不了的必须退回纯文本。
 */
class SymbolicResultDisplayTest {

    @Test
    fun `parseable latex becomes a formula`() {
        val display = SymbolicResultDisplay.of("\\frac{x^{3}}{3}", "x**3/3")
        val formula = display as SymbolicDisplay.Formula
        assertEquals("(x^(3))/(3)", formula.node.toPlainText())
        assertEquals("x**3/3", formula.plainText)
    }

    @Test
    fun `matrix latex falls back to plain text instead of showing nothing`() {
        val matrix = "\\left[\\begin{matrix}1 & 2\\\\3 & 4\\end{matrix}\\right]"
        val display = SymbolicResultDisplay.of(matrix, "Matrix([[1, 2], [3, 4]])")

        val plain = display as SymbolicDisplay.Plain
        assertEquals("Matrix([[1, 2], [3, 4]])", plain.plainText)
    }

    @Test
    fun `unknown latex command also falls back to plain text`() {
        val display = SymbolicResultDisplay.of("\\weirdcmd{x}", "whatever")
        assertTrue(display is SymbolicDisplay.Plain)
    }

    @Test
    fun `empty solution set is still renderable`() {
        val display = SymbolicResultDisplay.of("\\emptyset", "no solutions")
        assertTrue(display is SymbolicDisplay.Formula)
    }
}
