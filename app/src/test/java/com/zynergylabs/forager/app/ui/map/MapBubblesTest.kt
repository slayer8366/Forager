package com.zynergylabs.forager.app.ui.map

import androidx.compose.ui.geometry.Offset
import com.zynergylabs.forager.app.domain.ComputeTrackStatisticsUseCase
import com.zynergylabs.forager.app.domain.ForecastAvailability
import com.zynergylabs.forager.app.domain.ForecastBlock
import com.zynergylabs.forager.app.domain.ForecastCell
import com.zynergylabs.forager.app.domain.ForecastCellStore
import com.zynergylabs.forager.app.domain.ForecastCellsResult
import com.zynergylabs.forager.app.domain.ForecastDriver
import com.zynergylabs.forager.app.domain.MgrsConverter
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.GalleryPhoto
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.LogPhoto
import com.zynergylabs.forager.app.domain.model.MgrsCoordinate
import com.zynergylabs.forager.app.domain.model.MushroomLogEntry
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.domain.model.RecordPoint
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.Sighting
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackDecision
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.formatDistanceMeters
import com.zynergylabs.forager.app.ui.log.formatTrackDuration
import com.zynergylabs.forager.app.ui.map.layers.LEGEND_REFERENCE_CLASS
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.track.formatRecordTimestamp
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M1's plain-data half, headless (dispatch `2026-09-28-29`, B2 and B3, with continuation
 * `2026-09-28-30`): which layers take a bubble, the one tapped thing, re-anchoring for point
 * features, each kind's content from the lists a host holds, the forecast cell re-read from the store
 * by group, week and block, and the bubble's placement with its tail on the anchor.
 */
class MapBubblesTest {

    private val day = LocalDate.of(2026, 9, 12)

    private val find = MushroomLogEntry.draft(id = "find-1", location = LatLng(45.51, -122.61), date = day)
        .copy(isDraft = false, ownIdentification = "Chanterelle", photos = listOf(LogPhoto("ph-1", "photos/ph-1.jpg", 1_000L)))
    private val unnamedFind = MushroomLogEntry.draft(id = "find-2", location = LatLng(45.52, -122.62), date = day).copy(isDraft = false)
    private val photo = GalleryPhoto(LogPhoto("ph-1", "photos/ph-1.jpg", 1_700_000_000_000L, 45.53, -122.63), referencingEntryIds = listOf("find-1"))
    private val loosePhoto = GalleryPhoto(LogPhoto("ph-2", "photos/ph-2.jpg", null, 45.54, -122.64), referencingEntryIds = emptyList())
    private val waypoint = Waypoint("wp-1", 45.55, -122.65, null, "Creek pin", "", 1_000L)
    private val trip = PlannedTrip("trip-1", "Saddle loop", LatLng(45.56, -122.66), LocalDate.of(2026, 10, 3))
    private val track = Track(
        id = "trk-1",
        name = "Morning loop",
        startedAtEpochMillis = 1_700_000_000_000L,
        endedAtEpochMillis = 1_700_003_600_000L,
        points = listOf(
            TrackPoint(45.50, -122.60, null, 5f, 1_700_000_000_000L),
            TrackPoint(45.51, -122.60, null, 5f, 1_700_003_600_000L),
        ),
    )
    private val now = 1_800_000_000_000L
    private val freshRegion = OfflineRegionSummary(7L, "Forest Park", Region(45.57, -122.67, 5), 0.0, 15.0, 1200, 12_300_000L, now - TimeUnit.DAYS.toMillis(3))
    private val staleRegion = freshRegion.copy(id = 8L, name = "Old coast", createdAtEpochMillis = now - TimeUnit.DAYS.toMillis(90))

