package com.forager.app.data.repository

import com.forager.app.data.local.TrackDao
import com.forager.app.data.local.TrackEntity
import com.forager.app.data.local.TrackPointEntity
import com.forager.app.domain.TrackRepository
import com.forager.app.domain.model.Track
import com.forager.app.domain.model.TrackPoint

/**
 * Room-backed [TrackRepository]; the only place [TrackEntity]/[TrackPointEntity] and [Track]/
 * [TrackPoint] meet.
 *
 * [getById] loads a track's full point list in one call — appropriate for reading back one
 * recorded track, but [appendPoints] never round-trips through [Track]/[getById] itself: it maps
 * straight from the domain [TrackPoint]s handed in to entities and batch-inserts them, so a
 * recording session's per-fix write cost is one `INSERT` batch, not a read-modify-write of the
 * whole track on every sampled point.
 */
class RoomTrackRepository(
    private val dao: TrackDao,
) : TrackRepository {

    override suspend fun getAll(): Result<List<Track>> = runCatchingCancellable {
        dao.getAllTracks().map { entity -> entity.toDomain(dao.getPointsForTrack(entity.id)) }
    }

    override suspend fun getById(id: String): Result<Track?> = runCatchingCancellable {
        val entity = dao.getTrackById(id) ?: return@runCatchingCancellable null
        entity.toDomain(dao.getPointsForTrack(id))
    }

    override suspend fun create(track: Track): Result<Unit> = runCatchingCancellable {
        dao.insertTrack(track.toEntity())
    }

    override suspend fun appendPoints(trackId: String, points: List<TrackPoint>): Result<Unit> =
        runCatchingCancellable {
            dao.insertPoints(points.map { it.toEntity(trackId) })
        }

    override suspend fun end(trackId: String, endedAtEpochMillis: Long): Result<Unit> = runCatchingCancellable {
        dao.updateEndedAt(trackId, endedAtEpochMillis)
    }

    override suspend fun delete(id: String): Result<Unit> = runCatchingCancellable {
        dao.deleteTrackAndPoints(id)
    }
}

private fun TrackEntity.toDomain(points: List<TrackPointEntity>) = Track(
    id = id,
    name = name,
    startedAtEpochMillis = startedAtEpochMillis,
    endedAtEpochMillis = endedAtEpochMillis,
    points = points.map(TrackPointEntity::toDomain),
)

private fun Track.toEntity() = TrackEntity(
    id = id,
    name = name,
    startedAtEpochMillis = startedAtEpochMillis,
    endedAtEpochMillis = endedAtEpochMillis,
)

private fun TrackPointEntity.toDomain() = TrackPoint(
    lat = lat,
    lng = lng,
    altitude = altitude,
    accuracyMeters = accuracyMeters,
    timestampEpochMillis = timestampEpochMillis,
)

private fun TrackPoint.toEntity(trackId: String) = TrackPointEntity(
    trackId = trackId,
    lat = lat,
    lng = lng,
    altitude = altitude,
    accuracyMeters = accuracyMeters,
    timestampEpochMillis = timestampEpochMillis,
)
