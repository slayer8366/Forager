package com.forager.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room's on-disk shape for one [com.forager.app.domain.model.Waypoint]. Standalone, no owning row —
 * unlike [TrackPointEntity], a waypoint isn't part of anything else.
 *
 * [createdAtEpochMillis] is indexed as of [MIGRATION_9_10], for [WaypointDao]'s day-scoped read.
 */
@Entity(tableName = "waypoints", indices = [Index("createdAtEpochMillis")])
data class WaypointEntity(
    @PrimaryKey val id: String,
    val lat: Double,
    val lng: Double,
    val altitude: Double?,
    val name: String,
    val note: String,
    val createdAtEpochMillis: Long,
)