    private val sources = MapRecordSources(
        finds = listOf(find, unnamedFind),
        galleryPhotos = listOf(photo, loosePhoto),
        photoEntryReferenceCounts = mapOf("ph-1" to 2),
        waypoints = listOf(waypoint),
        tracks = listOf(track),
        plannedTrips = listOf(trip),
        offlineRegions = listOf(freshRegion, staleRegion),
        distanceUnit = DistanceUnit.KILOMETERS,
        staleThresholdDays = 60,
        nowEpochMillis = { now },
    )

    private fun target(kind: MapBubbleKind, layerId: String, id: String) =
        MapBubbleTarget.FeatureTarget(kind, layerId, id, LatLng(45.5, -122.6))

    // Which layers take a bubble.

    @Test
    fun `every record layer and both colour fields name a bubble kind, and nothing else does`() {
        assertEquals(MapBubbleKind.FIND, mapBubbleKindOf(MapLayerIds.FINDS))
        assertEquals(MapBubbleKind.PHOTO, mapBubbleKindOf(MapLayerIds.PHOTOS))
        assertEquals(MapBubbleKind.WAYPOINT, mapBubbleKindOf(MapLayerIds.WAYPOINTS))
        assertEquals(MapBubbleKind.PLANNED_TRIP, mapBubbleKindOf(MapLayerIds.PLANNED_TRIPS))
        assertEquals(MapBubbleKind.TRACK, mapBubbleKindOf(MapLayerIds.KEPT_TRACKS))
        assertEquals(MapBubbleKind.OFFLINE_REGION, mapBubbleKindOf(MapLayerIds.OFFLINE_REGION_OUTLINE))
        assertEquals(MapBubbleKind.FORECAST_CELL, mapBubbleKindOf(MapLayerIds.FORECAST_CHANTERELLES))
        assertEquals(MapBubbleKind.FORECAST_CELL, mapBubbleKindOf(MapLayerIds.FORECAST_CHICKEN_OF_THE_WOODS))
        // The sighting dots (their own bubble), the non-records and the casings name none.
        listOf(
            MapLayerIds.SIGHTINGS,
            MapLayerIds.SEARCH_CENTRE,
            MapLayerIds.BREADCRUMB,
            MapLayerIds.BREADCRUMB_CASING,
            MapLayerIds.KEPT_TRACKS_CASING,
            MapLayerIds.OFFLINE_REGION_FILL,
            "not-a-layer",
        ).forEach { assertNull(it, mapBubbleKindOf(it)) }
    }

    // The one tapped thing.

    @Test
    fun `a feature tap becomes the tapped thing, anchored where it was tapped`() {
        val tap = MapFeatureTap(MapLayerIds.WAYPOINTS, "wp-1", Offset(120f, 340f), 30f, LatLng(45.55, -122.65))
        assertEquals(
            TappedMapThing(target(MapBubbleKind.WAYPOINT, MapLayerIds.WAYPOINTS, "wp-1").copy(at = LatLng(45.55, -122.65)), Offset(120f, 340f), 30f),
            tappedThingOf(tap),
        )
        assertNull(tappedThingOf(tap.copy(layerId = MapLayerIds.SEARCH_CENTRE)))
    }

    @Test
    fun `only a point feature is focused for re-anchoring, and only a sighting sets the focused observation`() {
        val point = TappedMapThing(target(MapBubbleKind.FIND, MapLayerIds.FINDS, "find-1"), Offset.Zero, 0f)
        val line = TappedMapThing(target(MapBubbleKind.TRACK, MapLayerIds.KEPT_TRACKS, "trk-1"), Offset.Zero, 0f)
        val cell = TappedMapThing(target(MapBubbleKind.FORECAST_CELL, MapLayerIds.FORECAST_CHANTERELLES, "45.5,-122.6"), Offset.Zero, 0f)
        val sighting = TappedMapThing(MapBubbleTarget.SightingTarget(SIGHTING), Offset.Zero, 0f)

        assertEquals(FocusedMapFeature(MapLayerIds.FINDS, "find-1"), point.focusedFeature)
        assertNull(line.focusedFeature)
        assertNull(cell.focusedFeature)
        assertNull(sighting.focusedFeature)
        assertNull((null as TappedMapThing?).focusedFeature)
        assertEquals(42L, sighting.focusedObservationId)
        assertNull(point.focusedObservationId)
    }

