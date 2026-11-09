package com.zynergylabs.forager.app.domain.route

import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.domain.model.Waypoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Dispatch 2026-11-06-05, Amendment 2, item 3 (RECORD -2187): where a saved route's stop sends Navigate. Its link while the record is
 * on the phone; with none, a waypoint (then a trip) at exactly its spot with exactly its name; otherwise nowhere, which the card shows
 * as the owner's "This stop's waypoint was moved or deleted".
 */
class RouteStopNavigateTargetTest {

    private val creek = Waypoint("wp-creek", 45.37, -121.69, null, "Creek", "", 1L)
    private val ridge = Waypoint("wp-ridge", 45.38, -121.70, null, "Ridge", "", 2L)
    private val saddle = PlannedTrip("trip-saddle", "Saddle loop", LatLng(45.40, -121.72), null)
    private val creekSpot = LatLng(creek.lat, creek.lng)

    @Test
    fun `a linked stop goes to its waypoint while it is on the phone, even after it has moved`() {
        val moved = creek.copy(lat = 45.5, lng = -121.9)
        assertEquals(RouteStopLink.Waypoint(creek.id), navigateTargetOf(RouteStop("Creek", creekSpot, RouteStopLink.Waypoint(creek.id)), listOf(moved, ridge), emptyList()))
    }

    @Test
    fun `a linked stop goes to its trip while it is on the phone`() {
        assertEquals(RouteStopLink.Trip(saddle.id), navigateTargetOf(RouteStop("Saddle loop", saddle.location, RouteStopLink.Trip(saddle.id)), emptyList(), listOf(saddle)))
    }

    @Test
    fun `a stop with no link goes to the waypoint at exactly its spot with exactly its name`() {
        assertEquals(RouteStopLink.Waypoint(creek.id), navigateTargetOf(RouteStop("Creek", creekSpot), listOf(ridge, creek), emptyList()))
    }

    @Test
    fun `a stop with no link goes to the trip at its spot with its name, when no waypoint is`() {
        assertEquals(RouteStopLink.Trip(saddle.id), navigateTargetOf(RouteStop("Saddle loop", saddle.location), listOf(creek), listOf(saddle)))
    }

    @Test
    fun `a stop whose waypoint moved, was renamed or was deleted goes nowhere`() {
        assertNull("moved", navigateTargetOf(RouteStop("Creek", creekSpot), listOf(creek.copy(lat = creek.lat + 0.0001)), emptyList()))
        assertNull("renamed", navigateTargetOf(RouteStop("Creek", creekSpot), listOf(creek.copy(name = "Creek bend")), emptyList()))
        assertNull("deleted", navigateTargetOf(RouteStop("Creek", creekSpot), listOf(ridge), emptyList()))
    }

    @Test
    fun `a link to a waypoint already gone falls back to the spot and name, and to nowhere after that`() {
        val stop = RouteStop("Creek", creekSpot, RouteStopLink.Waypoint("wp-gone"))
        assertEquals(RouteStopLink.Waypoint(creek.id), navigateTargetOf(stop, listOf(creek), emptyList()))
        assertNull(navigateTargetOf(stop, listOf(ridge), emptyList()))
    }

    @Test
    fun `a tapped marker selects the first stop leading to it, and a marker no stop leads to selects none`() {
        val stops = listOf(RouteStop("Creek", creekSpot, RouteStopLink.Waypoint(creek.id)), RouteStop("Ridge", LatLng(ridge.lat, ridge.lng)), RouteStop("Creek", creekSpot, RouteStopLink.Waypoint(creek.id)))
        assertEquals(0, routeStopIndexOf(stops, RouteStopLink.Waypoint(creek.id), listOf(creek, ridge), emptyList()))
        assertEquals("an old stop, matched by spot and name", 1, routeStopIndexOf(stops, RouteStopLink.Waypoint(ridge.id), listOf(creek, ridge), emptyList()))
        assertNull(routeStopIndexOf(stops, RouteStopLink.Waypoint("wp-elsewhere"), listOf(creek, ridge), emptyList()))
    }
}
