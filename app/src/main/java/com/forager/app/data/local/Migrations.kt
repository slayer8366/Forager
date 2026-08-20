package com.forager.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds the mushroom log's two tables (`mushroom_log_entries`, `log_photos`) on top of version 3's
 * `planned_trips`/`cached_searches`. A real, hand-written migration — not
 * `fallbackToDestructiveMigration()` — see [ForagerDatabase]'s doc comment for why: this is the
 * first schema bump since field notes became storable, and losing a season of them on an app
 * update would be entirely this app's fault, unlike the disposable/re-creatable data the earlier
 * destructive fallback was judged acceptable for.
 *
 * Column types and nullability here must match [MushroomLogEntryEntity]/[LogPhotoEntity] exactly —
 * see [MushroomLogEntryEntity]'s doc comment for what each column encodes and why. Covered by
 * `MushroomLogMigrationTest`, which builds a real version-3 database, migrates it, and asserts the
 * pre-existing rows survive and the new tables are queryable.
 */
val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `mushroom_log_entries` (
            `id` TEXT NOT NULL,
            `lat` REAL NOT NULL,
            `lng` REAL NOT NULL,
            `foundOn` TEXT NOT NULL,
            `entryNotes` TEXT NOT NULL,
            `ownIdentification` TEXT,
            `syncStateKind` TEXT NOT NULL,
            `syncProgress` REAL,
            `syncRemoteObservationId` TEXT,
            `syncUploadedAtEpochMillis` INTEGER,
            `syncFailureReason` TEXT,
            `capShape` TEXT,
            `capSurface` TEXT,
            `capDecorationsState` TEXT NOT NULL,
            `capDecorationsValue` TEXT,
            `capMargin` TEXT,
            `capNotes` TEXT NOT NULL,
            `hymenophoreKind` TEXT,
            `gillAttachment` TEXT,
            `gillSpacing` TEXT,
            `gillEdge` TEXT,
            `hymenophoreNotes` TEXT NOT NULL,
            `stipeKind` TEXT,
            `stipePosition` TEXT,
            `stipeInterior` TEXT,
            `stipeBase` TEXT,
            `stipeNotes` TEXT NOT NULL,
            `annulusState` TEXT NOT NULL,
            `annulusValue` TEXT,
            `volvaState` TEXT NOT NULL,
            `volvaValue` TEXT,
            `veilNotes` TEXT NOT NULL,
            `fleshTexture` TEXT,
            `colorChangeState` TEXT NOT NULL,
            `colorChangeValue` TEXT,
            `exudateState` TEXT NOT NULL,
            `exudateValue` TEXT,
            `contextFleshNotes` TEXT NOT NULL,
            `sporePrintColorKind` TEXT,
            `sporePrintOtherText` TEXT,
            `sporePrintReadOn` TEXT,
            `sporePrintNotes` TEXT NOT NULL,
            `associationKind` TEXT,
            `associationHostSpecies` TEXT,
            `associationOtherText` TEXT,
            `forestType` TEXT,
            `hostHealth` TEXT,
            `hostSubstrateNotes` TEXT NOT NULL,
            PRIMARY KEY(`id`))
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `log_photos` (
            `id` TEXT NOT NULL,
            `entryId` TEXT NOT NULL,
            `relativePath` TEXT NOT NULL,
            PRIMARY KEY(`id`))
            """.trimIndent(),
        )
    }
}

/**
 * Adds `tracks`, `track_points`, and `waypoints` on top of version 4's tables — Phase 1a of the
 * Forager Navigator plan (`docs/plans/forager-navigator-plan.md`). A real, hand-written migration,
 * not `fallbackToDestructiveMigration()`: recorded tracks and dropped waypoints are irreplaceable
 * field data in exactly the way mushroom-log entries are — see [ForagerDatabase]'s doc comment and
 * [MIGRATION_3_4]'s for the precedent this follows.
 *
 * Column types and nullability here must match [TrackEntity]/[TrackPointEntity]/[WaypointEntity]
 * exactly. `track_points.id` is `INTEGER PRIMARY KEY AUTOINCREMENT`, not `TEXT`, matching
 * [TrackPointEntity]'s doc comment on why that one table departs from this database's usual `UUID`
 * key. The index on `track_points.trackId` uses Room's standard generated name
 * (`index_<table>_<column>`) so Room's own schema validation at app startup recognizes it as the
 * one [TrackPointEntity]'s `@Index` declares, rather than flagging a schema mismatch.
 */
val MIGRATION_4_5: Migration = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `tracks` (
            `id` TEXT NOT NULL,
            `name` TEXT,
            `startedAtEpochMillis` INTEGER NOT NULL,
            `endedAtEpochMillis` INTEGER,
            PRIMARY KEY(`id`))
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `track_points` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `trackId` TEXT NOT NULL,
            `lat` REAL NOT NULL,
            `lng` REAL NOT NULL,
            `altitude` REAL,
            `accuracyMeters` REAL,
            `timestampEpochMillis` INTEGER NOT NULL)
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_track_points_trackId` ON `track_points` (`trackId`)",
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `waypoints` (
            `id` TEXT NOT NULL,
            `lat` REAL NOT NULL,
            `lng` REAL NOT NULL,
            `altitude` REAL,
            `name` TEXT NOT NULL,
            `note` TEXT NOT NULL,
            `createdAtEpochMillis` INTEGER NOT NULL,
            PRIMARY KEY(`id`))
            """.trimIndent(),
        )
    }
}