    @Test
    fun `a focused point is re-found in the lists the map draws, and a record no longer drawn is not`() {
        val trips = listOf(trip)
        val waypoints = listOf(waypoint)
        val finds = listOf(RecordPoint("find-1", LatLng(45.51, -122.61)))
        val photos = listOf(RecordPoint("ph-1", LatLng(45.53, -122.63)))
        fun at(layer: String, id: String) = focusedFeaturePosition(FocusedMapFeature(layer, id), trips, waypoints, finds, photos)

        assertEquals(LatLng(45.56, -122.66), at(MapLayerIds.PLANNED_TRIPS, "trip-1"))
        assertEquals(LatLng(45.55, -122.65), at(MapLayerIds.WAYPOINTS, "wp-1"))
        assertEquals(LatLng(45.51, -122.61), at(MapLayerIds.FINDS, "find-1"))
        assertEquals(LatLng(45.53, -122.63), at(MapLayerIds.PHOTOS, "ph-1"))
        assertNull(at(MapLayerIds.FINDS, "deleted"))
        assertNull(at(MapLayerIds.KEPT_TRACKS, "trk-1"))
    }

    // Content per kind (B3).

    @Test
    fun `a find shows its identification, its date and its cover photo, an unnamed one its date as the title, and a record gone from its list nothing`() {
        assertEquals(
            MapBubbleContent.Find("find-1", "Chanterelle", "Find on 2026-09-12", "photos/ph-1.jpg"),
            mapBubbleContentFor(target(MapBubbleKind.FIND, MapLayerIds.FINDS, "find-1"), sources),
        )
        assertEquals(
            MapBubbleContent.Find("find-2", "Find on 2026-09-12", null, null),
            mapBubbleContentFor(target(MapBubbleKind.FIND, MapLayerIds.FINDS, "find-2"), sources),
        )
        // A record no longer in its list (a delete landed, a list reloaded) has no bubble.
        assertNull(mapBubbleContentFor(target(MapBubbleKind.FIND, MapLayerIds.FINDS, "gone"), sources))
        assertNull(mapBubbleContentFor(target(MapBubbleKind.WAYPOINT, MapLayerIds.WAYPOINTS, "gone"), sources))
        assertNull(mapBubbleContentFor(target(MapBubbleKind.OFFLINE_REGION, MapLayerIds.OFFLINE_REGION_OUTLINE, "not-a-number"), sources))
    }

    @Test
    fun `a photo shows itself, its date and what it is attached to`() {
        assertEquals(
            MapBubbleContent.Photo(photo.photo, formatRecordTimestamp(1_700_000_000_000L), "In Chanterelle · Kept in 2 journal entries"),
            mapBubbleContentFor(target(MapBubbleKind.PHOTO, MapLayerIds.PHOTOS, "ph-1"), sources),
        )
        assertEquals(
            MapBubbleContent.Photo(loosePhoto.photo, "Date unknown", "Not in a find or a journal entry"),
            mapBubbleContentFor(target(MapBubbleKind.PHOTO, MapLayerIds.PHOTOS, "ph-2"), sources),
        )
    }

    @Test
    fun `a waypoint shows its name and MGRS`() {
        val mgrs = (MgrsConverter.convert(LatLng(45.55, -122.65)) as MgrsCoordinate.Grid).value
        assertEquals(
            MapBubbleContent.WaypointContent(waypoint, mgrs),
            mapBubbleContentFor(target(MapBubbleKind.WAYPOINT, MapLayerIds.WAYPOINTS, "wp-1"), sources),
        )
    }

