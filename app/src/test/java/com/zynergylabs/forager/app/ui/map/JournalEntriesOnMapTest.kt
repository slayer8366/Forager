package com.zynergylabs.forager.app.ui.map

import com.zynergylabs.forager.app.domain.HighlightedRecord
import com.zynergylabs.forager.app.domain.HighlightedRecordKind
import com.zynergylabs.forager.app.domain.JournalEntryHighlights
import com.zynergylabs.forager.app.domain.JournalEntryOnMap
import com.zynergylabs.forager.app.domain.MapLayerPreferences
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.model.GalleryPhoto
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.LogPhoto
import com.zynergylabs.forager.app.domain.model.MushroomLogEntry
import com.zynergylabs.forager.app.domain.model.RecordPoint
import com.zynergylabs.forager.app.domain.model.RecordPolyline
import com.zynergylabs.forager.app.domain.model.RecordRegion
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.ui.map.layers.JOURNAL_ENTRIES_SWITCH_LAYER_ID
import com.zynergylabs.forager.app.ui.map.layers.LayerState
import com.zynergylabs.forager.app.ui.map.layers.MAP_LAYER_REGISTRY
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.map.layers.MapSourceIds
import com.zynergylabs.forager.app.ui.map.layers.MapLayersState
import com.zynergylabs.forager.app.ui.map.layers.layerPaintFor
import com.zynergylabs.forager.app.ui.map.layers.restoreMapLayersState
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import com.zynergylabs.forager.app.ui.format.displayDate

/**
 * J8's headless half on the UI side (`prompts/preserved/2026-09-28-52.md`, J8-3 and J8-4, with the
 * owner's Q2 ruling and the planner's Q3 in `2026-09-28-53`): the chip's text, a highlighted record's
 * bubble lines naming the entries that keep it, and the Layers sheet's "Journal entries" switch. The
 * composables that show these are exercised through the real screens in `JournalEntriesOnMapScreenTest`.
 */
class JournalEntriesOnMapTest {

    private fun onMap(id: String, day: Int) = JournalEntryOnMap(id, LocalDate.of(2026, 9, day))

    // ── The chip's content ──

    @Test
    fun `the chip counts the shown entries, singular for one`() {
        assertEquals("1 journal entry on map", journalEntriesChipLabel(1))
        assertEquals("2 journal entries on map", journalEntriesChipLabel(2))
        assertEquals("12 journal entries on map", journalEntriesChipLabel(12))
    }

    @Test
    fun `an entry is named by the report header's date form`() {
        assertEquals("Sep 12, 2026", journalEntryDateLabel(LocalDate.of(2026, 9, 12)))
    }

    // ── The bubble's entry lines ──

    @Test
    fun `up to three keeping entries are each a date line labelled Open entry and the date`() {
        assertNull(keptInEntriesLines(emptyList()))
        assertEquals(
            KeptInEntriesLines.Dates(listOf(EntryLine("a", "Sep 12, 2026", "Open entry Sep 12, 2026"))),
            keptInEntriesLines(listOf(onMap("a", 12))),
        )
        assertEquals(
            KeptInEntriesLines.Dates(
                listOf(
                    EntryLine("a", "Sep 12, 2026", "Open entry Sep 12, 2026"),
                    EntryLine("b", "Sep 5, 2026", "Open entry Sep 5, 2026"),
                    EntryLine("c", "Aug 30, 2026", "Open entry Aug 30, 2026"),
                ),
            ),
            keptInEntriesLines(listOf(onMap("a", 12), onMap("b", 5), JournalEntryOnMap("c", LocalDate.of(2026, 8, 30)))),
        )
    }

    @Test
    fun `more than three keeping entries are one line, Kept in N journal entries, over a list of their dates`() {
        val four = listOf(onMap("a", 12), onMap("b", 5), onMap("c", 3), onMap("d", 1))
        assertEquals(
            KeptInEntriesLines.Count(
                "In 4 journal entries",
                four.map { EntryLine(it.entryId, displayDate(it.date), "Open entry ${displayDate(it.date)}") },
            ),
            keptInEntriesLines(four),
        )
    }

    private val find = MushroomLogEntry.draft(id = "find-1", location = LatLng(45.0, -122.0), date = LocalDate.of(2026, 9, 12)).copy(isDraft = false)
    private val photo = GalleryPhoto(photo = LogPhoto(id = "photo-1", relativePath = "p.jpg", createdAtEpochMillis = 1L), referencingEntryIds = emptyList())
    private val waypoint = Waypoint(id = "waypoint-1", lat = 45.0, lng = -122.0, altitude = null, name = "Trailhead", note = "", createdAtEpochMillis = 0L)
    private val track = Track(id = "track-1", name = "Loop", startedAtEpochMillis = 0L, endedAtEpochMillis = 1L, points = emptyList())
    private val region = OfflineRegionSummary(id = 7L, name = "Ridge", region = Region(45.0, -122.0, 10), minZoom = 10.0, maxZoom = 15.0, tileCount = 0, sizeBytes = 0L, createdAtEpochMillis = 0L)

