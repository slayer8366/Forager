package com.forager.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The app's only local database, holding planned trips, the offline search cache, and the
 * mushroom log.
 *
 * [version] 2 adds `planned_trips.name` (see [PlannedTripEntity.name]) — the first real schema
 * change this database has had. The judgment call it raises — write a real [androidx.room.migration.Migration]
 * to keep any version-1 rows already on a device, or fall back to
 * [androidx.room.RoomDatabase.Builder.fallbackToDestructiveMigration] and drop them — was put to
 * the user rather than decided silently, per CLAUDE.md. Their answer: the only version-1 data in
 * the wild is their own on-device testing, not anything worth preserving, so
 * `fallbackToDestructiveMigration()` is used here instead of a hand-written migration — simpler,
 * and with no subtle migration-SQL bug to get wrong for data nobody needs kept.
 *
 * [version] 3 adds `cached_searches` ([CachedSearchEntity]). Unlike the version-2 bump this one was
 * **not** put to the user, and the difference is what the data is rather than who decided: a
 * planned trip is something the user created and cannot get back, whereas every row in
 * `cached_searches` is a copy of an answer iNaturalist will give again. Losing the cache on a
 * schema change costs one repopulating search and nothing else, so the destructive fallback is not
 * a trade-off worth anyone's time to weigh.
 *
 * [version] 4 adds `mushroom_log_entries`/`log_photos` ([MushroomLogEntryEntity], [LogPhotoEntity])
 * via a real [MIGRATION_3_4] — **not** `fallbackToDestructiveMigration()`. Field notes are neither
 * disposable (like the search cache) nor trivially re-creatable (like a handful of test planned
 * trips): losing a season of them would be entirely this app's fault, so this is the first bump
 * this database ships an actual hand-written migration for, added to [create] below alongside the
 * unchanged destructive fallback that still covers any device on version 1 or 2.
 *
 * `exportSchema` is `true` as of this bump, reversing the reasoning the version-1/2 comment above
 * gave for `false`: that reasoning ("a destructive fallback never reads a prior version") stops
 * applying the moment any migration on this database actually does — [MIGRATION_3_4] does, and a
 * later migration will need this version's schema history to migrate from. The schema JSON lands
 * under `app/schemas/` (see `room.schemaLocation` in `app/build.gradle.kts`); only version 4 onward
 * is exported, since versions 1-3 were never captured while `exportSchema` was `false`.
 *
 * [version] 5 adds `tracks`, `track_points`, and `waypoints` ([TrackEntity], [TrackPointEntity],
 * [WaypointEntity]) via a real [MIGRATION_4_5], for the same reason [MIGRATION_3_4] exists: a
 * recorded track or a dropped waypoint is irreplaceable field data, not something a destructive
 * fallback may drop.
 *
 * [version] 6 adds `offline_regions` ([OfflineRegionEntity]) and
 * [MushroomLogEntryEntity.offlineRegionId] via a real [MIGRATION_5_6] — same reasoning as
 * [MIGRATION_4_5]: a downloaded region is costly to recreate, not something to drop on a schema
 * bump. [OfflineRegionEntity] and [OfflineRegionDao] were added to this codebase before this
 * version but never registered here, so `offline_regions` has never existed in a real install
 * until this bump.
 *
 * [version] 7 makes `mushroom_log_entries.lat`/`.lng` nullable via a real [MIGRATION_6_7] —
 * Workstream L3 (`docs/plans/pr26-rework.md`): a log entry must be able to exist with no
 * location, since L4 routes entry creation to the entry page rather than through a
 * location-placement step first. Existing entries and their coordinates are not something a
 * schema bump may drop, same reasoning as every hand-written migration above.
 *
 * [version] 8 inverts photo ownership via a real [MIGRATION_7_8] — owner decision, 2026-08-22:
 * "photos live in a gallery in their own right; log entries reference them, they do not own them."
 * `log_photos` loses its required `entryId` and gains [LogPhotoEntity.createdAtEpochMillis]; a new
 * [LogEntryPhotoCrossRef] table (`log_entry_photos`) carries the entry↔photo relationship,
 * many-to-many rather than one-to-many (owner decision: "the album model"). Every existing
 * relationship is preserved as a cross-reference row before the old column is gone, same
 * no-data-loss reasoning as every hand-written migration above.
 *
 * [version] 9 adds [MushroomLogEntryEntity.isDraft] via a real [MIGRATION_8_9] — Workstream L4b,
 * owner decision, 2026-08-22: persisted drafts. "A draft is an entry row marked uncommitted" — a
 * discriminator column, not a second table or a change-list; see [MIGRATION_8_9]'s own doc comment
 * for the full reasoning, including why this codebase's legacy-fixture pitfall (see
 * `docs/audits/2026-08-24-migration-fixture-entity-reuse-pitfall.md`) does not recur here. Every
 * pre-existing row is a real, previously-committed entry, never a draft under the old model, so this
 * migration marks every one of them `isDraft = 0` explicitly.
 */
@Database(
    entities = [
        PlannedTripEntity::class,
        CachedSearchEntity::class,
        MushroomLogEntryEntity::class,
        LogPhotoEntity::class,
        LogEntryPhotoCrossRef::class,
        TrackEntity::class,
        TrackPointEntity::class,
        WaypointEntity::class,
        OfflineRegionEntity::class,
    ],
    version = 9,
    exportSchema = true,
)
abstract class ForagerDatabase : RoomDatabase() {
    abstract fun plannedTripDao(): PlannedTripDao

    abstract fun cachedSearchDao(): CachedSearchDao

    abstract fun mushroomLogDao(): MushroomLogDao

    abstract fun trackDao(): TrackDao

    abstract fun waypointDao(): WaypointDao

    abstract fun offlineRegionDao(): OfflineRegionDao

    companion object {
        fun create(context: Context): ForagerDatabase = Room.databaseBuilder(
            context.applicationContext,
            ForagerDatabase::class.java,
            "forager.db",
        ).addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9).fallbackToDestructiveMigration(true).build()
    }
}
