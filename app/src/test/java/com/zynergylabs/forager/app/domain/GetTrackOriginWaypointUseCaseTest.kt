package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.TrackPointRecord
import com.zynergylabs.forager.app.domain.model.Waypoint
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** HUD-foundations dispatch, Item 3: the read path for [Track.originWaypointId], joined in code across two repositories. */
class GetTrackOriginWaypointUseCaseTest {

    private val trailhead = Waypoint(id = "w-origin", lat = 45.52, lng = -122.68, altitude = 50.0, name = "Trailhead", note = "", createdAtEpochMillis = 1_000L, trackId = "t1")

    @Test
    fun `resolves the waypoint a track's origin pointer names`() = runTest {
        val tracks = InMemoryTracks(Track(id = "t1", name = null, startedAtEpochMillis = 1_000L, endedAtEpochMillis = null, points = emptyList(), originWaypointId = "w-origin"))
        val waypoints = InMemoryWaypoints(trailhead)

        val origin = GetTrackOriginWaypointUseCase(tracks, waypoints)("t1").getOrThrow()

        assertEquals(trailhead, origin)
    }

    @Test
    fun `a track with no origin pointer resolves to null, not to its first point`() = runTest {
        val firstPoint = TrackPoint(lat = 45.5, lng = -122.6, altitude = null, accuracyMeters = 8f, timestampEpochMillis = 1_000L)
        val tracks = InMemoryTracks(Track(id = "t1", name = null, startedAtEpochMillis = 1_000L, endedAtEpochMillis = null, points = listOf(firstPoint)))

        assertNull(GetTrackOriginWaypointUseCase(tracks, InMemoryWaypoints(trailhead))("t1").getOrThrow())
    }

    @Test
    fun `an unknown track, or a pointer to a waypoint since deleted, resolves to null`() = runTest {
        val tracks = InMemoryTracks(Track(id = "t1", name = null, startedAtEpochMillis = 1_000L, endedAtEpochMillis = null, points = emptyList(), originWaypointId = "w-gone"))

        assertNull(GetTrackOriginWaypointUseCase(tracks, InMemoryWaypoints())("t1").getOrThrow())
        assertNull(GetTrackOriginWaypointUseCase(tracks, InMemoryWaypoints(trailhead))("no-such-track").getOrThrow())
    }

    @Test
    fun `a repository failure stays a failure rather than reading as no origin`() = runTest {
        val tracks = InMemoryTracks(Track(id = "t1", name = null, startedAtEpochMillis = 1_000L, endedAtEpochMillis = null, points = emptyList(), originWaypointId = "w-origin"))
        val waypoints = InMemoryWaypoints(trailhead).apply { failReads = true }

        assertTrue(GetTrackOriginWaypointUseCase(tracks, waypoints)("t1").isFailure)
    }
}

internal class InMemoryTracks(vararg initial: Track) : TrackRepository {
    val tracks = initial.associateBy(Track::id).toMutableMap()
    val deletedIds = mutableListOf<String>()
    var failDelete = false

    override suspend fun getAll(): Result<List<Track>> = Result.success(tracks.values.toList())
    override suspend fun getById(id: String): Result<Track?> = Result.success(tracks[id])
    override suspend fun getFullRecord(id: String): Result<List<TrackPointRecord>> =
        Result.success((tracks[id]?.points ?: emptyList()).map { TrackPointRecord(it, kept = !it.isNetworkProviderFix()) })
    override suspend fun getForDay(dayStartInclusiveEpochMillis: Long, dayEndExclusiveEpochMillis: Long): Result<List<Track>> =
        Result.success(emptyList())
    override suspend fun create(track: Track): Result<Unit> = Result.success(Unit).also { tracks[track.id] = track }
    override suspend fun appendPoints(trackId: String, points: List<TrackPoint>): Result<Unit> = Result.success(Unit)
    override suspend fun end(trackId: String, endedAtEpochMillis: Long): Result<Unit> = Result.success(Unit)
    override suspend fun setOriginWaypoint(trackId: String, waypointId: String): Result<Unit> {
        tracks[trackId]?.let { tracks[trackId] = it.copy(originWaypointId = waypointId) }
        return Result.success(Unit)
    }
    override suspend fun delete(id: String): Result<Unit> {
        if (failDelete) return Result.failure(IllegalStateException("delete refused by test"))
        tracks.remove(id)
        deletedIds += id
        return Result.success(Unit)
    }
}

internal class InMemoryWaypoints(vararg initial: Waypoint) : WaypointRepository {
    val waypoints = initial.associateBy(Waypoint::id).toMutableMap()
    val detachedTrackIds = mutableListOf<String>()
    var failReads = false
    var failDetach = false

    override suspend fun getAll(): Result<List<Waypoint>> = Result.success(waypoints.values.toList())
    override suspend fun getForDay(dayStartInclusiveEpochMillis: Long, dayEndExclusiveEpochMillis: Long): Result<List<Waypoint>> =
        Result.success(emptyList())
    override suspend fun getById(id: String): Result<Waypoint?> =
        if (failReads) Result.failure(IllegalStateException("read refused by test")) else Result.success(waypoints[id])
    override suspend fun getForTrack(trackId: String): Result<List<Waypoint>> =
        Result.success(waypoints.values.filter { it.trackId == trackId }.sortedBy(Waypoint::createdAtEpochMillis))
    override suspend fun detachFromTrack(trackId: String): Result<Unit> {
        if (failDetach) return Result.failure(IllegalStateException("detach refused by test"))
        detachedTrackIds += trackId
        waypoints.replaceAll { _, waypoint -> if (waypoint.trackId == trackId) waypoint.copy(trackId = null) else waypoint }
        return Result.success(Unit)
    }
    override suspend fun save(waypoint: Waypoint): Result<Unit> = Result.success(Unit).also { waypoints[waypoint.id] = waypoint }
    override suspend fun delete(id: String): Result<Unit> = Result.success(Unit).also { waypoints.remove(id) }
}
