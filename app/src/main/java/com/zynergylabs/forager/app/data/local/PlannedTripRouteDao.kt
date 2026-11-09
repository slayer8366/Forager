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

    /**
     * Every trip with every route row, read in one transaction so a route is never read half-saved. First, every stop's link to
     * a waypoint or trip that is no longer on the phone is cleared (dispatch 2026-11-06-05, Amendment 2: "If the waypoint is
     * deleted, the link is cleared and the stop stays"), so a route never reads back a link to nothing. Done here rather than in
     * the waypoint's delete because that delete's repository is built where this dispatch may not edit (AppContainer.kt, shared
     * with the next-turn branch); the stop's name and spot are never touched.
     */
    @Transaction
    open suspend fun loadAll(): PlannedTripRows {
        clearMissingWaypointLinks()
        clearMissingTripLinks()
        return PlannedTripRows(trips(), stops(), legs())
    }

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
        clearTripLinks(id)
    }

    /** Clears every stop's link to the trip [tripId] (schema 24): the stops stay, under their names and at their spots. */
    @Query("UPDATE planned_trip_route_stops SET linkedTripId = NULL WHERE linkedTripId = :tripId")
    abstract suspend fun clearTripLinks(tripId: String)

    @Query("UPDATE planned_trip_route_stops SET linkedWaypointId = NULL WHERE linkedWaypointId IS NOT NULL AND linkedWaypointId NOT IN (SELECT id FROM waypoints)")
    abstract suspend fun clearMissingWaypointLinks()

    @Query("UPDATE planned_trip_route_stops SET linkedTripId = NULL WHERE linkedTripId IS NOT NULL AND linkedTripId NOT IN (SELECT id FROM planned_trips)")
    abstract suspend fun clearMissingTripLinks()

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
