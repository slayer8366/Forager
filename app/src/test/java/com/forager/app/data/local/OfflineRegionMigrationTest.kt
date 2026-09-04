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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The 5→6 counterpart to [MushroomLogMigrationTest]/[TrackWaypointMigrationTest] — see
 * [MIGRATION_5_6]'s own doc comment for why this ships a real migration rather than
 * `fallbackToDestructiveMigration()`.
 *
 * Builds a real version-5 database (via [LegacyForagerDatabaseV5], sharing the same entity/DAO
 * classes production version 5 used), seeds a planned trip and a mushroom-log entry through the
 * real repository so the seeded row is exactly what production code would have written, reopens
 * it as [ForagerDatabase] with [MIGRATION_5_6] applied, and asserts the pre-existing rows survived
 * intact, the new `offline_regions` table is actually usable, and the new
 * [MushroomLogEntryEntity.offlineRegionId] column exists and reads back `null` for a row that
 * predates it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OfflineRegionMigrationTest {

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
    fun `planned trips and mushroom log entries survive the 5 to 6 migration intact, offlineRegionId is null for the pre-existing entry, and offline_regions is usable`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>()

        val legacyTrip = PlannedTripEntity(id = "trip-1", name = "Chanterelle Ridge", lat = 45.51, lng = -122.66, date = "2026-09-01")
        val legacyEntry = MushroomLogEntry.draft(id = "entry-1", location = LatLng(45.5, -122.6), date = LocalDate.of(2026, 8, 1))

        val legacyDb = Room.databaseBuilder(context, LegacyForagerDatabaseV5::class.java, dbFile.absolutePath).build()
        try {
            legacyDb.plannedTripDao().upsert(legacyTrip)
            RoomMushroomLogRepository(legacyDb.mushroomLogDao()).save(legacyEntry).getOrThrow()

            // LegacyForagerDatabaseV5 reuses the *production* MushroomLogEntryEntity class (per
            // this file's own doc comment on why: never a hand-copied schema) — but that class now
            // has offlineRegionId (plus its index), a column real version-5 installs never had, so
            // Room creates this "legacy" table with both already present. Drop them here to restore
            // the table to its true pre-MIGRATION_5_6 shape before handing the file to the real
            // migration path; otherwise MIGRATION_5_6's own `ALTER TABLE ADD COLUMN` fails with
            // "duplicate column" against a database that was never actually missing it. The index
            // must go first — SQLite validates it against the column set on every subsequent
            // statement, so dropping the column while the index still references it fails with
            // "no such column" instead. This is not leftover debugging — see
            // docs/audits/2026-08-24-migration-fixture-entity-reuse-pitfall.md for why any future
            // migration that alters an existing entity will need the same treatment here.
            legacyDb.openHelper.writableDatabase.execSQL("DROP INDEX `index_mushroom_log_entries_offlineRegionId`")
            legacyDb.openHelper.writableDatabase.execSQL("ALTER TABLE `mushroom_log_entries` DROP COLUMN `offlineRegionId`")

            // The same pattern's third occurrence, in the opposite direction: LogPhotoEntity no
            // longer declares entryId as of MIGRATION_7_8 (gallery ownership), so this "legacy"
            // log_photos table is missing a column a real version-5 install always had — restored
            // here (a plain ADD COLUMN, not a drop, since nothing leaked in this time; something
            // leaked away) so MIGRATION_7_8's own `SELECT entryId, id FROM log_photos` doesn't fail
            // with "no such column" further down the same migration chain. See LogPhotoMigrationTest's
            // own doc comment for the general reasoning.
            legacyDb.openHelper.writableDatabase.execSQL("ALTER TABLE `log_photos` ADD COLUMN `entryId` TEXT")
        } finally {
            legacyDb.close()
        }

        // Reopen the same file as the real, current ForagerDatabase — no
        // fallbackToDestructiveMigration here, so if MIGRATION_5_6 is missing or wrong, this throws
        // rather than silently wiping the file.
        val migrated = Room.databaseBuilder(context, ForagerDatabase::class.java, dbFile.absolutePath)
            .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
            .build()

        try {
            val survivedTrips = migrated.plannedTripDao().getAll()
            assertEquals(listOf(legacyTrip), survivedTrips)

            val survivedEntries = RoomMushroomLogRepository(migrated.mushroomLogDao()).getAll().getOrThrow()
            // Workstream L4b: MIGRATION_8_9 marks every pre-existing row isDraft = false — see
            // MushroomLogEntryMigrationTest's identical note for why legacyEntry.isDraft (true,
            // MushroomLogEntry.draft()'s current default) isn't what a migrated row should carry.
            assertEquals(listOf(legacyEntry.copy(isDraft = false)), survivedEntries)

            // offlineRegionId is new in this version — a pre-migration row must read back null,
            // not some default the ALTER TABLE happened to leave behind. Read via the raw entity,
            // since the domain model (MushroomLogEntry) deliberately doesn't expose this column
            // yet — that wiring is Workstream B's, not this one's.
            val migratedEntryEntity = migrated.mushroomLogDao().getAllEntries().single { it.id == legacyEntry.id }
            assertNull(migratedEntryEntity.offlineRegionId)

            // offline_regions isn't just present after migration — it actually works: a real
            // save/read round-trip through the production DAO. No repository wraps it yet
            // (Workstream B's job), so the DAO itself is what "the production DAO" means here.
            val offlineRegionDao = migrated.offlineRegionDao()
            assertTrue("offline_regions should start empty after migrating a v5 database", offlineRegionDao.getAll().isEmpty())

            val region = OfflineRegionEntity(
                id = 1L,
                name = "Post-migration region",
                lat = 45.0,
                lng = -122.0,
                radiusKm = 15,
                minZoom = 10.0,
                maxZoom = 14.0,
                createdAtEpochMillis = 4_000L,
            )
            offlineRegionDao.upsert(region)
            assertEquals(region, offlineRegionDao.getById(region.id))
            assertEquals(false, offlineRegionDao.getById(region.id)?.isEntryCapture)
        } finally {
            migrated.close()
        }
    }

    private companion object {
        const val TEST_DB_NAME = "offline-region-migration-test.db"
    }
}

/**
 * Version 5 of [ForagerDatabase], reconstructed for this test from the *same* entity and DAO
 * classes production code used at that version — none of them are copied, so this can't drift from
 * what a real version-5 install actually wrote. `exportSchema = false` here matches how version 5
 * was captured for this test, the same as [TrackWaypointMigrationTest]'s `LegacyForagerDatabaseV4`.
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
    ],
    version = 5,
    exportSchema = false,
)
internal abstract class LegacyForagerDatabaseV5 : RoomDatabase() {
    abstract fun plannedTripDao(): PlannedTripDao
    abstract fun cachedSearchDao(): CachedSearchDao
    abstract fun mushroomLogDao(): MushroomLogDao
}