    @Test
    fun `an entry map's kept waypoint gone from Records is named from the entry's snapshot, with no details`() {
        val kept = Waypoint("wp-gone", 45.4, -122.5, null, "Old gate", "", 0L)
        val content = mapBubbleContentFor(target(MapBubbleKind.WAYPOINT, MapLayerIds.WAYPOINTS, "wp-gone"), sources.copy(snapshotWaypoints = listOf(kept)))
        assertEquals(MapBubbleContent.WaypointContent(kept, (MgrsConverter.convert(LatLng(45.4, -122.5)) as MgrsCoordinate.Grid).value, hasDetails = false), content)
        // A waypoint still in Records is the record, with details, even when a snapshot exists too.
        assertEquals(true, (mapBubbleContentFor(target(MapBubbleKind.WAYPOINT, MapLayerIds.WAYPOINTS, "wp-1"), sources.copy(snapshotWaypoints = listOf(kept))) as MapBubbleContent.WaypointContent).hasDetails)
    }

    @Test
    fun `a track shows its title, date, distance and duration`() {
        val stats = ComputeTrackStatisticsUseCase()(track.points)
        assertEquals(
            MapBubbleContent.TrackContent(
                trackId = "trk-1",
                title = "Morning loop",
                date = formatRecordTimestamp(1_700_000_000_000L),
                distance = formatDistanceMeters(stats.distanceMeters, DistanceUnit.KILOMETERS),
                duration = formatTrackDuration(stats.durationMillis),
            ),
            mapBubbleContentFor(target(MapBubbleKind.TRACK, MapLayerIds.KEPT_TRACKS, "trk-1"), sources),
        )
        assertEquals("1h 0m", formatTrackDuration(stats.durationMillis))
    }

    // F3 (dispatch 2026-09-28-195, item 5): the track's counterpart of the waypoint case above. A kept track
    // gone from Records is named from the entry's snapshot (name, distance and duration are on the decision),
    // with no details (there is no record to open) and no date (the snapshot never held one; a wrong date
    // would be worse than none).
    @Test
    fun `an entry map's kept track gone from Records is named from the entry's snapshot, with no date and no details`() {
        val kept = TrackDecision("trk-gone", "Old ridge", 1234.0, 3_660_000L, 5, kept = true)
        val content = mapBubbleContentFor(target(MapBubbleKind.TRACK, MapLayerIds.KEPT_TRACKS, "trk-gone"), sources.copy(snapshotTracks = listOf(kept)))
        assertEquals(
            MapBubbleContent.TrackContent(
                trackId = "trk-gone",
                title = "Old ridge",
                date = null,
                distance = formatDistanceMeters(1234.0, DistanceUnit.KILOMETERS),
                duration = formatTrackDuration(3_660_000L),
                hasDetails = false,
            ),
            content,
        )
    }

    @Test
    fun `a snapshot track with no name is titled as the entry report titles it`() {
        val kept = TrackDecision("trk-gone", null, 10.0, 60_000L, 1, kept = true)
        val content = mapBubbleContentFor(target(MapBubbleKind.TRACK, MapLayerIds.KEPT_TRACKS, "trk-gone"), sources.copy(snapshotTracks = listOf(kept))) as MapBubbleContent.TrackContent
        assertEquals("Recorded track", content.title)
    }

    @Test
    fun `a track still in Records is the record, with details, even when a snapshot exists too`() {
        val kept = TrackDecision("trk-1", "Old name", 1.0, 1L, 1, kept = true)
        val content = mapBubbleContentFor(target(MapBubbleKind.TRACK, MapLayerIds.KEPT_TRACKS, "trk-1"), sources.copy(snapshotTracks = listOf(kept))) as MapBubbleContent.TrackContent
        assertEquals("Morning loop", content.title)
        assertEquals(true, content.hasDetails)
        assertEquals(formatRecordTimestamp(1_700_000_000_000L), content.date)
    }

