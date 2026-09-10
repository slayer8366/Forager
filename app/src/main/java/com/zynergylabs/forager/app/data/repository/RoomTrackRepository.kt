package com.zynergylabs.forager.app.data.repository

import com.zynergylabs.forager.app.data.local.TrackDao
import com.zynergylabs.forager.app.data.local.TrackEntity
import com.zynergylabs.forager.app.data.local.TrackPointEntity
import com.zynergylabs.forager.app.domain.TIMESTAMP_MILLIS_NON_ZERO_RULE
import com.zynergylabs.forager.app.domain.TrackRepository
import com.zynergylabs.forager.app.domain.excludeNetworkProviderFixes
import com.zynergylabs.forager.app.domain.isNetworkProviderFix
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.TrackPointRecord

/**
 * Room-backed [TrackRepository]; the only place [TrackEntity]/[TrackPointEntity] and [Track]/
 * [TrackPoint] meet — and therefore the one place the network-provider-fix rule runs
 * ([com.zynergylabs.forager.app.domain.excludeNetworkProviderFixes], timestamp-filter dispatch): every read of
 * `track_points` passes through [toDomain] below, so no consumer can see an excluded point and no
 * consumer has to remember to apply the rule. The rows themselves are untouched; the count of what
 * was left out rides on [Track.excludedPointCount].
 *
 * [getById] loads a track's full point list in one call — appropriate for reading back one
 * recorded track, but [appendPoints] never round-trips through [Track]/[getById] itself: it maps
 * straight from the domain [TrackPoint]s handed in to entities and batch-inserts them, so a
 * recording session's per-fix write cost is one `INSERT` batch, not a read-modify-write of the
 * whole track on every sampled point.
 *
 * [getFullRecord] is the one deliberate exception to "every read passes through [toDomain]" —
 * GPX full-record export dispatch. It reads the same `track_points` rows but skips
 * [excludeNetworkProviderFixes] entirely, attaching the verdict per point instead of dropping the
 * excluded ones, and naming the rule that caught each excluded one
 * ([TIMESTAMP_MILLIS_NON_ZERO_RULE]) so an exported file can say which rule produced a verdict
 * rather than only that one did. Its only caller is the GPX exporter.
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

    override suspend fun getFullRecord(id: String): Result<List<TrackPointRecord>> = runCatchingCancellable {
        dao.getPointsForTrack(id).map { entity ->
            val point = entity.toDomain()
            if (point.isNetworkProviderFix()) {
                TrackPointRecord(point = point, kept = false, excludedByRule = TIMESTAMP_MILLIS_NON_ZERO_RULE)
            } else {
                TrackPointRecord(point = point, kept = true)
            }
        }
    }

    override suspend fun getForDay(dayStartInclusiveEpochMillis: Long, dayEndExclusiveEpochMillis: Long): Result<List<Track>> =
        runCatchingCancellable {
            dao.getTracksForDay(dayStartInclusiveEpochMillis, dayEndExclusiveEpochMillis)
                .map { entity -> entity.toDomain(dao.getPointsForTrack(entity.id)) }
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

    override suspend fun setOriginWaypoint(trackId: String, waypointId: String): Result<Unit> = runCatchingCancellable {
        dao.updateOriginWaypointId(trackId, waypointId)
    }

    override suspend fun delete(id: String): Result<Unit> = runCatchingCancellable {
        dao.deleteTrackAndPoints(id)
    }
}

private fun TrackEntity.toDomain(rows: List<TrackPointEntity>): Track {
    val stored = rows.map(TrackPointEntity::toDomain)
    // The read-seam rule — see NetworkProviderFix.kt. Applied here and nowhere else.
    val kept = excludeNetworkProviderFixes(stored)
    return Track(
        id = id,
        name = name,
        startedAtEpochMillis = startedAtEpochMillis,
        endedAtEpochMillis = endedAtEpochMillis,
        points = kept,
        originWaypointId = originWaypointId,
        excludedPointCount = stored.size - kept.size,
    )
}

private fun Track.toEntity() = TrackEntity(
    id = id,
    name = name,
    startedAtEpochMillis = startedAtEpochMillis,
    endedAtEpochMillis = endedAtEpochMillis,
    originWaypointId = originWaypointId,
)

private fun TrackPointEntity.toDomain() = TrackPoint(
    lat = lat,
    lng = lng,
    altitude = altitude,
    accuracyMeters = accuracyMeters,
    timestampEpochMillis = timestampEpochMillis,
    speedMetersPerSecond = speedMetersPerSecond,
    speedAccuracyMetersPerSecond = speedAccuracyMetersPerSecond,
)

private fun TrackPoint.toEntity(trackId: String) = TrackPointEntity(
    trackId = trackId,
    lat = lat,
    lng = lng,
    altitude = altitude,
    accuracyMeters = accuracyMeters,
    timestampEpochMillis = timestampEpochMillis,
    speedMetersPerSecond = speedMetersPerSecond,
    speedAccuracyMetersPerSecond = speedAccuracyMetersPerSecond,
)
