package com.wwwaker.warer.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 本地优先求值策略的测试。
 */
class LocalFirstEvaluatorTest {

    @Test
    fun `pure arithmetic is solved locally`() {
        val result = LocalFirstEvaluator.tryEvaluate("2+3*4")
        assertEquals(14.0, result!!.numericValue!!, 1e-9)
        assertEquals("14", result.plainText)
    }

    @Test
    fun `common fractions are restored in the display text`() {
        val result = LocalFirstEvaluator.tryEvaluate("1/2+1/3")
        assertEquals(5.0 / 6.0, result!!.numericValue!!, 1e-9)
        assertEquals("5/6", result.plainText)
    }

    @Test
    fun `blank input is not evaluated`() {
        assertNull(LocalFirstEvaluator.tryEvaluate(""))
        assertNull(LocalFirstEvaluator.tryEvaluate("   "))
    }

    @Test
    fun `symbolic commands are delegated upwards`() {
        assertNull(LocalFirstEvaluator.tryEvaluate("integrate(sin(x), x, 0, 1)"))
        assertNull(LocalFirstEvaluator.tryEvaluate("diff(x^2, x)"))
        assertNull(LocalFirstEvaluator.tryEvaluate("solve(x^2-1, x)"))
    }

    @Test
    fun `free variables are delegated upwards`() {
        assertNull(LocalFirstEvaluator.tryEvaluate("x+1"))
    }
}