    @Test
    fun `a track that is in neither Records nor the snapshots has no bubble`() {
        assertEquals(null, mapBubbleContentFor(target(MapBubbleKind.TRACK, MapLayerIds.KEPT_TRACKS, "trk-nowhere"), sources))
    }

    @Test
    fun `a planned trip shows what its Trip Planner row shows`() {
        val mgrs = (MgrsConverter.convert(LatLng(45.56, -122.66)) as MgrsCoordinate.Grid).value
        assertEquals(
            MapBubbleContent.Trip(trip, "Oct 3", mgrs, "45.5600, -122.6600"),
            mapBubbleContentFor(target(MapBubbleKind.PLANNED_TRIP, MapLayerIds.PLANNED_TRIPS, "trip-1"), sources),
        )
    }

    @Test
    fun `an offline region shows its name, radius and size, and says Stale only when stale`() {
        assertEquals(
            MapBubbleContent.Region(7L, "Forest Park", "5 km", "12.3 MB", stale = false),
            mapBubbleContentFor(target(MapBubbleKind.OFFLINE_REGION, MapLayerIds.OFFLINE_REGION_OUTLINE, "7"), sources),
        )
        assertEquals(true, (mapBubbleContentFor(target(MapBubbleKind.OFFLINE_REGION, MapLayerIds.OFFLINE_REGION_OUTLINE, "8"), sources) as MapBubbleContent.Region).stale)
    }

    // The forecast cell: re-read from the store by group, week and block (planner's M1 ruling).

    private val week = LocalDate.of(2026, 9, 28)

    private fun cellAt(lat: Double, lng: Double, chance: Double) = ForecastCell(
        group = "chanterelles",
        week = week,
        centre = LatLng(lat, lng),
        chance = chance,
        uncertaintyLow = 0.21,
        uncertaintyHigh = 0.58,
        applicable = true,
        drivers = listOf(ForecastDriver("rain, last 14 days", "62 mm"), ForecastDriver("soil temperature", "11")),
        weatherThrough = LocalDate.of(2026, 9, 26),
        modelVersion = "synthetic-1",
    )

    /** Records every request; answers with [cells] filtered to the requested blocks, as a real store would. */
    private class RecordingStore(private val cells: List<ForecastCell>) : ForecastCellStore {
        val requests = mutableListOf<Triple<String, LocalDate, Set<ForecastBlock>>>()
        override suspend fun availability(week: LocalDate) = ForecastAvailability.Groups(setOf("chanterelles"))
        override suspend fun cells(group: String, week: LocalDate, blocks: Set<ForecastBlock>): ForecastCellsResult {
            requests += Triple(group, week, blocks)
            return ForecastCellsResult.Cells(cells.filter { ForecastBlock.containing(it.centre) in blocks }, rejectedCount = 0)
        }
    }

    @Test
    fun `the tapped cell is re-read from the store by its group, week and block`() = runBlocking {
        val tapped = cellAt(45.5, -122.6, 0.37)
        val neighbour = cellAt(45.6, -122.6, 0.9)
        val store = RecordingStore(listOf(neighbour, tapped))

        val found = lookUpForecastCell(store, "chanterelles", week, LatLng(45.47, -122.62), forecastCellFeatureId(tapped))

        assertEquals(tapped, found)
        assertEquals(1, store.requests.size)
        val (group, askedWeek, blocks) = store.requests.single()
        assertEquals("chanterelles", group)
        assertEquals(week, askedWeek)
        assertTrue("the tapped cell's block is asked for ($blocks)", ForecastBlock(45, -123) in blocks)
        // A cell the store no longer has, or a store with no forecast data, gives no bubble.
        assertNull(lookUpForecastCell(RecordingStore(emptyList()), "chanterelles", week, LatLng(45.47, -122.62), forecastCellFeatureId(tapped)))
        assertNull(
            lookUpForecastCell(com.zynergylabs.forager.app.domain.AbsentForecastCellStore, "chanterelles", week, LatLng(45.47, -122.62), forecastCellFeatureId(tapped)),
        )
    }

