package com.zynergylabs.forager.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rules for a failed tile request (dispatch 2026-10-23-06 Amendment 1), decided without OkHttp or Android. */
class TileRetryPolicyTest {

    private val policy = TileRetryPolicy()

    @Test
    fun `the named limits are the planner's 5 minutes and 8 at once`() {
        assertEquals(300_000L, TILE_RETRY_HOLD_LIMIT_MS)
        assertEquals(8, TILE_RETRY_MAX_HELD)
        assertEquals(300_000L, policy.holdLimitMs)
        assertEquals(8, TileHoldGate().limit)
    }

    @Test
    fun `with no validated network every kind of failure waits for the network`() {
        for (failure in TileFailure.values()) {
            assertEquals(failure.name, TileRetryDecision.WaitForNetwork, policy.decide(failure, 1, 0, networkValidated = false))
            assertEquals(failure.name, TileRetryDecision.WaitForNetwork, policy.decide(failure, 7, 120_000, networkValidated = false))
        }
    }

    @Test
    fun `with a validated network each failure is resent after the backoff for its attempt`() {
        val expected = listOf(1_000L, 2_000L, 4_000L, 8_000L, 15_000L, 30_000L, 30_000L, 30_000L)
        for (failure in TileFailure.values()) {
            val delays = (1..8).map { attempt -> policy.decide(failure, attempt, 10_000, networkValidated = true) }
            assertEquals(failure.name, expected.map { TileRetryDecision.RetryAfter(it) }, delays)
        }
    }

    @Test
    fun `the backoff never runs past the hold limit`() {
        assertEquals(TileRetryDecision.RetryAfter(500), policy.decide(TileFailure.Server, 6, 299_500, networkValidated = true))
    }

    @Test
    fun `at the hold limit it gives up, network or not`() {
        for (failure in TileFailure.values()) {
            assertEquals(TileRetryDecision.GiveUp, policy.decide(failure, 2, 300_000, networkValidated = true))
            assertEquals(TileRetryDecision.GiveUp, policy.decide(failure, 2, 300_001, networkValidated = false))
        }
    }

    @Test
    fun `the gate holds at most its limit and frees a place on release`() {
        val gate = TileHoldGate(limit = 2)
        assertTrue(gate.tryHold())
        assertTrue(gate.tryHold())
        assertFalse("a third hold went through a gate of 2", gate.tryHold())
        assertEquals(2, gate.heldNow)
        gate.release()
        assertTrue(gate.tryHold())
        assertEquals(2, gate.heldNow)
    }
}
