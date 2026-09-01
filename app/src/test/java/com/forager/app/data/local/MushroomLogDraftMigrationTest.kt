package com.forager.app.data.local

import android.app.Application
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import com.forager.app.data.repository.RoomMushroomLogRepository
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.MushroomLogEntry
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The 8→9 counterpart to [MushroomLogEntryMigrationTest]/[LogPhotoMigrationTest] — Workstream L4b,
 * owner decision 2026-08-22: persisted drafts. See [MIGRATION_8_9]'s own doc comment for why this
 * ships a real migration (a table rebuild) rather than `fallbackToDestructiveMigration()`, and for
 * why the legacy-fixture pitfall (`docs/audits/2026-08-24-migration-fixture-entity-reuse-pitfall.md`)
 * does not recur here even though [MushroomLogEntryEntity] gains a `NOT NULL` column.
 *
 * Builds a real version-8 database (via [LegacyForagerDatabaseV8], sharing the same entity/DAO
 * classes production version 8 used), seeds an entry through the real repository, reopens it as
 * [ForagerDatabase] with [MIGRATION_8_9] applied, and asserts: the entry survives with every field
 * intact and `isDraft = false` — the gate this migration exists to satisfy, "no committed entry is
 * lost or marked draft."
 *
 * The seeded entry is deliberately saved with `isDraft = true` *and* a non-null `draftOfEntryId` —
 * not because a real version-8 install could ever have written either (neither column existed until
 * this migration and the one after it — see [MushroomLogEntryEntity.draftOfEntryId]'s own doc
 * comment for when it was added, L4b-R), but because [LegacyForagerDatabaseV8] reuses the *current*
 * [MushroomLogEntryEntity], which already declares both, so this fixture's `mushroom_log_entries`
 * table has both columns from creation, unlike a real version-8 file. This proves [MIGRATION_8_9]
 * ignores whatever those leaked columns hold and forces every row to `isDraft = false`/
 * `draftOfEntryId = null` regardless — the correct behavior, since every real version-8 row is a
 * committed entry with no draft relationship by construction (the old codebase had neither concept)
 * — rather than merely happening to pass in the common case where the leaked values are already
 * `false`/`null`. This is the third time this exact leaked-column shape has bitten this migration
 * (`isDraft` here, now `draftOfEntryId` too) — see `docs/audits/2026-08-24-migration-fixture-entity-reuse-pitfall.md`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MushroomLogDraftMigrationTest {

    private lateinit var dbFile: File

    @Before
    fun setUp() {
        dbFile = ApplicationProvider.getApplicationContext<Application>().getDatabasePath(TEST_DB_NAME)
        dbFile.delete()
    }

    @After
    fun tearDown() {
        dbFile.delete()
    }

    @Test
    fun `a pre-existing entry survives the 8 to 9 migration intact and is marked committed, never draft`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>()

        val legacyEntry = MushroomLogEntry.draft(id = "entry-1", location = LatLng(45.5, -122.6), date = LocalDate.of(2026, 8, 1))
            .copy(notes = "found under the big fir", isDraft = true, draftOfEntryId = "leaked-parent-id")

        val legacyDb = Room.databaseBuilder(context, LegacyForagerDatabaseV8::class.java, dbFile.absolutePath).build()
        try {
            RoomMushroomLogRepository(legacyDb.mushroomLogDao()).save(legacyEntry).getOrThrow()
        } finally {
            legacyDb.close()
        }

        // Reopen the same file as the real, current ForagerDatabase — no
        // fallbackToDestructiveMigration here, so if MIGRATION_8_9 is missing or wrong, this throws
        // rather than silently wiping the file.
        val migrated = Room.databaseBuilder(context, ForagerDatabase::class.java, dbFile.absolutePath)
            .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
            .build()

        try {
            val repository = RoomMushroomLogRepository(migrated.mushroomLogDao())
            val survivedEntries = repository.getAll().getOrThrow()

            assertEquals(listOf(legacyEntry.copy(isDraft = false, draftOfEntryId = null)), survivedEntries)
            assertFalse(
                "no committed entry may be marked draft by this migration, regardless of what a leaked isDraft column held",
                survivedEntries.single().isDraft,
            )
            assertEquals(
                "no committed entry may keep a parent pointer, regardless of what a leaked draftOfEntryId column held",
                null,
                survivedEntries.single().draftOfEntryId,
            )

            // A freshly-created entry post-migration is a draft, as normal — proves the new column
            // is actually load-bearing (queryable, not just present) after the rebuild, the same
            // "new tables/columns actually work" bar every other migration test in this package
            // holds itself to.
            val newDraft = MushroomLogEntry.draft(id = "post-migration-draft", location = LatLng(46.0, -123.0), date = LocalDate.of(2026, 8, 20))
            repository.save(newDraft).getOrThrow()
            val reloadedDraft = repository.getAll().getOrThrow().single { it.id == newDraft.id }
            assertEquals(newDraft, reloadedDraft)
        } finally {
            migrated.close()
        }
    }

    private companion object {
        const val TEST_DB_NAME = "mushroom-log-draft-migration-test.db"
    }
}

/**
 * Version 8 of [ForagerDatabase], reconstructed for this test from the *same* entity and DAO
 * classes production code used at that version — none of them are copied, so this can't drift from
 * what a real version-8 install actually wrote. `exportSchema = false` here matches every other
 * `LegacyForagerDatabaseVn` fixture in this package.
 *
 * **Not a faithful copy of the true version-8 `mushroom_log_entries` shape.** [MushroomLogEntryEntity]
 * already declares `isDraft` *and* `draftOfEntryId` on the shared production class as of this
 * migration, so Room builds this "legacy" table with both columns already present — a real
 * version-8 install never had either. This does **not** need the drop-index-then-drop-column
 * treatment (`docs/audits/2026-08-24-migration-fixture-entity-reuse-pitfall.md`): that fix exists
 * because `MIGRATION_5_6`'s `ALTER TABLE ... ADD COLUMN` fails outright against a column already
 * present. [MIGRATION_8_9] is a table *rebuild* whose `INSERT ... SELECT` names an explicit column
 * list that never mentions `isDraft`/`draftOfEntryId` on the source side, so both leaked columns
 * here are silently ignored, not conflicted with — confirmed by this test actually passing, not
 * assumed from that reasoning alone.
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
    version = 8,
    exportSchema = false,
)
internal abstract class LegacyForagerDatabaseV8 : RoomDatabase() {
    abstract fun mushroomLogDao(): MushroomLogDao
}
