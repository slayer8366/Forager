package com.zynergylabs.forager.app.ui.route

import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.UnitSystem
import com.zynergylabs.forager.app.domain.model.formatDistanceMeters
import com.zynergylabs.forager.app.domain.route.RouteElevation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Dispatch 2026-10-30-08, Amendment 6 (RECORD -1887): the route card's words, each written out here as the owner gave it.
 * Singular "Waypoint" only for 1. The elevation form is the owner's example, "Elevation: +240 ft (↑ 320 ↓ 80)": net change,
 * then climb and descent, in the Units setting. The metre values are worked by hand from whole feet (240 ft is 73.152 m).
 */
class RouteCardTextTest {

    @Test
    fun `the one-stop hint is singular and is the only selected text left (Amendment 12)`() {
        assertEquals("1 Waypoint selected, tap another to route", ONE_WAYPOINT_SELECTED)
    }

    @Test
    fun `waypoints routed is plural, and a route of fewer than two waypoints has no routed line`() {
        assertEquals("2 Waypoints routed", waypointsRoutedText(2))
        assertEquals("3 Waypoints routed", waypointsRoutedText(3))
        assertThrows(IllegalArgumentException::class.java) { waypointsRoutedText(1) }
    }

    @Test
    fun `a leg reads from-name to to-name`() {
        assertEquals("Creek to Ridge", routeLegText("Creek", "Ridge"))
    }

    @Test
    fun `elevation is net change, then climb and descent, in feet for Imperial`() {
        assertEquals("Elevation: +240 ft (↑ 320 ↓ 80)", routeElevationText(RouteElevation(73.152, 97.536, 24.384), UnitSystem.IMPERIAL))
    }

    @Test
    fun `elevation in metres for Metric, and a net fall reads with a minus`() {
        assertEquals("Elevation: +73 m (↑ 98 ↓ 24)", routeElevationText(RouteElevation(73.152, 97.536, 24.384), UnitSystem.METRIC))
        assertEquals("Elevation: -30 m (↑ 10 ↓ 40)", routeElevationText(RouteElevation(-30.0, 10.0, 40.0), UnitSystem.METRIC))
    }

    // Amendment 10 (RECORD -1904, the owner: "Yes, add up what's shown (Recommended)"): every total is the sum of the rows' shown,
    // rounded figures in the current units, so the digits always add up.

    private val metresPerMile = 1609.344
    private fun total(unit: DistanceUnit, vararg meters: Double) = routeTotalDistanceText(meters.toList(), unit)

    @Test
    fun `the phone's distance case - three rows of 0_4 mi total 1_2 mi, where the metres summed show 1_3`() {
        val leg = 0.44 * metresPerMile
        assertEquals("the rows", "0.4 mi", formatDistanceMeters(leg, DistanceUnit.MILES))
        assertEquals("what the metres summed show", "1.3 mi", formatDistanceMeters(leg * 3, DistanceUnit.MILES))
        assertEquals("1.2 mi", total(DistanceUnit.MILES, leg, leg, leg))
    }

    @Test
    fun `rows in tenths of a kilometre add up the same way`() {
        assertEquals("the rows", "1.2 km", formatDistanceMeters(1_240.0, DistanceUnit.KILOMETERS))
        assertEquals("3.6 km", total(DistanceUnit.KILOMETERS, 1_240.0, 1_240.0, 1_240.0))
    }

    @Test
    fun `rows in whole feet or metres add up in those units`() {
        assertEquals("the rows", "33 ft", formatDistanceMeters(10.15, DistanceUnit.MILES))
        assertEquals("99 ft", total(DistanceUnit.MILES, 10.15, 10.15, 10.15))
        assertEquals("the rows", "100 m", formatDistanceMeters(100.4, DistanceUnit.KILOMETERS))
        assertEquals("300 m", total(DistanceUnit.KILOMETERS, 100.4, 100.4, 100.4))
    }

    @Test
    fun `a total that reaches the longer unit is the shown rows' sum in that unit`() {
        // 900 + 900 = 1,800 ft is 0.34 mi; 400 + 400 + 400 = 1,200 m.
        assertEquals("the rows", "900 ft", formatDistanceMeters(274.32, DistanceUnit.MILES))
        assertEquals("0.3 mi", total(DistanceUnit.MILES, 274.32, 274.32))
        assertEquals("the rows", "400 m", formatDistanceMeters(400.4, DistanceUnit.KILOMETERS))
        assertEquals("1.2 km", total(DistanceUnit.KILOMETERS, 400.4, 400.4, 400.4))
    }

    @Test
    fun `rows in two units are each taken at their shown figure`() {
        // 0.4 mi and 900 ft: 0.4 + 0.17 reads 0.6, where the metres of 0.44 mi and 900 ft (0.61 mi) would too; 0.46 mi and 900 ft
        // are the case that differs, 0.5 + 0.17 reads 0.7 and the metres (0.63 mi) read 0.6.
        assertEquals("0.6 mi", total(DistanceUnit.MILES, 0.44 * metresPerMile, 274.32))
        assertEquals("0.7 mi", total(DistanceUnit.MILES, 0.46 * metresPerMile, 274.32))
    }

    @Test
    fun `one row's total is that row`() {
        assertEquals("0.4 mi", total(DistanceUnit.MILES, 0.44 * metresPerMile))
    }

    @Test
    fun `the elevation total adds up the shown digits, not the metres`() {
        // Each leg is 1.6 ft: it shows 2, and the metres summed, 4.8 ft, would show 5.
        val leg = RouteElevation(0.48768, 0.48768, 0.0)
        assertEquals("Elevation: +2 ft (↑ 2 ↓ 0)", routeElevationText(leg, UnitSystem.IMPERIAL))
        assertEquals("Elevation: +6 ft (↑ 6 ↓ 0)", routeTotalElevationText(listOf(leg, leg, leg), UnitSystem.IMPERIAL))
        // Each is 1.4 m down: it shows -1, and the metres summed, 4.2 m, would show -4.
        val fall = RouteElevation(-1.4, 0.0, 1.4)
        assertEquals("Elevation: -3 m (↑ 0 ↓ 3)", routeTotalElevationText(listOf(fall, fall, fall), UnitSystem.METRIC))
    }

    @Test
    fun `an elevation total of nothing reads plus zero, and only when every leg has an elevation`() {
        val up = RouteElevation(3.0, 3.0, 0.0)
        val down = RouteElevation(-3.0, 0.0, 3.0)
        assertEquals("Elevation: +0 m (↑ 3 ↓ 3)", routeTotalElevationText(listOf(up, down), UnitSystem.METRIC))
        assertNull(routeTotalElevationText(listOf(up, null), UnitSystem.METRIC))
        assertNull(routeTotalElevationText(emptyList(), UnitSystem.METRIC))
    }
}
