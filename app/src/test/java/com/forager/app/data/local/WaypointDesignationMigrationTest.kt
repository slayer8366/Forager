package com.forager.app.data.local

import android.app.Application
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import com.forager.app.data.repository.RoomWaypointRepository
import com.forager.app.domain.model.Waypoint
import com.forager.app.domain.model.WaypointDesignation
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The 13→14 counterpart to [TrackOriginWaypointMigrationTest] — navigation HUD stage one's
 * `designation` column. A real version-13 file via [LegacyForagerDatabaseV13], restored to its
 * true pre-migration shape (the shared entity leaks the new column into the fixture; see the
 * `DROP COLUMN` in the body and `docs/audits/2026-08-24-migration-fixture-entity-reuse-pitfall.md`),
 * reopened with [MIGRATION_13_14]: the legacy row survives with `NULL` designation and its
 * `trackId` intact; an origin and an end waypoint round-trip with their designation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WaypointDesignationMigrationTest {

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
    fun `a pre-existing linked waypoint survives 13 to 14 with no designation, and designated waypoints round-trip`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val legacy = WaypointEntity(id = "wp-legacy", lat = 45.5, lng = -122.6, altitude = 312.0, name = "Old parking", note = "", createdAtEpochMillis = 1_500L, trackId = "track-1")

        val legacyDb = Room.databaseBuilder(context, LegacyForagerDatabaseV13::class.java, dbFile.absolutePath).build()
        try {
            legacyDb.waypointDao().upsert(legacy)
            // The shared WaypointEntity now declares `designation`, which a real version-13 install
            // never had — dropped so the file reaches MIGRATION_13_14 in its true shape. No index on
            // it, so the column alone goes.
            legacyDb.openHelper.writableDatabase.execSQL("ALTER TABLE `waypoints` DROP COLUMN `designation`")
        } finally {
            legacyDb.close()
        }

        val migrated = Room.databaseBuilder(context, ForagerDatabase::class.java, dbFile.absolutePath)
            .addMigrations(MIGRATION_13_14)
            .build()
        try {
            val repository = RoomWaypointRepository(migrated.waypointDao())

            assertEquals(
                listOf(Waypoint(id = "wp-legacy", lat = 45.5, lng = -122.6, altitude = 312.0, name = "Old parking", note = "", createdAtEpochMillis = 1_500L, trackId = "track-1", designation = null)),
                repository.getAll().getOrThrow(),
            )

            val origin = Waypoint(id = "wp-origin", lat = 45.52, lng = -122.68, altitude = 50.0, name = "Start · Sep 5, 9:41 AM", note = "", createdAtEpochMillis = 3_000L, trackId = "track-2", designation = WaypointDesignation.ORIGIN)
            val end = Waypoint(id = "wp-end", lat = 45.53, lng = -122.69, altitude = null, name = "End · Sep 5, 11:02 AM", note = "", createdAtEpochMillis = 4_000L, trackId = "track-2", designation = WaypointDesignation.END)
            repository.save(origin).getOrThrow()
            repository.save(end).getOrThrow()

            assertEquals(listOf(origin, end), repository.getForTrack("track-2").getOrThrow())
        } finally {
            migrated.close()
        }
    }

    private companion object {
        const val TEST_DB_NAME = "waypoint-designation-migration-test.db"
    }
}

/** Version 13 of [ForagerDatabase] from the shared entity/DAO classes — see [LegacyForagerDatabaseV12] for the pattern and its caveat. */
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
        CartographyEntryEntity::class,
        CartographyEntryTrackRefEntity::class,
        CartographyEntryWaypointRefEntity::class,
        CartographyEntryOfflineRegionRefEntity::class,
        CartographyEntryFindRefEntity::class,
        CartographyEntryPhotoRefEntity::class,
    ],
    version = 13,
    exportSchema = false,
)
internal abstract class LegacyForagerDatabaseV13 : RoomDatabase() {
    abstract fun waypointDao(): WaypointDao
}
