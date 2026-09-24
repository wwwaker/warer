package com.wwwaker.warer.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 锁定本地数值引擎的行为（含智能舍入与隐式乘号预处理）。
 *
 * 注意：这些用例只断言"搬运前后行为一致"，不断言数学上限。
 */
class LocalNumericEngineTest {

    @Test
    fun `simple arithmetic`() {
        val r = LocalNumericEngine.compute("2+2")
        assertEquals(4.0, r!!.numericValue!!, 1e-9)
        assertEquals("4", r.plainText)
    }

    @Test
    fun `division result is rendered as a common fraction`() {
        val r = LocalNumericEngine.compute("1/2")
        assertEquals(0.5, r!!.numericValue!!, 1e-9)
        assertEquals("1/2", r.plainText)
    }

    @Test
    fun `trig of zero`() {
        val r = LocalNumericEngine.compute("sin(0)")
        assertEquals(0.0, r!!.numericValue!!, 1e-9)
        assertEquals("0", r.plainText)
    }

    @Test
    fun `expression with unknown symbol cannot be evaluated locally`() {
        assertNull(LocalNumericEngine.compute("x+1"))
    }

    @Test
    fun `nonsense input cannot be evaluated locally`() {
        assertNull(LocalNumericEngine.compute("abc"))
    }

    @Test
    fun `smartRound collapses near-integers`() {
        assertEquals("3", LocalNumericEngine.smartRound(3.0))
        assertEquals("-7", LocalNumericEngine.smartRound(-7.0))
    }

    @Test
    fun `smartRound restores common fractions`() {
        assertEquals("1/2", LocalNumericEngine.smartRound(0.5))
        assertEquals("1/3", LocalNumericEngine.smartRound(1.0 / 3.0))
        assertEquals("-1/4", LocalNumericEngine.smartRound(-0.25))
    }

    @Test
    fun `implicit multiplication is inserted between number and symbol`() {
        assertEquals("2*x", LocalNumericEngine.preprocessImplicitMultiplication("2x"))
        assertEquals("2*sin(x)", LocalNumericEngine.preprocessImplicitMultiplication("2sin(x)"))
    }

    @Test
    fun `implicit multiplication is inserted between number and parenthesis`() {
        assertEquals("2*(x+1)", LocalNumericEngine.preprocessImplicitMultiplication("2(x+1)"))
    }

    @Test
    fun `implicit multiplication is inserted between adjacent parentheses`() {
        assertEquals("(x+1)*(x-1)", LocalNumericEngine.preprocessImplicitMultiplication("(x+1)(x-1)"))
    }
}
