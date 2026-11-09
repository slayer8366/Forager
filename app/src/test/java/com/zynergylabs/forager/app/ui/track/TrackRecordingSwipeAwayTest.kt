package com.zynergylabs.forager.app.ui.track

import com.zynergylabs.forager.app.ui.availability.onNavigateToWaypoint
import com.zynergylabs.forager.app.ui.availability.onStopWaypointNavigation
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zynergylabs.forager.app.domain.AbandonedTrackSweepOnce
import com.zynergylabs.forager.app.domain.Alert
import com.zynergylabs.forager.app.domain.AlertAudibility
import com.zynergylabs.forager.app.domain.AlertAudibilityState
import com.zynergylabs.forager.app.domain.AlertDelivery
import com.zynergylabs.forager.app.domain.AlertKind
import com.zynergylabs.forager.app.domain.ComputeReturnToStartUseCase
import com.zynergylabs.forager.app.domain.CreateWaypointUseCase
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.DeleteTrackUseCase
import com.zynergylabs.forager.app.domain.DeleteWaypointUseCase
import com.zynergylabs.forager.app.domain.EndAbandonedTracksUseCase
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.GetTrackOriginWaypointUseCase
import com.zynergylabs.forager.app.domain.GetTracksUseCase
import com.zynergylabs.forager.app.domain.GetWaypointsUseCase
import com.zynergylabs.forager.app.domain.InMemoryKeptTrackPaths
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.LocationTracker
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.ReturnWatch
import com.zynergylabs.forager.app.domain.ReturnWatchState
import com.zynergylabs.forager.app.domain.RingerMode
import com.zynergylabs.forager.app.domain.RouteHome
import com.zynergylabs.forager.app.domain.StartTrackUseCase
import com.zynergylabs.forager.app.domain.TrackRepository
import com.zynergylabs.forager.app.domain.WaypointRepository
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.TrackPointRecord
import com.zynergylabs.forager.app.domain.model.TrackRecordingMode
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.WaypointDesignation
import com.zynergylabs.forager.app.ui.availability.mapLayersViewModel
import com.zynergylabs.forager.app.ui.backup.BackupMessage
import com.zynergylabs.forager.app.ui.backup.BackupViewModel
import com.zynergylabs.forager.app.ui.backup.FakeBackupFiles
import com.zynergylabs.forager.app.ui.backup.FakeJournalBackup
import com.zynergylabs.forager.app.ui.backup.FakeSchedulePreferences
import com.zynergylabs.forager.app.ui.backup.FakeScheduler
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.advanceTimeBy
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
 * The screen's ViewModel and [ReturnWatch] together, on a plain JVM, around the moment the app is
 * swiped away from recents (dispatch 2026-09-28-400; Part 1 pinned the fault here, Amendment 2
 * moved the off-track decision out of the ViewModel).
 *
 * **What moved.** The decision, the returning flag, the rolling window and the cooldown are
 * [ReturnWatch]'s, held by the app's container and driven by the recording service. The ViewModel
 * calls it for Return and copies its state. So when the Activity is destroyed and this ViewModel
 * is cleared, the decision is still there, and it still delivers as long as something feeds it.
 *
 * **Who stands in for the service here.** No service runs on a plain JVM. [serviceBegins] and the
 * watch half of [fixArrives] are the three calls `TrackRecordingService` makes (begin, every raw
 * fix, end), made by hand. The real service making them is
 * `TrackRecordingServiceSwipeAwayTest`. A real fix reaches two listeners, the service's and the
 * ViewModel's own, so [fixArrives] sends each fix both ways.
 *
 * **What stands in for the swipe-away:** `ViewModelStore.clear()`, as in Part 1. On the S22 the
 * real swipe was checked on 2026-10-02 (the report's "Phone check").
 *
 * ## The reopened screen (Amendment 3, Part 3a)
 *
 * Part 2 left two tests here pinning a fault: a ViewModel built after the swipe did not know a
 * recording was running, and pressing Record made a second track. They are replaced by the tests
 * of the fixed behaviour. A ViewModel built while the watch is begun **takes the recording up**:
 * it shows it as running, with its breadcrumbs, its start marker and the return that was under
 * way. The begun watch is what it goes by, never an open track row, because a killed process
 * leaves the same row.
 *
 * ## The poll loop
 *
 * `beginPolling` is an unbounded `delay` loop while recording (`CLAUDE.md`, "A stalled test run in
 * this repo is an unstopped poll loop"). [runRecordingTest] stops every ViewModel this class
 * built, in a `finally`, inside the test body. Only `runCurrent` and `advanceTimeBy`, never
 * `advanceUntilIdle`, once a recording has started.
 */
class TrackRecordingSwipeAwayTest {

    private val dispatcher = StandardTestDispatcher()
    private val createdViewModels = mutableListOf<TrackRecordingViewModel>()

    @Before fun setUpMain() = Dispatchers.setMain(dispatcher)

    @After fun tearDownMain() = Dispatchers.resetMain()

    private fun runRecordingTest(body: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        try {
            body()
        } finally {
            createdViewModels.forEach { it.stopRecording() }
        }
    }

    /** Every alert handed over, in order. One instance per test method. */
    private val delivered = mutableListOf<Alert>()

    /** What the ViewModels logged: tag and message. */
    private val logged = mutableListOf<String>()

    /** Storage, the fix stream and the watch: what outlives a destroyed Activity, shared by both ViewModels of a test. */
    private val tracks = SwipeAwayTracks()
    private val waypoints = SwipeAwayWaypoints()
    private val fixes = MutableSharedFlow<LocationFix>()
    private var trackIds = 0
    private var waypointIds = 0
    private val clock = CurrentTimeProvider { 1_000L }

    /** What the phone's silence state is when a ViewModel reads it at Record. Silenced makes the trip-start warning. */
    private var ringerMode = RingerMode.NORMAL
    private val returnWatch = ReturnWatch(ComputeReturnToStartUseCase(), { delivered += it })

    /**
     * The process's one sweep of tracks left open by an earlier process, as the container holds
     * it. This test's "process" started at [PROCESS_START], before the clock's 1,000 that every
     * recording here starts at, so no recording made in a test is ever a candidate.
     */
    private val sweepOnce = AbandonedTrackSweepOnce(
        EndAbandonedTracksUseCase(tracks, watchedTrackId = { returnWatch.state.value.trackId }, errorLog = { _, message, _ -> logged += message }),
        processStartedAtEpochMillis = PROCESS_START,
    )

    /** Whether a service is recording, in this test's world: only then does a fix reach the watch. */
    private var serviceRecording = false

    private fun buildViewModel() = TrackRecordingViewModel(
        trackRepository = tracks,
        // A counter, not a fixed id: the second-recording test needs the second row to be a second row.
        startTrack = StartTrackUseCase(tracks, currentTime = clock, idGenerator = { "track-${++trackIds}" }),
        getWaypoints = GetWaypointsUseCase(waypoints),
        createWaypoint = CreateWaypointUseCase(waypoints, currentTime = clock, idGenerator = { "waypoint-${++waypointIds}" }),
        deleteWaypoint = DeleteWaypointUseCase(waypoints),
        deleteTrack = DeleteTrackUseCase(tracks, waypoints, InMemoryKeptTrackPaths()),
        computeReturnToStart = ComputeReturnToStartUseCase(),
        locationTracker = object : LocationTracker { override val fixes = this@TrackRecordingSwipeAwayTest.fixes },
        getTracks = GetTracksUseCase(tracks),
        returnWatch = returnWatch,
        alertAudibility = object : AlertAudibility {
            override fun current() = AlertAudibilityState(ringerMode, doNotDisturbOn = false, notificationsEnabled = true)
        },
        errorLog = ErrorLog { _, message, _ -> logged += message },
        currentTime = clock,
        zone = ZoneOffset.UTC,
        getTrackOriginWaypoint = GetTrackOriginWaypointUseCase(tracks, waypoints),
        alreadyRecordingMessage = ALREADY_RECORDING,
        abandonedTrackSweepOnce = sweepOnce,
    ).also(createdViewModels::add)

    /** A ViewModel held the way an Activity holds one, so [ViewModelStore.clear] reaches its `onCleared`. */
    private fun viewModelIn(store: ViewModelStore): TrackRecordingViewModel =
        ViewModelProvider(store, viewModelFactory { initializer { buildViewModel() } })[TrackRecordingViewModel::class.java]

    /** `TrackRecordingService.startRecording`'s call. */
    private fun serviceBegins(trackId: String) {
        returnWatch.begin(trackId, TrackRecordingMode.HIGH_ACCURACY)
        serviceRecording = true
    }

    /** `TrackRecordingService.stopRecording`'s call. */
    private fun serviceEnds(trackId: String) {
        returnWatch.end(trackId)
        serviceRecording = false
    }

    /** One fix from the platform: to the service's listener (so to the watch) and to the ViewModel's own. */
    private suspend fun TestScope.fixArrives(lat: Double, t: Long) {
        val fix = LocationFix.Update(lat = lat, lng = -122.0, altitude = null, accuracyMeters = 5f, timestampEpochMillis = t, provider = FixProvider.GPS)
        if (serviceRecording) returnWatch.onFix(TrackPoint(fix.lat, fix.lng, fix.altitude, fix.accuracyMeters, fix.timestampEpochMillis), fix.provider)
        fixes.emit(fix)
        runCurrent()
    }

    /**
     * Record, the service begins, the first gated fix seeds the origin at 45.000, Return, and one
     * reading 111 m north of the start. One reading is below the three-reading window, so nothing
     * has alerted yet. Everything through the real entry points.
     */
    private suspend fun TestScope.recordAndStartReturning(vm: TrackRecordingViewModel) {
        vm.startRecording(TrackRecordingMode.HIGH_ACCURACY)
        runCurrent()
        serviceBegins(vm.uiState.value.activeTrack!!.trackId)
        fixArrives(lat = 45.000, t = 2_000L)
        assertEquals("precondition: the origin is seeded from the first gated fix", 45.000, vm.uiState.value.originWaypoint?.lat)
        vm.startReturn()
        runCurrent()
        fixArrives(lat = 45.001, t = 7_000L)
        assertTrue("precondition: a return is under way", vm.uiState.value.isReturning)
        assertEquals("precondition: one reading is not yet a trend, so nothing has been delivered", 0, delivered.size)
    }

    /**
     * Each about 111 m further from the start than the last. With the reading
     * [recordAndStartReturning] already took, the second of these completes a three-reading window
     * whose net increase is far past the 25 m threshold.
     */
    private suspend fun TestScope.walkAwayFromTheStart() {
        fixArrives(lat = 45.002, t = 12_000L)
        fixArrives(lat = 45.003, t = 17_000L)
        fixArrives(lat = 45.004, t = 22_000L)
    }

    /**
     * The control from Part 1, kept: with the screen alive, the readings in [walkAwayFromTheStart]
     * deliver exactly one off-track alert. Since Amendment 2 it also shows the copy: the screen's
     * "off track now" is the watch's.
     */
    @Test
    fun `control - with the screen's ViewModel alive, walking away from the start delivers exactly one off-track alert`() = runRecordingTest {
        val vm = viewModelIn(ViewModelStore())
        recordAndStartReturning(vm)

        walkAwayFromTheStart()

        assertEquals(listOf(Alert(kind = AlertKind.OFF_TRACK, overridesSilence = false)), delivered)
        assertTrue(vm.uiState.value.isOffTrack)
        vm.stopRecording()
    }

    /**
     * Dispatch 2026-09-28-400, the first path:
     * `Recording, returning > the app is swiped away > the walker moves away > the alert arrives`.
     *
     * **This was Part 1's fault test.** It asserted that nothing was delivered. The assertion that
     * turned is the last one: an empty list then, one off-track alert now. The readings are the
     * control's. The ViewModel is cleared and reads no fixes; the decision is the watch's, and the
     * service (stood in for here) goes on feeding it.
     */
    @Test
    fun `with a return under way and the ViewModel cleared, walking away from the start still delivers the off-track alert`() = runRecordingTest {
        val store = ViewModelStore()
        val vm = viewModelIn(store)
        recordAndStartReturning(vm)
        assertEquals("precondition: the ViewModel is the stream's one collector here", 1, fixes.subscriptionCount.value)

        // The swipe-away, as far as this ViewModel can tell: the Activity's store is cleared.
        store.clear()
        runCurrent()

        assertEquals("the cleared ViewModel reads no fixes", 0, fixes.subscriptionCount.value)
        assertTrue("the return is still under way: it is the watch's, and the watch was not cleared", returnWatch.state.value.isReturning)

        walkAwayFromTheStart()

        assertEquals(
            "the walker moved steadily away from the start on a return, with no ViewModel alive, and the alert was delivered",
            listOf(Alert(kind = AlertKind.OFF_TRACK, overridesSilence = false)),
            delivered,
        )
    }

    /**
     * The handover itself (Amendment 2, step 3): Return on the screen is Return on the watch, and
     * what the watch decides is what the screen shows. The second half is driven with no fix
     * reaching the ViewModel at all, so the screen's "off track now" can only have come from the
     * watch.
     */
    @Test
    fun `Return on the screen marks the watch as returning, and the watch's off-track shows on the screen`() = runRecordingTest {
        val vm = viewModelIn(ViewModelStore())
        vm.startRecording(TrackRecordingMode.HIGH_ACCURACY)
        runCurrent()
        serviceBegins("track-1")
        fixArrives(lat = 45.000, t = 2_000L)

        vm.startReturn()
        assertTrue("the watch is returning the moment the screen's callback returns", returnWatch.state.value.isReturning)
        assertTrue("and the screen says so without waiting", vm.uiState.value.isReturning)

        // To the watch only, as the service feeds it. Nothing is emitted on the ViewModel's own stream.
        // Dispatch 2026-09-28-425: four readings over 15 s (was three over 10 s), the new rule's hold.
        listOf(45.001 to 7_000L, 45.002 to 12_000L, 45.003 to 17_000L, 45.004 to 22_000L).forEach { (lat, t) ->
            returnWatch.onFix(TrackPoint(lat, -122.0, null, 5f, t), FixProvider.GPS)
        }
        runCurrent()

        assertTrue("the watch decided off track", returnWatch.state.value.isOffTrack)
        assertTrue("and the screen's copy follows", vm.uiState.value.isOffTrack)
        assertEquals(1, delivered.size)

        vm.stopReturn()
        assertFalse(returnWatch.state.value.isReturning)
        assertFalse(vm.uiState.value.isReturning)
        assertFalse(vm.uiState.value.isOffTrack)
        vm.stopRecording()
    }

    /**
     * Amendment 2, ruling 3: Record, then Return before the service has begun the watch. The
     * service begins a moment after the Record tap; a Return tapped inside that moment is kept.
     */
    @Test
    fun `Return tapped before the service has begun the watch is not lost`() = runRecordingTest {
        val vm = viewModelIn(ViewModelStore())
        vm.startRecording(TrackRecordingMode.HIGH_ACCURACY)
        runCurrent()

        vm.startReturn() // the service has not begun the watch yet
        assertTrue(vm.uiState.value.isReturning)

        serviceBegins("track-1")
        runCurrent()
        assertTrue("the service beginning the same track keeps the return", vm.uiState.value.isReturning)
        assertEquals(ReturnWatchState(trackId = "track-1", isBegun = true, mode = TrackRecordingMode.HIGH_ACCURACY, isReturning = true), returnWatch.state.value)
        vm.stopRecording()
    }

    /**
     * The start point is handed to the watch by the ViewModel whenever it changes, and the watch
     * keeps the last one: the first breadcrumb until the origin waypoint exists, then the origin,
     * the same rule the screen's own distance follows. Read back as the watch's distance to it.
     */
    @Test
    fun `the watch measures to the first breadcrumb until the origin waypoint exists, then to the origin`() = runRecordingTest {
        val vm = viewModelIn(ViewModelStore())
        vm.startRecording(TrackRecordingMode.HIGH_ACCURACY)
        runCurrent()
        serviceBegins("track-1")
        tracks.appendForTest("track-1", TrackPoint(45.000, -122.0, null, null, 1_000L))
        advanceTimeBy(POLL_INTERVAL_MILLIS)
        runCurrent()

        returnWatch.onFix(TrackPoint(45.002, -122.0, null, 5f, 2_000L), FixProvider.GPS)
        assertEquals("to the first breadcrumb at 45.000", 222.4, returnWatch.state.value.returnToStart!!.distanceMeters, 1.0)

        fixArrives(lat = 45.001, t = 3_000L) // the first gated fix: the origin waypoint, at 45.001
        assertEquals(45.001, vm.uiState.value.originWaypoint?.lat)
        returnWatch.onFix(TrackPoint(45.002, -122.0, null, 5f, 4_000L), FixProvider.GPS)
        assertEquals("to the origin at 45.001", 111.2, returnWatch.state.value.returnToStart!!.distanceMeters, 1.0)
        vm.stopRecording()
    }

    /**
     * A stop from the notification ends the recording in the service, which ends the watch. The
     * screen's copy of the return follows at once, before the screen has re-read the track's row.
     */
    @Test
    fun `when the service ends the watch, the screen's return ends with it`() = runRecordingTest {
        val vm = viewModelIn(ViewModelStore())
        recordAndStartReturning(vm)

        serviceEnds("track-1")
        runCurrent()

        assertFalse(vm.uiState.value.isReturning)
        assertFalse(vm.uiState.value.isOffTrack)
        vm.stopRecording()
    }

    /**
     * The owner's main path (2026-10-02, "Sounds good"):
     * `Recording > swipe the app away > open Forager again > the screen shows the recording still
     * running, and the return HUD if you were heading back`.
     *
     * **This replaces Part 2's first `PART 3 FAULT` pin.** The assertions that turned:
     * `isRecording` and `isReturning` were false and are true; `activeTrack` and `originWaypoint`
     * were null and are the running track and its start marker.
     *
     * The HUD shows while `isReturning` is true and points at `originWaypoint`
     * (`AvailabilityScreen`'s `isNavigating`), so those two are the HUD's state. The breadcrumbs
     * are the row's, and the screen's own poll goes on reading them. The trip-start warning is not
     * shown again: it belongs to a Record tap, and this is not one.
     */
    @Test
    fun `Recording, swipe the app away, open Forager again - the screen shows the recording still running, and the return that was under way`() = runRecordingTest {
        ringerMode = RingerMode.SILENT
        val firstStore = ViewModelStore()
        val first = viewModelIn(firstStore)
        recordAndStartReturning(first)
        assertEquals("precondition: the Record tap on a silenced phone raised the trip-start warning", 1, first.uiState.value.tripStartWarning?.id)
        tracks.appendForTest("track-1", TrackPoint(45.000, -122.0, null, 5f, 2_000L))
        tracks.appendForTest("track-1", TrackPoint(45.001, -122.0, null, 5f, 7_000L))
        firstStore.clear()
        runCurrent()

        // Forager is opened again: a new Activity, so a new store and a new ViewModel over the same storage.
        val reopened = viewModelIn(ViewModelStore())
        runCurrent()

        val state = reopened.uiState.value
        assertTrue("the record button reads this: it offers Stop", state.isRecording)
        assertEquals(ActiveTrack(trackId = "track-1", startedAtEpochMillis = 1_000L, mode = TrackRecordingMode.HIGH_ACCURACY), state.activeTrack)
        assertTrue("the return that was under way is shown", state.isReturning)
        assertEquals("the start marker the HUD points at is the one made before the swipe", "waypoint-1", state.originWaypoint?.id)
        assertEquals("the breadcrumbs are the row's", listOf(45.000, 45.001), state.breadcrumbPoints.map { it.lat })
        assertNull("taking a recording up is not a Record tap: no trip-start warning", state.tripStartWarning)
        assertEquals("the reopened screen reads fixes for itself again", 1, fixes.subscriptionCount.value)
        assertEquals("still one track", listOf("track-1"), tracks.getAll().getOrThrow().map { it.id })

        // Its own poll runs: a point the service writes later reaches the screen.
        tracks.appendForTest("track-1", TrackPoint(45.002, -122.0, null, 5f, 12_000L))
        advanceTimeBy(POLL_INTERVAL_MILLIS)
        runCurrent()
        assertEquals(3, reopened.uiState.value.breadcrumbPoints.size)

        // And the return is still live: the walk away alerts, and the reopened screen shows it.
        walkAwayFromTheStart()
        assertEquals(1, delivered.size)
        assertTrue(reopened.uiState.value.isOffTrack)

        // Stop on that screen. (That the service then ends the track is TrackRecordingServiceSwipeAwayTest's.)
        reopened.stopRecording()
        assertFalse(reopened.uiState.value.isRecording)
        assertFalse(reopened.uiState.value.isReturning)
    }

    /**
     * Dispatch 2026-09-28-423: a return the reopened screen takes up gets the route home, with no
     * second Return tap. The route tick follows the screen's returning flag, whichever way it was
     * set. Fails with the tick started only by this screen's own Return (the route stays null).
     */
    @Test
    fun `a return taken up by the reopened screen gets the route home without a second Return tap`() = runRecordingTest {
        val firstStore = ViewModelStore()
        val first = viewModelIn(firstStore)
        recordAndStartReturning(first)
        tracks.appendForTest("track-1", TrackPoint(45.000, -122.0, null, 5f, 2_000L))
        tracks.appendForTest("track-1", TrackPoint(45.001, -122.0, null, 5f, 7_000L))
        firstStore.clear()
        runCurrent()

        val reopened = viewModelIn(ViewModelStore())
        runCurrent()
        assertTrue("precondition: the return that was under way is shown", reopened.uiState.value.isReturning)
        fixArrives(lat = 45.001, t = 12_000L)
        advanceTimeBy(ROUTE_TICK_MILLIS)
        runCurrent()

        val route = reopened.uiState.value.routeHome
        assertTrue("the reopened screen has a route home, not $route", route is RouteHome.Ahead)
        assertEquals(111.19508, (route as RouteHome.Ahead).routeMeters, 0.01)
    }

    /**
     * The recording ended while the app was away (Stop from the notification, or the service
     * gone): the watch is not begun, nothing is taken up, and the screen offers Record. The row is
     * still open here, as a killed process leaves it, to show the row is not what is gone by.
     */
    @Test
    fun `the recording ended while the app was away - nothing is taken up, even with the track row still open`() = runRecordingTest {
        val firstStore = ViewModelStore()
        recordAndStartReturning(viewModelIn(firstStore))
        firstStore.clear()
        serviceEnds("track-1")
        runCurrent()
        assertNull("precondition: the row is open, as a killed process leaves it", tracks.getById("track-1").getOrThrow()?.endedAtEpochMillis)

        val reopened = viewModelIn(ViewModelStore())
        runCurrent()
        reopened.onEnteredForeground()
        runCurrent()

        assertFalse(reopened.uiState.value.isRecording)
        assertNull(reopened.uiState.value.activeTrack)
        assertFalse(reopened.uiState.value.isReturning)
        assertEquals("no fix collection was started", 0, fixes.subscriptionCount.value)
    }

    /**
     * The second moment a recording is taken up: when the app comes to the foreground. Here the
     * first try, at creation, could not read the track's row. That is logged and nothing is taken
     * up; "could not read the row" is not treated as "no recording". The next `ON_START` tries
     * again and takes it up.
     */
    @Test
    fun `a recording that could not be read when the screen was created is taken up when the app next comes to the foreground`() = runRecordingTest {
        val firstStore = ViewModelStore()
        recordAndStartReturning(viewModelIn(firstStore))
        firstStore.clear()
        runCurrent()

        tracks.failReads = true
        logged.clear()
        val reopened = viewModelIn(ViewModelStore())
        runCurrent()
        assertFalse(reopened.uiState.value.isRecording)
        assertTrue("the failed read is logged, naming the track: $logged", logged.any { it == "Couldn't read track 'track-1' to take up its recording; the screen shows no recording." })

        tracks.failReads = false
        reopened.onEnteredForeground() // what MainActivity's lifecycle observer calls on ON_START
        runCurrent()

        assertEquals("track-1", reopened.uiState.value.activeTrack?.trackId)
        assertTrue(reopened.uiState.value.isReturning)
        reopened.stopRecording()
    }

    /**
     * The pre-build report's flag 4, closed by taking the recording up: Restore from backup is
     * refused while a track is recording, and the guard reads this ViewModel's `isRecording`
     * (`MainActivity` wires it so). Before, a reopened screen read "not recording" while the
     * service was writing points, and a restore was allowed.
     */
    @Test
    fun `after the reopened screen takes up the recording, a restore from backup is refused as during any recording`() = runRecordingTest {
        val firstStore = ViewModelStore()
        recordAndStartReturning(viewModelIn(firstStore))
        firstStore.clear()
        runCurrent()
        val reopened = viewModelIn(ViewModelStore())
        runCurrent()

        val backup = FakeJournalBackup()
        val backupViewModel = BackupViewModel(
            backup = backup,
            preferences = FakeSchedulePreferences(),
            scheduler = FakeScheduler(),
            files = FakeBackupFiles(),
            errorLog = ErrorLog { _, _, _ -> },
            ioDispatcher = Dispatchers.Unconfined,
            // MainActivity's own wiring: isRecording = { trackRecordingViewModel.uiState.value.isRecording }
            isRecording = { reopened.uiState.value.isRecording },
        )
        runCurrent()

        val allowed = backupViewModel.controls(backupViewModel.uiState.value).onRestoreRequested()

        assertFalse(allowed)
        assertEquals(BackupMessage.RESTORE_BLOCKED_WHILE_RECORDING, backupViewModel.uiState.value.message)
        assertTrue(backup.restored.isEmpty())
        reopened.stopRecording()
    }

    /**
     * The owner's path 3: `Record pressed while another recording is running`, answered with the
     * words "Forager is already recording a track. Stop it before starting another."
     *
     * **This replaces Part 2's second `PART 3 FAULT` pin.** The assertion that turned: two open
     * track rows then, one now. Once a recording is taken up the screen offers Stop, so Record is
     * only reachable in this state when the take-up has not happened; here the row cannot be
     * read. The guard is behind the screen, and goes by the watch.
     */
    @Test
    fun `Record pressed while another recording is running starts nothing and says so in the owner's words`() = runRecordingTest {
        val firstStore = ViewModelStore()
        recordAndStartReturning(viewModelIn(firstStore))
        firstStore.clear()
        runCurrent()
        tracks.failReads = true
        val reopened = viewModelIn(ViewModelStore())
        runCurrent()
        assertFalse("precondition: the recording was not taken up, so the screen offers Record", reopened.uiState.value.isRecording)

        reopened.startRecording(TrackRecordingMode.HIGH_ACCURACY) // what the record button calls when isRecording is false
        runCurrent()

        assertEquals("Forager is already recording a track. Stop it before starting another.", reopened.uiState.value.startRecordingErrorMessage)
        assertNull("no recording was started on this screen", reopened.uiState.value.activeTrack)
        tracks.failReads = false
        assertEquals("no second track row was made", listOf("track-1"), tracks.getAll().getOrThrow().map { it.id })
        assertEquals("the watch is still for the first track", "track-1", returnWatch.state.value.trackId)
    }

    /**
     * The owner's path 2: `a recording swiped away before the phone had a good GPS fix, so no start
     * marker was made > reopen the app > a start marker is placed at the track's first recorded
     * point`. Never where the phone is at the reopen.
     *
     * No gated fix reaches the first ViewModel, so it makes no origin. The service has written two
     * points. On the reopen the marker is made at the first of them, 45.000. A good fix then
     * arrives at 45.500, where the walker is now: the marker does not move, no second one is made,
     * and the watch measures to the same point the screen shows.
     */
    @Test
    fun `a recording taken up without a start marker gets one at its first recorded point, not at the fix after the reopen`() = runRecordingTest {
        val firstStore = ViewModelStore()
        val first = viewModelIn(firstStore)
        first.startRecording(TrackRecordingMode.HIGH_ACCURACY)
        runCurrent()
        serviceBegins("track-1")
        assertNull("precondition: no good fix, so no start marker", first.uiState.value.originWaypoint)
        tracks.appendForTest("track-1", TrackPoint(45.000, -122.0, 120.0, 5f, 2_000L))
        tracks.appendForTest("track-1", TrackPoint(45.001, -122.0, null, 5f, 7_000L))
        firstStore.clear()
        runCurrent()

        val reopened = viewModelIn(ViewModelStore())
        runCurrent()

        val origin = requireNotNull(reopened.uiState.value.originWaypoint) { "a start marker was expected at the first recorded point" }
        assertEquals(45.000, origin.lat, 1e-9)
        assertEquals(120.0, origin.altitude)
        assertEquals(WaypointDesignation.ORIGIN, origin.designation)
        assertEquals("track-1", origin.trackId)
        assertEquals("the track row points at it", origin.id, tracks.getById("track-1").getOrThrow()?.originWaypointId)

        fixArrives(lat = 45.500, t = 60_000L) // a good fix, where the walker is now
        assertEquals("the marker stays at the first recorded point", 45.000, reopened.uiState.value.originWaypoint!!.lat, 1e-9)
        assertEquals("and no second start marker was made", 1, waypoints.getAll().getOrThrow().count { it.designation == WaypointDesignation.ORIGIN })
        assertEquals(
            "the watch measures to the same start the screen shows: 0.5 degrees of latitude, not zero",
            55_597.0,
            returnWatch.state.value.returnToStart!!.distanceMeters,
            60.0,
        )
        reopened.stopRecording()
    }

    /**
     * Path 2 when the track has no recorded point yet at the reopen: no marker is made until one
     * exists, and it is then made from that first recorded point. A good fix arriving in between
     * makes none.
     */
    @Test
    fun `a recording taken up with no recorded point yet gets its start marker when the first point is recorded, at that point`() = runRecordingTest {
        val firstStore = ViewModelStore()
        val first = viewModelIn(firstStore)
        first.startRecording(TrackRecordingMode.HIGH_ACCURACY)
        runCurrent()
        serviceBegins("track-1")
        firstStore.clear()
        runCurrent()

        val reopened = viewModelIn(ViewModelStore())
        runCurrent()
        assertTrue(reopened.uiState.value.isRecording)
        assertNull("no recorded point yet, so no marker", reopened.uiState.value.originWaypoint)

        fixArrives(lat = 45.500, t = 60_000L) // a good fix after the reopen
        assertNull("the fix received after the reopen never makes the marker", reopened.uiState.value.originWaypoint)
        assertTrue(waypoints.getAll().getOrThrow().isEmpty())

        tracks.appendForTest("track-1", TrackPoint(45.250, -122.0, null, 5f, 61_000L)) // the service's first kept point
        advanceTimeBy(POLL_INTERVAL_MILLIS)
        runCurrent()

        assertEquals(45.250, reopened.uiState.value.originWaypoint?.lat)
        assertEquals(1, waypoints.getAll().getOrThrow().size)
        reopened.stopRecording()
    }

    // ---- The window after Stop (found by the planner's review of Part 3a) ------------------------
    //
    // stopRecording() clears this screen's recording at once. The service is stopped a moment
    // later: MainActivity's effect sends ACTION_STOP after the next recomposition, and the service
    // ends the watch only when it handles that. In between, the watch is still begun for the track
    // this screen has just stopped, and the screen has no active track: exactly the state the
    // refusal and the take-up act on. In these tests the service simply has not ended the watch yet.

    /**
     * Stop, then Record again at once (a second quick tap on the same button), before the service
     * has handled the stop. As before Part 3a: a new recording starts. The recording this screen
     * has just stopped is not "another recording running", and the user who stopped it must not be
     * told to stop it.
     */
    @Test
    fun `Stop then Record at once, before the service has handled the stop, starts a new recording and refuses nothing`() = runRecordingTest {
        val vm = viewModelIn(ViewModelStore())
        vm.startRecording(TrackRecordingMode.HIGH_ACCURACY)
        runCurrent()
        serviceBegins("track-1")

        vm.stopRecording()
        assertTrue("precondition: the service has not handled the stop, so the watch is still begun for the stopped track", returnWatch.state.value.isBegun)
        logged.clear()
        vm.startRecording(TrackRecordingMode.HIGH_ACCURACY)
        runCurrent()

        assertNull("nothing was refused", vm.uiState.value.startRecordingErrorMessage)
        assertEquals("a new recording started, on a new track", "track-2", vm.uiState.value.activeTrack?.trackId)
        assertEquals(emptyList<String>(), logged)
        vm.stopRecording()
    }

    /** The same window, reached by the app coming to the foreground: the recording this screen has just stopped is not taken back up. */
    @Test
    fun `Stop, then the app comes to the foreground before the service has handled the stop - the stopped recording is not taken back up`() = runRecordingTest {
        val vm = viewModelIn(ViewModelStore())
        vm.startRecording(TrackRecordingMode.HIGH_ACCURACY)
        runCurrent()
        serviceBegins("track-1")

        vm.stopRecording()
        vm.onEnteredForeground()
        runCurrent()

        assertFalse("the screen stays stopped", vm.uiState.value.isRecording)
        assertNull(vm.uiState.value.activeTrack)
        assertEquals("and reads no fixes", 0, fixes.subscriptionCount.value)
    }

    /**
     * The other side: what this screen remembers is only the recording it stopped itself, and only
     * until the watch has moved on. Once the service has ended that one, a recording begun for
     * another track is still refused against, in the owner's words.
     */
    @Test
    fun `after its own stopped recording has ended, Record is still refused while a different recording is running`() = runRecordingTest {
        val vm = viewModelIn(ViewModelStore())
        vm.startRecording(TrackRecordingMode.HIGH_ACCURACY)
        runCurrent()
        serviceBegins("track-1")
        vm.stopRecording()
        serviceEnds("track-1")

        tracks.failReads = true // so the running recording cannot be taken up, and Record is what the screen offers
        serviceBegins("track-9")
        vm.startRecording(TrackRecordingMode.HIGH_ACCURACY)
        runCurrent()

        assertEquals(ALREADY_RECORDING, vm.uiState.value.startRecordingErrorMessage)
        assertNull(vm.uiState.value.activeTrack)
    }

    // ---- Tracks left open that nothing is recording (Amendment 3, Part 3b) ---------------------

    /**
     * The owner's path 1, answered "A": `Open Records > a track nothing is recording any more > it
     * shows as a normal finished track, ended at its last recorded point; Delete is offered as for
     * any track`.
     *
     * The first recording ViewModel created in a process launches the sweep. When it finishes the
     * ViewModel reads its track list again, so the track reads as finished on this screen at once,
     * without Records being reopened. Records shows "finished" and offers Delete from the row's
     * end time (`canBeDeleted`), and the ViewModel's own refusal to delete a recording track goes
     * by the same field, so those are what is asserted.
     */
    @Test
    fun `Open Records, a track nothing is recording any more - it is a finished track ended at its last recorded point, and it can be deleted`() = runRecordingTest {
        tracks.seedOpen("stuck", startedAt = 100L, TrackPoint(45.0, -122.0, null, 5f, 300L), TrackPoint(45.001, -122.0, null, 5f, 700L))

        val vm = viewModelIn(ViewModelStore())
        runCurrent()

        val shown = vm.uiState.value.tracks.single { it.id == "stuck" }
        assertEquals("ended at its last recorded point", 700L, shown.endedAtEpochMillis)
        assertTrue("Records offers Delete for it, as for any finished track", shown.canBeDeleted)
        assertEquals("no point was removed", 2, tracks.storedPointCount("stuck"))

        logged.clear()
        vm.requestRemoveTrack("stuck")
        assertEquals("and the delete is no longer refused as 'still recording'", "stuck", vm.uiState.value.pendingTrackDelete?.item?.id)
        assertEquals(emptyList<String>(), logged)
    }

    /** Once per process: a second recording ViewModel (a second Activity, or the screen reopened) does not sweep again. */
    @Test
    fun `a second ViewModel in the same process does not run the sweep again`() = runRecordingTest {
        tracks.seedOpen("stuck", startedAt = 100L, TrackPoint(45.0, -122.0, null, 5f, 300L))
        viewModelIn(ViewModelStore())
        runCurrent()
        assertEquals("precondition: the first ViewModel's sweep ended it", 300L, tracks.getById("stuck").getOrThrow()?.endedAtEpochMillis)

        tracks.seedOpen("left-later", startedAt = 200L, TrackPoint(45.0, -122.0, null, 5f, 400L))
        val second = viewModelIn(ViewModelStore())
        runCurrent()

        assertNull("the second ViewModel swept nothing", tracks.getById("left-later").getOrThrow()?.endedAtEpochMillis)
        assertNull(second.uiState.value.tracks.single { it.id == "left-later" }.endedAtEpochMillis)
    }

    /**
     * The sweep and a Record tap in the same process, with the tap landing first: the sweep's own
     * listing is slow here (it gives way once before it reads), so the recording's row already
     * exists, open, with no stored point and no watch begun for it, when the sweep lists the
     * tracks. Only the cut-off keeps it from being ended: it started after the process did.
     *
     * Until Part 3c this test passed with the cut-off removed, because a track with no stored
     * point was left alone whatever its start, and its row was made after the sweep had already
     * listed. Both are changed: such a track is now ended unless the cut-off or the watch excludes
     * it, and the listing is made to come second.
     */
    @Test
    fun `a recording started on this screen is not ended by the sweep`() = runRecordingTest {
        tracks.giveWayBeforeListing = true
        val vm = viewModelIn(ViewModelStore())
        vm.startRecording(TrackRecordingMode.HIGH_ACCURACY) // lands before the sweep has listed the tracks
        runCurrent()

        assertTrue(vm.uiState.value.isRecording)
        assertNull("the recording's own row is still open", tracks.getById("track-1").getOrThrow()?.endedAtEpochMillis)
        assertNull(vm.uiState.value.tracks.firstOrNull { it.id == "track-1" }?.endedAtEpochMillis)
        vm.stopRecording()
    }

    /** The owner's "Option A" for a stuck track with nothing recorded in it (Part 3c), on the screen: finished, ended at its start, and it can be deleted. */
    @Test
    fun `Open Records, a stuck track with nothing recorded in it - it is a finished track with no points, ended at its start time, and it can be deleted`() = runRecordingTest {
        tracks.seedOpen("empty", startedAt = 100L)

        val vm = viewModelIn(ViewModelStore())
        runCurrent()

        val shown = vm.uiState.value.tracks.single { it.id == "empty" }
        assertEquals("ended at its own start time", 100L, shown.endedAtEpochMillis)
        assertTrue(shown.points.isEmpty())
        assertTrue("Records offers Delete for it", shown.canBeDeleted)
        vm.requestRemoveTrack("empty")
        assertEquals("empty", vm.uiState.value.pendingTrackDelete?.item?.id)
        assertEquals("nothing was deleted by the sweep itself: the row is still stored", listOf("empty"), tracks.getAll().getOrThrow().map { it.id })
    }

    // ── Dispatch 2026-09-28-502, steps 6 and 7: a waypoint navigation overrules a return while active ──
    //
    // Through the real entry points on both sides: `AvailabilityViewModel.onNavigateToWaypoint` and
    // `onStopWaypointNavigation`, holding this ViewModel through `TrackRecordingReturnLeg` as `MainActivity`
    // wires it, and the watch fed as the service feeds it. The walk is the control's readings and one more, so
    // a fresh return's judge sees four readings over 15 s, which the control shows is an off-track alert.

    private suspend fun TestScope.walkAwayFromTheStartForFifteenSeconds() {
        walkAwayFromTheStart()
        fixArrives(lat = 45.005, t = 27_000L)
    }

    @Test
    fun `navigating to a waypoint during a return pauses the return, and walking away from the start then delivers no off-track alert`() = runRecordingTest {
        val vm = viewModelIn(ViewModelStore())
        recordAndStartReturning(vm)
        assertTrue("the leg reads the return under way", TrackRecordingReturnLeg(vm).isReturning)
        val navigation = mapLayersViewModel(returnLeg = TrackRecordingReturnLeg(vm))
        runCurrent()

        navigation.onNavigateToWaypoint("wp-1")
        runCurrent()

        assertFalse("the return is paused on the watch", returnWatch.state.value.isReturning)
        assertFalse("and on the screen", vm.uiState.value.isReturning)
        assertTrue("the recording carries on", vm.uiState.value.isRecording)

        walkAwayFromTheStartForFifteenSeconds()

        assertEquals("no off-track alert while navigating to a waypoint", emptyList<Alert>(), delivered)
        assertFalse(vm.uiState.value.isOffTrack)
    }

    @Test
    fun `ending the waypoint navigation picks the return back up, and walking away from the start then alerts again`() = runRecordingTest {
        val vm = viewModelIn(ViewModelStore())
        recordAndStartReturning(vm)
        val navigation = mapLayersViewModel(returnLeg = TrackRecordingReturnLeg(vm))
        runCurrent()
        navigation.onNavigateToWaypoint("wp-1")
        runCurrent()
        assertFalse("precondition: the waypoint navigation paused the return", returnWatch.state.value.isReturning)

        navigation.onStopWaypointNavigation()
        runCurrent()

        assertTrue("the return picks up again on the watch", returnWatch.state.value.isReturning)
        assertTrue("and on the screen", vm.uiState.value.isReturning)
        walkAwayFromTheStartForFifteenSeconds()
        assertEquals(listOf(Alert(kind = AlertKind.OFF_TRACK, overridesSilence = false)), delivered)
    }

    @Test
    fun `the waypoints are marked loaded once they have been read, not before`() = runRecordingTest {
        val vm = viewModelIn(ViewModelStore())
        assertFalse("not read yet", vm.uiState.value.waypointsLoaded)

        runCurrent()

        assertTrue(vm.uiState.value.waypointsLoaded)
    }

    // ---- Return ends on arrival (RECORD -844, -845) -------------------------------------------------
    //
    // The ViewModel half: the screen's state while and after the watch ends a return at the start. The
    // watch's own decision is ReturnWatchArrivalTest's; the real service feeding it,
    // TrackRecordingServiceReturnArrivalTest's. The hold runs on this class's virtual clock.

    /** [recordAndStartReturning] (left the start at 45.001), then the walker back at the start: the watch ends the return. */
    private suspend fun TestScope.recordReturnAndArrive(vm: TrackRecordingViewModel) {
        recordAndStartReturning(vm)
        fixArrives(lat = 45.000, t = 12_000L)
        runCurrent()
    }

    @Test
    fun `a return that ends on arrival holds Arrived for 10 s, held through a drive-off, then the display closes and the recording goes on`() = runRecordingTest {
        val vm = viewModelIn(ViewModelStore())
        recordReturnAndArrive(vm)

        assertFalse("the return ended at the start", vm.uiState.value.isReturning)
        assertTrue("the display holds Arrived", vm.uiState.value.isArrivalHeld)

        // Driving away inside the hold, a fix a second, 333 m apart.
        (1..8).forEach { s -> fixArrives(lat = 45.000 + s * 0.003, t = 12_000L + s * 1_000L) }
        assertTrue("held, not live: the drive-off does not end it", vm.uiState.value.isArrivalHeld)
        assertFalse("no off-track on the drive-off", vm.uiState.value.isOffTrack)
        assertEquals("nothing delivered", emptyList<Alert>(), delivered)

        advanceTimeBy(ARRIVAL_HOLD_MILLIS - 1)
        runCurrent()
        assertTrue("still held just before 10 s", vm.uiState.value.isArrivalHeld)

        advanceTimeBy(2)
        runCurrent()
        assertFalse("the display closed by itself after 10 s", vm.uiState.value.isArrivalHeld)
        assertFalse(vm.uiState.value.isReturning)
        assertEquals("the recording goes on", "track-1", vm.uiState.value.activeTrack?.trackId)
        assertTrue("and the service's watch is still begun", returnWatch.state.value.isBegun)
        vm.stopRecording()
    }

    @Test
    fun `the display's exit during the hold closes it at once and starts no new return`() = runRecordingTest {
        val vm = viewModelIn(ViewModelStore())
        recordReturnAndArrive(vm)
        assertTrue("precondition: held", vm.uiState.value.isArrivalHeld)

        vm.stopReturn()
        runCurrent()

        assertFalse(vm.uiState.value.isArrivalHeld)
        assertFalse(vm.uiState.value.isReturning)
        assertFalse(returnWatch.state.value.isReturning)
        vm.stopRecording()
    }

    @Test
    fun `stopping the recording during the hold ends it`() = runRecordingTest {
        val vm = viewModelIn(ViewModelStore())
        recordReturnAndArrive(vm)
        assertTrue("precondition: held", vm.uiState.value.isArrivalHeld)

        vm.stopRecording()
        runCurrent()

        assertFalse(vm.uiState.value.isArrivalHeld)
    }

    @Test
    fun `a return the walker ends before arriving is not held`() = runRecordingTest {
        val vm = viewModelIn(ViewModelStore())
        recordAndStartReturning(vm)

        vm.stopReturn()
        runCurrent()

        assertFalse(vm.uiState.value.isArrivalHeld)
        vm.stopRecording()
    }

    @Test
    fun `a screen opened after the arrival shows the plain map, not the hold`() = runRecordingTest {
        val first = viewModelIn(ViewModelStore())
        recordReturnAndArrive(first)
        assertTrue("precondition: the first screen holds it", first.uiState.value.isArrivalHeld)

        val reopened = viewModelIn(ViewModelStore())
        runCurrent()

        assertEquals("precondition: the reopened screen took the recording up", "track-1", reopened.uiState.value.activeTrack?.trackId)
        assertFalse(reopened.uiState.value.isArrivalHeld)
        assertFalse(reopened.uiState.value.isReturning)
        first.stopRecording()
    }

    private companion object {
        const val POLL_INTERVAL_MILLIS = 15_000L

        /** The ViewModel's route tick, decision D4. Repeated here as [POLL_INTERVAL_MILLIS] is. */
        const val ROUTE_TICK_MILLIS = 5_000L

        /** When this test's "process" started: before every recording a test makes (the clock reads 1,000). */
        const val PROCESS_START = 900L

        /** The owner's sentence, as `MainActivity` reads it from `strings.xml` and passes it in. */
        const val ALREADY_RECORDING = "Forager is already recording a track. Stop it before starting another."
    }
}

private class SwipeAwayTracks : TrackRepository {
    private val tracks = mutableMapOf<String, Track>()

    /** While true, every read of a single track fails, as a database error would. */
    var failReads = false

    /** While true, a listing gives way once before it reads, so whatever else is queued runs first: a slow query. */
    var giveWayBeforeListing = false

    override suspend fun getAll(): Result<List<Track>> {
        if (giveWayBeforeListing) kotlinx.coroutines.yield()
        return Result.success(tracks.values.toList())
    }
    override suspend fun getById(id: String): Result<Track?> =
        if (failReads) Result.failure(IllegalStateException("the database could not be read")) else Result.success(tracks[id])
    override suspend fun getFullRecord(id: String): Result<List<TrackPointRecord>> =
        Result.success(tracks[id]?.points.orEmpty().map { TrackPointRecord(it, kept = true) })

    /** The conditional end, as Room's: only a row that is still open. */
    override suspend fun endIfOpen(trackId: String, endedAtEpochMillis: Long): Result<Boolean> {
        val row = tracks[trackId] ?: return Result.success(false)
        if (row.endedAtEpochMillis != null) return Result.success(false)
        tracks[trackId] = row.copy(endedAtEpochMillis = endedAtEpochMillis)
        return Result.success(true)
    }

    /** A track an earlier process left open, with the points it stored. */
    fun seedOpen(id: String, startedAt: Long, vararg points: TrackPoint) {
        tracks[id] = Track(id = id, name = null, startedAtEpochMillis = startedAt, endedAtEpochMillis = null, points = points.toList())
    }

    fun storedPointCount(id: String): Int = tracks[id]?.points?.size ?: 0
    override suspend fun getForDay(dayStartInclusiveEpochMillis: Long, dayEndExclusiveEpochMillis: Long): Result<List<Track>> =
        Result.failure(UnsupportedOperationException("getForDay is not part of this test's path"))
    override suspend fun create(track: Track): Result<Unit> = Result.success(Unit).also { tracks[track.id] = track }
    override suspend fun appendPoints(trackId: String, points: List<TrackPoint>): Result<Unit> =
        Result.failure(UnsupportedOperationException("appendPoints is the service's write, and no service runs in this test"))

    /** Puts a point on a track without going through [appendPoints]: a breadcrumb the poll will read, as the service would have written it. */
    fun appendForTest(trackId: String, point: TrackPoint) {
        tracks[trackId] = tracks.getValue(trackId).let { it.copy(points = it.points + point) }
    }
    override suspend fun end(trackId: String, endedAtEpochMillis: Long): Result<Unit> =
        Result.failure(UnsupportedOperationException("end is the service's write, and no service runs in this test"))
    override suspend fun setOriginWaypoint(trackId: String, waypointId: String): Result<Unit> {
        val existing = tracks[trackId] ?: return Result.failure(IllegalStateException("no such track"))
        tracks[trackId] = existing.copy(originWaypointId = waypointId)
        return Result.success(Unit)
    }
    override suspend fun delete(id: String): Result<Unit> = Result.success(Unit).also { tracks.remove(id) }
}

private class SwipeAwayWaypoints : WaypointRepository {
    private val saved = mutableMapOf<String, Waypoint>()
    override suspend fun getAll(): Result<List<Waypoint>> = Result.success(saved.values.toList())
    override suspend fun getForDay(dayStartInclusiveEpochMillis: Long, dayEndExclusiveEpochMillis: Long): Result<List<Waypoint>> =
        Result.failure(UnsupportedOperationException("getForDay is not part of this test's path"))
    override suspend fun getById(id: String): Result<Waypoint?> = Result.success(saved[id])
    override suspend fun getForTrack(trackId: String): Result<List<Waypoint>> = Result.success(saved.values.filter { it.trackId == trackId })
    override suspend fun detachFromTrack(trackId: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("detachFromTrack is not part of this test's path"))
    override suspend fun save(waypoint: Waypoint): Result<Unit> = Result.success(Unit).also { saved[waypoint.id] = waypoint }
    override suspend fun delete(id: String): Result<Unit> = Result.success(Unit).also { saved.remove(id) }
}
