package com.forager.app.data.local

import android.app.Application
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import com.forager.app.data.repository.RoomCartographyEntryRepository
import com.forager.app.data.repository.RoomMushroomLogRepository
import com.forager.app.domain.model.CartographyEntry
import com.forager.app.domain.model.KeptFindRef
import com.forager.app.domain.model.KeptOfflineRegionRef
import com.forager.app.domain.model.KeptPhotoRef
import com.forager.app.domain.model.KeptTrackRef
import com.forager.app.domain.model.KeptWaypointRef
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
 * The 10→11 counterpart to [OfflineRegionMigrationTest]/[TrackWaypointMigrationTest] — see
 * [MIGRATION_10_11]'s own doc comment for why this ships a real migration rather than
 * `fallbackToDestructiveMigration()`.
 *
 * Builds a real version-10 database (via [LegacyForagerDatabaseV10], sharing the same entity/DAO
 * classes production version 10 used), seeds a mushroom-log entry through the real repository so the
 * seeded row is exactly what production code would have written, reopens it as [ForagerDatabase]
 * with [MIGRATION_10_11] applied, and asserts the pre-existing row survived intact and every one of
 * the five new `cartography_entry*` tables is actually usable — a real save/read round trip through
 * the production [RoomCartographyEntryRepository], not just table existence.
 *
 * **No legacy-fixture entity-reuse fix needed here** (contrast [OfflineRegionMigrationTest]'s own
 * `DROP INDEX`/`ALTER TABLE` workarounds) — see `docs/audits/2026-08-24-migration-fixture-entity-reuse-pitfall.md`
 * for why that class of failure only recurs when a migration alters an *existing* entity's shape.
 * [MIGRATION_10_11] does not: every statement in it is `CREATE TABLE`/`CREATE INDEX` for tables that
 * did not exist before, so [LegacyForagerDatabaseV10] needs no correction to be a faithful version-10
 * fixture.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CartographyEntryMigrationTest {

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
    fun `a pre-existing mushroom log entry survives the 10 to 11 migration intact, and every new cartography_entry table round-trips through the real repository`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>()

        val legacyEntry = MushroomLogEntry.draft(id = "entry-1", location = LatLng(45.5, -122.6), date = LocalDate.of(2026, 8, 1)).copy(isDraft = false)

        val legacyDb = Room.databaseBuilder(context, LegacyForagerDatabaseV10::class.java, dbFile.absolutePath).build()
        try {
            RoomMushroomLogRepository(legacyDb.mushroomLogDao()).save(legacyEntry).getOrThrow()
        } finally {
            legacyDb.close()
        }

        // Reopen the same file as the real, current ForagerDatabase — no
        // fallbackToDestructiveMigration here, so if MIGRATION_10_11 is missing or wrong, this
        // throws rather than silently wiping the file.
        val migrated = Room.databaseBuilder(context, ForagerDatabase::class.java, dbFile.absolutePath)
            .addMigrations(
                MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
                MIGRATION_9_10, MIGRATION_10_11,
            )
            .build()

        try {
            val survivedEntries = RoomMushroomLogRepository(migrated.mushroomLogDao()).getAll().getOrThrow()
            assertEquals(listOf(legacyEntry), survivedEntries)

            val repository = RoomCartographyEntryRepository(migrated.cartographyEntryDao())
            assertTrue("cartography_entries should start empty after migrating a v10 database", repository.getAll().getOrThrow().isEmpty())

            val entry = CartographyEntry(
                id = "cartography-entry-1",
                date = LocalDate.of(2026, 8, 1),
                text = "A good day.",
                tags = listOf("chanterelle", "ridge trail"),
                isDraft = false,
                updatedAtEpochMillis = 5_000L,
                keptFinds = listOf(KeptFindRef(findId = legacyEntry.id, foundOn = legacyEntry.foundOn, ownIdentification = null, hasPhotos = false)),
                keptTracks = listOf(KeptTrackRef(trackId = "track-1", name = "Ridge Loop", distanceMeters = 3200.0, durationMillis = 5_400_000L, pointCount = 240)),
                keptWaypoints = listOf(KeptWaypointRef(waypointId = "waypoint-1", name = "Trailhead", lat = 45.4, lng = -122.6)),
                keptOfflineRegions = listOf(KeptOfflineRegionRef(offlineRegionId = 1L, name = "Ridge Region", lat = 45.4, lng = -122.6, radiusKm = 10)),
                keptPhotos = listOf(KeptPhotoRef(photoId = "photo-1", attachedAtEpochMillis = 6_000L)),
            )
            repository.save(entry).getOrThrow()

            assertEquals(entry, repository.getById(entry.id).getOrThrow())
            assertEquals(listOf(entry), repository.getAll().getOrThrow())
            assertEquals(1, repository.countEntriesReferencingTrack("track-1").getOrThrow())
            assertEquals(1, repository.countEntriesReferencingWaypoint("waypoint-1").getOrThrow())
            assertEquals(1, repository.countEntriesReferencingOfflineRegion(1L).getOrThrow())
            assertEquals(1, repository.countEntriesReferencingPhoto("photo-1").getOrThrow())

            repository.delete(entry.id).getOrThrow()
            assertTrue(repository.getAll().getOrThrow().isEmpty())
            assertEquals(0, repository.countEntriesReferencingTrack("track-1").getOrThrow())
        } finally {
            migrated.close()
        }
    }

    private companion object {
        const val TEST_DB_NAME = "cartography-entry-migration-test.db"
    }
}

/**
 * Version 10 of [ForagerDatabase], reconstructed for this test from the *same* entity and DAO
 * classes production code used at that version — none of them are copied, so this can't drift from
 * what a real version-10 install actually wrote. `exportSchema = false` here matches
 * [OfflineRegionMigrationTest]'s own `LegacyForagerDatabaseV5`.
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
    version = 10,
    exportSchema = false,
)
internal abstract class LegacyForagerDatabaseV10 : RoomDatabase() {
    abstract fun plannedTripDao(): PlannedTripDao
    abstract fun cachedSearchDao(): CachedSearchDao
    abstract fun mushroomLogDao(): MushroomLogDao
    abstract fun trackDao(): TrackDao
    abstract fun waypointDao(): WaypointDao
    abstract fun offlineRegionDao(): OfflineRegionDao
}
