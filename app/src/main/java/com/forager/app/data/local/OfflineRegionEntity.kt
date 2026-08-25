package com.forager.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room's on-disk shape for one downloaded offline map region — the index the design doc's "Region
 * management" section adds on top of `OfflineManager`'s own store, which owns the actual tiles but
 * has no way to query "which regions do I have" beyond an opaque list.
 *
 * [id] is **not** an app-generated key — it's MapLibre's own native `OfflineRegion.getId()`
 * (verified via `javap` against the pinned `org.maplibre.gl:android-sdk:13.5.0` artifact), assigned
 * only once `OfflineManager.createOfflineRegion` succeeds. Reusing it as this row's primary key
 * means looking up "which region is this" from an `OfflineRegion` never needs a bounds-matching
 * heuristic — see `com.forager.app.map.MapLibreOfflineMapRepository`.
 *
 * Every other column is written into the region's own metadata blob too (see
 * `com.forager.app.map.RegionMetadata`), not just here — the design doc's own reasoning: "the blob
 * is the only durable label inside MapLibre's store, and the table is the source of truth so a
 * region can be rebuilt from scratch after a corruption or a library change." If this table is ever
 * lost (app data partially cleared, a migration bug) while `OfflineManager`'s own store survives,
 * `MapLibreOfflineMapRepository.listRegions` rebuilds the missing row from the surviving blob rather
 * than fabricating a placeholder name for it.
 */
@Entity(tableName = "offline_regions")
data class OfflineRegionEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val lat: Double,
    val lng: Double,
    val radiusKm: Int,
    val minZoom: Double,
    val maxZoom: Double,
    val createdAtEpochMillis: Long,
    /**
     * `true` for the small, automatic per-log-entry tile captures (Workstream B); `false` for a
     * region the user picked and downloaded themselves. Lets the region-management list filter
     * captures out without a second table. `@ColumnInfo(defaultValue = "0")` must match
     * `MIGRATION_5_6`'s `INTEGER NOT NULL DEFAULT 0` exactly — see that migration's doc comment for
     * why a mismatch here fails Room's schema validation at app startup rather than at compile time.
     */
    @ColumnInfo(defaultValue = "0")
    val isEntryCapture: Boolean = false,
)
