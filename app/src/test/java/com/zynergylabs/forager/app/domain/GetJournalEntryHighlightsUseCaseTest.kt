package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.domain.model.FindDecision
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.OfflineRegionDecision
import com.zynergylabs.forager.app.domain.model.PhotoAttachment
import com.zynergylabs.forager.app.domain.model.RecordPoint
import com.zynergylabs.forager.app.domain.model.RecordPolyline
import com.zynergylabs.forager.app.domain.model.RecordRegion
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.TrackDecision
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.WaypointDecision
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * J8's use case: shown saved entries in, highlight geometry out (`prompts/preserved/2026-09-28-52.md`,
 * J8-2; the owner's "Highlight in place", "Live records" and "Saved entries only"). Headless: every
 * input is a plain list. Each test names the rule it pins, and its data holds a case that the rule
 * decides both ways, so a test cannot pass on an input that could not fail it.
 */
class GetJournalEntryHighlightsUseCaseTest {

    private val useCase = GetJournalEntryHighlightsUseCase()

    private val dayA = LocalDate.of(2026, 9, 12)
    private val dayB = LocalDate.of(2026, 9, 5)

    // The records the Maps tab draws: live geometry, which differs from every entry's snapshot.
    private val trackLive = RecordPolyline("track-1", listOf(LatLng(45.0, -122.0), LatLng(45.01, -122.01)))
    private val trackOther = RecordPolyline("track-other", listOf(LatLng(46.0, -123.0), LatLng(46.01, -123.01)))
    private val findLive = RecordPoint("find-1", LatLng(45.2, -122.2))
    private val photoLive = RecordPoint("photo-1", LatLng(45.3, -122.3))
    private val regionLive = RecordRegion("7", Region(lat = 45.5, lng = -122.5, radiusKm = 12))
    private val waypointLive = Waypoint(id = "waypoint-1", lat = 45.44, lng = -122.66, altitude = null, name = "Trailhead (moved)", note = "", createdAtEpochMillis = 0L)
    private val waypointOther = Waypoint(id = "waypoint-other", lat = 44.0, lng = -121.0, altitude = null, name = "Other", note = "", createdAtEpochMillis = 0L)

    private val records = MapRecords(
        findMarkers = listOf(findLive, RecordPoint("find-other", LatLng(1.0, 1.0))),
        photoMarkers = listOf(photoLive, RecordPoint("photo-other", LatLng(2.0, 2.0))),
        trackPolylines = listOf(trackLive, trackOther),
        offlineRegionCircles = listOf(regionLive, RecordRegion("8", Region(lat = 3.0, lng = 3.0, radiusKm = 5))),
        failures = emptyList(),
    )
    private val waypoints = listOf(waypointLive, waypointOther)

    /** Keeps one record of every kind, and withholds a second of each: snapshots deliberately stale. */
    private fun entry(id: String, date: LocalDate, shown: Boolean = true, draft: Boolean = false) = CartographyEntry(
        id = id,
        date = date,
        text = "",
        tags = emptyList(),
        isDraft = draft,
        updatedAtEpochMillis = 1L,
        findDecisions = listOf(
            FindDecision(findId = "find-1", foundOn = date, ownIdentification = null, hasPhotos = false, kept = true),
            FindDecision(findId = "find-other", foundOn = date, ownIdentification = null, hasPhotos = false, kept = false),
        ),
        trackDecisions = listOf(
            TrackDecision(trackId = "track-1", name = null, distanceMeters = 1.0, durationMillis = 1L, pointCount = 2, kept = true),
            TrackDecision(trackId = "track-other", name = null, distanceMeters = 1.0, durationMillis = 1L, pointCount = 2, kept = false),
        ),
        waypointDecisions = listOf(
            WaypointDecision(waypointId = "waypoint-1", name = "Trailhead", lat = 45.4, lng = -122.6, kept = true),
            WaypointDecision(waypointId = "waypoint-other", name = "Other", lat = 44.0, lng = -121.0, kept = false),
        ),
        offlineRegionDecisions = listOf(
            OfflineRegionDecision(offlineRegionId = 7L, name = "Ridge", lat = 45.4, lng = -122.6, radiusKm = 10, kept = true),
            OfflineRegionDecision(offlineRegionId = 8L, name = "Other", lat = 3.0, lng = 3.0, radiusKm = 5, kept = false),
        ),
        photos = listOf(PhotoAttachment(photoId = "photo-1", attachedAtEpochMillis = 1L)),
        shownOnMap = shown,
    )

    private val onMapA = JournalEntryOnMap("entry-a", dayA)
    private val onMapB = JournalEntryOnMap("entry-b", dayB)

