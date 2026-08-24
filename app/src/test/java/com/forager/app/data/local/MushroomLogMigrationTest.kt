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
 * The migration this feature cannot ship without — see [ForagerDatabase]'s doc comment on why
 * version 4 uses a real [androidx.room.migration.Migration] rather than
 * `fallbackToDestructiveMigration()`: field notes are irreplaceable, and a version-3 device (one
 * already holding real [PlannedTripEntity]/[CachedSearchEntity] rows) upgrading to this app version
 * must keep them, not silently lose them because no explicit 3→4 path existed.
 *
 * Builds a real version-3 database (via [LegacyForagerDatabaseV3], which shares the *same* entity
 * classes production version 3 used — not a hand-copied schema that could drift from the real one),
 * inserts rows, reopens it as [ForagerDatabase] with [MIGRATION_3_4] applied, and asserts the
 * pre-existing rows survived with their values intact, plus that the new tables are actually
 * usable afterward — not just present.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MushroomLogMigrationTest {

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
    fun `planned trips and cached searches survive the 3 to 4 migration intact, and the new tables are usable`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>()

        // Build a real version-3 database, using the same production entity classes — not a
        // reimplementation of their schema that could quietly drift from what version 3 actually
        // wrote to disk.
        val legacyTrip = PlannedTripEntity(id = "trip-1", name = "Chanterelle Ridge", lat = 45.51, lng = -122.66, date = "2026-09-01")
        val legacySearch = CachedSearchEntity(
            key = "45.51|-122.66|15|8|iconic:Fungi:without=54743",
            lat = 45.51,
            lng = -122.66,
            radiusKm = 15,
            month = 8,
            filterLabel = "Fungi",
            filterIconicTaxonName = "Fungi",
            filterTaxonId = null,
            filterExcludedTaxonId = 54743L,
            entriesJson = "[]",
            fetchedAtEpochMillis = 1_000_000L,
            lastAccessedAtEpochMillis = 1_000_000L,
        )
        val legacyDb = Room.databaseBuilder(context, LegacyForagerDatabaseV3::class.java, dbFile.absolutePath).build()
        try {
            legacyDb.plannedTripDao().upsert(legacyTrip)
            legacyDb.cachedSearchDao().upsert(legacySearch)
        } finally {
            legacyDb.close()
        }

        // Reopen the same file as the real, current ForagerDatabase — no
        // fallbackToDestructiveMigration here, so if MIGRATION_3_4 is missing or wrong, this throws
        // rather than silently wiping the file.
        val migrated = Room.databaseBuilder(context, ForagerDatabase::class.java, dbFile.absolutePath)
            .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
            .build()

        try {
            val survivedTrips = migrated.plannedTripDao().getAll()
            assertEquals(listOf(legacyTrip), survivedTrips)

            val survivedSearches = migrated.cachedSearchDao().getAllOrderedByLastAccessed()
            assertEquals(listOf(legacySearch), survivedSearches)

            // The new tables aren't just present after migration — they actually work: a real
            // save/read round-trip through the production repository.
            val logRepository = RoomMushroomLogRepository(migrated.mushroomLogDao())
            assertTrue("mushroom log table should start empty after migrating a v3 database", logRepository.getAll().getOrThrow().isEmpty())

            val newEntry = MushroomLogEntry.draft(id = "post-migration-1", location = LatLng(45.0, -122.0), date = LocalDate.of(2026, 8, 20))
            logRepository.save(newEntry).getOrThrow()
            assertEquals(listOf(newEntry), logRepository.getAll().getOrThrow())
        } finally {
            migrated.close()
        }
    }

    private companion object {
        const val TEST_DB_NAME = "mushroom-log-migration-test.db"
    }
}

/**
 * Version 3 of [ForagerDatabase], reconstructed for this test from the *same* entity classes
 * production code used at that version — [PlannedTripEntity] and [CachedSearchEntity] are shared
 * with `main`, not copied, so this can't drift from what a real version-3 install actually wrote.
 * `exportSchema = false` here matches how version 3 itself shipped (see [ForagerDatabase]'s doc
 * comment: `exportSchema` only flipped to `true` at version 4).
 */
@Database(entities = [PlannedTripEntity::class, CachedSearchEntity::class], version = 3, exportSchema = false)
internal abstract class LegacyForagerDatabaseV3 : RoomDatabase() {
    abstract fun plannedTripDao(): PlannedTripDao
    abstract fun cachedSearchDao(): CachedSearchDao
}
