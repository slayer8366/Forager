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
 */
@Database(
    entities = [
        PlannedTripEntity::class,
        CachedSearchEntity::class,
        MushroomLogEntryEntity::class,
        LogPhotoEntity::class,
        TrackEntity::class,
        TrackPointEntity::class,
        WaypointEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class ForagerDatabase : RoomDatabase() {
    abstract fun plannedTripDao(): PlannedTripDao

    abstract fun cachedSearchDao(): CachedSearchDao

    abstract fun mushroomLogDao(): MushroomLogDao

    abstract fun trackDao(): TrackDao

    abstract fun waypointDao(): WaypointDao

    companion object {
        fun create(context: Context): ForagerDatabase = Room.databaseBuilder(
            context.applicationContext,
            ForagerDatabase::class.java,
            "forager.db",
        ).addMigrations(MIGRATION_3_4, MIGRATION_4_5).fallbackToDestructiveMigration(true).build()
    }
}
