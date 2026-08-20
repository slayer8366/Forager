package com.forager.app.ui.track

import com.forager.app.domain.ComputeReturnToStartUseCase
import com.forager.app.domain.CreateWaypointUseCase
import com.forager.app.domain.CurrentTimeProvider
import com.forager.app.domain.DeleteWaypointUseCase
import com.forager.app.domain.DetectOffTrackUseCase
import com.forager.app.domain.GetWaypointsUseCase
import com.forager.app.domain.LocationFix
import com.forager.app.domain.LocationTracker
import com.forager.app.domain.StartTrackUseCase
import com.forager.app.domain.TrackRepository
import com.forager.app.domain.model.Track
import com.forager.app.domain.model.TrackPoint
import com.forager.app.domain.model.TrackRecordingMode
import com.forager.app.domain.model.Waypoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The real [TrackRecordingViewModel] over an in-memory [TrackRepository] fake, driven through its
 * real public methods — the same "real ViewModel over fakes" style
 * [com.forager.app.ui.availability.AvailabilityViewModelPlannedTripsTest] uses.
 *
 * [beginPolling] runs an unbounded `while (true) { ...; delay(...) }` loop while recording, so
 * every test here uses [runCurrent]/[advanceTimeBy] rather than [advanceUntilIdle] once recording
 * has started — draining an infinite loop to "idle" never actually finishes. Every test that starts
 * a recording stops it before returning, so no job is left dangling when the test scope tears down.
 */
class TrackRecordingViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val fixedTime = CurrentTimeProvider { 1_000L }

    private fun viewModel(
        trackRepository: TrackRepository = InMemoryTrackRepository(),
        waypointRepository: FakeWaypointRepository = FakeWaypointRepository(),
        // Every test but the one that exercises beginLocationTracking() itself drives
        // returnToStart() directly rather than through a collected fix — see
        // TrackRecordingViewModel's own doc comment for why that's equivalent — so an empty
        // stream is enough there; nothing needs beginLocationTracking() to ever actually emit.
        locationTracker: LocationTracker = NoOpLocationTracker(),
    ) = TrackRecordingViewModel(
        trackRepository = trackRepository,
        startTrack = StartTrackUseCase(trackRepository, currentTime = fixedTime, idGenerator = { "track-1" }),
        getWaypoints = GetWaypointsUseCase(waypointRepository),
        createWaypoint = CreateWaypointUseCase(waypointRepository, currentTime = fixedTime, idGenerator = { "waypoint-1" }),
        deleteWaypoint = DeleteWaypointUseCase(waypointRepository),
        computeReturnToStart = ComputeReturnToStartUseCase(),
        detectOffTrack = DetectOffTrackUseCase(),
        locationTracker = locationTracker,
    )

    @Test
    fun `starting a recording creates a track and sets active state`() = runTest(dispatcher) {
        val trackRepository = InMemoryTrackRepository()
        val vm = viewModel(trackRepository)

        vm.startRecording(TrackRecordingMode.HIGH_ACCURACY)
        runCurrent()

        val active = vm.uiState.value.activeTrack
        assertEquals("track-1", active?.trackId)
        assertEquals(TrackRecordingMode.HIGH_ACCURACY, active?.mode)
        assertTrue(vm.uiState.value.isRecording)
        assertNull(vm.uiState.value.startRecordingErrorMessage)
        assertEquals(listOf("track-1"), trackRepository.createdTrackIds)

        vm.stopRecording()
    }

    @Test
    fun `a failed start surfaces an error and leaves no active track`() = runTest(dispatcher) {
        val vm = viewModel(FailingTrackRepository())

        vm.startRecording()
        runCurrent()

        assertNull(vm.uiState.value.activeTrack)
        assertEquals("boom", vm.uiState.value.startRecordingErrorMessage)
    }

    @Test
    fun `stopping clears active state and the poll loop stops scheduling further work`() = runTest(dispatcher) {
        val trackRepository = InMemoryTrackRepository()
        val vm = viewModel(trackRepository)

        vm.startRecording()
        runCurrent()
        vm.stopRecording()

        assertNull(vm.uiState.value.activeTrack)
        assertTrue(vm.uiState.value.breadcrumbPoints.isEmpty())

        // If the poll job weren't actually cancelled, this would hang forever the same way an
        // unguarded advanceUntilIdle() would with it still running.
        advanceUntilIdle()
    }

    @Test
    fun `breadcrumb points refresh on each poll while recording`() = runTest(dispatcher) {
        val trackRepository = InMemoryTrackRepository()
        val vm = viewModel(trackRepository)

        vm.startRecording()
        runCurrent()
        assertTrue(vm.uiState.value.breadcrumbPoints.isEmpty())

        trackRepository.appendPoints("track-1", listOf(point(lat = 45.0, t = 1_000L)))
        advanceTimeBy(POLL_INTERVAL_MILLIS)
        runCurrent()

        assertEquals(listOf(45.0), vm.uiState.value.breadcrumbPoints.map { it.lat })

        vm.stopRecording()
    }

    @Test
    fun `return to start uses the earliest breadcrumb point as the start`() = runTest(dispatcher) {
        val trackRepository = InMemoryTrackRepository()
        val vm = viewModel(trackRepository)

        vm.startRecording()
        runCurrent()
        trackRepository.appendPoints(
            "track-1",
            listOf(point(lat = 45.0, lng = -122.0, t = 1_000L), point(lat = 45.01, lng = -122.0, t = 2_000L)),
        )
        advanceTimeBy(POLL_INTERVAL_MILLIS)
        runCurrent()

        val current = point(lat = 45.05, lng = -122.0, t = 3_000L)
        val info = vm.returnToStart(current)

        assertEquals(180.0, info?.bearingDegrees ?: -1.0, 0.01) // due south, back toward the first point
        vm.stopRecording()
    }

    @Test
    fun `return to start is null with no active recording`() = runTest(dispatcher) {
        val vm = viewModel()

        val info = vm.returnToStart(point(lat = 45.0, t = 1_000L))

        assertNull(info)
    }

    @Test
    fun `starting a recording collects live fixes and updates returnToStart reactively, without a direct call`() = runTest(dispatcher) {
        val trackRepository = InMemoryTrackRepository()
        val fixes = MutableSharedFlow<LocationFix>()
        val vm = viewModel(trackRepository, locationTracker = FakeLocationTracker(fixes))

        vm.startRecording()
        runCurrent()
        trackRepository.appendPoints("track-1", listOf(point(lat = 45.0, lng = -122.0, t = 1_000L)))
        advanceTimeBy(POLL_INTERVAL_MILLIS)
        runCurrent()
        assertNull(vm.uiState.value.returnToStart)

        fixes.emit(LocationFix.Update(lat = 45.001, lng = -122.0, altitude = null, accuracyMeters = null, timestampEpochMillis = 2_000L))
        runCurrent()

        assertEquals(180.0, vm.uiState.value.returnToStart?.bearingDegrees ?: -1.0, 0.01)
        vm.stopRecording()
    }

    @Test
    fun `stopping the recording stops collecting fixes and clears returnToStart`() = runTest(dispatcher) {
        val trackRepository = InMemoryTrackRepository()
        val fixes = MutableSharedFlow<LocationFix>()
        val vm = viewModel(trackRepository, locationTracker = FakeLocationTracker(fixes))

        vm.startRecording()
        runCurrent()
        trackRepository.appendPoints("track-1", listOf(point(lat = 45.0, lng = -122.0, t = 1_000L)))
        advanceTimeBy(POLL_INTERVAL_MILLIS)
        runCurrent()
        fixes.emit(LocationFix.Update(lat = 45.001, lng = -122.0, altitude = null, accuracyMeters = null, timestampEpochMillis = 2_000L))
        runCurrent()
        assertEquals(180.0, vm.uiState.value.returnToStart?.bearingDegrees ?: -1.0, 0.01)

        vm.stopRecording()

        assertNull(vm.uiState.value.returnToStart)
        // Same shape as stopRecording()'s own poll-job test: if the location job weren't actually
        // cancelled, its still-running collect() would keep this scope non-idle and this would hang.
        advanceUntilIdle()
    }

    @Test
    fun `starting a recording does not mark the walker as returning`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.startRecording()
        runCurrent()

        assertFalse(vm.uiState.value.isReturning)
        assertFalse(vm.uiState.value.isOffTrack)
        vm.stopRecording()
    }

    @Test
    fun `startReturn is a no-op with nothing recording`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.startReturn()

        assertFalse(vm.uiState.value.isReturning)
    }

    @Test
    fun `startReturn marks returning while actively recording, stopReturn clears it without stopping the recording`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.startRecording()
        runCurrent()
        vm.startReturn()

        assertTrue(vm.uiState.value.isReturning)

        vm.stopReturn()

        assertFalse(vm.uiState.value.isReturning)
        assertFalse(vm.uiState.value.isOffTrack)
        assertTrue(vm.uiState.value.isRecording)
        vm.stopRecording()
    }

    @Test
    fun `moving steadily away from the start while returning sets off-track`() = runTest(dispatcher) {
        val trackRepository = InMemoryTrackRepository()
        val vm = viewModel(trackRepository)

        vm.startRecording()
        runCurrent()
        trackRepository.appendPoints("track-1", listOf(point(lat = 45.0, lng = -122.0, t = 1_000L)))
        advanceTimeBy(POLL_INTERVAL_MILLIS)
        runCurrent()
        vm.startReturn()

        // Each step ~111m further north of the start point — well past the 25m/3-reading
        // net-increase threshold DetectOffTrackUseCase uses.
        vm.returnToStart(point(lat = 45.001, lng = -122.0, t = 2_000L))
        vm.returnToStart(point(lat = 45.002, lng = -122.0, t = 3_000L))
        vm.returnToStart(point(lat = 45.003, lng = -122.0, t = 4_000L))

        assertTrue(vm.uiState.value.isOffTrack)
        vm.stopRecording()
    }

    @Test
    fun `moving steadily toward the start while returning stays on track`() = runTest(dispatcher) {
        val trackRepository = InMemoryTrackRepository()
        val vm = viewModel(trackRepository)

        vm.startRecording()
        runCurrent()
        trackRepository.appendPoints("track-1", listOf(point(lat = 45.0, lng = -122.0, t = 1_000L)))
        advanceTimeBy(POLL_INTERVAL_MILLIS)
        runCurrent()
        vm.startReturn()

        vm.returnToStart(point(lat = 45.003, lng = -122.0, t = 2_000L))
        vm.returnToStart(point(lat = 45.002, lng = -122.0, t = 3_000L))
        vm.returnToStart(point(lat = 45.001, lng = -122.0, t = 4_000L))

        assertFalse(vm.uiState.value.isOffTrack)
        vm.stopRecording()
    }

    @Test
    fun `moving away from the start does not set off-track unless actively returning`() = runTest(dispatcher) {
        val trackRepository = InMemoryTrackRepository()
        val vm = viewModel(trackRepository)

        vm.startRecording()
        runCurrent()
        trackRepository.appendPoints("track-1", listOf(point(lat = 45.0, lng = -122.0, t = 1_000L)))
        advanceTimeBy(POLL_INTERVAL_MILLIS)
        runCurrent()

        // No startReturn() call — this is ordinary outbound travel.
        vm.returnToStart(point(lat = 45.001, lng = -122.0, t = 2_000L))
        vm.returnToStart(point(lat = 45.002, lng = -122.0, t = 3_000L))
        vm.returnToStart(point(lat = 45.003, lng = -122.0, t = 4_000L))

        assertFalse(vm.uiState.value.isOffTrack)
        vm.stopRecording()
    }

    @Test
    fun `stopping the recording clears returning and off-track state`() = runTest(dispatcher) {
        val trackRepository = InMemoryTrackRepository()
        val vm = viewModel(trackRepository)

        vm.startRecording()
        runCurrent()
        trackRepository.appendPoints("track-1", listOf(point(lat = 45.0, lng = -122.0, t = 1_000L)))
        advanceTimeBy(POLL_INTERVAL_MILLIS)
        runCurrent()
        vm.startReturn()
        vm.returnToStart(point(lat = 45.001, lng = -122.0, t = 2_000L))
        vm.returnToStart(point(lat = 45.002, lng = -122.0, t = 3_000L))
        vm.returnToStart(point(lat = 45.003, lng = -122.0, t = 4_000L))
        assertTrue(vm.uiState.value.isOffTrack)

        vm.stopRecording()

        assertFalse(vm.uiState.value.isReturning)
        assertFalse(vm.uiState.value.isOffTrack)
    }

    @Test
    fun `waypoints load on init and adding one refreshes the list`() = runTest(dispatcher) {
        val waypointRepository = FakeWaypointRepository()
        val vm = viewModel(waypointRepository = waypointRepository)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.waypoints.isEmpty())

        vm.addWaypoint(lat = 45.0, lng = -122.0, name = "Big oak")
        advanceUntilIdle()

        val waypoint = vm.uiState.value.waypoints.single()
        assertEquals("Big oak", waypoint.name)
        assertEquals("waypoint-1", waypoint.id)
        assertNull(vm.uiState.value.waypointsErrorMessage)
    }

    @Test
    fun `removing a waypoint refreshes the list`() = runTest(dispatcher) {
        val waypointRepository = FakeWaypointRepository()
        waypointRepository.save(Waypoint(id = "waypoint-1", lat = 45.0, lng = -122.0, altitude = null, name = "Big oak", note = "", createdAtEpochMillis = 1_000L))
        val vm = viewModel(waypointRepository = waypointRepository)
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.waypoints.size)

        vm.removeWaypoint("waypoint-1")
        advanceUntilIdle()

        assertTrue(vm.uiState.value.waypoints.isEmpty())
    }

    private fun point(lat: Double, lng: Double = -122.0, t: Long) =
        TrackPoint(lat = lat, lng = lng, altitude = null, accuracyMeters = null, timestampEpochMillis = t)

    private companion object {
        const val POLL_INTERVAL_MILLIS = 15_000L
    }
}

