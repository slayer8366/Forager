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

/**
 * Photo gallery ownership — owner decision, 2026-08-22: "photos live in a gallery in their own
 * right; log entries reference them, they do not own them." Before this migration, `log_photos`
 * carried a required `entryId` column — one row per photo, always exactly one owning entry, gone
 * (both the row and the file) the moment that entry was deleted ([MIGRATION_3_4]'s original shape).
 * That model is why L1 (photo file cleanup on entry delete) made sense at the time it landed — it
 * was the correct behavior for the model that then existed, not a bug this migration is fixing.
 *
 * `log_photos` loses `entryId` and gains `createdAtEpochMillis` (nullable — see
 * [com.forager.app.domain.model.LogPhoto]'s own doc comment for why a migrated row's creation time
 * is left an honest unknown rather than fabricated). A new join table,
 * [LogEntryPhotoCrossRef]'s `log_entry_photos`, carries the entry↔photo relationship instead —
 * **many-to-many** (owner decision: "the album model," matching how photo apps behave, and
 * avoiding a second migration over the same tables to get there later), not one-to-many. Every
 * pre-existing `log_photos` row's `(id, entryId)` pair is preserved as a `log_entry_photos` row
 * before the old table is dropped, so no existing entry↔photo relationship is lost — only the
 * *ownership* direction inverts, not the data.
 *
 * `log_photos` gets a full SQLite table rebuild (create the new shape, copy by explicit column
 * list, drop the old table, rename) rather than `ALTER TABLE ... DROP COLUMN` — this project's own
 * standing practice already favors the rebuild shape uniformly (L3's `MIGRATION_6_7` used it for a
 * nullability change `DROP COLUMN` couldn't have expressed at all), and `DROP COLUMN` itself needs
 * SQLite 3.35.0+, unverified against every Android SQLite build this app must run on. Every
 * pre-existing entry<->photo relationship is captured into `log_entry_photos` while the old
 * `log_photos` (the only place that relationship was recorded) is still intact, before it's
 * dropped. `mushroom_log_entries` itself is untouched by this migration — only `log_photos` changes
 * shape; `log_entry_photos` is a brand-new table, the same "just `CREATE TABLE`" shape
 * [MIGRATION_5_6]'s `offline_regions` used.
 *
 * **The legacy-fixture problem, third occurrence — recurred, in the opposite direction, and L3's
 * narrowed rule missed it.** L3 narrowed the rule from "any migration that alters an existing
 * entity" to specifically `ALTER TABLE ... ADD COLUMN` against an entity a legacy test fixture
 * already declares (see `docs/audits/2026-08-24-migration-fixture-entity-reuse-pitfall.md`) — this
 * migration contains no `ALTER TABLE ... ADD COLUMN` at all, so that narrowed rule predicted no
 * recurrence. **Checked anyway, not trusted on the rule's say-so — and the check found a real
 * failure the rule didn't predict.** [LogPhotoEntity] no longer declares `entryId` on the shared
 * production class as of this migration, so every legacy fixture that reuses it (`LegacyForagerDatabaseV4`/
 * `V5`/`V6`/`V7`, one per existing migration test plus `LogPhotoMigrationTest`'s own) builds
 * `log_photos` *without* that column — a column a real install at any of those versions always
 * had. This migration's own `SELECT entryId, id FROM log_photos` then fails at runtime with
 * `no such column: entryId` against every one of them. Not a duplicate-column failure (the
 * `ADD COLUMN` shape L3's rule covers) — a *missing*-column failure, the mirror image, caused by a
 * column *leaving* the shared class rather than joining it. Fixed the same way as the original
 * `offlineRegionId` case, just inverted: each legacy fixture restores `log_photos.entryId` with a
 * plain `ALTER TABLE ... ADD COLUMN` (no `DROP INDEX` needed first this time — nothing leaked in to
 * conflict with) before being handed to the real migration chain. `log_entry_photos` needed no such
 * correction: this migration's own `CREATE TABLE IF NOT EXISTS` already tolerates every fixture
 * having built that table too early (via the same `LogEntryPhotoCrossRef` reuse, needed purely so
 * `MushroomLogDao`'s cross-reference methods compile against each fixture's `@Database` at all).
 * The takeaway for the next migration that hits this: narrow rules about *when* the legacy-fixture
 * problem recurs are worth having, but don't skip running the fixtures because a rule says a
 * particular shape of migration shouldn't need it.
 */