    @Test
    fun `a shown saved entry highlights its kept records with the live geometry the map draws, never its snapshots`() {
        val highlights = useCase(listOf(entry("entry-a", dayA)), records, waypoints)

        assertEquals(listOf(onMapA), highlights.shownEntries)
        assertEquals(listOf(trackLive), highlights.trackPolylines)
        assertEquals(listOf(findLive), highlights.findMarkers)
        assertEquals(listOf(photoLive), highlights.photoMarkers)
        // The waypoint at its live position (45.44, -122.66), not the entry's snapshot (45.4, -122.6).
        assertEquals(listOf(RecordPoint("waypoint-1", LatLng(45.44, -122.66))), highlights.waypointMarkers)
        // The region at its live radius (12 km), not the snapshot's 10.
        assertEquals(listOf(regionLive), highlights.offlineRegionCircles)
        assertEquals(
            mapOf(
                HighlightedRecord(HighlightedRecordKind.TRACK, "track-1") to listOf(onMapA),
                HighlightedRecord(HighlightedRecordKind.FIND, "find-1") to listOf(onMapA),
                HighlightedRecord(HighlightedRecordKind.PHOTO, "photo-1") to listOf(onMapA),
                HighlightedRecord(HighlightedRecordKind.WAYPOINT, "waypoint-1") to listOf(onMapA),
                HighlightedRecord(HighlightedRecordKind.OFFLINE_REGION, "7") to listOf(onMapA),
            ),
            highlights.keptIn,
        )
    }

    @Test
    fun `drafts are never highlighted, even with shownOnMap set, and hidden saved entries are not either`() {
        val draft = entry("entry-draft", dayA, shown = true, draft = true)
        val hidden = entry("entry-hidden", dayA, shown = false)
        assertEquals(JournalEntryHighlights.NONE, useCase(listOf(draft, hidden), records, waypoints))
        // The same entries shown and saved do highlight, so the input could have failed the rule.
        val highlighted = useCase(listOf(draft.copy(isDraft = false), hidden.copy(shownOnMap = true)), records, waypoints)
        assertEquals(listOf("entry-draft", "entry-hidden"), highlighted.shownEntries.map { it.entryId })
        assertEquals(listOf(trackLive), highlighted.trackPolylines)
    }

    @Test
    fun `a kept record deleted or not drawn today is dropped, and withheld decisions are never highlighted`() {
        // Every kept record gone from what the map draws: deleted, or (a track) not ended so not drawn.
        val nothingKeptDrawn = MapRecords(
            findMarkers = listOf(RecordPoint("find-other", LatLng(1.0, 1.0))),
            photoMarkers = listOf(RecordPoint("photo-other", LatLng(2.0, 2.0))),
            trackPolylines = listOf(trackOther),
            offlineRegionCircles = listOf(RecordRegion("8", Region(lat = 3.0, lng = 3.0, radiusKm = 5))),
            failures = emptyList(),
        )
        val highlights = useCase(listOf(entry("entry-a", dayA)), nothingKeptDrawn, listOf(waypointOther))
        assertEquals("the entry is still shown", listOf(onMapA), highlights.shownEntries)
        assertEquals(emptyList<RecordPolyline>(), highlights.trackPolylines)
        assertEquals(emptyList<RecordPoint>(), highlights.findMarkers)
        assertEquals(emptyList<RecordPoint>(), highlights.photoMarkers)
        assertEquals(emptyList<RecordPoint>(), highlights.waypointMarkers)
        assertEquals(emptyList<RecordRegion>(), highlights.offlineRegionCircles)
        assertEquals(emptyMap<HighlightedRecord, List<JournalEntryOnMap>>(), highlights.keptIn)
    }

    @Test
    fun `several shown entries highlight together, and a record kept by two names both, in the Journal's order`() {
        val a = entry("entry-a", dayA)
        // Entry B keeps only the track and the find.
        val b = entry("entry-b", dayB).copy(
            waypointDecisions = emptyList(),
            offlineRegionDecisions = emptyList(),
            photos = emptyList(),
            trackDecisions = listOf(TrackDecision(trackId = "track-other", name = null, distanceMeters = 1.0, durationMillis = 1L, pointCount = 2, kept = true)),
        )
        val highlights = useCase(listOf(a, b), records, waypoints)
        assertEquals(listOf(onMapA, onMapB), highlights.shownEntries)
        assertEquals(listOf(trackLive, trackOther), highlights.trackPolylines)
        assertEquals(listOf(onMapA, onMapB), highlights.keptIn[HighlightedRecord(HighlightedRecordKind.FIND, "find-1")])
        assertEquals(listOf(onMapA), highlights.keptIn[HighlightedRecord(HighlightedRecordKind.TRACK, "track-1")])
        assertEquals(listOf(onMapB), highlights.keptIn[HighlightedRecord(HighlightedRecordKind.TRACK, "track-other")])
        assertEquals(listOf(findLive), highlights.findMarkers)
    }

    @Test
    fun `an entry's photos are its attached photos, not the photos of the finds it keeps`() {
        // The kept find has no attached photo here; photo-other is on the map but attached to nothing.
        val onlyFind = entry("entry-a", dayA).copy(photos = emptyList())
        assertEquals(emptyList<RecordPoint>(), useCase(listOf(onlyFind), records, waypoints).photoMarkers)
        val attached = onlyFind.copy(photos = listOf(PhotoAttachment(photoId = "photo-other", attachedAtEpochMillis = 1L)))
        assertEquals(listOf(RecordPoint("photo-other", LatLng(2.0, 2.0))), useCase(listOf(attached), records, waypoints).photoMarkers)
    }
}
