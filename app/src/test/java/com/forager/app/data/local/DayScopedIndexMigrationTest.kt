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
 * The 9→10 counterpart to [MushroomLogDraftMigrationTest] and the rest of this package's migration
 * tests — Journal Stage 2a dispatch. See [MIGRATION_9_10]'s own doc comment for why this is pure
 * `CREATE INDEX IF NOT EXISTS`, not a table rebuild, and why that makes it immune to the
 * `ALTER TABLE ... ADD COLUMN`/leaked-column failure mode this package's own migration history
 * documents repeatedly (`docs/audits/2026-08-24-migration-fixture-entity-reuse-pitfall.md`): every
 * entity this migration adds an index to (`MushroomLogEntryEntity`/`TrackEntity`/`WaypointEntity`/
 * `OfflineRegionEntity`) is a *shared* production class, so [LegacyForagerDatabaseV9] below builds
 * its tables with these same indexes already present — `CREATE INDEX IF NOT EXISTS` tolerates that
 * silently, unlike `ADD COLUMN`, so (checked here rather than assumed) none of the drop-then-recreate
 * treatment `OfflineRegionMigrationTest` needed for a leaked *column* is needed for a leaked index.
 *
 * Builds a real version-9 database (via [LegacyForagerDatabaseV9]), seeds one row in each of the
 * four tables this migration indexes, reopens it as [ForagerDatabase] with [MIGRATION_9_10] applied,
 * and asserts every row survives intact **and** that the new day-scoped queries this migration exists
 * to support actually return it — proof the indexed columns are genuinely queryable after migrating
 * from a pre-index database, not just present.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DayScopedIndexMigrationTest {

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
    fun `pre-existing rows in all four indexed tables survive the 9 to 10 migration, and the new day-scoped queries find them`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>()

        val legacyEntry = MushroomLogEntry.draft(id = "entry-1", location = LatLng(45.5, -122.6), date = LocalDate.of(2026, 8, 5))
            .copy(isDraft = false)
        val legacyTrack = TrackEntity(id = "track-1", name = null, startedAtEpochMillis = 1_000L, endedAtEpochMillis = 2_000L)
        val legacyWaypoint = WaypointEntity(
            id = "waypoint-1",
            lat = 45.5,
            lng = -122.6,
            altitude = null,
            name = "Trailhead",
            note = "",
            createdAtEpochMillis = 1_500L,
        )
        val legacyRegion = OfflineRegionEntity(
            id = 1L,
            name = "Chanterelle Ridge",
            lat = 45.5,
            lng = -122.6,
            radiusKm = 10,
            minZoom = 10.0,
            maxZoom = 15.0,
            createdAtEpochMillis = 1_800L,
        )

        val legacyDb = Room.databaseBuilder(context, LegacyForagerDatabaseV9::class.java, dbFile.absolutePath).build()
        try {
            RoomMushroomLogRepository(legacyDb.mushroomLogDao()).save(legacyEntry).getOrThrow()
            legacyDb.trackDao().insertTrack(legacyTrack)
            legacyDb.waypointDao().upsert(legacyWaypoint)
            legacyDb.offlineRegionDao().upsert(legacyRegion)
        } finally {
            legacyDb.close()
        }

        // Reopen the same file as the real, current ForagerDatabase — no
        // fallbackToDestructiveMigration here, so if MIGRATION_9_10 is missing or wrong, this throws
        // rather than silently wiping the file.
        val migrated = Room.databaseBuilder(context, ForagerDatabase::class.java, dbFile.absolutePath)
            .addMigrations(
                MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
                MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12,
            )
            .build()

        try {
            // Every row survives — a real migration, not a destructive fallback.
            assertEquals(listOf(legacyEntry), RoomMushroomLogRepository(migrated.mushroomLogDao()).getAll().getOrThrow())
            assertEquals(listOf(legacyTrack), migrated.trackDao().getAllTracks())
            assertEquals(listOf(legacyWaypoint), migrated.waypointDao().getAll())
            assertEquals(listOf(legacyRegion), migrated.offlineRegionDao().getAll())

            // The new day-scoped queries actually find the migrated rows — not just "the migration
            // ran without throwing," but that the indexed columns are genuinely queryable.
            assertEquals(listOf(legacyEntry), RoomMushroomLogRepository(migrated.mushroomLogDao()).getForDay("2026-08-05").getOrThrow())
            assertTrue(RoomMushroomLogRepository(migrated.mushroomLogDao()).getForDay("2026-08-06").getOrThrow().isEmpty())

            assertEquals(listOf("track-1"), migrated.trackDao().getTracksForDay(0L, 3_000L).map { it.id })
            assertTrue(migrated.trackDao().getTracksForDay(3_000L, 4_000L).isEmpty())

            assertEquals(listOf("waypoint-1"), migrated.waypointDao().getForDay(0L, 3_000L).map { it.id })
            assertTrue(migrated.waypointDao().getForDay(3_000L, 4_000L).isEmpty())

            assertEquals(listOf(1L), migrated.offlineRegionDao().getForDay(0L, 3_000L).map { it.id })
            assertTrue(migrated.offlineRegionDao().getForDay(3_000L, 4_000L).isEmpty())
        } finally {
            migrated.close()
        }
    }

    private companion object {
        const val TEST_DB_NAME = "day-scoped-index-migration-test.db"
    }
}

/**
 * Version 9 of [ForagerDatabase], reconstructed for this test from the *same* entity and DAO classes
 * production code used at that version — none of them are copied, so this can't drift from what a
 * real version-9 install actually wrote. Exposes all four DAOs this migration indexes (unlike the
 * narrower `LegacyForagerDatabaseVn` fixtures elsewhere in this file, which only needed the ones
 * their own migration under test touched) since this test seeds a row into each of the four tables
 * before migrating. `exportSchema = false` matches every other `LegacyForagerDatabaseVn` fixture in
 * this package.
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
    version = 9,
    exportSchema = false,
)
internal abstract class LegacyForagerDatabaseV9 : RoomDatabase() {
    abstract fun mushroomLogDao(): MushroomLogDao
    abstract fun trackDao(): TrackDao
    abstract fun waypointDao(): WaypointDao
    abstract fun offlineRegionDao(): OfflineRegionDao
}
