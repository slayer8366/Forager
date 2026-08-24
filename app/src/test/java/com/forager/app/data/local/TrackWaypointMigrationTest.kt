package com.forager.app.data.local

import android.app.Application
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import com.forager.app.data.repository.RoomMushroomLogRepository
import com.forager.app.data.repository.RoomTrackRepository
import com.forager.app.data.repository.RoomWaypointRepository
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.MushroomLogEntry
import com.forager.app.domain.model.Track
import com.forager.app.domain.model.TrackPoint
import com.forager.app.domain.model.Waypoint
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
 * The 4→5 counterpart to [MushroomLogMigrationTest] — see [ForagerDatabase]'s doc comment on
 * [MIGRATION_4_5]: a recorded track or a dropped waypoint is irreplaceable field data, so this
 * ships a real migration, not `fallbackToDestructiveMigration()`.
 *
 * Builds a real version-4 database (via [LegacyForagerDatabaseV4], sharing the same entity/DAO
 * classes production version 4 used), seeds a planned trip and a mushroom-log entry through the
 * real repository so the seeded row is exactly what production code would have written — not a
 * hand-copied field list that could drift — reopens it as [ForagerDatabase] with [MIGRATION_4_5]
 * applied, and asserts the pre-existing rows survived intact and the new track/waypoint tables are
 * actually usable afterward, not just present.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrackWaypointMigrationTest {

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
    fun `planned trips and mushroom log entries survive the 4 to 5 migration intact, and the new tables are usable`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>()

        val legacyTrip = PlannedTripEntity(id = "trip-1", name = "Chanterelle Ridge", lat = 45.51, lng = -122.66, date = "2026-09-01")
        val legacyEntry = MushroomLogEntry.draft(id = "entry-1", location = LatLng(45.5, -122.6), date = LocalDate.of(2026, 8, 1))

        val legacyDb = Room.databaseBuilder(context, LegacyForagerDatabaseV4::class.java, dbFile.absolutePath).build()
        try {
            legacyDb.plannedTripDao().upsert(legacyTrip)
            RoomMushroomLogRepository(legacyDb.mushroomLogDao()).save(legacyEntry).getOrThrow()

            // LegacyForagerDatabaseV4 reuses the *production* MushroomLogEntryEntity class — but
            // that class now has offlineRegionId (plus its index), added in MIGRATION_5_6, which a
            // real version-4 install never had. Drop both here so the file reaches MIGRATION_5_6 in
            // its true pre-migration shape — see OfflineRegionMigrationTest's identical fix for the
            // full reasoning; the index has to go first, or dropping the column while it's still
            // referenced fails with "no such column."
            legacyDb.openHelper.writableDatabase.execSQL("DROP INDEX `index_mushroom_log_entries_offlineRegionId`")
            legacyDb.openHelper.writableDatabase.execSQL("ALTER TABLE `mushroom_log_entries` DROP COLUMN `offlineRegionId`")
        } finally {
            legacyDb.close()
        }

        // Reopen the same file as the real, current ForagerDatabase — no
        // fallbackToDestructiveMigration here, so if MIGRATION_4_5 is missing or wrong, this throws
        // rather than silently wiping the file.
        val migrated = Room.databaseBuilder(context, ForagerDatabase::class.java, dbFile.absolutePath)
            .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .build()

        try {
            val survivedTrips = migrated.plannedTripDao().getAll()
            assertEquals(listOf(legacyTrip), survivedTrips)

            val survivedEntries = RoomMushroomLogRepository(migrated.mushroomLogDao()).getAll().getOrThrow()
            assertEquals(listOf(legacyEntry), survivedEntries)

            // The new tables aren't just present after migration — they actually work: a real
            // save/read round-trip through the production repositories.
            val trackRepository = RoomTrackRepository(migrated.trackDao())
            assertTrue("tracks table should start empty after migrating a v4 database", trackRepository.getAll().getOrThrow().isEmpty())

            val track = Track(id = "post-migration-track", name = "Test loop", startedAtEpochMillis = 1_000L, endedAtEpochMillis = null, points = emptyList())
            trackRepository.create(track).getOrThrow()
            val point = TrackPoint(lat = 45.0, lng = -122.0, altitude = 100.0, accuracyMeters = 5f, timestampEpochMillis = 1_500L)
            trackRepository.appendPoints(track.id, listOf(point)).getOrThrow()
            trackRepository.end(track.id, endedAtEpochMillis = 2_000L).getOrThrow()

            val savedTrack = trackRepository.getById(track.id).getOrThrow()
            assertEquals(track.copy(endedAtEpochMillis = 2_000L, points = listOf(point)), savedTrack)

            val waypointRepository = RoomWaypointRepository(migrated.waypointDao())
            val waypoint = Waypoint(id = "post-migration-waypoint", lat = 45.1, lng = -122.1, altitude = null, name = "Trailhead", note = "", createdAtEpochMillis = 3_000L)
            waypointRepository.save(waypoint).getOrThrow()
            assertEquals(listOf(waypoint), waypointRepository.getAll().getOrThrow())
        } finally {
            migrated.close()
        }
    }

    private companion object {
        const val TEST_DB_NAME = "track-waypoint-migration-test.db"
    }
}

/**
 * Version 4 of [ForagerDatabase], reconstructed for this test from the *same* entity and DAO
 * classes production code used at that version — none of them are copied, so this can't drift from
 * what a real version-4 install actually wrote. `exportSchema = false` here matches how version 4
 * was captured for this test (the real, production version 4 does export its schema — see
 * [ForagerDatabase]'s doc comment — this local declaration just doesn't need its own copy of that
 * export).
 */
@Database(
    entities = [PlannedTripEntity::class, CachedSearchEntity::class, MushroomLogEntryEntity::class, LogPhotoEntity::class],
    version = 4,
    exportSchema = false,
)
internal abstract class LegacyForagerDatabaseV4 : RoomDatabase() {
    abstract fun plannedTripDao(): PlannedTripDao
    abstract fun cachedSearchDao(): CachedSearchDao
    abstract fun mushroomLogDao(): MushroomLogDao
}
