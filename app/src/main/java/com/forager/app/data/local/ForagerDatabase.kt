package com.forager.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The app's only local database, holding planned trips and the offline search cache.
 *
 * [version] 2 adds `planned_trips.name` (see [PlannedTripEntity.name]) — the first real schema
 * change this database has had. The judgment call it raises — write a real [androidx.room.migration.Migration]
 * to keep any version-1 rows already on a device, or fall back to
 * [androidx.room.RoomDatabase.Builder.fallbackToDestructiveMigration] and drop them — was put to
 * the user rather than decided silently, per CLAUDE.md. Their answer: the only version-1 data in
 * the wild is their own on-device testing, not anything worth preserving, so
 * `fallbackToDestructiveMigration()` is used here instead of a hand-written migration — simpler,
 * and with no subtle migration-SQL bug to get wrong for data nobody needs kept. `exportSchema`
 * stays `false` for the same reason it started that way (see the version-1 history of this file):
 * a schema-history JSON is only worth exporting once a migration actually has to read a prior
 * version, and a destructive fallback never does.
 *
 * [version] 3 adds `cached_searches` ([CachedSearchEntity]). Unlike the version-2 bump this one was
 * **not** put to the user, and the difference is what the data is rather than who decided: a
 * planned trip is something the user created and cannot get back, whereas every row in
 * `cached_searches` is a copy of an answer iNaturalist will give again. Losing the cache on a
 * schema change costs one repopulating search and nothing else, so the destructive fallback is not
 * a trade-off worth anyone's time to weigh.
 */
@Database(entities = [PlannedTripEntity::class, CachedSearchEntity::class], version = 3, exportSchema = false)
abstract class ForagerDatabase : RoomDatabase() {
    abstract fun plannedTripDao(): PlannedTripDao

    abstract fun cachedSearchDao(): CachedSearchDao

    companion object {
        fun create(context: Context): ForagerDatabase = Room.databaseBuilder(
            context.applicationContext,
            ForagerDatabase::class.java,
            "forager.db",
        ).fallbackToDestructiveMigration(true).build()
    }
}
