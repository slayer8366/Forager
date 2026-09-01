package com.forager.app.ui.track

import com.forager.app.domain.ComputeReturnToStartUseCase
import com.forager.app.domain.CreateWaypointUseCase
import com.forager.app.domain.CurrentTimeProvider
import com.forager.app.domain.DeleteWaypointUseCase
import com.forager.app.domain.DetectOffTrackUseCase
import com.forager.app.domain.GetTracksUseCase
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
        offTrackAlertClock: CurrentTimeProvider = fixedTime,
    ) = TrackRecordingViewModel(
        trackRepository = trackRepository,
        startTrack = StartTrackUseCase(trackRepository, currentTime = fixedTime, idGenerator = { "track-1" }),
        getWaypoints = GetWaypointsUseCase(waypointRepository),
        createWaypoint = CreateWaypointUseCase(waypointRepository, currentTime = fixedTime, idGenerator = { "waypoint-1" }),
        deleteWaypoint = DeleteWaypointUseCase(waypointRepository),
        computeReturnToStart = ComputeReturnToStartUseCase(),
        detectOffTrack = DetectOffTrackUseCase(),
        locationTracker = locationTracker,
        getTracks = GetTracksUseCase(trackRepository),
        currentTime = offTrackAlertClock,
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
    fun `a failed start surfaces an error and leaves no active track, without the exception's own message`() = runTest(dispatcher) {
        val vm = viewModel(FailingTrackRepository())

        vm.startRecording()
        runCurrent()

        assertNull(vm.uiState.value.activeTrack)
        // Error-presentation spec's absolute rule: FailingTrackRepository.create() throws
        // RuntimeException("boom") — that text must never reach state, however recognizable it is.
        assertEquals("Couldn't start recording.", vm.uiState.value.startRecordingErrorMessage)
    }

    @Test
    fun `a permission-denied start is reported without ever setting an active track`() = runTest(dispatcher) {
        val trackRepository = InMemoryTrackRepository()
        val vm = viewModel(trackRepository)

        vm.onStartRecordingPermissionDenied("Track recording needs location access.")

        assertNull(vm.uiState.value.activeTrack)
        assertFalse(vm.uiState.value.isRecording)
        assertEquals("Track recording needs location access.", vm.uiState.value.startRecordingErrorMessage)
        assertTrue("the Track row must never be created when the start is refused", trackRepository.createdTrackIds.isEmpty())
    }

    @Test
    fun `a permission-denied report after an active recording rolls it back, matching stopRecording`() = runTest(dispatcher) {
        val trackRepository = InMemoryTrackRepository()
        val vm = viewModel(trackRepository)
        vm.startRecording()
        runCurrent()
        assertTrue(vm.uiState.value.isRecording)

        // Mirrors MainActivity's own sequence for the narrow permission-revoked-mid-flight case:
        // report the reason, then roll the active track back — see
        // onStartRecordingPermissionDenied's own doc comment for why the caller does both.
        vm.onStartRecordingPermissionDenied("Track recording needs location access.")
        vm.stopRecording()

        assertNull(vm.uiState.value.activeTrack)
        assertFalse(vm.uiState.value.isRecording)
        assertEquals("Track recording needs location access.", vm.uiState.value.startRecordingErrorMessage)
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

    /**
     * These two tests are the coarse regression guard for the return-to-vehicle control while
     * `AvailabilityScreenMapIconStackTest`'s own three Compose-semantics-layer tests for the same
     * control sit `@Ignore`d (see that file's own `retryClick` comment and
     * `docs/audits/2026-08-30-return-to-vehicle-semantics-click-noop.md`). They were not added for
     * this — both already existed and already covered the precondition
     * (`startReturn is a no-op with nothing recording`, i.e. gating equivalent to the UI's
     * `enabled = isRecording`) and the toggle-back path
     * (`startReturn marks returning..., stopReturn clears it...`) — but they're the tests that fill
     * the gap while the ignored ones are down, so noting it here rather than leaving that implicit.
     *
     * **What this does not cover:** Compose's click-to-callback wiring — whether
     * `MapBarIconButton`'s `.clickable(...)` on the return-to-vehicle row actually reaches
     * `onToggleReturning` when tapped. These call `startReturn()`/`stopReturn()` directly, bypassing
     * the Compose semantics layer entirely. That's exactly the gap the three ignored tests exist to
     * close once the harness issue is root-caused.
     */
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

    /**
     * Field-test dispatch item 4: [TrackRecordingUiState.offTrackAlertId] is what `MainActivity`
     * observes to post a notification and vibrate — this suite can't drive an Activity, so it
     * asserts the counter [MainActivity] reacts to instead, the same boundary
     * [AvailabilityViewModelLocateMeTest] draws for its own permission-dialog side effects.
     */
    @Test
    fun `going off-track bumps offTrackAlertId once, not once per fix`() = runTest(dispatcher) {
        val trackRepository = InMemoryTrackRepository()
        val vm = viewModel(trackRepository, offTrackAlertClock = CurrentTimeProvider { 1_000L })

        vm.startRecording()
        runCurrent()
        trackRepository.appendPoints("track-1", listOf(point(lat = 45.0, lng = -122.0, t = 1_000L)))
        advanceTimeBy(POLL_INTERVAL_MILLIS)
        runCurrent()
        vm.startReturn()
        assertEquals(0, vm.uiState.value.offTrackAlertId)

        vm.returnToStart(point(lat = 45.001, lng = -122.0, t = 2_000L))
        vm.returnToStart(point(lat = 45.002, lng = -122.0, t = 3_000L))
        vm.returnToStart(point(lat = 45.003, lng = -122.0, t = 4_000L))
        assertTrue(vm.uiState.value.isOffTrack)
        assertEquals(1, vm.uiState.value.offTrackAlertId)

        // Still off-track (net distance keeps increasing) on the very next fix, same clock instant
        // — the cooldown, not the heuristic, is what must keep this from bumping again immediately.
        vm.returnToStart(point(lat = 45.004, lng = -122.0, t = 5_000L))
        assertEquals(1, vm.uiState.value.offTrackAlertId)

        vm.stopRecording()
    }

    @Test
    fun `a sustained drift alerts again once the cooldown elapses`() = runTest(dispatcher) {
        val trackRepository = InMemoryTrackRepository()
        var nowMillis = 1_000L
        val vm = viewModel(trackRepository, offTrackAlertClock = CurrentTimeProvider { nowMillis })

        vm.startRecording()
        runCurrent()
        trackRepository.appendPoints("track-1", listOf(point(lat = 45.0, lng = -122.0, t = 1_000L)))
        advanceTimeBy(POLL_INTERVAL_MILLIS)
        runCurrent()
        vm.startReturn()

        vm.returnToStart(point(lat = 45.001, lng = -122.0, t = 2_000L))
        vm.returnToStart(point(lat = 45.002, lng = -122.0, t = 3_000L))
        vm.returnToStart(point(lat = 45.003, lng = -122.0, t = 4_000L))
        assertEquals(1, vm.uiState.value.offTrackAlertId)

        // Just short of the cooldown: still just the one alert.
        nowMillis += OFF_TRACK_ALERT_COOLDOWN_MILLIS - 1
        vm.returnToStart(point(lat = 45.004, lng = -122.0, t = 5_000L))
        assertEquals(1, vm.uiState.value.offTrackAlertId)

        // Cooldown elapsed, and the drift continues: a second, real reminder.
        nowMillis += 1
        vm.returnToStart(point(lat = 45.005, lng = -122.0, t = 6_000L))
        assertEquals(2, vm.uiState.value.offTrackAlertId)

        vm.stopRecording()
    }

    @Test
    fun `staying on track never bumps offTrackAlertId`() = runTest(dispatcher) {
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

        assertEquals(0, vm.uiState.value.offTrackAlertId)
        vm.stopRecording()
    }

    @Test
    fun `stopReturn resets the cooldown so a later return attempt can alert immediately`() = runTest(dispatcher) {
        val trackRepository = InMemoryTrackRepository()
        val vm = viewModel(trackRepository, offTrackAlertClock = CurrentTimeProvider { 1_000L })

        vm.startRecording()
        runCurrent()
        trackRepository.appendPoints("track-1", listOf(point(lat = 45.0, lng = -122.0, t = 1_000L)))
        advanceTimeBy(POLL_INTERVAL_MILLIS)
        runCurrent()
        vm.startReturn()
        vm.returnToStart(point(lat = 45.001, lng = -122.0, t = 2_000L))
        vm.returnToStart(point(lat = 45.002, lng = -122.0, t = 3_000L))
        vm.returnToStart(point(lat = 45.003, lng = -122.0, t = 4_000L))
        assertEquals(1, vm.uiState.value.offTrackAlertId)

        vm.stopReturn()
        vm.startReturn()
        // Same fixed clock instant as the first alert — without the cooldown reset on stopReturn(),
        // this would be blocked exactly like the immediate-repeat case above.
        vm.returnToStart(point(lat = 45.001, lng = -122.0, t = 5_000L))
        vm.returnToStart(point(lat = 45.002, lng = -122.0, t = 6_000L))
        vm.returnToStart(point(lat = 45.003, lng = -122.0, t = 7_000L))

        assertEquals(2, vm.uiState.value.offTrackAlertId)
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

        /** Mirrors TrackRecordingViewModel's own private constant of the same name — see its own doc comment. */
        const val OFF_TRACK_ALERT_COOLDOWN_MILLIS = 120_000L
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
    override suspend fun getForDay(dayStartInclusiveEpochMillis: Long, dayEndExclusiveEpochMillis: Long): Result<List<Track>> =
        Result.success(
            tracks.values.filter { track ->
                track.startedAtEpochMillis < dayEndExclusiveEpochMillis &&
                    (track.endedAtEpochMillis == null || track.endedAtEpochMillis >= dayStartInclusiveEpochMillis)
            },
        )

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
    override suspend fun getForDay(dayStartInclusiveEpochMillis: Long, dayEndExclusiveEpochMillis: Long): Result<List<Track>> =
        Result.success(emptyList())
    override suspend fun create(track: Track): Result<Unit> = Result.failure(RuntimeException("boom"))
    override suspend fun appendPoints(trackId: String, points: List<TrackPoint>): Result<Unit> = Result.success(Unit)
    override suspend fun end(trackId: String, endedAtEpochMillis: Long): Result<Unit> = Result.success(Unit)
    override suspend fun delete(id: String): Result<Unit> = Result.success(Unit)
}

private class FakeWaypointRepository : com.forager.app.domain.WaypointRepository {
    private val waypoints = mutableMapOf<String, Waypoint>()

    override suspend fun getAll(): Result<List<Waypoint>> = Result.success(waypoints.values.toList())
    override suspend fun getForDay(dayStartInclusiveEpochMillis: Long, dayEndExclusiveEpochMillis: Long): Result<List<Waypoint>> =
        Result.success(
            waypoints.values.filter { it.createdAtEpochMillis in dayStartInclusiveEpochMillis until dayEndExclusiveEpochMillis },
        )
    override suspend fun save(waypoint: Waypoint): Result<Unit> {
        waypoints[waypoint.id] = waypoint
        return Result.success(Unit)
    }
    override suspend fun delete(id: String): Result<Unit> {
        waypoints.remove(id)
        return Result.success(Unit)
    }
}
