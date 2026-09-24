package com.wwwaker.warer.core.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 锁定 `needsCloud` 的判定行为。搬运重构期间这些用例必须始终通过。
 */
class CapabilityClassifierTest {

    @Test
    fun `pure arithmetic is handled locally`() {
        assertFalse(CapabilityClassifier.needsCloud("2+2"))
        assertFalse(CapabilityClassifier.needsCloud("3*4-1"))
    }

    @Test
    fun `trig of constants is handled locally`() {
        assertFalse(CapabilityClassifier.needsCloud("sin(0)"))
        assertFalse(CapabilityClassifier.needsCloud("cos(0)+1"))
    }

    @Test
    fun `pi and e are treated as local constants`() {
        assertFalse(CapabilityClassifier.needsCloud("pi"))
        assertFalse(CapabilityClassifier.needsCloud("e"))
        assertFalse(CapabilityClassifier.needsCloud("2*pi"))
    }

    @Test
    fun `symbolic commands require cloud`() {
        assertTrue(CapabilityClassifier.needsCloud("diff(x^2, x)"))
        assertTrue(CapabilityClassifier.needsCloud("integrate(x^2, x)"))
        assertTrue(CapabilityClassifier.needsCloud("solve(x^2-1, x)"))
        assertTrue(CapabilityClassifier.needsCloud("simplify((x+1)^2)"))
    }

    @Test
    fun `matrix literal requires cloud`() {
        assertTrue(CapabilityClassifier.needsCloud("[[1,2],[3,4]]"))
    }

    @Test
    fun `free variable requires cloud`() {
        assertTrue(CapabilityClassifier.needsCloud("x+1"))
        assertTrue(CapabilityClassifier.needsCloud("sin(x)"))
    }
}
