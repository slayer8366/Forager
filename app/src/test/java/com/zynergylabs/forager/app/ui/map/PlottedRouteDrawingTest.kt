package com.zynergylabs.forager.app.ui.map

import com.zynergylabs.forager.app.domain.EntryMapFrame
import com.zynergylabs.forager.app.domain.model.GeoBoundingBox
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.route.PlottedRoute
import com.zynergylabs.forager.app.domain.route.RouteLeg
import com.zynergylabs.forager.app.domain.route.RouteStop
import com.zynergylabs.forager.app.domain.route.StraightReason
import com.zynergylabs.forager.app.domain.route.routeFrame
import com.zynergylabs.forager.app.ui.theme.MapPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** How a plotted route is drawn and fitted (dispatch 2026-10-30-08, step 3): headless, the parts the map layers are fed. */
class PlottedRouteDrawingTest {

    private val a = LatLng(45.20, -122.50)
    private val t1 = LatLng(45.21, -122.51)
    private val t2 = LatLng(45.22, -122.52)
    private val b = LatLng(45.23, -122.53)

    @Test
    fun `a trail leg is drawn as a dashed join, its trail part solid, and a dashed join`() {
        val leg = RouteLeg.Trail(listOf(a, t1, t2, b), 400.0, trailFrom = 1, trailTo = 2)

        assertEquals(
            listOf(
                PlottedRoutePart(PlottedRouteIds.PART_STRAIGHT, listOf(a, t1)),
                PlottedRoutePart(PlottedRouteIds.PART_TRAIL, listOf(t1, t2)),
                PlottedRoutePart(PlottedRouteIds.PART_STRAIGHT, listOf(t2, b)),
            ),
            plottedRouteParts(listOf(leg)),
        )
    }

    @Test
    fun `a straight leg is drawn whole and dashed`() {
        val leg = RouteLeg.Straight(listOf(a, b), 900.0, StraightReason.NO_REGION_COVERS)

        assertEquals(listOf(PlottedRoutePart(PlottedRouteIds.PART_STRAIGHT, listOf(a, b))), plottedRouteParts(listOf(leg)))
    }

    @Test
    fun `N9, a stop exactly on the trail gives a zero-length join, kept as two points at one place`() {
        val leg = RouteLeg.Trail(listOf(a, a, t2, b), 300.0, trailFrom = 1, trailTo = 2)

        assertEquals(PlottedRoutePart(PlottedRouteIds.PART_STRAIGHT, listOf(a, a)), plottedRouteParts(listOf(leg)).first())
    }

    @Test
    fun `a saved route is fitted to every point of its legs, with the card's height below`() {
        val route = PlottedRoute(
            listOf(RouteStop("A", a), RouteStop("B", b)),
            listOf(RouteLeg.Trail(listOf(a, LatLng(45.30, -122.40), b), 500.0, trailFrom = 0, trailTo = 2)),
        )

        assertEquals(
            EntryMapFrame.FitInsets(GeoBoundingBox(north = 45.30, south = 45.20, east = -122.40, west = -122.53), topDp = 48, bottomDp = 200, leftDp = 48, rightDp = 48),
            routeFrame(route, sideDp = 48, bottomDp = 200),
        )
    }

    @Test
    fun `a request that stops following ends GPS following once per id, and only then`() {
        val frame = EntryMapFrame.SinglePoint(a)
        val asked = MapCameraRequest("r1", frame, stopsFollowing = true)

        assertTrue(cameraRequestStopsFollowing(isGpsTracking = true, request = asked, lastAppliedRequestId = null))
        assertFalse("already applied", cameraRequestStopsFollowing(true, asked, "r1"))
        assertFalse("not following", cameraRequestStopsFollowing(false, asked, null))
        assertFalse("an entry map's frame", cameraRequestStopsFollowing(true, MapCameraRequest("r2", frame), null))
    }

    @Test
    fun `the joins and straight legs carry the trail's casing, white by day and black by night, outlining each dash`() {
        // Dispatch 2026-10-30-08, Amendment 3 (RECORD -1861), the owner: "Add a thin outline (Recommended)".
        for ((palette, casing) in listOf(MapPalette.DAY to 0xFFFFFFFF.toInt(), MapPalette.NIGHT to 0xFF000000.toInt())) {
            val specs = plottedRouteLineSpecs(palette)
            fun one(part: String, colour: Int) = specs.singleOrNull { it.part == part && it.colour == colour }
                ?: throw AssertionError("${palette.name()}: no single $part line in ${colourOf(colour)}: $specs")
            val routeColour = if (palette == MapPalette.NIGHT) PLOTTED_ROUTE_COLOUR_NIGHT else PLOTTED_ROUTE_COLOUR_DAY
            val trail = one(PlottedRouteIds.PART_TRAIL, routeColour)
            val trailCasing = one(PlottedRouteIds.PART_TRAIL, casing)
            val straight = one(PlottedRouteIds.PART_STRAIGHT, routeColour)
            val straightCasing = one(PlottedRouteIds.PART_STRAIGHT, casing)
            val name = palette.name()

            assertEquals("$name: the same casing width each side", trailCasing.widthDp - trail.widthDp, straightCasing.widthDp - straight.widthDp, 0.001f)
            assertEquals("$name: both casings are under both lines", setOf(0, 1), setOf(specs.indexOf(trailCasing), specs.indexOf(straightCasing)))
            // lineDasharray is in line widths, so the casing's dashes are as long in dp as the line's own: each dash outlined.
            assertEquals("$name: dashes in dp", straight.dashes!!.map { it * straight.widthDp }, straightCasing.dashes!!.map { it * straightCasing.widthDp })
            assertFalse("$name: butt caps, as the dashes", straightCasing.roundCaps)
        }
    }

    private fun MapPalette.name() = if (this == MapPalette.NIGHT) "night" else "day"
    private fun colourOf(colour: Int) = "#%08X".format(colour)

}
