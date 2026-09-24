package com.wwwaker.warer.core.graph

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * GraphDetector 的回归测试。
 *
 * 这些用例是"搬运到 :core 后行为不变"的证明，也是后续继续搬运其它逻辑时的第一批安全网。
 */
class GraphDetectorTest {

    @Test
    fun `empty input falls back to linear with empty expr`() {
        val r = GraphDetector.detect("")
        assertEquals("linear", r.type)
        assertEquals("", r.expr)
    }

    @Test
    fun `blank input falls back to linear`() {
        val r = GraphDetector.detect("   ")
        assertEquals("linear", r.type)
        assertEquals("", r.expr)
    }

    @Test
    fun `explicit y notation is linear`() {
        val r = GraphDetector.detect("y(x^2)")
        assertEquals("linear", r.type)
        assertEquals("x^2", r.expr)
    }

    @Test
    fun `explicit r notation is polar`() {
        val r = GraphDetector.detect("r(1+cos(theta))")
        assertEquals("polar", r.type)
        assertEquals("1+cos(theta)", r.expr)
    }

    @Test
    fun `explicit theta notation is polar`() {
        val r = GraphDetector.detect("theta(sin(2*theta))")
        assertEquals("polar", r.type)
        assertEquals("sin(2*theta)", r.expr)
    }

    @Test
    fun `explicit t notation is parametric with two args`() {
        val r = GraphDetector.detect("t(cos(t), sin(t))")
        assertEquals("parametric", r.type)
        assertEquals("cos(t)", r.xExpr)
        assertEquals("sin(t)", r.yExpr)
    }

    @Test
    fun `explicit f notation is implicit`() {
        val r = GraphDetector.detect("f(x^2+y^2-1)")
        assertEquals("implicit", r.type)
        assertEquals("x^2+y^2-1", r.expr)
    }

    @Test
    fun `theta triggers polar heuristically`() {
        val r = GraphDetector.detect("1+cos(theta)")
        assertEquals("polar", r.type)
    }

    @Test
    fun `expression containing free y is implicit`() {
        val r = GraphDetector.detect("x^2+y^2")
        assertEquals("implicit", r.type)
    }

    @Test
    fun `known function of x stays linear`() {
        val r = GraphDetector.detect("sin(x)")
        assertEquals("linear", r.type)
        assertEquals("sin(x)", r.expr)
    }

    @Test
    fun `leading y= prefix is stripped for implicit detection`() {
        val r = GraphDetector.detect("y = x^2 + y")
        assertEquals("implicit", r.type)
        assertEquals("x^2 + y", r.expr)
    }

    @Test
    fun `symbolic command is treated as linear`() {
        val r = GraphDetector.detect("diff(x^2, x)")
        assertEquals("linear", r.type)
    }
}
