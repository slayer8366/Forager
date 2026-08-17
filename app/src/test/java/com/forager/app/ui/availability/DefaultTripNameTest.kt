package com.forager.app.ui.availability

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [defaultTripName]: the "Trip N" pre-fill [TripDatePickerDialog] starts a new trip's name with.
 * Plain JUnit, no Robolectric — this is pure arithmetic with no Android type in sight.
 */
class DefaultTripNameTest {

    @Test
    fun `the first trip defaults to Trip 1`() {
        assertEquals("Trip 1", defaultTripName(existingTripCount = 0))
    }

    @Test
    fun `the second trip defaults to Trip 2`() {
        assertEquals("Trip 2", defaultTripName(existingTripCount = 1))
    }

    @Test
    fun `the Nth trip defaults to Trip N plus 1`() {
        assertEquals("Trip 6", defaultTripName(existingTripCount = 5))
    }
}
