package com.zynergylabs.forager.app.ui.map

import com.zynergylabs.forager.app.domain.JournalEntryHighlights
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.RecordPoint
import com.zynergylabs.forager.app.ui.map.fanout.FanKey
import com.zynergylabs.forager.app.ui.map.fanout.FanMember
import com.zynergylabs.forager.app.ui.map.fanout.FanOffset
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What the fan-out pushes into the map's sources (dispatch 2026-09-28-197): the copies keep their own
 * icon, a journal halo follows a kept record, a dot keeps its selection ring, and every copy has a leg
 * to its true position. These are the pure builders; that the map then draws them, and hides the
 * originals, is device-only (a `MapView` cannot be built under Robolectric).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FanOutLayersTest {

    private fun member(layerId: String, id: String, lat: Double = 45.0, lng: Double = -122.0) =
        FanMember(FanKey(layerId, id), lat, lng, 0f, 0f, FanOffset(0f, -48f))

    private val movedTo = LatLng(45.001, -122.002)

    private fun frame(members: List<FanMember>, highlights: JournalEntryHighlights = JournalEntryHighlights.NONE, focused: Long? = null) =
        fanFrameCollections(members, { movedTo }, highlights, focused)

    @Test
    fun `a copy keeps its own icon and is drawn where it now is`() {
        val f = frame(
            listOf(
                member(MapLayerIds.FINDS, "find"),
                member(MapLayerIds.PHOTOS, "photo"),
                member(MapLayerIds.WAYPOINTS, "waypoint"),
                member(MapLayerIds.PLANNED_TRIPS, "trip"),
            ),
        )
        val byId = f.icons.features().orEmpty().associate { it.getStringProperty("featureId") to it.getStringProperty("image") }
        assertEquals(
            mapOf("find" to "find-mushroom", "photo" to "photo-square", "waypoint" to "waypoint-pin", "trip" to "planned-trip-flag"),
            byId,
        )
        f.icons.features().orEmpty().forEach {
            val at = it.geometry() as Point
            assertEquals(movedTo.lat, at.latitude(), 1e-9)
            assertEquals(movedTo.lng, at.longitude(), 1e-9)
        }
    }

    @Test
    fun `every copy has a leg from its true position to where it is`() {
        val f = frame(listOf(member(MapLayerIds.FINDS, "a", lat = 45.0, lng = -122.0), member(MapLayerIds.PHOTOS, "b", lat = 45.5, lng = -121.5)))
        val legs = f.legs.features().orEmpty().map { (it.geometry() as LineString).coordinates() }
        assertEquals(2, legs.size)
        assertEquals(listOf(Point.fromLngLat(-122.0, 45.0), Point.fromLngLat(movedTo.lng, movedTo.lat)), legs[0])
        assertEquals(listOf(Point.fromLngLat(-121.5, 45.5), Point.fromLngLat(movedTo.lng, movedTo.lat)), legs[1])
    }

    @Test
    fun `a halo goes under a copy whose record an entry keeps, and only that one`() {
        val highlights = JournalEntryHighlights.NONE.copy(
            findMarkers = listOf(RecordPoint("kept-find", LatLng(45.0, -122.0))),
            photoMarkers = listOf(RecordPoint("kept-photo", LatLng(45.0, -122.0))),
            waypointMarkers = listOf(RecordPoint("kept-waypoint", LatLng(45.0, -122.0))),
        )
        val f = frame(
            listOf(
                member(MapLayerIds.FINDS, "kept-find"),
                member(MapLayerIds.FINDS, "other-find"),
                member(MapLayerIds.PHOTOS, "kept-photo"),
                member(MapLayerIds.WAYPOINTS, "kept-waypoint"),
                member(MapLayerIds.PLANNED_TRIPS, "trip"),
            ),
            highlights,
        )
        assertEquals(
            listOf("find-journal-halo", "photo-journal-halo", "waypoint-journal-halo"),
            f.halos.features().orEmpty().map { it.getStringProperty("image") }.sorted(),
        )
        assertEquals("all five copies still have their icon", 5, f.icons.features().orEmpty().size)
    }

    @Test
    fun `with no entry shown there is no halo`() {
        assertTrue(frame(listOf(member(MapLayerIds.FINDS, "f"))).halos.features().orEmpty().isEmpty())
    }

    @Test
    fun `a sighting is a dot carrying its observation id, and the focused one is selected`() {
        val f = frame(listOf(member(MapLayerIds.SIGHTINGS, "9001"), member(MapLayerIds.SIGHTINGS, "9002")), focused = 9002L)
        val dots = f.dots.features().orEmpty()
        assertEquals(setOf(9001L, 9002L), dots.map { it.getNumberProperty("observationId").toLong() }.toSet())
        assertEquals(mapOf(9001L to false, 9002L to true), dots.associate { it.getNumberProperty("observationId").toLong() to it.getBooleanProperty("selected") })
        assertTrue("a dot is not an icon", f.icons.features().orEmpty().isEmpty())
    }

    @Test
    fun `a member whose layer has no icon of ours is left out entirely, leg included`() {
        val f = frame(listOf(member(MapLayerIds.KEPT_TRACKS, "track"), member(MapLayerIds.FINDS, "find")))
        assertEquals(1, f.icons.features().orEmpty().size)
        assertEquals("no leg to a marker that is not drawn", 1, f.legs.features().orEmpty().size)
    }

    @Test
    fun `no members is an empty frame`() {
        val f = frame(emptyList())
        assertTrue(f.legs.features().orEmpty().isEmpty() && f.icons.features().orEmpty().isEmpty() && f.halos.features().orEmpty().isEmpty() && f.dots.features().orEmpty().isEmpty())
    }

    @Test
    fun `the hiding filter names every hidden id, by the property the layer's features carry`() {
        val text = fanOutHiddenFilter("featureId", listOf("a", "b")).toString()
        assertTrue(text, "\"a\"" in text && "\"b\"" in text)
        assertTrue(text, "featureId" in text && "!=" in text && "all" in text)
        val numeric = fanOutHiddenFilter("observationId", listOf(9001L)).toString()
        assertTrue(numeric, "observationId" in numeric && "9001" in numeric && "\"9001\"" !in numeric)
    }

    @Test
    fun `no hidden ids is a filter that shows everything`() {
        val text = fanOutHiddenFilter("featureId", emptyList()).toString()
        assertEquals("[\"all\"]", text)
        assertFalse("!=" in text)
    }
}
