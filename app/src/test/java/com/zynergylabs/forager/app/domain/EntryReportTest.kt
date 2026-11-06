package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.domain.model.FindDecision
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackDecision
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.WaypointDecision
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [entryReportOf] — the figures behind the entry report's tiles, height profile and waypoint table
 * (dispatch 2026-09-28-667, data part A; the owner in RECORD -656). Headless.
 *
 * Distances by hand: one step of 0.001° of latitude is 111.195 m (mean Earth radius 6 371 008.8 m,
 * `GeoDistance`), so a track of n points 0.001° apart is (n - 1) x 111.195 m long.
 */
class EntryReportTest {

    private val day = LocalDate.of(2026, 8, 1)
    private val stats = ComputeTrackStatisticsUseCase()
    private val step = 111.195

    /** [heights] one point each, 0.001° north of the last, ten seconds apart from [t0]; `null` for a point with no height. */
    private fun track(id: String, heights: List<Double?>, t0: Long = 1_000_000L, lat0: Double = 45.0, importedWithoutTimes: Boolean = false) = Track(
        id = id,
        name = id,
        startedAtEpochMillis = t0,
        endedAtEpochMillis = t0 + heights.size * 10_000L,
        points = heights.mapIndexed { i, h -> TrackPoint(lat = lat0 + 0.001 * i, lng = -122.0, altitude = h, accuracyMeters = 5f, timestampEpochMillis = t0 + i * 10_000L) },
        importedWithoutTimes = importedWithoutTimes,
    )

    private fun decision(id: String, distance: Double = 1_000.0, duration: Long = 600_000L, kept: Boolean = true) =
        TrackDecision(trackId = id, name = id, distanceMeters = distance, durationMillis = duration, pointCount = 0, kept = kept)

    private fun entry(
        tracks: List<TrackDecision> = emptyList(),
        waypoints: List<WaypointDecision> = emptyList(),
        finds: List<FindDecision> = emptyList(),
    ) = CartographyEntry.draft(id = "e1", date = day, updatedAtEpochMillis = 0L).copy(trackDecisions = tracks, waypointDecisions = waypoints, findDecisions = finds)

    private fun findDecision(id: String, kept: Boolean) = FindDecision(findId = id, foundOn = day, ownIdentification = null, hasPhotos = false, kept = kept)

    @Test
    fun `the tiles add up the included tracks' snapshots and count the included finds, never a left-out one`() {
        val report = entryReportOf(
            entry(
                tracks = listOf(decision("a", 1_200.0, 600_000L), decision("b", 800.0, 1_800_000L), decision("left-out", 5_000.0, 9_000_000L, kept = false)),
                finds = listOf(findDecision("f1", true), findDecision("f2", true), findDecision("f3", false)),
            ),
            liveTracks = emptyList(),
            liveWaypoints = emptyList(),
            computeTrackStatistics = stats,
        )

        assertEquals(2, report.trackCount)
        assertEquals(2_000.0, report.distanceMeters, 0.0)
        assertEquals(2_400_000L, report.durationMillis)
        assertEquals(2, report.findCount)
    }

    /**
     * Climb is the included tracks' gain added up, through `ComputeTrackStatisticsUseCase` (4 m
     * hysteresis): 100 -> 120 in 5 m steps gains 20 m; 150 -> 130 gains nothing.
     */
    @Test
    fun `climb is the included tracks' gain together`() {
        val up = track("up", listOf(100.0, 105.0, 110.0, 115.0, 120.0))
        val down = track("down", listOf(150.0, 145.0, 140.0, 135.0, 130.0), t0 = 9_000_000L)
        val report = entryReportOf(entry(tracks = listOf(decision("up"), decision("down"))), listOf(up, down), emptyList(), stats)

        assertEquals(20.0, report.climbMeters!!, 1e-9)
    }

    @Test
    fun `climb is not known when an included track's points are gone, or carry no height`() {
        val up = track("up", listOf(100.0, 105.0, 110.0))
        val flatUnknown = track("unknown", listOf(null, null, null), t0 = 9_000_000L)

        assertNull("a deleted track", entryReportOf(entry(tracks = listOf(decision("up"), decision("gone"))), listOf(up), emptyList(), stats).climbMeters)
        assertNull("no heights", entryReportOf(entry(tracks = listOf(decision("up"), decision("unknown"))), listOf(up, flatUnknown), emptyList(), stats).climbMeters)
    }