private class NoOpLocationTracker : LocationTracker {
    override val fixes: Flow<LocationFix> = emptyFlow()
}

private class FakeLocationTracker(override val fixes: MutableSharedFlow<LocationFix>) : LocationTracker

private class InMemoryTrackRepository : TrackRepository {
    private val tracks = mutableMapOf<String, Track>()
    val createdTrackIds = mutableListOf<String>()

    override suspend fun getAll(): Result<List<Track>> = Result.success(tracks.values.toList())
    override suspend fun getById(id: String): Result<Track?> = Result.success(tracks[id])

    override suspend fun create(track: Track): Result<Unit> {
        tracks[track.id] = track
        createdTrackIds += track.id
        return Result.success(Unit)
    }

    override suspend fun appendPoints(trackId: String, points: List<TrackPoint>): Result<Unit> {
        val existing = tracks[trackId] ?: return Result.failure(IllegalStateException("no such track"))
        tracks[trackId] = existing.copy(points = existing.points + points)
        return Result.success(Unit)
    }

    override suspend fun end(trackId: String, endedAtEpochMillis: Long): Result<Unit> {
        val existing = tracks[trackId] ?: return Result.failure(IllegalStateException("no such track"))
        tracks[trackId] = existing.copy(endedAtEpochMillis = endedAtEpochMillis)
        return Result.success(Unit)
    }

