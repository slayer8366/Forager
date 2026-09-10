package com.zynergylabs.forager.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * HUD-foundations dispatch, Item 1: [ageMillis] is a pure function of the fix and the caller's
 * clock. Literals throughout — the expected ages are arithmetic done by hand, not read back from
 * the function under test.
 */
class LocationFixAgeTest {

    private val fix = LocationFix.Update(
        lat = 45.52,
        lng = -122.68,
        altitude = 50.0,
        accuracyMeters = 12.5f,
        timestampEpochMillis = 1_700_000_000_000L,
    )

    @Test
    fun `age is the clock minus the fix's own timestamp`() {
        assertEquals(0L, fix.ageMillis(nowEpochMillis = 1_700_000_000_000L))
        assertEquals(1_000L, fix.ageMillis(nowEpochMillis = 1_700_000_001_000L))
        // Twenty minutes later, with no newer fix: the age keeps growing, which is the whole point.
        assertEquals(1_200_000L, fix.ageMillis(nowEpochMillis = 1_700_001_200_000L))
    }

    /** Clock skew is reported as-is, never clamped to a plausible zero — see [ageMillis]'s own doc comment. */
    @Test
    fun `a clock earlier than the fix reports a negative age rather than zero`() {
        assertEquals(-5_000L, fix.ageMillis(nowEpochMillis = 1_699_999_995_000L))
    }
}
