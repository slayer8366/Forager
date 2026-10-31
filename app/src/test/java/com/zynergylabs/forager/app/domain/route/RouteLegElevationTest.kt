package com.zynergylabs.forager.app.domain.route

import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A plotted leg's elevation and the whole route's (dispatch 2026-10-30-08, Amendment 8, RECORD -1896): the net change is the
 * last height minus the first; the climb and descent are banked through the journal's 4 m hysteresis (`bankedClimb`); the
 * total is banked across the legs, not summed from them. No elevation for a straight leg, a leg with no heights, or a leg
 * with only some (the rule proposed in the PR body: show nothing). The total only when every leg has one.
 */
class RouteLegElevationTest {

    private val a = LatLng(45.30, -122.60)
    private val b = LatLng(45.31, -122.61)
    private val c = LatLng(45.32, -122.62)

    /** A trail leg from [from] to [to] whose trail part carries [heights], one per point, between the two joins. */
    private fun trail(from: LatLng, to: LatLng, heights: List<Double?>): RouteLeg.Trail {
        val n = heights.size
        val points = (1..n).map { k -> LatLng(from.lat + (to.lat - from.lat) * k / (n + 1), from.lng + (to.lng - from.lng) * k / (n + 1)) }
        return RouteLeg.Trail(listOf(from) + points + listOf(to), GeoDistance.metersBetween(from, to), trailFrom = 1, trailTo = n, trailHeightsMetres = heights)
    }

    private fun straight(from: LatLng, to: LatLng) = RouteLeg.Straight(listOf(from, to), GeoDistance.metersBetween(from, to), StraightReason.NO_TRAIL_ROUTES)

    private fun assertElevation(expected: RouteElevation, actual: RouteElevation?) {
        requireNotNull(actual) { "an elevation, expected $expected" }
        assertEquals("net", expected.netMeters, actual.netMeters, 1e-9)
        assertEquals("climb", expected.climbMeters, actual.climbMeters, 1e-9)
        assertEquals("descent", expected.descentMeters, actual.descentMeters, 1e-9)
    }

    @Test
    fun `a leg's net change is its last height minus its first, and its climb and descent are banked through 4 m`() {
        // 100 to 110 banks 10 up; 104 banks 6 down; 107 is 3 above 104, short of 4, so it stays pending.
        assertElevation(RouteElevation(7.0, 10.0, 6.0), legElevation(trail(a, b, listOf(100.0, 110.0, 104.0, 107.0))))
    }

    @Test
    fun `the total is banked across the legs, so a change pending at one leg's end is counted in the next`() {
        val first = trail(a, b, listOf(100.0, 110.0, 104.0, 107.0))
        val second = trail(b, c, listOf(107.0, 110.0, 112.0))
        // Leg by leg: 10 up 6 down, then 5 up (107 to 112). Across both: 104 to 110 banks 6, then 112 is 2 short.
        assertElevation(RouteElevation(5.0, 5.0, 0.0), legElevation(second))
        assertElevation(RouteElevation(12.0, 16.0, 6.0), routeElevation(listOf(first, second)))
    }

    @Test
    fun `a straight leg, a leg with no heights, and a leg with only some have no elevation`() {
        assertNull("straight", legElevation(straight(a, b)))
        assertNull("no heights", legElevation(trail(a, b, listOf(null, null, null))))
        assertNull("only some", legElevation(trail(a, b, listOf(100.0, null, 120.0))))
    }

    @Test
    fun `the total shows only when every leg has heights`() {
        val heighted = trail(a, b, listOf(100.0, 110.0))
        assertElevation(RouteElevation(10.0, 10.0, 0.0), routeElevation(listOf(heighted)))
        assertNull("a straight leg after it", routeElevation(listOf(heighted, straight(b, c))))
        assertNull("a leg with only some heights after it", routeElevation(listOf(heighted, trail(b, c, listOf(110.0, null)))))
        assertNull("no legs", routeElevation(emptyList()))
    }
}