    override suspend fun delete(id: String): Result<Unit> {
        tracks.remove(id)
        return Result.success(Unit)
    }
}

private class FailingTrackRepository : TrackRepository {
    override suspend fun getAll(): Result<List<Track>> = Result.success(emptyList())
    override suspend fun getById(id: String): Result<Track?> = Result.success(null)
    override suspend fun create(track: Track): Result<Unit> = Result.failure(RuntimeException("boom"))
    override suspend fun appendPoints(trackId: String, points: List<TrackPoint>): Result<Unit> = Result.success(Unit)
    override suspend fun end(trackId: String, endedAtEpochMillis: Long): Result<Unit> = Result.success(Unit)
    override suspend fun delete(id: String): Result<Unit> = Result.success(Unit)
}

private class FakeWaypointRepository : com.forager.app.domain.WaypointRepository {
    private val waypoints = mutableMapOf<String, Waypoint>()

    override suspend fun getAll(): Result<List<Waypoint>> = Result.success(waypoints.values.toList())
    override suspend fun save(waypoint: Waypoint): Result<Unit> {
        waypoints[waypoint.id] = waypoint
        return Result.success(Unit)
    }
    override suspend fun delete(id: String): Result<Unit> {
        waypoints.remove(id)
        return Result.success(Unit)
    }
}