    /**
     * RECORD -867, the planner's call (a): a track with fewer than two points has no height change to miss, so it is
     * left out of Climb rather than blanking it. The owner's Oct 10 entry held a recording started and stopped within
     * a minute beside the real walk, and Climb read "Not recorded" while the chart drew.
     */
    @Test
    fun `a track with fewer than two points is left out of climb, not a reason to blank it`() {
        val up = track("up", listOf(100.0, 105.0, 110.0, 115.0, 120.0))
        val blip = track("blip", listOf(130.0), t0 = 9_000_000L)
        val empty = track("empty", emptyList(), t0 = 9_500_000L)

        val report = entryReportOf(entry(tracks = listOf(decision("up"), decision("blip"), decision("empty"))), listOf(up, blip, empty), emptyList(), stats)

        assertEquals("the walk's 20 m, with a one-point and an empty track beside it", 20.0, report.climbMeters ?: -1.0, 1e-9)
    }

    /** With nothing but short tracks there is no track to measure, so Climb is still not known. */
    @Test
    fun `climb is not known when every included track has fewer than two points`() {
        val blip = track("blip", listOf(130.0))
        assertNull(entryReportOf(entry(tracks = listOf(decision("blip"))), listOf(blip), emptyList(), stats).climbMeters)
    }

    @Test
    fun `no included track means no profile at all, and a missing track's points say so`() {
        assertEquals(HeightProfile.NoTrack, entryReportOf(entry(tracks = listOf(decision("a", kept = false))), emptyList(), emptyList(), stats).heightProfile)
        assertEquals(HeightProfile.PointsUnavailable, entryReportOf(entry(tracks = listOf(decision("a"))), emptyList(), emptyList(), stats).heightProfile)
    }

    /** The limits as [PROVISIONAL_PROFILE_HEIGHT_LIMIT] sets them today: at least 10 points with a height, and at least half of all points. */
    @Test
    fun `too few heights draw no profile`() {
        assertEquals("the figures these cases are built on", ProfileHeightLimit(minPointsWithHeight = 10, minShareWithHeight = 0.5), PROVISIONAL_PROFILE_HEIGHT_LIMIT)
        val nine = track("nine", List(9) { 100.0 })
        assertEquals(HeightProfile.TooFewHeights(pointsWithHeight = 9, totalPoints = 9), entryReportOf(entry(tracks = listOf(decision("nine"))), listOf(nine), emptyList(), stats).heightProfile)

        val sparse = track("sparse", List(10) { 100.0 } + List(11) { null })
        assertEquals(HeightProfile.TooFewHeights(pointsWithHeight = 10, totalPoints = 21), entryReportOf(entry(tracks = listOf(decision("sparse"))), listOf(sparse), emptyList(), stats).heightProfile)

        val enough = track("enough", List(10) { 100.0 } + List(10) { null })
        assertTrue("10 of 20 is half, so it draws", entryReportOf(entry(tracks = listOf(decision("enough"))), listOf(enough), emptyList(), stats).heightProfile is HeightProfile.Drawn)
    }

