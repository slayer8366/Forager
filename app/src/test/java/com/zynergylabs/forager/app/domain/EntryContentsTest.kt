package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.domain.model.DerivedTrip
import com.zynergylabs.forager.app.domain.model.FindDecision
import com.zynergylabs.forager.app.domain.model.MushroomLogEntry
import com.zynergylabs.forager.app.domain.model.OfflineRegionDecision
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackDecision
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.WaypointDecision
import com.zynergylabs.forager.app.domain.model.WaypointDesignation
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [entryContentsOf] — the editor's "In this entry" panel (dispatch 2026-09-28-667, data part A; the
 * owner in RECORD -656). Headless: built from an entry and a day's candidates directly. Each test reads
 * the panel's actual arrangement (ids per list, the counts, the group states), not a proxy for it.
 */
class EntryContentsTest {

    private val day = LocalDate.of(2026, 8, 1)
    private val stats = ComputeTrackStatisticsUseCase()

    private fun waypoint(id: String, trackId: String? = null, designation: WaypointDesignation? = null, at: Long = 0L) =
        Waypoint(id = id, lat = 45.0, lng = -122.0, altitude = null, name = id, note = "", createdAtEpochMillis = at, trackId = trackId, designation = designation)

    private fun track(id: String) = Track(id = id, name = id, startedAtEpochMillis = 0L, endedAtEpochMillis = 1L, points = emptyList())

    private fun find(id: String) = MushroomLogEntry.draft(id = id, location = null, date = day)

    private fun trackDecision(id: String, kept: Boolean = true) = TrackDecision(trackId = id, name = id, distanceMeters = 1_000.0, durationMillis = 600_000L, pointCount = 10, kept = kept)
    private fun waypointDecision(id: String, kept: Boolean = true) = WaypointDecision(waypointId = id, name = id, lat = 45.0, lng = -122.0, kept = kept)
    private fun findDecision(id: String, kept: Boolean = true) = FindDecision(findId = id, foundOn = day, ownIdentification = null, hasPhotos = false, kept = kept)

    /** One walk with its Start, End and a dropped waypoint, a loose waypoint and a find, every one decided and included. */
    private val walkDay = DerivedTrip(
        date = day,
        finds = listOf(find("f1")),
        tracks = listOf(track("t1")),
        waypoints = listOf(
            waypoint("start", trackId = "t1", designation = WaypointDesignation.ORIGIN, at = 1L),
            waypoint("dropped", trackId = "t1", at = 2L),
            waypoint("end", trackId = "t1", designation = WaypointDesignation.END, at = 3L),
            waypoint("loose", at = 4L),
        ),
        offlineRegions = emptyList(),
    )

    private val walkEntry = CartographyEntry.draft(id = "e1", date = day, updatedAtEpochMillis = 0L).copy(
        trackDecisions = listOf(trackDecision("t1")),
        waypointDecisions = listOf(waypointDecision("start"), waypointDecision("dropped"), waypointDecision("end"), waypointDecision("loose")),
        findDecisions = listOf(findDecision("f1")),
    )

    @Test
    fun `a track's Start, End and dropped waypoint sit under it, and the loose waypoint is listed on its own`() {
        val contents = entryContentsOf(walkEntry, walkDay, emptyList(), stats)

        assertEquals(listOf("t1"), contents.tracks.map { it.track.id })
        assertEquals(listOf("start", "dropped", "end"), contents.tracks.single().waypoints.map { it.id })
        assertEquals(listOf("loose"), contents.waypoints.map { it.id })
        assertEquals(listOf("f1"), contents.finds.map { it.id })
        assertEquals(emptyList<String>(), contents.newItems.map { it.id })
        assertEquals(listOf("t1", "start", "dropped", "end"), contents.members(EntryGroup.TRACKS).map { it.id })
        assertEquals(listOf("loose"), contents.members(EntryGroup.WAYPOINTS).map { it.id })
    }

    /** The owner's example counts a walk's waypoints without its Start and End ("1 track, 5 waypoints" for five dropped). */
    @Test
    fun `the included counts leave out a track's own Start and End`() {
        val contents = entryContentsOf(walkEntry, walkDay, emptyList(), stats)

        assertEquals(EntryIncludedCounts(tracks = 1, waypoints = 2, finds = 1, offlineMaps = 0), contents.included)
    }

