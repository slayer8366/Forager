package com.zynergylabs.forager.app.data.repository

import com.zynergylabs.forager.app.data.local.PlannedTripEntity
import com.zynergylabs.forager.app.data.local.PlannedTripRouteDao
import com.zynergylabs.forager.app.data.local.PlannedTripRouteLegEntity
import com.zynergylabs.forager.app.data.local.PlannedTripRouteStopEntity
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.PlannedTripRepository
import com.zynergylabs.forager.app.domain.TrackPathCodec
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.domain.route.PlottedRoute
import com.zynergylabs.forager.app.domain.route.RouteLeg
import com.zynergylabs.forager.app.domain.route.RouteStop
import com.zynergylabs.forager.app.domain.route.StraightReason
import java.time.LocalDate

/**
 * Room-backed [PlannedTripRepository]; the only place the planned-trip entities and [PlannedTrip] meet.
 *
 * Since schema 23 a trip may carry a route (dispatch 2026-10-30-02, step 1), saved and read with it through
 * [PlannedTripRouteDao] in one transaction. **A stored route that does not read back as a valid [PlottedRoute] is
 * refused, never guessed into shape:** the trip loads without its route, and the refusal is logged through [errorLog]
 * (CLAUDE.md: no fallback that isn't logged). A leg row with both its trail indexes and a straight reason, or neither,
 * is one such row (Amendment 1, "Leg kind").
 */
class RoomPlannedTripRepository(
    private val dao: PlannedTripRouteDao,
    private val errorLog: ErrorLog,
) : PlannedTripRepository {

    override suspend fun getAll(): Result<List<PlannedTrip>> = runCatchingCancellable {
        val rows = dao.loadAll()
        val stopsByTrip = rows.stops.groupBy { it.tripId }
        val legsByTrip = rows.legs.groupBy { it.tripId }
        rows.trips.map { trip -> trip.toDomain(routeOf(trip, stopsByTrip[trip.id].orEmpty(), legsByTrip[trip.id].orEmpty())) }
    }

    override suspend fun save(trip: PlannedTrip): Result<Unit> = runCatchingCancellable {
        val route = trip.route
        dao.save(
            trip = trip.toEntity(),
            stops = route?.stops?.mapIndexed { i, stop -> stop.toEntity(trip.id, i) }.orEmpty(),
            legs = route?.legs?.mapIndexed { i, leg -> leg.toEntity(trip.id, i) }.orEmpty(),
        )
    }

    override suspend fun delete(id: String): Result<Unit> =
        runCatchingCancellable { dao.delete(id) }

    /** The route these rows hold, or null: none stored, or refused (logged). */
    private fun routeOf(trip: PlannedTripEntity, stops: List<PlannedTripRouteStopEntity>, legs: List<PlannedTripRouteLegEntity>): PlottedRoute? {
        if (stops.isEmpty() && legs.isEmpty()) return null
        if (STUB) return null // STUB (tests first): routes are not read yet.
        return try {
            require(stops.map { it.stopIndex } == stops.indices.toList()) { "stop indexes are ${stops.map { it.stopIndex }}" }
            require(legs.map { it.legIndex } == legs.indices.toList()) { "leg indexes are ${legs.map { it.legIndex }}" }
            PlottedRoute(stops.map { RouteStop(it.name, LatLng(it.lat, it.lng)) }, legs.map { it.toDomain() }).also { route ->
                // PlannedTrip's own rule (a route trip's pin is its first stop), checked here so a row breaking it refuses
                // the route rather than the whole list.
                require(route.stops.first().position == LatLng(trip.lat, trip.lng)) { "the trip's location is not its route's first stop" }
            }
        } catch (e: IllegalArgumentException) {
            errorLog.w(TAG, "Planned trip's stored route refused; the trip loads without it.", StoredRouteRefused(e.message ?: "invalid", e))
            null
        }
    }

    /** What [errorLog] is handed for a refused route: [ErrorLog] takes a throwable, and this names the reason. */
    class StoredRouteRefused(reason: String, cause: Throwable) : Exception(reason, cause)

    private companion object {
        const val STUB = true
        const val TAG = "RoomPlannedTripRepository"
    }
}

private fun PlannedTripEntity.toDomain(route: PlottedRoute?) = PlannedTrip(
    id = id,
    name = name,
    location = LatLng(lat = lat, lng = lng),
    date = date?.let(LocalDate::parse),
    route = route,
)

private fun PlannedTrip.toEntity() = PlannedTripEntity(
    id = id,
    name = name,
    lat = location.lat,
    lng = location.lng,
    date = date?.toString(),
)

private fun RouteStop.toEntity(tripId: String, index: Int) =
    PlannedTripRouteStopEntity(tripId = tripId, stopIndex = index, name = name, lat = position.lat, lng = position.lng)

private fun RouteLeg.toEntity(tripId: String, index: Int) = when (this) {
    is RouteLeg.Trail -> PlannedTripRouteLegEntity(tripId, index, TrackPathCodec.encode(line), meters, trailFrom, trailTo, straightReason = null)
    is RouteLeg.Straight -> PlannedTripRouteLegEntity(tripId, index, TrackPathCodec.encode(line), meters, trailFrom = null, trailTo = null, straightReason = reason.name)
}

/** The kind is read from which nullable columns are set; any other shape is refused (throws [IllegalArgumentException]). */
private fun PlannedTripRouteLegEntity.toDomain(): RouteLeg {
    val line = TrackPathCodec.decode(path)
    val trailFrom = trailFrom
    val trailTo = trailTo
    val straightReason = straightReason
    return when {
        trailFrom != null && trailTo != null && straightReason == null -> RouteLeg.Trail(line, meters, trailFrom, trailTo)
        trailFrom == null && trailTo == null && straightReason != null -> RouteLeg.Straight(
            line, meters,
            StraightReason.entries.firstOrNull { it.name == straightReason }
                ?: throw IllegalArgumentException("leg $legIndex has an unknown straight reason '$straightReason'"),
        )
        else -> throw IllegalArgumentException(
            "leg $legIndex is neither a trail leg nor a straight one (trailFrom=$trailFrom, trailTo=$trailTo, straightReason=$straightReason)",
        )
    }
}
