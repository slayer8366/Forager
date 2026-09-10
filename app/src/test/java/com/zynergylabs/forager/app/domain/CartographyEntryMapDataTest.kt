package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Region
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [CartographyEntryMapData]'s own emptiness and framing rules — plate-pulse follow-up, owner
 * rulings on items 4 and 6. The property is tested directly, on hand-built values, because the
 * pulse found the report screen surviving an empty polyline only through a *second* guard
 * (`boundingRegion` returning null) while [CartographyEntryMapData.isEmpty] itself said there was
 * content. A test of the screen would pass either way; only a test of the property catches the
 * property being wrong.
 */
class CartographyEntryMapDataTest {

    private val region = Region(lat = 45.6, lng = -122.6, radiusKm = 10)

    private fun data(
        trackPolylines: List<List<LatLng>> = emptyList(),
        findMarkers: List<LatLng> = emptyList(),
        waypointMarkers: List<LatLng> = emptyList(),
        photoMarkers: List<LatLng> = emptyList(),
        offlineRegionCircles: List<Region> = emptyList(),
    ) = CartographyEntryMapData(trackPolylines, findMarkers, waypointMarkers, photoMarkers, offlineRegionCircles)

    @Test
    fun `a polyline with no points is not content`() {
        // The shape a kept track takes once every stored point was excluded at the read seam.
        val data = data(trackPolylines = listOf(emptyList()))

        assertTrue("an empty polyline must not count as something to draw", data.isEmpty)
    }

    @Test
    fun `a kept offline region alone is not georeferenced content`() {
        val data = data(offlineRegionCircles = listOf(region))

        assertTrue("a green circle with nothing in it is not a day (owner ruling, item 4)", data.isEmpty)
    }

    @Test
    fun `one resolved point of any kind is content`() {
        assertFalse(data(trackPolylines = listOf(listOf(LatLng(45.5, -122.5)))).isEmpty)
        assertFalse(data(findMarkers = listOf(LatLng(45.5, -122.5))).isEmpty)
        assertFalse(data(waypointMarkers = listOf(LatLng(45.5, -122.5))).isEmpty)
        assertFalse(data(photoMarkers = listOf(LatLng(45.5, -122.5))).isEmpty)
    }

    @Test
    fun `drawable points exclude region centres, while the camera's own point set still includes them`() {
        val waypoint = LatLng(45.5, -122.5)
        val data = data(waypointMarkers = listOf(waypoint), offlineRegionCircles = listOf(region))

        assertEquals(listOf(waypoint), data.drawablePoints)
        assertEquals(listOf(waypoint, LatLng(region.lat, region.lng)), data.allPoints)
    }
}
