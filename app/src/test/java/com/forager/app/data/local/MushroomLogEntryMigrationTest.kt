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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The 6→7 counterpart to [OfflineRegionMigrationTest] — Workstream L3
 * (`docs/plans/pr26-rework.md`): [MushroomLogEntryEntity.lat]/`.lng` become nullable, so a log
 * entry can exist with no location at all. See [MIGRATION_6_7]'s own doc comment for why this
 * ships a real migration (a SQLite table rebuild) rather than `fallbackToDestructiveMigration()`.
 *
 * Builds a real version-6 database (via [LegacyForagerDatabaseV6], sharing the same entity/DAO
 * classes production version 6 used), seeds a planned trip and a mushroom-log entry with a real
 * location through the real repository, reopens it as [ForagerDatabase] with [MIGRATION_6_7]
 * applied, and asserts: the pre-existing entry keeps its coordinates; a brand-new entry saved
 * post-migration with no location round-trips as `null`, not a fabricated `(0.0, 0.0)` or a thrown
 * exception; and [MushroomLogEntryEntity.offlineRegionId]'s index (Workstream A) still works after
 * the rebuild.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MushroomLogEntryMigrationTest {

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
    fun `a pre-existing entry keeps its coordinates, and a new entry saved with no location round-trips as null`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>()

        val legacyTrip = PlannedTripEntity(id = "trip-1", name = "Chanterelle Ridge", lat = 45.51, lng = -122.66, date = "2026-09-01")
        val legacyEntry = MushroomLogEntry.draft(id = "entry-1", location = LatLng(45.5, -122.6), date = LocalDate.of(2026, 8, 1))

        val legacyDb = Room.databaseBuilder(context, LegacyForagerDatabaseV6::class.java, dbFile.absolutePath).build()
        try {
            legacyDb.plannedTripDao().upsert(legacyTrip)
            RoomMushroomLogRepository(legacyDb.mushroomLogDao()).save(legacyEntry).getOrThrow()

            // The legacy-fixture problem's third occurrence, in the opposite direction from the
            // offlineRegionId case: LogPhotoEntity no longer declares entryId as of MIGRATION_7_8
            // (gallery ownership), so this "legacy" log_photos table is missing a column a real
            // version-6 install always had — restored here (a plain ADD COLUMN, not a drop, since
            // nothing leaked in this time; something leaked away) so MIGRATION_7_8's own
            // `SELECT entryId, id FROM log_photos` doesn't fail with "no such column" further down
            // the same migration chain. See LogPhotoMigrationTest's own doc comment for the
            // general reasoning.
            legacyDb.openHelper.writableDatabase.execSQL("ALTER TABLE `log_photos` ADD COLUMN `entryId` TEXT")
        } finally {
            legacyDb.close()
        }

        // Reopen the same file as the real, current ForagerDatabase — no
        // fallbackToDestructiveMigration here, so if MIGRATION_6_7 is missing or wrong, this throws
        // rather than silently wiping the file.
        val migrated = Room.databaseBuilder(context, ForagerDatabase::class.java, dbFile.absolutePath)
            .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
            .build()

        try {
            val survivedTrips = migrated.plannedTripDao().getAll()
            assertEquals(listOf(legacyTrip), survivedTrips)

            val repository = RoomMushroomLogRepository(migrated.mushroomLogDao())
            val survivedEntries = repository.getAll().getOrThrow()
            assertEquals(listOf(legacyEntry), survivedEntries)
            assertEquals(LatLng(45.5, -122.6), survivedEntries.single().foundAt)

            // The migration this file exists to prove: a brand-new entry saved with no location at
            // all (only representable post-migration — lat/lng were NOT NULL before MIGRATION_6_7)
            // reads back with foundAt == null, not a fabricated (0.0, 0.0) or a crash.
            val locationLessEntry = MushroomLogEntry.draft(id = "post-migration-no-location", location = null, date = LocalDate.of(2026, 8, 20))
            repository.save(locationLessEntry).getOrThrow()
            val reloaded = repository.getAll().getOrThrow().single { it.id == locationLessEntry.id }
            assertNull(reloaded.foundAt)
            assertEquals(locationLessEntry, reloaded)

            // Workstream A's offlineRegionId index has to survive the table rebuild — exercised via
            // the raw entity (the domain model deliberately doesn't expose this column; that wiring
            // is Workstream B's, not L3's) rather than merely checking the index's name is present,
            // since a rebuild that silently dropped and never recreated it would still pass a
            // name-only check right up until a query actually needed it.
            val entities = migrated.mushroomLogDao().getAllEntries()
            assertEquals(2, entities.size)
            entities.forEach { assertNull("no entry in this test ever sets offlineRegionId", it.offlineRegionId) }
        } finally {
            migrated.close()
        }
    }

    private companion object {
        const val TEST_DB_NAME = "mushroom-log-entry-migration-test.db"
    }
}

/**
 * Version 6 of [ForagerDatabase], reconstructed for this test from the *same* entity and DAO
 * classes production code used at that version — none of them are copied, so this can't drift from
 * what a real version-6 install actually wrote. `exportSchema = false` here matches every other
 * `LegacyForagerDatabaseVn` fixture in this package.
 *
 * **Not a faithful copy of the true version-6 `lat`/`lng` columns' `NOT NULL` constraint.**
 * [MushroomLogEntryEntity.lat]/`.lng` are `Double?` on the shared production class as of this
 * migration, so Room builds this "legacy" table with nullable columns a real version-6 install
 * never had — the same *kind* of leak [OfflineRegionMigrationTest]/[TrackWaypointMigrationTest]
 * correct for with `offlineRegionId` (see
 * `docs/audits/2026-08-24-migration-fixture-entity-reuse-pitfall.md`), but this one does **not**
 * need the same drop-and-recreate treatment: that fix exists because `MIGRATION_5_6`'s
 * `ALTER TABLE ... ADD COLUMN` fails outright against a column that's already present.
 * [MIGRATION_6_7] is a table *rebuild* (`CREATE` the new shape, copy every row by explicit column
 * list, `DROP` the old table, `RENAME`) — it never inspects or depends on the source table's
 * declared column constraints, only the data in it, and every row this fixture seeds has a real,
 * non-null location. Confirmed empirically, not assumed: [MushroomLogMigrationTest],
 * [TrackWaypointMigrationTest], and [OfflineRegionMigrationTest] all still pass unchanged with
 * [MIGRATION_6_7] appended to their own migration chains — the `ADD COLUMN` failure mode that made
 * the `offlineRegionId` fix necessary does not recur for a rebuild-shaped migration. Leaving this
 * fixture's `lat`/`lng` nullable is a deliberate choice, not an oversight.
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
    version = 6,
    exportSchema = false,
)
internal abstract class LegacyForagerDatabaseV6 : RoomDatabase() {
    abstract fun plannedTripDao(): PlannedTripDao
    abstract fun mushroomLogDao(): MushroomLogDao
}
