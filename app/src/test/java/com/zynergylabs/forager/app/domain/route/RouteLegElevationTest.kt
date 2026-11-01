package com.zynergylabs.forager.app.domain.route

import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.UnitSystem
import com.zynergylabs.forager.app.ui.route.routeElevationText
import com.zynergylabs.forager.app.ui.route.routeTotalElevationText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A plotted leg's elevation and the whole route's (dispatch 2026-10-30-08, Amendment 8, RECORD -1896): the net change is the
 * last height minus the first; the climb and descent are banked through the journal's 4 m hysteresis (`bankedClimb`). No
 * elevation for a straight leg, a leg with no heights, or a leg with only some (the rule proposed in the PR body: show
 * nothing). The total only when every leg has one.
 *
 * Amendment 9 (RECORD -1902, the owner: "Total = sum of the legs (Recommended)"): the total's net change, climb and descent
 * are the sums of the legs' own figures, so the total matches the rows above it. This replaces Amendment 8's one run banked
 * across the legs, which on the S22 showed legs of ↑ 0, ↑ 0 and ↑ 14 ft over a total of ↑ 13
 * (docs/audits/2026-10-31-s22-phone-checks.md, F-1).
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
    fun `the total is the sum of the legs' own figures, not one run banked across them`() {
        val first = trail(a, b, listOf(100.0, 110.0, 104.0, 107.0))
        val second = trail(b, c, listOf(107.0, 110.0, 112.0))
        // Leg by leg: +7, 10 up 6 down; then +5, 5 up (107 to 112). Summed: +12, 15 up, 6 down. One run banked across both would
        // bank 104 to 110 as 6, so 16 up: the figure Amendment 9 replaces.
        assertElevation(RouteElevation(5.0, 5.0, 0.0), legElevation(second))
        assertEquals("Elevation: +12 m (↑ 15 ↓ 6)", routeTotalElevationText(listOf(first, second).map(::legElevation), UnitSystem.METRIC))
    }

    @Test
    fun `the phone's case - legs of up 0, up 0 and up 14 ft give a total of up 14`() {
        // Shaped on the S22's four-waypoint plot (F-1): two nearly flat legs, then one that climbs 4.3 m. Per leg: 4 m down and
        // 2 m pending; 1 m, short of 4; then 4.3 m up. One run across them would bank 96 to 100 as a climb of 4 and give 8.3 m up.
        val first = trail(a, b, listOf(100.0, 96.0, 98.0))
        val second = trail(b, c, listOf(99.0, 100.0))
        val third = trail(c, a, listOf(100.0, 104.3))
        val legs = listOf(first, second, third)
        fun shown(e: RouteElevation?) = routeElevationText(requireNotNull(e) { "an elevation" }, UnitSystem.IMPERIAL)
        assertEquals("Elevation: -7 ft (↑ 0 ↓ 13)", shown(legElevation(first)))
        assertEquals("Elevation: +3 ft (↑ 0 ↓ 0)", shown(legElevation(second)))
        assertEquals("Elevation: +14 ft (↑ 14 ↓ 0)", shown(legElevation(third)))
        // Amendment 10 (RECORD -1904): the total adds up the shown digits, -7 + 3 + 14, so +10; the metres summed would show +11.
        assertEquals("the total is the sum of the shown rows", "Elevation: +10 ft (↑ 14 ↓ 13)", routeTotalElevationText(legs.map(::legElevation), UnitSystem.IMPERIAL))
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
        fun total(vararg legs: RouteLeg) = routeTotalElevationText(legs.map(::legElevation), UnitSystem.METRIC)
        assertEquals("Elevation: +10 m (↑ 10 ↓ 0)", total(heighted))
        assertNull("a straight leg after it", total(heighted, straight(b, c)))
        assertNull("a leg with only some heights after it", total(heighted, trail(b, c, listOf(110.0, null))))
        assertNull("no legs", total())
    }
}