    private val keepers = listOf(onMap("a", 12), onMap("b", 5))
    private val sources = MapRecordSources(
        finds = listOf(find, find.copy(id = "find-2")),
        galleryPhotos = listOf(photo, photo.copy(photo = photo.photo.copy(id = "photo-2"))),
        waypoints = listOf(waypoint, waypoint.copy(id = "waypoint-2")),
        tracks = listOf(track, track.copy(id = "track-2")),
        offlineRegions = listOf(region, region.copy(id = 8L)),
        journalEntriesKeeping = mapOf(
            HighlightedRecord(HighlightedRecordKind.FIND, "find-1") to keepers,
            HighlightedRecord(HighlightedRecordKind.PHOTO, "photo-1") to keepers,
            HighlightedRecord(HighlightedRecordKind.WAYPOINT, "waypoint-1") to keepers,
            HighlightedRecord(HighlightedRecordKind.TRACK, "track-1") to keepers,
            HighlightedRecord(HighlightedRecordKind.OFFLINE_REGION, "7") to keepers,
        ),
    )

    private fun keptInOf(kind: MapBubbleKind, layerId: String, id: String): List<JournalEntryOnMap>? =
        when (val content = mapBubbleContentFor(MapBubbleTarget.FeatureTarget(kind, layerId, id, LatLng(45.0, -122.0)), sources, is24HourClock = false)) {
            is MapBubbleContent.Find -> content.keptIn
            is MapBubbleContent.Photo -> content.keptIn
            is MapBubbleContent.WaypointContent -> content.keptIn
            is MapBubbleContent.TrackContent -> content.keptIn
            is MapBubbleContent.Region -> content.keptIn
            else -> null
        }

    @Test
    fun `a highlighted record's bubble carries the entries that keep it, for each of the five kinds, and no other record's does`() {
        val kinds = listOf(
            Triple(MapBubbleKind.FIND, MapLayerIds.FINDS, "find" to "find"),
            Triple(MapBubbleKind.PHOTO, MapLayerIds.PHOTOS, "photo" to "photo"),
            Triple(MapBubbleKind.WAYPOINT, MapLayerIds.WAYPOINTS, "waypoint" to "waypoint"),
            Triple(MapBubbleKind.TRACK, MapLayerIds.KEPT_TRACKS, "track" to "track"),
        )
        kinds.forEach { (kind, layer, prefix) ->
            assertEquals("$kind 1 is kept", keepers, keptInOf(kind, layer, "${prefix.first}-1"))
            assertEquals("$kind 2 is not", emptyList<JournalEntryOnMap>(), keptInOf(kind, layer, "${prefix.second}-2"))
        }
        assertEquals(keepers, keptInOf(MapBubbleKind.OFFLINE_REGION, MapLayerIds.OFFLINE_REGION_OUTLINE, "7"))
        assertEquals(emptyList<JournalEntryOnMap>(), keptInOf(MapBubbleKind.OFFLINE_REGION, MapLayerIds.OFFLINE_REGION_OUTLINE, "8"))
    }

    // ── The Layers switch ──

    @Test
    fun `the Maps tab's Layers sheet lists Journal entries last, and the entry map's does not`() {
        assertEquals(MapOverlayOption(JOURNAL_ENTRIES_SWITCH_LAYER_ID, "Journal entries"), MAPS_TAB_OVERLAYS.last())
        assertEquals(8, MAPS_TAB_OVERLAYS.size)
        assertEquals(emptyList<MapOverlayOption>(), ENTRY_MAP_OVERLAYS.filter { it.layerId == JOURNAL_ENTRIES_SWITCH_LAYER_ID })
    }

    @Test
    fun `a stored Journal entries off is restored like the other overlay switches`() {
        val restored = restoreMapLayersState(MapLayerPreferences.NONE.copy(visibility = mapOf(JOURNAL_ENTRIES_SWITCH_LAYER_ID to false)), MAP_LAYER_REGISTRY)
        assertEquals(emptyList<String>(), restored.rejected)
        assertEquals(false, restored.state.stateOf(JOURNAL_ENTRIES_SWITCH_LAYER_ID).visible)
    }

    private val halos = mapOf(
        MapLayerIds.JOURNAL_ENTRY_REGIONS to MapLayerIds.OFFLINE_REGION_FILL,
        MapLayerIds.JOURNAL_ENTRY_TRACKS to MapLayerIds.KEPT_TRACKS,
        MapLayerIds.JOURNAL_ENTRY_WAYPOINTS to MapLayerIds.WAYPOINTS,
        MapLayerIds.JOURNAL_ENTRY_FINDS to MapLayerIds.FINDS,
        MapLayerIds.JOURNAL_ENTRY_PHOTOS to MapLayerIds.PHOTOS,
    )

