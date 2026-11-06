package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.TripDownloadArea
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Dispatch 2026-11-05-08: the Offline Maps state a trip's Download Maps sets, and puts back on leaving (A2), headless. The screen
 * tests drive the same through the real icon; this pins the cases a screen can't reach cheaply. Made-up points.
 */
class TripDownloadMapsStateTest {

    private val userEntry = AvailabilityUiState(
        offlineMapLatText = "41.0",
        offlineMapLngText = "-101.0",
        offlineMapNameText = "My spot",
        offlineMapRadiusKm = 5,
        offlineMapRadiusTouched = false,
    )
    private val plain = PlannedTrip("plain", "Test trip A", LatLng(40.0, -100.0), null)
    private val route = PlannedTrip("route", "Test route B", LatLng(40.0, -100.0), null)

    private fun aroundRoute(radiusKm: Int, lineCovered: Boolean = true, stopsOutside: Boolean = false) =
        TripDownloadArea.AroundRoute(LatLng(40.02, -100.0), radiusKm, lineCovered, stopsOutside)

    @Test
    fun `a plain trip sets the pin and the name, keeps the radius and counts the pin request`() {
        val opened = userEntry.withTripOpened(plain, TripDownloadArea.AtTrip(plain.location))

        assertEquals("40.0", opened.offlineMapLatText)
        assertEquals("-100.0", opened.offlineMapLngText)
        assertEquals("Test trip A", opened.offlineMapNameText)
        assertEquals("the radius the page held", 5, opened.offlineMapRadiusKm)
        assertEquals(false, opened.offlineMapRadiusTouched)
        assertEquals(1, opened.offlineMapPinRequest)
        assertNull("no note for a plain trip", opened.offlineMapsTripOpening?.note)
    }

    @Test
    fun `a route trip sets its starting radius, held as the user's own so a change of units leaves it`() {
        val opened = userEntry.withTripOpened(route, aroundRoute(12))

        assertEquals("40.02", opened.offlineMapLatText)
        assertEquals(12, opened.offlineMapRadiusKm)
        assertEquals(true, opened.offlineMapRadiusTouched)
        assertEquals(12, opened.offlineMapsTripOpening?.startingRadiusKm)
        assertNull(opened.offlineMapsTripOpening?.note)
    }

    @Test
    fun `leaving puts back the pin, the name and the radius from before the tap`() {
        val left = userEntry.withTripOpened(route, aroundRoute(12)).withTripForgotten()

        assertEquals(userEntry.copy(offlineMapPinRequest = 1), left)
    }

    @Test
    fun `a second trip before leaving keeps the first one's before, and a plain trip after a route takes the radius from before`() {
        val twice = userEntry.withTripOpened(route, aroundRoute(12)).withTripOpened(plain, TripDownloadArea.AtTrip(plain.location))

        assertEquals("the plain trip's radius is today's, not the route's", 5, twice.offlineMapRadiusKm)
        assertEquals(false, twice.offlineMapRadiusTouched)
        assertEquals(2, twice.offlineMapPinRequest)
        assertEquals(userEntry.copy(offlineMapPinRequest = 2), twice.withTripForgotten())
    }

    @Test
    fun `with no trip opened, leaving changes nothing`() {
        assertSame(userEntry, userEntry.withTripForgotten())
    }

    @Test
    fun `the stops note wins over the trail note, and a route one download covers has neither`() {
        assertEquals(TripAreaNote.STOPS_OUTSIDE, userEntry.withTripOpened(route, aroundRoute(24, lineCovered = false, stopsOutside = true)).offlineMapsTripOpening?.note)
        assertEquals(TripAreaNote.TRAIL_OUTSIDE, userEntry.withTripOpened(route, aroundRoute(24, lineCovered = false, stopsOutside = false)).offlineMapsTripOpening?.note)
        assertNull(userEntry.withTripOpened(route, aroundRoute(24, lineCovered = true, stopsOutside = false)).offlineMapsTripOpening?.note)
    }
}
