package com.zynergylabs.forager.app.ui.track

import com.zynergylabs.forager.app.domain.AbandonedTrackSweepOnce
import com.zynergylabs.forager.app.domain.AlertAudibility
import com.zynergylabs.forager.app.domain.AlertAudibilityState
import com.zynergylabs.forager.app.domain.AlertDelivery
import com.zynergylabs.forager.app.domain.BackByControl
import com.zynergylabs.forager.app.domain.BackByShown
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
import com.zynergylabs.forager.app.domain.NoEntryReferenceCounts
import com.zynergylabs.forager.app.domain.ReturnWatch
import com.zynergylabs.forager.app.domain.RingerMode
import com.zynergylabs.forager.app.domain.StartTrackUseCase
import com.zynergylabs.forager.app.domain.TrackRepository
import com.zynergylabs.forager.app.domain.WaypointRepository
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.TrackPointRecord
import com.zynergylabs.forager.app.domain.model.Waypoint
import java.lang.ref.WeakReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * A [TrackRecordingViewModel] built without `backBy` leaves nothing behind once its test lets go of it (dispatch
 * 2026-10-26-08, RECORD -1656). The default, [com.zynergylabs.forager.app.domain.NoBackBy], is an `object`; while
 * its `shown` was one shared flow, every such ViewModel's Back by collector stayed in that flow's slots for the rest
 * of the test JVM, holding the ViewModel and everything it reached: in the heap report's dump, 23 of 38 live app
 * graphs were first reached that way (prompts/preserved/2026-10-26-04-report.md, section 2).
 *
 * Read with a weak reference and forced collections, as the heap dump read it with GC roots. The second test is the
 * probe's positive control: the same ViewModel with a Back by flow the test itself holds is kept, so a collected
 * ViewModel in the first is the default's doing, not a probe that cannot see a collector. Nothing here records, so
 * the ViewModel's poll loop never starts.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrackRecordingNoBackByLeakTest {

    @Before fun setUpMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDownMain() = Dispatchers.resetMain()

    @Test
    fun `a ViewModel built without backBy is collected once nothing holds it`() {
        val held = buildAndLetGo(backBy = null)
        assertNull(
            "the ViewModel built without backBy was still reachable after it was let go: NoBackBy's shared flow " +
                "kept its Back by collector",
            collectedOrNull(held),
        )
    }

    @Test
    fun `a ViewModel whose Back by flow is still held is kept, so the probe can see a held collector`() {
        val control = HeldBackBy()
        val held = buildAndLetGo(backBy = control)
        assertEquals("the ViewModel's Back by collector is in the held flow", 1, control.flow.subscriptionCount.value)
        assertNotNull("a collector on a flow that is still held keeps the ViewModel", collectedOrNull(held))
    }

    /** Built in its own frame, so no local of the test's keeps it. */
    private fun buildAndLetGo(backBy: BackByControl?): WeakReference<TrackRecordingViewModel> =
        WeakReference(viewModel(backBy))

    private fun collectedOrNull(ref: WeakReference<TrackRecordingViewModel>): TrackRecordingViewModel? {
        repeat(GC_ATTEMPTS) {
            if (ref.get() == null) return null
            System.gc()
            Thread.sleep(GC_PAUSE_MILLIS)
        }
        return ref.get()
    }

    private class HeldBackBy : BackByControl {
        val flow = MutableStateFlow<BackByShown?>(null)
        override val shown = flow
        override fun set(trackId: String, atEpochMillis: Long) = false
        override fun clear(trackId: String) = Unit
    }

    private class SilentTracker : LocationTracker {
        override val fixes: Flow<LocationFix> = MutableSharedFlow()
    }

    /** Minimal fakes, local to this class, as [TrackRecordingBackByTest] keeps its own. */
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

    /** As [TrackRecordingBackByTest]'s, with `backBy` left at its default when [backBy] is null. */
    private fun viewModel(backBy: BackByControl?): TrackRecordingViewModel {
        val common = Common(InMemoryTracks(), InMemoryWaypoints(), CurrentTimeProvider { 1_791_050_400_000L })
        return if (backBy == null) common.build() else common.build(backBy)
    }

    /** The arguments every ViewModel here shares; [build] with no control leaves `backBy` at its default. */
    private class Common(val repository: InMemoryTracks, val waypoints: InMemoryWaypoints, val clock: CurrentTimeProvider) {
        fun build(): TrackRecordingViewModel = TrackRecordingViewModel(
            trackRepository = repository,
            startTrack = StartTrackUseCase(repository, currentTime = clock, idGenerator = { "track-1" }),
            getWaypoints = GetWaypointsUseCase(waypoints),
            createWaypoint = CreateWaypointUseCase(waypoints, currentTime = clock, idGenerator = { "waypoint-1" }),
            deleteWaypoint = DeleteWaypointUseCase(waypoints),
            deleteTrack = DeleteTrackUseCase(repository, waypoints, InMemoryKeptTrackPaths()),
            getTrackOriginWaypoint = GetTrackOriginWaypointUseCase(repository, waypoints),
            alreadyRecordingMessage = "Forager is already recording a track. Stop it before starting another.",
            abandonedTrackSweepOnce = sweep(),
            computeReturnToStart = ComputeReturnToStartUseCase(),
            locationTracker = SilentTracker(),
            getTracks = GetTracksUseCase(repository),
            returnWatch = ReturnWatch(ComputeReturnToStartUseCase(), AlertDelivery { }),
            alertAudibility = audibility(),
            currentTime = clock,
            entryReferenceCounts = NoEntryReferenceCounts,
        )

        fun build(control: BackByControl): TrackRecordingViewModel = TrackRecordingViewModel(
            trackRepository = repository,
            startTrack = StartTrackUseCase(repository, currentTime = clock, idGenerator = { "track-1" }),
            getWaypoints = GetWaypointsUseCase(waypoints),
            createWaypoint = CreateWaypointUseCase(waypoints, currentTime = clock, idGenerator = { "waypoint-1" }),
            deleteWaypoint = DeleteWaypointUseCase(waypoints),
            deleteTrack = DeleteTrackUseCase(repository, waypoints, InMemoryKeptTrackPaths()),
            getTrackOriginWaypoint = GetTrackOriginWaypointUseCase(repository, waypoints),
            alreadyRecordingMessage = "Forager is already recording a track. Stop it before starting another.",
            abandonedTrackSweepOnce = sweep(),
            computeReturnToStart = ComputeReturnToStartUseCase(),
            locationTracker = SilentTracker(),
            getTracks = GetTracksUseCase(repository),
            returnWatch = ReturnWatch(ComputeReturnToStartUseCase(), AlertDelivery { }),
            alertAudibility = audibility(),
            currentTime = clock,
            backBy = control,
            entryReferenceCounts = NoEntryReferenceCounts,
        )

        private fun sweep() =
            AbandonedTrackSweepOnce(EndAbandonedTracksUseCase(repository, { null }, { _, _, _ -> }), processStartedAtEpochMillis = 0L)

        private fun audibility() = object : AlertAudibility {
            override fun current() = AlertAudibilityState(RingerMode.NORMAL, doNotDisturbOn = false, notificationsEnabled = true)
        }
    }

    private companion object {
        const val GC_ATTEMPTS = 20
        const val GC_PAUSE_MILLIS = 20L
    }
}
