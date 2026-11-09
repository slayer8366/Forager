package com.zynergylabs.forager.app.ui.map

import com.zynergylabs.forager.app.domain.RouteLine
import com.zynergylabs.forager.app.domain.RouteLinePart
import com.zynergylabs.forager.app.domain.RouteLinePartKind
import com.zynergylabs.forager.app.domain.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.maplibre.geojson.LineString

/**
 * Dispatch 2026-10-24-10 (RECORD -1589, "Dotted, same colour"): the way home along trails reaches the map as one source
 * whose features name their kind, so the dotted layer draws the walker's track and the solid layer the trail, and both
 * change in one update. The track home stays one feature with no kind, drawn solid as before. How MapLibre draws them is
 * device-only.
 */
class RouteHomeWayHomeLayersTest {

    private val a = LatLng(45.0, -122.0)
    private val b = LatLng(45.001, -122.0)
    private val c = LatLng(45.002, -122.0)
    private val d = LatLng(45.003, -122.0)

    private val parts = listOf(
        RouteLinePart(RouteLinePartKind.TRACK, listOf(a, b)),
        RouteLinePart(RouteLinePartKind.TRAIL, listOf(b, c)),
        RouteLinePart(RouteLinePartKind.TRACK, listOf(c, d)),
    )

    @Test
    fun `a way home along trails goes to the ahead source as one feature per run, each naming its kind`() {
        val line = RouteLine(atReturn = listOf(a, b, c, d), ahead = listOf(a, b, c, d), aheadIsCurrent = true, aheadParts = parts)
        val features = routeHomeFeatureCollections(RouteOnMap(line, arrivedAt = null)).getValue(RouteHomeIds.AHEAD_SOURCE).features()!!
        assertEquals(listOf(RouteHomeIds.PART_TRACK, RouteHomeIds.PART_TRAIL, RouteHomeIds.PART_TRACK), features.map { it.getStringProperty(RouteHomeIds.PART_PROPERTY) })
        assertEquals(listOf(listOf(a, b), listOf(b, c), listOf(c, d)), features.map { f -> (f.geometry() as LineString).coordinates().map { LatLng(it.latitude(), it.longitude()) } })
    }

    @Test
    fun `the track home is one feature with no kind, and arrival still ends the line`() {
        val track = RouteLine(atReturn = listOf(a, b, c), ahead = listOf(a, b, c), aheadIsCurrent = true)
        val single = routeHomeFeatureCollections(RouteOnMap(track, arrivedAt = null)).getValue(RouteHomeIds.AHEAD_SOURCE).features()!!.single()
        assertNull(single.getStringProperty(RouteHomeIds.PART_PROPERTY))

        val arrived = RouteLine(atReturn = listOf(a, b, c, d), ahead = listOf(a, b, c, d), aheadIsCurrent = true, aheadParts = parts)
        assertEquals(0, routeHomeFeatureCollections(RouteOnMap(arrived, arrivedAt = a)).getValue(RouteHomeIds.AHEAD_SOURCE).features()!!.size)
    }

    /**
     * Step 6 PR 3 (dispatch 2026-10-26-11, N-412-1): the way to a waypoint's straight legs reach the ahead source named as
     * straight, for their own thin dashed layer, never as the walker's track, which is drawn dotted (RECORD -1589).
     */
    @Test
    fun `the way to a waypoint's straight legs are named straight, and its trail trail`() {
        val waypointParts = listOf(
            RouteLinePart(RouteLinePartKind.STRAIGHT, listOf(a, b)),
            RouteLinePart(RouteLinePartKind.TRAIL, listOf(b, c)),
            RouteLinePart(RouteLinePartKind.STRAIGHT, listOf(c, d)),
        )
        val line = RouteLine(atReturn = emptyList(), ahead = listOf(a, b, c, d), aheadIsCurrent = true, aheadParts = waypointParts)
        val features = routeHomeFeatureCollections(RouteOnMap(line, arrivedAt = null)).getValue(RouteHomeIds.AHEAD_SOURCE).features()!!
        assertEquals(listOf(RouteHomeIds.PART_STRAIGHT, RouteHomeIds.PART_TRAIL, RouteHomeIds.PART_STRAIGHT), features.map { it.getStringProperty(RouteHomeIds.PART_PROPERTY) })
    }

    /**
     * Dispatch 2026-10-26-11 Amendment 2, item 1 (RECORD -1670, the owner: "Fade and grey too (Recommended)"; Sorrel's
     * S-413-1): the way to a waypoint's line fades on a stale fix as the straight line does (the same faded opacity), and
     * once location is lost every part of it is marked grey, at full opacity, as the straight line kept then is drawn by
     * its grey layer. The way home's lines never carry the mark. How MapLibre draws the grey is device-only.
     */
    @Test
    fun `the way to a waypoint's line fades on a stale fix and is marked grey, at full opacity, once location is lost`() {
        val waypointParts = listOf(
            RouteLinePart(RouteLinePartKind.STRAIGHT, listOf(a, b)),
            RouteLinePart(RouteLinePartKind.TRAIL, listOf(b, c)),
            RouteLinePart(RouteLinePartKind.STRAIGHT, listOf(c, d)),
        )
        val lost = RouteLine(atReturn = emptyList(), ahead = listOf(a, b, c, d), aheadIsCurrent = false, aheadParts = waypointParts, aheadIsOffline = true)
        val greyed = routeHomeFeatureCollections(RouteOnMap(lost, arrivedAt = null)).getValue(RouteHomeIds.AHEAD_SOURCE).features()!!
        assertEquals("every part marked grey", listOf(true, true, true), greyed.map { it.hasNonNullValueForProperty(RouteHomeIds.OFFLINE_PROPERTY) })
        assertEquals("grey at full opacity, as the straight line's grey layer", 1f, routeAheadOpacity(RouteOnMap(lost, arrivedAt = null)))

        val stale = lost.copy(aheadIsOffline = false)
        val faded = routeHomeFeatureCollections(RouteOnMap(stale, arrivedAt = null)).getValue(RouteHomeIds.AHEAD_SOURCE).features()!!
        assertFalse("a stale fix: not grey", faded.any { it.hasProperty(RouteHomeIds.OFFLINE_PROPERTY) })
        assertEquals("a stale fix: faded, as the straight line", ROUTE_AHEAD_FADED_OPACITY, routeAheadOpacity(RouteOnMap(stale, arrivedAt = null)))

        val wayHome = RouteLine(atReturn = listOf(a, b, c, d), ahead = listOf(a, b, c, d), aheadIsCurrent = false, aheadParts = parts)
        val track = RouteLine(atReturn = listOf(a, b, c), ahead = listOf(a, b, c), aheadIsCurrent = false)
        for (line in listOf(wayHome, track)) {
            val features = routeHomeFeatureCollections(RouteOnMap(line, arrivedAt = null)).getValue(RouteHomeIds.AHEAD_SOURCE).features()!!
            assertFalse("the way home is never grey", features.any { it.hasProperty(RouteHomeIds.OFFLINE_PROPERTY) })
            assertEquals("the way home's withheld line is faded, as before", ROUTE_AHEAD_FADED_OPACITY, routeAheadOpacity(RouteOnMap(line, arrivedAt = null)))
        }
    }
}
