package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.WaypointDesignation
import org.junit.Assert.assertEquals
import org.junit.Test

/** Navigation HUD stage one's map display rules — the map's filter, never Records'. */
class MapVisibleWaypointsTest {

    private val user = Waypoint(id = "u", lat = 45.5, lng = -122.6, altitude = null, name = "Big oak", note = "", createdAtEpochMillis = 1L)
    private val origin = Waypoint(id = "o", lat = 45.5, lng = -122.6, altitude = null, name = "Start · Sep 5, 9:41 AM", note = "", createdAtEpochMillis = 2L, trackId = "t1", designation = WaypointDesignation.ORIGIN)
    private val end = Waypoint(id = "e", lat = 45.5, lng = -122.6, altitude = null, name = "End · Sep 5, 11:02 AM", note = "", createdAtEpochMillis = 3L, trackId = "t1", designation = WaypointDesignation.END)
    private val all = listOf(user, origin, end)

    @Test
    fun `not navigating - only user-dropped waypoints draw`() {
        assertEquals(listOf(user), mapVisibleWaypoints(all, isNavigating = false, target = origin))
    }

    @Test
    fun `navigating to the origin - the origin draws, the end never does`() {
        assertEquals(listOf(user, origin), mapVisibleWaypoints(all, isNavigating = true, target = origin))
    }

    @Test
    fun `navigating with no target - auto waypoints stay hidden`() {
        assertEquals(listOf(user), mapVisibleWaypoints(all, isNavigating = true, target = null))
    }

    @Test
    fun `a renamed origin still draws - the filter reads the designation and id, never the name`() {
        val renamed = origin.copy(name = "Truck")
        assertEquals(listOf(user, renamed), mapVisibleWaypoints(listOf(user, renamed, end), isNavigating = true, target = renamed))
    }
}
