package com.wwwaker.warer.core.formula.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 锁定 AST → 计算引擎文本语法的转换行为。
 * 这层转换是"公式编辑器"与"计算引擎"之间的桥，必须稳定。
 */
class FormulaTextTest {

    @Test
    fun `leaves map to their own text`() {
        assertEquals("x", Sym('x').toPlainText())
        assertEquals("12", Num("12").toPlainText())
        assertEquals("sin", Func("sin").toPlainText())
    }

    @Test
    fun `row concatenates children in order`() {
        assertEquals("2+3", Row(listOf(Num("2"), Sym('+'), Num("3"))).toPlainText())
    }

    @Test
    fun `fraction uses parenthesised division so precedence is preserved`() {
        assertEquals(
            "(1)/(2)",
            Frac(Row(listOf(Num("1"))), Row(listOf(Num("2")))).toPlainText()
        )
    }

    @Test
    fun `script uses caret and underscore`() {
        assertEquals(
            "x^(2)",
            Script(Row(listOf(Sym('x'))), superscript = Row(listOf(Num("2")))).toPlainText()
        )
        assertEquals(
            "x_(1)",
            Script(Row(listOf(Sym('x'))), subscript = Row(listOf(Num("1")))).toPlainText()
        )
        assertEquals(
            "x_(1)^(2)",
            Script(
                Row(listOf(Sym('x'))),
                superscript = Row(listOf(Num("2"))),
                subscript = Row(listOf(Num("1")))
            ).toPlainText()
        )
    }

    @Test
    fun `sqrt maps to the sqrt function`() {
        assertEquals("sqrt(2)", Sqrt(Row(listOf(Num("2")))).toPlainText())
    }

    @Test
    fun `nested structures produce engine friendly text`() {
        val node = Frac(
            numerator = Row(listOf(Num("1"))),
            denominator = Row(listOf(Sqrt(Row(listOf(Num("2"))))))
        )
        assertEquals("(1)/(sqrt(2))", node.toPlainText())
    }
}
