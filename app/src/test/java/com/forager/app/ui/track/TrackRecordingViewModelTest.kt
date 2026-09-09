package com.forager.app.ui.track

import com.forager.app.domain.Alert
import com.forager.app.domain.AlertAudibility
import com.forager.app.domain.AlertAudibilityState
import com.forager.app.domain.AlertDelivery
import com.forager.app.domain.AlertKind
import com.forager.app.domain.ComputeReturnToStartUseCase
import com.forager.app.domain.CreateWaypointUseCase
import com.forager.app.domain.CurrentTimeProvider
import com.forager.app.domain.DeleteWaypointUseCase
import com.forager.app.domain.DetectOffTrackUseCase
import com.forager.app.domain.GetTracksUseCase
import com.forager.app.domain.GetWaypointsUseCase
import com.forager.app.domain.LocationFix
import com.forager.app.domain.LocationTracker
import com.forager.app.domain.RingerMode
import com.forager.app.domain.StartTrackUseCase
import com.forager.app.domain.TrackRepository
import com.forager.app.domain.model.Track
import com.forager.app.domain.model.TrackPoint
import com.forager.app.domain.model.TrackRecordingMode
import com.forager.app.domain.model.WaypointDesignation
import java.time.ZoneOffset
import com.forager.app.domain.model.Waypoint
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
    ) = TrackRecordingViewModel(
        trackRepository = trackRepository,
        startTrack = StartTrackUseCase(trackRepository, currentTime = fixedTime, idGenerator = { "track-1" }),
        getWaypoints = GetWaypointsUseCase(waypointRepository),
        // A counter, not a fixed id: navigation HUD stage one creates an origin *and* an end
        // waypoint per recording, and a fixed id would make the second silently replace the first.
        createWaypoint = CreateWaypointUseCase(waypointRepository, currentTime = fixedTime, idGenerator = { "waypoint-${++waypointIds}" }),
        deleteWaypoint = DeleteWaypointUseCase(waypointRepository),
        computeReturnToStart = ComputeReturnToStartUseCase(),
        detectOffTrack = DetectOffTrackUseCase(),
        locationTracker = locationTracker,
        getTracks = GetTracksUseCase(trackRepository),
        alertDelivery = alertDelivery,
        alertAudibility = alertAudibility,
        currentTime = offTrackAlertClock,
        zone = ZoneOffset.UTC,
    ).also(createdViewModels::add)

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
        fixes.emit(LocationFix.Update(lat = 45.000, lng = -122.0, altitude = null, accuracyMeters = 5f, timestampEpochMillis = 1_000L))
        runCurrent()
        assertEquals(45.000, vm.uiState.value.originWaypoint!!.lat, 0.0)
        trackRepository.appendPoints(
            "track-1",
            listOf(45.000, 45.001, 45.002, 45.003, 45.002, 45.001).mapIndexed { i, lat -> point(lat = lat, t = 1_000L + i * 15_000L) },
        )
        fixes.emit(LocationFix.Update(lat = 45.001, lng = -122.0, altitude = null, accuracyMeters = 5f, timestampEpochMillis = 76_000L))
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
        fixes.emit(LocationFix.Update(lat = 45.000, lng = -122.0, altitude = null, accuracyMeters = 5f, timestampEpochMillis = 91_000L))
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

        fixes.emit(LocationFix.Update(lat = 45.001, lng = -122.0, altitude = null, accuracyMeters = null, timestampEpochMillis = 2_000L))
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

    @Test
    fun `moving steadily away from the start while returning sets off-track`() = runRecordingTest {
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
     * Field-test dispatch item 4, re-seated by the alert-delivery dispatch: the off-track alert is
     * handed to [AlertDelivery] directly from `returnToStart`, not signalled through UI state for a
     * composed observer — so this test counts deliveries on a fake, with no Compose anywhere in
     * it. That is the structural claim: the decision and the delivery are reachable with no
     * composed tree. Exactly one delivery per event, asserted as a count, not as the absence of a
     * second. Fails with the `alertDelivery.deliver` call removed (0 deliveries).
     */
    @Test
    fun `going off-track delivers exactly one alert, not one per fix`() = runRecordingTest {
        val trackRepository = InMemoryTrackRepository()
        val vm = viewModel(trackRepository, offTrackAlertClock = CurrentTimeProvider { 1_000L })

        vm.startRecording()
        runCurrent()
        trackRepository.appendPoints("track-1", listOf(point(lat = 45.0, lng = -122.0, t = 1_000L)))
        advanceTimeBy(POLL_INTERVAL_MILLIS)
        runCurrent()
        vm.startReturn()
        assertEquals(0, alertDelivery.alerts.size)

        vm.returnToStart(point(lat = 45.001, lng = -122.0, t = 2_000L))
        vm.returnToStart(point(lat = 45.002, lng = -122.0, t = 3_000L))
        vm.returnToStart(point(lat = 45.003, lng = -122.0, t = 4_000L))
        assertTrue(vm.uiState.value.isOffTrack)
        assertEquals(1, alertDelivery.alerts.size)
        // Off-track passes overridesSilence = true (owner decision) — asserted on the value the
        // delivery received, since the parameter exists precisely so it is never assumed.
        assertEquals(Alert(kind = AlertKind.OFF_TRACK, overridesSilence = true), alertDelivery.alerts.single())

        // Still off-track (net distance keeps increasing) on the very next fix, same clock instant
        // — the cooldown, not the heuristic, is what must keep this from delivering again immediately.
        vm.returnToStart(point(lat = 45.004, lng = -122.0, t = 5_000L))
        assertEquals(1, alertDelivery.alerts.size)

        vm.stopRecording()
    }

    @Test
    fun `a sustained drift alerts again once the cooldown elapses`() = runRecordingTest {
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
        assertEquals(1, alertDelivery.alerts.size)

        // Just short of the cooldown: still just the one alert.
        nowMillis += OFF_TRACK_ALERT_COOLDOWN_MILLIS - 1
        vm.returnToStart(point(lat = 45.004, lng = -122.0, t = 5_000L))
        assertEquals(1, alertDelivery.alerts.size)

        // Cooldown elapsed, and the drift continues: a second, real reminder.
        nowMillis += 1
        vm.returnToStart(point(lat = 45.005, lng = -122.0, t = 6_000L))
        assertEquals(2, alertDelivery.alerts.size)

        vm.stopRecording()
    }

    @Test
    fun `staying on track never delivers an alert`() = runRecordingTest {
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

        assertEquals(0, alertDelivery.alerts.size)
        vm.stopRecording()
    }

    @Test
    fun `stopReturn resets the cooldown so a later return attempt can alert immediately`() = runRecordingTest {
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
        assertEquals(1, alertDelivery.alerts.size)

        vm.stopReturn()
        vm.startReturn()
        // Same fixed clock instant as the first alert — without the cooldown reset on stopReturn(),
        // this would be blocked exactly like the immediate-repeat case above.
        vm.returnToStart(point(lat = 45.001, lng = -122.0, t = 5_000L))
        vm.returnToStart(point(lat = 45.002, lng = -122.0, t = 6_000L))
        vm.returnToStart(point(lat = 45.003, lng = -122.0, t = 7_000L))

        assertEquals(2, alertDelivery.alerts.size)
        vm.stopRecording()
    }

    @Test
    fun `moving steadily toward the start while returning stays on track`() = runRecordingTest {
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
    fun `moving away from the start does not set off-track unless actively returning`() = runRecordingTest {
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
    fun `stopping the recording clears returning and off-track state`() = runRecordingTest {
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

    // ---- Navigation HUD stage one: the auto-created origin and end waypoints -------------------
    // fixedTime is 1_000 ms after the epoch, so every default name reads "Jan 1, 12:00 AM" in UTC.
    // runCurrent(), never advanceUntilIdle(), while a recording is active: the breadcrumb poll is an
    // infinite delay loop, and advancing virtual time until idle never returns (found the hard way —
    // a 77-minute hung test worker). The origin/end saves have no delays, so runCurrent() runs them.
    // No per-test finally here any more: runRecordingTest stops every recording on the way out
    // (see the class doc comment), one mechanism for the whole class.

    private fun fix(lat: Double, accuracy: Float?, t: Long, altitude: Double? = null) =
        LocationFix.Update(lat = lat, lng = -122.0, altitude = altitude, accuracyMeters = accuracy, timestampEpochMillis = t)

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
        assertEquals("Start · Jan 1, 12:00 AM", origin.name)
        assertEquals(listOf(origin), waypointRepository.getAll().getOrThrow())
        assertEquals("waypoint-1", trackRepository.getById("track-1").getOrThrow()?.originWaypointId)
        assertEquals(listOf(origin), vm.uiState.value.waypoints)

        fixes.emit(fix(lat = 45.002, accuracy = 10f, t = 4_000L))
        runCurrent()
        assertEquals("a second gated fix must not create a second origin", 1, waypointRepository.getAll().getOrThrow().size)
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
        assertEquals("End · Jan 1, 12:00 AM", end.name)
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

    private fun point(lat: Double, lng: Double = -122.0, t: Long) =
        TrackPoint(lat = lat, lng = lng, altitude = null, accuracyMeters = null, timestampEpochMillis = t)

    private companion object {
        const val POLL_INTERVAL_MILLIS = 15_000L

        /** Mirrors TrackRecordingViewModel's own private constant of the same name — see its own doc comment. */
        const val OFF_TRACK_ALERT_COOLDOWN_MILLIS = 120_000L
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
    override suspend fun getForDay(dayStartInclusiveEpochMillis: Long, dayEndExclusiveEpochMillis: Long): Result<List<Track>> =
        Result.success(emptyList())
    override suspend fun create(track: Track): Result<Unit> = Result.failure(RuntimeException("boom"))
    override suspend fun appendPoints(trackId: String, points: List<TrackPoint>): Result<Unit> = Result.success(Unit)
    override suspend fun end(trackId: String, endedAtEpochMillis: Long): Result<Unit> = Result.success(Unit)
    override suspend fun setOriginWaypoint(trackId: String, waypointId: String): Result<Unit> = Result.success(Unit)
    override suspend fun delete(id: String): Result<Unit> = Result.success(Unit)
}

private class FakeWaypointRepository : com.forager.app.domain.WaypointRepository {
    private val waypoints = mutableMapOf<String, Waypoint>()

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
    override suspend fun getForTrack(trackId: String): Result<List<Waypoint>> =
        Result.failure(UnsupportedOperationException("getForTrack is not part of this test's path"))
    override suspend fun detachFromTrack(trackId: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("detachFromTrack is not part of this test's path"))
    override suspend fun save(waypoint: Waypoint): Result<Unit> {
        waypoints[waypoint.id] = waypoint
        return Result.success(Unit)
    }
    override suspend fun delete(id: String): Result<Unit> {
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
