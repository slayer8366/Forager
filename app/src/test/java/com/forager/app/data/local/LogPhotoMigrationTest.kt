package com.forager.app.data.local

import android.app.Application
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import com.forager.app.data.repository.RoomMushroomLogRepository
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.LogPhoto
import com.forager.app.domain.model.MushroomLogEntry
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The 7→8 counterpart to [MushroomLogEntryMigrationTest] — gallery ownership (owner decision,
 * 2026-08-22). See [MIGRATION_7_8]'s own doc comment for why this is a table rebuild
 * (`log_photos` loses `entryId`, gains `createdAtEpochMillis`) plus one brand-new table
 * (`log_entry_photos`), and for the legacy-fixture problem's third occurrence.
 *
 * Builds a real version-7 database (via [LegacyForagerDatabaseV7], sharing the same
 * entity/DAO classes production version 7 used) with the true pre-migration `log_photos` shape
 * restored — see that fixture's own doc comment for why Room can't build it faithfully from the
 * shared entity classes any more — seeds an entry through the real repository and a photo
 * referencing it via the true v7 shape, reopens it as [ForagerDatabase] with [MIGRATION_7_8]
 * applied, and asserts: the existing relationship survives as a cross-reference row; the migrated
 * photo's `createdAtEpochMillis` reads back `null` (unknown, not fabricated) for a row that
 * predates that column; and a brand-new photo — shared between two entries, only representable
 * post-migration — round-trips through the real repository.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LogPhotoMigrationTest {

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
    fun `an existing entry-photo relationship survives as a cross-reference, and a new photo can be shared between two entries`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>()

        val legacyEntry = MushroomLogEntry.draft(id = "entry-1", location = LatLng(45.5, -122.6), date = LocalDate.of(2026, 8, 1))
        val secondEntry = MushroomLogEntry.draft(id = "entry-2", location = LatLng(45.6, -122.5), date = LocalDate.of(2026, 8, 2))

        val legacyDb = Room.databaseBuilder(context, LegacyForagerDatabaseV7::class.java, dbFile.absolutePath).build()
        try {
            val legacyRepository = RoomMushroomLogRepository(legacyDb.mushroomLogDao())
            legacyRepository.save(legacyEntry).getOrThrow()
            legacyRepository.save(secondEntry).getOrThrow()

            // LegacyForagerDatabaseV7 reuses the *production* LogPhotoEntity class — but that
            // class no longer has entryId as of this migration, so Room builds this "legacy"
            // log_photos table without it, a real version-7 install always had. Restore it before
            // seeding a photo row directly (there is no repository method left that understands
            // entryId on log_photos — that shape only ever existed pre-migration) — see
            // MIGRATION_7_8's own doc comment for the general pattern this follows, and
            // docs/audits/2026-08-24-migration-fixture-entity-reuse-pitfall.md for why any future
            // migration that alters an existing entity needs the same treatment here. No DROP
            // needed first this time (unlike the offlineRegionId case) — there's no leaked index
            // referencing entryId to conflict with adding it back.
            legacyDb.openHelper.writableDatabase.execSQL("ALTER TABLE `log_photos` ADD COLUMN `entryId` TEXT")
            legacyDb.openHelper.writableDatabase.execSQL(
                "INSERT INTO `log_photos` (`id`, `relativePath`, `entryId`) VALUES ('legacy-photo', 'photos/legacy-photo.jpg', 'entry-1')",
            )
        } finally {
            legacyDb.close()
        }

        // Reopen the same file as the real, current ForagerDatabase — no
        // fallbackToDestructiveMigration here, so if MIGRATION_7_8 is missing or wrong, this
        // throws rather than silently wiping the file.
        val migrated = Room.databaseBuilder(context, ForagerDatabase::class.java, dbFile.absolutePath)
            .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
            .build()

        try {
            val repository = RoomMushroomLogRepository(migrated.mushroomLogDao())
            val survivedEntries = repository.getAll().getOrThrow().associateBy { it.id }
            assertEquals(1, survivedEntries.getValue("entry-1").photos.size)
            val migratedPhoto = survivedEntries.getValue("entry-1").photos.single()
            assertEquals("legacy-photo", migratedPhoto.id)
            assertNull("a migrated photo's creation time is unknown, never fabricated", migratedPhoto.createdAtEpochMillis)
            assertEquals(emptyList<LogPhoto>(), survivedEntries.getValue("entry-2").photos)

            // The migration this file exists to prove: a brand-new photo, shared between two
            // entries — only representable post-migration, since log_photos.entryId made this
            // impossible before — round-trips through the real repository for both.
            val sharedPhoto = LogPhoto(id = "shared-post-migration", relativePath = "photos/shared.jpg", createdAtEpochMillis = 5_000L)
            repository.addPhotoToGallery(sharedPhoto).getOrThrow()
            repository.attachPhotoToEntry("entry-1", sharedPhoto.id).getOrThrow()
            repository.attachPhotoToEntry("entry-2", sharedPhoto.id).getOrThrow()

            val afterSharing = repository.getAll().getOrThrow().associateBy { it.id }
            assertEquals(setOf(migratedPhoto, sharedPhoto), afterSharing.getValue("entry-1").photos.toSet())
            assertEquals(listOf(sharedPhoto), afterSharing.getValue("entry-2").photos)
        } finally {
            migrated.close()
        }
    }

    private companion object {
        const val TEST_DB_NAME = "log-photo-migration-test.db"
    }
}

/**
 * Version 7 of [ForagerDatabase], reconstructed for this test from the *same* entity and DAO
 * classes production code used at that version — none of them are copied, so this can't drift from
 * what a real version-7 install actually wrote. `exportSchema = false` here matches every other
 * `LegacyForagerDatabaseVn` fixture in this package.
 *
 * **Not a faithful copy of the true version-7 `log_photos` shape.** [LogPhotoEntity] no longer
 * declares `entryId` on the shared production class as of this migration, so Room builds this
 * "legacy" table without the column a real version-7 install always had — restored with a plain
 * `ALTER TABLE ... ADD COLUMN` in the test body itself before seeding, right before the real
 * migration path runs. `log_entry_photos` needs no such correction: it's a brand-new table in this
 * migration, and [MIGRATION_7_8]'s own `CREATE TABLE IF NOT EXISTS` is written to tolerate exactly
 * this fixture already having created it early via [LogEntryPhotoCrossRef] (needed here purely so
 * [MushroomLogDao]'s own cross-reference methods compile against this `@Database` — see that DAO's
 * doc comment).
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
    version = 7,
    exportSchema = false,
)
internal abstract class LegacyForagerDatabaseV7 : RoomDatabase() {
    abstract fun mushroomLogDao(): MushroomLogDao
}
