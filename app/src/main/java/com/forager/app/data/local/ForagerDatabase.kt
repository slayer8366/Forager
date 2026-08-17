package com.forager.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The app's only local database, currently holding planned trips.
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
 */
@Database(entities = [PlannedTripEntity::class], version = 2, exportSchema = false)
abstract class ForagerDatabase : RoomDatabase() {
    abstract fun plannedTripDao(): PlannedTripDao

    companion object {
        fun create(context: Context): ForagerDatabase = Room.databaseBuilder(
            context.applicationContext,
            ForagerDatabase::class.java,
            "forager.db",
        ).fallbackToDestructiveMigration(true).build()
    }
}
