package com.zynergylabs.forager.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The judge's two thresholds and its clear hold, pinned on both sides with literals — 15.0° and 12.0°
 * written out, never derived from the constants; 2 000 ms likewise.
 */
class CompassTrustJudgeTest {

    private fun estimated(degrees: Float, atMillis: Long = 0L) =
        CompassReading(magneticHeadingDegrees = 90f, uncertainty = HeadingUncertainty.Estimated(degrees), timestampMillis = atMillis)

    private fun status(level: CompassStatus, atMillis: Long = 0L) =
        CompassReading(magneticHeadingDegrees = 90f, uncertainty = HeadingUncertainty.Status(level), timestampMillis = atMillis)

    @Test
    fun `enters unreliable above 15 degrees, immediately - not at 15 or below`() {
        assertFalse(CompassTrustJudge().next(estimated(14.99f)))
        assertFalse(CompassTrustJudge().next(estimated(15.0f)))
        assertTrue(CompassTrustJudge().next(estimated(15.01f)))
        assertTrue(CompassTrustJudge().next(estimated(20.05f)))
    }

    @Test
    fun `leaves only below 12 degrees - 12 or 13 stays unreliable however long it holds`() {
        val judge = CompassTrustJudge()
        assertTrue(judge.next(estimated(20f, atMillis = 0L)))
        assertTrue(judge.next(estimated(13.5f, atMillis = 1_000L)))
        assertTrue(judge.next(estimated(12.0f, atMillis = 5_000L)))
        assertTrue(judge.next(estimated(12.0f, atMillis = 9_000L)))
        // 11.99 starts the hold; two seconds of it clears.
        assertTrue(judge.next(estimated(11.99f, atMillis = 10_000L)))
        assertFalse(judge.next(estimated(11.99f, atMillis = 12_000L)))
    }

    @Test
    fun `clears only after the reading has held good for 2000 ms - 1999 is not enough`() {
        val judge = CompassTrustJudge()
        assertTrue(judge.next(estimated(30f, atMillis = 0L)))
        assertTrue(judge.next(estimated(3f, atMillis = 100L)))
        assertTrue(judge.next(estimated(3f, atMillis = 2_099L))) // 1 999 ms held
        assertFalse(judge.next(estimated(3f, atMillis = 2_100L))) // 2 000 ms held
    }

    @Test
    fun `a bad reading during the hold restarts it`() {
        val judge = CompassTrustJudge()
        assertTrue(judge.next(estimated(30f, atMillis = 0L)))
        assertTrue(judge.next(estimated(3f, atMillis = 100L)))
        assertTrue(judge.next(estimated(30f, atMillis = 600L)))
        assertTrue(judge.next(estimated(3f, atMillis = 1_000L)))
        assertTrue(judge.next(estimated(3f, atMillis = 2_999L))) // only 1 999 ms since the restart
        assertFalse(judge.next(estimated(3f, atMillis = 3_000L)))
    }

    @Test
    fun `status path - UNRELIABLE and LOW enter at once, MEDIUM and HIGH are good, with the same hold`() {
        assertTrue(CompassTrustJudge().next(status(CompassStatus.UNRELIABLE)))
        assertTrue(CompassTrustJudge().next(status(CompassStatus.LOW)))
        assertFalse(CompassTrustJudge().next(status(CompassStatus.MEDIUM)))
        assertFalse(CompassTrustJudge().next(status(CompassStatus.HIGH)))

        val judge = CompassTrustJudge()
        assertTrue(judge.next(status(CompassStatus.LOW, atMillis = 0L)))
        assertTrue(judge.next(status(CompassStatus.HIGH, atMillis = 500L)))
        assertTrue(judge.next(status(CompassStatus.MEDIUM, atMillis = 2_499L)))
        assertFalse(judge.next(status(CompassStatus.HIGH, atMillis = 2_500L)))
    }

    @Test
    fun `the two paths share one state - a status LOW keeps an estimate-entered state bad, and the hold spans them`() {
        val judge = CompassTrustJudge()
        assertTrue(judge.next(estimated(20f, atMillis = 0L)))
        assertTrue(judge.next(status(CompassStatus.LOW, atMillis = 500L)))
        assertTrue(judge.next(status(CompassStatus.HIGH, atMillis = 1_000L)))
        assertTrue(judge.next(estimated(5f, atMillis = 2_999L)))
        assertFalse(judge.next(estimated(5f, atMillis = 3_000L)))
    }

    @Test
    fun `reset forgets an unreliable state and its hold`() {
        val judge = CompassTrustJudge()
        assertTrue(judge.next(estimated(20f, atMillis = 0L)))
        judge.reset()
        assertFalse(judge.next(estimated(5f, atMillis = 1L)))
    }
}
