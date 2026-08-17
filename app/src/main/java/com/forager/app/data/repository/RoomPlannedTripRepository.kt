package com.forager.app.data.repository

import com.forager.app.data.local.PlannedTripDao
import com.forager.app.data.local.PlannedTripEntity
import com.forager.app.domain.PlannedTripRepository
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.PlannedTrip
import java.time.LocalDate

/** Room-backed [PlannedTripRepository]; the only place [PlannedTripEntity] and [PlannedTrip] meet. */
class RoomPlannedTripRepository(
    private val dao: PlannedTripDao,
) : PlannedTripRepository {

    override suspend fun getAll(): Result<List<PlannedTrip>> =
        runCatchingCancellable { dao.getAll().map(PlannedTripEntity::toDomain) }

    override suspend fun save(trip: PlannedTrip): Result<Unit> =
        runCatchingCancellable { dao.upsert(trip.toEntity()) }

    override suspend fun delete(id: String): Result<Unit> =
        runCatchingCancellable { dao.deleteById(id) }
}

private fun PlannedTripEntity.toDomain() = PlannedTrip(
    id = id,
    name = name,
    location = LatLng(lat = lat, lng = lng),
    date = LocalDate.parse(date),
)

private fun PlannedTrip.toEntity() = PlannedTripEntity(
    id = id,
    name = name,
    lat = location.lat,
    lng = location.lng,
    date = date.toString(),
)
