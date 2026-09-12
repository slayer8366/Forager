package com.zynergylabs.forager.app.ui.track

import com.zynergylabs.forager.app.domain.AlertAudibility
import com.zynergylabs.forager.app.domain.AlertDelivery
import com.zynergylabs.forager.app.domain.ComputeReturnToStartUseCase
import com.zynergylabs.forager.app.domain.CreateWaypointUseCase
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.DeleteWaypointUseCase
import com.zynergylabs.forager.app.domain.DetectOffTrackUseCase
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
import com.zynergylabs.forager.app.domain.model.SundownCountdown
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The countdown through its real entry point: start a recording, emit a fix the way the tracker
 * does, read the state the screen reads. Not by calling the private `updateSundown`, which would
 * assert that a method works rather than that recording produces a countdown.
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
        nowEpochMillis: Long,
        tracker: LocationTracker,
        darknessMarginMinutes: Int = 60,
    ): TrackRecordingViewModel {
        val repository = InMemoryTracks()
        val waypoints = InMemoryWaypoints()
        val clock = CurrentTimeProvider { nowEpochMillis }
        var waypointIds = 0
        return TrackRecordingViewModel(
            trackRepository = repository,
            startTrack = StartTrackUseCase(repository, currentTime = clock, idGenerator = { "track-1" }),
            getWaypoints = GetWaypointsUseCase(waypoints),
            createWaypoint = CreateWaypointUseCase(waypoints, currentTime = clock, idGenerator = { "waypoint-${++waypointIds}" }),
            deleteWaypoint = DeleteWaypointUseCase(waypoints),
            computeReturnToStart = ComputeReturnToStartUseCase(),
            detectOffTrack = DetectOffTrackUseCase(),
            locationTracker = tracker,
            getTracks = GetTracksUseCase(repository),
            alertDelivery = AlertDelivery { },
            alertAudibility = object : AlertAudibility { override fun current() = AlertAudibilityState(RingerMode.NORMAL, doNotDisturbOn = false, notificationsEnabled = true) },
            currentTime = clock,
            zone = ZoneOffset.UTC,
            darknessMarginMinutes = { darknessMarginMinutes },
        ).also(createdViewModels::add)
    }

    @Test
    fun `before any fix the countdown is no-position, not a zeroed time`() = runRecordingTest {
        val viewModel = viewModel(SUNSET - 3 * HOUR, EmittingTracker())
        viewModel.startRecording()
        runCurrent()

        assertEquals(
            "a recording with no fix must say so rather than count down from nothing",
            SundownCountdown.NoPositionYet,
            viewModel.uiState.value.sundownCountdown,
        )
    }

    @Test
    fun `once a fix lands the countdown reports sunset and the turnaround before it`() = runRecordingTest {
        val now = SUNSET - 3 * HOUR
        val tracker = EmittingTracker()
        val viewModel = viewModel(now, tracker)
        viewModel.startRecording()
        runCurrent()

        tracker.emitted.emit(londonFix(now))
        // The countdown is refreshed by the poll loop, not by the fix arriving, so virtual time
        // has to reach the next tick. advanceTimeBy, never advanceUntilIdle: that loop is
        // unbounded and draining it to idle never returns.
        advanceTimeBy(POLL_INTERVAL_MILLIS + 1)
        runCurrent()

        val known = viewModel.uiState.value.sundownCountdown as SundownCountdown.Known
        assertEquals(
            "sunset, within a minute of the published time",
            SUNSET.toDouble(), known.sunsetAtEpochMillis.toDouble(), 60_000.0,
        )
        assertEquals(
            "the turnaround is the default hour before it",
            known.sunsetAtEpochMillis - HOUR, known.turnaroundAtEpochMillis,
        )
        assertTrue("which has not passed yet", !known.isPastTurnaround)
    }

    @Test
    fun `the stored margin reaches the countdown, not the default`() = runRecordingTest {
        val now = SUNSET - 3 * HOUR
        val tracker = EmittingTracker()
        val viewModel = viewModel(now, tracker, darknessMarginMinutes = 25)
        viewModel.startRecording()
        runCurrent()

        tracker.emitted.emit(londonFix(now))
        // The countdown is refreshed by the poll loop, not by the fix arriving, so virtual time
        // has to reach the next tick. advanceTimeBy, never advanceUntilIdle: that loop is
        // unbounded and draining it to idle never returns.
        advanceTimeBy(POLL_INTERVAL_MILLIS + 1)
        runCurrent()

        val known = viewModel.uiState.value.sundownCountdown as SundownCountdown.Known
        assertEquals(
            "a stored 25-minute margin must be what the turnaround uses",
            known.sunsetAtEpochMillis - 25 * 60_000L, known.turnaroundAtEpochMillis,
        )
    }

    @Test
    fun `the age of the fix is carried into the state the screen reads`() = runRecordingTest {
        val now = SUNSET - 2 * HOUR
        val tracker = EmittingTracker()
        val viewModel = viewModel(now, tracker)
        viewModel.startRecording()
        runCurrent()

        tracker.emitted.emit(londonFix(now - 4 * 60_000L))
        // The countdown is refreshed by the poll loop, not by the fix arriving, so virtual time
        // has to reach the next tick. advanceTimeBy, never advanceUntilIdle: that loop is
        // unbounded and draining it to idle never returns.
        advanceTimeBy(POLL_INTERVAL_MILLIS + 1)
        runCurrent()

        val known = viewModel.uiState.value.sundownCountdown as SundownCountdown.Known
        assertEquals("four minutes", 4 * 60_000L, known.fixAgeMillis)
    }

    /** Accuracy well inside BALANCED's 50 m ceiling, so the fix passes the gate and is kept. */
    private fun londonFix(atEpochMillis: Long) = LocationFix.Update(
        lat = 51.5074, lng = -0.1278, altitude = null,
        accuracyMeters = 5f, timestampEpochMillis = atEpochMillis,
    )

    private companion object {
        const val HOUR = 60 * 60 * 1000L

        /** Mirrors TrackRecordingViewModel.POLL_INTERVAL_MILLIS, which is private. */
        const val POLL_INTERVAL_MILLIS = 15_000L

        /** 2026-09-12 sunset at London, Open-Meteo, the reference `SunCrossingTest` also cites. */
        const val SUNSET = 1789237318000L
    }
}
