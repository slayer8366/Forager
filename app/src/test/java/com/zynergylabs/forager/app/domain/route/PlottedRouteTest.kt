package com.zynergylabs.forager.app.domain.route

import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** The rules [PlottedRoute] and its legs refuse at construction (dispatch 2026-10-30-02, step 1; Amendment 1). */
class PlottedRouteTest {

    private val a = LatLng(45.2, -122.5)
    private val b = LatLng(45.22, -122.52)

    private fun straight(from: LatLng, to: LatLng, meters: Double = 100.0) = RouteLeg.Straight(listOf(from, to), meters, StraightReason.NO_REGION_COVERS)

    @Test
    fun `A to B to A is allowed, and its total is the sum of its legs`() {
        val route = PlottedRoute(listOf(RouteStop("A", a), RouteStop("B", b), RouteStop("A again", a)), listOf(straight(a, b, 120.5), straight(b, a, 130.25)))

        assertEquals(250.75, route.totalMeters, 1e-9)
    }

    @Test
    fun `two consecutive stops at the same position are refused`() {
        val e = assertThrows(IllegalArgumentException::class.java) {
            PlottedRoute(listOf(RouteStop("A", a), RouteStop("A", a)), listOf(straight(a, a)))
        }
        assertEquals("stops 0 and 1 are at the same position", e.message)
    }

    @Test
    fun `a leg count that is not one fewer than the stops is refused`() {
        assertThrows(IllegalArgumentException::class.java) { PlottedRoute(listOf(RouteStop("A", a), RouteStop("B", b)), emptyList()) }
    }

    @Test
    fun `a leg that does not run between its two stops is refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            PlottedRoute(listOf(RouteStop("A", a), RouteStop("B", b)), listOf(straight(b, a)))
        }
    }

    @Test
    fun `a trail part outside the leg's line is refused`() {
        assertThrows(IllegalArgumentException::class.java) { RouteLeg.Trail(listOf(a, b), 100.0, trailFrom = 0, trailTo = 2) }
        assertThrows(IllegalArgumentException::class.java) { RouteLeg.Trail(listOf(a, b), 100.0, trailFrom = 1, trailTo = 1) }
    }

    @Test
    fun `a route trip's pin is its first stop, refused otherwise`() {
        val route = PlottedRoute(listOf(RouteStop("A", a), RouteStop("B", b)), listOf(straight(a, b)))

        assertEquals(a, PlannedTrip("t", "Ridge run", a, null, route).location)
        assertThrows(IllegalArgumentException::class.java) { PlannedTrip("t", "Ridge run", b, null, route) }
    }
}
