package com.zynergylabs.forager.app.data.local

import androidx.room.Entity
import androidx.room.Index

/**
 * A planned trip's route, on disk (dispatch 2026-10-30-02, step 1; schema 23, [MIGRATION_22_23]). Two tables keyed by
 * the trip's id, joined in code ([PlannedTripRouteDao], `RoomPlannedTripRepository`): `ForagerDatabase` declares no
 * `@ForeignKey` (CLAUDE.md, the Room/DataStore pitfall). Each primary key leads with `tripId`, so its index serves the
 * reads by trip and no other index is needed.
 *
 * Every column is read by the repository's mapping to `PlottedRoute` and by backup and restore (`JournalTables`), the
 * readers Amendment 1 rules enough for the column rule.
 */

/**
 * One stop of a route: a copy of the waypoint's name and position when it was plotted. Stop 0 is the first.
 *
 * Since schema 24 ([MIGRATION_23_24]; dispatch 2026-11-06-05, Amendment 2) a stop added from then on keeps a soft link to what it
 * came from: [linkedWaypointId] or [linkedTripId], at most one set. Plain, indexed, nullable columns, joined in code, with no
 * `@ForeignKey` (CLAUDE.md). Deleting the record clears the link and the stop stays (the owner: "deleting a waypoint must never
 * break a route"): [PlannedTripRouteDao.loadAll] clears links to records that are gone before it reads, and a trip's delete
 * clears links to it at once. Read by `RoomPlannedTripRepository` into `RouteStop.link`, which Navigate on the route card reads.
 */
@Entity(
    tableName = "planned_trip_route_stops",
    primaryKeys = ["tripId", "stopIndex"],
    indices = [Index("linkedWaypointId"), Index("linkedTripId")],
)
data class PlannedTripRouteStopEntity(
    val tripId: String,
    val stopIndex: Int,
    val name: String,
    val lat: Double,
    val lng: Double,
    val linkedWaypointId: String? = null,
    val linkedTripId: String? = null,
)

/**
 * One leg of a route: leg *i* joins stop *i* to stop *i + 1*. [path] is the line, in `TrackPathCodec`'s bytes.
 *
 * The kind is not a column (Amendment 1, "Leg kind: derived from which nullable field is set"): a trail leg has
 * [trailFrom] and [trailTo] and no [straightReason]; a straight leg has a [straightReason] (a `StraightReason` name) and
 * neither index. A row in neither shape, or both, is refused when read, and logged.
 */
@Entity(tableName = "planned_trip_route_legs", primaryKeys = ["tripId", "legIndex"])
data class PlannedTripRouteLegEntity(
    val tripId: String,
    val legIndex: Int,
    val path: ByteArray,
    val meters: Double,
    val trailFrom: Int?,
    val trailTo: Int?,
    val straightReason: String?,
)
