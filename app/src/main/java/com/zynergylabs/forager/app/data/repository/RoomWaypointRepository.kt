package com.zynergylabs.forager.app.data.repository

import com.zynergylabs.forager.app.data.local.WaypointDao
import com.zynergylabs.forager.app.data.local.WaypointEntity
import com.zynergylabs.forager.app.domain.WaypointRepository
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.WaypointDesignation

/** Room-backed [WaypointRepository]; the only place [WaypointEntity] and [Waypoint] meet. */
class RoomWaypointRepository(
    private val dao: WaypointDao,
) : WaypointRepository {

    override suspend fun getAll(): Result<List<Waypoint>> =
        runCatchingCancellable { dao.getAll().map(WaypointEntity::toDomain) }

    override suspend fun getForDay(dayStartInclusiveEpochMillis: Long, dayEndExclusiveEpochMillis: Long): Result<List<Waypoint>> =
        runCatchingCancellable {
            dao.getForDay(dayStartInclusiveEpochMillis, dayEndExclusiveEpochMillis).map(WaypointEntity::toDomain)
        }

    override suspend fun getById(id: String): Result<Waypoint?> =
        runCatchingCancellable { dao.getById(id)?.toDomain() }

    override suspend fun getForTrack(trackId: String): Result<List<Waypoint>> =
        runCatchingCancellable { dao.getForTrack(trackId).map(WaypointEntity::toDomain) }

    override suspend fun detachFromTrack(trackId: String): Result<Unit> =
        runCatchingCancellable { dao.clearTrackId(trackId) }

    override suspend fun save(waypoint: Waypoint): Result<Unit> =
        runCatchingCancellable { dao.upsert(waypoint.toEntity()) }

    override suspend fun delete(id: String): Result<Unit> =
        runCatchingCancellable { dao.deleteById(id) }
}

private fun WaypointEntity.toDomain() = Waypoint(
    id = id,
    lat = lat,
    lng = lng,
    altitude = altitude,
    name = name,
    note = note,
    createdAtEpochMillis = createdAtEpochMillis,
    trackId = trackId,
    // valueOf, not a lenient firstOrNull: a stored name this build doesn't know is a corrupt row,
    // and runCatchingCancellable turns the throw into a reported read failure rather than a
    // silently-ordinary waypoint (CLAUDE.md: no default fallback that isn't reported).
    designation = designation?.let(WaypointDesignation::valueOf),
)

private fun Waypoint.toEntity() = WaypointEntity(
    id = id,
    lat = lat,
    lng = lng,
    altitude = altitude,
    name = name,
    note = note,
    createdAtEpochMillis = createdAtEpochMillis,
    trackId = trackId,
    designation = designation?.name,
)
