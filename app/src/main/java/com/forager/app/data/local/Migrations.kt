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

/**
 * Adds `offline_regions` on top of version 5's tables — the region index the design doc's
 * "Region management" section calls for (see [OfflineRegionEntity]'s own doc comment for the
 * table's purpose and why its `id` is MapLibre's native region id, not a generated one). A real,
 * hand-written migration, not `fallbackToDestructiveMigration()`, for the same reason
 * [MIGRATION_3_4] and [MIGRATION_4_5] are: a downloaded region is costly to recreate (network,
 * time), so a schema bump must not casually wipe the table that indexes them.
 *
 * `offline_regions` was never wired into [ForagerDatabase]'s entity list before this version —
 * `OfflineRegionEntity`/`OfflineRegionDao` landed in an earlier pass but weren't registered with
 * Room, so this table has never existed in a real install. `isEntryCapture` distinguishes the
 * small, automatic per-log-entry tile captures (added when a log entry is created, see
 * `docs/plans/pr26-rework.md`'s Workstream B) from user-picked regions, so the region-management
 * list can filter them out without a second table — `INTEGER NOT NULL DEFAULT 0` here must match
 * [OfflineRegionEntity.isEntryCapture]'s `@ColumnInfo(defaultValue = "0")` exactly, or Room's
 * schema validation at app startup sees a declared-default mismatch and fails to open the database
 * — this is a runtime failure, not a compile-time one, which is why the two sides are called out
 * together rather than trusted to stay in sync.
 *
 * Also adds `mushroom_log_entries.offlineRegionId` — a nullable reference to the region an entry's
 * capture belongs to (or that it happened to be inside, once that linkage exists) — and its index.
 * **Deliberately not a `@ForeignKey`.** See [MushroomLogEntryEntity.offlineRegionId]'s own doc
 * comment for the full rationale; the short version is that nothing about a log entry may change
 * as a side effect of something happening to a region, and any FK action (`SET_NULL`, `RESTRICT`,
 * cascading delete) would do exactly that.
 */
val MIGRATION_5_6: Migration = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `offline_regions` (
            `id` INTEGER NOT NULL,
            `name` TEXT NOT NULL,
            `lat` REAL NOT NULL,
            `lng` REAL NOT NULL,
            `radiusKm` INTEGER NOT NULL,
            `minZoom` REAL NOT NULL,
            `maxZoom` REAL NOT NULL,
            `createdAtEpochMillis` INTEGER NOT NULL,
            `isEntryCapture` INTEGER NOT NULL DEFAULT 0,
            PRIMARY KEY(`id`))
            """.trimIndent(),
        )
        db.execSQL(
            "ALTER TABLE `mushroom_log_entries` ADD COLUMN `offlineRegionId` INTEGER",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_mushroom_log_entries_offlineRegionId` " +
                "ON `mushroom_log_entries` (`offlineRegionId`)",
        )
    }
}

/**
 * Makes `mushroom_log_entries.lat`/`.lng` nullable — Workstream L3
 * (`docs/plans/pr26-rework.md`), so a log entry can exist with no location at all. L4 routes entry
 * creation to the entry page rather than through a location-placement step first, so an entry will
 * routinely start without one; see [com.forager.app.domain.model.MushroomLogEntry.foundAt]'s own
 * doc comment.
 *
 * SQLite has no `ALTER TABLE ... ALTER COLUMN`, so a `NOT NULL` constraint cannot be dropped in
 * place — this uses the standard SQLite table-rebuild instead: create the new shape under a
 * temporary name, copy every row across by explicit column list (never `SELECT *`, so a future
 * column reorder can't silently misalign the copy), drop the old table, then rename. Every other
 * column, and the [MushroomLogEntryEntity.offlineRegionId] index Workstream A added in
 * [MIGRATION_5_6], must survive this rebuild unchanged — the index is dropped along with the old
 * table (SQLite indices don't survive a table drop) and explicitly recreated on the new one below.
 */
val MIGRATION_6_7: Migration = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE `mushroom_log_entries_new` (
            `id` TEXT NOT NULL,
            `lat` REAL,
            `lng` REAL,
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
            `offlineRegionId` INTEGER,
            PRIMARY KEY(`id`))
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO `mushroom_log_entries_new` (
            `id`, `lat`, `lng`, `foundOn`, `entryNotes`, `ownIdentification`,
            `syncStateKind`, `syncProgress`, `syncRemoteObservationId`, `syncUploadedAtEpochMillis`, `syncFailureReason`,
            `capShape`, `capSurface`, `capDecorationsState`, `capDecorationsValue`, `capMargin`, `capNotes`,
            `hymenophoreKind`, `gillAttachment`, `gillSpacing`, `gillEdge`, `hymenophoreNotes`,
            `stipeKind`, `stipePosition`, `stipeInterior`, `stipeBase`, `stipeNotes`,
            `annulusState`, `annulusValue`, `volvaState`, `volvaValue`, `veilNotes`,
            `fleshTexture`, `colorChangeState`, `colorChangeValue`, `exudateState`, `exudateValue`, `contextFleshNotes`,
            `sporePrintColorKind`, `sporePrintOtherText`, `sporePrintReadOn`, `sporePrintNotes`,
            `associationKind`, `associationHostSpecies`, `associationOtherText`, `forestType`, `hostHealth`, `hostSubstrateNotes`,
            `offlineRegionId`
            )
            SELECT
            `id`, `lat`, `lng`, `foundOn`, `entryNotes`, `ownIdentification`,
            `syncStateKind`, `syncProgress`, `syncRemoteObservationId`, `syncUploadedAtEpochMillis`, `syncFailureReason`,
            `capShape`, `capSurface`, `capDecorationsState`, `capDecorationsValue`, `capMargin`, `capNotes`,
            `hymenophoreKind`, `gillAttachment`, `gillSpacing`, `gillEdge`, `hymenophoreNotes`,
            `stipeKind`, `stipePosition`, `stipeInterior`, `stipeBase`, `stipeNotes`,
            `annulusState`, `annulusValue`, `volvaState`, `volvaValue`, `veilNotes`,
            `fleshTexture`, `colorChangeState`, `colorChangeValue`, `exudateState`, `exudateValue`, `contextFleshNotes`,
            `sporePrintColorKind`, `sporePrintOtherText`, `sporePrintReadOn`, `sporePrintNotes`,
            `associationKind`, `associationHostSpecies`, `associationOtherText`, `forestType`, `hostHealth`, `hostSubstrateNotes`,
            `offlineRegionId`
            FROM `mushroom_log_entries`
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE `mushroom_log_entries`")
        db.execSQL("ALTER TABLE `mushroom_log_entries_new` RENAME TO `mushroom_log_entries`")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_mushroom_log_entries_offlineRegionId` " +
                "ON `mushroom_log_entries` (`offlineRegionId`)",
        )
    }
}
