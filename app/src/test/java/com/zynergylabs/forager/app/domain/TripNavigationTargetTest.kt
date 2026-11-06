package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.domain.model.Waypoint
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Dispatch 2026-11-05-05, Amendment 1, A8 (RECORD -2148, the owner: "As a trip (Recommended)"): a navigation to a trip
 * resolves against the trips the app has loaded, never the waypoints, and waits for the trips to load rather than ending
 * as deleted. A waypoint navigation resolves exactly as before ([WaypointNavigationTargetTest]).
 */
class TripNavigationTargetTest {

    private val creek = Waypoint("wp-1", 45.3736, -121.6959, null, "Creek pin", "", 1_000L)
    private val saddle = PlannedTrip("trip-1", "Saddle loop", LatLng(45.56, -122.66), LocalDate.of(2026, 10, 3))
    private val other = PlannedTrip("trip-2", "Ridge day", LatLng(45.40, -122.50), null)
    private val toSaddle = WaypointNavigation("trip-1", resumesReturn = false, kind = NavigationTargetKind.TRIP)
    private val toCreek = WaypointNavigation("wp-1", resumesReturn = false)

    private fun found(target: WaypointNavigationTarget): Waypoint =
        (target as? WaypointNavigationTarget.Found)?.waypoint ?: throw AssertionError("expected Found, was $target")

    @Test
    fun `a trip being navigated to is found among the loaded trips, as a point at the trip's spot under its name and id`() {
        val point = found(navigationTargetOf(toSaddle, listOf(creek), waypointsLoaded = true, trips = listOf(other, saddle), tripsLoaded = true))

        assertEquals("trip-1", point.id)
        assertEquals("Saddle loop", point.name)
        assertEquals(45.56, point.lat, 0.0)
        assertEquals(-122.66, point.lng, 0.0)
        assertNull("no height of its own: a trip stores none", point.altitude)
        assertNull("an ordinary target, never the start or the end of a track", point.designation)
    }

    @Test
    fun `a trip is never looked for among the waypoints, even one with the same id`() {
        val sameId = creek.copy(id = "trip-1")

        assertEquals(
            WaypointNavigationTarget.Gone,
            navigationTargetOf(toSaddle, listOf(sameId), waypointsLoaded = true, trips = listOf(other), tripsLoaded = true),
        )
    }

    @Test
    fun `before the trips have loaded, a trip not in the list is waited for, whatever the waypoints say`() {
        assertEquals(
            WaypointNavigationTarget.Waiting,
            navigationTargetOf(toSaddle, listOf(creek), waypointsLoaded = true, trips = emptyList(), tripsLoaded = false),
        )
    }

    @Test
    fun `once the trips have loaded, a trip not among them is gone`() {
        assertEquals(WaypointNavigationTarget.Gone, navigationTargetOf(toSaddle, emptyList(), waypointsLoaded = false, trips = listOf(other), tripsLoaded = true))
        assertEquals(WaypointNavigationTarget.Gone, navigationTargetOf(toSaddle, emptyList(), waypointsLoaded = true, trips = emptyList(), tripsLoaded = true))
    }

    @Test
    fun `a trip found before the first load counts as found`() {
        assertEquals("trip-1", found(navigationTargetOf(toSaddle, emptyList(), waypointsLoaded = false, trips = listOf(saddle), tripsLoaded = false)).id)
    }

    @Test
    fun `a waypoint navigation resolves as before, with the trips unread`() {
        val tripWithTheWaypointsId = saddle.copy(id = "wp-1")
        assertEquals(WaypointNavigationTarget.Found(creek), navigationTargetOf(toCreek, listOf(creek), waypointsLoaded = true, trips = listOf(tripWithTheWaypointsId), tripsLoaded = true))
        assertEquals(WaypointNavigationTarget.Waiting, navigationTargetOf(toCreek, emptyList(), waypointsLoaded = false, trips = listOf(tripWithTheWaypointsId), tripsLoaded = true))
        assertEquals(WaypointNavigationTarget.Gone, navigationTargetOf(toCreek, emptyList(), waypointsLoaded = true, trips = listOf(tripWithTheWaypointsId), tripsLoaded = true))
    }

    @Test
    fun `nothing chosen is no target`() {
        assertEquals(WaypointNavigationTarget.None, navigationTargetOf(null, listOf(creek), waypointsLoaded = true, trips = listOf(saddle), tripsLoaded = true))
    }

    @Test
    fun `a navigation made with no kind is a waypoint's, as every one kept before the update`() {
        assertEquals(NavigationTargetKind.WAYPOINT, WaypointNavigation("wp-1", resumesReturn = true).kind)
    }
}
