package com.zynergylabs.forager.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room's on-disk shape for one [com.zynergylabs.forager.app.domain.model.Waypoint]. Standalone, no owning row —
 * unlike [TrackPointEntity], a waypoint isn't part of anything else.
 *
 * [createdAtEpochMillis] is indexed as of [MIGRATION_9_10], for [WaypointDao]'s day-scoped read.
 *
 * [trackId] is a nullable, indexed link column as of [MIGRATION_12_13] — the
 * `MushroomLogEntryEntity.offlineRegionId` precedent: no `@ForeignKey` (none in this database, by
 * decision; the join is done in code), an index because [WaypointDao.getForTrack] filters on it.
 * See [com.zynergylabs.forager.app.domain.model.Waypoint.trackId] for what the link means.
 */
@Entity(tableName = "waypoints", indices = [Index("createdAtEpochMillis"), Index("trackId")])
data class WaypointEntity(
    @PrimaryKey val id: String,
    val lat: Double,
    val lng: Double,
    val altitude: Double?,
    val name: String,
    val note: String,
    val createdAtEpochMillis: Long,
    val trackId: String? = null,
    /**
     * [com.zynergylabs.forager.app.domain.model.WaypointDesignation]'s name, or `null` for an ordinary waypoint
     * — nullable `TEXT` as of [MIGRATION_13_14] (navigation HUD stage one). Not indexed: the only
     * readers already filter on `trackId` (indexed) or on an in-memory list, never on this column
     * alone.
     */
    val designation: String? = null,
)
