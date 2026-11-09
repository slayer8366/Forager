package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.Waypoint
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Dispatch 2026-09-28-502, Amendment 1: a waypoint being navigated to resolves against the waypoints
 * the app has loaded. Deleted meanwhile, or while the app was closed, it is [WaypointNavigationTarget.Gone]
 * (the screen ends the navigation and says so); before the first load an absent id is only waited for,
 * since an empty list before the first read is not a deletion.
 */
class WaypointNavigationTargetTest {

    private val creek = Waypoint("wp-1", 45.326, -122.634, null, "Creek pin", "", 1_000L)
    private val oak = Waypoint("wp-2", 45.33, -122.64, null, "Big oak", "", 2_000L)
    private val toCreek = WaypointNavigation("wp-1", resumesReturn = false)

    @Test
    fun `nothing chosen is no target, loaded or not`() {
        assertEquals(WaypointNavigationTarget.None, waypointNavigationTarget(null, listOf(creek), waypointsLoaded = true))
        assertEquals(WaypointNavigationTarget.None, waypointNavigationTarget(null, emptyList(), waypointsLoaded = false))
    }

    @Test
    fun `the chosen waypoint is found by its id among the loaded waypoints`() {
        assertEquals(WaypointNavigationTarget.Found(creek), waypointNavigationTarget(toCreek, listOf(oak, creek), waypointsLoaded = true))
    }

    @Test
    fun `before the waypoints have loaded, a chosen id not in the list is waited for, not taken as deleted`() {
        assertEquals(WaypointNavigationTarget.Waiting, waypointNavigationTarget(toCreek, emptyList(), waypointsLoaded = false))
    }

    @Test
    fun `once loaded, a chosen id not in the list is gone`() {
        assertEquals(WaypointNavigationTarget.Gone, waypointNavigationTarget(toCreek, listOf(oak), waypointsLoaded = true))
        assertEquals(WaypointNavigationTarget.Gone, waypointNavigationTarget(toCreek, emptyList(), waypointsLoaded = true))
    }

    @Test
    fun `a waypoint found before the first load counts as found`() {
        assertEquals(WaypointNavigationTarget.Found(creek), waypointNavigationTarget(toCreek, listOf(creek), waypointsLoaded = false))
    }
}
