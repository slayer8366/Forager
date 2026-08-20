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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The migration [MIGRATION_4_5] cannot ship without — mirrors [MushroomLogMigrationTest]'s own
 * "build a real prior-version database, migrate it, assert survival" shape. A version-4 device (one
 * already holding real field notes from [MIGRATION_3_4]) upgrading to this app version must keep
 * them, not lose them because no explicit 4→5 path existed for the unrelated `offline_regions`
 * table this bump adds.
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
    fun `field notes survive the 4 to 5 migration intact, and offline_regions is usable afterward`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>()

        // Build a real version-4 database, using the same production entity classes — not a
        // reimplementation of their schema that could quietly drift from what version 4 actually
        // wrote to disk.
        val legacyEntry = MushroomLogEntry.draft(id = "entry-1", location = LatLng(45.0, -122.0), date = LocalDate.of(2026, 8, 1))
        val legacyDb = Room.databaseBuilder(context, LegacyForagerDatabaseV4::class.java, dbFile.absolutePath).build()
        try {
            RoomMushroomLogRepository(legacyDb.mushroomLogDao()).save(legacyEntry).getOrThrow()
        } finally {
            legacyDb.close()
        }

        // Reopen the same file as the real, current ForagerDatabase — no
        // fallbackToDestructiveMigration here, so if MIGRATION_4_5 is missing or wrong, this throws
        // rather than silently wiping the file.
        val migrated = Room.databaseBuilder(context, ForagerDatabase::class.java, dbFile.absolutePath)
            .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
            .build()

        try {
            val survivedEntries = RoomMushroomLogRepository(migrated.mushroomLogDao()).getAll().getOrThrow()
            assertEquals(listOf(legacyEntry), survivedEntries)

            // The new table isn't just present after migration — it actually works: a real
            // insert/read round trip through the production DAO.
            assertTrue("offline_regions should start empty after migrating a v4 database", migrated.offlineRegionDao().getAll().isEmpty())

            val newRegion = OfflineRegionEntity(
                id = 1L,
                name = "Chanterelle Ridge",
                lat = 45.326,
                lng = -122.634,
                radiusKm = 15,
                minZoom = 10.0,
                maxZoom = 14.0,
                createdAtEpochMillis = 1_755_000_000_000L,
            )
            migrated.offlineRegionDao().upsert(newRegion)
            assertEquals(listOf(newRegion), migrated.offlineRegionDao().getAll())
        } finally {
            migrated.close()
        }
    }

    private companion object {
        const val TEST_DB_NAME = "offline-region-migration-test.db"
    }
}

/**
 * Version 4 of [ForagerDatabase], reconstructed for this test from the *same* entity classes
 * production code used at that version — shared with `main`, not copied, so this can't drift from
 * what a real version-4 install actually wrote. `exportSchema = false` here doesn't matter for a
 * test-only `@Database` the same way [ForagerDatabase]'s doc comment explains for
 * `LegacyForagerDatabaseV3`.
 */
@Database(
    entities = [
        PlannedTripEntity::class,
        CachedSearchEntity::class,
        MushroomLogEntryEntity::class,
        LogPhotoEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
internal abstract class LegacyForagerDatabaseV4 : RoomDatabase() {
    abstract fun plannedTripDao(): PlannedTripDao
    abstract fun cachedSearchDao(): CachedSearchDao
    abstract fun mushroomLogDao(): MushroomLogDao
}