    /**
     * Two walks: the second's distance runs on from the first's end (the gap between them is not
     * walked), points without a height are skipped but still count toward distance, and the lowest and
     * highest are read off the samples.
     */
    @Test
    fun `a drawn profile runs on from one walk to the next and skips points with no height`() {
        val morning = track("morning", listOf(100.0, null, 120.0, 110.0, 105.0, 101.0), t0 = 1_000_000L, lat0 = 45.0)
        val afternoon = track("afternoon", listOf(90.0, 95.0, 130.0, 125.0, 120.0, 115.0), t0 = 50_000_000L, lat0 = 46.0)
        // Listed afternoon first: the profile orders by when each was walked.
        val profile = entryReportOf(entry(tracks = listOf(decision("afternoon"), decision("morning"))), listOf(afternoon, morning), emptyList(), stats).heightProfile as HeightProfile.Drawn

        assertEquals(2, profile.segments.size)
        val expected = listOf(0.0, 2 * step, 3 * step, 4 * step, 5 * step)
        val actual = profile.segments[0].map { it.distanceMeters }
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (e, a) -> assertEquals(e, a, 0.01) }
        // RECORD -867, part 4: the chart draws smoothed heights (trackHeights). By hand, on the morning's run 120, 110,
        // 105, 101 (10 s apart): a run's ends stay as recorded; 110 becomes the mean of the medians 120, 110, 105 =
        // 111.67, and 105 the mean of 110, 105, 101 = 105.33. The afternoon's 130 is a lone peak among 90, 95, 125, 120,
        // 115: its median is 120 and its smoothed height 109, so the highest drawn is the morning's 120, not 130.
        // (Until 2026-10-11 these read the raw 110, 105 and a highest of 130.)
        val morningHeights = listOf(100.0, 120.0, 111.0 + 2.0 / 3.0, 105.0 + 1.0 / 3.0, 101.0)
        morningHeights.zip(profile.segments[0].map { it.altitudeMeters }).forEach { (e, a) -> assertEquals(e, a, 1e-9) }
        assertEquals("the afternoon starts where the morning ended", 5 * step, profile.segments[1].first().distanceMeters, 0.01)
        assertEquals(10 * step, profile.lengthMeters, 0.01)
        assertEquals(90.0, profile.lowestMeters, 1e-9)
        assertEquals(120.0, profile.highestMeters, 1e-9)
    }

    /**
     * Each included waypoint's row: its record's time, and the distance along its own track to that time.
     * A waypoint with no track, or on an imported track without real times, has no distance; one whose
     * record is gone has neither. Rows read in time order, unknown times last.
     */
    @Test
    fun `waypoint rows carry their time and the distance walked along their own track`() {
        val walk = track("walk", List(12) { 100.0 }, t0 = 1_000_000L)
        val imported = track("imported", List(12) { 100.0 }, t0 = 5_000_000L, importedWithoutTimes = true)
        val live = listOf(
            Waypoint(id = "start", lat = 45.0, lng = -122.0, altitude = null, name = "Start", note = "", createdAtEpochMillis = 1_000_000L, trackId = "walk"),
            // 35 s in: the last point at or before it is the fourth (30 s), three steps along.
            Waypoint(id = "fir", lat = 45.003, lng = -122.0, altitude = null, name = "Big fir", note = "", createdAtEpochMillis = 1_035_000L, trackId = "walk"),
            Waypoint(id = "loose", lat = 45.5, lng = -122.5, altitude = null, name = "Creek pin", note = "", createdAtEpochMillis = 900_000L),
            Waypoint(id = "gpx", lat = 45.0, lng = -122.0, altitude = null, name = "GPX pin", note = "", createdAtEpochMillis = 5_050_000L, trackId = "imported"),
        )
        val decisions = listOf("fir", "gone", "gpx", "start", "loose").map { WaypointDecision(waypointId = it, name = it, lat = 45.1, lng = -122.1, kept = true) } +
            WaypointDecision(waypointId = "left-out", name = "left-out", lat = 0.0, lng = 0.0, kept = false)

        val rows = entryReportOf(entry(tracks = listOf(decision("walk"), decision("imported")), waypoints = decisions), listOf(walk, imported), live, stats).waypoints

        assertEquals(listOf("loose", "start", "fir", "gpx", "gone"), rows.map { it.id })
        assertEquals(listOf(900_000L, 1_000_000L, 1_035_000L, 5_050_000L, null), rows.map { it.timeEpochMillis })
        assertNull(rows[0].distanceFromStartMeters)
        assertEquals(0.0, rows[1].distanceFromStartMeters!!, 0.0)
        assertEquals(3 * step, rows[2].distanceFromStartMeters!!, 0.01)
        assertNull("imported without times", rows[3].distanceFromStartMeters)
        assertNull("record gone", rows[4].distanceFromStartMeters)
        assertEquals("the snapshot's coordinates, not the live record's", 45.1, rows[2].lat, 0.0)
    }
}
