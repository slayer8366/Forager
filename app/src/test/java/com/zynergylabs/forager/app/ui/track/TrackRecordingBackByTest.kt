package com.zynergylabs.forager.app.ui.track

import com.zynergylabs.forager.app.domain.AbandonedTrackSweepOnce
import com.zynergylabs.forager.app.domain.AlertAudibility
import com.zynergylabs.forager.app.domain.AlertAudibilityState
import com.zynergylabs.forager.app.domain.AlertDelivery
import com.zynergylabs.forager.app.domain.BackByChoice
import com.zynergylabs.forager.app.domain.BackByRecordEvent
import com.zynergylabs.forager.app.domain.BackByShown
import com.zynergylabs.forager.app.domain.BackByWatch
import com.zynergylabs.forager.app.domain.ComputeReturnToStartUseCase
import com.zynergylabs.forager.app.domain.CreateWaypointUseCase
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.DeleteTrackUseCase
import com.zynergylabs.forager.app.domain.DeleteWaypointUseCase
import com.zynergylabs.forager.app.domain.EndAbandonedTracksUseCase
import com.zynergylabs.forager.app.domain.GetTrackOriginWaypointUseCase
import com.zynergylabs.forager.app.domain.GetTracksUseCase
import com.zynergylabs.forager.app.domain.GetWaypointsUseCase
import com.zynergylabs.forager.app.domain.InMemoryKeptTrackPaths
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.LocationTracker
import com.zynergylabs.forager.app.domain.ReturnWatch
import com.zynergylabs.forager.app.domain.RingerMode
import com.zynergylabs.forager.app.domain.StartTrackUseCase
import com.zynergylabs.forager.app.domain.TrackRepository
import com.zynergylabs.forager.app.domain.WaypointRepository
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.TrackPointRecord
import com.zynergylabs.forager.app.domain.model.Waypoint
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Back by through the recording ViewModel (dispatch 2026-09-28-645, plan task T15): the quick menu's
 * choices reach [TrackRecordingViewModel.setBackBy] and [TrackRecordingViewModel.clearBackBy], which
 * `MainActivity` wires the menu to, and set the app's real [BackByWatch]; what the watch publishes is
 * what the screen reads ([TrackRecordingUiState.backBy]). The clock is fixed and the zone UTC, so a
 * picked time's day is checkable.
 *
 * Every recording is stopped in a `finally` ([runRecordingTest]), for the reason
 * `TrackRecordingSundownTest` records: the ViewModel's poll loop would otherwise spin `runTest` forever.
 */
class TrackRecordingBackByTest {

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

    private val now = 1_791_050_400_000L // 2026-10-03T18:00:00Z
    private val clock = CurrentTimeProvider { now }
    private val recorded = mutableListOf<BackByRecordEvent>()
    private val watch = BackByWatch(AlertDelivery { }, clock, { Result.success(null) }, { Result.success(null) }, { false }, { _, _, _ -> }, record = { recorded += it })

    private fun viewModel(): TrackRecordingViewModel {
        val repository = InMemoryTracks()
        val waypoints = InMemoryWaypoints()
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
            locationTracker = EmittingTracker(),
            getTracks = GetTracksUseCase(repository),
            returnWatch = ReturnWatch(ComputeReturnToStartUseCase(), AlertDelivery { }),
            alertAudibility = object : AlertAudibility { override fun current() = AlertAudibilityState(RingerMode.NORMAL, doNotDisturbOn = false, notificationsEnabled = true) },
            currentTime = clock,
            zone = ZoneOffset.UTC,
            backBy = watch,
        ).also(createdViewModels::add)
    }

    private val hour = 60 * 60 * 1_000L

    @Test
    fun `+1 h, +2 h and +3 h set the recording's Back by that far from now, and the screen shows it`() = runRecordingTest {
        val viewModel = viewModel()
        viewModel.startRecording()
        runCurrent()
        for (hours in 1..3) {
            viewModel.setBackBy(BackByChoice.HoursFromNow(hours))
            runCurrent()
            assertEquals(now + hours * hour, watch.shown.value?.backByAtEpochMillis)
            assertEquals(BackByShown("track-1", now + hours * hour, now), viewModel.uiState.value.backBy)
        }
    }

    @Test
    fun `a picked time later today is today's, and one earlier is tomorrow's`() = runRecordingTest {
        val viewModel = viewModel()
        viewModel.startRecording()
        runCurrent()
        viewModel.setBackBy(BackByChoice.AtTime(20, 30))
        runCurrent()
        assertEquals(now + 2 * hour + 30 * 60_000L, viewModel.uiState.value.backBy?.backByAtEpochMillis)
        viewModel.setBackBy(BackByChoice.AtTime(9, 0))
        runCurrent()
        assertEquals(now + 15 * hour, viewModel.uiState.value.backBy?.backByAtEpochMillis)
    }

    @Test
    fun `Clear clears it`() = runRecordingTest {
        val viewModel = viewModel()
        viewModel.startRecording()
        runCurrent()
        viewModel.setBackBy(BackByChoice.HoursFromNow(1))
        runCurrent()
        viewModel.clearBackBy()
        runCurrent()
        assertNull(watch.shown.value)
        assertNull(viewModel.uiState.value.backBy)
    }

    @Test
    fun `with no recording, a choice sets nothing`() = runRecordingTest {
        val viewModel = viewModel()
        runCurrent()
        viewModel.setBackBy(BackByChoice.HoursFromNow(1))
        runCurrent()
        assertNull(watch.shown.value)
        assertNull(viewModel.uiState.value.backBy)
        // Dispatch 2026-09-28-796: no longer silent.
        assertEquals("recorded, not silent", listOf<BackByRecordEvent>(BackByRecordEvent.SetWithNoRecording), recorded.toList())
    }

    /** Dispatch 2026-09-28-796: a picked time from the menu is recorded as set, accepted, for this recording. */
    @Test
    fun `a picked time is recorded as set and accepted for the recording`() = runRecordingTest {
        val viewModel = viewModel()
        viewModel.startRecording()
        runCurrent()
        viewModel.setBackBy(BackByChoice.AtTime(18, 45))
        runCurrent()
        assertEquals(
            listOf(BackByRecordEvent.Set("track-1", now + 45 * 60_000L, accepted = true, watchedTrackId = null)),
            recorded.filterIsInstance<BackByRecordEvent.Set>(),
        )
    }

    @Test
    fun `stopping the recording takes it off the screen at once`() = runRecordingTest {
        val viewModel = viewModel()
        viewModel.startRecording()
        runCurrent()
        viewModel.setBackBy(BackByChoice.HoursFromNow(1))
        runCurrent()
        viewModel.stopRecording()
        runCurrent()
        assertNull(viewModel.uiState.value.backBy)
    }
}
