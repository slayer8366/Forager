package com.zynergylabs.forager.app.ui.track

import com.zynergylabs.forager.app.domain.AbandonedTrackSweepOnce
import com.zynergylabs.forager.app.domain.AlertAudibility
import com.zynergylabs.forager.app.domain.AlertAudibilityState
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
import com.zynergylabs.forager.app.domain.wayhome.WayHomeWatch
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * The real recording ViewModel with a real way home, for the part 2 tests that drive it from Return (dispatch
 * 2026-10-24-10 Amendment 1, PR C): `TrackRecordingWayHomeTest` and the screen rig `AvailabilityScreenWayHomeRigTest`.
 * Repositories in memory; the track's points are appended as the service would store them.
 */
internal fun wayHomeRecordingViewModel(
    tracks: WayHomeTestTracks,
    tracker: LocationTracker,
    returnWatch: ReturnWatch,
    wayHome: WayHomeWatch,
    nowEpochMillis: Long,
): TrackRecordingViewModel {
    val waypoints = WayHomeTestWaypoints()
    val clock = CurrentTimeProvider { nowEpochMillis }
    var waypointIds = 0
    return TrackRecordingViewModel(
        trackRepository = tracks,
        startTrack = StartTrackUseCase(tracks, currentTime = clock, idGenerator = { "track-1" }),
        getWaypoints = GetWaypointsUseCase(waypoints),
        createWaypoint = CreateWaypointUseCase(waypoints, currentTime = clock, idGenerator = { "waypoint-${++waypointIds}" }),
        deleteWaypoint = DeleteWaypointUseCase(waypoints),
        deleteTrack = DeleteTrackUseCase(tracks, waypoints, InMemoryKeptTrackPaths()),
        getTrackOriginWaypoint = GetTrackOriginWaypointUseCase(tracks, waypoints),
        alreadyRecordingMessage = "already recording",
        abandonedTrackSweepOnce = AbandonedTrackSweepOnce(EndAbandonedTracksUseCase(tracks, { null }, { _, _, _ -> }), processStartedAtEpochMillis = 0L),
        computeReturnToStart = ComputeReturnToStartUseCase(),
        locationTracker = tracker,
        getTracks = GetTracksUseCase(tracks),
        returnWatch = returnWatch,
        alertAudibility = object : AlertAudibility { override fun current() = AlertAudibilityState(RingerMode.NORMAL, doNotDisturbOn = false, notificationsEnabled = true) },
        currentTime = clock,
        zone = ZoneOffset.UTC,
        wayHomeWatch = wayHome,
        entryReferenceCounts = NoEntryReferenceCounts,
    )
}

internal class WayHomeTestTracker : LocationTracker {
    val emitted = MutableSharedFlow<LocationFix>(replay = 1)
    override val fixes: Flow<LocationFix> = emitted
}

internal class WayHomeTestTracks : TrackRepository {
    val tracks = mutableMapOf<String, Track>()
    override suspend fun getAll() = Result.success(tracks.values.toList())
    override suspend fun getById(id: String) = Result.success(tracks[id])
    override suspend fun getFullRecord(id: String) = Result.success(emptyList<TrackPointRecord>())
    override suspend fun getForDay(dayStartInclusiveEpochMillis: Long, dayEndExclusiveEpochMillis: Long) = Result.success(tracks.values.toList())
    override suspend fun create(track: Track) = Result.success(Unit).also { tracks[track.id] = track }
    override suspend fun appendPoints(trackId: String, points: List<TrackPoint>) =
        Result.success(Unit).also { tracks[trackId]?.let { tracks[trackId] = it.copy(points = it.points + points) } }
    override suspend fun end(trackId: String, endedAtEpochMillis: Long) = Result.success(Unit)
    override suspend fun setOriginWaypoint(trackId: String, waypointId: String) = Result.success(Unit)
    override suspend fun delete(id: String) = Result.success(Unit).also { tracks.remove(id) }
}

internal class WayHomeTestWaypoints : WaypointRepository {
    private val saved = mutableMapOf<String, Waypoint>()
    override suspend fun getAll() = Result.success(saved.values.toList())
    override suspend fun getForDay(dayStartInclusiveEpochMillis: Long, dayEndExclusiveEpochMillis: Long) = Result.success(saved.values.toList())
    override suspend fun getById(id: String) = Result.success(saved[id])
    override suspend fun getForTrack(trackId: String) = Result.success(saved.values.filter { it.trackId == trackId })
    override suspend fun detachFromTrack(trackId: String) = Result.success(Unit)
    override suspend fun save(waypoint: Waypoint) = Result.success(Unit).also { saved[waypoint.id] = waypoint }
    override suspend fun delete(id: String) = Result.success(Unit).also { saved.remove(id) }
}
