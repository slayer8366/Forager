package com.forager.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Room's on-disk shape for one [com.forager.app.domain.model.Waypoint]. Standalone, no owning row — unlike [TrackPointEntity], a waypoint isn't part of anything else. */
@Entity(tableName = "waypoints")
data class WaypointEntity(
    @PrimaryKey val id: String,
    val lat: Double,
    val lng: Double,
    val altitude: Double?,
    val name: String,
    val note: String,
    val createdAtEpochMillis: Long,
)
