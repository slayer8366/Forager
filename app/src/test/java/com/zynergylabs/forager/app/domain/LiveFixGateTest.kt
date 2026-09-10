package com.zynergylabs.forager.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The live-fix gate's boundary, pinned on both sides with literals — location-accuracy dispatch, item 1. */
class LiveFixGateTest {

    private val fix = LocationFix.Update(lat = 45.52, lng = -122.68, altitude = 50.0, accuracyMeters = 12.5f, timestampEpochMillis = 1_700_000_000_000L)

    @Test
    fun `at or under 50 m passes, over 50 m is rejected`() {
        assertTrue(acceptLiveFix(fix.copy(accuracyMeters = 49.9f)))
        assertTrue(acceptLiveFix(fix.copy(accuracyMeters = 50.0f)))
        assertFalse(acceptLiveFix(fix.copy(accuracyMeters = 50.1f)))
        assertFalse(acceptLiveFix(fix.copy(accuracyMeters = 60f)))
    }

    @Test
    fun `no reported accuracy passes - not reported is not bad`() {
        assertTrue(acceptLiveFix(fix.copy(accuracyMeters = null)))
    }

    @Test
    fun `the ceiling is the gate's own constant, 50 m, and a caller may narrow it`() {
        assertTrue(LIVE_FIX_MAX_ACCURACY_METERS == 50f)
        assertFalse(acceptLiveFix(fix.copy(accuracyMeters = 40f), maxAccuracyMeters = 30f))
    }
}
