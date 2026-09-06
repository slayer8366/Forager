package com.forager.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Navigation HUD stage one arithmetic — literals only, chosen so a sign error changes the answer. */
class NavigationReadoutTest {

    /** The dispatch's own case: facing north-east, target north-west — a quarter turn to the *left*, 270, never 90. */
    @Test
    fun `a target north-west of a user facing north-east is 270 degrees relative, a left turn`() {
        assertEquals(270f, relativeBearingDegrees(bearingDegrees = 315.0, headingDegrees = 45f), 1e-4f)
    }

    @Test
    fun `a target north-east of a user facing north-west is 90 degrees relative, a right turn`() {
        assertEquals(90f, relativeBearingDegrees(bearingDegrees = 45.0, headingDegrees = 315f), 1e-4f)
    }

    @Test
    fun `relative bearing wraps across north in both directions`() {
        assertEquals(20f, relativeBearingDegrees(bearingDegrees = 10.0, headingDegrees = 350f), 1e-4f)
        assertEquals(340f, relativeBearingDegrees(bearingDegrees = 350.0, headingDegrees = 10f), 1e-4f)
        assertEquals(0f, relativeBearingDegrees(bearingDegrees = 123.0, headingDegrees = 123f), 1e-4f)
    }

    @Test
    fun `approaching is inside twice the reported accuracy, inclusive, and never without an accuracy`() {
        assertTrue(isApproaching(distanceMeters = 30.0, accuracyMeters = 20f))
        assertTrue(isApproaching(distanceMeters = 40.0, accuracyMeters = 20f))
        assertFalse(isApproaching(distanceMeters = 40.1, accuracyMeters = 20f))
        assertFalse(isApproaching(distanceMeters = 5.0, accuracyMeters = null))
    }

    @Test
    fun `freshness thresholds are 30 seconds and 5 minutes, inclusive at the boundary`() {
        assertEquals(FixFreshness.FRESH, fixFreshness(0L))
        assertEquals(FixFreshness.FRESH, fixFreshness(29_999L))
        assertEquals(FixFreshness.STALE, fixFreshness(30_000L))
        assertEquals(FixFreshness.STALE, fixFreshness(299_999L))
        assertEquals(FixFreshness.LOST, fixFreshness(300_000L))
    }
}
