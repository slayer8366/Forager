package com.zynergylabs.forager.app.ui.map

import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.route.RouteLeg
import com.zynergylabs.forager.app.domain.route.StraightReason
import com.zynergylabs.forager.app.ui.theme.MapPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.maplibre.geojson.Point
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Dispatch 2026-11-06-05, Amendment 2, item 4 (RECORD -2187): what the map's selected-stop source is fed, headless. One point, where
 * the selected stop's waypoint (or trip) stands, naming the accent pin (or flag) it is drawn with; nothing for none. The accent itself
 * is the dimmer cream #E3D3B5 by day (Amendment 6, "a dimmer cream by day"; #EDE3D0 before) and rust #A2612C at night, as hex, so another
 * colour fails (the pixels are `MarkerGlyphsTest`'s).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlottedRouteSelectedStopTest {

    private val a = LatLng(45.20, -122.50)
    private val b = LatLng(45.23, -122.53)
    private val legs = listOf(RouteLeg.Straight(listOf(a, b), 100.0, StraightReason.NO_REGION_COVERS))

    private fun selectedFeatures(route: PlottedRouteOnMap?) = plottedRouteFeatureCollections(route).getValue(PlottedRouteIds.SELECTED_SOURCE).features().orEmpty()

    @Test
    fun `a selected waypoint is one point at its spot, drawn with the accent pin`() {
        val features = selectedFeatures(PlottedRouteOnMap(legs, listOf(a, b), selected = SelectedStopMarker(b, trip = false, featureId = "wp-b")))

        assertEquals(1, features.size)
        val point = features.single().geometry() as Point
        assertEquals(b, LatLng(point.latitude(), point.longitude()))
        assertEquals("waypoint-pin-route-selected", features.single().getStringProperty(PlottedRouteIds.SELECTED_ICON_PROPERTY))
        assertEquals(MarkerIcon.WAYPOINT_ROUTE_SELECTED.imageId, features.single().getStringProperty(PlottedRouteIds.SELECTED_ICON_PROPERTY))
    }

    @Test
    fun `a selected trip is drawn with the accent flag`() {
        val features = selectedFeatures(PlottedRouteOnMap(legs, listOf(a, b), selected = SelectedStopMarker(a, trip = true, featureId = "trip-a")))

        assertEquals(MarkerIcon.PLANNED_TRIP_ROUTE_SELECTED.imageId, features.single().getStringProperty(PlottedRouteIds.SELECTED_ICON_PROPERTY))
    }

    @Test
    fun `no selection, or no route, draws nothing`() {
        assertEquals(0, selectedFeatures(PlottedRouteOnMap(legs, listOf(a, b))).size)
        assertEquals(0, selectedFeatures(null).size)
    }

    @Test
    fun `the accent is cream by day and rust red at night`() {
        assertEquals("the day accent is the dimmer cream", 0xFFE3D3B5.toInt(), routeStopSelectedColour(MapPalette.DAY))
        assertEquals("the night accent is rust, unchanged", 0xFFA2612C.toInt(), routeStopSelectedColour(MapPalette.NIGHT))
        assertNotEquals("the day accent differs from the plain pin", MapPalette.DAY.waypoint, routeStopSelectedColour(MapPalette.DAY))
        assertNotEquals("the day accent differs from the plain flag", MapPalette.DAY.plannedTrip, routeStopSelectedColour(MapPalette.DAY))
    }
}