    @Test
    fun `a tap near a block edge finds a cell centred in the next block`() = runBlocking {
        // The finger is at 45.97, inside the cell centred on 46.0, which belongs to block 46.
        val tapped = cellAt(46.0, -122.6, 0.12)
        val store = RecordingStore(listOf(tapped))
        assertEquals(tapped, lookUpForecastCell(store, "chanterelles", week, LatLng(45.97, -122.6), forecastCellFeatureId(tapped)))
    }

    @Test
    fun `the cell bubble's numbers are the stored cell's, beside its layer name, dates and the reference class`() {
        val cell = cellAt(45.5, -122.6, 0.37)
        assertEquals(
            MapBubbleContent.Cell(
                layerName = "Test forecast: chanterelles (synthetic data)",
                chance = "37%",
                range = "Uncertainty 21% to 58%",
                drivers = listOf("rain, last 14 days: 62 mm", "soil temperature: 11"),
                dates = "Week of 2026-09-28, weather to 2026-09-26",
                referenceClass = LEGEND_REFERENCE_CLASS,
            ),
            forecastCellBubble("Test forecast: chanterelles (synthetic data)", cell),
        )
    }

    // The bubble's placement: the tail's tip on the anchor, clamped or not (B2).

    private fun place(anchor: Offset, angle: Float = 135f) = bubblePlacement(
        anchor = anchor,
        arrowAngleDeg = angle,
        bubbleWidth = 300,
        bubbleHeight = 150,
        tailPx = 24f,
        maxWidth = 1000,
        maxHeight = 2000,
        minY = 100,
    )

    private fun assertTipOnAnchor(anchor: Offset, placement: BubblePlacement) {
        val tip = Offset(placement.topLeftX + placement.tipInBubble.x, placement.topLeftY + placement.tipInBubble.y)
        assertTrue("the tip $tip lands on the anchor $anchor", abs(tip.x - anchor.x) < 0.5f && abs(tip.y - anchor.y) < 0.5f)
    }

    private fun assertInBox(placement: BubblePlacement) {
        assertTrue("left edge in the box (${placement.topLeftX})", placement.topLeftX >= 0)
        assertTrue("right edge in the box (${placement.topLeftX})", placement.topLeftX + 300 <= 1000)
        assertTrue("top below minY (${placement.topLeftY})", placement.topLeftY >= 100)
        assertTrue("bottom in the box (${placement.topLeftY})", placement.topLeftY + 150 <= 2000)
    }

    @Test
    fun `unclamped, the bubble sits above and to the left of the anchor with the tip on it`() {
        val anchor = Offset(600f, 900f)
        val placement = place(anchor)
        assertTipOnAnchor(anchor, placement)
        assertInBox(placement)
        // The card is the placed bubble less the tail's 24 px margin on every side.
        assertTrue("the card is above the anchor (${placement.topLeftY})", placement.topLeftY + 150 - 24 <= 900)
        // The tail leaves the card's bottom edge (a wide card at 135 degrees), so the card's centre,
        // not its whole width, is left of the anchor.
        assertTrue("the card's centre is left of the anchor (${placement.topLeftX})", placement.topLeftX + 150 < 600)
    }

    @Test
    fun `clamped at the left, top and right edges the card moves and the tip stays on the anchor`() {
        listOf(Offset(20f, 900f), Offset(600f, 110f), Offset(990f, 1990f), Offset(0f, 0f)).forEach { anchor ->
            val placement = place(anchor)
            assertInBox(placement)
            assertTipOnAnchor(anchor, placement)
        }
    }

    private companion object {
        val SIGHTING = Sighting(42L, 1L, "Cantharellus formosus", "Chanterelle", 45.5, -122.6, LocalDate.of(2026, 8, 1), null, 10)
    }
}
