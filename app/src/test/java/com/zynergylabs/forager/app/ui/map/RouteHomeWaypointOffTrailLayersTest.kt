package com.zynergylabs.forager.app.ui.map

import com.zynergylabs.forager.app.domain.RouteLine
import com.zynergylabs.forager.app.domain.RouteLinePart
import com.zynergylabs.forager.app.domain.RouteLinePartKind
import com.zynergylabs.forager.app.domain.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The way to a waypoint's line while the walker is off it (step 6 PR 4, dispatch 2026-10-27-01; RECORD -1725, -1676:
 * "A: trail dims only", "60%, like passed route"): the trail part and its casing at [ROUTE_PASSED_OPACITY], composed
 * with the line's own fade (stale fix) and grey (location lost, Amendment 2 of 2026-10-26-11) by the lower one winning;
 * the dashed lead-in and final leg left as they are. What each layer is set to, by layer id; how MapLibre draws it is
 * device-only.
 */
class RouteHomeWaypointOffTrailLayersTest {

    private val a = LatLng(45.0, -122.0)
    private val b = LatLng(45.001, -122.0)
    private val c = LatLng(45.002, -122.0)
    private val d = LatLng(45.002, -122.001)
    private val parts = listOf(
        RouteLinePart(RouteLinePartKind.STRAIGHT, listOf(a, b)),
        RouteLinePart(RouteLinePartKind.TRAIL, listOf(b, c)),
        RouteLinePart(RouteLinePartKind.STRAIGHT, listOf(c, d)),
    )

    private fun opacities(current: Boolean, offline: Boolean, dimmed: Boolean) = routeHomeLayerOpacities(
        RouteOnMap(RouteLine(emptyList(), listOf(a, b, c, d), current, parts, aheadIsOffline = offline, trailDimmed = dimmed), arrivedAt = null),
    )

    @Test
    fun `fresh and off, the trail dims to 60 percent and the dashed legs stay full`() {
        val o = opacities(current = true, offline = false, dimmed = true)
        assertEquals(0.6f, o.getValue(RouteHomeIds.AHEAD_LAYER))
        assertEquals(0.6f, o.getValue(RouteHomeIds.AHEAD_CASING_LAYER))
        assertEquals(1f, o.getValue(RouteHomeIds.AHEAD_STRAIGHT_LAYER))
    }

    @Test
    fun `faded on a stale fix and off, the lower of the two wins`() {
        val o = opacities(current = false, offline = false, dimmed = true)
        assertEquals(minOf(ROUTE_AHEAD_FADED_OPACITY, ROUTE_PASSED_OPACITY), o.getValue(RouteHomeIds.AHEAD_LAYER))
        assertEquals(ROUTE_AHEAD_FADED_OPACITY, o.getValue(RouteHomeIds.AHEAD_STRAIGHT_LAYER))
    }

    @Test
    fun `grey once location is lost and off, the trail still dims to 60 percent`() {
        val o = opacities(current = false, offline = true, dimmed = true)
        assertEquals(0.6f, o.getValue(RouteHomeIds.AHEAD_LAYER))
        assertEquals(0.6f, o.getValue(RouteHomeIds.AHEAD_CASING_LAYER))
        assertEquals(1f, o.getValue(RouteHomeIds.AHEAD_STRAIGHT_LAYER))
    }

    @Test
    fun `on the line every layer is as the fade and grey alone leave it`() {
        assertEquals(1f, opacities(current = true, offline = false, dimmed = false).getValue(RouteHomeIds.AHEAD_LAYER))
        assertEquals(ROUTE_AHEAD_FADED_OPACITY, opacities(current = false, offline = false, dimmed = false).getValue(RouteHomeIds.AHEAD_LAYER))
        assertEquals(1f, opacities(current = false, offline = true, dimmed = false).getValue(RouteHomeIds.AHEAD_LAYER))
    }
}
