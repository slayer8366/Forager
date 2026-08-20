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
 * Adds `offline_regions` ([OfflineRegionEntity]) on top of version 4's mushroom log tables — the
 * index the "Region management" design doc describes: `OfflineManager` owns the actual downloaded
 * tiles in its own database, this table is only what this app adds on top (name, centre, radius,
 * zoom range, created timestamp) so multiple downloaded regions can be listed and queried, which a
 * single opaque `OfflineManager` region list can't do on its own.
 *
 * A real, hand-written migration for the same reason [MIGRATION_3_4] is one rather than
 * `fallbackToDestructiveMigration()`: by the time this ships, a device may already hold real
 * mushroom-log field notes from [MIGRATION_3_4], and a destructive fallback would drop those to add
 * an unrelated table. Covered by `OfflineRegionMigrationTest`, mirroring
 * `MushroomLogMigrationTest`'s "build a real prior-version database, migrate it, assert survival"
 * shape.
 */
val MIGRATION_4_5: Migration = object : Migration(4, 5) {
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
            PRIMARY KEY(`id`))
            """.trimIndent(),
        )
    }
}
