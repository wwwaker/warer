package com.wwwaker.warer.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 计算调度器（能力分类 → 本地 → 云端降级）的测试。
 *
 * 重点锁定三件事：
 * 1. 本地能算的**绝不联网**；
 * 2. 需要云端而云端不可用时，**说法要具体**（不是笼统的"算不了"）；
 * 3. 云端失败时的降级顺序正确。
 */
class ComputeDispatcherTest {

    private fun success(latex: String, plain: String) = CloudCallResult.Success(
        CloudSymbolicResult(latex = latex, plainText = plain)
    )

    // ============================ 本地优先 ============================

    @Test
    fun `pure arithmetic is solved locally and never touches the cloud`() {
        var cloudCalled = false
        val cloud = CloudEngine {
            cloudCalled = true
            success("?", "?")
        }

        val outcome = ComputeDispatcher.dispatch("2+3*4", cloud)

        assertTrue(outcome is ComputeOutcome.LocalNumeric)
        assertEquals(14.0, (outcome as ComputeOutcome.LocalNumeric).result.numericValue!!, 1e-9)
        assertEquals(false, cloudCalled)
    }

    // ============================ 空公式 ============================

    @Test
    fun `blank command is reported as empty`() {
        val outcome = ComputeDispatcher.dispatch("   ")
        assertEquals(FailureKind.EMPTY, (outcome as ComputeOutcome.Failure).kind)
    }

    // ============================ 无云端时的两种说法 ============================

    @Test
    fun `symbolic command without a cloud engine says it needs the network`() {
        val outcome = ComputeDispatcher.dispatch("diff(x^2, x)")
        val failure = outcome as ComputeOutcome.Failure
        assertEquals(FailureKind.CLOUD_UNAVAILABLE, failure.kind)
    }

    @Test
    fun `broken numeric expression without a cloud engine reports a syntax problem`() {
        val outcome = ComputeDispatcher.dispatch("2+*3")
        val failure = outcome as ComputeOutcome.Failure
        assertEquals(FailureKind.LOCAL_FAILED, failure.kind)
    }

    // ============================ 云端成功 ============================

    @Test
    fun `symbolic command goes to the cloud and returns the latex untouched`() {
        val cloud = CloudEngine { success("2 x", "2*x") }

        val outcome = ComputeDispatcher.dispatch("diff(x^2, x)", cloud)

        val symbolic = outcome as ComputeOutcome.CloudSymbolic
        assertEquals("2 x", symbolic.result.latex)
        assertEquals("2*x", symbolic.result.plainText)
    }

    // ============================ 云端拒绝 / 不可达 ============================

    @Test
    fun `cloud rejection is surfaced with its own error type`() {
        val cloud = CloudEngine {
            CloudCallResult.Rejected(type = "SyntaxError", message = "括号未闭合", position = 3)
        }

        val outcome = ComputeDispatcher.dispatch("diff(x^2, x)", cloud)
        val failure = outcome as ComputeOutcome.Failure

        assertEquals(FailureKind.CLOUD_REJECTED, failure.kind)
        assertEquals("SyntaxError: 括号未闭合", failure.message)
        assertEquals(3, failure.position)
    }

    @Test
    fun `unreachable cloud is reported as a network failure`() {
        val cloud = CloudEngine { throw java.io.IOException("timeout") }

        val outcome = ComputeDispatcher.dispatch("solve(x^2-4, x)", cloud)
        val failure = outcome as ComputeOutcome.Failure

        assertEquals(FailureKind.CLOUD_UNREACHABLE, failure.kind)
        assertTrue(failure.message.startsWith("网络错误"))
    }

    @Test
    fun `blank input never reaches the cloud`() {
        var cloudCalled = false
        val cloud = CloudEngine {
            cloudCalled = true
            success("", "")
        }

        ComputeDispatcher.dispatch("", cloud)

        assertEquals(false, cloudCalled)
    }
}
