package com.zynergylabs.forager.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * Planned trips with their routes (dispatch 2026-10-30-02, step 1). A DAO of its own rather than new methods on
 * [PlannedTripDao], because the `LegacyForagerDatabaseVn` test fixtures declare [PlannedTripDao] on databases that have
 * no route tables, where a query naming them would not compile. An abstract class, as [CachedSearchDao] records, so the
 * multi-table writes and the read are [Transaction]s: a trip and its route are saved, deleted and read together.
 */
@Dao
abstract class PlannedTripRouteDao {

    /** Every trip with every route row, read in one transaction so a route is never read half-saved. */
    @Transaction
    open suspend fun loadAll(): PlannedTripRows = PlannedTripRows(trips(), stops(), legs())

    /** Saves [trip], replacing any trip with its id, and replaces its route rows with [stops] and [legs] (none for no route). */
    @Transaction
    open suspend fun save(trip: PlannedTripEntity, stops: List<PlannedTripRouteStopEntity>, legs: List<PlannedTripRouteLegEntity>) {
        upsertTrip(trip)
        deleteStops(trip.id)
        deleteLegs(trip.id)
        insertStops(stops)
        insertLegs(legs)
    }

    /** Deletes the trip with [id] and its route rows. A no-op when there is no such trip. */
    @Transaction
    open suspend fun delete(id: String) {
        deleteTrip(id)
        deleteStops(id)
        deleteLegs(id)
    }

    @Query("SELECT * FROM planned_trips")
    abstract suspend fun trips(): List<PlannedTripEntity>

    @Query("SELECT * FROM planned_trip_route_stops ORDER BY tripId, stopIndex")
    abstract suspend fun stops(): List<PlannedTripRouteStopEntity>

    @Query("SELECT * FROM planned_trip_route_legs ORDER BY tripId, legIndex")
    abstract suspend fun legs(): List<PlannedTripRouteLegEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertTrip(trip: PlannedTripEntity)

    /** Plain insert, not replace: the rows were just deleted, so a clash is a bug and fails the save. */
    @Insert
    abstract suspend fun insertStops(stops: List<PlannedTripRouteStopEntity>)

    @Insert
    abstract suspend fun insertLegs(legs: List<PlannedTripRouteLegEntity>)

    @Query("DELETE FROM planned_trips WHERE id = :id")
    abstract suspend fun deleteTrip(id: String)

    @Query("DELETE FROM planned_trip_route_stops WHERE tripId = :tripId")
    abstract suspend fun deleteStops(tripId: String)

    @Query("DELETE FROM planned_trip_route_legs WHERE tripId = :tripId")
    abstract suspend fun deleteLegs(tripId: String)
}

/** What [PlannedTripRouteDao.loadAll] reads: the rows, joined by trip id in the repository. */
data class PlannedTripRows(
    val trips: List<PlannedTripEntity>,
    val stops: List<PlannedTripRouteStopEntity>,
    val legs: List<PlannedTripRouteLegEntity>,
)
