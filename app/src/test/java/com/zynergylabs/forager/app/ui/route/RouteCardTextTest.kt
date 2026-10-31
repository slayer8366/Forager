package com.zynergylabs.forager.app.ui.route

import com.zynergylabs.forager.app.domain.model.UnitSystem
import com.zynergylabs.forager.app.domain.route.RouteElevation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Dispatch 2026-10-30-08, Amendment 6 (RECORD -1887): the route card's words, each written out here as the owner gave it.
 * Singular "Waypoint" only for 1. The elevation form is the owner's example, "Elevation: +240 ft (↑ 320 ↓ 80)": net change,
 * then climb and descent, in the Units setting. The metre values are worked by hand from whole feet (240 ft is 73.152 m).
 */
class RouteCardTextTest {

    @Test
    fun `one waypoint selected is singular and carries the hint, two and more are plural`() {
        assertEquals("1 Waypoint selected, tap another to route", waypointsSelectedText(1))
        assertEquals("2 Waypoints selected", waypointsSelectedText(2))
        assertEquals("3 Waypoints selected", waypointsSelectedText(3))
        assertEquals("12 Waypoints selected", waypointsSelectedText(12))
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
}
