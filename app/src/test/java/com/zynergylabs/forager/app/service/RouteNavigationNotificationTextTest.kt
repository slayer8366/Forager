package com.zynergylabs.forager.app.service

import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.route.PlottedRoute
import com.zynergylabs.forager.app.domain.route.RouteLeg
import com.zynergylabs.forager.app.domain.route.RouteStop
import com.zynergylabs.forager.app.domain.route.StraightReason
import com.zynergylabs.forager.app.domain.routenav.RouteNavigation
import com.zynergylabs.forager.app.domain.routenav.RouteNavigationWatchState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Dispatch 2026-11-03-03, A9: what route navigation's ongoing notification says, at each point of the route. */
class RouteNavigationNotificationTextTest {

    private val creek = RouteStop("Creek", LatLng(45.0, -122.0))
    private val ridge = RouteStop("Ridge", LatLng(45.002, -122.0))
    private val route = PlottedRoute(listOf(creek, ridge), listOf(RouteLeg.Straight(listOf(creek.position, ridge.position), 222.0, StraightReason.NO_REGION_COVERS)))

    private fun at(next: Int) = RouteNavigationWatchState(RouteNavigation("trip-1", next, resumesReturn = false), "Ridge loop", route)

    @Test
    fun `it names the next stop and its place, then Arrived after the last`() {
        assertEquals("To Creek · 1 of 2", routeNavigationNotificationText(at(0)))
        assertEquals("To Ridge · 2 of 2", routeNavigationNotificationText(at(1)))
        assertEquals("Arrived", routeNavigationNotificationText(at(2)))
    }

    @Test
    fun `with nothing watched it says nothing, and an index past the end is never a stop`() {
        assertNull(routeNavigationNotificationText(RouteNavigationWatchState()))
        assertNull(routeNavigationNotificationText(at(3)))
    }
}
