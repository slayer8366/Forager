package com.zynergylabs.forager.app.ui.track

import com.zynergylabs.forager.app.ui.availability.onDarknessMarginChanged
import com.zynergylabs.forager.app.domain.AlertAudibility
import com.zynergylabs.forager.app.domain.AlertDelivery
import com.zynergylabs.forager.app.domain.ComputeReturnToStartUseCase
import com.zynergylabs.forager.app.domain.CreateWaypointUseCase
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.DeleteTrackUseCase
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.InMemoryKeptTrackPaths
import com.zynergylabs.forager.app.domain.DeleteWaypointUseCase
import com.zynergylabs.forager.app.domain.ReturnWatch
import com.zynergylabs.forager.app.domain.AbandonedTrackSweepOnce
import com.zynergylabs.forager.app.domain.EndAbandonedTracksUseCase
import com.zynergylabs.forager.app.domain.GetTrackOriginWaypointUseCase
import com.zynergylabs.forager.app.domain.GetTracksUseCase
import com.zynergylabs.forager.app.domain.GetWaypointsUseCase
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.LocationTracker
import com.zynergylabs.forager.app.domain.StartTrackUseCase
import com.zynergylabs.forager.app.domain.AlertAudibilityState
import com.zynergylabs.forager.app.domain.RingerMode
import com.zynergylabs.forager.app.domain.TrackRepository
import com.zynergylabs.forager.app.domain.WaypointRepository
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.TrackPointRecord
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.SundownLine
import com.zynergylabs.forager.app.domain.SundownShown
import com.zynergylabs.forager.app.domain.SundownWatch
import com.zynergylabs.forager.app.domain.SundownPreferencesRepository
import com.zynergylabs.forager.app.domain.isShown
import com.zynergylabs.forager.app.domain.ComputeSundownCountdownUseCase
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.SundownCountdown
import com.zynergylabs.forager.app.ui.availability.AvailabilityViewModel
import com.zynergylabs.forager.app.ui.availability.mapLayersViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import kotlinx.coroutines.launch
import org.junit.Before
import org.junit.Test

/**
 * The sundown line through the recording ViewModel (dispatch 2026-09-28-592, Amendment 1, RECORD
 * -593): start a recording through [TrackRecordingViewModel.startRecording] and read the state the
 * screen reads. The line is the [SundownWatch]'s, which the service ticks; here the test drives the
 * watch the way the service does. This class used to test the ViewModel's own countdown, which nothing
 * rendered; Amendment 1 retired it, and its five tests with it (no-position, the stored margin, the fix
 * age, a network fix giving the place, the default turnaround). Their claims now live where the value
 * is computed: `SundownWatchTest` (network fix, last known position) and `SundownWatchLineTest`.
 *
 * ## Why this class does not use `advanceUntilIdle`, and stops recordings in a `finally`
 *
 * `beginPolling` runs an unbounded `while (true) { ...; delay(...) }` while recording, so draining
 * to "idle" never finishes. [TrackRecordingViewModelTest] records the rest: a body that throws
 * before its own `stopRecording()` leaves that loop scheduled, and `runTest`'s closing
 * idle-advance spins through virtual time forever. The visible symptom is a run that never ends
 * rather than a failure, the assertion message is lost, and coroutines-test 1.11's own timeout did
 * not fire in 77 minutes on that spin. [runRecordingTest] here is the same guard, rebuilt rather
 * than inherited because that class is not open.
 */
class TrackRecordingSundownTest {

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

    private class EmittingTracker : LocationTracker {
        val emitted = MutableSharedFlow<LocationFix>(replay = 1)
        override val fixes: Flow<LocationFix> = emitted
    }

    /**
     * Minimal fakes, local to this class. [TrackRecordingViewModelTest] has equivalents but they
     * are private to that file, and reaching into another test's fixtures to save a few lines
     * would couple two suites that have no reason to move together.
     */
    private class InMemoryTracks : TrackRepository {
        private val tracks = mutableMapOf<String, Track>()
        override suspend fun getAll() = Result.success(tracks.values.toList())
        override suspend fun getById(id: String) = Result.success(tracks[id])
        override suspend fun getFullRecord(id: String) = Result.success(emptyList<TrackPointRecord>())
        override suspend fun getForDay(dayStartInclusiveEpochMillis: Long, dayEndExclusiveEpochMillis: Long) =
            Result.success(tracks.values.toList())
        override suspend fun create(track: Track) = Result.success(Unit).also { tracks[track.id] = track }
        override suspend fun appendPoints(trackId: String, points: List<TrackPoint>) = Result.success(Unit)
        override suspend fun end(trackId: String, endedAtEpochMillis: Long) = Result.success(Unit)
        override suspend fun setOriginWaypoint(trackId: String, waypointId: String) = Result.success(Unit)
        override suspend fun delete(id: String) = Result.success(Unit).also { tracks.remove(id) }
    }

