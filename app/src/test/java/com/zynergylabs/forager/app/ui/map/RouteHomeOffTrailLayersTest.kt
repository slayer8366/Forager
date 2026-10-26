package com.zynergylabs.forager.app.ui.map

import com.zynergylabs.forager.app.domain.RouteLine
import com.zynergylabs.forager.app.domain.RouteLinePart
import com.zynergylabs.forager.app.domain.RouteLinePartKind
import com.zynergylabs.forager.app.domain.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The way home's line while the walker is off the trail (dispatch 2026-10-26-13, plan step 7 PR 3; RECORD -1676, the
 * owner: "A: trail dims only", "60%, like passed route"): the solid trail layers and their casing at
 * [ROUTE_PASSED_OPACITY], the dotted track left as it is, and the withheld fade not multiplied in (the lower one wins).
 * What each layer is set to, by layer id; how MapLibre draws it is device-only.
 */
class RouteHomeOffTrailLayersTest {

    private val a = LatLng(45.0, -122.0)
    private val b = LatLng(45.001, -122.0)
    private val c = LatLng(45.002, -122.0)
    private val parts = listOf(RouteLinePart(RouteLinePartKind.TRACK, listOf(a, b)), RouteLinePart(RouteLinePartKind.TRAIL, listOf(b, c)))

    private fun opacities(current: Boolean, dimmed: Boolean) =
        routeHomeLayerOpacities(RouteOnMap(RouteLine(listOf(a, b, c), listOf(a, b, c), current, parts, trailDimmed = dimmed), arrivedAt = null))

    @Test
    fun `on the trail every line is at full strength`() {
        val o = opacities(current = true, dimmed = false)
        assertEquals(1f, o.getValue(RouteHomeIds.AHEAD_LAYER))
        assertEquals(1f, o.getValue(RouteHomeIds.AHEAD_CASING_LAYER))
        assertEquals(1f, o.getValue(RouteHomeIds.AHEAD_DOTTED_LAYER))
    }

    @Test
    fun `off the trail the trail and its casing dim to 60 percent, and the dotted track stays as it is`() {
        val o = opacities(current = true, dimmed = true)
        assertEquals(0.6f, o.getValue(RouteHomeIds.AHEAD_LAYER))
        assertEquals(0.6f, o.getValue(RouteHomeIds.AHEAD_CASING_LAYER))
        assertEquals(1f, o.getValue(RouteHomeIds.AHEAD_DOTTED_LAYER))
        assertEquals(1f, o.getValue(RouteHomeIds.AHEAD_STRAIGHT_LAYER))
    }

    @Test
    fun `withheld and off the trail, the lower of the two wins, never their product`() {
        val o = opacities(current = false, dimmed = true)
        assertEquals(ROUTE_AHEAD_FADED_OPACITY, o.getValue(RouteHomeIds.AHEAD_LAYER))
        assertEquals(ROUTE_AHEAD_FADED_OPACITY, o.getValue(RouteHomeIds.AHEAD_CASING_LAYER))
        assertEquals(ROUTE_AHEAD_FADED_OPACITY, o.getValue(RouteHomeIds.AHEAD_DOTTED_LAYER))
    }
}
