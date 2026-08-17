package com.forager.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room's on-disk shape for a planned trip. Room annotations are data-layer only — never
 * `domain/`, per CLAUDE.md — so this type and everything that touches it
 * ([PlannedTripDao], [ForagerDatabase], `RoomPlannedTripRepository`) stay in `data/`, and
 * `RoomPlannedTripRepository` is the only place this ever meets [com.forager.app.domain.model.PlannedTrip].
 *
 * [name] arrived in schema version 2 — see [ForagerDatabase]'s doc comment for why an on-device
 * install already holding version-1 rows (no [name] column) is dropped via
 * `fallbackToDestructiveMigration` rather than migrated in place.
 */
@Entity(tableName = "planned_trips")
data class PlannedTripEntity(
    @PrimaryKey val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    /** ISO-8601 (`yyyy-MM-dd`, [java.time.LocalDate.toString]/[java.time.LocalDate.parse]) — Room has no native LocalDate column type. */
    val date: String,
)