    private class InMemoryWaypoints : WaypointRepository {
        private val saved = mutableMapOf<String, Waypoint>()
        override suspend fun getAll() = Result.success(saved.values.toList())
        override suspend fun getForDay(dayStartInclusiveEpochMillis: Long, dayEndExclusiveEpochMillis: Long) =
            Result.success(saved.values.toList())
        override suspend fun getById(id: String) = Result.success(saved[id])
        override suspend fun getForTrack(trackId: String) = Result.success(saved.values.filter { it.trackId == trackId })
        override suspend fun detachFromTrack(trackId: String) = Result.success(Unit)
        override suspend fun save(waypoint: Waypoint) = Result.success(Unit).also { saved[waypoint.id] = waypoint }
        override suspend fun delete(id: String) = Result.success(Unit).also { saved.remove(id) }
    }

    private fun viewModel(
        nowEpochMillis: () -> Long,
        tracker: LocationTracker,
        sundownShown: MutableStateFlow<SundownShown?>,
    ): TrackRecordingViewModel {
        val repository = InMemoryTracks()
        val waypoints = InMemoryWaypoints()
        val clock = CurrentTimeProvider { nowEpochMillis() }
        var waypointIds = 0
        return TrackRecordingViewModel(
            trackRepository = repository,
            startTrack = StartTrackUseCase(repository, currentTime = clock, idGenerator = { "track-1" }),
            getWaypoints = GetWaypointsUseCase(waypoints),
            createWaypoint = CreateWaypointUseCase(waypoints, currentTime = clock, idGenerator = { "waypoint-${++waypointIds}" }),
            deleteWaypoint = DeleteWaypointUseCase(waypoints),
            deleteTrack = DeleteTrackUseCase(repository, waypoints, InMemoryKeptTrackPaths()),
            getTrackOriginWaypoint = GetTrackOriginWaypointUseCase(repository, waypoints),
            alreadyRecordingMessage = "Forager is already recording a track. Stop it before starting another.",
            abandonedTrackSweepOnce = AbandonedTrackSweepOnce(EndAbandonedTracksUseCase(repository, { null }, { _, _, _ -> }), processStartedAtEpochMillis = 0L),
            computeReturnToStart = ComputeReturnToStartUseCase(),
            locationTracker = tracker,
            getTracks = GetTracksUseCase(repository),
            returnWatch = ReturnWatch(ComputeReturnToStartUseCase(), AlertDelivery { }),
            alertAudibility = object : AlertAudibility { override fun current() = AlertAudibilityState(RingerMode.NORMAL, doNotDisturbOn = false, notificationsEnabled = true) },
            currentTime = clock,
            zone = ZoneOffset.UTC,
            sundownShown = sundownShown,
        ).also(createdViewModels::add)
    }

    private class Preferences(var margin: Int = 60, var enabled: Boolean = true) : SundownPreferencesRepository {
        override suspend fun getDarknessMarginMinutes() = Result.success(margin)
        override suspend fun setDarknessMarginMinutes(minutes: Int) = Result.success(Unit).also { margin = minutes }
        override suspend fun getAlertsEnabled() = Result.success(enabled)
        override suspend fun setAlertsEnabled(enabled: Boolean) = Result.success(Unit).also { this.enabled = enabled }
    }

    private val sample = SundownLine.DarkSince(SUNSET + 30 * MINUTE)

    @Test
    fun `no recording, no line, whatever the watch publishes`() = runRecordingTest {
        val shown = MutableStateFlow<SundownShown?>(SundownShown("track-1", sample))
        val viewModel = viewModel({ SUNSET }, EmittingTracker(), shown)
        runCurrent()
        assertNull(viewModel.uiState.value.sundownLine)
    }

    @Test
    fun `the watch's line for this recording is the line on screen, and follows it`() = runRecordingTest {
        val shown = MutableStateFlow<SundownShown?>(null)
        val viewModel = viewModel({ SUNSET }, EmittingTracker(), shown)
        viewModel.startRecording()
        runCurrent()
        assertNull("nothing published yet", viewModel.uiState.value.sundownLine)

        shown.value = SundownShown("track-1", sample)
        runCurrent()
        assertEquals(sample, viewModel.uiState.value.sundownLine)

        val later = SundownLine.AfterSunset(SUNSET + MINUTE, SUNSET, SUNSET + 30 * MINUTE)
        shown.value = SundownShown("track-1", later)
        runCurrent()
        assertEquals(later, viewModel.uiState.value.sundownLine)
    }

    @Test
    fun `another recording's line is not this screen's`() = runRecordingTest {
        val shown = MutableStateFlow<SundownShown?>(null)
        val viewModel = viewModel({ SUNSET }, EmittingTracker(), shown)
        viewModel.startRecording()
        runCurrent()
        shown.value = SundownShown("track-0", sample)
        runCurrent()
        assertNull(viewModel.uiState.value.sundownLine)
    }

    @Test
    fun `stopping the recording takes the line away at once`() = runRecordingTest {
        val shown = MutableStateFlow<SundownShown?>(SundownShown("track-1", sample))
        val viewModel = viewModel({ SUNSET }, EmittingTracker(), shown)
        viewModel.startRecording()
        runCurrent()
        assertEquals(sample, viewModel.uiState.value.sundownLine)
        viewModel.stopRecording()
        runCurrent()
        assertNull(viewModel.uiState.value.sundownLine)
    }