    private fun visible(layerId: String, state: MapLayersState): Boolean {
        val spec = MAP_LAYER_REGISTRY.singleOrNull { it.id == layerId }
        assertNotNull("no registry layer $layerId", spec)
        return layerPaintFor(spec!!, state).visible
    }

    @Test
    fun `the Journal entries switch hides every halo together, and shows them all by default`() {
        halos.keys.forEach { assertEquals("$it shown by default", true, visible(it, MapLayersState.DEFAULT)) }
        val off = MapLayersState(layers = mapOf(JOURNAL_ENTRIES_SWITCH_LAYER_ID to LayerState(visible = false)))
        halos.keys.forEach { assertEquals("$it hidden with the switch off", false, visible(it, off)) }
        // The records themselves stay drawn: the switch hides highlights, not records.
        halos.values.forEach { assertEquals("$it still drawn", true, visible(it, off)) }
    }

    @Test
    fun `a record switched off takes its own halo with it and no other`() {
        halos.forEach { (halo, record) ->
            val recordOff = MapLayersState(layers = mapOf(record to LayerState(visible = false)))
            assertEquals("$halo hidden with $record off", false, visible(halo, recordOff))
            (halos.keys - halo).forEach { other -> assertEquals("$other unaffected by $record", true, visible(other, recordOff)) }
        }
    }

    // ── What reaches the map (J8-2) ──

    @Test
    fun `each halo source receives the highlighted records of its kind, each feature carrying its record's id`() {
        val highlights = JournalEntryHighlights(
            shownEntries = listOf(onMap("a", 12)),
            trackPolylines = listOf(RecordPolyline("track-1", listOf(LatLng(45.0, -122.0), LatLng(45.1, -122.1)))),
            findMarkers = listOf(RecordPoint("find-1", LatLng(45.2, -122.2))),
            photoMarkers = listOf(RecordPoint("photo-1", LatLng(45.3, -122.3))),
            waypointMarkers = listOf(RecordPoint("waypoint-1", LatLng(45.4, -122.4))),
            offlineRegionCircles = listOf(RecordRegion("7", Region(45.5, -122.5, 10))),
            keptIn = emptyMap(),
        )
        val ids = journalHighlightFeatureCollections(highlights).mapValues { (_, collection) ->
            collection.features()!!.map { it.getStringProperty(FEATURE_ID_PROPERTY) }
        }
        assertEquals(
            mapOf(
                MapSourceIds.JOURNAL_ENTRY_REGIONS to listOf("7"),
                MapSourceIds.JOURNAL_ENTRY_TRACKS to listOf("track-1"),
                MapSourceIds.JOURNAL_ENTRY_WAYPOINTS to listOf("waypoint-1"),
                MapSourceIds.JOURNAL_ENTRY_FINDS to listOf("find-1"),
                MapSourceIds.JOURNAL_ENTRY_PHOTOS to listOf("photo-1"),
            ),
            ids,
        )
        // Every halo layer's source is one of these, so no halo layer is left without data.
        assertEquals(
            MAP_LAYER_REGISTRY.filter { it.drawnWith != null }.map { it.sourceId }.toSet(),
            journalHighlightFeatureCollections(JournalEntryHighlights.NONE).keys,
        )
    }

    @Test
    fun `each line halo is the halo width wider on each side than what it lies under, and thins with the zoom as the track does`() {
        val track = lineSpecForLayer(MapLayerIds.KEPT_TRACKS)!!
        val trackCasing = lineSpecForLayer(MapLayerIds.KEPT_TRACKS_CASING)!!
        val trackHalo = lineSpecForLayer(MapLayerIds.JOURNAL_ENTRY_TRACKS)!!
        val outline = lineSpecForLayer(MapLayerIds.OFFLINE_REGION_OUTLINE)!!
        val regionHalo = lineSpecForLayer(MapLayerIds.JOURNAL_ENTRY_REGIONS)!!
        for (zoom in listOf(8f, 11f, 13f, 15f, 18f)) {
            assertEquals("track halo at $zoom", lineWidthAtZoom(trackCasing, zoom) + 2 * JOURNAL_HALO_WIDTH_DP * lineWidthAtZoom(track, zoom) / track.widthDp, lineWidthAtZoom(trackHalo, zoom), 1e-4f)
        }
        assertEquals(outline.widthDp + 2 * JOURNAL_HALO_WIDTH_DP, regionHalo.widthDp, 1e-4f)
        assertEquals("solid", null, regionHalo.dashPattern)
        assertEquals("solid", null, trackHalo.dashPattern)
    }
}
