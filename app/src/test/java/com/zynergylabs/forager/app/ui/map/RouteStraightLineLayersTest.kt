package com.zynergylabs.forager.app.ui.map

import com.zynergylabs.forager.app.domain.RouteLine
import com.zynergylabs.forager.app.domain.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Test
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString

/**
 * Dispatch 2026-09-28-502, step 3: navigating to a waypoint, the map's straight source gets the line from
 * the walker to the waypoint, and only that. The return's sources stay empty, as the return is not drawn
 * while a waypoint overrules it. How MapLibre draws it (thin, dashed) is device-only.
 */
class RouteStraightLineLayersTest {

    private val walker = LatLng(45.32, -122.634)
    private val waypoint = LatLng(45.326, -122.634)

    @Test
    fun `the straight line goes to its own source, from the walker to the waypoint`() {
        val sources = routeHomeFeatureCollections(RouteOnMap(line = null, arrivedAt = null, straight = listOf(walker, waypoint)))
        assertEquals(listOf(walker, waypoint), lineOf(sources.getValue(RouteHomeIds.STRAIGHT_SOURCE)))
        assertEquals("no way back is drawn", 0, sources.getValue(RouteHomeIds.PASSED_SOURCE).features()!!.size)
        assertEquals(0, sources.getValue(RouteHomeIds.AHEAD_SOURCE).features()!!.size)
    }

    @Test
    fun `no straight line, and no route at all, leave its source empty`() {
        val line = RouteLine(listOf(walker, waypoint), listOf(walker, waypoint), aheadIsCurrent = true)
        assertEquals(0, routeHomeFeatureCollections(RouteOnMap(line, arrivedAt = null)).getValue(RouteHomeIds.STRAIGHT_SOURCE).features()!!.size)
        assertEquals(0, routeHomeFeatureCollections(null).getValue(RouteHomeIds.STRAIGHT_SOURCE).features()!!.size)
    }

    @Test
    fun `the line is full while current and faded, as the withheld way back is, once kept after the fix is lost`() {
        assertEquals(1f, routeStraightOpacity(RouteOnMap(line = null, arrivedAt = null, straight = listOf(walker, waypoint), straightIsCurrent = true)))
        val kept = RouteOnMap(line = null, arrivedAt = null, straight = listOf(walker, waypoint), straightIsCurrent = false)
        assertEquals(ROUTE_AHEAD_FADED_OPACITY, routeStraightOpacity(kept))
        assertEquals("the kept line is still drawn", listOf(walker, waypoint), lineOf(routeHomeFeatureCollections(kept).getValue(RouteHomeIds.STRAIGHT_SOURCE)))
    }

    @Test
    fun `kept after location is lost, the line goes to the grey offline source, not the blue one`() {
        val offline = RouteOnMap(line = null, arrivedAt = null, straight = listOf(walker, waypoint), straightIsCurrent = false, straightOffline = true)
        val sources = routeHomeFeatureCollections(offline)
        assertEquals(listOf(walker, waypoint), lineOf(sources.getValue(RouteHomeIds.STRAIGHT_OFFLINE_SOURCE)))
        assertEquals("not drawn blue", 0, sources.getValue(RouteHomeIds.STRAIGHT_SOURCE).features()!!.size)
        val stale = offline.copy(straightOffline = false)
        assertEquals("a stale line stays blue, faded", 0, routeHomeFeatureCollections(stale).getValue(RouteHomeIds.STRAIGHT_OFFLINE_SOURCE).features()!!.size)
    }

    @Test
    fun `once arrived at the waypoint its ring is drawn there`() {
        val sources = routeHomeFeatureCollections(RouteOnMap(line = null, arrivedAt = waypoint, straight = null))
        assertEquals(1, sources.getValue(RouteHomeIds.ARRIVED_SOURCE).features()!!.size)
        assertEquals(0, sources.getValue(RouteHomeIds.STRAIGHT_SOURCE).features()!!.size)
    }

    private fun lineOf(collection: FeatureCollection): List<LatLng> =
        (collection.features()!!.single().geometry() as LineString).coordinates().map { LatLng(it.latitude(), it.longitude()) }
}