    /**
     * End to end through the real entry points: a recording started on the ViewModel, a real watch
     * ticked as the service ticks it, and the margin changed through Settings' own handler
     * ([AvailabilityViewModel.onDarknessMarginChanged]), wired to the watch as `MainActivity` wires it.
     * The start-back time on screen moves by the difference at once, with no tick.
     */
    @Test
    fun `a margin changed in Settings moves the start-back time on screen at once`() = runRecordingTest {
        val here = londonFix(0L)
        val t0 = SUNSET - 4 * HOUR
        // Twenty minutes walked north at 1.2 m/s, a point every 5 s: a measured walk back.
        val points = (0..240).map { i -> TrackPoint(here.lat + i * 6.0 / 111_195.0, here.lng, null, 4f, t0 + i * 5_000L, 1.2f, 0.5f) }
        var now = t0 + 20 * MINUTE
        val preferences = Preferences()
        val watch = SundownWatch(
            alertDelivery = { },
            clock = CurrentTimeProvider { now },
            preferences = preferences,
            readTrack = { id -> Result.success(Track(id, null, t0, null, points)) },
            readWaypoint = { Result.success(null) },
            isReturning = { false },
            errorLog = { _, _, _ -> },
        )
        val shown = MutableStateFlow<SundownShown?>(null)
        val viewModel = viewModel({ now }, EmittingTracker(), shown)
        // MainActivity hands the ViewModel the watch's own flow; mirrored here so the test dispatcher drives the collection.
        val mirror = kotlinx.coroutines.CoroutineScope(dispatcher).launch { watch.shown.collect { shown.value = it } }
        try {
            viewModel.startRecording()
            runCurrent()
            watch.begin("track-1")
            watch.onFix(points.last().copy(timestampEpochMillis = now / 1_000L * 1_000L), FixProvider.GPS)
            watch.tick()
            runCurrent()
            val before = (viewModel.uiState.value.sundownLine as SundownLine.BeforeSunset).startBackAtEpochMillis
            assertTrue("precondition: a walk back, so a start-back time", before != null)

            val settings: AvailabilityViewModel = mapLayersViewModel(
                setDarknessMarginMinutes = { preferences.setDarknessMarginMinutes(it) },
                onDarknessMarginStored = watch::onMarginChanged,
            )
            settings.onDarknessMarginChanged(30)
            runCurrent()

            assertEquals("stored", 30, preferences.margin)
            assertEquals(
                "thirty minutes later on screen, with no tick",
                before!! + 30 * MINUTE,
                (viewModel.uiState.value.sundownLine as SundownLine.BeforeSunset).startBackAtEpochMillis,
            )
        } finally {
            mirror.cancel()
        }
    }

    /** Amendment 2 (RECORD -595) through the ViewModel: the line it holds at 2 h 31 min is hidden, at 2 h 29 min shown. */
    @Test
    fun `the line the ViewModel holds is hidden at 2 h 31 min before sunset and shown at 2 h 29 min`() = runRecordingTest {
        var now = 0L
        val watch = SundownWatch(
            alertDelivery = { },
            clock = CurrentTimeProvider { now },
            preferences = Preferences(),
            readTrack = { Result.success(null) },
            readWaypoint = { Result.success(null) },
            isReturning = { false },
            errorLog = { _, _, _ -> },
        )
        val here = londonFix(0L)
        val sunset = (ComputeSundownCountdownUseCase()(SUNSET - 6 * HOUR, LatLng(here.lat, here.lng), null, 0L) as SundownCountdown.Known).sunsetAtEpochMillis
        val shown = MutableStateFlow<SundownShown?>(null)
        val viewModel = viewModel({ now }, EmittingTracker(), shown)
        val mirror = kotlinx.coroutines.CoroutineScope(dispatcher).launch { watch.shown.collect { shown.value = it } }
        try {
            viewModel.startRecording()
            runCurrent()
            watch.begin("track-1")
            now = sunset - 151 * MINUTE
            watch.onFix(TrackPoint(here.lat, here.lng, null, 5f, now), FixProvider.GPS)
            watch.tick()
            runCurrent()
            assertEquals(false, viewModel.uiState.value.sundownLine!!.isShown())

            now = sunset - 149 * MINUTE
            watch.tick()
            runCurrent()
            assertEquals(true, viewModel.uiState.value.sundownLine!!.isShown())
        } finally {
            mirror.cancel()
        }
    }

    /** Accuracy well inside BALANCED's 50 m ceiling. */
    private fun londonFix(atEpochMillis: Long) = LocationFix.Update(
        lat = 51.5074, lng = -0.1278, altitude = null,
        accuracyMeters = 5f, timestampEpochMillis = atEpochMillis,
        provider = FixProvider.GPS,
    )

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE

        /** 2026-09-12 sunset at London, Open-Meteo, the reference `SunCrossingTest` also cites. */
        const val SUNSET = 1789237318000L
    }
}
