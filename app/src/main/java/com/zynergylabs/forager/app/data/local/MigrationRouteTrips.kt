package com.zynergylabs.forager.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version 22 to 23: a planned trip can carry a route, and its date becomes optional (dispatch 2026-10-30-02, step 1;
 * RECORD -1826, -1827). Its own file, not `Migrations.kt`, which is over 800 lines (the modular rule).
 *
 * 1. `planned_trips` is rebuilt with `date` nullable. A rebuild because SQLite cannot drop NOT NULL in place, and
 *    because the `LegacyForagerDatabaseVn` fixtures declare [PlannedTripEntity] directly, so their generated tables
 *    already have the new shape; the explicit column lists below work on both. Every existing trip keeps its date.
 *    `22.json` declares no index on the table, so none is recreated.
 * 2. `planned_trip_route_stops` and `planned_trip_route_legs` are created empty ([PlannedTripRouteStopEntity],
 *    [PlannedTripRouteLegEntity]).
 *
 * Version 23 was checked unclaimed on every remote ref on 2026-10-30 (the first stop,
 * docs/audits/2026-10-30-route-plotting-first-stop.md, (d)); re-checked before the PR. Heights are not stored here:
 * step 4b brings its own migration, 23 to 24 (Amendment 1, H3).
 */
val MIGRATION_22_23: Migration = object : Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE `planned_trips_new` (
            `id` TEXT NOT NULL,
            `name` TEXT NOT NULL,
            `lat` REAL NOT NULL,
            `lng` REAL NOT NULL,
            `date` TEXT,
            PRIMARY KEY(`id`))
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO `planned_trips_new` (`id`, `name`, `lat`, `lng`, `date`)
            SELECT `id`, `name`, `lat`, `lng`, `date` FROM `planned_trips`
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE `planned_trips`")
        db.execSQL("ALTER TABLE `planned_trips_new` RENAME TO `planned_trips`")
        db.execSQL(
            """
            CREATE TABLE `planned_trip_route_stops` (
            `tripId` TEXT NOT NULL,
            `stopIndex` INTEGER NOT NULL,
            `name` TEXT NOT NULL,
            `lat` REAL NOT NULL,
            `lng` REAL NOT NULL,
            PRIMARY KEY(`tripId`, `stopIndex`))
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE `planned_trip_route_legs` (
            `tripId` TEXT NOT NULL,
            `legIndex` INTEGER NOT NULL,
            `path` BLOB NOT NULL,
            `meters` REAL NOT NULL,
            `trailFrom` INTEGER,
            `trailTo` INTEGER,
            `straightReason` TEXT,
            PRIMARY KEY(`tripId`, `legIndex`))
            """.trimIndent(),
        )
    }
}
