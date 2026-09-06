package com.forager.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Navigation HUD stage one's one heading filter. Every expected value is hand-computed from the
 * unit-vector EMA at alpha 0.3 — `x += 0.3 (cos θ − x)`, `y += 0.3 (sin θ − y)`, angle `atan2(y, x)`
 * — not read back from the class.
 */
class HeadingSmootherTest {

    @Test
    fun `the first sample seeds the filter outright, with no lag from zero`() {
        assertEquals(90f, HeadingSmoother().next(90f), 1e-4f)
        assertEquals(270f, HeadingSmoother().next(270f), 1e-4f)
    }

    @Test
    fun `359 then 1 averages across north to 359 point 6, not to 180`() {
        val smoother = HeadingSmoother()
        smoother.next(359f)
        // y: −0.017452 + 0.3 (0.017452 − (−0.017452)) = −0.006981; x ≈ 0.99985 → atan2 = −0.40° → 359.60°
        assertEquals(359.6f, smoother.next(1f), 0.05f)
    }

    @Test
    fun `one step of 90 degrees moves the reading 23 point 2 degrees at alpha 0 point 3`() {
        val smoother = HeadingSmoother()
        smoother.next(0f)
        // x = 1 + 0.3 (0 − 1) = 0.7; y = 0 + 0.3 (1 − 0) = 0.3 → atan2(0.3, 0.7) = 23.199°
        assertEquals(23.2f, smoother.next(90f), 0.05f)
    }

    @Test
    fun `seven samples after a 90 degree step reach 84 point 9 degrees, about ninety percent settled`() {
        val smoother = HeadingSmoother()
        smoother.next(0f)
        var last = 0f
        repeat(7) { last = smoother.next(90f) }
        // x = 0.7^7 = 0.082354; y = 1 − 0.7^7 = 0.917646 → atan2 = 84.87°
        assertEquals(84.9f, last, 0.05f)
    }

    @Test
    fun `reset forgets the running vector so the next sample seeds afresh`() {
        val smoother = HeadingSmoother()
        smoother.next(0f)
        smoother.next(0f)
        smoother.reset()

        assertEquals(180f, smoother.next(180f), 1e-4f)
    }
}
