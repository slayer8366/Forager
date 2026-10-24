package com.zynergylabs.forager.app.ui.track

import com.zynergylabs.forager.app.domain.Alert
import com.zynergylabs.forager.app.domain.AlertAudibility
import com.zynergylabs.forager.app.domain.AlertAudibilityState
import com.zynergylabs.forager.app.domain.AlertDelivery
import com.zynergylabs.forager.app.domain.ComputeReturnToStartUseCase
import com.zynergylabs.forager.app.domain.CreateWaypointUseCase
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.DeleteTrackUseCase
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.InMemoryKeptTrackPaths
import com.zynergylabs.forager.app.domain.DeleteWaypointUseCase
import com.zynergylabs.forager.app.domain.ReturnWatch
import com.zynergylabs.forager.app.domain.HopBand
import com.zynergylabs.forager.app.domain.RouteHome
import com.zynergylabs.forager.app.domain.RouteWithheldReason
import com.zynergylabs.forager.app.domain.routeHome
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.AbandonedTrackSweepOnce
import com.zynergylabs.forager.app.domain.EndAbandonedTracksUseCase
import com.zynergylabs.forager.app.domain.GetTrackOriginWaypointUseCase
import com.zynergylabs.forager.app.domain.GetTracksUseCase
import com.zynergylabs.forager.app.domain.GetWaypointsUseCase
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.LocationTracker
import com.zynergylabs.forager.app.domain.RingerMode
import com.zynergylabs.forager.app.domain.StartTrackUseCase
import com.zynergylabs.forager.app.domain.TrackEnds
import com.zynergylabs.forager.app.domain.TrackRepository
import com.zynergylabs.forager.app.domain.isNetworkProviderFix
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.TrackPointRecord
import com.zynergylabs.forager.app.domain.model.TrackRecordingMode
import com.zynergylabs.forager.app.domain.model.WaypointDesignation
import java.time.ZoneOffset
import com.zynergylabs.forager.app.domain.model.Waypoint
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The real [TrackRecordingViewModel] over an in-memory [TrackRepository] fake, driven through its
 * real public methods — the same "real ViewModel over fakes" style
 * [com.zynergylabs.forager.app.ui.availability.AvailabilityViewModelPlannedTripsTest] uses.
 *
 * [beginPolling] runs an unbounded `while (true) { ...; delay(...) }` loop while recording, so
 * every test here uses [runCurrent]/[advanceTimeBy] rather than [advanceUntilIdle] once recording
 * has started — draining an infinite loop to "idle" never actually finishes.
 *
 * ## Every test runs inside [runRecordingTest], never bare `runTest`
 *
 * "Every test that starts a recording stops it before returning" was this class's rule, and it
 * held only while every assertion passed. A test body that throws *before* its own
 * `stopRecording()` leaves the poll loop scheduled on this test's own [dispatcher] — and
 * `runTest`'s closing idle-advance then spins through that loop forever. The visible symptom is a
 * run that never ends, not a failure: the assertion message is lost, and `runTest`'s own timeout
 * did not fire in 77 minutes of real time on a virtual-time spin (found by thread dump, twice, on
 * the same test). Not the next test, and not a shared dispatcher — JUnit 4 builds this class per
 * test method, so [dispatcher] is per test; the hang is the failing test's own `runTest`, which
 * also rules out an `@After` (it never gets to run). [runRecordingTest] stops every ViewModel the
 * [viewModel] fixture handed out, in a `finally`, *inside* the test body — before that closing
 * idle-advance — so a failing test fails and names itself, and the class continues. Proven by a
 * deliberately failing assertion with and without the helper, not by a green run: the suite was
 * green with the hazard in place, and a hang cannot turn a failure into a pass, only stall it.
 */
class TrackRecordingViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val fixedTime = CurrentTimeProvider { 1_000L }
    private var waypointIds = 0

    /** Every ViewModel [viewModel] built for this test — what [runRecordingTest] stops on the way out. */
    private val createdViewModels = mutableListOf<TrackRecordingViewModel>()

    /**
     * Alert-delivery dispatch: every [Alert] any ViewModel in this test handed to its delivery, in
     * order. One instance per test method (JUnit rebuilds the class), shared by every [viewModel]
     * built in it. The off-track tests assert on this list's size — an invocation count, the
     * dispatch's own "exactly one delivery per event" — and nothing here composes anything.
     */
    private val alertDelivery = RecordingAlertDelivery()

    /**
     * `runTest` on this class's [dispatcher], with every recording stopped before `runTest`'s own
     * closing idle-advance can reach an unstopped poll loop — see the class doc comment for the
     * hang this exists to make impossible. The one mechanism for the job: individual tests do not
     * need (and should not add) their own `finally`.
     */
    private fun runRecordingTest(body: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        try {
            body()
        } finally {
            createdViewModels.forEach { it.stopRecording() }
        }
    }

    private fun viewModel(
        trackRepository: TrackRepository = InMemoryTrackRepository(),
        waypointRepository: FakeWaypointRepository = FakeWaypointRepository(),
        // Every test but the one that exercises beginLocationTracking() itself drives
        // returnToStart() directly rather than through a collected fix — see
        // TrackRecordingViewModel's own doc comment for why that's equivalent — so an empty
        // stream is enough there; nothing needs beginLocationTracking() to ever actually emit.
        locationTracker: LocationTracker = NoOpLocationTracker(),
        offTrackAlertClock: CurrentTimeProvider = fixedTime,
        alertAudibility: AlertAudibility = FakeAlertAudibility(AUDIBLE),
        getWaypointReferenceCount: suspend (String) -> Int = { 0 },
        pendingDeleteCommitScope: CoroutineScope? = null,
        findRouteHome: (Track, LatLng, Waypoint?, HopBand) -> RouteHome = ::routeHome,
        shouldPromptBackgroundRun: suspend () -> Boolean = { false },
        is24HourClock: () -> Boolean = { false },
        trackEnds: TrackEnds = TrackEnds(),
        trackIdGenerator: () -> String = { "track-1" },
    ) = TrackRecordingViewModel(
        trackRepository = trackRepository,
        startTrack = StartTrackUseCase(trackRepository, currentTime = fixedTime, idGenerator = trackIdGenerator),
        getWaypoints = GetWaypointsUseCase(waypointRepository),
        // A counter, not a fixed id: navigation HUD stage one creates an origin *and* an end
        // waypoint per recording, and a fixed id would make the second silently replace the first.
        createWaypoint = CreateWaypointUseCase(waypointRepository, currentTime = fixedTime, idGenerator = { "waypoint-${++waypointIds}" }),
        deleteWaypoint = DeleteWaypointUseCase(waypointRepository),
        deleteTrack = DeleteTrackUseCase(trackRepository, waypointRepository, InMemoryKeptTrackPaths()),
        getTrackOriginWaypoint = GetTrackOriginWaypointUseCase(trackRepository, waypointRepository),
        alreadyRecordingMessage = "Forager is already recording a track. Stop it before starting another.",
        abandonedTrackSweepOnce = AbandonedTrackSweepOnce(EndAbandonedTracksUseCase(trackRepository, { null }, { _, _, _ -> }), processStartedAtEpochMillis = 0L),
        computeReturnToStart = ComputeReturnToStartUseCase(),
        locationTracker = locationTracker,
        getTracks = GetTracksUseCase(trackRepository),
        returnWatch = ReturnWatch(ComputeReturnToStartUseCase(), alertDelivery),
        alertAudibility = alertAudibility,
        currentTime = offTrackAlertClock,
        getWaypointReferenceCount = getWaypointReferenceCount,
        zone = ZoneOffset.UTC,
        is24HourClock = is24HourClock,
        pendingDeleteCommitScope = pendingDeleteCommitScope ?: com.zynergylabs.forager.app.ui.log.PendingDeleteCommitScope,
        findRouteHome = findRouteHome,
        shouldPromptBackgroundRun = shouldPromptBackgroundRun,
        trackEnds = trackEnds.latest,
    ).also(createdViewModels::add)

    /**
     * Dispatch 2026-10-21-04, review finding 1. The service's report of a stored end resyncs this screen when the
     * ended track is its recording, and the resync reads the row, which suspends. If the walker stops and records
     * again during that read (a Stop from the shade, then Record before the old end was written), the old
     * track's end must not clear the new recording. The read is held on a gate while that happens.
     */
    @Test
    fun `an old track's end, read while a new recording starts, does not clear the new recording`() = runRecordingTest {
        val inner = InMemoryTrackRepository()
        val gated = GetByIdGatedTrackRepository(inner, gatedId = "track-1")
        val trackEnds = TrackEnds()
        val ids = ArrayDeque(listOf("track-1", "track-2"))
        val vm = viewModel(gated, trackEnds = trackEnds, trackIdGenerator = { ids.removeFirst() })
        vm.startRecording()
        runCurrent()
        assertEquals("precondition: recording track-1", "track-1", vm.uiState.value.activeTrack?.trackId)

        // The service stores track-1's end and reports it; the resync's read of track-1 is held.
        inner.end("track-1", 5_000L).getOrThrow()
        trackEnds.report("track-1")
        runCurrent()
        assertEquals("precondition: the read is held, so nothing has cleared yet", "track-1", vm.uiState.value.activeTrack?.trackId)

        // During the read: Stop, then Record again.
        vm.stopRecording()
        runCurrent()
        vm.startRecording()
        runCurrent()
        assertEquals("precondition: the new recording runs", "track-2", vm.uiState.value.activeTrack?.trackId)

        gated.release()
        runCurrent()
        assertEquals(
            "the old track's end, read while the new recording started, must not clear the new recording",
            "track-2",
            vm.uiState.value.activeTrack?.trackId,
        )
    }

    /**
     * Dispatch 2026-10-21-02 (RECORD -1521): after Stop, Records showed "Tracks 0" until the Tracks chip was
     * tapped or the app restarted, because nothing reloaded the list. The screen's Stop does not end the row;
     * the service does, later, then reports it through [TrackEnds] (`TrackRecordingService.stopRecording`).
     * The test plays the service's part in that order, store and then report, and asserts on the list itself.
     */
    @Test
    fun `a track the service ends after Stop is in the track list with its end, without reopening Records`() = runRecordingTest {
        val trackRepository = InMemoryTrackRepository()
        val trackEnds = TrackEnds()
        val vm = viewModel(trackRepository, trackEnds = trackEnds)
        runCurrent()
        assertTrue("nothing recorded yet", vm.uiState.value.tracks.isEmpty())

        vm.startRecording()
        runCurrent()
        vm.stopRecording()
        runCurrent()

        // The service, on the stop intent: the end is stored first, and only then reported.
        trackRepository.end("track-1", 5_000L).getOrThrow()
        trackEnds.report("track-1")
        runCurrent()

        assertEquals(
            "the finished track, with its end, is in the list the Tracks count and the All list read",
            listOf("track-1" to 5_000L),
            vm.uiState.value.tracks.map { it.id to it.endedAtEpochMillis },
        )
    }

    @Test
    fun `starting a recording creates a track and sets active state`() = runRecordingTest {
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
    fun `a failed start surfaces an error and leaves no active track, without the exception's own message`() = runRecordingTest {
        val vm = viewModel(FailingTrackRepository())

        vm.startRecording()
        runCurrent()

        assertNull(vm.uiState.value.activeTrack)
        // Error-presentation spec's absolute rule: FailingTrackRepository.create() throws
        // RuntimeException("boom") — that text must never reach state, however recognizable it is.
        assertEquals("Couldn't start recording.", vm.uiState.value.startRecordingErrorMessage)
    }

    @Test
    fun `a permission-denied start is reported without ever setting an active track`() = runRecordingTest {
        val trackRepository = InMemoryTrackRepository()
        val vm = viewModel(trackRepository)

        vm.onStartRecordingPermissionDenied("Track recording needs location access.")

        assertNull(vm.uiState.value.activeTrack)
        assertFalse(vm.uiState.value.isRecording)
        assertEquals("Track recording needs location access.", vm.uiState.value.startRecordingErrorMessage)
        assertTrue("the Track row must never be created when the start is refused", trackRepository.createdTrackIds.isEmpty())
    }

    @Test
    fun `a permission-denied report after an active recording rolls it back, matching stopRecording`() = runRecordingTest {
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
    fun `stopping clears active state and the poll loop stops scheduling further work`() = runRecordingTest {
        val trackRepository = InMemoryTrackRepository()
        val vm = viewModel(trackRepository)

        vm.startRecording()
        runCurrent()
        vm.stopRecording()

        assertNull(vm.uiState.value.activeTrack)
        // No point was ever polled here, so this cannot fail; the clear on Stop is tested by
        // `the recording line is cleared by the screen's Stop` (dispatch 2026-10-24-05).
        assertTrue(vm.uiState.value.breadcrumbPoints.isEmpty())

        // If the poll job weren't actually cancelled, this would hang forever the same way an
        // unguarded advanceUntilIdle() would with it still running.
        advanceUntilIdle()
    }

    @Test
    fun `breadcrumb points refresh on each poll while recording`() = runRecordingTest {
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

    /**
     * Dispatch 2026-10-24-05 (RECORD -1602): the dotted recording line stayed on the map after Stop, until the
     * next Record or the process ended, because `clearRecordingState` reset every recording field but
     * [TrackRecordingUiState.breadcrumbPoints] (found on the S22, report 2026-10-24-04). The poll has read
     * points first, so the list is not empty before Stop: the assertion in
     * `stopping clears active state and the poll loop stops scheduling further work` runs before any point is
     * read, and passes with or without the clear.
     */
    @Test
    fun `the recording line is cleared by the screen's Stop`() = runRecordingTest {
        val trackRepository = InMemoryTrackRepository()
        val vm = viewModel(trackRepository)
        vm.startRecording()
        runCurrent()
        trackRepository.appendPoints("track-1", listOf(point(lat = 45.0, t = 1_000L), point(lat = 45.001, t = 2_000L)))
        advanceTimeBy(POLL_INTERVAL_MILLIS)
        runCurrent()
        assertEquals("the line is drawn while recording", listOf(45.0, 45.001), vm.uiState.value.breadcrumbPoints.map { it.lat })

        vm.stopRecording()
        runCurrent()

        assertEquals("no recording line after Stop", emptyList<TrackPoint>(), vm.uiState.value.breadcrumbPoints)
    }

    /**
     * The same, for Stop from the notification: the service ends the row and reports it through [TrackEnds],
     * and this screen clears through the resync (`onTrackEnded`), never through [TrackRecordingViewModel.stopRecording].
     * The test plays the service's part in its order, store and then report.
     */
    @Test
    fun `the recording line is cleared by Stop from the notification`() = runRecordingTest {
        val trackRepository = InMemoryTrackRepository()
        val trackEnds = TrackEnds()
        val vm = viewModel(trackRepository, trackEnds = trackEnds)
        vm.startRecording()
        runCurrent()
        trackRepository.appendPoints("track-1", listOf(point(lat = 45.0, t = 1_000L), point(lat = 45.001, t = 2_000L)))
        advanceTimeBy(POLL_INTERVAL_MILLIS)
        runCurrent()
        assertEquals("the line is drawn while recording", listOf(45.0, 45.001), vm.uiState.value.breadcrumbPoints.map { it.lat })

        trackRepository.end("track-1", 5_000L).getOrThrow()
        trackEnds.report("track-1")
        runCurrent()

        assertFalse("the screen took the notification's Stop", vm.uiState.value.isRecording)
        assertEquals("no recording line after Stop from the notification", emptyList<TrackPoint>(), vm.uiState.value.breadcrumbPoints)
    }

    /**
     * Path-home join dispatch, through the real entry points: the poll computes
     * [TrackRecordingUiState.pathHome] only while returning, from the last gated fix, and it is
     * the *joined* distance — the PathHomeTest double-back (north three legs, back two) reads one
     * leg, 111.195 m, with one join, not the five-leg sum. `startReturn()` restarts the poll so the
     * value is there at once; a later poll over the walker's arrival at the origin reads 0 (the
     * estimate decreased — the defect being fixed, seen from the ViewModel); `stopReturn()` clears
     * it. The origin waypoint is created from the first gated fix, at the first point, so the
     * origin's leg is zero and the total is the track figure alone.
     */
    @Test
    fun `path home is computed by the poll while returning, over the joined track, and cleared when the return stops`() = runRecordingTest {
        val trackRepository = InMemoryTrackRepository()
        val fixes = MutableSharedFlow<LocationFix>()
        val vm = viewModel(trackRepository, locationTracker = FakeLocationTracker(fixes))

        vm.startRecording()
        runCurrent()
        fixes.emit(LocationFix.Update(lat = 45.000, lng = -122.0, altitude = null, accuracyMeters = 5f, timestampEpochMillis = 1_000L, provider = FixProvider.GPS))
        runCurrent()
        assertEquals(45.000, vm.uiState.value.originWaypoint!!.lat, 0.0)
        trackRepository.appendPoints(
            "track-1",
            listOf(45.000, 45.001, 45.002, 45.003, 45.002, 45.001).mapIndexed { i, lat -> point(lat = lat, t = 1_000L + i * 15_000L) },
        )
        fixes.emit(LocationFix.Update(lat = 45.001, lng = -122.0, altitude = null, accuracyMeters = 5f, timestampEpochMillis = 76_000L, provider = FixProvider.GPS))
        runCurrent()
        advanceTimeBy(POLL_INTERVAL_MILLIS)
        runCurrent()
        assertEquals(6, vm.uiState.value.breadcrumbPoints.size)
        assertNull("nothing is computed for a walker who has not turned round", vm.uiState.value.pathHome)

        vm.startReturn()
        runCurrent()
        val returning = vm.uiState.value.pathHome!!
        assertEquals(111.19508, returning.trackMeters, 0.01)
        assertEquals(1, returning.joinsOnRoute)
        assertEquals(0.0, returning.hopMeters, 0.001)
        assertEquals(0.0, returning.originLegMeters!!, 0.0)
        assertEquals(111.19508, returning.totalMeters, 0.01)

        // The walker reaches the origin: the next poll reads zero — lower than before.
        trackRepository.appendPoints("track-1", listOf(point(lat = 45.000, t = 91_000L)))
        fixes.emit(LocationFix.Update(lat = 45.000, lng = -122.0, altitude = null, accuracyMeters = 5f, timestampEpochMillis = 91_000L, provider = FixProvider.GPS))
        runCurrent()
        advanceTimeBy(POLL_INTERVAL_MILLIS)
        runCurrent()
        assertEquals(0.0, vm.uiState.value.pathHome!!.totalMeters, 0.001)

        vm.stopReturn()
        assertNull(vm.uiState.value.pathHome)
        vm.stopRecording()
    }

    @Test
    fun `return to start uses the earliest breadcrumb point as the start`() = runRecordingTest {
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
    fun `return to start is null with no active recording`() = runRecordingTest {
        val vm = viewModel()

        val info = vm.returnToStart(point(lat = 45.0, t = 1_000L))

        assertNull(info)
    }

    @Test
    fun `starting a recording collects live fixes and updates returnToStart reactively, without a direct call`() = runRecordingTest {
        val trackRepository = InMemoryTrackRepository()
        val fixes = MutableSharedFlow<LocationFix>()
        val vm = viewModel(trackRepository, locationTracker = FakeLocationTracker(fixes))

        vm.startRecording()
        runCurrent()
        trackRepository.appendPoints("track-1", listOf(point(lat = 45.0, lng = -122.0, t = 1_000L)))
        advanceTimeBy(POLL_INTERVAL_MILLIS)
        runCurrent()
        assertNull(vm.uiState.value.returnToStart)

        fixes.emit(LocationFix.Update(lat = 45.001, lng = -122.0, altitude = null, accuracyMeters = null, timestampEpochMillis = 2_000L, provider = FixProvider.GPS))
        runCurrent()

        assertEquals(180.0, vm.uiState.value.returnToStart?.bearingDegrees ?: -1.0, 0.01)
        vm.stopRecording()
    }

    @Test
    fun `stopping the recording stops collecting fixes and clears returnToStart`() = runRecordingTest {
        val trackRepository = InMemoryTrackRepository()
        val fixes = MutableSharedFlow<LocationFix>()
        val vm = viewModel(trackRepository, locationTracker = FakeLocationTracker(fixes))

        vm.startRecording()
        runCurrent()
        trackRepository.appendPoints("track-1", listOf(point(lat = 45.0, lng = -122.0, t = 1_000L)))
        advanceTimeBy(POLL_INTERVAL_MILLIS)
        runCurrent()
        fixes.emit(LocationFix.Update(lat = 45.001, lng = -122.0, altitude = null, accuracyMeters = null, timestampEpochMillis = 2_000L, provider = FixProvider.GPS))
        runCurrent()
        assertEquals(180.0, vm.uiState.value.returnToStart?.bearingDegrees ?: -1.0, 0.01)

        vm.stopRecording()

        assertNull(vm.uiState.value.returnToStart)
        // Same shape as stopRecording()'s own poll-job test: if the location job weren't actually
        // cancelled, its still-running collect() would keep this scope non-idle and this would hang.
        advanceUntilIdle()
    }

    @Test
    fun `starting a recording does not mark the walker as returning`() = runRecordingTest {
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
     * `docs/navigation/2026-08-30-return-to-vehicle-semantics-click-noop.md`). They were not added for
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
    fun `startReturn is a no-op with nothing recording`() = runRecordingTest {
        val vm = viewModel()

        vm.startReturn()

        assertFalse(vm.uiState.value.isReturning)
    }

    @Test
    fun `startReturn marks returning while actively recording, stopReturn clears it without stopping the recording`() = runRecordingTest {
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

    // The eight off-track tests that stood here moved to `domain/ReturnWatchTest`, one for one, with
    // the decision they test (dispatch 2026-09-28-400, Amendment 2, ruling 4). The ViewModel no
    // longer decides; `TrackRecordingSwipeAwayTest` holds what it does now: calling the watch and
    // copying its state.

    @Test
    fun `the loaders return a Job, and once joined show waypoints and tracks written behind the screen's back`() = runRecordingTest {
        val waypointRepository = FakeWaypointRepository()
        val vm = viewModel(waypointRepository = waypointRepository)
        advanceUntilIdle()
        waypointRepository.save(Waypoint(id = "restored-w", lat = 45.0, lng = -122.0, altitude = null, name = "Restored oak", note = "", createdAtEpochMillis = 1_000L))

        vm.loadWaypoints().join()
        vm.loadTracks().join()

        assertEquals(listOf("restored-w"), vm.uiState.value.waypoints.map { it.id })
    }

    @Test
    fun `waypoints load on init and adding one refreshes the list`() = runRecordingTest {
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

    // ---- Dispatch -616 (plan T10): a waypoint dropped while recording belongs to that walk ----
    //
    // addWaypoint is what the map's '+' > Waypoint > name dialog calls (MainActivity's onDropWaypoint);
    // the screen half of that path, up to onDropWaypoint, is AvailabilityScreenWaypointFlowTest's.

    @Test
    fun `a waypoint dropped while recording is linked to that recording and read back as one of its waypoints`() = runRecordingTest {
        val waypointRepository = FakeWaypointRepository()
        val vm = viewModel(waypointRepository = waypointRepository)
        vm.startRecording(TrackRecordingMode.HIGH_ACCURACY)
        runCurrent()
        assertEquals("track-1", vm.uiState.value.activeTrack?.trackId)

        vm.addWaypoint(lat = 45.0, lng = -122.0, name = "Chanterelle bank")
        runCurrent()

        val dropped = waypointRepository.getAll().getOrThrow().single()
        assertEquals("Chanterelle bank", dropped.name)
        assertEquals("track-1", dropped.trackId)
        assertNull("a dropped waypoint is an ordinary one, not the walk's start or end", dropped.designation)
        assertEquals(listOf(dropped), waypointRepository.getForTrack("track-1").getOrThrow())
        vm.stopRecording()
    }

    @Test
    fun `a waypoint dropped with no recording running stays standalone`() = runRecordingTest {
        val waypointRepository = FakeWaypointRepository()
        val vm = viewModel(waypointRepository = waypointRepository)
        advanceUntilIdle()

        vm.addWaypoint(lat = 45.0, lng = -122.0, name = "Big oak")
        advanceUntilIdle()

        assertNull(waypointRepository.getAll().getOrThrow().single().trackId)
    }

    @Test
    fun `a waypoint dropped after the recording stopped is not linked to the walk that ended`() = runRecordingTest {
        val waypointRepository = FakeWaypointRepository()
        val vm = viewModel(waypointRepository = waypointRepository)
        vm.startRecording(TrackRecordingMode.HIGH_ACCURACY)
        runCurrent()
        vm.stopRecording()
        runCurrent()

        vm.addWaypoint(lat = 45.0, lng = -122.0, name = "On the drive home")
        runCurrent()

        assertNull(waypointRepository.getAll().getOrThrow().single { it.name == "On the drive home" }.trackId)
        assertEquals(emptyList<Waypoint>(), waypointRepository.getForTrack("track-1").getOrThrow())
    }

    @Test
    fun `deleting the walk keeps its dropped waypoint, as an ordinary waypoint with no link`() = runRecordingTest {
        val trackRepository = InMemoryTrackRepository()
        val waypointRepository = FakeWaypointRepository()
        val vm = viewModel(trackRepository, waypointRepository)
        vm.startRecording(TrackRecordingMode.HIGH_ACCURACY)
        runCurrent()
        vm.addWaypoint(lat = 45.0, lng = -122.0, name = "Chanterelle bank")
        runCurrent()
        vm.stopRecording()
        runCurrent()
        assertEquals("linked before the delete", listOf("Chanterelle bank"), waypointRepository.getForTrack("track-1").getOrThrow().map { it.name })
        // Ending the row is the recording service's job (stopRecording's doc comment), done here by hand.
        trackRepository.end("track-1", 2_000L)
        vm.loadTracks().join()

        vm.requestRemoveTrack("track-1")
        vm.commitRemoveTrack("track-1")
        runCurrent()

        assertNull("the walk is gone", trackRepository.getById("track-1").getOrThrow())
        val kept = waypointRepository.getAll().getOrThrow().single()
        assertEquals("Chanterelle bank", kept.name)
        assertNull("its link is cleared, not left pointing at a deleted walk", kept.trackId)
    }

    @Test
    fun `removing a waypoint refreshes the list`() = runRecordingTest {
        val waypointRepository = FakeWaypointRepository()
        waypointRepository.save(Waypoint(id = "waypoint-1", lat = 45.0, lng = -122.0, altitude = null, name = "Big oak", note = "", createdAtEpochMillis = 1_000L))
        val vm = viewModel(waypointRepository = waypointRepository)
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.waypoints.size)

        vm.removeWaypoint("waypoint-1")
        advanceUntilIdle()

        assertTrue(vm.uiState.value.waypoints.isEmpty())
    }

    // ---- Journal redesign J4, D1: a swiped waypoint is pending, not deleted, until its snackbar ends --

    private fun pendingDeleteWaypoints() = FakeWaypointRepository().apply {
        seed(Waypoint(id = "wp-creek", lat = 45.0, lng = -122.0, altitude = null, name = "Creek pin", note = "", createdAtEpochMillis = 2_000L))
        seed(Waypoint(id = "wp-oak", lat = 45.1, lng = -122.1, altitude = null, name = "Big oak", note = "", createdAtEpochMillis = 1_000L))
    }

    @Test
    fun `a requested waypoint delete is pending, hidden from visibleWaypoints, and deletes nothing`() = runRecordingTest {
        val repository = pendingDeleteWaypoints()
        val vm = viewModel(waypointRepository = repository, getWaypointReferenceCount = { id -> if (id == "wp-creek") 2 else 0 })
        advanceUntilIdle()

        vm.requestRemoveWaypoint("wp-creek")
        advanceUntilIdle()

        val pending = requireNotNull(vm.uiState.value.pendingWaypointDelete)
        assertEquals("wp-creek", pending.item.id)
        assertEquals(2, pending.entryReferenceCount)
        assertEquals(listOf("wp-oak"), vm.uiState.value.visibleWaypoints.map { it.id })
        assertEquals(emptyList<String>(), repository.deletedIds)
    }

    @Test
    fun `undo of a pending waypoint delete shows it again and deletes nothing`() = runRecordingTest {
        val repository = pendingDeleteWaypoints()
        val vm = viewModel(waypointRepository = repository)
        advanceUntilIdle()
        vm.requestRemoveWaypoint("wp-creek")
        advanceUntilIdle()
        assertEquals("positive control: pending before the undo", listOf("wp-oak"), vm.uiState.value.visibleWaypoints.map { it.id })

        vm.undoRemoveWaypoint("wp-creek")
        advanceUntilIdle()

        assertNull(vm.uiState.value.pendingWaypointDelete)
        assertEquals(setOf("wp-creek", "wp-oak"), vm.uiState.value.visibleWaypoints.map { it.id }.toSet())
        assertEquals(emptyList<String>(), repository.deletedIds)
    }

    @Test
    fun `committing a pending waypoint delete (the snackbar timing out) deletes exactly that waypoint once`() = runRecordingTest {
        val repository = pendingDeleteWaypoints()
        val vm = viewModel(waypointRepository = repository)
        advanceUntilIdle()
        vm.requestRemoveWaypoint("wp-creek")
        advanceUntilIdle()

        vm.commitRemoveWaypoint("wp-creek")
        advanceUntilIdle()
        vm.commitRemoveWaypoint("wp-creek")
        advanceUntilIdle()

        assertEquals(listOf("wp-creek"), repository.deletedIds)
        assertNull(vm.uiState.value.pendingWaypointDelete)
        assertEquals(listOf("wp-oak"), vm.uiState.value.visibleWaypoints.map { it.id })
    }

    @Test
    fun `a second waypoint delete commits the first when it replaces it`() = runRecordingTest {
        val repository = pendingDeleteWaypoints()
        val vm = viewModel(waypointRepository = repository)
        advanceUntilIdle()
        vm.requestRemoveWaypoint("wp-creek")
        advanceUntilIdle()

        vm.requestRemoveWaypoint("wp-oak")
        advanceUntilIdle()

        assertEquals(listOf("wp-creek"), repository.deletedIds)
        assertEquals("wp-oak", vm.uiState.value.pendingWaypointDelete?.item?.id)
        assertEquals(emptyList<String>(), vm.uiState.value.visibleWaypoints.map { it.id })
    }

    @Test
    fun `a waypoint delete still pending when the ViewModel is cleared is committed`() = runRecordingTest {
        val repository = pendingDeleteWaypoints()
        val testScope: CoroutineScope = this
        val store = ViewModelStore()
        val vm = ViewModelProvider(
            store,
            viewModelFactory { initializer { viewModel(waypointRepository = repository, pendingDeleteCommitScope = testScope) } },
        )[TrackRecordingViewModel::class.java]
        advanceUntilIdle()
        vm.requestRemoveWaypoint("wp-creek")
        advanceUntilIdle()
        assertEquals(emptyList<String>(), repository.deletedIds)

        store.clear()
        advanceUntilIdle()

        assertEquals(listOf("wp-creek"), repository.deletedIds)
    }

    // ---- Navigation HUD stage one: the auto-created origin and end waypoints -------------------
    // fixedTime is 1_000 ms after the epoch, so every default name reads "Jan 1, 1970, 12:00 AM" in UTC (RECORD -703 added the year).
    // runCurrent(), never advanceUntilIdle(), while a recording is active: the breadcrumb poll is an
    // infinite delay loop, and advancing virtual time until idle never returns (found the hard way —
    // a 77-minute hung test worker). The origin/end saves have no delays, so runCurrent() runs them.
    // No per-test finally here any more: runRecordingTest stops every recording on the way out
    // (see the class doc comment), one mechanism for the whole class.

    private fun fix(lat: Double, accuracy: Float?, t: Long, altitude: Double? = null) =
        LocationFix.Update(lat = lat, lng = -122.0, altitude = altitude, accuracyMeters = accuracy, timestampEpochMillis = t, provider = FixProvider.GPS)

    @Test
    fun `the origin is created from the first fix that passes the mode's accuracy gate, linked to the track and pointed at by it`() = runRecordingTest {
        val trackRepository = InMemoryTrackRepository()
        val waypointRepository = FakeWaypointRepository()
        val fixes = MutableSharedFlow<LocationFix>()
        val vm = viewModel(trackRepository, waypointRepository, locationTracker = FakeLocationTracker(fixes))
        vm.startRecording(TrackRecordingMode.HIGH_ACCURACY) // gate: 30 m
        runCurrent()
        fixes.emit(fix(lat = 45.0, accuracy = 80f, t = 2_000L)) // worse than the gate: not the origin
        runCurrent()
        assertNull(vm.uiState.value.originWaypoint)
        assertTrue(waypointRepository.getAll().getOrThrow().isEmpty())

        fixes.emit(fix(lat = 45.001, accuracy = 10f, t = 3_000L, altitude = 120.0))
        runCurrent()

        val origin = requireNotNull(vm.uiState.value.originWaypoint)
        assertEquals("waypoint-1", origin.id)
        assertEquals(45.001, origin.lat, 1e-9)
        assertEquals(120.0, origin.altitude)
        assertEquals(WaypointDesignation.ORIGIN, origin.designation)
        assertEquals("track-1", origin.trackId)
        assertEquals("Start · Jan 1, 1970, 12:00 AM", origin.name)
        assertEquals(listOf(origin), waypointRepository.getAll().getOrThrow())
        assertEquals("waypoint-1", trackRepository.getById("track-1").getOrThrow()?.originWaypointId)
        assertEquals(listOf(origin), vm.uiState.value.waypoints)

        fixes.emit(fix(lat = 45.002, accuracy = 10f, t = 4_000L))
        runCurrent()
        assertEquals("a second gated fix must not create a second origin", 1, waypointRepository.getAll().getOrThrow().size)
        vm.stopRecording()
    }

    /** Data part D (RECORD -703): on a 24-hour phone the origin's default name is in 24-hour time. */
    @Test
    fun `on a 24-hour phone the origin's default name is in 24-hour time`() = runRecordingTest {
        val waypointRepository = FakeWaypointRepository()
        val fixes = MutableSharedFlow<LocationFix>()
        val vm = viewModel(InMemoryTrackRepository(), waypointRepository, locationTracker = FakeLocationTracker(fixes), is24HourClock = { true })
        vm.startRecording(TrackRecordingMode.HIGH_ACCURACY)
        runCurrent()
        fixes.emit(fix(lat = 45.001, accuracy = 10f, t = 3_000L))
        runCurrent()

        assertEquals("Start · Jan 1, 1970, 00:00", requireNotNull(vm.uiState.value.originWaypoint).name)
        vm.stopRecording()
    }

    @Test
    fun `return to start points at the origin waypoint once it exists, not the first breadcrumb`() = runRecordingTest {
        val trackRepository = InMemoryTrackRepository()
        val fixes = MutableSharedFlow<LocationFix>()
        val vm = viewModel(trackRepository, locationTracker = FakeLocationTracker(fixes))
        vm.startRecording(TrackRecordingMode.HIGH_ACCURACY)
        runCurrent()
        trackRepository.appendPoints("track-1", listOf(point(lat = 45.0, lng = -122.0, t = 1_000L)))
        advanceTimeBy(POLL_INTERVAL_MILLIS)
        runCurrent()
        fixes.emit(fix(lat = 45.001, accuracy = 10f, t = 3_000L)) // seeds the origin at 45.001
        runCurrent()

        val info = vm.returnToStart(point(lat = 45.002, lng = -122.0, t = 4_000L))

        // 0.001° of latitude is 111.2 m: to the origin at 45.001, not 222 m to the breadcrumb at 45.0.
        assertEquals(111.2, info?.distanceMeters ?: -1.0, 1.0)
        assertEquals(180.0, info?.bearingDegrees ?: -1.0, 0.01)
        vm.stopRecording()
    }

    @Test
    fun `stopping creates the end waypoint from the last gated fix, not from a later rejected one`() = runRecordingTest {
        val waypointRepository = FakeWaypointRepository()
        val fixes = MutableSharedFlow<LocationFix>()
        val vm = viewModel(waypointRepository = waypointRepository, locationTracker = FakeLocationTracker(fixes))
        vm.startRecording(TrackRecordingMode.HIGH_ACCURACY)
        runCurrent()
        fixes.emit(fix(lat = 45.001, accuracy = 10f, t = 3_000L))
        runCurrent()
        fixes.emit(fix(lat = 45.010, accuracy = 10f, t = 4_000L))
        runCurrent()
        fixes.emit(fix(lat = 45.500, accuracy = 80f, t = 5_000L)) // rejected by the gate
        runCurrent()
        vm.stopRecording()
        runCurrent()

        val saved = waypointRepository.getAll().getOrThrow().sortedBy { it.id }
        assertEquals(listOf("waypoint-1", "waypoint-2"), saved.map { it.id })
        val end = saved.last()
        assertEquals(WaypointDesignation.END, end.designation)
        assertEquals("track-1", end.trackId)
        assertEquals(45.010, end.lat, 1e-9)
        assertEquals("End · Jan 1, 1970, 12:00 AM", end.name)
        assertNull(vm.uiState.value.originWaypoint)
    }

    @Test
    fun `a recording whose fixes never pass the gate has neither an origin nor an end - a valid state`() = runRecordingTest {
        val waypointRepository = FakeWaypointRepository()
        val fixes = MutableSharedFlow<LocationFix>()
        val vm = viewModel(waypointRepository = waypointRepository, locationTracker = FakeLocationTracker(fixes))
        vm.startRecording(TrackRecordingMode.HIGH_ACCURACY)
        runCurrent()
        fixes.emit(fix(lat = 45.0, accuracy = 80f, t = 2_000L))
        runCurrent()
        vm.stopRecording()
        runCurrent()

        assertNull(vm.uiState.value.originWaypoint)
        assertTrue(waypointRepository.getAll().getOrThrow().isEmpty())
    }

    // ── Dispatch 2026-09-28-527: the origin, the end and the route home take GPS only ──
    //
    // A 15 m fix clears every mode's ceiling, so before this dispatch a network one seeded the origin,
    // became the end and was where the route home was searched from. RECORD -558: the origin waypoint and
    // the route home take GPS only (the end waypoint by the dispatch's rule 2, which it also writes); the
    // sundown countdown keeps reading any fix (TrackRecordingSundownTest).

    private fun networkFix(lat: Double, t: Long) =
        LocationFix.Update(lat = lat, lng = -122.0, altitude = null, accuracyMeters = 15f, timestampEpochMillis = t + 123, provider = FixProvider.NETWORK)

    @Test
    fun `a 15 m network fix never seeds the origin, and a 15 m GPS fix after it does`() = runRecordingTest {
        val waypointRepository = FakeWaypointRepository()
        val fixes = MutableSharedFlow<LocationFix>()
        val vm = viewModel(waypointRepository = waypointRepository, locationTracker = FakeLocationTracker(fixes))
        vm.startRecording(TrackRecordingMode.HIGH_ACCURACY) // gate: 30 m, which 15 m clears
        runCurrent()

        fixes.emit(networkFix(lat = 45.0, t = 2_000L))
        runCurrent()
        assertNull("no origin from a network fix", vm.uiState.value.originWaypoint)
        assertTrue(waypointRepository.getAll().getOrThrow().isEmpty())

        fixes.emit(fix(lat = 45.001, accuracy = 15f, t = 3_000L))
        runCurrent()
        assertEquals("the origin is the GPS fix", 45.001, requireNotNull(vm.uiState.value.originWaypoint).lat, 1e-9)
        vm.stopRecording()
    }

    @Test
    fun `a 15 m fix from an unknown provider never seeds the origin`() = runRecordingTest {
        val waypointRepository = FakeWaypointRepository()
        val fixes = MutableSharedFlow<LocationFix>()
        val vm = viewModel(waypointRepository = waypointRepository, locationTracker = FakeLocationTracker(fixes))
        vm.startRecording(TrackRecordingMode.HIGH_ACCURACY)
        runCurrent()

        fixes.emit(fix(lat = 45.0, accuracy = 15f, t = 2_000L).copy(provider = FixProvider.UNKNOWN))
        runCurrent()

        assertNull(vm.uiState.value.originWaypoint)
        assertTrue(waypointRepository.getAll().getOrThrow().isEmpty())
        vm.stopRecording()
    }

    @Test
    fun `stopping makes the end waypoint from the last GPS fix, not a later 15 m network fix`() = runRecordingTest {
        val waypointRepository = FakeWaypointRepository()
        val fixes = MutableSharedFlow<LocationFix>()
        val vm = viewModel(waypointRepository = waypointRepository, locationTracker = FakeLocationTracker(fixes))
        vm.startRecording(TrackRecordingMode.HIGH_ACCURACY)
        runCurrent()
        fixes.emit(fix(lat = 45.001, accuracy = 10f, t = 3_000L))
        runCurrent()
        fixes.emit(fix(lat = 45.010, accuracy = 10f, t = 4_000L))
        runCurrent()
        fixes.emit(networkFix(lat = 45.500, t = 5_000L))
        runCurrent()
        vm.stopRecording()
        runCurrent()

        val end = waypointRepository.getAll().getOrThrow().single { it.designation == WaypointDesignation.END }
        assertEquals(45.010, end.lat, 1e-9)
    }

    /**
     * Recorded north as in [recordThreePointsNorth], the walker's only fix a 15 m one from [provider]. Returns
     * the route home once Return has been tapped.
     */
    private suspend fun kotlinx.coroutines.test.TestScope.routeHomeFrom(provider: FixProvider): RouteHome? {
        val trackRepository = InMemoryTrackRepository()
        val fixes = MutableSharedFlow<LocationFix>()
        val vm = viewModel(trackRepository, locationTracker = FakeLocationTracker(fixes))
        vm.startRecording()
        runCurrent()
        trackRepository.appendPoints("track-1", listOf(45.000, 45.001, 45.002).mapIndexed { i, lat -> point(lat = lat, t = 1_000L + i * 15_000L) })
        advanceTimeBy(POLL_INTERVAL_MILLIS * 2)
        runCurrent()
        val t = 31_000L
        fixes.emit(LocationFix.Update(lat = 45.002, lng = -122.0, altitude = null, accuracyMeters = 15f, timestampEpochMillis = if (provider == FixProvider.NETWORK) t + 123 else t, provider = provider))
        runCurrent()
        assertEquals("precondition: the poll has read the three points", 3, vm.uiState.value.breadcrumbPoints.size)

        vm.startReturn()
        runCurrent()
        return vm.uiState.value.routeHome.also { vm.stopRecording() }
    }

    @Test
    fun `the route home is never searched from a 15 m network fix`() = runRecordingTest {
        assertNull(routeHomeFrom(FixProvider.NETWORK))
    }

    @Test
    fun `the route home is never searched from a 15 m fix from an unknown provider`() = runRecordingTest {
        assertNull(routeHomeFrom(FixProvider.UNKNOWN))
    }

    /**
     * The control: the same fix from GPS is searched from, as today. Any result counts, since the GPS fix
     * also seeds the origin where the walker stands: the claim is that a search ran, and without a gated
     * fix [TrackRecordingViewModel] leaves the route home `null` without searching.
     */
    @Test
    fun `the route home is searched from a 15 m GPS fix`() = runRecordingTest {
        assertTrue(routeHomeFrom(FixProvider.GPS) != null)
    }

    private fun point(lat: Double, lng: Double = -122.0, t: Long) =
        TrackPoint(lat = lat, lng = lng, altitude = null, accuracyMeters = null, timestampEpochMillis = t)

    private companion object {
        const val POLL_INTERVAL_MILLIS = 15_000L

        /** The ViewModel's route tick, decision D4. Repeated here as [POLL_INTERVAL_MILLIS] is. */
        const val ROUTE_TICK_MILLIS = 5_000L
    }

    // ── Dispatch 2026-09-28-423: the route tick (plan task T6) ─────────────────────────────

    private fun gatedFix(lat: Double, lng: Double = -122.0, t: Long) =
        LocationFix.Update(lat = lat, lng = lng, altitude = null, accuracyMeters = 5f, timestampEpochMillis = t, provider = FixProvider.GPS)

    /**
     * Record; the first gated fix seeds the origin at 45.000; the service stores three points north,
     * 111 m apart; the walker stands on the last. Two polls have read the track. Returns the
     * ViewModel and the fix stream.
     */
    private suspend fun kotlinx.coroutines.test.TestScope.recordThreePointsNorth(
        trackRepository: InMemoryTrackRepository,
        findRouteHome: (Track, LatLng, Waypoint?, HopBand) -> RouteHome = ::routeHome,
    ): Pair<TrackRecordingViewModel, MutableSharedFlow<LocationFix>> {
        val fixes = MutableSharedFlow<LocationFix>()
        val vm = viewModel(trackRepository, locationTracker = FakeLocationTracker(fixes), findRouteHome = findRouteHome)
        vm.startRecording()
        runCurrent()
        fixes.emit(gatedFix(lat = 45.000, t = 1_000L))
        runCurrent()
        trackRepository.appendPoints("track-1", listOf(45.000, 45.001, 45.002).mapIndexed { i, lat -> point(lat = lat, t = 1_000L + i * 15_000L) })
        advanceTimeBy(POLL_INTERVAL_MILLIS * 2)
        runCurrent()
        fixes.emit(gatedFix(lat = 45.002, t = 31_000L))
        runCurrent()
        assertEquals("precondition: the poll has read the three points", 3, vm.uiState.value.breadcrumbPoints.size)
        return vm to fixes
    }

    /**
     * One route search per tick, through the real entry points: none before Return (the poll no
     * longer searches), one at Return, one every 5 s after, none once the return stops. The
     * path-home figure is the same object the route search built, so it cannot come from a second
     * search. The count is the claim, so the search is counted, not inferred from timing. Fails
     * with the poll still running its own path-home search (a second search on each 15 s poll),
     * and with the tick removed (one search at Return and no more).
     */
    @Test
    fun `while returning the route is searched once at Return and once every 5 s, and path home is that search's own figure`() = runRecordingTest {
        var searches = 0
        val (vm, _) = recordThreePointsNorth(InMemoryTrackRepository()) { track, current, origin, band ->
            searches++
            routeHome(track, current, origin, band)
        }
        assertEquals("no search before Return: the poll does not search", 0, searches)
        assertNull(vm.uiState.value.routeHome)

        vm.startReturn()
        runCurrent()
        assertEquals("one search at Return", 1, searches)
        val first = vm.uiState.value.routeHome as RouteHome.Ahead
        assertEquals(222.39016, first.routeMeters, 0.01)
        assertSame("path home is the route search's own figure", first.pathHome, vm.uiState.value.pathHome)

        advanceTimeBy(ROUTE_TICK_MILLIS)
        runCurrent()
        assertEquals(2, searches)
        // 20 s after Return: ticks at 5, 10, 15 and 20 s, and a poll at 15 s that adds none.
        advanceTimeBy(ROUTE_TICK_MILLIS * 3)
        runCurrent()
        assertEquals(5, searches)

        vm.stopReturn()
        assertNull(vm.uiState.value.routeHome)
        assertNull(vm.uiState.value.pathHome)
        advanceTimeBy(POLL_INTERVAL_MILLIS * 2)
        runCurrent()
        assertEquals("no search after the return stops", 5, searches)
    }

    /**
     * "Try again" recomputes at once, not at the next tick; before Return it does nothing, since
     * there is no route to show. Fails with retryRoute not searching.
     */
    @Test
    fun `Try again searches the route at once while returning, and does nothing when not returning`() = runRecordingTest {
        var searches = 0
        val (vm, _) = recordThreePointsNorth(InMemoryTrackRepository()) { track, current, origin, band ->
            searches++
            routeHome(track, current, origin, band)
        }
        vm.retryRoute()
        assertEquals(0, searches)
        assertNull(vm.uiState.value.routeHome)

        vm.startReturn()
        runCurrent()
        assertEquals(1, searches)
        vm.retryRoute()
        assertEquals("at once, with no time passing", 2, searches)
    }

    /**
     * Off the route and back, through fixes: 78 m east of the last stored point is the far band,
     * withheld as off the route; 48 m is still withheld, because the band leaves only below 45 m
     * and the tick carries it from the last result; 39 m is back on the route. Fails with the band
     * not carried between ticks (48 m would read as a route).
     */
    @Test
    fun `a walker off the route has it withheld, and the hop band is carried between ticks`() = runRecordingTest {
        val (vm, fixes) = recordThreePointsNorth(InMemoryTrackRepository())
        vm.startReturn()
        runCurrent()
        assertTrue(vm.uiState.value.routeHome is RouteHome.Ahead)

        // 0.001° of longitude at 45.002° N is 78.6 m.
        fixes.emit(gatedFix(lat = 45.002, lng = -122.0 + 0.001, t = 36_000L))
        runCurrent()
        advanceTimeBy(ROUTE_TICK_MILLIS)
        runCurrent()
        val off = vm.uiState.value.routeHome as RouteHome.Withheld
        assertEquals(RouteWithheldReason.OFF_ROUTE, off.reason)
        assertNull("no path-home figure off the route", vm.uiState.value.pathHome)

        fixes.emit(gatedFix(lat = 45.002, lng = -122.0 + 0.00061, t = 41_000L)) // 48 m
        runCurrent()
        advanceTimeBy(ROUTE_TICK_MILLIS)
        runCurrent()
        assertEquals(RouteWithheldReason.OFF_ROUTE, (vm.uiState.value.routeHome as RouteHome.Withheld).reason)

        fixes.emit(gatedFix(lat = 45.002, lng = -122.0 + 0.0005, t = 46_000L)) // 39 m
        runCurrent()
        advanceTimeBy(ROUTE_TICK_MILLIS)
        runCurrent()
        assertTrue(vm.uiState.value.routeHome is RouteHome.Ahead)
    }

    /**
     * Alert-delivery dispatch, Item 3: a silenced phone at record start sets the one-time warning,
     * a normal one sets none, and vibrate mode is not a silenced state for a vibration. The copy
     * is pinned as the owner's literal, not read from the constant. Fails with the audibility read
     * removed from startRecording (warning stays null).
     */
    @Test
    fun `a silenced phone at record start sets the trip-start warning`() = runRecordingTest {
        val vm = viewModel(alertAudibility = FakeAlertAudibility(AlertAudibilityState(RingerMode.SILENT, doNotDisturbOn = false, notificationsEnabled = true)))

        vm.startRecording()
        runCurrent()

        assertEquals("Your phone is silenced. If you go off track, the alert may not be felt.", vm.uiState.value.tripStartWarning?.message)
        vm.stopRecording()
    }

    @Test
    fun `a normal or vibrate-mode phone at record start sets no trip-start warning`() = runRecordingTest {
        val normal = viewModel()
        normal.startRecording()
        runCurrent()
        assertNull(normal.uiState.value.tripStartWarning)
        normal.stopRecording()

        val vibrate = viewModel(alertAudibility = FakeAlertAudibility(AlertAudibilityState(RingerMode.VIBRATE, doNotDisturbOn = false, notificationsEnabled = true)))
        vibrate.startRecording()
        runCurrent()
        assertNull(vibrate.uiState.value.tripStartWarning)
        vibrate.stopRecording()
    }

    /**
     * Dispatch 2026-09-28-626 (plan T14; Amendment 1, RECORD -627): the background prompt, through
     * startRecording and the real [com.zynergylabs.forager.app.domain.OffTrackReminderCheck]. Blocked
     * prompts with the owner's words, pinned as a literal; three recordings while blocked prompt once;
     * allowed, then blocked again, prompts again.
     */
    @Test
    fun `a phone blocking the reminder at record start prompts once per block`() = runRecordingTest {
        var phone = com.zynergylabs.forager.app.domain.BackgroundRun.BLOCKED
        val check = com.zynergylabs.forager.app.domain.OffTrackReminderCheck(
            { phone },
            com.zynergylabs.forager.app.domain.InMemoryOffTrackReminderPreferences(),
        ) { _, _, _ -> }
        val vm = viewModel(shouldPromptBackgroundRun = check::atRecordingStart)

        fun record(): String? {
            vm.startRecording()
            runCurrent()
            val prompt = vm.uiState.value.backgroundRunPrompt?.message
            vm.stopRecording()
            runCurrent()
            return prompt
        }

        val words = "To make sure your off-track reminder can buzz, let Forager run in the background"
        assertEquals(listOf(words, null, null), List(3) { record() })
        phone = com.zynergylabs.forager.app.domain.BackgroundRun.ALLOWED
        assertEquals(null, record())
        phone = com.zynergylabs.forager.app.domain.BackgroundRun.BLOCKED
        assertEquals("blocked again after being allowed prompts again", words, record())
    }

    @Test
    fun `an allowed phone at record start shows no background prompt`() = runRecordingTest {
        val check = com.zynergylabs.forager.app.domain.OffTrackReminderCheck(
            { com.zynergylabs.forager.app.domain.BackgroundRun.ALLOWED },
            com.zynergylabs.forager.app.domain.InMemoryOffTrackReminderPreferences(),
        ) { _, _, _ -> }
        val vm = viewModel(shouldPromptBackgroundRun = check::atRecordingStart)
        vm.startRecording()
        runCurrent()
        assertNull(vm.uiState.value.backgroundRunPrompt)
        vm.stopRecording()
    }

    /** The same text on a later trip must re-show, so each recording's warning carries a new id (the Snackbar effect is keyed on it). */
    @Test
    fun `the trip-start warning gets a new id on each recording so an identical message re-shows`() = runRecordingTest {
        val vm = viewModel(alertAudibility = FakeAlertAudibility(AlertAudibilityState(RingerMode.NORMAL, doNotDisturbOn = true, notificationsEnabled = true)))

        vm.startRecording()
        runCurrent()
        val first = vm.uiState.value.tripStartWarning
        vm.stopRecording()
        vm.startRecording()
        runCurrent()
        val second = vm.uiState.value.tripStartWarning

        assertEquals("Do Not Disturb is on. If you go off track, the alert may not be felt.", first?.message)
        assertEquals(first?.message, second?.message)
        assertTrue("expected a fresh id per recording, got ${first?.id} then ${second?.id}", first != null && second != null && second.id > first.id)
        vm.stopRecording()
    }

    /**
     * Timestamp-filter dispatch, Item 3: when the read seam is excluding most of the active track
     * (a non-aligned GPS clock), the breadcrumb poll raises the notice once per recording — not on
     * every poll. The stored track is replaced with what the seam would return for such a device:
     * three survivors, twelve excluded. Literal copy. Fails with the poll's notice removed (null).
     */
    @Test
    fun `the breadcrumb poll raises the network-fixes notice once per recording when most of the track is excluded`() = runRecordingTest {
        val trackRepository = InMemoryTrackRepository()
        val vm = viewModel(trackRepository)
        vm.startRecording()
        runCurrent()
        assertNull(vm.uiState.value.networkFixesNotice)

        trackRepository.create(
            Track(
                id = "track-1",
                name = null,
                startedAtEpochMillis = 1_000L,
                endedAtEpochMillis = null,
                points = listOf(point(lat = 45.0, lng = -122.0, t = 1_000L), point(lat = 45.001, lng = -122.0, t = 6_000L), point(lat = 45.002, lng = -122.0, t = 11_000L)),
                excludedPointCount = 12,
            ),
        )
        advanceTimeBy(POLL_INTERVAL_MILLIS)
        runCurrent()

        val first = vm.uiState.value.networkFixesNotice
        assertEquals(
            "Most of this track's fixes look like network fixes rather than GPS, so little or none of it is being drawn. It is still being recorded.",
            first?.message,
        )

        advanceTimeBy(POLL_INTERVAL_MILLIS)
        runCurrent()
        assertEquals("the notice must not be re-issued on the next poll", first, vm.uiState.value.networkFixesNotice)
        vm.stopRecording()
    }

    /** The evidence tracks' own proportion (8 of 17 excluded) is the rule working: no notice. */
    @Test
    fun `an ordinary exclusion raises no network-fixes notice`() = runRecordingTest {
        val trackRepository = InMemoryTrackRepository()
        val vm = viewModel(trackRepository)
        vm.startRecording()
        runCurrent()

        trackRepository.create(
            Track(
                id = "track-1",
                name = null,
                startedAtEpochMillis = 1_000L,
                endedAtEpochMillis = null,
                points = List(9) { point(lat = 45.0 + it * 0.001, lng = -122.0, t = 1_000L + it * 5_000L) },
                excludedPointCount = 8,
            ),
        )
        advanceTimeBy(POLL_INTERVAL_MILLIS)
        runCurrent()

        assertNull(vm.uiState.value.networkFixesNotice)
        vm.stopRecording()
    }


    // ── Dispatch 2026-09-28-497: the way back drawn (plan task T7) ──────────────────────────

    /**
     * Through the real entry points: Return draws the line (the route at Return and the route ahead);
     * a later tick moves only the line ahead; ending the return removes it. Fails with the ViewModel
     * not keeping the line, and with it kept after the return ends.
     */
    @Test
    fun `Return draws the way back, a later tick keeps the line at Return, and ending the return removes it`() = runRecordingTest {
        val (vm, _) = recordThreePointsNorth(InMemoryTrackRepository())
        assertNull("no line before Return", vm.uiState.value.routeLine)

        vm.startReturn()
        runCurrent()
        val first = vm.uiState.value.routeLine!!
        val ahead = vm.uiState.value.routeHome as RouteHome.Ahead
        assertEquals("the line at Return is the first route", ahead.route, first.atReturn)
        assertEquals(ahead.routeAhead, first.ahead)
        assertEquals("the three stored points, then the origin", 4, first.atReturn.size)

        advanceTimeBy(ROUTE_TICK_MILLIS)
        runCurrent()
        assertEquals("a later tick keeps the line at Return", first.atReturn, vm.uiState.value.routeLine!!.atReturn)

        vm.stopReturn()
        assertNull("ending the return removes the line", vm.uiState.value.routeLine)
    }

    /** Stopping the recording mid-return removes the line too. */
    @Test
    fun `stopping the recording while returning removes the way back`() = runRecordingTest {
        val (vm, _) = recordThreePointsNorth(InMemoryTrackRepository())
        vm.startReturn()
        runCurrent()
        assertTrue(vm.uiState.value.routeLine != null)
        vm.stopRecording()
        runCurrent()
        assertNull(vm.uiState.value.routeLine)
    }
}

private class NoOpLocationTracker : LocationTracker {
    override val fixes: Flow<LocationFix> = emptyFlow()
}


private class FakeLocationTracker(override val fixes: MutableSharedFlow<LocationFix>) : LocationTracker

/**
 * [InMemoryTrackRepository] with every [getById] of [gatedId] held until [release], so a test can change the
 * screen's state while a read of that track is suspended. Everything else passes straight through.
 */
private class GetByIdGatedTrackRepository(
    private val inner: InMemoryTrackRepository,
    private val gatedId: String,
) : TrackRepository by inner {
    private val gate = CompletableDeferred<Unit>()

    fun release() {
        gate.complete(Unit)
    }

    override suspend fun getById(id: String): Result<Track?> {
        if (id == gatedId) gate.await()
        return inner.getById(id)
    }
}

private class InMemoryTrackRepository : TrackRepository {
    private val tracks = mutableMapOf<String, Track>()
    val createdTrackIds = mutableListOf<String>()

    override suspend fun getAll(): Result<List<Track>> = Result.success(tracks.values.toList())
    override suspend fun getById(id: String): Result<Track?> = Result.success(tracks[id])
    override suspend fun getFullRecord(id: String): Result<List<TrackPointRecord>> =
        Result.success((tracks[id]?.points ?: emptyList()).map { TrackPointRecord(it, kept = !it.isNetworkProviderFix()) })
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

    override suspend fun setOriginWaypoint(trackId: String, waypointId: String): Result<Unit> {
        val existing = tracks[trackId] ?: return Result.success(Unit)
        tracks[trackId] = existing.copy(originWaypointId = waypointId)
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
    override suspend fun getFullRecord(id: String): Result<List<TrackPointRecord>> = Result.success(emptyList())
    override suspend fun getForDay(dayStartInclusiveEpochMillis: Long, dayEndExclusiveEpochMillis: Long): Result<List<Track>> =
        Result.success(emptyList())
    override suspend fun create(track: Track): Result<Unit> = Result.failure(RuntimeException("boom"))
    override suspend fun appendPoints(trackId: String, points: List<TrackPoint>): Result<Unit> = Result.success(Unit)
    override suspend fun end(trackId: String, endedAtEpochMillis: Long): Result<Unit> = Result.success(Unit)
    override suspend fun setOriginWaypoint(trackId: String, waypointId: String): Result<Unit> = Result.success(Unit)
    override suspend fun delete(id: String): Result<Unit> = Result.success(Unit)
}

private class FakeWaypointRepository : com.zynergylabs.forager.app.domain.WaypointRepository {
    private val waypoints = mutableMapOf<String, Waypoint>()

    /** Every id [delete] was called with, in order (journal redesign J4: the delete calls a pending delete defers). */
    val deletedIds = mutableListOf<String>()

    /** Puts a waypoint in place without going through [save], so a test's setup is not a write under test. */
    fun seed(waypoint: Waypoint) {
        waypoints[waypoint.id] = waypoint
    }

    override suspend fun getAll(): Result<List<Waypoint>> = Result.success(waypoints.values.toList())
    override suspend fun getForDay(dayStartInclusiveEpochMillis: Long, dayEndExclusiveEpochMillis: Long): Result<List<Waypoint>> =
        Result.success(
            waypoints.values.filter { it.createdAtEpochMillis in dayStartInclusiveEpochMillis until dayEndExclusiveEpochMillis },
        )
    // HUD-foundations dispatch, Item 3 — not exercised by this ViewModel's tests, so an explicit
    // "unsupported" rather than a fabricated answer (the same shape the MapPreferencesRepository
    // stubs in this suite use for methods outside their test's path).
    override suspend fun getById(id: String): Result<Waypoint?> =
        Result.failure(UnsupportedOperationException("getById is not part of this test's path"))
    // Dispatch -616 (walk-waypoints) changed these two from an explicit "unsupported": its tests read a
    // dropped waypoint back by its walk and delete the walk. Both mirror WaypointDao's queries: the walk's
    // waypoints oldest first, and every link to the walk nulled with the waypoints kept.
    override suspend fun getForTrack(trackId: String): Result<List<Waypoint>> =
        Result.success(waypoints.values.filter { it.trackId == trackId }.sortedBy { it.createdAtEpochMillis })
    override suspend fun detachFromTrack(trackId: String): Result<Unit> {
        waypoints.replaceAll { _, waypoint -> if (waypoint.trackId == trackId) waypoint.copy(trackId = null) else waypoint }
        return Result.success(Unit)
    }
    override suspend fun save(waypoint: Waypoint): Result<Unit> {
        waypoints[waypoint.id] = waypoint
        return Result.success(Unit)
    }
    override suspend fun delete(id: String): Result<Unit> {
        deletedIds += id
        waypoints.remove(id)
        return Result.success(Unit)
    }
}

/** Records every [Alert] handed over, in order — see [TrackRecordingViewModelTest.alertDelivery]. */
private class RecordingAlertDelivery : AlertDelivery {
    val alerts = mutableListOf<Alert>()
    override fun deliver(alert: Alert) {
        alerts += alert
    }
}

private class FakeAlertAudibility(private val state: AlertAudibilityState) : AlertAudibility {
    override fun current(): AlertAudibilityState = state
}

/** A phone that would deliver an alert normally — the default every test not about the warning runs with. */
private val AUDIBLE = AlertAudibilityState(ringerMode = RingerMode.NORMAL, doNotDisturbOn = false, notificationsEnabled = true)
