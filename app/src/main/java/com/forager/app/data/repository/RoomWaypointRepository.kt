package com.forager.app.data.repository

import com.forager.app.data.local.WaypointDao
import com.forager.app.data.local.WaypointEntity
import com.forager.app.domain.WaypointRepository
import com.forager.app.domain.model.Waypoint

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
)

private fun Waypoint.toEntity() = WaypointEntity(
    id = id,
    lat = lat,
    lng = lng,
    altitude = altitude,
    name = name,
    note = note,
    createdAtEpochMillis = createdAtEpochMillis,
)
