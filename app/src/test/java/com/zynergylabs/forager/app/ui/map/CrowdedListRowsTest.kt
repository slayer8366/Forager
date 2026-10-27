package com.zynergylabs.forager.app.ui.map

import com.zynergylabs.forager.app.domain.model.GalleryPhoto
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.LogPhoto
import com.zynergylabs.forager.app.domain.model.MushroomLogEntry
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.ui.map.fanout.FanKey
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The crowded list's rows (dispatch 2026-10-27-07, step 4; the owner: "Icon, name, date (Recommended)"): each row's icon,
 * name and date, read from the records the bubbles read, newest first; a record that is gone gets no row and is reported.
 */
class CrowdedListRowsTest {

    private val zone = ZoneOffset.UTC
    private val find = MushroomLogEntry.draft(id = "find-1", location = LatLng(45.0, -122.0), date = LocalDate.of(2026, 10, 7))
        .copy(isDraft = false, ownIdentification = "Golden chanterelle")
    private val photo = GalleryPhoto(LogPhoto("ph-1", "photos/ph-1.jpg", LocalDate.of(2026, 10, 9).atStartOfDay(zone).toInstant().toEpochMilli(), 45.0, -122.0), emptyList())
    private val undatedPhoto = GalleryPhoto(LogPhoto("ph-2", "photos/ph-2.jpg", null, 45.0, -122.0), emptyList())
    private val waypoint = Waypoint("wp-1", 45.0, -122.0, null, "Creek pin", "", LocalDate.of(2026, 10, 1).atStartOfDay(zone).toInstant().toEpochMilli())
    private val blankWaypoint = waypoint.copy(id = "wp-2", name = " ")
    private val trip = PlannedTrip("trip-1", "Saddle loop", LatLng(45.0, -122.0), LocalDate.of(2026, 10, 12))
    private val sources = MapRecordSources(
        finds = listOf(find),
        galleryPhotos = listOf(photo, undatedPhoto),
        waypoints = listOf(waypoint, blankWaypoint),
        plannedTrips = listOf(trip),
    )

    @Test
    fun `each kind's row has its map icon, its name and its date, newest first and the undated last`() {
        val keys = listOf(
            FanKey(MapLayerIds.FINDS, "find-1"),
            FanKey(MapLayerIds.PHOTOS, "ph-1"),
            FanKey(MapLayerIds.PHOTOS, "ph-2"),
            FanKey(MapLayerIds.WAYPOINTS, "wp-1"),
            FanKey(MapLayerIds.PLANNED_TRIPS, "trip-1"),
        )
        val rows = crowdedListRows(keys, sources, is24HourClock = false, zone = zone)

        assertEquals(
            listOf(
                Triple(MarkerIcon.PLANNED_TRIP, "Saddle loop", "Oct 12, 2026"),
                Triple(MarkerIcon.PHOTO, CROWDED_ROW_PHOTO_NAME, "Oct 9, 2026"),
                Triple(MarkerIcon.FIND, "Golden chanterelle", "Oct 7, 2026"),
                Triple(MarkerIcon.WAYPOINT, "Creek pin", "Oct 1, 2026"),
                Triple(MarkerIcon.PHOTO, CROWDED_ROW_PHOTO_NAME, null),
            ),
            rows.map { Triple(it.icon, it.name, it.date) },
        )
        assertEquals(FanKey(MapLayerIds.PLANNED_TRIPS, "trip-1"), rows.first().key)
    }

    @Test
    fun `a waypoint with a blank name, and a find with no name or time, still read as what they are`() {
        val unnamed = find.copy(id = "find-2", ownIdentification = null)
        val rows = crowdedListRows(
            listOf(FanKey(MapLayerIds.WAYPOINTS, "wp-2"), FanKey(MapLayerIds.FINDS, "find-2")),
            sources.copy(finds = listOf(unnamed)),
            is24HourClock = false,
            zone = zone,
        )
        assertEquals(setOf(CROWDED_ROW_WAYPOINT_FALLBACK, CROWDED_ROW_FIND_FALLBACK), rows.map { it.name }.toSet())
    }

    @Test
    fun `a record that is gone has no row, and is reported`() {
        val warnings = mutableListOf<String>()
        val rows = crowdedListRows(
            listOf(FanKey(MapLayerIds.FINDS, "find-1"), FanKey(MapLayerIds.FINDS, "gone")),
            sources,
            is24HourClock = false,
            zone = zone,
            warn = { warnings += it },
        )
        assertEquals(listOf("find-1"), rows.map { it.key.featureId })
        assertTrue(warnings.single(), warnings.single().contains("/gone"))
    }
}