val MIGRATION_7_8: Migration = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE `log_photos_new` (
            `id` TEXT NOT NULL,
            `relativePath` TEXT NOT NULL,
            `createdAtEpochMillis` INTEGER,
            PRIMARY KEY(`id`))
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO `log_photos_new` (`id`, `relativePath`, `createdAtEpochMillis`)
            SELECT `id`, `relativePath`, NULL FROM `log_photos`
            """.trimIndent(),
        )

        // Preserve every existing entry<->photo relationship as a cross-reference row before the
        // old log_photos (the only place that relationship is currently recorded) is dropped.
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `log_entry_photos` (
            `entryId` TEXT NOT NULL,
            `photoId` TEXT NOT NULL,
            PRIMARY KEY(`entryId`, `photoId`))
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO `log_entry_photos` (`entryId`, `photoId`)
            SELECT `entryId`, `id` FROM `log_photos`
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_log_entry_photos_photoId` ON `log_entry_photos` (`photoId`)",
        )

        db.execSQL("DROP TABLE `log_photos`")
        db.execSQL("ALTER TABLE `log_photos_new` RENAME TO `log_photos`")
    }
}

/**
 * Persisted drafts — owner decision, 2026-08-22 (Workstream L4b), **corrected 2026-08-25 (L4b-R)**:
 * a draft is a **standalone row**, not the committed entry itself wearing a flag. The first pass
 * shipped a single discriminator column and flipped the *same* row between draft and committed —
 * this migration replaces that with `isDraft` **plus** a nullable [MushroomLogEntryEntity.draftOfEntryId]
 * pointer. A brand-new entry's draft has no parent (`draftOfEntryId = null`) and becomes the
 * committed row in place on Save. A re-edit's draft is a *second* row, `draftOfEntryId` pointing at
 * the untouched committed row, so the committed entry keeps showing its last-saved values in the
 * log for the entire time it's being edited — see [MushroomLogViewModel]'s own doc comment for the
 * full state machine this drives, and the L4b-R dispatch (2026-08-25) for why the single-row shape
 * was rejected on its consequences, not just its merits: it let an interrupted edit overwrite a
 * committed entry in place.
 *
 * Nothing shipped between the first pass and this correction (this migration was never released),
 * so this amends `MIGRATION_8_9`/version 9 directly rather than stacking a 9→10 on top of a version
 * that never existed in the wild. Every pre-existing row is a real, previously-committed entry —
 * none of them were ever a draft under the pre-L4b (autosave-always-commits) model — so every row
 * this migration copies gets `isDraft = 0` and `draftOfEntryId = NULL` explicitly, never left to an
 * implicit default. No committed entry is lost or marked draft by this migration.
 *
 * A full SQLite table rebuild (create the new shape, copy by explicit column list, drop, rename),
 * not `ALTER TABLE ... ADD COLUMN` — this project's standing practice for `mushroom_log_entries`
 * (`MIGRATION_6_7` used it for a nullability change; see that migration's own doc comment for why
 * `ADD COLUMN` alone isn't trusted here even where SQLite would allow it).
 *
 * **The legacy-fixture problem, checked rather than assumed.** [MushroomLogEntryEntity] gains
 * `isDraft`/`draftOfEntryId` here, the same *kind* of change that broke `MIGRATION_5_6` against
 * `LegacyForagerDatabaseV4`/`V5` (see `docs/audits/2026-08-24-migration-fixture-entity-reuse-pitfall.md`)
 * — every `LegacyForagerDatabaseVn` fixture that declares [MushroomLogEntryEntity] (`V4` through
 * `V8`) now bakes in both columns too, at a version that never had them. Unlike the `offlineRegionId`
 * case, this does **not** need the drop-index-then-drop-column treatment: that fix was necessary
 * because `MIGRATION_5_6` uses `ALTER TABLE ... ADD COLUMN`, which fails outright against a column
 * already present. This migration follows the *rebuild* pattern instead — its `INSERT ... SELECT`
 * names an explicit column list that never mentions either leaked column on the source side, so
 * whatever a fixture's table already has is silently ignored, not conflicted with. More concretely:
 * every fixture older than version 7 passes through `MIGRATION_6_7`'s own rebuild (a stricter
 * explicit-column-list `CREATE`) before ever reaching this one, which drops any such leaked columns
 * along the way regardless of this migration; `LegacyForagerDatabaseV7`/`V8` reach this migration
 * directly (only `MIGRATION_7_8`, which never touches `mushroom_log_entries`, sits between V7 and
 * here) but are equally unaffected, for the same "explicit list, ignores extras" reason. Verified by
 * running every `LegacyForagerDatabaseVn` migration test with this migration appended to its chain,
 * not assumed from the reasoning above — see this migration's own test coverage for confirmation
 * none needed the treatment.
 */
val MIGRATION_8_9: Migration = object : Migration(8, 9) {
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
            `isDraft` INTEGER NOT NULL,
            `draftOfEntryId` TEXT,
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
            `offlineRegionId`, `isDraft`, `draftOfEntryId`
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
            `offlineRegionId`, 0, NULL
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

/**
 * Journal Stage 2a (derived-trip data layer): indexes supporting a day-scoped lookup on each of the
 * four entities a derived trip gathers — [MushroomLogEntryEntity.foundOn],
 * [TrackEntity.startedAtEpochMillis]/[TrackEntity.endedAtEpochMillis], [WaypointEntity.createdAtEpochMillis],
 * and [OfflineRegionEntity.createdAtEpochMillis]. Owner decision #2 for this dispatch: "indexed
 * queries, not in-memory filtering" — every existing query in this database reads a whole table
 * unconditionally (see [MushroomLogDao]/[TrackDao]/[WaypointDao]/[OfflineRegionDao]'s own doc
 * comments), so this is the first migration whose entire purpose is making a `WHERE` clause cheap
 * rather than adding a table or column.
 *
 * **No `ALTER TABLE`, no table rebuild — pure `CREATE INDEX IF NOT EXISTS`.** Unlike every migration
 * above, this changes no column's shape or nullability, so none of the rebuild machinery
 * [MIGRATION_6_7]/[MIGRATION_7_8]/[MIGRATION_8_9] needed applies here, and none of the
 * `ALTER TABLE ... ADD COLUMN` failure mode [MIGRATION_5_6] hit against a legacy fixture applies
 * either — `CREATE INDEX IF NOT EXISTS` is a genuine no-op against an index that's already there,
 * which is exactly the shape a legacy `LegacyForagerDatabaseVn` fixture produces when it shares
 * these entity classes (now carrying the same `@Index` annotations) at an old declared version: see
 * `DayScopedIndexMigrationTest`'s own `LegacyForagerDatabaseV9` fixture, run through this migration
 * to confirm that directly rather than assumed from this reasoning.
 *
 * Index names match Room's own generated convention (`index_<table>_<column>`) — see [MIGRATION_4_5]'s
 * doc comment for why that matters: Room's schema validation at app startup checks the on-disk index
 * name against what the `@Index` annotation declares, not just that *an* index covers the column.
 */
val MIGRATION_9_10: Migration = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_mushroom_log_entries_foundOn` " +
                "ON `mushroom_log_entries` (`foundOn`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_tracks_startedAtEpochMillis` " +
                "ON `tracks` (`startedAtEpochMillis`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_tracks_endedAtEpochMillis` " +
                "ON `tracks` (`endedAtEpochMillis`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_waypoints_createdAtEpochMillis` " +
                "ON `waypoints` (`createdAtEpochMillis`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_offline_regions_createdAtEpochMillis` " +
                "ON `offline_regions` (`createdAtEpochMillis`)",
        )
    }
}

/**
 * Journal Stage 2b: adds `cartography_entries` and its five ref tables ([CartographyEntryEntity],
 * [CartographyEntryTrackRefEntity], [CartographyEntryWaypointRefEntity],
 * [CartographyEntryOfflineRegionRefEntity], [CartographyEntryFindRefEntity],
 * [CartographyEntryPhotoRefEntity]) — a brand-new entity family, not a change to any existing table,
 * per `amendment-2b-entry-definition.md`: a Cartography entry is authored, not derived, and
 * [com.forager.app.domain.model.MushroomLogEntry] stays untouched.
 *
 * Amended twice after the initial six-table shape, both times in place rather than as a second
 * migration, since nothing built on this one had shipped yet either time (same precedent
 * [MIGRATION_8_9]'s own doc comment sets for amending an unreleased migration directly):
 * - The photo-ref table, per `amendment-2b-optional-writing.md`'s "photo attachment is load-bearing."
 * - `kept: Boolean` on the four trip-report-derived ref tables (track/waypoint/offline-region/find —
 *   not photo, which has no candidate pool to be undecided about), per the Stage 2b follow-up
 *   dispatch's point 2: withholding needed to become a persisted, revisitable decision, not an
 *   in-memory-only default that a reopen couldn't tell apart from "never considered." Row presence
 *   still means "decided one way or the other"; [kept] carries which way.
 *
 * A real, hand-written migration, not `fallbackToDestructiveMigration()` (release path) — a written
 * entry is exactly as irreplaceable as a log entry or a recorded track, the same reasoning
 * [MIGRATION_3_4]/[MIGRATION_4_5] give.
 *
 * **Pure `CREATE TABLE`, no rebuild** — every one of these six tables is new, so there is nothing to
 * copy forward and none of the rebuild machinery [MIGRATION_6_7]/[MIGRATION_7_8]/[MIGRATION_8_9]
 * needed applies here — the same shape [MIGRATION_5_6]'s `offline_regions` table used. **Zero
 * `@ForeignKey` anywhere**, per this database's standing rule (see [MushroomLogEntryEntity.offlineRegionId]'s
 * own doc comment) — every kept-item ref column is a plain, unconstrained, indexed id.
 */
val MIGRATION_10_11: Migration = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `cartography_entries` (
            `id` TEXT NOT NULL,
            `date` TEXT NOT NULL,
            `text` TEXT NOT NULL,
            `tags` TEXT NOT NULL,
            `isDraft` INTEGER NOT NULL,
            `updatedAtEpochMillis` INTEGER NOT NULL,
            PRIMARY KEY(`id`))
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cartography_entries_date` ON `cartography_entries` (`date`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cartography_entries_isDraft` ON `cartography_entries` (`isDraft`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `cartography_entry_track_refs` (
            `entryId` TEXT NOT NULL,
            `trackId` TEXT NOT NULL,
            `name` TEXT,
            `distanceMeters` REAL NOT NULL,
            `durationMillis` INTEGER NOT NULL,
            `pointCount` INTEGER NOT NULL,
            `kept` INTEGER NOT NULL,
            PRIMARY KEY(`entryId`, `trackId`))
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_cartography_entry_track_refs_trackId` " +
                "ON `cartography_entry_track_refs` (`trackId`)",
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `cartography_entry_waypoint_refs` (
            `entryId` TEXT NOT NULL,
            `waypointId` TEXT NOT NULL,
            `name` TEXT NOT NULL,
            `lat` REAL NOT NULL,
            `lng` REAL NOT NULL,
            `kept` INTEGER NOT NULL,
            PRIMARY KEY(`entryId`, `waypointId`))
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_cartography_entry_waypoint_refs_waypointId` " +
                "ON `cartography_entry_waypoint_refs` (`waypointId`)",
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `cartography_entry_offline_region_refs` (
            `entryId` TEXT NOT NULL,
            `offlineRegionId` INTEGER NOT NULL,
            `name` TEXT NOT NULL,
            `lat` REAL NOT NULL,
            `lng` REAL NOT NULL,
            `radiusKm` INTEGER NOT NULL,
            `kept` INTEGER NOT NULL,
            PRIMARY KEY(`entryId`, `offlineRegionId`))
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_cartography_entry_offline_region_refs_offlineRegionId` " +
                "ON `cartography_entry_offline_region_refs` (`offlineRegionId`)",
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `cartography_entry_find_refs` (
            `entryId` TEXT NOT NULL,
            `findId` TEXT NOT NULL,
            `foundOn` TEXT NOT NULL,
            `ownIdentification` TEXT,
            `hasPhotos` INTEGER NOT NULL,
            `kept` INTEGER NOT NULL,
            PRIMARY KEY(`entryId`, `findId`))
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_cartography_entry_find_refs_findId` " +
                "ON `cartography_entry_find_refs` (`findId`)",
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `cartography_entry_photo_refs` (
            `entryId` TEXT NOT NULL,
            `photoId` TEXT NOT NULL,
            `attachedAtEpochMillis` INTEGER NOT NULL,
            PRIMARY KEY(`entryId`, `photoId`))
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_cartography_entry_photo_refs_photoId` " +
                "ON `cartography_entry_photo_refs` (`photoId`)",
        )
    }
}
