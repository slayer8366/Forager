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

    @Test
    fun `GPX export writes the survivors only - a future export can no longer show a sub-second point`() = runTest(dispatcher) {
        seedTrack()
        val track = GetTracksUseCase(trackRepository)().getOrThrow().single()
        val dir = File(ApplicationProvider.getApplicationContext<Application>().cacheDir, "gpx-test").apply { mkdirs() }

        val gpx = TrackGpxExporter(dir).write(track).readText()

        assertEquals(3, Regex("<trkpt ").findAll(gpx).count())
        assertFalse("the network fixes' latitude must not be exported", gpx.contains("45.03"))
        assertFalse(gpx.contains(".500Z"))
        assertFalse(gpx.contains(".250Z"))
        assertTrue(gpx.contains("1970-01-01T00:00:01Z") && gpx.contains("1970-01-01T00:00:11Z"))
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