    @Test
    fun `left-out items are not counted, and the group says some or all are left out`() {
        val entry = walkEntry.copy(
            waypointDecisions = listOf(waypointDecision("start"), waypointDecision("dropped", kept = false), waypointDecision("end"), waypointDecision("loose", kept = false)),
            findDecisions = listOf(findDecision("f1", kept = false)),
        )
        val contents = entryContentsOf(entry, walkDay, emptyList(), stats)

        assertEquals(EntryIncludedCounts(tracks = 1, waypoints = 0, finds = 0, offlineMaps = 0), contents.included)
        assertEquals(EntryGroupState.SOME_INCLUDED, contents.groupState(EntryGroup.TRACKS))
        assertEquals(EntryGroupState.ALL_LEFT_OUT, contents.groupState(EntryGroup.WAYPOINTS))
        assertEquals(EntryGroupState.ALL_LEFT_OUT, contents.groupState(EntryGroup.FINDS))
        assertEquals(EntryGroupState.EMPTY, contents.groupState(EntryGroup.OFFLINE_MAPS))
    }

    /**
     * Items new since the last save (live candidates with no decision) are listed at the top only. A new
     * waypoint that names an included track belongs to Tracks, so the Tracks switch settles it; a new
     * loose one belongs to Waypoints; a group holding only new items has not been chosen yet.
     */
    @Test
    fun `new items are listed at the top only, each in the group whose switch settles it`() {
        val day2 = walkDay.copy(
            waypoints = walkDay.waypoints + waypoint("dropped-later", trackId = "t1", at = 5L) + waypoint("loose-later", at = 6L),
            offlineRegions = emptyList(),
        )
        val region = OfflineRegionSummary(id = 7L, name = "Ridge", region = Region(lat = 45.0, lng = -122.0, radiusKm = 10), minZoom = 0.0, maxZoom = 14.0, tileCount = 1, sizeBytes = 1L, createdAtEpochMillis = 0L)
        val contents = entryContentsOf(walkEntry, day2, listOf(region), stats)

        assertEquals(listOf("dropped-later", "loose-later", "7"), contents.newItems.map { it.id })
        assertEquals(listOf("start", "dropped", "end"), contents.tracks.single().waypoints.map { it.id })
        assertEquals(listOf("loose"), contents.waypoints.map { it.id })
        assertEquals(listOf("t1", "start", "dropped", "end", "dropped-later"), contents.members(EntryGroup.TRACKS).map { it.id })
        assertEquals(listOf("loose", "loose-later"), contents.members(EntryGroup.WAYPOINTS).map { it.id })
        assertEquals(EntryGroupState.NOT_CHOSEN, contents.groupState(EntryGroup.OFFLINE_MAPS))
        assertEquals("a new item is not counted as included", 2, contents.included.waypoints)
    }

    /** Before the day loads, the panel still shows every decision; with no live record to name a track, each waypoint is listed on its own. */
    @Test
    fun `with no candidates loaded every decided waypoint is listed on its own and nothing is new`() {
        val contents = entryContentsOf(walkEntry, candidates = null, candidateOfflineRegions = emptyList(), computeTrackStatistics = stats)

        assertEquals(listOf("t1"), contents.tracks.map { it.track.id })
        assertEquals(emptyList<String>(), contents.tracks.single().waypoints.map { it.id })
        assertEquals(listOf("start", "dropped", "end", "loose"), contents.waypoints.map { it.id })
        assertEquals(emptyList<String>(), contents.newItems.map { it.id })
        assertEquals("unknown designations count as waypoints", 4, contents.included.waypoints)
    }

    /**
     * The per-item setters move a changed decision to the end of its list. The panel orders decided items
     * as the day lists them, so flipping a switch does not move its row; one no longer live goes after.
     */
    @Test
    fun `decided items keep the day's order however their decisions are stored`() {
        val entry = walkEntry.copy(
            waypointDecisions = listOf(waypointDecision("gone"), waypointDecision("loose"), waypointDecision("end"), waypointDecision("start"), waypointDecision("dropped")),
        )
        val contents = entryContentsOf(entry, walkDay, emptyList(), stats)

        assertEquals(listOf("start", "dropped", "end"), contents.tracks.single().waypoints.map { it.id })
        assertEquals(listOf("loose", "gone"), contents.waypoints.map { it.id })
    }

    @Test
    fun `an offline map decision is counted and grouped`() {
        val entry = walkEntry.copy(offlineRegionDecisions = listOf(OfflineRegionDecision(offlineRegionId = 3L, name = "Ridge", lat = 45.0, lng = -122.0, radiusKm = 10, kept = true)))
        val contents = entryContentsOf(entry, walkDay, emptyList(), stats)

        assertEquals(1, contents.included.offlineMaps)
        assertEquals(listOf("3"), contents.members(EntryGroup.OFFLINE_MAPS).map { it.id })
        assertEquals(EntryGroupState.ALL_INCLUDED, contents.groupState(EntryGroup.OFFLINE_MAPS))
    }
}
