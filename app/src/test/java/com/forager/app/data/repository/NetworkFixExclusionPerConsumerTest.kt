package com.forager.app.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.forager.app.data.local.ForagerDatabase
import com.forager.app.domain.Alert
import com.forager.app.domain.AlertAudibility
import com.forager.app.domain.AlertAudibilityState
import com.forager.app.domain.AlertDelivery
import com.forager.app.domain.ComputeReturnToStartUseCase
import com.forager.app.domain.CreateWaypointUseCase
import com.forager.app.domain.CurrentTimeProvider
import com.forager.app.domain.DeleteWaypointUseCase
import com.forager.app.domain.DetectOffTrackUseCase
import com.forager.app.domain.GetCartographyEntryMapDataUseCase
import com.forager.app.domain.GetDerivedTripUseCase
import com.forager.app.domain.GetTracksUseCase
import com.forager.app.domain.GetTripReportOfflineRegionsUseCase
import com.forager.app.domain.GetWaypointsUseCase
import com.forager.app.domain.LocationFix
import com.forager.app.domain.LocationTracker
import com.forager.app.domain.OfflineMapRepository
import com.forager.app.domain.OfflineRegionSummary
import com.forager.app.domain.RingerMode
import com.forager.app.domain.StartTrackUseCase
import com.forager.app.domain.model.CartographyEntry
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.Track
import com.forager.app.domain.model.TrackDecision
import com.forager.app.domain.model.TrackPoint
import com.forager.app.domain.model.Waypoint
import com.forager.app.domain.model.WaypointDesignation
import com.forager.app.export.TrackGpxExporter
import com.forager.app.ui.track.TrackRecordingViewModel
import com.forager.app.ui.track.trackSubtitle
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every consumer of a track's points sees the read seam's exclusion — asserted **per consumer,
 * through each one's real entry point and the real Room-backed repository**, not once at the
 * repository (timestamp-filter dispatch). One stored track, five rows: three whole-second GPS points
 * a metre apart in latitude steps of 0.001° and two sub-second network fixes 3.3 km north of them.
 * The expected survivors are the three whole-second points, by hand.
 *
 * Also asserts the thing the rule's design rests on: **nothing on disk changes** — the DAO's own rows
 * after every read are the five that were inserted.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NetworkFixExclusionPerConsumerTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var database: ForagerDatabase
    private lateinit var trackRepository: RoomTrackRepository

    private val whole1 = TrackPoint(lat = 45.000, lng = -122.0, altitude = null, accuracyMeters = 8f, timestampEpochMillis = 1_000L)
    private val sub1 = TrackPoint(lat = 45.030, lng = -122.0, altitude = 111.6, accuracyMeters = 20f, timestampEpochMillis = 3_500L)
    private val whole2 = TrackPoint(lat = 45.001, lng = -122.0, altitude = null, accuracyMeters = 8f, timestampEpochMillis = 6_000L)
    private val sub2 = TrackPoint(lat = 45.030, lng = -122.0, altitude = 111.6, accuracyMeters = 20f, timestampEpochMillis = 8_250L)
    private val whole3 = TrackPoint(lat = 45.002, lng = -122.0, altitude = null, accuracyMeters = 8f, timestampEpochMillis = 11_000L)
    private val stored = listOf(whole1, sub1, whole2, sub2, whole3)
    private val survivors = listOf(whole1, whole2, whole3)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val direct = java.util.concurrent.Executor { it.run() }
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Application>(), ForagerDatabase::class.java)
            .setQueryExecutor(direct)
            .setTransactionExecutor(direct)
            .build()
        trackRepository = RoomTrackRepository(database.trackDao())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        if (database.isOpen) database.close()
    }

    private suspend fun seedTrack(id: String = "t1"): Track {
        val track = Track(id = id, name = null, startedAtEpochMillis = 1_000L, endedAtEpochMillis = null, points = emptyList())
        trackRepository.create(track).getOrThrow()
        trackRepository.appendPoints(id, stored).getOrThrow()
        return track
    }

    private suspend fun assertDiskUnchanged(id: String = "t1") {
        val rows = database.trackDao().getPointsForTrack(id)
        assertEquals(5, rows.size)
        assertEquals(stored.map { it.timestampEpochMillis }, rows.map { it.timestampEpochMillis })
        assertEquals(stored.map { it.lat }, rows.map { it.lat })
    }

    @Test
    fun `the repository returns the survivors with the excluded count, and the rows on disk are untouched`() = runTest(dispatcher) {
        seedTrack()

        val track = trackRepository.getById("t1").getOrThrow()!!
        assertEquals(survivors, track.points)
        assertEquals(2, track.excludedPointCount)
        assertEquals(survivors, trackRepository.getAll().getOrThrow().single().points)
        assertDiskUnchanged()
    }

    @Test
    fun `Records - GetTracksUseCase and the row subtitle see the survivors, with no note in the ordinary case`() = runTest(dispatcher) {
        seedTrack()

        val track = GetTracksUseCase(trackRepository)().getOrThrow().single()
        assertEquals(3, track.points.size)
        // 2 of 5 is the rule working, not a large exclusion: the row reads exactly as it always has.
        assertEquals("3 points · recording", trackSubtitle(track))
        assertDiskUnchanged()
    }

    /**
     * Re-scoped by the GPX full-record export dispatch (CLAUDE.md stop-and-ask A: this is the test
     * the dispatch's report identified as needing to go red once excluded points ride in the file —
     * confirmed here by the diff against the test this replaced, not silenced). `<trkseg>` is still
     * exactly the survivors, unchanged from before this dispatch — ruling 1 (in-app display, and
     * what `<trkseg>` renders, stays filtered). What's new is `<trk><extensions>`: the full raw
     * five-point sequence, including the two network fixes `<trkseg>` still excludes, each carrying
     * the same verdict [com.forager.app.domain.isNetworkProviderFix] would give it — format B1
     * (owner ruling), exercised through the real repository read
     * ([RoomTrackRepository.getFullRecord]), not a hand-built fixture.
     */
    @Test
    fun `GPX export writes the survivors to trkseg, and the full raw record with verdicts to extensions`() = runTest(dispatcher) {
        seedTrack()
        val waypointRepository = RoomWaypointRepository(database.waypointDao())
        val origin = Waypoint(
            id = "w-origin",
            lat = whole1.lat,
            lng = whole1.lng,
            altitude = null,
            name = "Start",
            note = "",
            createdAtEpochMillis = whole1.timestampEpochMillis,
            trackId = "t1",
            designation = WaypointDesignation.ORIGIN,
        )
        waypointRepository.save(origin).getOrThrow()
        val track = GetTracksUseCase(trackRepository)().getOrThrow().single()
        val fullRecord = trackRepository.getFullRecord("t1").getOrThrow()
        val dir = File(ApplicationProvider.getApplicationContext<Application>().cacheDir, "gpx-test").apply { mkdirs() }

        val gpx = TrackGpxExporter(dir).write(track, fullRecord = fullRecord, waypoints = listOf(origin)).readText()

        // <trkseg>: exactly the survivors, exactly as before this dispatch.
        val trkseg = gpx.substringAfter("<trkseg>").substringBefore("</trkseg>")
        assertEquals(3, Regex("<trkpt ").findAll(trkseg).count())
        assertFalse("the network fixes' latitude must not appear in trkseg", trkseg.contains("45.03"))
        assertFalse(trkseg.contains(".500Z"))
        assertFalse(trkseg.contains(".250Z"))
        assertTrue(trkseg.contains("1970-01-01T00:00:01Z") && trkseg.contains("1970-01-01T00:00:11Z"))

        // <trk><extensions>: the full raw sequence, all five stored points, with a verdict each.
        val extensions = gpx.substringAfter("<extensions>").substringBefore("</extensions>")
        assertTrue(gpx.contains("authoritative=\"true\""))
        assertEquals(5, Regex("<forager:point ").findAll(extensions).count())
        assertTrue("the raw record must still carry the network fixes' latitude", extensions.contains("45.03"))
        assertEquals(2, Regex("kept=\"false\"").findAll(extensions).count())
        assertEquals(3, Regex("kept=\"true\"").findAll(extensions).count())
        assertTrue(extensions.contains("timeEpochMillis=\"3500\""))
        assertTrue(extensions.contains("timeEpochMillis=\"8250\""))

        // <wpt><extensions>: what standard GPX has no element for.
        assertTrue(gpx.contains("trackId=\"t1\""))
        assertTrue(gpx.contains("designation=\"ORIGIN\""))

        assertDiskUnchanged()
    }

    /**
     * GPX rule-provenance dispatch (owner ruling, 2026-09-09), through the same real chain as the
     * test above — [RoomTrackRepository.getFullRecord] into [TrackGpxExporter.write], no hand-built
     * record and no hand-built document — because the two attributes are written at two different
     * places (the exporter stamps the rule set; the repository names each excluded point's rule)
     * and only the file they both land in can show that they agree.
     *
     * Why the record-level `rule` is the load-bearing half: it is what makes a file's **kept**
     * points unambiguous. `excludedByRule` alone would still leave every kept point in an old file
     * meaning "passed whatever rules were running then", with no way to say how many that was.
     *
     * The literals here are written out by hand, not read from [com.forager.app.domain.NETWORK_FIX_EXCLUSION_RULES] — a
     * test that takes its expectation from the code under test passes for any value that code holds.
     */
    @Test
    fun `the exported file names the rule set in force and the rule that excluded each point`() = runTest(dispatcher) {
        seedTrack()
        val waypointRepository = RoomWaypointRepository(database.waypointDao())
        val origin = Waypoint(
            id = "w-origin",
            lat = whole1.lat,
            lng = whole1.lng,
            altitude = null,
            name = "Start",
            note = "",
            createdAtEpochMillis = whole1.timestampEpochMillis,
            trackId = "t1",
            designation = WaypointDesignation.ORIGIN,
        )
        waypointRepository.save(origin).getOrThrow()
        val track = GetTracksUseCase(trackRepository)().getOrThrow().single()
        val fullRecord = trackRepository.getFullRecord("t1").getOrThrow()
        val dir = File(ApplicationProvider.getApplicationContext<Application>().cacheDir, "gpx-rule-test").apply { mkdirs() }

        val gpx = TrackGpxExporter(dir).write(track, fullRecord = fullRecord, waypoints = listOf(origin)).readText()

        val recordBlockTag = gpx.substringAfter("<forager:fullRecord").substringBefore(">")
        assertTrue("the record block must name the rule set in force, got:$recordBlockTag", recordBlockTag.contains("rule=\"timestampMillisNonZero\""))

        val points = Regex("<forager:point [^>]*/>").findAll(gpx).map { it.value }.toList()
        assertEquals(5, points.size)
        val excluded = points.filter { it.contains("kept=\"false\"") }
        val kept = points.filter { it.contains("kept=\"true\"") }
        assertEquals(2, excluded.size)
        assertEquals(3, kept.size)
        assertTrue("every excluded point names the rule that caught it", excluded.all { it.contains("excludedByRule=\"timestampMillisNonZero\"") })
        assertTrue("a kept point passed everything, so there is no rule to name", kept.none { it.contains("excludedByRule") })

        // The waypoint's own id — present in the store all along, absent from the file until now.
        assertTrue("the waypoint's id must reach the file", gpx.contains("id=\"w-origin\""))

        assertDiskUnchanged()
    }

    @Test
    fun `the derived trip, the entry map, and trip-report coverage all see the survivors`() = runTest(dispatcher) {
        seedTrack()
        val logRepository = RoomMushroomLogRepository(database.mushroomLogDao())
        val derivedTrip = GetDerivedTripUseCase(
            mushroomLogRepository = logRepository,
            trackRepository = trackRepository,
            waypointRepository = RoomWaypointRepository(database.waypointDao()),
            offlineRegionDayIndex = RoomOfflineRegionDayIndex(database.offlineRegionDao()),
        )(LocalDate.of(1970, 1, 1), ZoneOffset.UTC).getOrThrow()
        assertEquals(survivors, derivedTrip.tracks.single().points)

        val entry = CartographyEntry.draft(id = "e1", date = LocalDate.of(1970, 1, 1), updatedAtEpochMillis = 0L).copy(
            trackDecisions = listOf(TrackDecision(trackId = "t1", name = null, distanceMeters = 0.0, durationMillis = 0L, pointCount = 0, kept = true)),
        )
        val mapData = GetCartographyEntryMapDataUseCase(trackRepository, logRepository)(entry, emptyList())
        assertEquals(survivors.map { it.lat }, mapData.trackPolylines.single().map { it.lat })

        // A region whose footprint holds only the two excluded points: coverage must not count them.
        val aroundTheNetworkFixes = object : OfflineMapRepository {
            override suspend fun download(name: String, region: Region, onProgress: (Int, Int) -> Unit): Result<OfflineRegionSummary> = Result.failure(UnsupportedOperationException())
            override suspend fun deleteRegion(id: Long): Result<Unit> = Result.failure(UnsupportedOperationException())
            override suspend fun listRegions(): Result<List<OfflineRegionSummary>> = Result.success(
                listOf(OfflineRegionSummary(id = 1L, name = "phantom", region = Region(lat = 45.030, lng = -122.0, radiusKm = 1), minZoom = 10.0, maxZoom = 15.0, tileCount = 1, sizeBytes = 1L, createdAtEpochMillis = 0L)),
            )
        }
        assertEquals(emptyList<OfflineRegionSummary>(), GetTripReportOfflineRegionsUseCase(aroundTheNetworkFixes)(derivedTrip).getOrThrow())
        assertDiskUnchanged()
    }

    @Test
    fun `the live breadcrumb poll sees the survivors`() = runTest(dispatcher) {
        val waypointRepository = RoomWaypointRepository(database.waypointDao())
        val fixedTime = CurrentTimeProvider { 1_000L }
        var waypointIds = 0
        val vm = TrackRecordingViewModel(
            trackRepository = trackRepository,
            startTrack = StartTrackUseCase(trackRepository, currentTime = fixedTime, idGenerator = { "t1" }),
            getWaypoints = GetWaypointsUseCase(waypointRepository),
            createWaypoint = CreateWaypointUseCase(waypointRepository, currentTime = fixedTime, idGenerator = { "wp-${++waypointIds}" }),
            deleteWaypoint = DeleteWaypointUseCase(waypointRepository),
            computeReturnToStart = ComputeReturnToStartUseCase(),
            detectOffTrack = DetectOffTrackUseCase(),
            locationTracker = object : LocationTracker { override val fixes: Flow<LocationFix> = emptyFlow() },
            getTracks = GetTracksUseCase(trackRepository),
            alertDelivery = AlertDelivery { _: Alert -> },
            alertAudibility = object : AlertAudibility {
                override fun current() = AlertAudibilityState(RingerMode.NORMAL, doNotDisturbOn = false, notificationsEnabled = true)
            },
            currentTime = fixedTime,
            zone = ZoneOffset.UTC,
        )
        try {
            vm.startRecording()
            runCurrent()
            trackRepository.appendPoints("t1", stored).getOrThrow()
            advanceTimeBy(15_000L)
            runCurrent()

            assertEquals(survivors, vm.uiState.value.breadcrumbPoints)
            assertDiskUnchanged()
        } finally {
            vm.stopRecording()
        }
    }
}
